use serde::Serialize;
use serde_json::Value;
use std::env;
use std::fs::{self, File, OpenOptions};
use std::io::{Read, Write};
use std::net::{SocketAddr, TcpListener, TcpStream};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, ExitStatus, Stdio};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};
use tauri::{AppHandle, Manager};

const DEVELOPMENT_PORT: u16 = 8080;
const STARTUP_ATTEMPT_TIMEOUT: Duration = Duration::from_secs(75);
const HEALTH_INTERVAL: Duration = Duration::from_millis(250);
const MAX_START_ATTEMPTS: usize = 3;

#[derive(Clone, Debug, PartialEq, Eq)]
enum BackendStatus {
    Starting,
    Ready,
    Failed,
    Stopping,
}

#[derive(Clone, Debug, PartialEq, Eq)]
enum BackendOwnership {
    None,
    Reused,
    Owned,
}

impl BackendOwnership {
    fn should_terminate(&self) -> bool {
        *self == Self::Owned
    }
}

struct BackendState {
    status: BackendStatus,
    ownership: BackendOwnership,
    base_url: Option<String>,
    error: Option<String>,
    child: Option<Child>,
}

#[derive(Clone)]
pub struct BackendManager {
    state: Arc<Mutex<BackendState>>,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BackendInfo {
    status: &'static str,
    base_url: Option<String>,
    reused: bool,
    message: Option<String>,
}

struct LaunchConfiguration {
    java: PathBuf,
    jar: PathBuf,
    working_dir: PathBuf,
    database_url: String,
    logs_dir: PathBuf,
    packaged: bool,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum BuildMode {
    Debug,
    Release,
}

impl BuildMode {
    fn current() -> Self {
        if cfg!(debug_assertions) {
            Self::Debug
        } else {
            Self::Release
        }
    }
}

#[derive(Debug, PartialEq, Eq)]
enum JavaStrategy {
    SystemPath,
    Bundled(PathBuf),
}

fn java_strategy(mode: BuildMode, resource_dir: &Path) -> JavaStrategy {
    match mode {
        BuildMode::Debug => JavaStrategy::SystemPath,
        BuildMode::Release => {
            JavaStrategy::Bundled(resource_dir.join("runtime").join("bin").join("java.exe"))
        }
    }
}

fn bundled_backend_jar(resource_dir: &Path) -> PathBuf {
    resource_dir.join("backend").join("Biblioteca_personal.jar")
}

enum LaunchFailure {
    Retryable(String),
    Permanent(String),
}

impl BackendManager {
    pub fn new() -> Self {
        Self {
            state: Arc::new(Mutex::new(BackendState {
                status: BackendStatus::Starting,
                ownership: BackendOwnership::None,
                base_url: None,
                error: None,
                child: None,
            })),
        }
    }

    pub fn start(&self, app: AppHandle) {
        let manager = self.clone();
        thread::spawn(move || manager.start_inner(&app));
    }

    pub fn info(&self) -> Result<BackendInfo, String> {
        let state = self.state.lock().map_err(|_| internal_state_error())?;
        match state.status {
            BackendStatus::Starting => Ok(BackendInfo {
                status: "STARTING",
                base_url: None,
                reused: false,
                message: None,
            }),
            BackendStatus::Ready => Ok(BackendInfo {
                status: "READY",
                base_url: state.base_url.clone(),
                reused: state.ownership == BackendOwnership::Reused,
                message: None,
            }),
            BackendStatus::Failed => Err(state
                .error
                .clone()
                .unwrap_or_else(|| "No se pudo iniciar el servicio interno.".to_string())),
            BackendStatus::Stopping => Err("La aplicación se está cerrando.".to_string()),
        }
    }

