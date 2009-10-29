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
 * $Id$
 */
package de.dal33t.powerfolder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.FolderRepository;
import de.dal33t.powerfolder.event.AskForFriendshipEvent;
import de.dal33t.powerfolder.light.AccountInfo;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.light.MemberInfo;
import de.dal33t.powerfolder.message.AbortDownload;
import de.dal33t.powerfolder.message.AbortUpload;
import de.dal33t.powerfolder.message.AddFriendNotification;
import de.dal33t.powerfolder.message.ConfigurationLoadRequest;
import de.dal33t.powerfolder.message.DownloadQueued;
import de.dal33t.powerfolder.message.FileChunk;
import de.dal33t.powerfolder.message.FileHistoryReply;
import de.dal33t.powerfolder.message.FileHistoryRequest;
import de.dal33t.powerfolder.message.FileList;
import de.dal33t.powerfolder.message.FolderFilesChanged;
import de.dal33t.powerfolder.message.FolderList;
import de.dal33t.powerfolder.message.FolderRelatedMessage;
import de.dal33t.powerfolder.message.HandshakeCompleted;
import de.dal33t.powerfolder.message.Identity;
import de.dal33t.powerfolder.message.IdentityReply;
import de.dal33t.powerfolder.message.Invitation;
import de.dal33t.powerfolder.message.KnownNodes;
import de.dal33t.powerfolder.message.Message;
import de.dal33t.powerfolder.message.MessageListener;
import de.dal33t.powerfolder.message.NodeInformation;
import de.dal33t.powerfolder.message.Notification;
import de.dal33t.powerfolder.message.Ping;
import de.dal33t.powerfolder.message.Pong;
import de.dal33t.powerfolder.message.Problem;
import de.dal33t.powerfolder.message.RelayedMessage;
import de.dal33t.powerfolder.message.ReplyFilePartsRecord;
import de.dal33t.powerfolder.message.RequestDownload;
import de.dal33t.powerfolder.message.RequestFilePartsRecord;
import de.dal33t.powerfolder.message.RequestNodeInformation;
import de.dal33t.powerfolder.message.RequestNodeList;
import de.dal33t.powerfolder.message.RequestPart;
import de.dal33t.powerfolder.message.ScanCommand;
import de.dal33t.powerfolder.message.SearchNodeRequest;
import de.dal33t.powerfolder.message.SettingsChange;
import de.dal33t.powerfolder.message.SingleFileAccept;
import de.dal33t.powerfolder.message.SingleFileOffer;
import de.dal33t.powerfolder.message.StartUpload;
import de.dal33t.powerfolder.message.StopUpload;
import de.dal33t.powerfolder.message.TransferStatus;
import de.dal33t.powerfolder.message.UDTMessage;
import de.dal33t.powerfolder.message.clientserver.AccountStateChanged;
import de.dal33t.powerfolder.net.ConnectionException;
import de.dal33t.powerfolder.net.ConnectionHandler;
import de.dal33t.powerfolder.net.InvalidIdentityException;
import de.dal33t.powerfolder.net.PlainSocketConnectionHandler;
import de.dal33t.powerfolder.transfer.Download;
import de.dal33t.powerfolder.transfer.TransferManager;
import de.dal33t.powerfolder.transfer.Upload;
import de.dal33t.powerfolder.util.ConfigurationLoader;
import de.dal33t.powerfolder.util.Debug;
import de.dal33t.powerfolder.util.MessageListenerSupport;
import de.dal33t.powerfolder.util.Profiling;
import de.dal33t.powerfolder.util.ProfilingEntry;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.StringUtils;
import de.dal33t.powerfolder.util.Util;
import de.dal33t.powerfolder.util.Waiter;
import de.dal33t.powerfolder.util.logging.LoggingManager;

/**
 * A full quailfied member, can have a connection to interact with remote
 * member/fried/peer.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.115 $
 */
public class Member extends PFComponent implements Comparable<Member> {

    /** Listener support for incoming messages */
    private MessageListenerSupport messageListenerSupport;

    /** The current connection handler */
    private ConnectionHandler peer;

    /**
     * If this node has completely handshaked. TODO: Move this into
     * connectionHandler ?
     */
    private volatile boolean handshaked;

    /** The number of connection retries to the most recent known remote address */
    private volatile int connectionRetries;

    /** The total number of reconnection tries at this moment */
    private final AtomicInteger currentConnectTries = new AtomicInteger(0);

    /** his member information */
    private final MemberInfo info;

    /** The last time, the node was seen on the network */
    private Date lastNetworkConnectTime;

    /** Lock when peer is going to be initalized */
    private final Object peerInitalizeLock = new Object();

    /** Folderlist waiter */
    private final Object folderListWaiter = new Object();

    /** Handshake completed waiter */
    private final Object handshakeCompletedWaiter = new Object();

    /**
     * Lock to ensure that only one thread executes the folder membership
     * synchronization.
     */
    private final ReentrantLock folderJoinLock = new ReentrantLock();

    /**
     * The last message indicating that the handshake was completed
     */
    private HandshakeCompleted lastHandshakeCompleted;

    /** Last folder memberships */
    private FolderList lastFolderList;

    /**
     * The number of expected deltas to receive to have the filelist completed
     * on that folder. Might contain negativ values! means we received deltas
     * after the inital filelist.
     */
    private Map<FolderInfo, Integer> expectedListMessages = Util
        .createConcurrentHashMap();

    /** Last trasferstatus */
    private TransferStatus lastTransferStatus;

    /**
     * the last problem
     */
    private Problem lastProblem;

    /** maybe we cannot connect, but member might be online */
    private boolean isConnectedToNetwork;

    /** Flag if we received a wrong identity from remote side */
    private boolean receivedWrongRemoteIdentity;

    /** If already asked for friendship */
    private boolean askedForFriendship;

    /** If the remote node is a server. */
    private boolean server;

    /**
     * Constructs a member using parameters from another member. nick, id ,
     * connect address.
     * <p>
     * Attention:Does not takes friend status from memberinfo !! you have to
     * manually
     * 
     * @param controller
     *            Reference to the Controller
     * @param mInfo
     *            memberInfo to clone
     */
    public Member(Controller controller, MemberInfo mInfo) {
        super(controller);
        this.info = mInfo;
    }

    /**
     * @param searchString
     * @return if this member matches the search string or if it equals the IP
     *         nick contains the search String
     * @see MemberInfo#matches(String)
     */
    public boolean matches(String searchString) {
        return matches(searchString, false);
    }

    /**
     * @param searchString
     * @param matchAccount
     *            true if the Account username should be also considerd for
     *            matching.
     * @return if this member matches the search string or if it equals the IP
     *         nick contains the search String
     * @see MemberInfo#matches(String)
     */
    public boolean matches(String searchString, boolean matchAccount) {
        if (info.matches(searchString)) {
            return true;
        }
        if (!matchAccount) {
            return false;
        }
        AccountInfo aInfo = getAccountInfo();
        if (aInfo == null) {
            return false;
        }
        return aInfo.getUsername().toLowerCase().indexOf(searchString) >= 0;
    }

    public String getHostName() {
        if (getReconnectAddress() == null) {
            return null;
        }
        return getReconnectAddress().getHostName();
    }

    public String getIP() {
        // if (ip == null) {
        if (getReconnectAddress() == null
            || getReconnectAddress().getAddress() == null)
        {
            return null;
        }
        return getReconnectAddress().getAddress().getHostAddress();
        // }
        // return ip;
    }

    public int getPort() {
        if (getReconnectAddress() == null
            || getReconnectAddress().getAddress() == null)
        {
            return 0;
        }
        return getReconnectAddress().getPort();
    }

    /**
     * @return true if the connection to this node is secure.
     */
    public boolean isSecure() {
        return peer != null && peer.isEncrypted();
    }

    private Boolean mySelf;

    /**
     * Answers if this is myself
     * 
     * @return true if this object references to "myself" else false
     */
    public boolean isMySelf() {
        if (mySelf != null) {
            // Use cache
            return mySelf;
        }
        mySelf = equals(getController().getMySelf());
        return mySelf;
    }

    /**
     * #1646
     * 
     * @return true if this computer is one of mine computers (same login).
     */
    public boolean isMyComputer() {
        AccountInfo aInfo = getAccountInfo();
        if (aInfo == null) {
            return false;
        }
        return aInfo.equals(getController().getOSClient().getAccountInfo());
    }

