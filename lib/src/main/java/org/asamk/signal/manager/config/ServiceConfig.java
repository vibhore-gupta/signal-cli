package org.asamk.signal.manager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.signalservice.api.account.AccountAttributes;
import org.whispersystems.signalservice.api.push.TrustStore;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;

import okhttp3.Interceptor;

public class ServiceConfig {

    private final static Logger logger = LoggerFactory.getLogger(ServiceConfig.class);

    public final static int PREKEY_MINIMUM_COUNT = 20;
    public final static int PREKEY_BATCH_SIZE = 100;
    public final static int MAX_ATTACHMENT_SIZE = 150 * 1024 * 1024;
    public final static long MAX_ENVELOPE_SIZE = 0;
    public final static long AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE = 10 * 1024 * 1024;
    public final static boolean AUTOMATIC_NETWORK_RETRY = true;
    public final static int GROUP_MAX_SIZE = 1001;

    private final static KeyStore iasKeyStore;

    public static final AccountAttributes.Capabilities capabilities;

    static {
        capabilities = new AccountAttributes.Capabilities(false,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                false);

        try {
            TrustStore contactTrustStore = new IasTrustStore();

            var keyStore = KeyStore.getInstance("BKS");
            keyStore.load(contactTrustStore.getKeyStoreInputStream(),
                    contactTrustStore.getKeyStorePassword().toCharArray());

            iasKeyStore = keyStore;
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    public static boolean isSignalClientAvailable() {
        try {
            try {
                org.signal.libsignal.internal.Native.UuidCiphertext_CheckValidContents(new byte[0]);
            } catch (Exception e) {
                logger.trace("Expected exception when checking libsignal-client: {}", e.getMessage());
            }
            return true;
        } catch (UnsatisfiedLinkError e) {
            logger.warn("Failed to call libsignal-client: {}", e.getMessage());
            return false;
        }
    }

    public static KeyStore getIasKeyStore() {
        return iasKeyStore;
    }

    public static ServiceEnvironmentConfig getServiceEnvironmentConfig(
            ServiceEnvironment serviceEnvironment, String userAgent
    ) {
        final Interceptor userAgentInterceptor = chain -> chain.proceed(chain.request()
                .newBuilder()
                .header("User-Agent", userAgent)
                .build());

        final var interceptors = List.of(userAgentInterceptor);

        return switch (serviceEnvironment) {
            case LIVE -> new ServiceEnvironmentConfig(serviceEnvironment,
                    LiveConfig.createDefaultServiceConfiguration(interceptors),
                    LiveConfig.getUnidentifiedSenderTrustRoot(),
                    LiveConfig.createKeyBackupConfig(),
                    LiveConfig.createFallbackKeyBackupConfigs(),
                    LiveConfig.getCdsMrenclave(),
                    LiveConfig.getCdsiMrenclave());
            case STAGING -> new ServiceEnvironmentConfig(serviceEnvironment,
                    StagingConfig.createDefaultServiceConfiguration(interceptors),
                    StagingConfig.getUnidentifiedSenderTrustRoot(),
                    StagingConfig.createKeyBackupConfig(),
                    StagingConfig.createFallbackKeyBackupConfigs(),
                    StagingConfig.getCdsMrenclave(),
                    StagingConfig.getCdsiMrenclave());
        };
    }
}
