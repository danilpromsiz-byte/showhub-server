/**
 * ShowHub TV - Frontend Application & Remote Control Handler
 * Supports 10-foot D-Pad navigation, multi-source stream aggregation,
 * embedded HLS playback, and real-time Canary Health monitoring.
 */

// Application Version & Mandatory Update State
const CURRENT_APP_VERSION = "2.8.5";
const CURRENT_APP_VERSION_CODE = 64;
window.isForceUpdateActive = false;

// Migrate legacy local PC IP addresses to cloud server
try {
    const savedServer = localStorage.getItem("showhub_server");
    if (!savedServer || savedServer.includes("192.168.") || savedServer.includes("localhost") || savedServer.includes("127.0.0.1") || savedServer.includes(":8000")) {
        console.log("Migrating server from legacy IP to Render cloud:", savedServer);
        localStorage.setItem("showhub_server", "https://showhub-server.onrender.com");
    }
} catch (e) {}

// Universal API Base Interceptor for Android TV (supports both file:/// assets and http://)
const originalFetch = window.fetch;
window.fetch = function(url, options) {
    if (typeof url === "string" && url.startsWith("/api/")) {
        const host = (window.location.protocol === "file:") 
            ? (localStorage.getItem("showhub_server") || "https://showhub-server.onrender.com")
            : "";
        url = host.replace(/\/+$/, "") + url;
    }
    return originalFetch.call(this, url, options);
};

// Safe Year Parser for API Queries (never sends None, null, or invalid strings)
function getValidYear(yr) {
    if (!yr) return "";
    const s = String(yr).trim();
    if (s.toLowerCase() === "none" || s.toLowerCase() === "null" || s.toLowerCase() === "undefined") return "";
    if (/^\d{4}$/.test(s)) return s;
    const m = s.match(/\b(19\d\d|20\d\d)\b/);
    return m ? m[1] : "";
}

// Android TV Remote Back Key Handler
window.handleTvBack = function() {
    if (window.isForceUpdateActive) {
        const btnExit = document.getElementById("btn-force-update-exit");
        if (document.activeElement !== btnExit) {
            btnExit?.focus();
        } else {
            if (window.AndroidBridge && typeof window.AndroidBridge.closeApp === "function") {
                window.AndroidBridge.closeApp();
            }
        }
        return true;
    }
    const playerModal = document.getElementById("player-modal");
    if (playerModal && playerModal.style.display !== "none") {
        if (typeof handlePlayerBack === "function" && handlePlayerBack()) {
            return true;
        }
        closePlayer();
        return true;
    }
    const settingsModal = document.getElementById("settings-modal");
    if (settingsModal && settingsModal.style.display !== "none") {
        closeSettingsModal();
        return true;
    }
    const viewDetails = document.getElementById("view-details");
    if (currentView === "details" || (viewDetails && viewDetails.style.display !== "none")) {
        closeMediaModal();
        return true;
    }
    const mediaModal = document.getElementById("media-modal");
    if (mediaModal && mediaModal.style.display !== "none") {
        closeMediaModal();
        return true;
    }
    const searchModal = document.getElementById("search-modal");
    if (searchModal && searchModal.style.display !== "none") {
        closeSearchModal();
        return true;
    }
    const serverModal = document.getElementById("server-connect-modal");
    if (serverModal && serverModal.style.display !== "none") {
        serverModal.style.display = "none";
        return true;
    }
    if (currentView === "favorites" || currentView === "history" || currentView === "search" || currentView === "health" || currentView === "account") {
        switchView("catalog");
        const navTarget = document.getElementById("nav-search") || document.querySelector(".topbar .nav-item");
        if (navTarget) navTarget.focus();
        return true;
    }
    return false;
};

/* =========================================================
   Storage & User Settings Engine (Local Persistence)
   ========================================================= */
const WATCH_HISTORY_KEY = "showhub_watch_history";
const SETTINGS_KEY = "showhub_user_settings";
const FAVORITES_KEY = "showhub_favorites";

// Fast In-Memory Lazy Preload Caches for Instant TV Transitions
const detailsPreloadCache = new Map();
const streamPreloadCache = new Map();
let cardPreloadTimer = null;
let nextEpPreloadTimer = null;
let currentSkipTimes = { introStart: null, introEnd: null, outroStart: null, outroEnd: null };
let filmixProfile = null;

// Global exports for verification and bridge integration
window.detailsPreloadCache = detailsPreloadCache;
window.streamPreloadCache = streamPreloadCache;
window.currentSkipTimes = currentSkipTimes;
window.filmixProfile = filmixProfile;

function getFavorites() {
    try {
        return JSON.parse(localStorage.getItem(FAVORITES_KEY) || "{}");
    } catch (e) {
        return {};
    }
}

function isFavorite(mediaId) {
    if (!mediaId) return false;
    const favs = getFavorites();
    return Boolean(favs[String(mediaId)]);
}

function toggleFavorite(mediaItem) {
    if (!mediaItem || !mediaItem.id) return false;
    const idKey = String(mediaItem.id);
    const favs = getFavorites();
    let isNowFav = false;
    if (favs[idKey]) {
        delete favs[idKey];
        isNowFav = false;
        showPlayerToast("Удалено из избранного");
    } else {
        favs[idKey] = {
            id: idKey,
            source: mediaItem.source || "videocdn",
            kpId: mediaItem.kpId || "",
            title: mediaItem.title || "",
            poster: mediaItem.poster || "",
            year: mediaItem.year || "",
            isSeries: Boolean(mediaItem.isSeries),
            addedAt: Date.now()
        };
        isNowFav = true;
        showPlayerToast("★ Добавлено в избранное");
    }
    try {
        localStorage.setItem(FAVORITES_KEY, JSON.stringify(favs));
    } catch (e) {}

    updateFavoriteButtonUI(isNowFav);

    if (currentView === "favorites") {
        renderFavoritesView();
    }
    return isNowFav;
}

function updateFavoriteButtonUI(fav) {
    const btn = document.getElementById("btn-modal-fav");
    if (!btn) return;
    if (fav) {
        btn.classList.add("active");
        btn.textContent = "★ В избранном";
    } else {
        btn.classList.remove("active");
        btn.textContent = "★ В избранное";
    }
}

function renderFavoritesView() {
    const grid = document.getElementById("favorites-grid");
    const countSubtitle = document.getElementById("favorites-count-subtitle");
    if (!grid) return;

    const favsObj = getFavorites();
    const items = Object.values(favsObj).sort((a, b) => (b.addedAt || 0) - (a.addedAt || 0));

    if (countSubtitle) {
        countSubtitle.textContent = `${items.length} видео`;
    }

    if (items.length === 0) {
        grid.innerHTML = `
            <div style="grid-column: 1 / -1; text-align: center; padding: 60px 20px; color: var(--text-muted);">
                <div style="font-size: 40px; margin-bottom: 12px; color: #fbbf24;">★</div>
                <h3 style="font-size: 18px; color: #f8fafc; margin-bottom: 8px;">В избранном пока пусто</h3>
                <p style="font-size: 14px; max-width: 480px; margin: 0 auto;">Открывайте карточки понравившихся фильмов и нажимайте «★ В избранное».</p>
            </div>
        `;
        return;
    }

    const adapted = items.map(f => ({
        id: f.id,
        source_name: f.source || "videocdn",
        kinopoisk_id: f.kpId,
        title: f.title,
        poster: f.poster,
        year: f.year,
        is_series: f.isSeries,
        rating_kp: f.rating_kp || null
    }));

    renderMediaCards(adapted, grid);
}

async function checkFavoritesEpisodeUpdates() {
    try {
        const favsObj = getFavorites();
        const favList = Object.values(favsObj).filter(f => f.isSeries);
        if (favList.length === 0) return;

        const payload = favList.map(f => ({
            id: f.id,
            title: f.title,
            source: f.source || "hdrezka",
            is_series: true,
            season: f.lastKnownSeason || 1,
            episode: f.lastKnownEpisode || 0
        }));

        const res = await fetch("/api/favorites/check-updates", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        if (!res.ok) return;
        const updates = await res.json();
        if (updates && updates.length > 0) {
            let hasNew = false;
            updates.forEach(upd => {
                const key = String(upd.id);
                if (favsObj[key]) {
                    const prevE = favsObj[key].lastKnownEpisode || 0;
                    if (upd.latest_episode > prevE) {
                        favsObj[key].hasNewEpisode = true;
                        favsObj[key].latestAvailableSeason = upd.latest_season;
                        favsObj[key].latestAvailableEpisode = upd.latest_episode;
                        hasNew = true;
                        showPlayerToast(`🔔 Новая серия! «${upd.title}» S${upd.latest_season}E${upd.latest_episode} доступна!`);
                    }
                }
            });

            const badge = document.getElementById("fav-new-badge");
            if (badge) badge.style.display = hasNew ? "inline-block" : "none";

            if (hasNew) {
                try { localStorage.setItem(FAVORITES_KEY, JSON.stringify(favsObj)); } catch (e) {}
                if (currentView === "favorites") {
                    renderFavoritesView();
                }
            }
        }
    } catch (e) {
        console.warn("Favorites update check failed:", e);
    }
}

const THEME_KEY = "showhub_theme";

function applyTheme(themeName) {
    const validThemes = ["cyan", "emerald", "amber", "ruby", "amethyst", "sapphire"];
    const theme = validThemes.includes(themeName) ? themeName : "cyan";
    document.documentElement.setAttribute("data-theme", theme);
    document.body.setAttribute("data-theme", theme);
    try {
        localStorage.setItem(THEME_KEY, theme);
    } catch (e) {}
    const sel = document.getElementById("pref-theme");
    if (sel && sel.value !== theme) {
        sel.value = theme;
    }
}

function initThemeEngine() {
    let savedTheme = "cyan";
    try {
        savedTheme = localStorage.getItem(THEME_KEY) || "cyan";
    } catch (e) {}
    applyTheme(savedTheme);

    const sel = document.getElementById("pref-theme");
    if (sel) {
        sel.value = savedTheme;
        sel.addEventListener("change", () => {
            applyTheme(sel.value);
            saveSettings({ theme: sel.value });
        });
    }
}

// Immediate theme execution to prevent FOUC (flash of unstyled theme)
try {
    const earlyTheme = localStorage.getItem("showhub_theme") || "cyan";
    document.documentElement.setAttribute("data-theme", earlyTheme);
} catch (e) {}

function getSettings() {
    try {
        const s = JSON.parse(localStorage.getItem(SETTINGS_KEY) || "{}");
        return {
            player: s.player || "internal", // Default: Internal ShowHub Video Player
            quality: s.quality || "1080p",
            voice: s.voice || "any",
            voiceCustom: s.voiceCustom || "",
            autoSelect: (s.autoSelect !== undefined) ? s.autoSelect : true,
            theme: s.theme || localStorage.getItem(THEME_KEY) || "cyan"
        };
    } catch (e) {
        return { player: "internal", quality: "1080p", voice: "any", voiceCustom: "", autoSelect: true, theme: "cyan" };
    }
}

function saveSettings(newSettings) {
    try {
        const cur = getSettings();
        const updated = Object.assign(cur, newSettings);
        localStorage.setItem(SETTINGS_KEY, JSON.stringify(updated));
    } catch (e) {}
}

function getWatchHistory() {
    try {
        return JSON.parse(localStorage.getItem(WATCH_HISTORY_KEY) || "{}");
    } catch (e) {
        return {};
    }
}

function getMediaStorageKey(item) {
    if (!item) return null;
    const kp = item.kpId || item.kinopoisk_id;
    if (kp && String(kp) !== "null" && String(kp) !== "undefined" && String(kp) !== "0") {
        return `kp_${kp}`;
    }
    const id = item.id;
    const src = item.source || item.source_name || "media";
    if (id && String(id) !== "null" && String(id) !== "undefined" && String(id) !== "0") {
        return `${src}_${id}`;
    }
    if (item.title) {
        return `title_${String(item.title).trim().toLowerCase()}_${item.year || ''}`;
    }
    return null;
}

function getMediaProgress(mediaId, kpId, title, source, year) {
    if (!mediaId && !kpId && !title) return null;
    const hist = getWatchHistory();
    if (!hist) return null;

    const kid = (kpId && String(kpId) !== "null" && String(kpId) !== "undefined" && String(kpId) !== "0") ? String(kpId) : null;
    const mid = (mediaId && String(mediaId) !== "null" && String(mediaId) !== "undefined" && String(mediaId) !== "0") ? String(mediaId) : null;
    const src = source || "media";

    // 1. Direct canonical key lookups (Highest priority)
    if (kid && hist[`kp_${kid}`]) return hist[`kp_${kid}`];
    if (mid && src && src !== "media" && hist[`${src}_${mid}`]) return hist[`${src}_${mid}`];
    if (mid && hist[mid]) {
        const cand = hist[mid];
        if (cand && (!cand.source || cand.source === src || !src || src === "media")) {
            return cand;
        }
    }

    // 2. Exact match by kpId across all records
    if (kid) {
        for (const k in hist) {
            const item = hist[k];
            if (item && (String(item.kpId) === kid || String(item.id) === `kp_${kid}`)) {
                return item;
            }
        }
    }

    // 3. Exact match by source + id
    if (mid && src && src !== "media") {
        for (const k in hist) {
            const item = hist[k];
            if (item && item.source === src && (String(item.id) === mid || String(item.rawId) === mid || String(item.id) === `${src}_${mid}`)) {
                return item;
            }
        }
    }

    // 4. Strict Title + Year match (Only if title is >= 2 chars, never match empty string!)
    if (title && String(title).trim().length >= 2) {
        const norm = String(title).trim().toLowerCase();
        for (const k in hist) {
            const item = hist[k];
            if (item && item.title) {
                const itemNorm = String(item.title).trim().toLowerCase();
                if (itemNorm === norm) {
                    if (year && item.year) {
                        const y1 = parseInt(year);
                        const y2 = parseInt(item.year);
                        if (!isNaN(y1) && !isNaN(y2) && Math.abs(y1 - y2) > 1) {
                            continue;
                        }
                    }
                    return item;
                }
            }
        }
    }
    return null;
}

function saveMediaProgress(mediaItem, updateData) {
    if (!mediaItem) return;
    try {
        const hist = getWatchHistory();
        const idKey = getMediaStorageKey(mediaItem) || String(mediaItem.id || mediaItem.kpId);
        if (!idKey) return;

        const effectiveSource = activeSource || mediaItem.source || "filmix";
        const effectiveQuality = selectedStream?.quality || "1080p";
        const effectiveAudioId = activeTranslatorId || "";

        const existing = hist[idKey] || {
            id: idKey,
            rawId: mediaItem.id || "",
            source: effectiveSource,
            kpId: mediaItem.kpId || "",
            title: mediaItem.title || "",
            poster: mediaItem.poster || "",
            year: mediaItem.year || "",
            isSeries: Boolean(mediaItem.isSeries),
            season: 1,
            episode: 1,
            quality: effectiveQuality,
            audio_id: effectiveAudioId,
            positionSec: 0,
            durationSec: 0,
            percentage: 0,
            watched: false,
            episodesWatched: {}
        };

        const merged = Object.assign(existing, updateData, {
            updatedAt: Date.now(),
            source: effectiveSource || existing.source,
            quality: effectiveQuality || existing.quality,
            audio_id: (effectiveAudioId !== undefined) ? effectiveAudioId : existing.audio_id,
            title: mediaItem.title || existing.title,
            poster: mediaItem.poster || existing.poster,
            year: mediaItem.year || existing.year,
            kpId: mediaItem.kpId || existing.kpId,
            isSeries: (mediaItem.isSeries !== undefined) ? Boolean(mediaItem.isSeries) : existing.isSeries
        });

        if (merged.durationSec > 0 && merged.positionSec >= 0) {
            merged.percentage = Math.min(100, Math.round((merged.positionSec / merged.durationSec) * 100));
            if (merged.percentage >= 90) {
                merged.watched = true;
            }
        }

        if (merged.isSeries && merged.season && merged.episode && merged.watched) {
            if (!merged.episodesWatched) merged.episodesWatched = {};
            merged.episodesWatched[`${merged.season}_${merged.episode}`] = true;
        }

        hist[idKey] = merged;
        localStorage.setItem(WATCH_HISTORY_KEY, JSON.stringify(hist));
        updateHistoryBadgesOnCards(idKey, merged);
    } catch (e) {
        console.error("Failed to save watch progress:", e);
    }
}

function toggleMediaWatched(mediaId) {
    if (!mediaId) return false;
    try {
        const hist = getWatchHistory();
        const idKey = String(mediaId);
        if (!hist[idKey]) {
            if (currentMediaItem) {
                saveMediaProgress(currentMediaItem, { watched: true, percentage: 100 });
                return true;
            }
            return false;
        }
        hist[idKey].watched = !hist[idKey].watched;
        if (hist[idKey].watched) {
            hist[idKey].percentage = 100;
        } else {
            hist[idKey].percentage = 0;
            hist[idKey].positionSec = 0;
        }
        hist[idKey].updatedAt = Date.now();
        localStorage.setItem(WATCH_HISTORY_KEY, JSON.stringify(hist));
        updateHistoryBadgesOnCards(idKey, hist[idKey]);
        return hist[idKey].watched;
    } catch (e) {
        return false;
    }
}

function isEpisodeWatched(mediaId, season, episode, kpId, title, source, year) {
    const prog = getMediaProgress(mediaId, kpId, title, source, year);
    if (!prog || !prog.episodesWatched) return false;
    return Boolean(prog.episodesWatched[`${season}_${episode}`]);
}

function clearWatchHistory() {
    if (!confirm("Вы действительно хотите полностью очистить историю просмотров?")) return;
    localStorage.removeItem(WATCH_HISTORY_KEY);
    renderHistoryView();
    updateSettingsHistoryCount();
    document.querySelectorAll(".card-progress-bar, .card-watched-badge").forEach(el => el.remove());
}

function formatDuration(sec) {
    if (!sec || isNaN(sec) || sec <= 0) return "0:00";
    const totalSec = Math.floor(sec);
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    const s = totalSec % 60;
    const ss = s < 10 ? `0${s}` : `${s}`;
    if (h > 0) {
        const mm = m < 10 ? `0${m}` : `${m}`;
        return `${h}:${mm}:${ss}`;
    }
    return `${m}:${ss}`;
}

function updateHistoryBadgesOnCards(idKey, prog) {
    const cards = document.querySelectorAll(`.media-card[data-id="${idKey}"]`);
    cards.forEach(card => {
        const wrapper = card.querySelector(".media-poster-wrapper");
        if (!wrapper) return;
        wrapper.querySelector(".card-progress-bar")?.remove();
        wrapper.querySelector(".card-watched-badge")?.remove();

        if (prog && prog.watched) {
            const badge = document.createElement("div");
            badge.className = "card-watched-badge";
            badge.textContent = "✓";
            wrapper.appendChild(badge);
        } else if (prog && prog.percentage >= 3) {
            const bar = document.createElement("div");
            bar.className = "card-progress-bar";
            bar.innerHTML = `<div class="card-progress-fill" style="width: ${prog.percentage}%;"></div>`;
            wrapper.appendChild(bar);
        }
    });
}

let currentView = "catalog";
let previousView = "catalog";
let lastFocusedCardElement = null;
let currentCategory = "all";
let currentGenre = "";
let currentMediaItem = null;
let currentStreams = null;
let currentDetails = null;
let activeSource = null;
let selectedStream = null;
let activeTranslatorId = null;
let activeSeasonId = 1;
let activeEpisodeId = 1;
let hlsInstance = null;
let activeModalRequestId = 0;

/* =========================================================
   Mandatory Forced Update System (Hard Gate)
   ========================================================= */
let mandatoryUpdateData = null;

async function checkMandatoryUpdate() {
    try {
        const res = await fetch("/api/updates/check");
        if (!res.ok) return;
        const data = await res.json();
        if (!data || !data.success) return;

        const serverVersionCode = Number(data.version_code || 0);
        const minVersionCode = Number(data.min_version_code || 0);
        const isMandatory = (data.force_update && serverVersionCode > CURRENT_APP_VERSION_CODE) ||
                            (minVersionCode > CURRENT_APP_VERSION_CODE) ||
                            (serverVersionCode > CURRENT_APP_VERSION_CODE);

        if (isMandatory) {
            activateForceUpdateModal(data);
        }
    } catch (e) {
        console.warn("Auto-update check exception:", e);
    }
}

function activateForceUpdateModal(data) {
    mandatoryUpdateData = data;
    window.isForceUpdateActive = true;

    // 1. Stop any background or active video preview immediately
    try {
        if (typeof stopActivePreview === "function") stopActivePreview();
        if (typeof closePlayer === "function") closePlayer();
    } catch (e) {}

    // 2. Hide catalog and other overlays ("иначе ничего не показываем")
    const catalogContent = document.getElementById("catalog-grid");
    if (catalogContent) {
        catalogContent.style.filter = "blur(10px)";
        catalogContent.style.pointerEvents = "none";
    }

    const overlay = document.getElementById("force-update-overlay");
    if (!overlay) return;

    const titleEl = document.getElementById("force-update-title");
    const verLabel = document.getElementById("force-update-version-label");
    const changelogEl = document.getElementById("force-update-changelog-text");
    const btnNow = document.getElementById("btn-force-update-now");
    const btnExit = document.getElementById("btn-force-update-exit");
    const statusMsg = document.getElementById("force-update-status-msg");
    const progressCont = document.getElementById("force-update-progress-bar");

    if (titleEl) {
        titleEl.textContent = `Доступно обязательное обновление ShowHub TV v${data.version_name || ''}`;
    }
    if (verLabel) {
        verLabel.textContent = `У вас: v${CURRENT_APP_VERSION} (код ${CURRENT_APP_VERSION_CODE}) → Доступна: v${data.version_name || 'новое'} (код ${data.version_code || '27'})`;
    }
    if (changelogEl && data.changelog) {
        changelogEl.textContent = data.changelog;
    }
    if (statusMsg) statusMsg.style.display = "none";
    if (progressCont) progressCont.style.display = "none";

    overlay.style.display = "flex";

    // Action handlers (ensure single binding)
    if (btnNow && !btnNow._bound) {
        btnNow._bound = true;
        btnNow.addEventListener("click", () => {
            if (!mandatoryUpdateData) return;
            if (statusMsg) {
                statusMsg.textContent = `Подготовка к загрузке обновления v${mandatoryUpdateData.version_name || ''}...`;
                statusMsg.style.display = "block";
            }
            if (progressCont) {
                progressCont.style.display = "block";
            }
            btnNow.disabled = true;
            btnNow.style.opacity = "0.7";

            let targetUrl = mandatoryUpdateData.download_url || mandatoryUpdateData.apk_url || "/ShowHub.apk";
            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                const host = (localStorage.getItem("showhub_server") || "https://showhub-server.onrender.com").replace(/\/+$/, "");
                targetUrl = host + (targetUrl.startsWith("/") ? "" : "/") + targetUrl;
            }

            console.log("Starting forced update download from:", targetUrl);
            if (statusMsg) {
                statusMsg.textContent = "Загрузка ShowHub APK в память телевизора... Пожалуйста, подождите.";
            }
            setTimeout(() => {
                if (window.AndroidBridge && typeof window.AndroidBridge.downloadAndInstall === "function") {
                    window.AndroidBridge.downloadAndInstall(targetUrl);
                } else if (window.AndroidBridge && typeof window.AndroidBridge.installUpdate === "function") {
                    window.AndroidBridge.installUpdate(targetUrl);
                } else if (window.AndroidBridge && typeof window.AndroidBridge.openUrl === "function") {
                    window.AndroidBridge.openUrl(targetUrl);
                } else {
                    window.location.href = targetUrl;
                }
            }, 300);
        });
    }

    if (btnExit && !btnExit._bound) {
        btnExit._bound = true;
        btnExit.addEventListener("click", () => {
            if (window.AndroidBridge && typeof window.AndroidBridge.closeApp === "function") {
                window.AndroidBridge.closeApp();
            } else {
                window.close();
            }
        });
    }

    // Set initial focus on primary update button
    setTimeout(() => {
        try { btnNow?.focus(); } catch (e) {}
    }, 100);
}

document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll(".badge-version, #header-version-badge, #settings-version-badge").forEach(el => {
        el.textContent = `v${CURRENT_APP_VERSION}`;
    });
    checkMandatoryUpdate();
    initThemeEngine();
    initSettingsModal();
    initServerConnection();
    initNavigation();
    initGenreFilter();
    initCatalogFilters();
    initSearch();
    initModalEvents();
    initPlayerOSD();
    initFilmixAccount();
    loadCatalog();
    updateCatalogStats();
    checkHealth();
    loadFilmixAccount();

    // Favorites updates check on start and every 15 minutes
    setTimeout(checkFavoritesEpisodeUpdates, 2000);
    // Auto-refresh canary health checks every 60 seconds
    setInterval(checkHealth, 60000);
    setInterval(updateCatalogStats, 120000);

    // Preload popular genres in background for 0ms genre switching
    setTimeout(preloadPopularGenres, 1500);

    // Initial and periodic series episodes synchronization
    setTimeout(refreshCatalogSeriesEpisodes, 3000);
    setInterval(refreshCatalogSeriesEpisodes, 180000);

    // Check and restore last interrupted session on pause
    setTimeout(restoreLastSessionIfAny, 600);
});

/* =========================================================
   Navigation & View Switching
   ========================================================= */
function initNavigation() {
    const navItems = document.querySelectorAll(".nav-item, .nav-action-btn");
    navItems.forEach(item => {
        item.addEventListener("click", () => {
            const target = item.getAttribute("data-nav");
            const cat = item.getAttribute("data-category");
            
            navItems.forEach(n => n.classList.remove("active"));
            item.classList.add("active");

            if (cat) {
                currentCategory = cat;
                const titles = {
                    all: "Новинки фильмов и сериалов",
                    movies: "Фильмы (Новинки)",
                    series: "Сериалы (Новые серии)",
                    cartoons: "Мультфильмы (Новинки)",
                    anime: "Аниме (Новинки)"
                };
                const heading = document.getElementById("catalog-heading");
                if (heading) heading.textContent = titles[cat] || "Новинки";
                
                // Reset genre active on category change
                currentGenre = "";
                document.querySelectorAll(".genre-chip").forEach((c, idx) => {
                    c.classList.toggle("active", idx === 0);
                });

                loadCatalog();
                switchView("catalog");
            } else if (target) {
                switchView(target);
            }
        });
    });

    document.getElementById("btn-back-catalog")?.addEventListener("click", () => switchView("catalog"));
    document.getElementById("btn-back-search")?.addEventListener("click", () => switchView("catalog"));
    document.getElementById("btn-back-account")?.addEventListener("click", () => switchView("catalog"));
    document.getElementById("btn-clear-history-view")?.addEventListener("click", () => {
        if (confirm("Очистить историю просмотров?")) {
            localStorage.removeItem("showhub_watch_history");
            renderHistoryView();
            syncWatchedBadgesAcrossCatalog();
        }
    });

    document.getElementById("btn-clear-favorites-view")?.addEventListener("click", () => {
        if (confirm("Очистить список избранного?")) {
            localStorage.removeItem("showhub_favorites");
            renderFavoritesView();
        }
    });

    document.getElementById("btn-refresh-health")?.addEventListener("click", () => {
        refreshHealth();
    });

    document.getElementById("nav-server")?.addEventListener("click", () => {
        const serverModal = document.getElementById("server-connect-modal");
        if (serverModal) serverModal.style.display = "flex";
    });

    document.getElementById("btn-dismiss-banner")?.addEventListener("click", () => {
        const b = document.getElementById("canary-warning-banner");
        if (b) b.style.display = "none";
    });

    // Remote D-Pad Navigation Handler
    window.addEventListener("keydown", handleKeyDown);
}

function initGenreFilter() {
    document.querySelectorAll(".genre-chip").forEach(chip => {
        const applyGenre = () => {
            const isAlreadyActive = chip.classList.contains("active") && chip.getAttribute("data-genre") !== "";
            if (isAlreadyActive) {
                // Deselect genre on second press: return to "Все жанры"
                document.querySelectorAll(".genre-chip").forEach(c => c.classList.remove("active"));
                document.querySelector(".genre-chip[data-genre='']")?.classList.add("active");
                currentGenre = "";
            } else {
                document.querySelectorAll(".genre-chip").forEach(c => c.classList.remove("active"));
                chip.classList.add("active");
                currentGenre = chip.getAttribute("data-genre") || "";
            }
            const grid = document.getElementById("catalog-grid");
            if (grid) {
                grid.scrollTop = 0;
                showGridSkeleton(grid, currentGenre ? `Загрузка жанра: ${chip.textContent.trim()}...` : "Загрузка каталога...");
            }
            loadCatalog(false);
        };

        chip.addEventListener("click", applyGenre);
        chip.addEventListener("keydown", (e) => {
            if (e.key === "Enter" || e.keyCode === 23 || e.keyCode === 13) {
                e.preventDefault();
                applyGenre();
            }
        });
    });
}

function initCatalogFilters() {
    const filterSort = document.getElementById("filter-sort");
    const filterYear = document.getElementById("filter-year");
    const filterRating = document.getElementById("filter-rating");
    const filterType = document.getElementById("filter-type");
    const filterCountry = document.getElementById("filter-country");
    const btnReset = document.getElementById("btn-reset-filters");

    const onFilterChange = () => {
        loadCatalog();
    };

    filterSort?.addEventListener("change", onFilterChange);
    filterYear?.addEventListener("change", onFilterChange);
    filterRating?.addEventListener("change", onFilterChange);
    filterType?.addEventListener("change", onFilterChange);
    filterCountry?.addEventListener("change", onFilterChange);

    btnReset?.addEventListener("click", () => {
        if (filterSort) filterSort.value = "newest";
        if (filterYear) filterYear.value = "all";
        if (filterRating) filterRating.value = "0";
        if (filterType) filterType.value = "all";
        if (filterCountry) filterCountry.value = "all";
        document.querySelectorAll(".genre-chip").forEach(c => c.classList.remove("active"));
        document.querySelector(".genre-chip[data-genre='']")?.classList.add("active");
        currentGenre = "";
        loadCatalog();
    });

    document.getElementById("btn-catalog-load-more")?.addEventListener("click", () => {
        loadCatalog(true);
    });
}

async function updateCatalogStats() {
    try {
        const res = await fetch("/api/catalog/stats");
        if (res.ok) {
            const data = await res.json();
            const counterEl = document.getElementById("catalog-footer-counter");
            if (counterEl && data.total_all) {
                counterEl.textContent = `Доступно в каталоге: ~${data.total_all.toLocaleString('ru-RU')} наименований (${data.total_movies.toLocaleString('ru-RU')} фильмов, ${data.total_series.toLocaleString('ru-RU')} сериалов, ${(data.total_cartoons || 3120).toLocaleString('ru-RU')} мультфильмов, ${(data.total_anime || 2480).toLocaleString('ru-RU')} аниме)`;
            }
        }
    } catch (e) {}
}

