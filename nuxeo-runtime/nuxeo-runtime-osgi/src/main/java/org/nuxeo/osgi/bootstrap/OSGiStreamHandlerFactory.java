package org.nuxeo.osgi.bootstrap;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.StringTokenizer;

import org.osgi.framework.launch.Framework;
import org.osgi.service.url.URLStreamHandlerService;

class OSGiStreamHandlerFactory implements URLStreamHandlerFactory {

    final URLStreamHandlerFactory previous;

    OSGiStreamHandlerFactory() {
        previous = new URLStreamHandlerFactory() {

            @Override
            public URLStreamHandler createURLStreamHandler(String protocol) {
                return null;
            }
        };
    }

    OSGiStreamHandlerFactory(URLStreamHandlerFactory factory) {
        previous = factory;
    }

    MethodHandle openConnectionHandle = lookupOpenConnection();

    MethodHandle lookupOpenConnection() {
        try {
            Method method = URLStreamHandler.class.getDeclaredMethod("openConnection", URL.class);
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method);
        } catch (NoSuchMethodException | IllegalAccessException cause) {
            throw new AssertionError("Cannot lookup openConnection in URLStreamHandler", cause);
        }
    }

    URLConnection invokeHandler(URLStreamHandler handler, URL url) throws IOException {
        try {
            return (URLConnection) openConnectionHandle.invokeWithArguments(handler, url);
        } catch (IOException cause) {
            throw cause;
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", cause);
        } catch (Throwable cause) {
            throw new AssertionError(cause);
        }
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        return new URLStreamHandler() {
            URLStreamHandler bootHandler = getBootHandler(protocol);

            @Override
            protected URLConnection openConnection(URL url) throws IOException {
                Framework system = OSGiSecurityManager.self.lookupFramework();
                URLConnection connection = system != null
                        ? system.adapt(URLStreamHandlerService.class).openConnection(url) : null;
                if (connection == null) {
                    connection = invokeHandler(bootHandler, url);
                }
                return connection;
            }
        };
    }

    URLStreamHandler getBootHandler(String protocol) {

        { // try stacked factory
            URLStreamHandler handler = previous.createURLStreamHandler(protocol);
            if (handler != null) {
                return handler;
            }
        }

        String packagePrefixList = null;

        packagePrefixList = System.getProperty("java.protocol.handler.pkgs");
        if (packagePrefixList != "") {
            packagePrefixList += "|";
        }

        packagePrefixList += "sun.net.www.protocol";

        StringTokenizer packagePrefixIter = new StringTokenizer(packagePrefixList, "|");

        while (packagePrefixIter.hasMoreTokens()) {

            String packagePrefix = packagePrefixIter.nextToken().trim();
            try {
                String clsName = packagePrefix + "." + protocol + ".Handler";
                Class<?> cls = null;
                try {
                    cls = Class.forName(clsName);
                } catch (ClassNotFoundException e) {
                    ClassLoader cl = ClassLoader.getSystemClassLoader();
                    if (cl != null) {
                        cls = cl.loadClass(clsName);
                    }
                }
                if (cls != null) {
                    return (URLStreamHandler) cls.newInstance();
                }
            } catch (Exception e) {
                // any number of exceptions can get thrown here
            }
        }

        return new URLStreamHandler() {

            @Override
            protected URLConnection openConnection(URL u) throws IOException {
                return null;
            }
        };
    }

}
