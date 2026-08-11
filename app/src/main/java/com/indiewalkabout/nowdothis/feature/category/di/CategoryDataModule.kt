package com.indiewalkabout.nowdothis.feature.category.di

import com.indiewalkabout.nowdothis.feature.category.data.repository.OfflineCategoryRepository
import com.indiewalkabout.nowdothis.feature.category.domain.repository.CategoryRepository
import com.indiewalkabout.nowdothis.feature.category.presentation.AndroidDefaultCategoryNameResolver
import com.indiewalkabout.nowdothis.feature.category.presentation.DefaultCategoryNameResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CategoryDataModule {
    @Binds
    @Singleton
    abstract fun bindCategoryRepository(
        implementation: OfflineCategoryRepository
    ): CategoryRepository

    @Binds
    abstract fun bindDefaultCategoryNameResolver(
        implementation: AndroidDefaultCategoryNameResolver
    ): DefaultCategoryNameResolver
}
