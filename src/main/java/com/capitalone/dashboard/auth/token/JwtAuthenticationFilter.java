package com.capitalone.dashboard.auth.token;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JwtAuthenticationFilter {

    private static final Logger LOGGER = LogManager.getLogger(JwtAuthenticationFilter.class);
    private TokenAuthenticationService tokenAuthenticationService;

    @Autowired
    public JwtAuthenticationFilter(TokenAuthenticationService tokenAuthenticationService) {
        this.tokenAuthenticationService = tokenAuthenticationService;
    }
    public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        if (request == null) return;

        try {
            Authentication authentication = tokenAuthenticationService.getAuthentication(request);
            if (authentication == null) {
                //Handle Expired or bad JWT tokens
                LOGGER.info("Expired or bad JWT tokens, set response status to HttpServletResponse.SC_UNAUTHORIZED");
                if (response != null)
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                filterChain.doFilter(request, response);
            } else {
                // process properly authenticated requests
                SecurityContextHolder.getContext().setAuthentication(authentication);
                filterChain.doFilter(request, response);
                tokenAuthenticationService.addAuthentication(response, authentication);
            }
        } catch (Exception e) {
            LOGGER.error("JwtAuthenticationFilter - doFilter - EXCEPTION - " + e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
        }
    }
}
