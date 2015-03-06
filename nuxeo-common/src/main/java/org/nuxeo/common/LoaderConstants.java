/*
 * (C) Copyright 2006-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     bstefanescu
 */
package org.nuxeo.common;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public interface LoaderConstants {

    /**
     * The home directory.
     *
     * @deprecated never defined; use {@link #NUXEO_HOME_DIR}
     */
    @Deprecated
    public static final String HOME_DIR = "org.nuxeo.app.home";

    /**
     * The web root.
     *
     * @deprecated never defined; use {@link #NUXEO_WEB_DIR}
     */
    @Deprecated
    public static final String WEB_DIR = "org.nuxeo.app.web";

    /**
     * The config directory.
     *
     * @deprecated never defined; use {@link #NUXEO_CONFIG_DIR}
     */
    @Deprecated
    public static final String CONFIG_DIR = "org.nuxeo.app.config";

    /**
     * The data directory.
     *
     * @deprecated never defined; use {@link #NUXEO_DATA_DIR}
     */
    @Deprecated
    public static final String DATA_DIR = "org.nuxeo.app.data";

    /**
     * The log directory.
     *
     * @deprecated never defined; use {@link #NUXEO_LOG_DIR}
     */
    @Deprecated
    public static final String LOG_DIR = "org.nuxeo.app.log";

    /**
     * The application layout (optional): directory containing nuxeo runtime osgi bundles.
     */
    public static final String BUNDLES_DIR = "nuxeo.osgi.app.bundles";

    static final String LIBS = "org.nuxeo.app.libs"; // class path

    public static final String BUNDLES = "nuxeo.osgi.bundles";

    static final String APP_NAME = "org.nuxeo.app.name";

    static final String HOST_NAME = "org.nuxeo.app.host.name";

    static final String HOST_VERSION = "org.nuxeo.app.host.version";

    static final String TMP_DIR = "org.nuxeo.app.tmp";

    static final String WORKING_DIR = "org.nuxeo.app.work";

    static final String NESTED_DIR = "org.nuxeo.app.nested";

     static final String DEVMODE = "org.nuxeo.app.devmode";

    static final String PREPROCESSING = "org.nuxeo.app.preprocessing";

    static final String SCAN_FOR_NESTED_JARS = "org.nuxeo.app.scanForNestedJars";

    static final String INSTALL_RELOAD_TIMER = "org.nuxeo.app.installReloadTimer";

    static final String FLUSH_CACHE = "org.nuxeo.app.flushCache";

    static final String ARGS = "org.nuxeo.app.args";

    static final String OSGI_BOOT_DELEGATION = "org.osgi.framework.bootdelegation";

}
