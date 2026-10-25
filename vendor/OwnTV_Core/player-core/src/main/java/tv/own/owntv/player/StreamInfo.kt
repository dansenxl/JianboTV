package tv.own.owntv.player

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.core.os.ConfigurationCompat
import java.text.NumberFormat
import java.util.Locale
import tv.own.owntv.core.R

/** Stable labels and typed values for the technical stream overlay. */
enum class StreamInfoLabel { ENGINE, FORMAT, SOURCE, VIDEO, HDR, INTERLACING, SOURCE_SCAN, SOURCE_FIELD_RATE, DEINTERLACE, ACTUAL_DISPLAY_RATE, BITRATE, DECODER, AUDIO, AUDIO_OUTPUT, BUFFER, LIVE_BUFFER }

enum class StreamEngine { MPV, EXOPLAYER }

enum class StreamEngineMode { NORMAL, PREFERRED, FALLBACK, IMAGE_SUBTITLE_HANDOFF }

enum class StreamHdrMode { HDR10_PQ, HLG, SDR }

/** Whether the playback path can positively identify the incoming scan and deinterlace result. */
enum class InterlaceState { PROGRESSIVE, DEINTERLACED_BY_PLAYER, DEINTERLACED_BY_DEVICE, NOT_DEINTERLACED, INTERLACED_OUTPUT_UNKNOWN, UNKNOWN }

enum class SourceScanState { INTERLACED, PROGRESSIVE, UNKNOWN }

enum class DeinterlaceStatus { CONFIRMED_BY_PLAYER, CONFIRMED_BY_DEVICE, UNKNOWN }

enum class FrameRateKind { SOURCE, DECODED_OUTPUT, VO_ESTIMATE }

enum class DecoderKind { HARDWARE, SOFTWARE, NAMED, UNKNOWN }

enum class AudioOutputKind { PASSTHROUGH, DECODED_IN_APP, PCM }

sealed interface StreamInfoValue {
    data class Engine(val engine: StreamEngine, val mode: StreamEngineMode = StreamEngineMode.NORMAL) : StreamInfoValue
    data class Format(val name: String) : StreamInfoValue
    data class Source(val url: String) : StreamInfoValue
    data class Video(
        val codec: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        val fps: Double? = null,
        val bitDepth: Int? = null,
        val fpsKind: FrameRateKind = FrameRateKind.SOURCE,
        val sourceFps: Double? = null,
        /** Engine-level cadence sample (decoder output buffers or VO estimate), never display scan-out. */
        val outputFps: Double? = null,
        val outputFpsKind: FrameRateKind = FrameRateKind.DECODED_OUTPUT,
    ) : StreamInfoValue
    data class Hdr(val mode: StreamHdrMode) : StreamInfoValue
    data class Interlacing(val state: InterlaceState) : StreamInfoValue
    data class SourceScan(val state: SourceScanState) : StreamInfoValue
    data class SourceFieldRate(val fieldsPerSecond: Double?) : StreamInfoValue
    data class Deinterlace(val status: DeinterlaceStatus) : StreamInfoValue
    data class ActualDisplayRate(val framesPerSecond: Double?) : StreamInfoValue
    data class Bitrate(val bitsPerSecond: Long) : StreamInfoValue
    data class Decoder(
        val kind: DecoderKind,
        val name: String? = null,
        val direct: Boolean = false,
        /** True when the software decoder is followed by the GPU rendering path. */
        val gpu: Boolean = false,
        /** True only when [kind] is NAMED and the engine identified a hardware decoder. */
        val hardware: Boolean = false,
        /**
         * True only when [kind] is NAMED and the engine identified a *software* decoder. Both flags stay
         * false when the decoder's kind could not be established, so an unknown name reads as the bare
         * name rather than as a guess.
         */
        val software: Boolean = false,
    ) : StreamInfoValue
    data class Audio(
        val codec: String? = null,
        val channelCount: Int? = null,
        val sampleRateHz: Int? = null,
        val bitsPerSecond: Long? = null,
    ) : StreamInfoValue
    data class AudioOutput(
        val kind: AudioOutputKind,
        val channelCount: Int? = null,
        val multichannelAllowed: Boolean,
        val fallbackReason: String? = null,
    ) : StreamInfoValue
    data class Buffer(val bufferedMs: Long? = null, val droppedFrames: Long? = null) : StreamInfoValue
    data class LiveBuffer(
        val prerollEnabled: Boolean,
        val prerollSeconds: Double? = null,
        val depthSeconds: Double? = null,
        val readaheadSeconds: Double? = null,
        val playlistOverride: Boolean = false,
    ) : StreamInfoValue
    /** Only for genuinely unknown technical/provider text; fixed OwnTV prose must not use this. */
    data class Raw(val text: String) : StreamInfoValue
}

data class StreamInfoRow(val label: StreamInfoLabel, val value: StreamInfoValue)

