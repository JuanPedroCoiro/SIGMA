package com.ciemsa.sigma.ot.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ciemsa.sigma.TestcontainersConfiguration;
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

/** Catálogo de categorías de falla (HU-03). Las activas vienen de R__catalogos_prueba.sql. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CategoriaFallaControllerTest {

  private static final String CATEGORIAS = "/api/v1/categorias-falla";
  private static final String INACTIVA = "Categoría inactiva de prueba";

  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbc;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    jdbc.update(
        "insert into categoria_falla (nombre, prioridad_sugerida, activa) values (?, 'BAJA', false)",
        INACTIVA);
  }

  @AfterEach
  void tearDown() {
    jdbc.update("delete from categoria_falla where nombre = ?", INACTIVA);
  }

  @Test
  void listar_conOperador_devuelveLasActivasOrdenadasPorNombre() throws Exception {
    mvc.perform(get(CATEGORIAS).with(user("ana@ciemsa.com").roles("OPERADOR")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].nombre")
                .value(
                    contains(
                        "Daño en estructura",
                        "Falla de sincronización",
                        "Lámpara quemada",
                        "Otra",
                        "Pulsador peatonal roto",
                        "Semáforo apagado")))
        .andExpect(jsonPath("$[0].id").isNumber())
        .andExpect(jsonPath("$[0].prioridadSugerida").value("BAJA"))
        .andExpect(jsonPath("$[5].prioridadSugerida").value("ALTA"))
        .andExpect(jsonPath("$[*].nombre").value(not(hasItem(INACTIVA))));
  }

  @Test
  void listar_conJefatura_devuelveLaLista() throws Exception {
    mvc.perform(get(CATEGORIAS).with(user("jefa@ciemsa.com").roles("JEFATURA")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(6));
  }

  @Test
  void listar_conTecnico_devuelve403() throws Exception {
    mvc.perform(get(CATEGORIAS).with(user("tecnico@ciemsa.com").roles("TECNICO")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/errores/sin-permiso"));
  }

  @Test
  void listar_sinSesion_devuelve401() throws Exception {
    mvc.perform(get(CATEGORIAS))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/errores/sin-sesion"));
  }
}
