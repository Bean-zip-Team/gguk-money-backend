package com.ggukmoney.beanzip.domain.ranking.repository;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingEntry;
import com.ggukmoney.beanzip.domain.ranking.entity.RankingSeason;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RankingEntryRepository extends JpaRepository<RankingEntry, Long> {

    Optional<RankingEntry> findBySeasonAndUserId(RankingSeason season, UUID userId);

    @Query(value = """
            SELECT e.user_id AS userId,
                   u.nickname AS nickname,
                   u.profile_image_url AS profileImageUrl,
                   e.score AS score
            FROM ranking_entry e
            JOIN app_user u ON u.id = e.user_id
            WHERE e.season_id = :seasonId
              AND u.status = 'ACTIVE'
              AND e.score > 0
            ORDER BY e.score DESC, CAST(e.user_id AS text) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<RankingParticipantProjection> findTopParticipantProjections(
            @Param("seasonId") Long seasonId,
            @Param("limit") int limit
    );

    default List<RankingParticipantRow> findTopParticipants(RankingSeason season, int limit) {
        return findTopParticipantProjections(season.getId(), limit).stream()
                .map(RankingParticipantRow::from)
                .toList();
    }

    @Query(value = """
            SELECT e.user_id AS userId,
                   u.nickname AS nickname,
                   u.profile_image_url AS profileImageUrl,
                   e.score AS score
            FROM ranking_entry e
            JOIN app_user u ON u.id = e.user_id
            WHERE e.season_id = :seasonId
              AND e.user_id = :userId
              AND u.status = 'ACTIVE'
              AND e.score > 0
            """, nativeQuery = true)
    Optional<RankingParticipantProjection> findMyParticipantProjection(
            @Param("seasonId") Long seasonId,
            @Param("userId") UUID userId
    );

    default Optional<RankingParticipantRow> findMyParticipant(RankingSeason season, UUID userId) {
        return findMyParticipantProjection(season.getId(), userId).map(RankingParticipantRow::from);
    }

    @Query("""
            SELECT COUNT(entry)
            FROM RankingEntry entry
            WHERE entry.season = :season
              AND entry.user.status = com.ggukmoney.beanzip.domain.user.entity.AppUser$Status.ACTIVE
              AND entry.score > 0
            """)
    long countParticipants(@Param("season") RankingSeason season);

    @Query(value = """
            SELECT COUNT(*)
            FROM ranking_entry e
            JOIN app_user u ON u.id = e.user_id
            WHERE e.season_id = :seasonId
              AND u.status = 'ACTIVE'
              AND e.score > 0
              AND (
                    e.score > :score
                 OR (e.score = :score AND CAST(e.user_id AS text) > :userId)
              )
            """, nativeQuery = true)
    long countParticipantsAhead(
            @Param("seasonId") Long seasonId,
            @Param("score") long score,
            @Param("userId") String userId
    );

    default long countParticipantsAhead(RankingSeason season, long score, String userId) {
        return countParticipantsAhead(season.getId(), score, userId);
    }

    @Query("""
            SELECT entry
            FROM RankingEntry entry
            JOIN FETCH entry.user
            WHERE entry.season = :season
              AND (
                    entry.updatedAt > :lastProcessedUpdatedAt
                 OR (entry.updatedAt = :lastProcessedUpdatedAt AND entry.id > :lastProcessedEntryId)
              )
            ORDER BY entry.updatedAt ASC, entry.id ASC
            """)
    List<RankingEntry> findChangedEntries(
            @Param("season") RankingSeason season,
            @Param("lastProcessedUpdatedAt") Instant lastProcessedUpdatedAt,
            @Param("lastProcessedEntryId") Long lastProcessedEntryId,
            Pageable pageable
    );

    @Query("""
            SELECT entry
            FROM RankingEntry entry
            JOIN FETCH entry.user
            WHERE entry.season = :season
              AND entry.user.status = com.ggukmoney.beanzip.domain.user.entity.AppUser$Status.ACTIVE
              AND entry.score > 0
            ORDER BY entry.score DESC, entry.user.id DESC
            """)
    List<RankingEntry> findRebuildEntries(@Param("season") RankingSeason season, Pageable pageable);

    @Query("""
            SELECT entry
            FROM RankingEntry entry
            WHERE entry.season = :season
            ORDER BY entry.updatedAt DESC, entry.id DESC
            """)
    List<RankingEntry> findLastCursorEntry(@Param("season") RankingSeason season, Pageable pageable);

    @Query(value = """
            SELECT e.user_id AS userId,
                   u.nickname AS nickname,
                   u.profile_image_url AS profileImageUrl,
                   e.score AS score
            FROM ranking_entry e
            JOIN app_user u ON u.id = e.user_id
            WHERE e.season_id = :seasonId
              AND e.user_id IN (:userIds)
              AND u.status = 'ACTIVE'
              AND e.score > 0
            """, nativeQuery = true)
    List<RankingParticipantProjection> findParticipantProjectionsByUserIds(
            @Param("seasonId") Long seasonId,
            @Param("userIds") List<UUID> userIds
    );

    default List<RankingParticipantRow> findParticipantsByUserIds(RankingSeason season, List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return findParticipantProjectionsByUserIds(season.getId(), userIds).stream()
                .map(RankingParticipantRow::from)
                .toList();
    }

    interface RankingParticipantProjection {
        UUID getUserId();

        String getNickname();

        String getProfileImageUrl();

        Long getScore();
    }

    record RankingParticipantRow(
            UUID userId,
            String nickname,
            String profileImageUrl,
            long score
    ) {
        static RankingParticipantRow from(RankingParticipantProjection projection) {
            return new RankingParticipantRow(
                    projection.getUserId(),
                    projection.getNickname(),
                    projection.getProfileImageUrl(),
                    projection.getScore()
            );
        }
    }
}
