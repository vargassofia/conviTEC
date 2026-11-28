package cr.ac.tec.convitec.web;

import cr.ac.tec.convitec.estadisticas.ProductoEstrella;
import cr.ac.tec.convitec.estadisticas.ResumenProducto;
import cr.ac.tec.convitec.modelo.DetalleVenta;
import cr.ac.tec.convitec.modelo.Pedido;
import cr.ac.tec.convitec.modelo.Venta;
import cr.ac.tec.convitec.pedidos.ServicioPedidos;
import cr.ac.tec.convitec.servicios.IServicioEstadisticas;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Lógica de la pantalla de Estadísticas (GET /api/estadisticas) y del resumen de Inicio (GET /api/resumen).
 *
 * Los cálculos (historial, producto estrella, ranking, peores pedidos) los hace ServicioEstadisticas con el
 * MergeSort propio; aquí solo se arma la respuesta.
 */
final class ControladorEstadisticas {

    static final int PEORES_POR_DEFECTO = 5;
    static final int MAXIMO_PEORES = 50;

    private final IServicioEstadisticas estadisticas;
    private final ServicioPedidos pedidos;
    private final Clock reloj;

    ControladorEstadisticas(IServicioEstadisticas estadisticas, ServicioPedidos pedidos, Clock reloj) {
        this.estadisticas = estadisticas;
        this.pedidos = pedidos;
        this.reloj = reloj;
    }

    /**
     * Todo lo de la pantalla de Estadísticas.
     *
     * @param desde      "AAAA-MM-DD" o null/vacío (sin límite)
     * @param hasta      "AAAA-MM-DD" o null/vacío (sin límite)
     * @param peores     cuántos peores pedidos mostrar (texto, null = 5)
     * @throws IllegalArgumentException si una fecha o el número no son válidos, o desde es posterior a hasta.
     */
    EstadisticasDTO consultar(String desde, String hasta, String peores) {
        LocalDate inicio = fecha(desde, "desde");
        LocalDate fin = fecha(hasta, "hasta");
        int cantidadPeores = numeroPeores(peores);

        Venta[] historial;
        if (inicio == null && fin == null) {
            historial = estadisticas.historialVentas();
        } else {
            LocalDate a = inicio == null ? LocalDate.of(2000, 1, 1) : inicio;
            LocalDate b = fin == null ? LocalDate.of(2999, 12, 31) : fin;
            if (a.isAfter(b)) {
                throw new IllegalArgumentException("La fecha inicial (" + a + ") es posterior a la final (" + b + ").");
            }
            historial = estadisticas.historialVentas(a, b);
        }
        double montoRango = 0;
        for (int i = 0; i < historial.length; i++) {
            montoRango += historial[i].getMontoTotal();
        }

        ProductoEstrella estrella = estadisticas.productoEstrella();
        return new EstadisticasDTO(VentaDTO.de(historial), montoRango,
                estrella == null ? null : ProductoDTO.de(estrella),
                ProductoDTO.de(estadisticas.rankingProductos()),
                VentaDTO.de(estadisticas.peoresPedidos(cantidadPeores)),
                estadisticas.montoTotalVendido());
    }

    /** Números del día para la pantalla de Inicio. */
    ResumenDTO resumen() {
        LocalDate hoy = LocalDate.now(reloj);
        Venta[] ventasHoy = estadisticas.historialVentas(hoy, hoy);
        double montoHoy = 0;
        for (int i = 0; i < ventasHoy.length; i++) {
            montoHoy += ventasHoy[i].getMontoTotal();
        }
        Pedido[] historial = pedidos.listarHistorial();
        int pedidosHoy = 0;
        for (int i = 0; i < historial.length; i++) {
            if (historial[i].getFechaCreacion().toLocalDate().equals(hoy)) {
                pedidosHoy++;
            }
        }
        ProductoEstrella estrella = estadisticas.productoEstrella();
        return new ResumenDTO(pedidos.listarPendientes().length, pedidosHoy, ventasHoy.length, montoHoy,
                estrella == null ? null : ProductoDTO.de(estrella), hoy.toString());
    }

