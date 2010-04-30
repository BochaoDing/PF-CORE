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
package de.dal33t.powerfolder;

import static de.dal33t.powerfolder.disk.FolderSettings.FOLDER_SETTINGS_PREFIX_V3;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.Security;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.ServiceLoader;
import java.util.StringTokenizer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JOptionPane;

import org.apache.commons.cli.CommandLine;

import de.dal33t.powerfolder.clientserver.ServerClient;
import de.dal33t.powerfolder.disk.FolderRepository;
import de.dal33t.powerfolder.distribution.Distribution;
import de.dal33t.powerfolder.distribution.PowerFolderBasic;
import de.dal33t.powerfolder.distribution.PowerFolderPro;
import de.dal33t.powerfolder.event.AskForFriendshipEvent;
import de.dal33t.powerfolder.event.AskForFriendshipListener;
import de.dal33t.powerfolder.event.InvitationHandler;
import de.dal33t.powerfolder.event.LocalMassDeletionEvent;
import de.dal33t.powerfolder.event.MassDeletionHandler;
import de.dal33t.powerfolder.event.RemoteMassDeletionEvent;
import de.dal33t.powerfolder.event.SingleFileOfferHandler;
import de.dal33t.powerfolder.event.WarningEvent;
import de.dal33t.powerfolder.event.WarningHandler;
import de.dal33t.powerfolder.message.Invitation;
import de.dal33t.powerfolder.message.RequestNodeInformation;
import de.dal33t.powerfolder.message.SettingsChange;
import de.dal33t.powerfolder.message.SingleFileOffer;
import de.dal33t.powerfolder.net.BroadcastMananger;
import de.dal33t.powerfolder.net.ConnectionException;
import de.dal33t.powerfolder.net.ConnectionHandler;
import de.dal33t.powerfolder.net.ConnectionListener;
import de.dal33t.powerfolder.net.DynDnsManager;
import de.dal33t.powerfolder.net.HTTPProxySettings;
import de.dal33t.powerfolder.net.IOProvider;
import de.dal33t.powerfolder.net.NodeManager;
import de.dal33t.powerfolder.net.ReconnectManager;
import de.dal33t.powerfolder.plugin.PluginManager;
import de.dal33t.powerfolder.security.SecurityManager;
import de.dal33t.powerfolder.security.SecurityManagerClient;
import de.dal33t.powerfolder.task.PersistentTaskManager;
import de.dal33t.powerfolder.transfer.TransferManager;
import de.dal33t.powerfolder.ui.UIController;
import de.dal33t.powerfolder.util.Debug;
import de.dal33t.powerfolder.util.FileUtils;
import de.dal33t.powerfolder.util.ForcedLanguageFileResourceBundle;
import de.dal33t.powerfolder.util.Format;
import de.dal33t.powerfolder.util.JavaVersion;
import de.dal33t.powerfolder.util.NamedThreadFactory;
import de.dal33t.powerfolder.util.ProUtil;
import de.dal33t.powerfolder.util.Profiling;
import de.dal33t.powerfolder.util.PropertiesUtil;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.StringUtils;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.Util;
import de.dal33t.powerfolder.util.WrappedScheduledThreadPoolExecutor;
import de.dal33t.powerfolder.util.logging.LoggingManager;
import de.dal33t.powerfolder.util.os.OSUtil;
import de.dal33t.powerfolder.util.os.Win32.FirewallUtil;
import de.dal33t.powerfolder.util.os.Win32.WinUtils;
import de.dal33t.powerfolder.util.ui.LimitedConnectivityChecker;
import de.dal33t.powerfolder.util.ui.UIUnLockDialog;
import de.dal33t.powerfolder.util.update.Updater;

/**
 * Central class gives access to all core components in PowerFolder. Make sure
 * to extend PFComponent so you always have a reference to the main
 * {@link Controller}.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.107 $
 */
public class Controller extends PFComponent {
    private static final Logger log = Logger.getLogger(Controller.class
        .getName());

    /**
     * Program version. include "dev" if its a development version.
     */
    public static final String PROGRAM_VERSION = "4.2.3 RC6"; // 1.7.0.11 / RC5

    /**
     * the (java beans like) property, listen to changes of the networking mode
     * by calling addPropertyChangeListener with this as parameter
     */
    public static final String PROPERTY_NETWORKING_MODE = "networkingMode";
    public static final String PROPERTY_SILENT_MODE = "silentMode";
    public static final String PROPERTY_LIMITED_CONNECTIVITY = "limitedConnectivity";

    /** general wait time for all threads (5000 is a balanced value) */
    private static final long WAIT_TIME = 5000;

    /** general wait time for all threads (5000 is a balanced value) */
    private static final String DEFAULT_CONFIG_FILE = "PowerFolder.config";

    /** The command line entered by the user when starting the program */
    private CommandLine commandLine;

    /** filename of the current configFile */
    private String configFilename;
    /**
     * The actual config file.
     */
    private File configFile;

    /** The config properties */
    private Properties config;

    /**
     * The preferences
     */
    private Preferences preferences;

    /**
     * The distribution running.
     */
    private Distribution distribution;

    /** Program start time */
    private Date startTime;

    /** Are we in started state? */
    private volatile boolean started;

    /** Are we trying to shutdown? */
    private volatile boolean shuttingDown;

    /** Is a restart requested */
    private boolean restartRequested;

    /** Are we in verbose mode? */
    private boolean verbose;

    /** Should we request debug reports? */
    private boolean debugReports;

    /**
     * If running is silent mode
     */
    private volatile boolean silentMode;

    /**
     * cache the networking mode in a field so we dont heve to do all this
     * comparing
     */
    private NetworkingMode networkingMode;

    /** The nodemanager that holds all members */
    private NodeManager nodeManager;

    /**
     * Responsible for (re-)connecting to other nodes.
     */
    private ReconnectManager reconnectManager;

    /** The FolderRepository that holds all "joined" folders */
    private FolderRepository folderRepository;

    /** The Listener to incomming connections of other PowerFolder clients */
    private ConnectionListener connectionListener;

    /** The basic io provider */
    private IOProvider ioProvider;

    /**
     * besides the default listener we may have a list of connection listeners
     * that listen on other ports
     */
    private List<ConnectionListener> additionalConnectionListeners;

    private final List<AskForFriendshipListener> askForFriendshipListeners;
    private final List<InvitationHandler> invitationHandlers;
    private final List<SingleFileOfferHandler> singleFileOfferHandlers;
    private final List<WarningHandler> warningHandlers;
    private final List<MassDeletionHandler> massDeletionHandlers;

    /** The BroadcastManager send "broadcasts" on the LAN so we can */
    private BroadcastMananger broadcastManager;

    /**
     * The DynDNS manager that handles the working arwound for user with a
     * dynnamip IP address.
     */
    private DynDnsManager dyndnsManager;

    private PersistentTaskManager taskManager;

    private Callable<TransferManager> transferManagerFactory = new Callable<TransferManager>()
    {
        public TransferManager call() throws Exception {
            return new TransferManager(Controller.this);
        }
    };

    /** Handels the up and downloads */
    private TransferManager transferManager;

    /**
     * Remote Commands listener, a protocol handler for powerfolder links:
     * powerfolder://
     */
    private RemoteCommandManager rconManager;

    /** Holds the User interface */
    private UIController uiController;

    /** holds all installed plugins */
    private PluginManager pluginManager;
    /**
     * The security manager, handles access etc.
     */
    private SecurityManager securityManager;

    /**
     * The Online Storage client
     */
    private ServerClient osClient;

    /**
     * the currently used socket to connect to a new member used in shutdown,
     * connection try ofter take 30s
     */
    private Socket currentConnectingSocket;

    /** global Threadpool */
    private ScheduledExecutorService threadPool;

    /** Remembers if a port on the local firewall was opened */
    private boolean portWasOpened = false;

    /**
     * If we have limited connecvitiy
     */
    private boolean limitedConnectivity;

