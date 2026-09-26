"""
Anilibria Source Adapter using wwnd.space API mirror.
Reverse-engineered from LazyMedia Deluxe (anilibria provider).
Provides direct, universal HLS (.m3u8) streams for anime releases (1080p, 720p, 480p).
No authorization required, universal CDN without IP lock.
"""
import time
import urllib.parse
from typing import List, Optional, Dict, Any
import requests

from .base import (
    BaseSource, MediaItem, StreamResult, VideoStream, AudioTrack,
    CanaryReport, SeasonItem, EpisodeItem
)


class AnilibriaSource(BaseSource):
    name = "anilibria"
    display_name = "AniLibria"
    source_type = "portal"

    API_URL = "https://wwnd.space/public/api/index.php"

    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Content-Type": "application/x-www-form-urlencoded"
        })

    def search(self, query: str, year: Optional[int] = None, kp_id: Optional[str] = None) -> List[MediaItem]:
        clean_q = query.strip()
        if not clean_q:
            return []

        payload = {
            "query": "search",
            "search": clean_q
        }

        items: List[MediaItem] = []
        try:
            r = self.session.post(self.API_URL, data=payload, timeout=6)
            if r.status_code != 200:
                return []

            data = r.json()
            if not data.get("status"):
                return []

            releases = data.get("data", [])
            for rel in releases:
                rel_id = str(rel.get("id", ""))
                if not rel_id:
                    continue

                names = rel.get("names", [])
                ru_title = names[0] if len(names) > 0 and names[0] else ""
                en_title = names[1] if len(names) > 1 and names[1] else None

                item_year_raw = rel.get("year")
                try:
                    item_year = int(item_year_raw) if item_year_raw else None
                except (ValueError, TypeError):
                    item_year = None

                if item_year and item_year <= 0:
                    item_year = None

                if year and item_year:
                    try:
                        y_int = int(year)
                        if abs(item_year - y_int) > 1:
                            continue
                    except (ValueError, TypeError):
                        pass

                poster_path = rel.get("poster", "")
                poster = f"https://wwnd.space{poster_path}" if poster_path.startswith("/") else poster_path

                series_str = rel.get("series", "")
                ep_info = f"{series_str} сер." if series_str else None

                genres = rel.get("genres", [])
                status_text = rel.get("status", "")

                items.append(MediaItem(
                    id=rel_id,
                    source_name=self.name,
                    title=ru_title or (en_title or clean_q),
                    original_title=en_title,
                    year=item_year,
                    is_series=True,
                    poster=poster,
                    genres=genres,
                    episodes_info=ep_info,
                    extra_data={
                        "code": rel.get("code"),
                        "status": status_text,
                        "voices": rel.get("voices", [])
                    }
                ))
        except Exception as e:
            print(f"[AniLibria] Search error: {e}")

        return items

    def get_streams(
        self,
        media_id: str,
        season: Optional[int] = None,
        episode: Optional[int] = None,
        audio_id: Optional[str] = None
    ) -> StreamResult:
        rel_id = str(media_id).replace("anilibria_", "").strip()
        payload = {
            "query": "release",
            "id": rel_id
        }

        try:
            r = self.session.post(self.API_URL, data=payload, timeout=8)
            if r.status_code != 200:
                return StreamResult(
                    source_name=self.name,
                    media_id=media_id,
                    title="AniLibria",
                    error=f"API returned HTTP {r.status_code}"
                )

            data = r.json()
            if not data.get("status"):
                return StreamResult(
                    source_name=self.name,
                    media_id=media_id,
                    title="AniLibria",
                    error="Release data not found"
                )

            rel_data = data.get("data", {})
            playlist = rel_data.get("playlist", [])
            names = rel_data.get("names", [])
            title = names[0] if names else "AniLibria Stream"

            if not playlist:
                return StreamResult(
                    source_name=self.name,
                    media_id=media_id,
                    title=title,
                    error="No video playlist available for this release"
                )

            # Build episodes list
            ep_items: List[EpisodeItem] = []
            for ep_idx, ep in enumerate(playlist):
                ep_num = int(ep.get("id") or ep.get("ordinal") or (ep_idx + 1))
                ep_title = ep.get("name") or ep.get("title") or f"Серия {ep_num}"
                ep_items.append(EpisodeItem(episode_id=ep_num, title=ep_title, season_id=1))

            seasons_list = [SeasonItem(season_id=1, title="Сезон 1", episodes=ep_items)]

            # Target episode
            req_ep = episode or 1
            target_ep = None
            for ep in playlist:
                ep_num = int(ep.get("id") or ep.get("ordinal") or 1)
                if ep_num == req_ep:
                    target_ep = ep
                    break

            if not target_ep and playlist:
                target_ep = playlist[0]

            streams: List[VideoStream] = []
            quality_map = [
                ("fullhd", "1080p (AniLibria)"),
                ("hd", "720p (AniLibria)"),
                ("sd", "480p (AniLibria)")
            ]

            headers = {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Referer": "https://wwnd.space/"
            }

            for q_key, q_label in quality_map:
                u = target_ep.get(q_key)
                if u and isinstance(u, str) and u.startswith("http"):
                    streams.append(VideoStream(
                        quality=q_label,
                        url=u,
                        stream_type="hls",
                        headers=headers,
                        is_premium=False
                    ))

            audio_tracks = [AudioTrack(id="1", name="Озвучка AniLibria (Многоголосый)", is_default=True)]

            return StreamResult(
                source_name=self.name,
                media_id=media_id,
                title=f"{title} - Серия {req_ep}",
                streams=streams,
                audio_tracks=audio_tracks,
                seasons=seasons_list
            )

        except Exception as e:
            return StreamResult(
                source_name=self.name,
                media_id=media_id,
                title="AniLibria",
                error=f"AniLibria error: {str(e)}"
            )

    def canary_test(self) -> CanaryReport:
        start_t = time.time()
        try:
            payload = {"query": "search", "search": "клинок"}
            r = self.session.post(self.API_URL, data=payload, timeout=6)
            latency = (time.time() - start_t) * 1000
            if r.status_code == 200 and r.json().get("status"):
                return CanaryReport(
                    source_name=self.name,
                    is_active=True,
                    status="OK",
                    latency_ms=latency,
                    message="AniLibria API online! Direct HLS streams (cache.libria.fun) available.",
                    needs_rework=False,
                    endpoint_tested=self.API_URL,
                    last_tested=time.time()
                )
            else:
                return CanaryReport(
                    source_name=self.name,
                    is_active=False,
                    status=f"HTTP {r.status_code}",
                    latency_ms=latency,
                    message=f"AniLibria returned HTTP {r.status_code}",
                    needs_rework=True,
                    endpoint_tested=self.API_URL,
                    last_tested=time.time()
                )
        except Exception as e:
            return CanaryReport(
                source_name=self.name,
                is_active=False,
                status="ERROR",
                latency_ms=(time.time() - start_t) * 1000,
                message=f"AniLibria connection error: {str(e)}",
                needs_rework=True,
                endpoint_tested=self.API_URL,
                last_tested=time.time()
            )
