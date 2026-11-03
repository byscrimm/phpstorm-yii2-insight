package io.github.byscrimm.yii2insight.migrations.actions;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.content.Content;
import io.github.byscrimm.yii2insight.common.YiiApplicationUtils;
import io.github.byscrimm.yii2insight.migrations.commands.CommandBase;
import io.github.byscrimm.yii2insight.migrations.entities.DefaultMigrateCommand;
import io.github.byscrimm.yii2insight.migrations.entities.MigrateCommand;
import io.github.byscrimm.yii2insight.migrations.entities.Migration;
import io.github.byscrimm.yii2insight.migrations.entities.MigrationStatus;
import io.github.byscrimm.yii2insight.migrations.ui.toolWindow.ConsolePanel;
import io.github.byscrimm.yii2insight.migrations.ui.toolWindow.MigrationPanel;
import io.github.byscrimm.yii2insight.migrations.ui.toolWindow.MigrationsToolWindowFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

abstract class MigrateBaseAction extends AnActionButton {
    MigrateBaseAction(String name, Icon icon) {
        super(name, icon);
    }

    @NotNull
    MigrationPanel getPanel() {
        return (MigrationPanel) getContextComponent();
    }

    @NotNull
    JTree getTree() {
        return getPanel().getTree();
    }

    @Nullable
    DefaultMutableTreeNode getSelectedNode() {
        JTree tree = getTree();

        if (tree.getSelectionModel().getSelectionCount() > 0) {
            TreePath leadSelectionPath = tree.getLeadSelectionPath();
            if (leadSelectionPath == null) {
                return null;
            }

            return (DefaultMutableTreeNode) leadSelectionPath.getLastPathComponent();
        }

        return null;
    }

    void executeCommand(Project project, CommandBase ...command) {
        executeCommand(project, Arrays.asList(command));
    }

    void executeCommand(Project project, List<CommandBase> commands) {
        ToolWindow window = ToolWindowManager
                .getInstance(project).getToolWindow(MigrationsToolWindowFactory.TOOL_WINDOW_ID);

        if (window != null) {
            Content content = window.getContentManager().getContent(1);
            if (content != null) {
                ConsolePanel consolePanel = (ConsolePanel) content.getComponent();
                for (CommandBase command : commands) {
                    command.setConsoleView(consolePanel.getConsoleView());
                }
            }
        }

        Application application = ApplicationManager.getApplication();
        for (CommandBase command : commands) {
            command.repaintComponent(getTree());
            command.setApplication(application);
        }

        new com.intellij.openapi.progress.Task.Backgroundable(project, "Running Yii migrations", true) {
            @Override
            public void run(@org.jetbrains.annotations.NotNull com.intellij.openapi.progress.ProgressIndicator indicator) {
                for (CommandBase command : commands) {
                    if (indicator.isCanceled() || project.isDisposed()) return;
                    command.setIndicator(indicator);
                    command.run();
                }
            }
        }.queue();
    }

    boolean enableDownButtons(DefaultMutableTreeNode treeNode) {
        Object userObject = treeNode.getUserObject();
        if (userObject instanceof Migration) {
            MigrationStatus status = ((Migration) userObject).status;
            return status == MigrationStatus.Success || status == MigrationStatus.RollbackError;
        }

        if (userObject instanceof DefaultMigrateCommand) {
            return false;
        }

        if (userObject instanceof String || userObject instanceof MigrateCommand) {
            Enumeration migrationEnumeration = treeNode.children();
            while (migrationEnumeration.hasMoreElements()) {
                Object migration = ((DefaultMutableTreeNode) migrationEnumeration.nextElement()).getUserObject();
                if (migration instanceof Migration) {
                    MigrationStatus status = ((Migration) migration).status;
                    if (status == MigrationStatus.Success || status == MigrationStatus.RollbackError) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    boolean enableUpButtons(DefaultMutableTreeNode treeNode) {
        Object userObject = treeNode.getUserObject();
        if (userObject instanceof Migration) {
            MigrationStatus status = ((Migration) userObject).status;
            return ((Migration) userObject).status != MigrationStatus.Success && status != MigrationStatus.RollbackError;
        }

        if (userObject instanceof DefaultMigrateCommand) {
            return false;
        }

        if (userObject instanceof String || userObject instanceof MigrateCommand) {
            Enumeration migrationEnumeration = treeNode.children();
            while (migrationEnumeration.hasMoreElements()) {
                Object migration = ((DefaultMutableTreeNode) migrationEnumeration.nextElement()).getUserObject();
                if (migration instanceof Migration) {
                    MigrationStatus status = ((Migration) migration).status;
                    if (status != MigrationStatus.Success && status != MigrationStatus.RollbackError) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @NotNull
    MigrateCommand getCommand(@NotNull DefaultMutableTreeNode node) {
        if (node.getUserObject() instanceof MigrateCommand) {
            return (MigrateCommand) node.getUserObject();
        }

        return getCommand((DefaultMutableTreeNode) node.getParent());
    }

    @Nullable
    String getMigrationPath(Project project, TreeNode node) {
        String projectRoot = YiiApplicationUtils.getYiiRootPath(project) + "/";
        Object userObject = ((DefaultMutableTreeNode) node).getUserObject();
        if (userObject instanceof MigrateCommand) {
            List<String> paths = new ArrayList<>();
            for (String s : ((MigrateCommand) userObject).migrationPath) {
                String preparePath = preparePath(s, projectRoot);
                paths.add(preparePath);
            }

            return StringUtil.join(paths, ",");
        }

        if (userObject instanceof String) {
            return preparePath((String) userObject, projectRoot);
        }

        return null;
    }

    private String preparePath(String path, String projectRoot) {
        if (path.startsWith("@") || path.startsWith("/")) {
            return path;
        }

        if (path.length() > 1 && path.charAt(1) == ':') {
            return path;
        }

        return projectRoot + path;
    }
}
