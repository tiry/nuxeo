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
 *     Nuxeo - initial API and implementation
 * $Id$
 */

package org.nuxeo.runtime.model.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.nuxeo.common.xmap.Context;
import org.nuxeo.common.xmap.XMap;
import org.nuxeo.common.xmap.XValueFactory;
import org.nuxeo.runtime.Version;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.Extension;
import org.nuxeo.runtime.model.RuntimeContext;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
public class ComponentDescriptorReader {

    private final XMap xmap;

    public ComponentDescriptorReader() {
        xmap = new XMap();
        xmap.deferClassLoading();
        xmap.setValueFactory(new XValueFactory<ComponentName>() {
            @Override
            public ComponentName deserialize(Context context, String value) {
                return new ComponentName(value);
            }

            @Override
            public String serialize(Context context, ComponentName value) {
                if (value != null) {
                    return value.toString();
                }
                return null;
            }
        });
        xmap.setValueFactory(new XValueFactory<Version>() {
            @Override
            public Version deserialize(Context context, String value) {
                return Version.parseString(value);
            }

            @Override
            public String serialize(Context context, Version value) {
                if (value != null) {
                    return value.toString();
                }
                return null;
            }
        });
        xmap.register(RegistrationInfoImpl.class);
    }

    public RegistrationInfoImpl[] read(RuntimeContext ctx, URL url) throws IOException {
        InputStream in = url.openStream();
        try {
            String source = org.apache.commons.io.IOUtils.toString(in, "UTF-8");
            String expanded = ctx.getRuntime().expandVars(source);
            InputStream bin = new ByteArrayInputStream(expanded.getBytes());
            try {
                RegistrationInfoImpl impls[] =  read(ctx, bin);
                for (RegistrationInfoImpl impl:impls) {
                    impl.xmlFileUrl = url;
                }
                return impls;
            } finally {
                bin.close();
            }
        } finally {
            in.close();
        }
    }

    public RegistrationInfoImpl[] read(RuntimeContext ctx, InputStream in) throws IOException {
        Object[] result = xmap.loadAll(new XMapContext(ctx), in);
        RegistrationInfoImpl[] impls = new RegistrationInfoImpl[result.length];
        for (int i = 0; i < result.length; ++i) {
            RegistrationInfoImpl impl = (RegistrationInfoImpl)result[i];
            handleNewInfo(ctx, impl);
            impls[i] = impl;
        }
        return impls;
    }

    protected void handleNewInfo(RuntimeContext context, RegistrationInfoImpl info) {
        // requires extended services
        for (Extension xt:info.getExtensions()) {
            ComponentName target = xt.getTargetComponent();
            if (!info.getName().equals(target)) {
                info.requires.add(xt.getTargetComponent());
            }
        }
        // set runtime context
        info.context = (AbstractRuntimeContext)context;
        String name = info.getBundle();
        if (name != null) {
            // this is an external component XML.
            // should use the real owner bundle as the context.
            info.context = (AbstractRuntimeContext)context.getRuntime().getContext(name);
        }

    }

    public void flushDeferred() {
        xmap.flushDeferred();
    }
}
