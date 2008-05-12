/* $Id: TransferManager.java,v 1.92 2006/04/30 14:24:17 totmacherr Exp $
 */
package de.dal33t.powerfolder.transfer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang.Validate;

import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Constants;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.FolderRepository;
import de.dal33t.powerfolder.event.ListenerSupportFactory;
import de.dal33t.powerfolder.event.TransferManagerEvent;
import de.dal33t.powerfolder.event.TransferManagerListener;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.message.AbortDownload;
import de.dal33t.powerfolder.message.AbortUpload;
import de.dal33t.powerfolder.message.DownloadQueued;
import de.dal33t.powerfolder.message.FileChunk;
import de.dal33t.powerfolder.message.RequestDownload;
import de.dal33t.powerfolder.message.TransferStatus;
import de.dal33t.powerfolder.net.ConnectionHandler;
import de.dal33t.powerfolder.util.Debug;
import de.dal33t.powerfolder.util.Format;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.TransferCounter;
import de.dal33t.powerfolder.util.compare.MemberComparator;
import de.dal33t.powerfolder.util.compare.ReverseComparator;

/**
 * Transfer manager for downloading/uploading files
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.92 $
 */
public class TransferManager extends PFComponent {
    /** The maximum size of a chunk transferred at once */
    public static final int MAX_CHUNK_SIZE = 32 * 1024;

    private static final DecimalFormat CPS_FORMAT = new DecimalFormat(
        "#,###,###,###.##");

    private boolean started;

    private Thread myThread;
    /** Uploads that are waiting to start */
    private List<Upload> queuedUploads;
    /** currently uploading */
    private List<Upload> activeUploads;
    /** The list of completed download */
    private List<Upload> completedUploads;
    /** currenly downloading */
    private ConcurrentMap<FileInfo, DownloadManager> dlManagers;
    /** A set of pending files, which should be downloaded */
    private List<Download> pendingDownloads;
    /** The list of completed download */
    private List<DownloadManager> completedDownloads;

    /** The trigger, where transfermanager waits on */
    private Object waitTrigger = new Object();
    private boolean transferCheckTriggered = false;
    /**
     * To lock the transfer checker. Lock this to make sure no transfer checks
     * are executed untill the lock is released.
     */
    private ReentrantLock downloadsLock = new ReentrantLock();

    /**
     * To lock the transfer checker. Lock this to make sure no transfer checks
     * are executed untill the lock is released.
     */
    private Lock uploadsLock = new ReentrantLock();

    /** Threadpool for Upload Threads */
    private ExecutorService threadPool;

    /** The currently calculated transferstatus */
    private TransferStatus transferStatus;

    /** The maximum concurrent uploads */
    private int allowedUploads;

    /** the counter for uploads (effecitve) */
    private TransferCounter uploadCounter;
    /** the counter for downloads (effecitve) */
    private TransferCounter downloadCounter;

    /** the counter for up traffic (real) */
    private TransferCounter totalUploadTrafficCounter;
    /** the counter for download traffic (real) */
    private TransferCounter totalDownloadTrafficCounter;

    /** Provides bandwidth for the transfers */
    private BandwidthProvider bandwidthProvider;

    /** Input limiter, currently shared between all LAN connections */
    private BandwidthLimiter sharedLANInputHandler;
    /** Input limiter, currently shared between all WAN connections */
    private BandwidthLimiter sharedWANInputHandler;
    /** Output limiter, currently shared between all LAN connections */
    private BandwidthLimiter sharedLANOutputHandler;
    /** Output limiter, currently shared between all WAN connections */
    private BandwidthLimiter sharedWANOutputHandler;

    private TransferManagerListener listenerSupport;

    private DownloadManagerFactory downloadManagerFactory =
        MultiSourceDownloadManager.factory;

    public TransferManager(Controller controller) {
        super(controller);
        this.started = false;
        this.queuedUploads = new CopyOnWriteArrayList<Upload>();
        this.activeUploads = new CopyOnWriteArrayList<Upload>();
        this.completedUploads = new CopyOnWriteArrayList<Upload>();
        this.dlManagers = new ConcurrentHashMap<FileInfo, DownloadManager>();
        this.pendingDownloads = new CopyOnWriteArrayList<Download>();
        this.completedDownloads = new CopyOnWriteArrayList<DownloadManager>();
        this.uploadCounter = new TransferCounter();
        this.downloadCounter = new TransferCounter();
        totalUploadTrafficCounter = new TransferCounter();
        totalDownloadTrafficCounter = new TransferCounter();

        // Create listener support
        this.listenerSupport = (TransferManagerListener) ListenerSupportFactory
            .createListenerSupport(TransferManagerListener.class);

        // maximum concurrent uploads
        allowedUploads = ConfigurationEntry.UPLOADS_MAX_CONCURRENT.getValueInt(
            getController()).intValue();
        if (allowedUploads <= 0) {
            throw new NumberFormatException("Illegal value for max uploads: "
                + allowedUploads);
        }

        bandwidthProvider = new BandwidthProvider();

        sharedWANOutputHandler = new BandwidthLimiter();
        sharedWANInputHandler = new BandwidthLimiter();

        checkConfigCPS(ConfigurationEntry.UPLOADLIMIT_WAN, 0);
        checkConfigCPS(ConfigurationEntry.DOWNLOADLIMIT_WAN, 0);
        checkConfigCPS(ConfigurationEntry.UPLOADLIMIT_LAN, 0);
        checkConfigCPS(ConfigurationEntry.DOWNLOADLIMIT_LAN, 0);

        // bandwidthProvider.setLimitBPS(sharedWANOutputHandler, maxCps);
        // set ul limit
        setAllowedUploadCPSForWAN(getConfigCPS(ConfigurationEntry.UPLOADLIMIT_WAN));
        setAllowedDownloadCPSForWAN(getConfigCPS(ConfigurationEntry.DOWNLOADLIMIT_WAN));

        sharedLANOutputHandler = new BandwidthLimiter();
        sharedLANInputHandler = new BandwidthLimiter();

        // bandwidthProvider.setLimitBPS(sharedLANOutputHandler, maxCps);
        // set ul limit
        setAllowedUploadCPSForLAN(getConfigCPS(ConfigurationEntry.UPLOADLIMIT_LAN));
        setAllowedDownloadCPSForLAN(getConfigCPS(ConfigurationEntry.DOWNLOADLIMIT_LAN));
    }

    /**
     * Checks if the configration entry exists, and if not sets it to a given
     * value.
     * 
     * @param entry
     * @param _cps
     */
    private void checkConfigCPS(ConfigurationEntry entry, long _cps) {
        String cps = entry.getValue(getController());
        if (cps == null)
            entry.setValue(getController(), Long.toString(_cps / 1024));
    }

