@echo off
cd /d "%~dp0"
if exist build rmdir /s /q build
mkdir build
javac -encoding UTF-8 -d build src\LembretesTrabalho.java || (pause & exit /b 1)
echo Main-Class: LembretesTrabalho> manifest.txt
jar cfm LembretesTrabalho.jar manifest.txt -C build .
del manifest.txt
rmdir /s /q build
echo Pronto! LembretesTrabalho.jar gerado.
pause