    fn start_inner(&self, app: &AppHandle) {
        if cfg!(debug_assertions) && backend_is_this_project(DEVELOPMENT_PORT) {
            self.mark_ready(DEVELOPMENT_PORT, BackendOwnership::Reused);
            return;
        }

        let configuration = match prepare_launch_configuration(app) {
            Ok(configuration) => configuration,
            Err(error) => {
                self.mark_failed(error);
                return;
            }
        };

        if let Err(error) = verify_java(&configuration.java) {
            self.mark_failed(error);
            return;
        }

        let mut last_error = String::new();
        for attempt in 1..=MAX_START_ATTEMPTS {
            if self.is_stopping() {
                return;
            }

            let port = match select_loopback_port() {
                Ok(port) => port,
                Err(error) => {
                    last_error = error;
                    continue;
                }
            };

            match self.launch_and_wait(&configuration, port, attempt) {
                Ok(()) => return,
                Err(LaunchFailure::Retryable(error)) => last_error = error,
                Err(LaunchFailure::Permanent(error)) => {
                    self.mark_failed(error);
                    return;
                }
            }
        }

        self.mark_failed(format!(
            "No se pudo iniciar el servicio interno tras {MAX_START_ATTEMPTS} intentos. {last_error}"
        ));
    }

    fn launch_and_wait(
        &self,
        configuration: &LaunchConfiguration,
        port: u16,
        attempt: usize,
    ) -> Result<(), LaunchFailure> {
        fs::create_dir_all(&configuration.logs_dir).map_err(|_| {
            LaunchFailure::Permanent(
                "No se pudo preparar la carpeta de logs del servicio interno.".to_string(),
            )
        })?;
        let session = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis();
        let stdout_path = configuration
            .logs_dir
            .join(format!("backend-{session}-{attempt}-stdout.log"));
        let stderr_path = configuration
            .logs_dir
            .join(format!("backend-{session}-{attempt}-stderr.log"));
        let stdout = create_log(&stdout_path)?;
        let stderr = create_log(&stderr_path)?;
        let java_for_process = external_process_path(&configuration.java);
        let jar_for_process = external_process_path(&configuration.jar);
        let working_dir_for_process = external_process_path(&configuration.working_dir);

        append_manager_log(
            &configuration.logs_dir,
            &format!(
                "Inicio intento {attempt}: java={}, jar={}, working_dir={}, puerto={port}, stderr={}",
                java_for_process.display(),
                jar_for_process.display(),
                working_dir_for_process.display(),
                external_process_path(&stderr_path).display()
            ),
        );

        let mut command = hidden_command(&java_for_process);
        command
            .arg("-jar")
            .arg(&jar_for_process)
            .current_dir(&working_dir_for_process)
            .env("BIBLIOTECA_SERVER_ADDRESS", "127.0.0.1")
            .env("BIBLIOTECA_SERVER_PORT", port.to_string())
            .env("BIBLIOTECA_DB_URL", &configuration.database_url)
            .env("BIBLIOTECA_MANAGED_PROCESS", "true")
            .stdin(Stdio::piped())
            .stdout(Stdio::from(stdout))
            .stderr(Stdio::from(stderr));

        if configuration.packaged {
            command.env("SPRING_PROFILES_ACTIVE", "packaged");
        } else {
            command.env_remove("SPRING_PROFILES_ACTIVE");
        }

        let child = command.spawn().map_err(|error| {
            if error.kind() == std::io::ErrorKind::NotFound {
                LaunchFailure::Permanent(
                    "No se encontró Java para iniciar el servicio interno.".to_string(),
                )
            } else {
                append_manager_log(
                    &configuration.logs_dir,
                    &format!("No se pudo crear el proceso Java: {error}"),
                );
                LaunchFailure::Permanent(
                    "No se pudo ejecutar el servicio interno de Biblioteca personal.".to_string(),
                )
            }
        })?;

        self.store_owned_child(child, port)
            .map_err(LaunchFailure::Permanent)?;
        let deadline = Instant::now() + STARTUP_ATTEMPT_TIMEOUT;
        while Instant::now() < deadline {
            if self.is_stopping() {
                return Err(LaunchFailure::Permanent(
                    "El arranque se canceló porque la aplicación se está cerrando.".into(),
                ));
            }
            if let Some(status) = self
                .owned_child_exit_status()
                .map_err(LaunchFailure::Permanent)?
            {
                self.clear_owned_child();
                let stderr_tail = read_log_tail(&stderr_path, 20);
                let stdout_tail = read_log_tail(&stdout_path, 20);
                append_manager_log(
                    &configuration.logs_dir,
                    &format!(
                        "Java terminó antes de readiness: código={}; stdout final:\n{}\nstderr final:\n{}",
                        exit_code(&status),
                        stdout_tail,
                        stderr_tail
                    ),
                );
                let message = "El servicio interno terminó antes de completar el arranque. Revisa Backend/target/tauri-backend-logs/backend-manager.log.".to_string();
                return if is_retryable_port_failure(&format!("{stdout_tail}\n{stderr_tail}")) {
                    Err(LaunchFailure::Retryable(message))
                } else {
                    Err(LaunchFailure::Permanent(message))
                };
            }
            if health_is_up(port) {
                append_manager_log(
                    &configuration.logs_dir,
                    &format!("Backend listo en {}", base_url(port)),
                );
                self.mark_ready(port, BackendOwnership::Owned);
                return Ok(());
            }
            thread::sleep(HEALTH_INTERVAL);
        }

        self.stop_owned_child();
        append_manager_log(
            &configuration.logs_dir,
            &format!(
                "Timeout de readiness tras {} segundos en el puerto {port}",
                STARTUP_ATTEMPT_TIMEOUT.as_secs()
            ),
        );
        Err(LaunchFailure::Permanent(
            "El servicio interno no estuvo listo dentro del tiempo esperado. Revisa el log de desarrollo."
                .to_string(),
        ))
    }

