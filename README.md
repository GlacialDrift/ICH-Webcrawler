Build: Source → EXE (Windows)

This project ships as a single EXE using the JDK’s jpackage. Steps below assume Windows, JDK 17+ (works with JDK 25), and Maven.

0) Project prerequisites

pages.json location: put it at src/main/resources/pages.json.
The app loads it from the classpath; do not reference src/... in code.

Output directories: the app writes to a user folder (recommended):

%USERPROFILE%\ICH-Webcrawler\
    snapshots\YYYY-MM-DD.json
    diffs\YYYY-MM-DD.md


(If you use a custom data dir flag, adjust accordingly.)

1) Ensure the POM builds a shaded (fat) JAR

pom.xml must include the Shade plugin and your main class:

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


Tip: target/ICH-Webcrawler-0.1.0.jar is your shaded JAR (Maven keeps a backup as original-...jar).

2) Install JDK and set JAVA_HOME

Install a JDK with jpackage (Temurin/Oracle JDK 17+ or 25).

Set:

JAVA_HOME → full JDK folder (e.g. C:\Program Files\Eclipse Adoptium\jdk-25.0.0.36-hotspot)

add %JAVA_HOME%\bin to PATH

New PowerShell:

java -version
jpackage --version

3) Build the shaded JAR

From project root:

mvn clean package


You should see:

target\ICH-Webcrawler-0.1.0.jar


Verify pages.json is inside the JAR:

jar tf target\ICH-Webcrawler-0.1.0.jar | Select-String pages.json


It should print pages.json.

4) Create a portable EXE (app-image)

This produces a folder with ICH-Webcrawler.exe that you can zip/share.

& "$env:JAVA_HOME\bin\jpackage.exe" `
  --name "ICH-Webcrawler" `
  --input "target" `
  --main-jar "ICH-Webcrawler-0.1.0.jar" `
  --main-class com.Harris.ich.Main `
  --type app-image `
  --win-console


Output:

.\ICH-Webcrawler\ICH-Webcrawler.exe


--win-console keeps a console window (useful for logs). Remove it for a silent app.

5) (Optional) Create an MSI installer

jpackage needs WiX Toolset on PATH to build MSI.

Install WiX v3.x: https://wixtoolset.org/releases/

Add its bin folder to PATH (e.g. C:\Program Files (x86)\WiX Toolset v3.11\bin).

where candle
where light


Build MSI:

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


Output:

ICH-Webcrawler-1.0.0.msi

6) Run & verify

Portable app: double-click ICH-Webcrawler.exe

Installer: run the .msi, then launch from Start Menu.

On first run you should see:

%USERPROFILE%\ICH-Webcrawler\snapshots\<YYYY-MM-DD>.json
%USERPROFILE%\ICH-Webcrawler\diffs\<YYYY-MM-DD>.md


If changes were detected, the app opens the diff; otherwise it prints “No changes.”

Troubleshooting

App can’t find pages.json:

Ensure it exists at src/main/resources/pages.json.

Ensure your code loads via classpath:

Main.class.getResourceAsStream("/pages.json");


Rebuild mvn clean package.

Confirm it’s inside the JAR with jar tf ... | Select-String pages.json.

EXE closes too fast:

Build with --win-console to see logs.

Add a --pause flag in your app (readline at end) or create a .bat:

@echo off
"C:\Program Files\ICH-Webcrawler\ICH-Webcrawler.exe" %*
echo.
pause


Diff doesn’t open:

Use a shell-based opener on Windows:

new ProcessBuilder("cmd", "/c", "start", "", "\"" + path + "\"").start();


Log the full diff path; open it manually if there’s no .md association.

Installer build fails with WiX errors:

Use --type app-image (no WiX required), or

Install WiX and ensure candle.exe and light.exe are on PATH, then use --type msi.

Non-200 API responses:

Continue to next page (don’t abort the run).

Log API URL and status for the failing page.

One-liner build script (optional)

Create build.ps1:

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
