package com.capitalone.dashboard.config;

import com.capitalone.dashboard.auth.AuthProperties;
import com.capitalone.dashboard.auth.AuthenticationResultHandler;
import com.capitalone.dashboard.auth.apitoken.ApiTokenAuthenticationProvider;
import com.capitalone.dashboard.auth.apitoken.ApiTokenRequestFilter;
import com.capitalone.dashboard.auth.token.JwtAuthenticationFilter;
import com.capitalone.dashboard.auth.token.TokenAuthenticationServiceImpl;
import com.capitalone.dashboard.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.client.RestTemplate;


@Configuration
@EnableWebSecurity
@EnableConfigurationProperties
@EnableGlobalMethodSecurity(prePostEnabled = true)
@ComponentScan(basePackages = "com.capitalone.dashboard.settings")
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

	@Autowired
	private AuthenticationResultHandler authenticationResultHandler;

	@Autowired
	private ApiTokenAuthenticationProvider apiTokenAuthenticationProvider;

	@Autowired
	private AuthProperties authProperties;

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.headers().cacheControl();
		http.csrf().disable()
				.authorizeRequests()
				.antMatchers(HttpMethod.GET, "/**").permitAll()
				.anyRequest().authenticated()
				.and()
				.addFilterBefore(apiTokenRequestFilter(), UsernamePasswordAuthenticationFilter.class)
				.exceptionHandling().authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
	}

	@Override
	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
		auth.authenticationProvider(apiTokenAuthenticationProvider);
	}

	@Bean
	protected ApiTokenRequestFilter apiTokenRequestFilter() throws Exception {
		return new ApiTokenRequestFilter("/**", authenticationManager(), authenticationResultHandler);
	}


	@Bean
	public RestClient restClientUpgrade() {
		return new RestClient(RestTemplate::new);
	}

	@Bean
	protected AuthenticationManager authenticationManager() throws Exception {
		return super.authenticationManager();
	}

	@Bean
	protected JwtAuthenticationFilter jwtAuthenticationFilter() {
		return new JwtAuthenticationFilter(new TokenAuthenticationServiceImpl(authProperties));
	}

}
