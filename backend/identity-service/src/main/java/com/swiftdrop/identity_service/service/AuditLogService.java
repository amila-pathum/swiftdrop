package com.swiftdrop.identity_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private static final Logger audit = LoggerFactory.getLogger("com.swiftdrop.audit");

    public void logAdminAction(long adminId, String action, long targetUserId, String detail) {
        audit.info("admin={} action={} target={} detail={}", adminId, action, targetUserId, detail);
    }

    public void logAdminAction(long adminId, String action, String detail) {
        audit.info("admin={} action={} detail={}", adminId, action, detail);
    }
}
