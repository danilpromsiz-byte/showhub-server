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

def _normalize_title_words(s: str) -> set:
    if not s:
        return set()
    cleaned = re.sub(r'[^\w\s]', ' ', s.lower())
    return {w for w in cleaned.split() if len(w) > 1}

def _calc_title_match(cand: dict, target_title: str, target_orig: Optional[str] = None) -> float:
    t_words = _normalize_title_words(target_title)
    orig_words = _normalize_title_words(target_orig) if target_orig else set()

    cand_titles = [
        cand.get("title") or "",
        cand.get("name") or "",
        cand.get("original_title") or "",
        cand.get("original_name") or ""
    ]
    max_score = 0.0
    for ct in cand_titles:
        if not ct:
            continue
        c_words = _normalize_title_words(ct)
        if not c_words:
            continue
        if t_words:
            inter = len(t_words & c_words)
            score = inter / max(len(t_words), len(c_words))
            if score > max_score:
                max_score = score
        if orig_words:
            inter = len(orig_words & c_words)
            score = inter / max(len(orig_words), len(c_words))
            if score > max_score:
                max_score = score
        # Substring bonus
        if target_title and (target_title.lower() in ct.lower() or ct.lower() in target_title.lower()):
            max_score = max(max_score, 0.7)
        if target_orig and (target_orig.lower() in ct.lower() or ct.lower() in target_orig.lower()):
            max_score = max(max_score, 0.75)
    return max_score

_cache: Dict[str, Any] = {}

