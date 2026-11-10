package com.onesignal.platform.realtime.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

public class InboundMessageDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InboundRequest implements Serializable {
        private String action; // EVENT_ACK, PING, INBOUND_MESSAGE
        private UUID notificationId;
        private Long clientTimestamp;
        private String content;
        private Map<String, Object> payload;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InboundResponse implements Serializable {
        private String action; // PONG, ACK, ERROR
        private UUID notificationId;
        private Long serverTimestamp;
        private String status;
        private String message;
        private Map<String, Object> details;
    }
}
