package cr.ac.tec.convitec.persistencia;

/** Avisos producidos al cargar datos de una sede. */
public final class ResultadoCarga {

    private String[] avisos = new String[0];

    public synchronized void agregarAviso(String aviso) {
        if (aviso == null || aviso.trim().isEmpty()) {
            return;
        }
        String[] nuevos = new String[avisos.length + 1];
        System.arraycopy(avisos, 0, nuevos, 0, avisos.length);
        nuevos[avisos.length] = aviso;
        avisos = nuevos;
    }

    public synchronized String[] getAvisos() {
        String[] copia = new String[avisos.length];
        System.arraycopy(avisos, 0, copia, 0, avisos.length);
        return copia;
    }

    public synchronized boolean tieneAvisos() {
        return avisos.length > 0;
    }
}
