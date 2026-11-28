package cr.ac.tec.convitec.web;

import cr.ac.tec.convitec.catalogo.DisponibilidadSede;
import cr.ac.tec.convitec.catalogo.ExistenciaInventario;
import cr.ac.tec.convitec.catalogo.ProductoCatalogo;
import cr.ac.tec.convitec.catalogo.ServicioInventario;
import cr.ac.tec.convitec.catalogo.ServicioInventarioException;
import cr.ac.tec.convitec.config.SedesConocidas;
import cr.ac.tec.convitec.modelo.Producto;

/**
 * Lógica de la pantalla de Inventario (GET /api/inventario, GET /api/catalogo, POST /api/inventario/agregar).
 *
 * Todo pasa por ServicioInventario (Persona 2): aquí solo se combinan sus resultados en la forma que
 * necesita la pantalla.
 * - Inventario de la sede: TODOS los productos del catálogo, con 0 unidades si la sede todavía no
 *   tiene existencia de ese producto (así se le pueden agregar unidades desde la pantalla).
 * - Catálogo global: cada producto con su cantidad en cada sede. La de esta sede sale de su inventario
 *   local; la de las otras es el último stock conocido por mensajes (null si todavía no se conoce).
 */
final class ControladorInventario {

    /** Tope por operación, para evitar errores de digitación (por ejemplo 10000 en vez de 100). */
    static final int MAXIMO_AGREGAR = 10_000;

    private final ServicioInventario inventario;
    private final String codigoSede;

    ControladorInventario(ServicioInventario inventario, String codigoSede) {
        this.inventario = inventario;
        this.codigoSede = codigoSede;
    }

    /** Productos de la sede, en orden de código. */
    ProductoLocalDTO[] inventarioLocal() {
        ProductoCatalogo[] catalogo = inventario.consultarCatalogoGlobal();
        ExistenciaInventario[] existencias = inventario.consultarInventarioLocal();
        ProductoLocalDTO[] lista = new ProductoLocalDTO[catalogo.length];
        for (int i = 0; i < catalogo.length; i++) {
            Producto p = catalogo[i].getProducto();
            ExistenciaInventario e = buscarExistencia(existencias, p.getCodigo());
            lista[i] = new ProductoLocalDTO(p.getCodigo(), p.getNombre(), p.getDescripcion(), p.getCategoria(),
                    p.getPrecio(), e == null ? 0 : e.getCantidadDisponible(),
                    e == null ? 0 : e.getCantidadReservada());
        }
        return lista;
    }

    /** Catálogo global con la cantidad en cada sede (LIB, ALA, SJO, LIM), en orden de código. */
    ProductoGlobalDTO[] catalogoGlobal() {
        ProductoCatalogo[] catalogo = inventario.consultarCatalogoGlobal();
        ExistenciaInventario[] existencias = inventario.consultarInventarioLocal();
        String[] codigos = SedesConocidas.codigos();

        ProductoGlobalDTO[] lista = new ProductoGlobalDTO[catalogo.length];
        for (int i = 0; i < catalogo.length; i++) {
            Producto p = catalogo[i].getProducto();
            DisponibilidadSede[] conocidas = catalogo[i].getDisponibilidades();
            CantidadSedeDTO[] porSede = new CantidadSedeDTO[codigos.length];
            int total = 0;
            for (int s = 0; s < codigos.length; s++) {
                Integer cantidad;
                boolean esEsta = codigos[s].equals(codigoSede);
                if (esEsta) {
                    ExistenciaInventario e = buscarExistencia(existencias, p.getCodigo());
                    cantidad = e == null ? 0 : e.getCantidadDisponible();
                } else {
                    cantidad = buscarConocida(conocidas, codigos[s]);
                }
                if (cantidad != null) {
                    total += cantidad;
                }
                porSede[s] = new CantidadSedeDTO(codigos[s], SedesConocidas.nombreDe(codigos[s]), cantidad, esEsta);
            }
            lista[i] = new ProductoGlobalDTO(p.getCodigo(), p.getNombre(), p.getDescripcion(), p.getCategoria(),
                    p.getPrecio(), porSede, total);
        }
        return lista;
    }

