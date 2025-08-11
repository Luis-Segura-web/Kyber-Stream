# GitHub Copilot Instructions — IPTV Android (Media3 + VLC + Xtream Codes, single-connection)

> **Contexto**
> App IPTV para Android (TV y móvil) con Xtream Codes (live/VOD/series/EPG). **Solo se permite 1 conexión de streaming activa por dispositivo**. Player principal: **Media3**. Alternativo: **VLC (LibVLC)**. DI con Hilt, UI en Compose.

---

## Estilo y lenguaje
- Comentarios en **español latino**.
- Identificadores en **inglés** (`camelCase` / `PascalCase`).
- Formato con **ktlint/spotless**.

## Stack tecnológico (fijo)
- **Kotlin**: 2.2.0
- **AGP**: 8.12.0  
- **KSP**: 2.2.0-2.0.2
- **UI**: Compose + Hilt
- **Async**: Coroutines/Flow
- **Player**: Media3 (ExoPlayer) + LibVLC (alternativo)
- **Network**: Retrofit o Ktor + kotlinx.serialization
- **Storage**: Room + DataStore (proto) + EncryptedSharedPreferences
- **Background**: WorkManager
- **Image**: Coil
- **Logging**: Timber

---

## Regla fundamental: **Single Streaming Connection**

### Restricciones obligatorias
- **Nunca** tener dos reproducciones simultáneas (local, PiP, background, o cast).
- Al iniciar reproducción: **adquirir un "lease" exclusivo**. Si ya hay uno, **cancelar** la reproducción anterior o **bloquear** el intento actual con UI de aviso.
- **Liberar** antes de cambiar de canal o engine. Introducir **cooldown 1–3 s** al cambiar para evitar que el servidor cuente conexiones solapadas.
- En **casting**: transferir sesión y **detener inmediatamente** la reproducción local.
- En **VLC fallback**: **release** de Media3 antes de crear LibVLC (y viceversa).
- Los **requests de API/EPG** pueden ser concurrentes; la **restricción aplica solo al stream** (live/VOD).
- No hacer "preplay" que abra sockets HLS/DASH de un segundo canal (permitido: precargar UI; **prohibido** precargar stream).

### Componentes arquitectónicos obligatorios

#### `StreamingLeaseManager` (singleton en `core.player`)
- **Estado**: `None | Acquired(ownerId)` + `cooldownUntil`
- **API**: `tryAcquire(ownerId)`, `forceAcquire(ownerId)`, `release(ownerId)`, `withLease(ownerId) { ... }`

#### `PlayerCoordinator`
- Orquesta `PlayerEngine` y habla con `StreamingLeaseManager`
- Garantiza **1 engine vivo** y **0 overlaps** al hacer `play/switch/stop`

#### `PlayerEngine` (interfaz)
- Implementaciones: `Media3Engine` y `VlcEngine`

---

## Xtream Codes

### Endpoints y autenticación
- **Login y catálogos**: `player_api.php`
- **Streams**: `.../live/<u>/<p>/<streamId>.m3u8` (preferido) o `.ts/.mp4`
- **Headers**: Respetar headers por canal (`User-Agent`, `Referer`, `Authorization`)

### Seguridad
- **Nunca** loggear credenciales/URLs completas
- Usar **EncryptedSharedPreferences** para almacenamiento de credenciales
- Redactar URLs en logs: `url.replaceAfterLast("/", "***")`

---

## UI/UX (Compose)

### Gestión de lease ocupado
- Al intentar reproducir con lease ocupado:
  - Mostrar diálogo: "Ya hay una reproducción activa. ¿Detener y reproducir este canal?"
  - Botones: **Cambiar** (libera anterior y toma lease) / **Cancelar**
- Deshabilitar "Play" si `cooldown` activo; mostrar contador breve
- En transfer a Cast: aviso "Reproducción movida a dispositivo X"

---

## Telemetría y monitoring

