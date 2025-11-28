package systems.fehn.intellijdirenv.gradle

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionContext
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelperExtension
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import systems.fehn.intellijdirenv.services.EnvironmentService

/**
 * Extension that injects direnv-loaded environment variables into ALL Gradle operations.
 *
 * This is a global hook that intercepts both sync and execution operations,
 * ensuring environment variables are available regardless of the code path used.
 *
 * Uses GradleExecutionHelperExtension (IntelliJ 2024.2+) which provides a stable API
 * via configureSettings() that is called for all Gradle operations.
 */
class DirenvGradleExecutionHelperExtension : GradleExecutionHelperExtension {
    override fun configureSettings(
        settings: GradleExecutionSettings,
        context: GradleExecutionContext
    ) {
        val envService = ApplicationManager.getApplication()
            .getService(EnvironmentService::class.java)
        val loadedVariables = envService.getLoadedVariables()

        if (loadedVariables.isNotEmpty()) {
            val currentEnv = settings.env.toMutableMap()
            currentEnv.putAll(loadedVariables)
            settings.withEnvironmentVariables(currentEnv)
        }
    }
}
