package com.miguel.service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.miguel.exception.OperacionPapeleraException;

@Service
public class PapeleraWindowsService implements PapeleraService {
	static final String VARIABLE_RUTA = "BIBLIOTECA_ARCHIVO_PAPELERA";
	static final String SCRIPT = """
			$ErrorActionPreference = 'Stop'
			Add-Type -AssemblyName Microsoft.VisualBasic
			[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile(
			  $env:BIBLIOTECA_ARCHIVO_PAPELERA,
			  [Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,
			  [Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)
			""";
	private static final Duration TIEMPO_MAXIMO = Duration.ofSeconds(30);

	private final EjecutorProceso ejecutor;

	public PapeleraWindowsService() {
		this(new EjecutorProcesoReal());
	}

	PapeleraWindowsService(EjecutorProceso ejecutor) {
		this.ejecutor = ejecutor;
	}

	@Override
	public void enviar(Path archivo) {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
			throw new OperacionPapeleraException(
					"La Papelera de reciclaje solo está disponible en Windows.");
		}
		String directorioWindows = System.getenv("SystemRoot");
		if (directorioWindows == null || directorioWindows.isBlank()) {
			throw new OperacionPapeleraException("No se pudo localizar PowerShell de Windows.");
		}
		Path powershell = Path.of(directorioWindows, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
		List<String> comando = List.of(
				powershell.toString(), "-NoProfile", "-NonInteractive",
				"-ExecutionPolicy", "Bypass", "-Command", SCRIPT);
		try {
			int codigo = ejecutor.ejecutar(comando, VARIABLE_RUTA, archivo.toString(), TIEMPO_MAXIMO);
			if (codigo != 0) {
				throw new OperacionPapeleraException(
						"Windows no pudo enviar el archivo a la Papelera de reciclaje.");
			}
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw new OperacionPapeleraException(
					"Se interrumpió el envío del archivo a la Papelera de reciclaje.", error);
		} catch (IOException error) {
			throw new OperacionPapeleraException(
					"No se pudo iniciar la Papelera de reciclaje de Windows.", error);
		}
	}

	interface EjecutorProceso {
		int ejecutar(
				List<String> comando, String variable, String valor, Duration tiempoMaximo)
				throws IOException, InterruptedException;
	}

	private static final class EjecutorProcesoReal implements EjecutorProceso {
		@Override
		public int ejecutar(
				List<String> comando, String variable, String valor, Duration tiempoMaximo)
				throws IOException, InterruptedException {
			ProcessBuilder constructor = new ProcessBuilder(comando);
			constructor.environment().put(variable, valor);
			constructor.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			constructor.redirectError(ProcessBuilder.Redirect.DISCARD);
			Process proceso = constructor.start();
			if (!proceso.waitFor(tiempoMaximo.toMillis(), TimeUnit.MILLISECONDS)) {
				proceso.destroyForcibly();
				throw new IOException("PowerShell no respondió dentro del tiempo permitido");
			}
			return proceso.exitValue();
		}
	}
}
