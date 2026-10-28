package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.shared.seguridad.UsuarioAutenticado;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Carga la cuenta para el login (CU-12): Spring Security valida estado y contraseña. */
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

  private final UsuarioRepository usuarioRepository;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String correo) {
    Usuario usuario =
        usuarioRepository
            .findByCorreo(Usuario.normalizarCorreo(correo))
            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    Instant bloqueadoHasta =
        usuario.estaBloqueado(Instant.now(clock)) ? usuario.getBloqueadoHasta() : null;
    return new UsuarioAutenticado(
        usuario.getId(),
        usuario.getCorreo(),
        usuario.getNombre(),
        usuario.getRol(),
        usuario.isActivo(),
        bloqueadoHasta,
        usuario.getPasswordHash());
  }
}
