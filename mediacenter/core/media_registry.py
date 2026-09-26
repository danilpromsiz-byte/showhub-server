"""
ShowHub High-Performance Media Registry & Full-Text Search Index.
Stores canonical media records across all sources (Filmix, Zona, AniLibria, Kodik, HDRezka, TMDb).
Enables instant (<10ms) catalog filtering, sorting, and full-text search with SQLite FTS5.
Automatically harvests and indexes new items from catalogs, searches, and stream queries.
"""
import os
import re
import json
import time
import math
import sqlite3
import logging
import threading
from typing import List, Dict, Any, Optional, Tuple

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

def get_country_aliases(country: str) -> List[str]:
    c_clean = country.lower().strip()
    aliases = [c_clean]
    if "коре" in c_clean:
        aliases.extend(["корея", "южная корея", "korea", "корей"])
    elif "сша" in c_clean or "америк" in c_clean:
        aliases.extend(["сша", "usa", "америк", "соединенные штаты"])
    elif "росси" in c_clean or "ссср" in c_clean:
        aliases.extend(["россия", "ссср", "russia", "россий", "советск"])
    elif "великобрит" in c_clean or "англи" in c_clean:
        aliases.extend(["великобритания", "англия", "uk", "британ"])
    elif "япон" in c_clean:
        aliases.extend(["япония", "japan", "япон"])
    elif "турц" in c_clean:
        aliases.extend(["турция", "turkey", "турец"])
    elif "кита" in c_clean:
        aliases.extend(["китай", "china", "китай"])
    elif "инди" in c_clean:
        aliases.extend(["индия", "india", "индий"])
    elif "франц" in c_clean:
        aliases.extend(["франция", "france", "француз"])
    elif "герман" in c_clean:
        aliases.extend(["германия", "germany", "немец"])
    elif "италь" in c_clean or "итали" in c_clean:
        aliases.extend(["италия", "italy", "итальян"])
    elif "испан" in c_clean:
        aliases.extend(["испания", "spain", "испан"])
    elif "канад" in c_clean:
        aliases.extend(["канада", "canada", "канад"])
    elif "австрал" in c_clean:
        aliases.extend(["австралия", "australia"])
    elif "таиланд" in c_clean or "тайланд" in c_clean:
        aliases.extend(["таиланд", "тайланд", "thailand"])
    elif "швеци" in c_clean:
        aliases.extend(["швеция", "sweden"])
    return list(set(aliases))

def detect_category(data: Dict[str, Any], is_ser: int, genres_list: List[str]) -> str:
    cat_hint = str(data.get("category") or data.get("content_type") or "").lower()
    src = str(data.get("source_name") or "").lower()
    g_lower = [str(g).lower() for g in genres_list]
    title_lower = str(data.get("title") or "").lower()

    if src == "anilibria" or any("аниме" in g or "anime" in g for g in g_lower) or cat_hint == "anime":
        return "anime"
    if any("мульт" in g or "анимац" in g for g in g_lower) or cat_hint in ("cartoons", "cartoon"):
        return "cartoons"
    if is_ser == 1 or cat_hint in ("series", "serial") or any("сериал" in g for g in g_lower) or "сезон" in title_lower or "серия" in title_lower:
        return "series"
    return "movie"

