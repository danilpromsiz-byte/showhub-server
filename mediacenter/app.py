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
import threading
import logging
import urllib.parse
import urllib.request
import requests
from typing import List, Dict, Any, Optional, Tuple
from concurrent.futures import ThreadPoolExecutor

logger = logging.getLogger("mediacenter")

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
from mediacenter.sources.kodik import KodikSource
from mediacenter.sources.base import MediaItem, StreamResult
from mediacenter.core.tmdb import tmdb

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
kodik = KodikSource()

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
    """Returns dynamic curated trending & popular hits with verified ratings."""
    items = []
    seen_titles = set()
    try:
        tmdb_trending = tmdb.get_trending(page=1)
        for t in tmdb_trending:
            t_key = t["title"].lower().strip()
            if t_key not in seen_titles and t.get("poster"):
                seen_titles.add(t_key)
                items.append({
                    "id": f"tmdb_{t['tmdb_id']}",
                    "title": t["title"],
                    "original_title": t.get("original_title"),
                    "year": t.get("year"),
                    "poster": t.get("poster"),
                    "description": t.get("description"),
                    "rating": t.get("rating"),
                    "rating_imdb": t.get("rating"),
                    "rating_kp": t.get("rating"),
                    "is_series": t.get("is_series", False),
                    "source_name": "tmdb"
                })
    except Exception:
        pass

    try:
        pop_m = tmdb.get_popular_movies(page=1)
        for m in pop_m:
            t_key = m["title"].lower().strip()
            if t_key not in seen_titles and m.get("poster"):
                seen_titles.add(t_key)
                items.append({
                    "id": f"tmdb_{m['tmdb_id']}",
                    "title": m["title"],
                    "original_title": m.get("original_title"),
                    "year": m.get("year"),
                    "poster": m.get("poster"),
                    "description": m.get("description"),
                    "rating": m.get("rating"),
                    "rating_imdb": m.get("rating"),
                    "rating_kp": m.get("rating"),
                    "is_series": False,
                    "source_name": "tmdb"
                })
    except Exception:
        pass

    try:
        pop_s = tmdb.get_popular_series(page=1)
        for s in pop_s:
            t_key = s["title"].lower().strip()
            if t_key not in seen_titles and s.get("poster"):
                seen_titles.add(t_key)
                items.append({
                    "id": f"tmdb_{s['tmdb_id']}",
                    "title": s["title"],
                    "original_title": s.get("original_title"),
                    "year": s.get("year"),
                    "poster": s.get("poster"),
                    "description": s.get("description"),
                    "rating": s.get("rating"),
                    "rating_imdb": s.get("rating"),
                    "rating_kp": s.get("rating"),
                    "is_series": True,
                    "source_name": "tmdb"
                })
    except Exception:
        pass

    # Merge top catalog items
    try:
        cat_items = get_catalog(category="all", sort_by="popular", page=1)
        for c in cat_items:
            t_key = c.get("title", "").lower().strip()
            if t_key not in seen_titles:
                seen_titles.add(t_key)
                items.append(c)
    except Exception:
        pass

    return items[:40]

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

def classify_age_rating(
    title: str = "",
    desc: str = "",
    genres: Any = None,
    extra: Any = None,
    raw_limit: Optional[str] = None
) -> str:
    """
    Accurately classifies movie/series age rating (0+, 6+, 12+, 16+, 18+).
    Ensures that dark psychological thrillers, crime, murders, horror, and erotica
    (e.g., 'Парфюмер: История одного убийцы') are strictly marked 18+.
    """
    g_str = ""
    if isinstance(genres, list):
        g_str = " ".join(str(x) for x in genres)
    elif isinstance(genres, str):
        g_str = genres

    ex_str = ""
    if isinstance(extra, dict):
        ex_str = f"{extra.get('genre', '')} {extra.get('age_limit', '')} {extra.get('genres', '')} {extra.get('description', '')}"

    corpus = f"{title} {desc} {g_str} {ex_str}".lower()

    if raw_limit:
        r_clean = str(raw_limit).strip().upper()
        if r_clean in ["18+", "18", "R", "NC-17", "R-18", "X"]:
            return "18+"

    # 1. Strict 18+ Keywords & Themes (Horror, violent murder, serial killers, explicit eroticism, drugs)
    r18_keywords = [
        "18+", "18 плюс", "18 и старше", "r-rated", "nc-17",
        "парфюмер", "история одного убийцы", "убийц", "убийств", "маньяк", "расчлен",
        "потрошител", "кровав", "резня", "снафф", "пытки", "пыток", "бойня",
        "эротик", "порно", "секс", "интим", "разврат", "орги", "обнажен", "постельн",
        "наркоти", "кокаин", "героин", "передоз",
        "ужасы", "хоррор", "slasher", "слэшер", "gore", "людоед", "каннибал", "зомби"
    ]
    if any(kw in corpus for kw in r18_keywords):
        return "18+"

    # 2. 16+ Keywords & Themes (Action, crime, thriller, war, detectives)
    r16_keywords = [
        "16+", "16 плюс", "боевик", "детектив", "криминал", "триллер",
        "война", "военный", "мистика", "ограблен", "перестрелк",
        "мафия", "банда", "бандит", "жестокост", "action", "mystery",
        "драма", "психологическ", "суицид", "мрачн", "опасн"
    ]
    if any(kw in corpus for kw in r16_keywords):
        return "16+"

    # 3. 0+ Keywords (Infants, early childhood)
    r0_keywords = ["0+", "0 плюс", "для самых маленьких", "для малышей", "колыбельн"]
    if any(kw in corpus for kw in r0_keywords):
        return "0+"

    # 4. 6+ Keywords (Animation, family, fairy tales)
    r6_keywords = [
        "6+", "6 плюс", "мультфильм", "детский", "семейный", "сказка",
        "мультсериал", "анимация", "animation", "family", "kids"
    ]
    if any(kw in corpus for kw in r6_keywords):
        return "6+"

    # 5. 12+ Keywords (Adventure, fantasy, comedy, sci-fi)
    r12_keywords = [
        "12+", "12 плюс", "комедия", "фантастика", "фэнтези", "приключения",
        "мелодрама", "документальный", "спорт", "comedy", "adventure", "fantasy", "sci-fi"
    ]
    if any(kw in corpus for kw in r12_keywords):
        return "12+"

    if raw_limit and ("+" in str(raw_limit)):
        return str(raw_limit).strip()
    return "12+"

def compute_title_similarity(s1: str, s2: str) -> float:
    if not s1 or not s2:
        return 0.0
    n1 = normalize_search_title(s1)
    n2 = normalize_search_title(s2)
    if not n1 or not n2:
        return 0.0
    if n1 == n2:
        return 1.0
    if n1 in n2 or n2 in n1:
        return min(len(n1), len(n2)) / max(len(n1), len(n2))
    w1 = set(n1.split())
    w2 = set(n2.split())
    if not w1 or not w2:
        return 0.0
    intersection = w1 & w2
    union = w1 | w2
    jaccard = len(intersection) / len(union)
    overlap = len(intersection) / min(len(w1), len(w2))
    return max(jaccard, overlap * 0.7)

def rank_matches(items: list, target_year: Optional[Any] = None, target_is_series: Optional[Any] = None, target_title: Optional[str] = None) -> list:
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
        it_title = getattr(it, "title", "")

        # Title similarity matching
        if target_title and it_title:
            sim = compute_title_similarity(it_title, target_title)
            if sim < 0.40:
                return -9999  # Disqualify completely unrelated title matches
            score += int(sim * 200)

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

    filtered = [it for it in items if score_item(it) > -5000]
    return sorted(filtered, key=score_item, reverse=True)

def find_best_match(items: list, target_year: Optional[Any] = None, target_is_series: Optional[Any] = None, target_title: Optional[str] = None):
    ranked = rank_matches(items, target_year, target_is_series, target_title=target_title)
    if not ranked:
        return None
    best = ranked[0]
    if target_title:
        sim = compute_title_similarity(getattr(best, "title", ""), target_title)
        if sim < 0.40:
            return None
    return best

