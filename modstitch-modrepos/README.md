# MTK: `modrepos`

A Gradle plugin to provide shorthand functions to popular modding maven repositories.

## Usage

```kotlin
plugins {
    id("dev.isxander.mtk.modrepos") version "1.0.0"
}

repositories {
    minecraft()
    terraformersMC()
    terraformersMCSnapshots()
    modrinthApi.exclusive()
    curseMaven.exclusive()
    isxander()
    nucleoid()
    nucleoidSnapshots()
    fabricMC()
    neoForged()
    caffeineMC()
    caffeineMCSnapshots()
    kikugie()
    kikugieSnapshots()
    gegy()
    parchment()
}
```
