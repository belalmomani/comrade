package com.cuteghost.app

/** Feature switches. */
object Features {
    /** Voice conversation (speech to text -> AI -> text to speech). Disabled for now. */
    const val VOICE_ENABLED = false

    /**
     * Time while the ghost is hidden (app closed) does not count toward hunger, thirst or the
     * 24-hour limit, so a ghost never gets sick just because nobody could take care of it.
     */
    const val PAUSE_WHILE_HIDDEN = true
}
