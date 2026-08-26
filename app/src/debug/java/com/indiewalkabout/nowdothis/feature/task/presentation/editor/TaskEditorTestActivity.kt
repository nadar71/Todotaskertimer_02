package com.indiewalkabout.nowdothis.feature.task.presentation.editor

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class TaskEditorTestActivity : ComponentActivity() {
    private var testBody by mutableStateOf<@Composable () -> Unit>({})

    override fun attachBaseContext(newBase: Context) {
        val languageTags = TaskEditorTestLocale.languageTags
        if (languageTags == null) {
            super.attachBaseContext(newBase)
            return
        }
        val localizedConfiguration = Configuration(newBase.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(languageTags))
        }
        super.attachBaseContext(newBase.createConfigurationContext(localizedConfiguration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        setContent { testBody() }
    }

    fun setTestContent(content: @Composable () -> Unit) {
        testBody = content
    }
}

object TaskEditorTestLocale {
    @Volatile
    var languageTags: String? = null
}
