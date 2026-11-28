package cr.ac.tec.convitec.app;

import cr.ac.tec.convitec.demo.CatalogoGlobalDemo;
import cr.ac.tec.convitec.demo.InventarioDemo;
import cr.ac.tec.convitec.mensajeria.RespuestaReserva;
import cr.ac.tec.convitec.rutas.RecorridoEntrega;
import cr.ac.tec.convitec.rutas.SolicitudEntrega;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.function.Supplier;

/**
 * Consola de prueba de una sede, para usarla mientras la interfaz web no está lista.
 *
 * Lee comandos de un BufferedReader y escribe en un PrintStream (así se puede probar sin teclado).
 * Un comando desconocido o con argumentos inválidos muestra el error y la ayuda, pero NO termina.
 */
public class ConsolaSede {

    public static final String AYUDA = String.join("\n",
            "Comandos:",
            "  ayuda                                         muestra esta ayuda",
            "  stock                                         inventario de esta sede",
            "  catalogo                                      stock conocido de las otras sedes",
            "  agregar <codigo> <cantidad>                   agrega unidades y avisa a las otras sedes",
            "  reservar <SEDE> <idPedido> <codigo> <cant>    pide una reserva a otra sede (LIB, ALA, SJO, LIM)",
            "  confirmar <SEDE> <idPedido> <codigo>          confirma una reserva",
            "  liberar <SEDE> <idPedido> <codigo>            libera una reserva",
            "  ruta <destino>                                tiempo y camino desde esta sede",
            "  entregar <11:00|15:00> <id>:<destino> ...     planifica el recorrido de entregas",
            "  codigo-acceso                                 código para crear una cuenta o recuperar el acceso a la web",
            "  salir                                         cierra la sede",
            "Nombres con espacio: entre comillas (\"San José\") o con guion bajo (San_José).",
            "Las tildes son opcionales: San_Jose, Limon y Paraiso tambien sirven.");

    private final Sede sede;
    private final BufferedReader entrada;
    private final PrintStream salida;
    /** Genera el código de acceso de la web (lo pone Main); null si la web no está disponible. */
    private Supplier<String> generadorCodigoAcceso;

    public ConsolaSede(Sede sede, BufferedReader entrada, PrintStream salida) {
        if (sede == null || entrada == null || salida == null) {
            throw new IllegalArgumentException("La consola necesita la sede, la entrada y la salida.");
        }
        this.sede = sede;
        this.entrada = entrada;
        this.salida = salida;
    }

    /** Conecta el comando "codigo-acceso" con el servicio de acceso de la web. */
    public void setGeneradorCodigoAcceso(Supplier<String> generador) {
        this.generadorCodigoAcceso = generador;
    }

    /** Lee y ejecuta comandos hasta "salir" o hasta que se acabe la entrada. */
    public void ejecutar() {
        salida.println("ConviTEC - sede " + sede.getCodigo() + " (" + sede.getNombre() + "). Escriba 'ayuda'.");
        while (true) {
            salida.print(sede.getCodigo() + "> ");
            salida.flush();
            String linea;
            try {
                linea = entrada.readLine();
            } catch (IOException e) {
                salida.println("Error leyendo la consola: " + e.getMessage());
                return;
            }
            if (linea == null || !procesarLinea(linea)) {
                return;
            }
        }
    }

