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
- Run the bot's command-line interface (CLI): `java -jar build/libs/botlin-0.1.1-SNAPSHOT-all.jar your-email@example.com mc.server-address.example.com`

To get started with the available commands, run `help`.

![Botlin example session](https://i.imgur.com/eJ2Iai2.png)

## License

Botlin is licensed under the **[MIT license](http://www.opensource.org/licenses/mit-license.html)**.
