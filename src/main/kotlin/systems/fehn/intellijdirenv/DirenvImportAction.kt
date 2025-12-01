package systems.fehn.intellijdirenv

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import systems.fehn.intellijdirenv.services.DirenvProjectService

class DirenvImportAction : AnAction(MyBundle.message("importDirenvAction")) {
    private val logger = logger<DirenvImportAction>()

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)

        // Check if we're in a popup menu context (right-click)
        val isPopupMenu = e.place.contains("Popup", ignoreCase = true)

        if (isPopupMenu) {
            // Show action only when right-clicking on a .envrc file
            val isEnvrc = virtualFile?.name == ".envrc"
            e.presentation.isEnabledAndVisible = isEnvrc
        } else {
            // Always visible in toolbar, menus, and keyboard shortcuts
            e.presentation.isEnabledAndVisible = true
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.getService(DirenvProjectService::class.java)

        // Check if a .envrc file is selected (right-click context)
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile?.name == ".envrc") {
            service.importDirenv(virtualFile)
            return
        }

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
