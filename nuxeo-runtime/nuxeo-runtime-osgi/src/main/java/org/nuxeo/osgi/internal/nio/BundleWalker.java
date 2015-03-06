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
 *
 * $Id$
 */

package org.nuxeo.osgi.internal.nio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import org.nuxeo.common.trycompanion.TryCompanion;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class BundleWalker {

    public interface Callback {
        void visitBundle(Bundle bundle);
    }

    public static final String[] DEFAULT_PATTERNS = new String[] { "*.jar", "*.war", "*.rar", "*.sar", "*_jar", "*_war",
            "*_rar" };

    protected final BundleContext osgi;

    protected final String[] patterns;

    protected final Callback callback;

    public BundleWalker(BundleContext osgi, Callback cb) {
        this(osgi, cb, DEFAULT_PATTERNS);
    }

    public BundleWalker(BundleContext osgi, Callback cb, String[] patterns) {
        this.osgi = osgi;
        this.patterns = patterns;
        callback = cb;
    }

    public void visit(File root) throws IOException {
        Path rootPath = root.toPath();
        FilterBuilder<Path> filterBuilder = new FilterBuilder<Path>(rootPath.getFileSystem());
        try (RecursiveDirectoryStream directoryStream = new RecursiveDirectoryStream(
                rootPath,
                filterBuilder.newOrFilter(patterns))) {
            TryCompanion.<Void> of(IOException.class)
                    .sneakyForEachAndCollect(
                            directoryStream
                                    .stream()
                                    .map(Path::toFile),
                            this::visitBundleFile)
                    .orElseThrow(() -> new IOException("Caught errors while visiting " + root));
        }
    }

    public void visit(Collection<File> roots) throws IOException {
        TryCompanion.<Void> of(IOException.class)
                .sneakyForEachAndCollect(
                        roots.stream(),
                        this::visit)
                .orElseThrow(() -> new IOException("Caught errors while visiting " + roots));
    }

    public void visit(File... roots) throws IOException {
        visit(Arrays.asList(roots));
    }

    protected boolean visitBundleFile(File file) throws IOException {
        Bundle bundle;
        try {
            bundle = osgi.installBundle(file.toURI().toASCIIString());
        } catch (BundleException e) {
            throw new IOException("Cannot install bundle file " + file, e);
        }
        callback.visitBundle(bundle);
        return true;
    }

}
