package com.kghospital.workflow.service;

import com.kghospital.workflow.domain.entity.*;
import com.kghospital.workflow.domain.enums.TransitionType;
import com.kghospital.workflow.domain.event.StateChangedEvent;
import com.kghospital.workflow.dto.*;
import com.kghospital.workflow.exception.WorkflowException;
import com.kghospital.workflow.kafka.StateChangedProducer;
import com.kghospital.workflow.repository.*;
import com.kghospital.workflow.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final EntityWorkflowInstanceRepository instanceRepo;
    private final WorkflowDefinitionRepository workflowRepo;
    private final StateChangedProducer eventProducer;

    @Transactional
    public WorkflowStatusResponse start(WorkflowStartRequest req) {
        String tenantId = TenantContext.get();

        WorkflowDefinition workflow = workflowRepo
            .findEffective(req.entityType(), tenantId)
            .orElseThrow(() -> WorkflowException.notFound(
                "WorkflowDefinition", req.entityType()));

        EntityWorkflowInstance instance = EntityWorkflowInstance.builder()
            .entityId(req.entityId())
            .entityType(req.entityType())
            .tenantId(tenantId)
            .workflow(workflow)
            .currentState(workflow.getInitialState())
            .build();

        instance = instanceRepo.save(instance);

        // Record INSTANTIATE history entry
        TransitionHistory history = TransitionHistory.builder()
            .instance(instance)
            .toState(workflow.getInitialState())
            .actionCode("INSTANTIATE")
            .actorSubject("SYSTEM")
            .actorRole("SYSTEM")
            .transitionType(TransitionType.INSTANTIATE)
            .build();
        instance.getHistory().add(history);
        instanceRepo.save(instance);

        log.info("Workflow started: entity={} type={} state={}",
            req.entityId(), req.entityType(), workflow.getInitialState());

        return toStatusResponse(instance);
    }

    @Transactional
    public TransitionResponse transition(UUID entityId, String entityType,
                                         TransitionRequest req) {
        String tenantId = TenantContext.get();

        EntityWorkflowInstance instance = instanceRepo
            .findWithHistory(entityId, entityType, tenantId)
            .orElseThrow(() -> WorkflowException.notFound("WorkflowInstance", entityId));

        WorkflowDefinition workflow = workflowRepo
            .findEffective(entityType, tenantId)
            .orElseThrow(() -> WorkflowException.notFound("WorkflowDefinition", entityType));

        // Check terminal state
        workflow.getStates().stream()
            .filter(s -> s.getStateCode().equals(instance.getCurrentState()))
            .filter(StateDefinition::getIsTerminal)
            .findAny()
            .ifPresent(s -> { throw WorkflowException.alreadyTerminal(s.getStateCode()); });

        // Find valid transition
        TransitionDefinition transitionDef = workflow.getTransitions().stream()
            .filter(t -> t.getFromState().equals(instance.getCurrentState()))
            .filter(t -> t.getActionCode().equals(req.actionCode()))
            .filter(TransitionDefinition::getIsEnabled)
            .findFirst()
            .orElseThrow(() -> WorkflowException.invalidTransition(
                instance.getCurrentState(), req.actionCode()));

        // Verify target state is enabled
        workflow.getStates().stream()
            .filter(s -> s.getStateCode().equals(transitionDef.getToState()))
            .filter(s -> !s.getIsEnabled())
            .findAny()
            .ifPresent(s -> {
                throw new WorkflowException("PLAT-003-E009",
                    "Target state '" + s.getStateCode() + "' is disabled for this tenant.",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
            });

        String prevState = instance.getCurrentState();

        // Record history
        TransitionHistory history = TransitionHistory.builder()
            .instance(instance)
            .fromState(prevState)
            .toState(transitionDef.getToState())
            .actionCode(req.actionCode())
            .actorSubject(req.actorSubject())
            .actorRole(req.actorRole())
            .comment(req.comment())
            .correlationId(req.correlationId())
            .transitionType(TransitionType.NORMAL)
            .build();

        instance.getHistory().add(history);
        instance.setCurrentState(transitionDef.getToState());
        instance.setStateEnteredAt(Instant.now());
        instanceRepo.save(instance);

        // Publish async event
        StateChangedEvent event = new StateChangedEvent(
            instance.getId(), entityId, entityType, tenantId,
            prevState, transitionDef.getToState(), req.actionCode(),
            req.actorSubject(), req.actorRole(), req.comment(),
            req.correlationId(), Instant.now()
        );
        eventProducer.publish(event);

        log.info("Transition: entity={} {}→{} action={}",
            entityId, prevState, transitionDef.getToState(), req.actionCode());

        return new TransitionResponse(instance.getId(), entityId, entityType,
            prevState, transitionDef.getToState(), req.actionCode(), Instant.now());
    }

    @Transactional(readOnly = true)
    public WorkflowStatusResponse getStatus(UUID entityId, String entityType) {
        String tenantId = TenantContext.get();
        return instanceRepo.findWithHistory(entityId, entityType, tenantId)
            .map(this::toStatusResponse)
            .orElseThrow(() -> WorkflowException.notFound("WorkflowInstance", entityId));
    }

    private WorkflowStatusResponse toStatusResponse(EntityWorkflowInstance i) {
        List<WorkflowStatusResponse.HistoryEntry> history = i.getHistory().stream()
            .map(h -> new WorkflowStatusResponse.HistoryEntry(
                h.getId(), h.getFromState(), h.getToState(),
                h.getActionCode(), h.getActorRole(),
                h.getTransitionType().name(), h.getTransitionedAt()))
            .toList();
        return new WorkflowStatusResponse(i.getId(), i.getEntityId(),
            i.getEntityType(), i.getCurrentState(), i.getStateEnteredAt(),
            i.getTenantId(), history);
    }
}
