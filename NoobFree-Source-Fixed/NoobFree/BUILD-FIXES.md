# NoobFree build fixes

This patch addresses the Kotlin compiler errors reported by GitHub Actions in the supplied build log.

## Changes
1. `service/ShizukuUserService.kt`: changed the command allow-list regex to a Kotlin raw string so `\.` is a valid regex escape rather than an invalid Kotlin string escape.
2. `ui/AnimationScreen.kt`: cached the custom-getter `uniformScale` property in a local variable before the nullable comparison.
3. `ui/HomeScreen.kt`: applied the same local-variable fix for the `uniformScale` smart-cast error.
4. `ui/SettingsScreen.kt`: replaced illegal physical line breaks inside a regular Kotlin string with escaped newline characters (`\n`).

## Validation
The supplied GitHub Actions log identified these four source-level compiler problems. A local Gradle build could not be run in this environment because the project’s `gradlew` file delegates to a system `gradle` executable, and Gradle is not installed here. Re-run the GitHub Actions workflow to confirm the next build result. Any subsequent errors should be treated separately rather than assumed fixed.
