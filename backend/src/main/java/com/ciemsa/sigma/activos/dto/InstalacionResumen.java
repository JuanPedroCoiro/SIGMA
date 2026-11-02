package com.ciemsa.sigma.activos.dto;

import com.ciemsa.sigma.activos.Instalacion;

/** Lo que otros módulos necesitan de una instalación: su organismo define el SLA (RN-02). */
public record InstalacionResumen(Long id, Long idOrganismo) {

  public static InstalacionResumen de(Instalacion instalacion) {
    return new InstalacionResumen(instalacion.getId(), instalacion.getIdOrganismo());
  }
}
