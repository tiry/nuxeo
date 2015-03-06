package org.nuxeo.osgi.system;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import org.nuxeo.osgi.bootstrap.OSGiFile;
import org.nuxeo.osgi.bootstrap.OSGiHook;
import org.nuxeo.osgi.internal.nio.FilterBuilder;
import org.nuxeo.osgi.internal.nio.RecursiveDirectoryStream;
import org.nuxeo.osgi.system.OSGiBundleAdapter.BundleAdapter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

public class OSGiFilesystem {

    final OSGiSystem system;

    final Map<URI, OSGiFile> byLocations = new HashMap<>();

    final Map<Bundle, OSGiFile> byBundles = new HashMap<>();

    OSGiFilesystem(OSGiSystem system) throws BundleException {
        this.system = system;
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<Activation>() {

                    @Override
                    public Class<Activation> typeof() {
                        return Activation.class;
                    }

                    @Override
                    public Activation adapt(Bundle bundle) {
                        return new Activation(bundle);
                    }

                });
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<OSGiFile>() {

                    @Override
                    public Class<OSGiFile> typeof() {
                        return OSGiFile.class;
                    }

                    @Override
                    public OSGiFile adapt(Bundle bundle) {
                        return byBundles.get(bundle);
                    }

                });
        system.adapt(OSGiBundleAdapter.Activation.class)
                .install(new BundleAdapter<Manifest>() {

                    @Override
                    public Class<Manifest> typeof() {
                        return Manifest.class;
                    }

                    @Override
                    public Manifest adapt(Bundle bundle) throws BundleException {
                        return byBundles.get(bundle)
                                .getManifest();
                    }
                });
    }

    Path openArchive(URI location) throws BundleException {
        if (!location.toASCIIString()
                .endsWith(".jar")) {
            return Paths.get(location);
        }
        return openFilesystem(location).getPath("/");
    }

    FileSystem openFilesystem(URI location) throws BundleException {
        try {
            URI zip = new URI("jar:".concat(location.toASCIIString())
                    .concat("!/"));
            try {
                return FileSystems.getFileSystem(zip);
            } catch (FileSystemNotFoundException cause) {
                return FileSystems.newFileSystem(zip, Collections.emptyMap());
            }
        } catch (URISyntaxException | IOException cause) {
            throw new BundleException("Cannot open " + location, cause);
        }
    }

    Path rootPath(Path path) throws BundleException {
        return path;
    }

    @Override
    public String toString() {
        return "OSGiFilesystem@" + Integer.toHexString(hashCode()) + byBundles;
    }

    class BundleFile implements OSGiFile {

        protected final URI location;

        protected final FileSystem fs;

        protected final Path root;

        protected final Manifest mf;

        protected final ResolutionCache resolutionCache = new ResolutionCache();

        protected BundleFile(URI location, Path root) throws BundleException {
            fs = root.getFileSystem();
            this.location = location;
            this.root = root;
            mf = system.adapt(OSGiHook.class)
                    .onManifest(this, loadManifest(root));
        }

        @Override
        public Manifest getManifest() {
            return mf;
        }

        @Override
        public URI getLocation() {
            return location;
        }

        @Override
        public Path getEntry(String pathname) {
            if (pathname.startsWith("/")) {
                pathname = pathname.substring(1);
            }
            if (pathname.isEmpty()) {
                return root;
            }
            return resolutionCache.getEntry(pathname);
        }

        @Override
        public DirectoryStream<Path> getEntryPaths(String path) {
            throw new UnsupportedOperationException("The operation BundleFile.geEntryPaths() is not yet implemented");
        }

        @Override
        public DirectoryStream<Path> findEntries(final String name, final String pattern, boolean recurse) {

            final Path path = "/".equals(name) ? root : root.resolve(name);

            final DirectoryStream.Filter<Path> filter = new FilterBuilder<Path>(root.getFileSystem())
                    .newFilter(pattern);

            final RecursiveDirectoryStream stream = new RecursiveDirectoryStream(path,
                    filter);

            return stream;
        }

        @Override
        public String toString() {
            return "OSGiFileSystem$File [fs=" + fs + ",root=" + root + "]";
        }

        @Override
        public void update(final OSGiFile file) {
            throw new UnsupportedOperationException();
        }

        class ResolutionCache {
            final Map<String, Path> byNames = new HashMap<>();

            Path getEntry(String pathname) {
                return byNames.computeIfAbsent(pathname, key -> resolve(key));
            }

            Path resolve(String pathname) {
                Path path = root.resolve(pathname);
                if (!Files.exists(path)) {
                    return null;
                }
                return path;
            }

        }
    }

    class CompositeFile implements OSGiFile {

        final Collection<OSGiFile> files = new ArrayList<OSGiFile>();

        final OSGiFile main = files.iterator()
                .next();

        public CompositeFile(OSGiFile... files) {
            this.files.addAll(Arrays.asList(files));
        }

        @Override
        public void update(OSGiFile file) {
            files.add(file);
        }

        @Override
        public Manifest getManifest() {
            return main.getManifest();
        }

        @Override
        public URI getLocation() {
            return main.getLocation();
        }

        @Override
        public DirectoryStream<Path> getEntryPaths(final String pathname) {
            return new CompositeDirectoryStream(files.stream()
                    .map(file -> file.getEntryPaths(pathname)));
        }

        @Override
        public Path getEntry(String pathname) {
            for (OSGiFile file : files) {
                Path path = file.getEntry(pathname);
                if (path != null) {
                    return path;
                }
            }
            return null;
        }

        @Override
        public DirectoryStream<Path> findEntries(final String pathname, final String filter, final boolean recurse) {
            return new CompositeDirectoryStream(
                    files.stream()
                            .map(file -> file.findEntries(pathname, filter, recurse)));
        }
    }

    static class CompositeDirectoryStream implements DirectoryStream<Path> {
        final Stream<DirectoryStream<Path>> streams;

        public CompositeDirectoryStream(Stream<DirectoryStream<Path>> streams) {
            this.streams = streams;
        }

        @Override
        public void close() throws IOException {
            IOException ioerrors = new IOException();
            Iterator<DirectoryStream<Path>> iterator = streams.iterator();
            while (iterator.hasNext()) {
                try {
                    iterator.next()
                            .close();
                } catch (IOException cause) {
                    ioerrors.addSuppressed(cause);
                }
            }
            if (ioerrors.getSuppressed().length > 0) {
                throw ioerrors;
            }
        }

        @Override
        public Iterator<Path> iterator() {
            return new Iterator<Path>() {

                Iterator<DirectoryStream<Path>> directories = streams.iterator();

                Iterator<Path> paths;

                @Override
                public boolean hasNext() {
                    if (paths != null) {
                        if (paths.hasNext()) {
                            return true;
                        }
                        paths = null;
                    }
                    while (directories.hasNext()) {
                        paths = directories.next()
                                .iterator();
                        if (paths.hasNext()) {
                            return true;
                        }
                        paths = null;
                    }
                    return false;
                }

                @Override
                public Path next() {
                    if (paths == null) {
                        throw new NoSuchElementException();
                    }
                    return paths.next();
                }

            };
        }
    }

    class Activation {
        final Bundle bundle;

        Activation(Bundle bundle) {
            this.bundle = bundle;
        }

        OSGiFile install(OSGiFile file) throws BundleException {
            system.adapt(OSGiHook.class)
                    .onFile(file);
            byLocations.put(file.getLocation(), file);
            byBundles.put(bundle, file);
            return file;
        }

        OSGiFile adapt(URI location) throws BundleException {
            if (!byLocations.containsKey(location)) {
                return new BundleFile(location, openArchive(location));
            }
            return byLocations.get(location);
        }

        public void update(URI location) throws BundleException {
            BundleFile file = new BundleFile(location, openArchive(location));
            byLocations.put(location, file);
            byBundles.put(bundle, file);
        }
    }

}
