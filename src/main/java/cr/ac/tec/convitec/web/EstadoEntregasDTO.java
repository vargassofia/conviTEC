package cr.ac.tec.convitec.web;

import cr.ac.tec.convitec.modelo.Venta;
import cr.ac.tec.convitec.rutas.ParadaEntrega;

/**
 * Estado de la salida de entregas actual, tal como lo recibe la pantalla de Entregas.
 *
 * {
 *   "iniciado": true, "sede": "San José", "horaSalida": "11:00",
 *   "ubicacionActual": "Heredia", "tiempoAcumulado": 20, "hayPendientes": true,
 *   "totalPedidos": 4, "entregados": 2,
 *   "pendientes":     [ { "idPedido": "SJO-0003", "destino": "Cartago" }, ... ],
 *   "noAlcanzables":  [ { "idPedido": "LIB-0003", "explicacion": "No hay ruta de Liberia a Cartago" } ],
 *   "paradas":        [ { "numero": 1, "desde": "San José", "destino": "Heredia", "camino": [...],
 *                         "tiempoTramo": 20, "tiempoAcumulado": 20, "idsPedidos": ["SJO-0002"] } ],
 *   "ultimaParada":   { ... la parada recién hecha, o null ... },
 *   "ventasRegistradas": [ { "idVenta": "V-SJO-0002", "idPedido": "SJO-0002", "montoTotal": 3000.0 } ],
 *   "aviso":          null,   // texto si la parada se hizo pero sus ventas no se pudieron registrar
 *   "esDemostracion": false
 * }
 */
final class EstadoEntregasDTO {

    final boolean iniciado;
    final String sede;
    final String horaSalida;
    final String ubicacionActual;
    final int tiempoAcumulado;
    final boolean hayPendientes;
    final int totalPedidos;
    final int entregados;
    final PedidoDTO[] pendientes;
    final NoAlcanzableDTO[] noAlcanzables;
    final ParadaDTO[] paradas;
    final ParadaDTO ultimaParada;
    final VentaRegistradaDTO[] ventasRegistradas;
    final String aviso;
    final boolean esDemostracion;

    EstadoEntregasDTO(boolean iniciado, String sede, String horaSalida, String ubicacionActual, int tiempoAcumulado,
                      boolean hayPendientes, int totalPedidos, int entregados, PedidoDTO[] pendientes,
                      NoAlcanzableDTO[] noAlcanzables, ParadaDTO[] paradas, ParadaDTO ultimaParada,
                      VentaRegistradaDTO[] ventasRegistradas, String aviso, boolean esDemostracion) {
        this.iniciado = iniciado;
        this.sede = sede;
        this.horaSalida = horaSalida;
        this.ubicacionActual = ubicacionActual;
        this.tiempoAcumulado = tiempoAcumulado;
        this.hayPendientes = hayPendientes;
        this.totalPedidos = totalPedidos;
        this.entregados = entregados;
        this.pendientes = pendientes;
        this.noAlcanzables = noAlcanzables;
        this.paradas = paradas;
        this.ultimaParada = ultimaParada;
        this.ventasRegistradas = ventasRegistradas;
        this.aviso = aviso;
        this.esDemostracion = esDemostracion;
    }

    /** Antes de empezar cualquier salida: el vehículo está en la sede. */
    static EstadoEntregasDTO sinIniciar(String sede, boolean esDemostracion) {
        return new EstadoEntregasDTO(false, sede, null, sede, 0, false, 0, 0,
                new PedidoDTO[0], new NoAlcanzableDTO[0], new ParadaDTO[0], null, new VentaRegistradaDTO[0],
                null, esDemostracion);
    }

    static final class PedidoDTO {
        final String idPedido;
        final String destino;

        PedidoDTO(String idPedido, String destino) {
            this.idPedido = idPedido;
            this.destino = destino;
        }
    }

    static final class NoAlcanzableDTO {
        final String idPedido;
        final String explicacion;

        NoAlcanzableDTO(String idPedido, String explicacion) {
            this.idPedido = idPedido;
            this.explicacion = explicacion;
        }

        /** Une los dos arreglos paralelos del recorrido (ids y explicaciones). */
        static NoAlcanzableDTO[] de(String[] ids, String[] explicaciones) {
            NoAlcanzableDTO[] lista = new NoAlcanzableDTO[ids.length];
            for (int i = 0; i < ids.length; i++) {
                lista[i] = new NoAlcanzableDTO(ids[i], explicaciones[i]);
            }
            return lista;
        }
    }

    static final class ParadaDTO {
        final int numero;
        final String desde;
        final String destino;
        final String[] camino;
        final int tiempoTramo;
        final int tiempoAcumulado;
        final String[] idsPedidos;

        private ParadaDTO(int numero, String desde, String destino, String[] camino, int tiempoTramo,
                          int tiempoAcumulado, String[] idsPedidos) {
            this.numero = numero;
            this.desde = desde;
            this.destino = destino;
            this.camino = camino;
            this.tiempoTramo = tiempoTramo;
            this.tiempoAcumulado = tiempoAcumulado;
            this.idsPedidos = idsPedidos;
        }

        static ParadaDTO de(ParadaEntrega p) {
            return new ParadaDTO(p.getNumero(), p.getDesde(), p.getDestino(), p.getCamino(), p.getTiempoTramo(),
                    p.getTiempoAcumulado(), p.getIdsPedidos());
        }
    }

    /** Una venta registrada al entregar (resumen para el mensaje de la pantalla). */
    static final class VentaRegistradaDTO {
        final String idVenta;
        final String idPedido;
        final double montoTotal;

        private VentaRegistradaDTO(String idVenta, String idPedido, double montoTotal) {
            this.idVenta = idVenta;
            this.idPedido = idPedido;
            this.montoTotal = montoTotal;
        }

        static VentaRegistradaDTO[] de(Venta[] ventas) {
            VentaRegistradaDTO[] lista = new VentaRegistradaDTO[ventas.length];
            for (int i = 0; i < ventas.length; i++) {
                lista[i] = new VentaRegistradaDTO(ventas[i].getIdVenta(), ventas[i].getIdPedido(),
                        ventas[i].getMontoTotal());
            }
            return lista;
        }
    }
}