@app.get("/api/search")
def search_media(q: str = Query(..., min_length=1), type: Optional[str] = Query(None)) -> List[Dict[str, Any]]:
    """Searches across all sources in parallel with robust title/year deduplication, with dedicated actor filmography support."""
    # Check if this is an actor filmography search
    if type == "actor" or q.startswith("actor:"):
        actor_name = q.replace("actor:", "").strip()
        actor_films = []
        try:
            actor_films = tmdb.search_actor_filmography(actor_name)
        except Exception:
            pass

        try:
            k_actor_items = kodik.search_by_actor(actor_name)
            for kit in k_actor_items:
                actor_films.append(kit.model_dump())
        except Exception:
            pass

        if actor_films:
            seen_k = set()
            deduped = []
            for f in actor_films:
                k = (normalize_search_title(f.get("title", "")), f.get("year") or 0)
                if k not in seen_k:
                    seen_k.add(k)
                    deduped.append(f)
            return deduped[:60]

    all_items = []
    with ThreadPoolExecutor(max_workers=8) as executor:
        f_bazon = executor.submit(bazon.search, q)
        f_torrents = executor.submit(torrents.search, q)
        f_rezka = executor.submit(hdrezka.search, q)
        f_filmix = executor.submit(filmix.search, q)
        f_videocdn = executor.submit(videocdn.search, q)
        f_kodik = executor.submit(kodik.search, q)
        f_kodik_actor = executor.submit(kodik.search_by_actor, q)
        f_kodik_dir = executor.submit(kodik.search_by_director, q)

        for f in [f_bazon, f_torrents, f_rezka, f_filmix, f_videocdn, f_kodik, f_kodik_actor, f_kodik_dir]:
            try:
                items = f.result(timeout=6)
                all_items.extend(items)
            except Exception:
                pass

    # Search local catalog for actor, director, and title matches (filmography support)
    q_low = q.lower().strip()
    try:
        init_cat_path = os.path.join(CURRENT_DIR, "static", "initial_catalog.json")
        if os.path.exists(init_cat_path):
            with open(init_cat_path, "r", encoding="utf-8") as f:
                cat_list = json.load(f)
                for it in cat_list:
                    act_txt = str(it.get("actors") or it.get("extra_data", {}).get("actors") or "").lower()
                    dir_txt = str(it.get("director") or it.get("extra_data", {}).get("director") or "").lower()
                    tit_txt = str(it.get("title") or "").lower()
                    if q_low in act_txt or q_low in dir_txt or q_low in tit_txt:
                        all_items.append(MediaItem(
                            id=str(it.get("id")),
                            source_name=it.get("source_name", "kodik"),
                            title=it.get("title", ""),
                            year=it.get("year"),
                            is_series=bool(it.get("is_series")),
                            poster=it.get("poster"),
                            description=it.get("description"),
                            rating_kp=it.get("rating_kp"),
                            rating_imdb=it.get("rating_imdb"),
                            kinopoisk_id=it.get("kinopoisk_id"),
                            extra_data=it.get("extra_data") or {}
                        ))
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

    # Sort results by relevance to query q (exact match first, then prefix, then substring)
    qn = normalize_search_title(q)
    def search_relevance(item):
        t = normalize_search_title(item.get("title", ""))
        if not t or not qn:
            return 0
        if t == qn:
            return 1000
        if t.startswith(qn):
            return 800 - len(t)
        if qn in t:
            return 600 - len(t)
        w_t = set(t.split())
        w_q = set(qn.split())
        overlap = len(w_t & w_q)
        return overlap * 100 - len(t)

    res_list.sort(key=search_relevance, reverse=True)
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

    # 1. Top priority: Kinopoisk Unofficial if kp_id is available (crisp 1000x1500)
    if kp_id and str(kp_id).isdigit():
        kp_poster = f"https://kinopoiskapiunofficial.tech/images/posters/kp/{kp_id}.jpg"
        _poster_cache[cache_key] = kp_poster
        return kp_poster

    # 2. Bazon search (returns 1000x1500 high-res posters from i.kbd.so)
    try:
        b_matches = bazon.search(clean_t)
        b_match = find_best_match(b_matches, year_int, None)
        if b_match and b_match.poster and b_match.poster.startswith("http") and "no_image" not in b_match.poster:
            _poster_cache[cache_key] = b_match.poster
            return b_match.poster
    except Exception:
        pass

    # 3. Quick search via HDRezka
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

    # 4. Quick search via Filmix
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

    # 5. Check initial_catalog.json for verified high-res Kinopoisk avatars
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

    return None

@app.get("/api/media/poster")
def get_media_poster(title: str = Query(...), year: Optional[str] = None, kp_id: Optional[str] = None) -> Dict[str, Any]:
    poster = resolve_real_poster(title, year, kp_id)
    return {"success": bool(poster), "poster": poster or "/noposter.png"}


