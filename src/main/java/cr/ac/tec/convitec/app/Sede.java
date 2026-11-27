package cr.ac.tec.convitec.app;

import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.catalogo.CatalogoGlobal;
import cr.ac.tec.convitec.catalogo.InventarioLocal;
import cr.ac.tec.convitec.catalogo.ServicioInventario;
import cr.ac.tec.convitec.entregas.ServicioEntregas;
import cr.ac.tec.convitec.estadisticas.ServicioEstadisticas;
import cr.ac.tec.convitec.grafo.CargadorGrafo;
import cr.ac.tec.convitec.grafo.Grafo;
import cr.ac.tec.convitec.grafo.GrafoException;
import cr.ac.tec.convitec.mensajeria.MensajeriaRabbit;
import cr.ac.tec.convitec.persistencia.PersistenciaSede;
import cr.ac.tec.convitec.persistencia.RecuperacionSede;
import cr.ac.tec.convitec.persistencia.ResultadoRecuperacion;
import cr.ac.tec.convitec.pedidos.ServicioPedidos;
import cr.ac.tec.convitec.rutas.ServicioRutas;
import cr.ac.tec.convitec.servicios.ICatalogoGlobal;
import cr.ac.tec.convitec.servicios.IInventarioLocal;
import cr.ac.tec.convitec.servicios.IMensajeria;
import cr.ac.tec.convitec.servicios.IRepositorioVentas;

/**
 * Todo lo de UNA sede, armado a partir de su ConfigSede: grafo y rutas, ventas, estadísticas,
 * inventario, catálogo global y mensajería.
 *
 * La sede carga su inventario, catálogo, pedidos y ventas desde la carpeta local configurada.
 *
 * Uso:
 *   Sede sede = Sede.conDemostracion(config);
 *   sede.iniciar();   // conecta a RabbitMQ
 *   ...
 *   sede.cerrar();    // seguro aunque se llame dos veces
 */
public class Sede {

    private final ConfigSede config;
    private final Grafo grafo;
    private final ServicioRutas servicioRutas;
    private final IInventarioLocal inventario;
    private final ICatalogoGlobal catalogo;
    private final IRepositorioVentas repositorioVentas;
    private final ServicioEstadisticas estadisticas;
    private final IMensajeria mensajeria;
    private final ServicioInventario servicioInventario;
    private final ServicioPedidos servicioPedidos;
    private final ServicioEntregas servicioEntregas;
    private final ResultadoRecuperacion recuperacion;

    private boolean iniciada;
    private boolean cerrada;

    /**
     * Recupera una sede desde sus archivos locales; usa RabbitMQ real si no se inyecta mensajería.
     */
    public static Sede conDemostracion(ConfigSede config) {
        return conDemostracion(config, null);
    }

    /**
     * Arma la sede recuperando el estado local antes de habilitar RabbitMQ.
     *
     * @param mensajeria null = MensajeriaRabbit real; las pruebas pasan una falsa (SedeArranqueTest).
     */
    static Sede conDemostracion(ConfigSede config, IMensajeria mensajeria) {
        if (config == null) {
            throw new IllegalArgumentException("La sede necesita su configuración.");
        }

        Grafo grafo = CargadorGrafo.cargarDesdeRecurso("rutas.json");
        ServicioRutas rutas = new ServicioRutas(grafo, config.getNombre());
        PersistenciaSede persistencia = new PersistenciaSede(config.getCarpetaDatos());
        ResultadoRecuperacion recuperacion = new RecuperacionSede(persistencia)
                .recuperar(config, rutas, mensajeria);
        return new Sede(config, grafo, rutas, recuperacion);
    }

    /** Sede con mensajería RabbitMQ real. */
    public Sede(ConfigSede config, IInventarioLocal inventario, ICatalogoGlobal catalogo,
                IRepositorioVentas repositorioVentas) {
        this(config, inventario, catalogo, repositorioVentas,
                config == null ? null : new MensajeriaRabbit(config, inventario, catalogo));
    }

    /**
     * Sede con otra mensajería (por ejemplo una falsa en las pruebas, sin RabbitMQ).
     *
     * @throws IllegalArgumentException si algo es nulo.
     * @throws GrafoException           si el nombre de la sede no existe en el grafo de rutas.
     */
    public Sede(ConfigSede config, IInventarioLocal inventario, ICatalogoGlobal catalogo,
                IRepositorioVentas repositorioVentas, IMensajeria mensajeria) {
        this(config, null, null, inventario, catalogo, repositorioVentas, mensajeria, null);
    }