    /**
     * Answers if this member is a friend, also true if isMySelf()
     * 
     * @return true if this user is a friend or myself.
     */
    public boolean isFriend() {
        return getController().getNodeManager().isFriend(this);
    }

    /**
     * Sets friend status of this member
     * 
     * @param newFriend
     *            The new friend status.
     * @param personalMessage
     *            the personal message to send to the remote user.
     */
    public void setFriend(boolean newFriend, String personalMessage) {
        boolean stateChanged = isFriend() ^ newFriend;
        // Inform node manager
        if (stateChanged) {
            getController().getNodeManager().friendStateChanged(this,
                newFriend, personalMessage);
        }
    }

    /**
     * Marks the node for immediate connection
     */
    public void markForImmediateConnect() {
        getController().getReconnectManager().markNodeForImmediateReconnection(
            this);
    }

    /**
     * TODO Remove after major distribution of 4.0
     * 
     * @return true if this client is a pre 4.0 client.
     */
    public boolean isPre4Client() {
        ConnectionHandler conHan = peer;
        if (conHan == null) {
            return false;
        }
        Identity id = conHan.getIdentity();
        if (id != null) {
            // Not "4.0.0", because version "4.0.0 - 1.0.1" is before "4.0.0"
            return Util.compareVersions("3.9.9", id.getProgramVersion());
        } else {
            logSevere("Unable to determin if client is pre 4.0. Identity: "
                + id + ". Peer: " + conHan, new RuntimeException("here"));
        }
        return false;
    }

    /**
     * Answers if this node is interesting for us, that is defined as friends
     * users on LAN and has joined one of our folders. Or if its a supernode of
     * we are a supernode and there are still open connections slots.
     * 
     * @return true if this node is interesting for us
     */
    public boolean isInteresting() {
        // logFine("isOnLAN(): " + isOnLAN());
        // logFine("getController().isLanOnly():" +
        // getController().isLanOnly());

        boolean isRelay = getController().getIOProvider()
            .getRelayedConnectionManager().isRelay(getInfo());
        boolean isServer = isServer()
            || getController().getOSClient().isServer(this);
        boolean isRelayOrServer = isServer || isRelay;

        if (getController().getNetworkingMode().equals(
            NetworkingMode.SERVERONLYMODE)
            && !isRelayOrServer)
        {
            return false;
        }

        if (getController().isLanOnly() && !isOnLAN()) {
            return false;
        }

        // FIXME Does not work with temporary server nodes.
        if (isServer || isRelay) {
            // Always interesting is the server!
            // Always interesting a relay is!
            return true;
        }

        Identity id = getIdentity();
        if (id != null) {
            // log().debug(
            // "Got ID: " + id + ". pending msgs? " + id.isPendingMessages());
            if (Util.compareVersions("2.0.0", id.getProgramVersion())) {
                logWarning("Rejecting connection to old program client: " + id
                    + " v" + id.getProgramVersion());
                return false;
            }
            // FIX for #1124. Might produce problems!
            if (id.isPendingMessages()) {
                return true;
            }
        }

        // logFine("isFriend(): " + isFriend());
        // logFine("hasJoinedAnyFolder(): " + hasJoinedAnyFolder());

        if (isFriend() || isOnLAN() || hasJoinedAnyFolder()) {
            return true;
        }

        // Still capable of new connections?
        boolean conSlotAvail = !getController().getNodeManager()
            .maxConnectionsReached();
        if (conSlotAvail
            && (getController().getMySelf().isSupernode() || getController()
                .getMySelf().isServer()))
        {
            return true;
        }

        // Try to hold connection to supernode if max connections not reached
        // yet.
        if (conSlotAvail && isSupernode()) {
            return getController().getNodeManager().countConnectedSupernodes() < Constants.N_SUPERNODES_TO_CONNECT;
        }

        return false;
    }

    /**
     * @return true if this node is currently reconnecting (outbound) or
     *         connecting inbound
     */
    public boolean isConnecting() {
        return currentConnectTries.get() > 0;
    }

    /**
     * Marks the node as connecting (inbound or outbound).
     * <P>
     * Make sure to unmark the connecting status
     * 
     * @return the number of currently running connection tries. Should be 1
     */
    public int markConnecting() {
        return currentConnectTries.incrementAndGet();
    }

    /**
     * @return the current connection tries. 0 if not longer connecting.
     */
    public int unmarkConnecting() {
        return currentConnectTries.decrementAndGet();
    }

    /**
     * Answers if this member has a connected peer (a open socket). To check if
     * a node is completey connected & handshaked see
     * <code>isCompletelyConnected</code>
     * 
     * @see #isCompletelyConnected()
     * @return true if connected
     */
    public boolean isConnected() {
        try {
            return peer != null && peer.isConnected();
        } catch (NullPointerException e) {
            return false;
        }
    }

    /**
     * Answers if this node is completely connected & handshaked
     * 
     * @return true if connected & handshaked
     */
    public boolean isCompletelyConnected() {
        return handshaked && isConnected();
    }

    /**
     * Convinience method
     * 
     * @return true if the node is a supernode
     */
    public boolean isSupernode() {
        return info.isSupernode;
    }

    /**
     * Answers if this member is on the local area network.
     * 
     * @return true if this member is on LAN.
     */
    public boolean isOnLAN() {
        if (peer != null) {
            return peer.isOnLAN();
        }
        if (info.getConnectAddress() == null) {
            return false;
        }
        InetAddress adr = info.getConnectAddress().getAddress();
        if (adr == null) {
            return false;
        }
        return getController().getNodeManager().isOnLANorConfiguredOnLAN(adr);
    }

    /**
     * To set the lan status of the member for external source
     * 
     * @param onlan
     *            new LAN status
     */
    public void setOnLAN(boolean onlan) {
        if (peer != null) {
            peer.setOnLAN(onlan);
        }
    }

    /**
     * Answers if we received a wrong identity on reconnect
     * 
     * @return true if we received a wrong identity on reconnect
     */
    public boolean receivedWrongIdentity() {
        return receivedWrongRemoteIdentity;
    }

    /**
     * removes the peer handler, and shuts down connection
     */
    private void shutdownPeer() {
        ConnectionHandler thisPeer = peer;
        if (thisPeer != null) {
            thisPeer.shutdown();
            synchronized (peerInitalizeLock) {
                peer = null;
            }
        }
    }

    /**
     * @return the peer of this member.
     */
    public ConnectionHandler getPeer() {
        return peer;
    }