function switchView(viewName) {
    if (viewName !== "details") {
        previousView = viewName;
    }
    currentView = viewName;
    document.body.classList.toggle("in-details-view", viewName === "details");
    document.querySelectorAll(".view").forEach(v => v.style.display = "none");

    const activeView = document.getElementById(`view-${viewName}`);
    if (activeView) activeView.style.display = "block";

    if (viewName === "details") {
        window.scrollTo({ top: 0, behavior: 'instant' });
        setTimeout(() => {
            const banner = document.getElementById("modal-progress-banner");
            if (banner && banner.style.display !== "none") {
                document.getElementById("btn-modal-resume")?.focus();
            } else {
                document.getElementById("btn-modal-play")?.focus();
            }
        }, 60);
    } else if (viewName === "health") {
        renderHealthView();
    } else if (viewName === "account") {
        loadFilmixAccount();
    } else if (viewName === "history") {
        renderHistoryView();
        focusFirstElement("#history-grid .media-card");
    } else if (viewName === "favorites") {
        renderFavoritesView();
        focusFirstElement("#favorites-grid .media-card");
    } else if (viewName === "settings") {
        openSettingsModal();
    } else if (viewName === "catalog") {
        focusFirstElement("#catalog-grid .media-card");
    } else if (viewName === "search") {
        openSearchModal();
    }
}

function openSearchModal() {
    const modal = document.getElementById("search-modal");
    if (!modal) return;
    modal.style.display = "flex";
    const input = document.getElementById("search-modal-input");
    if (input) {
        const existing = document.getElementById("search-view-input")?.value || document.getElementById("search-input")?.value || "";
        input.value = existing;
        input.focus();
        input.select();
    }
    renderSearchHistory();
}

function closeSearchModal() {
    const modal = document.getElementById("search-modal");
    if (modal) modal.style.display = "none";
    // Explicitly dismiss TV / mobile virtual keyboard
    const inputs = [
        document.getElementById("search-modal-input"),
        document.getElementById("search-view-input"),
        document.getElementById("search-input"),
        document.activeElement
    ];
    inputs.forEach(inp => {
        if (inp && typeof inp.blur === "function") inp.blur();
    });
}

function saveSearchHistory(query) {
    if (!query || !query.trim()) return;
    try {
        let history = JSON.parse(localStorage.getItem("mediacenter_search_history") || "[]");
        history = history.filter(q => q.toLowerCase() !== query.toLowerCase());
        history.unshift(query.trim());
        if (history.length > 8) history = history.slice(0, 8);
        localStorage.setItem("mediacenter_search_history", JSON.stringify(history));
    } catch (e) {}
}

function renderSearchHistory() {
    const container = document.getElementById("search-history-chips");
    const section = document.getElementById("search-history-section");
    if (!container || !section) return;

    try {
        const history = JSON.parse(localStorage.getItem("mediacenter_search_history") || "[]");
        if (!history || history.length === 0) {
            section.style.display = "none";
            return;
        }

        section.style.display = "block";
        container.innerHTML = history.map(q => `
            <button class="search-chip" data-query="${q}" tabindex="0">${q}</button>
        `).join("");

        container.querySelectorAll(".search-chip").forEach(chip => {
            chip.addEventListener("click", () => {
                const q = chip.getAttribute("data-query");
                closeSearchModal();
                performSearch(q);
            });
        });
    } catch (e) {
        section.style.display = "none";
    }
}

/* =========================================================
   Remote Control (D-Pad / Keyboard) Spatial Navigation
   ========================================================= */
let lastNavKeyTime = 0;

function handleKeyDown(e) {
    if (window.isForceUpdateActive) {
        const btnNow = document.getElementById("btn-force-update-now");
        const btnExit = document.getElementById("btn-force-update-exit");
        if (e.key === "ArrowLeft" || e.key === "ArrowUp") {
            e.preventDefault();
            e.stopPropagation();
            btnNow?.focus();
            return;
        }
        if (e.key === "ArrowRight" || e.key === "ArrowDown") {
            e.preventDefault();
            e.stopPropagation();
            btnExit?.focus();
            return;
        }
        if (e.key === "Escape" || e.key === "Back" || e.key === "BrowserBack" || e.key === "Backspace" || e.keyCode === 4 || e.keyCode === 27) {
            e.preventDefault();
            e.stopPropagation();
            if (typeof window.handleTvBack === "function") window.handleTvBack();
            return;
        }
        if (e.key === "Enter" || e.keyCode === 13) {
            return; // allow click on active button
        }
        e.preventDefault();
        e.stopPropagation();
        return;
    }
    const activeModal = document.querySelector(".modal[style*='display: flex'], .modal[style*='display: block']");
    const playerModal = document.getElementById("player-modal");

    // Close on Escape / Back (including Android TV KEYCODE_BACK = 4, Backspace = 8, Esc = 27)
    if (e.key === "Escape" || e.key === "Back" || e.key === "BrowserBack" || e.key === "GoBack" || e.key === "Backspace" || e.keyCode === 4 || e.which === 4 || e.keyCode === 27 || e.which === 27 || e.keyCode === 8 || e.which === 8) {
        const isInput = (e.target && (e.target.tagName === "INPUT" || e.target.tagName === "TEXTAREA"));
        if (!isInput || e.key === "Escape" || e.keyCode === 4) {
            if (typeof window.handleTvBack === "function" && window.handleTvBack()) {
                e.preventDefault();
                e.stopPropagation();
                return;
            }
        }
    }

    // Active Player Controls Handler
    if (playerModal && playerModal.style.display !== "none") {
        if (typeof resetOsdTimeout === "function") resetOsdTimeout();

        // Media keys & Space & Bluetooth Headset / Remote buttons
        const isMediaPlayPause = (
            e.key === " " || e.key === "Spacebar" || e.code === "Space" ||
            e.key === "MediaPlayPause" || e.key === "MediaPlay" || e.key === "MediaPause" || e.key === "HeadsetHook" ||
            e.keyCode === 85 || e.keyCode === 79 || e.keyCode === 126 || e.keyCode === 127 || e.keyCode === 179
        );
        if (isMediaPlayPause) {
            e.preventDefault();
            e.stopPropagation();
            if (typeof togglePlayPause === "function") togglePlayPause();
            return;
        }

        const isMediaNext = (
            e.key === "MediaTrackNext" || e.key === "NextTrack" ||
            e.keyCode === 87 || e.keyCode === 176
        );
        if (isMediaNext) {
            e.preventDefault();
            e.stopPropagation();
            if (typeof playNextEpisode === "function") playNextEpisode();
            return;
        }

        const isMediaPrev = (
            e.key === "MediaTrackPrevious" || e.key === "PreviousTrack" ||
            e.keyCode === 88 || e.keyCode === 177
        );
        if (isMediaPrev) {
            e.preventDefault();
            e.stopPropagation();
            if (typeof playPrevEpisode === "function") playPrevEpisode();
            return;
        }

        const isFastForward = (
            e.key === "MediaFastForward" || e.keyCode === 90 || e.keyCode === 228
        );
        if (isFastForward) {
            e.preventDefault();
            e.stopPropagation();
            if (typeof seekRelative === "function") seekRelative(30);
            return;
        }

        const isRewind = (
            e.key === "MediaRewind" || e.keyCode === 89 || e.keyCode === 227
        );
        if (isRewind) {
            e.preventDefault();
            e.stopPropagation();
            if (typeof seekRelative === "function") seekRelative(-30);
            return;
        }

        const isMediaStop = (
            e.key === "MediaStop" || e.keyCode === 86 || e.keyCode === 178
        );
        if (isMediaStop) {
            e.preventDefault();
            e.stopPropagation();
            if (typeof closePlayer === "function") closePlayer();
            return;
        }

        // When OSD is hidden and no drawer is open: quick seek or toggle play
        if (!osdVisible && !activeDrawer) {
            if (e.key === "ArrowLeft") {
                e.preventDefault();
                if (typeof seekRelative === "function") seekRelative(-10);
                return;
            }
            if (e.key === "ArrowRight") {
                e.preventDefault();
                if (typeof seekRelative === "function") seekRelative(10);
                return;
            }
            if (e.key === "ArrowUp" || e.key === "ArrowDown") {
                e.preventDefault();
                const centerBtn = document.getElementById("osd-center-play-btn");
                if (centerBtn) {
                    showOSD(centerBtn);
                } else if (typeof showOSD === "function") {
                    showOSD(document.getElementById("osd-btn-play"));
                }
                return;
            }
            if (e.key === "Enter") {
                e.preventDefault();
                if (typeof togglePlayPause === "function") togglePlayPause();
                const centerBtn = document.getElementById("osd-center-play-btn");
                if (typeof showOSD === "function") showOSD(centerBtn || document.getElementById("osd-btn-play"));
                return;
            }
        }

        // Scrubber Left/Right Arrow Seeking and Enter confirmation
        if (document.activeElement && document.activeElement.id === "osd-progress-track") {
            if (e.key === "Enter") {
                e.preventDefault();
                togglePlayPause();
                return;
            }
            if (e.key === "ArrowLeft") {
                e.preventDefault();
                if (typeof seekRelative === "function") seekRelative(-15);
                return;
            }
            if (e.key === "ArrowRight") {
                e.preventDefault();
                if (typeof seekRelative === "function") seekRelative(15);
                return;
            }
        }
    }

    // Hotkey for search modal '/' or F3 if not currently typing in an input
    const isEditing = document.activeElement && ["INPUT", "TEXTAREA"].includes(document.activeElement.tagName);
    if (!isEditing && (e.key === "/" || e.key === "F3")) {
        e.preventDefault();
        openSearchModal();
        return;
    }

    // Don't intercept arrow keys if typing in inputs
    if (document.activeElement && ["search-input", "search-view-input", "search-modal-input", "filmix-login", "filmix-password", "filmix-cookie-input", "pref-voice-custom", "settings-server-ip", "settings-filmix-login", "settings-filmix-password", "settings-filmix-cookie"].includes(document.activeElement.id)) {
        if (e.key === "Enter") {
            const inputId = document.activeElement.id;
            if (inputId === "search-input" || inputId === "search-view-input" || inputId === "search-modal-input") {
                const val = document.activeElement.value;
                closeSearchModal();
                performSearch(val);
                const navSearch = document.getElementById("nav-search");
                if (navSearch) navSearch.focus();
            } else if (inputId === "filmix-cookie-input") {
                performCookieSubmit();
            } else if (inputId === "settings-filmix-cookie") {
                document.getElementById("btn-settings-filmix-save-cookie")?.click();
            } else if (inputId === "settings-filmix-login" || inputId === "settings-filmix-password") {
                document.getElementById("btn-settings-filmix-login")?.click();
            } else if (inputId === "settings-server-ip") {
                document.getElementById("btn-settings-server-save")?.click();
            } else {
                performFilmixLogin();
            }
            e.preventDefault();
        }
        return;
    }

    // Remote Control OK / D-Pad Center for Android TV (support Android TV KEYCODE_DPAD_CENTER = 23)
    if (e.keyCode === 23) {
        const active = document.activeElement;
        if (active && (active.tagName === "BUTTON" || active.classList?.contains("genre-chip") || active.classList?.contains("modal-tab-btn"))) {
            if (active.id !== "btn-modal-close") {
                e.preventDefault();
                active.click();
            }
        }
    }

    // Deterministic navigation within Settings Modal
    const settingsModal = document.getElementById("settings-modal");
    if (settingsModal && settingsModal.style.display !== "none") {
        const active = document.activeElement;
        const activeTabBtn = settingsModal.querySelector(".settings-tab-btn.active") || settingsModal.querySelector(".settings-tab-btn");
        const activeTabContent = settingsModal.querySelector(".settings-tab-content.active") || Array.from(settingsModal.querySelectorAll(".settings-tab-content")).find(c => c.style.display === "flex" || c.style.display === "block");
        const btnClose = document.getElementById("btn-close-settings-modal");
        const btnSave = document.getElementById("btn-close-settings-save");

        if (active && active.classList.contains("settings-tab-btn")) {
            if (e.key === "ArrowDown") {
                e.preventDefault();
                if (activeTabContent) {
                    const firstFocusable = activeTabContent.querySelector("select, input:not([type='hidden']), button, [tabindex='0']");
                    if (firstFocusable && firstFocusable.offsetParent !== null) {
                        firstFocusable.focus();
                        return;
                    }
                }
                if (btnSave && btnSave.offsetParent !== null) {
                    btnSave.focus();
                    return;
                }
            } else if (e.key === "ArrowUp") {
                e.preventDefault();
                if (btnClose && btnClose.offsetParent !== null) {
                    btnClose.focus();
                    return;
                }
            } else if (e.key === "ArrowRight" || e.key === "ArrowLeft") {
                const tabs = Array.from(settingsModal.querySelectorAll(".settings-tab-btn"));
                const idx = tabs.indexOf(active);
                if (idx !== -1) {
                    const nextIdx = e.key === "ArrowRight" ? Math.min(tabs.length - 1, idx + 1) : Math.max(0, idx - 1);
                    if (nextIdx !== idx) {
                        e.preventDefault();
                        tabs[nextIdx].focus();
                        switchSettingsTab(tabs[nextIdx].getAttribute("data-stab"));
                        return;
                    }
                }
            }
        } else if (active && active.id === "btn-close-settings-modal") {
            if (e.key === "ArrowDown") {
                e.preventDefault();
                if (activeTabBtn) activeTabBtn.focus();
                return;
            }
        } else if (active && active.id === "btn-close-settings-save") {
            if (e.key === "ArrowUp") {
                e.preventDefault();
                if (activeTabContent) {
                    const focusables = Array.from(activeTabContent.querySelectorAll("select, input:not([type='hidden']), button, [tabindex='0']")).filter(el => el.offsetParent !== null);
                    if (focusables.length > 0) {
                        focusables[focusables.length - 1].focus();
                        return;
                    }
                }
                if (activeTabBtn) activeTabBtn.focus();
                return;
            }
        } else if (active && active.closest(".settings-tab-content")) {
            const focusables = Array.from(activeTabContent.querySelectorAll("select, input:not([type='hidden']), button, [tabindex='0']")).filter(el => el.offsetParent !== null);
            const idx = focusables.indexOf(active);
            if (e.key === "ArrowUp" && (idx === 0 || idx === -1)) {
                e.preventDefault();
                if (activeTabBtn) activeTabBtn.focus();
                return;
            }
            if (e.key === "ArrowDown" && (idx === focusables.length - 1 || idx === -1)) {
                e.preventDefault();
                if (btnSave && btnSave.offsetParent !== null) {
                    btnSave.focus();
                    return;
                }
            }
        }
    }

    // Deterministic navigation within Search Modal
    const searchModal = document.getElementById("search-modal");
    if (searchModal && searchModal.style.display !== "none") {
        const active = document.activeElement;
        const input = document.getElementById("search-modal-input");
        const btnClose = document.getElementById("btn-close-search-modal");
        const chips = Array.from(searchModal.querySelectorAll(".search-chip")).filter(el => el.offsetParent !== null);
        const results = Array.from(searchModal.querySelectorAll("#search-modal-results .media-card")).filter(el => el.offsetParent !== null);

        if (active === input) {
            if (e.key === "ArrowDown") {
                e.preventDefault();
                if (chips.length > 0) chips[0].focus();
                else if (results.length > 0) results[0].focus();
                return;
            } else if (e.key === "ArrowUp") {
                e.preventDefault();
                if (btnClose) btnClose.focus();
                return;
            }
        } else if (active === btnClose) {
            if (e.key === "ArrowDown") {
                e.preventDefault();
                if (input) input.focus();
                return;
            }
        } else if (active && active.classList.contains("search-chip")) {
            if (e.key === "ArrowUp") {
                e.preventDefault();
                if (input) input.focus();
                return;
            } else if (e.key === "ArrowDown") {
                if (results.length > 0) {
                    e.preventDefault();
                    results[0].focus();
                    return;
                }
            }
        }
    }

    // Deterministic navigation within Server Connection Modal
    const serverModal = document.getElementById("server-connect-modal");
    if (serverModal && serverModal.style.display !== "none") {
        const active = document.activeElement;
        const input = document.getElementById("server-ip-input");
        const btnSave = document.getElementById("btn-server-save");
        const btnRetry = document.getElementById("btn-server-retry");
        const btnCancel = document.getElementById("btn-server-cancel");

        if (active === input && e.key === "ArrowDown") {
            e.preventDefault();
            if (btnSave) btnSave.focus();
            return;
        } else if ((active === btnSave || active === btnRetry || active === btnCancel) && e.key === "ArrowUp") {
            e.preventDefault();
            if (input) input.focus();
            return;
        }
    }

    // Direct navigation helpers for Details view (Strict leftmost element focus on ArrowUp / ArrowDown)
    if (currentView === "details") {
        const active = document.activeElement;
        const btnClose = document.getElementById("btn-modal-close");
        const btnPlay = document.getElementById("btn-modal-play");
        const btnResume = document.getElementById("btn-modal-resume");

        function getDetailsRowElements(rowType) {
            if (rowType === "close") {
                return (btnClose && btnClose.offsetParent !== null && !btnClose.disabled) ? [btnClose] : [];
            }
            if (rowType === "hero") {
                const heroActions = document.querySelector(".details-hero-actions");
                if (!heroActions || heroActions.offsetParent === null) return [];
                const buttons = Array.from(heroActions.querySelectorAll("button")).filter(b => b.offsetParent !== null && !b.disabled);
                if (btnResume && btnResume.offsetParent !== null && !btnResume.disabled) {
                    return [btnResume, ...buttons.filter(b => b !== btnResume)];
                }
                return buttons;
            }
            if (rowType === "tabs") {
                const navTabs = document.querySelector(".modal-nav-tabs");
                return navTabs ? Array.from(navTabs.querySelectorAll(".modal-tab-btn")).filter(b => b.offsetParent !== null && !b.disabled) : [];
            }
            if (rowType === "seasons") {
                const sec = document.getElementById("section-seasons");
                if (!sec || sec.offsetParent === null || sec.style.display === "none") return [];
                return Array.from(document.querySelectorAll("#modal-seasons-list .season-chip")).filter(b => b.offsetParent !== null && !b.disabled);
            }
            if (rowType === "episodes") {
                const sec = document.getElementById("section-episodes");
                if (!sec || sec.offsetParent === null || sec.style.display === "none") return [];
                return Array.from(document.querySelectorAll("#modal-episodes-list .episode-chip")).filter(b => b.offsetParent !== null && !b.disabled);
            }
            if (rowType === "sources") {
                const tabs = document.getElementById("modal-source-tabs");
                if (!tabs || tabs.offsetParent === null || tabs.style.display === "none") return [];
                const btns = Array.from(tabs.querySelectorAll(".source-tab-btn")).filter(b => b.offsetParent !== null && !b.disabled);
                return btns.length > 1 ? btns : [];
            }
            if (rowType === "streams") {
                const list = document.getElementById("modal-streams-list");
                if (!list || list.offsetParent === null || list.style.display === "none") return [];
                return Array.from(list.querySelectorAll(".stream-chip")).filter(b => b.offsetParent !== null && !b.disabled);
            }
            if (rowType === "translators") {
                const sec = document.getElementById("section-translators");
                if (!sec || sec.offsetParent === null || sec.style.display === "none") return [];
                return Array.from(document.querySelectorAll("#modal-translators-list .translator-chip")).filter(b => b.offsetParent !== null && !b.disabled);
            }
            if (rowType === "tab_content") {
                const activeTabContent = document.querySelector(".modal-tab-content.active");
                if (!activeTabContent || activeTabContent.id === "tab-content-watch") return [];
                return Array.from(activeTabContent.querySelectorAll("button:not([disabled]), [tabindex='0'], .comment-card, tr[tabindex='0']")).filter(b => b.offsetParent !== null && !b.disabled);
            }
            return [];
        }

        function focusRowTarget(rowType) {
            const elements = getDetailsRowElements(rowType);
            if (elements.length > 0) {
                // Prioritize active/selected chip (e.g. default quality chip .active, active episode, active season, active tab)
                // Otherwise fall back to the first element
                const target = elements.find(el => el.classList.contains("active")) || elements[0];
                const scrollParent = target.closest(".episodes-scroll-track, .chips-scroll-row, .streams-list, .source-tabs");
                if (scrollParent) {
                    target.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
                }
                target.focus();
                return true;
            }
            return false;
        }

        // Determine current row
        let currentRow = null;
        if (active === btnClose) currentRow = "close";
        else if (active && (active.closest(".details-hero-actions") || active.id === "btn-modal-resume" || active.id === "btn-modal-restart" || active.id === "btn-modal-play")) currentRow = "hero";
        else if (active && active.classList.contains("modal-tab-btn")) currentRow = "tabs";
        else if (active && active.classList.contains("season-chip")) currentRow = "seasons";
        else if (active && active.classList.contains("episode-chip")) currentRow = "episodes";
        else if (active && active.classList.contains("source-tab-btn")) currentRow = "sources";
        else if (active && active.classList.contains("stream-chip")) currentRow = "streams";
        else if (active && active.classList.contains("translator-chip")) currentRow = "translators";
        else if (active && active.closest(".modal-tab-content")) currentRow = "tab_content";

        const isWatchTab = !document.querySelector(".modal-tab-content.active") || document.querySelector(".modal-tab-content.active").id === "tab-content-watch";
        const rowsOrder = isWatchTab
            ? ["close", "hero", "tabs", "seasons", "episodes", "sources", "streams", "translators"]
            : ["close", "hero", "tabs", "tab_content"];

        if (currentRow) {
            const currentIdx = rowsOrder.indexOf(currentRow);

            if (e.key === "ArrowDown") {
                for (let i = currentIdx + 1; i < rowsOrder.length; i++) {
                    if (focusRowTarget(rowsOrder[i])) {
                        e.preventDefault();
                        return;
                    }
                }
            } else if (e.key === "ArrowUp") {
                for (let i = currentIdx - 1; i >= 0; i--) {
                    if (focusRowTarget(rowsOrder[i])) {
                        e.preventDefault();
                        return;
                    }
                }
            } else if (e.key === "ArrowRight" || e.key === "ArrowLeft") {
                const rowElements = getDetailsRowElements(currentRow);
                const activeIdx = rowElements.indexOf(active);
                if (activeIdx !== -1) {
                    if (e.key === "ArrowRight" && activeIdx + 1 < rowElements.length) {
                        e.preventDefault();
                        const nextEl = rowElements[activeIdx + 1];
                        nextEl.focus();
                        nextEl.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
                        if (currentRow === "tabs") {
                            switchModalTab(nextEl.getAttribute("data-tab"));
                        }
                        return;
                    } else if (e.key === "ArrowLeft") {
                        if (activeIdx - 1 >= 0) {
                            e.preventDefault();
                            const prevEl = rowElements[activeIdx - 1];
                            prevEl.focus();
                            prevEl.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
                            if (currentRow === "tabs") {
                                switchModalTab(prevEl.getAttribute("data-tab"));
                            }
                            return;
                        } else if (currentRow === "hero" && btnClose) {
                            e.preventDefault();
                            btnClose.focus();
                            return;
                        }
                    }
                }
            }
        }

        // Smooth vertical scroll for comments or schedule container
        if (active && (active.classList?.contains("comment-card") || active.closest?.(".comments-container") || active.closest?.(".schedule-container") || active.closest?.(".schedule-table-wrap"))) {
            const scrollBox = active.closest?.(".comments-container") || active.closest?.(".schedule-container") || active.closest?.(".schedule-table-wrap") || document.getElementById("modal-comments-list") || document.getElementById("modal-schedule-list");
            if (scrollBox) {
                if (e.key === "ArrowDown") {
                    scrollBox.scrollBy({ top: 120, behavior: 'smooth' });
                } else if (e.key === "ArrowUp") {
                    scrollBox.scrollBy({ top: -120, behavior: 'smooth' });
                }
            }
        }
    }

    // Direct navigation helpers for Player OSD
    if (playerModal && playerModal.style.display !== "none" && !activeDrawer) {
        const active = document.activeElement;
        const btnBack = document.getElementById("osd-btn-back");
        const btnSkipIntro = document.getElementById("player-btn-skip-intro");
        const centerPlayBtn = document.getElementById("osd-center-play-btn");
        const progressTrack = document.getElementById("osd-progress-track");
        const btnPlay = document.getElementById("osd-btn-play");

        showOSD();

        if (e.key === "ArrowDown") {
            if (active && (active.id === "osd-btn-back" || active.id === "player-btn-skip-intro" || active.id === "player-btn-skip-outro")) {
                e.preventDefault();
                if (centerPlayBtn && centerPlayBtn.offsetParent !== null) centerPlayBtn.focus();
                else if (progressTrack) progressTrack.focus();
                else if (btnPlay) btnPlay.focus();
                return;
            }
            if (active && active.id === "osd-center-play-btn") {
                e.preventDefault();
                if (progressTrack) progressTrack.focus();
                else if (btnPlay) btnPlay.focus();
                return;
            }
            if (active && active.id === "osd-progress-track") {
                e.preventDefault();
                if (btnPlay) btnPlay.focus();
                return;
            }
        } else if (e.key === "ArrowUp") {
            if (active && active.classList && active.classList.contains("osd-ctrl-btn")) {
                e.preventDefault();
                if (progressTrack) progressTrack.focus();
                else if (centerPlayBtn && centerPlayBtn.offsetParent !== null) centerPlayBtn.focus();
                return;
            }
            if (active && active.id === "osd-progress-track") {
                e.preventDefault();
                if (centerPlayBtn && centerPlayBtn.offsetParent !== null) {
                    centerPlayBtn.focus();
                } else if (btnSkipIntro && btnSkipIntro.style.display !== "none") {
                    btnSkipIntro.focus();
                } else if (btnBack) {
                    btnBack.focus();
                }
                return;
            }
            if (active && active.id === "osd-center-play-btn") {
                e.preventDefault();
                if (btnSkipIntro && btnSkipIntro.style.display !== "none") {
                    btnSkipIntro.focus();
                } else if (btnBack) {
                    btnBack.focus();
                }
                return;
            }
            if (active && (active.id === "player-btn-skip-intro" || active.id === "player-btn-skip-outro")) {
                e.preventDefault();
                if (btnBack) btnBack.focus();
                return;
            }
        }
    }

    if (["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight"].includes(e.key)) {
        e.preventDefault();

        // Navigation key throttle to avoid Android TV remote repeat spam / queue bursts
        const now = Date.now();
        if (now - lastNavKeyTime < 45) {
            return;
        }
        lastNavKeyTime = now;

        const currentEl = document.activeElement;

        // 1. FAST-PATH: Media Card Grid Navigation (Zero DOM queries, Zero reflows)
        const currentCard = currentEl?.classList?.contains("media-card") ? currentEl : currentEl?.closest(".media-card");
        const gridContainer = currentCard?.parentElement;

        if (currentCard && gridContainer && (gridContainer.id === "catalog-grid" || gridContainer.id === "search-results" || gridContainer.id === "favorites-grid" || gridContainer.id === "history-grid" || gridContainer.id === "search-modal-results")) {
            const gridCards = Array.from(gridContainer.children).filter(c => c.classList && c.classList.contains("media-card"));
            const cardIdx = gridCards.indexOf(currentCard);
            if (cardIdx !== -1) {
                // Compute columns from card/container width: instantaneous, zero reflow loop
                const cardWidth = currentCard.offsetWidth || 230;
                const gridWidth = gridContainer.clientWidth || 1920;
                const cols = Math.max(1, Math.floor(gridWidth / cardWidth)) || 5;

                if (e.key === "ArrowRight") {
                    if (cardIdx + 1 < gridCards.length) {
                        gridCards[cardIdx + 1].focus();
                        gridCards[cardIdx + 1].scrollIntoView({ block: 'nearest', inline: 'nearest' });
                        return;
                    }
                } else if (e.key === "ArrowLeft") {
                    if (cardIdx - 1 >= 0) {
                        gridCards[cardIdx - 1].focus();
                        gridCards[cardIdx - 1].scrollIntoView({ block: 'nearest', inline: 'nearest' });
                        return;
                    }
                } else if (e.key === "ArrowDown") {
                    const nextRowIdx = cardIdx + cols;
                    if (nextRowIdx < gridCards.length) {
                        gridCards[nextRowIdx].focus();
                        gridCards[nextRowIdx].scrollIntoView({ block: 'nearest', inline: 'nearest' });
                        return;
                    } else {
                        const loadMoreBtn = document.getElementById("btn-catalog-load-more");
                        if (loadMoreBtn && loadMoreBtn.offsetParent !== null) {
                            loadMoreBtn.focus();
                            loadMoreBtn.scrollIntoView({ block: 'nearest', inline: 'nearest' });
                            return;
                        }
                    }
                } else if (e.key === "ArrowUp") {
                    const prevRowIdx = cardIdx - cols;
                    if (prevRowIdx >= 0) {
                        gridCards[prevRowIdx].focus();
                        gridCards[prevRowIdx].scrollIntoView({ block: 'nearest', inline: 'nearest' });
                        return;
                    } else {
                        // User is on top row of cards: INSTANT 1-click jump into top menu (#nav-search)!
                        const topbarNav = document.getElementById("nav-search") || document.querySelector(".topbar .nav-item");
                        if (topbarNav) {
                            topbarNav.focus();
                            window.scrollTo({ top: 0, behavior: 'instant' });
                            return;
                        }
                    }
                }
                return;
            }
        }

        // 2. Fast Top Bar Navigation (Instant D-Pad horizontal & jump down into active grid)
        const inTopBar = currentEl && (currentEl.classList?.contains("nav-item") || currentEl.classList?.contains("nav-action-btn") || Boolean(currentEl.closest(".topbar")));
        if (inTopBar) {
            if (e.key === "ArrowDown") {
                const firstCard = document.querySelector(".view.active-view .media-card") || document.querySelector("#catalog-grid .media-card");
                if (firstCard) {
                    firstCard.focus();
                    firstCard.scrollIntoView({ block: 'nearest', inline: 'nearest' });
                    return;
                }
            } else if (e.key === "ArrowLeft" || e.key === "ArrowRight") {
                const topbarButtons = Array.from(document.querySelectorAll(".topbar .nav-item, .topbar .nav-action-btn")).filter(b => b.offsetParent !== null && !b.disabled);
                const bIdx = topbarButtons.indexOf(currentEl);
                if (bIdx !== -1) {
                    const nextBIdx = e.key === "ArrowRight" ? bIdx + 1 : bIdx - 1;
                    if (nextBIdx >= 0 && nextBIdx < topbarButtons.length) {
                        topbarButtons[nextBIdx].focus();
                        return;
                    }
                }
            }
        }

        // 3. Fast deterministic navigation between Filter Bar, Genre Bar, and Catalog Grid
        const inFilterBar = currentEl && (currentEl.closest("#catalog-filter-bar") || currentEl.classList?.contains("filter-select") || currentEl.classList?.contains("filter-reset-btn"));
        if (inFilterBar) {
            if (e.key === "ArrowDown") {
                const firstCard = document.querySelector("#catalog-grid .media-card");
                if (firstCard) {
                    firstCard.focus();
                    firstCard.scrollIntoView({ block: 'nearest', inline: 'nearest' });
                    return;
                }
                return;
            } else if (e.key === "ArrowUp") {
                const topbarNav = document.getElementById("nav-search") || document.querySelector(".topbar .nav-item");
                if (topbarNav) {
                    topbarNav.focus();
                    window.scrollTo({ top: 0, behavior: 'instant' });
                    return;
                }
            }
        }

        const inGenreBar = currentEl && (currentEl.classList?.contains("genre-chip") || currentEl.closest("#genre-bar"));
        if (inGenreBar) {
            if (e.key === "ArrowDown") {
                const firstCard = document.querySelector("#catalog-grid .media-card");
                if (firstCard) {
                    firstCard.focus();
                    firstCard.scrollIntoView({ block: 'nearest', inline: 'nearest' });
                    return;
                }
            } else if (e.key === "ArrowUp") {
                const topbarNav = document.getElementById("nav-search") || document.querySelector(".topbar .nav-item");
                if (topbarNav) {
                    topbarNav.focus();
                    window.scrollTo({ top: 0, behavior: 'instant' });
                    return;
                }
            }
        }

        // 4. Fallback Spatial Navigation (Only runs for modals, drawers, or complex non-grid views)
        let searchRoots = [];
        if (playerModal && playerModal.style.display !== "none") {
            if (activeDrawer) {
                searchRoots = [document.getElementById(`player-drawer-${activeDrawer}`) || playerModal];
            } else {
                searchRoots = [playerModal];
            }
        } else {
            const currentModal = document.querySelector(".modal[style*='display: flex'], .modal[style*='display: block']");
            if (currentModal) {
                searchRoots = [currentModal];
            } else if (currentView === "details") {
                searchRoots = [document.getElementById("view-details") || document.body];
            } else {
                const topbar = document.querySelector(".topbar");
                const activeView = document.getElementById(`view-${currentView}`);
                if (topbar) searchRoots.push(topbar);
                if (activeView) searchRoots.push(activeView);
            }
        }

        const focusables = [];
        const focusableSelector = "button:not([disabled]), [tabindex='0'], .player-skip-btn, .media-card, .stream-chip, .source-tab-btn, .genre-chip, .modal-tab-btn, .translator-chip, .season-chip, .episode-chip, .settings-tab-btn, .settings-select, .settings-input, .settings-toggle input, .osd-ctrl-btn, .osd-btn-back, .osd-center-play-btn, .player-drawer-item, .player-drawer-close, .up-next-actions button, #osd-progress-track, .filter-select, .filter-reset-btn, input, textarea";

        searchRoots.forEach(root => {
            root.querySelectorAll(focusableSelector).forEach(el => {
                if (el.offsetParent !== null && !el.disabled) {
                    focusables.push(el);
                }
            });
        });

        const currentIndex = focusables.indexOf(document.activeElement);
        if (currentIndex === -1) {
            if (focusables.length > 0) focusables[0].focus();
            return;
        }

        const currentRect = currentEl.getBoundingClientRect();
        const isFromCatalog = currentEl && (currentEl.classList.contains("media-card") || Boolean(currentEl.closest("#catalog-grid")));
        const isFromTopBar = currentEl && (currentEl.classList.contains("nav-item") || Boolean(currentEl.closest(".topbar")));
        let bestTarget = null;
        let bestDistance = Infinity;

        focusables.forEach((target, idx) => {
            if (idx === currentIndex) return;
            const targetRect = target.getBoundingClientRect();

            let isValidDirection = false;
            let distance = 0;

            const isTargetInCatalog = target.classList.contains("media-card") || Boolean(target.closest("#catalog-grid"));
            const isTargetInTopBar = target.classList.contains("nav-item") || Boolean(target.closest(".topbar"));

            if (e.key === "ArrowRight") {
                // If in catalog, moving sideways MUST NOT jump to the topbar!
                if (isFromCatalog && isTargetInTopBar) return;
                if (isFromTopBar && !isTargetInTopBar) return;

                if (targetRect.left >= currentRect.right - 10) {
                    const verticalOverlap = Math.max(0, Math.min(currentRect.bottom, targetRect.bottom) - Math.max(currentRect.top, targetRect.top));
                    const dy = Math.abs(targetRect.top - currentRect.top);
                    if (verticalOverlap > 0 || dy < 90) {
                        isValidDirection = true;
                        distance = Math.hypot(targetRect.left - currentRect.right, dy * 4);
                    }
                }
            } else if (e.key === "ArrowLeft") {
                // If in catalog, moving sideways MUST NOT jump to the topbar!
                if (isFromCatalog && isTargetInTopBar) return;
                if (isFromTopBar && !isTargetInTopBar) return;

                if (targetRect.right <= currentRect.left + 10) {
                    const verticalOverlap = Math.max(0, Math.min(currentRect.bottom, targetRect.bottom) - Math.max(currentRect.top, targetRect.top));
                    const dy = Math.abs(targetRect.top - currentRect.top);
                    if (verticalOverlap > 0 || dy < 90) {
                        isValidDirection = true;
                        distance = Math.hypot(currentRect.left - targetRect.right, dy * 4);
                    }
                }
            } else if (e.key === "ArrowDown" && targetRect.top >= currentRect.bottom - 10) {
                isValidDirection = true;
                distance = Math.hypot(targetRect.top - currentRect.bottom, (targetRect.left - currentRect.left) * 1.5);
            } else if (e.key === "ArrowUp" && targetRect.bottom <= currentRect.top + 10) {
                isValidDirection = true;
                distance = Math.hypot(currentRect.top - targetRect.bottom, (targetRect.left - currentRect.left) * 1.5);
            }

            if (isValidDirection && distance < bestDistance) {
                bestDistance = distance;
                bestTarget = target;
            }
        });

        if (bestTarget) {
            if (bestTarget.classList.contains("nav-item") || bestTarget.classList.contains("nav-action-btn") || Boolean(bestTarget.closest(".topbar"))) {
                const searchBtn = document.getElementById("nav-search");
                if (searchBtn && (!currentEl || !currentEl.closest(".topbar"))) {
                    bestTarget = searchBtn;
                }
            }
            bestTarget.focus();
            bestTarget.scrollIntoView({ block: 'nearest', inline: 'nearest' });
        }
    }
}

