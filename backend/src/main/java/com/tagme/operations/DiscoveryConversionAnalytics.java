package com.tagme.operations;

import com.tagme.auth.AuthPrincipal;
import com.tagme.auth.AuthRateLimiter;
import com.tagme.participation.ParticipationRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DiscoveryConversionAnalytics {
    public record Step(String code, long users, Double previousConversionRate, Double completionConversionRate) {}
    public record Report(String basis, Instant generatedAt, List<Step> steps) {}

    // Only current result rows are read. Resets/revocations remove those rows and archive
    // them separately; unrelated mission adjustments must not invalidate a retained result.
    private static final String COUNTS = """
        WITH participants AS (
            SELECT u.id FROM users u JOIN user_credentials c ON c.user_id=u.id
            WHERE u.status='ACTIVE' AND u.role='USER'
              AND NOT EXISTS (SELECT 1 FROM local_admin_accounts a WHERE a.user_id=u.id)
        ), completions AS (
            SELECT s.user_id,s.submitted_at FROM sync_results s
            JOIN participants p ON p.id=s.user_id
        ), visits AS (
            SELECT s.user_id,s.submitted_at,
                   GREATEST(v.first_viewed_at,r.first_viewed_at) AS mutual_formed_at
            FROM completions s JOIN profile_views v ON v.viewer_id=s.user_id
            JOIN participants peer ON peer.id=v.viewed_id
            LEFT JOIN profile_views r ON r.viewer_id=v.viewed_id AND r.viewed_id=v.viewer_id
            WHERE v.viewed_id<>s.user_id AND v.first_viewed_at>s.submitted_at
        )
        SELECT (SELECT COUNT(*) FROM completions) AS completed,
               COUNT(DISTINCT user_id) AS visited,
               COUNT(DISTINCT CASE WHEN mutual_formed_at>submitted_at THEN user_id END) AS mutual
        FROM visits
        """;

    private final JdbcTemplate db;
    private final OperationsService operations;
    private final ParticipationRepository participation;
    private final AuthRateLimiter limits;
    private final TransactionTemplate snapshot;

    public DiscoveryConversionAnalytics(JdbcTemplate db, OperationsService operations,
            ParticipationRepository participation, AuthRateLimiter limits, PlatformTransactionManager manager) {
        this.db=db; this.operations=operations; this.participation=participation; this.limits=limits;
        snapshot=new TransactionTemplate(manager);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshot.setTimeout(10);
    }

    public Report report(AuthPrincipal actor) {
        limits.check("admin-discovery-conversion",Long.toString(actor.userId()),30,60);
        return snapshot.execute(status -> {
            // Current DB role and credential version are checked in this same transaction.
            operations.access(actor);
            var steps=db.query(COUNTS,(row,n) -> {
                long completed=row.getLong("completed"),visited=row.getLong("visited"),mutual=row.getLong("mutual");
                return List.of(
                    new Step("SYNC_COMPLETED",completed,null,rate(completed,completed)),
                    new Step("PROFILE_FIRST_VISIT",visited,rate(visited,completed),rate(visited,completed)),
                    new Step("MUTUAL_VISIT",mutual,rate(mutual,visited),rate(mutual,completed)));
            }).getFirst();
            return new Report("CURRENT_SYNC_RESULTS_ALL_VERSIONS",participation.now(),steps);
        });
    }

    private static Double rate(long users,long denominator) { return denominator==0 ? null : 100.0*users/denominator; }
}
