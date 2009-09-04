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
package de.dal33t.powerfolder.ui.wizard;

import static de.dal33t.powerfolder.ui.wizard.WizardContextAttributes.FOLDERINFO_ATTRIBUTE;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import jwf.WizardPanel;

import com.jgoodies.binding.adapter.BasicComponentFactory;
import com.jgoodies.binding.value.ValueHolder;
import com.jgoodies.binding.value.ValueModel;
import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.Sizes;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.light.AccountInfo;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.message.Invitation;
import de.dal33t.powerfolder.security.FolderPermission;
import de.dal33t.powerfolder.security.FolderReadWritePermission;
import de.dal33t.powerfolder.ui.WikiLinks;
import de.dal33t.powerfolder.ui.action.BaseAction;
import de.dal33t.powerfolder.ui.dialog.NodesSelectDialog2;
import de.dal33t.powerfolder.ui.widget.ActionLabel;
import de.dal33t.powerfolder.ui.widget.AutoTextField;
import de.dal33t.powerfolder.ui.widget.JButtonMini;
import de.dal33t.powerfolder.util.BrowserLauncher;
import de.dal33t.powerfolder.util.Help;
import de.dal33t.powerfolder.util.InvitationUtil;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.Util;
import de.dal33t.powerfolder.util.ui.UIUtil;

/**
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.12 $
 */
public class SendInvitationsPanel extends PFWizardPanel {
    private static final Logger LOG = Logger
        .getLogger(SendInvitationsAdvancedPanel.class.getName());

    private JButtonMini addButton;
    private JButtonMini searchButton;
    private JButtonMini removeButton;
    private AutoTextField viaPowerFolderText;
    private JList inviteesList;
    private JScrollPane inviteesListScrollPane;
    private ActionLabel advancedLink;
    private ActionLabel addMessageLink;
    private ValueModel locationModel;
    private ValueModel permissionsModel;
    private ActionLabel inviteInfoLabel;
    private DefaultListModel inviteesListModel;
    private Invitation invitation;
    private ValueModel messageModel;
    private JPanel removeButtonPanel;
    private JComponent messageComp;

    public SendInvitationsPanel(Controller controller) {
        super(controller);
    }

    /**
     * Handles the invitation to nodes option.
     * 
     * @return true if send otherwise false
     */
    private boolean sendInvitation() {
        if (invitation == null) {
            return false;
        }
        FolderPermission fp = (FolderPermission) permissionsModel.getValue();
        if (fp == null) {
            fp = new FolderReadWritePermission(invitation.folder);
        }
        invitation.setPermission(fp);
        boolean theResult = false;
        Collection<Member> candidates = getCandidates();

        // Send invite from text or list.
        if (viaPowerFolderText.getText().length() > 0) {
            sendInvite(candidates, viaPowerFolderText.getText());
            theResult = true;
        }
        for (Object o : inviteesListModel.toArray()) {
            String invitee = (String) o;
            sendInvite(candidates, invitee);
            theResult = true;
        }

        Object value = locationModel.getValue();
        if (value != null && ((String) value).length() > 0) {
            File file = new File((String) value, constructInviteFileName());
            InvitationUtil.invitationToDisk(getController(), invitation, file);
            theResult = true;
        }

        return theResult;
    }

