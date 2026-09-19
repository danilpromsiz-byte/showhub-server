import re
import urllib.parse
import requests
from typing import Optional, Dict, Any, List

API_KEY = "8265bd1679663a7ea12ac168da84d2e8"
BASE_URL = "https://api.themoviedb.org/3"
IMG_BASE = "https://image.tmdb.org/t/p"

ISO_COUNTRY_MAP = {
    "IT": "Италия",
    "AR": "Аргентина",
    "US": "США",
    "RU": "Россия",
    "SU": "СССР",
    "FR": "Франция",
    "DE": "Германия",
    "GB": "Великобритания",
    "UK": "Великобритания",
    "ES": "Испания",
    "JP": "Япония",
    "KR": "Корея Южная",
    "CN": "Китай",
    "HK": "Гонконг",
    "TW": "Тайвань",
    "IN": "Индия",
    "TR": "Турция",
    "TH": "Таиланд",
    "SE": "Швеция",
    "MX": "Мексика",
    "CA": "Канада",
    "AU": "Австралия",
    "BR": "Бразилия",
    "PL": "Польша",
    "DK": "Дания",
    "NO": "Норвегия",
    "FI": "Финляндия",
    "BE": "Бельгия",
    "NL": "Нидерланды",
    "CH": "Швейцария",
    "AT": "Австрия",
    "IE": "Ирландия",
    "NZ": "Новая Зеландия",
    "UA": "Украина",
    "KZ": "Казахстан",
    "BY": "Беларусь",
    "CZ": "Чехия",
    "GR": "Греция",
    "PT": "Португалия",
    "IL": "Израиль",
    "ZA": "ЮАР",
    "CO": "Колумбия",
    "CL": "Чили"
}

_cache: Dict[str, Any] = {}

