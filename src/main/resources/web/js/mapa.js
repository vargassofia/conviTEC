// Mapa de rutas con Leaflet (window.L, cargado desde vendor/leaflet).
//
// Solo DIBUJA: las ubicaciones, rutas, tiempos y caminos vienen de la API (/api/grafo, /api/entregas,
// /api/ruta), que los calcula en Java con el grafo y Dijkstra. Las rutas se dibujan como líneas rectas
// entre ubicaciones, igual que en el grafo (no siguen las carreteras reales).

import { COLORES_SEDE, escapar, textoMinutos } from './util.js';

// Baldosas de OpenStreetMap: gratis y sin clave (solo piden mostrar la atribución y no abusar del servicio).
// En el CSS se les baja el color (.leaflet-tile-pane) para que resalten nuestras rutas y marcadores.
const BALDOSAS = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const ATRIBUCION = '&copy; <a href="https://www.openstreetmap.org/copyright">colaboradores de OpenStreetMap</a>';

// Dónde poner el nombre de cada lugar para que no se encimen (por defecto, a la derecha).
const POSICION_ETIQUETA = {
  'alajuela': 'left',
  'heredia': 'top',
  'san jose': 'bottom',
  'cartago': 'bottom',
  'paraiso': 'right',
  'orotina': 'bottom',
  'puntarenas': 'left',
  'liberia': 'left',
};

const COLOR_RUTA = '#8a97a8';
const COLOR_CONSULTA = '#f59e0b';

function clave(nombre) {
  return String(nombre || '').normalize('NFD').replace(/\p{M}/gu, '').trim().replace(/\s+/g, ' ').toLowerCase();
}

const ICONO_VEHICULO = `
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M2 6h12v10H2z"/><path d="M14 10h4l3 3v3h-7"/><circle cx="6" cy="18" r="2"/><circle cx="17" cy="18" r="2"/>
  </svg>`;

export class MapaConviTEC {
  /**
   * @param {HTMLElement} elemento contenedor del mapa
   * @param {object} datos respuesta de /api/grafo
   * @param {string} codigoSede código de esta sede (para el color)
   */
  constructor(elemento, datos, codigoSede) {
    this.datos = datos;
    this.color = COLORES_SEDE[codigoSede] || '#0f3d5e';
    this.coordenadas = {};
    datos.ubicaciones.forEach((u) => {
      this.coordenadas[clave(u.nombre)] = [u.lat, u.lng];
    });

    this.mapa = L.map(elemento, {
      zoomControl: true,
      zoomSnap: 0.25, // permite zoom intermedio, para que Costa Rica llene mejor el recuadro
      zoomDelta: 0.5,
      fadeAnimation: false, // las baldosas aparecen de una vez (más liviano en computadoras lentas)
      scrollWheelZoom: false, // así la rueda del mouse mueve la página y no el mapa por accidente
      attributionControl: true,
    });
    this.mapa.attributionControl.setPrefix('<a href="https://leafletjs.com">Leaflet</a>');
    this.agregarFondo(elemento);

    this.capaRutas = L.layerGroup().addTo(this.mapa);
    this.capaZonas = L.layerGroup().addTo(this.mapa);
    this.capaPendientes = L.layerGroup().addTo(this.mapa);
    this.capaRecorrido = L.layerGroup().addTo(this.mapa);
    this.capaConsulta = L.layerGroup().addTo(this.mapa);
    this.capaUbicaciones = L.layerGroup().addTo(this.mapa);
    this.capaVehiculo = L.layerGroup().addTo(this.mapa);

    this.dibujarZonasAisladas();
    this.dibujarRutas();
    this.dibujarUbicaciones();
    this.verTodo();

    // Al activar el mapa con un clic, la rueda del mouse sí hace zoom
    this.mapa.on('click', () => this.mapa.scrollWheelZoom.enable());
    this.mapa.on('mouseout', () => this.mapa.scrollWheelZoom.disable());
  }

  /** Coordenadas [lat, lng] de una ubicación, o null si no se conocen. */
  punto(nombre) {
    return this.coordenadas[clave(nombre)] || null;
  }

  /** Ajusta la vista para que se vean todas las ubicaciones. */
  verTodo() {
    const puntos = Object.values(this.coordenadas);
    if (puntos.length > 0) {
      this.mapa.fitBounds(L.latLngBounds(puntos), { padding: [40, 40] });
    } else {
      this.mapa.setView([9.9, -84.1], 8); // Costa Rica
    }
  }

  /** Recalcula el tamaño (cuando el contenedor cambia de tamaño). */
  ajustarTamano() {
    this.mapa.invalidateSize();
  }

  destruir() {
    this.mapa.remove();
  }

  // -------------------------------------------------------------------------
  // Fondo del mapa y modo sin conexión
  // -------------------------------------------------------------------------

