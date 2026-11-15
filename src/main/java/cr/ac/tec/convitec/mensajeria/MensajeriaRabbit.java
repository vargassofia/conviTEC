package cr.ac.tec.convitec.mensajeria;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.config.SedesConocidas;
import cr.ac.tec.convitec.servicios.ICatalogoGlobal;
import cr.ac.tec.convitec.servicios.IInventarioLocal;
import cr.ac.tec.convitec.servicios.IMensajeria;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * Mensajería entre sedes con RabbitMQ (ver TopologiaRabbit para exchanges y colas).
 *
 * Canales:
 * - canalConsumo:    consume la cola de la sede y la cola de stock (acks manuales, basicQos(1)).
 * - canalRespuestas: consume la cola exclusiva de respuestas. Va aparte para que una espera
 *                    nunca bloquee la entrega de respuestas (RabbitMQ entrega en serie por canal).
 * - canalPublicacion: para publicar. Un Channel no es seguro entre hilos, así que se publica
 *                    dentro de synchronized (candadoPublicacion).
 *
 * Por qué un LIBERAR enviado después de un timeout se procesa DESPUÉS de la solicitud original:
 * los dos mensajes van a la MISMA cola ("convitec.sede.<COD>"), por el mismo exchange directo y
 * desde el mismo canal de publicación. RabbitMQ conserva el orden FIFO dentro de una cola para
 * mensajes publicados por un mismo canal, y cada sede la consume con un solo consumidor y
 * basicQos(1) (procesa un mensaje a la vez y hace ack al terminar). Entonces:
 * - si la SOLICITAR todavía estaba en la cola, se procesa primero (reserva) y luego el LIBERAR
 *   la deshace;
 * - si la SOLICITAR ya había vencido (expiration = timeout), RabbitMQ la descarta y el LIBERAR
 *   llega sobre una reserva que no existe: no pasa nada, porque liberar es idempotente.
 * En ningún caso quedan unidades reservadas para siempre. (Esto supone UNA instancia por sede.)
 *
 * java.util.concurrent.TimeoutException solo se atrapa porque la lanza el cliente de RabbitMQ;
 * no se usa ninguna colección.
 */
public class MensajeriaRabbit implements IMensajeria {

    private static final String TIPO_CONTENIDO = "application/json";
    private static final int PERSISTENTE = 2;
    private static final int NO_PERSISTENTE = 1;

    private final ConfigSede config;
    private final String codigoSede;
    private final ProcesadorMensajes procesador;
    private final RespuestasPendientes pendientes = new RespuestasPendientes();
    private final TopologiaRabbit topologia;
    private final Object candadoPublicacion = new Object();

    private Connection conexion;
    private Channel canalConsumo;
    private Channel canalRespuestas;
    private Channel canalPublicacion;
    private volatile String colaRespuestas;
    private volatile boolean iniciada;

    /**
     * @throws IllegalArgumentException si algún argumento es nulo o el código de sede no es LIB, ALA, SJO ni LIM.
     */
    public MensajeriaRabbit(ConfigSede config, IInventarioLocal inventario, ICatalogoGlobal catalogo) {
        this(config, inventario, catalogo, TopologiaRabbit.PRODUCCION);
    }

