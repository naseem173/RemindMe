# Android Alarm Reminder App — Implementation Specification

## 0. Instruction to the implementation agent

Build a production-quality **Android-only reminder/alarm application**.

Do not build this in Capacitor, Ionic, React, Vite, React Native, or Flutter.

Use:

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX
- Android `AlarmManager`
- Android foreground services
- Android full-screen alarm notifications
- Room for persistent structured data
- DataStore for application preferences
- `java.time` for date/time primitives
- a mature RFC 5545 recurrence library rather than implementing recurrence calculations manually

The application must be fully usable without internet connectivity and must not require an account.

The overriding product goals are:

1. Extremely simple for an older non-technical user.
2. Alarm-clock-level reminder reliability.
3. Precise user-selected firing time.
4. Audible alarm rather than a brief notification sound.
5. Title and description visibly displayed while the alarm is ringing.
6. Alarm must work if the normal application UI is not running.
7. Alarm schedules must survive process death and be reconstructed after reboot.
8. Prefer Android/platform/Jetpack functionality or mature libraries over handwritten replacements.
9. No backend, authentication, analytics or cloud synchronization in v1.
10. No custom scheduling engine where an RFC 5545 recurrence implementation already exists.

---

# 1. Scope definition

“Google Calendar parity” in this specification means **personal scheduling parity**, not Google Workspace collaboration parity.

Implement:

- reminder title
- optional description
- date
- time
- date-only/all-day-style reminders
- one-time reminders
- daily recurrence
- weekday recurrence
- weekly recurrence
- recurrence on selected weekdays
- monthly recurrence
- yearly recurrence
- custom interval recurrence
- recurrence ending never
- recurrence ending on a date
- recurrence ending after N occurrences
- editing one occurrence
- editing this occurrence and following occurrences
- editing an entire series
- deleting one occurrence
- deleting this occurrence and following occurrences
- deleting an entire series
- enabling/disabling a reminder
- upcoming/agenda view
- month calendar view
- week calendar view if it remains simple enough
- search
- timezone-aware scheduling
- local device timezone mode
- fixed timezone mode
- alarm sound selection
- vibration
- snooze
- ringing screen
- lock-screen ringing
- alarm status/health diagnostics
- manual “Test alarm” functionality

Google Calendar currently supports repeating events and custom recurring tasks, including day/week/month/year recurrence and recurrence end conditions. Use that behavior as the UX reference rather than inventing an unfamiliar recurrence model.

Do **not** implement in v1:

- Google login
- Google Calendar API synchronization
- Android Calendar Provider synchronization
- guests
- guest availability
- invitations
- Meet links
- conference links
- rooms/resources
- shared calendars
- email
- cloud backup
- remote family administration
- attachments
- working locations
- third-party calendars
- server-side push notifications

Architect the data model so cloud synchronization could be added later, but do not let hypothetical future synchronization complicate the first version.

---

# 2. Platform decision

## Required architecture

Use native Kotlin + Jetpack Compose.

Android currently strongly recommends Jetpack Compose, single-activity architecture, ViewModels, repositories, UDF, coroutines and Flow for new applications.

Use Material 3 rather than attempting to clone Material styling manually. Current stable Compose Material 3 is available directly from AndroidX.

## SDK baseline

Start with:

```text
minSdk = 26
targetSdk = 36
compileSdk = 36
```

Target API 36 because new Play Store submissions currently require Android 16/API 36 or newer.

Before release, verify whether changing `compileSdk` to 37 is appropriate, but do not change `targetSdk` merely to chase a preview/new platform unless all behavior changes have been tested.

The app MUST nevertheless be tested on Android 17 because Android 17 applies background-audio restrictions even to apps targeting older API levels. A background app attempting audio must have a visible activity or suitable foreground service; exact-alarm applications using `USAGE_ALARM` have a relevant exemption from the stricter API-37 WIU condition.

---

# 3. Dependency policy

Use Gradle Kotlin DSL and a version catalog.

Pin stable dependency versions.

Do not use alpha/beta dependencies unless there is no production alternative.

At project creation time, check current stable versions rather than blindly copying versions from this document.

Known current reference points as of September 2026 include:

- Compose Material 3 stable 1.4.0
- Room 3.0.0
- iCal4j 4.3.0
- Kizitonwose Calendar 2.10.1

Room 3.0.0 became stable in July 2026.

## Required/approved libraries and platform APIs

Prefer:

```text
Jetpack Compose
Material 3
Navigation 3
Lifecycle / ViewModel
Kotlin coroutines
Flow
Room
DataStore
AndroidX Core / NotificationCompat
Hilt
java.time
AlarmManager
NotificationManager
MediaPlayer
AudioManager
RingtoneManager
Vibrator/VibratorManager
TextToSpeech
```

### Recurrence

Do NOT write recurrence generation by hand.

Store recurrence using RFC 5545-compatible recurrence rules.

First-choice recurrence implementation:

```text
org.mnode.ical4j:ical4j
```

Current Maven Central version at research time is 4.3.0. It is an established iCalendar implementation and therefore provides a standardized recurrence model rather than a bespoke one.

Before committing to it, create an Android instrumentation/release/R8 compatibility test covering recurrence calculation.

iCal4j explicitly supports Android, and recent releases include Android compatibility fixes, but its dependency footprint needs to be verified under the actual minSdk/R8 configuration.

If current iCal4j fails Android/R8 compatibility, use:

```text
org.dmfs:lib-recur
```

Current reference version is 0.17.1.

It is a dedicated RFC recurrence processor.

Under no circumstances replace either library with a handwritten “add one month/add seven days” recurrence engine.

### Calendar visual component

