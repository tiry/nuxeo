package org.nuxeo.common.trycompanion;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Enables you to pass throwing lambdas and method references to the {@link java.util.stream.Stream} API. . checked
 * exceptions are only checked by the compiler, the JVM allows throwing any Throwable at any time . type parameters are
 * compiler-only too, there is no trace of them in the bytecode (erasure) sneakyThrowInner “clearly” casts the Throwable
 * to T (which is RuntimeException). At runtime all the generic type information is “erased” – it’s not in the bytecode.
 * So throw (T) t; effectively becomes throw t; (no cast).
 */
public class SneakyThrow {

    private SneakyThrow() {

    }

    @FunctionalInterface
    public interface RunnableCheckException {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface SupplierCheckException<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface ConsumerCheckException<T> {
        void accept(T input) throws Exception;
    }

    @FunctionalInterface
    public interface BiConsumerCheckException<T, U> {
        void accept(T t, U u) throws Exception;
    }

    @FunctionalInterface
    public interface FunctionCheckException<T, R> {
        R apply(T input) throws Exception;

        default <U> FunctionCheckException<T, U> andThen(FunctionCheckException<R, U> after) {
            return input -> after.apply(this.apply(input));
        }

        default <U> FunctionCheckException<U, R> before(FunctionCheckException<U, T> before) {
            return before.andThen(this);
        }
    }

    @FunctionalInterface
    public interface BiFunctionCheckException<T, U, R> {
        R apply(T t, U u) throws Exception;

        default <V> BiFunctionCheckException<T, U, V> andThen(BiFunctionCheckException<R, U, V> after) {
            return (t, u) -> after.apply(this.apply(t, u), u);
        }

        default <V> BiFunctionCheckException<V, U, R> before(BiFunctionCheckException<V, U, T> before) {
            return before.andThen(this);
        }
    }

    public static Runnable discard(RunnableCheckException r,
            @SuppressWarnings("unchecked") Class<? extends Exception>... oftypes) {
        return () -> {
            try {
                r.run();
            } catch (Exception ex) {
                SneakyThrow.<Void>sneakyDiscard(ex, oftypes);
            }
        };
    }

    public static Runnable sneakyRunnable(RunnableCheckException r) {
        return () -> {
            try {
                r.run();
            } catch (Exception ex) {
                SneakyThrow.<Void>sneakyThrow(ex);
            }
        };
    }

    /**
     * Wraps a supplier that throws checked exceptions into a supplier that does not, by discarding the checked
     * exception.
     * This method is useful to pass throwing lambdas and method references to the {@link java.util.stream.Stream} API.
     *
     * @param s
     *            the supplier to wrap
     * @param oftypes
     *            the exception types to discard
     * @param <T>
     *            input type of the wrapped function
     * @return a function that does the same thing as f, except it will discard the unchecked when the original supplier
     *         would have thrown checked exceptions
     */
    @SafeVarargs
    public static <T> Supplier<Optional<T>> discard(SupplierCheckException<T> s,
            Class<? extends Exception>... oftypes) {
        return () -> {
            try {
                return Optional.of(s.get());
            } catch (Exception ex) {
                return SneakyThrow.<T>sneakyDiscard(ex, oftypes);
            }
        };

    }

    /**
     * Wraps a supplier that throws checked exceptions into a supplier that does not, by sneaky throwing the checked
     * exceptions {@link SneakyThrow}. This method is useful to pass throwing lambdas and method references to the
     * {@link java.util.stream.Stream} API.
     *
     * @param s
     *            the supplier to wrap
     * @param <T>
     *            input type of the wrapped supplier
     * @return a supplier that does the same thing as s
     */
    public static <T> Supplier<T> sneakySupplier(SupplierCheckException<T> s) {
        return () -> {
            try {
                return s.get();
            } catch (Exception ex) {
                return SneakyThrow.sneakyThrow(ex);
            }
        };
    }

    /**
     * Wraps a consumer that throws checked exceptions into a consumer that does not, by discarding the checked
     * exception.
     * This method is useful to pass throwing lambdas and method references to the {@link java.util.stream.Stream} API.
     *
     * @param c
     *            the consumer to wrap
     * @param oftypes
     *            the exception types to discard
     * @param <T>
     *            input type of the wrapped function
     * @return a function that does the same thing as f, except it will discard the unchecked when the original consumer
     *         would have thrown checked exceptions
     */
    @SafeVarargs
    public static <T> Consumer<T> discard(ConsumerCheckException<T> c, Class<? extends Exception>... oftypes) {
        return input -> {
            try {
                c.accept(input);
            } catch (Exception ex) {
                SneakyThrow.<Void>sneakyDiscard(ex, oftypes);
            }
        };
    }