function focusFirstElement(selector) {
    setTimeout(() => {
        const el = document.querySelector(selector);
        if (el) el.focus();
    }, 100);
}

/* =========================================================
   Catalog & Search Operations (with TV Connection Manager)
   ========================================================= */
let connectionRetryTimer = null;

function initServerConnection() {
    const btnSave = document.getElementById("btn-server-save");
    const btnRetry = document.getElementById("btn-server-retry");
    const btnCancel = document.getElementById("btn-server-cancel");
    const input = document.getElementById("server-ip-input");
    const currentUrl = document.getElementById("server-current-url");
    const connModal = document.getElementById("server-connect-modal");

    const saved = localStorage.getItem("showhub_server") || "https://showhub-server.onrender.com";
    if (input) input.value = saved;
    if (currentUrl) currentUrl.textContent = saved;

    function normalizeServerUrl(val) {
        let u = (val || "").trim();
        if (!u) return "https://showhub-server.onrender.com";
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = (u.includes("onrender.com") || !u.match(/^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}/)) ? ("https://" + u) : ("http://" + u);
        }
        try {
            const parsed = new URL(u);
            if (!parsed.port && parsed.hostname.match(/^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/)) {
                u = u.replace(/\/+$/, "") + ":8000";
            }
        } catch(e) {}
        return u.replace(/\/+$/, "");
    }

    btnSave?.addEventListener("click", () => {
        if (input && input.value.trim()) {
            const normalized = normalizeServerUrl(input.value);
            input.value = normalized;
            localStorage.setItem("showhub_server", normalized);
            if (currentUrl) currentUrl.textContent = normalized;
            if (connModal) connModal.style.display = "none";
            loadCatalog();
        }
    });

    btnRetry?.addEventListener("click", () => {
        loadCatalog();
    });

    btnCancel?.addEventListener("click", () => {
        if (connModal) connModal.style.display = "none";
    });
}

let offlineCatalogCache = null;

async function loadInitialCatalogFallback() {
    if (!offlineCatalogCache) {
        try {
            const fallbackPath = (window.location.protocol === "file:") ? "initial_catalog.json" : "/static/initial_catalog.json";
            const res = await originalFetch(fallbackPath);
            if (res.ok) offlineCatalogCache = await res.json();
        } catch (e) {}
    }
    if (offlineCatalogCache && offlineCatalogCache.length > 0) {
        let filtered = offlineCatalogCache;
        const typeVal = document.getElementById("filter-type")?.value || "all";
        const countryVal = document.getElementById("filter-country")?.value || "all";

        if (currentCategory === "movies" || typeVal === "movie") filtered = filtered.filter(it => !it.is_series);
        else if (currentCategory === "series" || typeVal === "series") filtered = filtered.filter(it => it.is_series);
        else if (currentCategory === "cartoons") filtered = filtered.filter(it => (it.description || "").toLowerCase().includes("мульт"));
        else if (currentCategory === "anime") filtered = filtered.filter(it => (it.description || "").toLowerCase().includes("аниме"));

        if (currentGenre) {
            const g = currentGenre.toLowerCase().trim();
            const stem = g.endsWith("ия") ? g.slice(0, -2) : (g.endsWith("а") ? g.slice(0, -1) : g);
            filtered = filtered.filter(it => (it.description || "").toLowerCase().includes(stem) || (it.description || "").toLowerCase().includes(g));
        }
        if (countryVal && countryVal !== "all") {
            const c = countryVal.toLowerCase().trim();
            filtered = filtered.filter(it => (it.description || "").toLowerCase().includes(c));
        }
        return filtered;
    }
    return [];
}

function showGridSkeleton(container, message = "Загрузка каталога новинок...") {
    if (!container) return;
    const skeletonCards = Array(12).fill(`
        <div class="skeleton-card">
            <div class="skeleton-poster"></div>
            <div class="skeleton-info">
                <div class="skeleton-line"></div>
                <div class="skeleton-line short"></div>
            </div>
        </div>
    `).join("");

    container.innerHTML = `
        <div class="grid-loading-box">
            <div class="spinner-neon"></div>
            <span class="spinner-text">${message}</span>
        </div>
        ${skeletonCards}
    `;
}

const clientCatalogCache = new Map();
const POPULAR_GENRES = ["боевик", "комедия", "фантастика", "драма", "триллер", "ужасы", "приключения", "фэнтези", "детектив", "криминал"];

async function preloadPopularGenres() {
    for (const g of POPULAR_GENRES) {
        const cKey = `all_${g}_newest_all_all_0_1`;
        if (!clientCatalogCache.has(cKey)) {
            try {
                const res = await fetch(`/api/catalog?category=all&genre=${encodeURIComponent(g)}&sort_by=newest&content_type=all&page=1`);
                if (res.ok) {
                    const data = await res.json();
                    if (data && data.length > 0) {
                        clientCatalogCache.set(cKey, data);
                    }
                }
            } catch (e) {}
            await new Promise(r => setTimeout(r, 600));
        }
    }
}

let catalogPage = 1;
let catalogLoadingMore = false;
let catalogHasMore = true;

async function loadCatalog(isAppend = false) {
    const grid = document.getElementById("catalog-grid");
    const connModal = document.getElementById("server-connect-modal");
    const loadMoreContainer = document.getElementById("catalog-load-more-container");
    const loadMoreBtn = document.getElementById("btn-catalog-load-more");

    const sortVal = document.getElementById("filter-sort")?.value || "newest";
    const yearVal = document.getElementById("filter-year")?.value || "all";
    const ratingVal = document.getElementById("filter-rating")?.value || "0";
    const typeVal = document.getElementById("filter-type")?.value || "all";
    const countryVal = document.getElementById("filter-country")?.value || "all";
    const cacheKey = `${currentCategory}_${currentGenre}_${sortVal}_${typeVal}_${yearVal}_${ratingVal}_${isAppend ? catalogPage + 1 : 1}`;

    let prevCount = 0;

    if (isAppend) {
        if (catalogLoadingMore || !catalogHasMore) return;
        catalogLoadingMore = true;
        catalogPage++;
        prevCount = grid ? grid.querySelectorAll(".media-card").length : 0;
        if (loadMoreBtn) {
            loadMoreBtn.disabled = true;
            loadMoreBtn.innerHTML = `<span class="spinner-neon-sm" style="border-width:2px; width:14px; height:14px; display:inline-block; vertical-align:middle; margin-right:6px;"></span> Загрузка страницы ${catalogPage}...`;
        }
    } else {
        catalogPage = 1;
        catalogHasMore = true;
        catalogLoadingMore = false;
        if (loadMoreContainer) loadMoreContainer.style.display = "none";

        // Fast client-side cache display: if this exact query was previously cached, render immediately without spinner!
        if (clientCatalogCache.has(cacheKey)) {
            const cachedItems = clientCatalogCache.get(cacheKey);
            if (cachedItems && cachedItems.length > 0) {
                renderMediaCards(cachedItems, grid);
            }
        } else {
            showGridSkeleton(grid, currentGenre ? `Загрузка жанра: ${currentGenre}...` : "Загрузка каталога ShowHub TV...");
            const offlineCached = await loadInitialCatalogFallback();
            if (offlineCached.length > 0 && !grid.querySelector(".media-card")) {
                renderMediaCards(offlineCached, grid);
            }
        }
    }

    try {
        let url = `/api/catalog?category=${encodeURIComponent(currentCategory)}&genre=${encodeURIComponent(currentGenre)}&sort_by=${encodeURIComponent(sortVal)}&content_type=${encodeURIComponent(typeVal)}&page=${catalogPage}`;
        if (yearVal && yearVal !== "all") {
            url += `&year=${encodeURIComponent(yearVal)}`;
        }
        if (ratingVal && parseFloat(ratingVal) > 0) {
            url += `&min_rating=${encodeURIComponent(ratingVal)}`;
        }
        if (countryVal && countryVal !== "all") {
            url += `&country=${encodeURIComponent(countryVal)}`;
        }
        const res = await fetch(url);
        if (!res.ok) throw new Error("HTTP " + res.status);
        const items = await res.json();

        // Cache response in memory
        if (items && items.length > 0) {
            clientCatalogCache.set(cacheKey, items);
        }

        // Success: hide connection modal
        if (connModal) connModal.style.display = "none";
        if (connectionRetryTimer) {
            clearTimeout(connectionRetryTimer);
            connectionRetryTimer = null;
        }

        if (isAppend) {
            if (!items || items.length === 0) {
                catalogHasMore = false;
                if (loadMoreContainer) loadMoreContainer.style.display = "none";
            } else {
                appendMediaCards(items, grid);
                if (loadMoreContainer) {
                    loadMoreContainer.style.display = "flex";
                    if (loadMoreBtn) {
                        loadMoreBtn.disabled = false;
                        loadMoreBtn.innerHTML = `<svg class="btn-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"></polyline></svg><span>Загрузить ещё фильмы (Стр. ${catalogPage + 1})</span>`;
                    }
                }
                // Move focus to first card of the newly loaded page
                const allCards = grid.querySelectorAll(".media-card");
                if (allCards.length > prevCount) {
                    const firstNewCard = allCards[prevCount];
                    if (firstNewCard) {
                        firstNewCard.focus();
                        firstNewCard.scrollIntoView({ block: 'center', inline: 'nearest' });
                    }
                }
            }
        } else {
            renderMediaCards(items, grid);
            const modalOpen = document.querySelector(".modal[style*='display: flex'], .modal[style*='display: block'], .modal-backdrop:not([style*='display: none'])");
            const activeTag = document.activeElement ? document.activeElement.tagName.toLowerCase() : "";
            if (!modalOpen && activeTag !== "input" && activeTag !== "select" && activeTag !== "textarea") {
                const firstCard = grid.querySelector(".media-card");
                if (firstCard) {
                    firstCard.focus();
                    firstCard.scrollIntoView({ block: 'nearest', inline: 'nearest' });
                }
            }
            if (loadMoreContainer) {
                if (items && items.length >= 20) {
                    loadMoreContainer.style.display = "flex";
                    if (loadMoreBtn) {
                        loadMoreBtn.disabled = false;
                        loadMoreBtn.innerHTML = `<svg class="btn-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"></polyline></svg><span>Загрузить ещё фильмы (Стр. 2)</span>`;
                    }
                } else {
                    loadMoreContainer.style.display = "none";
                }
            }
        }
    } catch (err) {
        console.warn("Catalog fetch from PC server failed, maintaining standalone autonomous catalog:", err);
        if (!isAppend) {
            const cachedItems = await loadInitialCatalogFallback();
            if (cachedItems.length > 0) {
                renderMediaCards(cachedItems, grid);
            } else {
                grid.innerHTML = `<div class="error-state">Автономный режим ShowHub TV: загрузка каталога...</div>`;
            }
        }
    } finally {
        catalogLoadingMore = false;
    }
}

function initSearch() {
    const input = document.getElementById("search-input");
    const btn = document.getElementById("search-btn");

    btn?.addEventListener("click", () => {
        performSearch(input.value);
    });

    // View search hero buttons
    const viewInput = document.getElementById("search-view-input");
    const viewBtn = document.getElementById("btn-search-view-submit");
    const viewClear = document.getElementById("btn-search-view-clear");
    const openModalBtn = document.getElementById("btn-open-search-modal");

    viewBtn?.addEventListener("click", () => {
        performSearch(viewInput?.value);
    });
    viewClear?.addEventListener("click", () => {
        if (viewInput) { viewInput.value = ""; viewInput.focus(); }
    });
    openModalBtn?.addEventListener("click", () => {
        openSearchModal();
    });

    // Modal buttons
    const modalInput = document.getElementById("search-modal-input");
    const modalBtn = document.getElementById("btn-search-modal-submit");
    const modalClear = document.getElementById("btn-search-modal-clear");
    const modalClose = document.getElementById("btn-close-search-modal");
    const modalCancel = document.getElementById("btn-search-modal-cancel");

    modalBtn?.addEventListener("click", () => {
        const val = modalInput?.value;
        closeSearchModal();
        performSearch(val);
    });
    modalClear?.addEventListener("click", () => {
        if (modalInput) { modalInput.value = ""; modalInput.focus(); }
    });
    modalClose?.addEventListener("click", closeSearchModal);
    modalCancel?.addEventListener("click", closeSearchModal);

    // Suggestion chips in both view and modal
    document.querySelectorAll(".search-chip").forEach(chip => {
        chip.addEventListener("click", () => {
            const q = chip.getAttribute("data-query");
            closeSearchModal();
            performSearch(q);
        });
    });
}

async function performSearch(query) {
    if (!query || !query.trim()) return;
    const cleanQuery = query.trim();

    // Sync all search inputs
    const topInput = document.getElementById("search-input");
    const viewInput = document.getElementById("search-view-input");
    const modalInput = document.getElementById("search-modal-input");
    if (topInput) topInput.value = cleanQuery;
    if (viewInput) viewInput.value = cleanQuery;
    if (modalInput) modalInput.value = cleanQuery;

    saveSearchHistory(cleanQuery);

    currentView = "search";
    document.querySelectorAll(".view").forEach(v => v.style.display = "none");
    const activeView = document.getElementById("view-search");
    if (activeView) activeView.style.display = "block";

    // Set active nav link
    document.querySelectorAll(".nav-item").forEach(n => {
        n.classList.toggle("active", n.id === "nav-search");
    });

    const grid = document.getElementById("search-grid");
    const subtitle = document.getElementById("search-subtitle");
    subtitle.textContent = `Поиск: "${cleanQuery}" во всех источниках...`;
    showGridSkeleton(grid, `Поиск "${cleanQuery}" во всех источниках...`);

    try {
        const res = await fetch(`/api/search?q=${encodeURIComponent(cleanQuery)}`);
        if (!res.ok) throw new Error("HTTP " + res.status);
        const items = await res.json();
        subtitle.textContent = `Найдено результатов: ${items.length} для "${cleanQuery}"`;
        renderMediaCards(items, grid);
    } catch (err) {
        console.warn("PC search failed, running direct autonomous TV search:", err);
        try {
            const bazonRes = await originalFetch(`https://bazon.cc/api/search?token=8488e0b2067756f2e82f5b82fb2ec686&title=${encodeURIComponent(cleanQuery)}`);
            const bData = await bazonRes.json();
            const bItems = (bData && bData.results) ? bData.results.map(it => ({
                id: String(it.kinopoisk_id || it.id),
                source_name: "bazon",
                title: it.name_rus || it.name_eng || cleanQuery,
                year: it.year,
                is_series: Boolean(it.serial),
                poster: it.poster || "noposter.png",
                kinopoisk_id: it.kinopoisk_id,
                extra_data: { embed: it.link, playlists: it.playlists }
            })) : [];
            subtitle.textContent = `Найдено результатов: ${bItems.length} для "${cleanQuery}" (Автономно)`;
            renderMediaCards(bItems, grid);
        } catch (e2) {
            grid.innerHTML = `<div class="error-state">Ошибка поиска: ${err.message}</div>`;
        }
    }
}

function formatVoteCount(num) {
    if (!num || isNaN(num) || Number(num) <= 0) return "";
    const n = Number(num);
    if (n >= 1000000) return (n / 1000000).toFixed(1) + "M";
    if (n >= 1000) return (n / 1000).toFixed(1) + "k";
    return String(n);
}

function handlePosterError(img, encodedTitle, year, kpId) {
    if (!img || img.getAttribute("data-poster-resolved")) {
        if (img) img.src = "noposter.png";
        return;
    }
    img.setAttribute("data-poster-resolved", "1");
    const title = decodeURIComponent(encodedTitle || "");
    if (!title) {
        img.src = "noposter.png";
        return;
    }
    const url = `/api/media/poster?title=${encodeURIComponent(title)}&year=${encodeURIComponent(year || '')}&kp_id=${encodeURIComponent(kpId || '')}`;
    fetch(url)
        .then(r => r.json())
        .then(d => {
            if (d && d.poster && !d.poster.includes("no_image_poster") && !d.poster.includes("noposter")) {
                img.src = d.poster;
            } else {
                img.src = "noposter.png";
            }
        })
        .catch(() => {
            img.src = "noposter.png";
        });
}

function buildSingleMediaCardHtml(it, favsObj = null) {
    if (!favsObj) favsObj = getFavorites();
    const prog = getMediaProgress(it.id, it.kinopoisk_id, it.title, it.source_name, it.year);
    let progressBadgeHtml = "";
    if (prog && prog.watched) {
        progressBadgeHtml = `<div class="card-watched-badge">✓</div>`;
    } else if (prog && prog.percentage >= 3) {
        progressBadgeHtml = `<div class="card-progress-bar"><div class="card-progress-fill" style="width: ${prog.percentage}%;"></div></div>`;
    }

    const ratingVal = it.rating_kp || it.rating_imdb;
    const voteCount = it.vote_num_kp || it.vote_num_imdb || it.extra_data?.vote_num_kp || it.extra_data?.vote_num_imdb;
    const formattedVotes = formatVoteCount(voteCount);
    let ratingBadgeHtml = "";
    if (ratingVal) {
        const rScore = Number(ratingVal).toFixed(1);
        ratingBadgeHtml = `<span class="media-badge-rating">★ ${rScore}${formattedVotes ? ` <span class="rating-votes">(${formattedVotes})</span>` : ''}</span>`;
    }

    const idKey = String(it.id || it.kinopoisk_id);
    const isFavItem = favsObj && favsObj[idKey];
    const newEpBadgeHtml = (isFavItem && isFavItem.hasNewEpisode) ? `<span class="card-new-ep-badge">Новая серия</span>` : "";
    const epInfo = it.episodes_info || it.extra_data?.episodes_info;
    const seriesBadgeHtml = (it.is_series && epInfo && !newEpBadgeHtml) 
        ? `<span class="card-series-badge">${epInfo}</span>` 
        : "";

    const isBadPoster = !it.poster || it.poster.includes("no_image_poster") || it.poster.includes("noposter");
    const safePoster = isBadPoster ? "noposter.png" : it.poster;
    const needResolveAttr = isBadPoster ? ` data-need-resolve="1"` : "";

    return `
    <div class="media-card" tabindex="0" 
         data-id="${it.id}" 
         data-source="${it.source_name}" 
         data-kp="${it.kinopoisk_id || ''}" 
         data-title="${encodeURIComponent(it.title)}"
         data-poster="${encodeURIComponent(it.poster || '')}"
         data-year="${it.year || ''}"
         data-series="${it.is_series ? '1' : '0'}">
        <div class="media-poster-wrapper">
            <img class="media-poster" src="${safePoster}" alt="${it.title}" loading="lazy" decoding="async"${needResolveAttr} onerror="handlePosterError(this, '${encodeURIComponent(it.title)}', '${it.year || ''}', '${it.kinopoisk_id || ''}')">
            <span class="media-badge-source">${it.source_name}</span>
            ${ratingBadgeHtml}
            ${newEpBadgeHtml}
            ${seriesBadgeHtml}
            ${progressBadgeHtml}
            <div class="card-preview-timeline"><div class="card-preview-progress"></div></div>
        </div>
        <div class="media-info">
            <div class="media-title" title="${it.title}"><span class="title-text">${it.title}</span></div>
            <div class="media-meta">${it.year || 'Н/Д'} • ${it.is_series ? (epInfo || 'Сериал') : 'Фильм'}</div>
        </div>
    </div>
    `;
}

let activeTvMarqueeTitle = null;
let activeTvFocusedCard = null;

function stopAllCardMarquees(container) {
    if (activeTvMarqueeTitle) {
        activeTvMarqueeTitle.classList.remove("marquee-scrolling");
        const s = activeTvMarqueeTitle.querySelector(".title-text");
        if (s) {
            s.style.removeProperty("--marquee-distance");
            s.style.removeProperty("--marquee-duration");
        }
        activeTvMarqueeTitle = null;
    }
}

function attachCardEvents(card, container) {
    const img = card.querySelector(".media-poster");
    if (img && img.hasAttribute("data-need-resolve")) {
        img.removeAttribute("data-need-resolve");
        const title = decodeURIComponent(card.getAttribute("data-title") || "");
        const year = card.getAttribute("data-year") || "";
        const kpId = card.getAttribute("data-kp") || "";
        handlePosterError(img, encodeURIComponent(title), year, kpId);
    }

    card.addEventListener("click", () => {
        stopCardPreview(card);
        openMediaModal(card);
    });
    card.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
            stopCardPreview(card);
            openMediaModal(card);
        }
    });
    card.addEventListener("focus", () => {
        if (activeTvFocusedCard && activeTvFocusedCard !== card) {
            activeTvFocusedCard.classList.remove("tv-focused");
        }
        card.classList.add("tv-focused");
        activeTvFocusedCard = card;
        handleCardFocus(card);

        // Cancel previous timers & animations immediately on focus switch
        if (cardFocusTimer) {
            clearTimeout(cardFocusTimer);
            cardFocusTimer = null;
        }
        if (cardVideoPreviewTimer) {
            clearTimeout(cardVideoPreviewTimer);
            cardVideoPreviewTimer = null;
        }
        stopAllCardMarquees(container);

        const timeline = card.querySelector(".card-preview-timeline");
        if (timeline) {
            timeline.classList.remove("active");
            timeline.classList.remove("playing");
        }

        // 2-second settle delay: prevents animation or lag when quickly jumping between cards
        cardFocusTimer = setTimeout(() => {
            if (document.activeElement !== card) return;

            // 1. Running line (marquee) if title overflows
            const titleEl = card.querySelector(".media-title");
            const span = titleEl?.querySelector(".title-text");
            if (titleEl && span) {
                const distance = span.scrollWidth - titleEl.clientWidth;
                if (distance > 3) {
                    const duration = Math.max(3.2, Math.min(12.0, distance / 22));
                    span.style.setProperty("--marquee-distance", `${distance + 8}px`);
                    span.style.setProperty("--marquee-duration", `${duration}s`);
                    titleEl.classList.add("marquee-scrolling");
                    activeTvMarqueeTitle = titleEl;
                }
            }

            // 2. Animate preview countdown timeline
            if (timeline) {
                timeline.classList.remove("playing");
                timeline.classList.remove("active");
                void timeline.offsetWidth;
                timeline.classList.add("active");
            }

            // 3. Silent video preview after countdown completes (2.7s)
            cardVideoPreviewTimer = setTimeout(() => {
                if (document.activeElement === card) {
                    startCardPreview(card);
                }
            }, 2700);
        }, 2000);
    });
    card.addEventListener("blur", () => {
        card.classList.remove("tv-focused");
        if (cardFocusTimer) {
            clearTimeout(cardFocusTimer);
            cardFocusTimer = null;
        }
        if (cardVideoPreviewTimer) {
            clearTimeout(cardVideoPreviewTimer);
            cardVideoPreviewTimer = null;
        }
        const timeline = card.querySelector(".card-preview-timeline");
        if (timeline) {
            timeline.classList.remove("active");
            timeline.classList.remove("playing");
        }
        const titleEl = card.querySelector(".media-title");
        const span = titleEl?.querySelector(".title-text");
        if (titleEl) titleEl.classList.remove("marquee-scrolling");
        if (span) {
            span.style.removeProperty("--marquee-distance");
            span.style.removeProperty("--marquee-duration");
        }
        stopCardPreview(card);
    });
}

function renderMediaCards(items, container) {
    if (!items || items.length === 0) {
        container.innerHTML = `<div class="empty-state">Ничего не найдено. Попробуйте другой запрос или категорию.</div>`;
        return;
    }

    const favsObj = getFavorites();
    container.innerHTML = items.map(it => buildSingleMediaCardHtml(it, favsObj)).join("");
    container.querySelectorAll(".media-card").forEach(card => attachCardEvents(card, container));
}

function appendMediaCards(items, container) {
    if (!items || items.length === 0) return;
    const existingIds = new Set();
    container.querySelectorAll(".media-card").forEach(c => {
        const cid = c.getAttribute("data-id") || c.getAttribute("data-kp");
        if (cid) existingIds.add(cid);
    });

    const newItems = items.filter(it => {
        const idKey = String(it.id || it.kinopoisk_id);
        return !existingIds.has(idKey);
    });
    if (newItems.length === 0) return;

    const favsObj = getFavorites();
    const tempDiv = document.createElement("div");
    tempDiv.innerHTML = newItems.map(it => buildSingleMediaCardHtml(it, favsObj)).join("");
    const newCards = Array.from(tempDiv.children);

    newCards.forEach(card => {
        container.appendChild(card);
        attachCardEvents(card, container);
    });
}

