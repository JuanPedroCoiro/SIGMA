package com.ciemsa.sigma.shared.archivos;

/**
 * Dónde se guardan los archivos subidos (fotos de incidentes, #71). Adapter de la Fig. 19: hoy en
 * disco; después, almacenamiento de objetos, sin tocar a quien lo usa. La ubicación es una clave
 * que devuelve {@link #guardar} y que se guarda en la base; nunca sale del nombre que mandó el
 * usuario.
 */
public interface AlmacenArchivos {

  /**
   * Guarda el contenido y devuelve su ubicación. {@code extension} sin punto, por ejemplo «jpg».
   */
  String guardar(byte[] contenido, String extension);

  /** El contenido guardado en esa ubicación; 404 si no está. */
  byte[] leer(String ubicacion);

  /** Borra el archivo si existe: para deshacer una subida que no terminó de guardarse. */
  void borrar(String ubicacion);
}
