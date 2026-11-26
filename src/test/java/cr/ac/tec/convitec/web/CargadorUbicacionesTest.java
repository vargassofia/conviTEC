package cr.ac.tec.convitec.web;

import cr.ac.tec.convitec.grafo.CargadorGrafo;
import cr.ac.tec.convitec.grafo.Grafo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CargadorUbicacionesTest {

    @Test
    void cadaUbicacionDelGrafoTieneCoordenadas() {
        CargadorUbicaciones.Ubicacion[] ubicaciones = CargadorUbicaciones.cargarDesdeRecurso("ubicaciones.json");
        Grafo grafo = CargadorGrafo.cargarDesdeRecurso("rutas.json");

        String[] nombres = grafo.getNombres();
        for (int i = 0; i < nombres.length; i++) {
            assertNotNull(CargadorUbicaciones.buscar(ubicaciones, nombres[i]), "Falta la coordenada de " + nombres[i]);
        }
    }

    @Test
    void buscarIgnoraTildesYMayusculas() {
        CargadorUbicaciones.Ubicacion[] u = CargadorUbicaciones.cargarDesdeTexto(
                "{\"ubicaciones\":[{\"nombre\":\"San José\",\"lat\":9.9,\"lng\":-84.1}]}");
        assertNotNull(CargadorUbicaciones.buscar(u, "  san   JOSE "));
        assertNull(CargadorUbicaciones.buscar(u, "Cartago"));
    }

    @Test
    void rechazaArchivosMalos() {
        assertThrows(IllegalArgumentException.class, () -> CargadorUbicaciones.cargarDesdeTexto(""));
        assertThrows(IllegalArgumentException.class, () -> CargadorUbicaciones.cargarDesdeTexto("no es json"));
        assertThrows(IllegalArgumentException.class, () -> CargadorUbicaciones.cargarDesdeTexto("{}"));
        // Sin nombre
        assertThrows(IllegalArgumentException.class, () -> CargadorUbicaciones.cargarDesdeTexto(
                "{\"ubicaciones\":[{\"lat\":9.9,\"lng\":-84.1}]}"));
        // Sin coordenada
        assertThrows(IllegalArgumentException.class, () -> CargadorUbicaciones.cargarDesdeTexto(
                "{\"ubicaciones\":[{\"nombre\":\"A\",\"lat\":9.9}]}"));
        // Fuera de rango
        assertThrows(IllegalArgumentException.class, () -> CargadorUbicaciones.cargarDesdeTexto(
                "{\"ubicaciones\":[{\"nombre\":\"A\",\"lat\":99,\"lng\":-84.1}]}"));
        // Repetida (sin importar tildes)
        assertThrows(IllegalArgumentException.class, () -> CargadorUbicaciones.cargarDesdeTexto(
                "{\"ubicaciones\":[{\"nombre\":\"Limón\",\"lat\":9.9,\"lng\":-83},"
                        + "{\"nombre\":\"limon\",\"lat\":9.9,\"lng\":-83}]}"));
        // Archivo que no existe
        assertThrows(IllegalArgumentException.class, () -> CargadorUbicaciones.cargarDesdeRecurso("no-existe.json"));
    }
}
