package com.boomerangbandits.api;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Singleton;
import java.io.IOException;

/**
 * OkHttp interceptor that injects auth headers on every outgoing request
 * to the Boomerang Bandits backend.
 * <p>
 * Headers injected (when set):
 * X-Member-Code  — identifies the member
 * X-Auth-Token   — HMAC-SHA256(memberCode, accountHash), proves ownership
 * X-Account-Hash — current OSRS account hash, required for alt-account support
 * <p>
 * Call {@link #setCredentials} after successful authentication and
 * {@link #clearCredentials} on logout.
 */
@Slf4j
@Singleton
public class AuthHeaderInterceptor implements Interceptor {

    private volatile String memberCode = null;
    private volatile String authToken = null;
    private volatile long accountHash = -1;

    public void setCredentials(String memberCode, String authToken, long accountHash) {
        this.memberCode = memberCode;
        this.authToken = authToken;
        this.accountHash = accountHash;
    }

    public void clearCredentials() {
        this.memberCode = null;
        this.authToken = null;
        this.accountHash = -1;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();

        // Only inject on requests to our backend
        String url = original.url().toString();
        if (!url.contains(ApiConstants.BACKEND_BASE_URL)) {
            return chain.proceed(original);
        }

        Request.Builder builder = original.newBuilder();

        if (memberCode != null && !memberCode.isEmpty()) {
            builder.header("X-Member-Code", memberCode);
        }
        if (authToken != null && !authToken.isEmpty()) {
            builder.header("X-Auth-Token", authToken);
        }
        if (accountHash != -1) {
            builder.header("X-Account-Hash", String.valueOf(accountHash));
        }

        return chain.proceed(builder.build());
    }
}
