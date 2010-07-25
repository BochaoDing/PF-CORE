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
 * $Id: FolderCreatePanel.java 9117 2009-08-21 14:22:42Z harry $
 */
package de.dal33t.powerfolder.ui.wizard;

import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

import jwf.Wizard;
import jwf.WizardPanel;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.ui.Icons;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.StringUtils;
import de.dal33t.powerfolder.util.Translation;

/**
 * A panel that actually starts a swingworker process on display. Automatically
 * switches to the next panel when succeeded otherwise prints error.
 * 
 * @author Christian Sprajc
 * @version $Revision$
 */
public class SwingWorkerPanel extends PFWizardPanel {

    private static final Logger LOG = Logger.getLogger(SwingWorkerPanel.class
        .getName());

    private JLabel statusLabel;
    private JLabel problemLabel;
    private JProgressBar bar;
    private SwingWorker<Void, Void> worker;
    private Runnable task;
    private String title;
    private String text;
    private WizardPanel nextPanel;

    public SwingWorkerPanel(Controller controller, Runnable task, String title,
        String text, WizardPanel nextPanel)
    {
        super(controller);
        this.title = title;
        this.text = text;
        this.nextPanel = nextPanel;
        this.task = task;
    }

    public void setTask(Runnable task) {
        this.task = task;
    }

    @Override
    public boolean canFinish() {
        return false;
    }

    @Override
    protected JPanel buildContent() {
        FormLayout layout = new FormLayout("140dlu, 0:grow", "pref, 7dlu, pref");

        PanelBuilder builder = new PanelBuilder(layout);
        builder.setBorder(createFewContentBorder());
        CellConstraints cc = new CellConstraints();
        int row = 1;

        statusLabel = builder.addLabel(text, cc.xyw(1, row, 2));
        row += 2;

        bar = new JProgressBar();
        bar.setIndeterminate(true);
        builder.add(bar, cc.xy(1, row));

        problemLabel = new JLabel();
        problemLabel.setIcon(Icons.getIconById(Icons.WARNING));
        problemLabel.setVisible(false);
        builder.add(problemLabel, cc.xyw(1, row, 2));

        return builder.getPanel();
    }

    @Override
    protected void afterDisplay() {
        if (worker != null) {
            worker.cancel(true);
            // Back one more.
            getWizard().back();
        } else {
            bar.setVisible(true);
            problemLabel.setVisible(false);
            updateButtons();
            worker = new MySwingWorker();
            worker.execute();
        }
    }

    @Override
    protected void initComponents() {
    }

    @Override
    protected String getTitle() {
        return !isProblem() ? title : Translation
            .getTranslation("wizard.worker.problem");
    }

    @Override
    public boolean hasNext() {
        // Always automatically goes to next
        return false;
    }

    @Override
    public WizardPanel next() {
        return nextPanel;
    }

    private void showProblem(String problem) {
        LOG.warning(problem);
        bar.setVisible(false);
        statusLabel.setText(" ");
        problemLabel.setText(problem.replace("de.dal33t.powerfolder.", ""));
        problemLabel.setVisible(true);
        updateButtons();
        updateTitle();
    }

    private boolean isProblem() {
        return problemLabel.isVisible();
    }

    private class MySwingWorker extends SwingWorker<Void, Void> {

        @Override
        public void done() {
            bar.setVisible(false);
            try {
                get();

                // Next
                Wizard wiz = getWizard();
                if (wiz.getCurrentPanel() == SwingWorkerPanel.this) {
                    // Go to next if still visible.
                    wiz.next();
                }
            } catch (InterruptedException e) {
                return;
            } catch (ExecutionException e) {
                String msg = e.getCause().getMessage();
                if (StringUtils.isBlank(msg)) {
                    msg = e.getMessage();
                }
                if (StringUtils.isBlank(msg)) {
                    msg = e.toString();
                }
                LOG.warning(msg);
                LOG.log(Level.WARNING, e.toString(), e);
                showProblem(msg);
            }
        }

        @Override
        protected Void doInBackground() throws Exception {
            Reject.ifNull(task, "No task found to execute");
            task.run();
            return null;
        }
    }
}
