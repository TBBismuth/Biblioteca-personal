# 📚 Biblioteca personal

> Spanish version available below.

**Biblioteca personal** is a Windows desktop application designed to organize and manage large local ebook collections.

The application scans an existing folder structure without requiring books to be imported into a proprietary library. It identifies books from their filenames, groups multiple physical copies of the same logical book, tracks read/unread status, provides fast title and author search, detects moved or renamed files and allows book metadata and physical files to be safely updated.

It is built with a **Spring Boot backend**, a **React + Vite frontend** and **Tauri 2** as the native desktop shell. Data is stored locally in an embedded **H2 database**.

The packaged Windows version includes its own **Java 21 runtime**, so end users do not need to install Java, Node.js, Maven or any development tools.

---

## ✨ Main Features

### Library scanning and synchronization

- Scan an existing ebook folder recursively.
- Preserve the original folder structure.
- Detect new files added outside the application.
- Detect files removed outside the application.
- Detect files moved or renamed.
- SHA-256 content hashing for reliable file identification.
- Preserve book history and read status when files are moved or renamed.
- Detect additional identical physical copies.
- Safe synchronization without deleting logical book history.

### Book organization

The standard filename format is:

```text
Author - Title.ext
```

Multiple authors are supported:

```text
Author One, Author Two - Title.ext
```

Recognized duplicate/version suffixes such as:

```text
[duplicado N]
[versión N]
```

can be preserved when processing filenames.

A logical book may contain one or more physical files in different locations or formats.

### Search and filtering

- Search by title.
- Search by author.
- Combined title/author search.
- Multi-term search independent of word order.
- Case-insensitive search.
- Accent-insensitive search.
- Partial word matching.
- Filter by:
  - All books.
  - Read books.
  - Pending books.
- Paginated results.

### Reading status

- Mark books as read or pending.
- Reading status belongs to the logical book rather than to an individual file.
- Reading history is preserved even when every physical copy of a book disappears.

### Multiple copies

- Display the number of physical copies of each book.
- Inspect the exact path of every copy.
- Show file extension, size and modification date.
- Support different file formats for the same logical book.

### Safe metadata editing and physical renaming

- Edit book title.
- Add, remove or modify authors.
- Rename the associated physical files automatically.
- Preserve folders, extensions and recognized suffixes.
- Detect destination conflicts before modifying files.
- Two-phase rename strategy to avoid path collisions.
- Database and filesystem consistency safeguards.

### Windows Recycle Bin integration

- Send an individual copy to the Windows Recycle Bin.
- Send all copies of a book to the Recycle Bin.
- Never fall back to permanent deletion.
- Validate paths before deletion.
- Protect against files outside the configured library root.
- Preserve book metadata, authors and reading history after the last copy is removed.
- Handle partial failures safely during multi-copy deletion.

### Desktop integration

- Native Windows desktop application using Tauri 2.
- Native folder selector.
- Spring Boot backend automatically started and supervised by Tauri.
- Backend bound exclusively to local loopback.
- Dynamic backend port selection.
- Health/readiness checks before loading the main application.
- Graceful backend shutdown when the desktop application closes.
- Persistent application data stored outside the installation directory.
- Windows NSIS installer.

---

## 📄 Supported ebook formats

The scanner currently supports:

- PDF
- EPUB
- DOC
- DOCX
- RTF
- MOBI
- AZW
- AZW3
- FB2
- TXT
- ODT

---

## 🧱 Tech Stack

### Backend

- Java 21
- Spring Boot 4
- Spring Web MVC
- Spring Data JPA
- Hibernate
- H2 Database
- Maven
- JUnit / Spring testing infrastructure

### Frontend

- React 19
- Vite
- JavaScript
- CSS
- Native Fetch API

### Desktop

- Tauri 2
- Rust
- Microsoft Edge WebView2
- Windows NSIS installer
- Embedded Java 21 runtime generated with `jlink`

### Persistence

- H2 file database.
- Local persistent application data.
- SHA-256 hashes for physical file identification.

