package cr.ac.tec.convitec.servicios;

import cr.ac.tec.convitec.grafo.ResultadoRuta;
import cr.ac.tec.convitec.rutas.ParadaEntrega;
import cr.ac.tec.convitec.rutas.RecorridoEntrega;
import cr.ac.tec.convitec.rutas.SolicitudEntrega;

/**
 * Servicio de rutas de una sede. Implementación: cr.ac.tec.convitec.rutas.ServicioRutas.
 */
public interface IServicioRutas {

    // Consultas de rutas (Dijkstra)
    boolean existeRuta(String origen, String destino);
    ResultadoRuta caminoMasCorto(String origen, String destino); // tiempo + camino

    // Recorrido de entregas paso a paso
    void iniciarRecorrido(SolicitudEntrega[] pendientes, String horaSalida); // "11:00" o "15:00"
    boolean hayEntregasPendientes();
    ParadaEntrega calcularSiguienteEntrega();   // una parada a la vez
    RecorridoEntrega obtenerRecorrido();        // lo hecho hasta el momento

    // Recorrido de entregas completo
    RecorridoEntrega planificarRecorridoCompleto(SolicitudEntrega[] pendientes, String horaSalida);
}
