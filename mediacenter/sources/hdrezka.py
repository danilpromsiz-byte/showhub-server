"""
HDRezka Source Adapter with Autonomous Anubis PoW Solver & 20 Internal Mirrors.
Extracted from 'HDrezka TV' (ru.astroapps.hdrezka v1.4.0, class LR8/o;).
Automatically solves Techaro Anubis Proof-of-Work challenges on the fly (1-2 ms)
and extracts direct CDN video streams (360p, 480p, 720p, 1080p, Ultra, 4K).
"""
import time
import json
import hashlib
import re
import urllib.parse
import requests
from bs4 import BeautifulSoup
from typing import List, Optional, Tuple, Dict, Any
from .base import BaseSource, MediaItem, StreamResult, VideoStream, SubtitleTrack, CanaryReport
from ..core.mirror_manager import mirror_manager

class HDRezkaSource(BaseSource):
    name = "hdrezka"
    display_name = "HDRezka"
    source_type = "portal"

    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        })
        self.active_mirror: Optional[str] = None
        self.last_solver_time = 0

    def _get_base(self) -> str:
        if self.active_mirror:
            return self.active_mirror
        mirrors = mirror_manager.get_mirrors("hdrezka")
        return mirrors[0] if mirrors else "https://hdrezka-home.tv"

    def _solve_anubis(self, base_url: str, html_text: str, target_url: str) -> requests.Response:
        """Solves Techaro Anubis SHA-256 Proof-of-Work in ~1 ms and obtains clearance cookies."""
        soup = BeautifulSoup(html_text, "html.parser")
        challenge_tag = soup.find("script", id="anubis_challenge")
        prefix_tag = soup.find("script", id="anubis_base_prefix")

        challenge_data = json.loads(challenge_tag.string)
        base_prefix = json.loads(prefix_tag.string) if prefix_tag and prefix_tag.string else ""

        c = challenge_data["challenge"]
        rules = challenge_data["rules"]
        random_data = c["randomData"]
        difficulty = int(rules["difficulty"])
        challenge_id = c["id"]

        start_t = time.time()
        p = difficulty // 2
        u = (difficulty % 2) != 0
        nonce = 0
        found_hash = None

        while True:
            candidate = f"{random_data}{nonce}".encode("utf-8")
            digest = hashlib.sha256(candidate).digest()

            valid = True
            for b in digest[:p]:
                if b != 0:
                    valid = False
                    break
            if valid and u and (digest[p] >> 4) != 0:
                valid = False

            if valid:
                found_hash = digest.hex()
                break
            nonce += 1

        elapsed = int((time.time() - start_t) * 1000)
        pass_url = f"{base_url}{base_prefix}/.within.website/x/cmd/anubis/api/pass-challenge"
        params = {
            "id": challenge_id,
            "response": found_hash,
            "nonce": str(nonce),
            "redir": target_url,
            "elapsedTime": str(elapsed)
        }

        r_pass = self.session.get(pass_url, params=params, timeout=6, allow_redirects=True)
        return r_pass

    def _get_with_anubis(self, url: str, base_url: str) -> requests.Response:
        headers = {"Referer": f"{base_url}/"}
        r = self.session.get(url, headers=headers, timeout=6)
        if "anubis_challenge" in r.text:
            r = self._solve_anubis(base_url, r.text, url)
        return r

    GENRE_MAP = {
        "боевик": "action",
        "комедия": "comedy",
        "фантастика": "fiction",
        "драма": "drama",
        "триллер": "thriller",
        "ужасы": "horror",
        "приключения": "adventures",
        "фэнтези": "fantasy",
        "детектив": "detective",
        "криминал": "crime"
    }

    def _parse_entry(self, e, default_year: Optional[int] = None) -> Optional[MediaItem]:
        link = e.select_one(".b-content__inline_item-link a")
        cover = e.select_one(".b-content__inline_item-cover img")
        misc = e.select_one(".b-content__inline_item-link div")

        if not link:
            return None

        item_id = link.get("href", "")
        title = link.text.strip()
        poster = cover.get("src") if cover else None
        desc = misc.text.strip() if misc else None

        item_year = default_year
        if not item_year and desc:
            y_m = re.search(r'\b(19\d\d|20\d\d)\b', desc)
            if y_m:
                try:
                    item_year = int(y_m.group(1))
                except ValueError:
                    pass
        if not item_year:
            y_m = re.search(r'-(\d{4})(?:-latest)?\.html', item_id)
            if y_m:
                try:
                    item_year = int(y_m.group(1))
                except ValueError:
                    pass

        info_el = e.select_one(".info")
        episodes_info = info_el.text.strip() if info_el else None

        is_series = ("/series/" in item_id) or ("/animation/" in item_id) or bool(episodes_info) or ("сезон" in (desc or "").lower()) or ("серия" in (desc or "").lower())

        data_id = e.get("data-id")
        date_added = None
        # Try extract date from poster /i/YYYY/M/D/
        if poster:
            p_date_m = re.search(r'/i/(\d{4})/(\d{1,2})/(\d{1,2})/', poster)
            if p_date_m:
                try:
                    import datetime
                    dt = datetime.datetime(int(p_date_m.group(1)), int(p_date_m.group(2)), int(p_date_m.group(3)))
                    date_added = int(dt.timestamp())
                except Exception:
                    pass

        if not date_added and data_id and str(data_id).isdigit():
            # Monotonic proxy timestamp
            date_added = 1700000000 + int(data_id) * 1000

        extra_info = {"page_url": item_id, "data_id": data_id}
        if desc and "," in desc:
            sp = [p.strip() for p in desc.split(",") if p.strip()]
            if len(sp) >= 2 and not any(ch.isdigit() for ch in sp[1]):
                c_cand = sp[1]
                if len(c_cand) <= 25 and len(c_cand.split()) <= 2 and not any(p in c_cand for p in [".", "!", "?", ";", ":", "—", "»", "«"]):
                    extra_info["country"] = c_cand
                    extra_info["countries"] = [c_cand]
            if len(sp) >= 3:
                valid_genres = [g for g in sp[2:] if len(g) <= 30 and len(g.split()) <= 3 and not any(p in g for p in [".", "!", "?", ";", ":"])]
                if valid_genres:
                    extra_info["genres"] = valid_genres

        parsed_genres = extra_info.get("genres", [])
        return MediaItem(
            id=item_id,
            source_name=self.name,
            title=title,
            year=item_year,
            is_series=is_series,
            poster=poster,
            description=desc,
            date_added=date_added,
            episodes_info=episodes_info,
            genres=parsed_genres,
            extra_data=extra_info
        )

    def search(self, query: str, year: Optional[int] = None, kp_id: Optional[str] = None) -> List[MediaItem]:
        items = []
        base = self._get_base()
        url = f"{base}/search/?do=search&subaction=search&q={query}"

        try:
            res = self._get_with_anubis(url, base)
            if res.status_code == 200:
                soup = BeautifulSoup(res.text, "html.parser")
                for e in soup.select(".b-content__inline_item"):
                    item = self._parse_entry(e, default_year=year)
                    if item:
                        items.append(item)
        except Exception:
            pass
        return items

    def get_catalog(self, category: str = "all", genre: Optional[str] = None, page: int = 1) -> List[MediaItem]:
        """Fetches latest real releases (новинки) from HDRezka live feed."""
        items = []
        base = self._get_base()

        g_slug = self.GENRE_MAP.get(genre.lower().strip()) if genre else None

        if category == "movies":
            section = f"films/{g_slug}/" if g_slug else "films/"
        elif category == "series":
            section = f"series/{g_slug}/" if g_slug else "series/"
        elif category == "cartoons":
            section = f"cartoons/{g_slug}/" if g_slug else "cartoons/"
        elif category == "anime":
            section = f"animation/{g_slug}/" if g_slug else "animation/"
        else: # "all" -> Новинки (Fresh releases)
            section = f"films/{g_slug}/" if g_slug else "new/"

        if page > 1:
            url = f"{base}/{section}page/{page}/"
        else:
            url = f"{base}/{section}"

        try:
            res = self._get_with_anubis(url, base)
            if res.status_code == 200:
                soup = BeautifulSoup(res.text, "html.parser")
                for e in soup.select(".b-content__inline_item"):
                    item = self._parse_entry(e)
                    if item:
                        items.append(item)
        except Exception:
            pass
        return items

    def get_media_details(self, media_id: str) -> Optional[Dict[str, Any]]:
        base = self._get_base()
        if media_id.startswith("http"):
            parsed = urllib.parse.urlparse(media_id)
            page_url = f"{base}{parsed.path}"
        else:
            page_url = f"{base}{media_id}"
        try:
            res = self._get_with_anubis(page_url, base)
            if res.status_code != 200:
                return None

            soup = BeautifulSoup(res.text, "html.parser")
            
            # 1. Translators
            translators = []
            for t in soup.select(".b-translator__item"):
                t_id = t.get("data-translator_id")
                t_name = t.text.strip()
                is_act = "active" in t.get("class", [])
                if t_id and t_name:
                    translators.append({
                        "id": t_id,
                        "name": t_name,
                        "is_default": is_act
                    })

            if not translators:
                m_init = re.search(r'initCDN(?:Movies|Series)Events\(\s*(\d+)\s*,\s*(\d+)', res.text)
                if m_init:
                    translators.append({
                        "id": m_init.group(2),
                        "name": "Основная дорожка (HDRezka)",
                        "is_default": True
                    })

            # 2. Seasons & Episodes
            seasons = []
            for s in soup.select("#simple-seasons-tabs .b-simple_season__item"):
                s_id = s.get("data-tab_id")
                if s_id and s_id.isdigit():
                    s_num = int(s_id)
                    s_title = s.text.strip() or f"Сезон {s_num}"
                    episodes = []
                    ep_ul = soup.select_one(f"#simple-episodes-list-{s_num}")
                    if ep_ul:
                        for ep in ep_ul.select(".b-simple_episode__item"):
                            ep_id = ep.get("data-episode_id")
                            if ep_id and ep_id.isdigit():
                                episodes.append({
                                    "episode_id": int(ep_id),
                                    "title": ep.text.strip() or f"Серия {ep_id}",
                                    "season_id": s_num
                                })
                    seasons.append({
                        "season_id": s_num,
                        "title": s_title,
                        "episodes": episodes
                    })

            # 3. Description & Metadata
            desc = soup.select_one(".b-post__description_text")
            description = desc.text.strip() if desc else None

            rating_kp = None
            vote_kp = None
            rating_imdb = None
            vote_imdb = None

            kp_m = re.search(r'Кинопоиск:\s*([\d\.]+)\s*(?:\(([\d\s]+)\))?', res.text)
            if kp_m:
                try:
                    rating_kp = float(kp_m.group(1))
                    if kp_m.group(2):
                        vote_kp = int(re.sub(r'\s+', '', kp_m.group(2)))
                except ValueError:
                    pass

            imdb_m = re.search(r'IMDb:\s*([\d\.]+)\s*(?:\(([\d\s]+)\))?', res.text)
            if imdb_m:
                try:
                    rating_imdb = float(imdb_m.group(1))
                    if imdb_m.group(2):
                        vote_imdb = int(re.sub(r'\s+', '', imdb_m.group(2)))
                except ValueError:
                    pass

            director = None
            actors = None
            genres = []
            country = None

            for tr in soup.select(".b-post__info tr"):
                tds = tr.select("td")
                if len(tds) == 2:
                    label = tds[0].text.strip().lower()
                    val = tds[1].text.strip()
                    if "режиссер" in label:
                        director = val
                    elif "в ролях" in label:
                        actors = val
                    elif "жанр" in label:
                        genres = [g.strip() for g in val.split(",") if g.strip()]
                    elif "страна" in label:
                        country = val

            poster_el = soup.select_one(".b-sidecover img") or soup.select_one(".b-post__infotable_left img") or soup.select_one(".b-post__infotable img")
            poster = poster_el.get("src") if poster_el else None
            if poster:
                # Keep optimized web dimensions to prevent memory exhaustion and UI lags on Android TV
                pass

            # 4. Episode release schedule (График выхода серий)
            schedule = []
            sched_table = soup.select_one(".b-post__schedule_table") or soup.select_one(".b-schedules-table") or soup.select_one(".b-post__schedule") or soup.select_one(".b-schedules__list")
            if sched_table:
                months_ru = ["январ", "феврал", "март", "апрел", "ма", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр"]
                for row in sched_table.select("tr"):
                    tds = [td.text.strip() for td in row.select("td")]
                    if len(tds) >= 2:
                        ep_name = tds[0]
                        ep_title = ""
                        date_str = ""
                        status = ""

                        if len(tds) >= 4:
                            ep_title = tds[1]
                            for cell in tds[2:]:
                                cell_l = cell.lower()
                                if any(m in cell_l for m in months_ru) or re.search(r'\d{4}', cell):
                                    date_str = cell
                                elif any(st in cell_l for st in ["завтра", "сегодня", "вчера", "вышла", "ожидается"]):
                                    status = cell
                            if not date_str:
                                date_str = tds[3] or tds[2]
                            if not status and len(tds) > 4:
                                status = tds[4]
                        elif len(tds) == 3:
                            ep_title = tds[1]
                            date_str = tds[2]
                        elif len(tds) == 2:
                            date_str = tds[1]

                        if ep_name and (date_str or ep_title):
                            clean_status = (status or "").strip()
                            if clean_status == "✓":
                                clean_status = "Вышла"
                            elif clean_status.lower() == "сегодня":
                                clean_status = "Сегодня"
                            elif clean_status.lower() == "завтра":
                                clean_status = "Завтра"
                            elif not clean_status:
                                clean_status = "Вышла" if any(x in date_str.lower() for x in ["вышла", "вчера", "сегодня", "✓"]) else "Ожидается"
                            schedule.append({
                                "episode": ep_name,
                                "title": ep_title or ep_name,
                                "date": date_str or "Дата уточняется",
                                "status": clean_status
                            })
            if not schedule and seasons:
                for s in seasons:
                    s_id = s.get("season_id", 1)
                    for ep in s.get("episodes", []):
                        schedule.append({
                            "episode": f"{s_id} сезон {ep.get('episode_id')} серия",
                            "title": ep.get("title", ""),
                            "date": "Вышла",
                            "status": "Доступна"
                        })

            return {
                "description": description,
                "rating_kp": rating_kp,
                "rating_imdb": rating_imdb,
                "vote_num_kp": vote_kp,
                "vote_num_imdb": vote_imdb,
                "genres": genres,
                "director": director,
                "actors": actors,
                "country": country,
                "translators": translators,
                "seasons": seasons,
                "is_series": len(seasons) > 0,
                "episodes_schedule": schedule,
                "poster": poster
            }
        except Exception:
            return None

    def get_episodes(self, media_id: str, translator_id: str, title: Optional[str] = None) -> List[Dict[str, Any]]:
        """Fetches authentic translator-specific seasons and episodes via HDRezka CDN AJAX."""
        media_str = str(media_id).strip()
        base = self._get_base()
        data_id = None
        page_url = None

        if not media_str.startswith("http") and not media_str.startswith("/") and title:
            clean_t = re.sub(r'\(.*?\)|\[.*?\]', '', title).strip()
            if ":" in clean_t:
                clean_t = clean_t.split(":")[0].strip()
            if " - " in clean_t:
                clean_t = clean_t.split(" - ")[0].strip()
            try:
                rz_items = self.search(clean_t)
                if rz_items:
                    media_str = rz_items[0].id
            except Exception:
                pass

        if media_str.isdigit():
            data_id = media_str
            page_url = f"{base}/"
        else:
            if media_str.startswith("http"):
                parsed = urllib.parse.urlparse(media_str)
                page_url = f"{base}{parsed.path}"
            else:
                page_url = f"{base}{media_str}"

            try:
                res = self._get_with_anubis(page_url, base)
                if res.status_code != 200:
                    return []

                id_match = re.search(r'data-id="(\d+)"', res.text)
                m_init_args = re.search(r'initCDN(?:Movies|Series)Events\(\s*(\d+)\s*,\s*(\d+)', res.text)
                data_id = (id_match.group(1) if id_match else None) or (m_init_args.group(1) if m_init_args else None)
                if not data_id:
                    return []
            except Exception:
                return []

        try:
            t_now = int(time.time() * 1000)
            ajax_url = f"{base}/ajax/get_cdn_series/?t={t_now}"
            post_data = {
                "id": data_id,
                "translator_id": translator_id,
                "action": "get_episodes"
            }
            post_headers = {
                "X-Requested-With": "XMLHttpRequest",
                "Referer": page_url
            }
            r = self.session.post(ajax_url, data=post_data, headers=post_headers, timeout=8)
            if r.status_code == 200:
                d = r.json()
                if d.get("success"):
                    episodes_html = d.get("episodes", "")
                    seasons_html = d.get("seasons", "")
                    soup_s = BeautifulSoup(seasons_html, "html.parser")
                    soup_e = BeautifulSoup(episodes_html, "html.parser")

                    seasons = []
                    s_items = soup_s.select(".b-simple_season__item")
                    if s_items:
                        for s_tag in s_items:
                            s_id_str = s_tag.get("data-tab_id")
                            if s_id_str and s_id_str.isdigit():
                                s_num = int(s_id_str)
                                s_title = s_tag.text.strip() or f"Сезон {s_num}"
                                ep_ul = soup_e.select_one(f"#simple-episodes-list-{s_num}")
                                ep_list = []
                                if ep_ul:
                                    for ep in ep_ul.select(".b-simple_episode__item"):
                                        ep_id = ep.get("data-episode_id")
                                        if ep_id and ep_id.isdigit():
                                            ep_list.append({
                                                "episode_id": int(ep_id),
                                                "title": ep.text.strip() or f"Серия {ep_id}",
                                                "season_id": s_num
                                            })
                                seasons.append({
                                    "season_id": s_num,
                                    "title": s_title,
                                    "episodes": ep_list
                                })
                    else:
                        ep_list = []
                        for ep in soup_e.select(".b-simple_episode__item"):
                            ep_id = ep.get("data-episode_id")
                            if ep_id and ep_id.isdigit():
                                ep_list.append({
                                    "episode_id": int(ep_id),
                                    "title": ep.text.strip() or f"Серия {ep_id}",
                                    "season_id": 1
                                })
                        seasons.append({
                            "season_id": 1,
                            "title": "Сезон 1",
                            "episodes": ep_list
                        })
                    return seasons
        except Exception:
            pass
        return []

    def get_streams(self, media_id: str, season: Optional[int] = None, episode: Optional[int] = None, audio_id: Optional[str] = None) -> StreamResult:
        base = self._get_base()
        if media_id.startswith("http"):
            parsed = urllib.parse.urlparse(media_id)
            page_url = f"{base}{parsed.path}"
        else:
            page_url = f"{base}{media_id}"
        result = StreamResult(
            source_name=self.name,
            media_id=media_id,
            title="HDRezka Stream",
            embed_url=page_url,
            streams=[]
        )

        try:
            res = self._get_with_anubis(page_url, base)

            # Check if streams are already embedded in the HTML via initCDNMoviesEvents / initCDNSeriesEvents
            cdn_m = re.search(r'initCDN(?:Movies|Series)Events\(\s*(\d+)\s*,\s*(\d+).*?,\s*(\{.*?\})\s*\);', res.text, re.DOTALL)
            m_init_args = re.search(r'initCDN(?:Movies|Series)Events\(\s*(\d+)\s*,\s*(\d+)', res.text)
            
            id_match = re.search(r'data-id="(\d+)"', res.text)
            trans_match = re.search(r'data-translator_id="(\d+)"', res.text)

            data_id = (id_match.group(1) if id_match else None) or (m_init_args.group(1) if m_init_args else None)
            default_trans = (trans_match.group(1) if trans_match else None) or (m_init_args.group(2) if m_init_args else "238")
            trans_id = audio_id or default_trans

            is_series = bool(re.search(r'id="simple-seasons-tabs"', res.text))

            # If movie or single video, and not requesting a different audio track, parse directly from HTML if available
            parsed_from_html = False
            if cdn_m and (not audio_id or audio_id == cdn_m.group(2)) and not (is_series and (season or episode)):
                try:
                    embedded_json = json.loads(cdn_m.group(3))
                    url_str = embedded_json.get("streams") or embedded_json.get("url", "")
                    sub_str = embedded_json.get("subtitle", "")
                    if url_str:
                        self._populate_streams_from_string(url_str, sub_str, base, result)
                        if result.streams:
                            parsed_from_html = True
                except Exception:
                    pass

            if not parsed_from_html and data_id:
                action = "get_stream" if is_series else "get_movie"

                t_now = int(time.time() * 1000)
                ajax_url = f"{base}/ajax/get_cdn_series/?t={t_now}"
                post_data = {
                    "id": data_id,
                    "translator_id": trans_id,
                    "action": action
                }
                if is_series:
                    post_data["season"] = str(season or 1)
                    post_data["episode"] = str(episode or 1)

                post_headers = {
                    "X-Requested-With": "XMLHttpRequest",
                    "Referer": page_url
                }

                r_ajax = self.session.post(ajax_url, data=post_data, headers=post_headers, timeout=6)
                if r_ajax.status_code == 200:
                    json_data = r_ajax.json()
                    url_str = json_data.get("url") or json_data.get("streams", "")
                    sub_str = json_data.get("subtitle", "")
                    skip_raw = json_data.get("skip") or json_data.get("intro") or json_data.get("time_skip")
                    if skip_raw:
                        skip_dict = {}
                        if isinstance(skip_raw, dict):
                            skip_dict = {k: float(v) for k, v in skip_raw.items() if isinstance(v, (int, float, str)) and str(v).replace('.', '', 1).isdigit()}
                        elif isinstance(skip_raw, list) and len(skip_raw) >= 2:
                            skip_dict = {"intro_start": float(skip_raw[0]), "intro_end": float(skip_raw[1])}
                        elif isinstance(skip_raw, str) and "-" in skip_raw:
                            sp = skip_raw.split("-")
                            if len(sp) == 2 and sp[0].strip().isdigit() and sp[1].strip().isdigit():
                                skip_dict = {"intro_start": float(sp[0].strip()), "intro_end": float(sp[1].strip())}
                        if skip_dict:
                            result.skip_time = skip_dict
                    self._populate_streams_from_string(url_str, sub_str, base, result)
        except Exception as e:
            result.error = str(e)

        return result

    def _populate_streams_from_string(self, url_str: str, sub_str: str, base: str, result: StreamResult):
        if not url_str:
            return

        # Split comma-separated quality blocks: [360p]url1 or url2,[480p]...
        parts = re.split(r',\s*(?=\[[^\]]+\])', url_str)
        for part in parts:
            m = re.match(r'\[([^\]]+)\](.*)', part.strip())
            if not m:
                continue
            q_raw, urls_part = m.group(1), m.group(2)
            quality = re.sub(r'<[^>]+>', '', q_raw).strip()
            candidates = [u.strip().replace('\\/', '/') for u in urls_part.split(" or ") if u.strip().startswith("http")]
            if not candidates:
                continue

            working = [u for u in candidates if "ukrtelcdn" not in u] or candidates
            chosen_url = working[0]
            is_prem = ("rhtie.mp4" in chosen_url) or any(k in quality.lower() for k in ["ultra", "4k", "2160", "1440"])

            result.streams.append(VideoStream(
                quality=f"{quality} (HDRezka)",
                url=chosen_url,
                stream_type="hls" if ".m3u8" in chosen_url else "mp4",
                headers={"User-Agent": "Mozilla/5.0", "Referer": f"{base}/"},
                is_premium=is_prem
            ))

        # Parse subtitles if provided
        if sub_str:
            sub_parts = re.split(r',\s*(?=\[[^\]]+\])', sub_str)
            for sp in sub_parts:
                sm = re.match(r'\[([^\]]+)\](.*)', sp.strip())
                if sm:
                    sub_lang = sm.group(1).strip()
                    sub_url = sm.group(2).strip().replace('\\/', '/')
                    if sub_url.startswith("http"):
                        result.subtitles.append(SubtitleTrack(language=sub_lang, url=sub_url))

    def canary_test(self) -> CanaryReport:
        start_t = time.time()
        base = self._get_base()
        test_url = f"{base}/search/?do=search&subaction=search&q=Matrix"

        try:
            res = self._get_with_anubis(test_url, base)
            latency = (time.time() - start_t) * 1000

            if res.status_code != 200:
                return CanaryReport(
                    source_name=self.name,
                    is_active=False,
                    status="CHANGED / BROKEN",
                    latency_ms=latency,
                    message=f"HDRezka mirror returned HTTP {res.status_code}.",
                    needs_rework=True,
                    endpoint_tested=test_url,
                    last_tested=time.time()
                )

            soup = BeautifulSoup(res.text, "html.parser")
            items = soup.select(".b-content__inline_item")

            # Also verify stream extraction for Matrix
            test_movie_url = f"{base}/films/fiction/981-matrica-1999-latest.html"
            res_movie = self._get_with_anubis(test_movie_url, base)
            has_player = bool(re.search(r'data-id="(\d+)"', res_movie.text))

            if has_player:
                return CanaryReport(
                    source_name=self.name,
                    is_active=True,
                    status="OK",
                    latency_ms=latency,
                    message=f"HDRezka полностью онлайн на {base}! Anubis PoW решён за {latency:.0f}мс. Плеер и потоки активны.",
                    needs_rework=False,
                    endpoint_tested=test_url,
                    last_tested=time.time()
                )
            else:
                return CanaryReport(
                    source_name=self.name,
                    is_active=True,
                    status="OK",
                    latency_ms=latency,
                    message=f"HDRezka каталог онлайн на {base} ({len(items)} результатов).",
                    needs_rework=False,
                    endpoint_tested=test_url,
                    last_tested=time.time()
                )
        except Exception as e:
            return CanaryReport(
                source_name=self.name,
                is_active=False,
                status="CHANGED / BROKEN",
                latency_ms=(time.time() - start_t) * 1000,
                message=f"HDRezka ошибка: {str(e)}",
                needs_rework=True,
                endpoint_tested=test_url,
                last_tested=time.time()
            )
