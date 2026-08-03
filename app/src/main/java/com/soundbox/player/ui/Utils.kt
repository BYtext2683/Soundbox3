package com.soundbox.player.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.soundbox.player.App

/** 在 Compose 里拿到手写依赖容器。 */
@Composable
fun app(): App = LocalContext.current.applicationContext as App
