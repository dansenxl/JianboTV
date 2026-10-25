package tv.own.owntv.features.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.stage.stageGlass
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.stageText
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.core.update.UpdateManager
import tv.own.owntv.ui.components.OwnTVSpinner

/**
 * Small semi-transparent status card (top-right corner) for the automatic startup update check:
 * "Checking…" → "You're up to date" (auto-hides after ~2s) — or, when an update exists, it stays
 * with Update now / Later. Update now downloads with progress and hands off to the system installer.
 * Only the Available state takes D-pad focus; the transient states never interrupt browsing.
 */
@Composable
fun UpdateStatusToast(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val manager: UpdateManager = koinInject()
    val state by manager.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        manager.resumePendingInstall()
    }

    // Transient outcomes hide themselves; an available update keeps the card up.
    LaunchedEffect(state) {
        when (state) {
            UpdateManager.State.UpToDate -> { delay(2_000); manager.reset(); onDone() }
            is UpdateManager.State.Failed -> { delay(2_500); manager.reset(); onDone() }
            is UpdateManager.State.Available, is UpdateManager.State.Downloading, UpdateManager.State.InstallPermissionRequired -> runCatching { focus.requestFocus() }
            UpdateManager.State.Idle -> onDone()
            else -> Unit
        }
    }

    Column(
        modifier = modifier
            // The Stage glass card, as the top-right pills.
            .padding(top = 40.mpx, end = 40.mpx)
            .widthIn(min = 460.mpx, max = 680.mpx)
            .stageGlass(26.mpx, overContent = true)
            .padding(horizontal = 26.mpx, vertical = 20.mpx)
            .focusGroup(),
    ) {
        when (val s = state) {
            UpdateManager.State.Idle, UpdateManager.State.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                OwnTVSpinner(sizeDp = 18)
                Spacer(Modifier.width(14.mpx))
                Text(stringResource(R.string.update_checking), style = stageText(17, 500), color = StageColors.Muted)
            }
            UpdateManager.State.UpToDate -> Text(
                stringResource(R.string.update_latest, manager.currentVersion),
                style = stageText(17, 500), color = StageColors.Muted,
            )
            is UpdateManager.State.Failed -> Text(
                updateFailureText(s.failure),
                style = stageText(17, 500), color = StageColors.Muted,
            )
            is UpdateManager.State.Available -> {
                BackHandler { manager.defer(s.info.version); onDone() } // Back = Later
                Text(stringResource(R.string.update_available), style = stageText(21, 800), color = StageColors.Text)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.update_ready, s.info.version, manager.currentVersion),
                    style = stageText(17, 500), color = StageColors.Muted,
                )
                Spacer(Modifier.height(12.dp))
                if (s.info.notes.isNotBlank()) {
                    Text(s.info.notes.take(480), style = stageText(15, 400), color = StageColors.Muted, maxLines = 4)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    tv.own.owntv.ui.stage.StageButton(stringResource(R.string.update_later), onClick = { manager.defer(s.info.version); onDone() }, height = 52.mpx, textSize = 18, modifier = Modifier.focusRequester(focus))
                    tv.own.owntv.ui.stage.StageButton(stringResource(R.string.update_now), onClick = { manager.downloadAndInstall() }, height = 52.mpx, textSize = 18, tinted = true)
                }
            }
            is UpdateManager.State.Downloading -> Row(verticalAlignment = Alignment.CenterVertically) {
                OwnTVSpinner(sizeDp = 18)
                Spacer(Modifier.width(14.mpx))
                Text(stringResource(R.string.update_downloading, s.percent), style = stageText(17, 500), color = StageColors.Muted)
                Spacer(Modifier.width(14.mpx))
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.update_cancel_download), onClick = { manager.cancelDownload() }, height = 48.mpx, textSize = 16, modifier = Modifier.focusRequester(focus))
            }
            UpdateManager.State.InstallPermissionRequired -> {
                BackHandler { manager.reset(); onDone() }
                Text(stringResource(R.string.update_install_permission_title), style = stageText(18, 600), color = StageColors.Text)
                Spacer(Modifier.height(8.dp))
                tv.own.owntv.ui.stage.StageButton(stringResource(R.string.update_install_permission_action), onClick = {
                    permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                }, height = 48.mpx, textSize = 16, tinted = true, modifier = Modifier.focusRequester(focus))
            }
        }
    }
}
