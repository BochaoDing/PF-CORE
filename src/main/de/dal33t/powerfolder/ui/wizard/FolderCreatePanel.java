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

import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.BACKUP_ONLINE_STOARGE;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.CREATE_DESKTOP_SHORTCUT;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.FOLDERINFO_ATTRIBUTE;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.FOLDER_CREATE_ITEMS;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.FOLDER_LOCAL_BASE;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.MAKE_FRIEND_AFTER;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.PREVIEW_FOLDER_ATTIRBUTE;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.SAVE_INVITE_LOCALLY;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.SEND_INVIATION_AFTER_ATTRIBUTE;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.SET_DEFAULT_SYNCHRONIZED_FOLDER;
import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.SYNC_PROFILE_ATTRIBUTE;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jwf.WizardPanel;
import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.clientserver.ServerClient;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.FolderRepository;
import de.dal33t.powerfolder.disk.FolderSettings;
import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.light.MemberInfo;
import de.dal33t.powerfolder.util.ArchiveMode;
import de.dal33t.powerfolder.util.FileUtils;
import de.dal33t.powerfolder.util.IdGenerator;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.StringUtils;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.os.Win32.ShellLink;
import de.dal33t.powerfolder.util.os.Win32.WinUtils;

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
public class FolderCreatePanel extends SwingWorkerPanel {

    private static final Logger log = Logger.getLogger(FolderCreatePanel.class
        .getName());

    private boolean sendInvitations;
    private final List<Folder> folders;

