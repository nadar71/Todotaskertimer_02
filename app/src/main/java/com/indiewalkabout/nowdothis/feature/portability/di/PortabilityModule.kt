package com.indiewalkabout.nowdothis.feature.portability.di

import com.indiewalkabout.nowdothis.feature.portability.data.local.PlanningDataSource
import com.indiewalkabout.nowdothis.feature.portability.data.local.PlanningDataStore
import com.indiewalkabout.nowdothis.feature.portability.data.repository.AndroidDocumentGateway
import com.indiewalkabout.nowdothis.feature.portability.data.repository.DocumentGateway
import com.indiewalkabout.nowdothis.feature.portability.data.repository.OfflinePortabilityRepository
import com.indiewalkabout.nowdothis.feature.portability.data.serialization.BackupCodec
import com.indiewalkabout.nowdothis.feature.portability.data.serialization.BackupValidator
import com.indiewalkabout.nowdothis.feature.portability.domain.repository.PortabilityRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PortabilityBindingsModule {
    @Binds
    @Singleton
    abstract fun bindPlanningDataStore(implementation: PlanningDataSource): PlanningDataStore

    @Binds
    @Singleton
    abstract fun bindDocumentGateway(implementation: AndroidDocumentGateway): DocumentGateway

    @Binds
    @Singleton
    abstract fun bindPortabilityRepository(
        implementation: OfflinePortabilityRepository
    ): PortabilityRepository
}

@Module
@InstallIn(SingletonComponent::class)
object PortabilityModule {
    @Provides
    @Singleton
    fun providePlanningDataSource(
        database: com.indiewalkabout.nowdothis.core.database.AppDatabase
    ): PlanningDataSource = PlanningDataSource(database)

    @Provides
    @Singleton
    fun provideBackupCodec(): BackupCodec = BackupCodec()

    @Provides
    @Singleton
    fun provideBackupValidator(): BackupValidator = BackupValidator()

}
