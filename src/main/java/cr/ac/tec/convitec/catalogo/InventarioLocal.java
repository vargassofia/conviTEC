package cr.ac.tec.convitec.catalogo;

import cr.ac.tec.convitec.estructuras.ArbolAVL;
import cr.ac.tec.convitec.mensajeria.RespuestaReserva;
import cr.ac.tec.convitec.modelo.Existencia;
import cr.ac.tec.convitec.modelo.Producto;
import cr.ac.tec.convitec.servicios.IInventarioLocal;

/** Inventario sincronizado de una sede y reservas asociadas a pedidos externos. */
public class InventarioLocal implements IInventarioLocal {

    private final CatalogoGlobal catalogo;
    private ArbolAVL<String, Existencia> existencias = new ArbolAVL<>();
    private ArbolAVL<String, Integer> reservas = new ArbolAVL<>();

    /** Crea un inventario local asociado al catálogo global de esta sede. */
    public InventarioLocal(CatalogoGlobal catalogo) {
        if (catalogo == null) {
            throw new IllegalArgumentException("El catálogo global no puede ser null");
        }
        this.catalogo = catalogo;
    }

    /** Reserva unidades; repetir una reserva existente del mismo pedido es idempotente. */
    @Override
    public synchronized RespuestaReserva reservar(String idPedido, String codigoProducto, int cantidad) {
        if (textoInvalido(idPedido) || codigoInvalido(codigoProducto)) {
            return RespuestaReserva.rechazo(null, "faltan idPedido o código de producto");
        }
        if (cantidad <= 0) {
            return RespuestaReserva.rechazo(null,
                    "la cantidad debe ser mayor que 0 (llegó " + cantidad + ")");
        }

        String pedido = idPedido.trim();
        String codigo = normalizarCodigo(codigoProducto);
        String claveReserva = crearClaveReserva(pedido, codigo);
        if (reservas.contiene(claveReserva)) {
            return RespuestaReserva.ok(null);
        }
        Existencia existencia = existencias.buscar(codigo);
        if (existencia == null) {
            return RespuestaReserva.rechazo(null, "el producto " + codigo + " no existe");
        }
        int disponible = existencia.getCantidadDisponible();
        if (disponible < cantidad) {
            return RespuestaReserva.rechazo(null, "stock insuficiente de " + codigo
                    + " (hay " + disponible + ", se piden " + cantidad + ")");
        }
        existencia.reservar(cantidad);
        reservas.insertar(claveReserva, cantidad);
        return RespuestaReserva.ok(null);
    }

    /** Confirma una reserva y devuelve el stock disponible nuevo; es idempotente. */
    @Override
    public synchronized int confirmar(String idPedido, String codigoProducto) {
        if (textoInvalido(idPedido) || codigoInvalido(codigoProducto)) {
            return obtenerDisponibleInterno(codigoProducto);
        }
        String codigo = normalizarCodigo(codigoProducto);
        String clave = crearClaveReserva(idPedido.trim(), codigo);
        Integer cantidad = reservas.buscar(clave);
        if (cantidad != null) {
            Existencia existencia = existencias.buscar(codigo);
            if (existencia != null) {
                existencia.confirmar(cantidad);
            }
            reservas.eliminar(clave);
        }
        return obtenerDisponibleInterno(codigo);
    }

    /** Libera una reserva y devuelve sus unidades al stock disponible; es idempotente. */
    @Override
    public synchronized void liberar(String idPedido, String codigoProducto) {
        if (textoInvalido(idPedido) || codigoInvalido(codigoProducto)) {
            return;
        }
        String codigo = normalizarCodigo(codigoProducto);
        String clave = crearClaveReserva(idPedido.trim(), codigo);
        Integer cantidad = reservas.buscar(clave);
        if (cantidad == null) {
            return;
        }
        Existencia existencia = existencias.buscar(codigo);
        if (existencia != null) {
            existencia.liberar(cantidad);
        }
        reservas.eliminar(clave);
    }

