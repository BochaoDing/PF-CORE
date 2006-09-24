package de.dal33t.powerfolder.test.folder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.event.FolderRepositoryEvent;
import de.dal33t.powerfolder.event.FolderRepositoryListener;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.test.ControllerTestCase;
import de.dal33t.powerfolder.test.TestHelper;
import de.dal33t.powerfolder.test.TestHelper.Condition;

/**
 * Tests the scanning of file in the local folders.
 * <p>
 * TODO Test scan of folder which already has a database.
 * 
 * @author <a href="mailto:sprajc@riege.com">Christian Sprajc</a>
 * @version $Revision: 1.5 $
 */
public class ScanFolderTest extends ControllerTestCase {

    private boolean initalScanOver = false;
    private boolean scanned;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        setupTestFolder(SyncProfile.MANUAL_DOWNLOAD);

        getController().getFolderRepository().addFolderRepositoryListener(
            new MyFolderRepoListener());

        TestHelper.waitForCondition(20, new TestHelper.Condition() {
            public boolean reached() {
                return initalScanOver;
            }
        });
        System.out.println("Inital scan over, setup ready");
    }

    /**
     * Tests the scan of one single file, including updates, deletion and
     * restore of the file.
     */
    public void testScanSingleFile() {
        File file = TestHelper.createRandomFile(getFolder().getLocalBase(),
            10 + (int) (Math.random() * 100));

        scanFolder();
        assertEquals(1, getFolder().getFilesCount());
        assertEquals(0, getFolder().getFiles()[0].getVersion());
        matches(file, getFolder().getFiles()[0]);

        TestHelper.changeFile(file);
        scanFolder();
        assertEquals(1, getFolder().getFiles()[0].getVersion());
        matches(file, getFolder().getFiles()[0]);

        TestHelper.changeFile(file);
        scanFolder();
        assertEquals(2, getFolder().getFiles()[0].getVersion());
        matches(file, getFolder().getFiles()[0]);

        // Delete.
        assertTrue(file.delete());
        scanFolder();
        assertTrue(!file.exists());
        assertTrue(getFolder().getFiles()[0].isDeleted());
        assertEquals(3, getFolder().getFiles()[0].getVersion());
        matches(file, getFolder().getFiles()[0]);

        // Restore.
        TestHelper.createRandomFile(file.getParentFile(), file.getName());
        scanFolder();
        assertEquals(4, getFolder().getFiles()[0].getVersion());
        matches(file, getFolder().getFiles()[0]);

        // 15 more filechanges
        for (int i = 0; i < 15; i++) {
            TestHelper.changeFile(file);
            scanFolder();
            assertEquals(5 + i, getFolder().getFiles()[0].getVersion());
            matches(file, getFolder().getFiles()[0]);
        }

        // Do some afterchecks.
        assertEquals(1, getFolder().getFilesCount());
    }

    /**
     * Tests the scan of one single file in a subdirectory.
     */
    public void testScanSingleFileInSubdir() {
        File subdir = new File(getFolder().getLocalBase(),
            "subDir1/SUBDIR2.ext");
        assertTrue(subdir.mkdirs());
        File file = TestHelper.createRandomFile(subdir, 10 + (int) (Math
            .random() * 100));

        scanFolder();
        assertEquals(1, getFolder().getFilesCount());
        assertEquals(0, getFolder().getFiles()[0].getVersion());
        matches(file, getFolder().getFiles()[0]);

        TestHelper.changeFile(file);
        scanFolder();
        assertEquals(1, getFolder().getFiles()[0].getVersion());
        matches(file, getFolder().getFiles()[0]);

        // Delete.
        assertTrue(file.delete());
        scanFolder();
        assertTrue(!file.exists());
        assertTrue(getFolder().getFiles()[0].isDeleted());
        assertEquals(2, getFolder().getFiles()[0].getVersion());
        matches(file, getFolder().getFiles()[0]);

        // Restore.
        TestHelper.createRandomFile(file.getParentFile(), file.getName());
        scanFolder();
        assertEquals(3, getFolder().getFiles()[0].getVersion());
        matches(file, getFolder().getFiles()[0]);

        TestHelper.changeFile(file);
        scanFolder();
        assertEquals(4, getFolder().getFiles()[0].getVersion());
        matches(file, getFolder().getFiles()[0]);

        // Do some afterchecks.
        assertEquals(1, getFolder().getFilesCount());
    }

    public void testScanFileMovement() {
        File subdir = new File(getFolder().getLocalBase(),
            "subDir1/SUBDIR2.ext");
        assertTrue(subdir.mkdirs());
        File file = TestHelper.createRandomFile(subdir, 10 + (int) (Math
            .random() * 100));

        scanFolder();
        assertEquals(1, getFolder().getFilesCount());
        assertEquals(0, getFolder().getFiles()[0].getVersion());
        matches(file, getFolder().getFiles()[0]);

        // Move file one subdirectory up
        File destFile = new File(file.getParentFile().getParentFile(), file
            .getName());
        assertTrue(file.renameTo(destFile));
        scanFolder();

        // Should have two fileinfos: one deleted and one new.
        assertEquals(2, getFolder().getFilesCount());
        FileInfo destFileInfo = retrieveFileInfo(destFile);
        matches(destFile, destFileInfo);
        assertEquals(0, destFileInfo.getVersion());

        FileInfo srcFileInfo = retrieveFileInfo(file);
        matches(file, srcFileInfo);
        assertEquals(1, srcFileInfo.getVersion());
        assertTrue(srcFileInfo.isDeleted());
    }

    /**
     * Scans multiple files with several changes.
     */
    public void testMultipleFileScan() {

    }

    /**
     * Tests the scan of very many files.
     * <p>
     * TOT Notes: This test takes @ 11000 files aprox. 40-107 (86) seconds.
     */
    public void testScanExtremlyManyFiles() {
        final int nFiles = 11000;
        List<File> files = new ArrayList<File>();
        for (int i = 0; i < nFiles; i++) {
            files.add(TestHelper
                .createRandomFile(getFolder().getLocalBase(), 5));
        }
        scanFolder();
        assertEquals(nFiles, getFolder().getFilesCount());

        for (File file : files) {
            FileInfo fInfo = retrieveFileInfo(file);
            matches(file, fInfo);
        }
    }

    /**
     * Test the scan of file and dirs, that just change the case.
     * <p>
     * e.g. "TestDir/SubDir/MyFile.txt" to "testdir/subdir/myfile.txt"
     * <p>
     * TRAC #232
     */
    public void testCaseChangeScan() {
        File testFile = TestHelper.createRandomFile(getFolder().getLocalBase(),
            "TESTFILE.TXT");

        getFolder().forceScanOnNextMaintenance();
        getFolder().maintain();

        TestHelper.waitForCondition(10, new Condition() {
            public boolean reached() {
                return getFolder().getFilesCount() == 1;
            }
        });

        assertEquals(testFile.getName(), getFolder().getFiles()[0]
            .getFilenameOnly());

        // Change case
        testFile.renameTo(new File(getFolder().getLocalBase(), "testfile.txt"));

        scanFolder();

        // HOW TO HANDLE THAT? WHAT TO EXPECT??
        // assertEquals(1, getFolderAtBart().getFilesCount());
    }

    // Helper *****************************************************************

    /**
     * @param file
     * @return the fileinfo in the test folder for this file.
     */
    private FileInfo retrieveFileInfo(File file) {
        return getFolder().getFile(new FileInfo(getFolder(), file));
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
    private void matches(File diskFile, FileInfo fInfo) {
        boolean nameMatch = diskFile.getName().equals(fInfo.getFilenameOnly());
        boolean sizeMatch = diskFile.length() == fInfo.getSize();
        boolean fileObjectEquals = diskFile.equals(fInfo
            .getDiskFile(getController().getFolderRepository()));
        boolean deleteStatusMatch = diskFile.exists() == !fInfo.isDeleted();
        boolean lastModifiedMatch = diskFile.lastModified() == fInfo
            .getModifiedDate().getTime();

        // Skip last modification test when diskfile is deleted.
        boolean matches = !diskFile.isDirectory() && nameMatch && sizeMatch
            && (!diskFile.exists() || lastModifiedMatch) && deleteStatusMatch
            && fileObjectEquals;

        assertTrue("FileInfo does not match physical file. \nFileInfo:\n "
            + fInfo.toDetailString() + "\nFile:\n "
            + diskFile.getAbsolutePath() + "\n\nWhat matches?:\nName: "
            + nameMatch + "\nSize: " + sizeMatch + "\nlastModifiedMatch: "
            + lastModifiedMatch + "\ndeleteStatus: " + deleteStatusMatch
            + "\nFileObjectEquals: " + fileObjectEquals, matches);
    }

    /**
     * Scans a folder and waits for the scan to complete.
     */
    private void scanFolder() {
        scanned = false;
        getFolder().forceScanOnNextMaintenance();
        getController().getFolderRepository().triggerMaintenance();
        TestHelper.waitForCondition(50, new Condition() {
            public boolean reached() {
                return scanned;
            }
        });
        assertTrue("Folder was not scanned as requested", scanned);
    }

    private final class MyFolderRepoListener implements
        FolderRepositoryListener
    {
        public void folderCreated(FolderRepositoryEvent e) {
        }

        public void folderRemoved(FolderRepositoryEvent e) {
        }

        public void scansFinished(FolderRepositoryEvent e) {
            initalScanOver = true;
            scanned = true;
        }

        public void scansStarted(FolderRepositoryEvent e) {
        }

        public void unjoinedFolderAdded(FolderRepositoryEvent e) {
        }

        public void unjoinedFolderRemoved(FolderRepositoryEvent e) {
        }

        public boolean fireInEventDispathThread() {
            return false;
        }
    }
}
