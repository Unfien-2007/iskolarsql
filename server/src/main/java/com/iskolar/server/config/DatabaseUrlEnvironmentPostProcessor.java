package com.iskolar.server.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Accepts {@code ISC_DB_URL} in either {@code postgres://…} or
 * {@code postgresql://…} form (the shape Neon/Render hand out) and rewrites it
 * into {@code spring.datasource.url} with the {@code jdbc:} scheme Spring needs.
 *
 * <p>Neon connection strings embed the credentials
 * ({@code postgresql://user:pass@host/db}). When {@code ISC_DB_USERNAME} /
 * {@code ISC_DB_PASSWORD} are not set, the credentials are extracted from the
 * URL into {@code spring.datasource.username} / {@code spring.datasource.password}
 * and stripped from the URL — so the {@code application.yml} defaults can never
 * override what the URL says. When the explicit variables are present they win,
 * and the URL passes through untouched (the JDBC driver prefers explicit
 * properties over URL userinfo).</p>
 *
 * <p>Registered via {@code META-INF/spring.factories} so it runs before any
 * DataSource is created. When {@code ISC_DB_URL} is absent, nothing changes and
 * the {@code application.yml} default applies.</p>
 */
public final class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String RAW_URL_KEY = "ISC_DB_URL";
    private static final String USERNAME_KEY = "ISC_DB_USERNAME";
    private static final String PASSWORD_KEY = "ISC_DB_PASSWORD";

    private static final String JDBC_PREFIX = "jdbc:postgresql://";
    private static final Pattern USERINFO = Pattern.compile("^(" + Pattern.quote(JDBC_PREFIX) + "[^/@]+)@(.+)$");

    /** A rewritten URL plus credentials extracted from it (null = leave to defaults). */
    record Normalized(String url, String username, String password) { }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        String raw = environment.getProperty(RAW_URL_KEY);
        if (raw == null || raw.isBlank()) {
            return;
        }
        boolean explicitUser = hasValue(environment.getProperty(USERNAME_KEY));
        boolean explicitPassword = hasValue(environment.getProperty(PASSWORD_KEY));
        Normalized normalized = normalize(raw.trim(), explicitUser, explicitPassword);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.datasource.url", normalized.url());
        if (normalized.username() != null) {
            props.put("spring.datasource.username", normalized.username());
        }
        if (normalized.password() != null) {
            props.put("spring.datasource.password", normalized.password());
        }
        MutablePropertySources sources = environment.getPropertySources();
        sources.addFirst(new MapPropertySource("iskolarDatabaseUrl", props));
    }

    static Normalized normalize(String url, boolean usernameExplicitlySet,
                                boolean passwordExplicitlySet) {
        String jdbcUrl;
        if (url.startsWith("postgres://")) {
            jdbcUrl = JDBC_PREFIX + url.substring("postgres://".length());
        } else if (url.startsWith("postgresql://")) {
            jdbcUrl = JDBC_PREFIX + url.substring("postgresql://".length());
        } else {
            jdbcUrl = url;
        }

        if (usernameExplicitlySet && passwordExplicitlySet) {
            return new Normalized(jdbcUrl, null, null);
        }

        Matcher matcher = USERINFO.matcher(jdbcUrl);
        if (!matcher.matches()) {
            return new Normalized(jdbcUrl, null, null);
        }

        String rawUserInfo = matcher.group(1).substring(JDBC_PREFIX.length());
        String rest = matcher.group(2);
        String[] parts = rawUserInfo.split(":", 2);
        String urlUser = decode(parts[0]);
        String urlPassword = parts.length > 1 ? decode(parts[1]) : "";
        return new Normalized(
                JDBC_PREFIX + rest,
                usernameExplicitlySet ? null : urlUser,
                passwordExplicitlySet ? null : urlPassword);
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return value;
        }
    }
}
