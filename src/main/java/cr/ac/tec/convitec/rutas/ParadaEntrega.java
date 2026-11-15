package cr.ac.tec.convitec.rutas;

/**
 * Una parada del recorrido de entregas.
 *
 * - numero: 1, 2, 3... en el orden en que se visita.
 * - desde: ubicación donde estaba el vehículo antes de esta parada.
 * - destino: ubicación donde se entregan los pedidos.
 * - camino: ubicaciones recorridas desde "desde" hasta "destino" (puede pasar por puntos sin pedidos).
 * - tiempoTramo: minutos de este tramo.
 * - tiempoAcumulado: minutos desde que salió de la sede hasta esta parada. Es el
 *   "tiempo de traslado" de los pedidos entregados aquí (se usa para el rendimiento: monto ÷ tiempo).
 * - idsPedidos: todos los pedidos entregados en esta parada (varios si van al mismo destino).
 *
 * Es inmutable: los getters de arreglos devuelven copias.
 */
public final class ParadaEntrega {

    private final int numero;
    private final String desde;
    private final String destino;
    private final String[] camino;
    private final int tiempoTramo;
    private final int tiempoAcumulado;
    private final String[] idsPedidos;

    ParadaEntrega(int numero, String desde, String destino, String[] camino,
                  int tiempoTramo, int tiempoAcumulado, String[] idsPedidos) {
        this.numero = numero;
        this.desde = desde;
        this.destino = destino;
        this.camino = copiar(camino);
        this.tiempoTramo = tiempoTramo;
        this.tiempoAcumulado = tiempoAcumulado;
        this.idsPedidos = copiar(idsPedidos);
    }

    public int getNumero() {
        return numero;
    }

    public String getDesde() {
        return desde;
    }

    public String getDestino() {
        return destino;
    }

    public String[] getCamino() {
        return copiar(camino);
    }

    public int getTiempoTramo() {
        return tiempoTramo;
    }

    public int getTiempoAcumulado() {
        return tiempoAcumulado;
    }

    public String[] getIdsPedidos() {
        return copiar(idsPedidos);
    }

    public int getCantidadPedidos() {
        return idsPedidos.length;
    }

    /** Ejemplo: "2. Heredia -> San José -> Cartago | 65 min (acumulado 85) | pedidos: P3" */
    @Override
    public String toString() {
        return numero + ". " + unir(camino, " -> ")
                + " | " + tiempoTramo + " min (acumulado " + tiempoAcumulado + ")"
                + " | pedidos: " + unir(idsPedidos, ", ");
    }

    static String unir(String[] partes, String separador) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            if (i > 0) {
                sb.append(separador);
            }
            sb.append(partes[i]);
        }
        return sb.toString();
    }

    static String[] copiar(String[] original) {
        if (original == null) {
            return new String[0];
        }
        String[] copia = new String[original.length];
        for (int i = 0; i < original.length; i++) {
            copia[i] = original[i];
        }
        return copia;
    }
}