    /**
     * Ejecuta una línea.
     *
     * @return false si el comando fue "salir"; true para seguir.
     */
    public boolean procesarLinea(String linea) {
        ComandoConsola comando;
        try {
            comando = ComandoConsola.interpretar(linea);
        } catch (IllegalArgumentException e) {
            errorConAyuda(e.getMessage());
            return true;
        }
        if (comando.esVacio()) {
            return true;
        }
        try {
            switch (comando.getNombre()) {
                case "ayuda":
                    salida.println(AYUDA);
                    break;
                case "stock":
                    stock();
                    break;
                case "catalogo":
                    catalogo();
                    break;
                case "agregar":
                    exigirArgumentos(comando, 2);
                    agregar(comando);
                    break;
                case "reservar":
                    exigirArgumentos(comando, 4);
                    reservar(comando);
                    break;
                case "confirmar":
                    exigirArgumentos(comando, 3);
                    sede.getMensajeria().confirmarReserva(sedeDe(comando), comando.getArgumento(1),
                            comando.getArgumento(2));
                    salida.println("Confirmación enviada a " + sedeDe(comando) + ".");
                    break;
                case "liberar":
                    exigirArgumentos(comando, 3);
                    sede.getMensajeria().liberarReserva(sedeDe(comando), comando.getArgumento(1),
                            comando.getArgumento(2));
                    salida.println("Liberación enviada a " + sedeDe(comando) + ".");
                    break;
                case "ruta":
                    exigirArgumentos(comando, 1);
                    salida.println(sede.getServicioRutas().caminoMasCorto(sede.getNombre(),
                            comando.getNombreUbicacion(0)));
                    break;
                case "entregar":
                    entregar(comando);
                    break;
                case "codigo-acceso":
                    codigoAcceso();
                    break;
                case "salir":
                    salida.println("Cerrando la sede " + sede.getCodigo() + "...");
                    return false;
                default:
                    errorConAyuda("Comando desconocido: '" + comando.getNombre() + "'.");
            }
        } catch (IllegalArgumentException e) {
            errorConAyuda(e.getMessage());
        } catch (RuntimeException e) {
            // Errores de rutas (GrafoException), de mensajería (MensajeriaException), etc.
            salida.println("Error: " + e.getMessage());
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Comandos
    // ------------------------------------------------------------------

    private void codigoAcceso() {
        if (generadorCodigoAcceso == null) {
            salida.println("La interfaz web no está disponible en esta sede.");
            return;
        }
        salida.println("Código de acceso: " + generadorCodigoAcceso.get() + "  (sirve una sola vez, vence en 60 minutos)");
        salida.println("En la página, elija \"¿No puede entrar?\" y escríbalo para crear una cuenta o cambiar"
                + " una contraseña olvidada.");
    }

    // TODO PERSONA 2: stock, agregar y catalogo solo funcionan con las clases de demo. Si quieren usarlos con
    //   sus clases reales, agreguen métodos para listar y agregar unidades y úsenlos aquí.
    private void stock() {
        if (!(sede.getInventario() instanceof InventarioDemo)) {
            salida.println("Este inventario no permite listar el stock desde la consola.");
            return;
        }
        String[] lineas = ((InventarioDemo) sede.getInventario()).listarProductos();
        salida.println("Inventario de " + sede.getNombre() + ":");
        for (int i = 0; i < lineas.length; i++) {
            salida.println("  " + lineas[i]);
        }
    }

    private void catalogo() {
        if (!(sede.getCatalogo() instanceof CatalogoGlobalDemo)) {
            salida.println("Este catálogo no permite listarse desde la consola.");
            return;
        }
        String[] lineas = ((CatalogoGlobalDemo) sede.getCatalogo()).listar();
        if (lineas.length == 0) {
            salida.println("Todavía no se conoce stock de otras sedes (llega cuando alguna confirma o agrega).");
            return;
        }
        salida.println("Stock conocido de otras sedes:");
        for (int i = 0; i < lineas.length; i++) {
            salida.println("  " + lineas[i]);
        }
    }

    private void agregar(ComandoConsola c) {
        if (!(sede.getInventario() instanceof InventarioDemo)) {
            salida.println("Este inventario no permite agregar unidades desde la consola.");
            return;
        }
        String codigo = c.getArgumento(0);
        int nuevo = ((InventarioDemo) sede.getInventario()).agregarUnidades(codigo, c.getEntero(1));
        salida.println("Stock de " + codigo + ": " + nuevo);
        try {
            sede.getMensajeria().publicarActualizacionStock(codigo, nuevo);
            salida.println("Se avisó a las otras sedes.");
        } catch (RuntimeException e) {
            salida.println("Aviso: no se pudo avisar a las otras sedes (" + e.getMessage() + ").");
        }
    }

    private void reservar(ComandoConsola c) {
        salida.println("Esperando respuesta de " + sedeDe(c) + "...");
        RespuestaReserva r = sede.getMensajeria().solicitarReserva(sedeDe(c), c.getArgumento(1),
                c.getArgumento(2), c.getEntero(3));
        salida.println(r);
    }

    /** entregar <hora> <id>:<destino> [<id>:<destino> ...] */
    private void entregar(ComandoConsola c) {
        if (c.getCantidadArgumentos() < 1) {
            throw new IllegalArgumentException("Uso: entregar <11:00|15:00> <idPedido>:<destino> ...");
        }
        String hora = c.getArgumento(0);
        int cantidad = c.getCantidadArgumentos() - 1;
        SolicitudEntrega[] pendientes = new SolicitudEntrega[cantidad];
        for (int i = 0; i < cantidad; i++) {
            String texto = c.getArgumento(i + 1);
            int dosPuntos = texto.indexOf(':');
            if (dosPuntos <= 0 || dosPuntos == texto.length() - 1) {
                throw new IllegalArgumentException("'" + texto + "' debe tener la forma idPedido:destino (ej. P1:Heredia).");
            }
            pendientes[i] = new SolicitudEntrega(texto.substring(0, dosPuntos),
                    ComandoConsola.comoUbicacion(texto.substring(dosPuntos + 1)));
        }
        RecorridoEntrega recorrido = sede.getServicioRutas().planificarRecorridoCompleto(pendientes, hora);
        salida.println(recorrido);
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static String sedeDe(ComandoConsola c) {
        return c.getArgumento(0).toUpperCase();
    }

    private static void exigirArgumentos(ComandoConsola c, int cantidad) {
        if (c.getCantidadArgumentos() != cantidad) {
            throw new IllegalArgumentException("'" + c.getNombre() + "' necesita " + cantidad
                    + " argumento(s) y recibió " + c.getCantidadArgumentos() + ".");
        }
    }

    private void errorConAyuda(String mensaje) {
        salida.println("Error: " + mensaje);
        salida.println(AYUDA);
    }
}
