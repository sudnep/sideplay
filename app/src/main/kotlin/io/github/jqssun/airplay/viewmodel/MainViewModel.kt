package io.github.jqssun.airplay.viewmodel

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.ServerName
import io.github.jqssun.airplay.audio.TrackInfo
import io.github.jqssun.airplay.service.AirPlayService
import io.github.jqssun.airplay.service.AirPlayService.ServerState
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jqssun.airplay.ui.tv.LanAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
    private val logFile = File(app.filesDir, "airplay_logs.txt")
    private var service: AirPlayService? = null

    private val _serverState = MutableStateFlow(ServerState.STOPPED)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val _connectionCount = MutableStateFlow(0)
    val connectionCount: StateFlow<Int> = _connectionCount.asStateFlow()

    private val _pinCode = MutableStateFlow<String?>(null)
    val pinCode: StateFlow<String?> = _pinCode.asStateFlow()

    private val _videoAspect = MutableStateFlow(16f / 9f)
    val videoAspect: StateFlow<Float> = _videoAspect.asStateFlow()

    private val _serverName = MutableStateFlow(prefs.getString(Prefs.SERVER_NAME, null) ?: ServerName.default(app))
    val serverName: StateFlow<String> = _serverName.asStateFlow()

    private val _firstRunDone = MutableStateFlow(prefs.getBoolean(Prefs.FIRST_RUN_DONE, Prefs.DEF_FIRST_RUN_DONE))
    val firstRunDone: StateFlow<Boolean> = _firstRunDone.asStateFlow()

    private val _lanAddress = MutableStateFlow<String?>(null)
    val lanAddress: StateFlow<String?> = _lanAddress.asStateFlow()

    init {
        // interface enumeration is too heavy for the 200 ms service poll
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                _lanAddress.value = LanAddress.current()
                delay(LAN_ADDRESS_REFRESH_MS)
            }
        }
    }

    fun completeFirstRun(name: String, startAtBoot: Boolean) {
        setBootAutoStart(startAtBoot)
        if (name != _serverName.value) setServerName(name)
        _firstRunDone.value = true
        prefs.edit().putBoolean(Prefs.FIRST_RUN_DONE, true).apply()
    }

    // uxplay has no per-client disconnect; a restart drops every connection
    fun disconnectAll() {
        val svc = service ?: return
        if (svc.serverState.value == ServerState.RUNNING) {
            svc.stopServer()
            svc.startServer(_serverName.value)
        }
    }

    // settings shown on tv; the service reads every other pref directly
    private val _serverPort = MutableStateFlow(prefs.getInt(Prefs.SERVER_PORT, Prefs.DEF_SERVER_PORT))
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _autoStart = MutableStateFlow(prefs.getBoolean(Prefs.AUTO_START, Prefs.DEF_AUTO_START))
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    private val _bootAutoStart = MutableStateFlow(prefs.getBoolean(Prefs.BOOT_AUTO_START, Prefs.DEF_BOOT_AUTO_START))
    val bootAutoStart: StateFlow<Boolean> = _bootAutoStart.asStateFlow()

    private val _runInBackground = MutableStateFlow(prefs.getBoolean(Prefs.RUN_IN_BACKGROUND, Prefs.DEF_RUN_IN_BACKGROUND))
    val runInBackground: StateFlow<Boolean> = _runInBackground.asStateFlow()

    private val _h265Enabled = MutableStateFlow(prefs.getBoolean(Prefs.H265_ENABLED, Prefs.DEF_H265_ENABLED))
    val h265Enabled: StateFlow<Boolean> = _h265Enabled.asStateFlow()

    private val _requirePin = MutableStateFlow(prefs.getBoolean(Prefs.REQUIRE_PIN, Prefs.DEF_REQUIRE_PIN))
    val requirePin: StateFlow<Boolean> = _requirePin.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(Prefs.KEEP_SCREEN_ON, Prefs.DEF_KEEP_SCREEN_ON))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    // audio mode
    private val _audioOnly = MutableStateFlow(false)
    val audioOnly: StateFlow<Boolean> = _audioOnly.asStateFlow()

    private val _videoPlaybackActive = MutableStateFlow(false)
    val videoPlaybackActive: StateFlow<Boolean> = _videoPlaybackActive.asStateFlow()

    // video channel polling but /play not received yet
    private val _videoSessionPending = MutableStateFlow(false)
    val videoSessionPending: StateFlow<Boolean> = _videoSessionPending.asStateFlow()

    private val _videoPositionMs = MutableStateFlow(0L)
    val videoPositionMs: StateFlow<Long> = _videoPositionMs.asStateFlow()

    private val _videoDurationMs = MutableStateFlow(0L)
    val videoDurationMs: StateFlow<Long> = _videoDurationMs.asStateFlow()

    private val _videoPlaybackAspect = MutableStateFlow(16f / 9f)
    val videoPlaybackAspect: StateFlow<Float> = _videoPlaybackAspect.asStateFlow()

    private val _videoBuffering = MutableStateFlow(false)
    val videoBuffering: StateFlow<Boolean> = _videoBuffering.asStateFlow()

    private val _videoTitle = MutableStateFlow("")
    val videoTitle: StateFlow<String> = _videoTitle.asStateFlow()

    // optimistic for instant icon feedback, re-synced from the delayed snapshot
    private val _videoPlaying = MutableStateFlow(true)
    val videoPlaying: StateFlow<Boolean> = _videoPlaying.asStateFlow()
    private var _videoActionAt = 0L
    private var _lastVideoPlaySeq = 0L

    private val _mirroringActive = MutableStateFlow(false)
    val mirroringActive: StateFlow<Boolean> = _mirroringActive.asStateFlow()

    private val _trackInfo = MutableStateFlow(TrackInfo())
    val trackInfo: StateFlow<TrackInfo> = _trackInfo.asStateFlow()

    // logs
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()
    private val _logLock = Any()
    private val _logList = mutableListOf<String>()
    private val _dateFmt = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    init {
        loadPersistedLogs()
    }

    fun addLog(msg: String) {
        val line = "${_dateFmt.get()!!.format(Date())} $msg"
        val snapshot: List<String>
        synchronized(_logLock) {
            _logList.add(line)
            while (_logList.size > 9999) _logList.removeAt(0)
            persistLogsLocked()
            snapshot = _logList.toList()
        }
        _logs.value = snapshot
    }

    private fun loadPersistedLogs() {
        val snapshot: List<String>
        synchronized(_logLock) {
            val restored = runCatching {
                if (logFile.exists()) logFile.readLines().takeLast(9999) else emptyList()
            }.getOrDefault(emptyList())
            _logList.clear()
            _logList.addAll(restored)
            snapshot = _logList.toList()
        }
        _logs.value = snapshot
    }

    private fun persistLogsLocked() {
        runCatching {
            logFile.writeText(_logList.joinToString("\n"))
        }
    }

    // settings setters
    private var _restartJob: Job? = null

    private fun _applyByServerRestart() {
        _restartJob?.cancel()
        _restartJob = viewModelScope.launch {
            delay(SERVER_RESTART_DEBOUNCE_MS)
            val svc = service ?: return@launch
            if (svc.serverState.value == ServerState.RUNNING) {
                svc.stopServer()
                svc.startServer(_serverName.value)
            }
        }
    }

    fun setServerName(name: String) { _serverName.value = name; prefs.edit().putString(Prefs.SERVER_NAME, name).apply(); _applyByServerRestart() }
    fun setBootAutoStart(v: Boolean) { _bootAutoStart.value = v; prefs.edit().putBoolean(Prefs.BOOT_AUTO_START, v).apply() }
    fun setH265Enabled(v: Boolean) { _h265Enabled.value = v; prefs.edit().putBoolean(Prefs.H265_ENABLED, v).apply(); _applyByServerRestart() }
    fun setRequirePin(v: Boolean) { _requirePin.value = v; prefs.edit().putBoolean(Prefs.REQUIRE_PIN, v).apply(); _applyByServerRestart() }

    // service binding
    fun bindService(svc: AirPlayService) {
        service = svc
        updateFromService()
    }

    fun unbindService() {
        service = null
    }

    fun startServer() {
        service?.startServer(_serverName.value)
    }

    fun stopServer() {
        service?.stopServer()
    }

    fun onSurfaceAvailable(surface: Surface) {
        service?.setVideoSurface(surface)
    }

    fun onSurfaceDestroyed(surface: Surface) {
        service?.clearVideoSurface(surface)
    }

    fun onVideoPlaybackSurfaceAvailable(surface: Surface) {
        service?.setVideoPlaybackSurface(surface)
    }

    fun onVideoPlaybackSurfaceDestroyed(surface: Surface) {
        service?.clearVideoPlaybackSurface(surface)
    }

    fun toggleVideoPlayPause() = setVideoPlaying(!_videoPlaying.value)

    fun setVideoPlaying(playing: Boolean) {
        // pending session: there is no local media yet, nothing to toggle
        if (!_videoPlaybackActive.value) return
        _videoPlaying.value = playing
        _videoActionAt = SystemClock.elapsedRealtime()
        service?.setVideoPlaying(playing)
    }

    fun stopVideoPlayback() {
        service?.stopVideoPlayback()
    }

    fun seekVideoBy(deltaMs: Long) {
        service?.seekVideoBy(deltaMs)
    }

    fun dismissPin() {
        _pinCode.value = null
        service?.acknowledgePin()
    }

    fun showPin(pin: String?) {
        _pinCode.value = pin
    }

    fun updateFromService() {
        service?.let {
            _serverState.value = it.serverState.value
            _connectionCount.value = it.connectionCount.value
            _videoAspect.value = it.videoAspect.value
            _audioOnly.value = it.audioOnly.value
            val videoActive = it.videoPlaybackActive.value
            val playSeq = it.videoPlaySeq.value
            if (playSeq != _lastVideoPlaySeq) {
                // fresh /play (incl. back-to-back): drop stale play state
                _lastVideoPlaySeq = playSeq
                _videoPlaying.value = true
                _videoActionAt = 0
            }
            _videoPlaybackActive.value = videoActive
            _videoSessionPending.value = it.videoSessionPending()
            if (videoActive) {
                val info = it.videoPlaybackInfo.value
                _videoPositionMs.value = info.positionMs
                _videoDurationMs.value = info.durationMs
                _videoBuffering.value = info.buffering
                _videoPlaybackAspect.value = it.videoPlaybackAspect.value
                // stream metadata first, dmap second; senders rarely provide either for video
                _videoTitle.value = it.videoTitle.value.ifEmpty { it.trackInfo.value.title }
                // stale snapshots must not overwrite a just-made optimistic action
                if (SystemClock.elapsedRealtime() - _videoActionAt > VIDEO_ACTION_SYNC_HOLD_MS) {
                    _videoPlaying.value = info.playing
                }
            } else {
                _videoPositionMs.value = 0
                _videoDurationMs.value = 0
                _videoBuffering.value = false
            }
            _mirroringActive.value = it.mirroringActive.value
            _trackInfo.value = it.trackInfo.value
        }
    }

    private companion object {
        const val LAN_ADDRESS_REFRESH_MS = 10_000L
        // raise if the play/pause icon flickers back right after a local toggle
        const val VIDEO_ACTION_SYNC_HOLD_MS = 700L
        const val SERVER_RESTART_DEBOUNCE_MS = 500L
    }
}
