package com.codeja.master_acdc.identity.infrastructure.adapters.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {
  @org.springframework.data.jpa.repository.Query(
      "select t.userId from RefreshTokenJpaEntity t where t.tokenHash = :hash")
  java.util.Optional<UUID> findUserIdByHash(
      @org.springframework.data.repository.query.Param("hash") String hash);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.QueryHints(
      @jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000"))
  @org.springframework.data.jpa.repository.Query(
      "select t from RefreshTokenJpaEntity t where t.tokenHash = :hash")
  java.util.Optional<RefreshTokenJpaEntity> findForUpdateByHash(
      @org.springframework.data.repository.query.Param("hash") String hash);

  @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
  @org.springframework.data.jpa.repository.Query(
      "update RefreshTokenJpaEntity t set t.revokedAt = :now where t.familyId = :familyId and"
          + " t.revokedAt is null")
  int revokeFamily(
      @org.springframework.data.repository.query.Param("familyId") UUID familyId,
      @org.springframework.data.repository.query.Param("now") java.time.Instant now);

  @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
  @org.springframework.data.jpa.repository.Query(
      "update RefreshTokenJpaEntity t set t.revokedAt = :now where t.userId = :userId and"
          + " t.revokedAt is null")
  int revokeUser(
      @org.springframework.data.repository.query.Param("userId") UUID userId,
      @org.springframework.data.repository.query.Param("now") java.time.Instant now);
}
