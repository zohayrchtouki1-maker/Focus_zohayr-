package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FocusRepository
import com.example.data.SeedData
import com.example.model.SmartListType
import com.example.model.TaskSortOption
import com.example.ui.components.TaskFilterUtils
import com.example.viewmodel.NavDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Focus To-Do", appName)
    }

    @Test
    fun `seed data and JSON export import roundtrip`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FocusRepository(context)
        val initial = SeedData.createInitialState()

        assertTrue(initial.tasks.isNotEmpty())
        assertTrue(initial.projects.isNotEmpty())
        assertTrue(initial.focusRecords.isNotEmpty())

        val json = repo.exportJson(initial)
        val parsed = repo.parseBackupJson(json)
        assertNotNull(parsed)
        assertEquals(initial.tasks.size, parsed?.tasks?.size)
        assertEquals(initial.projects.size, parsed?.projects?.size)
        assertEquals(initial.forestState.sunlightPoints, parsed?.forestState?.sunlightPoints)
    }

    @Test
    fun `smart list filtering returns today active and completed tasks`() {
        val initial = SeedData.createInitialState()
        val (activeToday, completedToday) = TaskFilterUtils.filterTasks(
            initial.tasks,
            NavDestination.SmartList(SmartListType.TODAY),
            TaskSortOption.PRIORITY
        )
        assertTrue(activeToday.isNotEmpty())
        assertTrue(completedToday.isNotEmpty())
        assertTrue(activeToday.all { !it.isCompleted })
        assertTrue(completedToday.all { it.isCompleted })
    }
}
