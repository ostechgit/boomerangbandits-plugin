package com.boomerangbandits;

import com.boomerangbandits.api.AuthHeaderInterceptor;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.io.File;
import okhttp3.Cache;
import okhttp3.OkHttpClient;

import javax.inject.Named;

/**
 * Guice module for the Boomerang Bandits plugin.
 * <p>
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

    @Provides
    @Singleton
    @Named("boomerangWom")
    OkHttpClient provideBoomerangWomHttpClient(@Named("boomerang") OkHttpClient base) {
        File cacheDir = new File(new File(System.getProperty("user.home"), ".runelite"), "boomerang-bandits-http-cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        return base.newBuilder()
                .cache(new Cache(cacheDir, 2L * 1024L * 1024L))
                .build();
    }
}
