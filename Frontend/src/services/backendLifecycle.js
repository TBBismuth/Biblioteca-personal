import { invoke, isTauri } from '@tauri-apps/api/core'
import { configurarBackendBaseUrl } from './bibliotecaApi.js'

const INTERVALO_CONSULTA_MS = 250
const MAXIMO_CONSULTAS = 400

function esperar(milisegundos) {
  return new Promise((resolve) => window.setTimeout(resolve, milisegundos))
}

export async function prepararBackend() {
  if (!isTauri()) {
    configurarBackendBaseUrl('')
    return { status: 'READY', baseUrl: '', reused: true }
  }

  for (let intento = 0; intento < MAXIMO_CONSULTAS; intento += 1) {
    let informacion
    try {
      informacion = await invoke('get_backend_info')
    } catch (error) {
      throw new Error(
        typeof error === 'string' && error.trim()
          ? error
          : 'No se pudo iniciar el servicio interno de Biblioteca personal.',
      )
    }

    if (informacion?.status === 'READY' && informacion.baseUrl) {
      configurarBackendBaseUrl(informacion.baseUrl)
      return informacion
    }

    await esperar(INTERVALO_CONSULTA_MS)
  }

  throw new Error('El servicio interno no estuvo disponible dentro del tiempo esperado.')
}
