; =====================================================================
; iskolarPath — NSIS installer
;
;   makensis.exe /DVERSION=1.0.0 /DBUILDROOT="C:\path\Iskolarsql" installer.nsi
;
; Wraps the jpackage app-image (target/packaging/app-image/iskolarPath)
; into a single per-user setup EXE. No admin rights needed; installs to
; %LOCALAPPDATA%\Programs\iskolarPath by default.
; =====================================================================
Unicode true
!include "MUI2.nsh"

!ifndef VERSION
  !define VERSION "1.0.0"
!endif
!ifndef BUILDROOT
  !define BUILDROOT ".."
!endif

Name "iskolarPath"
OutFile "${BUILDROOT}\target\packaging\iskolarPath-Setup-${VERSION}.exe"

InstallDir "$LOCALAPPDATA\Programs\iskolarPath"
InstallDirRegKey HKCU "Software\iskolarPath" "InstallDir"
RequestExecutionLevel user

!define MUI_ICON "${BUILDROOT}\packaging\iskolarPath.ico"
!define MUI_UNICON "${BUILDROOT}\packaging\iskolarPath.ico"
!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\iskolarPath.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Launch iskolarPath"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

Section "iskolarPath" SecMain
  SetOutPath "$INSTDIR"
  File /r "${BUILDROOT}\target\packaging\app-image\iskolarPath\*"

  CreateDirectory "$SMPROGRAMS\iskolarPath"
  CreateShortcut "$SMPROGRAMS\iskolarPath\iskolarPath.lnk" "$INSTDIR\iskolarPath.exe"
  CreateShortcut "$SMPROGRAMS\iskolarPath\Uninstall iskolarPath.lnk" "$INSTDIR\Uninstall.exe"
  CreateShortcut "$DESKTOP\iskolarPath.lnk" "$INSTDIR\iskolarPath.exe"

  WriteUninstaller "$INSTDIR\Uninstall.exe"

  WriteRegStr HKCU "Software\iskolarPath" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\iskolarPath" \
      "DisplayName" "iskolarPath"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\iskolarPath" \
      "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\iskolarPath" \
      "Publisher" "iskolarPath"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\iskolarPath" \
      "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\iskolarPath" \
      "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\iskolarPath" \
      "DisplayIcon" "$INSTDIR\iskolarPath.exe"
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\iskolarPath" \
      "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\iskolarPath" \
      "NoRepair" 1
SectionEnd

Section "Uninstall"
  Delete "$DESKTOP\iskolarPath.lnk"
  RMDir /r "$SMPROGRAMS\iskolarPath"
  RMDir /r "$INSTDIR"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\iskolarPath"
  DeleteRegKey HKCU "Software\iskolarPath"
SectionEnd