### Eventos a registrar
- `play_start`, `play_end`, `lease_acquire`, `lease_blocked`, `switch_engine`, `cast_transfer`
- **Metadatos**: `engine`, `channelId/streamId`, `host`, `bitrate`, `bufferMs`, `errorCode`

---

## Testing estratégico

### Unit tests
- `StreamingLeaseManager`: carreras, release, cooldown
- `PlayerCoordinator`: no overlaps
- Parsers M3U/XMLTV
- Repositorios Xtream

### Instrumented tests
- Room
- Repositorios con network

### UI tests
- Flujos de "lease ocupado" y "cooldown"

---

## Antipatrones (nunca sugerir)
- ❌ Dos players simultáneos (incluye PiP o prebuffer)
- ❌ Cambiar de canal sin `release()` previo
- ❌ Fallback a VLC **sin** cerrar Media3 primero
- ❌ "Preplay" del siguiente canal que abra socket HLS/DASH
- ❌ Logs con credenciales/URLs completas

---

## Plantillas de código

### `StreamingLeaseManager`
```kotlin
@Singleton
class StreamingLeaseManager @Inject constructor(
    private val clock: Clock = Clock.systemUTC()
) {
    private val mutex = Mutex()
    private var owner: String? = null
    private var cooldownUntil: Instant? = null

    private val _state = MutableStateFlow<LeaseState>(LeaseState.None)
    val state: StateFlow<LeaseState> = _state.asStateFlow()

    suspend fun tryAcquire(ownerId: String): Boolean = mutex.withLock {
        val now = Instant.now(clock)
        if (cooldownUntil?.isAfter(now) == true) return false
        if (owner == null) {
            owner = ownerId
            _state.value = LeaseState.Acquired(ownerId)
            true
        } else {
            false
        }
    }

    suspend fun forceAcquire(ownerId: String) = mutex.withLock {
        owner = ownerId
        cooldownUntil = null
        _state.value = LeaseState.Acquired(ownerId)
    }

    suspend fun release(ownerId: String, cooldown: Duration = Duration.ofSeconds(2)) = mutex.withLock {
        if (owner == ownerId) {
            owner = null
            cooldownUntil = Instant.now(clock).plus(cooldown)
            _state.value = LeaseState.None
        }
    }

    sealed interface LeaseState {
        data object None : LeaseState
        data class Acquired(val ownerId: String) : LeaseState
    }
}
```

### `PlayerCoordinator`
```kotlin
@Singleton
class PlayerCoordinator @Inject constructor(
    private val selector: PlayerSelector,
    private val lease: StreamingLeaseManager
) {
    private var engine: PlayerEngine? = null
    private var ownerId: String? = null

    suspend fun play(ownerId: String, media: MediaSpec): Boolean {
        val acquired = lease.tryAcquire(ownerId)
        if (!acquired) return false // UI mostrará diálogo para forzar

        stopInternal() // seguridad extra (no overlap)
        engine = selector.create()
        this.ownerId = ownerId
        engine!!.setMedia(media)
        engine!!.play()
        return true
    }

    suspend fun forcePlay(ownerId: String, media: MediaSpec) {
        lease.forceAcquire(ownerId)
        stopInternal()
        engine = selector.create()
        this.ownerId = ownerId
        engine!!.setMedia(media)
        engine!!.play()
    }

    suspend fun switchChannel(ownerId: String, media: MediaSpec) {
        // Cambio "hard": liberar antes de preparar el nuevo
        engine?.pause()
        engine?.release()
        engine = selector.create()
        this.ownerId = ownerId
        engine!!.setMedia(media)
        engine!!.play()
    }

    suspend fun stop(ownerId: String) {
        if (this.ownerId == ownerId) {
            stopInternal()
            lease.release(ownerId)
        }
    }

    suspend fun stopAll() {
        stopInternal()
        lease.forceAcquire("system_cleanup")
        lease.release("system_cleanup")
    }

    private fun stopInternal() {
        engine?.release()
        engine = null
        this.ownerId = null
    }
}
```

