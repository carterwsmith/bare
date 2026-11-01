package com.csmith.barelauncher.install

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl

/** Shared by the home screen, TV downloads, installer callbacks, and ADB progress reports. */
class InstallStore {
    private val _tasks = MutableStateFlow<List<InstallTask>>(emptyList())
    val tasks = _tasks.asStateFlow()

    fun begin(id: String, url: HttpUrl, origin: InstallOrigin) {
        val title = url.pathSegments.lastOrNull { it.isNotBlank() } ?: url.host
        _tasks.update { tasks -> tasks + InstallTask(id, title, origin) }
    }

    fun update(id: String, transform: (InstallTask) -> InstallTask) {
        _tasks.update { tasks -> tasks.map { if (it.id == id) transform(it) else it } }
    }

    fun progress(id: String, fraction: Float?) = update(id) {
        it.copy(fraction = fraction?.let { value -> (value.coerceIn(0f, 1f) * 100).toInt() / 100f })
    }

    fun fail(id: String, error: InstallError, detail: String? = null) = update(id) {
        it.copy(phase = InstallPhase.Failed, error = error, detail = detail)
    }

    fun remove(id: String) {
        _tasks.update { tasks -> tasks.filterNot { it.id == id } }
    }

    fun reportCli(id: String, url: HttpUrl, phase: InstallPhase, percent: Int?, error: InstallError?) {
        _tasks.update { tasks ->
            val existing = tasks.find { it.id == id }
            val task = (existing ?: InstallTask(
                id = id,
                title = url.pathSegments.lastOrNull { it.isNotBlank() } ?: url.host,
                origin = InstallOrigin.Cli,
            )).copy(phase = phase, fraction = percent?.div(100f), error = error)
            if (existing == null) tasks + task else tasks.map { if (it.id == id) task else it }
        }
    }
}
