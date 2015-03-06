/*
 * (C) Copyright 2006-2015 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.ecm.platform.convert.ooomanager;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.artofsolving.jodconverter.OfficeDocumentConverter;
import org.artofsolving.jodconverter.office.DefaultOfficeManagerConfiguration;
import org.artofsolving.jodconverter.office.OfficeConnectionProtocol;
import org.artofsolving.jodconverter.office.OfficeManager;
import org.artofsolving.jodconverter.office.OfficeTask;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.RuntimeServiceEvent;
import org.nuxeo.runtime.RuntimeServiceListener;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

public class OOoManagerComponent extends DefaultComponent implements OOoManagerService {

    protected static final Log log = LogFactory.getLog(OOoManagerComponent.class);

    private static final String CONNECTION_PROTOCOL_PROPERTY_KEY = "jod.connection.protocol";

    private static final String MAX_TASKS_PER_PROCESS_PROPERTY_KEY = "jod.max.tasks.per.process";

    private static final String OFFICE_HOME_PROPERTY_KEY = "jod.office.home";

    private static final String TASK_EXECUTION_TIMEOUT_PROPERTY_KEY = "jod.task.execution.timeout";

    private static final String TASK_QUEUE_TIMEOUT_PROPERTY_KEY = "jod.task.queue.timeout";

    private static final String TEMPLATE_PROFILE_DIR_PROPERTY_KEY = "jod.template.profile.dir";

    private static final String OFFICE_PIPES_PROPERTY_KEY = "jod.office.pipes";

    private static final String OFFICE_PORTS_PROPERTY_KEY = "jod.office.ports";

    protected static final String CONFIG_EP = "oooManagerConfig";

    private static OfficeManager officeManager;

    protected OOoManagerDescriptor descriptor = new OOoManagerDescriptor();

    protected volatile int state = STOPPED;

    protected static final int STOPPED = 0;

    protected static final int STARTED = 1;

    protected static final int RUNNING = 2;

    protected volatile Thread oooStarter = null;

    public OOoManagerDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (CONFIG_EP.equals(extensionPoint)) {
            OOoManagerDescriptor desc = (OOoManagerDescriptor) contribution;
            descriptor = desc;
        }
    }

    @Override
    public OfficeDocumentConverter getDocumentConverter() {
        if (isOOoManagerStarted()) {
            return new OfficeDocumentConverter(officeManager);
        } else {
            log.error("OfficeManager is not started.");
            return null;
        }
    }

    public void executeTask(OfficeTask task) {
        if (isOOoManagerStarted()) {
            officeManager.execute(task);
        } else {
            log.error("OfficeManager is not started.");
        }
    }

    @Override
    public void stopOOoManager() {
        if ((state & STARTED) == 0) {
            log.debug("OOoManager already stopped..");
            return;
        }
        NuxeoException errors = new NuxeoException("Stopping OOo Manager");
        try {
            state &= ~STARTED;
            waitOOoStarter();
            try {
                state &= ~RUNNING;
                log.debug("Stopping ooo manager.");
                try {
                    officeManager.stop();
                } catch (Throwable cause) {
                    errors.addSuppressed(cause);
                    if (cause instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                try {
                    new OOoKiller().kill();
                } catch (Throwable cause) {
                    errors.addSuppressed(cause);
                }
            }
        } finally {
            log.debug("Stopped ooo manager.");
        }
        if (errors.getSuppressed().length > 0) {
            throw errors;
        }
    }

    @Override
    public void startOOoManager() {
        if ((state & STARTED) != 0) {
            return;
        }
        state |= STARTED;
        oooStarter = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    startOOo();
                } finally {
                    oooStarter = null;
                }
            }
        }, "ooo-starter");
        oooStarter.start();
    }

    private void startOOo() {
        log.debug("Starting ooo manager.");
        try {
            DefaultOfficeManagerConfiguration configuration = configureOOo();
            officeManager = configuration.buildOfficeManager();
            officeManager.start();
            state |= RUNNING;
            log.debug("Started ooo manager.");
        } catch (Throwable error) {
            log.warn("Cannot start OpenOffice, JOD Converter won't be available", unwrapException(error));
        }
    }

    private DefaultOfficeManagerConfiguration configureOOo() {
        DefaultOfficeManagerConfiguration configuration = new DefaultOfficeManagerConfiguration();
        configuration.setConnectionProtocol(OfficeConnectionProtocol.SOCKET);
        // Properties configuration
        OfficeConnectionProtocol connectionProtocol = OfficeConnectionProtocol
                .valueOf(Framework.getProperty(CONNECTION_PROTOCOL_PROPERTY_KEY, "SOCKET"));
        ;
        if (connectionProtocol == OfficeConnectionProtocol.PIPE) {
            try {
                ConfigBuilderHelper.hackClassLoader();
            } catch (IOException cause) {
                throw new NuxeoException("Cannot hack system class loader", cause);
            }
            List<String> pipenames = new ArrayList<>();
            {
                String[] properties = Framework.getProperty(OFFICE_PIPES_PROPERTY_KEY, "").split(",\\s*");
                // Basic validation to avoid empty strings
                for (int i = 0; i < properties.length; i++) {
                    String pipename = properties[i].trim();
                    if (pipename.length() > 0) {
                        pipenames.add(pipename);
                    }
                }
            }
            if (pipenames.isEmpty()) {
                pipenames.addAll(descriptor.pipeNames);
            }
            if (pipenames.isEmpty()) {
                pipenames.add(Framework.getRuntime().getName());
            }
            configuration.setConnectionProtocol(OfficeConnectionProtocol.PIPE);
            if (pipenames.size() > 0) {
                configuration.setPipeNames(pipenames.toArray(new String[pipenames.size()]));
            }
        } else if (connectionProtocol == OfficeConnectionProtocol.SOCKET) {
            List<Integer> ports = new ArrayList<Integer>();
            {
                String[] properties = Framework.getProperty(OFFICE_PORTS_PROPERTY_KEY, "").split(",\\s*");
                // Basic validation to avoid empty strings
                for (int i = 0; i < properties.length; i++) {
                    String port = properties[i].trim();
                    if (port.length() > 0) {
                        ports.add(Integer.valueOf(port));
                    }
                }
            }
            if (ports.isEmpty()) {
                ports.addAll(descriptor.portNumbers);
            }
            if (ports.isEmpty()) {
                try (ServerSocket socket = new ServerSocket(0);) {
                    ports.add(Integer.valueOf(socket.getLocalPort()));
                } catch (IOException cause) {
                    throw new NuxeoException("Cannot allocate OOo Manager port", cause);
                }
            }
            configuration.setConnectionProtocol(OfficeConnectionProtocol.SOCKET);
            if (ports.size() > 0) {
                int numbers[] = new int[ports.size()];
                for (int i = 0; i < ports.size(); ++i) {
                    numbers[i] = ports.get(i).intValue();
                }
                configuration.setPortNumbers(numbers);
            }
        }
        String maxTasksPerProcessProperty = Framework.getProperty(MAX_TASKS_PER_PROCESS_PROPERTY_KEY);
        if (maxTasksPerProcessProperty != null && !"".equals(maxTasksPerProcessProperty)) {
            int maxTasksPerProcess = Integer.valueOf(maxTasksPerProcessProperty).intValue();
            configuration.setMaxTasksPerProcess(maxTasksPerProcess);
        }
        String officeHome = Framework.getProperty(OFFICE_HOME_PROPERTY_KEY);
        if (officeHome != null && !"".equals(officeHome)) {
            configuration.setOfficeHome(officeHome);
        }

        String taskExecutionTimeoutProperty = Framework.getProperty(TASK_EXECUTION_TIMEOUT_PROPERTY_KEY);
        if (taskExecutionTimeoutProperty != null && !"".equals(taskExecutionTimeoutProperty)) {
            long taskExecutionTimeout = Long.valueOf(taskExecutionTimeoutProperty).longValue();
            configuration.setTaskExecutionTimeout(taskExecutionTimeout);
        }
        String taskQueueTimeoutProperty = Framework.getProperty(TASK_QUEUE_TIMEOUT_PROPERTY_KEY);
        if (taskQueueTimeoutProperty != null && !"".equals(taskQueueTimeoutProperty)) {
            long taskQueueTimeout = Long.valueOf(taskQueueTimeoutProperty).longValue();
            configuration.setTaskQueueTimeout(taskQueueTimeout);
        }
        String templateProfileDir = Framework.getProperty(TEMPLATE_PROFILE_DIR_PROPERTY_KEY);
        if (templateProfileDir != null && !"".equals(templateProfileDir)) {
            File templateDirectory = new File(templateProfileDir);
            if (!templateDirectory.exists()) {
                try {
                    FileUtils.forceMkdir(templateDirectory);
                } catch (IOException e) {
                    throw new RuntimeException("I/O Error: could not create JOD templateDirectory");
                }
            }
            configuration.setTemplateProfileDir(templateDirectory);
        }

        return configuration;
    }

    public Throwable unwrapException(Throwable t) {
        Throwable cause = t.getCause();
        return cause == null ? t : unwrapException(cause);
    }

    @Override
    public void applicationStarted(ComponentContext context) {
        log.info("Starting OOo manager");
        Framework.addListener(new RuntimeServiceListener() {

            @Override
            public void handleEvent(RuntimeServiceEvent event) {
                if (event.id == RuntimeServiceEvent.RUNTIME_ABOUT_TO_STOP) {
                    stopOOoManager();
                }
            }
        });
        startOOoManager();
    }

    @Override
    public boolean isOOoManagerStarted() {
        if ((state & STARTED) == 0) {
            return false;
        }
        waitOOoStarter();
        return (state & RUNNING) != 0;
    }

    protected void waitOOoStarter() {
        if (oooStarter == null) {
            return;
        }
        for (int i = 0; i < 10; ++i) {
            try {
                oooStarter.join(200 * 300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (oooStarter == null || !oooStarter.isAlive()) {
                return;
            }
            log.error("Timeout on waiting for officeManager to start");
            oooStarter.interrupt();
        }
    }

    public OfficeManager getOfficeManager() {
        return officeManager;
    }

    class OOoKiller {

        void killFinalizerThread() {
            ThreadGroup tg = Thread.currentThread().getThreadGroup();

            int guessThreadCount = tg.activeCount() + 50;
            Thread[] threads = new Thread[guessThreadCount];
            int actualThreadCount = tg.enumerate(threads);
            while (actualThreadCount == guessThreadCount) { // Map was filled, there may be more
                guessThreadCount *= 2;
                threads = new Thread[guessThreadCount];
                actualThreadCount = tg.enumerate(threads);
            }
            for (Thread t : threads) {
                if (t == null) {
                    continue;
                }
                if (!t.getClass().getName().startsWith("com.sun.star.lib.util.AsynchronousFinalizer")) {
                    continue;
                }
                if (!t.isAlive()) {
                    return;
                }
                t.interrupt();
                break;
            }
        }

        public void kill() {
            shutdownExecutors();
            killFinalizerThread();
        }

        void shutdownExecutors() {
            Object[] pools = getFieldValue(officeManager, "pooledManagers");
            for (Object pool : pools) {
                Object process = getFieldValue(pool, "managedOfficeProcess");
                ExecutorService executor = getFieldValue(process, "executor");
                executor.shutdownNow();
            }
        }

        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Reflection methods
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        void gc() {

            Object obj = new Object();
            WeakReference<Object> ref = new WeakReference<Object>(obj);
            // noinspection UnusedAssignment
            obj = null;
            while (ref.get() != null) {
                System.gc();
            }
        }

        <E> E getStaticFieldValue(Class<?> clazz, String fieldName) {
            Field staticField = findField(clazz, fieldName);
            return (staticField != null) ? (E) getStaticFieldValue(staticField) : null;
        }

        <E> E getStaticFieldValue(ClassLoader loader, String className, String fieldName) {
            Field staticField = findFieldOfClass(loader, className, fieldName);
            return (staticField != null) ? (E) getStaticFieldValue(staticField) : null;
        }

        Field findFieldOfClass(ClassLoader loader, String className, String fieldName) {
            Class<?> clazz = findLoadedClass(loader, className);
            if (clazz != null) {
                return findField(clazz, fieldName);
            } else {
                return null;
            }
        }

        Class<?> loadClass(ClassLoader loader, String classname) {
            try {
                return loader.loadClass(classname);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        Class<?> findLoadedClass(ClassLoader loader, String classname) {
            return loadClass(loader, classname);
        }

        Field findField(Class<?> clazz, String fieldName) {
            if (clazz == null) {
                return null;
            }

            try {
                final Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true); // (Field is probably private)
                return field;
            } catch (NoSuchFieldException ex) {
                // Silently ignore
                return null;
            } catch (Exception ex) { // Example SecurityException
                return null;
            }
        }

        <T> T getStaticFieldValue(Field field) {
            try {
                if (!Modifier.isStatic(field.getModifiers())) {
                    return null;
                }

                return (T) field.get(null);
            } catch (Exception ex) {
                // Silently ignore
                return null;
            }
        }

        <T> T getFieldValue(Object obj, String fieldName) {
            final Field field = findField(obj.getClass(), fieldName);
            return (T) getFieldValue(field, obj);
        }

        <T> T getFieldValue(Field field, Object obj) {
            try {
                return (T) field.get(obj);
            } catch (Exception ex) {
                // Silently ignore
                return null;
            }
        }

        Method findMethod(Class<?> clazz, String methodName, Class... parameterTypes) {
            if (clazz == null) {
                return null;
            }

            try {
                final Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ex) {
                // Silently ignore
                return null;
            }
        }

    }

}