### ViewModel con diálogo de lease ocupado
```kotlin
fun onPlayRequested(stream: MediaSpec) = viewModelScope.launch {
    val ownerId = "player:${stream.url.hashCode()}"
    val started = coordinator.play(ownerId, stream)
    if (!started) {
        _ui.update { it.copy(showLeaseDialog = true, pendingStream = stream) }
    }
}

fun onConfirmForcePlay() = viewModelScope.launch {
    ui.value.pendingStream?.let { pending ->
        val ownerId = "player:${pending.url.hashCode()}"
        coordinator.forcePlay(ownerId, pending)
        _ui.update { it.copy(showLeaseDialog = false, pendingStream = null) }
    }
}
```

### Transferencia a Cast
```kotlin
castManager.onSessionStarted { device ->
    viewModelScope.launch {
        coordinator.stop(currentOwnerId) // corta conexión local inmediatamente
        // preparar MediaItem para Cast (solo Media3)
        castController.play(remoteMediaItem)
    }
}
```

---

## Configuración de DataStore (Protobuf)

### `app/src/main/proto/settings.proto`
```proto
syntax = "proto3";
option java_package = "com.kyberstream.settings";
option java_multiple_files = true;

message Settings {
  enum PlayerPref {
    AUTO = 0;
    MEDIA3 = 1;
    VLC = 2;
  }

  // Player preferido (principal/alternativo/auto)
  PlayerPref player_pref = 1;

  // Si es true, corta la reproducción al ir a background
  bool stop_on_background = 2;

  // Cooldown en ms entre cierres/aberturas para evitar doble conexión
  int32 cooldown_ms = 3;

  // Habilita fallback automático a VLC cuando Media3 falla
  bool enable_auto_fallback = 4;
}
```

### Configuración Gradle
```kotlin
// En app/build.gradle.kts
plugins {
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    implementation("androidx.datastore:datastore:1.1.1")
    implementation("com.google.protobuf:protobuf-javalite:3.25.5")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                register("java") {
                    option("lite")
                }
            }
        }
    }
}
```

---

## Lifecycle Management

### PlaybackLifecycleObserver
```kotlin
@Singleton
class PlaybackLifecycleObserver @Inject constructor(
    private val settings: SettingsDataStore,
    private val coordinator: PlayerCoordinator
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var stopOnBackground: Boolean = true

    init {
        scope.launch {
            settings.settings.collect { stopOnBackground = it.stopOnBackground }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        // App se fue a background
        if (stopOnBackground) {
            scope.launch {
                coordinator.stopAll()
            }
        }
    }
}
```

---

## Seguridad Xtream Codes

### Almacenamiento seguro
```kotlin
@Singleton
class XtreamSecureStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secrets_xtream",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(secrets: XtreamSecrets) {
        prefs.edit()
            .putString("baseUrl", secrets.baseUrl.trimEnd('/') + "/")
            .putString("username", secrets.username)
            .putString("password", secrets.password)
            .apply()
    }

    fun load(): XtreamSecrets? {
        val base = prefs.getString("baseUrl", null) ?: return null
        val user = prefs.getString("username", null) ?: return null
        val pass = prefs.getString("password", null) ?: return null
        return XtreamSecrets(base, user, pass)
    }

    fun clear() = prefs.edit().clear().apply()
}
```

---

## Checklist de Pull Request

* [ ] `StreamingLeaseManager` y `PlayerCoordinator` implementados y testeados
* [ ] **Cero overlaps** en logs: cada play libera el anterior antes de abrir conexión
* [ ] Cooldown aplicado (≥ 1 s) al cambiar/stop
* [ ] Transferencia a Cast corta local de inmediato
* [ ] Fallback a VLC **después** de liberar Media3
* [ ] Sin prebuffer de streams; solo UI
* [ ] Credenciales Xtream seguras y sin filtraciones en logs
* [ ] Lifecycle observer registrado correctamente
* [ ] DataStore configurado con protobuf
* [ ] Tests unitarios para componentes críticos

---

**Objetivo**: Cumplir la política **single-connection** del proveedor IPTV, evitando desconexiones y baneos, con UX clara y código estable, moderno y testeable.
