// Estructura general de la interfaz: encabezado de la sede, menú y navegación entre secciones.
// Cada sección se muestra en <main id="contenido"> según el #hash de la dirección (#inicio, #entregas...).

import { enviar, obtener, tiempoDesdeUltimaPeticion } from './api.js';
import { montarAcceso } from './acceso.js';
import { montarCuentas } from './cuentas.js';
import { montarEntregas } from './entregas.js';
import { montarEstadisticas } from './estadisticas.js';
import { montarInventario } from './inventario.js';
import { montarNuevoPedido } from './nuevo-pedido.js';
import { montarPedidos } from './pedidos.js';
import {
  ACTUALIZAR_CADA_MS, colones, escapar, mostrarError, notificar, repetirCada, textoSalida,
} from './util.js';

const ICONOS = {
  inicio: '<path d="M3 11 12 4l9 7"/><path d="M5 10v10h14V10"/><path d="M10 20v-6h4v6"/>',
  'nuevo-pedido': '<path d="M6 7h12l-1 13H7z"/><path d="M9 7a3 3 0 0 1 6 0"/><path d="M12 11v6M9 14h6"/>',
  pedidos: '<path d="M9 6h11M9 12h11M9 18h11"/><path d="M4 6h.01M4 12h.01M4 18h.01"/>',
  entregas: '<path d="M2 6h12v10H2z"/><path d="M14 10h4l3 3v3h-7"/><circle cx="6" cy="18" r="2"/><circle cx="17" cy="18" r="2"/>',
  inventario: '<path d="M3 7l9-4 9 4-9 4z"/><path d="M3 7v10l9 4 9-4V7"/><path d="M12 11v10"/>',
  estadisticas: '<path d="M4 20V11M10 20V5M16 20v-6M2 20h20"/>',
  cuentas: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 4.5a3.5 3.5 0 0 1 0 7M18 14a6 6 0 0 1 3.5 6"/>',
};

// Secciones del menú, en orden. "montar" dibuja la sección y devuelve (si hace falta) una función de limpieza.
const SECCIONES = [
  {
    id: 'inicio',
    titulo: 'Inicio',
    descripcion: 'Resumen de la sede y accesos rápidos.',
    montar: montarInicio,
  },
  {
    id: 'nuevo-pedido',
    titulo: 'Nuevo pedido',
    descripcion: 'Registre un pedido con productos de esta u otras sedes.',
    montar: montarNuevoPedido,
  },
  {
    id: 'pedidos',
    titulo: 'Pedidos',
    descripcion: 'Pedidos por entregar e historial de pedidos.',
    montar: montarPedidos,
  },
  {
    id: 'entregas',
    titulo: 'Entregas',
    descripcion: 'Recorrido del vehículo en el mapa, paso a paso.',
    montar: montarEntregas,
  },
  {
    id: 'inventario',
    titulo: 'Inventario',
    descripcion: 'Productos de esta sede y catálogo global.',
    montar: montarInventario,
  },
  {
    id: 'estadisticas',
    titulo: 'Estadísticas',
    descripcion: 'Historial de ventas, producto estrella y peores pedidos.',
    montar: montarEstadisticas,
  },
  {
    id: 'cuentas',
    titulo: 'Cuentas',
    descripcion: 'Personas que pueden entrar a esta sede.',
    montar: montarCuentas,
  },
];

const SECCION_INICIAL = 'inicio';

let sede = null; // datos de /api/sede
let usuario = null; // { usuario, nombre } de la sesión
let minutosInactividad = 30;
let pantallaAccesoVisible = false;
let desmontarSeccion = null; // limpia la sección anterior (por ejemplo, destruye el mapa)

// ---------------------------------------------------------------------------
// Utilidades
// ---------------------------------------------------------------------------

function icono(id) {
  return `<svg class="icono" viewBox="0 0 24 24" aria-hidden="true">${ICONOS[id] || ''}</svg>`;
}

