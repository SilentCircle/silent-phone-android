/*
Copyright (C) 2014-2015, Silent Circle, LLC. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Any redistribution, use, or modification is done solely for personal
      benefit and not for any commercial purpose or for monetary gain
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name Silent Circle nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL SILENT CIRCLE, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.silentcircle.silentphone2.util;

import android.util.Log;

import com.silentcircle.silentphone2.BuildConfig;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * This class contains and manages the trusted root certificates.
 *
 * Created by werner on 19.08.14.
 */
public class PinnedCertificateHandling {

    private static String TAG = "PinnedKeyStore";

    private static int mConfigurationInUse;
    private static KeyStore mKeyStore;

    /*
     * Silent Phone uses the following root certificates to setup TLS with the
     * provisioning servers of the production network.
     */
    private static final String productionCertificates =
            // Entrust root certificate, serial number 37:4a:d2:43, SHA1: 99:A6:9B:E6
            "-----BEGIN CERTIFICATE-----\r\n" +
                    "MIIE2DCCBEGgAwIBAgIEN0rSQzANBgkqhkiG9w0BAQUFADCBwzELMAkGA1UEBhMC\r\n" +
                    "VVMxFDASBgNVBAoTC0VudHJ1c3QubmV0MTswOQYDVQQLEzJ3d3cuZW50cnVzdC5u\r\n" +
                    "ZXQvQ1BTIGluY29ycC4gYnkgcmVmLiAobGltaXRzIGxpYWIuKTElMCMGA1UECxMc\r\n" +
                    "KGMpIDE5OTkgRW50cnVzdC5uZXQgTGltaXRlZDE6MDgGA1UEAxMxRW50cnVzdC5u\r\n" +
                    "ZXQgU2VjdXJlIFNlcnZlciBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTAeFw05OTA1\r\n" +
                    "MjUxNjA5NDBaFw0xOTA1MjUxNjM5NDBaMIHDMQswCQYDVQQGEwJVUzEUMBIGA1UE\r\n" +
                    "ChMLRW50cnVzdC5uZXQxOzA5BgNVBAsTMnd3dy5lbnRydXN0Lm5ldC9DUFMgaW5j\r\n" +
                    "b3JwLiBieSByZWYuIChsaW1pdHMgbGlhYi4pMSUwIwYDVQQLExwoYykgMTk5OSBF\r\n" +
                    "bnRydXN0Lm5ldCBMaW1pdGVkMTowOAYDVQQDEzFFbnRydXN0Lm5ldCBTZWN1cmUg\r\n" +
                    "U2VydmVyIENlcnRpZmljYXRpb24gQXV0aG9yaXR5MIGdMA0GCSqGSIb3DQEBAQUA\r\n" +
                    "A4GLADCBhwKBgQDNKIM0VBuJ8w+vN5Ex/68xYMmo6LIQaO2f55M28Qpku0f1BBc/\r\n" +
                    "I0dNxScZgSYMVHINiC3ZH5oSn7yzcdOAGT9HZnuMNSjSuQrfJNqc1lB5gXpa0zf3\r\n" +
                    "wkrYKZImZNHkmGw6AIr1NJtl+O3jEP/9uElY3KDegjlrgbEWGWG5VLbmQwIBA6OC\r\n" +
                    "AdcwggHTMBEGCWCGSAGG+EIBAQQEAwIABzCCARkGA1UdHwSCARAwggEMMIHeoIHb\r\n" +
                    "oIHYpIHVMIHSMQswCQYDVQQGEwJVUzEUMBIGA1UEChMLRW50cnVzdC5uZXQxOzA5\r\n" +
                    "BgNVBAsTMnd3dy5lbnRydXN0Lm5ldC9DUFMgaW5jb3JwLiBieSByZWYuIChsaW1p\r\n" +
                    "dHMgbGlhYi4pMSUwIwYDVQQLExwoYykgMTk5OSBFbnRydXN0Lm5ldCBMaW1pdGVk\r\n" +
                    "MTowOAYDVQQDEzFFbnRydXN0Lm5ldCBTZWN1cmUgU2VydmVyIENlcnRpZmljYXRp\r\n" +
                    "b24gQXV0aG9yaXR5MQ0wCwYDVQQDEwRDUkwxMCmgJ6AlhiNodHRwOi8vd3d3LmVu\r\n" +
                    "dHJ1c3QubmV0L0NSTC9uZXQxLmNybDArBgNVHRAEJDAigA8xOTk5MDUyNTE2MDk0\r\n" +
                    "MFqBDzIwMTkwNTI1MTYwOTQwWjALBgNVHQ8EBAMCAQYwHwYDVR0jBBgwFoAU8Bdi\r\n" +
                    "E1U9s/8KAGv7UISX8+1i0BowHQYDVR0OBBYEFPAXYhNVPbP/CgBr+1CEl/PtYtAa\r\n" +
                    "MAwGA1UdEwQFMAMBAf8wGQYJKoZIhvZ9B0EABAwwChsEVjQuMAMCBJAwDQYJKoZI\r\n" +
                    "hvcNAQEFBQADgYEAkNwwAvpkdMKnCqV8IY00F6j7Rw7/JXyNEwr75Ji174z4xRAN\r\n" +
                    "95K+8cPV1ZVqBLssziY2ZcgxxufuP+NXdYR6Ee9GTxj005i7qIcyunL2POI9n9cd\r\n" +
                    "2cNgQ4xYDiKWL2KjLB+6rQXvqzJ4h6BUcxm1XAX5Uj5tLUUL9wqT6u0G+bI=\r\n" +
                    "-----END CERTIFICATE-----\r\n" +

