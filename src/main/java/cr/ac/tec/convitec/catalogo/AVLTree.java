package cr.ac.tec.convitec.catalogo;

/**
 * Árbol AVL genérico que mantiene sus claves ordenadas y balanceadas.
 *
 * @param <K> tipo de las claves, ordenables mediante {@link Comparable}
 * @param <V> tipo de los valores asociados
 */
public class AVLTree<K extends Comparable<K>, V> {

    private Nodo raiz;
    private int cantidad;

    /** Inserta una clave nueva; no reemplaza valores de claves existentes. */
    public boolean insertar(K clave, V valor) {
        validarClave(clave);
        boolean[] insertado = {false};
        raiz = insertar(raiz, clave, valor, insertado);
        if (insertado[0]) {
            cantidad++;
        }
        return insertado[0];
    }

    /** Reemplaza el valor asociado a una clave existente. */
    public boolean actualizar(K clave, V valor) {
        validarClave(clave);
        Nodo nodo = buscarNodo(clave);
        if (nodo == null) {
            return false;
        }
        nodo.valor = valor;
        return true;
    }

    /** Busca una clave y devuelve su valor, o null si no existe. */
    public V buscar(K clave) {
        validarClave(clave);
        Nodo nodo = buscarNodo(clave);
        return nodo == null ? null : nodo.valor;
    }

    /** Indica si el árbol contiene la clave indicada. */
    public boolean contiene(K clave) {
        validarClave(clave);
        return buscarNodo(clave) != null;
    }

    /** Elimina una clave existente y rebalancea el árbol. */
    public boolean eliminar(K clave) {
        validarClave(clave);
        boolean[] eliminado = {false};
        raiz = eliminar(raiz, clave, eliminado);
        if (eliminado[0]) {
            cantidad--;
        }
        return eliminado[0];
    }

    /** Devuelve la cantidad de claves almacenadas. */
    public int tamano() {
        return cantidad;
    }

    /** Indica si el árbol no contiene claves. */
    public boolean estaVacio() {
        return cantidad == 0;
    }

    /** Devuelve la altura del árbol; la altura de un árbol vacío es cero. */
    public int altura() {
        return altura(raiz);
    }

    /** Elimina todos los nodos del árbol. */
    public void vaciar() {
        raiz = null;
        cantidad = 0;
    }

    /** Visita las claves en orden ascendente. */
    public void recorrerEnOrden(Visitante<K, V> visitante) {
        if (visitante == null) {
            throw new IllegalArgumentException("El visitante no puede ser null");
        }
        recorrerEnOrden(raiz, visitante);
    }

    /** Copia los valores del árbol en orden ascendente de clave. */
    public Object[] valoresEnOrden() {
        Object[] valores = new Object[cantidad];
        llenarValores(raiz, valores, new int[1]);
        return valores;
    }

    /** Copia las claves del árbol en orden ascendente. */
    public Object[] clavesEnOrden() {
        Object[] claves = new Object[cantidad];
        llenarClaves(raiz, claves, new int[1]);
        return claves;
    }

    /** Comprueba el orden, las alturas almacenadas y el balance AVL de cada nodo. */
    public boolean esValido() {
        return validar(raiz, null, null).valido;
    }

    private Nodo insertar(Nodo nodo, K clave, V valor, boolean[] insertado) {
        if (nodo == null) {
            insertado[0] = true;
            return new Nodo(clave, valor);
        }

        int comparacion = clave.compareTo(nodo.clave);
        if (comparacion < 0) {
            nodo.izquierdo = insertar(nodo.izquierdo, clave, valor, insertado);
        } else if (comparacion > 0) {
            nodo.derecho = insertar(nodo.derecho, clave, valor, insertado);
        } else {
            return nodo;
        }
        return insertado[0] ? rebalancear(nodo) : nodo;
    }

    private Nodo eliminar(Nodo nodo, K clave, boolean[] eliminado) {
        if (nodo == null) {
            return null;
        }

        int comparacion = clave.compareTo(nodo.clave);
        if (comparacion < 0) {
            nodo.izquierdo = eliminar(nodo.izquierdo, clave, eliminado);
        } else if (comparacion > 0) {
            nodo.derecho = eliminar(nodo.derecho, clave, eliminado);
        } else {
            eliminado[0] = true;
            if (nodo.izquierdo == null) {
                return nodo.derecho;
            }
            if (nodo.derecho == null) {
                return nodo.izquierdo;
            }

            Nodo sucesor = minimo(nodo.derecho);
            nodo.clave = sucesor.clave;
            nodo.valor = sucesor.valor;
            nodo.derecho = eliminarMinimo(nodo.derecho);
        }
        return eliminado[0] ? rebalancear(nodo) : nodo;
    }

    private Nodo eliminarMinimo(Nodo nodo) {
        if (nodo.izquierdo == null) {
            return nodo.derecho;
        }
        nodo.izquierdo = eliminarMinimo(nodo.izquierdo);
        return rebalancear(nodo);
    }

    private Nodo minimo(Nodo nodo) {
        Nodo actual = nodo;
        while (actual.izquierdo != null) {
            actual = actual.izquierdo;
        }
        return actual;
    }

