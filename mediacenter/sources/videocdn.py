"""
VideoCDN / Allarknow Balancer Source Adapter.
Extracted from 'Кино HD' (361cc_fix.apk).
Upgraded from obsolete khd.videocdn.pw to the active Allarknow/Apbugall VideoCDN network!
Supports search by title, Kinopoisk ID, and delivers responsive HD player embeds.
"""
import time
import urllib.parse
import requests
from typing import List, Optional
from .base import BaseSource, MediaItem, StreamResult, VideoStream, CanaryReport

class VideoCDNSource(BaseSource):
    name = "videocdn"
    display_name = "VideoCDN (Allarknow)"
    source_type = "balancer"

    API_BASE = "https://api.apbugall.org"
    TOKEN = "bfbb025034f40bbe51d8d640f8d182"

    def __init__(self):
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        }

    def search(self, query: str, year: Optional[int] = None, kp_id: Optional[str] = None) -> List[MediaItem]:
        items = []
        try:
            if kp_id:
                url = f"{self.API_BASE}/?token={self.TOKEN}&kp={kp_id}"
            else:
                encoded = urllib.parse.quote(query)
                url = f"{self.API_BASE}/?token={self.TOKEN}&name={encoded}"

            res = requests.get(url, headers=self.headers, timeout=6)
            if res.status_code == 200:
                data = res.json()
                raw_data = data.get("data", [])
                if isinstance(raw_data, dict):
                    raw_data = [raw_data]

                for r in raw_data:
                    item_year = r.get("year")
                    if year and item_year and abs(int(year) - int(item_year)) > 1:
                        continue

                    title = r.get("name") or r.get("original_name") or "Без названия"
                    kp = str(r.get("id_kp") or "")
                    items.append(MediaItem(
                        id=str(r.get("token_movie") or kp),
                        source_name=self.name,
                        title=title,
                        original_title=r.get("original_name"),
                        year=int(item_year) if item_year else None,
                        poster=r.get("poster"),
                        description=r.get("description"),
                        rating_kp=float(r.get("rating_kp", 0) or 0) or None,
                        rating_imdb=float(r.get("rating_imdb", 0) or 0) or None,
                        kinopoisk_id=kp,
                        extra_data={
                            "iframe": r.get("iframe"),
                            "quality": r.get("quality", "1080p"),
                            "translation": r.get("translation", "")
                        }
                    ))
        except Exception:
            pass
        return items

    def get_streams(self, media_id: str, season: Optional[int] = None, episode: Optional[int] = None, audio_id: Optional[str] = None) -> StreamResult:
        embed_url = None
        # Check if media_id is KP ID or token_movie
        try:
            if media_id.isdigit():
                url = f"{self.API_BASE}/?token={self.TOKEN}&kp={media_id}"
            else:
                url = f"{self.API_BASE}/?token={self.TOKEN}&token_movie={media_id}"

            res = requests.get(url, headers=self.headers, timeout=5)
            if res.status_code == 200:
                data = res.json()
                raw = data.get("data", {})
                if isinstance(raw, list) and raw:
                    raw = raw[0]
                embed_url = raw.get("iframe")
        except Exception:
            pass

        if not embed_url:
            embed_url = f"https://bayas.allarknow.online/?token={self.TOKEN}&kp={media_id}"

        return StreamResult(
            source_name=self.name,
            media_id=media_id,
            title=f"VideoCDN HD Stream ({media_id})",
            embed_url=embed_url,
            streams=[
                VideoStream(
                    quality="1080p HD (VideoCDN)",
                    url=embed_url,
                    stream_type="iframe",
                    headers=self.headers
                )
            ]
        )

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
                    message=f"VideoCDN (Allarknow) API returned HTTP {res.status_code}.",
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
                    message="VideoCDN (Allarknow) API response status != success.",
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
                message=f"VideoCDN (Allarknow) fully operational! Active player: {iframe[:45]}...",
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
                message=f"VideoCDN connection error: {str(e)}",
                needs_rework=True,
                endpoint_tested=test_url,
                last_tested=time.time()
            )
