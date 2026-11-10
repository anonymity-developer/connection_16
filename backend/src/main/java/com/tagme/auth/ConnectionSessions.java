package com.tagme.auth;

import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConnectionSessions {
    public static final String ATTRIBUTE="TAGME_CONNECTION_ID";
    private final JdbcTemplate db;
    public ConnectionSessions(JdbcTemplate db){this.db=db;}
    public void open(AuthRepository.Account account,HttpSession session){
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO connection_sessions(id,user_id,credential_version,expires_at) VALUES(?,?,?,TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)))",id,account.id(),account.version(),session.getMaxInactiveInterval());
        session.setAttribute(ATTRIBUTE,id);
    }
    public void touch(HttpSession session){
        Object id=session.getAttribute(ATTRIBUTE);if(id==null)return;
        db.update("UPDATE connection_sessions SET last_seen_at=UTC_TIMESTAMP(6),expires_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)) WHERE id=? AND ended_at IS NULL AND expires_at>UTC_TIMESTAMP(6)",session.getMaxInactiveInterval(),id.toString());
    }
    public void close(HttpSession session,String reason){if(session!=null){Object id=session.getAttribute(ATTRIBUTE);if(id!=null)close(id.toString(),reason);}}
    private void close(String id,String reason){db.update("UPDATE connection_sessions SET ended_at=LEAST(UTC_TIMESTAMP(6),expires_at),end_reason=? WHERE id=? AND ended_at IS NULL",reason,id);}
    public void revoke(long user,long version){db.update("UPDATE connection_sessions SET ended_at=LEAST(UTC_TIMESTAMP(6),expires_at),end_reason='REVOKED' WHERE user_id=? AND credential_version<>? AND ended_at IS NULL",user,version);}
    // Expiry is calculated from expires_at at read time. It remains correct when Redis expiry events are delayed or unavailable.
}
