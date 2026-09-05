package mihon.desktop.history

import mihon.desktop.library.model.HistoryWithDetails
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class DesktopHistoryGroup(
    val title: String,
    val items: List<HistoryWithDetails>,
)

object HistoryGrouper {
    fun group(
        items: List<HistoryWithDetails>,
        nowEpochMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<DesktopHistoryGroup> {
        if (items.isEmpty()) return emptyList()

        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
        val yesterday = today.minusDays(1)
        val pastWeek = today.minusDays(7)
        val pastMonth = today.minusDays(30)

        val todayList = mutableListOf<HistoryWithDetails>()
        val yesterdayList = mutableListOf<HistoryWithDetails>()
        val pastWeekList = mutableListOf<HistoryWithDetails>()
        val pastMonthList = mutableListOf<HistoryWithDetails>()
        val olderList = mutableListOf<HistoryWithDetails>()

        for (item in items) {
            val itemDate = Instant.ofEpochMilli(item.lastRead).atZone(zoneId).toLocalDate()
            when {
                itemDate == today -> todayList.add(item)
                itemDate == yesterday -> yesterdayList.add(item)
                itemDate.isAfter(pastWeek) -> pastWeekList.add(item)
                itemDate.isAfter(pastMonth) -> pastMonthList.add(item)
                else -> olderList.add(item)
            }
        }

        return buildList {
            if (todayList.isNotEmpty()) add(DesktopHistoryGroup("Today", todayList))
            if (yesterdayList.isNotEmpty()) add(DesktopHistoryGroup("Yesterday", yesterdayList))
            if (pastWeekList.isNotEmpty()) add(DesktopHistoryGroup("Earlier this week", pastWeekList))
            if (pastMonthList.isNotEmpty()) add(DesktopHistoryGroup("This month", pastMonthList))
            if (olderList.isNotEmpty()) add(DesktopHistoryGroup("Older", olderList))
        }
    }
}
