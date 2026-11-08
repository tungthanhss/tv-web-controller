$ErrorActionPreference = 'Stop'
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'D:\Android\Sdk' }
$tools = Join-Path $sdk 'build-tools\35.0.0'
$android = Join-Path $sdk 'platforms\android-33\android.jar'
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force build,build\classes,build\dex | Out-Null
function Run-Tool($program, $arguments) { & $program @arguments; if ($LASTEXITCODE -ne 0) { throw "$program failed ($LASTEXITCODE)" } }
Run-Tool (Join-Path $tools 'aapt2.exe') @('compile','--dir','res','-o','build\resources.zip')
Run-Tool (Join-Path $tools 'aapt2.exe') @('link','-o','build\base-raw.apk','-I',$android,'--manifest','AndroidManifest.xml','-A','assets','build\resources.zip')
# Windows AAPT2 can write nested asset names with backslashes. Android uses forward slashes.
Run-Tool 'python' @('normalize_apk.py','build/base-raw.apk','build/base.apk')
Run-Tool 'javac' @('-encoding','UTF-8','-source','8','-target','8','-classpath',$android,'-d','build\classes','src\vn\lan\tvbrowser\BrowserActivity.java','src\vn\lan\tvbrowser\AdBlock.java')
$classes = @(Get-ChildItem build\classes -Recurse -Filter *.class | ForEach-Object FullName)
Run-Tool (Join-Path $tools 'd8.bat') (@('--lib',$android,'--min-api','23','--output','build\dex') + $classes)
Copy-Item build\base.apk build\unsigned.apk -Force
Run-Tool 'jar' @('uf','build\unsigned.apk','-C','build\dex','classes.dex')
Run-Tool (Join-Path $tools 'zipalign.exe') @('-f','4','build\unsigned.apk','build\aligned.apk')
if (!(Test-Path debug.keystore)) {
  Run-Tool 'keytool' @('-genkeypair','-keystore','debug.keystore','-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=LAN Browser Development','-keyalg','RSA','-validity','10000')
}
Run-Tool (Join-Path $tools 'apksigner.bat') @('sign','--ks','debug.keystore','--ks-pass','pass:android','--out','build\LAN-Browser.apk','build\aligned.apk')
Run-Tool (Join-Path $tools 'apksigner.bat') @('verify','build\LAN-Browser.apk')
Write-Output 'Built: build\LAN-Browser.apk'
