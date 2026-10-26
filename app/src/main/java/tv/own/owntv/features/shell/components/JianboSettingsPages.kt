package tv.own.owntv.features.shell.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.BuildConfig
import tv.own.owntv.R
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.update.UpdateManager
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.SettingsViewModel
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.features.settings.StageSettingsNote
import tv.own.owntv.features.settings.StageSettingsPage
import tv.own.owntv.features.settings.VideoGroup
import tv.own.owntv.features.settings.VideoPlayerGroupRows
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageFocus
import tv.own.owntv.ui.stage.StageSurface
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

internal enum class JianboSettingsDestination { PLAYLISTS, EPG, PLAYBACK, UPDATE, ABOUT }

@Composable
internal fun JianboSettingsHub(onOpen: (JianboSettingsDestination) -> Unit) {
    val entries = listOf(
        Triple(JianboSettingsDestination.PLAYLISTS, R.string.jb_settings_playlists, R.string.jb_settings_playlists_desc),
        Triple(JianboSettingsDestination.EPG, R.string.jb_settings_epg, R.string.jb_settings_epg_desc),
        Triple(JianboSettingsDestination.PLAYBACK, R.string.jb_settings_playback, R.string.jb_settings_playback_desc),
        Triple(JianboSettingsDestination.UPDATE, R.string.jb_settings_update, R.string.jb_settings_update_desc),
        Triple(JianboSettingsDestination.ABOUT, R.string.jb_settings_about, R.string.jb_settings_about_desc),
    )
    val first = androidx.compose.runtime.remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(start = 76.mpx, end = 76.mpx, top = 128.mpx).focusGroup()) {
        Text(stringResource(R.string.jb_settings_title), style = stageText(34, 800), color = StageColors.Text)
        Text(stringResource(R.string.jb_settings_subtitle), style = stageText(17, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 6.mpx, bottom = 18.mpx))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.mpx)) {
            entries.forEachIndexed { index, (destination, title, description) ->
                StageSurface(
                    onClick = { onOpen(destination) }, radius = 17.mpx,
                    modifier = Modifier.fillMaxWidth().height(76.mpx).then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                    focusStyle = StageFocus.FX,
                    idle = Modifier.background(StageColors.ControlFill, RoundedCornerShape(17.mpx)),
                ) { focused ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 22.mpx), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.mpx)) {
                        OwnTVIcon(
                            when (destination) {
                                JianboSettingsDestination.PLAYLISTS -> OwnTVIcon.PLAYLIST
                                JianboSettingsDestination.EPG -> OwnTVIcon.EPG
                                JianboSettingsDestination.PLAYBACK -> OwnTVIcon.VIDEO
                                JianboSettingsDestination.UPDATE -> OwnTVIcon.REFRESH
                                JianboSettingsDestination.ABOUT -> OwnTVIcon.INFO
                            },
                            if (focused) StageColors.Text else StageColors.Muted, Modifier.size(24.mpx),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(title), style = stageText(20, 700), color = if (focused) stageAccent.onAccent else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(description), style = stageText(15, 400), color = if (focused) stageAccent.onAccent.copy(alpha = .82f) else StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.mpx))
                        }
                        OwnTVIcon(OwnTVIcon.CHEVRON, if (focused) stageAccent.onAccent else StageColors.Dim, Modifier.size(20.mpx))
                    }
                }
            }
        }
    }
}

@Composable
internal fun JianboPlaybackSettingsPage() {
    val scroll = rememberScrollState()
    StageSettingsPage(
        group = stringResource(R.string.jb_settings_playback), count = stringResource(R.string.jb_empty), searchQuery = stringResource(R.string.jb_empty), onSearchQuery = {},
        scroll = scroll, crumb = false, showSearch = false,
    ) {
        VideoPlayerGroupRows(VideoGroup.PLAYER, scroll)
        VideoPlayerGroupRows(VideoGroup.LIVE, scroll)
    }
}

