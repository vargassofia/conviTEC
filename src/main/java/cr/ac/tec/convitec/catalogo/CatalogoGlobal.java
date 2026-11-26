package cr.ac.tec.convitec.catalogo;

import cr.ac.tec.convitec.estructuras.ArbolAVL;
import cr.ac.tec.convitec.modelo.Producto;
import cr.ac.tec.convitec.servicios.ICatalogoGlobal;

/** Catálogo común de productos y copia del último stock conocido de cada sede. */
public class CatalogoGlobal implements ICatalogoGlobal {

    private static final String[] SEDES_VALIDAS = {"ALA", "LIB", "LIM", "SJO"};

    private final ArbolAVL<String, Producto> productos = new ArbolAVL<>();
    private final ArbolAVL<String, Integer> existenciasConocidas = new ArbolAVL<>();
    private int mensajesDescartados;

    /** Crea un catálogo global vacío. */
    public CatalogoGlobal() {
    }

    /** Crea el catálogo e incorpora los productos indicados. */
    public CatalogoGlobal(Producto[] productosIniciales) {
        if (productosIniciales == null) {
            throw new IllegalArgumentException("El arreglo de productos no puede ser null");
        }
        for (Producto producto : productosIniciales) {
            registrarProducto(producto);
        }
    }

    /** Registra un producto nuevo o reemplaza el producto del mismo código. */
    public synchronized boolean registrarProducto(Producto producto) {
        if (producto == null) {
            throw new IllegalArgumentException("El producto no puede ser null");
        }
        String codigo = normalizarCodigo(producto.getCodigo());
        if (productos.contiene(codigo)) {
            productos.actualizar(codigo, producto);
            return false;
        }
        productos.insertar(codigo, producto);
        return true;
    }

    /** Guarda el stock más reciente; descarta mensajes inválidos sin lanzar excepción. */
    @Override
    public synchronized void actualizarStock(String codigoSede, String codigoProducto, int cantidadNueva) {
        if (!sedeValida(codigoSede) || !codigoValido(codigoProducto) || cantidadNueva < 0) {
            mensajesDescartados++;
            return;
        }
        String sede = normalizarSede(codigoSede);
        String codigo = normalizarCodigo(codigoProducto);
        String clave = crearClaveStock(sede, codigo);
        if (existenciasConocidas.contiene(clave)) {
            existenciasConocidas.actualizar(clave, cantidadNueva);
        } else {
            existenciasConocidas.insertar(clave, cantidadNueva);
        }
    }

    /** Devuelve el último stock conocido para el producto, ordenado por código de sede. */
    public synchronized DisponibilidadSede[] disponibilidadDe(String codigoProducto) {
        if (!codigoValido(codigoProducto)) {
            throw new IllegalArgumentException("El código del producto no puede ser null ni vacío");
        }
        String codigo = normalizarCodigo(codigoProducto);
        int cantidad = 0;
        for (String sede : SEDES_VALIDAS) {
            if (existenciasConocidas.contiene(crearClaveStock(sede, codigo))) {
                cantidad++;
            }
        }

        DisponibilidadSede[] resultado = new DisponibilidadSede[cantidad];
        int indice = 0;
        for (String sede : SEDES_VALIDAS) {
            String clave = crearClaveStock(sede, codigo);
            Integer stock = existenciasConocidas.buscar(clave);
            if (stock != null) {
                resultado[indice++] = new DisponibilidadSede(sede, stock);
            }
        }
        return resultado;
    }

    /** Devuelve los productos del catálogo con sus stocks conocidos, ordenados por código. */
    public synchronized ProductoCatalogo[] listarCatalogo() {
        Object[] valores = productos.valoresEnOrden();
        ProductoCatalogo[] resultado = new ProductoCatalogo[valores.length];
        for (int i = 0; i < valores.length; i++) {
            Producto producto = (Producto) valores[i];
            resultado[i] = new ProductoCatalogo(producto, disponibilidadDe(producto.getCodigo()));
        }
        return resultado;
    }

    /** Busca un producto por código o devuelve null si no está registrado. */
    public synchronized Producto obtenerProducto(String codigoProducto) {
        if (!codigoValido(codigoProducto)) {
            return null;
        }
        return productos.buscar(normalizarCodigo(codigoProducto));
    }

    /** Indica si el producto está registrado en el catálogo global. */
    public synchronized boolean contieneProducto(String codigoProducto) {
        return codigoValido(codigoProducto) && productos.contiene(normalizarCodigo(codigoProducto));
    }

    /** Devuelve la cantidad de mensajes de stock descartados por inválidos. */
    public synchronized int getMensajesDescartados() {
        return mensajesDescartados;
    }

    private boolean sedeValida(String sede) {
        if (sede == null) {
            return false;
        }
        String normalizada = normalizarSede(sede);
        for (String sedeValida : SEDES_VALIDAS) {
            if (sedeValida.equals(normalizada)) {
                return true;
            }
        }
        return false;
    }

    private boolean codigoValido(String codigo) {
        return codigo != null && !codigo.trim().isEmpty() && codigo.indexOf('|') < 0;
    }

    private String normalizarCodigo(String codigo) {
        return normalizarMayusculas(codigo);
    }

    private String normalizarSede(String sede) {
        return normalizarMayusculas(sede);
    }

    private String normalizarMayusculas(String texto) {
        String recortado = texto.trim();
        StringBuilder resultado = new StringBuilder(recortado.length());
        for (int i = 0; i < recortado.length(); i++) {
            resultado.append(Character.toUpperCase(recortado.charAt(i)));
        }
        return resultado.toString();
    }

    private String crearClaveStock(String sede, String codigo) {
        return sede + "|" + codigo;
    }

}
