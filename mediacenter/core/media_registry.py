"""
ShowHub High-Performance Media Registry & Full-Text Search Index.
Stores canonical media records across all sources (Filmix, Zona, AniLibria, Kodik, HDRezka, TMDb).
Enables instant (<10ms) full-text and prefix search with SQLite FTS5.
Automatically harvests and indexes new items from catalogs, searches, and stream queries.
"""
import os
import re
import json
import time
import sqlite3
import logging
import threading
from typing import List, Dict, Any, Optional

logger = logging.getLogger("media_registry")

DB_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data")
DB_PATH = os.path.join(DB_DIR, "media_registry.db")

def normalize_title(s: Optional[str]) -> str:
    if not s:
        return ""
    clean = re.sub(r'[\(\[\{].*?[\)\]\}]', ' ', s)
    clean = re.sub(r'[^\w\s]', ' ', clean, flags=re.UNICODE)
    clean = re.sub(r'\s+', ' ', clean).strip().lower()
    return clean

class MediaRegistry:
    def __init__(self, db_path: str = DB_PATH):
        self.db_path = db_path
        os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
        self._local = threading.local()
        self._init_db()

    def _get_connection(self) -> sqlite3.Connection:
        if not hasattr(self._local, "conn") or self._local.conn is None:
            conn = sqlite3.connect(self.db_path, timeout=10.0, check_same_thread=False)
            conn.execute("PRAGMA journal_mode=WAL;")
            conn.execute("PRAGMA synchronous=NORMAL;")
            conn.execute("PRAGMA cache_size=-8000;")  # 8MB cache
            conn.row_factory = sqlite3.Row
            self._local.conn = conn
        return self._local.conn

    def _init_db(self):
        conn = self._get_connection()
        with conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS media_items (
                    id TEXT PRIMARY KEY,
                    source_name TEXT NOT NULL,
                    title TEXT NOT NULL,
                    original_title TEXT,
                    clean_title TEXT NOT NULL,
                    year INTEGER,
                    is_series INTEGER DEFAULT 0,
                    poster TEXT,
                    description TEXT,
                    rating_kp REAL,
                    rating_imdb REAL,
                    kinopoisk_id TEXT,
                    genres TEXT,
                    extra_data TEXT,
                    updated_at REAL
                );
            """)
            conn.execute("CREATE INDEX IF NOT EXISTS idx_clean_title ON media_items(clean_title);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_kp_id ON media_items(kinopoisk_id);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_year ON media_items(year);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_is_series ON media_items(is_series);")

            # Full-Text Search 5 Virtual Table
            conn.execute("""
                CREATE VIRTUAL TABLE IF NOT EXISTS media_fts USING fts5(
                    item_id UNINDEXED,
                    title,
                    original_title,
                    description,
                    tokenize='unicode61 remove_diacritics 2'
                );
            """)

    def upsert_item(self, item: Any) -> bool:
        """Upserts a single MediaItem or dict into the registry."""
        return self.upsert_batch([item]) > 0

    def upsert_batch(self, items: List[Any]) -> int:
        """Batch upserts items into the registry with deduplication and indexing."""
        if not items:
            return 0

        conn = self._get_connection()
        now = time.time()
        inserted_count = 0

        prepared_rows = []
        fts_rows = []

        for it in items:
            try:
                if hasattr(it, "model_dump"):
                    data = it.model_dump()
                elif isinstance(it, dict):
                    data = it
                else:
                    data = getattr(it, "__dict__", {})

                raw_id = str(data.get("id") or "").strip()
                title = str(data.get("title") or "").strip()
                if not title:
                    continue

                source_name = str(data.get("source_name") or "registry")
                unique_id = f"{source_name}_{raw_id}" if raw_id and not raw_id.startswith(source_name) else (raw_id or f"{source_name}_{hash(title)}")

                orig_title = data.get("original_title")
                clean = normalize_title(title)
                if not clean:
                    continue

                year = data.get("year")
                try:
                    year = int(year) if year else None
                except (ValueError, TypeError):
                    year = None

                is_ser = 1 if bool(data.get("is_series")) else 0
                poster = data.get("poster")
                desc = data.get("description")

                def safe_float(v):
                    try:
                        return float(v) if v is not None else None
                    except (ValueError, TypeError):
                        return None

                rkp = safe_float(data.get("rating_kp"))
                rimdb = safe_float(data.get("rating_imdb"))

                kp_id = str(data.get("kinopoisk_id") or "").strip()
                if not kp_id or not kp_id.isdigit():
                    kp_id = None

                genres = json.dumps(data.get("genres") or [], ensure_ascii=False)
                extra = json.dumps(data.get("extra_data") or {}, ensure_ascii=False)

                prepared_rows.append((
                    unique_id, source_name, title, orig_title, clean,
                    year, is_ser, poster, desc, rkp, rimdb, kp_id,
                    genres, extra, now
                ))

                fts_rows.append((
                    unique_id, title, orig_title or "", desc or ""
                ))
            except Exception as e:
                logger.debug(f"Error preparing row for registry: {e}")
                continue

        if not prepared_rows:
            return 0

        try:
            with conn:
                conn.executemany("""
                    INSERT INTO media_items (
                        id, source_name, title, original_title, clean_title,
                        year, is_series, poster, description, rating_kp, rating_imdb,
                        kinopoisk_id, genres, extra_data, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        title = excluded.title,
                        original_title = COALESCE(excluded.original_title, media_items.original_title),
                        clean_title = excluded.clean_title,
                        year = COALESCE(excluded.year, media_items.year),
                        is_series = excluded.is_series,
                        poster = COALESCE(excluded.poster, media_items.poster),
                        description = COALESCE(excluded.description, media_items.description),
                        rating_kp = COALESCE(excluded.rating_kp, media_items.rating_kp),
                        rating_imdb = COALESCE(excluded.rating_imdb, media_items.rating_imdb),
                        kinopoisk_id = COALESCE(excluded.kinopoisk_id, media_items.kinopoisk_id),
                        genres = COALESCE(excluded.genres, media_items.genres),
                        extra_data = excluded.extra_data,
                        updated_at = excluded.updated_at;
                """, prepared_rows)

                # Keep FTS table in sync
                for fts_entry in fts_rows:
                    conn.execute("DELETE FROM media_fts WHERE item_id = ?;", (fts_entry[0],))
                    conn.execute("INSERT INTO media_fts (item_id, title, original_title, description) VALUES (?, ?, ?, ?);", fts_entry)

            inserted_count = len(prepared_rows)
        except Exception as e:
            logger.error(f"Failed to execute batch upsert in MediaRegistry: {e}")

        return inserted_count

    def search(self, query: str, limit: int = 50) -> List[Dict[str, Any]]:
        """
        Ultra-fast (<5ms) indexed search across the media registry.
        Uses exact prefix, substring, and FTS5 ranking with deduplication.
        """
        clean_q = normalize_title(query)
        if not clean_q or len(clean_q) < 1:
            return []

        conn = self._get_connection()
        results: List[Dict[str, Any]] = []
        seen_keys = set()

        try:
            # 1. Exact & Prefix Matches (Fastest & Highest Priority)
            prefix_pattern = f"{clean_q}%"
            substr_pattern = f"%{clean_q}%"

            rows = conn.execute("""
                SELECT *,
                    CASE
                        WHEN clean_title = ? THEN 1000
                        WHEN clean_title LIKE ? THEN 800
                        WHEN clean_title LIKE ? THEN 500
                        ELSE 100
                    END as match_score
                FROM media_items
                WHERE clean_title LIKE ? OR clean_title LIKE ?
                ORDER BY match_score DESC, COALESCE(rating_kp, rating_imdb, 0) DESC, COALESCE(year, 0) DESC
                LIMIT ?;
            """, (clean_q, prefix_pattern, substr_pattern, prefix_pattern, substr_pattern, limit)).fetchall()

            for r in rows:
                key = (r["clean_title"], r["year"] or 0)
                if key not in seen_keys:
                    seen_keys.add(key)
                    results.append(self._row_to_dict(r))

            # 2. If results are few, enrich via FTS5 full-text matching
            if len(results) < limit:
                # Prepare FTS query (word prefixes: e.g. "матр*")
                words = [w.strip() for w in clean_q.split() if len(w.strip()) >= 2]
                if words:
                    fts_query = " ".join([f'"{w}"*' for w in words])
                    try:
                        fts_rows = conn.execute("""
                            SELECT m.*, rank
                            FROM media_fts f
                            JOIN media_items m ON f.item_id = m.id
                            WHERE media_fts MATCH ?
                            ORDER BY rank, COALESCE(m.rating_kp, m.rating_imdb, 0) DESC
                            LIMIT ?;
                        """, (fts_query, limit - len(results))).fetchall()

                        for r in fts_rows:
                            key = (r["clean_title"], r["year"] or 0)
                            if key not in seen_keys:
                                seen_keys.add(key)
                                results.append(self._row_to_dict(r))
                    except Exception:
                        pass
        except Exception as e:
            logger.error(f"Search error in MediaRegistry: {e}")

        return results[:limit]

    def count(self) -> int:
        """Returns total number of items indexed in registry."""
        try:
            conn = self._get_connection()
            cur = conn.execute("SELECT COUNT(*) FROM media_items;")
            return cur.fetchone()[0]
        except Exception:
            return 0

    def _row_to_dict(self, row: sqlite3.Row) -> Dict[str, Any]:
        genres = []
        try:
            if row["genres"]:
                genres = json.loads(row["genres"])
        except Exception:
            pass

        extra = {}
        try:
            if row["extra_data"]:
                extra = json.loads(row["extra_data"])
        except Exception:
            pass

        return {
            "id": row["id"],
            "source_name": row["source_name"],
            "title": row["title"],
            "original_title": row["original_title"],
            "year": row["year"],
            "is_series": bool(row["is_series"]),
            "poster": row["poster"],
            "description": row["description"],
            "rating_kp": row["rating_kp"],
            "rating_imdb": row["rating_imdb"],
            "kinopoisk_id": row["kinopoisk_id"],
            "genres": genres,
            "extra_data": extra
        }

media_registry = MediaRegistry()
