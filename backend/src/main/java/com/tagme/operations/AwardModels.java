package com.tagme.operations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AwardModels {
    private AwardModels() { }
    public enum Category { FIRST_COMPLETION, COMMENT_KING, POPULAR_CHEER, FAST_TAP }
    public record User(long id,String nickname,Instant profileRegisteredAt) { }
    public record Proof(long userId,String mission,String sourceKind,long sourceId,long receiptOrder,Instant acceptedAt,boolean valid) { }
    public record Activity(long userId,long count) { }
    public record Review(long userId,long sourceId,String reason) { }
    public record Window(Instant observedAt,Instant opensAt,Instant closesAt,Instant moderationClosesAt) { }
    public record Facts(Window window,List<User> users,List<Proof> proofs,List<Activity> comments,List<Activity> hearts,List<Activity> taps,List<Review> reviews,long pendingModeration) { }
    public record Candidate(int rank,long userId,String nickname,Instant profileRegisteredAt,Instant validCompletionAt,Long score,
                            boolean metricTie,boolean unresolvedTie,List<Category> overlappingTopCategories,List<Proof> proofs) { }
    public record Ranking(Map<Category,List<Candidate>> categories,int eligibleCompleteCount,long invalidProofs,long incompleteProofSets) { }
    public record Report(String ruleVersion,boolean provisional,Instant observedAt,Instant opensAt,Instant submitClosesAt,Instant moderationClosesAt,
                         String eventPhase,Category category,int eligibleCompleteCount,int total,int offset,int limit,List<Candidate> items,
                         long invalidProofs,long incompleteProofSets,long pendingModeration,long reviewTotal,List<Review> reviewRecords,List<String> pendingDecisions) { }
}
