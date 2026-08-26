package com.indiewalkabout.nowdothis.feature.naturallanguage

internal data class RegisteredAlarm(
    val triggerAt: Long,
    val type: String,
    val packageName: String,
    val receiverComponent: String,
    val requestCode: Int
)

internal object AlarmRegistryEvidence {
    fun parse(
        alarmDump: String,
        pendingIntentDump: String,
        packageName: String
    ): List<RegisteredAlarm> {
        val pendingAlarms = pendingAlarms(alarmDump, packageName)
        if (pendingAlarms.isEmpty()) return emptyList()
        val pendingIntents = pendingIntents(pendingIntentDump, packageName)

        return pendingAlarms.mapNotNull { alarm ->
            val operation = pendingIntents[alarm.pendingIntentRecord] ?: return@mapNotNull null
            RegisteredAlarm(
                triggerAt = alarm.triggerAt,
                type = alarm.type,
                packageName = operation.packageName,
                receiverComponent = operation.receiverComponent,
                requestCode = operation.requestCode
            )
        }
    }

    private fun pendingAlarms(dump: String, packageName: String): List<PendingAlarm> {
        val pendingSection = dump.substringAfter(" pending alarms:", missingDelimiterValue = "")
            .substringBefore("\n  LazyAlarmStore stats:")
        if (pendingSection.isEmpty()) return emptyList()
        val packagePattern = Regex.escape(packageName)
        val headerPattern = Regex(
            "(?m)^[ \\t]+(RTC(?:_WAKEUP)?) #\\d+: " +
                "Alarm\\{\\S+ type \\d+ origWhen (-?\\d+) whenElapsed -?\\d+ " +
                "$packagePattern\\}[ \\t]*$"
        )
        val operationPattern = Regex(
            "PendingIntentRecord\\{(\\S+) $packagePattern(?:/\\S+)? broadcastIntent\\}"
        )
        val headers = headerPattern.findAll(pendingSection).toList()

        return headers.mapIndexedNotNull { index, match ->
            val blockEnd = headers.getOrNull(index + 1)?.range?.first ?: pendingSection.length
            val block = pendingSection.substring(match.range.first, blockEnd)
            val pendingIntentRecord = operationPattern.find(block)?.groupValues?.get(1)
                ?: return@mapIndexedNotNull null
            PendingAlarm(
                triggerAt = match.groupValues[2].toLong(),
                type = match.groupValues[1],
                pendingIntentRecord = pendingIntentRecord
            )
        }
    }

    private fun pendingIntents(
        dump: String,
        packageName: String
    ): Map<String, PendingIntentIdentity> {
        val packagePattern = Regex.escape(packageName)
        val headerPattern = Regex(
            "(?m)^[ \\t]+#\\d+: PendingIntentRecord\\{(\\S+) " +
                "$packagePattern(?:/\\S+)? broadcastIntent\\}[ \\t]*$"
        )
        val headers = headerPattern.findAll(dump).toList()

        return headers.mapIndexedNotNull { index, match ->
            val blockEnd = headers.getOrNull(index + 1)?.range?.first ?: dump.length
            val block = dump.substring(match.range.first, blockEnd)
            val owner = Regex("packageName=(\\S+)").find(block)?.groupValues?.get(1)
                ?: return@mapIndexedNotNull null
            val requestCode = Regex("requestCode=(-?\\d+)").find(block)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: return@mapIndexedNotNull null
            val component = Regex("requestIntent=.*?\\bcmp=(\\S+)").find(block)
                ?.groupValues
                ?.get(1)
                ?: return@mapIndexedNotNull null
            match.groupValues[1] to PendingIntentIdentity(
                packageName = owner,
                receiverComponent = component,
                requestCode = requestCode
            )
        }.toMap()
    }
}

private data class PendingAlarm(
    val triggerAt: Long,
    val type: String,
    val pendingIntentRecord: String
)

private data class PendingIntentIdentity(
    val packageName: String,
    val receiverComponent: String,
    val requestCode: Int
)
