package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExoVideoPresentationTest {
    @Test
    fun `source rate and decoder buffer sample stay distinct from display output`() {
        val rows = exoVideoInfoRows(
            codec = "H264",
            width = 1920,
            height = 1080,
            sourceFps = 25.0,
            decodedBufferRate = 25.0,
        )
        val video = rows.first { it.label == StreamInfoLabel.VIDEO }.value as StreamInfoValue.Video
        assertEquals(25.0, video.sourceFps!!, 0.0)
        assertEquals(25.0, video.outputFps!!, 0.0)
        assertEquals(FrameRateKind.DECODED_OUTPUT, video.outputFpsKind)
        assertEquals(
            SourceScanState.UNKNOWN,
            (rows.first { it.label == StreamInfoLabel.SOURCE_SCAN }.value as StreamInfoValue.SourceScan).state,
        )
        assertNull(
            (rows.first { it.label == StreamInfoLabel.SOURCE_FIELD_RATE }.value as StreamInfoValue.SourceFieldRate)
                .fieldsPerSecond,
        )
        assertEquals(
            DeinterlaceStatus.UNKNOWN,
            (rows.first { it.label == StreamInfoLabel.DEINTERLACE }.value as StreamInfoValue.Deinterlace).status,
        )
        assertNull(
            (rows.first { it.label == StreamInfoLabel.ACTUAL_DISPLAY_RATE }.value as StreamInfoValue.ActualDisplayRate)
                .framesPerSecond,
        )
    }

    @Test
    fun `invalid source rates are not reported`() {
        assertNull(validSourceFrameRate(Float.NaN))
        assertNull(validSourceFrameRate(Float.POSITIVE_INFINITY))
        assertNull(validSourceFrameRate(0f))
        assertEquals(25f, validSourceFrameRate(25f))
    }

    @Test
    fun `resolution chip reports pixel height without guessing scan type`() {
        assertEquals("1080", exoResolutionLabel(1080))
        assertEquals("720", exoResolutionLabel(720))
        assertNull(exoResolutionLabel(0))
    }
}