Evaluate:

```text
com.kizitonwose.calendar:compose
```

It provides mature month/week/year calendar primitives and uses `java.time`.

However, verify compatibility with the project's chosen Compose version before adopting it.

Do not downgrade the entire Compose stack merely to satisfy a calendar UI dependency.

If compatibility is problematic, use standard Compose lazy-layout primitives for the visual calendar while keeping all date/recurrence logic in `java.time` and the recurrence library.

---

# 4. Application architecture

Use a single application module initially. Do not create a large multi-module architecture for a small offline app.

Suggested package structure:

```text
app/
  data/
    local/
    repository/
    settings/
  domain/
    model/
    recurrence/
    scheduler/
    usecase/
  alarm/
    scheduling/
    receiver/
    service/
    notification/
    audio/
    directboot/
  ui/
    home/
    calendar/
    editor/
    recurrence/
    alarm/
    settings/
    health/
    components/
    theme/
```

Use:

```text
UI -> ViewModel -> use case/repository -> Room/DataStore
```

Room is the source of truth for reminders.

UI must never directly manipulate `AlarmManager`.

UI must never directly manipulate Room DAOs.

All changes to scheduling must pass through a centralized alarm coordinator.

---

# 5. Core domain model

Do not persist a gigantic list of generated recurrence occurrences.

Persist a recurrence series plus exceptions.

Suggested principal entity:

```kotlin
ReminderSeriesEntity
```

Fields:

```text
id: UUID/string
title: string
description: nullable string

enabled: boolean

startDate: LocalDate
startTime: nullable LocalTime

dateOnly: boolean

timezoneMode:
    DEVICE_LOCAL
    FIXED_ZONE

zoneId: nullable IANA ZoneId string

recurrenceRule: nullable RFC5545 RRULE string

alarmSoundUri: nullable string
vibrate: boolean
speakReminder: boolean
snoozeMinutes: integer
maxRingMinutes: integer

createdAt: Instant
updatedAt: Instant
```

`startTime == null` for a date-only reminder.

Date-only reminders use the application's configured default alarm time, initially 09:00.

Store IANA timezone IDs such as:

```text
Asia/Kolkata
Europe/London
America/New_York
```

Never persist abbreviated timezone names such as `IST`, `EST`, etc.

---

# 6. Recurrence exception model

Create a separate occurrence-exception table.

For example:

```text
ReminderOccurrenceExceptionEntity

id
seriesId
originalOccurrenceLocalDateTime
type:
    SKIP
    OVERRIDE
overrideDate
overrideTime
overrideTitle
overrideDescription
overrideAlarmSettings...
```

Use it for “edit this occurrence” and “delete this occurrence.”

For “this and following,” split the recurrence:

1. truncate the existing series immediately before the selected occurrence;
2. create a new series beginning at the selected occurrence;
3. copy relevant user settings;
4. apply edits to the new series.

Do not mutate past occurrence history unnecessarily.

---

# 7. Runtime alarm record

Create a small runtime table to make alarm handling idempotent:

```text
AlarmInstanceEntity
```

Suggested fields:

```text
instanceId
seriesId
originalOccurrence
triggerInstant
state
snoozeCount
createdAt
firedAt
dismissedAt
```

States:

```text
SCHEDULED
FIRING
SNOOZED
DISMISSED
MISSED
CANCELLED
```

This provides:

- duplicate-delivery protection
- diagnostics
- snooze tracking
- testing visibility
- recovery after crashes

Do not use the runtime table as the recurrence source of truth.

---

# 8. Recurrence behavior

Offer simple presets first:

```text
Does not repeat
Every day
Every weekday
Every week
Every month
Every year
Custom…
```

Custom recurrence must support:

```text
Repeat every [N] day(s)
Repeat every [N] week(s)
Repeat every [N] month(s)
Repeat every [N] year(s)

For weekly:
Mon Tue Wed Thu Fri Sat Sun

Ends:
Never
On [date]
After [N] occurrences
```

For monthly schedules support at minimum:

```text
day 15 of each month
second Tuesday
last Friday
```

The recurrence library must produce occurrences.

Do not materialize infinite recurrences.

Do not copy Google Calendar's 730-instance storage/display limit. Google currently documents that limit for its recurring events, but this local architecture does not require generating hundreds of future rows.

---

# 9. Exact-alarm strategy

This is the most important implementation rule.

Android has made all repeating `AlarmManager` alarms inexact since API 19. Therefore **never use a repeating Android alarm to represent a recurring reminder**.

Instead:

```text
recurrence series
      ↓
calculate next occurrence
      ↓
schedule ONE exact Android alarm
      ↓
alarm fires
      ↓
calculate next occurrence
      ↓
schedule next exact Android alarm
```

Each enabled series therefore has at most one regular future Android alarm scheduled at a time.

Snoozes are independent one-shot exact alarms.

---

# 10. AlarmManager API choice

Primary implementation should use:

```kotlin
AlarmManager.setAlarmClock(...)
```

for reminders configured to behave as alarms.

Android describes `setAlarmClock()` alarms as its most critical/highly visible alarm type and guarantees that the system leaves low-power modes when necessary to deliver them.

Pass:

- exact wall-clock trigger timestamp
- `AlarmClockInfo`
- a `showIntent` that opens that reminder
- a broadcast `PendingIntent` that fires the application alarm receiver

Use immutable PendingIntents.

PendingIntent identity must be deterministic and collision-safe.

Never use an `OnAlarmListener` for durable alarms; Android explicitly says listener alarms may disappear when the process has no active components. Use PendingIntent-based APIs.

If product testing reveals an unacceptable system “next alarm” UX from `setAlarmClock()`, the approved fallback is:

