package no.nav.helse.sporbar

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.containers.PostgreSQLContainer

internal fun PostgreSQLContainer<Nothing>.dataSource() = this.let { postgreSQLContainer ->
    HikariDataSource(HikariConfig().apply {
        jdbcUrl = postgreSQLContainer.jdbcUrl
        username = postgreSQLContainer.username
        password = postgreSQLContainer.password
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
        initializationFailTimeout = 5000
    })
}
