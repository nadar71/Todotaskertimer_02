# Core Task Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the approved local-first task expansion: user-managed categories, completion, optional due dates, one reminder, recurrence, subtasks, calendar browsing, completion history, and complete Italian/English localization.

**Architecture:** Keep one Android application module and enforce sibling feature packages for task, category, calendar, and history. Each feature follows clean MVVM (`Compose -> ViewModel -> use case -> repository interface -> repository implementation -> DAO/platform`) and communicates through immutable `StateFlow` state plus one-time effect flows. Navigation 3 entries own their Hilt ViewModels through `NavEntry` decorators.

**Tech Stack:** Kotlin 2.2.21, Java 17, Jetpack Compose Material 3, Navigation 3 version 1.1.3, Room 2.8.4, Hilt 2.57.1, AndroidX lifecycle 2.10.0, coroutines/Flow, AlarmManager, JUnit 4, Room migration testing, Compose UI testing.

## Global Constraints

- Keep `compileSdk = 36`, `minSdk = 23`, and `targetSdk = 34`; upgrading platform targets is outside this feature plan.
- Keep a single `:app` Gradle module; package boundaries must remain suitable for later module extraction.
- Italian is the unqualified default locale; English is the only alternative locale in this release.
- No user-facing string may be hardcoded in Kotlin or Compose.
- Domain code must not import Room, Compose, Android framework, or Hilt types.
- Use `Flow` for observable data, suspend functions for mutations, `StateFlow<UiState>` for screen state, and one-time effect flows for navigation, snackbars, and permission requests.
- Persist timestamps as epoch milliseconds; perform date arithmetic through injected `Clock` and `ZoneIdProvider` abstractions.
- Preserve every existing version 1 task row through an explicit Room migration; never add destructive migration fallback.
- Support exactly one optional reminder and recurrence values `NONE`, `DAILY`, `WEEKLY`, and `MONTHLY` with an optional end date.
- Completing a recurring task preserves the completed occurrence and creates the next pending occurrence.
- Completing a parent completes unfinished subtasks; completing all subtasks does not complete the parent.
- Due dates remain optional; undated tasks appear in Unscheduled and never appear in Calendar.
- Completion history is read-only except for confirmed permanent deletion; reopening is outside this release.
- Preserve priority sorting, its saved preference, delete-all confirmation, and swipe-delete undo from the current app.
- Follow Material 3 Android conventions; use the screenshots as feature and hierarchy references, not pixel-identical specifications.

## File Structure Map

```text
app/src/main/java/com/indiewalkabout/nowdothis/
  app/navigation/AppNavigation.kt
  core/database/{AppDatabase,DatabaseMigrations}.kt
  core/di/{CoroutineModule,DatabaseModule,TimeModule}.kt
  core/notifications/{AlarmGateway,AndroidAlarmGateway,NotificationPublisher,
    ReminderReceiver,ReminderReconcileReceiver}.kt
  core/time/{AppClock,DayBounds,ZoneIdProvider}.kt
  feature/task/data/local/{TaskDao,TaskEntity,SubtaskEntity,TaskRelations}.kt
  feature/task/data/mapper/TaskEntityMapper.kt
  feature/task/data/repository/{OfflineTaskRepository,DataStoreTaskPreferencesRepository}.kt
  feature/task/domain/model/{Task,TaskPriority,Subtask,RecurrenceType,
    ReminderStatus,TaskSections,TaskFilter,TaskSort,DeletedTaskSnapshot}.kt
  feature/task/domain/repository/{TaskRepository,TaskScheduleReader,
    CompletionHistoryReader,ReminderScheduler,TaskPreferencesRepository}.kt
  feature/task/domain/usecase/{ObserveTaskSections,SaveTask,CompleteTask,
    DeleteTask,DeleteAllTasks,RestoreDeletedTask,CalculateNextOccurrence}.kt
  feature/task/presentation/list/{TaskListContract,TaskListViewModel,
    TaskListRoute,TaskListScreen,TaskRow,TaskSection}.kt
  feature/task/presentation/editor/{TaskEditorContract,TaskEditorViewModel,
    TaskEditorRoute,TaskEditorScreen,SubtaskEditor}.kt
  feature/category/data/local/{CategoryDao,CategoryEntity}.kt
  feature/category/data/repository/OfflineCategoryRepository.kt
  feature/category/domain/model/{Category,CategoryColor,DefaultCategoryKey}.kt
  feature/category/domain/repository/CategoryRepository.kt
  feature/category/presentation/{CategoryContract,CategoryViewModel,
    CategoryRoute,CategoryScreen}.kt
  feature/calendar/domain/usecase/ObserveCalendar.kt
  feature/calendar/presentation/{CalendarContract,CalendarViewModel,
    CalendarRoute,CalendarScreen,MonthGrid}.kt
  feature/history/domain/usecase/ObserveCompletionHistory.kt
  feature/history/presentation/{HistoryContract,HistoryViewModel,
    HistoryRoute,HistoryScreen}.kt
```

---

### Task 1: Build, Test, Time, And Localization Foundations

**Files:**
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-en/strings.xml`
- Create: `app/src/main/res/resources.properties`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/core/time/AppClock.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/core/time/ZoneIdProvider.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/core/time/DayBounds.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/core/di/TimeModule.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/local/ToDoDatabase.kt`
- Create: `app/src/androidTest/java/com/indiewalkabout/nowdothis/core/LocalizationTest.kt`

**Interfaces:**
- Consumes: Current Android application and resource setup.
- Produces: `AppClock.nowMillis(): Long`, `ZoneIdProvider.zoneId(): ZoneId`, `DayBounds.forEpochMillis(now, zoneId)`, Room schema export, lifecycle-aware Compose collection, NavEntry ViewModel support, and Italian/English resource infrastructure.

- [ ] **Step 1: Add a failing locale resource test**

```kotlin
@RunWith(AndroidJUnit4::class)
class LocalizationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun tasksTitle_resolvesInItalianAndEnglish() {
        fun title(language: String): String {
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(language))
            return context.createConfigurationContext(configuration)
                .getString(R.string.tasks_title)
        }

        assertEquals("Attività", title("it"))
        assertEquals("Tasks", title("en"))
    }
}
```

