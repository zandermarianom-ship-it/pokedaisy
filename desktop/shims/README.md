The desktop's copy of `ui-preview/src/shims`: the Android pieces the app's sources use, with real behaviour where
the Library and Settings need it on a desktop - an activity is a window (`desktop/DesktopHost.kt`), `startActivity`
/ `finish` open and close them, `registerForActivityResult` pickers are the system's file / folder dialogs
(`desktop/DesktopPickers.kt`, which hands out `file:` URIs that `ContentResolver`, `RomUris` and
`DocumentsContract` read back), `Toast` shows a short notice, links open in the browser. Keep the rest in step with
ui-preview's when its shims change.
