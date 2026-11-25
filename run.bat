@echo off
setlocal
chcp 65001 >nul

rem Запуск из каталога расположения run.bat (F5, P3).
cd /d "%~dp0"

rem Выбор java: KIVPN_JAVA > JAVA_HOME > PATH.
if defined KIVPN_JAVA set "JAVA=%KIVPN_JAVA%"
if not defined KIVPN_JAVA if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA set "JAVA=java"

rem Проверка наличия собранного jar (R4).
if not exist "target\kivpn.jar" goto :nojar

rem Проверка доступности java (R5).
"%JAVA%" -version >nul 2>nul
if errorlevel 1 goto :nojava

"%JAVA%" -jar "target\kivpn.jar"
set "RC=%errorlevel%"
endlocal & exit /b %RC%

:nojar
echo [ОШИБКА] Не найден target\kivpn.jar. Соберите проект: mvn clean package 1>&2
exit /b 1

:nojava
echo [ОШИБКА] Java не найдена: "%JAVA%". 1>&2
echo Установите JDK 21 и задайте JAVA_HOME либо переменную KIVPN_JAVA с путём к java.exe. 1>&2
exit /b 1
