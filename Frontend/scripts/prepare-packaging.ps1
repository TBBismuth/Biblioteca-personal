param(
    [string]$JdkHome,
    [string]$BackendJar
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Get-NormalPath([string]$Path) {
    return [System.IO.Path]::GetFullPath($Path)
}

function Remove-ExactGeneratedDirectory([string]$Path, [string]$ExpectedPath) {
    $normalPath = Get-NormalPath $Path
    $normalExpected = Get-NormalPath $ExpectedPath
    if (-not $normalPath.Equals($normalExpected, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Ruta de limpieza inesperada: $normalPath"
    }
    if (Test-Path -LiteralPath $normalPath) {
        Remove-Item -LiteralPath $normalPath -Recurse -Force
    }
}

function Find-Jdk21([string]$RequestedHome) {
    $candidates = [System.Collections.Generic.List[string]]::new()
    if ($RequestedHome) { $candidates.Add($RequestedHome) }
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $candidates.Add((Split-Path -Parent (Split-Path -Parent $javaCommand.Source)))
    }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        $resolvedJdkHome = Get-NormalPath $candidate
        $java = Join-Path $resolvedJdkHome 'bin\java.exe'
        $jlink = Join-Path $resolvedJdkHome 'bin\jlink.exe'
        $jdeps = Join-Path $resolvedJdkHome 'bin\jdeps.exe'
        $jar = Join-Path $resolvedJdkHome 'bin\jar.exe'
        if (-not ((Test-Path $java -PathType Leaf) -and
                  (Test-Path $jlink -PathType Leaf) -and
                  (Test-Path $jdeps -PathType Leaf) -and
                  (Test-Path $jar -PathType Leaf))) {
            continue
        }
        $previousErrorPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        $versionOutput = (& $java -version 2>&1 | Out-String)
        $versionExitCode = $LASTEXITCODE
        $ErrorActionPreference = $previousErrorPreference
        if ($versionExitCode -eq 0 -and $versionOutput -match 'version "21(?:\.|\")') {
            return [pscustomobject]@{
                Home = $resolvedJdkHome
                Java = $java
                Jlink = $jlink
                Jdeps = $jdeps
                Jar = $jar
                Version = (($versionOutput -split "`r?`n")[0]).Trim()
            }
        }
    }
    throw 'No se encontró un JDK 21 completo con java, jar, jdeps y jlink.'
}

function Get-FreeLoopbackPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    } finally {
        $listener.Stop()
    }
}

