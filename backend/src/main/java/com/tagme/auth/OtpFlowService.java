package com.tagme.auth;

import org.springframework.stereotype.Service;

@Service
public class OtpFlowService {
    public record Pending(String username,AuthService.OtpResponse challenge,int retryAfter,String limitCode,boolean expired,boolean inProgress) { }
    private final AuthService auth;
    private final OtpFlowStore store;
    private final OtpRequestLimiter limiter;
    private final AuthRateLimiter limits;
    private final AuthCrypto crypto;
    private final AuthProperties properties;
    public OtpFlowService(AuthService auth,OtpFlowStore store,OtpRequestLimiter limiter,AuthRateLimiter limits,AuthCrypto crypto,AuthProperties properties){
        this.auth=auth;this.store=store;this.limiter=limiter;this.limits=limits;this.crypto=crypto;this.properties=properties;
    }
    /** Called before the controller creates an anonymous session, including on rejected requests. */
    public void admit(boolean stateRead,String ip){
        limits.check(stateRead?"otp-state-ip":"otp-flow-ip",ip,properties.ipPerMinute(),60);
        if(!properties.enabled())throw AuthException.unavailable();
    }
    private static int seconds(long until,long now){return (int)Math.max(0,Math.ceil((until-now)/1000.0));}
    private int wait(OtpFlowStore.Flow flow,long now){return Math.max(seconds(flow.retryAt(),now),limiter.remaining(flow.username(),null).seconds());}
    private AuthService.OtpResponse response(OtpFlowStore.Flow flow,boolean reused){
        long now=System.currentTimeMillis();
        if(!flow.active(now))throw new AuthException(409,"AUTH_RESTART_REQUIRED","인증 시간이 지났어요. 인증번호를 새로 받아주세요.");
        return new AuthService.OtpResponse(flow.challengeId(),seconds(flow.expiresAt(),now),wait(flow,now),reused);
    }
    public Pending pending(OtpStore.Purpose purpose,AuthPrincipal principal,String session,String ip){
        if(!properties.enabled())throw AuthException.unavailable();
        if(purpose==OtpStore.Purpose.PASSWORD_CHANGE)auth.otpUsername(purpose,null,principal,session);
        if(session==null)return new Pending(null,null,0,null,false,false);
        var flow=store.pending(session,purpose);boolean busy=store.locked(session,purpose);
        if(flow==null)return new Pending(null,null,busy?1:0,null,false,busy);
        long now=System.currentTimeMillis();var state=limiter.remaining(flow.username(),null);
        int retry=Math.max(seconds(flow.retryAt(),now),state.seconds());
        String code=retry==0?null:state.quota()?"OTP_RATE_LIMITED":flow.errorCode()==null?"OTP_RESEND_COOLDOWN":flow.errorCode();
        var challenge=flow.active(now)&&!busy?new AuthService.OtpResponse(flow.challengeId(),seconds(flow.expiresAt(),now),retry,true):null;
        return new Pending(flow.username(),challenge,retry,code,flow.challengeId()!=null&&!flow.active(now),busy);
    }
    public AuthService.OtpResponse request(OtpStore.Purpose purpose,String input,boolean resend,String requestId,AuthPrincipal principal,String session,String ip){
        String name=auth.otpUsername(purpose,input,principal,session);
        String id=requestId==null?crypto.identifier():requestId,owner=crypto.identifier();
        if(!store.lock(session,purpose,owner))throw new AuthException(409,"OTP_REQUEST_IN_PROGRESS","진행 중인 인증 요청을 확인하고 있어요.",1);
        try {
            var receipt=store.receipt(session,purpose,id);
            if(receipt!=null){
                if(!name.equals(receipt.username()))throw new AuthException(409,"AUTH_RESTART_REQUIRED","아이디가 변경됐어요. 다시 요청해 주세요.");
                if(receipt.errorStatus()!=0)throw new AuthException(receipt.errorStatus(),receipt.errorCode(),receipt.errorMessage(),wait(receipt,System.currentTimeMillis()));
                // A late retry must never bring back a proof superseded by an explicit resend.
                var current=store.pending(session,purpose);
                if(current==null||!name.equals(current.username())||!current.active(System.currentTimeMillis()))
                    throw new AuthException(409,"AUTH_RESTART_REQUIRED","인증 시간이 지났어요. 인증번호를 새로 받아주세요.");
                return response(current,true);
            }
            var previous=store.pending(session,purpose);
            if(!resend&&previous!=null&&name.equals(previous.username())&&previous.active(System.currentTimeMillis()))return response(previous,true);
            try {
                var next=auth.request(purpose,input,principal,session,ip);long now=System.currentTimeMillis();
                var saved=new OtpFlowStore.Flow(name,next.challengeId(),now+next.expiresIn()*1000L,now+next.resendAfter()*1000L,id,0,null,null);
                store.save(session,purpose,owner,id,saved,saved);return next;
            } catch(AuthException failure){
                long now=System.currentTimeMillis();int retry=Math.max(failure.retryAfter(),limiter.remaining(name,null).seconds());
                var failed=new OtpFlowStore.Flow(name,null,0,now+retry*1000L,id,failure.status(),failure.code(),failure.getMessage());
                var current=failure.status()==429&&previous!=null&&name.equals(previous.username())&&previous.active(now)
                    ?new OtpFlowStore.Flow(name,previous.challengeId(),previous.expiresAt(),failed.retryAt(),id,failure.status(),failure.code(),failure.getMessage()):failed;
                store.save(session,purpose,owner,id,current,failed);
                throw new AuthException(failure.status(),failure.code(),failure.getMessage(),retry);
            }
        } finally {store.unlock(session,purpose,owner);}
    }
    public void clear(String session,OtpStore.Purpose purpose,String challenge){store.clear(session,purpose,challenge);}
}
