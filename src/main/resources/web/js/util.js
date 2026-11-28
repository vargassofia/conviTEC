// Utilidades compartidas por todas las pantallas.

/** Colores de cada sede (los mismos de css/estilos.css). */
export const COLORES_SEDE = {
  LIB: '#b45309',
  ALA: '#b91c1c',
  SJO: '#1d4ed8',
  LIM: '#047857',
};

/** Escapa texto antes de ponerlo en HTML. */
export function escapar(texto) {
  return String(texto ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

/** "11:00 (hoy)" o "11:00 (mañana)". */
export function textoSalida(salida) {
  if (!salida) {
    return '';
  }
  return salida.diaSiguiente ? `${salida.hora} (mañana)` : `${salida.hora} (hoy)`;
}

/** 95 -> "1 h 35 min"; 20 -> "20 min". */
export function textoMinutos(minutos) {
  if (minutos === null || minutos === undefined) {
    return '';
  }
  if (minutos < 60) {
    return `${minutos} min`;
  }
  const horas = Math.floor(minutos / 60);
  const resto = minutos % 60;
  return resto === 0 ? `${horas} h` : `${horas} h ${resto} min`;
}

/** ["San José", "Heredia"] -> "San José → Heredia". */
export function textoCamino(camino) {
  return (camino || []).map(escapar).join(' → ');
}

/** plural(1, 'parada', 'paradas') -> "1 parada"; plural(3, ...) -> "3 paradas". */
export function plural(n, singular, varios) {
  return `${n} ${n === 1 ? singular : varios}`;
}

const FORMATO_COLONES =new Intl.NumberFormat('es-CR', {
  style: 'currency', currency: 'CRC', minimumFractionDigits: 0, maximumFractionDigits: 2,
});

/** 1500 -> "₡1 500". */
export function colones(monto) {
  return FORMATO_COLONES.format(Number(monto) || 0);
}

/** "2026-11-27T14:05:12.123" -> "27 nov 2026, 2:05 p. m." (hora local de la sede). */
export function fechaHora(iso) {
  if (!iso) {
    return '—';
  }
  const fecha = new Date(iso);
  return Number.isNaN(fecha.getTime()) ? String(iso) : fecha.toLocaleString('es-CR', {
    day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit',
  });
}

/** Fecha de hoy en formato AAAA-MM-DD (para los campos de fecha). */
export function hoyISO() {
  const d = new Date();
  const dosDigitos = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${dosDigitos(d.getMonth() + 1)}-${dosDigitos(d.getDate())}`;
}

/** Nombre y estilo de cada estado de pedido, en palabras simples. */
export const ESTADOS_PEDIDO = {
  EN_PROCESO: { texto: 'En proceso', clase: 'estado-proceso' },
  CONFIRMADO: { texto: 'Por entregar', clase: 'estado-pendiente' },
  CANCELADO: { texto: 'Rechazado', clase: 'estado-rechazado' },
  ENTREGADO: { texto: 'Entregado', clase: 'estado-entregado' },
};

/** Etiqueta de color con el estado del pedido. */
export function etiquetaEstado(estado) {
  const e = ESTADOS_PEDIDO[estado] || { texto: estado, clase: '' };
  return `<span class="etiqueta-estado ${e.clase}">${escapar(e.texto)}</span>`;
}

/**
 * Pestañas accesibles. `pestanas` = [{ id, texto }]. Llama a alCambiar(id) al elegir una.
 * Devuelve el HTML; después llamar a activarPestanas(contenedor, alCambiar).
 */
export function htmlPestanas(pestanas, activa) {
  return `<div class="pestanas" role="tablist">${pestanas.map((p) => `
    <button type="button" role="tab" class="pestana" data-pestana="${escapar(p.id)}"
      aria-selected="${p.id === activa}">${escapar(p.texto)}</button>`).join('')}</div>`;
}

export function activarPestanas(contenedor, alCambiar) {
  const botones = contenedor.querySelectorAll('.pestana');
  botones.forEach((boton) => {
    boton.addEventListener('click', () => {
      botones.forEach((b) => b.setAttribute('aria-selected', String(b === boton)));
      alCambiar(boton.dataset.pestana);
    });
  });
}

/** Mensaje temporal abajo de la pantalla (éxito o error). */
export function notificar(texto, tipo = 'exito') {
  let caja = document.getElementById('notificacion');
  if (!caja) {
    caja = document.createElement('div');
    caja.id = 'notificacion';
    caja.setAttribute('role', 'status');
    caja.setAttribute('aria-live', 'polite');
    document.body.appendChild(caja);
  }
  caja.className = `notificacion notificacion-${tipo}`;
  caja.textContent = texto;
  caja.hidden = false;
  clearTimeout(caja.temporizador);
  caja.temporizador = setTimeout(() => { caja.hidden = true; }, 5000);
}

/**
 * Pone en `elemento` un bloque de error con el botón "Intentar de nuevo",
 * que vuelve a llamar a `reintentar` (normalmente la función cargar() de la pantalla).
 */
export function mostrarError(elemento, mensaje, reintentar) {
  elemento.innerHTML = `<div class="mensaje mensaje-error" role="alert"><strong>No se pudo cargar</strong>
    <span>${escapar(mensaje)}</span>
    <span><button class="boton boton-secundario" type="button" data-reintentar>Intentar de nuevo</button></span></div>`;
  const boton = elemento.querySelector('[data-reintentar]');
  boton.addEventListener('click', () => {
    boton.disabled = true;
    boton.textContent = 'Cargando…';
    reintentar();
  });
}

/**
 * Llama a `accion` cada `milisegundos` mientras la pestaña del navegador esté a la vista
 * (si está oculta no gasta peticiones, y al volver a ella actualiza enseguida).
 * Nunca lanza dos llamadas a la vez. Devuelve la función que detiene la repetición.
 */
export function repetirCada(milisegundos, accion) {
  let ocupado = false;
  async function ejecutar() {
    if (ocupado || document.hidden) {
      return;
    }
    ocupado = true;
    try {
      await accion();
    } finally {
      ocupado = false;
    }
  }
  const temporizador = setInterval(ejecutar, milisegundos);
  const alCambiarVisibilidad = () => { if (!document.hidden) { ejecutar(); } };
  document.addEventListener('visibilitychange', alCambiarVisibilidad);
  return () => {
    clearInterval(temporizador);
    document.removeEventListener('visibilitychange', alCambiarVisibilidad);
  };
}

/** Cada cuánto se actualizan solas las pantallas que muestran datos de otras sedes. */
export const ACTUALIZAR_CADA_MS = 30000;
