package com.indiewalkabout.nowdothis.feature.category.data.repository

import androidx.room.withTransaction
import com.indiewalkabout.nowdothis.core.database.AppDatabase
import com.indiewalkabout.nowdothis.core.time.AppClock
import com.indiewalkabout.nowdothis.feature.category.data.local.CategoryDao
import com.indiewalkabout.nowdothis.feature.category.data.local.CategoryEntity
import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryError
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryMutationResult
import com.indiewalkabout.nowdothis.feature.category.domain.model.DefaultCategoryKey
import com.indiewalkabout.nowdothis.feature.category.domain.repository.CategoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class OfflineCategoryRepository @Inject constructor(
    private val database: AppDatabase,
    private val categoryDao: CategoryDao,
    private val clock: AppClock
) : CategoryRepository {
    override fun observeAll(): Flow<List<Category>> = categoryDao.observeAll()
        .map { categories -> categories.map { it.toDomain() } }

    override suspend fun create(
        name: String,
        color: CategoryColor
    ): CategoryMutationResult = withValidName(name) { trimmedName ->
        database.withTransaction {
            val categories = categoryDao.observeAll().first()
            if (categories.hasName(trimmedName)) {
                CategoryMutationResult.Failure(CategoryError.DuplicateName)
            } else {
                categoryDao.insert(
                    CategoryEntity(
                        customName = trimmedName,
                        colorToken = color.name,
                        position = categories.maxOfOrNull(CategoryEntity::position)?.plus(1) ?: 0,
                        createdAt = clock.nowMillis()
                    )
                )
                CategoryMutationResult.Success
            }
        }
    }

    override suspend fun rename(id: Int, name: String): CategoryMutationResult =
        withValidName(name) { trimmedName ->
            database.withTransaction {
                val categories = categoryDao.observeAll().first()
                val existing = categories.firstOrNull { it.id == id }
                    ?: return@withTransaction CategoryMutationResult.Failure(CategoryError.NotFound)
                if (categories.hasName(trimmedName, excludingId = id)) {
                    CategoryMutationResult.Failure(CategoryError.DuplicateName)
                } else {
                    categoryDao.update(existing.copy(customName = trimmedName, defaultKey = null))
                    CategoryMutationResult.Success
                }
            }
        }

    override suspend fun recolor(
        id: Int,
        color: CategoryColor
    ): CategoryMutationResult = database.withTransaction {
        val existing = categoryDao.getById(id)
            ?: return@withTransaction CategoryMutationResult.Failure(CategoryError.NotFound)
        categoryDao.update(existing.copy(colorToken = color.name))
        CategoryMutationResult.Success
    }

    override suspend fun reorder(orderedIds: List<Int>): CategoryMutationResult =
        database.withTransaction {
            val categories = categoryDao.observeAll().first()
            if (orderedIds.size != categories.size || orderedIds.toSet() != categories.map { it.id }.toSet()) {
                return@withTransaction CategoryMutationResult.Failure(CategoryError.InvalidOrder)
            }
            val byId = categories.associateBy(CategoryEntity::id)
            orderedIds.forEachIndexed { position, id ->
                categoryDao.update(requireNotNull(byId[id]).copy(position = position))
            }
            CategoryMutationResult.Success
        }

    override suspend fun delete(id: Int): CategoryMutationResult = database.withTransaction {
        if (categoryDao.getById(id) == null) {
            CategoryMutationResult.Failure(CategoryError.NotFound)
        } else {
            categoryDao.deleteById(id)
            CategoryMutationResult.Success
        }
    }

    private suspend fun withValidName(
        name: String,
        mutation: suspend (String) -> CategoryMutationResult
    ): CategoryMutationResult {
        val trimmedName = name.trim()
        return if (trimmedName.isEmpty()) {
            CategoryMutationResult.Failure(CategoryError.BlankName)
        } else {
            mutation(trimmedName)
        }
    }

    private fun List<CategoryEntity>.hasName(name: String, excludingId: Int? = null): Boolean =
        any { category ->
            category.id != excludingId && category.stableName.equals(name, ignoreCase = true)
        }

    private val CategoryEntity.stableName: String
        get() = customName ?: defaultKey.orEmpty()

    private fun CategoryEntity.toDomain() = Category(
        id = id,
        customName = customName,
        defaultKey = defaultKey?.let { enumValueOf<DefaultCategoryKey>(it) },
        color = enumValueOf<CategoryColor>(colorToken),
        position = position,
        createdAt = createdAt
    )
}
