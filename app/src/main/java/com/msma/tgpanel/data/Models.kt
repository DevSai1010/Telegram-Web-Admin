package com.msma.tgpanel.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** The server is loose about ids (string or number, sometimes null) — read any of them as a string. */
object FlexString : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexString", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String {
        val jd = decoder as? JsonDecoder ?: return decoder.decodeString()
        val el = jd.decodeJsonElement()
        return if (el is JsonPrimitive && el !is JsonNull) el.content else ""
    }
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

val AppJson = Json {
    ignoreUnknownKeys = true      // the API may add fields (docs/API.md: "ignore unknown fields")
    coerceInputValues = true
    isLenient = true
}

@Serializable
data class ChatDto(
    @Serializable(with = FlexString::class) val id: String = "",
    @Serializable(with = FlexString::class) val title: String = "",
    @Serializable(with = FlexString::class) val last_text: String = "",
    @Serializable(with = FlexString::class) val last_sender: String = "",
    @Serializable(with = FlexString::class) val time: String = "",
    val is_group: Boolean = false,
    @Serializable(with = FlexString::class) val business_connection_id: String = "",
    val has_bot_chat: Boolean = false,
    val muted: Boolean = false,
    val pinned: Boolean = false,
    val ai_pending: Boolean = false,
)

@Serializable
data class MessageDto(
    @Serializable(with = FlexString::class) val id: String = "",
    @Serializable(with = FlexString::class) val msg_id: String = "",
    @Serializable(with = FlexString::class) val role: String = "user",
    @Serializable(with = FlexString::class) val text: String = "",
    @Serializable(with = FlexString::class) val sender: String = "",
    @Serializable(with = FlexString::class) val time: String = "",
    val ts: Long = 0,
    @Serializable(with = FlexString::class) val type: String = "",
    @Serializable(with = FlexString::class) val file_id: String = "",
    @Serializable(with = FlexString::class) val reply_to_msg_id: String = "",
    @Serializable(with = FlexString::class) val reply_to_preview: String = "",
    @Serializable(with = FlexString::class) val forwarded_from: String = "",
    @Serializable(with = FlexString::class) val my_reaction: String = "",
    @Serializable(with = FlexString::class) val their_reaction: String = "",
)

@Serializable
data class LoginResp(
    val token: String = "",
    @Serializable(with = FlexString::class) val username: String = "",
    val is_owner: Boolean = false,
    val must_change: Boolean = false,
    @Serializable(with = FlexString::class) val error: String = "",
    val suspended: Boolean = false,
    val retry_after: Int = 0,
)

@Serializable
data class VersionDto(val api: Int = 0, val min_client: Int = 0)

@Serializable
data class SyncDto(
    @Serializable(with = FlexString::class) val cursor: String = "",
    val reset: Boolean = false,
    val index: Boolean = false,
    val chats: List<@Serializable(with = FlexString::class) String> = emptyList(),
)

@Serializable
data class DeviceDto(
    @Serializable(with = FlexString::class) val name: String = "",
    @Serializable(with = FlexString::class) val platform: String = "",
    @Serializable(with = FlexString::class) val app: String = "",
)

@Serializable
data class SessionDto(
    @Serializable(with = FlexString::class) val id: String = "",
    val device: DeviceDto = DeviceDto(),
    val created: Long = 0,
    val last_seen: Long = 0,
    val current: Boolean = false,
)

@Serializable
data class ApiAck(
    val success: Boolean = false,
    @Serializable(with = FlexString::class) val error: String = "",
    @Serializable(with = FlexString::class) val name: String = "",
    @Serializable(with = FlexString::class) val message: String = "",
    @Serializable(with = FlexString::class) val resolvedChatId: String = "",
)

@Serializable
data class ProfileDto(
    @Serializable(with = FlexString::class) val id: String = "",
    @Serializable(with = FlexString::class) val first_name: String = "",
    @Serializable(with = FlexString::class) val last_name: String = "",
    @Serializable(with = FlexString::class) val username: String = "",
    @Serializable(with = FlexString::class) val bio: String = "",
    val limited: Boolean = false,
    @Serializable(with = FlexString::class) val limited_reason: String = "",
    @Serializable(with = FlexString::class) val error: String = "",
)

@Serializable
data class MediaItemDto(
    @Serializable(with = FlexString::class) val file_id: String = "",
    @Serializable(with = FlexString::class) val id: String = "",
    @Serializable(with = FlexString::class) val time: String = "",
    @Serializable(with = FlexString::class) val text: String = "",
)

@Serializable
data class ChatMediaDto(
    val photo: List<MediaItemDto> = emptyList(),
    val video: List<MediaItemDto> = emptyList(),
    val voice: List<MediaItemDto> = emptyList(),
    val document: List<MediaItemDto> = emptyList(),
)

@Serializable
data class ChatCfgDto(
    val ai_mode: Boolean = true,
    @Serializable(with = FlexString::class) val system_prompt: String = "",
)

@Serializable
data class StickerDto(
    @Serializable(with = FlexString::class) val file_id: String = "",
    @Serializable(with = FlexString::class) val emoji: String = "",
)

@Serializable
data class StickerSetDto(
    val stickers: List<StickerDto> = emptyList(),
    @Serializable(with = FlexString::class) val error: String = "",
)

@Serializable
data class SettingsDto(
    @Serializable(with = FlexString::class) val sticker_packs: String = "",
)

@Serializable
data class WsFrame(
    @Serializable(with = FlexString::class) val t: String = "",
    @Serializable(with = FlexString::class) val cursor: String = "",
)
