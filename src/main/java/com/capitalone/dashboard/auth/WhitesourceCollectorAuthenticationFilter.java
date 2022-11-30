package com.capitalone.dashboard.auth;

import com.capitalone.chassis.engine.core.security.jwt.apigateway.pop.PreAuthenticatedPopTokenHeaderProcessingFilter;
import com.capitalone.dashboard.auth.apitoken.ApiTokenRequestFilter;
import com.capitalone.dashboard.auth.token.JwtAuthenticationFilter;
import com.capitalone.dashboard.util.CommonConstants;
import com.capitalone.dashboard.utils.Constants;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class WhitesourceCollectorAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LogManager.getLogger(WhitesourceCollectorAuthenticationFilter.class.getName());

    @Autowired
    private PreAuthenticatedPopTokenHeaderProcessingFilter popTokenHeaderProcessingFilter;
    @Autowired
    private ApiTokenRequestFilter apiTokenRequestFilter;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    public void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (Objects.isNull(request)) return;
        long startTime = System.currentTimeMillis();
        String apiUser = request.getHeader(CommonConstants.HEADER_API_USER);
        apiUser = (StringUtils.isEmpty(apiUser) ? "unknown" : apiUser);
        String authHeaderVal = request.getHeader("Authorization");

        String correlation_id = request.getHeader(CommonConstants.HEADER_CLIENT_CORRELATION_ID);
        correlation_id = (StringUtils.isEmpty(correlation_id)) ? "NULL" : correlation_id;
        response.addHeader(CommonConstants.HEADER_CLIENT_CORRELATION_ID, correlation_id);

        try {
            if (Objects.nonNull(authHeaderVal)) {
                if (authHeaderVal.startsWith("apiToken ")) {
                    apiTokenRequestFilter.doFilter(request, response, filterChain);
                } else if (authHeaderVal.startsWith("Bearer ")) {
                    jwtAuthenticationFilter.doFilter(request, response, filterChain);
                } else if (StringUtils.startsWith(authHeaderVal, "PoP ")) {
                    popTokenHeaderProcessingFilter.doFilter(request, response, filterChain);
                } else {
                    unsuccessfulAuthentication(request, response, "Authorization invalid");
                }
            } else {
                unsuccessfulAuthentication(request, response, "Authorization cannot be null or empty");
            }

        } catch (Exception e) {
            LOGGER.error("EXCEPTION - whitesourceCollectorAuthenticationFilter - doFilterInternal - " + e.getMessage());
            response.addHeader("message", e.getMessage());
            unsuccessfulAuthentication(request, response, "Authorization cannot be null or empty");

        } finally {
            if (!StringUtils.containsIgnoreCase(request.getRequestURI(), "ping")) {
                String parameters = MapUtils.isEmpty(request.getParameterMap()) ? "NONE" :
                        Collections.list(request.getParameterNames()).stream()
                                .map(p -> p + ":" + Arrays.asList(request.getParameterValues(p)))
                                .collect(Collectors.joining(","));
                apiUser = (authHeaderVal == null) ? (StringUtils.isNotEmpty(apiUser) ? apiUser : "READ_ONLY") : apiUser;
                LOGGER.info(" correlation_id=" + correlation_id + " application=hygieia, service=" + Constants.APP_NAME +
                        ", requester=" + apiUser
                        + ", duration=" + (System.currentTimeMillis() - startTime)
                        + ", uri=" + request.getRequestURI()
                        + ", request_method=" + request.getMethod()
                        + ", response_code=" + (response == null ? 0 : response.getStatus())
                        + ", client_ip=" + request.getRemoteAddr()
                        + (StringUtils.equalsIgnoreCase(request.getMethod(), "GET") ? ", request_params=" + parameters : StringUtils.EMPTY));
            }
        }

    }

    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSONObject.toJSONString(Map.of("message", message)));
    }

    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return HttpMethod.GET.matches(request.getMethod());
    }
}
