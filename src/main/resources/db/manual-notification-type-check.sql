-- BEA-299: Hibernate-generated notification_type checks predate DAILY_MISSION, so saving
-- that preference fails with a check violation (500). Re-create both checks with every
-- NotificationType value. Update this list whenever a NotificationType is added.
-- BEA-390 added FRIEND, FRIEND_CHEER and FRIEND_INVITE_SUCCESS. Without them, saving the friend
-- consent fails with 500 and every friend push insert fails. Safe to re-run.
BEGIN;

-- ADD CONSTRAINT takes an ACCESS EXCLUSIVE lock. Queued behind a long transaction it would hold up
-- every read and write on these tables, so give up quickly and retry instead. No statement timeout:
-- validating the check reads the whole table, and how long that takes depends on its size.
SET LOCAL lock_timeout = '3s';

ALTER TABLE notification_preference
    DROP CONSTRAINT IF EXISTS notification_preference_notification_type_check;
ALTER TABLE notification_preference
    ADD CONSTRAINT notification_preference_notification_type_check
    CHECK (notification_type IN (
        'WEEKLY_REWARD_AVAILABLE', 'RANK_CHANGE', 'BOOSTER_RECHARGED', 'DAILY_REMINDER',
        'DAILY_MISSION', 'BOOSTER_UNUSED', 'KEYCAP_BOX_OPEN_AVAILABLE',
        'FRIEND', 'FRIEND_CHEER', 'FRIEND_INVITE_SUCCESS'
    ));

ALTER TABLE notification_delivery
    DROP CONSTRAINT IF EXISTS notification_delivery_notification_type_check;
ALTER TABLE notification_delivery
    ADD CONSTRAINT notification_delivery_notification_type_check
    CHECK (notification_type IN (
        'WEEKLY_REWARD_AVAILABLE', 'RANK_CHANGE', 'BOOSTER_RECHARGED', 'DAILY_REMINDER',
        'DAILY_MISSION', 'BOOSTER_UNUSED', 'KEYCAP_BOX_OPEN_AVAILABLE',
        'FRIEND', 'FRIEND_CHEER', 'FRIEND_INVITE_SUCCESS'
    ));

COMMIT;
