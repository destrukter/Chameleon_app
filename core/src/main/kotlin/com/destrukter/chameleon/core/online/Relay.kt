package com.destrukter.chameleon.core.online

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import com.destrukter.chameleon.core.Rules
import java.io.Closeable
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Internet play without running a server: phones exchange the normal protocol lines through a free public
 * MQTT broker. The host's phone is still the only place where the game runs; the broker just forwards
 * messages. Everything is end-to-end encrypted (AES-GCM) with a key derived from the lobby code, and the
 * broker topic is a hash of the code, so neither the broker nor other users of it can read the game.
 */
object RelayCode {
    /** Public brokers, tried in order by the host. The first character of a code says which one it used. */
    val PUBLIC_BROKERS = listOf(
        "ssl://broker.emqx.io:8883",
        "ssl://broker.hivemq.com:8883",
        "ssl://test.mosquitto.org:8886",
    )

    const val LENGTH = 12
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    data class Parsed(val brokerIndex: Int, val secret: String)

    /** A new code: broker index, ten random characters, one check character. Formatted as XXXX-XXXX-XXXX. */
    fun generate(brokerIndex: Int, random: SecureRandom = SecureRandom()): String {
        val body = ALPHABET[brokerIndex].toString() + (1..10).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        return LobbyCode.format(body + check(body))
    }

    fun parse(input: String): Parsed? {
        val clean = LobbyCode.normalize(input)
        if (clean.length != LENGTH || clean.any { it !in ALPHABET }) return null
        val body = clean.dropLast(1)
        if (check(body) != clean.last()) return null
        return Parsed(ALPHABET.indexOf(body.first()), clean)
    }

    private fun check(body: String): Char {
        var sum = 0
        body.forEachIndexed { i, c -> sum += ALPHABET.indexOf(c) * (i * 2 + 1) }
        return ALPHABET[sum % ALPHABET.length]
    }
}

/** Topic names and end-to-end encryption for one lobby. */
class RelayChannel(secret: String) {
    private val key = SecretKeySpec(sha256("chameleon-key:$secret"), "AES")
    private val random = SecureRandom()

    /** Base topic; clients publish to `<topic>/up/<clientId>` and listen on `<topic>/down/<clientId>`. */
    val topic: String = "chameleon-game/v1/" + sha256("chameleon-topic:$secret").take(16).joinToString("") { "%02x".format(it) }

    fun upTopic(clientId: String) = "$topic/up/$clientId"
    fun downTopic(clientId: String) = "$topic/down/$clientId"

    fun seal(line: String): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        return nonce + cipher.doFinal(line.toByteArray(Charsets.UTF_8))
    }

    /** Returns null for anything not sealed with this lobby's key. */
    fun open(payload: ByteArray): String? = runCatching {
        if (payload.size <= NONCE_BYTES) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, payload.copyOfRange(0, NONCE_BYTES)))
        String(cipher.doFinal(payload, NONCE_BYTES, payload.size - NONCE_BYTES), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val NONCE_BYTES = 12
        fun sha256(text: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    }
}

private fun mqttOptions() = MqttConnectOptions().apply {
    isCleanSession = true
    isAutomaticReconnect = true
    keepAliveInterval = 30
    connectionTimeout = 10
    maxInflight = 1000
}