    /** Agrega stock disponible y crea la existencia local si el producto está en el catálogo. */
    public synchronized int agregarUnidades(String codigoProducto, int cantidad) {
        validarCodigoYcantidad(codigoProducto, cantidad);
        String codigo = normalizarCodigo(codigoProducto);
        if (catalogo.obtenerProducto(codigo) == null) {
            throw new IllegalArgumentException("El producto " + codigo + " no existe en el catálogo");
        }
        Existencia existencia = existencias.buscar(codigo);
        if (existencia == null) {
            existencia = new Existencia(codigo, 0);
            existencias.insertar(codigo, existencia);
        }
        existencia.agregar(cantidad);
        return existencia.getCantidadDisponible();
    }

    /** Descuenta stock disponible para una venta directa. */
    public synchronized int descontar(String codigoProducto, int cantidad) {
        validarCodigoYcantidad(codigoProducto, cantidad);
        String codigo = normalizarCodigo(codigoProducto);
        Existencia existencia = existencias.buscar(codigo);
        if (existencia == null) {
            throw new IllegalArgumentException("El producto " + codigo + " no tiene existencia local");
        }
        existencia.descontar(cantidad);
        return existencia.getCantidadDisponible();
    }

    /** Devuelve los datos locales de un producto o null si no tiene existencia local. */
    public synchronized ExistenciaInventario consultar(String codigoProducto) {
        if (codigoInvalido(codigoProducto)) {
            throw new IllegalArgumentException("El código del producto no puede ser null ni vacío");
        }
        String codigo = normalizarCodigo(codigoProducto);
        Existencia existencia = existencias.buscar(codigo);
        Producto producto = catalogo.obtenerProducto(codigo);
        if (existencia == null || producto == null) {
            return null;
        }
        return crearVista(producto, existencia);
    }

    /** Devuelve las unidades disponibles locales, o -1 si no existe localmente. */
    public synchronized int obtenerDisponible(String codigoProducto) {
        if (codigoInvalido(codigoProducto)) {
            throw new IllegalArgumentException("El código del producto no puede ser null ni vacío");
        }
        return obtenerDisponibleInterno(normalizarCodigo(codigoProducto));
    }

    /** Devuelve las existencias locales ordenadas por código de producto. */
    public synchronized ExistenciaInventario[] listarExistencias() {
        Object[] valores = existencias.valoresEnOrden();
        ExistenciaInventario[] resultado = new ExistenciaInventario[valores.length];
        for (int i = 0; i < valores.length; i++) {
            Existencia existencia = (Existencia) valores[i];
            Producto producto = catalogo.obtenerProducto(existencia.getCodigoProducto());
            resultado[i] = crearVista(producto, existencia);
        }
        return resultado;
    }

    /** Devuelve copias de las existencias ordenadas por código. */
    public synchronized Existencia[] exportarExistencias() {
        Object[] valores = existencias.valoresEnOrden();
        Existencia[] resultado = new Existencia[valores.length];
        for (int i = 0; i < valores.length; i++) {
            resultado[i] = ((Existencia) valores[i]).copia();
        }
        return resultado;
    }

    /** Reemplaza el estado de existencias; luego pueden cargarse sus reservas asociadas. */
    public synchronized void cargarExistencias(Existencia[] nuevasExistencias) {
        if (nuevasExistencias == null) {
            throw new IllegalArgumentException("El arreglo de existencias no puede ser null");
        }
        ArbolAVL<String, Existencia> cargadas = new ArbolAVL<>();
        for (Existencia existencia : nuevasExistencias) {
            if (existencia == null) {
                throw new IllegalArgumentException("El arreglo contiene una existencia null");
            }
            String codigo = normalizarCodigo(existencia.getCodigoProducto());
            if (catalogo.obtenerProducto(codigo) == null) {
                throw new IllegalArgumentException("El producto " + codigo + " no existe en el catálogo");
            }
            if (!cargadas.insertar(codigo, existencia.copia())) {
                throw new IllegalArgumentException("El código " + codigo + " está repetido");
            }
        }
        existencias = cargadas;
        reservas = new ArbolAVL<>();
    }

    /** Devuelve las reservas ordenadas por la clave pedido|producto. */
    public synchronized ReservaPersistida[] exportarReservas() {
        Object[] claves = reservas.clavesEnOrden();
        ReservaPersistida[] resultado = new ReservaPersistida[claves.length];
        for (int i = 0; i < claves.length; i++) {
            String clave = (String) claves[i];
            int separador = clave.indexOf('|');
            resultado[i] = new ReservaPersistida(clave.substring(0, separador),
                    clave.substring(separador + 1), reservas.buscar(clave));
        }
        return resultado;
    }

