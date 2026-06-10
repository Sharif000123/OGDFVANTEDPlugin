param(
    [Parameter(Mandatory = $true)]
    [string]$VantedCoreJar,

    [string]$OutputDirectory = (Join-Path $PSScriptRoot "out")
)

$ErrorActionPreference = "Stop"

$coreJar = (Resolve-Path -LiteralPath $VantedCoreJar).Path
$javacTool = (Get-Command javac -ErrorAction Stop).Source
$jarCommand = Get-Command jar -ErrorAction SilentlyContinue

if ($jarCommand) {
    $jarTool = $jarCommand.Source
} else {
    $probeInfo = New-Object System.Diagnostics.ProcessStartInfo
    $probeInfo.FileName = $javacTool
    $probeInfo.Arguments = "-J-XshowSettings:properties -version"
    $probeInfo.UseShellExecute = $false
    $probeInfo.RedirectStandardOutput = $true
    $probeInfo.RedirectStandardError = $true

    $probe = New-Object System.Diagnostics.Process
    $probe.StartInfo = $probeInfo
    [void]$probe.Start()
    $settings = $probe.StandardError.ReadToEnd() + "`n" + $probe.StandardOutput.ReadToEnd()
    $probe.WaitForExit()
    if ($probe.ExitCode -ne 0) {
        throw "Could not inspect the active JDK."
    }

    $javaHomeLine = $settings -split "\r?\n" |
        Where-Object { $_ -match "^\s*java\.home\s*=" } |
        Select-Object -First 1
    if (-not $javaHomeLine) {
        throw "Could not determine the active JDK directory."
    }
    $javaHome = ($javaHomeLine -split "=", 2)[1].Trim()
    $jarTool = Join-Path $javaHome "bin\jar.exe"
}

if (-not (Test-Path -LiteralPath $jarTool -PathType Leaf)) {
    throw "The JDK jar tool was not found. Install a full JDK or add its bin directory to PATH."
}

$sourceRoot = Join-Path $PSScriptRoot "src"
$classesDirectory = Join-Path $OutputDirectory "classes"
$jarPath = Join-Path $OutputDirectory "OgdfIntegration.jar"
$sources = Get-ChildItem -Path $sourceRoot -Recurse -Filter "*.java"

if ($sources.Count -eq 0) {
    throw "No Java source files were found below $sourceRoot."
}

New-Item -ItemType Directory -Path $classesDirectory -Force | Out-Null

& $javacTool -encoding UTF-8 -cp $coreJar -d $classesDirectory $sources.FullName
if ($LASTEXITCODE -ne 0) {
    throw "Java compilation failed."
}

Copy-Item -LiteralPath (Join-Path $sourceRoot "OgdfIntegration.xml") `
    -Destination (Join-Path $classesDirectory "OgdfIntegration.xml") -Force

& $jarTool --create --file $jarPath -C $classesDirectory .
if ($LASTEXITCODE -ne 0) {
    throw "JAR packaging failed."
}

Write-Host "Created $jarPath"
