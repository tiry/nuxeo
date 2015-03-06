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
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

public class CachePath extends AbstractPath {

    final CacheFileSystem fs;

    final CachePath base;

    final org.nuxeo.common.utils.Path path;

    CachePath(CacheFileSystem fs, String... segments) {
        this.fs = fs;
        path = toNuxeoPath(segments);
        base = fs.root;
    }

    CachePath(CacheFileSystem fs, String first, String... others) {
        this(fs, toSegments(first, others));
    }


    CachePath(CacheFileSystem fs, org.nuxeo.common.utils.Path path) {
        this.fs = fs;
        this.path = path;
        base = fs.root;
    }

    CachePath(CachePath base, org.nuxeo.common.utils.Path path) {
        fs = base.fs;
        this.base = base;
        this.path = path;
    }

    @Override
    public FileSystem getFileSystem() {
        return fs;
    }

    @Override
    public boolean isAbsolute() {
        return base == fs.root;
    }

    @Override
    public Path getRoot() {
        return base;
    }

    @Override
    public Path getFileName() {
        return new CachePath(this, toNuxeoPath(path.lastSegment()));
    }

    @Override
    public Path getParent() {
        return new CachePath(base, path.removeLastSegments(1));
    }

    @Override
    public int getNameCount() {
        return path.segmentCount();
    }

    @Override
    public Path getName(int index) {
        return new CachePath(base, toNuxeoPath(path.segment(index)));
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        return new CachePath(base,
                path.removeFirstSegments(beginIndex).removeLastSegments(path.segmentCount() - endIndex));
    }

    @Override
    public boolean startsWith(Path other) {
        return path.isPrefixOf(((CachePath) other).path);
    }

    @Override
    public boolean endsWith(Path other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Path normalize() {
        return this;
    }

    @Override
    public Path resolve(Path other) {
        if (other.isAbsolute()) {
            return other;
        }

        return new CachePath(base, path.append(((CachePath)other).path) );
    }

    @Override
    public CachePath relativize(Path other) {
        CachePath otherPath = toCachePath(other);
        int count = path.matchingFirstSegments(otherPath.toAbsolutePath().path);
        if (count == 0) {
            return otherPath;
        }
        return new CachePath(this, otherPath.path.removeFirstSegments(count));
    }

    @Override
    public URI toUri() {
        return fs.uri.resolve(path.toString());
    }

    @Override
    public CachePath toAbsolutePath() {
        return new CachePath(base, base.path.append(path));
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(Path other) {
        return path.matchingFirstSegments(((CachePath) other).path);
    }



    static String[] toSegments(String first, String[] others) {
        String[] segments = new String[others.length + 1];
        segments[0] = first;
        for (int i = 0; i < others.length; ++i) {
            segments[i + 1] = others[i];
        }
        return segments;
    }

    static CachePath toCachePath(Path path) {
        return (CachePath)path;
    }

    static org.nuxeo.common.utils.Path toNuxeoPath(Path path) {
        return toCachePath(path).path;
    }

    static org.nuxeo.common.utils.Path toNuxeoPath(String... segments) {
        if (segments.length == 1 && segments[0] == null) {
            segments = new String[0];
        }
        return org.nuxeo.common.utils.Path.createFromSegments(segments);
    }
}
