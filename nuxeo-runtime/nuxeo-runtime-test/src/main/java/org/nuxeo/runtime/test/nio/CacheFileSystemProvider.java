/*******************************************************************************
 * Copyright (c) 2015 Nuxeo SA (http://nuxeo.com/) and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.nuxeo.runtime.test.nio;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CacheFileSystemProvider extends FileSystemProvider {

    final Map<String, CacheFileSystem> filesystems = new HashMap<>();

    @Override
    public String getScheme() {
        return "cache";
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        FileSystem sink = toMemory(uri, env);
        Path source = toSource(uri, env);
        CacheFileStore store = new CacheFileStore(source.getFileSystem().getFileStores().iterator().next());
        CacheFileSystem fs = new CacheFileSystem(this, store, uri, sink, source);
        filesystems.put(uri.getPath(), fs);
        return fs;
    }

    static FileSystem toMemory(URI uri, Map<String, ?> env) throws IOException {
        try {
            return FileSystems.newFileSystem(new URI("memory:".concat(uri.getPath())), env);
        } catch (URISyntaxException cause) {
            throw new IOException(cause);
        }
    }

    static Path toSource(URI uri, Map<String, ?> env) throws IOException {
        if (uri.getPath().endsWith(".jar")) {
            try {
                return FileSystems.newFileSystem(new URI("zipfs:".concat(uri.toASCIIString()).concat("!/")), env).getPath(
                        "/");
            } catch (URISyntaxException cause) {
                throw new IOException(cause);
            }
        }
        return Paths.get(uri);
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        String key = uri.getPath();
        if (!filesystems.containsKey(key)) {
            throw new FileSystemNotFoundException(key);
        }
        return filesystems.get(uri);
    }

    @Override
    public Path getPath(URI uri) {
        return getFileSystem(uri).getPath(uri.getFragment());
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
//        CachePath cachePath = toCachePath(path);
//        Path sinkPath = toSinkPath(cachePath);
//        try {
//            cachePath.fs.sink.provider().newByteChannel(sinkPath, options, attrs);
//        } catch (NoSuchFileException cause) {
//            Path sourcePath = toSourcePath(cachePath);
//            cachePath.fs.source.getFileSystem().
//        }
        throw new UnsupportedOperationException();
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(Path path) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSameFile(Path path1, Path path2) throws IOException {
        return CachePath.toCachePath(path1).equals(CachePath.toCachePath(path2));
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        CachePath cachePath = toCachePath(path);
        return cachePath.fs.source.getFileSystem().provider().isHidden(toSourcePath(cachePath));
    }

    static CachePath toCachePath(Path path) {
        return CachePath.toCachePath(path);
    }

    static Path toSourcePath(CachePath path) {
        return path.fs.source.resolve(path.path.toString());
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        return CachePath.toCachePath(path).fs.store;
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        CachePath cachePath = toCachePath(path);
        toSourceProvider(cachePath).checkAccess(toSourcePath(cachePath), modes);
    }

    static FileSystemProvider toSourceProvider(CachePath cachePath) {
        FileSystemProvider toSourceProvider = cachePath.fs.source.getFileSystem().provider();
        return toSourceProvider;
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        CachePath cachePath = toCachePath(path);
        return toSourceProvider(cachePath).getFileAttributeView(toSourcePath(cachePath), type, options);
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
            throws IOException {
        CachePath cachePath = toCachePath(path);
        return toSourceProvider(cachePath).readAttributes(toSourcePath(cachePath), type, options);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        CachePath cachePath = toCachePath(path);
        return toSourceProvider(cachePath).readAttributes(toSourcePath(cachePath), attributes, options);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }

}