    private Controller() {
        super();
        // Do some TTL fixing for dyndns resolving
        Security.setProperty("networkaddress.cache.ttl", "0");
        Security.setProperty("networkaddress.cache.negative.ttl", "0");
        System.setProperty("sun.net.inetaddr.ttl", "0");
        System.setProperty("com.apple.mrj.application.apple.menu.about.name",
            "PowerFolder");
        askForFriendshipListeners = new CopyOnWriteArrayList<AskForFriendshipListener>();
        invitationHandlers = new CopyOnWriteArrayList<InvitationHandler>();
        singleFileOfferHandlers = new CopyOnWriteArrayList<SingleFileOfferHandler>();
        warningHandlers = new CopyOnWriteArrayList<WarningHandler>();
        massDeletionHandlers = new CopyOnWriteArrayList<MassDeletionHandler>();
    }

    /**
     * Overwite the PFComponent.getController() otherwise that one returns null
     * for this Controller itself.
     * 
     * @return a reference to this
     */
    public Controller getController() {
        return this;
    }

    /**
     * Creates a fresh Controller.
     * 
     * @return the controller
     */
    public static Controller createController() {
        return new Controller();
    }

    /**
     * Starts this controller loading the config from the default config file
     */
    public void startDefaultConfig() {
        startConfig(DEFAULT_CONFIG_FILE);
    }

    /**
     * Starts a config with the given command line arguments
     * 
     * @param aCommandLine
     *            the command line as specified by the user
     */
    public void startConfig(CommandLine aCommandLine) {
        this.commandLine = aCommandLine;
        String configName = aCommandLine.getOptionValue("c");
        startConfig(configName);
    }

    /**
     * Starts controller with a special config file, and creates and starts all
     * components of PowerFolder.
     * 
     * @param filename
     *            The filename to uses as config file (located in the
     *            "getConfigLocationBase()")
     */
    public void startConfig(String filename) {
        if (started) {
            throw new IllegalStateException(
                "Configuration already started, shutdown controller first");
        }

        additionalConnectionListeners = Collections
            .synchronizedList(new ArrayList<ConnectionListener>());
        started = false;
        threadPool = new WrappedScheduledThreadPoolExecutor(
            Constants.CONTROLLER_THREADS_IN_THREADPOOL, new NamedThreadFactory(
                "Controller-Thread-"));

        // Initalize resouce bundle eager
        // check forced language file from commandline
        if (getCommandLine() != null && getCommandLine().hasOption("f")) {
            String langfilename = getCommandLine().getOptionValue("f");
            try {
                ResourceBundle resourceBundle = new ForcedLanguageFileResourceBundle(
                    langfilename);
                Translation.setResourceBundle(resourceBundle);
                logInfo("Loading language bundle from file " + langfilename);
            } catch (FileNotFoundException fnfe) {
                logSevere("forced language file (" + langfilename
                    + ") not found: " + fnfe.getMessage());
                logSevere("using setup language");
                Translation.resetResourceBundle();
            } catch (IOException ioe) {
                logSevere("forced language file io error: " + ioe.getMessage());
                logSevere("using setup language");
                Translation.resetResourceBundle();
            }
        } else {
            Translation.resetResourceBundle();
        }
        Translation.getResourceBundle();

        // loadConfigFile
        if (!loadConfigFile(filename)) {
            return;
        }
        boolean isDefaultConfig = DEFAULT_CONFIG_FILE
            .startsWith(getConfigName());
        if (isDefaultConfig) {
            // To keep compatible with previous versions
            preferences = Preferences.userNodeForPackage(PowerFolder.class);
        } else {
            preferences = Preferences.userNodeForPackage(PowerFolder.class)
                .node(getConfigName());
        }

        // initialize logger
        initLogger();
        logInfo("PowerFolder v" + PROGRAM_VERSION);
        logFine("OS: " + System.getProperty("os.name"));
        logFine("Java: " + JavaVersion.systemVersion().toString() + " ("
            + System.getProperty("java.vendor") + ')');
        logFine("Current time: " + new Date());
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        logFine("Max Memory: " + Format.formatBytesShort(maxMemory)
            + ", Total Memory: " + Format.formatBytesShort(totalMemory));
        if (!Desktop.isDesktopSupported() && isUIEnabled()) {
            logWarning("Desktop utility not supported");
        }

        // Init silentmode
        silentMode = preferences.getBoolean("silentMode", false);

        // Load and set http proxy settings
        HTTPProxySettings.loadFromConfig(this);

        // Initialize branding/preconfiguration of the client
        initDistribution();
        logInfo("Build time: " + getBuildTime());

        Debug.writeSystemProperties();

        // Initialize plugins
        setupProPlugins();
        pluginManager = new PluginManager(this);
        pluginManager.init();

        // create node manager
        nodeManager = new NodeManager(this);

        // Only one task brother left...
        taskManager = new PersistentTaskManager(this);

        // Folder repository
        folderRepository = new FolderRepository(this);
        setLoadingCompletion(0, 10);

        // Create transfer manager
        // If this is a unit test it might have been set before.
        try {
            transferManager = transferManagerFactory.call();
        } catch (Exception e) {
            logSevere("Exception", e);
        }

        reconnectManager = new ReconnectManager(this);
        // Create os client
        osClient = new ServerClient(this);

        if (isUIEnabled()) {
            uiController = new UIController(this);
            if (ConfigurationEntry.USER_INTERFACE_LOCKED.getValueBoolean(this))
            {
                // Don't let the user pass this step.
                new UIUnLockDialog(this).openAndWait();
            }
        }

        setLoadingCompletion(10, 20);

        // The io provider.
        ioProvider = new IOProvider(this);
        ioProvider.start();

        // initialize dyndns manager
        dyndnsManager = new DynDnsManager(this);

        setLoadingCompletion(20, 30);

        // initialize listener on local port
        if (!initializeListenerOnLocalPort()) {
            return;
        }
        if (!isUIEnabled()) {
            // Disable silent mode
            silentMode = false;
        }

        setLoadingCompletion(30, 35);

        // Start the nodemanager
        if (!ProUtil.isRunningProVersion()) {
            // Nodemanager gets later (re) started by ProLoader.
            nodeManager.start();
        }

        setLoadingCompletion(35, 60);
        securityManager = new SecurityManagerClient(getController(), osClient);

        // init repo (read folders)
        folderRepository.init();
        // init of folders takes rather long so a big difference with
        // last number to get smooth bar... ;-)
        setLoadingCompletion(60, 65);

        // start repo maintainance Thread
        folderRepository.start();
        setLoadingCompletion(65, 70);

        // Start the transfer manager thread
        transferManager.start();
        setLoadingCompletion(70, 75);

        // Initalize rcon manager
        startRConManager();

        setLoadingCompletion(75, 80);

        // Start all configured listener if not in silent mode
        startConfiguredListener();
        setLoadingCompletion(80, 85);

        // open broadcast listener
        openBroadcastManager();
        setLoadingCompletion(85, 90);

        // Controller now started
        started = true;
        startTime = new Date();

        // Now taskmanager
        taskManager.start();

        logInfo("Controller started");

        // dyndns updater
        /*
         * boolean onStartUpdate = ConfigurationEntry.DYNDNS_AUTO_UPDATE
         * .getValueBoolean(this).booleanValue(); if (onStartUpdate) {
         * getDynDnsManager().onStartUpdate(); }
         */
        dyndnsManager.updateIfNessesary();

        setLoadingCompletion(90, 100);

        // Start Plugins
        pluginManager.start();

        // open UI
        if (isConsoleMode()) {
            logFine("Running in console");
        } else {
            logFine("Opening UI");
            openUI();
        }

        // Load anything that was not handled last time.
        loadPersistentObjects();

        setLoadingCompletion(100, 100);
        if (!isConsoleMode()) {
            uiController.hideSplash();
        }

        if (ConfigurationEntry.AUTO_CONNECT.getValueBoolean(this)) {
            // Now start the connecting process
            reconnectManager.start();
        } else {
            logFine("Not starting reconnection process. "
                + "Config auto.connect set to false");
        }
        // Start connecting to OS client.
        if (Feature.OS_CLIENT.isEnabled()) {
            if (ConfigurationEntry.SERVER_CONNECT_USERNAME
                .hasValue(getController()))
            {
                String username = ConfigurationEntry.SERVER_CONNECT_USERNAME
                    .getValue(getController());
                String password = ConfigurationEntry.SERVER_CONNECT_PASSWORD
                    .getValue(getController());
                logWarning("Logging into server " + osClient.getServerString()
                    + ". Username: " + username);
                osClient.login(username, Util.toCharArray(password));
            } else {
                osClient.loginWithLastKnown();
            }

            osClient.start();
        } else {
            logWarning("Not starting Online Storage (reconnection), "
                + "feature disable");
        }

        // Setup our background working tasks
        setupPeriodicalTasks();
    }