```kotlin
setExactAndAllowWhileIdle()
```

Do not fall back to an inexact reminder.

---

# 11. Exact alarm permission

Declare:

```xml
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
```

This application has a legitimate alarm/calendar core function.

Android explicitly recommends `USE_EXACT_ALARM` for calendar and alarm-clock applications; it is granted at installation but Google Play restricts publication to qualifying applications.

Create an abstraction such as:

```text
ExactAlarmCapability
```

with:

```text
isAvailable()
openSettingsIfRequired()
```

Call `canScheduleExactAlarms()` defensively before arming.

Do not also request `SCHEDULE_EXACT_ALARM` in the same production manifest unless a deliberate fallback distribution strategy requires it.

If Play review rejects `USE_EXACT_ALARM`, create an alternate manifest/build configuration using `SCHEDULE_EXACT_ALARM` and the Android special-access flow rather than removing exact scheduling.

---

# 12. Required manifest capabilities

Expected permissions include:

```xml
android.permission.USE_EXACT_ALARM
android.permission.USE_FULL_SCREEN_INTENT
android.permission.POST_NOTIFICATIONS
android.permission.RECEIVE_BOOT_COMPLETED
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
android.permission.VIBRATE
android.permission.WAKE_LOCK
```

Do **not** add:

```text
INTERNET
READ_CALENDAR
WRITE_CALENDAR
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
ACCESS_NOTIFICATION_POLICY
```

unless a later feature actually requires one.

Keep the permission surface minimal.

Android 14+ requires foreground services to declare an appropriate type; background alarm audio should use a `mediaPlayback` foreground service and its corresponding permission.

---

# 13. Notification permission

Android 13+ requires `POST_NOTIFICATIONS` for normal application notifications, including visible foreground-service notification behavior.

Do not request it immediately on first launch without context.

Onboarding sequence:

```text
Welcome
↓
"To ring your reminders, allow notifications"
↓
request notification permission
↓
check full-screen alarm capability
↓
run test alarm
↓
finish
```

If permission is denied:

- reminders may still be saved;
- show an obvious persistent “Alarm setup incomplete” warning;
- settings/health page must explain exactly what is missing;
- provide a direct route to application notification settings.

Never silently claim reminders are fully configured when required capabilities are missing.

---

# 14. Full-screen alarm behavior

Declare:

```xml
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
```

For Android 14+, check:

```kotlin
NotificationManager.canUseFullScreenIntent()
```

If unavailable, provide a button opening:

```text
ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
```

Android reserves full-screen intents for very high-priority alarm/call scenarios, and alarm apps are explicitly within the approved use case.

Never launch the ringing Activity from arbitrary background code.

Use a full-screen notification intent.

---

# 15. Alarm firing pipeline

Implement the path approximately as follows:

```text
AlarmManager
    ↓
AlarmReceiver
    ↓
validate alarm ID / instance
    ↓
start AlarmRingingService
    ↓
service enters foreground immediately
    ↓
post CATEGORY_ALARM notification
    ↓
fullScreenIntent -> AlarmActivity
    ↓
start looping USAGE_ALARM audio
    ↓
start alarm vibration
    ↓
optional TTS
```

Android permits a foreground service to start from the background when an exact alarm fires to perform a user-requested action.

Do not perform substantial work inside `BroadcastReceiver.onReceive()`.

Use `goAsync()` where required and complete quickly.

The alarm receiver must be idempotent.

If the same PendingIntent is delivered twice, there must still only be one ringing service/alarm instance.

---

# 16. Foreground ringing service

Implement:

```text
AlarmRingingService
```

as a foreground service with:

```xml
android:foregroundServiceType="mediaPlayback"
```

When started:

1. immediately call `startForeground()`;
2. resolve alarm data;
3. mark runtime alarm `FIRING`;
4. play sound;
5. start vibration;
6. initialize optional TTS;
7. ensure the next regular recurrence has been scheduled;
8. remain active until Stop, Snooze, timeout or unrecoverable failure.

Do not use WorkManager for alarm playback.

WorkManager explicitly does not guarantee execution at a precise scheduled instant and is intended for deferrable work.

WorkManager may be used for:

- database cleanup
- optional backups
- non-time-critical diagnostics

but not the actual alarm trigger.

---

# 17. Android 17 background audio

The implementation MUST be compatible with Android 17's background-audio hardening.

Android 17 requires background audio interactions to occur with a visible activity or suitable foreground service. For target API 37, there are stricter WIU requirements, with an exception for applications holding exact-alarm permission and interacting with `USAGE_ALARM` streams.

Therefore:

- start the foreground service first;
- then initialize/request audio focus;
- use `USAGE_ALARM`;
- do not change system volume;
- do not attempt background MediaPlayer playback before foreground promotion.

---

# 18. Alarm audio

Use the Android system alarm-sound picker:

```text
RingtoneManager.ACTION_RINGTONE_PICKER
RingtoneManager.TYPE_ALARM
```

Android provides a dedicated alarm ringtone type and default alarm URI.

Persist the selected URI.

Use:

```text
Settings.System.DEFAULT_ALARM_ALERT_URI
```

when the user chooses Default.

For playback, use platform `MediaPlayer` with:

```text
AudioAttributes.USAGE_ALARM
CONTENT_TYPE_SONIFICATION
looping = true
```

Using `USAGE_ALARM` ensures Android treats this as alarm audio instead of ordinary media/notification audio.

Use:

```text
MediaPlayer.setWakeMode(...)
```

if needed so playback survives screen-off without manually maintaining a large custom wake-lock implementation.

Do not:

