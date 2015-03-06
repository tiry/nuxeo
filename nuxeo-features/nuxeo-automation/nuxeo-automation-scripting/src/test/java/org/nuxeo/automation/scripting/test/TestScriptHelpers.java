/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Vladimir Pasquier <vpasquier@nuxeo.com>
 */
package org.nuxeo.automation.scripting.test;

import static org.junit.Assert.assertThat;

import static org.hamcrest.CoreMatchers.*;

import javax.inject.Inject;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.RuntimeService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.test.runner.LogCaptureFeature;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * @since 7.10
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy({ "org.nuxeo.ecm.automation.core", "org.nuxeo.ecm.automation.scripting" })
@LocalDeploy({ "org.nuxeo.ecm.automation.scripting.tests:automation-scripting-contrib.xml" })
public class TestScriptHelpers {

    @Inject
    CoreSession session;

    @Inject
    AutomationService automationService;

    protected RuntimeService runtime = Framework.getRuntime();

    public static class IsConsoleEvent implements LogCaptureFeature.Filter {

        @Override
        public boolean accept(ILoggingEvent event) {
            return "org.nuxeo.automation.scripting.Console".equals(event.getLoggerName());
        }

    }


    @Test
    @LogCaptureFeature.FilterWith(IsConsoleEvent.class)
    public void canUseConsoleHelper() throws OperationException {
        OperationContext ctx = new OperationContext(session);
        automationService.run(ctx, "Scripting.UseConsoleHelper", null);
        assertEvent(0, Level.WARN, "Warnings");
        assertEvent(1, Level.ERROR, "Errors");
        runtime.setProperty(Framework.NUXEO_DEV_SYSTEM_PROP, true);
        automationService.run(ctx, "Scripting.UseConsoleHelper", null);
        assertEvent(2, Level.INFO, "[INFO] Informations");
        assertEvent(3, Level.WARN, "Warnings");
        assertEvent(4, Level.ERROR, "Errors");
    }


    @Inject
    LogCaptureFeature.Result caughtEvents;


    void assertEvent(int index, Level level, String message) {
        assertThat(caughtEvents.getCaughtEvents().size(),  Matchers.greaterThan(index));
        ILoggingEvent event = caughtEvents.getCaughtEvents().get(index);
        assertThat(event.getLevel(), is(level));
        assertThat(event.getMessage(), is(message));
    }

}
