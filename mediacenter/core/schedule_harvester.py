import requests
import re
from datetime import datetime, timezone
from typing import List, Dict, Any, Optional

RU_MONTHS = {
    1: "января", 2: "февраля", 3: "марта", 4: "апреля",
    5: "мая", 6: "июня", 7: "июля", 8: "августа",
    9: "сентября", 10: "октября", 11: "ноября", 12: "декабря"
}

def format_iso_to_ru(iso_str: str) -> str:
    """Formats '2024-07-18' or '2024-07-18T03:00:00+0000' to '18 июля 2024 г.'"""
    if not iso_str:
        return ""
    m = re.match(r'^(\d{4})-(\d{2})-(\d{2})', str(iso_str).strip())
    if m:
        y, mth, d = int(m.group(1)), int(m.group(2)), int(m.group(3))
        m_name = RU_MONTHS.get(mth, "")
        if m_name:
            return f"{d} {m_name} {y} г."
        return f"{d:02d}.{mth:02d}.{y}"
    return str(iso_str).strip()

def is_valid_calendar_date(d: str) -> bool:
    """Checks if a date string contains real calendar information rather than a generic status."""
    if not d:
        return False
    d_clean = d.strip().lower()
    if d_clean in ("вышла", "доступна", "дата уточняется", "ожидается", "в эфире", ""):
        return False
    return any(m in d_clean for m in ["январ", "феврал", "март", "апрел", "ма", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр"]) or bool(re.search(r'\d{4}', d_clean))

def get_myshows_schedule(title: str, year: Optional[int] = None, kp_id: Optional[str] = None, imdb_id: Optional[str] = None) -> List[Dict[str, Any]]:
    """Fetches TV episode schedule with exact dates from MyShows.me open API."""
    try:
        url = "https://api.myshows.me/v2/rpc/"
        headers = {"User-Agent": "ShowHub/1.0 (Android TV)", "Content-Type": "application/json"}
        
        # 1. Search for show
        p_search = {
            "jsonrpc": "2.0",
            "method": "shows.Search",
            "params": {"query": title},
            "id": 1
        }
        res = requests.post(url, json=p_search, headers=headers, timeout=4)
        if res.status_code != 200:
            return []
        
        shows = res.json().get("result", [])
        if not shows:
            return []
        
        target_show_id = None
        # Try matching by Kinopoisk ID first
        if kp_id and str(kp_id).isdigit():
            for s in shows:
                if str(s.get("kinopoiskId") or "") == str(kp_id):
                    target_show_id = s.get("id")
                    break
        
        # Try matching by IMDb ID
        if not target_show_id and imdb_id:
            clean_imdb = str(imdb_id).replace("tt", "").strip()
            for s in shows:
                if str(s.get("imdbId") or "") in (str(imdb_id), clean_imdb):
                    target_show_id = s.get("id")
                    break
        
        # Try matching by Year
        if not target_show_id and year:
            for s in shows:
                if s.get("year") and abs(int(s["year"]) - int(year)) <= 1:
                    target_show_id = s.get("id")
                    break
        
        # Default to first show if none matched
        if not target_show_id and shows:
            target_show_id = shows[0].get("id")
        
        if not target_show_id:
            return []
        
        # 2. Fetch episodes with air dates
        p_eps = {
            "jsonrpc": "2.0",
            "method": "shows.GetById",
            "params": {"showId": target_show_id, "withEpisodes": True},
            "id": 2
        }
        res_eps = requests.post(url, json=p_eps, headers=headers, timeout=5)
        if res_eps.status_code != 200:
            return []
        
        show_data = res_eps.json().get("result", {})
        episodes = show_data.get("episodes", [])
        if not episodes:
            return []
        
        today_iso = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        schedule = []
        for ep in episodes:
            s_num = ep.get("seasonNumber", 1)
            e_num = ep.get("episodeNumber", 1)
            if s_num == 0 and e_num == 0:
                continue
            
            raw_date = ep.get("airDate") or ""
            fmt_date = format_iso_to_ru(raw_date)
            
            clean_date_iso = raw_date[:10] if len(raw_date) >= 10 else ""
            if clean_date_iso and clean_date_iso <= today_iso:
                status = "Вышла"
            elif clean_date_iso and clean_date_iso > today_iso:
                status = "Ожидается"
            else:
                status = "Ожидается"
            
            ep_title = ep.get("title") or f"Серия {e_num}"
            schedule.append({
                "episode": f"{s_num} сезон {e_num} серия",
                "season": s_num,
                "episode_num": e_num,
                "title": ep_title,
                "date": fmt_date or "Дата уточняется",
                "raw_date": clean_date_iso,
                "status": status,
                "source": "myshows"
            })
        
        return schedule
    except Exception:
        return []

def get_tvmaze_schedule(title: str) -> List[Dict[str, Any]]:
    """Fetches TV episode schedule with exact dates from TVMaze open API (for foreign series)."""
    try:
        url = f"https://api.tvmaze.com/singlesearch/shows?q={requests.utils.quote(title)}&embed=episodes"
        res = requests.get(url, timeout=4)
        if res.status_code != 200:
            return []
        
        data = res.json()
        episodes = data.get("_embedded", {}).get("episodes", [])
        if not episodes:
            return []
        
        today_iso = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        schedule = []
        for ep in episodes:
            s_num = ep.get("season", 1)
            e_num = ep.get("number", 1)
            airdate = ep.get("airdate") or ""
            fmt_date = format_iso_to_ru(airdate)
            
            if airdate and airdate <= today_iso:
                status = "Вышла"
            elif airdate and airdate > today_iso:
                status = "Ожидается"
            else:
                status = "Ожидается"
            
            ep_title = ep.get("name") or f"Episode {e_num}"
            schedule.append({
                "episode": f"{s_num} сезон {e_num} серия",
                "season": s_num,
                "episode_num": e_num,
                "title": ep_title,
                "date": fmt_date or "Дата уточняется",
                "raw_date": airdate,
                "status": status,
                "source": "tvmaze"
            })
        
        return schedule
    except Exception:
        return []

def harvest_tv_schedule(
    title: str,
    year: Optional[int] = None,
    kp_id: Optional[str] = None,
    imdb_id: Optional[str] = None,
    existing_schedule: Optional[List[Dict[str, Any]]] = None,
    tmdb_schedule: Optional[List[Dict[str, Any]]] = None,
    original_title: Optional[str] = None
) -> List[Dict[str, Any]]:
    """Aggregates and completes TV episode schedule from multiple open sources:
    TMDb -> MyShows.me -> TVMaze -> HDRezka table.
    Guarantees every season and episode receives a real calendar date and authentic status.
    """
    merged_map: Dict[tuple, Dict[str, Any]] = {}

    def _extract_s_e(item: Dict[str, Any]) -> tuple:
        s_num = item.get("season")
        e_num = item.get("episode_num")
        if not s_num or not e_num:
            ep_text = item.get("episode", "")
            m = re.search(r'(?:(\d+)\s+сезон)?.*?(\d+)\s+серия', ep_text)
            if m:
                s_num = int(m.group(1)) if m.group(1) else (s_num or 1)
                e_num = int(m.group(2))
        return (int(s_num or 1), int(e_num or 1))

    # 1. Populate from TMDb schedule if available
    if tmdb_schedule:
        for it in tmdb_schedule:
            k = _extract_s_e(it)
            merged_map[k] = it.copy()

    # 2. Enrich/overlay existing schedule (e.g. HDRezka live airing season)
    if existing_schedule:
        for it in existing_schedule:
            k = _extract_s_e(it)
            if k in merged_map:
                cur = merged_map[k]
                if is_valid_calendar_date(it.get("date", "")) and not is_valid_calendar_date(cur.get("date", "")):
                    cur["date"] = it["date"]
                if it.get("status"):
                    cur["status"] = it["status"]
                if it.get("title") and not cur.get("title"):
                    cur["title"] = it["title"]
            else:
                merged_map[k] = it.copy()

    # 3. Check if we need further enrichment from MyShows.me (e.g. for Russian series or missing dates)
    needs_more = not merged_map or any(not is_valid_calendar_date(v.get("date", "")) for v in merged_map.values())
    if needs_more and title:
        myshows_data = get_myshows_schedule(title, year=year, kp_id=kp_id, imdb_id=imdb_id)
        if myshows_data:
            for it in myshows_data:
                k = _extract_s_e(it)
                if k in merged_map:
                    cur = merged_map[k]
                    if not is_valid_calendar_date(cur.get("date", "")) and is_valid_calendar_date(it.get("date", "")):
                        cur["date"] = it["date"]
                        cur["raw_date"] = it.get("raw_date")
                        cur["status"] = it.get("status", cur.get("status"))
                    if it.get("title") and (not cur.get("title") or "серия" in cur.get("title", "").lower()):
                        cur["title"] = it["title"]
                else:
                    merged_map[k] = it.copy()

    # 4. Fallback to TVMaze if still empty or missing dates (especially effective for foreign series)
    needs_more = not merged_map or any(not is_valid_calendar_date(v.get("date", "")) for v in merged_map.values())
    if needs_more:
        search_query = original_title or title
        if search_query:
            tvmaze_data = get_tvmaze_schedule(search_query)
            if tvmaze_data:
                for it in tvmaze_data:
                    k = _extract_s_e(it)
                    if k in merged_map:
                        cur = merged_map[k]
                        if not is_valid_calendar_date(cur.get("date", "")) and is_valid_calendar_date(it.get("date", "")):
                            cur["date"] = it["date"]
                            cur["raw_date"] = it.get("raw_date")
                            cur["status"] = it.get("status", cur.get("status"))
                        if it.get("title") and not cur.get("title"):
                            cur["title"] = it["title"]
                    else:
                        merged_map[k] = it.copy()

    # 5. Final pass: date formatting & year fallback
    today_iso = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    year_str = f"{year} г." if year else "Дата уточняется"

    for (s_n, e_n), item in merged_map.items():
        if not item.get("episode"):
            item["episode"] = f"{s_n} сезон {e_n} серия"
        item["season"] = s_n
        item["episode_num"] = e_n

        raw_d = item.get("raw_date") or ""
        cur_d = item.get("date") or ""

        if not is_valid_calendar_date(cur_d):
            if raw_d:
                item["date"] = format_iso_to_ru(raw_d)
            else:
                item["date"] = year_str

        # Update status if raw_date is available
        if raw_d and len(raw_d) >= 10:
            item["status"] = "Вышла" if raw_d[:10] <= today_iso else "Ожидается"
        elif not item.get("status") or item["status"] == "✓":
            item["status"] = "Вышла"

    # Sort descending: newest season and episode first
    sorted_schedule = sorted(
        merged_map.values(),
        key=lambda x: (x.get("season", 1), x.get("episode_num", 1)),
        reverse=True
    )
    return sorted_schedule
