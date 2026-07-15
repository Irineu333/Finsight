package com.neoutils.finsight.firebase

import dev.gitlive.firebase.FirebaseOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Mapeia o conteúdo de um `google-services.json` para os [FirebaseOptions] do GitLive,
 * mantendo fonte única de configuração com o Android. Isolado para permitir unit test.
 */
internal object GoogleServicesParser {

    fun parse(json: String): FirebaseOptions {
        val root = Json.parseToJsonElement(json).jsonObject
        val projectInfo = root.getValue("project_info").jsonObject
        val client = root.getValue("client").jsonArray.first().jsonObject

        val applicationId = client.getValue("client_info").jsonObject
            .getValue("mobilesdk_app_id").jsonPrimitive.content
        val apiKey = client.getValue("api_key").jsonArray.first().jsonObject
            .getValue("current_key").jsonPrimitive.content

        return FirebaseOptions(
            applicationId = applicationId,
            apiKey = apiKey,
            projectId = projectInfo["project_id"]?.jsonPrimitive?.content,
            gcmSenderId = projectInfo["project_number"]?.jsonPrimitive?.content,
            storageBucket = projectInfo["storage_bucket"]?.jsonPrimitive?.content,
        )
    }
}
