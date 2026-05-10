package com.microservices.analytics_report.security;

/**
 * Headers forwarded by the API gateway after JWT validation (same semantics as branch/menu services).
 */
public final class GatewayRoleHelper {

    private GatewayRoleHelper() {}

    public static boolean isHeadOfficeAdminRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            return false;
        }
        String r = userRole.trim();
        return "HEAD_OFFICE_ADMIN".equalsIgnoreCase(r)
                || "OFFICE_ADMIN".equalsIgnoreCase(r)
                || "Admin".equalsIgnoreCase(r);
    }
}