/** The translated name of the row — "Video", "Decoder", "Live buffer". */
@get:StringRes
val StreamInfoLabel.titleRes: Int
    get() = when (this) {
        StreamInfoLabel.ENGINE -> R.string.player_stream_engine
        StreamInfoLabel.FORMAT -> R.string.player_stream_format
        StreamInfoLabel.SOURCE -> R.string.player_stream_source
        StreamInfoLabel.VIDEO -> R.string.player_stream_video
        StreamInfoLabel.HDR -> R.string.player_stream_hdr
        StreamInfoLabel.INTERLACING -> R.string.player_stream_interlacing
        StreamInfoLabel.SOURCE_SCAN -> R.string.player_stream_source_scan
        StreamInfoLabel.SOURCE_FIELD_RATE -> R.string.player_stream_source_field_rate
        StreamInfoLabel.DEINTERLACE -> R.string.player_stream_deinterlace
        StreamInfoLabel.ACTUAL_DISPLAY_RATE -> R.string.player_stream_actual_display_rate
        StreamInfoLabel.BITRATE -> R.string.player_stream_bitrate
        StreamInfoLabel.DECODER -> R.string.player_stream_decoder
        StreamInfoLabel.AUDIO -> R.string.player_stream_audio
        StreamInfoLabel.AUDIO_OUTPUT -> R.string.player_stream_audio_output
        StreamInfoLabel.BUFFER -> R.string.player_stream_buffer
        StreamInfoLabel.LIVE_BUFFER -> R.string.player_stream_live_buffer
    }

/**
 * The row's value as one translated line — "hevc · 3840×2160 · 50 fps · 10-bit".
 *
 * Takes [Resources] rather than being a Composable so the TV overlay and the phone's stream-info
 * sheet render identical text from one place; numbers are formatted for the reader's locale.
 */
