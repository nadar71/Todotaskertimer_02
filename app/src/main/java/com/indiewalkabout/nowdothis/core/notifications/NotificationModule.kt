package com.indiewalkabout.nowdothis.core.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderScheduler
import com.indiewalkabout.nowdothis.feature.task.domain.repository.ReminderPermissionChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {
    @Provides
    @Singleton
    fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    @Provides
    @Singleton
    fun provideNotificationManager(@ApplicationContext context: Context): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    @Provides
    @Singleton
    fun provideAlarmGateway(implementation: AndroidAlarmGateway): AlarmGateway = implementation

    @Provides
    @Singleton
    fun provideReminderScheduler(
        implementation: AlarmManagerReminderScheduler
    ): ReminderScheduler = implementation

    @Provides
    @Singleton
    fun provideReminderPermissionChecker(
        implementation: AndroidReminderPermissionChecker
    ): ReminderPermissionChecker = implementation
}
