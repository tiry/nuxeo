/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nicolas Chapurlat <nchapurlat@nuxeo.com>
 */

package org.nuxeo.ecm.core.api.validation;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.constraints.ConstraintViolation;

/**
 * Provides a way to validate {@link DocumentModel} according to their
 * {@link Schema}'s constraints.
 *
 * @since 7.1
 */
public interface DocumentValidationService {

    /**
     * Validates all {@link Field} of all {@link Schema} of a
     * {@link DocumentModel}. Including no dirty properties.
     *
     * @since 7.1
     */
    Set<ConstraintViolation> validate(DocumentModel document);

    /**
     * Validates all {@link Field} of all {@link Schema} of a
     * {@link DocumentModel}.
     * 
     * @param dirtyOnly If true, limit validation to dirty properties of the
     *            {@link DocumentModel}.
     *
     * @since 7.1
     */
    Set<ConstraintViolation> validate(DocumentModel document, boolean dirtyOnly);

    /**
     * Validates a value according to some {@link Field} definition.
     *
     * @since 7.1
     */
    Set<ConstraintViolation> validate(Field field, Object value);

    /**
     * Validates a value according to some {@link Field} definition.
     *
     * @since 7.1
     */
    Set<ConstraintViolation> validate(Schema schema, Field field, Object value);

    /**
     * Validates a property according to its {@link Field} definition.
     *
     * @since 7.1
     */
    Set<ConstraintViolation> validate(Property property);

    /**
     * Validates a value according to some {@link Field} definition.
     *
     * @since 7.1
     */
    Set<ConstraintViolation> validate(String schema, String field, Object value);

    /**
     * Validates a value according to some {@link Field} definition.
     * 
     * @param xpath schema:fieldName, for example dc:title.
     *
     * @since 7.1
     */
    Set<ConstraintViolation> validate(String xpath, Object value);

    /**
     * Validates a Map of value for some {@link Schema} fields. Entries which
     * not match any of the {@link Schema}'s {@link Field} will be ignored.
     * 
     * @param values The keys are the local {@link Field} name.
     *
     * @since 7.1
     */
    Set<ConstraintViolation> validate(String schema,
            Map<String, Serializable> values);

    /**
     * Validates a Map of value. Entries which not match any of all
     * {@link Schema}'s {@link Field} will be ignored.
     * 
     * @param values The keys are the xpath {@link Field} name.
     *
     * @since 7.1
     */
    Set<ConstraintViolation> validate(Map<String, Serializable> values);

}
