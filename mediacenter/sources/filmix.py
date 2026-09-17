"""
Filmix Portal Source Adapter & Account Authentication.
Extracted from 'Кино HD' (361cc_fix.apk) and 'LazyMedia Deluxe'.
Features live session login, cookie management, Sphinx search,
#2 video stream deobfuscation, and Canary health monitoring.
"""
import os
import re
import time
import json
import base64
import requests
from bs4 import BeautifulSoup
from typing import List, Dict, Optional, Any
from .base import BaseSource, MediaItem, StreamResult, VideoStream, AudioTrack, CanaryReport, SeasonItem, EpisodeItem, CommentItem
from ..core.mirror_manager import mirror_manager

class FilmixSource(BaseSource):
    name = "filmix"
    display_name = "Filmix"
    source_type = "portal"

    SESSION_FILE = os.path.join(os.path.dirname(__file__), "..", "data", "filmix_session.json")

    MIRRORS = [
        "https://filmix.quest",
        "https://filmix.biz",
        "https://filmix.my",
        "https://filmix.tech",
        "https://filmix.life"
    ]

    def __init__(self):
        self.session = requests.Session()
        from urllib3.util import Retry
        from requests.adapters import HTTPAdapter
        retries = Retry(total=3, backoff_factor=0.3, status_forcelist=[500, 502, 503, 504])
        adapter = HTTPAdapter(max_retries=retries)
        self.session.mount("https://", adapter)
        self.session.mount("http://", adapter)
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Accept-Language": "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
        })
        self.user_profile: Dict[str, Any] = {
            "is_logged_in": False,
            "username": "",
            "display_name": "",
            "is_pro": False,
            "is_pro_plus": False,
            "pro_date": "",
            "avatar": ""
        }
        self.load_session()

    def _get_base(self) -> str:
        return mirror_manager.get_working_mirror("filmix") or self.MIRRORS[0]

    def load_session(self):
        """Loads saved session cookies and user profile from disk."""
        try:
            if os.path.exists(self.SESSION_FILE):
                with open(self.SESSION_FILE, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    cookies = data.get("cookies", {})
                    for k, v in cookies.items():
                        self.session.cookies.set(k, v)
                    self.user_profile = data.get("user_profile", self.user_profile)
        except Exception:
            pass

    def save_session(self):
        """Saves current session cookies and profile to disk."""
        try:
            os.makedirs(os.path.dirname(self.SESSION_FILE), exist_ok=True)
            data = {
                "cookies": self.session.cookies.get_dict(),
                "user_profile": self.user_profile,
                "updated_at": time.time()
            }
            with open(self.SESSION_FILE, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
        except Exception:
            pass

    def login(self, login_name: str, login_password: str) -> Dict[str, Any]:
        """
        Authenticates against Filmix via /engine/ajax/user_auth.php.
        Matches the exact protocol extracted from Кино HD (AccountSettings$O0O).
        """
        base = self._get_base()
        try:
            # 1. Establish session cookies
            self.session.get(f"{base}/", timeout=6)

            # 2. Submit credentials
            url = f"{base}/engine/ajax/user_auth.php"
            payload = {
                "login_name": login_name,
                "login_password": login_password,
                "login": "submit",
                "login_not_save": "yes"
            }
            headers = {
                "X-Requested-With": "XMLHttpRequest",
                "Referer": f"{base}/",
                "Origin": base,
                "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"
            }
            res = self.session.post(url, data=payload, headers=headers, timeout=8)
            response_text = res.text.strip()

            if response_text == "AUTHORIZED":
                # Verify and load profile
                profile = self.refresh_profile()
                self.save_session()
                return {"success": True, "message": "Успешная авторизация!", "profile": profile}
            elif response_text == "GA_CODE":
                return {"success": False, "message": "Требуется код двухфакторной аутентификации (2FA)."}
            else:
                msg = response_text if response_text else "Неверный логин или пароль."
                return {"success": False, "message": msg}
        except Exception as e:
            return {"success": False, "message": f"Ошибка соединения: {str(e)}"}

    def set_cookies(self, cookie_dict: Dict[str, str]) -> Dict[str, Any]:
        """Manually sets session cookies (e.g. dle_user_id, dle_password, dle_hash)."""
        for k, v in cookie_dict.items():
            self.session.cookies.set(k, v)
        profile = self.refresh_profile()
        self.save_session()
        return {"success": True, "profile": profile}

    def logout(self):
        """Clears current user session."""
        self.session.cookies.clear()
        self.user_profile = {
            "is_logged_in": False,
            "username": "",
            "display_name": "",
            "is_pro": False,
            "is_pro_plus": False,
            "pro_date": "",
            "avatar": ""
        }
        if os.path.exists(self.SESSION_FILE):
            try:
                os.remove(self.SESSION_FILE)
            except Exception:
                pass

    def refresh_profile(self) -> Dict[str, Any]:
        """Fetches current user profile from Filmix portal."""
        base = self._get_base()
        try:
            r = self.session.get(f"{base}/", timeout=6)
            # Check user_data in HTML
            m_user = re.search(r'var\s+user_data\s*=\s*({.*?});', r.text)
            m_uid = re.search(r'var\s+dle_user_id\s*=\s*(\d+);', r.text)
            m_uname = re.search(r'var\s+dle_user_name\s*=\s*[\'"]([^\'"]*)[\'"];', r.text)

            uid = int(m_uid.group(1)) if m_uid else 0
            uname = m_uname.group(1) if m_uname else ""

            if uid > 0 and uname:
                is_pro = False
                is_pro_plus = False
                if m_user:
                    try:
                        u_info = json.loads(m_user.group(1))
                        is_pro = bool(u_info.get("is_user_pro", 0))
                        is_pro_plus = bool(u_info.get("is_user_pro_plus", 0))
                    except Exception:
                        pass

                self.user_profile = {
                    "is_logged_in": True,
                    "username": uname,
                    "display_name": uname,
                    "is_pro": is_pro,
                    "is_pro_plus": is_pro_plus,
                    "pro_date": "Активна" if (is_pro or is_pro_plus) else "",
                    "avatar": ""
                }
            else:
                self.user_profile["is_logged_in"] = False
        except Exception:
            pass
        return self.user_profile

    def search(self, query: str, year: Optional[int] = None, kp_id: Optional[str] = None) -> List[MediaItem]:
        """
        Searches Filmix catalog via sphinx_search.php.
        Parses results into standard MediaItem models.
        """
        base = self._get_base()
        url = f"{base}/engine/ajax/sphinx_search.php"
        headers = {
            "X-Requested-With": "XMLHttpRequest",
            "Referer": f"{base}/",
            "Origin": base
        }
        items: List[MediaItem] = []
        try:
            res = self.session.post(url, data={"story": query}, headers=headers, timeout=6)
            if res.status_code != 200 or not res.text:
                return []

            articles = res.text.split("</article>")
            for art in articles:
                m_id = re.search(r'data-id=[\x22\x27](\d+)[\x22\x27]', art)
                if not m_id:
                    continue
                post_id = m_id.group(1)

                m_title = re.search(r'<h2[^>]*class=[\x22\x27]name[\x22\x27][^>]*itemprop=[\x22\x27]name[\x22\x27][^>]*content=[\x22\x27]([^\x22\x27]+)[\x22\x27]', art)
                title = m_title.group(1).strip() if m_title else ""
                if not title:
                    m_title2 = re.search(r'<h2[^>]*class=[\x22\x27]name[\x22\x27][^>]*>(.*?)</h2>', art, re.DOTALL)
                    if m_title2:
                        title = re.sub(r'<[^<]+?>', '', m_title2.group(1)).strip()

                if not title:
                    continue

                m_orig = re.search(r'itemprop=[\x22\x27]alternativeHeadline[\x22\x27][^>]*content=[\x22\x27]([^\x22\x27]+)[\x22\x27]', art)
                orig_title = m_orig.group(1).strip() if m_orig else None

                m_poster = re.search(r'<img[^>]*src=[\x22\x27](https?://[^\x22\x27]+)[\x22\x27]', art)
                poster = m_poster.group(1) if m_poster else None

                m_url = re.search(r'<a[^>]*href=[\x22\x27](https?://[^\x22\x27]+\.html)[\x22\x27]', art)
                page_url = m_url.group(1) if m_url else None

                item_year = None
                m_year = re.search(r'alt=[\x22\x27].*?(\d{4})[\x22\x27]', art)
                if m_year:
                    try:
                        item_year = int(m_year.group(1))
                    except Exception:
                        pass
                elif page_url:
                    m_year_url = re.search(r'-(\d{4})\.html', page_url)
                    if m_year_url:
                        try:
                            item_year = int(m_year_url.group(1))
                        except Exception:
                            pass

                is_series = "serial" in art.lower() or "/serial" in (page_url or "").lower()

                items.append(MediaItem(
                    id=post_id,
                    source_name=self.name,
                    title=title,
                    original_title=orig_title,
                    year=item_year,
                    is_series=is_series,
                    poster=poster,
                    extra_data={"page_url": page_url} if page_url else {}
                ))
        except Exception:
            pass

        # Prioritize exact title matches and matching years
        q_clean = query.strip().lower()
        items.sort(key=lambda it: (
            0 if it.title.strip().lower() == q_clean else (
                1 if (year and it.year == year) else (
                    2 if q_clean in it.title.strip().lower() else 3
                )
            )
        ))

        return items

    def get_catalog(self, category: str = "all", genre: Optional[str] = None, page: int = 1) -> List[MediaItem]:
        """
        Fetches fresh releases from Filmix catalog.
        Works for all users without requiring authentication.
        """
        base = self._get_base()
        path = "/films/"
        if category in ["series", "serials"]:
            path = "/serials/"
        elif category in ["cartoons", "animation"]:
            path = "/multfilmy/"
        elif category == "anime":
            path = "/anime/"

        if page > 1:
            url = f"{base}{path}page/{page}/"
        else:
            url = f"{base}{path}"

        items: List[MediaItem] = []
        try:
            r = self.session.get(url, timeout=6)
            if r.status_code != 200:
                return []

            soup = BeautifulSoup(r.text, "html.parser")
            articles = soup.select("article.shortstory, article.item, article.post")
            for art in articles:
                data_id = art.get("data-id")
                if not data_id:
                    continue

                name_el = art.select_one(".name a") or art.select_one(".name")
                title = name_el.text.strip() if name_el else ""
                if not title:
                    continue

                poster_el = art.select_one("img.poster") or art.select_one(".poster img") or art.select_one("img")
                poster = poster_el.get("src") if poster_el else None
                if poster and poster.startswith("/"):
                    poster = f"{base}{poster}"

                # Extract year
                item_year = None
                alt_txt = poster_el.get("alt", "") if poster_el else ""
                y_m = re.search(r'\b(19\d\d|20\d\d)\b', alt_txt)
                if y_m:
                    try:
                        item_year = int(y_m.group(1))
                    except ValueError:
                        pass
                if not item_year:
                    link_href = name_el.get("href", "") if name_el else ""
                    y_link = re.search(r'-(\d{4})\.html', link_href)
                    if y_link:
                        try:
                            item_year = int(y_link.group(1))
                        except ValueError:
                            pass

                # Genres
                genres = []
                for g_a in art.select(".category a, .genre a, [itemprop='genre']"):
                    g_text = g_a.text.strip()
                    if g_text and g_text not in genres:
                        genres.append(g_text)

                # Quality
                qual_el = art.select_one(".quality")
                quality = qual_el.text.strip() if qual_el else None

                # Is series
                link_href = name_el.get("href", "") if name_el else ""
                is_ser = ("/serials/" in link_href) or ("/serial" in link_href) or ("сезон" in art.text.lower()) or (category in ["series", "serials"])

                items.append(MediaItem(
                    id=str(data_id),
                    source_name=self.name,
                    title=title,
                    year=item_year,
                    is_series=is_ser,
                    poster=poster,
                    extra_data={
                        "genre": ", ".join(genres),
                        "quality": quality,
                        "page_url": link_href if link_href.startswith("http") else f"{base}{link_href}"
                    }
                ))
        except Exception:
            pass

        return items

    def _decode_stream_string(self, raw: str) -> str:
        """
        Deobfuscates Filmix #2 video link strings.
        Disassembled and reverse-engineered from Filmix Playerjs logic.
        Algorithm:
        1. Strips '#2' prefix.
        2. Splits by ':':<:' separator.
        3. Strips 24 dummy junk characters from the beginning of each subsequent segment.
        4. Reconstructs base64 string and decodes to UTF-8.
        """
        if not raw or not raw.startswith("#2"):
            return raw

        s = raw[2:]
        parts = s.split(":<:")
        b64 = parts[0]
        for p in parts[1:]:
            b64 += p[24:]

        b64 = b64.strip().rstrip("=")
        pad = len(b64) % 4
        if pad == 2:
            b64 += "=="
        elif pad == 3:
            b64 += "="
        elif pad == 1:
            b64 = b64[:-1]

        try:
            return base64.b64decode(b64).decode("utf-8", errors="replace")
        except Exception:
            return raw

    def get_streams(self, media_id: str, season: Optional[int] = None, episode: Optional[int] = None, audio_id: Optional[str] = None) -> StreamResult:
        """
        Extracts playable HLS / MP4 video streams for a movie or episode.
        Calls /api/movies/player-data with active user session.
        """
        base = self._get_base()
        post_id = media_id

        # 1. Establish session cookies (minotaurs is set by homepage GET)
        if "minotaurs" not in self.session.cookies:
            try:
                self.session.get(f"{base}/", timeout=6)
            except Exception:
                pass

        # Set required verification cookies extracted from Filmix reverse-engineering
        minotaurs = self.session.cookies.get("minotaurs")
        if minotaurs:
            self.session.cookies.set("alora", minotaurs)
        self.session.cookies.set("ishimura", "4814fae4fd4df74d11c48ceb23c2693c1c55eeef")

        # 2. Request player data
        player_url = f"{base}/api/movies/player-data?t={int(time.time() * 1000)}"
        headers = {
            "X-Requested-With": "XMLHttpRequest",
            "Referer": f"{base}/",
            "Origin": base,
            "Accept": "application/json, text/javascript, */*; q=0.01"
        }
        payload = {
            "post_id": str(post_id),
            "showfull": "true"
        }

        try:
            res = self.session.post(player_url, data=payload, headers=headers, timeout=8)
            if res.status_code != 200:
                return StreamResult(
                    source_name=self.name,
                    media_id=media_id,
                    title="Filmix Stream",
                    error=f"Filmix returned HTTP {res.status_code}"
                )

            data = res.json()
            msg = data.get("message", {})
            translations = msg.get("translations", {})
            video_data = translations.get("video", {})

            # Check if movie is blocked (locked video 'lv')
            if translations.get("lv"):
                is_logged = self.user_profile.get("is_logged_in")
                if not is_logged and (not isinstance(video_data, dict) or not video_data):
                    return StreamResult(
                        source_name=self.name,
                        media_id=media_id,
                        title="Filmix Stream",
                        error="Видео заблокировано для гостей (lv). Войдите в аккаунт Filmix с активной подпиской PRO+ в Настройках."
                    )

            if not isinstance(video_data, dict) or not video_data:
                return StreamResult(
                    source_name=self.name,
                    media_id=media_id,
                    title="Filmix Stream",
                    error="Видеопотоки не найдены или требуется авторизация."
                )

            all_streams: List[VideoStream] = []
            audio_tracks: List[AudioTrack] = []
            seasons_list: List[SeasonItem] = []
            is_playlist = (translations.get("pl") == "yes")

            for track_idx, (track_name, raw_stream) in enumerate(video_data.items()):
                audio_tracks.append(AudioTrack(
                    id=str(track_idx),
                    name=track_name,
                    is_default=(track_idx == 0)
                ))

                # Filter by selected audio if requested
                if audio_id is not None and str(track_idx) != str(audio_id):
                    continue

                if is_playlist:
                    try:
                        decoded_url = self._decode_stream_string(raw_stream)
                        r_pl = self.session.get(decoded_url, headers={"Referer": f"{base}/"}, timeout=6)
                        if r_pl.status_code == 200:
                            raw_pl_text = BeautifulSoup(r_pl.text, "html.parser").text.strip()
                            pl_json_str = self._decode_stream_string(raw_pl_text)
                            end_idx = pl_json_str.rfind("]")
                            if end_idx != -1:
                                pl_data = json.loads(pl_json_str[:end_idx + 1])
                                if not seasons_list:
                                    for s_idx, s_entry in enumerate(pl_data, start=1):
                                        s_title = s_entry.get("title", f"Сезон {s_idx}").strip()
                                        ep_list = []
                                        for e_idx, e_entry in enumerate(s_entry.get("folder", []), start=1):
                                            e_title = e_entry.get("title", f"Серия {e_idx}").strip()
                                            ep_list.append(EpisodeItem(episode_id=e_idx, title=e_title, season_id=s_idx))
                                        seasons_list.append(SeasonItem(season_id=s_idx, title=s_title, episodes=ep_list))

                                target_s = (season or 1) - 1
                                target_e = (episode or 1) - 1
                                if 0 <= target_s < len(pl_data):
                                    folder = pl_data[target_s].get("folder", [])
                                    if 0 <= target_e < len(folder):
                                        target_file = folder[target_e].get("file", "")
                                        found = re.findall(r'\[(.*?)\](https?://[^\s,\[\]]+)', target_file)
                                        for qual, url in found:
                                            stream_type = "hls" if ".m3u8" in url else "mp4"
                                            all_streams.append(VideoStream(
                                                quality=f"{qual} ({track_name})",
                                                url=url,
                                                stream_type=stream_type,
                                                headers={"User-Agent": "Mozilla/5.0", "Referer": f"{base}/"}
                                            ))
                    except Exception:
                        pass
                else:
                    decoded = self._decode_stream_string(raw_stream)
                    found = re.findall(r'\[(.*?)\](https?://[^\s,\[\]]+)', decoded)

                    for qual, url in found:
                        stream_type = "hls" if ".m3u8" in url else "mp4"
                        all_streams.append(VideoStream(
                            quality=f"{qual} ({track_name})",
                            url=url,
                            stream_type=stream_type,
                            headers={"User-Agent": "Mozilla/5.0", "Referer": f"{base}/"}
                        ))

            return StreamResult(
                source_name=self.name,
                media_id=media_id,
                title="Filmix Direct Stream",
                streams=all_streams,
                audio_tracks=audio_tracks,
                seasons=seasons_list
            )
        except Exception as e:
            return StreamResult(
                source_name=self.name,
                media_id=media_id,
                title="Filmix Stream",
                error=f"Filmix stream error: {str(e)}"
            )

    def get_comments(self, media_id: str, title: Optional[str] = None) -> List[CommentItem]:
        comments: List[CommentItem] = []
        base = self._get_base()
        try:
            page_url = None
            if title:
                items = self.search(title)
                if items:
                    page_url = items[0].extra_data.get("page_url")
            if not page_url and media_id.isdigit():
                page_url = f"{base}/film/{media_id}"

            if not page_url:
                return []

            r = self.session.get(page_url, timeout=6)
            if r.status_code == 200:
                soup = BeautifulSoup(r.text, "html.parser")
                for c in soup.select(".comment"):
                    author = c.select_one(".comment-name, .author, strong")
                    date = c.select_one(".comment-date, .date, time")
                    text = c.select_one(".comment-text, .comm-text, .text")
                    rating_el = c.select_one(".comment-rating")
                    
                    if text:
                        clean_text = text.text.strip().replace("\r", " ").replace("\n", " ")
                        clean_text = re.sub(r'\s+', ' ', clean_text)
                        if clean_text:
                            comments.append(CommentItem(
                                author=author.text.strip() if author else "Зритель",
                                date=date.text.strip() if date else "",
                                text=clean_text,
                                rating=rating_el.text.strip() if rating_el else None
                            ))
        except Exception:
            pass
        return comments

    def canary_test(self) -> CanaryReport:
        """
        Runs automated Canary health check:
        1. Probes live Sphinx search endpoint with test query ('Матрица').
        2. Validates latency and response parsing.
        3. Reports account status (PRO+ active, guest mode, or auth failure).
        """
        start_t = time.time()
        last_err = ""
        for base in self.MIRRORS:
            url = f"{base}/engine/ajax/sphinx_search.php"
            try:
                res = self.session.post(
                    url,
                    data={"story": "Матрица"},
                    headers={"X-Requested-With": "XMLHttpRequest", "Origin": base},
                    timeout=5
                )
                latency = (time.time() - start_t) * 1000

                if res.status_code == 200 and ("data-id=" in res.text or "shortstory" in res.text):
                    is_logged = self.user_profile.get("is_logged_in")
                    uname = self.user_profile.get("username", "")
                    is_pro_plus = self.user_profile.get("is_pro_plus")
                    is_pro = self.user_profile.get("is_pro")

                    if is_logged:
                        badge = "PRO+" if is_pro_plus else ("PRO" if is_pro else "FREE")
                        msg = f"Filmix API online ({base}). Авторизован: {uname} [{badge}]"
                    else:
                        msg = f"Filmix API online ({base}) (Гостевой режим; для 1080p/4K войдите в аккаунт Filmix)"

                    return CanaryReport(
                        source_name=self.name,
                        is_active=True,
                        status="OK",
                        latency_ms=latency,
                        message=msg,
                        needs_rework=False,
                        endpoint_tested=url,
                        last_tested=time.time()
                    )
            except Exception as e:
                last_err = str(e)

        return CanaryReport(
            source_name=self.name,
            is_active=False,
            status="CHANGED / BROKEN",
            latency_ms=(time.time() - start_t) * 1000,
            message=f"Filmix connection error: {last_err}",
            needs_rework=True,
            endpoint_tested=self.MIRRORS[0],
            last_tested=time.time()
        )
