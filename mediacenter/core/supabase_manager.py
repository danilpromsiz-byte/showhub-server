"""
ShowHub Supabase Cloud Database Manager.
Provides high-performance cloud persistence and cross-deployment sync via Supabase (PostgreSQL / PostgREST).
Features:
- Native HTTP PostgREST calls (no binary drivers required).
- Batch upserts with automatic deduplication ('resolution=merge-duplicates').
- Fast delta-sync for catalog sync between crawler and Render cloud server.
- Built-in error handling and status diagnostics.
"""
import os
import json
import time
import logging
from typing import List, Dict, Any, Optional

import requests

logger = logging.getLogger("supabase_manager")

BATCH_SIZE = 200


class SupabaseManager:
    def __init__(self):
        self._url: Optional[str] = None
        self._key: Optional[str] = None
        self._disabled_until: float = 0.0
        self._load_config()

    def _load_config(self):
        env_upper = {k.strip().upper(): v.strip() for k, v in os.environ.items()}
        for k in ("SUPABASE_URL", "SUPABASE_PROJECT_URL", "SUPABASE_REST_URL"):
            if k in env_upper and env_upper[k]:
                self._url = env_upper[k].rstrip("/")
                break

        for k in ("SUPABASE_KEY", "SUPABASE_SERVICE_ROLE_KEY", "SUPABASE_SERVICE_KEY", "SUPABASE_ANON_KEY", "SUPABASE_API_KEY"):
            if k in env_upper and env_upper[k]:
                self._key = env_upper[k]
                break

        if not self._url or not self._key:
            data_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data")
            secret_files = [
                "/etc/secrets/supabase.json",
                "/etc/secrets/supabase.env",
                "/etc/secrets/.env",
                os.path.join(data_dir, "supabase.json"),
                os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), ".env"),
            ]
            for sf in secret_files:
                if os.path.exists(sf):
                    try:
                        if sf.endswith(".json"):
                            with open(sf, "r", encoding="utf-8") as f:
                                data = json.load(f)
                                self._url = self._url or data.get("supabase_url") or data.get("url") or data.get("SUPABASE_URL")
                                self._key = self._key or data.get("supabase_key") or data.get("key") or data.get("service_role_key") or data.get("SUPABASE_KEY")
                        else:
                            with open(sf, "r", encoding="utf-8") as f:
                                for line in f:
                                    line = line.strip()
                                    if line and not line.startswith("#") and "=" in line:
                                        k, v = line.split("=", 1)
                                        k = k.strip().upper()
                                        v = v.strip().strip("\"'")
                                        if k in ("SUPABASE_URL", "SUPABASE_PROJECT_URL"):
                                            self._url = self._url or v
                                        elif k in ("SUPABASE_KEY", "SUPABASE_SERVICE_ROLE_KEY", "SUPABASE_ANON_KEY"):
                                            self._key = self._key or v
                    except Exception as e:
                        logger.debug(f"Failed to read {sf}: {e}")

        if self._url and not self._url.startswith("http"):
            self._url = f"https://{self._url}"
        if self._url:
            self._url = self._url.rstrip("/")

    def is_configured(self) -> bool:
        if time.time() < self._disabled_until:
            return False
        return bool(self._url and self._key)

    def _headers(self, prefer: Optional[str] = None) -> Dict[str, str]:
        h = {
            "apikey": self._key or "",
            "Authorization": f"Bearer {self._key or ''}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        }
        if prefer:
            h["Prefer"] = prefer
        return h

    def test_connection(self) -> Dict[str, Any]:
        """Tests connection to Supabase and checks if media_items table exists."""
        if not self._url or not self._key:
            return {"ok": False, "error": "Supabase credentials not configured (SUPABASE_URL, SUPABASE_KEY missing)"}

        try:
            url = f"{self._url}/rest/v1/media_items?select=id&limit=1"
            resp = requests.get(url, headers=self._headers(), timeout=7)
            if resp.status_code == 200:
                return {"ok": True, "status": "connected", "table_exists": True}
            elif resp.status_code == 404 or "relation" in resp.text:
                return {"ok": True, "status": "connected", "table_exists": False, "note": "Table media_items not created yet in SQL editor"}
            else:
                return {"ok": False, "status_code": resp.status_code, "error": resp.text[:200]}
        except Exception as e:
            return {"ok": False, "error": str(e)}

    def get_remote_count(self) -> int:
        """Returns total count of media items in Supabase table."""
        if not self.is_configured():
            return 0
        try:
            url = f"{self._url}/rest/v1/media_items?select=id"
            resp = requests.head(url, headers=self._headers(prefer="count=exact"), timeout=5)
            if resp.status_code in (200, 206):
                content_range = resp.headers.get("Content-Range", "")
                if "/" in content_range:
                    total_str = content_range.split("/")[-1].strip()
                    if total_str.isdigit():
                        return int(total_str)
        except Exception as e:
            logger.debug(f"[Supabase] get_remote_count error: {e}")
        return 0

    def get_item(self, item_id: str) -> Optional[Dict[str, Any]]:
        """Fetches a single media item from Supabase by ID."""
        if not self.is_configured() or not item_id:
            return None
        try:
            url = f"{self._url}/rest/v1/media_items?id=eq.{item_id}&select=*"
            resp = requests.get(url, headers=self._headers(), timeout=4)
            if resp.status_code == 200:
                items = resp.json()
                if items and isinstance(items, list):
                    return items[0]
        except Exception as e:
            logger.debug(f"[Supabase] get_item error: {e}")
        return None

    def push_items_batch(self, rows: List[Dict[str, Any]]) -> int:
        """
        Upserts a batch of media items into Supabase.
        Automatically converts and normalizes columns.
        """
        if not self.is_configured() or not rows:
            return 0

        clean_rows = []
        now = time.time()
        for it in rows:
            raw_id = str(it.get("id") or "")
            title = it.get("title") or ""
            if not raw_id or not title:
                continue

            # Ensure id has proper prefix if raw numeric
            item_id = raw_id
            if item_id.isdigit():
                item_id = f"tmdb_{item_id}" if it.get("source_name") != "kinopoisk" else f"kp_{item_id}"

            countries_val = it.get("countries")
            if isinstance(countries_val, (list, dict)):
                countries_val = json.dumps(countries_val, ensure_ascii=False)
            elif not countries_val:
                countries_val = "[]"

            genres_val = it.get("genres")
            if isinstance(genres_val, (list, dict)):
                genres_val = json.dumps(genres_val, ensure_ascii=False)
            elif not genres_val:
                genres_val = "[]"

            cast_val = it.get("cast")
            if isinstance(cast_val, (list, dict)):
                cast_val = json.dumps(cast_val, ensure_ascii=False)
            elif not cast_val:
                cast_val = "[]"

            directors_val = it.get("directors_list")
            if isinstance(directors_val, (list, dict)):
                directors_val = json.dumps(directors_val, ensure_ascii=False)
            elif not directors_val:
                directors_val = "[]"

            extra_val = it.get("extra_data")
            if isinstance(extra_val, (list, dict)):
                extra_val = json.dumps(extra_val, ensure_ascii=False)
            elif not extra_val:
                extra_val = "{}"

            clean_rows.append({
                "id": item_id,
                "source_name": it.get("source_name") or "",
                "title": title,
                "clean_title": it.get("clean_title") or "",
                "original_title": it.get("original_title") or "",
                "year": int(it["year"]) if it.get("year") and str(it["year"]).isdigit() else None,
                "is_series": 1 if it.get("is_series") else 0,
                "category": it.get("category") or ("series" if it.get("is_series") else "movie"),
                "country": it.get("country") or "",
                "countries": countries_val,
                "poster": it.get("poster"),
                "backdrop": it.get("backdrop"),
                "description": it.get("description"),
                "rating_lampa": float(it["rating_lampa"]) if it.get("rating_lampa") is not None else None,
                "rating_kp": float(it["rating_kp"]) if it.get("rating_kp") is not None else None,
                "rating_rezka": float(it["rating_rezka"]) if it.get("rating_rezka") is not None else None,
                "rating_imdb": float(it["rating_imdb"]) if it.get("rating_imdb") is not None else None,
                "effective_rating": float(it["effective_rating"]) if it.get("effective_rating") is not None else None,
                "lampa_popularity": float(it.get("lampa_popularity") or 0.0),
                "popularity": float(it.get("popularity") or 0.0),
                "age_limit": str(it.get("age_limit") or "") if it.get("age_limit") else None,
                "kinopoisk_id": str(it.get("kinopoisk_id") or "") if it.get("kinopoisk_id") else None,
                "tmdb_id": str(it.get("tmdb_id") or "") if it.get("tmdb_id") else None,
                "genres": genres_val,
                "actors": it.get("actors") or "",
                "cast": cast_val,
                "director": it.get("director") or "",
                "directors_list": directors_val,
                "recommendations": json.dumps(it.get("recommendations") or [], ensure_ascii=False) if isinstance(it.get("recommendations"), list) else (it.get("recommendations") or "[]"),
                "tags": json.dumps(it.get("tags") or [], ensure_ascii=False) if isinstance(it.get("tags"), list) else (it.get("tags") or "[]"),
                "comments": json.dumps(it.get("comments") or [], ensure_ascii=False) if isinstance(it.get("comments"), list) else (it.get("comments") or "[]"),
                "extra_data": extra_val,
                "updated_at": float(it.get("updated_at") or now),
            })

        if not clean_rows:
            return 0

        total_upserted = 0
        url = f"{self._url}/rest/v1/media_items"
        # Upsert in chunks
        for i in range(0, len(clean_rows), BATCH_SIZE):
            chunk = clean_rows[i:i + BATCH_SIZE]
            try:
                resp = requests.post(
                    url,
                    headers=self._headers(prefer="resolution=merge-duplicates"),
                    json=chunk,
                    timeout=12
                )
                if resp.status_code in (200, 201):
                    total_upserted += len(chunk)
                else:
                    logger.warning(f"[Supabase] push batch error HTTP {resp.status_code}: {resp.text[:200]}")
            except Exception as e:
                logger.error(f"[Supabase] push batch exception: {e}")
                time.sleep(1)

        return total_upserted

    def pull_items_delta(self, since_updated_at: float = 0.0, limit: int = 5000) -> List[Dict[str, Any]]:
        """Pulls items updated or added in Supabase since given timestamp."""
        if not self.is_configured():
            return []

        try:
            url = f"{self._url}/rest/v1/media_items?updated_at=gt.{since_updated_at}&order=updated_at.asc&limit={limit}"
            resp = requests.get(url, headers=self._headers(), timeout=15)
            if resp.status_code == 200:
                data = resp.json()
                if isinstance(data, list):
                    return data
            else:
                logger.debug(f"[Supabase] pull delta HTTP {resp.status_code}: {resp.text[:150]}")
        except Exception as e:
            logger.debug(f"[Supabase] pull delta error: {e}")
        return []


supabase_manager = SupabaseManager()
