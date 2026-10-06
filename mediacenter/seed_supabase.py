"""
Seed local SQLite database (25,000+ items) into Supabase PostgreSQL.
Run:
    python mediacenter/seed_supabase.py
"""
import os
import sys
import time
import sqlite3

# Set working directory to project root
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)

from mediacenter.core.supabase_manager import supabase_manager
from mediacenter.core.media_registry import media_registry

def main():
    print("=" * 60)
    print("ShowHub -> Supabase Database Migration & Seeding Tool")
    print("=" * 60)

    if not supabase_manager.is_configured():
        print("[ERROR] Supabase is not configured!")
        print("Please provide credentials in 'mediacenter/data/supabase.json':")
        print("""{
  "supabase_url": "https://xyzcompany.supabase.co",
  "supabase_key": "eyJhbGciOi..."
}""")
        print("Or set environment variables: SUPABASE_URL and SUPABASE_KEY")
        return

    print(f"[*] Target Supabase URL: {supabase_manager._url}")
    print("[*] Testing Supabase connection...")
    test_res = supabase_manager.test_connection()
    if not test_res.get("ok"):
        print(f"[ERROR] Connection test failed: {test_res.get('error')}")
        return

    if not test_res.get("table_exists"):
        print("[WARNING] Table 'media_items' does not exist in Supabase yet!")
        print("Please execute 'mediacenter/data/supabase_schema.sql' in your Supabase SQL Editor first.")
        return

    print("[OK] Supabase connected and table 'media_items' is ready.")
    db_path = os.path.join(ROOT, "mediacenter", "data", "media_registry.db")
    if not os.path.exists(db_path):
        print(f"[ERROR] Local SQLite database not found at {db_path}")
        return

    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    total_local = conn.execute("SELECT COUNT(*) FROM media_items;").fetchone()[0]
    print(f"[*] Found {total_local} items in local SQLite database.")

    cur = conn.cursor()
    cur.execute("SELECT * FROM media_items ORDER BY popularity DESC, updated_at DESC;")

    batch_size = 200
    batch = []
    synced = 0
    t0 = time.time()

    while True:
        row = cur.fetchone()
        if row:
            batch.append(dict(row))
        if len(batch) >= batch_size or (not row and batch):
            cnt = supabase_manager.push_items_batch(batch)
            synced += cnt
            pct = (synced / total_local * 100) if total_local else 100
            elapsed = time.time() - t0
            rate = synced / max(elapsed, 0.001)
            print(f"[*] Synced {synced}/{total_local} items ({pct:.1f}%) - {rate:.1f} items/sec", end="\r")
            batch = []
        if not row:
            break

    cur.close()
    conn.close()

    total_remote = supabase_manager.get_remote_count()
    print("\n" + "=" * 60)
    print(f"[SUCCESS] Seeding completed in {time.time() - t0:.1f}s!")
    print(f"[*] Total items in Supabase: {total_remote}")
    print("=" * 60)

if __name__ == "__main__":
    main()
