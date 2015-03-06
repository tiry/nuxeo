package org.nuxeo.runtime.services.url;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.url.AbstractURLStreamHandlerService;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;

public class URLStreamHandlersComponent extends DefaultComponent implements URLStreamHandlers {

    @XObject("handler")
    public static class Contribution {
        @XNode("@type")
        Class<? extends Handler> type;

        @XNode("@protocol")
        String protocol;
    }

    BundleContext osgi;

    @Override
    public void activate(ComponentContext context) {
        osgi = context.getRuntimeContext().getBundle().getBundleContext();
    }

    @Override
    public void registerContribution(Object object, String extensionPoint, ComponentInstance contributor) {
        Contribution contribution = (Contribution) object;
        register(contribution.protocol, contribution.type);
    }

    @Override
    public void unregisterContribution(Object object, String extensionPoint, ComponentInstance contributor) {
        Contribution contribution = (Contribution) object;
        unregister(contribution.protocol);
    }

    final Map<String, Activation> byProtocols = new HashMap<>();

    @Override
    public void register(String protocol, Class<? extends URLStreamHandlers.Handler> type) {
        byProtocols.put(protocol, new Activation(protocol, newHandler(type)));
    }

    @Override
    public void unregister(String protocol) {
        byProtocols.remove(protocol).registration.unregister();
    }

    Handler newHandler(Class<? extends Handler> type) {
        try {
            return type.newInstance();
        } catch (InstantiationException | IllegalAccessException cause) {
            throw new RuntimeServiceException("Cannot create URL handler of type " + type, cause);
        }
    }

    class Activation {
        final Handler handler;

        final ServiceRegistration<URLStreamHandlerService> registration;

        Activation(String protocol, Handler handler) {
            this.handler = handler;
            Dictionary<String, Object> props = new Hashtable<>();
            props.put(URLConstants.URL_HANDLER_PROTOCOL, protocol);
            registration = osgi.registerService(URLStreamHandlerService.class, new AbstractURLStreamHandlerService() {

                @Override
                public URLConnection openConnection(URL u) throws IOException {
                    return handler.openConnection(u);
                }
            }, props);
        }
    }


}
