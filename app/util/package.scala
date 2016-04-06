package util

import java.text.Normalizer

package object pattern {
    def using[A <: AutoCloseable, B](ac: A)(block: A => B) = {
        try {
            block(ac)
        } finally {
            ac.close()
        }
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