    /**
     * Sets the new connection handler for this member
     * 
     * @param newPeer
     *            The peer / connection handler to set
     * @throws InvalidIdentityException
     *             if peer identity doesn't match this member.
     * @return the result of the connection attempt
     */
    public ConnectResult setPeer(ConnectionHandler newPeer)
        throws InvalidIdentityException
    {
        Reject.ifNull(newPeer, "Illegal call of setPeer(null)");

        if (!newPeer.isConnected()) {
            logWarning("Peer disconnected while initializing connection: "
                + newPeer);
            return ConnectResult
                .failure("Peer disconnected while initializing connection");
        }

        if (isFiner()) {
            logFiner("Setting peer to " + newPeer);
        }

        Identity identity = newPeer.getIdentity();
        MemberInfo remoteMemberInfo = identity != null ? identity
            .getMemberInfo() : null;

        // check if identity is valid and matches the this member
        if (identity == null || !identity.isValid()
            || !remoteMemberInfo.matches(this))
        {
            // Wrong identity from remote side ? set our flag
            receivedWrongRemoteIdentity = remoteMemberInfo != null
                && !remoteMemberInfo.matches(this);

            String identityId = identity != null
                ? identity.getMemberInfo().id
                : "n/a";

            // tell remote client
            try {
                newPeer.sendMessage(IdentityReply.reject("Invalid identity: "
                    + identityId + ", expeced " + info));
            } catch (ConnectionException e) {
                logFiner("Unable to send identity reject", e);
            } finally {
                newPeer.shutdown();
            }
            throw new InvalidIdentityException(this
                + " Remote peer has wrong identity. remote ID: " + identityId
                + ", expected ID: " + getId(), newPeer);
        }

        // #1373
        if (!remoteMemberInfo.isOnSameNetwork(getController())) {
            if (isFine()) {
                logFine("Closing connection to node with diffrent network ID. Our netID: "
                    + getController().getNodeManager().getNetworkId()
                    + ", remote netID: "
                    + remoteMemberInfo.networkId
                    + " on "
                    + remoteMemberInfo);
            }
            newPeer.shutdown();
            throw new InvalidIdentityException(
                "Closing connection to node with diffrent network ID. Our netID: "
                    + getController().getNodeManager().getNetworkId()
                    + ", remote netID: " + remoteMemberInfo.networkId + " on "
                    + remoteMemberInfo, newPeer);
        }

        // Complete low-level handshake
        // FIXME: Problematic situation: Now we probably accept the new peer.
        // Messages received from this new peer can be delivered to the
        // Member which not get the correct peer since "peer" field is set
        // later...
        boolean accepted = newPeer.acceptIdentity(this);

        if (!accepted) {
            // Shutdown this member
            newPeer.shutdown();
            logFiner("Remote side did not accept our identity: " + newPeer);
            return ConnectResult
                .failure("Remote side did not accept our identity");
        }

        synchronized (peerInitalizeLock) {
            ConnectionHandler oldPeer = peer;
            // Set the new peer
            peer = newPeer;

            // ok, we accepted, kill old peer and shutdown.
            if (oldPeer != null) {
                oldPeer.shutdown();
            }
        }

        // Update infos!
        if (newPeer.getRemoteListenerPort() > 0) {
            // get the data from remote peer
            // connect address is his currently connected ip + his
            // listner port if not supernode
            if (newPeer.isOnLAN()) {
                // Supernode state no nessesary on lan
                // Take socket ip as reconnect address
                info.isSupernode = false;
                info.setConnectAddress(new InetSocketAddress(newPeer
                    .getRemoteAddress().getAddress(), newPeer
                    .getRemoteListenerPort()));
            } else if (identity.getMemberInfo().isSupernode) {
                // Remote peer is supernode, take his info, he knows
                // about himself (=reconnect hostname)
                info.isSupernode = true;
                info.setConnectAddress(identity.getMemberInfo()
                    .getConnectAddress());
            } else {
                // No supernode. take socket ip as reconnect address.
                info.isSupernode = false;
                info.setConnectAddress(new InetSocketAddress(newPeer
                    .getRemoteAddress().getAddress(), newPeer
                    .getRemoteListenerPort()));
            }
        } else if (!identity.isTunneled()) {
            // Remote peer has no listener running
            info.setConnectAddress(null);
            // Don't change the connection address on a tunneled connection.
        }

        info.id = identity.getMemberInfo().id;
        info.nick = identity.getMemberInfo().nick;
        // Reset the last connect time
        info.lastConnectTime = new Date();

        return completeHandshake();
    }

    /**
     * Calls which can only be executed with connection
     * 
     * @throws ConnectionException
     *             if not connected
     */
    private void checkPeer() throws ConnectionException {
        if (!isConnected()) {
            shutdownPeer();
            throw new ConnectionException("Not connected").with(this);
        }
    }

    /**
     * Tries to reconnect peer
     * 
     * @return the result of the connection attempt.
     * @throws InvalidIdentityException
     */
    public ConnectResult reconnect() throws InvalidIdentityException {
        return reconnect(true);
    }

    /**
     * Tries to reconnect peer
     * 
     * @param markConnecting
     *            true if this member should be marked as connecting. sometimes
     *            this has already been done by calling code.
     * @return the result of the connection attempt.
     * @throws InvalidIdentityException
     */
    public ConnectResult reconnect(boolean markConnecting)
        throws InvalidIdentityException
    {
        // do not reconnect if controller is not running
        if (!getController().isStarted()) {
            return ConnectResult.failure("Controller is not started");
        }
        if (isConnected()) {
            return ConnectResult.success();
        }
        // #1334
        // if (info.getConnectAddress() == null) {
        // return false;
        // }
        if (isFine()) {
            logFine("Reconnecting (tried " + connectionRetries + " time(s) to "
                + this + ")");
        }

        connectionRetries++;
        ConnectResult connectResult;
        ConnectionHandler handler = null;
        try {
            // #1334
            // if (info.getConnectAddress().getPort() <= 0) {
            // logWarning(this + " has illegal connect port "
            // + info.getConnectAddress().getPort());
            // return false;
            // }

            // Set reconnecting state
            if (markConnecting) {
                markConnecting();
            }

            // Re-resolve connect address
            String theHostname = getHostName(); // cached hostname
            if (isFiner()) {
                logFiner("Reconnect hostname to " + getNick() + " is: "
                    + theHostname);
            }
            if (!StringUtils.isBlank(theHostname)) {
                info.setConnectAddress(new InetSocketAddress(theHostname, info
                    .getConnectAddress().getPort()));
            }

            // Another check: do not reconnect if controller is not running
            if (!getController().isStarted()) {
                return ConnectResult.failure("Controller is not started");
            }

            // Try to establish a low-level connection.
            handler = getController().getIOProvider()
                .getConnectionHandlerFactory().tryToConnect(this.getInfo());
            connectResult = setPeer(handler);
        } catch (InvalidIdentityException e) {
            logFiner(e);
            // Shut down reconnect handler
            if (handler != null) {
                handler.shutdown();
            }
            throw e;
        } catch (ConnectionException e) {
            logFine(e.getMessage());
            logFiner(e);
            // Shut down reconnect handler
            if (handler != null) {
                handler.shutdown();
            }
            connectResult = ConnectResult.failure(e.getMessage());
        } finally {
            if (markConnecting) {
                // Was marked, unmark it.
                unmarkConnecting();
            }
        }

        if (connectResult.isSuccess()) {
            setConnectedToNetwork(true);
            connectionRetries = 0;
        } else {
            if (connectionRetries >= 15 && isConnectedToNetwork) {
                logWarning("Unable to connect directly");
                // FIXME: Find a better ways
                setConnectedToNetwork(false);
            }
        }

        return connectResult;
    }

