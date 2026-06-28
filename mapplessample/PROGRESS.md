# FastRoute - Biker Navigation App Progress

## Date: 2026-06-08

## Status: IN PROGRESS - Build running, battery low

---

## What's Working

| Feature | Status |
|---------|--------|
| Mappls SDK 8.3.1 (legacy OAuth 2.0) | ✅ Working |
| Map tiles loading | ✅ Working |
| API Key `6028f7f20d0f77db48c8de02979e8cf3` | ✅ Valid |
| WiFi debugging (192.168.29.155:5555) | ✅ Connected |
| Device: Realme CPH2661 (Android 16) | ✅ Connected |

## What's Built (Compose Rewrite)

| File | Purpose |
|------|---------|
| `app/build.gradle.kts` | Compose BOM + Mappls SDK + OkHttp + Gson |
| `build.gradle.kts` | Root: Kotlin 2.0.21 + Compose plugin |
| `settings.gradle.kts` | Mappls Maven repo |
| `FastRouteApplication.kt` | SDK init with keys |
| `MainActivity.kt` | Compose entry point |
| `ui/theme/Theme.kt` | Material3 teal theme |
| `MapScreen.kt` | Main screen: Map + Search + Route |
| `api/MapplsApi.kt` | REST API: search + routing |
| `AndroidManifest.xml` | Permissions + meta-data |

## What's Implemented

1. **Map with lifecycle** - MapView inside AndroidView with DisposableEffect lifecycle
2. **Search bar** - "Where to, biker?" with Mappls autosuggest
3. **Destination markers** - Place marker on selected destination
4. **Biker routing** - `driving-2-wheel` profile via Mappls Routing API
5. **Route polyline** - Decoded and drawn on map in teal (#00897B)
6. **Route info panel** - Distance (km) + Duration (min) + "GO" button
7. **My Location FAB** - Centers map on current location
8. **Camera bounds** - Auto-fits to show entire route

## Current Issue

**Black screen on launch** - Fixed by:
- Adding proper Activity lifecycle to MapView via DisposableEffect
- Using `activity.intent?.extras` for onCreate
- Adding `ON_START/ON_RESUME/ON_PAUSE/ON_STOP/ON_DESTROY` observers

**GPS not working** - Location permission requested but `lastKnownLocation` returns null because:
- Device GPS may be off
- First launch needs time to get fix
- Need to add `FusedLocationProviderClient` for reliable GPS

## Next Session To-Do

1. **Check if build succeeded** - Run:
   ```
   # From mapplessample directory
   cd C:\Users\srath\Downloads\mapplessample
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -version
   ```

2. **Install and test** (if build passed):
   ```
   adb -s 192.168.29.155:5555 install -r app\build\outputs\apk\debug\app-debug.apk
   adb -s 192.168.29.155:5555 shell am start -n com.example.fastenroute/.MainActivity
   ```

3. **Fix GPS** - Add FusedLocationProviderClient:
   ```kotlin
   // In MapScreen.kt, replace lastKnownLocation with:
   val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
   fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
       if (loc != null) {
           currentLat = loc.latitude
           currentLng = loc.longitude
       }
   }
   ```

4. **Reconnect WiFi debugging** (if disconnected):
   ```
   adb tcpip 5555
   adb connect 192.168.29.155:5555
   ```

## Build Command (for next session)

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\srath\AppData\Local\Android\Sdk"
& "C:\Users\srath\.gradle\wrapper\dists\gradle-8.11.1-bin\bpt9gzteqjrbo1mjrsomdt32c\gradle-8.11.1\bin\gradle.bat" assembleDebug --no-daemon
```

## Key Credentials

| Key | Value |
|-----|-------|
| REST API Key | `6028f7f20d0f77db48c8de02979e8cf3` |
| Map SDK Key | `6028f7f20d0f77db48c8de02979e8cf3` |
| Atlas Client ID | `96dHZVzsAusVK6FU4fGDDFrrFjNKTicKf9s5uxqepvMUkgNbN7ohQiwG2msyhfGCtBgjyjEtJ6hz-AKhDpnGQA==` |
| Atlas Client Secret | `lrFxI-iSEg9iM76CbYdAoW35RGGg8KRx1Hy8H2__HLHPn_iKQWf_Cf_hp727UXiHUAKVYJ0D-19AgLDvs0K89THliUOw0ksA` |
| Device WiFi ADB | `192.168.29.155:5555` |
| Package | `com.example.fastenroute` |
| Debug SHA-256 | `43:5B:28:79:79:C8:D2:DC:E5:95:D0:E5:43:8E:2F:DF:2D:94:85:6B:69:0D:11:87:26:8E:A8:C4:F5:CE:3D:26` |
