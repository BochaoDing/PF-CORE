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
 * $Id: InformationFrame.java 5457 2008-10-17 14:25:41Z harry $
 */
package de.dal33t.powerfolder.ui.information;

import java.awt.Frame;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import javax.swing.plaf.RootPaneUI;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.MagneticFrame;
import de.dal33t.powerfolder.event.FolderRepositoryEvent;
import de.dal33t.powerfolder.event.FolderRepositoryListener;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.ui.Icons;
import de.dal33t.powerfolder.ui.UIController;
import de.dal33t.powerfolder.ui.information.debug.DebugInformationCard;
import de.dal33t.powerfolder.ui.information.downloads.DownloadsInformationCard;
import de.dal33t.powerfolder.ui.information.folder.FolderInformationCard;
import de.dal33t.powerfolder.ui.information.uploads.UploadsInformationCard;
import de.dal33t.powerfolder.ui.information.notices.NoticesCard;
import de.javasoft.plaf.synthetica.SyntheticaRootPaneUI;

/**
 * The information window.
 */
public class InformationFrame extends MagneticFrame {

    private JFrame uiComponent;

    private FolderInformationCard folderInformationCard;
    private DownloadsInformationCard downloadsInformationCard;
    private UploadsInformationCard uploadsInformationCard;
    private DebugInformationCard debugInformationCard;
    private NoticesCard noticesCard;

    private boolean showingFolder;

    private FolderInfo currentFolderInfo;

    /**
     * Constructor
     * 
     * @param controller
     */
    public InformationFrame(Controller controller) {
        super(controller);
        controller.getFolderRepository().addFolderRepositoryListener(
            new MyFolderRepositoryListener());
    }

    /**
     * Returns the ui component.
     * 
     * @return
     */
    public JFrame getUIComponent() {
        if (uiComponent == null) {
            initialize();
            buildUIComponent();
        }
        return uiComponent;
    }

    /**
     * Builds the UI component.
     */
    private void buildUIComponent() {
        JFrame mainFrame = getUIController().getMainFrame().getUIComponent();
        Preferences prefs = getController().getPreferences();

        int y = prefs.getInt("infoframe4.y", mainFrame.getY());
        int x = prefs.getInt("infoframe4.x", 50);
        uiComponent.setLocation(x, y);

        // Pack elements
        uiComponent.pack();

        int width = prefs.getInt("infoframe4.width", mainFrame.getLocation().x
            - x - 10);
        int height = prefs.getInt("infoframe4.height", mainFrame.getHeight());
        if (width < 50) {
            width = 50;
        }
        if (height < 50) {
            height = 50;
        }
        uiComponent.setSize(width, height);

        if (prefs.getBoolean("infoframe4.maximized", false)) {
            // Fix Synthetica maximization, otherwise it covers the task bar.
            // See http://www.javasoft.de/jsf/public/products/synthetica/faq#q13
            RootPaneUI ui = uiComponent.getRootPane().getUI();
            if (ui instanceof SyntheticaRootPaneUI) {
                ((SyntheticaRootPaneUI) ui).setMaximizedBounds(uiComponent);
            }
            uiComponent.setExtendedState(Frame.MAXIMIZED_BOTH);
        }

        // everything is decided in window listener
        uiComponent.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    }

    /**
     * Initializes the components.
     */
    private void initialize() {
        uiComponent = new JFrame();
        uiComponent.addWindowFocusListener(new WindowFocusListener() {
            public void windowGainedFocus(WindowEvent e) {
                getUIController().setActiveFrame(UIController.INFO_FRAME_ID);
            }

            public void windowLostFocus(WindowEvent e) {
                // Ignore.
            }
        });

        uiComponent.setIconImage(Icons.getImageById(Icons.SMALL_LOGO));
    }

