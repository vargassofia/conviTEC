Nota 1
Cada archivo .java debe de empezar con su paquete.
Ejemplo: 
package cr.ac.tec.convitec.app;

public class Main {
    public static void main(String[] args) {
        System.out.println("ConviTEC iniciando...");
    }
}

Nota 2
No es necesario que instalen docker, mi computadora puede ser el broker ya que tengo la instalacion de RabbitMQ en mi computadora. Por lo que en el codigo, la parte de config.properties se apuntara la IP de mi compu(Mac).
En caso de que quieran probarlo sin mi compu, usen mocks(codigo falso) hasta integramos todo.