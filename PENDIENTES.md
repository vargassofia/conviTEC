# Pendientes de ConviTEC

Estado del proyecto y lo que falta, por persona. Las notas técnicas detalladas están en `notas.md` y el despliegue en `README.md`.

Para encontrar en el código todos los puntos donde se conecta el trabajo de cada una:

```bash
grep -rn "TODO PERSONA" src
```

- Solo los de una persona: `grep -rn "TODO PERSONA 1" src` o `grep -rn "TODO PERSONA 2" src`.
- En Windows (PowerShell): `Get-ChildItem -Recurse src -Filter *.java | Select-String "TODO PERSONA"`.
- O en VS Code / IntelliJ: buscar `TODO PERSONA` en todo el proyecto.

## Estado general

| Área | Paquete | Responsable | Estado |
|---|---|---|---|
| Grafo con matriz de adyacencia + carga de `rutas.json` | `grafo` | Persona 3 | ✅ Terminado |
| Dijkstra (camino más corto, alcanzabilidad) | `grafo` | Persona 3 | ✅ Terminado |
| Planificador de entregas (paso a paso y completo), salidas 11:00/15:00 | `rutas` | Persona 3 | ✅ Terminado |
| Venta y DetalleVenta | `modelo` | Persona 3 | ✅ Terminado |
| Registro de entregas como ventas | `entregas` | Persona 3 | ✅ Terminado (falta un `IGestorPedidos` real para conectarlo a la sede) |
| Estadísticas (historial, producto estrella, peores pedidos) + MergeSort propio | `estadisticas` | Persona 3 | ✅ Terminado |
| Mensajería RabbitMQ (reservar, confirmar, liberar, actualización de stock) | `mensajeria` | Persona 3 | ✅ Terminado |
| Configuración por sede (`.properties`, `CONVITEC_RABBIT_CLAVE`) | `config` | Persona 3 | ✅ Terminado |
| Arranque de la sede, consola de prueba, jar ejecutable | `app` | Persona 3 | ✅ Terminado |
| Contratos entre módulos | `servicios` | Persona 3 | ✅ Terminado |
| Inventario y catálogo de **demostración** | `demo` | Persona 3 | ⚠️ Provisional: lo reemplaza la Persona 2 |
| Pedido y Producto | `modelo` | Persona 2 | ⏳ Pendiente (clases vacías) |
| Inventario local, catálogo global, gestor de pedidos, repositorio de ventas | (nuevo) | Persona 2 | ⏳ Pendiente |
| Servicio de pedidos (orquestación entre sedes) | (nuevo) | Persona 2 | ⏳ Pendiente |
| Estructuras propias (AVL, cola) y persistencia en archivos | (nuevo) | Persona 2 | ⏳ Pendiente |
| API REST e interfaz web | (nuevo) | Persona 1 | ⏳ Pendiente |
| Prueba real en 2 computadoras, integración, documento de diseño, UML | — | Grupo | ⏳ Pendiente |

Pruebas actuales: **166** (`mvn clean test`). Las 5 de `MensajeriaRabbitIntegracionTest` salen como *Skipped* si RabbitMQ no está corriendo.

---

## Persona 2 — inventario, catálogo, pedidos y persistencia

### 1. Implementar las 4 interfaces del paquete `servicios`

| Interfaz | Qué hace | Clase de demo que reemplaza | Referencia para guiarse |
|---|---|---|---|
| `IInventarioLocal` | Reservar, confirmar y liberar unidades cuando otra sede lo pide. **synchronized** y confirmar/liberar **idempotentes** | `demo/InventarioDemo` | `InventarioDemo` + `InventarioDemoTest`; `ProcesadorMensajesTest.InventarioFalso` |
| `ICatalogoGlobal` | Guardar el último stock conocido de cada producto de las otras sedes. **synchronized** | `demo/CatalogoGlobalDemo` | `CatalogoGlobalDemo` + `InventarioDemoTest.catalogoGlobalDemo` |
| `IGestorPedidos` | `obtenerDetalle(idPedido)` → `DetalleVenta[]` (null si no existe) y `marcarEntregado` | (ninguna: hoy no hay) | `ServicioEntregasTest.GestorFalso` |
| `IRepositorioVentas` | Guardar y listar ventas **en archivos** de la sede | `entregas/RepositorioVentasMemoria` | `RepositorioVentasMemoria` + `RepositorioVentasMemoriaTest` |

Los archivos de cada sede van en `config.getCarpetaDatos()` (`datos/LIB`, `datos/ALA`, ...). Ninguna sede lee los archivos de otra.

### 2. Conectar sus clases en la sede

En `src/main/java/cr/ac/tec/convitec/app/Sede.java`, método `conDemostracion(ConfigSede, IMensajeria)`, cambiar las líneas marcadas con `TODO PERSONA 2`:

```java
IInventarioLocal inventario = InventarioDemo.paraSede(config.getCodigo());   // -> new SuInventario(config)
ICatalogoGlobal catalogo = new CatalogoGlobalDemo();                          // -> new SuCatalogoGlobal(config)
IRepositorioVentas repositorioVentas = new RepositorioVentasMemoria();        // -> new SuRepositorioVentas(config)
```

