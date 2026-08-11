package com.indiewalkabout.nowdothis.feature.category.presentation

import com.indiewalkabout.nowdothis.feature.category.domain.model.Category
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryColor
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryError
import com.indiewalkabout.nowdothis.feature.category.domain.model.CategoryMutationResult
import com.indiewalkabout.nowdothis.feature.category.domain.model.DefaultCategoryKey
import com.indiewalkabout.nowdothis.feature.category.domain.repository.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun observation_ordersCategoriesAndResolvesUntouchedDefaults() = runTest(dispatcher) {
        val repository = FakeCategoryRepository(
            listOf(customCategory(2, "Clienti", 1), defaultCategory(1, DefaultCategoryKey.WORK, 0))
        )

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf("Lavoro", "Clienti"), viewModel.uiState.value.categories.map { it.name })
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun renameDefaultCategory_usesLocalizedInitialNameAndDelegatesCustomName() = runTest(dispatcher) {
        val repository = FakeCategoryRepository(listOf(defaultCategory(1, DefaultCategoryKey.WORK, 0)))
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(CategoryEvent.Edit(1))
        assertEquals("Lavoro", viewModel.uiState.value.editor?.name)
        viewModel.onEvent(CategoryEvent.ChangeName("Clienti"))
        viewModel.onEvent(CategoryEvent.ConfirmEditor)
        advanceUntilIdle()

        assertEquals(listOf(1 to "Clienti"), repository.renames)
        assertNull(viewModel.uiState.value.editor)
    }

    @Test
    fun addCategory_delegatesTrimmedNameAndSelectedColor() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(CategoryEvent.Add)
        viewModel.onEvent(CategoryEvent.ChangeName("  Studio  "))
        viewModel.onEvent(CategoryEvent.SelectColor(CategoryColor.GREEN))
        viewModel.onEvent(CategoryEvent.ConfirmEditor)
        advanceUntilIdle()

        assertEquals(listOf("Studio" to CategoryColor.GREEN), repository.creates)
        assertNull(viewModel.uiState.value.editor)
    }

    @Test
    fun duplicateName_keepsEditorAndShowsTypedError() = runTest(dispatcher) {
        val repository = FakeCategoryRepository().apply {
            nextResult = CategoryMutationResult.Failure(CategoryError.DuplicateName)
        }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(CategoryEvent.Add)
        viewModel.onEvent(CategoryEvent.ChangeName("Lavoro"))
        viewModel.onEvent(CategoryEvent.ConfirmEditor)
        advanceUntilIdle()

        assertEquals(CategoryNameError.DUPLICATE, viewModel.uiState.value.editor?.nameError)
        assertEquals("Lavoro", viewModel.uiState.value.editor?.name)
    }

    @Test
    fun blankName_isRejectedWithoutCallingRepository() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(CategoryEvent.Add)
        viewModel.onEvent(CategoryEvent.ChangeName("   "))
        viewModel.onEvent(CategoryEvent.ConfirmEditor)
        advanceUntilIdle()

        assertEquals(CategoryNameError.BLANK, viewModel.uiState.value.editor?.nameError)
        assertTrue(repository.creates.isEmpty())
    }

    @Test
    fun editingChangedColor_renamesThenRecolors() = runTest(dispatcher) {
        val repository = FakeCategoryRepository(listOf(customCategory(5, "Casa", 0)))
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(CategoryEvent.Edit(5))
        viewModel.onEvent(CategoryEvent.ChangeName("Famiglia"))
        viewModel.onEvent(CategoryEvent.SelectColor(CategoryColor.PINK))
        viewModel.onEvent(CategoryEvent.ConfirmEditor)
        advanceUntilIdle()

        assertEquals(listOf(5 to "Famiglia"), repository.renames)
        assertEquals(listOf(5 to CategoryColor.PINK), repository.recolors)
    }

    @Test
    fun moveCategory_delegatesCompleteReorderedIdList() = runTest(dispatcher) {
        val repository = FakeCategoryRepository(
            listOf(customCategory(1, "A", 0), customCategory(2, "B", 1), customCategory(3, "C", 2))
        )
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(CategoryEvent.MoveDown(1))
        advanceUntilIdle()

        assertEquals(listOf(listOf(2, 1, 3)), repository.reorders)
    }

    @Test
    fun delete_requiresConfirmationAndDelegatesOnlyAfterConfirm() = runTest(dispatcher) {
        val repository = FakeCategoryRepository(listOf(customCategory(8, "Archivio", 0)))
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(CategoryEvent.RequestDelete(8))
        assertEquals("Archivio", viewModel.uiState.value.pendingDelete?.name)
        assertTrue(repository.deletes.isEmpty())
        viewModel.onEvent(CategoryEvent.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(listOf(8), repository.deletes)
        assertNull(viewModel.uiState.value.pendingDelete)
    }

    @Test
    fun failedObservation_canRetryWithoutLosingFeature() = runTest(dispatcher) {
        val repository = FakeCategoryRepository().apply { failObservation = true }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        assertEquals(CategoryScreenError.LOAD_FAILED, viewModel.uiState.value.error)

        repository.failObservation = false
        viewModel.onEvent(CategoryEvent.Retry)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertTrue(repository.observationCount >= 2)
    }

    @Test
    fun failedDelete_keepsConfirmationOpenForRetry() = runTest(dispatcher) {
        val repository = FakeCategoryRepository(listOf(customCategory(8, "Archivio", 0))).apply {
            nextResult = CategoryMutationResult.Failure(CategoryError.NotFound)
        }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(CategoryEvent.RequestDelete(8))
        viewModel.onEvent(CategoryEvent.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(8, viewModel.uiState.value.pendingDelete?.id)
    }

    @Test
    fun moveAtBoundary_doesNotCallRepository() = runTest(dispatcher) {
        val repository = FakeCategoryRepository(
            listOf(customCategory(1, "A", 0), customCategory(2, "B", 1))
        )
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(CategoryEvent.MoveUp(1))
        viewModel.onEvent(CategoryEvent.MoveDown(2))
        advanceUntilIdle()

        assertTrue(repository.reorders.isEmpty())
    }

    @Test
    fun repeatedConfirmWhileSaving_launchesOnlyOneCreate() = runTest(dispatcher) {
        val repository = FakeCategoryRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(CategoryEvent.Add)
        viewModel.onEvent(CategoryEvent.ChangeName("Studio"))
        viewModel.onEvent(CategoryEvent.ConfirmEditor)
        viewModel.onEvent(CategoryEvent.ConfirmEditor)
        advanceUntilIdle()

        assertEquals(1, repository.creates.size)
    }

    @Test
    fun repeatedDeleteConfirmation_launchesOnlyOneDelete() = runTest(dispatcher) {
        val repository = FakeCategoryRepository(listOf(customCategory(8, "Archivio", 0)))
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(CategoryEvent.RequestDelete(8))
        viewModel.onEvent(CategoryEvent.ConfirmDelete)
        viewModel.onEvent(CategoryEvent.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(1, repository.deletes.size)
    }

    private fun createViewModel(repository: FakeCategoryRepository) = CategoryViewModel(
        repository = repository,
        defaultNameResolver = DefaultCategoryNameResolver { key ->
            when (key) {
                DefaultCategoryKey.WORK -> "Lavoro"
                DefaultCategoryKey.PERSONAL -> "Personale"
                DefaultCategoryKey.WISHLIST -> "Desideri"
            }
        }
    )
}

private class FakeCategoryRepository(initial: List<Category> = emptyList()) : CategoryRepository {
    private val categories = MutableStateFlow(initial)
    var failObservation = false
    var observationCount = 0
    var nextResult: CategoryMutationResult = CategoryMutationResult.Success
    val creates = mutableListOf<Pair<String, CategoryColor>>()
    val renames = mutableListOf<Pair<Int, String>>()
    val recolors = mutableListOf<Pair<Int, CategoryColor>>()
    val reorders = mutableListOf<List<Int>>()
    val deletes = mutableListOf<Int>()

    override fun observeAll(): Flow<List<Category>> {
        observationCount += 1
        if (failObservation) error("load failed")
        return categories
    }

    override suspend fun create(name: String, color: CategoryColor): CategoryMutationResult {
        creates += name to color
        return takeResult()
    }

    override suspend fun rename(id: Int, name: String): CategoryMutationResult {
        renames += id to name
        return takeResult()
    }

    override suspend fun recolor(id: Int, color: CategoryColor): CategoryMutationResult {
        recolors += id to color
        return takeResult()
    }

    override suspend fun reorder(orderedIds: List<Int>): CategoryMutationResult {
        reorders += orderedIds
        return takeResult()
    }

    override suspend fun delete(id: Int): CategoryMutationResult {
        deletes += id
        return takeResult()
    }

    private fun takeResult() = nextResult.also { nextResult = CategoryMutationResult.Success }
}

private fun defaultCategory(id: Int, key: DefaultCategoryKey, position: Int) = Category(
    id = id,
    defaultKey = key,
    color = CategoryColor.BLUE,
    position = position,
    createdAt = id.toLong()
)

private fun customCategory(id: Int, name: String, position: Int) = Category(
    id = id,
    customName = name,
    color = CategoryColor.GREEN,
    position = position,
    createdAt = id.toLong()
)
