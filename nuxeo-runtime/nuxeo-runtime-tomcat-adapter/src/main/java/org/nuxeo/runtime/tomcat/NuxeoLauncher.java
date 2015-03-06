/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     bstefanescu, jcarsique
 */
package org.nuxeo.runtime.tomcat;

import java.io.File;
import org.apache.catalina.Container;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Loader;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.core.StandardContext;
import org.apache.naming.ContextAccessController;
import org.apache.naming.resources.DirContextURLStreamHandler;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class NuxeoLauncher implements LifecycleListener {

    protected String home = "${catalina.base}/nxserver";

    protected static NuxeoLauncher self;

    protected File homedir;

    public NuxeoLauncher() {
        self = this;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public String getHome() {
        return home;
    }


    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        Lifecycle lf = event.getLifecycle();
        if (lf instanceof ContainerBase) {
            Loader loader = ((Container) lf).getLoader();
            if (loader instanceof NuxeoWebappLoader) {
                handleEvent((NuxeoWebappLoader) loader, event);
            }
        }
    }

    protected void handleEvent(NuxeoWebappLoader loader, LifecycleEvent event) {
        String type = event.getType();
        if (type == Lifecycle.BEFORE_START_EVENT) {
            homedir = resolveHomeDirectory(loader);
        } else if (type == Lifecycle.CONFIGURE_START_EVENT) {
            DirContextURLStreamHandler.bind(loader.getClassLoader(), loader.getContainer().getResources());
        }
        // else if (type == Lifecycle.START_EVENT) {
        // loader.getClassLoader().bootstrap.start();
        // } else if (type == Lifecycle.STOP_EVENT) {
        // loader.getClassLoader().bootstrap.stop();
        // }
        return;
    }

    protected void grabContextSecurity(NuxeoWebappLoader loader, LifecycleEvent event) {
        final Object token = event.getLifecycle();
        final StandardContext source = (StandardContext) event.getSource();
        String name = "/" + source.getDomain() + "/" + source.getHostname() + source.getPath();
        ContextAccessController.setWritable(name, token);
    }

    protected File resolveHomeDirectory(NuxeoWebappLoader loader) {
        String path = null;
        if (home.startsWith("/") || home.startsWith("\\") || home.contains(":/") || home.contains(":\\")) {
            // absolute
            path = home;
        } else if (home.startsWith("${catalina.base}")) {
            path = getTomcatHome() + home.substring("${catalina.base}".length());
        } else {
            try {
                File baseDir = loader.getBaseDir();
                return new File(baseDir, home);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
        return new File(path);
    }

    public String getTomcatHome() {
        String tomcatHome = System.getProperty("catalina.base");
        if (tomcatHome == null) {
            tomcatHome = System.getProperty("catalina.home");
        }
        return tomcatHome;
    }

}
