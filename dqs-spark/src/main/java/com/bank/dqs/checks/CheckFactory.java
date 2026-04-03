package com.bank.dqs.checks;

import com.bank.dqs.model.DatasetContext;
import com.bank.dqs.model.DqsConstants;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry and factory for {@link DqCheck} implementations.
 *
 * <p>Instance-based — create a fresh {@code CheckFactory} per {@code DqsJob} run.
 * Do not share instances across threads.
 *
 * <p>Usage pattern in {@code DqsJob}:
 * <pre>{@code
 *   CheckFactory factory = new CheckFactory();
 *   factory.register(new FreshnessCheck());
 *   factory.register(new VolumeCheck());
 *   // ... register other checks
 *
 *   try (Connection conn = ...) {
 *       List<DqCheck> checks = factory.getEnabledChecks(datasetCtx, conn);
 *       // run checks, collect metrics
 *   }
 * }</pre>
 *
 * <p>The registry uses {@link LinkedHashMap} to preserve insertion order, which ensures
 * deterministic check execution order across runs.
 */
public final class CheckFactory {

    private final Map<String, DqCheck> registry = new LinkedHashMap<>();

    /**
     * Register a check implementation. Uses {@link DqCheck#getCheckType()} as the registry key.
     * Registering a second implementation with the same check_type overwrites the first.
     *
     * @param check the check implementation to register (must not be null)
     */
    public void register(DqCheck check) {
        if (check == null) {
            throw new IllegalArgumentException("check must not be null");
        }
        registry.put(check.getCheckType(), check);
    }

    /**
     * Returns the registered checks that are enabled for the given dataset context.
     *
     * <p>Queries {@code check_config} using a LIKE pattern match:
     * {@code dataset_name LIKE dataset_pattern}, which allows wildcard patterns such as
     * {@code "lob=retail/%"} to match all datasets under a LOB.
     *
     * <p>Only active rows are considered: {@code expiry_date = EXPIRY_SENTINEL} (from
     * {@link DqsConstants#EXPIRY_SENTINEL}). Only rows with {@code enabled = TRUE} are returned.
     *
     * <p>Check types returned by the DB query that are not registered in this factory
     * are silently ignored — this handles the case where a check_config row exists for
     * a check not yet deployed.
     *
     * @param ctx  the dataset context (provides the dataset_name for pattern matching)
     * @param conn an open JDBC connection to the database containing {@code check_config}
     * @return ordered list of enabled, registered checks (insertion order preserved)
     * @throws SQLException if the database query fails
     */
    public List<DqCheck> getEnabledChecks(DatasetContext ctx, Connection conn) throws SQLException {
        List<DqCheck> enabled = new ArrayList<>();
        String sql = "SELECT DISTINCT check_type FROM check_config "
                   + "WHERE ? LIKE dataset_pattern AND enabled = TRUE AND expiry_date = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ctx.getDatasetName());
            ps.setString(2, DqsConstants.EXPIRY_SENTINEL);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String checkType = rs.getString("check_type");
                    DqCheck check = registry.get(checkType);
                    if (check != null) {
                        enabled.add(check);
                    }
                }
            }
        }
        return enabled;
    }
}
