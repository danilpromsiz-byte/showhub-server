"""
ShowHub Background Catalog Harvester.
Continuously populates and refreshes the local SQLite MediaRegistry with
popular, trending, and fresh movies, series, anime, and cartoons from all available sources.
Uses Lampa as the primary authoritative source for popularity, ratings, age limits, and metadata.
Runs asynchronously in daemon threads without blocking API requests.
"""
import os
import time
import json
import logging
import threading
from typing import List, Any

from .media_registry import media_registry
from .lampa_source import lampa_source

logger = logging.getLogger("media_harvester")

class MediaHarvester:
    def __init__(self):
        self._started = False
        self._lock = threading.Lock()

    def start_background_harvest(self):
        with self._lock:
            if self._started:
                return
            self._started = True

        thread = threading.Thread(target=self._run_seeding_loop, daemon=True, name="MediaRegistryHarvester")
        thread.start()

    def _run_seeding_loop(self):
        logger.info("[Harvester] Starting initial media registry seeding and Lampa synchronization...")
        # Step 1: Initial static catalog seed (if empty)
        self._seed_static_catalog()

        # Step 2: Immediate Lampa main screen sync (trending, popular, now playing)
        try:
            self._harvest_lampa_feeds()
        except Exception as e:
            logger.error(f"[Harvester] Error during initial Lampa harvest: {e}")

        # Step 3: Live sources harvesting (popular & top categories)
        while True:
            try:
                self._harvest_all_sources()
            except Exception as e:
                logger.error(f"[Harvester] Error during periodic harvest: {e}")

            # Sleep for 2 hours between full refreshes
            time.sleep(2 * 3600)

    def _seed_static_catalog(self):
        try:
            curr_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
            init_cat_path = os.path.join(curr_dir, "static", "initial_catalog.json")
            if os.path.exists(init_cat_path):
                with open(init_cat_path, "r", encoding="utf-8") as f:
                    items = json.load(f)
                    if isinstance(items, list):
                        c = media_registry.upsert_batch(items)
                        logger.info(f"[Harvester] Seeded {c} items from initial_catalog.json")
        except Exception as e:
            logger.warning(f"[Harvester] Failed to seed static catalog: {e}")

    def _harvest_lampa_feeds(self) -> int:
        """Harvests Lampa's main screen feeds: trending today/week, popular, top rated, now playing."""
        logger.info("[Harvester] Fetching Lampa main screen feeds...")
        total_lampa = 0
        try:
            cards = lampa_source.get_main_screen_feeds(max_pages_per_feed=2)
            if cards:
                c = media_registry.upsert_batch(cards)
                total_lampa += c
                logger.info(f"[Harvester] Upserted {c} items from Lampa main feeds.")

                # Deep enrich top 40 trending items with cast, director, age rating, recs, tags
                for card in cards[:40]:
                    try:
                        tmdb_id = card.get("tmdb_id")
                        is_ser = card.get("is_series", False)
                        if tmdb_id:
                            det = lampa_source.get_details_with_appends(tmdb_id, is_series=is_ser)
                            if det:
                                det["id"] = f"tmdb_{tmdb_id}"
                                det["source_name"] = "lampa"
                                media_registry.upsert_item(det)
                        time.sleep(0.15)
                    except Exception:
                        pass
        except Exception as e:
            logger.error(f"[Harvester] Error in _harvest_lampa_feeds: {e}")
        return total_lampa

    def _enrich_existing_items_with_lampa(self, max_items: int = 50):
        """Enriches existing database items that lack Lampa popularity or ratings."""
        try:
            conn = media_registry._get_connection()
            rows = conn.execute("""
                SELECT id, title, year, is_series, original_title
                FROM media_items
                WHERE lampa_popularity = 0.0 OR rating_lampa IS NULL
                ORDER BY updated_at DESC
                LIMIT ?;
            """, (max_items,)).fetchall()

            for r in rows:
                try:
                    title = r["title"]
                    year = r["year"]
                    is_ser = bool(r["is_series"])
                    orig = r["original_title"]

                    from .tmdb import tmdb
                    tmdb_res = tmdb.search_and_enrich(title=title, year=year, is_series=is_ser, original_title=orig)
                    if tmdb_res and tmdb_res.get("tmdb_id"):
                        det = lampa_source.get_details_with_appends(str(tmdb_res["tmdb_id"]), is_series=is_ser)
                        if det:
                            det["id"] = r["id"]  # Merge directly into existing record
                            det["source_name"] = "lampa"
                            media_registry.upsert_item(det)
                    time.sleep(0.2)
                except Exception:
                    pass
        except Exception as e:
            logger.debug(f"[Harvester] Error enriching existing items: {e}")

    def _harvest_all_sources(self):
        total_indexed = 0

        # 1. Primary: Lampa main screen feeds
        total_indexed += self._harvest_lampa_feeds()

        # 2. Enrich existing items with Lampa metadata
        try:
            self._enrich_existing_items_with_lampa(max_items=50)
        except Exception:
            pass

        # 3. Harvest from Kodik (movies, series, anime)
        try:
            from ..sources.kodik import KodikSource
            kodik = KodikSource()
            for cat in ["all", "movies", "series", "anime"]:
                try:
                    for page in range(1, 4):
                        k_items = kodik.get_catalog(category=cat, page=page, limit=100)
                        if k_items:
                            total_indexed += media_registry.upsert_batch(k_items)
                        time.sleep(0.4)
                except Exception:
                    pass
        except Exception as e:
            logger.debug(f"[Harvester] Kodik harvest error: {e}")

        # 4. Harvest from Filmix (popular movies and series)
        try:
            from ..sources.filmix import FilmixSource
            filmix = FilmixSource()
            for cat in ["all", "movies", "series"]:
                try:
                    for p in range(1, 4):
                        fx_items = filmix.get_catalog(category=cat, page=p)
                        if fx_items:
                            total_indexed += media_registry.upsert_batch(fx_items)
                        time.sleep(0.4)
                except Exception:
                    pass
        except Exception as e:
            logger.debug(f"[Harvester] Filmix harvest error: {e}")

        # 5. Harvest from AniLibria (anime releases)
        try:
            from ..sources.anilibria import AnilibriaSource
            anilibria = AnilibriaSource()
            al_items = anilibria.get_schedule()
            if al_items:
                total_indexed += media_registry.upsert_batch(al_items)
        except Exception as e:
            logger.debug(f"[Harvester] AniLibria harvest error: {e}")

        # 6. Harvest from TMDb Discover (trending & new releases)
        try:
            from .tmdb import API_KEY as TMDB_API_KEY
            import urllib.request, urllib.parse
            for endpoint in ["/trending/all/day", "/movie/now_playing", "/tv/on_the_air"]:
                try:
                    url = f"https://api.themoviedb.org/3{endpoint}?api_key={TMDB_API_KEY}&language=ru-RU"
                    req = urllib.request.Request(url, headers={"User-Agent": "ShowHub/1.0"})
                    with urllib.request.urlopen(req, timeout=8) as resp:
                        if resp.status == 200:
                            data = json.loads(resp.read().decode("utf-8"))
                            results = data.get("results", [])
                            tmdb_items = []
                            for r in results:
                                is_ser = r.get("media_type") == "tv" or "first_air_date" in r
                                t_id = str(r["id"])
                                tmdb_items.append({
                                    "id": f"tmdb_tv_{t_id}" if is_ser else f"tmdb_{t_id}",
                                    "title": r.get("title") or r.get("name") or "",
                                    "original_title": r.get("original_title") or r.get("original_name") or "",
                                    "year": int((r.get("release_date") or r.get("first_air_date") or "0000")[:4]) or None,
                                    "is_series": is_ser,
                                    "poster": f"https://image.tmdb.org/t/p/w500{r['poster_path']}" if r.get("poster_path") else "",
                                    "description": r.get("overview") or "",
                                    "rating_imdb": float(r.get("vote_average") or 0.0),
                                    "category": "series" if is_ser else "movie",
                                    "source_name": "tmdb",
                                    "tmdb_id": t_id
                                })
                            if tmdb_items:
                                total_indexed += media_registry.upsert_batch(tmdb_items)
                except Exception:
                    pass
        except Exception as e:
            logger.debug(f"[Harvester] TMDb harvest error: {e}")

        logger.info(f"[Harvester] Harvesting cycle complete. Indexed batch of {total_indexed} items. Total in registry: {media_registry.count()}")

media_harvester = MediaHarvester()
