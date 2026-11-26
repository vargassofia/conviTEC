package cr.ac.tec.convitec.web;

import com.sun.net.httpserver.HttpServer;
import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.grafo.Grafo;
import cr.ac.tec.convitec.rutas.ServicioRutas;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.time.Clock;

/**
 * Servidor HTTP de UNA sede: sirve la interfaz web (archivos de src/main/resources/web) y la API REST.
 *
 *   ServidorWeb web = new ServidorWeb(config, sede.getGrafo(), sede.getServicioRutas(), pendientes);
 *   web.iniciar();                 // escucha en config.getPuertoHttp() (LIB 8081, ALA 8082, SJO 8083, LIM 8084)
 *   web.getUrl();                  // "http://localhost:8082"
 *   ...
 *   web.detener();                 // seguro aunque se llame dos veces
 *
 * API REST:
 *   GET  /api/sede                 qué sede es y la próxima salida
 *   GET  /api/grafo                ubicaciones (con coordenadas) y rutas, para el mapa
 *   GET  /api/ruta?destino=X       ruta más corta desde esta sede (Dijkstra)
 *   GET  /api/entregas             estado de la salida de entregas actual
 *   POST /api/entregas/iniciar     { "horaSalida": "11:00" }: empieza la salida con sus pendientes
 *   POST /api/entregas/siguiente   calcula y hace la siguiente parada
 *   POST /api/entregas/completo    { "horaSalida": "11:00" }: planifica toda la salida de una vez
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

    private HttpServer servidor;

    /**
     * @param grafo         el grafo de rutas de la sede (sede.getGrafo())
     * @param servicioRutas el servicio de rutas de la sede (sede.getServicioRutas())
     * @param pendientes    de dónde salen los pedidos a repartir (hoy PendientesDemo)
     * @throws IllegalArgumentException si algún argumento es nulo.
     * @throws cr.ac.tec.convitec.grafo.GrafoException si la sede no existe en el grafo.
     */
    public ServidorWeb(ConfigSede config, Grafo grafo, ServicioRutas servicioRutas, FuentePendientes pendientes) {
        this(config, grafo, servicioRutas, pendientes,
                config == null ? 0 : config.getPuertoHttp(), Clock.systemDefaultZone());
    }

    /**
     * Con otro puerto y otro reloj (las pruebas usan el puerto 0, que elige uno libre, y un reloj fijo).
     */
    ServidorWeb(ConfigSede config, Grafo grafo, ServicioRutas servicioRutas, FuentePendientes pendientes,
                int puerto, Clock reloj) {
        if (config == null || grafo == null || servicioRutas == null || pendientes == null) {
            throw new IllegalArgumentException("El servidor web necesita la configuración, el grafo, el servicio "
                    + "de rutas y la fuente de pedidos pendientes (ninguno puede ser nulo).");
        }
        if (puerto < 0 || puerto > 65535) {
            throw new IllegalArgumentException("Puerto HTTP inválido: " + puerto + ".");
        }
        if (reloj == null) {
            throw new IllegalArgumentException("El servidor web necesita un reloj.");
        }
        this.config = config;
        this.puertoSolicitado = puerto;
        this.reloj = reloj;
        this.mapa = MapaDTO.crear(grafo, cargarCoordenadas(), config.getNombre());
        this.entregas = new ControladorEntregas(servicioRutas, pendientes, mapa.sede);
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
        nuevo.createContext("/api/sede", new ApiSede(config, reloj));
        nuevo.createContext("/api/grafo", ManejadorApi.de("/api/grafo", x -> mapa, "GET", "HEAD"));
        nuevo.createContext("/api/ruta", ManejadorApi.de("/api/ruta",
                x -> entregas.ruta(ManejadorApi.parametro(x, "destino")), "GET", "HEAD"));
        nuevo.createContext("/api/entregas", ManejadorApi.de("/api/entregas", x -> entregas.estado(), "GET", "HEAD"));
        nuevo.createContext("/api/entregas/iniciar", ManejadorApi.de("/api/entregas/iniciar",
                x -> entregas.iniciar(ManejadorApi.leerJson(x, SolicitudSalida.class).horaSalida), "POST"));
        nuevo.createContext("/api/entregas/siguiente", ManejadorApi.de("/api/entregas/siguiente",
                x -> entregas.siguiente(), "POST"));
        nuevo.createContext("/api/entregas/completo", ManejadorApi.de("/api/entregas/completo",
                x -> entregas.completo(ManejadorApi.leerJson(x, SolicitudSalida.class).horaSalida), "POST"));
        nuevo.createContext("/api/", new ApiNoEncontrada());

        // Archivos de la interfaz (index.html, css, js, imágenes)
        nuevo.createContext("/", new RecursosEstaticos(CARPETA_WEB));

        nuevo.setExecutor(null); // un solo hilo: las peticiones se atienden en orden
        nuevo.start();
        servidor = nuevo;
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

    /** Cuerpo de POST /api/entregas/iniciar y /api/entregas/completo. */
    static final class SolicitudSalida {
        String horaSalida;
    }
}
