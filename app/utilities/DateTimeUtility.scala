package utilities

import java.time.{LocalDate, LocalDateTime, ZoneId}

object DateTimeUtility {

  val ISTZoneId = ZoneId.of("Asia/Calcutta")

  def localDateIST: LocalDate =
    LocalDate.now(ISTZoneId)

  def startOfDayMillis: Long =
    localDateIST.atStartOfDay(ISTZoneId).toEpochSecond * 1000

  def endOfDayMillis: Long = {
    val zoneOffset = ISTZoneId.getRules.getOffset(LocalDateTime.now(ISTZoneId))

    localDateIST.atTime(23, 59, 59).toEpochSecond(zoneOffset) * 1000
  }

}
