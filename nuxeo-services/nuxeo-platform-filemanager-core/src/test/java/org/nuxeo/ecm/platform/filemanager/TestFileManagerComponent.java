/*
 * (C) Copyright 2006-2007 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 *
 * $Id: JOOoConvertPluginImpl.java 18651 2007-05-13 20:28:53Z sfermigier $
 */

package org.nuxeo.ecm.platform.filemanager;

import java.util.List;

import javax.inject.Inject;

import org.junit.Test;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
import org.nuxeo.ecm.platform.filemanager.service.FileManagerService;
import org.nuxeo.ecm.platform.filemanager.service.extension.FileImporter;
import org.nuxeo.runtime.test.NXRuntimeTestCase;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.LocalDeploy;

@Deploy({"org.nuxeo.ecm.core.mimetype", "org.nuxeo.ecm.platform.query.api", "org.nuxeo.ecm.platform.filemanager.core"})
@LocalDeploy("org.nuxeo.ecm.platform.filemanager.core:nxfilemanager-test-contribs.xml")
public class TestFileManagerComponent extends NXRuntimeTestCase {

    @Inject
    private FileManager fm;


    @Test
    public void testPlugins() {
        FileImporter testPlu = ((FileManagerService)fm).getPluginByName("plug");
        List<String> filters = testPlu.getFilters();
        assertEquals(2, filters.size());
    }

}
