package io.github.byscrimm.yii2insight.ui.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import io.github.byscrimm.yii2insight.common.YiiApplicationUtils;
import io.github.byscrimm.yii2insight.utils.Yii2InsightSettings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SettingsForm implements Configurable {
    private JPanel mainPanel;
    private TextFieldWithBrowseButton yiiRootPath;
    private JLabel yiiRootPathLabel;
    final private Project myProject;
    final private Yii2InsightSettings settings;

    public SettingsForm(Project project) {
        myProject = project;
        settings = Yii2InsightSettings.getInstance(project);

        mainPanel = new JPanel(new java.awt.BorderLayout(0, 12));
        yiiRootPath = new TextFieldWithBrowseButton();
        yiiRootPathLabel = new JLabel("Yii root directory:");
        JPanel row = new JPanel(new java.awt.BorderLayout(12, 0));
        row.add(yiiRootPathLabel, java.awt.BorderLayout.WEST);
        row.add(yiiRootPath, java.awt.BorderLayout.CENTER);
        mainPanel.add(row, java.awt.BorderLayout.NORTH);
        yiiRootPathLabel.setLabelFor(yiiRootPath.getTextField());
        yiiRootPath.setButtonEnabled(true);
        FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false);
        yiiRootPath.addBrowseFolderListener(new TextBrowseFolderListener(fileChooserDescriptor));
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "Yii2 Insight";
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        return !yiiRootPath.getText().trim().equals(StringUtil.notNullize(settings.yiiRootPath));
    }

    @Override
    public void apply() {
        settings.yiiRootPath = StringUtil.nullize(yiiRootPath.getText().trim());

        YiiApplicationUtils.resetYiiRootPath(myProject);
    }

    @Override
    public void reset() {
        yiiRootPath.setText(StringUtil.notNullize(settings.yiiRootPath));
    }

    @Override
    public void disposeUIResources() {

    }
}
