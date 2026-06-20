package za.co.statecapture.android.data.network

import retrofit2.http.GET
import retrofit2.http.Path
import za.co.statecapture.android.domain.model.IndexResponse
import za.co.statecapture.android.domain.model.TariffProviderFile

interface TariffApi {
    @GET("index.json")
    suspend fun getIndex(): IndexResponse

    @GET("{path}")
    suspend fun getProviderFile(@Path(value = "path", encoded = true) path: String): TariffProviderFile
}
