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
package de.dal33t.powerfolder.light;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.DiskItem;
import de.dal33t.powerfolder.Feature;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.FolderRepository;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.StringUtils;
import de.dal33t.powerfolder.util.Util;
import de.dal33t.powerfolder.util.DateUtil;
import de.dal33t.powerfolder.util.os.OSUtil;

/**
 * File information of a local or remote file. NEVER USE A CONSTRUCTOR OF THIS
 * CLASS. YOU ARE DOING IT WRONG!. Use {@link FileInfoFactory}
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.33 $
 */
public class FileInfo implements Serializable, DiskItem, Cloneable {

    /**
     * #1531: If this system should ignore cases of files in
     * {@link #equals(Object)} and {@link #hashCode()}
     */
    public static final boolean IGNORE_CASE = OSUtil.isWindowsSystem();

    public static final String PROPERTYNAME_FILE_NAME = "fileName";
    public static final String PROPERTYNAME_SIZE = "size";
    public static final String PROPERTYNAME_MODIFIED_BY = "modifiedBy";
    public static final String PROPERTYNAME_LAST_MODIFIED_DATE = "lastModifiedDate";
    public static final String PROPERTYNAME_VERSION = "version";
    public static final String PROPERTYNAME_DELETED = "deleted";
    public static final String PROPERTYNAME_FOLDER_INFO = "folderInfo";

    private static final Logger log = Logger
        .getLogger(FileInfo.class.getName());
    private static final long serialVersionUID = 100L;

    /**
     * The relativeName of the file relative to the base
     * <p>
     * Actually 'final'. Only non-final because of serialization readObject()
     * fileName.intern();
     */
    private String fileName;

    /** The size of the file */
    private final Long size;

    /** modified info */
    private final MemberInfo modifiedBy;
    /** modified in folder on date */
    private final Date lastModifiedDate;

    /** Version number of this file */
    private final int version;

    /** the deleted flag */
    private final boolean deleted;

    /**
     * the folder.
     * <p>
     * Actually 'final'. Only non-final because of serialization readObject()
     * folderInfo.intern();
     */
    private FolderInfo folderInfo;

    /**
     * The cached hash info.
     */
    private transient int hash;

    /**
     * Contains some cached string.
     */
    private transient Reference<FileInfoStrings> cachedStrings;

    protected FileInfo() {
        // ONLY for backward compatibility to MP3FileInfo

        fileName = null;
        size = null;
        modifiedBy = null;
        lastModifiedDate = null;
        version = 0;
        deleted = false;
        folderInfo = null;

        // VERY IMPORANT. MUST BE DONE IN EVERY CONSTRUCTOR
        this.hash = hashCode0();
    }

    protected FileInfo(String relativeName, long size, MemberInfo modifiedBy,
        Date lastModifiedDate, int version, boolean deleted,
        FolderInfo folderInfo)
    {
        Reject.ifNull(folderInfo, "folder is null!");
        Reject.ifNull(relativeName, "relativeName is null!");
        Reject.ifTrue(relativeName.contains("../"),
            "relativeName must not contain ../");

        this.fileName = relativeName;
        this.size = size;
        this.modifiedBy = modifiedBy;
        this.lastModifiedDate = lastModifiedDate;
        this.version = version;
        this.deleted = deleted;
        this.folderInfo = folderInfo;
        validate();

        // VERY IMPORANT. MUST BE DONE IN EVERY CONSTRUCTOR
        this.hash = hashCode0();
    }

    protected FileInfo(FolderInfo folder, String relativeName) {
        Reject.ifNull(folder, "folder is null!");
        Reject.ifNull(relativeName, "relativeName is null!");
        Reject.ifTrue(relativeName.contains("../"),
            "relativeName must not contain ../");

        this.fileName = relativeName;
        folderInfo = folder;

        size = null;
        modifiedBy = null;
        lastModifiedDate = null;
        version = 0;
        deleted = false;

        // VERY IMPORANT. MUST BE DONE IN EVERY CONSTRUCTOR
        this.hash = hashCode0();
    }

