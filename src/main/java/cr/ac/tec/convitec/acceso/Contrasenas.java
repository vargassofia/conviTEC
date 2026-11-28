package cr.ac.tec.convitec.acceso;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado de contraseñas con PBKDF2-HMAC-SHA256 (viene en el JDK, sin librerías nuevas).
 *
 * Nunca se guarda la contraseña: se guarda una "sal" aleatoria y el resultado de aplicar PBKDF2
 * muchas veces (ITERACIONES). Para revisar una contraseña se repite el cálculo y se compara.
 * Así, aunque alguien copie el archivo de cuentas, no puede leer las contraseñas.
 */
public final class Contrasenas {

    /** Repeticiones de PBKDF2: hacen lento (y caro) probar contraseñas a la fuerza. */
    public static final int ITERACIONES = 120_000;

    static final int BYTES_SAL = 16;
    static final int BITS_HASH = 256;

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";

    private Contrasenas() {
        // Clase de utilidad: no se instancia
    }

    /** Sal aleatoria nueva, en Base64. */
    static String nuevaSal(SecureRandom azar) {
        byte[] sal = new byte[BYTES_SAL];
        azar.nextBytes(sal);
        return Base64.getEncoder().encodeToString(sal);
    }

    /** PBKDF2 de la contraseña con esa sal, en Base64. */
    static String cifrar(String clave, String salBase64, int iteraciones) {
        char[] caracteres = clave.toCharArray();
        PBEKeySpec especificacion = new PBEKeySpec(caracteres, Base64.getDecoder().decode(salBase64),
                iteraciones, BITS_HASH);
        try {
            byte[] hash = SecretKeyFactory.getInstance(ALGORITMO).generateSecret(especificacion).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Este Java no tiene " + ALGORITMO + ".", e);
        } finally {
            especificacion.clearPassword();
            for (int i = 0; i < caracteres.length; i++) {
                caracteres[i] = '\0';
            }
        }
    }

    /** true si la contraseña coincide. La comparación tarda lo mismo acierte o no. */
    static boolean coincide(String clave, String salBase64, String hashBase64, int iteraciones) {
        byte[] calculado = Base64.getDecoder().decode(cifrar(clave, salBase64, iteraciones));
        byte[] guardado = Base64.getDecoder().decode(hashBase64);
        return MessageDigest.isEqual(calculado, guardado);
    }
}