    fn store_owned_child(&self, mut child: Child, port: u16) -> Result<(), String> {
        let mut state = self.state.lock().map_err(|_| internal_state_error())?;
        if state.status == BackendStatus::Stopping {
            drop(state);
            let _ = child.kill();
            let _ = child.wait();
            return Err("La aplicación se está cerrando.".to_string());
        }
        state.ownership = BackendOwnership::Owned;
        state.base_url = Some(base_url(port));
        state.child = Some(child);
        Ok(())
    }

    fn owned_child_exit_status(&self) -> Result<Option<ExitStatus>, String> {
        let mut state = self.state.lock().map_err(|_| internal_state_error())?;
        match state.child.as_mut() {
            Some(child) => child
                .try_wait()
                .map_err(|_| "No se pudo supervisar el servicio interno.".to_string()),
            None => Err("Se perdió la referencia al servicio interno.".to_string()),
        }
    }

    fn clear_owned_child(&self) {
        if let Ok(mut state) = self.state.lock() {
            state.child = None;
            state.base_url = None;
            state.ownership = BackendOwnership::None;
        }
    }

    fn mark_ready(&self, port: u16, ownership: BackendOwnership) {
        if let Ok(mut state) = self.state.lock() {
            if state.status != BackendStatus::Stopping {
                state.status = BackendStatus::Ready;
                state.ownership = ownership;
                state.base_url = Some(base_url(port));
                state.error = None;
            }
        }
    }

    fn mark_failed(&self, error: String) {
        self.stop_owned_child();
        if let Ok(mut state) = self.state.lock() {
            if state.status != BackendStatus::Stopping {
                state.status = BackendStatus::Failed;
                state.ownership = BackendOwnership::None;
                state.base_url = None;
                state.error = Some(error);
            }
        }
    }

    fn is_stopping(&self) -> bool {
        self.state
            .lock()
            .map(|state| state.status == BackendStatus::Stopping)
            .unwrap_or(true)
    }

    pub fn shutdown(&self) {
        let owned = if let Ok(mut state) = self.state.lock() {
            state.status = BackendStatus::Stopping;
            state.ownership.should_terminate()
        } else {
            false
        };
        if owned {
            self.stop_owned_child();
        }
    }

    fn stop_owned_child(&self) {
        let mut child = self.state.lock().ok().and_then(|mut state| {
            state.ownership = BackendOwnership::None;
            state.base_url = None;
            state.child.take()
        });
        let Some(child) = child.as_mut() else {
            return;
        };

        if let Some(stdin) = child.stdin.as_mut() {
            let _ = stdin.write_all(b"shutdown\n");
            let _ = stdin.flush();
        }

        let deadline = Instant::now() + Duration::from_secs(3);
        while Instant::now() < deadline {
            match child.try_wait() {
                Ok(Some(_)) => return,
                Ok(None) => thread::sleep(Duration::from_millis(100)),
                Err(_) => break,
            }
        }
        let _ = child.kill();
        let _ = child.wait();
    }

    #[cfg(test)]
    fn set_reused_for_test(&self) {
        self.mark_ready(DEVELOPMENT_PORT, BackendOwnership::Reused);
    }

