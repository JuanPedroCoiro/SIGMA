package com.ciemsa.sigma.administracion;

import java.io.InputStream;

/**
 * Lee una planilla con encabezados en la primera fila (RF-29). Hoy solo CSV; un lector de XLSX
 * devolvería lo mismo.
 */
public interface LectorPlanilla {

  /**
   * @throws PlanillaInvalidaException si el archivo no se puede leer o está vacío
   */
  Planilla leer(InputStream archivo);
}