    /**
     * Wraps a consumer that throws checked exceptions into a consumer that does not, by sneaky throwing the checked
     * exceptions {@link SneakyThrow}. This method is useful to pass throwing lambdas and method references to the
     * {@link java.util.stream.Stream} API.
     *
     * @param c
     *            the consumer to wrap
     * @param <T>
     *            input type of the wrapped consumer
     * @return a consumer that does the same thing as c
     */
    public static <T> Consumer<T> sneakyConsumer(ConsumerCheckException<T> c) {
        return input -> {
            try {
                c.accept(input);
            } catch (Exception ex) {
                SneakyThrow.<Void>sneakyThrow(ex);
            }
        };
    }

    /**
     * Wraps a bi-consumer that throws checked exceptions into a consumer that does not, by discarding the checked
     * exception. This method is useful to pass throwing lambdas and method references to the
     * {@link java.util.stream.Stream} API.
     *
     * @param c
     *            the bi-consumer to wrap
     * @param oftypes
     *            the exception types to discard
     * @param <T>
     *            input type of the wrapped consumer
     * @param <U>
     *            input type of the wrapped consumer
     * @return a function that does the same thing as f, except it will discard the unchecked when the original consumer
     *         would have thrown checked exceptions
     */
    @SafeVarargs
    public static <T, U> BiConsumer<T, U> discard(BiConsumerCheckException<T, U> c,
            Class<? extends Exception>... oftypes) {
        return (t, u) -> {
            try {
                c.accept(t, u);
            } catch (Exception ex) {
                SneakyThrow.<Void>sneakyDiscard(ex, oftypes);
            }
        };
    }

    /**
     * Wraps a consumer that throws checked exceptions into a consumer that does not, by sneaky throwing the checked
     * exceptions. This method is useful to pass throwing lambdas and method references to the
     * {@link java.util.stream.Stream} API.
     *
     * @param c
     *            the consumer to wrap
     * @param <T>
     *            input type of the wrapped consumer
     * @param <U>
     *            input type of the wrapped consumer
     * @return a consumer that does the same thing as c
     */
    public static <T, U> BiConsumer<T, U> sneakyConsumer(BiConsumerCheckException<T, U> c) {
        return (t, u) -> {
            try {
                c.accept(t, u);
            } catch (Exception ex) {
                SneakyThrow.<Void>sneakyThrow(ex);
            }
        };
    }

    /**
     * Wraps a function that throws checked exceptions into a function that does not, by discarding the checked
     * exceptions. This method is useful to pass throwing lambdas and method references to the
     * {@link java.util.stream.Stream} API.
     *
     * @param f
     *            the function to wrap
     * @param discards
     *            the exception types to discard
     * @param <T>
     *            input type of the wrapped function
     * @param <R>
     *            output type of the wrapped function
     * @return a function that does the same thing as f, except it will discard the unchecked when the original function
     *         would have thrown checked exceptions
     */
    public static <T, R> Function<T, Optional<R>> discard(FunctionCheckException<T, Optional<R>> f,
            Class<? extends Exception> discards) {
        return input -> {
            try {
                return f.apply(input);
            } catch (Exception ex) {
                return SneakyThrow.<R>sneakyDiscard(ex, discards);
            }
        };
    }

    /**
     * Wraps a function that throws checked exceptions into a function that does not, by sneaky throwing the checked
     * exceptions {@link SneakyThrow}. This method is useful to pass throwing lambdas and method references to the
     * {@link java.util.stream.Stream} API.
     *
     * @param f
     *            the function to wrap
     * @param <T>
     *            input type of the wrapped function
     * @param <R>
     *            output type of the wrapped function
     * @return a function that does the same thing as f
     */
    public static <T, R> Function<T, R> sneakyFunction(FunctionCheckException<T, R> f) {
        return input -> {
            try {
                return f.apply(input);
            } catch (Exception ex) {
                return SneakyThrow.<R>sneakyThrow(ex);
            }
        };
    }

    /**
     * Re-throw the checked exception as runtime or return an empty result if discarded
     *
     * @param error
     *            the error to re-throw
     * @param discards
     *            the error types to discard
     * @return an empty result if the error was discarded
     */
    @SafeVarargs
    public static <T> Optional<T> sneakyDiscard(Exception cause, Class<? extends Exception>... discards) {
        if (!Stream.of(discards).anyMatch(oftype -> oftype.isAssignableFrom(cause.getClass()))) {
            sneakyThrow(cause);
        }
        return Optional.empty();
    }

    /**
     * Re-throw the checked exception as runtime
     *
     * @param cause
     *            the exception to re-throw
     * @return
     */
    public static <T> T sneakyThrow(Exception cause) {
        SneakyThrow.<RuntimeException>sneakyThrowErase(cause);
        throw new AssertionError("Should never reach this point");
    }

    /**
     * We cast to the parameterized type T. At runtime, however, the generic types have been erased, so that there is no
     * T
     * type anymore to cast to, so the cast disappears.
     *
     * @param cause
     *            the checked exception to re-throw
     */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T sneakyThrowErase(Exception cause) throws T {
        if (cause instanceof InterruptedException) {
            // restore interruption state before re-throwing
            Thread.currentThread().interrupt();
        }
        if (cause instanceof RuntimeException) {
            // doesn't need erasure
            throw (RuntimeException) cause;
        }
        throw (T) cause;
    }

}
