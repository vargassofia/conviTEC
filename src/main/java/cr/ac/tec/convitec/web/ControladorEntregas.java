package cr.ac.tec.convitec.web;

import cr.ac.tec.convitec.grafo.ResultadoRuta;
import cr.ac.tec.convitec.rutas.ParadaEntrega;
import cr.ac.tec.convitec.rutas.RecorridoEntrega;
import cr.ac.tec.convitec.rutas.SalidasDiarias;
import cr.ac.tec.convitec.rutas.ServicioRutas;
import cr.ac.tec.convitec.rutas.SolicitudEntrega;

/**
 * Lógica de la pantalla de Entregas y de la consulta de rutas. No sabe nada de HTTP: los endpoints
 * de /api/entregas y /api/ruta solo la llaman y envían lo que devuelve como JSON.
 *
 * Todo el cálculo lo hace ServicioRutas (Dijkstra y PlanificadorEntregas). Aquí solo se guarda
 * con qué pedidos empezó la salida, para poder mostrar cuáles faltan.
 *
 * No es thread-safe por sí sola: ServidorWeb atiende las peticiones de una en una.
 */
final class ControladorEntregas {

    private final ServicioRutas servicioRutas;
    private final FuentePendientes fuente;
    private final String sede;

    // Estado de la salida actual
    private boolean iniciado;
    private String horaSalida;
    private SolicitudEntrega[] pedidosDeLaSalida = new SolicitudEntrega[0];
    private ParadaEntrega ultimaParada;

    /** @param sede nombre de la sede en el grafo ("San José") */
    ControladorEntregas(ServicioRutas servicioRutas, FuentePendientes fuente, String sede) {
        this.servicioRutas = servicioRutas;
        this.fuente = fuente;
        this.sede = sede;
    }

    // ------------------------------------------------------------------
    // Entregas
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
        return armarEstado();
    }

    /**
     * Calcula y hace la siguiente parada (Dijkstra desde la ubicación actual, destino más cercano).
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
        return armarEstado();
    }

    /**
     * Planifica toda la salida de una vez (deja el recorrido terminado).
     *
     * @throws IllegalArgumentException si la hora no es "11:00" ni "15:00".
     */
    EstadoEntregasDTO completo(String hora) {
        exigirHora(hora);
        SolicitudEntrega[] pendientes = pedirPendientes(hora);
        RecorridoEntrega recorrido = servicioRutas.planificarRecorridoCompleto(pendientes, hora);
        this.iniciado = true;
        this.horaSalida = hora;
        this.pedidosDeLaSalida = pendientes;
        ParadaEntrega[] paradas = recorrido.getParadas();
        this.ultimaParada = paradas.length == 0 ? null : paradas[paradas.length - 1];
        return armarEstado();
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

        EstadoEntregasDTO.NoAlcanzableDTO[] rechazados = new EstadoEntregasDTO.NoAlcanzableDTO[noAlcanzables.length];
        for (int i = 0; i < noAlcanzables.length; i++) {
            rechazados[i] = new EstadoEntregasDTO.NoAlcanzableDTO(noAlcanzables[i], explicaciones[i]);
        }

        EstadoEntregasDTO.ParadaDTO[] paradasDTO = new EstadoEntregasDTO.ParadaDTO[paradas.length];
        for (int i = 0; i < paradas.length; i++) {
            paradasDTO[i] = EstadoEntregasDTO.ParadaDTO.de(paradas[i]);
        }

        String ubicacionActual = paradas.length == 0 ? recorrido.getSede() : paradas[paradas.length - 1].getDestino();
        return new EstadoEntregasDTO(true, recorrido.getSede(), horaSalida, ubicacionActual,
                recorrido.getTiempoTotal(), servicioRutas.hayEntregasPendientes(), pedidosDeLaSalida.length,
                recorrido.getCantidadPedidosEntregados(), pendientes, rechazados, paradasDTO,
                ultimaParada == null ? null : EstadoEntregasDTO.ParadaDTO.de(ultimaParada), fuente.esDemostracion());
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
