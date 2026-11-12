package com.tagme.operations;

import com.tagme.auth.*;
import com.tagme.participation.ParticipationRepository;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ErrorReportService {
    public record ReportRequest(@NotBlank @Size(max=400) String body,@NotBlank @Size(max=160) String pagePath) { }
    public record Receipt(long id,Instant createdAt) { }
    public record Report(long id,long reporterId,String username,String nickname,String body,String pagePath,Instant createdAt) { }
    private final JdbcTemplate db;
    private final AuthRepository accounts;
    private final ParticipationRepository participation;
    private final OperationsService operations;
    private final AuthRateLimiter limits;
    public ErrorReportService(JdbcTemplate db,AuthRepository accounts,ParticipationRepository participation,OperationsService operations,AuthRateLimiter limits) {
        this.db=db;this.accounts=accounts;this.participation=participation;this.operations=operations;this.limits=limits;
    }
    private String safePath(String path) {
        if(path==null)return "/";
        String clean=path.split("[?#]",2)[0];
        if(clean.startsWith("/t/"))return "/t/[tag]";
        return clean.matches("/[a-zA-Z0-9/_-]{0,159}")?clean:"/";
    }
    @Transactional
    public Receipt submit(AuthPrincipal actor,String key,ReportRequest input) {
        if(key==null||!key.matches("[A-Za-z0-9_-]{16,64}")||input.body()==null||input.body().isBlank()||input.body().codePointCount(0,input.body().length())>200)
            throw new AuthException(400,"INVALID_REPORT","오류 내용은 1~200자로 입력해 주세요.");
        participation.lockPrincipal(actor);
        String body=input.body().strip(),path=safePath(input.pagePath());
        var previous=db.queryForList("SELECT id,body,page_path,created_at FROM error_reports WHERE reporter_id=? AND request_key=?",actor.userId(),key);
        if(!previous.isEmpty()) {
            var row=previous.getFirst();
            if(!body.equals(row.get("body"))||!path.equals(row.get("page_path")))throw new AuthException(409,"IDEMPOTENCY_CONFLICT","같은 신고 요청의 내용이 바뀌었어요.");
            return receipt(((Number)row.get("id")).longValue());
        }
        limits.check("error-report",Long.toString(actor.userId()),5,600);
        var user=accounts.byId(actor.userId()).orElseThrow(AuthException::invalid);
        var keys=new GeneratedKeyHolder();
        db.update(c->{var p=c.prepareStatement("INSERT INTO error_reports(reporter_id,reporter_username,reporter_nickname,body,page_path,request_key) VALUES (?,?,?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);
            p.setLong(1,actor.userId());p.setString(2,user.username());p.setString(3,user.nickname());p.setString(4,body);p.setString(5,path);p.setString(6,key);return p;},keys);
        long id=Objects.requireNonNull(keys.getKey()).longValue();
        return receipt(id);
    }
    private Receipt receipt(long id) {return db.queryForObject("SELECT id,created_at FROM error_reports WHERE id=?",(r,n)->new Receipt(r.getLong("id"),r.getTimestamp("created_at").toInstant()),id);}
    @Transactional
    public AdminAnalytics.Page<Report> list(AuthPrincipal actor,int offset,int limit) {
        operations.access(actor);
        if(offset<0||offset>100000||limit<1||limit>50)throw new AuthException(400,"INVALID_PAGE","목록 범위를 확인해 주세요.");
        long total=db.queryForObject("SELECT COUNT(*) FROM error_reports",Long.class);
        var rows=db.query("SELECT * FROM error_reports ORDER BY id DESC LIMIT ? OFFSET ?",(r,n)->new Report(r.getLong("id"),r.getLong("reporter_id"),r.getString("reporter_username"),r.getString("reporter_nickname"),r.getString("body"),r.getString("page_path"),r.getTimestamp("created_at").toInstant()),limit,offset);
        return new AdminAnalytics.Page<>(rows,total,offset,offset+rows.size()<total?offset+rows.size():null);
    }
}
