package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface UserAccountJpaRepository extends JpaRepository<UserAccountJpaEntity, UUID> {
  Optional<UserAccountJpaEntity> findByEmail(String email);

  @Query(
      "select new"
          + " com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence.AccountCustomerRow(u.id,"
          + " u.customerId) from UserAccountJpaEntity u where u.id = :id")
  Optional<AccountCustomerRow> findCustomerAssociationById(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000"))
  @Query("select u from UserAccountJpaEntity u where u.id = :id")
  Optional<UserAccountJpaEntity> findForUpdate(@Param("id") UUID id);
}