- set system alarm volume automatically
- force maximum volume
- alter ring volume
- alter media volume
- play through `USAGE_MEDIA`
- package a copyrighted ringtone

Provide a small “Preview alarm sound” control.

---

# 19. Alarm duration

Default:

```text
10 minutes
```

Offer hidden/advanced choices:

```text
1 minute
5 minutes
10 minutes
20 minutes
30 minutes
Until dismissed
```

If timeout occurs:

- stop audio
- stop vibration
- stop TTS
- mark runtime instance `MISSED`
- leave a missed-reminder notification
- schedule the next recurrence normally

Do not allow a forgotten alarm to keep a foreground service/audio running indefinitely unless the user explicitly selects “Until dismissed.”

---

# 20. Audio focus

Request appropriate transient/exclusive audio focus after foreground-service start.

Handle:

```text
focus granted
focus delayed
focus denied
focus loss
```

Alarm reliability takes precedence over media niceties, but never bypass Android audio policy using unsupported APIs.

Do not programmatically increase the alarm volume if it is zero.

Instead, Alarm Health should warn:

```text
Alarm volume appears to be muted.
```

and offer a system-settings shortcut.

---

# 21. Do Not Disturb

Treat alarms as alarms:

```text
Notification.CATEGORY_ALARM
AudioAttributes.USAGE_ALARM
VibrationAttributes.USAGE_ALARM
```

Android DND policy can explicitly allow or block alarms. If the user has configured DND to disallow alarms, the application must respect that.

Do not request global notification-policy access in the default build.

Do not try to secretly bypass DND.

Instead:

- explain that Android's alarm/DND setting controls whether alarms may sound;
- offer a Test Alarm;
- provide a settings shortcut where appropriate.

---

# 22. Vibration

Default vibration = ON.

Use alarm-class vibration attributes.

On current Android versions:

```text
VibrationAttributes.USAGE_ALARM
```

Android specifically supports alarm usage for vibration occurring while an app is in the background.

Use a simple repeating alarm pattern.

Stop it immediately on:

- Stop
- Snooze
- timeout
- service shutdown

---

# 23. Spoken reminder / Text-to-Speech

Implement optional:

```text
Read reminder aloud
```

using Android's built-in `TextToSpeech`.

Do not depend on a cloud speech service.

When enabled:

1. begin alarm sound;
2. wait a short interval;
3. temporarily attenuate/pause alarm if required;
4. say:

```text
Reminder. {title}. {description}
```

5. resume normal alarm loop.

Set TTS audio attributes to `USAGE_ALARM` if supported.

Android's TTS API supports explicit audio attributes.

If description is empty:

```text
Reminder. {title}.
```

Do not continuously repeat the entire description every few seconds.

A reasonable cycle is:

```text
alarm
→ spoken text once
→ continue alarm
```

Optionally repeat speech every 2–5 minutes if the user enables that setting later.

TTS failure must never prevent the ordinary alarm from sounding.

Add the required TTS service query to the manifest for modern Android package visibility.

---

# 24. Ringing Activity

Create a dedicated Activity:

```text
AlarmActivity
```

It must:

- use Material 3
- appear over the lock screen
- turn the screen on
- not require the user to unlock merely to see/stop the alarm
- contain no navigation drawer or unnecessary controls
- use extremely large touch targets

Use:

```kotlin
setShowWhenLocked(true)
setTurnScreenOn(true)
```

These are the modern Android APIs intended for lock-screen-visible activities.

Suggested layout:

```text
[ current time ]

REMINDER

Take medicine

Take the blue tablet after breakfast

[ SNOOZE 10 MIN ]

[ STOP ]
```

Make Stop and Snooze enormous.

Minimum target sizes should meet or exceed Android accessibility guidance.

Avoid swipe-to-dismiss as the only interaction.

Hardware Back must not accidentally stop the alarm.

If Back is pressed, either ignore it or return to the same ringing UI while keeping the alarm active.

---

# 25. Notification while ringing

Create one notification channel:

```text
alarms_v1
```

Use:

```text
IMPORTANCE_HIGH
CATEGORY_ALARM
public/appropriate lockscreen visibility
```

Because the foreground service itself owns continuous audio/vibration, avoid producing a second competing notification-channel sound.

The notification should contain:

```text
title
description
Stop action
Snooze action
full-screen intent
```

Stop and Snooze actions must directly target an explicit receiver/service PendingIntent.

Do not use notification trampolines.

---

# 26. Stop behavior

On Stop:

1. atomically mark alarm instance `DISMISSED`;
2. stop MediaPlayer;
3. stop vibration;
4. stop TTS;
5. cancel ringing notification;
6. stop foreground;
7. stop service;
8. close AlarmActivity;
9. ensure next recurrence remains scheduled.

Stop must be idempotent.

Two Stop commands must have exactly the same result as one.

---

# 27. Snooze behavior

Default snooze:

```text
10 minutes
```

On Snooze:

1. mark current alarm `SNOOZED`;
2. stop current sound/vibration/TTS;
3. create a one-shot snooze `AlarmInstance`;
4. schedule it as an exact alarm at `now + snoozeDuration`;
5. close ringing UI;
6. stop service;
7. preserve the original recurrence schedule.

Snoozing a recurring reminder does NOT shift the recurring series.

Example:

```text
Daily reminder = 09:00
Snooze today's occurrence to 09:10
Tomorrow remains 09:00
```

---

# 28. Direct Boot and reboot reliability

This is mandatory for alarm-clock-grade behavior.

Android clears `AlarmManager` registrations at shutdown/reboot.

Additionally, before the first device unlock after boot, normal credential-protected application storage is inaccessible.

