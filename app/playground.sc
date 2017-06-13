import java.text.SimpleDateFormat
import java.util.Date

val date = new SimpleDateFormat("yyyy-MM-dd").parse("1111-11-11")
val da = new SimpleDateFormat("yyyy-MM-dd").format(date)

val date1 = new Date(System.currentTimeMillis())
val simpleDate = new SimpleDateFormat("yyyy-MM-dd").format(date1)

val