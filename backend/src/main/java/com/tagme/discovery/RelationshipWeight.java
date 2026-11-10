package com.tagme.discovery;

/** Version 1: bounded current state, never an incrementing activity counter. */
public final class RelationshipWeight {
    private RelationshipWeight() { }
    public static int score(boolean outgoing, boolean incoming, boolean ownPublicLike) {
        return (outgoing?1:0)+(incoming?1:0)+(outgoing&&incoming?2:0)+(ownPublicLike?2:0);
    }
    public static String strength(int weight) { return weight>=5?"STRONG":weight>=3?"MEDIUM":"LIGHT"; }
}
