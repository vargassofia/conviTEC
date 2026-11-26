package cr.ac.tec.convitec.web;

import cr.ac.tec.convitec.rutas.SalidasDiarias;
import cr.ac.tec.convitec.rutas.SolicitudEntrega;

// TODO PERSONA 2: cuando exista el gestor de pedidos, implementar FuentePendientes con los pedidos reales
//   (aceptados y no entregados de cada salida) y pasarlo a ServidorWeb en app/Main en lugar de esta clase.
/**
 * *** PROVISIONAL / DEMOSTRACIÓN ***
 * Pedidos pendientes de prueba para cada sede, para poder usar la pantalla de Entregas antes de que
 * exista el módulo de pedidos. Incluyen pedidos al mismo destino (se entregan juntos) y, en Liberia,
 * uno a un destino inalcanzable (Cartago), para mostrar cómo se explica.
 */
public class PendientesDemo implements FuentePendientes {

    private final String sede;

    /** @param nombreSede nombre de la sede en el grafo ("San José"). */
    public PendientesDemo(String nombreSede) {
        this.sede = CargadorUbicaciones.normalizar(nombreSede);
    }

    @Override
    public SolicitudEntrega[] pendientesDeSalida(String horaSalida) {
        boolean manana = SalidasDiarias.SALIDA_MANANA.equals(horaSalida);
        switch (sede) {
            case "san jose":
                return manana
                        ? pedidos("P101", "Limón", "P102", "Heredia", "P103", "Cartago", "P104", "Heredia")
                        : pedidos("P105", "Guápiles", "P106", "Alajuela", "P107", "Paraíso");
            case "alajuela":
                return manana
                        ? pedidos("P201", "Puntarenas", "P202", "Orotina", "P203", "Heredia")
                        : pedidos("P204", "San José", "P205", "Cartago", "P206", "Alajuela");
            case "limon":
                return manana
                        ? pedidos("P301", "Siquirres", "P302", "Turrialba", "P303", "Guápiles", "P304", "Cartago")
                        : pedidos("P305", "Limón", "P306", "Paraíso");
            case "liberia":
                return manana
                        ? pedidos("P401", "Cañas", "P402", "Liberia", "P403", "Cartago")
                        : pedidos("P404", "Cañas");
            default:
                return new SolicitudEntrega[0];
        }
    }

    @Override
    public boolean esDemostracion() {
        return true;
    }

    /** pedidos("P1", "Heredia", "P2", "Cartago") -> [P1 -> Heredia, P2 -> Cartago]. */
    private static SolicitudEntrega[] pedidos(String... idYDestino) {
        SolicitudEntrega[] lista = new SolicitudEntrega[idYDestino.length / 2];
        for (int i = 0; i < lista.length; i++) {
            lista[i] = new SolicitudEntrega(idYDestino[2 * i], idYDestino[2 * i + 1]);
        }
        return lista;
    }
}