  agregarFondo(elemento) {
    const baldosas = L.tileLayer(BALDOSAS, {
      attribution: ATRIBUCION,
      maxZoom: 18,
      minZoom: 6,
    }).addTo(this.mapa);

    let cargadas = 0;
    let fallidas = 0;
    baldosas.on('tileload', () => {
      cargadas++;
      elemento.classList.remove('mapa-sin-conexion');
    });
    baldosas.on('tileerror', () => {
      fallidas++;
      if (cargadas === 0 && fallidas >= 3) {
        elemento.classList.add('mapa-sin-conexion');
      }
    });
  }

  // -------------------------------------------------------------------------
  // Dibujo base: rutas, ubicaciones y zonas aisladas
  // -------------------------------------------------------------------------

  dibujarRutas() {
    const alcanzable = {};
    this.datos.ubicaciones.forEach((u) => { alcanzable[clave(u.nombre)] = u.alcanzable; });

    this.datos.rutas.forEach((r) => {
      const a = this.punto(r.origen);
      const b = this.punto(r.destino);
      if (!a || !b) {
        return;
      }
      const usable = alcanzable[clave(r.origen)] && alcanzable[clave(r.destino)];
      L.polyline([a, b], {
        color: COLOR_RUTA,
        weight: 3,
        opacity: usable ? 0.85 : 0.5,
        dashArray: usable ? null : '6 6',
      })
        .bindTooltip(`<strong>${escapar(r.id)}</strong> · ${escapar(r.origen)} – ${escapar(r.destino)}`
          + `<br>${escapar(textoMinutos(r.tiempo))}`, { sticky: true })
        .addTo(this.capaRutas);
    });
  }

  dibujarUbicaciones() {
    this.datos.ubicaciones.forEach((u) => {
      const punto = [u.lat, u.lng];
      let marcador;
      if (u.codigoSede) {
        const color = COLORES_SEDE[u.codigoSede] || '#0f3d5e';
        const clase = u.esEstaSede ? 'marcador-sede marcador-sede-actual' : 'marcador-sede';
        marcador = L.marker(punto, {
          icon: L.divIcon({
            className: '',
            html: `<span class="${clase}" style="--color:${color}">${escapar(u.codigoSede)}</span>`,
            iconSize: u.esEstaSede ? [54, 30] : [44, 24],
            iconAnchor: u.esEstaSede ? [27, 15] : [22, 12],
          }),
          zIndexOffset: u.esEstaSede ? 1000 : 500,
          keyboard: false,
        });
      } else {
        marcador = L.circleMarker(punto, {
          radius: 7,
          color: u.alcanzable ? '#1b2430' : '#9aa5b1',
          weight: 2,
          fillColor: '#ffffff',
          fillOpacity: 1,
          dashArray: u.alcanzable ? null : '3 3',
        });
      }

      const detalle = u.esEstaSede
        ? 'Esta sede'
        : u.codigoSede
          ? `Sede ${escapar(u.codigoSede)}`
          : u.alcanzable ? 'Punto de entrega' : 'Fuera del alcance de esta sede';
      marcador
        .bindTooltip(`<strong>${escapar(u.nombre)}</strong>`, {
          permanent: true,
          direction: POSICION_ETIQUETA[clave(u.nombre)] || 'right',
          offset: this.desplazamientoEtiqueta(POSICION_ETIQUETA[clave(u.nombre)] || 'right', !!u.codigoSede),
          className: u.alcanzable ? 'etiqueta-lugar' : 'etiqueta-lugar etiqueta-lugar-apagada',
        })
        .bindPopup(`<strong>${escapar(u.nombre)}</strong><br>${detalle}`)
        .addTo(this.capaUbicaciones);
    });
  }

  desplazamientoEtiqueta(direccion, esSede) {
    const d = esSede ? 22 : 8;
    switch (direccion) {
      case 'left': return [-d, 0];
      case 'top': return [0, -(esSede ? 12 : 8)];
      case 'bottom': return [0, esSede ? 12 : 8];
      default: return [d, 0];
    }
  }

  /** Encierra con una línea punteada las partes del mapa que no se conectan con la principal. */
  dibujarZonasAisladas() {
    const porComponente = {};
    this.datos.ubicaciones.forEach((u) => {
      (porComponente[u.componente] = porComponente[u.componente] || []).push([u.lat, u.lng]);
    });
    const grupos = Object.values(porComponente);
    if (grupos.length < 2) {
      return;
    }
    // La parte principal es la que tiene más ubicaciones; las demás son "zonas aisladas"
    let principal = grupos[0];
    grupos.forEach((g) => { if (g.length > principal.length) { principal = g; } });

    grupos.forEach((g) => {
      if (g === principal) {
        return;
      }
      const limites = L.latLngBounds(g).pad(0.45);
      L.rectangle(limites, {
        color: '#64748b',
        weight: 2,
        dashArray: '8 6',
        fillColor: '#64748b',
        fillOpacity: 0.06,
        interactive: false,
      }).addTo(this.capaZonas);
      L.marker(limites.getNorthWest(), {
        icon: L.divIcon({
          className: '',
          html: '<span class="etiqueta-zona">Zona aislada · sin rutas al resto</span>',
          iconSize: null,
          iconAnchor: [0, 26], // encima del recuadro, sin tapar los marcadores
        }),
        interactive: false,
        keyboard: false,
      }).addTo(this.capaZonas);
    });
  }