def extract_country_info(data: Dict[str, Any], extra: Dict[str, Any], genres_list: List[str]) -> Tuple[str, List[str]]:
    direct_c = str(data.get("country") or extra.get("country") or "").strip()
    countries_list = []
    raw_countries = extra.get("countries") or []
    if isinstance(raw_countries, list):
        countries_list = [str(c).strip() for c in raw_countries if str(c).strip()]
    elif isinstance(raw_countries, str) and raw_countries.strip():
        countries_list = [c.strip() for c in raw_countries.split(",") if c.strip()]

    if direct_c and direct_c not in countries_list:
        countries_list.insert(0, direct_c)

    # Infer from description or genres if empty
    if not countries_list:
        desc = str(data.get("description") or "")
        full = f"{desc} {' '.join(genres_list)}".lower()
        if any(w in full for w in ["япони", "японс", "аниме"]):
            countries_list.append("Япония")
        elif any(w in full for w in ["корея", "корей", "дорама"]):
            countries_list.append("Корея Южная")
        elif any(w in full for w in ["россия", "россий", "ссср"]):
            countries_list.append("Россия")
        elif any(w in full for w in ["сша", "америк"]):
            countries_list.append("США")
        elif any(w in full for w in ["турция", "турец"]):
            countries_list.append("Турция")
        elif any(w in full for w in ["франция", "француз"]):
            countries_list.append("Франция")
        elif any(w in full for w in ["великобритания", "британ"]):
            countries_list.append("Великобритания")

    primary_country = countries_list[0] if countries_list else ""
    return primary_country, countries_list

