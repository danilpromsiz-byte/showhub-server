"""
ShowHub Lampa / TMDb Source Integration.
Extracts popularity rankings, ratings, high-res posters, backdrops, cast, directors,
recommendations, age ratings, and keywords from Lampa's engine.
"""
import re
import json
import logging
import urllib.parse
from typing import Dict, Any, List, Optional, Tuple
import requests

logger = logging.getLogger("lampa_source")

LAMPA_API_KEY = "4ef0d7355d9ffb5151e987764708ce96"
BASE_URLS = [
    "https://api.themoviedb.org/3",
    "https://apitmdb.cub.red/3"
]
IMG_BASE = "https://image.tmdb.org/t/p"

NON_RU_LATIN_SCRIPT = re.compile(
    r'[\u4e00-\u9fff\u3400-\u4dbf\uac00-\ud7af\u1100-\u11ff\u3040-\u309f\u30a0-\u30ff'
    r'\u0900-\u097f\u0d00-\u0d7f\u0b80-\u0bff\u0c00-\u0c7f\u0e00-\u0e7f\u0600-\u06ff\u0590-\u05ff]'
)
READABLE_CHARS = re.compile(r'[\u0400-\u04ffA-Za-z]')

def is_untranslated_script(s: Optional[str]) -> bool:
    if not s:
        return False
    clean = str(s).strip()
    return bool(NON_RU_LATIN_SCRIPT.search(clean)) and not bool(READABLE_CHARS.search(clean))

ISO_COUNTRY_MAP = {
    "IT": "Италия", "AR": "Аргентина", "US": "США", "RU": "Россия", "SU": "СССР",
    "FR": "Франция", "DE": "Германия", "GB": "Великобритания", "UK": "Великобритания",
    "ES": "Испания", "JP": "Япония", "KR": "Корея Южная", "CN": "Китай", "HK": "Гонконг",
    "TW": "Тайвань", "IN": "Индия", "TR": "Турция", "TH": "Таиланд", "SE": "Швеция",
    "MX": "Мексика", "CA": "Канада", "AU": "Австралия", "BR": "Бразилия", "PL": "Польша",
    "DK": "Дания", "NO": "Норвегия", "FI": "Финляндия", "BE": "Бельгия", "NL": "Нидерланды"
}

def normalize_age_rating(cert: Optional[str]) -> Optional[str]:
    """Normalizes Russian or international age ratings into clean Russian format (18+, 16+, 12+, 6+, 0+)."""
    if not cert:
        return None
    c = str(cert).strip().upper()
    if c in ["18+", "18"]:
        return "18+"
    if c in ["16+", "16"]:
        return "16+"
    if c in ["12+", "12", "PG-13", "TV-14"]:
        return "12+"
    if c in ["6+", "6", "PG", "TV-PG"]:
        return "6+"
    if c in ["0+", "0", "G", "TV-G", "TV-Y"]:
        return "0+"
    if c in ["R", "NC-17", "TV-MA"]:
        return "18+"
    if c.isdigit():
        return f"{c}+"
    return c

