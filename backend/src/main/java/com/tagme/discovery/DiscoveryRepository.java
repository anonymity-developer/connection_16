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
        AND TRIM(COALESCE(u.real_name,''))<>'' AND TRIM(COALESCE(u.region,''))<>'' AND u.class_no>0 AND u.mattermost_profile_synced_at IS NOT NULL
        AND 2=(SELECT COUNT(*) FROM current_consent_policies cp JOIN user_consents uc
          ON uc.policy_type=cp.policy_type AND uc.policy_version=cp.policy_version AND uc.user_id=u.id AND uc.withdrawn_at IS NULL)
        """;
    private static final String PROFILE_COLUMNS="""
        u.id,p.nickname,s.type_code,s.questionnaire_version,q.definition_json,
        EXISTS(SELECT 1 FROM profile_views v WHERE v.viewer_id=? AND v.viewed_id=u.id) AS visited,
        EXISTS(SELECT 1 FROM profile_views v WHERE v.viewer_id=u.id AND v.viewed_id=?) AS visited_me,
        (SELECT COUNT(*) FROM user_interests a JOIN user_interests b ON b.interest_id=a.interest_id AND b.user_id=? WHERE a.user_id=u.id) AS common_count,
        COALESCE(s.type_code=mine.type_code AND s.questionnaire_version=mine.questionnaire_version,FALSE) AS same_type,
        (SELECT JSON_ARRAYAGG(JSON_OBJECT('id',i.id,'name',i.name,'shared',EXISTS(SELECT 1 FROM user_interests mi WHERE mi.user_id=? AND mi.interest_id=i.id)))
         FROM user_interests ui JOIN interests i ON i.id=ui.interest_id WHERE ui.user_id=u.id) AS interest_json
        FROM users u JOIN profiles p ON p.user_id=u.id LEFT JOIN sync_results s ON s.user_id=u.id
        LEFT JOIN sync_questionnaires q ON q.version=s.questionnaire_version LEFT JOIN sync_results mine ON mine.user_id=?
        WHERE
        """+VISIBLE;
    private static final String SELECT="SELECT "+PROFILE_COLUMNS;
    private List<Object> args(long actor) { return new ArrayList<>(List.of(actor,actor,actor,actor,actor)); }
    private Person person(ResultSet r,Map<String,Questionnaire> definitions) throws SQLException {
        List<Interest> interests=new ArrayList<>();List<String> shared=new ArrayList<>();
        try { for(var i:json.readTree(r.getString("interest_json")==null?"[]":r.getString("interest_json"))) { interests.add(new Interest(i.get("id").asLong(),i.get("name").asText()));if(i.get("shared").asInt()==1)shared.add(i.get("name").asText()); } }
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
    public List<Person> recommended(long actor) {
        var args=args(actor);
        args.add(actor);
        args.add(actor);
        return query(SELECT + """
             AND u.id<>?
             ORDER BY visited ASC,common_count DESC,same_type DESC,
               NOT EXISTS(SELECT 1 FROM users me WHERE me.id=? AND me.region=u.region AND me.class_no=u.class_no) DESC,
               u.id DESC LIMIT 12
            """, args);
    }
    public Person person(long actor,long id) { var args=args(actor);args.add(id);return query(SELECT+" AND u.id=?",args).stream().findFirst().orElse(null); }
    public record Fields(String realName,String region,int classNo,String bio,String githubUrl,String blogUrl,String photoUrl) { }
    public Fields fields(long id) { return db.query("SELECT u.real_name,u.region,u.class_no,p.bio,p.github_url,p.blog_url,u.mattermost_picture_updated_at FROM users u JOIN profiles p ON p.user_id=u.id WHERE u.id=?",(r,n)->new Fields(r.getString("real_name"),r.getString("region"),r.getInt("class_no"),r.getString("bio"),r.getString("github_url"),r.getString("blog_url"),r.getLong("mattermost_picture_updated_at")>0?"/api/v1/discovery/profiles/"+id+"/photo?v="+r.getLong("mattermost_picture_updated_at"):null),id).getFirst(); }
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
    public Graph graph(long actor,String nickname,int limit,int offset) {
        // Each existence flag contributes at most once. A like cannot create a visit.
        String candidates = """
            SELECT EXISTS(SELECT 1 FROM cheer_likes l
              JOIN content_items i ON i.id=l.card_id
              JOIN content_revisions r ON r.id=i.approved_revision_id AND r.item_id=i.id
              WHERE l.user_id=? AND i.author_id=u.id AND i.kind='CHEER'
                AND i.visibility_status='VISIBLE' AND r.moderation_status='APPROVED'
                AND r.anonymous=FALSE) AS own_like,
            """ + PROFILE_COLUMNS;
        var params=args(actor);
        params.addFirst(actor);
        params.add(actor);
        params.add(limit+1);
        params.add(offset);
        // A mutual visit (4 or 6) always precedes a single visit (1 or 3).
        String sql="SELECT * FROM ("+candidates+" AND u.id<>?) candidates WHERE visited OR visited_me "+
            "ORDER BY (visited AND visited_me) DESC,own_like DESC,id DESC LIMIT ? OFFSET ?";
        Set<Long> liked=new HashSet<>();
        Map<String,Questionnaire> definitions=new HashMap<>();
        var rows=db.query(sql,(r,n)->{
            var peer=person(r,definitions);
            if(r.getBoolean("own_like"))liked.add(peer.id());
            return peer;
        },params.toArray());
        var nodes=rows.stream().limit(limit).toList();
        List<Edge> edges=new ArrayList<>();
        for(var p:nodes) {
            int weight=RelationshipWeight.score(p.visited(),p.visitedMe(),liked.contains(p.id()));
            String strength=RelationshipWeight.strength(weight);
            if(p.visited())edges.add(new Edge(actor,p.id(),weight,strength));
            if(p.visitedMe())edges.add(new Edge(p.id(),actor,weight,strength));
        }
        return new Graph(new Self(actor,nickname),nodes,edges,rows.size()>limit);
    }
}
