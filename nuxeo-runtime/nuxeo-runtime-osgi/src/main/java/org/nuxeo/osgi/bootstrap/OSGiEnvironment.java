package org.nuxeo.osgi.bootstrap;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Properties;

import org.nuxeo.common.LoaderConstants;
import org.osgi.framework.BundleException;

public class OSGiEnvironment extends Properties {

    private static final long serialVersionUID = 1L;

    public final String name;
    public final File homeDir;
    public final File tmpDir;
    public final File dataDir;
    public final File nestedDir;
    public final File workingDir;

    OSGiEnvironment(Properties env) throws BundleException {
        super(env);
        name = getProperty(LoaderConstants.APP_NAME, "nuxeo");
        homeDir = newDir(LoaderConstants.HOME_DIR, newOSGiTempFile(), "home");
        dataDir = newDir(LoaderConstants.DATA_DIR, homeDir, "data");
        tmpDir = newDir(LoaderConstants.TMP_DIR, homeDir, "tmp");
        workingDir = newDir(LoaderConstants.WORKING_DIR, homeDir, "work");
        nestedDir = newDir(LoaderConstants.NESTED_DIR, tmpDir, "nested");
        removeContent(tmpDir);
    }

    File newDir(String key, File base, String name) {
        String path = getProperty(key, new File(base, name).getPath());
        File dir = new File(path);
        dir.mkdirs();
        return dir;
    }

    File newOSGiTempFile() throws BundleException {
        try {
            File tempfile = File.createTempFile("nxosgi", null);
            tempfile.delete();
            tempfile.mkdirs();
            return tempfile;
        } catch (IOException e) {
            throw new BundleException("Cannot create temp osgi file", e);
        }
    }

    File newNestedDir(File dir) {
        File nestedDir = new File(dir, "nested-bundles");
        nestedDir.mkdirs();
        return nestedDir;
    }

    void removeContent(File dir) throws BundleException {
        final Path base = dir.toPath();
        try {
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc == null) {
                        if (!base.equals(dir)) {
                            Files.delete(dir);
                        }
                        return FileVisitResult.CONTINUE;
                    } else {
                        throw exc;
                    }
                }
            });
        } catch (IOException e) {
            throw new BundleException("Cannot remove content of " + dir, e);
        }
    }

    String getName() {
        return name;
    }

}
