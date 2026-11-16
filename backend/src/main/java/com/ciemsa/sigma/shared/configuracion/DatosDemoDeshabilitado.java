package com.ciemsa.sigma.shared.configuracion;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** La negación de {@link DatosDemoHabilitado}: el caso de siempre (producción, tests, dev). */
class DatosDemoDeshabilitado implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return !DatosDemoHabilitado.habilitado(context.getEnvironment());
  }
}