@app.api_route("/api/updates/check", methods=["GET", "HEAD"])
@app.api_route("/version.json", methods=["GET", "HEAD"])
def check_updates() -> Dict[str, Any]:
    try:
        base_dir = os.path.dirname(os.path.abspath(__file__))
        candidates = [
            os.path.join(base_dir, "static", "version.json"),
            os.path.join(base_dir, "version.json"),
            os.path.join(os.getcwd(), "mediacenter", "static", "version.json"),
            os.path.join(os.getcwd(), "version.json")
        ]
        for c in candidates:
            if os.path.exists(c):
                with open(c, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    if isinstance(data, dict) and data.get("version_code"):
                        return data
    except Exception as e:
        logger.warning(f"Failed to read version.json from disk: {e}")

    return {
        "success": True,
        "version_name": "2.8.8",
        "version_code": 67,
        "force_update": True,
        "min_version_code": 67,
        "apk_url": "https://showhub-server.onrender.com/ShowHub.apk",
        "download_url": "https://showhub-server.onrender.com/ShowHub.apk",
        "changelog": "ShowHub TV v2.8.7: Исправление фейковых озвучек и серий (серии и дорожки фильтруются строго по сезонам); контрастный таймлайн серий в карточке фильма; однократное нажатие Назад для выхода из плеера; хронологический порядок в календаре «Скоро выйдут»; отображение текущего времени при перемотке (напр. 48 м. 34 с.), отображение минут и секунд при быстрой перемотке свыше 60с; таймер меню не сбрасывается во время перемотки; яркая подсветка таймлайна сверху."
    }

CRASHES_FILE = os.path.join(CURRENT_DIR, "data", "crashes.json")

_actor_photo_cache: Dict[str, Optional[str]] = {}

def resolve_actor_photo(actor_name: str) -> Optional[str]:
    """Resolves an actor or director portrait photo URL via multi-lingual Wikidata & Wikipedia with strict occupation validation."""
    if not actor_name or len(actor_name.strip()) < 2:
        return None
    name_clean = actor_name.strip()
    if name_clean in _actor_photo_cache:
        return _actor_photo_cache[name_clean]

    headers = {"User-Agent": "ShowHubMediaCenter/2.7.6 (mailto:support@showhub.tv)"}

    # 1. Query Wikidata (authoritative entity matching across languages)
    for lang in ["ru", "en"]:
        try:
            w_url = f"https://www.wikidata.org/w/api.php?action=wbsearchentities&search={urllib.parse.quote(name_clean)}&language={lang}&format=json"
            resp = requests.get(w_url, headers=headers, timeout=3)
            if resp.status_code == 200:
                s_list = resp.json().get("search", [])
                for s_it in s_list[:3]:
                    desc = str(s_it.get("description") or "").lower()
                    is_actor = any(w in desc for w in ["actor", "actress", "актёр", "актрис", "director", "режиссёр", "voice", "seiyuu", "сэйю", "filmmaker", "comedian", "комик", "entertainer", "theatre", "театр"])
                    is_bad = any(w in desc for w in ["basketball", "football", "politician", "badminton", "swimmer", "physicist", "family", "dynasty", "noble", "municipality", "commune", "river", "mountain"])
                    if is_actor and not is_bad:
                        ent_id = s_it.get("id")
                        if ent_id:
                            e_url = f"https://www.wikidata.org/w/api.php?action=wbgetentities&ids={ent_id}&props=claims&format=json"
                            e_resp = requests.get(e_url, headers=headers, timeout=3)
                            if e_resp.status_code == 200:
                                entity = e_resp.json().get("entities", {}).get(ent_id, {})
                                p18_claims = entity.get("claims", {}).get("P18", [])
                                if p18_claims:
                                    img_val = p18_claims[0].get("mainsnak", {}).get("datavalue", {}).get("value")
                                    if img_val:
                                        pic_url = f"https://commons.wikimedia.org/wiki/Special:FilePath/{urllib.parse.quote(img_val)}?width=320"
                                        _actor_photo_cache[name_clean] = pic_url
                                        return pic_url
        except Exception:
            pass

    # 2. Fallback to Wikipedia (RU, EN, IT) with strict occupation & name validation
    norm_name = re.sub(r'[^a-zA-Zа-яА-Я0-9]', '', name_clean.lower())
    for lang in ["ru", "en", "it"]:
        try:
            url = f"https://{lang}.wikipedia.org/w/api.php?action=query&list=search&srsearch={urllib.parse.quote(name_clean)}&format=json"
            resp = requests.get(url, headers=headers, timeout=3)
            if resp.status_code == 200:
                sr = resp.json().get("query", {}).get("search", [])
                for item in sr[:3]:
                    title = item.get("title", "")
                    t_norm = re.sub(r'[^a-zA-Zа-яА-Я0-9]', '', title.lower())
                    snippet = item.get("snippet", "").lower()

                    is_match = (norm_name in t_norm) or (t_norm in norm_name)
                    if not is_match:
                        parts = name_clean.lower().split()
                        if len(parts) >= 2 and parts[0] in title.lower() and parts[-1] in title.lower():
                            is_match = True
                    if not is_match:
                        continue

                    is_actor = any(w in snippet for w in ["актёр", "актрис", "actor", "actress", "director", "режисс", "film", "кино", "cinema", "theatre", "театр", "drama", "voice", "сериал", "singer"])
                    is_bad = any(w in snippet for w in ["basketball", "football", "politician", "badminton", "nobility", "river"])
                    if is_actor and not is_bad:
                        u2 = f"https://{lang}.wikipedia.org/w/api.php?action=query&titles={urllib.parse.quote(title)}&prop=pageimages&format=json&pithumbsize=320"
                        r2 = requests.get(u2, headers=headers, timeout=3)
                        if r2.status_code == 200:
                            pages = r2.json().get("query", {}).get("pages", {})
                            for p in pages.values():
                                src = p.get("thumbnail", {}).get("source")
                                if src and not any(bad in src.lower() for bad in ["flag", "stub", "placeholder", "disambig"]):
                                    _actor_photo_cache[name_clean] = src
                                    return src
        except Exception:
            pass

    _actor_photo_cache[name_clean] = None
    return None

@app.post("/api/analytics/crash")
def report_crash(crash_data: Dict[str, Any]):
    try:
        os.makedirs(os.path.join(CURRENT_DIR, "data"), exist_ok=True)
        crashes = []
        if os.path.exists(CRASHES_FILE):
            with open(CRASHES_FILE, "r", encoding="utf-8") as f:
                crashes = json.load(f)
        crash_data["server_timestamp"] = int(time.time())
        crashes.append(crash_data)
        crashes = crashes[-100:]
        with open(CRASHES_FILE, "w", encoding="utf-8") as f:
            json.dump(crashes, f, ensure_ascii=False, indent=2)
        return {"success": True, "count": len(crashes)}
    except Exception as e:
        logger.error(f"Failed to record crash: {e}")
        return {"success": False, "error": str(e)}

@app.get("/api/analytics/crashes")
def get_crashes(limit: int = 50):
    try:
        if os.path.exists(CRASHES_FILE):
            with open(CRASHES_FILE, "r", encoding="utf-8") as f:
                crashes = json.load(f)
            return crashes[-limit:]
    except Exception:
        pass
    return []

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
    page: int = 1,
    excluded_countries: Optional[str] = None
) -> List[Dict[str, Any]]:
    """
    Returns dynamic fresh releases (новинки) and catalog items aggregated across live sources.
    Supports filtering by genre, country, content type (movies/series/cartoons/anime), release year, minimum rating, excluded countries, and sorting.
    """
    cache_key = f"{category}_{genre}_{year}_{country}_{content_type}_{min_rating}_{sort_by}_{page}_{excluded_countries}"
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

        # 4. Fetch from Kodik catalog and merge (crucial for country-specific cinema, anime, and doramas)
        try:
            k_items = kodik.get_catalog(category=eff_category, genre=genre, country=country, page=p, limit=50)
            for it in k_items:
                t_key = it.title.lower().strip()
                if t_key not in seen_titles and it.id not in seen_ids:
                    seen_titles.add(t_key)
                    seen_ids.add(it.id)
                    all_items.append(it.model_dump())
                elif t_key in seen_titles:
                    existing = next((x for x in all_items if x.get("title", "").lower().strip() == t_key), None)
                    if existing:
                        if (not existing.get("rating_kp") or existing.get("rating_kp") == 0) and it.rating_kp:
                            existing["rating_kp"] = it.rating_kp
                        if (not existing.get("rating_imdb") or existing.get("rating_imdb") == 0) and it.rating_imdb:
                            existing["rating_imdb"] = it.rating_imdb
                        if not existing.get("country") and it.extra_data.get("country"):
                            existing["country"] = it.extra_data.get("country")
                        if not existing.get("episodes_info") and it.episodes_info:
                            existing["episodes_info"] = it.episodes_info
        except Exception:
            pass

    # 3. Apply Strict Genre Filtering
    if genre and genre != "all":
        g_clean = genre.lower().strip()
        stem = g_clean
        if g_clean.endswith(("ия", "ии", "ые", "ий", "ка", "ки")):
            stem = g_clean[:-2]
        elif g_clean.endswith(("а", "ы", "и", "я")):
            stem = g_clean[:-1]

        def match_genre(it):
            meta_genre = str(it.get("extra_data", {}).get("genre") or "").lower()
            desc = str(it.get("description") or "").lower()
            genres_arr = [str(x).lower() for x in (it.get("genres") or [])]
            return (stem in meta_genre) or (stem in desc) or (g_clean in meta_genre) or (g_clean in desc) or any(stem in x for x in genres_arr)

        all_items = [it for it in all_items if match_genre(it)]

    # 4. Apply Country Filtering (All 18 countries)
    if country and country != "all":
        c_clean = country.lower().strip()
        aliases = [c_clean]
        if "коре" in c_clean:
            aliases.extend(["корея", "южная корея", "korea", "корей"])
        elif "сша" in c_clean:
            aliases.extend(["сша", "usa", "америк"])
        elif "росси" in c_clean:
            aliases.extend(["россия", "ссср", "russia", "россий"])
        elif "великобрит" in c_clean or "англи" in c_clean:
            aliases.extend(["великобритания", "англия", "uk", "британ"])
        elif "япон" in c_clean:
            aliases.extend(["япония", "japan", "япон"])
        elif "турц" in c_clean:
            aliases.extend(["турция", "turkey", "турец"])
        elif "кита" in c_clean:
            aliases.extend(["китай", "china", "китай"])
        elif "инди" in c_clean:
            aliases.extend(["индия", "india", "индий"])
        elif "франц" in c_clean:
            aliases.extend(["франция", "france", "француз"])
        elif "герман" in c_clean:
            aliases.extend(["германия", "germany", "немец"])
        elif "италь" in c_clean or "итали" in c_clean:
            aliases.extend(["италия", "italy", "итальян"])
        elif "испан" in c_clean:
            aliases.extend(["испания", "spain", "испан"])
        elif "канад" in c_clean:
            aliases.extend(["канада", "canada", "канад"])
        elif "австрал" in c_clean:
            aliases.extend(["австралия", "australia"])
        elif "таиланд" in c_clean or "тайланд" in c_clean:
            aliases.extend(["таиланд", "тайланд", "thailand"])
        elif "швеци" in c_clean:
            aliases.extend(["швеция", "sweden"])

        def match_country(it):
            meta_c = str(it.get("extra_data", {}).get("country") or "").lower()
            desc = str(it.get("description") or "").lower()
            direct_c = str(it.get("country") or "").lower()
            return any(a in meta_c or a in desc or a in direct_c for a in aliases)

        all_items = [it for it in all_items if match_country(it)]

    # 4b. Apply Excluded Countries Filter
    if excluded_countries:
        ex_tokens = [c.strip().lower() for c in excluded_countries.split(",") if c.strip()]
        if ex_tokens:
            def is_not_excluded(it):
                meta_c = str(it.get("extra_data", {}).get("country") or "").lower()
                desc = str(it.get("description") or "").lower()
                direct_c = str(it.get("country") or "").lower()
                countries_list = " ".join([str(x).lower() for x in (it.get("extra_data", {}).get("countries") or [])])
                text = f"{meta_c} {desc} {direct_c} {countries_list}"
                for ex in ex_tokens:
                    if ex in text:
                        return False
                return True
            all_items = [it for it in all_items if is_not_excluded(it)]

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

    # 8. Apply Sorting (Smart Freshness Ranking prioritizing ratings & popularity)
    now = datetime.datetime.now()
    now_ts = int(now.timestamp())
    current_year = now.year

    def compute_freshness(it):
        raw_y = it.get("year")
        try:
            y = int(raw_y) if raw_y else (current_year - 6)
        except Exception:
            y = current_year - 6

        # Year recency score (linear, up to 500,000 for current year, not 100 trillion!)
        year_diff = max(0, current_year - y)
        year_score = max(0, (10 - min(year_diff, 10)) * 50_000)

        kp = float(it.get("rating_kp") or 0.0)
        imdb = float(it.get("rating_imdb") or 0.0)
        eff_rating = max(kp, imdb)

        # Rating score: high ratings boost significantly; unrated items are penalized
        if eff_rating > 0:
            rating_score = int(eff_rating * 80_000)
        else:
            rating_score = -400_000

        vkp = int(it.get("vote_num_kp") or 0)
        vimdb = int(it.get("vote_num_imdb") or 0)
        votes = max(vkp, vimdb)
        # Logarithmic votes boost
        import math
        vote_score = int(math.log10(max(votes, 1)) * 40_000) if votes > 0 else 0

        poster_str = str(it.get("poster") or "")
        has_real_poster = bool(poster_str and "no_image_poster" not in poster_str and "noposter" not in poster_str)
        poster_bonus = 100_000 if has_real_poster else -200_000

        series_bonus = 0
        if it.get("is_series") and y >= current_year - 1:
            ep_info = str(it.get("episodes_info") or "")
            if ep_info:
                ep_m = re.search(r'(\d+)\s*сер', ep_info, re.I)
                if ep_m:
                    series_bonus += min(int(ep_m.group(1)), 30) * 10_000

        return year_score + rating_score + vote_score + poster_bonus + series_bonus

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

    # Never dump fallback initial_catalog when a custom filter (country, genre, year) is active!
    has_custom_filter = bool((country and country != "all") or (genre and genre != "all") or (year and year != "all"))
    if not all_items and not has_custom_filter:
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

        # Promote country, countries, genres, actors, director to top-level fields
        extra = it.get("extra_data") or {}
        if not it.get("country"):
            it["country"] = extra.get("country") or (extra.get("countries", [None])[0] if isinstance(extra.get("countries"), list) and extra.get("countries") else "")
        if not it.get("country") and it.get("description"):
            desc_val = str(it.get("description"))
            if "," in desc_val:
                sp_c = [p.strip() for p in desc_val.split(",") if p.strip()]
                if len(sp_c) >= 2 and not any(ch.isdigit() for ch in sp_c[1]):
                    it["country"] = sp_c[1]
                    if not it.get("countries"):
                        it["countries"] = [sp_c[1]]

        # Comprehensive fallback country inference from text and genres
        if not it.get("country"):
            full_text = f"{it.get('description', '')} {it.get('title', '')} {' '.join(it.get('genres') or [])}".lower()
            if any(k in full_text for k in ["япони", "японс", "аниме", "anime"]):
                it["country"] = "Япония"
            elif any(k in full_text for k in ["коре", "дорам", "dorama"]):
                it["country"] = "Корея Южная"
            elif any(k in full_text for k in ["китай", "китайс", "донгхуа", "donghua"]):
                it["country"] = "Китай"
            elif any(k in full_text for k in ["сша", "америк", "usa"]):
                it["country"] = "США"
            elif any(k in full_text for k in ["росси", "российс", "ссср"]):
                it["country"] = "Россия"
            elif any(k in full_text for k in ["великобритан", "британ", "англи", "uk"]):
                it["country"] = "Великобритания"
            elif any(k in full_text for k in ["франци", "француз"]):
                it["country"] = "Франция"
            elif any(k in full_text for k in ["итали", "итальян"]):
                it["country"] = "Италия"
            elif any(k in full_text for k in ["испани", "испанс"]):
                it["country"] = "Испания"
            elif any(k in full_text for k in ["германи", "немец"]):
                it["country"] = "Германия"
            elif any(k in full_text for k in ["инди", "индийс"]):
                it["country"] = "Индия"
            elif any(k in full_text for k in ["турци", "турец"]):
                it["country"] = "Турция"
            elif any(k in full_text for k in ["таиланд", "тайланд", "тайс"]):
                it["country"] = "Таиланд"
            elif any(k in full_text for k in ["швеци", "швед"]):
                it["country"] = "Швеция"
            elif any(k in full_text for k in ["мексик"]):
                it["country"] = "Мексика"
            elif any(k in full_text for k in ["канад"]):
                it["country"] = "Канада"
            elif any(k in full_text for k in ["австрали"]):
                it["country"] = "Австралия"

        if it.get("country") and not it.get("countries"):
            it["countries"] = [it["country"]]

        # Promote episodes_info
        if not it.get("episodes_info"):
            it["episodes_info"] = extra.get("episodes_info") or ""
        if not it.get("countries") and extra.get("countries"):
            it["countries"] = extra.get("countries")
        if not it.get("genres") and extra.get("genres"):
            it["genres"] = extra.get("genres")
        if not it.get("actors") and extra.get("actors"):
            it["actors"] = extra.get("actors")
        if not it.get("director") and extra.get("director"):
            it["director"] = extra.get("director")

        # Accurately classify age rating for badge display
        it["age_limit"] = classify_age_rating(
            title=it.get("title", ""),
            desc=it.get("description", ""),
            genres=it.get("genres") or extra.get("genres") or extra.get("genre"),
            extra=extra,
            raw_limit=it.get("age_limit")
        )

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
    kp_id: Optional[str] = None,
    original_title: Optional[str] = None
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

    clean_title = re.sub(r'\(.*?\)|\[.*?\]', '', title).strip() if title else ""
    if clean_title and ":" in clean_title:
        clean_title = clean_title.split(":")[0].strip()
    if clean_title and " - " in clean_title:
        clean_title = clean_title.split(" - ")[0].strip()
    clean_title = re.sub(r'\b\d+\s+(сери[йия]|сезон(а|ов)?)\b', '', clean_title, flags=re.I).strip()
    clean_title = re.sub(r'\b(сезон|серия)\s+\d+\b', '', clean_title, flags=re.I).strip()

    # Determine real Kinopoisk ID if available
    resolved_kp = kp_id
    if not resolved_kp and source in ["bazon", "videocdn", "delivembd"] and media_id.isdigit():
        resolved_kp = media_id
    if not resolved_kp and clean_title:
        try:
            b_items = bazon.search(clean_title)
            b_match = find_best_match(b_items, year_int, is_ser_bool, target_title=clean_title)
            if b_match and b_match.kinopoisk_id:
                resolved_kp = b_match.kinopoisk_id
        except Exception:
            pass

    # 0a. If source is kodik, pre-extract authentic metadata from Kodik immediately
    if (source == "kodik" or not clean_title) and clean_title:
        try:
            kd_pre = kodik.search(clean_title, year=year_int, kp_id=resolved_kp)
            if kd_pre:
                first_k = kd_pre[0]
                if first_k.extra_data.get("country"):
                    details["country"] = first_k.extra_data["country"]
                if first_k.extra_data.get("countries"):
                    details["countries"] = first_k.extra_data["countries"]
                if first_k.extra_data.get("actors"):
                    details["actors"] = first_k.extra_data["actors"]
                if first_k.extra_data.get("director"):
                    details["director"] = first_k.extra_data["director"]
                if first_k.extra_data.get("genres"):
                    details["genres"] = first_k.extra_data["genres"]
                if first_k.description:
                    details["description"] = first_k.description
                if first_k.rating_kp:
                    details["rating_kp"] = first_k.rating_kp
                if first_k.rating_imdb:
                    details["rating_imdb"] = first_k.rating_imdb
        except Exception:
            pass

    # 0. Query TMDb as the primary authoritative metadata provider
    try:
        tmdb_info = tmdb.search_and_enrich(title=title, year=year_int, is_series=is_ser_bool, original_title=original_title)
        if tmdb_info:
            if tmdb_info.get("actors"):
                details["actors"] = tmdb_info["actors"]
            if tmdb_info.get("cast"):
                details["cast"] = tmdb_info["cast"]
            if tmdb_info.get("director"):
                details["director"] = tmdb_info["director"]
            if tmdb_info.get("directors_list"):
                details["directors_list"] = tmdb_info["directors_list"]
            if tmdb_info.get("country"):
                details["country"] = tmdb_info["country"]
            if tmdb_info.get("countries"):
                details["countries"] = tmdb_info["countries"]
            if not details.get("description") and tmdb_info.get("description"):
                details["description"] = tmdb_info["description"]
            if not details.get("poster") and tmdb_info.get("poster"):
                details["poster"] = tmdb_info["poster"]
            if not details.get("rating_imdb") and tmdb_info.get("rating"):
                details["rating_imdb"] = tmdb_info["rating"]
    except Exception:
        pass

    # 1. Fetch Bazon details (ratings, synopsis, cast, genres)
    if resolved_kp:
        try:
            b_info = bazon.get_details(resolved_kp)
            if b_info:
                for k, v in b_info.items():
                    if v is not None:
                        # NEVER overwrite authentic TMDb cast, director, or country with Bazon
                        if k in ["actors", "cast", "director", "directors_list", "country", "countries"] and details.get(k):
                            continue
                        details[k] = v
                if b_info.get("poster") and (not details.get("poster") or not str(details["poster"]).startswith("http")):
                    details["poster"] = b_info["poster"]
                if not details.get("actors") and b_info.get("orig"):
                    # Enrich from TMDb using Bazon's original title
                    tmdb_retry = tmdb.search_and_enrich(title=clean_title, year=year_int, is_series=is_ser_bool, original_title=b_info["orig"])
                    if tmdb_retry and tmdb_retry.get("actors"):
                        details["actors"] = tmdb_retry["actors"]
                        details["cast"] = tmdb_retry.get("cast", [])
                        details["director"] = tmdb_retry.get("director")
                        details["directors_list"] = tmdb_retry.get("directors_list", [])
                        details["country"] = tmdb_retry.get("country")
        except Exception:
            pass

    # 2. Fetch HDRezka details (translators, seasons & episodes, high-res poster)
    try:
        rz_id = media_id if (source == "hdrezka" and media_id.startswith("http")) else None
        if not rz_id and clean_title:
            rz_items = hdrezka.search(clean_title)
            rz_match = find_best_match(rz_items, year_int, is_ser_bool, target_title=clean_title)
            if rz_match:
                rz_id = rz_match.id
        if rz_id:
            rz_det = hdrezka.get_media_details(rz_id)
            if rz_det:
                if rz_det.get("poster") and (not details.get("poster") or not str(details["poster"]).startswith("http")):
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
                rz_country = rz_det.get("country") or ""
                cur_country = str(details.get("country") or "")
                cur_actors = str(details.get("actors") or "")
                asian_markers = ["китай", "япони", "коре", "тайван", "гонконг", "тайланд"]
                is_cur_asian = any(a in cur_country.lower() for a in asian_markers) or any(s in cur_actors.lower() for s in ["чэнь", "тун яо", "линь", "юань", "пань", "ван ян", "сюй", "дун", "чжан", "ким", "пак", "минхо", "хайси"])
                is_rz_asian = any(a in rz_country.lower() for a in asian_markers) if rz_country else False

                if is_cur_asian and not is_rz_asian:
                    # Keep authentic Asian metadata, do not overwrite with non-Asian HDRezka metadata (e.g. Ukraine, USA)
                    pass
                else:
                    if not details["director"]:
                        if rz_det.get("director"):
                            details["director"] = rz_det["director"]
                    if not details["actors"]:
                        if rz_det.get("actors"):
                            details["actors"] = rz_det["actors"]
                    if not details["genres"] and rz_det.get("genres"):
                        details["genres"] = rz_det["genres"]
                    if not details["country"] and rz_country:
                        details["country"] = rz_country
                if rz_det.get("episodes_schedule"):
                    details["episodes_schedule"] = rz_det["episodes_schedule"]

                # If TMDb was not resolved, retry using HDRezka's original title
                if (not details.get("actors") or not details.get("cast")) and rz_det.get("original_title") and not is_cur_asian:
                    tmdb_retry = tmdb.search_and_enrich(title=clean_title, year=year_int, is_series=is_ser_bool, original_title=rz_det["original_title"])
                    if tmdb_retry and tmdb_retry.get("actors"):
                        details["actors"] = tmdb_retry["actors"]
                        details["cast"] = tmdb_retry.get("cast", [])
                        details["director"] = tmdb_retry.get("director")
                        details["directors_list"] = tmdb_retry.get("directors_list", [])
                        details["country"] = tmdb_retry.get("country")
    except Exception:
        pass

    # 3. Multi-Source Translator & Season Aggregation across HDRezka, Filmix, and Kodik
    def _norm_t_name(n: str) -> str:
        s = n.lower().strip()
        for p in ["то ", "студия ", "озвучка ", "дубляж ", "профессиональный ", "многоголосый ", "авторский ", "русский ", "закадровый "]:
            s = s.replace(p, "")
        return re.sub(r'[^a-zA-Zа-яА-Я0-9]', '', s)

    rz_max_eps = max((len(s.get("episodes", [])) for s in details.get("seasons", [])), default=0)
    # Tag existing HDRezka translators
    for t in details.get("translators", []):
        if not t.get("source"):
            t["source"] = "hdrezka"
        if not t.get("episodes_count") or t["episodes_count"] == 0:
            t["episodes_count"] = rz_max_eps

    trans_map: Dict[str, Dict[str, Any]] = {}
    for t in details.get("translators", []):
        k = _norm_t_name(t.get("name", ""))
        trans_map[k] = t

    # Merge Filmix audio tracks
    try:
        fx_id = media_id if (source == "filmix" and media_id.isdigit()) else None
        if not fx_id and clean_title:
            fx_items = filmix.search(clean_title)
            fx_match = find_best_match(fx_items, year_int, is_ser_bool, target_title=clean_title)
            if fx_match:
                fx_id = fx_match.id
        if fx_id:
            fx_res = filmix.get_streams(fx_id)
            if fx_res.audio_tracks:
                for t in fx_res.audio_tracks:
                    k = _norm_t_name(t.name)
                    if k not in trans_map:
                        t_dict = t.model_dump()
                        t_dict["source"] = "filmix"
                        trans_map[k] = t_dict
                        details["translators"].append(t_dict)
    except Exception:
        pass

    # Enrich HDRezka translators with exact per-season episode counts
    if details.get("is_series") and details.get("translators") and rz_id:
        def _enrich_rz_tr(tr_item):
            t_id = tr_item.get("id")
            if t_id and str(t_id).isdigit() and not str(t_id).startswith("kodik_"):
                try:
                    eps = hdrezka.get_episodes(rz_id, str(t_id))
                    if eps:
                        s_eps = {}
                        for s in eps:
                            s_num = s.get("season_id") or s.get("season_number") or 1
                            s_eps[int(s_num)] = len(s.get("episodes", []))
                        tr_item["seasons_episodes"] = s_eps
                        tr_item["episodes_count"] = max(s_eps.values(), default=0)
                except Exception:
                    pass
        try:
            with ThreadPoolExecutor(max_workers=6) as ex:
                list(ex.map(_enrich_rz_tr, details["translators"]))
        except Exception:
            pass

    def _is_matching_title(item_title: str, query: str) -> bool:
        def norm(s: str) -> str:
            return re.sub(r'[^a-zA-Zа-яА-Я0-9]', '', (s or "").lower())
        qt = norm(query)
        it = norm(item_title)
        if not qt or not it:
            return False
        if qt == it:
            return True
        if qt in it:
            return True
        if it in qt and len(it) >= 0.75 * len(qt):
            return True
        return False

    # Merge Kodik translations & seasons
    kd_max_eps = 0
    kd_seasons_eps: Dict[int, int] = {}
    try:
        if resolved_kp or clean_title:
            k_raw_items = kodik.search(clean_title, year=year_int, kp_id=resolved_kp)
            # Filter strictly matching items only
            k_items = [
                it for it in k_raw_items
                if _is_matching_title(it.title, clean_title) or (resolved_kp and str(getattr(it, "kinopoisk_id", "") or "") == str(resolved_kp))
            ]
            for k_it in k_items:
                trans_name = k_it.extra_data.get("translation")
                if not trans_name:
                    continue
                k_key = _norm_t_name(trans_name)
                k_seasons = k_it.extra_data.get("seasons", {})
                k_this_seasons_eps: Dict[int, int] = {}
                k_eps_count = 0
                if isinstance(k_seasons, dict):
                    for s_k, s_v in k_seasons.items():
                        if isinstance(s_v, dict) and "episodes" in s_v:
                            s_num = int(s_k) if str(s_k).isdigit() else 1
                            ep_count_s = len(s_v["episodes"])
                            k_this_seasons_eps[s_num] = ep_count_s
                            kd_seasons_eps[s_num] = max(kd_seasons_eps.get(s_num, 0), ep_count_s)
                            k_eps_count = max(k_eps_count, ep_count_s)
                kd_max_eps = max(kd_max_eps, k_eps_count)

                k_trans_obj = {
                    "id": f"kodik_{k_it.id}",
                    "name": f"{trans_name} (Kodik)" if k_key in trans_map else trans_name,
                    "is_default": False,
                    "kodik_id": k_it.id,
                    "source": "kodik",
                    "episodes_count": k_eps_count,
                    "seasons_episodes": k_this_seasons_eps
                }

                if k_key in trans_map:
                    existing = trans_map[k_key]
                    existing["kodik_id"] = k_it.id
                    if k_eps_count > 0 and k_eps_count != existing.get("episodes_count", 0):
                        details["translators"].append(k_trans_obj)
                else:
                    trans_map[k_key] = k_trans_obj
                    details["translators"].append(k_trans_obj)

                # If Kodik has more episodes than currently in details["seasons"], expand details["seasons"]
                if k_eps_count > 0 and isinstance(k_seasons, dict):
                    details["is_series"] = True
                    existing_seasons = {s.get("season_number", s.get("season_id")): s for s in details.get("seasons", [])}
                    for s_k, s_v in k_seasons.items():
                        s_num = int(s_k) if str(s_k).isdigit() else 1
                        ep_keys = sorted([int(x) for x in s_v.get("episodes", {}).keys() if str(x).isdigit()])
                        if s_num not in existing_seasons:
                            new_s = {
                                "season_id": s_num,
                                "season_number": s_num,
                                "title": f"Сезон {s_num}",
                                "episodes": [{"episode_id": ep_n, "episode_number": ep_n, "title": f"Серия {ep_n}"} for ep_n in ep_keys]
                            }
                            details["seasons"].append(new_s)
                            existing_seasons[s_num] = new_s
                        else:
                            curr_s = existing_seasons[s_num]
                            curr_s["season_id"] = s_num
                            curr_s["season_number"] = s_num
                            curr_ep_nums = {e.get("episode_number", e.get("episode_id")) for e in curr_s.get("episodes", [])}
                            for ep_n in ep_keys:
                                if ep_n not in curr_ep_nums:
                                    curr_s.get("episodes", []).append({"episode_id": ep_n, "episode_number": ep_n, "title": f"Серия {ep_n}"})
                                    curr_ep_nums.add(ep_n)
                            curr_s["episodes"].sort(key=lambda x: x.get("episode_number", x.get("episode_id", 0)))
    except Exception:
        pass

    # Ensure all existing season dictionaries have both season_number and season_id, and episode_number & episode_id
    for s in details.get("seasons", []):
        s_num = s.get("season_number") or s.get("season_id") or 1
        s["season_number"] = s_num
        s["season_id"] = s_num
        for ep in s.get("episodes", []):
            e_num = ep.get("episode_number") or ep.get("episode_id") or 1
            ep["episode_number"] = e_num
            ep["episode_id"] = e_num

    # Normalize and ensure all translators have accurate series episode count and seasons mapping
    rz_seasons_eps = {s.get("season_number", s.get("season_id", 1)): len(s.get("episodes", [])) for s in details.get("seasons", [])}
    total_series_eps = max(rz_seasons_eps.values(), default=0)
    if total_series_eps > 0:
        details["is_series"] = True
    for t in details.get("translators", []):
        t_se = t.get("seasons_episodes")
        if not t_se and t.get("source") == "hdrezka" and not t.get("kodik_id"):
            if len(details.get("seasons", [])) <= 1:
                t["seasons_episodes"] = rz_seasons_eps
        
        ep_cnt = t.get("episodes_count")
        if t.get("seasons_episodes"):
            se_max = max([int(v) for v in t["seasons_episodes"].values() if str(v).isdigit()], default=0)
            if se_max > 0:
                t["episodes_count"] = se_max
        elif ep_cnt is None or ep_cnt <= 0:
            if len(details.get("seasons", [])) <= 1:
                t["episodes_count"] = total_series_eps
            else:
                t["episodes_count"] = 0

    # Source availability metadata for UI Source selector
    sources_info = []
    if kd_max_eps > 0 or any(t.get("source") == "kodik" for t in details.get("translators", [])):
        sources_info.append({
            "source": "kodik",
            "name": "Kodik",
            "episodes_count": kd_max_eps if kd_max_eps > 0 else total_series_eps,
            "seasons_episodes": kd_seasons_eps
        })
    if rz_max_eps > 0 or details.get("seasons"):
        sources_info.append({
            "source": "hdrezka",
            "name": "HDRezka",
            "episodes_count": rz_max_eps if rz_max_eps > 0 else total_series_eps,
            "seasons_episodes": rz_seasons_eps
        })
    details["sources_info"] = sources_info

    # 4b. Enrich missing ratings from Kodik and Shikimori (especially for anime and fresh titles)
    if (not details.get("rating_kp") or details.get("rating_kp") == 0.0) or (not details.get("rating_imdb") or details.get("rating_imdb") == 0.0):
        try:
            k_items_r = kodik.search(clean_title, year=year_int, kp_id=resolved_kp)
            if k_items_r:
                for kr in k_items_r:
                    if kr.rating_kp and (not details.get("rating_kp") or details.get("rating_kp") == 0.0):
                        details["rating_kp"] = kr.rating_kp
                    if kr.rating_imdb and (not details.get("rating_imdb") or details.get("rating_imdb") == 0.0):
                        details["rating_imdb"] = kr.rating_imdb
                    if (not details.get("rating_kp") or details.get("rating_kp") == 0.0) and kr.extra_data.get("shikimori_rating"):
                        details["rating_kp"] = float(kr.extra_data["shikimori_rating"])
                    if details.get("rating_kp") and details["rating_kp"] > 0:
                        break
        except Exception:
            pass

        # If still missing rating for anime or animated series, check Shikimori API directly
        if (not details.get("rating_kp") or details.get("rating_kp") == 0.0) and (details.get("is_series") or any("аним" in str(g).lower() for g in details.get("genres", []))):
            try:
                shiki_url = f"https://shikimori.one/api/animes?search={urllib.parse.quote(clean_title)}"
                shiki_res = requests.get(shiki_url, headers={"User-Agent": "ShowHubTV-MediaCenter/2.7.5"}, timeout=4).json()
                if shiki_res and isinstance(shiki_res, list) and len(shiki_res) > 0:
                    score = shiki_res[0].get("score")
                    if score and float(score) > 0:
                        details["rating_kp"] = float(score)
            except Exception:
                pass

    # 4c. Fallback to Kodik actors/directors/genres/country ONLY for confirmed anime/doramas or if source is kodik!
    cur_genres = [str(g).lower() for g in details.get("genres", [])]
    is_anime_or_dorama = (source == "kodik") or any("аним" in g or "дорам" in g for g in cur_genres)
    if is_anime_or_dorama and (not details.get("actors") or not details.get("director")) and (resolved_kp or clean_title):
        try:
            k_items = kodik.search(clean_title, year=year_int, kp_id=resolved_kp)
            if k_items:
                matched_it = k_items[0]
                if not details.get("actors") and matched_it.extra_data.get("actors"):
                    details["actors"] = matched_it.extra_data["actors"]
                if not details.get("director") and matched_it.extra_data.get("director"):
                    details["director"] = matched_it.extra_data["director"]
                if not details.get("country") and matched_it.extra_data.get("country"):
                    details["country"] = matched_it.extra_data["country"]
                if not details.get("genres") and matched_it.extra_data.get("genres"):
                    details["genres"] = matched_it.extra_data["genres"]
                if not details.get("description") and matched_it.description:
                    details["description"] = matched_it.description
        except Exception:
            pass

    # 5. Populate Actors with Photos (preserve TMDb cast if available, else Wikipedia photos)
    actors_list = []
    if details.get("cast"):
        for idx, c_item in enumerate(details["cast"][:12]):
            actors_list.append({
                "id": f"act_{idx+1}",
                "name": c_item.get("name", ""),
                "role": c_item.get("character", "В главных ролях") or "В главных ролях",
                "photo": c_item.get("photo", "")
            })
    elif details.get("actors"):
        raw_actors = str(details.get("actors") or "")
        names = [n.strip() for n in re.split(r'[,;•\n/]', raw_actors) if n.strip()]
        for idx, a_name in enumerate(names[:10]):
            photo = resolve_actor_photo(a_name)
            actors_list.append({
                "id": f"act_{idx+1}",
                "name": a_name,
                "role": "В главных ролях",
                "photo": photo or ""
            })
    details["actors_list"] = actors_list

    # 5b. Populate Directors with Photos (preserve TMDb directors if available, else Wikipedia photos)
    directors_list = []
    if details.get("directors_list"):
        for idx, d_item in enumerate(details["directors_list"][:5]):
            directors_list.append({
                "id": f"dir_{idx+1}",
                "name": d_item.get("name", ""),
                "role": d_item.get("job", "Режиссёр") or "Режиссёр",
                "photo": d_item.get("photo", "")
            })
    elif details.get("director"):
        raw_director = str(details.get("director") or "")
        d_names = [n.strip() for n in re.split(r'[,;•\n/]', raw_director) if n.strip()]
        for idx, d_name in enumerate(d_names[:5]):
            photo = resolve_actor_photo(d_name)
            directors_list.append({
                "id": f"dir_{idx+1}",
                "name": d_name,
                "role": "Режиссёр",
                "photo": photo or ""
            })
    details["directors_list"] = directors_list

    # 6. Determine Age Rating
    details["age_limit"] = classify_age_rating(
        title=details.get("title", ""),
        desc=details.get("description", ""),
        genres=details.get("genres"),
        extra=details.get("extra_data"),
        raw_limit=details.get("age_limit")
    )

    # 7. Final sanity check: detect country mismatch where country says Ukraine/USA/Russia/India but actors or Kodik confirm Asian
    act_str = str(details.get("actors") or "").lower()
    c_str = str(details.get("country") or "").lower()
    asian_surnames = ["чэнь", "тун яо", "линь", "юань", "пань", "ван ян", "сюй", "дун ", "чжан", "ким ", "пак ", "сон ", "ли мин", "минхо", "бай лу", "чжао лусы"]
    if any(s in act_str for s in asian_surnames) and not any(a in c_str for a in ["китай", "коре", "япони", "тайван", "гонконг", "ази"]):
        if clean_title:
            try:
                k_re = kodik.search(clean_title, year=year_int, kp_id=resolved_kp)
                for kit in k_re:
                    k_c = kit.extra_data.get("country")
                    if k_c and any(a in k_c.lower() for a in ["китай", "коре", "япони", "тайван"]):
                        details["country"] = k_c
                        break
            except Exception:
                pass
        if not any(a in str(details.get("country") or "").lower() for a in ["китай", "коре", "япони"]):
            details["country"] = "Китай"

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
    if episode and str(episode).isdigit() and int(episode) > 1:
        is_ser_bool = True

    resolved: Dict[str, Any] = {}
    resolved_kp = kp_id
    if not resolved_kp and media_id and str(media_id).isdigit():
        resolved_kp = str(media_id)

    clean_title = re.sub(r'\(.*?\)|\[.*?\]', '', title).strip() if title else ""
    if clean_title and ":" in clean_title:
        clean_title = clean_title.split(":")[0].strip()
    if clean_title and " - " in clean_title:
        clean_title = clean_title.split(" - ")[0].strip()
    clean_title = re.sub(r'\b\d+\s+(сери[йия]|сезон(а|ов)?)\b', '', clean_title, flags=re.I).strip()
    clean_title = re.sub(r'\b(сезон|серия)\s+\d+\b', '', clean_title, flags=re.I).strip()

    titles_to_try = []
    if clean_title:
        titles_to_try.append(clean_title)
        no_year = re.sub(r'\b(19\d\d|20\d\d)\b', '', clean_title).strip()
        if no_year and no_year not in titles_to_try:
            titles_to_try.append(no_year)
    if title and title not in titles_to_try:
        titles_to_try.append(title)

    if not resolved_kp and titles_to_try:
        for t_query in titles_to_try:
            try:
                b_items = bazon.search(t_query)
                b_match = find_best_match(b_items, year_int, is_ser_bool, target_title=clean_title)
                if b_match and b_match.kinopoisk_id:
                    resolved_kp = b_match.kinopoisk_id
                    break
            except Exception:
                pass

    def _resolve_filmix():
        try:
            candidate_fx_ids = []
            if source == "filmix" and media_id.isdigit():
                candidate_fx_ids.append(media_id)
            if titles_to_try:
                for t_query in titles_to_try:
                    fx_items = filmix.search(t_query)
                    for it in rank_matches(fx_items, year_int, is_ser_bool, target_title=clean_title):
                        if it.id not in candidate_fx_ids:
                            candidate_fx_ids.append(it.id)
            for fx_id in candidate_fx_ids[:3]:
                fx_streams = filmix.get_streams(fx_id, season=season, episode=episode, audio_id=audio_id)
                if fx_streams.streams:
                    return ("filmix", fx_streams.model_dump())
                if audio_id:
                    fx_streams_fallback = filmix.get_streams(fx_id, season=season, episode=episode, audio_id=None)
                    if fx_streams_fallback.streams:
                        return ("filmix", fx_streams_fallback.model_dump())
        except Exception:
            pass
        return None

    def _resolve_hdrezka():
        try:
            candidate_rz_ids = []
            if media_id and (media_id.startswith("http") or "hdrezka" in media_id):
                candidate_rz_ids.append(media_id)
            elif source == "hdrezka" and media_id.startswith("http"):
                candidate_rz_ids.append(media_id)
            if titles_to_try:
                for t_query in titles_to_try:
                    rz_items = hdrezka.search(t_query)
                    for it in rank_matches(rz_items, year_int, is_ser_bool, target_title=clean_title):
                        if it.id not in candidate_rz_ids:
                            candidate_rz_ids.append(it.id)
            for rz_id in candidate_rz_ids[:3]:
                rz_audio_id = audio_id if (audio_id and str(audio_id).isdigit()) else None
                rz_streams = hdrezka.get_streams(rz_id, season=season, episode=episode, audio_id=rz_audio_id)
                if rz_streams.streams:
                    return ("hdrezka", rz_streams.model_dump())
                # Fallback to default audio if specific audio returned no streams (e.g. translator didn't voice this episode)
                if rz_audio_id:
                    rz_streams_fallback = hdrezka.get_streams(rz_id, season=season, episode=episode, audio_id=None)
                    if rz_streams_fallback.streams:
                        return ("hdrezka", rz_streams_fallback.model_dump())
        except Exception:
            pass
        return None

    def _resolve_videocdn():
        try:
            vc_id = resolved_kp
            if not vc_id and titles_to_try:
                for t_query in titles_to_try:
                    vc_items = videocdn.search(t_query)
                    vc_match = find_best_match(vc_items, year_int, is_ser_bool, target_title=clean_title)
                    if vc_match and vc_match.kinopoisk_id:
                        vc_id = vc_match.kinopoisk_id
                        break
            if vc_id:
                vc_streams = videocdn.get_streams(vc_id)
                if vc_streams.streams or vc_streams.embed_url:
                    return ("videocdn", vc_streams.model_dump())
        except Exception:
            pass
        return None

    def _resolve_delivembd():
        if resolved_kp:
            try:
                d_streams = delivembd.get_streams(resolved_kp)
                if d_streams.streams or d_streams.embed_url:
                    return ("delivembd", d_streams.model_dump())
            except Exception:
                pass
        return None

    def _resolve_bazon():
        try:
            b_id = resolved_kp
            if not b_id and clean_title:
                b_items = bazon.search(clean_title)
                b_match = find_best_match(b_items, year_int, is_ser_bool, target_title=clean_title)
                if b_match and b_match.kinopoisk_id:
                    b_id = b_match.kinopoisk_id
            if b_id:
                b_streams = bazon.get_streams(b_id)
                if b_streams.streams or b_streams.embed_url:
                    return ("bazon", b_streams.model_dump())
        except Exception:
            pass
        return None

    def _resolve_kodik():
        try:
            if clean_title or media_id:
                k_items = kodik.search(clean_title, year=year_int, kp_id=resolved_kp) if clean_title else []
                target_k_id = None
                if audio_id and str(audio_id).startswith("kodik_"):
                    target_k_id = str(audio_id).replace("kodik_", "")
                elif source == "kodik" and media_id:
                    target_k_id = media_id.replace("kodik_", "")
                elif audio_id:
                    a_lower = str(audio_id).lower()
                    for it in k_items:
                        tr_name = str(it.extra_data.get("translation", "")).lower()
                        if tr_name and (a_lower in tr_name or tr_name in a_lower or it.id == audio_id):
                            target_k_id = it.id
                            break
                    if not target_k_id:
                        for it in k_items:
                            tr_name = str(it.extra_data.get("translation", "")).lower()
                            if "дубляж" in tr_name and ("дубляж" in a_lower or str(audio_id) == "618"):
                                target_k_id = it.id
                                break

                if not target_k_id:
                    ranked = rank_matches(k_items, year_int, is_ser_bool, target_title=clean_title)
                    target_k_id = ranked[0].id if ranked else (k_items[0].id if k_items else None)

                if target_k_id:
                    k_res = kodik.get_streams(target_k_id, season=season, episode=episode, audio_id=audio_id)
                    if k_res.streams or k_res.embed_url:
                        return ("kodik", k_res.model_dump())
        except Exception:
            pass
        return None

    def _resolve_torrents():
        if clean_title:
            try:
                torr_items = torrents.search(clean_title, year=year_int, season=season, episode=episode)
                if torr_items:
                    torr_streams = [
                        {
                            "quality": f"{t.extra_data.get('size', '')} (Сиды: {t.extra_data.get('seeds', '0')})",
                            "url": t.extra_data.get("stream_url", ""),
                            "stream_type": "torrent",
                            "headers": {},
                            "magnet": t.extra_data.get("magnet", "")
                        }
                        for t in torr_items[:8]
                    ]
                    return ("torrents", {
                        "source_name": "Rutor / TorrServe",
                        "media_id": media_id,
                        "title": title,
                        "streams": torr_streams,
                        "embed_url": None,
                        "error": None
                    })
            except Exception:
                pass
        return None

    with ThreadPoolExecutor(max_workers=7) as executor:
        futures = [
            executor.submit(_resolve_filmix),
            executor.submit(_resolve_hdrezka),
            executor.submit(_resolve_kodik),
            executor.submit(_resolve_videocdn),
            executor.submit(_resolve_delivembd),
            executor.submit(_resolve_bazon),
            executor.submit(_resolve_torrents)
        ]
        for f in futures:
            try:
                res = f.result(timeout=6.0)
                if res:
                    src_name, src_payload = res
                    resolved[src_name] = src_payload
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