function updatePlayButtonLabels() {
    const btnPlay = document.getElementById("btn-modal-play");
    const btnBottomMovie = document.getElementById("btn-bottom-play-movie");
    const btnBottomAbout = document.getElementById("btn-bottom-play-about");
    const playHtml = `<svg class="btn-icon-svg" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg><span>Смотреть</span>`;
    const playMovieHtml = `<svg class="btn-icon-svg" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg><span>Смотреть фильм</span>`;
    if (btnPlay) btnPlay.innerHTML = playHtml;
    if (btnBottomMovie) btnBottomMovie.innerHTML = playMovieHtml;
    if (btnBottomAbout) btnBottomAbout.innerHTML = playHtml;
}

function updateModalProgressUI() {
    const prog = getMediaProgress(currentMediaItem?.id, currentMediaItem?.kpId, currentMediaItem?.title, currentMediaItem?.source, currentMediaItem?.year);
    const banner = document.getElementById("modal-progress-banner");
    const bannerText = document.getElementById("modal-progress-text");
    const btnToggleWatched = document.getElementById("btn-modal-toggle-watched");

    if (btnToggleWatched) {
        if (prog && prog.watched) {
            btnToggleWatched.textContent = "✓ Просмотрено (Снять)";
        } else {
            btnToggleWatched.textContent = "○ Отметить просмотренным";
        }
    }

    if (prog && prog.positionSec > 15 && !prog.watched) {
        if (banner) banner.style.display = "flex";
        if (bannerText) {
            if (prog.isSeries && prog.season && prog.episode) {
                bannerText.textContent = `Остановились: Сезон ${prog.season}, Серия ${prog.episode} (${formatDuration(prog.positionSec)})`;
            } else {
                bannerText.textContent = `Остановились на ${formatDuration(prog.positionSec)} (${prog.percentage || 0}%)`;
            }
        }
    } else {
        if (banner) banner.style.display = "none";
    }
}

/* =========================================================
   Media Details & Source Picker Modal
   ========================================================= */
async function openMediaModal(card) {
    stopCardPreview();
    stopDetailsPreview();
    // 1. Immediately close player if open
    closePlayer();

    // 2. Extract accurate info from card data attributes
    const id = card.getAttribute("data-id");
    const source = card.getAttribute("data-source");
    const kpId = card.getAttribute("data-kp");
    const title = decodeURIComponent(card.getAttribute("data-title") || card.querySelector(".media-title")?.textContent || "");
    const poster = decodeURIComponent(card.getAttribute("data-poster") || card.querySelector(".media-poster")?.src || "");
    const year = card.getAttribute("data-year") || "";
    const isSeries = card.getAttribute("data-series") === "1";

    const requestId = ++activeModalRequestId;

    // Track navigation history and previous focused card
    if (currentView && currentView !== "details") {
        previousView = currentView;
    } else if (!previousView || previousView === "details") {
        previousView = "catalog";
    }
    lastFocusedCardElement = card;

    // 3. FULL STATE RESET - prevents old movie sticking!
    currentMediaItem = { id, source, kpId, title, poster, year, isSeries };
    selectedStream = null;
    currentStreams = null;
    activeTranslatorId = null;

    const prog = getMediaProgress(id, kpId, title, source, year);
    if (prog && prog.audio_id) {
        activeTranslatorId = prog.audio_id;
    }
    if (isSeries && prog && prog.season && prog.episode) {
        activeSeasonId = prog.season;
        activeEpisodeId = prog.episode;
    } else {
        activeSeasonId = isSeries ? 1 : null;
        activeEpisodeId = isSeries ? 1 : null;
    }

    // 4. Switch to Dedicated Fullscreen Details View
    switchView("details");

    // Start silent video preview after 1.5s on details screen
    if (detailsPreviewTimer) clearTimeout(detailsPreviewTimer);
    detailsPreviewTimer = setTimeout(() => {
        if (currentView === "details" && currentMediaItem) {
            startDetailsPreview(currentMediaItem);
        }
    }, 1500);

    // Clear new episode badge for this favorite series
    if (isSeries) {
        try {
            const favs = getFavorites();
            const idKey = String(id || kpId);
            if (favs[idKey] && favs[idKey].hasNewEpisode) {
                favs[idKey].hasNewEpisode = false;
                localStorage.setItem(FAVORITES_KEY, JSON.stringify(favs));
                const stillHasNew = Object.values(favs).some(f => f.hasNewEpisode);
                const badge = document.getElementById("fav-new-badge");
                if (badge) badge.style.display = stillHasNew ? "inline-block" : "none";
            }
        } catch (e) {}
    }

    // Breadcrumbs
    const bcCat = document.getElementById("details-bc-category");
    if (bcCat) {
        if (previousView === "search") bcCat.textContent = "Поиск";
        else if (previousView === "history") bcCat.textContent = "История просмотров";
        else bcCat.textContent = "Каталог ShowHub TV";
    }
    const bcTitle = document.getElementById("details-bc-title");
    if (bcTitle) bcTitle.textContent = title;

    // Reset Details UI elements
    const modalComp = document.getElementById("media-modal");
    if (modalComp) modalComp.style.display = "block";

    document.getElementById("modal-title").textContent = title;
    document.getElementById("modal-meta").textContent = `${year || 'Н/Д'} • ${isSeries ? 'Сериал' : 'Фильм'}`;
    document.getElementById("modal-poster").src = poster || "noposter.png";

    document.getElementById("rating-kp-box").style.display = "none";
    document.getElementById("rating-imdb-box").style.display = "none";
    document.getElementById("row-director").style.display = "none";
    document.getElementById("row-country").style.display = "none";
    document.getElementById("row-genres").style.display = "none";

    document.getElementById("section-translators").style.display = "none";
    document.getElementById("section-seasons").style.display = "none";
    document.getElementById("section-episodes").style.display = "none";
    const bottomMovieActions = document.getElementById("details-bottom-movie-actions");
    if (bottomMovieActions) bottomMovieActions.style.display = isSeries ? "none" : "flex";

    document.getElementById("modal-translators-list").innerHTML = "";
    document.getElementById("modal-seasons-list").innerHTML = "";
    document.getElementById("modal-episodes-list").innerHTML = "";

    const btnSchedule = document.getElementById("btn-tab-schedule");
    if (btnSchedule) btnSchedule.style.display = "none";
    const scheduleContainer = document.getElementById("modal-schedule-list");
    if (scheduleContainer) scheduleContainer.innerHTML = "";

    document.getElementById("modal-full-desc").innerHTML = `<div class="loading-inline"><div class="spinner-neon-sm"></div><span>Загрузка описания и характеристик...</span></div>`;
    document.getElementById("section-cast").style.display = "none";

    document.getElementById("comments-tab-count").textContent = "0";
    document.getElementById("modal-comments-list").innerHTML = `<div class="grid-loading-box" style="padding: 24px 0;"><div class="spinner-neon-sm"></div><span style="font-size: 14px;">Загрузка отзывов зрителей...</span></div>`;

    document.getElementById("modal-source-tabs").innerHTML = `<div class="loading-inline"><div class="spinner-neon-sm"></div><span>Поиск доступных источников...</span></div>`;
    document.getElementById("modal-streams-list").innerHTML = `<div class="loading-inline"><div class="spinner-neon-sm"></div><span>Проверка видеопотоков...</span></div>`;

    const btnPlay = document.getElementById("btn-modal-play");
    btnPlay.disabled = false;
    btnPlay.innerHTML = `<span style="display:inline-flex; align-items:center; gap:8px;"><span class="spinner-neon-sm" style="border-width:2px; width:16px; height:16px;"></span>Поиск потоков...</span>`;

    // Reset progress banner & watched button
    updateModalProgressUI();
    updatePlayButtonLabels();

    // Reset favorites and trailer button state
    updateFavoriteButtonUI(isFavorite(id));
    const btnTrailer = document.getElementById("btn-modal-trailer");
    if (btnTrailer) {
        btnTrailer.disabled = false;
        btnTrailer.innerHTML = "🎬 Трейлер";
    }

    // Reset tabs to Watch
    switchModalTab("watch");

    // Immediately set focus on interactive action button
    setTimeout(() => {
        const resumeBanner = document.getElementById("modal-progress-banner");
        if (resumeBanner && resumeBanner.style.display !== "none") {
            document.getElementById("btn-modal-resume")?.focus();
        } else {
            document.getElementById("btn-modal-play")?.focus();
        }
    }, 60);

    const queryId = kpId || id;
    const preloadKey = `${source}_${queryId}`;
    if (detailsPreloadCache.has(preloadKey)) {
        const cached = detailsPreloadCache.get(preloadKey);
        currentDetails = cached;
        renderDetailsUI(cached);
    }

    // 5. Fetch Details, Comments, and Streams in parallel
    fetchDetails(source, queryId, title, requestId, year, isSeries, kpId);
    fetchComments(source, queryId, title, requestId);
    fetchStreams(source, queryId, title, requestId, year, isSeries, kpId);
}

async function fetchDetails(source, queryId, title, reqId, year, isSeries, kpId) {
    const yr = year || currentMediaItem?.year;
    const isSer = (isSeries !== undefined && isSeries !== null) ? isSeries : currentMediaItem?.isSeries;
    const kp = kpId || currentMediaItem?.kpId;
    const preloadKey = `${source}_${queryId}_${yr || ''}_${isSer ? '1' : '0'}`;
    try {
        let url = `/api/media/details?source=${encodeURIComponent(source)}&media_id=${encodeURIComponent(queryId)}&title=${encodeURIComponent(title)}`;
        const validYr = getValidYear(yr);
        if (validYr) url += `&year=${encodeURIComponent(validYr)}`;
        if (isSer !== undefined && isSer !== null) url += `&is_series=${encodeURIComponent(isSer ? 1 : 0)}`;
        if (kp) url += `&kp_id=${encodeURIComponent(kp)}`;
        const res = await fetch(url);
        const details = await res.json();
        if (details && (details.title || details.description)) {
            detailsPreloadCache.set(preloadKey, details);
        }
        if (reqId !== activeModalRequestId) return; // Stale request, ignore

        currentDetails = details;
        renderDetailsUI(details);
    } catch (e) {
        console.warn("Error fetching details:", e);
    }
}

function renderDetailsUI(details) {
    currentDetails = details;
    if (details.poster) {
        const posterEl = document.getElementById("modal-poster");
        if (posterEl) posterEl.src = details.poster;
        if (currentMediaItem) currentMediaItem.poster = details.poster;
    }

    // Update series badge if detected
    if (details.is_series || (details.seasons && details.seasons.length > 0)) {
        if (currentMediaItem) currentMediaItem.isSeries = true;
        const year = currentMediaItem?.year || '';
        document.getElementById("modal-meta").textContent = `${year || 'Н/Д'} • Сериал`;
    }

    // Ratings
    if (details.rating_kp) {
        const kpBox = document.getElementById("rating-kp-box");
        document.getElementById("rating-kp-val").textContent = `${details.rating_kp}`;
        document.getElementById("rating-kp-votes").textContent = details.vote_num_kp ? `${(details.vote_num_kp / 1000).toFixed(0)}k оценок` : '';
        kpBox.style.display = "flex";
    }
    if (details.rating_imdb) {
        const imdbBox = document.getElementById("rating-imdb-box");
        document.getElementById("rating-imdb-val").textContent = `${details.rating_imdb}`;
        document.getElementById("rating-imdb-votes").textContent = details.vote_num_imdb ? `${(details.vote_num_imdb / 1000).toFixed(0)}k оценок` : '';
        imdbBox.style.display = "flex";
    }

    // Side Meta
    if (details.director) {
        document.getElementById("val-director").textContent = details.director;
        document.getElementById("row-director").style.display = "block";
    }
    if (details.country) {
        document.getElementById("val-country").textContent = details.country;
        document.getElementById("row-country").style.display = "block";
    }
    if (details.genres && details.genres.length) {
        document.getElementById("val-genres").textContent = details.genres.join(", ");
        document.getElementById("row-genres").style.display = "block";
    }

    // Full Description & Cast
    if (details.description) {
        document.getElementById("modal-full-desc").textContent = details.description;
    }
    if (details.actors) {
        document.getElementById("modal-cast-text").textContent = details.actors;
        document.getElementById("section-cast").style.display = "block";
    }

    // Translators / Audio Tracks
    if (details.translators && details.translators.length > 0) {
        const transSec = document.getElementById("section-translators");
        const transList = document.getElementById("modal-translators-list");
        transSec.style.display = "block";

        let defaultTranslatorIdx = 0;
        const settings = getSettings();
        const prog = getMediaProgress(currentMediaItem?.id, currentMediaItem?.kpId, currentMediaItem?.title, currentMediaItem?.source, currentMediaItem?.year);
        if (prog && prog.audio_id) {
            const matchedProgIdx = details.translators.findIndex(t => String(t.id) === String(prog.audio_id));
            if (matchedProgIdx !== -1) {
                defaultTranslatorIdx = matchedProgIdx;
            }
        } else if (settings.autoSelect && settings.voice && settings.voice !== "any") {
            const prefLower = settings.voice.toLowerCase();
            const matchedIdx = details.translators.findIndex(t => t.name.toLowerCase().includes(prefLower));
            if (matchedIdx !== -1) {
                defaultTranslatorIdx = matchedIdx;
            }
        }

        transList.innerHTML = details.translators.map((t, idx) => `
            <button class="translator-chip ${idx === defaultTranslatorIdx ? 'active' : ''}" data-id="${t.id}" tabindex="0">
                ${t.name}
            </button>
        `).join("");

        activeTranslatorId = details.translators[defaultTranslatorIdx].id;

        transList.querySelectorAll(".translator-chip").forEach(chip => {
            chip.addEventListener("click", () => {
                transList.querySelectorAll(".translator-chip").forEach(c => c.classList.remove("active"));
                chip.classList.add("active");
                activeTranslatorId = chip.getAttribute("data-id");
                reloadStreams();
            });
        });
    }

    // Seasons & Episodes (if series)
    if (details.is_series && details.seasons && details.seasons.length > 0) {
        const seasonSec = document.getElementById("section-seasons");
        const seasonList = document.getElementById("modal-seasons-list");
        seasonSec.style.display = "block";

        // Check if there is saved progress for a particular season
        const prog = getMediaProgress(currentMediaItem?.id, currentMediaItem?.kpId, currentMediaItem?.title, currentMediaItem?.source, currentMediaItem?.year);
        let defaultSeasonId = details.seasons[0].season_id;
        if (prog && prog.isSeries && prog.season) {
            const foundProgSeason = details.seasons.find(s => s.season_id === prog.season);
            if (foundProgSeason) {
                defaultSeasonId = prog.season;
            }
        }

        seasonList.innerHTML = details.seasons.map((s) => `
            <button class="season-chip ${s.season_id === defaultSeasonId ? 'active' : ''}" data-season="${s.season_id}" tabindex="0">
                ${s.title}
            </button>
        `).join("");

        activeSeasonId = defaultSeasonId;
        const initialSeason = details.seasons.find(s => s.season_id === defaultSeasonId) || details.seasons[0];
        renderEpisodesUI(initialSeason.episodes);

        seasonList.querySelectorAll(".season-chip").forEach(chip => {
            chip.addEventListener("click", () => {
                seasonList.querySelectorAll(".season-chip").forEach(c => c.classList.remove("active"));
                chip.classList.add("active");
                const sId = parseInt(chip.getAttribute("data-season"));
                activeSeasonId = sId;

                const foundSeason = details.seasons.find(s => s.season_id === sId);
                if (foundSeason) {
                    renderEpisodesUI(foundSeason.episodes);
                    reloadStreams();
                }
            });
        });
    }

    // 4. Render Episode Release Schedule if available or if series
    const btnSchedule = document.getElementById("btn-tab-schedule");
    const scheduleContainer = document.getElementById("modal-schedule-list");
    const scheduleItems = details.episodes_schedule || [];

    if (details.is_series || scheduleItems.length > 0) {
        if (btnSchedule) btnSchedule.style.display = "inline-flex";
        if (scheduleContainer) {
            if (scheduleItems.length > 0) {
                scheduleContainer.innerHTML = `
                    <div class="schedule-table-wrap">
                        <table class="schedule-table">
                            <thead>
                                <tr>
                                    <th>Сезон / Серия</th>
                                    <th>Название</th>
                                    <th>Дата выхода</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${scheduleItems.map(item => `
                                    <tr tabindex="0">
                                        <td class="schedule-ep-num">${item.season_episode || item.episode || 'Серия'}</td>
                                        <td class="schedule-ep-title">${item.title || item.name || 'Эпизод'}</td>
                                        <td class="schedule-ep-date">${item.release_date || item.date || 'Скоро'}</td>
                                    </tr>
                                `).join("")}
                            </tbody>
                        </table>
                    </div>
                `;
                scheduleContainer.querySelectorAll("tr[tabindex='0']").forEach(tr => {
                    tr.addEventListener("focus", () => {
                        tr.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                    });
                });
            } else if (details.seasons && details.seasons.length > 0) {
                scheduleContainer.innerHTML = `
                    <div class="schedule-cards-grid">
                        ${details.seasons.map(s => `
                            <div class="schedule-season-card" tabindex="0">
                                <h4>${s.title}</h4>
                                <div class="schedule-season-meta">Всего в базе: ${s.episodes ? s.episodes.length : 0} серий</div>
                            </div>
                        `).join("")}
                    </div>
                `;
                scheduleContainer.querySelectorAll(".schedule-season-card").forEach(c => {
                    c.addEventListener("focus", () => {
                        c.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                    });
                });
            } else {
                scheduleContainer.innerHTML = `<div class="empty-state" style="padding: 24px; text-align: center; color: var(--text-muted);">График выхода серий формируется...</div>`;
            }
        }
    } else {
        if (btnSchedule) btnSchedule.style.display = "none";
    }
}

function renderEpisodesUI(episodes) {
    const epSec = document.getElementById("section-episodes");
    const epList = document.getElementById("modal-episodes-list");
    if (!episodes || episodes.length === 0) {
        epSec.style.display = "none";
        return;
    }

    epSec.style.display = "block";

    // Check if progress matches this season
    const prog = getMediaProgress(currentMediaItem?.id, currentMediaItem?.kpId, currentMediaItem?.title, currentMediaItem?.source, currentMediaItem?.year);
    let defaultEpId = episodes[0].episode_id;
    if (prog && prog.isSeries && prog.season === activeSeasonId && prog.episode) {
        const foundEp = episodes.find(e => e.episode_id === prog.episode);
        if (foundEp) {
            defaultEpId = prog.episode;
        }
    }

    epList.innerHTML = episodes.map((ep) => {
        const watched = isEpisodeWatched(currentMediaItem?.id, activeSeasonId, ep.episode_id, currentMediaItem?.kpId, currentMediaItem?.title, currentMediaItem?.source, currentMediaItem?.year);
        const watchedCls = watched ? "watched" : "";
        const markIcon = watched ? " ✓" : "";
        return `
            <button class="episode-chip ${ep.episode_id === defaultEpId ? 'active' : ''} ${watchedCls}" data-ep="${ep.episode_id}" tabindex="0">
                ${ep.title}${markIcon}
            </button>
        `;
    }).join("");

    activeEpisodeId = defaultEpId;

    const updateBottomPlayButtonUI = () => {
        const bottomPlay = document.getElementById("btn-bottom-play-episode");
        if (bottomPlay) {
            bottomPlay.textContent = `▶ Смотреть: Сезон ${activeSeasonId || 1}, Серия ${activeEpisodeId || 1}`;
        }
    };
    updateBottomPlayButtonUI();

    const triggerPlayCurrentEpisode = () => {
        const heroPlay = document.getElementById("btn-modal-play");
        if (heroPlay && !heroPlay.disabled && selectedStream) {
            heroPlay.click();
        } else {
            reloadStreams().then(() => {
                document.getElementById("btn-modal-play")?.click();
            });
        }
    };

    const bottomPlayBtn = document.getElementById("btn-bottom-play-episode");
    if (bottomPlayBtn) {
        bottomPlayBtn.onclick = () => triggerPlayCurrentEpisode();
    }

    epList.querySelectorAll(".episode-chip").forEach(chip => {
        const onSelectAndPlay = () => {
            epList.querySelectorAll(".episode-chip").forEach(c => c.classList.remove("active"));
            chip.classList.add("active");
            activeEpisodeId = parseInt(chip.getAttribute("data-ep"));
            updateBottomPlayButtonUI();
            chip.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
            reloadStreams().then(() => {
                triggerPlayCurrentEpisode();
            });
        };

        chip.addEventListener("click", onSelectAndPlay);
        chip.addEventListener("keydown", (e) => {
            if (e.key === "Enter") {
                e.preventDefault();
                onSelectAndPlay();
            }
        });
        chip.addEventListener("focus", () => {
            activeEpisodeId = parseInt(chip.getAttribute("data-ep"));
            updateBottomPlayButtonUI();
            chip.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
        });
    });

    setTimeout(() => {
        const activeChip = epList.querySelector(".episode-chip.active");
        if (activeChip) {
            activeChip.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
        }
    }, 60);
}

async function fetchComments(source, queryId, title, reqId) {
    try {
        const res = await fetch(`/api/media/comments?source=${encodeURIComponent(source)}&media_id=${encodeURIComponent(queryId)}&title=${encodeURIComponent(title)}`);
        const comments = await res.json();
        if (reqId !== activeModalRequestId) return;

        document.getElementById("comments-tab-count").textContent = comments.length;
        const commList = document.getElementById("modal-comments-list");

        if (!comments || comments.length === 0) {
            commList.innerHTML = `<div class="empty-state">Отзывов к этому фильму пока нет.</div>`;
            return;
        }

        commList.innerHTML = comments.map(c => `
            <div class="comment-card" tabindex="0">
                <div class="comment-header">
                    <span class="comment-author">${c.author}</span>
                    <span class="comment-date">${c.date}</span>
                    ${c.rating ? `<span class="comment-rating-tag">${c.rating}</span>` : ''}
                </div>
                <div class="comment-body">${c.text}</div>
            </div>
        `).join("");

        commList.querySelectorAll(".comment-card").forEach(card => {
            card.addEventListener("focus", () => {
                card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            });
        });
    } catch (e) {
        console.warn("Error fetching comments:", e);
    }
}

/* =========================================================
   ShowHub Native On-Device Stream Resolver (Direct TV Mode)
   Queries HDRezka directly from the Android TV's residential IP
   to bypass datacenter anti-piracy blocks (url: false / 403).
   ========================================================= */
async function resolveDeviceStreams(title, year, isSeries, season, episode, translatorId) {
    if (!window.AndroidBridge || typeof window.AndroidBridge.httpRequest !== "function") {
        return null;
    }
    const cleanTitle = (title || "").split(":")[0].split(" - ")[0].trim();
    if (!cleanTitle) return null;

    const results = {};

    // 1. Resolve HDRezka directly on Android TV
    try {
        const rezkaBase = "https://hdrezka-home.tv";
        const searchUrl = `${rezkaBase}/search/?do=search&subaction=search&q=${encodeURIComponent(cleanTitle)}`;
        const headers = JSON.stringify({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Referer": `${rezkaBase}/`
        });

        const sRaw = window.AndroidBridge.httpRequest(searchUrl, "GET", null, headers);
        if (sRaw) {
            const sParsed = JSON.parse(sRaw);
            if (sParsed.status === 200 && sParsed.body) {
                const idMatch = sParsed.body.match(/data-id="(\d+)"/);
                const linkMatch = sParsed.body.match(/class="b-content__inline_item-link"[^>]*><a href="([^"]+)"/);
                if (idMatch && linkMatch) {
                    const dataId = idMatch[1];
                    const pageUrl = linkMatch[1].startsWith("http") ? linkMatch[1] : `${rezkaBase}${linkMatch[1]}`;

                    // Extract translator
                    let transId = translatorId;
                    if (!transId) {
                        const pageHeaders = JSON.stringify({
                            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                            "Referer": `${rezkaBase}/`
                        });
                        const pRaw = window.AndroidBridge.httpRequest(pageUrl, "GET", null, pageHeaders);
                        if (pRaw) {
                            try {
                                const pParsed = JSON.parse(pRaw);
                                if (pParsed.body) {
                                    const trM = pParsed.body.match(/data-translator_id="(\d+)"/);
                                    if (trM) transId = trM[1];
                                    else {
                                        const initM = pParsed.body.match(/initCDN(?:Movies|Series)Events\(\s*\d+\s*,\s*(\d+)/);
                                        if (initM) transId = initM[1];
                                    }
                                }
                            } catch (e) {}
                        }
                    }
                    if (!transId) transId = "56";

                    // Query Ajax from the TV's home IP
                    const tNow = Date.now();
                    const ajaxUrl = `${rezkaBase}/ajax/get_cdn_series/?t=${tNow}`;
                    let postData = `id=${dataId}&translator_id=${transId}&action=${isSeries ? "get_stream" : "get_movie"}`;
                    if (isSeries) {
                        postData += `&season=${season || 1}&episode=${episode || 1}`;
                    }
                    const ajaxHeaders = JSON.stringify({
                        "X-Requested-With": "XMLHttpRequest",
                        "Referer": pageUrl,
                        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                    });

                    const ajaxRaw = window.AndroidBridge.httpRequest(ajaxUrl, "POST", postData, ajaxHeaders);
                    if (ajaxRaw) {
                        const ajaxParsed = JSON.parse(ajaxRaw);
                        if (ajaxParsed.status === 200 && ajaxParsed.body) {
                            const cdnData = JSON.parse(ajaxParsed.body);
                            const streamStr = cdnData.url || cdnData.streams || "";
                            if (streamStr && typeof streamStr === "string" && streamStr.length > 5) {
                                const streams = [];
                                const parts = streamStr.split(/,\s*(?=\[[^\]]+\])/);
                                for (const part of parts) {
                                    const m = part.match(/\[([^\]]+)\](.*)/);
                                    if (m) {
                                        const quality = m[1].replace(/<[^>]+>/g, '').trim();
                                        const urls = m[2].split(" or ").map(u => u.trim().replace(/\\\//g, '/')).filter(u => u.startsWith("http"));
                                        const working = urls.filter(u => !u.includes("ukrtelcdn"))[0] || urls[0];
                                        if (working) {
                                            const isPrem = working.includes("rhtie.mp4") || /ultra|4k|2160|1440/i.test(quality);
                                            streams.push({
                                                quality: `${quality} (HDRezka Direct)`,
                                                url: working,
                                                stream_type: working.includes(".m3u8") ? "hls" : "mp4",
                                                headers: { "Referer": `${rezkaBase}/`, "User-Agent": "Mozilla/5.0" },
                                                is_premium: isPrem
                                            });
                                        }
                                    }
                                }
                                if (streams.length > 0) {
                                    results["hdrezka"] = {
                                        source_name: "HDRezka (Direct Device)",
                                        title: title,
                                        streams: streams,
                                        embed_url: pageUrl,
                                        error: null
                                    };
                                }
                            }
                        }
                    }
                }
            }
        }
    } catch (rezkaErr) {
        console.warn("Device-side HDRezka resolution failed:", rezkaErr);
    }

    return Object.keys(results).length > 0 ? results : null;
}

let isStreamsLoading = false;
let currentStreamsPromise = null;

async function fetchStreams(source, queryId, title, reqId, year, isSeries, kpId) {
    isStreamsLoading = true;
    const p = (async () => {
        try {
            let url = `/api/media/streams?source=${encodeURIComponent(source)}&media_id=${encodeURIComponent(queryId)}&title=${encodeURIComponent(title)}`;
            if (currentMediaItem?.isSeries && activeSeasonId && activeEpisodeId) {
                url += `&season=${activeSeasonId}&episode=${activeEpisodeId}`;
            }
            if (activeTranslatorId) {
                url += `&audio_id=${encodeURIComponent(activeTranslatorId)}`;
            }
            const yr = year || currentMediaItem?.year;
            const validYr = getValidYear(yr);
            if (validYr) url += `&year=${encodeURIComponent(validYr)}`;
            const isSer = (isSeries !== undefined && isSeries !== null) ? isSeries : currentMediaItem?.isSeries;
            if (isSer !== undefined && isSer !== null) url += `&is_series=${encodeURIComponent(isSer ? 1 : 0)}`;
            const kp = kpId || currentMediaItem?.kpId;
            if (kp) url += `&kp_id=${encodeURIComponent(kp)}`;

            const res = await fetch(url);
            if (!res.ok) throw new Error("HTTP " + res.status);
            const data = await res.json();
            if (reqId !== activeModalRequestId) return; // Ignore stale responses

            // If backend returned no direct HLS/MP4 streams (e.g. Render datacenter IP blocked),
            // resolve HDRezka directly from the Android TV's residential IP!
            const hasDirectStreams = Object.values(data).some(s => s && s.streams && s.streams.some(st => (st.stream_type === "hls" || st.stream_type === "mp4") && !st.is_premium));
            if (!hasDirectStreams && window.AndroidBridge && typeof window.AndroidBridge.httpRequest === "function") {
                try {
                    const devStreams = await resolveDeviceStreams(title, validYr, isSer, activeSeasonId, activeEpisodeId, activeTranslatorId);
                    if (devStreams && reqId === activeModalRequestId) {
                        Object.assign(data, devStreams);
                    }
                } catch (devErr) {
                    console.warn("Device stream resolution error:", devErr);
                }
            }

            currentStreams = data;
            renderSourceTabs(data);

            const btnPlay = document.getElementById("btn-modal-play");
            if (btnPlay) btnPlay.disabled = false;
            updatePlayButtonLabels();
        } catch (err) {
            if (reqId !== activeModalRequestId) return;
            console.warn("PC streams fetch failed, attempting direct autonomous stream fallback:", err);
            let devStreams = null;
            if (window.AndroidBridge && typeof window.AndroidBridge.httpRequest === "function") {
                try {
                    const validYr = getValidYear(year || currentMediaItem?.year);
                    const isSer = (isSeries !== undefined && isSeries !== null) ? isSeries : currentMediaItem?.isSeries;
                    devStreams = await resolveDeviceStreams(title, validYr, isSer, activeSeasonId, activeEpisodeId, activeTranslatorId);
                } catch (e) {}
            }
            const autoStreams = devStreams || {};
            if (queryId) {
                autoStreams["delivembd"] = {
                    source_name: "delivembd",
                    title: title,
                    embed_url: `https://api.delivembd.ws/embed/movie/${queryId}`,
                    streams: [{ quality: "HD 1080p", url: `https://api.delivembd.ws/embed/movie/${queryId}`, stream_type: "iframe" }]
                };
            }
            autoStreams["bazon"] = {
                source_name: "bazon",
                title: title,
                embed_url: `https://v2.bazon.site/embed/${queryId || 'search'}`,
                streams: [{ quality: "1080p [Плеер]", url: `https://v2.bazon.site/embed/${queryId || 'search'}`, stream_type: "iframe" }]
            };
            currentStreams = autoStreams;
            renderSourceTabs(autoStreams);
            const btnPlay = document.getElementById("btn-modal-play");
            if (btnPlay) btnPlay.disabled = false;
            updatePlayButtonLabels();
        } finally {
            if (reqId === activeModalRequestId) {
                isStreamsLoading = false;
            }
        }
    })();
    currentStreamsPromise = p;
    return p;
}

