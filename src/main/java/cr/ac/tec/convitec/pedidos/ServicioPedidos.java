package cr.ac.tec.convitec.pedidos;

import cr.ac.tec.convitec.catalogo.CatalogoGlobal;
import cr.ac.tec.convitec.catalogo.DisponibilidadSede;
import cr.ac.tec.convitec.catalogo.InventarioLocal;
import cr.ac.tec.convitec.mensajeria.RespuestaReserva;
import cr.ac.tec.convitec.modelo.DetallePedido;
import cr.ac.tec.convitec.modelo.DetalleVenta;
import cr.ac.tec.convitec.modelo.EstadoPedido;
import cr.ac.tec.convitec.modelo.Pedido;
import cr.ac.tec.convitec.modelo.Producto;
import cr.ac.tec.convitec.rutas.SalidasDiarias;
import cr.ac.tec.convitec.rutas.SolicitudEntrega;
import cr.ac.tec.convitec.servicios.IGestorPedidos;
import cr.ac.tec.convitec.servicios.IMensajeria;
import cr.ac.tec.convitec.servicios.IServicioRutas;

import java.time.LocalDateTime;
import java.time.LocalTime;

/** Orquesta la reserva distribuida, validación de ruta y confirmación de pedidos. */
public class ServicioPedidos implements IGestorPedidos {

    private final String nombreSede;
    private final String codigoSede;
    private final InventarioLocal inventario;
    private final CatalogoGlobal catalogo;
    private final IMensajeria mensajeria;
    private final IServicioRutas servicioRutas;

    private NodoPendiente primeroPendiente;
    private Pedido[] historial = new Pedido[0];
    private int contadorIds;

    /** Crea el servicio para una sede con sus dependencias locales y de red. */
    public ServicioPedidos(String nombreSede, String codigoSede, InventarioLocal inventario,
                           CatalogoGlobal catalogo, IMensajeria mensajeria,
                           IServicioRutas servicioRutas) {
        this.nombreSede = validarTexto(nombreSede, "El nombre de la sede");
        this.codigoSede = normalizarCodigo(validarTexto(codigoSede, "El código de la sede"));
        if (inventario == null || catalogo == null || mensajeria == null || servicioRutas == null) {
            throw new IllegalArgumentException("Inventario, catálogo, mensajería y rutas son obligatorios");
        }
        this.inventario = inventario;
        this.catalogo = catalogo;
        this.mensajeria = mensajeria;
        this.servicioRutas = servicioRutas;
    }

