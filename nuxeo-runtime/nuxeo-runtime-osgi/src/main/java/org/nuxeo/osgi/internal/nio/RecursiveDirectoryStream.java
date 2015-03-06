package org.nuxeo.osgi.internal.nio;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class RecursiveDirectoryStream implements DirectoryStream<Path> {

    public RecursiveDirectoryStream(Path startPath, DirectoryStream.Filter<Path> filter) {
        this.startPath = Objects.requireNonNull(startPath);
        this.filter = filter;
    }

    public RecursiveDirectoryStream(Path startPath) {
        this(startPath, AcceptAllFilter.FILTER);
    }

    public Stream<Path> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    public Stream<Path> parallelStream() {
        return StreamSupport.stream(spliterator(), true);
    }

    protected final LinkedBlockingQueue<Path> bag = new LinkedBlockingQueue<>();

    protected final Path startPath;

    protected final Filter<Path> filter;

    protected ForkJoinTask<Void> walkTask;

    @Override
    public Iterator<Path> iterator() {
        findFiles(startPath, filter);
        return new Iterator<Path>() {
            Path path;

            @Override
            public boolean hasNext() {
                try {
                    path = bag.poll();
                    while (!walkTask.isDone() && (path == null)) {
                        path = bag.poll(5, TimeUnit.MILLISECONDS);
                    }
                    return (path != null);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return false;
            }

            @Override
            public Path next() {
                return path;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Removal not supported");
            }
        };
    }

    protected void findFiles(final Path root, final Filter<Path> filter) {
        walkTask = ForkJoinTask.adapt(new Walker(root, filter));
        walkTask.fork();
    }

    @Override
    public void close() throws IOException {
        if (walkTask != null) {
            walkTask.cancel(true);
        }
        bag.clear();
        walkTask = null;
    }

    protected class Walker implements Callable<Void> {

        protected final Path root;

        protected final Filter<Path> filter;

        protected Walker(Path root, Filter<Path> filter) {
            this.root = root;
            this.filter = filter;
        }

        @Override
        public Void call() throws Exception {
            Files.walkFileTree(root, new FileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if ((dir != root) && filter.accept(dir)) {
                        bag.offer(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (filter.accept(file)) {
                        bag.offer(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

            });
            return null;
        }
    }

    static class AcceptAllFilter
            implements DirectoryStream.Filter<Path> {
        private AcceptAllFilter() {
        }

        @Override
        public boolean accept(Path entry) {
            return true;
        }

        static final AcceptAllFilter FILTER = new AcceptAllFilter();
    }

}
