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
package de.dal33t.powerfolder.ui.wizard;

import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.FOLDERINFO_ATTRIBUTE;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.MAKE_FRIEND_AFTER;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.SET_DEFAULT_SYNCHRONIZED_FOLDER;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import jwf.WizardPanel;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.PreferencesEntry;
import de.dal33t.powerfolder.clientserver.ServerClient;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.FolderException;
import de.dal33t.powerfolder.disk.FolderSettings;
import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.light.MemberInfo;
import de.dal33t.powerfolder.ui.dialog.SyncFolderPanel;
import de.dal33t.powerfolder.util.ArchiveMode;
import de.dal33t.powerfolder.util.FileUtils;
import de.dal33t.powerfolder.util.IdGenerator;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.ui.SwingWorker;

/**
 * A panel that actually starts the creation process of a folder on display.
 * Automatically switches to the next panel when succeeded otherwise prints
 * error.
 * <p>
 * Extracts the settings for the folder from the
 * <code>WizardContextAttributes</code>.
 * 
 * @author Christian Sprajc
 * @version $Revision$
 */
public class FolderCreatePanel extends PFWizardPanel {

    private static final Logger log = Logger.getLogger(FolderCreatePanel.class
        .getName());

    private final Map<FolderInfo, FolderSettings> configurations;
    private boolean backupByOS;
    private boolean sendInvitations;
    private boolean createShortcut;

    private final List<Folder> folders;

    private JLabel statusLabel;
    private JTextArea errorArea;
    private JComponent errorPane;
    private JProgressBar bar;

    public FolderCreatePanel(Controller controller) {
        super(controller);
        configurations = new HashMap<FolderInfo, FolderSettings>();
        folders = new ArrayList<Folder>();
    }

    @Override
    public boolean canFinish() {
        return false;
    }

    /**
     * Folders created; can not change that.
     * 
     * @return
     */
    @Override
    public boolean canGoBackTo() {
        return false;
    }

    @Override
    protected JPanel buildContent() {
        FormLayout layout = new FormLayout("140dlu, $lcg, $wfield",
            "pref, 3dlu, pref, 3dlu, pref");

        PanelBuilder builder = new PanelBuilder(layout);
        builder.setBorder(createFewContentBorder());
        CellConstraints cc = new CellConstraints();

        int row = 1;

        statusLabel = builder.addLabel(Translation
            .getTranslation("wizard.create_folder.working"), cc.xy(1, row));

        row += 2;
        bar = new JProgressBar();
        bar.setIndeterminate(true);
        builder.add(bar, cc.xy(1, row));

        errorArea = new JTextArea();
        errorArea.setRows(5);
        errorArea.setWrapStyleWord(true);
        errorPane = new JScrollPane(errorArea);
        builder.add(errorPane, cc.xy(1, row));
        return builder.getPanel();
    }

