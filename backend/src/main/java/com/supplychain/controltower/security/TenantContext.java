package com.supplychain.controltower.security;

import com.supplychain.controltower.exception.TenantContextMissingException;

public class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TENANT_CODE = new ThreadLocal<>();

    public static void setCompanyId(Long companyId) {
        CURRENT_TENANT_ID.set(companyId);
    }

    public static void setCurrentCompanyId(Long companyId) {
        setCompanyId(companyId);
    }

    public static Long getCompanyId() {
        return CURRENT_TENANT_ID.get();
    }

    public static Long getRequiredCompanyId() {
        Long companyId = CURRENT_TENANT_ID.get();
        if (companyId == null) {
            throw new TenantContextMissingException("Missing or invalid company tenant context. Access denied.");
        }
        return companyId;
    }

    public static void setCompanyCode(String companyCode) {
        CURRENT_TENANT_CODE.set(companyCode);
    }

    public static String getCompanyCode() {
        return CURRENT_TENANT_CODE.get();
    }

    public static void clear() {
        CURRENT_TENANT_ID.remove();
        CURRENT_TENANT_CODE.remove();
    }
}
