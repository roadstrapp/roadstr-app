package app.roadstr.service.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNavigationNotificationRuntimeTest {
    @Test
    fun `dispatcher delivers commands only while the service is attached`() {
        val commands = mutableListOf<NativeNavigationNotificationCommand>()
        assertFalse(
            NativeNavigationNotificationDispatcher.dispatch(
                NativeNavigationNotificationCommand.Reset,
            ),
        )
        val subscription = NativeNavigationNotificationDispatcher.attach(commands::add)

        try {
            assertTrue(
                NativeNavigationNotificationDispatcher.dispatch(
                    NativeNavigationNotificationCommand.Update("Turn right", "200 m"),
                ),
            )
            assertTrue(
                NativeNavigationNotificationDispatcher.dispatch(
                    NativeNavigationNotificationCommand.Reset,
                ),
            )
            assertEquals(2, commands.size)
        } finally {
            subscription.cancel()
        }

        assertFalse(
            NativeNavigationNotificationDispatcher.dispatch(
                NativeNavigationNotificationCommand.Reset,
            ),
        )
    }

    @Test
    fun `stale cancellation cannot detach a replacement service`() {
        val first = NativeNavigationNotificationDispatcher.attach {}
        val replacementCommands = mutableListOf<NativeNavigationNotificationCommand>()
        val replacement =
            NativeNavigationNotificationDispatcher.attach(replacementCommands::add)

        try {
            first.cancel()
            assertTrue(
                NativeNavigationNotificationDispatcher.dispatch(
                    NativeNavigationNotificationCommand.Reset,
                ),
            )
            assertEquals(
                listOf(NativeNavigationNotificationCommand.Reset),
                replacementCommands,
            )
        } finally {
            first.cancel()
            replacement.cancel()
        }
    }
}
