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
 *     bstefanescu
 */
package org.nuxeo.runtime.test.runner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Properties;

import org.mortbay.jetty.Connector;
import org.nuxeo.common.Environment;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.WorkingDirectoryConfigurator;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@Deploy("org.nuxeo.runtime.jetty")
@Features(RuntimeFeature.class)
public class JettyFeature extends SimpleFeature implements WorkingDirectoryConfigurator {

    @Override
    public void initialize(FeaturesRunner runner) throws Exception {
        Jetty jetty = runner.getConfig(Jetty.class);
        if (jetty == null) {
            jetty = Defaults.of(Jetty.class);
        }
        configureJetty(jetty);

        runner.getFeature(RuntimeFeature.class).getHarness().addWorkingDirectoryConfigurator(this);
    }

    protected void configureJetty(Jetty jetty) {
        Properties env = Environment.getDefault().getProperties();
        int p = jetty.port();
        try {
            String s = env.getProperty("JETTY_PORT");
            if (s != null) {
                p = Integer.parseInt(s);
            }
        } catch (Exception e) {
            // do nothing ; the jetty.port
        }
        if (p == 0) {
            try (ServerSocket socket = new ServerSocket(0)) {
                p = socket.getLocalPort();
            } catch (IOException cause) {
                throw new AssertionError("Cannot allocate port for jetty", cause);
            }
        }
        env.setProperty("org.nuxeo.jetty.port", Integer.toString(p));

        String host = env.getProperty("JETTY_HOST");
        if (host == null) {
            host = jetty.host();
        }
        if (host.length() > 0) {
            env.setProperty("org.nuxeo.jetty.host", host);
        }

        String config = env.getProperty("JETTY_CONFIG");
        if (config == null) {
            config = jetty.config();
        }
        if (config.length() > 0) {
            env.setProperty("org.nuxeo.jetty.config", config);
        }

        env.setProperty("org.nuxeo.jetty.propagateNaming", Boolean.toString(jetty.propagateNaming()));
    }

    @Override
    public void configure(RuntimeHarness harness, File workingDir) throws IOException {
        File dest = new File(workingDir, "config");
        dest.mkdirs();

        InputStream in = getResource("jetty/default-web.xml").openStream();
        dest = new File(workingDir + "/config", "default-web.xml");
        try {
            FileUtils.copyToFile(in, dest);
        } finally {
            in.close();
        }

        in = getResource("jetty/jetty.xml").openStream();
        dest = new File(workingDir + "/config", "jetty.xml");
        try {
            FileUtils.copyToFile(in, dest);
        } finally {
            in.close();
        }
    }

    public String getConnectionURL(String path) {
        Connector connector = Framework.getRuntime().getService(org.mortbay.jetty.Connector.class);
        return String.format("http://%s:%d%s", connector.getHost(), connector.getPort(), path);
    }

    private static URL getResource(String resource) {
        // return
        // Thread.currentThread().getContextClassLoader().getResource(resource);
        return Jetty.class.getClassLoader().getResource(resource);
    }

}
