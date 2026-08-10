package com.indiewalkabout.nowdothis.core.di

import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.core.time.ZoneIdProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    @Singleton
    fun provideAppClock(): AppClock = AppClock { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun provideZoneIdProvider(): ZoneIdProvider = ZoneIdProvider { ZoneId.systemDefault() }
}