@app.get("/api/media/episodes")
def get_media_episodes(
    source: str = Query("hdrezka"),
    media_id: str = Query(...),
    translator_id: str = Query(...),
    title: Optional[str] = Query(None),
    year: Optional[str] = Query(None),
    is_series: Optional[str] = Query(None),
    kp_id: Optional[str] = Query(None),
    original_title: Optional[str] = Query(None)
) -> List[Dict[str, Any]]:
    """Returns authentic translator-specific seasons and episodes aggregated across sources."""
    clean_t = re.sub(r'\(.*?\)|\[.*?\]', '', title).strip() if title else ""
    year_int = safe_parse_year(year)
    is_ser_bool = bool(int(is_series)) if str(is_series).isdigit() else (bool(is_series) if is_series is not None else True)
    resolved_kp = kp_id if kp_id else (media_id if media_id.isdigit() else None)
    target_id = media_id
    rz_seasons = []

    if not translator_id.startswith("kodik_"):
        try:
            if not target_id.startswith("http") and not target_id.startswith("/"):
                if clean_t:
                    rz_items = hdrezka.search(clean_t)
                    rz_match = find_best_match(rz_items, year_int, is_ser_bool=True)
                    if not rz_match:
                        ser_items = [it for it in rz_items if it.is_series]
                        rz_match = ser_items[0] if ser_items else (rz_items[0] if rz_items else None)
                    if rz_match:
                        target_id = rz_match.id
            if target_id and (target_id.startswith("http") or target_id.startswith("/") or target_id.isdigit()):
                rz_seasons = hdrezka.get_episodes(target_id, translator_id, title=title)
        except Exception:
            pass

    # Check Filmix for matching episodes
    filmix_seasons = []
    try:
        fx_id = media_id if (source == "filmix" and media_id.isdigit()) else None
        if not fx_id and clean_t:
            fx_items = filmix.search(clean_t)
            fx_match = find_best_match(fx_items, year_int, is_ser_bool)
            if fx_match:
                fx_id = fx_match.id
        if fx_id:
            fx_res = filmix.get_streams(fx_id, season=1, episode=1, audio_id=translator_id if translator_id.isdigit() else None)
            if fx_res and fx_res.seasons:
                for s in fx_res.seasons:
                    filmix_seasons.append({
                        "season_id": s.season_id,
                        "season_number": s.season_id,
                        "title": s.title or f"Сезон {s.season_id}",
                        "episodes": [
                            {"episode_id": ep.episode_id, "episode_number": ep.episode_id, "title": ep.title or f"Серия {ep.episode_id}"}
                            for ep in s.episodes
                        ]
                    })
    except Exception:
        pass

    # Check Kodik for matching episodes
    kodik_seasons = []
    try:
        if clean_t:
            k_raw_items = kodik.search(clean_t, year=year_int, kp_id=resolved_kp)
            def _is_matching_title_local(item_title: str, query: str) -> bool:
                def norm(s: str) -> str:
                    return re.sub(r'[^a-zA-Zа-яА-Я0-9]', '', (s or "").lower())
                qt = norm(query)
                it = norm(item_title)
                return bool(qt and it and (qt in it or it in qt))

            k_items = [
                it for it in k_raw_items
                if _is_matching_title_local(it.title, clean_t) or (resolved_kp and str(getattr(it, "kinopoisk_id", "") or "") == str(resolved_kp))
            ]
            matched_k = None
            if translator_id.startswith("kodik_"):
                matched_k_id = translator_id.replace("kodik_", "")
                matched_k = next((it for it in k_items if it.id == matched_k_id), None)

            if not matched_k:
                t_needle = ""
                if target_id and translator_id.isdigit():
                    try:
                        rz_det = hdrezka.get_media_details(target_id)
                        for tr in rz_det.get("translators", []):
                            if str(tr.get("id")) == str(translator_id):
                                t_needle = tr.get("name", "").lower().strip()
                                break
                    except Exception:
                        pass
                if t_needle:
                    matched_k = next((it for it in k_items if t_needle in str(it.extra_data.get("translation", "")).lower() or str(it.extra_data.get("translation", "")).lower() in t_needle), None)

            if not matched_k and translator_id == "618":
                matched_k = next((it for it in k_items if "дубляж" in str(it.extra_data.get("translation", "")).lower()), None)

            if not matched_k and k_items:
                # Fallback to the Kodik item with the most episodes
                matched_k = max(k_items, key=lambda it: max((len(sv.get("episodes", {})) for sv in it.extra_data.get("seasons", {}).values() if isinstance(sv, dict)), default=0))

            if matched_k and matched_k.extra_data.get("seasons"):
                k_raw_s = matched_k.extra_data["seasons"]
                for s_k, s_v in k_raw_s.items():
                    s_num = int(s_k) if s_k.isdigit() else 1
                    ep_keys = sorted([int(x) for x in s_v.get("episodes", {}).keys() if x.isdigit()])
                    kodik_seasons.append({
                        "season_id": s_num,
                        "season_number": s_num,
                        "title": f"Сезон {s_num}",
                        "episodes": [
                            {"episode_id": ep_n, "episode_number": ep_n, "title": f"Серия {ep_n}"}
                            for ep_n in ep_keys
                        ]
                    })
    except Exception:
        pass

    rz_eps = sum(len(s.get("episodes", [])) for s in rz_seasons)
    kd_eps = sum(len(s.get("episodes", [])) for s in kodik_seasons)
    fx_eps = sum(len(s.get("episodes", [])) for s in filmix_seasons)

    # If explicitly requested source has > 1 episodes, return it
    if (source == "kodik" or translator_id.startswith("kodik_")) and kd_eps > 1:
        return kodik_seasons
    if source == "filmix" and fx_eps > 1:
        return filmix_seasons
    if source == "hdrezka" and rz_eps > 1:
        return rz_seasons

    # If any source has full series episodes, return the one with the maximum episodes!
    candidates = [
        (rz_eps, rz_seasons),
        (kd_eps, kodik_seasons),
        (fx_eps, filmix_seasons)
    ]
    best_eps, best_seasons = max(candidates, key=lambda c: c[0])
    if best_eps > 0:
        return best_seasons

    return []

