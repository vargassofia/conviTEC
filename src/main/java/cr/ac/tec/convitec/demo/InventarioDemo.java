package cr.ac.tec.convitec.demo;

import cr.ac.tec.convitec.mensajeria.RespuestaReserva;
import cr.ac.tec.convitec.servicios.IInventarioLocal;

// TODO PERSONA 2: reemplazar esta clase de demo por su implementación real de IInventarioLocal
//   (inventario en archivos), cambiando la línea marcada en app/Sede.conDemostracion. Después borrar esta clase.
/**
 * *** PROVISIONAL / DEMOSTRACIÓN ***
 * Inventario de ejemplo para poder correr una sede antes de que exista el inventario real
 * de la Persona 2, que lo REEMPLAZARÁ (ver Sede.conDemostracion). No guarda nada en archivos.
 *
 * - stock = unidades DISPONIBLES (las reservadas ya no cuentan).
 * - reservar aparta unidades (salen del disponible); confirmar las da por vendidas;
 *   liberar las devuelve al disponible.
 * - Las reservas se identifican por idPedido + código de producto.
 * - confirmar y liberar son idempotentes. Todo es synchronized (los mensajes llegan en otro hilo).
 * - Solo usa arreglos nativos.
 */
public class InventarioDemo implements IInventarioLocal {

    private static final int CAPACIDAD_INICIAL = 8;

    private String[] codigos;
    private String[] nombres;
    private double[] precios;
    private int[] disponibles;
    private int cantidadProductos;

    private String[] reservaPedidos = new String[CAPACIDAD_INICIAL];
    private String[] reservaCodigos = new String[CAPACIDAD_INICIAL];
    private int[] reservaCantidades = new int[CAPACIDAD_INICIAL];
    private int cantidadReservas;

    /**
     * Inventario con los productos dados (arreglos paralelos del mismo tamaño).
     *
     * @throws IllegalArgumentException si los arreglos son nulos, de distinto tamaño, o tienen
     *                                  códigos vacíos o repetidos, precios o cantidades negativas.
     */
    public InventarioDemo(String[] codigos, String[] nombres, double[] precios, int[] cantidades) {
        if (codigos == null || nombres == null || precios == null || cantidades == null
                || codigos.length != nombres.length || codigos.length != precios.length
                || codigos.length != cantidades.length) {
            throw new IllegalArgumentException("Los arreglos de productos deben existir y tener el mismo tamaño.");
        }
        int n = codigos.length;
        this.codigos = new String[Math.max(n, CAPACIDAD_INICIAL)];
        this.nombres = new String[this.codigos.length];
        this.precios = new double[this.codigos.length];
        this.disponibles = new int[this.codigos.length];
        for (int i = 0; i < n; i++) {
            if (codigos[i] == null || codigos[i].isBlank()) {
                throw new IllegalArgumentException("Producto #" + (i + 1) + " sin código.");
            }
            if (buscarProducto(codigos[i]) != -1) {
                throw new IllegalArgumentException("El código " + codigos[i] + " está repetido.");
            }
            if (precios[i] < 0 || cantidades[i] < 0) {
                throw new IllegalArgumentException("El producto " + codigos[i] + " tiene precio o cantidad negativa.");
            }
            this.codigos[i] = codigos[i];
            this.nombres[i] = nombres[i];
            this.precios[i] = precios[i];
            this.disponibles[i] = cantidades[i];
            cantidadProductos++;
        }
    }

    /**
     * Productos de ejemplo según la sede. B01 "Bebida" solo existe en LIM, para poder
     * demostrar una reserva entre sedes desde cualquier otra.
     */
    public static InventarioDemo paraSede(String codigoSede) {
        if ("LIB".equals(codigoSede)) {
            return new InventarioDemo(new String[]{"C01", "G01", "A01"},
                    new String[]{"Café", "Galletas", "Agua"}, new double[]{1500, 800, 600}, new int[]{10, 20, 15});
        }
        if ("ALA".equals(codigoSede)) {
            return new InventarioDemo(new String[]{"C01", "P01", "A01"},
                    new String[]{"Café", "Pan", "Agua"}, new double[]{1500, 1000, 600}, new int[]{8, 12, 10});
        }
        if ("SJO".equals(codigoSede)) {
            return new InventarioDemo(new String[]{"C01", "G01", "S01"},
                    new String[]{"Café", "Galletas", "Sándwich"}, new double[]{1500, 800, 2500}, new int[]{12, 5, 6});
        }
        if ("LIM".equals(codigoSede)) {
            return new InventarioDemo(new String[]{"B01", "A01", "F01"},
                    new String[]{"Bebida", "Agua", "Fruta"}, new double[]{1000, 600, 700}, new int[]{5, 20, 10});
        }
        throw new IllegalArgumentException("No hay inventario de demostración para la sede '" + codigoSede + "'.");
    }

    // ------------------------------------------------------------------
    // IInventarioLocal
    // ------------------------------------------------------------------

