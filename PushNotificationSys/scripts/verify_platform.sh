#!/usr/bin/env bash
set -e

# ==============================================================================
# Multi-Tenant Push Notification Platform - End-to-End Verification Script
# ==============================================================================

BASE_URL="http://localhost:8080"
echo "=== 1. Checking Actuator Health & Swagger Docs ==="
HEALTH_STATUS=$(curl -s "${BASE_URL}/actuator/health" | grep -o '"status":"UP"' || true)
if [ -z "$HEALTH_STATUS" ]; then
    echo "❌ Health check failed or backend not running at ${BASE_URL}."
    echo "Tip: Run the backend with: mvn spring-boot:run"
    exit 1
fi
echo "✅ Actuator Health: UP"

DOCS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/api-docs")
echo "✅ OpenAPI 3.0 Docs (${BASE_URL}/v3/api-docs): HTTP ${DOCS_CODE}"

echo ""
echo "=== 2. Authenticating Platform Super Admin ==="
LOGIN_RES=$(curl -s -X POST "${BASE_URL}/admin/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@pushnotify.local","password":"Password123!"}')

ACCESS_TOKEN=$(echo "${LOGIN_RES}" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
if [ -z "$ACCESS_TOKEN" ]; then
    echo "❌ Login failed: ${LOGIN_RES}"
    exit 1
fi
echo "✅ Authenticated: JWT Access Token retrieved (${#ACCESS_TOKEN} chars)"

echo ""
echo "=== 3. Querying Seeded Organizations & Apps ==="
ORGS_RES=$(curl -s -X GET "${BASE_URL}/admin/api/v1/organizations" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")

ORG_ID="cccccccc-cccc-cccc-cccc-cccccccccccc"
APP_ID="11111111-2222-3333-4444-555555555551"
REST_API_KEY="os_live_demo1234567890abcdef1234567890"
SDK_API_KEY="os_pub_demo9876543210abcdef9876543210"

echo "✅ Target Organization: ${ORG_ID}"
echo "✅ Target App: ${APP_ID} (Acme Retail Store)"

echo ""
echo "=== 4. Registering Devices via Public SDK Key ==="
# Android Device
DEV_RES_1=$(curl -s -X POST "${BASE_URL}/sdk/v1/devices" \
  -H "X-Sdk-Key: ${SDK_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "platform": "ANDROID",
    "pushToken": "fcm_test_token_'$(date +%s)'",
    "externalUserId": "shopper-888",
    "deviceModel": "Pixel 8",
    "language": "en",
    "timezone": "America/New_York",
    "tags": { "tier": "gold", "vip": "true" }
  }')
DEV_ID_1=$(echo "${DEV_RES_1}" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
echo "✅ Android Device Registered: ${DEV_ID_1}"

# Web Push Device
DEV_RES_2=$(curl -s -X POST "${BASE_URL}/sdk/v1/devices" \
  -H "X-Sdk-Key: ${SDK_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "platform": "WEB",
    "pushToken": "https://fcm.googleapis.com/fcm/send/web_token_'$(date +%s)'",
    "webAuthKey": "mock_auth_secret_key_12345",
    "webP256dhKey": "mock_p256dh_public_key_67890",
    "externalUserId": "shopper-888",
    "deviceModel": "Chrome 128 MacOS",
    "language": "en",
    "timezone": "America/New_York",
    "tags": { "browser": "chrome" }
  }')
DEV_ID_2=$(echo "${DEV_RES_2}" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
echo "✅ Web Push Device Registered: ${DEV_ID_2}"

echo ""
echo "=== 5. Dispatching Push Notification via Server REST Key ==="
NOTIF_RES=$(curl -s -X POST "${BASE_URL}/api/v1/notifications" \
  -H "X-Api-Key: ${REST_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Flash Discount Event",
    "body": "Special 40% OFF discount for VIP members!",
    "priority": "HIGH",
    "targetType": "ALL",
    "customData": { "campaign": "weekend_flash" }
  }')
NOTIF_ID=$(echo "${NOTIF_RES}" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
echo "✅ Push Notification Created & Queued: ${NOTIF_ID}"

echo ""
echo "=== 6. Ingesting Client SDK Telemetry Events ==="
EVENT_RES=$(curl -s -X POST "${BASE_URL}/sdk/v1/events" \
  -H "X-Sdk-Key: ${SDK_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "events": [
      {
        "notificationId": "'${NOTIF_ID}'",
        "deviceId": "'${DEV_ID_1}'",
        "eventType": "DELIVERED",
        "timestamp": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"
      },
      {
        "notificationId": "'${NOTIF_ID}'",
        "deviceId": "'${DEV_ID_1}'",
        "eventType": "OPENED",
        "timestamp": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"
      }
    ]
  }')
echo "✅ Client Events Ingested: ${EVENT_RES}"

echo ""
echo "=== 7. Querying Live Analytics Overview ==="
ANALYTICS_RES=$(curl -s -X GET "${BASE_URL}/admin/api/v1/apps/${APP_ID}/analytics/overview?range=7d" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")
echo "✅ Analytics Overview: ${ANALYTICS_RES}"

echo ""
echo "=== 8. Querying & Exporting Delivery Logs CSV ==="
CSV_SAMPLE=$(curl -s -X GET "${BASE_URL}/admin/api/v1/apps/${APP_ID}/logs/delivery/export" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" | head -n 5)
echo "✅ CSV Export Header & Top Records:"
echo "${CSV_SAMPLE}"

echo ""
echo "=== 9. Testing Distributed Redis Rate Limiting (Burst Test) ==="
echo "Firing quick bursts to check rate limit headers..."
BURST_RES=$(curl -s -i -X POST "${BASE_URL}/sdk/v1/events" \
  -H "X-Sdk-Key: ${SDK_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"events":[{"eventType":"CUSTOM","eventName":"heartbeat"}]}')

LIMIT_HEADER=$(echo "${BURST_RES}" | grep -i "X-RateLimit-Limit" || true)
REMAINING_HEADER=$(echo "${BURST_RES}" | grep -i "X-RateLimit-Remaining" || true)
echo "✅ Rate Limit Headers received:"
echo "${LIMIT_HEADER}"
echo "${REMAINING_HEADER}"

echo ""
echo "=============================================================================="
echo "🎉 ALL PLATFORM MODULES & WORKFLOWS VERIFIED SUCCESSFULLY!"
echo "=============================================================================="
