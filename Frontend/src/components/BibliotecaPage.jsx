import { useCallback, useEffect, useRef, useState } from 'react'
import {
  cambiarEstadoLectura,
  escanearBiblioteca,
  guardarRuta,
  obtenerConfiguracion,
  obtenerCopiasLibro,
  obtenerLibros,
  obtenerResumenBiblioteca,
} from '../services/bibliotecaApi.js'
import { seleccionarCarpetaLibros } from '../services/selectorCarpeta.js'
import AccionesBiblioteca from './AccionesBiblioteca.jsx'
import ConfirmacionCambioCarpeta from './ConfirmacionCambioCarpeta.jsx'
import ControlesBiblioteca from './ControlesBiblioteca.jsx'
import EditarLibroDialogo from './EditarLibroDialogo.jsx'
import Paginacion from './Paginacion.jsx'
import ResumenBiblioteca from './ResumenBiblioteca.jsx'
import ResumenEscaneo from './ResumenEscaneo.jsx'
import ResultadoRenombrado from './ResultadoRenombrado.jsx'
import TablaLibros from './TablaLibros.jsx'
import './BibliotecaPage.css'

const TAMANO_PAGINA = 50

function mensajeCarga(error, contenido) {
  if (error?.message?.startsWith('No se puede contactar')) return error.message
  return `No se pudo cargar ${contenido}. Puedes volver a intentarlo.`
}

