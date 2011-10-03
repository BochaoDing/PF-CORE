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

import java.util.logging.Level;

import com.jgoodies.binding.adapter.PreferencesAdapter;
import com.jgoodies.binding.value.ValueModel;

import de.dal33t.powerfolder.skin.LightSky;
import de.dal33t.powerfolder.ui.information.folder.files.DirectoryFilter;
import de.dal33t.powerfolder.util.Reject;

/**
 * Refelects an entry setting in the preferences. Provides basic method for
 * accessing and setting the prefs. Preferences are stored (on windows) in the
 * registry.
 */
public enum PreferencesEntry {
    /**
     * Show offline members
     */
    NODE_MANAGER_MODEL_SHOW_OFFLINE("node_manager_model_show_offline", true),
    /** find offline users */
    FRIEND_SEARCH_HIDE_OFFLINE("FriendsSearch_HideOfflineUsers", false),

    QUIT_ON_X("quitonx", false),

    ASK_FOR_QUIT_ON_X("AskForQuitOnX", true),

    WARN_ON_CLOSE("WarnOnClose", true),

    ASK_FOR_FRIENDSHIP_ON_PRIVATE_FOLDER_JOIN(
        "AskForFriendshipOnPrivateFolderJoin", true),

    ASK_FOR_FRIENDSHIP_MESSAGE("AskForFriendshipMessage", true),

    SHOW_ADVANCED_SETTINGS("ShowAdvancedSettings", false),

    UNDERLINE_LINKS("UnderlineLinks", false),

    FILE_NAME_CHECK("folder.check_filenames", true),

    CHECK_UPDATE("updatechecker.askfornewreleaseversion", true),

    /**
     * Whether to show chat notifications when minimized.
     */
    SHOW_CHAT_NOTIFICATIONS("show.chat.notifications", true),

    /**
     * Whether to show system notifications when minimized.
     */
    SHOW_SYSTEM_NOTIFICATIONS("show.system.notifications", true),

    @Deprecated
    MASS_DELETE_PROTECTION("mass.delete.protection", true),

    @Deprecated
    MASS_DELETE_THRESHOLD("mass.delete.threshold", 75),

    /**
     * the pref that holds a boolean value if the connection should be tested
     * and a warning displayed if limited connectivty is given.
     */
    TEST_CONNECTIVITY("test_for_connectivity", true),

    /** Warn user if connection is poor. */
    WARN_POOR_QUALITY("warn.poor.quality", false),

    /**
     * Warn if changing a transfer mode for multiple folders
     */
    DUPLICATE_FOLDER_USE("duplicate_folder_use", true),

    SETUP_DEFAULT_FOLDER("setup_default_folder", false),

    /**
     * If the last password of login should be reminded.
     */
    SERVER_REMEMBER_PASSWORD("server_remind_password", true),

    DOCUMENT_LOGGING("document.logging", Level.WARNING.getName()),

    AUTO_EXPAND("auto.expand", false),

    /** Whether the user uses OS. If not, don't show OS stuff. */
    USE_ONLINE_STORAGE("use.os", true),

    /** How many seconds the notification should display. */
    NOTIFICATION_DISPLAY("notification.display", 10),

    /** How translucent the notification should display, as percentage. */
    NOTIFICATION_TRANSLUCENT("notification.translucent", 0),

    /** Skin name. */
    SKIN_NAME("skin.name", LightSky.NAME),

    /** Minimize to system tray */
    MIN_TO_SYS_TRAY("min.to.sys.tray", false),

    /** The 'Show offline' checkbox on the ComputersTab. */
    SHOW_OFFLINE("show.offline", true),

    /** Main frame always on top. */
    MAIN_ALWAYS_ON_TOP("main.stay.on.top", false),
    /**
     * Show the information tab with the mainframe 0=free, 1=docked. info
     * on the right.
     */
    INLINE_INFO_MODE("inline.info.mode", 1),

    MAIN_FRAME_WIDTH("mainframe4.width", 350),

    MAIN_FRAME_HEIGHT("mainframe4.height", -1),
    
    INFO_WIDTH("infoframe4.width", -1),

    MAIN_FRAME_X("mainframe4.x", Constants.UI_DEFAULT_SCREEN_BORDER),

    MAIN_FRAME_Y("mainframe4.y", Constants.UI_DEFAULT_SCREEN_BORDER),
    
    MAIN_FRAME_MAXIMIZED("mainframe.maximized", false),

    FILE_SEARCH_MODE("file.search.mode",
        DirectoryFilter.SEARCH_MODE_FILE_NAME_DIRECTORY_NAME),

    /**
     * If the "Tell a friend" / Referral system should be visible.
     */
    SHOW_TELL_A_FRIEND("show.tell-a-friend", true),

    SHOW_AUTO_CREATED_FOLDERS("show.auto.created.folders", true),
    
    /**
     * #2327
     */
    SHOW_TYPICAL_FOLDERS("show.typical.folders", true),

    DISPLAY_POWERFOLDERS_SHORTCUT("display.powerfolders.shortcut", true),