    /**
     * Completes the handshake between nodes. Exchanges the relevant information
     * 
     * @return the result of the connection attempt.
     */
    private ConnectResult completeHandshake() {
        if (!isConnected()) {
            return ConnectResult.failure("Not connected");
        }
        if (peer == null) {
            return ConnectResult.failure("Peer is not set");
        }
        boolean wasHandshaked = handshaked;
        boolean thisHandshakeCompleted = true;
        Identity identity = peer.getIdentity();

        synchronized (peerInitalizeLock) {
            if (!isConnected() || identity == null) {
                logFine("Disconnected while completing handshake");
                return ConnectResult
                    .failure("Disconnected while completing handshake");
            }
            // Send node informations now
            // Send joined folders to synchronize
            FolderList folderList = new FolderList(getController()
                .getFolderRepository().getJoinedFolderInfos(), peer
                .getRemoteMagicId());
            peer.sendMessagesAsynchron(folderList);
        }

        // My messages sent, now wait for his folder list.
        boolean receivedFolderList = waitForFolderList();
        synchronized (peerInitalizeLock) {
            if (!isConnected()) {
                logFine("Disconnected while completing handshake");
                return ConnectResult
                    .failure("Disconnected while completing handshake");
            }
            if (!receivedFolderList) {
                if (isConnected()) {
                    logFine("Did not receive a folder list after 60s, disconnecting");
                    return ConnectResult
                        .failure("Did not receive a folder list after 60s, disconnecting (1)");
                }
                shutdown();
                return ConnectResult
                    .failure("Did not receive a folder list after 60s, disconnecting (2)");
            }
            if (!isConnected()) {
                logFine("Disconnected while waiting for folder list");
                return ConnectResult
                    .failure("Disconnected while waiting for folder list");
            }
        }

        // Create request for nodelist.
        RequestNodeList request = getController().getNodeManager()
            .createDefaultNodeListRequestMessage();

        synchronized (peerInitalizeLock) {
            if (!isConnected()) {
                logFine("Disconnected while completing handshake");
                return ConnectResult
                    .failure("Disconnected while completing handshake");
            }

            if (!isInteresting()) {
                logFine("Rejected, Node not interesting");
                // Tell remote side
                try {
                    peer.sendMessage(new Problem("You are boring", true,
                        Problem.DO_NOT_LONGER_CONNECT));
                } catch (ConnectionException e) {
                    // Ignore
                }
                thisHandshakeCompleted = false;
            } else {
                // Send request for nodelist.
                peer.sendMessagesAsynchron(request);

                // Send our transfer status
                peer.sendMessagesAsynchron(getController().getTransferManager()
                    .getStatus());
            }
        }

        boolean acceptByConnectionHandler = peer != null
            && peer.acceptHandshake();
        // Handshaked ?
        thisHandshakeCompleted = thisHandshakeCompleted && isConnected()
            && acceptByConnectionHandler;

        if (!thisHandshakeCompleted) {
            String message = "not handshaked: connected? " + isConnected()
                + ", acceptByCH? " + acceptByConnectionHandler
                + ", interesting? " + isInteresting() + ", peer " + peer;
            if (isFiner()) {
                logFiner(message);
            }
            shutdown();
            return ConnectResult.failure(message);
        }

        List<Folder> foldersJoined = getJoinedFolders();
        List<Folder> foldersCommon = getFoldersInCommon();
        if (isFine() && !foldersJoined.isEmpty()) {
            logFine("Joined " + foldersJoined.size() + " folders: "
                + foldersJoined);
        } else if (isFiner()) {
            logFiner("Joined " + foldersJoined.size() + " folders: "
                + foldersJoined);
        }

        for (Folder folder : foldersJoined) {
            // FIX for #924
            folder.waitForScan();
            // Send filelist of joined folders
            sendMessagesAsynchron(FileList.createFileListMessages(folder,
                !isPre4Client()));
            foldersCommon.remove(folder);
        }
        if (!foldersCommon.isEmpty()) {
            logWarning("NOT joined: " + foldersCommon);
            logWarning("Joined: " + foldersJoined);
            for (Folder folder : foldersCommon) {
                if (isPre4Client()) {
                    sendMessagesAsynchron(FileList
                        .createNullListForPre4Client(folder.getInfo()));
                } else {
                    sendMessagesAsynchron(FileList.createNullList(folder
                        .getInfo()));
                }
            }
        }

        boolean ok = waitForFileLists(foldersJoined);
        if (!ok) {
            String reason = "Disconnecting. Did not receive the full filelists for "
                + foldersJoined.size() + " folders";
            logWarning(reason);
            if (isFine()) {
                for (Folder folder : foldersJoined) {
                    logFine("Got filelist for " + folder.getName() + " ? "
                        + hasCompleteFileListFor(folder.getInfo()));
                }
            }
            shutdown();
            return ConnectResult.failure(reason);
        }
        if (isFiner()) {
            logFiner("Got complete filelists");
        }

        // Wait for acknowledgement from remote side
        if (identity.isAcknowledgesHandshakeCompletion()) {
            sendMessageAsynchron(new HandshakeCompleted(), null);
            long start = System.currentTimeMillis();
            if (!waitForHandshakeCompletion()) {
                long took = System.currentTimeMillis() - start;
                String message = null;
                if (peer == null || !peer.isConnected()) {
                    if (lastProblem == null) {
                        message = "Peer disconnected while waiting for handshake acknownledge (or problem)";
                    }
                } else {
                    if (lastProblem == null) {
                        message = "Did not receive a handshake not acknownledged (or problem) by remote side after "
                            + (int) (took / 1000) + 's';
                    }
                }
                shutdown();
                if (message != null && isWarning()) {
                    logWarning(message);
                }
                return ConnectResult.failure(message);
            } else if (isFiner()) {
                logFiner("Got handshake completion!!");
            }
        } else if (peer != null && peer.isConnected()) {
            // Handshaked
            handshaked = true;
        } else {
            shutdown();
            return ConnectResult.failure("Unknown reason");
        }

        // Reset things
        connectionRetries = 0;

        if (wasHandshaked != handshaked) {
            // Inform nodemanger about it
            getController().getNodeManager().connectStateChanged(this);

            // Inform security manager to update account state.
            getController().getSecurityManager().nodeAccountStateChanged(this);
        }

        if (isInfo()) {
            logInfo("Connected ("
                + getController().getNodeManager().countConnectedNodes()
                + " total)");
        }

        // Request files
        for (Folder folder : foldersJoined) {
            // Trigger filerequesting. we may want re-request files on a
            // folder he joined.
            getController().getFolderRepository().getFileRequestor()
                .triggerFileRequesting(folder.getInfo());
            if (folder.getSyncProfile().isSyncDeletion()) {
                folder.triggerSyncRemoteDeletedFiles(Collections
                    .singleton(this), false);
            }
        }

        if (getController().isDebugReports()) {
            // Running with debugReports enabled (which incorporates verbose
            // mode)
            // then directly request node information.
            sendMessageAsynchron(new RequestNodeInformation(), null);
        }

        if (handshaked) {
            return ConnectResult.success();
        } else {
            return ConnectResult.failure("Not handshaked");
        }
    }

    /**
     * Waits for the filelists on those folders. After a certain amount of time
     * it runs on a timeout if no filelists were received. Waits max 2 minutes.
     * 
     * @param folders
     * @return true if the filelists of those folders received successfully.
     */
    private boolean waitForFileLists(List<Folder> folders) {
        if (isFiner()) {
            logFiner("Waiting for complete fileslists...");
        }
        // 120 minutes. Should never occur.
        Waiter waiter = new Waiter(1000L * 60 * 120);
        boolean fileListsCompleted = false;
        Date lastMessageReceived = null;
        while (!waiter.isTimeout() && isConnected()) {
            fileListsCompleted = true;
            for (Folder folder : folders) {
                if (!hasCompleteFileListFor(folder.getInfo())) {
                    fileListsCompleted = false;
                    break;
                }
            }
            if (fileListsCompleted) {
                break;
            }

            lastMessageReceived = peer != null ? peer
                .getLastKeepaliveMessageTime() : null;
            if (lastMessageReceived == null) {
                logSevere("Unable to check last received message date. got null while waiting for filelist");
                return false;
            }
            boolean noChangeReceivedSineOneMinute = System.currentTimeMillis()
                - lastMessageReceived.getTime() > 1000L * 60;
            if (noChangeReceivedSineOneMinute) {
                logWarning("No message received since 1 minute while waiting for filelist");
                return false;
            }

            try {
                waiter.waitABit();
            } catch (Exception e) {
                return false;
            }
        }
        if (waiter.isTimeout()) {
            logSevere("Got timeout ("
                + (waiter.getTimoutTimeMS() / (1000 * 60))
                + " minutes) while waiting for filelist");
        }
        if (!isConnected()) {
            logWarning("Disconnected while waiting for filelist");
        }
        return fileListsCompleted;
    }

    /**
     * Waits some time for the folder list
     * 
     * @return true if list was received successfully
     */
    private boolean waitForFolderList() {
        synchronized (folderListWaiter) {
            if (lastFolderList == null) {
                try {
                    if (isFiner()) {
                        logFiner("Waiting for folderlist");
                    }
                    folderListWaiter.wait(60000);
                } catch (InterruptedException e) {
                    logFiner(e);
                }
            }
        }
        return lastFolderList != null;
    }

    /**
     * Waits some time for the handshake to be completed
     * 
     * @return true if list was received successfully
     */
    private boolean waitForHandshakeCompletion() {
        synchronized (handshakeCompletedWaiter) {
            if (lastHandshakeCompleted == null) {
                try {
                    if (isFiner()) {
                        logFiner("Waiting for handshake completions");
                    }
                    handshakeCompletedWaiter
                        .wait(Constants.INCOMING_CONNECTION_TIMEOUT * 1000);
                } catch (InterruptedException e) {
                    logFiner(e);
                }
            }
        }
        return lastHandshakeCompleted != null && handshaked;
    }

    /**
     * Shuts the member and its connection down
     */
    public void shutdown() {
        boolean wasHandshaked = handshaked;

        // Notify waiting locks.
        synchronized (folderListWaiter) {
            folderListWaiter.notifyAll();
        }
        synchronized (handshakeCompletedWaiter) {
            handshakeCompletedWaiter.notifyAll();
        }

        shutdownPeer();

        lastFolderList = null;
        // Disco, assume completely
        setConnectedToNetwork(false);
        handshaked = false;
        lastHandshakeCompleted = null;
        lastTransferStatus = null;
        expectedListMessages.clear();
        messageListenerSupport = null;

        if (wasHandshaked) {
            // Inform security manager to update account state.
            getController().getSecurityManager().nodeAccountStateChanged(this);

            // Inform nodemanger about it
            getController().getNodeManager().connectStateChanged(this);

            if (isInfo()) {
                logInfo("Disconnected ("
                    + getController().getNodeManager().countConnectedNodes()
                    + " still connected)");
            }
        } else {
            // logFiner("Shutdown");
        }
    }

    /**
     * Helper method for sending messages on peer handler. Method waits for the
     * sendmessagebuffer to get empty
     * 
     * @param message
     *            The message to send
     * @throws ConnectionException
     */
    public void sendMessage(Message message) throws ConnectionException {
        checkPeer();

        if (peer != null) {
            // wait
            peer.waitForEmptySendQueue(-1);
            // synchronized (peerInitalizeLock) {
            if (peer != null) {
                // send
                peer.sendMessage(message);
            }
            // }

        }
    }

