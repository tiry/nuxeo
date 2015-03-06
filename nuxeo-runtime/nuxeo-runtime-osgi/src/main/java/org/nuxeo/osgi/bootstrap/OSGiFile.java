package org.nuxeo.osgi.bootstrap;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.function.Function;
import java.util.jar.Manifest;

import org.osgi.framework.BundleException;

public interface OSGiFile {

    URI getLocation();

    Manifest getManifest();

    Path getEntry(String pathname);

    DirectoryStream<Path> getEntryPaths(String pathname);

    DirectoryStream<Path> findEntries(String pathname, String filter, boolean recurse);

    void update(OSGiFile file);

    default Manifest loadManifest(Path basepath) throws BundleException {
        Path path = basepath.resolve("META-INF/nuxeo.mf");
        if (!Files.exists(path)) {
            path = basepath.resolve("META-INF/MANIFEST.MF");
        }
        try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
            return new Manifest(in);
        } catch (FileNotFoundException | NoSuchFileException error) {
            return new Manifest();
        } catch (IOException cause) {
            throw new BundleException("Cannot load manifest of system bundle in " + this,
                    BundleException.MANIFEST_ERROR, cause);
        }
    }


    static final Function<Path, URL> toURL = new Function<Path, URL>() {

        @Override
        public URL apply(Path path) {
            if (path == null) {
                return null;
            }
            try {
                return path.toUri().toURL();
            } catch (MalformedURLException cause) {
                throw new RuntimeException("Cannot locate " + path, cause);
            }
        }
    };

    static final Function<Path, String> toString = new Function<Path, String>() {

        @Override
        public String apply(Path path) {
            return path.toString();
        }
    };

    static <T> Enumeration<T> toEnums(DirectoryStream<Path> stream, Function<Path, T> adaptor) {
        return new Enumeration<T>() {

            Iterator<Path> paths = stream.iterator();

            @Override
            public boolean hasMoreElements() {
                return paths.hasNext();
            }

            @Override
            public T nextElement() {
                return adaptor.apply(paths.next());
            }

        };
    }

}
