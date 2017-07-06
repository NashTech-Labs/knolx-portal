package utilities

import java.time.{LocalDate, LocalDateTime, ZoneId}

import com.google.inject.Singleton

@Singleton
class DateTimeUtility {

  val ISTZoneId = ZoneId.of("Asia/Calcutta")

  def nowMillis: Long =
    System.currentTimeMillis

  def startOfDayMillis: Long =
    localDateIST.atStartOfDay(ISTZoneId).toEpochSecond * 1000

  def localDateIST: LocalDate =
    LocalDate.now(ISTZoneId)

  def endOfDayMillis: Long = {
    val zoneOffset = ISTZoneId.getRules.getOffset(LocalDateTime.now(ISTZoneId))

    localDateIST.atTime(23, 59, 59).toEpochSecond(zoneOffset) * 1000
  }

}
