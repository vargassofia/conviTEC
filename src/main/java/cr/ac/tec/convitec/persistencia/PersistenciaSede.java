package cr.ac.tec.convitec.persistencia;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import cr.ac.tec.convitec.catalogo.CatalogoGlobal;
import cr.ac.tec.convitec.catalogo.InventarioLocal;
import cr.ac.tec.convitec.catalogo.ReservaPersistida;
import cr.ac.tec.convitec.config.SedesConocidas;
import cr.ac.tec.convitec.modelo.Existencia;
import cr.ac.tec.convitec.modelo.EstadoPedido;
import cr.ac.tec.convitec.modelo.Pedido;
import cr.ac.tec.convitec.modelo.Producto;
import cr.ac.tec.convitec.modelo.Venta;
import cr.ac.tec.convitec.pedidos.ServicioPedidos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persistencia local de una sede; nunca resuelve rutas fuera de su carpeta. */
public final class PersistenciaSede {

    private static final Gson GSON = new Gson();
    private static final String ARCHIVO_PRODUCTOS = "productos.json";
    private static final String ARCHIVO_INVENTARIO = "inventario.json";
    private static final String ARCHIVO_RESERVAS = "reservas.json";
    private static final String ARCHIVO_PEDIDOS = "pedidos.json";
    private static final String ARCHIVO_VENTAS = "ventas.json";
    private static final String ARCHIVO_STOCK = "stock-conocido.json";
    private static final String ARCHIVO_META = "meta.json";

    private final Path carpeta;
    private final AlmacenArchivos almacen;
    private final ResultadoCarga resultadoCarga = new ResultadoCarga();

    public PersistenciaSede(String carpetaDatos) {
        this(carpetaDatos == null ? null : Path.of(carpetaDatos));
    }

    public PersistenciaSede(Path carpetaDatos) {
        if (carpetaDatos == null) {
            throw new IllegalArgumentException("La carpeta de datos no puede ser null");
        }
        carpeta = carpetaDatos.toAbsolutePath().normalize();
        almacen = new AlmacenArchivos(carpeta, resultadoCarga);
    }

    public void guardarInventario(InventarioLocal inventario) {
        if (inventario == null) {
            throw new IllegalArgumentException("El inventario no puede ser null");
        }
        Existencia[] existencias;
        ReservaPersistida[] reservas;
        synchronized (inventario) {
            existencias = inventario.exportarExistencias();
            reservas = inventario.exportarReservas();
        }
        synchronized (this) {
            guardarExistencias(existencias);
            guardarReservas(reservas);
        }
    }

    public synchronized void guardarExistencias(Existencia[] existencias) {
        RegistroExistencia[] registros = new RegistroExistencia[existencias.length];
        for (int i = 0; i < existencias.length; i++) {
            registros[i] = RegistroExistencia.desdeModelo(existencias[i]);
        }
        almacen.guardarArreglo(ARCHIVO_INVENTARIO, registros);
    }

    public void guardarReservas(InventarioLocal inventario) {
        if (inventario == null) {
            throw new IllegalArgumentException("El inventario no puede ser null");
        }
        ReservaPersistida[] reservas = inventario.exportarReservas();
        guardarReservas(reservas);
    }

    public synchronized void guardarReservas(ReservaPersistida[] reservas) {
        RegistroReserva[] registros = new RegistroReserva[reservas.length];
        for (int i = 0; i < reservas.length; i++) {
            registros[i] = RegistroReserva.desdeModelo(reservas[i]);
        }
        almacen.guardarArreglo(ARCHIVO_RESERVAS, registros);
    }

    public void guardarPedidos(ServicioPedidos pedidos) {
        if (pedidos == null) {
            throw new IllegalArgumentException("El servicio de pedidos no puede ser null");
        }
        Pedido[] modelos;
        int contadorIds;
        synchronized (pedidos) {
            modelos = pedidos.exportarPedidos();
            contadorIds = pedidos.getContadorIds();
        }
        guardarPedidos(modelos, contadorIds);
    }

    public synchronized void guardarPedidos(Pedido[] pedidos, int contadorIds) {
        RegistroPedido[] registros = new RegistroPedido[pedidos.length];
        for (int i = 0; i < pedidos.length; i++) {
            registros[i] = RegistroPedido.desdeModelo(pedidos[i]);
        }
        almacen.guardarArreglo(ARCHIVO_PEDIDOS, registros);
        guardarMeta(contadorIds);
    }

