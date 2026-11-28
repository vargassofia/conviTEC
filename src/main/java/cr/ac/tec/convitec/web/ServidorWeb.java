package cr.ac.tec.convitec.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import cr.ac.tec.convitec.acceso.ServicioAcceso;
import cr.ac.tec.convitec.config.ConfigSede;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.time.Clock;

/**
 * Servidor HTTP de UNA sede: sirve la interfaz web (archivos de src/main/resources/web) y la API REST.
 *
 *   ServidorWeb web = new ServidorWeb(new ServiciosWeb(config, grafo, rutas, pendientes, ...), acceso);
 *   web.iniciar();                 // escucha en config.getPuertoHttp() (LIB 8081, ALA 8082, SJO 8083, LIM 8084)
 *   web.getUrl();                  // "http://localhost:8082"
 *   ...
 *   web.detener();                 // seguro aunque se llame dos veces
 *
 * API REST (todas las respuestas en JSON; los errores tienen la forma { "estado": 400, "error": "..." }).
 * Todo exige sesión iniciada (401 si no) salvo /api/acceso/... (ver ControladorAcceso y FiltroSesion),
 * y todo POST exige el encabezado "X-ConviTEC: 1" (403 si no):
 *   GET  /api/acceso                     (público) sede, las 4 sedes, si falta la primera cuenta y la sesión
 *   POST /api/acceso/entrar              (público) { "usuario": "ana", "clave": "..." }
 *   POST /api/acceso/salir               (público) cierra la sesión
 *   POST /api/acceso/codigo              (público) primera cuenta o recuperar acceso con el código de acceso
 *   GET  /api/cuentas  POST /api/cuentas  POST /api/cuentas/estado  POST /api/cuentas/clave   sección Cuentas
 *   GET  /api/sede                       qué sede es y la próxima salida
 *   GET  /api/resumen                    números del día para la pantalla de Inicio
 *   GET  /api/grafo                      ubicaciones (con coordenadas) y rutas, para el mapa
 *   GET  /api/ruta?destino=X             ruta más corta desde esta sede (Dijkstra)
 *   GET  /api/entregas                   estado de la salida de entregas actual
 *   POST /api/entregas/iniciar           { "horaSalida": "11:00" }: empieza la salida con sus pendientes
 *   POST /api/entregas/siguiente         hace la siguiente parada y registra sus ventas
 *   POST /api/entregas/vista-previa      { "horaSalida": "11:00" }: el recorrido completo, sin entregar nada
 *   GET  /api/inventario                 productos de esta sede (disponible y reservado)
 *   POST /api/inventario/agregar         { "codigoProducto": "C01", "cantidad": 10 }
 *   GET  /api/catalogo                   catálogo global con la cantidad en cada sede
 *   GET  /api/pedidos?estado=...         pendientes, todos, o un estado (EN_PROCESO, CONFIRMADO, CANCELADO, ENTREGADO)
 *   POST /api/pedidos                    { "destino": "Heredia", "lineas": [ { "codigoProducto": "C01", "cantidad": 2 } ] }
 *   GET  /api/estadisticas?desde=&hasta=&peores=   historial de ventas, producto estrella, ranking y peores pedidos
 *
 * Usa com.sun.net.httpserver, que viene en el JDK (sin dependencias nuevas).
 *
 * Hilos: el servidor atiende las peticiones de UNA en UNA, en su propio hilo (executor por defecto).
 * Así los servicios con estado, como el recorrido paso a paso del planificador de entregas, nunca
 * reciben dos peticiones al mismo tiempo, y la consola de la sede sigue funcionando en el hilo principal.
 *
 * Cada sede corre su propio servidor: la página solo muestra los registros de esa sede.
 */
public class ServidorWeb {

    /** Carpeta dentro de src/main/resources con los archivos de la interfaz. */
    static final String CARPETA_WEB = "web";

    /** Coordenadas de las ubicaciones para el mapa (src/main/resources). */
    static final String ARCHIVO_UBICACIONES = "ubicaciones.json";

    private final ConfigSede config;
    private final int puertoSolicitado;
    private final Clock reloj;
    private final MapaDTO mapa;
    private final ControladorEntregas entregas;
    private final ControladorInventario inventario;
    private final ControladorPedidos pedidos;
    private final ControladorEstadisticas estadisticas;
    private final ControladorAcceso accesoWeb;
    private final FiltroSesion conSesion;
    private final FiltroSesion sinSesion;

    private HttpServer servidor;

