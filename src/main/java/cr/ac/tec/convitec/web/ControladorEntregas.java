package cr.ac.tec.convitec.web;

import cr.ac.tec.convitec.entregas.ServicioEntregas;
import cr.ac.tec.convitec.grafo.Grafo;
import cr.ac.tec.convitec.grafo.ResultadoRuta;
import cr.ac.tec.convitec.modelo.Venta;
import cr.ac.tec.convitec.rutas.ParadaEntrega;
import cr.ac.tec.convitec.rutas.PlanificadorEntregas;
import cr.ac.tec.convitec.rutas.RecorridoEntrega;
import cr.ac.tec.convitec.rutas.SalidasDiarias;
import cr.ac.tec.convitec.rutas.ServicioRutas;
import cr.ac.tec.convitec.rutas.SolicitudEntrega;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Lógica de la pantalla de Entregas y de la consulta de rutas. No sabe nada de HTTP: los endpoints
 * de /api/entregas y /api/ruta solo la llaman y envían lo que devuelve como JSON.
 *
 * - El recorrido paso a paso lo calcula ServicioRutas (Dijkstra y PlanificadorEntregas).
 * - Cada parada calculada es una ENTREGA REAL: sus pedidos se registran como ventas con
 *   ServicioEntregas (que además los marca como ENTREGADO en el servicio de pedidos).
 * - La vista previa del recorrido completo usa OTRO planificador, así no interrumpe una salida
 *   en curso y no registra ninguna entrega.
 *
 * No es thread-safe por sí sola: ServidorWeb atiende las peticiones de una en una.
 */
final class ControladorEntregas {

    private final ServicioRutas servicioRutas;
    private final ServicioEntregas servicioEntregas;
    private final FuentePendientes fuente;
    private final Grafo grafo;
    private final String sede;
    private final Clock reloj;

    // Estado de la salida actual
    private boolean iniciado;
    private String horaSalida;
    private SolicitudEntrega[] pedidosDeLaSalida = new SolicitudEntrega[0];
    private ParadaEntrega ultimaParada;
    private Venta[] ventasUltimaParada = new Venta[0];
    private String avisoUltimaParada;

