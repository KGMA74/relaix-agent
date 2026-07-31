package io.github.kgma74.relaix.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.kgma74.relaix.security.KeyStoreSecretCipher
import io.github.kgma74.relaix.security.SecretCipher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    /**
     * Bound as an interface so a test can swap in a fake cipher: the
     * AndroidKeyStore does not exist off-device.
     */
    @Binds
    @Singleton
    abstract fun bindSecretCipher(impl: KeyStoreSecretCipher): SecretCipher
}
