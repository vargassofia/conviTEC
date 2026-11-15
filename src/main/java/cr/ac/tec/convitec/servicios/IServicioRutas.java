package cr.ac.tec.convitec.servicios;

import cr.ac.tec.convitec.grafo.ResultadoRuta;
import cr.ac.tec.convitec.rutas.ParadaEntrega;
import cr.ac.tec.convitec.rutas.RecorridoEntrega;

public interface IServicioRutas {
    boolean existeRuta(String origen, String destino);
    ResultadoRuta caminoMasCorto(String origen, String destino); // tiempo + camino
    ParadaEntrega calcularSiguienteEntrega();   // paso a paso
    RecorridoEntrega planificarRecorridoCompleto();
}
