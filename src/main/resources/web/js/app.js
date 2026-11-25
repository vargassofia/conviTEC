// Estructura general de la interfaz: encabezado de la sede, menú y navegación entre secciones.
// Cada sección se muestra en <main id="contenido"> según el #hash de la dirección (#inicio, #entregas...).

import { obtener } from './api.js';

const ICONOS = {
  inicio: '<path d="M3 11 12 4l9 7"/><path d="M5 10v10h14V10"/><path d="M10 20v-6h4v6"/>',
  'nuevo-pedido': '<path d="M6 7h12l-1 13H7z"/><path d="M9 7a3 3 0 0 1 6 0"/><path d="M12 11v6M9 14h6"/>',
  pedidos: '<path d="M9 6h11M9 12h11M9 18h11"/><path d="M4 6h.01M4 12h.01M4 18h.01"/>',
  entregas: '<path d="M2 6h12v10H2z"/><path d="M14 10h4l3 3v3h-7"/><circle cx="6" cy="18" r="2"/><circle cx="17" cy="18" r="2"/>',
  inventario: '<path d="M3 7l9-4 9 4-9 4z"/><path d="M3 7v10l9 4 9-4V7"/><path d="M12 11v10"/>',
  estadisticas: '<path d="M4 20V11M10 20V5M16 20v-6M2 20h20"/>',
};

// Secciones del menú, en orden. "pronto" describe lo que tendrá mientras se construye.
const SECCIONES = [
  {
    id: 'inicio',
    titulo: 'Inicio',
    descripcion: 'Resumen de la sede y accesos rápidos.',
  },
  {
    id: 'nuevo-pedido',
    titulo: 'Nuevo pedido',
    descripcion: 'Registre un pedido con productos de esta u otras sedes.',
    pronto: 'Aquí podrá elegir productos del catálogo global, indicar las cantidades y el lugar de entrega, '
      + 'y el sistema le dirá si el pedido se acepta o por qué se rechaza.',
  },
  {
    id: 'pedidos',
    titulo: 'Pedidos',
    descripcion: 'Pedidos pendientes de entrega e historial de pedidos.',
    pronto: 'Aquí verá los pedidos que faltan por entregar y todos los pedidos registrados en esta sede.',
  },
  {
    id: 'entregas',
    titulo: 'Entregas',
    descripcion: 'Recorrido del vehículo en el mapa, paso a paso.',
    pronto: 'Aquí verá el mapa con las rutas de entrega y el botón “Calcular siguiente punto de entrega”.',
  },
  {
    id: 'inventario',
    titulo: 'Inventario',
    descripcion: 'Productos de esta sede y catálogo global.',
    pronto: 'Aquí podrá consultar los productos de esta sede, agregar unidades y ver la disponibilidad en todas las sedes.',
  },
  {
    id: 'estadisticas',
    titulo: 'Estadísticas',
    descripcion: 'Historial de ventas, producto estrella y peores pedidos.',
    pronto: 'Aquí verá el historial de ventas, el producto que más ingresos genera y los pedidos con menor rendimiento.',
  },
];

const SECCION_INICIAL = 'inicio';

let sede = null; // datos de /api/sede

// ---------------------------------------------------------------------------
// Utilidades
// ---------------------------------------------------------------------------

/** Escapa texto antes de ponerlo en HTML. */
export function escapar(texto) {
  return String(texto ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function icono(id) {
  return `<svg class="icono" viewBox="0 0 24 24" aria-hidden="true">${ICONOS[id] || ''}</svg>`;
}

function textoSalida(salida) {
  if (!salida) {
    return '';
  }
  return salida.diaSiguiente ? `${salida.hora} (mañana)` : `${salida.hora} (hoy)`;
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

function vistaInicio(seccion) {
  const accesos = SECCIONES.filter((s) => s.id !== 'inicio').map((s) => `
    <a class="acceso" href="#${s.id}">
      <span class="acceso-icono">${icono(s.id)}</span>
      <span class="acceso-texto">
        <strong>${escapar(s.titulo)}</strong>
        <span>${escapar(s.descripcion)}</span>
      </span>
    </a>`).join('');

  return `
    ${encabezadoSeccion(seccion)}
    <section class="bienvenida tarjeta">
      <div>
        <p class="bienvenida-etiqueta">Bienvenido a</p>
        <h2>ConviTEC ${escapar(sede.nombre)}</h2>
        <p>Desde aquí puede registrar pedidos, revisar el inventario, planificar las entregas y consultar las ventas de esta sede.</p>
      </div>
      <dl class="datos-sede">
        <div><dt>Código de sede</dt><dd>${escapar(sede.codigo)}</dd></div>
        <div><dt>Salidas del vehículo</dt><dd>${sede.salidas.map(escapar).join(' y ')}</dd></div>
        <div><dt>Próxima salida</dt><dd>${escapar(textoSalida(sede.proximaSalida))}</dd></div>
      </dl>
    </section>
    <h2 class="subtitulo">¿Qué desea hacer?</h2>
    <nav class="accesos" aria-label="Accesos rápidos">${accesos}</nav>`;
}

function vistaEnConstruccion(seccion) {
  return `
    ${encabezadoSeccion(seccion)}
    <section class="vacio tarjeta">
      <span class="vacio-icono">${icono(seccion.id)}</span>
      <h2>Esta sección se está preparando</h2>
      <p>${escapar(seccion.pronto)}</p>
      <a class="boton boton-secundario" href="#inicio">Volver al inicio</a>
    </section>`;
}

function mostrarSeccion() {
  const id = location.hash.replace('#', '') || SECCION_INICIAL;
  const seccion = SECCIONES.find((s) => s.id === id);
  if (!seccion) {
    location.hash = SECCION_INICIAL;
    return;
  }
  const contenido = document.getElementById('contenido');
  contenido.innerHTML = seccion.id === 'inicio' ? vistaInicio(seccion) : vistaEnConstruccion(seccion);
  marcarSeccionActiva(seccion.id);
  contenido.focus({ preventScroll: true });
  window.scrollTo(0, 0);
}

// ---------------------------------------------------------------------------
// Arranque
// ---------------------------------------------------------------------------

async function iniciar() {
  pintarMenu();
  try {
    sede = await obtener('/api/sede');
  } catch (error) {
    document.getElementById('sede-nombre').textContent = 'Sin conexión';
    document.getElementById('contenido').innerHTML = `
      <section class="vacio tarjeta">
        <h1>No se pudo cargar la sede</h1>
        <p>${escapar(error.message)}</p>
        <button class="boton" type="button" onclick="location.reload()">Intentar de nuevo</button>
      </section>`;
    mostrarAvisoConexion(error.message);
    return;
  }
  pintarEncabezado();
  window.addEventListener('hashchange', mostrarSeccion);
  mostrarSeccion();
}

iniciar();
