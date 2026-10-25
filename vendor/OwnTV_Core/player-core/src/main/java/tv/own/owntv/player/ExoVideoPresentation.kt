package tv.own.owntv.player

import tv.own.owntv.core.R

/**
 * Media3 exposes the declared input frame rate and decoder/render counters, but neither reports
 * whether the source is interlaced nor the final cadence scanned by the display. Keep those values
 * separate so a 25 Hz input/counter sample cannot be presented as measured 50p output.
 */
internal fun exoVideoInfoRows(
    codec: String?,
    width: Int?,
    height: Int?,
    sourceFps: Double?,
    decodedBufferRate: Double?,
    bitDepth: Int? = null,
): List<StreamInfoRow> = buildList {
    add(
        StreamInfoRow(
            StreamInfoLabel.VIDEO,
            StreamInfoValue.Video(
                codec = codec,
                width = width,
                height = height,
                fps = sourceFps,
                fpsKind = FrameRateKind.SOURCE,
                sourceFps = sourceFps,
                outputFps = decodedBufferRate,
                outputFpsKind = FrameRateKind.DECODED_OUTPUT,
                bitDepth = bitDepth,
            ),
        ),
    )
    add(StreamInfoRow(StreamInfoLabel.SOURCE_SCAN, StreamInfoValue.SourceScan(SourceScanState.UNKNOWN)))
    add(StreamInfoRow(StreamInfoLabel.SOURCE_FIELD_RATE, StreamInfoValue.SourceFieldRate(null)))
    add(StreamInfoRow(StreamInfoLabel.DEINTERLACE, StreamInfoValue.Deinterlace(DeinterlaceStatus.UNKNOWN)))
    add(StreamInfoRow(StreamInfoLabel.ACTUAL_DISPLAY_RATE, StreamInfoValue.ActualDisplayRate(null)))
}

/** Resolution class without a scan suffix: ExoPlayer cannot establish progressive vs interlaced. */
internal fun exoResolutionLabel(height: Int): String? =
    height.takeIf { it > 0 }?.toString()

/** Source FPS only. Decoder-buffer rates remain available in the details panel, never as a source chip. */
internal fun validSourceFrameRate(frameRate: Float): Float? = frameRate.takeIf { it.isFinite() && it > 0f }

internal fun sourceFrameRateChip(context: android.content.Context, frameRate: Float): String? =
    validSourceFrameRate(frameRate)?.let {
        context.getString(R.string.player_stream_fps_source_chip, Math.round(it))
    }
