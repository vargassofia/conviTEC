package cr.ac.tec.convitec.rutas;

import java.time.LocalTime;

/**
 * Horarios de salida del vehículo de cada sede: 11:00 y 15:00.
 *
 * Regla para saber en qué salida va un pedido según su hora de registro:
 * - antes de las 11:00            -> salida de las 11:00 del mismo día
 * - de 11:00 a antes de las 15:00 -> salida de las 15:00 del mismo día
 * - desde las 15:00               -> salida de las 11:00 del DÍA SIGUIENTE
 */
public final class SalidasDiarias {

    public static final String SALIDA_MANANA = "11:00";
    public static final String SALIDA_TARDE = "15:00";

    private static final LocalTime HORA_MANANA = LocalTime.of(11, 0);
    private static final LocalTime HORA_TARDE = LocalTime.of(15, 0);

    private SalidasDiarias() {
        // Clase de utilidad: no se instancia
    }

    /** Indica en qué salida va un pedido registrado a la hora dada. */
    public static Asignacion salidaParaRegistro(LocalTime horaRegistro) {
        if (horaRegistro == null) {
            throw new IllegalArgumentException("La hora de registro del pedido no puede ser nula.");
        }
        if (horaRegistro.isBefore(HORA_MANANA)) {
            return new Asignacion(SALIDA_MANANA, false);
        }
        if (horaRegistro.isBefore(HORA_TARDE)) {
            return new Asignacion(SALIDA_TARDE, false);
        }
        return new Asignacion(SALIDA_MANANA, true);
    }

    /** true si el texto es "11:00" o "15:00". */
    public static boolean esHoraSalidaValida(String hora) {
        return SALIDA_MANANA.equals(hora) || SALIDA_TARDE.equals(hora);
    }

    /** Resultado de salidaParaRegistro: la hora de salida y si es al día siguiente. */
    public static final class Asignacion {

        private final String horaSalida;
        private final boolean diaSiguiente;

        Asignacion(String horaSalida, boolean diaSiguiente) {
            this.horaSalida = horaSalida;
            this.diaSiguiente = diaSiguiente;
        }

        /** "11:00" o "15:00". */
        public String getHoraSalida() {
            return horaSalida;
        }

        /** true si el pedido sale hasta mañana. */
        public boolean esDiaSiguiente() {
            return diaSiguiente;
        }

        /** Ejemplo: "15:00" o "11:00 (día siguiente)". */
        @Override
        public String toString() {
            return diaSiguiente ? horaSalida + " (día siguiente)" : horaSalida;
        }
    }
}
