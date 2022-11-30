package com.capitalone.dashboard.auth.token;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Service
public interface TokenAuthenticationService {

    void addAuthentication(HttpServletResponse response, Authentication authentication);

    Authentication getAuthentication(HttpServletRequest request);

}
