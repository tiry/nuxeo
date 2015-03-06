/*
 * (C) Copyright 2012-2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Sun Seng David TAN <stan@nuxeo.com>, slacoin, jcarsique
 */
package org.nuxeo.runtime.test.runner;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.junit.Assert;
import org.junit.runners.model.FrameworkMethod;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Test feature to capture from a log4j appender to check that some log4j calls have been correctly called.</br> On a
 * test class or a test method using this feature, a custom {@link LogCaptureFeature.Filter} class is to be provided
 * with the annotation {@link LogCaptureFeature.FilterWith} to select the log events to capture.</br> A
 * {@link LogCaptureFeature.Result} instance is to be injected with {@link Inject} as an attribute of the test.</br> The
 * method {@link LogCaptureFeature.Result#assertHasEvent()} can then be called from test methods to check that matching
 * log calls (events) have been captured.
 *
 * @since 5.7
 */
public class LogCaptureFeature extends SimpleFeature {

    public static class NoLogCaptureFilterException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    public @interface FilterWith {
        /**
         * Custom implementation of a filter to select event to capture.
         */
        Class<? extends LogCaptureFeature.Filter> value();
    }

    public static class Result {
        protected final ArrayList<ILoggingEvent> caughtEvents = new ArrayList<>();

        protected boolean noFilterFlag = false;

        public void assertHasEvent() throws NoLogCaptureFilterException {
            if (noFilterFlag) {
                throw new LogCaptureFeature.NoLogCaptureFilterException();
            }
            Assert.assertFalse("No log result found", caughtEvents.isEmpty());
        }

        public void clear() {
            caughtEvents.clear();
            noFilterFlag = false;
        }

        public List<ILoggingEvent> getCaughtEvents() {
            return caughtEvents;
        }

        protected void setNoFilterFlag(boolean noFilterFlag) {
            this.noFilterFlag = noFilterFlag;
        }
    }

    public interface Filter {
        /**
         * {@link LogCaptureFeature} will capture the event if it does match the implementation condition.
         */
        boolean accept(ILoggingEvent event);
    }

    protected Filter filter;

    protected final Result myResult = new Result();

    protected final Appender appender = new Appender();

    class Appender extends AppenderBase<ILoggingEvent> {

        @Override
        protected void append(ILoggingEvent event) {
            if (filter == null) {
                myResult.setNoFilterFlag(true);
                return;
            }
            if (filter.accept(event)) {
                myResult.caughtEvents.add(event);
            }
        }
    };

    private Filter setupFilter;

    @Override
    public void configure(FeaturesRunner runner, com.google.inject.Binder binder) {
        binder.bind(Result.class).toInstance(myResult);
    };

    protected Logger getRootLogger() {
        return (Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    @Override
    public void beforeSetup(FeaturesRunner runner) throws Exception {
        super.beforeSetup(runner);
        FilterWith filterProvider = runner.getConfig(FilterWith.class);
        if (filterProvider.value() == null) {
            return;
        }
        Class<? extends Filter> filterClass = filterProvider.value();
        enable(filterClass);
    }

    @Override
    public void afterTeardown(FeaturesRunner runner) throws Exception {
        disable();
    }

    @Override
    public void beforeMethodRun(FeaturesRunner runner, FrameworkMethod method, Object test) throws Exception {
        FilterWith filterProvider = runner.getConfig(method, FilterWith.class);
        if (filterProvider.value() == null) {
            return;
        }
        Class<? extends Filter> filterClass = filterProvider.value();
        enable(filterClass);
    }

    @Override
    public void afterMethodRun(FeaturesRunner runner, FrameworkMethod method, Object test) throws Exception {
        disable();
    }


    /**
     * @since 6.0
     */
    protected void enable(Class<? extends Filter> filterClass) throws InstantiationException, IllegalAccessException {
        if (filter != null) {
            setupFilter = filter;
        } else {
            appender.start();
            getRootLogger().addAppender(appender);
        }
        filter = filterClass.newInstance();
    }

    /**
     * @since 6.0
     */
    protected void disable() {
        if (setupFilter != null) {
            filter = setupFilter;
            setupFilter = null;
            return;
        }
        if (filter != null) {
            myResult.clear();
            appender.stop();
            getRootLogger().detachAppender(appender);
            filter = null;
        }
    }

}
