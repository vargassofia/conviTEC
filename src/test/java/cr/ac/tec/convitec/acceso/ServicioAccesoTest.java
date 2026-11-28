package cr.ac.tec.convitec.acceso;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicioAccesoTest {

    private static final String CLAVE_ANA = "clave-de-ana";
    private static final String CLAVE_LUIS = "clave-de-luis";

    @TempDir
    Path carpeta;

    private RelojMovible reloj;
    private ServicioAcceso acceso;

    @BeforeEach
    void preparar() {
        reloj = new RelojMovible(Instant.parse("2026-11-28T15:00:00Z"));
        acceso = nuevoServicio();
    }

    private ServicioAcceso nuevoServicio() {
        return new ServicioAcceso("SJO", carpeta, reloj, 1000);
    }

    /** Crea la primera cuenta (ana) con el código de acceso y devuelve su sesión. */
    private Sesion instalarAna() {
        String token = acceso.usarCodigoAcceso(acceso.generarCodigoAcceso(), "Ana Mora", "Ana", CLAVE_ANA);
        return acceso.validar(token, false);
    }

    private static AccesoException.Motivo motivo(Runnable accion) {
        return assertThrows(AccesoException.class, accion::run).getMotivo();
    }

    // ------------------------------------------------------------------

    @Test
    void primeraCuentaConCodigoYQuedaGuardada() throws Exception {
        assertTrue(acceso.necesitaInstalacion());
        Sesion ana = instalarAna();
        assertNotNull(ana);
        assertEquals("ana", ana.getUsuario()); // el usuario se guarda en minúsculas
        assertEquals("Ana Mora", ana.getNombre());
        assertFalse(acceso.necesitaInstalacion());

        // El archivo no tiene la contraseña
        String archivo = Files.readString(carpeta.resolve("cuentas.json"), StandardCharsets.UTF_8);
        assertFalse(archivo.contains(CLAVE_ANA));

        // Otra instancia (la sede reiniciada) lee la cuenta y deja entrar; las sesiones no sobreviven
        ServicioAcceso reiniciado = nuevoServicio();
        assertFalse(reiniciado.necesitaInstalacion());
        assertNotNull(reiniciado.validar(reiniciado.entrar("ANA ", CLAVE_ANA), false));
        assertEquals("Código de acceso", reiniciado.cuentas()[0].creadaPor);
    }

    @Test
    void elCodigoEsDeUnSoloUsoYVence() {
        assertEquals(AccesoException.Motivo.CODIGO, motivo(() ->
                acceso.usarCodigoAcceso("1234-5678", "Ana Mora", "ana", CLAVE_ANA))); // nunca se generó

        String codigo = acceso.generarCodigoAcceso();
        assertTrue(codigo.matches("\\d{4}-\\d{4}"));
        acceso.usarCodigoAcceso(codigo.replace("-", " "), "Ana Mora", "ana", CLAVE_ANA); // sin guion también sirve
        assertEquals(AccesoException.Motivo.CODIGO, motivo(() ->
                acceso.usarCodigoAcceso(codigo, "Luis Soto", "luis", CLAVE_LUIS)));

        String otro = acceso.generarCodigoAcceso();
        reloj.avanzar(Duration.ofMinutes(61));
        assertEquals(AccesoException.Motivo.CODIGO, motivo(() ->
                acceso.usarCodigoAcceso(otro, "Luis Soto", "luis", CLAVE_LUIS)));
    }

    @Test
    void elCodigoSeAnulaTrasVariosIntentosIncorrectos() {
        String codigo = acceso.generarCodigoAcceso();
        String incorrecto = codigo.equals("0000-0000") ? "1111-1111" : "0000-0000";
        for (int i = 0; i < ServicioAcceso.FALLOS_CODIGO; i++) {
            motivo(() -> acceso.usarCodigoAcceso(incorrecto, "Ana Mora", "ana", CLAVE_ANA));
        }
        AccesoException e = assertThrows(AccesoException.class,
                () -> acceso.usarCodigoAcceso(codigo, "Ana Mora", "ana", CLAVE_ANA));
        assertTrue(e.getMessage().contains("codigo-acceso"), e.getMessage());
        assertTrue(acceso.necesitaInstalacion());
    }

    @Test
    void datosInvalidosNoGastanElCodigo() {
        String codigo = acceso.generarCodigoAcceso();
        assertThrows(IllegalArgumentException.class, () -> acceso.usarCodigoAcceso(codigo, "Ana", "a", CLAVE_ANA));
        assertThrows(IllegalArgumentException.class, () -> acceso.usarCodigoAcceso(codigo, "Ana", "ana mora", CLAVE_ANA));
        assertThrows(IllegalArgumentException.class, () -> acceso.usarCodigoAcceso(codigo, "Ana", "ana", "corta"));
        assertThrows(IllegalArgumentException.class, () -> acceso.usarCodigoAcceso(codigo, "Ana", "anamora1", "ANAMORA1"));
        assertThrows(IllegalArgumentException.class, () -> acceso.usarCodigoAcceso(codigo, " ", "ana", CLAVE_ANA));
        assertNotNull(acceso.usarCodigoAcceso(codigo, "Ana Mora", "ana", CLAVE_ANA));
    }

    @Test
    void contrasenaIncorrectaYEsperaTrasVariosIntentos() {
        instalarAna();
        assertEquals(AccesoException.Motivo.CREDENCIALES, motivo(() -> acceso.entrar("ana", "otra-clave")));
        assertEquals(AccesoException.Motivo.CREDENCIALES, motivo(() -> acceso.entrar("nadie", CLAVE_ANA)));
        assertEquals(AccesoException.Motivo.CREDENCIALES, motivo(() -> acceso.entrar(null, null)));

        for (int i = 1; i < ServicioAcceso.FALLOS_SIN_ESPERA; i++) {
            motivo(() -> acceso.entrar("ana", "otra-clave"));
        }
        // Ya van 5 fallos: aunque ahora la contraseña sea correcta, hay que esperar
        AccesoException bloqueo = assertThrows(AccesoException.class, () -> acceso.entrar("ana", CLAVE_ANA));
        assertEquals(AccesoException.Motivo.BLOQUEADO, bloqueo.getMotivo());
        assertEquals(30, bloqueo.getSegundosEspera());

        reloj.avanzar(Duration.ofSeconds(31));
        assertNotNull(acceso.entrar("ana", CLAVE_ANA));
        // El éxito borra los fallos: un error nuevo no bloquea
        assertEquals(AccesoException.Motivo.CREDENCIALES, motivo(() -> acceso.entrar("ana", "otra-clave")));
    }

    @Test
    void laEsperaCreceHastaQuinceMinutos() {
        instalarAna();
        for (int i = 0; i < ServicioAcceso.FALLOS_SIN_ESPERA; i++) {
            motivo(() -> acceso.entrar("ana", "otra-clave"));
        }
        long anterior = 0;
        for (int i = 0; i < 8; i++) {
            reloj.avanzar(Duration.ofMinutes(16));
            motivo(() -> acceso.entrar("ana", "otra-clave"));
            long espera = assertThrows(AccesoException.class, () -> acceso.entrar("ana", CLAVE_ANA)).getSegundosEspera();
            assertTrue(espera >= anterior && espera <= 15 * 60, "espera " + espera);
            anterior = espera;
        }
        assertEquals(15 * 60, anterior);
    }

    @Test
    void laSesionSeCierraTrasTreintaMinutosSinUso() {
        String token = acceso.usarCodigoAcceso(acceso.generarCodigoAcceso(), "Ana Mora", "ana", CLAVE_ANA);

        reloj.avanzar(Duration.ofMinutes(20));
        assertNotNull(acceso.validar(token, true)); // la persona usó la página: cuenta desde aquí
        reloj.avanzar(Duration.ofMinutes(20));
        assertNotNull(acceso.validar(token, false)); // actualización automática: NO renueva
        reloj.avanzar(Duration.ofMinutes(10));
        assertNull(acceso.validar(token, true)); // 30 min desde el último uso real
        assertNull(acceso.validar(token, true));

        // Aunque se use todo el tiempo, a las 12 horas se cierra
        String otro = acceso.entrar("ana", CLAVE_ANA);
        for (int i = 0; i < 12 * 4; i++) {
            reloj.avanzar(Duration.ofMinutes(15));
            acceso.validar(otro, true);
        }
        assertNull(acceso.validar(otro, true));

        assertNull(acceso.validar(null, true));
        assertNull(acceso.validar("inventado", true));
    }

    @Test
    void salirCierraLaSesion() {
        String token = acceso.usarCodigoAcceso(acceso.generarCodigoAcceso(), "Ana Mora", "ana", CLAVE_ANA);
        acceso.salir(token);
        assertNull(acceso.validar(token, true));
        acceso.salir(null); // no falla
    }

    @Test
    void crearCuentaPideLaContrasenaDeQuienLaCrea() {
        Sesion ana = instalarAna();
        assertEquals(AccesoException.Motivo.CONFIRMACION, motivo(() ->
                acceso.crearCuenta(ana, "no-es-la-clave", "Luis Soto", "luis", CLAVE_LUIS)));
        assertEquals(1, acceso.cuentas().length);

        acceso.crearCuenta(ana, CLAVE_ANA, "Luis Soto", "luis", CLAVE_LUIS);
        assertThrows(IllegalArgumentException.class, () ->
                acceso.crearCuenta(ana, CLAVE_ANA, "Otro Luis", "LUIS", CLAVE_LUIS)); // repetido

        ServicioAcceso.Cuenta[] cuentas = acceso.cuentas();
        assertEquals(2, cuentas.length);
        assertEquals("ana", cuentas[0].usuario); // ordenadas por usuario
        assertEquals("luis", cuentas[1].usuario);
        assertEquals("Ana Mora", cuentas[1].creadaPor);
        assertNull(cuentas[1].ultimoIngreso);
        assertNotNull(acceso.entrar("luis", CLAVE_LUIS));
        assertNotNull(acceso.cuentas()[1].ultimoIngreso);
    }

    @Test
    void desactivarCierraSusSesionesYNoSePuedeDesactivarLaPropia() {
        Sesion ana = instalarAna();
        acceso.crearCuenta(ana, CLAVE_ANA, "Luis Soto", "luis", CLAVE_LUIS);
        String tokenLuis = acceso.entrar("luis", CLAVE_LUIS);

        assertThrows(IllegalArgumentException.class, () -> acceso.cambiarEstado(ana, CLAVE_ANA, "ana", false));
        assertThrows(IllegalArgumentException.class, () -> acceso.cambiarEstado(ana, CLAVE_ANA, "nadie", false));

        acceso.cambiarEstado(ana, CLAVE_ANA, "luis", false);
        assertNull(acceso.validar(tokenLuis, true));
        AccesoException e = assertThrows(AccesoException.class, () -> acceso.entrar("luis", CLAVE_LUIS));
        assertTrue(e.getMessage().contains("desactivada"), e.getMessage());

        acceso.cambiarEstado(ana, CLAVE_ANA, "luis", true);
        assertNotNull(acceso.entrar("luis", CLAVE_LUIS));
    }

    @Test
    void cambiarContrasena() {
        String tokenAna = acceso.usarCodigoAcceso(acceso.generarCodigoAcceso(), "Ana Mora", "ana", CLAVE_ANA);
        Sesion ana = acceso.validar(tokenAna, false);
        acceso.crearCuenta(ana, CLAVE_ANA, "Luis Soto", "luis", CLAVE_LUIS);
        String tokenLuis = acceso.entrar("luis", CLAVE_LUIS);
        String otraSesionAna = acceso.entrar("ana", CLAVE_ANA);

        // La de otra persona: se cierran sus sesiones
        acceso.cambiarClave(ana, CLAVE_ANA, "luis", "luis-nueva-1");
        assertNull(acceso.validar(tokenLuis, true));
        assertEquals(AccesoException.Motivo.CREDENCIALES, motivo(() -> acceso.entrar("luis", CLAVE_LUIS)));
        assertNotNull(acceso.entrar("luis", "luis-nueva-1"));

        // La propia: la sesión actual sigue, las otras se cierran
        acceso.cambiarClave(ana, CLAVE_ANA, "ana", "ana-nueva-1");
        assertNotNull(acceso.validar(tokenAna, false));
        assertNull(acceso.validar(otraSesionAna, false));
        assertEquals(AccesoException.Motivo.CONFIRMACION, motivo(() ->
                acceso.cambiarClave(ana, CLAVE_ANA, "luis", "otra-mas-1"))); // ya no es su contraseña
        acceso.cambiarClave(ana, "ana-nueva-1", "luis", "otra-mas-1");
    }

    @Test
    void elCodigoRecuperaUnaCuentaOlvidada() {
        instalarAna();
        String token = acceso.usarCodigoAcceso(acceso.generarCodigoAcceso(), "", "ana", "ana-recuperada");
        assertNotNull(acceso.validar(token, false));
        assertEquals("Ana Mora", acceso.validar(token, false).getNombre()); // sin nombre, conserva el anterior
        assertEquals(AccesoException.Motivo.CREDENCIALES, motivo(() -> acceso.entrar("ana", CLAVE_ANA)));
        assertNotNull(acceso.entrar("ana", "ana-recuperada"));
        assertEquals(1, acceso.cuentas().length);
    }

    @Test
    void archivoDanadoDejaLaSedeSinCuentas() throws Exception {
        Files.writeString(carpeta.resolve("cuentas.json"), "{esto no es json", StandardCharsets.UTF_8);
        assertTrue(nuevoServicio().necesitaInstalacion());
    }

    /** Reloj que las pruebas mueven a mano. */
    static final class RelojMovible extends Clock {
        private Instant ahora;

        RelojMovible(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Costa_Rica");
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
