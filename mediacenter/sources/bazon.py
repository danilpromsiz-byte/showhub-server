"""
Bazon Balancer Source Adapter.
Extracted from 'Кино HD' (361cc_fix.apk).
Supports search by title and Kinopoisk ID.
Yields responsive embed player and direct streams.
"""
import time
import requests
from typing import List, Optional, Dict, Any
from .base import BaseSource, MediaItem, StreamResult, VideoStream, CanaryReport

class BazonSource(BaseSource):
    name = "bazon"
    display_name = "Bazon CDN"
    source_type = "balancer"

    API_BASE = "https://bazon.cc/api"
    TOKEN = "c118eb5f8d36565b2b08b5342da97f79"

    def __init__(self):
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Referer": "https://bazon.cc",
        }

    def search(self, query: str, year: Optional[int] = None, kp_id: Optional[str] = None) -> List[MediaItem]:
        items = []
        try:
            if kp_id:
                url = f"{self.API_BASE}/search?token={self.TOKEN}&kp={kp_id}"
            else:
                url = f"{self.API_BASE}/search?token={self.TOKEN}&title={query}"

            res = requests.get(url, headers=self.headers, timeout=6)
            if res.status_code == 200:
                data = res.json()
                results = data.get("results", [])
                for r in results:
                    info = r.get("info", {})
                    # Year filter if provided
                    item_year = None
                    try:
                        item_year = int(info.get("year", 0)) if info.get("year") else None
                    except ValueError:
                        pass

                    if year and item_year and abs(year - item_year) > 1:
                        continue

                    title = info.get("rus") or r.get("title") or info.get("orig") or "Без названия"
                    vkp_raw = info.get("rating", {}).get("vote_num_kp")
                    vimdb_raw = info.get("rating", {}).get("vote_num_imdb")
                    vkp = int(vkp_raw) if (vkp_raw and str(vkp_raw).isdigit()) else None
                    vimdb = int(vimdb_raw) if (vimdb_raw and str(vimdb_raw).isdigit()) else None

                    items.append(MediaItem(
                        id=kp if kp else str(r.get("id")),
                        source_name=self.name,
                        title=title,
                        original_title=info.get("orig"),
                        year=item_year,
                        is_series=bool(int(r.get("serial", 0))),
                        poster=info.get("poster"),
                        description=info.get("description"),
                        rating_kp=float(info.get("rating", {}).get("rating_kp", 0) or 0) or None,
                        rating_imdb=float(info.get("rating", {}).get("rating_imdb", 0) or 0) or None,
                        vote_num_kp=vkp,
                        vote_num_imdb=vimdb,
                        kinopoisk_id=str(r.get("kinopoisk_id") or ""),
                        extra_data={
                            "embed": r.get("iframe") or r.get("link") or f"https://bazon.cc/embed/kp/{r.get('kinopoisk_id')}",
                            "max_qual": r.get("max_qual", "1080"),
                            "translation": r.get("translation", ""),
                            "vote_num_kp": vkp,
                            "vote_num_imdb": vimdb
                        }
                    ))
        except Exception:
            pass
        return items

    def get_catalog(self, category: str = "all", genre: Optional[str] = None, page: int = 1) -> List[MediaItem]:
        items = []
        try:
            types_to_fetch = ["film"]
            b_genre = genre if (genre and genre != "all") else ""
            if category == "series":
                types_to_fetch = ["serial"]
            elif category == "cartoons":
                types_to_fetch = ["film"]
                b_genre = "мультфильм" if not b_genre else f"{b_genre},мультфильм"
            elif category == "anime":
                types_to_fetch = ["serial", "film"]
                b_genre = "аниме" if not b_genre else f"{b_genre},аниме"
            elif category == "movies":
                types_to_fetch = ["film"]
            elif category == "all":
                types_to_fetch = ["film", "serial"]

            raw_results = []
            for b_type in types_to_fetch:
                url = f"{self.API_BASE}/json?token={self.TOKEN}&type={b_type}&page={page}"
                try:
                    res = requests.get(url, headers=self.headers, timeout=5)
                    if res.status_code == 200:
                        raw_results.extend(res.json().get("results", []))
                except Exception:
                    pass

            for r in raw_results:
                    info = r.get("info", {})
                    item_year = None
                    try:
                        item_year = int(info.get("year", 0)) if info.get("year") else None
                    except ValueError:
                        pass

                    title = info.get("rus") or r.get("title") or info.get("orig") or "Без названия"
                    kp = str(r.get("kinopoisk_id") or "")
                    vkp_raw = info.get("rating", {}).get("vote_num_kp")
                    vimdb_raw = info.get("rating", {}).get("vote_num_imdb")
                    vkp = int(vkp_raw) if (vkp_raw and str(vkp_raw).isdigit()) else None
                    vimdb = int(vimdb_raw) if (vimdb_raw and str(vimdb_raw).isdigit()) else None
                    raw_date = r.get("date")
                    date_ts = int(raw_date) if (raw_date and str(raw_date).isdigit()) else None

                    is_ser = bool(int(r.get("serial", 0)))
                    ep_info = None
                    if is_ser:
                        last_ep = r.get("last_episode") or info.get("episode")
                        last_s = r.get("last_season") or info.get("season")
                        if last_s and last_ep:
                            ep_info = f"{last_s} сезон, {last_ep} серия"
                        elif last_ep:
                            ep_info = f"{last_ep} серия"

                    items.append(MediaItem(
                        id=kp if kp else str(r.get("id")),
                        source_name=self.name,
                        title=title,
                        original_title=info.get("orig"),
                        year=item_year,
                        is_series=is_ser,
                        poster=info.get("poster"),
                        description=info.get("description"),
                        rating_kp=float(info.get("rating", {}).get("rating_kp", 0) or 0) or None,
                        rating_imdb=float(info.get("rating", {}).get("rating_imdb", 0) or 0) or None,
                        vote_num_kp=vkp,
                        vote_num_imdb=vimdb,
                        kinopoisk_id=kp,
                        date_added=date_ts,
                        episodes_info=ep_info,
                        extra_data={
                            "embed": r.get("iframe") or r.get("link") or f"https://bazon.cc/embed/kp/{kp}",
                            "genre": info.get("genre", ""),
                            "director": info.get("director", ""),
                            "actors": info.get("actors", ""),
                            "country": info.get("country", ""),
                            "time": info.get("time", ""),
                            "vote_num_kp": vkp,
                            "vote_num_imdb": vimdb,
                            "date": raw_date,
                            "premiere": info.get("premiere", "")
                        }
                    ))
        except Exception:
            pass
        return items

    def get_details(self, media_id: str) -> Optional[Dict[str, Any]]:
        try:
            url = f"{self.API_BASE}/search?token={self.TOKEN}&kp={media_id}"
            res = requests.get(url, headers=self.headers, timeout=5)
            if res.status_code == 200:
                results = res.json().get("results", [])
                if results:
                    first = results[0]
                    info = first.get("info", {})
                    genres = [g.strip() for g in (info.get("genre") or "").split(",") if g.strip()]
                    kp_v = info.get("rating", {}).get("vote_num_kp")
                    imdb_v = info.get("rating", {}).get("vote_num_imdb")
                    return {
                        "description": info.get("description"),
                        "rating_kp": float(info.get("rating", {}).get("rating_kp", 0) or 0) or None,
                        "rating_imdb": float(info.get("rating", {}).get("rating_imdb", 0) or 0) or None,
                        "vote_num_kp": int(kp_v) if kp_v and str(kp_v).isdigit() else None,
                        "vote_num_imdb": int(imdb_v) if imdb_v and str(imdb_v).isdigit() else None,
                        "genres": genres,
                        "director": info.get("director"),
                        "actors": info.get("actors"),
                        "country": info.get("country"),
                        "year": int(info.get("year", 0)) if info.get("year") else None,
                        "poster": info.get("poster")
                    }
        except Exception:
            pass
        return None

    def get_streams(self, media_id: str, season: Optional[int] = None, episode: Optional[int] = None, audio_id: Optional[str] = None) -> StreamResult:
        embed_url = f"https://bazon.cc/embed/kp/{media_id}" if len(media_id) < 8 else f"https://bazon.cc/embed/{media_id}"
        
        # Query details by ID to get exact embed
        try:
            url = f"{self.API_BASE}/search?token={self.TOKEN}&kp={media_id}"
            res = requests.get(url, headers=self.headers, timeout=5)
            if res.status_code == 200:
                results = res.json().get("results", [])
                if results:
                    first = results[0]
                    embed_url = first.get("iframe") or first.get("link") or embed_url
        except Exception:
            pass

        return StreamResult(
            source_name=self.name,
            media_id=media_id,
            title=f"Bazon Playback ({media_id})",
            embed_url=embed_url,
            streams=[
                VideoStream(
                    quality="1080p (Embed)",
                    url=embed_url,
                    stream_type="iframe",
                    headers=self.headers
                )
            ]
        )

    def canary_test(self) -> CanaryReport:
        start_t = time.time()
        last_err = ""
        for base in ["https://bazon.cc/api", "https://v2.bazon.site/api"]:
            for token in ["c118eb5f8d36565b2b08b5342da97f79", "8488e0b2067756f2e82f5b82fb2ec686"]:
                endpoint = f"{base}/search?token=***&title=Matrix"
                try:
                    url = f"{base}/search?token={token}&title=Matrix"
                    res = requests.get(url, headers=self.headers, timeout=5)
                    latency = (time.time() - start_t) * 1000
                    if res.status_code == 200:
                        data = res.json()
                        if "results" in data and data["results"]:
                            self.API_BASE = base
                            self.TOKEN = token
                            return CanaryReport(
                                source_name=self.name,
                                is_active=True,
                                status="OK",
                                latency_ms=latency,
                                message=f"Bazon API fully operational ({base}). Returns embeds and metadata.",
                                needs_rework=False,
                                endpoint_tested=endpoint,
                                last_tested=time.time()
                            )
                except Exception as e:
                    last_err = str(e)

        return CanaryReport(
            source_name=self.name,
            is_active=False,
            status="CHANGED / BROKEN",
            latency_ms=(time.time() - start_t) * 1000,
            message=f"Bazon connection failure: {last_err}",
            needs_rework=True,
            endpoint_tested="https://bazon.cc/api",
            last_tested=time.time()
        )
