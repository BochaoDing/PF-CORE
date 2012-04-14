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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.MediaTracker;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Constants;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Feature;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.PreferencesEntry;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.FolderRepository;
import de.dal33t.powerfolder.disk.ScanResult;
import de.dal33t.powerfolder.event.*;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.light.MemberInfo;
import de.dal33t.powerfolder.message.Invitation;
import de.dal33t.powerfolder.skin.Skin;
import de.dal33t.powerfolder.ui.chat.ChatFrame;
import de.dal33t.powerfolder.ui.dialog.*;
import de.dal33t.powerfolder.ui.information.InformationCard;
import de.dal33t.powerfolder.ui.information.InformationFrame;
import de.dal33t.powerfolder.ui.model.ApplicationModel;
import de.dal33t.powerfolder.ui.model.TransferManagerModel;
import de.dal33t.powerfolder.ui.notices.*;
import de.dal33t.powerfolder.ui.notification.ChatNotificationHandler;
import de.dal33t.powerfolder.ui.notification.Slider;
import de.dal33t.powerfolder.ui.notification.PreviewNotificationHandler;
import de.dal33t.powerfolder.ui.wizard.PFWizard;
import de.dal33t.powerfolder.util.*;
import de.dal33t.powerfolder.util.os.OSUtil;
import de.dal33t.powerfolder.util.os.SystemUtil;
import de.dal33t.powerfolder.ui.util.*;
import de.dal33t.powerfolder.ui.preferences.PreferencesDialog;
import de.dal33t.powerfolder.util.update.Updater;
import de.dal33t.powerfolder.util.update.UpdaterHandler;
import de.dal33t.powerfolder.util.update.UIUpdateHandler;

/**
 * The ui controller.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.86 $
 */
public class UIController extends PFComponent {

    private static final long FIVE_GIG = 5L << 30;

    public static final int MAIN_FRAME_ID = 0;
    public static final int INFO_FRAME_ID = 1;
    public static final int CHAT_FRAME_ID = 2;
    public static final int WIZARD_DIALOG_ID = 3;

    private static final String COMMAND_OPENUI = "openui";
    private static final String COMMAND_HIDEUI = "hideui";
    private static final String COMMAND_SYNCALL = "syncall";
    private static final String COMMAND_EXIT = "exit";
    private static final String COMMAND_SYNC_SHUTDOWN = "sync-shutdown";
    private static final String COMMAND_SYNC_EXIT = "sync-exit";
    private static final String COMMAND_GOTOHP = "gotohp";
    private static final String COMMAND_PAUSE = "pause";
    private static final String COMMAND_RESUME = "resume";
    private static final String COMMAND_PREFERENCES = "preferences";

    private boolean started;
    private SplashScreen splash;
    private TrayIconManager trayIconManager;
    private MainFrame mainFrame;
    private SystemMonitorFrame systemMonitorFrame;
    private InformationFrame informationFrame;
    private ChatFrame chatFrame;
    private WeakReference<JDialog> wizardDialogReference;

    // List of pending jobs, execute when ui is opend
    private final List<Runnable> pendingJobs;
    private Menu sysTrayFoldersMenu;
    private MenuItem pauseResumeMenu;

    // The root of all models
    private ApplicationModel applicationModel;

    private boolean seenOome;

    private TransferManagerModel transferManagerModel;

    private final AtomicInteger activeFrame;

    /**
     * The UI distribution running.
     */
    private Skin[] skins;

    private Skin activeSkin;

    private boolean limitDialogShown = false;

