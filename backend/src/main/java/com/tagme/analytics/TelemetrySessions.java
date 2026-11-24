package com.tagme.analytics;

import com.tagme.auth.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class TelemetrySessions {
    private final AuthCrypto crypto;
    public TelemetrySessions(AuthCrypto crypto) { this.crypto=crypto; }
    public String key(HttpServletRequest request,long user) {
        var session=request.getSession(false);
        return session==null ? null : crypto.digest("page-analytics:"+user+":"+session.getId());
    }
    public String current(long user) {
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
            return key(attributes.getRequest(),user);
        return null;
    }
}
