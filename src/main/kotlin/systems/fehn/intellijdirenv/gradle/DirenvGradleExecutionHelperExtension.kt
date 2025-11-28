package systems.fehn.intellijdirenv.gradle

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
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
    private val logger = logger<DirenvGradleExecutionHelperExtension>()

    override fun configureSettings(
        settings: GradleExecutionSettings,
        context: GradleExecutionContext
    ) {
        logger.info("DirenvGradleExecutionHelperExtension.configureSettings called")

        try {
            val envService = ApplicationManager.getApplication()
                .getService(EnvironmentService::class.java)
            val loadedVariables = envService.getLoadedVariables()

            logger.info("Loaded ${loadedVariables.size} direnv variables")

            if (loadedVariables.isNotEmpty()) {
                val currentEnv = settings.env.toMutableMap()
                currentEnv.putAll(loadedVariables)
                settings.withEnvironmentVariables(currentEnv)
                logger.info("Injected direnv variables into Gradle settings")
            }
        } catch (e: Exception) {
            logger.error("Error in DirenvGradleExecutionHelperExtension", e)
        }
    }
}