    /**
     * Enque one messages for sending. code execution does not wait util message
     * was sent successfully
     * 
     * @see PlainSocketConnectionHandler#sendMessagesAsynchron(Message[])
     * @param message
     *            the message to send
     * @param errorMessage
     *            the error message to be logged on connection problem
     */
    public void sendMessageAsynchron(Message message, String errorMessage) {
        if (peer != null && peer.isConnected()) {
            peer.sendMessagesAsynchron(message);
        }
    }

    /**
     * Enque multiple messages for sending. code execution does not wait util
     * message was sent successfully
     * 
     * @see PlainSocketConnectionHandler#sendMessagesAsynchron(Message[])
     * @param messages
     *            the messages to send
     */
    public void sendMessagesAsynchron(Message... messages) {
        if (peer != null && peer.isConnected()) {
            peer.sendMessagesAsynchron(messages);
        }
    }

    /**
     * Handles an incomming message from the remote peer (ConnectionHandler)
     * 
     * @param message
     *            The message to handle
     * @param fromPeer
     *            the peer this message has been received from.
     */
    public void handleMessage(final Message message,
        final ConnectionHandler fromPeer)
    {

        if (message == null) {
            throw new NullPointerException(
                "Unable to handle message, message is null");
        }

        // Profile this execution.
        ProfilingEntry profilingEntry = null;
        if (Profiling.ENABLED) {
            profilingEntry = Profiling.start("Member.handleMessage", message
                .getClass().getSimpleName());
        }

        int expectedTime = -1;
        try {
            // related folder is filled if message is a folder related message
            final FolderInfo targetedFolderInfo;
            final Folder targetFolder;
            if (message instanceof FolderRelatedMessage) {
                targetedFolderInfo = ((FolderRelatedMessage) message).folder;
                if (targetedFolderInfo != null) {
                    targetFolder = getController().getFolderRepository()
                        .getFolder(targetedFolderInfo);
                } else {
                    targetFolder = null;
                    logSevere("Got folder message without FolderInfo: "
                        + message);
                }
            } else {
                targetedFolderInfo = null;
                targetFolder = null;
            }

            // do all the message processing
            // Processing of message also should take only
            // a short time, because member is not able
            // to received any other message meanwhile !

            // Identity is not handled HERE !
            if (message instanceof Ping) {
                // TRAC #812: Answer the ping here. PONG is handled in
                // ConnectionHandler!
                Pong pong = new Pong((Ping) message);
                sendMessagesAsynchron(pong);
                expectedTime = 50;
            } else if (message instanceof HandshakeCompleted) {
                lastHandshakeCompleted = (HandshakeCompleted) message;
                // Notify waiting ppl
                synchronized (handshakeCompletedWaiter) {
                    handshaked = true;
                    handshakeCompletedWaiter.notifyAll();
                }
                expectedTime = 100;
            } else if (message instanceof FolderList) {
                final FolderList fList = (FolderList) message;
                Runnable r = new Runnable() {
                    public void run() {
                        folderJoinLock.lock();
                        try {
                            lastFolderList = fList;
                            // Send filelist only during handshake
                            joinToLocalFolders(fList, fromPeer);
                            // TODO: Set AFTER the list has been processed
                        } finally {
                            folderJoinLock.unlock();
                        }
                        // Notify waiting ppl
                        synchronized (folderListWaiter) {
                            folderListWaiter.notifyAll();
                        }
                    }
                };
                getController().getIOProvider().startIO(r);

                expectedTime = 300;
            } else if (message instanceof ScanCommand) {
                if (targetFolder != null
                    && targetFolder.getSyncProfile().isAutoDetectLocalChanges())
                {
                    logFiner("Remote sync command received on " + targetFolder);
                    getController().setSilentMode(false);
                    // Now trigger the scan
                    targetFolder.recommendScanOnNextMaintenance();
                    getController().getFolderRepository().triggerMaintenance();
                }
                expectedTime = 50;

            } else if (message instanceof RequestDownload) {
                // a download is requested. Put handling in background thread
                // for faster processing.
                Runnable runner = new Runnable() {
                    public void run() {
                        RequestDownload dlReq = (RequestDownload) message;
                        Upload ul = getController().getTransferManager()
                            .queueUpload(Member.this, dlReq);
                        if (ul == null) {
                            // Send abort
                            logWarning("Sending abort of " + dlReq.file);
                            sendMessagesAsynchron(new AbortUpload(dlReq.file));
                        }
                    }
                };
                getController().getIOProvider().startIO(runner);
                expectedTime = 100;

            } else if (message instanceof DownloadQueued) {
                // set queued flag here, if we received status from other side
                DownloadQueued dlQueued = (DownloadQueued) message;
                Download dl = getController().getTransferManager()
                    .getActiveDownload(this, dlQueued.file);
                if (dl != null) {
                    dl.setQueued(dlQueued.file);
                } else if (downloadRecentlyCompleted(dlQueued.file)) {
                    logWarning("Remote side queued non-existant download: "
                        + dlQueued.file);
                    sendMessageAsynchron(new AbortDownload(dlQueued.file), null);
                }
                expectedTime = 100;

            } else if (message instanceof AbortDownload) {
                AbortDownload abort = (AbortDownload) message;
                // Abort the upload
                getController().getTransferManager().abortUpload(abort.file,
                    this);
                expectedTime = 100;

            } else if (message instanceof AbortUpload) {
                AbortUpload abort = (AbortUpload) message;
                // Abort the upload
                getController().getTransferManager().abortDownload(abort.file,
                    this);
                expectedTime = 100;

            } else if (message instanceof FileChunk) {
                // File chunk received
                FileChunk chunk = (FileChunk) message;
                Download d = getController().getTransferManager()
                    .getActiveDownload(this, chunk.file);
                if (d != null) {
                    d.addChunk(chunk);
                } else if (downloadRecentlyCompleted(chunk.file)) {
                    sendMessageAsynchron(new AbortDownload(chunk.file), null);
                }
                expectedTime = -1;

            } else if (message instanceof RequestNodeList) {
                // Nodemanager will handle that
                RequestNodeList request = (RequestNodeList) message;
                getController().getNodeManager().receivedRequestNodeList(
                    request, this);
                expectedTime = 100;

            } else if (message instanceof KnownNodes) {
                KnownNodes newNodes = (KnownNodes) message;
                // TODO Move this code into NodeManager.receivedKnownNodes(....)
                // TODO This code should be done in NodeManager
                // This might also just be a search result and thus not include
                // us
                for (int i = 0; i < newNodes.nodes.length; i++) {
                    MemberInfo remoteNodeInfo = newNodes.nodes[i];
                    if (remoteNodeInfo == null) {
                        continue;
                    }

                    if (getInfo().equals(remoteNodeInfo)) {
                        // Take his info
                        updateInfo(remoteNodeInfo);
                    }
                }

                // Queue arrived node list at nodemanager
                getController().getNodeManager().queueNewNodes(newNodes.nodes);
                expectedTime = 200;

            } else if (message instanceof RequestNodeInformation) {
                // send him our node information
                sendMessageAsynchron(new NodeInformation(getController()), null);
                expectedTime = 50;

            } else if (message instanceof TransferStatus) {
                // Hold transfer status
                lastTransferStatus = (TransferStatus) message;
                expectedTime = 50;

            } else if (message instanceof NodeInformation) {
                if (isFiner()) {
                    logFiner("Node information received");
                }
                if (LoggingManager.isLogToFile()) {
                    Debug.writeNodeInformation((NodeInformation) message);
                }
                // Cache the last node information
                // lastNodeInformation = (NodeInformation) message;
                expectedTime = -1;

            } else if (message instanceof SettingsChange) {
                SettingsChange settingsChange = (SettingsChange) message;
                if (settingsChange.newInfo != null) {
                    logFine(this.getInfo().nick + " changed nick to "
                        + settingsChange.newInfo.nick);
                    setNick(settingsChange.newInfo.nick);
                }
                expectedTime = 50;

            } else if (message instanceof FileList) {
                final FileList remoteFileList = (FileList) message;

                if (isFine()) {
                    logFine("Received new filelist. Expecting "
                        + remoteFileList.nFollowingDeltas + " more deltas. "
                        + message);
                }
                // Reset counter of expected filelists
                expectedListMessages.put(remoteFileList.folder,
                    remoteFileList.nFollowingDeltas);

                if (targetFolder != null) {
                    // Inform folder
                    targetFolder.fileListChanged(Member.this, remoteFileList);
                }
                expectedTime = 250;

            } else if (message instanceof FolderFilesChanged) {
                final FolderFilesChanged changes = (FolderFilesChanged) message;
                Integer nExpected = expectedListMessages.get(changes.folder);
                if (nExpected == null) {
                    logSevere("Received folder changes on "
                        + changes.folder.name
                        + ", but not received the full filelist");
                    return;
                }
                nExpected -= 1;
                expectedListMessages.put(changes.folder, nExpected);

                TransferManager tm = getController().getTransferManager();
                if (changes.added != null) {
                    for (int i = 0; i < changes.added.length; i++) {
                        FileInfo file = changes.added[i];
                        if (file.getRelativeName().contains(
                            Constants.POWERFOLDER_SYSTEM_SUBDIR))
                        {
                            continue;
                            // #1411
                        }
                        // TODO Optimize: Don't break if files are same.
                        tm.abortDownload(file, this);
                    }
                }
                if (changes.removed != null) {
                    for (int i = 0; i < changes.removed.length; i++) {
                        FileInfo file = changes.removed[i];
                        if (file.getRelativeName().contains(
                            Constants.POWERFOLDER_SYSTEM_SUBDIR))
                        {
                            continue;
                            // #1411
                        }
                        // TODO Optimize: Don't break if files are same.
                        tm.abortDownload(file, this);
                    }
                }

                if (isFine()) {
                    int msgs = expectedListMessages.get(targetedFolderInfo);
                    if (msgs >= 0) {
                        logFine("Received folder change. Expecting " + msgs
                            + " more deltas. " + message);
                    } else {
                        logFine("Received folder change. Received " + (-msgs)
                            + " additional deltas. " + message);
                    }
                }

                if (targetFolder != null) {
                    // Inform folder
                    targetFolder.fileListChanged(Member.this, changes);
                }
                expectedTime = 250;

            } else if (message instanceof Invitation) {

                // Invitation to folder
                Invitation invitation = (Invitation) message;

                // Server is the only one who is allowed to send invitations
                // with a different invitor
                if (!getController().getOSClient().isServer(this)) {
                    // To ensure invitor is correct for all other computers
                    invitation.setInvitor(getInfo());
                }

                getController().invitationReceived(invitation, false);
                expectedTime = 100;

            } else if (message instanceof Problem) {
                lastProblem = (Problem) message;

                if (lastProblem.problemCode == Problem.DO_NOT_LONGER_CONNECT) {
                    // Finds us boring
                    // set unable to connect
                    logFine("Problem received: Node reject our connection, "
                        + "we should not longer try to connect");
                    // Not connected to public network
                    setConnectedToNetwork(true);
                } else if (lastProblem.problemCode == Problem.DUPLICATE_CONNECTION)
                {
                    logWarning("Problem received: Node thinks we have a dupe connection to him");
                } else {
                    logWarning("Problem received: " + lastProblem);
                }

                if (lastProblem.fatal) {
                    // Shutdown
                    shutdown();
                }
                expectedTime = 100;

            } else if (message instanceof SearchNodeRequest) {
                // Send nodelist that matches the search.
                final SearchNodeRequest request = (SearchNodeRequest) message;
                getController().getNodeManager().receivedSearchNodeRequest(
                    request, this);
                expectedTime = 50;

            } else if (message instanceof AddFriendNotification) {
                AddFriendNotification notification = (AddFriendNotification) message;
                AskForFriendshipEvent event = new AskForFriendshipEvent(
                    notification.getMemberInfo(), notification
                        .getPersonalMessage());
                getController().addAskForFriendship(event);
                expectedTime = 50;
            } else if (message instanceof Notification) {
                // This is the V3 friendship notification class.
                // V4 uses AddFriendNotification.
                Notification not = (Notification) message;
                if (not.getEvent() == null) {
                    logWarning("Unknown event from peer");
                } else {
                    switch (not.getEvent()) {
                        case ADDED_TO_FRIENDS :
                            AskForFriendshipEvent event = new AskForFriendshipEvent(
                                getInfo(), not.getPersonalMessage());
                            getController().addAskForFriendship(event);
                            break;
                        default :
                            logWarning("Unhandled event: " + not.getEvent());
                    }
                }
                expectedTime = 50;

            } else if (message instanceof RequestPart) {
                final RequestPart pr = (RequestPart) message;
                Runnable r = new Runnable() {
                    public void run() {
                        Upload up = getController().getTransferManager()
                            .getUpload(Member.this, pr.getFile());
                        if (up != null) { // If the upload isn't broken
                            up.enqueuePartRequest(pr);
                        } else {
                            sendMessageAsynchron(new AbortUpload(pr.getFile()),
                                null);
                        }
                    }
                };
                getController().getIOProvider().startIO(r);
                expectedTime = 100;

            } else if (message instanceof StartUpload) {
                StartUpload su = (StartUpload) message;
                Download dl = getController().getTransferManager()
                    .getActiveDownload(this, su.getFile());
                if (dl != null) {
                    dl.uploadStarted(su.getFile());
                } else if (downloadRecentlyCompleted(su.getFile())) {
                    logInfo("Download invalid or obsolete:" + su.getFile());
                    sendMessageAsynchron(new AbortDownload(su.getFile()), null);
                }
                expectedTime = 100;

            } else if (message instanceof StopUpload) {
                StopUpload su = (StopUpload) message;
                Upload up = getController().getTransferManager().getUpload(
                    this, su.getFile());
                if (up != null) { // If the upload isn't broken
                    up.stopUploadRequest(su);
                }
                expectedTime = 100;

            } else if (message instanceof RequestFilePartsRecord) {
                RequestFilePartsRecord req = (RequestFilePartsRecord) message;
                Upload up = getController().getTransferManager().getUpload(
                    this, req.getFile());
                if (up != null) { // If the upload isn't broken
                    up.receivedFilePartsRecordRequest(req);
                } else {
                    sendMessageAsynchron(new AbortUpload(req.getFile()), null);
                }
                expectedTime = 100;

            } else if (message instanceof ReplyFilePartsRecord) {
                ReplyFilePartsRecord rep = (ReplyFilePartsRecord) message;
                Download dl = getController().getTransferManager()
                    .getActiveDownload(this, rep.getFile());
                if (dl != null) {
                    dl.receivedFilePartsRecord(rep.getFile(), rep.getRecord());
                } else if (downloadRecentlyCompleted(rep.getFile())) {
                    logInfo("Download not found: " + dl);
                    sendMessageAsynchron(new AbortDownload(rep.getFile()), null);
                }
                expectedTime = 100;

            } else if (message instanceof RelayedMessage) {
                RelayedMessage relMsg = (RelayedMessage) message;
                getController().getIOProvider().getRelayedConnectionManager()
                    .handleRelayedMessage(this, relMsg);
                expectedTime = -1;

            } else if (message instanceof UDTMessage) {
                getController().getIOProvider().getUDTSocketConnectionManager()
                    .handleUDTMessage(this, (UDTMessage) message);
                expectedTime = 50;

            } else if (message instanceof FileHistoryRequest) {
                final FileInfo requested = ((FileHistoryRequest) message)
                    .getFileInfo();
                // No need to wait for the FileDAO to have built the FileHistory
                getController().getIOProvider().startIO(new Runnable() {
                    public void run() {
                        Folder f = getController().getFolderRepository()
                            .getFolder(requested.getFolderInfo());
                        if (f == null) {
                            logWarning("Illegal FileHistoryRequest from "
                                + this
                                + ": This client is not member of the folder.");
                            return;
                        }
                        sendMessageAsynchron(new FileHistoryReply(f.getDAO()
                            .getFileHistory(requested), requested),
                            "Failed to send FileHistoryReply.");
                    }
                });

            } else if (message instanceof FileHistoryReply) {
                getController().getFolderRepository().getFileRequestor()
                    .receivedFileHistory((FileHistoryReply) message);

            } else if (message instanceof SingleFileOffer) {
                getController().singleFileOfferReceived(
                    (SingleFileOffer) message);
                expectedTime = 50;

            } else if (message instanceof SingleFileAccept) {
                // getController().getTransferManager().processSingleFileAcceptance(
                // (SingleFileAccept) message, fromPeer.getMember().getInfo());
                expectedTime = 50;
            } else if (message instanceof AccountStateChanged) {
                AccountStateChanged asc = (AccountStateChanged) message;
                logFine("Received: " + asc);
                Member node = asc.getNode().getNode(getController(), false);
                if (node != null) {
                    getController().getSecurityManager()
                        .nodeAccountStateChanged(node);
                }
                asc.decreaseTTL();
                if (asc.isAlive()) {
                    // Continue broadcast.
                    getController().getNodeManager().broadcastMessage(asc);
                }
            } else if (message instanceof ConfigurationLoadRequest) {
                if (isServer()) {
                    ConfigurationLoadRequest clr = (ConfigurationLoadRequest) message;
                    try {
                        Properties preConfig = ConfigurationLoader
                            .loadPreConfiguration(clr.getConfigURL());
                        ConfigurationLoader.mergeConfigs(preConfig,
                            getController().getConfig(), clr
                                .isReplaceExisting());
                        // Seems to be valid, store.
                        getController().saveConfig();
                        if (clr.isRestartRequired()) {
                            getController().shutdownAndRequestRestart();
                        }
                    } catch (IOException e) {
                        logSevere("Unable to reload configuration: " + clr
                            + ". " + e, e);
                    }
                } else {
                    logWarning("Ingnoring reload config request from non server: "
                        + message);
                }
            } else {
                logFiner("Message not known to message handling code, "
                    + "maybe handled in listener: " + message);
            }

            // Give message to node manager
            getController().getNodeManager().messageReceived(this, message);
            // now give the message to all message listeners
            fireMessageToListeners(message);
        } finally {
            Profiling.end(profilingEntry, expectedTime);
        }
    }

