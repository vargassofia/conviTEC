package cr.ac.tec.convitec.acceso;

/**
 * Una cuenta tal como se guarda en datos/COD/cuentas.json (Gson).
 *
 * No tiene la contraseña: solo la sal y el resultado de PBKDF2 (ver Contrasenas).
 * Las fechas van como texto ISO ("2026-11-28T10:15:00") para que el archivo se pueda leer.
 */
final class RegistroCuenta {

    String usuario;
    String nombre;
    String sal;
    String hash;
    int iteraciones;
    boolean activa;
    String creadaEn;
    String creadaPor;
    String ultimoIngreso;

    RegistroCuenta() {
        // Para Gson
    }
}
