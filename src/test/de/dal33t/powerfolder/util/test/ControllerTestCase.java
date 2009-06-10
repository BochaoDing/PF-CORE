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
package de.dal33t.powerfolder.util.test;

import java.io.File;
import java.util.Date;
import java.util.UUID;

import junit.framework.TestCase;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Feature;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.FolderSettings;
import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.util.ArchiveMode;
import de.dal33t.powerfolder.util.FileUtils;
import de.dal33t.powerfolder.util.Format;

/**
 * Provides basic testcase-setup with a controller.
 * <p>
 * After <code>#setUp()</code> is invoked it is ensured, that controller is
 * running
 * <p>
 * You can access the controller and do manupulating/testing stuff on it
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc</a>
 * @version $Revision: 1.2 $
 */
public class ControllerTestCase extends TestCase {
    // For the optional test folder.
    private static final File TESTFOLDER_BASEDIR = new File(TestHelper
        .getTestDir(), "/ControllerBart/testFolder");

    private Controller controller;

    // The optional test folders
    private Folder folder;

    @Override
    protected void setUp() throws Exception {
        System.setProperty("user.home", new File("build/test/home")
            .getCanonicalPath());
        super.setUp();

        Feature.setupForTests();

        // Cleanup
        TestHelper.cleanTestDir();

        // Copy fresh configs

        // Start controllers
        System.out.println("Starting controller...");
        controller = Controller.createController();
        File source = new File("src/test-resources/ControllerBart.config");
        File target = new File(Controller.getMiscFilesLocation(),
            "ControllerBart.config");
        FileUtils.copyFile(source, target);
        assertTrue(target.exists());

        controller.startConfig("ControllerBart");
        waitForStart(controller);
        // Wait for initial maintenance
        // triggerAndWaitForInitialMaitenenace(controller);
        controller.getPreferences().putBoolean("createdesktopshortcuts", false);

        System.out.println("Controller started");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        controller.shutdown();

        // Give them time to shut down
        Thread.sleep(1000);
        int i = 0;
        while (controller.isShuttingDown()) {
            i++;
            if (i > 100) {
                System.out.println("shutdown of controller 2 failed");
                break;
            }
            Thread.sleep(1000);
        }
        assertFalse(controller.isStarted());

    }

    // For subtest ************************************************************

    protected Controller getController() {
        return controller;
    }

    protected String getControllerNodeID() {
        return controller.getMySelf().getId();
    }

    // Helpers ****************************************************************

    /**
     * @see #setupTestFolder(SyncProfile)
     * @return the test folde. or null if not setup yet.
     */
    protected Folder getFolder() {
        return folder;
    }

    /**
     * Joins the controller into a testfolder. get these testfolder with
     * <code>getFolder()</code>.
     * 
     * @see #getFolder()
     * @param syncprofile
     * @param useRecycleBin
     *            whether to folder supports the recycle bin.
     */
    protected void setupTestFolder(SyncProfile syncprofile,
        boolean useRecycleBin, ArchiveMode archiveMode)
    {
        FolderInfo testFolder = new FolderInfo("testFolder", UUID.randomUUID()
            .toString());
        folder = joinFolder(testFolder, TESTFOLDER_BASEDIR, syncprofile,
            useRecycleBin, archiveMode);
        System.out.println(folder.getLocalBase());
    }

    /**
     * Joins the controller into a testfolder. get these testfolder with
     * <code>getFolder()</code>. Uses recycle bin.
     * 
     * @see #getFolder()
     */
    protected void setupTestFolder(SyncProfile syncprofile) {
        setupTestFolder(syncprofile, true, ArchiveMode.NO_BACKUP);
    }

    /**
     * Let the controller join the specified folder.
     * 
     * @param foInfo
     *            the folder to join
     * @param baseDir
     *            the local base dir for the controller
     * @param profile
     *            the profile to use
     * @return the folder joined
     */
    protected Folder joinFolder(FolderInfo foInfo, File baseDir,
        SyncProfile profile, boolean useRecycleBin, ArchiveMode archiveMode)
    {
        FolderSettings folderSettings = new FolderSettings(baseDir, profile,
            false, useRecycleBin, archiveMode);
        return getController().getFolderRepository().createFolder(foInfo,
            folderSettings);
    }

    /**
     * Scans a folder and waits for the scan to complete.
     */
    protected synchronized void scanFolder(Folder aFolders) {
        TestHelper.scanFolder(aFolders);
    }

    /**
     * Waits for the controller to startup
     * 
     * @param aController
     * @throws InterruptedException
     */
    protected void waitForStart(Controller aController)
        throws InterruptedException
    {
        int i = 0;
        while (!aController.isStarted()) {
            i++;
            Thread.sleep(100);
            if (i > 100) {
                fail("Unable to start controller");
            }
        }
    }

    /**
     * Tests if the diskfile matches the fileinfo. Checks name, lenght/size,
     * modification date and the deletion status.
     * 
     * @param diskFile
     *            the diskfile to compare
     * @param fInfo
     *            the fileinfo
     */
    protected void assertFileMatch(File diskFile, FileInfo fInfo) {
        boolean nameMatch = diskFile.getName().equals(fInfo.getFilenameOnly());
        boolean sizeMatch = diskFile.length() == fInfo.getSize();
        boolean fileObjectEquals = diskFile.equals(fInfo.getDiskFile(controller
            .getFolderRepository()));
        boolean deleteStatusMatch = diskFile.exists() == !fInfo.isDeleted();
        boolean lastModifiedMatch = diskFile.lastModified() == fInfo
            .getModifiedDate().getTime();

        // Skip last modification test when diskfile is deleted.
        boolean matches = !diskFile.isDirectory() && nameMatch && sizeMatch
            && (!diskFile.exists() || lastModifiedMatch) && deleteStatusMatch
            && fileObjectEquals;

        assertTrue("FileInfo does not match physical file. \nFileInfo:\n "
            + fInfo.toDetailString() + "\nFile:\n "
            + (diskFile.exists() ? "" : "(del) ") + diskFile.getName()
            + ", size: " + Format.formatBytes(diskFile.length())
            + ", lastModified: " + new Date(diskFile.lastModified()) + " ("
            + diskFile.lastModified() + ")" + "\n\nWhat matches?:\nName: "
            + nameMatch + "\nSize: " + sizeMatch + "\nlastModifiedMatch: "
            + lastModifiedMatch + "\ndeleteStatus: " + deleteStatusMatch
            + "\nFileObjectEquals: " + fileObjectEquals, matches);
    }
}