class TMDbClient:
    def __init__(self, api_key: str = API_KEY):
        self.api_key = api_key
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "ShowHubTV-MediaCenter/2.7.7",
            "Accept": "application/json"
        })

    def search_and_enrich(
        self,
        title: str,
        year: Optional[int] = None,
        is_series: Optional[bool] = None,
        original_title: Optional[str] = None
    ) -> Optional[Dict[str, Any]]:
        """Searches TMDb and returns authentic cast, director, country, overview, and poster."""
        if not title and not original_title:
            return None

        # Build candidate search queries
        queries = []
        if title:
            # 1. Strip parens
            clean_title = re.sub(r'\(.*?\)|\[.*?\]', '', title).strip()
            if "/" in clean_title:
                parts = [p.strip() for p in clean_title.split("/") if p.strip()]
                queries.extend(parts)
            else:
                if ":" in clean_title:
                    clean_title = clean_title.split(":")[0].strip()
                if " - " in clean_title:
                    clean_title = clean_title.split(" - ")[0].strip()
                if clean_title:
                    queries.append(clean_title)

            # Extract any original Latin title contained in parens or brackets
            m_parens = re.findall(r'\(([^)]+)\)|\[([^\]]+)\]', title)
            for m1, m2 in m_parens:
                cand = (m1 or m2).strip()
                if cand and re.search(r'[a-zA-Z]', cand) and cand not in queries:
                    queries.append(cand)

        if original_title and original_title.strip() not in queries:
            queries.append(original_title.strip())

        cache_key = f"{queries[0].lower() if queries else ''}_{year}_{is_series}"
        if cache_key in _cache:
            return _cache[cache_key]

        try:
            media_type = "tv" if is_series else ("movie" if is_series is False else "multi")
            results = []

            for q in queries:
                if not q or len(q) < 2:
                    continue
                if media_type == "multi":
                    url = f"{BASE_URL}/search/multi?api_key={self.api_key}&query={urllib.parse.quote(q)}&language=ru-RU"
                else:
                    url = f"{BASE_URL}/search/{media_type}?api_key={self.api_key}&query={urllib.parse.quote(q)}&language=ru-RU"

                if year and media_type == "movie":
                    url += f"&year={year}"
                elif year and media_type == "tv":
                    url += f"&first_air_date_year={year}"

                resp = self.session.get(url, timeout=4)
                if resp.status_code == 200:
                    r_json = resp.json().get("results", [])
                    if r_json:
                        results = r_json
                        break

            if not results:
                return None

            # Pick best match
            matched = None
            for cand in results:
                c_type = cand.get("media_type") or media_type
                if c_type not in ["movie", "tv"]:
                    continue
                date_str = cand.get("release_date") or cand.get("first_air_date") or ""
                cand_year = int(date_str[:4]) if len(date_str) >= 4 and date_str[:4].isdigit() else None
                if year and cand_year and abs(year - cand_year) > 2:
                    continue
                matched = (cand, c_type)
                break

            if not matched and results:
                c_type = results[0].get("media_type") or media_type
                if c_type in ["movie", "tv"]:
                    matched = (results[0], c_type)

            if not matched:
                return None

            item, resolved_type = matched
            m_id = item["id"]

            # Get Details (Country, Overview, Genres, Runtime)
            det_url = f"{BASE_URL}/{resolved_type}/{m_id}?api_key={self.api_key}&language=ru-RU"
            det_res = self.session.get(det_url, timeout=4)
            det_data = det_res.json() if det_res.status_code == 200 else {}

            countries = []
            for pc in det_data.get("production_countries", []):
                iso = pc.get("iso_3166_1", "").upper()
                c_name = ISO_COUNTRY_MAP.get(iso) or pc.get("name")
                if c_name and c_name not in countries:
                    countries.append(c_name)

            if not countries and det_data.get("origin_country"):
                for iso in det_data["origin_country"]:
                    c_name = ISO_COUNTRY_MAP.get(iso.upper())
                    if c_name and c_name not in countries:
                        countries.append(c_name)

            genres = [g["name"] for g in det_data.get("genres", []) if g.get("name")]
            overview = det_data.get("overview") or item.get("overview") or ""
            poster_path = det_data.get("poster_path") or item.get("poster_path")
            poster = f"{IMG_BASE}/w780{poster_path}" if poster_path else None
            backdrop_path = det_data.get("backdrop_path") or item.get("backdrop_path")
            backdrop = f"{IMG_BASE}/w1280{backdrop_path}" if backdrop_path else None

            # Get Credits (Cast with photos, Directors with photos)
            cred_url = f"{BASE_URL}/{resolved_type}/{m_id}/credits?api_key={self.api_key}&language=ru-RU"
            cred_res = self.session.get(cred_url, timeout=4)
            cred_data = cred_res.json() if cred_res.status_code == 200 else {}

            cast_list = []
            actors_str_list = []
            for c in cred_data.get("cast", [])[:15]:
                name = c.get("name", "").strip()
                if not name:
                    continue
                photo = f"{IMG_BASE}/w185{c['profile_path']}" if c.get("profile_path") else ""
                char = c.get("character", "")
                cast_list.append({
                    "name": name,
                    "character": char,
                    "photo": photo
                })
                actors_str_list.append(name)

            directors_list = []
            director_str_list = []
            for cr in cred_data.get("crew", []):
                job = cr.get("job", "")
                if job in ["Director", "Executive Producer"]:
                    d_name = cr.get("name", "").strip()
                    if d_name and d_name not in director_str_list:
                        d_photo = f"{IMG_BASE}/w185{cr['profile_path']}" if cr.get("profile_path") else ""
                        directors_list.append({
                            "name": d_name,
                            "job": "Режиссёр" if job == "Director" else "Продюсер",
                            "photo": d_photo
                        })
                        director_str_list.append(d_name)

            res_obj = {
                "tmdb_id": m_id,
                "title": det_data.get("title") or det_data.get("name") or item.get("title") or item.get("name"),
                "original_title": det_data.get("original_title") or det_data.get("original_name") or item.get("original_title"),
                "description": overview,
                "poster": poster,
                "backdrop": backdrop,
                "country": countries[0] if countries else None,
                "countries": countries,
                "genres": genres,
                "actors": ", ".join(actors_str_list[:8]),
                "cast": cast_list,
                "director": ", ".join(director_str_list[:3]),
                "directors_list": directors_list,
                "rating": det_data.get("vote_average"),
                "is_series": (resolved_type == "tv")
            }

            _cache[cache_key] = res_obj
            return res_obj

        except Exception:
            return None

tmdb = TMDbClient()
