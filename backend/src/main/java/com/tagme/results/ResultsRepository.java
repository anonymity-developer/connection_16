package com.tagme.results;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ResultsRepository {
    private final JdbcTemplate db;
    public ResultsRepository(JdbcTemplate db){this.db=db;}
    public record SyncRow(long userId,String version,String answers,String scores,String typeCode,String hash,Instant submittedAt) { }
    public record Definition(String version,String json) { }
    public record Attempt(long id,long userId,int generation,String key,Instant startsAt,Instant endsAt,Instant deadline,String status,Integer count,Instant submittedAt,String hash,String reason,boolean archived) { }
    private static Instant instant(ResultSet row,String key)throws SQLException{var ts=row.getTimestamp(key);return ts==null?null:ts.toInstant();}
    public Instant now(){return db.queryForObject("SELECT UTC_TIMESTAMP(6)",Timestamp.class).toInstant();}
    public Definition currentDefinition(){return db.query("SELECT q.* FROM current_sync_questionnaire c JOIN sync_questionnaires q ON q.version=c.version WHERE c.id=1 FOR SHARE",(r,n)->new Definition(r.getString("version"),r.getString("definition_json"))).stream().findFirst().orElse(null);}
    public Definition definition(String version){return db.query("SELECT * FROM sync_questionnaires WHERE version=?",(r,n)->new Definition(r.getString("version"),r.getString("definition_json")),version).stream().findFirst().orElse(null);}
    public SyncRow sync(long userId){return db.query("SELECT * FROM sync_results WHERE user_id=?",(r,n)->new SyncRow(userId,r.getString("questionnaire_version"),r.getString("answers_json"),r.getString("axis_scores_json"),r.getString("type_code"),r.getString("payload_hash"),instant(r,"submitted_at")),userId).stream().findFirst().orElse(null);}
    public void saveSync(long userId,String version,String answers,String scores,String typeCode,String hash,Instant received){db.update("INSERT INTO sync_results(user_id,questionnaire_version,answers_json,axis_scores_json,type_code,payload_hash,submitted_at) VALUES (?,?,?,?,?,?,?)",userId,version,answers,scores,typeCode,hash,Timestamp.from(received));}
    private Attempt mapAttempt(ResultSet r,int n)throws SQLException{return new Attempt(r.getLong("id"),r.getLong("user_id"),r.getInt("generation"),r.getString("idempotency_key"),instant(r,"starts_at"),instant(r,"ends_at"),instant(r,"submit_deadline"),r.getString("status"),r.getObject("tap_count",Integer.class),instant(r,"submitted_at"),r.getString("payload_hash"),r.getString("review_reason"),r.getTimestamp("archived_at")!=null);}
    public Attempt attempt(long userId,long id){return db.query("SELECT * FROM tap_attempts WHERE id=? AND user_id=? AND archived_at IS NULL",this::mapAttempt,id,userId).stream().findFirst().orElse(null);}
    public Attempt byKey(long userId,String key){return db.query("SELECT * FROM tap_attempts WHERE user_id=? AND idempotency_key=?",this::mapAttempt,userId,key).stream().findFirst().orElse(null);}
    public Attempt current(long userId){return db.query("SELECT a.* FROM tap_slots s JOIN tap_attempts a ON a.id=s.current_attempt_id AND a.user_id=s.user_id WHERE s.user_id=?",this::mapAttempt,userId).stream().findFirst().orElse(null);}
    public Instant eventClose(){return db.queryForObject("SELECT submit_closes_at FROM event_settings WHERE id=1",Timestamp.class).toInstant();}
    public int gameDuration(){return db.queryForObject("SELECT game_duration FROM event_settings WHERE id=1",Integer.class);}
    public long createAttempt(long userId,String key,Instant starts,Instant ends,Instant deadline){
        int generation=db.queryForObject("SELECT COALESCE(MAX(generation),0)+1 FROM tap_attempts WHERE user_id=?",Integer.class,userId);
        var holder=new GeneratedKeyHolder();db.update(c->{var s=c.prepareStatement("INSERT INTO tap_attempts(user_id,generation,idempotency_key,starts_at,ends_at,submit_deadline) VALUES (?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);s.setLong(1,userId);s.setInt(2,generation);s.setString(3,key);s.setTimestamp(4,Timestamp.from(starts));s.setTimestamp(5,Timestamp.from(ends));s.setTimestamp(6,Timestamp.from(deadline));return s;},holder);
        long id=holder.getKey().longValue();db.update("INSERT INTO tap_slots(user_id,current_attempt_id) VALUES (?,?) ON DUPLICATE KEY UPDATE current_attempt_id=VALUES(current_attempt_id),version=version+1",userId,id);return id;
    }
    public void cancel(Attempt attempt,String state){db.update("UPDATE tap_attempts SET status=?,review_reason='CLIENT_INTERRUPTED' WHERE id=?",state,attempt.id());if("CANCELLED".equals(state))db.update("UPDATE tap_slots SET current_attempt_id=NULL,version=version+1 WHERE user_id=?",attempt.userId());}
    public void finish(Attempt attempt,TapValidation.Verdict verdict,String hash,List<Integer> taps,List<TapValidation.Interruption> interruptions,Instant received){
        db.update("UPDATE tap_attempts SET status=?,tap_count=?,payload_hash=?,submitted_at=?,review_reason=? WHERE id=?",verdict.status(),verdict.count(),hash,Timestamp.from(received),verdict.reason(),attempt.id());
        List<Object[]> events=new ArrayList<>();for(int at:taps)events.add(new Object[]{attempt.id(),"TAP",at});for(var e:interruptions)events.add(new Object[]{attempt.id(),e.type(),e.atMs()});
        if(!events.isEmpty())db.batchUpdate("INSERT INTO tap_attempt_events(attempt_id,event_type,relative_time_ms) VALUES (?,?,?)",events);
    }
    public record StoredEvents(List<Integer> taps,List<TapValidation.Interruption> interruptions) { }
    public StoredEvents events(long attempt) {
        var taps=new ArrayList<Integer>();var interruptions=new ArrayList<TapValidation.Interruption>();
        db.query("SELECT event_type,relative_time_ms FROM tap_attempt_events WHERE attempt_id=? ORDER BY id",r->{
            if("TAP".equals(r.getString(1)))taps.add(r.getInt(2));else interruptions.add(new TapValidation.Interruption(r.getString(1),r.getInt(2)));
        },attempt);
        return new StoredEvents(taps,interruptions);
    }
    public void reclassify(long id,TapValidation.Verdict verdict) {
        db.update("UPDATE tap_attempts SET status=?,review_reason=? WHERE id=?",verdict.status(),verdict.reason(),id);
    }
}
