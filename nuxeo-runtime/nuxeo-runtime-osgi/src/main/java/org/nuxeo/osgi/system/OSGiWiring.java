package org.nuxeo.osgi.system;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.nuxeo.common.collections.Streams;
import org.nuxeo.common.trycompanion.SneakyThrow;
import org.nuxeo.common.trycompanion.SneakyThrow.ConsumerCheckException;
import org.nuxeo.common.trycompanion.Try;
import org.nuxeo.common.trycompanion.TryCompanion;
import org.nuxeo.osgi.bootstrap.OSGiFile;
import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.nuxeo.osgi.system.OSGiRepository.Revision.TransientRepository;
import org.nuxeo.osgi.system.OSGiRepository.Revision.TransientRepository.Metaspace;
import org.nuxeo.osgi.system.OSGiWiring.Module.ModuleWiring.Namespace.Wires;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.resolver.ResolverHook;
import org.osgi.framework.hooks.resolver.ResolverHookFactory;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.service.repository.Repository;
import org.osgi.service.resolver.HostedCapability;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.ResolveContext;
import org.osgi.service.resolver.Resolver;

public class OSGiWiring {

    OSGiWiring(OSGiSystem system) {
        this.system = system;
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<OSGiWiring>() {

                    @Override
                    public Class<OSGiWiring> typeof() {
                        return OSGiWiring.class;
                    }

                    @Override
                    public OSGiWiring adapt(Bundle bundle) {
                        return OSGiWiring.this;
                    }

                });
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<Module>() {

                    @Override
                    public Class<Module> typeof() {
                        return Module.class;
                    }

                    @Override
                    public Module adapt(Bundle bundle) {
                        final Metaspace metaspace = bundle.adapt(Metaspace.class);
                        if (!byRevisions.containsKey(metaspace)) {
                            return new Module(metaspace);
                        }
                        return byRevisions.get(metaspace);
                    }

                });
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<Admin>() {

                    @Override
                    public Class<Admin> typeof() {
                        return Admin.class;
                    }

                    @Override
                    public Admin adapt(Bundle bundle) throws BundleException {
                        return admin;
                    }

                });
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<FrameworkWiring>() {

                    @Override
                    public Class<FrameworkWiring> typeof() {
                        return FrameworkWiring.class;
                    }

                    @Override
                    public FrameworkWiring adapt(Bundle bundle) throws BundleException {
                        return admin;
                    }

                });
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<Repository>() {

                    @Override
                    public Class<Repository> typeof() {
                        return Repository.class;
                    }

                    @Override
                    public Repository adapt(Bundle bundle) {
                        return byRevisions.get(bundle.adapt(BundleRevision.class)).repository;
                    }

                });
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<ResolveContext>() {

                    @Override
                    public Class<ResolveContext> typeof() {
                        return ResolveContext.class;
                    }

                    @Override
                    public ResolveContext adapt(Bundle bundle) throws BundleException {
                        return byRevisions.get(bundle.adapt(BundleRevision.class)).context;
                    }
                });
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<BundleWiring>() {

                    @Override
                    public Class<BundleWiring> typeof() {
                        return BundleWiring.class;
                    }

                    @Override
                    public BundleWiring adapt(Bundle bundle) {
                        return byRevisions.get(bundle.adapt(BundleRevision.class)).wiring;
                    }

                });
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<BundleContext>() {

                    @Override
                    public Class<BundleContext> typeof() {
                        return BundleContext.class;
                    }

                    @Override
                    public BundleContext adapt(Bundle bundle) {
                        return byRevisions.get(bundle.adapt(BundleRevision.class)).bundleContext;
                    }

                });

    }

    final OSGiSystem system;

    final Map<BundleRevision, Module> byRevisions = new HashMap<>();

    final Activator activator = new Activator();

    final Admin admin = new Admin();

    <T> T adapt(Class<T> typeof) {
        return typeof.cast(activator);
    }

    class Activator implements BundleActivator {

        ServiceRegistration<FrameworkWiring> registration;

        @Override
        public void start(BundleContext context) throws Exception {
            registration = context.registerService(FrameworkWiring.class, admin, null);
        }

        @Override
        public void stop(BundleContext context) throws Exception {
            registration.unregister();
        }
    }

    class Admin implements FrameworkWiring {
        @Override
        public Bundle getBundle() {
            return system.bundle;
        }

        @Override
        public boolean resolveBundles(Collection<Bundle> bundles) {
            return resolve(bundles.stream()
                    .map(bundle -> bundle.adapt(BundleRevision.class))
                    .collect(Collectors.toList()), Collections.emptyList()).asOption()
                            .map(success -> true)
                            .orElse(false);
        }

        Try<Void> resolve(Collection<Resource> mandatories, Collection<Resource> optionals) {
            return resolve(mandatories, optionals, resource -> ((BundleRevision) resource).getBundle()
                    .adapt(OSGiLifecycle.StateMachine.class)
                    .resolved());
        }

        @Override
        public void refreshBundles(Collection<Bundle> bundles, FrameworkListener... listeners) {
            class Worker {
                void relayError(Exception error) {
                    FrameworkEvent event = new FrameworkEvent(FrameworkEvent.ERROR, system.bundle, error);
                    for (FrameworkListener listener : listeners) {
                        listener.frameworkEvent(event);
                    }
                }

                Optional<Bundle> unresolve(Bundle bundle) {
                    Optional<Bundle> restart = (bundle.getState() & Bundle.ACTIVE) != 0
                            ? Optional.of(bundle)
                            : Optional.empty();
                    try {
                        bundle.adapt(OSGiLifecycle.Transitions.class)
                                .unresolve();
                    } catch (BundleException error) {
                        relayError(error);
                    }
                    return restart;
                }

                void restart(Bundle bundle) {
                    try {
                        bundle.start();
                    } catch (BundleException error) {
                        relayError(error);
                    }
                }
            }
            Worker worker = new Worker();
            dependencyClosureOf(bundles.stream())
                    .map(worker::unresolve)
                    .forEach(restart -> restart.ifPresent(worker::restart));
        }

        @Override
        public Collection<Bundle> getRemovalPendingBundles() {
            throw new UnsupportedOperationException();
        }

        Stream<Bundle> dependencyClosureOf(Stream<Bundle> bundles) {
            return bundles
                    .map(bundle -> bundle.adapt(BundleRevision.class))
                    .map(revision -> Optional.ofNullable(byRevisions.get(revision)))
                    .filter(module -> module.isPresent())
                    .flatMap(module -> module.get().wiring.walkProvides())
                    .map(BundleWiring::getResource)
                    .map(BundleRevision::getBundle)
                    .distinct();
        }

        @Override
        public Collection<Bundle> getDependencyClosure(Collection<Bundle> bundles) {
            return dependencyClosureOf(bundles.stream()).collect(Collectors.toList());
        }

        @Override
        public Collection<BundleCapability> findProviders(Requirement requirement) {
            throw new UnsupportedOperationException();
        }

        Try<Void> resolve(Collection<Resource> mandatories, Collection<Resource> optionals,
                ConsumerCheckException<Resource> callback) {
            ResolverHook hook = system.adapt(ResolverHookFactory.class)
                    .begin(Stream.concat(mandatories.stream(), optionals.stream())
                            .map(resource -> (BundleRevision) resource)
                            .collect(Collectors.toList()));
            try {
                return resolve(mandatories, optionals, hook, callback);
            } finally {
                hook.end();
            }
        }

        Try<Void> resolve(Collection<Resource> mandatories, Collection<Resource> optionals, ResolverHook hook,
                ConsumerCheckException<Resource> callback) {

            // get candidates
            Map<Requirement, Collection<Capability>> byRequirements = system.registry.systemRepository
                    .findProviders(Stream.concat(mandatories.stream(), optionals.stream())
                            .flatMap(resource -> resource.getRequirements(null)
                                    .stream())
                            .distinct()
                            .collect(Collectors.toList()));
            final Set<BundleRevision> candidates = byRequirements.values()
                    .stream()
                    .flatMap(each -> each.stream())
                    .map(capability -> (BundleRevision) capability.getResource())
                    .collect(Collectors.toSet());

            // then ask for filtering
            hook.filterResolvable(candidates);

            // try resolve
            return TryCompanion.<Void> of(ResolutionException.class)
                    .sneakyRun(() -> {
                        Context context = new Context(mandatories, optionals);
                        Map<Resource, List<Wire>> resolution = system.adapt(Resolver.class)
                                .resolve(context);
                        context.commit(resolution, callback);
                    });
        }

        class Context extends ResolveContext {

            final Collection<Resource> mandatories;

            final Collection<Resource> optionals;

            final Map<Requirement, List<Capability>> byRequirements = new HashMap<>();

            final Map<Resource, List<HostedCapability>> byHosts = new HashMap<>();

            final Map<Resource, Wiring> wirings;

            Context(Collection<Resource> mandatories, Collection<Resource> optionals) {
                this.mandatories = mandatories;
                this.optionals = optionals;
                wirings = byRevisions.values()
                        .stream()
                        .map(module -> module.wiring)
                        .filter(module -> !mandatories.contains(module.getResource()))
                        .filter(module -> !optionals.contains(module.getResource()))
                        .collect(Collectors.toMap(Wiring::getResource, Function.identity()));
            }

            void commit(Map<Resource, List<Wire>> resolution, ConsumerCheckException<Resource> callback)
                    throws BundleException {
                TryCompanion.<Void> of(BundleException.class)
                        .forEachAndCollect(resolution.keySet()
                                .stream()
                                .map(BundleRevision.class::cast),
                                revision -> {
                                    if (byRevisions.containsKey(revision)) {
                                        return;
                                    }
                                    byRevisions.put(revision, new Module(revision));
                                })
                        .peek(self -> resolution
                                .forEach((resource, wires) -> wires
                                        .forEach(wire -> byRevisions.get(resource).wiring.wire(
                                                wire.getRequirement(),
                                                wire.getCapability()))))
                        .sneakyForEachAndCollect(resolution.keySet()
                                .stream()
                                .map(BundleRevision.class::cast)
                                .map(byRevisions::get),
                                module -> module.commit(
                                        byHosts.getOrDefault(module.revision, Collections.emptyList())))
                        .orElseThrow(
                                () -> new BundleException("Cannot commit resolution", BundleException.RESOLVE_ERROR))
                        .sneakyForEachAndCollect(resolution.keySet()
                                .stream()
                                .map(BundleRevision.class::cast),
                                revision -> revision.getBundle()
                                        .adapt(OSGiLoader.Activation.class)
                                        .install())
                        .sneakyForEachAndCollect(resolution.keySet()
                                .stream(),
                                callback);
            }

            @Override
            public Collection<Resource> getMandatoryResources() {
                return mandatories;
            }

            @Override
            public Collection<Resource> getOptionalResources() {
                return optionals;
            }

            @SuppressWarnings({ "unchecked", "rawtypes" })
            @Override
            public List<Capability> findProviders(Requirement requirement) {
                return byRequirements.computeIfAbsent(requirement,
                        key -> (List) system.registry.systemRepository.findProviders(requirement));
            }

            @Override
            public int insertHostedCapability(List<Capability> capabilities, HostedCapability hosted) {
                byHosts.computeIfAbsent(hosted.getResource(), host -> new LinkedList<>())
                        .add(hosted);
                class Matcher {
                    int index;

                    boolean matched;

                    Capability replace(Capability source) {
                        if (matched) {
                            return source;
                        }
                        index += 1;
                        if (source != hosted.getDeclaredCapability()) {
                            return source;
                        }
                        matched = true;
                        return source;
                    }
                }
                Matcher matcher = new Matcher();
                capabilities.replaceAll(matcher::replace);
                return matcher.index;
            }

            @Override
            public boolean isEffective(Requirement requirement) {
                return true;
            }

            @Override
            public Map<Resource, Wiring> getWirings() {
                return wirings;
            }

        }
    }

    class Module {

        Module(BundleRevision revision) {
            this.revision = revision;
            bundle = revision.getBundle();
            file = bundle.adapt(OSGiFile.class);
            repository = bundle.adapt(TransientRepository.class);
        }

        final Bundle bundle;

        final BundleRevision revision;

        final TransientRepository repository;

        final OSGiFile file;

        final ModuleWiring wiring = new ModuleWiring();

        final ModuleBundleContext bundleContext = new ModuleBundleContext();

        final ModuleResolverContext context = new ModuleResolverContext();

        void install(ResolverHook hook, ConsumerCheckException<Resource> callback) throws ResolutionException {
            admin.resolve(Collections.singletonList(revision), Collections.emptyList(), hook, callback)
                    .orElseThrow(() -> new ResolutionException("Cannot resolve " + revision));
        }

        void uninstall() throws BundleException {
            try {
                unresolve();
            } finally {
                byRevisions.remove(revision);
            }
        }

        void commit(List<HostedCapability> hosted) throws BundleException {
            repository.index(revision.getCapabilities(null).stream());
            repository.index(hosted.stream());
            // index revision direct acyclic graph in repository
            TryCompanion.<Void> of(BundleException.class)
                    .sneakyForEachAndCollect(Stream
                            .concat(wiring.walkRequires(),
                                    wiring.walkFragments()
                                            .flatMap(fragment -> fragment
                                                    .walkRequires()
                                                    .filter(wiring -> wiring != fragment)))
                            .distinct(),
                            wiring -> repository.index(wiring
                                    .getCapabilities(null)
                                    .stream()))
                    .orElseThrow(() -> new BundleException("Cannot index " + bundle + " capabilities"));
            repository.commit();
            context.commit();
        }

        void unresolve() throws BundleException {
            TryCompanion.<Void> of(BundleException.class)
                    .sneakyForEachAndCollect(wiring.walkProvides()
                            .map(wiring -> wiring.outer().bundle),
                            bundle -> bundle.adapt(OSGiLifecycle.Transitions.class)
                                    .unresolve());
        }

        @Override
        public String toString() {
            return new StringBuilder() // @formatter:off
                    .append("Module@").append(Integer.toHexString(hashCode()))
                    .append("[")
                    .append("bundle=").append(bundle)
                    .append("]").toString();
        } // @formatter:on

        class ModuleBundleContext implements BundleContext {

            @Override
            public Bundle installBundle(String location) throws BundleException {
                return system.installBundle(location);
            }

            @Override
            public Bundle installBundle(String location, InputStream input) throws BundleException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void addBundleListener(BundleListener listener) {
                bundle.adapt(OSGiEventRelayer.Activation.class)
                        .add(BundleListener.class, listener);
            }

            @Override
            public void addFrameworkListener(FrameworkListener listener) {
                bundle.adapt(OSGiEventRelayer.Activation.class)
                        .add(FrameworkListener.class, listener);
            }

            @Override
            public void addServiceListener(ServiceListener listener) {
                bundle.adapt(OSGiEventRelayer.Activation.class)
                        .add(ServiceListener.class, listener);
            }

            @Override
            public void addServiceListener(ServiceListener listener, String filter) throws InvalidSyntaxException {
                class FilterRelayer implements ServiceListener {

                    FilterRelayer(Filter filter, ServiceListener listener) {
                        this.filter = filter;
                        this.listener = listener;
                    }

                    final Filter filter;

                    final ServiceListener listener;

                    @Override
                    public void serviceChanged(ServiceEvent event) {
                        if (!filter.match(event.getServiceReference())) {
                            return;
                        }
                        listener.serviceChanged(event);
                    }
                }
                bundle.adapt(OSGiEventRelayer.Activation.class)
                        .add(ServiceListener.class, new FilterRelayer(FrameworkUtil.createFilter(filter), listener));
            }

            @Override
            public void removeBundleListener(BundleListener listener) {
                bundle.adapt(OSGiEventRelayer.Activation.class)
                        .remove(BundleListener.class, listener);
            }

            @Override
            public void removeFrameworkListener(FrameworkListener listener) {
                bundle.adapt(OSGiEventRelayer.Activation.class)
                        .remove(FrameworkListener.class, listener);
            }

            @Override
            public void removeServiceListener(ServiceListener listener) {
                bundle.adapt(OSGiEventRelayer.Activation.class)
                        .remove(ServiceListener.class, listener);
            }

            @Override
            public Filter createFilter(String filter) throws InvalidSyntaxException {
                return Optional.ofNullable(filter)
                        .map(SneakyThrow.sneakyFunction(FrameworkUtil::createFilter))
                        .orElse(OSGiRepository.OpenFilter.OPEN);
            }

            @Override
            public Bundle getBundle() {
                return bundle;
            }

            @Override
            public Bundle getBundle(long id) {
                return bundle.adapt(OSGiRepository.Revision.class)
                        .byId(id);
            }

            @Override
            public Bundle[] getBundles() {
                return bundle.adapt(OSGiRepository.Revision.class)
                        .all();
            }

            @Override
            public File getDataFile(String filepath) {
                return bundle.adapt(OSGiDataFile.Activation.class)
                        .resolve(filepath);
            }

            @Override
            public String getProperty(String key) {
                return bundle.adapt(Properties.class)
                        .getProperty(key);
            }

            @Override
            public Bundle getBundle(String location) {
                return bundle.adapt(OSGiRepository.Revision.class)
                        .byLocation(URI.create(location));
            }

            @Override
            public ServiceRegistration<?> registerService(String[] clazzes, Object service,
                    Dictionary<String, ?> properties) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <S> ServiceRegistration<S> registerService(Class<S> type, S instance,
                    Dictionary<String, ?> properties) {
                return system.adapt(OSGiService.Activation.class)
                        .register(this, type, instance, properties);
            }

            @Override
            public <S> ServiceRegistration<S> registerService(Class<S> type, ServiceFactory<S> factory,
                    Dictionary<String, ?> properties) {
                return system.adapt(OSGiService.Activation.class)
                        .register(this, type, factory, properties);
            }

            @SuppressWarnings("unchecked")
            @Override
            public ServiceRegistration<?> registerService(String classname, Object service,
                    Dictionary<String, ?> properties) {
                Class<Object> clazz;
                try {
                    clazz = (Class<Object>) bundle.loadClass(classname);
                } catch (ClassNotFoundException e) {
                    throw new UnsupportedOperationException();
                }
                return registerService(clazz, service, properties);
            }

            @Override
            public ServiceReference<?> getServiceReference(String classname) {
                Class<?> type;
                try {
                    type = bundle.loadClass(classname);
                } catch (ClassNotFoundException e) {
                    return null;
                }
                return getServiceReference(type);
            }

            @Override
            public ServiceReference<?>[] getServiceReferences(String classname, String filter)
                    throws InvalidSyntaxException {
                @SuppressWarnings("rawtypes")
                Class type;
                try {
                    type = bundle.loadClass(classname);
                } catch (ClassNotFoundException e) {
                    return new ServiceReference<?>[0];
                }
                @SuppressWarnings("unchecked")
                Collection<ServiceReference<?>> refs = getServiceReferences(type, filter);
                return refs.toArray(new ServiceReference<?>[refs.size()]);
            }

            @Override
            public <S> ServiceReference<S> getServiceReference(Class<S> clazz) {
                Enumeration<ServiceReference<S>> refs = bundle.adapt(OSGiService.Activation.class)
                        .resolve(clazz, OSGiRepository.OpenFilter.OPEN);
                if (refs.hasMoreElements()) {
                    return refs.nextElement();
                }
                return null;
            }

            @Override
            public ServiceReference<?>[] getAllServiceReferences(String clazz, String filter)
                    throws InvalidSyntaxException {
                throw new UnsupportedOperationException();
            }

            @Override
            public <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> clazz, String filter)
                    throws InvalidSyntaxException {
                return Collections.list(bundle.adapt(OSGiService.Activation.class)
                        .resolve(clazz, createFilter(filter)));
            }

            @Override
            public <S> S getService(ServiceReference<S> reference) {
                return ((OSGiService.Registration<S>.Reference) reference).getService(bundle);
            }

            @SuppressWarnings("rawtypes")
            @Override
            public boolean ungetService(ServiceReference<?> reference) {
                return ((OSGiService.Registration.Reference) reference).ungetService(bundle);
            }

            @Override
            public <S> ServiceObjects<S> getServiceObjects(ServiceReference<S> reference) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String toString() {
                return new StringBuilder().append("OSGiContext@")
                        .append(Integer.toHexString(hashCode()))
                        .append("[")
                        .append(bundle)
                        .append("]")
                        .toString();
            }
        }

        class ModuleResolverContext extends ResolveContext {

            Module outer() {
                return Module.this;
            }

            void commit() {
            }

            @Override
            public Collection<Resource> getMandatoryResources() {
                return Collections.singleton(revision);
            }

            @Override
            public List<Capability> findProviders(Requirement requirement) {
                Stream<Resource> requires = repository.content.keySet()
                        .stream();
                Stream<Resource> provides = wiring.provides(null)
                        .map(BundleWire::getRequirer)
                        .map(Resource.class::cast)
                        .distinct();
                return Stream.concat(requires, provides)
                        .map(resource -> byRevisions.get(resource).wiring)
                        .map(wiring -> byRevisions.get(wiring.getResource()))
                        .filter(module -> module != null)
                        .map(module -> module.repository)
                        .flatMap(repository -> repository.findProviders(Collections.singletonList(requirement))
                                .get(requirement)
                                .stream())
                        .collect(Collectors.toList());
            }

            @Override
            public int insertHostedCapability(List<Capability> capabilities, HostedCapability hostedCapability) {
                repository.index(hostedCapability.getNamespace(), Stream.of(hostedCapability));
                return 0;
            }

            @Override
            public boolean isEffective(Requirement requirement) {
                return true;
            }

            @Override
            public Map<Resource, Wiring> getWirings() {
                return repository.content.values()
                        .stream()
                        .collect(Collectors.toMap(Function.identity(), resource -> byRevisions.get(resource).wiring));
            }

            @Override
            public String toString() {
                return new StringBuilder() // @formatter:off
                        .append("Module Context@").append(Integer.toHexString(hashCode()))
                        .append("[")
                        .append(bundle)
                        .append("]")
                        .toString();
            } // @formatter:on
        }

        class ModuleWiring implements org.osgi.framework.wiring.BundleWiring {

            final Namespace emptyNamespace = new Namespace("empty");

            final Map<String, Namespace> byNamespaces = new HashMap<>();

            Module outer() {
                return Module.this;
            }

            BundleWire wire(Requirement requirement, Capability capability) {
                Module requirer = byRevisions.get(requirement.getResource());
                Module provider = byRevisions.get(capability.getResource());
                if (!(requirement instanceof BundleRequirement)) {
                    requirement = repository.newRequirement(requirer.revision, requirement.getNamespace(),
                            requirement.getAttributes(), requirement.getDirectives());
                }
                NamespaceWire wire = new NamespaceWire((BundleRequirement) requirement, (BundleCapability) capability);
                requirer.wiring.byNamespaces.computeIfAbsent(requirement.getNamespace(),
                        name -> new Namespace(name)).requires.add(wire);
                provider.wiring.byNamespaces.computeIfAbsent(capability.getNamespace(),
                        name -> new Namespace(name)).provides.add(wire);
                return wire;
            }

            Stream<ModuleWiring> walkRequires() {
                return new Walker(namespace -> namespace.requires).run();
            }

            Stream<ModuleWiring> walkProvides() {
                return new Walker(namespace -> namespace.provides).run();
            }

            Stream<ModuleWiring> walkFragments() {
                return getProvidedWires(HostNamespace.HOST_NAMESPACE).stream()
                        .map(wire -> (ModuleWiring) wire.getRequirerWiring())
                        .flatMap(fragment -> Stream.concat(Stream.of(fragment), fragment.walkFragments()));
            }

            Stream<NamespaceWire> wires(Optional<String> name, Function<Namespace, Wires> extractor) {
                return name.map(value -> byNamespaces.getOrDefault(value, emptyNamespace))
                        .map(extractor)
                        .map(wires -> wires.wires.stream())
                        .orElseGet(() -> byNamespaces.values()
                                .stream()
                                .flatMap(namespace -> namespace.provides.wires.stream()));
            }

            Stream<NamespaceWire> provides(String name) {
                return wires(Optional.ofNullable(name), namespace -> namespace.provides);
            }

            Stream<NamespaceWire> requires(String name) {
                return wires(Optional.ofNullable(name), namespace -> namespace.requires);
            }

            @Override
            public Bundle getBundle() {
                return bundle;
            }

            @Override
            public boolean isCurrent() {
                return true;
            }

            @Override
            public boolean isInUse() {
                return wires(Optional.empty(), namespace -> namespace.provides).findAny()
                        .isPresent();
            }

            @Override
            public List<BundleCapability> getCapabilities(String namespace) {
                return provides(namespace).map(BundleWire::getCapability)
                        .distinct()
                        .collect(Collectors.toList());
            }

            @Override
            public List<BundleRequirement> getRequirements(String namespace) {
                return requires(namespace).map(BundleWire::getRequirement)
                        .collect(Collectors.toList());
            }

            @Override
            public List<BundleWire> getProvidedWires(String namespace) {
                return provides(namespace).collect(Collectors.toList());
            }

            @Override
            public List<BundleWire> getRequiredWires(String namespace) {
                return requires(namespace).collect(Collectors.toList());
            }

            @Override
            public BundleRevision getRevision() {
                return bundle.adapt(BundleRevision.class);
            }

            @Override
            public ClassLoader getClassLoader() {
                return bundle.adapt(ClassLoader.class);
            }

            @Override
            public List<URL> findEntries(String path, String filePattern, int options) {
                boolean recurse = (options & BundleWiring.FINDENTRIES_RECURSE) != 0;
                return Stream.concat(walkRequires(), walkFragments())
                        .distinct()
                        .map(wiring -> wiring.outer().bundle)
                        .map(bundle -> bundle.findEntries(path, filePattern, recurse))
                        .flatMap(Streams::of)
                        .collect(Collectors.toList());
            }

            @Override
            public Collection<String> listResources(String path, String filePattern, int options) {
                throw new UnsupportedOperationException();
            }

            @SuppressWarnings({ "unchecked", "rawtypes" })
            @Override
            public List<Capability> getResourceCapabilities(String namespace) {
                return (List) getCapabilities(namespace);
            }

            @SuppressWarnings({ "unchecked", "rawtypes" })
            @Override
            public List<Requirement> getResourceRequirements(String namespace) {
                return (List) getRequirements(namespace);
            }

            @SuppressWarnings({ "unchecked", "rawtypes" })
            @Override
            public List<org.osgi.resource.Wire> getProvidedResourceWires(String namespace) {
                return (List) getProvidedWires(namespace);
            }

            @SuppressWarnings({ "rawtypes", "unchecked" })
            @Override
            public List<org.osgi.resource.Wire> getRequiredResourceWires(String namespace) {
                return (List) getRequiredWires(namespace);
            }

            @Override
            public BundleRevision getResource() {
                return bundle.adapt(BundleRevision.class);
            }

            @Override
            public String toString() {
                return new StringBuilder() // @formatter:off
                        .append("Wiring@").append(Integer.toHexString(hashCode()))
                        .append("[")
                        .append(bundle)
                        .append("]").toString();
            } // @formatter:on

            class Namespace extends org.osgi.service.resolver.ResolveContext {

                final String namespace;

                final Wires requires = new Wires(wire -> wire.getCapability()
                        .getRevision());

                final Wires provides = new Wires(wire -> wire.getRequirement()
                        .getRevision());

                Namespace(String namespace) {
                    this.namespace = namespace;
                }

                Module wiring() {
                    return Module.this;
                }

                @Override
                public List<Capability> findProviders(Requirement requirement) {
                    return provides.byNames.multimap.getOrDefault(requirement.getAttributes()
                            .get(namespace), Collections.emptyList())
                            .stream()
                            .map(wire -> wire.capability)
                            .collect(Collectors.toList());
                }

                @Override
                public int insertHostedCapability(List<Capability> capabilities, HostedCapability hostedCapability) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean isEffective(Requirement requirement) {
                    return true;
                }

                @SuppressWarnings({ "rawtypes", "unchecked" })
                @Override
                public Map<Resource, Wiring> getWirings() {
                    return (Map) requires.byWirings.multimap;
                }

                class Wires {

                    Wires(Function<NamespaceWire, BundleRevision> classifier) {
                        byWirings = new Multimap<>(classifier.andThen(revision -> (ModuleWiring) revision.getWiring()),
                                wiring -> new LinkedList<>());
                        byNames = new Multimap<>(wire -> wire.name, name -> new LinkedList<>());
                    }

                    final Set<NamespaceWire> wires = new HashSet<>();

                    final Multimap<String, NamespaceWire> byNames;

                    final Multimap<ModuleWiring, NamespaceWire> byWirings;

                    void add(NamespaceWire wire) {
                        wires.add(wire);
                        byNames.add(wire);
                        byWirings.add(wire);
                    }

                    void remove(NamespaceWire wire) {
                        wires.remove(wire);
                        byNames.remove(wire);
                        byWirings.remove(wire);
                    }

                    class Multimap<K, E> {
                        final Function<E, K> keymapper;

                        final Function<K, Collection<E>> supplier;

                        final Map<K, Collection<E>> multimap = new HashMap<K, Collection<E>>();

                        Multimap(Function<E, K> keymapper, Function<K, Collection<E>> supplier) {
                            this.supplier = supplier;
                            this.keymapper = keymapper;
                        }

                        void add(E e) {
                            final K k = keymapper.apply(e);
                            multimap.computeIfAbsent(k, supplier)
                                    .add(e);
                        }

                        void remove(E e) {
                            final K k = keymapper.apply(e);
                            final Collection<E> map = multimap.get(k);
                            map.remove(e);
                            if (map.isEmpty()) {
                                map.remove(k);
                            }

                        }
                    }

                }

            }

            class NamespaceWire implements BundleWire {

                final String name;

                final BundleRequirement requirement;

                final BundleCapability capability;

                NamespaceWire(BundleRequirement requirement, BundleCapability capability) {
                    this.requirement = requirement;
                    this.capability = capability;
                    name = (String) requirement.getAttributes()
                            .get(requirement.getNamespace());
                }

                @Override
                public BundleCapability getCapability() {
                    return capability;
                }

                @Override
                public BundleRequirement getRequirement() {
                    return requirement;
                }

                @Override
                public BundleWiring getProviderWiring() {
                    return capability.getRevision()
                            .getWiring();
                }

                @Override
                public BundleWiring getRequirerWiring() {
                    return requirement.getRevision()
                            .getWiring();
                }

                @Override
                public BundleRevision getProvider() {
                    return capability.getRevision();
                }

                @Override
                public BundleRevision getRequirer() {
                    return requirement.getRevision();
                }

                @Override
                public String toString() { // @formatter:off
          return new StringBuilder()
          .append("Wire@").append(Integer.toHexString(hashCode()))
          .append("[")
          .append(requirement).append(",")
          .append(capability)
          .append("]").toString();
        } // @formatter:on

            }

            class Walker {

                final Function<Namespace, Namespace.Wires> navigator;

                final Set<ModuleWiring> navigated = new HashSet<>();

                Walker(Function<Namespace, Namespace.Wires> navigator) {
                    super();
                    this.navigator = navigator;
                }

                Stream<ModuleWiring> run() {
                    return run(ModuleWiring.this);
                }

                Stream<ModuleWiring> run(ModuleWiring actual) {
                    navigated.add(actual);
                    Stream<ModuleWiring> others = wiring.byNamespaces.values()
                            .stream()
                            .map(namespace -> navigator.apply(namespace))
                            .flatMap(wires -> wires.byWirings.multimap.keySet()
                                    .stream())
                            .distinct();
                    List<ModuleWiring> wirings = others.collect(Collectors.toList());
                    return Stream.concat(Stream.of(actual), wirings.stream()
                            .filter(wiring -> !navigated.contains(wiring))
                            .flatMap(wiring -> run(wiring)));
                }

            }

        }

    }

}
