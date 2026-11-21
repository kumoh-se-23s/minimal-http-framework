package com.minihttp.core.route.dependency.security;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum SecureSchemaType {
    BASIC("Basic", BasicSchema.class),
    BEARER("Bearer", BearerSchema.class);

    private final String prefix;
    private final Class<?> schemaClass;

    SecureSchemaType(String prefix, Class<?> schemaClass) {
        this.prefix = prefix;
        this.schemaClass = schemaClass;
    }

    public static Optional<SecureSchemaType> from(Class<?> dependencyType) {
        return Arrays.stream(SecureSchemaType.values())
                     .filter(schemaType -> schemaType.schemaClass.isAssignableFrom(dependencyType))
                     .findFirst();
    }

    public String getPrefix() {
        return this.prefix;
    }

    public String getOpenApiScheme() {
        return this.prefix.toLowerCase(Locale.ROOT);
    }

    public boolean isCorrectSchemaType(String maybeCorrectSchema) {
        return this.prefix.equalsIgnoreCase(maybeCorrectSchema);
    }
}
