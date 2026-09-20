package com.sudantha2.youtube.ui.player

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sudantha2.youtube.core.innertube.InnerTubeApi
import com.sudantha2.youtube.data.model.QualitySelection
import com.sudantha2.youtube.data.model.StreamBundle
import com.sudantha2.youtube.data.model.WatchNextResult
import com.sudantha2.youtube.data.repo.PlayerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Concrete stream pair the player will consume. */
@Immutable
data class ResolvedSource(
    /** Progressive video-only URL, or a muxed URL when [isMuxed]. */
    val videoUrl: String,
    /** Audio companion URL; null for muxed streams. */
    val audioUrl: String?,
    val height: Int,
    val isMuxed: Boolean,
    val label: String,
)

@Stable
class PlayerViewModel(
    private val playerRepository: PlayerRepository,
    private val innerTubeApi: InnerTubeApi,
) : ViewModel() {

    sealed interface UiState {
        @Immutable
        data object Loading : UiState

        @Immutable
        data class Ready(val bundle: StreamBundle) : UiState

        @Immutable
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _selection = MutableStateFlow(QualitySelection.Auto)
    val selection: StateFlow<QualitySelection> = _selection.asStateFlow()

    private val _watchNext = MutableStateFlow<WatchNextResult?>(null)
    val watchNext: StateFlow<WatchNextResult?> = _watchNext.asStateFlow()

    private var currentVideoId: String? = null
    private var lastApplied: ResolvedSource? = null

    // ── Loading ────────────────────────────────────────────────────────────

    fun load(videoId: String) {
        if (currentVideoId == videoId && _state.value is UiState.Ready) return
        currentVideoId = videoId
        _state.value = UiState.Loading
        _selection.value = QualitySelection.Auto
        _watchNext.value = null
        lastApplied = null

        viewModelScope.launch {
            try {
                _state.value = UiState.Ready(playerRepository.streams(videoId))
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Failed to load video")
            }
        }
        // Watch-next + comments token load in parallel; failures are silent.
        viewModelScope.launch {
            runCatching { _watchNext.value = innerTubeApi.watchNext(videoId) }
        }
    }

    // ── Quality resolution ────────────────────────────────────────────────

    /**
     * Maps the user's [QualitySelection] onto concrete stream URLs.
     * Auto = highest video-only track ≤ 1080p (2160p excluded: not worth
     * the RAM/CPU on budget targets), falling back to muxed progressive.
     */
    fun resolveSource(bundle: StreamBundle, selection: QualitySelection): ResolvedSource? {
        val target = if (selection.isAuto) 1080 else selection.targetHeight

        val videoOnly = bundle.videoOnly.firstOrNull { it.height <= target }
            ?: bundle.videoOnly.minByOrNull { it.height }
        if (videoOnly != null && videoOnly.height <= target) {
            val audio = bundle.defaultAudio ?: return null
            return ResolvedSource(
                videoUrl = videoOnly.url,
                audioUrl = audio.url,
                height = videoOnly.height,
                isMuxed = false,
                label = videoOnly.label,
            )
        }

        val muxed = bundle.muxed.firstOrNull { it.height <= target }
            ?: bundle.muxed.minByOrNull { it.height }
            ?: return null
        return ResolvedSource(
            videoUrl = muxed.url,
            audioUrl = null,
            height = muxed.height,
            isMuxed = true,
            label = muxed.label,
        )
    }

    fun select(selection: QualitySelection) {
        _selection.value = selection
    }

    /** UI reports what it actually applied, so downgrade knows the ground truth. */
    fun reportApplied(source: ResolvedSource) {
        lastApplied = source
    }

    /**
     * Drop one quality tier. Order: current video-only → next lower
     * video-only → highest muxed → next lower muxed. Returns false when
     * already at the floor.
     */
    fun downgrade(): Boolean {
        val bundle = (_state.value as? UiState.Ready)?.bundle ?: return false
        val applied = lastApplied ?: return false

        if (!applied.isMuxed) {
            val next = bundle.videoOnly.firstOrNull { it.height < applied.height }
            if (next != null) {
                _selection.value = QualitySelection(next.height)
                return true
            }
            // Fall off the DASH cliff onto muxed progressive.
            val muxed = bundle.muxed.firstOrNull()
            if (muxed != null) {
                _selection.value = QualitySelection(muxed.height)
                return true
            }
            return false
        }

        val nextMuxed = bundle.muxed.firstOrNull { it.height < applied.height } ?: return false
        _selection.value = QualitySelection(nextMuxed.height)
        return true
    }

    // ── Engagement ─────────────────────────────────────────────────────────

    fun like(videoId: String) = viewModelScope.launch { innerTubeApi.like(videoId) }

    fun dislike(videoId: String) = viewModelScope.launch { innerTubeApi.dislike(videoId) }

    fun removeLike(videoId: String) = viewModelScope.launch { innerTubeApi.removeLike(videoId) }

    suspend fun postComment(videoId: String, text: String): Boolean =
        innerTubeApi.createComment(videoId, text)
}