function mostrarAvisoConexion(mensaje) {
  const aviso = document.getElementById('aviso-conexion');
  aviso.textContent = mensaje;
  aviso.hidden = !mensaje;
}

// ---------------------------------------------------------------------------
// Encabezado y menú
// ---------------------------------------------------------------------------

function pintarEncabezado() {
  document.documentElement.dataset.sede = sede.codigo;
  document.title = `ConviTEC · ${sede.nombre}`;
  document.getElementById('sede-codigo').textContent = sede.codigo;
  document.getElementById('sede-nombre').textContent = sede.nombre;

  const salida = document.getElementById('proxima-salida');
  document.getElementById('proxima-hora').textContent = textoSalida(sede.proximaSalida);
  salida.title = `Próxima salida del vehículo: ${textoSalida(sede.proximaSalida)}`;
  salida.hidden = false;
}

function pintarMenu() {
  const menu = document.getElementById('menu');
  menu.innerHTML = SECCIONES.map((s) => `
    <a href="#${s.id}" data-seccion="${s.id}">
      ${icono(s.id)}
      <span>${escapar(s.titulo)}</span>
    </a>`).join('');

  const boton = document.getElementById('boton-menu');
  boton.addEventListener('click', () => {
    const abierto = menu.classList.toggle('abierto');
    boton.setAttribute('aria-expanded', String(abierto));
  });
  // En pantallas pequeñas, el menú se cierra al elegir una sección
  menu.addEventListener('click', (evento) => {
    if (evento.target.closest('a')) {
      menu.classList.remove('abierto');
      boton.setAttribute('aria-expanded', 'false');
    }
  });
}

function marcarSeccionActiva(id) {
  document.querySelectorAll('#menu a').forEach((enlace) => {
    if (enlace.dataset.seccion === id) {
      enlace.setAttribute('aria-current', 'page');
    } else {
      enlace.removeAttribute('aria-current');
    }
  });
}

// ---------------------------------------------------------------------------
// Secciones
// ---------------------------------------------------------------------------

function encabezadoSeccion(seccion) {
  return `
    <header class="titulo-seccion">
      <h1>${escapar(seccion.titulo)}</h1>
      <p>${escapar(seccion.descripcion)}</p>
    </header>`;
}

