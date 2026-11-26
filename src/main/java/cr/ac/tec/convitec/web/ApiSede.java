package cr.ac.tec.convitec.web;

import com.sun.net.httpserver.HttpExchange;
import cr.ac.tec.convitec.config.ConfigSede;
import cr.ac.tec.convitec.rutas.SalidasDiarias;

import java.time.Clock;
import java.time.LocalTime;

/**
 * GET /api/sede: qué sede es esta y cuál es la próxima salida del vehículo.
 *
 * Ejemplo de respuesta:
 * {
 *   "codigo": "ALA", "nombre": "Alajuela", "puertoHttp": 8082,
 *   "salidas": ["11:00", "15:00"],
 *   "proximaSalida": { "hora": "15:00", "diaSiguiente": false }
 * }
 *
 * La próxima salida es la que le toca a un pedido registrado AHORA (SalidasDiarias.salidaParaRegistro).
 */
final class ApiSede extends ManejadorApi {

    private final ConfigSede config;
    private final Clock reloj;

    ApiSede(ConfigSede config, Clock reloj) {
        super("/api/sede", "GET", "HEAD");
        this.config = config;
        this.reloj = reloj;
    }

    @Override
    protected Object atender(HttpExchange intercambio) {
        SalidasDiarias.Asignacion proxima = SalidasDiarias.salidaParaRegistro(LocalTime.now(reloj));
        return new SedeDTO(config.getCodigo(), config.getNombre(), config.getPuertoHttp(),
                new String[]{SalidasDiarias.SALIDA_MANANA, SalidasDiarias.SALIDA_TARDE},
                new SalidaDTO(proxima.getHoraSalida(), proxima.esDiaSiguiente()));
    }

    /** Forma JSON de la sede. */
    static final class SedeDTO {
        final String codigo;
        final String nombre;
        final int puertoHttp;
        final String[] salidas;
        final SalidaDTO proximaSalida;

        SedeDTO(String codigo, String nombre, int puertoHttp, String[] salidas, SalidaDTO proximaSalida) {
            this.codigo = codigo;
            this.nombre = nombre;
            this.puertoHttp = puertoHttp;
            this.salidas = salidas;
            this.proximaSalida = proximaSalida;
        }
    }

    /** Forma JSON de una salida del vehículo. */
    static final class SalidaDTO {
        final String hora;
        final boolean diaSiguiente;

        SalidaDTO(String hora, boolean diaSiguiente) {
            this.hora = hora;
            this.diaSiguiente = diaSiguiente;
        }
    }
}