    /** Registra y confirma un pedido, o lo cancela liberando todas sus reservas. */
    public synchronized ResultadoPedido registrarPedido(String destino, SolicitudLinea[] lineas) {
        String id = generarId();
        LocalDateTime fecha = LocalDateTime.now();
        String destinoLimpio = destino == null ? "" : destino.trim();

        if (destinoLimpio.isEmpty()) {
            return registrarCancelado(id, destinoLimpio, fecha, "El destino no puede estar vacío");
        }
        if (lineas == null || lineas.length == 0) {
            return registrarCancelado(id, destinoLimpio, fecha, "El pedido no contiene productos");
        }

        LineaAgrupada[] agrupadas = agruparLineas(lineas);
        if (agrupadas == null) {
            return registrarCancelado(id, destinoLimpio, fecha, motivoValidacion);
        }

        Reservacion[] reservasHechas = new Reservacion[agrupadas.length * 5];
        int cantidadReservas = 0;
        DetallePedido[] detalles = new DetallePedido[agrupadas.length * 5];
        int cantidadDetalles = 0;
        String motivoFallo = null;

        for (LineaAgrupada linea : agrupadas) {
            Producto producto = linea.producto;
            int restante = linea.cantidad;
            int disponibleLocal = inventario.obtenerDisponible(producto.getCodigo());
            if (disponibleLocal < 0) {
                disponibleLocal = 0;
            }
            int tomarLocal = Math.min(restante, disponibleLocal);
            if (tomarLocal > 0) {
                RespuestaReserva respuesta = inventario.reservar(id, producto.getCodigo(), tomarLocal);
                if (respuesta == null || !respuesta.isExito()) {
                    motivoFallo = respuesta == null ? "El inventario local no respondió"
                            : respuesta.getMotivo();
                    break;
                }
                reservasHechas[cantidadReservas++] = new Reservacion(codigoSede, producto.getCodigo());
                detalles[cantidadDetalles++] = crearDetalle(producto, tomarLocal, codigoSede);
                restante -= tomarLocal;
            }

            if (restante > 0) {
                Distribucion distribucion = reservarEnOtrasSedes(id, producto, restante,
                        reservasHechas, cantidadReservas, detalles, cantidadDetalles);
                cantidadReservas = distribucion.cantidadReservas;
                cantidadDetalles = distribucion.cantidadDetalles;
                motivoFallo = distribucion.motivo;
                if (motivoFallo != null) {
                    break;
                }
            }
        }

        if (motivoFallo == null) {
            try {
                if (!servicioRutas.existeRuta(nombreSede, destinoLimpio)) {
                    motivoFallo = "No existe una ruta desde " + nombreSede + " hasta " + destinoLimpio;
                }
            } catch (RuntimeException error) {
                motivoFallo = "El destino '" + destinoLimpio + "' no existe o no se pudo validar la ruta";
            }
        }

        if (motivoFallo != null) {
            String fallosLiberacion = liberarReservas(id, reservasHechas, cantidadReservas);
            if (!fallosLiberacion.isEmpty()) {
                motivoFallo += "; " + fallosLiberacion;
            }
            return registrarCancelado(id, destinoLimpio, fecha, motivoFallo);
        }

        String[] participantes = sedesParticipantes(detalles, cantidadDetalles);
        Pedido pedido = new Pedido(id, codigoSede, destinoLimpio, fecha, EstadoPedido.EN_PROCESO,
                null, copiarDetalles(detalles, cantidadDetalles), participantes);

        String advertenciaPublicacion = confirmarReservas(pedido, detalles, cantidadDetalles);
        pedido.confirmar();
        agregarHistorial(pedido);
        agregarPendiente(pedido);
        return new ResultadoPedido(true, pedido,
                advertenciaPublicacion.isEmpty() ? null : advertenciaPublicacion);
    }

    /** Devuelve una copia de los pedidos confirmados pendientes de entrega. */
    public synchronized Pedido[] listarPendientes() {
        Pedido[] pendientes = new Pedido[contarPendientes()];
        int indice = 0;
        for (NodoPendiente nodo = primeroPendiente; nodo != null; nodo = nodo.siguiente) {
            pendientes[indice++] = nodo.pedido.copia();
        }
        return pendientes;
    }

    /** Devuelve copias del historial ordenadas por identificador. */
    public synchronized Pedido[] listarHistorial() {
        return copiarPedidos(historial);
    }

    /** Busca un pedido y devuelve una copia, o null si no existe. */
    public synchronized Pedido buscarPorId(String idPedido) {
        if (idPedido == null) {
            return null;
        }
        int indice = buscarIndiceHistorial(idPedido.trim());
        return indice < 0 ? null : historial[indice].copia();
    }

    /** Crea las solicitudes del planificador a partir de los pedidos pendientes. */
    public synchronized SolicitudEntrega[] construirSolicitudesEntrega() {
        Pedido[] pendientes = listarPendientes();
        SolicitudEntrega[] solicitudes = new SolicitudEntrega[pendientes.length];
        for (int i = 0; i < pendientes.length; i++) {
            solicitudes[i] = new SolicitudEntrega(pendientes[i].getId(), pendientes[i].getDestino());
        }
        return solicitudes;
    }

    /** Devuelve la salida diaria aplicable a la hora de registro. */
    public synchronized SalidasDiarias.Asignacion salidaParaRegistro(LocalTime horaRegistro) {
        return SalidasDiarias.salidaParaRegistro(horaRegistro);
    }

