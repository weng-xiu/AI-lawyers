<#
.SYNOPSIS
    MySQL dialect coupling scanner (prep for #13 DM/Kingbase adaptation)

.DESCRIPTION
    Scans mapper XML, Java sources and sql/ scripts for MySQL-specific syntax
    and emits a categorized hit list (file:line:text) to assess migration cost
    to KingbaseES (MySQL-compatible mode) or Dameng (DM). Read-only.

.PARAMETER RepoRoot
    Repository root. Defaults to the parent of the script directory.

.PARAMETER MarkdownOut
    Optional. Also writes the inventory (with per-construct hit lists) to this
    Markdown path (absolute or relative to repo root).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\scan-mysql-dialect.ps1
    powershell -ExecutionPolicy Bypass -File scripts\scan-mysql-dialect.ps1 -MarkdownOut doc\mysql-dialect-inventory.md
#>
param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [string]$MarkdownOut,
    [string[]]$TitleNote
)

$ErrorActionPreference = "Stop"

# Rule name => case-insensitive regex.
$rules = [ordered]@{
    "ON DUPLICATE KEY UPDATE" = "ON\s+DUPLICATE\s+KEY\s+UPDATE"
    "INSERT IGNORE"           = "INSERT\s+IGNORE"
    "REPLACE INTO"            = "REPLACE\s+INTO"
    "VALUES(col) in upsert"   = "=\s*values\s*\("
    "GROUP_CONCAT"            = "GROUP_CONCAT"
    "DATE_FORMAT"             = "DATE_FORMAT"
    "STR_TO_DATE"             = "STR_TO_DATE"
    "IFNULL"                  = "IFNULL"
    "UNIX_TIMESTAMP"          = "UNIX_TIMESTAMP"
    "FROM_UNIXTIME"           = "FROM_UNIXTIME"
    "NOW/CURDATE/SYSDATE"     = "\b(NOW|CURDATE|SYSDATE|CURTIME)\s*\("
    "DATE_ADD/SUB/INTERVAL"   = "DATE_(ADD|SUB)\s*\(|\bINTERVAL\s+"
    "DATEDIFF/TIMESTAMPDIFF"  = "(DATEDIFF|TIMESTAMPDIFF)\s*\("
    "YEARWEEK/LAST_DAY"       = "(YEARWEEK|LAST_DAY)\s*\("
    "LIMIT pagination"        = "\bLIMIT\s+\d+|\blimit\s+#\{|LIMIT\s+\?"
    "DELETE ... LIMIT"        = "delete\s+.*\blimit\s+"
    "Backtick identifiers"    = [char]0x60
    "MySQL CREATE options"    = "ENGINE\s*=|AUTO_INCREMENT|UNSIGNED|CHARACTER\s+SET|COLLATE|ON\s+UPDATE\s+CURRENT_TIMESTAMP"
    "MySQL types"             = "\b(tinyint|mediumtext|longtext|datetime|enum)\b"
}

# Dameng (Oracle-style) rewrite hint per rule.
$dmHint = @{
    "ON DUPLICATE KEY UPDATE" = "MERGE INTO (Kingbase MySQL mode supports it natively)"
    "INSERT IGNORE"           = "MERGE INTO / existence check"
    "REPLACE INTO"            = "MERGE INTO"
    "VALUES(col) in upsert"   = "Rework together with ON DUPLICATE KEY (EXCLUDED / dual table)"
    "GROUP_CONCAT"            = "LISTAGG / WM_CONCAT"
    "DATE_FORMAT"             = "TO_CHAR"
    "STR_TO_DATE"             = "TO_DATE"
    "IFNULL"                  = "NVL / COALESCE"
    "UNIX_TIMESTAMP"          = "DATEDIFF in seconds or custom UDF"
    "FROM_UNIXTIME"           = "TO_DATE('1970-01-01') + numeric"
    "NOW/CURDATE/SYSDATE"     = "SYSDATE / CURRENT_TIMESTAMP (verify semantics)"
    "DATE_ADD/SUB/INTERVAL"   = "INTERVAL 'n' DAY / DATEADD"
    "DATEDIFF/TIMESTAMPDIFF"  = "DATEDIFF argument order/unit differs"
    "YEARWEEK/LAST_DAY"       = "Custom expression required"
    "LIMIT pagination"        = "DM supports LIMIT; ROWNUM fallback; switch PageHelper helperDialect"
    "DELETE ... LIMIT"        = "DM disallows LIMIT in DELETE; wrap PK subquery"
    "Backtick identifiers"    = "Double-quoted identifiers (Kingbase MySQL mode keeps backticks)"
    "MySQL CREATE options"    = "Drop ENGINE/AUTO_INCREMENT/UNSIGNED; use sequence/IDENTITY"
    "MySQL types"             = "TINYINT->SMALLINT, DATETIME->TIMESTAMP, *TEXT->CLOB/VARCHAR"
}