function Invoke-RuntimeSmokeTest(
    [string]$Java,
    [string]$Jar,
    [string]$SmokeRoot
) {
    $port = Get-FreeLoopbackPort
    $databaseBase = (Join-Path $SmokeRoot 'data\biblioteca_personal').Replace('\', '/')
    $oldEnvironment = @{}
    $variables = @{
        SPRING_PROFILES_ACTIVE = 'packaged'
        BIBLIOTECA_SERVER_ADDRESS = '127.0.0.1'
        BIBLIOTECA_SERVER_PORT = [string]$port
        BIBLIOTECA_DB_URL = "jdbc:h2:file:$databaseBase"
        BIBLIOTECA_MANAGED_PROCESS = 'true'
    }
    foreach ($name in $variables.Keys) {
        $oldEnvironment[$name] = [System.Environment]::GetEnvironmentVariable($name, 'Process')
        [System.Environment]::SetEnvironmentVariable($name, $variables[$name], 'Process')
    }

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $Java
    $startInfo.Arguments = '-jar "' + $Jar + '"'
    $startInfo.WorkingDirectory = $SmokeRoot
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo

    try {
        if (-not $process.Start()) { throw 'El runtime incluido no pudo iniciar el JAR.' }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $deadline = [DateTime]::UtcNow.AddSeconds(90)
        $health = $null
        while ([DateTime]::UtcNow -lt $deadline -and -not $process.HasExited) {
            Start-Sleep -Milliseconds 250
            try {
                $health = Invoke-RestMethod -Uri "http://127.0.0.1:$port/api/health" -TimeoutSec 1
                if ($health.status -eq 'UP') { break }
            } catch { }
        }
        if ($null -eq $health -or $health.status -ne 'UP') {
            throw 'El Backend no alcanzó readiness durante la smoke test.'
        }

        $listenerLine = netstat -ano -p tcp | Select-String -Pattern ("^\s*TCP\s+127\.0\.0\.1:" + $port + "\s+.*LISTENING\s+" + $process.Id + "\s*$")
        $externalListener = netstat -ano -p tcp | Select-String -Pattern ("^\s*TCP\s+(?:0\.0\.0\.0|\[::\]):" + $port + "\s+.*LISTENING")
        if (-not $listenerLine -or $externalListener) {
            throw 'El Backend de smoke test no quedó limitado correctamente a loopback.'
        }

        try {
            $consoleResponse = Invoke-WebRequest -Uri "http://127.0.0.1:$port/h2-console" -TimeoutSec 2 -ErrorAction Stop
            if ($consoleResponse.StatusCode -eq 200) { throw 'La consola H2 está expuesta en perfil packaged.' }
        } catch {
            if ($_.Exception.Message -eq 'La consola H2 está expuesta en perfil packaged.') { throw }
        }

        $process.StandardInput.WriteLine('shutdown')
        $process.StandardInput.Flush()
        if (-not $process.WaitForExit(10000)) {
            $process.Kill()
            $process.WaitForExit()
            throw 'El Backend no completó el cierre graceful durante la smoke test.'
        }
        $stdout = $stdoutTask.Result
        $stderr = $stderrTask.Result
        if ($process.ExitCode -ne 0) { throw "El Backend terminó con código $($process.ExitCode). $stderr" }
        if ($stdout -notmatch 'profile is active: "packaged"') { throw 'No se confirmó el perfil packaged en el log de arranque.' }
        if ($stdout -notmatch 'Graceful shutdown complete') { throw 'No se confirmó el cierre graceful.' }

        $databaseFile = Join-Path $SmokeRoot 'data\biblioteca_personal.mv.db'
        if (-not (Test-Path -LiteralPath $databaseFile -PathType Leaf)) { throw 'H2 no creó la base temporal esperada.' }
        Start-Sleep -Milliseconds 200
        if (netstat -ano -p tcp | Select-String -Pattern (":" + $port + "\s+.*LISTENING")) {
            throw 'El puerto temporal continúa ocupado después del cierre.'
        }
        return [pscustomobject]@{
            Port = $port
            Database = $databaseFile
            ExitCode = $process.ExitCode
            GracefulShutdown = $true
        }
    } finally {
        if ($process -and -not $process.HasExited) {
            $process.Kill()
            $process.WaitForExit()
        }
        foreach ($name in $variables.Keys) {
            [System.Environment]::SetEnvironmentVariable($name, $oldEnvironment[$name], 'Process')
        }
    }
}

$frontendRoot = Get-NormalPath (Join-Path $PSScriptRoot '..')
$repositoryRoot = Get-NormalPath (Join-Path $frontendRoot '..')
$tauriRoot = Join-Path $frontendRoot 'src-tauri'
$resourcesRoot = Join-Path $tauriRoot 'resources'
$backendResource = Join-Path $resourcesRoot 'backend\Biblioteca_personal.jar'
$runtimeResource = Join-Path $resourcesRoot 'runtime'
$defaultJar = Join-Path $repositoryRoot 'Backend\target\Biblioteca_personal-0.0.1-SNAPSHOT.jar'
$sourceJar = Get-NormalPath $(if ($BackendJar) { $BackendJar } else { $defaultJar })
$jdk = Find-Jdk21 $JdkHome

if (-not (Test-Path -LiteralPath $sourceJar -PathType Leaf)) {
    throw "No existe el JAR ejecutable del Backend: $sourceJar. Ejecuta Backend\\mvnw.cmd clean verify."
}

$jarEntries = & $jdk.Jar tf $sourceJar
if ($LASTEXITCODE -ne 0 -or $jarEntries -notcontains 'org/springframework/boot/loader/launch/JarLauncher.class') {
    throw 'El JAR no contiene el JarLauncher ejecutable de Spring Boot.'
}

$stagingParent = Join-Path $tauriRoot 'target'
New-Item -ItemType Directory -Path $stagingParent -Force | Out-Null
$stagingRoot = Join-Path $stagingParent ("packaging-prep-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stagingRoot | Out-Null

try {
    $analysis = Join-Path $stagingRoot 'analysis'
    $stableBackendDir = Join-Path $stagingRoot 'backend'
    $stagedRuntime = Join-Path $stagingRoot 'runtime'
    $smokeRoot = Join-Path $stagingRoot 'smoke'
    New-Item -ItemType Directory -Path $analysis,$stableBackendDir,$smokeRoot | Out-Null
    Copy-Item -LiteralPath $sourceJar -Destination (Join-Path $stableBackendDir 'Biblioteca_personal.jar')

    Push-Location $analysis
    try {
        & $jdk.Jar xf $sourceJar 'META-INF/MANIFEST.MF' 'BOOT-INF/classes' 'BOOT-INF/lib'
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo analizar el contenido del JAR.' }
    } finally { Pop-Location }
    $manifest = Get-Content -LiteralPath (Join-Path $analysis 'META-INF\MANIFEST.MF') -Raw
    if ($manifest -notmatch 'Main-Class:\s*org\.springframework\.boot\.loader\.launch\.JarLauncher') {
        throw 'El manifiesto del JAR no declara JarLauncher como Main-Class.'
    }

    $classes = Join-Path $analysis 'BOOT-INF\classes'
    $libraries = Join-Path $analysis 'BOOT-INF\lib'
    $jdepsOutput = & $jdk.Jdeps --multi-release 21 --ignore-missing-deps --recursive --print-module-deps --class-path "$libraries\*" $classes 2>&1
    if ($LASTEXITCODE -ne 0) { throw "jdeps no pudo analizar el Backend. $($jdepsOutput | Out-String)" }
    $detectedLine = $jdepsOutput | Where-Object { $_ -match '^(?:java|jdk)\.[a-z0-9.]+(?:,(?:java|jdk)\.[a-z0-9.]+)*$' } | Select-Object -Last 1
    if (-not $detectedLine) { throw 'jdeps no devolvió una lista de módulos utilizable.' }

    $conservativeModules = @(
        'java.base','java.compiler','java.desktop','java.instrument','java.management',
        'java.naming','java.net.http','java.prefs','java.rmi','java.scripting',
        'java.security.jgss','java.sql','java.sql.rowset','java.transaction.xa','java.xml',
        'jdk.crypto.ec','jdk.jfr','jdk.management','jdk.unsupported','jdk.zipfs'
    )
    $modules = @(($detectedLine -split ',') + $conservativeModules | Sort-Object -Unique)
    & $jdk.Jlink --add-modules ($modules -join ',') --output $stagedRuntime --strip-debug --no-header-files --no-man-pages
    if ($LASTEXITCODE -ne 0) { throw 'jlink no pudo generar el runtime Java.' }

    $runtimeJava = Join-Path $stagedRuntime 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $runtimeJava -PathType Leaf)) { throw 'El runtime no contiene bin\\java.exe.' }
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $runtimeVersion = (& $runtimeJava -version 2>&1 | Out-String)
    $runtimeVersionExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorPreference
    if ($runtimeVersionExitCode -ne 0 -or $runtimeVersion -notmatch 'version "21(?:\.|\")') { throw 'El runtime generado no es Java 21.' }

    $smoke = Invoke-RuntimeSmokeTest $runtimeJava (Join-Path $stableBackendDir 'Biblioteca_personal.jar') $smokeRoot

    New-Item -ItemType Directory -Path (Join-Path $resourcesRoot 'backend') -Force | Out-Null
    Remove-ExactGeneratedDirectory $runtimeResource (Join-Path $resourcesRoot 'runtime')
    New-Item -ItemType Directory -Path $runtimeResource | Out-Null
    Copy-Item -Path (Join-Path $stagedRuntime '*') -Destination $runtimeResource -Recurse -Force
    Copy-Item -LiteralPath (Join-Path $stableBackendDir 'Biblioteca_personal.jar') -Destination $backendResource -Force
    New-Item -ItemType File -Path (Join-Path $runtimeResource '.gitkeep') -Force | Out-Null
    New-Item -ItemType File -Path (Join-Path $resourcesRoot 'backend\.gitkeep') -Force | Out-Null

    $runtimeBytes = (Get-ChildItem -LiteralPath $runtimeResource -Recurse -File | Measure-Object Length -Sum).Sum
    $jarBytes = (Get-Item -LiteralPath $backendResource).Length
    Write-Host 'Preparación completada correctamente.'
    Write-Host "JDK: $($jdk.Home)"
    Write-Host "Versión: $($jdk.Version)"
    Write-Host "Módulos: $($modules -join ',')"
    Write-Host ("Runtime: {0:N2} MiB" -f ($runtimeBytes / 1MB))
    Write-Host ("JAR estable: {0:N2} MiB" -f ($jarBytes / 1MB))
    Write-Host "Smoke health: UP; puerto: $($smoke.Port); cierre graceful: $($smoke.GracefulShutdown)"
} finally {
    Remove-ExactGeneratedDirectory $stagingRoot $stagingRoot
}
