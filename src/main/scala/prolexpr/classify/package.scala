package prolexpr

import org.apache.log4j.{Level, PatternLayout, ConsoleAppender, Logger}

/**
 *
 * @author Malcolm W. Greaves (greaves.malcolm@gmail.com)
 */
package object classify {
  def mkLogger(name: String, lvl: Level = Level.INFO) = {
    val log = Logger.getLogger(name)
    log.setLevel(lvl)
    log.addAppender(new ConsoleAppender(new PatternLayout("%d{ISO8601} %5p [%c{1}] %m%n")))
    log
  }
}