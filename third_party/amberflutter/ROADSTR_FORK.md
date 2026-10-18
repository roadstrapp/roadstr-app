# amberflutter — Roadstr fork

Vendored from [`amberflutter` 0.0.9](https://pub.dev/packages/amberflutter)
(MIT, Copyright (c) 2023 Sebastian Arriola — see `LICENSE`, unchanged).

## Why

The published plugin predates Android Gradle Plugin 9. Its Gradle script adds
the Kotlin Gradle Plugin to its classpath and applies both `kotlin-android` and
`org.jetbrains.kotlin.android`. AGP 9 provides Kotlin itself, so those explicit
applications prevent Roadstr from enabling Built-in Kotlin and will stop
working when Flutter removes the temporary opt-out.

## What changed

The runtime Dart and Kotlin sources are copied from 0.0.9. Build metadata has
the following compatibility changes:

1. the plugin-local AGP and KGP classpaths are removed;
2. `com.android.library` is still applied normally;
3. the literal `apply plugin: "kotlin-android"` is retained behind a check for
   `android.builtInKotlin=false`, preserving compatibility with the Flutter
   plugin detector and older host builds;
4. the Kotlin compiler target uses the modern `compilerOptions` API when KGP
   is active; AGP owns it in Built-in Kotlin builds.
5. the obsolete manifest `package` attribute is removed because `namespace`
   owns that value under current AGP versions, and the vendored pubspec omits
   upstream-only example/test metadata.

The Dart API, method channel, Android implementation and Amber protocol are
unchanged.

## Updating

Replace this directory with a newer upstream release, re-apply the conditional
Gradle integration only if upstream still lacks it, and run the complete
Flutter and Android build/test matrix. Remove the override as soon as a
published Amber release supports AGP 9 Built-in Kotlin.
