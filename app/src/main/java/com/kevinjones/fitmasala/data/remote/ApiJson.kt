package com.kevinjones.fitmasala.data.remote

import com.kevinjones.fitmasala.data.remote.dto.ResponseContentBlock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * The one JSON configuration every LLM request and response goes through.
 * Lives here rather than in the Hilt module so tests can serialize with exactly
 * what production sends.
 */
@OptIn(ExperimentalSerializationApi::class)
fun apiJson(): Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true

    /**
     * `ignoreUnknownKeys` covers unknown FIELDS. It does nothing for an
     * unknown polymorphic discriminator - a content block type added to the
     * API after this build would throw and take the whole response with it.
     * This maps anything unrecognised onto a block the app ignores.
     */
    serializersModule = SerializersModule {
        polymorphicDefaultDeserializer(ResponseContentBlock::class) {
            ResponseContentBlock.Unknown.serializer()
        }
    }
}
