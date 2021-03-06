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
package org.nuxeo.ecm.restapi.server;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;

/**
 * @since 5.8
 */
public class URLUtilsTest {

    HttpServletRequest req = mock(HttpServletRequest.class);

    HttpServletResponse resp = mock(HttpServletResponse.class);

    @Before
    public void doBefore() {
        when(req.getRequestDispatcher(anyString())).thenReturn(mock(RequestDispatcher.class));
    }

    @Test
    public void spaceAreEncodedInUrls() throws Exception {

        when(req.getPathInfo()).thenReturn("/path/doc with space/");

        APIServlet servlet = new APIServlet();
        servlet.service(req, resp);

        verify(req).getRequestDispatcher("/site/api/path/doc%20with%20space/");

    }

    @Test
    public void arobasAreNotEncodediInUrls() throws Exception {
        when(req.getPathInfo()).thenReturn("/path/default-domain/@children");

        APIServlet servlet = new APIServlet();
        servlet.service(req, resp);

        verify(req).getRequestDispatcher("/site/api/path/default-domain/@children");
    }
}
