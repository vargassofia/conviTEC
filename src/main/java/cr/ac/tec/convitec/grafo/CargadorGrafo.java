package cr.ac.tec.convitec.grafo;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lee rutas.json y construye el Grafo.
 *
 * Formato esperado:
 * {
 *   "rutas": [
 *     { "id": "R01", "origen": "Liberia", "destino": "Cañas", "tiempo": 65 },
 *     ...
 *   ]
 * }
 *
 * Gson deserializa a un ARREGLO (RutaDTO[]), nunca a List, para cumplir
 * la regla de no usar colecciones de Java.
 */
public final class CargadorGrafo {

    private static final Gson GSON = new Gson();

    private CargadorGrafo() {
        // Clase de utilidad: no se instancia
    }

    /** Carga desde un archivo en disco (por ejemplo, la ruta que venga en la configuración). */
    public static Grafo cargarDesdeArchivo(Path archivo) {
        if (archivo == null || !Files.exists(archivo)) {
            throw new GrafoException("No se encontró el archivo de rutas: " + archivo);
        }
        try {
            String json = Files.readString(archivo, StandardCharsets.UTF_8);
            return cargarDesdeTexto(json);
        } catch (IOException e) {
            throw new GrafoException("No se pudo leer el archivo de rutas: " + archivo, e);
        }
    }

    /** Carga desde src/main/resources (por ejemplo "rutas.json"). */
    public static Grafo cargarDesdeRecurso(String nombreRecurso) {
        try (InputStream entrada = CargadorGrafo.class.getClassLoader().getResourceAsStream(nombreRecurso)) {
            if (entrada == null) {
                throw new GrafoException("No se encontró el recurso: " + nombreRecurso);
            }
            String json = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
            return cargarDesdeTexto(json);
        } catch (IOException e) {
            throw new GrafoException("No se pudo leer el recurso: " + nombreRecurso, e);
        }
    }

    /** Carga desde un texto JSON. Es la que usan las pruebas. */
    public static Grafo cargarDesdeTexto(String json) {
        if (json == null || json.isBlank()) {
            throw new GrafoException("El archivo de rutas está vacío.");
        }

        ArchivoRutasDTO archivo;
        try {
            archivo = GSON.fromJson(json, ArchivoRutasDTO.class);
        } catch (JsonParseException e) {
            throw new GrafoException("El archivo de rutas no tiene un formato JSON válido: " + e.getMessage(), e);
        }

        if (archivo == null || archivo.rutas == null || archivo.rutas.length == 0) {
            throw new GrafoException("El archivo de rutas no contiene ninguna ruta.");
        }

        Grafo grafo = new Grafo(archivo.rutas.length * 2);
        for (int k = 0; k < archivo.rutas.length; k++) {
            RutaDTO ruta = archivo.rutas[k];
            validarRuta(ruta, k);
            validarIdNoRepetido(archivo.rutas, k);
            grafo.agregarArista(ruta.origen, ruta.destino, ruta.tiempo, ruta.id);
        }
        return grafo;
    }

    private static void validarRuta(RutaDTO ruta, int posicion) {
        String donde = "La ruta #" + (posicion + 1);
        if (ruta == null) {
            throw new GrafoException(donde + " está vacía.");
        }
        if (estaVacio(ruta.id)) {
            throw new GrafoException(donde + " no tiene 'id'.");
        }
        if (estaVacio(ruta.origen)) {
            throw new GrafoException(donde + " (" + ruta.id + ") no tiene 'origen'.");
        }
        if (estaVacio(ruta.destino)) {
            throw new GrafoException(donde + " (" + ruta.id + ") no tiene 'destino'.");
        }
        if (ruta.tiempo == null) {
            throw new GrafoException(donde + " (" + ruta.id + ") no tiene 'tiempo'.");
        }
        // Tiempo <= 0, origen == destino y pares duplicados los valida Grafo.agregarArista
    }

    /** Revisa que el id de la ruta en la posición k no haya aparecido antes. */
    private static void validarIdNoRepetido(RutaDTO[] rutas, int k) {
        String id = rutas[k].id.trim();
        for (int i = 0; i < k; i++) {
            if (rutas[i] != null && rutas[i].id != null && rutas[i].id.trim().equalsIgnoreCase(id)) {
                throw new GrafoException("El id de ruta '" + id + "' está repetido.");
            }
        }
    }

    private static boolean estaVacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
