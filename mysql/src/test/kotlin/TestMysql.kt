import mfwgenerics.kotq.mysql.MysqlDialect
import mfwgenerics.kotq.jdbc.ConnectionWithDialect
import mfwgenerics.kotq.test.TestDatabase
import java.sql.DriverManager
import kotlin.test.Test

class TestMysql: BaseTest() {
    override fun connect(db: String): TestDatabase {
        val outerCxn = DriverManager.getConnection("jdbc:mysql://localhost:3306/","root","my-secret-pw")

        outerCxn.prepareStatement("CREATE DATABASE $db").execute()

        return object : TestDatabase {
            override val cxn: ConnectionWithDialect = ConnectionWithDialect(
                MysqlDialect(),
                DriverManager.getConnection("jdbc:mysql://localhost:3306/$db", "root", "my-secret-pw")
            )

            override fun drop() {
                cxn.jdbc.close()
                outerCxn.prepareStatement("DROP DATABASE $db").execute()
            }
        }
    }

    @Test
    fun `select version`() = withCxn { cxn ->
        val rs = cxn
            .jdbc
            .prepareStatement("SELECT VERSION()")
            .executeQuery()

        while (rs.next()) {
            println(rs.getString(1))
        }
    }
}