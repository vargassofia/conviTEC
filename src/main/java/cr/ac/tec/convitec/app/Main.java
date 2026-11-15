package cr.ac.tec.convitec.app;

import cr.ac.tec.convitec.config.ConfigException;
import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.grafo.GrafoException;
import cr.ac.tec.convitec.mensajeria.MensajeriaException;

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
 * Códigos de salida: 0 = normal, 1 = error de configuración o broker no disponible,
 * 2 = argumentos inválidos.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(ejecutar(args));
    }

    /** Hace todo y devuelve el código de salida (sin stack traces para errores esperables). */
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
            sede = Sede.conDemostracion(config);
        } catch (ConfigException | GrafoException | IllegalArgumentException e) {
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

        Runtime.getRuntime().addShutdownHook(new Thread(sede::cerrar, "cerrar-sede-" + config.getCodigo()));

        // TODO PERSONA 1: arrancar aquí el servidor HTTP (API REST + interfaz web) en config.getPuertoHttp()
        //   (LIB 8081, ALA 8082, SJO 8083, LIM 8084), DESPUÉS de sede.iniciar(). Los servicios salen de los
        //   getters de Sede: getServicioRutas(), getEstadisticas(), getMensajeria(), getInventario(), etc.
        //   Si se deja también la consola, correr el servidor en otro hilo; al salir, detenerlo antes de
        //   sede.cerrar(). Si el puerto está ocupado, mostrar un mensaje claro y devolver 1.

        ConsolaSede consola = new ConsolaSede(sede,
                new BufferedReader(new InputStreamReader(System.in, charsetConsola())), System.out);
        consola.ejecutar();
        sede.cerrar();
        return 0;
    }

    /** En Windows la consola no siempre es UTF-8: se usa el charset de la consola si existe. */
    private static Charset charsetConsola() {
        Console consola = System.console();
        return consola != null ? consola.charset() : Charset.defaultCharset();
    }
}
