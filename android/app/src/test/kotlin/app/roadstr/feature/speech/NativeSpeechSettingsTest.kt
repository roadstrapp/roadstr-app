package app.roadstr.feature.speech

import app.roadstr.feature.settings.NativeSettingsInput
import app.roadstr.feature.settings.NativeSettingsPresenter
import app.roadstr.feature.settings.NativeSettingsSpeechProblem
import app.roadstr.feature.settings.NativeSettingsSpeechStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeSpeechSettingsTest {
    private val base = NativeSettingsInput()

    @Test
    fun noGatewayAndUnsupportedDevicesShowNothing() {
        assertEquals(NativeSettingsSpeechStatus.Hidden, base.withSpeech(null).speechStatus)
        val unsupported = NativeSpeechSnapshot(model = NativeSpeechModelStatus.Unsupported)
        assertEquals(NativeSettingsSpeechStatus.Hidden, base.withSpeech(unsupported).speechStatus)
    }

    @Test
    fun everyGatewayStateHasASettingsCounterpart() {
        val expected = mapOf(
            NativeSpeechModelStatus.Checking to NativeSettingsSpeechStatus.Checking,
            NativeSpeechModelStatus.NotDownloaded to NativeSettingsSpeechStatus.NotDownloaded,
            NativeSpeechModelStatus.Downloading to NativeSettingsSpeechStatus.Downloading,
            NativeSpeechModelStatus.Ready to NativeSettingsSpeechStatus.Ready,
            NativeSpeechModelStatus.Failed to NativeSettingsSpeechStatus.Unavailable,
        )
        for ((model, status) in expected) {
            assertEquals(status, base.withSpeech(NativeSpeechSnapshot(model = model)).speechStatus)
        }
    }

    @Test
    fun progressProblemAndSizeAreCarriedOver() {
        val shown = base.withSpeech(
            NativeSpeechSnapshot(
                model = NativeSpeechModelStatus.Downloading,
                downloadFraction = 0.42,
                problem = NativeSpeechProblem.Network,
            ),
        )
        assertEquals(0.42, shown.speechDownloadProgress, 0.0)
        assertEquals(NativeSettingsSpeechProblem.Network, shown.speechProblem)
        assertEquals(485, shown.speechModelMegabytes)
    }

    @Test
    fun theSettingsSessionRejectsNonsenseFromTheSpeechFields() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeSettingsPresenter.present(1L, base.copy(speechDownloadProgress = 1.5))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeSettingsPresenter.present(1L, base.copy(speechModelMegabytes = -1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeSpeechSnapshot(downloadFraction = Double.NaN)
        }
    }
}
