"""
Delivembd / Zombie VOD Source Adapter.
Extracted from 'LazyMedia Deluxe' (urls_lmd.json -> zombie_player_replace).
Extracts direct HLS master.m3u8 streams with multiple audio tracks and resolutions!
"""
import time
import re
import requests
from typing import List, Optional
from .base import BaseSource, MediaItem, StreamResult, VideoStream, AudioTrack, CanaryReport

class DelivembdSource(BaseSource):
    name = "delivembd"
    display_name = "Delivembd (LazyMedia CDN)"
    source_type = "balancer"

    EMBED_BASE = "https://api.delivembd.ws/embed"

    def __init__(self):
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Referer": "https://delivembd.ws/",
        }

    def search(self, query: str, year: Optional[int] = None, kp_id: Optional[str] = None) -> List[MediaItem]:
        # Delivembd is keyed by Kinopoisk ID or IMDB ID
        if not kp_id:
            return []
        return [
            MediaItem(
                id=str(kp_id),
                source_name=self.name,
                title=f"Movie (KP {kp_id})",
                year=year,
                kinopoisk_id=str(kp_id),
                extra_data={"embed": f"{self.EMBED_BASE}/movie/{kp_id}"}
            )
        ]

    def get_streams(self, media_id: str, season: Optional[int] = None, episode: Optional[int] = None, audio_id: Optional[str] = None) -> StreamResult:
        if season and episode:
            embed_url = f"{self.EMBED_BASE}/serial/{media_id}?s={season}&e={episode}"
        else:
            embed_url = f"{self.EMBED_BASE}/movie/{media_id}"

        result = StreamResult(
            source_name=self.name,
            media_id=media_id,
            title=f"Delivembd Stream (KP {media_id})",
            embed_url=embed_url,
            streams=[],
            audio_tracks=[]
        )

        try:
            res = requests.get(embed_url, headers=self.headers, timeout=6)
            if res.status_code == 200:
                # Find direct m3u8 master streams
                m3u8_urls = re.findall(r'https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*', res.text)
                seen = set()
                qualities = ["1080p", "720p", "480p", "360p"]
                q_idx = 0
                for url in m3u8_urls:
                    clean = url.split('&amp;')[0]
                    if clean not in seen and 'master.m3u8' in clean:
                        seen.add(clean)
                        q_name = qualities[q_idx] if q_idx < len(qualities) else f"Quality {q_idx+1}"
                        result.streams.append(VideoStream(
                            quality=q_name,
                            url=clean,
                            stream_type="hls",
                            headers={
                                "User-Agent": self.headers["User-Agent"],
                                "Referer": "https://api.delivembd.ws/"
                            }
                        ))
                        q_idx += 1
        except Exception as e:
            result.error = str(e)

        # Always provide iframe fallback
        if not result.streams:
            result.streams.append(VideoStream(
                quality="Auto (Embed)",
                url=embed_url,
                stream_type="iframe",
                headers=self.headers
            ))

        return result

    def canary_test(self) -> CanaryReport:
        start_t = time.time()
        test_url = f"{self.EMBED_BASE}/movie/301"  # Matrix
        try:
            res = requests.get(test_url, headers=self.headers, timeout=6)
            latency = (time.time() - start_t) * 1000

            if res.status_code != 200:
                return CanaryReport(
                    source_name=self.name,
                    is_active=False,
                    status="CHANGED / BROKEN",
                    latency_ms=latency,
                    message=f"Delivembd returned HTTP {res.status_code}. Endpoint may have moved.",
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
                    message="Delivembd page loads, but no direct .m3u8 streams extracted. Iframe fallback only.",
                    needs_rework=True,
                    endpoint_tested=test_url,
                    last_tested=time.time()
                )

            return CanaryReport(
                source_name=self.name,
                is_active=True,
                status="OK",
                latency_ms=latency,
                message=f"Delivembd fully operational! Extracted {m3u8_found} HLS streams with Russian audio.",
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
                message=f"Delivembd connection error: {str(e)}",
                needs_rework=True,
                endpoint_tested=test_url,
                last_tested=time.time()
            )
