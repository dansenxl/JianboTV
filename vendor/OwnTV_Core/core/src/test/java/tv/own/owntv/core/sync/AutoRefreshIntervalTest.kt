package tv.own.owntv.core.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh

class AutoRefreshIntervalTest {
    @Test
    fun twelveHourPlaylistRefreshUsesStoredStalenessThreshold() {
        val refresh = PlaylistRefresh(PlaylistAutoRefresh.HOURS_12)
        val now = 1_000_000_000L
        val lastSync = now - 12 * 60 * 60 * 1000L

        assertFalse(shouldRefreshPlaylist(refresh, lastSync + 1, now, includeStartup = false))
        assertTrue(shouldRefreshPlaylist(refresh, lastSync, now, includeStartup = false))
        assertTrue(shouldRefreshPlaylist(refresh, null, now, includeStartup = false))
    }
}