async function reloadStreams() {
    if (!currentMediaItem) return;
    const { id, source, kpId, title, year, isSeries } = currentMediaItem;
    const queryId = kpId || id;
    const reqId = activeModalRequestId;

    const tabsContainer = document.getElementById("modal-source-tabs");
    const streamsContainer = document.getElementById("modal-streams-list");
    tabsContainer.innerHTML = `<div class="loading-inline"><div class="spinner-neon-sm"></div><span>Смена озвучки / серии...</span></div>`;
    streamsContainer.innerHTML = `<div class="loading-inline"><div class="spinner-neon-sm"></div><span>Загрузка потоков...</span></div>`;

    const btnPlay = document.getElementById("btn-modal-play");
    btnPlay.disabled = true;
    btnPlay.innerHTML = `<span style="display:inline-flex; align-items:center; gap:8px;"><span class="spinner-neon-sm" style="border-width:2px; width:16px; height:16px;"></span>Загрузка...</span>`;

    await fetchStreams(source, queryId, title, reqId, year, isSeries, kpId);
}

function renderSourceTabs(sourcesData) {
    const tabsContainer = document.getElementById("modal-source-tabs");
    
    // Prioritize direct video sources first (Filmix, HDRezka, Bazon HLS) over iframe-only embeds
    const priority = ["filmix", "hdrezka", "bazon", "videocdn", "delivembd", "torrents"];
    const sourceKeys = Object.keys(sourcesData).sort((a, b) => {
        const sa = sourcesData[a];
        const sb = sourcesData[b];
        const aHasDirect = Boolean(sa.streams && sa.streams.some(st => (st.stream_type === "hls" || st.stream_type === "mp4") && (!st.url || !st.url.includes("rhtie.mp4"))));
        const bHasDirect = Boolean(sb.streams && sb.streams.some(st => (st.stream_type === "hls" || st.stream_type === "mp4") && (!st.url || !st.url.includes("rhtie.mp4"))));
        if (aHasDirect !== bHasDirect) {
            return aHasDirect ? -1 : 1;
        }
        const pa = priority.indexOf(a) !== -1 ? priority.indexOf(a) : 99;
        const pb = priority.indexOf(b) !== -1 ? priority.indexOf(b) : 99;
        return pa - pb;
    });

    // Only display sources that actually returned streams or embed URLs
    const workingSourceKeys = sourceKeys.filter(key => {
        const s = sourcesData[key];
        return (s.streams && s.streams.length > 0) || Boolean(s.embed_url);
    });

    if (workingSourceKeys.length === 0) {
        tabsContainer.innerHTML = `<span class="error-state">Потоки для этого видео не найдены.</span>`;
        document.getElementById("modal-streams-list").innerHTML = "";
        return;
    }

    const prog = getMediaProgress(currentMediaItem?.id, currentMediaItem?.kpId, currentMediaItem?.title, currentMediaItem?.source, currentMediaItem?.year);
    let initialSourceKey = workingSourceKeys[0];
    if (prog && prog.source && workingSourceKeys.includes(prog.source)) {
        initialSourceKey = prog.source;
    }

    tabsContainer.innerHTML = workingSourceKeys.map((key) => `
        <button class="source-tab-btn ${key === initialSourceKey ? 'active' : ''}" data-source="${key}" tabindex="0">
            ${sourcesData[key].source_name} (${sourcesData[key].streams.length || (sourcesData[key].embed_url ? '1' : '0')})
        </button>
    `).join("");

    tabsContainer.querySelectorAll(".source-tab-btn").forEach(btn => {
        btn.addEventListener("click", () => {
            tabsContainer.querySelectorAll(".source-tab-btn").forEach(b => b.classList.remove("active"));
            btn.classList.add("active");
            selectSource(btn.getAttribute("data-source"), prog?.quality);
        });
    });

    selectSource(initialSourceKey, prog?.quality);
}

function parseStreamHeight(qualityStr) {
    if (!qualityStr) return 0;
    const q = String(qualityStr).toLowerCase();
    if (q.includes("4k") || q.includes("2160") || q.includes("ultra")) return 2160;
    if (q.includes("1440") || q.includes("2k")) return 1440;
    if (q.includes("1080") || q.includes("fhd")) return 1080;
    if (q.includes("720") || q.includes("hd")) return 720;
    if (q.includes("480") || q.includes("sd")) return 480;
    if (q.includes("360")) return 360;
    return 0;
}

function pickBestStreamIndex(streams, requestedQuality) {
    if (!streams || streams.length === 0) return 0;
    const targetHeight = parseStreamHeight(requestedQuality) || 1080;
    const isFilmixLoggedIn = Boolean(typeof filmixProfile !== "undefined" && filmixProfile && filmixProfile.is_logged_in);

    // 1. Filter out voidboost stubs and prefer direct streams over iframes
    const nonStubs = [];
    const directIndices = [];
    streams.forEach((s, idx) => {
        if (s.url && s.url.includes("rhtie.mp4")) return;
        nonStubs.push(idx);
        if (s.stream_type === "hls" || s.stream_type === "mp4") {
            directIndices.push(idx);
        }
    });
    if (nonStubs.length === 0) return 0;

    const basePool = directIndices.length > 0 ? directIndices : nonStubs;

    // 2. Filter free streams if user is not logged in
    const freePool = basePool.filter(idx => {
        const s = streams[idx];
        const isPrem = s.is_premium || (s.quality && /4k|2160|ultra/i.test(s.quality));
        if (isPrem && !isFilmixLoggedIn) return false;
        return true;
    });

    const pool = freePool.length > 0 ? freePool : basePool;

    // 3. Highest resolution <= targetHeight
    const belowOrEqual = pool.filter(idx => parseStreamHeight(streams[idx].quality) <= targetHeight);
    if (belowOrEqual.length > 0) {
        belowOrEqual.sort((a, b) => parseStreamHeight(streams[b].quality) - parseStreamHeight(streams[a].quality));
        return belowOrEqual[0];
    }

    // 4. Closest resolution above targetHeight
    pool.sort((a, b) => parseStreamHeight(streams[a].quality) - parseStreamHeight(streams[b].quality));
    return pool[0];
}

function showProPromptModal(stream) {
    const q = stream?.quality || "1080p / 4K";
    const ok = confirm(`Поток «${q}» требует подписку Filmix PRO.\n\nПерейти в Настройки для авторизации в Filmix?`);
    if (ok) {
        openSettingsModal();
        switchSettingsTab("filmix");
    }
}

function selectSource(sourceKey, preferredQuality) {
    activeSource = sourceKey;
    const sourceObj = currentStreams[sourceKey];
    const streamsContainer = document.getElementById("modal-streams-list");

    if (!sourceObj || (!sourceObj.streams.length && !sourceObj.embed_url)) {
        streamsContainer.innerHTML = `<span class="error-state">Нет доступных стримов для этого источника</span>`;
        return;
    }

    const streams = sourceObj.streams.length ? sourceObj.streams : [
        { quality: "Embed Player", url: sourceObj.embed_url, stream_type: "iframe" }
    ];

    const settings = getSettings();
    const defaultIdx = pickBestStreamIndex(streams, preferredQuality || settings.quality);

    streamsContainer.innerHTML = streams.map((s, idx) => {
        const isPro = s.is_premium || (s.quality && /4k|2160|ultra/i.test(s.quality));
        const proBadge = isPro ? `<span class="premium-badge">👑 PRO</span>` : '';
        return `
            <button class="stream-chip ${idx === defaultIdx ? 'active' : ''}" data-idx="${idx}" tabindex="0">
                ${s.quality} [${s.stream_type.toUpperCase()}]${proBadge}
            </button>
        `;
    }).join("");

    selectedStream = streams[defaultIdx];

    streamsContainer.querySelectorAll(".stream-chip").forEach(chip => {
        chip.addEventListener("click", () => {
            const idx = parseInt(chip.getAttribute("data-idx"));
            const candidate = streams[idx];
            if (candidate && candidate.is_premium) {
                const isFilmixLoggedIn = Boolean(typeof filmixProfile !== "undefined" && filmixProfile && filmixProfile.is_logged_in);
                if (activeSource === "filmix" && !isFilmixLoggedIn) {
                    showProPromptModal(candidate);
                    return;
                }
            }
            streamsContainer.querySelectorAll(".stream-chip").forEach(c => c.classList.remove("active"));
            chip.classList.add("active");
            selectedStream = candidate;
            if (selectedStream) {
                const settings = getSettings();
                const prog = getMediaProgress(currentMediaItem?.id, currentMediaItem?.kpId, currentMediaItem?.title, currentMediaItem?.source, currentMediaItem?.year);
                const startSec = prog?.positionSec || 0;
                if (settings.player === "external") {
                    launchExternalPlayer(selectedStream, startSec);
                } else {
                    playStream(selectedStream, startSec);
                }
            }
        });
    });
}

function launchExternalPlayer(stream, startSec = 0) {
    if (!stream || !stream.url) return;
    const url = stream.url;
    const mediaTitle = currentMediaItem?.title || document.getElementById("modal-title")?.textContent || "ShowHub Video";
    const epMeta = (currentDetails?.is_series && activeSeasonId && activeEpisodeId) ? ` [С${activeSeasonId} Э${activeEpisodeId}]` : '';
    const fullTitle = `${mediaTitle}${epMeta}`;

    const prog = getMediaProgress(currentMediaItem?.id, currentMediaItem?.kpId, currentMediaItem?.title, currentMediaItem?.source, currentMediaItem?.year);
    const resumeSec = (startSec > 0) ? startSec : (prog?.positionSec || 0);
    const positionMs = Math.round(resumeSec * 1000);

    // Save playback launch into watch history
    if (currentMediaItem) {
        saveMediaProgress(currentMediaItem, {
            season: activeSeasonId || 1,
            episode: activeEpisodeId || 1,
            positionSec: resumeSec,
            source: activeSource || currentMediaItem.source,
            quality: selectedStream?.quality,
            audio_id: activeTranslatorId,
            updatedAt: Date.now()
        });
    }

    if (window.AndroidBridge && window.AndroidBridge.openInExternalPlayer) {
        try {
            window.AndroidBridge.openInExternalPlayer(url, fullTitle, positionMs);
        } catch (e) {
            try {
                window.AndroidBridge.openInExternalPlayer(url, fullTitle);
            } catch (e2) {
                console.warn("AndroidBridge invocation error:", e2);
            }
        }
    } else if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(url).then(() => {
            alert(`Ссылка на поток скопирована в буфер обмена!\n\n${url}\n\nВы можете вставить её в VLC Player на ТВ.`);
        }).catch(() => {
            prompt("Ссылка на видеопоток:", url);
        });
    } else {
        prompt("Ссылка на видеопоток:", url);
    }
}

let trailerReturnMedia = null;
let isPlayingTrailer = false;

async function playTrailer() {
    stopDetailsPreview();
    if (!currentMediaItem) return;
    trailerReturnMedia = currentMediaItem;
    const title = currentMediaItem.title;
    const year = currentMediaItem.year;
    const kpId = currentMediaItem.kpId;

    const btnTrailer = document.getElementById("btn-modal-trailer");
    if (btnTrailer) {
        btnTrailer.disabled = true;
        btnTrailer.innerHTML = `<span class="spinner-neon-sm" style="border-width:2px; width:14px; height:14px; display:inline-block; vertical-align:middle; margin-right:6px;"></span> Поиск трейлера...`;
    }

    try {
        const url = `/api/media/trailer?title=${encodeURIComponent(title)}&year=${encodeURIComponent(year || '')}&kp_id=${encodeURIComponent(kpId || '')}`;
        const res = await fetch(url);
        const data = await res.json();
        if (data && data.success && (data.web_url || data.embed_url || data.app_url)) {
            const target = data.web_url || (data.video_id ? `https://www.youtube.com/watch?v=${data.video_id}` : null) || data.app_url || data.embed_url;
            if (window.AndroidBridge && typeof window.AndroidBridge.openUrl === "function") {
                showPlayerToast("🎬 Запуск трейлера в YouTube / SmartTube...");
                window.AndroidBridge.openUrl(target);
                return;
            }
            if (window.AndroidBridge && typeof window.AndroidBridge.openInExternalPlayer === "function") {
                showPlayerToast("🎬 Запуск трейлера...");
                window.AndroidBridge.openInExternalPlayer(data.web_url || target, `Трейлер: ${title}`);
                return;
            }

            // Desktop / Web browser mode
            if (window.open && (window.location.protocol.startsWith("http") || !window.AndroidBridge)) {
                window.open(data.web_url || target, "_blank");
                return;
            }

            isPlayingTrailer = true;
            playStream({
                stream_type: "iframe",
                url: data.embed_url || data.web_url,
                quality: "Трейлер HD",
                source: "YouTube",
                extra: data
            });
        } else {
            showPlayerToast("⚠️ Трейлер не найден для этого видео");
        }
    } catch (e) {
        console.error("Trailer fetch error:", e);
        showPlayerToast("⚠️ Ошибка загрузки трейлера");
    } finally {
        if (btnTrailer) {
            btnTrailer.disabled = false;
            btnTrailer.innerHTML = `<svg class="btn-icon-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="2" width="20" height="20" rx="2"></rect><line x1="7" y1="2" x2="7" y2="22"></line><line x1="17" y1="2" x2="17" y2="22"></line><line x1="2" y1="12" x2="22" y2="12"></line></svg><span>Трейлер</span>`;
        }
    }
}

function initModalEvents() {
    document.getElementById("btn-modal-close")?.addEventListener("click", closeMediaModal);
    document.querySelector(".modal-backdrop")?.addEventListener("click", closeMediaModal);

    const ensureDirectStreamSelected = () => {
        if (!selectedStream || selectedStream.stream_type === "iframe") {
            if (currentStreams) {
                const priority = ["filmix", "hdrezka", "bazon", "videocdn", "delivembd", "torrents"];
                const sortedKeys = Object.keys(currentStreams).sort((a, b) => {
                    const pa = priority.indexOf(a) !== -1 ? priority.indexOf(a) : 99;
                    const pb = priority.indexOf(b) !== -1 ? priority.indexOf(b) : 99;
                    return pa - pb;
                });
                for (const k of sortedKeys) {
                    const srcObj = currentStreams[k];
                    if (srcObj && srcObj.streams && srcObj.streams.length > 0) {
                        const direct = srcObj.streams.filter(s => (s.stream_type === "hls" || s.stream_type === "mp4") && (!s.url || !s.url.includes("rhtie.mp4")));
                        if (direct.length > 0) {
                            selectSource(k);
                            const tabs = document.querySelectorAll("#modal-source-tabs .source-tab-btn");
                            tabs.forEach(b => {
                                if (b.getAttribute("data-source") === k) b.classList.add("active");
                                else b.classList.remove("active");
                            });
                            break;
                        }
                    }
                }
            }
        }
    };

    const triggerPlay = async () => {
        const btnPlay = document.getElementById("btn-modal-play");
        const origHtml = btnPlay ? btnPlay.innerHTML : "Смотреть";
        if (isStreamsLoading && currentStreamsPromise) {
            if (btnPlay) {
                btnPlay.innerHTML = `<span class="spinner-neon-sm" style="width:14px;height:14px;border-width:2px;display:inline-block;vertical-align:middle;margin-right:6px;"></span> Загрузка потоков...`;
            }
            try {
                await currentStreamsPromise;
            } catch (e) {}
            if (btnPlay) btnPlay.innerHTML = origHtml;
        }

        ensureDirectStreamSelected();
        if (!selectedStream) {
            if (currentStreams) {
                const keys = Object.keys(currentStreams);
                if (keys.length > 0) selectSource(keys[0]);
            }
        }
        if (!selectedStream) return;

        const settings = getSettings();
        const prog = getMediaProgress(currentMediaItem?.id, currentMediaItem?.kpId, currentMediaItem?.title, currentMediaItem?.source, currentMediaItem?.year);
        const startSec = prog?.positionSec || 0;
        if (settings.player === "external") {
            launchExternalPlayer(selectedStream, startSec);
        } else {
            playStream(selectedStream, startSec);
        }
    };

    const handleResumeClick = async (startFromBeginning = false) => {
        const btnResume = document.getElementById("btn-modal-resume");
        const origText = btnResume ? btnResume.innerHTML : "▶ Продолжить просмотр";
        const prog = getMediaProgress(currentMediaItem?.id, currentMediaItem?.kpId, currentMediaItem?.title, currentMediaItem?.source, currentMediaItem?.year);
        const targetSec = startFromBeginning ? 0 : (prog?.positionSec || 0);

        // 1. If it's a series and user stopped on a different season/episode, activate it first
        let needsReload = false;
        if (prog && prog.isSeries && prog.season && prog.episode) {
            if (prog.season !== activeSeasonId || prog.episode !== activeEpisodeId) {
                activeSeasonId = prog.season;
                activeEpisodeId = prog.episode;
                document.querySelectorAll("#modal-seasons-list .season-chip").forEach(c => {
                    c.classList.toggle("active", parseInt(c.getAttribute("data-season")) === activeSeasonId);
                });
                document.querySelectorAll("#modal-episodes-list .episode-chip").forEach(c => {
                    c.classList.toggle("active", parseInt(c.getAttribute("data-ep")) === activeEpisodeId);
                });
                needsReload = true;
            }
        }

        // 2. Restore saved audio / translator if available
        if (prog && prog.audio_id && activeTranslatorId !== prog.audio_id) {
            activeTranslatorId = prog.audio_id;
            document.querySelectorAll("#modal-translators-list .translator-chip").forEach(c => {
                c.classList.toggle("active", c.getAttribute("data-id") === String(prog.audio_id));
            });
            needsReload = true;
        }

        if (needsReload) {
            if (btnResume) {
                btnResume.innerHTML = `<span class="spinner-neon-sm" style="width:14px;height:14px;border-width:2px;display:inline-block;vertical-align:middle;margin-right:6px;"></span> Подготовка серии...`;
            }
            await reloadStreams();
        }

        if (isStreamsLoading && currentStreamsPromise) {
            if (btnResume) {
                btnResume.innerHTML = `<span class="spinner-neon-sm" style="width:14px;height:14px;border-width:2px;display:inline-block;vertical-align:middle;margin-right:6px;"></span> Загрузка потока...`;
            }
            try {
                await currentStreamsPromise;
            } catch (e) {}
        }

        // 3. Restore saved source if available
        if (prog && prog.source && currentStreams && currentStreams[prog.source]) {
            document.querySelectorAll("#modal-source-tabs .source-tab-btn").forEach(b => {
                b.classList.toggle("active", b.getAttribute("data-source") === prog.source);
            });
            selectSource(prog.source, prog.quality);
        } else {
            ensureDirectStreamSelected();
        }

        // 4. Restore saved quality if available
        if (prog && prog.quality && currentStreams && activeSource && currentStreams[activeSource]) {
            const streamsList = currentStreams[activeSource]?.streams || [];
            const matchingStream = streamsList.find(s => s.quality === prog.quality && (s.stream_type === "direct" || s.stream_type === "hls" || s.is_direct));
            if (matchingStream) {
                selectedStream = matchingStream;
                document.querySelectorAll("#modal-streams-list .stream-chip").forEach(c => {
                    const idx = parseInt(c.getAttribute("data-idx"));
                    c.classList.toggle("active", streamsList[idx] === matchingStream);
                });
            }
        }

        if (btnResume) btnResume.innerHTML = origText;

        if (!selectedStream) {
            if (currentStreams) {
                const keys = Object.keys(currentStreams);
                if (keys.length > 0) selectSource(keys[0], prog?.quality);
            }
        }

        if (!selectedStream) {
            alert("Потоки еще загружаются. Пожалуйста, подождите пару секунд и повторите нажатие.");
            return;
        }

        const settings = getSettings();
        if (settings.player === "external") {
            launchExternalPlayer(selectedStream, targetSec);
        } else {
            playStream(selectedStream, targetSec);
        }
    };

    // Primary action button & duplicate bottom buttons
    document.getElementById("btn-modal-play")?.addEventListener("click", triggerPlay);
    document.getElementById("btn-bottom-play-movie")?.addEventListener("click", triggerPlay);
    document.getElementById("btn-bottom-play-about")?.addEventListener("click", triggerPlay);
    document.getElementById("btn-bottom-play-episode")?.addEventListener("click", triggerPlay);

    // Resume and restart buttons in progress banner
    document.getElementById("btn-modal-resume")?.addEventListener("click", () => handleResumeClick(false));
    document.getElementById("btn-modal-restart")?.addEventListener("click", () => handleResumeClick(true));

    // Trailer action button
    document.getElementById("btn-modal-trailer")?.addEventListener("click", () => {
        playTrailer();
    });

    // Favorites action button
    document.getElementById("btn-modal-fav")?.addEventListener("click", () => {
        if (currentMediaItem) {
            toggleFavorite(currentMediaItem);
        }
    });

    // Secondary action button: Always internal web-player
    document.getElementById("btn-modal-online")?.addEventListener("click", () => {
        if (selectedStream) {
            const prog = getMediaProgress(currentMediaItem?.id, currentMediaItem?.kpId, currentMediaItem?.title, currentMediaItem?.source, currentMediaItem?.year);
            playStream(selectedStream, prog?.positionSec || 0);
        }
    });

    // Toggle watched button
    document.getElementById("btn-modal-toggle-watched")?.addEventListener("click", () => {
        if (currentMediaItem?.id) {
            toggleMediaWatched(currentMediaItem.id);
            updateModalProgressUI();
            if (currentView === "history") {
                renderHistoryView();
            }
        }
    });

    document.getElementById("btn-modal-external")?.addEventListener("click", () => {
        if (selectedStream) {
            launchExternalPlayer(selectedStream);
        }
    });

    document.getElementById("btn-close-player").addEventListener("click", closePlayer);

    // Modal Tabs Navigation (Watch, About, Comments, Schedule)
    document.querySelectorAll(".modal-tab-btn").forEach(btn => {
        const onTabSelect = () => {
            switchModalTab(btn.getAttribute("data-tab"));
        };
        btn.addEventListener("click", onTabSelect);
        btn.addEventListener("focus", onTabSelect);
    });
}

function switchModalTab(tabName) {
    document.querySelectorAll(".modal-tab-btn").forEach(b => {
        b.classList.toggle("active", b.getAttribute("data-tab") === tabName);
    });
    document.querySelectorAll(".modal-tab-content").forEach(c => {
        const isActive = (c.id === `tab-content-${tabName}`);
        c.classList.toggle("active", isActive);
        c.style.display = isActive ? "flex" : "none";
        if (isActive) {
            c.querySelectorAll(".loading-state, .empty-state").forEach(el => el.setAttribute("tabindex", "0"));
        }
    });
}

function closeMediaModal() {
    stopDetailsPreview();
    const viewDetails = document.getElementById("view-details");
    if (viewDetails) viewDetails.style.display = "none";
    const modalComp = document.getElementById("media-modal");
    if (modalComp) modalComp.style.display = "none";

    const target = (previousView && previousView !== "details") ? previousView : "catalog";
    previousView = "catalog";
    switchView(target);

    if (lastFocusedCardElement) {
        try { lastFocusedCardElement.focus(); } catch (e) {}
    } else {
        const navTarget = document.getElementById("nav-search") || document.querySelector(".topbar .nav-item");
        if (navTarget) navTarget.focus();
    }
    currentMediaItem = null;
    selectedStream = null;
    currentStreams = null;
}

/* =========================================================
   ShowHub TV Integrated Video Player Engine & OSD Controller
   ========================================================= */
let playerActive = false;
let osdVisible = true;
let osdHideTimer = null;
let activeDrawer = null; // 'audio' | 'subs' | 'quality' | 'episodes' | null
let playerProgressTimer = null;
let upNextTimer = null;
let upNextSecondsLeft = 0;
let feedbackHideTimer = null;
let toastHideTimer = null;
let clockInterval = null;
let currentSubtitlesList = [];
let activeSubtitleTrackIdx = -1; // -1 = Off
let currentQualityLevels = [];
let activeQualityIdx = 0;
let lastOsdFocusElement = null;