    /** Carga reservas y verifica que sus cantidades coincidan con las existencias reservadas. */
    public synchronized void cargarReservas(ReservaPersistida[] nuevasReservas) {
        if (nuevasReservas == null) {
            throw new IllegalArgumentException("El arreglo de reservas no puede ser null");
        }
        ArbolAVL<String, Integer> cargadas = new ArbolAVL<>();
        for (ReservaPersistida reserva : nuevasReservas) {
            if (reserva == null || textoInvalido(reserva.getIdPedido())
                    || codigoInvalido(reserva.getCodigoProducto()) || reserva.getCantidad() <= 0) {
                throw new IllegalArgumentException("El arreglo contiene una reserva inválida");
            }
            String codigo = normalizarCodigo(reserva.getCodigoProducto());
            String pedido = reserva.getIdPedido().trim();
            if (!existencias.contiene(codigo)) {
                throw new IllegalArgumentException("No existe la existencia local del producto " + codigo);
            }
            String clave = crearClaveReserva(pedido, codigo);
            if (!cargadas.insertar(clave, reserva.getCantidad())) {
                throw new IllegalArgumentException("La reserva de " + codigo + " para " + pedido
                        + " está duplicada");
            }
        }

        validarTotalesReservados(cargadas);
        reservas = cargadas;
    }

    /** Devuelve las cantidades del producto localmente reservado para un pedido. */
    public synchronized int getCantidadReservas() {
        return reservas.tamano();
    }

    private void validarTotalesReservados(ArbolAVL<String, Integer> reservasCargadas) {
        Object[] codigos = existencias.clavesEnOrden();
        Object[] clavesReservas = reservasCargadas.clavesEnOrden();
        for (Object objetoCodigo : codigos) {
            String codigo = (String) objetoCodigo;
            long total = 0;
            for (Object objetoClave : clavesReservas) {
                String clave = (String) objetoClave;
                if (clave.endsWith("|" + codigo)) {
                    total += reservasCargadas.buscar(clave);
                }
            }
            if (total != existencias.buscar(codigo).getCantidadReservada()) {
                throw new IllegalArgumentException("Las reservas de " + codigo
                        + " no coinciden con la cantidad reservada de su existencia");
            }
        }
    }

    private ExistenciaInventario crearVista(Producto producto, Existencia existencia) {
        if (producto == null) {
            throw new IllegalStateException("Falta el producto " + existencia.getCodigoProducto()
                    + " en el catálogo global");
        }
        return new ExistenciaInventario(producto.getCodigo(), producto.getNombre(), producto.getPrecio(),
                existencia.getCantidadDisponible(), existencia.getCantidadReservada());
    }

    private int obtenerDisponibleInterno(String codigoProducto) {
        if (codigoInvalido(codigoProducto)) {
            return -1;
        }
        Existencia existencia = existencias.buscar(normalizarCodigo(codigoProducto));
        return existencia == null ? -1 : existencia.getCantidadDisponible();
    }

    private void validarCodigoYcantidad(String codigoProducto, int cantidad) {
        if (codigoInvalido(codigoProducto)) {
            throw new IllegalArgumentException("El código del producto no puede ser null ni vacío");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
    }

    private boolean codigoInvalido(String codigo) {
        return codigo == null || codigo.trim().isEmpty() || codigo.indexOf('|') >= 0;
    }

    private boolean textoInvalido(String texto) {
        return texto == null || texto.trim().isEmpty() || texto.indexOf('|') >= 0;
    }

    private String normalizarCodigo(String codigo) {
        String recortado = codigo.trim();
        StringBuilder resultado = new StringBuilder(recortado.length());
        for (int i = 0; i < recortado.length(); i++) {
            resultado.append(Character.toUpperCase(recortado.charAt(i)));
        }
        return resultado.toString();
    }

    private String crearClaveReserva(String pedido, String codigo) {
        if (pedido.indexOf('|') >= 0 || codigo.indexOf('|') >= 0) {
            throw new IllegalArgumentException("El pedido y el código no pueden contener '|'");
        }
        return pedido + "|" + codigo;
    }
}
