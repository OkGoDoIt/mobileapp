package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.SBytes
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.SULong
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.structmapper.SUUID
import io.rebble.libpebblecommon.util.Endian
import kotlin.uuid.Uuid

/**
 * Private watch-app audio context protocol on endpoint 12000. Little endian.
 */
sealed class AppAudioContext(command: Command) : PebblePacket(ProtocolEndpoint.APP_AUDIO_CONTEXT) {
    val command = SUByte(m, command.value)
    val protocolVersion = SUByte(m, PROTOCOL_VERSION)
    val requestId = SUShort(m, endianness = Endian.Little)
    val appUuid = SUUID(m)
    val flags = SUInt(m, endianness = Endian.Little)

    init {
        type = command.value
    }

    class StatusRequest(
        requestId: UShort = 0u,
        appUuid: Uuid = Uuid.NIL,
        flags: UInt = 0u,
    ) : AppAudioContext(Command.StatusRequest) {
        init {
            this.requestId.set(requestId)
            this.appUuid.set(appUuid)
            this.flags.set(flags)
        }
    }

    class StatusResponse(
        requestId: UShort = 0u,
        appUuid: Uuid = Uuid.NIL,
        availabilityCode: UByte = 0u,
        backgroundAudioEnabledFlag: UByte = 0u,
        streamStateCode: UByte = 0u,
        transcriptionEnabledFlag: UByte = 0u,
        storageStateCode: UByte = 0u,
        liveSubscribers: UShort = 0u,
        rawSubscribers: UShort = 0u,
    ) : AppAudioContext(Command.StatusResponse) {
        val availability = SUByte(m, availabilityCode)
        val backgroundAudioEnabled = SUByte(m, backgroundAudioEnabledFlag)
        val streamState = SUByte(m, streamStateCode)
        val transcriptionEnabled = SUByte(m, transcriptionEnabledFlag)
        val storageState = SUByte(m, storageStateCode)
        val currentLiveSubscribers = SUShort(m, liveSubscribers, endianness = Endian.Little)
        val currentRawSubscribers = SUShort(m, rawSubscribers, endianness = Endian.Little)

        init {
            this.requestId.set(requestId)
            this.appUuid.set(appUuid)
        }
    }

    class EnablePromptRequest(
        requestId: UShort = 0u,
        appUuid: Uuid = Uuid.NIL,
    ) : AppAudioContext(Command.EnablePromptRequest) {
        init {
            this.requestId.set(requestId)
            this.appUuid.set(appUuid)
        }
    }

    class PromptResponse(
        requestId: UShort = 0u,
        appUuid: Uuid = Uuid.NIL,
        promptResult: UByte = 0u,
    ) : AppAudioContext(Command.PromptResponse) {
        val result = SUByte(m, promptResult)

        init {
            this.requestId.set(requestId)
            this.appUuid.set(appUuid)
        }
    }

    class PermissionRequest(
        requestId: UShort = 0u,
        appUuid: Uuid = Uuid.NIL,
        permissions: UByteArray = ubyteArrayOf(),
    ) : AppAudioContext(Command.PermissionRequest) {
        val permissionCount = SUByte(m, permissions.size.toUByte())
        val permissionBytes = SBytes(m, default = permissions, allRemainingBytes = true)

        init {
            this.requestId.set(requestId)
            this.appUuid.set(appUuid)
        }
    }

    class TranscriptRequest(
        requestId: UShort = 0u,
        appUuid: Uuid = Uuid.NIL,
        beforeSeconds: UShort = 60u,
        afterSeconds: UShort = 0u,
        anchorEpochMs: ULong = 0u,
        startedAtEpochMs: ULong = 0u,
        endedAtEpochMs: ULong = 0u,
        historyFlag: UByte = 0u,
    ) : AppAudioContext(Command.TranscriptRequest) {
        val beforeSeconds = SUShort(m, beforeSeconds, endianness = Endian.Little)
        val afterSeconds = SUShort(m, afterSeconds, endianness = Endian.Little)
        val anchorEpochMs = SULong(m, anchorEpochMs, endianness = Endian.Little)
        val startedAtEpochMs = SULong(m, startedAtEpochMs, endianness = Endian.Little)
        val endedAtEpochMs = SULong(m, endedAtEpochMs, endianness = Endian.Little)
        val history = SUByte(m, historyFlag)

        init {
            this.requestId.set(requestId)
            this.appUuid.set(appUuid)
        }
    }

    class TranscriptResponse(
        requestId: UShort = 0u,
        appUuid: Uuid = Uuid.NIL,
        responseStatus: UByte = 0u,
        partIndexValue: UShort = 0u,
        partCountValue: UShort = 1u,
        payload: UByteArray = ubyteArrayOf(),
    ) : AppAudioContext(Command.TranscriptResponse) {
        val status = SUByte(m, responseStatus)
        val partIndex = SUShort(m, partIndexValue, endianness = Endian.Little)
        val partCount = SUShort(m, partCountValue, endianness = Endian.Little)
        val payloadLength = SUShort(m, payload.size.toUShort(), endianness = Endian.Little)
        val payloadBytes = SBytes(m, default = payload, allRemainingBytes = true)

        init {
            this.requestId.set(requestId)
            this.appUuid.set(appUuid)
        }
    }

