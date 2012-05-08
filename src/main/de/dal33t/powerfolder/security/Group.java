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
 * $Id: Account.java 18110 2012-02-13 02:41:13Z tot $
 */
package de.dal33t.powerfolder.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.CollectionOfElements;
import org.hibernate.annotations.Index;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;
import org.hibernate.annotations.Type;

import de.dal33t.powerfolder.util.IdGenerator;
import de.dal33t.powerfolder.util.Reject;

/**
 * A group of accounts.
 *
 * @author <a href="mailto:krickl@powerfolder.com">Maximilian Krickl</a>
 * @version $Revision: 1.5 $
 */
@Entity(name = "AGroup")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Group implements Serializable {

    public static final String PROPERTYNAME_OID = "oid";
    public static final String PROPERTYNAME_GROUPNAME = "name";
    public static final String PROPERTYNAME_PERMISSIONS = "permissions";

    private static final long serialVersionUID = 100L;

    private static final Logger LOG = Logger.getLogger(Group.class.getName());

    @Id
    private String oid;
    @Index(name = "IDX_GROUP_NAME")
    @Column(nullable = false)
    private String name;

    @CollectionOfElements
    @Type(type = "permissionType")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @LazyCollection(LazyCollectionOption.FALSE)
    private Collection<Permission> permissions;

    protected Group() {
    }

    public Group(String name) {
        this(IdGenerator.makeId(), name);
    }

    public Group(String oid, String name) {
        Reject.ifBlank(oid, "OID");
        Reject.ifBlank(name, "Name");
        this.oid = oid;
        this.name = name;
        this.permissions = new CopyOnWriteArrayList<Permission>();
    }

    public void grant(Permission... newPermissions) {
        Reject.ifNull(newPermissions, "Permission is null");
        for (Permission p : newPermissions) {
            if (hasPermission(p)) {
                // Skip
                continue;
            }
            else {
                permissions.add(p);
            }
        }
    }

    public void revoke(Permission... revokePermission) {
        Reject.ifNull(revokePermission, "Permission is null");
        for (Permission p : revokePermission) {
            if (permissions.remove(p)) {
                LOG.fine("Revoked permission from " + this + ": " + p);
            }
        }
    }

    public boolean hasPermission(Permission permission) {
        Reject.ifNull(permission, "Permission is null");
        if (permissions == null) {
            LOG.severe("Illegal group " + name + ", permissions is null");
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

    public Collection<Permission> getPermission() {
        return Collections.unmodifiableCollection(permissions);
    }

    public String getOID() {
        return oid;
    }

    public String getName() {
        return name;
    }

    public void setName(String newName) {
        name = newName;
    }
}
