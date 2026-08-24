# TripleSplitScreen

An AOSP-oriented, three-pane extension for WindowManager Shell (WMShell). It
manages three task stages (A, B, and C), divider interaction, task placement,
and split lifecycle. This is privileged platform code: it is not an Android SDK
library and cannot run inside an ordinary APK.

The repository deliberately contains only the reusable implementation and its
resources. It does **not** contain an app entry point, demo activity, package
identifiers for a product app, signing configuration, keystores, credentials,
or prebuilt framework JARs.

## Integrating with AOSP

1. Copy this repository into an AOSP checkout, for example under
   `packages/SystemUI/TripleSplitScreen`.
2. Add `"triple_splitscreen"` to the consuming WMShell module's `static_libs`
   or `libs`, according to whether the platform build should merge it or load
   it as a library.
3. Create a `SplitScreenController` from the existing WMShell dependencies and
   register it through your SystemUI/WMShell initialization path. The module
   does not register an Activity, Service, receiver, or Binder API itself.
4. Build the module with `m triple_splitscreen`. Soong writes the generated
   `classes.jar` below `out/soong/.intermediates/.../triple_splitscreen/`.

`Android.bp` enables `platform_apis` because the implementation uses hidden
window-management APIs and WMShell types. It targets Android 12/API 31 or
newer; exact WMShell APIs differ by Android release, so align imports and
constructor wiring with the branch being integrated.

## API guide

The public facade is `com.android.wm.shell.triplesplit.split.SplitScreen`, obtained
from `SplitScreenController.asSplitScreen()`.

| API | Use |
| --- | --- |
| `startTask(taskId, index, options)` | Starts a recent task into pane 1, 2, or 3. |
| `startIntent(pendingIntent, fillInIntent, index, options)` | Starts a pending intent in a selected pane. |
| `enterSplitScreen(taskId, index)` | Moves an existing task into the selected pane. |
| `exitSplitScreen(toTopTaskId)` | Dismisses the triple split and brings the requested task forward. |
| `goToFullscreenFromSplit()` | Leaves split mode and returns to fullscreen. |
| `moveTaskToFullscreen(taskId)` | Removes one task from split and promotes it. |
| `setSplitScreenFocus(index)` | Gives input focus to pane 1, 2, or 3. |
| `setStageDecorBitmap(index, bitmap)` | Supplies optional pane decoration content. |
| `registerSplitScreenListener(listener, executor)` | Receives stage, bounds, task, and visibility changes on the supplied executor. |

The pane indices are `SplitScreenConstants.SPLIT_INDEX_1`, `_2`, and `_3`;
they map to left, middle, and right in the default horizontal layout. Use
`SplitScreenConstants` snap values (for example,
`SNAP_TO_3_33_33_33`) when calling the controller-level `startTasks` or
`startIntents` APIs. Those controller APIs accept all three tasks at once and
are intended for WMShell integration code.

Example platform-side wiring:

```kotlin
val split: SplitScreen = controller.asSplitScreen()
split.registerSplitScreenListener(listener, mainExecutor)
split.enterSplitScreen(taskId, SplitScreenConstants.SPLIT_INDEX_2)
split.setSplitScreenFocus(SplitScreenConstants.SPLIT_INDEX_2)
```

All API calls must originate from privileged SystemUI/WMShell code with the
required task-management permissions. Task IDs and pending intents must refer
to tasks accessible to the current system user.

## License

Licensed under the Apache License, Version 2.0. This is suitable for Android
platform development and is compatible with AOSP licensing. See [LICENSE](LICENSE)
and [NOTICE](NOTICE).
