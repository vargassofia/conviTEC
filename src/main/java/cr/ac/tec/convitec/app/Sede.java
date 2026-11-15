package cr.ac.tec.convitec.app;

import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.demo.CatalogoGlobalDemo;
import cr.ac.tec.convitec.demo.InventarioDemo;
import cr.ac.tec.convitec.entregas.RepositorioVentasMemoria;
import cr.ac.tec.convitec.estadisticas.ServicioEstadisticas;
import cr.ac.tec.convitec.grafo.CargadorGrafo;
import cr.ac.tec.convitec.grafo.Grafo;
import cr.ac.tec.convitec.grafo.GrafoException;
import cr.ac.tec.convitec.mensajeria.MensajeriaRabbit;
import cr.ac.tec.convitec.rutas.ServicioRutas;
import cr.ac.tec.convitec.servicios.ICatalogoGlobal;
import cr.ac.tec.convitec.servicios.IInventarioLocal;
import cr.ac.tec.convitec.servicios.IMensajeria;
import cr.ac.tec.convitec.servicios.IRepositorioVentas;

/**
 * Todo lo de UNA sede, armado a partir de su ConfigSede: grafo y rutas, ventas, estadísticas,
 * inventario, catálogo global y mensajería.
 *
 * Las dependencias de la Persona 2 (inventario, catálogo, repositorio de ventas) se reciben por
 * constructor. Mientras no existan, se usa conDemostracion(config), que arma la sede con las
 * clases PROVISIONALES del paquete demo.
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

    private boolean iniciada;
    private boolean cerrada;

    /**
     * Sede con las clases de DEMOSTRACIÓN (provisionales) y mensajería RabbitMQ real.
     * Es la que usa Main.
     */
    public static Sede conDemostracion(ConfigSede config) {
        return conDemostracion(config, null);
    }

    /**
     * Arma la sede con sus dependencias. Aquí están las ÚNICAS líneas que la Persona 2 debe
     * cambiar para conectar sus clases reales (marcadas con comentarios TODO para la Persona 2).
     * SedeArranqueTest usa este mismo método: si deja de pasar, el cambio rompió el arranque.
     *
     * @param mensajeria null = MensajeriaRabbit real; las pruebas pasan una falsa (SedeArranqueTest).
     */
    static Sede conDemostracion(ConfigSede config, IMensajeria mensajeria) {
        if (config == null) {
            throw new IllegalArgumentException("La sede necesita su configuración.");
        }

        // TODO PERSONA 2: implementar IInventarioLocal (servicios) con su inventario real, guardado en archivos
        //   dentro de config.getCarpetaDatos(), y reemplazar aquí la clase de demo InventarioDemo.
        //   Debe ser synchronized y confirmar/liberar deben ser idempotentes (ver IInventarioLocal).
        IInventarioLocal inventario = InventarioDemo.paraSede(config.getCodigo());

        // TODO PERSONA 2: implementar ICatalogoGlobal (servicios) con su catálogo global (AVL propio)
        //   y reemplazar aquí la clase de demo CatalogoGlobalDemo. Debe ser synchronized.
        ICatalogoGlobal catalogo = new CatalogoGlobalDemo();

        // TODO PERSONA 2: implementar IRepositorioVentas (servicios) guardando las ventas en archivos
        //   dentro de config.getCarpetaDatos(), y reemplazar aquí RepositorioVentasMemoria.
        IRepositorioVentas repositorioVentas = new RepositorioVentasMemoria();

        // TODO PERSONA 2: implementar IGestorPedidos (servicios) con su registro de pedidos. Cuando exista,
        //   agregarlo como dependencia de Sede y crear con él el servicio de entregas:
        //   new ServicioEntregas(config.getNombre(), gestorPedidos, repositorioVentas)  (paquete entregas).
        //   Hoy no hay gestor, por eso la sede todavía no registra entregas como ventas.

        if (mensajeria == null) {
            return new Sede(config, inventario, catalogo, repositorioVentas);
        }
        return new Sede(config, inventario, catalogo, repositorioVentas, mensajeria);
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
        if (config == null || inventario == null || catalogo == null || repositorioVentas == null
                || mensajeria == null) {
            throw new IllegalArgumentException("La sede necesita configuración, inventario, catálogo, "
                    + "repositorio de ventas y mensajería (ninguno puede ser nulo).");
        }
        this.config = config;
        this.grafo = CargadorGrafo.cargarDesdeRecurso("rutas.json");
        if (!grafo.existeVertice(config.getNombre())) {
            throw new GrafoException("La sede '" + config.getNombre() + "' (sede.nombre) no existe en el mapa "
                    + "de rutas. Revise el archivo de configuración.");
        }
        this.servicioRutas = new ServicioRutas(grafo, config.getNombre());
        this.inventario = inventario;
        this.catalogo = catalogo;
        this.repositorioVentas = repositorioVentas;
        this.estadisticas = new ServicioEstadisticas(config.getNombre(), repositorioVentas);
        this.mensajeria = mensajeria;
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
}
