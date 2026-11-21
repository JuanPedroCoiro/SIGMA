package com.ciemsa.sigma.shared.configuracion;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Datos de la demo (#81) activos: perfil {@code dev} y {@code sigma.datos-demo.habilitado=true}.
 * Las dos llaves a la vez, así la propiedad sola nunca los activa fuera de desarrollo. Es la única
 * definición: la usan el generador y el reloj desplazable.
 */
public class DatosDemoHabilitado implements Condition {

  public static final String PROPIEDAD = "sigma.datos-demo.habilitado";

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return habilitado(context.getEnvironment());
  }

  static boolean habilitado(Environment entorno) {
    return entorno.acceptsProfiles(Profiles.of("dev"))
        && entorno.getProperty(PROPIEDAD, Boolean.class, false);
  }
}
