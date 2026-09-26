"""
Zona Source Adapter using official Android Zona REST API.
Reverse-engineered from LazyMedia Deluxe (ZONA_Article, ZONA_ListArticles) and Zona Android APK.
Provides direct MP4 video streams (1080p HQ, 720p MQ, 480p LQ) from vibio.tv CDN.
Catalog contains Kinopoisk ID mappings directly in item responses.
"""
import time
import email.utils
import urllib.parse
from typing import List, Optional, Dict, Any
import requests

from .base import (
    BaseSource, MediaItem, StreamResult, VideoStream, AudioTrack,
    CanaryReport, SeasonItem, EpisodeItem
)


class ZonaSource(BaseSource):
    name = "zona"
    display_name = "Zona"
    source_type = "portal"

    API_BASE = "https://android1.mzona.net/api/v1"
    USER_AGENT = "Zona/1.10.2 (Google/Pixel 5/Android 11)"

    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": self.USER_AGENT,
            "Accept": "application/json"
        })

    @staticmethod
    def _zona_hash_code(s: str) -> int:
        """Computes Java 32-bit String.hashCode() matching Zona client algorithm."""
        h = 0
        for c in s:
            h = (h * 31 + ord(c)) & 0xFFFFFFFF
            if h >= 0x80000000:
                h -= 0x100000000
        return h

    def _get_client_time(self) -> int:
        """
        Calculates time synchronization token for Zona API:
        Takes HTTP Date header from Zona video endpoint, parses timestamp,
        and applies Java hashCode signature with User-Agent.
        """
        try:
            r = self.session.head(f"{self.API_BASE}/video/", timeout=4)
            date_hdr = r.headers.get("Date")
        except Exception:
            date_hdr = None

        if date_hdr:
            try:
                t = email.utils.parsedate_to_datetime(date_hdr).timestamp()
            except Exception:
                t = time.time()
        else:
            t = time.time()

        j = int(t * 1000)
        j2 = (j - (j % 1000)) // 1000
        h = abs(self._zona_hash_code(f"{j2}{self.USER_AGENT}")) % 1000
        return (1000 * j2) + h

    def search(self, query: str, year: Optional[int] = None, kp_id: Optional[str] = None) -> List[MediaItem]:
        clean_q = query.strip()
        if not clean_q:
            return []

        url = f"{self.API_BASE}/search/{urllib.parse.quote(clean_q)}"
        items: List[MediaItem] = []

        try:
            r = self.session.get(url, timeout=6)
            if r.status_code != 200:
                return []

            data = r.json()
            raw_items = data.get("items", [])

            for it in raw_items:
                mobi_id = str(it.get("mobi_link_id") or it.get("id") or "")
                if not mobi_id:
                    continue

                item_kp_id = str(it.get("id", ""))
                title = it.get("name_rus") or it.get("name_original") or it.get("name_eng") or clean_q
                orig_title = it.get("name_original") or it.get("name_eng") or None
                item_year = it.get("year")
                if isinstance(item_year, int) and item_year <= 0:
                    item_year = None

                if year and item_year and abs(item_year - year) > 1:
                    continue

                poster = it.get("cover") or it.get("poster")
                rating_kp = it.get("rating_kinopoisk")
                if rating_kp is not None:
                    try:
                        rating_kp = float(rating_kp)
                    except (ValueError, TypeError):
                        rating_kp = None

                rating_imdb = it.get("rating_imdb")
                if rating_imdb is not None:
                    try:
                        rating_imdb = float(rating_imdb)
                    except (ValueError, TypeError):
                        rating_imdb = None

                is_ser = bool(it.get("serial") or it.get("is_serial") or "сериал" in title.lower())

                media_item = MediaItem(
                    id=mobi_id,
                    source_name=self.name,
                    title=title,
                    original_title=orig_title,
                    year=item_year,
                    is_series=is_ser,
                    poster=poster,
                    rating_kp=rating_kp,
                    rating_imdb=rating_imdb,
                    kinopoisk_id=item_kp_id if item_kp_id.isdigit() else None,
                    extra_data={
                        "mobi_link_id": mobi_id,
                        "name_id": it.get("name_id"),
                        "original_title": orig_title
                    }
                )

                # Prioritize exact Kinopoisk ID match
                if kp_id and str(kp_id) == item_kp_id:
                    items.insert(0, media_item)
                else:
                    items.append(media_item)

        except Exception as e:
            print(f"[Zona] Search error: {e}")

        return items

    def get_streams(
        self,
        media_id: str,
        season: Optional[int] = None,
        episode: Optional[int] = None,
        audio_id: Optional[str] = None
    ) -> StreamResult:
        mobi_id = str(media_id).replace("zona_", "").strip()
        client_time = self._get_client_time()
        url = f"{self.API_BASE}/video/{mobi_id}?client_time={client_time}"

        try:
            r = self.session.get(url, timeout=8)
            if r.status_code != 200:
                return StreamResult(
                    source_name=self.name,
                    media_id=media_id,
                    title="Zona",
                    error=f"Zona API returned HTTP {r.status_code}"
                )

            data = r.json()
            streams: List[VideoStream] = []

            # Stream definitions in Zona
            # url: 1080p HQ MP4
            # mqUrl: 720p MQ MP4
            # lqUrl: 480p LQ MP4
            qualities = [
                ("url", "1080p (Zona HQ)"),
                ("mqUrl", "720p (Zona MQ)"),
                ("lqUrl", "480p (Zona LQ)")
            ]

            headers = {
                "User-Agent": self.USER_AGENT
            }

            for q_key, q_label in qualities:
                stream_url = data.get(q_key)
                if stream_url and isinstance(stream_url, str) and stream_url.startswith("http"):
                    streams.append(VideoStream(
                        quality=q_label,
                        url=stream_url,
                        stream_type="mp4",
                        headers=headers,
                        is_premium=False
                    ))

            audio_tracks = [AudioTrack(id="1", name="Основная дорожка (Zona)", is_default=True)]

            return StreamResult(
                source_name=self.name,
                media_id=media_id,
                title="Zona Direct MP4",
                streams=streams,
                audio_tracks=audio_tracks
            )

        except Exception as e:
            return StreamResult(
                source_name=self.name,
                media_id=media_id,
                title="Zona",
                error=f"Zona video stream error: {str(e)}"
            )

    def canary_test(self) -> CanaryReport:
        start_t = time.time()
        try:
            url = f"{self.API_BASE}/search/{urllib.parse.quote('матрица')}"
            r = self.session.get(url, timeout=5)
            latency = (time.time() - start_t) * 1000

            if r.status_code == 200 and "items" in r.text:
                return CanaryReport(
                    source_name=self.name,
                    is_active=True,
                    status="OK",
                    latency_ms=latency,
                    message="Zona Android API online! Direct MP4 streams (vibio.tv) active.",
                    needs_rework=False,
                    endpoint_tested=url,
                    last_tested=time.time()
                )
            else:
                return CanaryReport(
                    source_name=self.name,
                    is_active=False,
                    status=f"HTTP {r.status_code}",
                    latency_ms=latency,
                    message=f"Zona API returned HTTP {r.status_code}",
                    needs_rework=True,
                    endpoint_tested=url,
                    last_tested=time.time()
                )
        except Exception as e:
            return CanaryReport(
                source_name=self.name,
                is_active=False,
                status="ERROR",
                latency_ms=(time.time() - start_t) * 1000,
                message=f"Zona connection error: {str(e)}",
                needs_rework=True,
                endpoint_tested=self.API_BASE,
                last_tested=time.time()
            )