    FOLDER_LOCAL_COLLAPSED("folder.local.collapsed", false),

    FOLDER_TYPICAL_COLLAPSED("folder.typical.collapsed", true),
    
    FOLDER_ONLINE_COLLAPSED("folder.online.collapsed", false);

    /** String, Boolean, Integer */
    private Class type;

    private String preferencesKey;
    private Object defaultValue;

    // Methods/Constructors ***************************************************

    PreferencesEntry(String aPreferencesKey, boolean theDefaultValue) {
        Reject.ifBlank(aPreferencesKey, "Preferences key is blank");
        type = Boolean.class;
        preferencesKey = aPreferencesKey;
        defaultValue = theDefaultValue;
    }

    PreferencesEntry(String aPreferencesKey, int theDefaultValue) {
        Reject.ifBlank(aPreferencesKey, "Preferences key is blank");
        type = Integer.class;
        preferencesKey = aPreferencesKey;
        defaultValue = theDefaultValue;
    }

    PreferencesEntry(String aPreferencesKey, String theDefaultValue) {
        Reject.ifBlank(aPreferencesKey, "Preferences key is blank");
        type = String.class;
        preferencesKey = aPreferencesKey;
        defaultValue = theDefaultValue;
    }

    /**
     * @param controller
     *            the controller to read the config from
     * @return The current value from the configuration for this entry. or
     */
    public String getValueString(Controller controller) {
        if (!type.isAssignableFrom(String.class)) {
            throw new IllegalStateException("This preferences entry has type "
                + type.getName() + " cannot acces as String");
        }
        return controller.getPreferences().get(preferencesKey,
            (String) defaultValue);
    }

    public String getDefaultValue() {
        return (String) defaultValue;
    }

    public Integer getDefaultValueInt() {
        return (Integer) defaultValue;
    }

    /**
     * the preferences entry if its a Integer.
     * 
     * @param controller
     *            the controller to read the config from
     * @return The current value from the preferences for this entry. or the
     *         default value if value not set.
     */
    public Integer getValueInt(Controller controller) {
        if (!type.isAssignableFrom(Integer.class)) {
            throw new IllegalStateException("This preferences entry has type "
                + type.getName() + " cannot access as Integer");
        }
        return controller.getPreferences().getInt(preferencesKey,
            (Integer) defaultValue);
    }

    /**
     * Parses the configuration entry into a Boolen.
     * 
     * @param controller
     *            the controller to read the config from
     * @return The current value from the configuration for this entry. or the
     *         default value if value not set/unparseable.
     */
    public Boolean getValueBoolean(Controller controller) {
        if (!type.isAssignableFrom(Boolean.class)) {
            throw new IllegalStateException("This preferences entry has type "
                + type.getName() + " cannot access as Boolean");
        }
        return controller.getPreferences().getBoolean(preferencesKey,
            (Boolean) defaultValue);
    }

    /**
     * Constructs a preferences adapter which is directly bound to the
     * preferences entry.
     * 
     * @param controller
     *            the controller
     * @return the model bound to the pref entry.
     */
    public ValueModel getModel(Controller controller) {
        Reject.ifNull(controller, "Controller is null");
        return new PreferencesAdapter(controller.getPreferences(),
            preferencesKey, defaultValue);
    }

    /**
     * Sets the value of this preferences entry.
     * 
     * @param controller
     *            the controller of the prefs
     * @param value
     *            the value to set
     */
    public void setValue(Controller controller, String value) {
        Reject.ifNull(controller, "Controller is null");
        if (!type.isAssignableFrom(String.class)) {
            throw new IllegalStateException("This preferences entry has type "
                + type.getName() + " cannot set as String");
        }
        controller.getPreferences().put(preferencesKey, value);
    }

    /**
     * Sets the value of this preferences entry.
     * 
     * @param controller
     *            the controller of the prefs
     * @param value
     *            the value to set
     */
    public void setValue(Controller controller, boolean value) {
        Reject.ifNull(controller, "Controller is null");
        if (!type.isAssignableFrom(Boolean.class)) {
            throw new IllegalStateException("This preferences entry has type "
                + type.getName() + " cannot set as Boolean");
        }
        controller.getPreferences().putBoolean(preferencesKey, value);
    }

    /**
     * Sets the value of this preferences entry.
     * 
     * @param controller
     *            the controller of the prefs
     * @param value
     *            the value to set
     */
    public void setValue(Controller controller, int value) {
        Reject.ifNull(controller, "Controller is null");
        if (!type.isAssignableFrom(Integer.class)) {
            throw new IllegalStateException("This preferences entry has type "
                + type.getName() + " cannot set as Integer");
        }
        controller.getPreferences().putInt(preferencesKey, value);
    }

    /**
     * Removes the entry from the preferences.
     * 
     * @param controller
     *            the controller to use
     */
    public void removeValue(Controller controller) {
        Reject.ifNull(controller, "Controller is null");
        controller.getPreferences().remove(preferencesKey);
    }
}
