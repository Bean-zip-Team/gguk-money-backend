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