---

## 🏗️ Architecture

```text
┌─────────────────────────────────────┐
│        Tauri Desktop Application    │
│                                     │
│  ┌───────────────────────────────┐  │
│  │       React + Vite UI         │  │
│  └───────────────┬───────────────┘  │
│                  │ HTTP             │
│                  ▼                  │
│  ┌───────────────────────────────┐  │
│  │   Spring Boot REST Backend    │  │
│  │      127.0.0.1:<dynamic>      │  │
│  └───────────────┬───────────────┘  │
│                  │ JPA              │
│                  ▼                  │
│  ┌───────────────────────────────┐  │
│  │       Persistent H2 DB        │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
                  │
                  ▼
        Local ebook folders
```

In packaged builds, Tauri manages the complete lifecycle of the Spring Boot process.

The backend is not exposed to the local network and only listens on `127.0.0.1`.

---

## 💾 Application data

The database is deliberately stored separately from the installed application.

On Windows:

```text
%LOCALAPPDATA%\com.miguel.bibliotecapersonal\data\biblioteca_personal.mv.db
```

Backend logs are stored under:

```text
%LOCALAPPDATA%\com.miguel.bibliotecapersonal\logs\
```

This allows application updates or normal uninstall/reinstall operations to preserve the library database unless the user explicitly chooses to delete application data.

---

## ⚙️ End-user requirements

The packaged Windows installer includes the Java runtime required by the backend.

Users do **not** need to install:

- Java
- Maven
- Node.js
- npm
- Rust
- Spring Boot

The application is currently packaged for **64-bit Windows**.

---

## 🛠️ Development requirements

To build the project from source:

- Java 21 JDK
- Node.js + npm
- Rust stable toolchain
- Tauri build requirements for Windows
- Microsoft Edge WebView2

---

## ▶️ Running in development

### Backend

From the `Backend` directory:

```powershell
.\mvnw.cmd spring-boot:run
```

or run the Spring Boot project directly from an IDE such as Eclipse.

The development backend normally uses:

```text
http://127.0.0.1:8080
```

### Desktop frontend

From the `Frontend` directory:

```powershell
npm install
npm run tauri dev
```

During development, Tauri first checks whether the Biblioteca personal backend is already running on port `8080`.

If it is running, for example from Eclipse, Tauri reuses it.

If it is not running, Tauri can start the compiled Spring Boot JAR itself.

---

## 📦 Building the Windows application

First build the Spring Boot backend:

```powershell
cd Backend
.\mvnw.cmd clean verify
```

Then prepare the packaged backend and embedded Java runtime:

```powershell
cd ..\Frontend
.\scripts\prepare-packaging.ps1
```

Finally build the Tauri application:

```powershell
npx tauri build
```

The generated Windows installer is placed under:

```text
Frontend/src-tauri/target/release/bundle/nsis/
```

---

## 🛣️ Main API Areas

All backend routes are prefixed with `/api`.

### Books

```http
GET    /api/libros
GET    /api/libros/resumen
GET    /api/libros/{id}/copias
PATCH  /api/libros/{id}/leido
PUT    /api/libros/{id}
DELETE /api/libros/{idLibro}/copias/{idArchivo}
DELETE /api/libros/{idLibro}/copias
```

### Library synchronization

```http
POST /api/escaneo
```

### Configuration

```http
GET /api/configuracion
PUT /api/configuracion/ruta
```

### Health

```http
GET /api/health
```

---

## ✅ Testing and Validation

The project includes automated tests for the backend and the Tauri/Rust desktop lifecycle.

Validation covers areas such as:

- REST controllers and services.
- H2 persistence behavior.
- Library synchronization.
- File movement and rename detection.
- Multiple authors and multiple physical copies.
- Safe book renaming.
- Windows Recycle Bin deletion flows.
- CORS configuration.
- Health/readiness endpoint.
- Backend process ownership.
- Dynamic port handling.
- Windows path normalization.
- Embedded runtime selection.

The project is also validated through:

