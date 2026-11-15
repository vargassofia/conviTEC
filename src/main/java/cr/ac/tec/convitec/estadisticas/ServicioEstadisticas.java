package cr.ac.tec.convitec.estadisticas;

import cr.ac.tec.convitec.modelo.DetalleVenta;
import cr.ac.tec.convitec.modelo.Venta;
import cr.ac.tec.convitec.servicios.IRepositorioVentas;
import cr.ac.tec.convitec.servicios.IServicioEstadisticas;

import java.text.Normalizer;
import java.time.LocalDate;

/**
 * Estadísticas de una sede: historial de ventas, producto estrella y peores pedidos.
 *
 * - Lee las ventas con repositorio.listar() en CADA consulta, así siempre están actualizadas.
 * - Por seguridad ignora las ventas de otra sede (compara sin tildes ni mayúsculas).
 * - Todos los ordenamientos usan el MergeSort propio (Ordenamiento + Comparador).
 * - Solo usa arreglos nativos.
 *
 * Reglas de orden y desempate:
 * - Historial: fecha más reciente primero; si empatan, idVenta menor primero.
 * - Ranking / producto estrella: mayor monto; si empatan, más unidades;
 *   si siguen empatados, el código de producto menor alfabéticamente.
 * - Peores pedidos: menor rendimiento primero; si empatan, mayor tiempo de traslado;
 *   si siguen empatados, el idPedido menor alfabéticamente.
 */
public class ServicioEstadisticas implements IServicioEstadisticas {

    private static final int CAPACIDAD_INICIAL = 8;

    /** Historial: más reciente primero; empate -> idVenta menor primero. */
    private static final Comparador<Venta> POR_FECHA_DESC = (a, b) -> {
        int c = b.getFechaHora().compareTo(a.getFechaHora());
        return c != 0 ? c : a.getIdVenta().compareTo(b.getIdVenta());
    };

    /** Peores pedidos: menor rendimiento; empate -> más tiempo; empate -> idPedido menor. */
    private static final Comparador<Venta> POR_RENDIMIENTO_ASC = (a, b) -> {
        int c = Double.compare(a.getRendimiento(), b.getRendimiento());
        if (c != 0) {
            return c;
        }
        c = Integer.compare(b.getTiempoTraslado(), a.getTiempoTraslado());
        return c != 0 ? c : a.getIdPedido().compareTo(b.getIdPedido());
    };

    /** Ranking: mayor monto; empate -> más unidades; empate -> código menor. */
    private static final Comparador<ResumenProducto> POR_MONTO_DESC = (a, b) -> {
        int c = Double.compare(b.getMontoTotal(), a.getMontoTotal());
        if (c != 0) {
            return c;
        }
        c = Integer.compare(b.getUnidadesVendidas(), a.getUnidadesVendidas());
        return c != 0 ? c : a.getCodigoProducto().compareTo(b.getCodigoProducto());
    };

    private final String sede;
    private final String claveSede;
    private final IRepositorioVentas repositorio;

    /**
     * @throws IllegalArgumentException si la sede está vacía o el repositorio es nulo.
     */
    public ServicioEstadisticas(String sede, IRepositorioVentas repositorio) {
        if (sede == null || sede.isBlank()) {
            throw new IllegalArgumentException("El servicio de estadísticas necesita la sede.");
        }
        if (repositorio == null) {
            throw new IllegalArgumentException("El servicio de estadísticas necesita un repositorio de ventas.");
        }
        this.sede = sede.trim();
        this.claveSede = normalizar(sede);
        this.repositorio = repositorio;
    }

    public String getSede() {
        return sede;
    }

    // ------------------------------------------------------------------
    // Historial
    // ------------------------------------------------------------------

    @Override
    public Venta[] historialVentas() {
        return Ordenamiento.ordenar(ventasDeLaSede(), POR_FECHA_DESC);
    }

