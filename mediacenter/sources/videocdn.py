"""
VideoCDN / Alloha (Allarknow) + Vibix Balancer Source Adapter.
Extracted & decrypted from 'Кино HD' (Alloha.smali, Vibix.smali, fGrfleDmwo).
Supports search by title and Kinopoisk ID, series season/episode selection,
and delivers both Alloha and Vibix HD player embeds with authentic Referer headers.
"""
import time
import re
import urllib.parse
import requests
from typing import List, Optional, Dict, Any
from .base import BaseSource, MediaItem, StreamResult, VideoStream, AudioTrack, SeasonItem, EpisodeItem, CanaryReport


class VideoCDNSource(BaseSource):
    name = "videocdn"
    display_name = "Alloha / Vibix (HD Плеер)"
    source_type = "balancer"

    API_BASE = "https://api.apbugall.org"
    TOKEN = "bfbb025034f40bbe51d8d640f8d182"
    SECONDARY_TOKEN = "acd15023937b35dafa567fcb944979"

    VIBIX_API = "https://vibix.org/api/v1"
    VIBIX_BEARER = "Bearer 20698|h7KqmO1h8OXO2hhcUNl5vK8ldEBnFOsQtGEs9rCM8d814b0b"

    def __init__(self):
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Referer": "https://a-apps.net/",
        }
        self.vibix_headers = {
            "User-Agent": self.headers["User-Agent"],
            "Accept": "application/json",
            "Authorization": self.VIBIX_BEARER,
        }

    def _fetch_alloha_items(self, query: Optional[str] = None, kp_id: Optional[str] = None) -> List[Dict[str, Any]]:
        results: List[Dict[str, Any]] = []
        urls = []
        if kp_id and str(kp_id).isdigit():
            urls.append(f"{self.API_BASE}/?token={self.TOKEN}&kp={kp_id}")
        if query:
            encoded = urllib.parse.quote(query.strip())
            urls.append(f"{self.API_BASE}/?token={self.TOKEN}&name={encoded}")

        for url in urls:
            try:
                res = requests.get(url, headers=self.headers, timeout=5)
                if res.status_code == 200:
                    data = res.json()
                    raw_data = data.get("data")
                    if isinstance(raw_data, dict) and (raw_data.get("token_movie") or raw_data.get("iframe") or raw_data.get("seasons")):
                        results.append(raw_data)
                        break
                    elif isinstance(raw_data, list) and raw_data:
                        results.extend(raw_data)
                        break
            except Exception:
                continue
        return results

    def search(self, query: str, year: Optional[int] = None, kp_id: Optional[str] = None) -> List[MediaItem]:
        items: List[MediaItem] = []
        raw_data = self._fetch_alloha_items(query=query, kp_id=kp_id)

        for r in raw_data:
            item_year = r.get("year")
            if year and item_year:
                try:
                    if abs(int(year) - int(item_year)) > 2:
                        continue
                except ValueError:
                    pass

            title = r.get("name") or r.get("original_name") or "Без названия"
            kp = str(r.get("id_kp") or "").strip()
            token_movie = str(r.get("token_movie") or kp).strip()
            is_series = bool(r.get("seasons")) or str(r.get("category", "")) in ("2", "serial", "series")

            items.append(MediaItem(
                id=token_movie,
                source_name=self.name,
                title=title,
                original_title=r.get("original_name"),
                year=int(item_year) if item_year and str(item_year).isdigit() else None,
                poster=r.get("poster"),
                description=r.get("description"),
                rating_kp=float(r.get("rating_kp", 0) or 0) or None,
                rating_imdb=float(r.get("rating_imdb", 0) or 0) or None,
                kinopoisk_id=kp if kp and kp != "None" else None,
                is_series=is_series,
                extra_data={
                    "iframe": r.get("iframe"),
                    "quality": r.get("quality", "1080p"),
                    "translation": r.get("translation", ""),
                    "token_movie": r.get("token_movie"),
                }
            ))
        return items

    def _resolve_vibix(
        self,
        kp_id: Optional[str],
        title: Optional[str],
        season: Optional[int],
        episode: Optional[int],
        result: StreamResult
    ):
        """Queries Vibix API for additional Russian voiceover iframe embeds (e.g. SoftBox)."""
        v_data = None
        if kp_id and str(kp_id).isdigit():
            try:
                r = requests.get(f"{self.VIBIX_API}/publisher/videos/kp/{kp_id}", headers=self.vibix_headers, timeout=5)
                if r.status_code == 200:
                    v_data = r.json()
            except Exception:
                pass

        if not v_data and title:
            try:
                r = requests.post(
                    f"{self.VIBIX_API}/publisher/videos/search",
                    headers=self.vibix_headers,
                    data={"name": title.strip()},
                    timeout=5
                )
                if r.status_code == 200:
                    arr = r.json().get("data", [])
                    if isinstance(arr, list) and arr:
                        v_data = arr[0]
            except Exception:
                pass

        if not v_data or not isinstance(v_data, dict):
            return

        embed_code = str(v_data.get("embed_code") or "")
        iframe_url = str(v_data.get("iframe_url") or "")
        pub_m = re.search(r'data-publisher-id="(\d+)"', embed_code)
        type_m = re.search(r'data-type="([^"]+)"', embed_code)
        id_m = re.search(r'data-id="(\d+)"', embed_code)

        vibix_embed = None
        if pub_m and type_m and id_m:
            pub_id, v_type, v_id = pub_m.group(1), type_m.group(1), id_m.group(1)
            vibix_embed = f"https://{pub_id}.videoframe1.com/embed/{v_type}/{v_id}"
        elif iframe_url.startswith("http"):
            vibix_embed = iframe_url

        if vibix_embed:
            if season and episode:
                sep = "&" if "?" in vibix_embed else "?"
                vibix_embed = f"{vibix_embed}{sep}season={season}&episode={episode}"

            voiceovers = v_data.get("voiceovers") or []
            v_names = [vo.get("name") for vo in voiceovers if isinstance(vo, dict) and vo.get("name")]
            v_label = ", ".join(v_names[:2]) if v_names else (v_data.get("quality") or "HD")

            for vo in voiceovers:
                if isinstance(vo, dict) and vo.get("name"):
                    result.audio_tracks.append(AudioTrack(
                        id=f"vibix_{vo.get('id', vo['name'])}",
                        name=f"{vo['name']} (Vibix)"
                    ))

            if not result.embed_url:
                result.embed_url = vibix_embed

            result.streams.append(VideoStream(
                quality=f"1080p HD (Vibix - {v_label})",
                url=vibix_embed,
                stream_type="iframe",
                headers={
                    "User-Agent": self.headers["User-Agent"],
                    "Referer": "https://kngo.website/",
                }
            ))

    def get_streams(
        self,
        media_id: str,
        season: Optional[int] = None,
        episode: Optional[int] = None,
        audio_id: Optional[str] = None,
        title: Optional[str] = None,
        year: Optional[int] = None
    ) -> StreamResult:
        media_str = str(media_id or "").strip()
        raw: Optional[Dict[str, Any]] = None

        # 1. Try direct lookup by kp or token_movie
        try:
            if media_str.isdigit():
                url = f"{self.API_BASE}/?token={self.TOKEN}&kp={media_str}"
            else:
                url = f"{self.API_BASE}/?token={self.TOKEN}&token_movie={media_str}"
            res = requests.get(url, headers=self.headers, timeout=5)
            if res.status_code == 200:
                d = res.json().get("data")
                if isinstance(d, list) and d:
                    raw = d[0]
                elif isinstance(d, dict) and (d.get("token_movie") or d.get("iframe") or d.get("seasons")):
                    raw = d
        except Exception:
            pass

        # 2. Fallback by title if KP ID didn't match Alloha's KP ID (e.g. 5664825 -> 10372988)
        if not raw and title:
            candidates = self._fetch_alloha_items(query=title)
            if candidates:
                raw = candidates[0]

        real_kp = str(raw.get("id_kp") or "") if raw else (media_str if media_str.isdigit() else None)

        result = StreamResult(
            source_name=self.name,
            media_id=real_kp or media_str,
            title=title or (raw.get("name") if raw else f"Alloha Stream ({media_str})"),
            embed_url=None,
            streams=[],
            audio_tracks=[],
            seasons=[]
        )

        # 3. Extract Alloha stream/episode iframe if found
        if raw:
            embed_url = raw.get("iframe")
            seasons_dict = raw.get("seasons")
            if isinstance(seasons_dict, dict) and seasons_dict:
                s_keys = sorted(seasons_dict.keys(), key=lambda x: int(x) if str(x).isdigit() else 999)
                target_s = str(season) if (season and str(season) in seasons_dict) else s_keys[0]

                for s_k in s_keys:
                    s_obj = seasons_dict.get(s_k) or {}
                    eps_dict = s_obj.get("episodes") or {}
                    e_keys = sorted(eps_dict.keys(), key=lambda x: int(x) if str(x).isdigit() else 999)
                    if str(s_k).isdigit():
                        s_int = int(s_k)
                        ep_infos = [
                            EpisodeItem(episode_id=int(ek), title=f"Серия {ek}", season_id=s_int)
                            for ek in e_keys if str(ek).isdigit()
                        ]
                        result.seasons.append(SeasonItem(
                            season_id=s_int,
                            title=f"Сезон {s_k}",
                            episodes=ep_infos
                        ))

                target_s_obj = seasons_dict.get(target_s) or {}
                eps_dict = target_s_obj.get("episodes") or {}
                if eps_dict:
                    e_keys = sorted(eps_dict.keys(), key=lambda x: int(x) if str(x).isdigit() else 999)
                    target_e = str(episode) if (episode and str(episode) in eps_dict) else e_keys[0]
                    ep_data = eps_dict.get(target_e) or {}
                    if ep_data.get("iframe"):
                        embed_url = ep_data["iframe"]

                    trans_dict = ep_data.get("translation")
                    if isinstance(trans_dict, dict):
                        for tr_id, tr_val in trans_dict.items():
                            if isinstance(tr_val, dict):
                                tr_name = tr_val.get("translation") or f"Перевод {tr_id}"
                                tr_iframe = tr_val.get("iframe")
                                result.audio_tracks.append(AudioTrack(
                                    id=str(tr_id),
                                    name=f"{tr_name} (Alloha)"
                                ))
                                if audio_id and str(audio_id) == str(tr_id) and tr_iframe:
                                    embed_url = tr_iframe

            if embed_url:
                if "autoplay=" not in embed_url:
                    sep = "&" if "?" in embed_url else "?"
                    embed_url = f"{embed_url}{sep}autoplay=1"
                result.embed_url = embed_url
                trans_label = raw.get("translation") or "Alloha"
                result.streams.append(VideoStream(
                    quality=f"1080p HD ({trans_label})",
                    url=embed_url,
                    stream_type="iframe",
                    headers=self.headers
                ))

        # 4. Also resolve Vibix (provides SoftBox and other dubs!)
        self._resolve_vibix(kp_id=real_kp, title=title, season=season, episode=episode, result=result)

        return result

    def canary_test(self) -> CanaryReport:
        start_t = time.time()
        test_url = f"{self.API_BASE}/?token={self.TOKEN}&kp=301"
        try:
            res = requests.get(test_url, headers=self.headers, timeout=6)
            latency = (time.time() - start_t) * 1000

            if res.status_code != 200:
                return CanaryReport(
                    source_name=self.name,
                    is_active=False,
                    status="CHANGED / BROKEN",
                    latency_ms=latency,
                    message=f"Alloha/VideoCDN API returned HTTP {res.status_code}.",
                    needs_rework=True,
                    endpoint_tested=test_url,
                    last_tested=time.time()
                )

            data = res.json()
            if data.get("status") != "success":
                return CanaryReport(
                    source_name=self.name,
                    is_active=False,
                    status="CHANGED / BROKEN",
                    latency_ms=latency,
                    message="Alloha/VideoCDN API response status != success.",
                    needs_rework=True,
                    endpoint_tested=test_url,
                    last_tested=time.time()
                )

            iframe = data.get("data", {}).get("iframe", "")
            return CanaryReport(
                source_name=self.name,
                is_active=True,
                status="OK",
                latency_ms=latency,
                message=f"Alloha + Vibix fully operational! Active player: {iframe[:45]}...",
                needs_rework=False,
                endpoint_tested=test_url,
                last_tested=time.time()
            )
        except Exception as e:
            return CanaryReport(
                source_name=self.name,
                is_active=False,
                status="CHANGED / BROKEN",
                latency_ms=(time.time() - start_t) * 1000,
                message=f"Alloha/VideoCDN connection error: {str(e)}",
                needs_rework=True,
                endpoint_tested=test_url,
                last_tested=time.time()
            )
