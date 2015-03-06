package org.nuxeo.common.trycompanion;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.nuxeo.common.trycompanion.SneakyThrow.ConsumerCheckException;
import org.nuxeo.common.trycompanion.SneakyThrow.FunctionCheckException;
import org.nuxeo.common.trycompanion.SneakyThrow.RunnableCheckException;
import org.nuxeo.common.trycompanion.SneakyThrow.SupplierCheckException;

/**
 * Scala way of handling errors
 *
 * @param <X> The error type
 * @param <T> The value type
 */
public interface Try<T> extends Container<T> {

    /**
     * Each try is associated with it's companion which is shared with other {@code Try}.
     *
     * @return this {@code TryCompanion}
     */
    TryCompanion<T> companion();

    @SuppressWarnings({ "unchecked", "rawtypes" })
    default <O> Try<O> outer() {
        return (Try) companion().originator;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    default <O> Try<O> outer(Class<O> typeof) {
        return (Try) companion().originator;
    }

    /**
     * Returns <code>true</code> if the <T>Try</T> is a {@link Failure}, <code>false</code> otherwise.
     *
     * @return If the Try is a {@link Failure}
     */
    default boolean isFailure() {
        return !isSuccess();
    }

    /**
     * Returns <code>true</code> if the <T>Try</T> is a {@link Success}, <code>false</code> otherwise.
     *
     * @return If the Try is a {@link Success}
     */
    boolean isSuccess();

    /**
     * Returns the value held by this {@code Try}.
     */
    @Override
    Optional<T> get();

    /**
     * Returns the value held by this {@code Try} or throws the cause if this is a failure.
     *
     * @return The optional value
     */
    <X extends Exception> T getOrThrow() throws X;

    /**
     * Returns the value from this {@code Try} or the value provided by the supplier if this is a failure.
     *
     * @param supplier The supplier to return the value in case of a failure
     * @return
     */
    T getOrElse(Supplier<T> supplier);

    /**
     * Returns this {@code Try} as an {@link Optional}. <br>
     * If it is a success then the value is wrapped in {@link Success} else {@link Failure} is returned. <br>
     * Should the success contain a <code>null</code> value the result will be {@link Optional.empty} as
     * <code>null</code> values are per definition <T>none/nothing</T>.
     *
     * @return The {@link Optional} representing this Try
     */
    default Optional<T> asOption() {
        return Optional.ofNullable(getOrElse(() -> null));
    }

    /**
     * Returns this <T>Try</T> if it's a success or the value provided by the supplier if this is a {@link Failure}.
     *
     * @param supplier The supplier to return the value in case of a failure
     * @return This try or the value from the supplier
     */
    Try<T> orElse(Function<Try<T>, Try<T>> mapper);

    /**
     * Completes this <T>Try</T> with an exception wrapped in a {@link Success} (if a {@link Failure}).
     *
     * @return The value of the {@link Failure} in a {@link Success} or a
     */
    Try<Exception> failed();

    /**
     * Completes this <T>Try</T> with a @{@link Success}.
     *
     * @return The value of the {@link Success}.
     */
    Try<T> succeed();

    /**
     * Applies the predicate to the value of the {@link Try} and either returns the Try if the predicate matched or a
     * {@link Failure}. <br>
     * One of the three outcomes are applicable:
     * <ul>
     * <li>Instance is {@link Success} and predicate matches -&gt; return <code>this</code></li>
     * <li>Instance is {@link Success} and predicate does not match -&gt; return {@link Failure}</li>
     * <li>Instance is {@link Failure} -&gt; return <code>this</code></li>
     * </ul>
     *
     * @param filter The filter to apply
     * @return The try matching the filter, either <code>this</code> if matching or a {@link Failure} in case no match
     */
    Try<T> filter(Predicate<T> filter);

    /**
     * Maps the given function to the value from this {@link Success} or returns <code>this</code> if this is a
     * {@link Failure}. <br>
     * This allows for mapping a {@link Try} containing some type to some completely different type. <br>
     *
     * @param <R> The type for the return value from the function
     * @param function The function to use
     * @return The Try containing the mapped value
     */
    <R> Try<R> map(Function<T, R> function);

    /**
     * Maps the given function to the value from this {@link Success} or returns <code>this</code> if this is a
     * {@link Failure}.
     *
     * @param <R> The type for the return value from the function
     * @param function The function to use
     * @return The Try containing the mapped value
     */
    <R> Try<R> flatMap(Function<T, Try<R>> function);

    /**
     * Creates a new {@link Try} that in case <code>this</code> {@link Try} is a {@link Failure} will apply the function
     * to recover to a {@link Success}. <br>
     * Should <code>this</code> be a {@link Success} then <code>this</code> is returned, i.e. no new instance is
     * created. <br>
     * This is a kind of {@link #map(Function)} for failures only.<br>
     *
     * @param function The function to apply in case of a {@link Failure}
     * @return The recovered Try
     */
    Try<T> recover(Function<Exception, T> function);

    /**
     * Creates a new {@link Try} that in case <code>this</code> {@link Try} is a {@link Failure} will apply the function
     * to recover the {@link Try} rendered by the function. <br>
     * Should <code>this</code> be a {@link Success} the value is propagated as-is. <br>
     * This is a kind of {@link #map(Function)} for failures only.<br>
     * In case of <code>this</code> being successful then that value is passed on to <code>recovered</code>, in case of
     * failure then the recover function kicks in and returns the a {@link Success} with message from the throwable.
     *
     * @param predicate The predicate to apply to each @{link Exception} for determine if it should be recovered
     * @param function The function to apply in case of a {@link Failure}
     * @return The recovered Try
     */
    Try<T> recoverWith(Function<Exception, Try<T>> function);

    Try<T> onFailure(Consumer<Try<T>> consumer);

    Try<T> onSuccess(Consumer<Try<T>> consumer);

    default <X extends Exception> Try<T> onFailure(Class<X> oftype, Consumer<X> consumer) {
        onFailure(cause -> {
            if (cause.getClass().isAssignableFrom(oftype)) {
                consumer.accept(oftype.cast(cause));
            }
        });
        return this;
    }

    <X extends Exception> Try<T> orElseThrow(Supplier<X> supplier) throws X;

    default <X extends Exception> Try<T> orElseThrow(Function<Try<T>, X> supplier) throws X {
        return orElseThrow(() -> supplier.apply(this));
    }

    default <X extends Exception> Try<T> orElseSummaryThrow(Function<String, X> supplier) throws X {
        return orElseThrow(self -> companion().summarize(self.failed(), supplier));
    }

    default Try<T> sneakyRun(RunnableCheckException runnable) {
        TryCompanion<T> companion = companion();
        return companion.merge(this, companion.sneakyRun(runnable));
    }

    default Try<T> run(Runnable runnable) {
        TryCompanion<T> companion = companion();
        return companion.merge(this, companion.run(runnable));
    }

    default Try<T> sneakyConsume(ConsumerCheckException<Try<T>> consumer) {
        TryCompanion<T> companion = companion();
        return companion.merge(this, companion.sneakyRun(() -> consumer.accept(this)));
    }

    default Try<T> consume(Consumer<Try<T>> consumer) {
        TryCompanion<T> companion = companion();
        return companion.merge(this, companion.run(() -> consumer.accept(this)));
    }

    default Try<T> sneakySupply(SupplierCheckException<T> supplier) {
        TryCompanion<T> companion = companion();
        return companion.merge(this, companion.sneakySupply(supplier));
    }

    default Try<T> supply(Supplier<T> supplier) {
        TryCompanion<T> companion = companion();
        return companion.merge(this, companion.supply(supplier));
    }

    default <I> Try<T> sneakyMapAndCollect(Stream<I> stream, FunctionCheckException<I, T> function) {
        TryCompanion<T> companion = companion();
        return companion.merge(this, companion.sneakyMapAndCollect(stream, function));
    }

    default <I> Try<T> mapAndCollect(Stream<I> stream, Function<I, T> function) {
        TryCompanion<T> companion = companion();
        return companion.merge(this, companion.mapAndCollect(stream, function));
    }

    default <I> Stream<Supplier<T>> sneakyMap(Stream<I> supplier, FunctionCheckException<I, T> function) {
        return companion().sneakyMap(supplier, function);
    }

    default <I> Stream<Supplier<T>> map(Stream<I> supplier, Function<I, T> function) {
        return companion().map(supplier, function);
    }

    @SuppressWarnings("unchecked")
    default <I> Try<Void> sneakyForEachAndCollect(Stream<I> stream, ConsumerCheckException<I> function) {
        TryCompanion<Void> companion = (TryCompanion<Void>) companion();
        Try<Void> left = (Try<Void>) this;
        Try<Void> right = companion.sneakyForEachAndCollect(stream, function);
        return companion.merge(left, right);
    }

    @SuppressWarnings("unchecked")
    default <I> Try<Void> forEachAndCollect(Stream<I> stream, Consumer<I> function) {
        TryCompanion<Void> companion = (TryCompanion<Void>) companion();
        Try<Void> left = (Try<Void>) this;
        Try<Void> right = companion.forEachAndCollect(stream, function);
        return companion.merge(left, right);
    }

    default <I> Stream<Supplier<Void>> forEach(Stream<I> supplier, Consumer<I> function) {
        return companion().forEach(supplier, function);
    }

    default Try<T> peek(Consumer<Try<T>> consumer) {
        consumer.accept(this);
        return this;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    default <O> Try<O> reduce(FunctionCheckException<Try<T>, O> reducer) {
        return ((Try) companion().originator).sneakySupply(() -> reducer.apply(this));
    }

}