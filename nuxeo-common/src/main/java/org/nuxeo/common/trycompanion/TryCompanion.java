package org.nuxeo.common.trycompanion;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.nuxeo.common.trycompanion.SneakyThrow.ConsumerCheckException;
import org.nuxeo.common.trycompanion.SneakyThrow.FunctionCheckException;
import org.nuxeo.common.trycompanion.SneakyThrow.RunnableCheckException;
import org.nuxeo.common.trycompanion.SneakyThrow.SupplierCheckException;

public class TryCompanion<T> {

    private TryCompanion(Predicate<Throwable> sneakyPredicate) {
        this.sneakyPredicate = sneakyPredicate;
        this.originator = null;
    }

    private TryCompanion(Try<?> originator) {
        this.sneakyPredicate = originator.companion().sneakyPredicate;
        this.originator = originator;
    }

    final Predicate<Throwable> sneakyPredicate;

    final Try<?> originator;

    @SafeVarargs
    public static <T> TryCompanion<T> of(Class<? extends Exception>... oftypes) {
        Predicate<Throwable> predicate = cause -> Stream.of(oftypes)
                .anyMatch(type -> type.isAssignableFrom(cause.getClass()));
        return new TryCompanion<T>(predicate);
    }

    public static <T> TryCompanion<T> of(TryCompanion<?> originator) {
        return new TryCompanion<>(originator.sneakyPredicate);
    }

    public static <T> TryCompanion<T> of(Try<?> originator) {
        return new TryCompanion<>(originator);
    }

    public Try<T> run(Runnable runnable) {
        try {
            runnable.run();
            return successOf(Collections.emptyList());
        } catch (Exception cause) {
            if (!sneakyPredicate.test(cause)) {
                SneakyThrow.sneakyThrow(cause);
            }
            return failureOf(cause);
        }
    }

    public Try<T> sneakyRun(RunnableCheckException runnable) {
        return run(SneakyThrow.sneakyRunnable(runnable));
    }

    public Try<T> consume(Consumer<TryCompanion<T>> consumer) {
        return run(() -> consumer.accept(this));
    }

    public Try<T> sneakyConsume(ConsumerCheckException<TryCompanion<T>> consumer) {
        return consume(SneakyThrow.sneakyConsumer(consumer));
    }

    public <I> Stream<Supplier<Void>> forEach(Stream<I> supplier, Consumer<I> function) {
        return TryCompanion.<Void>of(this).map(supplier, (Function<I, Void>) input -> {
            function.accept(input);
            return null;
        });
    }

    public <I> Stream<Supplier<Void>> sneakyForEach(Stream<I> supplier, ConsumerCheckException<I> function) {
        return forEach(supplier, SneakyThrow.sneakyConsumer(function));
    }

    public <I> Try<Void> forEachAndCollect(Stream<I> supplier, Consumer<I> function) {
        return TryCompanion.<Void>of(this).mapAndCollect(supplier, (Function<I, Void>) input -> {
            function.accept(input);
            return null;
        });
    }

    public <I> Try<Void> sneakyForEachAndCollect(Stream<I> supplier, ConsumerCheckException<I> consumer) {
        return forEachAndCollect(supplier, SneakyThrow.sneakyConsumer(consumer));
    }

    public <I> Stream<Supplier<T>> map(Stream<I> supplier, Function<I, T> function) {
        return supplier.map(lazy(function));
    }

    public <I> Try<T> mapAndCollect(Stream<I> supplier, Function<I, T> function) {
        return map(supplier, function).collect(throwAtEnd());
    }

    public <I> Stream<Supplier<T>> sneakyMap(Stream<I> supplier, FunctionCheckException<I, T> function) {
        return map(supplier, SneakyThrow.sneakyFunction(function));
    }

    public <I> Try<T> sneakyMapAndCollect(Stream<I> supplier, FunctionCheckException<I, T> function) {
        return mapAndCollect(supplier, SneakyThrow.sneakyFunction(function));
    }

    public <I> Try<T> flatMapAndCollect(Stream<I> supplier, Function<I, Try<T>> function) {
        return supplier.map(function).reduce(this::merge).orElse(successOf(Collections.emptyList()));
    }

    public <I> Try<T> sneakyFlatMapAndCollect(Stream<I> supplier, FunctionCheckException<I, Try<T>> function) {
        return flatMapAndCollect(supplier, SneakyThrow.sneakyFunction(function));
    }

