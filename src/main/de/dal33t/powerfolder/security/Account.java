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
package de.dal33t.powerfolder.security;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jgoodies.binding.beans.Model;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.light.AccountInfo;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.light.MemberInfo;
import de.dal33t.powerfolder.light.ServerInfo;
import de.dal33t.powerfolder.message.clientserver.AccountDetails;
import de.dal33t.powerfolder.os.OnlineStorageSubscriptionType;
import de.dal33t.powerfolder.util.Format;
import de.dal33t.powerfolder.util.IdGenerator;
import de.dal33t.powerfolder.util.Reject;

/**
 * A access to the system indentified by username & password.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc</a>
 * @version $Revision: 1.5 $
 */
public class Account extends Model implements Serializable {

    private static final Logger LOG = Logger.getLogger(Account.class.getName());
    private static final long serialVersionUID = 100L;

    // Properties
    public static final String PROPERTYNAME_OID = "oid";
    public static final String PROPERTYNAME_USERNAME = "username";
    public static final String PROPERTYNAME_PASSWORD = "password";
    public static final String PROPERTYNAME_PERMISSIONS = "permissions";
    public static final String PROPERTYNAME_REGISTER_DATE = "registerDate";
    public static final String PROPERTYNAME_LAST_LOGIN_DATE = "lastLoginDate";
    public static final String PROPERTYNAME_LAST_LOGIN_FROM = "lastLoginFrom";
    public static final String PROPERTYNAME_NEWSLETTER = "newsLetter";
    public static final String PROPERTYNAME_PRO_USER = "proUser";
    public static final String PROPERTYNAME_SERVER = "server";
    public static final String PROPERTYNAME_DEFAULT_SYNCHRONIZED_FOLDER = "defaultSynchronizedFolder";
    public static final String PROPERTYNAME_OS_SUBSCRIPTION = "OSSubscription";
    public static final String PROPERTYNAME_LICENSE_KEY_FILES = "licenseKeyFiles";

    private String oid;
    private String username;
    private String password;
    private Date registerDate;
    private Date lastLoginDate;
    private MemberInfo lastLoginFrom;
    private boolean newsLetter;
    private boolean proUser;

    /**
     * The list of computers associated with this account.
     */
    private Collection<MemberInfo> computers;

    /**
     * Server where the folders of this account are hosted on.
     */
    private ServerInfo server;

    /**
     * The possible license key files of this account.
     * <code>AccountService.getValidLicenseKey</code>.
     */
    private Collection<String> licenseKeyFiles;

    /**
     * The default-synced folder of the user. May be null.
     * <p>
     * TRAC #991.
     */
    private FolderInfo defaultSynchronizedFolder;

    private Collection<Permission> permissions;
    private OnlineStorageSubscription osSubscription;

    public Account() {
        // Generate unique id
        this.oid = IdGenerator.makeId();
        this.permissions = new CopyOnWriteArrayList<Permission>();
        this.osSubscription = new OnlineStorageSubscription();
        this.osSubscription.setType(OnlineStorageSubscriptionType.NONE);
        this.licenseKeyFiles = new CopyOnWriteArrayList<String>();
        this.computers = new CopyOnWriteArrayList<MemberInfo>();
    }

    /**
     * @return a leightweight/reference object to this account.
     */
    public AccountInfo createInfo() {
        return new AccountInfo(oid, username);
    }

    // Basic permission stuff *************************************************

    public void grant(Permission... newPermissions) {
        Reject.ifNull(newPermissions, "Permission is null");
        for (Permission p : newPermissions) {
            if (hasPermission(p)) {
                // Skip
                continue;
            }
            if (p instanceof FolderPermission) {
                revokeAllFolderPermission(((FolderPermission) p).getFolder());
            }
            permissions.add(p);
        }
        LOG.fine("Granted permission to " + this + ": "
            + Arrays.asList(newPermissions));
        firePropertyChange(PROPERTYNAME_PERMISSIONS, null, null);
    }

    public void revoke(Permission... revokePermissions) {
        Reject.ifNull(revokePermissions, "Permission is null");
        for (Permission p : revokePermissions) {
            if (permissions.remove(p)) {
                LOG.fine("Revoked permission from " + this + ": " + p);
            }
        }
        firePropertyChange(PROPERTYNAME_PERMISSIONS, null, null);
    }

