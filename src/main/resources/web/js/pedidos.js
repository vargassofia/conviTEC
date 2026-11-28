// Pantalla Pedidos: pendientes de entrega e historial de pedidos de la sede.

import { obtener } from './api.js';
import {
  activarPestanas, colones, escapar, etiquetaEstado, fechaHora, htmlPestanas, mostrarError, plural, textoSalida,
} from './util.js';

const FILTROS_HISTORIAL = [
  { valor: 'todos', texto: 'Todos los estados' },
  { valor: 'CONFIRMADO', texto: 'Por entregar' },
  { valor: 'ENTREGADO', texto: 'Entregados' },
  { valor: 'CANCELADO', texto: 'Rechazados' },
  { valor: 'EN_PROCESO', texto: 'En proceso' },
];

export function montarPedidos(contenedor) {
  contenedor.insertAdjacentHTML('beforeend', `
    ${htmlPestanas([
      { id: 'pendientes', texto: 'Por entregar' },
      { id: 'historial', texto: 'Historial' },
    ], 'pendientes')}
    <div class="barra-herramientas" id="barra-filtro" hidden>
      <label class="campo-en-linea">Mostrar
        <select id="filtro-estado">${FILTROS_HISTORIAL.map((f) => `<option value="${f.valor}">${escapar(f.texto)}</option>`).join('')}</select>
      </label>
      <a class="boton boton-secundario" href="#nuevo-pedido">Nuevo pedido</a>
    </div>
    <section id="lista-pedidos" aria-live="polite"><p class="cargando">Cargando…</p></section>`);

  const $ = (id) => contenedor.querySelector(`#${id}`);
  let pestana = 'pendientes';
  let activa = true;

  function tarjeta(p) {
    const productos = p.detalles.reduce((suma, d) => suma + d.cantidad, 0);
    return `
      <li>
        <details class="pedido tarjeta">
          <summary>
            <span class="pedido-cabecera">
              <span class="pedido-id">${escapar(p.id)}</span>
              ${etiquetaEstado(p.estado)}
            </span>
            <span class="pedido-datos">
              <span><span class="etiqueta-dato">Destino</span>${escapar(p.destino)}</span>
              <span><span class="etiqueta-dato">Registrado</span>${escapar(fechaHora(p.fechaCreacion))}</span>
              <span><span class="etiqueta-dato">Productos</span>${productos}</span>
              <span><span class="etiqueta-dato">Total</span><strong>${colones(p.montoTotal)}</strong></span>
            </span>
          </summary>
          <div class="pedido-detalle">
            ${p.estado === 'CANCELADO' && p.motivoCancelacion
    ? `<p class="mensaje mensaje-error"><strong>Motivo del rechazo</strong><span>${escapar(p.motivoCancelacion)}</span>
        <span>No se descontó nada del inventario de ninguna sede.</span></p>` : ''}
            <dl class="datos-pedido">
              ${p.estado === 'CONFIRMADO' ? `<div><dt>Salida</dt><dd>${escapar(textoSalida(p.salida))}</dd></div>` : ''}
              ${p.fechaEntrega ? `<div><dt>Entregado</dt><dd>${escapar(fechaHora(p.fechaEntrega))}</dd></div>` : ''}
              <div><dt>Sedes que participan</dt><dd>${p.sedesParticipantes.map((s) => escapar(s.nombre)).join(', ')}</dd></div>
            </dl>
            ${p.detalles.length === 0 ? '<p class="ayuda">Este pedido no llegó a tener productos apartados.</p>' : `
            <table class="tabla">
              <thead><tr><th scope="col">Producto</th><th scope="col" class="numero">Cantidad</th>
                <th scope="col" class="numero">Precio</th><th scope="col">${p.estado === 'CANCELADO' ? 'Se pidió a' : 'Lo aporta'}</th><th scope="col" class="numero">Subtotal</th></tr></thead>
              <tbody>${p.detalles.map((d) => `
                <tr><td data-titulo="Producto">${escapar(d.nombre)} <span class="codigo">${escapar(d.codigoProducto)}</span></td>
                  <td data-titulo="Cantidad" class="numero">${d.cantidad}</td>
                  <td data-titulo="Precio" class="numero">${colones(d.precioUnitario)}</td>
                  <td data-titulo="${p.estado === 'CANCELADO' ? 'Se pidió a' : 'Lo aporta'}">${escapar(d.nombreSedeSurtidora)}</td>
                  <td data-titulo="Subtotal" class="numero">${colones(d.subtotal)}</td></tr>`).join('')}
              </tbody>
            </table>`}
          </div>
        </details>
      </li>`;
  }

  async function cargar() {
    const lista = $('lista-pedidos');
    lista.innerHTML = '<p class="cargando">Cargando…</p>';
    const filtro = pestana === 'pendientes' ? 'pendientes' : $('filtro-estado').value;
    try {
      const pedidos = await obtener(`/api/pedidos?estado=${encodeURIComponent(filtro)}`);
      if (!activa) {
        return;
      }
      if (pedidos.length === 0) {
        lista.innerHTML = pestana === 'pendientes'
          ? `<div class="vacio tarjeta"><h2>No hay pedidos por entregar</h2>
              <p>Los pedidos aceptados aparecen aquí hasta que el vehículo los entrega.</p>
              <a class="boton" href="#nuevo-pedido">Hacer un pedido</a></div>`
          : '<div class="vacio-simple tarjeta">No hay pedidos con ese estado.</div>';
        return;
      }
      lista.innerHTML = `
        <p class="ayuda">${plural(pedidos.length, 'pedido', 'pedidos')}. ${pestana === 'pendientes'
    ? 'En el orden en que llegaron.' : 'Del más reciente al más antiguo.'} Toque un pedido para ver su detalle.</p>
        <ul class="lista-tarjetas">${pedidos.map(tarjeta).join('')}</ul>`;
    } catch (error) {
      if (activa) {
        mostrarError(lista, error.message, cargar);
      }
    }
  }

  activarPestanas(contenedor, (id) => {
    pestana = id;
    $('barra-filtro').hidden = id !== 'historial';
    cargar();
  });
  $('filtro-estado').addEventListener('change', cargar);

  cargar();
  return () => { activa = false; };
}