- [ ] **Step 2: Run the test and confirm the missing resource failure**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`

Expected: compilation fails because `R.string.tasks_title` does not exist.

- [ ] **Step 3: Configure dependencies and deterministic time abstractions**

Add the Room Gradle plugin at version `2.8.4`, enable `coreLibraryDesugaring`, `androidResources.generateLocaleConfig = true`, configure `room.schemaDirectory("$projectDir/schemas")`, and add:

```kotlin
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.10.0")
implementation("androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0")
coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
testImplementation("androidx.arch.core:core-testing:2.2.0")
androidTestImplementation("androidx.room:room-testing:2.8.4")
```

Implement the time contracts exactly as:

```kotlin
fun interface AppClock { fun nowMillis(): Long }
fun interface ZoneIdProvider { fun zoneId(): ZoneId }

data class DayBounds(val startInclusive: Long, val endExclusive: Long) {
    companion object {
        fun forEpochMillis(now: Long, zoneId: ZoneId): DayBounds {
            val date = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
            return DayBounds(
                date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            )
        }
    }
}
```

Bind production implementations to `System.currentTimeMillis()` and `ZoneId.systemDefault()` in `TimeModule`.

Before changing the version 1 database in Task 3, set its current `@Database` declaration to `exportSchema = true` and run KSP with the configured Room schema directory. Commit the generated version 1 JSON under `app/schemas`; `MigrationTestHelper` requires that historical schema.

- [ ] **Step 4: Establish Italian default and English alternative resources**

Set `unqualifiedResLocale=it` in `resources.properties`. Translate all existing strings into Italian in `values/strings.xml`, mirror every key in English under `values-en/strings.xml`, and add `tasks_title` with the values asserted above. Replace apostrophe-sensitive text with XML-safe values and add plurals for `subtask_progress` in both locales.

- [ ] **Step 5: Verify resources and the baseline build**

Run: `./gradlew :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:assembleDebug`

Expected: all commands pass. Run `connectedDebugAndroidTest` when an emulator is available and confirm `LocalizationTest` passes.

- [ ] **Step 6: Commit the foundation**

```bash
git add build.gradle.kts app/build.gradle.kts app/schemas app/src/main/res app/src/main/java/com/indiewalkabout/nowdothis/core app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/local/ToDoDatabase.kt app/src/androidTest
git commit -m "build: add localized clean architecture foundations"
```

### Task 2: Clean Domain Models And Temporal Rules

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/model/Task.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/model/TaskPriority.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/model/Subtask.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/model/RecurrenceType.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/model/ReminderStatus.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/model/TaskFilter.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/model/TaskSort.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/model/TaskSections.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/usecase/CalculateNextOccurrence.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/usecase/TaskSectionClassifier.kt`
- Create: `app/src/test/java/com/indiewalkabout/nowdothis/feature/task/domain/CalculateNextOccurrenceTest.kt`
- Create: `app/src/test/java/com/indiewalkabout/nowdothis/feature/task/domain/TaskSectionClassifierTest.kt`

**Interfaces:**
- Consumes: `DayBounds`, `ZoneIdProvider`.
- Produces: framework-free `Task`, `Subtask`, recurrence and reminder enums, `TaskFilter`, `TaskSections`, and `CalculateNextOccurrence.invoke(Task): Long?`.

- [ ] **Step 1: Write recurrence and section-classification tests**

```kotlin
@Test
fun monthlyRecurrence_clampsJanuary31ToFebruaryEnd() {
    val zone = ZoneId.of("Europe/Rome")
    val due = ZonedDateTime.of(2025, 1, 31, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    val task = task(dueAt = due, recurrence = RecurrenceType.MONTHLY)

    val next = CalculateNextOccurrence { zone }.invoke(task)

    assertEquals(
        ZonedDateTime.of(2025, 2, 28, 9, 0, 0, 0, zone).toInstant().toEpochMilli(),
        next
    )
}

@Test
fun classify_placesUndatedPendingTaskInUnscheduled() {
    val result = TaskSectionClassifier.classify(
        tasks = listOf(task(dueAt = null)),
        bounds = DayBounds(1_000, 2_000)
    )
    assertEquals(1, result.unscheduled.size)
    assertTrue(result.overdue.isEmpty())
}
```

- [ ] **Step 2: Run tests and confirm missing-domain-type failures**

Run: `./gradlew :app:testDebugUnitTest --tests '*CalculateNextOccurrenceTest' --tests '*TaskSectionClassifierTest'`

Expected: compilation fails because the new domain types do not exist.

- [ ] **Step 3: Implement framework-free models**

Use these stable shapes:

```kotlin
enum class TaskPriority { HIGH, MEDIUM, LOW }
enum class RecurrenceType { NONE, DAILY, WEEKLY, MONTHLY }
enum class ReminderStatus { NONE, REQUESTED, SCHEDULED, UNAVAILABLE }

data class Subtask(
    val id: Int = 0,
    val taskId: Int = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val position: Int
)

data class Task(
    val id: Int = 0,
    val title: String,
    val description: String,
    val priority: TaskPriority,
    val categoryId: Int? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val dueAt: Long? = null,
    val reminderAt: Long? = null,
    val reminderStatus: ReminderStatus = ReminderStatus.NONE,
    val recurrence: RecurrenceType = RecurrenceType.NONE,
    val recurrenceEndAt: Long? = null,
    val seriesId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val subtasks: List<Subtask> = emptyList()
)
```

`TaskFilter` contains `query: String`, `categoryId: Int?`, and `sort: TaskSort`; `TaskSort` is `DEFAULT`, `LOW_FIRST`, or `HIGH_FIRST`. `TaskSections` contains lists named `overdue`, `today`, `upcoming`, `unscheduled`, and `completedToday`.

- [ ] **Step 4: Implement recurrence and classification**

`CalculateNextOccurrence` returns null for `NONE`, missing due time, or a next occurrence after `recurrenceEndAt`. Calculate in the injected zone with `plusDays(1)`, `plusWeeks(1)`, or `plusMonths(1)` and convert back to epoch milliseconds. `TaskSectionClassifier` uses `DayBounds`, completion state, and nullable due time; apply `TaskSort` after classification.

