-- PostgreSQL partial indexes that JPA @Index cannot represent.
-- Manual operations only: inspect duplicate data and existing indexes first,
-- then run each statement outside a transaction against the confirmed runtime database.

CREATE UNIQUE INDEX CONCURRENTLY ux_app_user_active_nickname_normalized
ON app_user (nickname_normalized)
WHERE nickname_normalized IS NOT NULL
  AND status = 'ACTIVE';

CREATE UNIQUE INDEX CONCURRENTLY ux_user_keycap_equipped
ON user_keycap (user_id)
WHERE equipped = true;

CREATE UNIQUE INDEX CONCURRENTLY uq_keycap_box_open_ad_reward_id
ON keycap_box_open (ad_reward_id)
WHERE ad_reward_id IS NOT NULL;

CREATE INDEX CONCURRENTLY ix_notification_preference_sendable_type_id
ON notification_preference (notification_type, id)
INCLUDE (user_id)
WHERE enabled = true
  AND agreement_status = 'AGREED';

CREATE INDEX CONCURRENTLY ix_notification_delivery_sent_cooldown
ON notification_delivery (user_id, notification_type, requested_at DESC)
WHERE status = 'SENT';

CREATE UNIQUE INDEX CONCURRENTLY uq_promotion_grant_toss_key
ON promotion_grant (toss_promotion_key)
WHERE toss_promotion_key IS NOT NULL;

-- Matches PromotionGrantRepository.findDueForUpdate. Without it the retry
-- scheduler silently degrades to a sequential scan.
CREATE INDEX CONCURRENTLY ix_promotion_grant_due
ON promotion_grant (next_attempt_at, created_at)
WHERE status IN ('PENDING', 'PROCESSING')
  AND hold_reason IS NULL;
