package com.iskolar.server.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.Map;

/**
 * Accepts {@code ISC_DB_URL} in either {@code postgres://…} or
 * {@code postgresql://…} form (the shape Neon/Render hand out) and rewrites it
 * into {@code spring.datasource.url} with the {@code jdbc:} scheme Spring needs.
 *
 * <p>Registered via {@code META-INF/spring.factories} so it runs before any
 * DataSource is created. When the variable is absent, nothing changes and the
 * {@code application.yml} default applies.</p>
 */
public final class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String RAW_URL_KEY = "ISC_DB_URL";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        String raw = environment.getProperty(RAW_URL_KEY);
        if (raw == null || raw.isBlank()) {
            return;
        }
        String jdbcUrl = normalize(raw.trim());
        MutablePropertySources sources = environment.getPropertySources();
        sources.addFirst(new MapPropertySource("iskolarDatabaseUrl",
                Map.of("spring.datasource.url", jdbcUrl)));
    }

    static String normalize(String url) {
        if (url.startsWith("postgres://")) {
            return "jdbc:postgresql://" + url.substring("postgres://".length());
        }
        if (url.startsWith("postgresql://")) {
            return "jdbc:postgresql://" + url.substring("postgresql://".length());
        }
        return url;
    }
}
