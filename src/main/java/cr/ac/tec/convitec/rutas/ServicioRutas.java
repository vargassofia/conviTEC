package cr.ac.tec.convitec.rutas;

import cr.ac.tec.convitec.grafo.Dijkstra;
import cr.ac.tec.convitec.grafo.Grafo;
import cr.ac.tec.convitec.grafo.ResultadoRuta;
import cr.ac.tec.convitec.servicios.IServicioRutas;

/**
 * Implementación de IServicioRutas para una sede.
 * No tiene lógica propia: delega en Dijkstra (consultas de rutas)
 * y en PlanificadorEntregas (recorridos de entrega).
 */
public class ServicioRutas implements IServicioRutas {

    private final Grafo grafo;
    private final PlanificadorEntregas planificador;

    /**
     * @throws cr.ac.tec.convitec.grafo.GrafoException si el grafo es nulo/vacío o la sede no existe.
     */
    public ServicioRutas(Grafo grafo, String sede) {
        this.planificador = new PlanificadorEntregas(grafo, sede);
        this.grafo = grafo;
    }

    @Override
    public boolean existeRuta(String origen, String destino) {
        return Dijkstra.existeRuta(grafo, origen, destino);
    }

    @Override
    public ResultadoRuta caminoMasCorto(String origen, String destino) {
        return Dijkstra.caminoMasCorto(grafo, origen, destino);
    }

    @Override
    public void iniciarRecorrido(SolicitudEntrega[] pendientes, String horaSalida) {
        planificador.iniciarRecorrido(pendientes, horaSalida);
    }

    @Override
    public boolean hayEntregasPendientes() {
        return planificador.hayEntregasPendientes();
    }

    @Override
    public ParadaEntrega calcularSiguienteEntrega() {
        return planificador.calcularSiguienteEntrega();
    }

    @Override
    public RecorridoEntrega obtenerRecorrido() {
        return planificador.obtenerRecorrido();
    }

    @Override
    public RecorridoEntrega planificarRecorridoCompleto(SolicitudEntrega[] pendientes, String horaSalida) {
        return planificador.planificarRecorridoCompleto(pendientes, horaSalida);
    }
}
