package cr.ac.tec.convitec.estadisticas;

import java.lang.reflect.Array;

/**
 * Ordenamiento propio: MergeSort genérico sobre arreglos.
 *
 * ¿Por qué MergeSort?
 * - Es O(n log n) en TODOS los casos (mejor, promedio y peor), a diferencia de QuickSort,
 *   que puede caer en O(n²) con datos ya ordenados.
 * - Es ESTABLE: si dos elementos son iguales para el comparador, conservan el orden que
 *   tenían. Esto sirve para los desempates: el resultado siempre es predecible.
 * - El costo es memoria extra O(n) para el arreglo auxiliar, que con los datos de una
 *   sede no es problema.
 *
 * No modifica el arreglo original: ordena una copia y la devuelve.
 * Solo usa arreglos nativos (sin colecciones ni Arrays.sort).
 */
public final class Ordenamiento {

    private Ordenamiento() {
        // Clase de utilidad: no se instancia
    }

    /**
     * Devuelve un arreglo NUEVO con los datos ordenados según el comparador.
     *
     * @throws IllegalArgumentException si datos o comparador son nulos.
     */
    public static <T> T[] ordenar(T[] datos, Comparador<T> comparador) {
        if (datos == null) {
            throw new IllegalArgumentException("No se puede ordenar un arreglo nulo.");
        }
        if (comparador == null) {
            throw new IllegalArgumentException("Se necesita un comparador para ordenar.");
        }
        T[] resultado = copiar(datos);
        if (resultado.length <= 1) {
            return resultado; // vacío o de un elemento: ya está ordenado
        }
        T[] auxiliar = crearArreglo(datos, datos.length);
        mergeSort(resultado, auxiliar, 0, resultado.length - 1, comparador);
        return resultado;
    }

    /** Ordena datos[inicio..fin] (ambos incluidos). */
    private static <T> void mergeSort(T[] datos, T[] auxiliar, int inicio, int fin, Comparador<T> comparador) {
        if (inicio >= fin) {
            return;
        }
        int medio = inicio + (fin - inicio) / 2;
        mergeSort(datos, auxiliar, inicio, medio, comparador);
        mergeSort(datos, auxiliar, medio + 1, fin, comparador);
        mezclar(datos, auxiliar, inicio, medio, fin, comparador);
    }

    /** Mezcla las dos mitades ya ordenadas datos[inicio..medio] y datos[medio+1..fin]. */
    private static <T> void mezclar(T[] datos, T[] auxiliar, int inicio, int medio, int fin,
                                    Comparador<T> comparador) {
        for (int k = inicio; k <= fin; k++) {
            auxiliar[k] = datos[k];
        }
        int i = inicio;      // recorre la mitad izquierda
        int j = medio + 1;   // recorre la mitad derecha
        int k = inicio;      // posición donde se escribe
        while (i <= medio && j <= fin) {
            // "<= 0": en empate se toma primero el de la izquierda -> esto hace que sea ESTABLE
            if (comparador.comparar(auxiliar[i], auxiliar[j]) <= 0) {
                datos[k] = auxiliar[i];
                i++;
            } else {
                datos[k] = auxiliar[j];
                j++;
            }
            k++;
        }
        while (i <= medio) {
            datos[k] = auxiliar[i];
            i++;
            k++;
        }
        // Lo que queda de la derecha ya está en su lugar
    }

    private static <T> T[] copiar(T[] original) {
        T[] copia = crearArreglo(original, original.length);
        for (int i = 0; i < original.length; i++) {
            copia[i] = original[i];
        }
        return copia;
    }

    /** Crea un arreglo del mismo tipo que el modelo (Java no permite "new T[n]"). */
    @SuppressWarnings("unchecked")
    private static <T> T[] crearArreglo(T[] modelo, int tamano) {
        return (T[]) Array.newInstance(modelo.getClass().getComponentType(), tamano);
    }
}
