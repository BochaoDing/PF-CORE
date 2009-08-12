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
package de.dal33t.powerfolder.ui;

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UnsupportedLookAndFeelException;

import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.PFUIComponent;
import de.dal33t.powerfolder.PreferencesEntry;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.FolderRepository;
import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.event.FolderRepositoryEvent;
import de.dal33t.powerfolder.event.FolderRepositoryListener;
import de.dal33t.powerfolder.event.LocalMassDeletionEvent;
import de.dal33t.powerfolder.event.MassDeletionHandler;
import de.dal33t.powerfolder.event.RemoteMassDeletionEvent;
import de.dal33t.powerfolder.event.WarningEvent;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.light.MemberInfo;
import de.dal33t.powerfolder.skin.Skin;
import de.dal33t.powerfolder.ui.action.SyncAllFoldersAction;
import de.dal33t.powerfolder.ui.chat.ChatFrame;
import de.dal33t.powerfolder.ui.dialog.SingleFileTransferDialog;
import de.dal33t.powerfolder.ui.dialog.SyncFolderPanel;
import de.dal33t.powerfolder.ui.information.InformationCard;
import de.dal33t.powerfolder.ui.information.InformationFrame;
import de.dal33t.powerfolder.ui.model.ApplicationModel;
import de.dal33t.powerfolder.ui.model.TransferManagerModel;
import de.dal33t.powerfolder.ui.notification.NotificationHandler;
import de.dal33t.powerfolder.ui.render.MainFrameBlinkManager;
import de.dal33t.powerfolder.ui.render.SysTrayBlinkManager;
import de.dal33t.powerfolder.ui.wizard.PFWizard;
import de.dal33t.powerfolder.util.BrowserLauncher;
import de.dal33t.powerfolder.util.FileUtils;
import de.dal33t.powerfolder.util.Format;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.Util;
import de.dal33t.powerfolder.util.os.OSUtil;
import de.dal33t.powerfolder.util.ui.DialogFactory;
import de.dal33t.powerfolder.util.ui.GenericDialogType;
import de.dal33t.powerfolder.util.ui.UIUtil;
import de.dal33t.powerfolder.util.update.UIUpdateHandler;
import de.dal33t.powerfolder.util.update.Updater;
import de.dal33t.powerfolder.util.update.UpdaterHandler;

/**
 * The ui controller.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.86 $
 */
public class UIController extends PFComponent {

    private static final Logger log = Logger.getLogger(UIController.class
        .getName());
    private static final long TEN_GIG = 10L << 30;

    public static final int MAIN_FRAME_ID = 0;
    public static final int INFO_FRAME_ID = 1;
    public static final int CHAT_FRAME_ID = 2;
    public static final int WIZARD_DIALOG_ID = 3;

    private boolean started;
    private SplashScreen splash;
    private Image defaultIcon;
    private TrayIcon sysTrayMenu;
    private MainFrame mainFrame;
    private SystemMonitorFrame systemMonitorFrame;
    private InformationFrame informationFrame;
    private ChatFrame chatFrame;
    private WeakReference<JDialog> wizardDialogReference;

    // List of pending jobs, execute when ui is opend
    private final List<Runnable> pendingJobs;
    private Menu sysTrayFoldersMenu;

    // The root of all models
    private ApplicationModel applicationModel;

    private boolean seenOome;

    private TransferManagerModel transferManagerModel;

    private final AtomicBoolean folderRepositorySynchronizing;

    private final AtomicInteger activeFrame = new AtomicInteger();

    /**
     * The UI distribution running.
     */
    private Skin[] skins;

    private Skin activeSkin;

    /**
     * Initializes a new UI controller. open UI with #start
     * 
     * @param controller
     */
    public UIController(Controller controller) {
        super(controller);

        folderRepositorySynchronizing = new AtomicBoolean();

        configureOomeHandler();

        // Initialize look and feel / icon set
        initSkin();

        pendingJobs = Collections.synchronizedList(new LinkedList<Runnable>());

        if (!controller.isStartMinimized()) {
            // Show splash if not starting minimized
            try {
                EventQueue.invokeAndWait(new Runnable() {
                    public void run() {
                        logFiner("Opening splashscreen");
                        splash = new SplashScreen(getController(), 260 * 1000);
                    }
                });
            } catch (InterruptedException e) {
                logSevere("InterruptedException", e);
            } catch (InvocationTargetException e) {
                logSevere("InvocationTargetException", e);
            }
        }

        informationFrame = new InformationFrame(getController());
        chatFrame = new ChatFrame(getController());
        systemMonitorFrame = new SystemMonitorFrame(getController());
        getController().addMassDeletionHandler(new MyMassDeletionHandler());
        started = false;
    }

