package org.nuxeo.common.collections;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Streams {

    @FunctionalInterface
    public interface ConsumerCheckException<T> {
        void accept(T elem) throws Exception;
    }

    @java.lang.FunctionalInterface
    public interface FunctionCheckException<T, R> {
        R apply(T elem) throws Exception;
    }

    public static <T> Enumeration<T> enumeration(Stream<T> stream) {
        Iterator<T> iterator = stream.iterator();
        return new Enumeration<T>() {

            @Override
            public boolean hasMoreElements() {
                return iterator.hasNext();
            }

            @Override
            public T nextElement() {
                return iterator.next();
            }

        };
    }

    public static <T> Stream<T> of(Enumeration<T> e) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator<T>() {
            @Override
            public T next() {
                return e.nextElement();
            }

            @Override
            public boolean hasNext() {
                return e.hasMoreElements();
            }
        }, Spliterator.ORDERED), false);
    }

}