package com.itangcent.easyapi.core.ide.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.internal.threading.swingBlocking
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class OpenApiDashboardActionTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun tearDown() {
        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = true
        }
        super.tearDown()
    }

    fun testDisabledScanningStillOpensDashboard() {
        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = false
        }
        val openedProjects = mutableListOf<Project>()
        val action = OpenApiDashboardAction(openedProjects::add)

        swingBlocking {
            action.actionPerformed(event(project))
        }

        assertEquals("Disabled scanning should not block the dashboard entry point", listOf(project), openedProjects)
    }

    fun testEnabledScanningOpensDashboard() {
        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = true
        }
        val openedProjects = mutableListOf<Project>()
        val action = OpenApiDashboardAction(openedProjects::add)

        swingBlocking {
            action.actionPerformed(event(project))
        }

        assertEquals("Enabled scanning should open the dashboard", listOf(project), openedProjects)
    }

    fun testActionWithoutProjectDoesNotOpenDashboard() {
        var openCount = 0
        val action = OpenApiDashboardAction { openCount++ }

        swingBlocking {
            action.actionPerformed(event(null))
        }

        assertEquals("Action without a project should be a no-op", 0, openCount)
    }

    private fun event(eventProject: Project?): AnActionEvent = AnActionEvent.createFromDataContext(
        "test",
        Presentation(),
        DataContext { eventProject }
    )
}
