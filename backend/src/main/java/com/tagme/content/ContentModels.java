package com.tagme.content;

import java.time.Instant;
import java.util.List;
import com.tagme.participation.ParticipationModels.MissionCode;

public final class ContentModels {
    private ContentModels() { }
    public enum Kind { WISH, CHEER;
        public MissionCode mission() { return this == WISH ? MissionCode.MAKE_A_WISH : MissionCode.SAY_SOMETHING; }
        public String source() { return this == WISH ? "WISH_REVISION" : "CHEER_REVISION"; }
    }
    public record Item(long id,long authorId,Kind kind,String visibility,Long approvedId,Long pendingId,Integer luckScore,Integer landingZone,long version) { }
    public record Revision(long id,long itemId,int number,String content,String templateId,String style,boolean anonymous,Instant submittedAt,String status,String reason,@com.fasterxml.jackson.annotation.JsonIgnore String hash) { }
    public record OwnSlot(Long id,long version,String visibility,Revision approvedRevision,Revision pendingRevision,Revision latestRevision,boolean missionCompleted,Integer luckScore,Integer landingZone) { }
    // Deliberately no author identifier, profile link, revision ID or submission timestamp.
    public record PublicContent(long id,String content,String templateId,String style,boolean anonymous,String nickname,Integer luckScore,Integer landingZone) { }
    public record Feed(List<PublicContent> items,Long nextCursor) { }
    public record Job(long id,long revisionId,long authorId,long itemId,String status,int attempts,long fence,Instant leaseUntil) { }
    public record Work(Job job,String text) { }
    public record ReviewTask(Job job,Revision revision,Revision approvedRevision) { }
    public record ReviewRequest(long expectedFence,String decision,String reason) { }
}
