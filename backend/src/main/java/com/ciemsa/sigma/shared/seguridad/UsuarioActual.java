package com.ciemsa.sigma.shared.seguridad;

/** Usuario que hace el pedido en curso. Los servicios lo usan sin depender de Spring Security. */
public interface UsuarioActual {

  Long id();

  Rol rol();
}
