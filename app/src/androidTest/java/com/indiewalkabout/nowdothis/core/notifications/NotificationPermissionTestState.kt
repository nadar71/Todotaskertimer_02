package com.indiewalkabout.nowdothis.core.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry

internal class NotificationPermissionTestState private constructor(
    private val context: Context
) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    val isGranted: Boolean
        get() = context.packageManager.checkPermission(
            Manifest.permission.POST_NOTIFICATIONS,
            context.packageName
        ) == PackageManager.PERMISSION_GRANTED

    fun force(granted: Boolean) {
        if (isGranted == granted) return

        if (granted) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            revokeWithoutKillingTestProcess()
        }
        instrumentation.waitForIdleSync()
        check(isGranted == granted) { "Unable to force POST_NOTIFICATIONS=$granted" }
    }

    fun <T> withForced(granted: Boolean, block: () -> T): T {
        val incomingState = isGranted
        return try {
            force(granted)
            block()
        } finally {
            force(incomingState)
        }
    }

    private fun revokeWithoutKillingTestProcess() {
        val uiAutomation = instrumentation.uiAutomation
        uiAutomation.adoptShellPermissionIdentity(
            REVOKE_RUNTIME_PERMISSIONS,
            REVOKE_WITHOUT_KILL_PERMISSION
        )
        try {
            // Ordinary runtime revocation kills the instrumented app; Android provides this
            // hidden test API specifically for CTS and local notification-permission tests.
            val permissionManager = checkNotNull(context.getSystemService("permission"))
            Class.forName("android.permission.PermissionManager").getDeclaredMethod(
                "revokePostNotificationPermissionWithoutKillForTest",
                String::class.java,
                Int::class.javaPrimitiveType
            ).apply { isAccessible = true }
                .invoke(permissionManager, context.packageName, Process.myUid() / PER_USER_RANGE)
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    companion object {
        private const val REVOKE_WITHOUT_KILL_PERMISSION =
            "android.permission.REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL"
        private const val REVOKE_RUNTIME_PERMISSIONS =
            "android.permission.REVOKE_RUNTIME_PERMISSIONS"
        private const val PER_USER_RANGE = 100_000

        fun create(): NotificationPermissionTestState {
            val context = InstrumentationRegistry.getInstrumentation()
                .targetContext.applicationContext
            return NotificationPermissionTestState(context)
        }
    }
}
