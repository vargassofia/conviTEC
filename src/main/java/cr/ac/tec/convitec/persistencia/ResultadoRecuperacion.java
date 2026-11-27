package cr.ac.tec.convitec.persistencia;

import cr.ac.tec.convitec.catalogo.CatalogoGlobal;
import cr.ac.tec.convitec.catalogo.InventarioLocal;
import cr.ac.tec.convitec.entregas.RepositorioVentasArchivo;
import cr.ac.tec.convitec.pedidos.ServicioPedidos;
import cr.ac.tec.convitec.servicios.IMensajeria;

/** Servicios reconstruidos y avisos del último arranque. */
public final class ResultadoRecuperacion {
    private final InventarioLocal inventario;
    private final CatalogoGlobal catalogo;
    private final ServicioPedidos servicioPedidos;
    private final RepositorioVentasArchivo repositorioVentas;
    private final LiberacionPendiente[] liberacionesPendientes;
    private final ResultadoCarga resultadoCarga;
    private final IMensajeria mensajeria;

    ResultadoRecuperacion(InventarioLocal inventario, CatalogoGlobal catalogo,
                          ServicioPedidos servicioPedidos, RepositorioVentasArchivo repositorioVentas,
                          LiberacionPendiente[] liberacionesPendientes, ResultadoCarga resultadoCarga,
                          IMensajeria mensajeria) {
        this.inventario = inventario;
        this.catalogo = catalogo;
        this.servicioPedidos = servicioPedidos;
        this.repositorioVentas = repositorioVentas;
        this.liberacionesPendientes = copiar(liberacionesPendientes);
        this.resultadoCarga = resultadoCarga;
        this.mensajeria = mensajeria;
    }

    public InventarioLocal getInventario() { return inventario; }
    public CatalogoGlobal getCatalogo() { return catalogo; }
    public ServicioPedidos getServicioPedidos() { return servicioPedidos; }
    public RepositorioVentasArchivo getRepositorioVentas() { return repositorioVentas; }
    public LiberacionPendiente[] getLiberacionesPendientes() { return copiar(liberacionesPendientes); }
    public ResultadoCarga getResultadoCarga() { return resultadoCarga; }
    public IMensajeria getMensajeria() { return mensajeria; }

    public String getInforme() {
        String[] avisos = resultadoCarga.getAvisos();
        StringBuilder informe = new StringBuilder("Recuperación de sede completada: ")
                .append(inventario.exportarExistencias().length).append(" existencias, ")
                .append(inventario.exportarReservas().length).append(" reservas, ")
                .append(servicioPedidos.exportarPedidos().length).append(" pedidos y ")
                .append(repositorioVentas.listar().length).append(" ventas.");
        if (liberacionesPendientes.length > 0) {
            informe.append(" Quedan ").append(liberacionesPendientes.length)
                    .append(" liberaciones remotas por enviar.");
        }
        for (String aviso : avisos) {
            informe.append(System.lineSeparator()).append("Aviso: ").append(aviso);
        }
        return informe.toString();
    }

    private static LiberacionPendiente[] copiar(LiberacionPendiente[] origen) {
        LiberacionPendiente[] copia = new LiberacionPendiente[origen.length];
        for (int i = 0; i < origen.length; i++) {
            LiberacionPendiente original = origen[i];
            copia[i] = new LiberacionPendiente(original.getCodigoSede(), original.getIdPedido(),
                    original.getCodigoProducto());
        }
        return copia;
    }
}
