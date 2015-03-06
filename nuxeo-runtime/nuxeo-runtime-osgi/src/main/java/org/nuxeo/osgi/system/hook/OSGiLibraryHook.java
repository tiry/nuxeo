package org.nuxeo.osgi.system.hook;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.nuxeo.osgi.bootstrap.OSGiFile;
import org.nuxeo.osgi.bootstrap.OSGiHook;
import org.osgi.framework.Constants;

public class OSGiLibraryHook implements OSGiHook {

    @Override
    public OSGiFile onFile(OSGiFile file) {
        return file;
    }

    @Override
    public Manifest onManifest(OSGiFile file, Manifest mf) {
        Attributes attrs = mf.getMainAttributes();
        if (attrs.containsKey(new Attributes.Name(Constants.BUNDLE_SYMBOLICNAME))) {
            return mf;
        }
        String name = guessName(file);
        attrs.putValue(Constants.BUNDLE_SYMBOLICNAME, name);
        long timestamp = System.nanoTime();
        try {
            OSGiPackagesIndexer pkgIndexer = new OSGiPackagesIndexer();
            pkgIndexer.index(file.findEntries("/", "*.class", true));
            attrs.putValue(Constants.EXPORT_PACKAGE, pkgIndexer.exports());
            attrs.putValue(Constants.IMPORT_PACKAGE, pkgIndexer.imports());
        } catch (IOException cause) {
            throw new AssertionError("Cannot index packages of " + name, cause);
        }
        attrs.putValue(Constants.REQUIRE_CAPABILITY, "osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=1.8))\"");
        attrs.putValue(Constants.DYNAMICIMPORT_PACKAGE, "true");
        attrs.putValue(Constants.BUNDLE_ACTIVATIONPOLICY, Constants.ACTIVATION_LAZY);

        return mf;
    }

    String guessExport(Collection<OSGiPackagesIndexer.Package> file) {

        throw new UnsupportedOperationException();

    }

    String guessName(OSGiFile file) {
        DirectoryStream<Path> poms = file.findEntries("/META-INF", "pom.properties", true);
        List<String> names = new LinkedList<>();
        poms.forEach(path -> {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException e) {
                ;
            }
            names.add(properties.get("groupId") + "." + properties.getProperty("artifactId"));
        });
        if (names.size() != 1) {
            String path = file.getLocation()
                    .getPath();
            int lastIndex = path.lastIndexOf('/');
            if (lastIndex > 0) {
                path = path.substring(lastIndex + 1);
            }
            if (path.endsWith(".jar")) {
                path = path.substring(0, path.length() - 4);
            }
            names.add(path);
        }
        return "library~".concat(names.get(0));
    }

}
