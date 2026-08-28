$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$prefix = "http://127.0.0.1:8765/"
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add($prefix)
try {
    $listener.Start()
} catch {
    Write-Host "Port 8765 is busy. Close the other server or change the port."
    exit 1
}
Write-Host "Serving $root"
Write-Host "Open ${prefix}"
$mime = @{
    ".html"="text/html; charset=utf-8"; ".htm"="text/html; charset=utf-8"
    ".css"="text/css"; ".js"="application/javascript"
    ".png"="image/png"; ".gif"="image/gif"; ".jpg"="image/jpeg"; ".jpeg"="image/jpeg"
    ".ico"="image/x-icon"; ".svg"="image/svg+xml"; ".txt"="text/plain"
    ".pdf"="application/pdf"
    ".woff"="font/woff"; ".woff2"="font/woff2"
    ".eot"="application/vnd.ms-fontobject"; ".ttf"="font/ttf"
}
while ($listener.IsListening) {
    $ctx = $listener.GetContext()
    $rel = [Uri]::UnescapeDataString($ctx.Request.Url.AbsolutePath.TrimStart("/"))
    if ([string]::IsNullOrWhiteSpace($rel)) { $rel = "index.html" }
    $rel = $rel -replace "/", [IO.Path]::DirectorySeparatorChar
    $path = [IO.Path]::GetFullPath((Join-Path $root $rel))
    $rootFull = [IO.Path]::GetFullPath($root)
    if (-not $path.StartsWith($rootFull)) {
        $ctx.Response.StatusCode = 403
        $ctx.Response.Close()
        continue
    }
    if (Test-Path $path -PathType Container) { $path = Join-Path $path "index.html" }
    if (-not (Test-Path $path -PathType Leaf)) {
        $ctx.Response.StatusCode = 404
        $bytes = [Text.Encoding]::UTF8.GetBytes("Not found")
        $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
        $ctx.Response.Close()
        continue
    }
    $ext = [IO.Path]::GetExtension($path).ToLowerInvariant()
    $ctx.Response.ContentType = $(if ($mime.ContainsKey($ext)) { $mime[$ext] } else { "application/octet-stream" })
    $bytes = [IO.File]::ReadAllBytes($path)
    $ctx.Response.ContentLength64 = $bytes.Length
    $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
    $ctx.Response.Close()
}
