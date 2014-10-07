package prolexpr.classify

import scala.concurrent.Lock


/**
 * y
 * @author Malcolm W. Greaves (greaves.malcolm@gmail.com)
 */
object Sync {
  val log = mkLogger(this.getClass.toString)

  class WaitGroup {
    val l = new Lock()
    private[this] var i: Int = 0

    def add(): Int = {
      var current = -1
      l.acquire()
      i += 1
      current = i
      l.release()
      current
    }

    def done(): Int = {
      var current = -1
      l.acquire()
      i -= 1
      current = i
      l.release()
      current
    }

    def waitUntilCompletion(ms: Int = 2000) = {
      var continue = true
      while (continue) {
        Thread.sleep(ms)
        l.acquire()
        if (i <= 0) {
          i = 0
          continue = false
        }
        l.release()
      }
    }

    def waitUntilSizeIsLessThan(n: Int, ms: Int = 2000) = {
      var current = 0
      do {
        l.acquire()
        current = i
        l.release()
        if (current >= n) {
          Thread.sleep(ms)
        }
      } while (current >= n)
    }
  }

}