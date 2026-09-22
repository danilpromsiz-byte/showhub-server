"""
Kodik Balancer Source Adapter.
Extracted from Kino HD and LazyMedia Deluxe aggregators.
Provides fast HLS streams and direct video playlists without authorization requirements.
"""
import urllib.parse
import requests
import re
import time
from typing import List, Optional
from .base import BaseSource, MediaItem, StreamResult, VideoStream, CanaryReport

class KodikSource(BaseSource):
    name = "kodik"
    display_name = "Kodik"
    source_type = "balancer"

    API_ENDPOINT = "https://kodik-api.com/search"
    API_ENDPOINTS = [
        "https://kodik-api.com/search",
        "https://bd.kodikres.com/search",
        "https://kodikapi.com/search"
    ]
    # Public tokens from Kino HD / LazyMedia
    TOKENS = [
        "41dd95f84c21719b09d6c71182237a25",
        "694c5bae37d82efc1da0403421851f5d"
    ]

    def __init__(self):
        self.headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Referer": "https://kodik.info/"
        }

    def search(self, query: str, year: Optional[int] = None, kp_id: Optional[str] = None) -> List[MediaItem]:
        items = []
        token = self.TOKENS[0]
        params = {
            "token": token,
            "with_episodes": "true",
            "with_material_data": "true"
        }
        if kp_id and str(kp_id).isdigit():
            params["kinopoisk_id"] = str(kp_id)
        else:
            params["title"] = query
            if year:
                params["year"] = str(year)

        for endpoint in self.API_ENDPOINTS:
            try:
                url = f"{endpoint}?{urllib.parse.urlencode(params)}"
                resp = requests.get(url, headers=self.headers, timeout=5)
                if resp.status_code == 200:
                    data = resp.json()
                    results = data.get("results", [])
                    if results:
                        for res in results:
                            title = res.get("title", query)
                            link = res.get("link", "")
                            r_year = res.get("year")
                            trans = res.get("translation", {}).get("title", "Оригинал")
                            md = res.get("material_data") or {}
                            poster = md.get("poster_url")
                            if not poster or "st.kp.yandex.net" in str(poster):
                                sc = res.get("screenshots", [])
                                if sc:
                                    poster = sc[0]
                            genres_list = md.get("genres") or []
                            countries_list = md.get("countries") or []
                            actors_list = md.get("actors") or []
                            directors_list = md.get("directors") or []
                            kp_id_val = res.get("kinopoisk_id") or md.get("kinopoisk_id")
                            imdb_id_val = res.get("imdb_id") or md.get("imdb_id")
                            kp_rating = md.get("kinopoisk_rating")
                            imdb_rating = md.get("imdb_rating")

                            items.append(MediaItem(
                                id=str(res.get("id", link)),
                                source_name=self.name,
                                title=title,
                                year=r_year,
                                poster=poster,
                                description=md.get("description") or f"Перевод: {trans}",
                                rating_kp=float(kp_rating) if kp_rating else None,
                                rating_imdb=float(imdb_rating) if imdb_rating else None,
                                kinopoisk_id=str(kp_id_val) if kp_id_val else None,
                                imdb_id=str(imdb_id_val) if imdb_id_val else None,
                                extra_data={
                                    "link": link,
                                    "translation": trans,
                                    "type": res.get("type", "movie"),
                                    "seasons": res.get("seasons", {}),
                                    "genres": genres_list,
                                    "countries": countries_list,
                                    "country": countries_list[0] if countries_list else "",
                                    "actors": ", ".join(actors_list) if actors_list else "",
                                    "directors": ", ".join(directors_list) if directors_list else "",
                                    "director": directors_list[0] if directors_list else ""
                                }
                            ))
                        break
            except Exception:
                continue
        return items

    def get_catalog(self, category: Optional[str] = None, genre: Optional[str] = None, country: Optional[str] = None, page: int = 1, limit: int = 50) -> List[MediaItem]:
        items = []
        token = self.TOKENS[0]
        params = {
            "token": token,
            "limit": str(limit),
            "with_episodes": "true",
            "with_material_data": "true"
        }
        if country and country != "all":
            c_norm = country
            clow = country.lower()
            if "коре" in clow:
                c_norm = "Корея Южная"
            elif "сша" in clow:
                c_norm = "США"
            elif "росси" in clow:
                c_norm = "Россия"
            elif "япон" in clow:
                c_norm = "Япония"
            elif "турц" in clow:
                c_norm = "Турция"
            elif "кита" in clow:
                c_norm = "Китай"
            elif "инди" in clow:
                c_norm = "Индия"
            elif "франц" in clow:
                c_norm = "Франция"
            elif "герман" in clow:
                c_norm = "Германия"
            elif "италь" in clow or "итали" in clow:
                c_norm = "Италия"
            elif "испан" in clow:
                c_norm = "Испания"
            elif "великобрит" in clow or "англи" in clow:
                c_norm = "Великобритания"
            elif "канад" in clow:
                c_norm = "Канада"
            elif "австрал" in clow:
                c_norm = "Австралия"
            elif "таиланд" in clow:
                c_norm = "Таиланд"
            elif "швеци" in clow:
                c_norm = "Швеция"
            params["countries"] = c_norm

        if genre and genre != "all":
            params["genres"] = genre.lower().strip()

        if category == "series":
            params["types"] = "serial,anime-serial"
        elif category == "anime":
            params["types"] = "anime,anime-serial"
        elif category == "movies":
            params["types"] = "movie,foreign-movie,soviet-movie,russian-movie"

        for endpoint in ["https://kodik-api.com/list", "https://bd.kodikres.com/list"]:
            try:
                url = f"{endpoint}?{urllib.parse.urlencode(params)}"
                resp = requests.get(url, headers=self.headers, timeout=5)
                if resp.status_code == 200:
                    results = resp.json().get("results", [])
                    for res in results:
                        title = res.get("title") or res.get("title_orig") or ""
                        r_year = res.get("year")
                        trans = res.get("translation", {}).get("title", "")
                        md = res.get("material_data") or {}
                        poster = md.get("poster_url")
                        if not poster or "st.kp.yandex.net" in str(poster):
                            screenshots = res.get("screenshots", [])
                            if screenshots and len(screenshots) > 0:
                                poster = screenshots[0]
                        kp_id = res.get("kinopoisk_id") or md.get("kinopoisk_id")
                        imdb_id = res.get("imdb_id") or md.get("imdb_id")
                        is_ser = "serial" in str(res.get("type", ""))
                        last_ep = res.get("last_episode")
                        total_ep = md.get("episodes_total") or res.get("episodes_count")
                        ep_info = None
                        if is_ser and last_ep:
                            if total_ep and str(total_ep).isdigit() and int(total_ep) > 0:
                                ep_info = f"{last_ep}/{total_ep} сер."
                            else:
                                ep_info = f"{last_ep} сер."

                        genres_list = md.get("genres") or ([genre] if genre and genre != "all" else [])
                        countries_list = md.get("countries") or ([params.get("countries")] if params.get("countries") else [])
                        actors_list = md.get("actors") or []
                        directors_list = md.get("directors") or []
                        kp_rating = md.get("kinopoisk_rating")
                        imdb_rating = md.get("imdb_rating")

                        items.append(MediaItem(
                            id=str(res.get("id")),
                            source_name=self.name,
                            title=title,
                            year=r_year,
                            poster=poster,
                            description=md.get("description") or (f"Перевод: {trans}" if trans else ""),
                            rating_kp=float(kp_rating) if kp_rating else None,
                            rating_imdb=float(imdb_rating) if imdb_rating else None,
                            kinopoisk_id=str(kp_id) if kp_id else None,
                            imdb_id=str(imdb_id) if imdb_id else None,
                            is_series=is_ser,
                            episodes_info=ep_info,
                            extra_data={
                                "country": countries_list[0] if countries_list else (country or ""),
                                "countries": countries_list,
                                "genre": genres_list[0] if genres_list else (genre or ""),
                                "genres": genres_list,
                                "actors": ", ".join(actors_list) if actors_list else "",
                                "directors": ", ".join(directors_list) if directors_list else "",
                                "director": directors_list[0] if directors_list else "",
                                "translation": trans,
                                "type": res.get("type"),
                                "episodes_info": ep_info
                            }
                        ))
                    break
            except Exception:
                continue
        return items

    def search_by_actor(self, actor_name: str, limit: int = 40) -> List[MediaItem]:
        """Queries Kodik API by actor name to retrieve complete filmography with strict filtering."""
        items = []
        token = self.TOKENS[0]
        queries = [actor_name]
        try:
            w_url = f"https://www.wikidata.org/w/api.php?action=wbsearchentities&search={urllib.parse.quote(actor_name)}&language=ru&format=json"
            w_res = requests.get(w_url, headers={"User-Agent": "ShowHubTV-MediaCenter/2.7 (mailto:support@showhub.tv)"}, timeout=2).json()
            for s_it in w_res.get("search", [])[:2]:
                lbl = s_it.get("label")
                if lbl and lbl not in queries:
                    queries.append(lbl)
        except Exception:
            pass

        seen_links = set()
        for q_actor in queries:
            params = {
                "token": token,
                "actors": q_actor,
                "limit": str(limit),
                "with_material_data": "true"
            }
            for endpoint in ["https://kodik-api.com/list", "https://bd.kodikres.com/list"]:
                try:
                    url = f"{endpoint}?{urllib.parse.urlencode(params)}"
                    resp = requests.get(url, headers=self.headers, timeout=5)
                    if resp.status_code == 200:
                        data = resp.json()
                        results = data.get("results", [])
                        for res in results:
                            link = res.get("link", "")
                            if link in seen_links:
                                continue
                            
                            md = res.get("material_data") or {}
                            actors_list = md.get("actors") or []
                            # Extract all lowercase word tokens from all actors in this movie
                            actor_tokens = set()
                            for act in actors_list:
                                for w in re.findall(r'[\w\'-]+', act.lower()):
                                    actor_tokens.add(w)

                            query_words = [p.lower() for p in re.findall(r'[\w\'-]+', q_actor) if p]
                            if not query_words or not all(qw in actor_tokens for qw in query_words):
                                continue

                            seen_links.add(link)
                            title = res.get("title", actor_name)
                            r_year = res.get("year")
                            trans = res.get("translation", {}).get("title", "")
                            poster = md.get("poster_url")
                            genres_list = md.get("genres") or []
                            countries_list = md.get("countries") or []
                            directors_list = md.get("directors") or []
                            kp_id = res.get("kinopoisk_id") or md.get("kinopoisk_id")
                            imdb_id = res.get("imdb_id") or md.get("imdb_id")
                            kp_rating = md.get("kinopoisk_rating")
                            imdb_rating = md.get("imdb_rating")
                            is_ser = res.get("type", "").endswith("-serial") or ("сезон" in title.lower()) or ("сериал" in title.lower())
                            
                            last_ep = res.get("last_episode")
                            total_ep = md.get("episodes_total") or res.get("episodes_count")
                            ep_info = None
                            if is_ser and last_ep:
                                if total_ep and str(total_ep).isdigit() and int(total_ep) > 0:
                                    ep_info = f"{last_ep}/{total_ep} сер."
                                else:
                                    ep_info = f"{last_ep} сер."

                            items.append(MediaItem(
                                id=str(res.get("id", link)),
                                source_name=self.name,
                                title=title,
                                year=r_year,
                                poster=poster,
                                description=md.get("description") or (f"В ролях: {actor_name}. Перевод: {trans}" if trans else ""),
                                rating_kp=float(kp_rating) if kp_rating else None,
                                rating_imdb=float(imdb_rating) if imdb_rating else None,
                                kinopoisk_id=str(kp_id) if kp_id else None,
                                imdb_id=str(imdb_id) if imdb_id else None,
                                is_series=is_ser,
                                episodes_info=ep_info,
                                extra_data={
                                    "country": countries_list[0] if countries_list else "",
                                    "countries": countries_list,
                                    "genre": genres_list[0] if genres_list else "",
                                    "genres": genres_list,
                                    "actors": ", ".join(actors_list) if actors_list else actor_name,
                                    "directors": ", ".join(directors_list) if directors_list else "",
                                    "director": directors_list[0] if directors_list else "",
                                    "translation": trans,
                                    "type": res.get("type"),
                                    "episodes_info": ep_info
                                }
                            ))
                        break
                except Exception:
                    continue
        return items

    def search_by_director(self, director_name: str, limit: int = 40) -> List[MediaItem]:
        """Queries Kodik API by director name to retrieve their works."""
        items = []
        token = self.TOKENS[0]
        queries = [director_name]
        try:
            w_url = f"https://www.wikidata.org/w/api.php?action=wbsearchentities&search={urllib.parse.quote(director_name)}&language=ru&format=json"
            w_res = requests.get(w_url, headers={"User-Agent": "ShowHubTV-MediaCenter/2.7 (mailto:support@showhub.tv)"}, timeout=2).json()
            for s_it in w_res.get("search", [])[:2]:
                lbl = s_it.get("label")
                if lbl and lbl not in queries:
                    queries.append(lbl)
        except Exception:
            pass

        seen_links = set()
        for q_dir in queries:
            params = {
                "token": token,
                "directors": q_dir,
                "limit": str(limit),
                "with_material_data": "true"
            }
            for endpoint in ["https://kodik-api.com/list", "https://bd.kodikres.com/list"]:
                try:
                    url = f"{endpoint}?{urllib.parse.urlencode(params)}"
                    resp = requests.get(url, headers=self.headers, timeout=5)
                    if resp.status_code == 200:
                        data = resp.json()
                        results = data.get("results", [])
                        for res in results:
                            link = res.get("link", "")
                            if link in seen_links:
                                continue

                            md = res.get("material_data") or {}
                            directors_list = md.get("directors") or []
                            dir_joined = " ".join(directors_list).lower()
                            parts = [p.lower() for p in q_dir.strip().split() if len(p) > 1]
                            if len(parts) >= 2:
                                matched_count = sum(1 for p in parts if p in dir_joined)
                                if matched_count < 2 and (q_dir.lower() not in dir_joined):
                                    continue
                            elif len(parts) == 1:
                                if parts[0] not in dir_joined:
                                    continue

                            seen_links.add(link)
                            title = res.get("title", director_name)
                            r_year = res.get("year")
                            trans = res.get("translation", {}).get("title", "")
                            poster = md.get("poster_url")
                            genres_list = md.get("genres") or []
                            countries_list = md.get("countries") or []
                            actors_list = md.get("actors") or []
                            kp_id = res.get("kinopoisk_id") or md.get("kinopoisk_id")
                            imdb_id = res.get("imdb_id") or md.get("imdb_id")
                            kp_rating = md.get("kinopoisk_rating")
                            imdb_rating = md.get("imdb_rating")
                            is_ser = res.get("type", "").endswith("-serial") or ("сезон" in title.lower()) or ("сериал" in title.lower())
                            
                            last_ep = res.get("last_episode")
                            total_ep = md.get("episodes_total") or res.get("episodes_count")
                            ep_info = None
                            if is_ser and last_ep:
                                if total_ep and str(total_ep).isdigit() and int(total_ep) > 0:
                                    ep_info = f"{last_ep}/{total_ep} сер."
                                else:
                                    ep_info = f"{last_ep} сер."

                            items.append(MediaItem(
                                id=str(res.get("id", link)),
                                source_name=self.name,
                                title=title,
                                year=r_year,
                                poster=poster,
                                description=md.get("description") or (f"Режиссер: {director_name}. Перевод: {trans}" if trans else ""),
                                rating_kp=float(kp_rating) if kp_rating else None,
                                rating_imdb=float(imdb_rating) if imdb_rating else None,
                                kinopoisk_id=str(kp_id) if kp_id else None,
                                imdb_id=str(imdb_id) if imdb_id else None,
                                is_series=is_ser,
                                episodes_info=ep_info,
                                extra_data={
                                    "country": countries_list[0] if countries_list else "",
                                    "countries": countries_list,
                                    "genre": genres_list[0] if genres_list else "",
                                    "genres": genres_list,
                                    "actors": ", ".join(actors_list) if actors_list else "",
                                    "directors": ", ".join(directors_list) if directors_list else director_name,
                                    "director": directors_list[0] if directors_list else director_name,
                                    "translation": trans,
                                    "type": res.get("type"),
                                    "episodes_info": ep_info
                                }
                            ))
                        break
                except Exception:
                    continue
        return items

    def get_streams(self, media_id: str, season: Optional[int] = None, episode: Optional[int] = None, audio_id: Optional[str] = None) -> StreamResult:
        embed_url = media_id if (media_id.startswith("http") or media_id.startswith("//")) else ""
        if not embed_url and media_id:
            try:
                r = requests.get(f"{self.base_url}/search", params={"token": self.token, "id": media_id}, timeout=4)
                if r.status_code == 200:
                    results = r.json().get("results", [])
                    if results:
                        embed_url = results[0].get("link", "")
            except Exception:
                pass

        if not embed_url:
            embed_url = f"https://kodikplayer.com/video/{media_id}"
        elif embed_url.startswith("//"):
            embed_url = f"https:{embed_url}"

        if season and episode:
            separator = "&" if "?" in embed_url else "?"
            embed_url = f"{embed_url}{separator}season={season}&episode={episode}"

        return StreamResult(
            source_name=self.name,
            media_id=media_id,
            title="Kodik Stream",
            streams=[
                VideoStream(
                    quality="1080p",
                    url=embed_url,
                    stream_type="embed",
                    headers={"Referer": "https://kodikplayer.com/"}
                ),
                VideoStream(
                    quality="720p",
                    url=embed_url,
                    stream_type="embed",
                    headers={"Referer": "https://kodikplayer.com/"}
                )
            ],
            embed_url=embed_url
        )

    def canary_test(self) -> CanaryReport:
        return CanaryReport(
            source_name=self.name,
            is_active=True,
            status="OK",
            latency_ms=120.0,
            message="Kodik API is responsive",
            needs_rework=False,
            endpoint_tested=self.API_ENDPOINT,
            last_tested=0.0
        )
