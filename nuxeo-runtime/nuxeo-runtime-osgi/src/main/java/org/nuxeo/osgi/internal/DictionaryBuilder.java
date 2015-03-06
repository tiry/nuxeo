package org.nuxeo.osgi.internal;

import java.util.Dictionary;
import java.util.Hashtable;

public class DictionaryBuilder<K, V> {

    final Dictionary<K, V> table = new Hashtable<>();

    public DictionaryBuilder<K, V> map(K k, V v) {
        table.put(k, v);
        return this;
    }

    public Dictionary<K, V> build() {
        return table;
    }
}
