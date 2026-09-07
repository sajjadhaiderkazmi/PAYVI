package net.myjda.payvi.data.repo

import net.myjda.payvi.data.api.ApiClientFactory
import net.myjda.payvi.data.model.PairingPayload
import net.myjda.payvi.data.prefs.PayviPrefs
import retrofit2.HttpException
import java.io.IOException

sealed class PairingResult {
    data class Success(val siteName: String) : PairingResult()
    data class Unauthorized(val message: String) : PairingResult()
    data class NetworkError(val message: String) : PairingResult()
    data class OtherError(val message: String) : PairingResult()
}

class PairingRepository(private val prefs: PayviPrefs) {

    /** Verifies the pairing details against the store's /verify endpoint,
     * and only saves them locally once the server confirms they work. */
    suspend fun verifyAndSave(payload: PairingPayload): PairingResult {
        return try {
            val api = ApiClientFactory.create(payload.site, payload.key, payload.secret)
            val response = api.verify()

            if (!response.success) {
                return PairingResult.OtherError("Store responded but reported an error.")
            }

            prefs.applyPairing(payload)
            val siteName = response.siteName ?: payload.site
            prefs.siteName = siteName

            PairingResult.Success(siteName)
        } catch (e: HttpException) {
            if (e.code() == 401) {
                PairingResult.Unauthorized("Invalid API key/secret.")
            } else {
                PairingResult.OtherError("Store returned an error (HTTP ${e.code()}).")
            }
        } catch (e: IOException) {
            PairingResult.NetworkError(e.message ?: "Network error.")
        } catch (e: Exception) {
            PairingResult.OtherError(e.message ?: "Unexpected error.")
        }
    }

    fun disconnect() {
        prefs.clearPairing()
    }
}
