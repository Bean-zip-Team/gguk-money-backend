package com.ggukmoney.beanzip.domain.keycap.repository;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapPassiveCheckpoint;
import com.ggukmoney.beanzip.domain.keycap.passive.KeycapAutoClickAccrual.Checkpoint;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
@DataJpaTest
class KeycapPassiveCheckpointRepositoryTest {
    @Autowired AppUserRepository users;
    @Autowired KeycapPassiveCheckpointRepository checkpoints;
    @Test void firstCheckpointWithAssignedUserIdPersistsAndReloads() {
        var user=users.saveAndFlush(AppUser.createActive("checkpoint-"+UUID.randomUUID(),null));
        var value=Checkpoint.initial(Instant.now(),"cheer",1,60);
        checkpoints.saveAndFlush(KeycapPassiveCheckpoint.create(user,value,"{}",7));
        var saved=checkpoints.findById(user.getId()).orElseThrow();
        assertThat(saved.getClicksPerDay()).isEqualTo(60);
        assertThat(saved.getUserId()).isEqualTo(user.getId());
    }
}
