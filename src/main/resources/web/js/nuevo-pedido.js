// Pantalla Nuevo pedido: asistente de 3 pasos (productos → lugar de entrega → confirmar).
// El pedido lo registra el servidor (ServicioPedidos): reserva en esta y otras sedes, valida la ruta
// y responde si se aceptó o por qué se rechazó.

import { obtener, enviar } from './api.js';
import {
  colones, escapar, etiquetaEstado, mostrarError, textoCamino, textoMinutos, textoSalida,
} from './util.js';

const MAXIMO_POR_PRODUCTO = 1000;

function normalizar(texto) {
  return String(texto || '').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

export function montarNuevoPedido(contenedor, sede) {
  contenedor.insertAdjacentHTML('beforeend', `
    <ol class="pasos" aria-label="Pasos del pedido">
      <li data-paso="1" aria-current="step"><span>1</span> Productos</li>
      <li data-paso="2"><span>2</span> Lugar de entrega</li>
      <li data-paso="3"><span>3</span> Confirmar</li>
    </ol>
    <div id="paso-contenido"><p class="cargando">Cargando catálogo…</p></div>`);

  const $ = (id) => contenedor.querySelector(`#${id}`);
  const contenido = $('paso-contenido');

  let catalogo = [];        // de /api/catalogo
  let ubicaciones = [];     // de /api/grafo
  const carrito = {};       // código -> cantidad
  let destino = '';
  let paso = 1;
  let activa = true;

  // ---------------------------------------------------------------------------
  // Utilidades
  // ---------------------------------------------------------------------------

  function lineas() {
    return catalogo.filter((p) => carrito[p.codigo] > 0)
      .map((p) => ({ producto: p, cantidad: carrito[p.codigo] }));
  }

  function total() {
    return lineas().reduce((suma, l) => suma + l.cantidad * l.producto.precio, 0);
  }

  function disponibilidad(p) {
    let aqui = 0;
    let otras = 0;
    let desconocidas = 0;
    p.sedes.forEach((s) => {
      if (s.esEstaSede) {
        aqui = s.cantidad || 0;
      } else if (s.cantidad === undefined || s.cantidad === null) {
        desconocidas++;
      } else {
        otras += s.cantidad;
      }
    });
    return { aqui, otras, desconocidas };
  }

  function irAPaso(n) {
    paso = n;
    contenedor.querySelectorAll('.pasos li').forEach((li) => {
      const numero = Number(li.dataset.paso);
      li.classList.toggle('hecho', numero < n);
      if (numero === n) {
        li.setAttribute('aria-current', 'step');
      } else {
        li.removeAttribute('aria-current');
      }
    });
    if (n === 1) {
      pintarProductos();
    } else if (n === 2) {
      pintarDestino();
    } else {
      pintarConfirmacion();
    }
    window.scrollTo(0, 0);
  }

  function resumenCarrito() {
    const ls = lineas();
    return `
      <aside class="tarjeta carrito" aria-label="Resumen del pedido">
        <h2>Su pedido</h2>
        ${ls.length === 0 ? '<p class="ayuda">Todavía no ha elegido productos.</p>' : `
          <ul class="lista-carrito">${ls.map((l) => `
            <li><span>${l.cantidad} × ${escapar(l.producto.nombre)}</span><strong>${colones(l.cantidad * l.producto.precio)}</strong></li>`).join('')}
          </ul>
          <p class="total-carrito"><span>Total</span><strong>${colones(total())}</strong></p>`}
        <button class="boton boton-ancho" type="button" id="ir-destino" ${ls.length === 0 ? 'disabled' : ''}>Siguiente: lugar de entrega</button>
      </aside>`;
  }

  // ---------------------------------------------------------------------------
  // Paso 1: productos
  // ---------------------------------------------------------------------------

  function tarjetaProducto(p) {
    const d = disponibilidad(p);
    const cantidad = carrito[p.codigo] || 0;
    let texto;
    if (d.aqui === 0 && d.otras === 0) {
      texto = '<span class="chip chip-alerta">Sin stock conocido</span>';
    } else {
      texto = `Aquí: <strong>${d.aqui}</strong> · Otras sedes: <strong>${d.otras}</strong>`;
    }
    return `
      <li class="producto-pedido${cantidad > 0 ? ' elegido' : ''}">
        <div class="producto-info">
          <span><strong>${escapar(p.nombre)}</strong> <span class="codigo">${escapar(p.codigo)}</span></span>
          <span class="descripcion">${escapar(p.categoria)} · ${colones(p.precio)}</span>
          <span class="disponibilidad">${texto}</span>
        </div>
        <div class="contador-cantidad" role="group" aria-label="Cantidad de ${escapar(p.nombre)}">
          <button type="button" class="boton-cantidad" data-menos="${escapar(p.codigo)}" aria-label="Quitar uno" ${cantidad === 0 ? 'disabled' : ''}>−</button>
          <input type="number" min="0" max="${MAXIMO_POR_PRODUCTO}" step="1" inputmode="numeric" value="${cantidad}"
            data-cantidad="${escapar(p.codigo)}" aria-label="Cantidad de ${escapar(p.nombre)}">
          <button type="button" class="boton-cantidad" data-mas="${escapar(p.codigo)}" aria-label="Agregar uno">+</button>
        </div>
      </li>`;
  }

  function pintarListaProductos() {
    const busqueda = normalizar($('buscar-pedido') ? $('buscar-pedido').value : '');
    const filtrados = catalogo.filter((p) => !busqueda || normalizar(p.nombre).includes(busqueda)
      || normalizar(p.codigo).includes(busqueda) || normalizar(p.categoria).includes(busqueda));
    $('lista-productos').innerHTML = filtrados.length === 0
      ? '<li class="vacio-simple">Ningún producto coincide con la búsqueda.</li>'
      : filtrados.map(tarjetaProducto).join('');
  }

  function pintarCarrito() {
    $('resumen').innerHTML = resumenCarrito();
    $('ir-destino').addEventListener('click', () => irAPaso(2));
  }

  function cambiarCantidad(codigo, valor) {
    let n = Math.floor(Number(valor));
    if (!Number.isFinite(n) || n < 0) {
      n = 0;
    }
    n = Math.min(n, MAXIMO_POR_PRODUCTO);
    carrito[codigo] = n;
    pintarListaProductos();
    pintarCarrito();
  }

  function pintarProductos() {
    contenido.innerHTML = `
      <div class="paso-con-resumen">
        <section class="tarjeta panel-paso">
          <h2>¿Qué productos necesita?</h2>
          <p class="ayuda">Puede pedir productos de esta sede o de las otras. Use los botones + y − o escriba la cantidad.</p>
          <label class="buscador">
            <span class="sr-only">Buscar producto</span>
            <svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>
            <input type="search" id="buscar-pedido" placeholder="Buscar producto">
          </label>
          <ul class="lista-productos" id="lista-productos"></ul>
        </section>
        <div id="resumen"></div>
      </div>`;
    pintarListaProductos();
    pintarCarrito();

    $('buscar-pedido').addEventListener('input', pintarListaProductos);
    $('lista-productos').addEventListener('click', (evento) => {
      const mas = evento.target.closest('[data-mas]');
      const menos = evento.target.closest('[data-menos]');
      if (mas) {
        cambiarCantidad(mas.dataset.mas, (carrito[mas.dataset.mas] || 0) + 1);
      } else if (menos) {
        cambiarCantidad(menos.dataset.menos, (carrito[menos.dataset.menos] || 0) - 1);
      }
    });
    $('lista-productos').addEventListener('change', (evento) => {
      const campo = evento.target.closest('[data-cantidad]');
      if (campo) {
        cambiarCantidad(campo.dataset.cantidad, campo.value);
      }
    });
  }

  // ---------------------------------------------------------------------------
  // Paso 2: lugar de entrega
  // ---------------------------------------------------------------------------

  function pintarDestino() {
    contenido.innerHTML = `
      <div class="paso-con-resumen">
        <section class="tarjeta panel-paso">
          <h2>¿Dónde se entrega?</h2>
          <p class="ayuda">El vehículo sale desde ${escapar(sede.nombre)}. Si no hay ruta hasta el lugar, el pedido se rechaza.</p>
          <label class="campo">Lugar de entrega
            <select id="elegir-destino">
              <option value="">Elija un lugar…</option>
              ${ubicaciones.map((u) => `<option value="${escapar(u.nombre)}"${u.nombre === destino ? ' selected' : ''}>${escapar(u.nombre)}${u.alcanzable ? '' : ' (sin ruta desde esta sede)'}</option>`).join('')}
            </select>
          </label>
          <div id="info-destino" aria-live="polite"></div>
          <div class="acciones-formulario">
            <button class="boton boton-secundario" type="button" id="volver-productos">Atrás</button>
            <button class="boton" type="button" id="ir-confirmar" ${destino ? '' : 'disabled'}>Siguiente: confirmar</button>
          </div>
        </section>
        <div id="resumen"></div>
      </div>`;
    $('resumen').innerHTML = resumenCarrito();
    $('ir-destino').hidden = true;

    $('volver-productos').addEventListener('click', () => irAPaso(1));
    $('ir-confirmar').addEventListener('click', () => irAPaso(3));
    $('elegir-destino').addEventListener('change', (evento) => {
      destino = evento.target.value;
      $('ir-confirmar').disabled = !destino;
      mostrarRuta();
    });
    mostrarRuta();
  }

  async function mostrarRuta() {
    const caja = $('info-destino');
    if (!caja) {
      return;
    }
    if (!destino) {
      caja.innerHTML = '';
      return;
    }
    caja.innerHTML = '<p class="cargando">Consultando la ruta…</p>';
    try {
      const r = await obtener(`/api/ruta?destino=${encodeURIComponent(destino)}`);
      if (!activa || !$('info-destino')) {
        return;
      }
      caja.innerHTML = r.alcanzable
        ? `<div class="mensaje mensaje-info"><strong>Ruta: ${escapar(textoMinutos(r.tiempoTotal))} desde ${escapar(r.origen)}</strong>
            <span>${textoCamino(r.camino)}</span></div>`
        : `<div class="mensaje mensaje-error"><strong>No hay ruta</strong><span>${escapar(r.explicacion)}
            Si continúa, el pedido será rechazado.</span></div>`;
    } catch (error) {
      caja.innerHTML = `<p class="mensaje mensaje-error">${escapar(error.message)}</p>`;
    }
  }

  // ---------------------------------------------------------------------------
  // Paso 3: confirmar
  // ---------------------------------------------------------------------------

  function pintarConfirmacion() {
    const ls = lineas();
    contenido.innerHTML = `
      <section class="tarjeta panel-paso confirmar-pedido">
        <h2>Revise el pedido</h2>
        <dl class="datos-pedido">
          <div><dt>Sede</dt><dd>${escapar(sede.nombre)}</dd></div>
          <div><dt>Lugar de entrega</dt><dd>${escapar(destino)}</dd></div>
          <div><dt>Salida estimada</dt><dd>${escapar(textoSalida(sede.proximaSalida))}</dd></div>
        </dl>
        <table class="tabla">
          <thead><tr><th scope="col">Producto</th><th scope="col" class="numero">Cantidad</th>
            <th scope="col" class="numero">Precio</th><th scope="col" class="numero">Subtotal</th></tr></thead>
          <tbody>${ls.map((l) => `
            <tr><td data-titulo="Producto">${escapar(l.producto.nombre)} <span class="codigo">${escapar(l.producto.codigo)}</span></td>
              <td data-titulo="Cantidad" class="numero">${l.cantidad}</td>
              <td data-titulo="Precio" class="numero">${colones(l.producto.precio)}</td>
              <td data-titulo="Subtotal" class="numero">${colones(l.cantidad * l.producto.precio)}</td></tr>`).join('')}
          </tbody>
          <tfoot><tr><th scope="row" colspan="3">Total</th><td class="numero"><strong>${colones(total())}</strong></td></tr></tfoot>
        </table>
        <p class="ayuda">Si hay productos de otras sedes, el sistema les pide que los aparten. Puede tardar unos segundos.</p>
        <div id="error-pedido"></div>
        <div class="acciones-formulario">
          <button class="boton boton-secundario" type="button" id="volver-destino">Atrás</button>
          <button class="boton boton-grande" type="button" id="registrar-pedido">Registrar pedido</button>
        </div>
      </section>`;
    $('volver-destino').addEventListener('click', () => irAPaso(2));
    $('registrar-pedido').addEventListener('click', registrar);
  }

  async function registrar() {
    const boton = $('registrar-pedido');
    boton.disabled = true;
    $('volver-destino').disabled = true;
    boton.innerHTML = '<span class="girando" aria-hidden="true"></span>Registrando pedido…';
    try {
      const r = await enviar('/api/pedidos', {
        destino,
        lineas: lineas().map((l) => ({ codigoProducto: l.producto.codigo, cantidad: l.cantidad })),
      });
      if (activa) {
        pintarResultado(r);
      }
    } catch (error) {
      // Error de datos (400) o de conexión: el pedido NO se registró
      $('error-pedido').innerHTML = `<div class="mensaje mensaje-error" role="alert"><strong>No se registró el pedido</strong>
        <span>${escapar(error.message)}</span></div>`;
      boton.disabled = false;
      $('volver-destino').disabled = false;
      boton.textContent = 'Registrar pedido';
    }
  }

  function pintarResultado(r) {
    const p = r.pedido;
    contenedor.querySelector('.pasos').hidden = true;
    contenido.innerHTML = `
      <section class="tarjeta resultado-pedido ${r.aceptado ? 'resultado-aceptado' : 'resultado-rechazado'}" role="alert">
        <div class="resultado-icono" aria-hidden="true">${r.aceptado ? '✓' : '✕'}</div>
        <h2>${r.aceptado ? 'Pedido aceptado' : 'Pedido rechazado'}</h2>
        <p class="resultado-id">Pedido <strong>${escapar(p.id)}</strong> ${etiquetaEstado(p.estado)}</p>
        ${r.aceptado
    ? `<p>Se entregará en <strong>${escapar(p.destino)}</strong> en la salida de las <strong>${escapar(textoSalida(p.salida))}</strong>.</p>`
    : `<p class="motivo"><strong>Motivo:</strong> ${escapar(r.motivo || 'No se indicó el motivo.')}</p>
           <p class="ayuda">No se descontó nada del inventario: las unidades apartadas se devolvieron.</p>`}
        ${r.aviso ? `<p class="mensaje mensaje-info">${escapar(r.aviso)}</p>` : ''}
        ${r.aceptado && p.detalles.length > 0 ? `
          <table class="tabla">
            <thead><tr><th scope="col">Producto</th><th scope="col" class="numero">Cantidad</th><th scope="col">Lo aporta</th>
              <th scope="col" class="numero">Subtotal</th></tr></thead>
            <tbody>${p.detalles.map((d) => `
              <tr><td data-titulo="Producto">${escapar(d.nombre)}</td><td data-titulo="Cantidad" class="numero">${d.cantidad}</td>
                <td data-titulo="Lo aporta">${escapar(d.nombreSedeSurtidora)}</td>
                <td data-titulo="Subtotal" class="numero">${colones(d.subtotal)}</td></tr>`).join('')}
            </tbody>
            <tfoot><tr><th scope="row" colspan="3">Total</th><td class="numero"><strong>${colones(p.montoTotal)}</strong></td></tr></tfoot>
          </table>` : ''}
        <div class="acciones-formulario">
          <button class="boton" type="button" id="otro-pedido">Hacer otro pedido</button>
          <a class="boton boton-secundario" href="#pedidos">Ver pedidos</a>
        </div>
      </section>`;
    $('otro-pedido').addEventListener('click', () => {
      Object.keys(carrito).forEach((k) => { delete carrito[k]; });
      destino = '';
      contenedor.querySelector('.pasos').hidden = false;
      cargar();
    });
  }

  // ---------------------------------------------------------------------------
  // Carga
  // ---------------------------------------------------------------------------

  async function cargar() {
    try {
      const [c, g] = await Promise.all([obtener('/api/catalogo'), obtener('/api/grafo')]);
      if (!activa) {
        return;
      }
      catalogo = c;
      ubicaciones = g.ubicaciones;
      if (catalogo.length === 0) {
        contenido.innerHTML = '<div class="tarjeta vacio-simple">El catálogo no tiene productos todavía.</div>';
        return;
      }
      irAPaso(1);
    } catch (error) {
      if (activa) {
        mostrarError(contenido, error.message, cargar);
      }
    }
  }

  cargar();
  return () => { activa = false; };
}
