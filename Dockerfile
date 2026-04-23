# escape=`

FROM mcr.microsoft.com/windows/servercore:ltsc2019

SHELL ["C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe", "-NoLogo", "-NoProfile", "-Command", "$ErrorActionPreference = 'Stop'; $ProgressPreference = 'SilentlyContinue';"]

ENV JAVA_HOME="C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.18.8-hotspot"
ENV PATH="C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.18.8-hotspot\\bin;%PATH%"

RUN [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; `
    Write-Host '=== Downloading JDK from Tsinghua University Mirror ==='; `
    $maxRetries = 3; `
    $retryDelay = 10; `
    for ($i = 1; $i -le $maxRetries; $i++) { `
        Write-Host 'Attempt' $i 'of' $maxRetries '...'; `
        try { `
            Invoke-WebRequest -Uri 'https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/OpenJDK17U-jdk_x64_windows_hotspot_17.0.18_8.msi' -OutFile 'C:\\jdk.msi' -UseBasicParsing -TimeoutSec 1200; `
            $fileOk = $false; `
            if (Test-Path 'C:\\jdk.msi') { $fileOk = ((Get-Item 'C:\\jdk.msi').Length -gt 150MB); }; `
            if ($fileOk) { `
                Write-Host 'Download successful!'; `
                break; `
            } else { `
                Write-Host 'Download incomplete, retrying...'; `
                Remove-Item 'C:\\jdk.msi' -Force -ErrorAction SilentlyContinue; `
                Start-Sleep $retryDelay; `
            } `
        } catch { `
            Write-Host 'Download error:' $_.Exception.Message; `
            Remove-Item 'C:\\jdk.msi' -Force -ErrorAction SilentlyContinue; `
            if ($i -eq $maxRetries) { throw 'All download attempts failed!'; } `
            Start-Sleep $retryDelay; `
        } `
    }; `
    Write-Host 'Installing JDK...'; `
    Start-Process msiexec.exe -ArgumentList '/i', 'C:\\jdk.msi', '/qn', '/norestart' -NoNewWindow -Wait; `
    Remove-Item 'C:\\jdk.msi' -Force -ErrorAction SilentlyContinue; `
    Write-Host 'Verifying Java installation...'; `
    & 'C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.18.8-hotspot\\bin\\java.exe' --version

WORKDIR C:\\app

COPY environment/repo C:\\app

EXPOSE 80

CMD ["cmd", "/S", "/C", "mvnw.cmd clean compile quarkus:dev -Dquarkus.http.port=80"]
