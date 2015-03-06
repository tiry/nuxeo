package org.nuxeo.runtime.services.url;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

public interface URLStreamHandlers {

    interface Handler {
        URLConnection openConnection(URL u) throws IOException;
    }

    void register(String protocol, Class<? extends Handler> type);

    void unregister(String protocol);
}