  // -------------------------------------------------------------------------
  // Entregas
  // -------------------------------------------------------------------------

  /**
   * Dibuja el estado de la salida: caminos recorridos, número de cada parada, destinos pendientes
   * y el vehículo en su ubicación actual.
   */
  mostrarEntregas(estado) {
    this.capaRecorrido.clearLayers();
    this.capaPendientes.clearLayers();
    this.capaVehiculo.clearLayers();

    // Destinos que faltan: anillo naranja
    const yaMarcados = {};
    (estado.pendientes || []).forEach((p) => {
      const punto = this.punto(p.destino);
      if (!punto || yaMarcados[clave(p.destino)]) {
        return;
      }
      yaMarcados[clave(p.destino)] = true;
      L.circleMarker(punto, {
        radius: 15,
        color: COLOR_CONSULTA,
        weight: 3,
        fillColor: COLOR_CONSULTA,
        fillOpacity: 0.15,
        className: 'anillo-pendiente',
        interactive: false,
      }).addTo(this.capaPendientes);
    });

    // Caminos de las paradas hechas; la última, más marcada
    const paradas = estado.paradas || [];
    paradas.forEach((parada, i) => {
      const esUltima = i === paradas.length - 1;
      const puntos = parada.camino.map((n) => this.punto(n)).filter(Boolean);
      if (puntos.length >= 2) {
        L.polyline(puntos, {
          color: this.color,
          weight: esUltima ? 7 : 5,
          opacity: esUltima ? 0.95 : 0.55,
          className: esUltima ? 'tramo-actual' : '',
        }).bindTooltip(`Parada ${parada.numero}: ${escapar(parada.desde)} → ${escapar(parada.destino)}`
          + `<br>${escapar(textoMinutos(parada.tiempoTramo))} (acumulado ${escapar(textoMinutos(parada.tiempoAcumulado))})`,
        { sticky: true }).addTo(this.capaRecorrido);
      }
      const destino = this.punto(parada.destino);
      if (destino) {
        L.marker(destino, {
          icon: L.divIcon({
            className: '',
            html: `<span class="marcador-parada" style="--color:${this.color}">${parada.numero}</span>`,
            iconSize: [26, 26],
            iconAnchor: [34, 30], // arriba a la izquierda del punto, para no quedar debajo del vehículo
          }),
          zIndexOffset: 1500,
          keyboard: false,
        }).bindTooltip(`Parada ${parada.numero}: ${escapar(parada.idsPedidos.join(', '))}`)
          .addTo(this.capaRecorrido);
      }
    });

    // Vehículo (solo cuando hay una salida en curso o terminada)
    const actual = estado.iniciado ? this.punto(estado.ubicacionActual) : null;
    if (actual) {
      L.marker(actual, {
        icon: L.divIcon({
          className: '',
          html: `<span class="marcador-vehiculo" style="--color:${this.color}">${ICONO_VEHICULO}</span>`,
          iconSize: [38, 38],
          iconAnchor: [19, 46],
        }),
        zIndexOffset: 2000,
        keyboard: false,
      }).bindTooltip(`Vehículo en ${escapar(estado.ubicacionActual)}`).addTo(this.capaVehiculo);
    }

    // Enfocar lo que importa de la salida: la sede, lo recorrido y los destinos que faltan
    if (estado.iniciado) {
      const puntos = [];
      const agregar = (nombre) => { const x = this.punto(nombre); if (x) { puntos.push(x); } };
      agregar(estado.sede);
      agregar(estado.ubicacionActual);
      paradas.forEach((p) => p.camino.forEach(agregar));
      (estado.pendientes || []).forEach((p) => agregar(p.destino));
      if (puntos.length >= 2) {
        this.mapa.flyToBounds(L.latLngBounds(puntos), { padding: [60, 60], maxZoom: 9.5, duration: 0.8 });
      }
    }
  }

  /** Dibuja una ruta consultada (línea naranja punteada) y la enfoca. */
  mostrarRuta(camino) {
    this.capaConsulta.clearLayers();
    const puntos = (camino || []).map((n) => this.punto(n)).filter(Boolean);
    if (puntos.length < 2) {
      return;
    }
    L.polyline(puntos, { color: COLOR_CONSULTA, weight: 6, opacity: 0.95, dashArray: '10 8' })
      .addTo(this.capaConsulta);
    this.mapa.flyToBounds(L.latLngBounds(puntos), { padding: [60, 60], maxZoom: 10, duration: 0.8 });
  }

  limpiarRuta() {
    this.capaConsulta.clearLayers();
  }
}
