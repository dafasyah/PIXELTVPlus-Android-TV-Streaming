# PIXELTV PostToolUse guard.
# Reads the hook JSON from stdin and emits project-specific reminders as
# additionalContext after an Edit/Write. Always exits 0 (never blocks).
$ErrorActionPreference = 'SilentlyContinue'

$raw = [Console]::In.ReadToEnd()
if (-not $raw) { exit 0 }
try { $data = $raw | ConvertFrom-Json } catch { exit 0 }

$path = $data.tool_input.file_path
if (-not $path) { exit 0 }

$messages = @()

# Hard rule #3: StreamSniffer / MediaStream must stay pure (JVM-testable, no android.* imports).
if ($path -match 'stream[\\/](StreamSniffer|MediaStream)\.kt$') {
    if (Test-Path $path) {
        $hits = Select-String -Path $path -Pattern '^\s*import\s+android' -ErrorAction SilentlyContinue
        if ($hits) {
            $name = [System.IO.Path]::GetFileName($path)
            $messages += "PIXELTV rule violated: $name imports android.* — keep StreamSniffer/MediaStream pure so unit tests stay JVM-only. Get cookies via CookieManager in the Activity and pass them into inspect(). See CLAUDE.md rule #3 + implementing-stream-features skill."
        }
    }
}

# Versioning rule: bump versionCode + versionName + changelog together.
if ($path -match 'build\.gradle\.kts$') {
    $messages += "PIXELTV reminder: bump BOTH versionCode (int +1) and versionName together, then update README changelog, OverlayMenu.APP_VERSION, and the Home footer string. See CLAUDE.md > Versioning."
}

if ($messages.Count -gt 0) {
    $ctx = ($messages -join ' ')
    $out = @{ hookSpecificOutput = @{ hookEventName = 'PostToolUse'; additionalContext = $ctx } } | ConvertTo-Json -Compress
    Write-Output $out
}

exit 0