class LampaSource:
    def __init__(self, api_key: str = LAMPA_API_KEY):
        self.api_key = api_key
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Accept": "application/json"
        })

    def _get(self, endpoint: str, params: Optional[Dict[str, Any]] = None, timeout: int = 6) -> Optional[Dict[str, Any]]:
        req_params = {"api_key": self.api_key, "language": "ru-RU"}
        if params:
            req_params.update(params)

        for base in BASE_URLS:
            url = f"{base}/{endpoint.lstrip('/')}"
            try:
                resp = self.session.get(url, params=req_params, timeout=timeout)
                if resp.status_code == 200:
                    return resp.json()
            except Exception as e:
                logger.debug(f"[LampaSource] Request to {url} failed: {e}")
                continue
        return None

    def get_main_screen_feeds(self, max_pages_per_feed: int = 2) -> List[Dict[str, Any]]:
        """
        Fetches all core feeds from Lampa's main screen:
        - trending/movie/day (В тренде за сегодня)
        - trending/movie/week (В тренде за неделю)
        - movie/now_playing (Сейчас смотрят)
        - movie/popular (Популярные фильмы)
        - trending/tv/week (Популярные сериалы)
        - tv/popular (Популярные сериалы)
        - movie/top_rated (Лучшие фильмы)
        - tv/top_rated (Лучшие сериалы)
        - movie/upcoming (Скоро)
        """
        feeds = [
            ("trending/movie/day", False, 1.4),
            ("trending/movie/week", False, 1.3),
            ("movie/now_playing", False, 1.35),
            ("movie/popular", False, 1.2),
            ("trending/tv/day", True, 1.4),
            ("trending/tv/week", True, 1.3),
            ("tv/popular", True, 1.2),
            ("tv/on_the_air", True, 1.25),
        ]

        seen_keys = set()
        items = []

        for endpoint, is_ser, weight in feeds:
            for page in range(1, max_pages_per_feed + 1):
                try:
                    data = self._get(endpoint, {"page": page})
                    if not data or "results" not in data:
                        break
                    for it in data.get("results", []):
                        m_id = it.get("id")
                        title = it.get("title") or it.get("name")
                        if not m_id or not title:
                            continue
                        key = (str(title).lower().strip(), is_ser)
                        if key in seen_keys:
                            continue
                        seen_keys.add(key)

                        parsed = self._parse_card(it, is_ser=is_ser, weight=weight)
                        if parsed:
                            items.append(parsed)
                except Exception as e:
                    logger.debug(f"[LampaSource] Error fetching {endpoint} p{page}: {e}")
                    break

        return items

    def _parse_card(self, it: Dict[str, Any], is_ser: bool, weight: float = 1.0) -> Optional[Dict[str, Any]]:
        m_id = it.get("id")
        title = it.get("title") or it.get("name")
        if not m_id or not title:
            return None

        orig_title = it.get("original_title") or it.get("original_name")
        if is_untranslated_script(title):
            if orig_title and not is_untranslated_script(orig_title) and READABLE_CHARS.search(orig_title):
                title = orig_title
            else:
                return None

        date_str = it.get("release_date") or it.get("first_air_date") or ""
        year = int(date_str[:4]) if len(date_str) >= 4 and date_str[:4].isdigit() else None

        raw_pop = float(it.get("popularity") or 0.0)
        pop_score = raw_pop * weight
        if is_ser and year and year < 2024:
            pop_score = min(pop_score * 0.05, 5.0)

        raw_vote = float(it.get("vote_average") or 0.0)
        votes = int(it.get("vote_count") or 0)

        poster_path = it.get("poster_path")
        poster = f"{IMG_BASE}/w342{poster_path}" if poster_path else None
        backdrop_path = it.get("backdrop_path")
        backdrop = f"{IMG_BASE}/w780{backdrop_path}" if backdrop_path else None

        overview = it.get("overview") or ""
        origin_country = it.get("origin_country") or []
        country = ISO_COUNTRY_MAP.get(origin_country[0].upper()) if origin_country else None

        return {
            "id": f"tmdb_{m_id}",
            "tmdb_id": str(m_id),
            "source_name": "lampa",
            "title": title,
            "original_title": orig_title,
            "year": year,
            "is_series": is_ser,
            "poster": poster,
            "backdrop": backdrop,
            "description": overview,
            "rating_lampa": round(raw_vote, 1) if raw_vote > 0 else None,
            "rating_kp": None,
            "rating_imdb": round(raw_vote, 1) if raw_vote > 0 else None,
            "lampa_popularity": round(pop_score, 2),
            "vote_count": votes,
            "country": country,
            "countries": [country] if country else [],
            "genre_ids": it.get("genre_ids", []),
            "extra_data": {
                "tmdb_id": str(m_id),
                "lampa_raw_popularity": raw_pop,
                "vote_count": votes
            }
        }

    def get_details_with_appends(self, tmdb_id: str, is_series: bool = False) -> Optional[Dict[str, Any]]:
        """
        Fetches full enriched metadata for a film/series:
        Credits (actors, directors), age certification, keywords, recommendations.
        """
        endpoint = f"tv/{tmdb_id}" if is_series else f"movie/{tmdb_id}"
        append = "credits,recommendations,similar,content_ratings,release_dates,keywords"
        data = self._get(endpoint, {"append_to_response": append}, timeout=8)
        if not data:
            return None

        title = data.get("title") or data.get("name")
        orig_title = data.get("original_title") or data.get("original_name")
        date_str = data.get("release_date") or data.get("first_air_date") or ""
        year = int(date_str[:4]) if len(date_str) >= 4 and date_str[:4].isdigit() else None

        # Age certification
        age_limit = None
        if not is_series:
            rel_results = data.get("release_dates", {}).get("results", [])
            for iso in ["RU", "US", "GB", "KR"]:
                entry = next((x for x in rel_results if x.get("iso_3166_1") == iso), None)
                if entry:
                    for d in entry.get("release_dates", []):
                        cert = (d.get("certification") or "").strip()
                        if cert:
                            age_limit = normalize_age_rating(cert)
                            break
                if age_limit:
                    break
        else:
            cr_results = data.get("content_ratings", {}).get("results", [])
            for iso in ["RU", "US", "GB", "KR"]:
                entry = next((x for x in cr_results if x.get("iso_3166_1") == iso), None)
                if entry and entry.get("rating"):
                    age_limit = normalize_age_rating(entry.get("rating"))
                    break

        # Countries
        countries = []
        for pc in data.get("production_countries", []):
            iso = pc.get("iso_3166_1", "").upper()
            c_name = ISO_COUNTRY_MAP.get(iso) or pc.get("name")
            if c_name and c_name not in countries:
                countries.append(c_name)
        if not countries and data.get("origin_country"):
            for iso in data["origin_country"]:
                c_name = ISO_COUNTRY_MAP.get(iso.upper())
                if c_name and c_name not in countries:
                    countries.append(c_name)

        # Genres
        genres = [g["name"] for g in data.get("genres", []) if g.get("name")]

        # Cast & Crew
        credits = data.get("credits", {})
        cast_list = []
        actors_names = []
        for c in credits.get("cast", [])[:15]:
            name = c.get("name", "").strip()
            if not name:
                continue
            photo = f"{IMG_BASE}/w185{c['profile_path']}" if c.get("profile_path") else ""
            char = c.get("character", "")
            cast_list.append({"name": name, "character": char, "photo": photo})
            actors_names.append(name)

        directors_list = []
        director_names = []
        for cr in credits.get("crew", []):
            job = cr.get("job", "")
            if job in ["Director", "Executive Producer"]:
                d_name = cr.get("name", "").strip()
                if d_name and d_name not in director_names:
                    d_photo = f"{IMG_BASE}/w185{cr['profile_path']}" if cr.get("profile_path") else ""
                    directors_list.append({
                        "name": d_name,
                        "job": "Режиссёр" if job == "Director" else "Продюсер",
                        "photo": d_photo
                    })
                    director_names.append(d_name)

        # Keywords / Tags
        raw_keywords = data.get("keywords", {})
        kw_list = raw_keywords.get("keywords") or raw_keywords.get("results") or []
        tags = [k.get("name") for k in kw_list if k.get("name")]

        # Recommendations (exclude unreleased future movies unless series)
        recs_data = data.get("recommendations", {}).get("results", [])
        recommendations = []
        import datetime
        cur_yr = datetime.date.today().year
        for r in recs_data[:15]:
            r_title = r.get("title") or r.get("name")
            r_poster = f"{IMG_BASE}/w342{r['poster_path']}" if r.get("poster_path") else ""
            r_date = r.get("release_date") or r.get("first_air_date") or ""
            r_year = int(r_date[:4]) if len(r_date) >= 4 and r_date[:4].isdigit() else None
            # Exclude unreleased future movies (like Avatar 4, Avatar 5)
            if not is_series and r_year and r_year > cur_yr:
                continue
            if r_title:
                recommendations.append({
                    "id": f"tmdb_{r.get('id')}",
                    "tmdb_id": str(r.get("id")),
                    "title": r_title,
                    "year": r_year,
                    "poster": r_poster,
                    "rating": r.get("vote_average")
                })

        raw_pop = float(data.get("popularity") or 0.0)
        raw_vote = float(data.get("vote_average") or 0.0)
        votes = int(data.get("vote_count") or 0)

        poster_path = data.get("poster_path")
        poster = f"{IMG_BASE}/w342{poster_path}" if poster_path else None
        backdrop_path = data.get("backdrop_path")
        backdrop = f"{IMG_BASE}/w780{backdrop_path}" if backdrop_path else None

        return {
            "tmdb_id": str(tmdb_id),
            "title": title,
            "original_title": orig_title,
            "year": year,
            "is_series": is_series,
            "poster": poster,
            "backdrop": backdrop,
            "description": data.get("overview") or "",
            "rating_lampa": round(raw_vote, 1) if raw_vote > 0 else None,
            "lampa_popularity": round(raw_pop, 2),
            "age_limit": age_limit,
            "country": countries[0] if countries else None,
            "countries": countries,
            "genres": genres,
            "actors": ", ".join(actors_names[:8]),
            "cast": cast_list,
            "director": ", ".join(director_names[:3]),
            "directors_list": directors_list,
            "tags": tags,
            "recommendations": recommendations,
            "vote_count": votes
        }

lampa_source = LampaSource()
