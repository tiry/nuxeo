package org.nuxeo.osgi.bootstrap;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ForkJoinTask;

import javax.management.remote.JMXServiceURL;
import javax.security.auth.login.Configuration;
import javax.swing.JEditorPane;

public class OSGiRuntimeBridge {

    public static void install() {
        ;
    }

    static {
        new Hook().handleClassLoaded();
    }

    static class Hook {

        void handleClassLoaded() {
            initForkJoinPool();

            initURLStreamHandlers();

            initSecurityConfiguration();

            initAwt();

            initSecurityProviders();

//            initJdbcDrivers();

            initSunAwtAppContext();

            initSecurityPolicy();

            initDocumentBuilderFactory();

            initDatatypeConverterImpl();

            initJavaxSecurityLoginConfiguration();

            initJMXRandomStacktrace();

            initLdapPoolManager();

            initJava2dDisposer();

            initSunGC();

            initOracleJdbcThread();
        }

        void initForkJoinPool() {
            ForkJoinTask.adapt(new Runnable() {

                @Override
                public void run() {
                    ;
                }
            }).invoke();
        }

        void initURLStreamHandlers() {
            URL.setURLStreamHandlerFactory(new OSGiStreamHandlerFactory());
        }

        void initSecurityConfiguration() {
            Configuration.setConfiguration(new OSGiLoginConfiguration());
        }

        /**
         * Initialize random stack trace outside of OSGi
         */
        void initJMXRandomStacktrace() {
            try {
                new JMXServiceURL("");
            } catch (MalformedURLException cause) {
                ;
            }
        }

        /**
         * To skip this step override this method in a subclass and make that subclass method empty. The first call to
         * java.awt.Toolkit.getDefaultToolkit() will spawn a new thread with the same contextClassLoader as the caller.
         * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
         */
        void initAwt() {
            try {
                java.awt.Toolkit.getDefaultToolkit(); // Will start a Thread
            } catch (Throwable t) {
                throw new AssertionError("Consider adding -Djava.awt.headless=true to your JVM parameters", t);
            }
        }

        /**
         * To skip this step override this method in a subclass and make that subclass method empty. Custom
         * java.security.Provider loaded in your OSGi and registered with java.security.Security.addProvider() must be
         * unregistered with java.security.Security.removeProvider() at application shutdown, or it will cause leaks.
         * See http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
         */
        void initSecurityProviders() {
            java.security.Security.getProviders();
        }

        /**
         * To skip this step override this method in a subclass and make that subclass method empty. Your JDBC driver
         * will be registered in java.sql.DriverManager, which means that if you include your JDBC driver inside your
         * web application, there will be a reference to your webapps classloader from system classes (see
         * <a href="http://java.jiderhamn.se/2012/01/01/classloader-leaks-ii-find-and-work-around-unwanted-references/">
         * part II</a>). The simple solution is to put JDBC driver on server level instead, but you can also deregister
         * the driver at application shutdown. See
         * http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
         */
        void initJdbcDrivers() {
            java.sql.DriverManager.getDrivers(); // Load initial drivers using system classloader
        }

        /**
         * There will be a strong reference from {@link sun.awt.AppContext#contextClassLoader} to the classloader of the
         * calls to {@link sun.awt.AppContext#getAppContext()}. Avoid leak by forcing initialization using system
         * classloader. Note that Google Web Toolkit (GWT) will trigger this leak via its use of javax.imageio. See
         * http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
         */
        void initSunAwtAppContext() {
            try {
                javax.imageio.ImageIO.getCacheDirectory(); // Will call sun.awt.AppContext.getAppContext()
                new JEditorPane("text/plain", "dummy"); // According to GitHub user dany52, the above is not enough
            } catch (Throwable t) {
                throw new AssertionError("Consider adding -Djava.awt.headless=true to your JVM parameters", t);
            }
        }

        /**
         * javax.security.auth.Policy.getPolicy() will keep a strong static reference to the contextClassLoader of the
         * first calling thread. See
         * http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
         */
        void initSecurityPolicy() {
            try {
                Class.forName("javax.security.auth.Policy")
                        .getMethod("getPolicy")
                        .invoke(null);
            } catch (Throwable e) {
                throw new AssertionError("Can't set context class loader reference of security policy", e);
            }
        }

