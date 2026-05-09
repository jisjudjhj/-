Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$currentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($currentIdentity)
$isAdmin = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    throw "Please run this script from an elevated PowerShell window."
}

$installer = "F:\Docker\Docker-Desktop-Installer.exe"
if (-not (Test-Path $installer)) {
    throw "Docker Desktop installer not found: $installer"
}

Write-Host "[docker-install] Enabling WSL optional features..."
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart

Write-Host "[docker-install] Launching Docker Desktop installer..."
Start-Process -FilePath $installer -Wait -ArgumentList "install", "--accept-license", "--backend=wsl-2"

Write-Host ""
Write-Host "[docker-install] If WSL was enabled for the first time, please reboot Windows once before starting Docker Desktop."
