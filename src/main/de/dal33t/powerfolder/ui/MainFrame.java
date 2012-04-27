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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.TransferHandler;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.RootPaneUI;

import com.jgoodies.forms.builder.ButtonBarBuilder;
import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PreferencesEntry;
import de.dal33t.powerfolder.clientserver.ServerClient;
import de.dal33t.powerfolder.clientserver.ServerClientEvent;
import de.dal33t.powerfolder.clientserver.ServerClientListener;
import de.dal33t.powerfolder.event.FolderRepositoryEvent;
import de.dal33t.powerfolder.event.FolderRepositoryListener;
import de.dal33t.powerfolder.event.NodeManagerAdapter;
import de.dal33t.powerfolder.event.NodeManagerEvent;
import de.dal33t.powerfolder.event.OverallFolderStatListener;
import de.dal33t.powerfolder.event.PausedModeEvent;
import de.dal33t.powerfolder.event.PausedModeListener;
import de.dal33t.powerfolder.message.clientserver.AccountDetails;
import de.dal33t.powerfolder.security.OnlineStorageSubscription;
import de.dal33t.powerfolder.ui.action.BaseAction;
import de.dal33t.powerfolder.ui.dialog.BaseDialog;
import de.dal33t.powerfolder.ui.dialog.DialogFactory;
import de.dal33t.powerfolder.ui.dialog.GenericDialogType;
import de.dal33t.powerfolder.ui.model.FolderRepositoryModel;
import de.dal33t.powerfolder.ui.util.DelayedUpdater;
import de.dal33t.powerfolder.ui.util.Icons;
import de.dal33t.powerfolder.ui.util.NeverAskAgainResponse;
import de.dal33t.powerfolder.ui.util.SyncIconButtonMini;
import de.dal33t.powerfolder.ui.util.UIUtil;
import de.dal33t.powerfolder.ui.widget.ActionLabel;
import de.dal33t.powerfolder.ui.widget.JButton3Icons;
import de.dal33t.powerfolder.ui.widget.JButtonMini;
import de.dal33t.powerfolder.ui.wizard.PFWizard;
import de.dal33t.powerfolder.util.BrowserLauncher;
import de.dal33t.powerfolder.util.DateUtil;
import de.dal33t.powerfolder.util.FileUtils;
import de.dal33t.powerfolder.util.Format;
import de.dal33t.powerfolder.util.StringUtils;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.os.OSUtil;
import de.javasoft.plaf.synthetica.SyntheticaRootPaneUI;

/**
 * Powerfolder gui mainframe
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.44 $
 */
public class MainFrame extends PFUIComponent {

    /**
     * The width of the main tabbed pane when in NORMAL state
     */
    private int mainWidth;

    private JFrame uiComponent;
    private JLabel logoLabel;
    private JPanel centralPanel;
    private MainTabbedPane mainTabbedPane;
    private JPanel inlineInfoPanel;
    private JLabel inlineInfoLabel;
    private JButton inlineInfoCloseButton;
    private JSplitPane split;
    private ServerClient client;
    private MyNodeManagerListener nodeManagerListener;

    // Left mini panel
    private JButtonMini allInSyncButton;
    private SyncIconButtonMini syncingButton;
    private JButtonMini setupButton;

    private ActionLabel upperMainTextLabel;
    private ActionLabel syncDateLabel;
    private ActionLabel setupLabel;

    private ActionLabel loginActionLabel;
    private JProgressBar usagePB;
    private ActionLabel noticesActionLabel;

    private DelayedUpdater mainStatusUpdater;

    // Right mini panel
    private ActionLabel expandCollapseActionLabel;
    private MyExpandCollapseAction expandCollapseAction;
    private ActionLabel openWebInterfaceActionLabel;
    private ActionLabel openFoldersBaseActionLabel;
    private ActionLabel pauseResumeActionLabel;
    private ActionLabel configurationActionLabel;
    private ActionLabel openDebugActionLabel;
    private ActionLabel openTransfersActionLabel;

    private AtomicBoolean compact = new AtomicBoolean();
    private JButton3Icons closeButton;
    private JButton3Icons plusButton;
    private JButton3Icons minusButton;

    /**
     * @param controller
     *            the controller.
     * @throws HeadlessException
     */
    public MainFrame(Controller controller) throws HeadlessException {
        super(controller);

        mainStatusUpdater = new DelayedUpdater(getController());
        compact.set(!PreferencesEntry.EXPERT_MODE
            .getValueBoolean(getController()));
        controller.getFolderRepository().addFolderRepositoryListener(
            new MyFolderRepositoryListener());
        initComponents();
        configureUi();
        updateOnlineStorageDetails();
        setCompactMode(compact.get(), true);
    }

    private JPanel createMiniPanel() {
        FormLayout layout = new FormLayout("left:pref:grow, left:pref",
            "top:pref:grow, 7dlu" /* 7dlu WTF for what? Too tired to get it */);
        DefaultFormBuilder builder = new DefaultFormBuilder(layout);
        builder.setBorder(Borders.createEmptyBorder("10dlu, 0, 0, 3dlu"));
        CellConstraints cc = new CellConstraints();

        builder.add(createLeftMiniPanel(), cc.xy(1, 1));
        builder.add(createRightMiniPanel(), cc.xy(2, 1));

        return builder.getPanel();
    }

