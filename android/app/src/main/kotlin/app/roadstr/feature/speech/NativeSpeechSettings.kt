package app.roadstr.feature.speech

import app.roadstr.core.speech.SpeechModelCatalog
import app.roadstr.feature.settings.NativeSettingsInput
import app.roadstr.feature.settings.NativeSettingsSpeechProblem
import app.roadstr.feature.settings.NativeSettingsSpeechStatus

/** Shows the gateway's offline speech state in Settings. A null gateway hides the section. */
fun NativeSettingsInput.withSpeech(snapshot: NativeSpeechSnapshot?): NativeSettingsInput {
    if (snapshot == null) return copy(speechStatus = NativeSettingsSpeechStatus.Hidden)
    return copy(
        speechStatus = when (snapshot.model) {
            NativeSpeechModelStatus.Unsupported -> NativeSettingsSpeechStatus.Hidden
            NativeSpeechModelStatus.Checking -> NativeSettingsSpeechStatus.Checking
            NativeSpeechModelStatus.NotDownloaded -> NativeSettingsSpeechStatus.NotDownloaded
            NativeSpeechModelStatus.Downloading -> NativeSettingsSpeechStatus.Downloading
            NativeSpeechModelStatus.Ready -> NativeSettingsSpeechStatus.Ready
            NativeSpeechModelStatus.Failed -> NativeSettingsSpeechStatus.Unavailable
        },
        speechDownloadProgress = snapshot.downloadFraction,
        speechProblem = when (snapshot.problem) {
            NativeSpeechProblem.Network -> NativeSettingsSpeechProblem.Network
            NativeSpeechProblem.Storage -> NativeSettingsSpeechProblem.Storage
            NativeSpeechProblem.Integrity -> NativeSettingsSpeechProblem.Integrity
            NativeSpeechProblem.EngineUnavailable -> NativeSettingsSpeechProblem.EngineUnavailable
            null -> null
        },
        speechModelMegabytes = (SpeechModelCatalog.default.byteSize / 1_000_000L).toInt(),
    )
}
