package com.tagme.discovery;

/** Internal ranking policy. Scores and interaction signals must never enter a public DTO. */
public final class RecommendationPolicy {
    public static final String VERSION = "weighted-v2";
    public static final int LIMIT = 12;
    private static final int INTEREST = 3000, TYPE = 2000, COMMENT = 1500,
        LIKE = 1000, UNVISITED = 2000, OTHER_CLASS = 500;

    private RecommendationPolicy() { }

    // Integer hundredths of a point: deterministic ordering without floating-point ties.
    public record Signals(int commonInterests, int viewerInterests, int peerInterests,
                          boolean sameType, boolean comment, boolean like,
                          boolean visited, boolean differentClass) {
        public Signals {
            if (commonInterests < 0 || viewerInterests < commonInterests || peerInterests < commonInterests)
                throw new IllegalArgumentException("Invalid interest counts");
        }
    }

    public static int score(Signals signals) {
        long union = (long) signals.viewerInterests() + signals.peerInterests() - signals.commonInterests();
        int interest = union == 0 ? 0 : (int) ((long) INTEREST * signals.commonInterests() / union);
        return interest + (signals.sameType() ? TYPE : 0) + (signals.comment() ? COMMENT : 0)
            + (signals.like() ? LIKE : 0) + (!signals.visited() ? UNVISITED : 0)
            + (signals.differentClass() ? OTHER_CLASS : 0);
    }

    /** SQL aliases belong to the repository; all weights come from this versioned policy. */
    static String scoreExpression() {
        return """
            (COALESCE(FLOOR(%d * common_count / NULLIF(viewer_count + peer_count - common_count, 0)), 0)
             + %d * same_type + %d * comment_interaction + %d * like_interaction
             + %d * (NOT visited) + %d * different_class)
            """.formatted(INTEREST, TYPE, COMMENT, LIKE, UNVISITED, OTHER_CLASS).strip();
    }
}
