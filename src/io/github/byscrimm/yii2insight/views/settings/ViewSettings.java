package io.github.byscrimm.yii2insight.views.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import io.github.byscrimm.yii2insight.utils.Yii2InsightSettings;
import io.github.byscrimm.yii2insight.views.util.ViewUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ViewSettings implements Configurable {
    private final Yii2InsightSettings mySettings;
    private Project myProject;
    private JPanel mainPanel;
    private JPanel viewPathMap;
    private JTextField defaultViewClass;
    private JComboBox defaultViewFileExt;
    private final List<Map.Entry<String, String>> currentThemePathMap;

    public ViewSettings(Project project) {
        myProject = project;
        mySettings = Yii2InsightSettings.getInstance(project);

        mainPanel = new JPanel(new java.awt.BorderLayout(0, 12));
        viewPathMap = new ThemePathMapPanel(project);
        defaultViewClass = new JTextField(30);
        defaultViewFileExt = new JComboBox<>(new String[]{"php", "twig", "tpl"});
        defaultViewFileExt.setEditable(true);
        JPanel fields = new JPanel(new java.awt.GridLayout(2, 2, 12, 8));
        fields.add(new JLabel("View class:")); fields.add(defaultViewClass);
        fields.add(new JLabel("View extension:")); fields.add(defaultViewFileExt);
        mainPanel.add(fields, java.awt.BorderLayout.NORTH);
        mainPanel.add(viewPathMap, java.awt.BorderLayout.CENTER);
        currentThemePathMap = new ArrayList<>(mySettings.viewPathMap.entrySet());
        ((ThemePathMapPanel) viewPathMap).setData(new ArrayList<>(mySettings.viewPathMap.entrySet()));

        defaultViewClass.setText(mySettings.defaultViewClass);
        defaultViewFileExt.getModel().setSelectedItem(mySettings.defaultViewExtension);
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "Views";
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
        return !mySettings.defaultViewClass.equals(defaultViewClass.getText())
                || !mySettings.defaultViewExtension.equals(defaultViewFileExt.getModel().getSelectedItem())
                || ((ThemePathMapPanel) viewPathMap).getData().hashCode() != currentThemePathMap.hashCode();
    }

    @Override
    public void apply() {
        if (((ThemePathMapPanel) viewPathMap).getData().hashCode() != currentThemePathMap.hashCode()
                || !mySettings.defaultViewExtension.equals(defaultViewFileExt.getModel().getSelectedItem())) {
            currentThemePathMap.clear();
            currentThemePathMap.addAll(((ThemePathMapPanel) viewPathMap).getData());
            mySettings.viewPathMap.clear();
            for (Map.Entry<String, String> entry : currentThemePathMap) {
                mySettings.viewPathMap.put(entry.getKey(), entry.getValue());
            }
            mySettings.defaultViewExtension = defaultViewFileExt.getModel().getSelectedItem().toString();

            ViewUtil.resetPathMapPatterns(myProject);
        }
        mySettings.defaultViewClass = defaultViewClass.getText();
    }

    @Override
    public void reset() {
        List<Map.Entry<String, String>> data = ((ThemePathMapPanel) viewPathMap).getData();
        data.clear();
        data.addAll(currentThemePathMap);
        viewPathMap.updateUI();

        defaultViewClass.setText(mySettings.defaultViewClass);
        defaultViewFileExt.getModel().setSelectedItem(mySettings.defaultViewExtension);
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
        viewPathMap = new ThemePathMapPanel(myProject);
    }

    @Override
    public void disposeUIResources() {
    }
}
