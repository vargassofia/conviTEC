// Pantalla Estadísticas: totales, producto estrella, ranking, peores pedidos e historial de ventas.
// Los cálculos y el orden los hace el servidor (ServicioEstadisticas, MergeSort propio).

import { obtener } from './api.js';
import { colones, escapar, fechaHora, mostrarError, plural, textoMinutos } from './util.js';

export function montarEstadisticas(contenedor) {
  contenedor.insertAdjacentHTML('beforeend', `
    <div id="contenido-estadisticas" aria-live="polite"><p class="cargando">Cargando…</p></div>`);

  const $ = (id) => contenedor.querySelector(`#${id}`);
  const filtros = { desde: '', hasta: '', peores: '5' };
  let activa = true;

  function tarjetasResumen(e) {
    const estrella = e.productoEstrella;
    return `
      <div class="tarjetas-resumen">
        <div class="tarjeta dato-resumen">
          <span class="dato-etiqueta">Total vendido</span>
          <strong class="dato-valor">${colones(e.montoTotalVendido)}</strong>
          <span class="dato-detalle">Todas las ventas de la sede</span>
        </div>
        <div class="tarjeta dato-resumen dato-estrella">
          <span class="dato-etiqueta">★ Producto estrella</span>
          ${estrella
    ? `<strong class="dato-valor">${escapar(estrella.nombre)}</strong>
             <span class="dato-detalle">${colones(estrella.monto)} en ventas · ${estrella.unidades} unidades</span>`
    : '<strong class="dato-valor">—</strong><span class="dato-detalle">Todavía no hay ventas</span>'}
        </div>
        <div class="tarjeta dato-resumen">
          <span class="dato-etiqueta">Productos vendidos</span>
          <strong class="dato-valor">${e.ranking.length}</strong>
          <span class="dato-detalle">Productos distintos con ventas</span>
        </div>
      </div>`;
  }

  function ranking(e) {
    if (e.ranking.length === 0) {
      return '<p class="vacio-simple">Cuando se entreguen pedidos, aquí verá qué productos generan más ingresos.</p>';
    }
    const mayor = e.ranking[0].monto || 1; // ya vienen de mayor a menor
    return `<ol class="ranking">${e.ranking.map((p, i) => `
      <li>
        <span class="ranking-posicion">${i + 1}</span>
        <span class="ranking-nombre">${escapar(p.nombre)} <span class="codigo">${escapar(p.codigo)}</span></span>
        <span class="ranking-barra" aria-hidden="true"><span style="width:${Math.max(4, (p.monto / mayor) * 100)}%"></span></span>
        <span class="ranking-monto">${colones(p.monto)} <span class="descripcion">${p.unidades} u.</span></span>
      </li>`).join('')}</ol>`;
  }

  function peores(e) {
    if (e.peoresPedidos.length === 0) {
      return '<p class="vacio-simple">Todavía no hay pedidos entregados.</p>';
    }
    return `
      <table class="tabla">
        <thead><tr><th scope="col">Pedido</th><th scope="col">Destino</th><th scope="col" class="numero">Monto</th>
          <th scope="col" class="numero">Traslado</th><th scope="col" class="numero">Rendimiento</th></tr></thead>
        <tbody>${e.peoresPedidos.map((v) => `
          <tr><td data-titulo="Pedido"><span class="codigo">${escapar(v.idPedido)}</span></td>
            <td data-titulo="Destino">${escapar(v.destino)}</td>
            <td data-titulo="Monto" class="numero">${colones(v.montoTotal)}</td>
            <td data-titulo="Traslado" class="numero">${escapar(textoMinutos(v.tiempoTraslado))}</td>
            <td data-titulo="Rendimiento" class="numero"><strong>${colones(v.rendimiento)}</strong>/min</td></tr>`).join('')}
        </tbody>
      </table>`;
  }

  function historial(e) {
    if (e.historial.length === 0) {
      return filtros.desde || filtros.hasta
        ? '<p class="vacio-simple">No hay ventas en esas fechas.</p>'
        : '<p class="vacio-simple">Todavía no hay ventas. Se registran cuando el vehículo entrega los pedidos.</p>';
    }
    return `
      <p class="ayuda">${plural(e.historial.length, 'venta', 'ventas')} por ${colones(e.montoHistorial)}. De la más reciente a la más antigua.</p>
      <ul class="lista-tarjetas">${e.historial.map((v) => `
        <li>
          <details class="pedido tarjeta">
            <summary>
              <span class="pedido-cabecera"><span class="pedido-id">${escapar(v.idVenta)}</span>
                <span class="descripcion">pedido ${escapar(v.idPedido)}</span></span>
              <span class="pedido-datos">
                <span><span class="etiqueta-dato">Fecha</span>${escapar(fechaHora(v.fechaHora))}</span>
                <span><span class="etiqueta-dato">Destino</span>${escapar(v.destino)}</span>
                <span><span class="etiqueta-dato">Salida</span>${escapar(v.horaSalida)}</span>
                <span><span class="etiqueta-dato">Monto</span><strong>${colones(v.montoTotal)}</strong></span>
              </span>
            </summary>
            <div class="pedido-detalle">
              <table class="tabla">
                <thead><tr><th scope="col">Producto</th><th scope="col" class="numero">Cantidad</th>
                  <th scope="col" class="numero">Precio unitario</th><th scope="col" class="numero">Subtotal</th></tr></thead>
                <tbody>${v.detalles.map((d) => `
                  <tr><td data-titulo="Producto">${escapar(d.nombre)} <span class="codigo">${escapar(d.codigoProducto)}</span></td>
                    <td data-titulo="Cantidad" class="numero">${d.cantidad}</td>
                    <td data-titulo="Precio unitario" class="numero">${colones(d.precioUnitario)}</td>
                    <td data-titulo="Subtotal" class="numero">${colones(d.subtotal)}</td></tr>`).join('')}
                </tbody>
              </table>
              <p class="ayuda">Traslado: ${escapar(textoMinutos(v.tiempoTraslado))} · rendimiento ${colones(v.rendimiento)} por minuto</p>
            </div>
          </details>
        </li>`).join('')}
      </ul>`;
  }

  function pintar(e) {
    $('contenido-estadisticas').innerHTML = `
      ${tarjetasResumen(e)}

      <div class="dos-columnas">
        <section class="tarjeta seccion-estadistica">
          <h2>Productos que más venden</h2>
          <p class="ayuda">Por ingresos: unidades vendidas × precio.</p>
          ${ranking(e)}
        </section>
        <section class="tarjeta seccion-estadistica">
          <div class="cabecera-con-control">
            <h2>Peores pedidos</h2>
            <label class="campo-en-linea">Mostrar
              <select id="cantidad-peores">${['5', '10', '20'].map((n) => `<option${n === filtros.peores ? ' selected' : ''}>${n}</option>`).join('')}</select>
            </label>
          </div>
          <p class="ayuda">Rendimiento = monto de la venta ÷ minutos de traslado. El peor primero.</p>
          ${peores(e)}
        </section>
      </div>

      <section class="tarjeta seccion-estadistica">
        <h2>Historial de ventas</h2>
        <form class="filtro-fechas" id="filtro-fechas">
          <label>Desde <input type="date" id="fecha-desde" value="${escapar(filtros.desde)}"></label>
          <label>Hasta <input type="date" id="fecha-hasta" value="${escapar(filtros.hasta)}"></label>
          <button class="boton" type="submit">Filtrar</button>
          <button class="boton boton-secundario" type="button" id="limpiar-fechas">Ver todas</button>
        </form>
        <div id="error-fechas"></div>
        ${historial(e)}
      </section>`;

    $('cantidad-peores').addEventListener('change', (evento) => {
      filtros.peores = evento.target.value;
      cargar();
    });
    $('filtro-fechas').addEventListener('submit', (evento) => {
      evento.preventDefault();
      const desde = $('fecha-desde').value;
      const hasta = $('fecha-hasta').value;
      if (desde && hasta && desde > hasta) {
        $('error-fechas').innerHTML = '<p class="mensaje mensaje-error">La fecha “Desde” no puede ser posterior a “Hasta”.</p>';
        return;
      }
      filtros.desde = desde;
      filtros.hasta = hasta;
      cargar();
    });
    $('limpiar-fechas').addEventListener('click', () => {
      filtros.desde = '';
      filtros.hasta = '';
      cargar();
    });
  }

  async function cargar() {
    const parametros = new URLSearchParams({ peores: filtros.peores });
    if (filtros.desde) {
      parametros.set('desde', filtros.desde);
    }
    if (filtros.hasta) {
      parametros.set('hasta', filtros.hasta);
    }
    try {
      const e = await obtener(`/api/estadisticas?${parametros}`);
      if (activa) {
        pintar(e);
      }
    } catch (error) {
      if (activa) {
        mostrarError($('contenido-estadisticas'), error.message, cargar);
      }
    }
  }

  cargar();
  return () => { activa = false; };
}
