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
* $Id: InformationFilesCard.java 5457 2008-10-17 14:25:41Z harry $
*/
package de.dal33t.powerfolder.ui.information.folder;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.disk.problem.ProblemListener;
import de.dal33t.powerfolder.disk.problem.Problem;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.ui.Icons;
import de.dal33t.powerfolder.ui.information.InformationCard;
import de.dal33t.powerfolder.ui.information.folder.files.FilesTab;
import de.dal33t.powerfolder.ui.information.folder.members.MembersTab;
import de.dal33t.powerfolder.ui.information.folder.settings.SettingsTab;
import de.dal33t.powerfolder.ui.information.folder.problems.ProblemsTab;
import de.dal33t.powerfolder.util.Translation;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Information card for a folder. Includes files, members and settings tabs.
 */
public class FolderInformationCard extends InformationCard {

    private static final int TAB_FILES = 0;
    private static final int TAB_MEMBERS = 1;
    private static final int TAB_SETTIGNS = 2;
    private static final int TAB_PROBLEMS = 3; // Must be the last tab.

    private FolderInfo folderInfo;
    private JTabbedPane tabbedPane;
    private FilesTab filesTab;
    private MembersTab membersTab;
    private SettingsTab settingsTab;
    private ProblemsTab problemsTab;

    private final ProblemListener problemListener;

    /**
     * Constructor
     *
     * @param controller
     */
    public FolderInformationCard(Controller controller) {
        super(controller);
        filesTab = new FilesTab(getController());
        membersTab = new MembersTab(getController());
        settingsTab = new SettingsTab(getController());
        problemsTab = new ProblemsTab(getController());
        problemListener = new MyProblemListener();

        initialize();
        buildUIComponent();

        updateProblems();
    }

    /**
     * Sets the folder in the tabs.
     *
     * @param folderInfo
     * @param directoryFilterMode
     */
    public void setFolderInfo(FolderInfo folderInfo, int directoryFilterMode) {
        detachProblemListener();
        this.folderInfo = folderInfo;
        filesTab.setFolderInfo(folderInfo, directoryFilterMode);
        membersTab.setFolderInfo(folderInfo);
        settingsTab.setFolderInfo(folderInfo);
        problemsTab.setFolderInfo(folderInfo);
        atachProblemListener();
        updateProblems();
    }

    /**
     * Sets the folder in the tabs with local and incoming set and sort date
     * descending.
     *
     * @param folderInfo
     */
    public void setFolderInfoLatest(FolderInfo folderInfo) {
        detachProblemListener();
        this.folderInfo = folderInfo;
        filesTab.setFolderInfoLatest(folderInfo);
        membersTab.setFolderInfo(folderInfo);
        settingsTab.setFolderInfo(folderInfo);
        problemsTab.setFolderInfo(folderInfo);
        atachProblemListener();
        updateProblems();
    }

    private void detachProblemListener() {

        if (folderInfo != null) {
            Folder folder = getController().getFolderRepository()
                    .getFolder(folderInfo);
            if (folder != null) {
                folder.removeProblemListener(problemListener);
            }
        }
    }

    private void atachProblemListener() {
        getController().getFolderRepository().getFolder(folderInfo)
                .addProblemListener(problemListener);
    }

    /**
     * Control the folder's problems from here so that the tab can be removed
     * if there are no poblems.
     */
    private void updateProblems() {
        if (folderInfo == null) {
            //  No fi, no show.
            removeProblemsTab();
        } else {
            List<Problem> problemList = getController().getFolderRepository()
                    .getFolder(folderInfo).getProblems();
            if (problemList.isEmpty()) {
                removeProblemsTab();
            } else {
                addProblemsTab();
            }
            problemsTab.updateProblems(problemList);
        }
    }

    /**
     * Add the problems tab to the pane.
     */
    private void addProblemsTab() {
        tabbedPane.addTab(Translation.getTranslation(
                "folder_information_card.problems.title"),
                problemsTab.getUIComponent());
        tabbedPane.setIconAt(TAB_PROBLEMS, Icons.getIconById(Icons.PROBLEMS));
        tabbedPane.setToolTipTextAt(TAB_PROBLEMS, Translation.getTranslation(
                "folder_information_card.problems.tips"));
    }

    /**
     * Remove the problems tab if displayed.
     */
    private void removeProblemsTab() {
        if (tabbedPane.getComponentCount() >= 1 + TAB_PROBLEMS) {
            tabbedPane.remove(TAB_PROBLEMS);
        }
    }

    /**
     * Gets the image for the card.
     *
     * @return
     */
    public Image getCardImage() {
        return Icons.getImageById(Icons.FOLDER);
    }

    /**
     * Gets the title for the card.
     *
     * @return
     */
    public String getCardTitle() {
        return folderInfo.name;
    }

    /**
     * Gets the ui component after initializing and building if necessary
     *
     * @return
     */
    public JComponent getUIComponent() {
        return tabbedPane;
    }

    /**
     * Initialize components
     */
    private void initialize() {
        tabbedPane = new JTabbedPane();
    }

    /**
     * Build the ui component tab pane.
     */
    private void buildUIComponent() {
        tabbedPane.addTab(Translation.getTranslation(
                "folder_information_card.files.title"),
                filesTab.getUIComponent());
        tabbedPane.setIconAt(TAB_FILES, Icons.getIconById(Icons.FILES));
        tabbedPane.setToolTipTextAt(TAB_FILES, Translation.getTranslation(
                "folder_information_card.files.tips"));

        tabbedPane.addTab(Translation.getTranslation(
                "folder_information_card.members.title"),
                membersTab.getUIComponent());
        tabbedPane.setIconAt(TAB_MEMBERS, Icons.getIconById(Icons.NODE_FRIEND_CONNECTED));
        tabbedPane.setToolTipTextAt(TAB_MEMBERS, Translation.getTranslation(
                "folder_information_card.members.tips"));

        tabbedPane.addTab(Translation.getTranslation(
                "folder_information_card.settings.title"),
                settingsTab.getUIComponent());
        tabbedPane.setIconAt(TAB_SETTIGNS, Icons.getIconById(Icons.SETTINGS));
        tabbedPane.setToolTipTextAt(TAB_SETTIGNS, Translation.getTranslation(
                "folder_information_card.settings.tips"));

    }

    /**
     * Display the files tab.
     */
    public void showFiles() {
        ((JTabbedPane) getUIComponent()).setSelectedIndex(TAB_FILES);
    }

    /**
     * Display the members tab.
     */
    public void showMembers() {
        ((JTabbedPane) getUIComponent()).setSelectedIndex(TAB_MEMBERS);
    }

    /**
     * Display the settings tab.
     */
    public void showSettings() {
        ((JTabbedPane) getUIComponent()).setSelectedIndex(TAB_SETTIGNS);
    }

    /**
     * Display the problems tab.
     */
    public void showProblems() {
        ((JTabbedPane) getUIComponent()).setSelectedIndex(TAB_PROBLEMS);
    }

    private class MyProblemListener implements ProblemListener {
        public void problemAdded(Problem problem) {
            updateProblems();
        }

        public void problemRemoved(Problem problem) {
            updateProblems();
        }

        public boolean fireInEventDispatchThread() {
            return true;
        }
    }

}