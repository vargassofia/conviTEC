// Pantalla de Entregas: salida del vehículo paso a paso (cada parada entrega y registra las ventas),
// vista previa del recorrido completo y consulta de rutas.

import { obtener, enviar } from './api.js';
import { MapaConviTEC } from './mapa.js';
import { colones, escapar, plural, textoCamino, textoMinutos } from './util.js';

/**
 * Muestra la pantalla dentro del contenedor.
 * @returns {Function} función que limpia la pantalla al salir (destruye el mapa)
 */
export function montarEntregas(contenedor, sede) {
  contenedor.insertAdjacentHTML('beforeend', `
    <div class="entregas">
      <section class="panel-entregas tarjeta" aria-labelledby="titulo-salida">
        <div class="panel-cabecera">
          <h2 id="titulo-salida">Salida del vehículo</h2>
          <span class="etiqueta-demo" id="etiqueta-demo" hidden title="Los pedidos son de prueba hasta que esté listo el módulo de pedidos.">Pedidos de prueba</span>
        </div>

        <fieldset class="selector-salida">
          <legend>Elija la salida</legend>
          <label><input type="radio" name="salida" value="11:00"><span>11:00 a. m.</span></label>
          <label><input type="radio" name="salida" value="15:00"><span>3:00 p. m.</span></label>
        </fieldset>

        <button class="boton boton-secundario boton-ancho" id="boton-iniciar" type="button">Iniciar salida</button>

        <dl class="indicadores" id="indicadores">
          <div><dt>Ubicación actual</dt><dd id="dato-ubicacion">—</dd></div>
          <div><dt>Tiempo recorrido</dt><dd id="dato-tiempo">—</dd></div>
          <div><dt>Pedidos entregados</dt><dd id="dato-entregados">—</dd></div>
        </dl>

        <button class="boton boton-grande boton-ancho" id="boton-siguiente" type="button" disabled>
          <svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>
          Calcular siguiente punto de entrega
        </button>
        <button class="boton-enlace" id="boton-completo" type="button">Ver el recorrido completo (vista previa, no entrega nada)</button>

        <div class="mensaje" id="mensaje" role="status" aria-live="polite" hidden></div>

        <div class="listas-entregas">
          <section>
            <h3>Por entregar <span class="contador" id="contador-pendientes">0</span></h3>
            <ul class="lista-pedidos" id="lista-pendientes"></ul>
          </section>
          <section id="seccion-rechazados" hidden>
            <h3>No se pueden entregar <span class="contador contador-error" id="contador-rechazados">0</span></h3>
            <ul class="lista-pedidos lista-rechazados" id="lista-rechazados"></ul>
          </section>
          <section>
            <h3>Paradas realizadas <span class="contador" id="contador-paradas">0</span></h3>
            <ol class="lista-paradas" id="lista-paradas"></ol>
          </section>
        </div>
      </section>

      <section class="mapa-tarjeta tarjeta" aria-label="Mapa de rutas">
        <div class="mapa" id="mapa"><p class="cargando">Cargando mapa…</p></div>
        <div class="aviso-mapa" id="aviso-mapa">Mapa sin conexión a internet: se muestran las ubicaciones y rutas sin el fondo.</div>
        <ul class="leyenda" aria-label="Leyenda del mapa">
          <li><span class="muestra muestra-sede"></span>Sede</li>
          <li><span class="muestra muestra-punto"></span>Punto de entrega</li>
          <li><span class="muestra muestra-pendiente"></span>Destino pendiente</li>
          <li><span class="muestra muestra-recorrido"></span>Recorrido</li>
          <li><span class="muestra muestra-consulta"></span>Ruta consultada</li>
          <li><span class="muestra muestra-aislada"></span>Zona aislada</li>
        </ul>
      </section>

      <section class="consulta-ruta tarjeta" aria-labelledby="titulo-consulta">
        <h2 id="titulo-consulta">Consultar una ruta</h2>
        <p class="ayuda">Vea el camino más corto y el tiempo desde ${escapar(sede.nombre)} hasta cualquier lugar.</p>
        <form class="formulario-ruta" id="formulario-ruta">
          <label for="destino-ruta">Destino</label>
          <div class="fila-formulario">
            <select id="destino-ruta" required>
              <option value="">Elija un lugar…</option>
            </select>
            <button class="boton" type="submit">Consultar</button>
          </div>
        </form>
        <div class="resultado-ruta" id="resultado-ruta" aria-live="polite"></div>
      </section>
    </div>`);

  const $ = (id) => contenedor.querySelector(`#${id}`);
  let mapa = null;
  let estado = null;
  let ocupado = false;

  // ---------------------------------------------------------------------------
  // Mostrar el estado
  // ---------------------------------------------------------------------------

  function horaElegida() {
    const marcada = contenedor.querySelector('input[name="salida"]:checked');
    return marcada ? marcada.value : null;
  }

  function elegirHora(hora) {
    contenedor.querySelectorAll('input[name="salida"]').forEach((r) => { r.checked = r.value === hora; });
  }

  function mostrarMensaje(tipo, html) {
    const caja = $('mensaje');
    caja.className = `mensaje mensaje-${tipo}`;
    caja.innerHTML = html;
    caja.hidden = !html;
  }

  function pintarEstado(e, dibujarMapa = true) {
    estado = e;
    $('etiqueta-demo').hidden = !e.esDemostracion;
    $('dato-ubicacion').textContent = e.ubicacionActual || '—';
    $('dato-tiempo').textContent = e.iniciado ? textoMinutos(e.tiempoAcumulado) : '—';
    $('dato-entregados').textContent = e.iniciado ? `${e.entregados} de ${e.totalPedidos}` : '—';
    $('boton-iniciar').textContent = e.iniciado ? 'Reiniciar la salida' : 'Iniciar salida';
    $('boton-siguiente').disabled = ocupado || !e.hayPendientes;

    // Pendientes
    $('contador-pendientes').textContent = e.pendientes.length;
    $('lista-pendientes').innerHTML = e.pendientes.length === 0
      ? `<li class="lista-vacia">${e.iniciado ? 'No queda nada por entregar.' : 'Inicie una salida para ver sus pedidos.'}</li>`
      : e.pendientes.map((p) => `
          <li><span class="id-pedido">${escapar(p.idPedido)}</span><span>${escapar(p.destino)}</span></li>`).join('');

    // No alcanzables
    $('seccion-rechazados').hidden = e.noAlcanzables.length === 0;
    $('contador-rechazados').textContent = e.noAlcanzables.length;
    $('lista-rechazados').innerHTML = e.noAlcanzables.map((r) => `
      <li><span class="id-pedido">${escapar(r.idPedido)}</span><span>${escapar(r.explicacion)}</span></li>`).join('');

    // Paradas
    $('contador-paradas').textContent = e.paradas.length;
    $('lista-paradas').innerHTML = e.paradas.length === 0
      ? '<li class="lista-vacia">Todavía no hay paradas.</li>'
      : e.paradas.map((p) => `
          <li>
            <strong>${escapar(p.destino)}</strong>
            <span class="detalle-parada">${textoCamino(p.camino)}</span>
            <span class="detalle-parada">${escapar(textoMinutos(p.tiempoTramo))} · acumulado ${escapar(textoMinutos(p.tiempoAcumulado))} · ${escapar(p.idsPedidos.join(', '))}</span>
          </li>`).join('');

    if (mapa && dibujarMapa) {
      mapa.mostrarEntregas(e);
    }
  }

  function mensajeDeParada(e) {
    const p = e.ultimaParada;
    if (!p) {
      return;
    }
    const pedidos = p.idsPedidos.length === 1 ? `el pedido ${p.idsPedidos[0]}` : `los pedidos ${p.idsPedidos.join(', ')}`;
    let html = `<strong>Parada ${p.numero}: ${escapar(p.destino)}</strong>
      <span>Camino: ${textoCamino(p.camino)}</span>
      <span>${escapar(textoMinutos(p.tiempoTramo))} de viaje (acumulado ${escapar(textoMinutos(p.tiempoAcumulado))}). Se entregan ${escapar(pedidos)}.</span>`;
    const ventas = e.ventasRegistradas || [];
    if (ventas.length > 0) {
      const monto = ventas.reduce((suma, v) => suma + v.montoTotal, 0);
      html += `<span>✓ Se ${ventas.length === 1 ? 'registró' : 'registraron'} ${plural(ventas.length, 'venta', 'ventas')} por <strong>${colones(monto)}</strong>.</span>`;
    }
    if (e.aviso) {
      html += `<span class="aviso-parada">⚠ ${escapar(e.aviso)}</span>`;
    }
    if (!e.hayPendientes) {
      html += `<span class="fin-recorrido">Recorrido terminado: ${plural(e.paradas.length, 'parada', 'paradas')}, ${escapar(textoMinutos(e.tiempoAcumulado))} en total.</span>`;
    }
    mostrarMensaje(e.aviso ? 'info' : 'exito', html);
  }

  // ---------------------------------------------------------------------------
  // Acciones
  // ---------------------------------------------------------------------------

  async function ejecutar(boton, textoEspera, accion) {
    if (ocupado) {
      return;
    }
    ocupado = true;
    const textoOriginal = boton.innerHTML;
    boton.disabled = true;
    boton.innerHTML = `<span class="girando" aria-hidden="true"></span>${escapar(textoEspera)}`;
    try {
      await accion();
    } catch (error) {
      mostrarMensaje('error', escapar(error.message));
    } finally {
      ocupado = false;
      boton.innerHTML = textoOriginal;
      boton.disabled = false;
      if (estado) {
        pintarEstado(estado, false); // solo los botones y listas; el mapa ya quedó dibujado
      }
    }
  }

  function exigirHora() {
    const hora = horaElegida();
    if (!hora) {
      mostrarMensaje('error', 'Primero elija la salida: 11:00 a. m. o 3:00 p. m.');
      $('mensaje').scrollIntoView({ block: 'nearest' });
    }
    return hora;
  }

  $('boton-iniciar').addEventListener('click', (evento) => {
    const hora = exigirHora();
    if (!hora) {
      return;
    }
    if (estado && estado.iniciado && estado.hayPendientes && estado.entregados > 0
      && !confirm('La salida actual todavía tiene entregas pendientes. ¿Desea empezar de nuevo?')) {
      return;
    }
    ejecutar(evento.currentTarget, 'Preparando…', async () => {
      const e = await enviar('/api/entregas/iniciar', { horaSalida: hora });
      pintarEstado(e);
      if (e.totalPedidos === 0) {
        mostrarMensaje('info', `No hay pedidos para la salida de las ${escapar(hora)}.`);
      } else if (e.pendientes.length === 0) {
        mostrarMensaje('info', 'Ninguno de los pedidos de esta salida se puede entregar desde esta sede.');
      } else {
        mostrarMensaje('info', `Salida de las ${escapar(hora)} lista: ${plural(e.pendientes.length, 'pedido', 'pedidos')} por entregar. `
          + 'Presione <strong>Calcular siguiente punto de entrega</strong>.');
      }
    });
  });

  $('boton-siguiente').addEventListener('click', (evento) => {
    ejecutar(evento.currentTarget, 'Calculando…', async () => {
      const e = await enviar('/api/entregas/siguiente', {});
      pintarEstado(e);
      mensajeDeParada(e);
    });
  });

  $('boton-completo').addEventListener('click', (evento) => {
    const hora = exigirHora();
    if (!hora) {
      return;
    }
    ejecutar(evento.currentTarget, 'Calculando…', async () => {
      const v = await enviar('/api/entregas/vista-previa', { horaSalida: hora });
      if (v.paradas.length === 0) {
        mostrarMensaje('info', `No hay pedidos por entregar en la salida de las ${escapar(hora)}.`);
        return;
      }
      const final = v.paradas[v.paradas.length - 1].destino;
      if (mapa) {
        // El mapa usa la misma forma que el estado de una salida terminada
        mapa.mostrarEntregas({
          iniciado: true, sede: v.sede, ubicacionActual: final, paradas: v.paradas, pendientes: [], hayPendientes: false,
        });
      }
      mostrarMensaje('info', `<strong>Vista previa de la salida de las ${escapar(hora)}</strong>
        <span>${v.paradas.map((p) => `${p.numero}. ${escapar(p.destino)} (${escapar(textoMinutos(p.tiempoAcumulado))})`).join(' · ')}</span>
        <span>${plural(v.paradas.length, 'parada', 'paradas')}, ${plural(v.pedidosEntregados, 'pedido', 'pedidos')}, <strong>${escapar(textoMinutos(v.tiempoTotal))}</strong> en total.
          El vehículo terminaría en ${escapar(final)} (no regresa a la sede).</span>
        ${v.noAlcanzables.length > 0 ? `<span>No se podrían entregar: ${v.noAlcanzables.map((n) => escapar(n.idPedido)).join(', ')}.</span>` : ''}
        <span class="aviso-parada">Esto es solo una vista previa: no se entregó ni se registró nada.</span>
        <button class="boton-enlace" type="button" id="volver-salida">Volver a ver la salida actual en el mapa</button>`);
      $('volver-salida').addEventListener('click', () => {
        if (mapa && estado) {
          mapa.mostrarEntregas(estado);
        }
        $('mensaje').hidden = true;
      });
    });
  });

  $('formulario-ruta').addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const destino = $('destino-ruta').value;
    const resultado = $('resultado-ruta');
    if (!destino) {
      resultado.innerHTML = '<p class="mensaje mensaje-error">Elija un destino.</p>';
      return;
    }
    resultado.innerHTML = '<p class="cargando">Calculando…</p>';
    try {
      const r = await obtener(`/api/ruta?destino=${encodeURIComponent(destino)}`);
      if (r.alcanzable) {
        resultado.innerHTML = `
          <div class="mensaje mensaje-exito">
            <strong>${escapar(r.origen)} → ${escapar(r.destino)}: ${escapar(textoMinutos(r.tiempoTotal))}</strong>
            <span>Camino: ${textoCamino(r.camino)}</span>
          </div>`;
        if (mapa) {
          mapa.mostrarRuta(r.camino);
        }
      } else {
        resultado.innerHTML = `<div class="mensaje mensaje-error"><strong>No hay ruta</strong><span>${escapar(r.explicacion)}</span></div>`;
        if (mapa) {
          mapa.limpiarRuta();
        }
      }
    } catch (error) {
      resultado.innerHTML = `<p class="mensaje mensaje-error">${escapar(error.message)}</p>`;
    }
  });

  // ---------------------------------------------------------------------------
  // Carga inicial
  // ---------------------------------------------------------------------------

  async function cargar() {
    let grafo;
    let inicial;
    try {
      [grafo, inicial] = await Promise.all([obtener('/api/grafo'), obtener('/api/entregas')]);
    } catch (error) {
      $('mapa').innerHTML = `<p class="mensaje mensaje-error">${escapar(error.message)}</p>`;
      mostrarMensaje('error', escapar(error.message));
      return;
    }

    // Destinos para consultar (ya vienen en orden alfabético desde el servidor)
    $('destino-ruta').insertAdjacentHTML('beforeend', grafo.ubicaciones
      .filter((u) => !u.esEstaSede)
      .map((u) => `<option value="${escapar(u.nombre)}">${escapar(u.nombre)}${u.alcanzable ? '' : ' (fuera de alcance)'}</option>`)
      .join(''));

    if (typeof L === 'undefined') {
      $('mapa').innerHTML = '<p class="mensaje mensaje-error">No se pudo cargar el mapa.</p>';
    } else {
      $('mapa').innerHTML = '';
      mapa = new MapaConviTEC($('mapa'), grafo, sede.codigo);
    }

    elegirHora(inicial.iniciado ? inicial.horaSalida : sede.proximaSalida.hora);
    pintarEstado(inicial);
    if (inicial.iniciado && inicial.ultimaParada) {
      mensajeDeParada(inicial);
    }
  }

  cargar();

  return () => {
    if (mapa) {
      mapa.destruir();
      mapa = null;
    }
  };
}
