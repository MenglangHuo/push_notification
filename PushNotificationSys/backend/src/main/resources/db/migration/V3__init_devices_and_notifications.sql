-- V3__init_devices_and_notifications.sql
-- Devices, Device Tags, Notifications, and Delivery Logs

-- ============================================================================
-- 1. Devices Table
-- ============================================================================

CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    external_user_id VARCHAR(255),
    platform VARCHAR(32) NOT NULL, -- ANDROID, IOS, WEB
    push_token TEXT NOT NULL,
    web_auth_key TEXT,
    web_p256dh_key TEXT,
    device_model VARCHAR(128),
    os_version VARCHAR(64),
    app_version VARCHAR(64),
    language VARCHAR(16) NOT NULL DEFAULT 'en',
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_active_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_devices_app_token UNIQUE(app_id, push_token)
);

CREATE INDEX idx_devices_app_id ON devices(app_id);
CREATE INDEX idx_devices_app_external_user ON devices(app_id, external_user_id);
CREATE INDEX idx_devices_app_platform_active ON devices(app_id, platform, is_active);
CREATE INDEX idx_devices_app_active ON devices(app_id, is_active);

-- ============================================================================
-- 2. Device Tags Table
-- ============================================================================

CREATE TABLE device_tags (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    tag_key VARCHAR(128) NOT NULL,
    tag_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_device_tags_key UNIQUE(device_id, tag_key)
);

CREATE INDEX idx_device_tags_device ON device_tags(device_id);
CREATE INDEX idx_device_tags_lookup ON device_tags(tag_key, tag_value);

-- ============================================================================
-- 3. Notifications Table
-- ============================================================================

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    image_url TEXT,
    deep_link TEXT,
    custom_data JSONB NOT NULL DEFAULT '{}',
    priority VARCHAR(32) NOT NULL DEFAULT 'HIGH', -- HIGH, NORMAL
    target_type VARCHAR(32) NOT NULL DEFAULT 'ALL', -- ALL, SEGMENT, DEVICE_IDS, EXTERNAL_USER_IDS
    target_payload JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED', -- DRAFT, QUEUED, PROCESSING, COMPLETED, FAILED
    idempotency_key VARCHAR(255),
    sent_count INTEGER NOT NULL DEFAULT 0,
    delivered_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    opened_count INTEGER NOT NULL DEFAULT 0,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notifications_app_idempotency UNIQUE(app_id, idempotency_key)
);

CREATE INDEX idx_notifications_app_created ON notifications(app_id, created_at DESC);
CREATE INDEX idx_notifications_app_status ON notifications(app_id, status);

-- ============================================================================
-- 4. Delivery Logs Table
-- ============================================================================

CREATE TABLE delivery_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
    device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    app_id UUID NOT NULL REFERENCES apps(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL, -- FCM, APNS, WEB_PUSH
    status VARCHAR(32) NOT NULL, -- SUCCESS, FAILED, RETRYING
    status_code VARCHAR(64),
    error_message TEXT,
    latency_ms INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_delivery_logs_notif ON delivery_logs(notification_id);
CREATE INDEX idx_delivery_logs_app_created ON delivery_logs(app_id, created_at DESC);
CREATE INDEX idx_delivery_logs_device ON delivery_logs(device_id);

-- ============================================================================
-- 5. Seed Demo Devices and Notification for Acme Retail Store
-- ============================================================================

-- Seed Android Device
INSERT INTO devices (id, app_id, external_user_id, platform, push_token, device_model, os_version, app_version, language, timezone)
VALUES (
    'dddddddd-1111-2222-3333-444444444441',
    '11111111-2222-3333-4444-555555555551',
    'alice_buyer',
    'ANDROID',
    'fcm_sample_token_alice_android_123',
    'Google Pixel 8',
    'Android 14',
    '2.4.0',
    'en',
    'America/New_York'
);

INSERT INTO device_tags (device_id, tag_key, tag_value) VALUES
    ('dddddddd-1111-2222-3333-444444444441', 'tier', 'VIP'),
    ('dddddddd-1111-2222-3333-444444444441', 'category', 'electronics');

-- Seed iOS Device
INSERT INTO devices (id, app_id, external_user_id, platform, push_token, device_model, os_version, app_version, language, timezone)
VALUES (
    'dddddddd-1111-2222-3333-444444444442',
    '11111111-2222-3333-4444-555555555551',
    'bob_shopper',
    'IOS',
    'apns_sample_token_bob_iphone_456',
    'iPhone 15 Pro',
    'iOS 17.5',
    '2.4.0',
    'en',
    'Europe/London'
);

INSERT INTO device_tags (device_id, tag_key, tag_value) VALUES
    ('dddddddd-1111-2222-3333-444444444442', 'tier', 'Regular');

-- Seed Web Device
INSERT INTO devices (id, app_id, external_user_id, platform, push_token, web_auth_key, web_p256dh_key, device_model, os_version, app_version, language, timezone)
VALUES (
    'dddddddd-1111-2222-3333-444444444443',
    '11111111-2222-3333-4444-555555555551',
    'charlie_web',
    'WEB',
    'https://fcm.googleapis.com/fcm/send/sample_web_token_789',
    'auth_sample_secret_key_web_demo',
    'BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QT9AcUbVU5m2JhGl00SknBQ90eaErIdjfPrDH5PEG00k',
    'Chrome on macOS',
    '126.0.0',
    '1.0.0',
    'en',
    'Asia/Phnom_Penh'
);

INSERT INTO device_tags (device_id, tag_key, tag_value) VALUES
    ('dddddddd-1111-2222-3333-444444444443', 'browser', 'Chrome');

-- Seed Sample Notification
INSERT INTO notifications (id, app_id, title, body, deep_link, priority, target_type, status, sent_count, delivered_count)
VALUES (
    '99999999-1111-2222-3333-444444444441',
    '11111111-2222-3333-4444-555555555551',
    'Welcome to Acme Retail! 🛍️',
    'Check out this week''s flash deals with 20% off coupon code: ACME20',
    'https://retail.acme.com/deals',
    'HIGH',
    'ALL',
    'COMPLETED',
    3,
    3
);

-- Seed Delivery Logs for the sample notification
INSERT INTO delivery_logs (notification_id, device_id, app_id, provider, status, status_code, latency_ms) VALUES
    ('99999999-1111-2222-3333-444444444441', 'dddddddd-1111-2222-3333-444444444441', '11111111-2222-3333-4444-555555555551', 'FCM', 'SUCCESS', '200', 142),
    ('99999999-1111-2222-3333-444444444441', 'dddddddd-1111-2222-3333-444444444442', '11111111-2222-3333-4444-555555555551', 'APNS', 'SUCCESS', '200', 98),
    ('99999999-1111-2222-3333-444444444441', 'dddddddd-1111-2222-3333-444444444443', '11111111-2222-3333-4444-555555555551', 'WEB_PUSH', 'SUCCESS', '201', 215);
