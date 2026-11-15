# conviTEC
Proyecto 2 de Algoritmos y Estructuras de Datos I

ConviTEC simula una cadena de 4 tiendas de conveniencia en Costa Rica. Cada sede es una aplicación Java independiente, con sus propios datos, que se comunica con las demás **solo** por mensajes RabbitMQ.

| Código | Sede      | Puerto HTTP |
|--------|-----------|-------------|
| LIB    | Liberia   | 8081        |
| ALA    | Alajuela  | 8082        |
| SJO    | San José  | 8083        |
| LIM    | Limón     | 8084        |

## Requisitos

- **Java 21 o superior** (`java -version`).
- **Maven 3.9+** (`mvn -version`).
- **RabbitMQ 4** en **una** de las computadoras (el broker). En macOS: `brew install rabbitmq`.

## Compilar y generar el jar

```bash
mvn clean test       # compila y corre las pruebas
mvn clean package    # genera target/convitec-sede.jar (incluye todas las dependencias)
```

Las pruebas de integración con RabbitMQ (`MensajeriaRabbitIntegracionTest`) se **saltan** (`Skipped`) si el broker no está corriendo en `localhost:5672`. Para correrlas en la Mac: `brew services start rabbitmq`.

## Ejecutar una sede

```bash
java -jar target/convitec-sede.jar ALA                                   # usa la configuración interna (localhost)
java -jar target/convitec-sede.jar ALA --config mi-sede-ALA.properties   # usa un archivo externo
```

- Sin `--config` se usa `src/main/resources/sedes/sede-<COD>.properties` (broker en `localhost`, usuario `guest`).
- Con `--config` se usa un archivo externo, así en otra computadora se cambia `rabbit.host` sin recompilar.
- Si el broker no está disponible, la sede muestra un mensaje con el host y el puerto y termina con código 1.

### Comandos de la consola

Mientras la interfaz web no está lista, cada sede se maneja desde una consola:

| Comando | Qué hace |
|---|---|
| `ayuda` | Muestra los comandos |
| `stock` | Inventario de esta sede (disponible y reservado) |
| `catalogo` | Último stock conocido de las otras sedes |
| `agregar <codigo> <cantidad>` | Agrega unidades y avisa a las otras sedes |
| `reservar <SEDE> <idPedido> <codigo> <cantidad>` | Pide una reserva a otra sede y espera la respuesta |
| `confirmar <SEDE> <idPedido> <codigo>` | Confirma la reserva (la otra sede descuenta y avisa su stock nuevo) |
| `liberar <SEDE> <idPedido> <codigo>` | Libera la reserva (la otra sede devuelve las unidades) |
| `ruta <destino>` | Tiempo mínimo y camino desde esta sede (Dijkstra) |
| `entregar <11:00\|15:00> <id>:<destino> ...` | Planifica el recorrido de entregas |
| `salir` | Cierra la sede |

Los nombres con espacio se escriben **entre comillas** (`ruta "San José"`) o con **guion bajo** (`ruta San_José`). Las tildes son opcionales: `San_Jose`, `Limon` y `Paraiso` también funcionan.

Inventario de demostración (provisional): todas las sedes tienen productos distintos, y **B01 "Bebida" solo existe en Limón**.

> En Windows, si las tildes se ven mal en la consola, ejecute antes `chcp 65001`.

## Despliegue en 2 computadoras

Ejemplo: el broker y las sedes LIB y ALA en la Mac; SJO y LIM en una computadora con Windows.

### a) Permitir conexiones externas al broker (Mac)

Homebrew deja RabbitMQ escuchando **solo en 127.0.0.1**, por esta línea de `/opt/homebrew/etc/rabbitmq/rabbitmq-env.conf`:

```
NODE_IP_ADDRESS=127.0.0.1
```

Coméntela (`#NODE_IP_ADDRESS=127.0.0.1`) y reinicie:

```bash
brew services restart rabbitmq
```

### b) Crear un usuario que no sea guest (Mac)

El usuario `guest` **solo funciona desde localhost**. Para las otras computadoras:

```bash
rabbitmqctl add_user convitec <clave>
rabbitmqctl set_permissions -p / convitec ".*" ".*" ".*"
```

### c) IP y firewall (Mac)

```bash
ipconfig getifaddr en0      # IP de la Mac, por ejemplo 192.168.43.20
```

En *Ajustes del Sistema → Red → Firewall*, permita las conexiones entrantes de RabbitMQ (`beam.smp`), o desactive el firewall durante la demostración.

### d) Probar la conexión (Windows, PowerShell)

```powershell
Test-NetConnection 192.168.43.20 -Port 5672
```

Debe decir `TcpTestSucceeded : True`.

### e) Configurar y ejecutar las sedes en Windows

