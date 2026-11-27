package cr.ac.tec.convitec.app;

import cr.ac.tec.convitec.config.ConfigException;
import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.grafo.GrafoException;
import cr.ac.tec.convitec.mensajeria.MensajeriaException;
import cr.ac.tec.convitec.persistencia.PersistenciaException;
import cr.ac.tec.convitec.web.PedidosFuentePendientes;
import cr.ac.tec.convitec.web.ServidorWeb;
import cr.ac.tec.convitec.web.ServidorWebException;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/**
 * Punto de entrada de una sede.
 *
 *   java -jar target/convitec-sede.jar ALA
 *   java -jar target/convitec-sede.jar ALA --config mi-sede-ALA.properties
 *
 * Al arrancar conecta la mensajería, abre la interfaz web en http.puerto y deja la consola de prueba.
 *
 * Códigos de salida: 0 = normal, 1 = error de configuración, broker no disponible o puerto HTTP ocupado,
 * 2 = argumentos inválidos.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(ejecutar(args));
    }

    /** Hace todo y devuelve el código de salida (sin stack traces para errores esperables) **/
    static int ejecutar(String[] args) {
        ArgumentosSede argumentos;
        try {
            argumentos = ArgumentosSede.interpretar(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 2;
        }

        ConfigSede config;
        Sede sede;
        try {
            config = argumentos.cargarConfiguracion();
            sede = Sede.crearDesdeConfiguracion(config);
        } catch (ConfigException | GrafoException | IllegalArgumentException | PersistenciaException e) {
            System.err.println("Error de configuración: " + e.getMessage());
            return 1;
        }

        System.out.println("Iniciando " + config);
        try {
            sede.iniciar();
        } catch (MensajeriaException e) {
            System.err.println();
            System.err.println("No se pudo conectar a RabbitMQ en " + config.getRabbitHost() + ":"
                    + config.getRabbitPuerto() + ".");
            System.err.println("- Si el broker está en esta computadora: brew services start rabbitmq");
            System.err.println("- Si está en otra computadora: revise rabbit.host (IP), el usuario/clave"
                    + " (guest solo sirve en localhost), el firewall y que estén en la misma red.");
            System.err.println("Detalle: " + e.getMessage());
            sede.cerrar();
            return 1;
        }

        // Interfaz web + API REST en config.getPuertoHttp() (LIB 8081, ALA 8082, SJO 8083, LIM 8084).
        // El servidor atiende en su propio hilo; la consola sigue en este.
        ServidorWeb servidorWeb = new ServidorWeb(config, sede.getGrafo(), sede.getServicioRutas(),
                new PedidosFuentePendientes(sede.getServicioPedidos()));
        try {
            servidorWeb.iniciar();
        } catch (ServidorWebException e) {
            System.err.println("No se pudo iniciar la interfaz web: " + e.getMessage());
            sede.cerrar();
            return 1;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> apagar(servidorWeb, sede),
                "cerrar-sede-" + config.getCodigo()));

        System.out.println("Interfaz web lista: " + servidorWeb.getUrl()
                + "  (desde otra computadora: http://<IP-de-esta-computadora>:" + config.getPuertoHttp() + ")");

        ConsolaSede consola = new ConsolaSede(sede,
                new BufferedReader(new InputStreamReader(System.in, charsetConsola())), System.out);
        consola.ejecutar();
        apagar(servidorWeb, sede);
        return 0;
    }

    /** Primero se detiene la web (para no recibir pedidos nuevos) y luego la mensajería. Seguro si se repite. */
    private static void apagar(ServidorWeb servidorWeb, Sede sede) {
        servidorWeb.detener();
        sede.cerrar();
    }

    /** En Windows la consola no siempre es UTF-8: se usa el charset de la consola si existe. */
    private static Charset charsetConsola() {
        Console consola = System.console();
        return consola != null ? consola.charset() : Charset.defaultCharset();
    }
}
