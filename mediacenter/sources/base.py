"""
Base Source Adapter and Data Models.
Defines standard interfaces for all aggregators, scrapers, and balancers.
"""
from abc import ABC, abstractmethod
from typing import List, Dict, Optional, Any
from pydantic import BaseModel, Field

class VideoStream(BaseModel):
    quality: str = Field(..., description="e.g. 360p, 720p, 1080p, 2160p")
    url: str = Field(..., description="Direct .m3u8, .mpd, or .mp4 stream URL")
    stream_type: str = Field("hls", description="'hls', 'mp4', 'dash', 'iframe'")
    headers: Dict[str, str] = Field(default_factory=dict, description="Required HTTP headers (Referer, User-Agent)")
    is_premium: bool = Field(False, description="Requires VIP/PRO subscription or is voidboost teaser")

class AudioTrack(BaseModel):
    id: str
    name: str  # e.g. "Дубляж", "Кубик в Кубе", "LostFilm"
    is_default: bool = False

class SubtitleTrack(BaseModel):
    language: str
    url: str

class MediaItem(BaseModel):
    id: str
    source_name: str
    title: str
    original_title: Optional[str] = None
    year: Optional[int] = None
    is_series: bool = False
    poster: Optional[str] = None
    description: Optional[str] = None
    rating_kp: Optional[float] = None
    rating_imdb: Optional[float] = None
    vote_num_kp: Optional[int] = None
    vote_num_imdb: Optional[int] = None
    kinopoisk_id: Optional[str] = None
    date_added: Optional[int] = None
    episodes_info: Optional[str] = None
    extra_data: Dict[str, Any] = Field(default_factory=dict)

class EpisodeItem(BaseModel):
    episode_id: int
    title: str
    season_id: int

class SeasonItem(BaseModel):
    season_id: int
    title: str
    episodes: List[EpisodeItem] = Field(default_factory=list)

class CommentItem(BaseModel):
    author: str
    date: str
    text: str
    rating: Optional[str] = None

class MediaDetails(BaseModel):
    media_id: str
    source_name: str
    title: str
    original_title: Optional[str] = None
    year: Optional[int] = None
    is_series: bool = False
    poster: Optional[str] = None
    description: Optional[str] = None
    rating_kp: Optional[float] = None
    rating_imdb: Optional[float] = None
    vote_num_kp: Optional[int] = None
    vote_num_imdb: Optional[int] = None
    kinopoisk_id: Optional[str] = None
    genres: List[str] = Field(default_factory=list)
    director: Optional[str] = None
    actors: Optional[str] = None
    country: Optional[str] = None
    translators: List[AudioTrack] = Field(default_factory=list)
    seasons: List[SeasonItem] = Field(default_factory=list)
    comments: List[CommentItem] = Field(default_factory=list)
    extra_data: Dict[str, Any] = Field(default_factory=dict)

class StreamResult(BaseModel):
    source_name: str
    media_id: str
    title: str
    streams: List[VideoStream] = Field(default_factory=list)
    audio_tracks: List[AudioTrack] = Field(default_factory=list)
    subtitles: List[SubtitleTrack] = Field(default_factory=list)
    seasons: List[SeasonItem] = Field(default_factory=list)
    embed_url: Optional[str] = None
    error: Optional[str] = None
    skip_time: Optional[Dict[str, float]] = None

class CanaryReport(BaseModel):
    source_name: str
    is_active: bool
    status: str  # "OK", "DEGRADED", "CHANGED / BROKEN"
    latency_ms: float
    message: str
    needs_rework: bool = False
    endpoint_tested: str
    last_tested: float

class BaseSource(ABC):
    name: str = "base"
    display_name: str = "Base Source"
    source_type: str = "balancer"  # "balancer", "portal", "torrent"

    @abstractmethod
    def search(self, query: str, year: Optional[int] = None, kp_id: Optional[str] = None) -> List[MediaItem]:
        """Search media in source catalog."""
        pass

    @abstractmethod
    def get_streams(self, media_id: str, season: Optional[int] = None, episode: Optional[int] = None, audio_id: Optional[str] = None) -> StreamResult:
        """Resolve playable streams."""
        pass

    @abstractmethod
    def canary_test(self) -> CanaryReport:
        """Run health and schema validation check to alert if source changed."""
        pass