@Composable
internal fun JianboUpdateSettingsPage() {
    val context = LocalContext.current
    val vm: SettingsViewModel = koinViewModel()
    val manager: UpdateManager = koinInject()
    val repository: SettingsRepository = koinInject()
    val enabled by vm.updateCheckOnStart.collectAsStateWithLifecycle()
    val checkedAt by repository.lastUpdateCheckAt.collectAsStateWithLifecycle(initialValue = null)
    val savedResult by manager.lastCheckResult.collectAsStateWithLifecycle(initialValue = UpdateManager.LastCheckResult.Unknown)
    val state by manager.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val persistedStatus = when (savedResult) {
        UpdateManager.LastCheckResult.Unknown -> stringResource(R.string.jb_update_waiting)
        UpdateManager.LastCheckResult.UpToDate -> stringResource(R.string.update_latest, manager.currentVersion)
        is UpdateManager.LastCheckResult.Available -> stringResource(
            R.string.update_available_version, (savedResult as UpdateManager.LastCheckResult.Available).version, manager.currentVersion,
        )
        UpdateManager.LastCheckResult.Failed -> stringResource(R.string.jb_update_failed)
    }
    val status = when (state) {
        UpdateManager.State.Idle -> persistedStatus
        UpdateManager.State.Checking -> stringResource(R.string.update_checking)
        UpdateManager.State.UpToDate -> stringResource(R.string.update_latest, manager.currentVersion)
        is UpdateManager.State.Available -> stringResource(R.string.update_available_version, (state as UpdateManager.State.Available).info.version, manager.currentVersion)
        is UpdateManager.State.Failed -> stringResource(R.string.jb_update_failed)
        is UpdateManager.State.Downloading -> stringResource(R.string.update_downloading, (state as UpdateManager.State.Downloading).percent)
        UpdateManager.State.InstallPermissionRequired -> stringResource(R.string.update_install_permission_title)
    }
    StageSettingsPage(
        group = stringResource(R.string.jb_settings_update), count = stringResource(R.string.jb_empty), searchQuery = stringResource(R.string.jb_empty), onSearchQuery = {},
        scroll = scroll, crumb = false, showSearch = false,
    ) {
        StageSettingsNote(stringResource(R.string.jb_update_version, BuildConfig.VERSION_NAME), null)
        StageSettingRow(
            icon = OwnTVIcon.CLOCK, title = stringResource(R.string.jb_update_auto), desc = stringResource(R.string.jb_update_auto_desc),
            value = SettingValue.Switch(enabled), onClick = { vm.setUpdateCheckOnStart(!enabled) },
        )
        StageSettingRow(
            icon = OwnTVIcon.REFRESH, title = stringResource(R.string.jb_update_check), desc = stringResource(R.string.jb_update_check_desc),
            value = SettingValue.Action(stringResource(R.string.jb_update_check)), onClick = { manager.check() },
        )
        StageSettingsNote(
            stringResource(R.string.jb_update_last_checked, checkedAt?.let { tv.own.owntv.ui.format.formatBestDateTime(context, stringResource(R.string.jb_update_date_format), it) } ?: stringResource(R.string.update_never_checked)),
            stringResource(R.string.jb_update_status, status),
        )
    }
}

@Composable
internal fun JianboAboutPage() {
    val context = LocalContext.current
    val projectUrl = stringResource(R.string.jb_about_url)
    val ownTvUrl = stringResource(R.string.jb_about_owntv_url)
    val coreUrl = stringResource(R.string.jb_about_core_url)
    val scroll = rememberScrollState()
    StageSettingsPage(
        group = stringResource(R.string.jb_settings_about), count = stringResource(R.string.jb_empty), searchQuery = stringResource(R.string.jb_empty), onSearchQuery = {},
        scroll = scroll, crumb = false, showSearch = false,
    ) {
        StageSettingsNote(stringResource(R.string.jb_about_version, BuildConfig.VERSION_NAME), stringResource(R.string.jb_about_summary))
        StageSettingRow(
            icon = OwnTVIcon.EXTERNAL, title = stringResource(R.string.jb_about_github), desc = projectUrl,
            value = SettingValue.Action(stringResource(R.string.jb_about_open)), onClick = { openWeb(context, projectUrl) },
        )
        StageSettingRow(
            icon = OwnTVIcon.INFO, title = stringResource(R.string.jb_about_owntv), desc = ownTvUrl,
            value = SettingValue.Action(stringResource(R.string.jb_about_open)), onClick = { openWeb(context, ownTvUrl) },
        )
        StageSettingRow(
            icon = OwnTVIcon.INFO, title = stringResource(R.string.jb_about_core), desc = coreUrl,
            value = SettingValue.Action(stringResource(R.string.jb_about_open)), onClick = { openWeb(context, coreUrl) },
        )
        StageSettingsNote(stringResource(R.string.jb_about_license), stringResource(R.string.jb_about_upstream))
    }
}

private fun openWeb(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
