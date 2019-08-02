# Botlin

Command-line Minecraft client leveraging Kotlin [coroutines](https://kotlinlang.org/docs/reference/coroutines-overview.html) for automation, conforming with [CivClassic botting rules](https://www.reddit.com/r/civclassics/wiki/rules#wiki_botting)

## Installation

- On Windows, ([use git-bash as a terminal](https://gitforwindows.org/))
- Download [the code](https://github.com/Gjum/Botlin/archive/master.zip) and extract it somewhere ("Botlin" directory)
- Enter the repository directory: `cd Botlin`
- Build the project binary: `./gradlew clean shadowJar`

## Usage

- Open a terminal, `cd` to your Botlin installation
- Create a file `.credentials` in the same directory, containing email and password separated by space: `your-email@example.com my pas$word`
    You can add multiple accounts, one per line.
- Run the bot's command-line interface (CLI), specifying username and server address: `java -jar build/libs/botlin-0.1.1-SNAPSHOT-all.jar your-email@example.com mc.server-address.example.com:12345`

To get started with the commands available in the CLI, type `help`.

![Botlin example session](https://i.imgur.com/eJ2Iai2.png)

## License

Botlin is licensed under the **[GNU General Public License version 3](https://www.gnu.org/licenses/gpl-3.0.html)**.

Copyright (C) 2019  Gjum

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
