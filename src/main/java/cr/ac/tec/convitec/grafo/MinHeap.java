package cr.ac.tec.convitec.grafo;

import java.util.Arrays;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Montículo mínimo genérico que ordena sus elementos con un comparador proporcionado.
 */
public class MinHeap<T> {

    private static final int CAPACIDAD_INICIAL = 10;

    private final Comparator<? super T> comparador;
    private Object[] elementos;
    private int tamano;

    public MinHeap(Comparator<? super T> comparador) {
        this.comparador = Objects.requireNonNull(comparador, "El comparador no puede ser null");
        this.elementos = new Object[CAPACIDAD_INICIAL];
    }

    /** Agrega un elemento y lo desplaza hasta conservar la propiedad de mínimo. */
    public void insertar(T elemento) {
        Objects.requireNonNull(elemento, "El elemento no puede ser null");
        asegurarCapacidad();

        int indice = tamano++;
        while (indice > 0) {
            int padre = (indice - 1) / 2;
            T elementoPadre = elementoEn(padre);
            if (comparador.compare(elemento, elementoPadre) >= 0) {
                break;
            }
            elementos[indice] = elementoPadre;
            indice = padre;
        }
        elementos[indice] = elemento;
    }

    /** Devuelve el menor elemento sin retirarlo. */
    public T verMinimo() {
        validarNoVacio();
        return elementoEn(0);
    }

    /** Retira y devuelve el menor elemento. */
    public T extraerMinimo() {
        validarNoVacio();
        T minimo = elementoEn(0);
        T ultimo = elementoEn(--tamano);
        elementos[tamano] = null;

        if (tamano > 0) {
            int indice = 0;
            while (true) {
                int hijoIzquierdo = indice * 2 + 1;
                if (hijoIzquierdo >= tamano) {
                    break;
                }

                int hijoMenor = hijoIzquierdo;
                int hijoDerecho = hijoIzquierdo + 1;
                if (hijoDerecho < tamano
                        && comparador.compare(elementoEn(hijoDerecho), elementoEn(hijoIzquierdo)) < 0) {
                    hijoMenor = hijoDerecho;
                }
                if (comparador.compare(ultimo, elementoEn(hijoMenor)) <= 0) {
                    break;
                }

                elementos[indice] = elementos[hijoMenor];
                indice = hijoMenor;
            }
            elementos[indice] = ultimo;
        }
        return minimo;
    }

    public boolean estaVacio() {
        return tamano == 0;
    }

    public int getTamano() {
        return tamano;
    }

    private void asegurarCapacidad() {
        if (tamano == elementos.length) {
            elementos = Arrays.copyOf(elementos, elementos.length * 2);
        }
    }

    private void validarNoVacio() {
        if (estaVacio()) {
            throw new NoSuchElementException("El montículo está vacío");
        }
    }

    @SuppressWarnings("unchecked")
    private T elementoEn(int indice) {
        return (T) elementos[indice];
    }
}
