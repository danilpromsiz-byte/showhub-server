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
                        magnet = ""
                        title = ""
                        for a_tag in cols[1].find_all("a"):
                            href = a_tag.get("href", "")
                            if href.startswith("magnet:"):
                                magnet = href
                            elif href.startswith("/torrent/"):
                                title = a_tag.text.strip()
                        
                        if not magnet:
                            continue
                        if not title:
                            title = cols[1].text.strip()

                        if len(cols) >= 5:
                            size = cols[3].text.strip()
                            green_span = cols[4].select_one("span.green")
                            seeds = green_span.text.strip() if green_span else (cols[4].text.strip().split()[0] if cols[4].text.strip() else "0")
                        elif len(cols) >= 4:
                            size = cols[2].text.strip()
                            seeds = cols[3].text.strip()
                        else:
                            size = "N/A"
                            seeds = "0"

                        # Skip audiobooks / music albums
                        t_low = title.lower()
                        if any(bad in t_low for bad in ["mp3", "flac", "lossless", "аудиокнига", "soundtrack", "ost"]):
                            continue

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

        # If searching with year yielded no video results, retry without year and match year in title
        if not items and year:
            raw_items = self.search(query, year=None, kp_id=kp_id, season=season, episode=episode)
            if raw_items:
                y_str = str(year)
                matched = [it for it in raw_items if y_str in it.title]
                if matched:
                    items = matched

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
