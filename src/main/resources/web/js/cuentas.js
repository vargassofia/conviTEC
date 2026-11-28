// Sección Cuentas: personas que pueden entrar a ESTA sede.
// No hay roles: cualquiera con cuenta puede crear cuentas, activarlas o desactivarlas y cambiar
// contraseñas, pero solo de esta sede, y siempre confirmando con su propia contraseña.

import { obtener, enviar } from './api.js';
import { escapar, fechaHora, mostrarError, notificar, plural } from './util.js';

const MINIMO_CLAVE = 8;

export function montarCuentas(contenedor, sede) {
  contenedor.insertAdjacentHTML('beforeend', `
    <div class="barra-herramientas">
      <p class="ayuda">Solo estas personas pueden entrar a la sede ${escapar(sede.nombre)}.</p>
      <button class="boton" type="button" id="nueva-cuenta">
        <svg class="icono" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>
        Nueva cuenta
      </button>
    </div>
    <section class="tarjeta formulario-agregar" id="panel-cuenta" hidden aria-live="polite"></section>
    <section class="tarjeta" id="lista-cuentas" aria-live="polite"><p class="cargando">Cargando…</p></section>`);

  const $ = (id) => contenedor.querySelector(`#${id}`);
  let cuentas = [];
  let activa = true;

  // ---------------------------------------------------------------------------
  // Lista
  // ---------------------------------------------------------------------------

  function tabla() {
    return `
      <p class="ayuda">${plural(cuentas.length, 'cuenta', 'cuentas')}. Las cuentas desactivadas no pueden entrar, pero se conservan.</p>
      <table class="tabla">
        <thead><tr>
          <th scope="col">Persona</th><th scope="col">Usuario</th><th scope="col">Estado</th>
          <th scope="col">Último ingreso</th><th scope="col">Creada por</th><th scope="col"><span class="sr-only">Acciones</span></th>
        </tr></thead>
        <tbody>${cuentas.map((c) => `
          <tr>
            <td data-titulo="Persona"><strong>${escapar(c.nombre)}</strong>${c.esUsted ? ' <span class="chip chip-usted">Usted</span>' : ''}</td>
            <td data-titulo="Usuario"><span class="codigo">${escapar(c.usuario)}</span></td>
            <td data-titulo="Estado">${c.activa
    ? '<span class="etiqueta-estado estado-entregado">Activa</span>'
    : '<span class="etiqueta-estado estado-rechazado">Desactivada</span>'}</td>
            <td data-titulo="Último ingreso">${c.ultimoIngreso ? escapar(fechaHora(c.ultimoIngreso)) : '<span class="desconocido">Nunca</span>'}</td>
            <td data-titulo="Creada por"><span>${escapar(c.creadaPor || '—')}${c.creadaEn ? `<span class="descripcion">${escapar(fechaHora(c.creadaEn))}</span>` : ''}</span></td>
            <td class="accion-fila"><div class="acciones-cuenta">
              <button class="boton-enlace" type="button" data-clave="${escapar(c.usuario)}">Cambiar contraseña</button>
              ${c.esUsted ? '' : `<button class="boton-enlace${c.activa ? ' enlace-peligro' : ''}" type="button"
                data-estado="${escapar(c.usuario)}" data-activar="${!c.activa}">${c.activa ? 'Desactivar' : 'Activar'}</button>`}
            </div></td>
          </tr>`).join('')}
        </tbody>
      </table>`;
  }

  async function cargar() {
    try {
      const lista = await obtener('/api/cuentas');
      if (!activa) {
        return;
      }
      cuentas = lista;
      $('lista-cuentas').innerHTML = tabla();
    } catch (error) {
      if (activa) {
        mostrarError($('lista-cuentas'), error.message, cargar);
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Formularios (uno a la vez, en el mismo panel)
  // ---------------------------------------------------------------------------

  function campoClave(id, etiqueta, autocompletar) {
    return `
      <label class="campo">${escapar(etiqueta)}
        <span class="campo-clave">
          <input type="password" id="${id}" autocomplete="${autocompletar}" maxlength="128">
          <button type="button" class="boton-enlace" data-mostrar="${id}" aria-pressed="false">Mostrar</button>
        </span>
      </label>`;
  }

  const CONFIRMACION = `
    <div class="confirmacion-clave">
      ${campoClave('cuenta-confirmacion', 'Su contraseña (para confirmar que es usted)', 'current-password')}
    </div>`;

  function abrirPanel(html, alEnviar, primerCampo) {
    const panel = $('panel-cuenta');
    panel.innerHTML = `
      <form id="form-cuenta" novalidate>
        ${html}
        <p class="error-campo" id="error-cuenta" role="alert" hidden></p>
        <div class="acciones-formulario">
          <button class="boton" type="submit" id="boton-cuenta">Guardar</button>
          <button class="boton boton-secundario" type="button" id="cancelar-cuenta">Cancelar</button>
        </div>
      </form>`;
    panel.hidden = false;
    panel.querySelectorAll('[data-mostrar]').forEach((boton) => {
      boton.addEventListener('click', () => {
        const campo = panel.querySelector(`#${boton.dataset.mostrar}`);
        const visible = campo.type === 'password';
        campo.type = visible ? 'text' : 'password';
        boton.textContent = visible ? 'Ocultar' : 'Mostrar';
        boton.setAttribute('aria-pressed', String(visible));
      });
    });
    $('cancelar-cuenta').addEventListener('click', cerrarPanel);
    $('form-cuenta').addEventListener('submit', async (evento) => {
      evento.preventDefault();
      const boton = $('boton-cuenta');
      boton.disabled = true;
      boton.textContent = 'Guardando…';
      try {
        const r = await alEnviar();
        if (r === null) {
          return; // error de validación ya mostrado
        }
        notificar(r.mensaje || 'Listo.');
        cerrarPanel();
        await cargar();
      } catch (error) {
        errorPanel(error.message);
        const confirmacion = $('cuenta-confirmacion');
        if (confirmacion && error.estado === 403) {
          confirmacion.value = '';
          confirmacion.focus();
        }
      } finally {
        if ($('boton-cuenta')) {
          $('boton-cuenta').disabled = false;
          $('boton-cuenta').textContent = 'Guardar';
        }
      }
    });
    panel.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    $(primerCampo).focus();
  }

  function cerrarPanel() {
    $('panel-cuenta').hidden = true;
    $('panel-cuenta').innerHTML = '';
  }

  function errorPanel(texto) {
    $('error-cuenta').textContent = texto;
    $('error-cuenta').hidden = false;
  }

  const valor = (id) => ($(id) ? $(id).value : '');

  /** Revisa la contraseña nueva y su repetición; muestra el error y devuelve false si algo falla. */
  function clavesValidas() {
    if (valor('cuenta-clave').length < MINIMO_CLAVE) {
      errorPanel(`La contraseña debe tener al menos ${MINIMO_CLAVE} caracteres.`);
      return false;
    }
    if (valor('cuenta-clave') !== valor('cuenta-repetir')) {
      errorPanel('Las dos contraseñas no son iguales. Escríbalas de nuevo.');
      return false;
    }
    if (!valor('cuenta-confirmacion')) {
      errorPanel('Escriba su propia contraseña para confirmar.');
      return false;
    }
    return true;
  }

  function nuevaCuenta() {
    abrirPanel(`
      <h2>Nueva cuenta para la sede ${escapar(sede.nombre)}</h2>
      <div class="campos-cuenta">
        <label class="campo">Nombre completo
          <input type="text" id="cuenta-nombre" autocomplete="off" maxlength="60">
        </label>
        <label class="campo">Usuario
          <input type="text" id="cuenta-usuario" autocomplete="off" autocapitalize="none" spellcheck="false" maxlength="30">
          <span class="ayuda">Sin espacios ni tildes, por ejemplo: <code>ana.mora</code></span>
        </label>
        ${campoClave('cuenta-clave', 'Contraseña', 'new-password')}
        ${campoClave('cuenta-repetir', 'Repita la contraseña', 'new-password')}
      </div>
      <p class="ayuda">Al menos ${MINIMO_CLAVE} caracteres. Entréguele la contraseña a la persona en privado.</p>
      ${CONFIRMACION}`, async () => {
      if (!valor('cuenta-nombre').trim() || !valor('cuenta-usuario').trim()) {
        errorPanel('Escriba el nombre y el usuario de la persona.');
        return null;
      }
      if (!clavesValidas()) {
        return null;
      }
      return enviar('/api/cuentas', {
        nombre: valor('cuenta-nombre'),
        usuario: valor('cuenta-usuario'),
        clave: valor('cuenta-clave'),
        claveConfirmacion: valor('cuenta-confirmacion'),
      });
    }, 'cuenta-nombre');
  }

  function cambiarClave(usuario) {
    const cuenta = cuentas.find((c) => c.usuario === usuario);
    if (!cuenta) {
      return;
    }
    abrirPanel(`
      <h2>${cuenta.esUsted ? 'Cambiar su contraseña' : `Cambiar la contraseña de ${escapar(cuenta.nombre)}`}</h2>
      <p class="ayuda">${cuenta.esUsted
    ? 'Su sesión en esta pantalla sigue abierta; las demás sesiones con su cuenta se cerrarán.'
    : 'Si tenía la sesión abierta en otra computadora, se cerrará y tendrá que entrar con la contraseña nueva.'}</p>
      <div class="campos-cuenta">
        ${campoClave('cuenta-clave', 'Contraseña nueva', 'new-password')}
        ${campoClave('cuenta-repetir', 'Repita la contraseña nueva', 'new-password')}
      </div>
      ${CONFIRMACION}`, async () => {
      if (!clavesValidas()) {
        return null;
      }
      return enviar('/api/cuentas/clave', {
        usuario, claveNueva: valor('cuenta-clave'), claveConfirmacion: valor('cuenta-confirmacion'),
      });
    }, 'cuenta-clave');
  }

  function cambiarEstado(usuario, activar) {
    const cuenta = cuentas.find((c) => c.usuario === usuario);
    if (!cuenta) {
      return;
    }
    abrirPanel(`
      <h2>${activar ? 'Activar' : 'Desactivar'} la cuenta de ${escapar(cuenta.nombre)}</h2>
      <p>${activar
    ? 'La persona podrá volver a entrar con su contraseña de siempre.'
    : 'La persona ya no podrá entrar y, si tiene la sesión abierta, se cerrará. La cuenta no se borra: se puede activar de nuevo cuando haga falta.'}</p>
      ${CONFIRMACION}`, async () => {
      if (!valor('cuenta-confirmacion')) {
        errorPanel('Escriba su propia contraseña para confirmar.');
        return null;
      }
      return enviar('/api/cuentas/estado', {
        usuario, activa: activar, claveConfirmacion: valor('cuenta-confirmacion'),
      });
    }, 'cuenta-confirmacion');
  }

  $('nueva-cuenta').addEventListener('click', nuevaCuenta);
  $('lista-cuentas').addEventListener('click', (evento) => {
    const botonClave = evento.target.closest('[data-clave]');
    if (botonClave) {
      cambiarClave(botonClave.dataset.clave);
      return;
    }
    const botonEstado = evento.target.closest('[data-estado]');
    if (botonEstado) {
      cambiarEstado(botonEstado.dataset.estado, botonEstado.dataset.activar === 'true');
    }
  });

  cargar();
  return () => { activa = false; };
}