                    // Entrust root certificate, serial number 45:6b:50:54, SHA1: B3:1E:B1:B7
                    "-----BEGIN CERTIFICATE-----\r\n" +
                    "MIIEkTCCA3mgAwIBAgIERWtQVDANBgkqhkiG9w0BAQUFADCBsDELMAkGA1UEBhMC\r\n" +
                    "VVMxFjAUBgNVBAoTDUVudHJ1c3QsIEluYy4xOTA3BgNVBAsTMHd3dy5lbnRydXN0\r\n" +
                    "Lm5ldC9DUFMgaXMgaW5jb3Jwb3JhdGVkIGJ5IHJlZmVyZW5jZTEfMB0GA1UECxMW\r\n" +
                    "KGMpIDIwMDYgRW50cnVzdCwgSW5jLjEtMCsGA1UEAxMkRW50cnVzdCBSb290IENl\r\n" +
                    "cnRpZmljYXRpb24gQXV0aG9yaXR5MB4XDTA2MTEyNzIwMjM0MloXDTI2MTEyNzIw\r\n" +
                    "NTM0MlowgbAxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1FbnRydXN0LCBJbmMuMTkw\r\n" +
                    "NwYDVQQLEzB3d3cuZW50cnVzdC5uZXQvQ1BTIGlzIGluY29ycG9yYXRlZCBieSBy\r\n" +
                    "ZWZlcmVuY2UxHzAdBgNVBAsTFihjKSAyMDA2IEVudHJ1c3QsIEluYy4xLTArBgNV\r\n" +
                    "BAMTJEVudHJ1c3QgUm9vdCBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTCCASIwDQYJ\r\n" +
                    "KoZIhvcNAQEBBQADggEPADCCAQoCggEBALaVtkNC+sZtKm9I35RMOVcF7sN5EUFo\r\n" +
                    "Nu3s/poBj6E4KPz3EEZmLk0eGrEaTsbRwJWIsMn/MYszA9u3g3s+IIRe7bJWKKf4\r\n" +
                    "4LlAcTfFy0cOlypowCKVYhXbR9n10Cv/gkvJrT7eTNuQgFA/CYqEAOwwCj0Yzfv9\r\n" +
                    "KlmaI5UXLEWeH25DeW0MXJj+SKfFI0dcXv1u5x609mhF0YaDW6KKjbHjKYD+JXGI\r\n" +
                    "rb68j6xSlkuqUY3kEzEZ6E5Nn9uss2rVvDlUccp6en+Q3X0dgNmBu1kmwhH+5pPi\r\n" +
                    "94DkZfs0Nw4pgHBNrziGLp5/V6+eF67rHMsoIV+2HNjnogQi+dPa2MsCAwEAAaOB\r\n" +
                    "sDCBrTAOBgNVHQ8BAf8EBAMCAQYwDwYDVR0TAQH/BAUwAwEB/zArBgNVHRAEJDAi\r\n" +
                    "gA8yMDA2MTEyNzIwMjM0MlqBDzIwMjYxMTI3MjA1MzQyWjAfBgNVHSMEGDAWgBRo\r\n" +
                    "kORnpKZTgMeGZqTx90tD+4S9bTAdBgNVHQ4EFgQUaJDkZ6SmU4DHhmak8fdLQ/uE\r\n" +
                    "vW0wHQYJKoZIhvZ9B0EABBAwDhsIVjcuMTo0LjADAgSQMA0GCSqGSIb3DQEBBQUA\r\n" +
                    "A4IBAQCT1DCw1wMgKtD5Y+iRDAUgqV8ZyntyTtSx29CW+1RaGSwMCPeyvIWonX9t\r\n" +
                    "O1KzKtvn1ISMY/YPyyYBkVBs9F8U4pN0wBOeMDpQ47RgxRzwIkSNcUesyBrJ6Zua\r\n" +
                    "AGAT/3B+XxFNSRuzFVJ7yVTav52Vr2ua2J7p8eRDjeIRRDq/r72DQnNSi6q7pynP\r\n" +
                    "9WQcCk3RvKqsnyrQ/39/2n3qse0wJcGE2jTSW3iDVuycNsMm4hH2Z0kdkquM++v/\r\n" +
                    "eu6FSqdQgPCnXEqULl8FmTxSQeDNtGPPAUO6nIPcj2A781q0tHuu2guQOHXvgR1m\r\n" +
                    "0vdXcDazv/wor3ElhVsT/h5/WrQ8\r\n" +
                    "-----END CERTIFICATE-----\r\n"  ;

