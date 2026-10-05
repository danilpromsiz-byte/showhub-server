"""
One-click migration script: Syncs all local media_registry items to Turso Cloud DB.
Usage:
    python -m mediacenter.scripts.sync_to_turso
Or:
    python mediacenter/scripts/sync_to_turso.py --url libsql://... --token eyJ...
"""
import os
import sys
import time
import sqlite3
import argparse
import logging

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("sync_to_turso")

# Ensure project root is in sys.path
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from mediacenter.core.turso_manager import turso_manager
from mediacenter.core.media_registry import DB_PATH


def sync_local_to_turso(url: str = None, token: str = None, batch_size: int = 150):
    if url:
        os.environ["TURSO_DATABASE_URL"] = url
    if token:
        os.environ["TURSO_AUTH_TOKEN"] = token

    turso_manager._load_config()

    if not turso_manager.is_configured():
        logger.error("Turso is not configured! Please provide TURSO_DATABASE_URL and TURSO_AUTH_TOKEN in environment or as arguments.")
        logger.info("Example: python mediacenter/scripts/sync_to_turso.py --url libsql://showhub-media.turso.io --token eyJhbGci...")
        return False

    logger.info(f"Target Turso DB: {turso_manager._url}")
    logger.info("Initializing schema and FTS5 index in Turso...")
    if not turso_manager.init_schema():
        logger.error("Failed to initialize Turso schema.")
        return False

    if not os.path.exists(DB_PATH):
        logger.error(f"Local database not found at {DB_PATH}")
        return False

    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    total_local = conn.execute("SELECT COUNT(*) FROM media_items;").fetchone()[0]
    logger.info(f"Local media_registry has {total_local} items. Beginning cloud synchronization...")

    cur = conn.execute("SELECT * FROM media_items;")
    batch = []
    synced = 0
    start_time = time.time()

    while True:
        rows = cur.fetchmany(batch_size)
        if not rows:
            break

        items = [dict(r) for r in rows]
        inserted = turso_manager.push_items_batch(items)
        synced += inserted
        pct = (synced / total_local * 100) if total_local > 0 else 100
        elapsed = time.time() - start_time
        speed = synced / elapsed if elapsed > 0 else 0
        logger.info(f"Synced {synced}/{total_local} items ({pct:.1f}%) [Speed: {speed:.1f} items/sec]")

    logger.info(f"Successfully migrated {synced} items to Turso in {time.time() - start_time:.1f} seconds!")
    remote_count = turso_manager.get_remote_count()
    logger.info(f"Verified remote count in Turso: {remote_count} items.")
    return True


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Sync local SQLite media items to Turso Cloud")
    parser.add_argument("--url", help="Turso database URL (libsql://...)")
    parser.add_argument("--token", help="Turso database auth token")
    parser.add_argument("--batch-size", type=int, default=150, help="Batch size for inserts")
    args = parser.parse_args()

    sync_local_to_turso(url=args.url, token=args.token, batch_size=args.batch_size)