    private Component createLeftMiniPanel() {
        CellConstraints cc = new CellConstraints();

        // UPPER PART
        FormLayout layoutUpper = new FormLayout("pref, 3dlu, pref:grow",
            "pref, pref");
        DefaultFormBuilder builderUpper = new DefaultFormBuilder(layoutUpper);
        PanelBuilder b = new PanelBuilder(new FormLayout("pref:grow",
            "pref:grow"));
        b.add(allInSyncButton, cc.xy(1, 1));
        b.add(syncingButton, cc.xy(1, 1));
        b.add(setupButton, cc.xy(1, 1));
        builderUpper.add(b.getPanel(), cc.xywh(1, 1, 1, 2));
        builderUpper.add(upperMainTextLabel.getUIComponent(), cc.xy(3, 1));
        builderUpper.add(syncDateLabel.getUIComponent(), cc.xy(3, 2));
        builderUpper.add(setupLabel.getUIComponent(), cc.xy(3, 2));
        // UPPER PART END

        // LOWER PART
        FormLayout layoutLower = new FormLayout("pref, 100dlu",
                "pref, pref, pref");
        DefaultFormBuilder builderLower = new DefaultFormBuilder(layoutLower);
        // Include a spacer icon that lines up the pair with builderUpper
        // when allInSyncLabel has null icon.
        builderLower.add(new JLabel((Icon) null), cc.xywh(1, 1, 1, 2));
        builderLower.add(loginActionLabel.getUIComponent(), cc.xy(2, 1));
        builderLower.add(usagePB, cc.xy(2, 2));
        builderLower.add(noticesActionLabel.getUIComponent(), cc.xy(2, 3));
        // LOWER PART END

        // PUT TOGETHER
        FormLayout layoutMain = new FormLayout("pref", "pref, 5dlu, pref");
        DefaultFormBuilder builderMain = new DefaultFormBuilder(layoutMain);
        builderMain.setBorder(Borders.createEmptyBorder("0, 5dlu, 5dlu, 0"));
        builderMain.add(builderUpper.getPanel(), cc.xy(1, 1));
        builderMain.add(builderLower.getPanel(), cc.xy(1, 3));
        // PUT TOGETHER END

        return builderMain.getPanel();
    }

    private Component createRightMiniPanel() {
        FormLayout layout = new FormLayout("pref:grow",
            "pref, pref, pref, pref, pref, pref, pref");
        DefaultFormBuilder builder = new DefaultFormBuilder(layout);
        CellConstraints cc = new CellConstraints();

        builder.add(expandCollapseActionLabel.getUIComponent(), cc.xy(1, 1));
        if (ConfigurationEntry.WEB_LOGIN_ALLOWED
            .getValueBoolean(getController()))
        {
            builder.add(openWebInterfaceActionLabel.getUIComponent(),
                cc.xy(1, 2));
        }
        builder.add(openFoldersBaseActionLabel.getUIComponent(), cc.xy(1, 3));
        builder.add(pauseResumeActionLabel.getUIComponent(), cc.xy(1, 4));
        builder.add(configurationActionLabel.getUIComponent(), cc.xy(1, 5));
        if (getController().isVerbose()) {
            builder.add(openDebugActionLabel.getUIComponent(), cc.xy(1, 6));
        }
        if (PreferencesEntry.EXPERT_MODE.getValueBoolean(getController())) {
            builder.add(openTransfersActionLabel.getUIComponent(), cc.xy(1, 7));
        }

        return builder.getPanel();
    }

    private void configureUi() {

        // Display the title pane.
        uiComponent.getRootPane().putClientProperty(
            "Synthetica.titlePane.enabled", Boolean.FALSE);
        uiComponent.getRootPane().updateUI();

        FormLayout layout = new FormLayout("fill:pref:grow, pref, 3dlu, pref",
            "pref, fill:0:grow, pref");
        DefaultFormBuilder builder = new DefaultFormBuilder(layout);
        CellConstraints cc = new CellConstraints();

        builder.add(logoLabel, cc.xyw(1, 1, 3));

        ButtonBarBuilder b = new ButtonBarBuilder();
        b.addFixed(minusButton);
        b.addFixed(plusButton);
        b.addFixed(closeButton);
        builder.add(b.getPanel(), cc.xywh(4, 1, 1, 1, "right, top"));

        builder.add(inlineInfoLabel,
            cc.xy(2, 1, CellConstraints.DEFAULT, CellConstraints.BOTTOM));
        builder.add(inlineInfoCloseButton,
            cc.xy(4, 1, CellConstraints.DEFAULT, CellConstraints.BOTTOM));

        builder.add(centralPanel, cc.xyw(1, 2, 4));

        builder.add(createMiniPanel(), cc.xyw(1, 3, 4));

        uiComponent.getContentPane().removeAll();
        uiComponent.getContentPane().add(builder.getPanel());
        uiComponent.setResizable(true);

        Controller c = getController();

        // Pack elements and set to default size.
        uiComponent.pack();
        uiComponent.setSize(uiComponent.getWidth(),
                    UIConstants.MAIN_FRAME_DEFAULT_HEIGHT);

        mainWidth = uiComponent.getWidth();
        logFine("Main/Info width: " + mainWidth + " / ?");

        // Initial top-left corner
        int mainX = PreferencesEntry.MAIN_FRAME_X.getValueInt(c);
        if (mainX < 0) {
            mainX = Toolkit.getDefaultToolkit().getScreenSize().width / 2
                - mainWidth / 2;
        }
        int mainY = PreferencesEntry.MAIN_FRAME_Y.getValueInt(c);
        if (mainY < 0) {
            mainY = Toolkit.getDefaultToolkit().getScreenSize().height / 3 * 2
                - uiComponent.getHeight() / 2;
        }

        uiComponent.setLocation(mainX, mainY);

        relocateIfNecessary();
        configureInlineInfo();
        updateMainStatus0();
        updateNoticesLabel();
    }

