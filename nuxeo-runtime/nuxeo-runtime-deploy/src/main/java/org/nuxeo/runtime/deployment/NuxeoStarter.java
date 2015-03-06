/*
 * (C) Copyright 2011-2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 *     Julien Carsique
 */
package org.nuxeo.runtime.deployment;

import static org.nuxeo.common.Environment.JBOSS_HOST;
import static org.nuxeo.common.Environment.NUXEO_CONFIG_DIR;
import static org.nuxeo.common.Environment.NUXEO_DATA_DIR;
import static org.nuxeo.common.Environment.NUXEO_LOG_DIR;
import static org.nuxeo.common.Environment.NUXEO_RUNTIME_HOME;
import static org.nuxeo.common.Environment.NUXEO_TMP_DIR;
import static org.nuxeo.common.Environment.NUXEO_WEB_DIR;
import static org.nuxeo.common.Environment.TOMCAT_HOST;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.LoaderConstants;
import org.nuxeo.osgi.bootstrap.application.OSGiFrameworkLoader;
import org.nuxeo.runtime.api.Framework;
import org.osgi.framework.BundleException;

/**
 * This is called at WAR startup and starts the Nuxeo OSGi runtime and registers the Nuxeo bundles with it.
 * <p>
 * This class must be configured as a {@code <listener>/<listener-class>} in {@code META-INF/web.xml}.
 * <p>
 * It uses servlet init parameters defined through {@code <context-param>/<param-name>/<param-value>} in web.xml.
 * Allowable parameter names come from {@link org.nuxeo.common.Environment}, mainly
 * {@link org.nuxeo.common.Environment#NUXEO_RUNTIME_HOME NUXEO_RUNTIME_HOME} and
 * {@link org.nuxeo.common.Environment#NUXEO_CONFIG_DIR NUXEO_CONFIG_DIR}, but also
 * {@link org.nuxeo.common.Environment#NUXEO_DATA_DIR NUXEO_DATA_DIR}, {@link org.nuxeo.common.Environment#NUXEO_LOG_DIR
 * NUXEO_LOG_DIR}, {@link org.nuxeo.common.Environment#NUXEO_TMP_DIR NUXEO_TMP_DIR} and
 * {@link org.nuxeo.common.Environment#NUXEO_WEB_DIR NUXEO_WEB_DIR}.
 */
public class NuxeoStarter implements ServletContextListener {

    private static final Log log = LogFactory.getLog(NuxeoStarter.class);

    /** Default location of the home in the server current directory. */
    private static final String DEFAULT_HOME = "nuxeo";

    /**
     * Name of the file listing Nuxeo bundles. If existing, this file will be used at start, else
     * {@code "/WEB-INF/lib/"} will be scanned.
     *
     * @since 5.9.3
     * @see #findBundles(ServletContext)
     */
    public static final String NUXEO_BUNDLES_LIST = ".nuxeo-bundles";

    protected final Map<String, String> env = new HashMap<>();

    protected final List<File> bundleFiles = new ArrayList<File>();

    protected final List<File> libraryFiles = new ArrayList<File>();

    boolean isLauncher;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        try {
            if (Framework.isInitialized()) {
                try {
                    OSGiFrameworkLoader.start();
                } catch (BundleException | IOException cause) {
                    throw new RuntimeException("Cannot initialize nuxeo runtime", cause);
                }
                return;
            }
            isLauncher = true;
            try {
                long startTime = System.currentTimeMillis();
                start(event);
                long finishedTime = System.currentTimeMillis();
                @SuppressWarnings("boxing")
                Double duration = (finishedTime - startTime) / 1000.0;
                log.info(String.format("Nuxeo framework started in %.1f sec.", duration));
            } catch (IOException | BundleException cause) {
                throw new RuntimeException("Cannot initialize nuxeo runtime", cause);
            }
        } catch (Throwable cause) {
            throw cause;
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (!isLauncher) {
            return;
        }
        try {
            stop();
        } catch (Exception cause) {
            throw new RuntimeException("Cannot stop nuxeo runtime", cause);
        } finally {
            isLauncher = false;
        }
    }