class TMDbClient:
    def __init__(self, api_key: str = API_KEY):
        self.api_key = api_key
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "ShowHubTV-MediaCenter/2.8.0",
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
                
                # If year constraint returned nothing, retry query without year constraint
                if year and not results:
                    if media_type == "multi":
                        u2 = f"{BASE_URL}/search/multi?api_key={self.api_key}&query={urllib.parse.quote(q)}&language=ru-RU"
                    else:
                        u2 = f"{BASE_URL}/search/{media_type}?api_key={self.api_key}&query={urllib.parse.quote(q)}&language=ru-RU"
                    resp2 = self.session.get(u2, timeout=4)
                    if resp2.status_code == 200:
                        r_json2 = resp2.json().get("results", [])
                        if r_json2:
                            results = r_json2
                            break

            if not results:
                return None

            # Pick best match using title similarity and year proximity
            matched = None
            best_score = -1.0
            search_title = queries[0] if queries else (title or "")

            for cand in results:
                c_type = cand.get("media_type") or media_type
                if c_type not in ["movie", "tv"]:
                    continue
                date_str = cand.get("release_date") or cand.get("first_air_date") or ""
                cand_year = int(date_str[:4]) if len(date_str) >= 4 and date_str[:4].isdigit() else None

                t_score = _calc_title_match(cand, search_title, original_title)
                # Reject completely mismatched titles (e.g. random Asian anime for a Western movie)
                if t_score < 0.22:
                    continue

                total_score = t_score * 100.0
                if year and cand_year:
                    diff = abs(year - cand_year)
                    if diff == 0:
                        total_score += 40.0
                    elif diff == 1:
                        total_score += 20.0
                    elif diff > 2:
                        continue  # Year mismatch too large

                if total_score > best_score:
                    best_score = total_score
                    matched = (cand, c_type)

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

    def get_trending(self, page: int = 1) -> List[Dict[str, Any]]:
        """Fetches worldwide trending movies and series for the week in Russian."""
        try:
            url = f"{BASE_URL}/trending/all/week?api_key={self.api_key}&language=ru-RU&page={page}"
            resp = self.session.get(url, timeout=5)
            if resp.status_code == 200:
                results = resp.json().get("results", [])
                out = []
                for it in results:
                    media_type = it.get("media_type")
                    if media_type not in ["movie", "tv"]:
                        continue
                    m_id = it.get("id")
                    title = it.get("title") or it.get("name")
                    orig_title = it.get("original_title") or it.get("original_name")
                    r_date = it.get("release_date") or it.get("first_air_date") or ""
                    year_val = None
                    if r_date and len(r_date) >= 4 and r_date[:4].isdigit():
                        year_val = int(r_date[:4])
                    p_path = it.get("poster_path")
                    poster = f"{IMG_BASE}/w780{p_path}" if p_path else None
                    rating = it.get("vote_average")
                    votes = it.get("vote_count", 0)
                    is_ser = (media_type == "tv")
                    out.append({
                        "tmdb_id": m_id,
                        "title": title,
                        "original_title": orig_title,
                        "description": it.get("overview", ""),
                        "year": year_val,
                        "poster": poster,
                        "rating": rating,
                        "vote_count": votes,
                        "is_series": is_ser,
                        "origin_country": it.get("origin_country", [])
                    })
                return out
        except Exception:
            pass
        return []

    def get_popular_movies(self, page: int = 1) -> List[Dict[str, Any]]:
        """Fetches top popular movies from TMDb in Russian."""
        try:
            url = f"{BASE_URL}/movie/popular?api_key={self.api_key}&language=ru-RU&page={page}"
            resp = self.session.get(url, timeout=5)
            if resp.status_code == 200:
                results = resp.json().get("results", [])
                out = []
                for it in results:
                    m_id = it.get("id")
                    title = it.get("title")
                    orig_title = it.get("original_title")
                    r_date = it.get("release_date") or ""
                    year_val = int(r_date[:4]) if (r_date and len(r_date) >= 4 and r_date[:4].isdigit()) else None
                    p_path = it.get("poster_path")
                    poster = f"{IMG_BASE}/w780{p_path}" if p_path else None
                    out.append({
                        "tmdb_id": m_id,
                        "title": title,
                        "original_title": orig_title,
                        "description": it.get("overview", ""),
                        "year": year_val,
                        "poster": poster,
                        "rating": it.get("vote_average"),
                        "vote_count": it.get("vote_count", 0),
                        "is_series": False
                    })
                return out
        except Exception:
            pass
        return []

    def get_popular_series(self, page: int = 1) -> List[Dict[str, Any]]:
        """Fetches top popular TV series from TMDb in Russian."""
        try:
            url = f"{BASE_URL}/tv/popular?api_key={self.api_key}&language=ru-RU&page={page}"
            resp = self.session.get(url, timeout=5)
            if resp.status_code == 200:
                results = resp.json().get("results", [])
                out = []
                for it in results:
                    m_id = it.get("id")
                    title = it.get("name")
                    orig_title = it.get("original_name")
                    r_date = it.get("first_air_date") or ""
                    year_val = int(r_date[:4]) if (r_date and len(r_date) >= 4 and r_date[:4].isdigit()) else None
                    p_path = it.get("poster_path")
                    poster = f"{IMG_BASE}/w780{p_path}" if p_path else None
                    out.append({
                        "tmdb_id": m_id,
                        "title": title,
                        "original_title": orig_title,
                        "description": it.get("overview", ""),
                        "year": year_val,
                        "poster": poster,
                        "rating": it.get("vote_average"),
                        "vote_count": it.get("vote_count", 0),
                        "is_series": True,
                        "origin_country": it.get("origin_country", [])
                    })
                return out
        except Exception:
            pass
        return []

    def search_actor_filmography(self, actor_name: str) -> List[Dict[str, Any]]:
        """Searches for an actor on TMDb and returns their real filmography sorted by popularity."""
        if not actor_name or len(actor_name.strip()) < 2:
            return []
        try:
            q_enc = urllib.parse.quote(actor_name.strip())
            p_url = f"{BASE_URL}/search/person?api_key={self.api_key}&query={q_enc}&language=ru-RU"
            p_resp = self.session.get(p_url, timeout=5)
            if p_resp.status_code != 200:
                return []
            p_results = p_resp.json().get("results", [])
            if not p_results:
                return []

            person_id = p_results[0].get("id")
            if not person_id:
                return []

            c_url = f"{BASE_URL}/person/{person_id}/combined_credits?api_key={self.api_key}&language=ru-RU"
            c_resp = self.session.get(c_url, timeout=6)
            if c_resp.status_code != 200:
                return []

            raw_cast = c_resp.json().get("cast", [])
            sorted_cast = sorted(
                raw_cast,
                key=lambda x: (x.get("vote_count", 0) * 10 + x.get("popularity", 0)),
                reverse=True
            )

            filmography = []
            seen_ids = set()
            for item in sorted_cast:
                m_id = item.get("id")
                if not m_id or m_id in seen_ids:
                    continue
                seen_ids.add(m_id)

                title = item.get("title") or item.get("name")
                if not title:
                    continue
                orig_title = item.get("original_title") or item.get("original_name")
                r_date = item.get("release_date") or item.get("first_air_date") or ""
                year_val = int(r_date[:4]) if (r_date and len(r_date) >= 4 and r_date[:4].isdigit()) else None
                p_path = item.get("poster_path")
                poster = f"{IMG_BASE}/w780{p_path}" if p_path else None
                is_ser = item.get("media_type") == "tv"

                char = item.get("character", "")
                desc = item.get("overview", "")
                if char:
                    role_desc = f"В роли: {char}"
                    desc = f"{role_desc}. {desc}" if desc else role_desc

                filmography.append({
                    "id": f"tmdb_{m_id}",
                    "source_name": "tmdb",
                    "title": title,
                    "original_title": orig_title,
                    "year": year_val,
                    "poster": poster,
                    "description": desc,
                    "rating_imdb": item.get("vote_average"),
                    "rating_kp": item.get("vote_average"),
                    "is_series": is_ser,
                    "actors": actor_name,
                    "extra_data": {
                        "character": char,
                        "person_id": person_id,
                        "person_name": p_results[0].get("name", actor_name)
                    }
                })
            return filmography
        except Exception:
            return []

tmdb = TMDbClient()
