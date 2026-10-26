package com.ciemsa.sigma.shared.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * Seguridad de la API (RF-20, HU-01): sesión por cookie HttpOnly y protección CSRF. Todo exige
 * sesión salvo el login y el ping; los permisos por rol se declaran en cada endpoint
 * con @PreAuthorize y se verifican en cada pedido.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, ManejadorErroresSeguridad manejadorErrores) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/error")
                    .permitAll()
                    .requestMatchers("/api/v1/ping")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/sesion")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .csrf(csrf -> csrf.spa())
        .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
        .requestCache(RequestCacheConfigurer::disable)
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(manejadorErrores)
                    .accessDeniedHandler(manejadorErrores));
    return http.build();
  }

  /** Contraseñas con hash BCrypt, nunca en texto plano (CU-12). */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
