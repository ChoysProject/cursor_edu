@echo off
chcp 65001 > nul
echo ============================================
echo   인터페이스 검증 시스템  빌드 스크립트
echo ============================================
echo.

set SRC_DIR=src
set OUT_DIR=out
set DIST_DIR=dist
set JAR_NAME=InterfaceValidator.jar
set APP_NAME=인터페이스검증시스템

if not exist %OUT_DIR%  mkdir %OUT_DIR%
if not exist %DIST_DIR% mkdir %DIST_DIR%

echo [1/3] Java 소스 컴파일 중...
javac -encoding UTF-8 -sourcepath %SRC_DIR% -d %OUT_DIR% %SRC_DIR%\Main.java
if errorlevel 1 (
    echo [오류] 컴파일 실패. 소스를 확인하세요.
    pause & exit /b 1
)
echo       컴파일 완료.

echo.
echo [2/3] 실행 가능한 JAR 생성 중...
echo Main-Class: Main > MANIFEST.MF
jar cfm %DIST_DIR%\%JAR_NAME% MANIFEST.MF -C %OUT_DIR% .
del MANIFEST.MF
echo       JAR 생성 완료: %DIST_DIR%\%JAR_NAME%

echo.
echo [3/3] jpackage 로 독립 실행형 .exe 생성 중...
echo       (JDK 14 이상 필요. 실패 시 JAR 파일로 실행하세요.)

jpackage ^
    --input      %DIST_DIR% ^
    --main-jar   %JAR_NAME% ^
    --main-class Main ^
    --name       "%APP_NAME%" ^
    --type       app-image ^
    --dest       %DIST_DIR%\app ^
    --app-content sample

if errorlevel 1 (
    echo.
    echo [참고] jpackage 가 실패했거나 없는 경우:
    echo   java -jar %DIST_DIR%\%JAR_NAME%  으로 실행하세요.
) else (
    echo.
    echo -----------------------------------------------
    echo  빌드 완료!
    echo  실행 폴더: %DIST_DIR%\app\%APP_NAME%\
    echo  실행 파일: %DIST_DIR%\app\%APP_NAME%\%APP_NAME%.exe
    echo -----------------------------------------------
)
pause
