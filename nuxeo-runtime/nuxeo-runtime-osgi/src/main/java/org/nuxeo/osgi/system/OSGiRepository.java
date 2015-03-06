/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Nuxeo - initial API and implementation
 * $Id$
 */

package org.nuxeo.osgi.system;

import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.nuxeo.common.trycompanion.SneakyThrow;
import org.nuxeo.common.trycompanion.SneakyThrow.ConsumerCheckException;
import org.nuxeo.common.trycompanion.Try;
import org.nuxeo.common.trycompanion.TryCompanion;
import org.nuxeo.osgi.bootstrap.OSGiFile;
import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.nuxeo.osgi.system.OSGiRepository.Revision.TransientRepository;
import org.nuxeo.osgi.system.OSGiRepository.Revision.TransientRepository.Metaspace;
import org.nuxeo.osgi.system.OSGiRepository.Revision.TransientRepository.Metaspace.Element.Component;
import org.nuxeo.osgi.system.OSGiRepository.Revision.TransientRepository.RepositoryNamespace.RepositoryCapability;
import org.nuxeo.osgi.system.OSGiRepository.Revision.TransientRepository.RepositoryNamespace.RepositoryRequirement;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.repository.ExpressionCombiner;
import org.osgi.service.repository.IdentityExpression;
import org.osgi.service.repository.Repository;
import org.osgi.service.repository.RequirementBuilder;
import org.osgi.service.repository.RequirementExpression;
import org.osgi.util.promise.Promise;

public class OSGiRepository {

