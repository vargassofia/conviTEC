package cr.ac.tec.convitec.app;

import cr.ac.tec.convitec.config.ConfigSede;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ArgumentosSedeTest {

    @Test
    void codigoValidoUsaElRecursoInterno() {
        ArgumentosSede a = ArgumentosSede.interpretar(new String[]{"ala"});
        assertEquals("ALA", a.getCodigoSede());
        assertNull(a.getArchivoConfig());
        assertEquals("sedes/sede-ALA.properties", a.getRecursoConfig());
        ConfigSede c = a.cargarConfiguracion();
        assertEquals("Alajuela", c.getNombre());
    }

    @Test
    void codigoDesconocido() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ArgumentosSede.interpretar(new String[]{"XYZ"}));
        assertTrue(e.getMessage().contains("Uso:"));
    }

    @Test
    void sinArgumentos() {
        assertThrows(IllegalArgumentException.class, () -> ArgumentosSede.interpretar(new String[0]));
        assertThrows(IllegalArgumentException.class, () -> ArgumentosSede.interpretar(null));
        assertThrows(IllegalArgumentException.class, () -> ArgumentosSede.interpretar(new String[]{"  "}));
    }

    @Test
    void configSinRuta() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ArgumentosSede.interpretar(new String[]{"ALA", "--config"}));
        assertTrue(e.getMessage().contains("--config"));
    }

    @Test
    void configConArchivoInexistente() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ArgumentosSede.interpretar(new String[]{"ALA", "--config", "no-existe.properties"}));
        assertTrue(e.getMessage().contains("No existe"));
    }

    @Test
    void opcionDesconocidaYArgumentosDeMas() {
        assertThrows(IllegalArgumentException.class,
                () -> ArgumentosSede.interpretar(new String[]{"ALA", "--puerto", "9000"}));
        assertThrows(IllegalArgumentException.class,
                () -> ArgumentosSede.interpretar(new String[]{"ALA", "--config", "a.properties", "extra"}));
    }

    @Test
    void configConArchivoExterno() throws IOException {
        Path archivo = Files.createTempFile("sede-ALA", ".properties");
        try {
            String texto = new String(ArgumentosSedeTest.class.getClassLoader()
                    .getResourceAsStream("sedes/sede-ALA.properties").readAllBytes(), StandardCharsets.UTF_8)
                    .replace("rabbit.host=localhost", "rabbit.host=192.168.1.20");
            Files.writeString(archivo, texto, StandardCharsets.UTF_8);

            ArgumentosSede a = ArgumentosSede.interpretar(new String[]{"ALA", "--config", archivo.toString()});
            assertEquals(archivo, a.getArchivoConfig());
            assertEquals("192.168.1.20", a.cargarConfiguracion().getRabbitHost());

            // El archivo es de ALA, pero se pide arrancar LIM
            ArgumentosSede otra = ArgumentosSede.interpretar(new String[]{"LIM", "--config", archivo.toString()});
            assertThrows(IllegalArgumentException.class, otra::cargarConfiguracion);
        } finally {
            Files.deleteIfExists(archivo);
        }
    }

    @Test
    void mainConArgumentosMalosTerminaConCodigo2() {
        assertEquals(2, Main.ejecutar(new String[]{"XYZ"}));
    }
}
