# Roomy: Production-Ready Android MediaStore & System Consent Architecture

This plan establishes real, production-ready Android storage and media workflows for Roomy. It completely removes all synthetic dummy data, integrates real Android `MediaStore` queries via `ContentResolver`, declares and requests modern Android runtime permissions (`READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO`), and utilizes the official Android 11+ system deletion consent dialog (`MediaStore.createDeleteRequest`).

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed by the user in Phase 1 clarification:

- **MediaStore Permissions with Fallback**: The app will request standard Android runtime media permissions (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` on Android 13+, and `READ_EXTERNAL_STORAGE` on older versions). If the user grants partial access or denies permission, Roomy gracefully falls back to the zero-permission Android Photo Picker (`ActivityResultContracts.PickMultipleVisualMedia`).
- **Official OS Deletion Consent Flow**: Gallery media deletions will trigger Android's native system prompt via `MediaStore.createDeleteRequest(contentResolver, uris)` and `ActivityResultContracts.StartIntentSenderForResult()`. Android itself asks the user to authorize permanent deletion, complying fully with Scoped Storage.
- **Zero Dummy Data**: All synthetic file generators (`prepareSampleMediaIfEmpty`) are completely removed. If the gallery contains zero media items, Roomy displays an honest diagnostic scan report indicating 0 items found, with a button to pick files manually.

---

## 1. Overview & Core Concept

Roomy's production implementation connects directly to the Android OS storage provider:
1. **Real Media Inventory**: Scans actual on-device images and videos stored in the user's camera roll, downloads, screenshots, and movies folders using `ContentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ...)` and `MediaStore.Video.Media.EXTERNAL_CONTENT_URI`.
2. **Permission Lifecycle**: Checks permission state dynamically. Prompts for runtime permissions when appropriate, handles granted, partial, and denied states, and provides deep links to Android App Settings if permission is permanently denied.
3. **Hardware-Accelerated Local Compression**: Real on-device compression targeting the user's genuine photos and videos, saving outputs to app-owned or public media storage, and verifying byte reductions.
4. **Exact SHA-256 Duplicate Engine**: Streams real file descriptors via `contentResolver.openInputStream(uri)` to compute streamed SHA-256 hashes on actual device files.

---

## 2. User Experience & Visual Design

### Permission Request & Scope State
- **Permission Banner**: When permissions are not yet granted, the Home screen displays an informative, non-intrusive card:
  - *"Grant gallery access to scan photos and videos on your phone, or pick specific files manually."*
  - Action 1: `[Grant Media Permission]` (launches system permission sheet).
  - Action 2: `[Select Files via Picker]` (zero-permission Photo Picker).
- **Accurate Scope Indication**:
  - Full Access: *"Scope: Scanning device gallery & accessible media"*
  - Partial / Denied: *"Scope: Selected files via Photo Picker"*
- **Empty State Diagnostics**:
  - Displays *"0 files found in device gallery"*, last scan timestamp, and direct buttons to launch Camera or Photo Picker.

### System Deletion Confirmation Flow
- When the user selects files for deletion in the Review, Duplicates, or Videos screens, Roomy generates an official `IntentSender` via `MediaStore.createDeleteRequest()`.
- Android renders the native system bottom sheet: *"Allow Roomy to delete 3 photos?"*.
- Upon user authorization, the OS deletes the files, and Roomy immediately refreshes its state without desynchronization.

---

## 3. Key Technical Decisions & Architecture

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                              ROOMY UI                                  │
│  ┌───────────────────────┐  ┌───────────────────────────────────────┐  │
│  │ Permission Handlers   │  │             ReviewScreen              │  │
│  │ (Runtime / PhotoPick) │  │  [Keep]  [Compress]  [OS Delete Prompt] │  │
│  └───────────┬───────────┘  └───────────────────┬───────────────────┘  │
└──────────────┼──────────────────────────────────┼──────────────────────┘
               ▼                                  ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        VIEWMODEL & DATA LAYER                          │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ RoomyViewModel: manages scan state, permission state, URIs       │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
│                                     ▼                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ RoomyRepository                                                  │  │
│  │  ├─ MediaStoreScanner (ContentResolver, Images & Video URIs)     │  │
│  │  ├─ DeletionConsentProvider (MediaStore.createDeleteRequest)     │  │
│  │  ├─ SHA-256 Streamer (contentResolver.openInputStream)           │  │
│  │  └─ OnDeviceCompressor (BitmapFactory hardware pipeline)         │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
└─────────────────────────────────────┼──────────────────────────────────┘
                                      ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        ANDROID SYSTEM FRAMEWORK                        │
│  ┌───────────────────────┐  ┌─────────────────┐  ┌──────────────────┐  │
│  │ Android MediaStore OS │  │ IntentSender OS │  │ StatFs Hardware  │  │
│  │ (Real Images & Videos)│  │ (Delete Dialog) │  │ (Real Disk Space)│  │
│  └───────────────────────┘  └─────────────────┘  └──────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### Component Details
1. **`AndroidManifest.xml`**:
   - Declare `READ_MEDIA_IMAGES`
   - Declare `READ_MEDIA_VIDEO`
   - Declare `READ_EXTERNAL_STORAGE` (with `android:maxSdkVersion="32"`)
2. **`RoomyRepository`**:
   - `scanMediaStore(context)`: Projection on `_ID`, `DISPLAY_NAME`, `SIZE`, `DATE_MODIFIED`, `WIDTH`, `HEIGHT`, `DURATION`, `MIME_TYPE`.
   - `buildDeleteRequest(context, uris)`: Calls `MediaStore.createDeleteRequest(contentResolver, uris)`.
   - `deleteFallback(context, uri)`: Direct deletion for app-owned files.
   - Streamed SHA-256 via `ContentResolver.openInputStream(uri)`.
3. **`MainActivity`**:
   - Registers `RequestMultiplePermissions` launcher.
   - Registers `StartIntentSenderForResult` launcher for system delete consent.
   - Dispatches deletion results back to `RoomyViewModel`.

---

## 4. Verification Plan

1. **Permissions Verification**:
   - Verify permissions are declared properly in `AndroidManifest.xml`.
   - Verify dynamic checking on startup without crashing on newer/older Android versions.
2. **Real ContentResolver Queries**:
   - Verify that `scanMediaStore` accurately queries external storage without generating synthetic files.
   - Verify handling when gallery contains 0 items (diagnostic empty state).
3. **Deletion Consent**:
   - Verify that deletion intent sender is created with valid URIs and dispatched to the Android system launcher.
4. **Compilation & Unit Tests**:
   - Run `compile_applet` and `gradle :app:testDebugUnitTest` to guarantee 100% build integrity.
