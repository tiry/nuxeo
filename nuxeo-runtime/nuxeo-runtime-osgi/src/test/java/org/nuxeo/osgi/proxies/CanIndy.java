package org.nuxeo.osgi.proxies;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.Assert;
import org.junit.Test;
import org.nuxeo.osgi.internal.proxy.MethodBuilder;
import org.nuxeo.osgi.internal.proxy.Proxies;
import org.nuxeo.osgi.internal.proxy.ProxyContext;
import org.nuxeo.osgi.internal.proxy.ProxyFactory;
import org.nuxeo.osgi.internal.proxy.ProxyHandler;

public class CanIndy {

    @Test
    public void canProxyInterfaces() {
        ProxyFactory<Call> factory = Proxies.DEFAULT.proxy(Call.class,
                MethodType.methodType(Call.class).appendParameterTypes(Call.class), new ProxyHandler.Default() {

                    @Override
                    public CallSite bootstrap(ProxyContext context) throws Throwable {
                        MethodHandle target = MethodBuilder.methodBuilder(context.type())
                                .dropFirst()
                                .unreflect(MethodHandles.publicLookup(), context.method());
                        return new ConstantCallSite(target);
                    }
                });
        Call proxy = factory.create(new Concrete());
        try {
            proxy.call();
        } catch (RuntimeException cause) {
            Assert.assertEquals(10, cause.getStackTrace().length);
        }
        proxy.call();
    }

}
