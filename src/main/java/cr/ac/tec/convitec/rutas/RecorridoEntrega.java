package cr.ac.tec.convitec.rutas;

/**
 * Recorrido de entregas de una salida (11:00 o 15:00) de una sede.
 *
 * - paradas: en el orden en que se hicieron.
 * - tiempoTotal: minutos desde la sede hasta la ÚLTIMA entrega (el vehículo no regresa a la sede).
 * - idsNoAlcanzables / explicacionesNoAlcanzables: pedidos que no se pudieron entregar,
 *   en la misma posición (explicacionesNoAlcanzables[i] explica idsNoAlcanzables[i]).
 *
 * Si se pide en modo paso a paso antes de terminar, contiene lo hecho hasta ese momento.
 * Es inmutable: los getters de arreglos devuelven copias.
 */
public final class RecorridoEntrega {

    private final String sede;
    private final String horaSalida;
    private final ParadaEntrega[] paradas;
    private final int tiempoTotal;
    private final int cantidadPedidosEntregados;
    private final String[] idsNoAlcanzables;
    private final String[] explicacionesNoAlcanzables;

    RecorridoEntrega(String sede, String horaSalida, ParadaEntrega[] paradas, int tiempoTotal,
                     int cantidadPedidosEntregados, String[] idsNoAlcanzables,
                     String[] explicacionesNoAlcanzables) {
        this.sede = sede;
        this.horaSalida = horaSalida;
        this.paradas = copiarParadas(paradas);
        this.tiempoTotal = tiempoTotal;
        this.cantidadPedidosEntregados = cantidadPedidosEntregados;
        this.idsNoAlcanzables = ParadaEntrega.copiar(idsNoAlcanzables);
        this.explicacionesNoAlcanzables = ParadaEntrega.copiar(explicacionesNoAlcanzables);
    }

    public String getSede() {
        return sede;
    }

    public String getHoraSalida() {
        return horaSalida;
    }

    public ParadaEntrega[] getParadas() {
        return copiarParadas(paradas);
    }

    public int getCantidadParadas() {
        return paradas.length;
    }

    public int getTiempoTotal() {
        return tiempoTotal;
    }

    public int getCantidadPedidosEntregados() {
        return cantidadPedidosEntregados;
    }

    public String[] getIdsNoAlcanzables() {
        return ParadaEntrega.copiar(idsNoAlcanzables);
    }

    public String[] getExplicacionesNoAlcanzables() {
        return ParadaEntrega.copiar(explicacionesNoAlcanzables);
    }

    public int getCantidadNoAlcanzables() {
        return idsNoAlcanzables.length;
    }

    /**
     * Ejemplo:
     * Recorrido desde San José (salida 11:00)
     *   1. San José -> Heredia | 20 min (acumulado 20) | pedidos: P2, P4
     *   ...
     * Tiempo total: 220 min | pedidos entregados: 4
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Recorrido desde ").append(sede).append(" (salida ").append(horaSalida).append(")\n");
        if (paradas.length == 0) {
            sb.append("  Sin paradas\n");
        }
        for (int i = 0; i < paradas.length; i++) {
            sb.append("  ").append(paradas[i]).append('\n');
        }
        sb.append("Tiempo total: ").append(tiempoTotal).append(" min | pedidos entregados: ")
                .append(cantidadPedidosEntregados);
        if (idsNoAlcanzables.length > 0) {
            sb.append("\nNo alcanzables:");
            for (int i = 0; i < idsNoAlcanzables.length; i++) {
                sb.append("\n  - ").append(idsNoAlcanzables[i]).append(": ").append(explicacionesNoAlcanzables[i]);
            }
        }
        return sb.toString();
    }

    private static ParadaEntrega[] copiarParadas(ParadaEntrega[] original) {
        if (original == null) {
            return new ParadaEntrega[0];
        }
        ParadaEntrega[] copia = new ParadaEntrega[original.length];
        for (int i = 0; i < original.length; i++) {
            copia[i] = original[i];
        }
        return copia;
    }
}
