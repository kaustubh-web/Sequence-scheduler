$ErrorActionPreference = "Stop"

if (Test-Path "out") {
    Remove-Item -Recurse -Force "out"
}

New-Item -ItemType Directory -Path "out" | Out-Null
javac -d out src/com/sequencescheduler/Main.java
Copy-Item -Path src/web -Destination out -Recurse -Force
Write-Host "Build complete."
Write-Host "Starting Sequence Scheduler on http://localhost:8080"
Write-Host "Keep this window open while using the app. Press Ctrl+C to stop it."
java -cp out com.sequencescheduler.Main
