"""
Delivembd / Collaps / Namy VOD Source Adapter.
Extracted & decrypted from 'Кино HD' (libzona.so -> zombie: https://dfg.apicollaps.cc)
and 'LazyMedia Deluxe' (api.namy.ws / api.embess.ws / api.nextembed.ws).
Extracts direct HLS master.m3u8 streams with Russian audio tracks, episodes, and subtitles!
"""
import time
import json
import re
import urllib.parse
import requests
from typing import List, Optional, Dict, Any
from .base import BaseSource, MediaItem, StreamResult, VideoStream, AudioTrack, SubtitleTrack, SeasonItem, EpisodeItem, CanaryReport


class DelivembdSource(BaseSource):
    name = "delivembd"
    display_name = "Collaps / Delivembd (Direct HLS)"
    source_type = "balancer"

    API_BASE = "https://dfg.apicollaps.cc"
    TOKEN = "eedefb541aeba871dcfc756e6b31c02e"
    EMBED_MIRRORS = [
        "https://api.namy.ws",
        "https://api.embess.ws",
        "https://api.nextembed.ws",
    ]

    def __init__(self):
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Referer": "https://api.namy.ws/",
        }

    def search(self, query: str, year: Optional[int] = None, kp_id: Optional[str] = None) -> List[MediaItem]:
        items: List[MediaItem] = []
        raw_results: List[Dict[str, Any]] = []

        # 1. Try by Kinopoisk ID first if provided
        if kp_id and str(kp_id).isdigit():
            try:
                url = f"{self.API_BASE}/franchise/details?token={self.TOKEN}&kinopoisk_id={kp_id}"
                r = requests.get(url, headers=self.headers, timeout=5)
                if r.status_code == 200:
                    data = r.json()
                    if isinstance(data, dict) and data.get("id"):
                        raw_results.append(data)
            except Exception:
                pass

        # 2. Fallback / search by title query
        if not raw_results and query:
            try:
                encoded = urllib.parse.quote(query.strip())
                url = f"{self.API_BASE}/list?token={self.TOKEN}&name={encoded}"
                r = requests.get(url, headers=self.headers, timeout=6)
                if r.status_code == 200:
                    data = r.json()
                    results = data.get("results", [])
                    if isinstance(results, list):
                        raw_results.extend(results)
            except Exception:
                pass

        for item in raw_results:
            item_year = item.get("year")
            if year and item_year:
                try:
                    if abs(int(year) - int(item_year)) > 2:
                        continue
                except ValueError:
                    pass

            kp = str(item.get("kinopoisk_id") or "").strip()
            collaps_id = str(item.get("id") or "").strip()
            item_id = kp if kp and kp != "None" else f"collaps_{collaps_id}"
            title = item.get("name") or item.get("origin_name") or f"Collaps ({item_id})"
            is_series = bool(item.get("seasons")) or str(item.get("type", "")).lower() in ("series", "serial", "anime")

            items.append(MediaItem(
                id=item_id,
                source_name=self.name,
                title=title,
                original_title=item.get("origin_name"),
                year=int(item_year) if item_year and str(item_year).isdigit() else None,
                poster=item.get("poster"),
                description=item.get("description"),
                rating_kp=float(item.get("kinopoisk") or 0) or None,
                rating_imdb=float(item.get("imdb") or 0) or None,
                kinopoisk_id=kp if kp and kp != "None" else None,
                is_series=is_series,
                extra_data={
                    "collaps_id": collaps_id,
                    "iframe_url": item.get("iframe_url"),
                    "voices": item.get("voiceActing") or [],
                }
            ))

        return items

    def _fetch_embed_html(self, kp_id: Optional[str] = None, collaps_id: Optional[str] = None) -> tuple[Optional[str], Optional[str]]:
        """Fetches the Collaps/Namy player HTML across active mirrors. Returns (html_text, embed_url)."""
        urls_to_try = []
        if kp_id and str(kp_id).isdigit():
            for mirror in self.EMBED_MIRRORS:
                urls_to_try.append((f"{mirror}/embed/kp/{kp_id}", mirror))
        if collaps_id and str(collaps_id).isdigit():
            for mirror in self.EMBED_MIRRORS:
                urls_to_try.append((f"{mirror}/embed/movie/{collaps_id}", mirror))

        for url, mirror in urls_to_try:
            try:
                r = requests.get(url, headers={
                    "User-Agent": self.headers["User-Agent"],
                    "Referer": f"{mirror}/"
                }, timeout=6)
                if r.status_code == 200 and ("makePlayer" in r.text or ".m3u8" in r.text):
                    r.encoding = "utf-8"
                    return r.text, url
            except Exception:
                continue
        return None, None

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
        kp_id = None
        collaps_id = None

        if media_str.startswith("collaps_"):
            collaps_id = media_str.replace("collaps_", "")
        elif media_str.isdigit():
            kp_id = media_str

        html_text, active_embed_url = self._fetch_embed_html(kp_id=kp_id, collaps_id=collaps_id)

        # Fallback by title if KP ID was not found or mismatched on Collaps (e.g. KP 5664825 -> 10372988)
        if not html_text and title:
            matches = self.search(title, year=year)
            for m in matches[:3]:
                m_kp = m.kinopoisk_id
                m_cid = m.extra_data.get("collaps_id")
                html_text, active_embed_url = self._fetch_embed_html(kp_id=m_kp, collaps_id=m_cid)
                if html_text:
                    kp_id = m_kp or kp_id
                    collaps_id = m_cid or collaps_id
                    break

        result = StreamResult(
            source_name=self.name,
            media_id=kp_id or collaps_id or media_str,
            title=title or f"Collaps Stream ({kp_id or collaps_id or media_str})",
            embed_url=active_embed_url,
            streams=[],
            audio_tracks=[],
            subtitles=[],
            seasons=[]
        )

        if not html_text:
            result.error = "Media not found on Collaps/Delivembd"
            return result

        referer_base = "https://api.namy.ws/"
        if active_embed_url:
            parsed_u = urllib.parse.urlparse(active_embed_url)
            referer_base = f"{parsed_u.scheme}://{parsed_u.netloc}/"

        stream_headers = {
            "User-Agent": self.headers["User-Agent"],
            "Referer": referer_base,
            "Origin": referer_base.rstrip("/")
        }

        try:
            # 1. Check if it's a series with seasons: [...] in makePlayer
            seasons_idx = html_text.find("seasons:")
            if seasons_idx != -1:
                arr_start = html_text.find("[", seasons_idx)
                if arr_start != -1:
                    seasons_data, _ = json.JSONDecoder().raw_decode(html_text, arr_start)
                    if isinstance(seasons_data, list) and seasons_data:
                        target_s = int(season) if season else int(seasons_data[0].get("season", 1))
                        target_e = int(episode) if episode else 1

                        chosen_ep_obj = None
                        for s_obj in seasons_data:
                            s_num = int(s_obj.get("season", 1))
                            ep_infos = []
                            for ep_obj in s_obj.get("episodes", []):
                                ep_str = str(ep_obj.get("episode", "1"))
                                ep_num = int(ep_str) if ep_str.isdigit() else 1
                                ep_infos.append(EpisodeItem(
                                    episode_id=ep_num,
                                    title=ep_obj.get("title") or f"Серия {ep_num}",
                                    season_id=s_num
                                ))
                                if s_num == target_s and ep_num == target_e:
                                    chosen_ep_obj = ep_obj
                            result.seasons.append(SeasonItem(
                                season_id=s_num,
                                title=f"Сезон {s_num}",
                                episodes=ep_infos
                            ))

                        # Fallback to first available episode if target_s/target_e wasn't found
                        if not chosen_ep_obj and seasons_data[0].get("episodes"):
                            chosen_ep_obj = seasons_data[0]["episodes"][0]

                        if chosen_ep_obj:
                            hls_url = chosen_ep_obj.get("hls")
                            audio_obj = chosen_ep_obj.get("audio") or {}
                            voice_names = [n for n in (audio_obj.get("names") or []) if n]
                            voice_label = ", ".join(voice_names[:2]) if voice_names else "Русская озвучка"

                            for idx_v, v_name in enumerate(voice_names):
                                result.audio_tracks.append(AudioTrack(
                                    id=str(idx_v),
                                    name=v_name,
                                    is_default=(idx_v == 0)
                                ))

                            if hls_url:
                                result.streams.append(VideoStream(
                                    quality=f"1080p / Auto HLS ({voice_label})",
                                    url=hls_url,
                                    stream_type="hls",
                                    headers=stream_headers
                                ))

                            for cc in (chosen_ep_obj.get("cc") or []):
                                if cc.get("url"):
                                    result.subtitles.append(SubtitleTrack(
                                        language=cc.get("name") or "ru",
                                        url=cc["url"]
                                    ))

            # 2. If movie (or no seasons parsed), extract from source: { hls: "...", audio: {...} }
            if not result.streams:
                hls_m = re.search(r'hls:\s*["\'](https?://[^"\']+\.m3u8[^"\']*)["\']', html_text)
                if hls_m:
                    hls_url = hls_m.group(1)
                    voice_names = []
                    audio_m = re.search(r'audio:\s*(\{"names":\[.*?\]\s*(?:,\s*"order":\[.*?\])?\})', html_text)
                    if audio_m:
                        try:
                            a_data = json.loads(audio_m.group(1))
                            voice_names = [n for n in (a_data.get("names") or []) if n]
                        except Exception:
                            pass

                    for idx_v, v_name in enumerate(voice_names):
                        result.audio_tracks.append(AudioTrack(
                            id=str(idx_v),
                            name=v_name,
                            is_default=(idx_v == 0)
                        ))

                    voice_label = voice_names[0] if voice_names else "Multi-Audio"
                    result.streams.append(VideoStream(
                        quality=f"1080p / Auto HLS ({voice_label})",
                        url=hls_url,
                        stream_type="hls",
                        headers=stream_headers
                    ))

            # 3. Also provide the interactive Collaps iframe player as a secondary option
            if active_embed_url:
                iframe_url = active_embed_url
                if season and episode:
                    sep = "&" if "?" in iframe_url else "?"
                    iframe_url = f"{iframe_url}{sep}season={season}&episode={episode}"
                result.embed_url = iframe_url
                result.streams.append(VideoStream(
                    quality="Auto (Collaps Плеер)",
                    url=iframe_url,
                    stream_type="iframe",
                    headers=stream_headers
                ))
        except Exception as e:
            result.error = str(e)

        return result

    def canary_test(self) -> CanaryReport:
        start_t = time.time()
        test_url = f"{self.EMBED_MIRRORS[0]}/embed/kp/301"  # Matrix
        try:
            res = requests.get(test_url, headers=self.headers, timeout=6)
            latency = (time.time() - start_t) * 1000

            if res.status_code != 200:
                return CanaryReport(
                    source_name=self.name,
                    is_active=False,
                    status="CHANGED / BROKEN",
                    latency_ms=latency,
                    message=f"Collaps/Delivembd returned HTTP {res.status_code}.",
                    needs_rework=True,
                    endpoint_tested=test_url,
                    last_tested=time.time()
                )

            m3u8_found = len(re.findall(r'\.m3u8', res.text))
            if m3u8_found == 0:
                return CanaryReport(
                    source_name=self.name,
                    is_active=False,
                    status="DEGRADED",
                    latency_ms=latency,
                    message="Collaps page loads, but no direct .m3u8 streams extracted.",
                    needs_rework=True,
                    endpoint_tested=test_url,
                    last_tested=time.time()
                )

            return CanaryReport(
                source_name=self.name,
                is_active=True,
                status="OK",
                latency_ms=latency,
                message=f"Collaps/Delivembd fully operational! Direct HLS master.m3u8 active ({latency:.0f}ms).",
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
                message=f"Collaps/Delivembd connection error: {str(e)}",
                needs_rework=True,
                endpoint_tested=test_url,
                last_tested=time.time()
            )