@app.get("/api/media/details")
def get_media_details_query(
    source: str = Query("bazon"),
    media_id: Optional[str] = Query(""),
    title: Optional[str] = None,
    year: Optional[str] = None,
    is_series: Optional[str] = None,
    kp_id: Optional[str] = None,
    original_title: Optional[str] = None
) -> Dict[str, Any]:
    return _fetch_media_details(source, media_id or "", title, year, is_series, kp_id, original_title)

@app.get("/api/media/{source}/{media_id}/details")
def get_media_details_path(source: str, media_id: str, title: Optional[str] = None, year: Optional[str] = None, is_series: Optional[str] = None, kp_id: Optional[str] = None, original_title: Optional[str] = None) -> Dict[str, Any]:
    return _fetch_media_details(source, media_id, title, year, is_series, kp_id, original_title)


@app.get("/api/media/comments")
def get_media_comments_query(
    source: str = Query("filmix"),
    media_id: Optional[str] = Query(""),
    title: Optional[str] = None
) -> List[Dict[str, Any]]:
    return _fetch_media_comments(source, media_id or "", title)

@app.get("/api/media/{source}/{media_id}/comments")
def get_media_comments_path(source: str, media_id: str, title: Optional[str] = None) -> List[Dict[str, Any]]:
    return _fetch_media_comments(source, media_id, title)


