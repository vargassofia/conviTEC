package cr.ac.tec.convitec.rutas;

import cr.ac.tec.convitec.grafo.Dijkstra;
import cr.ac.tec.convitec.grafo.Grafo;
import cr.ac.tec.convitec.grafo.GrafoException;
import cr.ac.tec.convitec.grafo.ResultadoDijkstra;

/**
 * Planificador de entregas de una sede (procedimiento del enunciado):
 *
 * 1. El recorrido empieza en la sede.
 * 2. Se ejecuta Dijkstra desde la ubicación actual hacia todos los destinos.
 * 3. Se elige el pedido pendiente cuyo destino tenga el menor tiempo.
 * 4. Se entrega (junto con los demás pedidos al MISMO destino) y la ubicación actual pasa a ser ese destino.
 * 5. Se vuelve a ejecutar Dijkstra desde la nueva ubicación.
 * 6. Se repite hasta entregar todos los pedidos alcanzables.
 *
 * - El vehículo NO regresa a la sede: el tiempo total termina en la última entrega.
 * - Empates: gana el pedido que aparece primero en el arreglo de pendientes.
 * - Dijkstra se ejecuta una sola vez por parada (no una vez por cada pedido).
 * - Es un algoritmo "el más cercano primero": no garantiza el recorrido global más corto
 *   (el enunciado lo acepta).
 *
 * Se puede usar paso a paso (iniciarRecorrido + calcularSiguienteEntrega) o completo
 * (planificarRecorridoCompleto, que reutiliza el paso a paso).
 *
 * Solo usa arreglos nativos.
 */
public class PlanificadorEntregas {

    private final Grafo grafo;
    private final int indiceSede;

    // Estado del recorrido actual
    private boolean recorridoIniciado;
    private String horaSalida;
    private int ubicacionActual;
    private int tiempoAcumulado;
    private ResultadoDijkstra distanciasDesdeActual; // Dijkstra desde la ubicación actual

    // Pedidos alcanzables (en el orden original del arreglo de pendientes)
    private String[] idsPendientes;
    private int[] destinosPendientes;   // índice del destino en el grafo
    private boolean[] entregado;
    private int cantidadPendientes;
    private int cantidadSinEntregar;

    // Pedidos que no se pueden entregar
    private String[] idsNoAlcanzables;
    private String[] explicacionesNoAlcanzables;
    private int cantidadNoAlcanzables;

    // Paradas hechas
    private ParadaEntrega[] paradas;
    private int cantidadParadas;
    private int pedidosEntregados;

    /**
     * @throws GrafoException si el grafo es nulo o está vacío, o si la sede no existe en el grafo.
     */
    public PlanificadorEntregas(Grafo grafo, String sede) {
        if (grafo == null || grafo.getCantidadVertices() == 0) {
            throw new GrafoException("El planificador necesita un grafo con ubicaciones (llegó vacío o nulo).");
        }
        int indice = grafo.indiceDe(sede);
        if (indice == -1) {
            throw new GrafoException("La sede '" + sede + "' no existe en el mapa de rutas.");
        }
        this.grafo = grafo;
        this.indiceSede = indice;
        this.ubicacionActual = indice;
    }

    public String getSede() {
        return grafo.getNombre(indiceSede);
    }

    // ------------------------------------------------------------------
    // Modo paso a paso
    // ------------------------------------------------------------------

