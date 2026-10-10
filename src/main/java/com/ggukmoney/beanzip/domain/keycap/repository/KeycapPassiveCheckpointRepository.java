package com.ggukmoney.beanzip.domain.keycap.repository;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapPassiveCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface KeycapPassiveCheckpointRepository extends JpaRepository<KeycapPassiveCheckpoint,UUID> {}
