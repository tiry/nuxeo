package org.nuxeo.osgi.internal.proxy;

import java.lang.invoke.CallSite;
import java.lang.reflect.Method;

public interface ProxyHandler {
    /**
     * Provide default implementations of all methods of {@link ProxyHandler} but
     * {@link ProxyHandler#bootstrap(ProxyContext)}.
     */
    public static abstract class Default implements ProxyHandler {
        /**
         * {@inheritDoc}
         *
         * @implSpec The implementation always returns false.
         */
        @Override
        public boolean override(Method method) {
            return false;
        }

        /**
         * {@inheritDoc}
         *
         * @implSpec The implementation always returns false.
         */
        @Override
        public boolean isMutable(int fieldIndex, Class<?> fieldType) {
            return false;
        }
    }

    /**
     * Define the bootstrap function as a functional interface.
     */
    public interface Bootstrap {
        /**
         * Called to link a proxy method to a target method handle (through a callsite's target). This method is
         * called once by method at runtime the first time the proxy method is called.
         *
         * @param context
         *            object containing information like the method that will be linked and methods to access
         *            the fields and methods of the proxy implementation.
         * @return a callsite object indicating how to link the method to a target method handle.
         * @throws Throwable
         *             if any errors occur.
         */
        public CallSite bootstrap(ProxyContext context) throws Throwable;
    }

    /**
     * Returns true if the proxy field should be mutable.
     *
     * @param fieldIndex
     *            the index of the proxy field.
     * @param fieldType
     *            the type of the proxy field.
     * @return true if the proxy field should be mutable, false otherwise.
     */
    public boolean isMutable(int fieldIndex, Class<?> fieldType);

    /**
     * Returns true if the method should be overridden by the proxy. This method is only called for method that have
     * an existing implementation (default methods or Object's toString(), equals() and hashCode(). This method is
     * called once by method when generating the proxy call.
     *
     * @param method
     *            a method of the interface that may be overridden
     * @return true if the method should be overridden by the proxy.
     */
    public boolean override(Method method);

    /**
     * Called to link a proxy method to a target method handle (through a callsite's target). This method is called
     * once by method at runtime the first time the proxy method is called.
     *
     * @param context
     *            object containing information like the method that will be linked and methods to access the
     *            fields and methods of the proxy implementation.
     * @return a callsite object indicating how to link the method to a target method handle.
     * @throws Throwable
     *             if any errors occur.
     */
    public CallSite bootstrap(ProxyContext context) throws Throwable;
}