    /**
     * Add ask for friend listener.
     * 
     * @param l
     */
    public void addAskForFriendshipListener(AskForFriendshipListener l) {
        askForFriendshipListeners.add(l);
    }

    /**
     * Remove ask for friend listener.
     * 
     * @param l
     */
    public void removeAskForFriendshipListener(AskForFriendshipListener l) {
        askForFriendshipListeners.remove(l);
    }

    /**
     * Add invitation listener.
     * 
     * @param l
     */
    public void addInvitationHandler(InvitationHandler l) {
        invitationHandlers.add(l);
    }

    /**
     * Remove invitation listener.
     * 
     * @param l
     */
    public void removeInvitationHandler(InvitationHandler l) {
        invitationHandlers.remove(l);
    }

    /**
     * Add mass delete listener.
     * 
     * @param l
     */
    public void addMassDeletionHandler(MassDeletionHandler l) {
        massDeletionHandlers.add(l);
    }

    /**
     * Remove mass delete listener.
     * 
     * @param l
     */
    public void removeMassDeletionHandler(MassDeletionHandler l) {
        massDeletionHandlers.remove(l);
    }

    /**
     * Add single file offer listener.
     * 
     * @param l
     */
    public void addSingleFileOfferHandler(SingleFileOfferHandler l) {
        singleFileOfferHandlers.add(l);
    }

    /**
     * Add single file offer listener.
     * 
     * @param l
     */
    public void addWarningHandler(WarningHandler l) {
        warningHandlers.add(l);
    }

    /**
     * Remove single file offer listener.
     * 
     * @param l
     */
    public void removeSingleFileOfferHandler(SingleFileOfferHandler l) {
        singleFileOfferHandlers.remove(l);
    }

    private void setupProPlugins() {
        String pluginConfig = ConfigurationEntry.PLUGINS.getValue(this);
        boolean autoSetupPlugins = StringUtils.isEmpty(pluginConfig)
            || !pluginConfig.contains(Constants.PRO_LOADER_PLUGIN_CLASS);
        if (ProUtil.isRunningProVersion() && autoSetupPlugins) {
            logFine("Setting up pro loader");
            String newPluginConfig = Constants.PRO_LOADER_PLUGIN_CLASS;
            if (!StringUtils.isBlank(pluginConfig)) {
                newPluginConfig += ',' + pluginConfig;
            }
            ConfigurationEntry.PLUGINS.setValue(getController(),
                newPluginConfig);
        }
    }

    private void initLogger() {

        // Set a nice prefix for file looging file names.
        String configName = getConfigName();
        if (configName != null) {
            LoggingManager.setPrefix(configName);
        }

        // Enabled verbose mode if in config.
        // This logs to file for analysis.
        verbose = ConfigurationEntry.VERBOSE.getValueBoolean(getController());
        if (verbose) {
            String str = ConfigurationEntry.LOG_LEVEL_CONSOLE.getValue(this);
            Level consoleLevel = LoggingManager.levelForName(str);
            LoggingManager.setConsoleLogging(consoleLevel != null
                ? consoleLevel
                : Level.WARNING);

            // Enable file logging
            str = ConfigurationEntry.LOG_LEVEL_FILE.getValue(this);
            Level fileLevel = LoggingManager.levelForName(str);
            LoggingManager.setFileLogging(fileLevel != null
                ? fileLevel
                : Level.FINE);

            // Switch on the document handler.
            if (isUIEnabled()) {
                str = PreferencesEntry.DOCUMENT_LOGGING.getValueString(this);
                Level uiLogLevel = LoggingManager.levelForName(str);
                LoggingManager.setDocumentLogging(uiLogLevel != null
                    ? uiLogLevel
                    : Level.WARNING, this);
            }

            if (LoggingManager.isLogToFile()) {
                logInfo("Running in VERBOSE mode, logging to file '"
                    + LoggingManager.getLoggingFileName() + '\'');
            } else {
                logInfo("Running in VERBOSE mode, no logging to file");
            }
            Profiling.setEnabled(false);
            Profiling.reset();
        }

        if (commandLine != null && commandLine.hasOption('l')) {
            String str = commandLine.getOptionValue('l');
            Level consoleLevel = LoggingManager.levelForName(str);
            if (consoleLevel != null) {
                LoggingManager.setConsoleLogging(consoleLevel);
            }
        }

        // Enable debug reports.
        debugReports = ConfigurationEntry.DEBUG_REPORTS
            .getValueBoolean(getController());
    }