    /**
     * Syncs fileinfo with diskfile. If diskfile has other lastmodified date
     * that this. Assume that file has changed on disk and update its modified
     * info.
     * 
     * @param controller
     * @param diskFile
     *            the diskfile of this file, not gets it from controller !
     * @return the new FileInfo if the file was synced or null if the file is in
     *         sync
     */
    public FileInfo syncFromDiskIfRequired(Controller controller, File diskFile)
    {
        if (controller == null) {
            throw new NullPointerException("controller is null");
        }
        if (diskFile == null) {
            throw new NullPointerException("diskFile is null");
        }
        String diskFileName = diskFile.getName();
        boolean nameMatch = getRelativeName().endsWith(diskFileName);

        if (!nameMatch && IGNORE_CASE) {
            // Try harder if ignore case
            nameMatch = diskFileName.equalsIgnoreCase(getFilenameOnly());
        }

        // Check if files match
        if (!nameMatch) {
            throw new IllegalArgumentException(
                "Diskfile does not match fileinfo name '" + getFilenameOnly()
                    + "', details: " + this.toDetailString()
                    + ", diskfile name '" + diskFile.getName() + "', path: "
                    + diskFile);
        }

        // if (!diskFile.exists()) {
        // log.warning("File does not exsists on disk: " + toDetailString());
        // }

        if (!inSyncWithDisk(diskFile)) {
            if (diskFile.exists()) {
                return FileInfoFactory.modifiedFile(this, controller
                    .getFolderRepository(), diskFile, controller.getMySelf()
                    .getInfo());
            } else {
                return FileInfoFactory.deletedFile(this, controller.getMySelf()
                    .getInfo(), new Date());
            }
            // log.warning("File updated to: " + this.toDetailString());
        }

        return null;
    }

    /**
     * @param diskFile
     *            the file on disk.
     * @return true if the fileinfo is in sync with the file on disk.
     */
    public boolean inSyncWithDisk(File diskFile) {
        return inSyncWithDisk0(diskFile, false);
    }

    /**
     * @param diskFile
     *            the file on disk.
     * @param ignoreSizeAndModDate
     *            ignore the reported size of the diskfile/dir.
     * @return true if the fileinfo is in sync with the file on disk.
     */
    protected boolean inSyncWithDisk0(File diskFile,
        boolean ignoreSizeAndModDate)
    {
        Reject.ifNull(diskFile, "Diskfile is null");
        boolean diskFileDeleted = !diskFile.exists();

        boolean existanceSync = diskFileDeleted && deleted || !diskFileDeleted
            && !deleted;
        if (ignoreSizeAndModDate) {
            return existanceSync;
        }
        if (!existanceSync) {
            return false;
        }
        boolean lastModificationSync = DateUtil.equalsFileDateCrossPlattform(
            diskFile.lastModified(), lastModifiedDate.getTime());
        if (!lastModificationSync) {
            return false;
        }
        boolean sizeSync = size == diskFile.length();
        if (!sizeSync) {
            return false;
        }
        return true;
        // return existanceSync && lastModificationSync && sizeSync;
    }

    /**
     * @return the name , relative to the folder base.
     */
    public String getRelativeName() {
        return fileName;
    }

    /**
     * @return The filename (including the path from the base of the folder)
     *         converted to lowercase
     */
    public String getLowerCaseFilenameOnly() {
        if (Feature.CACHE_FILEINFO_STRINGS.isDisabled()) {
            return getFilenameOnly0().toLowerCase();
        }
        FileInfoStrings strings = getStringsCache();
        if (strings.getLowerCaseName() == null) {
            strings.setLowerCaseName(fileName.toLowerCase());
        }
        return strings.getLowerCaseName();
    }

    private FileInfoStrings getStringsCache() {
        FileInfoStrings stringsRef = cachedStrings != null ? cachedStrings
            .get() : null;
        if (stringsRef == null) {
            // Cache miss. create new entry
            stringsRef = new FileInfoStrings();
            cachedStrings = new WeakReference<FileInfoStrings>(stringsRef);
        }
        return stringsRef;
    }

    /**
     * @return everything after the last point (.) in the fileName in upper case
     */
    public String getExtension() {
        String tmpFileName = getFilenameOnly();
        int index = tmpFileName.lastIndexOf('.');
        if (index == -1) {
            return "";
        }
        return tmpFileName.substring(index + 1, tmpFileName.length())
            .toUpperCase();
    }

    /**
     * Gets the filename only, without the directory structure
     * 
     * @return the filename only of this file.
     */
    public String getFilenameOnly() {
        if (Feature.CACHE_FILEINFO_STRINGS.isDisabled()) {
            return getFilenameOnly0();
        }
        FileInfoStrings strings = getStringsCache();
        if (strings.getFileNameOnly() == null) {
            strings.setFileNameOnly(getFilenameOnly0());
        }
        return strings.getFileNameOnly();
    }

    private final String getFilenameOnly0() {
        int index = fileName.lastIndexOf('/');
        if (index > -1) {
            return fileName.substring(index + 1);
        } else {
            return fileName;
        }
    }

    /**
     * @return if this file was deleted.
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * @param repo
     * @return if this file is expeced
     */
    public boolean isExpected(FolderRepository repo) {
        if (deleted) {
            return false;
        }
        Folder folder = repo.getFolder(folderInfo);
        if (folder == null) {
            return false;
        }
        return !folder.isKnown(this);
    }

