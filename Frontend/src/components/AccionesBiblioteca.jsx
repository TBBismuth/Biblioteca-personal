function AccionesBiblioteca({ ocupada, onActualizar, onCambiarCarpeta }) {
  return (
    <div className="acciones-biblioteca">
      <button type="button" onClick={onActualizar} disabled={ocupada}>
        Actualizar biblioteca
      </button>
      <button
        type="button"
        className="boton-secundario"
        onClick={onCambiarCarpeta}
        disabled={ocupada}
      >
        Cambiar carpeta
      </button>
    </div>
  )
}

export default AccionesBiblioteca