    /** Devuelve el detalle de venta de un pedido o null si no existe o fue cancelado. */
    @Override
    public synchronized DetalleVenta[] obtenerDetalle(String idPedido) {
        Pedido pedido = buscarPedidoInterno(idPedido);
        if (pedido == null) {
            return null;
        }
        DetallePedido[] detalles = pedido.getDetalles();
        DetalleVenta[] resultado = new DetalleVenta[detalles.length];
        for (int i = 0; i < detalles.length; i++) {
            resultado[i] = new DetalleVenta(detalles[i].getCodigoProducto(), detalles[i].getNombre(),
                    detalles[i].getCantidad(), detalles[i].getPrecioUnitario());
        }
        return resultado;
    }

    /** Marca un pedido confirmado como entregado y lo quita de pendientes; es idempotente. */
    @Override
    public synchronized void marcarEntregado(String idPedido, LocalDateTime fechaHora) {
        if (idPedido == null || fechaHora == null) {
            return;
        }
        Pedido pedido = buscarPedidoInterno(idPedido.trim());
        if (pedido == null || pedido.getEstado() == EstadoPedido.ENTREGADO) {
            return;
        }
        pedido.marcarEntregado(fechaHora);
        if (pedido.getEstado() == EstadoPedido.ENTREGADO) {
            quitarPendiente(idPedido.trim());
        }
    }

    /** Devuelve copias de todos los pedidos del historial. */
    public synchronized Pedido[] exportarPedidos() {
        return copiarPedidos(historial);
    }

    /** Carga pedidos y determina el consecutivo mayor encontrado. */
    public synchronized void cargarPedidos(Pedido[] pedidos) {
        cargarPedidos(pedidos, consecutivoMaximo(pedidos));
    }

    /** Carga pedidos y restaura explícitamente el consecutivo de ids. */
    public synchronized void cargarPedidos(Pedido[] pedidos, int consecutivo) {
        if (pedidos == null || consecutivo < 0) {
            throw new IllegalArgumentException("Los pedidos y un consecutivo no negativo son requeridos");
        }
        Pedido[] cargados = new Pedido[pedidos.length];
        for (int i = 0; i < pedidos.length; i++) {
            if (pedidos[i] == null) {
                throw new IllegalArgumentException("El arreglo contiene un pedido null");
            }
            if (buscarEnArreglo(cargados, i, pedidos[i].getId()) >= 0) {
                throw new IllegalArgumentException("El id " + pedidos[i].getId() + " está repetido");
            }
            cargados[i] = pedidos[i].copia();
        }
        ordenarPorId(cargados);

        historial = cargados;
        primeroPendiente = null;
        contadorIds = Math.max(consecutivo, consecutivoMaximo(pedidos));
        for (Pedido pedido : historial) {
            if (pedido.getEstado() == EstadoPedido.CONFIRMADO) {
                agregarPendiente(pedido);
            }
        }
    }

    /** Devuelve el consecutivo actual para persistir la secuencia de identificadores. */
    public synchronized int getContadorIds() {
        return contadorIds;
    }

    private String motivoValidacion;

    private LineaAgrupada[] agruparLineas(SolicitudLinea[] lineas) {
        LineaAgrupada[] agrupadas = new LineaAgrupada[lineas.length];
        int cantidadAgrupada = 0;
        for (int i = 0; i < lineas.length; i++) {
            SolicitudLinea solicitud = lineas[i];
            if (solicitud == null || solicitud.getCodigoProducto() == null
                    || solicitud.getCodigoProducto().trim().isEmpty()) {
                motivoValidacion = "El código del producto no puede estar vacío";
                return null;
            }
            if (solicitud.getCantidad() <= 0) {
                motivoValidacion = "La cantidad debe ser mayor que cero";
                return null;
            }
            String codigo = normalizarCodigo(solicitud.getCodigoProducto());
            Producto producto = catalogo.obtenerProducto(codigo);
            if (producto == null) {
                motivoValidacion = "El producto " + codigo + " no existe en el catálogo";
                return null;
            }
            int indice = buscarLinea(agrupadas, cantidadAgrupada, codigo);
            if (indice < 0) {
                agrupadas[cantidadAgrupada++] = new LineaAgrupada(producto, solicitud.getCantidad());
            } else {
                long suma = (long) agrupadas[indice].cantidad + solicitud.getCantidad();
                if (suma > Integer.MAX_VALUE) {
                    motivoValidacion = "La cantidad total del producto " + codigo + " excede el máximo permitido";
                    return null;
                }
                agrupadas[indice].cantidad = (int) suma;
            }
        }
        LineaAgrupada[] resultado = new LineaAgrupada[cantidadAgrupada];
        System.arraycopy(agrupadas, 0, resultado, 0, cantidadAgrupada);
        motivoValidacion = null;
        return resultado;
    }

