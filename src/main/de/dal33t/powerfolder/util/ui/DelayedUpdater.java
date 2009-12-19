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
 * $Id: UploadsQuickInfoPanel.java 4504 2008-07-02 13:30:59Z harry $
 */
package de.dal33t.powerfolder.util.ui;

import java.util.TimerTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.WrappedScheduledThreadPoolExecutor;

/**
 * Helper class to perform UI updates delayed. If an UI update is scheduled
 * twice quickly after each other (<delay) only last update is actually
 * performed. But it is ensured that at least every "delay" time period at least
 * an update is performed once. This is useful if you want to reduce the number
 * of events in EDT thread. ATTENTION: Do only schedule events in ONE
 * DelayedUpdater that can override each other. This utility class DISCARDS
 * previously scheduled events. In other words: The last update wins.
 * <p>
 * Graphcial representation. E = scheduled event, U = update in UI.
 * <p>
 * Timeline with one event:
 * <p>
 * -----E-----------U-----------------
 * <p>
 * Two events:
 * <p>
 * -----E-------E---U-----------------
 * <p>
 * Many events:
 * <p>
 * -----E--E-E--E---U--E--E----E---U--
 * 
 * @author Christian Sprajc
 * @version $Revision$
 */
public class DelayedUpdater {

    private static final Logger LOG = Logger.getLogger(DelayedUpdater.class
        .getName());
    private static final long DEFAULT_DELAY = 250L;

    private long delay;
    private long nextMandatoryEvent = -1;
    private static ScheduledExecutorService scheduledES;
    private volatile DelayedTimerTask currentTask;

    /**
     * Constructs a delayed execution. Creates own timer lazily
     */
    public DelayedUpdater() {
        delay = DEFAULT_DELAY;
    }

    /**
     * Constructs a delayed execution. Uses shared timer from Controller.
     * 
     * @param controller
     */
    public DelayedUpdater(Controller controller) {
        this(controller, DEFAULT_DELAY);
    }

    /**
     * Constructs a delayed execution. Uses shared timer from Controller.
     * 
     * @param controller
     * @param delay
     *            the delay to use
     */
    public DelayedUpdater(Controller controller, long delay) {
        scheduledES = controller.getThreadPool();
        this.delay = delay;
    }

    public synchronized void setDelay(long delay) {
        Reject.ifTrue(delay < 0, "Illegal delay value: " + delay);
        this.delay = delay;
    }

    public long getDelay() {
        return delay;
    }

    /**
     * Schedules a task to be performed in EDT thread after a given time. If a
     * former update has not been processed yet the old task gets canceled.
     * 
     * @param task
     */
    public synchronized void schedule(final Runnable task) {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask.canceled = true;
        }
        currentTask = new DelayedTimerTask(task);
        if (scheduledES == null) {
            scheduledES = new WrappedScheduledThreadPoolExecutor(1);
        }
        try {
            long now = System.currentTimeMillis();
            if (nextMandatoryEvent < 0) {
                nextMandatoryEvent = now + delay;
            }
            long delayUntilEvent = Math.max(nextMandatoryEvent - now, 0);
            scheduledES.schedule(currentTask, delayUntilEvent,
                TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOG.log(Level.FINER, "Unable to schedule task to timer: " + e, e);
        }
    }

    private final class DelayedTimerTask extends TimerTask {
        private final Runnable task;
        private volatile boolean canceled;

        private DelayedTimerTask(Runnable task) {
            this.task = task;
            this.canceled = false;
        }

        @Override
        public void run() {
            // Ready for new tasks
            synchronized (DelayedUpdater.this) {
                currentTask = null;
                nextMandatoryEvent = -1;
            }
            if (canceled) {
                return;
            }
            UIUtil.invokeLaterInEDT(new Runnable() {
                public void run() {
                    if (canceled) {
                        return;
                    }
                    try {
                        task.run();
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE,
                            "Exception while executing delayed task: " + e, e);
                    }
                }
            });
        }
    }
}
