package com.ciemsa.sigma.shared.archivos.infrastructure;

import com.ciemsa.sigma.shared.archivos.AlmacenArchivos;
import com.ciemsa.sigma.shared.domain.NoEncontradoException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Archivos en un directorio del disco ({@code sigma.archivos.directorio}): en desarrollo, fuera del
 * repo; en el servidor, un volumen de Docker. Cuando haya almacenamiento de objetos, otra
 * implementación elegida por perfil de Spring (guía de arquitectura).
 */
@Component
public class AlmacenArchivosEnDisco implements AlmacenArchivos {

  private static final Pattern EXTENSION = Pattern.compile("[a-z0-9]{1,5}");

  private final Path directorio;

  public AlmacenArchivosEnDisco(@Value("${sigma.archivos.directorio}") Path directorio) {
    this.directorio = directorio.toAbsolutePath().normalize();
  }

  /** Nombre al azar, en una subcarpeta por sus dos primeros caracteres para no llenar una sola. */
  @Override
  public String guardar(byte[] contenido, String extension) {
    if (!EXTENSION.matcher(extension).matches()) {
      throw new IllegalArgumentException("Extensión inválida: " + extension);
    }
    String nombre = UUID.randomUUID() + "." + extension;
    String ubicacion = nombre.substring(0, 2) + "/" + nombre;
    Path destino = resolver(ubicacion);
    try {
      Files.createDirectories(destino.getParent());
      Files.write(destino, contenido, StandardOpenOption.CREATE_NEW);
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo guardar el archivo.", e);
    }
    return ubicacion;
  }

  @Override
  public byte[] leer(String ubicacion) {
    Path archivo = resolver(ubicacion);
    if (!Files.isRegularFile(archivo)) {
      throw new NoEncontradoException("No se encontró el archivo.");
    }
    try {
      return Files.readAllBytes(archivo);
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo leer el archivo.", e);
    }
  }

  @Override
  public void borrar(String ubicacion) {
    try {
      Files.deleteIfExists(resolver(ubicacion));
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo borrar el archivo.", e);
    }
  }

  /** La ubicación siempre queda dentro del directorio, aunque venga alterada (con «..»). */
  private Path resolver(String ubicacion) {
    Path archivo = directorio.resolve(ubicacion).normalize();
    if (!archivo.startsWith(directorio)) {
      throw new IllegalArgumentException("Ubicación fuera del directorio de archivos.");
    }
    return archivo;
  }
}
