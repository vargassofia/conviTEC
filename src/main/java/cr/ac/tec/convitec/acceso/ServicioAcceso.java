package cr.ac.tec.convitec.acceso;

import cr.ac.tec.convitec.estructuras.ArbolAVL;
import cr.ac.tec.convitec.persistencia.AlmacenArchivos;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;

/**
 * Cuentas y sesiones de UNA sede (cada sede tiene las suyas, en datos/COD/cuentas.json).
 *
 * Reglas (acordadas con el equipo):
 * - No hay roles: toda persona con cuenta puede usar todas las pantallas de SU sede.
 * - Desde la web solo se crean cuentas para la sede en la que se está trabajando,
 *   así que nadie puede darse acceso a otra sede.
 * - Crear, activar/desactivar o cambiar contraseñas pide la contraseña de quien lo hace.
 * - Nadie puede desactivar su propia cuenta (así la sede nunca queda sin cuentas activas).
 * - La sesión se cierra tras {@link #MINUTOS_INACTIVIDAD} minutos sin uso, y a las 12 horas en cualquier caso.
 * - Tras 5 contraseñas incorrectas seguidas para un usuario hay que esperar (30 s, y el doble cada vez, hasta 15 min).
 *
 * Código de acceso (de un solo uso, vence en 60 minutos): sirve para crear la primera cuenta, o para
 * recuperar el acceso si nadie recuerda su contraseña. Solo lo ve quien está frente a la computadora de la
 * sede: el programa lo muestra al arrancar si no hay cuentas, o con el comando de consola "codigo-acceso".
 *
 * Solo usa el árbol AVL del proyecto (sin colecciones de Java). Todos los métodos son synchronized porque
 * lo usan el hilo del servidor web y el de la consola.
 */
public class ServicioAcceso {

    public static final int MINUTOS_INACTIVIDAD = 30;
    static final Duration INACTIVIDAD = Duration.ofMinutes(MINUTOS_INACTIVIDAD);
    static final Duration DURACION_MAXIMA = Duration.ofHours(12);
    static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(60);

    static final int FALLOS_SIN_ESPERA = 5;
    static final Duration ESPERA_INICIAL = Duration.ofSeconds(30);
    static final Duration ESPERA_MAXIMA = Duration.ofMinutes(15);
    static final int FALLOS_CODIGO = 5;

    static final int MAXIMO_SESIONES = 200;
    static final int MAXIMO_INTENTOS_REGISTRADOS = 500;

    static final int LARGO_MINIMO_CLAVE = 8;
    static final int LARGO_MAXIMO_CLAVE = 128;

    static final String ARCHIVO = "cuentas.json";

    private final String codigoSede;
    private final AlmacenArchivos almacen;
    private final Clock reloj;
    private final int iteraciones;
    private final SecureRandom azar = new SecureRandom();

    private final ArbolAVL<String, RegistroCuenta> cuentas = new ArbolAVL<>();
    /** Clave: SHA-256 del token (así ni en memoria se buscan sesiones por el token tal cual). */
    private final ArbolAVL<String, Sesion> sesiones = new ArbolAVL<>();
    /** Contraseñas incorrectas seguidas, por usuario (exista o no). */
    private final ArbolAVL<String, Intentos> intentos = new ArbolAVL<>();

    /** Sal y hash de relleno: se calculan aunque el usuario no exista, para tardar lo mismo. */
    private final String salRelleno;
    private final String hashRelleno;

    private String codigoAcceso;
    private Instant venceCodigo;
    private int fallosCodigo;

    /**
     * @param codigoSede   LIB, ALA, SJO o LIM (solo para los mensajes)
     * @param carpetaDatos carpeta de la sede (config datos.carpeta)
     */
    public ServicioAcceso(String codigoSede, Path carpetaDatos) {
        this(codigoSede, carpetaDatos, Clock.systemDefaultZone(), Contrasenas.ITERACIONES);
    }

    /** Con otro reloj y menos iteraciones de PBKDF2 (las pruebas usan pocas para ir rápido). */
    public ServicioAcceso(String codigoSede, Path carpetaDatos, Clock reloj, int iteraciones) {
        if (codigoSede == null || carpetaDatos == null || reloj == null || iteraciones < 1) {
            throw new IllegalArgumentException("El servicio de acceso necesita la sede, su carpeta, un reloj"
                    + " y las iteraciones de cifrado.");
        }
        this.codigoSede = codigoSede;
        this.almacen = new AlmacenArchivos(carpetaDatos);
        this.reloj = reloj;
        this.iteraciones = iteraciones;
        this.salRelleno = Contrasenas.nuevaSal(azar);
        this.hashRelleno = Contrasenas.cifrar("relleno", salRelleno, iteraciones);
        cargar();
    }