    public synchronized void guardarVentas(Venta[] ventas) {
        RegistroVenta[] registros = new RegistroVenta[ventas.length];
        for (int i = 0; i < ventas.length; i++) {
            registros[i] = RegistroVenta.desdeModelo(ventas[i]);
        }
        almacen.guardarArreglo(ARCHIVO_VENTAS, registros);
    }

    public void guardarStockConocido(CatalogoGlobal catalogo) {
        if (catalogo == null) {
            throw new IllegalArgumentException("El catálogo no puede ser null");
        }
        RegistroStockConocido[] stocks = catalogo.exportarStocksConocidos();
        synchronized (this) {
            almacen.guardarArreglo(ARCHIVO_STOCK, stocks);
        }
    }

    public synchronized void guardarCatalogo(Producto[] productos) {
        RegistroProducto[] registros = new RegistroProducto[productos.length];
        for (int i = 0; i < productos.length; i++) {
            registros[i] = RegistroProducto.desdeModelo(productos[i]);
        }
        almacen.guardarArreglo(ARCHIVO_PRODUCTOS, registros);
    }

    public synchronized void guardarMeta(int contadorIds) {
        almacen.guardarArreglo(ARCHIVO_META, new RegistroMeta[]{RegistroMeta.desdeModelo(contadorIds)});
    }

    /** Carga, valida y reconstruye los modelos; los servicios los cargan después en sus AVL/listas. */
    public synchronized EstadoSede cargarTodo() {
        Producto[] productos = cargarProductos();
        RegistroExistencia[] registrosExistencias =
                almacen.cargarArreglo(ARCHIVO_INVENTARIO, RegistroExistencia[].class);
        RegistroReserva[] registrosReservas =
                almacen.cargarArreglo(ARCHIVO_RESERVAS, RegistroReserva[].class);
        RegistroPedido[] registrosPedidos =
                almacen.cargarArreglo(ARCHIVO_PEDIDOS, RegistroPedido[].class);
        RegistroVenta[] registrosVentas =
                almacen.cargarArreglo(ARCHIVO_VENTAS, RegistroVenta[].class);
        RegistroStockConocido[] stocks =
                almacen.cargarArreglo(ARCHIVO_STOCK, RegistroStockConocido[].class);
        RegistroMeta[] registrosMeta = almacen.cargarArreglo(ARCHIVO_META, RegistroMeta[].class);

        Existencia[] existencias = convertirExistencias(registrosExistencias, productos);
        ReservaPersistida[] reservas = convertirReservas(registrosReservas, existencias);
        ReconciliacionReservas reconciliacion = reconciliarReservas(existencias, reservas);
        existencias = reconciliacion.existencias;
        reservas = reconciliacion.reservas;
        Pedido[] pedidos = convertirPedidos(registrosPedidos);
        Venta[] ventas = convertirVentas(registrosVentas);
        RegistroStockConocido[] stocksValidos = convertirStocks(stocks, productos);
        int contadorIds = convertirMeta(registrosMeta);
        contadorIds = Math.max(contadorIds, mayorConsecutivo(pedidos));

        return new EstadoSede(productos, existencias, reservas, pedidos, ventas, stocksValidos,
                contadorIds, resultadoCarga);
    }

    public Path getCarpeta() {
        return carpeta;
    }

    public ResultadoCarga getResultadoCarga() {
        return resultadoCarga;
    }

    private Producto[] cargarProductos() {
        Path archivo = carpeta.resolve(ARCHIVO_PRODUCTOS);
        RegistroProducto[] registros = almacen.cargarArreglo(ARCHIVO_PRODUCTOS, RegistroProducto[].class);
        if (registros.length == 0) {
            Producto[] iniciales = cargarCatalogoInicial();
            if (!Files.exists(archivo) || iniciales.length > 0) {
                guardarCatalogo(iniciales);
            }
            return iniciales;
        }
        Producto[] productos = new Producto[registros.length];
        int cantidad = 0;
        for (int i = 0; i < registros.length; i++) {
            try {
                Producto candidato = registros[i] == null ? null : registros[i].haciaModelo();
                if (candidato == null || existeProducto(productos, cantidad, candidato.getCodigo())) {
                    throw new PersistenciaException("producto null o código duplicado");
                }
                productos[cantidad++] = candidato;
            } catch (PersistenciaException e) {
                resultadoCarga.agregarAviso("Se descartó producto #" + i + ": " + e.getMessage());
            }
        }
        if (cantidad == productos.length) {
            return productos;
        }
        Producto[] validos = new Producto[cantidad];
        System.arraycopy(productos, 0, validos, 0, cantidad);
        return validos;
    }

