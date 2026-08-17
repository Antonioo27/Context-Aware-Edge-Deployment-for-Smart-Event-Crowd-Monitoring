package it.unibo.cas.eventmanagement.controllers;

import it.unibo.cas.eventmanagement.models.enums.OrchestrationPolicy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/orchestration")
public class OrchestrationController {

    private OrchestrationPolicy activePolicy = OrchestrationPolicy.CONTEXT_AWARE;

    @GetMapping("/policy")
    public ResponseEntity<OrchestrationPolicy> getActivePolicy() {
        return ResponseEntity.ok(activePolicy);
    }

    @PostMapping("/policy")
    public ResponseEntity<String> setActivePolicy(@RequestParam OrchestrationPolicy policy) {
        this.activePolicy = policy;
        return ResponseEntity.ok("Politica di orchestrazione aggiornata a: " + policy);
    }

    public OrchestrationPolicy getCurrentPolicyInternal() {
        return this.activePolicy;
    }
}