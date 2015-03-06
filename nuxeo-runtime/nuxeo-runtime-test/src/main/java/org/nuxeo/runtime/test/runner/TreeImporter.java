/*******************************************************************************
 * Copyright (c) 2015 Nuxeo SA (http://nuxeo.com/) and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.nuxeo.runtime.test.runner;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class TreeImporter implements FileVisitor<Path> {
    final Path source;

    final Path sink;

    public TreeImporter(Path source, Path sink) {
        this.source = source;
        this.sink = sink;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (dir == source) {
            return CONTINUE;
        }
        Files.copy(dir, toSinkPath(dir), COPY_ATTRIBUTES, REPLACE_EXISTING);
        return CONTINUE;
    }

    Path toSinkPath(Path path) {
        if (path == source) {
            return sink;
        }
        path = source.relativize(path);
        path = sink.resolve(path.toString());
        return path;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.copy(file, toSinkPath(file), COPY_ATTRIBUTES, REPLACE_EXISTING);
        return CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException error) {
        if (error != null) {
            return FileVisitResult.TERMINATE;
        }
        return CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException error) {
        if (error != null) {
            return FileVisitResult.TERMINATE;
        }
        return CONTINUE;
    }


}
