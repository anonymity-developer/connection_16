package com.tagme.operations;

import com.tagme.auth.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class ErrorReportController {
    private final ErrorReportService reports;
    public ErrorReportController(ErrorReportService reports){this.reports=reports;}
    private AuthPrincipal actor(Authentication auth){if(auth==null||!(auth.getPrincipal() instanceof AuthPrincipal p))throw AuthException.invalid();return p;}
    @PostMapping("/api/v1/error-reports")
    public ErrorReportService.Receipt submit(Authentication auth,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody ErrorReportService.ReportRequest body){return reports.submit(actor(auth),key,body);}
    @GetMapping("/api/v1/admin/error-reports")
    public AdminAnalytics.Page<ErrorReportService.Report> list(Authentication auth,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit){return reports.list(actor(auth),offset,limit);}
}