def _resolve_trailer(title: str, year: Optional[str] = None, kp_id: Optional[str] = None) -> Optional[str]:
    """Resolves trailer/teaser stream or web URL."""
    # 1. Kinopoisk Unofficial API videos
    if kp_id and str(kp_id).isdigit():
        try:
            url = f"https://kinopoiskapiunofficial.tech/api/v2.2/films/{kp_id}/videos"
            req = urllib.request.Request(url, headers={"X-API-KEY": "e069b222-2ba6-455b-b9f1-f0ca333246eb", "User-Agent": "ShowHubTV"})
            with urllib.request.urlopen(req, timeout=4) as resp:
                if resp.status == 200:
                    data = json.loads(resp.read().decode("utf-8"))
                    for it in data.get("items", []):
                        u = it.get("url", "")
                        if u.startswith("http"):
                            return u
        except Exception:
            pass

    # 2. Bazon trailer
    try:
        b_items = bazon.search(title)
        b_match = find_best_match(b_items, safe_parse_year(year), None)
        if b_match and b_match.kinopoisk_id:
            b_info = bazon.get_details(b_match.kinopoisk_id)
            if b_info and b_info.get("trailer"):
                return b_info["trailer"]
    except Exception:
        pass

    # 3. Web trailer fallback
    q_enc = urllib.parse.quote(f"трейлер {title} {year or ''}".strip())
    return f"https://www.youtube.com/results?search_query={q_enc}"


