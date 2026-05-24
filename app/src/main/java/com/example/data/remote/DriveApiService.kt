package com.example.data.remote

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface DriveApiService {

    @GET("files")
    suspend fun listFiles(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
        @Query("fields") fields: String = "files(id, name, mimeType, size)",
        @Query("pageSize") pageSize: Int = 100
    ): DriveFileListResponse

    @GET("files/{fileId}")
    @Streaming
    suspend fun downloadFile(
        @Header("Authorization") authorization: String,
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media"
    ): Response<ResponseBody>

    companion object {
        private const val BASE_URL = "https://www.googleapis.com/drive/v3/"

        fun create(): DriveApiService {
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
            return retrofit.create(DriveApiService::class.java)
        }
    }
}

@JsonClass(generateAdapter = true)
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: String? = null
)

@JsonClass(generateAdapter = true)
data class DriveFileListResponse(
    val files: List<DriveFile>
)
