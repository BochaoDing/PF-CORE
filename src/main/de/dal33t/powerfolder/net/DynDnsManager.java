package de.dal33t.powerfolder.net;

import java.awt.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import org.apache.commons.lang.StringUtils;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.Borders;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.ui.dialog.ErrorDialog;
import de.dal33t.powerfolder.ui.preferences.DynDnsSettingsTab;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.ui.DialogFactory;

/**
 * The DynDnsManager class is responsible for: - to provide services to the
 * DynDns updates and, - DynDns validation as well as some UI utility methods.
 * 
 * @author Albena Roshelova
 */

public class DynDnsManager extends PFComponent {

    private static final long DYNDNS_TIMER_INTERVAL = 1000 * 60 * 5;
    private TimerTask updateTask;
    private Thread updateThread;

    private Hashtable<String, DynDns> dynDnsTable;
    public DynDns activeDynDns;
    public String externalIP;
    private ErrorDialog errorDialog;

    private JDialog uiComponent;

    public DynDnsManager(Controller controller) {
        super(controller);

        registerDynDns("DynDnsOrg", new DynDnsOrg(controller));
        errorDialog = new ErrorDialog(controller, true);
    }

    /*
     * RegisterDynDns methods register the dyndns source
     */
    public void registerDynDns(String dynDnsId, DynDns dynDns) {
        dynDnsTable = new Hashtable<String, DynDns>();
        dynDns.setDynDnsManager(this);
        dynDnsTable.put(dynDnsId, dynDns);
    }

    public String getUsername() {
        if (DynDnsSettingsTab.username == null) {
            return ConfigurationEntry.DYNDNS_USERNAME.getValue(getController());
        }
        return DynDnsSettingsTab.username;
    }

    public String getUserPassword() {
        if (DynDnsSettingsTab.password == null) {
            return ConfigurationEntry.DYNDNS_PASSWORD.getValue(getController());
        }
        return DynDnsSettingsTab.password;
    }

    public String getHost2Update() {
        if (DynDnsSettingsTab.newDyndns == null) {
            return ConfigurationEntry.DYNDNS_HOSTNAME.getValue(getController());
        }
        return DynDnsSettingsTab.newDyndns;
    }

    public void fillDefaultUpdateData(DynDnsUpdateData updateData) {
        updateData.username = getUsername();
        updateData.pass = getUserPassword();
        updateData.host = getHost2Update();
        updateData.ipAddress = getIPviaHTTPCheckIP();
    }

