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
package de.dal33t.powerfolder.transfer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.commons.lang.Validate;

import de.dal33t.powerfolder.Constants;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.message.AbortDownload;
import de.dal33t.powerfolder.message.FileChunk;
import de.dal33t.powerfolder.message.RequestDownload;
import de.dal33t.powerfolder.message.RequestFilePartsRecord;
import de.dal33t.powerfolder.message.RequestPart;
import de.dal33t.powerfolder.message.StopUpload;
import de.dal33t.powerfolder.util.Range;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.Util;
import de.dal33t.powerfolder.util.Loggable;
import de.dal33t.powerfolder.util.delta.FilePartsRecord;

/**
 * Download class, containing file and member.<BR>
 * Serializable for remembering completed Downloads in DownLoadTableModel.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.30 $
 */
public class Download extends Transfer {

    private static final long serialVersionUID = 100L;
    public final static int MAX_REQUESTS_QUEUED = 15;

    private Date lastTouch;
    private boolean automatic;
    private boolean queued;

    private Queue<RequestPart> pendingRequests = new LinkedList<RequestPart>();

    private transient DownloadManager manager;

    /** for serialisation */
    public Download() {
    }

    /**
     * Constuctor for download, package protected, can only be created by
     * transfer manager
     * <p>
     * Downloads start in pending state. Move to requested by calling
     * <code>request(Member)</code>
     */
    Download(TransferManager tm, FileInfo file, boolean automatic) {
        super(tm, file, null);
        // from can be null
        this.lastTouch = new Date();
        this.automatic = automatic;
        this.queued = false;
    }

    /**
     * Re-initalized the Transfer with the TransferManager. Use this only if you
     * are know what you are doing .
     * 
     * @param aTransferManager
     *            the transfermanager
     */
    public void init(TransferManager aTransferManager) {
        super.init(aTransferManager);
        queued = false;
    }

    /**
     * @return if this download was automatically requested
     */
    public boolean isRequestedAutomatic() {
        return automatic;
    }

    /**
     * @return the managing MultiSourceDownload for this download.
     */
    public DownloadManager getDownloadManager() {
        return manager;
    }

    public void setDownloadManager(DownloadManager manager) {
        this.manager = manager;
    }

    /**
     * Called when the partner supports part-transfers and is ready to upload
     * 
     * @param usedFileInfo
     */
    public void uploadStarted() {
        lastTouch.setTime(System.currentTimeMillis());
        if (isStarted()) {
            Loggable.logWarningStatic(Download.class,
                    "Received multiple upload start messages!");
            return;
        }
        
        Loggable.logFinerStatic(Download.class,
                "Uploader supports partial transfers.");
        setStarted();
        manager.readyForRequests(this);
    }

    /**
     * Requests a FPR from the remote side.
     */
    public void requestFilePartsRecord() {
        assert Util.useDeltaSync(getController(), getPartner()) : "Requesting FilePartsRecord from a client that doesn't support that!";

        getPartner().sendMessagesAsynchron(
            new RequestFilePartsRecord(getFile()));
    }

    public void receivedFilePartsRecord(FilePartsRecord record) {
        Reject.ifNull(record, "Record is null");

        lastTouch.setTime(System.currentTimeMillis());
        Loggable.logInfoStatic(Download.class,
                "Received parts record");
        manager.receivedFilePartsRecord(this, record);
    }

    /**
     * Requests a single part from the remote peer.
     * 
     * @param range
     * @return
     * @throws BrokenDownloadException
     */
    public boolean requestPart(Range range) throws BrokenDownloadException {
        Validate.notNull(range);
        RequestPart rp;
        synchronized (pendingRequests) {
            if (pendingRequests.size() >= MAX_REQUESTS_QUEUED) {
                return false;
            }

            try {
                rp = new RequestPart(getFile(), range, Math.max(0,
                    transferState.getProgress()));
            } catch (IllegalArgumentException e) {
                // I need to do this because FileInfos are NOT immutable...
                Loggable.logWarningStatic(Download.class,
                        "Concurrent file change while requesting:" + e);
                throw new BrokenDownloadException(
                    "Concurrent file change while requesting: " + e);
            }
            pendingRequests.add(rp);
        }
        getPartner().sendMessagesAsynchron(rp);
        return true;
    }

    public Collection<RequestPart> getPendingRequests() {
        synchronized (pendingRequests) {
            return new ArrayList<RequestPart>(pendingRequests);
        }
    }

