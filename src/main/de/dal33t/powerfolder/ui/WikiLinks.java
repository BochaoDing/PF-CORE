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
 * $Id: WikiLinks.java 7764 2009-04-24 10:31:44Z tot $
 */
package de.dal33t.powerfolder.ui;

/**
 * Repository of links to Wiki pages. Any references to the PowerFolder Wiki
 * should go here, so that we can ensure the Wiki is up to date.
 */
public interface WikiLinks {

    String SETTINGS_GENERAL = "Settings-General";
    String SETTINGS_UI = "Settings-UI";
    String SETTINGS_NETWORK = "Settings-Network";
    String SETTINGS_DIALOG = "Settings-Dialog";
    String SETTINGS_DYN_DNS = "DYN-Dns";
    String SETTINGS_ADVANCED = "Settings-Advanced";
    String SETTINGS_PLUGIN = "Settings-Plugin";
    String PROBLEM_UNSYNCED_FOLDER = "Unsynchronized-Folder";
    String PROBLEM_DUPLICATE_FILENAME = "Duplicate-Filename";
    String PROBLEM_ILLEGAL_END_CHARS = "Illegal-End-Chars";
    String PROBLEM_ILLEGAL_CHARS = "Illegal-Chars";
    String PROBLEM_RESERVED_WORD = "Reserved-Word";
    String PROBLEM_FILENAME_TOO_LONG = "File-Name-Too-Long";
    String PROBLEM_NO_CONFLICT_DETECTION_POSSIBLE = "Version_Conflict_With_Old_Client";
    String SCRIPT_EXECUTION = "Script_execution";
    String DEFAULT_FOLDER = "Default_Folder";
}
