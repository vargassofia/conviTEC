package cr.ac.tec.convitec.web;

import cr.ac.tec.convitec.acceso.ServicioAcceso;
import cr.ac.tec.convitec.catalogo.CatalogoGlobal;
import cr.ac.tec.convitec.catalogo.ServicioInventario;
import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.entregas.ServicioEntregas;
import cr.ac.tec.convitec.estadisticas.ServicioEstadisticas;
import cr.ac.tec.convitec.grafo.CargadorGrafo;
import cr.ac.tec.convitec.grafo.Grafo;
import cr.ac.tec.convitec.mensajeria.RespuestaReserva;
import cr.ac.tec.convitec.pedidos.ResultadoPedido;
import cr.ac.tec.convitec.pedidos.ServicioPedidos;
import cr.ac.tec.convitec.pedidos.SolicitudLinea;
import cr.ac.tec.convitec.persistencia.PersistenciaSede;
import cr.ac.tec.convitec.persistencia.RecuperacionSede;
import cr.ac.tec.convitec.persistencia.ResultadoRecuperacion;
import cr.ac.tec.convitec.rutas.ServicioRutas;
import cr.ac.tec.convitec.servicios.IMensajeria;

import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Una sede completa para las pruebas de la web, armada igual que Sede.conDemostracion:
 * inventario, catálogo, pedidos, ventas y persistencia REALES, pero
 * - en una carpeta temporal (no toca datos/ del proyecto), y
 * - con una mensajería falsa (sin RabbitMQ).
 */
final class SedeWebDePrueba {

    final ConfigSede config;
    final MensajeriaFalsa mensajeria = new MensajeriaFalsa();
    final CatalogoGlobal catalogo;
    final ServicioInventario inventario;
    final ServicioPedidos pedidos;
    final ServiciosWeb servicios;
    /** Cuentas de la sede (PBKDF2 con pocas iteraciones para que las pruebas vayan rápido). */
    final ServicioAcceso acceso;
    private String cookie;

    private SedeWebDePrueba(String codigo, Path carpeta) {
        ConfigSede base = ConfigSede.cargarDesdeRecurso("sedes/sede-" + codigo + ".properties");
        config = new ConfigSede(base.getCodigo(), base.getNombre(), base.getPuertoHttp(), "localhost", 5672,
                "guest", "guest", 1000, carpeta.toString());
        Grafo grafo = CargadorGrafo.cargarDesdeRecurso("rutas.json");
        ServicioRutas rutas = new ServicioRutas(grafo, config.getNombre());
        ResultadoRecuperacion r = new RecuperacionSede(new PersistenciaSede(carpeta)).recuperar(config, rutas, mensajeria);
        catalogo = r.getCatalogo();
        pedidos = r.getServicioPedidos();
        inventario = new ServicioInventario(r.getInventario(), catalogo, mensajeria);
        servicios = new ServiciosWeb(config, grafo, rutas, new PedidosFuentePendientes(pedidos),
                new ServicioEntregas(config.getNombre(), pedidos, r.getRepositorioVentas()), inventario, pedidos,
                new ServicioEstadisticas(config.getNombre(), r.getRepositorioVentas()));
        acceso = new ServicioAcceso(config.getCodigo(), carpeta, Clock.systemDefaultZone(), 1000);
    }

    static SedeWebDePrueba crear(String codigo, Path carpeta) {
        return new SedeWebDePrueba(codigo, carpeta);
    }

    /** Servidor web de esta sede, sin iniciar. */
    ServidorWeb servidor(int puerto, Clock reloj) {
        return new ServidorWeb(servicios, acceso, puerto, reloj);
    }

    /** Cookie de una sesión abierta ("convitec_SJO=..."); la primera vez crea la cuenta "prueba". */
    String cookieSesion() {
        if (cookie == null) {
            String token = acceso.usarCodigoAcceso(acceso.generarCodigoAcceso(), "Persona de prueba", "prueba",
                    "clave-de-prueba");
            cookie = Cookies.nombrePara(config.getCodigo()) + "=" + token;
        }
        return cookie;
    }

    /** Agrega a la petición la sesión y el encabezado que pone la página (js/api.js). */
    HttpRequest.Builder conSesion(HttpRequest.Builder peticion) {
        return peticion.header("Cookie", cookieSesion()).header(FiltroSesion.ENCABEZADO_APP, "1");
    }

    /** Registra un pedido de una línea que debe ser aceptado (con stock local). */
    String pedidoAceptado(String destino, String codigo, int cantidad) {
        ResultadoPedido r = pedidos.registrarPedido(destino, new SolicitudLinea[]{new SolicitudLinea(codigo, cantidad)});
        assertTrue(r.isAceptado(), "El pedido a " + destino + " debía aceptarse: " + r.getMotivo());
        return r.getPedido().getId();
    }

    /** Mensajería sin RabbitMQ: guarda lo que se le pide y responde lo que se configure. */
    static final class MensajeriaFalsa implements IMensajeria {
        boolean aceptarReservas = true;
        boolean fallarPublicacion = false;
        int publicaciones = 0;
        int reservasSolicitadas = 0;

        @Override
        public void iniciar() {
        }

        @Override
        public void cerrar() {
        }

        @Override
        public RespuestaReserva solicitarReserva(String sedeDestino, String idPedido, String codigoProducto,
                                                 int cantidad) {
            reservasSolicitadas++;
            return aceptarReservas ? RespuestaReserva.ok(sedeDestino)
                    : RespuestaReserva.rechazo(sedeDestino, "Limón: stock insuficiente de " + codigoProducto);
        }

        @Override
        public void confirmarReserva(String sedeDestino, String idPedido, String codigoProducto) {
        }

        @Override
        public void liberarReserva(String sedeDestino, String idPedido, String codigoProducto) {
        }

        @Override
        public void publicarActualizacionStock(String codigoProducto, int cantidadNueva) {
            if (fallarPublicacion) {
                throw new IllegalStateException("la mensajería no está iniciada");
            }
            publicaciones++;
        }
    }
}
