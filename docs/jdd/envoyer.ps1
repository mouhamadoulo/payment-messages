<#
.SYNOPSIS
    Publie un ou plusieurs jeux de données de docs/jdd/ sur la file d'entrée IBM MQ,
    via l'API de simulation (POST /api/v1/simulation/sends).

.DESCRIPTION
    Le payload part tel quel sur la file : c'est le consommateur applicatif qui le
    désérialise, le valide et décide de son sort. Rien n'est écrit en base par cette API.

    UniqueIds est FAUX par défaut, contrairement à l'IHM : les jeux de données portent des
    messageId choisis, et les réécrire casserait le scénario de déduplication (dossier
    04-idempotence) autant que la relecture d'un cas précis dans la liste.

    Le serveur n'accepte qu'un envoi en vol à la fois : le script attend donc la fin de
    chaque envoi avant de lancer le suivant.

.PARAMETER Chemin
    Fichier .json / .ndjson, ou dossier parcouru récursivement.

.PARAMETER BaseUrl
    Origine de l'API. Défaut : http://localhost:8080

.PARAMETER Count
    Nombre de copies par payload. Au-delà de 1, activer -UniqueIds sinon l'ingestion,
    idempotente sur messageId, n'en gardera qu'une.

.PARAMETER RatePerSecond
    Cadence de publication.

.PARAMETER UniqueIds
    Laisse le serveur réécrire messageId sur chaque copie. À réserver à la volumétrie.

.PARAMETER InclurePoison
    Autorise l'envoi des fichiers marqués POISON, écartés par défaut : ils provoquent une
    redélivrance en boucle jusqu'au seuil de backout de la file.

    Aucun jeu ne porte ce marqueur aujourd'hui : le seul qui l'avait, un messageId trop long,
    est désormais borné au contrat (@Size) et rejeté définitivement. Le filtre reste pour un
    jeu futur.

.EXAMPLE
    .\envoyer.ps1 .\01-nominal\01-virement-sepa.json

.EXAMPLE
    .\envoyer.ps1 .\01-nominal

.EXAMPLE
    .\envoyer.ps1 .\07-volumetrie\gabarit.json -Count 500 -RatePerSecond 100 -UniqueIds
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Chemin,

    [string]$BaseUrl = 'http://localhost:8080',

    [int]$Count = 1,

    [int]$RatePerSecond = 20,

    [switch]$UniqueIds,

    [switch]$InclurePoison
)

$ErrorActionPreference = 'Stop'

function Get-Payloads {
    param([string]$Fichier)

    if ([System.IO.Path]::GetExtension($Fichier) -eq '.ndjson') {
        # Une ligne = un message. Le fichier entier n'est pas un payload valide.
        return Get-Content -Path $Fichier -Encoding UTF8 | Where-Object { $_.Trim().Length -gt 0 }
    }
    return , (Get-Content -Path $Fichier -Raw -Encoding UTF8)
}

function Send-Payload {
    param([string]$Payload)

    $corps = @{
        payload       = $Payload
        count         = $Count
        ratePerSecond = $RatePerSecond
        uniqueIds     = [bool]$UniqueIds
    } | ConvertTo-Json -Depth 3

    # PowerShell 5.1 encode le corps en ISO-8859-1 par défaut : les accents et idéogrammes
    # du jeu 06-limites-et-pieges arriveraient mutilés sur la file.
    $octets = [System.Text.Encoding]::UTF8.GetBytes($corps)

    return Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/simulation/sends" `
        -ContentType 'application/json; charset=utf-8' -Body $octets
}

function Wait-Task {
    param([string]$TaskId)

    # Un seul envoi en vol côté serveur : sans cette attente, l'appel suivant se verrait
    # rendre la tâche déjà en cours au lieu d'en démarrer une nouvelle.
    for ($i = 0; $i -lt 600; $i++) {
        $etat = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/v1/simulation/sends/$TaskId"
        if ($etat.state -ne 'RUNNING') { return $etat }
        Start-Sleep -Milliseconds 200
    }
    Write-Warning "Tâche $TaskId toujours en cours après 2 minutes, on continue."
    return $null
}

if (-not (Test-Path -Path $Chemin)) {
    throw "Chemin introuvable : $Chemin"
}

if (Test-Path -Path $Chemin -PathType Container) {
    $fichiers = Get-ChildItem -Path $Chemin -Recurse -File |
        Where-Object { $_.Extension -in @('.json', '.ndjson') -and $_.Name -ne 'manifeste.json' } |
        Sort-Object FullName
} else {
    $fichiers = @(Get-Item -Path $Chemin)
}

if (-not $InclurePoison) {
    $poison = $fichiers | Where-Object { $_.Name -like '*POISON*' }
    foreach ($p in $poison) {
        Write-Warning "Écarté (message empoisonné, -InclurePoison pour l'envoyer) : $($p.Name)"
    }
    $fichiers = $fichiers | Where-Object { $_.Name -notlike '*POISON*' }
}

if (-not $fichiers) {
    Write-Host 'Aucun fichier à envoyer.'
    return
}

Write-Host "Destination : file configurée côté serveur (ibm.mq.queue)" -ForegroundColor DarkGray
Write-Host "uniqueIds   : $([bool]$UniqueIds)" -ForegroundColor DarkGray
Write-Host ''

$totalPublies = 0
$totalEchecs = 0

foreach ($fichier in $fichiers) {

    $payloads = @(Get-Payloads -Fichier $fichier.FullName)
    $numero = 0

    foreach ($payload in $payloads) {
        $numero++
        $etiquette = $fichier.Name
        if ($payloads.Count -gt 1) { $etiquette = "$($fichier.Name) [ligne $numero/$($payloads.Count)]" }

        try {
            $tache = Send-Payload -Payload $payload
        } catch {
            $reponse = $_.Exception.Response
            if ($reponse -and $reponse.StatusCode.value__ -eq 503) {
                throw "Simulation désactivée sur cet environnement (app.simulation.enabled = false)."
            }
            Write-Host ("  ECHEC  {0} : {1}" -f $etiquette, $_.Exception.Message) -ForegroundColor Red
            $totalEchecs++
            continue
        }

        $final = Wait-Task -TaskId $tache.taskId

        if ($null -eq $final) {
            $totalEchecs++
        } elseif ($final.state -eq 'COMPLETED' -and $final.failed -eq 0) {
            Write-Host ("  OK     {0} -> {1} publié(s) sur {2}" -f $etiquette, $final.published, $final.destination) -ForegroundColor Green
            $totalPublies += $final.published
        } else {
            Write-Host ("  ECHEC  {0} -> état {1}, {2} échec(s) : {3}" -f $etiquette, $final.state, $final.failed, $final.error) -ForegroundColor Red
            $totalPublies += $final.published
            $totalEchecs += $final.failed
        }
    }
}

Write-Host ''
Write-Host "Publiés : $totalPublies | Échecs de publication : $totalEchecs"
Write-Host "Sort applicatif de chaque message : GET $BaseUrl/api/v1/messages" -ForegroundColor DarkGray