    @Override
    public Venta[] historialVentas(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Las fechas del rango no pueden ser nulas.");
        }
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("Rango de fechas inválido: " + desde + " es posterior a " + hasta + ".");
        }
        Venta[] ventas = ventasDeLaSede();

        // Contar y luego copiar las que están en el rango (ambos días incluidos)
        int cantidad = 0;
        for (int i = 0; i < ventas.length; i++) {
            if (estaEnRango(ventas[i], desde, hasta)) {
                cantidad++;
            }
        }
        Venta[] enRango = new Venta[cantidad];
        int pos = 0;
        for (int i = 0; i < ventas.length; i++) {
            if (estaEnRango(ventas[i], desde, hasta)) {
                enRango[pos] = ventas[i];
                pos++;
            }
        }
        return Ordenamiento.ordenar(enRango, POR_FECHA_DESC);
    }

    // ------------------------------------------------------------------
    // Productos
    // ------------------------------------------------------------------

    /**
     * Agrupa los detalles de todas las ventas por código de producto (búsqueda lineal,
     * sin HashMap) y ordena de mayor a menor monto.
     * Si un código aparece con nombres distintos, se usa el primer nombre encontrado.
     */
    @Override
    public ResumenProducto[] rankingProductos() {
        Venta[] ventas = ventasDeLaSede();

        String[] codigos = new String[CAPACIDAD_INICIAL];
        String[] nombres = new String[CAPACIDAD_INICIAL];
        int[] unidades = new int[CAPACIDAD_INICIAL];
        double[] montos = new double[CAPACIDAD_INICIAL];
        int cantidad = 0;

        for (int v = 0; v < ventas.length; v++) {
            DetalleVenta[] detalles = ventas[v].getDetalles();
            for (int d = 0; d < detalles.length; d++) {
                DetalleVenta detalle = detalles[d];
                int pos = buscarCodigo(codigos, cantidad, detalle.getCodigoProducto());
                if (pos == -1) {
                    if (cantidad == codigos.length) {
                        // Crecer los cuatro arreglos al doble
                        int nueva = codigos.length * 2;
                        codigos = copiar(codigos, new String[nueva], cantidad);
                        nombres = copiar(nombres, new String[nueva], cantidad);
                        unidades = copiar(unidades, new int[nueva], cantidad);
                        montos = copiar(montos, new double[nueva], cantidad);
                    }
                    pos = cantidad;
                    codigos[pos] = detalle.getCodigoProducto();
                    nombres[pos] = detalle.getNombreProducto();
                    cantidad++;
                }
                unidades[pos] += detalle.getCantidad();
                montos[pos] += detalle.getSubtotal();
            }
        }

        ResumenProducto[] resumen = new ResumenProducto[cantidad];
        for (int i = 0; i < cantidad; i++) {
            resumen[i] = new ResumenProducto(codigos[i], nombres[i], unidades[i], montos[i]);
        }
        return Ordenamiento.ordenar(resumen, POR_MONTO_DESC);
    }

    /** El primero del ranking, o null si la sede no tiene ventas. */
    @Override
    public ProductoEstrella productoEstrella() {
        ResumenProducto[] ranking = rankingProductos();
        if (ranking.length == 0) {
            return null;
        }
        ResumenProducto primero = ranking[0];
        return new ProductoEstrella(primero.getCodigoProducto(), primero.getNombreProducto(),
                primero.getUnidadesVendidas(), primero.getMontoTotal());
    }

    // ------------------------------------------------------------------
    // Peores pedidos y totales
    // ------------------------------------------------------------------

    /**
     * Rendimiento = monto ÷ tiempo de traslado (ver Venta.getRendimiento: el tiempo 0 cuenta como 1).
     */
    @Override
    public Venta[] peoresPedidos(int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad de peores pedidos debe ser mayor que 0 (llegó "
                    + cantidad + ").");
        }
        Venta[] ordenadas = Ordenamiento.ordenar(ventasDeLaSede(), POR_RENDIMIENTO_ASC);
        int n = Math.min(cantidad, ordenadas.length);
        Venta[] peores = new Venta[n];
        for (int i = 0; i < n; i++) {
            peores[i] = ordenadas[i];
        }
        return peores;
    }

    @Override
    public double montoTotalVendido() {
        Venta[] ventas = ventasDeLaSede();
        double total = 0;
        for (int i = 0; i < ventas.length; i++) {
            total += ventas[i].getMontoTotal();
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Ventas actuales del repositorio que son de esta sede (ignora nulos y otras sedes). */
    private Venta[] ventasDeLaSede() {
        Venta[] todas = repositorio.listar();
        if (todas == null) {
            return new Venta[0];
        }
        int cantidad = 0;
        for (int i = 0; i < todas.length; i++) {
            if (esDeLaSede(todas[i])) {
                cantidad++;
            }
        }
        Venta[] propias = new Venta[cantidad];
        int pos = 0;
        for (int i = 0; i < todas.length; i++) {
            if (esDeLaSede(todas[i])) {
                propias[pos] = todas[i];
                pos++;
            }
        }
        return propias;
    }

    private boolean esDeLaSede(Venta venta) {
        return venta != null && normalizar(venta.getSede()).equals(claveSede);
    }

    private static boolean estaEnRango(Venta venta, LocalDate desde, LocalDate hasta) {
        LocalDate dia = venta.getFechaHora().toLocalDate();
        return !dia.isBefore(desde) && !dia.isAfter(hasta);
    }

    private static int buscarCodigo(String[] codigos, int cantidad, String codigo) {
        for (int i = 0; i < cantidad; i++) {
            if (codigos[i].equals(codigo)) {
                return i;
            }
        }
        return -1;
    }

    /** "  San  José " -> "san jose" (igual que la búsqueda del Grafo). */
    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private static String[] copiar(String[] origen, String[] destino, int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            destino[i] = origen[i];
        }
        return destino;
    }

    private static int[] copiar(int[] origen, int[] destino, int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            destino[i] = origen[i];
        }
        return destino;
    }

    private static double[] copiar(double[] origen, double[] destino, int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            destino[i] = origen[i];
        }
        return destino;
    }
}