    /**
     * Creates an instance of Try wrapping the result of the provided function. <br>
     * If the function results in a value then {@link Success} with the value is returned. <br>
     * In case the function raises an exception then {@link Failure} is returned containing that exception. <br>
     * If <code>null</code> is provided as argument then the {@link #apply(Object)} is invoked. <br>
     *
     * @param <T>
     *            The type for the Try
     * @param r
     *            The supplier to store either the value <i>T</i> or the raised exception.
     * @return The resulting Try instance wrapping what the function resulted in
     */
    public Try<T> supply(Supplier<T> supplier) {
        try {
            return successOf(supplier.get());
        } catch (Exception cause) {
            if (!sneakyPredicate.test(cause)) {
                SneakyThrow.sneakyThrow(cause);
            }
            return failureOf(cause);
        }
    }

    public Try<T> sneakySupply(SupplierCheckException<T> supplier) {
        return supply(SneakyThrow.sneakySupplier(supplier));
    }

    public Try<T> tryOf(Collection<T> values, Collection<Exception> causes) {
        if (causes.isEmpty()) {
            return successOf(values);
        }
        return failureOf(values, causes);
    }

    /**
     * {@code BinaryOperator<Try>} that merges the contents of its right argument into its left argument.
     *
     * @param left
     *            The left {@code Try} to merge
     * @param right
     *            the right {@code Try} to merge
     * @return the merged @{code Try}
     */
    public Try<T> merge(Try<T> left, Try<T> right) {
        if (left.isSuccess() && left.collection().isEmpty()) {
            return right;
        }
        if (right.isSuccess() && right.collection().isEmpty()) {
            return left;
        }
        List<T> values = Stream.concat(left.stream(), right.stream()).collect(Collectors.toList());
        Collection<Exception> causes = TryCompanion.<Exception>of(this)
                .merge(left.failed(), right.failed())
                .collection();
        return tryOf(values, causes);
    }

    public Try<T> empty() {
        return new Success<>(this, Collections.emptyList());
    }

    public Try<T> successOf(T value) {
        return successOf(Collections.singleton(value));
    }

    public Success<T> successOf(Collection<T> values) {
        return new Success<>(this, values);
    }

    public Success<T> successOf(@SuppressWarnings("unchecked") T... values) {
        return successOf(Arrays.asList(values));
    }

    public Failure<T> failureOf(Exception cause) {
        return failureOf(Collections.emptyList(), Collections.singleton(cause));
    }

    public Failure<T> failureOf(Collection<T> values, Collection<Exception> causes) {
        return new Failure<>(successOf(values), causes);
    }

    /**
     * Returns a function that wraps a value in a {@link java.util.function.Supplier} of that value.
     *
     * @param <T>
     *            the type of the value to wrap
     */
    public Function<T, Supplier<T>> toSupplier() {
        return input -> () -> input;
    }

    /**
     * Wraps a function into one that will accept and return {@link java.util.function.Supplier}s of the input and
     * output
     * values, instead of the values themselves.
     * <p>
     * Designed to be used to wrap functions passed to {@link Stream#map(java.util.function.Function)} calls following
     * an
     * initial map call that uses {@link #lazy(java.util.function.Function)}.
     * </p>
     *
     * @param f
     *            the function to wrap
     * @param <R>
     *            output type of the wrapped function
     * @return a function that does the same thing as f, except it accepts {@link java.util.function.Supplier}s of
     *         values
     *         instead of values.
     */
    public <I> Function<Supplier<I>, Supplier<T>> liftToSuppliers(Function<I, T> f) {
        return supplier -> () -> f.apply(supplier.get());
    }

    /**
     * Wraps a function into one that returns a {@link java.util.function.Supplier} of the result of the original
     * function.
     * <p>
     * Wrapped functions can be passed to {@link Stream#map(java.util.function.Function)}, effectively delegating
     * control
     * of the computation to a downstream component such as a {@link java.util.stream.Collector}.
     * </p>
     * <p>
     * {@code stream.map(lazy(f)} is equivalent to {@code stream.map(toSuppliers()).map(liftToSuppliers(f))}
     * </p>
     *
     * @param f
     *            the function to wrap
     * @param <T>
     *            input type of the wrapped function
     * @param <R>
     *            output type of the wrapped function
     * @return a function that does the same thing as f, except it returns a {@link java.util.function.Supplier} of the
     *         result.
     */
    public <I> Function<I, Supplier<T>> lazy(Function<I, T> f) {
        return input -> () -> f.apply(input);
    }

    public <I> Function<I, Supplier<T>> lazy(Consumer<I> c) {
        return input -> () -> {
            c.accept(input);
            return null;
        };
    }

    /**
     * Same as {@link #liftToSuppliers(java.util.function.Function)}.
     */
    public <R> Function<Supplier<R>, Supplier<T>> lazylift(Function<R, T> f) {
        return liftToSuppliers(f);
    }

    /**
     * Returns a collector that filters out computations that failed with the specified exceptions classes.
     *
     * @param failures
     *            exceptions to discard
     * @param <T>
     *            type of the results
     */
    public Collector<Supplier<T>, Collection<T>, Try<T>> discarding() {
        return Collector.of(
                LinkedList::new,
                (result, supplier) -> {
                    try {
                        result.add(supplier.get());
                    } catch (Exception e) {
                        if (!sneakyPredicate.test(e)) {
                            throw e;
                        }
                    }
                },
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                result -> successOf(result));
    }

