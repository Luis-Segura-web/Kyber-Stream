
# Guía para agentes AI — Kyber-Stream IPTV Android

## Propósito y arquitectura
App IPTV para Android (TV/móvil) usando Xtream Codes. **Solo se permite 1 stream activo por dispositivo**. Player principal: Media3 (ExoPlayer), alternativo: LibVLC. DI con Hilt, UI en Compose. El objetivo es cumplir la política single-connection del proveedor, evitando overlaps y baneos.

## Reglas clave
- **Nunca** abrir dos streams simultáneos (incluye PiP, background, cast, fallback).
- Al reproducir, adquirir lease exclusivo vía `StreamingLeaseManager`. Si ocupado, mostrar diálogo y permitir forzar/cancelar.
- Liberar lease antes de cambiar canal/engine. Aplicar cooldown (1–3s) entre cierres/aberturas.
- En cast, transferir sesión y cortar local de inmediato.
- En fallback a VLC, liberar Media3 antes de crear LibVLC (y viceversa).
- No hacer prebuffer de streams; solo precargar UI.

## Componentes principales
- `StreamingLeaseManager` (`core/player`): controla el lease de streaming. API: `tryAcquire`, `forceAcquire`, `release`.
- `PlayerCoordinator`: orquesta engines y lease, garantiza cero overlaps.
- `PlayerEngine` (interfaz): implementaciones `Media3Engine` y `VlcEngine`.
- `XtreamSecureStore`: almacena credenciales Xtream encriptadas.
- DataStore (protobuf) para settings (`app/src/main/proto/settings.proto`).

## Flujos críticos
- **Reproducción:** Usar `PlayerCoordinator.play(ownerId, media)`; si lease ocupado, mostrar diálogo y usar `forcePlay` si el usuario confirma.
- **Cambio de canal/engine:** Siempre liberar antes de preparar el nuevo stream.
- **Transferencia a Cast:** Detener local y reproducir en remoto.
- **Fallback:** Solo crear engine alternativo tras liberar el anterior.

## Convenciones y patrones
- Identificadores en inglés (`camelCase`/`PascalCase`).
- Comentarios en español latino.
- Formato: ktlint/spotless.
- Logging con Timber, nunca loggear credenciales ni URLs completas (redactar con `url.replaceAfterLast("/", "***")`).
- Tests unitarios para lease, coordinator, parsers y repositorios.

## Build, test y configuración
- Build principal: `./gradlew build` (o desde IDE).
- Protobuf: ver configuración en `app/build.gradle.kts` y `settings.proto`.
- Instrumented tests en `app/src/test/java/com/kybers/play/`.

## Ejemplo de workflow de reproducción
```kotlin
val started = coordinator.play(ownerId, media)
if (!started) {
    // Mostrar diálogo de lease ocupado y permitir forcePlay
}
```

## Antipatrones prohibidos
- Dos players simultáneos (incluye PiP/prebuffer)
- Cambiar canal sin `release()` previo
- Fallback sin cerrar el engine anterior
- Preplay que abra sockets HLS/DASH
- Logs con credenciales/URLs completas

## Archivos clave
- `core/player/StreamingLeaseManager.kt`, `PlayerCoordinator.kt`, `PlayerEngine.kt`
- `core/security/XtreamSecureStore.kt`
- `proto/settings.proto`, `build.gradle.kts`

## Idioma
Toda la comunicación y generación de código debe ser en español latino.