y agregar el `IGestorPedidos` como dependencia de `Sede` para crear `new ServicioEntregas(config.getNombre(), gestor, repositorioVentas)`.

Después corran `mvn clean test`: **`SedeArranqueTest`** usa ese mismo método y falla si el cambio rompe el arranque o los contratos básicos. Cuando todo pase, se puede borrar el paquete `demo`.

### 3. Completar `modelo/Pedido` y `modelo/Producto`

Están vacías y marcadas con `TODO PERSONA 2`. `Venta` y `DetalleVenta` ya existen (no las cambien).

### 4. Servicio de pedidos (orquestación)

Usa `IMensajeria` (la sede la da con `sede.getMensajeria()`) y `IServicioRutas`:

1. Por cada producto que esté en otra sede: `solicitarReserva(sede, idPedido, codigo, cantidad)`.
2. Validar que hay ruta al destino: `servicioRutas.existeRuta(nombreSede, destino)`.
3. Si **todo** se pudo y hay ruta → `confirmarReserva(...)` de cada reserva. Si **algo** falla → `liberarReserva(...)` de **todas** las que sí se reservaron y rechazar el pedido con el motivo (`RespuestaReserva.getMotivo()`).
4. Convertir los pedidos pendientes de la sede a `SolicitudEntrega[]` (id + destino) para el planificador, y la hora de registro a la salida con `SalidasDiarias.salidaParaRegistro(LocalTime)`.

Ejemplo completo en `notas.md`, Nota 23.

### 5. Estructuras propias y persistencia

- **AVL** propio para el catálogo global.
- **Cola** propia para los pedidos pendientes.
- Persistencia en archivos locales de la sede (inventario, pedidos, ventas).
- Recordatorio: **sin colecciones de Java** (`List`, `Map`, `Set`, `Queue`...) ni ordenamientos de librería. Para ordenar pueden usar `estadisticas.Ordenamiento` (MergeSort propio, Nota 19).

---

## Persona 1 — API REST e interfaz web

### 1. Arrancar el servidor HTTP

En `src/main/java/cr/ac/tec/convitec/app/Main.java` (marcado con `TODO PERSONA 1`), después de `sede.iniciar()`, en el puerto `config.getPuertoHttp()` (LIB 8081, ALA 8082, SJO 8083, LIM 8084).

### 2. Servicios ya listos que deben llamar

Todos se obtienen desde la `Sede` (`sede.getServicioRutas()`, etc.). Revisen la columna de la derecha contra las pantallas exactas del enunciado.

| Servicio | Métodos principales | Pantalla / función |
|---|---|---|
| `ServicioRutas` (`rutas`) | `existeRuta`, `caminoMasCorto` | Consulta de ruta y tiempo entre ubicaciones |
| `ServicioRutas` (`rutas`) | `iniciarRecorrido`, `hayEntregasPendientes`, `calcularSiguienteEntrega`, `obtenerRecorrido` | Botón **"Calcular siguiente punto de entrega"** (paso a paso) |
| `ServicioRutas` (`rutas`) | `planificarRecorridoCompleto` | Ver el recorrido completo de una salida (11:00 / 15:00) |
| `ServicioEntregas` (`entregas`) | `registrarParada`, `registrarRecorrido` | Registrar las entregas como ventas (necesita el `IGestorPedidos` de la Persona 2) |
| `ServicioEstadisticas` (`estadisticas`) | `historialVentas()`, `historialVentas(desde, hasta)` | Historial de ventas de la sede |
| `ServicioEstadisticas` (`estadisticas`) | `productoEstrella()` (null si no hay ventas), `rankingProductos()` | Producto estrella |
| `ServicioEstadisticas` (`estadisticas`) | `peoresPedidos(n)` | Peores pedidos (menor rendimiento ₡/min) |
| `IMensajeria` (`servicios`) | `solicitarReserva`, `confirmarReserva`, `liberarReserva` | Registro de pedidos con productos de otras sedes (a través del servicio de pedidos de la Persona 2) |
| `ConfigSede` (`config`) | `getCodigo`, `getNombre`, `getPuertoHttp` | Encabezado de la interfaz (qué sede es) y puerto del servidor |

Ejemplos de uso en `notas.md`: Notas 11 (rutas), 16 (entregas), 20 (estadísticas) y 29 (arranque).

---

## Pendientes en grupo

- [ ] **Prueba real en 2 computadoras** siguiendo el `README.md` (sección *Despliegue en 2 computadoras* y *Guion de la demostración*): broker en la Mac con `NODE_IP_ADDRESS` comentado, usuario `convitec`, hotspot.
- [ ] Correr `mvn clean test` **con RabbitMQ encendido** y confirmar 0 pruebas saltadas.
- [ ] **Integración final**: clases reales de la Persona 2 en `Sede`, servidor HTTP de la Persona 1 en `Main`, y borrar el paquete `demo`. Verificar `grep -rn "TODO PERSONA" src` sin resultados.
- [ ] **Documento de diseño**: DI1, DI2, DI3 y DI4.
- [ ] **Diagrama UML** de clases.
- [ ] **Commits**: cada integrante con **al menos 5 commits significativos** en el repositorio.