- [ ] **Step 5: Run domain tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.indiewalkabout.nowdothis.feature.task.domain.*'`

Expected: tests cover all four recurrence values, recurrence end boundaries, month end, overdue/today/upcoming/unscheduled/completed-today classification, and all priority sort modes.

- [ ] **Step 6: Commit domain rules**

```bash
git add app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain app/src/test/java/com/indiewalkabout/nowdothis/feature/task/domain
git commit -m "feat: add task scheduling domain model"
```

### Task 3: Room Version 2 Schema And Migration

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/core/database/AppDatabase.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/core/database/DatabaseMigrations.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/core/di/DatabaseModule.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/local/TaskEntity.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/local/SubtaskEntity.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/local/TaskRelations.kt`
- Replace: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/local/ToDoDao.kt` with `TaskDao.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/category/data/local/CategoryEntity.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/category/data/local/CategoryDao.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/repository/ToDoRepository.kt`
- Delete: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/local/ToDoDatabase.kt`
- Delete: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/di/DatabaseModule.kt`
- Create: `app/src/androidTest/java/com/indiewalkabout/nowdothis/core/database/Migration1To2Test.kt`
- Create: `app/src/androidTest/java/com/indiewalkabout/nowdothis/core/database/TaskDaoTest.kt`

**Interfaces:**
- Consumes: Domain enums and models from Task 2.
- Produces: `AppDatabase` version 2, `MIGRATION_1_2`, `TaskDao`, `CategoryDao`, Room relations, and a temporary compatibility implementation of existing `ToDoRepository` so the old UI stays buildable until Task 12.

- [ ] **Step 1: Write the migration preservation test**

```kotlin
@Test
fun migrate1To2_preservesLegacyTaskAsPendingUnscheduled() {
    helper.createDatabase(TEST_DB, 1).apply {
        execSQL("INSERT INTO todo_table (id, title, description, priority) VALUES (7, 'Legacy', 'Keep me', 'HIGH')")
        close()
    }

    val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
    db.query("SELECT title, is_completed, due_at, category_id FROM tasks WHERE id = 7").use {
        assertTrue(it.moveToFirst())
        assertEquals("Legacy", it.getString(0))
        assertEquals(0, it.getInt(1))
        assertTrue(it.isNull(2))
        assertTrue(it.isNull(3))
    }
}
```

- [ ] **Step 2: Run the migration test and confirm version/schema failure**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`

Expected: compilation fails because `AppDatabase` and `MIGRATION_1_2` do not exist.

- [ ] **Step 3: Define entities and database aggregation**

Create `tasks`, `subtasks`, and `categories`. Use `ForeignKey.CASCADE` from subtasks to tasks and `ForeignKey.SET_NULL` from tasks to categories. Add indices for `category_id`, `due_at`, `completed_at`, `is_completed`, `series_id`, and `task_id`. `AppDatabase` exports its schema and exposes `taskDao()` and `categoryDao()`.

The migration must create `categories`, insert fixed IDs `1/2/3` with keys `WORK/PERSONAL/WISHLIST`, create `tasks_new` with explicit defaults, copy legacy columns, drop `todo_table`, rename `tasks_new` to `tasks`, create `subtasks`, and create all indices. Keep the database filename `todo_database` and register only `MIGRATION_1_2` in `Room.databaseBuilder`.

- [ ] **Step 4: Implement DAO queries and legacy compatibility**

`TaskDao` must expose:

```kotlin
fun observeTask(taskId: Int): Flow<TaskWithSubtasks?>
fun observeOverdue(endExclusive: Long, query: String, categoryId: Int?): Flow<List<TaskWithSubtasks>>
fun observeDueBetween(start: Long, end: Long, query: String, categoryId: Int?): Flow<List<TaskWithSubtasks>>
fun observeUpcoming(startInclusive: Long, query: String, categoryId: Int?): Flow<List<TaskWithSubtasks>>
fun observeUnscheduled(query: String, categoryId: Int?): Flow<List<TaskWithSubtasks>>
fun observeCompletedBetween(start: Long, end: Long, query: String, categoryId: Int?): Flow<List<TaskWithSubtasks>>
fun observeMonth(start: Long, end: Long): Flow<List<TaskWithSubtasks>>
fun observeHistory(before: Long, query: String, categoryId: Int?): Flow<List<TaskWithSubtasks>>
suspend fun insertTask(entity: TaskEntity): Long
suspend fun updateTask(entity: TaskEntity)
suspend fun deleteTaskById(taskId: Int)
suspend fun replaceSubtasks(taskId: Int, subtasks: List<SubtaskEntity>)
```

Rewrite the current `ToDoRepository` as a compatibility adapter over `TaskDao`, mapping the four legacy fields so `SharedViewModel` and the old screens still compile and run. Mark it with a comment identifying removal in Task 12; do not expose it to new code.

- [ ] **Step 5: Test DAO boundaries and migration**

Add instrumentation cases for every time-section query, month/history bounds, search, category filtering, subtask cascade, category set-null, and seeded defaults.

Run: `./gradlew :app:connectedDebugAndroidTest`

Expected: migration and DAO tests pass on an emulator. Also run `./gradlew :app:assembleDebug` and confirm the compatibility UI builds.

- [ ] **Step 6: Commit the schema**

```bash
git add app/build.gradle.kts app/schemas app/src/main/java app/src/androidTest
git commit -m "feat: migrate task storage to room schema v2"
```

### Task 4: Repository Contracts And Offline Implementations

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/model/DeletedTaskSnapshot.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/repository/TaskRepository.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/repository/TaskScheduleReader.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/repository/CompletionHistoryReader.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/repository/TaskPreferencesRepository.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/mapper/TaskEntityMapper.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/repository/OfflineTaskRepository.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/repository/DataStoreTaskPreferencesRepository.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/category/domain/model/Category.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/category/domain/model/CategoryColor.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/category/domain/model/DefaultCategoryKey.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/category/domain/model/CategoryError.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/category/domain/model/CategoryMutationResult.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/category/domain/repository/CategoryRepository.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/category/data/repository/OfflineCategoryRepository.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/di/TaskDataModule.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/category/di/CategoryDataModule.kt`
- Create: `app/src/test/java/com/indiewalkabout/nowdothis/feature/task/data/TaskEntityMapperTest.kt`