```powershell
.\mvnw.cmd clean verify
npm run build
cargo fmt --check
cargo test
cargo check
```

Packaging validation additionally includes isolated smoke tests using a temporary H2 database.

---

## 🚀 Current Status

Current desktop version:

```text
0.1.0
```

The application currently supports:

- Persistent local ebook library management.
- Thousands of indexed books.
- Read/pending tracking.
- Advanced title and author search.
- Multiple copies and formats per logical book.
- Safe external synchronization.
- Physical file renaming.
- Windows Recycle Bin integration.
- Embedded Java runtime.
- Native Windows installation through NSIS.
- Persistent user data independent from installation files.

---

## 📥 Download

Windows releases can be downloaded from:

https://github.com/TBBismuth/Biblioteca-personal/releases

For normal use, download the latest Windows `x64-setup.exe` installer from the release assets.

---

## 🧪 Future Plans

Possible future improvements include:

- Automatic application updates.
- Signed Windows installers.
- Improved log rotation.
- Additional library statistics.
- Additional metadata management.
- Further duplicate-management tools.
- Additional usability and accessibility improvements.

---

## ✍️ Author

Developed by **Miguel Guerrero Murillo**.

GitHub: https://github.com/TBBismuth

---

# 📚 Biblioteca personal — Versión en Español

> English version available above.

**Biblioteca personal** es una aplicación de escritorio para Windows diseñada para organizar y gestionar grandes colecciones locales de libros electrónicos.

La aplicación trabaja directamente sobre una estructura de carpetas existente, sin obligar a importar los libros a una biblioteca propietaria. Puede identificar libros a partir de sus nombres de archivo, agrupar distintas copias físicas de un mismo libro lógico, registrar si están leídos o pendientes, buscar por título o autor, detectar movimientos y renombrados y modificar de forma segura tanto los metadatos como los archivos físicos.

Está desarrollada con un **backend Spring Boot**, un **frontend React + Vite** y **Tauri 2** como contenedor nativo de escritorio. Los datos se almacenan localmente en una base de datos embebida **H2**.

La versión empaquetada para Windows incluye su propio **runtime Java 21**, por lo que el usuario final no necesita instalar Java, Node.js, Maven ni ninguna herramienta de desarrollo.

---

## ✨ Características principales

### Escaneo y sincronización

- Escaneo recursivo de una carpeta existente de libros.
- Conservación de la estructura original de carpetas.
- Detección de nuevos archivos añadidos externamente.
- Detección de archivos eliminados externamente.
- Detección de archivos movidos o renombrados.
- Hash SHA-256 para identificar de forma fiable los archivos.
- Conservación del historial y estado de lectura tras movimientos o renombrados.
- Detección de nuevas copias físicas idénticas.
- Sincronización segura sin eliminar el historial de libros lógicos.

### Organización de libros

El formato estándar de los archivos es:

```text
Autor - Título.ext
```

También se admiten varios autores:

```text
Autor Uno, Autor Dos - Título.ext
```

Los sufijos reconocidos como:

```text
[duplicado N]
[versión N]
```

pueden conservarse durante el procesamiento y renombrado.

Un mismo libro lógico puede contener varias copias físicas, ubicaciones o formatos.

### Búsqueda y filtros

- Búsqueda por título.
- Búsqueda por autor.
- Búsqueda combinada de título y autor.
- Varios términos independientemente de su orden.
- Búsqueda sin distinguir mayúsculas/minúsculas.
- Búsqueda sin distinguir acentos.
- Coincidencias parciales.
- Filtro por:
  - Todos.
  - Leídos.
  - Pendientes.
- Resultados paginados.

### Estado de lectura

- Marcar libros como leídos o pendientes.
- El estado pertenece al libro lógico, no a una copia física concreta.
- El historial permanece aunque desaparezcan todas las copias físicas.

### Gestión de copias

- Número de copias físicas de cada libro.
- Consulta de la ruta exacta de cada copia.
- Formato, tamaño y fecha de modificación.
- Distintos formatos para un mismo libro lógico.

