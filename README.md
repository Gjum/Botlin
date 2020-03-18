# Botlin

Command-line Minecraft client leveraging Kotlin [coroutines](https://kotlinlang.org/docs/reference/coroutines-overview.html) for automation

## Installation

- On Windows, ([use git-bash as a terminal](https://gitforwindows.org/))
- Download [the code](https://github.com/Gjum/Botlin/archive/master.zip) and extract it somewhere ("Botlin" directory)
- Enter the repository directory: `cd Botlin`
- Build the project binary: `./gradlew clean shadowJar`

## Usage

- Open a terminal, `cd` to your Botlin installation
- Create a file `.credentials` in the same directory, containing email and password separated by space: `your-email@example.com my pas$word`
    You can add multiple accounts, one per line.
- Run the bot's command-line interface (CLI), specifying username and server address: `java -jar build/libs/botlin-0.2.0-SNAPSHOT-all.jar your-email@example.com mc.server-address.example.com:12345`

To get started with the commands available in the CLI, type `help`.

![Botlin example session](https://i.imgur.com/eJ2Iai2.png)

[![Build Status](https://travis-ci.org/Gjum/Botlin.svg?branch=master)](https://travis-ci.org/Gjum/Botlin)