    private Producto[] cargarCatalogoInicial() {
        try (InputStream entrada = PersistenciaSede.class.getClassLoader()
                .getResourceAsStream("catalogo-inicial.json")) {
            if (entrada == null) {
                resultadoCarga.agregarAviso("No existe el recurso catalogo-inicial.json; catálogo vacío");
                return new Producto[0];
            }
            RegistroProducto[] registros = GSON.fromJson(
                    new String(entrada.readAllBytes(), StandardCharsets.UTF_8), RegistroProducto[].class);
            if (registros == null) {
                return new Producto[0];
            }
            Producto[] productos = new Producto[registros.length];
            for (int i = 0; i < registros.length; i++) {
                productos[i] = registros[i].haciaModelo();
            }
            resultadoCarga.agregarAviso("Se cargó el catálogo inicial de solo lectura");
            return productos;
        } catch (IOException | JsonParseException e) {
            throw new PersistenciaException("No se pudo cargar catalogo-inicial.json", e);
        }
    }

    private Existencia[] convertirExistencias(RegistroExistencia[] registros, Producto[] productos) {
        Existencia[] resultado = new Existencia[registros.length];
        int cantidad = 0;
        for (int i = 0; i < registros.length; i++) {
            try {
                if (registros[i] == null) {
                    throw new PersistenciaException("registro null");
                }
                Existencia existencia = registros[i].haciaModelo();
                if (!existeProducto(productos, productos.length, existencia.getCodigoProducto())
                        || existeExistencia(resultado, cantidad, existencia.getCodigoProducto())) {
                    throw new PersistenciaException("producto inexistente o código duplicado");
                }
                resultado[cantidad++] = existencia;
            } catch (PersistenciaException e) {
                resultadoCarga.agregarAviso("Se descartó existencia #" + i + ": " + e.getMessage());
            }
        }
        return copiar(resultado, cantidad);
    }

    private ReservaPersistida[] convertirReservas(RegistroReserva[] registros, Existencia[] existencias) {
        ReservaPersistida[] resultado = new ReservaPersistida[registros.length];
        int cantidad = 0;
        for (int i = 0; i < registros.length; i++) {
            try {
                if (registros[i] == null) {
                    throw new PersistenciaException("registro null");
                }
                ReservaPersistida reserva = registros[i].haciaModelo();
                if (!existeExistencia(existencias, existencias.length, reserva.getCodigoProducto())
                        || existeReserva(resultado, cantidad, reserva.getIdPedido(), reserva.getCodigoProducto())) {
                    throw new PersistenciaException("reserva duplicada o sin existencia");
                }
                resultado[cantidad++] = reserva;
            } catch (PersistenciaException e) {
                resultadoCarga.agregarAviso("Se descartó reserva #" + i + ": " + e.getMessage());
            }
        }
        return copiar(resultado, cantidad);
    }