    private Distribucion reservarEnOtrasSedes(String id, Producto producto, int cantidadNecesaria,
                                               Reservacion[] reservasHechas, int cantidadReservas,
                                               DetallePedido[] detalles, int cantidadDetalles) {
        DisponibilidadSede[] disponibilidades = catalogo.disponibilidadDe(producto.getCodigo());
        boolean[] consideradas = new boolean[disponibilidades.length];
        int restante = cantidadNecesaria;

        while (restante > 0) {
            int mejor = -1;
            for (int i = 0; i < disponibilidades.length; i++) {
                if (consideradas[i]
                        || codigoSede.equalsIgnoreCase(disponibilidades[i].getCodigoSede())
                        || disponibilidades[i].getCantidadDisponible() <= 0) {
                    continue;
                }
                if (mejor < 0 || disponibilidades[i].getCantidadDisponible()
                        > disponibilidades[mejor].getCantidadDisponible()) {
                    mejor = i;
                }
            }
            if (mejor < 0) {
                return new Distribucion(cantidadReservas, cantidadDetalles,
                        "Stock insuficiente conocido para " + producto.getCodigo());
            }

            consideradas[mejor] = true;
            DisponibilidadSede sede = disponibilidades[mejor];
            int porSolicitar = Math.min(restante, sede.getCantidadDisponible());
            reservasHechas[cantidadReservas++] = new Reservacion(sede.getCodigoSede(), producto.getCodigo());
            RespuestaReserva respuesta;
            try {
                respuesta = mensajeria.solicitarReserva(sede.getCodigoSede(), id,
                        producto.getCodigo(), porSolicitar);
            } catch (RuntimeException error) {
                return new Distribucion(cantidadReservas, cantidadDetalles,
                        "La sede " + sede.getCodigoSede() + " no respondió al reservar "
                                + producto.getCodigo());
            }
            if (respuesta == null || !respuesta.isExito()) {
                String motivo = respuesta == null ? null : respuesta.getMotivo();
                return new Distribucion(cantidadReservas, cantidadDetalles,
                        motivo == null || motivo.trim().isEmpty()
                                ? "La sede " + sede.getCodigoSede() + " rechazó la reserva de "
                                + producto.getCodigo()
                                : motivo);
            }
            detalles[cantidadDetalles++] = crearDetalle(producto, porSolicitar, sede.getCodigoSede());
            restante -= porSolicitar;
        }
        return new Distribucion(cantidadReservas, cantidadDetalles, null);
    }

    private String confirmarReservas(Pedido pedido, DetallePedido[] detalles, int cantidadDetalles) {
        StringBuilder advertencias = new StringBuilder();
        for (int i = 0; i < cantidadDetalles; i++) {
            DetallePedido detalle = detalles[i];
            if (codigoSede.equalsIgnoreCase(detalle.getSedeSurtidora())) {
                int stock = inventario.confirmar(pedido.getId(), detalle.getCodigoProducto());
                try {
                    mensajeria.publicarActualizacionStock(detalle.getCodigoProducto(), stock);
                } catch (RuntimeException error) {
                    anexarAdvertencia(advertencias, "No se pudo publicar el stock de "
                            + detalle.getCodigoProducto());
                }
            }
        }
        for (int i = 0; i < cantidadDetalles; i++) {
            DetallePedido detalle = detalles[i];
            if (!codigoSede.equalsIgnoreCase(detalle.getSedeSurtidora())) {
                try {
                    mensajeria.confirmarReserva(detalle.getSedeSurtidora(),
                            pedido.getId(), detalle.getCodigoProducto());
                } catch (RuntimeException error) {
                    anexarAdvertencia(advertencias, "No se pudo confirmar la reserva remota en "
                            + detalle.getSedeSurtidora());
                }
            }
        }
        return advertencias.toString();
    }

