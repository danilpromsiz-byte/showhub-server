"""
Torrents (Rutor / TorrServe) Source Adapter.
Extracted from LazyMedia Deluxe and Кино HD torrent aggregators.
Provides high-bitrate 4K/1080p magnets and direct streaming via TorrServe engine.
"""
import re
import time
import urllib.parse
import requests
from bs4 import BeautifulSoup
from typing import List, Optional, Tuple
from .base import BaseSource, MediaItem, StreamResult, VideoStream, CanaryReport
from ..core.mirror_manager import mirror_manager

def parse_torrent_quality(title: str, size: str = "", seeds: str = "0") -> Tuple[str, str, int]:
    """
    Parses torrent release title to extract resolution, HDR/DV, Remux/Rip,
    and returns (display_label, quality_tier, quality_rank).
    """
    t_low = title.lower()

    if any(k in t_low for k in ["2160", "4k", "uhd"]):
        res = "4K"
        rank = 2160
    elif any(k in t_low for k in ["1440", "2k", "qhd"]):
        res = "2K"
        rank = 1440
    elif "1080" in t_low:
        res = "1080p"
        rank = 1080
    elif "720" in t_low:
        res = "720p"
        rank = 720
    elif "480" in t_low:
        res = "480p"
        rank = 480
    else:
        res = "HD"
        rank = 720

    feats = []
    if any(k in t_low for k in ["dolby vision", "dovi", "dv"]):
        feats.append("DV")
    elif "hdr" in t_low:
        feats.append("HDR")

    if "remux" in t_low:
        feats.append("Remux")
    elif any(k in t_low for k in ["bdrip", "bluray"]):
        feats.append("BDRip")
    elif any(k in t_low for k in ["web-dl", "webdl"]):
        feats.append("WEB-DL")

    feat_str = (" " + " ".join(feats)) if feats else ""
    s_clean = size.replace("\xa0", " ").strip() if size else ""
    seeds_clean = seeds.split()[0].replace("\xa0", "").strip() if seeds else "0"
    label = f"{res}{feat_str}"
    if s_clean and s_clean != "N/A":
        label += f" • {s_clean}"
    if seeds_clean and seeds_clean != "0":
        label += f" (S: {seeds_clean})"
    return label, res, rank

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
        clean_query = query.strip()
        if not clean_query:
            return []

        # For search, clean query works best across Russian trackers.
        # Trackers do not match "s01e01"; instead, they match title and have season info in release names.
        encoded = urllib.parse.quote(clean_query)
        url = f"{base}/search/0/0/0/0/{encoded}"

        try:
            res = requests.get(url, headers=self.headers, timeout=6)
            if res.status_code == 200:
                soup = BeautifulSoup(res.text, "html.parser")
                rows = soup.select("#index tr")
                for tr in rows[1:25]:  # Top 24 torrents
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

                        # Skip audiobooks / music albums / games / software
                        t_low = title.lower()
                        if any(bad in t_low for bad in ["mp3", "flac", "lossless", "аудиокнига", "soundtrack", "ost", "pc | repack", "repack от"]):
                            continue

                        quality_label, quality_tier, quality_rank = parse_torrent_quality(title, size, seeds)
                        # Clean slug for player URL (keep letters/digits, replace punctuation/slashes)
                        clean_title_part = re.sub(r'[\s/\\|:?*<>"+#]+', '_', title[:45]).strip('_') or "video"
                        stream_url = f"{self.TORRSERVE_HOST}/stream/{urllib.parse.quote(clean_title_part)}.mkv?link={urllib.parse.quote(magnet)}&play"

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
                                "quality_label": quality_label,
                                "quality_tier": quality_tier,
                                "quality_rank": quality_rank,
                                "stream_url": stream_url
                            }
                        ))
        except Exception:
            pass

        # If a specific season was requested, rank matching season releases first
        if season and items:
            s_patterns = [
                f"s{season:02d}", f"s{season}",
                f"{season} сезон", f"{season}-й сезон", f"{season}й сезон",
                f"сезон {season}", f"сезон: {season}"
            ]
            matching_season = [it for it in items if any(p in it.title.lower() for p in s_patterns)]
            if matching_season:
                # Keep matching season items at the top
                other_items = [it for it in items if it not in matching_season]
                items = matching_season + other_items

        # If year was specified for a movie, prioritize items containing that year
        elif year and items:
            y_str = str(year)
            matching_year = [it for it in items if y_str in it.title]
            if matching_year:
                other_items = [it for it in items if it not in matching_year]
                items = matching_year + other_items

        return items

    def get_streams(self, media_id: str, season: Optional[int] = None, episode: Optional[int] = None, audio_id: Optional[str] = None) -> StreamResult:
        magnet = media_id
        safe_slug = "video"
        torr_stream = f"{self.TORRSERVE_HOST}/stream/{safe_slug}.mkv?link={urllib.parse.quote(magnet)}&play"

        return StreamResult(
            source_name=self.name,
            media_id=media_id,
            title="P2P Torrent Stream",
            streams=[
                VideoStream(
                    quality="Original Bitrate (TorrServe)",
                    url=torr_stream,
                    stream_type="torrent",
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
