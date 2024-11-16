package no.nav.helse.sporbar

import com.github.navikt.tbd_libs.test_support.CleanupStrategy
import com.github.navikt.tbd_libs.test_support.DatabaseContainers
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer

private val cleanupStrategy = CleanupStrategy.tables("dokument, flyway_schema_history, hendelse, hendelse_dokument, oppdrag, utbetaling, vedtak, vedtak_hendelse, vedtak_tilstand, vedtaksperiode, vedtaksperiode_hendelse")
val databaseContainer = DatabaseContainers.container("sporbar", cleanupStrategy)