/*
 * (C) Copyright 2006-2012 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     IBM Corporation - initial API and implementation
 */
package org.nuxeo.common.collections;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Internal class to maintain a list of listeners. This class is a thread-safe list that is optimized for frequent reads
 * and infrequent writes. Copy on write is used to ensure readers can access the list without synchronization overhead.
 * Readers are given access to the underlying array data structure for reading, with the trust that they will not modify
 * the underlying array.
 * <p>
 * Contains code from Eclipse org.eclipse.core.runtime.ListenerList class
 *
 * @see http://www.eclipse.org
 */
public class ListenerList<T> {

    /**
     * Mode constant (value 0) indicating that listeners should be compared using equality.
     */
    public static final int EQUALITY = 0;

    /**
     * Mode constant (value 1) indicating that listeners should be compared using identity.
     */
    public static final int IDENTITY = 1;

    /**
     * Indicates the comparison mode used to determine if two listeners are equivalent.
     */
    private final int compareMode;

    /**
     * The list of listeners. Initially <code>null</code> but initialized to an array of size capacity the first time a
     * listener is added. Maintains invariant: <code>listeners != null</code>.
     */
    private volatile T[] listeners;

    private final Class<T> typeof;

    /**
     * Used to order listeners
     */
    private final Comparator<T> comparator;

    /**
     * Creates a listener list.
     */
    public ListenerList(Class<T> typeof) {
        this(typeof, EQUALITY, null);
    }

    public ListenerList(Class<T> typeof, Comparator<T> comparator) {
        this(typeof, EQUALITY, comparator);
    }

    /**
     * Creates a listener list using the provided comparison mode.
     */
    public ListenerList(Class<T> typeof, int compareMode, Comparator<T> comparator) {
        this.typeof = typeof;
        this.compareMode = compareMode;
        this.comparator = comparator;
        this.listeners = newArray(0);
    }

    /**
     * Adds the given listener to this list. Has no effect if an equal listener is already registered.
     * <p>
     * This method is synchronized to protect against multiple threads adding or removing listeners concurrently. This
     * does not block concurrent readers.
     *
     * @param listener the listener to add
     */
    public synchronized void add(T listener) {
        // check for duplicates
        final int oldSize = listeners.length;
        for (int i = 0; i < oldSize; ++i) {
            if (same(listener, listeners[i])) {
                return;
            }
        }
        // Thread safety: create new array to avoid affecting concurrent readers
        T[] newListeners = newArray(oldSize+1);
        System.arraycopy(listeners, 0, newListeners, 0, oldSize);
        newListeners[oldSize] = listener;
        if (comparator != null) {
            Arrays.sort(newListeners, comparator);
        }
        // atomic assignment
        listeners = newListeners;
    }

    /**
     * Returns an array containing all the registered listeners. The resulting array is unaffected by subsequent adds or
     * removes. If there are no listeners registered, the result is an empty array singleton instance (no garbage is
     * created). Use this method when notifying listeners, so that any modifications to the listener list during the
     * notification will have no effect on the notification itself.
     * <p>
     * Note: callers must not modify the returned array.
     *
     * @return the list of registered listeners
     */
    public T[] getListeners() {
        return listeners;
    }

    public synchronized T[] getListenersCopy() {
        T[] tmp = newArray(listeners.length);
        System.arraycopy(listeners, 0, tmp, 0, listeners.length);
        return tmp;
    }

    /**
     * Returns whether this listener list is empty.
     *
     * @return <code>true</code> if there are no registered listeners, and <code>false</code> otherwise
     */
    public boolean isEmpty() {
        return listeners.length == 0;
    }

    /**
     * Removes the given listener from this list. Has no effect if an identical listener was not already registered.
     * <p>
     * This method is synchronized to protect against multiple threads adding or removing listeners concurrently. This
     * does not block concurrent readers.
     *
     * @param listener the listener
     */
    public synchronized void remove(T listener) {
        int oldSize = listeners.length;
        for (int i = 0; i < oldSize; ++i) {
            if (same(listener, listeners[i])) {
                if (oldSize == 1) {
                    listeners = newArray(0);
                } else {
                    // Thread safety: create new array to avoid affecting concurrent readers
                    T[] newListeners = newArray(oldSize - 1);
                    System.arraycopy(listeners, 0, newListeners, 0, i);
                    System.arraycopy(listeners, i + 1, newListeners, i, oldSize - i - 1);
                    // atomic assignment to field
                    listeners = newListeners;
                }
                return;
            }
        }
    }

    /**
     * Returns <code>true</code> if the two listeners are the same based on the specified comparison mode, and
     * <code>false</code> otherwise.
     */
    private boolean same(Object listener1, Object listener2) {
        return compareMode == IDENTITY ? listener1 == listener2 : listener1.equals(listener2);
    }

    /**
     * Returns the number of registered listeners.
     *
     * @return the number of registered listeners
     */
    public int size() {
        return listeners.length;
    }

    @SuppressWarnings("unchecked")
    T[] newArray(int length)  {
        return (T[])Array.newInstance(typeof, length);
    }

}