$scanDirs = @(
    (Join-Path $RepoRoot "ai-system\src"),
    (Join-Path $RepoRoot "ai-admin\src"),
    (Join-Path $RepoRoot "ai-framework\src"),
    (Join-Path $RepoRoot "ai-common\src"),
    (Join-Path $RepoRoot "ai-quartz\src"),
    (Join-Path $RepoRoot "ai-generator\src"),
    (Join-Path $RepoRoot "sql")
) | Where-Object { Test-Path $_ }

$files = foreach ($d in $scanDirs) {
    Get-ChildItem -Path $d -Recurse -Include *.xml,*.java,*.sql -File |
        Where-Object { $_.FullName -notmatch "\\target\\" }
}

$results = [ordered]@{}
foreach ($name in $rules.Keys) { $results[$name] = New-Object System.Collections.Generic.List[object] }

foreach ($f in $files) {
    $rel = $f.FullName.Substring($RepoRoot.Length).TrimStart("\", "/")
    $lineNo = 0
    foreach ($line in [System.IO.File]::ReadLines($f.FullName)) {
        $lineNo++
        foreach ($name in $rules.Keys) {
            if ($line -match $rules[$name]) {
                $results[$name].Add([pscustomobject]@{
                    File = $rel; Line = $lineNo; Text = $line.Trim()
                }) | Out-Null
            }
        }
    }
}

$total = 0
foreach ($name in $rules.Keys) {
    $hits = $results[$name]
    $total += $hits.Count
    Write-Host ("{0,-28} {1,5}" -f $name, $hits.Count) -ForegroundColor $(if ($hits.Count) { "Yellow" } else { "Gray" })
}
Write-Host ("{0,-28} {1,5}" -f "TOTAL", $total) -ForegroundColor Cyan

if ($MarkdownOut) {
    $out = New-Object System.Collections.Generic.List[string]
    $out.Add("# MySQL Dialect Inventory (auto-generated by scan-mysql-dialect.ps1 - do not edit)")
    $out.Add("")
    $out.Add("> Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm'); files scanned: $($files.Count)")
    $out.Add("> Command: ``scripts/scan-mysql-dialect.ps1 -MarkdownOut doc/mysql-dialect-inventory.md`` (read-only scan of mapper XML / Java / sql, target/ excluded)")
    foreach ($n in $TitleNote) { if ($n) { $out.Add($n) } }
    $out.Add("")
    $out.Add("| Construct | Hits | Dameng rewrite hint |")
    $out.Add("|-----------|------|---------------------|")
    foreach ($name in $rules.Keys) {
        $out.Add("| $name | $($results[$name].Count) | $($dmHint[$name]) |")
    }
    $out.Add("")
    foreach ($name in $rules.Keys) {
        $hits = $results[$name]
        if ($hits.Count -eq 0) { continue }
        $out.Add("## $name ($($hits.Count))")
        $out.Add("")
        foreach ($h in $hits) {
            $t = $h.Text -replace "\|", "\|"
            if ($t.Length -gt 160) { $t = $t.Substring(0, 160) + " ..." }
            $out.Add("- ``$($h.File):$($h.Line)`` - ``$t``")
        }
        $out.Add("")
    }
    $path = if ([System.IO.Path]::IsPathRooted($MarkdownOut)) { $MarkdownOut } else { Join-Path $RepoRoot $MarkdownOut }
    $dir = Split-Path $path -Parent
    if ($dir -and !(Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllLines($path, $out, (New-Object System.Text.UTF8Encoding($true)))
    Write-Host "Inventory written: $path" -ForegroundColor Green
}
