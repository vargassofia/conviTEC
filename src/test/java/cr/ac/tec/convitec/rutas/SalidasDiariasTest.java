package cr.ac.tec.convitec.rutas;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class SalidasDiariasTest {

    @Test
    void antesDeLas11VaEnLaDeLas11() {
        SalidasDiarias.Asignacion a = SalidasDiarias.salidaParaRegistro(LocalTime.of(9, 30));
        assertEquals("11:00", a.getHoraSalida());
        assertFalse(a.esDiaSiguiente());
    }

    @Test
    void desdeLas11HastaAntesDeLas15VaEnLaDeLas15() {
        SalidasDiarias.Asignacion a = SalidasDiarias.salidaParaRegistro(LocalTime.of(11, 0));
        assertEquals("15:00", a.getHoraSalida());
        assertFalse(a.esDiaSiguiente());

        SalidasDiarias.Asignacion b = SalidasDiarias.salidaParaRegistro(LocalTime.of(14, 59));
        assertEquals("15:00", b.getHoraSalida());
        assertFalse(b.esDiaSiguiente());
    }

    @Test
    void desdeLas15VaEnLaDeLas11DelDiaSiguiente() {
        SalidasDiarias.Asignacion a = SalidasDiarias.salidaParaRegistro(LocalTime.of(15, 0));
        assertEquals("11:00", a.getHoraSalida());
        assertTrue(a.esDiaSiguiente());
        assertEquals("11:00 (día siguiente)", a.toString());
    }

    @Test
    void horaNulaYHorasValidas() {
        assertThrows(IllegalArgumentException.class, () -> SalidasDiarias.salidaParaRegistro(null));
        assertTrue(SalidasDiarias.esHoraSalidaValida("11:00"));
        assertTrue(SalidasDiarias.esHoraSalidaValida("15:00"));
        assertFalse(SalidasDiarias.esHoraSalidaValida("12:00"));
        assertFalse(SalidasDiarias.esHoraSalidaValida(null));
    }
}