    OSGiRepository(OSGiSystem system) {
        this.system = system;
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<OSGiRepository>() {

                    @Override
                    public Class<OSGiRepository> typeof() {
                        return OSGiRepository.class;
                    }

                    @Override
                    public OSGiRepository adapt(Bundle bundle) {
                        return OSGiRepository.this;
                    }

                });
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<Revision>() {

                    @Override
                    public Class<Revision> typeof() {
                        return Revision.class;
                    }

                    @Override
                    public Revision adapt(Bundle bundle) {
                        if (byBundles.containsKey(bundle)) {
                            return byBundles.get(bundle);
                        }
                        return new Revision(bundle);
                    }

                });
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<BundleRevision>() {

                    @Override
                    public Class<BundleRevision> typeof() {
                        return BundleRevision.class;
                    }

                    @Override
                    public BundleRevision adapt(Bundle bundle) {
                        return byBundles.get(bundle).metaspace;
                    }

                });
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<Metaspace>() {

                    @Override
                    public Class<Metaspace> typeof() {
                        return Metaspace.class;
                    }

                    @Override
                    public Metaspace adapt(Bundle bundle) {
                        return byBundles.get(bundle).metaspace;
                    }

                });

        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<TransientRepository>() {

                    @Override
                    public Class<TransientRepository> typeof() {
                        return TransientRepository.class;
                    }

                    @Override
                    public TransientRepository adapt(Bundle bundle) {
                        return byBundles.get(bundle)
                                .newRepository();
                    }
                });

    }

    final Activator activator = new Activator();

    long last = 0;

    final Map<String, Revision> byNames = new HashMap<>();

    final OSGiSystem system;

    final Map<Bundle, Revision> byBundles = new HashMap<>();

    final Map<Long, Bundle> byIds = new HashMap<>();

    final Map<URI, Bundle> byLocations = new HashMap<>();

    final Version DEFAULT_VERSION = new Version(0, 0, 0, null);

    TransientRepository systemRepository;

    Bundle byName(String name) {
        final Revision registration = byNames.get(name);
        if ((registration == null) || (registration.bundle == null)) {
            return null;
        }
        return registration.bundle;
    }

    void loadIndex() {
        return;
    }

    void storeIndex() throws IOException {
        return;
    }

    <T> T adapt(Class<T> type) {
        return type.cast(activator);
    }

    Filter createFilter(String filter) throws InvalidSyntaxException {
        return (filter == null) || "*".equals(filter) ? new OpenFilter() : FrameworkUtil.createFilter(filter);
    }

    class Revision {

        final Bundle bundle;

        long id = -1L;

        Metaspace metaspace;

        Revision(Bundle bundle) {
            this.bundle = bundle;
        }

        void install() throws BundleException {
            if (bundle == system.bundle) {
                systemRepository = new TransientRepository();
            }
            metaspace = systemRepository.loadMetaspace(bundle);
            id = last++;
            byNames.put(metaspace.name, this);
            byBundles.put(bundle, this);
            byIds.put(Long.valueOf(id), bundle);
            byLocations.put(bundle.adapt(OSGiFile.class)
                    .getLocation(), bundle);
        }

        void uninstall() {
            metaspace.unindex();
            byBundles.remove(bundle);
            byLocations.remove(bundle.adapt(OSGiFile.class)
                    .getLocation());
            byIds.remove(Long.valueOf(id));
            byNames.remove(metaspace.name);
            metaspace = null;
            id = -1L;
        }

        TransientRepository newRepository() {
            return new TransientRepository();
        }

        Bundle byLocation(URI location) {
            return byLocations.get(location);
        }

        Bundle byId(long id) {
            return byIds.get(id);
        }

        Bundle[] all() {
            return byIds.values()
                    .toArray(new Bundle[byIds.size()]);
        }

        @Override
        public String toString() {
            return "Registration[" + bundle.toString() + "]";
        }

        class TransientRepository implements org.osgi.service.repository.Repository {

            final Map<Resource, Resource> content = new HashMap<>();

            final Map<String, RepositoryNamespace> byNames = new HashMap<>();

            Metaspace loadMetaspace(Bundle bundle) throws BundleException {
                Metaspace metaspace = new Metaspace(bundle, bundle.adapt(OSGiFile.class)
                        .getManifest());
                if (metaspace.getRequirements("osgi.ee")
                        .isEmpty()) {
                    Map<String, String> directives = new HashMap<>();
                    directives.put("filter", "(&(osgi.ee=JavaSE)(version=1.8))");
                    metaspace.requirements.computeIfAbsent(Constants.REQUIRE_CAPABILITY, key -> new LinkedList<>())
                            .add(metaspace.newRequirement("osgi.ee", Collections.emptyMap(), directives));
                }
                index(metaspace);
                return metaspace;
            }

            RepositoryRequirement newRequirement(BundleRevision revision, String namespace,
                    Map<String, Object> attributes, Map<String, String> directives) {
                return byNames.computeIfAbsent(namespace, key -> new RepositoryNamespace(key))
                        .newRequirement(revision,
                                attributes, directives);
            }

            RepositoryCapability newCapability(BundleRevision revision, String namespace,
                    Map<String, Object> attributes, Map<String, String> directives) {
                return byNames.computeIfAbsent(namespace, key -> new RepositoryNamespace(key))
                        .newCapability(revision,
                                attributes, directives);
            }

            void index(Metaspace metaspace) throws BundleException {
                Resource registered = content.putIfAbsent(metaspace, metaspace);
                if ((registered != null) && metaspace.bundle != system.bundle) {
                    throw new BundleException(metaspace.name + " is already installed",
                            BundleException.DUPLICATE_BUNDLE_ERROR, null);
                }
                metaspace.capabilities.entrySet()
                        .stream()
                        .forEach(entry -> index(entry.getKey(), entry.getValue()
                                .stream()));
            }

            void index(Stream<? extends Capability> content) {
                content.forEach(capability -> byNames
                        .computeIfAbsent(capability.getNamespace(), RepositoryNamespace::new)
                        .index(capability));
            }

            void index(String namespace, Stream<? extends Capability> content) {
                byNames.computeIfAbsent(namespace, RepositoryNamespace::new)
                        .index(content);
            }

            void commit() {
                byNames.values()
                        .forEach(namespace -> namespace.commit());
            }

            void unindex(String namespace, Stream<Capability> content) {
                byNames.computeIfAbsent(namespace, RepositoryNamespace::new)
                        .unindex(content);
            }

            @Override
            public Map<Requirement, Collection<Capability>> findProviders(
                    Collection<? extends Requirement> requirements) {
                Map<Requirement, Collection<Capability>> providers = new HashMap<>();
                requirements.stream()
                        .forEach(requirement -> providers.put(requirement, findProviders(requirement)));
                return providers;
            }

            Collection<Capability> findProviders(Requirement requirement) {
                return namespacesOf(requirement).flatMap(namespace -> namespace.findCapabilities(requirement)
                        .stream())
                        .collect(Collectors.toList());
            }

            Stream<RepositoryNamespace> namespacesOf(Requirement requirement) {
                String name = requirement.getNamespace();
                if (name == null || "*".equals(name)) {
                    return byNames.values()
                            .stream();
                }
                if (!byNames.containsKey(name)) {
                    return Stream.empty();
                }
                return Stream.of(byNames.get(name));
            }

            @Override
            public Promise<Collection<Resource>> findProviders(RequirementExpression expression) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ExpressionCombiner getExpressionCombiner() {
                throw new UnsupportedOperationException();
            }

            @Override
            public RepositoryNamespace.RepositoryRequirementBuilder newRequirementBuilder(String namespace) {
                return byNames.computeIfAbsent(namespace, key -> new RepositoryNamespace(key))
                        .newRequirementBuilder();
            }

            class Metaspace implements org.osgi.framework.wiring.BundleRevision,
                    Comparable<org.osgi.framework.wiring.BundleRevision> {

                Metaspace(Bundle bundle, Manifest mf) throws BundleException {
                    this.bundle = bundle;
                    Parser parser = new Parser(mf).withSymbolicName()
                            .withDescription()
                            .withFragmentHost()
                            .withRequireCapabilities()
                            .withProvideCapabilities()
                            .withVersion()
                            .withRequireBundle()
                            .withImportPackage()
                            .withExportPackage()
                            .withPrivatePackage()
                            .orElseThrow(() -> new BundleException("Cannot parse manifest of " + bundle));
                    version = parser.version;
                    headers = parser.headers;
                    capabilities = parser.trycapabilities.stream()
                            .collect(Collectors.groupingBy(Capability::getNamespace));
                    requirements = parser.tryrequirements.stream()
                            .collect(Collectors.groupingBy(Requirement::getNamespace));
                    name = (String) capabilities.get(HostNamespace.HOST_NAMESPACE)
                            .stream()
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("can't find name"))
                            .getAttributes()
                            .get(HostNamespace.HOST_NAMESPACE);
                }

                final Dictionary<String, String> headers;

                final String name;

                final Bundle bundle;

                final Version version;

                final Map<String, List<Capability>> capabilities;

                final Map<String, List<Requirement>> requirements;

                Metaspace index(TransientRepository repository) {
                    return this;
                }

                void unindex() {
                    capabilities.entrySet()
                            .stream()
                            .forEach(entry -> byNames
                                    .computeIfAbsent(entry.getKey(), name -> new RepositoryNamespace(name))
                                    .unindex(entry.getValue()
                                            .stream()));
                }

                RepositoryRequirement newRequirement(String namespace, Map<String, Object> attributes,
                        Map<String, String> directives) {
                    return systemRepository.newRequirement(Metaspace.this, namespace, attributes, directives);
                }

                RepositoryCapability newCapability(String namespace, Map<String, Object> attributes,
                        Map<String, String> directives) {
                    return systemRepository.newCapability(Metaspace.this, namespace, attributes, directives);
                }

                VersionRange versionRangeOf(String range) {
                    return VersionRange.valueOf(range);
                }

                Version versionOf(String v) {
                    if (v == null) {
                        return null;
                    }
                    return new Version(v);
                }

                @Override
                public Bundle getBundle() {
                    return bundle;
                }

                @Override
                public String getSymbolicName() {
                    return name;
                }

                @Override
                public Version getVersion() {
                    return version;
                }

                @SuppressWarnings({ "unchecked", "rawtypes" })
                @Override
                public List<BundleCapability> getDeclaredCapabilities(String namespace) {
                    return (List) getCapabilities(namespace);
                }

                @SuppressWarnings({ "unchecked", "rawtypes" })
                @Override
                public List<BundleRequirement> getDeclaredRequirements(String namespace) {
                    return (List) getRequirements(namespace);
                }

                @Override
                public int getTypes() {
                    return getRequirements(HostNamespace.HOST_NAMESPACE).isEmpty() ? 0 : BundleRevision.TYPE_FRAGMENT;
                }

                @Override
                public BundleWiring getWiring() {
                    return bundle.adapt(BundleWiring.class);
                }

                @Override
                public List<Capability> getCapabilities(String namespace) {
                    return collect(capabilities, namespace);
                }

                @Override
                public List<Requirement> getRequirements(String namespace) {
                    return collect(requirements, namespace);
                }

                <R> List<R> collect(Map<String, List<R>> map, String namespace) {
                    return stream(map, namespace).collect(Collectors.toList());
                }

                <R> Stream<R> stream(Map<String, List<R>> map, String namespace) {
                    if ((namespace == null) || "*".equals(namespace == "*")) {
                        return map.values()
                                .stream()
                                .flatMap(collection -> collection.stream());
                    }
                    return map.getOrDefault(namespace, Collections.emptyList())
                            .stream();
                }

                @Override
                public int compareTo(BundleRevision o) {
                    final int nameDiff = name.compareTo(o.getSymbolicName());
                    if (nameDiff != 0) {
                        return nameDiff;
                    }
                    return version.compareTo(o.getVersion());
                }

                @Override
                public String toString() {
                    return new StringBuilder() // @formatter:off
                            .append("Metaspace@").append(Integer.toHexString(hashCode()))
                            .append("[")
                            .append(name).append(",")
                            .append("version=").append(version)
                            .append("]")
                            .toString(); // @formatter:on
                }

                class Parser {

                    Parser(Manifest mf) {
                        mf.getMainAttributes()
                                .entrySet()
                                .stream()
                                .forEach(
                                        entry -> headers.put(entry.getKey()
                                                .toString(), entry.getValue()
                                                        .toString()));
                    }

                    final Dictionary<String, String> headers = new Hashtable<>();

                    Try<Void> monitor = TryCompanion.<Void> of(ParseException.class)
                            .empty();

                    Try<RepositoryCapability> trycapabilities = TryCompanion.<RepositoryCapability> of(monitor)
                            .empty();

                    Try<RepositoryRequirement> tryrequirements = TryCompanion.<RepositoryRequirement> of(monitor)
                            .empty();

                    Try<Void> reduce(Try<?> submonitor, String message) {
                        return submonitor.reduce(self -> {
                            self.orElseThrow(() -> new ParseException(message, 0));
                            return null;
                        });
                    }

                    Parser orElseThrow(Supplier<BundleException> supplier) throws BundleException {
                        reduce(trycapabilities, "Cannot parse capabilities");
                        reduce(tryrequirements, "Cannot parse requirements");
                        monitor.orElseThrow(supplier);
                        return this;
                    }

                    String description;

                    Parser withDescription() {
                        description = Optional.ofNullable(headers.get(Constants.BUNDLE_DESCRIPTION))
                                .orElse("");
                        return this;
                    }

                    Version version;

                    Parser withVersion() {
                        monitor = monitor.sneakyRun(() -> {
                            version = Optional.ofNullable(headers.get(Constants.VERSION_ATTRIBUTE))
                                    .map(version -> versionOf(version))
                                    .orElseGet(() -> new Version(0, 0, 0));
                        });
                        return this;
                    }

                    Parser withFragmentHost() {
                        return withElement(Constants.FRAGMENT_HOST,
                                element -> tryrequirements = parseRequirement(element, HostNamespace.HOST_NAMESPACE));
                    }

                    Parser withSymbolicName() {
                        return withElement(Constants.BUNDLE_SYMBOLICNAME,
                                element -> trycapabilities = parseCapablities(element, HostNamespace.HOST_NAMESPACE))
                                        .withElement(Constants.BUNDLE_SYMBOLICNAME,
                                                element -> trycapabilities = parseCapablities(element,
                                                        BundleNamespace.BUNDLE_NAMESPACE));
                    }

                    Parser withRequireCapabilities() {
                        return withElement(Constants.REQUIRE_CAPABILITY,
                                element -> tryrequirements = parseRequirement(element));
                    }

                    Parser withProvideCapabilities() {
                        return withElement(Constants.PROVIDE_CAPABILITY,
                                element -> trycapabilities = parseCapablities(element));
                    }

                    Parser withRequireBundle() {
                        return withElement(Constants.REQUIRE_BUNDLE,
                                element -> tryrequirements = parseRequirement(element,
                                        BundleNamespace.BUNDLE_NAMESPACE));
                    }

                    Parser withImportPackage() {
                        return withElement(Constants.IMPORT_PACKAGE,
                                element -> tryrequirements = parseRequirement(element,
                                        PackageNamespace.PACKAGE_NAMESPACE));
                    }

                    Parser withExportPackage() {
                        return withElement(Constants.EXPORT_PACKAGE,
                                element -> trycapabilities = parseCapablities(element,
                                        PackageNamespace.PACKAGE_NAMESPACE));
                    }

                    Parser withPrivatePackage() {
                        return withElement("Private-Package", element -> trycapabilities = parseCapablities(element,
                                PackageNamespace.PACKAGE_NAMESPACE,
                                capability -> capability.extend()
                                        .addDirective("private", "true")
                                        .buildCapability()));
                    }

                    Parser withElement(String name, ConsumerCheckException<Element> consumer) {
                        monitor = Optional.ofNullable(headers.get(name))
                                .map(text -> monitor.sneakyRun(() -> consumer.accept(new Element(name, text))))
                                .orElse(monitor);
                        return this;
                    }

                    Try<RepositoryRequirement> parseRequirement(Element header) throws ParseException {
                        return parseRequirement(header, UnaryOperator.identity(), UnaryOperator.identity());
                    }

                    Try<RepositoryRequirement> parseRequirement(Element header, String namespace)
                            throws ParseException {
                        return parseRequirement(header, namespace, UnaryOperator.identity());
                    }

                    Try<RepositoryRequirement> parseRequirement(Element header, String namespace,
                            UnaryOperator<RepositoryRequirement> hook) {
                        return parseRequirement(header, component -> component.remap(namespace), hook);
                    }

                    Try<RepositoryRequirement> parseRequirement(Element header, UnaryOperator<Component> componenthook,
                            UnaryOperator<RepositoryRequirement> capabilityhook) {
                        return tryrequirements.sneakyMapAndCollect(Stream.of(header.components)
                                .map(componenthook),
                                component -> capabilityhook.apply(newRequirement(component)));
                    }

                    Try<RepositoryCapability> parseCapablities(Element header) throws ParseException {
                        return parseCapablities(header, UnaryOperator.identity(), UnaryOperator.identity());
                    }

                    Try<RepositoryCapability> parseCapablities(Element header, String namespace) throws ParseException {
                        return parseCapablities(header, namespace, UnaryOperator.identity());
                    }

                    Try<RepositoryCapability> parseCapablities(Element header, String namespace,
                            UnaryOperator<RepositoryCapability> hook) throws ParseException {
                        return parseCapablities(header, component -> component.remap(namespace), hook);
                    }

                    Try<RepositoryCapability> parseCapablities(Element header, UnaryOperator<Component> componenthook,
                            UnaryOperator<RepositoryCapability> capabilityhook) throws ParseException {
                        return trycapabilities.sneakyMapAndCollect(Stream.of(header.components)
                                .map(componenthook),
                                component -> capabilityhook.apply(newCapability(component)));
                    }

                    RepositoryRequirement newRequirement(Element.Component component) {
                        return Metaspace.this.newRequirement(component.namespace, component.attributes,
                                component.directives);
                    }

                    RepositoryCapability newCapability(Element.Component component) {
                        return Metaspace.this.newCapability(component.namespace, component.attributes,
                                component.directives);
                    }

                }

                class Element {

                    final String name;

                    final String text;

                    final Component[] components;

                    Element(String name, String text) throws ParseException {
                        this.name = name;
                        this.text = text;
                        components = new Parser(text).parseComponents();
                    }

                    @Override
                    public String toString() { // @formatter:off
            return new StringBuilder()
                .append("Metaspace Element@").append(Integer.toHexString(hashCode()))
                .append("[")
                .append(name).append(",")
                .append(Arrays.toString(components))
                .append("]").toString();
          } // @formatter::on


          class Component {

            final String namespace;

            final Map<String, String> directives;

            final Map<String, Object> attributes;

            Component(String namespace, Map<String, String> directives, Map<String, Object> attributes) {
              this.namespace = namespace;
              this.directives = directives;
              this.attributes = attributes;
            }

            public <T> T getAttribute(Class<T> oftype, String name) {
              return oftype.cast(attributes.get(name));
            }

            Component remap(String namespace) {
              attributes.put(namespace, this.namespace);
              return new Component(namespace, directives, attributes);
            }

            @Override
            public String toString() {
              return new StringBuilder() // @formatter:off
                 .append("Metaspace Component@").append(Integer.toHexString(hashCode()))
                 .append("[")
                 .append(namespace).append(",")
                 .append("attributes=").append(attributes).append(",")
                 .append("directives=").append(directives)
                 .append("]").toString(); // @formatter:on
                        }
                    }

                    class Parser {

                        final Reader reader;

                        Parser(String value) {
                            reader = new Reader(value);
                        }

                        Component[] parseComponents() throws ParseException {
                            final List<Component> components = new ArrayList<Component>();
                            do {
                                final Component component = parseComponent();
                                components.add(component);
                            } while (reader.hasRemaining() && reader.consume(","));
                            return components.toArray(new Component[components.size()]);
                        }

                        void error(String message) throws ParseException {
                            reader.error(message);
                        }

                        Component parseComponent() throws ParseException {
                            final String values = parseValue();
                            final int mark = reader.pos;
                            final Map<String, Object> attributes = parseAttributes();
                            reader.pos = mark;
                            final Map<String, String> directives = parseDirectives();
                            return new Component(values, directives, attributes);
                        }

                        String[] parseValues() throws ParseException {
                            final List<String> values = new ArrayList<String>();
                            do {
                                try {
                                    values.add(parseValue());
                                } catch (final ParseException cause) {
                                    break;
                                }
                            } while (reader.consume(";"));
                            return values.toArray(new String[values.size()]);
                        }

                        String parseValue() throws ParseException {
                            reader.mark();
                            do {
                                switch (reader.peek()) {
                                case '"':
                                    reader.consume();
                                    do {
                                        switch (reader.consume()) {
                                        case '"':
                                            break;
                                        case '\0':
                                            error("unterminated quoted string");
                                        }
                                    } while (true);
                                case '\\':
                                    reader.consume();
                                    break;
                                case ':':
                                    if (reader.hasRemaining() && (reader.peekNext() == '=')) {
                                        reader.error("not a value");
                                    }
                                    reader.consume();
                                    break;
                                case '=':
                                    reader.error("not a value");
                                    break;
                                case ',':
                                case ';':
                                case '\0':
                                    return reader.region()
                                            .trim();
                                default:
                                    reader.consume();
                                }
                            } while (true);
                        }

                        Map<String, String> parseDirectives() throws ParseException {
                            final Map<String, String> directives = new HashMap<>();
                            if (reader.peek() == '\0') {
                                return directives;
                            }
                            do {
                                final String token = parseToken();
                                if (!reader.consume(":=")) {
                                    while ((reader.peek() != ';') && (reader.peek() != ',')) {
                                        if (reader.peek() == '"') {
                                            parseQuotedString();
                                            continue;
                                        }
                                        if (reader.consume() == '\0') {
                                            return directives;
                                        }
                                    }
                                    continue;
                                }
                                final String argument = parseArgument();
                                directives.put(token, argument);
                            } while ((reader.peek() != '\0') && reader.consume(";"));
                            String resolution = directives.get(Constants.RESOLUTION_DIRECTIVE);
                            if (resolution == null) {
                                resolution = Constants.RESOLUTION_MANDATORY;
                            }
                            directives.put(Constants.RESOLUTION_DIRECTIVE, resolution);
                            return directives;
                        }

                        <T> T reset(T object) {
                            reader.reset();
                            return object;
                        }

                        Map<String, Object> parseAttributes() throws ParseException {
                            final Map<String, Object> attributes = new HashMap<>();
                            if (reader.peek() == '\0') {
                                return attributes;
                            }
                            do {
                                final String token = parseExtended();
                                if (!reader.consume("=")) {
                                    while ((reader.peek() != ';') && (reader.peek() != ',')) {
                                        if (reader.peek() == '"') {
                                            parseQuotedString();
                                            continue;
                                        }
                                        if (reader.consume() == '\0') {
                                            return attributes;
                                        }
                                    }
                                    continue;
                                }
                                final String argument = parseArgument();
                                attributes.put(token, parseAttribute(token, argument));
                            } while ((reader.peek() != '\0') && reader.consume(";"));

                            return attributes;
                        }

                        Object parseAttribute(String name, String value) throws ParseException {
                            if (Constants.VERSION_ATTRIBUTE.equals(name)) {
                                return versionRangeOf(value);
                            }
                            return value;
                        }

                        String parseArgument() throws ParseException {
                            if (reader.peek() == '"') {
                                return parseQuotedString();
                            }
                            return parseExtended();
                        }

                        String parseQuotedString() throws ParseException {
                            reader.consume("\"");
                            reader.mark();
                            do {
                                final char c = reader.peek();
                                if (c == '"') {
                                    try {
                                        return reader.region();
                                    } finally {
                                        reader.consume();
                                    }
                                }
                                if (c == '\0') {
                                    error("unterminated quoted string");
                                }
                                reader.consume();
                            } while (true);
                        }

                        void skipWhitespaces() {
                            do {
                                final char c = reader.peek();
                                if (!Character.isWhitespace(c)) {
                                    return;
                                }
                                reader.consume();
                            } while (true);
                        }

                        String parseToken() throws ParseException {
                            skipWhitespaces();
                            reader.mark();
                            do {
                                final char c = reader.peek();
                                if (Character.isLetterOrDigit(c)) {
                                    reader.consume();
                                } else if (c == '_') {
                                    reader.consume();
                                } else if (c == '-') {
                                    reader.consume();
                                } else {
                                    return reader.region();
                                }
                            } while (true);
                        }

                        String parseExtended() throws ParseException {
                            skipWhitespaces();
                            reader.mark();
                            do {
                                final char c = reader.peek();
                                if (Character.isLetterOrDigit(c) || (c == '_') || (c == '-') || (c == '.')) {
                                    reader.consume();
                                    continue;
                                }
                                return reader.region();
                            } while (true);
                        }

                        class Reader {

                            final CharSequence data;

                            int mark = 0;

                            int pos = 0;

                            final int end;

                            Reader(String value) {
                                data = value;
                                end = data == null ? -1 : data.length() - 1;
                            }

                            public void error(String message) throws ParseException {
                                reset();
                                throw new ParseException(message + "(" + reader.toString() + ")", pos);
                            }

                            boolean hasRemaining() {
                                return pos < end;
                            }

                            void reset() {
                                pos = mark;
                            }

                            void mark() {
                                mark = pos;
                            }

                            String region() throws ParseException {
                                if (data == null) {
                                    return null;
                                }
                                return data.subSequence(mark, pos)
                                        .toString();
                            }

                            char consume() {
                                if (pos > end) {
                                    return '\0';
                                }
                                try {
                                    return data.charAt(pos);
                                } finally {
                                    pos += 1;
                                }
                            }

                            boolean consume(String expected) throws ParseException {
                                if ((pos + expected.length()) > end) {
                                    return false;
                                }
                                final int length = expected.length();
                                final String value = data.subSequence(pos, pos + expected.length())
                                        .toString();
                                if (!expected.equals(value)) {
                                    return false;
                                }
                                pos += length;
                                return true;
                            }

                            char peek() {
                                if (pos > end) {
                                    return '\0';
                                }
                                return data.charAt(pos);
                            }

                            char peekNext() {
                                return data.charAt(pos + 1);
                            }

                            @Override
                            public String toString() {
                                if (pos > end) {
                                    return "";
                                }
                                return data.subSequence(pos, end)
                                        .toString();
                            }
                        }

                    }

                }

            }

            class RepositoryNamespace {
                final String namespace;

                final Comparator<Capability> comparator = (c1, c2) -> c1.equals(c2) ? 0 : 1;

                final Function<Capability, String> classifier;

                final Function<String, Collection<Capability>> supplier = name -> new LinkedList<>();

                final Map<String, Collection<Capability>> byNames = new HashMap<>();

                RepositoryNamespace(String namespace) {
                    this.namespace = namespace;
                    classifier = capability -> (String) capability.getAttributes()
                            .get(namespace);
                }

                RepositoryRequirementBuilder newRequirementBuilder() {
                    return new RepositoryRequirementBuilder();
                }

                RepositoryCapability newCapability(BundleRevision revision, Map<String, Object> attributes,
                        Map<String, String> directives) {
                    return new RepositoryCapability(revision, attributes, directives);
                }

                RepositoryRequirement newRequirement(BundleRevision revision, Map<String, Object> attributes,
                        Map<String, String> directives) {
                    return new RepositoryRequirement(revision, attributes, directives);
                }

                void index(Stream<? extends Capability> capabilities) {
                    capabilities.forEach(this::index);
                }

                void index(Capability capability) {
                    byNames.computeIfAbsent(classifier.apply(capability), supplier)
                            .add(capability);
                }

                void commit() {
                    byNames.keySet()
                            .forEach(name -> byNames // remove duplicates
                                    .compute(name,
                                            (key, collection) -> collection.stream()
                                                    .distinct()
                                                    .collect(Collectors.toList()))
                                    .stream() // then index content
                                    .map(Capability::getResource)
                                    .distinct()
                                    .forEach(resource -> content.computeIfAbsent(resource, key -> key)));
                }

                void unindex(Stream<Capability> capabilities) {
                    capabilities.forEach(capability -> byNames.computeIfAbsent(classifier.apply(capability), supplier)
                            .remove(capability));
                }

                Predicate<Capability> predicate(Requirement requirement) {
                    if (requirement instanceof BundleRequirement) {
                        class FilterPredicate {
                            boolean accept(Capability capability) {
                                if (!(capability instanceof BundleCapability)) {
                                    return false;
                                }
                                return ((BundleRequirement) requirement).matches((BundleCapability) capability);
                            }
                        }
                        return new FilterPredicate()::accept;
                    } else {
                        class AttributesPredicate {
                            boolean accept(Capability capability) {
                                return requirement.getAttributes()
                                        .entrySet()
                                        .stream()
                                        .allMatch(entry -> entry
                                                .getValue()
                                                .equals(capability.getAttributes()
                                                        .get(entry.getKey())));
                            }
                        }
                        return new AttributesPredicate()::accept;
                    }
                }

                Collection<Capability> findCapabilities(Requirement requirement) {
                    Predicate<Capability> predicate = predicate(requirement);
                    return Optional.ofNullable((String) requirement.getAttributes()
                            .get(namespace))
                            .map(name -> byNames.getOrDefault(name, Collections.emptySortedSet())
                                    .stream())
                            .orElseGet(() -> byNames.values()
                                    .stream()
                                    .flatMap(capabilities -> capabilities.stream()))
                            .filter(predicate)
                            .collect(Collectors.toList());
                }

                class FilterBuilder {

                    Filter build(Requirement requirement) {
                        String filter = Optional
                                .ofNullable(requirement.getDirectives()
                                        .get(org.osgi.resource.Namespace.REQUIREMENT_FILTER_DIRECTIVE))
                                .orElse(buildFilter(requirement));
                        try {
                            return FrameworkUtil.createFilter(filter);
                        } catch (final InvalidSyntaxException cause) {
                            return SneakyThrow.sneakyThrow(new BundleException("Cannot parse " + filter, cause));
                        }
                    }

                    String buildFilter(Requirement requirement) {
                        class Builder {
                            StringBuilder builder = new StringBuilder();

                            Builder enter() {
                                builder.append("(&");
                                return this;
                            }

                            Builder with(Map<String, Object> attributes) {
                                attributes.forEach(this::with);
                                return this;
                            }

                            Builder with(String name, Object value) {
                                if (value instanceof VersionRange) {
                                    builder.append(((VersionRange) value).toFilterString(name));
                                } else {
                                    builder.append("(")
                                            .append(name)
                                            .append("=")
                                            .append(escape(value.toString()))
                                            .append(")");
                                }
                                return this;
                            }

                            String build() {
                                return builder.append(")")
                                        .toString();
                            }

                            String escape(String value) {
                                final StringBuilder builder = new StringBuilder();
                                value.chars()
                                        .forEachOrdered(code -> {
                                            if ((code == '(') || (code == ')') || (code == '*')) {
                                                builder.append('\\');
                                            }
                                            builder.append((char) code);
                                        });
                                return builder.toString();
                            }

                        }

                        return new Builder().enter()
                                .with(requirement.getAttributes())
                                .build();
                    }

                }

                class RepositoryRequirement implements BundleRequirement {

                    final BundleRevision revision;

                    final Map<String, String> directives;

                    final Map<String, Object> attributes;

                    final Filter filter;

                    RepositoryNamespace outer() {
                        return RepositoryNamespace.this;
                    }

                    RepositoryRequirement(BundleRevision revision, Map<String, Object> attributes,
                            Map<String, String> directives) {
                        this.revision = revision;
                        this.attributes = attributes;
                        this.directives = directives;
                        filter = new FilterBuilder().build(this);
                    }

                    @Override
                    public String getNamespace() {
                        return namespace;
                    }

                    @Override
                    public Map<String, String> getDirectives() {
                        return directives;
                    }

                    @Override
                    public Map<String, Object> getAttributes() {
                        return attributes;
                    }

                    @Override
                    public BundleRevision getResource() {
                        return revision;
                    }

                    @Override
                    public BundleRevision getRevision() {
                        return revision;
                    }

                    @Override
                    public boolean matches(BundleCapability capability) {
                        boolean matches = filter.matches(capability.getAttributes());
                        return matches;
                    }

                    @Override
                    public boolean equals(Object object) {
                        if (object == null) {
                            return false;
                        }
                        if (!(object instanceof Requirement)) {
                            return false;
                        }
                        Requirement other = (Requirement) object;
                        if (!getNamespace().equals(other.getNamespace())) {
                            return false;
                        }
                        if (!Optional.ofNullable(getAttributes().get(namespace))
                                .map(name -> name.equals(other.getAttributes()
                                        .get(namespace)))
                                .orElseGet(() -> directives.get(Namespace.REQUIREMENT_FILTER_DIRECTIVE)
                                        .equals(other.getDirectives()
                                                .get(Namespace.REQUIREMENT_FILTER_DIRECTIVE)))) {
                            return false;
                        }
                        return revision.equals(other.getResource());
                    }

                    @Override
                    public int hashCode() {
                        final int prime = 31;
                        int result = 1;
                        result = result * prime + namespace.hashCode();
                        result = result * prime + Optional.ofNullable(attributes.get(namespace))
                                .orElseGet(() -> directives.get(Namespace.REQUIREMENT_FILTER_DIRECTIVE))
                                .hashCode();
                        result = result * prime + revision.hashCode();
                        return result;
                    }

                    @Override
                    public String toString() {
                        return new StringBuilder() // @formatter:off
                .append("Repository Requirement@").append(Integer.toHexString(hashCode()))
                .append("[")
                .append(getRevision()).append(",")
                .append("namespace=").append(namespace).append(",")
                .append("attributes=").append(attributes.toString()).append(",")
                .append("directives=").append(directives.toString())
                .append("]")
                .toString(); // @formatter:on
                    }

                }

                class RepositoryRequirementBuilder implements RequirementBuilder {

                    BundleRevision revision;

                    final Map<String, Object> attributes = new HashMap<>();

                    final Map<String, String> directives = new HashMap<>();

                    @Override
                    public RepositoryRequirementBuilder addAttribute(String name, Object value) {
                        attributes.put(name, value);
                        return this;
                    }

                    @Override
                    public RepositoryRequirementBuilder addDirective(String name, String value) {
                        directives.put(name, value);
                        return this;
                    }

                    @Override
                    public RepositoryRequirementBuilder setAttributes(Map<String, Object> attributes) {
                        this.attributes.putAll(attributes);
                        return this;
                    }

                    @Override
                    public RepositoryRequirementBuilder setDirectives(Map<String, String> directives) {
                        this.directives.putAll(directives);
                        return this;
                    }

                    @Override
                    public RepositoryRequirementBuilder setResource(Resource resource) {
                        revision = (BundleRevision) resource;
                        return this;
                    }

                    @Override
                    public RepositoryRequirement build() {
                        return new RepositoryRequirement(revision, attributes, directives);
                    }

                    @Override
                    public IdentityExpression buildExpression() {
                        throw new UnsupportedOperationException();
                    }

                    public RepositoryCapability buildCapability() {
                        return new RepositoryCapability(revision, attributes, directives);
                    }

                }

                class RepositoryCapability implements BundleCapability {

                    final BundleRevision revision;

                    final Map<String, Object> attributes;

                    final Map<String, String> directives;

                    RepositoryRequirementBuilder extend() {
                        return newRequirementBuilder().setResource(revision)
                                .setAttributes(attributes)
                                .setDirectives(directives);
                    }

                    RepositoryCapability(BundleRevision revision, Map<String, Object> attributes,
                            Map<String, String> directives) {
                        this.revision = revision;
                        this.attributes = attributes;
                        this.directives = directives;
                    }

                    @Override
                    public String getNamespace() {
                        return namespace;
                    }

                    @Override
                    public Map<String, String> getDirectives() {
                        return directives;
                    }

                    @Override
                    public Map<String, Object> getAttributes() {
                        return attributes;
                    }

                    @Override
                    public BundleRevision getResource() {
                        return revision;
                    }

                    @Override
                    public BundleRevision getRevision() {
                        return revision;
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
                        if (!getNamespace().equals(other.getNamespace())) {
                            return false;
                        }
                        if (!getAttributes().get(getNamespace())
                                .equals(other.getAttributes()
                                        .get(getNamespace()))) {
                            return false;
                        }
                        return getResource().equals(other.getResource());
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
                    public String toString() {
                        return new StringBuilder() // @formatter:off
                .append("Repository Capability@").append(Integer.toHexString(hashCode()))
                .append("[")
                .append(getRevision()).append(",")
                .append("namespace=").append(namespace).append(",")
                .append("attributes=").append(attributes.toString()).append(",")
                .append("directives=").append(directives.toString())
                .append("]")
                .toString();
          } // @formatter:on

                }

            }

        }

    }

    class Activator implements BundleActivator {

        ServiceRegistration<Repository> registration;

        @Override
        public void start(BundleContext context) throws Exception {
            registration = context.registerService(Repository.class, systemRepository, null);
            BundleRevision revision = system.adapt(BundleRevision.class);
            extendSystem(revision);
        }

        void extendSystem(BundleRevision revision) {
            // osgi.ee

            systemRepository.index("osgi.ee",
                    Stream.of("1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8")
                            .map(version -> systemRepository.newRequirementBuilder("osgi.ee")
                                    .addAttribute("osgi.ee", "JavaSE")
                                    .addAttribute("version", version)
                                    .setResource(revision)
                                    .buildCapability()));
        }

        @Override
        public void stop(BundleContext context) throws Exception {
            registration.unregister();
        }

    }

    static class OpenFilter implements Filter {

        @Override
        public boolean match(ServiceReference<?> reference) {
            return true;
        }

        @Override
        public boolean match(Dictionary<String, ?> dictionary) {
            return true;
        }

        @Override
        public boolean matchCase(Dictionary<String, ?> dictionary) {
            return true;
        }

        @Override
        public boolean matches(Map<String, ?> map) {
            return true;
        }

        static final OpenFilter OPEN = new OpenFilter();
    }

    static class VersionRange extends org.osgi.framework.VersionRange implements Comparable<VersionRange> {

        public static VersionRange valueOf(String range) {
            return Optional.ofNullable(range)
                    .map(VersionRange::new)
                    .orElse(null);
        }

        VersionRange(String range) {
            super(range);
        }

        @Override
        public int compareTo(VersionRange o) {
            return getLeft().compareTo(intersection(o).getLeft());
        }
    }

}
