package cr.ac.tec.convitec.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigSedeTest {

    private static final String BASE = String.join("\n",
            "# comentario",
            "sede.codigo=ALA",
            "sede.nombre=Alajuela",
            "http.puerto=8082",
            "rabbit.host=localhost",
            "rabbit.puerto=5672",
            "rabbit.usuario=guest",
            "rabbit.clave=guest",
            "reserva.timeoutMs=5000",
            "datos.carpeta=datos/ALA");

    @Test
    void cargaLasCuatroSedes() {
        String[] codigos = {"LIB", "ALA", "SJO", "LIM"};
        String[] nombres = {"Liberia", "Alajuela", "San José", "Limón"};
        for (int i = 0; i < codigos.length; i++) {
            ConfigSede c = ConfigSede.cargarDesdeRecurso("sedes/sede-" + codigos[i] + ".properties");
            assertEquals(codigos[i], c.getCodigo());
            assertEquals(nombres[i], c.getNombre()); // las tildes se leen bien (UTF-8)
            assertEquals(8081 + i, c.getPuertoHttp());
            assertEquals("localhost", c.getRabbitHost());
            assertEquals(5672, c.getRabbitPuerto());
            assertEquals("guest", c.getRabbitUsuario());
            assertEquals(5000, c.getTimeoutReservaMs());
            assertEquals("datos/" + codigos[i], c.getCarpetaDatos());
            assertEquals(nombres[i], SedesConocidas.nombreDe(c.getCodigo()));
        }
    }

    @Test
    void cargaDesdeArchivo() throws IOException {
        Path archivo = Files.createTempFile("sede-prueba", ".properties");
        try {
            Files.writeString(archivo, BASE, StandardCharsets.UTF_8);
            assertEquals("ALA", ConfigSede.cargarDesdeArchivo(archivo).getCodigo());
        } finally {
            Files.deleteIfExists(archivo);
        }
        assertThrows(ConfigException.class, () -> ConfigSede.cargarDesdeArchivo(Path.of("no-existe.properties")));
        assertThrows(ConfigException.class, () -> ConfigSede.cargarDesdeRecurso("sedes/no-existe.properties"));
    }

    @Test
    void campoFaltanteLanzaError() {
        String sinHost = BASE.replace("rabbit.host=localhost\n", "");
        ConfigException e = assertThrows(ConfigException.class, () -> ConfigSede.desdeTexto(sinHost, "prueba", null));
        assertTrue(e.getMessage().contains("rabbit.host"));

        String hostVacio = BASE.replace("rabbit.host=localhost", "rabbit.host=");
        assertThrows(ConfigException.class, () -> ConfigSede.desdeTexto(hostVacio, "prueba", null));
        assertThrows(ConfigException.class, () -> ConfigSede.desdeTexto("", "prueba", null));
    }

    @Test
    void numeroInvalidoLanzaError() {
        ConfigException e = assertThrows(ConfigException.class,
                () -> ConfigSede.desdeTexto(BASE.replace("rabbit.puerto=5672", "rabbit.puerto=abc"), "prueba", null));
        assertTrue(e.getMessage().contains("rabbit.puerto"));
        assertThrows(ConfigException.class,
                () -> ConfigSede.desdeTexto(BASE.replace("http.puerto=8082", "http.puerto=70000"), "prueba", null));
        assertThrows(ConfigException.class,
                () -> ConfigSede.desdeTexto(BASE.replace("reserva.timeoutMs=5000", "reserva.timeoutMs=0"), "prueba", null));
    }

    @Test
    void codigoDebeTenerTresLetras() {
        assertThrows(ConfigException.class,
                () -> ConfigSede.desdeTexto(BASE.replace("sede.codigo=ALA", "sede.codigo=AL"), "prueba", null));
        assertThrows(ConfigException.class,
                () -> ConfigSede.desdeTexto(BASE.replace("sede.codigo=ALA", "sede.codigo=A1A"), "prueba", null));
    }

    @Test
    void claveRepetidaOLineaMalaLanzaError() {
        assertThrows(ConfigException.class, () -> ConfigSede.desdeTexto(BASE + "\nrabbit.puerto=5673", "prueba", null));
        assertThrows(ConfigException.class, () -> ConfigSede.desdeTexto(BASE + "\nesto no es clave valor", "prueba", null));
    }

    @Test
    void variableDeEntornoReemplazaLaClave() {
        assertEquals("guest", ConfigSede.desdeTexto(BASE, "prueba", null).getRabbitClave());
        assertEquals("secreta", ConfigSede.desdeTexto(BASE, "prueba", "secreta").getRabbitClave());
        assertFalse(ConfigSede.desdeTexto(BASE, "prueba", "secreta").toString().contains("secreta"));
    }
}
