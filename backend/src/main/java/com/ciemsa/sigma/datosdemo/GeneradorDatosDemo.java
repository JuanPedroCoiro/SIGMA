package com.ciemsa.sigma.datosdemo;

import com.ciemsa.sigma.administracion.UsuarioDetailsService;
import com.ciemsa.sigma.ot.application.AnulacionOtService;
import com.ciemsa.sigma.ot.application.AsignacionOtService;
import com.ciemsa.sigma.ot.application.EjecucionOtService;
import com.ciemsa.sigma.ot.application.FotoCierre;
import com.ciemsa.sigma.ot.application.FotoNueva;
import com.ciemsa.sigma.ot.application.IncidenteConOt;
import com.ciemsa.sigma.ot.application.IncidenteService;
import com.ciemsa.sigma.ot.application.PausaOtService;
import com.ciemsa.sigma.ot.application.RegistrarIncidenteCommand;
import com.ciemsa.sigma.ot.domain.CierreTecnico;
import com.ciemsa.sigma.ot.domain.Incidente;
import com.ciemsa.sigma.ot.domain.MomentoFoto;
import com.ciemsa.sigma.ot.domain.MotivoPausa;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.OrigenIncidente;
import com.ciemsa.sigma.ot.infrastructure.IncidenteRepository;
import com.ciemsa.sigma.shared.configuracion.RelojDesplazable;
import com.ciemsa.sigma.shared.seguridad.UsuarioAutenticado;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Genera al arrancar el escenario de la demo (#81) con fechas relativas al arranque: incidentes y
 * OTs en todos los estados del sprint 2, vencidas y por vencer, pausas, reasignaciones, anulaciones
 * y esperas de terceros, y desde el sprint 3 OTs en proceso y cerradas técnicamente (HU-07). Cada
 * acción pasa por el servicio de la aplicación, con el reloj corrido al momento de la acción y el
 * usuario que la hace, así se respetan las reglas y la bitácora queda con todo el historial.
 *
 * <p>Solo si no hay incidentes: reiniciar no duplica nada. Todo en una transacción: si algo falla
 * no queda a medias y el próximo arranque lo vuelve a intentar. Lo registra {@link
 * DatosDemoConfig}, solo en {@code dev} con la propiedad activa.
 */
@Slf4j
@RequiredArgsConstructor
public class GeneradorDatosDemo implements ApplicationRunner {

  static final String JEFATURA = "jefatura.dev@sigma.test";
  static final String OPERADOR_1 = "operador.dev@sigma.test";
  static final String OPERADOR_2 = "operador2.dev@sigma.test";
  static final String TECNICO_NORTE = "tecnico1.dev@sigma.test";
  static final String TECNICO_ESTE_1 = "tecnico3.dev@sigma.test";
  static final String TECNICO_ESTE_2 = "tecnico4.dev@sigma.test";

  /**
   * Una imagen PNG de 1×1 px: el cierre técnico exige al menos una foto (#90, HU-08). Es de la
   * demo, no de un caso real.
   */
  private static final byte[] FOTO_DEMO =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

  private static final String NORTE = "Cuadrilla Dev Norte";
  private static final String SUR = "Cuadrilla Dev Sur";
  private static final String ESTE = "Cuadrilla Dev Este";
  private static final String OESTE = "Cuadrilla Dev Oeste";

  private static final String APAGADO = "Semáforo apagado";
  private static final String LAMPARA = "Lámpara quemada";
  private static final String SINCRONIZACION = "Falla de sincronización";
  private static final String PULSADOR = "Pulsador peatonal roto";
  private static final String ESTRUCTURA = "Daño en estructura";

  private final IncidenteRepository incidenteRepository;
  private final IncidenteService incidenteService;
  private final AsignacionOtService asignacionOtService;
  private final PausaOtService pausaOtService;
  private final EjecucionOtService ejecucionOtService;
  private final AnulacionOtService anulacionOtService;
  private final UsuarioDetailsService usuarioDetailsService;
  private final RelojDesplazable reloj;
  private final ZoneId zonaHoraria;
  private final EntityManager entityManager;
  private final PlatformTransactionManager transactionManager;

  @Override
  public void run(ApplicationArguments args) {
    generarSiHaceFalta();
  }

  /** Genera el escenario si no hay ningún incidente; devuelve si lo generó. */
  public boolean generarSiHaceFalta() {
    if (incidenteRepository.count() > 0) {
      log.info("Datos de la demo: ya hay incidentes, no se genera el escenario.");
      return false;
    }
    Map<String, UsuarioAutenticado> usuarios = cargarUsuarios();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(estado -> new Escenario(reloj.instant(), usuarios).generar());
    log.info("Datos de la demo: escenario generado ({} incidentes).", incidenteRepository.count());
    return true;
  }

  /** Los usuarios que actúan en el escenario, cargados como en el login. */
  private Map<String, UsuarioAutenticado> cargarUsuarios() {
    Map<String, UsuarioAutenticado> usuarios = new HashMap<>();
    for (String correo :
        List.of(JEFATURA, OPERADOR_1, OPERADOR_2, TECNICO_NORTE, TECNICO_ESTE_1, TECNICO_ESTE_2)) {
      usuarios.put(correo, (UsuarioAutenticado) usuarioDetailsService.loadUserByUsername(correo));
    }
    return usuarios;
  }

  /**
   * El guion. Cada caso arma sus pasos; después se ejecutan todos en orden cronológico (el orden es
   * estable, así que dentro de un caso se respeta), y los números INC y OT crecen con el tiempo.
   */
  private final class Escenario {

    private final Instant t0;
    private final LocalDate hoy;
    private final LocalDate manana;
    private final Map<String, UsuarioAutenticado> usuarios;
    private final List<Paso> pasos = new ArrayList<>();

    Escenario(Instant t0, Map<String, UsuarioAutenticado> usuarios) {
      this.t0 = t0;
      this.hoy = LocalDate.ofInstant(t0, zonaHoraria);
      this.manana = hoy.plusDays(1);
      this.usuarios = usuarios;
    }

    void generar() {
      // Vencidas: ALTA (8 h) registradas hace más de 8 h; la primera ya asignada
      Caso vencida1 = registrar(horas(14), OPERADOR_1, "DEV-021", APAGADO, null);
      asignar(vencida1, horas(12), NORTE, hoy);
      registrar(horas(11), OPERADOR_2, "DEV-012", APAGADO, null);
      Caso vencida3 = registrar(minutos(9 * 60 + 30), OPERADOR_1, "DEV-016", APAGADO, null);

      // Por vencer en las próximas horas
      registrar(horas(6), OPERADOR_2, "DEV-008", APAGADO, null);
      registrar(horas(5), OPERADOR_1, "DEV-025", APAGADO, null);
      registrar(horas(44), OPERADOR_2, "DEV-019", LAMPARA, null);

      // En plazo, pendientes sin asignar
      registrar(horas(1), OPERADOR_1, "DEV-013", LAMPARA, null);
      registrar(horas(3), OPERADOR_2, "DEV-009", SINCRONIZACION, null);
      registrar(horas(20), OPERADOR_1, "DEV-023", LAMPARA, null);
      registrar(horas(2), OPERADOR_2, "DEV-017", PULSADOR, null);
      registrar(horas(30), OPERADOR_1, "DEV-011", ESTRUCTURA, null);

      // Asignadas a distintas cuadrillas, para hoy y mañana
      asignar(registrar(horas(20), OPERADOR_2, "DEV-010", LAMPARA, null), horas(19), SUR, hoy);
      asignar(registrar(horas(18), OPERADOR_1, "DEV-024", PULSADOR, null), horas(16), ESTE, manana);
      asignar(
          registrar(horas(8), OPERADOR_2, "DEV-015", SINCRONIZACION, null), horas(7), OESTE, hoy);
      asignar(
          registrar(horas(5), OPERADOR_1, "DEV-020", ESTRUCTURA, null),
          minutos(270),
          NORTE,
          manana);
      asignar(registrar(horas(2), OPERADOR_2, "DEV-018", LAMPARA, null), horas(1), NORTE, hoy);

      // Reasignada: pasa de Sur a Oeste, la cuadrilla de la Ruta 5
      Caso reasignada = registrar(horas(22), OPERADOR_1, "DEV-014", SINCRONIZACION, null);
      asignar(reasignada, horas(20), SUR, hoy);
      asignar(reasignada, horas(4), OESTE, manana);

      // «SLA sin definir»: el organismo de DEV-007 no tiene reglas
      registrar(horas(3), OPERADOR_2, "DEV-007", LAMPARA, null);

      // En pausa por un tercero (no vence) y por materiales (el plazo sigue corriendo)
      Caso pausaTercero = registrar(horas(20), OPERADOR_2, "DEV-022", LAMPARA, null);
      asignar(pausaTercero, horas(19), NORTE, hoy);
      pausar(
          pausaTercero,
          horas(16),
          TECNICO_NORTE,
          MotivoPausa.TERCERO,
          "UTE: sin energía en el cruce");
      Caso pausaMateriales = registrar(horas(20), OPERADOR_1, "DEV-026", SINCRONIZACION, null);
      asignar(pausaMateriales, horas(19), ESTE, hoy);
      pausar(pausaMateriales, horas(6), TECNICO_ESTE_1, MotivoPausa.MATERIALES, null);

      // Pausada por un tercero y reanudada: la fecha límite se recalcula desde la reanudación
      Caso reanudada = registrar(horas(40), OPERADOR_1, "DEV-027", LAMPARA, null);
      asignar(reanudada, horas(39), ESTE, hoy);
      pausar(reanudada, horas(38), TECNICO_ESTE_2, MotivoPausa.TERCERO, "Antel: cableado cortado");
      reanudar(reanudada, horas(10), TECNICO_ESTE_2);

      // Anuladas con motivo: una duplicada (mismo cruce que la vencida 3) y una improcedente
      Caso duplicada = registrar(horas(7), OPERADOR_2, "DEV-016", APAGADO, null);
      anular(
          duplicada,
          horas(6),
          () -> "Duplicada de la " + vencida3.ot.getNumero() + ": mismo reclamo del organismo.");
      Caso improcedente = registrar(horas(28), OPERADOR_1, "DEV-013", ESTRUCTURA, null);
      asignar(improcedente, horas(27), OESTE, hoy);
      anular(improcedente, horas(24), () -> "Improcedente: la cuadrilla verificó que funciona.");

      // En ejecución (HU-07): una en proceso y dos cerradas técnicamente, una en plazo y otra
      // fuera de plazo (ALTA con límite hace 18 h), para validar u observar (HU-09)
      Caso enProceso = registrar(horas(10), OPERADOR_1, "DEV-009", LAMPARA, null);
      asignar(enProceso, horas(9), NORTE, hoy);
      iniciar(enProceso, horas(2), TECNICO_NORTE);
      Caso cerradaEnPlazo = registrar(horas(30), OPERADOR_2, "DEV-010", SINCRONIZACION, null);
      asignar(cerradaEnPlazo, horas(29), ESTE, hoy);
      iniciar(cerradaEnPlazo, horas(26), TECNICO_ESTE_1);
      cerrar(
          cerradaEnPlazo,
          horas(24),
          TECNICO_ESTE_1,
          "El controlador perdió la hora: los grupos de luces se desfasaban.",
          "Se sincronizó el reloj del controlador y se verificó el ciclo completo.",
          null);
      Caso cerradaFueraDePlazo = registrar(horas(26), OPERADOR_1, "DEV-023", APAGADO, null);
      asignar(cerradaFueraDePlazo, horas(25), NORTE, hoy);
      iniciar(cerradaFueraDePlazo, horas(20), TECNICO_NORTE);
      cerrar(
          cerradaFueraDePlazo,
          horas(16),
          TECNICO_NORTE,
          "Fuente de alimentación quemada por una sobretensión.",
          "Se cambió la fuente y se probó el cruce en todas las fases.",
          "Hubo que esperar la fuente de repuesto del depósito.");

      // En espera de un tercero
      registrar(horas(5), OPERADOR_1, "DEV-020", APAGADO, "UTE", "Corte de energía en la zona");
      registrar(horas(18), OPERADOR_2, "DEV-012", SINCRONIZACION, "Antel", "Sin enlace de datos");
      registrar(horas(2), OPERADOR_1, "DEV-025", APAGADO, "Tigo", "Falla de la antena del cruce");

      // Esperas resueltas: el tercero volvió a comunicar (sin OT) o no (se genera la OT)
      Caso cerrado =
          registrar(horas(48), OPERADOR_2, "DEV-017", APAGADO, "UTE", "Corte programado");
      paso(horas(30), OPERADOR_2, () -> incidenteService.cerrarSinOt(cerrado.incidente.getId()));
      Caso resuelta = registrar(horas(36), OPERADOR_1, "DEV-015", LAMPARA, "Antel", null);
      paso(
          horas(12),
          JEFATURA,
          () -> resuelta.guardar(incidenteService.generarOt(resuelta.incidente.getId())));

      pasos.sort(Comparator.comparing(Paso::hace).reversed());
      pasos.forEach(this::ejecutar);
    }

    private Caso registrar(
        Duration hace, String correo, String codigo, String categoria, String tercero) {
      return registrar(hace, correo, codigo, categoria, tercero, null);
    }

    /** Con la prioridad sugerida por la categoría; {@code tercero} no null = en espera (4b). */
    private Caso registrar(
        Duration hace,
        String correo,
        String codigo,
        String categoria,
        String tercero,
        String causa) {
      Caso caso = new Caso();
      paso(
          hace,
          correo,
          () ->
              caso.guardar(
                  incidenteService.registrar(
                      new RegistrarIncidenteCommand(
                          idInstalacion(codigo),
                          OPERADOR_1.equals(correo)
                              ? OrigenIncidente.MONITOREO
                              : OrigenIncidente.RECLAMO_ORGANISMO,
                          idCategoria(categoria),
                          null,
                          categoria + " en " + codigo + " (dato de la demo).",
                          tercero,
                          causa))));
      return caso;
    }

    private Caso asignar(Caso caso, Duration hace, String cuadrilla, LocalDate fecha) {
      paso(
          hace,
          JEFATURA,
          () -> asignacionOtService.asignar(List.of(caso.idOt()), idCuadrilla(cuadrilla), fecha));
      return caso;
    }

    private void pausar(
        Caso caso, Duration hace, String tecnico, MotivoPausa motivo, String detalle) {
      paso(hace, tecnico, () -> pausaOtService.pausar(caso.idOt(), motivo, detalle));
    }

    private void reanudar(Caso caso, Duration hace, String tecnico) {
      paso(hace, tecnico, () -> pausaOtService.reanudar(caso.idOt()));
    }

    private void iniciar(Caso caso, Duration hace, String tecnico) {
      paso(hace, tecnico, () -> ejecucionOtService.iniciar(caso.idOt()));
    }

    private void cerrar(
        Caso caso,
        Duration hace,
        String tecnico,
        String diagnostico,
        String solucion,
        String observaciones) {
      paso(
          hace,
          tecnico,
          () ->
              ejecucionOtService.cerrar(
                  caso.idOt(),
                  UUID.randomUUID(),
                  new CierreTecnico(diagnostico, solucion, observaciones),
                  List.of(
                      new FotoCierre(
                          MomentoFoto.DESPUES, new FotoNueva("despues-demo.png", FOTO_DEMO)))));
    }

    private void anular(Caso caso, Duration hace, Supplier<String> motivo) {
      paso(hace, JEFATURA, () -> anulacionOtService.anular(caso.idOt(), motivo.get()));
    }

    private void paso(Duration hace, String correo, Runnable accion) {
      pasos.add(new Paso(hace, correo, accion));
    }

    /**
     * Ejecuta el paso con el reloj en {@code t0 - hace} y el usuario en sesión. El flush va dentro,
     * así {@code modificado_en} también queda con la hora del paso.
     */
    private void ejecutar(Paso paso) {
      SecurityContext anterior = SecurityContextHolder.getContext();
      UsuarioAutenticado usuario = usuarios.get(paso.correo());
      SecurityContext contexto = SecurityContextHolder.createEmptyContext();
      contexto.setAuthentication(
          UsernamePasswordAuthenticationToken.authenticated(
              usuario, null, usuario.getAuthorities()));
      SecurityContextHolder.setContext(contexto);
      try {
        reloj.en(
            t0.minus(paso.hace()),
            () -> {
              paso.accion().run();
              entityManager.flush();
              return null;
            });
      } finally {
        SecurityContextHolder.setContext(anterior);
      }
    }

    private Long idInstalacion(String codigo) {
      return entityManager
          .createQuery("select i.id from Instalacion i where i.codigo = :codigo", Long.class)
          .setParameter("codigo", codigo)
          .getSingleResult();
    }

    private Long idCategoria(String nombre) {
      return entityManager
          .createQuery("select c.id from CategoriaFalla c where c.nombre = :nombre", Long.class)
          .setParameter("nombre", nombre)
          .getSingleResult();
    }

    private Long idCuadrilla(String nombre) {
      return entityManager
          .createQuery("select c.id from Cuadrilla c where c.nombre = :nombre", Long.class)
          .setParameter("nombre", nombre)
          .getSingleResult();
    }
  }

  private static Duration horas(long horas) {
    return Duration.ofHours(horas);
  }

  private static Duration minutos(long minutos) {
    return Duration.ofMinutes(minutos);
  }

  /** Una acción del guion: cuánto antes del arranque, quién la hace y qué hace. */
  private record Paso(Duration hace, String correo, Runnable accion) {}

  /** El incidente y la OT de un caso, que se conocen al ejecutar sus pasos. */
  private static final class Caso {

    private Incidente incidente;
    private OrdenTrabajo ot;

    void guardar(IncidenteConOt resultado) {
      this.incidente = resultado.incidente();
      this.ot = resultado.ordenTrabajo();
    }

    Long idOt() {
      return ot.getId();
    }
  }
}
