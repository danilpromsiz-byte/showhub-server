"""
ShowHub Autonomous Background Catalog Harvester.
Continuously populates and expands the local SQLite MediaRegistry with
ALL available movies, series, anime, and cartoons from Kodik, TMDb, Lampa, Filmix, and AniLibria.
Persists crawler state (cursors and page numbers) in SQLite so it resumes seamlessly across server restarts.
Runs asynchronously in a background daemon thread with smooth rate-limiting.
"""
import os
import time
import json
import logging
import threading
import urllib.request
import urllib.parse
from typing import List, Dict, Any, Optional

import requests

from .media_registry import media_registry
from .lampa_source import lampa_source
from .turso_manager import turso_manager

logger = logging.getLogger("media_harvester")

KODIK_TOKEN = "41dd95f84c21719b09d6c71182237a25"
TMDB_KEY = "8265bd1679663a7ea12ac168da84d2e8"
TMDB_BASE = "https://api.themoviedb.org/3"

GENRES_MAP = {
    28: "боевик", 12: "приключения", 16: "мультфильм", 35: "комедия", 80: "криминал",
    99: "документальный", 18: "драма", 10751: "семейный", 14: "фэнтези", 36: "история",
    27: "ужасы", 10402: "музыка", 9648: "детектив", 10749: "мелодрама", 878: "фантастика",
    10770: "телефильм", 53: "триллер", 10752: "военный", 37: "вестерн",
    10759: "боевик и приключения", 10762: "детский", 10763: "новости", 10764: "реалити-шоу",
    10765: "научная фантастика и фэнтези", 10766: "мыльная опера", 10767: "ток-шоу",
    10768: "война и политика"
}

KODIK_CATEGORIES = {
    "kodik_movies": ("movie,foreign-movie,soviet-movie,russian-movie", "movie"),
    "kodik_series": ("serial,foreign-serial,russian-serial,documentary-serial", "series"),
    "kodik_anime": ("anime,anime-serial", "anime"),
    "kodik_cartoons": ("cartoon,cartoon-serial", "cartoons")
}

TMDB_FEEDS = [
    ("tmdb_movies_popular", "/movie/popular", "movie", {"region": "RU"}),
    ("tmdb_movies_top", "/movie/top_rated", "movie", {"region": "RU"}),
    ("tmdb_tv_popular", "/tv/popular", "series", {}),
    ("tmdb_tv_top", "/tv/top_rated", "series", {}),
    ("tmdb_ru_movies", "/discover/movie", "movie", {"with_original_language": "ru", "sort_by": "popularity.desc"}),
    ("tmdb_ru_tv", "/discover/tv", "series", {"with_original_language": "ru", "sort_by": "popularity.desc"}),
    ("tmdb_new_movies", "/discover/movie", "movie", {"primary_release_date.gte": "2023-01-01", "sort_by": "popularity.desc"}),
    ("tmdb_new_tv", "/discover/tv", "series", {"first_air_date.gte": "2023-01-01", "sort_by": "popularity.desc"}),
    ("tmdb_animation_popular", "/discover/movie", "cartoons", {"with_genres": "16", "sort_by": "popularity.desc"}),
    ("tmdb_animation_tv", "/discover/tv", "cartoons", {"with_genres": "16", "sort_by": "popularity.desc"}),
    ("tmdb_soviet_movies", "/discover/movie", "movie", {"with_original_language": "ru", "primary_release_date.lte": "1991-12-31", "sort_by": "vote_count.desc"})
]