    /**
     * Adds a chunk to the download
     * 
     * @param chunk
     * @return true if the chunk was successfully appended to the download file.
     */
    public synchronized boolean addChunk(FileChunk chunk) {
        Reject.ifNull(chunk, "Chunk is null");
        assert chunk.file.isCompletelyIdentical(getFile());

        if (isBroken()) {
            return false;
        }

        // logFine("Received " + chunk);

        if (!isStarted()) {
            // donwload begins to start
            setStarted();
        }
        lastTouch.setTime(System.currentTimeMillis());

        // Remove pending requests for the received chunk since
        // the manager below might want to request new parts.
        Range range = Range.getRangeByLength(chunk.offset, chunk.data.length);
        synchronized (pendingRequests) {
            // Maybe the sender merged requests from us, so check all
            // requests
            for (Iterator<RequestPart> ip = pendingRequests.iterator(); ip
                .hasNext();)
            {
                RequestPart p = ip.next();
                if (p.getRange().contains(range)) {
                    ip.remove();
                }
            }
        }

        getCounter().chunkTransferred(chunk);

        manager.receivedChunk(this, chunk);
        return true;
    }

    /**
     * Requests this download from the partner.
     * 
     * @param startOffset
     */
    public void request(long startOffset) {
        getPartner().sendMessagesAsynchron(
            new RequestDownload(getFile(), startOffset));
    }

    /**
     * Requests to abort this dl
     */
    public void abort() {
        if (getPartner() != null && getPartner().isCompleteyConnected()) {
            getPartner().sendMessageAsynchron(new AbortDownload(getFile()),
                null);
        }
        getController().getTransferManager().downloadAborted(this);
    }

    /**
     * This download is queued at the remote side
     */
    public void setQueued() {
        Loggable.logFinerStatic(Download.class,
                "DL queued by remote side: " + this);
        queued = true;
    }

    @Override
    void setCompleted() {
        if (Util.usePartRequests(getController(), getPartner())) {
            getPartner().sendMessagesAsynchron(new StopUpload(getFile()));
        }
        super.setCompleted();
    }

    /**
     * @return if this is a pending download
     */
    public boolean isPending() {
        if (isCompleted()) {
            // not pending when completed
            return false;
        }
        return getPartner() == null || isBroken();
    }

    /**
     * Sets the download to a broken state.
     * 
     * @param problem
     * @param message
     */
    public void setBroken(TransferProblem problem, String message) {
        getController().getTransferManager().setBroken(this, problem, message);
    }

    /**
     * @return if this download is broken. timed out or has no connection
     *         anymore or (on blacklist in folder and isRequestedAutomatic)
     */
    public boolean isBroken() {
        if (super.isBroken()) {
            return true;
        }
        // timeout is, when dl is not enqued at remote side,
        // and has timeout
        boolean timedOut = System.currentTimeMillis()
            - Constants.DOWNLOAD_REQUEST_TIMEOUT_LIMIT > lastTouch.getTime()
            && !queued;
        if (timedOut) {
            Loggable.logWarningStatic(Download.class, "Abort cause: Timeout.");
            return true;
        }
        // Check queueing at remote side
        boolean isQueuedAtPartner = stillQueuedAtPartner();
        if (!isQueuedAtPartner) {
            Loggable.logWarningStatic(Download.class, "Abort cause: not queued.");
            return true;
        }
        // check blacklist
        if (automatic) {
            Folder folder = getFile().getFolder(
                getController().getFolderRepository());
            boolean onBlacklist = folder.getDiskItemFilter().isExcluded(
                getFile());
            if (onBlacklist) {
                Loggable.logWarningStatic(Download.class, "Abort cause: On blacklist.");
                return true;
            }

            // Check if newer file is available.
            boolean newerFileAvailable = getFile().isNewerAvailable(
                getController().getFolderRepository());
            if (newerFileAvailable) {
                Loggable.logWarningStatic(Download.class,
                    "Abort cause: Newer version available. "
                        + getFile().toDetailString());
                return true;
                // throw new RuntimeException("ABORT: " + this);
            }
        }

        return false;
    }

    /**
     * @return if this download is queued
     */
    public boolean isQueued() {
        return queued && !isBroken();
    }

    /*
     * General
     */

    public int hashCode() {
        int hash = 37;
        if (getFile() != null) {
            hash += getFile().hashCode();
        }
        return hash;
    }

    public boolean equals(Object o) {
        if (o instanceof Download) {
            Download other = (Download) o;
            return Util.equals(this.getFile(), other.getFile());
        }

        return false;
    }

    // General ****************************************************************

    @Override
    public String toString() {
        String msg = getFile().toDetailString();
        if (getPartner() != null) {
            msg += " from '" + getPartner().getNick() + "'";
            if (getPartner().isOnLAN()) {
                msg += " (local-net)";
            }
        } else {
            msg += " (pending)";
        }
        return msg;
    }

    @Override
    public FileInfo getFile() {
        // This is necessary, because FileInfo also contains version information
        // (which might be old at this point)
        if (manager != null) {
            return manager.getFileInfo();
        }
        return super.getFile();
    }

}