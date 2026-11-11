package com.tagme.operations;

import com.tagme.content.ContentModels.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public final class OperationsModels {
    private OperationsModels() { }
    public record Page<T>(List<T> items,Long nextCursor) { }
    public record Access(boolean admin,boolean moderationEnabled) { }
    public record Task(ReviewTask review,Kind kind,String visibility,long version,int retryCount) { }
    public record Content(long id,Kind kind,long authorId,String visibility,long version,Long parentWishId,
                          Revision approvedRevision,Revision pendingRevision,Revision latestRevision) { }
    public record Audit(long id,long actorId,String action,String targetType,long targetId,String reason,
                        JsonNode before,JsonNode after,Instant createdAt) { }
}
