package com.csmith.barelauncher.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.darkColorScheme
import com.csmith.barelauncher.R

@Composable
fun BareTheme(content: @Composable () -> Unit) {
    val background = colorResource(R.color.background)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = colorResource(R.color.accent),
            background = background,
            surface = background,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
