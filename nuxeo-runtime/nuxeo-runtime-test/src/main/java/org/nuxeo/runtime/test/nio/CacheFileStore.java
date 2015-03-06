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
import java.nio.file.FileStore;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

public class CacheFileStore extends FileStore{

    final FileStore source;

    CacheFileStore(FileStore source) {
        this.source = source;
    }

    @Override
    public String name() {
        return "cache";
    }

    @Override
    public String type() {
        return "none";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public long getTotalSpace() throws IOException {
        return source.getTotalSpace();
    }

    @Override
    public long getUsableSpace() throws IOException {
        return source.getUsableSpace();
    }

    @Override
    public long getUnallocatedSpace() throws IOException {
       return source.getUnallocatedSpace();
    }

    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        return source.supportsFileAttributeView(type);
    }

    @Override
    public boolean supportsFileAttributeView(String name) {
        return source.supportsFileAttributeView(name);
    }

    @Override
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        return source.getFileStoreAttributeView(type);
    }

    @Override
    public Object getAttribute(String attribute) throws IOException {
        return source.getAttribute(attribute);
    }

}
