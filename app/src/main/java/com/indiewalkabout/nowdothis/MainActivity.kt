package com.indiewalkabout.nowdothis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.indiewalkabout.nowdothis.navigation.SetupNavigation
import com.indiewalkabout.nowdothis.ui.theme.ToDoComposeTheme
import com.indiewalkabout.nowdothis.ui.viewmodels.SharedViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
@ExperimentalAnimationApi
class MainActivity : ComponentActivity() {

    private val sharedViewModel: SharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
//        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ToDoComposeTheme {
                SetupNavigation(
                    sharedViewModel = sharedViewModel
                )
            }
        }
    }
}
