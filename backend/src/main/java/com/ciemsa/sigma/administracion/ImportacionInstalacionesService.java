package com.ciemsa.sigma.administracion;

import com.ciemsa.sigma.activos.Instalacion;
import com.ciemsa.sigma.activos.InstalacionService;
import com.ciemsa.sigma.activos.dto.NuevaInstalacion;
import com.ciemsa.sigma.administracion.dto.ErrorFila;
import com.ciemsa.sigma.administracion.dto.ResultadoImportacion;
import com.ciemsa.sigma.ot.application.OrganismoService;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga inicial de instalaciones desde una planilla (HU-02, RF-29). Valida todas las filas y, si
 * alguna tiene errores, no importa ninguna: se corrige la planilla y se vuelve a subir entera.
 */
@Service
@Transactional
public class ImportacionInstalacionesService {

  static final String CODIGO = "codigo";
  static final String NOMBRE = "nombre";
  static final String RUTA_ZONA = "ruta_zona";
  static final String LATITUD = "latitud";
  static final String LONGITUD = "longitud";
  private static final List<String> COLUMNAS =
      List.of(CODIGO, NOMBRE, RUTA_ZONA, LATITUD, LONGITUD);

  /** Largos máximos de las columnas de texto de la tabla instalacion. */
  private static final Map<String, Integer> LARGO_MAXIMO =
      Map.of(CODIGO, 30, NOMBRE, 150, RUTA_ZONA, 100);

  private static final int DECIMALES_COORDENADA = 6;

  private final LectorPlanilla lectorPlanilla;
  private final InstalacionService instalacionService;
  private final OrganismoService organismoService;
  private final String organismo;

  public ImportacionInstalacionesService(
      LectorPlanilla lectorPlanilla,
      InstalacionService instalacionService,
      OrganismoService organismoService,
      @Value("${sigma.importacion.organismo}") String organismo) {
    this.lectorPlanilla = lectorPlanilla;
    this.instalacionService = instalacionService;
    this.organismoService = organismoService;
    this.organismo = organismo;
  }

  public ResultadoImportacion importar(InputStream archivo) {
    Planilla planilla = lectorPlanilla.leer(archivo);
    validarEncabezados(planilla);

    List<ErrorFila> errores = new ArrayList<>();
    Map<String, NuevaInstalacion> nuevas = new LinkedHashMap<>();
    Map<String, Integer> filaPorCodigo = new HashMap<>();
    for (Planilla.Fila fila : planilla.filas()) {
      NuevaInstalacion nueva = validar(fila, errores);
      if (nueva == null) continue;
      Integer filaAnterior = filaPorCodigo.putIfAbsent(nueva.codigo(), fila.numero());
      if (filaAnterior != null) {
        errores.add(
            new ErrorFila(
                fila.numero(),
                CODIGO,
                "El código " + nueva.codigo() + " ya aparece en la fila " + filaAnterior + "."));
      } else {
        nuevas.put(nueva.codigo(), nueva);
      }
    }

    Set<String> existentes = instalacionService.codigosExistentes(nuevas.keySet());
    for (String codigo : existentes) {
      errores.add(
          new ErrorFila(
              filaPorCodigo.get(codigo),
              CODIGO,
              "Ya existe una instalación con el código " + codigo + "."));
    }

    if (!errores.isEmpty()) {
      errores.sort(Comparator.comparingInt(ErrorFila::fila));
      return ResultadoImportacion.conErrores(errores);
    }
    Long idOrganismo = organismoService.obtenerIdPorNombre(organismo);
    return ResultadoImportacion.exitoso(
        instalacionService.crearTodas(List.copyOf(nuevas.values()), idOrganismo));
  }

  private static void validarEncabezados(Planilla planilla) {
    List<String> faltantes =
        COLUMNAS.stream().filter(c -> !planilla.encabezados().contains(c)).toList();
    if (!faltantes.isEmpty()) {
      throw new PlanillaInvalidaException(
          "Faltan las columnas "
              + String.join(", ", faltantes)
              + ". La primera fila tiene que tener: "
              + String.join(";", COLUMNAS)
              + ".");
    }
    if (planilla.filas().isEmpty()) {
      throw new PlanillaInvalidaException("La planilla no tiene filas de datos.");
    }
  }

  /** Devuelve la instalación si la fila es válida; si no, agrega sus errores y devuelve null. */
  private static NuevaInstalacion validar(Planilla.Fila fila, List<ErrorFila> errores) {
    int erroresAntes = errores.size();
    String codigo = texto(fila, CODIGO, errores);
    String nombre = texto(fila, NOMBRE, errores);
    String rutaZona = texto(fila, RUTA_ZONA, errores);
    BigDecimal latitud = coordenada(fila, LATITUD, errores);
    BigDecimal longitud = coordenada(fila, LONGITUD, errores);

    if (latitud != null && longitud != null && !Instalacion.dentroDeUruguay(latitud, longitud)) {
      String mensaje = "Las coordenadas (" + latitud + ", " + longitud + ") no están en Uruguay.";
      if (Instalacion.dentroDeUruguay(longitud, latitud)) {
        mensaje += " ¿Están invertidas la latitud y la longitud?";
      }
      errores.add(new ErrorFila(fila.numero(), LATITUD + "/" + LONGITUD, mensaje));
    }

    if (errores.size() > erroresAntes) return null;
    return new NuevaInstalacion(codigo, nombre, rutaZona, latitud, longitud);
  }

  private static String texto(Planilla.Fila fila, String columna, List<ErrorFila> errores) {
    String valor = fila.valor(columna);
    if (valor.isEmpty()) {
      errores.add(new ErrorFila(fila.numero(), columna, "Falta el valor de " + columna + "."));
      return null;
    }
    int maximo = LARGO_MAXIMO.get(columna);
    if (valor.length() > maximo) {
      errores.add(
          new ErrorFila(fila.numero(), columna, "El valor supera los " + maximo + " caracteres."));
      return null;
    }
    return valor;
  }

  /** Acepta punto o coma decimal; redondea a los 6 decimales que guarda la base. */
  private static BigDecimal coordenada(
      Planilla.Fila fila, String columna, List<ErrorFila> errores) {
    String valor = fila.valor(columna);
    if (valor.isEmpty()) {
      errores.add(new ErrorFila(fila.numero(), columna, "Falta el valor de " + columna + "."));
      return null;
    }
    try {
      return new BigDecimal(valor.replace(',', '.'))
          .setScale(DECIMALES_COORDENADA, RoundingMode.HALF_UP);
    } catch (NumberFormatException e) {
      errores.add(
          new ErrorFila(
              fila.numero(),
              columna,
              "«" + valor + "» no es un número: usá grados decimales, por ejemplo -34,901234."));
      return null;
    }
  }
}
