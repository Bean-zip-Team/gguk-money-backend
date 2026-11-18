package com.ggukmoney.beanzip.domain.friend.repository;

import com.ggukmoney.beanzip.domain.friend.entity.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {

    Optional<InviteCode> findByCode(String code);

    /**
     * 코드를 발급한다. 이 사용자에게 이미 코드가 있거나 같은 코드를 다른 사람이 쓰고 있으면 넣지 않는다.
     *
     * @return 넣었으면 1, 아니면 0
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO invite_code (user_id, code, created_at)
            VALUES (:userId, :code, :now)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId, @Param("code") String code, @Param("now") Instant now);
}