private fun randomClientId(): String {
    val bytes = ByteArray(8).also(SecureRandom()::nextBytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

private const val QOS = 1
private const val WAIT_MS = 12_000L

/**
 * The host's bridge between the relay and its [HostSession]. Each remote phone becomes one peer.
 * With a [publicListing] the lobby is also announced in the public lobby list until it is closed.
 */
class RelayHost private constructor(
    private val client: MqttAsyncClient,
    private val channel: RelayChannel,
    private val session: HostSession,
    /** The code to show to players, e.g. `E4KD-9QXM-27TB`. */
    val code: String,
    private val publicListing: PublicSetup?,
) : Closeable {

    /** Name and id of a lobby that is shown in the public list. */
    class PublicSetup(val lobbyName: String, val id: String = PublicLobbies.newId())

    val isPublic: Boolean get() = publicListing != null

    private class Remote(val peer: HostSession.Peer) {
        @Volatile var lastSeen = System.currentTimeMillis()
    }

    private val remotes = ConcurrentHashMap<String, Remote>()
    @Volatile private var closed = false
    private val monitor = Thread {
        // MQTT has no per-player connection to drop, so silence (no pings) counts as leaving.
        while (!closed) {
            try {
                Thread.sleep(2_000)
            } catch (_: InterruptedException) {
                break
            }
            val cutoff = System.currentTimeMillis() - Protocol.READ_TIMEOUT_MS
            remotes.entries.filter { it.value.lastSeen < cutoff }.forEach { (id, remote) ->
                if (remotes.remove(id, remote)) remote.peer.closed()
            }
        }
    }.apply { isDaemon = true }

    private fun start() {
        client.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                if (reconnect) runCatching { client.subscribe(channel.upTopic("+"), QOS) }
            }

            override fun connectionLost(cause: Throwable?) = Unit

            override fun messageArrived(topic: String, message: MqttMessage) {
                val clientId = topic.substringAfterLast('/')
                val line = channel.open(message.payload) ?: return
                val remote = remotes.computeIfAbsent(clientId) { Remote(session.connect(linkFor(it))) }
                remote.lastSeen = System.currentTimeMillis()
                remote.peer.receive(line)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })
        client.subscribe(channel.upTopic("+"), QOS).waitForCompletion(WAIT_MS)
        monitor.start()
        if (publicListing != null) announcer(publicListing).start()
    }

    /** Keeps the public listing current: on every change of players or phase, and periodically as a heartbeat. */
    private fun announcer(setup: PublicSetup) = Thread {
        var last: HostSession.Summary? = null
        var lastSent = 0L
        while (!closed) {
            val summary = session.summary.value
            val now = System.currentTimeMillis()
            if (summary.hostName != null && (summary != last || now - lastSent > PublicLobbies.REFRESH_MS)) {
                val listing = LobbyListing(
                    id = setup.id,
                    name = setup.lobbyName,
                    hostName = summary.hostName,
                    players = summary.players,
                    maxPlayers = Rules.MAX_ONLINE_PLAYERS,
                    inGame = summary.phase != Phase.LOBBY && summary.phase != Phase.RESULTS,
                    code = code,
                    updatedAt = now,
                )
                runCatching { client.publish(PublicLobbies.topic(setup.id), PublicLobbies.encode(listing), QOS, true) }
                    .onSuccess {
                        last = summary
                        lastSent = now
                    }
            }
            try {
                Thread.sleep(1_000)
            } catch (_: InterruptedException) {
                break
            }
        }
    }.apply { isDaemon = true }

    private fun linkFor(clientId: String) = object : PeerLink {
        override fun send(line: String) {
            try {
                client.publish(channel.downTopic(clientId), channel.seal(line), QOS, false)
            } catch (_: MqttException) {
                // The relay connection is down; the player times out or catches up with the next state.
            }
        }

        override fun close() {
            remotes.remove(clientId)
        }
    }

    override fun close() {
        closed = true
        monitor.interrupt()
        // An empty retained message removes the lobby from the public list.
        publicListing?.let { runCatching { client.publish(PublicLobbies.topic(it.id), ByteArray(0), QOS, true).waitForCompletion(2_000) } }
        remotes.values.forEach { it.peer.closed() }
        remotes.clear()
        runCatching { client.disconnect().waitForCompletion(2_000) }
        runCatching { client.close() }
    }

    companion object {
        /** Connects to the first reachable broker. Blocking; throws [IOException] when none works. */
        fun start(
            session: HostSession,
            brokers: List<String> = RelayCode.PUBLIC_BROKERS,
            publicListing: PublicSetup? = null,
        ): RelayHost {
            var lastError: Exception? = null
            brokers.forEachIndexed { index, uri ->
                val code = RelayCode.generate(index)
                val client = MqttAsyncClient(uri, "chm-host-" + randomClientId(), MemoryPersistence())
                val options = mqttOptions().apply {
                    // If the host's phone drops off without closing, the broker clears the listing for us.
                    publicListing?.let { setWill(PublicLobbies.topic(it.id), ByteArray(0), QOS, true) }
                }
                try {
                    client.connect(options).waitForCompletion(WAIT_MS)
                    return RelayHost(client, RelayChannel(RelayCode.parse(code)!!.secret), session, code, publicListing)
                        .also { it.start() }
                } catch (e: Exception) {
                    lastError = e
                    runCatching { client.close(true) }
                }
            }
            throw IOException("Could not reach an internet relay", lastError)
        }
    }
}

/** A player's connection to a lobby through the relay. */
class RelayClientTransport(code: String, private val brokers: List<String> = RelayCode.PUBLIC_BROKERS) : ClientTransport {
    private val parsed = requireNotNull(RelayCode.parse(code)) { "Not an internet lobby code" }
    private val channel = RelayChannel(parsed.secret)
    private val clientId = randomClientId()
    private val incoming = LinkedBlockingQueue<String>()
    private var client: MqttAsyncClient? = null

    override val unreachableMessage =
        "Could not find that lobby. Check the code, and that you and the host are both online."

    override fun connect() {
        val uri = brokers.getOrNull(parsed.brokerIndex) ?: throw IOException("Unknown relay in this code")
        val mqtt = MqttAsyncClient(uri, "chm-" + clientId, MemoryPersistence())
        client = mqtt
        mqtt.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                if (reconnect) runCatching { mqtt.subscribe(channel.downTopic(clientId), QOS) }
            }

            override fun connectionLost(cause: Throwable?) = Unit

            override fun messageArrived(topic: String, message: MqttMessage) {
                channel.open(message.payload)?.let { incoming.offer(it) }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })
        try {
            mqtt.connect(mqttOptions()).waitForCompletion(WAIT_MS)
            mqtt.subscribe(channel.downTopic(clientId), QOS).waitForCompletion(WAIT_MS)
        } catch (e: MqttException) {
            throw IOException("Could not reach the internet relay", e)
        }
    }

    override fun readLine(): String? {
        val line = incoming.poll(Protocol.READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            ?: throw SocketTimeoutException("No message from the host")
        return if (line === END) null else line
    }

    override fun writeLine(line: String) {
        val mqtt = client ?: throw IOException("Not connected")
        try {
            mqtt.publish(channel.upTopic(clientId), channel.seal(line), QOS, false)
        } catch (e: MqttException) {
            throw IOException("Relay connection lost", e)
        }
    }

    override fun close() {
        incoming.offer(END)
        val mqtt = client ?: return
        runCatching { mqtt.disconnect().waitForCompletion(2_000) }
        runCatching { mqtt.close() }
    }

    private companion object {
        /** Marks the end of the stream in [incoming]; compared by identity. */
        val END = String(charArrayOf('\u0000'))
    }
}
