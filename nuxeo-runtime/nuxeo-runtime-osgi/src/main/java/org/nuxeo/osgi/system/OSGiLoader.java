package org.nuxeo.osgi.system;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.nuxeo.common.trycompanion.SneakyThrow;
import org.nuxeo.osgi.bootstrap.OSGiClassLoader;
import org.nuxeo.osgi.bootstrap.OSGiFile;
import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.nuxeo.osgi.system.OSGiLoader.Activation.ClassnameRequirement;
import org.nuxeo.osgi.system.OSGiLoader.Activation.PathnameRequiremment;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.resource.Capability;
import org.osgi.resource.Resource;
import org.osgi.service.resolver.HostedCapability;
import org.osgi.service.resolver.ResolveContext;

public class OSGiLoader {

    final OSGiSystem system;

    final Map<BundleRevision, Activation> byRevisions = new HashMap<>();

    OSGiLoader(OSGiSystem osgi) throws BundleException {
        system = osgi;
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<Activation>() {

                    @Override
                    public Class<Activation> typeof() {
                        return Activation.class;
                    }

                    @Override
                    public Activation adapt(Bundle bundle) throws BundleException {
                        BundleRevision revision = hostof(bundle.adapt(BundleRevision.class));
                        if (!byRevisions.containsKey(revision)) {
                            return new Activation(revision);
                        }
                        return byRevisions.get(revision);
                    }

                });
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<OSGiClassLoader>() {

                    @Override
                    public Class<OSGiClassLoader> typeof() {
                        return OSGiClassLoader.class;
                    }

                    @Override
                    public OSGiClassLoader adapt(Bundle bundle) {
                        if (bundle == system.bundle) {
                            return system.bootstrap.loader();
                        }
                        return bundle.adapt(Activation.class).loader;
                    }

                });
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<ClassLoader>() {

                    @Override
                    public Class<ClassLoader> typeof() {
                        return ClassLoader.class;
                    }

                    @Override
                    public ClassLoader adapt(Bundle bundle) {
                        return bundle.adapt(Activation.class).loader;
                    }

                });
    }

    @Override
    public String toString() {
        return "OSGiLoader@" + Integer.toHexString(hashCode()) + byRevisions;
    }

    interface Strategy {
        Optional<Class<?>> findClass(ClassnameRequirement requirement);

        Optional<Path> findResource(PathnameRequiremment requirement);

        Stream<Path> findResources(PathnameRequiremment requirement);
    }

    class Activation {

        Activation(BundleRevision revision) {
            this.revision = revision;
            bundle = revision.getBundle();
            wiring = bundle.adapt(BundleWiring.class);
            resolve = bundle.adapt(ResolveContext.class);
            revisions = revisionsOf(revision).collect(Collectors.toList());
            file = bundle.adapt(OSGiFile.class);
            loader = selectLoader();
            bundleStrategy = new BundleStrategy();
            siblingStrategy = new ResolveStrategy(bundle.adapt(ResolveContext.class));
            systemStrategy = new ResolveStrategy(system.adapt(ResolveContext.class));
            dynamicStrategy = selectStrategy();
            chainedStrategy = new ChainedStrategy(bundleStrategy, siblingStrategy, dynamicStrategy);
        }

        final Bundle bundle;

        final OSGiFile file;

        final BundleRevision revision;

        final BundleWiring wiring;

        final ResolveContext resolve;

        final List<BundleRevision> revisions;

        final Strategy bundleStrategy;

        final Strategy siblingStrategy;

        final Strategy systemStrategy;

        final Strategy dynamicStrategy;

        final Strategy chainedStrategy;

        final Context context = new Context();

        OSGiClassLoader loader = selectLoader();

        Stream<BundleRevision> revisionsOf(BundleRevision revision) {
            return Stream.concat(
                    Stream.of(revision),
                    revision.getWiring()
                            .getProvidedWires(HostNamespace.HOST_NAMESPACE)
                            .stream()
                            .map(BundleWire::getRequirer)
                            .flatMap(this::revisionsOf));
        }

        OSGiClassLoader selectLoader() {
            if (bundle == system.bundle) {
                return system.bootstrap.loader();
            }
            return new OSGiClassLoader(system.bootstrap, system.bootstrap.loader(), context);
        }

        Strategy selectStrategy() {
            if (bundle.getHeaders()
                    .get(Constants.DYNAMICIMPORT_PACKAGE) != null) {
                return new DynamicStrategy();
            }
            return wiring
                    .getProvidedWires(HostNamespace.HOST_NAMESPACE)
                    .stream()
                    .map(wire -> wire.getProvider()
                            .getBundle())
                    .filter(fragment -> fragment.getHeaders()
                            .get(Constants.DYNAMICIMPORT_PACKAGE) != null)
                    .findFirst()
                    .map(bundle -> (Strategy) new DynamicStrategy())
                    .orElseGet(() -> new NullStrategy());
        }

        void install() {
            if (byRevisions.containsKey(bundle)) {
                return;
            }
            byRevisions.put(revision, this);
        }

        void reset() {
            if (bundle == system.bundle) {
                return;
            }
            try {
                loader.close();
            } finally {
                loader = selectLoader();
            }
        }

        void uninstall() throws BundleException {
            byRevisions.remove(revision);
            try {
                loader.close();
            } finally {
                loader = null;
            }
        }

        Class<?> loadClass(String classname, Path path) throws ClassNotFoundException {
            system.activator.classLoading(bundle, classname);
            try {
                try {
                    return loader.defineClass(classname, path);
                } finally {
                    system.activator.classLoaded(bundle, classname);
                }
            } catch (IOException | BundleException cause) {
                throw new ClassNotFoundException("Cannot load class " + classname + " from " + bundle, cause);
            }
        }

        class Context implements OSGiClassLoader.Context {
            @Override
            public Bundle getBundle() {
                return bundle;
            }

            @Override
            public Class<?> findWiredClass(String classname) throws BundleException, IOException {
                return chainedStrategy.findClass(new ClassnameRequirement(classname))
                        .orElseThrow(() -> new BundleException("Cannot find " + classname + " in " + bundle,
                                BundleException.RESOLVE_ERROR,
                                null));
            }

            @Override
            public URL getWiredResource(String pathname) throws BundleException {
                return chainedStrategy
                        .findResource(new PathnameRequiremment(pathname))
                        .map(path -> OSGiFile.toURL.apply(path))
                        .orElse(null);
            }

            @Override
            public Stream<URL> findWiredResources(String pathname) throws BundleException, IOException {
                return chainedStrategy.findResources(new PathnameRequiremment(pathname))
                        .map(OSGiFile.toURL);
            }

        }

        @Override
        public String toString() {
            return "OSGiLoader@" + Integer.toHexString(hashCode()) + "[" + bundle.getSymbolicName() + "]";
        }

        class PathnameRequiremment implements BundleRequirement {

            final String pathname;

            final String pkgname;

            final Map<String, Object> attributes = new HashMap<>();

            PathnameRequiremment(String pathname) {
                this.pathname = pathname;
                pkgname = pkgname(pathname);
                attributes.put(PackageNamespace.PACKAGE_NAMESPACE, pkgname);
            }

            String pkgname(String pathname) {
                int startindex = pathname.charAt(0) == '/' ? 1 : 0;
                int lastindex = pathname.lastIndexOf('/');
                return (lastindex > 0 ? pathname.substring(startindex, lastindex) : "").replace('/', '.');
            }

            @Override
            public BundleRevision getRevision() {
                return revision;
            }

            @Override
            public boolean matches(BundleCapability capability) {
                boolean ismatch = capability
                        .getAttributes()
                        .get(PackageNamespace.PACKAGE_NAMESPACE)
                        .equals(pkgname);
                return ismatch;
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
                return Collections.unmodifiableMap(attributes);
            }

            @Override
            public BundleRevision getResource() {
                return revision;
            }

        }

        class ClassnameRequirement extends PathnameRequiremment {

            final String classname;

            ClassnameRequirement(String classname) {
                super(pathname(classname));
                this.classname = classname;
            }

            @Override
            public String toString() { // @formatter:off
        return new StringBuilder()
        .append("Class Requirement@").append(Integer.toHexString(hashCode()))
        .append("[")
        .append(classname).append(",")
        .append(getResource())
        .append("]").toString();
      } // @formatter:on
        }

        protected URL getBootResource(String path) {
            return loader.getBootResource(path);
        }

        protected String pathname(String classname) {
            return "/".concat(classname.replace('.', '/')
                    .concat(".class"));
        }

        class ChainedStrategy implements Strategy {
            final Collection<Strategy> chain;

            ChainedStrategy(Strategy... strategies) {
                chain = Arrays.asList(strategies);
            }

            @Override
            public Optional<Class<?>> findClass(ClassnameRequirement requirement) {
                return chain.stream()
                        .map(strategy -> strategy.findClass(requirement))
                        .filter(option -> option.isPresent())
                        .findFirst()
                        .orElse(Optional.empty());
            }

            @Override
            public Optional<Path> findResource(PathnameRequiremment requirement) {
                return chain.stream()
                        .map(strategy -> strategy.findResource(requirement))
                        .filter(option -> option.isPresent())
                        .findFirst()
                        .orElse(Optional.empty());
            }

            @Override
            public Stream<Path> findResources(PathnameRequiremment requirement) {
                return chain.stream()
                        .map(SneakyThrow.sneakyFunction(strategy -> strategy.findResources(requirement)))
                        .reduce(Stream::concat)
                        .orElse(Stream.empty());
            }
        }

        class BundleStrategy implements Strategy {

            final ResolveContext context = bundle.adapt(ResolveContext.class);

            @Override
            public Optional<Class<?>> findClass(ClassnameRequirement requirement) {
                return findResource(requirement)
                        .map(SneakyThrow.<Path, Class<?>> sneakyFunction(path -> loadClass(
                                requirement.classname,
                                path)));
            }

            @Override
            public Optional<Path> findResource(PathnameRequiremment requirement) {
                return findResources(requirement)
                        .findFirst();
            }

            @Override
            public Stream<Path> findResources(PathnameRequiremment requirement) {
                return revisions.stream()
                        .map(revision -> getEntry(requirement, revision))
                        .filter(pathoption -> pathoption.isPresent())
                        .map(pathoption -> pathoption.get());
            }

            Optional<Path> getEntry(PathnameRequiremment requirement, BundleRevision revision) {
                return Optional.ofNullable(revision
                        .getBundle()
                        .adapt(OSGiFile.class)
                        .getEntry(requirement.pathname));
            }

            @Override
            public String toString() {
                return new StringBuilder() // @formatter:off
            .append("Bundle Strategy@").append(Integer.toHexString(hashCode()))
            .append("[")
            .append(bundle)
            .append("]").toString(); // @formatter:on
            }

        }

        class ResolveStrategy implements Strategy {

            final ResolveContext context;

            ResolveStrategy(ResolveContext context) {
                this.context = context;
            }

            @Override
            public Optional<Class<?>> findClass(ClassnameRequirement requirement) {
                return context.findProviders(requirement)
                        .stream()
                        .filter(capability -> capability.getResource() != revision)
                        .map(capability -> loadClass((BundleCapability) capability, requirement))
                        .filter(result -> result.isPresent())
                        .findFirst()
                        .orElse(Optional.empty());
            }

            @Override
            public Optional<Path> findResource(PathnameRequiremment requirement) {
                return findResources(requirement).findFirst();
            }

            @Override
            public Stream<Path> findResources(PathnameRequiremment requirement) {
                Set<Resource> keySet = context
                        .getWirings()
                        .keySet();
                return keySet
                        .stream()
                        .map(resource -> hostof((BundleRevision) resource))
                        .filter(host -> host != revision)
                        .distinct()
                        .map(byRevisions::get)
                        .flatMap(activation -> activation.bundleStrategy.findResources(requirement))
                        .distinct();
            }

            protected Optional<Class<?>> loadClass(BundleCapability capability, ClassnameRequirement requirement) {
                BundleRevision host = hostof(capability.getRevision()); // needed only for dynamic (still testing)
                Activation activation = byRevisions.get(host);
                if (activation == null) {
                    throw new AssertionError("no activation for " + capability);
                }
                return activation.bundleStrategy.findClass(requirement);
            }

            protected Optional<Path> getEntry(BundleCapability capability, PathnameRequiremment requirement) {
                return Optional.ofNullable(capability
                        .getResource()
                        .getBundle()
                        .adapt(OSGiFile.class)
                        .getEntry(requirement.pathname));
            }

            @Override
            public String toString() { // @formatter:off
                 return new StringBuilder()
                 .append("Resolve Strategy@").append(Integer.toHexString(hashCode()))
                 .append("[")
                 .append(context)
                 .append("]").toString();
            } // @formatter:on
        }

        class DynamicStrategy extends ResolveStrategy {

            public DynamicStrategy() {
                super(system.adapt(ResolveContext.class));
            }

            @Override
            public Optional<Class<?>> findClass(ClassnameRequirement requirement) {
                // trigger resolution if needed
                List<Bundle> bundles = system.registry.systemRepository
                        .findProviders(Collections.singletonList(requirement))
                        .get(requirement)
                        .stream()
                        .map(Capability::getResource)
                        .map(BundleRevision.class::cast)
                        .map(BundleRevision::getBundle)
                        .filter(bundle -> (bundle.getState() & Bundle.RESOLVED) == 0)
                        .collect(Collectors.toList());
                if (!bundles.isEmpty()) {
                    system.adapt(FrameworkWiring.class)
                            .resolveBundles(bundles);
                }
                return super.findClass(requirement);
            }

            @Override
            protected Optional<Class<?>> loadClass(BundleCapability capability, ClassnameRequirement requirement) {
                Optional<Class<?>> result = super.loadClass(capability, requirement);
                result.ifPresent(clazz -> resolve
                        .insertHostedCapability(
                                resolve.findProviders(requirement),
                                new DynamicCapability(capability)));
                return result;
            }

            class DynamicCapability implements HostedCapability {

                BundleCapability other;

                public DynamicCapability(BundleCapability other) {
                    this.other = other;
                }

                @Override
                public String getNamespace() {
                    return other.getNamespace();
                }

                @Override
                public Map<String, String> getDirectives() {
                    return other.getDirectives();
                }

                @Override
                public Map<String, Object> getAttributes() {
                    return other.getAttributes();
                }

                @Override
                public Resource getResource() {
                    return other.getResource();
                }

                @Override
                public Capability getDeclaredCapability() {
                    throw new UnsupportedOperationException();
                }

            }

            @Override
            public String toString() {
                return new StringBuilder() // @formatter:off
            .append("Dynamic Strategy@").append(Integer.toHexString(hashCode()))
            .append("[")
            .append(context)
            .append("]").toString(); // @formatter:on
            }

        }

        class NullStrategy implements Strategy {

            @Override
            public Optional<Class<?>> findClass(ClassnameRequirement requirement) {
                return Optional.empty();
            }

            @Override
            public Optional<Path> findResource(PathnameRequiremment requirement) {
                return Optional.empty();
            }

            @Override
            public Stream<Path> findResources(PathnameRequiremment requirement) {
                return Stream.empty();
            }

            @Override
            public String toString() {
                return new StringBuilder()
                        .append("Null Strategy@")
                        .append(Integer.toHexString(hashCode()))
                        .toString();
            }
        }

    }

    BundleRevision hostof(BundleRevision revision) {
        return revision
                .getWiring()
                .getRequiredWires(HostNamespace.HOST_NAMESPACE)
                .stream()
                .map(wire -> wire.getProvider())
                .map(this::hostof)
                .findFirst()
                .orElse(revision);
    }
}
