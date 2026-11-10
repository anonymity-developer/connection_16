[CmdletBinding()]
param([string]$Snapshot,[string]$LocalOrigin)
$ErrorActionPreference='Stop'
$repo=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
# No .env/credential lookup, SQL connection, login, MM or database writes.
$arguments=@('scripts/readiness/opening-check.mjs')
if($Snapshot){$arguments+=@('--snapshot',(Resolve-Path -LiteralPath $Snapshot -ErrorAction Stop).Path)}
if($LocalOrigin){$arguments+=@('--local-origin',$LocalOrigin)}
Push-Location $repo
try {
  & node @arguments
  if($LASTEXITCODE -eq 2){Write-Host 'NO-GO pending evidence/approval. READY items describe metadata only.'}
  elseif($LASTEXITCODE -ne 0){throw 'Preflight could not run. No input values or credentials are printed.'}
}finally{Pop-Location}
