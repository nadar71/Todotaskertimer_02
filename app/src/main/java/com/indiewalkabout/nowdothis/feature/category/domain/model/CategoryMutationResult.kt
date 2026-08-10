package com.indiewalkabout.nowdothis.feature.category.domain.model

sealed interface CategoryMutationResult {
    data object Success : CategoryMutationResult
    data class Failure(val error: CategoryError) : CategoryMutationResult
}
