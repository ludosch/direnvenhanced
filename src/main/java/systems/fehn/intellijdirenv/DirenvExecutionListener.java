package systems.fehn.intellijdirenv;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import systems.fehn.intellijdirenv.services.DirenvProjectService;
import systems.fehn.intellijdirenv.settings.DirenvSettingsState;

class DirenvExecutionListener implements ExecutionListener {
    private static final Logger LOG = Logger.getInstance(DirenvExecutionListener.class);

    @Override
    public void processStarting(@NotNull String executorId, @NotNull ExecutionEnvironment env) {
        LOG.info("DirenvExecutionListener.processStarting called for executor: " + executorId);

        if (!DirenvSettingsState.getInstance().direnvSettingsImportEveryExecution) {
            LOG.info("Import on every execution is disabled, skipping");
            return;
        }

        Project project = env.getProject();
        DirenvProjectService service = project.getService(DirenvProjectService.class);
        VirtualFile envrcFile = service.getProjectEnvrcFile();
        LOG.info("Found .envrc file: " + (envrcFile != null ? envrcFile.getPath() : "null"));

        if (envrcFile != null) {
            // Synchronous, no notifications for Run Configuration
            service.importDirenv(envrcFile, true, false, false);
        }
    }
}
