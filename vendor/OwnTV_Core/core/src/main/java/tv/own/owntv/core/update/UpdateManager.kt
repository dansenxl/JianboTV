package tv.own.owntv.core.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import tv.own.owntv.core.CoreBuildInfo
import tv.own.owntv.core.settings.SettingsRepository
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

/** Updates are accepted only from this app's public, stable GitHub Releases feed. */
class UpdateManager(
    private val context: Context,
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
) {
    data class UpdateInfo(
        val version: String,
        val versionCode: Long,
        val notes: String,
        val apkUrl: String,
        val apkName: String,
        val sha256: String,
        val size: Long,
    )

    sealed interface Failure {
        data class CheckHttp(val code: Int) : Failure
        data object NoCompatibleApk : Failure
        data object InvalidReleaseResponse : Failure
        data object CheckNetwork : Failure
        data class DownloadHttp(val code: Int) : Failure
        data object EmptyDownload : Failure
        data object DownloadNetwork : Failure
        data object Install : Failure
        data class NotEnoughSpace(val requiredBytes: Long) : Failure
        data object DamagedDownload : Failure
        data object ChecksumMismatch : Failure
        data object SignatureMismatch : Failure
        data object VersionMismatch : Failure
        data object UntrustedDownload : Failure
        data class InstallRejected(val message: String?) : Failure
    }

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: UpdateInfo) : State
        data class Downloading(val percent: Int) : State
        data object InstallPermissionRequired : State
        data class Failed(val failure: Failure, val retryInfo: UpdateInfo? = null) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private val activeCall = AtomicReference<okhttp3.Call?>(null)
    private val cancelledDownload = AtomicBoolean(false)
    private val downloadInProgress = AtomicBoolean(false)
    private var verifiedPendingApk: File? = null
    private var verifiedPendingInfo: UpdateInfo? = null
    private var installReceiverRegistered = false
    val currentVersion = CoreBuildInfo.versionName
    val currentVersionCode = CoreBuildInfo.versionCode.toLong()

    private class CheckHttpException(val code: Int) : IOException()
    private class DownloadHttpException(val code: Int) : IOException()
    private class InvalidReleaseException : IOException()
    private class NoApkException : IOException()
    private class EmptyDownloadException : IOException()
    private class NotEnoughSpaceException(val required: Long) : IOException()
    private class DamagedDownloadException : IOException()
    private class ChecksumException : IOException()
    private class SignatureException : IOException()
    private class VersionException : IOException()
    private class UntrustedException : IOException()
    private class InstallException(cause: Throwable) : IOException(cause)

    private fun failureFor(t: Throwable, checking: Boolean): Failure = when (t) {
        is CheckHttpException -> Failure.CheckHttp(t.code)
        is DownloadHttpException -> Failure.DownloadHttp(t.code)
        is InvalidReleaseException -> Failure.InvalidReleaseResponse
        is NoApkException -> Failure.NoCompatibleApk
        is EmptyDownloadException -> Failure.EmptyDownload
        is NotEnoughSpaceException -> Failure.NotEnoughSpace(t.required)
        is DamagedDownloadException -> Failure.DamagedDownload
        is ChecksumException -> Failure.ChecksumMismatch
        is SignatureException -> Failure.SignatureMismatch
        is VersionException -> Failure.VersionMismatch
        is UntrustedException -> Failure.UntrustedDownload
        is InstallException -> Failure.Install
        else -> if (checking) Failure.CheckNetwork else Failure.DownloadNetwork
    }

    /** Manual checks bypass the persisted startup deferral; automatic checks honor it. */
    fun check(automatic: Boolean = false) {
        if (_state.value is State.Checking || _state.value is State.Downloading) return
        _state.value = State.Checking
        scope.launch {
            runCatching {
                settings.recordUpdateCheck(System.currentTimeMillis())
                val request = Request.Builder()
                    .url("https://api.github.com/repos/$REPOSITORY/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", USER_AGENT)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw CheckHttpException(response.code)
                    val body = response.body.string()
                    if (body.isBlank()) throw InvalidReleaseException()
                    val json = runCatching { JSONObject(body) }.getOrElse { throw InvalidReleaseException() }
                    if (json.optBoolean("draft", true) || json.optBoolean("prerelease", true)) throw InvalidReleaseException()
                    val tag = json.optString("tag_name")
                    if (!TAG_REGEX.matches(tag)) throw InvalidReleaseException()
                    val version = tag.removePrefix("v")
                    val versionCode = VERSION_CODE_REGEX.find(json.optString("body"))?.groupValues?.get(1)?.toLongOrNull()
                        ?: throw InvalidReleaseException()
                    val assets = json.optJSONArray("assets") ?: throw InvalidReleaseException()
                    val apkName = "JianboTV-$tag-arm.apk"
                    val checksumName = "$apkName.sha256"
                    var apkAsset: JSONObject? = null
                    var checksumAsset: JSONObject? = null
                    for (index in 0 until assets.length()) {
                        val asset = assets.optJSONObject(index) ?: continue
                        when (asset.optString("name")) {
                            apkName -> apkAsset = asset
                            checksumName -> checksumAsset = asset
                        }
                    }
                    val apk = apkAsset ?: throw NoApkException()
                    if (apk.optString("state") != "uploaded") throw NoApkException()
                    val size = apk.optLong("size", -1L)
                    if (size !in 1..MAX_APK_BYTES) throw InvalidReleaseException()
                    val expectedUrl = "https://github.com/$REPOSITORY/releases/download/$tag/$apkName"
                    if (apk.optString("browser_download_url") != expectedUrl) throw UntrustedException()
                    var digest = apk.optString("digest").removePrefix("sha256:").lowercase()
                    if (!SHA256_REGEX.matches(digest)) {
                        val checksum = checksumAsset ?: throw InvalidReleaseException()
                        if (checksum.optString("state") != "uploaded" || checksum.optLong("size", -1L) !in 1..1024L) throw InvalidReleaseException()
                        val checksumUrl = "https://github.com/$REPOSITORY/releases/download/$tag/$checksumName"
                        if (checksum.optString("browser_download_url") != checksumUrl) throw UntrustedException()
                        val contents = fetchTextFollowingTrustedRedirects(checksumUrl)
                        digest = CHECKSUM_LINE_REGEX.matchEntire(contents.trim())?.groupValues?.get(1)?.lowercase()
                            ?: throw InvalidReleaseException()
                    }
                    val info = UpdateInfo(version, versionCode, json.optString("body").take(MAX_RELEASE_NOTES), expectedUrl, apkName, digest, size)
                    val deferred = settings.deferredUpdateVersion.first()
                    _state.value = if (versionCode > currentVersionCode && !(automatic && deferred == tag)) {
                        State.Available(info)
                    } else {
                        State.UpToDate
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "update check failed: ${error.javaClass.simpleName}")
                _state.value = State.Failed(failureFor(error, checking = true))
            }
        }
    }

    fun defer(version: String) {
        scope.launch { settings.setDeferredUpdateVersion("v$version") }
    }

    fun downloadAndInstall() {
        val info = (_state.value as? State.Available)?.info ?: return
        if (!downloadInProgress.compareAndSet(false, true)) return
        cancelledDownload.set(false)
        _state.value = State.Downloading(0)
        scope.launch {
            val output = File(context.filesDir, "updates/${info.apkName}")
            try {
                output.parentFile?.mkdirs()
                output.delete()
                if (output.parentFile?.usableSpace?.let { it < info.size * 2 } == true) throw NotEnoughSpaceException(info.size * 2)
                val request = Request.Builder().url(info.apkUrl).header("User-Agent", USER_AGENT).build()
                val downloaded = executeTrustedDownload(request, output, info)
                verifyApk(downloaded, info)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                    verifiedPendingApk = downloaded
                    verifiedPendingInfo = info
                    _state.value = State.InstallPermissionRequired
                } else {
                    install(downloaded)
                    _state.value = State.Available(info)
                }
            } catch (e: CancellationException) {
                output.delete()
                _state.value = State.Available(info)
            } catch (e: Throwable) {
                output.delete()
                if (cancelledDownload.get()) {
                    _state.value = State.Available(info)
                } else {
                    Log.w(TAG, "update download failed: ${e.javaClass.simpleName}")
                    _state.value = State.Failed(failureFor(e, checking = false), retryInfo = info)
                }
            } finally {
                activeCall.set(null)
                downloadInProgress.set(false)
            }
        }
    }

    fun cancelDownload() {
        cancelledDownload.set(true)
        activeCall.getAndSet(null)?.cancel()
    }

    /** Called after returning from Android's per-app "Install unknown apps" settings page. */
    fun resumePendingInstall() {
        val apk = verifiedPendingApk ?: return
        val info = verifiedPendingInfo ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) return
        scope.launch {
            runCatching { install(apk); verifiedPendingApk = null; verifiedPendingInfo = null; _state.value = State.Available(info) }
                .onFailure { _state.value = State.Failed(failureFor(it, checking = false)) }
        }
    }

    private fun executeTrustedDownload(request: Request, output: File, info: UpdateInfo): File {
        val redirectClient = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        var url = request.url
        var count = 0
        while (true) {
            if (cancelledDownload.get()) throw CancellationException("download cancelled")
            val nextRequest = request.newBuilder().url(url).build()
            val call = redirectClient.newCall(nextRequest)
            if (!activeCall.compareAndSet(null, call)) throw CancellationException("download cancelled")
            if (cancelledDownload.get()) {
                activeCall.compareAndSet(call, null)
                call.cancel()
                throw CancellationException("download cancelled")
            }
            val response = call.execute()
            if (response.code in REDIRECT_CODES) {
                val location = response.header("Location") ?: run { response.close(); throw UntrustedException() }
                response.close()
                activeCall.compareAndSet(call, null)
                if (++count > MAX_REDIRECTS) throw UntrustedException()
                val redirected = url.resolve(location) ?: throw UntrustedException()
                if (!isTrustedDownloadUrl(redirected)) throw UntrustedException()
                url = redirected
                continue
            }
            response.use {
                if (!it.isSuccessful) throw DownloadHttpException(it.code)
                val body = it.body
                val total = body.contentLength()
                if (total > 0 && total != info.size) throw DamagedDownloadException()
                if (total > MAX_APK_BYTES) throw DamagedDownloadException()
                var copied = 0L
                val digest = MessageDigest.getInstance("SHA-256")
                body.byteStream().use { input -> output.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        copied += n
                        if (copied > info.size || copied > MAX_APK_BYTES) throw DamagedDownloadException()
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        _state.value = State.Downloading((copied * 100 / info.size).toInt().coerceIn(0, 100))
                    }
                } }
                if (copied != info.size) throw DamagedDownloadException()
                if (!digest.digest().joinToString("") { "%02x".format(it) }.equals(info.sha256, true)) throw ChecksumException()
            }
            activeCall.compareAndSet(call, null)
            return output
        }
    }

    private fun fetchTextFollowingTrustedRedirects(rawUrl: String): String {
        val redirectClient = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        var url = rawUrl.toHttpUrl()
        repeat(MAX_REDIRECTS + 1) { hop ->
            val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            redirectClient.newCall(req).execute().use { response ->
                if (response.code in REDIRECT_CODES) {
                    if (hop == MAX_REDIRECTS) throw UntrustedException()
                    val resolved = response.header("Location")?.let(url::resolve) ?: throw UntrustedException()
                    if (!isTrustedDownloadUrl(resolved)) throw UntrustedException()
                    url = resolved
                } else {
                    if (!response.isSuccessful) throw DownloadHttpException(response.code)
                    val text = response.body.string()
                    if (text.length > 1024) throw InvalidReleaseException()
                    return text
                }
            }
        }
        throw UntrustedException()
    }

    private fun isTrustedDownloadUrl(url: HttpUrl): Boolean =
        url.isHttps && url.username.isEmpty() && url.password.isEmpty() &&
            url.port == 443 && url.host in TRUSTED_DOWNLOAD_HOSTS

    private fun verifyApk(apk: File, expected: UpdateInfo) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: throw DamagedDownloadException()
        if (archive.packageName != "tv.jianbo.tv") throw DamagedDownloadException()
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else @Suppress("DEPRECATION") archive.versionCode.toLong()
        if (versionCode != expected.versionCode || versionCode <= currentVersionCode) throw VersionException()
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val archiveSigners = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.signingInfo?.apkContentsSigners?.toList().orEmpty() else @Suppress("DEPRECATION") archive.signatures?.toList().orEmpty()
        val installedSigners = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) installed.signingInfo?.apkContentsSigners?.toList().orEmpty() else @Suppress("DEPRECATION") installed.signatures?.toList().orEmpty()
        fun signerHashes(signatures: List<android.content.pm.Signature>) = signatures.map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) }
        }.toSet()
        if (archiveSigners.isEmpty() || signerHashes(archiveSigners) != signerHashes(installedSigners)) throw SignatureException()
    }

    private fun install(apk: File) {
        registerInstallReceiver()
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite(SESSION_ENTRY, 0, apk.length()).use { output -> apk.inputStream().use { it.copyTo(output) }; session.fsync(output) }
            val intent = Intent(INSTALL_STATUS_ACTION).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            session.commit(PendingIntent.getBroadcast(context, 0, intent, flags).intentSender)
        }
        apk.delete()
    }

    private fun registerInstallReceiver() {
        if (installReceiverRegistered) return
        installReceiverRegistered = true
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
                    PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> Log.i(TAG, "system installer completed with status=$status")
                    else -> _state.value = State.Failed(Failure.InstallRejected(message?.takeIf(String::isNotBlank)))
                }
            }
        }, IntentFilter(INSTALL_STATUS_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun retry() {
        val failed = _state.value as? State.Failed ?: return
        if (failed.retryInfo != null && failed.failure !is Failure.CheckHttp && failed.failure !is Failure.NoCompatibleApk &&
            failed.failure !is Failure.InvalidReleaseResponse && failed.failure !is Failure.CheckNetwork) {
            _state.value = State.Available(failed.retryInfo)
            downloadAndInstall()
        } else check()
    }

    fun reset() { if (_state.value !is State.Downloading && _state.value !is State.InstallPermissionRequired) _state.value = State.Idle }

    companion object {
        private const val TAG = "UpdateManager"
        private const val USER_AGENT = "JianboTV-Android-TV"
        private const val REPOSITORY = "dansenxl/JianboTV"
        private const val SESSION_ENTRY = "jianbotv-update"
        private const val INSTALL_STATUS_ACTION = "tv.jianbo.tv.UPDATE_INSTALL_STATUS"
        private const val MAX_APK_BYTES = 150L * 1024 * 1024
        private const val MAX_RELEASE_NOTES = 16_000
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val TRUSTED_DOWNLOAD_HOSTS = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
        private val TAG_REGEX = Regex("v[0-9]+\\.[0-9]+(?:\\.[0-9]+)?")
        private val VERSION_CODE_REGEX = Regex("(?im)^versionCode:\\s*([0-9]+)\\s*$")
        private val SHA256_REGEX = Regex("[0-9a-f]{64}")
        private val CHECKSUM_LINE_REGEX = Regex("(?i)^([0-9a-f]{64})\\s+\\*?JianboTV-v[0-9.]+-arm\\.apk$")
    }
}
