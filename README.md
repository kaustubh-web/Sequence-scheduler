# Sequence Scheduler

A simple sequence scheduler built with plain Java and a lightweight browser frontend.

## Features

- Creates dependency-based task schedules
- Detects duplicate task ids and circular dependencies
- Serves a basic frontend from the same Java app
- Runs without Maven, Gradle, or external libraries

## Run

Use the helper script:

```powershell
.\run.ps1
```

The server keeps running in that terminal window. That is expected. Once you see:

```text
Sequence Scheduler is running on http://localhost:8080
```

open `http://localhost:8080` in your browser.

You can also run it manually:

```powershell
javac -d out src/com/sequencescheduler/Main.java
Copy-Item -Path src/web -Destination out -Recurse -Force
java -cp out com.sequencescheduler.Main
```

## Troubleshooting

- If the terminal looks stuck after launch, that means the server is running.
- If `http://localhost:8080` does not open, make sure another app is not already using port `8080`.
- If the frontend copy step fails, the app can still serve files directly from `src/web` while running from the project folder.
- Stop the app with `Ctrl+C`.

## Deploy on Render

This project can be deployed to Render as a `Web Service` using `Docker`.

1. Push this repository to GitHub.
2. In Render, create a new `Web Service`.
3. Select this GitHub repository.
4. Choose `Docker` as the runtime.
5. Leave the build and start commands empty so Render uses the `Dockerfile`.
6. Deploy the service.

The app reads the `PORT` environment variable automatically, so it works with Render's assigned port.
