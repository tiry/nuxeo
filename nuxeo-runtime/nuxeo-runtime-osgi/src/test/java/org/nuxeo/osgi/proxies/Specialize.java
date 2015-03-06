package org.nuxeo.osgi.proxies;

public class Specialize extends Concrete {
    public Specialize() {
        super();
    }

    public static Concrete pfouh() {
        return new Specialize();
    }
}