    /**
     * @param controller
     * @return if this file is currently downloading
     */
    public boolean isDownloading(Controller controller) {
        return controller.getTransferManager().isDownloadingActive(this);
    }

    /**
     * @param controller
     * @return if this file is currently uploading
     */
    public boolean isUploading(Controller controller) {
        return controller.getTransferManager().isUploading(this);
    }

    /**
     * @param controller
     * @return if the diskfile exists
     */
    public boolean diskFileExists(Controller controller) {
        File diskFile = getDiskFile(controller.getFolderRepository());
        return diskFile != null && diskFile.exists();
    }

    /**
     * @return the size of the file.
     */
    public long getSize() {
        return size;
    }

    /**
     * @return the modificator of this file.
     */
    public MemberInfo getModifiedBy() {
        return modifiedBy;
    }

    /**
     * @return the modification date.
     */
    public Date getModifiedDate() {
        return lastModifiedDate;
    }

    /**
     * @return the version of the file.
     */
    public int getVersion() {
        return version;
    }

    public boolean isLookupInstance() {
        return size == null;
    }

    public boolean isDiretory() {
        return false;
    }

    public boolean isFile() {
        return true;
    }

    /**
     * @param ofInfo
     *            the other fileinfo.
     * @return if this file is newer than the other one. By file version, or
     *         file modification date if version of both =0
     */
    public boolean isNewerThan(FileInfo ofInfo) {
        return isNewerThan(ofInfo, false);
    }

    protected boolean isNewerThan(FileInfo ofInfo, boolean ignoreLastModified) {
        if (ofInfo == null) {
            throw new NullPointerException("Other file is null");
        }
        if (Feature.DETECT_UPDATE_BY_VERSION.isDisabled()) {
            // Directly detected by last modified
            return DateUtil.isNewerFileDateCrossPlattform(lastModifiedDate,
                ofInfo.lastModifiedDate);
        }
        if (version == ofInfo.version) {
            if (ignoreLastModified) {
                return false;
            }
            return DateUtil.isNewerFileDateCrossPlattform(lastModifiedDate,
                ofInfo.lastModifiedDate);
        }
        return version > ofInfo.version;
    }

    /**
     * Also considers myself.
     * 
     * @param repo
     *            the folder repository
     * @return if there is a newer version available of this file
     */
    public boolean isNewerAvailable(FolderRepository repo) {
        FileInfo newestFileInfo = getNewestVersion(repo);
        return newestFileInfo != null && newestFileInfo.isNewerThan(this);
    }

    /**
     * Also considers myself
     * 
     * @param repo
     * @return the newest available version of this file
     */
    public FileInfo getNewestVersion(FolderRepository repo) {
        if (repo == null) {
            throw new NullPointerException("FolderRepo is null");
        }
        Folder folder = getFolder(repo);
        if (folder == null) {
            throw new IllegalStateException(
                "Unable to determine newest version. Folder not joined "
                    + folderInfo);
        }
        // TODO: Many temporary objects!!
        ArrayList<String> domains = new ArrayList<String>(folder
            .getMembersCount());
        for (Member member : folder.getMembersAsCollection()) {
            if (member.isCompletelyConnected()) {
                domains.add(member.getId());
            } else if (member.isMySelf()) {
                domains.add(null);
            }
        }
        return folder.getDAO().findNewestVersion(this,
            domains.toArray(new String[domains.size()]));
    }

    /**
     * @param repo
     * @return the newest available version of this file, excludes deleted
     *         remote files
     */
    public FileInfo getNewestNotDeletedVersion(FolderRepository repo) {
        if (repo == null) {
            throw new NullPointerException("FolderRepo is null");
        }
        Folder folder = getFolder(repo);
        if (folder == null) {
            log
                .warning("Unable to determine newest version. Folder not joined "
                    + folderInfo);
            return null;
        }
        FileInfo newestVersion = null;
        for (Member member : folder.getMembersAsCollection()) {
            if (member.isCompletelyConnected() || member.isMySelf()) {
                // Get remote file
                FileInfo remoteFile = member.getFile(this);
                if (remoteFile == null || remoteFile.deleted) {
                    continue;
                }
                // Check if remote file is newer
                if (newestVersion == null
                    || remoteFile.isNewerThan(newestVersion))
                {
                    // log.finer("Newer version found at " + member);
                    newestVersion = remoteFile;
                }
            }
        }
        return newestVersion;
    }

    /**
     * Resolves a file from local disk by folder repository, File MAY NOT Exist!
     * Returns null if folder was not found
     * 
     * @param repo
     * @return the file.
     */
    public File getDiskFile(FolderRepository repo) {
        Reject.ifNull(repo, "Repo is null");

        Folder folder = getFolder(repo);
        if (folder == null) {
            return null;
        }
        return folder.getDiskFile(this);
    }

