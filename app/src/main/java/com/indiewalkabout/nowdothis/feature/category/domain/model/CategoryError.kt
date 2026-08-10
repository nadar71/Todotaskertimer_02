package com.indiewalkabout.nowdothis.feature.category.domain.model

sealed interface CategoryError {
    data object BlankName : CategoryError
    data object DuplicateName : CategoryError
    data object NotFound : CategoryError
    data object InvalidOrder : CategoryError
}
