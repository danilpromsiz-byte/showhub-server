"""
Canary Health Checker & Source Change Alert System.
Periodically probes each decompiled source endpoint with real queries.
Detects API changes, anti-bot protections, mirror blocks, and schema breaks.
Generates structured health reports with visual warnings for sources needing rework.
"""
import time
import json
import os
from concurrent.futures import ThreadPoolExecutor
from typing import List, Dict, Any
from ..sources.base import CanaryReport
from ..sources.bazon import BazonSource
from ..sources.delivembd import DelivembdSource
from ..sources.torrents import TorrentsSource
from ..sources.hdrezka import HDRezkaSource
from ..sources.videocdn import VideoCDNSource
from ..sources.filmix import FilmixSource
from ..sources.anilibria import AnilibriaSource
from ..sources.zona import ZonaSource

class HealthChecker:
    STATUS_FILE = os.path.join(os.path.dirname(__file__), "..", "health_status.json")

    def __init__(self):
        self.sources = [
            BazonSource(),
            DelivembdSource(),
            TorrentsSource(),
            HDRezkaSource(),
            VideoCDNSource(),
            FilmixSource(),
            AnilibriaSource(),
            ZonaSource(),
        ]
        self.last_reports: Dict[str, CanaryReport] = {}
        self.last_check_time = 0

    def _probe_source(self, source) -> CanaryReport:
        rep = None
        for attempt in range(2):
            try:
                rep = source.canary_test()
                if rep.status == "OK":
                    return rep
                if attempt == 0:
                    time.sleep(1)
            except Exception as e:
                if attempt == 0:
                    time.sleep(1)
                else:
                    return CanaryReport(
                        source_name=source.name,
                        is_active=False,
                        status="CHANGED / BROKEN",
                        latency_ms=10000,
                        message=f"Timeout or unhandled exception: {str(e)}",
                        needs_rework=True,
                        endpoint_tested=source.name,
                        last_tested=time.time()
                    )
        return rep if rep else source.canary_test()

    def run_checks(self) -> List[CanaryReport]:
        reports = []
        from concurrent.futures import as_completed
        with ThreadPoolExecutor(max_workers=len(self.sources)) as executor:
            future_to_source = {executor.submit(self._probe_source, s): s for s in self.sources}
            try:
                for future in as_completed(future_to_source, timeout=20):
                    try:
                        report = future.result()
                        reports.append(report)
                        self.last_reports[report.source_name] = report
                    except Exception as e:
                        src = future_to_source[future]
                        rep = CanaryReport(
                            source_name=src.name,
                            is_active=False,
                            status="CHANGED / BROKEN",
                            latency_ms=10000,
                            message=f"Probe failed: {str(e)}",
                            needs_rework=True,
                            endpoint_tested=src.name,
                            last_tested=time.time()
                        )
                        reports.append(rep)
                        self.last_reports[src.name] = rep
            except Exception:
                pass

        self.last_check_time = time.time()
        self.save_status()
        return reports

    def get_summary(self) -> Dict[str, Any]:
        if not self.last_reports:
            self.run_checks()

        reports_list = list(self.last_reports.values())
        ok_count = sum(1 for r in reports_list if r.status == "OK")
        rework_count = sum(1 for r in reports_list if r.needs_rework)

        return {
            "total_sources": len(reports_list),
            "ok_sources": ok_count,
            "broken_sources": rework_count,
            "has_warnings": rework_count > 0,
            "last_checked": self.last_check_time,
            "reports": [r.model_dump() for r in reports_list]
        }

    def save_status(self):
        try:
            with open(self.STATUS_FILE, "w", encoding="utf-8") as f:
                json.dump(self.get_summary(), f, indent=2, ensure_ascii=False)
        except Exception:
            pass

health_checker = HealthChecker()
