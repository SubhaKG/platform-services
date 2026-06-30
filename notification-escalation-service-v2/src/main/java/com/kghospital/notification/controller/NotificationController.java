package com.kghospital.notification.controller;

import com.kghospital.notification.domain.entity.AlertInstance;
import com.kghospital.notification.domain.enums.AlertStatus;
import com.kghospital.notification.domain.enums.AlertType;
import com.kghospital.notification.dto.*;
import com.kghospital.notification.exception.NotificationException;
import com.kghospital.notification.repository.AlertInstanceRepository;
import com.kghospital.notification.repository.EscalationChainConfigRepository;
import com.kghospital.notification.repository.HospitalChannelConfigRepository;
import com.kghospital.notification.domain.entity.EscalationChainConfig;
import com.kghospital.notification.domain.entity.HospitalChannelConfig;
import com.kghospital.notification.service.AcknowledgeService;
import com.kghospital.notification.service.AlertDispatchService;
import com.kghospital.notification.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController {

    private final AlertDispatchService dispatchService;
    private final AcknowledgeService acknowledgeService;
    private final AlertInstanceRepository alertRepo;
    private final HospitalChannelConfigRepository channelConfigRepo;
    private final EscalationChainConfigRepository chainConfigRepo;

    @PostMapping("/notifications/dispatch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('NOTIFICATION_DISPATCHER')")
    public void dispatch(@Valid @RequestBody AlertDispatchRequest req) {
        dispatchService.dispatch(req);
    }

    @PostMapping("/notifications/{id}/acknowledge")
    public AlertInstance acknowledge(
        @PathVariable UUID id,
        @RequestBody(required = false) AcknowledgeRequest req,
        Principal principal
    ) {
        UUID actorId = UUID.fromString(principal.getName());
        return acknowledgeService.acknowledge(id, actorId,
            req != null ? req.comment() : null);
    }

    @GetMapping("/notifications/{id}")
    @PreAuthorize("hasRole('NOTIFICATION_READER')")
    public AlertInstance getById(@PathVariable UUID id) {
        return alertRepo.findById(id)
            .orElseThrow(() -> NotificationException.notFound(id));
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasRole('NOTIFICATION_READER')")
    public Page<AlertInstance> query(
        @RequestParam(required = false) UUID recipientId,
        @RequestParam(required = false) AlertType alertType,
        @RequestParam(required = false) AlertStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return alertRepo.query(recipientId, alertType, status, from, to,
            PageRequest.of(page, Math.min(size, 200)));
    }

    /** §3.5.1 Context C — tenant-scoped: JWT tenant claim must match X-Tenant-ID/path tenant */
    @PutMapping("/config/channels")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public void updateChannelConfig(@Valid @RequestBody ChannelConfigRequest req) {
        String tenantId = TenantContext.get();
        HospitalChannelConfig config = channelConfigRepo.findByTenantId(tenantId)
            .orElse(HospitalChannelConfig.builder().tenantId(tenantId).build());
        config.setConfigJson(req.configJson());
        config.setUpdatedAt(Instant.now());
        channelConfigRepo.save(config);
    }

    @PutMapping("/config/escalation-chains")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public void updateEscalationChain(@Valid @RequestBody EscalationChainRequest req) {
        String tenantId = TenantContext.get();
        AlertType alertType = AlertType.valueOf(req.alertType());

        EscalationChainConfig config = chainConfigRepo
            .findByTenantIdAndAlertType(tenantId, alertType)
            .orElse(EscalationChainConfig.builder()
                .tenantId(tenantId).alertType(alertType).build());
        config.setChainJson(req.chainJson());
        config.setUpdatedAt(Instant.now());
        chainConfigRepo.save(config);
    }
}