    private ReconciliacionReservas reconciliarReservas(Existencia[] existencias,
                                                        ReservaPersistida[] reservas) {
        ReservaPersistida[] filtradas = new ReservaPersistida[reservas.length];
        int cantidad = 0;
        for (ReservaPersistida reserva : reservas) {
            long totalPrevio = 0;
            int reservadasExistencia = obtenerReservado(existencias, reserva.getCodigoProducto());
            for (int i = 0; i < cantidad; i++) {
                if (filtradas[i].getCodigoProducto().equalsIgnoreCase(reserva.getCodigoProducto())) {
                    totalPrevio += filtradas[i].getCantidad();
                }
            }
            if ((long) totalPrevio + reserva.getCantidad() <= reservadasExistencia) {
                filtradas[cantidad++] = reserva;
            } else {
                resultadoCarga.agregarAviso("Se descartó reserva incompatible de "
                        + reserva.getCodigoProducto() + " para " + reserva.getIdPedido());
            }
        }
        ReservaPersistida[] reservasValidas = copiar(filtradas, cantidad);
        Existencia[] existenciasConsistentes = new Existencia[existencias.length];
        for (int i = 0; i < existencias.length; i++) {
            Existencia existencia = existencias[i];
            long total = 0;
            for (ReservaPersistida reserva : reservasValidas) {
                if (reserva.getCodigoProducto().equalsIgnoreCase(existencia.getCodigoProducto())) {
                    total += reserva.getCantidad();
                }
            }
            if (total < existencia.getCantidadReservada()) {
                resultadoCarga.agregarAviso("Se liberaron unidades reservadas sin registro para "
                        + existencia.getCodigoProducto());
            }
            int cantidadReservada = (int) total;
            int disponible = existencia.getCantidadDisponible()
                    + existencia.getCantidadReservada() - cantidadReservada;
            existenciasConsistentes[i] = new Existencia(existencia.getCodigoProducto(),
                    disponible, cantidadReservada);
        }
        return new ReconciliacionReservas(existenciasConsistentes, reservasValidas);
    }

    private Pedido[] convertirPedidos(RegistroPedido[] registros) {
        Pedido[] resultado = new Pedido[registros.length];
        int cantidad = 0;
        for (int i = 0; i < registros.length; i++) {
            try {
                if (registros[i] == null) {
                    throw new PersistenciaException("registro null");
                }
                Pedido pedido = registros[i].haciaModelo();
                if (existePedido(resultado, cantidad, pedido.getId())) {
                    throw new PersistenciaException("id duplicado");
                }
                if ((pedido.getEstado() == EstadoPedido.CONFIRMADO
                        || pedido.getEstado() == EstadoPedido.ENTREGADO)
                        && pedido.getDetalles().length == 0) {
                    throw new PersistenciaException("pedido confirmado/entregado sin detalles");
                }
                resultado[cantidad++] = pedido;
            } catch (PersistenciaException e) {
                resultadoCarga.agregarAviso("Se descartó pedido #" + i + ": " + e.getMessage());
            }
        }
        return copiar(resultado, cantidad);
    }

    private Venta[] convertirVentas(RegistroVenta[] registros) {
        Venta[] resultado = new Venta[registros.length];
        int cantidad = 0;
        for (int i = 0; i < registros.length; i++) {
            try {
                if (registros[i] == null) {
                    throw new PersistenciaException("registro null");
                }
                Venta venta = registros[i].haciaModelo();
                if (existeVenta(resultado, cantidad, venta.getIdPedido())) {
                    throw new PersistenciaException("venta duplicada para pedido");
                }
                resultado[cantidad++] = venta;
            } catch (PersistenciaException e) {
                resultadoCarga.agregarAviso("Se descartó venta #" + i + ": " + e.getMessage());
            }
        }
        return copiar(resultado, cantidad);
    }

    private RegistroStockConocido[] convertirStocks(RegistroStockConocido[] registros, Producto[] productos) {
        RegistroStockConocido[] resultado = new RegistroStockConocido[registros.length];
        int cantidad = 0;
        for (int i = 0; i < registros.length; i++) {
            try {
                RegistroStockConocido stock = registros[i];
                if (stock == null) {
                    throw new PersistenciaException("registro null");
                }
                stock.validar();
                if (!existeProducto(productos, productos.length, stock.codigoProducto)
                        || !SedesConocidas.esCodigoValido(stock.codigoSede)
                        || existeStock(resultado, cantidad, stock.codigoSede, stock.codigoProducto)) {
                    throw new PersistenciaException("producto inexistente o stock duplicado");
                }
                resultado[cantidad++] = stock;
            } catch (PersistenciaException e) {
                resultadoCarga.agregarAviso("Se descartó stock conocido #" + i + ": " + e.getMessage());
            }
        }
        RegistroStockConocido[] copia = new RegistroStockConocido[cantidad];
        System.arraycopy(resultado, 0, copia, 0, cantidad);
        return copia;
    }

