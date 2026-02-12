package com.waangu.platform.copilot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Standard /copilot/intents endpoint (chagpt). Override or extend intents in your module.
 */
@RestController
@RequestMapping("/copilot")
public class CopilotIntentController {

    @Value("${waangu.module-id:unknown}")
    private String moduleId;

    @GetMapping("/intents")
    public Map<String, Object> intents() {
        return Map.of(
                "module_id", moduleId,
                "intents", defaultIntents()
        );
    }

    /**
     * Override in a @Configuration in the consuming app to provide module-specific intents.
     */
    protected List<Map<String, Object>> defaultIntents() {
        return List.of();
    }
}
