$ErrorActionPreference = 'Stop'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
# Only pure Java records/collections are compiled here; Spring/JDK21/SQL are NOT validated.
$target = Join-Path ([IO.Path]::GetTempPath()) ('tagme-award-calculator-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $target | Out-Null
try {
  & javac --release 17 -d $target (Join-Path $repo 'backend/src/main/java/com/tagme/operations/AwardModels.java') (Join-Path $repo 'backend/src/main/java/com/tagme/operations/AwardCalculator.java') (Join-Path $repo 'backend/src/test/java/com/tagme/operations/AwardsCalculatorChecks.java')
  if ($LASTEXITCODE -ne 0) { throw 'Pure calculator compile failed' }
  & java -cp $target com.tagme.operations.AwardsCalculatorChecks
  if ($LASTEXITCODE -ne 0) { throw 'Pure calculator regression failed' }
} finally { Remove-Item -LiteralPath $target -Recurse -Force }