    /**
     * Prepara un recorrido nuevo desde la sede. Reinicia cualquier recorrido anterior.
     *
     * - pendientes nulo o vacío: recorrido sin paradas y tiempo 0.
     * - destino que no existe o no es alcanzable desde la sede: va a "no alcanzables" con explicación.
     *
     * @param horaSalida "11:00" o "15:00" (ver SalidasDiarias)
     * @throws GrafoException si hay un pedido nulo, con id nulo/vacío o repetido, o con destino nulo.
     * @throws IllegalArgumentException si la hora de salida no es "11:00" ni "15:00".
     */
    public void iniciarRecorrido(SolicitudEntrega[] pendientes, String horaSalida) {
        if (!SalidasDiarias.esHoraSalidaValida(horaSalida)) {
            throw new IllegalArgumentException("Hora de salida inválida: '" + horaSalida
                    + "'. Debe ser " + SalidasDiarias.SALIDA_MANANA + " o " + SalidasDiarias.SALIDA_TARDE + ".");
        }
        int total = (pendientes == null) ? 0 : pendientes.length;
        validarPendientes(pendientes, total); // antes de tocar el estado

        // Reiniciar el estado
        this.horaSalida = horaSalida;
        ubicacionActual = indiceSede;
        tiempoAcumulado = 0;
        idsPendientes = new String[total];
        destinosPendientes = new int[total];
        entregado = new boolean[total];
        cantidadPendientes = 0;
        idsNoAlcanzables = new String[total];
        explicacionesNoAlcanzables = new String[total];
        cantidadNoAlcanzables = 0;
        paradas = new ParadaEntrega[total]; // como máximo una parada por pedido
        cantidadParadas = 0;
        pedidosEntregados = 0;
        distanciasDesdeActual = null;
        recorridoIniciado = true;

        if (total == 0) {
            cantidadSinEntregar = 0;
            return;
        }

        // Primer Dijkstra desde la sede: sirve para la primera parada y para descartar inalcanzables.
        // Como el grafo es no dirigido, lo que no se alcanza desde la sede tampoco se alcanza
        // desde ningún punto del recorrido.
        distanciasDesdeActual = Dijkstra.calcular(grafo, indiceSede);

        for (int k = 0; k < total; k++) {
            String id = pendientes[k].getIdPedido().trim();
            String destino = pendientes[k].getDestino();
            int indiceDestino = grafo.indiceDe(destino);
            if (indiceDestino == -1) {
                agregarNoAlcanzable(id, "La ubicación '" + destino + "' no existe en el mapa de rutas");
            } else if (!distanciasDesdeActual.esAlcanzable(indiceDestino)) {
                agregarNoAlcanzable(id, "No hay ruta de " + getSede() + " a " + grafo.getNombre(indiceDestino));
            } else {
                idsPendientes[cantidadPendientes] = id;
                destinosPendientes[cantidadPendientes] = indiceDestino;
                cantidadPendientes++;
            }
        }
        cantidadSinEntregar = cantidadPendientes;
    }

    public boolean hayEntregasPendientes() {
        return recorridoIniciado && cantidadSinEntregar > 0;
    }

    /**
     * Hace la siguiente parada: elige el destino pendiente más cercano a la ubicación actual,
     * entrega ahí todos los pedidos de ese destino y mueve el vehículo.
     *
     * @throws IllegalStateException si no se llamó a iniciarRecorrido o si ya no quedan entregas.
     */
    public ParadaEntrega calcularSiguienteEntrega() {
        if (!recorridoIniciado) {
            throw new IllegalStateException("No hay recorrido iniciado: llame primero a iniciarRecorrido.");
        }
        if (cantidadSinEntregar == 0) {
            throw new IllegalStateException("No quedan entregas pendientes en este recorrido.");
        }

        // 1) Elegir el pendiente con menor tiempo. Con "<" estricto, en empate gana el primero del arreglo.
        int elegido = -1;
        for (int k = 0; k < cantidadPendientes; k++) {
            if (entregado[k]) {
                continue;
            }
            int distancia = distanciasDesdeActual.getDistancia(destinosPendientes[k]);
            if (elegido == -1 || distancia < distanciasDesdeActual.getDistancia(destinosPendientes[elegido])) {
                elegido = k;
            }
        }
        int destino = destinosPendientes[elegido];
        int tramo = distanciasDesdeActual.getDistancia(destino);
        String[] camino = distanciasDesdeActual.getCamino(destino);

        // 2) Entregar todos los pedidos que van a ese mismo destino (en orden de llegada)
        int cantidadEnParada = 0;
        for (int k = 0; k < cantidadPendientes; k++) {
            if (!entregado[k] && destinosPendientes[k] == destino) {
                cantidadEnParada++;
            }
        }
        String[] idsEnParada = new String[cantidadEnParada];
        int pos = 0;
        for (int k = 0; k < cantidadPendientes; k++) {
            if (!entregado[k] && destinosPendientes[k] == destino) {
                entregado[k] = true;
                idsEnParada[pos] = idsPendientes[k];
                pos++;
            }
        }
        cantidadSinEntregar -= cantidadEnParada;
        pedidosEntregados += cantidadEnParada;

        // 3) Registrar la parada y mover el vehículo
        String desde = grafo.getNombre(ubicacionActual);
        tiempoAcumulado += tramo;
        ParadaEntrega parada = new ParadaEntrega(cantidadParadas + 1, desde, grafo.getNombre(destino),
                camino, tramo, tiempoAcumulado, idsEnParada);
        paradas[cantidadParadas] = parada;
        cantidadParadas++;
        ubicacionActual = destino;

        // 4) Volver a ejecutar Dijkstra desde la nueva ubicación (solo si queda algo por entregar)
        if (cantidadSinEntregar > 0) {
            distanciasDesdeActual = Dijkstra.calcular(grafo, ubicacionActual);
        }
        return parada;
    }

