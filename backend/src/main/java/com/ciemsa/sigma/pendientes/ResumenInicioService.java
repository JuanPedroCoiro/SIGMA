package com.ciemsa.sigma.pendientes;

import com.ciemsa.sigma.administracion.UsuarioService;
import com.ciemsa.sigma.ot.domain.EstadoIncidente;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contadores de la página de inicio según el rol (#72): solo consultas con count. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResumenInicioService {

  private static final List<EstadoOT> ABIERTAS =
      List.of(EstadoOT.PENDIENTE, EstadoOT.ASIGNADA, EstadoOT.EN_PROCESO, EstadoOT.EN_PAUSA);

  /** Las que una cuadrilla tiene entre manos. */
  private static final List<EstadoOT> DE_LA_CUADRILLA =
      List.of(EstadoOT.ASIGNADA, EstadoOT.EN_PROCESO, EstadoOT.EN_PAUSA);

  private static final Duration POR_VENCER = Duration.ofHours(24);

  // OTs abiertas cuya fecha límite cae en [desde, hasta). La pausada por un tercero no vence hasta
  // reanudar: misma regla que OrdenTrabajo.estaVencida, OrdenPendiente.vencida y el orden de
  // PendientesService (TERCERO es el único MotivoPausa con reiniciaSla(), que JPQL no puede
  // llamar).
  private static final String CON_LIMITE_ENTRE =
      """
    select count(o) from OrdenTrabajo o
    where o.estado in :estados
        and o.fechaLimite >= :desde and o.fechaLimite < :hasta
        and not exists (
            select p from PausaOT p
            where p.ordenTrabajo = o and p.fin is null
                and p.motivo = com.ciemsa.sigma.ot.domain.MotivoPausa.TERCERO)""";

  private final EntityManager entityManager;
  private final UsuarioActual usuarioActual;
  private final UsuarioService usuarioService;
  private final Clock clock;
  private final ZoneId zonaHoraria;

  public ResumenInicio resumen() {
    Instant ahora = Instant.now(clock);
    return switch (usuarioActual.rol()) {
      case JEFATURA ->
          new ResumenInicio(
              contarOts(List.of(EstadoOT.PENDIENTE)),
              vencidas(ahora),
              contarConLimiteEntre(ahora, ahora.plus(POR_VENCER)),
              contarOts(List.of(EstadoOT.EN_PAUSA)),
              incidentesEnEspera(),
              null,
              null);
      case OPERADOR ->
          new ResumenInicio(
              null,
              vencidas(ahora),
              null,
              null,
              incidentesEnEspera(),
              incidentesRegistradosDesde(inicioDelDia(ahora)),
              null);
      case TECNICO ->
          new ResumenInicio(null, null, null, null, null, null, otsDeHoyDeSuCuadrilla(ahora));
      case ENCARGADO_STOCK -> ResumenInicio.sinTarjetas();
    };
  }

  /** Cualquier fecha límite anterior a ahora: desde el comienzo de los tiempos. */
  private long vencidas(Instant ahora) {
    return contarConLimiteEntre(Instant.EPOCH, ahora);
  }

  private long contarConLimiteEntre(Instant desde, Instant hasta) {
    return entityManager
        .createQuery(CON_LIMITE_ENTRE, Long.class)
        .setParameter("estados", ABIERTAS)
        .setParameter("desde", desde)
        .setParameter("hasta", hasta)
        .getSingleResult();
  }

  private long contarOts(List<EstadoOT> estados) {
    return entityManager
        .createQuery("select count(o) from OrdenTrabajo o where o.estado in :estados", Long.class)
        .setParameter("estados", estados)
        .getSingleResult();
  }

  private long incidentesEnEspera() {
    return entityManager
        .createQuery("select count(i) from Incidente i where i.estado = :estado", Long.class)
        .setParameter("estado", EstadoIncidente.EN_ESPERA_TERCERO)
        .getSingleResult();
  }

  private long incidentesRegistradosDesde(Instant desde) {
    return entityManager
        .createQuery("select count(i) from Incidente i where i.creadoEn >= :desde", Long.class)
        .setParameter("desde", desde)
        .getSingleResult();
  }

  /** Las de su cuadrilla con fecha de ejecución hoy; 0 si no tiene cuadrilla. */
  private long otsDeHoyDeSuCuadrilla(Instant ahora) {
    LocalDate hoy = ahora.atZone(zonaHoraria).toLocalDate();
    return usuarioService
        .idCuadrillaDe(usuarioActual.id())
        .map(
            idCuadrilla ->
                entityManager
                    .createQuery(
                        """
              select count(o) from OrdenTrabajo o
              where o.idCuadrilla = :cuadrilla and o.fechaEjecucion = :hoy
                  and o.estado in :estados""",
                        Long.class)
                    .setParameter("cuadrilla", idCuadrilla)
                    .setParameter("hoy", hoy)
                    .setParameter("estados", DE_LA_CUADRILLA)
                    .getSingleResult())
        .orElse(0L);
  }

  /** Las 00:00 de hoy en Montevideo: «hoy» es el día de CIEMSA, no el de UTC. */
  private Instant inicioDelDia(Instant ahora) {
    return ahora.atZone(zonaHoraria).toLocalDate().atStartOfDay(zonaHoraria).toInstant();
  }
}
