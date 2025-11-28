package systems.fehn.intellijdirenv

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import systems.fehn.intellijdirenv.services.DirenvProjectService

class DirenvImportAction : AnAction(MyBundle.message("importDirenvAction")) {
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        when (e.place) {
            ActionPlaces.MAIN_TOOLBAR -> e.presentation.isEnabledAndVisible = true
            ActionPlaces.PROJECT_VIEW_POPUP -> {
                // Show action only when right-clicking on a .envrc file
                val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
                e.presentation.isEnabledAndVisible = virtualFile?.name == ".envrc"
            }

            else -> e.presentation.isEnabledAndVisible = false
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.getService(DirenvProjectService::class.java)

        when (e.place) {
            ActionPlaces.PROJECT_VIEW_POPUP -> {
                // Use the selected .envrc file
                val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
                if (virtualFile != null) {
                    service.importDirenv(virtualFile)
                }
            }
            else -> {
                // Toolbar: use the project's .envrc file
                val envrcFile = service.projectEnvrcFile
                if (envrcFile != null) {
                    service.importDirenv(envrcFile)
                } else {
                    notificationGroup
                        .createNotification(
                            MyBundle.message("noTopLevelEnvrcFileFound"),
                            "",
                            NotificationType.ERROR,
                        )
                        .notify(project)
                }
            }
        }
    }
}
