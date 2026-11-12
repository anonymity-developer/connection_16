package com.tagme.discovery;

/** Internal ranking only. Public DTOs never contain scores or private interaction signals. */
public final class RecommendationPolicy {
    public static final String VERSION="weighted-v3";
    public static final int LIMIT=12;
    private RecommendationPolicy() { }
    public record Similarity(int common,int viewer,int peer) {
        public Similarity { if(common<0||viewer<common||peer<common)throw new IllegalArgumentException("Invalid interest counts"); }
        int points() { long union=(long)viewer+peer-common;return union==0?0:(int)(1000L*common/union); }
    }
    public record Signals(Similarity industry,Similarity development,Similarity stack,boolean sameType,
                          boolean comment,boolean like,boolean visited,boolean differentClass,int exposures) {
        public Signals { if(exposures<0)throw new IllegalArgumentException("Invalid exposure count"); }
    }
    public static int score(Signals s) {
        return s.industry().points()+s.development().points()+s.stack().points()
            +(s.sameType()?2000:0)+(s.comment()?1500:0)+(s.like()?1000:0)
            +(!s.visited()?2000:0)+(s.differentClass()?500:0)-(!s.visited()?Math.min(4,s.exposures())*500:0);
    }
    static String scoreExpression() {
        String interest=java.util.stream.Stream.of("industry","development","stack").map(c->
            "COALESCE(FLOOR(1000 * "+c+"_common / NULLIF("+c+"_viewer + "+c+"_peer - "+c+"_common,0)),0)")
            .collect(java.util.stream.Collectors.joining(" + "));
        return "("+interest+" + 2000 * same_type + 1500 * comment_interaction + 1000 * like_interaction"
            +" + 2000 * (NOT visited) + 500 * different_class - 500 * (NOT visited) * LEAST(4, exposures))";
    }
}
