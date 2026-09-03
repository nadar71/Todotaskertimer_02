package com.indiewalkabout.nowdothis.feature.ads.presentation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun AdMobBanner(adUnitId: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val widthDp = maxWidth.value.toInt().coerceAtLeast(1)
        val adSize = remember(context, widthDp) {
            AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)
        }
        val height = remember(context, density, adSize) {
            with(density) { adSize.getHeightInPixels(context).toDp() }
        }
        key(adSize, adUnitId) {
            var adView by remember { mutableStateOf<AdView?>(null) }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height.coerceAtLeast(50.dp))
                    .testTag("admob-bottom-banner"),
                factory = { viewContext ->
                    AdView(viewContext).apply {
                        setAdSize(adSize)
                        setAdUnitId(adUnitId)
                        loadAd(AdRequest.Builder().build())
                        adView = this
                    }
                }
            )

            DisposableEffect(lifecycleOwner, adView) {
                val view = adView
                if (view == null) return@DisposableEffect onDispose {}
                val observer = BannerAdLifecycleObserver(
                    pause = view::pause,
                    resume = view::resume,
                    destroy = view::destroy
                )
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    observer.dispose()
                    adView = null
                }
            }
        }
    }
}

internal class BannerAdLifecycleObserver(
    private val pause: () -> Unit,
    private val resume: () -> Unit,
    private val destroy: () -> Unit
) : DefaultLifecycleObserver {
    private var destroyed = false

    override fun onResume(owner: LifecycleOwner) = resume()

    override fun onPause(owner: LifecycleOwner) = pause()

    override fun onDestroy(owner: LifecycleOwner) = dispose()

    fun dispose() {
        if (destroyed) return
        destroyed = true
        destroy()
    }
}
