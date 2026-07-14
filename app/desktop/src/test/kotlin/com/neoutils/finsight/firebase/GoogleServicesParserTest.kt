package com.neoutils.finsight.firebase

import kotlin.test.Test
import kotlin.test.assertEquals

class GoogleServicesParserTest {

    private val googleServicesJson = """
        {
          "project_info": {
            "project_number": "403461964525",
            "project_id": "finsight-81ae8",
            "storage_bucket": "finsight-81ae8.firebasestorage.app"
          },
          "client": [
            {
              "client_info": {
                "mobilesdk_app_id": "1:403461964525:android:cd5a8d2bbb1837cd4f42e4",
                "android_client_info": {
                  "package_name": "com.neoutils.finsight"
                }
              },
              "oauth_client": [],
              "api_key": [
                {
                  "current_key": "AIzaSyD-Qg1GVv5Gxzi3MGNmTBt4-q12PAauwjo"
                }
              ]
            }
          ],
          "configuration_version": "1"
        }
    """.trimIndent()

    @Test
    fun `maps google-services fields to firebase options`() {
        val options = GoogleServicesParser.parse(googleServicesJson)

        assertEquals("1:403461964525:android:cd5a8d2bbb1837cd4f42e4", options.applicationId)
        assertEquals("AIzaSyD-Qg1GVv5Gxzi3MGNmTBt4-q12PAauwjo", options.apiKey)
        assertEquals("finsight-81ae8", options.projectId)
        assertEquals("403461964525", options.gcmSenderId)
        assertEquals("finsight-81ae8.firebasestorage.app", options.storageBucket)
    }
}
