package cr.ac.tec.convitec.modelo;

import java.time.LocalDateTime;

/**
 * Una venta: se crea cuando un pedido se ENTREGA.
 *
 * Registra lo que pide el enunciado: productos, cantidades, precio unitario (en los detalles),
 * monto total, fecha de la transacción y sede. Además guarda el destino, la hora de salida
 * y el tiempo de traslado, que se usan para las estadísticas (rendimiento = monto ÷ tiempo).
 *
 * - montoTotal se calcula sumando los subtotales de los detalles.
 * - Es inmutable: getDetalles() devuelve una copia del arreglo.
 */
public final class Venta {

    /** Horas de salida válidas (las mismas de cr.ac.tec.convitec.rutas.SalidasDiarias). */
    private static final String SALIDA_MANANA = "11:00";
    private static final String SALIDA_TARDE = "15:00";

    private final String idVenta;
    private final String idPedido;
    private final String sede;
    private final String destino;
    private final LocalDateTime fechaHora;
    private final String horaSalida;
    private final DetalleVenta[] detalles;
    private final double montoTotal;
    private final int tiempoTraslado;

    /**
     * @param tiempoTraslado minutos desde la sede hasta el destino del pedido (>= 0)
     * @throws IllegalArgumentException si algún dato es nulo/vacío, la hora de salida no es
     *                                  "11:00" ni "15:00", no hay detalles, hay un detalle nulo,
     *                                  un producto repetido o el tiempo es negativo.
     */
    public Venta(String idVenta, String idPedido, String sede, String destino, LocalDateTime fechaHora,
                 String horaSalida, DetalleVenta[] detalles, int tiempoTraslado) {
        validarTexto(idVenta, "idVenta");
        validarTexto(idPedido, "idPedido");
        validarTexto(sede, "sede");
        validarTexto(destino, "destino");
        if (fechaHora == null) {
            throw new IllegalArgumentException("La venta del pedido '" + idPedido + "' no tiene fecha.");
        }
        if (!SALIDA_MANANA.equals(horaSalida) && !SALIDA_TARDE.equals(horaSalida)) {
            throw new IllegalArgumentException("Hora de salida inválida: '" + horaSalida
                    + "'. Debe ser " + SALIDA_MANANA + " o " + SALIDA_TARDE + ".");
        }
        if (tiempoTraslado < 0) {
            throw new IllegalArgumentException("El tiempo de traslado no puede ser negativo (llegó "
                    + tiempoTraslado + ").");
        }
        validarDetalles(detalles, idPedido);

        this.idVenta = idVenta.trim();
        this.idPedido = idPedido.trim();
        this.sede = sede.trim();
        this.destino = destino.trim();
        this.fechaHora = fechaHora;
        this.horaSalida = horaSalida;
        this.detalles = copiar(detalles);
        this.tiempoTraslado = tiempoTraslado;

        double suma = 0;
        for (int i = 0; i < detalles.length; i++) {
            suma += detalles[i].getSubtotal();
        }
        this.montoTotal = suma;
    }

    public String getIdVenta() {
        return idVenta;
    }

    public String getIdPedido() {
        return idPedido;
    }

    public String getSede() {
        return sede;
    }

    public String getDestino() {
        return destino;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public String getHoraSalida() {
        return horaSalida;
    }

    /** Copia de los detalles (productos, cantidades y precios). */
    public DetalleVenta[] getDetalles() {
        return copiar(detalles);
    }

    public int getCantidadDetalles() {
        return detalles.length;
    }

    /** Suma de los subtotales de todos los detalles, en colones. */
    public double getMontoTotal() {
        return montoTotal;
    }

    /** Minutos desde la sede hasta el destino (tiempo acumulado de la parada). */
    public int getTiempoTraslado() {
        return tiempoTraslado;
    }

    /**
     * Rendimiento = montoTotal ÷ tiempoTraslado (colones por minuto).
     *
     * Si el tiempo es 0 (pedido entregado en la misma sede) se usa 1 minuto como mínimo,
     * para no dividir entre cero. Así esos pedidos tienen un rendimiento alto pero finito.
     */
    public double getRendimiento() {
        int tiempo = (tiempoTraslado == 0) ? 1 : tiempoTraslado;
        return montoTotal / tiempo;
    }

    /**
     * Ejemplo:
     * Venta V-P2 (pedido P2) | San José -> Heredia | 2026-11-14T11:20 salida 11:00
     *   2 x Café (C01) a ₡1500.00 = ₡3000.00
     * Total ₡3000.00 | traslado 20 min | rendimiento 150.00 ₡/min
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Venta ").append(idVenta).append(" (pedido ").append(idPedido).append(") | ")
                .append(sede).append(" -> ").append(destino).append(" | ")
                .append(fechaHora).append(" salida ").append(horaSalida).append('\n');
        for (int i = 0; i < detalles.length; i++) {
            sb.append("  ").append(detalles[i]).append('\n');
        }
        sb.append(String.format("Total ₡%.2f | traslado %d min | rendimiento %.2f ₡/min",
                montoTotal, tiempoTraslado, getRendimiento()));
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static void validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("La venta no tiene '" + campo + "'.");
        }
    }

    /** No vacío, sin nulos y sin el mismo producto repetido. */
    private static void validarDetalles(DetalleVenta[] detalles, String idPedido) {
        if (detalles == null || detalles.length == 0) {
            throw new IllegalArgumentException("La venta del pedido '" + idPedido + "' no tiene productos.");
        }
        for (int i = 0; i < detalles.length; i++) {
            if (detalles[i] == null) {
                throw new IllegalArgumentException("La venta del pedido '" + idPedido
                        + "' tiene un detalle nulo en la posición " + i + ".");
            }
            for (int j = 0; j < i; j++) {
                if (detalles[j].getCodigoProducto().equalsIgnoreCase(detalles[i].getCodigoProducto())) {
                    throw new IllegalArgumentException("El producto '" + detalles[i].getCodigoProducto()
                            + "' está repetido en la venta del pedido '" + idPedido + "'.");
                }
            }
        }
    }

    private static DetalleVenta[] copiar(DetalleVenta[] original) {
        DetalleVenta[] copia = new DetalleVenta[original.length];
        for (int i = 0; i < original.length; i++) {
            copia[i] = original[i];
        }
        return copia;
    }
}