    /**
     * Adds a message listener
     * 
     * @param aListener
     *            The listener to add
     */
    public void addMessageListener(MessageListener aListener) {
        getMessageListenerSupport().addMessageListener(aListener);
    }

    /**
     * Adds a message listener, which is only triggerd if a message of type
     * <code>messageType</code> is received.
     * 
     * @param messageType
     *            The type of messages to register too.
     * @param aListener
     *            The listener to add
     */
    public void addMessageListener(Class<?> messageType,
        MessageListener aListener)
    {
        getMessageListenerSupport().addMessageListener(messageType, aListener);
    }

    /**
     * Removes a message listener completely from this member
     * 
     * @param aListener
     *            The listener to remove
     */
    public void removeMessageListener(MessageListener aListener) {
        getMessageListenerSupport().removeMessageListener(aListener);
    }

    /**
     * Overridden, removes message listeners.
     */
    @Override
    public void removeAllListeners() {
        if (isFiner()) {
            logFiner("Removing all listeners from member. " + this);
        }
        super.removeAllListeners();
        // Remove message listeners
        getMessageListenerSupport().removeAllListeners();
    }

    /**
     * Fires a message to all message listeners
     * 
     * @param message
     *            the message to fire
     */
    private void fireMessageToListeners(Message message) {
        getMessageListenerSupport().fireMessage(this, message);
    }

