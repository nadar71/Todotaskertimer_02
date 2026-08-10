package com.indiewalkabout.nowdothis.feature.category.domain.repository

import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryMutationResult
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun create(name: String, color: CategoryColor): CategoryMutationResult
    suspend fun rename(id: Int, name: String): CategoryMutationResult
    suspend fun recolor(id: Int, color: CategoryColor): CategoryMutationResult
    suspend fun reorder(orderedIds: List<Int>): CategoryMutationResult
    suspend fun delete(id: Int): CategoryMutationResult
}
