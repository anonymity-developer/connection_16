package com.tagme.participation;

import java.time.Instant;
import java.util.List;

public final class ParticipationModels {
    private ParticipationModels() { }
    public enum MissionCode { SHOW_ME, SYNC_ME, MAKE_A_WISH, FAST_TAP, SAY_SOMETHING }
    public enum PolicyType { PARTICIPATION, PROFILE_PUBLICATION }
    public record Policy(PolicyType type, String version, String title, String body, boolean agreed) { }
    public record Policies(boolean ready, List<Policy> policies) { }
    public record Event(String status, Instant opensAt, Instant submitClosesAt, boolean profilePreparationEnabled) { }
    public record Interest(long id, String name) { }
    public record CatalogInterest(long id,String name,String category) { }
    public record Identity(String realName,String region,Integer classNo,String photoUrl,boolean ready) { }
    public record OwnProfile(String nickname, @com.fasterxml.jackson.annotation.JsonIgnore String realName, @com.fasterxml.jackson.annotation.JsonIgnore String region, @com.fasterxml.jackson.annotation.JsonIgnore Integer classNo, String bio,
                             String githubUrl, String blogUrl, @com.fasterxml.jackson.annotation.JsonIgnore boolean isPublic, long version, List<Long> interestIds,@com.fasterxml.jackson.annotation.JsonIgnore boolean sourceReady) { }
    public record ProfileState(Identity identity,OwnProfile profile, Policies consents, boolean profileComplete, boolean participationReady) { }
    public record Mission(long id, MissionCode code, String letter, String title, String hint, boolean active,
                          String state, Instant unlockedAt, Instant completedAt) { }
    public record Completion(boolean complete, Instant completedAt, Long completionOrder, boolean awardConfirmed) { }
    public record Collection(List<Mission> missions, int completedCount, Completion completion,long resetGeneration) { }
    public record TagResult(MissionCode missionCode, String state, String route, boolean newlyUnlocked, boolean newlyCompleted) { }
    public record TagInspection(MissionCode missionCode,String letter,String title) { }
    public record Receipt(long id, long userId, String sourceKind, long sourceId, Instant receivedAt) { }
    public record Pending(boolean present) { }
}
