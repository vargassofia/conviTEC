// Pantalla de acceso: elegir la sede, iniciar sesión, configurar la sede (primera cuenta)
// y recuperar el acceso con el código que muestra el programa de la sede.
//
// Cada sede es un programa aparte con su propia dirección (8081 a 8084) y sus propias cuentas:
// elegir otra sede abre la página de esa sede, donde se inicia sesión con una cuenta de allá.

import { enviar } from './api.js';
import { COLORES_SEDE, escapar } from './util.js';

const MINIMO_CLAVE = 8;

/**
 * Muestra la pantalla de acceso en `contenedor`.
 * @param estado    respuesta de GET /api/acceso
 * @param aviso     mensaje opcional arriba del formulario (por ejemplo, "Su sesión terminó")
 * @param alEntrar  se llama cuando la persona ya tiene sesión
 */
export function montarAcceso(contenedor, estado, { aviso = '', alEntrar }) {
  const sede = estado.sede;
  let modo = estado.necesitaInstalacion ? 'instalar' : 'entrar';
  let mensajeAviso = aviso;

  function direccionDe(s) {
    return `${window.location.protocol}//${window.location.hostname}:${s.puerto}/`;
  }

  function selectorSedes() {
    return `
      <fieldset class="selector-sedes">
        <legend>¿En qué sede va a trabajar?</legend>
        <div class="opciones-sede">${estado.sedes.map((s) => {
    const contenido = `
            <span class="sede-insignia" style="background:${COLORES_SEDE[s.codigo] || 'var(--marca)'}">${escapar(s.codigo)}</span>
            <span class="opcion-sede-texto"><strong>${escapar(s.nombre)}</strong>
              <span>${s.esEstaSede ? 'Esta sede' : 'Abrir su página'}</span></span>`;
    if (s.esEstaSede) {
      return `<span class="opcion-sede opcion-sede-actual" data-sede-opcion="${escapar(s.codigo)}" aria-current="true">${contenido}</span>`;
    }
    if (!s.puerto) {
      return `<span class="opcion-sede opcion-sede-inactiva" data-sede-opcion="${escapar(s.codigo)}">${contenido}</span>`;
    }
    return `<a class="opcion-sede" data-sede-opcion="${escapar(s.codigo)}" href="${escapar(direccionDe(s))}">${contenido}</a>`;
  }).join('')}</div>
        <p class="ayuda">Cada sede tiene sus propias cuentas. Si elige otra sede, se abrirá su página para iniciar sesión allá.</p>
      </fieldset>`;
  }

  function campoClave(id, etiqueta, autocompletar) {
    return `
      <label class="campo">${escapar(etiqueta)}
        <span class="campo-clave">
          <input type="password" id="${id}" autocomplete="${autocompletar}" required minlength="${MINIMO_CLAVE}" maxlength="128">
          <button type="button" class="boton-enlace" data-mostrar="${id}" aria-pressed="false">Mostrar</button>
        </span>
      </label>`;
  }

  function formularioEntrar() {
    return `
      <h1 id="titulo-acceso">Iniciar sesión</h1>
      <p class="ayuda">Sede <strong>${escapar(sede.nombre)}</strong>. Escriba su usuario y su contraseña.</p>
      <form id="form-acceso" class="form-acceso" novalidate>
        <label class="campo">Usuario
          <input type="text" id="acceso-usuario" autocomplete="username" autocapitalize="none" spellcheck="false" required maxlength="30">
        </label>
        ${campoClave('acceso-clave', 'Contraseña', 'current-password')}
        <p class="error-campo" id="error-acceso" role="alert" hidden></p>
        <button class="boton boton-grande boton-ancho" type="submit" id="boton-acceso">Entrar</button>
      </form>
      <button type="button" class="boton-enlace enlace-centrado" id="ir-recuperar">¿No puede entrar?</button>`;
  }

  function formularioCodigo(instalar) {
    return `
      <h1 id="titulo-acceso">${instalar ? `Configurar la sede ${escapar(sede.nombre)}` : 'Recuperar el acceso'}</h1>
      ${instalar ? `
        <p>Esta sede todavía no tiene cuentas. Cree la primera; después, desde la sección <strong>Cuentas</strong>, podrá crear las de las demás personas.</p>`
    : `
        <p>Si otra persona de la sede puede entrar, pídale que le cambie la contraseña en la sección <strong>Cuentas</strong>.</p>
        <p>Si nadie puede entrar, use un código de acceso: en la ventana donde corre el programa de la sede, escriba <code>codigo-acceso</code> y presione Enter.</p>`}
      <div class="pasos-codigo">
        <span class="paso-numero" aria-hidden="true">1</span>
        <p><strong>Código de acceso.</strong> ${instalar
    ? 'Aparece en la ventana donde se inició el programa de la sede (por ejemplo, <code>4829-1375</code>).'
    : 'Es de un solo uso y vence en 60 minutos.'}</p>
      </div>
      <form id="form-acceso" class="form-acceso" novalidate>
        <label class="campo">Código de acceso
          <input type="text" id="acceso-codigo" inputmode="numeric" autocomplete="one-time-code" placeholder="0000-0000" required maxlength="12">
        </label>
        <div class="pasos-codigo">
          <span class="paso-numero" aria-hidden="true">2</span>
          <p><strong>${instalar ? 'Su cuenta.' : 'La cuenta.'}</strong> ${instalar ? ''
    : 'Escriba el usuario que olvidó para ponerle una contraseña nueva, o un usuario nuevo para crear otra cuenta.'}</p>
        </div>
        <label class="campo">Nombre completo${instalar ? '' : ' <span class="opcional">(solo si es una cuenta nueva)</span>'}
          <input type="text" id="acceso-nombre" autocomplete="name" maxlength="60" ${instalar ? 'required' : ''}>
        </label>
        <label class="campo">Usuario
          <input type="text" id="acceso-usuario" autocomplete="username" autocapitalize="none" spellcheck="false" required maxlength="30"
            aria-describedby="ayuda-usuario">
          <span class="ayuda" id="ayuda-usuario">Sin espacios ni tildes, por ejemplo: <code>ana.mora</code></span>
        </label>
        ${campoClave('acceso-clave', instalar ? 'Contraseña' : 'Contraseña nueva', 'new-password')}
        ${campoClave('acceso-repetir', 'Repita la contraseña', 'new-password')}
        <p class="ayuda">Al menos ${MINIMO_CLAVE} caracteres. Mejor si es una frase fácil de recordar para usted.</p>
        <p class="error-campo" id="error-acceso" role="alert" hidden></p>
        <button class="boton boton-grande boton-ancho" type="submit" id="boton-acceso">${instalar ? 'Crear cuenta y entrar' : 'Guardar y entrar'}</button>
      </form>
      ${instalar ? '' : '<button type="button" class="boton-enlace enlace-centrado" id="ir-entrar">Volver a iniciar sesión</button>'}`;
  }

  function pintar() {
    contenedor.innerHTML = `
      <div class="acceso-pagina">
        <div class="acceso-marca">
          <img src="img/icono.svg" alt="" width="44" height="44">
          <span>ConviTEC</span>
        </div>
        <section class="tarjeta acceso-tarjeta" aria-labelledby="titulo-acceso">
          ${mensajeAviso ? `<p class="mensaje mensaje-info acceso-aviso" role="status">${escapar(mensajeAviso)}</p>` : ''}
          ${modo === 'instalar' ? '' : selectorSedes()}
          ${modo === 'entrar' ? formularioEntrar() : formularioCodigo(modo === 'instalar')}
        </section>
        <p class="acceso-pie">Sistema de pedidos de las sedes ConviTEC</p>
      </div>`;
    activar();
    const primero = contenedor.querySelector(modo === 'entrar' ? '#acceso-usuario' : '#acceso-codigo');
    if (primero) {
      primero.focus();
    }
  }

  function mostrarError(texto) {
    const caja = contenedor.querySelector('#error-acceso');
    caja.textContent = texto;
    caja.hidden = false;
  }

  function valor(id) {
    const campo = contenedor.querySelector(`#${id}`);
    return campo ? campo.value : '';
  }

  function activar() {
    contenedor.querySelectorAll('[data-mostrar]').forEach((boton) => {
      boton.addEventListener('click', () => {
        const campo = contenedor.querySelector(`#${boton.dataset.mostrar}`);
        const visible = campo.type === 'password';
        campo.type = visible ? 'text' : 'password';
        boton.textContent = visible ? 'Ocultar' : 'Mostrar';
        boton.setAttribute('aria-pressed', String(visible));
      });
    });
    const irRecuperar = contenedor.querySelector('#ir-recuperar');
    if (irRecuperar) {
      irRecuperar.addEventListener('click', () => { modo = 'recuperar'; mensajeAviso = ''; pintar(); });
    }
    const irEntrar = contenedor.querySelector('#ir-entrar');
    if (irEntrar) {
      irEntrar.addEventListener('click', () => { modo = 'entrar'; mensajeAviso = ''; pintar(); });
    }
    contenedor.querySelector('#form-acceso').addEventListener('submit', enviarFormulario);
  }

  /** Revisa en la página lo que se puede revisar antes de ir al servidor. Devuelve el error o ''. */
  function validar() {
    if (modo === 'entrar') {
      if (!valor('acceso-usuario').trim() || !valor('acceso-clave')) {
        return 'Escriba su usuario y su contraseña.';
      }
      return '';
    }
    if (!valor('acceso-codigo').trim()) {
      return 'Escriba el código de acceso.';
    }
    if (modo === 'instalar' && !valor('acceso-nombre').trim()) {
      return 'Escriba su nombre completo.';
    }
    if (!valor('acceso-usuario').trim()) {
      return 'Escriba el usuario.';
    }
    if (valor('acceso-clave').length < MINIMO_CLAVE) {
      return `La contraseña debe tener al menos ${MINIMO_CLAVE} caracteres.`;
    }
    if (valor('acceso-clave') !== valor('acceso-repetir')) {
      return 'Las dos contraseñas no son iguales. Escríbalas de nuevo.';
    }
    return '';
  }

  async function enviarFormulario(evento) {
    evento.preventDefault();
    const error = validar();
    if (error) {
      mostrarError(error);
      return;
    }
    const boton = contenedor.querySelector('#boton-acceso');
    const textoBoton = boton.textContent;
    boton.disabled = true;
    boton.textContent = 'Revisando…';
    try {
      const usuario = modo === 'entrar'
        ? await enviar('/api/acceso/entrar', { usuario: valor('acceso-usuario'), clave: valor('acceso-clave') })
        : await enviar('/api/acceso/codigo', {
          codigo: valor('acceso-codigo'),
          nombre: valor('acceso-nombre'),
          usuario: valor('acceso-usuario'),
          clave: valor('acceso-clave'),
        });
      alEntrar(usuario);
    } catch (e) {
      mostrarError(e.message);
      const clave = contenedor.querySelector('#acceso-clave');
      if (clave && modo === 'entrar') {
        clave.value = '';
        clave.focus();
      }
      boton.disabled = false;
      boton.textContent = textoBoton;
    }
  }

  document.documentElement.dataset.sede = sede.codigo;
  document.title = `ConviTEC · ${sede.nombre} · Iniciar sesión`;
  pintar();
}
