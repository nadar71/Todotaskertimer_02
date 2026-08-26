package com.indiewalkabout.nowdothis.feature.naturallanguage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmRegistryEvidenceTest {
    @Test
    fun parse_correlatesRegisteredAlarmWithPendingIntentRequestIdentity() {
        val evidence = AlarmRegistryEvidence.parse(
            alarmDump = ALARM_DUMP,
            pendingIntentDump = PENDING_INTENT_DUMP,
            packageName = PACKAGE_NAME
        )

        assertEquals(
            listOf(
                RegisteredAlarm(
                    triggerAt = 2_145_990_600_000L,
                    type = "RTC_WAKEUP",
                    packageName = PACKAGE_NAME,
                    receiverComponent = "$PACKAGE_NAME/.core.notifications.ReminderReceiver",
                    requestCode = 42
                )
            ),
            evidence
        )
    }

    @Test
    fun parse_rejectsPendingIntentIdentityWithoutRegisteredAlarm() {
        val evidence = AlarmRegistryEvidence.parse(
            alarmDump = "Current Alarm Manager state:\n  0 pending alarms:\n",
            pendingIntentDump = PENDING_INTENT_DUMP,
            packageName = PACKAGE_NAME
        )

        assertEquals(emptyList<RegisteredAlarm>(), evidence)
    }

    @Test
    fun parse_rejectsAlarmWhosePendingIntentRecordCannotBeCorrelated() {
        val evidence = AlarmRegistryEvidence.parse(
            alarmDump = ALARM_DUMP.replace("record42", "differentRecord"),
            pendingIntentDump = PENDING_INTENT_DUMP,
            packageName = PACKAGE_NAME
        )

        assertNull(evidence.singleOrNull())
    }

    private companion object {
        const val PACKAGE_NAME = "com.indiewalkabout.nowdothis"
        val ALARM_DUMP = """
            Current Alarm Manager state:
              1 pending alarms:
                RTC_WAKEUP #1: Alarm{alarm42 type 0 origWhen 2145990600000 whenElapsed 90123 $PACKAGE_NAME}
                  tag=*walarm*:$PACKAGE_NAME/.core.notifications.ReminderReceiver
                  type=RTC_WAKEUP origWhen=2037-12-30 17:00:00.000 window=0 repeatInterval=0 count=0 flags=0x9
                  operation=PendingIntent{operation42: PendingIntentRecord{record42 $PACKAGE_NAME broadcastIntent}}
              LazyAlarmStore stats:
        """.trimIndent()
        val PENDING_INTENT_DUMP = """
            ACTIVITY MANAGER PENDING INTENTS (dumpsys activity intents)
              * $PACKAGE_NAME: 1 items
                #0: PendingIntentRecord{record42 $PACKAGE_NAME broadcastIntent}
                  uid=10199 packageName=$PACKAGE_NAME featureId=null type=broadcastIntent flags=0x48000000
                  requestCode=42 requestResolvedType=null
                  requestIntent=xflg=0x4 cmp=$PACKAGE_NAME/.core.notifications.ReminderReceiver (has extras)
        """.trimIndent()
    }
}
