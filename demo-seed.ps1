param([string]$Base = 'http://localhost:8080')
$ErrorActionPreference = 'Continue'
$pwd0 = 'Password@123'
$tokens = @{}
function Tok($email) {
    if ($tokens.ContainsKey($email)) { return $tokens[$email] }
    for ($try = 0; $try -lt 2; $try++) {
        try {
            $t = (Invoke-RestMethod -Method Post "$Base/api/auth/login" -ContentType 'application/json' -Body (@{ email = $email; password = $pwd0 } | ConvertTo-Json)).accessToken
            $tokens[$email] = $t
            return $t
        } catch {
            if ("$($_.ErrorDetails.Message)" -match '429|Too many') { Write-Host '  rate limit, 65 sec wait...' -ForegroundColor Yellow; Start-Sleep 65 }
            else { Write-Host "  login FAIL $email : $($_.ErrorDetails.Message)" -ForegroundColor Red; return $null }
        }
    }
    return $null
}
function Call($method, $path, $tok, $body) {
    $p = @{ Method = $method; Uri = "$Base$path"; Headers = @{ Authorization = "Bearer $tok" } }
    if ($null -ne $body) { $p.ContentType = 'application/json'; $p.Body = ($body | ConvertTo-Json -Depth 5) }
    try { Invoke-RestMethod @p } catch { Write-Host "  ERR $method $path : $($_.ErrorDetails.Message)" -ForegroundColor Red; $null }
}
function Iso($d) { $d.ToString('yyyy-MM-ddTHH:mm:ss') }
function Advance($vol, $did, $statuses) {
    $vt = Tok "$vol@foodloop.test"
    foreach ($s in $statuses) {
        $r = Call 'PATCH' "/api/volunteer/deliveries/$did/status" $vt @{ status = $s }
        if (-not $r) { return $false }
    }
    return $true
}

$loc = @{
 'RS Puram'       = @('14 DB Road', '641002', 11.0074, 76.9500)
 'Gandhipuram'    = @('88 Cross Cut Road', '641012', 11.0183, 76.9725)
 'Peelamedu'      = @('27 Avinashi Road', '641004', 11.0270, 77.0200)
 'Race Course'    = @('5 Racecourse Road', '641018', 11.0010, 76.9750)
}
$plan = @(
 @('annapoorna@foodloop.test', 1, 'Veg meals (sambar rice, poriyal)', 28, 'KG', 'HOT_HELD', $true, 8, 'RS Puram', 'Hotel lunch buffet leftovers'),
 @('annapoorna@foodloop.test', 1, 'Curd rice and lemon rice', 18, 'KG', 'ROOM_TEMP', $true, 7, 'RS Puram', 'Prepared this morning'),
 @('annapoorna@foodloop.test', 8, 'Mysore pak and laddu', 6, 'KG', 'ROOM_TEMP', $true, 48, 'RS Puram', 'Festival sweets, sealed boxes'),
 @('greenleaf@foodloop.test', 3, 'Mixed vegetables', 45, 'KG', 'ROOM_TEMP', $true, 36, 'Gandhipuram', 'Fresh stock, slight cosmetic marks'),
 @('greenleaf@foodloop.test', 3, 'Bananas and papaya', 30, 'KG', 'ROOM_TEMP', $true, 30, 'Gandhipuram', 'Ripe fruit, good for same-day use'),
 @('greenleaf@foodloop.test', 7, 'Rice and dal sacks', 60, 'KG', 'ROOM_TEMP', $true, 120, 'Gandhipuram', 'Packaging torn, grain is clean'),
 @('freshmart@foodloop.test', 2, 'Bread loaves and buns', 22, 'KG', 'ROOM_TEMP', $true, 14, 'Peelamedu', 'Baked today, unsold'),
 @('freshmart@foodloop.test', 4, 'Packed milk', 40, 'LITRE', 'REFRIGERATED', $true, 40, 'Peelamedu', 'Close to best-before date'),
 @('freshmart@foodloop.test', 5, 'Biscuits and snack packs', 25, 'KG', 'ROOM_TEMP', $true, 168, 'Peelamedu', 'Sealed retail packs'),
 @('celebration@foodloop.test', 1, 'Biryani and raita', 55, 'KG', 'HOT_HELD', $false, 8, 'Race Course', 'Wedding reception surplus'),
 @('celebration@foodloop.test', 1, 'Chapati and paneer curry', 35, 'KG', 'HOT_HELD', $true, 7, 'Race Course', 'Corporate event surplus'),
 @('celebration@foodloop.test', 6, 'Fruit juice bottles', 24, 'LITRE', 'REFRIGERATED', $true, 48, 'Race Course', 'Unopened bottles')
)

