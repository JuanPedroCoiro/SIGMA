package com.ciemsa.sigma.auditoria;

import com.ciemsa.sigma.administracion.UsuarioService;
import com.ciemsa.sigma.auditoria.dto.RegistroAuditoriaResponse;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Bitácora de las acciones críticas (RF-21, HU-12): grabar y consultar. */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

  private final RegistroAuditoriaRepository repositorio;
  private final UsuarioService usuarioService;
  private final JsonMapper jsonMapper;

  /**
   * Graba un registro en la transacción de la acción: MANDATORY hace que falle si no hay una, en
   * lugar de grabar por separado. Si la acción vuelve atrás, el registro también; si el registro
   * falla, falla la acción. Los valores se guardan como JSON (null = no aplica).
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void registrar(
      EntidadAuditada entidad,
      Long idEntidad,
      AccionAuditada accion,
      Long idUsuario,
      Instant fechaHora,
      Object valorAnterior,
      Object valorNuevo) {
    String nombreUsuario =
        usuarioService
            .nombreDe(idUsuario)
            .orElseThrow(
                () -> new IllegalStateException("No existe el usuario " + idUsuario + "."));
    repositorio.save(
        RegistroAuditoria.de(
            entidad,
            idEntidad,
            accion,
            idUsuario,
            nombreUsuario,
            fechaHora,
            aJson(valorAnterior),
            aJson(valorNuevo)));
  }

  /** Más reciente primero si no se pide otro orden; filtros opcionales. */
  @Transactional(readOnly = true)
  public Page<RegistroAuditoriaResponse> listar(
      EntidadAuditada entidad, Long idEntidad, Pageable pageable) {
    Specification<RegistroAuditoria> filtro = Specification.unrestricted();
    if (entidad != null) {
      filtro = filtro.and((r, query, cb) -> cb.equal(r.get("entidad"), entidad));
    }
    if (idEntidad != null) {
      filtro = filtro.and((r, query, cb) -> cb.equal(r.get("idEntidad"), idEntidad));
    }
    return repositorio.findAll(filtro, pageable).map(RegistroAuditoriaResponse::de);
  }

  private String aJson(Object valor) {
    return valor == null ? null : jsonMapper.writeValueAsString(valor);
  }
}
