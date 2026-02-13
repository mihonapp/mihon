@echo off
setlocal enabledelayedexpansion

REM Configurar variables
set GITEA_URL=http://192.168.1.119:10009
set REPO_OWNER=MatiasDesu
set REPO_NAME=Mihon-fork
set GITEA_TOKEN=47dc8216649cd1d8f9b40174f3156bf75cdb7600
REM Reemplaza con tu token de Gitea
set APK_PATH=app\build\outputs\apk\release\app-release.apk
set RELEASE_TAG=v1.0.0-%random%
set RELEASE_NAME=Release %RELEASE_TAG%
set RELEASE_BODY=Automatic release from build script

REM Select build options
echo Select the options (comma-separated):
echo 1. Clean Releases
echo 2. Android APK
set /p OPTION_CHOICES="Choose options (1-2, e.g., 1,2): "

REM Replace commas with spaces for parsing
set OPTION_CHOICES=%OPTION_CHOICES:,= %

REM Validate choices
set ALL_VALID=1
for %%i in (%OPTION_CHOICES%) do (
    set IS_VALID=0
    if %%i==1 set IS_VALID=1
    if %%i==2 set IS_VALID=1
    if !IS_VALID!==0 (
        echo Invalid option: %%i
        set ALL_VALID=0
    )
)

if !ALL_VALID!==0 goto end

REM Process each choice in order
for %%i in (%OPTION_CHOICES%) do (
    if %%i==1 call :clean_releases
    if %%i==2 call :android_build
)
goto end

:android_build
REM Ejecutar el build
echo Building APK...
call gradlew assembleRelease

if %errorlevel% neq 0 (
    echo Build failed!
    exit /b 1
)

echo Build successful. APK created at %APK_PATH%

REM Crear el release en Gitea
echo Creating release on Gitea...
curl --fail -X POST %GITEA_URL%/api/v1/repos/%REPO_OWNER%/%REPO_NAME%/releases?token=%GITEA_TOKEN% -H "Content-Type: application/json" -d "{\"tag_name\":\"%RELEASE_TAG%\",\"name\":\"%RELEASE_NAME%\",\"body\":\"%RELEASE_BODY%\"}" -o release_response.json
if %errorlevel% neq 0 (
    echo Failed to create release! Response:
    type release_response.json
    exit /b 1
)

if %errorlevel% neq 0 (
    echo Failed to create release!
    exit /b 1
)

REM Obtener la URL de subida
for /f "delims=" %%i in ('powershell.exe -Command "$release = Get-Content release_response.json | ConvertFrom-Json; $uploadUrl = $release.upload_url -replace '{.*}', ''; Write-Host $uploadUrl"') do set UPLOAD_URL=%%i

if "%UPLOAD_URL%"=="" (
    echo Failed to get upload URL! Response:
    type release_response.json
    exit /b 1
)

echo Release created. Upload URL: %UPLOAD_URL%

REM Subir el APK
echo Uploading APK...
curl -X POST "%UPLOAD_URL%?name=app-release.apk" -H "Authorization: token %GITEA_TOKEN%" -F "attachment=@%APK_PATH%"

if %errorlevel% neq 0 (
    echo Failed to upload APK!
    exit /b 1
)

echo APK uploaded successfully.
goto :eof

:clean_releases
REM Clean all releases on Gitea
echo Cleaning all releases on Gitea...
curl -X GET "%GITEA_URL%/api/v1/repos/%REPO_OWNER%/%REPO_NAME%/releases?token=%GITEA_TOKEN%" -o releases.json

if %errorlevel% neq 0 (
    echo Failed to fetch releases!
    exit /b 1
)

for /f "delims=" %%j in ('powershell.exe -Command "$releases = Get-Content releases.json | ConvertFrom-Json; foreach ($r in $releases) { Write-Host $r.id }"') do call :delete_release %%j

echo All releases cleaned.
goto :eof

:delete_release
set RELEASE_ID=%1
curl --fail -X DELETE "%GITEA_URL%/api/v1/repos/%REPO_OWNER%/%REPO_NAME%/releases/%RELEASE_ID%?token=%GITEA_TOKEN%"

if %errorlevel% neq 0 (
    echo Failed to delete release %RELEASE_ID%!
) else (
    echo Deleted release %RELEASE_ID%
)
goto :eof

:end
echo Script completed successfully!