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
| Registro de entregas como ventas | `entregas` | Persona 3 | ✅ Terminado; falta conectarlo al gestor real desde `Sede`/`Main` |
| Estadísticas (historial, producto estrella, peores pedidos) + MergeSort propio | `estadisticas` | Persona 3 | ✅ Terminado |
| Mensajería RabbitMQ (reservar, confirmar, liberar, actualización de stock) | `mensajeria` | Persona 3 | ✅ Terminado |
| Configuración por sede (`.properties`, `CONVITEC_RABBIT_CLAVE`) | `config` | Persona 3 | ✅ Terminado |
| Arranque de la sede, consola de prueba, jar ejecutable | `app` | Persona 3 | ✅ Terminado |
| Contratos entre módulos | `servicios` | Persona 3 | ✅ Terminado |
| Inventario local, catálogo global y fachada | `catalogo` | Persona 2 | ✅ Implementado y probado; `Sede` todavía arranca con demos |
| Producto, Existencia y modelos de pedido | `modelo` | Persona 2 | ✅ Implementado y probado |
| Servicio de pedidos (reservas, rutas, historial y pendientes enlazados) | `pedidos` | Persona 2 | ✅ Implementado y probado; falta conectarlo a la sede/API |
| Árbol AVL propio | `catalogo`, `estructuras` | Persona 2 | ✅ Implementado y probado |
| Persistencia en archivos para inventario, pedidos y ventas | `catalogo`, `pedidos`, `entregas` | Persona 2 | ⏳ Pendiente; exportar/cargar de inventario y pedidos no guarda archivos |
| API REST e interfaz web | `web`, `app` | Persona 1 | 🟡 Parcial: mapa, rutas y entregas; faltan pantallas/endpoints de negocio y conectar pedidos reales |
| Prueba real en 2 computadoras, integración, documento de diseño, UML | — | Grupo | ⏳ Pendiente |

Pruebas actuales: **260** (`mvn clean test`). Las pruebas de integración de RabbitMQ se omiten si el broker no está corriendo.

---

## Persona 2 — inventario, catálogo, pedidos y persistencia

### ✅ Implementado

| Componente | Estado y uso |
|---|---|
| `catalogo.InventarioLocal` / `catalogo.CatalogoGlobal` | Implementan `IInventarioLocal` y `ICatalogoGlobal`; sus métodos de estado son sincronizados. Confirmar/liberar reservas son idempotentes. |
| `catalogo.ServicioInventario` | Fachada para consultas, agregados y descuentos; publica los cambios locales y reporta errores de publicación con `ServicioInventarioException`. |
| `modelo.Producto`, `Existencia`, `Pedido`, `DetallePedido`, `EstadoPedido` | Implementados con validaciones, copias defensivas y estados de pedido. |
| `pedidos.ServicioPedidos` | Implementa `IGestorPedidos`; coordina reservas local/remotas, ruta, confirmación/liberación, historial, pendientes, exportación/carga y el contador de ids. |
| `catalogo.AVLTree` / `estructuras.ArbolAVL` | Árbol AVL implementado; las existencias y el catálogo usan árboles AVL. |
| Pruebas | `InventarioLocalTest`, `CatalogoGlobalTest`, `ServicioInventarioTest` y `ServicioPedidosTest`; dobles manuales para mensajería y rutas. |

### ⏳ Pendiente: persistencia y conexión de producción

- Implementar almacenamiento duradero en archivos locales para inventario, pedidos y ventas bajo `config.getCarpetaDatos()` (`datos/LIB`, `datos/ALA`, etc.). Los métodos `exportar...`/`cargar...` de inventario y pedidos solo proporcionan datos serializables: no leen ni escriben archivos.
- Reemplazar `entregas/RepositorioVentasMemoria` por un `IRepositorioVentas` persistente. Ninguna sede debe leer los archivos de otra.
- Conectar las instancias reales en `app/Sede.java`; ahora mismo `Sede.conDemostracion` todavía crea `InventarioDemo` y `CatalogoGlobalDemo`. Agregar las referencias a `ServicioInventario` y `ServicioPedidos`, y crear `ServicioEntregas` con el gestor real.
- Sustituir `PendientesDemo` en `app/Main.java` por un adaptador `FuentePendientes` que use los pedidos reales; luego validar la integración con `ServicioEntregas`.
- Mantener restricciones del proyecto: arreglos y estructuras propias, sin colecciones ni ordenamientos de librería.

---

## Persona 1 — API REST e interfaz web

### ✅ Implementado parcialmente

Existe el servidor HTTP (`web.ServidorWeb`), páginas estáticas e interfaz de mapa, endpoints de sede, grafo, rutas y recorrido de entregas. `app.Main` ya inicia y detiene el servidor.

### ⏳ Pendiente

- Integrar con Persona 2 y agregar endpoints/pantallas para catálogo e inventario local, registrar pedidos, consultar historial/estado y reportar aceptación o rechazo. Por ahora Main entrega `PendientesDemo` al servidor.
- Actualizar `Sede` para exponer servicios reales de inventario/pedidos y conectarlos a `ServidorWeb`; actualmente están disponibles las rutas, mensajería y catálogo/inventario de demostración.
- Mantener y ampliar las pruebas HTTP en `src/test/java/.../web`; probar errores de validación y estados, no solo respuestas exitosas.

### Servicios existentes que puede integrar

Cuando `Sede` exponga las dependencias reales, usar los servicios siguientes en vez de acceder a datos de otra sede:

| Servicio | Métodos principales | Pantalla / función |
|---|---|---|
| `ServicioRutas` (`rutas`) | `existeRuta`, `caminoMasCorto` | Consulta de ruta y tiempo entre ubicaciones |
| `ServicioRutas` (`rutas`) | `iniciarRecorrido`, `hayEntregasPendientes`, `calcularSiguienteEntrega`, `obtenerRecorrido` | Botón **"Calcular siguiente punto de entrega"** (paso a paso) |
| `ServicioRutas` (`rutas`) | `planificarRecorridoCompleto` | Ver el recorrido completo de una salida (11:00 / 15:00) |
| `ServicioEntregas` (`entregas`) | `registrarParada`, `registrarRecorrido` | Registrar entregas como ventas; pendiente de cablear el gestor real y repositorio persistente |
| `ServicioInventario` (`catalogo`) | consultas, `agregarUnidades`, `descontar` | Inventario propio de la sede; publica cambios de stock |
| `ServicioPedidos` (`pedidos`) | `registrarPedido`, consultas, `construirSolicitudesEntrega` | Registrar y consultar pedidos; no llamar directamente a la mensajería para orquestar reservas |
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
- [ ] **Integración final**: conectar servicios reales de Persona 2 en `Sede`, sustituir `PendientesDemo` en `Main` y conectar API/UI de Persona 1; retirar demos solo después de comprobar que ninguna prueba o arranque depende de ellas.
- [ ] **Persistencia**: probar reinicio de una sede y recuperación de inventario, reservas, pedidos, consecutivo y ventas desde archivos propios.
- [ ] **Documento de diseño**: completar los entregables DI1, DI2 y DI4; revisar/actualizar DI3 para reflejar los módulos reales de Persona 2 y el estado de la API.
- [ ] **Diagrama UML**: actualizarlo para incluir catálogo, inventario, servicio de pedidos, persistencia y API.
- [ ] **Commits**: cada integrante con **al menos 5 commits significativos** en el repositorio.