    public void revokeAllFolderPermission(FolderInfo foInfo) {
        revoke(new FolderReadPermission(foInfo), new FolderReadWritePermission(
            foInfo), new FolderAdminPermission(foInfo),
            new FolderOwnerPermission(foInfo));
    }

    public void revokeAllPermissions() {
        LOG.fine("Revoking all permission from " + this + ": " + permissions);
        permissions.clear();
        firePropertyChange(PROPERTYNAME_PERMISSIONS, null, null);
    }

    public boolean hasPermission(Permission permission) {
        Reject.ifNull(permission, "Permission is null");
        if (permissions == null) {
            LOG.severe("Illegal account " + username + ", permissions is null");
            return false;
        }
        for (Permission p : permissions) {
            if (p == null) {
                LOG.severe("Got null permission on " + this);
                continue;
            }
            if (p.equals(permission)) {
                return true;
            }
            if (p.implies(permission)) {
                return true;
            }
        }
        return false;
    }

    public Collection<Permission> getPermissions() {
        return Collections.unmodifiableCollection(permissions);
    }

    /**
     * @return all folders the account has directly at folder read permission
     *         granted.
     */
    public Collection<FolderInfo> getFolders() {
        List<FolderInfo> folderInfos = new ArrayList<FolderInfo>(permissions
            .size());
        for (Permission permission : permissions) {
            if (permission instanceof FolderPermission) {
                FolderPermission fp = (FolderPermission) permission;
                folderInfos.add(fp.getFolder());
            }
        }
        return folderInfos;
    }

    // Accessing / API ********************************************************

    /**
     * @return true if this is a valid account
     */
    public boolean isValid() {
        return username != null;
    }

    public String getOID() {
        return oid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        Object oldValue = getUsername();
        this.username = username;
        firePropertyChange(PROPERTYNAME_USERNAME, oldValue, this.username);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        Object oldValue = getPassword();
        this.password = password;
        firePropertyChange(PROPERTYNAME_PASSWORD, oldValue, this.password);
    }

    public Date getRegisterDate() {
        return registerDate;
    }

    public void setRegisterDate(Date registerDate) {
        Object oldValue = getRegisterDate();
        this.registerDate = registerDate;
        firePropertyChange(PROPERTYNAME_REGISTER_DATE, oldValue,
            this.registerDate);
    }

    public boolean isNewsLetter() {
        return newsLetter;
    }

    public void setNewsLetter(boolean newsLetter) {
        Object oldValue = isNewsLetter();
        this.newsLetter = newsLetter;
        firePropertyChange(PROPERTYNAME_NEWSLETTER, oldValue, this.newsLetter);
    }

    public OnlineStorageSubscription getOSSubscription() {
        if (osSubscription == null) {
            // osSubscription = new OnlineStorageSubscription();
            // osSubscription.setType(OnlineStorageSubscriptionType.TRIAL_5GB);
        }
        return osSubscription;
    }

    public void setOSSubscription(OnlineStorageSubscription osSubscription) {
        Object oldValue = getOSSubscription();
        this.osSubscription = osSubscription;
        firePropertyChange(PROPERTYNAME_OS_SUBSCRIPTION, oldValue,
            this.osSubscription);
    }

    public boolean isProUser() {
        return proUser;
    }

    public void setProUser(boolean proUser) {
        Object oldValue = isProUser();
        this.proUser = proUser;
        firePropertyChange(PROPERTYNAME_PRO_USER, oldValue, this.proUser);
    }

    public ServerInfo getServer() {
        return server;
    }

    public void setServer(ServerInfo server) {
        Object oldValue = getServer();
        this.server = server;
        firePropertyChange(PROPERTYNAME_SERVER, oldValue, this.server);
    }

    public FolderInfo getDefaultSynchronizedFolder() {
        return defaultSynchronizedFolder;
    }

    public void setDefaultSynchronizedFolder(
        FolderInfo defaultSynchronizedFolder)
    {
        Object oldValue = getDefaultSynchronizedFolder();
        this.defaultSynchronizedFolder = defaultSynchronizedFolder;
        firePropertyChange(PROPERTYNAME_DEFAULT_SYNCHRONIZED_FOLDER, oldValue,
            this.defaultSynchronizedFolder);
    }

    public MemberInfo getLastLoginFrom() {
        return lastLoginFrom;
    }

