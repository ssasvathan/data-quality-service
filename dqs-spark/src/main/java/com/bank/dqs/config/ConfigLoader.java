package com.bank.dqs.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads application properties with environment-specific overlay and
 * {@code ${VAR}} / {@code ${VAR:default}} placeholder resolution.
 *
 * <h3>Loading order (later wins):</h3>
 * <ol>
 *   <li>{@code application.properties} — base config</li>
 *   <li>{@code application-{env}.properties} — environment overlay (if {@code DQS_ENV} or
 *       system property {@code dqs.env} is set)</li>
 * </ol>
 *
 * <h3>Placeholder resolution:</h3>
 * <p>Property values may contain {@code ${VAR}} or {@code ${VAR:default}} tokens.
 * Resolution order: Java system properties → OS environment variables → default value.
 * If no value is found and no default is provided, the raw placeholder is preserved
 * and a warning is logged.</p>
 */
public final class ConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

    /** Matches ${VAR} or ${VAR:default_value} — non-greedy, no nesting. */
    static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private ConfigLoader() {}

    /**
     * Load properties using classpath-first, filesystem-fallback strategy,
     * with environment overlay and placeholder resolution.
     *
     * @param configCandidates filesystem directories to search for property files
     *                         (e.g., {@code Path.of("config")}, {@code Path.of("dqs-spark/config")})
     * @return fully resolved properties
     */
    public static Properties load(List<Path> configCandidates) throws IOException {
        String env = resolveEnv();
        Properties props = new Properties();

        // 1. Load base application.properties
        loadLayer(props, "application.properties", configCandidates);

        // 2. Overlay environment-specific properties
        if (env != null && !env.isBlank()) {
            String envFile = "application-" + env + ".properties";
            boolean loaded = loadLayer(props, envFile, configCandidates);
            if (loaded) {
                LOG.info("Applied environment overlay: {}", envFile);
            } else {
                LOG.warn("DQS_ENV={} but {} was not found — using base config only", env, envFile);
            }
        }

        // 3. Resolve placeholders
        resolvePlaceholders(props);

        return props;
    }

    /**
     * Determine the active environment from system property {@code dqs.env}
     * or environment variable {@code DQS_ENV}. System property takes precedence.
     *
     * @return environment name (e.g., "dev", "prod") or {@code null} if not set
     */
    static String resolveEnv() {
        String env = System.getProperty("dqs.env");
        if (env == null || env.isBlank()) {
            env = System.getenv("DQS_ENV");
        }
        return env;
    }

    /**
     * Attempt to load a named properties file from classpath first, then from
     * filesystem candidate directories.
     *
     * @return {@code true} if the file was found and loaded from any source
     */
    static boolean loadLayer(Properties props, String filename, List<Path> candidates)
            throws IOException {
        // Classpath first
        try (InputStream is = ConfigLoader.class.getResourceAsStream("/" + filename)) {
            if (is != null) {
                props.load(is);
                LOG.debug("Loaded {} from classpath", filename);
                return true;
            }
        }

        // Filesystem fallback
        for (Path dir : candidates) {
            Path candidate = dir.resolve(filename);
            if (Files.isRegularFile(candidate) && Files.isReadable(candidate)) {
                try (InputStream is = Files.newInputStream(candidate)) {
                    props.load(is);
                    LOG.debug("Loaded {} from {}", filename, candidate.toAbsolutePath());
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Resolve {@code ${VAR}} and {@code ${VAR:default}} placeholders in all
     * property values. Resolution order: system properties → environment variables
     * → default value after colon. Unresolvable placeholders without defaults
     * are left as-is with a warning.
     */
    static void resolvePlaceholders(Properties props) {
        for (String key : props.stringPropertyNames()) {
            String raw = props.getProperty(key);
            String resolved = resolvePlaceholders(raw);
            if (!raw.equals(resolved)) {
                props.setProperty(key, resolved);
            }
        }
    }

    /**
     * Resolve all {@code ${...}} placeholders in a single string value.
     * Package-private for testability.
     */
    static String resolvePlaceholders(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String token = matcher.group(1);
            String varName;
            String defaultValue;

            int colonIdx = token.indexOf(':');
            if (colonIdx >= 0) {
                varName = token.substring(0, colonIdx);
                defaultValue = token.substring(colonIdx + 1);
            } else {
                varName = token;
                defaultValue = null;
            }

            String resolved = System.getProperty(varName);
            if (resolved == null) {
                resolved = System.getenv(varName);
            }
            if (resolved == null) {
                resolved = defaultValue;
            }

            if (resolved != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
            } else {
                LOG.warn("Unresolved placeholder: ${{{}}} in property value — "
                        + "set env var or system property '{}'", varName, varName);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
