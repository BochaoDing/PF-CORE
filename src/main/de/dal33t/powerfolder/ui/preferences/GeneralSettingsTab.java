/*
* Copyright 2004 - 2008 Christian Sprajc. All rights reserved.
*
* This file is part of PowerFolder.
*
* PowerFolder is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation.
*
* PowerFolder is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with PowerFolder. If not, see <http://www.gnu.org/licenses/>.
*
* $Id$
*/
package de.dal33t.powerfolder.ui.preferences;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.File;
import java.util.Hashtable;
import java.util.Dictionary;

import javax.swing.*;

import com.jgoodies.binding.adapter.BasicComponentFactory;
import com.jgoodies.binding.adapter.PreferencesAdapter;
import com.jgoodies.binding.value.BufferedValueModel;
import com.jgoodies.binding.value.Trigger;
import com.jgoodies.binding.value.ValueHolder;
import com.jgoodies.binding.value.ValueModel;
import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import de.dal33t.powerfolder.*;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.ui.Icons;
import de.dal33t.powerfolder.ui.widget.JButtonMini;
import de.dal33t.powerfolder.util.StringUtils;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.os.OSUtil;
import de.dal33t.powerfolder.util.os.Win32.WinUtils;
import de.dal33t.powerfolder.util.ui.DialogFactory;
import de.dal33t.powerfolder.util.ui.GenericDialogType;

public class GeneralSettingsTab extends PFUIComponent implements PreferenceTab {


    private JPanel panel;
    private JTextField nickField;
    private JCheckBox createDesktopShortcutsBox;

    private JCheckBox startWithWindowsBox;

    private JTextField locationTF;
    private ValueModel locationModel;
    private JComponent locationField;

    private JCheckBox massDeleteBox;
    private JSlider massDeleteSlider;

    private JCheckBox useRecycleBinBox;

    private JCheckBox showAdvancedSettingsBox;
    private ValueModel showAdvancedSettingsModel;

    private JCheckBox backupOnlyClientBox;

    private JCheckBox usePowerFolderIconBox;
    private JCheckBox usePowerFolderLink;

    private boolean needsRestart;

    // The triggers the writing into core
    private Trigger writeTrigger;

    public GeneralSettingsTab(Controller controller) {
        super(controller);
        initComponents();
    }

    public String getTabName() {
        return Translation.getTranslation("preferences.dialog.general.title");
    }

    public boolean needsRestart() {
        return needsRestart;
    }

    public boolean validate() {
        return true;
    }

    // Exposing *************************************************************

    /**
     * TODO Move this into a <code>PreferencesModel</code>
     * 
     * @return the model containing the visibible-state of the advanced settings
     *         dialog
     */
    public ValueModel getShowAdvancedSettingsModel() {
        return showAdvancedSettingsModel;
    }

