package cr.ac.tec.convitec.web;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * Lee ubicaciones.json: las coordenadas de cada lugar del grafo, SOLO para dibujarlo en el mapa.
 *
 * {
 *   "ubicaciones": [ { "nombre": "San José", "lat": 9.9281, "lng": -84.0907 }, ... ]
 * }
 *
 * Los tiempos y los caminos salen siempre del grafo (rutas.json + Dijkstra), nunca de las coordenadas.
 * Gson lee a un ARREGLO (no a List), como el resto del proyecto.
 */
final class CargadorUbicaciones {

    private static final Gson GSON = new Gson();

    private CargadorUbicaciones() {
        // Clase de utilidad: no se instancia
    }

    /** Una ubicación con sus coordenadas. */
    static final class Ubicacion {
        String nombre;
        Double lat;
        Double lng;

        Ubicacion() {
            // Para Gson
        }

        Ubicacion(String nombre, double lat, double lng) {
            this.nombre = nombre;
            this.lat = lat;
            this.lng = lng;
        }
    }

    /** Forma completa del archivo. */
    private static final class Archivo {
        Ubicacion[] ubicaciones;
    }

    /**
     * Carga desde src/main/resources (por ejemplo "ubicaciones.json").
     *
     * @throws IllegalArgumentException si el archivo no existe o tiene errores (el mensaje dice cuál).
     */
    static Ubicacion[] cargarDesdeRecurso(String nombreRecurso) {
        try (InputStream entrada = CargadorUbicaciones.class.getClassLoader().getResourceAsStream(nombreRecurso)) {
            if (entrada == null) {
                throw new IllegalArgumentException("No se encontró el archivo de ubicaciones: " + nombreRecurso);
            }
            return cargarDesdeTexto(new String(entrada.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo de ubicaciones: " + nombreRecurso, e);
        }
    }

    /**
     * Carga desde un texto JSON.
     *
     * @throws IllegalArgumentException si el JSON está vacío o mal escrito, falta un nombre o una coordenada,
     *                                  una coordenada está fuera de rango o un nombre está repetido.
     */
    static Ubicacion[] cargarDesdeTexto(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("El archivo de ubicaciones está vacío.");
        }
        Archivo archivo;
        try {
            archivo = GSON.fromJson(json, Archivo.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("El archivo de ubicaciones no es un JSON válido: " + e.getMessage(), e);
        }
        if (archivo == null || archivo.ubicaciones == null) {
            throw new IllegalArgumentException("El archivo de ubicaciones no tiene la lista \"ubicaciones\".");
        }
        Ubicacion[] lista = archivo.ubicaciones;
        for (int i = 0; i < lista.length; i++) {
            Ubicacion u = lista[i];
            String donde = "La ubicación #" + (i + 1);
            if (u == null || u.nombre == null || u.nombre.isBlank()) {
                throw new IllegalArgumentException(donde + " no tiene nombre.");
            }
            if (u.lat == null || u.lng == null) {
                throw new IllegalArgumentException(donde + " (" + u.nombre + ") no tiene lat y lng.");
            }
            if (u.lat < -90 || u.lat > 90 || u.lng < -180 || u.lng > 180) {
                throw new IllegalArgumentException(donde + " (" + u.nombre + ") tiene coordenadas fuera de rango.");
            }
            for (int j = 0; j < i; j++) {
                if (normalizar(lista[j].nombre).equals(normalizar(u.nombre))) {
                    throw new IllegalArgumentException("La ubicación '" + u.nombre + "' está repetida.");
                }
            }
        }
        return lista;
    }

    /** Busca por nombre ignorando tildes, mayúsculas y espacios extra; null si no está. */
    static Ubicacion buscar(Ubicacion[] ubicaciones, String nombre) {
        String clave = normalizar(nombre);
        for (int i = 0; i < ubicaciones.length; i++) {
            if (normalizar(ubicaciones[i].nombre).equals(clave)) {
                return ubicaciones[i];
            }
        }
        return null;
    }

    /** "  San  José " -> "san jose" (igual que la búsqueda del Grafo). */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
