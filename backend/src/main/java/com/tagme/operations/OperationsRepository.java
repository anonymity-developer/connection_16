package com.tagme.operations;

import com.tagme.content.ContentModels.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OperationsRepository {
    private final JdbcTemplate db;
    public OperationsRepository(JdbcTemplate db) { this.db=db; }
    public List<Long> contentIds(long cursor,int limit,String kind,String visibility) {
        String sql="SELECT id FROM content_items WHERE id<?";List<Object> args=new ArrayList<>();args.add(cursor);
        if(kind!=null){sql+=" AND kind=?";args.add(kind);}if(visibility!=null){sql+=" AND visibility_status=?";args.add(visibility);}
        sql+=" ORDER BY id DESC LIMIT ?";args.add(limit);return db.queryForList(sql,Long.class,args.toArray());
    }
    public List<Long> taskIds(long after,int limit,String status) {
        String sql="SELECT id FROM moderation_jobs WHERE id>?";List<Object> args=new ArrayList<>();args.add(after);
        if("PENDING".equals(status))sql+=" AND status IN ('QUEUED','PROCESSING','REVIEW_REQUIRED')";
        else if(status!=null){sql+=" AND status=?";args.add(status);}
        sql+=" ORDER BY id LIMIT ?";args.add(limit);return db.queryForList(sql,Long.class,args.toArray());
    }
    public int retryCount(long job) { return db.queryForObject("SELECT manual_retry_count FROM moderation_jobs WHERE id=?",Integer.class,job); }
    public void retry(Job job) {
        db.update("UPDATE moderation_jobs SET status='QUEUED',attempts=0,manual_retry_count=manual_retry_count+1,fence=fence+1,lease_until=NULL,next_run_at=UTC_TIMESTAMP(6),provider=NULL,policy_version=NULL,last_error_code=NULL WHERE id=?",job.id());
        db.update("UPDATE content_revisions SET moderation_status='QUEUED',reason_code=NULL WHERE id=?",job.revisionId());
    }
    public void visibility(Item item,String next) {
        db.update("UPDATE content_items SET visibility_status=?,version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=?",next,item.id());
        if("HIDDEN".equals(next)&&item.pendingId()!=null) {
            db.update("UPDATE moderation_jobs SET status='REVIEW_REQUIRED',fence=fence+1,lease_until=NULL,last_error_code='CONTENT_HIDDEN' WHERE revision_id=? AND status IN ('QUEUED','PROCESSING','REVIEW_REQUIRED')",item.pendingId());
            db.update("UPDATE content_revisions SET moderation_status='REVIEW_REQUIRED',reason_code='CONTENT_HIDDEN' WHERE id=?",item.pendingId());
        }
    }
}
