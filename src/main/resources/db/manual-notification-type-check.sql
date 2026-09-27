-- BEA-299: Hibernate-generated notification_type checks predate DAILY_MISSION, so saving
-- that preference fails with a check violation (500). Re-create both checks with every
-- NotificationType value. Update this list whenever a NotificationType is added.
BEGIN;

ALTER TABLE notification_preference
    DROP CONSTRAINT IF EXISTS notification_preference_notification_type_check;
ALTER TABLE notification_preference
    ADD CONSTRAINT notification_preference_notification_type_check
    CHECK (notification_type IN (
        'WEEKLY_REWARD_AVAILABLE', 'RANK_CHANGE', 'BOOSTER_RECHARGED', 'DAILY_REMINDER',
        'DAILY_MISSION', 'BOOSTER_UNUSED', 'KEYCAP_BOX_OPEN_AVAILABLE'
    ));

ALTER TABLE notification_delivery
    DROP CONSTRAINT IF EXISTS notification_delivery_notification_type_check;
ALTER TABLE notification_delivery
    ADD CONSTRAINT notification_delivery_notification_type_check
    CHECK (notification_type IN (
        'WEEKLY_REWARD_AVAILABLE', 'RANK_CHANGE', 'BOOSTER_RECHARGED', 'DAILY_REMINDER',
        'DAILY_MISSION', 'BOOSTER_UNUSED', 'KEYCAP_BOX_OPEN_AVAILABLE'
    ));

COMMIT;
