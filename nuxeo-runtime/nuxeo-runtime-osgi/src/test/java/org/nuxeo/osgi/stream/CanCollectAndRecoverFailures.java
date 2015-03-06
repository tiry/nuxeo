package org.nuxeo.osgi.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Collection;

import org.assertj.core.api.AssertDelegateTarget;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.nuxeo.common.trycompanion.Try;
import org.nuxeo.common.trycompanion.TryCompanion;

import com.tngtech.jgiven.Stage;
import com.tngtech.jgiven.annotation.ExpectedScenarioState;
import com.tngtech.jgiven.annotation.ExtendedDescription;
import com.tngtech.jgiven.annotation.ProvidedScenarioState;
import com.tngtech.jgiven.junit.ScenarioTest;

public class CanCollectAndRecoverFailures
        extends
        ScenarioTest<CanCollectAndRecoverFailures.Given, CanCollectAndRecoverFailures.When, CanCollectAndRecoverFailures.ThenFailure> {

    static class Given extends Stage<Given> {
        @ProvidedScenarioState
        TryCompanion<String> companion;

        @ProvidedScenarioState
        Logic logic = new Logic();

        @ProvidedScenarioState
        Collection<String> input;

        @ExtendedDescription("A try companion which process strings and catch Pfouh errors")
        Given aCompanion() {
            companion = TryCompanion.of(Logic.Pfouh.class);
            return self();
        }

        Given a_collection_of_strings(String... input) {
            this.input = Arrays.asList(input);
            return self();
        }

    }

    @Test
    public void finally_blocks_are_executed() {
        given().aCompanion().and().a_collection_of_strings("foo", "bar");
    }

    @Test
    public void strings_are_recoverable() {
        given().aCompanion().and().a_collection_of_strings("foo", "bar");
        when().I_collect_all_strings();
        then().this_is_a_failure().of_all_elements();

        when().I_recover_errors();
        addStage(ThenSuccess.class).this_is_a_success().of_all_elements();
    }

    static class When extends Stage<When> {
        @ExpectedScenarioState
        Logic logic;

        @ExpectedScenarioState
        TryCompanion<String> companion;

        @ExpectedScenarioState
        Collection<String> input;

        @ProvidedScenarioState
        Try<String> tryee;

        When I_collect_all_strings() {
            tryee = companion.successOf();
            tryee.sneakyMapAndCollect(input.stream(), logic::takeAnRun);
            return self();
        }

        When I_recover_errors() {
            tryee = tryee.recover(logic::recover);
            return self();
        }

    }

    static class ThenFailure extends Stage<ThenFailure> {
        @ExpectedScenarioState
        Logic logic;

        @ExpectedScenarioState
        Collection<String> input;

        @ExpectedScenarioState
        Try<String> tryee;

        ThenFailure this_is_a_failure(String... names) {
            TryAssert.assertThat(tryee).isFailure();
            return self();
        }

        ThenFailure of_all_elements() {
            assertThat(tryee.failed()
                    .map(cause -> (Logic.Pfouh) cause)
                    .map(cause -> cause.name)).containsAll(input);
            return this;
        }

    }

    static class ThenSuccess extends Stage<ThenSuccess> {
        @ExpectedScenarioState
        Logic logic;

        @ExpectedScenarioState
        Collection<String> input;

        @ExpectedScenarioState
        Try<String> tryee;

        ThenSuccess this_is_a_success() {
            assertThat(tryee.isSuccess()).isTrue();
            return self();
        }

        ThenSuccess of_all_elements() {
            assertThat(tryee.succeed()).containsAll(input);
            return this;
        }

    }

    static class TryAssert<T> implements AssertDelegateTarget {

        TryAssert(Try<T> actual) {
            this.actual = actual;
        }

        final Try<T> actual;

        static <T> TryAssert<T> assertThat(Try<T> actual) {
            return new TryAssert<>(actual);
        }

        void isSuccess() {
            Assertions.assertThat(actual.isSuccess()).isTrue();
        }

        void isFailure() {
            Assertions.assertThat(actual.isFailure()).isTrue();
        }
    }

    static class Logic {

        static class Pfouh extends Exception {

            private static final long serialVersionUID = 1L;

            final String name;

            Pfouh(String name) {
                super(name);
                this.name = name;
            }

        }

        String takeAnRun(String name) throws Pfouh {
            throw new Pfouh(name);
        }

        String run() throws Pfouh {
            return takeAnRun("pfouh");
        }

        void end() {

        }

        String recover(Exception cause) {
            return cause.getMessage();
        }

        Closeable with() {
            return null;
        }
    }

}
