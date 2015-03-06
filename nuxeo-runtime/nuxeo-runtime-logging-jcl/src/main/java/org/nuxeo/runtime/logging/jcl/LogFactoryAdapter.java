package org.nuxeo.runtime.logging.jcl;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogConfigurationException;
import org.apache.commons.logging.LogFactory;

public class LogFactoryAdapter extends LogFactory {

    protected final Map<String, Object> attributes = new HashMap<>();

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public String[] getAttributeNames() {
        return attributes.keySet().toArray(new String[attributes.size()]);
    }

    @Override
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    protected final Map<String, LogAdapter> adapters = new HashMap<>();

    @Override
    public Log getInstance(@SuppressWarnings("rawtypes") Class clazz) throws LogConfigurationException {
        String name = clazz.getName();
        return getInstance(name);
    }

    @Override
    public Log getInstance(String name) throws LogConfigurationException {
        if (!adapters.containsKey(name)) {
            adapters.put(name, new LogAdapter(name));
        }
        return adapters.get(name);
    }

    @Override
    public void release() {
        adapters.clear();
        attributes.clear();
    }

}