    private String liberarReservas(String id, Reservacion[] hechas, int cantidad) {
        StringBuilder fallos = new StringBuilder();
        for (int i = cantidad - 1; i >= 0; i--) {
            Reservacion reserva = hechas[i];
            try {
                if (codigoSede.equalsIgnoreCase(reserva.sede)) {
                    inventario.liberar(id, reserva.codigo);
                } else {
                    mensajeria.liberarReserva(reserva.sede, id, reserva.codigo);
                }
            } catch (RuntimeException error) {
                anexarAdvertencia(fallos, "no se pudo liberar " + reserva.codigo + " en "
                        + reserva.sede);
            }
        }
        return fallos.toString();
    }

    private ResultadoPedido registrarCancelado(String id, String destino, LocalDateTime fecha, String motivo) {
        String destinoGuardado = destino.isEmpty() ? "Destino inválido" : destino;
        Pedido pedido = new Pedido(id, codigoSede, destinoGuardado, fecha, EstadoPedido.EN_PROCESO,
                null, new DetallePedido[0], new String[]{codigoSede});
        pedido.cancelar(motivo == null || motivo.trim().isEmpty() ? "Pedido cancelado" : motivo);
        agregarHistorial(pedido);
        return new ResultadoPedido(false, pedido, pedido.getMotivoCancelacion());
    }

    private DetallePedido crearDetalle(Producto producto, int cantidad, String sede) {
        return new DetallePedido(producto.getCodigo(), producto.getNombre(), cantidad,
                producto.getPrecio(), sede);
    }

    private String[] sedesParticipantes(DetallePedido[] detalles, int cantidad) {
        String[] sedes = new String[cantidad + 1];
        int cantidadSedes = 0;
        sedes[cantidadSedes++] = codigoSede;
        for (int i = 0; i < cantidad; i++) {
            String sede = detalles[i].getSedeSurtidora();
            if (!contiene(sedes, cantidadSedes, sede)) {
                sedes[cantidadSedes++] = sede;
            }
        }
        String[] resultado = new String[cantidadSedes];
        System.arraycopy(sedes, 0, resultado, 0, cantidadSedes);
        return resultado;
    }

    private boolean contiene(String[] arreglo, int cantidad, String valor) {
        for (int i = 0; i < cantidad; i++) {
            if (arreglo[i].equalsIgnoreCase(valor)) {
                return true;
            }
        }
        return false;
    }

    private DetallePedido[] copiarDetalles(DetallePedido[] origen, int cantidad) {
        DetallePedido[] copia = new DetallePedido[cantidad];
        System.arraycopy(origen, 0, copia, 0, cantidad);
        return copia;
    }

    private void agregarHistorial(Pedido pedido) {
        Pedido[] nuevo = new Pedido[historial.length + 1];
        int indice = historial.length;
        while (indice > 0 && historial[indice - 1].getId().compareTo(pedido.getId()) > 0) {
            nuevo[indice] = historial[indice - 1];
            indice--;
        }
        nuevo[indice] = pedido;
        if (indice > 0) {
            System.arraycopy(historial, 0, nuevo, 0, indice);
        }
        historial = nuevo;
    }

    private void agregarPendiente(Pedido pedido) {
        NodoPendiente nuevo = new NodoPendiente(pedido);
        if (primeroPendiente == null) {
            primeroPendiente = nuevo;
            return;
        }
        NodoPendiente actual = primeroPendiente;
        while (actual.siguiente != null) {
            actual = actual.siguiente;
        }
        actual.siguiente = nuevo;
    }

    private void quitarPendiente(String id) {
        NodoPendiente anterior = null;
        NodoPendiente actual = primeroPendiente;
        while (actual != null) {
            if (actual.pedido.getId().equals(id)) {
                if (anterior == null) {
                    primeroPendiente = actual.siguiente;
                } else {
                    anterior.siguiente = actual.siguiente;
                }
                return;
            }
            anterior = actual;
            actual = actual.siguiente;
        }
    }