1. Copie `src/main/resources/sedes/sede-SJO.properties` a, por ejemplo, `C:\convitec\sede-SJO.properties`.
2. Cambie:
   ```properties
   rabbit.host=192.168.43.20
   rabbit.usuario=convitec
   rabbit.clave=cambiar
   ```
3. Para no escribir la clave real en el archivo, use la variable de entorno `CONVITEC_RABBIT_CLAVE`, que reemplaza a `rabbit.clave`:
   ```bat
   :: CMD
   set CONVITEC_RABBIT_CLAVE=<clave>
   ```
   ```powershell
   # PowerShell
   $env:CONVITEC_RABBIT_CLAVE="<clave>"
   ```
4. Ejecute:
   ```bash
   java -jar target/convitec-sede.jar SJO --config C:\convitec\sede-SJO.properties
   ```

Haga lo mismo con LIM. En la Mac, LIB y ALA pueden quedarse con la configuración interna (`localhost` y `guest`).

### f) Red

Use el **hotspot de un celular**: la red del TEC puede aislar las computadoras entre sí y bloquear el puerto 5672.

## Guion de la demostración

Con LIB y ALA en la Mac, SJO y LIM en Windows (una terminal por sede):

1. **Reservar entre sedes** (en ALA):
   `reservar LIM P1 B01 2` → `Reserva OK (LIM)`. En LIM, `stock` muestra B01 con 3 disponibles y 2 reservados.
2. **Confirmar y propagar el stock** (en ALA):
   `confirmar LIM P1 B01`. Luego, en ALA, SJO y LIB, `catalogo` muestra `LIM B01 stock 3`.
3. **Reservar sin stock** (en ALA):
   `reservar LIM P2 B01 10` → `Reserva rechazada (LIM): Limón: stock insuficiente de B01 (hay 3, se piden 10)`.
4. **Liberar** (en SJO):
   `reservar LIM P3 B01 1`, luego `liberar LIM P3 B01`. En LIM, `stock` vuelve a mostrar 3 disponibles.
5. **Sede que no responde**: cierre SJO (`salir`) y desde ALA ejecute `reservar SJO P4 C01 1` → después de 5 s: `San José no respondió a tiempo`.
6. **Ruta inalcanzable** (en LIB):
   `ruta Cartago` → `Liberia -> Cartago: no hay ruta` (Liberia y Cañas están aisladas del resto).
7. **Ruta y entregas** (en ALA):
   `ruta Limón` → 215 min por San José, Cartago, Paraíso y Turrialba.
   `entregar 11:00 P1:Puntarenas P2:Orotina P3:Heredia` → Heredia (20), Orotina (95), Puntarenas (140).

## Solución de problemas

| Síntoma | Causa probable | Solución |
|---|---|---|
| `No se pudo conectar a RabbitMQ ... Connection refused` | El broker no está corriendo, o solo escucha en 127.0.0.1 | `brew services start rabbitmq`; comente `NODE_IP_ADDRESS` (paso a); revise la IP en `rabbit.host` |
| `ACCESS_REFUSED` / `Login was refused` | Se usa `guest` desde otra computadora, o la clave es incorrecta | Use el usuario creado en el paso b y revise `CONVITEC_RABBIT_CLAVE` |
| `Test-NetConnection` falla o la conexión tarda y se cae | Firewall de la Mac o red que aísla equipos | Permita conexiones entrantes (paso c) y use un hotspot (paso f) |
| `<Sede> no respondió a tiempo` | Esa sede no está corriendo o está en otro broker | Inicie la sede y verifique que todas usan el mismo `rabbit.host` |
| `Address already in use` / puerto ocupado | Ya hay otra sede (u otro programa) usando ese puerto HTTP | Cierre la otra instancia, o cambie `http.puerto` en el `.properties`. Cada sede debe correr **una sola vez** |
| Tildes raras en la consola de Windows | La consola no está en UTF-8 | `chcp 65001` antes de ejecutar |

## Estructura

Diseño de la solución (DI3), con diagramas de arquitectura, secuencia y clases: [docs/diseno/DI3-diseno-solucion.md](docs/diseno/DI3-diseno-solucion.md)

| Paquete | Contenido |
|---|---|
| `grafo` | Grafo con matriz de adyacencia y Dijkstra |
| `rutas` | Planificador de entregas y salidas diarias |
| `modelo` | Venta y DetalleVenta (Pedido y Producto: Persona 2) |
| `entregas` | Registro de entregas como ventas |
| `estadisticas` | Historial, producto estrella, peores pedidos y MergeSort propio |
| `mensajeria` | Mensajes y transporte RabbitMQ |
| `config` | Configuración de cada sede |
| `demo` | Inventario y catálogo **provisionales** de demostración |
| `app` | Arranque de la sede (`Main`, `Sede`) y consola |
| `servicios` | Interfaces entre los módulos de los integrantes |
