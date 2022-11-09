package no.nav.helse.sporbar

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties

class Environment(
    val raw: Map<String, String>,
    val db: DB
) {
    constructor(raw: Map<String, String>) : this(
        raw = raw,
        db = DB(
            name = raw.getValue("DATABASE_NAME"),
            host = raw.getValue("DATABASE_HOST"),
            port = raw.getValue("DATABASE_PORT").toInt(),
            vaultMountPath = raw.getValue("DATABASE_VAULT_MOUNT_PATH")
        )
    )

    class DB(
        val name: String,
        val host: String,
        val port: Int,
        val vaultMountPath: String
    )
}