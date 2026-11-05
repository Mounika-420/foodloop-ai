package com.foodloop.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodloop.entity.AuditLog;
import com.foodloop.repository.AuditLogRepository;
import com.foodloop.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Writes security-relevant actions to audit_logs (joins the caller's transaction). */
@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public void log(Long userId, String action, String entityType, Long entityId, Object oldValue, Object newValue) {
        AuditLog entry = new AuditLog();
        if (userId != null) entry.setUser(userRepository.getReferenceById(userId));
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setOldValue(toJson(oldValue));
        entry.setNewValue(toJson(newValue));
        entry.setIpAddress(currentIp());
        auditLogRepository.save(entry);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise audit value: {}", e.getMessage());
            return null;
        }
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
