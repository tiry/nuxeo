/*
 * (C) Copyright 2006-2008 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     bstefanescu
 *
 * $Id$
 */

package org.nuxeo.runtime.tomcat;

import java.io.IOException;
import java.net.URL;
import org.apache.catalina.loader.WebappClassLoader;
import org.apache.catalina.util.ServerInfo;
import org.nuxeo.osgi.application.FrameworkBootstrap;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class NuxeoWebappClassLoader extends WebappClassLoader {

    public NuxeoWebappClassLoader(ClassLoader parent) {
        super(parent);
        hasExternalRepositories = true;
    }

    final FrameworkBootstrap bootstrap = bootstrap();

    @Override
    public URL[] getURLs() {
        return bootstrap.getURLs(); // scanned jars
    }

    protected FrameworkBootstrap bootstrap() {
        try {
            FrameworkBootstrap bootstrap = new FrameworkBootstrap(this,
                    "org.apache.tomcat,org.apache.el,org.apache.jasper,org.apache.catalina,org.apache.naming",
                    NuxeoLauncher.self.homedir);
            String info = ServerInfo.getServerInfo();
            int i = info.indexOf('/'); // Apache Tomcat/6.0.35
            String version;
            if (i > 0) {
                version = info.substring(i + 1);
            } else {
                version = ServerInfo.getServerNumber(); // 6.0.35.0
            }
            bootstrap.setHostName("Tomcat");
            bootstrap.setHostVersion(version);
            bootstrap.initialize();
            return bootstrap;
        } catch (IOException | RuntimeException cause) {
            throw new RuntimeException("Cannot initialize framework", cause);
        }
    }

}
