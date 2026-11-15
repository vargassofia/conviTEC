package cr.ac.tec.convitec.mensajeria;

import cr.ac.tec.convitec.servicios.ICatalogoGlobal;
import cr.ac.tec.convitec.servicios.IInventarioLocal;

/**
 * Decide qué hacer con un mensaje que llega a ESTA sede. No sabe nada de RabbitMQ:
 * recibe un Mensaje y devuelve el Mensaje que hay que enviar como respuesta (o null).
 *
 * - SOLICITAR_RESERVA   -> inventario.reservar   -> devuelve RESPUESTA_RESERVA (mismo correlationId)
 * - CONFIRMAR_RESERVA   -> inventario.confirmar  -> devuelve ACTUALIZACION_STOCK para difundir
 * - LIBERAR_RESERVA     -> inventario.liberar    -> null
 * - ACTUALIZACION_STOCK de otra sede -> catalogo.actualizarStock -> null (la propia se ignora)
 *
 * Un mensaje malo (sin tipo, para otra sede, con datos faltantes) se IGNORA y se registra en
 * System.err, sin lanzar excepción: un mensaje malo no debe tumbar la sede.
 */
public class ProcesadorMensajes {

    private final String codigoSede;
    private final IInventarioLocal inventario;
    private final ICatalogoGlobal catalogo;

    /**
     * @throws IllegalArgumentException si algún argumento es nulo.
     */
    public ProcesadorMensajes(String codigoSede, IInventarioLocal inventario, ICatalogoGlobal catalogo) {
        if (codigoSede == null || codigoSede.isBlank()) {
            throw new IllegalArgumentException("El procesador de mensajes necesita el código de la sede.");
        }
        if (inventario == null || catalogo == null) {
            throw new IllegalArgumentException("El procesador de mensajes necesita el inventario y el catálogo.");
        }
        this.codigoSede = codigoSede;
        this.inventario = inventario;
        this.catalogo = catalogo;
    }

    /**
     * Procesa un mensaje entrante.
     *
     * @return el mensaje a enviar (RESPUESTA_RESERVA o ACTUALIZACION_STOCK), o null si no hay que enviar nada.
     */
    public Mensaje procesar(Mensaje mensaje) {
        if (mensaje == null) {
            registrar("mensaje nulo", null);
            return null;
        }
        if (mensaje.getTipo() == null) {
            registrar("mensaje sin tipo (o con un tipo desconocido)", mensaje);
            return null;
        }
        if (vacio(mensaje.getSedeOrigen())) {
            registrar("mensaje sin sede de origen", mensaje);
            return null;
        }
        switch (mensaje.getTipo()) {
            case SOLICITAR_RESERVA:
                return procesarSolicitud(mensaje);
            case CONFIRMAR_RESERVA:
                return procesarConfirmacion(mensaje);
            case LIBERAR_RESERVA:
                procesarLiberacion(mensaje);
                return null;
            case ACTUALIZACION_STOCK:
                procesarActualizacion(mensaje);
                return null;
            default:
                // RESPUESTA_RESERVA no llega por aquí: va directo a RespuestasPendientes
                registrar("una respuesta no se procesa aquí", mensaje);
                return null;
        }
    }

    // ------------------------------------------------------------------
    // Por tipo
    // ------------------------------------------------------------------

    private Mensaje procesarSolicitud(Mensaje m) {
        if (!esParaMi(m) || faltanDatosDePedido(m)) {
            return null;
        }
        if (m.getCantidad() == null || m.getCantidad() <= 0) {
            registrar("solicitud con cantidad inválida", m);
            return null;
        }
        RespuestaReserva r;
        try {
            r = inventario.reservar(m.getIdPedido(), m.getCodigoProducto(), m.getCantidad());
        } catch (RuntimeException e) {
            return Mensaje.respuestaReserva(m, codigoSede, false, "error al reservar: " + e.getMessage());
        }
        if (r == null) {
            return Mensaje.respuestaReserva(m, codigoSede, false, "el inventario no dio respuesta");
        }
        return Mensaje.respuestaReserva(m, codigoSede, r.isExito(), r.getMotivo());
    }

    private Mensaje procesarConfirmacion(Mensaje m) {
        if (!esParaMi(m) || faltanDatosDePedido(m)) {
            return null;
        }
        try {
            int stockNuevo = inventario.confirmar(m.getIdPedido(), m.getCodigoProducto());
            return Mensaje.actualizacionStock(codigoSede, m.getCodigoProducto(), stockNuevo);
        } catch (RuntimeException e) {
            registrar("error al confirmar: " + e.getMessage(), m);
            return null;
        }
    }

    private void procesarLiberacion(Mensaje m) {
        if (!esParaMi(m) || faltanDatosDePedido(m)) {
            return;
        }
        try {
            inventario.liberar(m.getIdPedido(), m.getCodigoProducto());
        } catch (RuntimeException e) {
            registrar("error al liberar: " + e.getMessage(), m);
        }
    }

    private void procesarActualizacion(Mensaje m) {
        if (m.getSedeOrigen().equals(codigoSede)) {
            return; // es mi propia actualización (el fanout también me la entrega): ya la conozco
        }
        if (vacio(m.getCodigoProducto()) || m.getCantidad() == null || m.getCantidad() < 0) {
            registrar("actualización de stock con datos faltantes", m);
            return;
        }
        try {
            catalogo.actualizarStock(m.getSedeOrigen(), m.getCodigoProducto(), m.getCantidad());
        } catch (RuntimeException e) {
            registrar("error al actualizar el catálogo: " + e.getMessage(), m);
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private boolean esParaMi(Mensaje m) {
        if (!codigoSede.equals(m.getSedeDestino())) {
            registrar("el mensaje es para la sede " + m.getSedeDestino() + ", no para " + codigoSede, m);
            return false;
        }
        return true;
    }

    private boolean faltanDatosDePedido(Mensaje m) {
        if (vacio(m.getIdPedido()) || vacio(m.getCodigoProducto())) {
            registrar("faltan idPedido o codigoProducto", m);
            return true;
        }
        return false;
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private void registrar(String razon, Mensaje m) {
        System.err.println("[ConviTEC " + codigoSede + "] Mensaje ignorado: " + razon
                + (m == null ? "" : " -> " + m.aJson()));
    }
}
