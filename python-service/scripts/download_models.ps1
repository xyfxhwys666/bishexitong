$ErrorActionPreference = 'Stop'
$modelDirectory = Join-Path $env:LOCALAPPDATA 'FacePrivacy\models'
$modelPath = Join-Path $modelDirectory 'blaze_face_short_range.tflite'
$modelUrl = 'https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/1/blaze_face_short_range.tflite'

New-Item -ItemType Directory -Force -Path $modelDirectory | Out-Null
if (-not (Test-Path -LiteralPath $modelPath)) {
    Invoke-WebRequest -Uri $modelUrl -OutFile $modelPath
}
Write-Output "Face detector model: $modelPath"
