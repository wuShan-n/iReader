package com.ireader.feature.reader.ui

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.ireader.core.common.android.surface.DefaultFragmentRenderSurface
import com.ireader.reader.runtime.ReaderHandle
import kotlinx.coroutines.launch

@Composable
internal fun ReaderSurface(
    handle: ReaderHandle,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    if (activity == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(text = "当前 Activity 不支持 EPUB 导航器渲染")
        }
        return
    }

    val fragmentManager = activity.supportFragmentManager
    val containerId = remember(handle) { View.generateViewId() }
    val surface = remember(fragmentManager, containerId) {
        DefaultFragmentRenderSurface(
            fragmentManager = fragmentManager,
            containerViewId = containerId
        )
    }
    val scope = rememberCoroutineScope()

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            FragmentContainerView(viewContext).apply {
                id = containerId
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }
    )

    LaunchedEffect(handle, surface) {
        handle.bindSurface(surface)
    }

    DisposableEffect(handle, surface) {
        onDispose {
            scope.launch {
                handle.unbindSurface()
            }
        }
    }
}