    /**
     * Loads a config file (located in "getConfigLocationBase()")
     * 
     * @param theFilename
     * @return false if unsuccesfull, true if file found and reading succeded.
     */
    private boolean loadConfigFile(String theFilename) {
        String filename = theFilename;
        if (filename == null) {
            filename = DEFAULT_CONFIG_FILE;
        }

        if (filename.indexOf('.') < 0) {
            // append .config extension
            filename += ".config";
        }

        configFilename = null;
        config = new Properties();
        BufferedInputStream bis = null;
        try {
            configFilename = filename;
            configFile = new File(getConfigLocationBase(), filename);
            if (configFile.exists()) {
                logInfo("Loading configfile " + configFile);
            } else {
                logInfo("Config file does not exist. " + configFile);
            }
            if (OSUtil.isWebStart()) {
                logFine("WebStart, config file location: "
                    + configFile.getAbsolutePath());
            }
            bis = new BufferedInputStream(new FileInputStream(configFile));
            config.load(bis);
            backupConfigIfV3();
        } catch (FileNotFoundException e) {
            logWarning("Unable to start config, file '" + filename
                + "' not found, using defaults");
        } catch (IOException e) {
            logSevere("Unable to start config from file '" + filename + '\'');
            config = null;
            return false;
        } finally {
            try {
                if (bis != null) {
                    bis.close();
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return true;
    }

    /**
     * This backs up version 3 config, because version 4 config is very
     * different and once converted, there is no going back.
     */
    @SuppressWarnings("unchecked")
    private void backupConfigIfV3() {

        boolean v3 = false;
        for (Enumeration<String> en = (Enumeration<String>) config
            .propertyNames(); en.hasMoreElements();)
        {
            // Look for a v3 'folder.' entry.
            String propName = en.nextElement();
            if (propName.startsWith(FOLDER_SETTINGS_PREFIX_V3)) {
                v3 = true;
                break;
            }
        }

        // Found a V3 config file. Back it up before anything changes it.
        if (v3) {
            String backupFilename = configFilename + ".v3";
            File backupFile = new File(getConfigLocationBase(), backupFilename);

            // Do it only once.
            if (!backupFile.exists()) {
                try {
                    logInfo("Backing up version 3 config file to "
                        + backupFile.getAbsolutePath());
                    PropertiesUtil.saveConfig(backupFile, config,
                        "PowerFolder config file (v3 backup)");
                } catch (IOException e) {
                    logSevere("Unable to save v3 backup config", e);
                } catch (Exception e) {
                    // major problem , setting code is wrong
                    System.out
                        .println("major problem , v3 setting code is wrong");
                    e.printStackTrace();
                    logSevere("major problem , v3 setting code is wrong", e);
                }
            }
        }
    }

    /**
     * Use to schedule a lightweight short running task that gets repeated
     * periodically.
     * 
     * @param task
     *            the task to schedule
     * @param period
     *            the time in ms between executions
     */
    public void scheduleAndRepeat(Runnable task, long period) {
        if (!isShuttingDown()) {
            threadPool.scheduleWithFixedDelay(task, 0, period,
                TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Use to schedule a lightweight short running task that gets repeated
     * periodically.
     * 
     * @param task
     *            the task to schedule
     * @param initialDelay
     *            the initial delay in ms
     * @param period
     *            the time in ms between executions
     */
    public void scheduleAndRepeat(Runnable task, long initialDelay, long period)
    {
        if (!isShuttingDown()) {
            threadPool.scheduleWithFixedDelay(task, initialDelay, period,
                TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Use to schedule a lightweight short running task.
     * 
     * @param task
     *            the task to schedule
     * @param delay
     *            the initial delay in ms
     */
    public void schedule(Runnable task, long delay) {
        if (!isShuttingDown()) {
            threadPool.schedule(task, delay, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Removes a schduled task for the threadpool
     * 
     * @param task
     */
    public void removeScheduled(Runnable task) {
        if (!isShuttingDown()) {
            if (threadPool instanceof ScheduledThreadPoolExecutor) {
                ((ScheduledThreadPoolExecutor) threadPool).remove(task);
            } else {
                logSevere("Unable to remove scheduled task. Wrong threadpool. "
                    + task);
            }
        }
    }

    /**
     * Sets up the task, which should be executes periodically.
     */
    private void setupPeriodicalTasks() {
        // Test the connectivity after a while.
        LimitedConnectivityChecker.install(this);

        // Schedule a task to reconfigure the Logger file every day.
        Calendar cal = new GregorianCalendar();
        long now = cal.getTime().getTime();

        // Midnight tomorrow morning.
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.add(Calendar.DATE, 1);

        // Add a few seconds to be sure the file name definately is for
        // tomorrow.
        cal.add(Calendar.SECOND, 2);

        long midnight = cal.getTime().getTime();
        // How long to wait initially?
        long secondsToMidnight = (midnight - now) / 1000;
        logInfo("Initial log reconfigure in " + secondsToMidnight + " seconds");
        threadPool.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                logInfo("Reconfiguring logs for new day");
                initLogger();
                LoggingManager.resetFileLogging();
                logInfo("Reconfigured logs for new day");
            }
        }, secondsToMidnight, 24 * 3600, TimeUnit.SECONDS);

        if (Profiling.ENABLED) {
            threadPool.scheduleWithFixedDelay(new TimerTask() {
                @Override
                public void run() {
                    logFine(Profiling.dumpStats());
                }
            }, 0, 60L, TimeUnit.SECONDS);
        }
    }

    /**
     * creates and starts the Broadcast manager, will not be created if config
     * property disablebroadcasts=true
     */
    private void openBroadcastManager() {
        // TODO Make ConfigurationEntry!
        if (Boolean.valueOf(config.getProperty("disablebroadcasts"))) {
            logWarning("Auto-local subnet connection disabled");
        } else {
            try {
                broadcastManager = new BroadcastMananger(this);
                broadcastManager.start();
            } catch (ConnectionException e) {
                logSevere("Unable to open broadcast manager, you wont automatically join pf-network on local net: "
                    + e.getMessage());
                logSevere("ConnectionException", e);
            }
        }
    }

    /**
     * Starts the rcon manager
     */
    private void startRConManager() {
        if (RemoteCommandManager.hasRunningInstance()) {
            alreadyRunningCheck();
        }
        if (!ConfigurationEntry.NET_RCON_MANAGER
            .getValueBoolean(getController()))
        {
            logWarning("Not starting RemoteCommandManager");
            return;
        }
        rconManager = new RemoteCommandManager(this);
        rconManager.start();
    }

    /**
     * Starts a connection listener for each port found in config property
     * "port" ("," separeted), if "random-port" is set to "true" this "port"
     * entry will be ignored and a random port will be used (between 49152 and
     * 65535).
     */
    private boolean initializeListenerOnLocalPort() {
        if (ConfigurationEntry.NET_BIND_RANDOM_PORT
            .getValueBoolean(getController()))
        {
            bindRandomPort();
        } else {
            String ports = ConfigurationEntry.NET_BIND_PORT
                .getValue(getController());
            if ("0".equals(ports)) {
                logWarning("Not opening connection listener. (port=0)");
            } else {
                if (ports == null) {
                    ports = "-1";
                }
                StringTokenizer nizer = new StringTokenizer(ports, ",");
                while (nizer.hasMoreElements()) {
                    String portStr = nizer.nextToken();
                    try {
                        int port = Integer.parseInt(portStr);
                        boolean listenerOpened = openListener(port);
                        if (listenerOpened && connectionListener != null) {
                            // set reconnect on first successfull listener
                            nodeManager.getMySelf().getInfo()
                                .setConnectAddress(
                                    connectionListener.getAddress());
                        }
                        if (!listenerOpened && !isUIOpen()) {
                            logSevere("Couldn't bind to port " + port);
                            // exit(1);
                            // fatalStartError(Translation
                            // .getTranslation("dialog.bind_error"));
                            // return false; // Shouldn't reach this!
                        }
                    } catch (NumberFormatException e) {
                        logFine("Unable to read listener port ('" + portStr
                            + "') from config");
                    }
                }
                // If this is the GUI version we didn't kill the program yet,
                // even though
                // there might have been multiple failed tries.
                if (connectionListener == null) {
                    portBindFailureProblem(ports);
                }
            }
        }

        if (ConfigurationEntry.NET_FIREWALL_OPENPORT.getValueBoolean(this)) {
            if (FirewallUtil.isFirewallAccessible()
                && connectionListener != null)
            {
                Thread opener = new Thread(new Runnable() {
                    public void run() {
                        try {
                            logFine("Opening port on Firewall.");
                            FirewallUtil.openport(connectionListener.getPort());
                            portWasOpened = true;
                        } catch (IOException e) {
                            logWarning("Unable to open port "
                                + connectionListener.getPort()
                                + "/TCP in Windows Firewall. " + e);
                        }
                    }
                }, "Portopener");
                opener.start();
                try {
                    opener.join(12000);
                } catch (InterruptedException e) {
                    logSevere("Opening of ports failed: " + e);
                }
            }
        }
        return true;
    }

    /**
     * Call to notify the Controller of a problem while binding a required
     * listening socket.
     * 
     * @param ports
     */
    private void portBindFailureProblem(String ports) {
        if (!isUIEnabled()) {
            logSevere("Unable to open incoming port from the portlist: "
                + ports);
            exit(1);
            return;
        }

        // Must use JOptionPane here because there is no Controller yet for
        // DialogFactory!
        int response = JOptionPane
            .showOptionDialog(
                null,
                Translation.getTranslation("dialog.bind_error.option.text"),
                Translation.getTranslation("dialog.bind_error.option.title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                new String[]{
                    Translation
                        .getTranslation("dialog.bind_error.option.ignore"),
                    Translation.getTranslation("dialog.bind_error.option.exit")},
                0);
        switch (response) {
            case 1 :
                exit(0);
                break;
            default :
                bindRandomPort();
                break;
        }
    }

    /**
     * Tries to bind a random port
     */
    private void bindRandomPort() {
        if ((openListener(ConnectionListener.DEFAULT_PORT) || openListener(0))
            && connectionListener != null)
        {
            nodeManager.getMySelf().getInfo().setConnectAddress(
                connectionListener.getAddress());
        } else {
            logSevere("failed to open random port!!!");
            fatalStartError(Translation.getTranslation("dialog.bind_error"));
        }
    }

    /**
     * Starts all configures connection listener
     */
    private void startConfiguredListener() {
        // Start the connection listener
        if (connectionListener != null) {
            try {
                connectionListener.start();
            } catch (ConnectionException e) {
                logSevere("Problems starting listener " + connectionListener, e);
            }
            for (ConnectionListener additionalConnectionListener : additionalConnectionListeners)
            {
                try {
                    ConnectionListener addListener = additionalConnectionListener;
                    addListener.start();
                } catch (ConnectionException e) {
                    logSevere("Problems starting listener "
                        + connectionListener, e);
                }
            }
        }
    }

    /**
     * Saves the current config to disk
     */
    public synchronized void saveConfig() {
        if (!isStarted()) {
            return;
        }
        logFine("Saving config (" + getConfigName() + ".config)");
        File file = new File(getConfigLocationBase(), getConfigName()
            + ".config");
        File backupFile = new File(getConfigLocationBase(), getConfigName()
            + ".config.backup");
        try {
            // make backup
            if (file.exists()) {
                FileUtils.copyFile(file, backupFile);
            }
            // Store config in misc base
            PropertiesUtil.saveConfig(file, config,
                "PowerFolder config file (v" + PROGRAM_VERSION + ')');
        } catch (IOException e) {
            logSevere("Unable to save config", e);
        } catch (Exception e) {
            // major problem , setting code is wrong
            System.out.println("major problem , setting code is wrong");
            e.printStackTrace();
            logSevere("major problem , setting code is wrong", e);
            // restore old settings file because it was probably flushed with
            // this error
            try {
                FileUtils.copyFile(backupFile, file);
            } catch (Exception e2) {

            }
        }
    }

    /**
     * Answers if controller is started (by config)
     * 
     * @return true if controller is started (by config)
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * @return true is shutdown still in progress
     */
    public boolean isShuttingDown() {
        return shuttingDown;
    }

    /**
     * the uptime in milliseconds.
     * 
     * @return The uptime time in millis, or -1 if not started yet
     */
    public long getUptime() {
        if (startTime == null) {
            return -1;
        }
        return System.currentTimeMillis() - startTime.getTime();
    }

    /**
     * @return Name of the JAR file on windows installations.
     */
    public String getJARName() {
        if (distribution != null && distribution.getBinaryName() != null) {
            return distribution.getBinaryName() + ".jar";
        }
        logSevere("Unable to get JAR name for distribution: " + distribution,
            new RuntimeException());
        return "PowerFolder.jar";
    }

    /**
     * @return Name of the L4J INI file on windows installations.
     */
    public String getL4JININame() {
        if (distribution != null && distribution.getBinaryName() != null) {
            return distribution.getBinaryName() + ".l4j.ini";
        }
        logSevere("Unable to get l4j.ini name for distribution: "
            + distribution);
        return "PowerFolder.l4j.ini";
    }

    /**
     *Sets the silent mode.
     * 
     * @param newSilentMode
     */
    public void setSilentMode(boolean newSilentMode) {
        boolean oldValue = isSilentMode();
        this.silentMode = newSilentMode;
        if (newSilentMode) {
            getFolderRepository().getFolderScanner().abortScan();
        }
        if (oldValue != newSilentMode) {
            getTransferManager().updateSpeedLimits();
        }
        preferences.putBoolean("silentMode", newSilentMode);
        firePropertyChange(PROPERTY_SILENT_MODE, oldValue, newSilentMode);
    }

    /**
     * @return true if the controller is running is silent mode.
     */
    public boolean isSilentMode() {
        return silentMode;
    }

    /**
     * Answers if node is running in LAN only networking mode
     * 
     * @return true if in LAN only mode else false
     */
    public boolean isLanOnly() {
        return getNetworkingMode() == NetworkingMode.LANONLYMODE;
    }

    /**
     * If this client is running in backup only mode.
     * <p>
     * Backup only client feature. Controls:
     * <p>
     * 1) If the client can send invitations
     * <p>
     * 2) If the client can add friends
     * <p>
     * 3) The client can connect to others except the server.
     * 
     * @return true if running as backup only client.
     */
    public boolean isBackupOnly() {
        return ConfigurationEntry.BACKUP_ONLY_CLIENT.getValueBoolean(this);
    }

    /**
     * returns the enum with the current networkin mode.
     * 
     * @return The Networking mode either NetworkingMode.PUBLICMODE,
     *         NetworkingMode.PRIVATEMODE or NetworkingMode.LANONLYMODE
     */
    public NetworkingMode getNetworkingMode() {
        if (networkingMode == null) {
            if (isBackupOnly()) {
                // ALWAYS server only mode.
                networkingMode = NetworkingMode.SERVERONLYMODE;
                return networkingMode;
            }
            // default = private
            String value = ConfigurationEntry.NETWORKING_MODE.getValue(this);
            try {
                networkingMode = NetworkingMode.valueOf(value);
            } catch (Exception e) {
                logSevere(
                    "Unable to read networking mode, reverting to PRIVATEMODE: "
                        + e.toString(), e);
                networkingMode = NetworkingMode.PRIVATEMODE;
            }
        }
        return networkingMode;
    }

    public void setNetworkingMode(NetworkingMode newMode) {
        if (isBackupOnly() && newMode != NetworkingMode.SERVERONLYMODE) {
            // ALWAYS server only mode if backup-only.
            newMode = NetworkingMode.SERVERONLYMODE;
            logWarning("Backup only client. Only supports server only networking mode");
        }
        logFine("setNetworkingMode: " + newMode);
        NetworkingMode oldValue = getNetworkingMode();
        if (newMode != oldValue) {
            ConfigurationEntry.NETWORKING_MODE.setValue(this, newMode.name());

            networkingMode = newMode;
            firePropertyChange(PROPERTY_NETWORKING_MODE, oldValue, newMode
                .toString());

            // Restart nodeManager
            nodeManager.shutdown();
            nodeManager.start();
            reconnectManager.buildReconnectionQueue();

            if (isUIEnabled()) {
                uiController.getMainFrame().setNetworkingModeStatus(newMode);
            }
        }
    }

    /**
     * Answers if this controller has restricted connection to the network
     * 
     * @return true if no incomming connections, else false.
     */
    public boolean isLimitedConnectivity() {
        return limitedConnectivity;
    }

    public void setLimitedConnectivity(boolean limitedConnectivity) {
        Object oldValue = isLimitedConnectivity();
        this.limitedConnectivity = limitedConnectivity;
        firePropertyChange(PROPERTY_LIMITED_CONNECTIVITY, oldValue,
            this.limitedConnectivity);
    }

    /**
     * Closes the currently connecting outgoing socket
     */
    public void closeCurrentConnectionTry() {
        // shut down current connection try
        if (currentConnectingSocket != null) {
            try {
                currentConnectingSocket.close();
            } catch (IOException e) {
                logFiner("IOException", e);
            }
        }
    }

    /**
     * Tries to shutdown the controller and exits to system with the given
     * status. May be canceled by user intervention when folders are still
     * syncing.
     * 
     * @param status
     */
    public void tryToExit(int status) {
        if (status == 0) { // only on normal shutdown
            if (isShutDownAllowed()) {
                shutdown();
                System.exit(status);
            } else {
                logWarning("not allow shutdown");
            }
        } else {
            shutdown();
            System.exit(status);
        }
    }

    /**
     * Shutsdown controller and exits to system with the given status
     * 
     * @param status
     */
    public void exit(int status) {
        if (Feature.EXIT_ON_SHUTDOWN.isDisabled()) {
            System.err
                .println("Running in JUnit testmode, no system.exit() called");
            return;
        }
        shutdown();
        System.exit(status);
    }

    /**
     * Shuts down the controller and requests and moves into restart requested
     * state
     */
    public void shutdownAndRequestRestart() {
        shuttingDown = true;
        restartRequested = true;
        shutdown();
    }

    /**
     * @return true if the controller was shut down, with the request to restart
     */
    public boolean isRestartRequested() {
        return restartRequested;
    }

    /** do we allow a normal shutdown as this time? */
    private boolean isShutDownAllowed() {
        return folderRepository.isShutdownAllowed();
    }

    /**
     * Shuts down all activities of this controller
     */
    public synchronized void shutdown() {
        shuttingDown = true;
        logInfo("Shutting down...");
        // if (started && !OSUtil.isSystemService()) {
        // // Save config need a started in that method so do that first
        // saveConfig();
        // }

        if (Profiling.isEnabled()) {
            logFine(Profiling.dumpStats());
        }

        // stop
        boolean wasStarted = started;
        started = false;
        startTime = null;

        if (taskManager != null) {
            logFine("Shutting down task manager");
            taskManager.shutdown();
        }

        // Save anything important that has not bben handled.
        savePersistentObjects();

        if (threadPool != null) {
            logFine("Shutting down global threadpool");
            threadPool.shutdownNow();
        }

        // shut down current connection try
        closeCurrentConnectionTry();

        if (isUIOpen()) {
            logFine("Shutting down UI");
            uiController.shutdown();
        }

        if ((portWasOpened || ConfigurationEntry.NET_FIREWALL_OPENPORT
            .getValueBoolean(this))
            && connectionListener != null)
        {
            if (FirewallUtil.isFirewallAccessible()) {
                Thread closer = new Thread(new Runnable() {
                    public void run() {
                        try {
                            logFine("Closing port on Firewall.");
                            FirewallUtil
                                .closeport(connectionListener.getPort());
                        } catch (IOException e) {
                            logWarning("Unable to remove firewall rule in Windows Firewall. "
                                + e);
                        }
                    }
                }, "Firewallcloser");
                closer.start();
                try {
                    closer.join(12000);
                } catch (InterruptedException e) {
                    logSevere("Closing of listener port failed: " + e);
                }
            }
        }

        if (rconManager != null) {
            logFine("Shutting down RConManager");
            rconManager.shutdown();
        }

        logFine("Shutting down connection listener(s)");
        if (connectionListener != null) {
            connectionListener.shutdown();
        }
        for (ConnectionListener addListener : additionalConnectionListeners) {
            addListener.shutdown();
        }
        additionalConnectionListeners.clear();
        if (broadcastManager != null) {
            logFine("Shutting down broadcast manager");
            broadcastManager.shutdown();
        }

        if (transferManager != null) {
            logFine("Shutting down transfer manager");
            transferManager.shutdown();
        }

        if (nodeManager != null) {
            logFine("Shutting down node manager");
            nodeManager.shutdown();
        }

        if (ioProvider != null) {
            logFine("Shutting down io provider");
            ioProvider.shutdown();
        }

        // shut down folder repository
        if (folderRepository != null) {
            logFine("Shutting down folder repository");
            folderRepository.shutdown();
        }

        if (reconnectManager != null) {
            logFine("Shutting down reconnect manager");
            reconnectManager.shutdown();
        }

        if (pluginManager != null) {
            logFine("Shutting down plugin manager");
            pluginManager.shutdown();
        }

        if (wasStarted) {
            System.out.println("------------ PowerFolder "
                + Controller.PROGRAM_VERSION
                + " Controller Shutdown ------------");
        }

        // remove current config
        // config = null;
        shuttingDown = false;
        logInfo("Shutting down done");
    }

    public ScheduledExecutorService getThreadPool() {
        return threadPool;
    }

    /**
     * Returns a debug report
     * 
     * @return the Debug report.
     */
    public String getDebugReport() {
        return Debug.buildDebugReport(this);
    }

    /**
     * Writes the debug report to diks
     */
    public void writeDebugReport() {
        try {
            FileOutputStream fOut = new FileOutputStream(getConfigName()
                + ".report.txt");
            String report = getDebugReport();
            fOut.write(report.getBytes());
            fOut.close();
        } catch (FileNotFoundException e) {
            logSevere("FileNotFoundException", e);
        } catch (IOException e) {
            logSevere("IOException", e);
        }
    }

    /**
     * Answers the current config name loaded <configname>.properties
     * 
     * @return The name of the current config
     */
    public String getConfigName() {
        if (configFilename == null) {
            return null;
        }

        String configName = configFilename;
        int dot = configName.indexOf('.');
        if (dot > 0) {
            configName = configName.substring(0, dot);
        }
        return configName;
    }

    public File getConfigFile() {
        return configFile;
    }

    /**
     * Returns the config, read from the configfile.
     * 
     * @return the config as properties object
     */
    public Properties getConfig() {
        return config;
    }

    /**
     * Returns the command line of the start
     * 
     * @return The command line
     */
    public CommandLine getCommandLine() {
        return commandLine;
    }

    /**
     * Returns local preferences, Preferences are stored till the next start. On
     * windows they are stored in the registry.
     * 
     * @return The preferences
     */
    public Preferences getPreferences() {
        return preferences;
    }

    /**
     * @return the distribution of this client.
     */
    public Distribution getDistribution() {
        return distribution;
    }

    /**
     * @return the configured update settings for the current distribution
     */
    public Updater.UpdateSetting getUpdateSettings() {
        return distribution.createUpdateSettings();
    }

    /**
     * Answers the own identity, of course with no connection
     * 
     * @return a referens to the member object representing myself.
     */
    public Member getMySelf() {
        return nodeManager != null ? nodeManager.getMySelf() : null;
    }

    /**
     * Changes the nick and tells other nodes
     * 
     * @param newNick
     *            the new nick
     * @param saveConfig
     *            true if the config should be save directly otherwise you have
     *            to do it by hand
     */
    public void changeNick(String newNick, boolean saveConfig) {
        getMySelf().setNick(newNick);
        ConfigurationEntry.NICK.setValue(this, getMySelf().getNick());
        if (saveConfig) {
            saveConfig();
        }
        // broadcast nickchange
        getNodeManager().broadcastMessage(new SettingsChange(getMySelf()));
        if (isUIOpen()) {
            // Update title
            getUIController().getMainFrame().updateTitle();
        }
    }

    /**
     * @return the io provider.
     */
    public IOProvider getIOProvider() {
        return ioProvider;
    }

    /**
     * @return the Online Storage client.
     */
    public ServerClient getOSClient() {
        return osClient;
    }

    /**
     * Retruns the plugin manager
     * 
     * @return the plugin manager
     */
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    /**
     * Returns the dyndns manager
     * 
     * @return the dyndns manager
     */
    public DynDnsManager getDynDnsManager() {
        return dyndnsManager;
    }

    /**
     * Returns the broadcast manager
     * 
     * @return broadcast manager
     */
    public BroadcastMananger getBroadcastManager() {
        return broadcastManager;
    }

    /**
     * Returns the NodeManager
     * 
     * @return the NodeManager
     */
    public NodeManager getNodeManager() {
        return nodeManager;
    }

    public ReconnectManager getReconnectManager() {
        return reconnectManager;
    }

    public PersistentTaskManager getTaskManager() {
        return taskManager;
    }

    /**
     * Returns the folder repository
     * 
     * @return the folder repository
     */
    public FolderRepository getFolderRepository() {
        return folderRepository;
    }

    /**
     * Returns the transfer manager of the controller
     * 
     * @return transfer manager
     */
    public TransferManager getTransferManager() {
        return transferManager;
    }

    /**
     * ONLY USE THIS METHOD FOR TESTING PURPOSES!
     * 
     * @param factory
     */
    public void setTransferManagerFactory(Callable<TransferManager> factory) {
        Reject.ifNull(factory, "TransferManager factory is null");
        if (transferManager != null) {
            throw new IllegalStateException("TransferManager was already set!");
        }
        transferManagerFactory = factory;
    }

    public SecurityManager getSecurityManager() {
        return securityManager;
    }

    /**
     * Injects a security manager.
     * 
     * @param securityManager
     *            the security manager to set.
     */
    public void setSecurityManager(SecurityManager securityManager) {
        logFiner("Security manager set: " + securityManager);
        this.securityManager = securityManager;
    }

    /**
     * Connects to a remote peer, with ip and port
     * 
     * @param address
     * @return the node that connected
     * @throws ConnectionException
     *             if connection failed
     * @returns the connected node
     */
    public Member connect(InetSocketAddress address) throws ConnectionException
    {
        if (!isStarted()) {
            logInfo("NOT Connecting to " + address + ". Controller not started");
            throw new ConnectionException("NOT Connecting to " + address
                + ". Controller not started");
        }

        if (address.getPort() <= 0) {
            // connect to defaul port
            logWarning("Unable to connect, port illegal " + address.getPort());
        }
        logInfo("Connecting to " + address + "...");

        ConnectionHandler conHan = ioProvider.getConnectionHandlerFactory()
            .tryToConnect(address);

        // Accept new node
        return getNodeManager().acceptConnection(conHan);
    }

    /**
     * Connect to a remote node Interprets a string as connection string Format
     * is expeced as ' <connect host>' or ' <connect host>: <port>'
     * 
     * @param connectStr
     * @return the member that connected under the given addresse
     * @throws ConnectionException
     * @returns the connected node
     */
    public Member connect(final String connectStr) throws ConnectionException {
        return connect(Util.parseConnectionString(connectStr));
    }

    /**
     * Answers if controller is started in console mode
     * 
     * @return true if in console mode
     */
    public boolean isConsoleMode() {
        if (commandLine != null) {
            return commandLine.hasOption('s');
        }
        if (config != null) {
            return ConfigurationEntry.DISABLE_GUI.getValueBoolean(this);
        }
        return GraphicsEnvironment.isHeadless();
    }

    /**
     * Whether to display notifications bottom-left instead of the normal
     * bottom-right. Primarily a development switch for running two PFs on one
     * PC.
     * 
     * @return true if notifications should be displayed on the left.
     */
    public boolean isNotifyLeft() {
        return commandLine != null && commandLine.hasOption('y');
    }

    /**
     * Opens the graphical user interface
     */
    private void openUI() {
        uiController.start();
    }

    /**
     * Answers if the user interface (ui) is enabled
     * 
     * @return true if the user interface is enabled, else false
     */
    public boolean isUIEnabled() {
        return !isConsoleMode();
    }

    /**
     * Answers if we have the ui open
     * 
     * @return true if the uiserinterface is actualy started
     */
    public boolean isUIOpen() {
        return uiController != null && uiController.isStarted();
    }

    /**
     * Exposing UIController, acces to all UserInterface elements
     * 
     * @return the UIController
     */
    public UIController getUIController() {
        return uiController;
    }

    /**
     * Waits for the ui to open, afterwards it is guranteed that uicontroller is
     * started
     */
    public void waitForUIOpen() {
        if (!isUIEnabled()) {
            throw new IllegalStateException(
                "Unable to ui to open, ui is not enabled");
        }
        while (!isUIOpen()) {
            try {
                // Wait....
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logFiner("InterruptedException", e);
                break;
            }
        }
    }

    /**
     * Opens the listener on local port. The first listener is set to
     * "connectionListener". All others are added the the list of
     * additionalConnectionListeners.
     * 
     * @return if succeeded
     */
    private boolean openListener(int port) {
        String bind = ConfigurationEntry.NET_BIND_ADDRESS
            .getValue(getController());
        logFine("Opening incoming connection listener on port " + port
            + " on interface " + (bind != null ? bind : "(all)"));
        while (true) {
            try {
                ConnectionListener newListener = new ConnectionListener(this,
                    port, bind);
                if (connectionListener == null) {
                    // its our primary listener
                    connectionListener = newListener;
                } else {
                    additionalConnectionListeners.add(newListener);
                }
                return true;
            } catch (ConnectionException e) {
                logWarning("Unable to bind to port " + port);
                logFiner("ConnectionException", e);
                if (bind != null) {
                    logSevere("This could've been caused by a binding error on the interface... Retrying without binding");
                    bind = null;
                } else { // Already tried binding once or not at all so get
                    // out
                    return false;
                }
            }
        }
    }

    /**
     * Do we have a connection listener?
     * 
     * @return true if we have a connection listener, otherwise false
     */
    public boolean hasConnectionListener() {
        return connectionListener != null;
    }

    /**
     * Answers the connection listener
     * 
     * @return the connection listener
     */
    public ConnectionListener getConnectionListener() {
        return connectionListener;
    }

    /**
     * Answers if this controller is runing in verbose mode. Set verbose=true on
     * config file to enable this, this gives access to all kinds of debugging
     * stuff.
     * 
     * @return true if we are in verbose mode
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Answers if debug reports should be requested. Set debugReports=true on
     * config file to enable this, this request node information. Only enabled
     * if in verbose mode.
     * 
     * @see RequestNodeInformation
     * @return true if we are in verbose mode
     */
    public boolean isDebugReports() {
        return debugReports && verbose;
    }

    /**
     * Returns the buildtime of this jar
     * 
     * @return the Date the application jar was build.
     */
    public Date getBuildTime() {
        File jar = new File(getJARName());
        if (jar.exists()) {
            return new Date(jar.lastModified());
        }
        return null;
    }

    /**
     * Sets the loading completion of this controller. Used in the splash
     * screen.
     * 
     * @param percentage
     *            the percentage complete
     */
    private void setLoadingCompletion(int percentage, int nextPerc) {
        if (uiController != null) {
            uiController.setLoadingCompletion(percentage, nextPerc);
        }
    }

    /**
     * Answers if minimized start is wanted. Use startup option -m to enable
     * this.
     * 
     * @return if a minimized startup should be performed.
     */
    public boolean isStartMinimized() {
        return commandLine != null && commandLine.hasOption('m');
    }

    /**
     * The base directory where to store/load config files. or null if on
     * working path
     * 
     * @return The File object representing the absolute location of where the
     *         config files are/should be stored.
     */
    private File getConfigLocationBase() {
        // First check if we have a config file in local path
        File aConfigFile = new File(getConfigName() + ".config");

        // Load configuration in misc file if config file if in
        if (OSUtil.isWebStart() || !aConfigFile.exists()) {
            logFine("Config location base: "
                + getMiscFilesLocation().getAbsolutePath());
            return getMiscFilesLocation();
        }

        // Otherwise use local path as configuration base
        return null;
    }

    /**
     * Answers the path, where to load/store miscellanouse files created by
     * PowerFolder. e.g. .nodes files
     * 
     * @return the file base, a directory
     */
    public static File getMiscFilesLocation() {
        File base;
        File unixConfigDir = new File(System.getProperty("user.home")
            + "/.PowerFolder");
        if (OSUtil.isWindowsSystem()) {
            String appData;
            if (Feature.CONFIGURATION_ALL_USERS.isEnabled()) {
                appData = WinUtils.getAppDataAllUsers();
            } else {
                appData = WinUtils.getAppDataCurrentUser();
            }

            if (StringUtils.isBlank(appData)) {
                // Appdata not found. Fallback.
                return unixConfigDir;
            }

            File windowsConfigDir = new File(appData, "PowerFolder");
            base = windowsConfigDir;

            // Check if migration is necessary
            if (unixConfigDir.exists()) {
                boolean migrateConfig = false;
                if (windowsConfigDir.exists()) {
                    // APPDATA/PowerFolder does not yet contain a config file OR
                    migrateConfig = windowsConfigDir.list(new FilenameFilter() {
                        public boolean accept(File dir, String name) {
                            return name.endsWith("config");
                        }
                    }).length == 0;
                } else {
                    // Migrate if APPDATA/PowerFolder not existing yet.
                    migrateConfig = true;
                }

                if (migrateConfig) {
                    boolean migrationOk = migrateWindowsMiscLocation(
                        unixConfigDir, windowsConfigDir);
                    if (!migrationOk) {
                        // Fallback, migration failed.
                        base = unixConfigDir;
                    }
                }
            }
        } else {
            base = unixConfigDir;
        }
        if (!base.exists()) {
            if (!base.mkdirs()) {
                log.severe("Failed to create " + base.getAbsolutePath());
            }
        }
        return base;
    }

    /**
     * Migrate config dir (if necessary) in Windows from user.home to APPDATA.
     * Pre Version 4, the config was in 'user.home'/.PowerFolder.
     * 'APPDATA'/PowerFolder is a more normal Windows location for application
     * data.
     * 
     * @param unixBaseDir
     *            the old user.home based config directory.
     * @param windowsBaseDir
     *            the preferred APPDATA based config directory.
     */
    private static boolean migrateWindowsMiscLocation(File unixBaseDir,
        File windowsBaseDir)
    {
        if (!windowsBaseDir.exists() && windowsBaseDir.mkdirs()) {
            log.severe("Failed to create " + windowsBaseDir.getAbsolutePath());
        }
        try {
            FileUtils.recursiveCopy(unixBaseDir, windowsBaseDir);
            log.warning("Migrated config from " + unixBaseDir + " to "
                + windowsBaseDir);
            return true;
        } catch (IOException e) {
            log.warning("Failed to migrate config from " + unixBaseDir + " to "
                + windowsBaseDir + ". " + e);
            return false;
        }
    }

    /**
     * Answers the path, where to load/store temp files created by PowerFolder.
     * 
     * @return the file base, a directory
     */
    public static File getTempFilesLocation() {
        File base = new File(System.getProperty("java.io.tmpdir"));
        if (!base.exists()) {
            System.err.println("temp dir does not exsits");
            base.mkdirs();
        }
        return base;
    }

    /**
     * Called if controller has detected a already running instance
     */
    private void alreadyRunningCheck() {
        Component parent = null;
        if (isUIOpen()) {
            parent = uiController.getMainFrame().getUIComponent();
        }
        if (!isStartMinimized() && isUIEnabled() && !commandLine.hasOption('z'))
        {
            Object[] options = {
                Translation
                    .getTranslation("dialog.already_running.start_button"),
                Translation
                    .getTranslation("dialog.already_running.exit_button")};
            if (JOptionPane.showOptionDialog(parent, Translation
                .getTranslation("dialog.already_running.warning"), Translation
                .getTranslation("dialog.already_running.title"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]) == 1)
            { // exit pressed
                exit(1);
            }
        } else {
            // If no gui show error but start anyways
            logWarning("PowerFolder already running");
        }
    }

    private void fatalStartError(String message) {
        Component parent = null;
        if (isUIOpen()) {
            parent = uiController.getMainFrame().getUIComponent();
        }
        if (isUIEnabled()) {
            Object[] options = new Object[]{Translation
                .getTranslation("dialog.already_running.exit_button")};
            JOptionPane.showOptionDialog(parent, message, Translation
                .getTranslation("dialog.fatal_error.title"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null,
                options, options[0]);
        } else {
            logSevere(message);
        }
        exit(1);
    }

    private void initDistribution() {
        try {
            ServiceLoader<Distribution> brandingLoader = ServiceLoader
                .load(Distribution.class);
            for (Distribution br : brandingLoader) {
                if (distribution != null) {
                    logSevere("Found multiple distribution classes: "
                        + br.getName() + ", got already "
                        + distribution.getName());
                }
                distribution = br;
            }
            if (distribution == null) {
                if (ProUtil.isRunningProVersion()) {
                    distribution = new PowerFolderPro();
                } else {
                    distribution = new PowerFolderBasic();
                }
                logFine("Distributon not found. Falling back to "
                    + distribution.getName());
            }
            logInfo("Running distribution: " + distribution.getName());
            distribution.init(this);
        } catch (Exception e) {
            logSevere("Failed to initialize distribution "
                + (distribution == null ? "null" : distribution.getName()), e);

            // Fallback
            try {
                if (distribution == null) {
                    if (ProUtil.isRunningProVersion()) {
                        distribution = new PowerFolderPro();
                    } else {
                        distribution = new PowerFolderBasic();
                    }
                }
                logInfo("Running distribution: " + distribution.getName());
                distribution.init(this);
            } catch (Exception e2) {
                logSevere("Failed to initialize fallback distribution", e2);
            }
        }
    }

    /**
     * Answers the waittime for threads time differst a bit to avoid
     * concurrencies
     * 
     * @return The time to wait
     */
    public static long getWaitTime() {
        return WAIT_TIME;
    }

    public String toString() {
        return "Controller '" + getMySelf() + '\'';
    }

    /**
     * Distribute ask for friendship events.
     * 
     * @param event
     */
    public void addAskForFriendship(AskForFriendshipEvent event) {
        if (networkingMode.equals(NetworkingMode.SERVERONLYMODE)) {
            logFine("Ignoring ask for friendship from client "
                + event.getMemberInfo() + ". Running in server only mode");
            return;
        }
        for (AskForFriendshipListener listener : askForFriendshipListeners) {
            listener.askForFriendship(event);
        }
    }

    /**
     * Distribute invitations.
     * 
     * @param invitation
     * @param sendIfJoined
     */
    public void invitationReceived(Invitation invitation, boolean sendIfJoined)
    {
        for (InvitationHandler handler : invitationHandlers) {
            handler.gotInvitation(invitation, sendIfJoined);
        }
    }

    /**
     * Distribute local mass deletion notifications.
     * 
     * @param event
     */
    public void localMassDeletionDetected(LocalMassDeletionEvent event) {
        for (MassDeletionHandler massDeletionHandler : massDeletionHandlers) {
            massDeletionHandler.localMassDeletion(event);
        }
    }

    /**
     * Distribute remote mass deletion notifications.
     * 
     * @param event
     */
    public void remoteMassDeletionDetected(RemoteMassDeletionEvent event) {
        for (MassDeletionHandler massDeletionHandler : massDeletionHandlers) {
            massDeletionHandler.remoteMassDeletion(event);
        }
    }

    /**
     * Process receipt of a SingleFileOffer.
     * 
     * @param singleFileOffer
     */
    public void singleFileOfferReceived(SingleFileOffer singleFileOffer) {

        if (!isUIEnabled()) {
            // This is a UI-only feature.
            return;
        }

        for (SingleFileOfferHandler handler : singleFileOfferHandlers) {
            handler.gotOffer(singleFileOffer);
        }

    }

    /**
     * Adds a warning event to the app model.
     * 
     * @param event
     */
    public void pushWarningEvent(WarningEvent event) {
        for (WarningHandler warningHandler : warningHandlers) {
            warningHandler.pushWarning(event);
        }
    }

    /**
     * Save anything important that was not handled.
     */
    private void savePersistentObjects() {

        if (started && isUIEnabled()) {

            // Save unhandled invitations.
            Invitation invitation;
            List<Invitation> invitations = new ArrayList<Invitation>();
            while ((invitation = uiController.getApplicationModel()
                .getReceivedInvitationsModel().popInvitation()) != null)
            {
                invitations.add(invitation);
            }
            String filename = getController().getConfigName() + ".invitations";
            File file = new File(getMiscFilesLocation(), filename);
            ObjectOutputStream outputStream = null;
            try {
                logInfo("There are " + invitations.size()
                    + " unhandled invitations.");
                outputStream = new ObjectOutputStream(new BufferedOutputStream(
                    new FileOutputStream(file)));
                outputStream.writeUnshared(invitations);
            } catch (FileNotFoundException e) {
                logSevere("FileNotFoundException", e);
            } catch (IOException e) {
                logSevere("IOException", e);
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        logSevere("IOException", e);
                    }
                }
            }
        }
    }

    /**
     * Load anything that was not handled last time.
     */
    @SuppressWarnings("unchecked")
    private void loadPersistentObjects() {

        if (isUIEnabled()) {

            // Load invitations.
            String filename = getController().getConfigName() + ".invitations";
            File file = new File(getMiscFilesLocation(), filename);
            if (file.exists()) {
                logInfo("Loading invitations");
                ObjectInputStream inputStream = null;
                try {
                    inputStream = new ObjectInputStream(
                        new BufferedInputStream(new FileInputStream(file)));
                    List<Invitation> invitations = (List<Invitation>) inputStream
                        .readObject();
                    inputStream.close();
                    for (Invitation invitation : invitations) {
                        uiController.getApplicationModel()
                            .getReceivedInvitationsModel().gotInvitation(
                                invitation, false);
                    }
                    logInfo("Loaded " + invitations.size() + " invitations.");
                } catch (FileNotFoundException e) {
                    logSevere("FileNotFoundException", e);
                } catch (IOException e) {
                    logSevere("IOException", e);
                } catch (ClassNotFoundException e) {
                    logSevere("ClassNotFoundException", e);
                } catch (ClassCastException e) {
                    logSevere("ClassCastException", e);
                } finally {
                    if (inputStream != null) {
                        try {
                            inputStream.close();
                        } catch (IOException e) {
                            logSevere("IOException", e);
                        }
                    }
                }
            } else {
                logInfo("No invitations found - probably first start of PF.");
            }
        }
    }

}