    /**
     * Resolves a FileInfo from local folder db by folder repository, File MAY
     * NOT Exist! Returns null if folder was not found
     * 
     * @param repo
     * @return the FileInfo which is is in my own DB/knownfiles.
     */
    public FileInfo getLocalFileInfo(FolderRepository repo) {
        Reject.ifNull(repo, "Repo is null");
        Folder folder = getFolder(repo);
        if (folder == null) {
            return null;
        }
        return folder.getFile(this);
    }

    /**
     * @return the folderinfo this file belongs to.
     */
    public FolderInfo getFolderInfo() {
        return folderInfo;
    }

    /**
     * @param repo
     *            the folder repository.
     * @return the folder for this file.
     */
    public Folder getFolder(FolderRepository repo) {
        if (repo == null) {
            throw new NullPointerException("Repository is null");
        }
        return repo.getFolder(folderInfo);
    }

    /*
     * General
     */

    /**
     * @param otherFile
     * @return true if the file name, version and date is equal.
     */
    public boolean isVersionDateAndSizeIdentical(FileInfo otherFile) {
        if (otherFile == null) {
            return false;
        }
        if (version != otherFile.version) {
            // This is quick do it first
            return false;
        }
        if (!Util.equals(size, otherFile.size)) {
            return false;
        }
        if (!equals(otherFile)) {
            // not equals, return
            return false;
        }
        if (!lastModifiedDate.equals(otherFile.lastModifiedDate)) {
            return false;
        }
        // All match!
        return true;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    private int hashCode0() {
        int hash = IGNORE_CASE ? fileName.toLowerCase().hashCode() : fileName
            .hashCode();
        hash += folderInfo.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof FileInfo) {
            FileInfo otherInfo = (FileInfo) other;
            boolean caseMatch = IGNORE_CASE ? Util.equalsIgnoreCase(fileName,
                otherInfo.fileName) : Util.equals(fileName, otherInfo.fileName);
            return caseMatch && Util.equals(folderInfo, otherInfo.folderInfo);
        }

        return false;
    }

    @Override
    public String toString() {
        return '[' + folderInfo.name + "]:" + (deleted ? "(del) /" : "/")
            + fileName;
    }

    /**
     * appends to buffer
     * 
     * @param str
     *            the stringbuilder to add the detail info to.
     */
    private final void toDetailString(StringBuilder str) {
        str.append(toString());
        str.append(", size: ");
        str.append(size);
        str.append(" bytes, version: ");
        str.append(version);
        str.append(", modified: ");
        str.append(lastModifiedDate);
        str.append(" (");
        if (lastModifiedDate != null) {
            str.append(lastModifiedDate.getTime());
        } else {
            str.append("-n/a-");
        }
        str.append(") by '");
        if (modifiedBy == null) {
            str.append("-n/a-");
        } else {
            str.append(modifiedBy.nick);
        }
        str.append('\'');
    }

    public String toDetailString() {
        StringBuilder str = new StringBuilder();
        toDetailString(str);
        return str.toString();
    }

    /**
     * @return true if this instance is valid. false if is broken,e.g. Negative
     *         Time
     */
    public boolean isValid() {
        try {
            validate();
            return true;
        } catch (Exception e) {
            log
                .log(Level.WARNING, "Invalid: " + toDetailString() + ". " + e,
                    e);
            return false;
        }
    }

    /**
     * Validates the state of the FileInfo. This should actually not be public -
     * checks should be made while constructing this class (by
     * constructor/deserialization).
     * 
     * @throws IllegalArgumentException
     *             if the state is corrupt
     */
    private void validate() {
        Reject.ifNull(lastModifiedDate, "Modification date is null");
        if (lastModifiedDate.getTime() < 0) {
            throw new IllegalStateException("Modification date is invalid: "
                + lastModifiedDate);
        }
        Reject.ifTrue(StringUtils.isEmpty(fileName), "Filename is empty");
        Reject.ifNull(size, "Size is null");
        Reject.ifFalse(size >= 0, "Negative file size");
        Reject.ifNull(folderInfo, "FolderInfo is null");
    }

    // Serialization optimization *********************************************

    private void readObject(ObjectInputStream in) throws IOException,
        ClassNotFoundException
    {
        in.defaultReadObject();
        // Internalized strings are not guaranteed to be garbage collected!
        fileName = fileName.intern();

        // Oh! Default value. Better recalculate hashcode cache
        if (hash == 0) {
            hash = hashCode0();
        }

        folderInfo = folderInfo.intern();

        // TODO MemberInfo.intern

        // validate();
    }
}