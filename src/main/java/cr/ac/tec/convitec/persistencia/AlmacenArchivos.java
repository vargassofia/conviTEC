package cr.ac.tec.convitec.persistencia;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Lectura y escritura atómica de archivos JSON que contienen arreglos. */
public final class AlmacenArchivos {

    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter SUFIJO_CORRUPCION =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final Path carpeta;
    private final ResultadoCarga resultadoCarga;

    public AlmacenArchivos(Path carpeta) {
        this(carpeta, new ResultadoCarga());
    }

    public AlmacenArchivos(Path carpeta, ResultadoCarga resultadoCarga) {
        if (carpeta == null || resultadoCarga == null) {
            throw new IllegalArgumentException("La carpeta y el resultado de carga son obligatorios");
        }
        this.carpeta = carpeta.toAbsolutePath().normalize();
        this.resultadoCarga = resultadoCarga;
    }

    public synchronized <T> void guardarArreglo(String nombre, T[] datos) {
        Path destino = resolver(nombre);
        if (datos == null) {
            throw new IllegalArgumentException("El arreglo a guardar no puede ser null");
        }
        Path temporal = destino.resolveSibling(destino.getFileName() + ".tmp");
        try {
            Files.createDirectories(carpeta);
            Files.writeString(temporal, GSON.toJson(datos), StandardCharsets.UTF_8);
            try {
                Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | SecurityException e) {
            throw new PersistenciaException("No se pudo guardar " + destino + ": " + e.getMessage(), e);
        }
    }

    public synchronized <T> T[] cargarArreglo(String nombre, Class<T[]> tipo) {
        if (tipo == null || !tipo.isArray()) {
            throw new IllegalArgumentException("El tipo de carga debe ser un arreglo");
        }
        Path archivo = resolver(nombre);
        Path temporal = archivo.resolveSibling(archivo.getFileName() + ".tmp");
        try {
            if (Files.exists(temporal)) {
                Files.delete(temporal);
                resultadoCarga.agregarAviso("Se descartó el temporal incompleto " + temporal.getFileName());
            }
            if (!Files.exists(archivo)) {
                resultadoCarga.agregarAviso("No existe " + archivo.getFileName() + "; se inicia vacío");
                return arregloVacio(tipo);
            }
            String json = Files.readString(archivo, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                throw new JsonParseException("El archivo está vacío");
            }
            JsonElement elemento = JsonParser.parseString(json);
            if (!elemento.isJsonArray()) {
                throw new JsonParseException("Se esperaba un arreglo JSON");
            }
            T[] datos = GSON.fromJson(elemento, tipo);
            if (datos == null) {
                throw new JsonParseException("El contenido no coincide con el tipo esperado");
            }
            return datos;
        } catch (CharacterCodingException e) {
            renombrarCorrupto(archivo, new JsonParseException("El archivo no contiene UTF-8 válido", e));
            return arregloVacio(tipo);
        } catch (IOException | SecurityException e) {
            throw new PersistenciaException("No se pudo leer " + archivo + ": " + e.getMessage(), e);
        } catch (JsonParseException | IllegalStateException e) {
            renombrarCorrupto(archivo, e);
            return arregloVacio(tipo);
        }
    }

    public synchronized ResultadoCarga getResultadoCarga() {
        return resultadoCarga;
    }

    private Path resolver(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del archivo no puede ser vacío");
        }
        Path ruta = carpeta.resolve(nombre).normalize();
        if (!ruta.getParent().equals(carpeta)) {
            throw new IllegalArgumentException("El archivo debe estar directamente en la carpeta de la sede");
        }
        return ruta;
    }

    private void renombrarCorrupto(Path archivo, RuntimeException causa) {
        String fecha = LocalDateTime.now().format(SUFIJO_CORRUPCION);
        Path respaldo = archivo.resolveSibling(archivo.getFileName() + ".corrupto-" + fecha);
        try {
            Files.move(archivo, respaldo, StandardCopyOption.REPLACE_EXISTING);
            resultadoCarga.agregarAviso("Archivo dañado " + archivo.getFileName() + " renombrado a "
                    + respaldo.getFileName() + ": " + causa.getMessage());
        } catch (IOException | SecurityException e) {
            throw new PersistenciaException("No se pudo aislar el archivo dañado " + archivo + ": "
                    + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T[] arregloVacio(Class<T[]> tipo) {
        return (T[]) java.lang.reflect.Array.newInstance(tipo.getComponentType(), 0);
    }
}
