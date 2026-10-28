package com.ciemsa.sigma.shared.seguridad;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Usuario en sesión: lo que Spring Security guarda tras el login. Es una foto tomada al ingresar,
 * por eso un cambio de rol rige desde el siguiente inicio de sesión (CU-13b).
 */
@Getter
public class UsuarioAutenticado implements UserDetails, CredentialsContainer {

  private final Long id;
  private final String correo;
  private final String nombre;
  private final Rol rol;
  private final boolean activo;

  /** Fin del bloqueo vigente (CU-12 4c); null si al cargarla la cuenta no estaba bloqueada. */
  private final Instant bloqueadoHasta;

  private String passwordHash;

  public UsuarioAutenticado(
      Long id,
      String correo,
      String nombre,
      Rol rol,
      boolean activo,
      Instant bloqueadoHasta,
      String passwordHash) {
    this.id = id;
    this.correo = correo;
    this.nombre = nombre;
    this.rol = rol;
    this.activo = activo;
    this.bloqueadoHasta = bloqueadoHasta;
    this.passwordHash = passwordHash;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
  }

  @Override
  public String getUsername() {
    return correo;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public boolean isEnabled() {
    return activo;
  }

  @Override
  public boolean isAccountNonLocked() {
    return bloqueadoHasta == null;
  }

  /** Spring lo llama después del login: el hash no queda guardado en la sesión. */
  @Override
  public void eraseCredentials() {
    this.passwordHash = null;
  }
}
