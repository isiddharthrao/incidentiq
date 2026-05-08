package com.incidentmgmt.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Translates service-layer exceptions to user-facing error pages.
 *
 * Note: AccessDeniedException raised by @PreAuthorize is handled by Spring
 * Security's filter chain (see SecurityConfig.accessDeniedPage). This handler
 * fires for the same exception thrown manually from service code (e.g.,
 * IncidentService.findVisibleDetail when a Reporter touches another user's
 * incident).
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warn("Access denied for {} on {}: {}",
                request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous",
                request.getRequestURI(),
                e.getMessage());
        return "error/403";
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(EntityNotFoundException e, HttpServletRequest request) {
        log.info("Not found: {} ({})", request.getRequestURI(), e.getMessage());
        return "error/404";
    }

    /**
     * Catch-all so unexpected exceptions render the friendly 500 page rather
     * than Spring's Whitelabel error. We log the stack trace at ERROR so the
     * underlying issue is still visible in server logs.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleAny(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception on {}: {}", request.getRequestURI(), e.getMessage(), e);
        return "error/500";
    }
}
