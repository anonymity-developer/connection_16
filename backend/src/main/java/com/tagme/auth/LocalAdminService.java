package com.tagme.auth;

import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LocalAdminService implements ApplicationRunner {
    private final AuthRepository accounts;
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final AuthRateLimiter limits;
    private final Environment environment;
    private final AuthProperties properties;
    private final TransactionTemplate tx;
    private final String dummyHash;
    public LocalAdminService(AuthRepository accounts,JdbcTemplate db,PasswordEncoder passwords,AuthRateLimiter limits,Environment environment,AuthProperties properties,PlatformTransactionManager manager) {
        this.accounts=accounts;this.db=db;this.passwords=passwords;this.limits=limits;this.environment=environment;this.properties=properties;tx=new TransactionTemplate(manager);dummyHash=passwords.encode(UUID.randomUUID().toString());
    }
    // ':' is outside the participant username grammar: these namespaces cannot collide.
    public static boolean adminLogin(String name) { return name!=null&&name.strip().toLowerCase(Locale.ROOT).startsWith("admin:"); }
    @Override public void run(ApplicationArguments args) {
        String name=environment.getProperty("tagme.admin.bootstrap-id","").strip().toLowerCase(Locale.ROOT);
        String password=environment.getProperty("tagme.admin.bootstrap-password","");
        if(name.isBlank()&&password.isBlank())return;
        if(!name.matches("admin:[a-z0-9._-]{1,48}"))throw new IllegalArgumentException("ADMIN_BOOTSTRAP_ID must be admin: followed by 1-48 lowercase letters, digits, '.', '_' or '-'");
        PasswordPolicy.validateAdmin(password);
        try {if(Base64.getDecoder().decode(properties.hmacKey()).length<32)throw new IllegalArgumentException();}
        catch(IllegalArgumentException invalid){throw new IllegalArgumentException("Set AUTH_HMAC_KEY to at least 32 random bytes encoded as Base64 for administrator login");}
        String hash=passwords.encode(password);
        tx.executeWithoutResult(status->{
            var existing=accounts.byAdminLogin(name);
            if(existing.isEmpty()) {
                long id=accounts.insertUser("local-admin:"+UUID.randomUUID(),null);
                db.update("UPDATE users SET role='ADMIN' WHERE id=?",id);
                db.update("INSERT INTO local_admin_accounts(user_id,login_id) VALUES(?,?)",id,name);
                accounts.insertCredential(id,hash);
            } else {
                accounts.lockUser(existing.get().id());var locked=accounts.byId(existing.get().id()).orElseThrow();
                if(!"ADMIN".equals(locked.role())||!"ACTIVE".equals(locked.status()))throw new IllegalStateException("Configured administrator is not active; restore it explicitly before bootstrapping");
                if(!passwords.matches(password,locked.hash())) {
                    accounts.changePassword(locked.id(),hash);
                    db.update("UPDATE connection_sessions SET ended_at=LEAST(UTC_TIMESTAMP(6),expires_at),end_reason='REVOKED' WHERE user_id=? AND ended_at IS NULL",locked.id());
                }
            }
        });
    }
    public AuthRepository.Account login(String input,String password,String ip) {
        String name=input.strip().toLowerCase(Locale.ROOT);
        limits.check("admin-login-ip",ip,30,60);limits.check("admin-login-name",name,10,900);
        var account=accounts.byAdminLogin(name).orElse(null);
        boolean matches=false;
        try {matches=passwords.matches(password,account==null||account.hash()==null?dummyHash:account.hash());}catch(IllegalArgumentException invalid){passwords.matches(password,dummyHash);}
        if(!matches||account==null||!"ACTIVE".equals(account.status())||!"ADMIN".equals(account.role()))throw AuthException.loginFailed();
        return tx.execute(status->{
            accounts.lockUser(account.id());var current=accounts.byId(account.id()).orElseThrow(AuthException::loginFailed);
            if(!"ACTIVE".equals(current.status())||!"ADMIN".equals(current.role())||!Objects.equals(current.version(),account.version()))throw AuthException.loginFailed();
            accounts.recordLogin(current.id());return accounts.byId(current.id()).orElseThrow();
        });
    }
}
