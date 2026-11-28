package cr.ac.tec.convitec.web;

import cr.ac.tec.convitec.config.SedesConocidas;
import cr.ac.tec.convitec.grafo.Grafo;
import cr.ac.tec.convitec.modelo.DetallePedido;
import cr.ac.tec.convitec.modelo.EstadoPedido;
import cr.ac.tec.convitec.modelo.Pedido;
import cr.ac.tec.convitec.pedidos.ResultadoPedido;
import cr.ac.tec.convitec.pedidos.ServicioPedidos;
import cr.ac.tec.convitec.pedidos.SolicitudLinea;
import cr.ac.tec.convitec.rutas.SalidasDiarias;

/**
 * Lógica de las pantallas Nuevo pedido y Pedidos (POST /api/pedidos, GET /api/pedidos).
 *
 * Registrar un pedido lo hace ServicioPedidos (Persona 2): reserva en esta y otras sedes, valida la ruta
 * y confirma o cancela todo. Aquí solo se validan antes los datos del formulario, para que un error de
 * digitación (cantidad 0, sin productos, destino que no existe) se avise en la pantalla y no quede
 * registrado como un pedido cancelado en el historial.
 */
final class ControladorPedidos {

    /** Tope por línea, para evitar errores de digitación. */
    static final int MAXIMO_POR_PRODUCTO = 1_000;
    /** Tope de líneas por pedido. */
    static final int MAXIMO_LINEAS = 50;

    private final ServicioPedidos pedidos;
    private final Grafo grafo;

    ControladorPedidos(ServicioPedidos pedidos, Grafo grafo) {
        this.pedidos = pedidos;
        this.grafo = grafo;
    }

    /**
     * Registra un pedido. Puede tardar varios segundos si necesita productos de otras sedes
     * (espera la respuesta de cada una).
     *
     * @return aceptado o rechazado, con el motivo y el pedido tal como quedó.
     * @throws IllegalArgumentException si los datos del formulario no son válidos.
     */
    ResultadoPedidoDTO registrar(SolicitudPedido solicitud) {
        if (solicitud == null) {
            throw new IllegalArgumentException("La solicitud del pedido está vacía.");
        }
        if (solicitud.destino == null || solicitud.destino.isBlank()) {
            throw new IllegalArgumentException("Elija el lugar de entrega del pedido.");
        }
        if (!grafo.existeVertice(solicitud.destino)) {
            throw new IllegalArgumentException("El lugar de entrega '" + solicitud.destino.trim()
                    + "' no existe en el mapa de rutas.");
        }
        if (solicitud.lineas == null || solicitud.lineas.length == 0) {
            throw new IllegalArgumentException("Agregue al menos un producto al pedido.");
        }
        if (solicitud.lineas.length > MAXIMO_LINEAS) {
            throw new IllegalArgumentException("Un pedido puede tener como máximo " + MAXIMO_LINEAS + " productos.");
        }
        SolicitudLinea[] lineas = new SolicitudLinea[solicitud.lineas.length];
        for (int i = 0; i < lineas.length; i++) {
            LineaPedido linea = solicitud.lineas[i];
            if (linea == null || linea.codigoProducto == null || linea.codigoProducto.isBlank()) {
                throw new IllegalArgumentException("El producto #" + (i + 1) + " no tiene código.");
            }
            if (linea.cantidad == null || linea.cantidad <= 0) {
                throw new IllegalArgumentException("La cantidad de " + linea.codigoProducto.trim()
                        + " debe ser un número entero mayor que 0.");
            }
            if (linea.cantidad > MAXIMO_POR_PRODUCTO) {
                throw new IllegalArgumentException("Se pueden pedir como máximo " + MAXIMO_POR_PRODUCTO
                        + " unidades de un mismo producto.");
            }
            lineas[i] = new SolicitudLinea(linea.codigoProducto.trim(), linea.cantidad);
        }

        // El nombre oficial del lugar ("San José" aunque se escriba "san jose")
        String destino = grafo.getNombre(grafo.indiceDe(solicitud.destino));
        ResultadoPedido resultado = pedidos.registrarPedido(destino, lineas);
        return new ResultadoPedidoDTO(resultado.isAceptado(),
                resultado.isAceptado() ? null : resultado.getMotivo(),
                resultado.isAceptado() ? resultado.getMotivo() : null,
                PedidoDTO.de(resultado.getPedido()));
    }

    /**
     * Pedidos de la sede.
     *
     * @param filtro "pendientes" (confirmados sin entregar, en orden de llegada), "todos" o null (historial,
     *               del más reciente al más antiguo), o un estado: EN_PROCESO, CONFIRMADO, CANCELADO, ENTREGADO.
     * @throws IllegalArgumentException si el filtro no existe.
     */
    PedidoDTO[] listar(String filtro) {
        if (filtro != null && filtro.trim().equalsIgnoreCase("pendientes")) {
            return convertir(pedidos.listarPendientes(), false, null);
        }
        Pedido[] historial = pedidos.listarHistorial();
        if (filtro == null || filtro.isBlank() || filtro.trim().equalsIgnoreCase("todos")) {
            return convertir(historial, true, null);
        }
        EstadoPedido estado;
        try {
            estado = EstadoPedido.valueOf(filtro.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Filtro de pedidos desconocido: '" + filtro.trim()
                    + "'. Use pendientes, todos, EN_PROCESO, CONFIRMADO, CANCELADO o ENTREGADO.");
        }
        return convertir(historial, true, estado);
    }

