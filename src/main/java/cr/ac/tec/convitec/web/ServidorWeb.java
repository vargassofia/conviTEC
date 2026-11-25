package cr.ac.tec.convitec.web;

import com.sun.net.httpserver.HttpServer;
import cr.ac.tec.convitec.config.ConfigSede;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.time.Clock;

/**
 * Servidor HTTP de UNA sede: sirve la interfaz web (archivos de src/main/resources/web) y la API REST.
 *
 *   ServidorWeb web = new ServidorWeb(config);
 *   web.iniciar();                 // escucha en config.getPuertoHttp() (LIB 8081, ALA 8082, SJO 8083, LIM 8084)
 *   web.getUrl();                  // "http://localhost:8082"
 *   ...
 *   web.detener();                 // seguro aunque se llame dos veces
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

    private final ConfigSede config;
    private final int puertoSolicitado;
    private final Clock reloj;

    private HttpServer servidor;

    /**
     * @throws IllegalArgumentException si la configuración es nula.
     */
    public ServidorWeb(ConfigSede config) {
        this(config, config == null ? 0 : config.getPuertoHttp(), Clock.systemDefaultZone());
    }

    /**
     * Con otro puerto y otro reloj (las pruebas usan el puerto 0, que elige uno libre, y un reloj fijo).
     */
    ServidorWeb(ConfigSede config, int puerto, Clock reloj) {
        if (config == null) {
            throw new IllegalArgumentException("El servidor web necesita la configuración de la sede.");
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

        // API REST. El contexto más largo gana: /api/sede antes que /api/
        nuevo.createContext("/api/sede", new ApiSede(config, reloj));
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
}
