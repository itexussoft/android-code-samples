package com.ready.be.beready.logic

import android.Manifest
import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Entity
import android.provider.CalendarContract.*
import android.support.annotation.RequiresPermission
import com.ready.be.beready.BeReadyApp
import java.util.*
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.CalendarEntity as CalendarEntityHelper
import com.ready.be.beready.persistence.entity.Negotiation
import android.provider.CalendarContract.Events


typealias CalendarEntity = Entity

val CalendarEntity.id: Int
    get() = entityValues.getAsInteger("_id")
val CalendarEntity.timeZone: String
    get() = entityValues.getAsString("calendar_timezone")


object CalendarHelper {

    private val DEFAULT_CALENDAR_ENTITY: CalendarEntity?
        get() {
            val accountEmail = getFirstCreatedGoogleAccountEmail()
            return accountEmail?.let { getCalendarForEmail(it) }
        }

    fun extractEventsFromCalendar(beginTime: Calendar, endTime: Calendar,
                                  calendarEntity: CalendarEntity? = DEFAULT_CALENDAR_ENTITY,
                                  context: Context = BeReadyApp.instance): List<Entity> {
        if (calendarEntity == null) {
            return emptyList()
        }

        val contentResolver = context.contentResolver
        val builder = Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, beginTime.timeInMillis)
        ContentUris.appendId(builder, endTime.timeInMillis)
        val cursor = contentResolver.query(builder.build(),
                null,
                "${Instances.CALENDAR_ID} = ?",
                arrayOf(calendarEntity.id.toString()),
                null)

        val eventsEntities = EventsEntity.newEntityIterator(cursor, contentResolver)
        return try {
            eventsEntities.asSequence().toList()
        } catch (e: Exception) {
            emptyList<Entity>()
        } finally {
            eventsEntities.close()
        }
    }

    @SuppressLint("MissingPermission")
    fun insertNegotiationForCalendar(negotiation: Negotiation,
                                     timeInterval: LongRange,
                                     calendarEntity: CalendarEntity? = DEFAULT_CALENDAR_ENTITY,
                                     context: Context = BeReadyApp.instance): Long? {
        if (calendarEntity == null) {
            return null
        }

        val cr = context.contentResolver
        val values = ContentValues().apply {
            put(Events.DTSTART, timeInterval.start)
            put(Events.DTEND, timeInterval.endInclusive)
            put(Events.TITLE, negotiation.name)
            put(Events.DESCRIPTION, negotiation.info)
            put(Events.CALENDAR_ID, calendarEntity.id)
            put(Events.EVENT_LOCATION, negotiation.location)
            put(Events.EVENT_TIMEZONE, calendarEntity.timeZone)
            put(Events.AVAILABILITY, Events.AVAILABILITY_BUSY)
        }

        val uri = cr.insert(Events.CONTENT_URI, values)
        return uri.lastPathSegment.toLong()
    }

    @SuppressLint("MissingPermission")
    fun updateNegotiationForCalendar(negotiation: Negotiation,
                                     timeInterval: LongRange,
                                     context: Context = BeReadyApp.instance) {
        if (negotiation.calendarEventId == null) {
            throw IllegalArgumentException("Negotiation field 'calendarEventId' cannot be null!")
        }

        val cr = context.contentResolver
        val values = ContentValues().apply {
            put(Events.DTSTART, timeInterval.start)
            put(Events.DTEND, timeInterval.endInclusive)
            put(Events.TITLE, negotiation.name)
            put(Events.DESCRIPTION, negotiation.info)
            put(Events.EVENT_LOCATION, negotiation.location)
        }

        val eventUri = ContentUris.withAppendedId(Events.CONTENT_URI, negotiation.calendarEventId!!)
        val rowsUpdatedAmount = cr.update(eventUri, values, null, null)
    }

    @SuppressLint("MissingPermission")
    fun deleteNegotiationInCalendar(negotiation: Negotiation,
                                    context: Context = BeReadyApp.instance) {
        if (negotiation.calendarEventId == null) {
            throw IllegalArgumentException("Negotiation field 'calendarEventId' cannot be null!")
        }

        val cr = context.contentResolver

        val eventUri = ContentUris.withAppendedId(Events.CONTENT_URI, negotiation.calendarEventId!!)
        val rowsDeletedAmount = cr.delete(eventUri, null, null)
    }

    @SuppressLint("MissingPermission")
    fun extractAvailableCalendars(context: Context = BeReadyApp.instance): List<Entity> {
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
                Events.CONTENT_URI, null, null, null, null
        )
        val calendarIterator = CalendarEntityHelper.newEntityIterator(cursor)
        return try {
            calendarIterator.asSequence().toList()
        } catch (e: Exception) {
            emptyList<Entity>()
        } finally {
            calendarIterator.close()
        }
    }

    @SuppressLint("MissingPermission")
    @Throws(IllegalArgumentException::class)
    fun getCalendarForEmail(email: String,
                            context: Context = BeReadyApp.instance): CalendarEntity? {
        val contentResolver = context.contentResolver
        val selection = (
                "((${Calendars.ACCOUNT_NAME } = ?) " +
                "AND (${Calendars.ACCOUNT_TYPE} = ?) " +
                "AND (${Calendars.OWNER_ACCOUNT} = ?))"
        )
        val selectionArgs = arrayOf(email, "com.google", email)

        val cursor = contentResolver.query(
                Calendars.CONTENT_URI, null,
                selection,
                selectionArgs, null
        )
        val calendarIterator = CalendarEntityHelper.newEntityIterator(cursor)
        return try {
            calendarIterator.asSequence().first()
        } catch (e: Exception) {
            null
        } finally {
            calendarIterator.close()
        }
    }

    // If it's supposed to handle multiuser situation,
    // add Google email picker dialog to select email
    @RequiresPermission(allOf = arrayOf(Manifest.permission.GET_ACCOUNTS, Manifest.permission.READ_CONTACTS))
    fun getFirstCreatedGoogleAccountEmail(context: Context = BeReadyApp.instance): String? {
        val accounts = AccountManager.get(context).getAccountsByType("com.google")
        return accounts.firstOrNull()?.name
    }
}