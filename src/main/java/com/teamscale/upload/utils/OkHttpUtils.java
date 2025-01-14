package com.teamscale.upload.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

/**
 * Utilities for creating an {@link OkHttpClient}
 */
public class OkHttpUtils {

	/**
	 * An empty request body that can be reused.
	 */
	public static final RequestBody EMPTY_BODY = RequestBody.create(null, new byte[0]);

	/**
	 * Creates the {@link OkHttpClient} based on the given connection settings.
	 *
	 * @param trustStorePath
	 *            May be null if no trust store should be used.
	 * @param trustStorePassword
	 *            May be null if no trust store should be used.
	 */
	public static OkHttpClient createClient(boolean validateSsl, String trustStorePath, String trustStorePassword,
			long timeoutInSeconds) {
		OkHttpClient.Builder builder = new OkHttpClient.Builder();

		setTimeouts(builder, timeoutInSeconds);
		builder.followRedirects(false).followSslRedirects(false);

		if (trustStorePath != null) {
			configureTrustStore(builder, trustStorePath, trustStorePassword);
		}
		if (!validateSsl) {
			disableSslValidation(builder);
		}

		return builder.build();
	}

	/**
	 * Reads the keystore at the given path and configures the builder so the
	 * {@link OkHttpClient} will accept the certificates stored in the keystore.
	 */
	private static void configureTrustStore(OkHttpClient.Builder builder, String trustStorePath,
			String trustStorePassword) {
		KeyStore keyStore = readKeyStore(trustStorePath, trustStorePassword);
		try {
			SSLContext sslContext = SSLContext.getInstance("SSL");
			TrustManagerFactory trustManagerFactory = TrustManagerFactory
					.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			trustManagerFactory.init(keyStore);
			sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());

			if (trustManagerFactory.getTrustManagers().length == 0) {
				LogUtils.fail("No trust managers found. This is a bug. Please report it to CQSE.");
			}

			applySslContextAndTrustManager(builder, sslContext, trustManagerFactory);
		} catch (NoSuchAlgorithmException e) {
			LogUtils.failWithStackTrace("Failed to instantiate an SSLContext or TrustManagerFactory."
					+ "\nThis is a bug. Please report it to CQSE.", e);
		} catch (KeyStoreException e) {
			LogUtils.failWithStackTrace("Failed to initialize the TrustManagerFactory with the keystore."
					+ "\nThis is a bug. Please report it to CQSE.", e);
		} catch (KeyManagementException e) {
			LogUtils.failWithStackTrace("Failed to initialize the SSLContext with the trust managers."
					+ "\nThis is a bug. Please report it to CQSE.", e);
		}
	}

	private static void applySslContextAndTrustManager(OkHttpClient.Builder builder, SSLContext sslContext,
			TrustManagerFactory trustManagerFactory) {
		try {
			X509TrustManager x509TrustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
			builder.sslSocketFactory(sslContext.getSocketFactory(), x509TrustManager);
		} catch (ClassCastException e) {
			LogUtils.failWithStackTrace(
					"Trust manager is not of X509 format." + "\nThis is a bug. Please report it to CQSE.", e);
		}
	}

	private static KeyStore readKeyStore(String keystorePath, String keystorePassword) {
		try (FileInputStream stream = new FileInputStream(keystorePath)) {
			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(stream, keystorePassword.toCharArray());
			return keyStore;
		} catch (IOException e) {
			LogUtils.failWithoutStackTrace("Failed to read keystore file " + keystorePath
					+ "\nPlease make sure that file exists and is readable and that you provided the correct password."
					+ " Please also make sure that the keystore file is a valid Java keystore."
					+ " You can use the program `keytool` from your JVM installation to check this:"
					+ "\nkeytool -list -keystore " + keystorePath, e);
		} catch (CertificateException e) {
			LogUtils.failWithoutStackTrace("Failed to load one of the certificates in the keystore file " + keystorePath
					+ "\nPlease make sure that the certificate is stored correctly and the certificate version and encoding are supported.",
					e);
		} catch (NoSuchAlgorithmException e) {
			LogUtils.failWithoutStackTrace("Failed to verify the integrity of the keystore file " + keystorePath
					+ " because it uses an unsupported hashing algorithm."
					+ "\nPlease change the keystore so it uses a supported algorithm (e.g. the default used by `keytool` is supported).",
					e);
		} catch (KeyStoreException e) {
			LogUtils.failWithStackTrace(
					"Failed to instantiate an in-memory keystore." + "\nThis is a bug. Please report it to CQSE.", e);
		}

		return null;
	}

	private static void disableSslValidation(OkHttpClient.Builder builder) {
		SSLSocketFactory sslSocketFactory;
		try {
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, new TrustManager[] { TrustAllCertificatesManager.INSTANCE }, new SecureRandom());
			sslSocketFactory = sslContext.getSocketFactory();
		} catch (GeneralSecurityException e) {
			LogUtils.warn("Could not disable SSL certificate validation. Leaving it enabled", e);
			return;
		}

		builder.sslSocketFactory(sslSocketFactory, TrustAllCertificatesManager.INSTANCE);
		builder.hostnameVerifier((hostName, session) -> true);
	}

	private static void setTimeouts(okhttp3.OkHttpClient.Builder builder, long timeoutInSeconds) {
		builder.connectTimeout(timeoutInSeconds, TimeUnit.SECONDS);
		builder.readTimeout(timeoutInSeconds, TimeUnit.SECONDS);
		builder.writeTimeout(timeoutInSeconds, TimeUnit.SECONDS);
	}

	private static class TrustAllCertificatesManager implements X509TrustManager {
		static final TrustAllCertificatesManager INSTANCE = new TrustAllCertificatesManager();

		public TrustAllCertificatesManager() {
		}

		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}

		public void checkServerTrusted(X509Certificate[] certs, String authType) {
		}

		public void checkClientTrusted(X509Certificate[] certs, String authType) {
		}
	}

}
