package com.tagme.participation;

/** Read-time ranks share one cohort; persisted completion numbers are never renumbered. */
public final class CompletionRanking {
    private CompletionRanking() { }

    public static final String RANKED = """
        SELECT e.user_id, COALESCE(proof.recognized_at,e.completed_at) AS recognized_completed_at,
          ROW_NUMBER() OVER (ORDER BY COALESCE(proof.recognized_at,e.completed_at),e.completion_order,e.user_id) AS completion_rank
        FROM event_completions e JOIN users participant ON participant.id=e.user_id
        JOIN user_credentials credential ON credential.user_id=participant.id
        LEFT JOIN (
          SELECT evidence.user_id,MAX(evidence.accepted_received_at) recognized_at
          FROM mission_completion_evidence evidence JOIN missions m ON m.id=evidence.mission_id
          JOIN mission_progress p ON p.user_id=evidence.user_id AND p.mission_id=m.id AND p.status='COMPLETED'
          WHERE m.code IN ('SHOW_ME','SYNC_ME','MAKE_A_WISH','FAST_TAP','SAY_SOMETHING')
          GROUP BY evidence.user_id HAVING COUNT(DISTINCT m.code)=5
        ) proof ON proof.user_id=e.user_id
        WHERE participant.status='ACTIVE' AND participant.role='USER'
          AND NOT EXISTS (SELECT 1 FROM local_admin_accounts admin WHERE admin.user_id=participant.id)
        """;
}