    // ------------------------------------------------------------------
    // Estado general
    // ------------------------------------------------------------------

    /** true si la sede no tiene ninguna cuenta activa: hay que crear la primera con el código de acceso. */
    public synchronized boolean necesitaInstalacion() {
        Object[] todas = cuentas.valoresEnOrden();
        for (int i = 0; i < todas.length; i++) {
            if (((RegistroCuenta) todas[i]).activa) {
                return false;
            }
        }
        return true;
    }

    /**
     * Genera un código de acceso nuevo (el anterior deja de servir). Vence en 60 minutos.
     *
     * @return el código con el formato "1234-5678", para mostrarlo en la consola de la sede
     */
    public synchronized String generarCodigoAcceso() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append((char) ('0' + azar.nextInt(10)));
        }
        codigoAcceso = sb.toString();
        venceCodigo = ahora().plus(VIGENCIA_CODIGO);
        fallosCodigo = 0;
        return codigoAcceso;
    }

    // ------------------------------------------------------------------
    // Entrar y salir
    // ------------------------------------------------------------------

    /**
     * Revisa usuario y contraseña y abre una sesión.
     *
     * @return el token de la sesión (va en la cookie del navegador)
     * @throws AccesoException CREDENCIALES si no coinciden o la cuenta está desactivada; BLOQUEADO si hay que esperar.
     */
    public synchronized String entrar(String usuario, String clave) {
        String clavePedida = clave == null ? "" : clave;
        String nombreUsuario = normalizarUsuario(usuario);
        exigirSinBloqueo(nombreUsuario);
        RegistroCuenta cuenta = nombreUsuario.isEmpty() ? null : cuentas.buscar(nombreUsuario);
        boolean correcta;
        if (cuenta == null) {
            // Se hace el mismo cálculo aunque el usuario no exista: así no se nota por el tiempo de respuesta
            Contrasenas.coincide(clavePedida, salRelleno, hashRelleno, iteraciones);
            correcta = false;
        } else {
            correcta = Contrasenas.coincide(clavePedida, cuenta.sal, cuenta.hash, cuenta.iteraciones);
        }
        if (!correcta) {
            registrarFallo(nombreUsuario);
            throw new AccesoException(AccesoException.Motivo.CREDENCIALES, "El usuario o la contraseña no son"
                    + " correctos. Revise que estén bien escritos (las mayúsculas cuentan en la contraseña).");
        }
        intentos.eliminar(nombreUsuario);
        if (!cuenta.activa) {
            throw new AccesoException(AccesoException.Motivo.CREDENCIALES, "Esta cuenta está desactivada."
                    + " Pídale a otra persona de la sede que la active en la sección Cuentas.");
        }
        RegistroCuenta copia = copiar(cuenta);
        copia.ultimoIngreso = ahoraTexto();
        try {
            reemplazar(cuenta, copia);
        } catch (RuntimeException e) {
            // No poder anotar la fecha de ingreso no debe impedir entrar
            System.err.println("[ConviTEC acceso] No se pudo guardar el último ingreso: " + e.getMessage());
        }
        return abrirSesion(cuenta.usuario, cuenta.nombre);
    }

    /**
     * Con el código de acceso: crea una cuenta nueva o, si el usuario ya existe, le pone esta contraseña
     * y la activa (para recuperar el acceso). Después abre la sesión.
     *
     * @return el token de la sesión
     * @throws AccesoException CODIGO si el código no sirve.
     * @throws IllegalArgumentException si los datos no son válidos.
     */
    public synchronized String usarCodigoAcceso(String codigo, String nombre, String usuario, String clave) {
        exigirCodigo(codigo);
        String nombreUsuario = validarUsuario(usuario);
        validarClave(clave, nombreUsuario);
        RegistroCuenta existente = cuentas.buscar(nombreUsuario);
        RegistroCuenta nueva;
        if (existente == null) {
            nueva = nuevaCuenta(nombreUsuario, validarNombre(nombre), clave, "Código de acceso");
        } else {
            nueva = copiar(existente);
            if (nombre != null && !nombre.isBlank()) {
                nueva.nombre = validarNombre(nombre);
            }
            ponerClave(nueva, clave);
            nueva.activa = true;
        }
        nueva.ultimoIngreso = ahoraTexto();
        reemplazar(existente, nueva);
        codigoAcceso = null; // de un solo uso
        venceCodigo = null;
        cerrarSesionesDe(nombreUsuario, null);
        intentos.eliminar(nombreUsuario);
        return abrirSesion(nueva.usuario, nueva.nombre);
    }

    /**
     * La sesión de ese token, o null si no existe, venció o la cuenta ya no está activa.
     *
     * @param renovar true si la petición la hizo la persona (cuenta como actividad); false para las
     *                actualizaciones automáticas de la página, que no deben mantener viva la sesión.
     */
    public synchronized Sesion validar(String token, boolean renovar) {
        if (token == null || token.isEmpty() || token.length() > 100) {
            return null;
        }
        String clave = huella(token);
        Sesion sesion = sesiones.buscar(clave);
        if (sesion == null) {
            return null;
        }
        Instant ahora = ahora();
        RegistroCuenta cuenta = cuentas.buscar(sesion.getUsuario());
        if (vencida(sesion, ahora) || cuenta == null || !cuenta.activa) {
            sesiones.eliminar(clave);
            return null;
        }
        if (renovar) {
            sesion.renovar(ahora);
        }
        return sesion;
    }

    /** Cierra la sesión de ese token (si existe). */
    public synchronized void salir(String token) {
        if (token != null && !token.isEmpty() && token.length() <= 100) {
            sesiones.eliminar(huella(token));
        }
    }

    // ------------------------------------------------------------------
    // Administración de cuentas (desde la sección Cuentas)
    // ------------------------------------------------------------------

    /** Las cuentas de la sede, ordenadas por usuario (orden del árbol AVL). */
    public synchronized Cuenta[] cuentas() {
        Object[] todas = cuentas.valoresEnOrden();
        Cuenta[] resultado = new Cuenta[todas.length];
        for (int i = 0; i < todas.length; i++) {
            RegistroCuenta r = (RegistroCuenta) todas[i];
            resultado[i] = new Cuenta(r.usuario, r.nombre, r.activa, r.creadaEn, r.creadaPor, r.ultimoIngreso);
        }
        return resultado;
    }

    /**
     * Crea una cuenta nueva para esta sede.
     *
     * @param quien              la sesión de la persona que la crea
     * @param claveConfirmacion  la contraseña de esa persona
     */
    public synchronized void crearCuenta(Sesion quien, String claveConfirmacion, String nombre, String usuario,
                                         String clave) {
        RegistroCuenta autor = exigirConfirmacion(quien, claveConfirmacion);
        String nombreUsuario = validarUsuario(usuario);
        String nombreValido = validarNombre(nombre);
        validarClave(clave, nombreUsuario);
        if (cuentas.contiene(nombreUsuario)) {
            throw new IllegalArgumentException("Ya existe una cuenta con el usuario \"" + nombreUsuario
                    + "\". Elija otro.");
        }
        reemplazar(null, nuevaCuenta(nombreUsuario, nombreValido, clave, autor.nombre));
    }

    /** Activa o desactiva una cuenta. Al desactivarla se cierran sus sesiones abiertas. */
    public synchronized void cambiarEstado(Sesion quien, String claveConfirmacion, String usuario, boolean activa) {
        exigirConfirmacion(quien, claveConfirmacion);
        RegistroCuenta cuenta = exigirCuenta(usuario);
        if (!activa && cuenta.usuario.equals(quien.getUsuario())) {
            throw new IllegalArgumentException("No puede desactivar su propia cuenta.");
        }
        if (cuenta.activa == activa) {
            return;
        }
        RegistroCuenta copia = copiar(cuenta);
        copia.activa = activa;
        reemplazar(cuenta, copia);
        if (!activa) {
            cerrarSesionesDe(cuenta.usuario, null);
        }
    }

    /**
     * Cambia la contraseña de una cuenta (la propia o la de otra persona que la olvidó).
     * Se cierran las otras sesiones de esa cuenta; si es la propia, la sesión actual sigue abierta.
     */
    public synchronized void cambiarClave(Sesion quien, String claveConfirmacion, String usuario, String claveNueva) {
        exigirConfirmacion(quien, claveConfirmacion);
        RegistroCuenta cuenta = exigirCuenta(usuario);
        validarClave(claveNueva, cuenta.usuario);
        RegistroCuenta copia = copiar(cuenta);
        ponerClave(copia, claveNueva);
        reemplazar(cuenta, copia);
        cerrarSesionesDe(cuenta.usuario, cuenta.usuario.equals(quien.getUsuario()) ? quien : null);
    }

    // ------------------------------------------------------------------
    // Validaciones
    // ------------------------------------------------------------------

    /** "  Ana.Mora " -> "ana.mora". */
    static String normalizarUsuario(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
    }

    static String validarUsuario(String usuario) {
        String u = normalizarUsuario(usuario);
        if (u.length() < 3 || u.length() > 30) {
            throw new IllegalArgumentException("El usuario debe tener entre 3 y 30 caracteres.");
        }
        for (int i = 0; i < u.length(); i++) {
            char c = u.charAt(i);
            boolean valido = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_';
            if (!valido) {
                throw new IllegalArgumentException("El usuario solo puede tener letras sin tilde, números, punto,"
                        + " guion y guion bajo (por ejemplo: ana.mora).");
            }
        }
        return u;
    }

    static String validarNombre(String nombre) {
        String n = nombre == null ? "" : nombre.trim();
        if (n.length() < 2 || n.length() > 60) {
            throw new IllegalArgumentException("Escriba el nombre de la persona (entre 2 y 60 caracteres).");
        }
        for (int i = 0; i < n.length(); i++) {
            if (Character.isISOControl(n.charAt(i))) {
                throw new IllegalArgumentException("El nombre tiene caracteres no permitidos.");
            }
        }
        return n;
    }

    static void validarClave(String clave, String usuario) {
        if (clave == null || clave.length() < LARGO_MINIMO_CLAVE) {
            throw new IllegalArgumentException("La contraseña debe tener al menos " + LARGO_MINIMO_CLAVE
                    + " caracteres.");
        }
        if (clave.length() > LARGO_MAXIMO_CLAVE) {
            throw new IllegalArgumentException("La contraseña puede tener como máximo " + LARGO_MAXIMO_CLAVE
                    + " caracteres.");
        }
        if (clave.isBlank()) {
            throw new IllegalArgumentException("La contraseña no puede ser solo espacios.");
        }
        if (normalizarUsuario(clave).equals(usuario)) {
            throw new IllegalArgumentException("La contraseña no puede ser igual al usuario.");
        }
    }

    // ------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------

    private RegistroCuenta exigirConfirmacion(Sesion quien, String claveConfirmacion) {
        if (quien == null) {
            throw new AccesoException(AccesoException.Motivo.SIN_SESION, "Inicie sesión para continuar.");
        }
        RegistroCuenta autor = cuentas.buscar(quien.getUsuario());
        if (autor == null || !autor.activa) {
            throw new AccesoException(AccesoException.Motivo.SIN_SESION, "Su cuenta ya no está activa.");
        }
        exigirSinBloqueo(autor.usuario);
        if (!Contrasenas.coincide(claveConfirmacion == null ? "" : claveConfirmacion, autor.sal, autor.hash,
                autor.iteraciones)) {
            registrarFallo(autor.usuario);
            throw new AccesoException(AccesoException.Motivo.CONFIRMACION,
                    "Su contraseña no es correcta. Escriba la contraseña con la que entró.");
        }
        intentos.eliminar(autor.usuario);
        return autor;
    }

    private RegistroCuenta exigirCuenta(String usuario) {
        String u = normalizarUsuario(usuario);
        RegistroCuenta cuenta = u.isEmpty() ? null : cuentas.buscar(u);
        if (cuenta == null) {
            throw new IllegalArgumentException("No existe la cuenta \"" + usuario + "\".");
        }
        return cuenta;
    }

    private void exigirCodigo(String codigo) {
        Instant ahora = ahora();
        if (codigoAcceso == null || venceCodigo == null || ahora.isAfter(venceCodigo)) {
            codigoAcceso = null;
            throw new AccesoException(AccesoException.Motivo.CODIGO, "El código de acceso no es válido o ya venció."
                    + " Para obtener uno nuevo, escriba codigo-acceso en la ventana del programa de la sede "
                    + codigoSede + ".");
        }
        String escrito = soloDigitos(codigo);
        boolean correcto = MessageDigest.isEqual(escrito.getBytes(StandardCharsets.UTF_8),
                soloDigitos(codigoAcceso).getBytes(StandardCharsets.UTF_8));
        if (!correcto) {
            fallosCodigo++;
            if (fallosCodigo >= FALLOS_CODIGO) {
                codigoAcceso = null;
                throw new AccesoException(AccesoException.Motivo.CODIGO, "El código se anuló por seguridad"
                        + " después de varios intentos. Para obtener uno nuevo, escriba codigo-acceso en la ventana"
                        + " del programa de la sede " + codigoSede + ".");
            }
            throw new AccesoException(AccesoException.Motivo.CODIGO, "El código de acceso no es correcto."
                    + " Revíselo en la ventana del programa de la sede.");
        }
    }

    private static String soloDigitos(String texto) {
        StringBuilder sb = new StringBuilder();
        if (texto != null) {
            for (int i = 0; i < texto.length() && sb.length() < 20; i++) {
                char c = texto.charAt(i);
                if (c >= '0' && c <= '9') {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private void exigirSinBloqueo(String usuario) {
        Intentos registro = usuario.isEmpty() ? null : intentos.buscar(usuario);
        if (registro == null || registro.bloqueadoHasta == null) {
            return;
        }
        long milisegundos = Duration.between(ahora(), registro.bloqueadoHasta).toMillis();
        if (milisegundos > 0) {
            long segundos = (milisegundos + 999) / 1000; // redondeado hacia arriba
            String espera = segundos < 60 ? segundos + " segundos"
                    : ((segundos + 59) / 60) + ((segundos + 59) / 60 == 1 ? " minuto" : " minutos");
            throw new AccesoException(AccesoException.Motivo.BLOQUEADO, "Hubo demasiados intentos con una"
                    + " contraseña incorrecta. Por seguridad, espere " + espera + " e intente de nuevo.", segundos);
        }
    }

    private void registrarFallo(String usuario) {
        if (usuario.isEmpty()) {
            return;
        }
        Intentos registro = intentos.buscar(usuario);
        if (registro == null) {
            if (intentos.tamano() >= MAXIMO_INTENTOS_REGISTRADOS) {
                intentos.vaciar(); // para que nadie llene la memoria probando usuarios inventados
            }
            registro = new Intentos();
            intentos.insertar(usuario, registro);
        }
        registro.fallos++;
        if (registro.fallos >= FALLOS_SIN_ESPERA) {
            Duration espera = ESPERA_INICIAL;
            for (int i = FALLOS_SIN_ESPERA; i < registro.fallos && espera.compareTo(ESPERA_MAXIMA) < 0; i++) {
                espera = espera.multipliedBy(2);
            }
            if (espera.compareTo(ESPERA_MAXIMA) > 0) {
                espera = ESPERA_MAXIMA;
            }
            registro.bloqueadoHasta = ahora().plus(espera);
        }
    }

    private String abrirSesion(String usuario, String nombre) {
        limpiarSesiones();
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sesiones.insertar(huella(token), new Sesion(usuario, nombre, ahora()));
        return token;
    }

    /** Quita las sesiones vencidas; si aún hay demasiadas, cierra la que lleva más tiempo sin usarse. */
    private void limpiarSesiones() {
        Instant ahora = ahora();
        Object[] claves = sesiones.clavesEnOrden();
        for (int i = 0; i < claves.length; i++) {
            Sesion s = sesiones.buscar((String) claves[i]);
            if (s != null && vencida(s, ahora)) {
                sesiones.eliminar((String) claves[i]);
            }
        }
        if (sesiones.tamano() >= MAXIMO_SESIONES) {
            Object[] restantes = sesiones.clavesEnOrden();
            String masVieja = null;
            Instant uso = null;
            for (int i = 0; i < restantes.length; i++) {
                Sesion s = sesiones.buscar((String) restantes[i]);
                if (uso == null || s.getUltimoUso().isBefore(uso)) {
                    uso = s.getUltimoUso();
                    masVieja = (String) restantes[i];
                }
            }
            sesiones.eliminar(masVieja);
        }
    }

    /** Cierra todas las sesiones de un usuario menos `excepto` (puede ser null). */
    private void cerrarSesionesDe(String usuario, Sesion excepto) {
        Object[] claves = sesiones.clavesEnOrden();
        for (int i = 0; i < claves.length; i++) {
            Sesion s = sesiones.buscar((String) claves[i]);
            if (s != null && s != excepto && s.getUsuario().equals(usuario)) {
                sesiones.eliminar((String) claves[i]);
            }
        }
    }

    private static boolean vencida(Sesion sesion, Instant ahora) {
        return !ahora.isBefore(sesion.getUltimoUso().plus(INACTIVIDAD))
                || !ahora.isBefore(sesion.getInicio().plus(DURACION_MAXIMA));
    }

    private RegistroCuenta nuevaCuenta(String usuario, String nombre, String clave, String creadaPor) {
        RegistroCuenta r = new RegistroCuenta();
        r.usuario = usuario;
        r.nombre = nombre;
        r.activa = true;
        r.creadaEn = ahoraTexto();
        r.creadaPor = creadaPor;
        ponerClave(r, clave);
        return r;
    }

    private void ponerClave(RegistroCuenta r, String clave) {
        r.sal = Contrasenas.nuevaSal(azar);
        r.iteraciones = iteraciones;
        r.hash = Contrasenas.cifrar(clave, r.sal, iteraciones);
    }

    private static RegistroCuenta copiar(RegistroCuenta o) {
        RegistroCuenta c = new RegistroCuenta();
        c.usuario = o.usuario;
        c.nombre = o.nombre;
        c.sal = o.sal;
        c.hash = o.hash;
        c.iteraciones = o.iteraciones;
        c.activa = o.activa;
        c.creadaEn = o.creadaEn;
        c.creadaPor = o.creadaPor;
        c.ultimoIngreso = o.ultimoIngreso;
        return c;
    }

    /**
     * Pone `nueva` en lugar de `anterior` (null = cuenta nueva) y guarda el archivo.
     * Si no se puede guardar, deja todo como estaba y lanza el error.
     */
    private void reemplazar(RegistroCuenta anterior, RegistroCuenta nueva) {
        if (anterior == null) {
            cuentas.insertar(nueva.usuario, nueva);
        } else {
            cuentas.actualizar(nueva.usuario, nueva);
        }
        try {
            guardar();
        } catch (RuntimeException e) {
            if (anterior == null) {
                cuentas.eliminar(nueva.usuario);
            } else {
                cuentas.actualizar(anterior.usuario, anterior);
            }
            throw e;
        }
    }

    private void guardar() {
        Object[] todas = cuentas.valoresEnOrden();
        RegistroCuenta[] arreglo = new RegistroCuenta[todas.length];
        for (int i = 0; i < todas.length; i++) {
            arreglo[i] = (RegistroCuenta) todas[i];
        }
        almacen.guardarArreglo(ARCHIVO, arreglo);
    }

    private void cargar() {
        RegistroCuenta[] guardadas = almacen.cargarArreglo(ARCHIVO, RegistroCuenta[].class);
        for (int i = 0; i < guardadas.length; i++) {
            RegistroCuenta r = guardadas[i];
            if (r == null || r.usuario == null || r.sal == null || r.hash == null || r.iteraciones < 1) {
                System.err.println("[ConviTEC acceso] Se ignoró una cuenta incompleta en " + ARCHIVO + ".");
                continue;
            }
            r.usuario = normalizarUsuario(r.usuario);
            if (!cuentas.insertar(r.usuario, r)) {
                System.err.println("[ConviTEC acceso] Se ignoró la cuenta repetida \"" + r.usuario + "\".");
            }
        }
    }

    private static String huella(String token) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (int i = 0; i < h.length; i++) {
                sb.append(Character.forDigit((h[i] >> 4) & 0xF, 16)).append(Character.forDigit(h[i] & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Este Java no tiene SHA-256.", e);
        }
    }

    private Instant ahora() {
        return reloj.instant();
    }

    private String ahoraTexto() {
        return LocalDateTime.now(reloj).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    /** Contraseñas incorrectas seguidas de un usuario. */
    private static final class Intentos {
        int fallos;
        Instant bloqueadoHasta;
    }

    /** Vista de solo lectura de una cuenta (sin sal ni hash), para la sección Cuentas. */
    public static final class Cuenta {
        public final String usuario;
        public final String nombre;
        public final boolean activa;
        public final String creadaEn;
        public final String creadaPor;
        public final String ultimoIngreso;

        Cuenta(String usuario, String nombre, boolean activa, String creadaEn, String creadaPor,
               String ultimoIngreso) {
            this.usuario = usuario;
            this.nombre = nombre;
            this.activa = activa;
            this.creadaEn = creadaEn;
            this.creadaPor = creadaPor;
            this.ultimoIngreso = ultimoIngreso;
        }
    }
}
