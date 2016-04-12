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

import java.util.function.Function;

/**
 * MarkLogic helper centralizes all common actions needed in different services.
 *
 * @since 8.2
 */
class MarkLogicHelper {

    public static final Function<String, String> ID_FORMATTER = id -> String.format("/%s.json", id);

    public static final String SCHEMA_ORIGINAL_DELIMITER = ":";

    public static final String SCHEMA_MARKLOGIC_DELIMITER = "__";

    public static final Function<String, String> KEY_SERIALIZER = key -> key.replace(SCHEMA_ORIGINAL_DELIMITER,
            SCHEMA_MARKLOGIC_DELIMITER);

    public static final Function<String, String> KEY_DESERIALIZER = key -> key.replace(SCHEMA_MARKLOGIC_DELIMITER,
            SCHEMA_ORIGINAL_DELIMITER);

}
