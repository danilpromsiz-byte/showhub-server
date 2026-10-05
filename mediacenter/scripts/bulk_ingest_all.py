"""
ShowHub High-Throughput Bulk Catalog Ingestion Engine.
Harvests all available movies, series, anime, and cartoons from Kodik (78,000+ items)
and TMDb (popular, top-rated, Russian releases, Soviet cinema, animation) in parallel.
Upserts canonical media records into local SQLite (with FTS5 index) AND mirrors to Turso Cloud.
"""
import os
import sys
import time
import json
import logging
import argparse
import threading
import urllib.parse
from concurrent.futures import ThreadPoolExecutor
from typing import List, Dict, Any, Optional

import requests

# Ensure project root is in sys.path
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from mediacenter.core.media_registry import media_registry, DB_PATH
from mediacenter.core.turso_manager import turso_manager

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("bulk_ingest")

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

KODIK_WORKERS = [
    ("kodik_movies", "movie,foreign-movie,soviet-movie,russian-movie", "movie"),
    ("kodik_series", "serial,foreign-serial,russian-serial,documentary-serial", "series"),
    ("kodik_anime", "anime,anime-serial", "anime"),
    ("kodik_cartoons", "cartoon,cartoon-serial", "cartoons")
]

TMDB_WORKERS = [
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

stop_event = threading.Event()
stats_lock = threading.Lock()
total_added = 0
total_pushed_turso = 0


def process_batch(items: List[Dict[str, Any]], sync_turso: bool = True) -> int:
    global total_added, total_pushed_turso
    if not items:
        return 0

    c = media_registry.upsert_batch(items, enqueue_covers=False)
    with stats_lock:
        total_added += c

    if sync_turso and turso_manager.is_configured() and items:
        try:
            pushed = turso_manager.push_items_batch(items)
            with stats_lock:
                total_pushed_turso += pushed
        except Exception as e:
            logger.debug(f"[BulkIngest] Turso sync error: {e}")
    return c


def run_kodik_crawler(worker_key: str, types_str: str, default_cat: str, max_batches: int = 500, sync_turso: bool = True):
    session = requests.Session()
    session.headers.update({"User-Agent": "ShowHubBulk/1.0"})
    state = media_registry.get_harvester_state(worker_key)
    url = state.get("next_url")
    if not url:
        url = f"https://kodik-api.com/list?token={KODIK_TOKEN}&limit=100&with_material_data=true&types={types_str}"

    page = state.get("last_page", 0)
    batches_run = 0

    logger.info(f"[{worker_key}] Starting crawler from page {page}...")

    while not stop_event.is_set() and batches_run < max_batches:
        try:
            resp = session.get(url, timeout=12)
            if resp.status_code == 429:
                logger.warning(f"[{worker_key}] Rate limited (429), backing off...")
                time.sleep(3.0)
                continue
            if resp.status_code != 200:
                logger.warning(f"[{worker_key}] HTTP {resp.status_code}, pausing...")
                time.sleep(2.0)
                continue

            data = resp.json()
            results = data.get("results", [])
            if not results:
                logger.info(f"[{worker_key}] Completed all available titles in category!")
                media_registry.set_harvester_state(worker_key, next_url=None, last_page=page, total_harvested=state.get("total_harvested", 0), is_completed=True)
                break

            items = []
            for res in results:
                k_id = str(res.get("id") or "")
                title = res.get("title") or res.get("title_orig") or ""
                if not k_id or not title:
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

            c = process_batch(items, sync_turso=sync_turso)
            page += 1
            batches_run += 1
            next_url = data.get("next_page")

            media_registry.set_harvester_state(
                worker_key,
                next_url=next_url,
                last_page=page,
                total_harvested=state.get("total_harvested", 0) + c,
                is_completed=(next_url is None)
            )

            if not next_url:
                logger.info(f"[{worker_key}] Reached end of category.")
                break

            url = next_url
            time.sleep(0.3)  # Gentle 300ms pacing

        except Exception as e:
            logger.error(f"[{worker_key}] Error: {e}")
            time.sleep(2.0)


def run_tmdb_crawler(worker_key: str, endpoint: str, cat_type: str, extra_params: Dict[str, str], max_pages: int = 200, sync_turso: bool = True):
    session = requests.Session()
    session.headers.update({"User-Agent": "ShowHubBulk/1.0"})
    state = media_registry.get_harvester_state(worker_key)
    page = state.get("last_page", 0) + 1
    pages_run = 0

    logger.info(f"[{worker_key}] Starting TMDb crawl from page {page}...")

    while not stop_event.is_set() and pages_run < max_pages and page <= 500:
        try:
            params = dict(extra_params)
            params["api_key"] = TMDB_KEY
            params["language"] = "ru-RU"
            params["page"] = str(page)

            qs = urllib.parse.urlencode(params)
            url = f"{TMDB_BASE}{endpoint}?{qs}"

            resp = session.get(url, timeout=10)
            if resp.status_code == 429:
                time.sleep(2.0)
                continue
            if resp.status_code != 200:
                time.sleep(1.0)
                continue

            results = resp.json().get("results", [])
            if not results:
                break

            is_ser_feed = (cat_type == "series")
            items = []
            for item in results:
                t_id = str(item.get("id") or "")
                title = item.get("title") or item.get("name") or ""
                if not t_id or not title:
                    continue

                is_ser = is_ser_feed or ("first_air_date" in item)
                db_id = f"tmdb_tv_{t_id}" if is_ser else f"tmdb_{t_id}"
                orig_title = item.get("original_title") or item.get("original_name")
                date_str = item.get("release_date") or item.get("first_air_date") or ""
                year = int(date_str[:4]) if len(date_str) >= 4 and date_str[:4].isdigit() else None

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

            c = process_batch(items, sync_turso=sync_turso)
            pages_run += 1
            media_registry.set_harvester_state(worker_key, last_page=page, total_harvested=state.get("total_harvested", 0) + c)
            page += 1
            time.sleep(0.25)

        except Exception as e:
            logger.error(f"[{worker_key}] Error: {e}")
            time.sleep(2.0)


def main():
    parser = argparse.ArgumentParser(description="Bulk Ingest all media into local SQLite and Turso Cloud")
    parser.add_argument("--kodik-batches", type=int, default=150, help="Max batches per Kodik category (100 items each)")
    parser.add_argument("--tmdb-pages", type=int, default=50, help="Max pages per TMDb feed (20 items each)")
    parser.add_argument("--sync-turso", action="store_true", default=True, help="Mirror items to Turso Cloud")
    args = parser.parse_args()

    start_time = time.time()
    initial_count = media_registry.count()
    logger.info(f"=== Starting High-Throughput Bulk Ingestion ===")
    logger.info(f"Local Registry initial count: {initial_count}")
    logger.info(f"Turso Cloud configured: {turso_manager.is_configured()}")
    if turso_manager.is_configured():
        logger.info(f"Turso initial count: {turso_manager.get_remote_count()}")

    with ThreadPoolExecutor(max_workers=16) as executor:
        futures = []

        # Launch Kodik category workers
        for worker_key, types_str, default_cat in KODIK_WORKERS:
            f = executor.submit(run_kodik_crawler, worker_key, types_str, default_cat, args.kodik_batches, args.sync_turso)
            futures.append(f)

        # Launch ALL TMDb feed workers (popular, top-rated, RU cinema, animations, new releases, Soviet classics)
        for worker_key, endpoint, cat_type, extra_params in TMDB_WORKERS:
            f = executor.submit(run_tmdb_crawler, worker_key, endpoint, cat_type, extra_params, args.tmdb_pages, args.sync_turso)
            futures.append(f)

        try:
            while any(not f.done() for f in futures):
                time.sleep(5.0)
                stats = media_registry.get_stats()
                elapsed = time.time() - start_time
                cur_total = stats.get("total_items", 0)
                delta_added = cur_total - initial_count
                speed = delta_added / elapsed if elapsed > 0 else 0
                logger.info(f"[Progress] Total: {cur_total:,} (+{delta_added:,}) | By Cat: {stats.get('by_category', {})} | Speed: {speed:.1f} items/s | Turso synced: {total_pushed_turso:,}")
        except KeyboardInterrupt:
            logger.info("Stopping bulk ingest workers...")
            stop_event.set()

    elapsed = time.time() - start_time
    final_stats = media_registry.get_stats()
    logger.info(f"=== Bulk Ingestion Complete in {elapsed:.1f}s ===")
    logger.info(f"Final local count: {final_stats.get('total_items', 0):,}")
    logger.info(f"Categories: {final_stats.get('by_category', {})}")
    if turso_manager.is_configured():
        logger.info(f"Final Turso count: {turso_manager.get_remote_count():,}")


if __name__ == "__main__":
    main()
