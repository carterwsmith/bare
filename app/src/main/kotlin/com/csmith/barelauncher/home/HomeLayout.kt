package com.csmith.barelauncher.home

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val KEY_LAYOUT = "layout"

/**
 * How the user has arranged the home screen, by package name. Pinned apps are a group at
 * the top; the rest follow in the user's order, then alphabetically for any never moved.
 */
@Serializable
data class HomeLayout(
    val pinned: List<String> = emptyList(),
    /** The user's order for apps that aren't pinned. */
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
) {
    fun arrange(apps: List<LaunchableApp>): List<LaunchableApp> {
        val visible = apps.filterNot { it.packageName in hidden }
        val byPackage = visible.associateBy { it.packageName }
        val others = visible.filterNot { it.packageName in pinned }
        return pinned.mapNotNull { byPackage[it] } +
            order.mapNotNull { byPackage[it] } +
            others.filterNot { it.packageName in order }
    }

    /** Pins after anything already pinned. */
    fun pin(packageName: String) = copy(pinned = pinned - packageName + packageName, order = order - packageName)

    /** Unpinned apps land just below the pinned ones, close to where they were. */
    fun unpin(packageName: String) = copy(pinned = pinned - packageName, order = listOf(packageName) + order)

    fun hide(packageName: String) = copy(
        hidden = hidden + packageName,
        pinned = pinned - packageName,
        order = order - packageName,
    )

    fun show(packageName: String) = copy(hidden = hidden - packageName)

    /** Moves an app [offset] places within its group: pinned apps stay at the top. */
    fun move(apps: List<LaunchableApp>, packageName: String, offset: Int): HomeLayout {
        val isPinned = packageName in pinned
        val group = arrange(apps).map { it.packageName }.filter { (it in pinned) == isPinned }
        val from = group.indexOf(packageName)
        val moved = group.toMutableList().apply { add((from + offset).coerceIn(0, lastIndex), removeAt(from)) }
        return if (isPinned) copy(pinned = moved) else copy(order = moved)
    }
}

/** Keeps the [HomeLayout] across restarts. */
class HomeLayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("home-layout", Context.MODE_PRIVATE)

    private val _layout = MutableStateFlow(
        prefs.getString(KEY_LAYOUT, null)?.let { Json.decodeFromString<HomeLayout>(it) } ?: HomeLayout(),
    )
    val layout: StateFlow<HomeLayout> = _layout.asStateFlow()

    fun update(change: (HomeLayout) -> HomeLayout) {
        val layout = _layout.updateAndGet(change)
        prefs.edit { putString(KEY_LAYOUT, Json.encodeToString(layout)) }
    }
}
