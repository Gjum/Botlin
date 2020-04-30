@file:DependsOn("com.github.Gjum:Botlin:0.3.0-SNAPSHOT")

import com.github.gjum.minecraft.botlin.api.*
import kotlinx.coroutines.delay

runBotScript("Botlin") {
	connect("localhost:25565")
	chat("Hello")
	delay(3000)
	chat("Bye!")
}
