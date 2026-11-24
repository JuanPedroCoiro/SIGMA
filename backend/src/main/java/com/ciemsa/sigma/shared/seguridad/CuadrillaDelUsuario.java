package com.ciemsa.sigma.shared.seguridad;

import java.util.Optional;

/**
 * La cuadrilla actual de un usuario, leída de la base: puede cambiar con la sesión abierta (HU-20,
 * CU-13b). La implementa el módulo de administración, para que la seguridad no dependa de él.
 */
public interface CuadrillaDelUsuario {

  /** Vacío si el usuario no existe o no tiene cuadrilla. */
  Optional<Long> idCuadrillaDe(Long idUsuario);
}
