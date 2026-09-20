package com.sudantha2.youtube.data.repo

import com.sudantha2.youtube.core.di.ServiceLocator
import com.sudantha2.youtube.data.model.AudioStreamOption
import com.sudantha2.youtube.data.model.StreamBundle
import com.sudantha2.youtube.data.model.VideoStreamOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Stream-format extraction via NewPipeExtractor.
 *
 * Runs on [Dispatchers.IO] — NPE is blocking, parses HTML/JS and must never
 * touch the main thread. Results are mapped into immutable option lists the
 * player can consume directly.
 */
class PlayerRepository {

    suspend fun streams(videoId: String): StreamBundle = withContext(Dispatchers.IO) {
        ServiceLocator.ensureNewPipe()
        val info = StreamInfo.getInfo(
            ServiceList.YouTube,
            "https://www.youtube.com/watch?v=$videoId",
        )

        val videoOnly = info.videoOnlyStreams
            .asSequence()
            .filter { it.content.isNotBlank() }
            .mapNotNull { s ->
                val height = s.resolution?.takeWhile(Char::isDigit)?.toIntOrNull() ?: return@mapNotNull null
                VideoStreamOption(
                    url = s.content,
                    height = height,
                    label = s.resolution ?: "${height}p",
                    bitrate = s.averageBitrate,
                    isMuxed = false,
                )
            }
            .sortedByDescending { it.height }
            .toList()

        val muxed = info.muxedStreams
            .asSequence()
            .filter { it.content.isNotBlank() && it.format === MediaFormat.MPEG_4 }
            .mapNotNull { s ->
                val height = s.resolution?.takeWhile(Char::isDigit)?.toIntOrNull() ?: return@mapNotNull null
                VideoStreamOption(
                    url = s.content,
                    height = height,
                    label = s.resolution ?: "${height}p",
                    bitrate = s.averageBitrate,
                    isMuxed = true,
                )
            }
            .sortedByDescending { it.height }
            .toList()

        val audio = info.audioStreams
            .asSequence()
            .filter {
                it.content.isNotBlank() &&
                    (it.format === MediaFormat.WEBMA_OPUS || it.format === MediaFormat.M4A)
            }
            .map { s ->
                AudioStreamOption(
                    url = s.content,
                    bitrate = s.averageBitrate,
                    label = when (s.format) {
                        MediaFormat.WEBMA_OPUS -> "Opus ${s.averageBitrate}k"
                        else -> "AAC ${s.averageBitrate}k"
                    },
                )
            }
            .sortedByDescending { it.bitrate }
            .toList()

        StreamBundle(
            videoId = videoId,
            title = info.name.orEmpty(),
            author = info.uploaderName.orEmpty(),
            lengthSeconds = info.duration,
            videoOnly = videoOnly,
            muxed = muxed,
            audio = audio,
        )
    }
}
