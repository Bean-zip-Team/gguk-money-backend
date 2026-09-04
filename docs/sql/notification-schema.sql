CREATE TABLE notification_preference (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    notification_type VARCHAR(60) NOT NULL,
    enabled BOOLEAN NOT NULL,
    agreement_status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_notification_preference_user_type UNIQUE (user_id, notification_type)
);

CREATE TABLE notification_delivery (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    notification_type VARCHAR(60) NOT NULL,
    dedupe_key VARCHAR(255) NOT NULL,
    template_set_code VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    content_id VARCHAR(255),
    failure_code VARCHAR(120),
    failure_reason TEXT,
    provider_response_json TEXT,
    requested_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_notification_delivery_dedupe_key UNIQUE (dedupe_key)
);

CREATE TABLE notification_rank_state (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    season_id BIGINT NOT NULL,
    baseline_rank BIGINT NOT NULL,
    baseline_recorded_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_notification_rank_state_user_season UNIQUE (user_id, season_id)
);

CREATE INDEX ix_notification_delivery_user_type ON notification_delivery (user_id, notification_type);
CREATE INDEX ix_notification_rank_state_user ON notification_rank_state (user_id);
