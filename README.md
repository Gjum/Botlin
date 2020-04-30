# Botlin [![Minecraft 1.12.2](https://img.shields.io/badge/MC-1.12.2-brightgreen.svg)](https://github.com/Gjum/Botlin) [![Travis build status](https://travis-ci.org/Gjum/Botlin.svg?branch=master)](https://travis-ci.com/Gjum/Botlin) [![JCenter/Bintray latest version](https://api.bintray.com/packages/gjum/minecraft/Botlin/images/download.svg)](https://bintray.com/gjum/minecraft/Botlin/_latestVersion#files) [![JitPack latest version](https://jitpack.io/v/Gjum/Botlin.svg)](https://jitpack.io/#Gjum/Botlin)

Command-line Minecraft client and scripting environment

![Botlin example session](https://i.imgur.com/eJ2Iai2.png)

## Command-line client

- Download [the latest Botlin-...-full.jar](https://github.com/Gjum/Botlin/releases/latest)
- Create a file `.credentials` in a folder of your choice (e.g., where you keep all your bot scripts), containing email and password separated by space: `your-email@example.com my pas$word`
    You can add multiple accounts, one per line.
- Open a terminal and `cd` to that folder containing the `.credentials` file
- Start the bot, specifying username and server address: `java -jar path/to/Botlin-0.2.1-full.jar your-email@example.com mc.server-address.example.com:12345`

To get started with the commands available in the CLI, type `help`.

## Scripting

This may become easier [in the future](https://github.com/Gjum/Botlin/issues/10),
but for now, install [kscript](https://github.com/holgerbrandl/kscript#installation).

Then write this code in `bot.kts` and run it with `kscript bot.kts`:

```kotlin
@file:DependsOn("com.github.Gjum:Botlin:0.3.0")
import com.github.gjum.minecraft.botlin.api.*
import kotlinx.coroutines.delay

runBotScript("your-mojang-account@example.com") {
    connect("mc-server-address.example.com:25565")
    chat("Hello!")
    delay(3000)
    chat("Bye!")
}
```

For more examples, see [`src/test/kotlin/scripts/`](https://github.com/Gjum/Botlin/blob/master/src/test/kotlin/scripts/).

## Building the JAR

- Install a Java Development Kit (JDK)
- On Windows, [use git-bash as a terminal](https://gitforwindows.org/) (or Cygwin or anything else that has basic command line tools).
- Download [the code](https://github.com/Gjum/Botlin/archive/master.zip) and extract it somewhere ("Botlin" directory)
- Enter the repository directory: `cd Botlin`
- Build the full JAR: `./gradlew clean shadowJar`
