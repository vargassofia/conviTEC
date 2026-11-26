package cr.ac.tec.convitec.mensajeria;

/**
 * Nombres de exchanges y colas de RabbitMQ.
 *
 *   Exchange DIRECTO "convitec.directo"
 *       routing key LIB -> cola durable "convitec.sede.LIB"
 *       routing key ALA -> cola durable "convitec.sede.ALA"   (SOLICITAR, CONFIRMAR, LIBERAR)
 *       ...
 *   Exchange FANOUT "convitec.stock"
 *       -> cola durable "convitec.stock.LIB"
 *       -> cola durable "convitec.stock.ALA"                   (ACTUALIZACION_STOCK, a todas)
 *       ...
 *   Cada sede además tiene una cola de RESPUESTAS exclusiva con nombre generado por el
 *   servidor (amq.gen-...), que se pone como replyTo en sus solicitudes.
 *
 * RabbitMQ 4 no permite colas "no durables y no exclusivas": por eso las colas de sede son
 * durables (sobreviven a reinicios del broker y guardan los mensajes si la sede está apagada)
 * y la de respuestas es exclusiva (se borra sola al cerrar la conexión).
 *
 * El sufijo permite crear una topología aparte para pruebas, sin chocar con las sedes reales.
 */
public final class TopologiaRabbit {

    public static final String EXCHANGE_DIRECTO = "convitec.directo";
    public static final String EXCHANGE_STOCK = "convitec.stock";
    public static final String PREFIJO_COLA_SEDE = "convitec.sede.";
    public static final String PREFIJO_COLA_STOCK = "convitec.stock.";

    /** Topología real de las sedes. */
    public static final TopologiaRabbit PRODUCCION = new TopologiaRabbit("");

    private final String sufijo;

    /** @param sufijo se agrega al final de todos los nombres ("" para la topología real). */
    public TopologiaRabbit(String sufijo) {
        this.sufijo = sufijo == null ? "" : sufijo;
    }

    public String exchangeDirecto() {
        return EXCHANGE_DIRECTO + sufijo;
    }

    public String exchangeStock() {
        return EXCHANGE_STOCK + sufijo;
    }

    /** "convitec.sede.ALA": aquí llegan las solicitudes, confirmaciones y liberaciones para ALA. */
    public String colaSede(String codigoSede) {
        return PREFIJO_COLA_SEDE + codigoSede + sufijo;
    }

    /** "convitec.stock.ALA": aquí le llegan a ALA las actualizaciones de stock de todas las sedes. */
    public String colaStock(String codigoSede) {
        return PREFIJO_COLA_STOCK + codigoSede + sufijo;
    }

    public String getSufijo() {
        return sufijo;
    }
}
