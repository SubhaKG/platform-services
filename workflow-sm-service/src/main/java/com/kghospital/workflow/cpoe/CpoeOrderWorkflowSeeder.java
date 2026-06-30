package com.kghospital.workflow.cpoe;

import com.kghospital.workflow.domain.entity.*;
import com.kghospital.workflow.domain.enums.*;
import com.kghospital.workflow.repository.WorkflowDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)   // runs after Flyway (Order 1)
public class CpoeOrderWorkflowSeeder implements ApplicationRunner {

    private final WorkflowDefinitionRepository repo;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repo.findByEntityTypeAndTenantIdIsNullAndStatus(
                "CPOE_ORDER", WorkflowStatus.ACTIVE).isPresent()) {
            log.info("CPOE_ORDER workflow already seeded");
            return;
        }

        WorkflowDefinition wf = WorkflowDefinition.builder()
            .entityType("CPOE_ORDER")
            .name("CPOE Order Lifecycle v1.0")
            .tenantId(null)
            .initialState("DRAFT")
            .status(WorkflowStatus.ACTIVE)
            .build();

        // ── States ────────────────────────────────────────────────────────────
        addState(wf, "DRAFT",           "Draft",                false, 60);
        addState(wf, "PENDING_VERIFY",  "Pending Verification", false, 30);
        addState(wf, "VERIFIED",        "Verified",             false, 120);
        addState(wf, "IN_PROGRESS",     "In Progress",          false, null);
        addState(wf, "DISPENSED",       "Dispensed",            false, null);
        addState(wf, "ADMINISTERED",    "Administered",         false, null);
        addState(wf, "COMPLETED",       "Completed",            true,  null);
        addState(wf, "CANCELLED",       "Cancelled",            true,  null);
        addState(wf, "SUSPENDED",       "Suspended",            false, null);

        // ── Happy path ────────────────────────────────────────────────────────
        addTransition(wf, "DRAFT",         "PENDING_VERIFY", "SUBMIT",     "PHYSICIAN,RESIDENT");
        addTransition(wf, "PENDING_VERIFY","VERIFIED",       "VERIFY",     "PHARMACIST,PHYSICIAN");
        addTransition(wf, "VERIFIED",      "IN_PROGRESS",    "PROCESS",    "PHARMACIST,NURSE");
        addTransition(wf, "IN_PROGRESS",   "DISPENSED",      "DISPENSE",   "PHARMACIST");
        addTransition(wf, "DISPENSED",     "ADMINISTERED",   "ADMINISTER", "NURSE");
        addTransition(wf, "ADMINISTERED",  "COMPLETED",      "COMPLETE",   "NURSE,PHYSICIAN");

        // ── Cancellations ─────────────────────────────────────────────────────
        for (String s : new String[]{"DRAFT","PENDING_VERIFY","VERIFIED","IN_PROGRESS"}) {
            addTransition(wf, s, "CANCELLED", "CANCEL", "PHYSICIAN,PHARMACIST,ADMIN");
        }

        // ── Suspend / resume ──────────────────────────────────────────────────
        addTransition(wf, "VERIFIED",    "SUSPENDED", "SUSPEND", "PHYSICIAN,PHARMACIST");
        addTransition(wf, "IN_PROGRESS", "SUSPENDED", "SUSPEND", "PHYSICIAN,PHARMACIST");
        addTransition(wf, "SUSPENDED",   "VERIFIED",  "RESUME",  "PHYSICIAN,PHARMACIST");

        // ── Cosign (stays in DRAFT) ───────────────────────────────────────────
        addTransition(wf, "DRAFT", "DRAFT", "COSIGN", "PHYSICIAN");

        repo.save(wf);
        log.info("CPOE_ORDER workflow seeded: {} states, {} transitions",
            wf.getStates().size(), wf.getTransitions().size());
    }

    private void addState(WorkflowDefinition wf, String code, String name,
                           boolean terminal, Integer sla) {
        wf.getStates().add(StateDefinition.builder()
            .workflow(wf).stateCode(code).displayName(name)
            .isTerminal(terminal).isEnabled(true).slaMinutes(sla)
            .build());
    }

    private void addTransition(WorkflowDefinition wf, String from,
                                String to, String action, String roles) {
        wf.getTransitions().add(TransitionDefinition.builder()
            .workflow(wf).fromState(from).toState(to)
            .actionCode(action).allowedRoles(roles)
            .trigger(TransitionTrigger.MANUAL).isEnabled(true)
            .build());
    }
}
