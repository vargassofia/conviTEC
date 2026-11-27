package cr.ac.tec.convitec.persistencia;

import cr.ac.tec.convitec.catalogo.ReservaPersistida;
import cr.ac.tec.convitec.modelo.Existencia;
import cr.ac.tec.convitec.modelo.Pedido;
import cr.ac.tec.convitec.modelo.Producto;
import cr.ac.tec.convitec.modelo.Venta;

/** Datos reconstruibles de una sede, todavía independientes de sus servicios en memoria. */
public final class EstadoSede {
    private final Producto[] productos;
    private final Existencia[] existencias;
    private final ReservaPersistida[] reservas;
    private final Pedido[] pedidos;
    private final Venta[] ventas;
    private final RegistroStockConocido[] stocksConocidos;
    private final int contadorIds;
    private final ResultadoCarga resultadoCarga;

    public EstadoSede(Producto[] productos, Existencia[] existencias, ReservaPersistida[] reservas,
                      Pedido[] pedidos, Venta[] ventas, RegistroStockConocido[] stocksConocidos,
                      int contadorIds, ResultadoCarga resultadoCarga) {
        this.productos = productos;
        this.existencias = existencias;
        this.reservas = reservas;
        this.pedidos = pedidos;
        this.ventas = ventas;
        this.stocksConocidos = stocksConocidos;
        this.contadorIds = contadorIds;
        this.resultadoCarga = resultadoCarga;
    }

    public Producto[] getProductos() { return copiar(productos); }
    public Existencia[] getExistencias() { return copiar(existencias); }
    public ReservaPersistida[] getReservas() { return copiar(reservas); }
    public Pedido[] getPedidos() { return copiar(pedidos); }
    public Venta[] getVentas() { return copiar(ventas); }
    public RegistroStockConocido[] getStocksConocidos() { return copiar(stocksConocidos); }
    public int getContadorIds() { return contadorIds; }
    public ResultadoCarga getResultadoCarga() { return resultadoCarga; }

    private static Producto[] copiar(Producto[] origen) {
        Producto[] copia = new Producto[origen.length];
        for (int i = 0; i < origen.length; i++) {
            Producto p = origen[i];
            copia[i] = new Producto(p.getCodigo(), p.getNombre(), p.getDescripcion(),
                    p.getCategoria(), p.getPrecio());
        }
        return copia;
    }

    private static Existencia[] copiar(Existencia[] origen) {
        Existencia[] copia = new Existencia[origen.length];
        for (int i = 0; i < origen.length; i++) {
            copia[i] = origen[i].copia();
        }
        return copia;
    }

    private static ReservaPersistida[] copiar(ReservaPersistida[] origen) {
        ReservaPersistida[] copia = new ReservaPersistida[origen.length];
        for (int i = 0; i < origen.length; i++) {
            ReservaPersistida r = origen[i];
            copia[i] = new ReservaPersistida(r.getIdPedido(), r.getCodigoProducto(), r.getCantidad());
        }
        return copia;
    }

    private static Pedido[] copiar(Pedido[] origen) {
        Pedido[] copia = new Pedido[origen.length];
        for (int i = 0; i < origen.length; i++) {
            copia[i] = origen[i].copia();
        }
        return copia;
    }

    private static Venta[] copiar(Venta[] origen) {
        Venta[] copia = new Venta[origen.length];
        System.arraycopy(origen, 0, copia, 0, origen.length);
        return copia;
    }

    private static RegistroStockConocido[] copiar(RegistroStockConocido[] origen) {
        RegistroStockConocido[] copia = new RegistroStockConocido[origen.length];
        for (int i = 0; i < origen.length; i++) {
            RegistroStockConocido r = origen[i];
            copia[i] = RegistroStockConocido.desdeModelo(r.codigoSede, r.codigoProducto, r.stock);
        }
        return copia;
    }
}
