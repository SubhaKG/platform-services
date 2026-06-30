package com.kghospital.escalation.controller;
import com.kghospital.escalation.service.EscalationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/escalations")
@RequiredArgsConstructor
public class EscalationController {
    private final EscalationService service;

    @PostMapping("/{entityId}/acknowledge")
    public void acknowledge(
        @PathVariable UUID entityId,
        @RequestParam String eventType,
        @RequestParam String acknowledgedBy
    ) {
        service.acknowledge(entityId, eventType, acknowledgedBy);
    }
}
