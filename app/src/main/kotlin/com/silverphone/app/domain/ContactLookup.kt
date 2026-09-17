package com.silverphone.app.domain

/**
 * Reads one contact for the dial path.
 *
 * The dial flow re-reads the contact immediately before dispatching rather than
 * trusting the copy the card was drawn from, so a contact deleted or edited in
 * between cannot produce a call to stale data.
 */
fun interface ContactLookup {
    suspend fun findContact(id: String): Contact?
}
