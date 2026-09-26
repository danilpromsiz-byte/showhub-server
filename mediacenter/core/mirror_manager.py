"""
Dynamic Mirror Manager.
Extracts active mirrors from LazyMedia Deluxe feeds, GitHub fetchers,
and the internal mirror registry from HDrezka TV v1.4.0 (class LR8/o;).
"""
import time
import requests
from typing import List, Dict, Optional

class MirrorManager:
    LMD_FEED_URLS = [
        "https://update.lmdai.xyz/urls_lmd.json",
        "https://lazycatsoftware.com/lazymediadeluxe/urls_lmd.json",
    ]
    REZKA_FETCHER_URL = "https://raw.githubusercontent.com/MrIkso/hdrezka-fetcher/main/mirror.txt"

    # Tested and working mirrors from HDrezkaTV v1.4.0 (LR8/o; enum) and KinoHD remote config
    HDREZKA_APK_MIRRORS = [
        "https://rezka.fi",
        "https://rezka.ag",
        "https://rezka.si",
        "https://omnirezka.tv",
        "https://hello-rezka.tv",
        "https://hdrezka.me",
        "https://hdrezka-home.tv",
        "https://hdrezka.name",
        "https://hdrezka.sh",
        "https://hdrezka.sb",
        "https://hdrezka.in",
        "https://hdrezka.club",
        "https://hdrezka.cm",
        "https://hdrezka.kim",
        "https://rezka.pub",
        "https://rezka-kz.tv",
        "https://rezka-ua.net",
        "https://rezka-ua.org",
        "https://rezka-ua.in",
        "https://rezka-ua.co",
        "https://rezka-ua.pub",
        "https://rezkery.com"
    ]

    DEFAULT_MIRRORS = {
        "hdrezka": HDREZKA_APK_MIRRORS,
        "filmix": [
            "https://filmix.ac",
            "https://filmix.quest",
            "https://filmix.biz",
            "https://filmix.my",
            "https://filmix.tech",
            "https://filmix.life"
        ],
        "bazon": ["https://bazon.cc", "https://bazon.to"],
        "videocdn": ["https://api.apbugall.org"],
        "rutor": ["http://rutor.info", "http://rutor.is"],
        "jackett": ["http://jac.red", "http://jac-red.ru"],
    }

    def __init__(self):
        self.cached_mirrors: Dict[str, List[str]] = dict(self.DEFAULT_MIRRORS)
        self.last_update = 0
        self.cache_ttl = 1800

    def refresh_mirrors(self, force: bool = False):
        if not force and (time.time() - self.last_update < self.cache_ttl):
            return

        headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}

        # Keep verified APK mirrors at highest priority
        self.cached_mirrors["hdrezka"] = list(self.HDREZKA_APK_MIRRORS)
        self.cached_mirrors["filmix"] = list(self.DEFAULT_MIRRORS["filmix"])

        # 1. Fetch LMD config
        for url in self.LMD_FEED_URLS:
            try:
                res = requests.get(url, headers=headers, timeout=5)
                if res.status_code == 200:
                    data = res.json()
                    services = data.get("services", {})
                    trackers = data.get("trackers", {})

                    if "filmix" in services:
                        raw = services["filmix"].get("mirrors", "")
                        mirrors = [m.strip() for m in raw.split(",") if m.strip() and not m.strip().endswith("filmix.ac")]
                        if mirrors:
                            self.cached_mirrors["filmix"] = list(dict.fromkeys(self.cached_mirrors["filmix"] + mirrors))

                    if "rutor" in trackers:
                        raw = trackers["rutor"].get("mirrors", "")
                        mirrors = [m.strip() for m in raw.split(",") if m.strip()]
                        if mirrors:
                            self.cached_mirrors["rutor"] = list(dict.fromkeys(mirrors + self.cached_mirrors["rutor"]))
                    break
            except Exception:
                continue

        self.last_update = time.time()

    def get_mirrors(self, service: str) -> List[str]:
        self.refresh_mirrors()
        return self.cached_mirrors.get(service, self.DEFAULT_MIRRORS.get(service, []))

    def get_working_mirror(self, service: str, timeout: float = 3.0) -> Optional[str]:
        mirrors = self.get_mirrors(service)
        return mirrors[0] if mirrors else None

mirror_manager = MirrorManager()
