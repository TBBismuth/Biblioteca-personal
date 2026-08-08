mod backend_manager;

use backend_manager::{get_backend_info as manager_info, BackendInfo, BackendManager};
use tauri::Manager;

#[tauri::command]
fn get_backend_info(manager: tauri::State<'_, BackendManager>) -> Result<BackendInfo, String> {
    manager_info(manager.inner())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let manager = BackendManager::new();
    let shutdown_manager = manager.clone();

    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .manage(manager)
        .invoke_handler(tauri::generate_handler![get_backend_info])
        .setup(|app| {
            app.state::<BackendManager>().start(app.handle().clone());
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("error al preparar Biblioteca personal")
        .run(move |_app_handle, event| {
            if matches!(event, tauri::RunEvent::ExitRequested { .. }) {
                shutdown_manager.shutdown();
            }
        });
}