    /** Nombre de la ubicación donde está el vehículo (la sede si aún no ha salido). */
    public String getUbicacionActual() {
        return grafo.getNombre(ubicacionActual);
    }

    /** Minutos recorridos desde la sede hasta la ubicación actual. */
    public int getTiempoAcumulado() {
        return tiempoAcumulado;
    }

    /**
     * Recorrido con lo hecho hasta el momento (completo si ya no hay pendientes).
     *
     * @throws IllegalStateException si no se llamó a iniciarRecorrido.
     */
    public RecorridoEntrega obtenerRecorrido() {
        if (!recorridoIniciado) {
            throw new IllegalStateException("No hay recorrido iniciado: llame primero a iniciarRecorrido.");
        }
        ParadaEntrega[] hechas = new ParadaEntrega[cantidadParadas];
        for (int i = 0; i < cantidadParadas; i++) {
            hechas[i] = paradas[i];
        }
        return new RecorridoEntrega(getSede(), horaSalida, hechas, tiempoAcumulado, pedidosEntregados,
                recortar(idsNoAlcanzables, cantidadNoAlcanzables),
                recortar(explicacionesNoAlcanzables, cantidadNoAlcanzables));
    }

    // ------------------------------------------------------------------
    // Modo completo
    // ------------------------------------------------------------------

    /** Planifica todo el recorrido de una vez, reutilizando el modo paso a paso. */
    public RecorridoEntrega planificarRecorridoCompleto(SolicitudEntrega[] pendientes, String horaSalida) {
        iniciarRecorrido(pendientes, horaSalida);
        while (hayEntregasPendientes()) {
            calcularSiguienteEntrega();
        }
        return obtenerRecorrido();
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Revisa nulos e ids repetidos. Los ids se comparan sin espacios y sin importar mayúsculas. */
    private static void validarPendientes(SolicitudEntrega[] pendientes, int total) {
        for (int k = 0; k < total; k++) {
            SolicitudEntrega s = pendientes[k];
            if (s == null) {
                throw new GrafoException("El pedido pendiente #" + (k + 1) + " es nulo.");
            }
            if (s.getIdPedido() == null || s.getIdPedido().isBlank()) {
                throw new GrafoException("El pedido pendiente #" + (k + 1) + " no tiene idPedido.");
            }
            if (s.getDestino() == null) {
                throw new GrafoException("El pedido '" + s.getIdPedido() + "' no tiene destino.");
            }
            String id = s.getIdPedido().trim();
            for (int i = 0; i < k; i++) {
                if (pendientes[i].getIdPedido().trim().equalsIgnoreCase(id)) {
                    throw new GrafoException("El idPedido '" + id + "' está repetido en los pendientes.");
                }
            }
        }
    }

    private void agregarNoAlcanzable(String id, String explicacion) {
        idsNoAlcanzables[cantidadNoAlcanzables] = id;
        explicacionesNoAlcanzables[cantidadNoAlcanzables] = explicacion;
        cantidadNoAlcanzables++;
    }

    private static String[] recortar(String[] arreglo, int cantidad) {
        String[] copia = new String[cantidad];
        for (int i = 0; i < cantidad; i++) {
            copia[i] = arreglo[i];
        }
        return copia;
    }
}
