---
Author: Mike Harris
Version: 0.1.1
Github: https://github.com/GlacialDrift/ICH-Webcrawler/
---
# ICH Webcrawler

A lightweight crawler for the [ICH website](https://www.ich.org/), built in Java.  
It checks guideline pages for changes, saves daily snapshots, and generates diffs to highlight new/removed/updated guidelines.

## Installation (for users)

1. Download the portable app folder (`ICH-Webcrawler-App.zip`) from releases.
2. Unzip it and save to a known location (Documents, C:\, wherever)
3. Open the folder and double-click `ICH-Webcrawler.exe`.
4. The .exe file must remain adjacent to the /app/ and /runtime/ subfolders

## Usage

- **Run manually:** Double-click the EXE. A console window will show progress.
- **What it does:**
  - Fetches the latest JSON data from ICH guideline pages.
  - Writes a snapshot of all guideline code/title pairs.
  - Compares today’s snapshot with the most recent earlier snapshot.
  - Automatically opens Today's DIFF file showing changes from the most recent earlier snapshot

## Data storage

Snapshots and diffs are stored under your user profile:

```
%USERPROFILE%\ICH-Webcrawler  
snapshots\YYYY-MM-DD.json  
diffs\YYYY-MM-DD.md
```

- **Snapshots**: full list of guidelines at a point in time.
- **Diffs**: added, removed, or changed guidelines since last run.

## Example diff

```
ICH Weekly Diff – 2025-10-02

ADDED:   [Q1G] New Guideline Title
REMOVED: [Q1C] Stability Testing for New Dosage Forms
TITLE_CHANGED: [Q1 EWG]
    "Old Title"
 -> "New Revised Title"
 ```

## Scheduling (optional)

- **Automated runs:** Use Windows Task Scheduler to run weekly
- **Manual runs:** Just double-click the EXE whenever you want to check for updates.
## Requirements

- Windows 10/11
- No separate Java install needed — the EXE bundles its own runtime.


# Build From Source to .exe (Dev)

This section explains how to build the shaded jar and bundle it as a Windows EXE.
### 0) Prerequisites

- JDK 17+ (JDK 25 also works). Must include `jpackage`.
- Maven installed, or use IDE’s Maven support.
- WiX Toolset (only if you want to build an MSI installer).

### 1) Build a shaded JAR

Ensure `pom.xml` contains the **Maven Shade plugin**:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-shade-plugin</artifactId>
      <version>3.5.0</version>
      <executions>
        <execution>
          <phase>package</phase>
          <goals><goal>shade</goal></goals>
          <configuration>
            <createDependencyReducedPom>false</createDependencyReducedPom>
            <transformers>
              <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                <mainClass>com.Harris.ich.Main</mainClass>
              </transformer>
            </transformers>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>

```

Build with Maven:

```powershell
mvn clean package
```

Result:  
`target/ICH-Webcrawler-0.1.0.jar` ← this is the fat (shaded) jar.

Verify `pages.json` is inside:

```powershell
jar tf target\ICH-Webcrawler-0.1.0.jar | Select-String pages.json
```

### 2) Package as portable EXE (no installer)

```powershell
& "$env:JAVA_HOME\bin\jpackage.exe" `
  --name "ICH-Webcrawler" `
  --input "target" `
  --main-jar "ICH-Webcrawler-0.1.0.jar" `
  --main-class com.Harris.ich.Main `
  --type app-image `
  --win-console

```

Result:  
`.\ICH-Webcrawler\ICH-Webcrawler.exe` (zip/share this folder).

### 3) Package as MSI installer (optional)

Requires [WiX Toolset](https://github.com/wixtoolset/wix/releases/). Add its `bin` folder to PATH.

```powershell
& "$env:JAVA_HOME\bin\jpackage.exe" `
  --name "ICH-Webcrawler" `
  --app-version "1.0.0" `
  --input "target" `
  --main-jar "ICH-Webcrawler-0.1.0.jar" `
  --main-class com.Harris.ich.Main `
  --type msi `
  --vendor "Your Org" `
  --win-menu `
  --win-shortcut `
  --win-dir-chooser `
  --win-console

```

Result:  
`ICH-Webcrawler-1.0.0.msi` ← double-click to install.


### Troubleshooting

- **`pages.json` not found:** ensure it’s in `src/main/resources/` and loaded with `Main.class.getResourceAsStream("/pages.json")`.
- **EXE closes too fast:** use `--pause` flag or build a `.bat` wrapper with `pause`.
- **Diff doesn’t open:** no default app for `.md` installed. Open manually (e.g. in VS Code).
- **WiX errors:** skip MSI, use `--type app-image`.


## One-liner build script (optional)

Create `build.ps1`:

```powershell
# Build shaded jar
mvn clean package || exit $LASTEXITCODE

# Portable EXE
& "$env:JAVA_HOME\bin\jpackage.exe" `
  --name "ICH-Webcrawler" `
  --input "target" `
  --main-jar "ICH-Webcrawler-0.1.0.jar" `
  --main-class com.Harris.ich.Main `
  --type app-image `
  --win-console

Write-Host "App image at: .\ICH-Webcrawler\ICH-Webcrawler.exe"

# MSI (only if WiX is available)
if (Get-Command candle -ErrorAction SilentlyContinue) {
  & "$env:JAVA_HOME\bin\jpackage.exe" `
    --name "ICH-Webcrawler" `
    --app-version "1.0.0" `
    --input "target" `
    --main-jar "ICH-Webcrawler-0.1.0.jar" `
    --main-class com.Harris.ich.Main `
    --type msi `
    --vendor "Your Org" `
    --win-menu `
    --win-shortcut `
    --win-dir-chooser `
    --win-console
  Write-Host "MSI at: ICH-Webcrawler-1.0.0.msi"
} else {
  Write-Host "WiX not found; skipped MSI."
}

```