    /**
     * Show notices link if there are notices available.
     */
    private void updateNoticesLabel() {
        int unreadCount = (Integer) getController().getUIController()
                .getApplicationModel().getNoticesModel()
                .getUnreadNoticesCountVM().getValue();
        if (unreadCount == 0) {
            noticesActionLabel.setVisible(false);
        } else if (unreadCount == 1) {
            noticesActionLabel.setVisible(true);
            noticesActionLabel.setText(Translation.getTranslation(
                    "main_frame.unread_notices.single.text"));
        } else {
            noticesActionLabel.setVisible(true);
            noticesActionLabel.setText(Translation.getTranslation(
                    "main_frame.unread_notices.plural.text",
                    String.valueOf(unreadCount)));
        }
    }

    /**
     * Asks user about exit behavior of the program when the program is used for
     * the first time
     */
    private void handleExitFirstRequest() {
        boolean askForQuitOnX = PreferencesEntry.ASK_FOR_QUIT_ON_X
            .getValueBoolean(getController());
        if (askForQuitOnX) {
            // Prompt for personal message.
            String[] options = {
                Translation
                    .getTranslation("dialog.ask_for_quit_on_x.Minimize_button"),
                Translation
                    .getTranslation("dialog.ask_for_quit_on_x.Exit_button")};

            NeverAskAgainResponse response = DialogFactory.genericDialog(
                getController(),
                Translation.getTranslation("dialog.ask_for_quit_on_x.title"),
                Translation.getTranslation("dialog.ask_for_quit_on_x.text"),
                options, 0, GenericDialogType.QUESTION,
                Translation.getTranslation("general.neverAskAgain"));

            if (response.getButtonIndex() == 1) { // == Exit
                PreferencesEntry.QUIT_ON_X.setValue(getController(), true);
            } else {
                PreferencesEntry.QUIT_ON_X.setValue(getController(), false);
            }

            if (response.isNeverAskAgain()) {
                // don't ask me again
                PreferencesEntry.ASK_FOR_QUIT_ON_X.setValue(getController(),
                    false);
            }
        }
    }