Android explicitly documents alarm-clock applications as a reason to use Direct Boot and device-protected storage.

Implement a **small device-protected alarm mirror database**.

Do not move the entire main database into Direct Boot storage.

Store only what is needed to fire currently armed next alarms:

```text
instance ID
series ID
trigger timestamp
title
description
alarm sound URI
vibration setting
TTS setting
snooze setting
max ring duration
```

Build this Room database using:

```text
createDeviceProtectedStorageContext()
```

Main Room database remains credential protected.

Whenever an alarm schedule changes:

```text
main DB
↓
calculate next occurrence
↓
update direct-boot mirror
↓
update AlarmManager
```

Create a direct-boot-aware receiver listening to:

```text
ACTION_LOCKED_BOOT_COMPLETED
```

At locked boot:

1. open only device-protected storage;
2. read pending alarm mirrors;
3. discard stale entries according to missed-alarm policy;
4. re-register future exact alarms;
5. do not open normal Room DB.

On ordinary:

```text
ACTION_BOOT_COMPLETED
```

after unlock:

1. open full database;
2. perform complete reconciliation;
3. regenerate direct-boot mirror;
4. reconcile AlarmManager schedules.

AlarmReceiver, AlarmRingingService and AlarmActivity must be capable of functioning before credential unlock if a mirrored alarm fires.

No dependency used by this Direct Boot path may eagerly open the credential-protected main database.

---

# 29. Other schedule-reconciliation triggers

The central reconciliation code must also handle:

```text
app update / MY_PACKAGE_REPLACED
device timezone changed
manual device clock changed
exact-alarm permission/capability changed
user unlock
app startup
reminder creation
reminder edit
reminder delete
enable/disable
```

Do not duplicate scheduling logic in individual receivers.

Every trigger should call the same:

```text
ReconcileAlarmScheduleUseCase
```

or equivalent.

---

# 30. Timezone model

Support two modes.

## Follow device timezone

Default.

A reminder such as:

```text
08:00 every day
```

rings at 08:00 according to whatever timezone the phone currently uses.

Good for:

- medicine
- breakfast
- daily routine
- household tasks

On timezone change, recalculate future exact alarms.

## Fixed timezone

Advanced option.

Example:

```text
09:00 Europe/London
```

remains tied to London time while travelling.

Google Calendar similarly models calendar events around timezone-aware date/time behavior.

---

# 31. DST rules

Write explicit tests for daylight-saving transitions.

For a nonexistent local time during spring-forward:

```text
02:30 does not exist
```

policy:

```text
use the next valid wall-clock time that day
```

For an ambiguous local time during fall-back:

```text
01:30 exists twice
```

policy:

```text
fire once using the earlier offset
```

Never accidentally fire twice merely because an offset repeated.

Do all recurrence computation in the intended local timezone rather than generating in UTC and then assuming wall-clock equivalence.

---

# 32. Missed alarms

Define a deterministic policy.

Recommended:

```text
0–10 minutes late:
    ring immediately

>10 minutes and <=2 hours late:
    show a "Missed reminder" high-priority notification,
    do not begin a surprise full alarm

>2 hours late:
    record as missed and show in app,
    no alarm
```

This matters after:

- reboot
- force-stop recovery
- clock changes
- prolonged device shutdown

Expose constants centrally so behavior is testable and adjustable.

---

# 33. Force-stop limitation

Android force-stop intentionally places the application in a stopped state and prevents it from self-starting until the user explicitly interacts with it. Android removes associated runtime state including alarms.

The app cannot defeat this by design.

Alarm Health should explain:

```text
If Android Settings > Force stop was used, open this app once to restore reminders.
```

On the next app launch, perform full reconciliation.

Do not attempt hacks to evade force-stop semantics.

---

# 34. Home experience

Default screen:

```text
Today / Upcoming agenda
```

not a complex calendar grid.

Top section:

```text
Saturday, 5 September

Next alarm
8:00 PM
Take medicine
```

Then upcoming reminders ordered chronologically.

Floating action button:

```text
+
```

label/icon semantics:

```text
Add reminder
```

Do not present separate Event/Task/Reminder entity types.

There is only one thing:

```text
Reminder
```

---

# 35. Reminder editor UX

The default editor should fit the primary workflow without scrolling excessively.

Order:

```text
Title
Description
Date
Time
Repeat
Save
```

Title is required.

Description optional.

Advanced options hidden beneath:

```text
More options
```

containing:

```text
Alarm sound
Vibrate
Read aloud
Timezone
Snooze
Ring duration
```

Most reminders should require fewer than ~6 interactions.

Remember sensible defaults from settings.

When user taps Save:

1. validate;
2. persist;
3. generate next occurrence;
4. schedule exact alarm;
5. display confirmation including actual next ring time.

Example:

```text
Saved
Next alarm: Tomorrow at 8:00 AM
```

---

# 36. Date-only reminders

Support date-only reminders.

Default ring time:

```text
09:00
```

Google Tasks also currently treats tasks with a date but no explicit time as 9 AM notifications, making this a familiar default.

Allow changing default date-only time in Settings.

Do not silently create an alarm at midnight.

---

# 37. Calendar screen

Provide:

```text
Agenda
Month
```

and optionally:

```text
Week
```

if the calendar component makes this clean.

Do not prioritize Day/3-day/Week complexity over alarm reliability.

Month day cells should show small indicators for reminders.

Tapping a date shows that day's reminders.

Tapping a reminder opens details/edit.

Long-press should not be required for basic operations.

Respect locale for:

- first day of week
- month names
- weekday labels
- 12/24-hour clock
- date formatting

---

# 38. Search

Provide local search over:

