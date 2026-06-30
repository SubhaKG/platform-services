package com.kghospital.escalation.repository;
import com.kghospital.escalation.domain.entity.EscalationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface EscalationRuleRepository extends JpaRepository<EscalationRule, UUID> {
    List<EscalationRule> findByTenantIdAndEventTypeAndIsActiveTrue(String tenantId, String eventType);
}
