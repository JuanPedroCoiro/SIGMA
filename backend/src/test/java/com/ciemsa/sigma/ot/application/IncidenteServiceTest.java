package com.ciemsa.sigma.ot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ciemsa.sigma.activos.InstalacionService;
import com.ciemsa.sigma.activos.dto.InstalacionResumen;
import com.ciemsa.sigma.ot.domain.CategoriaFalla;
import com.ciemsa.sigma.ot.domain.EstadoIncidente;
import com.ciemsa.sigma.ot.domain.EstadoOT;
import com.ciemsa.sigma.ot.domain.Incidente;
import com.ciemsa.sigma.ot.domain.IncidenteCerradoSinOt;
import com.ciemsa.sigma.ot.domain.IncidenteRegistrado;
import com.ciemsa.sigma.ot.domain.OrdenGenerada;
import com.ciemsa.sigma.ot.domain.OrdenTrabajo;
import com.ciemsa.sigma.ot.domain.OrigenIncidente;
import com.ciemsa.sigma.ot.domain.Prioridad;
import com.ciemsa.sigma.ot.domain.ReglaSLA;
import com.ciemsa.sigma.ot.infrastructure.CategoriaFallaRepository;
import com.ciemsa.sigma.ot.infrastructure.IncidenteRepository;
import com.ciemsa.sigma.ot.infrastructure.OrdenTrabajoRepository;
import com.ciemsa.sigma.ot.infrastructure.ReglaSlaRepository;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import com.ciemsa.sigma.shared.domain.ReglaNegocioException;
import com.ciemsa.sigma.shared.seguridad.UsuarioActual;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IncidenteServiceTest {

  private static final Instant AHORA = Instant.parse("2026-11-02T13:00:00Z");
  private static final Long ID_INSTALACION = 10L;
  private static final Long ID_ORGANISMO = 1L;
  private static final Long ID_CATEGORIA = 2L;
  private static final Long ID_USUARIO = 3L;

  @Mock private IncidenteRepository incidenteRepository;
  @Mock private OrdenTrabajoRepository ordenTrabajoRepository;
  @Mock private CategoriaFallaRepository categoriaFallaRepository;
  @Mock private ReglaSlaRepository reglaSlaRepository;
  @Mock private InstalacionService instalacionService;
  @Mock private UsuarioActual usuarioActual;

  private final List<Object> publicados = new ArrayList<>();
  private final AtomicLong ids = new AtomicLong(100);
  private IncidenteService servicio;

  @BeforeEach
  void setUp() {
    servicio = servicioCon(Clock.fixed(AHORA, ZoneOffset.UTC));
    when(usuarioActual.id()).thenReturn(ID_USUARIO);
    when(instalacionService.buscarActiva(ID_INSTALACION))
        .thenReturn(Optional.of(new InstalacionResumen(ID_INSTALACION, ID_ORGANISMO)));
    when(categoriaFallaRepository.findById(ID_CATEGORIA))
        .thenReturn(Optional.of(categoria("Lámpara quemada", Prioridad.MEDIA, true)));
    when(incidenteRepository.save(any())).thenAnswer(i -> conId(i.getArgument(0)));
    when(ordenTrabajoRepository.save(any())).thenAnswer(i -> conId(i.getArgument(0)));
  }

  // --- Registrar ---

  @Test
  void registrar_conRegla_generaOtPendienteConVencimientoYPublicaLosEventos() {
    conRegla(Prioridad.MEDIA, 48);

    IncidenteConOt resultado = servicio.registrar(comando(Prioridad.MEDIA, null));

    Incidente incidente = resultado.incidente();
    OrdenTrabajo ot = resultado.ordenTrabajo();
    assertThat(incidente.getEstado()).isEqualTo(EstadoIncidente.CON_OT);
    assertThat(incidente.getIdUsuario()).isEqualTo(ID_USUARIO);
    assertThat(ot.getEstado()).isEqualTo(EstadoOT.PENDIENTE);
    assertThat(ot.getInicioSla()).isEqualTo(AHORA);
    assertThat(ot.getFechaLimite()).isEqualTo(Instant.parse("2026-11-04T13:00:00Z"));
    assertThat(publicados)
        .containsExactly(
            new IncidenteRegistrado(incidente.getId(), ID_USUARIO, AHORA),
            new OrdenGenerada(ot.getId(), incidente.getId(), AHORA));
  }

  @Test
  void registrar_sinRegla_otConSlaSinDefinir() {
    when(reglaSlaRepository.findByIdOrganismoAndPrioridad(any(), any()))
        .thenReturn(Optional.empty());

    IncidenteConOt resultado = servicio.registrar(comando(Prioridad.ALTA, null));

    assertThat(resultado.ordenTrabajo().getFechaLimite()).isNull();
    assertThat(resultado.ordenTrabajo().getInicioSla()).isEqualTo(AHORA);
  }

  @Test
  void registrar_sinPrioridad_usaLaSugeridaPorLaCategoria() {
    conRegla(Prioridad.MEDIA, 48);

    IncidenteConOt resultado = servicio.registrar(comando(null, null));

    assertThat(resultado.incidente().getPrioridad()).isEqualTo(Prioridad.MEDIA);
    verify(reglaSlaRepository).findByIdOrganismoAndPrioridad(ID_ORGANISMO, Prioridad.MEDIA);
  }

  @Test
  void registrar_prioridadConfirmadaDistinta_buscaLaReglaDeEsaPrioridad() {
    conRegla(Prioridad.ALTA, 8);

    IncidenteConOt resultado = servicio.registrar(comando(Prioridad.ALTA, null));

    assertThat(resultado.ordenTrabajo().getFechaLimite())
        .isEqualTo(Instant.parse("2026-11-02T21:00:00Z"));
  }

  @Test
  void registrar_fallaDeTercero_quedaEnEsperaSinOtNiSla() {
    IncidenteConOt resultado = servicio.registrar(comando(Prioridad.ALTA, "UTE"));

    assertThat(resultado.incidente().getEstado()).isEqualTo(EstadoIncidente.EN_ESPERA_TERCERO);
    assertThat(resultado.incidente().getTercero()).isEqualTo("UTE");
    assertThat(resultado.ordenTrabajo()).isNull();
    verify(ordenTrabajoRepository, never()).save(any());
    verifyNoInteractions(reglaSlaRepository);
    assertThat(publicados).hasSize(1).first().isInstanceOf(IncidenteRegistrado.class);
  }

  @Test
  void registrar_instalacionInexistenteOInactiva_lanzaReglaNegocioYNoGuarda() {
    when(instalacionService.buscarActiva(ID_INSTALACION)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicio.registrar(comando(Prioridad.ALTA, null)))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("instalación");
    verify(incidenteRepository, never()).save(any());
    assertThat(publicados).isEmpty();
  }

  @Test
  void registrar_categoriaInexistente_lanzaReglaNegocio() {
    when(categoriaFallaRepository.findById(ID_CATEGORIA)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicio.registrar(comando(Prioridad.ALTA, null)))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("categoría");
    verify(incidenteRepository, never()).save(any());
  }

  @Test
  void registrar_categoriaInactiva_lanzaReglaNegocio() {
    when(categoriaFallaRepository.findById(ID_CATEGORIA))
        .thenReturn(Optional.of(categoria("Lámpara quemada", Prioridad.MEDIA, false)));

    assertThatThrownBy(() -> servicio.registrar(comando(Prioridad.ALTA, null)))
        .isInstanceOf(ReglaNegocioException.class);
    verify(incidenteRepository, never()).save(any());
  }

  @Test
  void registrar_categoriaOtraConDescripcionCorta_lanzaReglaNegocio() {
    when(categoriaFallaRepository.findById(ID_CATEGORIA))
        .thenReturn(Optional.of(categoria("Otra", Prioridad.MEDIA, true)));
    RegistrarIncidenteCommand comando =
        new RegistrarIncidenteCommand(
            ID_INSTALACION, OrigenIncidente.MONITOREO, ID_CATEGORIA, null, "Falla", null, null);

    assertThatThrownBy(() -> servicio.registrar(comando)).isInstanceOf(ReglaNegocioException.class);
    verify(incidenteRepository, never()).save(any());
  }

  // --- Resolver la espera ---

  @Test
  void generarOt_desdeLaEspera_cuentaElSlaDesdeEseMomento() {
    Incidente incidente = registrarEnBase(comando(Prioridad.MEDIA, "UTE"));
    conRegla(Prioridad.MEDIA, 48);
    Instant despues = AHORA.plusSeconds(86_400);

    IncidenteConOt resultado =
        servicioCon(Clock.fixed(despues, ZoneOffset.UTC)).generarOt(incidente.getId());

    assertThat(incidente.getEstado()).isEqualTo(EstadoIncidente.CON_OT);
    assertThat(resultado.ordenTrabajo().getInicioSla()).isEqualTo(despues);
    assertThat(resultado.ordenTrabajo().getFechaLimite())
        .isEqualTo(Instant.parse("2026-11-05T13:00:00Z"));
    assertThat(publicados)
        .containsExactly(
            new OrdenGenerada(resultado.ordenTrabajo().getId(), incidente.getId(), despues));
  }

  @Test
  void generarOt_incidenteConOt_lanzaReglaNegocio() {
    Incidente incidente = registrarEnBase(comando(Prioridad.MEDIA, null));

    assertThatThrownBy(() -> servicio.generarOt(incidente.getId()))
        .isInstanceOf(ReglaNegocioException.class);
    assertThat(publicados).isEmpty();
  }

  @Test
  void generarOt_inexistente_lanzaNoEncontrado() {
    when(incidenteRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicio.generarOt(999L)).isInstanceOf(NoEncontradoException.class);
  }

  @Test
  void cerrarSinOt_desdeLaEspera_cierraYPublicaElEvento() {
    Incidente incidente = registrarEnBase(comando(Prioridad.MEDIA, "UTE"));

    IncidenteConOt resultado = servicio.cerrarSinOt(incidente.getId());

    assertThat(resultado.incidente().getEstado()).isEqualTo(EstadoIncidente.CERRADO_SIN_OT);
    assertThat(resultado.ordenTrabajo()).isNull();
    assertThat(publicados)
        .containsExactly(new IncidenteCerradoSinOt(incidente.getId(), ID_USUARIO, AHORA));
  }

  @Test
  void cerrarSinOt_incidenteConOt_lanzaReglaNegocioYNoPublica() {
    Incidente incidente = registrarEnBase(comando(Prioridad.MEDIA, null));

    assertThatThrownBy(() -> servicio.cerrarSinOt(incidente.getId()))
        .isInstanceOf(ReglaNegocioException.class);
    assertThat(publicados).isEmpty();
  }

  @Test
  void cerrarSinOt_inexistente_lanzaNoEncontrado() {
    when(incidenteRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> servicio.cerrarSinOt(999L)).isInstanceOf(NoEncontradoException.class);
  }

  /** Registra un incidente, lo deja disponible por id y descarta los eventos del registro. */
  private Incidente registrarEnBase(RegistrarIncidenteCommand comando) {
    Incidente incidente = servicio.registrar(comando).incidente();
    when(incidenteRepository.findById(incidente.getId())).thenReturn(Optional.of(incidente));
    publicados.clear();
    return incidente;
  }

  private IncidenteService servicioCon(Clock clock) {
    ApplicationEventPublisher publicador = publicados::add;
    return new IncidenteService(
        incidenteRepository,
        ordenTrabajoRepository,
        categoriaFallaRepository,
        reglaSlaRepository,
        instalacionService,
        usuarioActual,
        clock,
        publicador);
  }

  private void conRegla(Prioridad prioridad, int horasResolucion) {
    when(reglaSlaRepository.findByIdOrganismoAndPrioridad(ID_ORGANISMO, prioridad))
        .thenReturn(Optional.of(ReglaSLA.crear(ID_ORGANISMO, prioridad, 1, horasResolucion)));
  }

  private static RegistrarIncidenteCommand comando(Prioridad prioridad, String tercero) {
    return new RegistrarIncidenteCommand(
        ID_INSTALACION,
        OrigenIncidente.RECLAMO_ORGANISMO,
        ID_CATEGORIA,
        prioridad,
        "Lámpara roja quemada",
        tercero,
        tercero == null ? null : "Corte de energía");
  }

  /** Simula el IDENTITY de la base. */
  private <T> T conId(T entidad) {
    ReflectionTestUtils.setField(entidad, "id", ids.incrementAndGet());
    return entidad;
  }

  private static CategoriaFalla categoria(String nombre, Prioridad sugerida, boolean activa) {
    CategoriaFalla categoria = BeanUtils.instantiateClass(CategoriaFalla.class);
    ReflectionTestUtils.setField(categoria, "id", ID_CATEGORIA);
    ReflectionTestUtils.setField(categoria, "nombre", nombre);
    ReflectionTestUtils.setField(categoria, "prioridadSugerida", sugerida);
    ReflectionTestUtils.setField(categoria, "activa", activa);
    return categoria;
  }
}