Write-Host '=== 1. Donations ===' -ForegroundColor Cyan
$now = Get-Date
$created = @()
foreach ($p in $plan) {
    $t = Tok $p[0]
    if (-not $t) { continue }
    $l = $loc[$p[8]]
    $body = [ordered]@{
        categoryId = $p[1]; foodName = $p[2]; quantity = $p[3]; unit = $p[4]
        preparedAt = Iso $now.AddHours(-1); expiresAt = Iso $now.AddHours($p[7])
        storageCondition = $p[5]; vegetarian = $p[6]; description = $p[9]
        pickupLocation = [ordered]@{ addressLine = $l[0]; area = $p[8]; city = 'Coimbatore'; state = 'Tamil Nadu'; pincode = $l[1]; latitude = $l[2]; longitude = $l[3] }
        pickupWindowStart = Iso $now.AddMinutes(30)
        pickupWindowEnd = Iso $now.AddHours([Math]::Min(5, $p[7] - 1))
        safetyDeclared = $true
    }
    $r = Call 'POST' '/api/donations' $t $body
    if ($r) {
        $created += [pscustomobject]@{ id = [long]$r.id; name = $p[2]; kg = [double]$r.quantityKg; start = [datetime]$r.pickupWindowStart }
        Write-Host ("  OK #{0} {1} ({2})" -f $r.id, $p[2], $r.status) -ForegroundColor Green
    }
}
if ($created.Count -eq 0) { Write-Host 'Donation onnum create aagala, stop.' -ForegroundColor Red; return }

Write-Host '=== 2. NGO accept and pickup ===' -ForegroundColor Cyan
$ids = @($created | ForEach-Object { $_.id })
$best = @{}
foreach ($n in 'hopemeals', 'annadhanam', 'littlehearts', 'seva') {
    $t = Tok "$n@foodloop.test"
    if (-not $t) { continue }
    $r = Call 'GET' '/api/ngo/recommended?size=200' $t $null
    foreach ($it in @($r.content)) {
        $d = [long]$it.donation.id
        if ($ids -contains $d -and (-not $best.ContainsKey($d) -or $it.rank -lt $best[$d].rank)) { $best[$d] = @{ ngo = $n; matchId = $it.matchId; rank = $it.rank } }
    }
}
$pickups = @()
foreach ($ix in 0, 2, 3, 6, 9, 10) {
    if ($ix -ge $created.Count) { continue }
    $c = $created[$ix]
    $m = $best[[long]$c.id]
    if (-not $m) { Write-Host "  #$($c.id) match kaanom, skip" -ForegroundColor Yellow; continue }
    $nt = Tok "$($m.ngo)@foodloop.test"
    $a = Call 'POST' "/api/ngo/matches/$($m.matchId)/accept" $nt $null
    if (-not $a) { continue }
    $pk = Call 'POST' '/api/ngo/pickups' $nt @{ donationId = $c.id; scheduledPickupAt = (Iso $c.start.AddMinutes(15)) }
    if (-not $pk) { continue }
    $pickups += [pscustomobject]@{ pid = $pk.id; c = $c; ngo = $m.ngo; vol = $null; did = $null }
    Write-Host ("  #{0} {1} -> {2} (pickup {3})" -f $c.id, $c.name, $m.ngo, $pk.id) -ForegroundColor Green
}

Write-Host '=== 3. Volunteers ===' -ForegroundColor Cyan
$vols = 'vol.karthik', 'vol.divya', 'vol.sanjay'
$n = 0
foreach ($pk in @($pickups | Select-Object -First 4)) {
    for ($k = 0; $k -lt $vols.Count -and -not $pk.vol; $k++) {
        $v = $vols[($n + $k) % $vols.Count]
        $vt = Tok "$v@foodloop.test"
        if (-not $vt) { continue }
        $va = Call 'POST' "/api/volunteer/pickups/$($pk.pid)/accept" $vt $null
        if ($va) { $pk.vol = $v; $pk.did = $va.deliveryId; Write-Host "  pickup $($pk.pid) -> $v (delivery $($va.deliveryId))" -ForegroundColor Green }
    }
    $n++
}

Write-Host '=== 4. Deliveries ===' -ForegroundColor Cyan
$assigned = @($pickups | Where-Object { $_.vol -and $_.did })
if ($assigned.Count -ge 1) {
    $a = $assigned[0]
    if (Advance $a.vol $a.did @('PICKED_UP', 'IN_TRANSIT', 'DELIVERED')) {
        $recv = [math]::Round($a.c.kg * 0.98, 1)
        $cf = Call 'POST' "/api/ngo/deliveries/$($a.did)/confirm" (Tok "$($a.ngo)@foodloop.test") @{ quantityReceivedKg = $recv; note = 'Received in good condition' }
        if ($cf) { Write-Host "  $($a.c.name): delivered and confirmed ($recv kg)" -ForegroundColor Green }
    }
}
if ($assigned.Count -ge 2) { $a = $assigned[1]; if (Advance $a.vol $a.did @('PICKED_UP', 'IN_TRANSIT')) { Write-Host "  $($a.c.name): in transit" -ForegroundColor Green } }
if ($assigned.Count -ge 3) { $a = $assigned[2]; if (Advance $a.vol $a.did @('PICKED_UP')) { Write-Host "  $($a.c.name): picked up" -ForegroundColor Green } }
Write-Host "`nDemo data ready: $($created.Count) donations, $($pickups.Count) pickups, $($assigned.Count) with volunteers." -ForegroundColor Cyan