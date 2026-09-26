"""
Filmix Source Adapter using official Filmix Partner API.
Reverse-engineered from LazyMedia Deluxe (qi.java, f71.java, r71.java).
Provides search, full film details, direct HLS/MP4 streams (up to 720p with free account, 1080p/4K with PRO).
"""
import os
import re
import time
import json
import hashlib
import urllib.parse
from typing import List, Dict, Optional, Any
import requests

from .base import (
    BaseSource, MediaItem, StreamResult, VideoStream, AudioTrack,
    CanaryReport, SeasonItem, EpisodeItem, CommentItem
)


class FilmixSource(BaseSource):
    name = "filmix"
    display_name = "Filmix"
    source_type = "portal"

    PARTNER_API = "http://5.61.56.18/partner_api"
    PARTNER_KEY = "38a8432b32821eab153d7ffe9a6d88a0"
    PARTNER_SECRET1 = "RrHeXvfBkRs8wPkeE7cgeJ9AYD42E6wseg4L7E3f4va6w6W8"
    PARTNER_SECRET2 = "e0f133fb6bf87d6c12685cc7c317cec4"

    SESSION_FILE = os.path.join(os.path.dirname(__file__), "..", "data", "filmix_session.json")

    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        })
        self.login_name: str = ""
        self.login_password: str = ""
        self.cached_jwt: str = ""
        self.token_expired: float = 0
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

    def _get_external_ip(self) -> str:
        """Fetches client's external IPv4 address for hardware signature."""
        for u in ["https://api.ipify.org?format=json", "http://ip-api.com/json?fields=query"]:
            try:
                r = requests.get(u, timeout=3)
                if r.status_code == 200:
                    data = r.json()
                    ip = data.get("ip") or data.get("query")
                    if ip and re.match(r'^\d+\.\d+\.\d+\.\d+$', ip):
                        return ip
            except Exception:
                pass
        return "127.0.0.1"

    def _generate_hw_token(self, ip: str) -> str:
        """
        Generates hardware token signature matching LMD algorithm (f71.OooO0o0 + r71.OooO00o):
        1. sum = sum of IP octets
        2. h_md5 = md5(str(sum) + PARTNER_SECRET2)
        3. token = sha1(PARTNER_SECRET1 + PARTNER_KEY + h_md5)
        """
        try:
            octets = [int(p) for p in ip.split('.')]
            s = sum(octets)
        except Exception:
            s = 0
        h_md5 = hashlib.md5(f"{s}{self.PARTNER_SECRET2}".encode("utf-8")).hexdigest()
        return hashlib.sha1(f"{self.PARTNER_SECRET1}{self.PARTNER_KEY}{h_md5}".encode("utf-8")).hexdigest()

    def _get_jwt_token(self, force_refresh: bool = False) -> str:
        """Returns valid JWT token for Partner API. Caches until expiration."""
        now = time.time()
        if not force_refresh and self.cached_jwt and self.token_expired > now + 60:
            return self.cached_jwt

        ip = self._get_external_ip()
        hw_token = self._generate_hw_token(ip)
        url = f"{self.PARTNER_API}/request-token"
        payload = {
            "user_name": self.login_name or "",
            "user_passw": self.login_password or "",
            "key": self.PARTNER_KEY,
            "token": hw_token
        }

        try:
            r = self.session.post(url, data=payload, timeout=8)
            if r.status_code == 200:
                data = r.json()
                self.cached_jwt = data.get("token", "")
                self.token_expired = float(data.get("expired", now + 3600))
                self.save_session()
                return self.cached_jwt
        except Exception as e:
            print(f"[Filmix] Failed to get JWT token: {e}")

        return self.cached_jwt or ""

    def load_session(self):
        """Loads saved session and credentials from disk."""
        try:
            if os.path.exists(self.SESSION_FILE):
                with open(self.SESSION_FILE, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    self.login_name = data.get("login_name", "")
                    self.login_password = data.get("login_password", "")
                    self.cached_jwt = data.get("jwt_token", "")
                    self.token_expired = data.get("token_expired", 0)
                    self.user_profile = data.get("user_profile", self.user_profile)
        except Exception:
            pass

    def save_session(self):
        """Saves session credentials and JWT token to disk."""
        try:
            os.makedirs(os.path.dirname(self.SESSION_FILE), exist_ok=True)
            data = {
                "login_name": self.login_name,
                "login_password": self.login_password,
                "jwt_token": self.cached_jwt,
                "token_expired": self.token_expired,
                "user_profile": self.user_profile,
                "updated_at": time.time()
            }
            with open(self.SESSION_FILE, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
        except Exception:
            pass

    def login(self, login_name: str, login_password: str) -> Dict[str, Any]:
        """
        Authenticates user account via Filmix Partner API.
        With a free registered account, streams up to 720p become accessible.
        With PRO/PRO+, 1080p and 4K streams are unlocked.
        """
        self.login_name = login_name.strip()
        self.login_password = login_password.strip()

        token = self._get_jwt_token(force_refresh=True)
        if not token:
            return {"success": False, "message": "Не удалось подключиться к серверу авторизации Filmix."}

        # Check profile
        profile = self.refresh_profile()
        if profile.get("is_logged_in"):
            self.save_session()
            badge = "PRO+" if profile.get("is_pro_plus") else ("PRO" if profile.get("is_pro") else "Базовый (720p)")
            return {
                "success": True,
                "message": f"Успешная авторизация в Filmix! Тариф: {badge}",
                "profile": profile
            }
        else:
            self.login_name = ""
            self.login_password = ""
            self.save_session()
            return {"success": False, "message": "Неверный логин или пароль Filmix."}

    def set_cookies(self, cookie_dict: Dict[str, str]) -> Dict[str, Any]:
        """Legacy helper for session compatibility."""
        return {"success": True, "profile": self.user_profile}

    def logout(self):
        """Logs out and resets account to guest mode."""
        self.login_name = ""
        self.login_password = ""
        self.cached_jwt = ""
        self.token_expired = 0
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
        # Request a new guest token
        self._get_jwt_token(force_refresh=True)

    def refresh_profile(self) -> Dict[str, Any]:
        """Checks user profile and subscription status via Partner API."""
        token = self._get_jwt_token()
        if not token:
            self.user_profile["is_logged_in"] = False
            return self.user_profile

        try:
            r = self.session.get(
                f"{self.PARTNER_API}/profile/login",
                headers={"X-FX-Token": token},
                timeout=6
            )
            if r.status_code == 200:
                data = r.json()
                if data.get("status") == "ok":
                    uname = data.get("user_name") or self.login_name
                    is_pro = bool(data.get("is_pro", False))
                    is_pro_plus = bool(data.get("is_pro_plus", False))
                    pro_date = str(data.get("pro_date", ""))

                    self.user_profile = {
                        "is_logged_in": True,
                        "username": uname,
                        "display_name": uname,
                        "is_pro": is_pro,
                        "is_pro_plus": is_pro_plus,
                        "pro_date": pro_date if (is_pro or is_pro_plus) else "Бесплатный аккаунт (до 720p)",
                        "avatar": ""
                    }
                    self.save_session()
                    return self.user_profile
        except Exception:
            pass

        self.user_profile["is_logged_in"] = False
        return self.user_profile

    def search(self, query: str, year: Optional[int] = None, kp_id: Optional[str] = None) -> List[MediaItem]:
        """
        Searches Filmix catalog via Partner API list endpoint.
        If kp_id is provided, verifies top results with /film/{id}/details for 100% precision.
        """
        token = self._get_jwt_token()
        if not token:
            return []

        clean_q = re.sub(r'[\(\)\[\]\{\}]', ' ', query).strip()
        url = f"{self.PARTNER_API}/list?search={urllib.parse.quote(clean_q)}"

        items: List[MediaItem] = []
        try:
            r = self.session.get(url, headers={"X-FX-Token": token}, timeout=6)
            if r.status_code != 200:
                return []

            data = r.json()
            raw_items = data.get("items", [])

            for it in raw_items:
                film_id = str(it.get("id", ""))
                if not film_id:
                    continue

                cat = str(it.get("category", "")).lower()
                # Skip news, trailers and collections (s87)
                if cat == "s87":
                    continue

                title = (it.get("title") or "").replace(r"\'", "'").strip()
                orig_title = (it.get("original_title") or "").replace(r"\'", "'").strip() or None
                item_year = it.get("year")
                if isinstance(item_year, int) and item_year <= 0:
                    item_year = None

                # Year tolerance check
                if year and item_year and abs(item_year - year) > 1:
                    continue

                # Series detection: category s7, or has last_episode / max_episode, or has 'Сериалы' in genres
                raw_genres = it.get("genres", [])
                genre_names = [g.get("name") for g in raw_genres if isinstance(g, dict) and g.get("name")]
                is_ser = (
                    cat == "s7" or
                    "last_episode" in it or
                    "max_episode" in it or
                    "Сериалы" in genre_names or
                    "сериал" in title.lower()
                )

                poster = it.get("poster")
                rating_kp = it.get("ratingKinopoisk")
                if rating_kp is not None:
                    try:
                        rating_kp = float(rating_kp)
                    except (ValueError, TypeError):
                        rating_kp = None

                rating_imdb = it.get("ratingImdb")
                if rating_imdb is not None:
                    try:
                        rating_imdb = float(rating_imdb)
                    except (ValueError, TypeError):
                        rating_imdb = None

                items.append(MediaItem(
                    id=film_id,
                    source_name=self.name,
                    title=title,
                    original_title=orig_title,
                    year=item_year,
                    is_series=is_ser,
                    poster=poster,
                    genres=genre_names,
                    rating_kp=rating_kp,
                    rating_imdb=rating_imdb,
                    extra_data={
                        "original_title": orig_title,
                        "category": cat,
                        "quality": it.get("quality")
                    }
                ))

            # If Kinopoisk ID specified, match against film details
            if kp_id and str(kp_id).isdigit() and items:
                kp_target = int(kp_id)
                for item in items[:6]:
                    try:
                        d_res = self.session.get(
                            f"{self.PARTNER_API}/film/{item.id}/details",
                            headers={"X-FX-Token": token},
                            timeout=3
                        )
                        if d_res.status_code == 200:
                            det = d_res.json()
                            if det.get("idKinopoisk") == kp_target:
                                item.kinopoisk_id = str(kp_target)
                                # Prioritize matched item
                                items.remove(item)
                                items.insert(0, item)
                                break
                    except Exception:
                        pass

        except Exception as e:
            print(f"[Filmix] Search error: {e}")

        return items

    def get_streams(
        self,
        media_id: str,
        season: Optional[int] = None,
        episode: Optional[int] = None,
        audio_id: Optional[str] = None
    ) -> StreamResult:
        """
        Extracts playable HLS / MP4 video streams for movie or episode.
        Calls /partner_api/video_links/{media_id} with X-FX-Token.
        Returns translations and resolutions (480p, 720p, 1080p, 4K).
        """
        token = self._get_jwt_token()
        if not token:
            return StreamResult(
                source_name=self.name,
                media_id=media_id,
                title="Filmix",
                error="Не удалось получить авторизационный токен Filmix"
            )

        url = f"{self.PARTNER_API}/video_links/{media_id}"
        try:
            r = self.session.get(url, headers={"X-FX-Token": token}, timeout=8)
            if r.status_code != 200:
                return StreamResult(
                    source_name=self.name,
                    media_id=media_id,
                    title="Filmix",
                    error=f"Filmix API вернул код {r.status_code}"
                )

            data = r.json()
            if not isinstance(data, list) or len(data) == 0:
                is_logged = self.user_profile.get("is_logged_in", False)
                err_msg = (
                    "Видео доступно только для авторизованных пользователей Filmix (войдите в настройках ShowHub)."
                    if not is_logged
                    else "Для данного фильма нет доступных видеопотоков."
                )
                return StreamResult(
                    source_name=self.name,
                    media_id=media_id,
                    title="Filmix",
                    error=err_msg
                )

            audio_tracks: List[AudioTrack] = []
            all_streams: List[VideoStream] = []
            seasons_list: List[SeasonItem] = []
            is_pro = self.user_profile.get("is_pro", False) or self.user_profile.get("is_pro_plus", False)

            # Iterate translation studios
            for tr_idx, studio in enumerate(data):
                studio_name = studio.get("name") or f"Озвучка {tr_idx + 1}"
                track_id = str(tr_idx + 1)
                audio_tracks.append(AudioTrack(id=track_id, name=studio_name, language="rus"))

                # Filter by audio track if requested
                if audio_id and audio_id != track_id:
                    continue

                # Check if series or movie
                if "seasons" in studio and studio.get("seasons"):
                    seasons_raw = studio.get("seasons", [])
                    if not seasons_list:
                        for s_entry in seasons_raw:
                            s_num = int(s_entry.get("season", 1))
                            eps_raw = s_entry.get("episodes", {})
                            ep_items = []

                            # episodes can be dict or list
                            if isinstance(eps_raw, dict):
                                ep_iter = eps_raw.values()
                            elif isinstance(eps_raw, list):
                                ep_iter = eps_raw
                            else:
                                ep_iter = []

                            for e_entry in ep_iter:
                                ep_num = int(e_entry.get("episode", 1))
                                ep_title = e_entry.get("title") or f"Серия {ep_num}"
                                ep_items.append(EpisodeItem(episode_id=ep_num, title=ep_title, season_id=s_num))

                            seasons_list.append(SeasonItem(season_id=s_num, title=f"Сезон {s_num}", episodes=ep_items))

                    # Find target episode
                    req_s = season or 1
                    req_e = episode or 1
                    for s_entry in seasons_raw:
                        if int(s_entry.get("season", 1)) == req_s:
                            eps_raw = s_entry.get("episodes", {})
                            ep_iter = eps_raw.values() if isinstance(eps_raw, dict) else (eps_raw if isinstance(eps_raw, list) else [])
                            for e_entry in ep_iter:
                                if int(e_entry.get("episode", 1)) == req_e:
                                    files = e_entry.get("files", [])
                                    for f in files:
                                        f_url = f.get("url", "")
                                        q_val = f.get("quality", 720)
                                        if f_url:
                                            st = "hls" if ".m3u8" in f_url else "mp4"
                                            is_prem = (q_val >= 1080 and not is_pro)
                                            all_streams.append(VideoStream(
                                                quality=f"{q_val}p ({studio_name})",
                                                url=f_url,
                                                stream_type=st,
                                                headers={"User-Agent": "Mozilla/5.0"},
                                                is_premium=is_prem
                                            ))
                            break
                elif "files" in studio and studio.get("files"):
                    # Regular movie
                    files = studio.get("files", [])
                    for f in files:
                        f_url = f.get("url", "")
                        q_val = f.get("quality", 720)
                        if f_url:
                            st = "hls" if ".m3u8" in f_url else "mp4"
                            is_prem = (q_val >= 1080 and not is_pro)
                            all_streams.append(VideoStream(
                                quality=f"{q_val}p ({studio_name})",
                                url=f_url,
                                stream_type=st,
                                headers={"User-Agent": "Mozilla/5.0"},
                                is_premium=is_prem
                            ))

            # If filtered by audio_id yielded no streams, fallback to all studios
            if not all_streams and audio_id:
                return self.get_streams(media_id, season=season, episode=episode, audio_id=None)

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
                title="Filmix",
                error=f"Filmix stream exception: {str(e)}"
            )

    def get_catalog(self, category: str, genre: Optional[str] = None, page: int = 1) -> List[MediaItem]:
        """Fetches catalog feed via Partner API."""
        token = self._get_jwt_token()
        if not token:
            return []

        url = f"{self.PARTNER_API}/list?page={page}"
        try:
            r = self.session.get(url, headers={"X-FX-Token": token}, timeout=6)
            if r.status_code == 200:
                raw_items = r.json().get("items", [])
                items = []
                for it in raw_items:
                    cat = str(it.get("category", "")).lower()
                    if cat == "s87":
                        continue
                    genre_names = [g.get("name") for g in it.get("genres", []) if isinstance(g, dict) and g.get("name")]
                    is_ser = cat == "s7" or "Сериалы" in genre_names or "last_episode" in it
                    items.append(MediaItem(
                        id=str(it.get("id")),
                        source_name=self.name,
                        title=(it.get("title") or "").replace(r"\'", "'").strip(),
                        original_title=(it.get("original_title") or "").replace(r"\'", "'").strip() or None,
                        year=it.get("year"),
                        is_series=is_ser,
                        poster=it.get("poster"),
                        genres=genre_names,
                        rating_kp=float(it["ratingKinopoisk"]) if it.get("ratingKinopoisk") is not None else None,
                        rating_imdb=float(it["ratingImdb"]) if it.get("ratingImdb") is not None else None
                    ))
                return items
        except Exception:
            pass
        return []

    def get_comments(self, media_id: str, title: Optional[str] = None) -> List[CommentItem]:
        """Viewer reviews and comments."""
        return []

    def canary_test(self) -> CanaryReport:
        """
        Canary health monitoring:
        Pings Partner API with test search query and validates response latency.
        """
        start_t = time.time()
        try:
            token = self._get_jwt_token()
            if not token:
                return CanaryReport(
                    source_name=self.name,
                    is_active=False,
                    status="BROKEN",
                    latency_ms=(time.time() - start_t) * 1000,
                    message="Filmix Partner API: не удалось получить JWT токен",
                    needs_rework=True,
                    endpoint_tested=f"{self.PARTNER_API}/request-token",
                    last_tested=time.time()
                )

            test_url = f"{self.PARTNER_API}/list?search=matrix"
            r = self.session.get(test_url, headers={"X-FX-Token": token}, timeout=5)
            latency = (time.time() - start_t) * 1000

            if r.status_code == 200 and "items" in r.text:
                is_logged = self.user_profile.get("is_logged_in", False)
                uname = self.user_profile.get("username", "")
                is_pro = self.user_profile.get("is_pro", False)
                is_pro_plus = self.user_profile.get("is_pro_plus", False)

                if is_logged:
                    badge = "PRO+" if is_pro_plus else ("PRO" if is_pro else "Базовый (до 720p)")
                    msg = f"Filmix Partner API online. Авторизован: {uname} [{badge}]"
                else:
                    msg = "Filmix Partner API online (Гостевой режим; войдите в аккаунт Filmix для стримов до 720p/1080p)"

                return CanaryReport(
                    source_name=self.name,
                    is_active=True,
                    status="OK",
                    latency_ms=latency,
                    message=msg,
                    needs_rework=False,
                    endpoint_tested=test_url,
                    last_tested=time.time()
                )
            else:
                return CanaryReport(
                    source_name=self.name,
                    is_active=False,
                    status=f"HTTP {r.status_code}",
                    latency_ms=latency,
                    message=f"Filmix API error: HTTP {r.status_code}",
                    needs_rework=True,
                    endpoint_tested=test_url,
                    last_tested=time.time()
                )
        except Exception as e:
            return CanaryReport(
                source_name=self.name,
                is_active=False,
                status="ERROR",
                latency_ms=(time.time() - start_t) * 1000,
                message=f"Filmix connection error: {str(e)}",
                needs_rework=True,
                endpoint_tested=self.PARTNER_API,
                last_tested=time.time()
            )