def compute_popularity(rkp: Optional[float], rimdb: Optional[float], year: Optional[int], votes: int, poster: Optional[str]) -> float:
    eff_r = max(rkp or 0.0, rimdb or 0.0)
    score = eff_r * 10000.0 if eff_r > 0 else 5000.0
    if votes > 0:
        score += math.log10(max(votes, 1)) * 5000.0
    if poster and "no_image_poster" not in poster and "noposter" not in poster:
        score += 8000.0
    current_year = 2026
    if year:
        diff = max(0, current_year - year)
        score += max(0, (10 - min(diff, 10)) * 1000.0)
    return score

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
                    category TEXT DEFAULT 'movie',
                    country TEXT DEFAULT '',
                    countries TEXT DEFAULT '[]',
                    poster TEXT,
                    description TEXT,
                    rating_kp REAL,
                    rating_imdb REAL,
                    popularity REAL DEFAULT 0.0,
                    kinopoisk_id TEXT,
                    genres TEXT,
                    extra_data TEXT,
                    updated_at REAL
                );
            """)

            # Migration for existing DBs if columns are missing
            cols = [c[1] for c in conn.execute("PRAGMA table_info(media_items);").fetchall()]
            if "category" not in cols:
                conn.execute("ALTER TABLE media_items ADD COLUMN category TEXT DEFAULT 'movie';")
            if "country" not in cols:
                conn.execute("ALTER TABLE media_items ADD COLUMN country TEXT DEFAULT '';")
            if "countries" not in cols:
                conn.execute("ALTER TABLE media_items ADD COLUMN countries TEXT DEFAULT '[]';")
            if "popularity" not in cols:
                conn.execute("ALTER TABLE media_items ADD COLUMN popularity REAL DEFAULT 0.0;")

            conn.execute("CREATE INDEX IF NOT EXISTS idx_clean_title ON media_items(clean_title);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_kp_id ON media_items(kinopoisk_id);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_year ON media_items(year);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_is_series ON media_items(is_series);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_cat_rating ON media_items(category, rating_kp DESC);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_cat_year ON media_items(category, year DESC);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_cat_pop ON media_items(category, popularity DESC);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_cat_updated ON media_items(category, updated_at DESC);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_rating_desc ON media_items(rating_kp DESC);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_year_desc ON media_items(year DESC);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_pop_desc ON media_items(popularity DESC);")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_country ON media_items(country);")

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

                raw_genres = data.get("genres") or []
                if isinstance(raw_genres, list):
                    genres_list = [str(g).strip() for g in raw_genres if str(g).strip()]
                else:
                    genres_list = [str(raw_genres).strip()]

                genres_json = json.dumps(genres_list, ensure_ascii=False)
                extra = data.get("extra_data") or {}
                if not isinstance(extra, dict):
                    extra = {}

                # Rich category, country, and popularity detection
                cat = detect_category(data, is_ser, genres_list)
                primary_c, c_list = extract_country_info(data, extra, genres_list)
                countries_json = json.dumps(c_list, ensure_ascii=False)

                votes = int(extra.get("vote_num_kp") or extra.get("vote_num_imdb") or 0)
                pop_score = compute_popularity(rkp, rimdb, year, votes, poster)

                extra_json = json.dumps(extra, ensure_ascii=False)

                prepared_rows.append((
                    unique_id, source_name, title, orig_title, clean,
                    year, is_ser, cat, primary_c, countries_json,
                    poster, desc, rkp, rimdb, pop_score, kp_id,
                    genres_json, extra_json, now
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
                        year, is_series, category, country, countries,
                        poster, description, rating_kp, rating_imdb, popularity,
                        kinopoisk_id, genres, extra_data, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        title = excluded.title,
                        original_title = COALESCE(excluded.original_title, media_items.original_title),
                        clean_title = excluded.clean_title,
                        year = COALESCE(excluded.year, media_items.year),
                        is_series = excluded.is_series,
                        category = excluded.category,
                        country = CASE WHEN excluded.country != '' THEN excluded.country ELSE media_items.country END,
                        countries = CASE WHEN excluded.countries != '[]' THEN excluded.countries ELSE media_items.countries END,
                        poster = COALESCE(excluded.poster, media_items.poster),
                        description = COALESCE(excluded.description, media_items.description),
                        rating_kp = COALESCE(excluded.rating_kp, media_items.rating_kp),
                        rating_imdb = COALESCE(excluded.rating_imdb, media_items.rating_imdb),
                        popularity = MAX(excluded.popularity, media_items.popularity),
                        kinopoisk_id = COALESCE(excluded.kinopoisk_id, media_items.kinopoisk_id),
                        genres = excluded.genres,
                        extra_data = excluded.extra_data,
                        updated_at = excluded.updated_at;
                """, prepared_rows)

                # Upsert FTS5 entries (delete old, insert fresh)
                for fts in fts_rows:
                    conn.execute("DELETE FROM media_fts WHERE item_id = ?;", (fts[0],))
                    conn.execute("""
                        INSERT INTO media_fts (item_id, title, original_title, description)
                        VALUES (?, ?, ?, ?);
                    """, fts)

                inserted_count = len(prepared_rows)
        except Exception as e:
            logger.error(f"Failed to upsert batch into MediaRegistry: {e}")

        return inserted_count

    def query_catalog(
        self,
        category: str = "all",
        genre: Optional[str] = None,
        year: Optional[str] = None,
        country: Optional[str] = None,
        content_type: Optional[str] = "all",
        min_rating: Optional[float] = None,
        sort_by: Optional[str] = "newest",
        page: int = 1,
        limit: int = 50,
        excluded_countries: Optional[str] = None,
        excluded_genres: Optional[str] = None
    ) -> List[Dict[str, Any]]:
        """
        High-performance (<3ms) indexed catalog query with real multi-criteria filtering,
        global sorting across the entire library, and pagination.
        """
        conn = self._get_connection()
        conditions = ["1=1"]
        params: List[Any] = []

        # 1. Effective category / content_type
        eff_cat = category if category != "all" else (content_type if content_type != "all" else "all")
        if eff_cat in ("movie", "movies"):
            conditions.append("(category = 'movie' OR is_series = 0)")
        elif eff_cat in ("series", "serial"):
            conditions.append("(category = 'series' OR is_series = 1)")
        elif eff_cat == "cartoons":
            conditions.append("(category = 'cartoons' OR genres LIKE '%мультфильм%' OR genres LIKE '%мультсериал%' OR genres LIKE '%анимация%')")
        elif eff_cat == "anime":
            conditions.append("(category = 'anime' OR genres LIKE '%аниме%')")

        # 2. Genre filtering with Russian stem matching
        if genre and genre != "all":
            g_clean = genre.lower().strip()
            stem = g_clean
            if g_clean.endswith(("ия", "ии", "ые", "ий", "ка", "ки")):
                stem = g_clean[:-2]
            elif g_clean.endswith(("а", "ы", "и", "я")):
                stem = g_clean[:-1]
            conditions.append("(genres LIKE ? OR description LIKE ?)")
            params.extend([f"%{stem}%", f"%{stem}%"])

        # 3. Country filtering with aliases
        if country and country != "all":
            aliases = get_country_aliases(country)
            c_clauses = []
            for a in aliases:
                c_clauses.append("(country LIKE ? OR countries LIKE ? OR description LIKE ?)")
                params.extend([f"%{a}%", f"%{a}%", f"%{a}%"])
            conditions.append(f"({' OR '.join(c_clauses)})")

        # 4. Year filtering
        if year and year != "all":
            if year.isdigit():
                conditions.append("year = ?")
                params.append(int(year))
            elif year == "2020-2022":
                conditions.append("year BETWEEN 2020 AND 2022")
            elif year == "2010s":
                conditions.append("year BETWEEN 2010 AND 2019")
            elif year == "2000s":
                conditions.append("year BETWEEN 2000 AND 2009")
            elif year == "before_2000":
                conditions.append("year < 2000")

        # 5. Rating filtering
        if min_rating and min_rating > 0:
            conditions.append("(rating_kp >= ? OR rating_imdb >= ?)")
            params.extend([float(min_rating), float(min_rating)])

        # 6. Excluded countries
        if excluded_countries:
            for ex in [c.strip().lower() for c in excluded_countries.split(",") if c.strip()]:
                conditions.append("(country NOT LIKE ? AND countries NOT LIKE ?)")
                params.extend([f"%{ex}%", f"%{ex}%"])

        # 7. Excluded genres
        if excluded_genres:
            for ex in [g.strip().lower() for g in excluded_genres.split(",") if g.strip()]:
                conditions.append("genres NOT LIKE ?")
                params.append(f"%{ex}%")

        # 8. True Global Sorting
        if sort_by == "rating":
            order_by = "COALESCE(rating_kp, rating_imdb, 0) DESC, popularity DESC, updated_at DESC"
        elif sort_by == "year":
            order_by = "COALESCE(year, 0) DESC, COALESCE(rating_kp, rating_imdb, 0) DESC"
        elif sort_by == "popular":
            order_by = "popularity DESC, COALESCE(rating_kp, rating_imdb, 0) DESC"
        else:  # "newest" / fresh releases
            order_by = "updated_at DESC, COALESCE(year, 0) DESC, COALESCE(rating_kp, rating_imdb, 0) DESC"

        offset = max(0, (page - 1) * limit)
        sql = f"""
            SELECT * FROM media_items
            WHERE {' AND '.join(conditions)}
            GROUP BY clean_title, COALESCE(year, 0)
            ORDER BY {order_by}
            LIMIT ? OFFSET ?
        """
        params.extend([limit, offset])

        results: List[Dict[str, Any]] = []
        try:
            rows = conn.execute(sql, params).fetchall()
            for r in rows:
                results.append(self._row_to_dict(r))
        except Exception as e:
            logger.error(f"Catalog query error in MediaRegistry: {e}")

        return results

    def search(self, query: str, limit: int = 30) -> List[Dict[str, Any]]:
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

            if len(results) < limit:
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

        keys = row.keys()
        country = row["country"] if "country" in keys and row["country"] else (extra.get("country") or "")
        countries = []
        try:
            if "countries" in keys and row["countries"]:
                countries = json.loads(row["countries"])
            elif extra.get("countries"):
                countries = extra.get("countries")
        except Exception:
            pass
        if not countries and country:
            countries = [country]

        category = row["category"] if "category" in keys and row["category"] else ("series" if row["is_series"] else "movie")

        return {
            "id": row["id"],
            "source_name": row["source_name"],
            "title": row["title"],
            "original_title": row["original_title"],
            "year": row["year"],
            "is_series": bool(row["is_series"]),
            "category": category,
            "country": country,
            "countries": countries,
            "poster": row["poster"],
            "description": row["description"],
            "rating_kp": row["rating_kp"],
            "rating_imdb": row["rating_imdb"],
            "kinopoisk_id": row["kinopoisk_id"],
            "genres": genres,
            "episodes_info": extra.get("episodes_info"),
            "extra_data": extra
        }

media_registry = MediaRegistry()
