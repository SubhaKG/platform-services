package com.kghospital.iam.repository;

import com.kghospital.iam.domain.entity.IamUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface IamUserRepository extends JpaRepository<IamUser, UUID> {
    Optional<IamUser> findByUsernameAndIsActiveTrue(String username);
    Optional<IamUser> findByEmailAndIsActiveTrue(String email);
    Optional<IamUser> findByKeycloakSubject(String subject);
}
