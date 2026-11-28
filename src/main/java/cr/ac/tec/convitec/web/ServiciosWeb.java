package cr.ac.tec.convitec.web;

import cr.ac.tec.convitec.catalogo.ServicioInventario;
import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.entregas.ServicioEntregas;
import cr.ac.tec.convitec.grafo.Grafo;
import cr.ac.tec.convitec.pedidos.ServicioPedidos;
import cr.ac.tec.convitec.rutas.ServicioRutas;
import cr.ac.tec.convitec.servicios.IServicioEstadisticas;

/**
 * Los servicios de UNA sede que usa la interfaz web. Se arma una sola vez al arrancar
 * (app/Main, con los getters de Sede) y se le pasa a ServidorWeb.
 *
 * La web solo llama a estos servicios: nunca lee archivos ni estructuras de otra sede.
 */
public final class ServiciosWeb {

    private final ConfigSede config;
    private final Grafo grafo;
    private final ServicioRutas rutas;
    private final FuentePendientes pendientes;
    private final ServicioEntregas entregas;
    private final ServicioInventario inventario;
    private final ServicioPedidos pedidos;
    private final IServicioEstadisticas estadisticas;

    /**
     * @throws IllegalArgumentException si falta alguno.
     */
    public ServiciosWeb(ConfigSede config, Grafo grafo, ServicioRutas rutas, FuentePendientes pendientes,
                        ServicioEntregas entregas, ServicioInventario inventario, ServicioPedidos pedidos,
                        IServicioEstadisticas estadisticas) {
        if (config == null || grafo == null || rutas == null || pendientes == null || entregas == null
                || inventario == null || pedidos == null || estadisticas == null) {
            throw new IllegalArgumentException("La interfaz web necesita la configuración, el grafo, las rutas, "
                    + "los pendientes, las entregas, el inventario, los pedidos y las estadísticas de la sede "
                    + "(ninguno puede faltar).");
        }
        this.config = config;
        this.grafo = grafo;
        this.rutas = rutas;
        this.pendientes = pendientes;
        this.entregas = entregas;
        this.inventario = inventario;
        this.pedidos = pedidos;
        this.estadisticas = estadisticas;
    }

    public ConfigSede getConfig() {
        return config;
    }

    public Grafo getGrafo() {
        return grafo;
    }

    public ServicioRutas getRutas() {
        return rutas;
    }

    public FuentePendientes getPendientes() {
        return pendientes;
    }

    public ServicioEntregas getEntregas() {
        return entregas;
    }

    public ServicioInventario getInventario() {
        return inventario;
    }

    public ServicioPedidos getPedidos() {
        return pedidos;
    }

    public IServicioEstadisticas getEstadisticas() {
        return estadisticas;
    }
}
