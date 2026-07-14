package com.ggukmoney.beanzip.persistence;

import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ADomainPersistenceSmokeTest {

    private static final EntityContract[] ENTITIES = {
            entity("com.ggukmoney.beanzip.domain.user.entity.AppUser", "app_user", false, UUID.class),
            entity("com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity", "auth_identity", true),
            entity("com.ggukmoney.beanzip.global.config.entity.AppConfig", "app_config", true),
            entity("com.ggukmoney.beanzip.domain.keycap.entity.Keycap", "keycap", true),
            entity("com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap", "user_keycap", true),
            entity("com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount", "keycap_box_account", true),
            entity("com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen", "keycap_box_open", true),
            entity("com.ggukmoney.beanzip.domain.onboarding.entity.OnboardingRewardAttempt", "onboarding_reward_attempt", true),
            entity("com.ggukmoney.beanzip.domain.tap.entity.TapBatch", "tap_batch", true),
            entity("com.ggukmoney.beanzip.domain.tap.entity.UserTapDaily", "user_tap_daily", true),
            entity("com.ggukmoney.beanzip.domain.tap.entity.UserTapProgress", "user_tap_progress", true),
            entity("com.ggukmoney.beanzip.domain.point.entity.PointAccount", "point_account", true),
            entity("com.ggukmoney.beanzip.domain.point.entity.PointLedger", "point_ledger", true),
            entity("com.ggukmoney.beanzip.domain.cashout.entity.CashoutRequest", "cashout_request", true),
            entity("com.ggukmoney.beanzip.domain.booster.entity.BoosterGrant", "booster_grant", true)
    };

    private static final String[] REPOSITORIES = {
            "com.ggukmoney.beanzip.domain.user.repository.AppUserRepository",
            "com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository",
            "com.ggukmoney.beanzip.global.config.repository.AppConfigRepository",
            "com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository",
            "com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository",
            "com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository",
            "com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxOpenRepository",
            "com.ggukmoney.beanzip.domain.onboarding.repository.OnboardingRewardAttemptRepository",
            "com.ggukmoney.beanzip.domain.tap.repository.TapBatchRepository",
            "com.ggukmoney.beanzip.domain.tap.repository.UserTapDailyRepository",
            "com.ggukmoney.beanzip.domain.tap.repository.UserTapProgressRepository",
            "com.ggukmoney.beanzip.domain.point.repository.PointAccountRepository",
            "com.ggukmoney.beanzip.domain.point.repository.PointLedgerRepository",
            "com.ggukmoney.beanzip.domain.cashout.repository.CashoutRequestRepository",
            "com.ggukmoney.beanzip.domain.booster.repository.BoosterGrantRepository"
    };

    @Test
    void aDomainEntitiesExposeExpectedTableNamesAndIdentityFields() throws Exception {
        for (EntityContract contract : ENTITIES) {
            Class<?> entityClass = Class.forName(contract.className());

            Table table = entityClass.getAnnotation(Table.class);
            assertThat(table).as(contract.className() + " @Table").isNotNull();
            assertThat(table.name()).isEqualTo(contract.tableName());

            Field id = entityClass.getDeclaredField("id");
            assertThat(id.getType()).isEqualTo(contract.idType());
            assertThat(id.getAnnotation(Id.class)).isNotNull();

            if (contract.hasPublicId()) {
                assertThat(entityClass.getDeclaredField("publicId").getType()).isEqualTo(UUID.class);
            } else {
                assertThat(entityClass.getDeclaredFields())
                        .extracting(Field::getName)
                        .doesNotContain("publicId");
            }
        }
    }

    @Test
    void aDomainRepositoriesUseJpaRepository() throws Exception {
        for (String repositoryName : REPOSITORIES) {
            Class<?> repositoryClass = Class.forName(repositoryName);

            assertThat(JpaRepository.class).isAssignableFrom(repositoryClass);
        }
    }

    private static EntityContract entity(String className, String tableName, boolean hasPublicId) {
        return entity(className, tableName, hasPublicId, Long.class);
    }

    private static EntityContract entity(String className, String tableName, boolean hasPublicId, Class<?> idType) {
        return new EntityContract(className, tableName, hasPublicId, idType);
    }

    private record EntityContract(String className, String tableName, boolean hasPublicId, Class<?> idType) {
    }
}
