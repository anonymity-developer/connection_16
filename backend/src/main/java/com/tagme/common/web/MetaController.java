package com.tagme.common.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetaController {
    @GetMapping("/api/v1/meta")
    public MetaResponse meta() {
        return new MetaResponse("TAG ME", "bootstrap", List.of(
                new MissionInfo("SHOW_ME", "S", "SHOW ME"),
                new MissionInfo("SYNC_ME", "S", "SYNC ME"),
                new MissionInfo("MAKE_A_WISH", "A", "MAKE A WISH"),
                new MissionInfo("FAST_TAP", "F", "FAST TAP"),
                new MissionInfo("SAY_SOMETHING", "Y", "SAY SOMETHING")));
    }

    public record MetaResponse(String service, String stage, List<MissionInfo> missions) { }
    public record MissionInfo(String code, String letter, String title) { }
}