```text
title
description
```

Use Room FTS if the dataset/search UX justifies it.

Do not write a custom indexing engine.

For a small dataset, a parameterized `LIKE` query may be sufficient; choose based on actual requirements and tests.

---

# 39. Reminder details

Display:

```text
title
description
next occurrence
repeat description
alarm sound
snooze duration
enabled status
```

Actions:

```text
Edit
Disable/Enable
Delete
```

For recurring instances, opening through a calendar occurrence must preserve its recurrence occurrence ID so edits can target:

```text
This reminder
This and following
Entire series
```

---

# 40. Recurrence editing UX

When editing/deleting an occurrence within a recurring series, display a bottom sheet/dialog with clear language:

```text
Only this occurrence

This and following

Entire series
```

Do not perform a destructive whole-series edit without asking.

Past completed occurrences/history should not unexpectedly mutate when “this and following” is chosen.

---

# 41. Settings screen

Keep settings short.

Sections:

```text
Alarm defaults
Appearance
Calendar
Alarm health
About
```

Alarm defaults:

```text
Default alarm sound
Vibrate
Snooze duration
Ring duration
Read reminder aloud
Date-only alarm time
```

Calendar:

```text
Use device timezone
First day of week: System default / selectable
```

Appearance:

```text
System / Light / Dark
Dynamic color
```

---

# 42. Alarm Health screen

This screen is essential because Android gives users control over several things that can prevent ideal alarm behavior.

Show independent status rows:

```text
Exact alarms             OK / Problem
Notifications            OK / Problem
Full-screen alarms       OK / Problem
Alarm volume              70%
Alarm sound               Default alarm
Battery/background        Informational
Next scheduled alarm      <timestamp>
Last alarm fired          <timestamp/result>
```

Provide:

```text
Test alarm in 10 seconds
```

The test MUST use the same real alarm path as production:

```text
AlarmManager
→ receiver
→ foreground service
→ notification/fullscreen
→ sound
```

Do not implement the test as an Activity directly playing audio, because that would test the wrong thing.

---

# 43. Onboarding

Limit onboarding to what is required for reliability.

Suggested flow:

## Page 1

```text
Simple reminders that ring like an alarm.
```

## Page 2

Explain notification permission.

Request it.

## Page 3

Check full-screen intent permission/capability.

If missing, explain why and open Android settings.

## Page 4

```text
Test your alarm
```

Run a 10-second exact alarm.

User confirms:

```text
I heard it
```

Then finish.

Do not ask the user about recurrence engines, battery optimization, Android APIs, foreground services, etc.

---

# 44. Accessibility / older-user requirements

This application's intended user makes accessibility a first-class requirement.

Requirements:

- minimum 48dp touch targets, preferably larger on alarm UI
- Material typography
- support Android font scaling
- no clipped title/description at 200% text scaling
- TalkBack content descriptions
- logical TalkBack traversal order
- high contrast
- no color-only state communication
- no small unlabeled icon-only destructive actions
- destructive actions ask for confirmation when appropriate
- Snooze and Stop remain visible without scrolling
- one obvious primary action per editor
- error messages in plain language
- no technical Android terminology on ordinary screens

Use standard Material components wherever possible because they provide established accessibility behavior.

---

# 45. Data privacy

Default application should have no network functionality.

Do not request `INTERNET`.

Reminder data remains on device.

Do not integrate:

```text
Firebase Analytics
Google Analytics
Facebook SDK
ad SDKs
remote logging SDKs
```

for v1.

Local debug logging must never log full reminder descriptions in release builds unless explicitly needed.

---

# 46. Backup/export

Not required for the first alarm milestone.

After alarm reliability is complete, optionally implement manual:

```text
Export reminders
Import reminders
```

Use Android Storage Access Framework.

Prefer RFC 5545 `.ics` export/import through iCal4j where practical.

Do not invent a proprietary calendar format as the only portable backup format.

Application-only settings can additionally be represented in a versioned JSON sidecar if necessary.

---

# 47. Database migrations

Never enable destructive migration in release.

Every schema change must have:

- explicit migration
- migration test
- pre/post data assertions

Keep Room schema export enabled and commit schema history to Git.

---

# 48. Dependency injection

Use Hilt for:

```text
repositories
DAOs
scheduler
recurrence service
settings repository
alarm coordinator
```

Be careful with Direct Boot.

The application/Hilt graph MUST NOT eagerly open the credential-protected main Room database during locked boot.

Dependencies injected into direct-boot-aware components must be lazy and Direct-Boot-safe.

A dedicated provider for the device-protected mirror database is preferable.

---

# 49. Scheduler interfaces

Define testable abstractions.

For example:

```kotlin
interface ReminderScheduler {
    suspend fun reconcileAll()
    suspend fun scheduleNext(seriesId: ReminderId)
    suspend fun cancelSeries(seriesId: ReminderId)
    suspend fun scheduleSnooze(instanceId: AlarmInstanceId, at: Instant)
}
```

```kotlin
interface RecurrenceCalculator {
    fun nextOccurrence(
        series: ReminderSeries,
        after: Instant
    ): ReminderOccurrence?
}
```

```kotlin
interface AlarmAudioController {
    suspend fun start(config: AlarmAudioConfig)
    fun stop()
}
```

This lets recurrence and state transitions be unit-tested without sleeping/waiting for real clocks.

Inject a:

```text
Clock
```

rather than calling `Instant.now()` throughout business logic.

---

# 50. State consistency

Use Room transactions when changing reminder data and runtime schedule state.

AlarmManager is outside the DB transaction, therefore implement reconciliation as an idempotent operation.

The database is authoritative.

