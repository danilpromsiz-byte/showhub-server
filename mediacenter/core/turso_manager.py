"""
ShowHub Turso (LibSQL) Cloud Database Manager.
Provides seamless synchronization and cloud persistence for media catalog items,
FTS5 full-text search index, and harvester crawler states across Render deployments.
Supports both native libsql-client and standard HTTP pipeline fallback via requests.
"""
import os
import json
import time
import logging
from typing import List, Dict, Any, Optional

import requests

logger = logging.getLogger("turso_manager")

try:
    import libsql_client
    HAS_LIBSQL = True
except ImportError:
    HAS_LIBSQL = False


def _py_to_hrana_val(val: Any) -> Dict[str, Any]:
    if val is None:
        return {"type": "null"}
    if isinstance(val, bool):
        return {"type": "integer", "value": "1" if val else "0"}
    if isinstance(val, int):
        return {"type": "integer", "value": str(val)}
    if isinstance(val, float):
        return {"type": "float", "value": val}
    return {"type": "text", "value": str(val)}


class TursoManager:
    def __init__(self):
        self._url: Optional[str] = None
        self._token: Optional[str] = None
        self._load_config()

    def _load_config(self):
        env_upper = {k.strip().upper(): v.strip() for k, v in os.environ.items()}
        for k in ("TURSO_DATABASE_URL", "TURSO_URL", "TURSO_DB_URL", "DATABASE_URL"):
            if k in env_upper and env_upper[k]:
                self._url = env_upper[k]
                break
        for k in ("TURSO_AUTH_TOKEN", "TURSO_TOKEN", "TURSO_DB_TOKEN", "TURSO_API_TOKEN", "AUTH_TOKEN"):
            if k in env_upper and env_upper[k]:
                self._token = env_upper[k]
                break

        if not self._url or not self._token:
            secret_files = [
                "/etc/secrets/turso.json",
                "/etc/secrets/.env",
                "/etc/secrets/turso.env",
                "/etc/secrets/secrets.env",
                os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data", "turso.json"),
                os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), ".env"),
            ]
            for p in secret_files:
                if os.path.exists(p):
                    try:
                        if p.endswith(".json"):
                            with open(p, "r", encoding="utf-8") as f:
                                data = json.load(f)
                                self._url = self._url or data.get("database_url") or data.get("TURSO_DATABASE_URL") or data.get("TURSO_URL")
                                self._token = self._token or data.get("auth_token") or data.get("TURSO_AUTH_TOKEN") or data.get("TURSO_TOKEN")
                        else:
                            with open(p, "r", encoding="utf-8") as f:
                                for line in f:
                                    line = line.strip()
                                    if line and not line.startswith("#") and "=" in line:
                                        k, v = line.split("=", 1)
                                        k = k.strip().upper()
                                        v = v.strip().strip("'\"")
                                        if "URL" in k: self._url = self._url or v
                                        if "TOKEN" in k: self._token = self._token or v
                    except Exception:
                        pass
                if self._url and self._token:
                    break

        if self._url:
            self._url = self._url.strip()
            if self._url.startswith("libsql://"):
                self._url = "https://" + self._url[len("libsql://"):]
        if self._token:
            self._token = self._token.strip()

    def is_configured(self) -> bool:
        self._load_config()
        return bool(self._url and self._token)

    def get_client(self):
        if not self.is_configured() or not HAS_LIBSQL:
            return None
        try:
            return libsql_client.create_client_sync(self._url, auth_token=self._token)
        except Exception as e:
            logger.debug(f"[Turso] Native client creation skipped: {e}")
            return None

    def _post_pipeline(self, stmts: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
        """Sends batch of statements via Turso HTTP pipeline (/v2/pipeline)."""
        if not self.is_configured() or not stmts:
            return None
        pipeline_url = f"{self._url.rstrip('/')}/v2/pipeline"
        headers = {
            "Authorization": f"Bearer {self._token}",
            "Content-Type": "application/json"
        }
        reqs = []
        for s in stmts:
            reqs.append({
                "type": "execute",
                "stmt": {
                    "sql": s["sql"],
                    "args": [_py_to_hrana_val(a) for a in s.get("args", [])]
                }
            })
        reqs.append({"type": "close"})

        for attempt in range(3):
            try:
                resp = requests.post(pipeline_url, headers=headers, json={"requests": reqs}, timeout=12)
                if resp.status_code == 200:
                    return resp.json()
                logger.debug(f"[Turso] HTTP pipeline attempt {attempt+1} status: {resp.status_code}")
            except Exception as e:
                logger.debug(f"[Turso] HTTP pipeline attempt {attempt+1} error: {e}")
            if attempt < 2:
                time.sleep(1.0 * (attempt + 1))
        return None

    def init_schema(self) -> bool:
        """Initializes tables, indexes and FTS5 in Turso cloud."""
        if not self.is_configured():
            return False

        sqls = [
            """CREATE TABLE IF NOT EXISTS media_items (
                id TEXT PRIMARY KEY,
                source_name TEXT,
                title TEXT NOT NULL,
                clean_title TEXT,
                original_title TEXT,
                year INTEGER,
                is_series INTEGER DEFAULT 0,
                category TEXT DEFAULT 'movie',
                country TEXT DEFAULT '',
                countries TEXT DEFAULT '[]',
                poster TEXT,
                backdrop TEXT,
                description TEXT,
                rating_lampa REAL,
                rating_kp REAL,
                rating_rezka REAL,
                rating_imdb REAL,
                effective_rating REAL,
                lampa_popularity REAL DEFAULT 0.0,
                popularity REAL DEFAULT 0.0,
                age_limit TEXT,
                kinopoisk_id TEXT,
                tmdb_id TEXT,
                genres TEXT,
                actors TEXT,
                cast TEXT,
                director TEXT,
                directors_list TEXT,
                recommendations TEXT,
                tags TEXT,
                comments TEXT,
                extra_data TEXT,
                updated_at REAL
            );""",
            "CREATE INDEX IF NOT EXISTS idx_clean_title ON media_items(clean_title);",
            "CREATE INDEX IF NOT EXISTS idx_kp_id ON media_items(kinopoisk_id);",
            "CREATE INDEX IF NOT EXISTS idx_tmdb_id ON media_items(tmdb_id);",
            "CREATE INDEX IF NOT EXISTS idx_cat_pop ON media_items(category, lampa_popularity DESC);",
            "CREATE INDEX IF NOT EXISTS idx_cat_rating ON media_items(category, effective_rating DESC);",
            "CREATE INDEX IF NOT EXISTS idx_updated_at ON media_items(updated_at DESC);",
            """CREATE VIRTUAL TABLE IF NOT EXISTS media_fts USING fts5(
                item_id UNINDEXED,
                title,
                original_title,
                description,
                actors,
                director,
                tags,
                tokenize='unicode61 remove_diacritics 2'
            );""",
            """CREATE TABLE IF NOT EXISTS harvester_state (
                source_key TEXT PRIMARY KEY,
                next_url TEXT,
                last_page INTEGER DEFAULT 0,
                total_harvested INTEGER DEFAULT 0,
                last_run_at REAL DEFAULT 0,
                is_completed INTEGER DEFAULT 0
            );"""
        ]

        if HAS_LIBSQL:
            client = self.get_client()
            if client:
                try:
                    for s in sqls:
                        client.execute(s)
                    logger.info("[Turso] Cloud schema initialized via native client.")
                    return True
                except Exception as e:
                    logger.debug(f"[Turso] Native schema init error, falling back to HTTP: {e}")
                finally:
                    try:
                        client.close()
                    except Exception:
                        pass

        # Fallback to HTTP pipeline
        stmts = [{"sql": s, "args": []} for s in sqls]
        res = self._post_pipeline(stmts)
        return bool(res and "results" in res)

    def push_items_batch(self, rows: List[Dict[str, Any]]) -> int:
        """Pushes a batch of media item dictionaries to Turso in a single atomic batch roundtrip."""
        if not self.is_configured() or not rows:
            return 0

        raw_stmts = []
        valid_count = 0

        for item in rows:
            item_id = str(item.get("id"))
            title = item.get("title") or ""
            if not item_id or not title:
                continue

            insert_sql = """
                INSERT INTO media_items (
                    id, source_name, title, clean_title, original_title, year,
                    is_series, category, country, countries, poster, backdrop,
                    description, rating_lampa, rating_kp, rating_rezka, rating_imdb,
                    effective_rating, lampa_popularity, popularity, age_limit,
                    kinopoisk_id, tmdb_id, genres, actors, cast, director,
                    directors_list, recommendations, tags, comments, extra_data, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                ON CONFLICT(id) DO UPDATE SET
                    title = excluded.title,
                    clean_title = excluded.clean_title,
                    original_title = excluded.original_title,
                    year = excluded.year,
                    is_series = excluded.is_series,
                    category = excluded.category,
                    country = excluded.country,
                    countries = excluded.countries,
                    poster = COALESCE(excluded.poster, media_items.poster),
                    backdrop = COALESCE(excluded.backdrop, media_items.backdrop),
                    description = COALESCE(excluded.description, media_items.description),
                    rating_lampa = excluded.rating_lampa,
                    rating_kp = excluded.rating_kp,
                    rating_rezka = excluded.rating_rezka,
                    rating_imdb = excluded.rating_imdb,
                    effective_rating = excluded.effective_rating,
                    lampa_popularity = excluded.lampa_popularity,
                    popularity = excluded.popularity,
                    age_limit = excluded.age_limit,
                    kinopoisk_id = excluded.kinopoisk_id,
                    tmdb_id = excluded.tmdb_id,
                    genres = excluded.genres,
                    actors = excluded.actors,
                    cast = excluded.cast,
                    director = excluded.director,
                    directors_list = excluded.directors_list,
                    recommendations = excluded.recommendations,
                    tags = excluded.tags,
                    comments = excluded.comments,
                    extra_data = excluded.extra_data,
                    updated_at = excluded.updated_at;
            """
            insert_args = [
                item_id,
                item.get("source_name") or "",
                title,
                item.get("clean_title") or "",
                item.get("original_title") or "",
                item.get("year"),
                1 if item.get("is_series") else 0,
                item.get("category") or "movie",
                item.get("country") or "",
                item.get("countries") if isinstance(item.get("countries"), str) else json.dumps(item.get("countries") or []),
                item.get("poster") or "",
                item.get("backdrop") or "",
                item.get("description") or "",
                item.get("rating_lampa"),
                item.get("rating_kp"),
                item.get("rating_rezka"),
                item.get("rating_imdb"),
                item.get("effective_rating"),
                item.get("lampa_popularity") or 0.0,
                item.get("popularity") or 0.0,
                item.get("age_limit") or "",
                item.get("kinopoisk_id") or "",
                item.get("tmdb_id") or "",
                item.get("genres") if isinstance(item.get("genres"), str) else json.dumps(item.get("genres") or []),
                item.get("actors") or "",
                item.get("cast") if isinstance(item.get("cast"), str) else json.dumps(item.get("cast") or []),
                item.get("director") or "",
                item.get("directors_list") if isinstance(item.get("directors_list"), str) else json.dumps(item.get("directors_list") or []),
                item.get("recommendations") if isinstance(item.get("recommendations"), str) else json.dumps(item.get("recommendations") or []),
                item.get("tags") if isinstance(item.get("tags"), str) else json.dumps(item.get("tags") or []),
                item.get("comments") if isinstance(item.get("comments"), str) else json.dumps(item.get("comments") or []),
                item.get("extra_data") if isinstance(item.get("extra_data"), str) else json.dumps(item.get("extra_data") or {}),
                item.get("updated_at") or time.time()
            ]

            del_fts_sql = "DELETE FROM media_fts WHERE item_id = ?;"
            ins_fts_sql = """
                INSERT INTO media_fts (item_id, title, original_title, description, actors, director, tags)
                VALUES (?, ?, ?, ?, ?, ?, ?);
            """
            ins_fts_args = [
                item_id,
                title,
                item.get("original_title") or "",
                item.get("description") or "",
                item.get("actors") or "",
                item.get("director") or "",
                item.get("tags") if isinstance(item.get("tags"), str) else json.dumps(item.get("tags") or [])
            ]

            raw_stmts.append({"sql": insert_sql, "args": insert_args})
            raw_stmts.append({"sql": del_fts_sql, "args": [item_id]})
            raw_stmts.append({"sql": ins_fts_sql, "args": ins_fts_args})
            valid_count += 1

        if not raw_stmts:
            return 0

        # Try native client if available
        if HAS_LIBSQL:
            client = self.get_client()
            if client:
                try:
                    stmts = [libsql_client.Statement(s["sql"], s["args"]) for s in raw_stmts]
                    for attempt in range(3):
                        try:
                            client.batch(stmts)
                            return valid_count
                        except Exception as ex:
                            if attempt < 2:
                                time.sleep(1.0 * (attempt + 1))
                                continue
                            logger.error(f"[Turso] Native batch error: {ex}")
                finally:
                    try:
                        client.close()
                    except Exception:
                        pass

        # Fallback to pure HTTP pipeline
        res = self._post_pipeline(raw_stmts)
        if res and "results" in res:
            return valid_count
        return 0

    def pull_items_delta(self, since_updated_at: float = 0.0, limit: int = 5000) -> List[Dict[str, Any]]:
        """Pulls items updated or added in Turso since given timestamp."""
        if not self.is_configured():
            return []

        if HAS_LIBSQL:
            client = self.get_client()
            if client:
                try:
                    res = client.execute(
                        "SELECT * FROM media_items WHERE updated_at > ? ORDER BY updated_at ASC LIMIT ?;",
                        [since_updated_at, limit]
                    )
                    return [row.asdict() for row in res.rows]
                except Exception as e:
                    logger.debug(f"[Turso] Native pull delta error: {e}")
                finally:
                    try:
                        client.close()
                    except Exception:
                        pass

        # Fallback to HTTP pipeline
        res = self._post_pipeline([{
            "sql": "SELECT * FROM media_items WHERE updated_at > ? ORDER BY updated_at ASC LIMIT ?;",
            "args": [since_updated_at, limit]
        }])
        if res and "results" in res and res["results"]:
            first = res["results"][0]
            if first.get("type") == "ok" and "response" in first:
                r_data = first["response"].get("result", {})
                cols = [c["name"] for c in r_data.get("cols", [])]
                rows = r_data.get("rows", [])
                items = []
                for row_vals in rows:
                    row_dict = {}
                    for col_name, val_dict in zip(cols, row_vals):
                        v = val_dict.get("value")
                        if val_dict.get("type") == "null":
                            v = None
                        elif val_dict.get("type") == "integer" and v is not None:
                            v = int(v)
                        row_dict[col_name] = v
                    items.append(row_dict)
                return items
        return []

    def get_remote_count(self) -> int:
        """Returns total count of media items in Turso."""
        if not self.is_configured():
            return 0
        if HAS_LIBSQL:
            client = self.get_client()
            if client:
                try:
                    res = client.execute("SELECT COUNT(*) FROM media_items;")
                    return res.rows[0][0]
                except Exception:
                    pass
                finally:
                    try:
                        client.close()
                    except Exception:
                        pass

        # HTTP fallback
        res = self._post_pipeline([{"sql": "SELECT COUNT(*) FROM media_items;", "args": []}])
        try:
            if res and "results" in res:
                val = res["results"][0]["response"]["result"]["rows"][0][0]["value"]
                return int(val)
        except Exception:
            pass
        return 0


turso_manager = TursoManager()
