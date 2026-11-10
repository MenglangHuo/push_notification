-- V4__init_segments_and_schedules.sql
-- Segments, Filter AST, and Notification Campaign Schedules

-- ============================================================================
-- 1. Segments Table
-- ============================================================================

CREATE TABLE segments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    filter_ast JSONB NOT NULL DEFAULT '{"conjunction":"AND","rules":[]}',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_segments_app_name UNIQUE(app_id, name)
);

CREATE INDEX idx_segments_app_id ON segments(app_id);
CREATE INDEX idx_segments_app_active ON segments(app_id, is_active);

-- ============================================================================
-- 2. Notification Schedules Table
-- ============================================================================

CREATE TABLE notification_schedules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    schedule_type VARCHAR(32) NOT NULL DEFAULT 'FIXED_TIME', -- FIXED_TIME, USER_TIMEZONE
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    user_target_time VARCHAR(8), -- e.g. '09:00' for morning delivery in local timezone
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, TRIGGERED, CANCELLED, FAILED
    triggered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notif_schedules_app_status ON notification_schedules(app_id, status);
CREATE INDEX idx_notif_schedules_pending ON notification_schedules(status, scheduled_at);
CREATE INDEX idx_notif_schedules_notification ON notification_schedules(notification_id);

-- ============================================================================
-- 3. ShedLock Table (Optional Fallback / Audit for Distributed Locking)
-- ============================================================================

CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

-- ============================================================================
-- 4. Seed Sample Segments for Demo App (Acme Retail Store)
-- ============================================================================

INSERT INTO segments (id, app_id, name, description, filter_ast) VALUES
    (
        'bbbbbbbb-1111-2222-3333-444444444441',
        '11111111-2222-3333-4444-555555555551',
        'VIP Customers',
        'Users flagged with tier VIP tag',
        '{"conjunction":"AND","rules":[{"field":"tag","tagKey":"tier","op":"EQ","value":"VIP"}]}'
    ),
    (
        'bbbbbbbb-1111-2222-3333-444444444442',
        '11111111-2222-3333-4444-555555555551',
        'Active iOS Users',
        'Active Apple iPhone and iPad users',
        '{"conjunction":"AND","rules":[{"field":"platform","op":"EQ","value":"IOS"},{"field":"is_active","op":"EQ","value":"true"}]}'
    ),
    (
        'bbbbbbbb-1111-2222-3333-444444444443',
        '11111111-2222-3333-4444-555555555551',
        'Android Electronics Shoppers',
        'Android users interested in consumer electronics',
        '{"conjunction":"AND","rules":[{"field":"platform","op":"EQ","value":"ANDROID"},{"field":"tag","tagKey":"category","op":"EQ","value":"electronics"}]}'
    );
