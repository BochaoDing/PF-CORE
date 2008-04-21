/* $Id: FolderJoinPanel.java,v 1.30 2006/02/28 16:44:33 totmacherr Exp $
 */
package de.dal33t.powerfolder.ui.dialog;

import com.jgoodies.binding.value.ValueHolder;
import com.jgoodies.binding.value.ValueModel;
import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.ButtonBarFactory;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.disk.*;
import de.dal33t.powerfolder.ui.Icons;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.ui.BaseDialog;
import de.dal33t.powerfolder.util.ui.SyncProfileSelectorPanel;
import de.dal33t.powerfolder.util.ui.DialogFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;

/**
 * Panel displayed when wanting to move a folder from preview to join
 *
 * @author <a href="mailto:harry@powerfolder.com">Harry Glasgow </a>
 * @version $Revision: 2.3 $
 */
public class PreviewToJoinPanel extends BaseDialog {

    private Folder folder;
    private JButton joinButton;
    private JButton cancelButton;
    private SyncProfileSelectorPanel syncProfileSelectorPanel;
    private ValueModel locationModel;
    private JTextField locationTF;

    /**
     * Contructor when used on choosen folder
     *
     * @param controller
     * @param foInfo
     */
    public PreviewToJoinPanel(Controller controller, Folder folder) {
        super(controller, true);
        Reject.ifFalse(folder.isPreviewOnly(), "Folder should be a preview");
        this.folder = folder;
    }

    /**
     * Initalizes all ui components
     */
    private void initComponents() {

        FolderSettings folderSettings = getController().getFolderRepository()
                .loadFolderSettings(folder.getName());

        syncProfileSelectorPanel = new SyncProfileSelectorPanel(
            getController(), folderSettings.getSyncProfile());

        syncProfileSelectorPanel
            .addModelValueChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    if (!SyncProfileSelectorPanel
                        .vetoableFolderSyncProfileChange(null,
                            (SyncProfile) evt.getNewValue()))
                    {
                        syncProfileSelectorPanel.setSyncProfile(
                            (SyncProfile) evt.getOldValue(), false);
                    }
                }
            });

        locationModel = new ValueHolder(folderSettings
                .getLocalBaseDir().getAbsolutePath());

        // Behavior
        locationModel.addValueChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                locationTF.setText((String) evt.getNewValue());
            }
        });

        // Buttons
        joinButton = new JButton(Translation.getTranslation("folderjoin.join"));
        joinButton.setMnemonic(Translation.getTranslation("folderjoin.join.key")
            .trim().charAt(0));

        final FolderSettings existingFoldersSettings = getController()
                .getFolderRepository().loadFolderSettings(folder.getName());
        joinButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                FolderSettings newFolderSettings = new FolderSettings(
                        new File((String) locationModel.getValue()),
                        syncProfileSelectorPanel.getSyncProfile(),
                        false, existingFoldersSettings.isUseRecycleBin(), false);

                FolderPreviewHelper.convertFolderFromPreview(getController(),
                        folder, newFolderSettings, false);
                close();
            }
        });

        cancelButton = createCancelButton(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                close();
            }
        });
    }

    // Methods for BaseDialog *************************************************

    public String getTitle() {
        return Translation.getTranslation("folderjoin.dialog.title",
            folder.getName());
    }

    protected Icon getIcon() {
        return Icons.JOIN_FOLDER;
    }

    protected Component getContent() {
        initComponents();

        FormLayout layout = new FormLayout(
            "right:pref, 4dlu, max(120dlu;pref):grow",
            "pref, 4dlu, pref");
        PanelBuilder builder = new PanelBuilder(layout);

        CellConstraints cc = new CellConstraints();

        int row = 1;
        builder.addLabel(Translation.getTranslation("general.synchonisation"),
            cc.xy(1, row));
        builder.add(syncProfileSelectorPanel.getUIComponent(), cc.xy(3, row));

        row += 2;

        builder.addLabel(Translation.getTranslation("general.local_copy_at"), cc
            .xy(1, row));
        builder.add(createLocationField(), cc.xy(3, row));

        row += 2;

        return builder.getPanel();
    }

    protected Component getButtonBar() {
        return ButtonBarFactory.buildCenteredBar(joinButton, cancelButton);
    }

    /**
     * Creates a pair of location text field and button.
     *
     * @param folderInfo
     * @return
     */
    private JComponent createLocationField() {
        FormLayout layout = new FormLayout("100dlu, 4dlu, 15dlu", "pref");

        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();

        locationTF = new JTextField();
        locationTF.setEditable(false);
        locationTF.setText((String) locationModel.getValue());
        builder.add(locationTF, cc.xy(1, 1));

        JButton locationButton = new JButton(Icons.DIRECTORY);
        locationButton.addActionListener(new MyActionListener());
        builder.add(locationButton, cc.xy(3, 1));
        return builder.getPanel();
    }

    /**
     * Action listener for the location button. Opens a choose dir dialog and
     * sets the location model with the result.
     */
    private class MyActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            String initial = (String) locationModel.getValue();
            String file = DialogFactory.chooseDirectory(getController(),
                initial);
            locationModel.setValue(file);
        }
    }
}