package eu.kanade.tachiyomi.util

import com.skydoves.sandwich.ApiResponse
import timber.log.Timber

fun ApiResponse<*>.log(type: String) {
    when (this) {
        is ApiResponse.Failure.Exception -> {
            Timber.e(this.exception, "Exception $type ${this.message}")
        }
        is ApiResponse.Failure.Error -> {
            Timber.e("error $type ${this.errorBody?.string()}")
            Timber.e("error response code ${this.statusCode.code}")
        }
        else -> {
            Timber.e("error $type")
        }
    }
}

fun ApiResponse<*>.throws(type: String) {
    when (this) {
        is ApiResponse.Failure.Error -> {
            throw Exception("Error $type http code: ${this.statusCode.code}")
        }
        is ApiResponse.Failure.Exception -> {
            throw Exception("Error $type ${this.message} ${this.exception}")
        }
        else -> {
            throw Exception("Error $type ")
        }
    }
}