    /**
     * Validates given dynDns for compatibility with the current host
     * 
     * @param dynDns
     *            to validate
     * @return true if validation succeeded, false otherwise
     */
    public boolean validateDynDns(String dynDns) {

        // validates the dynamic dns entry if there is one entered
        if (!StringUtils.isBlank(dynDns)) {
            if (getController().hasConnectionListener()) {

                // sets the new dyndns with validation enabled
                int res = getController().getConnectionListener().setMyDynDns(
                    dynDns, true);

                // check the result from validation
                switch (res) {
                    case ConnectionListener.VALIDATION_FAILED :

                        // validation failed ask the user if he/she
                        // wants to continue with these settings
                        String message = Translation
                            .getTranslation("preferences.dialog.dyndnsmanager.nomatch.text");
                        String title = Translation
                            .getTranslation("preferences.dialog.dyndnsmanager.nomatch.title");

                        int result = DialogFactory.showConfirmDialog(
                                getController().getUIController().getMainFrame().getUIComponent(),
                                title, message, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                        if (result == JOptionPane.YES_OPTION) {
                            // the user is happy with his/her settings, then
                            // set the new dyndns without further validation
                            getController().getConnectionListener()
                                .setMyDynDns(dynDns, false);
                        } else {
                            // the user wants to change the dyndns settings
                            getController().getConnectionListener()
                                .setMyDynDns(null, false);
                            return false;
                        }
                        break;
                    case ConnectionListener.CANNOT_RESOLVE :
                        // the new dyndns could not be resolved
                        // force the user to enter a new one
                        getController().getConnectionListener().setMyDynDns(
                            null, false);
                        return false;

                    case ConnectionListener.OK :
                        log().info(
                            "Successfully validated dyndns '" + dynDns + '\'');
                        // getController().getUIController()
                        // .showMessage(null,
                        // "Success",
                        // Translation.getTranslation("preferences.dialog.statusDynDnsSuccess",
                        // dynDns));
                }
            }

        } else {
            // just resets the dyndns entry
            if (getController().getConnectionListener() != null) {
                getController().getConnectionListener()
                    .setMyDynDns(null, false);
            }
        }
        // all validations have passed
        return true;
    }

    /**
     * Shows warning message to the user in case the validation goes wrong
     * 
     * @param type
     *            of validation failure
     * @param arg
     *            additional argument (dyndns)
     */
    public void showWarningMsg(int type, String arg) {
        switch (type) {
            case ConnectionListener.VALIDATION_FAILED :
                getController().getUIController().showWarningMessage(
                    Translation
                        .getTranslation("preferences.dialog.warnningMessage"),
                    Translation.getTranslation(
                        "preferences.dialog.statusValidFailed", arg));
                break;

            case ConnectionListener.CANNOT_RESOLVE :
                getController().getUIController().showWarningMessage(
                    Translation
                        .getTranslation("preferences.dialog.warnningMessage"),
                    Translation.getTranslation(
                        "preferences.dialog.statusValidFailed", arg));
        }
    }

    public void showPanelErrorMessage() {
        String err = "";
        if (getHost2Update().equals(""))
            err = "hostname";
        else if (getUsername().equals(""))
            err = "username";
        else if (getUserPassword().equals(""))
            err = "password";

        getController().getUIController().showErrorMessage(
            Translation.getTranslation("preferences.dialog.dyndnsUpdateTitle"),
            "The field " + err + " can not be empty!", null);
    }

    /**
     *
     */
    public void showDynDnsUpdaterMsg(int type) {

        switch (type) {
            case ErrorManager.NO_ERROR :
                DialogFactory.showInfoDialog(getController().getUIController().getMainFrame().getUIComponent(),
                        Translation.getTranslation("preferences.dialog.dyndnsUpdateTitle"),
                        activeDynDns.getErrorText());
                break;

            case ErrorManager.WARN :
            case ErrorManager.ERROR :
                errorDialog.open(activeDynDns.getErrorText(), type);
                break;

            case ErrorManager.UNKNOWN :
                getController()
                    .getUIController()
                    .showErrorMessage(
                        Translation
                            .getTranslation("preferences.dialog.dyndnsUpdateTitle"),
                        Translation
                            .getTranslation("preferences.dialog.dyndnsUpdateUnknowError"),
                        null);
                break;

        }
    }

    /**
     * close the wait message box
     */
    public final void close() {
        log().verbose("Close called: " + this);
        if (uiComponent != null) {
            uiComponent.dispose();
            uiComponent = null;
        }
    }

    /**
     * Shows (and builds) the wait message box
     */
    public final void show(String dyndns) {
        log().verbose("Open called: " + this);
        getUIComponent(dyndns).setVisible(true);
    }

    /**
     * retrieves the title of the message box
     * 
     * @return
     */
    private String getTitle() {
        return Translation.getTranslation("preferences.dialog.titleProcessing");
    }

    /**
     * saves updated ip to the config file
     * 
     * @param updateData
     */
    private void saveUpdatedIP(DynDnsUpdateData updateData) {
        ConfigurationEntry.DYNDNS_LAST_UPDATED_IP.setValue(getController(),
            updateData.ipAddress);
        // save
        getController().saveConfig();
    }

    /**
     * Setups the UI for the wait message box
     * 
     * @param dyndns
     * @return
     */
    protected final JDialog getUIComponent(String dyndns) {
        if (uiComponent == null) {
            log().verbose("Building ui component for " + this);
            uiComponent = new JDialog(getController().getUIController()
                .getMainFrame().getUIComponent(), getTitle());
            uiComponent.setResizable(false);

            uiComponent.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            FormLayout layout = new FormLayout("pref, 14dlu, pref:grow",
                "pref, pref:grow, 7dlu, pref");
            PanelBuilder builder = new PanelBuilder(layout);
            builder.setBorder(Borders.DLU14_BORDER);

            CellConstraints cc = new CellConstraints();

            // Build
            int xpos = 1, ypos = 1, wpos = 1, hpos = 1;
            builder.add(new JLabel(Translation.getTranslation(
                "preferences.dialog.statusWaitDynDns", dyndns)), cc.xywh(xpos,
                ypos, wpos, hpos));

            // Add panel to component
            uiComponent.getContentPane().add(builder.getPanel());

            uiComponent.pack();
            Component parent = uiComponent.getOwner();
            int x = parent.getX()
                + (parent.getWidth() - uiComponent.getWidth()) / 2;
            int y = parent.getY()
                + (parent.getHeight() - uiComponent.getHeight()) / 2;
            uiComponent.setLocation(x, y);
        }
        return uiComponent;
    }

    /**
     * Checks if the current dyndns host is still valid (=matches the real ip).
     * 
     * @return false, if the dyndns service should be updated.
     */
    private boolean dyndnsValid() {
        String dyndnsIP = getHostIP(ConfigurationEntry.DYNDNS_HOSTNAME
            .getValue(getController()));
        if (StringUtils.isEmpty(dyndnsIP)) {
            return true;
        }

        String myHostIP = getIPviaHTTPCheckIP();
        String lastUpdatedIP = ConfigurationEntry.DYNDNS_LAST_UPDATED_IP
            .getValue(getController());
        log().debug(
            "Dyndns hostname IP: " + dyndnsIP + ". Real IP: " + myHostIP
                + ". Last update IP: " + lastUpdatedIP);
        if (dyndnsIP.equals(myHostIP)) {
            return true;
        }
        // If host did non change...
        if (myHostIP.equals(lastUpdatedIP)) {
            return true;
        }

        return false;
    }

    /**
     * Forces an update of the DynDNS service.
     * 
     * @return The update result
     */
    private int updateDynDNS() {
        activeDynDns = (DynDns) dynDnsTable.get("DynDnsOrg");
        DynDnsUpdateData updateData = activeDynDns.getDynDnsUpdateData();
        int res = activeDynDns.update(updateData);

        log().info("Updated dyndns. Result: " + res);
        if (res == ErrorManager.NO_ERROR) {
            saveUpdatedIP(updateData);
        }

        log().verbose("the updated dyndns > " + externalIP);
        return res;
    }

    public void forceUpdate() {
        showDynDnsUpdaterMsg(updateDynDNS());
    }

    /**
     * Updates DYNDNS if neccessary.
     */
    public synchronized void updateIfNessesary() {
        if (!ConfigurationEntry.DYNDNS_AUTO_UPDATE.getValueBoolean(
            getController()).booleanValue())
        {
            return;
        }
        if (updateTask == null) {
            setupUpdateTask();
            log().verbose("DNS Autoupdate requested. Starting updater.");
        }

        if (updateThread != null) {
            log().debug("No dyndns update performed. Already running");
            return;
        }

        // Perform this by a seperate Thread
        updateThread = new Thread("DynDns Updater") {
            @Override
            public void run()
            {
                boolean dyndnsIsValid = dyndnsValid();
                log().debug(
                    "Dyndns updater start. Update required? " + dyndnsIsValid);
                if (!dyndnsIsValid) {
                    updateDynDNS();
                } else {
                    log().verbose("No dyndns update performed: IP still valid");
                }
                log().verbose("Dyndns updater finished");
                updateThread = null;
            }
        };
        updateThread.start();
    }

    /**
     * Start updating Timer.
     */
    private void setupUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        updateTask = new TimerTask() {
            @Override
            public void run()
            {
                updateIfNessesary();
            }
        };
        getController().scheduleAndRepeat(updateTask, 0, DYNDNS_TIMER_INTERVAL);
    }

