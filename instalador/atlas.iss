#define Version "1.0.0"

[Setup]
AppId={{7B1E6C1A-9A0F-4C5E-9D3B-2F6A8E41C7D2}
AppName=Atlas
AppVersion={#Version}
AppPublisher=Proyecto Atlas
DefaultDirName={localappdata}\Programs\Atlas
DisableDirPage=no
DisableProgramGroupPage=yes
DisableWelcomePage=no
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\target\instalador\salida
OutputBaseFilename=Atlas-Instalador-{#Version}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayIcon={app}\Atlas.exe

[Languages]
Name: "es"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "escritorio"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "..\target\instalador\imagen\Atlas\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\scripts\instalar-todo.ps1"; DestDir: "{app}\scripts"; Flags: ignoreversion
Source: "..\scripts\instalar-dependencias.ps1"; DestDir: "{app}\scripts"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\Atlas"; Filename: "{app}\Atlas.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\Atlas"; Filename: "{app}\Atlas.exe"; WorkingDir: "{app}"; Tasks: escritorio

[Run]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\scripts\instalar-todo.ps1"" -SinCompilar"; WorkingDir: "{app}"; Description: "Descargar los componentes necesarios (requiere internet)"; Flags: postinstall waituntilterminated skipifsilent
Filename: "{app}\Atlas.exe"; WorkingDir: "{app}"; Description: "Abrir Atlas"; Flags: postinstall nowait skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{app}\herramientas"
Type: filesandordirs; Name: "{app}\config"
