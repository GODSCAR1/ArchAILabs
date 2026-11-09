package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {
  @Query("select t.userId from RefreshTokenJpaEntity t where t.tokenHash = :hash")
  Optional<UUID> findUserIdByHash(@Param("hash") String hash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000"))
  @Query("select t from RefreshTokenJpaEntity t where t.tokenHash = :hash")
  Optional<RefreshTokenJpaEntity> findForUpdateByHash(@Param("hash") String hash);

  @Modifying(flushAutomatically = true)
  @Query(
      "update RefreshTokenJpaEntity t set t.revokedAt = :now where t.familyId = :familyId and"
          + " t.revokedAt is null")
  int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

  @Modifying(flushAutomatically = true)
  @Query(
      "update RefreshTokenJpaEntity t set t.revokedAt = :now where t.userId = :userId and"
          + " t.revokedAt is null")
  int revokeUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