**Interfaces:**
- Consumes: Room DAOs and clean domain models.
- Produces: `TaskRepository`, `TaskScheduleReader`, `CompletionHistoryReader`, `CategoryRepository`, offline implementations, Hilt bindings, and lossless entity/domain mapping.

- [ ] **Step 1: Write a mapper round-trip test**

```kotlin
@Test
fun taskRoundTrip_preservesSchedulingAndSubtasks() {
    val original = task(
        categoryId = 2,
        dueAt = 1_000,
        reminderAt = 900,
        recurrence = RecurrenceType.WEEKLY,
        subtasks = listOf(Subtask(title = "One", position = 0))
    )

    val (entity, subtasks) = TaskEntityMapper.toEntities(original)
    val mapped = TaskEntityMapper.toDomain(TaskWithSubtasks(entity, subtasks))

    assertEquals(original.copy(id = mapped.id), mapped)
}
```

- [ ] **Step 2: Define exact repository contracts**

```kotlin
interface TaskRepository {
    fun observeTask(taskId: Int): Flow<Task?>
    fun observeSections(filter: TaskFilter, bounds: DayBounds): Flow<TaskSections>
    suspend fun getTask(taskId: Int): Task?
    suspend fun upsert(task: Task): Int
    suspend fun completeAtomically(taskId: Int, completedAt: Long, next: Task?): Task?
    suspend fun deleteWithSnapshot(taskId: Int): DeletedTaskSnapshot
    suspend fun deleteAll(): List<Int>
    suspend fun restore(snapshot: DeletedTaskSnapshot): Int
    suspend fun deleteCompleted(taskId: Int)
    suspend fun updateReminderStatus(taskId: Int, status: ReminderStatus)
    suspend fun futureReminders(after: Long): List<Task>
}

interface TaskScheduleReader {
    fun observeMonth(startInclusive: Long, endExclusive: Long): Flow<List<Task>>
    fun observeDay(startInclusive: Long, endExclusive: Long): Flow<List<Task>>
}

interface CompletionHistoryReader {
    fun observeHistory(before: Long, filter: TaskFilter): Flow<List<Task>>
}

interface TaskPreferencesRepository {
    val taskSort: Flow<TaskSort>
    suspend fun setTaskSort(sort: TaskSort)
}
```

`DataStoreTaskPreferencesRepository` reuses `todo_preferences` and maps legacy values `NONE -> DEFAULT`, `LOW -> LOW_FIRST`, and `HIGH -> HIGH_FIRST`; any other value maps to `DEFAULT`.

`CategoryRepository` exposes `observeAll`, `create`, `rename`, `recolor`, `reorder`, and `delete`. Validate trimmed unique names case-insensitively in the implementation and return typed `CategoryError` values.

- [ ] **Step 3: Implement mappers and Room-backed repositories**

Map every enum by stable `name`, sort subtasks by `position`, and execute task/subtask writes inside `AppDatabase.withTransaction`. `completeAtomically` updates the current task and unfinished subtasks and inserts the supplied next occurrence in one transaction. `deleteWithSnapshot` reads the full relation before deletion. `restore` inserts task and subtasks together and retains the original ID when available.

Use the exact mapper signatures `toEntities(task: Task): Pair<TaskEntity, List<SubtaskEntity>>` and `toDomain(relation: TaskWithSubtasks): Task`.

Define category mutations as:

```kotlin
interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun create(name: String, color: CategoryColor): CategoryMutationResult
    suspend fun rename(id: Int, name: String): CategoryMutationResult
    suspend fun recolor(id: Int, color: CategoryColor): CategoryMutationResult
    suspend fun reorder(orderedIds: List<Int>): CategoryMutationResult
    suspend fun delete(id: Int): CategoryMutationResult
}

sealed interface CategoryMutationResult {
    data object Success : CategoryMutationResult
    data class Failure(val error: CategoryError) : CategoryMutationResult
}
```

- [ ] **Step 4: Bind interfaces and run tests**

Bind offline implementations with `@Binds @Singleton`. Add DAO-backed instrumentation assertions for `observeSections`, atomic completion, snapshot restore, schedule reads, history reads, and category uniqueness.

Run: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug`

Expected: mapper, repository, DAO, and migration tests pass; the compatibility UI still builds.

- [ ] **Step 5: Commit repositories**

```bash
git add app/src/main/java/com/indiewalkabout/nowdothis/feature app/src/test app/src/androidTest
git commit -m "feat: add clean offline task repositories"
```

### Task 5: Task Lifecycle Use Cases

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/repository/ReminderScheduler.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/usecase/ObserveTaskSections.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/usecase/SaveTask.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/usecase/CompleteTask.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/usecase/DeleteTask.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/usecase/DeleteAllTasks.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/usecase/RestoreDeletedTask.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/usecase/ValidateTask.kt`
- Create: `app/src/test/java/com/indiewalkabout/nowdothis/feature/task/domain/TaskLifecycleUseCasesTest.kt`

**Interfaces:**
- Consumes: Task repositories, time abstractions, and recurrence calculation.
- Produces: tested task lifecycle use cases plus `ReminderScheduler.schedule(taskId, triggerAt)`, `cancel(taskId)`, and `reconcile()`.

- [ ] **Step 1: Write completion and validation tests with fakes**

```kotlin
@Test
fun completeRecurringTask_completesChildrenAndCreatesNextOccurrence() = runTest {
    val repository = FakeTaskRepository(recurringTaskWithTwoPendingSubtasks())
    val scheduler = FakeReminderScheduler()
    val useCase = CompleteTask(repository, scheduler, CalculateNextOccurrence { ROME }, fixedClock)

    useCase(taskId = 4)

    assertTrue(repository.completedTask!!.subtasks.all(Subtask::isCompleted))
    assertNotNull(repository.nextOccurrence)
    assertFalse(repository.nextOccurrence!!.isCompleted)
    assertEquals(listOf(4), scheduler.cancelledIds)
}

@Test
fun validate_rejectsReminderAfterDueTime() {
    assertEquals(
        TaskValidationError.REMINDER_AFTER_DUE,
        ValidateTask().invoke(task(dueAt = 1_000, reminderAt = 1_001)).single()
    )
}
```

