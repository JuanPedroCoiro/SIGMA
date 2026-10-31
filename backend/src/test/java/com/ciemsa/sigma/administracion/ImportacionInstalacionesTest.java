package com.ciemsa.sigma.administracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ciemsa.sigma.TestcontainersConfiguration;
import com.ciemsa.sigma.activos.Instalacion;
import com.ciemsa.sigma.activos.InstalacionRepository;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ImportacionInstalacionesTest {

  private static final String IMPORTAR = "/api/v1/instalaciones/importar";

  @Autowired private WebApplicationContext context;
  @Autowired private InstalacionRepository instalacionRepository;
  @Autowired private JdbcTemplate jdbc;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void tearDown() {
    instalacionRepository.deleteAll();
  }

  private static MockMultipartFile planilla(String recurso) throws IOException {
    try (InputStream contenido =
        ImportacionInstalacionesTest.class.getResourceAsStream("/importacion/" + recurso)) {
      return new MockMultipartFile("archivo", recurso, "text/csv", contenido.readAllBytes());
    }
  }

  @Test
  void importar_jefaturaConPlanillaValida_creaLasInstalacionesDelMtop() throws Exception {
    mvc.perform(
            multipart(IMPORTAR)
                .file(planilla("instalaciones-validas.csv"))
                .with(user("jefa@ciemsa.com").roles("JEFATURA"))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.importadas").value(4));

    Long idMtop =
        jdbc.queryForObject("select id_organismo from organismo where nombre = 'MTOP'", Long.class);
    assertThat(instalacionRepository.findAll())
        .hasSize(4)
        .allSatisfy(i -> assertThat(i.getIdOrganismo()).isEqualTo(idMtop))
        .extracting(Instalacion::getNombre)
        .contains("Cruce Ficticio 3; acceso norte");
  }

  @Test
  void importar_planillaConErrores_devuelve422PorFilaYNoImportaNada() throws Exception {
    mvc.perform(
            multipart(IMPORTAR)
                .file(planilla("instalaciones-con-errores.csv"))
                .with(user("jefa@ciemsa.com").roles("JEFATURA"))
                .with(csrf()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value("/errores/planilla-invalida"))
        .andExpect(jsonPath("$.title").value("Planilla con errores"))
        .andExpect(jsonPath("$.detail").isNotEmpty())
        .andExpect(jsonPath("$.errores.length()").value(3))
        .andExpect(jsonPath("$.errores[0].fila").value(3))
        .andExpect(jsonPath("$.errores[0].campo").value("nombre"))
        .andExpect(jsonPath("$.errores[1].fila").value(4))
        .andExpect(jsonPath("$.errores[2].fila").value(5));

    assertThat(instalacionRepository.count()).isZero();
  }

  @Test
  void importar_codigoYaImportado_devuelve422() throws Exception {
    MockMultipartFile archivo = planilla("instalaciones-validas.csv");
    mvc.perform(
            multipart(IMPORTAR)
                .file(archivo)
                .with(user("jefa@ciemsa.com").roles("JEFATURA"))
                .with(csrf()))
        .andExpect(status().isOk());

    mvc.perform(
            multipart(IMPORTAR)
                .file(archivo)
                .with(user("jefa@ciemsa.com").roles("JEFATURA"))
                .with(csrf()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errores.length()").value(4));

    assertThat(instalacionRepository.count()).isEqualTo(4);
  }

  @Test
  void importar_tecnico_devuelve403() throws Exception {
    mvc.perform(
            multipart(IMPORTAR)
                .file(planilla("instalaciones-validas.csv"))
                .with(user("tecnico@ciemsa.com").roles("TECNICO"))
                .with(csrf()))
        .andExpect(status().isForbidden());

    assertThat(instalacionRepository.count()).isZero();
  }

  @Test
  void importar_sinSesion_devuelve401() throws Exception {
    mvc.perform(multipart(IMPORTAR).file(planilla("instalaciones-validas.csv")).with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void catalogos_migracionesYDatosDePrueba_cargaMtopCategoriasYReglas() {
    Integer reglasMtop =
        jdbc.queryForObject(
            "select count(*) from regla_sla r join organismo o using (id_organismo)"
                + " where o.nombre = 'MTOP'",
            Integer.class);
    Integer categorias =
        jdbc.queryForObject("select count(*) from categoria_falla where activa", Integer.class);

    assertThat(reglasMtop).isEqualTo(3);
    assertThat(categorias).isPositive();
  }
}
