package cr.ac.tec.convitec.modelo;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductoTest {

    @Test
    void construyeProductoValidoYRecortaCampos() {
        Producto producto = new Producto(" B01 ", " Bebida ", " Bebida fría ", " Bebidas ", 1000.0);

        assertEquals("B01", producto.getCodigo());
        assertEquals("Bebida", producto.getNombre());
        assertEquals("Bebida fría", producto.getDescripcion());
        assertEquals("Bebidas", producto.getCategoria());
        assertEquals(1000.0, producto.getPrecio());
    }

    @Test
    void rechazaCodigoNombreYCategoriaInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> new Producto(null, "Bebida", "", "Bebidas", 1000));
        assertThrows(IllegalArgumentException.class, () -> new Producto("", "Bebida", "", "Bebidas", 1000));
        assertThrows(IllegalArgumentException.class, () -> new Producto("   ", "Bebida", "", "Bebidas", 1000));
        assertThrows(IllegalArgumentException.class, () -> new Producto("B01", null, "", "Bebidas", 1000));
        assertThrows(IllegalArgumentException.class, () -> new Producto("B01", "", "", "Bebidas", 1000));
        assertThrows(IllegalArgumentException.class, () -> new Producto("B01", "  ", "", "Bebidas", 1000));
        assertThrows(IllegalArgumentException.class, () -> new Producto("B01", "Bebida", "", null, 1000));
        assertThrows(IllegalArgumentException.class, () -> new Producto("B01", "Bebida", "", "", 1000));
        assertThrows(IllegalArgumentException.class, () -> new Producto("B01", "Bebida", "", "  ", 1000));
    }

    @Test
    void descripcionNulaSeGuardaComoVacia() {
        Producto producto = new Producto("C01", "Café", null, "Bebidas calientes", 1500);

        assertEquals("", producto.getDescripcion());
    }

    @Test
    void normalizaCodigoAMayusculas() {
        Producto minusculas = new Producto(" b01 ", "Bebida", "", "Bebidas", 1000);
        Producto mayusculas = new Producto("B01", "Otra bebida", "Otra descripción", "Otra", 2000);

        assertEquals("B01", minusculas.getCodigo());
        assertEquals(minusculas, mayusculas);
        assertEquals(0, minusculas.compararPorCodigo(mayusculas));
    }

    @Test
    void rechazaPrecioCeroNegativoONoFinito() {
        assertThrows(IllegalArgumentException.class,
                () -> new Producto("B01", "Bebida", "", "Bebidas", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Producto("B01", "Bebida", "", "Bebidas", -1));
        assertThrows(IllegalArgumentException.class,
                () -> new Producto("B01", "Bebida", "", "Bebidas", Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new Producto("B01", "Bebida", "", "Bebidas", Double.POSITIVE_INFINITY));
    }

    @Test
    void conPrecioCreaUnaCopiaSinModificarElOriginal() {
        Producto original = new Producto("C01", "Café", "", "Bebidas calientes", 1500);

        Producto actualizado = original.conPrecio(1750);

        assertNotSame(original, actualizado);
        assertEquals(1500, original.getPrecio());
        assertEquals(1750, actualizado.getPrecio());
        assertEquals(original, actualizado);
    }

    @Test
    void igualdadYHashCodeDependenSoloDelCodigo() {
        Producto primero = new Producto("B01", "Bebida", "Fría", "Bebidas", 1000);
        Producto mismoCodigo = new Producto("b01", "Otra", "Diferente", "Otra", 2000);
        Producto otroCodigo = new Producto("C01", "Bebida", "Fría", "Bebidas", 1000);

        assertEquals(primero, mismoCodigo);
        assertEquals(primero.hashCode(), mismoCodigo.hashCode());
        assertNotEquals(primero, otroCodigo);
        assertNotEquals(null, primero);
        assertNotEquals("B01", primero);
    }

    @Test
    void comparaCodigosIgnorandoMayusculas() {
        Producto primero = new Producto("C01", "Café", "", "Bebidas calientes", 1500);
        Producto anterior = new Producto("B01", "Bebida", "", "Bebidas", 1000);
        Producto posterior = new Producto("S01", "Sándwich", "", "Comidas", 2500);

        assertTrue(primero.compararPorCodigo(anterior) > 0);
        assertTrue(primero.compararPorCodigo(posterior) < 0);
        assertThrows(IllegalArgumentException.class, () -> primero.compararPorCodigo(null));
    }

    @Test
    void gsonConvierteArregloDeProductosDeIdaYVuelta() {
        Producto[] productos = {
                new Producto("B01", "Bebida", "Bebida fría", "Bebidas", 1000),
                new Producto("C01", "Café", "Café preparado", "Bebidas calientes", 1500)
        };
        Gson gson = new Gson();

        Producto[] recuperados = gson.fromJson(gson.toJson(productos), Producto[].class);

        assertArrayEquals(productos, recuperados);
        assertEquals(productos[0].getPrecio(), recuperados[0].getPrecio());
        assertEquals(productos[1].getNombre(), recuperados[1].getNombre());
    }
}
