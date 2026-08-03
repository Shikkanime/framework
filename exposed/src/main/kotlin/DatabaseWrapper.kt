package fr.shikkanime.exposed

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import liquibase.command.CommandScope
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Coordinates database access through HikariCP, Exposed, and Liquibase.
 *
 * Call [connect] before starting transactions, creating Exposed tables, or applying Liquibase
 * migrations. Transactions opened through [inTransaction] are reused by nested calls on the same
 * thread.
 *
 * @param jdbcUrl JDBC URL used by HikariCP and Liquibase.
 * @param driverClassName fully qualified name of the JDBC driver.
 * @param maximumPoolSize maximum number of connections retained by HikariCP.
 * @param isReadOnly whether connections obtained from the pool are read-only.
 * @param transactionIsolation JDBC transaction isolation level used by HikariCP.
 */
class DatabaseWrapper(
    private val jdbcUrl: String,
    private val driverClassName: String,
    private val maximumPoolSize: Int = 10,
    private val isReadOnly: Boolean = false,
    private val transactionIsolation: String = "TRANSACTION_SERIALIZABLE"
) {
    private val hikariConfig = HikariConfig().apply {
        this.jdbcUrl = this@DatabaseWrapper.jdbcUrl
        this.driverClassName = this@DatabaseWrapper.driverClassName
        this.maximumPoolSize = this@DatabaseWrapper.maximumPoolSize
        this.isReadOnly = this@DatabaseWrapper.isReadOnly
        this.transactionIsolation = this@DatabaseWrapper.transactionIsolation
    }
    private val threadLocal = ThreadLocal<JdbcTransaction>()
    private val tables = mutableListOf<Table>()

    /**
     * Executes [block] in an Exposed transaction.
     *
     * A transaction already associated with the current thread is reused. Otherwise, a new
     * transaction is opened, SQL logging is enabled, and failures trigger a rollback.
     *
     * @param block operation to execute with the current [JdbcTransaction] as receiver.
     * @return the value returned by [block].
     * @throws Exception when [block] fails; the original exception is propagated after rollback.
     */
    internal fun <T> inTransaction(block: JdbcTransaction.() -> T): T {
        if (threadLocal.get() != null) {
            return block(threadLocal.get()!!)
        }

        return transaction {
            threadLocal.set(this)
            addLogger(SqlOutCustomLogger)

            try {
                block()
            } catch (e: Exception) {
                rollback()
                throw e
            } finally {
                threadLocal.remove()
            }
        }
    }

    /**
     * Registers Exposed tables for subsequent schema initialization or removal.
     *
     * @param tables tables managed by [initializeSchema] and [dropSchema].
     * @return `true` when the collection of registered tables changed.
     */
    fun addTables(vararg tables: Table) =
        this.tables.addAll(tables)

    /**
     * Creates the HikariCP data source and registers it as an Exposed database.
     *
     * @return the connected Exposed [Database].
     */
    fun connect() =
        Database.connect(datasource = HikariDataSource(hikariConfig))

    /**
     * Creates all registered Exposed tables that do not already exist.
     *
     * The schema operation is protected by Exposed's database lock.
     */
    fun initializeSchema() =
        inTransaction { withDataBaseLock { SchemaUtils.create(*tables.toTypedArray()) } }

    /**
     * Applies pending Liquibase changesets from [changelogFile].
     *
     * Liquibase records applied changesets in its standard `DATABASECHANGELOG` table, making
     * repeated calls idempotent.
     *
     * @param changelogFile changelog location understood by Liquibase, such as a classpath XML file.
     * @throws Exception when Liquibase cannot load or apply the changelog.
     */
    fun migrateSchema(changelogFile: String) =
        inTransaction {
            withDataBaseLock {
                CommandScope("update")
                    .addArgumentValue("changeLogFile", changelogFile)
                    .addArgumentValue("url", jdbcUrl)
                    .addArgumentValue("username", hikariConfig.username)
                    .addArgumentValue("password", hikariConfig.password)
                    .execute()
            }
        }

    /**
     * Drops all registered Exposed tables.
     *
     * The schema operation is protected by Exposed's database lock.
     */
    fun dropSchema() =
        inTransaction { withDataBaseLock { SchemaUtils.drop(*tables.toTypedArray()) } }
}