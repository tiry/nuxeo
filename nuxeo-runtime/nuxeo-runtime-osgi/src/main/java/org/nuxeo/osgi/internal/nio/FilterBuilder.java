package org.nuxeo.osgi.internal.nio;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

public class FilterBuilder<T extends Path> {

    protected final FileSystem fs;

    public FilterBuilder(FileSystem fs) {
        this.fs = fs;
    }

    public DirectoryStream.Filter<T> newFilter(final String pattern) {
        return new DirectoryStream.Filter<T>() {
            final PathMatcher matcher = fs.getPathMatcher("glob:".concat(pattern));

            @Override
            public boolean accept(T entry) throws IOException {
                Path filename = entry.getFileName();
                if (filename == null) {
                    return false;
                }
                if (!matcher.matches(filename)) {
                    return false;
                }
                if (!Files.exists(entry)) {
                    return false;
                }
                return true;
            }
        };
    }

    public DirectoryStream.Filter<T> newOrFilter(final String patterns[]) {
        return new DirectoryStream.Filter<T>() {
            @SuppressWarnings("unchecked")
            DirectoryStream.Filter<T> filters[] = new DirectoryStream.Filter[patterns.length];
            {
                for (int i = 0; i < patterns.length; ++i) {
                    filters[i] = newFilter(patterns[i]);
                }
            }

            @Override
            public boolean accept(T entry) throws IOException {
                for (DirectoryStream.Filter<T> filter : filters) {
                    if (filter.accept(entry)) {
                        return true;
                    }
                }
                return false;
            }
        };

    }

    public DirectoryStream.Filter<T> newAndFilter(final String[] patterns) {
        return new DirectoryStream.Filter<T>() {
            @SuppressWarnings("unchecked")
            DirectoryStream.Filter<T> filters[] = new DirectoryStream.Filter[patterns.length];
            {
                for (int i = 0; i < patterns.length; ++i) {
                    filters[i] = newFilter(patterns[i]);
                }
            }

            @Override
            public boolean accept(T entry) throws IOException {
                boolean accepted = true;
                for (DirectoryStream.Filter<T> filter : filters) {
                    if (!filter.accept(entry)) {
                        accepted = false;
                        ;
                    }
                }
                return accepted;
            }
        };

    }
}