- [ ] **Step 2: Implement validation and save orchestration**

Validation returns typed errors for blank title, blank description, reminder after due, recurrence without due, recurrence end before due, and reminder in the past. `SaveTask` validates, stamps creation/update time, assigns a UUID series ID for new recurring series, persists first, then schedules or cancels the reminder. It updates `ReminderStatus` to `SCHEDULED` or `UNAVAILABLE` without rolling back the task.

Define the scheduling contract as:

```kotlin
enum class ReminderScheduleResult { EXACT, INEXACT, FAILED }

interface ReminderScheduler {
    suspend fun schedule(taskId: Int, triggerAt: Long): ReminderScheduleResult
    suspend fun cancel(taskId: Int)
    suspend fun reconcile()
}
```

- [ ] **Step 3: Implement completion, deletion, and undo orchestration**

`CompleteTask` loads the task, computes the next due time, copies pending subtasks, shifts reminder by `nextDueAt - currentDueAt`, and passes the next occurrence into `completeAtomically`. It cancels the completed alarm and schedules the next one. `DeleteTask` returns `DeletedTaskSnapshot` after canceling; `RestoreDeletedTask` restores the snapshot and reschedules a future requested reminder.

`DeleteAllTasks` obtains the IDs of tasks with active reminders from `TaskRepository.deleteAll()`, cancels each stable alarm ID, and returns success only after the Room transaction commits. It is called only after UI confirmation and does not offer undo.

- [ ] **Step 4: Run lifecycle tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*TaskLifecycleUseCasesTest'`

Expected: tests pass for add/update, all validation errors, non-recurring completion, all recurrence modes, recurrence end, subtask copying, scheduling failure, delete, and restore.

- [ ] **Step 5: Commit lifecycle rules**

```bash
git add app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain app/src/test/java/com/indiewalkabout/nowdothis/feature/task/domain
git commit -m "feat: implement task lifecycle use cases"
```

### Task 6: Android Reminder Delivery And Reconciliation

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/core/notifications/AlarmGateway.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/core/notifications/AndroidAlarmGateway.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/core/notifications/AlarmManagerReminderScheduler.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/core/notifications/NotificationPublisher.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/core/notifications/ReminderReceiver.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/core/notifications/ReminderReconcileReceiver.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/core/di/CoroutineModule.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/core/notifications/NotificationModule.kt`
- Create: `app/src/test/java/com/indiewalkabout/nowdothis/core/notifications/AlarmManagerReminderSchedulerTest.kt`

**Interfaces:**
- Consumes: `ReminderScheduler`, `TaskRepository.futureReminders`, application-scoped coroutine context.
- Produces: exact/inexact alarm selection, cancellation, localized notification delivery, boot/upgrade/permission reconciliation, and notification intents carrying `task_id`.

- [ ] **Step 1: Test exact and fallback scheduling**

```kotlin
@Test
fun schedule_usesInexactAlarmWhenExactAccessIsUnavailable() = runTest {
    val gateway = FakeAlarmGateway(canScheduleExact = false)
    val scheduler = AlarmManagerReminderScheduler(gateway, repository, fixedClock)

    val result = scheduler.schedule(taskId = 9, triggerAt = 5_000)

    assertEquals(ReminderScheduleResult.INEXACT, result)
    assertEquals(AlarmCall.Inexact(9, 5_000), gateway.lastCall)
}
```

- [ ] **Step 2: Implement platform gateways**

`AlarmGateway` exposes `canScheduleExact`, `setExact`, `setInexact`, and `cancel`. Use `setExactAndAllowWhileIdle` when permitted and `setAndAllowWhileIdle` otherwise. Pending intents use request code `taskId`, immutable/update-current flags, and explicit `ReminderReceiver` intents.

- [ ] **Step 3: Implement notification and reconciliation receivers**

`ReminderReceiver` posts on channel `task_reminders` and launches `MainActivity` with action `com.indiewalkabout.nowdothis.OPEN_TASK` and integer extra `task_id`. `ReminderReconcileReceiver` handles `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, and `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, calls `goAsync()`, launches `ReminderScheduler.reconcile()` in the injected application scope, and always finishes the pending result.

- [ ] **Step 4: Declare permissions and receivers**

Declare `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, and `RECEIVE_BOOT_COMPLETED`. Export neither receiver. Add localized notification channel name/description and notification title/body format in both resource sets.

- [ ] **Step 5: Verify scheduler behavior**

Run: `./gradlew :app:testDebugUnitTest --tests '*AlarmManagerReminderSchedulerTest' :app:assembleDebug`

Expected: exact, inexact, replacement, cancellation, and future-only reconciliation tests pass; manifest merge succeeds.

- [ ] **Step 6: Commit reminders**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/indiewalkabout/nowdothis/core app/src/main/res app/src/test
git commit -m "feat: add resilient task reminders"
```

### Task 7: Task List Vertical Slice

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/list/TaskListContract.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/list/TaskListViewModel.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/list/TaskListRoute.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/list/TaskListScreen.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/list/TaskRow.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/list/TaskSection.kt`
- Create: `app/src/test/java/com/indiewalkabout/nowdothis/feature/task/presentation/list/TaskListViewModelTest.kt`
- Create: `app/src/androidTest/java/com/indiewalkabout/nowdothis/feature/task/presentation/list/TaskListScreenTest.kt`

**Interfaces:**
- Consumes: `ObserveTaskSections`, `CompleteTask`, `DeleteTask`, `DeleteAllTasks`, `RestoreDeletedTask`, `CategoryRepository`, `TaskPreferencesRepository`.
- Produces: `TaskListUiState`, `TaskListEvent`, `TaskListEffect`, feature-scoped `TaskListViewModel`, and stateless Material 3 task-list UI.

- [ ] **Step 1: Test filter state and deletion undo effects**

