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
package de.dal33t.powerfolder.util.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import java.util.logging.Logger;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.Feature;

/**
 * Panel with a combobox for selecting the line speed and a textfield for
 * entering upload speed. Editing the textfield is only possible if Custom
 * line_speed was chosen first.
 * 
 * @author Bytekeeper
 * @version $revision$
 */
public class LineSpeedSelectionPanel extends JPanel {

    private static final Logger log = Logger
        .getLogger(LineSpeedSelectionPanel.class.getName());

    private JComboBox speedSelectionBox;
    private JComponent customSpeedPanel;
    private JFormattedTextField customUploadSpeedField;
    private JFormattedTextField customDownloadSpeedField;
    private LineSpeed defaultSpeed;
    private boolean alwaysShowCustomEntryPanels;

    /**
     * Constructs a new LineSpeedSelectionPanel.
     * 
     * @param alwaysShowCustomEntryPanels
     *            true if the entry panels for custom upload/download speed
     *            selection should be shown all the time, otherwise they only
     *            visible on demand.
     */
    public LineSpeedSelectionPanel(boolean alwaysShowCustomEntryPanels) {
        initComponents();
        buildPanel();
        this.alwaysShowCustomEntryPanels = alwaysShowCustomEntryPanels;
    }

    private void buildPanel() {
        FormLayout layout = new FormLayout("pref:grow", "pref, 1dlu, pref");
        setLayout(layout);

        CellConstraints cc = new CellConstraints();
        customSpeedPanel = createCustomSpeedInputFieldPanel();
        customSpeedPanel.setBorder(Borders.createEmptyBorder("0, 0, 3dlu, 0"));
        JPanel speedSelectionPanel = createSpeedSelectionPanel();
        add(speedSelectionPanel, cc.xy(1, 1));
        add(customSpeedPanel, cc.xy(1, 3));
    }

