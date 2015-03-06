package org.nuxeo.osgi.system;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

class OSGiService {

    final OSGiSystem system;

    final Map<BundleContext, Activation> byContexts = new HashMap<>();

    final Map<Class<?>, List<Registration<?>>> byTypes = new HashMap<>();

    OSGiService(OSGiSystem system) {
        this.system = system;
        system.adapt(OSGiBundleAdapter.Activation.class).install(new BundleAdapter<Activation>() {

            @Override
            public Class<Activation> typeof() {
                return Activation.class;
            }

            @Override
            public Activation adapt(Bundle bundle) {
                BundleContext context = bundle.adapt(BundleContext.class);
                if (!byContexts.containsKey(context)) {
                    return new Activation(bundle, context);
                }
                return byContexts.get(context);
            }

        });
    }

    <S> Enumeration<ServiceReference<S>> resolve(final Class<S> type, final Filter filter) {
        if (!byTypes.containsKey(type)) {
            return Collections.emptyEnumeration();
        }
        return new Enumeration<ServiceReference<S>>() {
            Enumeration<Registration<?>> base = Collections.enumeration(byTypes.get(type));

            Registration<S> next;

            @Override
            public boolean hasMoreElements() {
                if (next != null) {
                    return true;
                }
                return (next = fetchNext()) != null;
            }

            Registration<S> fetchNext() {
                if (next != null) {
                    return next;
                }
                while (base.hasMoreElements()) {
                    @SuppressWarnings("unchecked")
                    Registration<S> next = (Registration<S>) base.nextElement();
                    if (filter.match(next.getReference())) {
                        return next;
                    }
                }
                return null;
            }

            @Override
            public ServiceReference<S> nextElement() {
                if (next == null) {
                    next = fetchNext();
                }
                Registration<S> fetched = next;
                next = null;
                if (fetched == null) {
                    throw new NoSuchElementException();
                }
                return fetched.reference;
            }
        };
    }

    @Override
    public String toString() {
        return "OSGiService@" + Integer.toHexString(hashCode()) + byContexts;
    }

    class Activation {
        final Bundle bundle;

        final BundleContext context;

        final Set<Registration<?>> registrations = new HashSet<>();

        final Set<Registration<?>.Reference> references = new HashSet<>();

        Activation(Bundle bundle, BundleContext context) {
            this.bundle = bundle;
            this.context = context;
        }

        void install() {
            byContexts.put(context, this);
        }

        void uninstall() {
            for (Registration<?> registration : registrations) {
                byTypes.remove(registration);
                context.getBundle().adapt(ServiceListener.class).serviceChanged(
                        new ServiceEvent(ServiceEvent.UNREGISTERING, registration.reference));
            }
            byContexts.remove(context);
        }

        <S> Registration<S> register(BundleContext context, Class<S> type, S instance, Dictionary<String, ?> dict) {
            return new Registration<S>(context, type, new ServiceFactory<S>() {

                @Override
                public S getService(Bundle bundle, ServiceRegistration<S> registration) {
                    return instance;
                }

                @Override
                public void ungetService(Bundle bundle, ServiceRegistration<S> registration, S service) {
                    ;
                }
            }, dict).register();
        }

        <S> Registration<S> register(BundleContext context, Class<S> type, ServiceFactory<S> instance,
                Dictionary<String, ?> dict) {
            return new Registration<S>(context, type, instance, dict).register();
        }

        @Override
        public String toString() {
            return "OSGiService$Activation@" + Integer.toHexString(hashCode()) + registrations;
        }

        @SuppressWarnings("unchecked")
        Enumeration<ServiceReference<?>> resolve(String classname, Filter filter) {
            try {
                @SuppressWarnings("rawtypes")
                Class type = bundle.loadClass(classname);
                return OSGiService.this.resolve(type, filter);
            } catch (ClassNotFoundException e) {
                return Collections.emptyEnumeration();
            }
        }

        <S> Enumeration<ServiceReference<S>> resolve(Class<S> type, Filter filter) {
            return OSGiService.this.resolve(type, filter);
        }
    }

    class Registration<S> implements ServiceRegistration<S> {

        final BundleContext context;

        final Class<S> type;

        final ServiceFactory<S> factory;

        final Hashtable<String, Object> dict = new Hashtable<String, Object>();

        final Reference reference;

        Registration(BundleContext context, Class<S> type, ServiceFactory<S> factory, Dictionary<String, ?> dict) {
            this.type = type;
            this.context = context;
            this.factory = factory;
            this.reference = new Reference();
            this.dict.put("objectClass", type.getName());
            if (dict != null) {
                setProperties(dict);
            }
        }

