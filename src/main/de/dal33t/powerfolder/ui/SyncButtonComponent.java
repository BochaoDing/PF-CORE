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
 * $Id: SyncButtonComponent.java 5500 2008-10-25 04:23:44Z harry $
 */
package de.dal33t.powerfolder.ui;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PFUIComponent;
import de.dal33t.powerfolder.event.*;
import de.dal33t.powerfolder.ui.action.SyncAllFoldersAction;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class to display and handle Sync All button.
 */
public class SyncButtonComponent extends PFUIComponent {

    private JLabel syncAllLabel;
    private final AtomicBoolean mouseOver;
    private final AtomicBoolean folderRepositorySyncing;
    private final AtomicBoolean mousePressed;

    /**
     * Constructor
     * 
     * @param controller
     */
    public SyncButtonComponent(Controller controller) {
        super(controller);
        mouseOver = new AtomicBoolean();
        folderRepositorySyncing = new AtomicBoolean();
        mousePressed = new AtomicBoolean();

        // Start listening for synchronizatoin stats.
        controller.getFolderRepository().addSynchronizationStatsListener(
                new MySynchronizationStatsListener());
        controller.getThreadPool().submit(new MyRunnable());
    }

    /**
     * @return the sync 'button'.
     */
    public Component getUIComponent() {
        if (syncAllLabel == null) {
            initComponents();
        }
        return syncAllLabel;
    }

    private void initComponents() {
        syncAllLabel = new JLabel(Icons
            .getIcon("icons/sync/normal/sync_00.png"));
        syncAllLabel.setToolTipText(Translation
            .getTranslation("action_sync_all_folders.description"));
        syncAllLabel.addMouseListener(new MyMouseAdapter());
    }

    private class MySynchronizationStatsListener implements SynchronizationStatsListener {
        public void synchronizationStatsChanged(SynchronizationStatsEvent event) {
            folderRepositorySyncing.set(event.isSynchronizing());
        }

        public boolean fireInEventDispatchThread() {
            // simple implementation - so do it now
            return false;
        }
    }

    /**
     * Class to handle mouse overs and clicks.
     */
    private class MyMouseAdapter extends MouseAdapter {

        public void mousePressed(MouseEvent e) {
            mousePressed.set(true);
            logFine("Triggering SyncAllFoldersAction perfomSync");
            SyncAllFoldersAction.perfomSync(getController());
        }

        public void mouseReleased(MouseEvent e) {
            mousePressed.set(false);
        }

        public void mouseEntered(MouseEvent e) {
            mouseOver.set(true);
        }

        public void mouseExited(MouseEvent e) {
            mouseOver.set(false);
        }
    }

    private class MyRunnable implements Runnable {
        public void run() {
            int index = 0;
            while (!getController().getThreadPool().isShutdown()) {
                try {
                    Thread.sleep(40);
                    if (folderRepositorySyncing.get()) {
                        index++;
                        if (index > 17) {
                            index = 0;
                        }
                    } else {
                        index = 0;
                    }

                    String numberString;
                    if (index > 9) {
                        numberString = String.valueOf(index);
                    } else {
                        numberString = '0' + String.valueOf(index);
                    }

                    String directoryString;
                    if (mouseOver.get()) {
                        if (mousePressed.get()) {
                            directoryString = "push";
                        } else {
                            directoryString = "hover";
                        }
                    } else {
                        directoryString = "normal";
                    }

                    String iconString = "icons/sync/" + directoryString
                        + "/sync_" + numberString + ".png";
                    final Icon icon = Icons.getIcon(iconString);
                    // Move into EDT, otherwise it violates the one thread EDT
                    // rule.
                    UIUtil.invokeAndWaitInEDT(new Runnable() {
                        public void run() {
                            syncAllLabel.setIcon(icon);
                        }
                    });

                } catch (InterruptedException e) {
                    // Ignore.
                }
            }
            logFine("Thread terminated");
        }
    }
}
