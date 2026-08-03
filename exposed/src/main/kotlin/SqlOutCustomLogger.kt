package fr.shikkanime.exposed

import fr.shikkanime.core.LoggerFactory
import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.expandArgs

/**
 * Exposed SQL logger that delegates expanded SQL statements to the framework logger.
 *
 * Statements are logged under the `org.jetbrains.exposed.SQL` category at the `CONFIG` level.
 */
internal object SqlOutCustomLogger : SqlLogger {
    private val logger = LoggerFactory.getLogger("org.jetbrains.exposed.SQL")

    /**
     * Logs the SQL represented by [context] after replacing placeholders with transaction arguments.
     *
     * @param context statement and arguments being executed.
     * @param transaction Exposed transaction executing the statement.
     */
    override fun log(context: StatementContext, transaction: Transaction) {
        logger.config(context.expandArgs(transaction))
    }
}