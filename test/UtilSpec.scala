import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, MustMatchers, WordSpec}

import scala.util.Try

class UtilSpec extends WordSpec
    with MustMatchers
    with BeforeAndAfter
    with MockitoSugar
{
    "using" should {
        class BlockException extends Exception
        class CloseException extends Exception

        import util.pattern.using
        "execute the code block" in {
            var wasExecuted = false
            val resource = mock[AutoCloseable]
            using(resource) { r =>
                r must be(resource)
                verify(r, times(0)).close()

                wasExecuted = true
            }

            wasExecuted must equal(true)
        }
        "close the resource on success" in {
            val resource = mock[AutoCloseable]
            using(resource)(identity)

            verify(resource, times(1)).close()
        }
        "close the resource on failure" in {
            val resource = mock[AutoCloseable]
            Try(using(resource) { _ => sys.error("") })

            verify(resource, times(1)).close()
        }
        "let block failures through" in {
            val resource = mock[AutoCloseable]
            intercept[Exception] {
                using(resource) { _ => sys.error("") }
            }
        }
        "let close failures through" in {
            val resource = mock[AutoCloseable]
            when(resource.close()) thenThrow new CloseException

            intercept[CloseException] {
                using(resource)(identity)
            }
        }
        "suppress close failures when block fails too" in {
            val expectedSuppressed = new CloseException

            val resource = mock[AutoCloseable]
            when(resource.close()) thenThrow(expectedSuppressed)

            intercept[BlockException] {
                using(resource) { _ => throw new BlockException }
            }.getSuppressed must equal(Array(expectedSuppressed))
        }
    }

    "normalize" should {
        import util.text.normalize
        "remove accent characters" in {
            normalize("äēîôù") must equal("aeiou")
        }
        "lowercase" in {
            normalize("ÂBCD") must equal("abcd")
        }
    }
}