### Edición y renombrado seguro

- Editar el título.
- Añadir, eliminar o modificar autores.
- Renombrar automáticamente los archivos físicos asociados.
- Conservar carpeta, extensión y sufijos reconocidos.
- Detectar conflictos antes de modificar archivos.
- Renombrado físico en dos fases para evitar colisiones.
- Medidas de protección para mantener coherentes sistema de archivos y base de datos.

### Papelera de reciclaje de Windows

- Enviar una copia concreta a la Papelera.
- Enviar todas las copias de un libro a la Papelera.
- Nunca realizar borrado permanente como alternativa.
- Validar las rutas antes de eliminar.
- Impedir operaciones fuera de la raíz configurada.
- Mantener título, autores e historial de lectura cuando desaparece la última copia.
- Gestión segura de errores parciales al eliminar varias copias.

### Integración de escritorio

- Aplicación nativa de Windows mediante Tauri 2.
- Selector nativo de carpetas.
- Backend Spring Boot iniciado y supervisado automáticamente por Tauri.
- Backend accesible únicamente desde loopback.
- Puerto local dinámico.
- Comprobación de disponibilidad mediante health check.
- Cierre ordenado del backend al cerrar la aplicación.
- Datos persistentes separados de los archivos instalados.
- Instalador Windows NSIS.

---

## 📄 Formatos soportados

Actualmente:

- PDF
- EPUB
- DOC
- DOCX
- RTF
- MOBI
- AZW
- AZW3
- FB2
- TXT
- ODT

---

## 🧱 Stack tecnológico

### Backend

- Java 21
- Spring Boot 4
- Spring Web MVC
- Spring Data JPA
- Hibernate
- H2
- Maven
- JUnit / Spring Test

### Frontend

- React 19
- Vite
- JavaScript
- CSS
- Fetch API nativa

### Escritorio

- Tauri 2
- Rust
- Microsoft Edge WebView2
- Instalador Windows NSIS
- Runtime Java 21 propio generado mediante `jlink`

### Persistencia

- Base de datos H2 persistente.
- Datos almacenados localmente.
- Hash SHA-256 para identificar archivos físicos.

---

## 🏗️ Arquitectura

```text
┌─────────────────────────────────────┐
│        Aplicación Tauri             │
│                                     │
│  ┌───────────────────────────────┐  │
│  │       React + Vite            │  │
│  └───────────────┬───────────────┘  │
│                  │ HTTP             │
│                  ▼                  │
│  ┌───────────────────────────────┐  │
│  │       Spring Boot REST        │  │
│  │      127.0.0.1:<dinámico>     │  │
│  └───────────────┬───────────────┘  │
│                  │ JPA              │
│                  ▼                  │
│  ┌───────────────────────────────┐  │
│  │          H2 persistente       │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
                  │
                  ▼
        Carpetas locales de libros
```

En la aplicación empaquetada, Tauri administra todo el ciclo de vida del proceso Spring Boot.

El backend no se expone a la red local y escucha únicamente en `127.0.0.1`.

---

## 💾 Datos de la aplicación

La base de datos se almacena deliberadamente fuera de la carpeta de instalación.

En Windows:

```text
%LOCALAPPDATA%\com.miguel.bibliotecapersonal\data\biblioteca_personal.mv.db
```

Los logs del backend se guardan en:

```text
%LOCALAPPDATA%\com.miguel.bibliotecapersonal\logs\
```

Esto permite actualizar o desinstalar/reinstalar normalmente la aplicación conservando la biblioteca, salvo que el usuario solicite expresamente eliminar los datos de la aplicación.

---

## ⚙️ Requisitos para el usuario

El instalador de Windows incluye el runtime Java utilizado por el backend.

El usuario **no necesita instalar**:

- Java
- Maven
- Node.js
- npm
- Rust
- Spring Boot

Actualmente la aplicación se distribuye para **Windows de 64 bits**.

---

## 🛠️ Requisitos de desarrollo

Para compilar el proyecto desde el código fuente:

