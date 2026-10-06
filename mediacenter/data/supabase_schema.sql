-- ShowHub PostgreSQL / Supabase Schema Definition
-- Run this script in the Supabase SQL Editor (https://supabase.com/dashboard/project/_/sql)

-- 1. Enable useful extensions
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. Create the main media_items table
CREATE TABLE IF NOT EXISTS public.media_items (
    id TEXT PRIMARY KEY,
    source_name TEXT DEFAULT '',
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
    genres TEXT DEFAULT '[]',
    actors TEXT,
    "cast" TEXT DEFAULT '[]',
    director TEXT,
    directors_list TEXT DEFAULT '[]',
    recommendations TEXT DEFAULT '[]',
    tags TEXT DEFAULT '[]',
    comments TEXT DEFAULT '[]',
    extra_data TEXT DEFAULT '{}',
    updated_at DOUBLE PRECISION
);

-- 3. High-performance indexes
CREATE INDEX IF NOT EXISTS idx_media_clean_title ON public.media_items(clean_title);
CREATE INDEX IF NOT EXISTS idx_media_updated_at ON public.media_items(updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_media_year ON public.media_items(year);
CREATE INDEX IF NOT EXISTS idx_media_kp_id ON public.media_items(kinopoisk_id);
CREATE INDEX IF NOT EXISTS idx_media_tmdb_id ON public.media_items(tmdb_id);
CREATE INDEX IF NOT EXISTS idx_media_cat_pop ON public.media_items(category, lampa_popularity DESC);
CREATE INDEX IF NOT EXISTS idx_media_cat_rating ON public.media_items(category, effective_rating DESC);

-- Trigram fuzzy title index for instant search (supports typos!)
CREATE INDEX IF NOT EXISTS idx_media_clean_title_trgm ON public.media_items USING gin (clean_title gin_trgm_ops);

-- 4. Enable Row Level Security (RLS) with open read/write for ShowHub server
ALTER TABLE public.media_items ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "ShowHub Public Read" ON public.media_items;
CREATE POLICY "ShowHub Public Read" ON public.media_items
    FOR SELECT USING (true);

DROP POLICY IF EXISTS "ShowHub Service Full Access" ON public.media_items;
CREATE POLICY "ShowHub Service Full Access" ON public.media_items
    FOR ALL USING (true) WITH CHECK (true);
