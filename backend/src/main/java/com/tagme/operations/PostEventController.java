package com.tagme.operations;

import com.tagme.auth.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/results")
public class PostEventController {
    private final PostEventService service;private final ObjectMapper json;
    public PostEventController(PostEventService service,ObjectMapper json){this.service=service;this.json=json;}
    private AuthPrincipal actor(Authentication a){if(a==null||!(a.getPrincipal() instanceof AuthPrincipal p))throw AuthException.invalid();return p;}
    private <T> ResponseEntity<T> response(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").header("X-Content-Type-Options","nosniff").body(body);}
    @GetMapping public ResponseEntity<PostEventModels.Report> report(Authentication a){return response(service.report(actor(a)));}
    @GetMapping("/participants/{id}") public ResponseEntity<PostEventModels.History> history(Authentication a,@PathVariable long id,@RequestParam String purpose,@RequestParam(defaultValue="0") long after){return response(service.history(actor(a),id,purpose,after));}
    @GetMapping("/export") public ResponseEntity<byte[]> export(Authentication a,@RequestParam String format,@RequestParam String snapshot)throws Exception{
        var report=service.forExport(actor(a),snapshot);String text,type,extension;
        switch(format){case "csv"->{text=PostEventExport.csv(report);type="text/csv";extension="csv";}case "json"->{text=json.writeValueAsString(report);type="application/json";extension="json";}case "md"->{text=PostEventExport.markdown(report);type="text/markdown";extension="md";}default->throw new AuthException(400,"INVALID_FORMAT","CSV·JSON·Markdown 중 선택해 주세요.");}
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").header("X-Content-Type-Options","nosniff").header("Content-Disposition","attachment; filename=tagme-post-event."+extension).contentType(MediaType.parseMediaType(type+";charset=UTF-8")).body(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    // Individual activity dataset export has intentionally NO route. Policy approval is required.
}
