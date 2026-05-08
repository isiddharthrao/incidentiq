package com.incidentmgmt.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Renders custom error pages that are reachable by URL (so Spring Security can
 * forward to /error/403 via accessDeniedPage). Spring Boot's BasicErrorController
 * already handles 404 and 500 by looking up templates/error/{code}.html.
 */
@Controller
public class ErrorPageController {

    @GetMapping("/error/403")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String forbidden() {
        return "error/403";
    }

    @GetMapping("/error/ai-unavailable")
    public String aiUnavailable() {
        return "error/ai-unavailable";
    }
}
