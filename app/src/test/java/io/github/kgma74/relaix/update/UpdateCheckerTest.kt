package io.github.kgma74.relaix.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun comparesVersionsNumberByNumber() {
        assertTrue(UpdateChecker.isNewer("1.1.0", "1.0"))
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.2"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.99.99"))
        assertFalse(UpdateChecker.isNewer("1.1.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("1.0.9", "1.1"))
        assertFalse(UpdateChecker.isNewer("", "1.0"))
    }
}
