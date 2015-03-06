package org.nuxeo.osgi.internal.proxy;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * A factory of proxy implementing an interface.
 *
 * @param <T>
 *            the type of the proxy interface.
 * @see Proxy2#proxy(Class, Class[], ProxyHandler)
 */
public class ProxyFactory<T> {

    final Class<T> type;

    final MethodHandle target;

    ProxyFactory(Class<T> type, MethodHandle handle) {
        this.type = type;
        this.target = handle;
    }

    /**
     * Create a proxy with a value for each field of the proxy.
     *
     * @param fieldValues
     *            the value of each field of the proxy.
     * @return a new proxy instance.
     */
    public T create(Object... parms) {
        try {
            Object object = target.invokeWithArguments(parms);
            return type.cast(object);
        } catch (RuntimeException | Error cause) {
            throw cause;
        } catch (Throwable cause) {
            throw new UndeclaredThrowableException(cause);
        }
    }

    public Class<? extends T> getType() {
        return type;
    }

}