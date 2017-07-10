package utilities

import java.time._
import java.util.{Date, TimeZone}

import com.google.inject.Singleton

@Singleton
class DateTimeUtility {

  val ISTZoneId = ZoneId.of("Asia/Kolkata")
  val ISTTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
  val ZoneOffset = ISTZoneId.getRules.getOffset(LocalDateTime.now(ISTZoneId))

  def nowMillis: Long =
    System.currentTimeMillis

  def startOfDayMillis: Long =
    localDateIST.atStartOfDay(ISTZoneId).toEpochSecond * 1000

  def localDateIST: LocalDate =
    LocalDate.now(ISTZoneId)

  def endOfDayMillis: Long =
    localDateIST.atTime(23, 59, 59).toEpochSecond(ZoneOffset) * 1000

  def toLocalDateTimeEndOfDay(date: Date): LocalDateTime =
    Instant.ofEpochMilli(date.getTime).atZone(ISTZoneId).toLocalDateTime.`with`(LocalTime.MAX)

  def toMillis(localDateTime: LocalDateTime): Long =
    localDateTime.toEpochSecond(ZoneOffset) * 1000

}
