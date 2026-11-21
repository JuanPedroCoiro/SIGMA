package com.ciemsa.sigma.datosdemo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ciemsa.sigma.administracion.UsuarioDetailsService;
import com.ciemsa.sigma.ot.application.AnulacionOtService;
import com.ciemsa.sigma.ot.application.AsignacionOtService;
import com.ciemsa.sigma.ot.application.EjecucionOtService;
import com.ciemsa.sigma.ot.application.IncidenteService;
import com.ciemsa.sigma.ot.application.PausaOtService;
import com.ciemsa.sigma.ot.infrastructure.IncidenteRepository;
import com.ciemsa.sigma.shared.configuracion.RelojDesplazable;
import com.ciemsa.sigma.shared.configuracion.TiempoConfig;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * El escenario de la demo y el reloj desplazable solo existen con el perfil dev y la propiedad en
 * true. En cualquier otro caso (producción, tests, dev sin la propiedad) el reloj es el de siempre.
 */
class DatosDemoConfigTest {

  private final ApplicationContextRunner contexto =
      new ApplicationContextRunner()
          .withUserConfiguration(TiempoConfig.class, DatosDemoConfig.class)
          // TiempoConfig habilita la auditoría JPA, que pide el metamodelo: acá no hay entidades
          .withBean(
              "jpaMappingContext",
              JpaMetamodelMappingContext.class,
              () -> mock(JpaMetamodelMappingContext.class))
          .withBean(IncidenteRepository.class, () -> mock(IncidenteRepository.class))
          .withBean(IncidenteService.class, () -> mock(IncidenteService.class))
          .withBean(AsignacionOtService.class, () -> mock(AsignacionOtService.class))
          .withBean(PausaOtService.class, () -> mock(PausaOtService.class))
          .withBean(EjecucionOtService.class, () -> mock(EjecucionOtService.class))
          .withBean(AnulacionOtService.class, () -> mock(AnulacionOtService.class))
          .withBean(UsuarioDetailsService.class, () -> mock(UsuarioDetailsService.class))
          .withBean(EntityManager.class, () -> mock(EntityManager.class))
          .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));

  @Test
  void fueraDeDevNoSeActivaAunqueEsteLaPropiedad() {
    contexto
        .withPropertyValues("sigma.datos-demo.habilitado=true")
        .run(
            ctx -> {
              assertThat(ctx).doesNotHaveBean(GeneradorDatosDemo.class);
              assertThat(ctx).hasSingleBean(Clock.class);
              assertThat(ctx.getBean(Clock.class)).isNotInstanceOf(RelojDesplazable.class);
            });
  }

  @Test
  void enDevSinLaPropiedadNoSeActiva() {
    contexto
        .withPropertyValues("spring.profiles.active=dev")
        .run(
            ctx -> {
              assertThat(ctx).doesNotHaveBean(GeneradorDatosDemo.class);
              assertThat(ctx).hasSingleBean(Clock.class);
              assertThat(ctx.getBean(Clock.class)).isNotInstanceOf(RelojDesplazable.class);
            });
  }

  @Test
  void enDevConLaPropiedadEnFalseNoSeActiva() {
    contexto
        .withPropertyValues("spring.profiles.active=dev", "sigma.datos-demo.habilitado=false")
        .run(
            ctx -> {
              assertThat(ctx).doesNotHaveBean(GeneradorDatosDemo.class);
              assertThat(ctx.getBean(Clock.class)).isNotInstanceOf(RelojDesplazable.class);
            });
  }

  @Test
  void enDevConLaPropiedadSeActivaConElRelojDesplazable() {
    contexto
        .withPropertyValues("spring.profiles.active=dev", "sigma.datos-demo.habilitado=true")
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(GeneradorDatosDemo.class);
              assertThat(ctx).hasSingleBean(Clock.class);
              assertThat(ctx.getBean(Clock.class)).isInstanceOf(RelojDesplazable.class);
            });
  }
}
