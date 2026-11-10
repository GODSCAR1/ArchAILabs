package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenJpaRepository
    extends JpaRepository<PasswordResetTokenJpaEntity, UUID> {}
