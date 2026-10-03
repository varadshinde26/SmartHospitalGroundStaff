@echo off
setlocal
set "ROOT=%~dp0"
cd /d "%ROOT%"

echo ================================================
echo Smart Hospital Ground Staff - Build
echo ================================================

if "%JAVA_HOME%"=="" (
  echo ERROR: JAVA_HOME is not set.
  echo Example: set JAVA_HOME=C:\Program Files\Java\jdk-21.0.12
  exit /b 1
)

if not exist "%JAVA_HOME%\bin\javac.exe" (
  echo ERROR: javac was not found under JAVA_HOME.
  exit /b 1
)

if not exist "lib\servlet-api-4.0.1.jar" (
  echo ERROR: lib\servlet-api-4.0.1.jar is missing.
  exit /b 1
)

if not exist "lib\mysql-connector-j-9.4.0.jar" (
  echo MySQL Connector/J 9.4.0 is not bundled because it is an external binary dependency.
  echo Downloading it from Maven Central...
  where curl >nul 2>nul
  if not errorlevel 1 (
    curl -L --fail --silent --show-error -o "lib\mysql-connector-j-9.4.0.jar" "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar"
  ) else (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar' -OutFile 'lib\mysql-connector-j-9.4.0.jar'"
  )
  if not exist "lib\mysql-connector-j-9.4.0.jar" (
    echo ERROR: MySQL Connector/J could not be downloaded.
    echo Put mysql-connector-j-9.4.0.jar manually into lib\ and run build.bat again.
    exit /b 1
  )
)

if exist build rmdir /s /q build
mkdir build\classes
mkdir build\war\WEB-INF\classes
mkdir build\war\WEB-INF\lib

"%JAVA_HOME%\bin\javac.exe" --release 17 -encoding UTF-8 -cp "lib\servlet-api-4.0.1.jar;lib\mysql-connector-j-9.4.0.jar" -d build\classes src\main\java\com\smarthospital\util\*.java src\main\java\com\smarthospital\model\*.java src\main\java\com\smarthospital\service\*.java src\main\java\com\smarthospital\controller\*.java
if errorlevel 1 (
  echo ERROR: Java compilation failed.
  exit /b 1
)

xcopy /e /i /y src\main\webapp build\war >nul
xcopy /e /i /y build\classes build\war\WEB-INF\classes >nul
copy /y lib\mysql-connector-j-9.4.0.jar build\war\WEB-INF\lib\ >nul
if exist build\war\WEB-INF\lib\servlet-api-4.0.1.jar del /q build\war\WEB-INF\lib\servlet-api-4.0.1.jar

cd build\war
"%JAVA_HOME%\bin\jar.exe" -cf ..\smart-hospital-ground-staff.war *
cd ..\..
if errorlevel 1 (
  echo ERROR: WAR packaging failed.
  exit /b 1
)

echo.
echo BUILD SUCCESSFUL
echo WAR: %ROOT%build\smart-hospital-ground-staff.war
echo.
echo Deploy this WAR to your Tomcat 9 webapps folder.
endlocal
