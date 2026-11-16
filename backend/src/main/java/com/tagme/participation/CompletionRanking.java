package com.tagme.participation;

/** Read-time ranks share one cohort; persisted completion numbers are never renumbered. */
public final class CompletionRanking {
    private CompletionRanking() { }

    public static final String RANKED = """
        SELECT e.user_id, ROW_NUMBER() OVER (ORDER BY e.completion_order) AS completion_rank
        FROM event_completions e JOIN users participant ON participant.id=e.user_id
        JOIN user_credentials credential ON credential.user_id=participant.id
        WHERE participant.status='ACTIVE' AND participant.role='USER'
          AND NOT EXISTS (SELECT 1 FROM local_admin_accounts admin WHERE admin.user_id=participant.id)
        """;
}
