package systems.fehn.intellijdirenv.services

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import systems.fehn.intellijdirenv.MyBundle
import systems.fehn.intellijdirenv.notificationGroup
import systems.fehn.intellijdirenv.settings.DirenvSettingsState
import systems.fehn.intellijdirenv.switchNull
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class DirenvProjectService(private val project: Project) {
    private val logger by lazy { logger<DirenvProjectService>() }

    private val projectDir: VirtualFile? = run {
        // Try basePath first (more reliable for WSL projects)
        val basePath = project.basePath
        if (basePath != null) {
            val vfs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            val dir = vfs.findFileByPath(basePath)
            if (dir != null) {
                return@run dir
            }
        }

        // Fallback to guessProjectDir
        project.guessProjectDir().also {
            if (it == null) {
                logger.warn("Could not determine project dir of project ${project.name}")
            }
        }
    }

    val projectEnvrcFile: VirtualFile?
        get() = findEnvrcInParents(projectDir)
            .switchNull(
                onNull = { logger.trace { "Project ${project.name} contains no .envrc file" } },
                onNonNull = { logger.trace { "Project ${project.name} has .envrc file ${it.path}" } },
            )

    /**
     * Search for .envrc file in the project directory and up to 1 parent directory.
     * Uses java.io.File to avoid triggering IntelliJ VFS scans.
     * For WSL projects, ensures we never leave the WSL filesystem.
     */
    private fun findEnvrcInParents(dir: VirtualFile?): VirtualFile? {
        if (dir == null) return null

        val basePath = dir.path
        val isWslProject = basePath.startsWith("//wsl")

        // Use java.io.File to search without triggering VFS operations
        var currentPath = java.io.File(basePath)
        var depth = 0
        val maxDepth = 2  // Project dir + 1 parent max

        while (depth < maxDepth) {
            val path = currentPath.absolutePath

            // Stop at filesystem root
            if (path == "/" || path.matches(Regex("^[A-Z]:[\\\\/]?$"))) {
                break
            }

            // For WSL projects: stop at distribution root
            if (isWslProject && path.replace('\\', '/').matches(Regex("^//wsl[^/]*/[^/]+/?$"))) {
                break
            }

            // For WSL projects: never leave WSL filesystem
            if (isWslProject && !path.replace('\\', '/').startsWith("//wsl")) {
                break
            }

            // Check if .envrc exists using java.io.File (no VFS)
            val envrcFile = java.io.File(currentPath, ".envrc")
            if (envrcFile.exists() && envrcFile.isFile) {
                // Only now use VFS to get the VirtualFile
                val vfs = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                return vfs.findFileByPath(envrcFile.absolutePath)
            }

            val parent = currentPath.parentFile ?: break
            currentPath = parent
            depth++
        }
        return null
    }

    private val envService by lazy { ApplicationManager.getApplication().getService(EnvironmentService::class.java) }

    private val jsonFactory by lazy { JsonFactory() }

    fun importDirenv(envrcFile: VirtualFile, notifyNoChange: Boolean = true) {
        // Run blocking operation on a pooled thread to avoid EDT blocking
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val process = executeDirenv(envrcFile, "export", "json")

                // Read stdout BEFORE waitFor() to prevent deadlock
                // direnv only terminates after its output has been read
                val output = process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)

                val completed = process.waitFor(30, TimeUnit.SECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    logger.error("Direnv process timed out after 30 seconds")
                    notificationGroup
                        .createNotification(
                            MyBundle.message("errorDuringDirenv"),
                            "Process timed out",
                            NotificationType.ERROR,
                        ).notify(project)
                    return@executeOnPooledThread
                }

                if (process.exitValue() != 0) {
                    handleDirenvError(process, envrcFile)
                    return@executeOnPooledThread
                }

                jsonFactory.createParser(output).use { parser ->

                    try {
                        val didWork = handleDirenvOutput(parser)

                        if (didWork) {
                            notificationGroup
                                .createNotification(
                                    MyBundle.message("executedSuccessfully"),
                                    "",
                                    NotificationType.INFORMATION,
                                ).notify(project)
                        } else if (notifyNoChange) {
                            notificationGroup
                                .createNotification(
                                    MyBundle.message("alreadyUpToDate"),
                                    "",
                                    NotificationType.INFORMATION,
                                ).notify(project)
                        }
                    } catch (e: EnvironmentService.ManipulateEnvironmentException) {
                        notificationGroup
                            .createNotification(
                                MyBundle.message("exceptionNotification"),
                                e.localizedMessage,
                                NotificationType.ERROR,
                            ).notify(project)
                    }
                }
            } catch (e: Exception) {
                logger.error("importDirenv: Exception occurred", e)
                notificationGroup
                    .createNotification(
                        MyBundle.message("errorDuringDirenv"),
                        e.message ?: "Unknown error",
                        NotificationType.ERROR,
                    ).notify(project)
            }
        }
    }

    private fun handleDirenvOutput(parser: JsonParser): Boolean {
        var didWork = false

        while (parser.nextToken() != null) {
            if (parser.currentToken == JsonToken.FIELD_NAME) {
                when (parser.nextToken()) {
                    JsonToken.VALUE_NULL -> envService.unsetVariable(parser.currentName)
                    JsonToken.VALUE_STRING -> envService.setVariable(parser.currentName, parser.valueAsString)

                    else -> continue
                }

                didWork = true
                logger.trace { "Set variable ${parser.currentName} to ${parser.valueAsString}" }
            }
        }

        return didWork
    }

    private fun handleDirenvError(process: Process, envrcFile: VirtualFile) {
        val error = process.errorStream.readAllBytes().toString(StandardCharsets.UTF_8)

        val notification = if (error.contains(" is blocked")) {
            notificationGroup
                .createNotification(
                    MyBundle.message("envrcNotYetAllowed"),
                    "",
                    NotificationType.WARNING,
                )
                .addAction(
                    NotificationAction.create(MyBundle.message("allow")) { _, notification ->
                        notification.hideBalloon()
                        // Run on pooled thread to avoid blocking EDT
                        ApplicationManager.getApplication().executeOnPooledThread {
                            executeDirenv(envrcFile, "allow").waitFor(10, TimeUnit.SECONDS)
                            importDirenv(envrcFile)
                        }
                    },
                )
        } else {
            logger.error(error)

            notificationGroup
                .createNotification(
                    MyBundle.message("errorDuringDirenv"),
                    "",
                    NotificationType.ERROR,
                )
        }

        notification
            .addAction(
                NotificationAction.create(MyBundle.message("openEnvrc")) { _, it ->
                    it.hideBalloon()
                    // Must run on EDT for file operations
                    ApplicationManager.getApplication().invokeLater {
                        FileEditorManager.getInstance(project).openFile(envrcFile, true, true)
                    }
                },
            )
            .notify(project)
    }

    private fun executeDirenv(envrcFile: VirtualFile, vararg args: String): Process {
        val workingDir = envrcFile.parent.path
        val appSettings = DirenvSettingsState.getInstance()

        val direnvPath = if (appSettings.direnvSettingsPath.isNotEmpty()) {
            appSettings.direnvSettingsPath
        } else {
            "direnv"
        }

        // Try to detect WSL path (\\wsl.localhost\... or \\wsl$\...)
        val wslPath = WslPath.parseWindowsUncPath(workingDir)
        val wslDistribution: WSLDistribution? = wslPath?.distribution
            ?: WslPath.getDistributionByWindowsUncPath(workingDir)

        val cli = if (wslDistribution != null) {
            val linuxWorkingDir = wslPath?.linuxPath
                ?: workingDir.replace('\\', '/')

            val options = WSLCommandLineOptions()
                .setRemoteWorkingDirectory(linuxWorkingDir)
                .setLaunchWithWslExe(true)
                .setExecuteCommandInDefaultShell(true)

            val commandLine = GeneralCommandLine(direnvPath, *args)
            wslDistribution.patchCommandLine(commandLine, project, options)
        } else {
            GeneralCommandLine(direnvPath, *args)
                .withWorkDirectory(workingDir)
        }

        return cli.createProcess()
    }
}
