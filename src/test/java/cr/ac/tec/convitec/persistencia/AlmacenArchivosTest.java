package cr.ac.tec.convitec.persistencia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AlmacenArchivosTest {

    @TempDir
    Path carpeta;

    @Test
    void guardaYCargaArregloYArchivoInexistenteEsVacio() {
        AlmacenArchivos almacen = new AlmacenArchivos(carpeta);
        RegistroMeta[] registros = {new RegistroMeta(14)};
        almacen.guardarArreglo("meta.json", registros);
        assertEquals(14, almacen.cargarArreglo("meta.json", RegistroMeta[].class)[0].contadorIds);
        assertEquals(0, almacen.cargarArreglo("no-existe.json", RegistroMeta[].class).length);
        assertTrue(almacen.getResultadoCarga().tieneAvisos());
    }

    @Test
    void contenidoVacioTruncadoYTipoIncorrectoSeAislanComoCorruptos() throws IOException {
        AlmacenArchivos almacen = new AlmacenArchivos(carpeta);
        String[] invalidos = {"", "[{\"version\":", "{\"version\":1}"};
        String[] archivos = {"vacio.json", "truncado.json", "tipo.json"};
        for (int i = 0; i < archivos.length; i++) {
            Files.writeString(carpeta.resolve(archivos[i]), invalidos[i]);
            assertEquals(0, almacen.cargarArreglo(archivos[i], RegistroMeta[].class).length);
            assertFalse(Files.exists(carpeta.resolve(archivos[i])));
        }
        int archivosAislados = 0;
        for (String aviso : almacen.getResultadoCarga().getAvisos()) {
            if (aviso.contains(".corrupto-")) {
                archivosAislados++;
            }
        }
        assertEquals(3, archivosAislados);
    }

    @Test
    void eliminaTemporalDeEscrituraInterrumpida() throws IOException {
        Path temporal = carpeta.resolve("inventario.json.tmp");
        Files.writeString(temporal, "[incompleto");
        AlmacenArchivos almacen = new AlmacenArchivos(carpeta);
        assertEquals(0, almacen.cargarArreglo("inventario.json", RegistroExistencia[].class).length);
        assertFalse(Files.exists(temporal));
        assertTrue(almacen.getResultadoCarga().tieneAvisos());
    }

    @Test
    void datosDesconocidosEnRegistroSeConservanParaMigracionCompatible() throws IOException {
        Files.writeString(carpeta.resolve("meta.json"),
                "[{\"version\":1,\"contadorIds\":9,\"campoFuturo\":\"ignorado\"}]");
        AlmacenArchivos almacen = new AlmacenArchivos(carpeta);
        assertEquals(9, almacen.cargarArreglo("meta.json", RegistroMeta[].class)[0].contadorIds);
    }

    @Test
    void errorDeEscrituraSeInformaComoPersistenciaException() throws IOException {
        Path archivoComoCarpeta = carpeta.resolve("no-es-carpeta");
        Files.writeString(archivoComoCarpeta, "archivo");
        AlmacenArchivos almacen = new AlmacenArchivos(archivoComoCarpeta);
        assertThrows(PersistenciaException.class,
                () -> almacen.guardarArreglo("meta.json", new RegistroMeta[]{new RegistroMeta(1)}));
    }

    @Test
    void escriturasConcurrentesDejanUnJsonCompleto() throws InterruptedException {
        AlmacenArchivos almacen = new AlmacenArchivos(carpeta);
        int hilos = 8;
        CountDownLatch inicio = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(hilos);
        AtomicReference<Throwable> error = new AtomicReference<>();
        for (int i = 0; i < hilos; i++) {
            int valor = i;
            new Thread(() -> {
                try {
                    inicio.await();
                    for (int vuelta = 0; vuelta < 30; vuelta++) {
                        almacen.guardarArreglo("meta.json",
                                new RegistroMeta[]{new RegistroMeta(valor + vuelta)});
                    }
                } catch (Throwable e) {
                    error.compareAndSet(null, e);
                } finally {
                    fin.countDown();
                }
            }).start();
        }
        inicio.countDown();
        fin.await();
        assertNull(error.get());
        assertEquals(1, almacen.cargarArreglo("meta.json", RegistroMeta[].class).length);
    }
}
