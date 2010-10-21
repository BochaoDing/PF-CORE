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
 * $Id: FoldersTab.java 5495 2008-10-24 04:59:13Z harry $
 */
package de.dal33t.powerfolder.ui.folders;

import com.jgoodies.forms.builder.ButtonBarBuilder;
import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PFUIComponent;
import de.dal33t.powerfolder.ui.FileDropTransferHandler;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.ui.UIUtil;

import javax.swing.*;

/**
 * Class to display the forders tab.
 */
public class FoldersTab extends PFUIComponent {

    private JPanel uiComponent;
    private FoldersList foldersList;
    private JScrollPane scrollPane;
    private JLabel emptyLabel;

    /**
     * Constructor
     * 
     * @param controller
     */
    public FoldersTab(Controller controller) {
        super(controller);
        emptyLabel = new JLabel(Translation
            .getTranslation("folders_tab.no_folders_available"),
            SwingConstants.CENTER);
        emptyLabel.setEnabled(false);
        foldersList = new FoldersList(getController(), this);
    }

    /**
     * Returns the ui component.
     * 
     * @return
     */
    public JPanel getUIComponent() {
        if (uiComponent == null) {
            buildUI();
        }
        uiComponent.setTransferHandler(new FileDropTransferHandler(
            getController()));
        return uiComponent;
    }

    /**
     * Builds the ui component.
     */
    private void buildUI() {

        // Build ui
        FormLayout layout = new FormLayout("pref:grow",
            "3dlu, pref, 3dlu, pref, 3dlu, fill:0:grow");
        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();

        JPanel toolbar = createToolBar();
        builder.add(toolbar, cc.xy(1, 2));
        builder.addSeparator(null, cc.xy(1, 4));
        scrollPane = new JScrollPane(foldersList.getUIComponent());
        scrollPane.getVerticalScrollBar().setUnitIncrement(10);
        foldersList.setScroller(scrollPane);
        UIUtil.removeBorder(scrollPane);

        // emptyLabel and scrollPane occupy the same slot.
        builder.add(emptyLabel, cc.xy(1, 6));
        builder.add(scrollPane, cc.xy(1, 6));

        uiComponent = builder.getPanel();

        updateEmptyLabel();

    }

    public void updateEmptyLabel() {
        if (foldersList != null) {
            if (emptyLabel != null) {
                emptyLabel.setVisible(foldersList.isEmpty());
            }
            if (scrollPane != null) {
                scrollPane.setVisible(!foldersList.isEmpty());
            }
        }
    }

    /**
     * @return the toolbar
     */
    private JPanel createToolBar() {
        JButton newFolderButton = new JButton(getApplicationModel()
            .getActionModel().getNewFolderAction());

        // Same width of the buttons please
        JButton searchComputerButton = new JButton(getApplicationModel()
            .getActionModel().getFindComputersAction());
        newFolderButton.setMinimumSize(searchComputerButton.getMinimumSize());
        newFolderButton.setMaximumSize(searchComputerButton.getMaximumSize());
        newFolderButton.setPreferredSize(searchComputerButton
            .getPreferredSize());
        searchComputerButton.setVisible(false);

        ButtonBarBuilder bar = ButtonBarBuilder.createLeftToRightBuilder();
        bar.setBorder(Borders.createEmptyBorder("0, 3dlu, 0, 0"));
        bar.addGridded(newFolderButton);
        bar.addRelatedGap();
        bar.addGridded(searchComputerButton);
        return bar.getPanel();
    }

    /**
     * Populates the folders in the list.
     */
    public void populate() {
        foldersList.populate();
    }
}
