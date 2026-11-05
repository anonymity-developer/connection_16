package com.tagme.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tagme.content.ContentModels.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.tagme.operations.OperationsModels.*;

@Repository
public class AdminAuditRepository {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public AdminAuditRepository(JdbcTemplate db,ObjectMapper json) { this.db=db;this.json=json; }
    public static Map<String,Object> state(Item item,Job job) {
        Map<String,Object> state=new LinkedHashMap<>();
        state.put("itemId",item.id());state.put("visibility",item.visibility());state.put("version",item.version());
        state.put("approvedRevisionId",item.approvedId());state.put("pendingRevisionId",item.pendingId());
        if(job!=null){state.put("status",job.status());state.put("fence",job.fence());}
        return state;
    }
    private String encode(Object value) { try{return json.writeValueAsString(value);}catch(Exception bad){throw new IllegalStateException("Cannot encode audit metadata",bad);} }
    public void record(long actor,String action,String type,long target,String reason,Object before,Object after) {
        db.update("INSERT INTO admin_audit_logs(actor_id,action,target_type,target_id,reason,before_json,after_json) VALUES (?,?,?,?,?,?,?)",actor,action,type,target,reason.strip(),encode(before),encode(after));
    }
    public List<Audit> list(long cursor,int limit,String action) {
        String sql="SELECT * FROM admin_audit_logs WHERE id<?";List<Object> args=new ArrayList<>();args.add(cursor);
        if(action!=null){sql+=" AND action=?";args.add(action);}sql+=" ORDER BY id DESC LIMIT ?";args.add(limit);
        return db.query(sql,(r,n)->{try{return new Audit(r.getLong("id"),r.getLong("actor_id"),r.getString("action"),r.getString("target_type"),r.getLong("target_id"),r.getString("reason"),json.readTree(r.getString("before_json")),json.readTree(r.getString("after_json")),r.getTimestamp("created_at").toInstant());}catch(java.io.IOException bad){throw new IllegalStateException("Cannot decode audit metadata",bad);}},args.toArray());
    }
}