    #[cfg(test)]
    fn owns_process_for_test(&self) -> bool {
        self.state
            .lock()
            .map(|state| state.ownership == BackendOwnership::Owned)
            .unwrap_or(false)
    }
}

pub fn get_backend_info(manager: &BackendManager) -> Result<BackendInfo, String> {
    manager.info()
}

fn prepare_launch_configuration(app: &AppHandle) -> Result<LaunchConfiguration, String> {
    if BuildMode::current() == BuildMode::Debug {
        let backend_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("..")
            .join("..")
            .join("Backend")
            .canonicalize()
            .map_err(|_| "No se encontró la carpeta Backend del proyecto.".to_string())?;
        let jar = backend_dir
            .join("target")
            .join("Biblioteca_personal-0.0.1-SNAPSHOT.jar");
        if !jar.is_file() {
            return Err(
                "No se encontró el JAR del Backend. Compílalo con .\\mvnw.cmd clean package."
                    .to_string(),
            );
        }
        let jar = jar
            .canonicalize()
            .map_err(|_| "No se pudo resolver la ruta absoluta del JAR del Backend.".to_string())?;
        let database_path = backend_dir.join("data").join("biblioteca_personal");
        Ok(LaunchConfiguration {
            java: resolve_java_executable()?,
            jar,
            working_dir: backend_dir.clone(),
            database_url: database_url_from_path(&database_path)?,
            logs_dir: backend_dir.join("target").join("tauri-backend-logs"),
            packaged: false,
        })
    } else {
        let app_data = app
            .path()
            .app_local_data_dir()
            .map_err(|_| "No se pudo determinar la carpeta local de la aplicación.".to_string())?;
        let data_dir = app_data.join("data");
        let logs_dir = app_data.join("logs");
        fs::create_dir_all(&data_dir)
            .and_then(|_| fs::create_dir_all(&logs_dir))
            .map_err(|_| {
                "No se pudieron preparar las carpetas de datos de la aplicación.".to_string()
            })?;
        let resource_dir = app
            .path()
            .resource_dir()
            .map_err(|_| "No se pudo determinar la carpeta de recursos.".to_string())?;
        let java = match java_strategy(BuildMode::Release, &resource_dir) {
            JavaStrategy::Bundled(path) => path,
            JavaStrategy::SystemPath => unreachable!("release nunca utiliza Java del sistema"),
        };
        let jar = bundled_backend_jar(&resource_dir);
        validate_bundled_resources(&java, &jar)?;
        Ok(LaunchConfiguration {
            java: java.canonicalize().map_err(|_| {
                "No se pudo resolver el runtime Java incluido con la aplicación.".to_string()
            })?,
            jar: jar
                .canonicalize()
                .map_err(|_| "No se pudo resolver el servicio interno incluido.".to_string())?,
            working_dir: app_data,
            database_url: database_url_from_path(&data_dir.join("biblioteca_personal"))?,
            logs_dir,
            packaged: true,
        })
    }
}

fn validate_bundled_resources(java: &Path, jar: &Path) -> Result<(), String> {
    if !java.is_file() {
        return Err("No se encontró el runtime Java incluido con la aplicación.".to_string());
    }
    if !jar.is_file() {
        return Err("No se encontró el servicio interno incluido con la aplicación.".to_string());
    }
    Ok(())
}

fn resolve_java_executable() -> Result<PathBuf, String> {
    let executable = if cfg!(windows) { "java.exe" } else { "java" };
    env::var_os("PATH")
        .into_iter()
        .flat_map(|path| env::split_paths(&path).collect::<Vec<_>>())
        .map(|directory| directory.join(executable))
        .find(|candidate| candidate.is_file())
        .and_then(|candidate| candidate.canonicalize().ok())
        .ok_or_else(|| "No se encontró Java para iniciar el servicio interno.".to_string())
}

fn verify_java(java: &Path) -> Result<(), String> {
    hidden_command(external_process_path(java))
        .arg("-version")
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status()
        .map_err(|_| "No se encontró Java para iniciar el servicio interno.".to_string())?
        .success()
        .then_some(())
        .ok_or_else(|| "Java no pudo iniciarse correctamente. Se necesita Java 21.".to_string())
}

fn hidden_command(program: impl AsRef<std::ffi::OsStr>) -> Command {
    let mut command = Command::new(program);
    #[cfg(target_os = "windows")]
    {
        use std::os::windows::process::CommandExt;
        command.creation_flags(0x0800_0000);
    }
    command
}

fn create_log(path: &Path) -> Result<File, LaunchFailure> {
    File::create(path).map_err(|_| {
        LaunchFailure::Permanent("No se pudo crear el log del servicio interno.".to_string())
    })
}

fn append_manager_log(directory: &Path, message: &str) {
    let path = directory.join("backend-manager.log");
    if let Ok(mut log) = OpenOptions::new().create(true).append(true).open(path) {
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        let _ = writeln!(log, "[{timestamp}] {message}");
    }
}

fn read_log_tail(path: &Path, maximum_lines: usize) -> String {
    let content = fs::read_to_string(path).unwrap_or_default();
    let mut lines = content
        .lines()
        .rev()
        .take(maximum_lines)
        .collect::<Vec<_>>();
    lines.reverse();
    lines.join("\n")
}

fn exit_code(status: &ExitStatus) -> String {
    status
        .code()
        .map(|code| code.to_string())
        .unwrap_or_else(|| "sin código disponible".to_string())
}

fn is_retryable_port_failure(stderr: &str) -> bool {
    let normalized = stderr.to_lowercase();
    normalized.contains("bindexception")
        || normalized.contains("address already in use")
        || normalized.contains("port already in use")
        || normalized.contains("puerto ya está en uso")
}

fn select_loopback_port() -> Result<u16, String> {
    TcpListener::bind(("127.0.0.1", 0))
        .and_then(|listener| listener.local_addr())
        .map(|address| address.port())
        .map_err(|_| "No se pudo reservar un puerto local para el servicio interno.".to_string())
}

fn base_url(port: u16) -> String {
    format!("http://127.0.0.1:{port}")
}

fn database_url_from_path(path: &Path) -> Result<String, String> {
    if !path.is_absolute() {
        return Err("La ruta de la base de datos debe ser absoluta.".to_string());
    }
    let normalized = external_process_path(path)
        .to_string_lossy()
        .replace('\\', "/");
    Ok(format!("jdbc:h2:file:{normalized}"))
}

#[cfg(target_os = "windows")]
fn external_process_path(path: &Path) -> PathBuf {
    use std::ffi::OsString;
    use std::os::windows::ffi::{OsStrExt, OsStringExt};

    const SEPARATOR: u16 = b'\\' as u16;
    const QUESTION: u16 = b'?' as u16;
    const COLON: u16 = b':' as u16;
    let wide = path.as_os_str().encode_wide().collect::<Vec<_>>();
    let has_verbatim_prefix = wide.starts_with(&[SEPARATOR, SEPARATOR, QUESTION, SEPARATOR]);
    if !has_verbatim_prefix {
        return path.to_path_buf();
    }

    let is_ascii_letter = |value: u16, uppercase: u8| {
        value == uppercase as u16 || value == uppercase.to_ascii_lowercase() as u16
    };
    let is_verbatim_unc = wide.len() >= 8
        && is_ascii_letter(wide[4], b'U')
        && is_ascii_letter(wide[5], b'N')
        && is_ascii_letter(wide[6], b'C')
        && wide[7] == SEPARATOR;
    if is_verbatim_unc {
        let mut normal = vec![SEPARATOR, SEPARATOR];
        normal.extend_from_slice(&wide[8..]);
        return PathBuf::from(OsString::from_wide(&normal));
    }

    let is_drive_path = wide.len() >= 6 && wide[5] == COLON;
    if is_drive_path {
        return PathBuf::from(OsString::from_wide(&wide[4..]));
    }

    path.to_path_buf()
}

#[cfg(not(target_os = "windows"))]
fn external_process_path(path: &Path) -> PathBuf {
    path.to_path_buf()
}

fn health_is_up(port: u16) -> bool {
    http_get(port, "/api/health")
        .ok()
        .filter(|(status, _)| *status == 200)
        .and_then(|(_, body)| serde_json::from_str::<Value>(&body).ok())
        .and_then(|body| {
            body.get("status")
                .and_then(Value::as_str)
                .map(str::to_owned)
        })
        .as_deref()
        == Some("UP")
}

fn backend_is_this_project(port: u16) -> bool {
    if !health_is_up(port) {
        return false;
    }
    http_get(port, "/api/configuracion")
        .ok()
        .filter(|(status, _)| *status == 200)
        .and_then(|(_, body)| serde_json::from_str::<Value>(&body).ok())
        .is_some_and(|body| {
            body.get("configurada").is_some_and(Value::is_boolean)
                && body.get("rutaAccesible").is_some_and(Value::is_boolean)
                && body.get("rutaLibros").is_some()
        })
}

fn http_get(port: u16, path: &str) -> Result<(u16, String), String> {
    let address = SocketAddr::from(([127, 0, 0, 1], port));
    let mut stream = TcpStream::connect_timeout(&address, Duration::from_millis(400))
        .map_err(|_| "No disponible".to_string())?;
    stream
        .set_read_timeout(Some(Duration::from_millis(800)))
        .map_err(|_| "No disponible".to_string())?;
    write!(
        stream,
        "GET {path} HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nConnection: close\r\n\r\n"
    )
    .map_err(|_| "No disponible".to_string())?;
    let mut response = String::new();
    stream
        .read_to_string(&mut response)
        .map_err(|_| "No disponible".to_string())?;
    let (headers, body) = response
        .split_once("\r\n\r\n")
        .ok_or_else(|| "Respuesta HTTP no válida".to_string())?;
    let status = headers
        .lines()
        .next()
        .and_then(|line| line.split_whitespace().nth(1))
        .and_then(|value| value.parse::<u16>().ok())
        .ok_or_else(|| "Respuesta HTTP no válida".to_string())?;
    let body = if headers
        .lines()
        .any(|line| line.eq_ignore_ascii_case("Transfer-Encoding: chunked"))
    {
        decode_chunked_body(body)?
    } else {
        body.to_string()
    };
    Ok((status, body))
}

fn decode_chunked_body(mut encoded: &str) -> Result<String, String> {
    let mut decoded = String::new();
    loop {
        let (size_line, rest) = encoded
            .split_once("\r\n")
            .ok_or_else(|| "Respuesta HTTP fragmentada no válida".to_string())?;
        let size = usize::from_str_radix(size_line.split(';').next().unwrap_or_default(), 16)
            .map_err(|_| "Respuesta HTTP fragmentada no válida".to_string())?;
        if size == 0 {
            return Ok(decoded);
        }
        if rest.len() < size + 2 || !rest.is_char_boundary(size) || &rest[size..size + 2] != "\r\n"
        {
            return Err("Respuesta HTTP fragmentada no válida".to_string());
        }
        decoded.push_str(&rest[..size]);
        encoded = &rest[size + 2..];
    }
}

fn internal_state_error() -> String {
    "No se pudo consultar el estado del servicio interno.".to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn genera_url_h2_desde_ruta_absoluta_sin_sufijo_de_archivo() {
        let path = if cfg!(windows) {
            PathBuf::from(r"C:\Users\Prueba\AppData Local\Biblioteca\data\biblioteca_personal")
        } else {
            PathBuf::from("/tmp/Biblioteca/data/biblioteca_personal")
        };
        let url = database_url_from_path(&path).unwrap();
        assert!(url.starts_with("jdbc:h2:file:"));
        assert!(url.ends_with("/data/biblioteca_personal"));
        assert!(!url.ends_with(".mv.db"));
    }

    #[test]
    fn rechaza_ruta_h2_relativa() {
        assert!(database_url_from_path(Path::new("data/biblioteca_personal")).is_err());
    }

    #[test]
    fn selecciona_un_puerto_libre_solo_en_loopback() {
        let port = select_loopback_port().unwrap();
        assert_ne!(port, 0);
        let listener = TcpListener::bind(("127.0.0.1", port)).unwrap();
        assert_eq!(listener.local_addr().unwrap().ip().to_string(), "127.0.0.1");
    }

    #[test]
    fn backend_reutilizado_no_se_marca_como_proceso_propio() {
        let manager = BackendManager::new();
        manager.set_reused_for_test();
        let info = manager.info().unwrap();
        assert_eq!(info.status, "READY");
        assert!(info.reused);
        assert!(!manager.owns_process_for_test());
        manager.shutdown();
        assert!(!manager.owns_process_for_test());
    }

    #[test]
    fn solo_la_propiedad_owned_autoriza_terminar_un_proceso() {
        assert!(BackendOwnership::Owned.should_terminate());
        assert!(!BackendOwnership::Reused.should_terminate());
        assert!(!BackendOwnership::None.should_terminate());
    }

    #[test]
    fn estado_inicial_es_starting() {
        let manager = BackendManager::new();
        let info = manager.info().unwrap();
        assert_eq!(info.status, "STARTING");
        assert!(info.base_url.is_none());
    }

    #[test]
    fn interpreta_respuesta_http_fragmentada() {
        let body = decode_chunked_body("f\r\n{\"status\":\"UP\"}\r\n0\r\n\r\n").unwrap();
        assert_eq!(body, "{\"status\":\"UP\"}");
    }

    #[test]
    fn solo_reintenta_errores_que_indican_conflicto_de_puerto() {
        assert!(is_retryable_port_failure(
            "java.net.BindException: Address already in use"
        ));
        assert!(!is_retryable_port_failure(
            "ClassNotFoundException: JarLauncher"
        ));
    }

    #[test]
    fn timeout_admite_arranques_lentos_de_spring() {
        assert!(STARTUP_ATTEMPT_TIMEOUT >= Duration::from_secs(60));
    }

    #[cfg(target_os = "windows")]
    #[test]
    fn convierte_ruta_verbatim_de_unidad_c_a_formato_externo() {
        assert_eq!(
            external_process_path(Path::new(r"\\?\C:\ruta\archivo.jar")),
            PathBuf::from(r"C:\ruta\archivo.jar")
        );
    }

    #[cfg(target_os = "windows")]
    #[test]
    fn convierte_ruta_verbatim_con_espacios_de_unidad_e() {
        assert_eq!(
            external_process_path(Path::new(r"\\?\E:\ruta con espacios\archivo.jar")),
            PathBuf::from(r"E:\ruta con espacios\archivo.jar")
        );
    }

    #[cfg(target_os = "windows")]
    #[test]
    fn conserva_una_ruta_windows_ya_normal() {
        let path = Path::new(r"E:\ruta\archivo.jar");
        assert_eq!(external_process_path(path), path);
    }

    #[cfg(target_os = "windows")]
    #[test]
    fn convierte_unc_verbatim_sin_destruir_la_ruta_de_red() {
        assert_eq!(
            external_process_path(Path::new(r"\\?\UNC\servidor\recurso\archivo.jar")),
            PathBuf::from(r"\\servidor\recurso\archivo.jar")
        );
    }

    #[test]
    fn debug_conserva_la_estrategia_de_java_del_sistema() {
        assert_eq!(
            java_strategy(BuildMode::Debug, Path::new("recursos-ignorados")),
            JavaStrategy::SystemPath
        );
    }

    #[test]
    fn release_elige_exclusivamente_java_y_jar_de_resources() {
        let resources = Path::new(r"C:\Aplicacion\resources");
        assert_eq!(
            java_strategy(BuildMode::Release, resources),
            JavaStrategy::Bundled(resources.join("runtime").join("bin").join("java.exe"))
        );
        assert_eq!(
            bundled_backend_jar(resources),
            resources.join("backend").join("Biblioteca_personal.jar")
        );
    }

    #[test]
    fn validacion_release_exige_java_y_jar_presentes() {
        let unique = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let root = env::temp_dir().join(format!("biblioteca-runtime-test-{unique}"));
        let java = root.join("runtime").join("bin").join("java.exe");
        let jar = root.join("backend").join("Biblioteca_personal.jar");

        assert!(validate_bundled_resources(&java, &jar).is_err());
        fs::create_dir_all(java.parent().unwrap()).unwrap();
        fs::create_dir_all(jar.parent().unwrap()).unwrap();
        File::create(&java).unwrap();
        assert!(validate_bundled_resources(&java, &jar).is_err());
        File::create(&jar).unwrap();
        assert!(validate_bundled_resources(&java, &jar).is_ok());

        fs::remove_dir_all(root).unwrap();
    }
}
