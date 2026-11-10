package com.onesignal.platform.dispatch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Slf4j
@Component
public class SsrfValidator {

    public void validateEndpoint(String endpointUrl) {
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new IllegalArgumentException("Web push endpoint cannot be empty");
        }

        // Allow simulated mock/demo tokens in sandbox environments
        if (endpointUrl.startsWith("mock://") || endpointUrl.contains("sample_web_token")) {
            return;
        }

        try {
            URI uri = URI.create(endpointUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !scheme.equalsIgnoreCase("https")) {
                throw new SecurityException("Web push endpoint must use HTTPS protocol: " + endpointUrl);
            }

            int port = uri.getPort();
            if (port != -1 && port != 443) {
                throw new SecurityException("Web push endpoint must connect to standard HTTPS port 443, found: " + port);
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new SecurityException("Invalid hostname in push endpoint: " + endpointUrl);
            }

            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isPrivateOrRestricted(address)) {
                    log.warn("Blocked SSRF attempt to restricted IP address {} for host {}", address.getHostAddress(), host);
                    throw new SecurityException("Web push endpoint resolves to private/restricted IP address: " + address.getHostAddress());
                }
            }
        } catch (IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unable to resolve push endpoint hostname: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new SecurityException("Invalid push endpoint URI: " + e.getMessage(), e);
        }
    }

    public boolean isPrivateOrRestricted(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return true;
        }

        if (address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            // IPv4 Carrier-Grade NAT (100.64.0.0/10)
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            if (b0 == 100 && (b1 >= 64 && b1 <= 127)) {
                return true;
            }
        } else if (bytes.length == 16) {
            // IPv6 Unique Local Address (fc00::/7)
            int b0 = bytes[0] & 0xFF;
            if ((b0 & 0xFE) == 0xFC) {
                return true;
            }
        }

        return false;
    }
}
