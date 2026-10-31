package com.ciemsa.sigma.activos;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ciemsa.sigma.TestcontainersConfiguration;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InstalacionControllerTest {

  private static final String INSTALACIONES = "/api/v1/instalaciones";

  @Autowired private WebApplicationContext context;
  @Autowired private InstalacionRepository instalacionRepository;
  @Autowired private JdbcTemplate jdbc;
  private MockMvc mvc;
  private Long idMtop;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    idMtop =
        jdbc.queryForObject("select id_organismo from organismo where nombre = 'MTOP'", Long.class);
  }

  @AfterEach
  void tearDown() {
    instalacionRepository.deleteAll();
  }

  private void guardar(String codigo, String nombre) {
    instalacionRepository.save(
        Instalacion.crear(
            codigo,
            nombre,
            "Ruta 1",
            new BigDecimal("-34.712345"),
            new BigDecimal("-56.301234"),
            idMtop));
  }

  @Test
  void listar_conJefatura_devuelvePaginaOrdenadaPorCodigo() throws Exception {
    guardar("INST-003", "Cruce Ficticio 3");
    guardar("INST-001", "Cruce Ficticio 1");
    guardar("INST-002", "Cruce Ficticio 2");

    mvc.perform(get(INSTALACIONES).with(user("jefa@ciemsa.com").roles("JEFATURA")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido[0].id").isNumber())
        .andExpect(jsonPath("$.contenido[0].codigo").value("INST-001"))
        .andExpect(jsonPath("$.contenido[0].nombre").value("Cruce Ficticio 1"))
        .andExpect(jsonPath("$.contenido[0].rutaZona").value("Ruta 1"))
        .andExpect(jsonPath("$.contenido[0].latitud").value(-34.712345))
        .andExpect(jsonPath("$.contenido[0].longitud").value(-56.301234))
        .andExpect(jsonPath("$.contenido[1].codigo").value("INST-002"))
        .andExpect(jsonPath("$.contenido[2].codigo").value("INST-003"))
        .andExpect(jsonPath("$.pagina").value(0))
        .andExpect(jsonPath("$.tamanio").value(20))
        .andExpect(jsonPath("$.totalElementos").value(3))
        .andExpect(jsonPath("$.totalPaginas").value(1));
  }

  @Test
  void listar_conOperador_devuelveLaLista() throws Exception {
    guardar("INST-001", "Cruce Ficticio 1");

    mvc.perform(get(INSTALACIONES).with(user("ana@ciemsa.com").roles("OPERADOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido[0].codigo").value("INST-001"))
        .andExpect(jsonPath("$.totalElementos").value(1));
  }

  @Test
  void listar_conTecnico_devuelve403() throws Exception {
    mvc.perform(get(INSTALACIONES).with(user("tecnico@ciemsa.com").roles("TECNICO")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/errores/sin-permiso"));
  }

  @Test
  void listar_sinSesion_devuelve401() throws Exception {
    mvc.perform(get(INSTALACIONES))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/errores/sin-sesion"));
  }

  @Test
  void listar_sinInstalaciones_devuelvePaginaVacia() throws Exception {
    mvc.perform(get(INSTALACIONES).with(user("jefa@ciemsa.com").roles("JEFATURA")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido").isEmpty())
        .andExpect(jsonPath("$.totalElementos").value(0))
        .andExpect(jsonPath("$.totalPaginas").value(0));
  }
}
