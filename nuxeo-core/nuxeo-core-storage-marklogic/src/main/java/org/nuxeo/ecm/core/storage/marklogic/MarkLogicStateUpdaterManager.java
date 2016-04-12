/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Kevin Leturc
 */
package org.nuxeo.ecm.core.storage.marklogic;

import org.nuxeo.ecm.core.storage.State.StateDiff;

import com.marklogic.client.admin.ExtensionMetadata.ScriptLanguage;
import com.marklogic.client.admin.MethodType;
import com.marklogic.client.extensions.ResourceManager;
import com.marklogic.client.io.StringHandle;
import com.marklogic.client.util.RequestParameters;

/**
 * @since 8.3
 */
class MarkLogicStateUpdaterManager extends ResourceManager {

    public static final MarkLogicResourceExtension DEFINITION;
    static {
        DEFINITION = new MarkLogicResourceExtension();
        DEFINITION.setName("nuxeo-state-updater");
        DEFINITION.setTitle("Nuxeo State Updater");
        DEFINITION.setDescription("Resource extension to update state");
        DEFINITION.setProvider("Nuxeo");
        DEFINITION.setVersion("0.1");
        DEFINITION.setScriptLanguage(ScriptLanguage.JAVASCRIPT);
        DEFINITION.addMethodParam(MethodType.PUT);
    }

    public void update(String id, StateDiff diff) {
        RequestParameters requestParameters = new RequestParameters();
        requestParameters.add("uri", MarkLogicHelper.ID_FORMATTER.apply(id));
        getServices().put(requestParameters, new StateHandle(diff), new StringHandle());
    }

}