    private Nodo buscarNodo(K clave) {
        Nodo actual = raiz;
        while (actual != null) {
            int comparacion = clave.compareTo(actual.clave);
            if (comparacion == 0) {
                return actual;
            }
            actual = comparacion < 0 ? actual.izquierdo : actual.derecho;
        }
        return null;
    }

    private Nodo rebalancear(Nodo nodo) {
        actualizarAltura(nodo);
        int balance = factorBalance(nodo);

        if (balance > 1) {
            if (factorBalance(nodo.izquierdo) < 0) {
                // El subárbol izquierdo se inclina a la derecha: primero se corrige ese hijo.
                nodo.izquierdo = rotacionSimpleIzquierda(nodo.izquierdo);
                return rotacionSimpleDerecha(nodo);
            }
            // El subárbol izquierdo está pesado: rotación simple hacia la derecha.
            return rotacionSimpleDerecha(nodo);
        }

        if (balance < -1) {
            if (factorBalance(nodo.derecho) > 0) {
                // El subárbol derecho se inclina a la izquierda: primero se corrige ese hijo.
                nodo.derecho = rotacionSimpleDerecha(nodo.derecho);
                return rotacionSimpleIzquierda(nodo);
            }
            // El subárbol derecho está pesado: rotación simple hacia la izquierda.
            return rotacionSimpleIzquierda(nodo);
        }
        return nodo;
    }

    private Nodo rotacionSimpleIzquierda(Nodo pivote) {
        Nodo nuevaRaiz = pivote.derecho;
        pivote.derecho = nuevaRaiz.izquierdo;
        nuevaRaiz.izquierdo = pivote;
        actualizarAltura(pivote);
        actualizarAltura(nuevaRaiz);
        return nuevaRaiz;
    }

    private Nodo rotacionSimpleDerecha(Nodo pivote) {
        Nodo nuevaRaiz = pivote.izquierdo;
        pivote.izquierdo = nuevaRaiz.derecho;
        nuevaRaiz.derecho = pivote;
        actualizarAltura(pivote);
        actualizarAltura(nuevaRaiz);
        return nuevaRaiz;
    }

    private void recorrerEnOrden(Nodo nodo, Visitante<K, V> visitante) {
        if (nodo == null) {
            return;
        }
        recorrerEnOrden(nodo.izquierdo, visitante);
        visitante.visitar(nodo.clave, nodo.valor);
        recorrerEnOrden(nodo.derecho, visitante);
    }

    private void llenarValores(Nodo nodo, Object[] valores, int[] indice) {
        if (nodo == null) {
            return;
        }
        llenarValores(nodo.izquierdo, valores, indice);
        valores[indice[0]++] = nodo.valor;
        llenarValores(nodo.derecho, valores, indice);
    }

    private void llenarClaves(Nodo nodo, Object[] claves, int[] indice) {
        if (nodo == null) {
            return;
        }
        llenarClaves(nodo.izquierdo, claves, indice);
        claves[indice[0]++] = nodo.clave;
        llenarClaves(nodo.derecho, claves, indice);
    }

    private EstadoValidacion<K> validar(Nodo nodo, K limiteInferior, K limiteSuperior) {
        if (nodo == null) {
            return new EstadoValidacion<>(true, 0);
        }
        if ((limiteInferior != null && nodo.clave.compareTo(limiteInferior) <= 0)
                || (limiteSuperior != null && nodo.clave.compareTo(limiteSuperior) >= 0)) {
            return new EstadoValidacion<>(false, 0);
        }

        EstadoValidacion<K> izquierda = validar(nodo.izquierdo, limiteInferior, nodo.clave);
        EstadoValidacion<K> derecha = validar(nodo.derecho, nodo.clave, limiteSuperior);
        int alturaCalculada = Math.max(izquierda.altura, derecha.altura) + 1;
        boolean balanceado = Math.abs(izquierda.altura - derecha.altura) <= 1;
        boolean alturaCorrecta = nodo.altura == alturaCalculada;
        return new EstadoValidacion<>(
                izquierda.valido && derecha.valido && balanceado && alturaCorrecta,
                alturaCalculada);
    }

    private void actualizarAltura(Nodo nodo) {
        nodo.altura = Math.max(altura(nodo.izquierdo), altura(nodo.derecho)) + 1;
    }

    private int factorBalance(Nodo nodo) {
        return altura(nodo.izquierdo) - altura(nodo.derecho);
    }

    private int altura(Nodo nodo) {
        return nodo == null ? 0 : nodo.altura;
    }

    private void validarClave(K clave) {
        if (clave == null) {
            throw new IllegalArgumentException("La clave no puede ser null");
        }
    }

    /** Acción ejecutada para cada par clave-valor durante un recorrido. */
    @FunctionalInterface
    public interface Visitante<K, V> {
        void visitar(K clave, V valor);
    }

    private final class Nodo {
        private K clave;
        private V valor;
        private int altura;
        private Nodo izquierdo;
        private Nodo derecho;

        private Nodo(K clave, V valor) {
            this.clave = clave;
            this.valor = valor;
            this.altura = 1;
        }
    }

    private static final class EstadoValidacion<K extends Comparable<K>> {
        private final boolean valido;
        private final int altura;

        private EstadoValidacion(boolean valido, int altura) {
            this.valido = valido;
            this.altura = altura;
        }
    }
}
