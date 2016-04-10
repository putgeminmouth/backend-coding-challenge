package util

import java.sql.{Connection, DriverManager}
import java.text.Normalizer

import play.api.Logger

package object pattern {
    // https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html
    def using[A <: AutoCloseable, B](ac: A)(block: A => B) = {
        val ret = try {
            block(ac)
        } catch {
            case t: Throwable =>
                try {
                    ac.close()
                } catch {
                    case suppressed: Throwable =>
                        t.addSuppressed(suppressed)
                }
                throw t
        }
        ac.close()
        ret
    }
}

package object text {
    /**
      * Strip accents (e.g. 'é' becomes 'e')
      * Convert to lowercase
      */
    def normalize(src: String) =
    // http://stackoverflow.com/questions/3322152/is-there-a-way-to-get-rid-of-accents-and-convert-a-whole-string-to-regular-lette
        Normalizer.normalize(src, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "")
            .toLowerCase
}

package object db {
    import pattern._

    def usingNewConnection(block: Connection => Unit) {
        val url = sys.env("JDBC_DATABASE_URL")
        Logger.info(s"Database Url: $url")
        val conn = DriverManager.getConnection(url)

        using(conn) {

            block
        }
    }
}