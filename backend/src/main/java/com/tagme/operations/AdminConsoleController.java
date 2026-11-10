package com.tagme.operations;

import com.tagme.auth.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin")
public class AdminConsoleController {
    private final AdminAnalytics analytics;
    public AdminConsoleController(AdminAnalytics analytics){this.analytics=analytics;}
    private AuthPrincipal actor(Authentication auth){if(auth==null||!(auth.getPrincipal() instanceof AuthPrincipal p))throw AuthException.invalid();return p;}
    @GetMapping("/overview") public AdminAnalytics.Overview overview(Authentication auth){return analytics.overview(actor(auth));}
    @GetMapping("/members") public AdminAnalytics.Page<AdminAnalytics.Member> members(Authentication auth,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit,@RequestParam(required=false) String q){return analytics.members(actor(auth),offset,limit,q,false);}
    @GetMapping("/members/{id}") public AdminAnalytics.Detail detail(Authentication auth,@PathVariable long id){return analytics.detail(actor(auth),id);}
    @GetMapping("/completions") public AdminAnalytics.Page<AdminAnalytics.Member> completions(Authentication auth,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit){return analytics.members(actor(auth),offset,limit,null,true);}
    @GetMapping("/rankings/{mission}") public AdminAnalytics.Ranking ranking(Authentication auth,@PathVariable String mission,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit){return analytics.ranking(actor(auth),mission,offset,limit);}
}
