package org.nuxeo.common.trycompanion;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

public interface Container<T> extends Iterable<T> {

    Collection<T> collection();

    default Stream<T> stream() {
        return collection().stream();
    }

    @Override
    default Iterator<T> iterator() {
        return collection().iterator();
    }

    default Optional<T> get() {
        return stream().findFirst();
    }

    public class SingleContainer<T> implements Container<T> {

        T t;

        @Override
        public Collection<T> collection() {
            if (t == null) {
                return Collections.emptyList();
            }
            return Collections.singleton(t);
        }

        public T set(T value) {
            T last = t;
            t = value;
            return last;
        }

    }
}