    private Sede(ConfigSede config, Grafo grafo, ServicioRutas rutas, ResultadoRecuperacion recuperacion) {
        this(config, grafo, rutas, recuperacion.getInventario(), recuperacion.getCatalogo(),
                recuperacion.getRepositorioVentas(), recuperacion.getMensajeria(), recuperacion);
    }

    private Sede(ConfigSede config, Grafo grafoPreparado, ServicioRutas rutasPreparadas,
                 IInventarioLocal inventario, ICatalogoGlobal catalogo,
                 IRepositorioVentas repositorioVentas, IMensajeria mensajeria,
                 ResultadoRecuperacion recuperacion) {
        if (config == null || inventario == null || catalogo == null || repositorioVentas == null
                || mensajeria == null) {
            throw new IllegalArgumentException("La sede necesita configuración, inventario, catálogo, "
                    + "repositorio de ventas y mensajería (ninguno puede ser nulo).");
        }
        this.config = config;
        this.grafo = grafoPreparado == null ? CargadorGrafo.cargarDesdeRecurso("rutas.json") : grafoPreparado;
        if (!grafo.existeVertice(config.getNombre())) {
            throw new GrafoException("La sede '" + config.getNombre() + "' (sede.nombre) no existe en el mapa "
                    + "de rutas. Revise el archivo de configuración.");
        }
        this.servicioRutas = rutasPreparadas == null
                ? new ServicioRutas(grafo, config.getNombre()) : rutasPreparadas;
        this.inventario = inventario;
        this.catalogo = catalogo;
        this.repositorioVentas = repositorioVentas;
        this.estadisticas = new ServicioEstadisticas(config.getNombre(), repositorioVentas);
        this.mensajeria = mensajeria;
        this.recuperacion = recuperacion;
        this.servicioInventario = inventario instanceof InventarioLocal inventarioLocal
                && catalogo instanceof CatalogoGlobal catalogoGlobal
                ? new ServicioInventario(inventarioLocal, catalogoGlobal, mensajeria) : null;
        this.servicioPedidos = recuperacion == null ? null : recuperacion.getServicioPedidos();
        this.servicioEntregas = servicioPedidos == null ? null
                : new ServicioEntregas(config.getNombre(), servicioPedidos, repositorioVentas);
    }

    /**
     * Conecta la mensajería. Lanza MensajeriaException si el broker no está disponible.
     *
     * @throws IllegalStateException si la sede ya se cerró.
     */
    public synchronized void iniciar() {
        if (cerrada) {
            throw new IllegalStateException("La sede " + config.getCodigo() + " ya se cerró.");
        }
        if (!iniciada) {
            mensajeria.iniciar();
            iniciada = true;
            if (recuperacion != null) {
                for (cr.ac.tec.convitec.persistencia.LiberacionPendiente liberacion
                        : recuperacion.getLiberacionesPendientes()) {
                    mensajeria.liberarReserva(liberacion.getCodigoSede(), liberacion.getIdPedido(),
                            liberacion.getCodigoProducto());
                }
            }
        }
    }

    /** Cierra la mensajería. Se puede llamar varias veces (por ejemplo, desde el shutdown hook). */
    public synchronized void cerrar() {
        if (cerrada) {
            return;
        }
        cerrada = true;
        iniciada = false;
        mensajeria.cerrar();
    }

    public synchronized boolean estaIniciada() {
        return iniciada;
    }

    // ------------------------------------------------------------------
    // Getters (para la consola y la interfaz web)
    // ------------------------------------------------------------------

    public ConfigSede getConfig() {
        return config;
    }

    public String getCodigo() {
        return config.getCodigo();
    }

    /** Nombre de la sede en el grafo ("Alajuela"). */
    public String getNombre() {
        return config.getNombre();
    }

    public Grafo getGrafo() {
        return grafo;
    }

    public ServicioRutas getServicioRutas() {
        return servicioRutas;
    }

    public IInventarioLocal getInventario() {
        return inventario;
    }

    public ICatalogoGlobal getCatalogo() {
        return catalogo;
    }

    public IRepositorioVentas getRepositorioVentas() {
        return repositorioVentas;
    }

    public ServicioEstadisticas getEstadisticas() {
        return estadisticas;
    }

    public IMensajeria getMensajeria() {
        return mensajeria;
    }

    public ServicioInventario getServicioInventario() {
        return servicioInventario;
    }

    public ServicioPedidos getServicioPedidos() {
        return servicioPedidos;
    }

    public ServicioEntregas getServicioEntregas() {
        return servicioEntregas;
    }

    public ResultadoRecuperacion getRecuperacion() {
        return recuperacion;
    }
}
