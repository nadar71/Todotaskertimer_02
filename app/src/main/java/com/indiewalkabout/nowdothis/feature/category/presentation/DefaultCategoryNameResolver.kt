package com.indiewalkabout.nowdothis.feature.category.presentation

import android.content.Context
import com.indiewalkabout.nowdothis.R
import com.indiewalkabout.nowdothis.feature.category.domain.model.DefaultCategoryKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface DefaultCategoryNameResolver {
    fun resolve(key: DefaultCategoryKey): String
}

class AndroidDefaultCategoryNameResolver @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DefaultCategoryNameResolver {
    override fun resolve(key: DefaultCategoryKey): String = context.getString(
        when (key) {
            DefaultCategoryKey.WORK -> R.string.category_work
            DefaultCategoryKey.PERSONAL -> R.string.category_personal
            DefaultCategoryKey.WISHLIST -> R.string.category_wishlist
        }
    )
}