    private int contarPendientes() {
        int cantidad = 0;
        for (NodoPendiente actual = primeroPendiente; actual != null; actual = actual.siguiente) {
            cantidad++;
        }
        return cantidad;
    }

    private Pedido buscarPedidoInterno(String id) {
        if (id == null) {
            return null;
        }
        int indice = buscarIndiceHistorial(id.trim());
        return indice < 0 ? null : historial[indice];
    }

    private int buscarIndiceHistorial(String id) {
        for (int i = 0; i < historial.length; i++) {
            if (historial[i].getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private Pedido[] copiarPedidos(Pedido[] pedidos) {
        Pedido[] copias = new Pedido[pedidos.length];
        for (int i = 0; i < pedidos.length; i++) {
            copias[i] = pedidos[i].copia();
        }
        return copias;
    }

    private void ordenarPorId(Pedido[] pedidos) {
        for (int i = 1; i < pedidos.length; i++) {
            Pedido actual = pedidos[i];
            int j = i;
            while (j > 0 && pedidos[j - 1].getId().compareTo(actual.getId()) > 0) {
                pedidos[j] = pedidos[j - 1];
                j--;
            }
            pedidos[j] = actual;
        }
    }

    private int buscarEnArreglo(Pedido[] pedidos, int cantidad, String id) {
        for (int i = 0; i < cantidad; i++) {
            if (pedidos[i].getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private int consecutivoMaximo(Pedido[] pedidos) {
        int maximo = 0;
        for (Pedido pedido : pedidos) {
            if (pedido != null) {
                String prefijo = codigoSede + "-";
                String id = pedido.getId();
                if (id.startsWith(prefijo)) {
                    try {
                        int numero = Integer.parseInt(id.substring(prefijo.length()));
                        if (numero > maximo) {
                            maximo = numero;
                        }
                    } catch (NumberFormatException ignored) {
                        // Los ids ajenos al formato de esta sede no alteran su consecutivo.
                    }
                }
            }
        }
        return maximo;
    }

    private String generarId() {
        contadorIds++;
        String numero = Integer.toString(contadorIds);
        StringBuilder id = new StringBuilder(codigoSede).append('-');
        for (int i = numero.length(); i < 4; i++) {
            id.append('0');
        }
        return id.append(numero).toString();
    }

    private int buscarLinea(LineaAgrupada[] lineas, int cantidad, String codigo) {
        for (int i = 0; i < cantidad; i++) {
            if (lineas[i].producto.getCodigo().equals(codigo)) {
                return i;
            }
        }
        return -1;
    }

    private String normalizarCodigo(String texto) {
        String recortado = texto.trim();
        StringBuilder resultado = new StringBuilder(recortado.length());
        for (int i = 0; i < recortado.length(); i++) {
            resultado.append(Character.toUpperCase(recortado.charAt(i)));
        }
        return resultado.toString();
    }

    private String validarTexto(String texto, String campo) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new IllegalArgumentException(campo + " no puede ser null ni vacío");
        }
        return texto.trim();
    }

    private void anexarAdvertencia(StringBuilder advertencias, String texto) {
        if (advertencias.length() > 0) {
            advertencias.append("; ");
        }
        advertencias.append(texto);
    }

    private static final class LineaAgrupada {
        private final Producto producto;
        private int cantidad;

        private LineaAgrupada(Producto producto, int cantidad) {
            this.producto = producto;
            this.cantidad = cantidad;
        }
    }

    private static final class Reservacion {
        private final String sede;
        private final String codigo;

        private Reservacion(String sede, String codigo) {
            this.sede = sede;
            this.codigo = codigo;
        }
    }

    private static final class Distribucion {
        private final int cantidadReservas;
        private final int cantidadDetalles;
        private final String motivo;

        private Distribucion(int cantidadReservas, int cantidadDetalles, String motivo) {
            this.cantidadReservas = cantidadReservas;
            this.cantidadDetalles = cantidadDetalles;
            this.motivo = motivo;
        }
    }

    private static final class NodoPendiente {
        private final Pedido pedido;
        private NodoPendiente siguiente;

        private NodoPendiente(Pedido pedido) {
            this.pedido = pedido;
        }
    }
}
