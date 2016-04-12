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

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.marklogic.client.admin.ExtensionMetadata;
import com.marklogic.client.admin.ExtensionMetadata.ScriptLanguage;
import com.marklogic.client.admin.MethodType;
import com.marklogic.client.admin.ResourceExtensionsManager.MethodParameters;

/**
 * A resource extension object.
 */
class MarkLogicResourceExtension {

    private String name;

    private final ExtensionMetadata metadata = new ExtensionMetadata();

    private final List<MethodParameters> methodParameters = new ArrayList<>();

    public String getName() {
        if (StringUtils.isNotBlank(name)) {
            return name;
        }
        return metadata.getTitle();
    }

    public File getResource() throws URISyntaxException {
        URI resourceURI = getClass().getResource(getName() + ".sjs").toURI();
        return Paths.get(resourceURI).toFile();
    }

    public ExtensionMetadata getMetadata() {
        return metadata;
    }

    public MethodParameters[] getMethodParameters() {
        return methodParameters.toArray(new MethodParameters[methodParameters.size()]);
    }

    protected void setName(String name) {
        this.name = name;
    }

    protected void setTitle(String title) {
        metadata.setTitle(title);
    }

    protected void setDescription(String description) {
        metadata.setDescription(description);
    }

    protected void setProvider(String provider) {
        metadata.setProvider(provider);
    }

    protected void setVersion(String version) {
        metadata.setVersion(version);
    }

    protected void setScriptLanguage(ScriptLanguage scriptLanguage) {
        metadata.setScriptLanguage(scriptLanguage);
    }

    protected void addMethodParam(MethodType type) {
        addMethodParam(new MethodParameters(type));
    }

    protected void addMethodParam(MethodParameters methodParams) {
        methodParameters.add(methodParams);
    }

}