    /**
     * Agrega unidades a un producto de esta sede y avisa a las otras sedes (ServicioInventario publica el cambio).
     * Si las unidades se agregaron pero no se pudo avisar a las otras sedes, la respuesta trae un aviso:
     * NO se debe repetir la operación, porque las unidades ya quedaron agregadas.
     *
     * @throws IllegalArgumentException si falta el código, la cantidad no es válida o el producto no existe.
     */
    ResultadoAgregarDTO agregar(SolicitudAgregar solicitud) {
        if (solicitud == null || solicitud.codigoProducto == null || solicitud.codigoProducto.isBlank()) {
            throw new IllegalArgumentException("Elija el producto al que desea agregar unidades.");
        }
        if (solicitud.cantidad == null || solicitud.cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser un número entero mayor que 0.");
        }
        if (solicitud.cantidad > MAXIMO_AGREGAR) {
            throw new IllegalArgumentException("Por seguridad, se pueden agregar como máximo " + MAXIMO_AGREGAR
                    + " unidades a la vez.");
        }
        String codigo = solicitud.codigoProducto.trim();
        try {
            int nuevo = inventario.agregarUnidades(codigo, solicitud.cantidad);
            return new ResultadoAgregarDTO(codigo.toUpperCase(), nuevo, null);
        } catch (ServicioInventarioException e) {
            // Las unidades SÍ se agregaron: solo falló el aviso a las otras sedes
            ExistenciaInventario existencia = buscarExistencia(inventario.consultarInventarioLocal(), codigo);
            return new ResultadoAgregarDTO(codigo.toUpperCase(),
                    existencia == null ? 0 : existencia.getCantidadDisponible(),
                    "Las unidades se agregaron, pero no se pudo avisar a las otras sedes (sin conexión con la "
                            + "mensajería). Se enterarán con el próximo cambio de stock.");
        }
    }

    private static ExistenciaInventario buscarExistencia(ExistenciaInventario[] existencias, String codigo) {
        for (int i = 0; i < existencias.length; i++) {
            if (existencias[i].getCodigoProducto().equalsIgnoreCase(codigo.trim())) {
                return existencias[i];
            }
        }
        return null;
    }

    private static Integer buscarConocida(DisponibilidadSede[] conocidas, String codigoSede) {
        for (int i = 0; i < conocidas.length; i++) {
            if (conocidas[i].getCodigoSede().equalsIgnoreCase(codigoSede)) {
                return conocidas[i].getCantidadDisponible();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Formas JSON
    // ------------------------------------------------------------------

    /** Cuerpo de POST /api/inventario/agregar. Integer para detectar si no vino la cantidad. */
    static final class SolicitudAgregar {
        String codigoProducto;
        Integer cantidad;
    }

    static final class ProductoLocalDTO {
        final String codigo;
        final String nombre;
        final String descripcion;
        final String categoria;
        final double precio;
        final int disponible;
        final int reservado;

        ProductoLocalDTO(String codigo, String nombre, String descripcion, String categoria, double precio,
                         int disponible, int reservado) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.categoria = categoria;
            this.precio = precio;
            this.disponible = disponible;
            this.reservado = reservado;
        }
    }

    static final class CantidadSedeDTO {
        final String codigoSede;
        final String nombreSede;
        final Integer cantidad; // null = todavía no se conoce el stock de esa sede
        final boolean esEstaSede;

        CantidadSedeDTO(String codigoSede, String nombreSede, Integer cantidad, boolean esEstaSede) {
            this.codigoSede = codigoSede;
            this.nombreSede = nombreSede;
            this.cantidad = cantidad;
            this.esEstaSede = esEstaSede;
        }
    }

    static final class ProductoGlobalDTO {
        final String codigo;
        final String nombre;
        final String descripcion;
        final String categoria;
        final double precio;
        final CantidadSedeDTO[] sedes;
        final int totalConocido;

        ProductoGlobalDTO(String codigo, String nombre, String descripcion, String categoria, double precio,
                          CantidadSedeDTO[] sedes, int totalConocido) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.categoria = categoria;
            this.precio = precio;
            this.sedes = sedes;
            this.totalConocido = totalConocido;
        }
    }

    static final class ResultadoAgregarDTO {
        final String codigoProducto;
        final int disponible;
        final String aviso; // null si todo salió bien

        ResultadoAgregarDTO(String codigoProducto, int disponible, String aviso) {
            this.codigoProducto = codigoProducto;
            this.disponible = disponible;
            this.aviso = aviso;
        }
    }
}
