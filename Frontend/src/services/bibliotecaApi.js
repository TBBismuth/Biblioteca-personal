let backendBaseUrl = ''

export function configurarBackendBaseUrl(baseUrl = '') {
  if (!baseUrl) {
    backendBaseUrl = ''
    return
  }

  const url = new URL(baseUrl)
  if (url.protocol !== 'http:' || url.hostname !== '127.0.0.1' || url.pathname !== '/') {
    throw new Error('El servicio interno devolvió una dirección no válida.')
  }
  backendBaseUrl = url.origin
}

function apiUrl(ruta) {
  return `${backendBaseUrl}/api${ruta}`
}

async function solicitar(ruta, opciones) {
  let respuesta

  try {
    respuesta = await fetch(apiUrl(ruta), opciones)
  } catch {
    throw new Error('No se puede contactar con el backend. Comprueba que está iniciado.')
  }

  if (!respuesta.ok) {
    let detalle = ''
    let resultadoParcial = null

    try {
      const cuerpo = await respuesta.json()
      detalle = typeof cuerpo.message === 'string' ? cuerpo.message : ''
      resultadoParcial = cuerpo.resultadoParcial && typeof cuerpo.resultadoParcial === 'object'
        ? cuerpo.resultadoParcial
        : null
    } catch {
      // El backend puede responder sin un cuerpo JSON.
    }

    const error = new Error(detalle)
    error.status = respuesta.status
    error.resultadoParcial = resultadoParcial
    throw error
  }

  return respuesta.json()
}

export function obtenerConfiguracion() {
  return solicitar('/configuracion')
}

export function guardarRuta(ruta) {
  return solicitar('/configuracion/ruta', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ ruta }),
  })
}

export function escanearBiblioteca() {
  return solicitar('/escaneo', {
    method: 'POST',
  })
}

export function obtenerLibros({
  busqueda = '',
  estado = 'TODOS',
  pagina = 0,
  tamano = 50,
  signal,
} = {}) {
  const parametros = new URLSearchParams()

  if (busqueda.trim()) {
    parametros.set('busqueda', busqueda.trim())
  }

  parametros.set('estado', estado)
  parametros.set('pagina', String(pagina))
  parametros.set('tamano', String(tamano))

  return solicitar(`/libros?${parametros.toString()}`, { signal })
}

export function obtenerResumenBiblioteca({ signal } = {}) {
  return solicitar('/libros/resumen', { signal })
}

export function cambiarEstadoLectura(id, leido) {
  return solicitar(`/libros/${id}/leido`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ leido }),
  })
}

export function obtenerCopiasLibro(id) {
  return solicitar(`/libros/${id}/copias`)
}

export function actualizarLibro(id, datos) {
  return solicitar(`/libros/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      titulo: datos.titulo,
      autores: datos.autores,
    }),
  })
}

export function eliminarCopiaLibro(idLibro, idArchivo) {
  return solicitar(`/libros/${idLibro}/copias/${idArchivo}`, {
    method: 'DELETE',
  })
}

export function eliminarTodasCopiasLibro(idLibro) {
  return solicitar(`/libros/${idLibro}/copias`, {
    method: 'DELETE',
  })
}
