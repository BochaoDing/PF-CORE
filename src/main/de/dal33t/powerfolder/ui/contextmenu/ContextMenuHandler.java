/*
 * Copyright 2004 - 2014 Christian Sprajc. All rights reserved.
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
 */
package de.dal33t.powerfolder.ui.contextmenu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.liferay.nativity.modules.contextmenu.ContextMenuControlCallback;
import com.liferay.nativity.modules.contextmenu.model.ContextMenuAction;
import com.liferay.nativity.modules.contextmenu.model.ContextMenuItem;

import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Constants;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.DocumentType;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.FolderRepository;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.light.FileInfoFactory;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.os.OSUtil;
import de.dal33t.powerfolder.util.os.Win32.WinUtils;

/**
 * Builds the Context Menu Items and applies the the correct
 * {@link ContextMenuAction}.
 * 
 * @author <a href="mailto:krickl@powerfolder.com">Maximilian Krickl</a>
 */
public class ContextMenuHandler extends PFComponent implements
    ContextMenuControlCallback
{
    private ContextMenuItem pfMainItem;
    private ContextMenuItem openColabItem;

    private ContextMenuItem shareLinkItem;
    private ContextMenuItem shareFolderItem;
    private ContextMenuItem moveFolderItem;
    private ContextMenuItem openWebItem;
    private ContextMenuItem stopSyncItem;
    private ContextMenuItem lockItem;
    private ContextMenuItem unlockItem;
    private ContextMenuItem versionHistoryItem;
    private ContextMenuItem lockInfoItem;

    public ContextMenuHandler(Controller controller) {
        super(controller);

        openColabItem = new ContextMenuItem(
            Translation.get("context_menu.open_and_colaborate",
            ConfigurationEntry.DIST_NAME.getValue(getController())));
        openColabItem.setContextMenuAction(new OpenColaborateAction(
            getController()));

        pfMainItem = new ContextMenuItem(
            Translation.get("context_menu.main_item"));

        shareLinkItem = new ContextMenuItem(
            Translation.get("context_menu.share_link"));
        shareLinkItem
            .setContextMenuAction(new ShareLinkAction(getController()));

        shareFolderItem = new ContextMenuItem(
            Translation.get("context_menu.share_folder"));
        shareFolderItem.setContextMenuAction(new ShareFolderAction(
            getController()));

        moveFolderItem = new ContextMenuItem(
            Translation.get("context_menu.move_folder"));
        moveFolderItem.setContextMenuAction(new MoveExistingFolderAction(
            getController()));

        openWebItem = new ContextMenuItem(
            Translation.get("context_menu.open_web"));
        openWebItem.setContextMenuAction(new OpenWebAction(getController()));

        stopSyncItem = new ContextMenuItem(
            Translation.get("context_menu.stop_sync"));
        stopSyncItem.setContextMenuAction(new StopSyncAction(getController()));

        lockInfoItem = new ContextMenuItem(
            Translation.get("context_menu.lock_information"));
        lockInfoItem.setContextMenuAction(new LockInfoAction(getController()));

        lockItem = new ContextMenuItem(
            Translation.get("context_menu.lock"));
        lockItem.setContextMenuAction(new LockAction(getController()));

        unlockItem = new ContextMenuItem(
            Translation.get("context_menu.unlock"));
        unlockItem.setContextMenuAction(new UnlockAction(getController()));

        versionHistoryItem = new ContextMenuItem(
            Translation.get("context_menu.version_history"));
        versionHistoryItem.setContextMenuAction(new VersionHistoryAction(
            getController()));
    }

    @Override
    public List<ContextMenuItem> getContextMenuItems(String[] pathNames) {
        try {
            // Clear the context menu
            for (ContextMenuItem cmi : pfMainItem.getAllContextMenuItems()) {
                pfMainItem.removeContextMenuItem(cmi);
            }

            // Gather some information to decide which context menu items to
            // show
            boolean containsFolderPath = false;
            boolean containsFileInfoPath = false;
            boolean containsDirectoryInfoPath = false;
            FileInfo found = null;

            String startMenu = "";

            if (OSUtil.isWindowsSystem()) {
                startMenu = WinUtils.getInstance().getSystemFolderPath(
                    WinUtils.CSIDL_START_MENU, false);
            }

            // Check for folder base paths
            FolderRepository fr = getController().getFolderRepository();
            for (String pathName : pathNames) {
                if (pathName.equals(startMenu)) {
                    continue;
                }

                Path path = Paths.get(pathName);
                Folder folder = fr.findContainingFolder(path);

                if (folder == null) {
                    continue;
                }

                if (!containsFolderPath && folder.getLocalBase().equals(path)) {
                    containsFolderPath = true;
                    continue;
                }

                if (path.getFileName() == null) {
                    // path is a root node, this would lead to an NPE in the
                    // following call
                    continue;
                }

                FileInfo lookup = FileInfoFactory.lookupInstance(folder, path);
                if ((found = folder.getDAO().find(lookup, null)) != null) {
                    if (found.isFile()) {
                        containsFileInfoPath = true;
                    } else if (found.isDiretory()) {
                        containsDirectoryInfoPath = true;
                    }
                }

                if (containsFolderPath && containsFileInfoPath
                    && containsDirectoryInfoPath)
                {
                    break;
                }
            }

            if (pathNames.length == 1
                && (
                    (containsFolderPath
                        && pathNames[0].contains(Constants.POWERFOLDER_SYSTEM_SUBDIR))
                    || pathNames[0].equals(startMenu)
                    )
                )
            {
                return new ArrayList<>(0);
            }

            if (pathNames.length == 1
                && (containsFileInfoPath || containsDirectoryInfoPath || containsFolderPath)
                && ConfigurationEntry.WEB_LOGIN_ALLOWED
                    .getValueBoolean(getController()))
            {
                pfMainItem.addContextMenuItem(openWebItem);
            }

            if ((containsFolderPath && pathNames.length == 1)
                || (pathNames.length == 1
                    && Files.isDirectory(Paths.get(pathNames[0]))
                    && getController().getOSClient().isAllowedToCreateFolders()
                    && !containsDirectoryInfoPath))
            {
                pfMainItem.addContextMenuItem(shareFolderItem);
            }

            if (containsFolderPath
                && !(containsFileInfoPath || containsDirectoryInfoPath))
            {
                pfMainItem.addContextMenuItem(moveFolderItem);
                pfMainItem.addContextMenuItem(stopSyncItem);
            }

            if (!containsFolderPath && containsFileInfoPath) {
                pfMainItem.addContextMenuItem(versionHistoryItem);
            }

            if ((containsDirectoryInfoPath || containsFileInfoPath)
                && pathNames.length == 1)
            {
                pfMainItem.addContextMenuItem(shareLinkItem);
            }

            if (containsFileInfoPath
                && !(containsFolderPath || containsDirectoryInfoPath))
            {
                if (containsFileInfoPath && pathNames.length == 1
                    && found.isLocked(getController()))
                {
                    pfMainItem.addContextMenuItem(lockInfoItem);
                }
                pfMainItem.addContextMenuItem(lockItem);
                pfMainItem.addContextMenuItem(unlockItem);
            }

            List<ContextMenuItem> items = new ArrayList<>(2);
            if (containsFileInfoPath && pathNames.length == 1) {
                String pathName = pathNames[0];
                boolean addEditItem = false;

                for (DocumentType type : DocumentType.values()) {
                    for (String ext : type.getExtensions()) {
                        if (pathName.endsWith(ext)) {
                            addEditItem = true;
                            break;
                        }
                    }
                    if (addEditItem) {
                        break;
                    }
                }

                if (addEditItem) {
                    items.add(openColabItem);
                }
            }

            if (pfMainItem.getContextMenuItems().size() > 0) {
                items.add(pfMainItem);
            }

            return items;
        } catch (RuntimeException re) {
            logWarning("Error trying to compile context menu " + re, re);
            return new ArrayList<ContextMenuItem>(0);
        }
    }
}