    /**
     * Returns dyndns IP address
     * 
     * @param newDns
     */

    public String getHostIP(String host) {

        String strDyndnsIP = "";

        if (host == null)
            return "";

        try {
            InetAddress myDyndnsIP = InetAddress.getByName(host);
            if (myDyndnsIP != null) {
                strDyndnsIP = myDyndnsIP.getHostAddress();
            }
        } catch (IllegalArgumentException ex) {
            log().error("Can't get the host ip address" + ex.toString());
        } catch (UnknownHostException ex) {
            log().error("Can't get the host ip address" + ex.toString());
        }
        return strDyndnsIP;
    }

    /**
     * Returns the internet address of this machine.
     * 
     * @return the ip address or empty string if none is found
     */

    public String getIPviaHTTPCheckIP() {

        String ipAddr = "";

        try {
            URL dyndns = new URL("http://checkip.dyndns.org/");
            URLConnection urlConn = dyndns.openConnection();
            int length = urlConn.getContentLength();
            ByteArrayOutputStream tempBuffer;

            if (length < 0) {
                tempBuffer = new ByteArrayOutputStream();
            } else {
                tempBuffer = new ByteArrayOutputStream(length);
            }

            InputStream inStream = urlConn.getInputStream();

            int ch;
            while ((ch = inStream.read()) >= 0) {
                tempBuffer.write(ch);
            }

            String ipText = tempBuffer.toString();
            log().verbose("Received '" + ipText + "' from " + dyndns);
            ipAddr = filterIPs(ipText);

        } catch (IOException e) {
        }

        // return the ip address or empty string if none is found
        return ipAddr;
    }

    /**
     * Parse the HTML string and filter everything out but the ip address
     * 
     * @param str
     * @return
     */
    private String filterIPs(String txt) {
        String ip = null;
        Pattern p = Pattern
            .compile("[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}");
        Matcher m = p.matcher(txt);

        if (m.find()) {
            // ip match is found
            ip = txt.substring(m.start(), m.end());
        }
        return ip;
    }

}