    /**
     * Initalizes all needed ui components
     */
    private void initComponents() {
        writeTrigger = new Trigger();

        showAdvancedSettingsModel = new ValueHolder(
            PreferencesEntry.SHOW_ADVANCED_SETTINGS
                .getValueBoolean(getController()));
        ValueModel backupOnlyClientModel = new ValueHolder(
                ConfigurationEntry.BACKUP_ONLY_CLIENT
                        .getValueBoolean(getController()));

        nickField = new JTextField(getController().getMySelf().getNick());

        // Local base selection
        locationModel = new ValueHolder(getController().getFolderRepository()
            .getFoldersBasedir());

        // Behavior
        locationModel.addValueChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                updateLocationComponents();
            }
        });

        locationField = createLocationField();

        showAdvancedSettingsBox = BasicComponentFactory.createCheckBox(
            showAdvancedSettingsModel, Translation
                .getTranslation("preferences.dialog.show_advanced"));

        backupOnlyClientBox = BasicComponentFactory.createCheckBox(
                backupOnlyClientModel, Translation
                .getTranslation("preferences.dialog.backup_only_clinet"));

        ValueModel urbModel = new ValueHolder(
            ConfigurationEntry.USE_RECYCLE_BIN.getValueBoolean(getController()));
        useRecycleBinBox = BasicComponentFactory.createCheckBox(
            new BufferedValueModel(urbModel, writeTrigger), Translation
                .getTranslation("preferences.dialog.use_recycle_bin"));

        ValueModel massDeleteModel = new ValueHolder(
                PreferencesEntry.MASS_DELETE_PROTECTION.getValueBoolean(getController()));
        massDeleteBox = BasicComponentFactory.createCheckBox(
            new BufferedValueModel(massDeleteModel, writeTrigger), Translation
                .getTranslation("preferences.dialog.use_mass_delete"));
        massDeleteBox.addItemListener(new MassDeleteItemListener());
        massDeleteSlider = new JSlider(20, 100, PreferencesEntry.
                MASS_DELETE_THRESHOLD.getValueInt(getController()));
        massDeleteSlider.setMajorTickSpacing(20);
        massDeleteSlider.setMinorTickSpacing(5);
        massDeleteSlider.setPaintTicks(true);
        massDeleteSlider.setPaintLabels(true);
        Dictionary<Integer, JLabel> dictionary = new Hashtable<Integer, JLabel>();
        for (int i = 20; i <= 100; i += massDeleteSlider.getMajorTickSpacing()) {
            dictionary.put(i, new JLabel(Integer.toString(i) + '%'));
        }
        massDeleteSlider.setLabelTable(dictionary);
        enableMassDeleteSlider();

        // Windows only...
        if (OSUtil.isWindowsSystem()) {

            ValueModel csModel = new PreferencesAdapter(getController()
                .getPreferences(), "createdesktopshortcuts", Boolean.TRUE);
            createDesktopShortcutsBox = BasicComponentFactory
                .createCheckBox(
                    new BufferedValueModel(csModel, writeTrigger),
                    Translation
                        .getTranslation("preferences.dialog.create_desktop_shortcuts"));

            if (WinUtils.getInstance() != null) {
                ValueModel startWithWindowsVM = new ValueHolder(WinUtils.getInstance()
                        .isPFStartup());
                startWithWindowsVM
                    .addValueChangeListener(new PropertyChangeListener() {
                        public void propertyChange(PropertyChangeEvent evt) {
                            try {
                                if (WinUtils.getInstance() != null) {
                                    WinUtils.getInstance().setPFStartup(
                                        evt.getNewValue().equals(true));
                                }
                            } catch (IOException e) {
                                logSevere("IOException", e);
                            }
                        }
                    });
                ValueModel tmpModel = new BufferedValueModel(
                        startWithWindowsVM, writeTrigger);
                startWithWindowsBox = BasicComponentFactory.createCheckBox(
                    tmpModel, Translation
                        .getTranslation("preferences.dialog.start_with_windows"));
            }

            if (OSUtil.isWindowsSystem()) {
                ValueModel pfiModel = new ValueHolder(
                    ConfigurationEntry.USE_PF_ICON
                        .getValueBoolean(getController()));
                usePowerFolderIconBox = BasicComponentFactory.createCheckBox(
                    new BufferedValueModel(pfiModel, writeTrigger), Translation
                        .getTranslation("preferences.dialog.use_pf_icon"));

                ValueModel pflModel = new ValueHolder(
                    ConfigurationEntry.USE_PF_LINK
                        .getValueBoolean(getController()));
                usePowerFolderLink = BasicComponentFactory.createCheckBox(
                    new BufferedValueModel(pflModel, writeTrigger), Translation
                        .getTranslation("preferences.dialog.show_pf_link"));
            }

        }
    }

    /**
     * Builds general ui panel
     */
    public JPanel getUIPanel() {
        if (panel == null) {
            FormLayout layout = new FormLayout(
                "right:pref, 3dlu, 140dlu, pref:grow",
                "pref, 3dlu, pref, 3dlu, pref, 3dlu, pref, 3dlu, pref, 3dlu, pref, 3dlu, pref");

            PanelBuilder builder = new PanelBuilder(layout);
            builder.setBorder(Borders
                .createEmptyBorder("3dlu, 3dlu, 3dlu, 3dlu"));

            CellConstraints cc = new CellConstraints();
            int row = 1;

            builder.add(new JLabel(Translation
                .getTranslation("preferences.dialog.nickname")), cc.xy(1, row));
            builder.add(nickField, cc.xy(3, row));

            row += 2;
            builder.add(new JLabel(Translation
                .getTranslation("preferences.dialog.base_dir")), cc.xy(1, row));
            builder.add(locationField, cc.xy(3, row));

            row += 2;
            builder.add(useRecycleBinBox, cc.xyw(3, row, 2));

            row += 2;
            builder.add(massDeleteBox, cc.xyw(3, row, 2));

            row += 2;
            builder.add(new JLabel(Translation
                .getTranslation("preferences.dialog.mass_delete_threshold")),
                    cc.xy(1, row));
            builder.add(massDeleteSlider, cc.xy(3, row));

            // Add info for non-windows systems
            if (OSUtil.isWindowsSystem()) { // Windows System
                builder.appendRow("3dlu");
                builder.appendRow("pref");
                builder.appendRow("3dlu");
                builder.appendRow("pref");

                row += 2;
                builder.add(createDesktopShortcutsBox, cc.xyw(3, row, 2));

                if (startWithWindowsBox != null) {
                    row += 2;
                    builder.add(startWithWindowsBox, cc.xyw(3, row, 2));
                }

                builder.appendRow("3dlu");
                builder.appendRow("pref");
                row += 2;
                builder.add(usePowerFolderIconBox, cc.xyw(3, row, 2));

                // Links only available in Vista
                if (OSUtil.isWindowsVistaSystem()) {
                    builder.appendRow("3dlu");
                    builder.appendRow("pref");
                    row += 2;
                    builder.add(usePowerFolderLink, cc.xyw(3, row, 2));
                }
            } else {
                builder.appendRow("3dlu");
                builder.appendRow("pref");

                row += 2;
                builder.add(new JLabel(Translation
                    .getTranslation("preferences.dialog.non_windows_info"),
                    SwingConstants.CENTER), cc.xyw(1, row, 4));
            }

            row += 2;
            builder.add(backupOnlyClientBox, cc.xyw(3, row, 2));

            row += 2;
            builder.add(showAdvancedSettingsBox, cc.xyw(3, row, 2));

            panel = builder.getPanel();
        }
        return panel;
    }

    public void undoChanges() {
    }

    /**
     * Called when the location model changes value. Sets the location text
     * field value and enables the location button.
     */
    private void updateLocationComponents() {
        String value = (String) locationModel.getValue();
        locationTF.setText(value);
    }

    /**
     * Creates a pair of location text field and button.
     *
     * @param folderInfo
     * @return
     */
    private JComponent createLocationField() {
        FormLayout layout = new FormLayout("122dlu, 3dlu, pref", "pref");

        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();

        locationTF = new JTextField();
        locationTF.setEditable(false);
        locationTF.setText((String) locationModel.getValue());
        builder.add(locationTF, cc.xy(1, 1));

        JButton locationButton = new JButtonMini(Icons.getIconById(Icons.DIRECTORY),
                Translation.getTranslation("folder_create.dialog.select_file.text"));
        locationButton.addActionListener(new MyActionListener());
        builder.add(locationButton, cc.xy(3, 1));
        return builder.getPanel();
    }

    /**
     * Enable the mass delete slider if the box is selected.
     */
    private void enableMassDeleteSlider() {
        massDeleteSlider.setEnabled(massDeleteBox.isSelected());
    }

    public void save() {
        // Write properties into core
        writeTrigger.triggerCommit();

        // Set folder base
        String folderbase = (String) locationModel.getValue();
        ConfigurationEntry.FOLDER_BASEDIR.setValue(getController(), folderbase);

        // Nickname
        if (!StringUtils.isBlank(nickField.getText())) {
            getController().changeNick(nickField.getText(), false);
        }

        // setAdvanced
        PreferencesEntry.SHOW_ADVANCED_SETTINGS.setValue(getController(),
            showAdvancedSettingsBox.isSelected());

        // set bu only
        if (!ConfigurationEntry.BACKUP_ONLY_CLIENT.getValue(getController())
                .equals(String.valueOf(backupOnlyClientBox.isSelected()))) {
            needsRestart = true;
        }
        ConfigurationEntry.BACKUP_ONLY_CLIENT.setValue(getController(),
            String.valueOf(backupOnlyClientBox.isSelected()));

        // UseRecycleBin
        ConfigurationEntry.USE_RECYCLE_BIN.setValue(getController(), Boolean
            .toString(useRecycleBinBox.isSelected()));

        if (usePowerFolderIconBox != null) {
            // PowerFolder icon
            ConfigurationEntry.USE_PF_ICON.setValue(getController(), Boolean
                .toString(usePowerFolderIconBox.isSelected()));
        }

        if (usePowerFolderLink != null) {
            boolean oldValue = Boolean.parseBoolean(ConfigurationEntry
                    .USE_PF_LINK.getValue(getController()));
            boolean newValue = usePowerFolderLink.isSelected();
            if (oldValue ^ newValue) {
                configureFavorite(newValue);
            }
            // PowerFolder favorite
            ConfigurationEntry.USE_PF_LINK.setValue(getController(), Boolean
                .toString(usePowerFolderLink.isSelected()));
        }

        PreferencesEntry.MASS_DELETE_PROTECTION.setValue(getController(),
                massDeleteBox.isSelected());
        PreferencesEntry.MASS_DELETE_THRESHOLD.setValue(getController(),
                massDeleteSlider.getValue()); 
    }

    private void configureFavorite(boolean newValue) {
        try {
            WinUtils.getInstance().setPFFavorite(newValue, getController());
        } catch (IOException e) {
            logSevere(e);
        }
    }

    /**
     * Action listener for the location button. Opens a choose dir dialog and
     * sets the location model with the result.
     */
    private class MyActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            String initial = (String) locationModel.getValue();
            String newLocationName = DialogFactory.chooseDirectory(getController(),
                initial);
            File newLocation = new File(newLocationName);

            // Make sure that the user is not setting this to the base dir of
            // an existing folder.
            for (Folder folder : getController().getFolderRepository().getFolders()) {
                if (folder.getLocalBase().equals(newLocation)) {
                    DialogFactory.genericDialog(getController(),
                            Translation.getTranslation(
                                    "preferences.dialog.duplicate_localbase.title"),
                            Translation.getTranslation(
                                    "preferences.dialog.duplicate_localbase.message", 
                                    folder.getName()),
                            GenericDialogType.ERROR);
                    return;
                }
            }
            locationModel.setValue(newLocationName);
        }
    }

    private class MassDeleteItemListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            enableMassDeleteSlider();
        }
    }
}