    /**
     * Returns a collector that consumes the stream and retains results until one of the specified exception is raised.
     *
     * @param considers
     *            exception types that should interrupt the overall operation
     * @param <T>
     *            type of the results
     */
    public Collector<Supplier<T>, EndFastAccumulator, Try<T>> upToSucces() {
        return Collector.of(() -> new EndFastAccumulator(result -> result.isSuccess()),
                EndFastAccumulator::run,
                EndFastAccumulator::combine,
                EndFastAccumulator::finish);
    }

    /**
     * Returns a collector that consumes the stream and retains results until one of the specified exception is raised.
     *
     * @param considers
     *            exception types that should interrupt the overall operation
     * @param <T>
     *            type of the results
     */
    public Collector<Supplier<T>, EndFastAccumulator, Try<T>> upToFailure() {
        return Collector.of(() -> new EndFastAccumulator(result -> result.isFailure()),
                EndFastAccumulator::run,
                EndFastAccumulator::combine,
                EndFastAccumulator::finish);
    }

    /**
     * @{link Collector} that consumes the entire stream even if an exception is thrown. All successfully computed
     *        results
     *        and all thrown exceptions are stored in a {@link Try}.
     * @param considers
     *            types of exception that should be thrown at the end
     * @param <T>
     *            type of the results
     * @return Try<T> the operation result
     */
    public Collector<Supplier<T>, FailAtEndAccumulator, Try<T>> throwAtEnd() {
        return Collector.of(
                FailAtEndAccumulator::new,
                FailAtEndAccumulator::run,
                FailAtEndAccumulator::combine,
                FailAtEndAccumulator::finish);
    }

    class EndFastAccumulator {

        final List<Exception> causes = new LinkedList<>();

        final List<T> values = new LinkedList<>();

        final Predicate<Try<T>> isTermination;

        boolean aborted;

        EndFastAccumulator(Predicate<Try<T>> predicate) {
            isTermination = predicate;
        }

        EndFastAccumulator run(Supplier<T> supplier) {
            aborted = isTermination.test(supply(supplier)
                    .onFailure(self -> self.failed().stream().forEach(cause -> causes.add(cause)))
                    .onSuccess(self -> self.succeed().stream().forEach(value -> values.add(value))));
            return this;
        }

        EndFastAccumulator combine(EndFastAccumulator other) {
            if (aborted) {
                return this;
            }
            causes.addAll(other.causes);
            values.addAll(other.values);
            return this;
        }

        Try<T> finish() {
            return tryOf(values, causes);
        }

    }

    class FailAtEndAccumulator {

        final List<Exception> causes = new LinkedList<>();

        final List<T> values = new LinkedList<>();

        FailAtEndAccumulator run(Supplier<T> supplier) {
            supply(supplier)
                    .onFailure(self -> self.failed().stream().forEach(cause -> causes.add(cause)))
                    .onSuccess(self -> self.succeed().stream().forEach(value -> values.add(value)));
            return this;
        }

        FailAtEndAccumulator combine(FailAtEndAccumulator other) {
            causes.addAll(other.causes);
            values.addAll(other.values);
            return this;
        }

        Try<T> finish() {
            return tryOf(values, causes);
        }

    }

    class TypedConsumer<X extends Exception> implements Consumer<Exception> {
        Class<X> oftype;

        Consumer<X> consumer;

        @Override
        public void accept(Exception t) {
            if (!t.getClass().isAssignableFrom(oftype)) {
                return;
            }
            consumer.accept(oftype.cast(t));
        }
    }

    public <X extends Exception> X summarize(Try<Exception> failure, Function<String, X> supplier) {
        class Summary {
            final StringBuilder builder = new StringBuilder();

            final String lineseparator = System.lineSeparator();

            void append(Exception error) {
                append(0, error);
            }

            StringBuilder append(int level) {
                while (level-- > 0) {
                    builder.append(' ');
                }
                return builder;
            }

            void append(int level, Throwable error) {
                if (sneakyPredicate.test(error)) {
                    append(level++).append(error.getMessage()).append(lineseparator);
                }
                for (Throwable suppressed : error.getSuppressed()) {
                    append(level, suppressed);
                }
                Throwable cause = error.getCause();
                if ((cause == null) || (cause == error)) {
                    return;
                }
                append(level, cause);
            }

            Summary with(Try<Exception> failure) {
                failure.forEach(this::append);
                return this;
            }

            String build() {
                return builder.toString();
            }

        }
        String summary = new Summary().with(failure).build();
        return supplier.apply(summary);
    }

}