    /**
     * Convierte a DTO. El historial viene en orden de id (el más antiguo primero): si "invertir" es true se
     * recorre de atrás hacia adelante, para mostrar primero el más reciente (sin ordenar de nuevo).
     */
    private static PedidoDTO[] convertir(Pedido[] lista, boolean invertir, EstadoPedido soloEstado) {
        int cantidad = 0;
        for (int i = 0; i < lista.length; i++) {
            if (soloEstado == null || lista[i].getEstado() == soloEstado) {
                cantidad++;
            }
        }
        PedidoDTO[] resultado = new PedidoDTO[cantidad];
        int pos = 0;
        for (int k = 0; k < lista.length; k++) {
            Pedido p = lista[invertir ? lista.length - 1 - k : k];
            if (soloEstado == null || p.getEstado() == soloEstado) {
                resultado[pos++] = PedidoDTO.de(p);
            }
        }
        return resultado;
    }

    // ------------------------------------------------------------------
    // Formas JSON
    // ------------------------------------------------------------------

    /** Cuerpo de POST /api/pedidos: { "destino": "Heredia", "lineas": [ { "codigoProducto": "C01", "cantidad": 2 } ] } */
    static final class SolicitudPedido {
        String destino;
        LineaPedido[] lineas;
    }

    static final class LineaPedido {
        String codigoProducto;
        Integer cantidad;
    }

    static final class ResultadoPedidoDTO {
        final boolean aceptado;
        final String motivo;   // por qué se rechazó (null si se aceptó)
        final String aviso;    // advertencia de un pedido aceptado (por ejemplo, no se pudo avisar a otra sede)
        final PedidoDTO pedido;

        ResultadoPedidoDTO(boolean aceptado, String motivo, String aviso, PedidoDTO pedido) {
            this.aceptado = aceptado;
            this.motivo = motivo;
            this.aviso = aviso;
            this.pedido = pedido;
        }
    }

    static final class PedidoDTO {
        final String id;
        final String destino;
        final String fechaCreacion;   // ISO: "2026-11-27T10:15:30"
        final String estado;          // EN_PROCESO, CONFIRMADO, CANCELADO, ENTREGADO
        final String motivoCancelacion;
        final String fechaEntrega;    // null si no se ha entregado
        final double montoTotal;
        final SalidaDTO salida;       // salida que le toca según la hora en que se registró
        final SedeDTO[] sedesParticipantes;
        final DetalleDTO[] detalles;

        private PedidoDTO(String id, String destino, String fechaCreacion, String estado, String motivoCancelacion,
                          String fechaEntrega, double montoTotal, SalidaDTO salida, SedeDTO[] sedesParticipantes,
                          DetalleDTO[] detalles) {
            this.id = id;
            this.destino = destino;
            this.fechaCreacion = fechaCreacion;
            this.estado = estado;
            this.motivoCancelacion = motivoCancelacion;
            this.fechaEntrega = fechaEntrega;
            this.montoTotal = montoTotal;
            this.salida = salida;
            this.sedesParticipantes = sedesParticipantes;
            this.detalles = detalles;
        }

        static PedidoDTO de(Pedido p) {
            DetallePedido[] detalles = p.getDetalles();
            DetalleDTO[] lineas = new DetalleDTO[detalles.length];
            for (int i = 0; i < detalles.length; i++) {
                DetallePedido d = detalles[i];
                lineas[i] = new DetalleDTO(d.getCodigoProducto(), d.getNombre(), d.getCantidad(),
                        d.getPrecioUnitario(), d.getSubtotal(), d.getSedeSurtidora(),
                        SedesConocidas.nombreDe(d.getSedeSurtidora()));
            }
            String[] codigos = p.getSedesParticipantes();
            SedeDTO[] sedes = new SedeDTO[codigos.length];
            for (int i = 0; i < codigos.length; i++) {
                sedes[i] = new SedeDTO(codigos[i], SedesConocidas.nombreDe(codigos[i]));
            }
            SalidasDiarias.Asignacion salida = SalidasDiarias.salidaParaRegistro(p.getFechaCreacion().toLocalTime());
            return new PedidoDTO(p.getId(), p.getDestino(), p.getFechaCreacion().toString(), p.getEstado().name(),
                    p.getMotivoCancelacion(), p.getFechaEntrega() == null ? null : p.getFechaEntrega().toString(),
                    p.getMontoTotal(), new SalidaDTO(salida.getHoraSalida(), salida.esDiaSiguiente()), sedes, lineas);
        }
    }

    static final class SalidaDTO {
        final String hora;
        final boolean diaSiguiente;

        SalidaDTO(String hora, boolean diaSiguiente) {
            this.hora = hora;
            this.diaSiguiente = diaSiguiente;
        }
    }

    static final class SedeDTO {
        final String codigo;
        final String nombre;

        SedeDTO(String codigo, String nombre) {
            this.codigo = codigo;
            this.nombre = nombre;
        }
    }

    static final class DetalleDTO {
        final String codigoProducto;
        final String nombre;
        final int cantidad;
        final double precioUnitario;
        final double subtotal;
        final String sedeSurtidora;
        final String nombreSedeSurtidora;

        DetalleDTO(String codigoProducto, String nombre, int cantidad, double precioUnitario, double subtotal,
                   String sedeSurtidora, String nombreSedeSurtidora) {
            this.codigoProducto = codigoProducto;
            this.nombre = nombre;
            this.cantidad = cantidad;
            this.precioUnitario = precioUnitario;
            this.subtotal = subtotal;
            this.sedeSurtidora = sedeSurtidora;
            this.nombreSedeSurtidora = nombreSedeSurtidora;
        }
    }
}