    private synchronized MessageListenerSupport getMessageListenerSupport() {
        if (messageListenerSupport == null) {
            messageListenerSupport = new MessageListenerSupport(this);
        }
        return messageListenerSupport;
    }

    /*
     * Remote group joins
     */

    /**
     * Synchronizes the folder memberships on both sides
     */
    public void synchronizeFolderMemberships() {
        if (isMySelf()) {
            return;
        }
        if (!isCompletelyConnected()) {
            return;
        }

        Collection<FolderInfo> joinedFolders = getController()
            .getFolderRepository().getJoinedFolderInfos();
        ConnectionHandler thisPeer = peer;
        String remoteMagicId = thisPeer != null
            ? thisPeer.getRemoteMagicId()
            : null;
        if (thisPeer == null || StringUtils.isBlank(remoteMagicId)) {
            return;
        }
        folderJoinLock.lock();
        try {
            FolderList folderList = getLastFolderList();
            if (folderList != null) {
                // Rejoin to local folders
                joinToLocalFolders(folderList, thisPeer);
            } else {
                // Hopefully we receive this later.
                logSevere("Unable to synchronize memberships, "
                    + "did not received folderlist from remote");
            }
            FolderList myFolderList = new FolderList(joinedFolders,
                remoteMagicId);
            sendMessageAsynchron(myFolderList, null);
        } finally {
            folderJoinLock.unlock();
        }
    }

    /**
     * Joins member to all local folders which are also available on remote
     * peer, removes member from all local folders, if not longer member of
     * 
     * @throws ConnectionException
     */
    private void joinToLocalFolders(FolderList folderList,
        ConnectionHandler fromPeer)
    {
        folderJoinLock.lock();
        try {
            FolderRepository repo = getController().getFolderRepository();
            Set<FolderInfo> joinedFolders = new HashSet<FolderInfo>();
            Collection<Folder> localFolders = repo.getFolders();

            String myMagicId = fromPeer != null
                ? fromPeer.getMyMagicId()
                : null;
            if (fromPeer == null) {
                logWarning("Unable to join to local folders. peer is null/disconnected");
                return;
            }
            if (StringUtils.isBlank(myMagicId)) {
                logSevere("Unable to join to local folders. Own magic id of peer is blank: "
                    + peer);
                return;
            }

            // Process secret folders now
            if (folderList.secretFolders != null
                && folderList.secretFolders.length > 0)
            {
                // Step 1: Calculate secure folder ids for local secret folders
                Map<FolderInfo, Folder> localSecretFolders = new HashMap<FolderInfo, Folder>();
                for (Folder folder : localFolders) {
                    // Calculate id with my magic id
                    String secureId = folder.getInfo().calculateSecureId(
                        myMagicId);
                    FolderInfo secretFolderCanidate = new FolderInfo(folder
                        .getInfo().getName(), secureId);
                    // Add to local secret folder list
                    localSecretFolders.put(secretFolderCanidate, folder);
                }

                // Step 2: Check if remote side has joined one of our secret
                // folders
                for (int i = 0; i < folderList.secretFolders.length; i++) {
                    FolderInfo secretFolder = folderList.secretFolders[i];
                    if (localSecretFolders.containsKey(secretFolder)) {
                        logFiner("Also has secret folder: " + secretFolder);
                        Folder folder = localSecretFolders.get(secretFolder);
                        // Join him into our folder if possible.
                        if (folder.join(this)) {
                            joinedFolders.add(folder.getInfo());
                        }
                    }
                }
            }

            // ok now remove member from not longer joined folders
            for (Folder folder : localFolders) {
                if (folder != null && !joinedFolders.contains(folder.getInfo()))
                {
                    // remove this member from folder, if not on new folder
                    folder.remove(this);
                }
            }

            if (!joinedFolders.isEmpty()) {
                logInfo(getNick() + " joined " + joinedFolders.size()
                    + " folder(s)");
                if (!isFriend() && !isServer()) {
                    AskForFriendshipEvent event = new AskForFriendshipEvent(
                        getInfo(), joinedFolders);
                    getController().addAskForFriendship(event);
                }
            }
        } finally {
            folderJoinLock.unlock();
        }
    }

    /*
     * Request to remote peer
     */

    /**
     * Answers the latest received folder list
     * 
     * @return the latest received folder list
     */
    public FolderList getLastFolderList() {
        return lastFolderList;
    }

    /**
     * Answers if we received the complete filelist (+all nessesary deltas) on
     * that folder.
     * 
     * @param foInfo
     * @return true if we received the complete filelist (+all nessesary deltas)
     *         on that folder.
     */
    public boolean hasCompleteFileListFor(FolderInfo foInfo) {
        Integer nUpcomingMsgs = expectedListMessages.get(foInfo);
        if (nUpcomingMsgs == null) {
            return false;
        }
        // nUpcomingMsgs might have negativ values! means we received deltas
        // after the inital filelist.
        return nUpcomingMsgs <= 0;
    }

    /**
     * Returns the last transfer status of this node
     * 
     * @return the last transfer status of this node
     */
    public TransferStatus getLastTransferStatus() {
        if (isMySelf()) {
            return getController().getTransferManager().getStatus();
        }
        return lastTransferStatus;
    }

