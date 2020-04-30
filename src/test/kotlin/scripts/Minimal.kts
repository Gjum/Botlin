@file:DependsOn("com.github.Gjum:Botlin:0.3.0-SNAPSHOT")

import com.github.gjum.minecraft.botlin.api.*

runBotScript("Botlin") {
	connect("localhost:25565")
	chat("Hello")
}