    /**
     * Initalizes all ui components
     */
    private void initComponents() {
        logFine("Screen resolution: "
            + Toolkit.getDefaultToolkit().getScreenSize()
            + " / Width over all monitors: "
            + UIUtil.getScreenWidthAllMonitors());

        uiComponent = new JFrame();
        uiComponent.setTransferHandler(new MyTransferHandler());
        checkOnTop();
        uiComponent.addWindowFocusListener(new MyWindowFocusListner());
        uiComponent.setIconImage(Icons.getImageById(Icons.SMALL_LOGO));
        uiComponent.setBackground(Color.white);

        allInSyncButton = new JButtonMini(new MyOpenFoldersBaseAction(
            getController()));
        allInSyncButton.setIcon(Icons.getIconById(Icons.SYNC_COMPLETE));
        allInSyncButton.setText(null);

        syncingButton = new SyncIconButtonMini(getController());
        syncingButton.addActionListener(new MyOpenFoldersBaseAction(
            getController()));
        syncingButton.setVisible(false);

        setupButton = new JButtonMini(new MySetupAction());
        setupButton.setIcon(Icons.getIconById(Icons.ACTION_ARROW));
        setupButton.setText(null);

        upperMainTextLabel = new ActionLabel(getController(),
            new SwitchCompactMode());
        // syncTextLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // syncTextLabel.addMouseListener(new SwitchCompactMode());

        syncDateLabel = new ActionLabel(getController(),
            new SwitchCompactMode());
        setupLabel = new ActionLabel(getController(), new MySetupAction());
        // syncDateLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // syncDateLabel.addMouseListener(new SwitchCompactModeByMouse());

        loginActionLabel = new ActionLabel(getController(), new MyLoginAction(
            getController()));
        noticesActionLabel = new ActionLabel(getController(),
            new MyShowNoticesAction(getController()));
        updateNoticesLabel();

        usagePB = new JProgressBar();
        usagePB.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        usagePB.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    PFWizard.openLoginWizard(getController(), client);
                }
            }
        });

        expandCollapseAction = new MyExpandCollapseAction(getController());
        expandCollapseActionLabel = new ActionLabel(getController(),
            expandCollapseAction);
        openWebInterfaceActionLabel = new ActionLabel(getController(),
            new MyOpenWebInterfaceAction(getController()));
        openFoldersBaseActionLabel = new ActionLabel(getController(),
            new MyOpenFoldersBaseAction(getController()));
        pauseResumeActionLabel = new ActionLabel(getController(),
            new MyPauseResumeAction(getController()));
        configurationActionLabel = new ActionLabel(getController(),
            getApplicationModel().getActionModel().getOpenPreferencesAction());
        openDebugActionLabel = new ActionLabel(getController(),
            new MyOpenDebugAction(getController()));
        openTransfersActionLabel = new ActionLabel(getController(),
            new MyOpenTransfersAction(getController()));

        // add window listener, checks if exit is needed on pressing X
        MyWindowListener myWindowListener = new MyWindowListener();
        uiComponent.addWindowListener(myWindowListener);
        uiComponent.addWindowStateListener(myWindowListener);

        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setOneTouchExpandable(false);

        // everything is decided in window listener
        uiComponent
            .setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        logoLabel = new JLabel();
        logoLabel.setIcon(Icons.getIconById(Icons.LOGO400UI));
        logoLabel.setHorizontalAlignment(SwingConstants.LEFT);

        logoLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MyMouseWindowDragListener logoMouseListener = new MyMouseWindowDragListener();
        logoLabel.addMouseListener(logoMouseListener);
        logoLabel.addMouseMotionListener(logoMouseListener);

        closeButton = new JButton3Icons(
            Icons.getIconById(Icons.FILTER_TEXT_FIELD_CLEAR_BUTTON_NORMAL),
            Icons.getIconById(Icons.FILTER_TEXT_FIELD_CLEAR_BUTTON_HOVER),
            Icons.getIconById(Icons.FILTER_TEXT_FIELD_CLEAR_BUTTON_PUSH));
        closeButton.setToolTipText(Translation
            .getTranslation("main_frame.close.tips"));
        closeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doCloseOperation();
            }
        });

        plusButton = new JButton3Icons(
            Icons.getIconById(Icons.WINDOW_PLUS_NORMAL),
            Icons.getIconById(Icons.WINDOW_PLUS_HOVER),
            Icons.getIconById(Icons.WINDOW_PLUS_PUSH));
        plusButton.setVisible(false);

        minusButton = new JButton3Icons(
            Icons.getIconById(Icons.WINDOW_MINUS_NORMAL),
            Icons.getIconById(Icons.WINDOW_MINUS_HOVER),
            Icons.getIconById(Icons.WINDOW_MINUS_PUSH));
        minusButton.setVisible(false);

        centralPanel = new JPanel(new BorderLayout(0, 0));

        mainTabbedPane = new MainTabbedPane(getController());

        updateTitle();

        inlineInfoCloseButton = new JButton3Icons(
            Icons.getIconById(Icons.FILTER_TEXT_FIELD_CLEAR_BUTTON_NORMAL),
            Icons.getIconById(Icons.FILTER_TEXT_FIELD_CLEAR_BUTTON_HOVER),
            Icons.getIconById(Icons.FILTER_TEXT_FIELD_CLEAR_BUTTON_PUSH));
        inlineInfoCloseButton.setToolTipText(Translation
            .getTranslation("main_frame.inline_info_close.tip"));
        inlineInfoCloseButton
            .addActionListener(new MyInlineCloseInfoActionListener());
        inlineInfoCloseButton.setContentAreaFilled(false);

        inlineInfoLabel = new JLabel();

        getController().addPausedModeListener(new MyPausedModeListener());
        configurePauseResumeLink();

        client = getApplicationModel().getServerClientModel().getClient();
        client.addListener(new MyServerClientListener());

        getApplicationModel().getFolderRepositoryModel()
            .addOverallFolderStatListener(new MyOverallFolderStatListener());

        nodeManagerListener = new MyNodeManagerListener();
        getController().getNodeManager().addWeakNodeManagerListener(
            nodeManagerListener);

        getController().getFolderRepository().addFolderRepositoryListener(
            new MyFolderRepoListener());

        // Init
        setCompactMode(compact.get(), true);

        // Start listening to notice changes.
        getController().getUIController().getApplicationModel()
            .getNoticesModel().getAllNoticesCountVM()
            .addValueChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    updateNoticesLabel();
                }
            });
        getController().getUIController().getApplicationModel()
            .getNoticesModel().getUnreadNoticesCountVM()
            .addValueChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    updateNoticesLabel();
                }
            });
    }

    /**
     * Force UI on top if compact, but only if there are no wizards or dialogs
     * open.
     */
    public void checkOnTop() {
        boolean onTop = uiComponent.isAlwaysOnTopSupported() && compact.get()
                && !PFWizard.isWizardOpen() && !BaseDialog.isDialogOpen();
        uiComponent.setAlwaysOnTop(onTop);
    }

    private void updateMainStatus() {
        mainStatusUpdater.schedule(new Runnable() {
            public void run() {
                updateMainStatus0();
            }
        });
    }

    private void updateMainStatus0() {
        FolderRepositoryModel folderRepositoryModel = getUIController()
            .getApplicationModel().getFolderRepositoryModel();
        boolean syncing = folderRepositoryModel.isSyncing();
        Date syncDate;
        if (syncing) {
            syncDate = folderRepositoryModel.getEstimatedSyncDate();
        } else {
            syncDate = folderRepositoryModel.getLastSyncDate();
        }
        double overallSyncPercentage = folderRepositoryModel
            .getOverallSyncPercentage();

        String upperText;
        String setupText = null;
        boolean synced = false;
        boolean setup = false;
        if (!getController().getNodeManager().isStarted()) {
            // Not started
            upperText = Translation.getTranslation("main_frame.not_running");
            setup = true;
            setupText = Translation.getTranslation("main_frame.activate_now");
        } else if (getController().getFolderRepository().getFoldersCount() == 0)
        {
            // No folders
            upperText = Translation.getTranslation("main_frame.no_folders");
            setup = true;
            setupText = getApplicationModel().getActionModel()
                .getNewFolderAction().getName();
        } else if (syncDate == null && !syncing) { // Never synced
            upperText = Translation.getTranslation("main_frame.never_synced");
        } else {
            if (syncing) {
                upperText = Translation.getTranslation("main_frame.syncing",
                    Format.formatDecimal(overallSyncPercentage));
            } else {
                upperText = Translation.getTranslation("main_frame.in_sync");
                synced = true;
            }
        }
        upperMainTextLabel.setText(upperText);
        if (setupText != null) {
            setupLabel.setText(setupText);
        }

        String dateText = " ";
        if (syncDate != null) {
            if (!DateUtil.isDateMoreThanNDaysInFuture(syncDate, 2)) {
                String date = Format.formatDateShort(syncDate);
                boolean inFuture = syncDate.after(new Date());
                dateText = inFuture ? Translation.getTranslation(
                    "main_frame.sync_eta", date) : Translation.getTranslation(
                    "main_frame.last_synced", date);
            }
        }
        syncDateLabel.setText(dateText);

        syncDateLabel.setVisible(!setup);
        setupLabel.setVisible(setup);

        if (setup) {
            setupButton.setVisible(true);
            syncingButton.setVisible(false);
            syncingButton.spin(false);
            allInSyncButton.setVisible(false);
        } else {
            setupButton.setVisible(false);
            syncingButton.setVisible(!synced);
            syncingButton.spin(!synced);
            allInSyncButton.setVisible(synced);
        }
    }

    /**
     * Updates the title
     */
    public void updateTitle() {
        StringBuilder title = new StringBuilder();

        String appName = Translation.getTranslation("general.application.name");
        // Urg
        if (StringUtils.isEmpty(appName) || appName.startsWith("- ")) {
            appName = "PowerFolder";
        }
        title.append(appName);

        if (getController().isVerbose()) {
            // Append in front of program name in verbose mode
            title.append(" v" + Controller.PROGRAM_VERSION);
            if (getController().getBuildTime() != null) {
                title.append(" | build: " + getController().getBuildTime());
            }
            title.append(" | " + getController().getMySelf().getNick());
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        if (cal.get(Calendar.DAY_OF_MONTH) == 21
            && cal.get(Calendar.MONTH) == 2)
        {
            title.append(" | Happy birthday archi !");
        }
        uiComponent.setTitle(title.toString());
    }

    /**
     * @return the ui panel of the mainframe.
     */
    public JFrame getUIComponent() {
        return uiComponent;
    }

    /**
     * Add a change listener to the main tabbed pane selection.
     * 
     * @param l
     */
    public void addTabbedPaneChangeListener(ChangeListener l) {
        mainTabbedPane.addTabbedPaneChangeListener(l);
    }

    /**
     * Remove a change listener from the main tabbed pane.
     * 
     * @param l
     */
    public void removeTabbedPaneChangeListener(ChangeListener l) {
        mainTabbedPane.removeTabbedPaneChangeListener(l);
    }

    /**
     * Stores all current window values.
     */
    public void storeValues() {
        // Store main window preferences
        Controller c = getController();

        PreferencesEntry.MAIN_FRAME_WIDTH.setValue(c, mainWidth);

        if (isMaximized()) {
            PreferencesEntry.MAIN_FRAME_MAXIMIZED.setValue(c, true);
        } else {
            PreferencesEntry.MAIN_FRAME_MAXIMIZED.setValue(c, false);

            PreferencesEntry.MAIN_FRAME_X.setValue(c, uiComponent.getX());
            PreferencesEntry.MAIN_FRAME_Y.setValue(c, uiComponent.getY());

            // If info is inline and info is showing, do not store width because
            // info will not show at start up and the frame will be W-I-D-E.

            if (uiComponent.getWidth() > 0
                && (!shouldShowInfoInline() || !isShowingInfoInline()))
            {
                PreferencesEntry.MAIN_FRAME_WIDTH.setValue(c,
                    uiComponent.getWidth());
            }

            if (uiComponent.getHeight() > 0) {
                PreferencesEntry.MAIN_FRAME_HEIGHT.setValue(c,
                    uiComponent.getHeight());
            }
        }
    }

    /**
     * @return true, if application is currently minimized
     */
    public boolean isIconified() {
        return (uiComponent.getExtendedState() & Frame.ICONIFIED) != 0;
    }

    /**
     * @return true, if application is currently minimized
     */
    public boolean isMaximized() {
        return (uiComponent.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
    }

    /**
     * Determine if application is currently minimized or hidden (for example,
     * in the systray)
     * 
     * @return true, if application is currently minimized or hidden
     */
    public boolean isIconifiedOrHidden() {
        return isIconified() || !uiComponent.isVisible();
    }

    /**
     * @return the selected main tab index.
     */
    public int getSelectedMainTabIndex() {
        return mainTabbedPane.getSelectedTabIndex();
    }

    /**
     * Shows the folders tab.
     */
    public void showFoldersTab() {
        mainTabbedPane.setActiveTab(MainTabbedPane.FOLDERS_INDEX);
    }

    public void showInlineInfoPanel(JPanel panel, String title) {
        // Fix Synthetica maximization, otherwise it covers the task
        // bar. See
        // http://www.javasoft.de/jsf/public/products/synthetica/faq#q13
        RootPaneUI ui = uiComponent.getRootPane().getUI();
        if (ui instanceof SyntheticaRootPaneUI) {
            ((SyntheticaRootPaneUI) ui).setMaximizedBounds(uiComponent);
        }

        if (isShowingInfoInline()) {
            mainWidth = split.getDividerLocation();
        } else if (!isMaximized()) {
            mainWidth = uiComponent.getWidth();
        }

        inlineInfoPanel = panel;
        inlineInfoLabel.setText(title);

        configureInlineInfo();
    }

    private void closeInlineInfoPanel() {
        if (isShowingInfoInline()) {
            mainWidth = split.getDividerLocation();
        }
        inlineInfoPanel = null;
        configureInlineInfo();
    }

    public boolean shouldShowInfoInline() {
        int inline = PreferencesEntry.INLINE_INFO_MODE
            .getValueInt(getController());
        return inline != 0;
    }

    public boolean isShowingInfoInline() {
        return inlineInfoPanel != null;
    }

    private void configureInlineInfo() {
        boolean inline = shouldShowInfoInline();
        boolean displaying = isShowingInfoInline();
        inlineInfoCloseButton.setVisible(inline && displaying);

        if (inline && displaying) {
            // Make sure the info inline panel does not take the full width
            // and hiding the main tabbed pane
            // inlineInfoPanel.setSize(new Dimension(inlineInfoPanel
            // .getMinimumSize().width, inlineInfoPanel.getHeight()));

            centralPanel.removeAll();
            split.setLeftComponent(mainTabbedPane.getUIComponent());
            split.setRightComponent(inlineInfoPanel);

            final int dividerLocation = mainWidth;
            centralPanel.add(split, BorderLayout.CENTER);
            split.setDividerLocation(dividerLocation);

            // No clue why this have to be done later.
            // However if not this change does not come thru
            // on the first time the inline component/splitpane is shown.
            UIUtil.invokeLaterInEDT(new Runnable() {
                public void run() {
                    split.setDividerLocation(dividerLocation);
                }
            });
        } else {
            // Splitpane place holders
            split.setLeftComponent(new JPanel());
            split.setRightComponent(new JPanel());

            centralPanel.removeAll();
            centralPanel.add(mainTabbedPane.getUIComponent(),
                BorderLayout.CENTER);
            inlineInfoPanel = null;
            inlineInfoLabel.setText("");
            if (!isMaximized()) {
                uiComponent.setSize(mainWidth, uiComponent.getSize().height);
            }
        }

        relocateIfNecessary();
    }

    /**
     * Did we move the UI outside the screen boundary?
     */
    private void relocateIfNecessary() {
        if (isIconified() || isMaximized()) {
            // Don't care.
            return;
        }
        GraphicsEnvironment ge = GraphicsEnvironment
            .getLocalGraphicsEnvironment();
        if (ge.getScreenDevices().length != 1) {
            // TODO: Relocate on any screen
            return;
        }

        // Now adjust for off-screen problems.
        int uiY = uiComponent.getY();
        int uiX = uiComponent.getX();

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int screenWidth = (int) screenSize.getWidth();
        int uiWidth = uiComponent.getWidth();
        if (uiX < 0) {
            uiComponent.setLocation(0, uiY);
        }
        if (uiX + uiWidth > screenWidth) {
            uiComponent.setLocation(screenWidth - uiWidth, uiY);
        }
        if (uiY < 0 || uiY > (int) screenSize.getHeight()) {
            uiComponent.setLocation(uiComponent.getX(), 0);
        }
    }

    /**
     * Source:
     * http://stackoverflow.com/questions/309023/howto-bring-a-java-window
     * -to-the-front
     */
    public void toFront() {
        uiComponent.setVisible(true);
        int state = uiComponent.getExtendedState();
        state &= ~Frame.ICONIFIED;
        uiComponent.setExtendedState(state);
        boolean onTop = uiComponent.isAlwaysOnTop();
        uiComponent.setAlwaysOnTop(true);
        uiComponent.toFront();
        uiComponent.requestFocus();
        uiComponent.setAlwaysOnTop(onTop);
    }

    private void doCloseOperation() {
        if (OSUtil.isSystraySupported()) {
            handleExitFirstRequest();
            boolean quitOnX = PreferencesEntry.QUIT_ON_X
                .getValueBoolean(getController());
            if (quitOnX) {
                exitProgram();
            } else {
                getUIController().hideChildPanels();
                uiComponent.setVisible(false);
            }
        } else {
            // Quit if systray is not Supported by OS.
            exitProgram();
        }
    }

    /**
     * Shuts down the program
     */
    private void exitProgram() {
        if (getUIController().isShutdownAllowed()) {
            uiComponent.setVisible(false);
            uiComponent.dispose();
            new Thread("Close PowerFolder Thread") {
                @Override
                public void run() {
                    getController().exit(0);
                }
            }.start();
        }
    }

    // ////////////////
    // Inner Classes //
    // ////////////////

    @SuppressWarnings("serial")
    private class SwitchCompactMode extends AbstractAction {
        public void actionPerformed(ActionEvent e) {
            switchCompactMode();
        }
    }

    private class MyWindowFocusListner implements WindowFocusListener {
        public void windowGainedFocus(WindowEvent e) {
            getUIController().setActiveFrame(UIController.MAIN_FRAME_ID);
        }

        public void windowLostFocus(WindowEvent e) {
            // Nothing to do here.
        }
    }

    private class MyWindowListener extends WindowAdapter {

        public void windowClosing(WindowEvent e) {
            doCloseOperation();
        }

        /**
         * Hide other frames when main frame gets minimized.
         * 
         * @param e
         */
        public void windowIconified(WindowEvent e) {
            boolean minToSysTray = PreferencesEntry.MIN_TO_SYS_TRAY
                .getValueBoolean(getController());
            if (minToSysTray) {
                getUIController().hideChildPanels();
                uiComponent.setVisible(false);
            } else {
                super.windowIconified(e);
            }
        }
    }

    private class MyFolderRepositoryListener implements
        FolderRepositoryListener
    {

        // If showing the inline panel and the folder has been removed,
        // close the inline panel.
        public void folderRemoved(FolderRepositoryEvent e) {
            if (isShowingInfoInline()) {
                closeInlineInfoPanel();
            }
        }

        public void folderCreated(FolderRepositoryEvent e) {
            // Don't care.
        }

        public void maintenanceStarted(FolderRepositoryEvent e) {
            // Don't care.
        }

        public void maintenanceFinished(FolderRepositoryEvent e) {
            // Don't care.
        }

        public boolean fireInEventDispatchThread() {
            return true;
        }
    }

    private void configurePauseResumeLink() {
        if (getController().isPaused()) {
            pauseResumeActionLabel.setText(Translation
                .getTranslation("action_resume_sync.name"));
            pauseResumeActionLabel.setToolTipText(Translation
                .getTranslation("action_resume_sync.description"));
        } else {
            pauseResumeActionLabel.setText(Translation
                .getTranslation("action_pause_sync.name"));
            pauseResumeActionLabel.setToolTipText(Translation
                .getTranslation("action_pause_sync.description"));
        }
    }

    private void updateOnlineStorageDetails() {
        double percentageUsed = 0;
        long totalStorage = 0;
        long spaceUsed = 0;
        if (StringUtils.isBlank(client.getUsername())) {
            loginActionLabel.setText(Translation
                .getTranslation("main_frame.account_not_set.text"));
        } else if (client.isPasswordEmpty()) {
            loginActionLabel.setText(Translation
                .getTranslation("main_frame.password_required.text"));
        } else if (client.isConnected()) {
            if (client.isLoggedIn()) {
                OnlineStorageSubscription storageSubscription = client
                    .getAccount().getOSSubscription();
                AccountDetails ad = client.getAccountDetails();
                if (storageSubscription.isDisabled()) {
                    loginActionLabel.setText(Translation
                        .getTranslation("main_frame.account_disabled.text"));
                } else {
                    totalStorage = storageSubscription.getStorageSize();
                    spaceUsed = ad.getSpaceUsed();
                    if (totalStorage > 0) {
                        percentageUsed = 100.0d * (double) spaceUsed
                            / (double) totalStorage;
                    }
                    percentageUsed = Math.max(0.0d, percentageUsed);
                    percentageUsed = Math.min(100.0d, percentageUsed);
                    String s = client.getUsername();
                    if (!StringUtils.isEmpty(s)) {
                        loginActionLabel.setText(s);
                    }
                }
            } else if (client.isLoggingIn()) {
                loginActionLabel.setText(Translation
                    .getTranslation("main_frame.loging_in.text"));
            } else {
                // Not logged in and not logging in? Looks like it has failed.
                loginActionLabel.setText(Translation
                    .getTranslation("main_frame.log_in_failed.text"));
            }
        } else {
            loginActionLabel.setText(Translation
                .getTranslation("main_frame.connecting.text"));
        }
        usagePB.setValue((int) percentageUsed);
        usagePB.setToolTipText(Format.formatBytesShort(spaceUsed) + " / "
            + Format.formatBytesShort(totalStorage));
    }

    private void switchCompactMode() {
        boolean compactMe = !compact.getAndSet(!compact.get());
        setCompactMode(compactMe, false);
    }

    private void setCompactMode(boolean compactMe, boolean init) {

        // @todo this will need some rework when the main frame is maximizable.

        expandCollapseAction.setShowExpand(compactMe);

        int oldY = uiComponent.getY();
        int oldH = uiComponent.getHeight();

        if (compactMe) {

            // Need to hide the child windows when minimize.
            if (!init) {
                closeInlineInfoPanel();
                getUIController().hideChildPanels();
            }

            uiComponent.setSize(uiComponent.getMinimumSize());
            uiComponent.setResizable(false);

            toFront();
        } else {
            uiComponent.setSize(uiComponent.getWidth(),
                    UIConstants.MAIN_FRAME_DEFAULT_HEIGHT);
            uiComponent.setResizable(true);
        }

        // Try to maintain the lower window location,
        // as this is where the user clicked open / collapse.
        if (!init) {
            int oldB = oldY + oldH;
            int newY = uiComponent.getY();
            int newH = uiComponent.getHeight();
            int newB = newY + newH;
            int diff = newB - oldB;
            int targetY = newY - diff;
            uiComponent.setLocation(uiComponent.getX(), Math.max(targetY, 0));
        }

        checkOnTop();
    }

    // ////////////////
    // Inner classes //
    // ////////////////

    private class MyInlineCloseInfoActionListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            Object source = e.getSource();
            if (source == inlineInfoCloseButton) {
                closeInlineInfoPanel();
            }
        }
    }

    private class MyPausedModeListener implements PausedModeListener {

        public boolean fireInEventDispatchThread() {
            return true;
        }

        public void setPausedMode(PausedModeEvent event) {
            configurePauseResumeLink();
        }
    }

    private class MyOpenWebInterfaceAction extends BaseAction {

        private MyOpenWebInterfaceAction(Controller controller) {
            super("action_open_web_interface", controller);
        }

        public void actionPerformed(ActionEvent e) {
            try {
                BrowserLauncher.openURL(client.getLoginURLWithCredentials());
            } catch (IOException e1) {
                logWarning("Unable to open web portal", e1);
            }
        }
    }

    private class MyExpandCollapseAction extends BaseAction {

        private MyExpandCollapseAction(Controller controller) {
            super("action_expand_interface", controller);
        }

        public void actionPerformed(ActionEvent e) {
            switchCompactMode();
        }

        public void setShowExpand(boolean expand) {
            if (expand) {
                configureFromActionId("action_expand_interface");
            } else {
                configureFromActionId("action_collapse_interface");
            }
        }
    }

    private static class MyOpenFoldersBaseAction extends BaseAction {

        private MyOpenFoldersBaseAction(Controller controller) {
            super("action_open_folders_base", controller);
        }

        public void actionPerformed(ActionEvent e) {
            FileUtils.openFile(getController().getFolderRepository()
                .getFoldersAbsoluteDir());
        }
    }

    private class MySetupAction extends AbstractAction {

        public void actionPerformed(ActionEvent e) {
            if (!getController().getNodeManager().isStarted()) {
                getApplicationModel().getLicenseModel().getActivationAction()
                    .actionPerformed(e);
            } else if (getController().getFolderRepository().getFoldersCount() == 0)
            {
                getApplicationModel().getActionModel().getNewFolderAction()
                    .actionPerformed(e);
            }
        }
    }

    private static class MyPauseResumeAction extends BaseAction {

        private MyPauseResumeAction(Controller controller) {
            super("action_pause_sync", controller);
        }

        public void actionPerformed(ActionEvent e) {
            getUIController().askToPauseResume();
        }
    }

    private static class MyOpenTransfersAction extends BaseAction {

        private MyOpenTransfersAction(Controller controller) {
            super("action_open_tansfers_information", controller);
        }

        public void actionPerformed(ActionEvent e) {
            getUIController().openTransfersInformation();
        }
    }

    private static class MyOpenDebugAction extends BaseAction {

        private MyOpenDebugAction(Controller controller) {
            super("action_open_debug_information", controller);
        }

        public void actionPerformed(ActionEvent e) {
            getUIController().openDebugInformation();
        }
    }

    private class MyServerClientListener implements ServerClientListener {

        public boolean fireInEventDispatchThread() {
            return true;
        }

        public void accountUpdated(ServerClientEvent event) {
            updateOnlineStorageDetails();
        }

        public void login(ServerClientEvent event) {
            updateOnlineStorageDetails();
        }

        public void serverConnected(ServerClientEvent event) {
            updateOnlineStorageDetails();
        }

        public void serverDisconnected(ServerClientEvent event) {
            updateOnlineStorageDetails();
        }

        public void nodeServerStatusChanged(ServerClientEvent event) {
            updateOnlineStorageDetails();
        }
    }

    private class MyOverallFolderStatListener implements
        OverallFolderStatListener
    {
        public void statCalculated() {
            updateMainStatus();
        }

        public boolean fireInEventDispatchThread() {
            return true;
        }
    }

    private class MyNodeManagerListener extends NodeManagerAdapter {

        @Override
        public void startStop(NodeManagerEvent e) {
            updateMainStatus();
        }

        public boolean fireInEventDispatchThread() {
            return true;
        }

    }

    private class MyFolderRepoListener implements FolderRepositoryListener {

        public boolean fireInEventDispatchThread() {
            return true;
        }

        public void folderRemoved(FolderRepositoryEvent e) {
            updateMainStatus();
        }

        public void folderCreated(FolderRepositoryEvent e) {
            updateMainStatus();

        }

        public void maintenanceStarted(FolderRepositoryEvent e) {
        }

        public void maintenanceFinished(FolderRepositoryEvent e) {
        }

    }

    private class MyLoginAction extends BaseAction {

        MyLoginAction(Controller controller) {
            super("action_login", controller);
        }

        public void actionPerformed(ActionEvent e) {
            PFWizard.openLoginWizard(getController(), client);
        }
    }

    private static class MyShowNoticesAction extends BaseAction {

        MyShowNoticesAction(Controller controller) {
            super("action_show_notices", controller);
        }

        public void actionPerformed(ActionEvent e) {
            getController().getUIController().openNoticesCard();
        }
    }

    private class MyMouseWindowDragListener extends MouseAdapter {
        private int startX;
        private int startY;
        private boolean inDrag;

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {

                if (isMaximized()) {
                    uiComponent.setExtendedState(Frame.NORMAL);
                }

                if (isShowingInfoInline()) {
                    if (!isMaximized()) {
                        uiComponent.setExtendedState(Frame.MAXIMIZED_BOTH);
                    }
                } else {
                    switchCompactMode();
                }
            }
        }

        /** Called when the mouse has been pressed. */
        public void mousePressed(MouseEvent e) {
            Point p = e.getPoint();
            startX = p.x;
            startY = p.y;
            inDrag = true;
        }

        /** Called when the mouse has been released. */
        public void mouseReleased(MouseEvent e) {
            inDrag = false;
        }

        // And two methods from MouseMotionListener:
        public void mouseDragged(MouseEvent e) {
            Point p = e.getPoint();
            if (inDrag) {
                int dx = p.x - startX;
                int dy = p.y - startY;
                Point l = uiComponent.getLocation();
                uiComponent.setLocation(l.x + dx, l.y + dy);
            }
        }
    }

    /**
     * Handle drag-n-drops of Folders direct into the application.
     */
    private class MyTransferHandler extends TransferHandler {
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @SuppressWarnings({"unchecked"})
        public boolean importData(TransferSupport support) {
            try {
                Transferable t = support.getTransferable();
                List<File> fileList = (List<File>)
                        t.getTransferData(DataFlavor.javaFileListFlavor);

                // One at a time!
                if (fileList == null || fileList.size() != 1) {
                    return false;
                }

                // Directories only.
                File file = fileList.get(0);
                if (!file.isDirectory()) {
                    return false;
                }
                
                // Make sure we do not already have this as a folder.
                if (!getController().getFolderRepository()
                        .doesFolderAlreadyExist(file)) {
                    PFWizard.openExistingDirectoryWizard(getController(), file);
                }
            } catch (UnsupportedFlavorException e) {
                logSevere(e);
                return false;
            } catch (IOException e) {
                logSevere(e);
                return false;
            }
            return true;
        }
    }

}
