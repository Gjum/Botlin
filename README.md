# Botlin

Command-line Minecraft client leveraging Kotlin [coroutines](https://kotlinlang.org/docs/reference/coroutines-overview.html) for automation, conforming with [CivClassic botting rules](https://www.reddit.com/r/civclassics/wiki/rules#wiki_botting)

## Installation

- Install git ([Windows: use git-bash as a terminal](https://gitforwindows.org/)) and Java
- Open a terminal and clone the repository: `git clone https://github.com/Gjum/Botlin.git`
- Enter the repository directory: `cd Botlin`
- Build the project binary: `./gradlew clean shadowJar`

## Usage

- Open a terminal, `cd` to your Botlin installation
- Provide your Minecraft password to the current terminal session: `export MINECRAFT_PASSWORD="replace this with your password"`
- Run the bot's command-line interface (CLI): `java -jar build/libs/botlin-*-all.jar`

To get started with the available commands, run `help`.

![Botlin example session](https://i.imgur.com/eJ2Iai2.png)

## License

Botlin is licensed under the **[MIT license](http://www.opensource.org/licenses/mit-license.html)**.