    /**
     * Stores all current window valus.
     */
    public void storeValues() {
        Preferences prefs = getController().getPreferences();
        if (uiComponent == null) {
            return;
        }
        if ((uiComponent.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH)
        {
            prefs.putBoolean("infoframe4.maximized", true);
        } else {
            prefs.putInt("infoframe4.x", uiComponent.getX());
            if (uiComponent.getWidth() > 0) {
                prefs.putInt("infoframe4.width", uiComponent.getWidth());
            }
            prefs.putInt("infoframe4.y", uiComponent.getY());
            if (uiComponent.getHeight() > 0) {
                prefs.putInt("infoframe4.height", uiComponent.getHeight());
            }
            prefs.putBoolean("infoframe4.maximized", false);
        }
    }

    public boolean isShowingFolder() {
        return showingFolder;
    }

    /**
     * Displays file info for a folder.
     * 
     * @param folderInfo
     */
    public void displayFolderFiles(FolderInfo folderInfo) {
        buildFolderInformationCard();
        folderInformationCard.setFolderInfo(folderInfo);
        folderInformationCard.showFiles();
        displayCard(folderInformationCard);
        showingFolder = true;
        currentFolderInfo = folderInfo;
    }

    /**
     * Displays file info for a folder with filter set to new and sort set to
     * date descending.
     * 
     * @param folderInfo
     */
    public void displayFolderFilesLatest(FolderInfo folderInfo) {
        buildFolderInformationCard();
        folderInformationCard.setFolderInfoLatest(folderInfo);
        folderInformationCard.showFiles();
        displayCard(folderInformationCard);
        showingFolder = true;
        currentFolderInfo = folderInfo;
    }

    /**
     * Displays file info for a folder with filter set to incoming.
     * 
     * @param folderInfo
     */
    public void displayFolderFilesIncoming(FolderInfo folderInfo) {
        buildFolderInformationCard();
        folderInformationCard.setFolderInfoIncoming(folderInfo);
        folderInformationCard.showFiles();
        displayCard(folderInformationCard);
        showingFolder = true;
        currentFolderInfo = folderInfo;
    }

    /**
     * Displays settings info for a folder
     * 
     * @param folderInfo
     */
    public void displayFolderSettings(FolderInfo folderInfo) {
        buildFolderInformationCard();
        folderInformationCard.setFolderInfo(folderInfo);
        folderInformationCard.showSettings();
        displayCard(folderInformationCard);
        showingFolder = true;
        currentFolderInfo = folderInfo;
    }

    /**
     * Displays folder member info
     * 
     * @param folderInfo
     */
    public void displayFolderMembers(FolderInfo folderInfo) {
        buildFolderInformationCard();
        folderInformationCard.setFolderInfo(folderInfo);
        folderInformationCard.showMembers();
        displayCard(folderInformationCard);
        showingFolder = true;
        currentFolderInfo = folderInfo;
    }

    /**
     * Displays folder problems
     * 
     * @param folderInfo
     */
    public void displayFolderProblems(FolderInfo folderInfo) {
        buildFolderInformationCard();
        folderInformationCard.setFolderInfo(folderInfo);
        folderInformationCard.showProblems();
        displayCard(folderInformationCard);
        showingFolder = true;
        currentFolderInfo = folderInfo;
    }

    public void displayDownloads() {
        buildDownloadsInformationCard();
        displayCard(downloadsInformationCard);
        showingFolder = false;
    }

    public void displayUploads() {
        buildUploadsInformationCard();
        displayCard(uploadsInformationCard);
        showingFolder = false;
    }

    public void displayDebug() {
        buildDebugInformationCard();
        displayCard(debugInformationCard);
        showingFolder = false;
    }

    public void displayNotices() {
        buildNoticesCard();
        displayCard(noticesCard);
        showingFolder = false;
    }

    /**
     * Displays a card with tile and icon.
     * 
     * @param card
     */
    public void displayCard(InformationCard card) {
        getUIComponent().setIconImage(card.getCardImage());
        getUIComponent().setTitle(card.getCardTitle());
        getUIComponent().getContentPane().removeAll();
        getUIComponent().getContentPane().add(card.getUIComponent());
    }

    /**
     * Builds the local FolderInformationCard if required.
     */
    private void buildFolderInformationCard() {
        if (folderInformationCard == null) {
            folderInformationCard = new FolderInformationCard(getController());
        }
    }

    /**
     * Builds the local FolderInformationCard if required.
     */
    private void buildDownloadsInformationCard() {
        if (downloadsInformationCard == null) {
            downloadsInformationCard = new DownloadsInformationCard(
                getController());
        }
    }

    /**
     * Builds the local FolderInformationCard if required.
     */
    private void buildUploadsInformationCard() {
        if (uploadsInformationCard == null) {
            uploadsInformationCard = new UploadsInformationCard(getController());
        }
    }

    /**
     * Builds the local DebugInformationCard if required.
     */
    private void buildDebugInformationCard() {
        if (debugInformationCard == null) {
            debugInformationCard = new DebugInformationCard(getController());
        }
    }

    /**
     * Builds the local NoticesCard if required.
     */
    private void buildNoticesCard() {
        if (noticesCard == null) {
            noticesCard = new NoticesCard(getController());
        }
    }

    /**
     * Fires when a folder is removed. Hide this if showing the folder.
     * 
     * @param folderInfo
     */
    private void removedFolder(FolderInfo folderInfo) {
        if (showingFolder && currentFolderInfo != null
            && currentFolderInfo.equals(folderInfo))
        {
            getUIComponent().setVisible(false);
        }
    }

    private class MyFolderRepositoryListener implements
        FolderRepositoryListener
    {

        public void folderRemoved(FolderRepositoryEvent e) {
            removedFolder(e.getFolderInfo());
        }

        public void folderCreated(FolderRepositoryEvent e) {
            if (getController().isUIOpen() && showingFolder) {
                displayFolderFiles(e.getFolderInfo());
            }
        }

        public void maintenanceStarted(FolderRepositoryEvent e) {
            // Don't care.
        }

        public void maintenanceFinished(FolderRepositoryEvent e) {
            // Don't care.
        }

        public boolean fireInEventDispatchThread() {
            return true;
        }
    }
}
