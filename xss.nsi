
!define VERSION "0.9-NOTYETRELEASED"
Name "Bricole XML Session Server ${VERSION}"
OutFile bricole-xss-win32-installer.exe
InstallDir C:\XSS
InstallDirRegKey HKLM SOFTWARE\Bricole\XSS "Install_Dir"
DirText "Please choose a destination directory for Bricole XSS."

ComponentText "You are about to install Bricole XSS. Choose what parts you want installed."

Section "XSS core (required)"
  SectionIn RO

  SetOutPath $INSTDIR
  File /R dist\*.*

  WriteRegStr HKLM SOFTWARE\Bricole\XSS "Install_Dir" "$INSTDIR"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BricoleXSS" "DisplayName" "Bricole XSS (remove only)"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\BricoleXSS" "UninstallString" '"$INSTDIR\uninstall.exe"'
  WriteUninstaller "uninstall.exe"

;; update the install dir in the start script
ClearErrors
FileOpen $0 "$INSTDIR\bin\xss.bat" "r"
GetTempFileName $R0
FileOpen $1 $R0 "w"
loop:
   FileRead $0 $2
   IfErrors done
   StrCmp $2 "set BASEPATH=C:\XSS$\r$\n" 0 +3
      FileWrite $1 "set BASEPATH=$INSTDIR$\r$\n"
      Goto loop
   StrCmp $2 "set BASEPATH=C:\XSS" 0 +3
      FileWrite $1 "set BASEPATH=$INSTDIR"
      Goto loop
   FileWrite $1 $2
   Goto loop

done:
   FileClose $0
   FileClose $1
   Delete "$INSTDIR\bin\xss.bat"
   CopyFiles /SILENT $R0 "$INSTDIR\bin\xss.bat"
   Delete $R0

SectionEnd

Section "Start menu shortcuts"
  CreateDirectory "$SMPROGRAMS\Bricole XSS"
  CreateShortCut "$SMPROGRAMS\Bricole XSS\Start XSS.lnk" "$INSTDIR\bin\xss.bat" "" "$INSTDIR\bin\xss.bat" 0
  CreateShortCut "$SMPROGRAMS\Bricole XSS\Uninstall XSS.lnk" "$INSTDIR\uninstall.exe" "" "$INSTDIR\uninstall.exe" 0
  CreateShortCut "$SMPROGRAMS\Bricole XSS\README file.lnk" "$INSTDIR\README.txt" "" "$INSTDIR\README.txt" 0
  CreateShortCut "$SMPROGRAMS\Bricole XSS\INSTALL file.lnk" "$INSTDIR\INSTALL.txt" "" "$INSTDIR\INSTALL.txt" 0
  CreateShortCut "$SMPROGRAMS\Bricole XSS\Browse the XSS folder.lnk" "$INSTDIR" "" "$INSTDIR" 0
  CreateShortCut "$SMPROGRAMS\Bricole XSS\Browse the XSS javadoc.lnk" "$INSTDIR\docs\index.html" "" "$INSTDIR\docs\index.html" 0
SectionEnd

; Uninstaller

Uninstalltext "This Will Uninstall Bricole XSS. Hit Next To Continue."

; Uninstall Section

Section "Uninstall"
  
  ; Remove Registry Keys
  Deleteregkey Hklm "Software\Microsoft\Windows\Currentversion\Uninstall\Bricole XSS"
  Deleteregkey Hklm "Software\Bricole\XSS"

  ; Remove Files And Uninstaller
  RMDir /r $INSTDIR\*.*
  ;Delete $INSTDIR\Uninstall.Exe

  ; Remove Shortcuts, If Any
  Delete "$SMPROGRAMS\Bricole XSS\*.*"

  ; Remove Directories Used
  Rmdir "$SMPROGRAMS\Bricole XSS"
  Rmdir "$INSTDIR"

Sectionend