    /*
     * Silent Phone uses the following certificate to setup TLS with the
     * provisioning servers of the development network.
     */
    private static final String developmentCertificates =
            // Entrust root certificate, serial number: 38:63:DE:F8, SHA1: 50:30:06:09
            "-----BEGIN CERTIFICATE-----\r\n" +
                    "MIIEKjCCAxKgAwIBAgIEOGPe+DANBgkqhkiG9w0BAQUFADCBtDEUMBIGA1UEChMLRW50cnVzdC5u\r\n" +
                    "ZXQxQDA+BgNVBAsUN3d3dy5lbnRydXN0Lm5ldC9DUFNfMjA0OCBpbmNvcnAuIGJ5IHJlZi4gKGxp\r\n" +
                    "bWl0cyBsaWFiLikxJTAjBgNVBAsTHChjKSAxOTk5IEVudHJ1c3QubmV0IExpbWl0ZWQxMzAxBgNV\r\n" +
                    "BAMTKkVudHJ1c3QubmV0IENlcnRpZmljYXRpb24gQXV0aG9yaXR5ICgyMDQ4KTAeFw05OTEyMjQx\r\n" +
                    "NzUwNTFaFw0yOTA3MjQxNDE1MTJaMIG0MRQwEgYDVQQKEwtFbnRydXN0Lm5ldDFAMD4GA1UECxQ3\r\n" +
                    "d3d3LmVudHJ1c3QubmV0L0NQU18yMDQ4IGluY29ycC4gYnkgcmVmLiAobGltaXRzIGxpYWIuKTEl\r\n" +
                    "MCMGA1UECxMcKGMpIDE5OTkgRW50cnVzdC5uZXQgTGltaXRlZDEzMDEGA1UEAxMqRW50cnVzdC5u\r\n" +
                    "ZXQgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkgKDIwNDgpMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A\r\n" +
                    "MIIBCgKCAQEArU1LqRKGsuqjIAcVFmQqK0vRvwtKTY7tgHalZ7d4QMBzQshowNtTK91euHaYNZOL\r\n" +
                    "Gp18EzoOH1u3Hs/lJBQesYGpjX24zGtLA/ECDNyrpUAkAH90lKGdCCmziAv1h3edVc3kw37XamSr\r\n" +
                    "hRSGlVuXMlBvPci6Zgzj/L24ScF2iUkZ/cCovYmjZy/Gn7xxGWC4LeksyZB2ZnuU4q941mVTXTzW\r\n" +
                    "nLLPKQP5L6RQstRIzgUyVYr9smRMDuSYB3Xbf9+5CFVghTAp+XtIpGmG4zU/HoZdenoVve8AjhUi\r\n" +
                    "VBcAkCaTvA5JaJG/+EfTnZVCwQ5N328mz8MYIWJmQ3DW1cAH4QIDAQABo0IwQDAOBgNVHQ8BAf8E\r\n" +
                    "BAMCAQYwDwYDVR0TAQH/BAUwAwEB/zAdBgNVHQ4EFgQUVeSB0RGAvtiJuQijMfmhJAkWuXAwDQYJ\r\n" +
                    "KoZIhvcNAQEFBQADggEBADubj1abMOdTmXx6eadNl9cZlZD7Bh/KM3xGY4+WZiT6QBshJ8rmcnPy\r\n" +
                    "T/4xmf3IDExoU8aAghOY+rat2l098c5u9hURlIIM7j+VrxGrD9cv3h8Dj1csHsm7mhpElesYT6Yf\r\n" +
                    "zX1XEC+bBAlahLVu2B064dae0Wx5XnkcFMXj0EyTO2U87d89vqbllRrDtRnDvV5bu/8j72gZyxKT\r\n" +
                    "J1wDLW8w0B62GqzeWvfRqqgnpv55gcR5mTNXuhKwqeBCbJPKVt7+bYQLCIt+jerXmCHG8+c8eS9e\r\n" +
                    "nNFMFY3h7CI3zJpDC5fcgJCNs2ebb0gIFVbPv/ErfF6adulZkMV8gzURZVE=\r\n" +
                    "-----END CERTIFICATE-----\r\n";


