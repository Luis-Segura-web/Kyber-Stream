# Dynamic Player Settings Implementation

## Overview
This implementation allows player settings (network buffer, hardware acceleration, stream format) to be applied dynamically without requiring an app restart.

## Changes Made

### 1. PreferenceManager.kt
- Added `getVLCOptions()` method that generates VLC options based on current preferences
- Replaces hardcoded options with dynamic settings
- Includes logging for debugging configuration application

### 2. Player ViewModels Updated
- **MovieDetailsViewModel.kt**: Uses dynamic options in `startPlayback()` and adds `updatePlayerSettings()`
- **SeriesDetailsViewModel.kt**: Uses dynamic options in episode playback and adds `updatePlayerSettings()`
- **ChannelsViewModel.kt**: Uses dynamic options in `setupVLC()` and channel selection, adds `updatePlayerSettings()`

### 3. SettingsViewModel.kt
- Added `SettingsEvent.PlayerSettingsChanged` event
- Added `notifyPlayerSettingsChanged()` method
- Settings change methods now emit notification events

## How to Connect Settings to Active Players

To complete the implementation, the UI layer (Activity/Fragment) should:

1. **Observe SettingsViewModel events**:
```kotlin
settingsViewModel.events.collect { event ->
    when (event) {
        is SettingsEvent.PlayerSettingsChanged -> {
            // Notify active player ViewModels
            movieDetailsViewModel?.updatePlayerSettings()
            seriesDetailsViewModel?.updatePlayerSettings()
            channelsViewModel?.updatePlayerSettings()
        }
        // ... other events
    }
}
```

2. **Alternative: Global Application-level notification**:
```kotlin
// In Application class or dependency injection setup
class PlayerSettingsNotifier {
    private val _settingsChanged = MutableSharedFlow<Unit>()
    val settingsChanged = _settingsChanged.asSharedFlow()
    
    fun notifySettingsChanged() {
        _settingsChanged.tryEmit(Unit)
    }
}
```

## Verification

The implementation has been tested with a mock to verify:
- Buffer settings map correctly (LOW=1000ms, MEDIUM=3000ms, HIGH=5000ms, ULTRA=8000ms)
- Hardware acceleration toggles properly
- Stream format specific options are added correctly
- Base performance options are always included

## Benefits

1. **Immediate application** - Settings apply without restart
2. **Dynamic configuration** - VLC options generated based on current preferences
3. **Better user experience** - No need to restart the app after changing settings
4. **Maintainable code** - Centralized VLC options logic in PreferenceManager