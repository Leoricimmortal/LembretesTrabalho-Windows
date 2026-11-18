@echo off
powershell -NoProfile -Command "$s=(New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Startup')+'\Lembretes de Trabalho.lnk'); $s.TargetPath='%~dp0LembretesTrabalho.exe'; $s.Arguments='--minimizado'; $s.WorkingDirectory='%~dp0'; $s.Save()"
echo Pronto: o Lembretes de Trabalho vai abrir minimizado junto com o Windows.
pause
