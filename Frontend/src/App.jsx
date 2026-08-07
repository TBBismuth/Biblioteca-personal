import { useCallback, useEffect, useState } from 'react'
import {
  escanearBiblioteca,
  guardarRuta,
  obtenerConfiguracion,
} from './services/bibliotecaApi.js'
import { seleccionarCarpetaLibros } from './services/selectorCarpeta.js'
import BibliotecaPage from './components/BibliotecaPage.jsx'
import './App.css'

const FASES_BLOQUEADAS = new Set(['cargando', 'seleccionando', 'guardando', 'escaneando'])

function mensajeError(error, operacion) {
  if (error?.message?.startsWith('No se puede contactar')) {
    return error.message
  }

  if (operacion === 'guardar' && error?.status >= 400 && error?.status < 500) {
    return error.message || 'El backend ha rechazado la carpeta seleccionada.'
  }

  if (operacion === 'guardar') {
    return 'No se pudo guardar la carpeta. Puedes volver a intentarlo.'
  }

  if (operacion === 'escanear') {
    return 'No se pudo escanear la biblioteca. Puedes volver a intentarlo.'
  }

  return 'No se pudo cargar la configuración. Puedes volver a intentarlo.'
}

function App() {
  const [fase, setFase] = useState('cargando')
  const [rutaLibros, setRutaLibros] = useState('')
  const [error, setError] = useState('')
  const [operacionFallida, setOperacionFallida] = useState(null)
  const [faseOrigenSeleccion, setFaseOrigenSeleccion] = useState('configuracion')

  const cargarConfiguracion = useCallback(async () => {
    setFase('cargando')
    setError('')
    setOperacionFallida(null)

    try {
      const configuracion = await obtenerConfiguracion()

      if (configuracion.configurada && configuracion.rutaAccesible) {
        setRutaLibros(configuracion.rutaLibros)
        setFase('principal')
      } else if (configuracion.configurada) {
        setRutaLibros(configuracion.rutaLibros)
        setFase('ruta-inaccesible')
      } else {
        setRutaLibros('')
        setFase('configuracion')
      }
    } catch (errorCarga) {
      setError(mensajeError(errorCarga, 'cargar'))
      setOperacionFallida('cargar')
      setFase('error')
    }
  }, [])

  useEffect(() => {
    cargarConfiguracion()
  }, [cargarConfiguracion])

  const ejecutarEscaneo = async () => {
    setFase('escaneando')
    setError('')
    setOperacionFallida(null)

    try {
      await escanearBiblioteca()
      setFase('principal')
    } catch (errorEscaneo) {
      setError(mensajeError(errorEscaneo, 'escanear'))
      setOperacionFallida('escanear')
      setFase('error')
    }
  }

  const guardarYEscanear = async (ruta) => {
    setFase('guardando')
    setError('')
    setOperacionFallida(null)

    try {
      const configuracion = await guardarRuta(ruta)
      setRutaLibros(configuracion.rutaLibros)
      await ejecutarEscaneo()
    } catch (errorGuardado) {
      setError(mensajeError(errorGuardado, 'guardar'))
      setOperacionFallida('guardar')
      setFase('error')
    }
  }

  const seleccionarCarpeta = async (faseCancelacion = 'configuracion') => {
    setFaseOrigenSeleccion(faseCancelacion)
    setFase('seleccionando')
    setError('')

    try {
      const ruta = await seleccionarCarpetaLibros()

      if (ruta === null) {
        setFase(faseCancelacion)
        return
      }

      setRutaLibros(ruta)
      await guardarYEscanear(ruta)
    } catch {
      setError('No se pudo abrir el selector de carpetas. Puedes volver a intentarlo.')
      setOperacionFallida('seleccionar')
      setFase('error')
    }
  }

  const reintentar = () => {
    if (operacionFallida === 'cargar') {
      cargarConfiguracion()
    } else if (operacionFallida === 'guardar') {
      guardarYEscanear(rutaLibros)
    } else if (operacionFallida === 'escanear') {
      ejecutarEscaneo()
    } else {
      seleccionarCarpeta(faseOrigenSeleccion)
    }
  }

  const bloqueada = FASES_BLOQUEADAS.has(fase)

  if (fase === 'principal') {
    return (
      <BibliotecaPage
        rutaLibros={rutaLibros}
        onConfiguracionActualizada={(configuracion) => {
          if (!configuracion.configurada) {
            setRutaLibros('')
            setFase('configuracion')
          } else if (!configuracion.rutaAccesible) {
            setRutaLibros(configuracion.rutaLibros)
            setFase('ruta-inaccesible')
          } else {
            setRutaLibros(configuracion.rutaLibros)
          }
        }}
      />
    )
  }

  return (
    <main className="app-shell" aria-busy={bloqueada}>
      <section className="panel">
        <h1>Biblioteca personal</h1>

        {fase === 'cargando' && <p className="estado">Cargando configuración...</p>}

        {(fase === 'configuracion'
          || (fase === 'seleccionando' && faseOrigenSeleccion === 'configuracion')) && (
          <>
            <p className="introduccion">
              Selecciona la carpeta principal donde guardas tus libros. También se
              revisarán automáticamente todas sus subcarpetas.
            </p>
            <button
              type="button"
              onClick={() => seleccionarCarpeta('configuracion')}
              disabled={bloqueada}
            >
              Seleccionar carpeta de libros
            </button>
          </>
        )}

        {(fase === 'ruta-inaccesible'
          || (fase === 'seleccionando' && faseOrigenSeleccion === 'ruta-inaccesible')) && (
          <>
            <p className="aviso-ruta" role="alert">
              No se encuentra la carpeta de libros configurada.
            </p>
            <p className="explicacion-ruta">
              La carpeta puede haberse movido, renombrado o encontrarse en una
              unidad desconectada.
            </p>
            <p className="ruta"><span>Ruta anterior:</span>{rutaLibros}</p>
            <button
              type="button"
              onClick={() => seleccionarCarpeta('ruta-inaccesible')}
              disabled={bloqueada}
            >
              Seleccionar nueva ubicación
            </button>
          </>
        )}

        {(fase === 'guardando' || fase === 'escaneando') && (
          <>
            <p className="ruta"><span>Carpeta seleccionada:</span>{rutaLibros}</p>
            <p className="estado" role="status">
              {fase === 'guardando' ? 'Guardando carpeta...' : 'Escaneando biblioteca...'}
            </p>
            <button type="button" disabled>Seleccionar carpeta de libros</button>
          </>
        )}

        {fase === 'error' && (
          <>
            {rutaLibros && <p className="ruta"><span>Carpeta seleccionada:</span>{rutaLibros}</p>}
            <p className="mensaje-error" role="alert">{error}</p>
            <button type="button" onClick={reintentar}>Volver a intentar</button>
          </>
        )}

      </section>
    </main>
  )
}

export default App
