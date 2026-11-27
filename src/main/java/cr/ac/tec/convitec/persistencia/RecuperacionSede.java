package cr.ac.tec.convitec.persistencia;

import cr.ac.tec.convitec.catalogo.CatalogoGlobal;
import cr.ac.tec.convitec.catalogo.InventarioLocal;
import cr.ac.tec.convitec.entregas.RepositorioVentasArchivo;
import cr.ac.tec.convitec.modelo.Pedido;
import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.mensajeria.MensajeriaRabbit;
import cr.ac.tec.convitec.pedidos.ServicioPedidos;
import cr.ac.tec.convitec.servicios.IMensajeria;
import cr.ac.tec.convitec.servicios.IServicioRutas;

/** Reconstruye las estructuras y repara pedidos interrumpidos antes de conectar mensajería. */
public final class RecuperacionSede {

    private final PersistenciaSede persistencia;

    public RecuperacionSede(PersistenciaSede persistencia) {
        if (persistencia == null) {
            throw new IllegalArgumentException("La persistencia de sede no puede ser null");
        }
        this.persistencia = persistencia;
    }

    public synchronized ResultadoRecuperacion recuperar(String nombreSede, String codigoSede,
                                                         IMensajeria mensajeria,
                                                         IServicioRutas servicioRutas) {
        if (nombreSede == null || nombreSede.isBlank() || codigoSede == null || codigoSede.isBlank()
                || mensajeria == null || servicioRutas == null) {
            throw new IllegalArgumentException("Nombre, código, mensajería y rutas son obligatorios");
        }

        return recuperarInterno(null, nombreSede, codigoSede, mensajeria, servicioRutas);
    }

    /** Recupera usando mensajería suministrada (pruebas) o creando RabbitMQ con los objetos reconstruidos. */
    public synchronized ResultadoRecuperacion recuperar(ConfigSede config, IServicioRutas servicioRutas,
                                                         IMensajeria mensajeriaExistente) {
        if (config == null || servicioRutas == null) {
            throw new IllegalArgumentException("La configuración y las rutas son obligatorias");
        }
        return recuperarInterno(config, config.getNombre(), config.getCodigo(),
                mensajeriaExistente, servicioRutas);
    }

    private ResultadoRecuperacion recuperarInterno(ConfigSede config, String nombreSede, String codigoSede,
                                                    IMensajeria mensajeriaExistente,
                                                    IServicioRutas servicioRutas) {
        EstadoSede estado = persistencia.cargarTodo();
        CatalogoGlobal catalogo = new CatalogoGlobal();
        catalogo.restaurar(estado.getProductos(), estado.getStocksConocidos());
        InventarioLocal inventario = new InventarioLocal(catalogo);
        inventario.restaurar(estado.getExistencias(), estado.getReservas());
        IMensajeria mensajeria = mensajeriaExistente == null
                ? new MensajeriaRabbit(config, inventario, catalogo) : mensajeriaExistente;
        ServicioPedidos pedidos = new ServicioPedidos(nombreSede, codigoSede, inventario,
                catalogo, mensajeria, servicioRutas);
        Pedido[] modelos = estado.getPedidos();
        pedidos.restaurar(modelos, estado.getContadorIds());
        LiberacionPendiente[] liberaciones = pedidos.recuperarPedidosIncompletos();
        RepositorioVentasArchivo ventas = new RepositorioVentasArchivo(persistencia.getCarpeta());

        if (liberaciones.length > 0 || contieneEnProceso(modelos)) {
            persistencia.guardarInventario(inventario);
            persistencia.guardarPedidos(pedidos);
        }

        for (String aviso : ventas.getAvisosCarga()) {
            estado.getResultadoCarga().agregarAviso(aviso);
        }
        inventario.setOyenteCambios(() -> persistencia.guardarInventario(inventario));
        catalogo.setOyenteCambios(() -> {
            persistencia.guardarCatalogo(catalogo.exportarCatalogo());
            persistencia.guardarStockConocido(catalogo);
        });
        pedidos.setOyenteCambios(() -> persistencia.guardarPedidos(pedidos));

        return new ResultadoRecuperacion(inventario, catalogo, pedidos, ventas,
                liberaciones, estado.getResultadoCarga(), mensajeria);
    }

    private boolean contieneEnProceso(Pedido[] pedidos) {
        for (Pedido pedido : pedidos) {
            if (pedido.getEstado() == cr.ac.tec.convitec.modelo.EstadoPedido.EN_PROCESO) {
                return true;
            }
        }
        return false;
    }
}