fun StreamInfoValue.displayText(res: Resources): String {
    val locale = ConfigurationCompat.getLocales(res.configuration)[0] ?: Locale.US
    val separator = res.getString(R.string.player_metadata_separator)
    fun number(value: Double): String = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }.format(value)
    fun channels(count: Int?): String? = when (count) {
        null -> null
        1 -> res.getString(R.string.player_audio_mono)
        2 -> res.getString(R.string.player_audio_stereo)
        6 -> "5.1"
        8 -> "7.1"
        else -> res.getQuantityString(R.plurals.player_audio_channels, count, count)
    }
    return when (this) {
        is StreamInfoValue.Engine -> when (engine) {
            StreamEngine.MPV -> res.getString(R.string.settings_player_mpv)
            StreamEngine.EXOPLAYER -> when (mode) {
                StreamEngineMode.PREFERRED -> res.getString(R.string.player_stream_engine_exo_preferred)
                StreamEngineMode.FALLBACK -> res.getString(R.string.player_stream_engine_exo_fallback)
                StreamEngineMode.IMAGE_SUBTITLE_HANDOFF ->
                    res.getString(R.string.player_stream_engine_exo_image_subtitle)
                StreamEngineMode.NORMAL -> res.getString(R.string.settings_player_exoplayer)
            }
        }
        is StreamInfoValue.Format -> name
        is StreamInfoValue.Source -> url
        is StreamInfoValue.Video -> listOfNotNull(
            codec,
            if (width != null && height != null) "$width×$height" else null,
            sourceFps?.let { res.getString(R.string.player_stream_fps_source, it) }
                ?: fps?.let {
                    res.getString(
                        when (fpsKind) {
                            FrameRateKind.SOURCE -> R.string.player_stream_fps_source
                            FrameRateKind.DECODED_OUTPUT -> R.string.player_stream_fps_output
                            FrameRateKind.VO_ESTIMATE -> R.string.player_stream_fps_estimate
                        }, it,
                    )
                },
            outputFps?.let {
                res.getString(
                    when (outputFpsKind) {
                        FrameRateKind.SOURCE -> R.string.player_stream_fps_source
                        FrameRateKind.DECODED_OUTPUT -> R.string.player_stream_fps_output
                        FrameRateKind.VO_ESTIMATE -> R.string.player_stream_fps_estimate
                    }, it,
                )
            },
            bitDepth?.let { res.getString(R.string.player_stream_bit_depth, it) },
        ).joinToString(separator)
        is StreamInfoValue.Hdr -> when (mode) {
            StreamHdrMode.HDR10_PQ -> res.getString(R.string.player_stream_hdr10_pq)
            StreamHdrMode.HLG -> res.getString(R.string.player_stream_hlg)
            StreamHdrMode.SDR -> res.getString(R.string.player_stream_sdr)
        }
        is StreamInfoValue.Interlacing -> res.getString(
            when (state) {
                InterlaceState.PROGRESSIVE -> R.string.player_stream_progressive
                InterlaceState.DEINTERLACED_BY_PLAYER -> R.string.player_stream_deinterlaced_by_player
                InterlaceState.DEINTERLACED_BY_DEVICE -> R.string.player_stream_deinterlaced_by_device
                InterlaceState.NOT_DEINTERLACED -> R.string.player_stream_not_deinterlaced
                InterlaceState.INTERLACED_OUTPUT_UNKNOWN -> R.string.player_stream_interlaced_output_unknown
                InterlaceState.UNKNOWN -> R.string.player_stream_interlacing_unknown
            },
        )
        is StreamInfoValue.SourceScan -> res.getString(
            when (state) {
                SourceScanState.INTERLACED -> R.string.player_stream_scan_interlaced
                SourceScanState.PROGRESSIVE -> R.string.player_stream_scan_progressive
                SourceScanState.UNKNOWN -> R.string.player_stream_interlacing_unknown
            },
        )
        is StreamInfoValue.SourceFieldRate -> fieldsPerSecond?.let {
            res.getString(R.string.player_stream_source_field_rate_value, number(it))
        } ?: res.getString(R.string.player_stream_interlacing_unknown)
        is StreamInfoValue.Deinterlace -> res.getString(
            when (status) {
                DeinterlaceStatus.CONFIRMED_BY_PLAYER -> R.string.player_stream_deinterlaced_by_player
                DeinterlaceStatus.CONFIRMED_BY_DEVICE -> R.string.player_stream_deinterlaced_by_device
                DeinterlaceStatus.UNKNOWN -> R.string.player_stream_interlacing_unknown
            },
        )
        is StreamInfoValue.ActualDisplayRate -> framesPerSecond?.let {
            res.getString(R.string.player_stream_actual_display_rate_value, it)
        } ?: res.getString(R.string.player_stream_interlacing_unknown)
        is StreamInfoValue.Bitrate ->
            res.getString(R.string.player_stream_mbps, number(bitsPerSecond / 1_000_000.0))
        is StreamInfoValue.Decoder -> buildList {
            when (kind) {
                DecoderKind.HARDWARE -> {
                    name?.takeIf { it.isNotBlank() }?.let(::add)
                    add(res.getString(R.string.player_decoder_hardware))
                }
                DecoderKind.SOFTWARE -> {
                    name?.takeIf { it.isNotBlank() }?.let(::add)
                    add(res.getString(R.string.player_decoder_software))
                    if (gpu) add(res.getString(R.string.player_decoder_gpu))
                }
                DecoderKind.NAMED -> {
                    name?.takeIf { it.isNotBlank() }?.let(::add)
                    if (hardware) add(res.getString(R.string.player_decoder_hardware))
                    else if (software) add(res.getString(R.string.player_decoder_software))
                }
                DecoderKind.UNKNOWN -> add(res.getString(R.string.player_decoder_unknown))
            }
            if (direct) add(res.getString(R.string.player_decoder_direct))
        }.joinToString(separator)
        is StreamInfoValue.Audio -> listOfNotNull(
            codec,
            channels(channelCount),
            sampleRateHz?.let { res.getString(R.string.player_stream_khz, number(it / 1000.0)) },
            bitsPerSecond?.takeIf { it > 0 }
                ?.let { res.getString(R.string.player_stream_kbps, number(it / 1000.0)) },
        ).joinToString(separator)
        is StreamInfoValue.AudioOutput -> buildList {
            add(
                when (kind) {
                    AudioOutputKind.PASSTHROUGH -> res.getString(R.string.player_stream_audio_passthrough)
                    AudioOutputKind.DECODED_IN_APP -> res.getString(R.string.player_stream_audio_decoded_in_app)
                    AudioOutputKind.PCM -> channelCount?.let { count ->
                        res.getString(
                            R.string.player_stream_audio_pcm,
                            channels(count) ?: count.toString(),
                        )
                    } ?: res.getString(R.string.player_stream_audio_decoded_in_app)
                },
            )
            add(
                res.getString(
                    if (multichannelAllowed) R.string.player_stream_multichannel_allowed
                    else R.string.settings_surround_stereo,
                ),
            )
            fallbackReason?.let { add(res.getString(R.string.player_stream_fell_back, it)) }
        }.joinToString(separator)
        is StreamInfoValue.Buffer -> listOfNotNull(
            bufferedMs?.let { res.getString(R.string.player_stream_seconds, number(it / 1000.0)) },
            droppedFrames?.let {
                res.getQuantityString(R.plurals.player_stream_dropped_frames, it.toInt(), it)
            },
        ).joinToString(separator)
        is StreamInfoValue.LiveBuffer -> buildList {
            add(
                if (prerollEnabled) {
                    res.getString(R.string.player_stream_preroll_video, number(prerollSeconds ?: 0.0))
                } else {
                    res.getString(R.string.player_stream_preroll_off)
                },
            )
            depthSeconds?.let { add(res.getString(R.string.player_stream_depth, number(it))) }
            readaheadSeconds?.let { add(res.getString(R.string.player_stream_readahead, number(it))) }
            if (playlistOverride) add(res.getString(R.string.player_stream_playlist_override))
        }.joinToString(separator)
        is StreamInfoValue.Raw -> text
    }
}
