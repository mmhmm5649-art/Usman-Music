# USMAN Music Player - Echo Extension Runtime Limitations

USMAN implements a genuine Echo Extension (.eapk) compatibility layer using standard Android `DexClassLoader` and interface bridging. While this allows running original Echo extensions without modification, there are several architectural limitations to be aware of:

## 1. Process Isolation
* **Same-Process Execution**: Extensions run in the same process as the USMAN Music Player app. There is no process-level isolation between the player and the extension code.
* **Shared Memory**: Extensions can theoretically access and modify internal app state if they use reflection or memory-scanning techniques.
* **Crashes**: An unhandled exception or native crash in an extension can terminate the entire USMAN process.

## 2. Permission Sharing
* **Shared Permissions**: Extensions inherit all permissions granted to USMAN (Internet, Storage, Notifications, etc.). USMAN does not support per-extension permission sandboxing.
* **User Trust**: Because extensions run with the same privileges as the main app, users are presented with a **Security Warning** before installing any genuine Echo extension.

## 3. Dependency Management
* **Class Conflicts**: Since extensions run in the same process, dependency version conflicts (e.g., different versions of OkHttp or Kotlin Coroutines) may occur if not properly managed by the `ClassLoader` hierarchy.
* **USMAN SDK Stubs**: Extensions depend on the `dev.brahmkshatriya.echo.common` interfaces. USMAN provides compatible stubs, but newer SDK features from the official Echo player may not be immediately available.

## 4. Resource Usage
* **Memory Leakage**: Extensions that don't properly clean up resources (Coroutines, HTTP connections, Listeners) can lead to memory leaks in the main process.
* **Lifecycle**: While USMAN attempts to trigger Echo's `onExtensionSelected` lifecycle, complex lifecycle events tied to the official Echo app's internal architecture may not be perfectly replicated.

## 5. Security & Safety
* **No Code Review**: USMAN does not perform static analysis or code review of DEX-loaded binaries. 
* **Trust Requirement**: Users should only install extensions from trusted authors or official USMAN/Bloomee repositories.

---
*USMAN Phase 6 - Echo Compatibility Verification Complete.*
