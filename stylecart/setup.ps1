param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$storeRoot = $PSScriptRoot
$envPath = Join-Path $storeRoot '.env'
$utf8 = New-Object System.Text.UTF8Encoding $false

function New-RandomBase64([int]$Length) {
    $bytes = New-Object byte[] $Length
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

if (-not (Test-Path -LiteralPath $envPath)) {
    $content = [IO.File]::ReadAllText((Join-Path $storeRoot '.env.example'))
    $content = $content.Replace('STYLECART_DB_PASSWORD=', 'STYLECART_DB_PASSWORD=' + (New-RandomBase64 24))
    $content = $content.Replace('STYLECART_ADMIN_PASSWORD=', 'STYLECART_ADMIN_PASSWORD=' + (New-RandomBase64 24))
    $content = $content.Replace('STYLECART_SETTINGS_KEY=', 'STYLECART_SETTINGS_KEY=' + (New-RandomBase64 32))
    [IO.File]::WriteAllText($envPath, $content, $utf8)
    Write-Output 'Created stylecart/.env with independent local credentials. Existing SwiftDrop files were not changed.'
}

$config = @{}
foreach ($line in [IO.File]::ReadAllLines($envPath)) {
    if ($line -match '^([A-Z_]+)=(.*)$') { $config[$Matches[1]] = $Matches[2].Trim() }
}
foreach ($name in @('STYLECART_DB_PASSWORD','STYLECART_ADMIN_PASSWORD','STYLECART_SETTINGS_KEY','STYLECART_TLS_PASSWORD')) {
    if (-not $config[$name]) { throw "Set $name in stylecart/.env before continuing." }
}
if ([Convert]::FromBase64String($config['STYLECART_SETTINGS_KEY']).Length -ne 32) { throw 'STYLECART_SETTINGS_KEY must contain 32 decoded bytes.' }

if (-not $JavaHome) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = (Get-Command java).Source
    $info.Arguments = '-XshowSettings:properties -version'
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($info)
    $properties = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($properties -match '(?m)^\s*java.home = (.+)$') { $JavaHome = $Matches[1].Trim() }
}
if (-not $JavaHome) { throw 'Install Java 21 or pass -JavaHome with the JDK directory.' }
$keytoolPath = Join-Path $JavaHome 'bin/keytool.exe'
$caPath = Join-Path $JavaHome 'lib/security/cacerts'
if (-not (Test-Path -LiteralPath $keytoolPath) -or -not (Test-Path -LiteralPath $caPath)) { throw 'Pass -JavaHome with the JDK directory used by Maven.' }

function Invoke-LocalKeytool([string[]]$Arguments, [switch]$AllowFailure) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $keytoolPath @Arguments *> $null
        $result = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($result -ne 0 -and -not $AllowFailure) { throw 'Local TLS setup failed. Check the JDK and STYLECART_TLS_PASSWORD.' }
    return $result
}

$tlsDirectory = Join-Path $storeRoot 'backend/target/local-tls'
New-Item -ItemType Directory -Path $tlsDirectory -Force | Out-Null
$keystorePath = Join-Path $tlsDirectory 'stylecart.p12'
$certificatePath = Join-Path $tlsDirectory 'stylecart-cert.pem'
$truststorePath = Join-Path $tlsDirectory 'notification-truststore'
$tlsPassword = $config['STYLECART_TLS_PASSWORD']
if (-not (Test-Path -LiteralPath $keystorePath)) {
    $null = Invoke-LocalKeytool -Arguments @('-genkeypair','-alias','stylecart','-keyalg','RSA','-keysize','2048','-storetype','PKCS12','-keystore',$keystorePath,'-storepass',$tlsPassword,'-keypass',$tlsPassword,'-validity','365','-dname','CN=localhost, OU=StyleCart, O=StyleCart, L=Colombo, C=LK','-ext','SAN=DNS:localhost,IP:127.0.0.1','-noprompt')
}
$null = Invoke-LocalKeytool -Arguments @('-exportcert','-rfc','-alias','stylecart','-keystore',$keystorePath,'-storepass',$tlsPassword,'-file',$certificatePath)
if (-not (Test-Path -LiteralPath $truststorePath)) { Copy-Item -LiteralPath $caPath -Destination $truststorePath }
$aliasExists = Invoke-LocalKeytool -Arguments @('-list','-alias','stylecart-local','-keystore',$truststorePath,'-storepass','changeit') -AllowFailure
if ($aliasExists -ne 0) {
    $null = Invoke-LocalKeytool -Arguments @('-importcert','-noprompt','-alias','stylecart-local','-file',$certificatePath,'-keystore',$truststorePath,'-storepass','changeit')
}
function BashQuote([string]$Value) { return "'" + $Value.Replace("'", "'\''") + "'" }
$javaOptions = '-Djavax.net.ssl.trustStore="' + ($truststorePath -replace '\\','/') + '" -Djavax.net.ssl.trustStorePassword=changeit'
$exports = @(
    '# Source this in the SwiftDrop terminal before starting its services.',
    '# If you need other JAVA_TOOL_OPTIONS, append them after sourcing this file.',
    ('export JAVA_TOOL_OPTIONS=' + (BashQuote $javaOptions)),
    'export SWIFTDROP_WEBHOOKS_ALLOWED_ORIGINS=https://localhost:8090',
    'export SWIFTDROP_WEBHOOKS_CONSUMER_ENABLED=true',
    'export SWIFTDROP_WEBHOOKS_WORKER_ENABLED=true'
)
[IO.File]::WriteAllText((Join-Path $tlsDirectory 'swiftdrop-env.sh'), ($exports -join "`n") + "`n", $utf8)
Write-Output 'StyleCart HTTPS and a separate Notification truststore are ready. System/JDK trust settings were not changed.'
Write-Output 'Admin email: admin@stylecart.local (unless changed in stylecart/.env). Read STYLECART_ADMIN_PASSWORD in that file; do not share it.'
Write-Output 'Next, from stylecart/: docker compose up -d --wait db'
