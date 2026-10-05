"""
ShowHub Turso (LibSQL) Cloud Database Manager.
Provides seamless synchronization and cloud persistence for media catalog items,
FTS5 full-text search index, and harvester crawler states across Render deployments.
"""
import os
import json
import time
import logging
from typing import List, Dict, Any, Optional

logger = logging.getLogger("turso_manager")

try:
    import libsql_client
    HAS_LIBSQL = True
except ImportError:
    HAS_LIBSQL = False


class TursoManager:
    def __init__(self):
        self._url: Optional[str] = None
        self._token: Optional[str] = None
        self._load_config()

    def _load_config(self):
        self._url = os.environ.get("TURSO_DATABASE_URL") or os.environ.get("TURSO_URL")
        self._token = os.environ.get("TURSO_AUTH_TOKEN") or os.environ.get("TURSO_TOKEN")
        if self._url:
            self._url = self._url.strip()
        if self._token:
            self._token = self._token.strip()

    def is_configured(self) -> bool:
        self._load_config()
        return bool(HAS_LIBSQL and self._url and self._token)

    def get_client(self):
        if not self.is_configured():
            return None
        try:
            return libsql_client.create_client_sync(self._url, auth_token=self._token)
        except Exception as e:
            logger.error(f"[Turso] Failed to create client: {e}")
            return None

    def init_schema(self) -> bool:
        """Initializes tables, indexes and FTS5 in Turso cloud."""
        if not self.is_configured():
            return False

        client = self.get_client()
        if not client:
            return False

        try:
            client.execute("""
                CREATE TABLE IF NOT EXISTS media_items (
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
                );
            """)

            # Indexes
            client.execute("CREATE INDEX IF NOT EXISTS idx_clean_title ON media_items(clean_title);")
            client.execute("CREATE INDEX IF NOT EXISTS idx_kp_id ON media_items(kinopoisk_id);")
            client.execute("CREATE INDEX IF NOT EXISTS idx_tmdb_id ON media_items(tmdb_id);")
            client.execute("CREATE INDEX IF NOT EXISTS idx_cat_pop ON media_items(category, lampa_popularity DESC);")
            client.execute("CREATE INDEX IF NOT EXISTS idx_cat_rating ON media_items(category, effective_rating DESC);")
            client.execute("CREATE INDEX IF NOT EXISTS idx_updated_at ON media_items(updated_at DESC);")

            # FTS5 Virtual Table
            client.execute("""
                CREATE VIRTUAL TABLE IF NOT EXISTS media_fts USING fts5(
                    item_id UNINDEXED,
                    title,
                    original_title,
                    description,
                    actors,
                    director,
                    tags,
                    tokenize='unicode61 remove_diacritics 2'
                );
            """)

            # Harvester state table
            client.execute("""
                CREATE TABLE IF NOT EXISTS harvester_state (
                    source_key TEXT PRIMARY KEY,
                    next_url TEXT,
                    last_page INTEGER DEFAULT 0,
                    total_harvested INTEGER DEFAULT 0,
                    last_run_at REAL DEFAULT 0,
                    is_completed INTEGER DEFAULT 0
                );
            """)

            logger.info("[Turso] Cloud schema and FTS5 initialized successfully.")
            return True
        except Exception as e:
            logger.error(f"[Turso] Schema init failed: {e}")
            return False
        finally:
            try:
                client.close()
            except Exception:
                pass

    def push_items_batch(self, rows: List[Dict[str, Any]]) -> int:
        """Pushes a batch of media item dictionaries to Turso."""
        if not self.is_configured() or not rows:
            return 0

        client = self.get_client()
        if not client:
            return 0

        inserted = 0
        try:
            for item in rows:
                try:
                    item_id = str(item.get("id"))
                    title = item.get("title") or ""
                    if not item_id or not title:
                        continue

                    # Upsert media_items
                    client.execute("""
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
                    """, [
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
                    ])

                    # Maintain FTS5
                    client.execute("DELETE FROM media_fts WHERE item_id = ?;", [item_id])
                    client.execute("""
                        INSERT INTO media_fts (item_id, title, original_title, description, actors, director, tags)
                        VALUES (?, ?, ?, ?, ?, ?, ?);
                    """, [
                        item_id,
                        title,
                        item.get("original_title") or "",
                        item.get("description") or "",
                        item.get("actors") or "",
                        item.get("director") or "",
                        item.get("tags") if isinstance(item.get("tags"), str) else json.dumps(item.get("tags") or [])
                    ])
                    inserted += 1
                except Exception as ex:
                    logger.debug(f"[Turso] Item push error: {ex}")
            return inserted
        finally:
            try:
                client.close()
            except Exception:
                pass

    def pull_items_delta(self, since_updated_at: float = 0.0, limit: int = 5000) -> List[Dict[str, Any]]:
        """Pulls items updated or added in Turso since given timestamp."""
        if not self.is_configured():
            return []

        client = self.get_client()
        if not client:
            return []

        try:
            res = client.execute(
                "SELECT * FROM media_items WHERE updated_at > ? ORDER BY updated_at ASC LIMIT ?;",
                [since_updated_at, limit]
            )
            return [row.asdict() for row in res.rows]
        except Exception as e:
            logger.error(f"[Turso] Failed to pull delta: {e}")
            return []
        finally:
            try:
                client.close()
            except Exception:
                pass

    def get_remote_count(self) -> int:
        """Returns total count of media items in Turso."""
        if not self.is_configured():
            return 0
        client = self.get_client()
        if not client:
            return 0
        try:
            res = client.execute("SELECT COUNT(*) FROM media_items;")
            return res.rows[0][0]
        except Exception:
            return 0
        finally:
            try:
                client.close()
            except Exception:
                pass


turso_manager = TursoManager()
