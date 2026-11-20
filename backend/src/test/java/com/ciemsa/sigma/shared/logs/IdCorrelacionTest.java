package com.ciemsa.sigma.shared.logs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ciemsa.sigma.shared.api.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Id de correlación de cada pedido y respuesta genérica de los errores no controlados (#95). Sin
 * base ni contexto de Spring: solo el filtro, el manejador de errores y un controller de prueba.
 */
class IdCorrelacionTest {

  private static final String CABECERA = IdCorrelacionFilter.CABECERA;
  private static final int LARGO_UUID = 36;

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new IdCorrelacionFilter())
            .build();
  }

  @Test
  void sinCabecera_generaUnIdYLoDevuelve() throws Exception {
    MvcResult resultado =
        mvc.perform(get("/ok"))
            .andExpect(status().isOk())
            .andExpect(header().exists(CABECERA))
            .andReturn();

    assertThat(resultado.getResponse().getHeader(CABECERA)).hasSize(LARGO_UUID);
  }

  @Test
  void conCabeceraValida_laReutiliza() throws Exception {
    mvc.perform(get("/ok").header(CABECERA, "proxy-1234abcd"))
        .andExpect(header().string(CABECERA, "proxy-1234abcd"));
  }

  @Test
  void conCabeceraInvalida_noLaUsaYGeneraOtra() throws Exception {
    String inyectado = "x\nERROR falso";

    MvcResult resultado = mvc.perform(get("/ok").header(CABECERA, inyectado)).andReturn();

    assertThat(resultado.getResponse().getHeader(CABECERA))
        .isNotEqualTo(inyectado)
        .hasSize(LARGO_UUID);
  }

  @Test
  void errorNoControlado_respondeGenericoConElIdDeCorrelacion() throws Exception {
    MvcResult resultado =
        mvc.perform(get("/falla").header(CABECERA, "pedido-1234abcd"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.type").value("/errores/interno"))
            .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado."))
            .andExpect(jsonPath("$.idCorrelacion").value("pedido-1234abcd"))
            .andReturn();

    assertThat(resultado.getResponse().getContentAsString()).doesNotContain("detalle interno");
  }

  @RestController
  static class ControladorDePrueba {

    @GetMapping("/ok")
    String ok() {
      return "ok";
    }

    @GetMapping("/falla")
    String falla() {
      throw new IllegalStateException("detalle interno");
    }
  }
}
