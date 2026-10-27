package com.ciemsa.sigma.administracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ciemsa.sigma.activos.InstalacionService;
import com.ciemsa.sigma.activos.dto.NuevaInstalacion;
import com.ciemsa.sigma.administracion.dto.ErrorFila;
import com.ciemsa.sigma.administracion.dto.ResultadoImportacion;
import com.ciemsa.sigma.administracion.infrastructure.LectorPlanillaCsv;
import com.ciemsa.sigma.ot.application.OrganismoService;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ImportacionInstalacionesServiceTest {

  private static final String ENCABEZADOS = "codigo;nombre;ruta_zona;latitud;longitud\n";
  private static final Long ID_MTOP = 7L;

  private final InstalacionService instalacionService = mock(InstalacionService.class);
  private final OrganismoService organismoService = mock(OrganismoService.class);
  private final ImportacionInstalacionesService servicio =
      new ImportacionInstalacionesService(
          new LectorPlanillaCsv(), instalacionService, organismoService, "MTOP");

  @BeforeEach
  void setUp() {
    when(instalacionService.codigosExistentes(anyCollection())).thenReturn(Set.of());
    when(organismoService.obtenerIdPorNombre("MTOP")).thenReturn(ID_MTOP);
    when(instalacionService.crearTodas(anyList(), any()))
        .thenAnswer(invocacion -> invocacion.<List<?>>getArgument(0).size());
  }

  private ResultadoImportacion importar(String filas) {
    byte[] bytes = (ENCABEZADOS + filas).getBytes(StandardCharsets.UTF_8);
    return servicio.importar(new ByteArrayInputStream(bytes));
  }

  @SuppressWarnings("unchecked")
  private List<NuevaInstalacion> creadas() {
    ArgumentCaptor<List<NuevaInstalacion>> captor = ArgumentCaptor.forClass(List.class);
    verify(instalacionService).crearTodas(captor.capture(), eq(ID_MTOP));
    return captor.getValue();
  }

  @Test
  void importar_filasValidas_lasCreaAsignadasAlOrganismo() {
    ResultadoImportacion resultado =
        importar("A-1;Cruce 1;Ruta 1;-34.9;-56.1\nA-2;Cruce 2;Ruta 5;-33.5;-55.0\n");

    assertThat(resultado.importadas()).isEqualTo(2);
    assertThat(resultado.errores()).isEmpty();
    assertThat(creadas()).extracting(NuevaInstalacion::codigo).containsExactly("A-1", "A-2");
  }

  @Test
  void importar_comaDecimal_laConvierteConSeisDecimales() {
    importar("A-1;Cruce 1;Ruta 1;-34,9012345;-56,1\n");

    NuevaInstalacion creada = creadas().getFirst();
    assertThat(creada.latitud()).isEqualTo(new BigDecimal("-34.901235"));
    assertThat(creada.longitud()).isEqualTo(new BigDecimal("-56.100000"));
  }

  @Test
  void importar_campoObligatorioVacio_informaLaFilaYElCampo() {
    ResultadoImportacion resultado = importar("A-1;;Ruta 1;-34.9;-56.1\n");

    assertThat(resultado.errores())
        .extracting(ErrorFila::fila, ErrorFila::campo)
        .containsExactly(tuple(2, "nombre"));
  }

  @Test
  void importar_coordenadaNoNumerica_informaError() {
    ResultadoImportacion resultado = importar("A-1;Cruce 1;Ruta 1;abc;-56.1\n");

    assertThat(resultado.errores())
        .singleElement()
        .extracting(ErrorFila::campo)
        .isEqualTo("latitud");
  }

  @Test
  void importar_coordenadasInvertidas_loSugiereEnElMensaje() {
    ResultadoImportacion resultado = importar("A-1;Cruce 1;Ruta 1;-56.1;-34.9\n");

    assertThat(resultado.errores())
        .singleElement()
        .extracting(ErrorFila::mensaje)
        .asString()
        .contains("no están en Uruguay", "invertidas");
  }

  @Test
  void importar_coordenadasFueraDeUruguay_informaError() {
    ResultadoImportacion resultado = importar("A-1;Cruce 1;Ruta 1;40.4;-3.7\n");

    assertThat(resultado.errores())
        .singleElement()
        .extracting(ErrorFila::mensaje)
        .asString()
        .contains("no están en Uruguay")
        .doesNotContain("invertidas");
  }

  @Test
  void importar_codigoRepetidoEnElArchivo_informaLaSegundaFila() {
    ResultadoImportacion resultado =
        importar("A-1;Cruce 1;Ruta 1;-34.9;-56.1\nA-1;Cruce 1 bis;Ruta 1;-34.8;-56.2\n");

    assertThat(resultado.errores())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.fila()).isEqualTo(3);
              assertThat(error.mensaje()).contains("fila 2");
            });
  }

  @Test
  void importar_codigoExistenteEnLaBase_informaError() {
    when(instalacionService.codigosExistentes(anyCollection())).thenReturn(Set.of("A-2"));

    ResultadoImportacion resultado =
        importar("A-1;Cruce 1;Ruta 1;-34.9;-56.1\nA-2;Cruce 2;Ruta 5;-33.5;-55.0\n");

    assertThat(resultado.errores())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.fila()).isEqualTo(3);
              assertThat(error.mensaje()).contains("Ya existe");
            });
  }

  @Test
  void importar_unaFilaConError_noCreaNinguna() {
    ResultadoImportacion resultado =
        importar("A-1;Cruce 1;Ruta 1;-34.9;-56.1\nA-2;Cruce 2;;-33.5;-55.0\n");

    assertThat(resultado.importadas()).isZero();
    verify(instalacionService, never()).crearTodas(anyList(), any());
  }

  @Test
  void importar_faltaUnaColumna_lanzaPlanillaInvalida() {
    byte[] bytes = "codigo;nombre;latitud;longitud\nA-1;Cruce;-34.9;-56.1\n".getBytes();

    assertThatThrownBy(() -> servicio.importar(new ByteArrayInputStream(bytes)))
        .isInstanceOf(PlanillaInvalidaException.class)
        .hasMessageContaining("ruta_zona");
  }

  @Test
  void importar_sinFilasDeDatos_lanzaPlanillaInvalida() {
    assertThatThrownBy(() -> importar(""))
        .isInstanceOf(PlanillaInvalidaException.class)
        .hasMessageContaining("no tiene filas");
  }
}
