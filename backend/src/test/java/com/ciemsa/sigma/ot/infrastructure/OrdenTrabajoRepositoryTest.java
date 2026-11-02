package com.ciemsa.sigma.ot.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ciemsa.sigma.TestcontainersConfiguration;
import com.ciemsa.sigma.activos.Instalacion;
import com.ciemsa.sigma.activos.InstalacionRepository;
import com.ciemsa.sigma.administracion.Cuadrilla;
import com.ciemsa.sigma.administracion.CuadrillaRepository;
import com.ciemsa.sigma.administracion.Usuario;
import com.ciemsa.sigma.administracion.UsuarioRepository;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.Incidente;
import com.ciemsa.sigma.ot.domain.MotivoPausa;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.OrigenIncidente;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.ReglaSLA;
import com.ciemsa.sigma.shared.seguridad.Rol;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Incidente y OrdenTrabajo contra PostgreSQL: mapeo, numeración por secuencia y CHECKs de V6. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class OrdenTrabajoRepositoryTest {

  private static final Instant AHORA = Instant.parse("2026-11-02T13:00:00Z");
  private static final LocalDate FECHA = LocalDate.parse("2026-11-03");

  @Autowired private IncidenteRepository incidenteRepository;
  @Autowired private OrdenTrabajoRepository ordenTrabajoRepository;
  @Autowired private ReglaSlaRepository reglaSlaRepository;
  @Autowired private OrganismoRepository organismoRepository;
  @Autowired private InstalacionRepository instalacionRepository;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private CuadrillaRepository cuadrillaRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;

  private Long idOrganismo;
  private Long idInstalacion;
  private Long idCategoria;
  private Long idUsuario;
  private Long idCuadrilla;

  @BeforeEach
  void setUp() {
    idOrganismo = organismoRepository.findByNombre("MTOP").orElseThrow().getId();
    idInstalacion =
        instalacionRepository
            .save(
                Instalacion.crear(
                    "TEST-001",
                    "Cruce de prueba",
                    "Ruta 1",
                    new BigDecimal("-34.900000"),
                    new BigDecimal("-56.160000"),
                    idOrganismo))
            .getId();
    idCategoria =
        jdbc.queryForObject("SELECT min(id_categoria_falla) FROM categoria_falla", Long.class);
    idUsuario =
        usuarioRepository
            .save(Usuario.crear("Ana", "ana@ciemsa.com", "$2a$10$hash", Rol.OPERADOR, null))
            .getId();
    idCuadrilla = cuadrillaRepository.save(Cuadrilla.crear("Cuadrilla Test", "Norte")).getId();
  }

  @Test
  void findByIdOrganismoAndPrioridad_reglaDePrueba_laEncuentra() {
    ReglaSLA regla =
        reglaSlaRepository.findByIdOrganismoAndPrioridad(idOrganismo, Prioridad.ALTA).orElseThrow();

    assertThat(regla.getHorasResolucion()).isEqualTo(8);
  }

  @Test
  void save_incidenteConOt_asignaNumerosAuditoriaYVersion() {
    ReglaSLA regla =
        reglaSlaRepository.findByIdOrganismoAndPrioridad(idOrganismo, Prioridad.ALTA).orElseThrow();
    Incidente incidente = incidenteRepository.save(incidente());
    OrdenTrabajo ot =
        ordenTrabajoRepository.saveAndFlush(incidente.generarOrdenCorrectiva(AHORA, regla));
    Long idOt = ot.getId();
    entityManager.clear();

    Incidente leido = incidenteRepository.findById(incidente.getId()).orElseThrow();
    OrdenTrabajo otLeida = ordenTrabajoRepository.findById(idOt).orElseThrow();
    assertThat(leido.getNumero()).matches("INC-\\d{4,}");
    assertThat(otLeida.getNumero()).matches("OT-\\d{4,}");
    assertThat(otLeida.getCreadoEn()).isNotNull();
    assertThat(otLeida.getVersion()).isZero();
    assertThat(otLeida.getIncidente().getId()).isEqualTo(incidente.getId());
    assertThat(otLeida.getFechaLimite()).isEqualTo(Instant.parse("2026-11-02T21:00:00Z"));
  }

  @Test
  void save_numeroGeneradoPorLaBase_quedaEnLaEntidadAlGuardar() {
    Incidente incidente = incidenteRepository.saveAndFlush(incidente());

    assertThat(incidente.getNumero()).matches("INC-\\d{4,}");
  }

  @Test
  void save_dosIncidentes_numerosConsecutivos() {
    Incidente primero = incidenteRepository.saveAndFlush(incidente());
    Incidente segundo = incidenteRepository.saveAndFlush(incidente());

    assertThat(numero(segundo.getNumero())).isEqualTo(numero(primero.getNumero()) + 1);
  }

  @Test
  void formatearNumero_rellenaACuatroDigitosSinRecortar() {
    assertThat(jdbc.queryForObject("SELECT formatear_numero('OT-', 7)", String.class))
        .isEqualTo("OT-0007");
    assertThat(jdbc.queryForObject("SELECT formatear_numero('INC-', 12345)", String.class))
        .isEqualTo("INC-12345");
  }

  @Test
  void save_otPausadaYAnuladaDesdePausa_cumpleLosChecks() {
    OrdenTrabajo ot =
        ordenTrabajoRepository.save(OrdenTrabajo.preventiva(idInstalacion, FECHA, AHORA));
    ot.asignar(idCuadrilla, FECHA, AHORA);
    ot.pausar(MotivoPausa.FIN_DE_JORNADA, null, idUsuario, AHORA);
    ordenTrabajoRepository.saveAndFlush(ot);

    ot.anular("Ya no hace falta", idUsuario, AHORA);
    ordenTrabajoRepository.saveAndFlush(ot);
    entityManager.clear();

    OrdenTrabajo leida = ordenTrabajoRepository.findById(ot.getId()).orElseThrow();
    assertThat(leida.getEstado()).isEqualTo(EstadoOT.ANULADA);
    assertThat(leida.getEstadoAnteriorPausa()).isNull();
    assertThat(leida.getPausas())
        .singleElement()
        .satisfies(p -> assertThat(p.getMotivo()).isEqualTo(MotivoPausa.FIN_DE_JORNADA))
        .satisfies(p -> assertThat(p.getFin()).isEqualTo(AHORA))
        .satisfies(p -> assertThat(p.getIdUsuarioReanudacion()).isEqualTo(idUsuario));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "tipo = 'CORRECTIVA'", // correctiva sin incidente
        "fecha_limite = now()", // preventiva con fecha límite
        "estado = 'VENCIDA'", // el vencimiento no es un estado
        "estado = 'EN_PAUSA'", // en pausa sin estado anterior
        "estado_anterior_pausa = 'ASIGNADA'", // estado anterior sin estar en pausa
        "estado = 'ANULADA'", // anulada sin motivo
        "estado = 'ASIGNADA'" // asignada sin cuadrilla
      })
  void insert_violaUnCheck_esRechazado(String cambio) {
    Long idOt =
        jdbc.queryForObject(
            "INSERT INTO orden_trabajo (tipo, estado, id_instalacion)"
                + " VALUES ('PREVENTIVA', 'PENDIENTE', ?) RETURNING id_orden_trabajo",
            Long.class,
            idInstalacion);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE orden_trabajo SET " + cambio + " WHERE id_orden_trabajo = ?", idOt))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void insert_incidenteEnEsperaSinTercero_esRechazado() {
    Incidente incidente = incidenteRepository.saveAndFlush(incidente());

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE incidente SET estado = 'EN_ESPERA_TERCERO' WHERE id_incidente = ?",
                    incidente.getId()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void insert_dosPausasAbiertasEnLaMismaOt_esRechazado() {
    OrdenTrabajo ot =
        ordenTrabajoRepository.saveAndFlush(OrdenTrabajo.preventiva(idInstalacion, FECHA, AHORA));
    String sql =
        "INSERT INTO pausa_ot (id_orden_trabajo, inicio, motivo, id_usuario_pausa)"
            + " VALUES (?, now(), 'CLIMA', ?)";
    jdbc.update(sql, ot.getId(), idUsuario);

    assertThatThrownBy(() -> jdbc.update(sql, ot.getId(), idUsuario))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Incidente incidente() {
    return Incidente.registrar(
        idInstalacion,
        OrigenIncidente.MONITOREO,
        idCategoria,
        Prioridad.ALTA,
        "Semáforo apagado",
        idUsuario);
  }

  private static int numero(String numeroVisible) {
    return Integer.parseInt(numeroVisible.substring(numeroVisible.indexOf('-') + 1));
  }
}
