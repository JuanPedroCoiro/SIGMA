package com.ciemsa.sigma.shared.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Seguridad de la API (RF-20, HU-01): sesión por cookie HttpOnly y protección CSRF. Todo exige
 * sesión salvo el login y el ping; los permisos por rol se declaran en cada endpoint
 * con @PreAuthorize y se verifican en cada pedido.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private static final String SESION = "/api/v1/sesion";

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, ManejadorErroresSeguridad manejadorErrores) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/error")
                    .permitAll()
                    .requestMatchers("/api/v1/ping", "/actuator/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, SESION)
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            login ->
                login
                    .loginPage(SESION)
                    .usernameParameter("correo")
                    .passwordParameter("contrasena")
                    .successHandler((request, response, auth) -> response.setStatus(204))
                    .failureHandler(manejadorErrores))
        .logout(
            logout ->
                logout
                    .logoutRequestMatcher(
                        PathPatternRequestMatcher.pathPattern(HttpMethod.DELETE, SESION))
                    .logoutSuccessHandler(
                        new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                    .deleteCookies("JSESSIONID"))
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

  /**
   * Login contra la base (CU-12): el bloqueo se verifica antes de la contraseña (4c sale aunque sea
   * correcta) y la cuenta desactivada después (4b solo se revela con la contraseña correcta).
   */
  @Bean
  DaoAuthenticationProvider authenticationProvider(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    provider.setPreAuthenticationChecks(SecurityConfig::verificarBloqueo);
    provider.setPostAuthenticationChecks(SecurityConfig::verificarActiva);
    return provider;
  }

  private static void verificarBloqueo(UserDetails usuario) {
    if (usuario instanceof UsuarioAutenticado autenticado && !autenticado.isAccountNonLocked()) {
      throw new CuentaBloqueadaException(autenticado.getBloqueadoHasta());
    }
  }

  private static void verificarActiva(UserDetails usuario) {
    if (!usuario.isEnabled()) {
      throw new DisabledException("Cuenta desactivada");
    }
  }
}
