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
