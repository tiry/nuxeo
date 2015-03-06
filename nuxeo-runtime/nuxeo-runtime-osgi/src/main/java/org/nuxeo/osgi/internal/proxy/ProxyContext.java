package org.nuxeo.osgi.internal.proxy;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;

/**
 * Object that encapsulate the data that are available to implement a proxy method.
 */
public class ProxyContext {
    private final Lookup lookup;

    private final MethodType methodType;

    private final Method method;

    private ProxyContext(Lookup lookup, MethodType methodType, Method method) {
        this.lookup = lookup;
        this.methodType = methodType;
        this.method = method;
    }

    /**
     * Returns the interface method about to be linked.
     *
     * @return the interface method about to be linked.
     */
    public Method method() {
        return method;
    }

    /**
     * Returns the method type of the invokedynamic call inside the implementation of the proxy method. This method
     * type must also be the {@link CallSite#type() type of the callsite} returned by
     * {@link ProxyHandler#bootstrap(ProxyContext)}.
     *
     * @return type of the invokedynamic inside the implementation of the proxy method.
     */
    public MethodType type() {
        return methodType;
    }

    /**
     * Returns a method handle that returns the value of a field of the proxy.
     *
     * @param fieldIndex
     *            the index of the field.
     * @param type
     *            the type of the field
     * @return a method handle that returns the value of a field of the proxy.
     * @throws NoSuchFieldException
     *             if the field doesn't exist.
     * @see Lookup#findGetter(Class, String, Class)
     */
    public MethodHandle findFieldGetter(int fieldIndex, Class<?> type) throws NoSuchFieldException {
        try {
            return lookup.findGetter(lookup.lookupClass(), "arg" + fieldIndex, type)
                    .asType(MethodType.methodType(type, Object.class));
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns a method handle that set the value of a field of the proxy. The field must be
     * {@link ProxyHandler#isMutable(int, Class) mutable}.
     *
     * @param fieldIndex
     *            the index of the field.
     * @param type
     *            the type of the field
     * @return a method handle that set the value of a field of the proxy..
     * @throws NoSuchFieldException
     *             if the field doesn't exist.
     * @see Lookup#findSetter(Class, String, Class)
     */
    public MethodHandle findFieldSetter(int fieldIndex, Class<?> type) throws NoSuchFieldException {
        try {
            return lookup.findSetter(lookup.lookupClass(), "arg" + fieldIndex, type)
                    .asType(MethodType.methodType(void.class, Object.class, type));
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    // referenced by a method handle
    static ProxyContext create(Lookup lookup, MethodType methodType, Method method) {
        return new ProxyContext(lookup, methodType, method);
    }
}