- JDK Java 21
- Node.js + npm
- Rust estable
- Requisitos de compilación de Tauri para Windows
- Microsoft Edge WebView2

---

## ▶️ Ejecución en desarrollo

### Backend

Desde `Backend`:

```powershell
.\mvnw.cmd spring-boot:run
```

También puede iniciarse directamente desde un IDE como Eclipse.

Normalmente escucha en:

```text
http://127.0.0.1:8080
```

### Aplicación Tauri

Desde `Frontend`:

```powershell
npm install
npm run tauri dev
```

Durante el desarrollo, Tauri comprueba primero si ya existe una instancia válida del backend en el puerto `8080`.

Si está ejecutándose, por ejemplo desde Eclipse, la reutiliza.

Si no está ejecutándose, Tauri puede iniciar por sí mismo el JAR compilado del backend.

---

## 📦 Compilación para Windows

Primero:

```powershell
cd Backend
.\mvnw.cmd clean verify
```

Preparar después el backend empaquetado y el runtime Java:

```powershell
cd ..\Frontend
.\scripts\prepare-packaging.ps1
```

Finalmente:

```powershell
npx tauri build
```

El instalador NSIS se genera en:

```text
Frontend/src-tauri/target/release/bundle/nsis/
```

---

## 🛣️ Principales endpoints

Todas las rutas utilizan el prefijo `/api`.

### Libros

```http
GET    /api/libros
GET    /api/libros/resumen
GET    /api/libros/{id}/copias
PATCH  /api/libros/{id}/leido
PUT    /api/libros/{id}
DELETE /api/libros/{idLibro}/copias/{idArchivo}
DELETE /api/libros/{idLibro}/copias
```

### Sincronización

```http
POST /api/escaneo
```

### Configuración

```http
GET /api/configuracion
PUT /api/configuracion/ruta
```

### Health check

```http
GET /api/health
```

---

## ✅ Pruebas y validación

El proyecto dispone de pruebas automáticas tanto para el backend como para el ciclo de vida Tauri/Rust.

Entre otras áreas se prueban:

- Controladores y servicios REST.
- Persistencia H2.
- Sincronización de biblioteca.
- Detección de movimientos y renombrados.
- Varios autores y copias físicas.
- Renombrado seguro.
- Integración con la Papelera de Windows.
- CORS.
- Endpoint de health.
- Propiedad y cierre del proceso backend.
- Puertos dinámicos.
- Normalización de rutas Windows.
- Selección del runtime Java empaquetado.

Comandos principales:

```powershell
.\mvnw.cmd clean verify
npm run build
cargo fmt --check
cargo test
cargo check
```

El empaquetado también se valida mediante smoke tests aisladas con una base H2 temporal.

---

## 🚀 Estado actual

Versión actual:

```text
0.1.0
```

Actualmente incluye:

- Gestión persistente de bibliotecas locales.
- Soporte para miles de libros.
- Estado leído/pendiente.
- Búsqueda avanzada por título y autor.
- Varias copias y formatos por libro lógico.
- Sincronización con cambios realizados externamente.
- Renombrado físico de archivos.
- Papelera de reciclaje de Windows.
- Runtime Java incluido.
- Instalador nativo NSIS.
- Datos del usuario independientes de la instalación.

---

## 📥 Descarga

Las versiones para Windows pueden descargarse desde:

https://github.com/TBBismuth/Biblioteca-personal/releases

Para utilizar la aplicación normalmente basta con descargar el último instalador `x64-setup.exe` de los assets de la Release.

---

## 🧪 Mejoras futuras

Algunas posibles mejoras:

- Actualización automática de la aplicación.
- Firma digital del instalador.
- Rotación más avanzada de logs.
- Estadísticas de biblioteca.
- Gestión adicional de metadatos.
- Herramientas adicionales para duplicados.
- Mejoras de usabilidad y accesibilidad.

---

## ✍️ Autor

Desarrollado por **Miguel Guerrero Murillo**.

GitHub: https://github.com/TBBismuth