class MediaHarvester:
    def __init__(self):
        self._started = False
        self._lock = threading.Lock()
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "ShowHubTV/1.0",
            "Accept": "application/json"
        })

    def start_background_harvest(self):
        with self._lock:
            if self._started:
                return
            self._started = True

        thread = threading.Thread(target=self._run_crawler_loop, daemon=True, name="MediaRegistryHarvester")
        thread.start()
        logger.info("[Harvester] Autonomous continuous background harvester started.")

    def _upsert_and_cloud_sync(self, items: List[Dict[str, Any]]) -> int:
        """Upserts items into local SQLite and mirrors batch to Turso Cloud if configured."""
        c = media_registry.upsert_batch(items)
        if turso_manager.is_configured() and items:
            try:
                turso_manager.push_items_batch(items)
            except Exception as e:
                logger.debug(f"[Harvester] Turso push error: {e}")
        return c

    def _run_crawler_loop(self):
        """Continuous stateful crawler that populates ALL available titles from Kodik and TMDb."""
        logger.info("[Harvester] Seeding and starting continuous catalog expansion...")

        # Initial Turso Cloud synchronization if configured
        if turso_manager.is_configured():
            try:
                turso_manager.init_schema()
                last_updated = media_registry._get_connection().execute("SELECT MAX(updated_at) FROM media_items;").fetchone()[0] or 0.0
                delta = turso_manager.pull_items_delta(since_updated_at=last_updated, limit=5000)
                if delta:
                    media_registry.upsert_batch(delta)
                    logger.info(f"[Harvester] Synced {len(delta)} delta items from Turso Cloud on startup.")
            except Exception as e:
                logger.debug(f"[Harvester] Turso boot sync error: {e}")

        # Initial Lampa feeds sync
        try:
            self._harvest_lampa_feeds()
        except Exception as e:
            logger.debug(f"[Harvester] Initial Lampa harvest error: {e}")

        last_fresh_poll = 0.0

        while True:
            try:
                now = time.time()

                # Every 15 minutes: poll fresh updates of the day
                if now - last_fresh_poll > 15 * 60:
                    self._poll_fresh_updates()
                    last_fresh_poll = now

                # 1. Step Kodik Crawlers across categories
                for state_key, (types_str, default_cat) in KODIK_CATEGORIES.items():
                    try:
                        self._crawl_kodik_category_step(state_key, types_str, default_cat)
                    except Exception as e:
                        logger.debug(f"[Harvester] Kodik step error ({state_key}): {e}")
                    time.sleep(1.0)

                # 2. Step TMDb Crawlers
                for state_key, endpoint, cat_type, extra_params in TMDB_FEEDS:
                    try:
                        self._crawl_tmdb_feed_step(state_key, endpoint, cat_type, extra_params)
                    except Exception as e:
                        logger.debug(f"[Harvester] TMDb step error ({state_key}): {e}")
                    time.sleep(0.8)

            except Exception as e:
                logger.warning(f"[Harvester] Unexpected error in crawler loop: {e}")
                time.sleep(15.0)

    def _crawl_kodik_category_step(self, state_key: str, types_str: str, default_cat: str):
        """Fetches 1 batch of 100 items from Kodik for a category and advances cursor."""
        state = media_registry.get_harvester_state(state_key)
        if state.get("is_completed") and (time.time() - state.get("last_run_at", 0) < 3600):
            return  # Cooldown completed category for 1 hour before re-scanning

        url = state.get("next_url")

        if not url:
            url = f"https://kodik-api.com/list?token={KODIK_TOKEN}&limit=100&with_material_data=true&types={types_str}"

        resp = self.session.get(url, timeout=12)
        if resp.status_code != 200:
            logger.debug(f"[Harvester] Kodik returned HTTP {resp.status_code} for {state_key}")
            return

        data = resp.json()
        results = data.get("results", [])
        if not results:
            # Reached end: reset cursor to loop again from latest additions
            media_registry.set_harvester_state(
                state_key,
                next_url=None,
                last_page=state.get("last_page", 0) + 1,
                total_harvested=state.get("total_harvested", 0),
                is_completed=True
            )
            return

        items = []
        for res in results:
            k_id = str(res.get("id") or "")
            if not k_id:
                continue

            title = res.get("title") or res.get("title_orig") or ""
            if not title:
                continue

            year = res.get("year")
            is_ser = "serial" in str(res.get("type", ""))
            md = res.get("material_data") or {}

            poster = md.get("poster_url")
            if not poster or "st.kp.yandex.net" in str(poster):
                shots = res.get("screenshots", [])
                poster = shots[0] if shots else ""

            kp_id = str(res.get("kinopoisk_id") or md.get("kinopoisk_id") or "")
            imdb_id = str(res.get("imdb_id") or md.get("imdb_id") or "")
            desc = md.get("description") or ""

            kp_r = md.get("kinopoisk_rating")
            imdb_r = md.get("imdb_rating")

            genres = md.get("genres") or []
            countries = md.get("countries") or []
            actors = ", ".join(md.get("actors") or [])
            directors = ", ".join(md.get("directors") or [])

            # Determine category
            if "anime" in str(res.get("type", "")):
                cat = "anime"
            elif any("мульт" in g.lower() for g in genres):
                cat = "anime" if any("япон" in c.lower() for c in countries) else "cartoons"
            elif is_ser:
                cat = "series"
            else:
                cat = default_cat

            extra = {
                "imdb_id": imdb_id,
                "translation": res.get("translation", {}).get("title", ""),
                "actors": actors,
                "directors": directors
            }

            items.append({
                "id": k_id,
                "source_name": "kodik",
                "title": title,
                "original_title": res.get("title_orig"),
                "year": year,
                "is_series": is_ser,
                "poster": poster,
                "description": desc,
                "rating_kp": float(kp_r) if kp_r else None,
                "rating_imdb": float(imdb_r) if imdb_r else None,
                "kinopoisk_id": kp_id if kp_id else None,
                "imdb_id": imdb_id if imdb_id else None,
                "category": cat,
                "genres": genres,
                "countries": countries,
                "extra_data": extra
            })

        c = self._upsert_and_cloud_sync(items)
        next_page = data.get("next_page")
        new_total = state.get("total_harvested", 0) + c
        new_page = state.get("last_page", 0) + 1

        media_registry.set_harvester_state(
            state_key,
            next_url=next_page,
            last_page=new_page,
            total_harvested=new_total,
            is_completed=(next_page is None)
        )
        logger.info(f"[Harvester] {state_key} page {new_page}: +{c} new items (Total indexed: {new_total})")

    def _crawl_tmdb_feed_step(self, state_key: str, endpoint: str, cat_type: str, extra_params: Dict[str, str]):
        """Fetches 1 page (20 items) from TMDb and advances page."""
        state = media_registry.get_harvester_state(state_key)
        if state.get("is_completed") and (time.time() - state.get("last_run_at", 0) < 3600):
            return  # Cooldown completed feed for 1 hour before re-scanning

        page = state.get("last_page", 0) + 1
        if page > 500:
            page = 1  # TMDb max is 500 pages

        params = dict(extra_params)
        params["api_key"] = TMDB_KEY
        params["language"] = "ru-RU"
        params["page"] = str(page)

        qs = urllib.parse.urlencode(params)
        url = f"{TMDB_BASE}{endpoint}?{qs}"

        resp = self.session.get(url, timeout=10)
        if resp.status_code != 200:
            return

        results = resp.json().get("results", [])
        if not results:
            media_registry.set_harvester_state(state_key, last_page=1, total_harvested=state.get("total_harvested", 0), is_completed=True)
            return

        is_ser_feed = (cat_type == "series")
        items = []
        for item in results:
            t_id = str(item.get("id") or "")
            if not t_id:
                continue

            title = item.get("title") or item.get("name") or ""
            if not title:
                continue

            is_ser = is_ser_feed or ("first_air_date" in item)
            db_id = f"tmdb_tv_{t_id}" if is_ser else f"tmdb_{t_id}"
            date_str = item.get("release_date") or item.get("first_air_date") or ""
            year = int(date_str[:4]) if len(date_str) >= 4 and date_str[:4].isdigit() else None
            import datetime
            if year and year > datetime.date.today().year:
                continue

            poster = f"https://image.tmdb.org/t/p/w500{item['poster_path']}" if item.get("poster_path") else ""
            backdrop = f"https://image.tmdb.org/t/p/w1280{item['backdrop_path']}" if item.get("backdrop_path") else ""
            desc = item.get("overview") or ""
            rating = item.get("vote_average") or 0.0
            pop = item.get("popularity") or 0.0

            g_ids = item.get("genre_ids", [])
            g_names = [GENRES_MAP[g] for g in g_ids if g in GENRES_MAP]
            orig_countries = item.get("origin_country", [])

            if 16 in g_ids:
                cat = "anime" if ("JP" in orig_countries or "Japan" in orig_countries) else "cartoons"
            elif is_ser:
                cat = "series"
            else:
                cat = "movie"

            items.append({
                "id": db_id,
                "source_name": "tmdb",
                "title": title,
                "original_title": orig_title,
                "year": year,
                "is_series": is_ser,
                "poster": poster,
                "description": desc,
                "rating_imdb": float(rating) if rating else None,
                "category": cat,
                "genres": g_names,
                "countries": orig_countries,
                "popularity": float(pop),
                "backdrop": backdrop,
                "tmdb_id": t_id
            })

        c = self._upsert_and_cloud_sync(items)
        new_total = state.get("total_harvested", 0) + c
        media_registry.set_harvester_state(state_key, last_page=page, total_harvested=new_total)
        logger.info(f"[Harvester] {state_key} page {page}: +{c} new items (Total indexed: {new_total})")

    def _poll_fresh_updates(self):
        """Polls fresh daily releases from Kodik, AniLibria, and TMDb trending."""
        logger.info("[Harvester] Polling fresh daily updates...")
        try:
            # 1. TMDb trending today
            url = f"{TMDB_BASE}/trending/all/day?api_key={TMDB_KEY}&language=ru-RU"
            r = self.session.get(url, timeout=8)
            if r.status_code == 200:
                results = r.json().get("results", [])
                items = []
                for res in results:
                    t_id = str(res.get("id"))
                    is_ser = res.get("media_type") == "tv" or "first_air_date" in res
                    title = res.get("title") or res.get("name") or ""
                    if not title: continue
                    date_str = res.get("release_date") or res.get("first_air_date") or ""
                    year = int(date_str[:4]) if len(date_str) >= 4 and date_str[:4].isdigit() else None
                    poster = f"https://image.tmdb.org/t/p/w500{res['poster_path']}" if res.get("poster_path") else ""
                    items.append({
                        "id": f"tmdb_tv_{t_id}" if is_ser else f"tmdb_{t_id}",
                        "source_name": "tmdb",
                        "title": title,
                        "original_title": res.get("original_title") or res.get("original_name"),
                        "year": year,
                        "is_series": is_ser,
                        "poster": poster,
                        "description": res.get("overview") or "",
                        "rating_imdb": float(res.get("vote_average") or 0.0),
                        "category": "series" if is_ser else "movie",
                        "tmdb_id": t_id
                    })
                if items:
                    self._upsert_and_cloud_sync(items)
        except Exception as e:
            logger.debug(f"[Harvester] Fresh TMDb updates error: {e}")

        try:
            # 2. AniLibria schedule
            from ..sources.anilibria import AnilibriaSource
            anilibria = AnilibriaSource()
            al_items = anilibria.get_schedule()
            if al_items:
                self._upsert_and_cloud_sync(al_items)
        except Exception as e:
            logger.debug(f"[Harvester] Fresh AniLibria error: {e}")

    def _harvest_lampa_feeds(self) -> int:
        """Harvests Lampa's main screen feeds: trending today/week, popular, top rated, now playing."""
        total_lampa = 0
        try:
            cards = lampa_source.get_main_screen_feeds(max_pages_per_feed=2)
            if cards:
                c = self._upsert_and_cloud_sync(cards)
                total_lampa += c
                logger.info(f"[Harvester] Upserted {c} items from Lampa main feeds.")
        except Exception as e:
            logger.debug(f"[Harvester] Error in _harvest_lampa_feeds: {e}")
        return total_lampa


media_harvester = MediaHarvester()