    /** @param sede nombre de la sede en el grafo ("San José") */
    ControladorEntregas(ServicioRutas servicioRutas, ServicioEntregas servicioEntregas, FuentePendientes fuente,
                        Grafo grafo, String sede, Clock reloj) {
        this.servicioRutas = servicioRutas;
        this.servicioEntregas = servicioEntregas;
        this.fuente = fuente;
        this.grafo = grafo;
        this.sede = sede;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------
    // Entregas paso a paso
    // ------------------------------------------------------------------

    /** Cómo va la salida actual (iniciado = false si todavía no se empezó ninguna). */
    EstadoEntregasDTO estado() {
        return armarEstado();
    }

    /**
     * Empieza una salida con los pedidos pendientes de esa hora. Reinicia la anterior.
     *
     * @throws IllegalArgumentException si la hora no es "11:00" ni "15:00".
     */
    EstadoEntregasDTO iniciar(String hora) {
        exigirHora(hora);
        SolicitudEntrega[] pendientes = pedirPendientes(hora);
        servicioRutas.iniciarRecorrido(pendientes, hora);
        this.iniciado = true;
        this.horaSalida = hora;
        this.pedidosDeLaSalida = pendientes;
        this.ultimaParada = null;
        this.ventasUltimaParada = new Venta[0];
        this.avisoUltimaParada = null;
        return armarEstado();
    }

    /**
     * Calcula la siguiente parada (Dijkstra desde la ubicación actual, destino más cercano),
     * entrega ahí sus pedidos y los registra como ventas.
     *
     * Si el registro de ventas falla (por ejemplo, un pedido ya tenía venta), la parada igual se
     * hizo: el problema se informa en "aviso" y no se registra ninguna venta de esa parada.
     *
     * @throws IllegalStateException si no hay salida iniciada o ya no quedan entregas.
     */
    EstadoEntregasDTO siguiente() {
        if (!iniciado) {
            throw new IllegalStateException("Primero elija la salida (11:00 o 15:00) y presione “Iniciar salida”.");
        }
        if (!servicioRutas.hayEntregasPendientes()) {
            throw new IllegalStateException("Ya no quedan entregas pendientes en esta salida.");
        }
        ultimaParada = servicioRutas.calcularSiguienteEntrega();
        try {
            ventasUltimaParada = servicioEntregas.registrarParada(ultimaParada, horaSalida, LocalDateTime.now(reloj));
            avisoUltimaParada = null;
        } catch (IllegalStateException | IllegalArgumentException e) {
            ventasUltimaParada = new Venta[0];
            avisoUltimaParada = "La parada se hizo, pero no se pudieron registrar sus ventas: " + e.getMessage();
        }
        return armarEstado();
    }

    // ------------------------------------------------------------------
    // Vista previa del recorrido completo
    // ------------------------------------------------------------------

    /**
     * Planifica toda la salida de una vez, SIN entregar nada: sirve para ver el orden y el tiempo total.
     * No cambia la salida en curso (usa un planificador aparte).
     *
     * @throws IllegalArgumentException si la hora no es "11:00" ni "15:00".
     */
    VistaPreviaDTO vistaPrevia(String hora) {
        exigirHora(hora);
        PlanificadorEntregas planificador = new PlanificadorEntregas(grafo, sede);
        RecorridoEntrega recorrido = planificador.planificarRecorridoCompleto(pedirPendientes(hora), hora);
        return VistaPreviaDTO.de(recorrido);
    }

    // ------------------------------------------------------------------
    // Consulta de ruta
    // ------------------------------------------------------------------

    /**
     * Ruta más corta desde esta sede.
     *
     * @throws IllegalArgumentException si falta el destino.
     * @throws cr.ac.tec.convitec.grafo.GrafoException si el destino no existe en el mapa.
     */
    RutaConsultadaDTO ruta(String destino) {
        if (destino == null || destino.isBlank()) {
            throw new IllegalArgumentException("Indique el destino de la ruta.");
        }
        ResultadoRuta r = servicioRutas.caminoMasCorto(sede, destino.trim());
        return new RutaConsultadaDTO(r.getOrigen(), r.getDestino(), r.esAlcanzable(),
                r.esAlcanzable() ? r.getTiempoTotal() : null, r.getCamino(),
                r.esAlcanzable() ? null : "No hay ruta de " + r.getOrigen() + " a " + r.getDestino()
                        + ": están en partes del mapa que no se conectan.");
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private SolicitudEntrega[] pedirPendientes(String hora) {
        SolicitudEntrega[] pendientes = fuente.pendientesDeSalida(hora);
        return pendientes == null ? new SolicitudEntrega[0] : pendientes;
    }

    private static void exigirHora(String hora) {
        if (!SalidasDiarias.esHoraSalidaValida(hora)) {
            throw new IllegalArgumentException("Elija una salida válida: " + SalidasDiarias.SALIDA_MANANA
                    + " o " + SalidasDiarias.SALIDA_TARDE + ".");
        }
    }

    private EstadoEntregasDTO armarEstado() {
        if (!iniciado) {
            return EstadoEntregasDTO.sinIniciar(sede, fuente.esDemostracion());
        }
        RecorridoEntrega recorrido = servicioRutas.obtenerRecorrido();
        ParadaEntrega[] paradas = recorrido.getParadas();
        String[] noAlcanzables = recorrido.getIdsNoAlcanzables();
        String[] explicaciones = recorrido.getExplicacionesNoAlcanzables();

        // Faltan = pedidos de la salida que no se entregaron y que sí se pueden alcanzar
        int faltan = 0;
        for (int i = 0; i < pedidosDeLaSalida.length; i++) {
            if (faltaEntregar(pedidosDeLaSalida[i].getIdPedido(), paradas, noAlcanzables)) {
                faltan++;
            }
        }
        EstadoEntregasDTO.PedidoDTO[] pendientes = new EstadoEntregasDTO.PedidoDTO[faltan];
        int p = 0;
        for (int i = 0; i < pedidosDeLaSalida.length; i++) {
            SolicitudEntrega s = pedidosDeLaSalida[i];
            if (faltaEntregar(s.getIdPedido(), paradas, noAlcanzables)) {
                pendientes[p++] = new EstadoEntregasDTO.PedidoDTO(s.getIdPedido().trim(), s.getDestino());
            }
        }

        EstadoEntregasDTO.ParadaDTO[] paradasDTO = new EstadoEntregasDTO.ParadaDTO[paradas.length];
        for (int i = 0; i < paradas.length; i++) {
            paradasDTO[i] = EstadoEntregasDTO.ParadaDTO.de(paradas[i]);
        }

        String ubicacionActual = paradas.length == 0 ? recorrido.getSede() : paradas[paradas.length - 1].getDestino();
        return new EstadoEntregasDTO(true, recorrido.getSede(), horaSalida, ubicacionActual,
                recorrido.getTiempoTotal(), servicioRutas.hayEntregasPendientes(), pedidosDeLaSalida.length,
                recorrido.getCantidadPedidosEntregados(), pendientes,
                EstadoEntregasDTO.NoAlcanzableDTO.de(noAlcanzables, explicaciones), paradasDTO,
                ultimaParada == null ? null : EstadoEntregasDTO.ParadaDTO.de(ultimaParada),
                EstadoEntregasDTO.VentaRegistradaDTO.de(ventasUltimaParada), avisoUltimaParada,
                fuente.esDemostracion());
    }

    private static boolean faltaEntregar(String idPedido, ParadaEntrega[] paradas, String[] noAlcanzables) {
        String id = idPedido.trim();
        for (int i = 0; i < noAlcanzables.length; i++) {
            if (noAlcanzables[i].equals(id)) {
                return false;
            }
        }
        for (int i = 0; i < paradas.length; i++) {
            String[] ids = paradas[i].getIdsPedidos();
            for (int j = 0; j < ids.length; j++) {
                if (ids[j].equals(id)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Respuesta de POST /api/entregas/vista-previa. */
    static final class VistaPreviaDTO {
        final String sede;
        final String horaSalida;
        final EstadoEntregasDTO.ParadaDTO[] paradas;
        final int tiempoTotal;
        final int pedidosEntregados;
        final EstadoEntregasDTO.NoAlcanzableDTO[] noAlcanzables;

        private VistaPreviaDTO(String sede, String horaSalida, EstadoEntregasDTO.ParadaDTO[] paradas, int tiempoTotal,
                               int pedidosEntregados, EstadoEntregasDTO.NoAlcanzableDTO[] noAlcanzables) {
            this.sede = sede;
            this.horaSalida = horaSalida;
            this.paradas = paradas;
            this.tiempoTotal = tiempoTotal;
            this.pedidosEntregados = pedidosEntregados;
            this.noAlcanzables = noAlcanzables;
        }

        static VistaPreviaDTO de(RecorridoEntrega r) {
            ParadaEntrega[] paradas = r.getParadas();
            EstadoEntregasDTO.ParadaDTO[] dtos = new EstadoEntregasDTO.ParadaDTO[paradas.length];
            for (int i = 0; i < paradas.length; i++) {
                dtos[i] = EstadoEntregasDTO.ParadaDTO.de(paradas[i]);
            }
            return new VistaPreviaDTO(r.getSede(), r.getHoraSalida(), dtos, r.getTiempoTotal(),
                    r.getCantidadPedidosEntregados(),
                    EstadoEntregasDTO.NoAlcanzableDTO.de(r.getIdsNoAlcanzables(), r.getExplicacionesNoAlcanzables()));
        }
    }

    /** Respuesta de GET /api/ruta. */
    static final class RutaConsultadaDTO {
        final String origen;
        final String destino;
        final boolean alcanzable;
        final Integer tiempoTotal;   // null si no es alcanzable
        final String[] camino;       // vacío si no es alcanzable
        final String explicacion;    // null si es alcanzable

        RutaConsultadaDTO(String origen, String destino, boolean alcanzable, Integer tiempoTotal, String[] camino,
                          String explicacion) {
            this.origen = origen;
            this.destino = destino;
            this.alcanzable = alcanzable;
            this.tiempoTotal = tiempoTotal;
            this.camino = camino;
            this.explicacion = explicacion;
        }
    }
}
