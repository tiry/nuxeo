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
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.Set;

public class CacheFileSystem extends FileSystem {

    final CacheFileSystemProvider provider;

    final CacheFileStore store;

    final FileSystem sink;

    final Path source;

    final URI uri;

    final CachePath root;

    public CacheFileSystem(CacheFileSystemProvider provider, CacheFileStore store, URI uri, FileSystem sink, Path source) {
        this.provider = provider;
        this.uri = uri;
        this.store = store;
        this.sink = sink;
        this.source = source;
        root = new CachePath(this);
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {
        sink.close();
    }

    @Override
    public boolean isOpen() {
        return sink.isOpen();
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String getSeparator() {
        return sink.getSeparator();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return sink.getRootDirectories();
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return Collections.singleton(store);
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return sink.supportedFileAttributeViews();
    }

    @Override
    public Path getPath(String first, String... more) {
        return new CachePath(this, first, more);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        return sink.getUserPrincipalLookupService();
    }

    @Override
    public WatchService newWatchService() throws IOException {
        return sink.newWatchService();
    }

    URI toURI() {
        return uri;
    }

}