```kotlin
@Test
fun categorySelection_restartsSectionsWithSelectedCategory() = runTest {
    val viewModel = createViewModel()
    viewModel.onEvent(TaskListEvent.SelectCategory(3))
    advanceUntilIdle()
    assertEquals(3, viewModel.uiState.value.selectedCategoryId)
    assertEquals(3, fakeObserveSections.lastFilter.categoryId)
}

@Test
fun undoDelete_restoresSnapshot() = runTest {
    val viewModel = createViewModel()
    viewModel.onEvent(TaskListEvent.DeleteTask(8))
    viewModel.onEvent(TaskListEvent.UndoDelete)
    advanceUntilIdle()
    assertEquals(8, fakeRestore.lastSnapshot.task.id)
}
```

- [ ] **Step 2: Implement immutable contract and ViewModel**

`TaskListUiState` contains loading/error flags, `TaskSections`, categories, query, selected category ID, and `TaskSort`. Initialize sort from `TaskPreferencesRepository` and persist every sort selection. Events cover query, category, sort, complete, delete, undo, request/confirm delete all, retry, open editor, open category management, open calendar, and open history. Effects carry destination requests and localized message resource IDs with formatting arguments.

- [ ] **Step 3: Build the Material 3 list UI**

Use a search field, lazy category chips, and section headers in the approved order. Omit empty sections, show a localized global empty state when all sections are empty, and render a See all action for Completed Today. `TaskRow` includes a checkbox, title, category label/color, priority icon, due text, reminder/recurrence icons, and localized subtask progress. Preserve swipe delete with snackbar undo.

- [ ] **Step 4: Run ViewModel and Compose tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*TaskListViewModelTest' :app:compileDebugAndroidTestKotlin :app:assembleDebug`

Expected: state/effect tests pass; Compose tests compile for section order, empty state, category chips, semantic labels, and swipe-delete callback.

- [ ] **Step 5: Commit the list slice**

```bash
git add app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/list app/src/test app/src/androidTest app/src/main/res
git commit -m "feat: add grouped task list experience"
```

### Task 8: Task Editor Vertical Slice

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorContract.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorViewModel.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorRoute.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorScreen.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/SubtaskEditor.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/navigation/TaskEditorKey.kt`
- Create: `app/src/test/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorViewModelTest.kt`
- Create: `app/src/androidTest/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor/TaskEditorScreenTest.kt`

**Interfaces:**
- Consumes: `TaskEditorKey`, `TaskRepository.observeTask`, `CategoryRepository.observeAll`, `SaveTask`, reminder capability checks.
- Produces: assisted-injected `TaskEditorViewModel`, restorable draft state, inline validation, permission effects, and create/update UI.

- [ ] **Step 1: Test new-task initialization and draft restoration**

```kotlin
@Test
fun newTask_usesInitialCalendarDateAndRestoresEditedTitle() = runTest {
    val handle = SavedStateHandle(mapOf("title" to "Bozza"))
    val viewModel = createEditor(
        key = TaskEditorKey(taskId = null, initialDueAt = 10_000),
        savedStateHandle = handle
    )
    advanceUntilIdle()
    assertEquals("Bozza", viewModel.uiState.value.title)
    assertEquals(10_000, viewModel.uiState.value.dueAt)
}
```

- [ ] **Step 2: Implement assisted ViewModel and saved draft fields**

Use `@HiltViewModel(assistedFactory = Factory::class)` and `@Assisted TaskEditorKey`. Persist title, description, priority, category, date/time, reminder, recurrence, recurrence end, and subtask draft list in `SavedStateHandle`. Existing tasks load once by ID; a missing ID emits localized feedback followed by `NavigateBack`.

Define the feature-owned key before the ViewModel so the feature never imports from `app`:

```kotlin
@Serializable
data class TaskEditorKey(val taskId: Int?, val initialDueAt: Long?) : NavKey
```

- [ ] **Step 3: Implement editor events and save flow**

Events update each field, add/rename/toggle/reorder/delete subtasks, clear scheduling values, and save. Map `TaskValidationError` to inline field errors. When enabling a reminder, emit `RequestNotificationPermission` on API 33+ and `RequestExactAlarmAccess` when exact access is absent; denial retains the requested reminder and permits inexact fallback.

- [ ] **Step 4: Build the editor UI**

Use Material 3 text fields, exposed dropdown menus, date/time pickers, recurrence segmented/menu control, optional end-date picker, and a stable lazy subtask editor. Add/update is an explicit app-bar command. Show scheduling status without hiding saved data. Every icon button has localized semantics.

- [ ] **Step 5: Run editor tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*TaskEditorViewModelTest' :app:compileDebugAndroidTestKotlin :app:assembleDebug`

Expected: tests cover create/edit, missing ID, restoration, every validation error, subtask ordering, permission effects, save success, database failure, and scheduling failure.

- [ ] **Step 6: Commit the editor**

```bash
git add app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/editor app/src/test app/src/androidTest app/src/main/res
git commit -m "feat: add scheduled task editor"
```

### Task 9: Category Management Vertical Slice

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/category/presentation/CategoryContract.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/category/presentation/CategoryViewModel.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/category/presentation/CategoryRoute.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/category/presentation/CategoryScreen.kt`
- Create: `app/src/test/java/com/indiewalkabout/nowdothis/feature/category/presentation/CategoryViewModelTest.kt`
- Create: `app/src/androidTest/java/com/indiewalkabout/nowdothis/feature/category/presentation/CategoryScreenTest.kt`

**Interfaces:**
- Consumes: `CategoryRepository` and localized default-category resolver.
- Produces: category CRUD/reorder UI, confirmation flow, and localized display of untouched defaults.

- [ ] **Step 1: Test default rename and deletion behavior**

```kotlin
@Test
fun renameDefaultCategory_sendsCustomNameAndClearsDefaultKey() = runTest {
    val viewModel = createViewModel(defaultWorkCategory())
    viewModel.onEvent(CategoryEvent.ConfirmRename(id = 1, name = "Clienti"))
    advanceUntilIdle()
    assertEquals(CategoryRename(1, "Clienti", clearDefaultKey = true), repository.lastRename)
}
```

- [ ] **Step 2: Implement contract and ViewModel**

State contains ordered categories, active dialog, editable name, selected color, and typed name errors. Events cover add, rename, recolor, move, request delete, confirm delete, dismiss dialog, and retry. Map `DefaultCategoryKey` to `R.string.category_work`, `category_personal`, and `category_wishlist` only in presentation.

- [ ] **Step 3: Build category management UI**

Render one un-nested list surface with drag/reorder controls, color swatches, edit and delete icon buttons, and Material confirmation dialogs. Explain that deletion moves tasks to Uncategorized. Keep color tokens limited to an accessible predefined palette.

- [ ] **Step 4: Verify and commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*CategoryViewModelTest' :app:compileDebugAndroidTestKotlin :app:assembleDebug`

