/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     dmetzler
 */
package org.nuxeo.runtime.test.runner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.mockito.MockitoTestCase;
import org.nuxeo.runtime.test.mockito.RuntimeService;

/**
 * @since 5.8
 */

@RunWith(FeaturesRunner.class)
@Features({ RuntimeFeature.class })
public class CanMockServicesTest extends MockitoTestCase {

    @RuntimeService
    @Mock
    AFakeService myService;

    @Test
    public void itShouldBindMocktoAService() throws Exception {
        AFakeService service = Framework.getService(AFakeService.class);
        assertNotNull(service);
    }

    @Test
    public void itShouldMockFields() throws Exception {
        when(myService.getSomething()).thenReturn("Hello !");
        assertEquals("Hello !", myService.getSomething());
    }

    @Test
    public void canMockServletRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLength()).thenReturn(10);
        assertEquals(10, request.getContentLength());
        return;
    }
}
