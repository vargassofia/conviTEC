package cr.ac.tec.convitec.estadisticas;

/**
 * Comparador propio (no se usa java.util.Comparator).
 *
 * comparar(a, b) devuelve:
 * - un número negativo si a va ANTES que b,
 * - 0 si son equivalentes para el orden,
 * - un número positivo si a va DESPUÉS de b.
 *
 * Al tener un solo método se puede escribir como lambda:
 *   Comparador<Venta> porMonto = (a, b) -> Double.compare(a.getMontoTotal(), b.getMontoTotal());
 */
@FunctionalInterface
public interface Comparador<T> {
    int comparar(T a, T b);
}
