package com.minihttp.config;

import com.minihttp.core.route.RoutePaths;
import lombok.NonNull;

import java.util.Optional;

public record OpenApiConfig(boolean enabled,
                            @NonNull String title,
                            @NonNull String version,
                            @NonNull Optional<String> description,
                            @NonNull String specPath,
                            @NonNull String docsPath) {

    public OpenApiConfig {
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        specPath = RoutePaths.normalize(specPath);
        docsPath = RoutePaths.normalize(docsPath);
        if (specPath.equals(docsPath)) {
            throw new IllegalArgumentException("specPath and docsPath must differ: " + specPath);
        }
        description = description.filter(text -> !text.isBlank());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static OpenApiConfig disabled() {
        return OpenApiConfig.builder().enabled(false).build();
    }

    public static final class Builder {
        private boolean enabled = true;
        private String title = "MiniHttp";
        private String version = "0.1.0";
        private String description;
        private String specPath = "/openapi.json";
        private String docsPath = "/docs";

        private Builder() {
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder title(@NonNull String title) {
            this.title = title;
            return this;
        }

        public Builder version(@NonNull String version) {
            this.version = version;
            return this;
        }

        public Builder description(@NonNull String description) {
            this.description = description;
            return this;
        }

        public Builder specPath(@NonNull String specPath) {
            this.specPath = specPath;
            return this;
        }

        public Builder docsPath(@NonNull String docsPath) {
            this.docsPath = docsPath;
            return this;
        }

        public OpenApiConfig build() {
            return new OpenApiConfig(
                    this.enabled,
                    this.title,
                    this.version,
                    Optional.ofNullable(this.description),
                    this.specPath,
                    this.docsPath
            );
        }
    }
}
