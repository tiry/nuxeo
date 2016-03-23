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

import static org.nuxeo.ecm.core.storage.marklogic.MarkLogicStateSerializer.SERIALIZER;

import java.io.Serializable;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.lang.StringUtils;
import org.nuxeo.ecm.core.api.model.Delta;
import org.nuxeo.ecm.core.storage.State.ListDiff;
import org.nuxeo.ecm.core.storage.State.StateDiff;

import com.marklogic.client.document.DocumentMetadataPatchBuilder.PatchHandle;
import com.marklogic.client.document.DocumentPatchBuilder;
import com.marklogic.client.document.DocumentPatchBuilder.Position;

/**
 * Builder to convert a {@link StateDiff} into a {@link PatchHandle}.
 *
 * @since 8.2
 */
class MarkLogicUpdateBuilder implements Function<StateDiff, PatchHandle> {

    private final Supplier<DocumentPatchBuilder> supplier;

    public MarkLogicUpdateBuilder(Supplier<DocumentPatchBuilder> supplier) {
        this.supplier = supplier;
    }

    @Override
    public PatchHandle apply(StateDiff diff) {
        DocumentPatchBuilder patchBuilder = supplier.get();
        fillPatch(patchBuilder, "", diff);
        return patchBuilder.build();
    }

    private void fillPatch(DocumentPatchBuilder patchBuilder, String path, StateDiff diff) {
        for (Entry<String, Serializable> entry : diff.entrySet()) {
            String subPath = path + "/node('" + entry.getKey() + "')";
            Serializable value = entry.getValue();
            if (value instanceof StateDiff) {
                fillPatch(patchBuilder, subPath, (StateDiff) value);
            } else if (value instanceof ListDiff) {

            } else if (value instanceof Delta) {

            } else {
                Optional<String> fragment = SERIALIZER.getValueSerializer().apply(value);
                if (fragment.isPresent()) {
                    patchBuilder.replaceInsertFragment(subPath, StringUtils.defaultIfBlank(path, "/"), Position.AFTER,
                            fragment.get());
                } else {
                    patchBuilder.delete(subPath);
                }
            }
        }
    }

}
