import { open } from '@tauri-apps/plugin-dialog'

export function seleccionarCarpetaLibros() {
  return open({
    directory: true,
    multiple: false,
    title: 'Selecciona la carpeta de tus libros',
  })
}
