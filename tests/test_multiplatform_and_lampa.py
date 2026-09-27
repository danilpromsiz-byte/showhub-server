import os
import sys
import json
import zipfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

class TestMultiplatformAndLampa(unittest.TestCase):

    def test_version_json_consistency(self):
        for vpath in [r"c:\WORK\VID\version.json", r"c:\WORK\VID\mediacenter\static\version.json"]:
            self.assertTrue(os.path.exists(vpath), f"File {vpath} does not exist")
            with open(vpath, "r", encoding="utf-8") as f:
                data = json.load(f)

            self.assertEqual(data["version_code"], 123)
            self.assertEqual(data["version_name"], "2.8.64")
            self.assertIn("ShowHub.apk", data["download_url"])

            # TV
            self.assertIn("tv", data)
            self.assertEqual(data["tv"]["version_code"], 123)
            self.assertIn("ShowHub.apk", data["tv"]["download_url"])

            # Mobile
            self.assertIn("mobile", data)
            self.assertEqual(data["mobile"]["version_code"], 123)
            self.assertIn("ShowHub-Mobile.apk", data["mobile"]["download_url"])

            # PC
            self.assertIn("pc", data)
            self.assertEqual(data["pc"]["version_code"], 123)
            self.assertIn("ShowHub-PC.zip", data["pc"]["download_url"])

    def test_distribution_packages(self):
        # TV APK
        tv_apk = r"c:\WORK\VID\mediacenter\static\ShowHub.apk"
        self.assertTrue(os.path.exists(tv_apk))
        self.assertGreater(os.path.getsize(tv_apk), 8_000_000)
        with zipfile.ZipFile(tv_apk, "r") as z:
            names = z.namelist()
            self.assertIn("AndroidManifest.xml", names)
            self.assertIn("classes.dex", names)

        # Mobile APK
        mob_apk = r"c:\WORK\VID\mediacenter\static\ShowHub-Mobile.apk"
        self.assertTrue(os.path.exists(mob_apk))
        self.assertGreater(os.path.getsize(mob_apk), 8_000_000)
        with zipfile.ZipFile(mob_apk, "r") as z:
            names = z.namelist()
            self.assertIn("AndroidManifest.xml", names)
            self.assertIn("classes.dex", names)

        # PC ZIP
        pc_zip = r"c:\WORK\VID\mediacenter\static\ShowHub-PC.zip"
        self.assertTrue(os.path.exists(pc_zip))
        self.assertGreater(os.path.getsize(pc_zip), 20_000_000)
        with zipfile.ZipFile(pc_zip, "r") as z:
            names = z.namelist()
            self.assertIn("ShowHub.exe", names)
            self.assertIn("showhub_config.json", names)
            self.assertIn("app.ico", names)
            self.assertIn("README.txt", names)

    def test_priority_hierarchy_rating(self):
        from mediacenter.core.media_registry import compute_effective_rating

        # Priority 1: Lampa
        self.assertEqual(compute_effective_rating(r_lampa=8.5, r_kp=7.0, r_rezka=6.0, r_imdb=7.5), 8.5)
        self.assertEqual(compute_effective_rating(r_lampa=9.1, r_kp=9.5), 9.1)

        # Priority 2: Kinopoisk (when Lampa is None/0)
        self.assertEqual(compute_effective_rating(r_lampa=None, r_kp=8.1, r_rezka=7.9, r_imdb=6.5), 8.1)
        self.assertEqual(compute_effective_rating(r_lampa=0.0, r_kp=7.8, r_rezka=8.5), 7.8)

        # Priority 3: HDRezka (when Lampa and KP are None)
        self.assertEqual(compute_effective_rating(r_lampa=None, r_kp=None, r_rezka=7.6, r_imdb=7.0), 7.6)

        # Priority 4: Others (IMDb)
        self.assertEqual(compute_effective_rating(r_lampa=None, r_kp=None, r_rezka=None, r_imdb=6.9), 6.9)

    def test_priority_hierarchy_age_limit(self):
        from mediacenter.core.media_registry import compute_effective_age_limit

        # Priority 1: Lampa
        self.assertEqual(compute_effective_age_limit(age_lampa="18+", age_kp="16+", age_rezka="12+"), "18+")

        # Priority 2: KP
        self.assertEqual(compute_effective_age_limit(age_lampa=None, age_kp="16+", age_rezka="18+"), "16+")

        # Priority 3: HDRezka
        self.assertEqual(compute_effective_age_limit(age_lampa=None, age_kp=None, age_rezka="18+"), "18+")

        # Priority 4: Other
        self.assertEqual(compute_effective_age_limit(age_lampa=None, age_kp=None, age_rezka=None, age_other="12+"), "12+")

    def test_lampa_popularity_and_catalog(self):
        from mediacenter.core.media_registry import media_registry

        # Query catalog sorted by popular
        items = media_registry.query_catalog(category="all", sort_by="popular", limit=10)
        self.assertGreater(len(items), 0)

        # Ensure popularity is monotonic non-increasing or top items have high popularity
        popularities = [it.get("lampa_popularity") or it.get("popularity") or 0.0 for it in items]
        for i in range(len(popularities) - 1):
            self.assertGreaterEqual(popularities[i], popularities[i+1], f"Item {i} ({popularities[i]}) < Item {i+1} ({popularities[i+1]})")

    def test_search_by_actor_and_director(self):
        import tempfile
        from mediacenter.core.media_registry import MediaRegistry

        with tempfile.NamedTemporaryFile(suffix=".db", delete=False) as tmp:
            tmp_db_path = tmp.name

        try:
            test_registry = MediaRegistry(db_path=tmp_db_path)
            test_item = {
                "id": "test_unique_actor_123",
                "title": "Тестовый Фильм Про Агентов",
                "clean_title": "тестовый фильм про агентов",
                "year": 2026,
                "director": "Кристофер ТестовыйНолан",
                "actors": "Леонардо ДиКаприо, Киллиан Мёрфи, Том Харди",
                "lampa_popularity": 850.0,
                "rating_lampa": 8.8,
                "source_name": "lampa"
            }
            test_registry.upsert_item(test_item)

            # Search by actor
            res_actor = test_registry.search("ДиКаприо")
            self.assertTrue(any("test_unique_actor_123" in r["id"] for r in res_actor), "Actor search failed")

            # Search by director
            res_dir = test_registry.search("ТестовыйНолан")
            self.assertTrue(any("test_unique_actor_123" in r["id"] for r in res_dir), "Director search failed")
        finally:
            try:
                os.remove(tmp_db_path)
            except Exception:
                pass

    def test_api_popular_and_platform_endpoints(self):
        from fastapi.testclient import TestClient
        from mediacenter.app import app

        client = TestClient(app)

        # 1. /api/popular
        pop_resp = client.get("/api/popular")
        self.assertEqual(pop_resp.status_code, 200)
        pop_data = pop_resp.json()
        self.assertIsInstance(pop_data, list)
        self.assertGreater(len(pop_data), 0)
        # Top item must have positive rating and valid title
        first = pop_data[0]
        self.assertIn("title", first)
        self.assertGreater(first.get("rating", 0), 0)

        # 2. /ShowHub-Mobile.apk
        mob_resp = client.head("/ShowHub-Mobile.apk")
        self.assertEqual(mob_resp.status_code, 200)
        self.assertEqual(mob_resp.headers.get("content-type"), "application/vnd.android.package-archive")

        # 3. /ShowHub-PC.zip
        pc_resp = client.head("/ShowHub-PC.zip")
        self.assertEqual(pc_resp.status_code, 200)
        self.assertEqual(pc_resp.headers.get("content-type"), "application/zip")

        # 4. /version.json
        v_resp = client.get("/version.json")
        self.assertEqual(v_resp.status_code, 200)
        v_data = v_resp.json()
        self.assertEqual(v_data["version_code"], 123)
        self.assertEqual(v_data["version_name"], "2.8.64")
        self.assertIn("mobile", v_data)
        self.assertIn("pc", v_data)
        self.assertIn("tv", v_data)

if __name__ == "__main__":
    unittest.main()