function formatPlayerTime(seconds) {
    if (isNaN(seconds) || seconds < 0) return "00:00";
    const totalSecs = Math.floor(seconds);
    const h = Math.floor(totalSecs / 3600);
    const m = Math.floor((totalSecs % 3600) / 60);
    const s = totalSecs % 60;
    if (h > 0) {
        return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    }
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

function updateOsdClock() {
    const el = document.getElementById("osd-clock");
    if (!el) return;
    const now = new Date();
    const h = String(now.getHours()).padStart(2, '0');
    const m = String(now.getMinutes()).padStart(2, '0');
    el.textContent = `${h}:${m}`;
}

function showCenterFeedback(text, icon = "▶") {
    const el = document.getElementById("osd-center-feedback");
    const iconEl = document.getElementById("osd-feedback-icon");
    const labelEl = document.getElementById("osd-feedback-label");
    const centerBtn = document.getElementById("osd-center-play-btn");
    const buffEl = document.getElementById("osd-buffering");
    if (!el || !iconEl || !labelEl) return;
    // Suppress buffering overlay to prevent duplicate center notifications
    if (buffEl) buffEl.style.display = "none";

    let svgHtml = "";
    if (icon === "rewind" || icon === "⏪" || icon === "<<" || String(text).includes("-")) {
        svgHtml = `<svg viewBox="0 0 24 24" width="54" height="54" fill="currentColor"><polygon points="11 19 2 12 11 5 11 19"></polygon><polygon points="22 19 13 12 22 5 22 19"></polygon></svg>`;
    } else if (icon === "forward" || icon === "⏩" || icon === ">>" || String(text).includes("+")) {
        svgHtml = `<svg viewBox="0 0 24 24" width="54" height="54" fill="currentColor"><polygon points="13 19 22 12 13 5 13 19"></polygon><polygon points="2 19 11 12 2 5 2 19"></polygon></svg>`;
    } else if (icon === "pause" || icon === "❚❚") {
        svgHtml = `<svg viewBox="0 0 24 24" width="54" height="54" fill="currentColor"><rect x="6" y="4" width="4" height="16" rx="1"></rect><rect x="14" y="4" width="4" height="16" rx="1"></rect></svg>`;
    } else {
        svgHtml = `<svg viewBox="0 0 24 24" width="54" height="54" fill="currentColor"><polygon points="6 4 20 12 6 20 6 4"></polygon></svg>`;
    }

    iconEl.innerHTML = svgHtml;
    labelEl.textContent = text;
    iconEl.style.display = "flex";
    labelEl.style.display = "block";
    if (centerBtn) centerBtn.style.display = "none";

    el.style.display = "flex";
    el.style.opacity = "1";
    el.style.transform = "translate(-50%, -50%) scale(1.05)";
    if (feedbackHideTimer) clearTimeout(feedbackHideTimer);
    feedbackHideTimer = setTimeout(() => {
        el.style.opacity = "0";
        el.style.transform = "translate(-50%, -50%) scale(0.95)";
        setTimeout(() => {
            if (el.style.opacity === "0") {
                iconEl.style.display = "none";
                labelEl.style.display = "none";
                if (centerBtn) centerBtn.style.display = "flex";
            }
        }, 150);
        const video = document.getElementById("tv-video");
        if (video && (video.seeking || video.readyState < 3) && playerActive) {
            const buffAfter = document.getElementById("osd-buffering");
            if (buffAfter) buffAfter.style.display = "flex";
        }
    }, 900);
}

function showPlayerToast(text, durationMs = 3200) {
    const toast = document.getElementById("player-toast");
    const textEl = document.getElementById("player-toast-text");
    if (!toast || !textEl) return;
    textEl.textContent = text;
    toast.style.display = "block";
    toast.style.opacity = "1";
    if (toastHideTimer) clearTimeout(toastHideTimer);
    toastHideTimer = setTimeout(() => {
        toast.style.opacity = "0";
        setTimeout(() => { toast.style.display = "none"; }, 300);
    }, durationMs);
}

function showOSD(targetFocus = null) {
    const osd = document.getElementById("player-osd");
    if (!osd) return;
    osdVisible = true;
    osd.classList.remove("hidden");
    osd.classList.add("visible");
    updateOsdClock();
    resetOsdTimeout();
    if (targetFocus) {
        try { targetFocus.focus(); } catch (e) {}
    }
}

function hideOSD(force = false) {
    const video = document.getElementById("tv-video");
    const osd = document.getElementById("player-osd");
    if (!osd) return;
    if (activeDrawer || (!force && video && video.paused)) return;
    osdVisible = false;
    osd.classList.remove("visible");
    osd.classList.add("hidden");
    if (osdHideTimer) {
        clearTimeout(osdHideTimer);
        osdHideTimer = null;
    }
}

function resetOsdTimeout() {
    if (osdHideTimer) clearTimeout(osdHideTimer);
    const video = document.getElementById("tv-video");
    if (activeDrawer || (video && video.paused)) return;
    osdHideTimer = setTimeout(() => {
        hideOSD();
    }, 3800);
}

let lastPlayPauseTs = 0;

function updateCenterPlayButtonState(isPaused) {
    const centerIcon = document.getElementById("osd-center-play-icon");
    if (!centerIcon) return;
    if (isPaused) {
        centerIcon.innerHTML = `<svg viewBox="0 0 24 24" width="38" height="38" fill="currentColor"><polygon points="6 4 20 12 6 20 6 4"></polygon></svg>`;
    } else {
        centerIcon.innerHTML = `<svg viewBox="0 0 24 24" width="38" height="38" fill="currentColor"><rect x="6" y="4" width="4" height="16" rx="1"></rect><rect x="14" y="4" width="4" height="16" rx="1"></rect></svg>`;
    }
}

function togglePlayPause() {
    const now = Date.now();
    if (now - lastPlayPauseTs < 260) {
        return;
    }
    lastPlayPauseTs = now;

    const video = document.getElementById("tv-video");
    const icon = document.getElementById("osd-play-icon");
    const centerBtn = document.getElementById("osd-center-play-btn");
    if (!video) return;
    if (video.paused) {
        video.play().then(() => {
            if (icon) icon.textContent = "❚❚";
            updateCenterPlayButtonState(false);
            showCenterFeedback("Воспроизведение", "▶");
            resetOsdTimeout();
            if ('mediaSession' in navigator) {
                try { navigator.mediaSession.playbackState = 'playing'; } catch (e) {}
            }
        }).catch(e => console.warn("Play error:", e));
    } else {
        video.pause();
        if (icon) icon.textContent = "▶";
        updateCenterPlayButtonState(true);
        showCenterFeedback("Пауза", "❚❚");
        showOSD(centerBtn || document.getElementById("osd-btn-play"));
        if ('mediaSession' in navigator) {
            try { navigator.mediaSession.playbackState = 'paused'; } catch (e) {}
        }
    }
}

let wasPlayingBeforeSeek = false;
let seekDebounceTimer = null;
let virtualSeekTime = null;
let lastSeekApplyTime = 0;

function seekToTime(targetSec) {
    const video = document.getElementById("tv-video");
    if (!video || !video.duration) return;
    if (!video.seeking && virtualSeekTime === null) {
        wasPlayingBeforeSeek = !video.paused;
    }
    virtualSeekTime = Math.max(0, Math.min(video.duration, targetSec));
    const newTime = virtualSeekTime;
    showBuffering(true);

    // Instant UI update for timeline scrubber
    const playedEl = document.getElementById("osd-progress-played");
    if (playedEl && video.duration > 0) {
        playedEl.style.width = `${Math.min(100, Math.max(0, (newTime / video.duration) * 100))}%`;
    }
    const curEl = document.getElementById("osd-time-cur");
    if (curEl) curEl.textContent = formatPlayerTime(newTime);

    const now = Date.now();
    // If holding down button and >160ms elapsed, apply seek periodically to decoder
    if (now - lastSeekApplyTime > 160) {
        lastSeekApplyTime = now;
        try {
            video.currentTime = newTime;
        } catch (e) {}
    }

    // Final debounce timer when key is released
    if (seekDebounceTimer) {
        clearTimeout(seekDebounceTimer);
    }
    seekDebounceTimer = setTimeout(() => {
        if (!video) return;
        try {
            video.currentTime = newTime;
        } catch (e) {
            console.warn("Seek error:", e);
        }
        virtualSeekTime = null;
        updatePlayerTimeline();
    }, 140);

    resetOsdTimeout();
}

function seekRelative(deltaSec) {
    const video = document.getElementById("tv-video");
    if (!video || !video.duration) return;
    if (virtualSeekTime === null) {
        wasPlayingBeforeSeek = !video.paused;
        virtualSeekTime = video.currentTime;
    }
    virtualSeekTime = Math.max(0, Math.min(video.duration, virtualSeekTime + deltaSec));
    seekToTime(virtualSeekTime);
    showCenterFeedback(
        `${deltaSec > 0 ? '+' : ''}${deltaSec} сек [${formatPlayerTime(virtualSeekTime)}]`,
        deltaSec > 0 ? "⏩" : "⏪"
    );
}

function updatePlayerTimeline() {
    const video = document.getElementById("tv-video");
    if (!video) return;
    const cur = video.currentTime || 0;
    const dur = video.duration || 0;
    const curEl = document.getElementById("osd-time-cur");
    const durEl = document.getElementById("osd-time-dur");
    const remEl = document.getElementById("osd-time-rem");
    const playedEl = document.getElementById("osd-progress-played");
    const bufferedEl = document.getElementById("osd-progress-buffered");
    const bufEl = document.getElementById("osd-time-buf");

    if (curEl) curEl.textContent = formatPlayerTime(cur);
    if (durEl) durEl.textContent = formatPlayerTime(dur);
    if (remEl && dur > 0) {
        const rem = Math.max(0, dur - cur);
        remEl.textContent = `-${formatPlayerTime(rem)}`;
    }

    if (playedEl && dur > 0) {
        const pct = Math.min(100, Math.max(0, (cur / dur) * 100));
        playedEl.style.width = `${pct}%`;
    }

    if (dur > 0 && video.buffered && video.buffered.length > 0) {
        try {
            let activeBufEnd = 0;
            for (let i = 0; i < video.buffered.length; i++) {
                const bStart = video.buffered.start(i);
                const bEnd = video.buffered.end(i);
                if (cur >= bStart - 1.0 && cur <= bEnd + 0.5) {
                    activeBufEnd = bEnd;
                    break;
                }
                if (bEnd > activeBufEnd) {
                    activeBufEnd = bEnd;
                }
            }
            if (activeBufEnd === 0 && video.buffered.length > 0) {
                activeBufEnd = video.buffered.end(video.buffered.length - 1);
            }

            const bufPct = Math.min(100, Math.max(0, (activeBufEnd / dur) * 100));
            if (bufferedEl) {
                bufferedEl.style.width = `${bufPct.toFixed(1)}%`;
            }
            if (bufEl) {
                const aheadSec = Math.max(0, Math.round(activeBufEnd - cur));
                bufEl.textContent = `Буфер: +${formatPlayerTime(aheadSec)} (${Math.round(bufPct)}%)`;
                bufEl.style.display = "inline-block";
            }
        } catch (e) {
            if (bufEl) bufEl.style.display = "none";
        }
    } else {
        if (bufferedEl) bufferedEl.style.width = "0%";
        if (bufEl) bufEl.style.display = "none";
    }

    if ('mediaSession' in navigator && typeof navigator.mediaSession.setPositionState === "function" && dur > 0) {
        try {
            navigator.mediaSession.setPositionState({
                duration: Math.max(0, dur),
                playbackRate: video.playbackRate || 1.0,
                position: Math.max(0, Math.min(dur, cur))
            });
        } catch (e) {}
    }

    checkSkipButtons(cur, dur);
}

function checkSkipButtons(cur, dur) {
    const btnIntro = document.getElementById("player-btn-skip-intro");
    const btnOutro = document.getElementById("player-btn-skip-outro");
    if (!btnIntro || !btnOutro) return;

    if (!dur || dur < 60) {
        btnIntro.style.display = "none";
        btnOutro.style.display = "none";
        return;
    }

    const isSeries = Boolean(currentDetails?.is_series);
    const hasExplicitIntro = currentSkipTimes.introStart !== null && currentSkipTimes.introEnd !== null;
    const hasExplicitOutro = currentSkipTimes.outroStart !== null;

    // Check Intro visibility (auto-dismiss quickly so it doesn't linger after intro)
    let showIntro = false;
    if (hasExplicitIntro) {
        showIntro = (cur >= currentSkipTimes.introStart && cur < currentSkipTimes.introEnd);
    } else if (isSeries) {
        showIntro = (cur >= 10 && cur <= 30);
    }

    // Check Outro visibility
    let showOutro = false;
    if (hasExplicitOutro) {
        showOutro = (cur >= currentSkipTimes.outroStart && cur <= (currentSkipTimes.outroEnd || dur));
    } else if (isSeries) {
        const rem = dur - cur;
        showOutro = (rem <= 130 && rem > 8);
    }

    btnIntro.style.display = showIntro ? "inline-flex" : "none";
    btnOutro.style.display = showOutro ? "inline-flex" : "none";
}

function skipIntro() {
    const video = document.getElementById("tv-video");
    if (!video) return;
    const btnIntro = document.getElementById("player-btn-skip-intro");
    if (btnIntro) btnIntro.style.display = "none";
    const target = (currentSkipTimes.introEnd && currentSkipTimes.introEnd > video.currentTime)
        ? (currentSkipTimes.introEnd + 1)
        : Math.min(video.duration || 9999, video.currentTime + 85);
    wasPlayingBeforeSeek = true;
    seekToTime(target);
    video.play().catch(() => {});
    showPlayerToast("⏭ Заставка пропущена");
    updatePlayerTimeline();
    resetOsdTimeout();
}

function skipOutro() {
    showPlayerToast("⏭ Переход к следующей серии");
    dismissUpNext();
    playNextEpisode();
}

function scheduleNextEpisodePreload() {
    if (nextEpPreloadTimer) clearTimeout(nextEpPreloadTimer);
    if (!currentDetails?.is_series || !currentMediaItem) return;

    // Lazy background preload triggered 5 seconds into playback
    nextEpPreloadTimer = setTimeout(() => {
        preloadNextEpisode();
    }, 5000);
}

function getNextEpisodeInfo() {
    if (!currentDetails?.is_series) return null;
    const seasons = currentDetails.seasons || [];
    let curSeason = seasons.find(s => s.season_id === activeSeasonId);
    let nextEpId = (activeEpisodeId || 1) + 1;
    let nextSeasonId = activeSeasonId || 1;

    if (curSeason && curSeason.episodes) {
        const existsInCurSeason = curSeason.episodes.some(ep => ep.episode_id === nextEpId);
        if (!existsInCurSeason) {
            const curIdx = seasons.indexOf(curSeason);
            if (curIdx !== -1 && curIdx + 1 < seasons.length) {
                nextSeasonId = seasons[curIdx + 1].season_id;
                nextEpId = 1;
            } else {
                return null;
            }
        }
    }
    return { season: nextSeasonId, episode: nextEpId };
}

async function preloadNextEpisode() {
    const nextInfo = getNextEpisodeInfo();
    if (!nextInfo || !currentMediaItem) return;

    const { id, source, kpId, title } = currentMediaItem;
    const queryId = kpId || id;
    const cacheKey = `${source}_${queryId}_s${nextInfo.season}_e${nextInfo.episode}_a${activeTranslatorId || 'def'}`;

    if (streamPreloadCache.has(cacheKey)) return;

    try {
        let url = `/api/media/streams?source=${encodeURIComponent(source)}&media_id=${encodeURIComponent(queryId)}&title=${encodeURIComponent(title)}&season=${nextInfo.season}&episode=${nextInfo.episode}`;
        if (activeTranslatorId) {
            url += `&audio_id=${encodeURIComponent(activeTranslatorId)}`;
        }

        const res = await fetch(url);
        if (res.ok) {
            const data = await res.json();
            streamPreloadCache.set(cacheKey, data);
            console.log(`[ShowHub Preloader] Cached next episode streams: S${nextInfo.season}E${nextInfo.episode}`);
        }
    } catch (e) {
        console.warn("[ShowHub Preloader] Preload next episode failed:", e);
    }
}

function handleCardFocus(card) {
    if (cardPreloadTimer) clearTimeout(cardPreloadTimer);
    cardPreloadTimer = setTimeout(() => {
        if (!card) return;
        const id = card.getAttribute("data-id");
        const source = card.getAttribute("data-source");
        const kpId = card.getAttribute("data-kp");
        const title = decodeURIComponent(card.getAttribute("data-title") || "");
        const year = card.getAttribute("data-year") || "";
        const isSeries = card.getAttribute("data-series") === "1";
        if (!source || (!id && !kpId)) return;
        const queryId = kpId || id;
        const preloadKey = `${source}_${queryId}_${year}_${isSeries ? '1' : '0'}`;
        if (!detailsPreloadCache.has(preloadKey)) {
            let url = `/api/media/details?source=${encodeURIComponent(source)}&media_id=${encodeURIComponent(queryId)}&title=${encodeURIComponent(title)}`;
            const validYr = getValidYear(year);
            if (validYr) url += `&year=${encodeURIComponent(validYr)}`;
            if (isSeries) url += `&is_series=1`;
            if (kpId) url += `&kp_id=${encodeURIComponent(kpId)}`;
            fetch(url)
                .then(r => r.json())
                .then(data => {
                    if (data && (data.title || data.description)) {
                        detailsPreloadCache.set(preloadKey, data);
                    }
                })
                .catch(() => {});
        }
    }, 200);
}

function updateCardSeriesBadge(cardEl, newEpInfo) {
    if (!cardEl || !newEpInfo) return;
    const badge = cardEl.querySelector(".card-series-badge");
    const meta = cardEl.querySelector(".media-meta");
    if (badge) {
        badge.textContent = newEpInfo;
    } else {
        const posterWrapper = cardEl.querySelector(".media-poster-wrapper");
        if (posterWrapper) {
            const newBadge = document.createElement("span");
            newBadge.className = "card-series-badge";
            newBadge.textContent = newEpInfo;
            posterWrapper.appendChild(newBadge);
        }
    }
    if (meta && meta.textContent.includes("Сериал")) {
        const yearPart = cardEl.getAttribute("data-year") || "Н/Д";
        meta.textContent = `${yearPart} • ${newEpInfo}`;
    }
}

async function refreshCatalogSeriesEpisodes() {
    try {
        const res = await fetch("/api/catalog?category=series&page=1");
        if (!res.ok) return;
        const freshSeries = await res.json();
        freshSeries.forEach(item => {
            const epInfo = item.episodes_info || item.extra_data?.episodes_info;
            if (!epInfo) return;
            const card = document.querySelector(`.media-card[data-id="${item.id}"]`) ||
                         document.querySelector(`.media-card[data-title="${encodeURIComponent(item.title)}"]`);
            if (card) {
                updateCardSeriesBadge(card, epInfo);
            }
        });
    } catch (e) {}
}

let cardFocusTimer = null;
let cardVideoPreviewTimer = null;
let activePreviewCard = null;
let activePreviewVideo = null;
let activePreviewHls = null;

function stopCardPreview(card = null) {
    if (cardFocusTimer) {
        clearTimeout(cardFocusTimer);
        cardFocusTimer = null;
    }
    if (cardVideoPreviewTimer) {
        clearTimeout(cardVideoPreviewTimer);
        cardVideoPreviewTimer = null;
    }
    const target = card || document;
    target.querySelectorAll(".card-preview-timeline").forEach(tl => {
        tl.classList.remove("active");
        tl.classList.remove("playing");
    });
    if (activePreviewHls) {
        try { activePreviewHls.destroy(); } catch (e) {}
        activePreviewHls = null;
    }
    if (activePreviewVideo) {
        try {
            activePreviewVideo.pause();
            activePreviewVideo.removeAttribute("src");
            activePreviewVideo.load();
            activePreviewVideo.remove();
        } catch (e) {}
        activePreviewVideo = null;
    }
    const iframes = target.querySelectorAll(".card-preview-iframe, .card-preview-video");
    iframes.forEach(el => {
        try { el.remove(); } catch (e) {}
    });
    activePreviewCard = null;
}

async function startCardPreview(card) {
    if (!card || document.activeElement !== card || currentView !== "catalog") return;
    const title = decodeURIComponent(card.getAttribute("data-title") || "");
    const source = card.getAttribute("data-source") || "";
    const id = card.getAttribute("data-id") || "";
    const kpId = card.getAttribute("data-kp") || "";
    const year = card.getAttribute("data-year") || "";
    const isSeries = card.getAttribute("data-series") === "1";
    if (!title) return;

    stopCardPreview();
    activePreviewCard = card;

    const posterWrapper = card.querySelector(".media-poster-wrapper");
    if (!posterWrapper) return;

    try {
        let url = `/api/media/preview-stream?title=${encodeURIComponent(title)}&source=${encodeURIComponent(source)}&media_id=${encodeURIComponent(id)}&kp_id=${encodeURIComponent(kpId)}`;
        if (year) url += `&year=${encodeURIComponent(year)}`;
        if (isSeries) url += `&is_series=1`;
        const res = await fetch(url);
        if (!res.ok) return;
        const data = await res.json();
        if (!data || !data.success) return;

        // Verify that user is STILL focused on this card and in catalog view
        if (document.activeElement !== card || activePreviewCard !== card || currentView !== "catalog") return;

        // Strictly direct video only (no trailers or iframes)
        if (data.stream_url) {
            const vid = document.createElement("video");
            vid.className = "card-preview-video";
            vid.muted = true;
            vid.playsInline = true;
            vid.autoplay = true;
            vid.loop = true;
            activePreviewVideo = vid;
            posterWrapper.appendChild(vid);

            const startTime = (data.start_time !== undefined && data.start_time !== null) ? data.start_time : 1320;
            if (data.stream_type === "hls" && window.Hls && Hls.isSupported()) {
                const hls = new Hls({ startPosition: startTime, maxBufferLength: 8, maxMaxBufferLength: 16, enableWorker: true });
                activePreviewHls = hls;
                let seekDone = false;
                const ensureStart = () => {
                    if (!seekDone && startTime > 0 && vid.currentTime < Math.min(startTime - 5, 30)) {
                        seekDone = true;
                        try { vid.currentTime = startTime; } catch (e) {}
                    }
                };
                vid.addEventListener("canplay", ensureStart);
                vid.addEventListener("loadeddata", ensureStart);
                hls.loadSource(data.stream_url);
                hls.attachMedia(vid);
                hls.on(Hls.Events.MANIFEST_PARSED, () => {
                    const dur = vid.duration || 0;
                    const safeStart = (dur > 0 && startTime >= dur) ? Math.floor(dur * 0.15) : startTime;
                    if (safeStart > 0) {
                        try { vid.currentTime = safeStart; } catch (e) {}
                    }
                    vid.play().then(() => {
                        vid.classList.add("loaded");
                        const timeline = card.querySelector(".card-preview-timeline");
                        if (timeline) timeline.classList.add("playing");
                    }).catch(() => {});
                });
            } else {
                vid.src = data.stream_url;
                vid.addEventListener("loadedmetadata", () => {
                    const dur = vid.duration || 0;
                    const safeStart = (dur > 0 && startTime >= dur) ? Math.floor(dur * 0.15) : startTime;
                    if (safeStart > 0) {
                        try { vid.currentTime = safeStart; } catch (e) {}
                    }
                });
                vid.play().then(() => {
                    vid.classList.add("loaded");
                    const timeline = card.querySelector(".card-preview-timeline");
                    if (timeline) timeline.classList.add("playing");
                }).catch(() => {});
            }
            vid.addEventListener("ended", () => {
                const dur = vid.duration || 0;
                const safeStart = (dur > 0 && startTime >= dur) ? Math.floor(dur * 0.15) : startTime;
                if (safeStart > 0) {
                    try { vid.currentTime = safeStart; } catch (e) {}
                }
                vid.play().catch(() => {});
            });
        }
    } catch (e) {
        console.warn("Card preview error:", e);
    }
}

/* =========================================================
   Dedicated Media Details Preview Player (Netflix-Style)
   ========================================================= */
let detailsPreviewTimer = null;
let detailsPreviewHls = null;
let detailsPreviewActive = false;

function stopDetailsPreview() {
    if (detailsPreviewTimer) {
        clearTimeout(detailsPreviewTimer);
        detailsPreviewTimer = null;
    }
    if (detailsPreviewHls) {
        try { detailsPreviewHls.destroy(); } catch (e) {}
        detailsPreviewHls = null;
    }
    const vid = document.getElementById("modal-preview-video");
    if (vid) {
        try {
            vid.pause();
            vid.removeAttribute("src");
            vid.load();
        } catch (e) {}
        vid.style.display = "none";
        vid.classList.remove("loaded");
    }
    const ifr = document.getElementById("modal-preview-iframe");
    if (ifr) {
        try {
            ifr.src = "about:blank";
        } catch (e) {}
        ifr.style.display = "none";
        ifr.classList.remove("loaded");
    }
    detailsPreviewActive = false;
}

async function startDetailsPreview(item) {
    if (!item || currentView !== "details") return;
    stopDetailsPreview();
    detailsPreviewActive = true;

    const title = item.title || "";
    const source = item.source || "";
    const id = item.id || "";
    const kpId = item.kpId || "";
    const year = item.year || "";
    const isSeries = Boolean(item.isSeries);
    if (!title) return;

    try {
        let url = `/api/media/preview-stream?title=${encodeURIComponent(title)}&source=${encodeURIComponent(source)}&media_id=${encodeURIComponent(id)}&kp_id=${encodeURIComponent(kpId)}`;
        if (year) url += `&year=${encodeURIComponent(year)}`;
        if (isSeries) url += `&is_series=1`;
        const res = await fetch(url);
        if (!res.ok) return;
        const data = await res.json();
        if (!data || !data.success || !detailsPreviewActive || currentView !== "details") return;

        // Strictly direct video only (no trailers or iframes)
        if (data.stream_url) {
            const vid = document.getElementById("modal-preview-video");
            if (!vid) return;
            vid.style.display = "block";
            vid.muted = true;
            vid.playsInline = true;
            vid.autoplay = true;
            vid.loop = true;

            const startTime = (data.start_time !== undefined && data.start_time !== null) ? data.start_time : 1320;
            if (data.stream_type === "hls" && window.Hls && Hls.isSupported()) {
                const hls = new Hls({ startPosition: startTime, maxBufferLength: 6, maxMaxBufferLength: 12, enableWorker: true });
                detailsPreviewHls = hls;
                let seekDone = false;
                const ensureStart = () => {
                    if (!seekDone && startTime > 0 && vid.currentTime < Math.min(startTime - 5, 30)) {
                        seekDone = true;
                        try { vid.currentTime = startTime; } catch (e) {}
                    }
                };
                vid.addEventListener("canplay", ensureStart);
                vid.addEventListener("loadeddata", ensureStart);
                hls.loadSource(data.stream_url);
                hls.attachMedia(vid);
                hls.on(Hls.Events.MANIFEST_PARSED, () => {
                    if (startTime > 0) {
                        try { vid.currentTime = startTime; } catch (e) {}
                    }
                    vid.play().then(() => {
                        vid.classList.add("loaded");
                    }).catch(() => {});
                });
            } else {
                vid.src = data.stream_url;
                vid.addEventListener("loadedmetadata", () => {
                    if (startTime > 0 && vid.duration && startTime < vid.duration) {
                        try { vid.currentTime = startTime; } catch (e) {}
                    }
                });
                vid.play().then(() => {
                    vid.classList.add("loaded");
                }).catch(() => {});
            }
        }
    } catch (e) {
        console.warn("Details preview error:", e);
    }
}

function setupMediaSession(mediaItem, seasonId, episodeId) {
    if (!('mediaSession' in navigator)) return;

    try {
        const title = mediaItem?.title || "ShowHub TV";
        const artist = "ShowHub TV";
        let album = mediaItem?.original_title || "";
        if (seasonId && episodeId) {
            album = `Сезон ${seasonId}, Серия ${episodeId}`;
        }
        const artwork = [];
        if (mediaItem?.poster) {
            artwork.push({ src: mediaItem.poster, sizes: '512x512', type: 'image/jpeg' });
        }

        navigator.mediaSession.metadata = new MediaMetadata({
            title: title,
            artist: artist,
            album: album,
            artwork: artwork
        });

        navigator.mediaSession.setActionHandler('play', () => {
            const video = document.getElementById("tv-video");
            if (video && video.paused) {
                togglePlayPause();
            }
        });

        navigator.mediaSession.setActionHandler('pause', () => {
            const video = document.getElementById("tv-video");
            if (video && !video.paused) {
                togglePlayPause();
            }
        });

        navigator.mediaSession.setActionHandler('seekbackward', (details) => {
            const skip = details?.seekOffset || 15;
            seekRelative(-skip);
        });

        navigator.mediaSession.setActionHandler('seekforward', (details) => {
            const skip = details?.seekOffset || 15;
            seekRelative(skip);
        });

        navigator.mediaSession.setActionHandler('previoustrack', () => {
            if (typeof playPrevEpisode === "function") playPrevEpisode();
        });

        navigator.mediaSession.setActionHandler('nexttrack', () => {
            if (typeof playNextEpisode === "function") playNextEpisode();
        });

        navigator.mediaSession.setActionHandler('stop', () => {
            if (typeof closePlayer === "function") closePlayer();
        });

        navigator.mediaSession.playbackState = 'playing';
        console.log(`[ShowHub MediaSession] Configured MediaSession for: ${title}`);
    } catch (e) {
        console.warn("[ShowHub MediaSession] Registration warning:", e);
    }
}

window.handleTvMediaKeyCode = function(keyCode) {
    console.log("[ShowHub Native] Received Media KeyCode:", keyCode);
    const playerModal = document.getElementById("player-modal");
    if (!playerModal || playerModal.style.display === "none") return false;

    // 85: KEYCODE_MEDIA_PLAY_PAUSE, 79: KEYCODE_HEADSETHOOK, 179: MediaPlayPause
    if (keyCode === 85 || keyCode === 79 || keyCode === 179) {
        togglePlayPause();
        return true;
    }
    // 126: KEYCODE_MEDIA_PLAY
    if (keyCode === 126) {
        const video = document.getElementById("tv-video");
        if (video && video.paused) togglePlayPause();
        return true;
    }
    // 127: KEYCODE_MEDIA_PAUSE
    if (keyCode === 127) {
        const video = document.getElementById("tv-video");
        if (video && !video.paused) togglePlayPause();
        return true;
    }
    // 87: KEYCODE_MEDIA_NEXT, 176: NEXT
    if (keyCode === 87 || keyCode === 176) {
        if (typeof playNextEpisode === "function") playNextEpisode();
        return true;
    }
    // 88: KEYCODE_MEDIA_PREVIOUS, 177: PREV
    if (keyCode === 88 || keyCode === 177) {
        if (typeof playPrevEpisode === "function") playPrevEpisode();
        return true;
    }
    // 89: KEYCODE_MEDIA_REWIND
    if (keyCode === 89 || keyCode === 227) {
        seekRelative(-15);
        return true;
    }
    // 90: KEYCODE_MEDIA_FAST_FORWARD
    if (keyCode === 90 || keyCode === 228) {
        seekRelative(15);
        return true;
    }
    // 86: KEYCODE_MEDIA_STOP
    if (keyCode === 86 || keyCode === 178) {
        closePlayer();
        return true;
    }
    return false;
};

// Global exports for verification
window.updatePlayerTimeline = updatePlayerTimeline;
window.checkSkipButtons = checkSkipButtons;
window.skipIntro = skipIntro;
window.skipOutro = skipOutro;
window.preloadNextEpisode = preloadNextEpisode;
window.checkUpNext = checkUpNext;
window.dismissUpNext = dismissUpNext;
window.playStream = playStream;
window.closePlayer = closePlayer;
window.setupMediaSession = setupMediaSession;

function showBuffering(show) {
    const el = document.getElementById("osd-buffering");
    const loadingBackdrop = document.getElementById("player-loading-backdrop");
    const feedbackEl = document.getElementById("osd-center-feedback");
    if (!el) return;
    if (show && loadingBackdrop && loadingBackdrop.style.display !== "none") {
        el.style.display = "none";
        return;
    }
    // Suppress buffering overlay if center feedback is currently visible
    if (show && feedbackEl && feedbackEl.style.opacity === "1" && feedbackEl.style.display !== "none") {
        el.style.display = "none";
        return;
    }
    el.style.display = show ? "flex" : "none";
}

function closePlayerDrawers() {
    ["audio", "subs", "quality", "episodes"].forEach(d => {
        const el = document.getElementById(`player-drawer-${d}`);
        if (el) el.style.display = "none";
    });
    activeDrawer = null;
    resetOsdTimeout();
    if (lastOsdFocusElement) {
        try { lastOsdFocusElement.focus(); } catch (e) {}
    } else {
        document.getElementById("osd-btn-play")?.focus();
    }
}

function openAudioDrawer() {
    closePlayerDrawers();
    lastOsdFocusElement = document.getElementById("osd-btn-audio");
    const drawer = document.getElementById("player-drawer-audio");
    const list = document.getElementById("player-audio-list");
    if (!drawer || !list) return;

    let tracks = [];
    if (currentDetails && currentDetails.translators && currentDetails.translators.length > 0) {
        tracks = currentDetails.translators.map(t => ({ id: t.id, name: t.name, type: 'translator' }));
    } else if (hlsInstance && hlsInstance.audioTracks && hlsInstance.audioTracks.length > 0) {
        tracks = hlsInstance.audioTracks.map((t, idx) => ({ id: idx, name: t.name || t.lang || `Дорожка ${idx + 1}`, type: 'hls' }));
    } else {
        tracks = [{ id: 'default', name: 'Основная аудиодорожка', type: 'default' }];
    }

    list.innerHTML = tracks.map(t => {
        const isActive = (String(t.id) === String(activeTranslatorId)) || (t.type === 'hls' && hlsInstance && hlsInstance.audioTrack === t.id);
        return `
            <button class="player-drawer-item ${isActive ? 'active' : ''}" data-id="${t.id}" data-type="${t.type}" data-name="${t.name}" tabindex="0">
                <span>${t.name}</span>
                <span class="item-check">${isActive ? '✓' : ''}</span>
            </button>
        `;
    }).join("");

    list.querySelectorAll(".player-drawer-item").forEach(btn => {
        btn.addEventListener("click", () => {
            const id = btn.getAttribute("data-id");
            const type = btn.getAttribute("data-type");
            const name = btn.getAttribute("data-name");
            switchAudioOnFly(id, name, type);
        });
    });

    drawer.style.display = "flex";
    activeDrawer = 'audio';
    showOSD();
    setTimeout(() => {
        const activeItem = list.querySelector(".player-drawer-item.active") || list.querySelector(".player-drawer-item");
        if (activeItem) activeItem.focus();
    }, 40);
}

function switchAudioOnFly(id, name, type) {
    closePlayerDrawers();
    const video = document.getElementById("tv-video");
    const curSec = video ? video.currentTime : 0;

    const lbl = document.getElementById("osd-label-audio");
    const badge = document.getElementById("osd-badge-voice");
    if (lbl) lbl.textContent = name.length > 14 ? name.substring(0, 14) + '…' : name;
    if (badge) {
        badge.textContent = name;
        badge.style.display = "inline-block";
    }

    if (type === 'hls' && hlsInstance) {
        hlsInstance.audioTrack = parseInt(id);
        showPlayerToast(`🔊 Озвучка: ${name}`);
        return;
    }

    activeTranslatorId = id;
    showPlayerToast(`🔊 Переключение озвучки: ${name}...`);
    reloadStreamsForPlayer(curSec);
}

function openSubsDrawer() {
    closePlayerDrawers();
    lastOsdFocusElement = document.getElementById("osd-btn-subs");
    const drawer = document.getElementById("player-drawer-subs");
    const list = document.getElementById("player-subs-list");
    if (!drawer || !list) return;

    let subs = [{ id: -1, label: "Отключены" }];
    if (hlsInstance && hlsInstance.subtitleTracks && hlsInstance.subtitleTracks.length > 0) {
        hlsInstance.subtitleTracks.forEach((t, idx) => {
            subs.push({ id: idx, label: t.name || t.lang || `Субтитры ${idx + 1}` });
        });
    }

    list.innerHTML = subs.map(s => {
        const isActive = activeSubtitleTrackIdx === s.id;
        return `
            <button class="player-drawer-item ${isActive ? 'active' : ''}" data-id="${s.id}" data-label="${s.label}" tabindex="0">
                <span>${s.label}</span>
                <span class="item-check">${isActive ? '✓' : ''}</span>
            </button>
        `;
    }).join("");

    list.querySelectorAll(".player-drawer-item").forEach(btn => {
        btn.addEventListener("click", () => {
            const id = parseInt(btn.getAttribute("data-id"));
            const label = btn.getAttribute("data-label");
            switchSubtitleOnFly(id, label);
        });
    });

    drawer.style.display = "flex";
    activeDrawer = 'subs';
    showOSD();
    setTimeout(() => {
        const activeItem = list.querySelector(".player-drawer-item.active") || list.querySelector(".player-drawer-item");
        if (activeItem) activeItem.focus();
    }, 40);
}

function switchSubtitleOnFly(id, label) {
    closePlayerDrawers();
    activeSubtitleTrackIdx = id;
    if (hlsInstance) {
        hlsInstance.subtitleTrack = id;
    }
    const lbl = document.getElementById("osd-label-subs");
    if (lbl) lbl.textContent = (id === -1) ? "Субтитры" : label;
    showPlayerToast(`💬 ${id === -1 ? 'Субтитры отключены' : 'Субтитры: ' + label}`);
}

function openQualityDrawer() {
    closePlayerDrawers();
    lastOsdFocusElement = document.getElementById("osd-btn-quality");
    const drawer = document.getElementById("player-drawer-quality");
    const list = document.getElementById("player-quality-list");
    if (!drawer || !list) return;

    let qualities = [];
    if (hlsInstance && hlsInstance.levels && hlsInstance.levels.length > 1) {
        qualities.push({ id: -1, label: "Автоматически (Auto)", type: 'hls_level' });
        hlsInstance.levels.forEach((l, idx) => {
            const height = l.height ? `${l.height}p` : `${Math.round(l.bitrate / 1000)}k`;
            qualities.push({ id: idx, label: height, type: 'hls_level' });
        });
    } else if (currentStreams && activeSource && currentStreams[activeSource] && currentStreams[activeSource].streams) {
        qualities = currentStreams[activeSource].streams.map((s, idx) => ({
            id: idx,
            label: s.quality,
            url: s.url,
            type: 'stream_obj'
        }));
    } else {
        qualities = [{ id: 0, label: selectedStream?.quality || "1080p", type: 'fixed' }];
    }

    list.innerHTML = qualities.map(q => {
        let isActive = false;
        if (q.type === 'hls_level' && hlsInstance) {
            isActive = (hlsInstance.currentLevel === parseInt(q.id)) || (parseInt(q.id) === -1 && hlsInstance.autoLevelEnabled);
        } else if (q.type === 'stream_obj') {
            isActive = (selectedStream && selectedStream.url === q.url);
        }
        const isPro = q.label && /4k|2160|ultra/i.test(q.label);
        const proBadge = isPro ? `<span class="premium-badge">👑 PRO</span>` : '';
        return `
            <button class="player-drawer-item ${isActive ? 'active' : ''}" data-id="${q.id}" data-label="${q.label}" data-type="${q.type}" tabindex="0">
                <span>${q.label}${proBadge}</span>
                <span class="item-check">${isActive ? '✓' : ''}</span>
            </button>
        `;
    }).join("");

    list.querySelectorAll(".player-drawer-item").forEach(btn => {
        btn.addEventListener("click", () => {
            const id = btn.getAttribute("data-id");
            const label = btn.getAttribute("data-label");
            const type = btn.getAttribute("data-type");
            switchQualityOnFly(id, label, type);
        });
    });

    drawer.style.display = "flex";
    activeDrawer = 'quality';
    showOSD();
    setTimeout(() => {
        const activeItem = list.querySelector(".player-drawer-item.active") || list.querySelector(".player-drawer-item");
        if (activeItem) activeItem.focus();
    }, 40);
}

function switchQualityOnFly(id, label, type) {
    closePlayerDrawers();
    const lbl = document.getElementById("osd-label-quality");
    const badge = document.getElementById("osd-badge-quality");
    if (lbl) lbl.textContent = label;
    if (badge) badge.textContent = label;

    if (type === 'hls_level' && hlsInstance) {
        hlsInstance.currentLevel = parseInt(id);
        showPlayerToast(`⚙️ Качество: ${label}`);
        return;
    }

    if (type === 'stream_obj' && currentStreams && activeSource) {
        const streamIdx = parseInt(id);
        const newStream = currentStreams[activeSource].streams[streamIdx];
        if (newStream) {
            selectedStream = newStream;
            const video = document.getElementById("tv-video");
            const curSec = video ? video.currentTime : 0;
            showPlayerToast(`⚙️ Качество: ${label}`);
            playStream(selectedStream, curSec);
        }
    }
}

function openEpisodesDrawer() {
    closePlayerDrawers();
    lastOsdFocusElement = document.getElementById("osd-btn-episodes");
    const drawer = document.getElementById("player-drawer-episodes");
    const seasonsRow = document.getElementById("player-seasons-tabs");
    const list = document.getElementById("player-episodes-list");
    if (!drawer || !list) return;

    const seasons = (currentDetails && currentDetails.seasons && currentDetails.seasons.length > 0)
        ? currentDetails.seasons
        : [{ season_id: 1, title: "Сезон 1", episodes: Array.from({length: 12}, (_, i) => ({ episode_id: i + 1, title: `Серия ${i + 1}`, season_id: 1 })) }];

    let currentSId = activeSeasonId || seasons[0].season_id;

    const renderSeasons = () => {
        if (seasonsRow) {
            seasonsRow.innerHTML = seasons.map(s => `
                <button class="season-chip ${s.season_id === currentSId ? 'active' : ''}" data-sid="${s.season_id}" tabindex="0">
                    ${s.title || 'Сезон ' + s.season_id}
                </button>
            `).join("");

            seasonsRow.querySelectorAll(".season-chip").forEach(chip => {
                chip.addEventListener("click", () => {
                    currentSId = parseInt(chip.getAttribute("data-sid"));
                    renderSeasons();
                    renderEpisodes();
                });
            });
        }
    };

    const renderEpisodes = () => {
        const activeSeasonObj = seasons.find(s => s.season_id === currentSId) || seasons[0];
        const eps = activeSeasonObj ? activeSeasonObj.episodes : [];

        list.innerHTML = eps.map(ep => {
            const isPlaying = (currentSId === activeSeasonId && ep.episode_id === activeEpisodeId);
            return `
                <button class="player-drawer-item ${isPlaying ? 'active' : ''}" data-sid="${currentSId}" data-eid="${ep.episode_id}" tabindex="0">
                    <span>${ep.title || 'Серия ' + ep.episode_id}</span>
                    <span class="item-check">${isPlaying ? '▶ Воспроизводится' : ''}</span>
                </button>
            `;
        }).join("");

        list.querySelectorAll(".player-drawer-item").forEach(btn => {
            btn.addEventListener("click", () => {
                const s = parseInt(btn.getAttribute("data-sid"));
                const e = parseInt(btn.getAttribute("data-eid"));
                switchEpisodeOnFly(s, e);
            });
        });
    };

    renderSeasons();
    renderEpisodes();

    drawer.style.display = "flex";
    activeDrawer = 'episodes';
    showOSD();
    setTimeout(() => {
        const activeEp = list.querySelector(".player-drawer-item.active") || list.querySelector(".player-drawer-item");
        if (activeEp) activeEp.focus();
    }, 40);
}

function switchEpisodeOnFly(seasonNum, episodeNum) {
    closePlayerDrawers();
    activeSeasonId = seasonNum;
    activeEpisodeId = episodeNum;

    // Check watch history for this specific episode
    const prog = getMediaProgress(currentMediaItem?.id, currentMediaItem?.kpId, currentMediaItem?.title, currentMediaItem?.source, currentMediaItem?.year);
    const startSec = (prog && prog.season === seasonNum && prog.episode === episodeNum) ? (prog.positionSec || 0) : 0;

    showPlayerToast(`Сезон ${seasonNum}, Серия ${episodeNum}`);
    reloadStreamsForPlayer(startSec);
}

function playNextEpisode() {
    if (!currentDetails?.is_series) return;
    dismissUpNext();

    const seasons = currentDetails.seasons || [];
    let curSeason = seasons.find(s => s.season_id === activeSeasonId);
    let nextEpId = (activeEpisodeId || 1) + 1;
    let nextSeasonId = activeSeasonId || 1;

    if (curSeason && curSeason.episodes) {
        const existsInCurSeason = curSeason.episodes.some(ep => ep.episode_id === nextEpId);
        if (!existsInCurSeason) {
            // Check next season
            const curIdx = seasons.indexOf(curSeason);
            if (curIdx !== -1 && curIdx + 1 < seasons.length) {
                nextSeasonId = seasons[curIdx + 1].season_id;
                nextEpId = 1;
            } else {
                showPlayerToast("Вы досмотрели все серии сериала!");
                return;
            }
        }
    }

    switchEpisodeOnFly(nextSeasonId, nextEpId);
}

function playPrevEpisode() {
    if (!currentDetails?.is_series) return;
    dismissUpNext();
    if (activeEpisodeId > 1) {
        switchEpisodeOnFly(activeSeasonId, activeEpisodeId - 1);
    }
}

function checkUpNext(currentTime, duration) {
    if (!currentDetails?.is_series || upNextTimer || duration < 60) return;
    const timeLeft = duration - currentTime;
    if (timeLeft <= 22 && timeLeft > 2) {
        const nextEpId = (activeEpisodeId || 1) + 1;
        const banner = document.getElementById("player-up-next-banner");
        const titleEl = document.getElementById("up-next-title");
        const timerEl = document.getElementById("up-next-timer");
        if (!banner) return;

        if (titleEl) titleEl.textContent = `Сезон ${activeSeasonId || 1}, Серия ${nextEpId}`;
        upNextSecondsLeft = Math.round(timeLeft);
        if (timerEl) timerEl.textContent = `Автопереход через ${upNextSecondsLeft} сек`;
        banner.style.display = "flex";

        upNextTimer = setInterval(() => {
            upNextSecondsLeft--;
            if (timerEl) timerEl.textContent = `Автопереход через ${upNextSecondsLeft} сек`;
            if (upNextSecondsLeft <= 1) {
                dismissUpNext();
                playNextEpisode();
            }
        }, 1000);
    }
}

function dismissUpNext() {
    if (upNextTimer) {
        clearInterval(upNextTimer);
        upNextTimer = null;
    }
    const banner = document.getElementById("player-up-next-banner");
    if (banner) banner.style.display = "none";
}

function handlePlayerBack() {
    if (activeDrawer) {
        closePlayerDrawers();
        return true;
    }
    const upNext = document.getElementById("player-up-next-banner");
    if (upNext && upNext.style.display !== "none") {
        dismissUpNext();
        return true;
    }
    const osd = document.getElementById("player-osd");
    if (osdVisible || (osd && (osd.classList.contains("visible") || osd.style.opacity === "1"))) {
        hideOSD(true);
        return true;
    }
    return false;
}

async function reloadStreamsForPlayer(resumeSec = 0) {
    if (!currentMediaItem) return;
    const { id, source, kpId, title } = currentMediaItem;
    const queryId = kpId || id;
    const reqId = ++activeModalRequestId;

    const cacheKey = `${source}_${queryId}_s${activeSeasonId || 1}_e${activeEpisodeId || 1}_a${activeTranslatorId || 'def'}`;
    if (streamPreloadCache.has(cacheKey)) {
        const cachedData = streamPreloadCache.get(cacheKey);
        currentStreams = cachedData;
        renderSourceTabs(cachedData);
        const srcObj = currentStreams[activeSource] || Object.values(currentStreams)[0];
        if (srcObj && srcObj.streams && srcObj.streams.length > 0) {
            const settings = getSettings();
            const bIdx = pickBestStreamIndex(srcObj.streams, settings.quality);
            selectedStream = srcObj.streams[bIdx] || srcObj.streams[0];
            playStream(selectedStream, resumeSec);
            return;
        } else if (srcObj && srcObj.embed_url) {
            selectedStream = { quality: "Embed", url: srcObj.embed_url, stream_type: "iframe" };
            playStream(selectedStream, resumeSec);
            return;
        }
    }

    showBuffering(true);

    try {
        let url = `/api/media/streams?source=${encodeURIComponent(source)}&media_id=${encodeURIComponent(queryId)}&title=${encodeURIComponent(title)}`;
        if (activeSeasonId && activeEpisodeId) {
            url += `&season=${activeSeasonId}&episode=${activeEpisodeId}`;
        }
        if (activeTranslatorId) {
            url += `&audio_id=${encodeURIComponent(activeTranslatorId)}`;
        }

        const res = await fetch(url);
        if (!res.ok) throw new Error("HTTP " + res.status);
        const data = await res.json();
        streamPreloadCache.set(cacheKey, data);
        if (reqId !== activeModalRequestId) return;

        currentStreams = data;
        renderSourceTabs(data);

        const srcObj = currentStreams[activeSource] || Object.values(currentStreams)[0];
        if (srcObj && srcObj.streams && srcObj.streams.length > 0) {
            const settings = getSettings();
            const bIdx = pickBestStreamIndex(srcObj.streams, settings.quality);
            selectedStream = srcObj.streams[bIdx] || srcObj.streams[0];
            playStream(selectedStream, resumeSec);
        } else if (srcObj && srcObj.embed_url) {
            selectedStream = { quality: "Embed", url: srcObj.embed_url, stream_type: "iframe" };
            playStream(selectedStream, resumeSec);
        } else {
            showPlayerError(selectedStream || { quality: "Stream" }, "Потоки для выбранной озвучки/серии не найдены");
        }
    } catch (err) {
        console.warn("Reload streams failed:", err);
        showBuffering(false);
    }
}

function playStream(stream, startSec = 0, startPaused = false) {
    stopDetailsPreview();
    stopCardPreview();
    const playerModal = document.getElementById("player-modal");
    const video = document.getElementById("tv-video");
    const iframe = document.getElementById("tv-iframe");
    const errOverlay = document.getElementById("player-error-overlay");

    if (errOverlay) errOverlay.style.display = "none";
    showBuffering(false);
    dismissUpNext();
    closePlayerDrawers();

    playerActive = true;
    selectedStream = stream;
    window.activeStreamStartSec = startSec;

    // Parse skip times from stream or current source metadata
    const activeStreamData = currentStreams?.[activeSource];
    const skipData = stream.skip_time || activeStreamData?.skip_time;
    if (skipData) {
        currentSkipTimes = {
            introStart: (typeof skipData.intro_start === 'number') ? skipData.intro_start : null,
            introEnd: (typeof skipData.intro_end === 'number') ? skipData.intro_end : null,
            outroStart: (typeof skipData.outro_start === 'number') ? skipData.outro_start : null,
            outroEnd: (typeof skipData.outro_end === 'number') ? skipData.outro_end : null,
        };
    } else {
        currentSkipTimes = { introStart: null, introEnd: null, outroStart: null, outroEnd: null };
    }
    window.currentSkipTimes = currentSkipTimes;

    // Trigger lazy background preload for next episode
    scheduleNextEpisodePreload();

    // Update Titles and Meta in OSD
    const mainTitle = document.getElementById("osd-main-title");
    const subTitle = document.getElementById("osd-sub-title");
    const voiceBadge = document.getElementById("osd-badge-voice");
    const qualityBadge = document.getElementById("osd-badge-quality");
    const voiceLabel = document.getElementById("osd-label-audio");
    const qualityLabel = document.getElementById("osd-label-quality");
    const prevEpBtn = document.getElementById("osd-btn-prev-ep");
    const nextEpBtn = document.getElementById("osd-btn-next-ep");
    const epDrawerBtn = document.getElementById("osd-btn-episodes");

    const mediaTitle = currentMediaItem?.title || document.getElementById("modal-title")?.textContent || "Видео";
    if (mainTitle) mainTitle.textContent = mediaTitle;

    if (currentDetails?.is_series) {
        if (subTitle) subTitle.textContent = `Сезон ${activeSeasonId || 1}, Серия ${activeEpisodeId || 1}`;
        if (prevEpBtn) {
            prevEpBtn.style.display = "inline-flex";
            prevEpBtn.disabled = (activeEpisodeId <= 1);
        }
        if (nextEpBtn) nextEpBtn.style.display = "inline-flex";
        if (epDrawerBtn) epDrawerBtn.style.display = "inline-flex";
    } else {
        if (subTitle) subTitle.textContent = `${currentMediaItem?.year || '2026'} • Фильм`;
        if (prevEpBtn) prevEpBtn.style.display = "none";
        if (nextEpBtn) nextEpBtn.style.display = "none";
        if (epDrawerBtn) epDrawerBtn.style.display = "none";
    }

    // Voiceover label
    let transName = "Озвучка";
    if (currentDetails?.translators && activeTranslatorId) {
        const t = currentDetails.translators.find(x => String(x.id) === String(activeTranslatorId));
        if (t) transName = t.name;
    }
    if (voiceLabel) voiceLabel.textContent = transName.length > 14 ? transName.substring(0, 14) + '…' : transName;
    if (voiceBadge) {
        voiceBadge.textContent = transName;
        voiceBadge.style.display = (transName !== "Озвучка") ? "inline-block" : "none";
    }

    // Quality label
    const qStr = stream.quality || "1080p";
    if (qualityLabel) qualityLabel.textContent = qStr.replace(/\s*\([^)]*\)/, '');
    if (qualityBadge) qualityBadge.textContent = qStr.replace(/\s*\([^)]*\)/, '');

    playerModal.style.display = "flex";
    setupMediaSession(currentMediaItem, activeSeasonId, activeEpisodeId);

    // Clean up existing HLS & progress timer
    if (hlsInstance) {
        hlsInstance.destroy();
        hlsInstance = null;
    }
    if (playerProgressTimer) {
        clearInterval(playerProgressTimer);
        playerProgressTimer = null;
    }

    const loadingBackdrop = document.getElementById("player-loading-backdrop");
    const loadingTitle = document.getElementById("player-loading-title");
    const loadingSub = document.getElementById("player-loading-sub");

    if (stream.stream_type === "iframe") {
        if (loadingBackdrop) loadingBackdrop.style.display = "none";
        video.style.display = "none";
        iframe.style.display = "block";
        iframe.src = stream.url;
        showOSD(document.getElementById("osd-btn-back"));
    } else {
        iframe.style.display = "none";
        video.style.display = "block";
        video.style.opacity = "0"; // Keep hidden until first frame is ready

        if (loadingBackdrop) {
            loadingBackdrop.style.display = "flex";
            showBuffering(false);
            if (loadingTitle) loadingTitle.textContent = mediaTitle;
            if (loadingSub) loadingSub.textContent = `Подключение: ${qStr} (${stream.source_name || activeSource})`;
        }

        const revealVideo = () => {
            if (video.currentTime > 0.05 || video.readyState >= 3) {
                video.style.opacity = "1";
                if (loadingBackdrop) loadingBackdrop.style.display = "none";
            }
        };

        const onVideoReadyToSeek = () => {
            if (startSec > 0 && Math.abs(video.currentTime - startSec) > 1) {
                try {
                    video.currentTime = startSec;
                } catch (e) {}
            }
            if (startPaused) {
                video.pause();
                const icon = document.getElementById("osd-play-icon");
                if (icon) icon.textContent = "▶";
                showCenterFeedback("Пауза", "❚❚");
                showPlayerToast(`⏸ Фильм на паузе (${formatPlayerTime(startSec)}). Нажмите OK для продолжения.`, 5000);
            } else if (startSec > 5) {
                showPlayerToast(`▶ Воспроизведение продолжено с ${formatPlayerTime(startSec)}`);
            }
        };

        video.onloadeddata = onVideoReadyToSeek;
        video.onplaying = () => {
            video.style.opacity = "1";
            if (loadingBackdrop) loadingBackdrop.style.display = "none";
        };
        video.ontimeupdate = () => {
            if (video.currentTime > 0.1 && video.style.opacity !== "1") {
                video.style.opacity = "1";
                if (loadingBackdrop) loadingBackdrop.style.display = "none";
            }
        };

        if (Hls.isSupported() && stream.url.includes(".m3u8")) {
            hlsInstance = new Hls({
                enableWorker: true,
                lowLatencyMode: false,
                maxBufferLength: 120,
                maxMaxBufferLength: 300,
                maxBufferSize: 60 * 1024 * 1024,
                backBufferLength: 60,
                progressive: true,
                // Critical buffer hole tolerance and seek stall recovery
                maxBufferHole: 0.5,
                maxFragLookUpTolerance: 0.5,
                nudgeOffset: 0.2,
                nudgeMaxRetry: 8,
                highBufferWatchdogPeriod: 2,
                fragLoadingTimeOut: 15000,
                manifestLoadingTimeOut: 15000,
                levelLoadingTimeOut: 15000
            });
            hlsInstance.loadSource(stream.url);
            hlsInstance.attachMedia(video);
            hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
                if (startPaused) {
                    onVideoReadyToSeek();
                } else {
                    video.play().then(onVideoReadyToSeek).catch(e => console.log("Autoplay blocked:", e));
                }
            });
            hlsInstance.on(Hls.Events.FRAG_LOADED, () => {
                // Fragment buffered; keep video hidden until onplaying renders actual decoded frames
            });
            hlsInstance.on(Hls.Events.ERROR, (event, data) => {
                console.warn("HLS stream event error:", data.type, data.details);
                // Recover from non-fatal buffer stalls / hole gaps during seeking
                if (data.details === "bufferStalledError" || 
                    data.details === "bufferSeekOverHole" || 
                    data.details === "bufferNudgeOnStall") {
                    if (hlsInstance) {
                        try { hlsInstance.startLoad(video.currentTime); } catch (e) {}
                    }
                    return;
                }
                if (data.fatal) {
                    if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
                        console.log("HLS network error, attempting startLoad recovery...");
                        try { hlsInstance.startLoad(); } catch (e) {}
                    } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
                        console.log("HLS media error, attempting recoverMediaError...");
                        try { hlsInstance.recoverMediaError(); } catch (e) {}
                    } else {
                        showPlayerError(stream, "Ошибка загрузки HLS потока");
                    }
                }
            });
        } else {
            video.src = stream.url;
            video.onloadedmetadata = onVideoReadyToSeek;
            video.onerror = () => {
                showPlayerError(stream, "Ошибка прямого воспроизведения видео");
            };
            if (startPaused) {
                video.pause();
                onVideoReadyToSeek();
            } else {
                video.play().then(onVideoReadyToSeek).catch(e => console.log("Autoplay blocked:", e));
            }
        }

        // Periodic progress and session save every 3 seconds
        playerProgressTimer = setInterval(() => {
            if (video && video.currentTime > 5 && video.duration > 0 && currentMediaItem) {
                saveMediaProgress(currentMediaItem, {
                    season: activeSeasonId || 1,
                    episode: activeEpisodeId || 1,
                    positionSec: Math.floor(video.currentTime),
                    durationSec: Math.floor(video.duration),
                });
                saveSessionSnapshot(video.paused);
            }
        }, 3000);

        showOSD(document.getElementById("osd-btn-play"));
    }

    if (clockInterval) clearInterval(clockInterval);
    clockInterval = setInterval(updateOsdClock, 10000);
    updateOsdClock();

    // Request fullscreen on TV
    playerModal.requestFullscreen().catch(() => {});
}

