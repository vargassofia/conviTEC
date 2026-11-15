package cr.ac.tec.convitec.mensajeria;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.time.LocalDateTime;

/**
 * Un mensaje entre sedes. Viaja por RabbitMQ como JSON (UTF-8).
 *
 * Ejemplo (SOLICITAR_RESERVA de Alajuela a Limón):
 * {
 *   "id": "ALA-1763140000000-1-48213", "tipo": "SOLICITAR_RESERVA",
 *   "sedeOrigen": "ALA", "sedeDestino": "LIM", "correlationId": "ALA-1763140000000-1-48213",
 *   "idPedido": "P7", "codigoProducto": "B01", "cantidad": 2,
 *   "exito": false, "fechaHora": "2026-11-15T10:30:00.123"
 * }
 *
 * - sedeDestino es null en las difusiones (ACTUALIZACION_STOCK).
 * - correlationId une una solicitud con su respuesta (la respuesta lleva el mismo).
 * - Se crea con las fábricas estáticas (Mensaje.solicitarReserva(...), etc.) o con desdeJson.
 * - Es inmutable.
 */
public final class Mensaje {

    private static final Gson GSON = new Gson();
    private static long contador = 0;

    private String id;
    private TipoMensaje tipo;
    private String sedeOrigen;
    private String sedeDestino;
    private String correlationId;
    private String idPedido;
    private String codigoProducto;
    private Integer cantidad;
    private boolean exito;
    private String motivo;
    private String fechaHora;

    /** Lo usa Gson. */
    private Mensaje() {
    }

    private Mensaje(TipoMensaje tipo, String sedeOrigen, String sedeDestino, String correlationId,
                    String idPedido, String codigoProducto, Integer cantidad, boolean exito, String motivo) {
        this.id = generarId(sedeOrigen);
        this.tipo = tipo;
        this.sedeOrigen = sedeOrigen;
        this.sedeDestino = sedeDestino;
        this.correlationId = correlationId != null ? correlationId : this.id;
        this.idPedido = idPedido;
        this.codigoProducto = codigoProducto;
        this.cantidad = cantidad;
        this.exito = exito;
        this.motivo = motivo;
        this.fechaHora = LocalDateTime.now().toString();
    }

    // ------------------------------------------------------------------
    // Fábricas
    // ------------------------------------------------------------------

    /** Solicitud de reserva. Su correlationId es su propio id. */
    public static Mensaje solicitarReserva(String origen, String destino, String idPedido,
                                           String codigoProducto, int cantidad) {
        return new Mensaje(TipoMensaje.SOLICITAR_RESERVA, origen, destino, null,
                idPedido, codigoProducto, cantidad, false, null);
    }

    /** Respuesta a una solicitud: va a quien la pidió y lleva el mismo correlationId. */
    public static Mensaje respuestaReserva(Mensaje solicitud, String origen, boolean exito, String motivo) {
        return new Mensaje(TipoMensaje.RESPUESTA_RESERVA, origen, solicitud.getSedeOrigen(),
                solicitud.getCorrelationId(), solicitud.getIdPedido(), solicitud.getCodigoProducto(),
                solicitud.getCantidad(), exito, motivo);
    }

    public static Mensaje confirmarReserva(String origen, String destino, String idPedido, String codigoProducto) {
        return new Mensaje(TipoMensaje.CONFIRMAR_RESERVA, origen, destino, null,
                idPedido, codigoProducto, null, false, null);
    }

    public static Mensaje liberarReserva(String origen, String destino, String idPedido, String codigoProducto) {
        return new Mensaje(TipoMensaje.LIBERAR_RESERVA, origen, destino, null,
                idPedido, codigoProducto, null, false, null);
    }

    /** Difusión a todas las sedes: sedeDestino = null. */
    public static Mensaje actualizacionStock(String origen, String codigoProducto, int cantidadNueva) {
        return new Mensaje(TipoMensaje.ACTUALIZACION_STOCK, origen, null, null,
                null, codigoProducto, cantidadNueva, true, null);
    }

    // ------------------------------------------------------------------
    // JSON
    // ------------------------------------------------------------------

    public String aJson() {
        return GSON.toJson(this);
    }

    /**
     * Lee un mensaje desde JSON. Solo revisa que el JSON sea válido; si faltan datos
     * lo decide quien procesa el mensaje (ProcesadorMensajes).
     *
     * @throws MensajeriaException si el texto está vacío o no es un JSON válido.
     */
    public static Mensaje desdeJson(String json) {
        if (json == null || json.isBlank()) {
            throw new MensajeriaException("Mensaje vacío.");
        }
        Mensaje m;
        try {
            m = GSON.fromJson(json, Mensaje.class);
        } catch (JsonParseException e) {
            throw new MensajeriaException("Mensaje mal formado: " + e.getMessage(), e);
        }
        if (m == null) {
            throw new MensajeriaException("Mensaje mal formado: no es un objeto JSON.");
        }
        return m;
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public TipoMensaje getTipo() {
        return tipo;
    }

    public String getSedeOrigen() {
        return sedeOrigen;
    }

    public String getSedeDestino() {
        return sedeDestino;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getIdPedido() {
        return idPedido;
    }

    public String getCodigoProducto() {
        return codigoProducto;
    }

    /** null si el mensaje no lleva cantidad (CONFIRMAR, LIBERAR) o si no venía en el JSON. */
    public Integer getCantidad() {
        return cantidad;
    }

    public boolean isExito() {
        return exito;
    }

    public String getMotivo() {
        return motivo;
    }

    /** Fecha y hora de creación en texto ISO (ej. "2026-11-15T10:30:00.123"). */
    public String getFechaHora() {
        return fechaHora;
    }

    @Override
    public String toString() {
        return aJson();
    }

    /**
     * Id único: código de sede + milisegundos + contador + número aleatorio.
     * El código de sede evita choques entre computadoras y el contador entre mensajes del mismo milisegundo.
     */
    private static synchronized String generarId(String origen) {
        contador++;
        return origen + "-" + System.currentTimeMillis() + "-" + contador + "-" + (int) (Math.random() * 100000);
    }
}
