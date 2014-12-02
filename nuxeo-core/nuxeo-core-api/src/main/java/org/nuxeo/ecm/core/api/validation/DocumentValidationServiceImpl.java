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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.api.DataModel;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.schema.DocumentType;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.ComplexType;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.QName;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.ConstraintViolation;
import org.nuxeo.ecm.core.schema.types.constraints.NotNullConstraint;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

public class DocumentValidationServiceImpl extends DefaultComponent implements
        DocumentValidationService {

    private SchemaManager schemaManager;

    @Override
    public void activate(ComponentContext context) {
        super.activate(context);
        this.schemaManager = Framework.getService(SchemaManager.class);
    }

    @Override
    public Set<ConstraintViolation> validate(DocumentModel document) {
        return this.validate(document, false);
    }

    @Override
    public Set<ConstraintViolation> validate(DocumentModel document,
            boolean dirtyOnly) {
        Set<ConstraintViolation> violations = new HashSet<ConstraintViolation>();
        DocumentType docType = document.getDocumentType();
        if (dirtyOnly) {
            for (DataModel dataModel : document.getDataModels().values()) {
                Schema schemaDef = docType.getSchema(dataModel.getSchema());
                for (String fieldName : dataModel.getDirtyFields()) {
                    Field field = schemaDef.getField(fieldName);
                    Property property = document.getProperty(field.getName().getPrefixedName());
                    violations.addAll(this.validate(property));
                }
            }
        } else {
            for (Schema schema : docType.getSchemas()) {
                for (Field field : schema.getFields()) {
                    Property property = document.getProperty(field.getName().getPrefixedName());
                    violations.addAll(this.validate(property));
                }
            }
        }
        return violations;
    }

    public Set<ConstraintViolation> validate(DataModel data) {
        return this.validate(data, false);
    }

    public Set<ConstraintViolation> validate(DataModel data, boolean dirtyOnly) {
        Set<ConstraintViolation> violations = new HashSet<ConstraintViolation>();
        Schema schemaDef = this.schemaManager.getSchema(data.getSchema());
        if (dirtyOnly) {
            for (String fieldName : data.getDirtyFields()) {
                Field field = schemaDef.getField(fieldName);
                violations.addAll(this.validate(schemaDef, field,
                        data.getData(fieldName)));
            }
        } else {
            for (Field field : schemaDef.getFields()) {
                String fieldName = field.getName().getLocalName();
                Object value = data.getData(fieldName);
                violations.addAll(this.validate(schemaDef, field, value));
            }
        }
        return violations;
    }

    @Override
    public Set<ConstraintViolation> validate(Field field, Object value) {
        String prefix = field.getName().getPrefix();
        Schema schema = this.schemaManager.getSchemaFromPrefix(prefix);
        return this.validate(schema, field, value);
    }

    @Override
    public Set<ConstraintViolation> validate(Schema schema, Field field,
            Object value) {
        return this.validateAnyTypeField(schema, new ArrayList<Field>(), field,
                value);
    }

    @Override
    public Set<ConstraintViolation> validate(Property property) {
        return this.validateAnyTypeProperty(property.getSchema(),
                new ArrayList<Field>(), property);
    }

    @Override
    public Set<ConstraintViolation> validate(String schema, String field,
            Object value) {
        Schema schemaDef = this.schemaManager.getSchema(schema);
        Field fieldDef = schemaDef.getField(field);
        return this.validate(schemaDef, fieldDef, value);
    }

    @Override
    public Set<ConstraintViolation> validate(String xpath, Object value) {
        QName name = QName.valueOf(xpath);
        Schema schemaDef = this.schemaManager.getSchemaFromPrefix(name.getPrefix());
        Field fieldDef = schemaDef.getField(name.getLocalName());
        return this.validate(schemaDef, fieldDef, value);
    }

    @Override
    public Set<ConstraintViolation> validate(String schema,
            Map<String, Serializable> values) {
        Set<ConstraintViolation> violations = new HashSet<ConstraintViolation>();
        Schema schemaDef = this.schemaManager.getSchema(schema);
        for (Map.Entry<String, Serializable> entry : values.entrySet()) {
            Field field = schemaDef.getField(entry.getKey());
            if (field != null) {
                Object value = entry.getValue();
                violations.addAll(this.validate(schemaDef, field, value));
            }
        }
        return violations;
    }

    @Override
    public Set<ConstraintViolation> validate(Map<String, Serializable> values) {
        Set<ConstraintViolation> violations = new HashSet<ConstraintViolation>();
        for (Map.Entry<String, Serializable> entry : values.entrySet()) {
            violations.addAll(this.validate(entry.getKey(), entry.getValue()));
        }
        return violations;
    }

    // ////////////////////////////
    // Exploration based on Fields

    /**
     * @since 7.1
     */
    private Set<ConstraintViolation> validateAnyTypeField(Schema schema,
            List<Field> path, Field field, Object value) {
        List<Field> subPath = new ArrayList<Field>(path);
        subPath.add(field);
        if (field.getType().isSimpleType()) {
            return this.validateSimpleTypeField(schema, subPath, field, value);
        } else if (field.getType().isComplexType()) {
            return this.validateComplexTypeField(schema, subPath, field, value);
        } else if (field.getType().isListType()) {
            return this.validateListTypeField(schema, subPath, field, value);
        }
        // unrecognized type : ignored
        return new HashSet<ConstraintViolation>();
    }

    /**
     * This method should be the only one to create {@link ConstraintViolation}.
     *
     * @since 7.1
     */
    private Set<ConstraintViolation> validateSimpleTypeField(Schema schema,
            List<Field> path, Field field, Object value) {
        assert field.getType().isSimpleType();
        Set<ConstraintViolation> violations = new HashSet<ConstraintViolation>();
        for (Constraint constraint : field.getConstraints()) {
            if (!constraint.validate(value)) {
                ConstraintViolation violation = new ConstraintViolation(schema,
                        path, constraint, value);
                violations.add(violation);
            }
        }
        return violations;
    }

    /**
     * @since 7.1
     */
    private Set<ConstraintViolation> validateComplexTypeField(Schema schema,
            List<Field> path, Field field, Object value) {
        assert field.getType().isComplexType();
        Set<ConstraintViolation> violations = new HashSet<ConstraintViolation>();
        ComplexType complexType = (ComplexType) field.getType();
        // this code does not support other type than Map as value
        if (value != null && !(value instanceof Map)) {
            return violations;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        if (value == null || map.isEmpty()) {
            if (!field.isNillable()) {
                addNotNullViolation(violations, schema, path);
            }
        } else {
            for (Field child : complexType.getFields()) {
                Object item = map.get(child.getName().getLocalName());
                violations.addAll(this.validateAnyTypeField(schema, path,
                        child, item));
            }
        }
        return violations;
    }

    /**
     * @since 7.1
     */
    private Set<ConstraintViolation> validateListTypeField(Schema schema,
            List<Field> path, Field field, Object value) {
        assert field.getType().isListType();
        Set<ConstraintViolation> violations = new HashSet<ConstraintViolation>();
        Collection<?> castedValue = null;
        if (value instanceof Collection) {
            castedValue = (Collection<?>) value;
        } else if (value instanceof Object[]) {
            castedValue = Arrays.asList((Object[]) value);
        }
        if (castedValue != null) {
            ListType listType = (ListType) field.getType();
            Field listField = listType.getField();
            for (Object item : (Collection<?>) value) {
                violations.addAll(this.validateAnyTypeField(schema, path,
                        listField, item));
            }
            return violations;
        }
        return violations;
    }

    // //////////////////////////////
    // Exploration based on Property

    /**
     * @since 7.1
     */
    private Set<ConstraintViolation> validateAnyTypeProperty(Schema schema,
            List<Field> path, Property prop) {
        Field field = prop.getField();
        List<Field> subPath = new ArrayList<Field>(path);
        subPath.add(field);
        if (field.getType().isSimpleType()) {
            return this.validateSimpleTypeProperty(schema, subPath, prop);
        } else if (field.getType().isComplexType()) {
            return this.validateComplexTypeProperty(schema, subPath, prop);
        } else if (field.getType().isListType()) {
            return this.validateListTypeProperty(schema, subPath, prop);
        }
        // unrecognized type : ignored
        return new HashSet<ConstraintViolation>();
    }

    /**
     * @since 7.1
     */
    private Set<ConstraintViolation> validateSimpleTypeProperty(Schema schema,
            List<Field> path, Property prop) {
        Field field = prop.getField();
        assert field.getType().isSimpleType();
        Set<ConstraintViolation> violations = new HashSet<ConstraintViolation>();
        Serializable value = prop.getValue();
        if (prop.isPhantom() || value == null) {
            if (!field.isNillable()) {
                addNotNullViolation(violations, schema, path);
            }
        } else {
            violations.addAll(this.validateSimpleTypeField(schema, path, field,
                    value));
        }
        return violations;
    }

    /**
     * @since 7.1
     */
    private Set<ConstraintViolation> validateComplexTypeProperty(Schema schema,
            List<Field> path, Property prop) {
        Field field = prop.getField();
        assert field.getType().isComplexType();
        Set<ConstraintViolation> violations = new HashSet<ConstraintViolation>();
        boolean allChildrenPhantom = true;
        for (Property child : prop.getChildren()) {
            if (!child.isPhantom()) {
                allChildrenPhantom = false;
                break;
            }
        }
        Object value = prop.getValue();
        if (prop.isPhantom() || value == null || allChildrenPhantom) {
            if (!field.isNillable()) {
                addNotNullViolation(violations, schema, path);
            }
        } else {
            // this code does not support other type than Map as value
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> castedValue = (Map<String, Object>) value;
                if (value == null || castedValue.isEmpty()) {
                    if (!field.isNillable()) {
                        addNotNullViolation(violations, schema, path);
                    }
                } else {
                    for (Property child : prop.getChildren()) {
                        violations.addAll(this.validateAnyTypeProperty(schema,
                                path, child));
                    }
                }
            }
        }
        return violations;
    }

    /**
     * @since 7.1
     */
    private Set<ConstraintViolation> validateListTypeProperty(Schema schema,
            List<Field> path, Property prop) {
        Field field = prop.getField();
        assert field.getType().isListType();
        Set<ConstraintViolation> violations = new HashSet<ConstraintViolation>();
        Serializable value = prop.getValue();
        if (prop.isPhantom() || value == null) {
            if (!field.isNillable()) {
                addNotNullViolation(violations, schema, path);
            }
        } else {
            Collection<?> castedValue = null;
            if (value instanceof Collection) {
                castedValue = (Collection<?>) value;
            } else if (value instanceof Object[]) {
                castedValue = Arrays.asList((Object[]) value);
            }
            if (castedValue != null) {
                for (Property child : prop.getChildren()) {
                    violations.addAll(this.validateAnyTypeProperty(schema,
                            path, child));
                }
            }
        }
        return violations;
    }

    // //////
    // Utils

    private void addNotNullViolation(Set<ConstraintViolation> violations,
            Schema schema, List<Field> fieldPath) {
        violations.add(new ConstraintViolation(schema, fieldPath,
                NotNullConstraint.get(), null));
    }

}
