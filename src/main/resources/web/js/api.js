// Comunicación con la API REST de ESTA sede.
// Todas las respuestas de error del servidor tienen la forma { estado, error }.

export class ErrorApi extends Error {
  constructor(mensaje, estado) {
    super(mensaje);
    this.name = 'ErrorApi';
    this.estado = estado; // 0 = no hubo conexión con el servidor
  }
}

const SIN_CONEXION =
  'No se pudo conectar con el servidor de la sede. Revise que la sede siga abierta e intente de nuevo.';

async function pedir(ruta, opciones = {}) {
  let respuesta;
  try {
    respuesta = await fetch(ruta, {
      ...opciones,
      headers: { Accept: 'application/json', ...(opciones.headers || {}) },
    });
  } catch {
    throw new ErrorApi(SIN_CONEXION, 0);
  }

  let datos = null;
  try {
    datos = await respuesta.json();
  } catch {
    // Respuesta sin JSON: se maneja abajo
  }

  if (!respuesta.ok) {
    const mensaje = datos && datos.error ? datos.error : `El servidor respondió con el error ${respuesta.status}.`;
    throw new ErrorApi(mensaje, respuesta.status);
  }
  return datos;
}

/** GET que devuelve el JSON de la respuesta. */
export function obtener(ruta) {
  return pedir(ruta);
}

/** POST con un cuerpo JSON. */
export function enviar(ruta, cuerpo) {
  return pedir(ruta, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify(cuerpo),
  });
}