    /** Con otra topología (las pruebas usan una con sufijo para no chocar con las sedes reales). */
    MensajeriaRabbit(ConfigSede config, IInventarioLocal inventario, ICatalogoGlobal catalogo,
                     TopologiaRabbit topologia) {
        if (config == null) {
            throw new IllegalArgumentException("La mensajería necesita la configuración de la sede.");
        }
        if (!SedesConocidas.esCodigoValido(config.getCodigo())) {
            throw new IllegalArgumentException("Código de sede desconocido en la configuración: " + config.getCodigo());
        }
        this.config = config;
        this.codigoSede = config.getCodigo();
        this.procesador = new ProcesadorMensajes(codigoSede, inventario, catalogo);
        this.topologia = topologia;
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    @Override
    public synchronized void iniciar() {
        if (iniciada) {
            return;
        }
        ConnectionFactory fabrica = new ConnectionFactory();
        fabrica.setHost(config.getRabbitHost());
        fabrica.setPort(config.getRabbitPuerto());
        fabrica.setUsername(config.getRabbitUsuario());
        fabrica.setPassword(config.getRabbitClave());
        fabrica.setConnectionTimeout(5000);
        try {
            conexion = fabrica.newConnection("convitec-" + codigoSede);
            canalConsumo = conexion.createChannel();
            canalRespuestas = conexion.createChannel();
            canalPublicacion = conexion.createChannel();
            declararTopologia();
            empezarAConsumir();
            iniciada = true;
        } catch (IOException | TimeoutException | RuntimeException e) {
            cerrar();
            throw new MensajeriaException("No se pudo conectar a RabbitMQ en " + config.getRabbitHost() + ":"
                    + config.getRabbitPuerto() + " con el usuario '" + config.getRabbitUsuario()
                    + "'. ¿Está corriendo el broker y es correcta la configuración? Detalle: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void cerrar() {
        iniciada = false;
        cerrarSinError(canalConsumo);
        cerrarSinError(canalRespuestas);
        cerrarSinError(canalPublicacion);
        if (conexion != null) {
            try {
                conexion.close();
            } catch (Exception e) {
                // Se ignora: al cerrar no importa si la conexión ya estaba caída
            }
        }
        canalConsumo = null;
        canalRespuestas = null;
        canalPublicacion = null;
        conexion = null;
    }

    public boolean estaIniciada() {
        return iniciada;
    }

    public String getCodigoSede() {
        return codigoSede;
    }

    private void declararTopologia() throws IOException {
        Channel c = canalConsumo;
        c.exchangeDeclare(topologia.exchangeDirecto(), BuiltinExchangeType.DIRECT, true);
        c.exchangeDeclare(topologia.exchangeStock(), BuiltinExchangeType.FANOUT, true);

        // Colas durables, no exclusivas, sin autoDelete (RabbitMQ 4 no permite no-durables compartidas)
        c.queueDeclare(topologia.colaSede(codigoSede), true, false, false, null);
        c.queueBind(topologia.colaSede(codigoSede), topologia.exchangeDirecto(), codigoSede);
        c.queueDeclare(topologia.colaStock(codigoSede), true, false, false, null);
        c.queueBind(topologia.colaStock(codigoSede), topologia.exchangeStock(), "");

        // Cola de respuestas: exclusiva, con nombre generado por el servidor
        colaRespuestas = canalRespuestas.queueDeclare().getQueue();
        if (conexion instanceof AutorecoveringConnection) {
            // Si la conexión se recupera sola, el servidor le da otro nombre a la cola de respuestas
            ((AutorecoveringConnection) conexion).addQueueRecoveryListener((anterior, nuevo) -> {
                if (anterior.equals(colaRespuestas)) {
                    colaRespuestas = nuevo;
                }
            });
        }
    }

    private void empezarAConsumir() throws IOException {
        canalConsumo.basicQos(1);
        canalRespuestas.basicQos(1);
        canalConsumo.basicConsume(topologia.colaSede(codigoSede), false,
                (etiqueta, entrega) -> alRecibir(entrega), etiqueta -> { });
        canalConsumo.basicConsume(topologia.colaStock(codigoSede), false,
                (etiqueta, entrega) -> alRecibir(entrega), etiqueta -> { });
        canalRespuestas.basicConsume(colaRespuestas, false,
                (etiqueta, entrega) -> alRecibirRespuesta(entrega), etiqueta -> { });
    }

    // ------------------------------------------------------------------
    // Recepción (hilos de RabbitMQ)
    // ------------------------------------------------------------------

    /** Mensajes de la cola de la sede y de la cola de stock. */
    private void alRecibir(Delivery entrega) {
        try {
            Mensaje entrante = Mensaje.desdeJson(new String(entrega.getBody(), StandardCharsets.UTF_8));
            Mensaje salida = procesador.procesar(entrante);
            if (salida != null) {
                if (salida.getTipo() == TipoMensaje.RESPUESTA_RESERVA) {
                    responder(entrega.getProperties(), salida);
                } else if (salida.getTipo() == TipoMensaje.ACTUALIZACION_STOCK) {
                    difundir(salida); // después de CONFIRMAR: avisar a todas las sedes el stock nuevo
                }
            }
        } catch (Exception e) {
            // Mensaje mal formado o error al responder: se registra y se sigue (no se tumba la sede)
            System.err.println("[ConviTEC " + codigoSede + "] Error procesando mensaje: " + e.getMessage());
        } finally {
            ack(canalConsumo, entrega);
        }
    }

    /** Respuestas a MIS solicitudes. */
    private void alRecibirRespuesta(Delivery entrega) {
        try {
            Mensaje respuesta = Mensaje.desdeJson(new String(entrega.getBody(), StandardCharsets.UTF_8));
            if (!pendientes.completar(respuesta.getCorrelationId(), respuesta)) {
                System.err.println("[ConviTEC " + codigoSede + "] Respuesta tardía o desconocida ignorada: "
                        + respuesta.getCorrelationId());
            }
        } catch (Exception e) {
            System.err.println("[ConviTEC " + codigoSede + "] Respuesta mal formada ignorada: " + e.getMessage());
        } finally {
            ack(canalRespuestas, entrega);
        }
    }

    private void responder(AMQP.BasicProperties propiedadesSolicitud, Mensaje respuesta) throws IOException {
        String replyTo = propiedadesSolicitud.getReplyTo();
        if (replyTo == null || replyTo.isBlank()) {
            System.err.println("[ConviTEC " + codigoSede + "] Solicitud sin replyTo: no se puede responder "
                    + respuesta.getCorrelationId());
            return;
        }
        AMQP.BasicProperties propiedades = propiedades(respuesta, NO_PERSISTENTE)
                .correlationId(respuesta.getCorrelationId())
                .build();
        publicar("", replyTo, propiedades, respuesta);
    }

    // ------------------------------------------------------------------
    // Envío
    // ------------------------------------------------------------------

    @Override
    public RespuestaReserva solicitarReserva(String sedeDestino, String idPedido, String codigoProducto, int cantidad) {
        validarDestino(sedeDestino);
        validarTexto(idPedido, "idPedido");
        validarTexto(codigoProducto, "codigoProducto");
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad a reservar debe ser mayor que 0 (llegó " + cantidad + ").");
        }
        String nombreDestino = SedesConocidas.nombreDe(sedeDestino);
        if (!iniciada) {
            return RespuestaReserva.rechazo(sedeDestino, "No se pudo enviar la solicitud a " + nombreDestino
                    + ": la mensajería no está iniciada");
        }

        Mensaje solicitud = Mensaje.solicitarReserva(codigoSede, sedeDestino, idPedido.trim(),
                codigoProducto.trim(), cantidad);
        int timeout = config.getTimeoutReservaMs();
        pendientes.registrar(solicitud.getCorrelationId()); // antes de enviar, para no perder una respuesta rápida
        try {
            AMQP.BasicProperties propiedades = propiedades(solicitud, PERSISTENTE)
                    .correlationId(solicitud.getCorrelationId())
                    .replyTo(colaRespuestas)
                    .expiration(String.valueOf(timeout)) // una solicitud vieja no se procesa tarde
                    .build();
            publicar(topologia.exchangeDirecto(), sedeDestino, propiedades, solicitud);
        } catch (Exception e) {
            pendientes.cancelar(solicitud.getCorrelationId());
            return RespuestaReserva.rechazo(sedeDestino, "No se pudo enviar la solicitud a " + nombreDestino
                    + ": " + e.getMessage());
        }

        Mensaje respuesta = pendientes.esperar(solicitud.getCorrelationId(), timeout);
        if (respuesta == null) {
            return RespuestaReserva.rechazo(sedeDestino, nombreDestino + " no respondió a tiempo");
        }
        if (respuesta.isExito()) {
            return RespuestaReserva.ok(sedeDestino);
        }
        String motivo = respuesta.getMotivo() == null ? "reserva rechazada" : respuesta.getMotivo();
        return RespuestaReserva.rechazo(sedeDestino, nombreDestino + ": " + motivo);
    }

    /** @throws MensajeriaException si la mensajería no está iniciada o el envío falla. */
    @Override
    public void confirmarReserva(String sedeDestino, String idPedido, String codigoProducto) {
        validarDestino(sedeDestino);
        validarTexto(idPedido, "idPedido");
        validarTexto(codigoProducto, "codigoProducto");
        enviarDirecto(Mensaje.confirmarReserva(codigoSede, sedeDestino, idPedido.trim(), codigoProducto.trim()));
    }

    /** @throws MensajeriaException si la mensajería no está iniciada o el envío falla. */
    @Override
    public void liberarReserva(String sedeDestino, String idPedido, String codigoProducto) {
        validarDestino(sedeDestino);
        validarTexto(idPedido, "idPedido");
        validarTexto(codigoProducto, "codigoProducto");
        enviarDirecto(Mensaje.liberarReserva(codigoSede, sedeDestino, idPedido.trim(), codigoProducto.trim()));
    }

    /** @throws MensajeriaException si la mensajería no está iniciada o el envío falla. */
    @Override
    public void publicarActualizacionStock(String codigoProducto, int cantidadNueva) {
        validarTexto(codigoProducto, "codigoProducto");
        if (cantidadNueva < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo (llegó " + cantidadNueva + ").");
        }
        exigirIniciada();
        try {
            difundir(Mensaje.actualizacionStock(codigoSede, codigoProducto.trim(), cantidadNueva));
        } catch (IOException | RuntimeException e) {
            throw new MensajeriaException("No se pudo publicar la actualización de stock: " + e.getMessage(), e);
        }
    }

    private void enviarDirecto(Mensaje mensaje) {
        exigirIniciada();
        try {
            publicar(topologia.exchangeDirecto(), mensaje.getSedeDestino(),
                    propiedades(mensaje, PERSISTENTE).build(), mensaje);
        } catch (IOException | RuntimeException e) {
            throw new MensajeriaException("No se pudo enviar " + mensaje.getTipo() + " a "
                    + SedesConocidas.nombreDe(mensaje.getSedeDestino()) + ": " + e.getMessage(), e);
        }
    }

    private void difundir(Mensaje actualizacion) throws IOException {
        publicar(topologia.exchangeStock(), "", propiedades(actualizacion, PERSISTENTE).build(), actualizacion);
    }

    private void publicar(String exchange, String routingKey, AMQP.BasicProperties propiedades, Mensaje mensaje)
            throws IOException {
        byte[] cuerpo = mensaje.aJson().getBytes(StandardCharsets.UTF_8);
        synchronized (candadoPublicacion) {
            Channel canal = canalPublicacion;
            if (canal == null || !canal.isOpen()) {
                throw new MensajeriaException("el canal de RabbitMQ está cerrado");
            }
            canal.basicPublish(exchange, routingKey, propiedades, cuerpo);
        }
    }

    private static AMQP.BasicProperties.Builder propiedades(Mensaje mensaje, int modoEntrega) {
        return new AMQP.BasicProperties.Builder()
                .contentType(TIPO_CONTENIDO)
                .contentEncoding("UTF-8")
                .deliveryMode(modoEntrega)
                .messageId(mensaje.getId())
                .type(mensaje.getTipo().name());
    }

    // ------------------------------------------------------------------
    // Validaciones y utilidades
    // ------------------------------------------------------------------

    private void validarDestino(String sedeDestino) {
        if (sedeDestino == null || !SedesConocidas.esCodigoValido(sedeDestino)) {
            throw new IllegalArgumentException("Sede destino desconocida: '" + sedeDestino
                    + "'. Use LIB, ALA, SJO o LIM.");
        }
        if (sedeDestino.equals(codigoSede)) {
            throw new IllegalArgumentException("La sede " + codigoSede + " no puede enviarse mensajes a sí misma.");
        }
    }

    private static void validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta '" + campo + "'.");
        }
    }

    private void exigirIniciada() {
        if (!iniciada) {
            throw new MensajeriaException("La mensajería de la sede " + codigoSede + " no está iniciada (llame a iniciar()).");
        }
    }

    private void ack(Channel canal, Delivery entrega) {
        try {
            if (canal != null && canal.isOpen()) {
                canal.basicAck(entrega.getEnvelope().getDeliveryTag(), false);
            }
        } catch (Exception e) {
            System.err.println("[ConviTEC " + codigoSede + "] No se pudo confirmar (ack) un mensaje: " + e.getMessage());
        }
    }

    private static void cerrarSinError(Channel canal) {
        if (canal == null) {
            return;
        }
        try {
            if (canal.isOpen()) {
                canal.close();
            }
        } catch (Exception e) {
            // Se ignora: al cerrar no importa si el canal ya estaba cerrado
        }
    }
}
