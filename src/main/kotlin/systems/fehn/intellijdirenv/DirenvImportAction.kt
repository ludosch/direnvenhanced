package systems.fehn.intellijdirenv

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
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
                // Show action if the project has an .envrc file (anywhere in parent dirs)
                val service = project.getService(DirenvProjectService::class.java)
                e.presentation.isEnabledAndVisible = service.projectEnvrcFile != null
            }

            else -> e.presentation.isEnabledAndVisible = false
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.getService(DirenvProjectService::class.java)

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