```bash
git add app/src/main/java/com/indiewalkabout/nowdothis/feature/category app/src/test app/src/androidTest app/src/main/res
git commit -m "feat: add category management"
```

### Task 10: Calendar Vertical Slice

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/calendar/domain/usecase/ObserveCalendar.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/calendar/presentation/CalendarContract.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/calendar/presentation/CalendarViewModel.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/calendar/presentation/CalendarRoute.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/calendar/presentation/CalendarScreen.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/calendar/presentation/MonthGrid.kt`
- Create: `app/src/test/java/com/indiewalkabout/nowdothis/feature/calendar/CalendarViewModelTest.kt`
- Create: `app/src/androidTest/java/com/indiewalkabout/nowdothis/feature/calendar/CalendarScreenTest.kt`

**Interfaces:**
- Consumes: `TaskScheduleReader`, `AppClock`, `ZoneIdProvider`.
- Produces: month/day calendar state, task indicators, date-prefilled editor effect, and task-editor navigation effect.

- [ ] **Step 1: Test month and selected-day boundaries**

```kotlin
@Test
fun selectDate_observesThatLocalDayAndPrefillsFab() = runTest {
    val viewModel = createCalendar(zone = ZoneId.of("Europe/Rome"))
    viewModel.onEvent(CalendarEvent.SelectDate(LocalDate.of(2025, 12, 16)))
    advanceUntilIdle()
    assertEquals(LocalDate.of(2025, 12, 16), viewModel.uiState.value.selectedDate)
    viewModel.onEvent(CalendarEvent.AddTask)
    assertEquals(16, Instant.ofEpochMilli(viewModel.effects.first().initialDueAt).atZone(ROME).dayOfMonth)
}
```

- [ ] **Step 2: Implement use case and ViewModel**

Calculate visible-month and selected-day epoch bounds in the injected zone. Combine month summaries and selected-day tasks into `CalendarUiState`. Events cover previous month, next month, today, date selection, add task, and open task.

- [ ] **Step 3: Build calendar UI**

Use a seven-column stable grid with locale-aware weekday labels, month controls, selected/today states, and non-color-only task indicators. Render the selected day task list below. The FAB emits `OpenEditor(taskId = null, initialDueAt = selectedDateStart)`.

- [ ] **Step 4: Verify and commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*CalendarViewModelTest' :app:compileDebugAndroidTestKotlin :app:assembleDebug`

```bash
git add app/src/main/java/com/indiewalkabout/nowdothis/feature/calendar app/src/test app/src/androidTest app/src/main/res
git commit -m "feat: add task calendar"
```

### Task 11: Completion History Vertical Slice

**Files:**
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/history/domain/usecase/ObserveCompletionHistory.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/history/presentation/HistoryContract.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/history/presentation/HistoryViewModel.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/history/presentation/HistoryRoute.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/history/presentation/HistoryScreen.kt`
- Create: `app/src/test/java/com/indiewalkabout/nowdothis/feature/history/HistoryViewModelTest.kt`
- Create: `app/src/androidTest/java/com/indiewalkabout/nowdothis/feature/history/HistoryScreenTest.kt`

**Interfaces:**
- Consumes: `CompletionHistoryReader`, `TaskRepository.deleteCompleted`, categories, clock, and zone.
- Produces: date-grouped older history, search/category filters, task inspection, and confirmed permanent deletion.

- [ ] **Step 1: Test exclusion of today's completions**

```kotlin
@Test
fun history_usesStartOfTodayAsExclusiveUpperBound() = runTest {
    val viewModel = createHistory(now = ROME_NOON)
    advanceUntilIdle()
    assertEquals(startOfRomeDay(ROME_NOON), historyReader.lastBefore)
}
```

- [ ] **Step 2: Implement history state and behavior**

State contains date-grouped tasks, categories, query, selected category, pending-delete task, and loading/error state. Events cover search, category filter, inspect, request delete, confirm delete, dismiss, and retry. Do not expose a reopen action.

- [ ] **Step 3: Build and verify history UI**

Render lazy date headers, completed task rows, search, category chips, empty state, and permanent-delete confirmation. Opening a row displays a read-only Material 3 modal bottom sheet inside the history feature; the sheet exposes only close and delete commands.

Run: `./gradlew :app:testDebugUnitTest --tests '*HistoryViewModelTest' :app:compileDebugAndroidTestKotlin :app:assembleDebug`

- [ ] **Step 4: Commit history**

```bash
git add app/src/main/java/com/indiewalkabout/nowdothis/feature/history app/src/test app/src/androidTest app/src/main/res
git commit -m "feat: add completion history"
```

### Task 12: Navigation 3 Integration And Legacy Removal

**Files:**
- Replace: `app/src/main/java/com/indiewalkabout/nowdothis/app/navigation/Navigation.kt` with `AppNavigation.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/app/MainActivity.kt`
- Modify: `app/src/main/java/com/indiewalkabout/nowdothis/app/ToDoApplication.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/navigation/TaskListKey.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/category/navigation/CategoryManagementKey.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/calendar/navigation/CalendarKey.kt`
- Create: `app/src/main/java/com/indiewalkabout/nowdothis/feature/history/navigation/CompletionHistoryKey.kt`
- Delete: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/navigation/Screens.kt`
- Delete: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/navigation/ListComposable.kt`
- Delete: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/navigation/TaskComposable.kt`
- Delete: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/SharedViewModel.kt`
- Delete: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/SearchAppBarState.kt`
- Delete: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/detail/`
- Delete: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/list/EmptyContent.kt`
- Delete: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/list/ListAppBar.kt`
- Delete: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/list/ListContent.kt`
- Delete: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/presentation/list/ListScreen.kt`
- Delete: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/model/ToDoTask.kt`
- Delete: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/model/Priority.kt`
- Delete: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/domain/model/Action.kt`
- Delete: compatibility `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/repository/ToDoRepository.kt`
- Delete: `app/src/main/java/com/indiewalkabout/nowdothis/feature/task/data/repository/DataStoreRepository.kt`
- Create: `app/src/androidTest/java/com/indiewalkabout/nowdothis/app/navigation/AppNavigationTest.kt`

