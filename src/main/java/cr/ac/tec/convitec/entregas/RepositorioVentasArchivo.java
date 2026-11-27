package cr.ac.tec.convitec.entregas;

import cr.ac.tec.convitec.modelo.Venta;
import cr.ac.tec.convitec.persistencia.AlmacenArchivos;
import cr.ac.tec.convitec.persistencia.PersistenciaException;
import cr.ac.tec.convitec.persistencia.RegistroVenta;
import cr.ac.tec.convitec.persistencia.ResultadoCarga;
import cr.ac.tec.convitec.servicios.IRepositorioVentas;

import java.nio.file.Path;

/** Repositorio local de ventas respaldado por ventas.json. */
public final class RepositorioVentasArchivo implements IRepositorioVentas {

    private final AlmacenArchivos almacen;
    private final ResultadoCarga resultadoCarga;
    private Venta[] ventas;

    public RepositorioVentasArchivo(String carpetaDatos) {
        this(carpetaDatos == null ? null : Path.of(carpetaDatos));
    }

    public RepositorioVentasArchivo(Path carpetaDatos) {
        if (carpetaDatos == null) {
            throw new IllegalArgumentException("La carpeta de datos no puede ser null");
        }
        resultadoCarga = new ResultadoCarga();
        almacen = new AlmacenArchivos(carpetaDatos, resultadoCarga);
        ventas = cargarVentas();
    }

    @Override
    public synchronized void guardar(Venta venta) {
        if (venta == null) {
            throw new IllegalArgumentException("No se puede guardar una venta nula.");
        }
        if (buscarPorIdPedido(venta.getIdPedido()) != null) {
            throw new IllegalStateException("El pedido '" + venta.getIdPedido()
                    + "' ya tiene una venta guardada.");
        }
        Venta[] nuevasVentas = new Venta[ventas.length + 1];
        System.arraycopy(ventas, 0, nuevasVentas, 0, ventas.length);
        nuevasVentas[ventas.length] = venta;
        RegistroVenta[] registros = new RegistroVenta[nuevasVentas.length];
        for (int i = 0; i < nuevasVentas.length; i++) {
            registros[i] = RegistroVenta.desdeModelo(nuevasVentas[i]);
        }
        almacen.guardarArreglo("ventas.json", registros);
        ventas = nuevasVentas;
    }

    @Override
    public synchronized Venta[] listar() {
        Venta[] copia = new Venta[ventas.length];
        System.arraycopy(ventas, 0, copia, 0, ventas.length);
        return copia;
    }

    @Override
    public synchronized Venta buscarPorIdPedido(String idPedido) {
        if (idPedido == null) {
            return null;
        }
        String buscado = idPedido.trim();
        for (Venta venta : ventas) {
            if (venta.getIdPedido().equals(buscado)) {
                return venta;
            }
        }
        return null;
    }

    public synchronized int getCantidad() {
        return ventas.length;
    }

    public synchronized String[] getAvisosCarga() {
        return resultadoCarga.getAvisos();
    }

    private Venta[] cargarVentas() {
        RegistroVenta[] registros = almacen.cargarArreglo("ventas.json", RegistroVenta[].class);
        Venta[] cargadas = new Venta[registros.length];
        int cantidad = 0;
        for (int i = 0; i < registros.length; i++) {
            try {
                if (registros[i] == null) {
                    throw new PersistenciaException("registro null");
                }
                Venta venta = registros[i].haciaModelo();
                boolean duplicada = false;
                for (int j = 0; j < cantidad; j++) {
                    if (cargadas[j].getIdPedido().equals(venta.getIdPedido())) {
                        duplicada = true;
                        break;
                    }
                }
                if (duplicada) {
                    throw new PersistenciaException("pedido con venta duplicada");
                }
                cargadas[cantidad++] = venta;
            } catch (PersistenciaException e) {
                resultadoCarga.agregarAviso("Se descartó venta #" + i + ": " + e.getMessage());
            }
        }
        Venta[] resultado = new Venta[cantidad];
        System.arraycopy(cargadas, 0, resultado, 0, cantidad);
        return resultado;
    }
}