    private static LocalDate fecha(String texto, String campo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("La fecha '" + campo + "' no es válida (use AAAA-MM-DD).");
        }
    }

    private static int numeroPeores(String texto) {
        if (texto == null || texto.isBlank()) {
            return PEORES_POR_DEFECTO;
        }
        int n;
        try {
            n = Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("La cantidad de peores pedidos debe ser un número entero.");
        }
        if (n <= 0 || n > MAXIMO_PEORES) {
            throw new IllegalArgumentException("La cantidad de peores pedidos debe estar entre 1 y "
                    + MAXIMO_PEORES + ".");
        }
        return n;
    }

    // ------------------------------------------------------------------
    // Formas JSON
    // ------------------------------------------------------------------

    static final class EstadisticasDTO {
        final VentaDTO[] historial;          // del más reciente al más antiguo, dentro del rango
        final double montoHistorial;         // suma del historial mostrado
        final ProductoDTO productoEstrella;  // null si no hay ventas
        final ProductoDTO[] ranking;         // de mayor a menor monto
        final VentaDTO[] peoresPedidos;      // de menor a mayor rendimiento
        final double montoTotalVendido;      // de todas las ventas de la sede

        EstadisticasDTO(VentaDTO[] historial, double montoHistorial, ProductoDTO productoEstrella,
                        ProductoDTO[] ranking, VentaDTO[] peoresPedidos, double montoTotalVendido) {
            this.historial = historial;
            this.montoHistorial = montoHistorial;
            this.productoEstrella = productoEstrella;
            this.ranking = ranking;
            this.peoresPedidos = peoresPedidos;
            this.montoTotalVendido = montoTotalVendido;
        }
    }

    static final class ResumenDTO {
        final int pedidosPendientes;
        final int pedidosHoy;
        final int ventasHoy;
        final double montoHoy;
        final ProductoDTO productoEstrella;
        final String fecha;

        ResumenDTO(int pedidosPendientes, int pedidosHoy, int ventasHoy, double montoHoy,
                   ProductoDTO productoEstrella, String fecha) {
            this.pedidosPendientes = pedidosPendientes;
            this.pedidosHoy = pedidosHoy;
            this.ventasHoy = ventasHoy;
            this.montoHoy = montoHoy;
            this.productoEstrella = productoEstrella;
            this.fecha = fecha;
        }
    }

    static final class ProductoDTO {
        final String codigo;
        final String nombre;
        final int unidades;
        final double monto;

        private ProductoDTO(String codigo, String nombre, int unidades, double monto) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.unidades = unidades;
            this.monto = monto;
        }

        static ProductoDTO de(ResumenProducto p) {
            return new ProductoDTO(p.getCodigoProducto(), p.getNombreProducto(), p.getUnidadesVendidas(),
                    p.getMontoTotal());
        }

        static ProductoDTO[] de(ResumenProducto[] lista) {
            ProductoDTO[] resultado = new ProductoDTO[lista.length];
            for (int i = 0; i < lista.length; i++) {
                resultado[i] = de(lista[i]);
            }
            return resultado;
        }
    }

    static final class VentaDTO {
        final String idVenta;
        final String idPedido;
        final String destino;
        final String fechaHora;      // ISO
        final String horaSalida;
        final double montoTotal;
        final int tiempoTraslado;
        final double rendimiento;    // ₡ por minuto
        final LineaDTO[] detalles;

        private VentaDTO(String idVenta, String idPedido, String destino, String fechaHora, String horaSalida,
                         double montoTotal, int tiempoTraslado, double rendimiento, LineaDTO[] detalles) {
            this.idVenta = idVenta;
            this.idPedido = idPedido;
            this.destino = destino;
            this.fechaHora = fechaHora;
            this.horaSalida = horaSalida;
            this.montoTotal = montoTotal;
            this.tiempoTraslado = tiempoTraslado;
            this.rendimiento = rendimiento;
            this.detalles = detalles;
        }

        static VentaDTO[] de(Venta[] ventas) {
            VentaDTO[] resultado = new VentaDTO[ventas.length];
            for (int i = 0; i < ventas.length; i++) {
                Venta v = ventas[i];
                DetalleVenta[] d = v.getDetalles();
                LineaDTO[] lineas = new LineaDTO[d.length];
                for (int j = 0; j < d.length; j++) {
                    lineas[j] = new LineaDTO(d[j].getCodigoProducto(), d[j].getNombreProducto(), d[j].getCantidad(),
                            d[j].getPrecioUnitario(), d[j].getSubtotal());
                }
                resultado[i] = new VentaDTO(v.getIdVenta(), v.getIdPedido(), v.getDestino(),
                        v.getFechaHora().toString(), v.getHoraSalida(), v.getMontoTotal(), v.getTiempoTraslado(),
                        v.getRendimiento(), lineas);
            }
            return resultado;
        }
    }

    static final class LineaDTO {
        final String codigoProducto;
        final String nombre;
        final int cantidad;
        final double precioUnitario;
        final double subtotal;

        LineaDTO(String codigoProducto, String nombre, int cantidad, double precioUnitario, double subtotal) {
            this.codigoProducto = codigoProducto;
            this.nombre = nombre;
            this.cantidad = cantidad;
            this.precioUnitario = precioUnitario;
            this.subtotal = subtotal;
        }
    }
}
