package com.boomerangbandits;

import com.boomerangbandits.api.AuthHeaderInterceptor;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import javax.inject.Named;
import okhttp3.OkHttpClient;

/**
 * Guice module for the Boomerang Bandits plugin.
 *
 * Provides a plugin-scoped {@link OkHttpClient} that wraps RuneLite's shared
 * client with {@link AuthHeaderInterceptor}. All plugin services should inject
 * {@code @Named("boomerang") OkHttpClient} so auth headers are added automatically.
 */
public class BoomerangBanditsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(AuthHeaderInterceptor.class).in(Singleton.class);
    }

    @Provides
    @Singleton
    @Named("boomerang")
    OkHttpClient provideBoomerangHttpClient(OkHttpClient base, AuthHeaderInterceptor interceptor) {
        return base.newBuilder()
            .addInterceptor(interceptor)
            .build();
    }
}
