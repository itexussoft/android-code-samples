package com.ready.be.beready.persistence.entity

import android.text.format.DateUtils
import com.ready.be.beready.utils.hours
import com.ready.be.beready.utils.minutes
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import java.util.*

/**
 * Created by denis on 1/22/18.
 */
open class Negotiation(
        @PrimaryKey @Index var id: String = "",
        var itemNumber: Long = 1,
        var name: String = "",
        var date: Date? = null,
        var time: Date? = null,
        var location: String = "",
        var info: String = "",
        var isDraft: Boolean = true,
        var isArchived: Boolean = false,
        var initialized: Boolean = false,
        var currentWizardStep: Int = 0,
        var isCompleted: Boolean = false,
        var ourParticipants: RealmList<Participant> = RealmList(),
        var theirParticipants: RealmList<Participant> = RealmList(),
        var ourInterest: RealmList<Interest> = RealmList(),
        var theirInterest: RealmList<Interest> = RealmList(),
        var usePositionText: Boolean = false,
        var positionStrength: String = "",
        var positionWeakness: String = "",
        var ourPressure: String = "",
        var theirsPressure: String = "",
        var ourPlanB: String = "",
        var theirPlanB: String = "",
        var position: Position = Position(),
        var extraAgreement: String = "",
        var isStateInterests: Boolean = true,
        var nextStep: String = "",
        var attachments: RealmList<AttachmentEntry> = RealmList(),
        var calendarEventId: Long? = null // Id, that corresponds to Google Calendar Event Id
) : RealmObject() {

    /** Date, that combines date and time fields */
    val combinedDate: Date?
        get() {
            return if (date == null || time == null) {
                null
            } else {
                val timeCalendar = Calendar.getInstance().also {
                    it.time = time
                }
                val dateCalendar = Calendar.getInstance().also {
                    it.time = date
                }
                dateCalendar.apply {
                    hours = timeCalendar.hours
                    minutes = timeCalendar.minutes
                }.time
            }
        }

    val timeInterval: LongRange?
        get() = when {
            combinedDate != null -> {
                val startTime = combinedDate!!.time
                startTime..(startTime + DateUtils.HOUR_IN_MILLIS)
            }
            date != null -> {
                val startTime = date!!.time
                startTime..(startTime + DateUtils.DAY_IN_MILLIS)
            }
            else -> null
        }

    fun copyWithNewId(): Negotiation {
        var negotiation = Negotiation(UUID.randomUUID().toString())
        negotiation.name = name
        negotiation.itemNumber = itemNumber
        negotiation.date = date
        negotiation.time = time
        negotiation.location = location
        negotiation.info = info
        negotiation.isDraft = isDraft
        negotiation.isArchived = isArchived
        negotiation.initialized = initialized
        negotiation.isCompleted = isCompleted
        negotiation.currentWizardStep = currentWizardStep
        negotiation.ourParticipants = ourParticipants
        negotiation.theirParticipants = theirParticipants
        negotiation.ourInterest = ourInterest
        negotiation.theirInterest = theirInterest
        negotiation.usePositionText = usePositionText
        negotiation.positionStrength = positionStrength
        negotiation.positionWeakness = positionWeakness
        negotiation.ourPressure = ourPressure
        negotiation.theirsPressure = theirsPressure
        negotiation.ourPlanB = ourPlanB
        negotiation.theirPlanB = theirPlanB
        negotiation.position = position
        negotiation.extraAgreement = extraAgreement
        negotiation.isStateInterests = isStateInterests
        negotiation.nextStep = nextStep
        negotiation.attachments = attachments
        return negotiation
    }
}