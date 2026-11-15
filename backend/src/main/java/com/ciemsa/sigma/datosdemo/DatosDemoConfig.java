package com.ciemsa.sigma.datosdemo;

import com.ciemsa.sigma.shared.configuracion.DatosDemoHabilitado;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Escenario de la demo (#81): solo con el perfil {@code dev} y {@code
 * sigma.datos-demo.habilitado=true}. En cualquier otro caso el generador no existe.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(DatosDemoHabilitado.class)
@Import(GeneradorDatosDemo.class)
public class DatosDemoConfig {}