    public void setLastLoginFrom(MemberInfo lastLoginFrom) {
        Object oldValue = getLastLoginFrom();
        this.lastLoginFrom = lastLoginFrom;
        firePropertyChange(PROPERTYNAME_LAST_LOGIN_FROM, oldValue,
            this.lastLoginFrom);

        // Set login date
        touchLogin();

        // Ensure initialization
        getComputers();
        if (lastLoginFrom != null && !computers.contains(lastLoginFrom)) {
            computers.add(lastLoginFrom);
        }
    }

    public Date getLastLoginDate() {
        return lastLoginDate;
    }

    /**
     * Sets the last login date to NOW.
     */
    public void touchLogin() {
        lastLoginDate = new Date();
    }

    /**
     * @return the computers this account is associated with.
     */
    public Collection<MemberInfo> getComputers() {
        if (computers == null) {
            computers = new CopyOnWriteArrayList<MemberInfo>();
        }
        return computers;
    }

    public Collection<String> getLicenseKeyFiles() {
        if (licenseKeyFiles == null) {
            // Migrate
            licenseKeyFiles = new CopyOnWriteArrayList<String>();
        }
        return licenseKeyFiles;
    }

    /**
     * @return the days since the user has registered
     */
    public int getDaysSinceRegistration() {
        if (registerDate == null) {
            return -1;
        }
        long daysSinceRegistration = (System.currentTimeMillis() - registerDate
            .getTime())
            / (1000L * 60 * 60 * 24);
        return (int) daysSinceRegistration;
    }

    public String toString() {
        return "Account '" + username + "', " + permissions.size()
            + " permissions";
    }

    public String toDetailString() {
        return toString() + ", pro? " + proUser + ", regdate: "
            + Format.formatDateShort(registerDate) + ", licenses: "
            + (licenseKeyFiles != null ? licenseKeyFiles.size() : "n/a") + ", "
            + osSubscription;
    }

    // Convenience/Applogic ***************************************************

    public AccountDetails calculateDetails(Controller controller) {
        Reject.ifNull(controller, "Controller");
        long used = calculateTotalUsage(controller);
        int nFolders = countNumberOfFolders(controller);
        long archiveSize = calculateArchiveSize(controller);
        return new AccountDetails(this, used, nFolders, archiveSize);
    }

    /**
     * @param controller
     * @return the total used online storage size
     */
    public long calculateTotalUsage(Controller controller) {
        return calculateTotalFoldersSize(controller)
            + calculateArchiveSize(controller);
    }

    /**
     * @param controller
     * @return the total size used by this user
     */
    public long calculateTotalFoldersSize(Controller controller) {
        long totalSize = 0;
        for (Permission p : permissions) {
            if (p instanceof FolderOwnerPermission
                || p instanceof FolderAdminPermission)
            {
                FolderPermission fp = (FolderPermission) p;
                Folder f = fp.getFolder().getFolder(controller);
                if (f == null) {
                    continue;
                }
                totalSize += f.getStatistic().getLocalSize();
            }
        }
        return totalSize;
    }

    /**
     * @param controller
     * @return the total size of recycle bin
     */
    public long calculateArchiveSize(Controller controller) {
        long start = System.currentTimeMillis();
        long size = 0;
        for (Permission p : permissions) {
            if (p instanceof FolderOwnerPermission
                || p instanceof FolderAdminPermission)
            {
                FolderPermission fp = (FolderPermission) p;
                Folder f = fp.getFolder().getFolder(controller);
                if (f == null) {
                    continue;
                }
                size += f.getFileArchiver().getSize();
            }
        }
        long took = System.currentTimeMillis() - start;
        if (took > 1000L * 20) {
            LOG.severe("Calculating archive size for " + this + " took " + took
                + "ms");
        }
        return size;
    }

    /**
     * @param controller
     * @return the mirrored # of folders by this user
     */
    public int countNumberOfFolders(Controller controller) {
        int nFolders = 0;
        for (Permission p : permissions) {
            if (p instanceof FolderAdminPermission
                || p instanceof FolderOwnerPermission)
            {
                FolderPermission fp = (FolderPermission) p;
                Folder f = fp.getFolder().getFolder(controller);
                if (f == null) {
                    continue;
                }
                nFolders++;
            }
        }
        return nFolders;
    }

