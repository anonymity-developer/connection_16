[CmdletBinding()]
param([switch]$Backend,[switch]$Browser,[switch]$Faults,[switch]$FullRegression)
$ErrorActionPreference = 'Stop'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$results = [ordered]@{ Frontend='UNVERIFIED'; BackendBuild='UNVERIFIED'; ApiDatabaseJourney='UNVERIFIED'; Browser='UNVERIFIED'; DependencyFaults='UNVERIFIED'; PhysicalDevice='UNVERIFIED'; ApplicationRestart='UNVERIFIED' }
$durations = [ordered]@{}
function Checked([string]$Name,[scriptblock]$Action) {
  $timer = [Diagnostics.Stopwatch]::StartNew()
  $global:LASTEXITCODE = 0
  try { & $Action; if ($LASTEXITCODE -ne 0) { throw "Step failed: $Name" }; $results[$Name] = 'PASS' }
  catch { $results[$Name] = 'FAIL'; throw }
  finally { $timer.Stop(); $durations[$Name] = [math]::Round($timer.Elapsed.TotalSeconds,2) }
}
$previousRunId = [Environment]::GetEnvironmentVariable('TAGME_REHEARSAL_RUN_ID','Process')
$previousFaultFlag = [Environment]::GetEnvironmentVariable('TAGME_REHEARSAL_FAULTS','Process')
Push-Location $repo
try {
  $flags = @()
  if ($Backend) { $flags += '--backend' }; if ($Browser) { $flags += '--browser' }; if ($Faults) { $flags += '--faults' }; if ($FullRegression) { $flags += '--full-regression' }
  $planJson = & node scripts/rehearsal/plan.mjs @flags
  if ($LASTEXITCODE -ne 0) { throw 'Local-only rehearsal preflight rejected the configuration. No DB actions started.' }
  $plan = $planJson | ConvertFrom-Json
  Checked 'Frontend' {
    git diff --check; if ($LASTEXITCODE -ne 0) { throw 'diff check failed' }
    npm.cmd --prefix frontend run lint; if ($LASTEXITCODE -ne 0) { throw 'lint failed' }
    npm.cmd --prefix frontend run build; if ($LASTEXITCODE -ne 0) { throw 'build failed' }
    npm.cmd --prefix frontend run test:unit
  }
  if ($Backend) {
    if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'JDK21 required. Backend remains UNVERIFIED.' }
    $version = (& javac -version 2>&1 | Out-String)
    if ($version -notmatch 'javac 21(?:\.|\s)') { throw 'Select JDK21. Do not replace MySQL with H2.' }
    & docker info --format '{{.ServerVersion}}' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Docker unavailable. No Compose resets are attempted.' }
    $env:TAGME_REHEARSAL_RUN_ID = [guid]::NewGuid().ToString()
    if ($Faults) { $env:TAGME_REHEARSAL_FAULTS = '1' } else { Remove-Item Env:TAGME_REHEARSAL_FAULTS -ErrorAction SilentlyContinue }
    Push-Location backend
    try {
      Checked 'BackendBuild' { .\gradlew.bat --no-daemon test bootJar }
      $gradleArgs = @('--no-daemon','integrationTest')
      foreach ($class in $plan.backendClasses) { $gradleArgs += @('--tests',[string]$class) }
      Checked 'ApiDatabaseJourney' { & .\gradlew.bat @gradleArgs }
      if ($Faults) {
        $results['DependencyFaults'] = 'FAIL'
        $xmlPath = Join-Path $repo 'backend\build\test-results\integrationTest\TEST-com.tagme.integration.EventRehearsalIntegrationTest.xml'
        if (-not (Test-Path -LiteralPath $xmlPath)) { throw 'Fault test report missing. Do not claim PASS.' }
        [xml]$xml = Get-Content -LiteralPath $xmlPath -Raw
        $cases = @($xml.SelectNodes("//testcase[starts-with(@name,'pausedEphemeralDependenciesFailClosedAndRecoverWithoutDeletingData')]"))
        if ($cases.Count -ne 1 -or $cases[0].SelectNodes('skipped|failure|error').Count -ne 0) { throw 'Fault test missing, skipped or failed.' }
        $results['DependencyFaults'] = 'PASS'
      }
    } finally { Pop-Location }
  }
  if ($Browser) {
    Push-Location frontend
    try {
      Checked 'Browser' {
        foreach ($suite in $plan.browserSuites) { & npm.cmd run $suite; if ($LASTEXITCODE -ne 0) { throw "Browser contract failed: $suite" } }
        foreach ($script in $plan.browserScripts) { & node $script; if ($LASTEXITCODE -ne 0) { throw "Browser fixture failed: $script" } }
      }
    } finally { Pop-Location }
  }
} finally {
  [Environment]::SetEnvironmentVariable('TAGME_REHEARSAL_RUN_ID',$previousRunId,'Process')
  [Environment]::SetEnvironmentVariable('TAGME_REHEARSAL_FAULTS',$previousFaultFlag,'Process')
  $folder = Join-Path $repo 'output\rehearsal'
  New-Item -ItemType Directory -Force -Path $folder | Out-Null
  $report = Join-Path $folder ('rehearsal-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.json')
  [ordered]@{ recordedAt=(Get-Date).ToUniversalTime().ToString('o'); results=$results; seconds=$durations; note='PASS means the selected command exited successfully. Inspect JUnit/fixture reports; omitted checks remain UNVERIFIED. No production URL or database is accepted.' } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $report -Encoding UTF8
  Write-Output "Rehearsal status report: $report"
  Pop-Location
}