    @Override
    public synchronized RespuestaReserva reservar(String idPedido, String codigoProducto, int cantidad) {
        if (idPedido == null || idPedido.isBlank() || codigoProducto == null || codigoProducto.isBlank()) {
            return RespuestaReserva.rechazo(null, "faltan idPedido o código de producto");
        }
        if (cantidad <= 0) {
            return RespuestaReserva.rechazo(null, "la cantidad debe ser mayor que 0 (llegó " + cantidad + ")");
        }
        int p = buscarProducto(codigoProducto);
        if (p == -1) {
            return RespuestaReserva.rechazo(null, "el producto " + codigoProducto + " no existe");
        }
        if (buscarReserva(idPedido, codigoProducto) != -1) {
            return RespuestaReserva.rechazo(null, "ya hay una reserva de " + codigoProducto
                    + " para el pedido " + idPedido);
        }
        if (disponibles[p] < cantidad) {
            return RespuestaReserva.rechazo(null, "stock insuficiente de " + codigoProducto
                    + " (hay " + disponibles[p] + ", se piden " + cantidad + ")");
        }
        disponibles[p] -= cantidad;
        agregarReserva(idPedido, codigoProducto, cantidad);
        return RespuestaReserva.ok(null);
    }

    /** Las unidades ya salieron del disponible al reservar: solo se borra la reserva. Idempotente. */
    @Override
    public synchronized int confirmar(String idPedido, String codigoProducto) {
        int r = buscarReserva(idPedido, codigoProducto);
        if (r != -1) {
            quitarReserva(r);
        }
        return getStock(codigoProducto);
    }

    /** Devuelve las unidades al disponible. Idempotente: sin reserva no hace nada. */
    @Override
    public synchronized void liberar(String idPedido, String codigoProducto) {
        int r = buscarReserva(idPedido, codigoProducto);
        if (r == -1) {
            return;
        }
        int p = buscarProducto(codigoProducto);
        if (p != -1) {
            disponibles[p] += reservaCantidades[r];
        }
        quitarReserva(r);
    }

    // ------------------------------------------------------------------
    // Extras para la consola
    // ------------------------------------------------------------------

    /** Unidades disponibles del producto, o -1 si no existe. */
    public synchronized int getStock(String codigoProducto) {
        int p = buscarProducto(codigoProducto);
        return p == -1 ? -1 : disponibles[p];
    }

    /** Unidades reservadas (sin confirmar) del producto. */
    public synchronized int getReservado(String codigoProducto) {
        int total = 0;
        for (int i = 0; i < cantidadReservas; i++) {
            if (reservaCodigos[i].equals(codigoProducto)) {
                total += reservaCantidades[i];
            }
        }
        return total;
    }

    /**
     * Agrega unidades a un producto existente.
     *
     * @return el stock disponible nuevo.
     * @throws IllegalArgumentException si el producto no existe o la cantidad es <= 0.
     */
    public synchronized int agregarUnidades(String codigoProducto, int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad a agregar debe ser mayor que 0.");
        }
        int p = buscarProducto(codigoProducto);
        if (p == -1) {
            throw new IllegalArgumentException("El producto " + codigoProducto + " no existe en esta sede.");
        }
        disponibles[p] += cantidad;
        return disponibles[p];
    }

    /** Una línea por producto: "B01  Bebida  ₡1000.00  disponible 5  reservado 0". */
    public synchronized String[] listarProductos() {
        String[] lineas = new String[cantidadProductos];
        for (int i = 0; i < cantidadProductos; i++) {
            lineas[i] = String.format("%-5s %-12s ₡%8.2f  disponible %3d  reservado %3d",
                    codigos[i], nombres[i], precios[i], disponibles[i], getReservado(codigos[i]));
        }
        return lineas;
    }

    public synchronized int getCantidadReservas() {
        return cantidadReservas;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private int buscarProducto(String codigo) {
        for (int i = 0; i < cantidadProductos; i++) {
            if (codigos[i].equals(codigo)) {
                return i;
            }
        }
        return -1;
    }

    private int buscarReserva(String idPedido, String codigo) {
        for (int i = 0; i < cantidadReservas; i++) {
            if (reservaPedidos[i].equals(idPedido) && reservaCodigos[i].equals(codigo)) {
                return i;
            }
        }
        return -1;
    }

    private void agregarReserva(String idPedido, String codigo, int cantidad) {
        if (cantidadReservas == reservaPedidos.length) {
            int nueva = reservaPedidos.length * 2;
            String[] pedidos = new String[nueva];
            String[] cods = new String[nueva];
            int[] cants = new int[nueva];
            for (int i = 0; i < cantidadReservas; i++) {
                pedidos[i] = reservaPedidos[i];
                cods[i] = reservaCodigos[i];
                cants[i] = reservaCantidades[i];
            }
            reservaPedidos = pedidos;
            reservaCodigos = cods;
            reservaCantidades = cants;
        }
        reservaPedidos[cantidadReservas] = idPedido;
        reservaCodigos[cantidadReservas] = codigo;
        reservaCantidades[cantidadReservas] = cantidad;
        cantidadReservas++;
    }

    /** Quita la reserva r moviendo la última a su lugar. */
    private void quitarReserva(int r) {
        cantidadReservas--;
        reservaPedidos[r] = reservaPedidos[cantidadReservas];
        reservaCodigos[r] = reservaCodigos[cantidadReservas];
        reservaCantidades[r] = reservaCantidades[cantidadReservas];
        reservaPedidos[cantidadReservas] = null;
        reservaCodigos[cantidadReservas] = null;
    }
}