    /**
     * Configure a handler for OutOfMemoryErrors. Note that the Logger must be
     * configured to process Severe messages.
     */
    private void configureOomeHandler() {
        Handler oomeHandler = new Handler() {
            public void publish(LogRecord record) {
                Throwable throwable = record.getThrown();
                if (throwable instanceof OutOfMemoryError) {
                    OutOfMemoryError oome = (OutOfMemoryError) throwable;
                    showOutOfMemoryError(oome);
                }
            }

            public void flush() {
            }

            public void close() throws SecurityException {
            }
        };
        Logger logger = Logger.getLogger("");
        logger.addHandler(oomeHandler);
    }

    /**
     * Starts the UI
     */
    public void start() {
        if (getController().isVerbose()) {
            // EventDispatchThreadHangMonitor.initMonitoring();
            // RepaintManager
            // .setCurrentManager(new CheckThreadViolationRepaintManager());
        }

        // Hack for customer
        boolean openWiz = ConfigurationEntry.PREF_SHOW_FIRST_TIME_WIZARD
            .getValueBoolean(getController());
        if (!openWiz) {
            getController().getPreferences().putBoolean("openwizard2", false);
        }

        // The central application model
        applicationModel = new ApplicationModel(getController());
        applicationModel.initialize();

        // create the Frame
        mainFrame = new MainFrame(getController());

        // create the models
        getController().getFolderRepository().addFolderRepositoryListener(
            new MyFolderRepositoryListener());

        transferManagerModel = new TransferManagerModel(getController()
            .getTransferManager());
        transferManagerModel.initialize();

        if (OSUtil.isSystraySupported()) {
            initalizeSystray();
        } else {
            logWarning("System tray currently only supported on windows (>98)");
            mainFrame.getUIComponent().setDefaultCloseOperation(
                JFrame.EXIT_ON_CLOSE);
        }

        if (getController().isStartMinimized()) {
            logWarning("Starting minimized");
        }

        // Show main window
        try {
            EventQueue.invokeAndWait(new Runnable() {
                public void run() {
                    mainFrame.getUIComponent().setVisible(
                        !OSUtil.isSystraySupported()
                            || !getController().isStartMinimized());
                }
            });
        } catch (InterruptedException e) {
            logSevere("InterruptedException", e);
        } catch (InvocationTargetException e) {
            logSevere("InvocationTargetException", e);
        }

        chatFrame.initializeChatModelListener(applicationModel.getChatModel());

        started = true;

        // Process all pending runners
        synchronized (pendingJobs) {
            if (!pendingJobs.isEmpty()) {
                logFiner("Executing " + pendingJobs.size() + " pending ui jobs");
                for (Runnable runner : pendingJobs) {
                    SwingUtilities.invokeLater(runner);
                }
            }
        }

        // Open wizard on first start. PRO version has activation wizard first
        if (!Util.isRunningProVersion()
            && getController().getPreferences().getBoolean("openwizard2", true))
        {
            UIUtil.invokeLaterInEDT(new Runnable() {

                // Don't block start!
                public void run() {
                    hideSplash();
                    PFWizard.openBasicSetupWizard(getController());
                }
            });
        }

        // Goes to the home page if required.
        gotoHPIfRequired();
        detectAndShowLimitDialog();

        // Start the blinkers later, so the UI is fully displayed first.
        UIUtil.invokeLaterInEDT(new Runnable() {
            public void run() {
                new SysTrayBlinkManager(UIController.this);
                new MainFrameBlinkManager(UIController.this);
            }
        });

        UpdaterHandler updateHandler = new UIUpdateHandler(getController());
        Updater.installPeriodicalUpdateCheck(getController(), updateHandler);
    }

    private void gotoHPIfRequired() {
        if (Util.isRunningProVersion() && !Util.isTrial(getController())) {
            return;
        }
        String prefKey = "startCount" + Controller.PROGRAM_VERSION;
        int thisVersionStartCount = getController().getPreferences().getInt(
            prefKey, 0);
        // Go to HP every 20 starts
        if (thisVersionStartCount % 20 == 0) {
            try {
                BrowserLauncher.openURL(ConfigurationEntry.PROVIDER_BUY_URL
                    .getValue(getController()));
            } catch (IOException e1) {
                log.log(Level.WARNING, "Unable to goto PowerFolder homepage",
                    e1);
            }
        }
        thisVersionStartCount++;
        getController().getPreferences().putInt(prefKey, thisVersionStartCount);
    }

