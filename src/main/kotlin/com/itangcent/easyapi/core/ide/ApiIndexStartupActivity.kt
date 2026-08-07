package com.itangcent.easyapi.core.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.itangcent.easyapi.core.cache.api.ApiScanLifecycleController
import com.itangcent.easyapi.core.config.ConfigSyncService
import com.itangcent.easyapi.core.internal.threading.backgroundAsync
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Starts configuration synchronization and reconciles saved API scan state.
 *
 * Startup work is launched through the clean plugin background dispatcher so
 * downstream lifecycle and PSI operations never inherit an EDT context. In unit
 * tests this activity returns immediately; tests own the lifecycle explicitly and
 * must not inherit delayed background work from project startup.
 */
class ApiIndexStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode) return

        backgroundAsync {
            DumbModeHelper.waitForSmartMode(project)
            delay(5.seconds)
            if (project.isDisposed) return@backgroundAsync

            ConfigSyncService.getInstance(project).start()
            ApiScanLifecycleController.getInstance(project).reconcileInitial().await()
        }
    }
}
