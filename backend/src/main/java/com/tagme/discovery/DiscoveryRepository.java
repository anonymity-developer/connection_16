package com.tagme.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tagme.participation.ParticipationModels.Interest;
import com.tagme.results.Questionnaire;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.tagme.discovery.DiscoveryModels.*;

@Repository
public class DiscoveryRepository {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public DiscoveryRepository(JdbcTemplate db,ObjectMapper json) { this.db=db;this.json=json; }
    // Alias u/p is always the profile being disclosed; this predicate is shared by every surface.
    private static final String VISIBLE="""
        u.status='ACTIVE' AND p.is_public=TRUE AND TRIM(p.nickname)<>''
        AND TRIM(COALESCE(u.real_name,''))<>'' AND u.region='서울' AND u.class_no>0 AND TRIM(COALESCE(p.bio,''))<>''
        AND EXISTS(SELECT 1 FROM user_interests vi WHERE vi.user_id=u.id)
        AND 2=(SELECT COUNT(*) FROM current_consent_policies cp JOIN user_consents uc
          ON uc.policy_type=cp.policy_type AND uc.policy_version=cp.policy_version AND uc.user_id=u.id AND uc.withdrawn_at IS NULL)
        """;
    private static final String SELECT="""
        SELECT u.id,p.nickname,s.type_code,s.questionnaire_version,q.definition_json,
        EXISTS(SELECT 1 FROM profile_views v WHERE v.viewer_id=? AND v.viewed_id=u.id) AS visited,
        EXISTS(SELECT 1 FROM profile_views v WHERE v.viewer_id=u.id AND v.viewed_id=?) AS visited_me,
        (SELECT COUNT(*) FROM user_interests a JOIN user_interests b ON b.interest_id=a.interest_id AND b.user_id=? WHERE a.user_id=u.id) AS common_count,
        (s.type_code=mine.type_code AND s.questionnaire_version=mine.questionnaire_version) AS same_type,
        (SELECT JSON_ARRAYAGG(JSON_OBJECT('id',i.id,'name',i.name,'shared',EXISTS(SELECT 1 FROM user_interests mi WHERE mi.user_id=? AND mi.interest_id=i.id)))
         FROM user_interests ui JOIN interests i ON i.id=ui.interest_id WHERE ui.user_id=u.id) AS interest_json
        FROM users u JOIN profiles p ON p.user_id=u.id LEFT JOIN sync_results s ON s.user_id=u.id
        LEFT JOIN sync_questionnaires q ON q.version=s.questionnaire_version LEFT JOIN sync_results mine ON mine.user_id=?
        WHERE
        """+VISIBLE;
    private List<Object> args(long actor) { return new ArrayList<>(List.of(actor,actor,actor,actor,actor)); }
    private Person person(ResultSet r,Map<String,Questionnaire> definitions) throws SQLException {
        List<Interest> interests=new ArrayList<>();List<String> shared=new ArrayList<>();
        try { for(var i:json.readTree(r.getString("interest_json"))) { interests.add(new Interest(i.get("id").asLong(),i.get("name").asText()));if(i.get("shared").asInt()==1)shared.add(i.get("name").asText()); } }
        catch(Exception bad) { throw new IllegalStateException("Cannot read profile interests",bad); }
        interests.sort(Comparator.comparingLong(Interest::id));shared.sort(String::compareTo);
        Type type=null;String version=r.getString("questionnaire_version"),code=r.getString("type_code");
        if(version!=null&&r.getString("definition_json")!=null) {
            try { var definition=definitions.computeIfAbsent(version,v-> { try{return Questionnaire.parse(r.getString("definition_json"),json);}catch(SQLException bad){throw new IllegalStateException(bad);} });
                var found=definition.types().stream().filter(t->t.code().equals(code)).findFirst().orElse(null);if(found!=null)type=new Type(found.code(),found.title(),version);
            } catch(IllegalArgumentException invalidDefinition) { /* A missing/invalid optional type never invents a result. */ }
        }
        boolean visited=r.getBoolean("visited"),inbound=r.getBoolean("visited_me");
        return new Person(r.getLong("id"),r.getString("nickname"),interests,type,shared,type!=null&&r.getBoolean("same_type"),visited,inbound,visited&&inbound);
    }
    private List<Person> query(String sql,List<Object> args) { Map<String,Questionnaire> definitions=new HashMap<>();return db.query(sql,(r,n)->person(r,definitions),args.toArray()); }
    public People people(long actor,long cursor,int limit,String q,Long interest,String type,String version) {
        var args=args(actor);String sql=SELECT+" AND u.id<>? AND u.id<?";args.add(actor);args.add(cursor);
        if(q!=null&&!q.isBlank()) { sql+=" AND p.nickname LIKE ? ESCAPE '!'";args.add("%"+q.strip().replace("!","!!").replace("%","!%").replace("_","!_")+"%"); }
        if(interest!=null) { sql+=" AND EXISTS(SELECT 1 FROM user_interests fi WHERE fi.user_id=u.id AND fi.interest_id=?)";args.add(interest); }
        if(type!=null) { sql+=" AND s.type_code=? AND s.questionnaire_version=?";args.add(type);args.add(version); }
        sql+=" ORDER BY u.id DESC LIMIT ?";args.add(limit+1);var rows=query(sql,args);var items=rows.stream().limit(limit).toList();return new People(items,rows.size()>limit?items.getLast().id():null);
    }
    public List<Person> recommended(long actor) { var args=args(actor);args.add(actor);return query(SELECT+" AND u.id<>? ORDER BY visited ASC,common_count DESC,same_type DESC,u.id DESC LIMIT 12",args); }
    public Person person(long actor,long id) { var args=args(actor);args.add(id);return query(SELECT+" AND u.id=?",args).stream().findFirst().orElse(null); }
    public record Fields(String realName,String region,int classNo,String bio,String githubUrl,String blogUrl) { }
    public Fields fields(long id) { return db.query("SELECT u.real_name,u.region,u.class_no,p.bio,p.github_url,p.blog_url FROM users u JOIN profiles p ON p.user_id=u.id WHERE u.id=?",(r,n)->new Fields(r.getString("real_name"),r.getString("region"),r.getInt("class_no"),r.getString("bio"),r.getString("github_url"),r.getString("blog_url")),id).getFirst(); }
    public boolean visit(long actor,long target) {
        if(actor==target)return false;
        if(db.queryForObject("SELECT COUNT(*) FROM profile_views WHERE viewer_id=? AND viewed_id=?",Integer.class,actor,target)>0)return false;
        db.update("INSERT INTO profile_views(viewer_id,viewed_id,first_viewed_at) VALUES (?,?,UTC_TIMESTAMP(6))",actor,target);return true;
    }
    public Stats stats(long actor) {
        return db.query("""
            SELECT COALESCE(SUM(EXISTS(SELECT 1 FROM profile_views v WHERE v.viewer_id=? AND v.viewed_id=u.id)),0) AS outgoing,
            COALESCE(SUM(EXISTS(SELECT 1 FROM profile_views v WHERE v.viewer_id=u.id AND v.viewed_id=?)),0) AS incoming,
            COALESCE(SUM(EXISTS(SELECT 1 FROM profile_views v WHERE v.viewer_id=? AND v.viewed_id=u.id)
             AND EXISTS(SELECT 1 FROM profile_views v WHERE v.viewer_id=u.id AND v.viewed_id=?)),0) AS mutual
            FROM users u JOIN profiles p ON p.user_id=u.id WHERE
            """+VISIBLE+" AND u.id<>?",(r,n)->new Stats(r.getLong("outgoing"),r.getLong("incoming"),r.getLong("mutual")),actor,actor,actor,actor,actor).getFirst();
    }
    public Graph graph(long actor,String nickname,int limit) {
        var args=args(actor);args.add(actor);args.add(actor);args.add(actor);args.add(limit+1);
        var rows=query(SELECT+" AND u.id<>? AND (EXISTS(SELECT 1 FROM profile_views v WHERE v.viewer_id=? AND v.viewed_id=u.id) OR EXISTS(SELECT 1 FROM profile_views v WHERE v.viewer_id=u.id AND v.viewed_id=?)) ORDER BY u.id DESC LIMIT ?",args);
        var nodes=rows.stream().limit(limit).toList();List<Edge> edges=new ArrayList<>();for(var p:nodes){if(p.visited())edges.add(new Edge(actor,p.id()));if(p.visitedMe())edges.add(new Edge(p.id(),actor));}
        return new Graph(new Self(actor,nickname),nodes,edges,rows.size()>limit);
    }
}
