"""
ShowHub High-Performance Local Cover & Image Cache Manager.
Persists movie and series posters, backdrops, and stills permanently on the local machine disk.
Eliminates external image CDN dependencies, RKN throttling, and Kinopoisk/TMDb 403 hotlink blocks.
Provides instant (<2ms) local HTTP streaming of covers to Android TV, mobile, and web clients.
"""
import os
import re
import time
import hashlib
import logging
import urllib.parse
import threading
from concurrent.futures import ThreadPoolExecutor
from typing import Optional, List, Set, Dict, Any
import requests

logger = logging.getLogger("cover_cache")

DATA_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data")
COVERS_DIR = os.path.join(DATA_DIR, "covers")
os.makedirs(COVERS_DIR, exist_ok=True)


def get_image_hash(url: str) -> str:
    """Generates a stable 24-character hexadecimal hash for an image URL."""
    clean = str(url or "").strip()
    return hashlib.sha256(clean.encode("utf-8")).hexdigest()[:24]


class CoverCacheManager:
    def __init__(self, covers_dir: str = COVERS_DIR, max_workers: int = 8):
        self.covers_dir = covers_dir
        os.makedirs(self.covers_dir, exist_ok=True)
        self.max_workers = max_workers
        self._executor: Optional[ThreadPoolExecutor] = None
        self._queued_hashes: Set[str] = set()
        self._failed_hashes: Dict[str, float] = {}  # hash -> failure timestamp
        self._lock = threading.Lock()
        self._session: Optional[requests.Session] = None

    @property
    def session(self) -> requests.Session:
        if self._session is None:
            s = requests.Session()
            s.headers.update({
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Accept": "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
            })
            self._session = s
        return self._session

    @property
    def executor(self) -> ThreadPoolExecutor:
        if self._executor is None:
            self._executor = ThreadPoolExecutor(max_workers=self.max_workers, thread_name_prefix="CoverCache")
        return self._executor

    def get_file_path(self, url: str) -> str:
        h = get_image_hash(url)
        return os.path.join(self.covers_dir, f"{h}.jpg")

    def is_cached(self, url: Optional[str]) -> bool:
        if not url or not url.strip():
            return False
        path = self.get_file_path(url)
        try:
            return os.path.isfile(path) and os.path.getsize(path) >= 500
        except OSError:
            return False

    def get_headers_for_url(self, url: str) -> Dict[str, str]:
        headers = dict(self.session.headers)
        u_low = url.lower()
        if "yandex" in u_low or "kinopoisk" in u_low:
            headers["Referer"] = "https://www.kinopoisk.ru/"
        elif "filmix" in u_low or "werkecdn" in u_low or "cdnsqu" in u_low:
            headers["Referer"] = "https://filmix.my/"
        elif "rezka" in u_low:
            headers["Referer"] = "https://hdrezka.ag/"
        elif "tmdb" in u_low or "themoviedb" in u_low:
            headers["Referer"] = "https://www.themoviedb.org/"
        return headers

    def download_and_save(self, url: str, timeout: Any = (3.0, 7.0)) -> Optional[str]:
        """Synchronously downloads a remote cover and atomically saves it to local disk."""
        if not url or not url.startswith("http"):
            return None

        target_path = self.get_file_path(url)
        if self.is_cached(url):
            return target_path

        h = get_image_hash(url)
        with self._lock:
            fail_time = self._failed_hashes.get(h)
            # Retry failed images after 10 minutes
            if fail_time and (time.time() - fail_time < 600):
                return None

        headers = self.get_headers_for_url(url)
        tmp_path = f"{target_path}.tmp_{os.getpid()}_{int(time.time() * 1000)}"

        try:
            resp = self.session.get(url, headers=headers, timeout=timeout, stream=True)
            if resp.status_code == 200:
                content_len = 0
                with open(tmp_path, "wb") as f:
                    for chunk in resp.iter_content(chunk_size=16384):
                        if chunk:
                            f.write(chunk)
                            content_len += len(chunk)

                if content_len >= 500:
                    os.replace(tmp_path, target_path)
                    with self._lock:
                        self._failed_hashes.pop(h, None)
                    return target_path
                else:
                    if os.path.exists(tmp_path):
                        os.remove(tmp_path)
            else:
                with self._lock:
                    self._failed_hashes[h] = time.time()
        except Exception as e:
            logger.debug(f"Failed to download cover from {url[:80]}: {e}")
            with self._lock:
                self._failed_hashes[h] = time.time()
            if os.path.exists(tmp_path):
                try:
                    os.remove(tmp_path)
                except OSError:
                    pass

        return None

    def enqueue_url(self, url: Optional[str]):
        """Enqueues a cover URL for background download to local machine disk."""
        if not url or not url.startswith("http"):
            return
        if any(bad in url for bad in ["noposter", "no_image_poster", "/covers/"]):
            return

        h = get_image_hash(url)
        with self._lock:
            if h in self._queued_hashes:
                return
            if self.is_cached(url):
                return
            self._queued_hashes.add(h)

        def _worker():
            try:
                self.download_and_save(url)
            finally:
                with self._lock:
                    self._queued_hashes.discard(h)

        self.executor.submit(_worker)

    def enqueue_batch(self, urls: List[str]):
        """Enqueues a list of cover URLs for background download."""
        for u in urls:
            self.enqueue_url(u)

    def get_local_url(self, url: Optional[str]) -> Optional[str]:
        """
        Converts an image URL into a local server URL.
        If already cached on local disk: returns direct static path `/covers/{hash}.jpg`.
        If not yet cached: returns `/api/media/image?url=...` and enqueues download in background.
        """
        if not url or not str(url).strip():
            return None
        u_str = str(url).strip()
        if u_str.startswith("/covers/") or u_str.startswith("/api/media/image"):
            return u_str
        if not u_str.startswith("http"):
            return u_str

        h = get_image_hash(u_str)
        if self.is_cached(u_str):
            return f"/covers/{h}.jpg"

        # Enqueue background cache and provide on-demand proxy fallback
        self.enqueue_url(u_str)
        return f"/api/media/image?url={urllib.parse.quote(u_str, safe='')}"

    def preload_registry_covers(self, db_path: str):
        """Asynchronously scans the SQLite media registry and downloads all posters & backdrops to disk."""
        import sqlite3
        def _scan():
            if not os.path.exists(db_path):
                return
            try:
                conn = sqlite3.connect(db_path, timeout=10.0)
                cur = conn.cursor()
                rows = cur.execute("SELECT poster, backdrop FROM media_items WHERE poster IS NOT NULL OR backdrop IS NOT NULL;").fetchall()
                conn.close()
                count = 0
                for p, b in rows:
                    if p and p.startswith("http") and not self.is_cached(p):
                        self.enqueue_url(p)
                        count += 1
                    if b and b.startswith("http") and not self.is_cached(b):
                        self.enqueue_url(b)
                        count += 1
                logger.info(f"Enqueued {count} covers from registry for local machine storage.")
            except Exception as e:
                logger.error(f"Error scanning registry covers: {e}")

        threading.Thread(target=_scan, daemon=True, name="PreloadCovers").start()

    def stats(self) -> Dict[str, Any]:
        """Returns storage statistics for locally saved covers."""
        try:
            files = [os.path.join(self.covers_dir, f) for f in os.listdir(self.covers_dir) if f.endswith(".jpg")]
            total_bytes = sum(os.path.getsize(f) for f in files if os.path.isfile(f))
            return {
                "count": len(files),
                "size_mb": round(total_bytes / (1024 * 1024), 2),
                "queued": len(self._queued_hashes)
            }
        except Exception:
            return {"count": 0, "size_mb": 0.0, "queued": 0}


cover_cache = CoverCacheManager()
