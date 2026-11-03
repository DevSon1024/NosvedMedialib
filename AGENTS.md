# AI Agent Instructions for NosvedMedia Development

This document serves as the absolute source of truth for any AI agent or LLM assisting with the development of the **NosvedMedia** project. You must strictly adhere to these rules, architectural guidelines, development philosophies, and workflows before generating or modifying any code.

---

## 1. Core Development Philosophy

- **Goal:** NosvedMedia is an independent, high-performance Android media library designed to provide extended media decoding capabilities for Android applications, distributed as an Android AAR.
- **Technology Stack:**
  - Kotlin for Android APIs
  - C++17 for native components
  - Gradle Kotlin DSL
  - Android NDK and CMake
  - AndroidX Media3 as the playback framework
  - Target Architecture: Initial ABI focus on `arm64-v8a`
  - Minimum Android SDK: API 26
  - Distribution Format: Android AAR
- **Independence:** The project is maintained completely independently from Nosved Player Lite.
- **Flawless Execution:** The library and demo app MUST operate smoothly without memory leaks, native crashes, frame drops, or pipeline stalls.
- **Zero Crash Tolerance:** Prioritize robustness and safe error propagation across native and JVM layers. Never let unhandled C++ exceptions or unverified JNI return states crash the host application.
- **No Hallucinations:** Only use verified APIs, classes, dependencies, and resources. Never assume a file, class, dependency, or module exists—always verify first. To confirm adherence and avoid hallucinations, start every chat with the words **"Hey Devson"**.
- **Concise Communication:** Be short and precise. Do not explain things in long phrases or paragraphs if not required.

---

## 2. Mandatory Engineering Rules

1. **Inspect Before Modifying:** Inspect existing project files before modifying anything.
2. **Verify Existence:** Never assume a file, class, dependency, or module exists. Verify it first.
3. **Preserve Working Configurations:** Preserve existing working code, Gradle configs, and module setups.
4. **Gradle Version Stability:** Do not replace project-wide Gradle or AGP versions without an explicit technical reason.
5. **Media3 Version Consistency:** Keep all AndroidX Media3 dependencies locked to one single, consistent version across all modules.
6. **Official Upstream Sources:** Use official upstream sources for dependencies, toolchains, and source distributions.
7. **No Random Precompiled Binaries:** Do not download random precompiled native `.so` libraries.
8. **Reproducible Native Builds:** Build native libraries reproducibly from audited source code.
9. **License Boundary Integrity:** Do not copy GPL-licensed implementation code into proprietary components. Respect LGPL dynamic linking requirements.
10. **License Inventory:** Maintain an accurate third-party license inventory (`LICENSES.md`).
11. **Minimal Dependencies:** Avoid unnecessary dependencies and duplicate implementations.
12. **Robust Error Handling:** Use proper Kotlin, JNI, and C++ error handling. Check every JNI boundary call and clear/throw exceptions appropriately.
13. **No Raw Pointer Leakage:** Never expose raw native pointers directly to application/JVM code. Wrap native handles in opaque, type-safe structures with strict lifecycle management.
14. **Thread Safety & Non-blocking:** Do not block the Android main thread with native decoding, parsing, or I/O operations.
15. **Verify Before Claiming:** Do not claim that a feature or fix works until it has been built and tested.

---

## 3. Code Quality, JNI & Native Guidelines

- **Language Standards:** Kotlin for JVM code; modern C++17 for native components.
- **Resource Management (RAII):** All native C++ resources (codecs, packet buffers, frame contexts) must use RAII, smart pointers (`std::unique_ptr`, `std::shared_ptr`), or custom deleters to prevent memory leaks.
- **JNI Best Practices:**
  - Cache `jclass`, `jmethodID`, and `jfieldID` lookups in `JNI_OnLoad`.
  - Always check for pending JNI exceptions (`env->ExceptionCheck()`) after invoking JVM callbacks.
  - Release all local and global references (`ReleaseByteArrayElements`, `DeleteLocalRef`) promptly.
- **Null Safety & Kotlin Conventions:**
  - Handle nullable types safely and exhaustively.
  - **Never** use the not-null assertion operator (`!!`).
- **Formatting Restriction:** Do not add the box-drawing character `'─'` anywhere in code files.

---

## 4. Documentation & Update Tracking

You must actively maintain the project changelog. After every completed task, error resolution, or feature addition, append an entry to `update_details.md`.

### Format and Rules for `update_details.md`:
- Do NOT rewrite or overwrite the entire file; always append new entries at the very end.
- Include a Date and Time stamp for each update.
- Document every change using this exact schema:

```markdown
### [YYYY-MM-DD HH:MM:SS]
- **Issue:** (Briefly describe the exact issue, task, or feature)
- **Type:** (Error | Bug | Native | Media3 | Architecture | Performance | Feature)
- **Solution:** (Explain how the issue was solved. Maximum 10 lines.)
---
```
- Append exactly `---` on a new line to close out that specific session.
- Do not include conversational filler in `update_details.md`.

---

## 5. Agent Workflow & Phase Execution

For each task:
1. **Inspect** relevant project files.
2. **Explain** the current architecture.
3. **Identify** files that need modification or creation.
4. **Propose** the implementation plan.
5. **Implement** only the requested phase. Do not proceed automatically to another phase.
6. **Run** relevant Gradle, CMake, and test commands.
7. **Fix** any errors introduced by changes.
8. **Report** completed work, verification results, and remaining limitations.

### Required Final Report Format
At the end of each phase, provide:
- **Files created**
- **Files modified**
- **Dependencies added**
- **Architecture changes**
- **Build commands executed**
- **Actual build/test results**
- **Known issues**
- **Licensing implications**
- **Remaining work**
- **Exact instructions for starting the next phase**

*Never fabricate build or test results.*

---

## 6. Version Control (Git) Protocol

- **No Unauthorized Commits/Pushes:** Do not commit or push any changes to the Git repository until explicitly instructed to do so by the developer.
- **Protect `.gitignore`:** Never modify any `.gitignore` file automatically. All `.gitignore` updates are managed manually by the developer.

