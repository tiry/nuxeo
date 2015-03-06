package org.nuxeo.common.trycompanion;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Represents the <i>failure</i> implementation of {@link Try}. <br>
 * Acts as a carrier for the result/throwable of a unsuccessful computation. <br>
 * For examples on usage refer to the documentation for {@link Try}.
 *
 * @param <T>
 *            The type of the exception represented by this instance
 */
public class Failure<T> implements Try<T> {

    /**
     * Creates a Failure for the provided causes. <br>
     *
     * @param cause
     *            The cause
     */
    Failure(Success<T> success, Collection<Exception> causes) {
        this.successes = success;
        this.failures = TryCompanion.<Exception>of(success).successOf(causes);
    }

    final Try<T> successes;

    final Try<Exception> failures;

    @Override
    public TryCompanion<T> companion() {
        return successes.companion();
    }

    @Override
    public Optional<T> get() {
        return successes.get();
    }

    @Override
    public Collection<T> collection() {
        return successes.collection();
    }

    @Override
    public T getOrThrow() throws Exception {
        return successes.getOrThrow();
    }

    /**
     * Always returns <code>false</code>.
     */
    @Override
    public boolean isSuccess() {
        return false;
    }

    /**
     * Always returns the value provided by the supplier. <br>
     * As per definition this is a failure without any data to return.
     */
    @Override
    public T getOrElse(Supplier<T> supplier) {
        return supplier.get();
    }

    /**
     * Always returns the value provided by the supplier. <br>
     * As per definition this is a failure without any data to return.
     */
    @Override
    public Try<T> orElse(Function<Try<T>, Try<T>> mapper) {
        return mapper.apply(this);
    }

    /**
     * Returns a {@link Success} with the {@link Exception} this instance represents.
     */
    @Override
    public Try<Exception> failed() {
        return failures;
    }

    /**
     * Returns empty, not a success
     */
    @Override
    public Try<T> succeed() {
        return successes;
    }

    /**
     * Filters the success part of this {@code Try}
     */
    @Override
    public Try<T> filter(Predicate<T> filter) {
        return successes.filter(filter);
    }

    /**
     * Transfers the error in {@code Failures} of type {@code R}
     */
    @Override
    public <R> Try<R> map(Function<T, R> function) {
        return TryCompanion.<R>of(successes)
                .tryOf(successes.map(function).collection(), failures.collection());
    }

    /**
     * Transfers the error in {@code Failure} of type {@code R}
     */
    @Override
    public <R> Try<R> flatMap(Function<T, Try<R>> function) {
        return TryCompanion.<R>of(successes)
                .tryOf(successes.flatMap(function).collection(), failures.collection());
    }

    /**
     * Maps the provided function to failures and merges the result with the successes.
     */
    @Override
    public Try<T> recover(Function<Exception, T> function) {
        return successes.companion().merge(successes, failures.map(function));
    }

    /**
     * Flat maps the provided function to failures and merges the result with the successes.
     */
    @Override
    public Try<T> recoverWith(Function<Exception, Try<T>> function) {
        return successes.companion().merge(successes, failures.flatMap(function));
    }

    @Override
    public <X extends Exception> Try<T> orElseThrow(Supplier<X> supplier) throws X {
        X error = supplier.get();
        failed().stream().forEach(cause -> error.addSuppressed(cause));
        throw error;
    }

    @Override
    public Try<T> onSuccess(Consumer<Try<T>> consumer) {
        return this;
    }

    @Override
    public Try<T> onFailure(Consumer<Try<T>> consumer) {
        consumer.accept(this);
        return this;
    }

    /**
     * Returns a String representation of the instance.
     */
    @Override
    public String toString() { // @formatter:off
    return new StringBuilder()
        .append("Failure@").append(Integer.toHexString(hashCode())).append("[")
        .append(successes)
        .append(", ")
        .append(failures)
        .append("]")
        .toString();
  } // @formatter:on
}