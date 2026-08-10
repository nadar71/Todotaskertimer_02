package com.indiewalkabout.nowdothis.feature.category.domain.model

data class Category(
    val id: Int = 0,
    val customName: String? = null,
    val defaultKey: DefaultCategoryKey? = null,
    val color: CategoryColor,
    val position: Int,
    val createdAt: Long
)
