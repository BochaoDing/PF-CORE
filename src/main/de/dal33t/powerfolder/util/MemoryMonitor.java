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
package de.dal33t.powerfolder.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.ui.WikiLinks;
import de.dal33t.powerfolder.ui.notices.WarningNotice;
import de.dal33t.powerfolder.util.os.OSUtil;
import de.dal33t.powerfolder.util.ui.DialogFactory;
import de.dal33t.powerfolder.util.ui.GenericDialogType;

/**
 * Detects if PowerFolder is running out of memory.
 */
public class MemoryMonitor implements Runnable {

    private static final Logger log = Logger.getLogger(MemoryMonitor.class
        .getName());

    private Controller controller;
    private boolean runAlready;

    public MemoryMonitor(Controller controller) {
        this.controller = controller;
    }

    public void run() {

        // Do not show dialog repeatedly.
        if (runAlready) {
            return;
        }

        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        log.fine("Max Memory: " + Format.formatBytesShort(maxMemory)
            + ", Total Memory: " + Format.formatBytesShort(totalMemory));

        if (maxMemory == totalMemory) {
            addWarning();
            runAlready = true;
        }
    }

    /**
     * Add a warning event for the user.
     */
    private void addWarning() {
        WarningNotice notice = new WarningNotice(
                Translation.getTranslation("warning_notice.title"),
                Translation.getTranslation("warning_notice.low_memory"),
                new Runnable() {
            public void run() {
                if (OSUtil.isWindowsSystem() && !OSUtil.isWebStart()) {
                    int response = DialogFactory
                        .genericDialog(controller, Translation
                            .getTranslation("low_memory.title"), Translation
                            .getTranslation("low_memory.text", Help
                                .getWikiArticleURL(controller,
                                    WikiLinks.MEMORY_CONFIGURATION)),
                            new String[]{
                                Translation
                                    .getTranslation("low_memory.increase"),
                                Translation
                                    .getTranslation("low_memory.do_nothing")},
                            0, GenericDialogType.WARN);
                    if (response == 0) { // Increase memory
                        increaseAvailableMemory();
                    }
                } else {
                    // No ini - Can only warn user.
                    DialogFactory.genericDialog(controller, Translation
                        .getTranslation("low_memory.title"), Translation
                        .getTranslation("low_memory.warn"),
                        new String[]{Translation.getTranslation("general.ok")},
                        0, GenericDialogType.WARN);
                }
            }
        });
        controller.getUIController().getApplicationModel()
            .getNoticesModel().addNotice(notice);
    }

    /**
     * Reconfigure ini from (initial) 54M to 256M max memory.
     */
    private void increaseAvailableMemory() {

        // Read the current ini file.
        boolean wroteNewIni = false;
        PrintWriter pw = null;
        try {
            // log.fine("Looking for ini...");
            // br = new BufferedReader(new FileReader("PowerFolder.ini"));
            // Loggable.logFineStatic(MemoryMonitor.class, "Found ini...");
            // String line;
            // boolean found = false;
            // while ((line = br.readLine()) != null) {
            // if (line.startsWith("-Xmx")) {
            // // Found default ini.
            // found = true;
            // Loggable.logFineStatic(MemoryMonitor.class,
            // "Found maximum memory line...");
            // }
            // }

            boolean alreadyMax = Runtime.getRuntime().totalMemory() / 1024 / 1024 > 500;
            // Write a new one if found.
            if (!alreadyMax) {
                pw = new PrintWriter(new FileWriter(controller.getL4JININame()));
                log.fine("Writing new ini...");
                pw.println("-Xms16m");
                pw.println("-Xmx512m");
                pw.println("-XX:MinHeapFreeRatio=10");
                pw.println("-XX:MaxHeapFreeRatio=20");
                pw.flush();
                wroteNewIni = true;
                log.fine("Wrote new ini...");
            }
        } catch (IOException e) {
            log.log(Level.FINE, "Problem reconfiguring ini: " + e.getMessage());
        } finally {
            // if (br != null) {
            // try {
            // br.close();
            // } catch (IOException e) {
            // // Ignore
            // }
            // }
            if (pw != null) {
                pw.close();
            }
        }

        // Show a response
        if (wroteNewIni) {
            DialogFactory.genericDialog(controller, Translation
                .getTranslation("low_memory.title"), Translation
                .getTranslation("low_memory.configure_success"),
                GenericDialogType.INFO);
        } else {
            DialogFactory.genericDialog(controller, Translation
                .getTranslation("low_memory.title"), Translation
                .getTranslation("low_memory.configure_failure"),
                GenericDialogType.WARN);
        }
    }
}