function montarInicio(contenedor) {
  const accesos = SECCIONES.filter((s) => s.id !== 'inicio').map((s) => `
    <a class="acceso" href="#${s.id}">
      <span class="acceso-icono">${icono(s.id)}</span>
      <span class="acceso-texto">
        <strong>${escapar(s.titulo)}</strong>
        <span>${escapar(s.descripcion)}</span>
      </span>
    </a>`).join('');

  contenedor.insertAdjacentHTML('beforeend', `
    <section class="bienvenida tarjeta">
      <div>
        <p class="bienvenida-etiqueta">Hola, ${escapar(usuario.nombre)}. Está trabajando en</p>
        <h2>ConviTEC ${escapar(sede.nombre)}</h2>
        <p>Desde aquí puede registrar pedidos, revisar el inventario, planificar las entregas y consultar las ventas de esta sede.</p>
      </div>
      <dl class="datos-sede">
        <div><dt>Código de sede</dt><dd>${escapar(sede.codigo)}</dd></div>
        <div><dt>Salidas del vehículo</dt><dd>${sede.salidas.map(escapar).join(' y ')}</dd></div>
        <div><dt>Próxima salida</dt><dd>${escapar(textoSalida(sede.proximaSalida))}</dd></div>
      </dl>
    </section>
    <h2 class="subtitulo">Hoy en la sede</h2>
    <div class="tarjetas-resumen" id="resumen-hoy"><p class="cargando">Cargando…</p></div>
    <h2 class="subtitulo">¿Qué desea hacer?</h2>
    <nav class="accesos" aria-label="Accesos rápidos">${accesos}</nav>`);

  let activa = true;
  let ultimo = '';

  /** En la actualización automática (`silenciosa`) solo redibuja si cambió y no muestra errores. */
  async function cargarResumen(silenciosa = false) {
    const caja = contenedor.querySelector('#resumen-hoy');
    let r;
    try {
      r = await obtener('/api/resumen', { automatica: silenciosa });
    } catch (error) {
      if (activa && !silenciosa) {
        mostrarError(caja, error.message, () => cargarResumen());
      }
      return;
    }
    const texto = JSON.stringify(r);
    if (!activa || (silenciosa && texto === ultimo)) {
      return;
    }
    ultimo = texto;
    caja.innerHTML = `
      <a class="tarjeta dato-resumen dato-enlace" href="#pedidos">
        <span class="dato-etiqueta">Pedidos por entregar</span>
        <strong class="dato-valor">${r.pedidosPendientes}</strong>
        <span class="dato-detalle">${r.pedidosPendientes === 0 ? 'Nada pendiente' : 'Salen en la próxima salida'}</span>
      </a>
      <div class="tarjeta dato-resumen">
        <span class="dato-etiqueta">Pedidos registrados hoy</span>
        <strong class="dato-valor">${r.pedidosHoy}</strong>
        <span class="dato-detalle">Aceptados y rechazados</span>
      </div>
      <a class="tarjeta dato-resumen dato-enlace" href="#estadisticas">
        <span class="dato-etiqueta">Ventas de hoy</span>
        <strong class="dato-valor">${colones(r.montoHoy)}</strong>
        <span class="dato-detalle">${r.ventasHoy === 1 ? '1 pedido entregado' : `${r.ventasHoy} pedidos entregados`}</span>
      </a>
      <a class="tarjeta dato-resumen dato-estrella dato-enlace" href="#estadisticas">
        <span class="dato-etiqueta">★ Producto estrella</span>
        <strong class="dato-valor">${r.productoEstrella ? escapar(r.productoEstrella.nombre) : '—'}</strong>
        <span class="dato-detalle">${r.productoEstrella ? `${colones(r.productoEstrella.monto)} en ventas` : 'Todavía no hay ventas'}</span>
      </a>`;
  }

  cargarResumen();
  const detener = repetirCada(ACTUALIZAR_CADA_MS, () => cargarResumen(true));
  return () => {
    activa = false;
    detener();
  };
}

function mostrarSeccion() {
  const id = location.hash.replace('#', '') || SECCION_INICIAL;
  const seccion = SECCIONES.find((s) => s.id === id);
  if (!seccion) {
    location.hash = SECCION_INICIAL;
    return;
  }
  if (desmontarSeccion) {
    desmontarSeccion();
    desmontarSeccion = null;
  }
  const contenido = document.getElementById('contenido');
  contenido.innerHTML = encabezadoSeccion(seccion);
  desmontarSeccion = seccion.montar(contenido, sede) || null;
  marcarSeccionActiva(seccion.id);
  contenido.focus({ preventScroll: true });
  window.scrollTo(0, 0);
}

// ---------------------------------------------------------------------------
// Arranque
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Sesión
// ---------------------------------------------------------------------------

/** Cada cuánto, como máximo, la actividad de la persona renueva la sesión en el servidor. */
const RENOVAR_SESION_MS = 60 * 1000;
/** Aviso antes de que la sesión se cierre por inactividad. */
const AVISO_CIERRE_MS = 2 * 60 * 1000;

/** Oculta la aplicación y muestra la pantalla de inicio de sesión. */
function mostrarPantallaAcceso(estado, aviso = '') {
  pantallaAccesoVisible = true;
  if (desmontarSeccion) {
    desmontarSeccion();
    desmontarSeccion = null;
  }
  document.querySelector('.encabezado').hidden = true;
  document.querySelector('.estructura').hidden = true;
  mostrarAvisoConexion('');
  const pantalla = document.getElementById('pantalla-acceso');
  pantalla.hidden = false;
  // Al entrar se recarga la página: así todo arranca limpio y se vuelve a la sección que estaba abierta
  montarAcceso(pantalla, estado, { aviso, alEntrar: () => window.location.reload() });
}

