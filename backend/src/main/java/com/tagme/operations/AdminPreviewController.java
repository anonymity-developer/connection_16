package com.tagme.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tagme.auth.*;
import com.tagme.results.Questionnaire;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/admin/preview")
public class AdminPreviewController {
    public record SyncDefinition(String version,String source,Questionnaire definition) { }
    private final OperationsService operations;private final JdbcTemplate db;private final ObjectMapper json;
    public AdminPreviewController(OperationsService operations,JdbcTemplate db,ObjectMapper json){this.operations=operations;this.db=db;this.json=json;}
    @GetMapping("/sync") public SyncDefinition sync(Authentication authentication)throws java.io.IOException {
        if(authentication==null||!(authentication.getPrincipal() instanceof AuthPrincipal actor))throw AuthException.invalid();operations.access(actor);
        var definitions=db.queryForList("SELECT q.version,q.definition_json FROM current_sync_questionnaire c JOIN sync_questionnaires q ON q.version=c.version WHERE c.id=1");
        if(!definitions.isEmpty()) {
            var d=definitions.getFirst();try{return new SyncDefinition((String)d.get("version"),"CONFIGURED",Questionnaire.parse((String)d.get("definition_json"),json));}
            catch(IllegalArgumentException invalid){throw new AuthException(503,"QUESTIONNAIRE_NOT_READY","등록된 성향 문항을 확인해 주세요.");}
        }
        String sample=new ClassPathResource("preview/sync-demo.json").getContentAsString(StandardCharsets.UTF_8);
        return new SyncDefinition("admin-preview-example-v1","EXAMPLE",Questionnaire.parse(sample,json));
    }
}
