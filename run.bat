@echo off
chcp 65001 > nul
echo ===== 개발용 실행 (컴파일 + 즉시 실행) =====

:: -------------------------------------------------------
:: JDK 가 PATH 에 없는 경우 아래 줄의 주석을 해제하고
:: 실제 JDK 설치 경로로 변경하세요.
:: 예) set JAVA_HOME=C:\Program Files\Java\jdk-21
:: -------------------------------------------------------
:: set JAVA_HOME=C:\Program Files\Java\jdk-21
:: set PATH=%JAVA_HOME%\bin;%PATH%

if not exist out mkdir out

javac -encoding UTF-8 -sourcepath src -d out src\Main.java
if errorlevel 1 ( echo [오류] 컴파일 실패. JDK 가 설치되어 있고 PATH 에 등록되어 있는지 확인하세요. & pause & exit /b 1 )

java -cp out Main
pause
