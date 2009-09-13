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
package de.dal33t.powerfolder.ui.widget;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.SystemColor;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.PreferencesEntry;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.ui.ColorUtil;

/**
 * A Label which executes the action when clicked.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.4 $
 */
public class ActionLabel extends PFComponent {

    private JLabel uiComponent;
    private volatile boolean enabled = true;
    private String text;
    private Action action;
    private volatile boolean mouseOver;

    public ActionLabel(Controller controller, Action action) {
        super(controller);
        this.action = action;
        uiComponent = new JLabel();
        text = (String) action.getValue(Action.NAME);
        displayText();
        String toolTips = (String) action.getValue(Action.SHORT_DESCRIPTION);
        if (toolTips != null && toolTips.length() > 0) {
            uiComponent.setToolTipText(toolTips);
        }
        Reject.ifNull(action, "Action listener is null");
        uiComponent.addMouseListener(new MyMouseAdapter());
        uiComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public JComponent getUIComponent() {
        return uiComponent;
    }

    /**
     * IMPORTANT - make component text changes here, not in the uiComponent.
     * Otherwise mouse-over activity will over-write the text.
     * 
     * @param text
     */
    public void setText(String text) {
        this.text = text;
        displayText();
    }

    public void setIcon(Icon icon) {
        uiComponent.setIcon(icon);
    }

    public void setToolTipText(String text) {
        uiComponent.setToolTipText(text);
    }

    public void setForeground(Color c) {
        uiComponent.setForeground(c);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        displayText();
    }

    public void displayText() {
        if (enabled) {
            if (mouseOver
                || PreferencesEntry.UNDERLINE_LINKS
                    .getValueBoolean(getController()))
            {
                Color color = ColorUtil.getTextForegroundColor();
                String rgb = ColorUtil.getRgbForColor(color);
                uiComponent.setText("<html><font color=\"" + rgb
                    + "\"><a href=\"#\">" + text + "</a></font></html>");
            } else {
                uiComponent.setForeground(SystemColor.textText);
                uiComponent.setText(text);
            }
        } else {
            uiComponent.setForeground(SystemColor.textInactiveText);
            uiComponent.setText(text);
        }
    }

    private class MyMouseAdapter extends MouseAdapter {

        public void mouseEntered(MouseEvent e) {
            mouseOver = true;
            displayText();
        }

        public void mouseExited(MouseEvent e) {
            mouseOver = false;
            displayText();
        }

        public void mouseClicked(MouseEvent e) {
            if (enabled) {
                action.actionPerformed(new ActionEvent(e.getSource(), 0,
                    "clicked"));
            }
        }
    }
}
