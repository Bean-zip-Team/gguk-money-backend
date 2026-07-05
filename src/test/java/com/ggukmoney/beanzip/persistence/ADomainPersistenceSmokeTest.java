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
            entity("com.ggukmoney.beanzip.domain.user.entity.AppUser", "app_user", true),
            entity("com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity", "auth_identity", true),
            entity("com.ggukmoney.beanzip.domain.legal.entity.LegalDocument", "legal_document", true),
            entity("com.ggukmoney.beanzip.domain.legal.entity.UserConsent", "user_consent", true),
            entity("com.ggukmoney.beanzip.domain.config.entity.AppConfig", "app_config", true),
            entity("com.ggukmoney.beanzip.domain.keycap.entity.Keycap", "keycap", true),
            entity("com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap", "user_keycap", true),
            entity("com.ggukmoney.beanzip.domain.keycap.entity.KeycapDropTable", "keycap_drop_table", true),
            entity("com.ggukmoney.beanzip.domain.keycap.entity.KeycapDropItem", "keycap_drop_item", true),
            entity("com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount", "keycap_box_account", true),
            entity("com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxLedger", "keycap_box_ledger", true),
            entity("com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen", "keycap_box_open", true),
            entity("com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpenResult", "keycap_box_open_result", true),
            entity("com.ggukmoney.beanzip.domain.region.entity.Region", "region", true),
            entity("com.ggukmoney.beanzip.domain.region.entity.UserRegion", "user_region", true),
            entity("com.ggukmoney.beanzip.domain.region.entity.UserRegionChange", "user_region_change", true),
            entity("com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason", "ranking_season", true),
            entity("com.ggukmoney.beanzip.domain.ranking.entity.RankingParticipation", "ranking_participation", true),
            entity("com.ggukmoney.beanzip.domain.ranking.entity.RankingScore", "ranking_score", true),
            entity("com.ggukmoney.beanzip.domain.ranking.entity.RankingScoreEvent", "ranking_score_event", true),
            entity("com.ggukmoney.beanzip.domain.ranking.entity.RankingSnapshot", "ranking_snapshot", true),
            entity("com.ggukmoney.beanzip.domain.ranking.entity.RankingReward", "ranking_reward", true),
            entity("com.ggukmoney.beanzip.domain.notification.entity.PushDevice", "push_device", true),
            entity("com.ggukmoney.beanzip.domain.notification.entity.NotificationPreference", "notification_preference", true),
            entity("com.ggukmoney.beanzip.domain.notification.entity.NotificationLog", "notification_log", true),
            entity("com.ggukmoney.beanzip.domain.record.entity.UserRecordDaily", "user_record_daily", true),
            entity("com.ggukmoney.beanzip.domain.record.entity.UserRecordSummary", "user_record_summary", true),
            entity("com.ggukmoney.beanzip.domain.record.entity.UserRecordReward", "user_record_reward", true),
            entity("com.ggukmoney.beanzip.domain.reliability.entity.EventOutbox", "event_outbox", false),
            entity("com.ggukmoney.beanzip.domain.reliability.entity.EventInbox", "event_inbox", false)
    };

    private static final String[] REPOSITORIES = {
            "com.ggukmoney.beanzip.domain.user.repository.AppUserRepository",
            "com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository",
            "com.ggukmoney.beanzip.domain.legal.repository.LegalDocumentRepository",
            "com.ggukmoney.beanzip.domain.legal.repository.UserConsentRepository",
            "com.ggukmoney.beanzip.domain.config.repository.AppConfigRepository",
            "com.ggukmoney.beanzip.domain.keycap.repository.KeycapRepository",
            "com.ggukmoney.beanzip.domain.keycap.repository.UserKeycapRepository",
            "com.ggukmoney.beanzip.domain.keycap.repository.KeycapDropTableRepository",
            "com.ggukmoney.beanzip.domain.keycap.repository.KeycapDropItemRepository",
            "com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxAccountRepository",
            "com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxLedgerRepository",
            "com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxOpenRepository",
            "com.ggukmoney.beanzip.domain.keycap.repository.KeycapBoxOpenResultRepository",
            "com.ggukmoney.beanzip.domain.region.repository.RegionRepository",
            "com.ggukmoney.beanzip.domain.region.repository.UserRegionRepository",
            "com.ggukmoney.beanzip.domain.region.repository.UserRegionChangeRepository",
            "com.ggukmoney.beanzip.domain.ranking.repository.RankingSeasonRepository",
            "com.ggukmoney.beanzip.domain.ranking.repository.RankingParticipationRepository",
            "com.ggukmoney.beanzip.domain.ranking.repository.RankingScoreRepository",
            "com.ggukmoney.beanzip.domain.ranking.repository.RankingScoreEventRepository",
            "com.ggukmoney.beanzip.domain.ranking.repository.RankingSnapshotRepository",
            "com.ggukmoney.beanzip.domain.ranking.repository.RankingRewardRepository",
            "com.ggukmoney.beanzip.domain.notification.repository.PushDeviceRepository",
            "com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository",
            "com.ggukmoney.beanzip.domain.notification.repository.NotificationLogRepository",
            "com.ggukmoney.beanzip.domain.record.repository.UserRecordDailyRepository",
            "com.ggukmoney.beanzip.domain.record.repository.UserRecordSummaryRepository",
            "com.ggukmoney.beanzip.domain.record.repository.UserRecordRewardRepository",
            "com.ggukmoney.beanzip.domain.reliability.repository.EventOutboxRepository",
            "com.ggukmoney.beanzip.domain.reliability.repository.EventInboxRepository"
    };

    @Test
    void aDomainEntitiesExposeExpectedTableNamesAndIdentityFields() throws Exception {
        for (EntityContract contract : ENTITIES) {
            Class<?> entityClass = Class.forName(contract.className());

            Table table = entityClass.getAnnotation(Table.class);
            assertThat(table).as(contract.className() + " @Table").isNotNull();
            assertThat(table.name()).isEqualTo(contract.tableName());

            Field id = entityClass.getDeclaredField("id");
            assertThat(id.getType()).isEqualTo(Long.class);
            assertThat(id.getAnnotation(Id.class)).isNotNull();

            if (contract.hasPublicId()) {
                assertThat(entityClass.getDeclaredField("publicId").getType()).isEqualTo(UUID.class);
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
        return new EntityContract(className, tableName, hasPublicId);
    }

    private record EntityContract(String className, String tableName, boolean hasPublicId) {
    }
}