    private long getConfigCPS(ConfigurationEntry entry) {
        String cps = entry.getValue(getController());
        long maxCps = 0;
        if (cps != null) {
            try {
                maxCps = (long) Double.parseDouble(cps) * 1024;
                if (maxCps < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                log().warn(
                    "Illegal value for KByte." + entry + " '" + cps + "'");
            }
        }
        return maxCps;
    }

    // General ****************************************************************

    /**
     * Starts the transfermanager thread
     */
    public void start() {
        if (!ConfigurationEntry.TRANSFER_MANAGER_ENABLED
            .getValueBoolean(getController()))
        {
            log().warn("Not starting TransferManager. disabled by config");
            return;
        }
        bandwidthProvider.start();

        threadPool = Executors.newCachedThreadPool();

        myThread = new Thread(new TransferChecker(), "Transfer manager");
        myThread.start();

        // Load all pending downloads
        loadDownloads();

        started = true;
        log().debug("Started");
    }

    /**
     * Shuts down the transfer manager
     */
    public void shutdown() {
        // Remove listners, not bothering them by boring shutdown events
        // ListenerSupportFactory.removeAllListeners(listenerSupport);

        // shutdown on thread
        if (myThread != null) {
            myThread.interrupt();
        }

        if (threadPool != null) {
            threadPool.shutdown();
        }

        // shutdown active uploads
        Upload[] uploads = getActiveUploads();
        for (int i = 0; i < uploads.length; i++) {
            // abort && shutdown uploads
            uploads[i].abort();
            uploads[i].shutdown();
        }

        bandwidthProvider.shutdown();

        if (started) {
            storeDownloads();
        }

        // abort / shutdown active downloads
        // done after storeDownloads(), so they are restored!
        downloadsLock.lock();
        try {
            for (DownloadManager man : dlManagers.values()) {
                man.abort();
            }
        } catch (AssertionError e) {
            log().error(e);
            throw e;
        } finally {
            downloadsLock.unlock();
        }

        started = false;
        log().debug("Stopped");
    }

    /**
     * for debug
     * 
     * @param suspended
     */
    public void setSuspendFireEvents(boolean suspended) {
        ListenerSupportFactory.setSuspended(listenerSupport, suspended);
        log().debug("setSuspendFireEvents: " + suspended);
    }

    /**
     * Triggers the workingn checker thread.
     */
    public void triggerTransfersCheck() {
        // log().verbose("Triggering transfers check");
        transferCheckTriggered = true;
        synchronized (waitTrigger) {
            waitTrigger.notifyAll();
        }
    }

    public BandwidthProvider getBandwidthProvider() {
        return bandwidthProvider;
    }

    public BandwidthLimiter getOutputLimiter(ConnectionHandler handler) {
        if (handler.isOnLAN())
            return sharedLANOutputHandler;
        return sharedWANOutputHandler;
    }

    public BandwidthLimiter getInputLimiter(ConnectionHandler handler) {
        if (handler.isOnLAN())
            return sharedLANInputHandler;
        return sharedWANInputHandler;
    }

    /**
     * Returns the transferstatus by calculating it new
     * 
     * @return the current status
     */
    public TransferStatus getStatus() {
        transferStatus = new TransferStatus();

        // Upload status
        transferStatus.activeUploads = activeUploads.size();
        transferStatus.queuedUploads = queuedUploads.size();

        transferStatus.maxUploads = getAllowedUploads();
        transferStatus.maxUploadCPS = getAllowedUploadCPSForWAN();
        transferStatus.currentUploadCPS = (long) uploadCounter
            .calculateCurrentCPS();
        transferStatus.uploadedBytesTotal = uploadCounter.getBytesTransferred();

        // Download status
        for (DownloadManager man : dlManagers.values()) {
            for (Download download : man.getSources()) {
                if (download.isStarted()) {
                    transferStatus.activeDownloads++;
                } else {
                    transferStatus.queuedDownloads++;
                }
            }
        }

        transferStatus.maxDownloads = Integer.MAX_VALUE;
        transferStatus.maxDownloadCPS = Double.MAX_VALUE;
        transferStatus.currentDownloadCPS = (long) downloadCounter
            .calculateCurrentCPS();
        transferStatus.downloadedBytesTotal = downloadCounter
            .getBytesTransferred();

        return transferStatus;
    }

    // General Transfer callback methods **************************************

    /**
     * Returns the MultiSourceDownload, that's managing the given info.
     * 
     * @param loaddownload
     * @return
     * @return
     */
    private DownloadManager getDownloadManagerFor(FileInfo info) {
        Validate.notNull(info);
        return dlManagers.get(info);
    }

    /**
     * Sets an transfer as started
     * 
     * @param transfer
     *            the transfer
     */
    void setStarted(Transfer transfer) {
        if (transfer instanceof Upload) {
            uploadsLock.lock();
            try {
                queuedUploads.remove(transfer);
                activeUploads.add((Upload) transfer);
            } finally {
                uploadsLock.unlock();
            }

            // Fire event
            fireUploadStarted(new TransferManagerEvent(this, (Upload) transfer));
        } else if (transfer instanceof Download) {
            // Fire event
            fireDownloadStarted(new TransferManagerEvent(this,
                (Download) transfer));
        }

        log().debug("Transfer started: " + transfer);
    }

    /**
     * Callback to inform, that a download has been enqued at the remote ide
     * 
     * @param dlQueuedRequest
     *            the download request
     * @param member
     */
    public void setQueued(DownloadQueued dlQueuedRequest, Member member) {

        downloadsLock.lock();
        try {
            DownloadManager man = dlManagers.get(dlQueuedRequest.file);
            if (man == null
                || !man.getFileInfo().isCompletelyIdentical(
                    dlQueuedRequest.file))
            {
                boolean completed = false;
                for (DownloadManager m : completedDownloads) {
                    if (dlQueuedRequest.file.isCompletelyIdentical(m
                        .getFileInfo()))
                    {
                        completed = true;
                        break;
                    }
                }
                log().warn(
                    "Found queued download which isn't active:"
                        + dlQueuedRequest.file + " (Maybe completed = "
                        + completed + ").");
                return;
            }
            Download dl = man.getSourceFor(member);
            if (dl != null) {
                // set this dl as queued
                dl.setQueued();
                // Fire
                fireDownloadQueued(new TransferManagerEvent(this, dl));
            }
        } catch (AssertionError e) {
            log().error(e);
            throw e;
        } finally {
            downloadsLock.unlock();
        }
    }

    /**
     * Sets a transfer as broken, removes from queues
     * 
     * @param tranfer
     *            the transfer
     * @param transferProblem
     *            the problem that broke the transfer
     */
    void setBroken(Transfer transfer, TransferProblem transferProblem) {
        setBroken(transfer, transferProblem, null);
    }

    /**
     * Sets the download broken, removes from active queue and might adds a
     * pending download.
     * 
     * @param dlMan
     * @param problem
     * @param message
     */
    void setBroken(DownloadManager dlMan, TransferProblem problem,
        String message)
    {
        downloadsLock.lock();
        try {
            log().warn(
                "Download broken (manager): " + dlMan + " problem: " + problem
                    + " msg:" + message);

            // It's possible that the manager is still here if it had
            // only one source that did break it.
            if (dlManagers.containsValue(dlMan)) {
                removeDownloadManager(dlMan);
            }

            if (!dlMan.isRequestedAutomatic()) {
                log().info("Enqueueing pending download " + dlMan);
                enquePendingDownload(new Download(this, dlMan.getFileInfo(),
                    dlMan.isRequestedAutomatic()));
            }
        } catch (AssertionError e) {
            log().error(e);
            throw e;
        } finally {
            downloadsLock.unlock();
        }
    }

    /**
     * Sets a transfer as broken, removes from queues
     * 
     * @param tranfer
     *            the transfer
     * @param transferProblem
     *            the problem that broke the transfer
     * @param problemInformation
     *            specific information about the problem
     */
    void setBroken(Transfer transfer, TransferProblem transferProblem,
        String problemInformation)
    {
        // Ensure shutdown
        transfer.shutdown();

        boolean transferFound = false;
        if (transfer instanceof Download) {
            Download dl = (Download) transfer;
            log().warn(
                "Download broken: " + transfer + " "
                    + (transferProblem == null ? "" : transferProblem) + ": "
                    + problemInformation);
            downloadsLock.lock();
            try {
                // transferFound = downloads.remove(transfer.getFile()) != null;
                transferFound = dl.getDownloadManager() != null;
                if (transferFound) {
                    removeDownload(dl);
                }
            } catch (AssertionError e) {
                log().error(e);
                throw e;
            } finally {
                downloadsLock.unlock();
            }
            // Tell remote peer if possible
            FileInfo fInfo = dl.getFile();
            Member from = dl.getPartner();
            if (from != null && from.isCompleteyConnected()) {
                from.sendMessageAsynchron(new AbortDownload(fInfo), null);
            }

            if (transferFound) {
                dl.setTransferProblem(transferProblem);
                dl.setProblemInformation(problemInformation);

                // Fire event
                fireDownloadBroken(new TransferManagerEvent(this, dl));
            }
        } else if (transfer instanceof Upload) {
            log().warn(
                "Upload broken: " + transfer + " "
                    + (transferProblem == null ? "" : transferProblem) + ": "
                    + problemInformation);
            uploadsLock.lock();
            try {
                transferFound = queuedUploads.remove(transfer);
                transferFound = activeUploads.remove(transfer) || transferFound;
            } finally {
                uploadsLock.unlock();
            }

            // Tell remote peer if possible
            Upload ul = (Upload) transfer;
            if (ul.getPartner().isCompleteyConnected()) {
                log().warn("Sending abort upload of " + ul);
                ul.getPartner().sendMessagesAsynchron(
                    new AbortUpload(ul.getFile()));
            }

            // Fire event
            if (transferFound) {
                fireUploadBroken(new TransferManagerEvent(this,
                    (Upload) transfer));
            }
        }

        // Now trigger, to check uploads/downloads to start
        triggerTransfersCheck();
    }

    /**
     * Breaks all transfers from and to that node. Usually done on disconnect
     * 
     * @param node
     */
    public void breakTransfers(Member node) {
        // Search for uls to break
        if (!queuedUploads.isEmpty()) {
            for (Upload upload : queuedUploads) {
                if (node.equals(upload.getPartner())) {
                    setBroken(upload, TransferProblem.NODE_DISCONNECTED, node
                        .getNick());
                }
            }
        }

        if (!activeUploads.isEmpty()) {
            for (Upload upload : activeUploads) {
                if (node.equals(upload.getPartner())) {
                    setBroken(upload, TransferProblem.NODE_DISCONNECTED, node
                        .getNick());
                }
            }
        }

        // Search for dls to break
        downloadsLock.lock();
        try {
            for (DownloadManager man : dlManagers.values()) {
                for (Download download : man.getSources()) {
                    if (node.equals(download.getPartner())) {
                        setBroken(download, TransferProblem.NODE_DISCONNECTED, node
                            .getNick());
                    }
                }
            }
        } catch (AssertionError e) {
            log().error(e);
            throw e;
        } finally {
            downloadsLock.unlock();
        }
    }

    void setCompleted(DownloadManager download) {
        assert download.isDone();

        FileInfo fInfo = download.getFileInfo();
        // Inform other folder member of added file
        Folder folder = fInfo.getFolder(getController().getFolderRepository());
        if (folder != null) {
            // scan in new downloaded file
            // TODO React on failed scan?
            // TODO PREVENT further uploads of this file unless it's "there"
            // Search for active uploads of the file and break them
            boolean abortedDL;
            do {
                abortedDL = false;
                for (Upload u : activeUploads) {
                    if (u.getFile().equals(fInfo)) {
                        abortDownload(fInfo, u.getPartner());
                        abortedDL = true;
                    }
                }
                if (abortedDL) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                    }
                }
            } while (abortedDL);

            assert getActiveUploads(fInfo).size() == 0;
            
            if (!folder.scanDownloadFile(fInfo, download.getTempFile())) {
                log().debug("Scanning of completed file failed.");
                downloadsLock.lock();
                try {
                    download.broken("Failed to scan downloaded file");
                    return;
                } catch (AssertionError e) {
                    log().error(e);
                    throw e;
                } finally {
                    downloadsLock.unlock();
                }
            }
        }
        downloadsLock.lock();
        try {
            // Might be broken in the meantime
            if (!download.isCompleted()) {
                return;
            }
            completedDownloads.add(download);
            removeDownloadManager(download);
        } catch (AssertionError e) {
            log().error(e);
            throw e;
        } finally {
            downloadsLock.unlock();
        }
        // Auto cleanup of Downloads
        if (ConfigurationEntry.DOWNLOADS_AUTO_CLEANUP
            .getValueBoolean(getController()))
        {
            if (log().isVerbose()) {
                log().verbose("Auto-cleaned " + download);
            }
            clearCompletedDownload(download);
        }
    }

    /**
     * Returns all uploads of the given file.
     * @param info
     * @return
     */
    private List<Upload> getActiveUploads(FileInfo info) {
        List<Upload> ups = new ArrayList<Upload>();
        for (Upload u: activeUploads) {
            if (u.getFile().equals(info)) {
                ups.add(u);
            }
        }
        return ups;
    }

    /**
     * Callback method to indicate that a transfer is completed
     * 
     * @param transfer
     */
    void setCompleted(Transfer transfer) {
        boolean transferFound = false;

        transfer.setCompleted();

        if (transfer instanceof Download) {
            // Fire event
            fireDownloadCompleted(new TransferManagerEvent(this,
                (Download) transfer));


            Integer nDlFromNode = countNodesActiveAndQueuedDownloads().get(
                transfer.getPartner());
            boolean requestMoreFiles = nDlFromNode == null
                || nDlFromNode.intValue() == 0;
            if (!requestMoreFiles) {
                // Hmm maybe end of transfer queue is near (20% or less filled),
                // request if yes!
                if (transfer.getPartner().isOnLAN()) {
                    requestMoreFiles = nDlFromNode.intValue() <= Constants.MAX_DLS_FROM_LAN_MEMBER / 5;
                } else {
                    requestMoreFiles = nDlFromNode.intValue() <= Constants.MAX_DLS_FROM_INET_MEMBER / 5;
                }
            }

            if (requestMoreFiles) {
                // Trigger filerequestor
                getController().getFolderRepository().getFileRequestor()
                    .triggerFileRequesting(transfer.getFile().getFolderInfo());
            } else {
                log().verbose(
                    "Not triggering file requestor. " + nDlFromNode
                        + " more dls from " + transfer.getPartner());
            }

        } else if (transfer instanceof Upload) {
            uploadsLock.lock();
            try {
                transferFound = queuedUploads.remove(transfer);
                transferFound = activeUploads.remove(transfer) || transferFound;
                completedUploads.add((Upload) transfer);
            } finally {
                uploadsLock.unlock();
            }

            if (transferFound) {
                // Fire event
                fireUploadCompleted(new TransferManagerEvent(this,
                    (Upload) transfer));
            }

            // Auto cleanup of uploads
            if (ConfigurationEntry.UPLOADS_AUTO_CLEANUP
                .getValueBoolean(getController()))
            {
                if (log().isVerbose()) {
                    log().verbose("Auto-cleaned " + transfer);
                }
                clearCompletedUpload((Upload) transfer);
            }

        }

        // Now trigger, to start next transfer
        triggerTransfersCheck();

        if (logVerbose) {
            log().verbose("Transfer completed: " + transfer);
        }
    }

    // Upload management ******************************************************

    /**
     * This method is called after any change associated with bandwidth. I.e.:
     * upload limits, silent mode
     */
    public void updateSpeedLimits() {
        int throttle = 100;

        if (getController().isSilentMode()) {
            try {
                throttle = Integer
                    .parseInt(ConfigurationEntry.UPLOADLIMIT_SILENTMODE_THROTTLE
                        .getValue(getController()));
                if (throttle < 10) {
                    throttle = 10;
                } else if (throttle > 100) {
                    throttle = 100;
                }
            } catch (NumberFormatException nfe) {
                throttle = 100;
                // log().debug(nfe);
            }
        }

        // Any setting that is "unlimited" will stay unlimited!
        bandwidthProvider.setLimitBPS(sharedLANOutputHandler,
            getAllowedUploadCPSForLAN() * throttle / 100);
        bandwidthProvider.setLimitBPS(sharedWANOutputHandler,
            getAllowedUploadCPSForWAN() * throttle / 100);
        bandwidthProvider.setLimitBPS(sharedLANInputHandler,
            getAllowedDownloadCPSForLAN() * throttle / 100);
        bandwidthProvider.setLimitBPS(sharedWANInputHandler,
            getAllowedDownloadCPSForWAN() * throttle / 100);
    }

    /**
     * Sets the maximum upload bandwidth usage in CPS
     * 
     * @param allowedCPS
     */
    public void setAllowedUploadCPSForWAN(long allowedCPS) {
        if (allowedCPS != 0 && allowedCPS < 3 * 1024
            && !getController().isVerbose())
        {
            log().warn("Setting upload limit to a minimum of 3 KB/s");
            allowedCPS = 3 * 1024;
        }

        // Store in config
        ConfigurationEntry.UPLOADLIMIT_WAN.setValue(getController(), ""
            + (allowedCPS / 1024));

        updateSpeedLimits();

        log().info(
            "Upload limit: " + allowedUploads + " allowed, at maximum rate of "
                + (getAllowedUploadCPSForWAN() / 1024) + " KByte/s");
    }

    /**
     * @return the allowed upload rate (internet) in CPS
     */
    public long getAllowedUploadCPSForWAN() {
        return Integer.parseInt(ConfigurationEntry.UPLOADLIMIT_WAN
            .getValue(getController())) * 1024;
    }

    /**
     * Sets the maximum download bandwidth usage in CPS
     * 
     * @param allowedCPS
     */
    public void setAllowedDownloadCPSForWAN(long allowedCPS) {
        // if (allowedCPS != 0 && allowedCPS < 3 * 1024
        // && !getController().isVerbose())
        // {
        // log().warn("Setting download limit to a minimum of 3 KB/s");
        // allowedCPS = 3 * 1024;
        // }
        //
        // Store in config
        ConfigurationEntry.DOWNLOADLIMIT_WAN.setValue(getController(), ""
            + (allowedCPS / 1024));

        updateSpeedLimits();

        log().info(
            "Download limit: " + allowedUploads
                + " allowed, at maximum rate of "
                + (getAllowedDownloadCPSForWAN() / 1024) + " KByte/s");
    }

    /**
     * @return the allowed download rate (internet) in CPS
     */
    public long getAllowedDownloadCPSForWAN() {
        return ConfigurationEntry.DOWNLOADLIMIT_WAN
            .getValueInt(getController()) * 1024;
    }

    /**
     * Sets the maximum upload bandwidth usage in CPS for LAN
     * 
     * @param allowedCPS
     */
    public void setAllowedUploadCPSForLAN(long allowedCPS) {
        if (allowedCPS != 0 && allowedCPS < 3 * 1024
            && !getController().isVerbose())
        {
            log().warn("Setting upload limit to a minimum of 3 KB/s");
            allowedCPS = 3 * 1024;
        }
        // Store in config
        ConfigurationEntry.UPLOADLIMIT_LAN.setValue(getController(), ""
            + (allowedCPS / 1024));

        updateSpeedLimits();

        log().info(
            "LAN Upload limit: " + allowedUploads
                + " allowed, at maximum rate of "
                + (getAllowedUploadCPSForLAN() / 1024) + " KByte/s");
    }

    /**
     * @return the allowed upload rate (LAN) in CPS
     */
    public long getAllowedUploadCPSForLAN() {
        return ConfigurationEntry.UPLOADLIMIT_LAN.getValueInt(getController()) * 1024;

    }

    /**
     * Sets the maximum upload bandwidth usage in CPS for LAN
     * 
     * @param allowedCPS
     */
    public void setAllowedDownloadCPSForLAN(long allowedCPS) {
        // if (allowedCPS != 0 && allowedCPS < 3 * 1024
        // && !getController().isVerbose())
        // {
        // log().warn("Setting upload limit to a minimum of 3 KB/s");
        // allowedCPS = 3 * 1024;
        // }
        // Store in config
        ConfigurationEntry.DOWNLOADLIMIT_LAN.setValue(getController(), ""
            + (allowedCPS / 1024));

        updateSpeedLimits();

        log().info(
            "LAN Download limit: " + allowedUploads
                + " allowed, at maximum rate of "
                + (getAllowedDownloadCPSForLAN() / 1024) + " KByte/s");
    }

    /**
     * @return the allowed download rate (LAN) in CPS
     */
    public long getAllowedDownloadCPSForLAN() {
        return Integer.parseInt(ConfigurationEntry.DOWNLOADLIMIT_LAN
            .getValue(getController())) * 1024;
    }

    /**
     * @return true if the manager has free upload slots
     */
    private boolean hasFreeUploadSlots() {
        Set<Member> uploadsTo = new HashSet<Member>();
        for (Upload upload : activeUploads) {
            uploadsTo.add(upload.getPartner());
        }
        return uploadsTo.size() < allowedUploads;
    }

    /**
     * @return the maximum number of allowed uploads
     */
    public int getAllowedUploads() {
        return allowedUploads;
    }

    /**
     * @return the counter for upload speed
     */
    public TransferCounter getUploadCounter() {
        return uploadCounter;
    }

    /**
     * @return the counter for total upload traffic (real)
     */
    public TransferCounter getTotalUploadTrafficCounter() {
        return totalUploadTrafficCounter;
    }

    /**
     * Queues a upload into the upload queue. Breaks all former upload requests
     * for the file from that member. Will not be queued if file not exists or
     * is deleted in the meantime or if the connection with the requestor is
     * lost.
     * 
     * @param from
     * @param dl
     * @return the enqued upload, or null if not queued.
     */
    public Upload queueUpload(Member from, RequestDownload dl) {
        log().debug("Received download request from " + from + ": " + dl);
        if (dl == null || dl.file == null) {
            throw new NullPointerException("Downloadrequest/File is null");
        }
        // Never upload db files !!
        if (Folder.DB_FILENAME.equalsIgnoreCase(dl.file.getName())
            || Folder.DB_BACKUP_FILENAME.equalsIgnoreCase(dl.file.getName()))
        {
            log()
                .error(
                    from.getNick()
                        + " has illegally requested to download a folder database file");
            return null;
        }
        if (dl.file.getFolder(getController().getFolderRepository()) == null) {
            log().error(
                "Received illegal download request from " + from.getNick()
                    + ". Not longer on folder " + dl.file.getFolderInfo());
        }
        downloadsLock.lock();
        try {
            if (dlManagers.containsKey(dl.file)) {
                log()
                    .warn(
                        "Not queuing upload, active download of the file is in progress.");
                return null;
            }
        } catch (AssertionError e) {
            log().error(e);
            throw e;
        } finally {
            downloadsLock.unlock();
        }
        Upload upload = new Upload(this, from, dl);
        FolderRepository repo = getController().getFolderRepository();
        File diskFile = upload.getFile().getDiskFile(repo);
        boolean fileInSyncWithDisk = upload.getFile().inSyncWithDisk(diskFile);
        if (!fileInSyncWithDisk) {
            // This should free up an otherwise waiting for download partner
            Folder folder = upload.getFile().getFolder(repo);
            folder.recommendScanOnNextMaintenance();
            log().warn(
                "File not in sync with disk: '"
                    + upload.getFile().toDetailString()
                    + "', should be modified at " + diskFile.lastModified());
            return null;
        }

        if (upload.isBroken()) { // connection lost
            // Check if this download is broken
            return null;
        }

        Upload oldUpload = null;
        // Check if we have a old upload to break
        uploadsLock.lock();
        try {
            int oldUploadIndex = activeUploads.indexOf(upload);
            if (oldUploadIndex >= 0) {
                oldUpload = activeUploads.get(oldUploadIndex);
                activeUploads.remove(oldUploadIndex);
            }
            oldUploadIndex = queuedUploads.indexOf(upload);
            if (oldUploadIndex >= 0) {
                if (oldUpload != null) {
                    // Should never happen
                    throw new IllegalStateException(
                        "Found illegal upload. is in list of queued AND active uploads: "
                            + oldUpload);
                }
                oldUpload = queuedUploads.get(oldUploadIndex);
                queuedUploads.remove(oldUploadIndex);
            }

            log().debug(
                "Upload enqueud: " + dl.file.toDetailString()
                    + ", startOffset: " + dl.startOffset + ", to: " + from);
            queuedUploads.add(upload);
        } finally {
            uploadsLock.unlock();
        }

        if (oldUpload != null) {
            log().warn(
                "Received already known download request for " + dl.file
                    + " from " + from.getNick() + ", overwriting old request");
            // Stop former upload request
            oldUpload.abort();
            oldUpload.shutdown();
            setBroken(oldUpload, TransferProblem.OLD_UPLOAD, from.getNick());
        }

        // Trigger working thread on upload enqueued
        triggerTransfersCheck();

        // If upload is not started, tell peer
        if (!upload.isStarted()) {
            from.sendMessageAsynchron(new DownloadQueued(upload.getFile()),
                null);
        } else {
            log().warn("Optimization!");
        }

        if (!upload.isBroken()) {
            fireUploadRequested(new TransferManagerEvent(this, upload));
        }

        return upload;
    }

    /**
     * Aborts a upload
     * 
     * @param fInfo
     *            the file to upload
     * @param to
     *            the member where is file is going to
     */
    public void abortUpload(FileInfo fInfo, Member to) {
        if (fInfo == null) {
            throw new NullPointerException(
                "Unable to abort upload, file is null");
        }
        if (to == null) {
            throw new NullPointerException(
                "Unable to abort upload, to-member is null");
        }

        Upload abortedUpload = null;

        for (Upload upload : queuedUploads) {
            if (upload.getFile().equals(fInfo)
                && to.equals(upload.getPartner()))
            {
                // Remove upload from queue
                uploadsLock.lock();
                try {
                    queuedUploads.remove(upload);
                } finally {
                    uploadsLock.unlock();
                }
                upload.abort();
                abortedUpload = upload;
            }
        }

        for (Upload upload : activeUploads) {
            if (upload.getFile().equals(fInfo)
                && to.equals(upload.getPartner()))
            {
                // Remove upload from queue
                uploadsLock.lock();
                try {
                    activeUploads.remove(upload);
                } finally {
                    uploadsLock.unlock();
                }
                upload.abort();
                abortedUpload = upload;
            }
        }

        if (abortedUpload != null) {
            fireUploadAborted(new TransferManagerEvent(this, abortedUpload));

            // Trigger check
            triggerTransfersCheck();
        }
    }

    /**
     * Perfoms a upload in the tranfsermanagers threadpool.
     * 
     * @param uploadPerformer
     */
    void perfomUpload(Runnable uploadPerformer) {
        threadPool.submit(uploadPerformer);
    }

    /**
     * @return the currently active uploads
     */
    public Upload[] getActiveUploads() {
        return activeUploads.toArray(new Upload[0]);
    }

    /**
     * @return the total number of queued / active uploads
     */
    public int countLiveUploads() {
        return activeUploads.size() + queuedUploads.size();
    }

    /**
     * @return the total number of uploads
     */
    public int countAllUploads() {
        return activeUploads.size() + queuedUploads.size()
            + completedUploads.size();
    }

    /**
     * @param folder
     *            the folder.
     * @return the number of uploads on the folder
     */
    public int countUploadsOn(Folder folder) {
        int nUploads = 0;
        for (Upload upload : activeUploads) {
            if (upload.getFile().getFolderInfo().equals(folder.getInfo())) {
                nUploads++;
            }
        }
        for (Upload upload : queuedUploads) {
            if (upload.getFile().getFolderInfo().equals(folder.getInfo())) {
                nUploads++;
            }
        }
        return nUploads;
    }

    /**
     * Answers all queued uploads
     * 
     * @return Array of all queued upload
     */
    public Upload[] getQueuedUploads() {
        return queuedUploads.toArray(new Upload[0]);

    }

    /**
     * Answers, if we are uploading to this member
     * 
     * @param member
     *            the receiver
     * @return true if we are uploading to that member
     */
    public boolean uploadingTo(Member member) {
        return uploadingToSize(member) >= 0;
    }

    /**
     * @param member
     *            the receiver
     * @return the total size of bytes of the files currently upload to that
     *         member. Or -1 if not uploading to that member.
     */
    public long uploadingToSize(Member member) {
        long size = 0;
        boolean uploading = false;
        for (Upload upload : activeUploads) {
            if (member.equals(upload.getPartner())) {
                size += upload.getFile().getSize();
                uploading = true;
            }
        }
        if (!uploading) {
            return -1;
        }
        return size;
    }

    // Download management ***************************************************

    /**
     * Be sure to hold downloadsLock when calling this method!
     */
    private void removeDownload(Download download) {
        assert downloadsLock.isHeldByCurrentThread();

        DownloadManager man = download.getDownloadManager();
        
        if (!man.getFileInfo().isCompletelyIdentical(download.getFile())) {
            log().debug("Got abort for old download, ignoring.");
            return;
        }
        if (man.getSourceFor(download.getPartner()) == null) {
            log()
                .info(
                    "Not removing source of invalid partner! (Maybe got removed before)");
            return;
        }
        man.removeSource(download);
        if (!man.hasSources()) {
            log().debug("No further sources in that manager, removing it!");
            if (!man.isDone()) {
                man.broken("Out of sources for download");
            }
            // If the downloadmanager itself got broken it may happen that it's
            // still managed. 
            // TODO Removing of managers should be done "better"
            if (dlManagers.containsValue(man)) {
                removeDownloadManager(man);
            } 
        }
    }

    /**
     * @return the download counter
     */
    public TransferCounter getDownloadCounter() {
        return downloadCounter;
    }

    /**
     * @return the download traffic counter (real)
     */
    public TransferCounter getTotalDownloadTrafficCounter() {
        return totalDownloadTrafficCounter;
    }

    /**
     * Addds a file for download if source is not known. Download will be
     * started when a source is found.
     * 
     * @param download
     * @return true if succeeded
     */
    public boolean enquePendingDownload(Download download) {
        Validate.notNull(download);

        FileInfo fInfo = download.getFile();
        if (download.isRequestedAutomatic()) {
            log()
                .verbose("Not adding pending download, is a auto-dl: " + fInfo);
            return false;
        }

        if (!getController().getFolderRepository().hasJoinedFolder(
            fInfo.getFolderInfo()))
        {
            log().warn("Not adding pending download, not on folder: " + fInfo);
            return false;
        }

        Folder folder = fInfo.getFolder(getController().getFolderRepository());
        FileInfo localFile = folder != null ? folder.getFile(fInfo) : null;
        if (fInfo != null && localFile != null
            && fInfo.isCompletelyIdentical(localFile))
        {
            log().warn("Not adding pending download, already have: " + fInfo);
            return false;
        }

        boolean contained = true;

        // Add to pending list
        downloadsLock.lock();
        try {
            if (!pendingDownloads.contains(download)) {
                contained = false;
                pendingDownloads.add(0, download);
            }
        } catch (AssertionError e) {
            log().error(e);
            throw e;
        } finally {
            downloadsLock.unlock();
        }

        if (!contained) {
            log().debug("Pending download added for: " + fInfo);
            firePendingDownloadEnqueud(new TransferManagerEvent(this, download));
        }
        return true;
    }

    public DownloadManagerFactory getDownloadManagerFactory() {
        return downloadManagerFactory;
    }

    public void setDownloadManagerFactory(
        DownloadManagerFactory downloadManagerFactory)
    {
        this.downloadManagerFactory = downloadManagerFactory;
    }

    /**
     * Requests to download the newest version of the file. Returns null if
     * download wasn't enqued at a node. Download is then pending
     * 
     * @param fInfo
     * @return the member where the dl was requested at, or null if no source
     *         available (DL was NOT started)
     */
    public DownloadManager downloadNewestVersion(FileInfo fInfo) {
        return downloadNewestVersion(fInfo, false);
    }

    /**
     * Requests to download the newest version of the file. Returns null if
     * download wasn't enqued at a node. Download is then pending or blacklisted
     * (for autodownload)
     * 
     * @param fInfo
     * @param automatic
     *            if this download was requested automatically
     * @return the member where the dl was requested at, or null if no source
     *         available (DL was NOT started) and null if blacklisted
     */
    public DownloadManager downloadNewestVersion(FileInfo fInfo,
        boolean automatic)
    {
        Folder folder = fInfo.getFolder(getController().getFolderRepository());
        if (folder == null) {
            // on shutdown folder maybe null here
            return null;
        }

        // Check if the FileInfo is valid.
        // (This wouldn't be necessary, if the info had already checked itself.)
        try {
            fInfo.validate();
        } catch (Exception e) {
            log().error(e);
            return null;
        }

        downloadsLock.lock();
        try {
            if (automatic) {
                // return null if in blacklist on automatic download
                if (folder.getBlacklist().isIgnored(fInfo)) {
                    return null;
                }

                // Check if we have the file already downloaded in the meantime.
                FileInfo localFile = folder.getFile(fInfo);
                if (localFile != null && !fInfo.isNewerThan(localFile)) {
                    log().verbose(
                        "NOT requesting download, already have: "
                            + fInfo.toDetailString());
                    return null;
                }
            }

            if (!getController().getFolderRepository().hasJoinedFolder(
                fInfo.getFolderInfo()))
            {
                return null;
            }

            // only if we have joined the folder
            List<Member> sources = getSourcesFor(fInfo);
            // log().verbose("Got " + sources.length + " sources for " + fInfo);

            // Now walk through all sources and get the best one
            // Member bestSource = null;
            FileInfo newestVersionFile = fInfo.getNewestVersion(getController()
                .getFolderRepository());
            // ap<>
            Map<Member, Integer> downloadCountList = countNodesActiveAndQueuedDownloads();

            Collection<Member> bestSources = new LinkedList<Member>();
            for (Member source : sources) {
                FileInfo remoteFile = source.getFile(fInfo);
                if (remoteFile == null) {
                    continue;
                }

                // Skip sources with old versions
                if (newestVersionFile != null
                    && newestVersionFile.isNewerThan(remoteFile))
                {
                    continue;
                }

                int nDownloadFrom = 0;
                if (downloadCountList.containsKey(source)) {
                    nDownloadFrom = downloadCountList.get(source).intValue();
                }
                int maxAllowedDls = source.isOnLAN()
                    ? Constants.MAX_DLS_FROM_LAN_MEMBER
                    : Constants.MAX_DLS_FROM_INET_MEMBER;
                if (nDownloadFrom >= maxAllowedDls) {
                    // No more dl from this node allowed, skip
                    // log().warn("No more download allowed from " + source);
                    continue;
                }

                bestSources.add(source);
            }

            if (newestVersionFile != null) {
                // Check if the FileInfo is valid.
                // (This wouldn't be necessary, if the info had already checked
                // itself.)
                try {
                    newestVersionFile.validate();
                } catch (Exception e) {
                    log().error(e);
                    return null;
                }

                for (Member bestSource : bestSources) {
                    Download download;
                    download = new Download(this, newestVersionFile, automatic);
                    if (logVerbose) {
                        log().verbose(
                            "Best source for " + fInfo + " is " + bestSource);
                    }
                    requestDownload(download, bestSource);
                }
            }

            if (bestSources.isEmpty() && !automatic) {
                // Okay enque as pending download if was manually requested
                enquePendingDownload(new Download(this, fInfo, automatic));
                return null;
            }
            return getActiveDownload(fInfo);
        } catch (AssertionError e) {
            log().error(e);
            throw e;
        } finally {
            downloadsLock.unlock();
        }
    }

    /**
     * Requests the download from that member
     * 
     * @param download
     * @param from
     */
    private void requestDownload(final Download download, final Member from) {
        final FileInfo fInfo = download.getFile();

        boolean dlWasRequested = false;
        // Lock/Disable transfer checker
        DownloadManager man;
        downloadsLock.lock();
        try {
            man = dlManagers.get(fInfo);

            if (man == null || fInfo.isNewerThan(man.getFileInfo())) {
                if (man != null) {
                    assert !man.isDone();
                    log().debug(
                        "Got active download of older file version, aborting.");
                    man.abortAndCleanup();
                }
                try {
                    man = new MultiSourceDownloadManager(getController(),
                        fInfo, download.isRequestedAutomatic());
                } catch (IOException e) {
                    // Something gone badly wrong
                    log().error(e);
                    return;
                }
                if (dlManagers.put(fInfo, man) != null) {
                    throw new AssertionError("Found old manager!");
                }
            }
        } catch (AssertionError e) {
            log().error(e);
            throw e;
        } finally {
            downloadsLock.unlock();
        }
        boolean uploadAborted = false;
        uploadsLock.lock();
        try {
            for (Upload u : activeUploads) {
                if (u.getFile().equals(fInfo)) {
                    uploadAborted = true;
                    abortUpload(fInfo, u.getPartner());
                }
            }
        } finally {
            uploadsLock.unlock();
        }
        if (uploadAborted) {
            log().debug("Aborted uploads of file to be downloaded. DLMan: " + man);
        }

        downloadsLock.lock();
        try {
            final DownloadManager manager = man;

            if (manager.getSourceFor(from) == null && !manager.isDone()
                && manager.allowsSourceFor(from))
            {
                if (logEnabled) {
                    log().debug(
                        "Requesting " + fInfo.toDetailString() + " from "
                            + from);
                }

                if (download.getFile().isNewerThan(manager.getFileInfo())) {
                    log().error(
                        "Requested newer download: " + download + " than "
                            + manager);
                }
                dlWasRequested = true;
                pendingDownloads.remove(download);
                download.setPartner(from);
                download.setDownloadManager(manager);
                manager.addSource(download);
            }
        } catch (AssertionError e) {
            log().error(e);
            throw e;
        } finally {
            downloadsLock.unlock();
        }
        if (dlWasRequested) {
            log().debug("File really was requested!");
            // Fire event
            fireDownloadRequested(new TransferManagerEvent(this, download));
        }
    }

    /**
     * Finds the sources for the file. Returns only sources which are connected
     * The members are sorted in order of best source.
     * <p>
     * WARNING: Versions of files are ingnored
     * 
     * @param fInfo
     * @return the list of members, where the file is available
     */
    public List<Member> getSourcesFor(FileInfo fInfo) {
        Reject.ifNull(fInfo, "File is null");
        Folder folder = fInfo.getFolder(getController().getFolderRepository());
        Reject.ifNull(folder, "Folder not joined of file: " + fInfo);

        // List<Member> nodes = getController().getNodeManager()
        // .getNodeWithFileListFrom(fInfo.getFolderInfo());
        List<Member> sources = new ArrayList<Member>();
        // List<Member> sources = new ArrayList<Member>(nodes.size());
        for (Member node : folder.getMembers()) {
            if (node.isCompleteyConnected() && !node.isMySelf()
                && node.hasFile(fInfo))
            {
                // node is connected and has file
                sources.add(node);
            }
        }

        // Sort by the best upload availibility
        Collections.shuffle(sources);
        Collections.sort(sources, new ReverseComparator(
            MemberComparator.BY_UPLOAD_AVAILIBILITY));

        return sources;
    }

    /**
     * Aborts all automatically enqueued download of a folder.
     * 
     * @param folder
     *            the folder to break downloads on
     */
    public void abortAllAutodownloads(Folder folder) {
        int aborted = 0;
        downloadsLock.lock();
        try {
            for (DownloadManager dl : getActiveDownloads()) {
                boolean fromFolder = folder.getInfo().equals(
                    dl.getFileInfo().getFolderInfo());
                if (fromFolder && dl.isRequestedAutomatic()) {
                    // Abort
                    dl.abort();
                    aborted++;
                }
            }
        } catch (AssertionError e) {
            log().error(e);
            throw e;
        } finally {
            downloadsLock.unlock();
        }
        log().debug("Aborted " + aborted + " downloads on " + folder);
    }

    void downloadAborted(DownloadManager manager) {
        log().warn("Aborted download: " + manager);

        downloadsLock.lock();
        try {
            // If the manager had no sources, it could still be listed as active
            if (dlManagers.containsValue(manager)) {
                removeDownloadManager(manager);
            }
//            Shouldn't try to download again
//            if (!manager.isRequestedAutomatic()) {
//                enquePendingDownload(new Download(this, manager.getFileInfo(),
//                    false));
//            }
        } catch (AssertionError e) {
            log().error(e);
            throw e;
        } finally {
            downloadsLock.unlock();
        }
    }

    private Map<FileInfo, Throwable> debug = new HashMap<FileInfo, Throwable>();
    /**
     * @param manager
     */
    private void removeDownloadManager(DownloadManager manager) {
        // log().debug("Removing: " + manager);
        // Debug.dumpCurrentStackTrace();
        assert manager.isDone() : "Manager to remove is NOT done!";
        if (!dlManagers.remove(manager.getFileInfo(), manager)) {
            // FIXME This can happen if inbetween COMPLETED an abort message is received!
//            throw new Error(debug.get(manager.getFileInfo()));
//            throw new AssertionError("Manager not found: "
//                + Debug.detailedObjectState(manager);
            return;
        } else {
            debug.put(manager.getFileInfo(), new RuntimeException());
        }
    }

    /**
     * Aborts an download. Gets removed compeletly.
     */
    void downloadAborted(Download download) {
        downloadsLock.lock();
        try {
            removeDownload(download);
            pendingDownloads.remove(download);
        } catch (AssertionError e) {
            log().error(e);
            throw e;
        } finally {
            downloadsLock.unlock();
        }

        // Fire event
        fireDownloadAborted(new TransferManagerEvent(this, download));
    }

    /**
     * abort a download, only if the downloading partner is the same
     * 
     * @param fileInfo
     * @param from
     */
    public void abortDownload(FileInfo fileInfo, Member from) {
        Reject.ifNull(fileInfo, "FileInfo is null");
        Reject.ifNull(from, "From is null");
        Download download = getDownload(from, fileInfo);
        if (download != null) {
            if (download.getPartner().equals(from)) {
                download.abort();
            }
        } else {
            for (Download pendingDL : pendingDownloads) {
                if (pendingDL.getFile().equals(fileInfo)
                    && pendingDL.getPartner() != null
                    && pendingDL.getPartner().equals(from))
                {
                    pendingDL.abort();
                }
            }
        }
    }

    /**
     * Clears a completed downloads
     */
    public void clearCompletedDownload(DownloadManager dlMan) {
        if (completedDownloads.remove(dlMan)) {
            for (Download download : dlMan.getSources()) {
                fireCompletedDownloadRemoved(new TransferManagerEvent(this,
                    download));
            }
        }
    }

    /**
     * Clears a completed uploads
     */
    public void clearCompletedUpload(Upload upload) {
        if (completedUploads.remove(upload)) {
            fireCompletedUploadRemoved(new TransferManagerEvent(this, upload));
        }
    }

    /**
     * Called by member, always a new filechunk is received
     * 
     * @param chunk
     * @param from
     */
    public void chunkReceived(FileChunk chunk, Member from) {
        if (chunk == null) {
            throw new NullPointerException("Chunk is null");
        }
        if (from == null) {
            throw new NullPointerException("Member is null");
        }
        FileInfo file = chunk.file;
        if (file == null) {
            throw new NullPointerException(
                "Fileinfo is null from received chunk");
        }

        downloadsLock.lock();
        try {
            Download download = getDownload(from, file);
            if (download != null
                && !download.getFile().isCompletelyIdentical(file))
            {
                setBroken(download, TransferProblem.BROKEN_DOWNLOAD,
                    "Concurrent modification");
                return;
            }
            if (download == null) {
                // Don't abort or warn if the chunk was empty
                // if (chunk.data.length == 0) {
                // return;
                // }
                log().warn(
                    "Received download, which has not been requested, sending abort: "
                        + file + " Chunk: Offset:" + chunk.offset + " Length: "
                        + chunk.data.length);

                // Abort dl
                if (from != null && from.isCompleteyConnected()) {
                    from.sendMessageAsynchron(new AbortDownload(chunk.file),
                        null);
                }
                return;
            }

            // add chunk to DL
            if (download.addChunk(chunk)) {
                // Add to calculator
                downloadCounter.chunkTransferred(chunk);
            }
        } catch (AssertionError e) {
            log().error(e);
            throw e;
        } finally {
            downloadsLock.unlock();
        }
    }

    /**
     * @param fInfo
     * @return true if that file gets downloaded
     */
    public boolean isDownloadingActive(FileInfo fInfo) {
        return dlManagers.containsKey(fInfo);
    }

    /**
     * @param fInfo
     * @return true if the file is enqued as pending download
     */
    public boolean isDownloadingPending(FileInfo fInfo) {
        Reject.ifNull(fInfo, "File is null");
        downloadsLock.lock();
        try {
            for (Download d : pendingDownloads) {
                if (d.getFile().equals(fInfo)) {
                    return true;
                }
            }
            return false;
        } catch (AssertionError e) {
            log().error(e);
            throw e;
        } finally {
            downloadsLock.unlock();
        }
    }

    /**
     * @param fInfo
     * @return true if that file is uploading, or queued
     */
    public boolean isUploading(FileInfo fInfo) {
        for (Upload upload : activeUploads) {
            if (upload.getFile() == fInfo) {
                return true;
            }
        }
        for (Upload upload : queuedUploads) {
            if (upload.getFile() == fInfo) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the upload for the given file to the given member
     * 
     * @param to
     *            the member which the upload transfers to
     * @param fInfo
     *            the file which is transfered
     * @return the Upload or null if there is none
     */
    public Upload getUpload(Member to, FileInfo fInfo) {
        for (Upload u : activeUploads) {
            if (u.getFile().equals(fInfo) && u.getPartner().equals(to)) {
                return u;
            }
        }
        for (Upload u : queuedUploads) {
            if (u.getFile().equals(fInfo) && u.getPartner().equals(to)) {
                return u;
            }
        }
        return null;
    }

    public Download getDownload(Member from, FileInfo fInfo) {
        DownloadManager man = getDownloadManagerFor(fInfo);
        if (man == null) {
            return null;
        }
        Download d = man.getSourceFor(from);
        if (d == null) {
            return null;
        }
        if (d.getPartner().equals(from)) {
            return d;
        }
        return null;
    }

    /**
     * @param fInfo
     * @return the active download for a file
     */
    public DownloadManager getActiveDownload(FileInfo fInfo) {
        return getDownloadManagerFor(fInfo);
    }

    /**
     * @param fileInfo
     * @return the pending download for the file
     */
    public Download getPendingDownload(FileInfo fileInfo) {
        for (Download pendingDl : pendingDownloads) {
            if (fileInfo.equals(pendingDl.getFile())) {
                return pendingDl;
            }
        }
        return null;
    }

    /**
     * @param folder
     *            the folder
     * @return the number of downloads (active & queued) from on a folder.
     */
    public int countNumberOfDownloads(Folder folder) {
        Reject.ifNull(folder, "Folder is null");
        int n = 0;

        for (DownloadManager man : dlManagers.values()) {
            for (Download download : man.getSources()) {
                if (download.getFile().getFolderInfo().equals(folder.getInfo()))
                {
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * @param from
     * @return the number of downloads (active & queued) from a member
     */
    public int getNumberOfDownloadsFrom(Member from) {
        if (from == null) {
            throw new NullPointerException("From is null");
        }
        int n = 0;
        for (DownloadManager man : dlManagers.values()) {
            if (man.getSourceFor(from) != null) {
                n++;
            }
        }
        return n;
    }

    /**
     * @return the internal unodifiable collection of pending downloads.
     */
    public Collection<Download> getPendingDownloads() {
        return Collections.unmodifiableCollection(pendingDownloads);
    }

    /**
     * @return unodifiable instance to the internal active downloads collection.
     */
    public Collection<DownloadManager> getActiveDownloads() {
        return new LinkedList<DownloadManager>(dlManagers.values());
    }

    /**
     * @return the number of completed downloads
     */
    public int countCompletedDownloads() {
        return completedDownloads.size();
    }

    public int countCompletedDownloads(Folder folder) {
        int count = 0;
        for (DownloadManager completedDownload : completedDownloads) {
            Folder f = completedDownload.getFileInfo().getFolder(
                getController().getFolderRepository());
            if (f != null && f.getInfo().equals(folder.getInfo())) {
                count++;
            }
        }
        return count;
    }

    /**
     * @return an unmodifiable collection reffering to the internal completed
     *         downloads list. May change after returned.
     */
    public List<DownloadManager> getCompletedDownloadsCollection() {
        return Collections.unmodifiableList(completedDownloads);
    }

    /**
     * @return the number of all downloads
     */
    public int getActiveDownloadCount() {
        return dlManagers.values().size();
    }

    /**
     * @return the number of total downloads (queued, active and pending)
     */
    public int getTotalDownloadCount() {
        return getActiveDownloadCount() + pendingDownloads.size()
            + completedDownloads.size();
    }

    /**
     * Counts the number of downloads grouped by Node.
     * <p>
     * contains:
     * <p>
     * Member -> Number of active or enqued downloads to that node
     * 
     * @return Member -> Number of active or enqued downloads to that node
     */
    private Map<Member, Integer> countNodesActiveAndQueuedDownloads() {
        Map<Member, Integer> countList = new HashMap<Member, Integer>();

        for (DownloadManager man : dlManagers.values()) {
            for (Download download : man.getSources()) {
                int nDownloadsFrom = 0;
                if (countList.containsKey(download.getPartner())
                    && !download.isCompleted() && !download.isBroken())
                {
                    nDownloadsFrom = countList.get(download.getPartner())
                        .intValue();
                }

                nDownloadsFrom++;
                countList.put(download.getPartner(), Integer
                    .valueOf(nDownloadsFrom));
            }
        }

        return countList;
    }

    /**
     * Answers if we have active downloads from that member (not used)
     * 
     * @param from
     * @return
     */
    /*
     * private boolean hasActiveDownloadsFrom(Member from) { synchronized
     * (downloads) { for (Iterator it = downloads.values().iterator();
     * it.hasNext();) { Download download = (Download) it.next(); if
     * (download.isStarted() && download.getFrom().equals(from)) { return true; } } }
     * return false; }
     */

    /**
     * Loads all pending downloads and enqueus them for re-download
     */
    private void loadDownloads() {
        File transferFile = new File(Controller.getMiscFilesLocation(),
            getController().getConfigName() + ".transfers");
        if (!transferFile.exists()) {
            log().debug(
                "No downloads to restore, " + transferFile.getAbsolutePath()
                    + " does not exists");
            return;
        }
        try {
            FileInputStream fIn = new FileInputStream(transferFile);
            ObjectInputStream oIn = new ObjectInputStream(fIn);
            List<?> storedDownloads = (List<?>) oIn.readObject();
            oIn.close();
            // Reverse to restore in right order
            Collections.reverse(storedDownloads);

            for (Iterator<?> it = storedDownloads.iterator(); it.hasNext();) {
                Download download = (Download) it.next();

                // Initalize after dezerialisation
                download.init(this);
                if (download.isCompleted()) {
                    downloadsLock.lock();
                    try {
                        DownloadManager man = null;
                        for (DownloadManager tmp : completedDownloads) {
                            if (tmp.getFileInfo().equals(download.getFile())) {
                                man = tmp;
                                break;
                            }
                        }
                        if (man == null) {
                            man = downloadManagerFactory.createDownloadManager(getController(),
                                download.getFile(), download.isRequestedAutomatic());
                            completedDownloads.add(man);
                        }
                        download.setDownloadManager(man);
                        man.addSource(download);
                    } catch (AssertionError e) {
                        log().error(e);
                        throw e;
                    } finally {
                        downloadsLock.unlock();
                    }
                } else if (download.isPending()) {
                    enquePendingDownload(download);
                }
            }

            log().debug("Loaded " + storedDownloads.size() + " downloads");
        } catch (IOException e) {
            log().error("Unable to load pending downloads", e);
            if (!transferFile.delete()) {
                log().error("Unable to delete transfer file!");
            }
        } catch (ClassNotFoundException e) {
            log().error("Unable to load pending downloads", e);
            transferFile.delete();
        } catch (ClassCastException e) {
            log().error("Unable to load pending downloads", e);
            transferFile.delete();
        }
    }

    /**
     * Stores all pending downloads to disk
     */
    private void storeDownloads() {
        // Store pending downloads
        try {
            // Collect all download infos
            List<Download> storedDownloads = new ArrayList<Download>(
                pendingDownloads);
            int nPending = getActiveDownloadCount();
            int nCompleted = completedDownloads.size();
            for (DownloadManager man : dlManagers.values()) {
                storedDownloads.add(new Download(this, man.getFileInfo(), man
                    .isRequestedAutomatic()));
            }

            for (DownloadManager man : completedDownloads) {
                storedDownloads.addAll(man.getSources());
            }

            log().verbose(
                "Storing " + storedDownloads.size() + " downloads (" + nPending
                    + " pending, " + nCompleted + " completed)");
            File transferFile = new File(Controller.getMiscFilesLocation(),
                getController().getConfigName() + ".transfers");
            // for testing we should support getConfigName() with subdirs
            if (!transferFile.getParentFile().exists()
                && !new File(transferFile.getParent()).mkdirs())
            {
                log().error("Failed to mkdir misc directory!");
            }
            OutputStream fOut = new BufferedOutputStream(new FileOutputStream(
                transferFile));
            ObjectOutputStream oOut = new ObjectOutputStream(fOut);
            oOut.writeObject(storedDownloads);
            oOut.close();
        } catch (IOException e) {
            log().error("Unable to store pending downloads", e);
        }
    }

    // Worker code ************************************************************

    /**
     * The core maintenance thread of transfermanager.
     */
    private class TransferChecker implements Runnable {
        public void run() {
            long waitTime = getController().getWaitTime() * 2;
            int count = 0;

            while (!Thread.currentThread().isInterrupted()) {
                // Check uploads/downloads every 10 seconds
                if (logVerbose) {
                    log().verbose("Checking uploads/downloads");
                }

                // Check queued uploads
                checkQueuedUploads();

                // Check pending downloads
                checkPendingDownloads();

                // Checking downloads
                checkDownloads();

                // log upload / donwloads
                if (count % 2 == 0) {
                    log().debug(
                        "Transfers: "
                            + getActiveDownloadCount()
                            + " download(s), "
                            + activeUploads.size()
                            + " active upload(s), "
                            + queuedUploads.size()
                            + " in queue, "
                            + Format.NUMBER_FORMATS.format(getUploadCounter()
                                .calculateCurrentKBS()) + " KByte/s");
                }

                count++;

                // wait a bit to next work
                try {
                    if (!transferCheckTriggered) {
                        synchronized (waitTrigger) {
                            waitTrigger.wait(waitTime);
                        }
                    }
                    transferCheckTriggered = false;

                    // Wait another 200ms to avoid spamming via trigger
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    // Break
                    break;
                }
            }
        }
    }

    /**
     * Checks the queued or active downloads and breaks them if nesseary. Also
     * searches for additional sources of download.
     */
    private void checkDownloads() {
        if (logVerbose) {
            log().verbose(
                "Checking " + getActiveDownloadCount() + " download(s)");
        }

        for (DownloadManager man : dlManagers.values()) {
            downloadsLock.lock();
            try {
                downloadNewestVersion(man.getFileInfo(), true);
                for (Download download : man.getSources()) {
                    if (download.isBroken()) {
                        // Set broken
                        setBroken(download, TransferProblem.BROKEN_DOWNLOAD);
                    }
                }
//                assert (!man.getSources().isEmpty() && !man.isDone()) || !dlManagers.containsValue(man)
//                    : "sources: " + man.getSources().size() + " " 
//                    + "isDone: " + man.isDone() + " "
//                    + "isActive: " + dlManagers.containsValue(man) + " "
//                    + " toString: " + man;
            } finally {
                downloadsLock.unlock();
            }
        }
    }

    /**
     * Checks the queued uploads and start / breaks them if nessesary.
     */
    private void checkQueuedUploads() {
        int uploadsStarted = 0;
        int uploadsBroken = 0;

        if (logVerbose) {
            log().verbose(
                "Checking " + queuedUploads.size() + " queued uploads");
        }

        for (Upload upload : queuedUploads) {
            if (upload.isBroken()) {
                // Broken
                setBroken(upload, TransferProblem.BROKEN_UPLOAD);
                uploadsBroken++;
            } else if (hasFreeUploadSlots() || upload.getPartner().isOnLAN()) {
                boolean alreadyUploadingTo;
                // The total size planned+current uploading to that node.
                long totalPlannedSizeUploadingTo = uploadingToSize(upload
                    .getPartner());
                if (totalPlannedSizeUploadingTo == -1) {
                    alreadyUploadingTo = false;
                    totalPlannedSizeUploadingTo = 0;
                } else {
                    alreadyUploadingTo = true;
                }
                totalPlannedSizeUploadingTo += upload.getFile().getSize();

                if (!alreadyUploadingTo
                    || totalPlannedSizeUploadingTo <= 500 * 1024)
                {
                    // if (!alreadyUploadingTo) {
                    if (alreadyUploadingTo && logDebug) {
                        log()
                            .debug(
                                "Starting another upload to "
                                    + upload.getPartner().getNick()
                                    + ". Total size to upload to: "
                                    + Format
                                        .formatBytesShort(totalPlannedSizeUploadingTo));

                    }
                    // start the upload if we have free slots
                    // and not uploading to member currently
                    // or user is on local network

                    // TODO should check if this file is not sended (or is
                    // being send) to other user in the last minute or so to
                    // allow for disitributtion of that file by user that
                    // just received that file from us

                    // Enqueue upload to friends and lan members first

                    log().verbose("Starting upload: " + upload);
                    upload.start();
                    uploadsStarted++;
                }
            }
        }

        if (logVerbose) {
            log().verbose(
                "Started " + uploadsStarted + " upload(s), " + uploadsBroken
                    + " broken upload(s)");
        }
    }

    /**
     * Checks the pendings download. Restores them if a source is found
     */
    private void checkPendingDownloads() {
        if (logVerbose) {
            log().verbose(
                "Checking " + pendingDownloads.size() + " pending downloads");
        }

        // Checking pending downloads
        for (Download dl : pendingDownloads) {
            FileInfo fInfo = dl.getFile();
            downloadsLock.lock();
            try {
                boolean notDownloading = getDownloadManagerFor(fInfo) == null;
                if (notDownloading
                    && getController().getFolderRepository().hasJoinedFolder(
                        fInfo.getFolderInfo()))
                {
                    // MultiSourceDownload source = downloadNewestVersion(fInfo,
                    // download
                    // .isRequestedAutomatic());
                    DownloadManager source = downloadNewestVersion(fInfo, dl
                        .isRequestedAutomatic());
                    if (source != null) {
                        log().debug(
                            "Pending download restored: " + fInfo + " from "
                                + source);
                        pendingDownloads.remove(dl);
                    }
                } else if (dl.getDownloadManager() != null
                    && !dl.getDownloadManager().isBroken())
                {
                    // Not joined folder, break pending dl
                    log().warn("Pending download removed: " + fInfo);
                    pendingDownloads.remove(dl);
                }
            } catch (AssertionError e) {
                log().error(e);
                throw e;
            } finally {
                downloadsLock.unlock();
            }
        }
    }

    // Helper code ************************************************************

    /**
     * logs a up- or download with speed and time
     * 
     * @param download
     * @param took
     * @param fInfo
     * @param member
     */
    public void logTransfer(boolean download, long took, FileInfo fInfo,
        Member member)
    {

        String memberInfo = "";
        if (member != null) {
            memberInfo = ((download) ? " from " : " to ") + "'"
                + member.getNick() + "'";
        }

        String cpsStr = "-";

        // printout rate only if dl last longer than 0,5 s
        if (took > 1000) {
            double cps = fInfo.getSize();
            cps = cps / 1024;
            cps = cps / took;
            cps = cps * 1000;
            synchronized (CPS_FORMAT) {
                cpsStr = CPS_FORMAT.format(cps);
            }
        }

        synchronized (Format.NUMBER_FORMATS) {
            log().info(
                (download ? "Download" : "Upload") + " completed: "
                    + Format.NUMBER_FORMATS.format(fInfo.getSize())
                    + " bytes in " + (took / 1000) + "s (" + cpsStr
                    + " KByte/s): " + fInfo + memberInfo);
        }
    }

    // Event/Listening code ***************************************************

    public void addListener(TransferManagerListener listener) {
        ListenerSupportFactory.addListener(listenerSupport, listener);
    }

    public void removeListener(TransferManagerListener listener) {
        ListenerSupportFactory.removeListener(listenerSupport, listener);
    }

    private void fireUploadStarted(TransferManagerEvent event) {
        listenerSupport.uploadStarted(event);
    }

    private void fireUploadAborted(TransferManagerEvent event) {
        listenerSupport.uploadAborted(event);
    }

    private void fireUploadBroken(TransferManagerEvent event) {
        listenerSupport.uploadBroken(event);
    }

    private void fireUploadCompleted(TransferManagerEvent event) {
        listenerSupport.uploadCompleted(event);
    }

    private void fireUploadRequested(TransferManagerEvent event) {
        listenerSupport.uploadRequested(event);
    }

    private void fireDownloadAborted(TransferManagerEvent event) {
        listenerSupport.downloadAborted(event);
    }

    private void fireDownloadBroken(TransferManagerEvent event) {
        listenerSupport.downloadBroken(event);
    }

    private void fireDownloadCompleted(TransferManagerEvent event) {
        listenerSupport.downloadCompleted(event);
    }

    private void fireDownloadQueued(TransferManagerEvent event) {
        listenerSupport.downloadQueued(event);
    }

    private void fireDownloadRequested(TransferManagerEvent event) {
        listenerSupport.downloadRequested(event);
    }

    private void fireDownloadStarted(TransferManagerEvent event) {
        listenerSupport.downloadStarted(event);
    }

    private void fireCompletedDownloadRemoved(TransferManagerEvent event) {
        listenerSupport.completedDownloadRemoved(event);
    }

    private void fireCompletedUploadRemoved(TransferManagerEvent event) {
        listenerSupport.completedUploadRemoved(event);
    }

    private void firePendingDownloadEnqueud(TransferManagerEvent event) {
        listenerSupport.pendingDownloadEnqueud(event);
    }

}
