package org.nuxeo.osgi.system;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.nuxeo.common.trycompanion.SneakyThrow;
import org.nuxeo.common.trycompanion.Try;
import org.nuxeo.common.trycompanion.TryCompanion;
import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.hooks.resolver.ResolverHook;
import org.osgi.framework.hooks.resolver.ResolverHookFactory;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.service.repository.Repository;
import org.osgi.service.resolver.HostedCapability;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.ResolveContext;
import org.osgi.service.resolver.Resolver;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class OSGiResolver {

    final OSGiSystem system;

    OSGiResolver(OSGiSystem system) {
        this.system = system;
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<OSGiResolver>() {

                    @Override
                    public Class<OSGiResolver> typeof() {
                        return OSGiResolver.class;
                    }

                    @Override
                    public OSGiResolver adapt(Bundle bundle) {
                        return OSGiResolver.this;
                    }

                });
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<Resolver>() {

                    @Override
                    public Class<Resolver> typeof() {
                        return Resolver.class;
                    }

                    @Override
                    public Resolver adapt(Bundle bundle) throws BundleException {
                        return resolverHandlers.resolver;
                    }

                });

        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<ResolverHookFactory>() {

                    @Override
                    public Class<ResolverHookFactory> typeof() {
                        return ResolverHookFactory.class;
                    }

                    @Override
                    public ResolverHookFactory adapt(Bundle bundle) throws BundleException {
                        return hooksHandler.factory;
                    }
                });
    }

    final BundleActivator activator = new Activator();

    final ResolversHandler resolverHandlers = new ResolversHandler();

    final HooksHandler hooksHandler = new HooksHandler();

    <T> T adapt(Class<T> type) {
        return type.cast(activator);
    }

    class BootHandler {

        BootHandler(ResolveContext context) {
            this.context = context;
        }

        final ResolveContext context;

        List<Capability> findProviders(Requirement requirement) {
            return insertHosted(context.findProviders(requirement), requirement);
        }

        List<Capability> insertHosted(List<Capability> providers, Requirement requirement) {
            if (!PackageNamespace.PACKAGE_NAMESPACE.equals(requirement.getNamespace())) {
                return providers;
            }
            String classpath = (String) requirement.getAttributes()
                    .get(PackageNamespace.PACKAGE_NAMESPACE);
            if (!system.bootstrap.matchBootPackages(classpath)) {
                return providers;
            }
            if (providers.isEmpty()) {
                context.insertHostedCapability(providers, new BootRuntimeCapability(classpath));
            } else {
                new ArrayList<>(providers).forEach(capability -> context.insertHostedCapability(providers,
                        new BootHostedCapability((BundleCapability) capability)));
            }
            return providers;
        }

        class BootHostedCapability implements BundleCapability, HostedCapability {

            BootHostedCapability(BundleCapability source) {
                super();
                this.source = source;
            }

            final BundleCapability source;

            @Override
            public BundleRevision getRevision() {
                return source.getRevision();
            }

            @Override
            public String getNamespace() {
                return source.getNamespace();
            }

            @Override
            public Map<String, String> getDirectives() {
                return source.getDirectives();
            }

            @Override
            public Map<String, Object> getAttributes() {
                return source.getAttributes();
            }

            @Override
            public boolean equals(Object obj) {
                return source.equals(obj);
            }

            @Override
            public BundleRevision getResource() {
                return source.getResource();
            }

            @Override
            public int hashCode() {
                return source.hashCode();
            }

            @Override
            public Capability getDeclaredCapability() {
                return source;
            }

        }

        class BootRuntimeCapability implements BundleCapability, HostedCapability {

            final Map<String, Object> attributes = new HashMap<>();

            BootRuntimeCapability(String classpath) {
                attributes.put(PackageNamespace.PACKAGE_NAMESPACE, classpath);
            }

            @Override
            public String getNamespace() {
                return PackageNamespace.PACKAGE_NAMESPACE;
            }

            @Override
            public Map<String, String> getDirectives() {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, Object> getAttributes() {
                return attributes;
            }

            @Override
            public BundleRevision getRevision() {
                return system.adapt(BundleRevision.class);
            }

            @Override
            public BundleRevision getResource() {
                return system.adapt(BundleRevision.class);
            }

            @Override
            public Capability getDeclaredCapability() {
                return this;
            }

            @Override
            public boolean equals(Object object) {
                if (object == null) {
                    return false;
                }
                if (!(object instanceof Capability)) {
                    return false;
                }
                Capability other = (Capability) object;
                String namespace = getNamespace();
                if (!namespace.equals(other.getNamespace())) {
                    return false;
                }
                Object name = getAttributes().get(namespace);
                if (!name.equals(other.getAttributes()
                        .get(namespace))) {
                    return false;
                }
                return true;
            }

            @Override
            public int hashCode() {
                final int prime = 31;
                int result = 1;
                result = prime * result + getNamespace().hashCode();
                result = prime * result + getAttributes().get(getNamespace())
                        .hashCode();
                result = prime * result + getResource().hashCode();
                return result;
            }

            @Override
            public String toString() { // @formatter:off
        return new StringBuilder()
            .append("Boot Capability@").append(Integer.toHexString(hashCode()))
            .append("[")
            .append("namespace=").append(getNamespace()).append(",")
            .append("atttributes=").append(getAttributes()).append(",")
            .append("directives=").append(getDirectives())
            .append("]").toString();
      } // @formatter:on

        }

    }

    class ModuleResolver implements Resolver {

        @Override
        public Map<Resource, List<Wire>> resolve(ResolveContext context) throws ResolutionException {
            return new Fluent(context).resolve(context.getMandatoryResources())
                    .orElseThrow("Cannot resolve mandatory resources")
                    .resolve(context.getOptionalResources())
                    .get();
        }

        class Fluent {
            Session session;

            Try<Void> monitor = TryCompanion.<Void> of(ResolutionException.class)
                    .empty();

            Fluent(ResolveContext request) {
                session = new Session(request);
            }

            Fluent resolve(Collection<Resource> resources) {
                monitor = monitor.sneakyRun(() -> session.resolve(resources));
                return this;
            }

            Fluent orElseThrow(String message) throws ResolutionException {
                monitor.orElseSummaryThrow(
                        summary -> new ResolutionException(message + System.lineSeparator() + summary));
                return this;
            }

            Map<Resource, List<Wire>> get() {
                return session.end();
            }
        }

        class Session {
            final ResolveContext context;

            final BootHandler boothandler;

            final HostHandler hosthandler = new HostHandler();

            final Map<String, List<Resource>> byHosts;

            final Set<Resource> pendings = new HashSet<>();

            final Map<Resource, List<Wire>> resolution = new LinkedHashMap<>();

            final Map<Capability, List<ResolverWire>> provides = new HashMap<>();

            final Map<Resource, Wiring> wirings;

            Session(ResolveContext context) {
                this.context = context;
                boothandler = new BootHandler(context);
                wirings = context.getWirings();
                byHosts = indexFragments();
            }

            Map<String, List<Resource>> indexFragments() {
                Repository repository = system.adapt(OSGiRepository.class).systemRepository;
                Requirement bundles = repository.newRequirementBuilder(HostNamespace.HOST_NAMESPACE)
                        .setResource(system.adapt(BundleRevision.class))
                        .addDirective(Namespace.REQUIREMENT_FILTER_DIRECTIVE, "(&(osgi.wiring.host=*))")
                        .build();
                return context.findProviders(bundles)
                        .stream()
                        .map(Capability::getResource)
                        .flatMap(resource -> resource.getRequirements(HostNamespace.HOST_NAMESPACE)
                                .stream())
                        .collect(Collectors.groupingBy(
                                requirement -> requirement.getAttributes()
                                        .get(HostNamespace.HOST_NAMESPACE)
                                        .toString(),
                                Collectors.mapping(Requirement::getResource, Collectors.toList())));

            }

            List<Resource> lookupFragments(Resource resource) {
                if (!(resource instanceof BundleRevision)) {
                    return Collections.emptyList();
                }
                return byHosts.getOrDefault(((BundleRevision) resource).getBundle()
                        .getSymbolicName(),
                        Collections.emptyList());
            }

            void resolve(Collection<Resource> resources) throws ResolutionException {
                TryCompanion.<Void> of(ResolutionException.class)
                        .sneakyForEachAndCollect(resources.stream(), this::resolve)
                        .orElseThrow(() -> new ResolutionException("Cannot resolve resources"));
            }

            void resolve(Resource resource) throws ResolutionException {
                if (wirings.containsKey(resource)
                        || resolution.containsKey(resource)) {
                    return;
                }
                if (!pendings.add(resource)) {
                    return;
                }
                TryCompanion.<Stream<Wire>> of(ResolutionException.class)
                        .sneakyMapAndCollect(resource
                                .getRequirements(null)
                                .stream(),
                                this::resolve)
                        .peek(self -> pendings.remove(resource))
                        .onSuccess(self -> resolution.put(
                                resource,
                                self.stream()
                                        .flatMap(wires -> wires)
                                        .collect(Collectors.toList())))
                        .orElseThrow(() -> new ResolutionException("Cannot resolve " + resource));
                resolve(lookupFragments(resource));
            }

            Stream<Wire> resolve(Requirement requirement) throws ResolutionException {
                List<Capability> providers = boothandler.findProviders(requirement);
                boolean isMandatory = Optional
                        .ofNullable(requirement.getDirectives()
                                .get(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE))
                        .map(resolution -> resolution.equals(Namespace.RESOLUTION_MANDATORY))
                        .orElse(false);
                Try<Wire> result = TryCompanion.<Wire> of(ResolutionException.class)
                        .sneakyMapAndCollect(providers.stream(), capability -> resolve(requirement, capability));
                if (isMandatory) {
                    return result.orElseThrow(() -> new ResolutionException("Cannot resolve mandatory " + requirement))
                            .stream();
                }
                return result.stream();
            }

            Wire resolve(Requirement requirement, Capability capability) throws ResolutionException {
                resolve(capability.getResource());
                ResolverWire wire = new ResolverWire(requirement, capability);
                provides.computeIfAbsent(capability, key -> new LinkedList<>())
                        .add(wire);
                return wire;
            }

            Map<Resource, List<Wire>> end() {
                hosthandler.remap();
                return resolution;
            }

            class ResolverWire implements Wire {

                ResolverWire(Requirement requirement, Capability capability) {
                    super();
                    this.requirement = requirement;
                    this.capability = capability;
                }

                final Requirement requirement;

                Capability capability;

                @Override
                public Capability getCapability() {
                    return capability;
                }

                @Override
                public Requirement getRequirement() {
                    return requirement;
                }

                @Override
                public Resource getProvider() {
                    return capability.getResource();
                }

                @Override
                public Resource getRequirer() {
                    return requirement.getResource();
                }

                @Override
                public String toString() { // @formatter:off
                    return new StringBuilder()
                            .append("Resolved Wire@").append(Integer.toHexString(hashCode()))
                            .append("[")
                            .append("requirement=").append(requirement).append(",")
                            .append("capability=").append(capability)
                            .append("]").toString();
                } // @formatter:on

            }

            class HostHandler {

                void remap() {
                    resolution.keySet()
                            .forEach(this::remap);
                }

                void remap(Resource resource) {
                    if (!(resource instanceof BundleRevision)) {
                        return;
                    }
                    BundleRevision revision = (BundleRevision) resource;
                    hostof((BundleRevision) resource).ifPresent(host -> remap(host, revision.getCapabilities(null)));
                }

                void remap(BundleRevision host, List<Capability> capabilities) {
                    capabilities.stream()
                            .filter(capability -> !HostNamespace.HOST_NAMESPACE.equals(capability.getNamespace()))
                            .forEach(capability -> remap(host, capability));
                }

                void remap(Resource host, Capability capability) {
                    provides.getOrDefault(capability, Collections.emptyList())
                            .forEach(wire -> remap(host, wire));
                }

                void remap(Resource host, ResolverWire wire) {
                    Requirement requirement = wire.getRequirement();
                    wire.capability = new ResolverHostedCapability(host, wire.capability);
                    context.insertHostedCapability(context.findProviders(requirement),
                            (HostedCapability) wire.capability);
                }

                Optional<BundleRevision> hostof(BundleRevision resource) {
                    return Optional.ofNullable(resolution.get(resource))
                            .orElseGet(
                                    () -> wirings.get(resource)
                                            .getRequiredResourceWires(HostNamespace.HOST_NAMESPACE))
                            .stream()
                            .filter(wire -> (wire.getRequirement()
                                    .getResource() == resource)
                                    && HostNamespace.HOST_NAMESPACE.equals(wire.getRequirement()
                                            .getNamespace()))
                            .findAny()
                            .map(wire -> (BundleRevision) wire.getCapability()
                                    .getResource())
                            .map(host -> hostof(host).orElse(host));
                }

                class ResolverHostedCapability implements BundleCapability, HostedCapability {

                    ResolverHostedCapability(Resource host, Capability source) {
                        this.host = (BundleRevision) host;
                        capability = (BundleCapability) source;
                    }

                    final BundleRevision host;

                    final BundleCapability capability;

                    @Override
                    public Capability getDeclaredCapability() {
                        return capability;
                    }

                    @Override
                    public String getNamespace() {
                        return capability.getNamespace();
                    }

                    @Override
                    public Map<String, String> getDirectives() {
                        return capability.getDirectives();
                    }

                    @Override
                    public Map<String, Object> getAttributes() {
                        return capability.getAttributes();
                    }

                    @Override
                    public BundleRevision getResource() {
                        return host;
                    }

                    @Override
                    public BundleRevision getRevision() {
                        return host;
                    }

                    @Override
                    public boolean equals(Object obj) {
                        if (obj instanceof HostedCapability) {
                            obj = ((HostedCapability) obj).getDeclaredCapability();
                        }
                        return capability.equals(obj);
                    }

                    @Override
                    public int hashCode() {
                        return capability.hashCode();
                    }

                    @Override
                    public String toString() { // @formatter:off
              return new StringBuilder()
              .append("Hosted Capability@").append(Integer.toHexString(hashCode()))
              .append("[")
              .append(host).append(",")
              .append(capability)
              .append("]").toString();
            } // @formatter:on
                };

            };

        }

    }

    @Override
    public String toString() {
        return "OSGiResolver@" + Integer.toHexString(hashCode());
    }

    class Activator implements BundleActivator {

        @Override
        public void start(BundleContext context) throws Exception {
            TryCompanion.<Void> of(Exception.class)
                    .sneakyRun(() -> hooksHandler.activator.start(context))
                    .sneakyRun(() -> resolverHandlers.activator.start(context))
                    .orElseThrow(
                            () -> new BundleException("Cannot activate trackers", BundleException.ACTIVATOR_ERROR));
        }

        @Override
        public void stop(BundleContext context) throws Exception {
            TryCompanion.<Void> of(Exception.class)
                    .sneakyRun(() -> hooksHandler.activator.stop(context))
                    .sneakyRun(() -> resolverHandlers.activator.stop(context))
                    .orElseThrow(
                            () -> new BundleException("Cannot de-activate trackers", BundleException.ACTIVATOR_ERROR));
        }
    }

    class ResolversHandler {

        final List<Resolver> extensions = new LinkedList<Resolver>();

        {
            extensions.add(new ModuleResolver());
        }

        final Resolver resolver = new Resolver() {

            @Override
            public Map<Resource, List<Wire>> resolve(ResolveContext context) throws ResolutionException {
                Session session = new Session(context);
                extensions.forEach(SneakyThrow.sneakyConsumer(session::apply));
                return session.wires;
            }

        };

        final Activator activator = new Activator();

        class Session extends ResolveContext {

            @SuppressWarnings({ "rawtypes", "unchecked" })
            Session(ResolveContext context) {
                this.context = context;
                wirings = new MergedMap(transients, context.getWirings());
            }

            final ResolveContext context;

            final Map<Resource, Wiring> wirings;

            final Map<Resource, TransientWiring> transients = new HashMap<>();

            final Map<Resource, List<Wire>> wires = new LinkedHashMap<>();

            void apply(Resolver resolver) throws ResolutionException {
                commit(resolver.resolve(this));
            }

            void commit(Map<Resource, List<Wire>> other) {
                other.forEach((resource, wires) -> {
                    wires.forEach(this::wire);
                    this.wires.computeIfAbsent(resource, key -> new LinkedList<>())
                            .addAll(wires);
                });

            }

            void wire(Wire wire) {
                transients.computeIfAbsent(wire.getProvider(), TransientWiring::new).provided.add(wire);
                transients.computeIfAbsent(wire.getRequirer(), TransientWiring::new).required.add(wire);
            }

            @Override
            public List<Capability> findProviders(Requirement requirement) {
                return context.findProviders(requirement);
            }

            @Override
            public int insertHostedCapability(List<Capability> capabilities, HostedCapability hostedCapability) {
                return context.insertHostedCapability(capabilities, hostedCapability);
            }

            @Override
            public boolean isEffective(Requirement requirement) {
                return context.isEffective(requirement);
            }

            @Override
            public Map<Resource, Wiring> getWirings() {
                return wirings;
            }

            @Override
            public Collection<Resource> getMandatoryResources() {
                return context.getMandatoryResources();
            }

            @Override
            public Collection<Resource> getOptionalResources() {
                return context.getOptionalResources();
            }

            class MergedMap<K, V> extends AbstractMap<K, V> {

                final Map<K, V> first;

                final Map<K, V> other;

                MergedMap(Map<K, V> first, Map<K, V> other) {
                    this.first = first;
                    this.other = other;
                }

                @Override
                public boolean containsKey(Object key) {
                    return first.containsKey(key) || other.containsKey(key);
                }

                @Override
                public Set<Entry<K, V>> entrySet() {
                    return new AbstractSet<Entry<K, V>>() {

                        @Override
                        public Iterator<Entry<K, V>> iterator() {
                            return new Iterator<Entry<K, V>>() {

                                final Iterator<Entry<K, V>> firstIterator = first.entrySet()
                                        .iterator();

                                final Iterator<Entry<K, V>> otherIterator = other.entrySet()
                                        .iterator();

                                Iterator<Entry<K, V>> current = firstIterator.hasNext() ? firstIterator : otherIterator;

                                @Override
                                public boolean hasNext() {
                                    return current.hasNext();
                                }

                                @Override
                                public Entry<K, V> next() {
                                    try {
                                        return current.next();
                                    } finally {
                                        if (current.hasNext() == false && current == firstIterator) {
                                            current = otherIterator;
                                        }
                                    }
                                }

                            };
                        }

                        @Override
                        public int size() {
                            return first.size() + other.size();
                        }

                    };
                }
            }

            class TransientWiring implements Wiring {

                TransientWiring(Resource resource) {
                    this.resource = resource;
                }

                final Resource resource;

                final List<Wire> provided = new LinkedList<>();

                final List<Wire> required = new LinkedList<>();

                @Override
                public List<Capability> getResourceCapabilities(String namespace) {
                    return provided.stream()
                            .map(Wire::getCapability)
                            .filter(capability -> capability.getNamespace()
                                    .equals(namespace))
                            .collect(Collectors.toList());
                }

                @Override
                public List<Requirement> getResourceRequirements(String namespace) {
                    return required.stream()
                            .map(Wire::getRequirement)
                            .filter(capability -> capability.getNamespace()
                                    .equals(namespace))
                            .collect(Collectors.toList());
                }

                @Override
                public List<Wire> getProvidedResourceWires(String namespace) {
                    return provided.stream()
                            .filter(wire -> wire.getCapability()
                                    .getNamespace()
                                    .equals(namespace))
                            .collect(Collectors.toList());
                }

                @Override
                public List<Wire> getRequiredResourceWires(String namespace) {
                    return provided.stream()
                            .filter(wire -> wire.getRequirement()
                                    .getNamespace()
                                    .equals(namespace))
                            .collect(Collectors.toList());
                }

                @Override
                public Resource getResource() {
                    return resource;
                }

            }
        };

        class Activator implements BundleActivator {
            ServiceTracker<Resolver, Resolver> tracker;

            @Override
            public void start(BundleContext context) {
                tracker = new ServiceTracker<>(context, Resolver.class,
                        new ServiceTrackerCustomizer<Resolver, Resolver>() {

                            @Override
                            public Resolver addingService(ServiceReference<Resolver> reference) {
                                Resolver extension = context.getService(reference);
                                extensions.add(extension);
                                return extension;
                            }

                            @Override
                            public void modifiedService(ServiceReference<Resolver> reference, Resolver factory) {
                                ;
                            }

                            @Override
                            public void removedService(ServiceReference<Resolver> reference, Resolver factory) {
                                extensions.remove(factory);
                                context.ungetService(reference);
                            }
                        });
                tracker.open();
            }

            @Override
            public void stop(BundleContext context) {
                try {
                    tracker.close();
                } finally {
                    tracker = null;
                }
            }
        }

    }

    class HooksHandler {

        final Factory factory = new Factory();

        final List<ResolverHookFactory> extensions = new LinkedList<ResolverHookFactory>();

        final Activator activator = new Activator();

        class Factory implements ResolverHookFactory {

            final ResolverHook noop = new ResolverHook() {

                @Override
                public void filterSingletonCollisions(BundleCapability singleton,
                        Collection<BundleCapability> collisionCandidates) {
                }

                @Override
                public void filterResolvable(Collection<BundleRevision> candidates) {
                }

                @Override
                public void filterMatches(BundleRequirement requirement, Collection<BundleCapability> candidates) {
                }

                @Override
                public void end() {
                }
            };

            @Override
            public ResolverHook begin(Collection<BundleRevision> triggers) {
                if (extensions.isEmpty()) {
                    return noop;
                }
                return new ResolverHook() {
                    List<ResolverHook> hooks = extensions.stream()
                            .map(factory -> factory.begin(triggers))
                            .collect(Collectors.toList());

                    @Override
                    public void filterSingletonCollisions(BundleCapability singleton,
                            Collection<BundleCapability> collisionCandidates) {
                        hooks.forEach(hook -> hook.filterSingletonCollisions(singleton, collisionCandidates));
                    }

                    @Override
                    public void filterResolvable(Collection<BundleRevision> candidates) {
                        hooks.forEach(hook -> hook.filterResolvable(candidates));
                    }

                    @Override
                    public void filterMatches(BundleRequirement requirement, Collection<BundleCapability> candidates) {
                        hooks.forEach(hook -> hook.filterMatches(requirement, candidates));
                    }

                    @Override
                    public void end() {
                        hooks.forEach(hook -> hook.end());
                    }
                };
            }
        }

        class Activator implements BundleActivator {
            ServiceTracker<ResolverHookFactory, ResolverHookFactory> tracker;

            @Override
            public void start(BundleContext context) {
                tracker = new ServiceTracker<>(context, ResolverHookFactory.class,
                        new ServiceTrackerCustomizer<ResolverHookFactory, ResolverHookFactory>() {

                            @Override
                            public ResolverHookFactory addingService(ServiceReference<ResolverHookFactory> reference) {
                                final ResolverHookFactory extension = context.getService(reference);
                                extensions.add(extension);
                                return extension;
                            }

                            @Override
                            public void modifiedService(ServiceReference<ResolverHookFactory> reference,
                                    ResolverHookFactory factory) {
                                ;
                            }

                            @Override
                            public void removedService(ServiceReference<ResolverHookFactory> reference,
                                    ResolverHookFactory factory) {
                                extensions.remove(factory);
                                context.ungetService(reference);
                            }
                        });
                tracker.open();
            }

            @Override
            public void stop(BundleContext context) {
                try {
                    tracker.close();
                } finally {
                    tracker = null;
                }
            }
        }

    }

}
