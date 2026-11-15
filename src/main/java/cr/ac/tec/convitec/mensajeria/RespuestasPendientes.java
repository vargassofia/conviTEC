package cr.ac.tec.convitec.mensajeria;

/**
 * Registro de las solicitudes que están esperando respuesta de otra sede.
 *
 * Funciona con dos hilos:
 * - El hilo que pide la reserva llama registrar(id) y luego esperar(id, timeout): se queda
 *   dormido con wait() hasta que llegue la respuesta o se venza el tiempo.
 * - El hilo de RabbitMQ, cuando llega la respuesta, llama completar(id, mensaje): guarda la
 *   respuesta y despierta al que espera con notifyAll().
 *
 * Todo es synchronized sobre este objeto. Las entradas se guardan en un arreglo propio que
 * crece manualmente, y se eliminan apenas se usan (o se vence el tiempo) para no acumular memoria.
 * Las respuestas que llegan tarde o con un correlationId desconocido se ignoran.
 */
public class RespuestasPendientes {

    private static final int CAPACIDAD_INICIAL = 8;

    /** Una solicitud pendiente. */
    private static final class Entrada {
        final String correlationId;
        Mensaje respuesta;
        boolean completada;

        Entrada(String correlationId) {
            this.correlationId = correlationId;
        }
    }

    private Entrada[] entradas = new Entrada[CAPACIDAD_INICIAL];
    private int cantidad = 0;

    /**
     * Anota que se va a esperar una respuesta con ese correlationId.
     * Debe llamarse ANTES de enviar la solicitud, para no perder una respuesta muy rápida.
     *
     * @throws IllegalArgumentException si el id es nulo o ya está registrado.
     */
    public synchronized void registrar(String correlationId) {
        if (correlationId == null) {
            throw new IllegalArgumentException("El correlationId no puede ser nulo.");
        }
        if (buscar(correlationId) != -1) {
            throw new IllegalArgumentException("El correlationId '" + correlationId + "' ya está registrado.");
        }
        if (cantidad == entradas.length) {
            Entrada[] nuevas = new Entrada[entradas.length * 2];
            for (int i = 0; i < cantidad; i++) {
                nuevas[i] = entradas[i];
            }
            entradas = nuevas;
        }
        entradas[cantidad] = new Entrada(correlationId);
        cantidad++;
    }

    /**
     * Espera la respuesta hasta timeoutMs milisegundos. Siempre elimina la entrada al terminar.
     *
     * @return la respuesta, o null si se venció el tiempo (o el id no estaba registrado).
     * @throws IllegalArgumentException si timeoutMs <= 0.
     */
    public synchronized Mensaje esperar(String correlationId, long timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("El tiempo de espera debe ser mayor que 0.");
        }
        int pos = buscar(correlationId);
        if (pos == -1) {
            return null;
        }
        Entrada entrada = entradas[pos];
        long limite = System.currentTimeMillis() + timeoutMs;
        try {
            // Se usa un while porque wait() puede despertar sin aviso (o por otra respuesta)
            while (!entrada.completada) {
                long restante = limite - System.currentTimeMillis();
                if (restante <= 0) {
                    break;
                }
                wait(restante);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // se respeta la interrupción y se trata como "sin respuesta"
        } finally {
            eliminar(entrada.correlationId);
        }
        return entrada.completada ? entrada.respuesta : null;
    }

    /**
     * Entrega una respuesta (lo llama el hilo de RabbitMQ).
     *
     * @return true si alguien la estaba esperando; false si llegó tarde o el id es desconocido (se ignora).
     */
    public synchronized boolean completar(String correlationId, Mensaje respuesta) {
        int pos = buscar(correlationId);
        if (pos == -1 || entradas[pos].completada) {
            return false;
        }
        entradas[pos].respuesta = respuesta;
        entradas[pos].completada = true;
        notifyAll();
        return true;
    }

    /** Quita una solicitud sin esperar (por ejemplo, si el envío falló). */
    public synchronized void cancelar(String correlationId) {
        eliminar(correlationId);
    }

    /** Cuántas solicitudes están esperando (para pruebas y diagnóstico). */
    public synchronized int getCantidadPendientes() {
        return cantidad;
    }

    private int buscar(String correlationId) {
        if (correlationId == null) {
            return -1;
        }
        for (int i = 0; i < cantidad; i++) {
            if (entradas[i].correlationId.equals(correlationId)) {
                return i;
            }
        }
        return -1;
    }

    /** Elimina moviendo la última entrada al hueco (el orden no importa). */
    private void eliminar(String correlationId) {
        int pos = buscar(correlationId);
        if (pos == -1) {
            return;
        }
        cantidad--;
        entradas[pos] = entradas[cantidad];
        entradas[cantidad] = null;
    }
}
