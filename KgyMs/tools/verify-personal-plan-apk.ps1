$ErrorActionPreference = 'Stop'
$androidRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$manifestPath = Join-Path $androidRoot 'app/build/intermediates/merged_manifests/personalPlanEmulator/processPersonalPlanEmulatorManifest/AndroidManifest.xml'
if (-not (Test-Path -LiteralPath $manifestPath)) { throw 'Build assemblePersonalPlanEmulator before verification.' }
[xml]$manifest = Get-Content -Raw -LiteralPath $manifestPath
$namespace = 'http://schemas.android.com/apk/res/android'
if ($manifest.manifest.package -ne 'com.kgyms.app.personalplanemu') { throw 'Wrong application ID' }
$application = $manifest.manifest.application
if (@($application.provider | Where-Object { $_.GetAttribute('name',$namespace) -eq 'com.google.firebase.provider.FirebaseInitProvider' }).Count) { throw 'Firebase auto-init provider must be removed' }
if (@($application.activity | Where-Object { $_.GetAttribute('name',$namespace) -eq 'com.kgyms.app.MainActivity' }).Count) { throw 'Production launcher must be removed' }
if (-not @($application.activity | Where-Object { $_.GetAttribute('name',$namespace) -eq 'com.kgyms.app.personalplan.PersonalPlanEmulatorActivity' }).Count) { throw 'Dedicated launcher missing' }
$sdk = 'C:/Users/ADMIN/AppData/Local/Android/Sdk'
$aapt = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory | Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'aapt2.exe' } | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
$apk = Join-Path $androidRoot 'app/build/outputs/apk/personalPlanEmulator/app-personalPlanEmulator.apk'
$resources = & $aapt dump resources $apk
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect APK resources' }
if ($resources -match 'string/(google_app_id|google_api_key|firebase_database_url|gcm_defaultSenderId|project_id)(\s|:)') { throw 'Google Services generated options found; refusing network tests' }
Write-Output 'PASS: separate APK ID, dedicated launcher, no Firebase auto-init, no generated Google Services options.'
Write-Output $apk
