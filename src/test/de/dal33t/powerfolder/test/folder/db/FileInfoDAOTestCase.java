/*
 * Copyright 2004 - 2009 Christian Sprajc. All rights reserved.
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
package de.dal33t.powerfolder.test.folder.db;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

import de.dal33t.powerfolder.disk.dao.FileInfoDAO;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.light.FileInfoFactory;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.light.MemberInfo;
import de.dal33t.powerfolder.util.IdGenerator;
import de.dal33t.powerfolder.util.test.ControllerTestCase;

public abstract class FileInfoDAOTestCase extends ControllerTestCase {

    protected void testFindAll(FileInfoDAO dao, int n) {
        Collection<FileInfo> fInfos = new ArrayList<FileInfo>();
        for (int i = 0; i < n; i++) {
            fInfos.add(createRandomFileInfo(i, IdGenerator.makeId()));
        }
        dao.store(null, fInfos);
        fInfos.clear();
        for (int i = 0; i < n; i++) {
            fInfos.add(createRandomFileInfo(i, IdGenerator.makeId()));
        }
        dao.store("XXX", fInfos);
        fInfos.clear();
        for (int i = 0; i < n; i++) {
            fInfos.add(createRandomFileInfo(i, IdGenerator.makeId()));
        }
        dao.store("WWW", fInfos);

        assertEquals(n, dao.count(null));
        assertEquals(n, dao.count("XXX"));
        assertEquals(n, dao.count("WWW"));
        assertEquals(0, dao.count("123"));

        assertEquals(n, dao.findAllFiles(null).size());
        assertEquals(n, dao.findAllFiles("XXX").size());
        assertEquals(n, dao.findAllFiles("WWW").size());
        assertEquals(0, dao.findAllFiles("123").size());

        assertEquals(n, dao.findAllFiles(null).size());
        assertEquals(n, dao.findAllFiles("XXX").size());
        assertEquals(n, dao.findAllFiles("WWW").size());
        assertEquals(0, dao.findAllFiles("123").size());
    }

    protected void testFindNewestVersion(FileInfoDAO dao) {
        FileInfo expected = createRandomFileInfo(10, "MyExcelsheet.xls");
        expected = FileInfoFactory.updatedVersion(expected, 1);
        dao.store(null, expected);

        FileInfo remote = FileInfoFactory.updatedVersion(expected, 0);
        dao.store("REMOTE1", remote);

        FileInfo remote2 = FileInfoFactory.updatedVersion(expected, 2);
        dao.store("REMOTE2", remote2);

        assertNotNull(dao.findNewestVersion(expected, ""));
        assertEquals(1, dao.findNewestVersion(expected, "").getVersion());
        assertNotNull(dao.findNewestVersion(expected, "REMOTE1", "REMOTE2",
            null));
        assertEquals(2, dao.findNewestVersion(expected, "REMOTE1", "REMOTE2",
            null).getVersion());
        assertNotNull(dao.findNewestVersion(expected, "REMOTE1", "REMOTE2"));
        assertEquals(2, dao.findNewestVersion(expected, "REMOTE1", "REMOTE2")
            .getVersion());
    }

    protected void testIndexFileInfo(FileInfoDAO dao) {
        FileInfo expected = createRandomFileInfo(10, "MyExcelsheet.xls");
        dao.store(null, expected);
        FileInfo retrieved = dao.find(FileInfoFactory.lookupInstance(expected
            .getFolderInfo(), expected.getRelativeName()), null);
        assertNotNull("Retrieved FileInfo is null", retrieved);
        assertEquals(expected, retrieved);

        // Should overwrite
        dao.store(null, retrieved);

        assertEquals(1, dao.findAllFiles(null).size());
        assertEquals(1, dao.count(null));
    }

    protected static FileInfo createRandomFileInfo(int n, String name) {
        FolderInfo foInfo = createRandomFolderInfo();
        String fn = "subdir1/SUBDIR2/" + name + "-" + n;
        MemberInfo mInfo = new MemberInfo(IdGenerator.makeId(), IdGenerator
            .makeId(), IdGenerator.makeId());
        return FileInfoFactory.unmarshallExistingFile(foInfo, fn, (long) Math.random()
            * Long.MAX_VALUE, mInfo, new Date(), 0, false);
    }

    protected static FolderInfo createRandomFolderInfo() {
        FolderInfo foInfo = new FolderInfo("TestFolder / " + UUID.randomUUID(),
            IdGenerator.makeId());
        return foInfo;
    }
}