    protected void start(ServletContextEvent event) throws IOException, BundleException {
        ServletContext servletContext = event.getServletContext();
        findLibraries(servletContext);
        findBundles(servletContext);
        findEnv(servletContext);

        File home = new File(env.get(NUXEO_RUNTIME_HOME).toString());
        OSGiFrameworkLoader.initialize(home, null, libraryFiles.toArray(new File[libraryFiles.size()]),
                bundleFiles.toArray(new File[bundleFiles.size()]), env);
        OSGiFrameworkLoader.start();
    }

    protected void stop() throws BundleException {
        OSGiFrameworkLoader.stop();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            try {
                DriverManager.deregisterDriver(driver);
                log.warn(String.format("Deregister JDBC driver: %s", driver));
            } catch (SQLException e) {
                log.error(String.format("Error deregistering JDBC driver %s", driver), e);
            }
        }
    }

    protected void findBundles(ServletContext servletContext) throws IOException {
        InputStream bundlesListStream = servletContext.getResourceAsStream("/WEB-INF/" + NUXEO_BUNDLES_LIST);
        if (bundlesListStream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(bundlesListStream))) {
                String bundleName;
                while ((bundleName = reader.readLine()) != null) {
                    String path = servletContext.getRealPath("/WEB-INF/lib/" + bundleName);
                    if (path == null) {
                        continue;
                    }
                    bundleFiles.add(new File(path));
                }
            }
        }
        if (bundleFiles.isEmpty()) { // Fallback on directory scan
            Set<String> ctxpaths = servletContext.getResourcePaths("/WEB-INF/lib/");
            for (String ctxpath : ctxpaths) {
                if (!ctxpath.endsWith(".jar")) {
                    continue;
                }
                String path = servletContext.getRealPath(ctxpath);
                if (path == null) {
                    continue;
                }
                bundleFiles.add(new File(path));
            }
        }
    }

    protected void findLibraries(ServletContext servletContext) {
        @SuppressWarnings("unchecked")
        Set<String> ctxpaths = servletContext.getResourcePaths("/OSGI-INF/lib/");
        for (String ctxpath : ctxpaths) {
            if (!ctxpath.endsWith(".jar")) {
                continue;
            }
            String path = servletContext.getRealPath(ctxpath);
            if (path == null) {
                continue;
            }
            bundleFiles.add(new File(path));
        }
    }

    protected void findEnv(ServletContext servletContext) throws MalformedURLException {
        for (String param : Arrays.asList( //
                NUXEO_RUNTIME_HOME, //
                NUXEO_CONFIG_DIR, //
                NUXEO_DATA_DIR, //
                NUXEO_LOG_DIR, //
                NUXEO_TMP_DIR, //
                NUXEO_WEB_DIR)) {
            String value = servletContext.getInitParameter(param);
            if (value != null && !"".equals(value.trim())) {
                env.put(param, value);
            }
        }
        // default env values
        if (!env.containsKey(NUXEO_CONFIG_DIR)) {
            String webinf = servletContext.getResource("OSGI-INF").getFile();
            env.put(NUXEO_CONFIG_DIR, webinf);
        }
        if (!env.containsKey(NUXEO_RUNTIME_HOME)) {
            File home = new File(DEFAULT_HOME);
            env.put(NUXEO_RUNTIME_HOME, home.getAbsolutePath());
        }
        // host
        if (getClass().getClassLoader().getClass().getName().startsWith("org.jboss.classloader")) {
            env.put(LoaderConstants.HOST_NAME, JBOSS_HOST);
        } else if (servletContext.getClass().getName().startsWith("org.apache.catalina")) {
            env.put(LoaderConstants.HOST_NAME, TOMCAT_HOST);
        }
    }

}