    /**
     * Initializes a new UI controller. open UI with #start
     * 
     * @param controller
     */
    public UIController(Controller controller) {
        super(controller);

        activeFrame = new AtomicInteger();

        configureOomeHandler();

        // Initialize look and feel / icon set
        initSkin();

        if (OSUtil.isMacOS()) {
            UIUtil.setMacDockImage(Icons.getImageById(Icons.LOGO128X128));
        }

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
        if (Feature.SYSTEM_MONITOR.isEnabled()) {
            systemMonitorFrame = new SystemMonitorFrame(getController());
        }
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
    @SuppressWarnings("serial")
    public void start() {
        if (getController().isVerbose()) {
            // EventDispatchThreadHangMonitor.initMonitoring();
            // RepaintManager
            // .setCurrentManager(new CheckThreadViolationRepaintManager());
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
            logInfo("Starting minimized");
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
        if (getController().isFirstStart()
            && (!ProUtil.isRunningProVersion() || Feature.BETA.isEnabled()))
        {
            UIUtil.invokeLaterInEDT(new Runnable() {
                // Don't block start!
                public void run() {
                    hideSplash();
                    PFWizard.openBasicSetupWizard(getController());
                }
            });
        }

        UpdaterHandler updateHandler = new UIUpdateHandler(getController());
        Updater.installPeriodicalUpdateCheck(getController(), updateHandler);

        // Handle promo stuff
        // #2259: handlePromo();

        // Check limits
        if (!ProUtil.isRunningProVersion()) {
            getController().scheduleAndRepeat(new TimerTask() {
                @Override
                public void run() {
                    checkLimits(false);
                }
            }, 30L * 1000);
            applicationModel.getLicenseModel().setActivationAction(
                new AbstractAction() {
                    public void actionPerformed(ActionEvent e) {
                        checkLimits(true);
                    }
                });
        }

        configureDesktopShortcut(false);

        getController().addMassDeletionHandler(new MyMassDeletionHandler());
        getController().addInvitationHandler(new MyInvitationHandler());
        getController().addAskForFriendshipListener(
            new MyAskForFriendshipListener());
        getController().getFolderRepository().addFolderAutoCreateListener(
            new MyFolderAutoCreateListener());

    }

    /**
     * Creates / removes a desktop shortcut of the folders base dir.
     * 
     * @param removeFirst
     *            remove any shortcut before creating a new one.
     */
    public void configureDesktopShortcut(boolean removeFirst) {
        String shortcutName = getController().getFolderRepository()
            .getFoldersAbsoluteDir().getName();
        if (removeFirst
            || !PreferencesEntry.DISPLAY_POWERFOLDERS_SHORTCUT
                .getValueBoolean(getController()))
        {
            Util.removeDesktopShortcut(shortcutName);
        }
        if (PreferencesEntry.DISPLAY_POWERFOLDERS_SHORTCUT
            .getValueBoolean(getController()))
        {
            Util.createDesktopShortcut(shortcutName, getController()
                .getFolderRepository().getFoldersAbsoluteDir());
        }
    }

    private void checkLimits(boolean forceOpen) {
        long totalFolderSize = calculateTotalLocalSharedSize();
        logFine("Local shared folder size: "
            + Format.formatBytes(totalFolderSize));
        boolean limitHit = totalFolderSize > FIVE_GIG
            || getController().getFolderRepository().getFoldersCount() > 3;
        if (limitHit) {
            getController().getNodeManager().shutdown();
            if (!limitDialogShown || forceOpen) {
                limitDialogShown = true;
                new FreeLimitationDialog(getController()).open();
            }
        } else {
            if (!getController().getNodeManager().isStarted()) {
                getController().getNodeManager().start();
            }
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

    public void showPromoGFX(Window parent) {
        if (StringUtils.isBlank(ProUtil.getBuyNowURL(getController()))) {
            return;
        }
        try {
            ImageIcon icon = new ImageIcon(new URL(
                Constants.PROVIDER_CLIENT_PROMO_URL));
            if (icon.getImageLoadStatus() == MediaTracker.COMPLETE) {
                JLabel promoLabel = new JLabel(icon);
                promoLabel.setSize(new Dimension(230, 230));
                Border border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.DARK_GRAY),
                    Borders.createEmptyBorder("15, 15, 15, 15"));
                promoLabel.setBorder(border);
                CursorUtils.setHandCursor(promoLabel);
                promoLabel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        try {
                            BrowserLauncher.openURL(ProUtil
                                .getBuyNowURL(getController()));
                        } catch (IOException e1) {
                            logWarning("Unable to goto homepage", e1);
                        }
                    }
                });
                notifyComponent(promoLabel, parent, 20);
            } else {
                logWarning("Failed to downlaod PROVIDER_CLIENT_PROMO_URL");
            }
        } catch (MalformedURLException e) {
            logWarning("Unable to show promo gfx. " + e, e);
        }
    }

    public void askToPauseResume() {
        boolean silent = getController().isPaused();
        if (silent) {
            // Resuming - nothing to ask.
            getController().setPaused(!silent);
        } else {
            if (PreferencesEntry.SHOW_ASK_FOR_PAUSE.getValueBoolean(
                    getController())) {
                PauseDialog pd = new PauseDialog(getController());
                pd.open();
            } else {
                getController().setPaused(!silent);
            }
        }

    }

    private void handlePromo() {
        String prefKey = "startCount" + Controller.PROGRAM_VERSION;
        int thisVersionStartCount = getController().getPreferences().getInt(
            prefKey, 0);

        // #1838 Ads in trial
        if (!ProUtil.isRunningProVersion() || ProUtil.isTrial(getController()))
        {
            // Go to HP every 5 starts
            if (thisVersionStartCount % 5 == 4) {
                try {
                    BrowserLauncher.openURL(ProUtil
                        .getBuyNowURL(getController()));
                } catch (IOException e1) {
                    logWarning("Unable to goto homepage", e1);
                }
            }
        }

        // Show promo after 10 seconds
        if (getController().getDistribution().showClientPromo()
            && thisVersionStartCount >= 6)
        {
            getController().scheduleAndRepeat(new TimerTask() {
                @Override
                public void run() {
                    UIUtil.invokeLaterInEDT(new Runnable() {
                        public void run() {
                            if (!ProUtil.isRunningProVersion()
                                || ProUtil.isTrial(getController()))
                            {
                                if (!PFWizard.isWizardOpen()) {
                                    showPromoGFX(getMainFrame()
                                        .getUIComponent());
                                }
                            }
                        }
                    });
                }
            }, 10 * 1000L, 1000L * 60 * 60);
        }

        thisVersionStartCount++;
        getController().getPreferences().putInt(prefKey, thisVersionStartCount);
    }

    private void initalizeSystray() {
        trayIconManager = new TrayIconManager(this);
        PopupMenu menu = new PopupMenu();

        TrayIcon trayIcon = trayIconManager.getTrayIcon();
        if (trayIcon != null) {
            trayIcon.setPopupMenu(menu);
        }

        ActionListener systrayActionHandler = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (COMMAND_OPENUI.equals(e.getActionCommand())) {
                    mainFrame.toFront();
                } else if (COMMAND_HIDEUI.equals(e.getActionCommand())) {
                    mainFrame.getUIComponent().setVisible(false);
                } else if (COMMAND_EXIT.equals(e.getActionCommand())) {
                    // Exit to system
                    if (isShutdownAllowed()) {
                        getController().exit(0);
                    }
                } else if (COMMAND_SYNC_SHUTDOWN.equals(e.getActionCommand())) {
                    if (OSUtil.isLinux()) {
                        FormLayout layout = new FormLayout(
                            "pref, pref:grow, 3dlu, pref, pref",
                            "3dlu, pref, 3dlu, pref, 3dlu");
                        PanelBuilder builder = new PanelBuilder(layout);
                        CellConstraints cc = new CellConstraints();
                        builder.add(
                            new JLabel(Translation
                                .getTranslation("shutdown.message")), cc.xyw(2,
                                2, 3));
                        builder.add(
                            new JLabel(Translation
                                .getTranslation("shutdown.prompt")), cc
                                .xy(2, 4));
                        JPasswordField textField = new JPasswordField(20);
                        builder.add(textField, cc.xy(4, 4));
                        int i = DialogFactory.genericDialog(
                            getController(),
                            Translation.getTranslation("shutdown.title"),
                            builder.getPanel(),
                            new String[]{
                                Translation.getTranslation("general.ok"),
                                Translation.getTranslation("general.cancel")},
                            0, GenericDialogType.QUESTION);
                        if (i == 0) {
                            String password = textField.getText();
                            getController().performFullSync();
                            getController().shutdownAfterSync(password);
                        }
                    } else {
                        getController().performFullSync();
                        getController().shutdownAfterSync(null);
                    }
                } else if (COMMAND_SYNC_EXIT.equals(e.getActionCommand())) {
                    getController().performFullSync();
                    getController().exitAfterSync(4);
                } else if (COMMAND_SYNCALL.equals(e.getActionCommand())) {
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            getController().performFullSync();
                        }
                    });
                } else if (COMMAND_GOTOHP.equals(e.getActionCommand())) {
                    try {
                        BrowserLauncher.openURL(ConfigurationEntry.PROVIDER_URL
                            .getValue(getController()));
                    } catch (IOException e1) {
                        logWarning("Unable to goto PowerFolder homepage", e1);
                    }
                } else if (COMMAND_PAUSE.equals(e.getActionCommand()) ||
                        COMMAND_RESUME.equals(e.getActionCommand())) {
                    askToPauseResume();
                } else if (COMMAND_PREFERENCES.equals(e.getActionCommand())) {
                    new PreferencesDialog(getController()).open();
                }
            }
        };

        // /////////////////////////
        // Open / close menu item //
        // /////////////////////////
        final MenuItem opentUI = new MenuItem(Translation.getTranslation(
                "systray.show"));
        menu.add(opentUI);
        opentUI.setActionCommand(COMMAND_OPENUI);
        opentUI.addActionListener(systrayActionHandler);

        // //////
        // Web //
        // //////
        MenuItem item = menu.add(new MenuItem(Translation.getTranslation(
                "general.application.name")));
        item.setActionCommand(COMMAND_GOTOHP);
        item.addActionListener(systrayActionHandler);

        // //////////
        // Folders //
        // //////////
        sysTrayFoldersMenu = new Menu(
            Translation.getTranslation("general.folder"));
        sysTrayFoldersMenu.setEnabled(false);
        menu.add(sysTrayFoldersMenu);

        // /////////////////
        // Pause / Resume //
        // /////////////////
        pauseResumeMenu = new MenuItem(Translation.getTranslation(
                "action_resume_sync.name"));
        menu.add(pauseResumeMenu);
        pauseResumeMenu.addActionListener(systrayActionHandler);
        getController().addPausedModeListener(new MyPausedModeListener());
        configurePauseResumeLink();

        // //////////////
        // Preferences //
        // //////////////
        item = menu.add(new MenuItem(Translation.getTranslation(
                "action_open_preferences.name")));
        item.setActionCommand(COMMAND_PREFERENCES);
        item.addActionListener(systrayActionHandler);

        menu.addSeparator();

        // ////////////////
        // Sync Shutdown //
        // ////////////////
        if (SystemUtil.isShutdownSupported()) {
            item = menu.add(new MenuItem(Translation.getTranslation(
                    "systray.sync_shutdown")));
            item.setActionCommand(COMMAND_SYNC_SHUTDOWN);
            item.addActionListener(systrayActionHandler);
        }

        // ////////////
        // Sync Exit //
        // ////////////
        item = menu.add(new MenuItem(Translation.getTranslation(
                "systray.sync_exit")));
        item.setActionCommand(COMMAND_SYNC_EXIT);
        item.addActionListener(systrayActionHandler);

        // ///////
        // Exit //
        // ///////
        item = menu.add(new MenuItem(Translation.getTranslation(
                "systray.exit")));
        item.setActionCommand(COMMAND_EXIT);
        item.addActionListener(systrayActionHandler);

        if (trayIcon != null) {
            trayIcon.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    mainFrame.toFront();
                }
            });
        }

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (Exception e) {
            logSevere("Exception", e);
            OSUtil.disableSystray();
            return;
        }

        // Switch Systray show/hide menuitem dynamically
        mainFrame.getUIComponent().addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent arg0) {
                opentUI.setLabel(Translation.getTranslation("systray.hide"));
                opentUI.setActionCommand(COMMAND_HIDEUI);
            }

            public void componentHidden(ComponentEvent arg0) {
                opentUI.setLabel(Translation.getTranslation("systray.show"));
                opentUI.setActionCommand(COMMAND_OPENUI);
            }
        });

        mainFrame.getUIComponent().addWindowListener(new WindowAdapter() {
            @Override
            public void windowIconified(WindowEvent e) {
                opentUI.setLabel(Translation.getTranslation("systray.show"));
                opentUI.setActionCommand(COMMAND_OPENUI);
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
        final File localBase = folder.getCommitOrLocalDir();
        menuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (localBase.exists()) {
                    FileUtils.openFile(localBase);
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
            PreferencesEntry.SKIN_NAME.setValue(getController(),
                activeSkin.getName());
        }

        Properties props = activeSkin.getIconsProperties();
        if (props != null) {
            Icons.setIconProperties(props);
        }
        try {
            LookAndFeelSupport.setLookAndFeel(activeSkin.getLookAndFeel());
        } catch (UnsupportedLookAndFeelException e) {
            logSevere(
                "Failed to set look and feel for skin " + activeSkin.getName(),
                e);
        } catch (ParseException e) {
            logSevere(
                "Failed to set look and feel for skin " + activeSkin.getName(),
                e);
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
            applicationModel.getNoticesModel().handleNotice(
                    new OutOfMemoryNotice(oome));
        }
    }

    /**
     * Displays the information window if not already displayed.
     */
    public void displaySystemMonitorWindow() {
        if (systemMonitorFrame != null) {
            UIUtil.putOnScreen(systemMonitorFrame.getUIComponent());
            systemMonitorFrame.getUIComponent().setVisible(true);
        }
    }

    /**
     * Displays the information window if not already displayed.
     */
    private void displayInformationWindow() {
        if (mainFrame.shouldShowInfoInline()) {
            mainFrame.showInlineInfoPanel((JPanel) informationFrame
                .getUIComponent().getContentPane(), informationFrame
                .getUIComponent().getTitle());
        } else {
            JFrame frame = informationFrame.getUIComponent();
            if (frame.getExtendedState() == Frame.ICONIFIED) {
                frame.setExtendedState(Frame.NORMAL);
            }
            if (frame.getWidth() < MainFrame.MIN_INFO_WIDTH) {
                frame.setSize(MainFrame.MIN_INFO_WIDTH, frame.getHeight());
            }
            UIUtil.putOnScreen(frame);
            frame.setVisible(true);
        }
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
     */
    public void openFilesInformationIncoming(FolderInfo folderInfo) {
        informationFrame.displayFolderFilesIncoming(folderInfo);
        displayInformationWindow();
    }

    public void openFilesInformationDeleted(FolderInfo folderInfo) {
        informationFrame.displayFolderFilesDeleted(folderInfo);
        displayInformationWindow();
    }

    public void openFilesInformationUnsynced(FolderInfo folderInfo) {
        informationFrame.displayFolderFilesUnsynced(folderInfo);
        displayInformationWindow();
    }

    /**
     * Opens the Files information for a folder.
     * 
     * @param folderInfo
     *            info of the folder to display files information for.
     */
    public void openFilesInformation(FolderInfo folderInfo) {
        informationFrame.displayFolderFiles(folderInfo);
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
     * Displays the Settings information move folder dialog.
     * 
     * @param folderInfo
     *            info of the folder to display member settings information for.
     */
    public void moveLocalFolder(FolderInfo folderInfo) {
        informationFrame.moveLocalFolder(folderInfo);
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
        JFrame frame = chatFrame.getUIComponent();
        if (frame.getExtendedState() == Frame.ICONIFIED) {
            frame.setExtendedState(Frame.NORMAL);
        }
        UIUtil.putOnScreen(chatFrame.getUIComponent());
        chatFrame.getUIComponent().setVisible(true);
    }

    public void openTransfersInformation() {
        informationFrame.displayTransfers();
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

    public void openNoticesCard() {
        informationFrame.displayNotices();
        displayInformationWindow();
    }

    /**
     * Call when non-quitOnX close called. Hides child frames.
     */
    public void hideChildPanels() {
        informationFrame.getUIComponent().setVisible(false);
        chatFrame.getUIComponent().setVisible(false);
        if (systemMonitorFrame != null) {
            systemMonitorFrame.getUIComponent().setVisible(false);
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
     * Attention: If possible use method
     * {@link UIUtil#getParentWindow(ActionEvent)} to determine the active
     * window.
     * <p>
     * This returns most recently active PowerFolder frame. Possibly the
     * InformationFrame, ChatFrame or (default) MainFrame. Used by dialogs, so
     * focus does not always jump to the wrong (Main) frame.
     * <P>
     * 
     * @return the active frame.
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

    public boolean chatFrameVisible() {
        return chatFrame.getUIComponent().isVisible();
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

            if (systemMonitorFrame != null) {
                systemMonitorFrame.storeValues();
                systemMonitorFrame.getUIComponent().setVisible(false);
                systemMonitorFrame.getUIComponent().dispose();
            }

            mainFrame.storeValues();
            mainFrame.getUIComponent().setVisible(false);
            mainFrame.getUIComponent().dispose();

            // Close systray
            if (OSUtil.isSystraySupported() && trayIconManager != null) {
                SystemTray.getSystemTray()
                    .remove(trayIconManager.getTrayIcon());
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
     * @return true if the information frame is showing a folder.
     */
    public boolean isShowingFolder() {
        return isShowingInfo() && informationFrame.isShowingFolder();
    }

    /**
     * @return true if the info panel is displayed currently (either inline or
     *         floating)
     */
    public boolean isShowingInfo() {
        if (mainFrame.shouldShowInfoInline()) {
            return mainFrame.isShowingInfoInline();
        } else {
            JFrame frame = informationFrame.getUIComponent();
            return frame.isVisible()
                && (frame.getExtendedState() & Frame.ICONIFIED) != Frame.ICONIFIED;
        }

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

    /**
     * Only use this for preview from the DialogSettingsTab.
     *
     * @param title
     * @param message
     */
    public void previewMessage(String title, String message) {
        PreviewNotificationHandler notificationHandler =
                new PreviewNotificationHandler(getController(), title,
                        message);
        notificationHandler.show();
    }

    /**
     * Show a chat message popup notification.
     * 
     * @param title
     *            message title
     * @param message
     *            the message to popup
     */
    public void showChatNotification(MemberInfo memberInfo, String title,
        String message)
    {
        if (started && !getController().isShuttingDown()) {
            if ((Boolean) applicationModel.getChatNotificationsValueModel()
                .getValue())
            {
                ChatNotificationHandler notificationHandler = new ChatNotificationHandler(
                    getController(), memberInfo, title, message);
                notificationHandler.show();
            }
        }
    }

    private void notifyComponent(JComponent content, Window owner,
        int secondsToDisplay)
    {
        Slider slider = new Slider(content, owner, secondsToDisplay,
            PreferencesEntry.NOTIFICATION_TRANSLUCENT
                .getValueInt(getController()), getController().isNotifyLeft());
        slider.show();
    }

    private void handleFolderAutoCreate(FolderAutoCreateEvent event) {
        if (PreferencesEntry.SHOW_AUTO_CREATED_FOLDERS
            .getValueBoolean(getController()))
        {
            applicationModel.getNoticesModel().handleNotice(
                new FolderAutoCreateNotice(event.getFolderInfo()));
        }
    }

    /**
     * Scan results have been created after the user requested folder sync. So
     * give the user some feedback.
     * 
     * @param scanResult
     */
    public void scanResultCreated(ScanResult scanResult) {
        // UI hidden?
        if (mainFrame == null || mainFrame.isIconifiedOrHidden()) {
            return;
        }
        int newSize = scanResult.getNewFiles().size();
        int changedSize = scanResult.getChangedFiles().size();
        int deletedSize = scanResult.getDeletedFiles().size();
        StringBuilder sb = new StringBuilder();
        sb.append(Translation.getTranslation("uicontroller.sync_info.start")
            + "\n\n" + '(');
        boolean addComma = false;
        if (newSize > 0) {
            sb.append(Translation.getTranslation("uicontroller.sync_info.new",
                String.valueOf(newSize)));
            addComma = true;
        }
        if (changedSize > 0) {
            if (addComma) {
                sb.append(", ");
            }
            sb.append(Translation.getTranslation(
                "uicontroller.sync_info.changed", String.valueOf(changedSize)));
            addComma = true;
        }
        if (deletedSize > 0) {
            if (addComma) {
                sb.append(", ");
            }
            sb.append(Translation.getTranslation(
                "uicontroller.sync_info.deleted", String.valueOf(deletedSize)));
        }
        if (newSize == 0 && changedSize == 0 && deletedSize == 0) {
            sb.append(Translation
                .getTranslation("uicontroller.sync_info.no_changes_detected"));
        }
        sb.append(')');
        if (newSize > 0 || changedSize > 0) {
            sb.append("\n\n");
            sb.append(Translation.getTranslation(
                "uicontroller.sync_info.transfer",
                String.valueOf(newSize + changedSize)));
        }
        DialogFactory.genericDialog(getController(),
            Translation.getTranslation("uicontroller.sync_info.title"),
            sb.toString(), GenericDialogType.INFO);
    }

//    public void clearBlink() {
//        if (trayIconManager != null) {
//            trayIconManager.clearBlink();
//        }
//    }

    /**
     * Special case. A folder has just been created from an invite.
     * Switch to the folder tab and crack open the new folder info.
     *
     * @param folderInfo
     */
    public void displayInviteFolderContents(FolderInfo folderInfo) {
        mainFrame.showFoldersTab();
        openFilesInformation(folderInfo);
    }

    private void configurePauseResumeLink() {
        if (getController().isPaused()) {
            pauseResumeMenu.setLabel(Translation.getTranslation(
                    "action_resume_sync.name"));
            pauseResumeMenu.setActionCommand(COMMAND_RESUME);
        } else {
            pauseResumeMenu.setLabel(Translation.getTranslation(
                    "action_pause_sync.name"));
            pauseResumeMenu.setActionCommand(COMMAND_PAUSE);
        }
    }

    // ////////////////
    // Inner Classes //
    // ////////////////

    private class MyFolderRepositoryListener implements
            FolderRepositoryListener {

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
            Collection<Folder> folders = repo.getFolders(true);

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
            if (changed) {
                String text2 = Translation.getTranslation(
                    "check_status.powerfolders",
                    Format.formatBytes(nTotalBytes),
                    String.valueOf(folders.size()));

                applicationModel.getNoticesModel().handleNotice(
                    new SimpleNotificationNotice(Translation
                        .getTranslation("check_status.title"), text1 + "\n\n"
                        + text2));
            }
        }
    }

    /**
     * Class to handle local and remote mass deletion events. This pushes
     * warnings into the app model.
     */
    private class MyMassDeletionHandler implements MassDeletionHandler {
        public void localMassDeletion(LocalMassDeletionEvent event) {
            LocalDeleteNotice notice = new LocalDeleteNotice(
                Translation.getTranslation("warning_notice.title"),
                Translation.getTranslation("warning_notice.mass_deletion"),
                event.getFolderInfo());
            applicationModel.getNoticesModel().handleNotice(notice);
        }

        public void remoteMassDeletion(RemoteMassDeletionEvent event) {
            String message;
            if (event.isPercentage()) {
                message = Translation.getTranslation(
                    "uicontroller.remote_mass_delete.warning_message", event
                        .getMemberInfo().nick, String.valueOf(event
                        .getDeleteFigure()), event.getFolderInfo().name, event
                        .getOldProfile().getName(), event.getNewProfile()
                        .getName());
            } else {
                message = Translation.getTranslation(
                    "uicontroller.remote_mass_delete.warning_absolute_message",
                    event.getMemberInfo().nick, String.valueOf(event
                        .getDeleteFigure()), event.getFolderInfo().name, event
                        .getOldProfile().getName(), event.getNewProfile()
                        .getName());
            }

            WarningNotice notice = new WarningNotice(
                Translation.getTranslation("warning_notice.title"),
                Translation.getTranslation("warning_notice.mass_deletion"),
                message);
            applicationModel.getNoticesModel().handleNotice(notice);
        }
    }

    private class MyInvitationHandler implements InvitationHandler {
        public void gotInvitation(Invitation invitation) {
            boolean autoAccepted = false;

            if (ConfigurationEntry.AUTO_SETUP_ACCOUNT_FOLDERS
                .getValueBoolean(getController()))
            {
                // Automatically accept this invitation, if possible.
                autoAccepted = getController().getFolderRepository()
                    .autoAcceptInvitation(invitation);
            }

            if (autoAccepted) {
                // Just tell the user what happened
                Notice notice = new SimpleNotificationNotice(
                    Translation.getTranslation("notice.invitation.title"),
                    Translation.getTranslation("notice.invitation.summary",
                        invitation.getInvitor().getNick(),
                        invitation.folder.name));
                applicationModel.getNoticesModel().handleNotice(notice);
            } else {
                // Let user decide what to do with the invitation.
                Notice notice = new InvitationNotice(
                    Translation.getTranslation("notice.invitation.title"),
                    Translation.getTranslation("notice.invitation.summary",
                        invitation.getInvitor().getNick(),
                        invitation.folder.name), invitation);
                applicationModel.getNoticesModel().handleNotice(notice);
            }
        }
    }

    private class MyAskForFriendshipListener implements
        AskForFriendshipListener
    {
        public void askForFriendship(AskForFriendshipEvent event) {
            if (PreferencesEntry.ASK_FOR_FRIENDSHIP_ON_PRIVATE_FOLDER_JOIN
                .getValueBoolean(getController()))
            {
                Notice notice = new AskForFriendshipEventNotice(
                    Translation
                        .getTranslation("notice.ask_for_friendship.title"),
                    Translation.getTranslation(
                        "notice.ask_for_friendship.summary", event
                            .getMemberInfo().getNick()), event);
                applicationModel.getNoticesModel().handleNotice(notice);
            }
        }
    }

    private class MyFolderAutoCreateListener implements
        FolderAutoCreateListener
    {

        public boolean fireInEventDispatchThread() {
            return true;
        }

        public void folderAutoCreated(FolderAutoCreateEvent e) {
            handleFolderAutoCreate(e);
        }
    }

    /**
     * Can we shut down?
     * If WARN_ON_CLOSE, let user know if there are any folders still syncing.
     *
     * @return if all clear to shut down.
     */
    public boolean isShutdownAllowed() {
        boolean warnOnClose = PreferencesEntry.WARN_ON_CLOSE
            .getValueBoolean(getController());
        if (warnOnClose) {
            Collection<Folder> folderCollection =
                    getController().getFolderRepository().getFolders();
            List<Folder> foldersToWarn = new ArrayList<Folder>(
                folderCollection.size());
            for (Folder folder : folderCollection) {
                if (folder.isTransferring()) {
                    logWarning("Close warning on folder: " + folder);
                    foldersToWarn.add(folder);
                }
            }
            if (!foldersToWarn.isEmpty()) {
                StringBuilder folderslist = new StringBuilder();
                for (Folder folder : foldersToWarn) {
                    folderslist.append("\n     - " + folder.getName());
                }
                String title = Translation
                    .getTranslation("uicontroller.warn_on_close.title");
                String text;
                if (applicationModel.getFolderRepositoryModel().isSyncing()) {
                    Date syncDate = applicationModel.getFolderRepositoryModel()
                        .getEstimatedSyncDate();
                    text = Translation.getTranslation(
                        "uicontroller.warn_on_close_eta.text",
                        folderslist.toString(),
                        Format.formatDateShort(syncDate));
                } else {
                    text = Translation.getTranslation(
                        "uicontroller.warn_on_close.text",
                        folderslist.toString());
                }
                String question = Translation
                    .getTranslation("general.neverAskAgain");
                NeverAskAgainResponse response = DialogFactory
                    .genericDialog(getController(), title, text,
                            new String[]{
                            Translation.getTranslation(
                                    "uicontroller.continue_exit"),
                            Translation.getTranslation("general.cancel")},
                        0, GenericDialogType.QUESTION, question);
                if (response.isNeverAskAgain()) {
                    PreferencesEntry.WARN_ON_CLOSE.setValue(
                        getController(), false);
                }
                return response.getButtonIndex() == 0;
            }

            // No folders unsynced
            return true;
        }

        // Do not warn on close, so we allow shut down
        return true;
    }

    private class MyPausedModeListener implements PausedModeListener {

        public boolean fireInEventDispatchThread() {
            return true;
        }

        public void setPausedMode(PausedModeEvent event) {
            configurePauseResumeLink();
        }
    }



}