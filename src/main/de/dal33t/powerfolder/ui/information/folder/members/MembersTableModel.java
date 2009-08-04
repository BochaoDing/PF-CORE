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
 * $Id: MembersTableModel.java 5457 2008-10-17 14:25:41Z harry $
 */
package de.dal33t.powerfolder.ui.information.folder.members;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.PFUIComponent;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.FolderRepository;
import de.dal33t.powerfolder.disk.FolderStatistic;
import de.dal33t.powerfolder.event.NodeManagerEvent;
import de.dal33t.powerfolder.event.NodeManagerListener;
import de.dal33t.powerfolder.light.AccountInfo;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.net.NodeManager;
import de.dal33t.powerfolder.security.FolderAdminPermission;
import de.dal33t.powerfolder.security.FolderPermission;
import de.dal33t.powerfolder.security.SecurityManagerEvent;
import de.dal33t.powerfolder.security.SecurityManagerListener;
import de.dal33t.powerfolder.ui.model.SortedTableModel;
import de.dal33t.powerfolder.util.Format;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.compare.ReverseComparator;
import de.dal33t.powerfolder.util.ui.SyncProfileUtil;

/**
 * Class to model a folder's members. provides columns for image, name, sync
 * status, folder size, local size.
 */
public class MembersTableModel extends PFUIComponent implements TableModel,
    SortedTableModel
{

    private static final int COL_TYPE = 0;
    private static final int COL_NICK = 1;
    private static final int COL_SYNC_STATUS = 2;
    private static final int COL_LOCAL_SIZE = 3;
    private static final int COL_USERNAME = 4;
    private static final int COL_PERMISSION = 5;

    private final List<FolderMember> members;
    private final List<TableModelListener> listeners;
    private Folder folder;
    private final FolderRepository folderRepository;

    private int sortColumn = -1;
    private boolean sortAscending = true;

    private String[] columnHeaders = {
        Translation.getTranslation("folder_member_table_model.icon"), // 0
        Translation.getTranslation("folder_member_table_model.name"), // 1
        Translation.getTranslation("folder_member_table_model.sync_status"), // 2
        Translation.getTranslation("folder_member_table_model.local_size"), // 3
        Translation.getTranslation("folder_member_table_model.account"), // 4
        Translation.getTranslation("folder_member_table_model.permission")}; // 5

    /**
     * Constructor
     * 
     * @param controller
     */
    public MembersTableModel(Controller controller) {
        super(controller);

        folderRepository = controller.getFolderRepository();
        members = new ArrayList<FolderMember>();
        listeners = new ArrayList<TableModelListener>();

        // Node changes
        NodeManager nodeManager = controller.getNodeManager();
        nodeManager.addNodeManagerListener(new MyNodeManagerListener());
        getController().getSecurityManager().addListener(
            new MySecurityManagerListener());
    }

    /**
     * Sets model for a new folder.
     * 
     * @param folderInfo
     */
    public void setFolderInfo(FolderInfo folderInfo) {
        folder = folderRepository.getFolder(folderInfo);
        members.clear();
        for (Member member : folder.getMembersAsCollection()) {
            members.add(new FolderMember(folder, member,
                new FolderAdminPermission(folder.getInfo())));
        }
        modelChanged(new TableModelEvent(this, 0, members.size() - 1));
    }

    /**
     * Adds a listener to the list.
     * 
     * @param l
     */
    public void addTableModelListener(TableModelListener l) {
        listeners.add(l);
    }

    /**
     * Removes a listener from the list.
     * 
     * @param l
     */
    public void removeTableModelListener(TableModelListener l) {
        listeners.remove(l);
    }

    /**
     * @param columnIndex
     * @return the column class.
     */
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0 :
                return Member.class;
            default :
                return String.class;

        }
    }

    /**
     * @return count of the displayable columns.
     */
    public int getColumnCount() {
        return columnHeaders.length;
    }

    /**
     * @param columnIndex
     * @return the column header name.
     */
    public String getColumnName(int columnIndex) {
        return columnHeaders[columnIndex];
    }

    /**
     * @return count of the rows.
     */
    public int getRowCount() {
        return members.size();
    }

    public Member getMemberAt(int rowIndex) {
        return (Member) getValueAt(rowIndex, 0);
    }

    /**
     * Gets a value at a specific row / column.
     * 
     * @param rowIndex
     * @param columnIndex
     * @return
     */
    public Object getValueAt(int rowIndex, int columnIndex) {
        Member member = members.get(rowIndex).getMember();
        FolderStatistic stats = folder.getStatistic();

        if (columnIndex == COL_TYPE) {
            return member;
        } else if (columnIndex == COL_NICK) {
            return member.getNick();
        } else if (columnIndex == COL_USERNAME) {
            AccountInfo aInfo = member.getAccountInfo();
            if (aInfo == null) {
                return "";
            } else {
                return aInfo.getScrabledUsername();
            }
        } else if (columnIndex == COL_PERMISSION) {
            FolderPermission permission = members.get(rowIndex).getPermission();
            if (permission == null) {
                return "";
            } else {
                return permission.getName();
            }
        } else if (columnIndex == COL_SYNC_STATUS) {
            double sync = stats.getSyncPercentage(member);
            return SyncProfileUtil.renderSyncPercentage(sync);
        } else if (columnIndex == COL_LOCAL_SIZE) {
            int filesRcvd = stats.getFilesCountInSync(member);
            long bytesRcvd = stats.getSizeInSync(member);
            return filesRcvd + " "
                + Translation.getTranslation("general.files") + " ("
                + Format.formatBytes(bytesRcvd) + ')';
        } else {
            return 0;
        }
    }

    /**
     * Answers if cell is editable - no, it is not!
     * 
     * @param rowIndex
     * @param columnIndex
     * @return
     */
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    /**
     * Not implemented - cannot set values in this model.
     * 
     * @param aValue
     * @param rowIndex
     * @param columnIndex
     */
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        throw new IllegalStateException(
            "Unable to set value in MembersTableModel; not editable");
    }

    /**
     * Handle node add event.
     * 
     * @param e
     */
    private void handleNodeAdded(NodeManagerEvent e) {
        try {
            Member member = e.getNode();
            check(member);
            Collection<Member> folderMembers = folder.getMembersAsCollection();
            if (folderMembers.contains(member) && !members.contains(member)) {
                members.add(new FolderMember(folder, member,
                    new FolderAdminPermission(folder.getInfo())));
            }
            modelChanged(new TableModelEvent(this, 0, members.size() - 1));
        } catch (IllegalStateException ex) {
            logSevere("IllegalStateException", ex);
        }
    }

    /**
     * Handle node add event.
     * 
     * @param e
     */
    private void handleNodeRemoved(NodeManagerEvent e) {
        try {
            Member member = e.getNode();
            check(member);
            Collection<Member> folderMembers = folder.getMembersAsCollection();
            if (folderMembers.contains(member)) {
                members.remove(member);
            }
            modelChanged(new TableModelEvent(this, 0, members.size() - 1));
        } catch (IllegalStateException ex) {
            logSevere("IllegalStateException", ex);
        }
    }

    /**
     * Handle node add event.
     * 
     * @param e
     */
    private void handleNodeChanged(NodeManagerEvent e) {
        handleNodeChanged(e.getNode());
    }

    /**
     * Handle node add event.
     * 
     * @param e
     */
    private void handleNodeChanged(Member eventMember) {
        try {
            check(eventMember);
            for (int i = 0; i < members.size(); i++) {
                FolderMember localMember = members.get(i);
                if (eventMember.equals(localMember)) {
                    // Found the member.
                    modelChanged(new TableModelEvent(this, i, i));
                    return;
                }
            }
        } catch (IllegalStateException ex) {
            logSevere("IllegalStateException", ex);
        }
    }

    /**
     * Checks that the folder and member are valid.
     * 
     * @param e
     * @throws IllegalStateException
     */
    private void check(Member member) throws IllegalStateException {
        if (folder == null) {
            throw new IllegalStateException("Folder not set");
        }
        if (member == null) {
            throw new IllegalStateException("Member not set in event");
        }
    }

    /**
     * Fires a model event to all listeners, that model has changed
     */
    private void modelChanged(final TableModelEvent e) {
        for (TableModelListener listener : listeners) {
            listener.tableChanged(e);
        }
    }

    /**
     * @return the sorting column.
     */
    public int getSortColumn() {
        return sortColumn;
    }

    /**
     * @return if sorting ascending.
     */
    public boolean isSortAscending() {
        return sortAscending;
    }

    /**
     * Sorts by this column.
     * 
     * @param columnIndex
     * @return
     */
    public boolean sortBy(int columnIndex) {
        boolean newSortColumn = sortColumn != columnIndex;
        sortColumn = columnIndex;
        switch (columnIndex) {
            case COL_TYPE :
                sortMe(FolderMemberComparator.BY_TYPE, newSortColumn);
                break;
            case COL_NICK :
                sortMe(FolderMemberComparator.BY_NICK, newSortColumn);
                break;
            case COL_USERNAME :
                sortMe(FolderMemberComparator.BY_USERNAME, newSortColumn);
                break;
            case COL_SYNC_STATUS :
                sortMe(FolderMemberComparator.BY_SYNC_STATUS, newSortColumn);
                break;
            case COL_LOCAL_SIZE :
                sortMe(FolderMemberComparator.BY_LOCAL_SIZE, newSortColumn);
                break;
            default :
                logWarning("Unknown sort column: " + columnIndex);
        }
        return true;
    }

    private void sortMe(FolderMemberComparator comparator, boolean newSortColumn)
    {

        if (!newSortColumn) {
            // Reverse list.
            sortAscending = !sortAscending;
        }

        if (sortAscending) {
            Collections.sort(members, comparator);
        } else {
            Collections.sort(members, new ReverseComparator<FolderMember>(
                comparator));
        }

        modelChanged(new TableModelEvent(this, 0, members.size() - 1));
    }

    /**
     * Listener for node events
     */
    private class MyNodeManagerListener implements NodeManagerListener {

        public boolean fireInEventDispatchThread() {
            return true;
        }

        public void friendAdded(NodeManagerEvent e) {
            handleNodeAdded(e);
        }

        public void friendRemoved(NodeManagerEvent e) {
            handleNodeRemoved(e);
        }

        public void nodeAdded(NodeManagerEvent e) {
            handleNodeAdded(e);
        }

        public void nodeConnected(NodeManagerEvent e) {
            handleNodeChanged(e);
        }

        public void nodeDisconnected(NodeManagerEvent e) {
            handleNodeChanged(e);
        }

        public void nodeRemoved(NodeManagerEvent e) {
            handleNodeRemoved(e);
        }

        public void settingsChanged(NodeManagerEvent e) {
            handleNodeChanged(e);
        }

        public void startStop(NodeManagerEvent e) {
            // Don't care.
        }
    }

    private class MySecurityManagerListener implements SecurityManagerListener {

        public void nodeAccountStateChanged(SecurityManagerEvent event) {
            handleNodeChanged(event.getNode());
        }

        public boolean fireInEventDispatchThread() {
            return true;
        }

    }
}