If DB and AlarmManager might disagree:

```text
DB wins
→ cancel/rebuild platform alarms
```

Never regard PendingIntents as the canonical reminder store.

---

# 51. Editing algorithm

When a reminder changes:

```text
begin DB transaction
load existing series
validate edit
save series/exceptions
invalidate old runtime instance
commit
cancel existing PendingIntent(s)
calculate next occurrence
write/update Direct Boot mirror
schedule exact alarm
```

If scheduling fails:

- preserve the reminder in DB;
- record schedule error;
- surface warning;
- do not silently pretend the alarm is armed.

---

# 52. Deleting algorithm

Deleting a reminder must:

```text
cancel Android PendingIntent
cancel pending snooze alarms
stop it if currently ringing
remove/update Direct Boot mirror
mark/delete runtime instances
remove main series/exception data as appropriate
```

Deletion must be idempotent.

---

# 53. Notification channel lifecycle

Because Android notification-channel attributes are user-controlled and largely immutable once created, create channel IDs deliberately.

Do not create a new channel on every reminder.

Use a versioned identifier such as:

```text
alarms_v1
```

Only create `alarms_v2` if a migration genuinely requires materially different channel behavior.

---

# 54. Process-death testing

The alarm must succeed when:

```text
UI open
UI backgrounded
app swiped from Recents
process killed with adb
phone screen off
phone locked
Doze active
Battery Saver active
device rebooted
device rebooted and not unlocked
```

A normal process kill is not the same as Android Force Stop; test both separately.

---

# 55. Test strategy

## Unit tests

Test:

- every recurrence preset
- every custom recurrence combination
- monthly edge cases
- leap years
- February 29
- 30/31-day transitions
- DST spring-forward
- DST fall-back
- timezone changes
- recurrence end date
- recurrence count
- exception skipping
- occurrence override
- series split
- next-occurrence calculation
- snooze state transition
- missed-alarm policy
- idempotent Stop
- idempotent receiver handling

## Database tests

Test:

- inserts
- updates
- delete scopes
- migrations
- exception joins
- runtime-state transitions

## Alarm integration tests

Use real Android instrumentation for:

```text
exact trigger
screen locked
full-screen activity
foreground service
Stop action
Snooze action
reboot reconciliation
Direct Boot
notification permission denied
full-screen permission denied
```

## Compose tests

Test:

- create reminder
- edit reminder
- recurrence dialogs
- delete flows
- very large font
- TalkBack semantics
- alarm screen button availability

---

# 56. Physical-device matrix

At minimum test:

```text
actual father's device
Pixel / AOSP-like device
Samsung device if father's isn't Samsung
```

OS coverage:

```text
Android 13
Android 14
Android 15
Android 16
Android 17
```

Give highest priority to:

```text
father's exact OS/OEM combination
Android 16 production target
Android 17 background-audio behavior
```

---

# 57. Critical manual test cases

Before calling the app release-ready, manually verify:

```text
alarm in 1 minute while screen on
alarm in 1 minute while screen locked
alarm after swiping app away
alarm after process kill
alarm during Doze
alarm during Battery Saver
snooze
stop from full-screen UI
stop from notification
selected custom alarm sound
alarm volume zero
DND allowing alarms
DND blocking alarms
notification permission denied
full-screen intent disabled
reboot then unlock
reboot without unlock before alarm time
change timezone
change wall-clock time
edit upcoming reminder
delete upcoming reminder
daily recurrence
weekly selected-day recurrence
monthly recurrence
yearly recurrence
DST transition
```

---

# 58. Release build testing

Never validate only debug builds.

Run release/R8 builds because:

- iCal4j and other dependencies may behave differently under shrinker rules;
- background components can expose manifest mistakes;
- TTS/package visibility can differ;
- reflection-based dependencies can fail after shrinking.

Run the core alarm suite against the signed release APK/AAB.

---

# 59. Google Play considerations

If distributing on Play:

1. target API 36 or newer as required at submission time; current requirement is API 36 for new apps.
2. declare the application's alarm functionality accurately.
3. complete the full-screen-intent declaration.
4. complete foreground-service declarations where Play Console requests them.
5. justify `USE_EXACT_ALARM` as core alarm/reminder functionality.
6. do not include unrelated uses of those permissions.
7. make store screenshots/listing clearly show that the core purpose is setting alarms/reminders.

Google Play explicitly permits exact-alarm permission for alarm/timer and calendar apps whose user-facing functionality genuinely requires precise timing.

---

# 60. Error handling

Never silently downgrade an alarm to an inexact notification.

Possible user-visible failures:

```text
Alarm permission unavailable
Notifications disabled
Full-screen alarm permission disabled
Selected ringtone unavailable
Could not schedule exact alarm
```

Every failure must:

- be logged internally;
- produce a clear app status;
- provide a recovery action where possible.

Do not crash because a selected ringtone URI disappeared.

Fallback audio order:

```text
selected alarm URI
→ system default alarm URI
→ bundled simple fallback alarm tone
```

If including a fallback tone, create/license it specifically for the application.

---

# 61. Performance rules

No polling service.

No permanently running background service.

No timer that wakes every minute to search for reminders.

No pre-generating thousands of recurrence instances.

No network scheduler.

Normal steady state should be:

```text
app not running
Room persisted
AlarmManager holds exact next alarms
```

The foreground service exists only while an alarm is actually ringing.

---

# 62. Battery rules

Exact alarms are inherently more battery-expensive, which Android explicitly notes. Use them only because precise alarm delivery is the application's core feature.

Schedule only the next occurrence for each active series.

Do not wake the device for UI/calendar refreshes.

