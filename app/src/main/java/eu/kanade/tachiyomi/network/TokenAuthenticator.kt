package eu.kanade.tachiyomi.network

/*
class TokenAuthenticator(val loginHelper: MangaDexLoginHelper) :
    Authenticator {

    private val mutext = Mutex()
    val log = Timber.tag("||J-TokenAuthenticator")

    override fun authenticate(route: Route?, response: Response): Request? {
        log.i("Detected Auth error ${response.code} on ${response.request.url}")
        val token = refreshToken(loginHelper)
        return if (token.isEmpty()) {
            null
        } else {
            response.request.newBuilder().header("Authorization", token).build()
        }
    }

    fun refreshToken(loginHelper: MangaDexLoginHelper): String {
        var validated = false
        return runBlocking {
            mutext.withLock {
                val checkToken =
                    loginHelper.isAuthenticated()
                if (checkToken) {
                    log.i("Token is valid, other thread must have refreshed it")
                    validated = true
                }
                if (validated.not()) {
                    log.i("Token is invalid trying to refresh")
                    validated =
                        loginHelper.refreshToken()
                }

                if (validated.not()) {
                    log.i("Did not refresh token, trying to login")
                    validated = loginHelper.login()
                }
                return@runBlocking when {
                    validated -> "Bearer ${loginHelper.preferences.sessionToken()!!}"
                    else -> ""
                }
            }
        }
    }
}*/