    /**
     * Enables the selected account:
     * <p>
     * The Online Storage subscription
     * <P>
     * Sets all folders to SyncProfile.BACKUP_TARGET.
     * <p>
     * FIXME: Does only set the folders hosted on the CURRENT server to backup.
     * <p>
     * Account needs to be stored afterwards!!
     * 
     * @param controller
     *            the controller
     */
    public void enable(Controller controller) {
        Reject.ifNull(controller, "Controller is null");

        getOSSubscription().setWarnedUsageDate(null);
        getOSSubscription().setDisabledUsageDate(null);
        getOSSubscription().setWarnedExpirationDate(null);
        getOSSubscription().setDisabledExpirationDate(null);

        enableSync(controller);
    }

    /**
     * Sets all folders that have SyncProfile.DISABLED to
     * SyncProfile.BACKUP_TARGET_NO_CHANGE_DETECT.
     * 
     * @param controller
     * @return the number of folder the sync was re-enabled.
     */
    public int enableSync(Controller controller) {
        Reject.ifNull(controller, "Controller is null");
        int n = 0;
        for (Permission p : getPermissions()) {
            if (p instanceof FolderAdminPermission
                || p instanceof FolderOwnerPermission)
            {
                FolderPermission fp = (FolderPermission) p;
                Folder f = fp.getFolder().getFolder(controller);
                if (f == null) {
                    continue;
                }
                if (f.getSyncProfile().equals(SyncProfile.DISABLED)) {
                    n++;
                    f
                        .setSyncProfile(SyncProfile.BACKUP_TARGET_NO_CHANGE_DETECT);
                }
            }
        }
        return n;
    }

    /**
     * Sets all folders that don't have SyncProfile.DISABLED to
     * SyncProfile.DISABLED.
     * 
     * @param controller
     * @return the number of folder the sync was disabled.
     */
    public int disableSync(Controller controller) {
        Reject.ifNull(controller, "Controller is null");
        int nNewDisabled = 0;
        for (Permission p : getPermissions()) {
            if (p instanceof FolderAdminPermission
                || p instanceof FolderOwnerPermission)
            {
                FolderPermission fp = (FolderPermission) p;
                Folder folder = fp.getFolder().getFolder(controller);
                if (folder == null) {
                    continue;
                }
                if (LOG.isLoggable(Level.FINER)) {
                    LOG.finer("Disable download of new files for folder: "
                        + folder + " for " + getUsername());
                }
                if (!folder.getSyncProfile().equals(SyncProfile.DISABLED)) {
                    folder.setSyncProfile(SyncProfile.DISABLED);
                    nNewDisabled++;
                }
            }
        }
        if (nNewDisabled > 0) {
            LOG.info("Disabled " + nNewDisabled + " folder for "
                + getUsername());
        }
        return nNewDisabled;
    }

    // Permission convenience ************************************************

    /**
     * Answers if the user is allowed to read the folder contents.
     * 
     * @param foInfo
     *            the folder to check
     * @return true if the user is allowed to read the folder contents
     */
    public boolean hasReadPermissions(FolderInfo foInfo) {
        Reject.ifNull(foInfo, "Folder info is null");
        return hasPermission(new FolderReadPermission(foInfo));
    }

    /**
     * Answers if the user is allowed to write into the folder.
     * 
     * @param foInfo
     *            the folder to check
     * @return true if the user is allowed to write into the folder.
     */
    public boolean hasReadWritePermissions(FolderInfo foInfo) {
        Reject.ifNull(foInfo, "Folder info is null");
        return hasPermission(new FolderReadWritePermission(foInfo));
    }

    /**
     * Answers if the user is allowed to write into the folder.
     * 
     * @param foInfo
     *            the folder to check
     * @return true if the user is allowed to write into the folder.
     */
    public boolean hasWritePermissions(FolderInfo foInfo) {
        return hasReadWritePermissions(foInfo);
    }

    /**
     * @param foInfo
     * @return true if the user is admin of the folder.
     */
    public boolean hasAdminPermission(FolderInfo foInfo) {
        Reject.ifNull(foInfo, "Folder info is null");
        return hasPermission(new FolderAdminPermission(foInfo));
    }

    /**
     * @param foInfo
     * @return true if the user is owner of the folder.
     */
    public boolean hasOwnerPermission(FolderInfo foInfo) {
        Reject.ifNull(foInfo, "Folder info is null");
        return hasPermission(new FolderOwnerPermission(foInfo));
    }

    private void readObject(java.io.ObjectInputStream stream)
        throws IOException, ClassNotFoundException
    {
        stream.defaultReadObject();
        if (oid == null) {
            // Migration.
            oid = IdGenerator.makeId();
        }
    }
}