    class SubscribeRequest(
        requestId: UShort = 0u,
        appUuid: Uuid = Uuid.NIL,
        includePartialFlag: UByte = 1u,
        startedAtEpochMsValue: ULong = 0u,
    ) : AppAudioContext(Command.SubscribeRequest) {
        val includePartial = SUByte(m, includePartialFlag)
        val startedAtEpochMs = SULong(m, startedAtEpochMsValue, endianness = Endian.Little)

        init {
            this.requestId.set(requestId)
            this.appUuid.set(appUuid)
        }
    }

    class Event(
        requestId: UShort = 0u,
        appUuid: Uuid = Uuid.NIL,
        payload: UByteArray = ubyteArrayOf(),
    ) : AppAudioContext(Command.Event) {
        val payloadLength = SUShort(m, payload.size.toUShort(), endianness = Endian.Little)
        val payloadBytes = SBytes(m, default = payload, allRemainingBytes = true)

        init {
            this.requestId.set(requestId)
            this.appUuid.set(appUuid)
        }
    }

    class CancelRequest(
        requestId: UShort = 0u,
        appUuid: Uuid = Uuid.NIL,
    ) : AppAudioContext(Command.CancelRequest) {
        init {
            this.requestId.set(requestId)
            this.appUuid.set(appUuid)
        }
    }

    class ErrorResponse(
        requestId: UShort = 0u,
        appUuid: Uuid = Uuid.NIL,
        errorCodeValue: UByte = 0u,
        message: String = "",
    ) : AppAudioContext(Command.ErrorResponse) {
        val errorCode = SUByte(m, errorCodeValue)
        val messageLength = SUShort(m, message.encodeToByteArray().size.toUShort(), endianness = Endian.Little)
        val messageBytes = SBytes(
            mapper = m,
            default = message.encodeToByteArray().toUByteArray(),
            allRemainingBytes = true,
        )

        init {
            this.requestId.set(requestId)
            this.appUuid.set(appUuid)
        }
    }

    enum class Command(val value: UByte) {
        StatusRequest(0x01u),
        StatusResponse(0x02u),
        EnablePromptRequest(0x03u),
        PromptResponse(0x04u),
        PermissionRequest(0x05u),
        TranscriptRequest(0x06u),
        TranscriptResponse(0x07u),
        SubscribeRequest(0x08u),
        Event(0x09u),
        CancelRequest(0x0au),
        ErrorResponse(0x0bu),
    }

    enum class ErrorCode(val value: UByte) {
        Unavailable(0u),
        PermissionDenied(1u),
        CapabilityNotDeclared(2u),
        BackgroundAudioDisabled(3u),
        TranscriptionUnavailable(4u),
        NoData(5u),
        ResponseTooLarge(6u),
        InternalError(7u),
    }

    enum class WirePermission(val value: UByte) {
        Status(1u),
        RecentTranscript(2u),
        TranscriptHistory(3u),
        LiveTranscript(4u),
        RawAudio(5u),
    }

    enum class StreamStateCode(val value: UByte) {
        Unknown(0u),
        Idle(1u),
        Receiving(2u),
    }

    enum class StorageStateCode(val value: UByte) {
        Unknown(0u),
        Accepting(1u),
        Paused(2u),
        Full(3u),
    }

    companion object {
        const val PROTOCOL_VERSION: UByte = 1u
        const val MAX_TRANSCRIPT_CHUNK_BYTES: Int = 3500
    }
}

fun appAudioContextPacketsRegister() {
    AppAudioContext.Command.entries.forEach { command ->
        PacketRegistry.register(ProtocolEndpoint.APP_AUDIO_CONTEXT, command.value) {
            when (command) {
                AppAudioContext.Command.StatusRequest -> AppAudioContext.StatusRequest()
                AppAudioContext.Command.StatusResponse -> AppAudioContext.StatusResponse()
                AppAudioContext.Command.EnablePromptRequest -> AppAudioContext.EnablePromptRequest()
                AppAudioContext.Command.PromptResponse -> AppAudioContext.PromptResponse()
                AppAudioContext.Command.PermissionRequest -> AppAudioContext.PermissionRequest()
                AppAudioContext.Command.TranscriptRequest -> AppAudioContext.TranscriptRequest()
                AppAudioContext.Command.TranscriptResponse -> AppAudioContext.TranscriptResponse()
                AppAudioContext.Command.SubscribeRequest -> AppAudioContext.SubscribeRequest()
                AppAudioContext.Command.Event -> AppAudioContext.Event()
                AppAudioContext.Command.CancelRequest -> AppAudioContext.CancelRequest()
                AppAudioContext.Command.ErrorResponse -> AppAudioContext.ErrorResponse()
            }
        }
    }
}
