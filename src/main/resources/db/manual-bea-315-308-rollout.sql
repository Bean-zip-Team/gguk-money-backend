-- Run after manual-weekly-ranking-reward.sql and manual-system-ranking-boost.sql.
-- This prepares runtime configuration only; both policies remain disabled.
BEGIN;

DO $$
DECLARE
    active_internal_accounts integer;
BEGIN
    SELECT COUNT(*)
      INTO active_internal_accounts
      FROM app_user
     WHERE id = ANY (ARRAY[
         '3368d399-1ea6-4609-b999-db853f6d494a'::uuid,
         '7dc0edad-f5ed-41a9-b828-4167c6646569'::uuid,
         '06b310bf-ea66-425f-ac6d-5ed0edcd3d3e'::uuid,
         '4b179019-3cd6-46c8-ad52-36517831f662'::uuid,
         'c115f184-fbaf-4ec0-9fe0-66734c985b57'::uuid,
         '98d72dd1-f642-4332-85b7-8261ca828a5c'::uuid
     ])
       AND status = 'ACTIVE';

    IF active_internal_accounts <> 6 THEN
        RAISE EXCEPTION 'expected 6 active internal accounts, found %', active_internal_accounts;
    END IF;
END $$;

INSERT INTO app_config (public_id, config_key, config_value, effective_at, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'ranking.weeklyReward.policy',
    '{"enabled":false,"rewards":{"1":10000,"2":5000,"3":2500}}'::jsonb,
    now(), now(), now()
);

INSERT INTO app_config (public_id, config_key, config_value, effective_at, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'ranking.systemBoost.policy',
    '{"enabled":false,"internalUserIds":["3368d399-1ea6-4609-b999-db853f6d494a","7dc0edad-f5ed-41a9-b828-4167c6646569","06b310bf-ea66-425f-ac6d-5ed0edcd3d3e","4b179019-3cd6-46c8-ad52-36517831f662","c115f184-fbaf-4ec0-9fe0-66734c985b57","98d72dd1-f642-4332-85b7-8261ca828a5c"],"minimumLeaderScore":1000,"minIncrement":200,"maxIncrement":500}'::jsonb,
    now(), now(), now()
);

COMMIT;

SELECT config_key, config_value, effective_at
FROM app_config
WHERE config_key IN ('ranking.weeklyReward.policy', 'ranking.systemBoost.policy')
ORDER BY config_key, effective_at DESC, id DESC;
