package com.cuteghost.app

/** Feature switches and settings. */
object Features {
    /** Voice conversation (speech to text -> AI -> text to speech). Disabled for now. */
    const val VOICE_ENABLED = false

    /**
     * Time while the ghost is hidden (app closed) does not count toward hunger, thirst or the
     * 24-hour limit, so a ghost never gets sick just because nobody could take care of it.
     */
    const val PAUSE_WHILE_HIDDEN = true

    /** Phone time at which the ghosts go to bed (hour of the day, 0 = midnight) and wake up. */
    const val BEDTIME_START_HOUR = 0
    const val BEDTIME_END_HOUR = 6

    /** From this hour in the evening the ghosts look sleepy. */
    const val SLEEPY_EVENING_HOUR = 21
}