    /**
     * @param servicios los servicios de la sede (ver app/Main)
     * @param acceso    cuentas y sesiones de la sede
     * @throws IllegalArgumentException si falta alguno.
     * @throws cr.ac.tec.convitec.grafo.GrafoException si la sede no existe en el grafo.
     */
    public ServidorWeb(ServiciosWeb servicios, ServicioAcceso acceso) {
        this(servicios, acceso, servicios == null ? 0 : servicios.getConfig().getPuertoHttp(),
                Clock.systemDefaultZone());
    }

    /**
     * Con otro puerto y otro reloj (las pruebas usan el puerto 0, que elige uno libre, y un reloj fijo).
     */
    ServidorWeb(ServiciosWeb servicios, ServicioAcceso acceso, int puerto, Clock reloj) {
        if (servicios == null) {
            throw new IllegalArgumentException("El servidor web necesita los servicios de la sede.");
        }
        if (acceso == null) {
            throw new IllegalArgumentException("El servidor web necesita el servicio de acceso (cuentas y sesiones).");
        }
        if (puerto < 0 || puerto > 65535) {
            throw new IllegalArgumentException("Puerto HTTP inválido: " + puerto + ".");
        }
        if (reloj == null) {
            throw new IllegalArgumentException("El servidor web necesita un reloj.");
        }
        this.config = servicios.getConfig();
        this.puertoSolicitado = puerto;
        this.reloj = reloj;
        this.mapa = MapaDTO.crear(servicios.getGrafo(), cargarCoordenadas(), config.getNombre());
        this.entregas = new ControladorEntregas(servicios.getRutas(), servicios.getEntregas(),
                servicios.getPendientes(), servicios.getGrafo(), mapa.sede, reloj);
        this.inventario = new ControladorInventario(servicios.getInventario(), config.getCodigo());
        this.pedidos = new ControladorPedidos(servicios.getPedidos(), servicios.getGrafo());
        this.estadisticas = new ControladorEstadisticas(servicios.getEstadisticas(), servicios.getPedidos(), reloj);
        this.accesoWeb = new ControladorAcceso(acceso, config);
        this.conSesion = new FiltroSesion(acceso, accesoWeb.getNombreCookie(), true);
        this.sinSesion = new FiltroSesion(acceso, accesoWeb.getNombreCookie(), false);
    }

    /**
     * Abre el puerto y empieza a atender peticiones. Si ya estaba iniciado, no hace nada.
     *
     * @throws ServidorWebException si el puerto está ocupado o no se puede abrir.
     */
    public synchronized void iniciar() {
        if (servidor != null) {
            return;
        }
        HttpServer nuevo;
        try {
            nuevo = HttpServer.create(new InetSocketAddress(puertoSolicitado), 0);
        } catch (BindException e) {
            throw new ServidorWebException("El puerto " + puertoSolicitado + " ya está en uso. ¿Hay otra instancia de la sede "
                    + config.getCodigo() + " (u otro programa) abierta? Ciérrela, o cambie http.puerto en el archivo"
                    + " de configuración.", e);
        } catch (IOException e) {
            throw new ServidorWebException("No se pudo abrir el puerto " + puertoSolicitado + " para la interfaz web: "
                    + e.getMessage(), e);
        }

        // API REST. Para cada petición gana el contexto más largo que coincida: /api/sede antes que /api/
        publica(nuevo, "/api/acceso", ManejadorApi.de("/api/acceso", accesoWeb::estado, "GET", "HEAD"));
        publica(nuevo, "/api/acceso/entrar", ManejadorApi.de("/api/acceso/entrar", accesoWeb::entrar, "POST"));
        publica(nuevo, "/api/acceso/salir", ManejadorApi.de("/api/acceso/salir", accesoWeb::salir, "POST"));
        publica(nuevo, "/api/acceso/codigo", ManejadorApi.de("/api/acceso/codigo", accesoWeb::usarCodigo, "POST"));

        protegida(nuevo, "/api/cuentas", ManejadorApi.de("/api/cuentas", accesoWeb::cuentas, "GET", "HEAD", "POST"));
        protegida(nuevo, "/api/cuentas/estado", ManejadorApi.de("/api/cuentas/estado", accesoWeb::cambiarEstado,
                "POST"));
        protegida(nuevo, "/api/cuentas/clave", ManejadorApi.de("/api/cuentas/clave", accesoWeb::cambiarClave, "POST"));

        protegida(nuevo, "/api/sede", new ApiSede(config, reloj));
        protegida(nuevo, "/api/resumen", ManejadorApi.de("/api/resumen", x -> estadisticas.resumen(), "GET", "HEAD"));
        protegida(nuevo, "/api/grafo", ManejadorApi.de("/api/grafo", x -> mapa, "GET", "HEAD"));
        protegida(nuevo, "/api/ruta", ManejadorApi.de("/api/ruta",
                x -> entregas.ruta(ManejadorApi.parametro(x, "destino")), "GET", "HEAD"));

        protegida(nuevo, "/api/entregas", ManejadorApi.de("/api/entregas", x -> entregas.estado(), "GET", "HEAD"));
        protegida(nuevo, "/api/entregas/iniciar", ManejadorApi.de("/api/entregas/iniciar",
                x -> entregas.iniciar(ManejadorApi.leerJson(x, SolicitudSalida.class).horaSalida), "POST"));
        protegida(nuevo, "/api/entregas/siguiente", ManejadorApi.de("/api/entregas/siguiente",
                x -> entregas.siguiente(), "POST"));
        protegida(nuevo, "/api/entregas/vista-previa", ManejadorApi.de("/api/entregas/vista-previa",
                x -> entregas.vistaPrevia(ManejadorApi.leerJson(x, SolicitudSalida.class).horaSalida), "POST"));

        protegida(nuevo, "/api/inventario", ManejadorApi.de("/api/inventario",
                x -> inventario.inventarioLocal(), "GET", "HEAD"));
        protegida(nuevo, "/api/inventario/agregar", ManejadorApi.de("/api/inventario/agregar",
                x -> inventario.agregar(ManejadorApi.leerJson(x, ControladorInventario.SolicitudAgregar.class)),
                "POST"));
        protegida(nuevo, "/api/catalogo", ManejadorApi.de("/api/catalogo",
                x -> inventario.catalogoGlobal(), "GET", "HEAD"));

        protegida(nuevo, "/api/pedidos", ManejadorApi.de("/api/pedidos", x -> "POST".equalsIgnoreCase(x.getRequestMethod())
                ? pedidos.registrar(ManejadorApi.leerJson(x, ControladorPedidos.SolicitudPedido.class))
                : pedidos.listar(ManejadorApi.parametro(x, "estado")), "GET", "HEAD", "POST"));

        protegida(nuevo, "/api/estadisticas", ManejadorApi.de("/api/estadisticas",
                x -> estadisticas.consultar(ManejadorApi.parametro(x, "desde"), ManejadorApi.parametro(x, "hasta"),
                        ManejadorApi.parametro(x, "peores")), "GET", "HEAD"));

        protegida(nuevo, "/api/", new ApiNoEncontrada());

        // Archivos de la interfaz (index.html, css, js, imágenes)
        nuevo.createContext("/", new RecursosEstaticos(CARPETA_WEB));

        nuevo.setExecutor(null); // un solo hilo: las peticiones se atienden en orden
        nuevo.start();
        servidor = nuevo;
    }

