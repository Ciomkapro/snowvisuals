$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$WarningPreference = 'SilentlyContinue'

function Wait-WinRt($Operation, [Type] $ResultType, [int] $TimeoutMs = 3500) {
    $task = $asTask.MakeGenericMethod($ResultType).Invoke($null, @($Operation))
    if (!$task.Wait($TimeoutMs)) { throw 'Media operation timed out' }
    return $task.Result
}

function Test-SpotifyId([string] $AppId) {
    return $AppId -match '^(?:Spotify|(?:.*[\\/])?Spotify\.exe|SpotifyAB\.SpotifyMusic_[A-Za-z0-9]+!Spotify)$'
}

function Find-SpotifySession {
    $current = $manager.GetCurrentSession()
    if ($null -ne $current -and (Test-SpotifyId $current.SourceAppUserModelId)) {
        return $current
    }

    $fallback = $null
    foreach ($session in $manager.GetSessions()) {
        if (!(Test-SpotifyId $session.SourceAppUserModelId)) { continue }
        if ($session.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing') { return $session }
        if ($null -eq $fallback) { $fallback = $session }
    }
    return $fallback
}

function Encode-Field([string] $Value) {
    $Value = $Value.Trim()
    if ($Value.Length -gt 240) { $Value = $Value.Substring(0, 240) }
    return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Value))
}

function Get-CoverArt($Thumbnail) {
    if ($null -eq $Thumbnail) { return '' }
    $stream = $null
    $reader = $null
    try {
        $stream = Wait-WinRt ($Thumbnail.OpenReadAsync()) $streamType 1200
        # OpenReadAsync projects this interface as __ComObject in Windows PowerShell.
        $size = $randomStreamType.GetProperty('Size').GetValue($stream, $null)
        if ($size -le 0 -or $size -gt 512KB) { return '' }
        $inputStream = $randomStreamType.GetMethod('GetInputStreamAt').Invoke($stream, @([uint64] 0))
        $reader = $readerType::new($inputStream)
        $loaded = Wait-WinRt ($reader.LoadAsync([uint32] $size)) ([uint32]) 1200
        if ($loaded -ne $size) { return '' }
        $bytes = [byte[]]::new([int] $size)
        $reader.ReadBytes($bytes)
        return [Convert]::ToBase64String($bytes)
    } catch {
        return ''
    } finally {
        if ($null -ne $reader) { try { $reader.Dispose() } catch {} }
        if ($null -ne $stream) { try { [void] [IDisposable].GetMethod('Dispose').Invoke($stream, $null) } catch {} }
    }
}

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows, ContentType=WindowsRuntime]
    $mediaType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows, ContentType=WindowsRuntime]
    $streamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType=WindowsRuntime]
    $readerType = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType=WindowsRuntime]
    $randomStreamType = [Windows.Storage.Streams.IRandomAccessStream, Windows.Storage.Streams, ContentType=WindowsRuntime]
    $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and $_.GetGenericArguments().Count -eq 1 -and
            $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    } | Select-Object -First 1
    if ($null -eq $asTask) { throw 'Windows Runtime task adapter unavailable' }
    $manager = Wait-WinRt ($managerType::RequestAsync()) $managerType

    $coverIdentity = ''
    $coverSent = $false
    $nextCoverAttempt = 0L
    $clock = [Diagnostics.Stopwatch]::StartNew()

    while ($null -ne ($command = [Console]::ReadLine())) {
        $session = Find-SpotifySession
        if ($null -eq $session) {
            $coverIdentity = ''
            [Console]::WriteLine('NONE')
            continue
        }

        switch ($command) {
            'TOGGLE' { [void](Wait-WinRt ($session.TryTogglePlayPauseAsync()) ([bool])) }
            'PREVIOUS' { [void](Wait-WinRt ($session.TrySkipPreviousAsync()) ([bool])) }
            'NEXT' { [void](Wait-WinRt ($session.TrySkipNextAsync()) ([bool])) }
        }

        $media = Wait-WinRt ($session.TryGetMediaPropertiesAsync()) $mediaType
        if ($null -eq $media -or [string]::IsNullOrWhiteSpace($media.Title)) {
            $coverIdentity = ''
            [Console]::WriteLine('NONE')
            continue
        }
        $title = Encode-Field $media.Title
        $artist = Encode-Field $media.Artist
        $album = Encode-Field $media.AlbumTitle
        $identity = [string]::Join('.', [string[]] @($title, $artist, $album))
        if ($identity -ne $coverIdentity) {
            $coverIdentity = $identity
            $coverSent = $false
            $nextCoverAttempt = 0L
        }

        $cover = ''
        if ($command -eq 'POLL' -and !$coverSent -and $clock.ElapsedMilliseconds -ge $nextCoverAttempt) {
            $nextCoverAttempt = $clock.ElapsedMilliseconds + 10000
            $cover = Get-CoverArt $media.Thumbnail
            $coverSent = $cover.Length -gt 0
        }

        $duration = 0L
        $position = 0L
        $lastUpdated = 0L
        try {
            $timeline = $session.GetTimelineProperties()
            if ($null -ne $timeline) {
                $startTime = $timeline.StartTime.TotalMilliseconds
                $duration = [long][Math]::Max(0, $timeline.EndTime.TotalMilliseconds - $startTime)
                $position = [long][Math]::Max(0, $timeline.Position.TotalMilliseconds - $startTime)
                if ($null -ne $timeline.LastUpdatedTime) {
                    $lastUpdated = ([DateTimeOffset] $timeline.LastUpdatedTime).ToUnixTimeMilliseconds()
                }
            }
        } catch {
            # Some sessions omit timing even though their metadata and playback controls work.
        }

        $status = if ($session.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing') { 'PLAYING' } else { 'PAUSED' }
        [Console]::WriteLine([string]::Join([char]9, [string[]]@(
            'TRACK', $status, $title, $artist, $album, $duration, $position, $lastUpdated, $cover)))
    }
} catch {
    [Console]::WriteLine('ERROR')
}