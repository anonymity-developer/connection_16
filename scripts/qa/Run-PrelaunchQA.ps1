[CmdletBinding()]
param([switch]$Backend, [switch]$Browser)
$ErrorActionPreference = 'Stop'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
function Checked([scriptblock]$Step) { & $Step; if ($LASTEXITCODE -ne 0) { throw 'QA step failed. Stop and inspect the local report.' } }
Push-Location $repo
try {
  Checked { git diff --check }
  Checked { npm.cmd --prefix frontend run lint }
  Checked { npm.cmd --prefix frontend run build }
  Checked { npm.cmd --prefix frontend run test:unit }
  if ($Backend) {
    if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'JDK 21 required.' }
    $version = (& javac -version 2>&1 | Out-String)
    if ($version -notmatch 'javac 21(?:\.|\s)') { throw 'Select JDK 21. Do not substitute JDK 17 or H2.' }
    Checked { docker info --format '{{.ServerVersion}}' | Out-Null }
    Push-Location backend
    try {
      Checked { .\gradlew.bat --no-daemon test bootJar }
      # Existing @Container MySQL/Redis and @MockitoBean MM/provider; never Compose databases.
      Checked { .\gradlew.bat --no-daemon integrationTest --tests 'com.tagme.integration.EventRehearsalIntegrationTest' --tests 'com.tagme.integration.InfrastructureIntegrationTest' --tests 'com.tagme.integration.AuthIntegrationTest' --tests 'com.tagme.integration.ParticipationIntegrationTest' --tests 'com.tagme.integration.ResultsIntegrationTest' --tests 'com.tagme.integration.ContentIntegrationTest' --tests 'com.tagme.integration.ReactionIntegrationTest' --tests 'com.tagme.integration.DiscoveryIntegrationTest' --tests 'com.tagme.integration.OperationsIntegrationTest' }
    } finally { Pop-Location }
  }
  if ($Browser) {
    Push-Location frontend
    try {
      foreach ($suite in @('test:auth-nfc-ui','test:participation-ui','test:results-ui','test:content-ui','test:reactions-ui','test:discovery-ui','test:operations-ui')) {
        Checked { npm.cmd run $suite }
      }
      Checked { node scripts/check-polish-ui.mjs }
      Checked { node scripts/check-awards-ui.mjs }
    } finally { Pop-Location }
  }
  Write-Output 'Selected checks completed. Omitted Backend/Browser checks remain UNVERIFIED.'
} finally { Pop-Location }
