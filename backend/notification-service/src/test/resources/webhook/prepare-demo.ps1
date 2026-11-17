param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../../../../..')).Path
$demo = Join-Path $repo 'backend/notification-service/target/webhook-demo'
New-Item -ItemType Directory -Path $demo -Force | Out-Null
$cert = Join-Path $demo 'receiver-cert.pem'
$key = Join-Path $demo 'receiver-key.pem'
$trust = Join-Path $demo 'java-truststore'
$openssl = Get-Command openssl -ErrorAction SilentlyContinue
if ($openssl) { $openssl = $openssl.Source }
else { $openssl = 'C:\Program Files\Git\mingw64\bin\openssl.exe' }
if (-not (Test-Path -LiteralPath $openssl)) { throw 'Install OpenSSL or use Git for Windows.' }
if (-not $JavaHome) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = (Get-Command java).Source
    $info.Arguments = '-XshowSettings:properties -version'
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($info)
    $properties = $process.StandardError.ReadToEnd() -split "`n"
    $process.WaitForExit()
    $homeLine = $properties | Select-String '^\s*java.home = (.+)$' | Select-Object -First 1
    if ($homeLine) { $JavaHome = $homeLine.Matches[0].Groups[1].Value.Trim() }
}
$keytool = Join-Path $JavaHome 'bin/keytool.exe'
$cacerts = Join-Path $JavaHome 'lib/security/cacerts'
if (-not (Test-Path -LiteralPath $keytool) -or -not (Test-Path -LiteralPath $cacerts)) { throw 'Pass -JavaHome with the JDK directory used by Maven.' }
if ((Test-Path -LiteralPath $cert) -xor (Test-Path -LiteralPath $key)) { throw 'Incomplete demo certificate pair. Restore the matching certificate/key before continuing.' }
if (-not (Test-Path -LiteralPath $cert)) {
    & $openssl req -x509 -newkey rsa:2048 -nodes -days 365 -keyout $key -out $cert -subj '/CN=localhost' -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1'
    if ($LASTEXITCODE -ne 0) { throw 'Demo certificate generation failed.' }
}
if (-not (Test-Path -LiteralPath $trust)) { Copy-Item -LiteralPath $cacerts -Destination $trust }
& $keytool -list -alias swiftdrop-webhook-demo -keystore $trust -storepass changeit *> $null
if ($LASTEXITCODE -ne 0) {
    & $keytool -importcert -noprompt -alias swiftdrop-webhook-demo -file $cert -keystore $trust -storepass changeit
    if ($LASTEXITCODE -ne 0) { throw 'Demo truststore import failed.' }
}
function BashQuote([string]$value) { return "'" + $value.Replace("'", "'\''") + "'" }
$options = '-Djavax.net.ssl.trustStore="' + ($trust -replace '\\','/') + '" -Djavax.net.ssl.trustStorePassword=changeit'
$lines = @(
    'export WEBHOOK_DEMO_CERT=' + (BashQuote ($cert -replace '\\','/'))
    'export WEBHOOK_DEMO_KEY=' + (BashQuote ($key -replace '\\','/'))
    'export JAVA_TOOL_OPTIONS=' + (BashQuote $options)
)
[System.IO.File]::WriteAllText((Join-Path $demo 'demo-env.sh'), ($lines -join "`n") + "`n", (New-Object System.Text.UTF8Encoding $false))
Write-Output 'Prepared local HTTPS receiver and a separate Java truststore. System trust settings are unchanged.'
Write-Output 'In Git Bash at repository root: source backend/notification-service/target/webhook-demo/demo-env.sh'
Write-Output 'Webhook URL: https://localhost:9090/webhook'
