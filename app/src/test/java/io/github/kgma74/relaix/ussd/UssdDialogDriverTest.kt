package io.github.kgma74.relaix.ussd

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UssdDialogDriverTest {

    /** Short enough to keep the test quick, long enough not to fire by itself. */
    private fun driver() = UssdDialogDriver().apply {
        waits = UssdDialogDriver.Waits(answerMillis = 500, typeMillis = 300, settleMillis = 100)
    }

    private suspend fun UssdDialogDriver.settle() {
        // Lets await() consume what was just pushed before the next step.
        repeat(3) { yield() }
        delay(10)
    }

    @Test
    fun `answers every screen in order and finishes on the last one`() = runBlocking {
        val driver = driver()
        assertTrue(driver.begin(listOf("2", "1", "1234")))
        val menu = async { driver.await() }

        assertTrue(driver.screen("Menu principal\n1. Solde"))
        assertEquals("2", driver.replyToType())
        driver.dialogSeen("com.android.phone")
        driver.replySent()
        driver.settle()

        assertTrue(driver.screen("1. Envoyer de l'argent"))
        assertEquals("1", driver.replyToType())
        driver.replySent()
        driver.settle()

        // The same screen reported from another direction is not news.
        assertFalse(driver.screen("1. Envoyer de l'argent"))
        assertTrue(driver.screen("Entrez votre code PIN"))
        assertEquals("1234", driver.replyToType())
        driver.replySent()
        driver.settle()

        assertTrue(driver.screen("Transfert réussi. Ref: CI123"))
        val outcome = menu.await()

        assertTrue(outcome.errorMessage, outcome.success)
        assertEquals(
            listOf(
                "Menu principal 1. Solde",
                "1. Envoyer de l'argent",
                "Entrez votre code PIN",
                "Transfert réussi. Ref: CI123",
            ),
            outcome.responses,
        )
        assertNull(driver.replyToType())
        driver.end()
    }

    @Test
    fun `refuses a reply while one is with the network`() = runBlocking {
        val driver = driver()
        driver.begin(listOf("1"))
        val menu = async { driver.await() }

        driver.screen("Menu")
        assertEquals("1", driver.replyToType())
        driver.replySent()
        // A second event on the same screen must not type the next reply —
        // there is none — nor the same one twice.
        driver.screen("Menu")
        assertNull(driver.replyToType())

        driver.screen("Réponse du réseau")
        val outcome = menu.await()
        assertTrue(outcome.success)
        assertEquals(listOf("Menu", "Réponse du réseau"), outcome.responses)
        driver.end()
    }

    @Test
    fun `says a dialog could not be typed into instead of pretending`() = runBlocking {
        val driver = driver()
        driver.begin(listOf("1"))
        val menu = async { driver.await() }

        // The screen is read — the callback got it — but no dialog with an
        // input field was ever seen: nothing will type into anything.
        driver.screen("Menu")
        val outcome = menu.await()

        assertFalse(outcome.success)
        assertEquals("USSD_MENU_UNANSWERED", outcome.errorCode)
        assertTrue(outcome.errorMessage, outcome.errorMessage.contains("input field"))
        driver.end()
    }

    @Test
    fun `names the package of a dialog it could not answer`() = runBlocking {
        val driver = driver()
        driver.begin(listOf("1"))
        val menu = async { driver.await() }

        driver.screen("Menu")
        driver.dialogSeen("com.oem.unknown")
        val outcome = menu.await()

        assertEquals("USSD_MENU_UNANSWERED", outcome.errorCode)
        assertTrue(outcome.errorMessage, outcome.errorMessage.contains("com.oem.unknown"))
        driver.end()
    }

    @Test
    fun `a failed dial ends the menu at once`() = runBlocking {
        val driver = driver()
        driver.begin(listOf("1"))
        val menu = async { driver.await() }

        driver.dialFailed("USSD_FAILED", "the network refused or could not run the code")
        val outcome = menu.await()

        assertFalse(outcome.success)
        assertEquals("USSD_FAILED", outcome.errorCode)
        assertEquals("the network refused or could not run the code", outcome.errorMessage)
        driver.end()
    }

    @Test
    fun `the service going away fails what was running`() = runBlocking {
        val driver = driver()
        assertTrue(driver.enabled.value.not())
        driver.attach()
        assertTrue(driver.enabled.value)
        driver.begin(listOf("1"))
        val menu = async { driver.await() }

        driver.screen("Menu")
        driver.detach()

        val outcome = menu.await()
        assertFalse(outcome.success)
        assertEquals("USSD_ACCESSIBILITY_DISABLED", outcome.errorCode)
        assertFalse(driver.enabled.value)
        driver.end()
    }

    @Test
    fun `one menu at a time, and nothing remembered after it`() = runBlocking {
        val driver = driver()
        assertTrue(driver.begin(listOf("1")))
        assertFalse(driver.begin(listOf("2")))

        driver.end()
        assertNull(driver.replyToType())
        assertFalse(driver.screen("a screen nobody asked for"))
        assertTrue(driver.begin(listOf("2")))
        assertNull(driver.replyToType()) // the first session's reply is gone
        driver.end()
    }

    @Test
    fun `the service reads nothing outside a session`() {
        val driver = driver()
        driver.attach()
        // Bound is not running: a window event now must not read a node.
        assertFalse(driver.active())
        assertFalse(driver.acceptsFinalScreen())
        driver.begin(listOf("1"))
        assertTrue(driver.active())
        driver.end()
        assertFalse(driver.active())
    }

    @Test
    fun `a screen without a field counts only while something is with the network`() {
        val driver = driver()
        driver.begin(listOf("1"))
        // The code is out: the network may answer with a notice and no menu.
        assertTrue(driver.acceptsFinalScreen())
        driver.screen("Menu")
        // The menu is up and waiting on us: an OK-only window is not news.
        assertFalse(driver.acceptsFinalScreen())
        driver.replySent()
        assertTrue(driver.acceptsFinalScreen())
        driver.end()
    }
}
