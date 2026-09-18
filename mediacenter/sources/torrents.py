"""
Torrents (Rutor / TorrServe) Source Adapter.
Extracted from LazyMedia Deluxe and Кино HD torrent aggregators.
Provides high-bitrate 4K/1080p magnets and direct streaming via TorrServe engine.
"""
import time
import urllib.parse
import requests
from bs4 import BeautifulSoup
from typing import List, Optional
from .base import BaseSource, MediaItem, StreamResult, VideoStream, CanaryReport
from ..core.mirror_manager import mirror_manager

class TorrentsSource(BaseSource):
    name = "torrents"
    display_name = "Rutor & TorrServe"
    source_type = "torrent"

    TORRSERVE_HOST = "http://127.0.0.1:8090"

    def __init__(self):
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        }

    def _get_base(self) -> str:
        return mirror_manager.get_working_mirror("rutor") or "http://rutor.info"

    def search(self, query: str, year: Optional[int] = None, kp_id: Optional[str] = None, season: Optional[int] = None, episode: Optional[int] = None) -> List[MediaItem]:
        items = []
        base = self._get_base()
        clean_query = query
        if season:
            clean_query += f" s{season:02d}"
            if episode:
                clean_query += f"e{episode:02d}"
        elif year:
            clean_query += f" {year}"

        encoded = urllib.parse.quote(clean_query)
        url = f"{base}/search/0/0/0/0/{encoded}"

        try:
            res = requests.get(url, headers=self.headers, timeout=6)
            if res.status_code == 200:
                soup = BeautifulSoup(res.text, "html.parser")
                rows = soup.select("#index tr")
                for tr in rows[1:15]:  # Top 15 torrents
                    cols = tr.find_all("td")
                    if len(cols) >= 4:
                        # Extract title and link
                        link_tag = cols[1].find_all("a")
                        if len(link_tag) >= 2:
                            magnet_tag = link_tag[0]
                            title_tag = link_tag[1]
                            magnet = magnet_tag.get("href", "")
                            title = title_tag.text.strip()
                            size = cols[2].text.strip() if len(cols) > 2 else "N/A"
                            seeds = cols[3].text.strip() if len(cols) > 3 else "0"

                            if magnet.startswith("magnet:"):
                                items.append(MediaItem(
                                    id=magnet,
                                    source_name=self.name,
                                    title=title,
                                    year=year,
                                    description=f"Размер: {size} | Сиды: {seeds}",
                                    extra_data={
                                        "magnet": magnet,
                                        "size": size,
                                        "seeds": seeds,
                                        "stream_url": f"{self.TORRSERVE_HOST}/stream?link={urllib.parse.quote(magnet)}"
                                    }
                                ))
        except Exception:
            pass
        return items

    def get_streams(self, media_id: str, season: Optional[int] = None, episode: Optional[int] = None, audio_id: Optional[str] = None) -> StreamResult:
        magnet = media_id
        torr_stream = f"{self.TORRSERVE_HOST}/stream?link={urllib.parse.quote(magnet)}"

        return StreamResult(
            source_name=self.name,
            media_id=media_id,
            title="P2P Torrent Stream",
            streams=[
                VideoStream(
                    quality="Original Bitrate (TorrServe)",
                    url=torr_stream,
                    stream_type="hls",
                    headers={}
                )
            ],
            extra_data={"magnet": magnet}
        )

    def canary_test(self) -> CanaryReport:
        start_t = time.time()
        base = self._get_base()
        url = f"{base}/search/0/0/0/0/Matrix"
        try:
            res = requests.get(url, headers=self.headers, timeout=6)
            latency = (time.time() - start_t) * 1000

            if res.status_code != 200:
                return CanaryReport(
                    source_name=self.name,
                    is_active=False,
                    status="CHANGED / BROKEN",
                    latency_ms=latency,
                    message=f"Rutor tracker returned HTTP {res.status_code}. Domain may be blocked by ISP.",
                    needs_rework=True,
                    endpoint_tested=url,
                    last_tested=time.time()
                )

            soup = BeautifulSoup(res.text, "html.parser")
            magnets = soup.find_all("a", href=lambda h: h and h.startswith("magnet:"))
            if not magnets:
                return CanaryReport(
                    source_name=self.name,
                    is_active=False,
                    status="CHANGED / BROKEN",
                    latency_ms=latency,
                    message="Rutor search returned 0 magnets or HTML layout changed.",
                    needs_rework=True,
                    endpoint_tested=url,
                    last_tested=time.time()
                )

            return CanaryReport(
                source_name=self.name,
                is_active=True,
                status="OK",
                latency_ms=latency,
                message=f"Rutor Tracker fully online! Found {len(magnets)} torrent magnets with seeders.",
                needs_rework=False,
                endpoint_tested=url,
                last_tested=time.time()
            )
        except Exception as e:
            return CanaryReport(
                source_name=self.name,
                is_active=False,
                status="CHANGED / BROKEN",
                latency_ms=(time.time() - start_t) * 1000,
                message=f"Rutor connection failed: {str(e)}",
                needs_rework=True,
                endpoint_tested=url,
                last_tested=time.time()
            )