        /**
         * The classloader of the first thread to call DocumentBuilderFactory.newInstance().newDocumentBuilder() seems
         * to be unable to garbage collection. Is it believed this is caused by some JVM internal bug. See
         * http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
         */
        void initDocumentBuilderFactory() {
            try {
                javax.xml.parsers.DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder();
            } catch (Throwable e) { // Example: ParserConfigurationException
                throw new AssertionError("Can't set context class loader reference of document builder factory", e);
            }
        }

        /**
         * javax.xml.bind.DatatypeConverterImpl in the JAXB Reference Implementation shipped with JDK 1.6+ will keep a
         * static reference (datatypeFactory) to a concrete subclass of javax.xml.datatype.DatatypeFactory, that is
         * resolved when the class is loaded (which I believe happens if you have custom bindings that reference the
         * static methods in javax.xml.bind.DatatypeConverter). It seems that if for example you have a version of
         * Xerces inside your application, the factory method may resolve
         * org.apache.xerces.jaxp.datatype.DatatypeFactoryImpl as the implementation to use (rather than
         * com.sun.org.apache.xerces.internal.jaxp.datatype.DatatypeFactoryImpl shipped with the JDK), which means there
         * will a reference from javax.xml.bind.DatatypeConverterImpl to your classloader. See
         * http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
         */
        void initDatatypeConverterImpl() {
            try {
                Class.forName("javax.xml.bind.DatatypeConverterImpl"); // Since JDK 1.6. May throw java.lang.Error
            } catch (ClassNotFoundException e) {
                ;
            } catch (Throwable e) {
                throw new AssertionError("Can't set context class loader of xml data converter", e);
            }
        }

        /**
         * The class javax.security.auth.login.Configuration will keep a strong static reference to the
         * contextClassLoader of Thread from which the class is loaded. See
         * http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
         */
        void initJavaxSecurityLoginConfiguration() {
            try {
                Class.forName("javax.security.auth.login.Configuration", true, ClassLoader.getSystemClassLoader());
            } catch (ClassNotFoundException e) {
                throw new AssertionError("Can't set context class loader of default auth login configuration", e);
            }
        }

        /**
         * The contextClassLoader of the thread loading the com.sun.jndi.ldap.LdapPoolManager class may be kept from
         * being garbage collected, since it will start a new thread if the system property. See
         * http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
         */
        void initLdapPoolManager() {
            try {
                Class.forName("com.sun.jndi.ldap.LdapPoolManager");
            } catch (ClassNotFoundException e) {
                ;
            }
        }

        /**
         * Loading the class sun.java2d.Disposer will spawn a new thread with the same contextClassLoader.
         * <a href="https://issues.apache.org/bugzilla/show_bug.cgi?id=51687">More info</a>. See
         * http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
         */
        void initJava2dDisposer() {
            try {
                Class.forName("sun.java2d.Disposer"); // Will start a Thread
            } catch (ClassNotFoundException e) {
                ;
            }
        }

        /**
         * sun.misc.GC.requestLatency(long), which is known to be called from
         * javax.management.remote.rmi.RMIConnectorServer.start(), will cause the current contextClassLoader to be
         * unavailable for garbage collection. See
         * http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
         */
        void initSunGC() {
            try {
                Class<?> gcClass = Class.forName("sun.misc.GC");
                final Method requestLatency = gcClass.getDeclaredMethod("requestLatency", long.class);
                requestLatency.invoke(null, 3600000L);
            } catch (ClassNotFoundException e) {
                ;
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Can't initialize context class loader of gc");
            }
        }

        /**
         * See https://github.com/mjiderhamn/classloader-leak-prevention/issues/8 and
         * https://github.com/mjiderhamn/classloader-leak-prevention/issues/23 and
         * http://java.jiderhamn.se/2012/02/26/classloader-leaks-v-common-mistakes-and-known-offenders/
         */
        void initOracleJdbcThread() {
            // Cause oracle.jdbc.driver.OracleTimeoutPollingThread to be started with contextClassLoader = system
            // classloader
            try {
                Class.forName("oracle.jdbc.driver.OracleTimeoutThreadPerVM");
            } catch (ClassNotFoundException e) {
                // Ignore silently - class not present
            }
        }
    }
}
