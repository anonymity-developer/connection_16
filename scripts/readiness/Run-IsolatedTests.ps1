[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Push-Location (Join-Path $repo 'backend')
try {
  if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'JDK 21 (javac) required.' }
  $jdk = (& javac -version 2>&1 | Out-String)
  if ($jdk -notmatch 'javac 21(?:\.|\s)') { throw 'Select JDK 21 before running tests.' }
  & docker info --format '{{.ServerVersion}}' | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop server unavailable.' }
  # Fixed allowlist only: each integration class binds fresh @Container JDBC/Redis endpoints.
  # No Compose up/down, no SQL client, no .env loading and no --tests user input.
  & .\gradlew.bat --no-daemon test --tests 'com.tagme.auth.MattermostSenderSessionTest' --tests 'com.tagme.auth.AuthPropertiesBindingTest' --tests 'com.tagme.participation.OperationalReadinessTest'
  if ($LASTEXITCODE -ne 0) { throw 'Unit tests failed. Stop before integration.' }
  & .\gradlew.bat --no-daemon integrationTest --tests 'com.tagme.integration.AuthIntegrationTest' --tests 'com.tagme.integration.ParticipationIntegrationTest' --tests 'com.tagme.integration.ResultsIntegrationTest' --tests 'com.tagme.integration.ContentIntegrationTest' --tests 'com.tagme.integration.ReactionIntegrationTest' --tests 'com.tagme.integration.DiscoveryIntegrationTest' --tests 'com.tagme.integration.OperationsIntegrationTest'
  if ($LASTEXITCODE -ne 0) { throw 'Isolated integration failed. See backend/build/reports/tests.' }
} finally { Pop-Location }