**Interfaces:**
- Consumes: All feature routes and their ViewModels.
- Produces: final Navigation 3 graph, two-destination root scaffold, destination-scoped Hilt ViewModels, notification deep-link handling, and no legacy global state.

- [ ] **Step 1: Define serializable navigation keys**

```kotlin
@Serializable data object TaskListKey : NavKey
@Serializable data object CalendarKey : NavKey
@Serializable data object CategoryManagementKey : NavKey
@Serializable data object CompletionHistoryKey : NavKey
```

- [ ] **Step 2: Add NavEntry state and ViewModel decorators**

Configure `NavDisplay.entryDecorators` in this order:

```kotlin
listOf(
    rememberSaveableStateHolderNavEntryDecorator(),
    rememberViewModelStoreNavEntryDecorator()
)
```

Obtain regular feature ViewModels with `hiltViewModel()`. Obtain `TaskEditorViewModel` with its assisted factory and the current `TaskEditorKey`. Pop the editor after successful save; repository flows update the destination underneath.

- [ ] **Step 3: Implement root navigation and notification routing**

Tasks and Calendar appear in a Material 3 navigation bar. Editor, category management, and history cover the root scaffold and hide the navigation bar. `MainActivity.onCreate` and `onNewIntent` pass `OPEN_TASK` task IDs to a small navigation intent flow; consume each ID once and push `TaskEditorKey(taskId, null)`.

- [ ] **Step 4: Remove legacy state and UI**

Remove the activity-wide `SharedViewModel`, action-through-route database mutations, Room-annotated domain model, Compose-dependent priority enum, old list/detail screens, and compatibility repository. Move priority colors into a presentation mapper under `core/designsystem`. Confirm `rg "SharedViewModel|ToDoTask|Action.NO_ACTION|Priority\.NONE" app/src/main` returns no matches.

- [ ] **Step 5: Test navigation and build**

Add tests for every key, back behavior, Tasks/Calendar switching, editor arguments, missing IDs, category/history entry, save pop, and notification task ID routing.

Run: `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug`

Expected: all targets pass with no legacy symbols.

- [ ] **Step 6: Commit integration**

```bash
git add app/src/main
git add -u app/src/main
git add app/src/androidTest
git commit -m "refactor: integrate clean feature navigation"
```

### Task 13: End-To-End Verification, Accessibility, And Release Cleanup

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: affected Compose files under `feature/*/presentation/`
- Create: `app/src/androidTest/java/com/indiewalkabout/nowdothis/CoreTaskJourneyTest.kt`
- Modify: `README.md`

**Interfaces:**
- Consumes: Complete integrated application.
- Produces: full localized journey coverage, accessibility cleanup, passing lint/tests/build, and documented manual verification.

- [ ] **Step 1: Add one end-to-end Compose journey**

```kotlin
@Test
fun createCompleteRecurringTask_thenBrowseCalendarAndHistory() {
    launchAppWithItalianLocale()
    val firstDue = LocalDate.now().plusDays(1)
    val nextDue = firstDue.plusMonths(1)
    createCategory("Clienti")
    createTask(
        title = "Invia rapporto",
        dueDate = localizedDate(firstDue),
        recurrence = "Mensile",
        subtasks = listOf("Controlla dati")
    )
    completeTask("Invia rapporto")
    assertTaskExists("Invia rapporto", section = "Completate oggi")
    openCalendar()
    assertSelectedDateHasTask(localizedDate(nextDue), "Invia rapporto")
    seedCompletedTask("Archiviata", completedAt = startOfTodayMillis() - 1)
    openHistory()
    assertTaskExists("Archiviata")
}
```

- [ ] **Step 2: Run translation and hardcoded-text scans**

Run:

```bash
./gradlew :app:lintDebug
rg -n 'Text\("|contentDescription\s*=\s*"|Toast\.makeText\([^,]+,\s*"' app/src/main/java
```

Expected: lint reports no missing translations; the ripgrep command returns no user-facing literals.

- [ ] **Step 3: Audit semantics and responsive layout**

Verify all icon-only controls have localized content descriptions, category/priority states include text or icons in addition to color, task rows expose checkbox state, touch targets are at least 48 dp, and 200% font scale does not clip task rows, editor controls, calendar cells, dialogs, or navigation labels. Add Compose assertions for the task checkbox, calendar selected date, category color label, and reminder scheduling status.

- [ ] **Step 4: Run the full automated gate**

Run:

```bash
./gradlew clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

Expected: every unit, migration, DAO, Compose, navigation, and journey test passes; lint and debug assembly pass.

- [ ] **Step 5: Perform the manual platform matrix**

On API 23, verify Italian/English resources, create/edit/delete/undo, recurrence, calendar, and process recreation. On API 33 or newer, additionally verify notification denial, notification grant, exact-alarm denial with inexact fallback, exact-alarm grant, notification deep link, reboot reconciliation, and Android per-app language selection. Record the checked API levels and outcomes in the README verification section.

- [ ] **Step 6: Commit release cleanup**

```bash
git add app/src README.md
git commit -m "test: verify core task expansion journeys"
```

## Reference Documentation

- Navigation 3 destination ViewModels: <https://developer.android.com/guide/navigation/navigation-3/recipes/passingarguments>
- Navigation 3 entry decorators: <https://developer.android.com/guide/navigation/navigation-3/naventrydecorators>
- Room migration testing: <https://developer.android.com/training/data-storage/room/migrating-db-versions>
- Android alarm scheduling: <https://developer.android.com/develop/background-work/services/alarms>
- Notification runtime permission: <https://developer.android.com/develop/ui/compose/notifications/notification-permission>
- Per-app languages: <https://developer.android.com/guide/topics/resources/app-languages>
