package app.jittapan.passport

import com.zaxxer.hikari.HikariDataSource
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLoginEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class Passport: JavaPlugin(), Listener {

    private val SELECT_QUERY: String = "SELECT uuid FROM whitelist WHERE is_banned = 0 AND DATE(NOW()) <= playable_until"
    private val BAN_QUERY: String = "SELECT uuid FROM whitelist WHERE is_banned = 1"

    private var hikariDataSource: HikariDataSource = HikariDataSource()
    private var whitelist: List<UUID> = listOf<UUID>()
    private var banlist: List<UUID> = listOf<UUID>()

    companion object {
        var instance: Passport? = null
    }

    fun reloadPassportConfig(init: Boolean) {
        saveDefaultConfig()
        reloadConfig()
        if(!init && !hikariDataSource.isClosed)
            hikariDataSource.close()
        hikariDataSource = HikariDataSource()
        hikariDataSource.jdbcUrl = "jdbc:mysql://" + config.getString("sql.hostname") + ":" + config.getString("sql.port") + "/" + config.getString("sql.database") + "?useSSL=false"
        hikariDataSource.username = config.getString("sql.username")
        hikariDataSource.password = config.getString("sql.password")
        hikariDataSource.setMaxLifetime(600 * 1000)
    }

    fun createTable() {
        hikariDataSource.connection.use {
            val statement = it.createStatement()
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `whitelist` (" +
                    "`uuid` VARCHAR(36) NOT NULL DEFAULT '0'," +
                    "`playable_until` DATE NULL DEFAULT NULL," +
                    "`is_banned` TINYINT(1) NOT NULL DEFAULT '0'," +
                    "`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "`updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (`uuid`)" +
                    ")" +
                    "COLLATE='utf8mb4_general_ci'" +
                    "ENGINE=InnoDB" +
                    ";")
        }
    }

    override fun onEnable() {
        instance = this

        reloadPassportConfig(true)
        createTable()
        Bukkit.getScheduler().runTaskAsynchronously(this) {
            loadData()
        }
        val delayTicks: Long = 20L * config.getLong("whitelistRefreshRate")
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, {
            loadData()
        }, delayTicks, delayTicks)
        server.pluginManager.registerEvents(this, this)
    }

    override fun onDisable() {
        if(!hikariDataSource.isClosed)
            hikariDataSource.close()
    }

    fun loadData() {
        hikariDataSource.connection.use {
            val stmt = it.prepareStatement(SELECT_QUERY)
            val whitelistTmp = mutableListOf<UUID>()
            stmt.use {
                val result = stmt.executeQuery()
                result.use {
                    while (result.next()) {
                        val uuid = result.getString("uuid")
                        whitelistTmp.add(UUID.fromString(uuid))
                    }
                }
            }

            whitelist = whitelistTmp
        }
        hikariDataSource.connection.use {
            val stmt = it.prepareStatement(BAN_QUERY)
            val banlistTmp = mutableListOf<UUID>()
            stmt.use {
                val result = stmt.executeQuery()
                result.use {
                    while (result.next()) {
                        val uuid = result.getString("uuid")
                        banlistTmp.add(UUID.fromString(uuid))
                    }
                }
            }

            banlist = banlistTmp
        }
    }

    fun isWhitelisted(uuid: UUID): Boolean {
        return whitelist.contains(uuid)
    }

    fun isBanned(uuid: UUID): Boolean {
        return banlist.contains(uuid)
    }

    @EventHandler
    fun onPlayerLogin(event: PlayerLoginEvent) {
        if(isBanned(event.player.uniqueId)) {
            event.result = PlayerLoginEvent.Result.KICK_BANNED
            event.kickMessage = config.getString("message.banned")
        }
        else if(!isWhitelisted(event.player.uniqueId)) {
            event.result = PlayerLoginEvent.Result.KICK_WHITELIST
            event.kickMessage = config.getString("messages.noWhitelist")
        }
    }

    override fun onCommand(sender: CommandSender?, command: Command?, label: String?, args: Array<out String>?): Boolean {
        if(args == null)
            return false

        if(args.size >= 2) {
            when (args[0]) {
                "add" -> {

                }
                "remove" -> {

                }
            }
        } else {
            when (args[0]) {
                "reload" -> {
                    Bukkit.getScheduler().runTaskAsynchronously(this) {
                        loadData()
                    }
                    sender?.sendMessage("Reloaded whitelist cache.")
                }
            }
        }

        return false
    }
}
