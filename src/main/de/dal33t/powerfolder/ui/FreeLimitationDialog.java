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

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.factories.ButtonBarFactory;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.ui.widget.LinkLabel;
import de.dal33t.powerfolder.util.BrowserLauncher;
import de.dal33t.powerfolder.util.ProUtil;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.ui.BaseDialog;

/**
 * A dialog that gets displayed when the free version hits its limits.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc</a>
 * @version $Revision: 1.5 $
 */
public class FreeLimitationDialog extends BaseDialog {

    private JButton buyProButton;

    protected FreeLimitationDialog(Controller controller) {
        super(controller, false);
    }

    @Override
    protected Icon getIcon() {
        return Icons.getIconById(Icons.SMALL_LOGO);
    }

    @Override
    public String getTitle() {
        return Translation.getTranslation("free_limit_dialog.title");
    }

    @Override
    protected Component getButtonBar() {
        return buildToolbar();
    }

    @Override
    protected JComponent getContent() {
        FormLayout layout = new FormLayout("pref:grow",
            "pref, 2dlu, pref, 14dlu, pref, 3dlu, pref, 3dlu, pref, 14dlu, pref");
        PanelBuilder builder = new PanelBuilder(layout);

        CellConstraints cc = new CellConstraints();
        int row = 1;
        builder.addLabel(Translation
            .getTranslation("free_limit_dialog.heavy_usage_detected"), cc.xy(1,
            row));
        row += 2;
        builder.addLabel(Translation.getTranslation("free_limit_dialog.reason",
            "3", "5"), cc.xy(1, row));
        row += 2;
        builder.addLabel(Translation
            .getTranslation("free_limit_dialog.buy_recommendation"), cc.xy(1,
            row));
        row += 2;
        builder.addLabel(Translation
            .getTranslation("free_limit_dialog.buy_recommendation2"), cc.xy(1,
            row));
        row += 2;
        builder.addLabel(Translation
            .getTranslation("free_limit_dialog.buy_recommendation3"), cc.xy(1,
            row));
        row += 2;
        LinkLabel linkLabel = new LinkLabel(getController(), Translation
            .getTranslation("free_limit_dialog.whatispro"), ProUtil
            .getBuyNowURL(getController()));
        builder.add(linkLabel.getUIComponent(), cc.xy(1, row));

        return builder.getPanel();
    }

    protected JButton getDefaultButton() {
        return buyProButton;
    }

    private Component buildToolbar() {
        buyProButton = new JButton(Translation
            .getTranslation("free_limit_dialog.buy_pro"));
        buyProButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    BrowserLauncher.openURL(ProUtil
                        .getBuyNowURL(getController()));
                } catch (IOException e1) {
                    logSevere("IOException", e1);
                }
            }
        });

        JButton reduceButton = new JButton(Translation
            .getTranslation("free_limit_dialog.reduce"));
        reduceButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                close();
            }
        });
        return ButtonBarFactory.buildCenteredBar(buyProButton, reduceButton);
    }

}
