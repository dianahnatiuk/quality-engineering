# ЛР8, Завд.1 (Windows PowerShell): те саме, що sonar-ck.sh
#   $env:SONAR_TOKEN="..."; $env:PROJECT_KEY="labs3"; .\scripts\lab8\sonar-ck.ps1
param([string[]]$Files = @(
  "src/main/java/lab8/legacy/OrderProcessor.java",   # Core Domain
  "src/main/java/lab8/infra/PaymentGateway.java",    # Infrastructure
  "src/main/java/lab8/model/Order.java"))            # Utility / DTO

$SonarUrl = if ($env:SONAR_URL) { $env:SONAR_URL } else { "http://localhost:9000" }
$Out = "docs/lab8/sonar"; New-Item -ItemType Directory -Force $Out | Out-Null
$auth = @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("$($env:SONAR_TOKEN):")) }

$wanted = "class_complexity","complexity","cognitive_complexity","coupling_between_objects","lcom4","cohesion_in_methods","depth_in_tree","ncloc","functions","classes"
$avail = (Invoke-RestMethod -Headers $auth -Uri "$SonarUrl/api/metrics/search?ps=500").metrics.key
$keys = $wanted | Where-Object { $avail -contains $_ }
$missing = $wanted | Where-Object { $avail -notcontains $_ }
"metricKeys: $($keys -join ',')"      | Tee-Object -FilePath "$Out/requests.log"
"не віддає сервер: $($missing -join ' ')" | Tee-Object -FilePath "$Out/requests.log" -Append

foreach ($f in $Files + @($null)) {
  $component = if ($f) { "$($env:PROJECT_KEY):$f" } else { $env:PROJECT_KEY }
  $url = "$SonarUrl/api/measures/component?component=$component&metricKeys=$($keys -join ',')"
  "GET $url" | Tee-Object -FilePath "$Out/requests.log" -Append
  $name = if ($f) { $f -replace '/', '_' } else { "project" }
  $json = Invoke-RestMethod -Headers $auth -Uri $url
  $json | ConvertTo-Json -Depth 6 | Tee-Object -FilePath "$Out/$name.json"
}
