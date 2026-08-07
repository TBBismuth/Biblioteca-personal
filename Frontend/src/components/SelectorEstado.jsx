import { useEffect, useRef } from 'react'

function SelectorEstado({ libro, abierto, guardando, onAbrir, onSeleccionar, onCerrar }) {
  const contenedorRef = useRef(null)

  useEffect(() => {
    if (!abierto) return undefined

    const cerrarFuera = (evento) => {
      if (!contenedorRef.current?.contains(evento.target)) onCerrar()
    }
    const cerrarConEscape = (evento) => {
      if (evento.key === 'Escape') onCerrar()
    }

    document.addEventListener('mousedown', cerrarFuera)
    document.addEventListener('keydown', cerrarConEscape)
    return () => {
      document.removeEventListener('mousedown', cerrarFuera)
      document.removeEventListener('keydown', cerrarConEscape)
    }
  }, [abierto, onCerrar])

  return (
    <div className="selector-estado" ref={contenedorRef}>
      <button
        type="button"
        className={`boton-estado ${libro.leido ? 'estado-leido' : 'estado-pendiente'}`}
        aria-expanded={abierto}
        aria-haspopup="menu"
        disabled={guardando}
        onClick={onAbrir}
      >
        {guardando ? 'Guardando...' : libro.leido ? 'Leído' : 'Pendiente'}
      </button>

      {abierto && (
        <div className="menu-estado" role="menu">
          <button type="button" role="menuitem" onClick={() => onSeleccionar(true)}>
            Marcar como leído
          </button>
          <button type="button" role="menuitem" onClick={() => onSeleccionar(false)}>
            Marcar como pendiente
          </button>
        </div>
      )}
    </div>
  )
}

export default SelectorEstado
