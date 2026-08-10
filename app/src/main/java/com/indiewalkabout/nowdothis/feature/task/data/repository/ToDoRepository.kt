package com.indiewalkabout.nowdothis.feature.task.data.repository

import com.indiewalkabout.nowdothis.feature.task.data.local.TaskDao
import com.indiewalkabout.nowdothis.feature.task.data.local.TaskEntity
import com.indiewalkabout.nowdothis.feature.task.domain.model.Priority
import com.indiewalkabout.nowdothis.feature.task.domain.model.ToDoTask
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// Temporary compatibility adapter for the legacy UI. Remove in Task 12.
@ViewModelScoped
class ToDoRepository @Inject constructor(private val taskDao: TaskDao) {

    val getAllTasks: Flow<List<ToDoTask>> = taskDao.observeLegacyTasks().mapToLegacyTasks()
    val sortByLowPriority: Flow<List<ToDoTask>> =
        taskDao.sortLegacyByLowPriority().mapToLegacyTasks()
    val sortByHighPriority: Flow<List<ToDoTask>> =
        taskDao.sortLegacyByHighPriority().mapToLegacyTasks()

    fun getSelectedTask(taskId: Int): Flow<ToDoTask?> {
        return taskDao.observeLegacyTask(taskId).map { entity -> entity?.toLegacyTask() }
    }

    suspend fun addTask(toDoTask: ToDoTask) {
        taskDao.insertTask(toDoTask.toEntity())
    }

    suspend fun updateTask(toDoTask: ToDoTask) {
        taskDao.updateLegacyFields(
            taskId = toDoTask.id,
            title = toDoTask.title,
            description = toDoTask.description,
            priority = toDoTask.priority.name
        )
    }

    suspend fun deleteTask(toDoTask: ToDoTask) {
        taskDao.deleteTaskById(toDoTask.id)
    }

    suspend fun deleteAllTasks() {
        taskDao.deleteAllTasks()
    }

    fun searchDatabase(searchQuery: String): Flow<List<ToDoTask>> {
        return taskDao.searchLegacyTasks(searchQuery).mapToLegacyTasks()
    }

    private fun Flow<List<TaskEntity>>.mapToLegacyTasks(): Flow<List<ToDoTask>> =
        map { entities -> entities.map { entity -> entity.toLegacyTask() } }

    private fun TaskEntity.toLegacyTask(): ToDoTask = ToDoTask(
        id = id,
        title = title,
        description = description,
        priority = Priority.valueOf(priority)
    )

    private fun ToDoTask.toEntity(): TaskEntity = TaskEntity(
        id = id,
        title = title,
        description = description,
        priority = priority.name
    )

}
