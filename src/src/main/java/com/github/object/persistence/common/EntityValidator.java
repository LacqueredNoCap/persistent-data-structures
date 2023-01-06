package com.github.object.persistence.common;

import com.github.object.persistence.common.utils.ReflectionUtils;
import com.github.object.persistence.common.utils.StringUtils;
import com.github.object.persistence.exception.ValidationException;
import com.github.object.persistence.sql.types.TypeMapper;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class EntityValidator {
    private static final EntityValidator INSTANCE = new EntityValidator();

    private EntityValidator() {
    }

    public static EntityValidator getInstance() {
        return INSTANCE;
    }

    public void validateEntity(Class<?> entity) {
        validateClass(entity);
        validateIds(entity);
        for (Field field : entity.getDeclaredFields()) {
            validateField(field);
        }
    }

    private void validateClass(Class<?> entity) {
        String message;
        if (!entity.isAnnotationPresent(Entity.class)) {
            message = String.format("Using %s class without Entity annotation", entity.getSimpleName());
            throw new ValidationException(message);
        }
    }

    private void validateIds(Class<?> entity) {
        List<Field> ids = ReflectionUtils.getIds(entity);
        String message;
        if (ids.isEmpty()) {
            message = String.format("Id in entity class %s is not present", entity.getSimpleName());
            throw new ValidationException(message);
        }
        if (ids.size() != 1) {
            message = String.format("Using %s class with several ids", entity.getSimpleName());
            throw new ValidationException(message);
        }
    }

    private void validateField(Field fieldType) {
        validateSingleRelation(fieldType);
        Class<?> fieldClass = fieldType.getType();
        if (Collection.class.isAssignableFrom(fieldClass)) {
            validateCollectionField(fieldType);
        }

        if (fieldClass.isAnnotationPresent(Entity.class)) {
            validateEntity(fieldType);
        } else {
            TypeMapper.validateSupportedType(fieldClass, prepareException(fieldType, "Current type is not entity or supported"));
        }
    }

    private void validateCollectionField(Field fieldType) {
        Class<?> itemClass = ReflectionUtils.getGenericType(fieldType);
        if (!itemClass.isAnnotationPresent(Entity.class)) {
            throwWithValidationMessage(fieldType, "Using non-entity class as Collection parameter");
        }
        if (!fieldType.isAnnotationPresent(OneToMany.class)) {
            throwWithValidationMessage(fieldType, "List of entities without relation annotation");
        } else {
            validateOneToMany(fieldType);
        }
    }

    private void validateEntity(Field fieldType) {
        if (fieldType.isAnnotationPresent(OneToMany.class)) {
            throwWithValidationMessage(fieldType, "Single entity can't be OneToMany relation. Use ManyToOne or OneToOne instead");
        }
        if (!fieldType.isAnnotationPresent(OneToOne.class)) {
            if (!fieldType.isAnnotationPresent(ManyToOne.class)) {
                throwWithValidationMessage(fieldType, "Entity property without relation annotation");
            }
        } else {
            validateOneToOne(fieldType);
        }
    }

    private void validateOneToMany(Field fieldType) {
        OneToMany oneToMany = fieldType.getAnnotation(OneToMany.class);
        String mappedBy = oneToMany.mappedBy();
        if (StringUtils.isBlank(mappedBy)) {
            long relatedClasses = Arrays.stream(fieldType.getType().getDeclaredFields())
                    .filter(field -> field.getType().equals(fieldType.getDeclaringClass())).count();
            if (relatedClasses == 0) {
                throwWithValidationMessage(fieldType, "One directional OneToMany relation is not supported");
            }
            if (relatedClasses > 1) {
                throwWithValidationMessage(fieldType, "Target class contains more then one relation. Set \"mappedBy\" property to OneToMany annotation");
            }
        } else {
            validateMappedBy(fieldType, mappedBy);
        }
    }

    private void validateOneToOne(Field fieldType) {
        OneToOne oneToMany = fieldType.getAnnotation(OneToOne.class);
        String mappedBy = oneToMany.mappedBy();
        if (!StringUtils.isBlank(mappedBy)) {
            validateMappedBy(fieldType, mappedBy);
        }
    }

    private void validateSingleRelation(Field fieldType) {
        boolean onlyOneToOne = fieldType.isAnnotationPresent(OneToOne.class) &&
                (fieldType.isAnnotationPresent(OneToMany.class) || fieldType.isAnnotationPresent(ManyToOne.class));
        boolean onlyManyToOne = fieldType.isAnnotationPresent(ManyToOne.class) && (
                fieldType.isAnnotationPresent(OneToOne.class) || fieldType.isAnnotationPresent(OneToMany.class));
        boolean onlyOneToMany = fieldType.isAnnotationPresent(OneToMany.class) && (
                fieldType.isAnnotationPresent(OneToOne.class) || fieldType.isAnnotationPresent(ManyToOne.class));

        if (onlyOneToOne || onlyManyToOne || onlyOneToMany) {
            throwWithValidationMessage(fieldType, "Use only one relation annotation");
        }
    }

    private void validateMappedBy(Field fieldType, String mappedBy) {
        try {
            Field targetField = fieldType.getType().getDeclaredField(mappedBy);
            if (!targetField.getType().equals(fieldType.getDeclaringClass())) {
                throwWithValidationMessage(fieldType, "In target class \"mappedBy\" wrong type");
            }
        } catch (NoSuchFieldException e) {
            throwWithValidationMessage(fieldType, "In target class \"mappedBy\" field is not present");
        }
    }

    private void throwWithValidationMessage(Field fieldType, String message) {
        throw prepareException(fieldType, message);
    }

    private ValidationException prepareException(Field fieldType, String message) {
        String errorMessage = String.format(
                "Error while processing field %s in class %s: %s",
                fieldType.getName(),
                fieldType.getDeclaringClass().getSimpleName(),
                message
        );

        return new ValidationException(errorMessage);
    }
}