    /**
     * Send an invite to a friend. The invitee must be in the list of friends or
     * be a valid email.
     * 
     * @param friends
     * @param invitee
     */
    private void sendInvite(Collection<Member> candidates, String invitee) {
        for (Member member : candidates) {
            if (invitee.equalsIgnoreCase(member.getNick())) {
                InvitationUtil.invitationToNode(getController(), invitation,
                    member);
                if (member.getAccountInfo() != null) {
                    try {
                        InvitationUtil.invitationByServer(getController(),
                            invitation, member.getAccountInfo().getUsername(),
                            false);
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "Unable to send invitation to "
                            + member + " / " + member.getAccountInfo() + ". "
                            + e, e);
                    }

                }
                break;
            }
        }
        if (Util.isValidEmail(invitee)) {
            try {
                InvitationUtil.invitationByServer(getController(), invitation,
                    invitee, false);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Unable to send invitation to " + invitee
                    + ". " + e, e);
            }
        }
    }

    public boolean hasNext() {
        return !inviteesListModel.isEmpty()
            || viaPowerFolderText.getText().length() > 0
            || locationModel.getValue() != null
            && ((String) locationModel.getValue()).length() > 0;
    }

    public boolean validateNext() {
        return true;
    }

    public WizardPanel next() {
        if (messageModel.getValue() != null) {
            invitation.setInvitationText((String) messageModel.getValue());
        }
        Runnable inviteTask = new Runnable() {
            public void run() {
                if (messageModel.getValue() != null) {
                    invitation.setInvitationText((String) messageModel
                        .getValue());
                }
                if (!sendInvitation()) {
                    throw new RuntimeException(Translation
                        .getTranslation("wizard.send_invitations.no_invitees"));
                }
            }

        };
        WizardPanel successPanel = (WizardPanel) getWizardContext()
            .getAttribute(PFWizard.SUCCESS_PANEL);
        return new SwingWorkerPanel(
            getController(),
            inviteTask,
            Translation
                .getTranslation("wizard.send_invitations.sending_invites"),
            Translation
                .getTranslation("wizard.send_invitations.sending_invites.text"),
            successPanel);
    }

    protected JPanel buildContent() {
        FormLayout layout = new FormLayout(
            "140dlu, pref:grow",
            "pref, 3dlu, pref, 3dlu, pref, max(9dlu;pref), 3dlu, pref, 20dlu, pref, 3dlu, pref");
        // inv join text inv fdl hint1 hint2 auto list remove adv
        PanelBuilder builder = new PanelBuilder(layout);
        builder.setBorder(createFewContentBorder());
        CellConstraints cc = new CellConstraints();
        int row = 1;

        builder.addLabel(Translation
            .getTranslation("wizard.send_invitations.invitation_hint1"), cc
            .xyw(1, row, 2));
        row += 2;

        FormLayout layout2 = new FormLayout("pref:grow, 3dlu, pref, pref",
            "pref");
        PanelBuilder builder2 = new PanelBuilder(layout2);
        builder2.add(viaPowerFolderText, cc.xy(1, 1));
        builder2.add(addButton, cc.xy(3, 1));
        builder2.add(searchButton, cc.xy(4, 1));
        JPanel panel2 = builder2.getPanel();
        panel2.setOpaque(false);
        builder.add(panel2, cc.xy(1, row));
        row += 2;

        inviteesListScrollPane = new JScrollPane(inviteesList);
        inviteesListScrollPane.setPreferredSize(new Dimension(
            getPreferredSize().width, Sizes.dialogUnitYAsPixel(40,
                inviteesListScrollPane)));
        builder.add(inviteesListScrollPane, cc.xy(1, row));
        inviteesListScrollPane.setVisible(false);
        row += 1;

        FormLayout layout3 = new FormLayout("pref, pref:grow", "pref");
        PanelBuilder builder3 = new PanelBuilder(layout3);
        builder3.add(removeButton, cc.xy(1, 1));
        removeButtonPanel = builder3.getPanel();
        removeButtonPanel.setOpaque(false);
        builder.add(removeButtonPanel, cc.xy(1, row));
        removeButtonPanel.setVisible(false);
        row += 2;

        builder.add(addMessageLink.getUIComponent(), cc.xy(1, row));
        builder.add(messageComp, cc.xy(1, row));
        row += 2;

        builder.add(advancedLink.getUIComponent(), cc.xy(1, row));
        row += 2;

        builder.add(inviteInfoLabel.getUIComponent(), cc.xyw(1, row, 2));

        return builder.getPanel();
    }

    /**
     * Initializes all necessary components
     */
    protected void initComponents() {
        messageModel = new ValueHolder();

        FolderInfo folder = (FolderInfo) getWizardContext().getAttribute(
            FOLDERINFO_ATTRIBUTE);
        Reject.ifNull(folder, "Unable to send invitation, folder is null");

        // Clear folder attribute
        getWizardContext().setAttribute(FOLDERINFO_ATTRIBUTE, null);

        invitation = folder.getFolder(getController()).createInvitation();

        addButton = new JButtonMini(new MyAddAction(getController()));
        removeButton = new JButtonMini(new MyRemoveAction(getController()));
        searchButton = new JButtonMini(new MySearchAction(getController()));

        inviteInfoLabel = new ActionLabel(getController(), new AbstractAction()
        {
            public void actionPerformed(ActionEvent e) {
                String wikiArticleURL = Help.getWikiArticleURL(getController(),
                    WikiLinks.INVITATIONS);
                try {
                    BrowserLauncher.openURL(wikiArticleURL);
                } catch (IOException e1) {
                    // Hmmm.
                }
            }
        });

        inviteInfoLabel.setText(Translation
            .getTranslation("wizard.send_invitations.information"));

        viaPowerFolderText = new AutoTextField();
        viaPowerFolderText.addKeyListener(new MyKeyListener());

        inviteesListModel = new DefaultListModel();
        inviteesList = new JList(inviteesListModel);
        inviteesList.getSelectionModel().setSelectionMode(
            ListSelectionModel.SINGLE_SELECTION);
        inviteesList.getSelectionModel().addListSelectionListener(
            new MyListSelectionListener());

        List<String> candidateAddresses = getCandidatesAddresses();
        viaPowerFolderText.setDataList(candidateAddresses);
        advancedLink = new ActionLabel(getController(), new MyAdvanceAction(
            getController()));

        addMessageLink = new ActionLabel(getController(),
            new MyAttachMessageAction());
        addMessageLink.setText(Translation
            .getTranslation("wizard.send_invitations.add_message.text"));
        addMessageLink.setToolTipText(Translation
            .getTranslation("wizard.send_invitations.add_message.tip"));
        UIUtil.convertToBigLabel((JLabel) addMessageLink.getUIComponent());

        locationModel = new ValueHolder("");
        locationModel.addValueChangeListener(new MyPropertyChangeListener());

        permissionsModel = new ValueHolder(
            new FolderReadWritePermission(folder), true);

        JScrollPane messagePane = new JScrollPane(BasicComponentFactory
            .createTextArea(messageModel));
        FormLayout layout2 = new FormLayout("fill:140dlu",
            "pref, 3dlu, fill:40dlu");
        PanelBuilder builder2 = new PanelBuilder(layout2);
        CellConstraints cc = new CellConstraints();
        builder2.addLabel(Translation
            .getTranslation("dialog.personalized_message.hint"), cc.xy(1, 1));
        builder2.add(messagePane, cc.xy(1, 3));
        messageComp = builder2.getPanel();
        messageComp.setVisible(false);
        messageComp.setOpaque(false);

        enableAddButton();
        enableRemoveButton();

    }

    private List<String> getCandidatesAddresses() {
        List<String> candidateAddresses = new ArrayList<String>();
        for (Member friend : getController().getNodeManager().getFriends()) {
            AccountInfo aInfo = friend.getAccountInfo();
            if (aInfo != null) {
                // FIXME Shows email unscrambled!
                candidateAddresses.add(aInfo.getUsername());
            }
            candidateAddresses.add(friend.getNick());
        }
        for (Member node : getController().getNodeManager().getConnectedNodes())
        {
            if (!node.isOnLAN()) {
                continue;
            }
            AccountInfo aInfo = node.getAccountInfo();
            if (aInfo != null) {
                // FIXME Shows email unscrambled!
                candidateAddresses.add(aInfo.getUsername());
            }
            candidateAddresses.add(node.getNick());
        }
        return candidateAddresses;
    }

    private List<Member> getCandidates() {
        List<Member> candidate = new ArrayList<Member>();
        for (Member friend : getController().getNodeManager().getFriends()) {
            candidate.add(friend);
        }
        for (Member node : getController().getNodeManager().getConnectedNodes())
        {
            if (!node.isOnLAN()) {
                continue;
            }
            candidate.add(node);
        }
        return candidate;
    }

    protected String getTitle() {
        return Translation.getTranslation("wizard.send_invitations.title");
    }

    private void enableAddButton() {
        addButton.setEnabled(viaPowerFolderText.getText().length() > 0);
    }

    private void enableRemoveButton() {
        removeButton.setEnabled(!inviteesListModel.isEmpty()
            && inviteesList.getSelectedIndex() >= 0);
    }

    private void processInvitee() {
        String text = viaPowerFolderText.getText();
        if (text.length() > 0) {
            inviteesListModel.addElement(text);
            inviteesListScrollPane.setVisible(true);
            removeButtonPanel.setVisible(true);
            viaPowerFolderText.clear();
            updateButtons();
            enableAddButton();
            enableRemoveButton();
        }
    }

    private String constructInviteFileName() {
        return invitation.folder.name + ".invitation";
    }

    // /////////////////
    // Inner classes //
    // /////////////////

    private class MyAddAction extends BaseAction {

        MyAddAction(Controller controller) {
            super("action_add_invitee", controller);
        }

        public void actionPerformed(ActionEvent e) {
            processInvitee();
        }
    }

    private class MySearchAction extends BaseAction {

        MySearchAction(Controller controller) {
            super("action_search_invitee", controller);
        }

        public void actionPerformed(ActionEvent e) {

            Collection<Member> selectedMembers = new ArrayList<Member>();
            NodesSelectDialog2 nsd2 = new NodesSelectDialog2(getController(),
                selectedMembers);
            nsd2.open();
            for (Member selectedMember : selectedMembers) {
                boolean got = false;
                for (Object o : inviteesListModel.toArray()) {
                    String invitee = (String) o;
                    if (selectedMember.getNick().equals(invitee)) {
                        got = true;
                        break;
                    }
                }
                if (!got) {
                    inviteesListModel.addElement(selectedMember.getNick());
                    inviteesListScrollPane.setVisible(true);
                    removeButtonPanel.setVisible(true);
                }
            }
            updateButtons();
        }
    }

    private class MyRemoveAction extends BaseAction {

        MyRemoveAction(Controller controller) {
            super("action_remove_invitee", controller);
        }

        public void actionPerformed(ActionEvent e) {
            int index = inviteesList.getSelectedIndex();
            if (index >= 0) {
                inviteesListModel.remove(index);
                inviteesListScrollPane.setVisible(!inviteesListModel.isEmpty());
                removeButtonPanel.setVisible(!inviteesListModel.isEmpty());
                enableRemoveButton();
            }
        }
    }

    private class MyKeyListener implements KeyListener {

        public void keyTyped(KeyEvent e) {
        }

        public void keyPressed(KeyEvent e) {
        }

        public void keyReleased(KeyEvent e) {
            updateButtons();
            enableAddButton();
        }
    }

    private class MyListSelectionListener implements ListSelectionListener {

        public void valueChanged(ListSelectionEvent e) {
            enableRemoveButton();
            updateButtons();
        }
    }

    private class MyAdvanceAction extends BaseAction {

        MyAdvanceAction(Controller controller) {
            super("action_invite_advanced", controller);
        }

        public void actionPerformed(ActionEvent e) {
            SendInvitationsAdvancedPanel advPanel = new SendInvitationsAdvancedPanel(
                getController(), invitation.folder, locationModel,
                permissionsModel, constructInviteFileName());
            advPanel.open();
        }
    }

    private class MyAttachMessageAction extends AbstractAction {

        public void actionPerformed(ActionEvent e) {
            messageComp.setVisible(true);
            addMessageLink.getUIComponent().setVisible(false);
            // AttachPersonalizedMessageDialog d = new
            // AttachPersonalizedMessageDialog(
            // getController(), messageModel);
            // d.open();
        }
    }

    private class MyPropertyChangeListener implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getSource() == locationModel) {
                updateButtons();
            }
        }
    }
}