package com.destrukter.chameleon.core.online

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * An entry of the public lobby list. Public lobbies publish one of these as a retained message on the relay,
 * so anyone opening the browser sees them straight away. It carries the internet code, so anybody can join –
 * private lobbies simply never publish one.
 */
@Serializable
data class LobbyListing(
    val id: String,
    val name: String,
    val hostName: String,
    val players: Int,
    val maxPlayers: Int,
    val inGame: Boolean,
    val code: String,
    /** Host clock when published; listings that stopped being refreshed disappear. */
    val updatedAt: Long,
    val version: Int = Protocol.VERSION,
)

object PublicLobbies {
    const val TOPIC_ROOT = "chameleon-game/v1/lobbies"
    const val REFRESH_MS = 20_000L
    /** Hide listings not refreshed for this long (the host's phone died or lost its connection). */
    const val STALE_MS = 75_000L
    const val MAX_NAME_LENGTH = 30

    internal val json = Json { ignoreUnknownKeys = true }

    fun topic(id: String) = "$TOPIC_ROOT/$id"

    fun newId(): String = ByteArray(8).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }

    fun encode(listing: LobbyListing): ByteArray = json.encodeToString(LobbyListing.serializer(), listing).toByteArray()

    fun decode(payload: ByteArray): LobbyListing? = runCatching {
        json.decodeFromString(LobbyListing.serializer(), String(payload, Charsets.UTF_8))
    }.getOrNull()?.takeIf { it.isPlausible() }

    /** Listings come from a public broker anyone can write to, so only accept sane ones. */
    private fun LobbyListing.isPlausible(): Boolean =
        name.isNotBlank() && name.length <= MAX_NAME_LENGTH * 2 && hostName.length <= Protocol.MAX_NAME_LENGTH * 2 &&
            players in 0..100 && maxPlayers in 1..100 && RelayCode.parse(code) != null
}

/**
 * Shows the public lobbies of every relay broker. Listens on all brokers at once because each host announces
 * on the broker it happened to connect to.
 */
class LobbyBrowser(
    private val brokers: List<String> = RelayCode.PUBLIC_BROKERS,
    private val clock: () -> Long = System::currentTimeMillis,
) : Closeable {

    private data class Seen(val listing: LobbyListing, val receivedAt: Long)

    private val seen = ConcurrentHashMap<String, Seen>()
    private val clients = mutableListOf<MqttAsyncClient>()
    private val _lobbies = MutableStateFlow<List<LobbyListing>>(emptyList())
    private val _connected = MutableStateFlow(0)
    @Volatile private var closed = false

    /** Joinable public lobbies, newest first. */
    val lobbies: StateFlow<List<LobbyListing>> = _lobbies.asStateFlow()

    /** How many relay brokers the browser reached; 0 after a while means the phone is offline. */
    val connectedBrokers: StateFlow<Int> = _connected.asStateFlow()

    fun start() {
        brokers.forEach { uri ->
            thread(isDaemon = true, name = "lobby-browser") { listen(uri) }
        }
        thread(isDaemon = true, name = "lobby-browser-expiry") {
            while (!closed) {
                publish()
                try {
                    Thread.sleep(5_000)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun listen(uri: String) {
        val client = MqttAsyncClient(uri, "chm-browse-" + PublicLobbies.newId(), MemoryPersistence())
        synchronized(clients) {
            if (closed) return
            clients += client
        }
        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                if (reconnect) runCatching { client.subscribe(PublicLobbies.TOPIC_ROOT + "/+", 1) }
            }

            override fun connectionLost(cause: Throwable?) = Unit

            override fun messageArrived(topic: String, message: MqttMessage) {
                val id = topic.substringAfterLast('/')
                val listing = PublicLobbies.decode(message.payload)
                if (listing == null || listing.id != id) seen.remove(id) else seen[id] = Seen(listing, clock())
                publish()
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })
        try {
            client.connect(MqttConnectOptions().apply {
                isCleanSession = true
                isAutomaticReconnect = true
                connectionTimeout = 10
                keepAliveInterval = 30
            }).waitForCompletion(15_000)
            client.subscribe(PublicLobbies.TOPIC_ROOT + "/+", 1).waitForCompletion(15_000)
            _connected.value += 1
        } catch (_: Exception) {
            // This broker is unreachable; the others may still work.
        }
    }

    private fun publish() {
        val now = clock()
        _lobbies.value = seen.values
            .filter { it.listing.version == Protocol.VERSION }
            // Retained messages from a host that vanished without clearing them are caught by their age.
            .filter { now - it.receivedAt < PublicLobbies.STALE_MS && now - it.listing.updatedAt < PublicLobbies.STALE_MS * 4 }
            .map { it.listing }
            .sortedByDescending { it.updatedAt }
    }

    override fun close() {
        closed = true
        val toClose = synchronized(clients) { clients.toList().also { clients.clear() } }
        thread(isDaemon = true) {
            toClose.forEach {
                runCatching { it.disconnect().waitForCompletion(2_000) }
                runCatching { it.close() }
            }
        }
    }
}
