// Comunicación con la API REST de ESTA sede.
// Todas las respuestas de error del servidor tienen la forma { estado, error }.
//
// - Todas las peticiones llevan "X-ConviTEC: 1": el servidor rechaza los POST que no lo traen,
//   así otra página web no puede hacer pedidos a nombre de quien tiene la sesión abierta.
// - Las actualizaciones automáticas (cada 30 s) llevan además "X-ConviTEC-Automatica: 1": no cuentan
//   como actividad, para que la sesión se cierre a los 30 minutos aunque la página quede abierta.
// - Si una petición responde 401 (la sesión terminó), se avisa con el evento "convitec:sin-sesion"
//   y la página muestra la pantalla de inicio de sesión (ver app.js).

export class ErrorApi extends Error {
  constructor(mensaje, estado) {
    super(mensaje);
    this.name = 'ErrorApi';
    this.estado = estado; // 0 = no hubo conexión con el servidor
  }
}

const SIN_CONEXION =
  'No se pudo conectar con el servidor de la sede. Revise que la sede siga abierta e intente de nuevo.';

/** Momento de la última petición hecha por la persona (no automática). */
let ultimaPeticionManual = Date.now();

/** Milisegundos desde la última petición que renovó la sesión en el servidor. */
export function tiempoDesdeUltimaPeticion() {
  return Date.now() - ultimaPeticionManual;
}

async function pedir(ruta, opciones = {}, automatica = false) {
  const encabezados = { Accept: 'application/json', 'X-ConviTEC': '1', ...(opciones.headers || {}) };
  if (automatica) {
    encabezados['X-ConviTEC-Automatica'] = '1';
  }
  let respuesta;
  try {
    respuesta = await fetch(ruta, { ...opciones, headers: encabezados, credentials: 'same-origin' });
  } catch {
    throw new ErrorApi(SIN_CONEXION, 0);
  }
  if (!automatica) {
    ultimaPeticionManual = Date.now();
  }

  let datos = null;
  try {
    datos = await respuesta.json();
  } catch {
    // Respuesta sin JSON: se maneja abajo
  }

  if (!respuesta.ok) {
    const mensaje = datos && datos.error ? datos.error : `El servidor respondió con el error ${respuesta.status}.`;
    // En /api/acceso/... un 401 es "contraseña incorrecta", no "sesión terminada"
    if (respuesta.status === 401 && !ruta.startsWith('/api/acceso')) {
      window.dispatchEvent(new CustomEvent('convitec:sin-sesion', { detail: mensaje }));
    }
    throw new ErrorApi(mensaje, respuesta.status);
  }
  return datos;
}

/**
 * GET que devuelve el JSON de la respuesta.
 * @param {{automatica?: boolean}} opciones automatica = actualización que la persona no pidió
 */
export function obtener(ruta, { automatica = false } = {}) {
  return pedir(ruta, {}, automatica);
}

/** POST con un cuerpo JSON. */
export function enviar(ruta, cuerpo) {
  return pedir(ruta, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(cuerpo),
  });
}
