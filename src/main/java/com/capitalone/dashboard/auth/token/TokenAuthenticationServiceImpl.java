package com.capitalone.dashboard.auth.token;

import com.capitalone.dashboard.auth.AuthProperties;
import com.google.common.collect.Sets;
import io.jsonwebtoken.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.Date;

@Service
public class TokenAuthenticationServiceImpl implements TokenAuthenticationService {

    private static final Logger LOGGER = LogManager.getLogger(TokenAuthenticationServiceImpl.class);
    private static final String AUTHORIZATION = "Authorization";
    private static final String AUTH_PREFIX_W_SPACE = "Bearer ";
    private static final String AUTH_RESPONSE_HEADER = "X-Authentication-Token";
    private static final String ROLES_CLAIM = "roles";
    private static final String DETAILS_CLAIM = "details";

    private AuthProperties authProperties;

    @Autowired
    public TokenAuthenticationServiceImpl(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @Override
    public void addAuthentication(HttpServletResponse response, Authentication authentication) {
        String jwt = Jwts.builder().setSubject(authentication.getName())
                .claim(DETAILS_CLAIM, authentication.getDetails())
                .claim(ROLES_CLAIM, getRoles(authentication.getAuthorities()))
                .setExpiration(new Date(System.currentTimeMillis() + authProperties.getExpirationTime()))
                .signWith(SignatureAlgorithm.HS512, authProperties.getSecret()).compact();
        response.addHeader(AUTH_RESPONSE_HEADER, jwt);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Authentication getAuthentication(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION);
        if (StringUtils.isBlank(authHeader)) return null;

        String token = StringUtils.removeStart(authHeader, AUTH_PREFIX_W_SPACE);
        try {
            Claims claims = Jwts.parser().setSigningKey(authProperties.getSecret()).parseClaimsJws(token).getBody();
            String username = claims.getSubject();
            Collection<? extends GrantedAuthority> authorities = getAuthorities(claims.get(ROLES_CLAIM, Collection.class));
            PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(username, null, authorities);
            authentication.setDetails(claims.get(DETAILS_CLAIM));

            return authentication;

        } catch (ExpiredJwtException | SignatureException | MalformedJwtException e) {
            LOGGER.error("TokenAuthenticationServiceImpl - getAuthentication - " + e.getMessage());
            return null;
        }
    }

    private Collection<String> getRoles(Collection<? extends GrantedAuthority> authorities) {
        Collection<String> roles = Sets.newHashSet();
        authorities.forEach(authority -> {
            roles.add(authority.getAuthority());
        });

        return roles;
    }

    private Collection<? extends GrantedAuthority> getAuthorities(Collection<String> roles) {
        Collection<GrantedAuthority> authorities = Sets.newHashSet();
        roles.forEach(role -> {
            authorities.add(new SimpleGrantedAuthority(role));
        });

        return authorities;
    }

}
