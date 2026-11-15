package cr.ac.tec.convitec.grafo;

/**
 * Representa una ruta tal como viene en rutas.json.
 * Gson llena estos campos automáticamente por nombre.
 * Se usa Integer (y no int) para poder detectar si "tiempo" no venía en el archivo.
 */
public class RutaDTO {
    String id;
    String origen;
    String destino;
    Integer tiempo;

    public RutaDTO() {
        // Constructor vacío requerido por Gson
    }

    public RutaDTO(String id, String origen, String destino, Integer tiempo) {
        this.id = id;
        this.origen = origen;
        this.destino = destino;
        this.tiempo = tiempo;
    }
}
