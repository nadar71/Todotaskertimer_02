package com.indiewalkabout.nowdothis.feature.task.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.indiewalkabout.nowdothis.core.util.Constants.PREFERENCE_KEY
import com.indiewalkabout.nowdothis.feature.task.domain.model.TaskSort
import com.indiewalkabout.nowdothis.feature.task.domain.repository.TaskPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreTaskPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) : TaskPreferencesRepository {
    private val sortKey = stringPreferencesKey(PREFERENCE_KEY)

    override val taskSort: Flow<TaskSort> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            when (preferences[sortKey]) {
                "LOW" -> TaskSort.LOW_FIRST
                "HIGH" -> TaskSort.HIGH_FIRST
                "NONE" -> TaskSort.DEFAULT
                else -> TaskSort.DEFAULT
            }
        }

    override suspend fun setTaskSort(sort: TaskSort) {
        context.dataStore.edit { preferences ->
            preferences[sortKey] = when (sort) {
                TaskSort.DEFAULT -> "NONE"
                TaskSort.LOW_FIRST -> "LOW"
                TaskSort.HIGH_FIRST -> "HIGH"
            }
        }
    }
}