    private int convertirMeta(RegistroMeta[] registros) {
        if (registros.length == 0) {
            return 0;
        }
        if (registros[0] == null) {
            resultadoCarga.agregarAviso("Se descartaron metadatos inválidos");
            return 0;
        }
        try {
            int contador = registros[0].haciaContador();
            if (registros.length > 1) {
                resultadoCarga.agregarAviso("Se ignoraron registros meta adicionales");
            }
            return contador;
        } catch (PersistenciaException e) {
            resultadoCarga.agregarAviso("Se descartaron metadatos: " + e.getMessage());
            return 0;
        }
    }

    private int mayorConsecutivo(Pedido[] pedidos) {
        int mayor = 0;
        for (Pedido pedido : pedidos) {
            String id = pedido.getId();
            int separador = id.lastIndexOf('-');
            if (separador >= 0) {
                try {
                    int numero = Integer.parseInt(id.substring(separador + 1));
                    mayor = Math.max(mayor, numero);
                } catch (NumberFormatException ignored) {
                    // El formato no corresponde a un identificador consecutivo.
                }
            }
        }
        return mayor;
    }

    private int obtenerReservado(Existencia[] existencias, String codigo) {
        for (Existencia existencia : existencias) {
            if (existencia.getCodigoProducto().equalsIgnoreCase(codigo)) {
                return existencia.getCantidadReservada();
            }
        }
        return 0;
    }

    private boolean existeProducto(Producto[] arreglo, int cantidad, String codigo) {
        for (int i = 0; i < cantidad; i++) {
            if (arreglo[i].getCodigo().equalsIgnoreCase(codigo)) {
                return true;
            }
        }
        return false;
    }

    private boolean existeExistencia(Existencia[] arreglo, int cantidad, String codigo) {
        for (int i = 0; i < cantidad; i++) {
            if (arreglo[i].getCodigoProducto().equalsIgnoreCase(codigo)) {
                return true;
            }
        }
        return false;
    }

    private boolean existeReserva(ReservaPersistida[] arreglo, int cantidad, String id, String codigo) {
        for (int i = 0; i < cantidad; i++) {
            if (arreglo[i].getIdPedido().equals(id)
                    && arreglo[i].getCodigoProducto().equalsIgnoreCase(codigo)) {
                return true;
            }
        }
        return false;
    }

    private boolean existePedido(Pedido[] arreglo, int cantidad, String id) {
        for (int i = 0; i < cantidad; i++) {
            if (arreglo[i].getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean existeVenta(Venta[] arreglo, int cantidad, String idPedido) {
        for (int i = 0; i < cantidad; i++) {
            if (arreglo[i].getIdPedido().equals(idPedido)) {
                return true;
            }
        }
        return false;
    }

    private boolean existeStock(RegistroStockConocido[] arreglo, int cantidad, String sede, String codigo) {
        for (int i = 0; i < cantidad; i++) {
            if (arreglo[i].codigoSede.equalsIgnoreCase(sede)
                    && arreglo[i].codigoProducto.equalsIgnoreCase(codigo)) {
                return true;
            }
        }
        return false;
    }

    private Existencia[] copiar(Existencia[] arreglo, int cantidad) {
        Existencia[] copia = new Existencia[cantidad];
        System.arraycopy(arreglo, 0, copia, 0, cantidad);
        return copia;
    }

    private ReservaPersistida[] copiar(ReservaPersistida[] arreglo, int cantidad) {
        ReservaPersistida[] copia = new ReservaPersistida[cantidad];
        System.arraycopy(arreglo, 0, copia, 0, cantidad);
        return copia;
    }

    private Pedido[] copiar(Pedido[] arreglo, int cantidad) {
        Pedido[] copia = new Pedido[cantidad];
        System.arraycopy(arreglo, 0, copia, 0, cantidad);
        return copia;
    }

    private Venta[] copiar(Venta[] arreglo, int cantidad) {
        Venta[] copia = new Venta[cantidad];
        System.arraycopy(arreglo, 0, copia, 0, cantidad);
        return copia;
    }

    private static final class ReconciliacionReservas {
        private final Existencia[] existencias;
        private final ReservaPersistida[] reservas;

        private ReconciliacionReservas(Existencia[] existencias, ReservaPersistida[] reservas) {
            this.existencias = existencias;
            this.reservas = reservas;
        }
    }
}