        @Override
        public ServiceReference<S> getReference() {
            return reference;
        }

        @Override
        public void setProperties(Dictionary<String, ?> dict) {
            Enumeration<String> en = dict.keys();
            while (en.hasMoreElements()) {
                String key = en.nextElement();
                this.dict.put(key, dict.get(key));
            }
            system.adapt(ServiceListener.class).serviceChanged(new ServiceEvent(ServiceEvent.MODIFIED, reference));
        }

        Registration<S> register() {
            Activation activation = byContexts.get(context);
            if (activation == null) {
                throw new IllegalStateException("no activation for " + context.getBundle());
            }
            activation.registrations.add(this);
            ServiceEvent event;
            if (!byTypes.containsKey(type)) {
                byTypes.put(type, new ArrayList<Registration<?>>());
                event = new ServiceEvent(ServiceEvent.REGISTERED, reference);
            } else {
                event = new ServiceEvent(ServiceEvent.MODIFIED, reference);
            }
            byTypes.get(type).add(this);
            system.adapt(ServiceListener.class).serviceChanged(event);
            return this;
        }

        @Override
        public void unregister() {
            byTypes.get(type).remove(this);
            byContexts.get(context).registrations.remove(this);
            system.adapt(ServiceListener.class).serviceChanged(new ServiceEvent(ServiceEvent.UNREGISTERING, reference));
        }

        class Reference implements ServiceReference<S> {

            final ReferenceCount<Bundle> usages = new ReferenceCount<>(Bundle.class);

            S instance;

            @Override
            public Object getProperty(String key) {
                return dict.get(key);
            }

            @Override
            public String[] getPropertyKeys() {
                Set<String> keySet = dict.keySet();
                return keySet.toArray(new String[keySet.size()]);
            }

            @Override
            public Bundle getBundle() {
                return context.getBundle();
            }

            @Override
            public Bundle[] getUsingBundles() {
                return usages.references();
            }

            @Override
            public boolean isAssignableTo(Bundle bundle, String className) {
                try {
                    return bundle.loadClass(className).isAssignableFrom(type);
                } catch (ClassNotFoundException e) {
                    return false;
                }
            }

            @Override
            public int compareTo(Object reference) {
                throw new UnsupportedOperationException("Not implemented");
            }

            S getService(Bundle bundle) {
                if (instance == null) {
                    instance = factory.getService(bundle, Registration.this);
                }
                usages.adapt(bundle).increment(1);
                return instance;
            }

            boolean ungetService(Bundle bundle) {
                boolean last = usages.adapt(bundle).decrement(1) <= 0;
                if (!last) {
                    return true;
                }
                try {
                    factory.ungetService(bundle, Registration.this, instance);
                } finally {
                    instance = null;
                }
                return false;
            }

            @Override
            public String toString() {
                return "Reference<" + type.getSimpleName() + ">@" + Integer.toHexString(hashCode()) + "[" + factory
                        + "]";
            }
        }

        @Override
        public String toString() {
            return "OSGiService$Registration<" + type.getSimpleName() + ">@" + Integer.toHexString(hashCode()) + "["
                    + context + "," + reference + "]";
        }
    }

    class ReferenceCount<R extends Comparable<R>> {

        final Class<R> type;

        final Map<R, Count> usages = new HashMap<>();

        public class Count {
            final R reference;

            int count;

            Count(R reference) {
                this.reference = reference;
            }

            public int increment(int inc) {
                try {
                    return count += inc;
                } finally {
                    if (count > 0) {
                        usages.put(reference, this);
                    }
                }
            }

            public int decrement(int dec) {
                try {
                    return count -= dec;
                } finally {
                    if (count <= 0) {
                        usages.remove(reference);
                    }
                }
            }
        }

        public ReferenceCount(Class<R> type) {
            this.type = type;
        }

        public Count adapt(R reference) {
            if (!usages.containsKey(reference)) {
                return new Count(reference);
            }
            return usages.get(reference);
        }

        @SuppressWarnings("unchecked")
        public R[] references() {
            return usages.keySet().toArray((R[]) Array.newInstance(type, usages.size()));
        }

        StringBuilder internalToString(StringBuilder builder) {
            builder.append(usages.keySet());
            return builder;
        }

        @Override
        public String toString() {
            return "ReferenceCount[" + internalToString(new StringBuilder()) + "]";
        }

    }

}