/** La sesión terminó (por inactividad, porque se cerró en otra pantalla o porque desactivaron la cuenta). */
async function sesionTerminada(aviso) {
  if (pantallaAccesoVisible) {
    return;
  }
  pantallaAccesoVisible = true;
  try {
    mostrarPantallaAcceso(await obtener('/api/acceso'), aviso);
  } catch {
    window.location.reload();
  }
}

function pintarUsuario() {
  document.getElementById('usuario-nombre').textContent = usuario.nombre;
  document.getElementById('usuario-actual').hidden = false;
  document.getElementById('boton-salir').addEventListener('click', async () => {
    try {
      await enviar('/api/acceso/salir', {});
    } catch {
      // Aunque falle, se recarga: si la sesión seguía abierta, vence sola
    }
    window.location.reload();
  });
}

/**
 * Cierra la sesión en esta pantalla tras `minutosInactividad` minutos sin tocar la página,
 * avisando 2 minutos antes. Mientras la persona la usa, renueva la sesión en el servidor
 * (como máximo una vez por minuto), aunque no haga ninguna petición.
 */
function vigilarInactividad() {
  let ultimaActividad = Date.now();
  let avisado = false;
  const limite = minutosInactividad * 60 * 1000;

  function actividad() {
    ultimaActividad = Date.now();
    avisado = false;
    if (tiempoDesdeUltimaPeticion() > RENOVAR_SESION_MS) {
      obtener('/api/acceso').then((estado) => {
        if (!estado.sesion) {
          sesionTerminada('Su sesión terminó. Vuelva a iniciar sesión para continuar.');
        }
      }).catch(() => { /* sin conexión: ya lo avisan las pantallas */ });
    }
  }
  ['pointerdown', 'keydown'].forEach((tipo) => document.addEventListener(tipo, actividad, { passive: true }));

  setInterval(() => {
    if (pantallaAccesoVisible) {
      return;
    }
    const inactivo = Date.now() - ultimaActividad;
    if (inactivo >= limite) {
      enviar('/api/acceso/salir', {}).catch(() => {});
      sesionTerminada(`Su sesión se cerró después de ${minutosInactividad} minutos sin uso. Vuelva a entrar para continuar.`);
    } else if (!avisado && inactivo >= limite - AVISO_CIERRE_MS) {
      avisado = true;
      notificar('Su sesión se cerrará en 2 minutos por falta de uso. Toque cualquier parte de la página para seguir.',
        'alerta');
    }
  }, 15 * 1000);
}

// ---------------------------------------------------------------------------
// Arranque
// ---------------------------------------------------------------------------

function mostrarErrorArranque(error) {
  document.getElementById('sede-nombre').textContent = 'Sin conexión';
  document.getElementById('contenido').innerHTML = `
    <section class="vacio tarjeta">
      <h1>No se pudo cargar la sede</h1>
      <p>${escapar(error.message)}</p>
      <button class="boton" type="button" onclick="location.reload()">Intentar de nuevo</button>
    </section>`;
  mostrarAvisoConexion(error.message);
}

async function iniciar() {
  let estado;
  try {
    estado = await obtener('/api/acceso');
  } catch (error) {
    mostrarErrorArranque(error);
    return;
  }
  if (!estado.sesion) {
    mostrarPantallaAcceso(estado);
    return;
  }
  usuario = estado.sesion;
  minutosInactividad = estado.minutosInactividad || minutosInactividad;
  try {
    sede = await obtener('/api/sede');
  } catch (error) {
    if (!pantallaAccesoVisible) {
      mostrarErrorArranque(error);
    }
    return;
  }
  window.addEventListener('convitec:sin-sesion', () => {
    sesionTerminada('Su sesión terminó. Vuelva a iniciar sesión para continuar.');
  });
  pintarMenu();
  pintarEncabezado();
  pintarUsuario();
  vigilarInactividad();
  window.addEventListener('hashchange', mostrarSeccion);
  mostrarSeccion();
}

iniciar();