function BibliotecaPage({ rutaLibros, onConfiguracionActualizada }) {
  const [resumen, setResumen] = useState(null)
  const [datos, setDatos] = useState(null)
  const [busqueda, setBusqueda] = useState('')
  const [busquedaAplicada, setBusquedaAplicada] = useState('')
  const [estado, setEstado] = useState('TODOS')
  const [pagina, setPagina] = useState(0)
  const [cargandoResumen, setCargandoResumen] = useState(true)
  const [cargandoLista, setCargandoLista] = useState(true)
  const [errorCarga, setErrorCarga] = useState('')
  const [errorLista, setErrorLista] = useState('')
  const [errorOperacion, setErrorOperacion] = useState('')
  const [reintento, setReintento] = useState(0)
  const [recargaLista, setRecargaLista] = useState(0)
  const [menuEstadoId, setMenuEstadoId] = useState(null)
  const [guardandoId, setGuardandoId] = useState(null)
  const [detalle, setDetalle] = useState(null)
  const [operacionBiblioteca, setOperacionBiblioteca] = useState(null)
  const [confirmacionCarpeta, setConfirmacionCarpeta] = useState(null)
  const [resultadoEscaneo, setResultadoEscaneo] = useState(null)
  const [avisoAccion, setAvisoAccion] = useState('')
  const [libroEnEdicion, setLibroEnEdicion] = useState(null)
  const [guardandoEdicion, setGuardandoEdicion] = useState(false)
  const [resultadoRenombrado, setResultadoRenombrado] = useState(null)
  const datosRef = useRef(null)
  const solicitudListaRef = useRef(0)
  const solicitudCopiasRef = useRef(0)
  const contenidoRef = useRef(null)

  useEffect(() => {
    datosRef.current = datos
  }, [datos])

  useEffect(() => {
    const temporizador = window.setTimeout(() => {
      setPagina(0)
      setBusquedaAplicada(busqueda)
    }, 300)
    return () => window.clearTimeout(temporizador)
  }, [busqueda])

  useEffect(() => {
    let activa = true
    setCargandoResumen(true)

    obtenerResumenBiblioteca()
      .then((respuesta) => {
        if (!activa) return
        setResumen(respuesta)
      })
      .catch((error) => {
        if (!activa) return
        if (resumen === null) setErrorCarga(mensajeCarga(error, 'el resumen de la biblioteca'))
        else setErrorOperacion(mensajeCarga(error, 'el resumen de la biblioteca'))
      })
      .finally(() => {
        if (activa) setCargandoResumen(false)
      })

    return () => { activa = false }
  }, [reintento]) // El resumen solo cambia al cargar o tras actualizar un estado.

  useEffect(() => {
    const numeroSolicitud = ++solicitudListaRef.current
    setCargandoLista(true)

    obtenerLibros({
      busqueda: busquedaAplicada,
      estado,
      pagina,
      tamano: TAMANO_PAGINA,
    })
      .then((respuesta) => {
        if (numeroSolicitud !== solicitudListaRef.current) return

        if (pagina > 0 && pagina >= respuesta.totalPaginas) {
          setPagina(Math.max(0, respuesta.totalPaginas - 1))
          return
        }

        setDatos(respuesta)
        setErrorLista('')
      })
      .catch((error) => {
        if (numeroSolicitud !== solicitudListaRef.current) return
        if (datosRef.current === null) setErrorCarga(mensajeCarga(error, 'la lista de libros'))
        else setErrorLista(mensajeCarga(error, 'la lista de libros'))
      })
      .finally(() => {
        if (numeroSolicitud === solicitudListaRef.current) setCargandoLista(false)
      })
  }, [busquedaAplicada, estado, pagina, reintento, recargaLista])

  const reintentarCarga = () => {
    setErrorCarga('')
    setErrorLista('')
    setErrorOperacion('')
    setReintento((valor) => valor + 1)
  }

  const cambiarBusqueda = (valor) => {
    setBusqueda(valor)
    setPagina(0)
  }

  const cambiarFiltro = (nuevoEstado) => {
    if (nuevoEstado === estado) return
    setEstado(nuevoEstado)
    setPagina(0)
    setMenuEstadoId(null)
    setDetalle(null)
  }

  const cambiarPagina = (nuevaPagina) => {
    setMenuEstadoId(null)
    setDetalle(null)
    setPagina(nuevaPagina)
    contenidoRef.current?.scrollIntoView({ block: 'start' })
  }

  const cerrarMenuEstado = useCallback(() => setMenuEstadoId(null), [])

  const guardarEstado = async (libro, leido) => {
    setMenuEstadoId(null)
    if (libro.leido === leido) return

    setGuardandoId(libro.id)
    setErrorOperacion('')

    try {
      const actualizado = await cambiarEstadoLectura(libro.id, leido)
      setDatos((actual) => actual ? {
        ...actual,
        libros: actual.libros.map((item) => item.id === libro.id ? actualizado : item),
      } : actual)

      try {
        setResumen(await obtenerResumenBiblioteca())
      } catch (errorResumen) {
        setErrorOperacion(mensajeCarga(errorResumen, 'el resumen actualizado'))
      }

      setRecargaLista((valor) => valor + 1)
    } catch (errorEstado) {
      setErrorOperacion(
        errorEstado?.message?.startsWith('No se puede contactar')
          ? errorEstado.message
          : 'No se pudo cambiar el estado de lectura. Inténtalo de nuevo.',
      )
    } finally {
      setGuardandoId(null)
    }
  }

  const alternarCopias = async (libroId) => {
    setMenuEstadoId(null)
    if (detalle?.id === libroId) {
      solicitudCopiasRef.current += 1
      setDetalle(null)
      return
    }

    const numeroSolicitud = ++solicitudCopiasRef.current
    setDetalle({ id: libroId, cargando: true, error: '', copias: [] })

    try {
      const copias = await obtenerCopiasLibro(libroId)
      if (numeroSolicitud === solicitudCopiasRef.current) {
        setDetalle({ id: libroId, cargando: false, error: '', copias })
      }
    } catch (errorCopias) {
      if (numeroSolicitud === solicitudCopiasRef.current) {
        setDetalle({
          id: libroId,
          cargando: false,
          copias: [],
          error: errorCopias?.message?.startsWith('No se puede contactar')
            ? errorCopias.message
            : 'No se pudieron cargar las copias de este libro.',
        })
      }
    }
  }

  const cerrarControlesAbiertos = () => {
    setMenuEstadoId(null)
    solicitudCopiasRef.current += 1
    setDetalle(null)
  }

  const abrirEdicion = (libro) => {
    if (libroEnEdicion || guardandoEdicion || operacionBiblioteca) return
    cerrarControlesAbiertos()
    setErrorOperacion('')
    setLibroEnEdicion(libro)
  }

  const completarEdicion = (resultado) => {
    setGuardandoEdicion(false)
    setLibroEnEdicion(null)
    cerrarControlesAbiertos()
    setResultadoRenombrado(resultado)
    setRecargaLista((valor) => valor + 1)
  }

  const recargarDespuesDeEscaneo = async (paginaSolicitada) => {
    solicitudListaRef.current += 1
    setCargandoLista(true)
    const parametrosLista = {
      busqueda: busquedaAplicada,
      estado,
      pagina: paginaSolicitada,
      tamano: TAMANO_PAGINA,
    }
    const [configuracionResultado, resumenResultado, listaResultado] = await Promise.allSettled([
      obtenerConfiguracion(),
      obtenerResumenBiblioteca(),
      obtenerLibros(parametrosLista),
    ])

    if (configuracionResultado.status === 'fulfilled') {
      onConfiguracionActualizada(configuracionResultado.value)
      if (!configuracionResultado.value.configurada || !configuracionResultado.value.rutaAccesible) {
        setCargandoLista(false)
        return
      }
    }

    if (resumenResultado.status === 'fulfilled') setResumen(resumenResultado.value)

    if (listaResultado.status === 'fulfilled') {
      let listaActualizada = listaResultado.value
      if (
        paginaSolicitada > 0
        && listaActualizada.totalPaginas > 0
        && paginaSolicitada >= listaActualizada.totalPaginas
      ) {
        const ultimaPagina = listaActualizada.totalPaginas - 1
        try {
          listaActualizada = await obtenerLibros({ ...parametrosLista, pagina: ultimaPagina })
          setPagina(ultimaPagina)
        } catch {
          setErrorOperacion(
            'La biblioteca se actualizó, pero no se pudo cargar la última página disponible.',
          )
          setCargandoLista(false)
          return
        }
      } else {
        setPagina(paginaSolicitada)
      }
      setDatos(listaActualizada)
      setErrorLista('')
    }

    if (
      configuracionResultado.status === 'rejected'
      || resumenResultado.status === 'rejected'
      || listaResultado.status === 'rejected'
    ) {
      setErrorOperacion(
        'La biblioteca se actualizó, pero no se pudieron refrescar todos los datos. Puedes volver a intentarlo.',
      )
    }
    setCargandoLista(false)
  }

  const actualizarBiblioteca = async () => {
    if (operacionBiblioteca) return
    cerrarControlesAbiertos()
    setOperacionBiblioteca('actualizando')
    setErrorOperacion('')
    setAvisoAccion('')

    try {
      const configuracion = await obtenerConfiguracion()
      onConfiguracionActualizada(configuracion)
      if (!configuracion.configurada || !configuracion.rutaAccesible) return

      const resultado = await escanearBiblioteca()
      await recargarDespuesDeEscaneo(pagina)
      setResultadoEscaneo(resultado)
    } catch (errorEscaneo) {
      try {
        const configuracion = await obtenerConfiguracion()
        onConfiguracionActualizada(configuracion)
      } catch {
        // El mensaje principal ya informa del fallo de conexión o escaneo.
      }
      setErrorOperacion(
        errorEscaneo?.message?.startsWith('No se puede contactar')
          ? errorEscaneo.message
          : 'No se pudo actualizar la biblioteca. Los datos anteriores siguen visibles.',
      )
    } finally {
      setOperacionBiblioteca(null)
    }
  }

  const rutasEquivalentes = (primera, segunda) => {
    const normalizar = (ruta) => ruta.replace(/[\\/]+$/, '').toLocaleLowerCase('es')
    return normalizar(primera) === normalizar(segunda)
  }

  const elegirNuevaCarpeta = async () => {
    if (operacionBiblioteca) return
    cerrarControlesAbiertos()
    setErrorOperacion('')
    setAvisoAccion('')

    try {
      const rutaNueva = await seleccionarCarpetaLibros()
      if (rutaNueva === null) return
      if (rutasEquivalentes(rutaLibros, rutaNueva)) {
        setAvisoAccion('Esa carpeta ya está configurada.')
        return
      }
      setConfirmacionCarpeta({ rutaNueva, error: '' })
    } catch {
      setErrorOperacion('No se pudo abrir el selector de carpetas. Puedes volver a intentarlo.')
    }
  }

  const cambiarCarpetaYEscanear = async () => {
    if (!confirmacionCarpeta || operacionBiblioteca) return
    const rutaNueva = confirmacionCarpeta.rutaNueva
    setConfirmacionCarpeta(null)
    setOperacionBiblioteca('cambiando')
    setErrorOperacion('')
    setAvisoAccion('')
    cerrarControlesAbiertos()
    let rutaGuardada = false

    try {
      const configuracion = await guardarRuta(rutaNueva)
      rutaGuardada = true
      onConfiguracionActualizada(configuracion)
      const resultado = await escanearBiblioteca()
      await recargarDespuesDeEscaneo(0)
      setResultadoEscaneo(resultado)
    } catch (errorCambio) {
      if (!rutaGuardada) {
        const mensaje = errorCambio?.message?.startsWith('No se puede contactar')
          ? errorCambio.message
          : errorCambio?.message || 'No se pudo guardar la nueva carpeta.'
        setConfirmacionCarpeta({ rutaNueva, error: mensaje })
      } else {
        try {
          const configuracionReal = await obtenerConfiguracion()
          onConfiguracionActualizada(configuracionReal)
        } catch {
          // Se conserva la ruta confirmada por el PUT si no es posible refrescarla.
        }
        setErrorOperacion(
          'La nueva ubicación se guardó, pero no se pudo actualizar la biblioteca. Puedes volver a intentarlo.',
        )
      }
    } finally {
      setOperacionBiblioteca(null)
    }
  }

  const cargaInicial = (datos === null || resumen === null) && (cargandoLista || cargandoResumen)

  if (errorCarga && (datos === null || resumen === null)) {
    return (
      <main className="biblioteca-carga">
        <h1>Biblioteca personal</h1>
        <p className="mensaje-error" role="alert">{errorCarga}</p>
        <button type="button" onClick={reintentarCarga}>Reintentar</button>
      </main>
    )
  }

  if (cargaInicial || datos === null || resumen === null) {
    return (
      <main className="biblioteca-carga" aria-busy="true">
        <h1>Biblioteca personal</h1>
        <p className="estado" role="status">Cargando biblioteca...</p>
      </main>
    )
  }

  const numeroResultados = new Intl.NumberFormat('es-ES').format(datos.totalResultados)
  const textoResultados = `${numeroResultados} ${datos.totalResultados === 1 ? 'resultado' : 'resultados'}${busquedaAplicada.trim() ? ` para ${busquedaAplicada.trim()}` : ''}`
  const bibliotecaVacia = resumen.totalLibros === 0
  const sinCoincidencias = !bibliotecaVacia && datos.libros.length === 0

  return (
    <main
      className="biblioteca-page"
      ref={contenidoRef}
      aria-busy={Boolean(operacionBiblioteca) || guardandoEdicion}
    >
      <header className="cabecera-biblioteca">
        <div className="identidad-biblioteca">
          <h1>Biblioteca personal</h1>
          <p className="ruta-principal" title={rutaLibros}>{rutaLibros}</p>
          <AccionesBiblioteca
            ocupada={Boolean(operacionBiblioteca) || guardandoEdicion}
            onActualizar={actualizarBiblioteca}
            onCambiarCarpeta={elegirNuevaCarpeta}
          />
        </div>
        <ResumenBiblioteca resumen={resumen} />
      </header>

      {operacionBiblioteca && (
        <section className="estado-escaneo" role="status" aria-live="polite">
          <strong>
            {operacionBiblioteca === 'cambiando'
              ? 'Cambiando ubicación y actualizando biblioteca...'
              : 'Actualizando biblioteca...'}
          </strong>
          <span>La duración depende del número y tamaño de los archivos.</span>
        </section>
      )}

      {avisoAccion && <p className="aviso-accion" role="status">{avisoAccion}</p>}

      <ControlesBiblioteca
        busqueda={busqueda}
        estado={estado}
        buscando={cargandoLista && datos !== null}
        onBusqueda={cambiarBusqueda}
        onEstado={cambiarFiltro}
      />

      {errorLista && <p className="mensaje-error error-puntual" role="alert">{errorLista}</p>}
      {errorOperacion && <p className="mensaje-error error-puntual" role="alert">{errorOperacion}</p>}

      <section className="resultados-biblioteca" aria-busy={cargandoLista}>
        <p className="contador-resultados">{textoResultados}</p>

        {bibliotecaVacia ? (
          <div className="estado-vacio">
            <p>No hay libros disponibles en la carpeta configurada.</p>
          </div>
        ) : sinCoincidencias ? (
          <div className="estado-vacio">
            <p>No se encontraron libros con la búsqueda y el filtro seleccionados.</p>
            {busqueda && (
              <button type="button" className="boton-secundario" onClick={() => cambiarBusqueda('')}>
                Limpiar búsqueda
              </button>
            )}
          </div>
        ) : (
          <>
            <TablaLibros
              libros={datos.libros}
              menuEstadoId={menuEstadoId}
              guardandoId={guardandoId}
              detalle={detalle}
              onAbrirEstado={(id) => setMenuEstadoId((actual) => actual === id ? null : id)}
              onCerrarEstado={cerrarMenuEstado}
              onCambiarEstado={guardarEstado}
              onAlternarCopias={alternarCopias}
              onEditar={abrirEdicion}
              edicionBloqueada={Boolean(libroEnEdicion) || guardandoEdicion || Boolean(operacionBiblioteca)}
            />
            <Paginacion datos={datos} onPagina={cambiarPagina} />
          </>
        )}
      </section>

      {confirmacionCarpeta && (
        <ConfirmacionCambioCarpeta
          rutaActual={rutaLibros}
          rutaNueva={confirmacionCarpeta.rutaNueva}
          error={confirmacionCarpeta.error}
          ocupada={Boolean(operacionBiblioteca)}
          onCancelar={() => setConfirmacionCarpeta(null)}
          onConfirmar={cambiarCarpetaYEscanear}
        />
      )}

      {resultadoEscaneo && (
        <ResumenEscaneo
          resultado={resultadoEscaneo}
          onCerrar={() => setResultadoEscaneo(null)}
        />
      )}

      {libroEnEdicion && (
        <EditarLibroDialogo
          libro={libroEnEdicion}
          onCancelar={() => {
            if (!guardandoEdicion) setLibroEnEdicion(null)
          }}
          onGuardando={setGuardandoEdicion}
          onGuardado={completarEdicion}
        />
      )}

      {resultadoRenombrado && (
        <ResultadoRenombrado
          resultado={resultadoRenombrado}
          onCerrar={() => setResultadoRenombrado(null)}
        />
      )}
    </main>
  )
}

export default BibliotecaPage
