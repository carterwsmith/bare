package com.csmith.barelauncher.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class LaunchableApp(
    val label: String,
    val component: ComponentName,
    /** The launcher category the app was found under: TV or phone. */
    val launchCategory: String,
    /** 16:9 TV artwork. Sideloaded phone apps usually only have an icon. */
    val banner: ImageBitmap?,
    val icon: ImageBitmap,
) {
    val packageName: String
        get() = component.packageName

    /**
     * The same intent the stock launchers send. If it matched the one that started the
     * app's existing task, Android brings that task back as it was, mid-episode included,
     * instead of stacking a fresh copy of the app's home screen on top.
     */
    val launchIntent: Intent
        get() = Intent(Intent.ACTION_MAIN)
            .addCategory(launchCategory)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

/** Every app with a TV or phone launcher entry (TV entry preferred), sorted by name. */
fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val packageManager = context.packageManager
    return listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        .flatMap { category ->
            packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
                .map { category to it.activityInfo }
        }
        .distinctBy { (_, activity) -> activity.packageName }
        .filter { (_, activity) -> activity.packageName != context.packageName }
        .map { (category, activity) ->
            LaunchableApp(
                label = activity.loadLabel(packageManager).toString(),
                component = ComponentName(activity.packageName, activity.name),
                launchCategory = category,
                banner = activity.loadBanner(packageManager)?.toBitmap()?.asImageBitmap(),
                icon = activity.loadIcon(packageManager).toBitmap().asImageBitmap(),
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}