    private void detectAndShowLimitDialog() {
        if (Util.isRunningProVersion()) {
            return;
        }
        long totalFolderSize = calculateTotalLocalSharedSize();
        logFine("Local shared folder size: "
            + Format.formatBytes(totalFolderSize));
        boolean limitHit = totalFolderSize > TEN_GIG
            || getController().getFolderRepository().getFoldersCount() > 3;
        if (limitHit) {
            getController().getNodeManager().shutdown();
            getController().getIOProvider().shutdown();
            new FreeLimitationDialog(getController()).open();
        }
    }

    private long calculateTotalLocalSharedSize() {
        long totalSize = 0L;
        for (Folder folder : getController().getFolderRepository().getFolders())
        {
            totalSize += folder.getStatistic().getSize(
                getController().getMySelf());
        }
        return totalSize;
    }

    private void initalizeSystray() {
        defaultIcon = Icons.getImageById(Icons.SYSTRAY_DEFAULT);
        if (defaultIcon == null) {
            logSevere("Unable to retrieve default system tray icon. "
                + "System tray disabled");
            OSUtil.disableSystray();
            return;
        }
        sysTrayMenu = new TrayIcon(defaultIcon);
        sysTrayMenu.setImageAutoSize(true);
        sysTrayMenu.setToolTip(getController().getMySelf().getNick()
            + " | "
            + Translation.getTranslation("systray.powerfolder",
                Controller.PROGRAM_VERSION));
        PopupMenu menu = new PopupMenu();

        sysTrayMenu.setPopupMenu(menu);

        ActionListener systrayActionHandler = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if ("openui".equals(e.getActionCommand())) {
                    mainFrame.getUIComponent().setVisible(true);
                } else if ("hideui".equals(e.getActionCommand())) {
                    mainFrame.getUIComponent().setVisible(false);
                } else if ("exit".equals(e.getActionCommand())) {
                    // Exit to system
                    getController().tryToExit(0);
                } else if ("syncall".equals(e.getActionCommand())) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            SyncAllFoldersAction.perfomSync(getController());
                        }
                    });
                } else if ("gotohp".equals(e.getActionCommand())) {
                    try {
                        BrowserLauncher.openURL(ConfigurationEntry.PROVIDER_URL
                            .getValue(getController()));
                    } catch (IOException e1) {
                        log.log(Level.WARNING,
                            "Unable to goto PowerFolder homepage", e1);
                    }
                }
            }
        };
        MenuItem item = menu.add(new MenuItem("PowerFolder.com"));
        item.setActionCommand("gotohp");
        item.addActionListener(systrayActionHandler);

        menu.addSeparator();

        Menu notificationsMenu = new Menu(Translation
            .getTranslation("systray.notifications"));
        menu.add(notificationsMenu);
        notificationsMenu.addActionListener(systrayActionHandler);

        final CheckboxMenuItem chatMenuItem = new CheckboxMenuItem(Translation
            .getTranslation("systray.notifications.chat"));
        notificationsMenu.add(chatMenuItem);
        chatMenuItem.setState((Boolean) applicationModel
            .getChatNotificationsValueModel().getValue());
        chatMenuItem.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                applicationModel.getChatNotificationsValueModel().setValue(
                    chatMenuItem.getState());
            }
        });
        applicationModel.getChatNotificationsValueModel()
            .addValueChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    chatMenuItem.setState((Boolean) evt.getNewValue());
                }
            });

        final CheckboxMenuItem systemMenuItem = new CheckboxMenuItem(
            Translation.getTranslation("systray.notifications.system"));
        notificationsMenu.add(systemMenuItem);
        systemMenuItem.setState((Boolean) applicationModel
            .getSystemNotificationsValueModel().getValue());
        systemMenuItem.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                applicationModel.getSystemNotificationsValueModel().setValue(
                    systemMenuItem.getState());
            }
        });
        applicationModel.getSystemNotificationsValueModel()
            .addValueChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    systemMenuItem.setState((Boolean) evt.getNewValue());
                }
            });

        sysTrayFoldersMenu = new Menu(Translation
            .getTranslation("general.powerfolder"));
        sysTrayFoldersMenu.setEnabled(false);
        if (OSUtil.isMacOS() || OSUtil.isWindowsSystem()) {
            menu.add(sysTrayFoldersMenu);
            menu.addSeparator();
        }

        item = menu.add(new MenuItem(Translation
            .getTranslation("systray.sync_all")));
        item.setActionCommand("syncall");
        item.addActionListener(systrayActionHandler);

        final MenuItem opentUI = menu.add(new MenuItem(Translation
            .getTranslation("systray.show")));
        opentUI.setActionCommand("openui");
        opentUI.addActionListener(systrayActionHandler);

        menu.addSeparator();

        item = menu
            .add(new MenuItem(Translation.getTranslation("systray.exit")));
        item.setActionCommand("exit");
        item.addActionListener(systrayActionHandler);

        sysTrayMenu.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Previously was double click, this isn't supported by this
                // systray implementation
                // Double clicked, open gui directly
                mainFrame.getUIComponent().setVisible(true);
                mainFrame.getUIComponent().setState(Frame.NORMAL);
            }
        });

        try {
            SystemTray.getSystemTray().add(sysTrayMenu);
        } catch (AWTException e) {
            logSevere("AWTException", e);
            OSUtil.disableSystray();
            return;
        }
        getController().scheduleAndRepeat(new UpdateSystrayTask(), 5000L);

        // Switch Systray show/hide menuitem dynamically
        mainFrame.getUIComponent().addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent arg0) {
                opentUI.setLabel(Translation.getTranslation("systray.hide"));
                opentUI.setActionCommand("hideui");
            }

            public void componentHidden(ComponentEvent arg0) {
                opentUI.setLabel(Translation.getTranslation("systray.show"));
                opentUI.setActionCommand("openui");

            }
        });

        // Load initial folders in menu.
        for (Folder folder : getController().getFolderRepository().getFolders())
        {
            addFolderToSysTray(folder);
        }
    }

    /**
     * Add a folder to the SysTray menu structure.
     * 
     * @param folder
     */
    private void addFolderToSysTray(Folder folder) {
        MenuItem menuItem = new MenuItem(folder.getName());
        // Insert in the correct position.
        boolean done = false;
        for (int i = 0; i < sysTrayFoldersMenu.getItemCount(); i++) {
            if (sysTrayFoldersMenu.getItem(i).getLabel().toLowerCase()
                .compareTo(folder.getName().toLowerCase()) > 0)
            {
                sysTrayFoldersMenu.insert(menuItem, i);
                done = true;
                break;
            }
        }
        if (!done) {
            sysTrayFoldersMenu.add(menuItem);
        }
        sysTrayFoldersMenu.setEnabled(true);
        final File localBase = folder.getLocalBase();
        final String folderName = folder.getName();
        menuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (localBase.exists()) {
                    try {
                        FileUtils.openFile(localBase);
                    } catch (IOException e1) {
                        log.log(Level.WARNING, "Problem opening folder "
                            + folderName, e1);
                    }
                }
            }
        });
    }

    /**
     * Remove a folder from the SysTray menu structure.
     * 
     * @param folder
     */
    private void removeFolderFromSysTray(Folder folder) {
        for (int i = 0; i < sysTrayFoldersMenu.getItemCount(); i++) {
            MenuItem menuItem = sysTrayFoldersMenu.getItem(i);
            if (menuItem.getLabel().equals(folder.getName())) {
                sysTrayFoldersMenu.remove(i);
            }
        }
        if (sysTrayFoldersMenu.getItemCount() == 0) {
            sysTrayFoldersMenu.setEnabled(false);
        }
    }

    public void hideSplash() {
        if (splash != null) {
            splash.shutdown();
        }
    }

    public TransferManagerModel getTransferManagerModel() {
        return transferManagerModel;
    }

    /**
     * @return the available skins - may be empty;
     */
    public Skin[] getSkins() {
        return skins;
    }

    /**
     * @return the active skin - may be null.
     */
    public Skin getActiveSkin() {
        return activeSkin;
    }

    private void initSkin() {

        List<Skin> skinList = new ArrayList<Skin>();

        // Now all skins (defaults + additional skins)
        ServiceLoader<Skin> skinLoader = ServiceLoader.load(Skin.class);
        for (Skin sk : skinLoader) {
            logFine("Loading skin " + sk.getName());
            skinList.add(sk);
        }

        skins = new Skin[skinList.size()];
        int i = 0;
        for (Skin skin : skinList) {
            // Check for dupes.
            for (int j = 0; j < i; j++) {
                if (skins[j].getName().equals(skin.getName())) {
                    logSevere("Multiple skins with name: " + skin.getName());
                }
            }
            skins[i++] = skin;
        }

        String skinName = PreferencesEntry.SKIN_NAME
            .getValueString(getController());
        boolean found = false;
        for (Skin skin : skins) {
            if (skin.getName().equals(skinName)) {
                activeSkin = skin;
                found = true;
                break;
            }
        }
        if (!found) {
            // Can not find one with this name - use the first one.
            activeSkin = skins[0];
            PreferencesEntry.SKIN_NAME.setValue(getController(), activeSkin
                .getName());
        }

        String fileName = activeSkin.getIconsPropertiesFileName();
        Icons.loadOverrideFile(fileName);
        try {
            LookAndFeelSupport.setLookAndFeel((LookAndFeel) activeSkin
                .getLookAndFeel());
        } catch (UnsupportedLookAndFeelException e) {
            logSevere("Failed to set look and feel for skin "
                + activeSkin.getName(), e);
        } catch (ParseException e) {
            logSevere("Failed to set look and feel for skin "
                + activeSkin.getName(), e);
        }
    }

    /**
     * Shows an OutOfMemoryError to the user.
     * 
     * @param oome
     */
    public void showOutOfMemoryError(OutOfMemoryError oome) {
        if (!seenOome) {
            seenOome = true;
            int response = DialogFactory.genericDialog(getController(),
                Translation.getTranslation("low_memory.error.title"),
                Translation.getTranslation("low_memory.error.text"),
                new String[]{
                    Translation.getTranslation("general.ok"),
                    Translation
                        .getTranslation("dialog.already_running.exit_button")},
                0, GenericDialogType.ERROR);
            if (response == 1) { // Exit
                getController().exit(0);
            }
        }
    }

    /**
     * Displays the information window if not already displayed.
     */
    public void displaySystemMonitorWindow() {
        systemMonitorFrame.getUIComponent().setVisible(true);
    }

    /**
     * Displays the information window if not already displayed.
     */
    private void displayInformationWindow() {
        informationFrame.getUIComponent().setVisible(true);
    }

    /**
     * Opens the Files information for a folder.
     * 
     * @param folderInfo
     *            info of the folder to display files information for.
     */
    public void openFilesInformation(FolderInfo folderInfo) {
        openFilesInformation(folderInfo, Integer.MIN_VALUE);
    }

    /**
     * Opens the Files information for a folder.
     * 
     * @param folderInfo
     *            info of the folder to display files information for.
     */
    public void openFilesInformationLatest(FolderInfo folderInfo) {
        informationFrame.displayFolderFilesLatest(folderInfo);
        displayInformationWindow();
    }

    /**
     * Opens the Files information for a folder.
     * 
     * @param folderInfo
     *            info of the folder to display files information for.
     * @param directoryFilterMode
     *            the directory filter mode to be in
     */
    public void openFilesInformation(FolderInfo folderInfo,
        int directoryFilterMode)
    {
        informationFrame.displayFolderFiles(folderInfo, directoryFilterMode);
        displayInformationWindow();
    }

    /**
     * Opens the Settings information for a folder.
     * 
     * @param folderInfo
     *            info of the folder to display member settings information for.
     */
    public void openSettingsInformation(FolderInfo folderInfo) {
        informationFrame.displayFolderSettings(folderInfo);
        displayInformationWindow();
    }

    /**
     * Opens the Members information for a folder.
     * 
     * @param folderInfo
     *            info of the folder to display member computer information for.
     */
    public void openMembersInformation(FolderInfo folderInfo) {
        informationFrame.displayFolderMembers(folderInfo);
        displayInformationWindow();
    }

    /**
     * Opens the Problems information for a folder.
     * 
     * @param folderInfo
     *            info of the folder to display problems information for.
     */
    public void openProblemsInformation(FolderInfo folderInfo) {
        informationFrame.displayFolderProblems(folderInfo);
        displayInformationWindow();
    }

    /**
     * Opens the Files information for a folder.
     * 
     * @param memberInfo
     *            info of the folder to display files information for.
     */
    public void openChat(MemberInfo memberInfo) {
        if (memberInfo != null) {
            chatFrame.displayChat(memberInfo, true);
        }
        chatFrame.getUIComponent().setVisible(true);
    }

    public void openDownloadsInformation() {
        informationFrame.displayDownloads();
        displayInformationWindow();
    }

    public void openUploadsInformation() {
        informationFrame.displayUploads();
        displayInformationWindow();
    }

    public void openDebugInformation() {
        informationFrame.displayDebug();
        displayInformationWindow();
    }

    public void openInformationCard(InformationCard card) {
        informationFrame.displayCard(card);
        displayInformationWindow();
    }

    /**
     * Call when non-quitOnX close called. Hides child frames.
     */
    public void hideChildPanels() {
        informationFrame.getUIComponent().setVisible(false);
        chatFrame.getUIComponent().setVisible(false);
        systemMonitorFrame.getUIComponent().setVisible(false);
    }

    public void syncFolder(FolderInfo folderInfo) {
        Folder folder = getController().getFolderRepository().getFolder(
            folderInfo);

        if (SyncProfile.MANUAL_SYNCHRONIZATION.equals(folder.getSyncProfile()))
        {
            // Ask for more sync options on that folder if on project sync
            new SyncFolderPanel(getController(), folder).open();
        } else {

            // Let other nodes scan now!
            folder.broadcastScanCommand();

            // Recommend scan on this
            folder.recommendScanOnNextMaintenance();

            // Now trigger the scan
            getController().getFolderRepository().triggerMaintenance();

            // Trigger file requesting.
            getController().getFolderRepository().getFileRequestor()
                .triggerFileRequesting(folderInfo);
        }
    }

    /**
     * This method handles movement of the main frame and nudges any
     * MagneticFrames. USE_MAGNETIC_FRAMES pref xor control key activates this.
     * 
     * @param diffX
     * @param diffY
     */
    public void mainFrameMoved(boolean controlKeyDown, int diffX, int diffY) {

        Boolean magnetic = PreferencesEntry.USE_MAGNETIC_FRAMES
            .getValueBoolean(getController());
        if (magnetic ^ controlKeyDown) {
            informationFrame.nudge(diffX, diffY);
            chatFrame.nudge(diffX, diffY);
            systemMonitorFrame.nudge(diffX, diffY);
        }
    }

    /**
     * Handles single file transfer requests. Displays dialog to send offer to
     * member.
     * 
     * @param file
     * @param node
     */
    public void transferSingleFile(File file, Member node) {
        SingleFileTransferDialog sftd = new SingleFileTransferDialog(
            getController(), file, node);
        sftd.open();
    }

    /**
     * This returns most recently active PowerFolder frame. Possibly the
     * InformationFrame, ChatFrame or (default) MainFrame. Used by dialogs, so
     * focus does not always jump to the wrong (Main) frame.
     * 
     * @return
     */
    public Window getActiveFrame() {

        int f = activeFrame.get();
        if (f == INFO_FRAME_ID) {
            JFrame infoComponent = informationFrame.getUIComponent();
            if (infoComponent.isVisible()) {
                return infoComponent;
            }
        } else if (f == CHAT_FRAME_ID) {
            JFrame chatComponent = chatFrame.getUIComponent();
            if (chatComponent.isVisible()) {
                return chatComponent;
            }
        } else if (f == WIZARD_DIALOG_ID) {
            if (wizardDialogReference != null) {
                JDialog wizardDialog = wizardDialogReference.get();
                if (wizardDialog != null) {
                    return wizardDialog;
                }
            }
        }

        // Default - main frame
        return mainFrame.getUIComponent();
    }

    public void setActiveFrame(int activeFrameId) {
        activeFrame.set(activeFrameId);
    }

    public void setWizardDialogReference(JDialog wizardDialog) {
        wizardDialogReference = new WeakReference<JDialog>(wizardDialog);
    }

    /**
     * Hide the Online Storage lines in the home tab.
     */
    public void hideOSLines() {
        mainFrame.hideOSLines();
    }

    public boolean chatFrameVisible() {
        return chatFrame.getUIComponent().isVisible();
    }

    /**
     * Show the pending messages button in the status bar.
     * 
     * @param show
     */
    public void showPendingMessages(boolean show) {
        mainFrame.showPendingMessages(show);
    }

    // /////////////////
    // Inner Classes //
    // /////////////////

    private class UpdateSystrayTask extends TimerTask {
        public void run() {
            StringBuilder tooltip = new StringBuilder();

            tooltip.append(Translation.getTranslation("general.powerfolder"));
            tooltip.append(' ');
            if (folderRepositorySynchronizing.get()) {
                tooltip.append(Translation
                    .getTranslation("systray.tooltip.syncing"));
            } else {
                tooltip.append(Translation
                    .getTranslation("systray.tooltip.in_sync"));
            }
            double totalCPSdownKB = getController().getTransferManager()
                .getDownloadCounter().calculateAverageCPS() / 1024;
            double totalCPSupKB = getController().getTransferManager()
                .getUploadCounter().calculateAverageCPS() / 1024;

            String downText;

            if (totalCPSdownKB > 1024) {
                downText = Translation.getTranslation(
                    "systray.tooltip.down.mb", Format.getNumberFormat().format(
                        totalCPSdownKB / 1024));
            } else {
                downText = Translation.getTranslation("systray.tooltip.down",
                    Format.getNumberFormat().format(totalCPSdownKB));
            }

            String upText;
            if (totalCPSupKB > 1024) {
                upText = Translation.getTranslation("systray.tooltip.up.mb",
                    Format.getNumberFormat().format(totalCPSupKB / 1024));
            } else {
                upText = Translation.getTranslation("systray.tooltip.up",
                    Format.getNumberFormat().format(totalCPSupKB));
            }

            tooltip.append(' ' + upText + ' ' + downText);
            sysTrayMenu.setToolTip(tooltip.toString());
        }
    }

    /**
     * Shuts the ui down
     */
    public void shutdown() {
        hideSplash();

        if (started) {
            informationFrame.storeValues();
            informationFrame.getUIComponent().setVisible(false);
            informationFrame.getUIComponent().dispose();

            chatFrame.storeValues();
            chatFrame.getUIComponent().setVisible(false);
            chatFrame.getUIComponent().dispose();

            systemMonitorFrame.storeValues();
            systemMonitorFrame.getUIComponent().setVisible(false);
            systemMonitorFrame.getUIComponent().dispose();

            mainFrame.storeValues();
            mainFrame.getUIComponent().setVisible(false);
            mainFrame.getUIComponent().dispose();

            // Close systray
            if (OSUtil.isSystraySupported()) {
                SystemTray.getSystemTray().remove(sysTrayMenu);
            }
        }

        started = false;
    }

    /**
     * @return true if the ui controller is started
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * @return the controller
     */
    public Controller getController() {
        return super.getController();
    }

    /**
     * Sets the loading percentage
     * 
     * @param percentage
     * @param nextPerc
     */
    public void setLoadingCompletion(int percentage, int nextPerc) {
        if (splash != null) {
            splash.setCompletionPercentage(percentage, nextPerc);
        }
    }

    // Exposing ***************************************************************

    /**
     * @return the mainframe
     */
    public MainFrame getMainFrame() {
        return mainFrame;
    }

    /**
     * For a more convenience way you can also use
     * PFUIComponent.getApplicationModel()
     * 
     * @return the application model
     * @see PFUIComponent#getApplicationModel()
     */
    public ApplicationModel getApplicationModel() {
        return applicationModel;
    }

    // Systray interface/install code *****************************************

    /**
     * Sets the icon of the systray
     * 
     * @param icon
     */
    public synchronized void setTrayIcon(Image icon) {
        if (!OSUtil.isSystraySupported()) {
            return;
        }
        if (sysTrayMenu == null) {
            return;
        }
        if (icon == null) {
            sysTrayMenu.setImage(defaultIcon);
        } else {
            sysTrayMenu.setImage(icon);
        }
    }

    // Message dialog helpers *************************************************

    /**
     * Invokes a runner for later processing. It is ENSURED, that UI is open,
     * when the runner is executed
     * 
     * @param runner
     */
    public void invokeLater(Runnable runner) {
        if (started) {
            SwingUtilities.invokeLater(runner);
        } else {
            logFine("Added runner to pending jobs: " + runner);
            // Add to pending jobs
            pendingJobs.add(runner);
        }
    }

    private class MyFolderRepositoryListener implements
        FolderRepositoryListener
    {

        private final AtomicBoolean synchronizing = new AtomicBoolean();

        public void folderRemoved(FolderRepositoryEvent e) {
            removeFolderFromSysTray(e.getFolder());
            checkStatus();
        }

        public void folderCreated(FolderRepositoryEvent e) {
            addFolderToSysTray(e.getFolder());
            checkStatus();
        }

        public void maintenanceStarted(FolderRepositoryEvent e) {
            checkStatus();
        }

        public void maintenanceFinished(FolderRepositoryEvent e) {
            checkStatus();
        }

        public boolean fireInEventDispatchThread() {
            return true;
        }

        /**
         * Display folder synchronization info. A copy of the MyFolders quick
         * info panel text.
         */
        private void checkStatus() {
            long nTotalBytes = 0;
            FolderRepository repo = getController().getFolderRepository();
            Collection<Folder> folders = repo.getFolders();

            int synchronizingFolders = 0;
            for (Folder folder : folders) {
                if (folder.isTransferring()
                    || Double.compare(folder.getStatistic()
                        .getAverageSyncPercentage(), 100.0d) != 0)
                {
                    synchronizingFolders++;
                }
                nTotalBytes += folder.getStatistic().getTotalSize();
            }

            String text1;
            boolean changed = false;
            synchronized (synchronizing) {
                if (synchronizingFolders == 0) {
                    text1 = Translation
                        .getTranslation("check_status.in_sync_all");
                    if (synchronizing.get()) {
                        changed = true;
                        synchronizing.set(false);
                    }
                }

                else {
                    text1 = Translation.getTranslation("check_status.syncing",
                        String.valueOf(synchronizingFolders));
                    if (!synchronizing.get()) {
                        changed = true;
                        synchronizing.set(true);
                    }
                }
            }

            // Disabled popup of sync start.
            if (changed
                && ConfigurationEntry.SHOW_SYSTEM_NOTIFICATIONS
                    .getValueBoolean(getController()))
            {
                String text2 = Translation.getTranslation(
                    "check_status.powerfolders", Format
                        .formatBytes(nTotalBytes), String.valueOf(folders.size()));

                notifyMessage(Translation.getTranslation("check_status.title"),
                    text1 + "\n\n" + text2, false);
            }
        }
    }

    /**
     * Shows a notification message only if the UI is minimized.
     * 
     * @param title
     *            The title to display under 'PowerFolder'.
     * @param message
     *            Message to show if notification is displayed.
     * @param chat
     *            True if this is a chat message, otherwise it is a system
     *            message
     */
    public void notifyMessage(String title, String message, boolean chat) {
        if (started && mainFrame.isIconifiedOrHidden()
            && !getController().isShuttingDown())
        {
            if (chat
                ? (Boolean) applicationModel.getChatNotificationsValueModel()
                    .getValue()
                : (Boolean) applicationModel.getSystemNotificationsValueModel()
                    .getValue())
            {
                NotificationHandler notificationHandler = new NotificationHandler(
                    getController(), title, message, true);
                notificationHandler.show();
            }
        }
    }

    /**
     * Only use this for preview from the DialogSettingsTab. It by-passes all
     * the usual safty checks.
     * 
     * @param title
     * @param message
     */
    public void previewMessage(String title, String message) {

        NotificationHandler notificationHandler = new NotificationHandler(
            getController(), title, message, false);
        notificationHandler.show();
    }

    /**
     * Run a task via the notification system. If the UI is minimized, a
     * notification message will appear. If the user selects the accept button,
     * the task runs. If the UI is not minimized, the task runs anyway.
     * 
     * @param title
     *            The title to display under 'PowerFolder'.
     * @param message
     *            Message to show if notification is displayed.
     * @param task
     *            Task to do if user selects 'accept' option or if UI is not
     *            minimized.
     * @param runIfShown
     *            Whether to run the task if PF is already shown.
     */
    public void notifyMessage(String title, String message, TimerTask task,
        boolean runIfShown)
    {
        if (started
            && mainFrame.isIconifiedOrHidden()
            && !getController().isShuttingDown()
            && (Boolean) applicationModel.getSystemNotificationsValueModel()
                .getValue())
        {
            NotificationHandler notificationHandler = new NotificationHandler(
                getController(), title, message, task);
            notificationHandler.show();
        } else {
            if (runIfShown) {
                task.run();
            }
        }
    }

    /**
     * Class to handle local and remote mass deletion events. This pushes
     * warnings into the app model.
     */
    private class MyMassDeletionHandler implements MassDeletionHandler {
        public void localMassDeletion(final LocalMassDeletionEvent event) {
            WarningEvent warningEvent = new WarningEvent(new Runnable() {
                public void run() {
                    int response = DialogFactory
                        .genericDialog(
                            getController(),
                            Translation
                                .getTranslation("uicontroller.local_mass_delete.title"),
                            Translation.getTranslation(
                                "uicontroller.local_mass_delete.message", event
                                    .getFolderInfo().name),
                            new String[]{
                                Translation
                                    .getTranslation("uicontroller.local_mass_delete.broadcast_deletions"),
                                Translation
                                    .getTranslation("uicontroller.local_mass_delete.remove_folder_locally"),
                                Translation.getTranslation("general.close")},
                            0, GenericDialogType.WARN);
                    if (response == 0) {
                        // Broadcast deletions
                        FolderRepository folderRepository = getController()
                            .getFolderRepository();
                        Folder folder = folderRepository.getFolder(event
                            .getFolderInfo());
                        folder.scanLocalFiles(true);
                    } else if (response == 1) {
                        // Remove folder locally
                        FolderRepository folderRepository = getController()
                            .getFolderRepository();
                        Folder folder = folderRepository.getFolder(event
                            .getFolderInfo());
                        folderRepository.removeFolder(folder, false);
                    }
                }
            });
            applicationModel.getWarningsModel().pushWarning(warningEvent);
        }

        public void remoteMassDeletion(RemoteMassDeletionEvent event) {
            WarningEvent warningEvent = new WarningEvent(
                getController(),
                Translation
                    .getTranslation("uicontroller.remote_mass_delete.warning_title"),
                Translation.getTranslation(
                    "uicontroller.remote_mass_delete.warning_message", event
                        .getMemberInfo().nick, String.valueOf(event.getDeletePercentage()),
                    event.getFolderInfo().name,
                    event.getOldProfile().getName(), event.getNewProfile()
                        .getName()));
            applicationModel.getWarningsModel().pushWarning(warningEvent);
        }
    }
}