@app.get("/api/media/trailer")
def get_media_trailer(
    title: str = Query(...),
    year: Optional[str] = None,
    kp_id: Optional[str] = None
) -> Dict[str, Any]:
    trailer = _resolve_trailer(title, year, kp_id)
    return {
        "success": bool(trailer),
        "web_url": trailer or "",
        "embed_url": trailer or "",
        "app_url": trailer or ""
    }



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
    is_series: Optional[str] = None,
    start_min: Optional[int] = None
) -> Dict[str, Any]:
    """Returns a fast silent preview direct video stream (HLS/MP4) for TV card hover. Strictly no trailers or iframes."""
    cache_key = f"{source}_{media_id}_{kp_id}_{title}_{year}_{is_series}_{start_min}"
    if cache_key in _preview_cache:
        return _preview_cache[cache_key]

    # Try to find direct stream (HDRezka, Filmix, Bazon)
    # Prefer lightweight SD 480p/360p/720p or standard 1080p, strictly excluding Ultra/4K/2160p/1440p
    clean_title = re.sub(r'\(.*?\)|\[.*?\]', '', title).strip() if title else ""
    if clean_title and ":" in clean_title:
        clean_title = clean_title.split(":")[0].strip()
    if clean_title and " - " in clean_title:
        clean_title = clean_title.split(" - ")[0].strip()

    candidate_streams = []

    def is_usable_preview_stream(st):
        if not st or not getattr(st, "url", None):
            return False
        if getattr(st, "stream_type", "hls") not in ["hls", "mp4"]:
            return False
        u = str(st.url).lower()
        if any(bad in u for bad in ["rhtie.mp4", "rhtie", "trial", "preview", "teaser", "promo"]):
            return False
        if getattr(st, "is_premium", False):
            return False
        q = str(st.quality).lower()
        if any(bad in q for bad in ["ultra", "4k", "2160", "1440", "vip", "premium"]):
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

    target_kp = kp_id
    if not target_kp and media_id and str(media_id).isdigit():
        target_kp = str(media_id)

    # Source 4: Bazon if real KP ID
    if not candidate_streams and (target_kp or (source in ["bazon", "videocdn", "delivembd"] and media_id and media_id.isdigit())):
        target_id = target_kp or media_id
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
            if start_min is not None and start_min > 0:
                seek_seconds = start_min * 60
            else:
                is_ser_flag = str(is_series) in ["1", "true", "True"]
                seek_seconds = 720 if is_ser_flag else 1320
            res = {
                "success": True,
                "stream_url": chosen.url,
                "stream_type": chosen.stream_type,
                "quality": chosen.quality,
                "start_time": seek_seconds,
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

# User Analytics & Telemetry
USERS_STATS_FILE = os.path.join(CURRENT_DIR, "data", "users_stats.json")
users_stats_lock = threading.Lock()

def _load_users_stats() -> Dict[str, Any]:
    if os.path.exists(USERS_STATS_FILE):
        try:
            with open(USERS_STATS_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}
    return {}

def _save_users_stats(data: Dict[str, Any]):
    os.makedirs(os.path.dirname(USERS_STATS_FILE), exist_ok=True)
    try:
        with open(USERS_STATS_FILE, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f"[Analytics] Error saving user stats: {e}")

@app.get("/api/analytics/ping")
def analytics_ping(device_id: str = Query(..., description="Unique device ID"), version: Optional[str] = "2.7.0") -> Dict[str, Any]:
    """Records device heartbeat and returns aggregate user counts."""
    now_ts = int(time.time())
    with users_stats_lock:
        stats = _load_users_stats()
        device_entry = stats.get(device_id, {})
        if not device_entry:
            device_entry = {
                "first_seen": now_ts,
                "created_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            }
        device_entry["last_seen"] = now_ts
        device_entry["version"] = version
        device_entry["updated_at"] = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        stats[device_id] = device_entry
        _save_users_stats(stats)
        
        total_users = len(stats)
        active_today = sum(1 for d in stats.values() if now_ts - d.get("last_seen", 0) <= 86400)
        active_month = sum(1 for d in stats.values() if now_ts - d.get("last_seen", 0) <= 30 * 86400)

    return {
        "status": "ok",
        "total_users": total_users,
        "active_today": active_today,
        "active_month": active_month
    }

@app.get("/api/analytics/users")
def get_user_stats() -> Dict[str, Any]:
    """Returns aggregated count of total and active users."""
    now_ts = int(time.time())
    with users_stats_lock:
        stats = _load_users_stats()
        total_users = len(stats)
        active_today = sum(1 for d in stats.values() if now_ts - d.get("last_seen", 0) <= 86400)
        active_month = sum(1 for d in stats.values() if now_ts - d.get("last_seen", 0) <= 30 * 86400)
    return {
        "status": "ok",
        "total_users": total_users,
        "active_today": active_today,
        "active_month": active_month
    }

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
        
# ---------------------------------------------------------
# Bug Reporting / Feedback Endpoints
# ---------------------------------------------------------
BUG_REPORTS_FILE = os.path.join(CURRENT_DIR, "data", "bug_reports.json")

@app.post("/api/feedback/bug-report")
def submit_bug_report(payload: Dict[str, Any]):
    """Accepts bug reports from ShowHub TV app and saves to mediacenter/data/bug_reports.json."""
    os.makedirs(os.path.dirname(BUG_REPORTS_FILE), exist_ok=True)
    reports = []
    if os.path.exists(BUG_REPORTS_FILE):
        try:
            with open(BUG_REPORTS_FILE, "r", encoding="utf-8") as f:
                reports = json.load(f)
        except Exception:
            reports = []

    report_entry = {
        "id": int(time.time() * 1000),
        "created_at": datetime.datetime.now().isoformat(),
        "text": payload.get("text", "").strip(),
        "category": payload.get("category", "general"),
        "device": payload.get("device", "Android TV"),
        "app_version": payload.get("app_version", "2.6.8"),
        "version_code": payload.get("version_code", 47),
        "current_screen": payload.get("current_screen", ""),
        "extra": payload.get("extra", {})
    }
    reports.insert(0, report_entry)
    reports = reports[:200]

    with open(BUG_REPORTS_FILE, "w", encoding="utf-8") as f:
        json.dump(reports, f, ensure_ascii=False, indent=2)

    return {"success": True, "id": report_entry["id"], "message": "Bug report received"}

@app.get("/api/feedback/bugs")
def get_bug_reports(limit: int = 50):
    """Returns submitted bug reports."""
    if os.path.exists(BUG_REPORTS_FILE):
        try:
            with open(BUG_REPORTS_FILE, "r", encoding="utf-8") as f:
                reports = json.load(f)
                return {"count": len(reports), "reports": reports[:limit]}
        except Exception:
            pass
    return {"count": 0, "reports": []}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("mediacenter.app:app", host="0.0.0.0", port=8000, reload=False)
