-- V5__init_events_and_partitioned_logs.sql
-- Client SDK Events, Filtering Indexes, and Sample Analytics Seed Data

-- ============================================================================
-- 1. Client Events Table
-- ============================================================================

CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    notification_id UUID REFERENCES notifications(id) ON DELETE SET NULL,
    device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    event_type VARCHAR(32) NOT NULL, -- DELIVERED, OPENED, CLICKED, CUSTOM
    event_name VARCHAR(128),
    properties JSONB NOT NULL DEFAULT '{}',
    client_timestamp TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_events_app_created ON events(app_id, created_at DESC);
CREATE INDEX idx_events_notification ON events(notification_id, event_type);
CREATE INDEX idx_events_device ON events(device_id);
CREATE INDEX idx_events_type ON events(app_id, event_type);

-- ============================================================================
-- 2. Compound Filter Indexes on Delivery Logs
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_delivery_logs_filter ON delivery_logs(app_id, provider, status, created_at DESC);

-- ============================================================================
-- 3. Seed Sample Events for Acme Retail Store
-- ============================================================================

-- Seed Delivered Events
INSERT INTO events (id, app_id, notification_id, device_id, event_type, event_name, created_at) VALUES
    (
        'eeeeeeee-1111-2222-3333-444444444441',
        '11111111-2222-3333-4444-555555555551',
        '99999999-1111-2222-3333-444444444441',
        'dddddddd-1111-2222-3333-444444444441',
        'DELIVERED',
        'notification_delivered',
        CURRENT_TIMESTAMP - INTERVAL '2 hours'
    ),
    (
        'eeeeeeee-1111-2222-3333-444444444442',
        '11111111-2222-3333-4444-555555555551',
        '99999999-1111-2222-3333-444444444441',
        'dddddddd-1111-2222-3333-444444444442',
        'DELIVERED',
        'notification_delivered',
        CURRENT_TIMESTAMP - INTERVAL '2 hours'
    ),
    (
        'eeeeeeee-1111-2222-3333-444444444443',
        '11111111-2222-3333-4444-555555555551',
        '99999999-1111-2222-3333-444444444441',
        'dddddddd-1111-2222-3333-444444444443',
        'DELIVERED',
        'notification_delivered',
        CURRENT_TIMESTAMP - INTERVAL '2 hours'
    );

-- Seed Opened & Clicked Events
INSERT INTO events (id, app_id, notification_id, device_id, event_type, event_name, properties, created_at) VALUES
    (
        'eeeeeeee-1111-2222-3333-444444444444',
        '11111111-2222-3333-4444-555555555551',
        '99999999-1111-2222-3333-444444444441',
        'dddddddd-1111-2222-3333-444444444441',
        'OPENED',
        'notification_opened',
        '{"action": "tap_banner"}',
        CURRENT_TIMESTAMP - INTERVAL '90 minutes'
    ),
    (
        'eeeeeeee-1111-2222-3333-444444444445',
        '11111111-2222-3333-4444-555555555551',
        '99999999-1111-2222-3333-444444444441',
        'dddddddd-1111-2222-3333-444444444442',
        'OPENED',
        'notification_opened',
        '{"action": "tap_banner"}',
        CURRENT_TIMESTAMP - INTERVAL '60 minutes'
    ),
    (
        'eeeeeeee-1111-2222-3333-444444444446',
        '11111111-2222-3333-4444-555555555551',
        '99999999-1111-2222-3333-444444444441',
        'dddddddd-1111-2222-3333-444444444441',
        'CLICKED',
        'button_clicked',
        '{"button": "shop_now", "url": "https://retail.acme.com/deals"}',
        CURRENT_TIMESTAMP - INTERVAL '85 minutes'
    );

-- Update sample notification opened_count to reflect events
UPDATE notifications SET opened_count = 2 WHERE id = '99999999-1111-2222-3333-444444444441';