    /** Endpoint que exige sesión iniciada. */
    private void protegida(HttpServer servidorHttp, String ruta, HttpHandler manejador) {
        servidorHttp.createContext(ruta, manejador).getFilters().add(conSesion);
    }

    /** Endpoint sin sesión (inicio de sesión); igual revisa que los POST vengan de la página. */
    private void publica(HttpServer servidorHttp, String ruta, HttpHandler manejador) {
        servidorHttp.createContext(ruta, manejador).getFilters().add(sinSesion);
    }

    /** Deja de atender peticiones y libera el puerto. Se puede llamar varias veces. */
    public synchronized void detener() {
        if (servidor == null) {
            return;
        }
        servidor.stop(0);
        servidor = null;
    }

    public synchronized boolean estaIniciado() {
        return servidor != null;
    }

    /**
     * Puerto donde escucha (útil cuando se pidió el puerto 0).
     *
     * @throws IllegalStateException si el servidor no está iniciado.
     */
    public synchronized int getPuerto() {
        if (servidor == null) {
            throw new IllegalStateException("El servidor web de la sede " + config.getCodigo() + " no está iniciado.");
        }
        return servidor.getAddress().getPort();
    }

    /** Dirección para abrir en el navegador de ESTA computadora, por ejemplo "http://localhost:8082". */
    public String getUrl() {
        return "http://localhost:" + getPuerto();
    }

    /**
     * Sin coordenadas el mapa no puede dibujar los puntos, pero el resto de la sede funciona igual:
     * por eso un archivo dañado solo se avisa y no impide arrancar.
     */
    private static CargadorUbicaciones.Ubicacion[] cargarCoordenadas() {
        try {
            return CargadorUbicaciones.cargarDesdeRecurso(ARCHIVO_UBICACIONES);
        } catch (IllegalArgumentException e) {
            System.err.println("[ConviTEC web] Aviso: " + e.getMessage() + " El mapa se mostrará sin ubicaciones.");
            return new CargadorUbicaciones.Ubicacion[0];
        }
    }

    /** Cuerpo de POST /api/entregas/iniciar y /api/entregas/vista-previa. */
    static final class SolicitudSalida {
        String horaSalida;
    }
}
