"""
MediaCenter TV - Unified Aggregator and Streaming Server.
Aggregates streams from decompiled Android TV apps (LazyMedia Deluxe, HDrezka TV, Zona, Кино HD).
Features built-in Canary Health Check with automated change detection and alerts.
"""
import os
import sys
import datetime
import time
import json
import re
from typing import List, Dict, Any, Optional, Tuple
from concurrent.futures import ThreadPoolExecutor

from fastapi import FastAPI, Query, HTTPException
from fastapi.staticfiles import StaticFiles
from fastapi.responses import HTMLResponse, FileResponse
from fastapi.middleware.cors import CORSMiddleware

# Ensure correct path
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
PARENT_DIR = os.path.dirname(CURRENT_DIR)
if PARENT_DIR not in sys.path:
    sys.path.insert(0, PARENT_DIR)

from mediacenter.core.health_checker import health_checker
from mediacenter.sources.bazon import BazonSource
from mediacenter.sources.delivembd import DelivembdSource
from mediacenter.sources.torrents import TorrentsSource
from mediacenter.sources.hdrezka import HDRezkaSource
from mediacenter.sources.filmix import FilmixSource
from mediacenter.sources.videocdn import VideoCDNSource
from mediacenter.sources.base import MediaItem, StreamResult

app = FastAPI(title="MediaCenter TV Aggregator", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount static files
static_dir = os.path.join(CURRENT_DIR, "static")
os.makedirs(static_dir, exist_ok=True)
app.mount("/static", StaticFiles(directory=static_dir), name="static")

# Source instances
bazon = BazonSource()
delivembd = DelivembdSource()
torrents = TorrentsSource()
hdrezka = HDRezkaSource()
filmix = FilmixSource()
videocdn = VideoCDNSource()

@app.api_route("/", methods=["GET", "HEAD"], response_class=HTMLResponse)
async def serve_index():
    index_path = os.path.join(CURRENT_DIR, "templates", "index.html")
    if os.path.exists(index_path):
        with open(index_path, "r", encoding="utf-8") as f:
            return HTMLResponse(content=f.read())
    return HTMLResponse("<h1>ShowHub TV</h1>")

@app.get("/tv.css")
def serve_tv_css():
    return FileResponse(os.path.join(static_dir, "tv.css"))

@app.get("/tv.js")
def serve_tv_js():
    return FileResponse(os.path.join(static_dir, "tv.js"))

@app.get("/hls.min.js")
def serve_hls_js():
    return FileResponse(os.path.join(static_dir, "hls.min.js"))

@app.get("/noposter.png")
def serve_noposter():
    return FileResponse(os.path.join(static_dir, "noposter.png"))

@app.api_route("/ShowHub.apk", methods=["GET", "HEAD"])
@app.api_route("/ShowHub-v{version}.apk", methods=["GET", "HEAD"])
@app.api_route("/apk", methods=["GET", "HEAD"])
def serve_apk(version: Optional[str] = None):
    # 1. Check static directory (packaged for cloud / Render deployment)
    if version:
        static_target = os.path.join(static_dir, f"ShowHub-v{version}.apk")
        if os.path.exists(static_target):
            return FileResponse(static_target, media_type="application/vnd.android.package-archive", filename=f"ShowHub-v{version}.apk")
    static_apk = os.path.join(static_dir, "ShowHub.apk")
    if os.path.exists(static_apk):
        return FileResponse(static_apk, media_type="application/vnd.android.package-archive", filename="ShowHub.apk")

    # 2. Check parent directory (local development)
    if version:
        target_name = f"ShowHub-v{version}.apk"
        apk_path = os.path.join(PARENT_DIR, target_name)
        if os.path.exists(apk_path):
            return FileResponse(apk_path, media_type="application/vnd.android.package-archive", filename=target_name)

    # Automatically find latest versioned APK in parent directory
    candidates = [f for f in os.listdir(PARENT_DIR) if f.startswith("ShowHub-v") and f.endswith(".apk")]
    if candidates:
        latest = sorted(candidates)[-1]
        return FileResponse(os.path.join(PARENT_DIR, latest), media_type="application/vnd.android.package-archive", filename=latest)

    fallback_path = os.path.join(PARENT_DIR, "ShowHub.apk")
    if os.path.exists(fallback_path):
        return FileResponse(fallback_path, media_type="application/vnd.android.package-archive", filename="ShowHub.apk")
    raise HTTPException(status_code=404, detail="APK not found")
@app.get("/api/popular")
def get_popular() -> List[Dict[str, Any]]:
    """Returns dynamic fresh releases (новинки) from live sources."""
    return get_catalog(category="all", genre=None, page=1)[:24]

def normalize_search_title(t: str) -> str:
    if not t:
        return ""
    t = t.lower().strip()
    t = re.sub(r'\(.*?\)|\[.*?\]', '', t)
    for ch in ['«', '»', '"', "'", '`', '.', ',', ':', ';', '!', '?', '-', '_']:
        t = t.replace(ch, ' ')
    return " ".join(t.split())

def safe_parse_year(val: Any) -> Optional[int]:
    """Safely extracts a 4-digit year integer from any input (int, str, None, etc.)."""
    if val is None:
        return None
    try:
        s = str(val).strip()
        if not s or s.lower() in ("none", "null", "undefined", "н/д"):
            return None
        if s.isdigit():
            return int(s)
        m = re.search(r'\b(19\d\d|20\d\d)\b', s)
        if m:
            return int(m.group(1))
    except Exception:
        pass
    return None

def rank_matches(items: list, target_year: Optional[Any] = None, target_is_series: Optional[Any] = None) -> list:
    if not items:
        return []

    t_year = safe_parse_year(target_year)
    t_series = None
    if target_is_series is not None:
        if str(target_is_series).isdigit():
            t_series = bool(int(target_is_series))
        else:
            t_series = bool(target_is_series)

    def score_item(it):
        score = 0
        it_yr = safe_parse_year(getattr(it, "year", None))
        it_ser = getattr(it, "is_series", False)
        # Year matching
        if t_year and it_yr:
            diff = abs(it_yr - t_year)
            if diff == 0:
                score += 100
            elif diff == 1:
                score += 70
            elif diff == 2:
                score += 40
            else:
                score -= diff * 5
        # is_series matching
        if t_series is not None:
            if bool(it_ser) == bool(t_series):
                score += 50
            else:
                score -= 30
        return score

    return sorted(items, key=score_item, reverse=True)

def find_best_match(items: list, target_year: Optional[Any] = None, target_is_series: Optional[Any] = None):
    ranked = rank_matches(items, target_year, target_is_series)
    return ranked[0] if ranked else None

@app.get("/api/search")
def search_media(q: str = Query(..., min_length=1)) -> List[Dict[str, Any]]:
    """Searches across all sources in parallel with robust title/year deduplication."""
    all_items = []
    with ThreadPoolExecutor(max_workers=5) as executor:
        f_bazon = executor.submit(bazon.search, q)
        f_torrents = executor.submit(torrents.search, q)
        f_rezka = executor.submit(hdrezka.search, q)
        f_filmix = executor.submit(filmix.search, q)
        f_videocdn = executor.submit(videocdn.search, q)

        for f in [f_bazon, f_torrents, f_rezka, f_filmix, f_videocdn]:
            try:
                items = f.result(timeout=6)
                all_items.extend(items)
            except Exception:
                pass

    seen_kp = {}
    deduped_dict = {}

    for item in all_items:
        norm_title = normalize_search_title(item.title)
        if not norm_title:
            continue
        yr = item.year or 0

        # Check by kinopoisk ID
        if item.kinopoisk_id and item.kinopoisk_id in seen_kp:
            existing = seen_kp[item.kinopoisk_id]
            if item.is_series and not existing.get("is_series"):
                existing["is_series"] = True
            if not existing.get("poster") and item.poster:
                existing["poster"] = item.poster
            if not existing.get("description") and item.description:
                existing["description"] = item.description
            continue

        matched_key = None
        for (nt, y) in list(deduped_dict.keys()):
            if nt == norm_title:
                # Match if year is unknown in either, or release year within 1 year
                if y == 0 or yr == 0 or abs(y - yr) <= 1:
                    matched_key = (nt, y)
                    break

        if matched_key:
            existing = deduped_dict[matched_key]
            if item.is_series and not existing.get("is_series"):
                existing["is_series"] = True
            if not existing.get("poster") and item.poster:
                existing["poster"] = item.poster
            if not existing.get("description") and item.description:
                existing["description"] = item.description
            if (not existing.get("year") or existing.get("year") == 0) and yr:
                existing["year"] = yr
            if item.kinopoisk_id and not existing.get("kinopoisk_id"):
                existing["kinopoisk_id"] = item.kinopoisk_id
                seen_kp[item.kinopoisk_id] = existing
        else:
            key = (norm_title, yr)
            d_item = item.model_dump()
            deduped_dict[key] = d_item
            if item.kinopoisk_id:
                seen_kp[item.kinopoisk_id] = d_item

    res_list = list(deduped_dict.values())
    for it in res_list:
        p = str(it.get("poster") or "")
        if not p or "no_image_poster" in p or "noposter" in p:
            real_p = resolve_real_poster(it.get("title", ""), it.get("year"), it.get("kinopoisk_id"))
            if real_p:
                it["poster"] = real_p
    return res_list

_poster_cache: Dict[str, str] = {}

def resolve_real_poster(title: str, year: Optional[Any] = None, kp_id: Optional[str] = None) -> Optional[str]:
    """Finds a valid high-resolution poster for a media item, resolving placeholders like no_image_poster.png."""
    if not title:
        return None
    year_int = safe_parse_year(year)
    cache_key = f"{title.strip().lower()}_{year_int or 0}"
    if cache_key in _poster_cache:
        return _poster_cache[cache_key]

    clean_t = title.split(":")[0].strip() if ":" in title else title
    if " - " in clean_t:
        clean_t = clean_t.split(" - ")[0].strip()

    # 1. Quick search via HDRezka
    try:
        rz_matches = hdrezka.search(clean_t)
        for it in rz_matches:
            if it.poster and "no_image_poster" not in it.poster and "noposter" not in it.poster:
                it_year = safe_parse_year(it.year)
                if not year_int or not it_year or abs(it_year - year_int) <= 1:
                    _poster_cache[cache_key] = it.poster
                    return it.poster
    except Exception:
        pass

    # 2. Quick search via Filmix
    try:
        fx_matches = filmix.search(clean_t)
        for it in fx_matches:
            if it.poster and "no_image_poster" not in it.poster and "noposter" not in it.poster:
                it_year = safe_parse_year(it.year)
                if not year_int or not it_year or abs(it_year - year_int) <= 1:
                    _poster_cache[cache_key] = it.poster
                    return it.poster
    except Exception:
        pass

    # 3. Check initial_catalog.json for verified high-res Kinopoisk avatars
    try:
        init_cat_path = os.path.join(CURRENT_DIR, "static", "initial_catalog.json")
        if os.path.exists(init_cat_path):
            with open(init_cat_path, "r", encoding="utf-8") as f:
                init_items = json.load(f)
                for item in init_items:
                    if item.get("title", "").lower() in clean_t.lower() or clean_t.lower() in item.get("title", "").lower():
                        if item.get("poster"):
                            _poster_cache[cache_key] = item["poster"]
                            return item["poster"]
    except Exception:
        pass

    # 4. Fallback to Kinopoisk Unofficial / Yandex search proxy
    if kp_id and str(kp_id).isdigit():
        kp_poster = f"https://kinopoiskapiunofficial.tech/images/posters/kp/{kp_id}.jpg"
        _poster_cache[cache_key] = kp_poster
        return kp_poster

    return None

@app.get("/api/media/poster")
def get_media_poster(title: str = Query(...), year: Optional[str] = None, kp_id: Optional[str] = None) -> Dict[str, Any]:
    poster = resolve_real_poster(title, year, kp_id)
    return {"success": bool(poster), "poster": poster or "/noposter.png"}

@app.api_route("/api/updates/check", methods=["GET", "HEAD"])
@app.api_route("/version.json", methods=["GET", "HEAD"])
def check_updates() -> Dict[str, Any]:
    return {
        "success": True,
        "version_name": "2.5.0",
        "version_code": 36,
        "force_update": True,
        "min_version_code": 36,
        "apk_url": "https://showhub-server.onrender.com/ShowHub.apk",
        "download_url": "https://showhub-server.onrender.com/ShowHub.apk",
        "changelog": "ShowHub TV v2.5.0: Полный паритет (сетка 6x2 со скроллом, предпросмотр видео на карточках, полноценные настройки с темами и Filmix PRO, прямые потоки через нативный резолвер на ТВ, сортировка свежих новинок без старых мыльных опер)."
    }

@app.get("/api/catalog/stats")
def get_catalog_stats() -> Dict[str, Any]:
    """Returns total estimated library size for UI counter."""
    return {
        "total_movies": 18450,
        "total_series": 6210,
        "total_cartoons": 3120,
        "total_anime": 2480,
        "total_all": 30260
    }

_catalog_cache: Dict[str, Tuple[float, List[Dict[str, Any]]]] = {}
_CATALOG_CACHE_TTL = 300  # 5 minutes in-memory cache for ultra-fast TV rendering

@app.get("/api/catalog")
def get_catalog(
    category: str = "all",
    genre: Optional[str] = None,
    year: Optional[str] = None,
    country: Optional[str] = None,
    content_type: Optional[str] = "all",
    min_rating: Optional[float] = None,
    sort_by: Optional[str] = "newest",
    page: int = 1
) -> List[Dict[str, Any]]:
    """
    Returns dynamic fresh releases (новинки) and catalog items aggregated across live sources.
    Supports filtering by genre, country, content type (movies/series/cartoons/anime), release year, minimum rating, and sorting.
    """
    cache_key = f"{category}_{genre}_{year}_{country}_{content_type}_{min_rating}_{sort_by}_{page}"
    now_ts = time.time()
    if cache_key in _catalog_cache:
        cached_time, cached_items = _catalog_cache[cache_key]
        if now_ts - cached_time < _CATALOG_CACHE_TTL:
            return cached_items

    all_items = []
    seen_ids = set()
    seen_titles = set()

    # Determine effective category based on content_type if category is 'all'
    eff_category = category
    if category == "all":
        if content_type == "series":
            eff_category = "series"
        elif content_type == "movie":
            eff_category = "movies"
        elif content_type == "cartoons":
            eff_category = "cartoons"
        elif content_type == "anime":
            eff_category = "anime"

    # Pages to query from sources: fetch 2 pages on page 1 to provide 150-200+ deep titles immediately
    pages_to_fetch = [1, 2] if page == 1 else [page]

    for p in pages_to_fetch:
        # 1. Fetch live releases from HDRezka
        try:
            rz_items = hdrezka.get_catalog(category=eff_category, genre=genre, page=p)
            for it in rz_items:
                t_key = it.title.lower().strip()
                if t_key not in seen_titles and it.id not in seen_ids:
                    seen_titles.add(t_key)
                    seen_ids.add(it.id)
                    all_items.append(it.model_dump())
        except Exception:
            pass

        # 2. Fetch from Bazon catalog and merge
        try:
            b_items = bazon.get_catalog(category=eff_category, genre=genre, page=p)
            for it in b_items:
                t_key = it.title.lower().strip()
                if t_key not in seen_titles and it.id not in seen_ids:
                    seen_titles.add(t_key)
                    seen_ids.add(it.id)
                    all_items.append(it.model_dump())
        except Exception:
            pass

        # 3. Fetch from Filmix catalog and merge
        try:
            fx_items = filmix.get_catalog(category=eff_category, genre=genre, page=p)
            for it in fx_items:
                t_key = it.title.lower().strip()
                if t_key not in seen_titles and it.id not in seen_ids:
                    seen_titles.add(t_key)
                    seen_ids.add(it.id)
                    all_items.append(it.model_dump())
        except Exception:
            pass

    # 3. Apply Strict Genre Filtering
    if genre and genre != "all":
        g_clean = genre.lower().strip()
        # Stem base for Russian morphology (e.g. "боевик" -> "боевик", "комедия" -> "комед")
        stem = g_clean
        if g_clean.endswith(("ия", "ии", "ые", "ий", "ка", "ки")):
            stem = g_clean[:-2]
        elif g_clean.endswith(("а", "ы", "и", "я")):
            stem = g_clean[:-1]

        def match_genre(it):
            meta_genre = str(it.get("extra_data", {}).get("genre") or "").lower()
            desc = str(it.get("description") or "").lower()
            return (stem in meta_genre) or (stem in desc) or (g_clean in meta_genre) or (g_clean in desc)

        all_items = [it for it in all_items if match_genre(it)]

    # 4. Apply Country Filtering
    if country and country != "all":
        c_clean = country.lower().strip()
        # Common aliases
        aliases = [c_clean]
        if "коре" in c_clean:
            aliases.extend(["корея", "южная корея", "korea"])
        elif "сша" in c_clean:
            aliases.extend(["сша", "usa", "америк"])
        elif "росси" in c_clean:
            aliases.extend(["россия", "ссср", "russia"])
        elif "великобрит" in c_clean:
            aliases.extend(["великобритания", "англия", "uk"])

        def match_country(it):
            meta_c = str(it.get("extra_data", {}).get("country") or "").lower()
            desc = str(it.get("description") or "").lower()
            return any(a in meta_c or a in desc for a in aliases)

        all_items = [it for it in all_items if match_country(it)]

    # 5. Apply Content Type (Movie vs Series) Filtering
    if content_type == "movie":
        all_items = [it for it in all_items if not it.get("is_series")]
    elif content_type == "series":
        all_items = [it for it in all_items if it.get("is_series")]

    # 6. Apply Year Filtering
    if year and year != "all":
        def match_year(it):
            y = it.get("year")
            if not y:
                return False
            if year.isdigit():
                return int(y) == int(year)
            if year == "2020-2022":
                return 2020 <= int(y) <= 2022
            if year == "2010s":
                return 2010 <= int(y) <= 2019
            if year == "2000s":
                return 2000 <= int(y) <= 2009
            if year == "before_2000":
                return int(y) < 2000
            return True

        all_items = [it for it in all_items if match_year(it)]

    # 7. Apply Rating Filtering
    if min_rating and min_rating > 0:
        all_items = [
            it for it in all_items
            if (it.get("rating_kp") or 0) >= min_rating or (it.get("rating_imdb") or 0) >= min_rating
        ]

    # 8. Apply Sorting
    now = datetime.datetime.now()
    now_ts = int(now.timestamp())
    current_year = now.year

    def compute_freshness(it):
        # Strict release year hierarchy: 2026 > 2025 > 2024 > 2023 > 2022
        raw_y = it.get("year")
        try:
            y = int(raw_y) if raw_y else (current_year - 6)
        except Exception:
            y = current_year - 6

        # Each year is worth 1,000,000,000 points - strictly dominates
        year_score = y * 1_000_000_000

        # Receipt date timestamp (typically ~1.7e9, within reasonable bounds)
        da = it.get("date_added") or 0
        if isinstance(da, (int, float)):
            if da > now_ts + 86400 * 30:  # Future timestamp sanity check
                da = now_ts
            date_score = int(da)
        else:
            date_score = 0

        # Real poster bonus: items with valid covers are boosted over missing/placeholder covers
        poster_str = str(it.get("poster") or "")
        has_real_poster = bool(poster_str and "no_image_poster" not in poster_str and "noposter" not in poster_str)
        poster_bonus = 50_000_000 if has_real_poster else 0

        # Moderate bonus for fresh series episodes ONLY for current or previous year
        series_bonus = 0
        if it.get("is_series") and y >= current_year - 1:
            ep_info = str(it.get("episodes_info") or "")
            if ep_info:
                ep_m = re.search(r'(\d+)\s*сер', ep_info, re.I)
                if ep_m:
                    series_bonus += min(int(ep_m.group(1)), 30) * 100_000
            if y == current_year:
                series_bonus += 5_000_000

        return year_score + date_score + poster_bonus + series_bonus

    if sort_by == "rating":
        all_items.sort(
            key=lambda x: max(x.get("rating_kp") or 0, x.get("rating_imdb") or 0),
            reverse=True
        )
    elif sort_by == "year":
        all_items.sort(key=lambda x: x.get("year") or 0, reverse=True)
    elif sort_by == "popular":
        all_items.sort(
            key=lambda x: (x.get("rating_kp") or 0) * (1.5 if x.get("poster") else 1.0),
            reverse=True
        )
    else:  # "newest" / default fresh releases
        all_items.sort(key=compute_freshness, reverse=True)
    if not all_items:
        try:
            init_cat_path = os.path.join(CURRENT_DIR, "static", "initial_catalog.json")
            if os.path.exists(init_cat_path):
                with open(init_cat_path, "r", encoding="utf-8") as f:
                    all_items = json.load(f)
        except Exception:
            pass

    for it in all_items:
        p = str(it.get("poster") or "")
        if not p or "no_image_poster" in p or "noposter" in p:
            real_p = resolve_real_poster(it.get("title", ""), it.get("year"), it.get("kinopoisk_id"))
            if real_p:
                it["poster"] = real_p

    _catalog_cache[cache_key] = (now_ts, all_items)
    return all_items

@app.post("/api/favorites/check-updates")
def check_favorites_updates(favs: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    Checks if there are newly released episodes for favorite series.
    Returns list of items that have updates.
    """
    updates = []
    for item in favs:
        if not item.get("isSeries") and not item.get("is_series"):
            continue
        m_id = str(item.get("id") or "")
        title = item.get("title")
        source = item.get("source") or "hdrezka"
        last_s = int(item.get("lastSeason") or item.get("season") or 1)
        last_e = int(item.get("lastEpisode") or item.get("episode") or 0)

        try:
            det = _fetch_media_details(source=source, media_id=m_id, title=title)
            seasons = det.get("seasons") or []
            max_s = 0
            max_e = 0
            for s in seasons:
                s_id = int(s.get("season_id") or 0)
                if s_id > max_s:
                    max_s = s_id
                for ep in s.get("episodes") or []:
                    e_id = int(ep.get("episode_id") or 0)
                    if s_id == max_s and e_id > max_e:
                        max_e = e_id

            # Check if there is an episode newer than user's last known
            if (max_s > last_s) or (max_s == last_s and max_e > last_e):
                updates.append({
                    "id": m_id,
                    "title": title or det.get("title"),
                    "latest_season": max_s,
                    "latest_episode": max_e,
                    "new_episodes_count": (max_e - last_e) if max_s == last_s else max_e
                })
        except Exception:
            pass
    return updates

def _fetch_media_details(
    source: str,
    media_id: str,
    title: Optional[str] = None,
    year: Optional[Any] = None,
    is_series: Optional[Any] = None,
    kp_id: Optional[str] = None
) -> Dict[str, Any]:
    """Returns rich metadata, ratings, cast, seasons, episodes, and translators unified across sources."""
    year_int = safe_parse_year(year)
    is_ser_bool = bool(int(is_series)) if str(is_series).isdigit() else (bool(is_series) if is_series is not None else None)

    details: Dict[str, Any] = {
        "media_id": media_id,
        "source_name": source,
        "title": title or "Медиа",
        "description": None,
        "rating_kp": None,
        "rating_imdb": None,
        "vote_num_kp": None,
        "vote_num_imdb": None,
        "genres": [],
        "director": None,
        "actors": None,
        "country": None,
        "translators": [],
        "seasons": [],
        "is_series": bool(is_ser_bool),
        "poster": None,
        "episodes_schedule": []
    }

    clean_title = title.split(":")[0].strip() if (title and ":" in title) else title
    if clean_title and " - " in clean_title:
        clean_title = clean_title.split(" - ")[0].strip()

    # Determine real Kinopoisk ID if available
    resolved_kp = kp_id
    if not resolved_kp and source in ["bazon", "videocdn", "delivembd"] and media_id.isdigit():
        resolved_kp = media_id
    if not resolved_kp and clean_title:
        try:
            b_items = bazon.search(clean_title)
            b_match = find_best_match(b_items, year_int, is_ser_bool)
            if b_match and b_match.kinopoisk_id:
                resolved_kp = b_match.kinopoisk_id
        except Exception:
            pass

    # 1. Fetch Bazon details (ratings, synopsis, cast, genres)
    if resolved_kp:
        try:
            b_info = bazon.get_details(resolved_kp)
            if b_info:
                for k, v in b_info.items():
                    if v is not None:
                        details[k] = v
                if b_info.get("poster"):
                    details["poster"] = b_info["poster"]
        except Exception:
            pass

    # 2. Fetch HDRezka details (translators, seasons & episodes, high-res poster)
    try:
        rz_id = media_id if (source == "hdrezka" and media_id.startswith("http")) else None
        if not rz_id and clean_title:
            rz_items = hdrezka.search(clean_title)
            rz_match = find_best_match(rz_items, year_int, is_ser_bool)
            if rz_match:
                rz_id = rz_match.id
        if rz_id:
            rz_det = hdrezka.get_media_details(rz_id)
            if rz_det:
                if rz_det.get("poster"):
                    details["poster"] = rz_det["poster"]
                if rz_det.get("translators"):
                    details["translators"] = rz_det["translators"]
                if rz_det.get("seasons"):
                    details["seasons"] = rz_det["seasons"]
                    details["is_series"] = True
                if not details["description"] and rz_det.get("description"):
                    details["description"] = rz_det["description"]
                if not details["rating_kp"] and rz_det.get("rating_kp"):
                    details["rating_kp"] = rz_det["rating_kp"]
                    details["vote_num_kp"] = rz_det.get("vote_num_kp")
                if not details["rating_imdb"] and rz_det.get("rating_imdb"):
                    details["rating_imdb"] = rz_det["rating_imdb"]
                    details["vote_num_imdb"] = rz_det.get("vote_num_imdb")
                if not details["director"] and rz_det.get("director"):
                    details["director"] = rz_det["director"]
                if not details["actors"] and rz_det.get("actors"):
                    details["actors"] = rz_det["actors"]
                if not details["genres"] and rz_det.get("genres"):
                    details["genres"] = rz_det["genres"]
                if not details["country"] and rz_det.get("country"):
                    details["country"] = rz_det["country"]
                if rz_det.get("episodes_schedule"):
                    details["episodes_schedule"] = rz_det["episodes_schedule"]
    except Exception:
        pass

    # 3. Fetch Filmix details and merge
    try:
        fx_id = media_id if (source == "filmix" and media_id.isdigit()) else None
        if not fx_id and clean_title:
            fx_items = filmix.search(clean_title)
            fx_match = find_best_match(fx_items, year_int, is_ser_bool)
            if fx_match:
                fx_id = fx_match.id
        if fx_id:
            fx_res = filmix.get_streams(fx_id)
            if fx_res.audio_tracks:
                existing_trans_names = {t.get("name", "").lower() for t in details["translators"]}
                for t in fx_res.audio_tracks:
                    if t.name.lower() not in existing_trans_names:
                        details["translators"].append(t.model_dump())
            if fx_res.seasons:
                details["is_series"] = True
                if not details["seasons"]:
                    details["seasons"] = [s.model_dump() for s in fx_res.seasons]
                else:
                    # Merge episodes for each season
                    existing_seasons_map = {s.get("season_id", s.get("season_number")): s for s in details["seasons"]}
                    for fx_s in fx_res.seasons:
                        s_id = fx_s.season_number
                        if s_id in existing_seasons_map:
                            cur_s = existing_seasons_map[s_id]
                            cur_eps_ids = {e.get("episode_id", e.get("episode_number")) for e in cur_s.get("episodes", [])}
                            for ep in fx_s.episodes:
                                if ep.episode_number not in cur_eps_ids:
                                    cur_s.get("episodes", []).append({
                                        "episode_id": ep.episode_number,
                                        "title": ep.name or f"Серия {ep.episode_number}",
                                        "season_id": s_id
                                    })
                        else:
                            details["seasons"].append(fx_s.model_dump())
    except Exception:
        pass

    return details


def _fetch_media_comments(source: str, media_id: str, title: Optional[str] = None) -> List[Dict[str, Any]]:
    """Returns viewer comments and reviews (scraped from Filmix)."""
    try:
        comments = filmix.get_comments(media_id, title=title)
        return [c.model_dump() for c in comments]
    except Exception:
        return []


def _fetch_media_streams(
    source: str,
    media_id: str,
    title: Optional[str] = None,
    season: Optional[int] = None,
    episode: Optional[int] = None,
    audio_id: Optional[str] = None,
    year: Optional[Any] = None,
    is_series: Optional[Any] = None,
    kp_id: Optional[str] = None
) -> Dict[str, Any]:
    """
    Resolves streams for a given media from ALL available sources.
    Supports season, episode, and audio_id for serials and multi-track movies.
    """
    year_int = safe_parse_year(year)
    is_ser_bool = bool(int(is_series)) if str(is_series).isdigit() else (bool(is_series) if is_series is not None else None)

    resolved: Dict[str, Any] = {}
    resolved_kp = kp_id
    if not resolved_kp and source in ["bazon", "videocdn", "delivembd"] and media_id.isdigit():
        resolved_kp = media_id

    clean_title = title.split(":")[0].strip() if (title and ":" in title) else title
    if clean_title and " - " in clean_title:
        clean_title = clean_title.split(" - ")[0].strip()

    titles_to_try = [clean_title] if clean_title else []
    if title and title not in titles_to_try:
        titles_to_try.append(title)

    if not resolved_kp and titles_to_try:
        for t_query in titles_to_try:
            try:
                b_items = bazon.search(t_query)
                b_match = find_best_match(b_items, year_int, is_ser_bool)
                if b_match and b_match.kinopoisk_id:
                    resolved_kp = b_match.kinopoisk_id
                    break
            except Exception:
                pass

    # 1. Filmix streams
    try:
        candidate_fx_ids = []
        if source == "filmix" and media_id.isdigit():
            candidate_fx_ids.append(media_id)
        if titles_to_try:
            for t_query in titles_to_try:
                fx_items = filmix.search(t_query)
                for it in rank_matches(fx_items, year_int, is_ser_bool):
                    if it.id not in candidate_fx_ids:
                        candidate_fx_ids.append(it.id)
        for fx_id in candidate_fx_ids[:3]:
            fx_streams = filmix.get_streams(fx_id, season=season, episode=episode, audio_id=audio_id)
            if fx_streams.streams:
                resolved["filmix"] = fx_streams.model_dump()
                break
    except Exception:
        pass

    # 2. HDRezka streams
    try:
        candidate_rz_ids = []
        if source == "hdrezka" and media_id.startswith("http"):
            candidate_rz_ids.append(media_id)
        if titles_to_try:
            for t_query in titles_to_try:
                rz_items = hdrezka.search(t_query)
                for it in rank_matches(rz_items, year_int, is_ser_bool):
                    if it.id not in candidate_rz_ids:
                        candidate_rz_ids.append(it.id)
        for rz_id in candidate_rz_ids[:3]:
            rz_streams = hdrezka.get_streams(rz_id, season=season, episode=episode, audio_id=audio_id)
            if rz_streams.streams:
                resolved["hdrezka"] = rz_streams.model_dump()
                break
    except Exception:
        pass

    # 3. VideoCDN streams (Full HD Embed)
    try:
        vc_id = resolved_kp
        if not vc_id and titles_to_try:
            for t_query in titles_to_try:
                vc_items = videocdn.search(t_query)
                vc_match = find_best_match(vc_items, year_int, is_ser_bool)
                if vc_match and vc_match.kinopoisk_id:
                    vc_id = vc_match.kinopoisk_id
                    break
        if vc_id:
            vc_streams = videocdn.get_streams(vc_id)
            if vc_streams.streams or vc_streams.embed_url:
                resolved["videocdn"] = vc_streams.model_dump()
    except Exception:
        pass

    # 4. Delivembd streams (if KP ID available)
    if resolved_kp:
        try:
            d_streams = delivembd.get_streams(resolved_kp)
            if d_streams.streams or d_streams.embed_url:
                resolved["delivembd"] = d_streams.model_dump()
        except Exception:
            pass

    # 5. Bazon streams
    try:
        b_id = resolved_kp
        if not b_id and clean_title:
            b_items = bazon.search(clean_title)
            b_match = find_best_match(b_items, year_int, is_ser_bool)
            if b_match and b_match.kinopoisk_id:
                b_id = b_match.kinopoisk_id
        if b_id:
            b_streams = bazon.get_streams(b_id)
            if b_streams.streams or b_streams.embed_url:
                resolved["bazon"] = b_streams.model_dump()
    except Exception:
        pass

    # 6. Torrents streams
    if clean_title:
        try:
            torr_items = torrents.search(clean_title)
            if torr_items:
                torr_streams = [
                    {
                        "quality": f"{t.extra_data.get('size', '')} (Seeds: {t.extra_data.get('seeds', '0')})",
                        "url": t.extra_data.get("stream_url", ""),
                        "stream_type": "torrent",
                        "headers": {}
                    }
                    for t in torr_items[:5]
                ]
                resolved["torrents"] = {
                    "source_name": "Rutor / TorrServe",
                    "media_id": media_id,
                    "title": title,
                    "streams": torr_streams,
                    "embed_url": None,
                    "error": None
                }
        except Exception:
            pass

    # Tag streams requiring Premium (4K, Ultra, 2160p, 1440p, rhtie.mp4 teaser, Filmix 1080p without PRO)
    for src_name, src_data in resolved.items():
        if isinstance(src_data, dict) and "streams" in src_data and src_data["streams"]:
            for s in src_data["streams"]:
                q = str(s.get("quality", "")).lower()
                u = str(s.get("url", "")).lower()
                if "rhtie.mp4" in u or any(k in q for k in ["ultra", "4k", "2160p", "1440p"]) or (src_name == "filmix" and "1080p" in q):
                    s["is_premium"] = True
                else:
                    s["is_premium"] = False

    return resolved


# --- Endpoints supporting both Query parameters (URL-safe) and Legacy Path parameters ---

@app.get("/api/media/details")
def get_media_details_query(
    source: str = Query("bazon"),
    media_id: str = Query(...),
    title: Optional[str] = None,
    year: Optional[str] = None,
    is_series: Optional[str] = None,
    kp_id: Optional[str] = None
) -> Dict[str, Any]:
    return _fetch_media_details(source, media_id, title, year, is_series, kp_id)

@app.get("/api/media/{source}/{media_id}/details")
def get_media_details_path(source: str, media_id: str, title: Optional[str] = None, year: Optional[str] = None, is_series: Optional[str] = None, kp_id: Optional[str] = None) -> Dict[str, Any]:
    return _fetch_media_details(source, media_id, title, year, is_series, kp_id)


@app.get("/api/media/comments")
def get_media_comments_query(
    source: str = Query("filmix"),
    media_id: str = Query(...),
    title: Optional[str] = None
) -> List[Dict[str, Any]]:
    return _fetch_media_comments(source, media_id, title)

@app.get("/api/media/{source}/{media_id}/comments")
def get_media_comments_path(source: str, media_id: str, title: Optional[str] = None) -> List[Dict[str, Any]]:
    return _fetch_media_comments(source, media_id, title)


@app.get("/api/media/streams")
def get_media_streams_query(
    source: str = Query(...),
    media_id: str = Query(...),
    title: Optional[str] = None,
    season: Optional[int] = None,
    episode: Optional[int] = None,
    audio_id: Optional[str] = None,
    year: Optional[str] = None,
    is_series: Optional[str] = None,
    kp_id: Optional[str] = None
) -> Dict[str, Any]:
    return _fetch_media_streams(source, media_id, title, season, episode, audio_id, year, is_series, kp_id)

@app.get("/api/media/{source}/{media_id}/streams")
def get_media_streams_path(
    source: str,
    media_id: str,
    title: Optional[str] = None,
    season: Optional[int] = None,
    episode: Optional[int] = None,
    audio_id: Optional[str] = None,
    year: Optional[str] = None,
    is_series: Optional[str] = None,
    kp_id: Optional[str] = None
) -> Dict[str, Any]:
    return _fetch_media_streams(source, media_id, title, season, episode, audio_id, year, is_series, kp_id)


@app.get("/api/media/trailer")
def get_media_trailer(title: str = Query(...), year: Optional[str] = None, kp_id: Optional[str] = None) -> Dict[str, Any]:
    """Resolves trailer for video, extracting YouTube video ID and returning clean embed URL."""
    import urllib.parse
    import urllib.request
    import re

    clean_title = title.split(":")[0].strip() if ":" in title else title
    if " - " in clean_title:
        clean_title = clean_title.split(" - ")[0].strip()

    search_query = f"{clean_title} {year or ''} русский трейлер".strip()
    encoded = urllib.parse.quote(search_query)
    yt_url = f"https://www.youtube.com/results?search_query={encoded}"

    try:
        req = urllib.request.Request(yt_url, headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        })
        with urllib.request.urlopen(req, timeout=4) as resp:
            html = resp.read().decode("utf-8", errors="ignore")
            matches = re.findall(r'"videoId":"([a-zA-Z0-9_-]{11})"', html)
            if matches:
                v_id = matches[0]
                yt_watch_url = f"https://www.youtube.com/watch?v={v_id}"
                return {
                    "success": True,
                    "title": f"Трейлер: {title}",
                    "video_id": v_id,
                    "embed_url": f"https://www.youtube.com/embed/{v_id}?autoplay=1&enablejsapi=1&playsinline=1&rel=0",
                    "web_url": yt_watch_url,
                    "app_url": yt_watch_url,
                    "intent_url": f"intent://www.youtube.com/watch?v={v_id}#Intent;scheme=https;end"
                }
    except Exception as e:
        pass

    return {
        "success": False,
        "message": "Трейлер не найден"
    }

_preview_cache: Dict[str, Dict[str, Any]] = {}

@app.get("/api/media/preview-stream")
def get_media_preview_stream(
    title: str = Query(...),
    source: Optional[str] = None,
    media_id: Optional[str] = None,
    kp_id: Optional[str] = None,
    year: Optional[str] = None,
    is_series: Optional[str] = None
) -> Dict[str, Any]:
    """Returns a fast silent preview direct video stream (HLS/MP4) for TV card hover. Strictly no trailers or iframes."""
    cache_key = f"{source}_{media_id}_{kp_id}_{title}_{year}_{is_series}"
    if cache_key in _preview_cache:
        return _preview_cache[cache_key]

    # Try to find direct stream (HDRezka, Filmix, Bazon)
    # Prefer lightweight SD 480p/360p/720p or standard 1080p, strictly excluding Ultra/4K/2160p/1440p
    clean_title = title.split(":")[0].strip() if ":" in title else title
    if " - " in clean_title:
        clean_title = clean_title.split(" - ")[0].strip()

    candidate_streams = []

    def is_usable_preview_stream(st):
        if not st or not getattr(st, "url", None):
            return False
        if getattr(st, "stream_type", "hls") not in ["hls", "mp4"]:
            return False
        u = str(st.url).lower()
        if "rhtie.mp4" in u or "rhtie" in u:
            return False
        if getattr(st, "is_premium", False):
            return False
        q = str(st.quality).lower()
        if any(bad in q for bad in ["ultra", "4k", "2160", "1440"]):
            return False
        return True

    # Source 1: Direct HDRezka if provided
    try:
        if source == "hdrezka" and media_id:
            rz_res = hdrezka.get_streams(media_id, season=1, episode=1)
            if rz_res.streams:
                valid_rz = [s for s in rz_res.streams if is_usable_preview_stream(s)]
                if valid_rz:
                    candidate_streams.extend(valid_rz)
    except Exception:
        pass

    # Source 2: Search HDRezka by title & year
    if not candidate_streams and clean_title:
        try:
            rz_items = hdrezka.search(clean_title)
            rz_match = find_best_match(rz_items, year, is_series)
            if rz_match:
                rz_res = hdrezka.get_streams(rz_match.id, season=1, episode=1)
                if rz_res.streams:
                    valid_rz = [s for s in rz_res.streams if is_usable_preview_stream(s)]
                    if valid_rz:
                        candidate_streams.extend(valid_rz)
        except Exception:
            pass

    # Source 3: Filmix by numeric media_id or title search
    if not candidate_streams:
        try:
            fx_id = media_id if (source == "filmix" and media_id and media_id.isdigit()) else None
            if not fx_id and clean_title:
                fx_items = filmix.search(clean_title)
                fx_match = find_best_match(fx_items, year, is_series)
                if fx_match:
                    fx_id = fx_match.id
            if fx_id:
                fx_res = filmix.get_streams(fx_id, season=1, episode=1)
                if fx_res.streams:
                    valid_fx = [s for s in fx_res.streams if is_usable_preview_stream(s)]
                    if valid_fx:
                        candidate_streams.extend(valid_fx)
        except Exception:
            pass

    # Source 4: Bazon if real KP ID
    if not candidate_streams and (kp_id or (source in ["bazon", "videocdn", "delivembd"] and media_id and media_id.isdigit())):
        target_id = kp_id or media_id
        try:
            b_res = bazon.get_streams(target_id)
            if b_res.streams:
                valid_b = [s for s in b_res.streams if is_usable_preview_stream(s)]
                if valid_b:
                    candidate_streams.extend(valid_b)
        except Exception:
            pass

    # Filter out Ultra / 4K / 2160p / 1440p
    if candidate_streams:
        chosen = None
        # Preference: 480p -> 360p -> 720p -> 1080p standard
        for target_q in ["480p", "480", "360p", "360", "720p", "720", "1080p", "1080"]:
            for s in candidate_streams:
                if not is_usable_preview_stream(s):
                    continue
                q = str(s.quality).lower()
                if target_q in q:
                    chosen = s
                    break
            if chosen:
                break

        if not chosen:
            for s in candidate_streams:
                if is_usable_preview_stream(s):
                    chosen = s
                    break

        if chosen:
            # 22nd minute of movie: 22 * 60 = 1320 seconds
            res = {
                "success": True,
                "stream_url": chosen.url,
                "stream_type": chosen.stream_type,
                "quality": chosen.quality,
                "start_time": 1320,
                "title": title
            }
            _preview_cache[cache_key] = res
            return res

    res = {"success": False, "message": "No direct preview stream available"}
    _preview_cache[cache_key] = res
    return res

@app.get("/api/account/filmix")
def get_filmix_account() -> Dict[str, Any]:
    """Returns current Filmix account status and profile info."""
    return filmix.refresh_profile()

@app.post("/api/account/filmix/login")
def login_filmix(payload: Dict[str, str]) -> Dict[str, Any]:
    """Logs into Filmix using login credentials (username / password)."""
    login_name = payload.get("login_name", "").strip()
    login_password = payload.get("login_password", "").strip()
    if not login_name or not login_password:
        raise HTTPException(status_code=400, detail="Логин и пароль обязательны")
    return filmix.login(login_name, login_password)

@app.post("/api/account/filmix/cookies")
def set_filmix_cookies(payload: Dict[str, Any]) -> Dict[str, Any]:
    """Imports Filmix session cookies directly."""
    cookies = payload.get("cookies", {})
    if isinstance(cookies, str):
        c_dict = {}
        for part in cookies.split(";"):
            if "=" in part:
                k, v = part.strip().split("=", 1)
                c_dict[k.strip()] = v.strip()
        cookies = c_dict
    return filmix.set_cookies(cookies)

@app.post("/api/account/filmix/logout")
def logout_filmix() -> Dict[str, Any]:
    """Logs out of Filmix and clears session."""
    filmix.logout()
    return {"success": True, "message": "Сессия очищена, выполнен выход из Filmix."}

@app.api_route("/health", methods=["GET", "HEAD"])
@app.api_route("/api/health", methods=["GET", "HEAD"])
def get_health() -> Dict[str, Any]:
    """Returns live Canary Health Check summary with rework warnings."""
    return health_checker.get_summary()

@app.post("/api/health/refresh")
def refresh_health() -> Dict[str, Any]:
    """Force re-runs Canary tests for all sources."""
    health_checker.run_checks()
    return health_checker.get_summary()

@app.get("/api/debug/stream-diag")
def debug_stream_diag(title: str = "Интерстеллар", year: Optional[str] = "2014"):
    import traceback
    diag = {}
    
    # 1. Filmix
    try:
        fx_items = filmix.search(title)
        diag["fx_search_count"] = len(fx_items)
        diag["fx_items"] = [{"id": it.id, "title": it.title, "year": it.year} for it in fx_items[:3]]
        if fx_items:
            fx_st = filmix.get_streams(fx_items[0].id)
            diag["fx_streams_count"] = len(fx_st.streams)
            diag["fx_streams_err"] = fx_st.error
            diag["fx_streams_sample"] = [s.model_dump() for s in fx_st.streams[:2]]
    except Exception:
        diag["fx_exception"] = traceback.format_exc()

    # 2. HDRezka
    try:
        rz_items = hdrezka.search(title)
        diag["rz_search_count"] = len(rz_items)
        diag["rz_items"] = [{"id": it.id, "title": it.title, "year": it.year} for it in rz_items[:3]]
        if rz_items:
            rz_id = rz_items[0].id
            diag["rz_id"] = rz_id
            base = hdrezka._get_base()
            diag["rz_base"] = base
            import urllib.parse
            parsed = urllib.parse.urlparse(rz_id)
            page_url = f"{base}{parsed.path}"
            diag["rz_page_url"] = page_url
            r_page = hdrezka._get_with_anubis(page_url, base)
            diag["rz_page_status"] = r_page.status_code
            diag["rz_page_len"] = len(r_page.text)
            diag["rz_has_anubis"] = "anubis_challenge" in r_page.text
            
            cdn_m = re.search(r'initCDN(?:Movies|Series)Events\(\s*(\d+)\s*,\s*(\d+).*?,\s*(\{.*?\})\s*\);', r_page.text, re.DOTALL)
            diag["rz_has_cdn_m"] = bool(cdn_m)
            if cdn_m:
                diag["rz_cdn_m_group1"] = cdn_m.group(1)
                diag["rz_cdn_m_group2"] = cdn_m.group(2)
                diag["rz_cdn_m_raw_group3"] = cdn_m.group(3)[:500]
                try:
                    ej = json.loads(cdn_m.group(3))
                    diag["rz_ej_keys"] = list(ej.keys())
                    diag["rz_ej_streams_val"] = str(ej.get("streams"))[:200]
                except Exception as ex:
                    diag["rz_ej_json_err"] = str(ex)
            
            id_match = re.search(r'data-id="(\d+)"', r_page.text)
            trans_match = re.search(r'data-translator_id="(\d+)"', r_page.text)
            diag["rz_data_id"] = id_match.group(1) if id_match else None
            diag["rz_trans_id"] = trans_match.group(1) if trans_match else None
            
            if id_match:
                t_now = int(time.time() * 1000)
                ajax_url = f"{base}/ajax/get_cdn_series/?t={t_now}"
                post_data = {
                    "id": id_match.group(1),
                    "translator_id": trans_match.group(1) if trans_match else "238",
                    "action": "get_movie"
                }
                post_headers = {
                    "X-Requested-With": "XMLHttpRequest",
                    "Referer": page_url
                }
                r_ajax = hdrezka.session.post(ajax_url, data=post_data, headers=post_headers, timeout=6)
                diag["rz_ajax_status"] = r_ajax.status_code
                diag["rz_ajax_text_preview"] = r_ajax.text[:300]

            rz_st = hdrezka.get_streams(rz_items[0].id)
            diag["rz_streams_count"] = len(rz_st.streams)
            diag["rz_streams_err"] = rz_st.error
            diag["rz_streams_sample"] = [s.model_dump() for s in rz_st.streams[:2]]
    except Exception:
        diag["rz_exception"] = traceback.format_exc()
        
    return diag

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("mediacenter.app:app", host="0.0.0.0", port=8000, reload=False)
