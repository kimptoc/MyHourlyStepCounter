# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**MyHourlyStepCounter** is an Android application built with Kotlin and Jetpack Compose. It's designed to track hourly step counts with a Material Design 3 interface featuring adaptive navigation.
This application should show the date time including minutes and seconds.
At the start of each hour, it should show zero steps taken
As the hour progress, it should show the number of steps taken in that hour. It should be update every 5 seconds with the latest step total for that hour.
It should show below the current hour steps the history of steps taken that day, for example at midday it would have totals for 11am, 10am , 9am etc listed on the screen.
Rename the Favorites tab to be history and show the history on there.

### Tech Stack
- **Language:** Kotlin 2.0.21
- **UI Framework:** Jetpack Compose with Material Design 3
- **Build System:** Gradle (Kotlin DSL)
- **Android SDK:** Target API 36, Min API 33
- **Java Version:** 11

## Build & Development Commands

### Build the App
```bash
./gradlew build           # Full build
./gradlew assembleDebug   # Debug APK
./gradlew assembleRelease # Release APK
```

### Run on Device/Emulator
```bash
./gradlew installDebug    # Install debug APK
./gradlew run             # Build and run
```

### Tests
```bash
./gradlew test                      # Run unit tests
./gradlew connectedAndroidTest      # Run instrumented tests
./gradlew test --info              # Run unit tests with details
```

### Linting & Code Quality
```bash
./gradlew lint            # Run Android lint
```

### Development Tasks
```bash
./gradlew clean           # Clean build artifacts
./gradlew build --watch   # Watch and rebuild on changes
```

## Project Structure

### Main Architecture

The app uses a simple single-activity architecture with Jetpack Compose:

- **MainActivity** (`app/src/main/java/com/example/myhourlystepcounter/MainActivity.kt`): Entry point containing the app's UI structure
- **Navigation:** Uses `NavigationSuiteScaffold` (Material Design 3 adaptive navigation) to handle three destinations: HOME, FAVORITES, PROFILE
- **State Management:** Navigation state is managed with `rememberSaveable` for persistence across configuration changes
- **Theme:** Custom Material Design 3 theme in `ui/theme/` package

### Directory Layout

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/example/myhourlystepcounter/
│   │   │   ├── MainActivity.kt          # Main UI composition & navigation
│   │   │   └── ui/theme/
│   │   │       ├── Color.kt
│   │   │       ├── Theme.kt
│   │   │       └── Type.kt
│   │   ├── res/                         # Android resources (strings, layouts, etc.)
│   │   └── AndroidManifest.xml
│   ├── test/                            # Unit tests
│   └── androidTest/                     # Instrumented tests
├── build.gradle.kts                     # App-level build configuration
└── proguard-rules.pro                   # ProGuard/R8 configuration

gradle/
└── libs.versions.toml                   # Centralized dependency version management

build.gradle.kts                         # Root build configuration
gradle.properties                        # Gradle system properties
settings.gradle.kts                      # Project settings & module definition
```

### Key Configuration Files

- **libs.versions.toml**: Centralized version catalog for all dependencies
- **gradle.properties**: JVM args set to 2048m for build performance
- **build.gradle.kts** (app level): Compose feature enabled, AndroidX enabled, non-transitive R class enabled

## Dependency Management

All dependencies are managed through `gradle/libs.versions.toml`. Update versions there, and reference them in `build.gradle.kts` using version refs like `libs.plugins.android.application`.

Key dependencies:
- Jetpack Compose UI (Material Design 3)
- Material 3 Adaptive Navigation Suite
- Lifecycle Runtime & Activity Compose
- JUnit 4, Espresso for testing

## Development Notes

### Compose Preview
The app includes multiple preview composables (with `@Preview` annotation) for iterating on UI in Android Studio without deploying to a device.

### Navigation State
Currently using simple state management with `rememberSaveable` for navigation. If the app grows, consider moving to Navigation Compose for more complex routing.

### Adaptive UI
The `NavigationSuiteScaffold` automatically adapts the navigation layout based on screen size (rail on larger screens, bottom bar on phones).

### Minimum SDK 33
The project targets Android 13+ (API 33), which provides modern Compose features and Material Design 3 support.
