## VLC Retry System and Memory Leak Fix - Testing Guide

This document provides testing instructions for the implemented VLC retry system and memory leak fixes.

### Overview of Changes

1. **New Management Classes**:
   - `MediaManager.kt` - Centralized VLC Media lifecycle management
   - `RetryManager.kt` - Exponential backoff retry logic (1s, 2s, 4s, 8s, 16s)

2. **Enhanced PlayerStatus**:
   - Added `RETRYING`, `RETRY_FAILED`, `LOADING` states

3. **Updated ViewModels**:
   - All VLC-using ViewModels now use MediaManager and RetryManager
   - Automatic retry on VLC errors
   - Manual retry methods available

4. **UI Enhancements**:
   - Retry button with loading animation
   - Status messages and attempt counters
   - Different states for retry scenarios

### Manual Testing Scenarios

#### 1. Memory Leak Testing
**Objective**: Verify VLC Media objects are properly released

**Steps**:
1. Monitor logcat for VLC memory leak messages: `adb logcat | grep "VLCObject"`
2. Play multiple channels/movies in sequence
3. Verify no "VLCObject finalized but not natively released" messages appear
4. Test extended playback sessions (>30 minutes)
5. Switch between content types (channels → movies → series)

**Expected Result**: No VLC memory leak messages in logs

#### 2. Automatic Retry Testing
**Objective**: Test automatic retry when VLC encounters errors

**Steps**:
1. Use intentionally broken stream URLs
2. Modify network to simulate connectivity issues
3. Observe retry UI states and attempt counters
4. Verify exponential backoff delays (1s, 2s, 4s, 8s, 16s)
5. Check retry failure after 5 attempts

**Expected Result**: 
- Retry UI shows with attempt counter
- Proper delays between attempts
- Appropriate failure message after 5 attempts

#### 3. Manual Retry Testing
**Objective**: Test user-initiated retry functionality

**Steps**:
1. Force a playback error
2. Wait for retry failure state
3. Press the retry button
4. Verify new retry sequence starts
5. Test retry button during active retry (should cancel and restart)

**Expected Result**: 
- Retry button appears in error/failure states
- Manual retry restarts the sequence
- UI updates appropriately

#### 4. UI Control Synchronization Testing
**Objective**: Verify player controls reflect correct states

**Test Cases**:
- During loading: Loading indicator visible
- During retry: Retry counter and status message
- During failure: Retry button available
- During playback: Normal play/pause button

**Steps**:
1. Test each state transition
2. Verify button states match actual player status
3. Test across all content types (channels, movies, series)
4. Verify loading indicators sync properly

#### 5. Edge Case Testing

**Network Interruption**:
1. Start playback
2. Disable network
3. Verify retry mechanism triggers
4. Re-enable network during retry
5. Verify recovery

**Rapid Content Switching**:
1. Quickly switch between channels/content
2. Verify no memory leaks during rapid switching
3. Verify retry states are properly cancelled
4. Verify no orphaned retry operations

**App Backgrounding**:
1. Start playback
2. Trigger retry state
3. Background app during retry
4. Return to app
5. Verify retry state is properly managed

### Verification Commands

**Check for Memory Leaks**:
```bash
adb logcat | grep -E "(VLCObject|MediaManager|Memory)"
```

**Monitor Retry Operations**:
```bash
adb logcat | grep -E "(RetryManager|RETRYING|retry)"
```

**Check Player State Changes**:
```bash
adb logcat | grep -E "(PlayerStatus|LOADING|ERROR)"
```

### Expected Log Messages

**Successful Media Management**:
```
MediaManager: Media set successfully
MediaManager: Previous media released successfully
```

**Retry Operations**:
```
RetryManager: Starting retry sequence, max attempts: 5
RetryManager: Retry attempt 1 of 5
RetryManager: Waiting 1000ms before next attempt
RetryManager: Retry succeeded on attempt 2
```

**Error Scenarios**:
```
RetryManager: All 5 retry attempts failed
MediaManager: Error releasing media after failed set
```

### Performance Verification

**Memory Usage**:
- Monitor app memory usage during extended playback
- Verify memory usage remains stable over time
- Check for memory spikes during content switching

**Network Efficiency**:
- Verify retry attempts don't create excessive network requests
- Check that failed retry attempts are properly aborted
- Verify exponential backoff reduces server load

### Integration Points

**ChannelsViewModel**:
- Channel switching with retry
- Live stream error recovery
- EPG loading synchronization

**MovieDetailsViewModel**:
- Movie playback retry
- Resume position handling during retry
- Quality switching with retry

**SeriesDetailsViewModel**:
- Episode playback retry
- Series navigation with retry
- Progress saving during retry operations

### Success Criteria

1. ✅ No VLC memory leak messages in logs during normal usage
2. ✅ Automatic retry triggers on VLC errors with proper UI feedback
3. ✅ Manual retry button available and functional in error states
4. ✅ Exponential backoff implemented correctly (1s, 2s, 4s, 8s, 16s)
5. ✅ Maximum 5 retry attempts enforced
6. ✅ UI controls synchronized with actual player state
7. ✅ Proper cleanup in all error scenarios
8. ✅ No orphaned retry operations after app backgrounding
9. ✅ Stable memory usage during extended playback
10. ✅ Consistent behavior across all content types (channels, movies, series)