    /**
     * Get a SSL context that uses a trust manager with a restricted set of root certificates.
     *
     * @param configuration Which network configuration to use
     * @return SSL context or {@code null} if context creation failed
     */
    public static SSLContext getPinnedSslContext(final int configuration) {
        final KeyStore keyStore = getKeyStore(configuration);
        if (keyStore == null) {
            Log.e(TAG, "Failed to get KeyStore object");
            return null;
        }

        final String algorithm = TrustManagerFactory.getDefaultAlgorithm();
        final SSLContext context;
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm);
            tmf.init(keyStore);
            context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), null);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "TrustManager/SSLContext generation failed", e);
            return null;
        } catch (KeyStoreException e) {
            Log.e(TAG, "TrustManager generation failed", e);
            return null;
        } catch (KeyManagementException e) {
            Log.e(TAG, "SSLContext generation failed", e);
            return null;
        }
        return context;
    }

    private static KeyStore getKeyStore(final int configuration) {
        if (mConfigurationInUse != configuration || mKeyStore == null) {
            mConfigurationInUse = configuration;
            final String certData;
            switch (configuration) {
                default:
                case ConfigurationUtilities.PRODUCTION_NETWORK:
                    certData = productionCertificates;
                    break;

                case ConfigurationUtilities.DEVELOPMENT_NETWORK:
                    certData = developmentCertificates;
                    break;
            }
            mKeyStore = createKeystore(certData);
            if (mKeyStore == null) {
                Log.e(TAG, "Failed to create KeyStore object");
            }
        }
        return mKeyStore;
    }

    private static KeyStore createKeystore(String certData) {
        final ByteArrayInputStream fis = new ByteArrayInputStream(certData.getBytes());
        final BufferedInputStream bis = new BufferedInputStream(fis);

        final KeyStore store;
        try {
            // Get an empty PKCS12 key store to store the root certificates
            store = KeyStore.getInstance("PKCS12");
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            store.load(null, null);
            int certCount = 0;

            while (bis.available() > 0) {
                Certificate cert = cf.generateCertificate(bis);
                String alias = "cert" + certCount;
                if (BuildConfig.DEBUG) Log.d(TAG, "Cert " + alias + ": ");
                store.setCertificateEntry(alias, cert);
                certCount++;
            }
        } catch (CertificateException e) {
            Log.e(TAG, "cert generation failed", e);
            return null;
        } catch (IOException e) {
            Log.e(TAG, "i/o exception failed", e);
            return null;
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "No such algorithm exception", e);
            return null;
        } catch (KeyStoreException e) {
            Log.e(TAG, "Key store exception", e);
            return null;
        }
        return store;
    }
}