function showPlayerError(stream, message) {
    const overlay = document.getElementById("player-error-overlay");
    const title = document.getElementById("player-error-title");
    const desc = document.getElementById("player-error-desc");
    if (!overlay) return;

    title.textContent = `Поток ${stream.quality} недоступен`;
    desc.textContent = `${message}. Сервер источника не отвечает. Нажмите кнопку ниже для переключения на другой источник.`;
    overlay.style.display = "flex";

    const btnNext = document.getElementById("btn-player-next-source");
    const btnRetry = document.getElementById("btn-player-retry");
    const btnClose = document.getElementById("btn-player-close-error");

    if (btnNext) {
        btnNext.onclick = () => {
            overlay.style.display = "none";
            switchToNextStreamOrSource();
        };
        btnNext.focus();
    }
    if (btnRetry) {
        btnRetry.onclick = () => {
            overlay.style.display = "none";
            playStream(stream);
        };
    }
    if (btnClose) {
        btnClose.onclick = () => {
            overlay.style.display = "none";
            closePlayer();
        };
    }
}

function switchToNextStreamOrSource() {
    if (!currentStreams || !activeSource) return;
    const sourceObj = currentStreams[activeSource];

    // Try next stream in current source
    if (sourceObj && sourceObj.streams && sourceObj.streams.length > 1) {
        const curIdx = sourceObj.streams.findIndex(s => s.url === selectedStream?.url);
        const nextIdx = (curIdx + 1) % sourceObj.streams.length;
        if (nextIdx !== curIdx) {
            selectedStream = sourceObj.streams[nextIdx];
            playStream(selectedStream);
            return;
        }
    }

    // Otherwise try next working source
    const workingKeys = Object.keys(currentStreams).filter(k => (currentStreams[k].streams?.length || currentStreams[k].embed_url));
    const curSourceIdx = workingKeys.indexOf(activeSource);
    const nextSourceIdx = (curSourceIdx + 1) % workingKeys.length;
    if (nextSourceIdx !== curSourceIdx) {
        const nextKey = workingKeys[nextSourceIdx];
        selectSource(nextKey);
        document.querySelectorAll(".source-tab-btn").forEach(b => {
            b.classList.toggle("active", b.getAttribute("data-source") === nextKey);
        });
        if (selectedStream) {
            playStream(selectedStream);
        }
    }
}

function closePlayer() {
    const playerModal = document.getElementById("player-modal");
    const video = document.getElementById("tv-video");
    const iframe = document.getElementById("tv-iframe");
    const overlay = document.getElementById("player-error-overlay");

    if (overlay) overlay.style.display = "none";
    showBuffering(false);
    dismissUpNext();
    closePlayerDrawers();

    if (playerProgressTimer) {
        clearInterval(playerProgressTimer);
        playerProgressTimer = null;
    }
    if (clockInterval) {
        clearInterval(clockInterval);
        clockInterval = null;
    }
    if (osdHideTimer) {
        clearTimeout(osdHideTimer);
        osdHideTimer = null;
    }
    if (nextEpPreloadTimer) {
        clearTimeout(nextEpPreloadTimer);
        nextEpPreloadTimer = null;
    }
    const btnIntro = document.getElementById("player-btn-skip-intro");
    const btnOutro = document.getElementById("player-btn-skip-outro");
    if (btnIntro) btnIntro.style.display = "none";
    if (btnOutro) btnOutro.style.display = "none";

    // Save final position before stopping
    if (video && video.currentTime > 5 && video.duration > 0 && currentMediaItem) {
        saveMediaProgress(currentMediaItem, {
            season: activeSeasonId || 1,
            episode: activeEpisodeId || 1,
            positionSec: Math.floor(video.currentTime),
            durationSec: Math.floor(video.duration)
        });
    }

    if (document.fullscreenElement) {
        document.exitFullscreen().catch(() => {});
    }

    const loadingBackdrop = document.getElementById("player-loading-backdrop");
    if (loadingBackdrop) loadingBackdrop.style.display = "none";
    const btnExtYt = document.getElementById("osd-btn-external-youtube");
    if (btnExtYt) btnExtYt.style.display = "none";

    video.pause();
    video.style.opacity = "0";
    video.src = "";
    iframe.src = "";

    if (hlsInstance) {
        hlsInstance.destroy();
        hlsInstance = null;
    }

    playerActive = false;
    playerModal.style.display = "none";

    if ('mediaSession' in navigator) {
        try {
            navigator.mediaSession.playbackState = 'none';
        } catch (e) {}
    }

    clearSessionSnapshot();
    updateModalProgressUI();
    if (currentView === "history") {
        renderHistoryView();
    }

    if (isPlayingTrailer) {
        isPlayingTrailer = false;
        if (trailerReturnMedia) {
            const itemToRestore = trailerReturnMedia;
            trailerReturnMedia = null;
            switchView("details");
            setTimeout(() => {
                const btnTrailer = document.getElementById("btn-modal-trailer") || document.getElementById("btn-modal-play");
                if (btnTrailer) btnTrailer.focus();
            }, 100);
            return;
        }
    }
}

function saveSessionSnapshot(isPaused = false) {
    if (!playerActive || !currentMediaItem || !selectedStream) {
        return;
    }
    const video = document.getElementById("tv-video");
    const curTime = video ? (video.currentTime || 0) : 0;
    const session = {
        mediaItem: currentMediaItem,
        source: activeSource,
        stream: selectedStream,
        translatorId: activeTranslatorId,
        seasonId: activeSeasonId,
        episodeId: activeEpisodeId,
        positionSec: Math.floor(curTime),
        duration: video ? Math.floor(video.duration || 0) : 0,
        savedAt: Date.now(),
        paused: isPaused || (video ? video.paused : true)
    };
    try {
        localStorage.setItem("showhub_last_session", JSON.stringify(session));
    } catch (e) {
        console.warn("Failed to save session snapshot:", e);
    }
}

function clearSessionSnapshot() {
    try {
        localStorage.removeItem("showhub_last_session");
    } catch (e) {}
}

function restoreLastSessionIfAny() {
    try {
        const raw = localStorage.getItem("showhub_last_session");
        if (!raw) return;
        const session = JSON.parse(raw);
        if (!session || !session.mediaItem || !session.stream) return;
        // Expire session after 24 hours
        if (Date.now() - (session.savedAt || 0) > 86400000) {
            clearSessionSnapshot();
            return;
        }
        console.log("Restoring last session on pause:", session.mediaItem.title, "at", session.positionSec);
        currentMediaItem = session.mediaItem;
        activeSource = session.source;
        activeTranslatorId = session.translatorId;
        activeSeasonId = session.seasonId;
        activeEpisodeId = session.episodeId;
        selectedStream = session.stream;

        playStream(session.stream, session.positionSec || 0, true);
    } catch (e) {
        console.warn("Failed to restore session:", e);
    }
}

window.onAndroidPause = function() {
    console.log("Android Activity onPause triggered");
    if (playerActive) {
        const video = document.getElementById("tv-video");
        if (video) video.pause();
        saveSessionSnapshot(true);
    }
};

window.onAndroidResume = function() {
    console.log("Android Activity onResume triggered");
    if (trailerReturnMedia) {
        const itemToRestore = trailerReturnMedia;
        trailerReturnMedia = null;
        switchView("details");
        setTimeout(() => {
            const btnTrailer = document.getElementById("btn-modal-trailer") || document.getElementById("btn-modal-play");
            if (btnTrailer) btnTrailer.focus();
        }, 150);
        return;
    }
    if (!playerActive) {
        restoreLastSessionIfAny();
    }
};

document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "hidden") {
        if (playerActive) {
            const video = document.getElementById("tv-video");
            if (video) video.pause();
            saveSessionSnapshot(true);
        }
    }
});

window.addEventListener("pagehide", () => {
    if (playerActive) {
        saveSessionSnapshot(true);
    }
});