Do not request blanket exemption from battery optimization during onboarding.

If a specific OEM causes demonstrated failures, Alarm Health may offer manufacturer-appropriate guidance, but do not make invasive battery settings part of the default UX.

---

# 63. Suggested implementation sequence

## Milestone 1 — project foundation

Deliver:

- native Kotlin project
- Compose
- Material 3
- Navigation
- Room
- DataStore
- Hilt
- repository architecture
- CI build/lint/unit tests

No alarm UI polish yet.

## Milestone 2 — reminder CRUD

Deliver:

- ReminderSeries model
- create
- edit
- delete
- enable/disable
- agenda

## Milestone 3 — recurrence

Deliver:

- RFC 5545 integration
- presets
- custom recurrence
- exceptions
- series splitting
- exhaustive recurrence tests

Do not proceed to release-level alarm scheduling until recurrence tests are reliable.

## Milestone 4 — exact alarm prototype

Implement:

```text
save one reminder
→ setAlarmClock
→ receiver
→ foreground service
→ looping alarm sound
```

Validate this on physical hardware.

## Milestone 5 — full alarm UX

Add:

- full-screen intent
- lock-screen activity
- title/description
- Stop
- Snooze
- vibration
- alarm sound picker
- optional TTS

## Milestone 6 — lifecycle/recovery

Add:

- boot
- Direct Boot
- device-protected mirror
- timezone changes
- clock changes
- package update reconciliation
- missed alarm handling

## Milestone 7 — calendar UX

Add:

- month view
- day selection
- recurrence occurrence editing
- search

## Milestone 8 — health/onboarding

Add:

- permissions setup
- test alarm
- status diagnostics
- alarm volume warning
- full-screen-intent settings flow

## Milestone 9 — accessibility

Test:

- font scaling
- TalkBack
- touch targets
- contrast
- lock-screen usability

## Milestone 10 — release hardening

Run:

- physical device matrix
- release/R8 builds
- reboot tests
- Android 17 tests
- Play policy audit
- database migration tests

---

# 64. Definition of done

Do not consider v1 complete until all of the following are true:

- A reminder can be created with title/date/time in under approximately 30 seconds.
- Description is optional.
- A one-time reminder rings at the selected time.
- A recurring reminder rings at each exact generated occurrence.
- Recurrence is calculated by a standards-based library.
- Alarm works with the application process dead.
- Alarm works with screen off.
- Alarm UI appears on lock screen when Android allows full-screen alarm intents.
- Alarm produces continuous audible alarm audio, not merely a notification chirp.
- Title is clearly visible during ringing.
- Description is clearly visible during ringing.
- Stop works.
- Snooze works.
- Vibration works.
- Custom/system alarm sound selection works.
- Optional TTS works but its failure cannot break the alarm.
- Recurrence continues correctly after Snooze.
- Alarms are restored after normal reboot.
- An upcoming alarm can be restored before first unlock through Direct Boot.
- Timezone changes do not produce duplicate alarms.
- DST changes do not produce duplicate alarms.
- Editing a reminder cancels obsolete exact alarms.
- Deleting a reminder prevents obsolete alarms from firing.
- Android Force Stop limitation is correctly explained.
- Notification denial is detected.
- Full-screen-intent denial is detected.
- Exact-alarm capability problems are detected.
- Test Alarm exercises the real scheduling path.
- Release/R8 build passes the alarm test suite.
- Actual target device has passed overnight/reboot testing.
- No backend or internet connection is required.
- No calendar/recurrence functionality was reimplemented manually where a proven library already exists.

---

# 65. Things the implementation agent must NOT do

Do not:

- replace exact AlarmManager alarms with WorkManager
- use `setRepeating()` for exact recurrence
- implement recurrence by manually adding days/months
- implement this as a PWA
- rely on a WebView timer
- rely solely on local notifications
- play alarm audio using notification sound alone
- rely solely on JavaScript while Android process is backgrounded
- automatically max out system volume
- bypass user DND settings
- request unnecessary battery-optimization exemption
- request unnecessary network/calendar/contact/location permissions
- put the entire reminder database into device-protected storage
- eagerly access credential-protected storage from Direct Boot components
- silently schedule an inexact alarm when an exact one fails
- create multiple notification channels per reminder
- run a permanent background polling service
- materialize thousands of recurring occurrences
- assume swiping from Recents is the same as Force Stop
- claim Force Stop can be defeated
- ship without physical-device tests
- ship based only on debug builds
- introduce a backend merely for alarm scheduling

---

# 66. Architecture summary

The final runtime design should approximately be:

```text
                    ┌─────────────────────┐
                    │ Jetpack Compose UI  │
                    └─────────┬───────────┘
                              │
                         ViewModels
                              │
                           Use Cases
                              │
             ┌────────────────┴───────────────┐
             │                                │
      Reminder Repository              Alarm Coordinator
             │                                │
         Room DB                       Recurrence Engine
             │                                │
             │                          next occurrence
             │                                │
             │                          AlarmManager
             │                                │
             │                         setAlarmClock()
             │                                │
             │                          AlarmReceiver
             │                                │
             │                    AlarmRingingService (FGS)
             │                        /        |       \
             │                       /         |        \
             │                  MediaPlayer  Vibrator   TTS
             │                       |
             │                Audio USAGE_ALARM
             │
             └──────> Direct Boot alarm mirror
                            │
                    LOCKED_BOOT_COMPLETED
                            │
                       reschedule
```

This architecture should be treated as a reliability requirement, not merely a suggestion.

The application is fundamentally an **alarm clock whose alarms happen to contain reminder text**, rather than a notification application with louder notifications. That distinction should guide every technical decision.