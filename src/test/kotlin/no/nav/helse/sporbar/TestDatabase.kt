package no.nav.helse.sporbar

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer

object TestDatabase {
    private val postgres = PostgreSQLContainer<Nothing>("postgres:13").also { it.start() }

    val dataSource by lazy {
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            maximumPoolSize = 3
            minimumIdle = 1
            idleTimeout = 10001
            connectionTimeout = 1000
            maxLifetime = 30001
        })
    }

    init {
        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()

        Runtime.getRuntime().addShutdownHook(Thread {
            dataSource.close()
        })
    }
}
