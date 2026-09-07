package net.myjda.payvi.data.api

import net.myjda.payvi.data.model.OrderResponse
import net.myjda.payvi.data.model.OrdersResponse
import net.myjda.payvi.data.model.StatusUpdateRequest
import net.myjda.payvi.data.model.StatusUpdateResponse
import net.myjda.payvi.data.model.VerifyResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * REST API exposed by the PAYVI Connector WordPress plugin
 * (see Payvi_Api::register_routes()). Paths are relative - no leading
 * slash - so they combine correctly with a Retrofit base URL that has no
 * path component (e.g. "https://myjda.net/").
 */
interface PayviApiService {

    @GET("wp-json/payvi/v1/verify")
    suspend fun verify(): VerifyResponse

    @GET("wp-json/payvi/v1/orders")
    suspend fun listOrders(
        @Query("since") since: Long,
        @Query("per_page") perPage: Int = 50
    ): OrdersResponse

    @GET("wp-json/payvi/v1/orders/{id}")
    suspend fun getOrder(@Path("id") id: Long): OrderResponse

    @POST("wp-json/payvi/v1/orders/{id}/status")
    suspend fun setOrderStatus(
        @Path("id") id: Long,
        @Body body: StatusUpdateRequest
    ): StatusUpdateResponse
}