    private void initComponents() {
        setOpaque(false);

        customUploadSpeedField = new JFormattedTextField();
        customDownloadSpeedField = new JFormattedTextField();

        speedSelectionBox = new JComboBox();
        speedSelectionBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (((LineSpeed) speedSelectionBox.getSelectedItem())
                    .isEditable())
                {
                    customUploadSpeedField.setEnabled(true);
                    customDownloadSpeedField.setEnabled(true);
                    if (!alwaysShowCustomEntryPanels) {
                        customSpeedPanel.setVisible(true);
                    }

                } else {
                    customUploadSpeedField.setEnabled(false);
                    customDownloadSpeedField.setEnabled(false);
                    if (!alwaysShowCustomEntryPanels) {
                        customSpeedPanel.setVisible(false);
                    }

                    customUploadSpeedField.setText(Long
                        .toString(((LineSpeed) speedSelectionBox
                            .getSelectedItem()).getUploadSpeed()));
                    customDownloadSpeedField.setText(Long
                        .toString(((LineSpeed) speedSelectionBox
                            .getSelectedItem()).getDownloadSpeed()));
                }
            }
        });
    }

    private JPanel createSpeedSelectionPanel() {
        FormLayout layout = new FormLayout(
            "140dlu, pref:grow", "pref");
        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();

        builder.add(speedSelectionBox, cc.xy(1,1));

        JPanel panel = builder.getPanel();
        panel.setOpaque(false);
        return panel;
    }

    private JPanel createCustomSpeedInputFieldPanel() {
        FormLayout layout = new FormLayout(
            "pref, 3dlu, 30dlu, 3dlu, pref, 7dlu, pref, 3dlu, 30dlu, 3dlu, pref",
            "pref");
        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();

        builder.add(new JLabel(Translation
            .getTranslation("line_speed.download_speed")), cc.xy(1, 1));
        builder.add(customDownloadSpeedField, cc.xy(3, 1));
        builder.add(new JLabel(Translation.getTranslation("general.kbPerS")),
                cc.xy(5, 1));

        builder.add(new JLabel(Translation
            .getTranslation("line_speed.upload_speed")), cc.xy(7, 1));
        builder.add(customUploadSpeedField, cc.xy(9, 1));
        builder.add(new JLabel(Translation.getTranslation("general.kbPerS")), 
                cc.xy(11, 1));

        JPanel panel = builder.getPanel();
        panel.setOpaque(false);
        return panel;
    }

    /**
     * Loads the selection with the default values for LAN
     */
    public void loadLANSelection() {
        if (Feature.AUTO_SPEED_DETECT.isEnabled()) {
            addLineSpeed("line_speed.auto_speed", -1, 0);
        }
        addLineSpeed("line_speed.lan10", 1000, 0);
        addLineSpeed("line_speed.lan100", 10000, 0);
        addLineSpeed("line_speed.lan1000", 100000, 0);
        addLineSpeed("line_speed.unlimited", 0, 0);
        defaultSpeed = addLineSpeed("line_speed.custom_speed", 0, 0, true);
    }

    /**
     * Loads the selection with the default values for WAN
     */
    public void loadWANSelection() {
        if (Feature.AUTO_SPEED_DETECT.isEnabled()) {
            addLineSpeed("line_speed.auto_speed", -1, 0);
        }
        addLineSpeed("line_speed.adsl128", 11, 0);
        addLineSpeed("line_speed.adsl256", 23, 0);
        addLineSpeed("line_speed.adsl512", 46, 0);
        addLineSpeed("line_speed.adsl768", 69, 0);
        addLineSpeed("line_speed.adsl1024", 128, 0);
        addLineSpeed("line_speed.adsl1536", 192, 0);
        addLineSpeed("line_speed.T1", 140, 0);
        addLineSpeed("line_speed.T3", 3930, 0);
        addLineSpeed("line_speed.unlimited", 0, 0);
        defaultSpeed = addLineSpeed("line_speed.custom_speed", 0, 0, true);
    }

    /**
     * @return the default "fallback" line_speed if one was set, otherwise
     *         returns the current selected speed.
     */
    private LineSpeed getDefaultLineSpeed() {
        return defaultSpeed != null
            ? defaultSpeed
            : (LineSpeed) speedSelectionBox.getSelectedItem();
    }

    /**
     * Sets the default "fallback" line_speed.
     * 
     * @param speed
     *            the LineSpeed or null it should be cleared
     */
    public void setDefaultLineSpeed(LineSpeed speed) {
        defaultSpeed = speed;
    }

    /**
     * Creates a LineSpeed instance with the given parameters then adds and
     * returns it.
     * 
     * @param descr
     *            the translation property's name whose value will be used
     * @param uploadSpeed
     *            the upload speed in kb/s, 0 for unlimited
     * @param downloadSpeed
     *            the download speed in kb/s, 0 for unlimited
     * @return
     */
    private LineSpeed addLineSpeed(String descr, long uploadSpeed,
        long downloadSpeed)
    {
        return addLineSpeed(descr, uploadSpeed, downloadSpeed, false);
    }

    /**
     * Creates a LineSpeed instance with the given parameters then adds and
     * returns it.
     * 
     * @param descr
     *            the translation property's name whose value will be used
     * @param uploadSpeed
     *            the upload speed in kb/s, 0 for unlimited
     * @param downloadSpeed
     *            the download speed in kb/s, 0 for unlimited
     * @param editable
     *            true if the user should be allowed to modify the upload speed
     *            setting. (The value of LineSpeed.uploadSpeed remains
     *            untouched)
     * @return the line_speed entry.
     */
    private LineSpeed addLineSpeed(String descr, long uploadSpeed,
        long downloadSpeed, boolean editable)
    {
        LineSpeed ls = new LineSpeed(Translation.getTranslation(descr),
            uploadSpeed, downloadSpeed, editable);
        addLineSpeed(ls);
        return ls;
    }

    /**
     * Adds the given LineSpeed to the selection list.
     * 
     * @param speed
     */
    public void addLineSpeed(LineSpeed speed) {
        speedSelectionBox.addItem(speed);
    }

    /**
     * Removes the given LineSpeed from the selection list.
     * 
     * @param speed
     */
    public void removeLineSpeed(LineSpeed speed) {
        speedSelectionBox.removeItem(speed);
    }

    /**
     * Updates the panel by selecting the correct Item for the given speed and
     * also updates the custom value field with that value. TODO: Since some
     * lines might have the same upload limit (like ISDN/DSL) this method
     * currenlty selects the first matching item.
     * 
     * @param uploadSpeed
     *            the upload speed in kb/s, 0 for unlimited
     * @param downloadSpeed
     *            the download speed in kb/s, 0 for unlimited
     */
    public void setSpeedKBPS(long uploadSpeed, long downloadSpeed) {
        // Find the "best" item to select for the given speed
        // if none matches, falls thru tu "Custom"
        for (int i = 0; i < speedSelectionBox.getItemCount(); i++) {
            LineSpeed ls = (LineSpeed) speedSelectionBox.getItemAt(i);
            if (ls.getUploadSpeed() == uploadSpeed
                && ls.getDownloadSpeed() == downloadSpeed)
            {
                speedSelectionBox.setSelectedItem(ls);
                break;
            }
        }

        customUploadSpeedField.setValue(uploadSpeed);
        customDownloadSpeedField.setValue(downloadSpeed);
        if (((LineSpeed) speedSelectionBox.getSelectedItem()).getUploadSpeed() != uploadSpeed
            || ((LineSpeed) speedSelectionBox.getSelectedItem())
                .getDownloadSpeed() != downloadSpeed)
        {
            speedSelectionBox.setSelectedItem(getDefaultLineSpeed());
        }
    }

    /**
     * Returns the currently selected upload speed.
     * 
     * @return The upload speed in kb/s or a number < 0 if an error occured
     */
    public long getUploadSpeedKBPS() {
        try {
            return (Long) customUploadSpeedField.getFormatter().stringToValue(
                customUploadSpeedField.getText()) * 1024;
        } catch (ParseException e) {
            log.warning("Unable to parse uploadlimit '"
                + customUploadSpeedField.getText() + '\'');
        }
        return -1;
    }

    /**
     * Returns the currently selected download speed.
     * 
     * @return The download speed in kb/s or a number < 0 if an error occured
     */
    public long getDownloadSpeedKBPS() {
        try {
            return (Long) customDownloadSpeedField.getFormatter()
                .stringToValue(customDownloadSpeedField.getText()) * 1024;
        } catch (ParseException e) {
            log.warning("Unable to parse downloadlimit '"
                + customDownloadSpeedField.getText() + '\'');
        }
        return -1;
    }

    @Override
    public void setEnabled(boolean enabled) {
        customSpeedPanel.setEnabled(enabled);
        speedSelectionBox.setEnabled(enabled);
        customUploadSpeedField.setEditable(enabled);
        customDownloadSpeedField.setEditable(enabled);
        super.setEnabled(enabled);
    }

    // Inner classes **********************************************************

    /**
     * Container holding the description and upload rate.
     */
    private static class LineSpeed {
        private long uploadSpeed;
        private long downloadSpeed;
        private String desc;
        private boolean editable;

        /**
         * Creates a new LineSpeed
         * 
         * @param desc
         *            the "name" of the speed value
         * @param uploadSpeed
         *            a value >= 0. If this value is below 0 the user may enter
         *            a speed in Kilobytes per second.
         */
        LineSpeed(String desc, long uploadSpeed, long downloadSpeed,
            boolean editable)
        {
            this.desc = desc;
            this.uploadSpeed = uploadSpeed;
            this.downloadSpeed = downloadSpeed;
            this.editable = editable;
        }

        public long getUploadSpeed() {
            return uploadSpeed;
        }

        public String toString() {
            return desc;
        }

        public boolean isEditable() {
            return editable;
        }

        public long getDownloadSpeed() {
            return downloadSpeed;
        }
    }
}
