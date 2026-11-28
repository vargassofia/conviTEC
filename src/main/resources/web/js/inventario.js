// Pantalla de Inventario: productos de esta sede (con "Agregar unidades") y catálogo global.

import { obtener, enviar } from './api.js';
import {
  ACTUALIZAR_CADA_MS, activarPestanas, colones, escapar, htmlPestanas, mostrarError, notificar, repetirCada,
} from './util.js';

const MAXIMO_AGREGAR = 10000;

/** Quita tildes y pasa a minúsculas, para buscar "cafe" y encontrar "Café". */
function normalizar(texto) {
  return String(texto || '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

function coincide(producto, busqueda) {
  if (!busqueda) {
    return true;
  }
  const b = normalizar(busqueda);
  return normalizar(producto.codigo).includes(b) || normalizar(producto.nombre).includes(b)
    || normalizar(producto.categoria).includes(b);
}

export function montarInventario(contenedor, sede) {
  contenedor.insertAdjacentHTML('beforeend', `
    ${htmlPestanas([
      { id: 'local', texto: `Productos de ${sede.nombre}` },
      { id: 'global', texto: 'Catálogo global' },
    ], 'local')}

    <div class="barra-herramientas">
      <label class="buscador">
        <span class="sr-only">Buscar producto</span>
        <svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>
        <input type="search" id="buscar-producto" placeholder="Buscar por nombre, código o categoría">
      </label>
      <button class="boton" type="button" id="abrir-agregar">
        <svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>
        Agregar unidades
      </button>
    </div>

    <section class="tarjeta formulario-agregar" id="formulario-agregar" hidden aria-labelledby="titulo-agregar">
      <h2 id="titulo-agregar">Agregar unidades al inventario de ${escapar(sede.nombre)}</h2>
      <form id="form-agregar" novalidate>
        <div class="campos-agregar">
          <label>Producto
            <select id="agregar-producto" required><option value="">Elija un producto…</option></select>
          </label>
          <label>Cantidad
            <input type="number" id="agregar-cantidad" min="1" max="${MAXIMO_AGREGAR}" step="1" inputmode="numeric" required>
          </label>
        </div>
        <p class="error-campo" id="error-agregar" role="alert" hidden></p>
        <div class="acciones-formulario">
          <button class="boton" type="submit" id="boton-agregar">Agregar</button>
          <button class="boton boton-secundario" type="button" id="cancelar-agregar">Cancelar</button>
        </div>
        <p class="ayuda">Las otras sedes se enteran automáticamente del stock nuevo.</p>
      </form>
    </section>

    <section class="tarjeta" id="vista-inventario" aria-live="polite"><p class="cargando">Cargando…</p></section>`);

  const $ = (id) => contenedor.querySelector(`#${id}`);
  let pestana = 'local';
  let locales = [];
  let globales = [];
  let activa = true;

  // ---------------------------------------------------------------------------
  // Tablas
  // ---------------------------------------------------------------------------

  function tablaLocal() {
    const busqueda = $('buscar-producto').value.trim();
    const filas = locales.filter((p) => coincide(p, busqueda));
    if (locales.length === 0) {
      return '<div class="vacio-simple">El catálogo todavía no tiene productos.</div>';
    }
    if (filas.length === 0) {
      return `<div class="vacio-simple">Ningún producto coincide con “${escapar(busqueda)}”.</div>`;
    }
    return `
      <table class="tabla">
        <thead><tr>
          <th scope="col">Producto</th><th scope="col">Categoría</th><th scope="col" class="numero">Precio</th>
          <th scope="col" class="numero">Disponible</th><th scope="col" class="numero">Reservado</th><th scope="col"><span class="sr-only">Acciones</span></th>
        </tr></thead>
        <tbody>${filas.map((p) => `
          <tr>
            <td data-titulo="Producto"><strong>${escapar(p.nombre)}</strong> <span class="codigo">${escapar(p.codigo)}</span></td>
            <td data-titulo="Categoría">${escapar(p.categoria)}</td>
            <td data-titulo="Precio" class="numero">${colones(p.precio)}</td>
            <td data-titulo="Disponible" class="numero">${p.disponible === 0
    ? '<span class="chip chip-alerta">Sin unidades</span>' : `<strong>${p.disponible}</strong>`}</td>
            <td data-titulo="Reservado" class="numero" title="Apartadas para pedidos de otras sedes, todavía sin confirmar">${p.reservado}</td>
            <td class="accion-fila"><button class="boton-enlace" type="button" data-agregar="${escapar(p.codigo)}">Agregar</button></td>
          </tr>`).join('')}
        </tbody>
      </table>`;
  }

  function celdaSede(s) {
    const clase = s.esEstaSede ? ' class="numero celda-sede"' : ' class="numero"';
    const valor = s.cantidad === undefined || s.cantidad === null
      ? '<span class="desconocido" title="Esa sede todavía no ha informado su stock">—</span>'
      : String(s.cantidad);
    return `<td data-titulo="${escapar(s.nombreSede)}"${clase}>${valor}</td>`;
  }

  function tablaGlobal() {
    const busqueda = $('buscar-producto').value.trim();
    const filas = globales.filter((p) => coincide(p, busqueda));
    if (globales.length === 0) {
      return '<div class="vacio-simple">El catálogo todavía no tiene productos.</div>';
    }
    if (filas.length === 0) {
      return `<div class="vacio-simple">Ningún producto coincide con “${escapar(busqueda)}”.</div>`;
    }
    const sedes = filas[0].sedes;
    return `
      <p class="ayuda">Unidades disponibles en cada sede. <span class="desconocido">—</span> significa que esa sede todavía no ha informado su stock.</p>
      <table class="tabla">
        <thead><tr>
          <th scope="col">Producto</th><th scope="col" class="numero">Precio</th>
          ${sedes.map((s) => `<th scope="col" class="numero${s.esEstaSede ? ' celda-sede' : ''}">${escapar(s.nombreSede)}${s.esEstaSede ? ' (esta sede)' : ''}</th>`).join('')}
          <th scope="col" class="numero">Total</th>
        </tr></thead>
        <tbody>${filas.map((p) => `
          <tr>
            <td data-titulo="Producto"><strong>${escapar(p.nombre)}</strong> <span class="codigo">${escapar(p.codigo)}</span>
              <span class="descripcion">${escapar(p.categoria)}</span></td>
            <td data-titulo="Precio" class="numero">${colones(p.precio)}</td>
            ${p.sedes.map(celdaSede).join('')}
            <td data-titulo="Total" class="numero"><strong>${p.totalConocido}</strong></td>
          </tr>`).join('')}
        </tbody>
      </table>`;
  }

  function pintar() {
    $('vista-inventario').innerHTML = pestana === 'local' ? tablaLocal() : tablaGlobal();
  }

  /**
   * Trae el inventario y el catálogo. En la actualización automática (`silenciosa`) solo vuelve a dibujar
   * si algo cambió, y si falla deja a la vista los últimos datos en lugar de mostrar un error.
   */
  async function cargar(silenciosa = false) {
    try {
      const opciones = { automatica: silenciosa };
      const [l, g] = await Promise.all([obtener('/api/inventario', opciones), obtener('/api/catalogo', opciones)]);
      if (!activa) {
        return;
      }
      if (silenciosa && JSON.stringify([l, g]) === JSON.stringify([locales, globales])) {
        return;
      }
      locales = l;
      globales = g;
      const select = $('agregar-producto');
      const elegido = select.value;
      select.innerHTML = '<option value="">Elija un producto…</option>' + locales.map((p) => `
        <option value="${escapar(p.codigo)}">${escapar(p.nombre)} (${escapar(p.codigo)}) · hay ${p.disponible}</option>`).join('');
      select.value = elegido;
      pintar();
    } catch (error) {
      if (activa && !silenciosa) {
        mostrarError($('vista-inventario'), error.message, () => cargar());
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Agregar unidades
  // ---------------------------------------------------------------------------

  function abrirFormulario(codigo) {
    $('formulario-agregar').hidden = false;
    $('error-agregar').hidden = true;
    if (codigo) {
      $('agregar-producto').value = codigo;
    }
    (codigo ? $('agregar-cantidad') : $('agregar-producto')).focus();
  }

  function cerrarFormulario() {
    $('formulario-agregar').hidden = true;
    $('form-agregar').reset();
  }

  function mostrarErrorCampo(texto) {
    $('error-agregar').textContent = texto;
    $('error-agregar').hidden = false;
  }

  $('abrir-agregar').addEventListener('click', () => abrirFormulario(null));
  $('cancelar-agregar').addEventListener('click', cerrarFormulario);
  $('vista-inventario').addEventListener('click', (evento) => {
    const boton = evento.target.closest('[data-agregar]');
    if (boton) {
      abrirFormulario(boton.dataset.agregar);
      $('formulario-agregar').scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }
  });

  $('form-agregar').addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const codigo = $('agregar-producto').value;
    const texto = $('agregar-cantidad').value.trim();
    const cantidad = Number(texto);
    if (!codigo) {
      mostrarErrorCampo('Elija el producto.');
      return;
    }
    if (!texto || !Number.isInteger(cantidad) || cantidad <= 0) {
      mostrarErrorCampo('Escriba una cantidad entera mayor que 0.');
      return;
    }
    if (cantidad > MAXIMO_AGREGAR) {
      mostrarErrorCampo(`Se pueden agregar como máximo ${MAXIMO_AGREGAR} unidades a la vez.`);
      return;
    }
    const boton = $('boton-agregar');
    boton.disabled = true;
    boton.textContent = 'Agregando…';
    try {
      const r = await enviar('/api/inventario/agregar', { codigoProducto: codigo, cantidad });
      const producto = locales.find((p) => p.codigo === r.codigoProducto);
      const nombre = producto ? producto.nombre : r.codigoProducto;
      if (r.aviso) {
        notificar(r.aviso, 'alerta');
      } else {
        notificar(`Se agregaron ${cantidad} unidades de ${nombre}. Ahora hay ${r.disponible}.`);
      }
      cerrarFormulario();
      await cargar();
    } catch (error) {
      mostrarErrorCampo(error.message);
    } finally {
      boton.disabled = false;
      boton.textContent = 'Agregar';
    }
  });

  // ---------------------------------------------------------------------------
  // Pestañas y búsqueda
  // ---------------------------------------------------------------------------

  activarPestanas(contenedor, (id) => {
    pestana = id;
    pintar();
  });
  $('buscar-producto').addEventListener('input', pintar);

  cargar();
  // El stock de las otras sedes llega por mensajería: se actualiza solo, sin recargar la página.
  const detener = repetirCada(ACTUALIZAR_CADA_MS, () => cargar(true));
  return () => {
    activa = false;
    detener();
  };
}