function initPlayerOSD() {
    document.getElementById("osd-btn-back")?.addEventListener("click", closePlayer);
    document.getElementById("btn-close-player")?.addEventListener("click", closePlayer);

    document.getElementById("osd-btn-play")?.addEventListener("click", togglePlayPause);
    document.getElementById("osd-center-play-btn")?.addEventListener("click", togglePlayPause);
    document.getElementById("osd-btn-rewind")?.addEventListener("click", () => seekRelative(-10));
    document.getElementById("osd-btn-forward")?.addEventListener("click", () => seekRelative(10));
    document.getElementById("osd-btn-prev-ep")?.addEventListener("click", playPrevEpisode);
    document.getElementById("osd-btn-next-ep")?.addEventListener("click", playNextEpisode);
    document.getElementById("player-btn-skip-intro")?.addEventListener("click", skipIntro);
    document.getElementById("player-btn-skip-outro")?.addEventListener("click", skipOutro);

    document.getElementById("osd-btn-audio")?.addEventListener("click", openAudioDrawer);
    document.getElementById("osd-btn-subs")?.addEventListener("click", openSubsDrawer);
    document.getElementById("osd-btn-quality")?.addEventListener("click", openQualityDrawer);
    document.getElementById("osd-btn-episodes")?.addEventListener("click", openEpisodesDrawer);

    document.getElementById("osd-btn-external")?.addEventListener("click", () => {
        if (selectedStream) {
            const video = document.getElementById("tv-video");
            launchExternalPlayer(selectedStream, video ? video.currentTime : 0);
        }
    });

    // Scrubber click seeking
    document.getElementById("osd-progress-track")?.addEventListener("click", (e) => {
        // Prevent synthetic click emitted by TV remote OK / Enter key from resetting position to 00:00!
        if (e.clientX === 0 && e.clientY === 0) {
            togglePlayPause();
            return;
        }
        const track = e.currentTarget;
        const rect = track.getBoundingClientRect();
        if (rect.width <= 0) return;
        const pct = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
        const video = document.getElementById("tv-video");
        if (video && video.duration) {
            seekToTime(pct * video.duration);
        }
    });

    // Drawers close triggers
    document.getElementById("btn-close-drawer-audio")?.addEventListener("click", closePlayerDrawers);
    document.getElementById("btn-close-drawer-subs")?.addEventListener("click", closePlayerDrawers);
    document.getElementById("btn-close-drawer-quality")?.addEventListener("click", closePlayerDrawers);
    document.getElementById("btn-close-drawer-episodes")?.addEventListener("click", closePlayerDrawers);

    // Up Next triggers
    document.getElementById("btn-up-next-play")?.addEventListener("click", playNextEpisode);
    document.getElementById("btn-up-next-cancel")?.addEventListener("click", dismissUpNext);

    // Mouse / touch activity waking up OSD
    const modal = document.getElementById("player-modal");
    if (modal) {
        modal.addEventListener("mousemove", () => {
            showOSD();
            resetOsdTimeout();
        });
        modal.addEventListener("click", (e) => {
            if (e.target.id === "tv-video" || e.target.id === "player-viewport") {
                togglePlayPause();
            }
        });
    }

    // Video events
    const video = document.getElementById("tv-video");
    if (video) {
        let seekWatchdogTimer = null;
        const clearSeekWatchdog = () => {
            if (seekWatchdogTimer) {
                clearTimeout(seekWatchdogTimer);
                seekWatchdogTimer = null;
            }
        };

        const onSeekOrWait = () => {
            showBuffering(true);
            clearSeekWatchdog();
            // If buffering or seeking stalls longer than 2.8s without readyState >= 3, un-stick decoder
            seekWatchdogTimer = setTimeout(() => {
                if (playerActive && video && (video.seeking || video.readyState < 3)) {
                    console.warn("Seek/buffer stall detected by watchdog, unsticking decoder...");
                    let unblocked = false;
                    if (video.buffered && video.buffered.length > 0) {
                        for (let i = 0; i < video.buffered.length; i++) {
                            const bStart = video.buffered.start(i);
                            if (bStart > video.currentTime && (bStart - video.currentTime) < 2.5) {
                                video.currentTime = bStart + 0.05;
                                unblocked = true;
                                break;
                            }
                        }
                    }
                    if (!unblocked) {
                        video.currentTime = video.currentTime + 0.35;
                    }
                    if (hlsInstance) {
                        try { hlsInstance.startLoad(video.currentTime); } catch (e) {}
                    }
                    if (!video.paused) {
                        video.play().catch(() => {});
                    }
                }
            }, 2800);
        };

        const onPlaybackResumed = () => {
            clearSeekWatchdog();
            showBuffering(false);
            const icon = document.getElementById("osd-play-icon");
            if (icon && !video.paused) icon.textContent = "❚❚";
        };

        video.addEventListener("timeupdate", () => {
            updatePlayerTimeline();
            checkUpNext(video.currentTime, video.duration);
        });
        video.addEventListener("progress", updatePlayerTimeline);
        video.addEventListener("waiting", onSeekOrWait);
        video.addEventListener("seeking", onSeekOrWait);
        video.addEventListener("seeked", () => {
            if (hlsInstance) {
                try { hlsInstance.startLoad(video.currentTime); } catch (e) {}
            }
            if (wasPlayingBeforeSeek) {
                video.play().catch(e => console.warn("Auto-resume play failed:", e));
            }
            if (video.paused || video.readyState >= 3) {
                onPlaybackResumed();
            }
        });
        video.addEventListener("canplay", () => {
            if (wasPlayingBeforeSeek && video.paused) {
                video.play().catch(e => console.warn("Auto-resume canplay failed:", e));
            }
            onPlaybackResumed();
        });
        video.addEventListener("canplaythrough", onPlaybackResumed);
        video.addEventListener("playing", () => {
            onPlaybackResumed();
            if ('mediaSession' in navigator) {
                try { navigator.mediaSession.playbackState = 'playing'; } catch (e) {}
            }
        });
        video.addEventListener("pause", () => {
            const icon = document.getElementById("osd-play-icon");
            if (icon) icon.textContent = "▶";
            if ('mediaSession' in navigator) {
                try { navigator.mediaSession.playbackState = 'paused'; } catch (e) {}
            }
        });
        video.addEventListener("ended", () => {
            if (currentDetails?.is_series) {
                playNextEpisode();
            } else {
                showOSD(document.getElementById("osd-btn-play"));
            }
        });
    }
}

/* =========================================================
   Canary Health Diagnostics & Alert System
   ========================================================= */
async function checkHealth() {
    try {
        const res = await fetch("/api/health");
        const data = await res.json();
        updateHealthUI(data);
    } catch (err) {
        console.error("Health check error:", err);
    }
}

async function refreshHealth() {
    const btn = document.getElementById("btn-refresh-health");
    btn.textContent = "Проверяем источники...";
    btn.disabled = true;

    try {
        const res = await fetch("/api/health/refresh", { method: "POST" });
        const data = await res.json();
        updateHealthUI(data);
    } catch (err) {
        alert("Ошибка обновления: " + err.message);
    } finally {
        btn.textContent = "Проверить источники сейчас";
        btn.disabled = false;
    }
}

function updateHealthUI(summary) {
    const banner = document.getElementById("canary-warning-banner");
    const warningDetails = document.getElementById("warning-text-details");
    const healthIcon = document.getElementById("health-icon");
    const alertBadge = document.getElementById("health-alert-badge");

    const brokenReports = summary.reports.filter(r => r.needs_rework);

    if (brokenReports.length > 0) {
        banner.style.display = "flex";
        const brokenNames = brokenReports.map(r => r.source_name.toUpperCase()).join(", ");
        warningDetails.textContent = `Обнаружены изменения/поломки в источниках: [${brokenNames}]. Требуется актуализация парсеров или смена зеркал.`;
        if (healthIcon) healthIcon.className = "status-dot warning";
        alertBadge.style.display = "inline-block";
        alertBadge.textContent = `${brokenReports.length} требуют внимания`;
    } else {
        banner.style.display = "none";
        if (healthIcon) healthIcon.className = "status-dot online";
        alertBadge.style.display = "none";
    }

    if (currentView === "health") {
        renderHealthView(summary);
    }
}

function renderHealthView(summaryData) {
    fetch("/api/health").then(res => res.json()).then(summary => {
        const summaryCards = document.getElementById("health-summary");
        const tbody = document.getElementById("health-table-body");

        summaryCards.innerHTML = `
            <div class="health-stat-card">
                <div class="stat-value" style="color: var(--accent);">${summary.total_sources}</div>
                <div class="stat-label">Всего декомпилированных источников</div>
            </div>
            <div class="health-stat-card">
                <div class="stat-value" style="color: var(--accent-success);">${summary.ok_sources}</div>
                <div class="stat-label">Источников полностью онлайн</div>
            </div>
            <div class="health-stat-card">
                <div class="stat-value" style="color: ${summary.broken_sources > 0 ? 'var(--accent-danger)' : 'var(--accent-success)'};">${summary.broken_sources}</div>
                <div class="stat-label">Требуют внимания / переделки</div>
            </div>
        `;

        tbody.innerHTML = summary.reports.map(r => {
            const statusClass = r.status === "OK" ? "ok" : (r.status === "DEGRADED" ? "degraded" : "broken");
            return `
                <tr>
                    <td><strong>${r.source_name.toUpperCase()}</strong></td>
                    <td><span class="source-type-pill">${r.source_name === 'torrents' ? 'P2P / Torrent' : 'CDN Balancer'}</span></td>
                    <td><span class="status-badge ${statusClass}">${r.status}</span></td>
                    <td>${Math.round(r.latency_ms)} ms</td>
                    <td>
                        ${r.message}
                        ${r.needs_rework ? '<span class="rework-tag">ТРЕБУЕТСЯ ПЕРЕДЕЛКА</span>' : ''}
                    </td>
                    <td>
                        <button class="btn-secondary" onclick="alert('Эндпоинт:\\n${r.endpoint_tested}\\n\\nДиагностика:\\n${r.message}')" tabindex="0">Инфо</button>
                    </td>
                </tr>
            `;
        }).join("");
    });
}

/* =========================================================
   Filmix Account Management (PRO+ Authorization)
   ========================================================= */
function initFilmixAccount() {
    document.getElementById("btn-submit-login")?.addEventListener("click", performFilmixLogin);
    document.getElementById("btn-submit-cookies")?.addEventListener("click", performCookieSubmit);
    document.getElementById("btn-logout-filmix")?.addEventListener("click", performFilmixLogout);
    document.getElementById("btn-refresh-account")?.addEventListener("click", loadFilmixAccount);
}

async function loadFilmixAccount() {
    try {
        const res = await fetch("/api/account/filmix");
        const profile = await res.json();
        filmixProfile = profile;
        window.filmixProfile = profile;
        updateAccountUI(profile);
    } catch (e) {
        console.error("Failed to load Filmix profile:", e);
    }
}

function updateAccountUI(profile) {
    const pill = document.getElementById("account-pill-status");
    const userName = document.getElementById("account-user-name");
    const badgeStatus = document.getElementById("account-badge-status");
    const badgePro = document.getElementById("account-badge-pro");
    const metaText = document.getElementById("account-meta-text");
    const avatar = document.getElementById("account-avatar");
    const loggedActions = document.getElementById("account-logged-actions");
    const loginCard = document.getElementById("account-login-card");

    if (profile.is_logged_in) {
        const badgeLabel = profile.is_pro_plus ? "PRO+" : (profile.is_pro ? "PRO" : "Авторизован");
        if (pill) {
            pill.textContent = badgeLabel;
            pill.className = "status-pill-small badge-authorized";
        }
        if (userName) userName.textContent = profile.display_name || profile.username;
        if (badgeStatus) {
            badgeStatus.textContent = "Авторизован";
            badgeStatus.style.background = "rgba(16, 185, 129, 0.2)";
            badgeStatus.style.color = "var(--accent-success)";
        }
        if (badgePro) {
            if (profile.is_pro_plus || profile.is_pro) {
                badgePro.textContent = profile.is_pro_plus ? "PRO+ ULTRA" : "PRO";
                badgePro.style.display = "inline-block";
            } else {
                badgePro.style.display = "none";
            }
        }
        if (metaText) {
            metaText.textContent = `Подписка: ${profile.pro_date || 'Активна'}. Доступны прямые потоки 1080p Ultra+, 4K UHD и приватные каталоги.`;
        }
        if (avatar) avatar.textContent = profile.is_pro_plus ? "FX+" : "FX";
        if (loggedActions) loggedActions.style.display = "flex";
        if (loginCard) loginCard.style.display = "none";
    } else {
        if (pill) {
            pill.textContent = "Гость";
            pill.className = "status-pill-small badge-guest";
        }
        if (userName) userName.textContent = "Гостевой режим";
        if (badgeStatus) {
            badgeStatus.textContent = "Не авторизован";
            badgeStatus.style.background = "rgba(255, 255, 255, 0.1)";
            badgeStatus.style.color = "var(--text-secondary)";
        }
        if (badgePro) badgePro.style.display = "none";
        if (metaText) metaText.textContent = "Для доступа к качеству 1080p Ultra+ и 4K UHD войдите в аккаунт Filmix.";
        if (avatar) avatar.textContent = "TV";
        if (loggedActions) loggedActions.style.display = "none";
        if (loginCard) loginCard.style.display = "block";
    }
}

async function performFilmixLogin() {
    const loginInput = document.getElementById("filmix-login");
    const passInput = document.getElementById("filmix-password");
    const statusMsg = document.getElementById("login-status-msg");
    const btn = document.getElementById("btn-submit-login");

    const username = loginInput.value.trim();
    const password = passInput.value.trim();

    if (!username || !password) {
        statusMsg.textContent = "Пожалуйста, укажите логин и пароль.";
        statusMsg.className = "status-msg error";
        return;
    }

    btn.disabled = true;
    statusMsg.textContent = "Авторизация через Filmix API...";
    statusMsg.className = "status-msg";

    try {
        const res = await fetch("/api/account/filmix/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ login_name: username, login_password: password })
        });
        const data = await res.json();
        btn.disabled = false;

        if (data.success) {
            statusMsg.textContent = data.message || "Успешный вход!";
            statusMsg.className = "status-msg success";
            updateAccountUI(data.profile);
            checkHealth();
            passInput.value = "";
        } else {
            statusMsg.textContent = data.message || "Ошибка авторизации";
            statusMsg.className = "status-msg error";
        }
    } catch (e) {
        btn.disabled = false;
        statusMsg.textContent = `Ошибка сети: ${e.message}`;
        statusMsg.className = "status-msg error";
    }
}

async function performCookieSubmit() {
    const cookieInput = document.getElementById("filmix-cookie-input");
    const statusMsg = document.getElementById("cookie-status-msg");
    const btn = document.getElementById("btn-submit-cookies");

    const rawCookies = cookieInput.value.trim();
    if (!rawCookies) {
        statusMsg.textContent = "Вставьте строку Cookies.";
        statusMsg.className = "status-msg error";
        return;
    }

    btn.disabled = true;
    statusMsg.textContent = "Проверка и применение Cookies...";
    statusMsg.className = "status-msg";

    try {
        const res = await fetch("/api/account/filmix/cookies", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ cookies: rawCookies })
        });
        const data = await res.json();
        btn.disabled = false;

        if (data.success) {
            statusMsg.textContent = "Куки успешно сохранены!";
            statusMsg.className = "status-msg success";
            updateAccountUI(data.profile);
            checkHealth();
        } else {
            statusMsg.textContent = data.message || "Ошибка сохранения кук";
            statusMsg.className = "status-msg error";
        }
    } catch (e) {
        btn.disabled = false;
        statusMsg.textContent = `Ошибка: ${e.message}`;
        statusMsg.className = "status-msg error";
    }
}

async function performFilmixLogout() {
    if (!confirm("Вы уверены, что хотите выйти из аккаунта Filmix?")) return;

    try {
        await fetch("/api/account/filmix/logout", { method: "POST" });
        loadFilmixAccount();
        checkHealth();
    } catch (e) {
        console.error("Logout failed:", e);
    }
}

/* =========================================================
   Unified Settings Modal Management
   ========================================================= */
function initSettingsModal() {
    const modal = document.getElementById("settings-modal");
    const navSettings = document.getElementById("nav-settings");
    const btnClose = document.getElementById("btn-close-settings-modal");
    const btnSave = document.getElementById("btn-close-settings-save");
    const backdrop = modal?.querySelector(".modal-backdrop");

    // Form inputs
    const prefTheme = document.getElementById("pref-theme");
    const prefPlayer = document.getElementById("pref-player");
    const prefQuality = document.getElementById("pref-quality");
    const prefVoice = document.getElementById("pref-voice");
    const prefVoiceCustom = document.getElementById("pref-voice-custom");
    const prefAutoSelect = document.getElementById("pref-auto-select");

    const settings = getSettings();
    if (prefTheme) {
        prefTheme.value = settings.theme || localStorage.getItem(THEME_KEY) || "cyan";
        prefTheme.addEventListener("change", () => {
            applyTheme(prefTheme.value);
            saveSettings({ theme: prefTheme.value });
        });
    }
    if (prefPlayer) prefPlayer.value = settings.player;
    if (prefQuality) prefQuality.value = settings.quality;
    if (prefVoice) {
        const standardVoices = ["any", "Дубляж", "LostFilm", "HDRezka", "Кубик в кубе", "NewStudio", "AlexFilm", "TVShows", "Сыендук", "Пифагор"];
        if (standardVoices.includes(settings.voice)) {
            prefVoice.value = settings.voice;
            if (prefVoiceCustom) prefVoiceCustom.style.display = "none";
        } else if (settings.voice) {
            prefVoice.value = "custom";
            if (prefVoiceCustom) {
                prefVoiceCustom.style.display = "block";
                prefVoiceCustom.value = settings.voiceCustom || settings.voice;
            }
        }
    }
    if (prefAutoSelect) prefAutoSelect.checked = settings.autoSelect;

    // Real-time setting saves
    prefPlayer?.addEventListener("change", () => {
        saveSettings({ player: prefPlayer.value });
        updatePlayButtonLabels();
    });

    prefQuality?.addEventListener("change", () => {
        saveSettings({ quality: prefQuality.value });
    });

    prefVoice?.addEventListener("change", () => {
        if (prefVoice.value === "custom") {
            if (prefVoiceCustom) {
                prefVoiceCustom.style.display = "block";
                prefVoiceCustom.focus();
            }
            saveSettings({ voice: prefVoiceCustom?.value || "custom", voiceCustom: prefVoiceCustom?.value || "" });
        } else {
            if (prefVoiceCustom) prefVoiceCustom.style.display = "none";
            saveSettings({ voice: prefVoice.value });
        }
    });

    prefVoiceCustom?.addEventListener("input", () => {
        const val = prefVoiceCustom.value.trim();
        saveSettings({ voice: val, voiceCustom: val });
    });

    prefAutoSelect?.addEventListener("change", () => {
        saveSettings({ autoSelect: prefAutoSelect.checked });
    });

    // Tab buttons
    document.querySelectorAll(".settings-tab-btn").forEach(btn => {
        btn.addEventListener("click", () => {
            switchSettingsTab(btn.getAttribute("data-stab"));
        });
    });

    // Open/Close triggers
    navSettings?.addEventListener("click", openSettingsModal);
    btnClose?.addEventListener("click", closeSettingsModal);
    btnSave?.addEventListener("click", closeSettingsModal);
    backdrop?.addEventListener("click", closeSettingsModal);

    // Settings Sources tab refresh
    document.getElementById("btn-settings-refresh-health")?.addEventListener("click", async () => {
        await refreshHealth();
        populateSettingsHealthTable();
    });

    // Settings History tab
    document.getElementById("btn-settings-clear-history")?.addEventListener("click", clearWatchHistory);
    document.getElementById("btn-clear-history-view")?.addEventListener("click", clearWatchHistory);

    // Settings Server tab
    initSettingsServer();

    // Settings Filmix tab
    initSettingsFilmix();

    // Settings Updates tab
    initSettingsUpdates();
}

let lastViewBeforeSettings = "catalog";

function openSettingsModal() {
    if (currentView && currentView !== "settings") {
        lastViewBeforeSettings = currentView;
    }
    const modal = document.getElementById("settings-modal");
    if (!modal) return;
    modal.style.display = "flex";
    populateSettingsHealthTable();
    updateSettingsHistoryCount();
    syncFilmixSettingsUI();
    setTimeout(() => {
        const activeTab = modal.querySelector(".settings-tab-btn.active") || modal.querySelector(".settings-tab-btn");
        if (activeTab) activeTab.focus();
    }, 60);
}

function closeSettingsModal() {
    const modal = document.getElementById("settings-modal");
    if (modal) modal.style.display = "none";
    switchView(lastViewBeforeSettings || "catalog");
    const navSettings = document.getElementById("nav-settings");
    if (navSettings) navSettings.focus();
}

function switchSettingsTab(tabName) {
    document.querySelectorAll(".settings-tab-btn").forEach(b => {
        b.classList.toggle("active", b.getAttribute("data-stab") === tabName);
    });
    document.querySelectorAll(".settings-tab-content").forEach(c => {
        c.style.display = (c.id === `stab-content-${tabName}`) ? "flex" : "none";
    });
}

function populateSettingsHealthTable() {
    const tbody = document.getElementById("settings-health-tbody");
    if (!tbody) return;
    fetch("/api/health").then(res => res.json()).then(summary => {
        tbody.innerHTML = summary.reports.map(r => {
            const statusClass = r.status === "OK" ? "ok" : (r.status === "DEGRADED" ? "degraded" : "broken");
            return `
                <tr>
                    <td><strong>${r.source_name.toUpperCase()}</strong></td>
                    <td><span class="status-badge ${statusClass}">${r.status}</span></td>
                    <td>${Math.round(r.latency_ms)} ms</td>
                    <td>${r.message}</td>
                </tr>
            `;
        }).join("");
    }).catch(e => {
        tbody.innerHTML = `<tr><td colspan="4" class="error-state">Ошибка загрузки: ${e.message}</td></tr>`;
    });
}

function updateSettingsHistoryCount() {
    const countEl = document.getElementById("settings-history-count");
    if (!countEl) return;
    const hist = getWatchHistory();
    const count = Object.keys(hist).length;
    countEl.textContent = `Сохранено: ${count} фильмов и сериалов`;
}

function initSettingsServer() {
    const btnSave = document.getElementById("btn-settings-server-save");
    const btnTest = document.getElementById("btn-settings-server-test");
    const input = document.getElementById("settings-server-ip");
    const statusMsg = document.getElementById("settings-server-msg");

    const saved = localStorage.getItem("showhub_server") || "https://showhub-server.onrender.com";
    if (input) input.value = saved;

    btnSave?.addEventListener("click", () => {
        if (!input) return;
        let u = input.value.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = (u.includes("onrender.com") || !u.match(/^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}/)) ? ("https://" + u) : ("http://" + u);
        }
        localStorage.setItem("showhub_server", u.replace(/\/+$/, ""));
        input.value = u.replace(/\/+$/, "");
        if (statusMsg) {
            statusMsg.textContent = "Адрес успешно сохранен!";
            statusMsg.className = "status-msg success";
        }
    });

    btnTest?.addEventListener("click", async () => {
        if (statusMsg) {
            statusMsg.textContent = "Проверка соединения...";
            statusMsg.className = "status-msg";
        }
        try {
            const res = await fetch("/api/health");
            if (res.ok) {
                statusMsg.textContent = "Связь с сервером установлена!";
                statusMsg.className = "status-msg success";
            } else {
                throw new Error("HTTP " + res.status);
            }
        } catch (e) {
            if (statusMsg) {
                statusMsg.textContent = "Сервер недоступен: " + e.message;
                statusMsg.className = "status-msg error";
            }
        }
    });
}

function initSettingsFilmix() {
    const btnLogin = document.getElementById("btn-settings-filmix-login");
    const btnLogout = document.getElementById("btn-settings-filmix-logout");
    const btnSaveCookie = document.getElementById("btn-settings-filmix-save-cookie");

    btnLogin?.addEventListener("click", async () => {
        const u = document.getElementById("settings-filmix-login")?.value.trim();
        const p = document.getElementById("settings-filmix-password")?.value.trim();
        const msg = document.getElementById("settings-filmix-msg");
        if (!u || !p) {
            if (msg) { msg.textContent = "Заполните логин и пароль"; msg.className = "status-msg error"; }
            return;
        }
        try {
            const res = await fetch("/api/account/filmix/login", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ login_name: u, login_password: p })
            });
            const d = await res.json();
            if (d.success) {
                if (msg) { msg.textContent = d.message || "Вход выполнен!"; msg.className = "status-msg success"; }
                syncFilmixSettingsUI(d.profile);
                loadFilmixAccount();
            } else {
                if (msg) { msg.textContent = d.message || "Ошибка входа"; msg.className = "status-msg error"; }
            }
        } catch (e) {
            if (msg) { msg.textContent = e.message; msg.className = "status-msg error"; }
        }
    });

    btnLogout?.addEventListener("click", async () => {
        if (!confirm("Выйти из Filmix?")) return;
        await fetch("/api/account/filmix/logout", { method: "POST" });
        loadFilmixAccount();
        syncFilmixSettingsUI();
    });

    btnSaveCookie?.addEventListener("click", async () => {
        const raw = document.getElementById("settings-filmix-cookie")?.value.trim();
        const msg = document.getElementById("settings-filmix-msg");
        if (!raw) return;
        try {
            const res = await fetch("/api/account/filmix/cookies", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ cookies: raw })
            });
            const d = await res.json();
            if (d.success) {
                if (msg) { msg.textContent = "Cookies сохранены!"; msg.className = "status-msg success"; }
                syncFilmixSettingsUI(d.profile);
                loadFilmixAccount();
            } else {
                if (msg) { msg.textContent = d.message || "Ошибка"; msg.className = "status-msg error"; }
            }
        } catch (e) {
            if (msg) { msg.textContent = e.message; msg.className = "status-msg error"; }
        }
    });
}

function syncFilmixSettingsUI(profileData) {
    const applyProfile = (profile) => {
        const nameEl = document.getElementById("settings-filmix-login-text");
        const badgeEl = document.getElementById("settings-filmix-badge");
        const proEl = document.getElementById("settings-filmix-pro");
        const btnLogout = document.getElementById("btn-settings-filmix-logout");

        if (profile && profile.is_logged_in) {
            if (nameEl) nameEl.textContent = profile.display_name || profile.username;
            if (badgeEl) {
                badgeEl.textContent = "Авторизован";
                badgeEl.style.background = "rgba(16, 185, 129, 0.2)";
                badgeEl.style.color = "var(--accent-success)";
            }
            if (proEl) {
                proEl.style.display = (profile.is_pro || profile.is_pro_plus) ? "inline-block" : "none";
                proEl.textContent = profile.is_pro_plus ? "PRO+ ULTRA" : "PRO";
            }
            if (btnLogout) btnLogout.style.display = "inline-block";
        } else {
            if (nameEl) nameEl.textContent = "Гостевой режим (Filmix)";
            if (badgeEl) {
                badgeEl.textContent = "Гость";
                badgeEl.style.background = "rgba(255, 255, 255, 0.1)";
                badgeEl.style.color = "var(--text-secondary)";
            }
            if (proEl) proEl.style.display = "none";
            if (btnLogout) btnLogout.style.display = "none";
        }
    };

    if (profileData) {
        applyProfile(profileData);
    } else {
        fetch("/api/account/filmix").then(r => r.json()).then(applyProfile).catch(() => {});
    }
}

/* =========================================================
   Updates Engine (Google Drive / Web Endpoint Check)
   ========================================================= */
let latestUpdateApkUrl = "/ShowHub.apk";

function initSettingsUpdates() {
    const btnCheck = document.getElementById("btn-check-updates");
    const btnDoUpdate = document.getElementById("btn-do-update");
    const statusText = document.getElementById("update-status-text");
    const actionContainer = document.getElementById("update-action-container");
    const inputUrl = document.getElementById("pref-update-url");

    if (inputUrl) {
        const savedUrl = localStorage.getItem("showhub_update_url") || "";
        inputUrl.value = savedUrl;
        inputUrl.addEventListener("change", () => {
            localStorage.setItem("showhub_update_url", inputUrl.value.trim());
        });
    }

    btnCheck?.addEventListener("click", async () => {
        if (statusText) {
            statusText.textContent = "Проверка наличия обновлений...";
            statusText.style.color = "var(--text-secondary)";
        }
        if (actionContainer) actionContainer.style.display = "none";

        try {
            const customUrl = inputUrl?.value.trim();
            const targetUrl = customUrl || "/api/updates/check";
            const res = await fetch(targetUrl);
            if (!res.ok) throw new Error("HTTP " + res.status);
            const data = await res.json();

            const currentVersionCode = CURRENT_APP_VERSION_CODE;
            if (data && data.version_code && data.version_code > currentVersionCode) {
                if (statusText) {
                    statusText.textContent = `Доступна новая версия: v${data.version_name || data.version}! ${data.changelog || ''}`;
                    statusText.style.color = "var(--accent-color)";
                }
                latestUpdateApkUrl = data.apk_url || data.download_url || "/ShowHub.apk";
                if (actionContainer) actionContainer.style.display = "block";
                setTimeout(() => {
                    try { btnDoUpdate?.focus(); } catch (e) {}
                }, 50);
            } else {
                if (statusText) {
                    statusText.textContent = `У вас установлена самая актуальная версия (${data.version_name || 'v' + CURRENT_APP_VERSION}). Обновлений не требуется.`;
                    statusText.style.color = "var(--accent-success)";
                }
            }
        } catch (e) {
            if (statusText) {
                statusText.textContent = `Ошибка проверки обновлений: ${e.message || 'сеть недоступна'}`;
                statusText.style.color = "var(--accent-danger)";
            }
        }
    });

    btnDoUpdate?.addEventListener("click", () => {
        if (window.AndroidBridge && typeof window.AndroidBridge.downloadAndInstall === "function") {
            window.AndroidBridge.downloadAndInstall(latestUpdateApkUrl);
        } else if (window.AndroidBridge && typeof window.AndroidBridge.installUpdate === "function") {
            window.AndroidBridge.installUpdate(latestUpdateApkUrl);
        } else if (window.AndroidBridge && typeof window.AndroidBridge.openUrl === "function") {
            window.AndroidBridge.openUrl(latestUpdateApkUrl);
        } else {
            window.open(latestUpdateApkUrl, "_blank");
        }
    });
}

/* =========================================================
   History View Implementation
   ========================================================= */
function renderHistoryView() {
    const grid = document.getElementById("history-grid");
    const countSubtitle = document.getElementById("history-count-subtitle");
    if (!grid) return;

    const hist = getWatchHistory();
    const rawItems = Object.values(hist).sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));

    // Deduplicate history items across alias keys or duplicate titles
    const seen = new Set();
    const items = [];
    for (const h of rawItems) {
        if (!h || !h.title) continue;
        const dedupKey = (h.kpId && String(h.kpId) !== "0" && String(h.kpId) !== "null")
            ? `kp_${h.kpId}`
            : `${h.source || 'media'}_${h.rawId || h.id || h.title}_${h.year || ''}`;
        if (!seen.has(dedupKey)) {
            seen.add(dedupKey);
            items.push(h);
        }
    }

    if (countSubtitle) {
        countSubtitle.textContent = `${items.length} видео`;
    }

    if (items.length === 0) {
        grid.innerHTML = `<div class="empty-state">История просмотров пуста. Начните смотреть фильм или сериал, и он появится здесь!</div>`;
        return;
    }

    const adapted = items.map(h => ({
        id: h.rawId || h.id,
        source_name: h.source || "filmix",
        kinopoisk_id: h.kpId,
        title: h.title,
        poster: h.poster,
        year: h.year,
        is_series: h.isSeries,
        rating_kp: null
    }));

    renderMediaCards(adapted, grid);
}

