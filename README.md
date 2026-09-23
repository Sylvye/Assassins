# Assassins

A GUI-first, server-wide Assassins event for Paper 26.2.

## Requirements

- Paper 26.2
- Java 25

## Build

```bash
./gradlew build
```

The deployable JAR is written to `build/libs/Assassins-1.0.0.jar`.

## Use

Run `/assassins` (aliases `/assassin` and `/assn`) to view your target, timer, progress, and claims. Use `/assassins rewards` to preview prizes and `/assassins settings` to open the administrator dashboard.

- `assassins.use` — participate and use the player GUI (default: everyone)
- `assassins.admin` — configure and control events (default: operators)
