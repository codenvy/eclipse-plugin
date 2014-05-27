/*
 * CODENVY CONFIDENTIAL
 * ________________
 * 
 * [2012] - [2014] Codenvy, S.A.
 * All Rights Reserved.
 * NOTICE: All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any. The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
package com.codenvy.eclipse.core.launcher;

import static com.codenvy.eclipse.core.CodenvyPlugin.PLUGIN_ID;
import static com.codenvy.eclipse.core.model.CodenvyRunnerStatus.Status.CANCELLED;
import static com.codenvy.eclipse.core.model.CodenvyRunnerStatus.Status.RUNNING;
import static com.codenvy.eclipse.core.model.CodenvyRunnerStatus.Status.STOPPED;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.eclipse.core.runtime.IStatus.ERROR;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.debug.core.model.IStreamsProxy;

import com.codenvy.eclipse.core.exceptions.APIException;
import com.codenvy.eclipse.core.model.CodenvyProject;
import com.codenvy.eclipse.core.model.CodenvyRunnerStatus;
import com.codenvy.eclipse.core.model.Link;
import com.codenvy.eclipse.core.services.RunnerService;

/**
 * The codenvy runner process.
 * 
 * @author Kevin Pollet
 */
public class CodenvyRunnerProcess implements IProcess {
    private static final int                TICK_DELAY     = 500;
    private static final TimeUnit           TICK_TIME_UNIT = MILLISECONDS;

    private final ILaunch                   launch;
    private final RunnerService             runnerService;
    private final CodenvyProject            project;
    private final Map<String, String>       attributes;
    private final AtomicBoolean             terminated;
    private final AtomicBoolean             running;
    private long                            processId;
    private final ScheduledExecutorService  executorService;
    private final CodenvyRunnerLogsThread   codenvyRunnerLogsThread;
    private Link                            webLink;
    private final StringBufferStreamMonitor outputStream;
    private final StringBufferStreamMonitor errorStream;
    private int                             exitValue;

    /**
     * Constructs an instance of {@link CodenvyRunnerProcess}.
     * 
     * @param launch the {@link ILaunch} object.
     * @param runnerService the {@link RunnerService}.
     * @param project the {@link CodenvyProject} to run.
     * @throws NullPointerException if launch, runnerService or project parameter is {@code null}.
     */
    public CodenvyRunnerProcess(ILaunch launch, RunnerService runnerService, CodenvyProject project) {
        this.launch = launch;
        this.runnerService = runnerService;
        this.project = project;
        this.attributes = new HashMap<>();
        this.terminated = new AtomicBoolean(false);
        this.running = new AtomicBoolean(false);
        this.executorService = Executors.newScheduledThreadPool(4);
        this.codenvyRunnerLogsThread = new CodenvyRunnerLogsThread();
        this.outputStream = new StringBufferStreamMonitor();
        this.errorStream = new StringBufferStreamMonitor();
        this.exitValue = 0;

        this.attributes.put(ATTR_PROCESS_TYPE, getClass().getName());
        launch.addProcess(this);

        try {

            final CodenvyRunnerStatus runnerStatus = this.runnerService.run(project);
            this.processId = runnerStatus.processId;

            executorService.scheduleAtFixedRate(new CodenvyRunnerStatusThread(), 0, TICK_DELAY, TICK_TIME_UNIT);
            executorService.scheduleAtFixedRate(codenvyRunnerLogsThread, 0, TICK_DELAY, TICK_TIME_UNIT);

        } catch (APIException e) {
            terminateWithAnError(e);
        }
    }

    @Override
    public Object getAdapter(@SuppressWarnings("rawtypes") Class adapter) {
        if (ILaunch.class.equals(adapter)) {
            return getLaunch();
        }
        return null;
    }

    @Override
    public boolean canTerminate() {
        return !isTerminated();
    }

    @Override
    public boolean isTerminated() {
        return terminated.get();
    }

    @Override
    public void terminate() throws DebugException {
        try {

            runnerService.stop(project, processId);

            stopProcess();

        } catch (APIException e) {
            terminateWithAnError(e);
        }
    }

    private void terminateWithAnError(APIException exception) {
        errorStream.append("Error: " + exception.getMessage());
        exitValue = exception.getStatus();

        stopProcess();
    }

    private void stopProcess() {
        terminated.set(true);
        running.set(false);

        executorService.shutdownNow();
        fireDebugEvent(DebugEvent.TERMINATE);
    }

    @Override
    public String getLabel() {
        return "Running project on Codenvy";
    }

    @Override
    public ILaunch getLaunch() {
        return launch;
    }

    @Override
    public IStreamsProxy getStreamsProxy() {
        return new IStreamsProxy() {
            @Override
            public void write(String input) throws IOException {
                outputStream.append(input);
            }

            @Override
            public IStreamMonitor getErrorStreamMonitor() {
                return errorStream;
            }

            @Override
            public IStreamMonitor getOutputStreamMonitor() {
                return outputStream;
            }
        };
    }

    @Override
    public void setAttribute(String key, String value) {
        synchronized (attributes) {
            attributes.put(key, value);
        }

        fireDebugEvent(DebugEvent.CHANGE);
    }

    @Override
    public String getAttribute(String key) {
        synchronized (attributes) {
            return attributes.get(key);
        }
    }

    @Override
    public int getExitValue() throws DebugException {
        if (!isTerminated()) {
            throw new DebugException(new Status(ERROR, PLUGIN_ID, "Process not yet terminated"));
        }
        return exitValue;
    }

    public Link getWebLink() {
        return webLink;
    }

    private void fireDebugEvent(int kind) {
        DebugPlugin.getDefault().fireDebugEventSet(new DebugEvent[]{new DebugEvent(this, kind)});
    }

    /**
     * {@link Runnable} polling the runner status.
     * 
     * @author Kevin Pollet
     */
    class CodenvyRunnerStatusThread implements Runnable {
        @Override
        public void run() {
            try {

                final CodenvyRunnerStatus runnerStatus = runnerService.status(project, processId);
                final boolean isTerminated = runnerStatus.status == STOPPED || runnerStatus.status == CANCELLED;

                if (runnerStatus.status == RUNNING) {
                    webLink = runnerStatus.getWebLink();
                    running.set(true);
                }

                if (isTerminated) {
                    stopProcess();
                }

            } catch (APIException e) {
                terminateWithAnError(e);
            }
        }
    }

    /**
     * {@link Runnable} polling the runner logs.
     * 
     * @author Kevin Pollet
     */
    class CodenvyRunnerLogsThread implements Runnable {
        @Override
        public void run() {
            if (running.get()) {
                try {

                    final String fullLogs = runnerService.logs(project, processId).trim();
                    final String logsDiff = fullLogs.substring(outputStream.getContents().length());

                    if (!logsDiff.isEmpty()) {
                        outputStream.append(logsDiff);

                    } else if (!outputStream.isFlushed() && fullLogs.equals(outputStream.getContents())) {
                        outputStream.flush();
                    }

                } catch (APIException e) {
                    terminateWithAnError(e);
                }
            }
        }
    }
}