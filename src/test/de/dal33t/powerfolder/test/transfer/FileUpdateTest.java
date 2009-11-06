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
* $Id: AddLicenseHeader.java 4282 2008-06-16 03:25:09Z tot $
*/
package de.dal33t.powerfolder.test.transfer;

import java.io.File;

import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.util.test.Condition;
import de.dal33t.powerfolder.util.test.TestHelper;
import de.dal33t.powerfolder.util.test.TwoControllerTestCase;

/**
 * Tests the correct updating of files.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc</a>
 * @version $Revision: 1.5 $
 */
public class FileUpdateTest extends TwoControllerTestCase {
    private static final String TEST_FILENAME = "TestFile.bin";
    private static final byte[] SMALLER_FILE_CONTENTS = "Changed/smaller"
        .getBytes();
    private static final byte[] LONG_FILE_CONTENTS = "Some test file with long contents"
        .getBytes();

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        connectBartAndLisa();
        // Join on testfolder
        joinTestFolder(SyncProfile.HOST_FILES);
    }

    /**
     * Test the inital sync of two files with same name and place but diffrent
     * modification dates.
     * <p>
     * Ticket #345
     */
    public void testInitalSync() {
        File fileAtBart = TestHelper.createTestFile(getFolderAtBart()
            .getLocalBase(), "TestInitalFile.bin",
            "A older version of the file".getBytes());
        // File @ Bart was modified one days before (=newer)
        fileAtBart.setLastModified(System.currentTimeMillis() - 1000 * 60 * 60
            * 24 * 1);

        File fileAtLisa = TestHelper.createTestFile(getFolderAtLisa()
            .getLocalBase(), "TestInitalFile.bin",
            "My newest version of the file".getBytes());
        // File @ Lisa was modified three days before (=older)
        fileAtLisa.setLastModified(System.currentTimeMillis() - 1000 * 60 * 60
            * 24 * 3);

        // Let them scan the new content
        scanFolder(getFolderAtBart());
        scanFolder(getFolderAtLisa());
        TestHelper.waitForCondition(20, new Condition() {
            public boolean reached() {
                return getFolderAtBart().getKnownFilesCount() == 1
                    && getFolderAtLisa().getKnownFilesCount() == 1;
            }
        });

        assertFileMatch(fileAtBart, getFolderAtBart().getKnownFiles().iterator().next(),
            getContollerBart());
        assertFileMatch(fileAtLisa, getFolderAtLisa().getKnownFiles().iterator().next(),
            getContollerLisa());

        // Now let them sync with auto-download
        getFolderAtBart().setSyncProfile(SyncProfile.AUTOMATIC_DOWNLOAD);
        getFolderAtLisa().setSyncProfile(SyncProfile.AUTOMATIC_DOWNLOAD);

        // Let the copy
        TestHelper.waitForCondition(5, new Condition() {
            public boolean reached() {
                return getContollerLisa().getTransferManager()
                    .countCompletedDownloads() == 1;
            }
        });

        // Test barts file (=newer)
        FileInfo fileInfoAtBart = getFolderAtBart().getKnownFiles().iterator().next();
        assertEquals(0, fileInfoAtBart.getVersion());
        assertEquals(fileAtBart.getName(), fileInfoAtBart.getFilenameOnly());
        assertEquals(fileAtBart.length(), fileInfoAtBart.getSize());
        assertEquals(fileAtBart.lastModified(), fileInfoAtBart
            .getModifiedDate().getTime());
        assertEquals(getContollerBart().getMySelf().getInfo(), fileInfoAtBart
            .getModifiedBy());

        // Test lisas file (=should be override by barts newer file)
        FileInfo fileInfoAtLisa = getFolderAtLisa().getKnownFiles().iterator().next();
        assertFileMatch(fileAtLisa, getFolderAtLisa().getKnownFiles().iterator().next(),
            getContollerLisa());
        assertTrue(fileInfoAtLisa.inSyncWithDisk(fileAtLisa));
        assertEquals(fileAtBart.getName(), fileInfoAtLisa.getFilenameOnly());
        assertEquals(fileAtBart.length(), fileInfoAtLisa.getSize());
        assertEquals(fileAtBart.lastModified(), fileInfoAtLisa
            .getModifiedDate().getTime());
        assertEquals(getContollerBart().getMySelf().getInfo(), fileInfoAtLisa
            .getModifiedBy());
    }

    /**
     * Tests the when the internal db is out of sync with the disk. Ticket #387
     */
    public void testFileChangedOnDisk() {
        File fileAtBart = TestHelper.createTestFile(getFolderAtBart()
            .getLocalBase(), TEST_FILENAME, LONG_FILE_CONTENTS);
        // Scan the file
        scanFolder(getFolderAtBart());
        assertEquals(1, getFolderAtBart().getKnownFilesCount());

        // Change the file on disk. make it shorter.
        fileAtBart = TestHelper.createTestFile(
            getFolderAtBart().getLocalBase(), TEST_FILENAME,
            SMALLER_FILE_CONTENTS);
        // Now the DB of Barts folder is out of sync with the disk!
        // = disk
        assertEquals(SMALLER_FILE_CONTENTS.length, fileAtBart.length());
        // = db
        assertEquals(LONG_FILE_CONTENTS.length, getFolderAtBart()
            .getKnownFiles().iterator().next().getSize());
        assertNotSame(fileAtBart.length(), getFolderAtBart().getKnownFiles().iterator().next()
            .getSize());

        // Change sync profile = auto download.
        getFolderAtLisa().setSyncProfile(SyncProfile.AUTOMATIC_DOWNLOAD);

        // Abort of upload should have been sent to lisa = NO download.
        TestHelper.waitForCondition(10, new Condition() {
            public boolean reached() {
                return getContollerLisa().getTransferManager()
                    .countNumberOfDownloads(getFolderAtLisa()) == 0;
            }
        });
        assertEquals("Lisa has a stuck download", 0, getContollerLisa()
            .getTransferManager().countNumberOfDownloads(getFolderAtLisa()));

        // Now trigger barts folders mainteance, detect new file.
        getContollerBart().getFolderRepository().triggerMaintenance();
        TestHelper.waitMilliSeconds(1000);
        // and trigger Lisas transfer check, detect broken download
        getContollerLisa().getTransferManager().triggerTransfersCheck();
        TestHelper.waitMilliSeconds(5000);

        // Download stays forever
        assertEquals("Lisa has a stuck download", 0, getContollerLisa()
            .getTransferManager().countNumberOfDownloads(getFolderAtLisa()));
    }
}
