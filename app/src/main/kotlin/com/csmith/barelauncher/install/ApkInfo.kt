package com.csmith.barelauncher.install

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.IOException

class InvalidApkException : IOException("The download is not a valid APK.")

data class ApkInfo(val packageName: String, val label: String, val icon: Bitmap, val isUpdate: Boolean)

fun readApkInfo(context: Context, apk: File): ApkInfo {
    val pm = context.packageManager
    val info = pm.getPackageArchiveInfo(apk.path, 0) ?: throw InvalidApkException()
    val app = info.applicationInfo ?: throw InvalidApkException()
    app.sourceDir = apk.path
    app.publicSourceDir = apk.path
    val isUpdate = try {
        pm.getPackageInfo(info.packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
    return ApkInfo(info.packageName, app.loadLabel(pm).toString(), app.loadIcon(pm).toBitmap(), isUpdate)
}
