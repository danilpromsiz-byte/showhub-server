"""
Unified Cloud Database Manager for ShowHub.
Intelligently prioritizes Supabase (PostgreSQL / PostgREST) without quota limits,
with fallback to Turso (libSQL) if configured.
"""
import logging
from typing import List, Dict, Any, Optional

from .supabase_manager import supabase_manager
from .turso_manager import turso_manager

logger = logging.getLogger("cloud_manager")


class CloudManager:
    @property
    def provider(self) -> str:
        if supabase_manager.is_configured():
            return "supabase"
        if turso_manager.is_configured():
            return "turso"
        return "none"

    def is_configured(self) -> bool:
        return supabase_manager.is_configured() or turso_manager.is_configured()

    def push_items_batch(self, rows: List[Dict[str, Any]]) -> int:
        if supabase_manager.is_configured():
            try:
                return supabase_manager.push_items_batch(rows)
            except Exception as e:
                logger.error(f"[CloudManager] Supabase push error: {e}")
        elif turso_manager.is_configured():
            try:
                return turso_manager.push_items_batch(rows)
            except Exception as e:
                logger.error(f"[CloudManager] Turso push error: {e}")
        return 0

    def pull_items_delta(self, since_updated_at: float = 0.0, limit: int = 5000) -> List[Dict[str, Any]]:
        if supabase_manager.is_configured():
            try:
                return supabase_manager.pull_items_delta(since_updated_at=since_updated_at, limit=limit)
            except Exception as e:
                logger.error(f"[CloudManager] Supabase pull error: {e}")
        elif turso_manager.is_configured():
            try:
                return turso_manager.pull_items_delta(since_updated_at=since_updated_at, limit=limit)
            except Exception as e:
                logger.error(f"[CloudManager] Turso pull error: {e}")
        return []

    def get_remote_count(self) -> int:
        if supabase_manager.is_configured():
            return supabase_manager.get_remote_count()
        if turso_manager.is_configured():
            return turso_manager.get_remote_count()
        return 0

    def get_item(self, item_id: str) -> Optional[Dict[str, Any]]:
        if supabase_manager.is_configured():
            return supabase_manager.get_item(item_id)
        return None

    def get_status(self) -> Dict[str, Any]:
        """Provides status summary for admin/diagnostics API."""
        prov = self.provider
        return {
            "active_provider": prov,
            "supabase": {
                "configured": supabase_manager.is_configured(),
                "url": (supabase_manager._url[:15] + "..." + supabase_manager._url[-10:]) if supabase_manager._url else None,
                "remote_count": supabase_manager.get_remote_count() if supabase_manager.is_configured() else 0,
            },
            "turso": {
                "configured": turso_manager.is_configured(),
                "url": (turso_manager._url[:15] + "..." + turso_manager._url[-10:]) if turso_manager._url else None,
                "remote_count": turso_manager.get_remote_count() if turso_manager.is_configured() else 0,
            }
        }


cloud_manager = CloudManager()
