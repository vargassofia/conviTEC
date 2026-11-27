package cr.ac.tec.convitec.demo;

import cr.ac.tec.convitec.servicios.ICatalogoGlobal;

/**
 * Implementación provisional conservada para pruebas de consola antiguas.
 * Las sedes reales usan CatalogoGlobal y PersistenciaSede.
 *
 * Arreglos paralelos (sede, código, cantidad) que crecen manualmente. Todo synchronized.
 */
@Deprecated
public class CatalogoGlobalDemo implements ICatalogoGlobal {

    private String[] sedes = new String[8];
    private String[] codigos = new String[8];
    private int[] cantidades = new int[8];
    private int cantidad;

    @Override
    public synchronized void actualizarStock(String codigoSede, String codigoProducto, int cantidadNueva) {
        if (codigoSede == null || codigoProducto == null || cantidadNueva < 0) {
            return; // dato inválido: se ignora (la mensajería ya filtra estos casos)
        }
        int pos = buscar(codigoSede, codigoProducto);
        if (pos == -1) {
            if (cantidad == sedes.length) {
                crecer();
            }
            pos = cantidad;
            sedes[pos] = codigoSede;
            codigos[pos] = codigoProducto;
            cantidad++;
        }
        cantidades[pos] = cantidadNueva;
    }

    /** Último stock conocido, o -1 si no se conoce. */
    public synchronized int getStock(String codigoSede, String codigoProducto) {
        int pos = buscar(codigoSede, codigoProducto);
        return pos == -1 ? -1 : cantidades[pos];
    }

    public synchronized int getCantidadRegistros() {
        return cantidad;
    }

    /** Una línea por registro: "LIM  B01  stock 3". */
    public synchronized String[] listar() {
        String[] lineas = new String[cantidad];
        for (int i = 0; i < cantidad; i++) {
            lineas[i] = String.format("%-4s %-5s stock %d", sedes[i], codigos[i], cantidades[i]);
        }
        return lineas;
    }

    private int buscar(String sede, String codigo) {
        for (int i = 0; i < cantidad; i++) {
            if (sedes[i].equals(sede) && codigos[i].equals(codigo)) {
                return i;
            }
        }
        return -1;
    }

    private void crecer() {
        int nueva = sedes.length * 2;
        String[] s = new String[nueva];
        String[] c = new String[nueva];
        int[] n = new int[nueva];
        for (int i = 0; i < cantidad; i++) {
            s[i] = sedes[i];
            c[i] = codigos[i];
            n[i] = cantidades[i];
        }
        sedes = s;
        codigos = c;
        cantidades = n;
    }
}
