package com.tagme.operations;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class PostEventModels {
    private PostEventModels() { }
    public record Metric(String key,long value,String unit,String basis) { }
    public record Mission(String code,Long unlocked,long performed,long completed,long pending,String basis) { }
    public record Bucket(String group,String label,long count) { }
    public record Hour(String hourUtc,String event,long participants,long records) { }
    public record Evidence(String mission,String sourceKind,long sourceId,long receiptOrder,Instant acceptedAt,boolean valid) { }
    public record Candidate(String category,int rank,String participantId,Long score,Instant validCompletionAt,Instant profileRegisteredAt,boolean metricTie,boolean unresolvedTie,List<AwardModels.Category> overlappingTopCategories,List<Evidence> proofs) { }
    public record Review(String participantId,long sourceId,String reason) { }
    public record Data(AwardModels.Window window,String phase,String scope,List<Metric> metrics,List<Mission> missions,List<Bucket> buckets,List<Hour> hours,boolean hoursTruncated,List<Candidate> candidates,List<Review> reviewRecords,long reviewTotal,List<String> policyDecisions,List<String> limitations,boolean finalizationAvailable) { }
    public record Report(String ruleVersion,String snapshotId,Data data) { }
    public record Step(String mission,String status,Instant unlockedAt,Instant completedAt,String sourceType,Long sourceId,Instant acceptedAt,boolean validProof) { }
    public record Activity(long cursor,String eventType,String missionCode,Instant eventTime,String status,String sourceType) { }
    public record History(String participantId,Instant observedAt,String purpose,Instant profileFirstPublishedAt,List<Step> missions,List<Activity> receipts,Long nextCursor,String limitation) { }
}