    @Override
    protected void afterDisplay() {

        // Mandatory
        Boolean saveLocalInvite = (Boolean) getWizardContext().getAttribute(
            WizardContextAttributes.SAVE_INVITE_LOCALLY);
        Reject.ifNull(saveLocalInvite,
            "Save invite locally attribute is null/not set");

        // Optional
        Boolean prevAtt = (Boolean) getWizardContext().getAttribute(
            WizardContextAttributes.PREVIEW_FOLDER_ATTIRBUTE);
        boolean previewFolder = prevAtt != null && prevAtt;

        createShortcut = (Boolean) getWizardContext().getAttribute(
            WizardContextAttributes.CREATE_DESKTOP_SHORTCUT);
        Boolean osAtt = (Boolean) getWizardContext().getAttribute(
            WizardContextAttributes.BACKUP_ONLINE_STOARGE);
        backupByOS = osAtt != null && osAtt;
        if (backupByOS) {
            getController().getUIController().getApplicationModel()
                .getServerClientModel().checkAndSetupAccount();
        }
        Boolean sendInvsAtt = (Boolean) getWizardContext().getAttribute(
            WizardContextAttributes.SEND_INVIATION_AFTER_ATTRIBUTE);
        sendInvitations = sendInvsAtt == null || sendInvsAtt;

        // Either we have FOLDER_CREATE_ITEMS ...
        List<FolderCreateItem> folderCreateItems = (List<FolderCreateItem>) getWizardContext()
            .getAttribute(WizardContextAttributes.FOLDER_CREATE_ITEMS);
        if (folderCreateItems != null && !folderCreateItems.isEmpty()) {
            for (FolderCreateItem folderCreateItem : folderCreateItems) {
                File localBase = folderCreateItem.getLocalBase();
                SyncProfile syncProfile = folderCreateItem.getSyncProfile();
                if (syncProfile == null) {
                    syncProfile = SyncProfile.AUTOMATIC_SYNCHRONIZATION;
                }
                FolderInfo folderInfo = folderCreateItem.getFolderInfo();
                if (folderInfo == null) {
                    folderInfo = createFolderInfo(localBase);
                }
                ArchiveMode archiveMode = folderCreateItem.getArchiveMode();
                int archiveHistory = folderCreateItem.getArchiveHistory();
                FolderSettings folderSettings = new FolderSettings(localBase,
                    syncProfile, saveLocalInvite, archiveMode, previewFolder,
                    null, archiveHistory);
                configurations.put(folderInfo, folderSettings);
            }
        } else {
            // ... or FOLDER_LOCAL_BASE + SYNC_PROFILE_ATTRIBUTE + optional
            // FOLDERINFO_ATTRIBUTE...
            File localBase = (File) getWizardContext().getAttribute(
                WizardContextAttributes.FOLDER_LOCAL_BASE);
            SyncProfile syncProfile = (SyncProfile) getWizardContext()
                .getAttribute(WizardContextAttributes.SYNC_PROFILE_ATTRIBUTE);
            Reject.ifNull(localBase, "Local base for folder is null/not set");
            Reject.ifNull(syncProfile,
                "Sync profile for folder is null/not set");

            // Optional
            FolderInfo folderInfo = (FolderInfo) getWizardContext()
                .getAttribute(FOLDERINFO_ATTRIBUTE);
            if (folderInfo == null) {
                folderInfo = createFolderInfo(localBase);
            }

            FolderSettings folderSettings = new FolderSettings(localBase,
                syncProfile, saveLocalInvite, ArchiveMode.valueOf(
                            PreferencesEntry.DEFAULT_ARCHIVE_MODE
                                    .getValueString(getController())),
                previewFolder, null, PreferencesEntry.DEFAULT_ARCHIVE_VERIONS
                                .getValueInt(getController()));
            configurations.put(folderInfo, folderSettings);
        }

        // Reset
        folders.clear();

        SwingWorker worker = new MyFolderCreateWorker();
        bar.setVisible(true);
        errorPane.setVisible(false);
        worker.start();

        updateButtons();
    }

    private static FolderInfo createFolderInfo(File localBase) {
        // Create new folder info
        String name = localBase.getName();
        String folderId = '[' + IdGenerator.makeId() + ']';
        return new FolderInfo(name, folderId);
    }

    @Override
    protected void initComponents() {
    }

    @Override
    protected String getTitle() {
        return Translation.getTranslation("wizard.create_folder.title");
    }

    @Override
    public boolean hasNext() {
        return !folders.isEmpty();
    }

    @Override
    public WizardPanel next() {
        WizardPanel next;
        if (sendInvitations) {
            next = new SendInvitationsPanel(getController());
        } else {
            next = (WizardPanel) getWizardContext().getAttribute(
                PFWizard.SUCCESS_PANEL);
        }
        return next;
    }

    private class MyFolderCreateWorker extends SwingWorker {

        private boolean problems;

