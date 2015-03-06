package org.nuxeo.osgi.proxies;

public class Concrete implements Call {

    @Override
    public Call call() {
        throw new RuntimeException("let looks at the stacktrace");
    }

}