    /**
     * @return true if user joined any folder
     */
    public boolean hasJoinedAnyFolder() {
        for (Folder folder : getController().getFolderRepository().getFolders())
        {
            if (folder.hasMember(this)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the list of joined folders.
     */
    public List<Folder> getJoinedFolders() {
        List<Folder> joinedFolders = new ArrayList<Folder>();
        for (Folder folder : getController().getFolderRepository().getFolders())
        {
            if (folder.hasMember(this)) {
                joinedFolders.add(folder);
            }
        }
        return joinedFolders;
    }

    /**
     * @return the list folders in common.
     */
    public List<Folder> getFoldersInCommon() {
        String magicId = getPeer().getMyMagicId();
        FolderList fList = getLastFolderList();
        if (fList == null) {
            return Collections.emptyList();
        }
        List<Folder> joinedFolders = new ArrayList<Folder>();
        for (Folder folder : getController().getFolderRepository().getFolders())
        {
            if (fList.contains(folder.getInfo(), magicId)) {
                joinedFolders.add(folder);
            }
        }
        return joinedFolders;
    }

    /**
     * Answers if member has the file available to download. Does NOT check
     * version match
     * 
     * @param file
     *            the FileInfo to find at this user
     * @return true if this user has this file, or false if not or if no
     *         filelist received (yet)
     */
    public boolean hasFile(FileInfo file) {
        FileInfo remoteFile = getFile(file);
        return remoteFile != null && !remoteFile.isDeleted();
    }

    /**
     * Returns the remote file info from the node. May return null if file is
     * not known by remote or no filelist was received yet. Does return the
     * internal database file if myself.
     * 
     * @param file
     *            local file
     * @return the fileInfo of remote side, or null
     */
    public FileInfo getFile(FileInfo file) {
        if (file == null) {
            throw new NullPointerException("File is null");
        }
        Folder folder = file.getFolder(getController().getFolderRepository());
        if (folder == null) {
            // Folder not joined, so we don't have the file.
            return null;
        }
        if (isMySelf()) {
            return folder.getDAO().find(file, null);
        }
        return folder.getDAO().find(file, getId());
    }

    /*
     * Simple getters
     */

    /**
     * @return The ID of this member
     */
    public String getId() {
        return info.id;
    }

    /**
     * @return nick name of the member
     */
    public String getNick() {
        return info.nick;
    }

    /**
     * set the nick name of this member
     * 
     * @param nick
     *            The nick to set
     */
    public void setNick(String nick) {
        info.nick = nick;
        // Fire event on nodemanager
        getController().getNodeManager().fireNodeSettingsChanged(this);
    }

    /**
     * #1373
     * 
     * @return true if this node is on the same network.
     */
    public boolean isOnSameNetwork() {
        return info.isOnSameNetwork(getController());
    }

    /**
     * Returns the identity of this member.
     * 
     * @return the identity if connection is established, otherwise null
     */
    public Identity getIdentity() {
        if (peer != null) {
            return peer.getIdentity();
        }
        return null;
    }

    /**
     * @return the ip + portnumber in InetSocketAddress to connect to.
     */
    public InetSocketAddress getReconnectAddress() {
        return info.getConnectAddress();
    }

    /**
     * Answers when the member connected last time or null, if member never
     * connected
     * 
     * @return Date Object representing the last connect time or null, if member
     *         never connected
     */
    public Date getLastConnectTime() {
        return info.lastConnectTime;
    }

    /**
     * Answers the last connect time of the user to the network. Last connect
     * time is determinded by the information about users from other nodes and
     * own last connection date to that node
     * 
     * @return Date object representing the last time on the network
     */
    public Date getLastNetworkConnectTime() {
        if (info.lastConnectTime == null) {
            return lastNetworkConnectTime;
        } else if (lastNetworkConnectTime == null) {
            return info.lastConnectTime;
        }
        if (info.lastConnectTime.after(lastNetworkConnectTime)) {
            return info.lastConnectTime;
        }
        return lastNetworkConnectTime;
    }

    /**
     * Returns the member information. add connected info
     * 
     * @return the MemberInfo object
     */
    public MemberInfo getInfo() {
        info.isConnected = isConnected();
        return info;
    }

    /**
     * Answers if this member is connected to the PF network
     * 
     * @return true if this member is connected to the PF network
     */
    public boolean isConnectedToNetwork() {
        return isCompletelyConnected() || isConnectedToNetwork;
    }

    /**
     * set the connected to network status
     * 
     * @param connected
     *            flag indicating if this member is connected
     */
    public void setConnectedToNetwork(boolean connected) {
        boolean changed = isConnectedToNetwork != connected;
        isConnectedToNetwork = connected;
        if (changed) {
            getController().getNodeManager()
                .networkConnectionStateChanged(this);
        }
    }

    /**
     * Answers if we the remote node told us not longer to connect.
     * 
     * @return true if the remote side didn't want to be connected.
     */
    public boolean isDontConnect() {
        return lastProblem != null
            && lastProblem.problemCode == Problem.DO_NOT_LONGER_CONNECT;
    }

    /**
     * @return true if no direct connection to this member is possible. (At
     *         least 2 tries)
     */
    public boolean isUnableToConnect() {
        return connectionRetries >= 3;
    }

    /**
     * @return the last problem received from this node.
     */
    public Problem getLastProblem() {
        return lastProblem;
    }

    /**
     * @return true if this is a server that should be reconnected
     */
    public boolean isServer() {
        return server;
    }

    /**
     * Sets/Unsets this member as server that should be reconnected.
     * 
     * @param server
     */
    public void setServer(boolean server) {
        this.server = server;
    }

    /**
     * @return the account info of the user logged in at the remote node.
     */
    public AccountInfo getAccountInfo() {
        return getController().getSecurityManager().getAccountInfo(this);
    }

    /**
     * Updates connection information, if the other is more 'valueble'.
     * <p>
     * TODO CLEAN UP THIS MESS!!!! -> Define behaviour and write tests.
     * 
     * @param newInfo
     *            The new MemberInfo to use if more valueble
     * @return true if we found valueble information
     */
    public boolean updateInfo(MemberInfo newInfo) {
        boolean updated = false;
        if (!isConnected() && newInfo.isConnected) {
            // take info, if this is now a supernode
            if (newInfo.isSupernode && !info.isSupernode) {
                if (isFiner()) {
                    logFiner("Received new supernode information: " + newInfo);
                }
            }
            info.isSupernode = newInfo.isSupernode;
            info.networkId = newInfo.networkId;
            // if (!isOnLAN()) {
            // Take his dns address, but only if not on lan
            // (Otherwise our ip to him will be used as reconnect address)
            // Commentend until 100% LAN/inet detection accurate
            info.setConnectAddress(newInfo.getConnectAddress());
            // }
            updated = true;
        }

        // Take his last connect time if newer
        boolean updateLastNetworkConnectTime = (lastNetworkConnectTime == null && newInfo.lastConnectTime != null)
            || (newInfo.lastConnectTime != null && lastNetworkConnectTime
                .before(newInfo.lastConnectTime));

        if (!isConnected() && updateLastNetworkConnectTime) {
            // logFiner(
            // "Last connect time fresher on remote side. this "
            // + lastNetworkConnectTime + ", remote: "
            // + newInfo.lastConnectTime);
            lastNetworkConnectTime = newInfo.lastConnectTime;
            updated = true;
        }

        if (updated) {
            // Re try connection
            connectionRetries = 0;
        }
        return updated;
    }

    public boolean askedForFriendship() {
        return askedForFriendship;
    }

    public void setAskedForFriendship(boolean flag) {
        askedForFriendship = flag;
    }

    private boolean downloadRecentlyCompleted(FileInfo fInfo) {
        Reject.ifNull(fInfo, "FileInfo is null");
        Download dl = getController().getTransferManager()
            .getCompletedDownload(this, fInfo);
        if (dl != null) {
            return true;
        }
        if (ConfigurationEntry.DOWNLOADS_AUTO_CLEANUP
            .getValueBoolean(getController()))
        {
            // THIS is a HACK(tm), we never know if this download has been
            // completed, since it probably is already removed.
            return true;
        }
        return false;
    }

    // Logger methods *********************************************************

    @Override
    public String getLoggerName() {
        return super.getLoggerName() + " '" + getNick() + "'"
            + (isSupernode() ? " (s)" : "");
    }

    /*
     * General
     */

    @Override
    public String toString() {
        String connect;

        if (isConnected()) {
            connect = peer + "";
        } else {
            connect = isMySelf() ? "myself" : "-disco.-, " + "recon. at "
                + getReconnectAddress();
        }

        return "Member '" + info.nick + "' (" + connect + ")";
    }

    /**
     * true if the ID's of the memberInfo objects are equal
     * 
     * @param other
     * @return true if the ID's of the memberInfo objects are equal
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof Member) {
            Member oM = (Member) other;
            return Util.equals(this.info.id, oM.info.id);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return (info.id == null) ? 0 : info.id.hashCode();
    }

    public int compareTo(Member m) {
        return info.id.compareTo(m.info.id);
    }
}