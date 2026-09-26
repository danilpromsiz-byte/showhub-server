"""
ShowHub Background Catalog Harvester.
Continuously populates and refreshes the local SQLite MediaRegistry with
popular, trending, and fresh movies, series, anime, and cartoons from all available sources.
Runs asynchronously in daemon threads without blocking API requests.
"""
import os
import time
import json
import logging
import threading
from typing import List, Any

from .media_registry import media_registry

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
        logger.info("[Harvester] Starting initial media registry seeding...")
        # Step 1: Initial static catalog seed
        self._seed_static_catalog()

        # Step 2: Live sources harvesting (popular & top categories)
        while True:
            try:
                self._harvest_all_sources()
            except Exception as e:
                logger.error(f"[Harvester] Error during periodic harvest: {e}")

            # Sleep for 4 hours between full refreshes
            time.sleep(4 * 3600)

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

    def _harvest_all_sources(self):
        total_indexed = 0

        # 1. Harvest from Kodik (movies, series, anime)
        try:
            from ..sources.kodik import KodikSource
            kodik = KodikSource()
            for cat in ["all", "movies", "series", "anime"]:
                try:
                    for page in range(1, 4):
                        k_items = kodik.get_catalog(category=cat, page=page, limit=100)
                        if k_items:
                            total_indexed += media_registry.upsert_batch(k_items)
                        time.sleep(0.5)
                except Exception:
                    pass
        except Exception as e:
            logger.debug(f"[Harvester] Kodik harvest error: {e}")

        # 2. Harvest from Filmix (popular movies and series)
        try:
            from ..sources.filmix import FilmixSource
            filmix = FilmixSource()
            for cat in ["all", "movies", "series"]:
                try:
                    for p in range(1, 4):
                        fx_items = filmix.get_catalog(category=cat, sort_by="popular", page=p)
                        if fx_items:
                            total_indexed += media_registry.upsert_batch(fx_items)
                        time.sleep(0.5)
                except Exception:
                    pass
        except Exception as e:
            logger.debug(f"[Harvester] Filmix harvest error: {e}")

        # 3. Harvest from AniLibria (anime releases)
        try:
            from ..sources.anilibria import AnilibriaSource
            anilibria = AnilibriaSource()
            al_items = anilibria.get_schedule()
            if al_items:
                total_indexed += media_registry.upsert_batch(al_items)
        except Exception as e:
            logger.debug(f"[Harvester] AniLibria harvest error: {e}")

        # 4. Harvest from TMDb (popular movies and popular series)
        try:
            from ..sources.tmdb import TMDbSource
            tmdb = TMDbSource()
            for page in range(1, 6):
                try:
                    m_pop = tmdb.get_popular_movies(page=page)
                    if m_pop:
                        total_indexed += media_registry.upsert_batch(m_pop)
                    s_pop = tmdb.get_popular_series(page=page)
                    if s_pop:
                        total_indexed += media_registry.upsert_batch(s_pop)
                    time.sleep(0.3)
                except Exception:
                    pass
        except Exception as e:
            logger.debug(f"[Harvester] TMDb harvest error: {e}")

        logger.info(f"[Harvester] Harvesting cycle complete. Indexed batch of {total_indexed} items. Total in registry: {media_registry.count()}")

media_harvester = MediaHarvester()
