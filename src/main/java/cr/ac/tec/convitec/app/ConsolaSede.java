package cr.ac.tec.convitec.app;

import cr.ac.tec.convitec.demo.CatalogoGlobalDemo;
import cr.ac.tec.convitec.demo.InventarioDemo;
import cr.ac.tec.convitec.catalogo.ExistenciaInventario;
import cr.ac.tec.convitec.catalogo.ProductoCatalogo;
import cr.ac.tec.convitec.catalogo.ServicioInventario;
import cr.ac.tec.convitec.estadisticas.ProductoEstrella;
import cr.ac.tec.convitec.estadisticas.ResumenProducto;
import cr.ac.tec.convitec.modelo.DetallePedido;
import cr.ac.tec.convitec.modelo.Pedido;
import cr.ac.tec.convitec.modelo.Venta;
import cr.ac.tec.convitec.mensajeria.RespuestaReserva;
import cr.ac.tec.convitec.pedidos.ResultadoPedido;
import cr.ac.tec.convitec.pedidos.ServicioPedidos;
import cr.ac.tec.convitec.pedidos.SolicitudLinea;
import cr.ac.tec.convitec.rutas.ParadaEntrega;
import cr.ac.tec.convitec.rutas.RecorridoEntrega;
import cr.ac.tec.convitec.rutas.SalidasDiarias;
import cr.ac.tec.convitec.rutas.SolicitudEntrega;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
            "  pedido <destino> <codigo>:<cantidad> ...       registra un pedido",
            "  pendientes | historial                        consulta pedidos",
            "  detalle <idPedido>                            muestra un pedido",
            "  reservar <SEDE> <idPedido> <codigo> <cant>    pide una reserva a otra sede (LIB, ALA, SJO, LIM)",
            "  confirmar <SEDE> <idPedido> <codigo>          confirma una reserva",
            "  liberar <SEDE> <idPedido> <codigo>            libera una reserva",
            "  ruta <destino>                                tiempo y camino desde esta sede",
            "  entregar                                      entrega los pedidos pendientes y registra ventas",
            "  entregar <11:00|15:00> <id>:<destino> ...     solo planifica un recorrido indicado",
            "  ventas | estrella | peores [cantidad]          consulta estadísticas de ventas",
            "  salir                                         cierra la sede",
            "Nombres con espacio: entre comillas (\"San José\") o con guion bajo (San_José).",
            "Las tildes son opcionales: San_Jose, Limon y Paraiso tambien sirven.");

    private final Sede sede;
    private final BufferedReader entrada;
    private final PrintStream salida;

    public ConsolaSede(Sede sede, BufferedReader entrada, PrintStream salida) {
        if (sede == null || entrada == null || salida == null) {
            throw new IllegalArgumentException("La consola necesita la sede, la entrada y la salida.");
        }
        this.sede = sede;
        this.entrada = entrada;
        this.salida = salida;
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
                case "pedido":
                    pedido(comando);
                    break;
                case "pendientes":
                    exigirArgumentos(comando, 0);
                    listarPedidos(sede.getServicioPedidos() == null ? null
                            : sede.getServicioPedidos().listarPendientes(), "Pedidos pendientes");
                    break;
                case "historial":
                    exigirArgumentos(comando, 0);
                    listarPedidos(sede.getServicioPedidos() == null ? null
                            : sede.getServicioPedidos().listarHistorial(), "Historial de pedidos");
                    break;
                case "detalle":
                    exigirArgumentos(comando, 1);
                    detalle(comando.getArgumento(0));
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
                case "ventas":
                    exigirArgumentos(comando, 0);
                    ventas();
                    break;
                case "estrella":
                    exigirArgumentos(comando, 0);
                    estrella();
                    break;
                case "peores":
                    if (comando.getCantidadArgumentos() > 1) {
                        throw new IllegalArgumentException("Uso: peores [cantidad].");
                    }
                    peores(comando);
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

    private void stock() {
        ServicioInventario servicio = sede.getServicioInventario();
        if (servicio != null) {
            ExistenciaInventario[] existencias = servicio.consultarInventarioLocal();
            salida.println("Inventario de " + sede.getNombre() + ":");
            if (existencias.length == 0) {
                salida.println("  No hay existencias locales registradas.");
                return;
            }
            for (ExistenciaInventario existencia : existencias) {
                salida.printf("  %-5s %-18s ₡%8.2f  disponible %3d  reservado %3d%n",
                        existencia.getCodigoProducto(), existencia.getNombreProducto(),
                        existencia.getPrecio(), existencia.getCantidadDisponible(),
                        existencia.getCantidadReservada());
            }
            return;
        }
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
        ServicioInventario servicio = sede.getServicioInventario();
        if (servicio != null) {
            ProductoCatalogo[] productos = servicio.consultarCatalogoGlobal();
            if (productos.length == 0) {
                salida.println("El catálogo está vacío.");
                return;
            }
            salida.println("Catálogo global (precios y último stock conocido):");
            for (ProductoCatalogo fila : productos) {
                salida.printf("  %-5s %-18s ₡%8.2f", fila.getProducto().getCodigo(),
                        fila.getProducto().getNombre(), fila.getProducto().getPrecio());
                for (var disponibilidad : fila.getDisponibilidades()) {
                    salida.printf("  %s:%d", disponibilidad.getCodigoSede(),
                            disponibilidad.getCantidadDisponible());
                }
                salida.println();
            }
            return;
        }
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
        ServicioInventario servicio = sede.getServicioInventario();
        if (servicio != null) {
            String codigo = c.getArgumento(0);
            int nuevo = servicio.agregarUnidades(codigo, c.getEntero(1));
            salida.println("Stock de " + codigo + ": " + nuevo);
            salida.println("Se avisó a las otras sedes.");
            return;
        }
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
        if (c.getCantidadArgumentos() == 0) {
            entregarPendientes();
            return;
        }
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

    private void pedido(ComandoConsola c) {
        ServicioPedidos servicio = sede.getServicioPedidos();
        if (servicio == null) {
            salida.println("El servicio real de pedidos no está disponible en esta sede.");
            return;
        }
        if (c.getCantidadArgumentos() < 2) {
            throw new IllegalArgumentException("Uso: pedido <destino> <codigo>:<cantidad> ...");
        }
        SolicitudLinea[] lineas = new SolicitudLinea[c.getCantidadArgumentos() - 1];
        for (int i = 0; i < lineas.length; i++) {
            String texto = c.getArgumento(i + 1);
            int separador = texto.indexOf(':');
            if (separador <= 0 || separador == texto.length() - 1
                    || texto.indexOf(':', separador + 1) >= 0) {
                throw new IllegalArgumentException("'" + texto
                        + "' debe tener la forma codigo:cantidad (ej. C01:2).");
            }
            String codigo = texto.substring(0, separador).trim();
            if (codigo.isEmpty()) {
                throw new IllegalArgumentException("El código del producto no puede estar vacío.");
            }
            int cantidad;
            try {
                cantidad = Integer.parseInt(texto.substring(separador + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("La cantidad de '" + texto
                        + "' debe ser un número entero.");
            }
            lineas[i] = new SolicitudLinea(codigo, cantidad);
        }
        ResultadoPedido resultado = servicio.registrarPedido(c.getNombreUbicacion(0), lineas);
        if (resultado.isAceptado()) {
            salida.println("Pedido " + resultado.getPedido().getId() + " aceptado");
        } else {
            salida.println("Pedido " + resultado.getPedido().getId() + " rechazado: " + resultado.getMotivo());
        }
    }

    private void listarPedidos(Pedido[] pedidos, String titulo) {
        if (pedidos == null) {
            salida.println("El servicio real de pedidos no está disponible en esta sede.");
            return;
        }
        salida.println(titulo + ":");
        if (pedidos.length == 0) {
            salida.println("  No hay pedidos.");
            return;
        }
        for (Pedido pedido : pedidos) {
            salida.println("  " + pedido.getId() + " | " + pedido.getEstado() + " | "
                    + pedido.getDestino() + " | ₡" + String.format("%.2f", pedido.getMontoTotal())
                    + (pedido.getMotivoCancelacion() == null ? "" : " | " + pedido.getMotivoCancelacion()));
        }
    }

    private void detalle(String idPedido) {
        ServicioPedidos servicio = sede.getServicioPedidos();
        if (servicio == null) {
            salida.println("El servicio real de pedidos no está disponible en esta sede.");
            return;
        }
        Pedido pedido = servicio.buscarPorId(idPedido);
        if (pedido == null) {
            salida.println("No existe el pedido " + idPedido + ".");
            return;
        }
        salida.println("Pedido " + pedido.getId() + " | " + pedido.getEstado()
                + " | " + pedido.getSedeOrigen() + " -> " + pedido.getDestino());
        for (DetallePedido detalle : pedido.getDetalles()) {
            salida.printf("  %s %s x%d a ₡%.2f | surte %s%n", detalle.getCodigoProducto(),
                    detalle.getNombre(), detalle.getCantidad(), detalle.getPrecioUnitario(),
                    detalle.getSedeSurtidora());
        }
        salida.printf("Total: ₡%.2f%n", pedido.getMontoTotal());
        if (pedido.getMotivoCancelacion() != null) {
            salida.println("Motivo de cancelación: " + pedido.getMotivoCancelacion());
        }
    }

    private void entregarPendientes() {
        ServicioPedidos pedidos = sede.getServicioPedidos();
        if (pedidos == null || sede.getServicioEntregas() == null) {
            salida.println("El servicio real de pedidos y entregas no está disponible en esta sede.");
            return;
        }
        SolicitudEntrega[] solicitudes = pedidos.construirSolicitudesEntrega();
        if (solicitudes.length == 0) {
            salida.println("No hay pedidos pendientes de entrega.");
            return;
        }
        SalidasDiarias.Asignacion salidaDiaria = pedidos.salidaParaRegistro(LocalTime.now());
        sede.getServicioRutas().iniciarRecorrido(solicitudes, salidaDiaria.getHoraSalida());
        int cantidadVentas = 0;
        while (sede.getServicioRutas().hayEntregasPendientes()) {
            ParadaEntrega parada = sede.getServicioRutas().calcularSiguienteEntrega();
            Venta[] ventasParada = sede.getServicioEntregas()
                    .registrarParada(parada, salidaDiaria.getHoraSalida(), LocalDateTime.now());
            cantidadVentas += ventasParada.length;
            for (Venta venta : ventasParada) {
                salida.println(venta);
            }
        }
        salida.println("Entregas registradas: " + cantidadVentas + " | salida " + salidaDiaria + ".");
    }

    private void ventas() {
        Venta[] ventas = sede.getEstadisticas().historialVentas();
        salida.println("Ventas de " + sede.getNombre() + ":");
        if (ventas.length == 0) {
            salida.println("  No hay ventas.");
            return;
        }
        for (Venta venta : ventas) {
            salida.println(venta);
        }
        salida.printf("Total vendido: ₡%.2f%n", sede.getEstadisticas().montoTotalVendido());
    }

    private void estrella() {
        ProductoEstrella producto = sede.getEstadisticas().productoEstrella();
        salida.println(producto == null ? "Todavía no hay productos vendidos." : producto);
    }

    private void peores(ComandoConsola c) {
        int cantidad = c.getCantidadArgumentos() == 0 ? 5 : c.getEntero(0);
        Venta[] peores = sede.getEstadisticas().peoresPedidos(cantidad);
        salida.println("Peores pedidos:");
        if (peores.length == 0) {
            salida.println("  No hay ventas.");
            return;
        }
        for (Venta venta : peores) {
            salida.println("  " + venta.getIdPedido() + " | rendimiento "
                    + String.format("%.2f", venta.getRendimiento()) + " ₡/min | traslado "
                    + venta.getTiempoTraslado() + " min");
        }
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