    public FolderCreatePanel(Controller controller) {
        super(controller, null, Translation
            .getTranslation("wizard.create_folder.title"), Translation
            .getTranslation("wizard.create_folder.working"), null);
        setTask(new MyFolderCreateWorker());
        folders = new ArrayList<Folder>();
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

    private static FolderInfo createFolderInfo(File localBase) {
        // Create new folder info
        String name = FileUtils.getSuggestedFolderName(localBase);
        String folderId = '[' + IdGenerator.makeId() + ']';
        return new FolderInfo(name, folderId);
    }

    private class MyFolderCreateWorker implements Runnable {

        public void run() {
            final Map<FolderInfo, FolderSettings> configurations = new HashMap<FolderInfo, FolderSettings>();
            final Map<FolderInfo, String> joinFolders = new HashMap<FolderInfo, String>();
            boolean backupByOS;

            boolean createDesktopShortcut;

            // Mandatory
            Boolean saveLocalInvite = (Boolean) getWizardContext()
                .getAttribute(SAVE_INVITE_LOCALLY);
            Reject.ifNull(saveLocalInvite,
                "Save invite locally attribute is null/not set");

            // Optional
            Boolean prevAtt = (Boolean) getWizardContext().getAttribute(
                PREVIEW_FOLDER_ATTIRBUTE);
            boolean previewFolder = prevAtt != null && prevAtt;

            createDesktopShortcut = (Boolean) getWizardContext().getAttribute(
                CREATE_DESKTOP_SHORTCUT);
            Boolean osAtt = (Boolean) getWizardContext().getAttribute(
                BACKUP_ONLINE_STOARGE);
            backupByOS = osAtt != null && osAtt;
            if (backupByOS) {
                getController().getUIController().getApplicationModel()
                    .getServerClientModel().checkAndSetupAccount();
            }
            Boolean sendInvsAtt = (Boolean) getWizardContext().getAttribute(
                SEND_INVIATION_AFTER_ATTRIBUTE);
            sendInvitations = sendInvsAtt == null || sendInvsAtt;

            // Either we have FOLDER_CREATE_ITEMS ...
            List<FolderCreateItem> folderCreateItems = (List<FolderCreateItem>) getWizardContext()
                .getAttribute(FOLDER_CREATE_ITEMS);
            if (folderCreateItems != null && !folderCreateItems.isEmpty()) {
                for (FolderCreateItem folderCreateItem : folderCreateItems) {
                    File localBase = folderCreateItem.getLocalBase();
                    Reject.ifNull(localBase,
                        "Local base for folder is null/not set");
                    SyncProfile syncProfile = folderCreateItem.getSyncProfile();
                    Reject.ifNull(syncProfile,
                        "Sync profile for folder is null/not set");
                    FolderInfo folderInfo = folderCreateItem.getFolderInfo();
                    if (folderInfo == null) {
                        folderInfo = createFolderInfo(localBase);
                    }
                    ArchiveMode archiveMode = folderCreateItem.getArchiveMode();
                    int archiveHistory = folderCreateItem.getArchiveHistory();
                    if (!StringUtils.isBlank(folderCreateItem
                        .getLinkToOnlineFolder()))
                    {
                        joinFolders.put(folderInfo, folderCreateItem
                            .getLinkToOnlineFolder());
                    }
                    FolderSettings folderSettings = new FolderSettings(
                        localBase, syncProfile, saveLocalInvite, archiveMode,
                        previewFolder, null, archiveHistory, true);
                    configurations.put(folderInfo, folderSettings);
                }
            } else {

                // ... or FOLDER_LOCAL_BASE + SYNC_PROFILE_ATTRIBUTE + optional
                // FOLDERINFO_ATTRIBUTE...
                File localBase = (File) getWizardContext().getAttribute(
                    FOLDER_LOCAL_BASE);
                Reject.ifNull(localBase,
                    "Local base for folder is null/not set");
                SyncProfile syncProfile = (SyncProfile) getWizardContext()
                    .getAttribute(SYNC_PROFILE_ATTRIBUTE);
                Reject.ifNull(syncProfile,
                    "Sync profile for folder is null/not set");

                // Optional
                FolderInfo folderInfo = (FolderInfo) getWizardContext()
                    .getAttribute(FOLDERINFO_ATTRIBUTE);
                if (folderInfo == null) {
                    folderInfo = createFolderInfo(localBase);
                }

                FolderSettings folderSettings = new FolderSettings(localBase,
                    syncProfile, saveLocalInvite, ArchiveMode
                        .valueOf(ConfigurationEntry.DEFAULT_ARCHIVE_MODE
                            .getValue(getController())), previewFolder, null,
                    ConfigurationEntry.DEFAULT_ARCHIVE_VERIONS
                        .getValueInt(getController()), true);
                configurations.put(folderInfo, folderSettings);
            }

            // Reset
            folders.clear();
            updateButtons();

            ServerClient client = getController().getOSClient();

            Collection<FolderInfo> onlineFolderInfos = client
                .getAccountFolders();

            for (Map.Entry<FolderInfo, FolderSettings> entry : configurations
                .entrySet())
            {
                FolderInfo folderInfo = entry.getKey();
                FolderSettings folderSettings = entry.getValue();
                String joinFolderName = joinFolders.get(folderInfo);

                if (joinFolderName == null) {
                    // Look for folders where there is already an online folder
                    // with
                    // the same name. Offer to join instead of create
                    // duplicates.
                    for (FolderInfo onlineFolderInfo : onlineFolderInfos) {
                        if (onlineFolderInfo.getName().equals(
                            folderInfo.getName()))
                        {
                            if (!onlineFolderInfo.equals(folderInfo)) {
                                log.info("Found online folder with same name: "
                                    + folderInfo.getName() + ". Using it");

                                // User actually wants to join, so use online.
                                folderInfo = onlineFolderInfo;
                                log
                                    .info("Changed folder info to online version: "
                                        + folderInfo.getName());
                                break;
                            }
                        }
                    }
                } else {
                    // User already specified online folder to join - join it.
                    boolean gotIt = false;
                    for (FolderInfo onlineFolderInfo : onlineFolderInfos) {
                        if (onlineFolderInfo.getName().equals(joinFolderName)) {
                            log.info("Joining specified folder "
                                + joinFolderName);
                            folderInfo = onlineFolderInfo;
                            gotIt = true;
                            break;
                        }
                    }
                    if (!gotIt) {
                        // Hmmm - link folder specified but can not find it now?
                        log.warning("Could not find link folder "
                            + joinFolderName + " for " + folderInfo);
                    }
                }

                Folder folder = getController().getFolderRepository()
                    .createFolder(folderInfo, folderSettings);

                folder.addDefaultExcludes();
                if (createDesktopShortcut) {
                    folder.setDesktopShortcut(true);
                }
                createShortcutToFolder(folderInfo, folderSettings);

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
                    // Try to back this up by online storage.
                    if (client.joinedByCloud(folder)) {
                        // Already have this os folder.
                        log.log(Level.WARNING, "Already have os folder "
                            + folderInfo.name);
                        continue;
                    }

                    client.getFolderService().createFolder(folderInfo,
                        SyncProfile.BACKUP_TARGET_NO_CHANGE_DETECT);

                    // Set as default synced folder?
                    attribute = getWizardContext().getAttribute(
                        SET_DEFAULT_SYNCHRONIZED_FOLDER);
                    if (attribute != null && (Boolean) attribute) {
                        // TODO: Ugly. Use abstraction: Runnable? Callback
                        // with
                        // folder? Which is placed on WizardContext.
                        client.getFolderService().setDefaultSynchronizedFolder(
                            folderInfo);
                        createDefaultFolderHelpFile(folder);
                        folder.recommendScanOnNextMaintenance();
                        FileUtils.openFile(folder.getLocalBase());
                    }
                }
            }
        }

        private void createShortcutToFolder(FolderInfo folderInfo,
            FolderSettings folderSettings)
        {
            FolderRepository folderRepo = getController().getFolderRepository();
            File baseDir = new File(folderRepo.getFoldersBasedir());
            if (!baseDir.exists()) {

                log.info(String.format("Creating basedir: %s", baseDir
                    .getAbsolutePath()));
                baseDir.mkdirs();
            }

            File existingFolder = new File(baseDir, folderInfo.name);
            if (existingFolder.exists()) {
                log.info("Folder is already a subdirectory of basedir");
                return;
            }

            File shortcutFile = new File(baseDir, folderInfo.getName() + ".lnk");
            String shortcutPath = shortcutFile.getAbsolutePath();
            String filePath = folderSettings.getLocalBaseDir()
                .getAbsolutePath();

            if (WinUtils.isSupported()) {
                WinUtils winUtils = WinUtils.getInstance();
                ShellLink shellLink = new ShellLink(null, null, filePath, null);
                try {
                    log.info(String.format(
                        "Attempting to create shortcut %s to %s", shortcutPath,
                        filePath));
                    winUtils.createLink(shellLink, shortcutPath);
                } catch (IOException e) {
                    log
                        .warning(String
                            .format(
                                "An exception was thrown when creating shortcut %s to %s",
                                shortcutPath, filePath));
                }
            }
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

    }

}