        @Override
        public Object construct() {
            ServerClient client = getController().getOSClient();
            for (Map.Entry<FolderInfo, FolderSettings> folderInfoFolderSettingsEntry : configurations
                .entrySet())
            {
                FolderSettings folderSettings = folderInfoFolderSettingsEntry
                    .getValue();
                Folder folder = getController().getFolderRepository()
                    .createFolder(folderInfoFolderSettingsEntry.getKey(),
                        folderSettings);
                folder.addDefaultExcludes();
                if (createShortcut) {
                    folder.setDesktopShortcut(true);
                }
                folders.add(folder);
                if (configurations.size() == 1) {
                    // Set for SendInvitationsPanel
                    getWizardContext().setAttribute(FOLDERINFO_ATTRIBUTE,
                        folder.getInfo());
                }

                // Is there a member to make a friend?
                // Invitation invitors are automatically made friends.
                Object attribute = getWizardContext().getAttribute(
                    MAKE_FRIEND_AFTER);
                if (attribute != null && attribute instanceof MemberInfo) {
                    MemberInfo memberInfo = (MemberInfo) attribute;
                    Member member = getController().getNodeManager().getNode(
                        memberInfo);
                    if (member != null) {
                        if (!member.isFriend()) {
                            member.setFriend(true, null);
                        }
                    }
                }

                if (backupByOS && client.isLoggedIn()) {
                    try {
                        // Try to back this up by online storage.
                        if (client.hasJoined(folder)) {
                            // Already have this os folder.
                            log.log(Level.WARNING, "Already have os folder "
                                + folderInfoFolderSettingsEntry.getKey().name);
                            continue;
                        }

                        client.getFolderService().createFolder(
                            folderInfoFolderSettingsEntry.getKey(),
                            SyncProfile.BACKUP_TARGET_NO_CHANGE_DETECT);

                        // Set as default synced folder?
                        attribute = getWizardContext().getAttribute(
                            SET_DEFAULT_SYNCHRONIZED_FOLDER);
                        if (attribute != null && (Boolean) attribute) {
                            // TODO: Ugly. Use abstraction: Runnable? Callback
                            // with
                            // folder? Which is placed on WizardContext.
                            client.getFolderService()
                                .setDefaultSynchronizedFolder(
                                    folderInfoFolderSettingsEntry.getKey());
                            createDefaultFolderHelpFile(folder);
                            folder.recommendScanOnNextMaintenance();
                            try {
                                FileUtils.openFile(folder.getLocalBase());
                            } catch (IOException e) {
                                log.log(Level.FINER, "IOException", e);
                            }
                        }
                    } catch (FolderException e) {
                        addProblem(Translation.getTranslation(
                            "folder_create.os_error.text", e.fInfo.name)
                            + '\n' + e.getMessage());
                        log.log(Level.SEVERE,
                            "Unable to backup folder to online storage", e);
                    }
                }
            }

            return null;
        }

        private void addProblem(String problem) {
            problems = true;
            StringBuilder stringBuilder = new StringBuilder(errorArea.getText());
            if (stringBuilder.length() > 0) {
                stringBuilder.append("\n\n");
            }
            stringBuilder.append(problem);
            errorArea.setText(stringBuilder.toString());
            errorPane.setVisible(true);
        }

        private void createDefaultFolderHelpFile(Folder folder) {
            File helpFile = new File(folder.getLocalBase(),
                "Place files to sync here.txt");
            if (helpFile.exists()) {
                return;
            }
            Writer w = null;
            try {
                w = new OutputStreamWriter(new FileOutputStream(helpFile));
                w
                    .write("This is the default synchronized folder of PowerFolder.\r\n");
                w
                    .write("Simply place files into this directory to sync them\r\n");
                w.write("across all your computers running PowerFolder.\r\n");
                w.write("\r\n");
                w
                    .write("More information: http://wiki.powerfolder.com/wiki/Default_Folder");
                w.close();
            } catch (IOException e) {
                // Doesn't matter.
            } finally {
                if (w != null) {
                    try {
                        w.close();
                    } catch (IOException e) {
                        // Ignore
                    }
                }
            }
        }

        @Override
        public void finished() {
            bar.setVisible(false);

            if (problems) {
                updateButtons();

                statusLabel.setText(Translation
                    .getTranslation("wizard.create_folder.problems"));
                errorPane.setVisible(true);
            } else {
                for (Folder folder : folders) {
                    if (SyncProfile.MANUAL_SYNCHRONIZATION.equals(folder
                        .getSyncProfile()))
                    {
                        // Show sync folder panel after created a project folder
                        new SyncFolderPanel(getController(), folder).open();
                    }
                }
                getWizard().next();
            }
        }
    }
}
