//VoipPhone
//Created by Janis Narbuts
//Copyright (c) 2004-2012 Tivi LTD, www.tiviphone.com. All rights reserved.

#include "../baseclasses/CTBase.h"
#include "../baseclasses/CTEditBase.h"
#include "../os/CTiViSock.h"
#include "../os/CTThread.h"
#include "../os/CTTcp.h"
#include "../encrypt/tls/CTTLS.h"

#ifdef ANDROID
void androidLog(const char* format, ...);
#endif

#ifdef _WIN32
#define snprintf _snprintf
#endif

/*
 * Silent Phone uses the following root certificates to setup TLS with the
 * provisioning servers of the production network.
 */
static const char *productionCert=
// Entrust root certificate, serial number 37:4a:d2:43, SHA1: 99:A6:9B:E6
"-----BEGIN CERTIFICATE-----\r\n"
"MIIE2DCCBEGgAwIBAgIEN0rSQzANBgkqhkiG9w0BAQUFADCBwzELMAkGA1UEBhMC\r\n"
"VVMxFDASBgNVBAoTC0VudHJ1c3QubmV0MTswOQYDVQQLEzJ3d3cuZW50cnVzdC5u\r\n"
"ZXQvQ1BTIGluY29ycC4gYnkgcmVmLiAobGltaXRzIGxpYWIuKTElMCMGA1UECxMc\r\n"
"KGMpIDE5OTkgRW50cnVzdC5uZXQgTGltaXRlZDE6MDgGA1UEAxMxRW50cnVzdC5u\r\n"
"ZXQgU2VjdXJlIFNlcnZlciBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTAeFw05OTA1\r\n"
"MjUxNjA5NDBaFw0xOTA1MjUxNjM5NDBaMIHDMQswCQYDVQQGEwJVUzEUMBIGA1UE\r\n"
"ChMLRW50cnVzdC5uZXQxOzA5BgNVBAsTMnd3dy5lbnRydXN0Lm5ldC9DUFMgaW5j\r\n"
"b3JwLiBieSByZWYuIChsaW1pdHMgbGlhYi4pMSUwIwYDVQQLExwoYykgMTk5OSBF\r\n"
"bnRydXN0Lm5ldCBMaW1pdGVkMTowOAYDVQQDEzFFbnRydXN0Lm5ldCBTZWN1cmUg\r\n"
"U2VydmVyIENlcnRpZmljYXRpb24gQXV0aG9yaXR5MIGdMA0GCSqGSIb3DQEBAQUA\r\n"
"A4GLADCBhwKBgQDNKIM0VBuJ8w+vN5Ex/68xYMmo6LIQaO2f55M28Qpku0f1BBc/\r\n"
"I0dNxScZgSYMVHINiC3ZH5oSn7yzcdOAGT9HZnuMNSjSuQrfJNqc1lB5gXpa0zf3\r\n"
"wkrYKZImZNHkmGw6AIr1NJtl+O3jEP/9uElY3KDegjlrgbEWGWG5VLbmQwIBA6OC\r\n"
"AdcwggHTMBEGCWCGSAGG+EIBAQQEAwIABzCCARkGA1UdHwSCARAwggEMMIHeoIHb\r\n"
"oIHYpIHVMIHSMQswCQYDVQQGEwJVUzEUMBIGA1UEChMLRW50cnVzdC5uZXQxOzA5\r\n"
"BgNVBAsTMnd3dy5lbnRydXN0Lm5ldC9DUFMgaW5jb3JwLiBieSByZWYuIChsaW1p\r\n"
"dHMgbGlhYi4pMSUwIwYDVQQLExwoYykgMTk5OSBFbnRydXN0Lm5ldCBMaW1pdGVk\r\n"
"MTowOAYDVQQDEzFFbnRydXN0Lm5ldCBTZWN1cmUgU2VydmVyIENlcnRpZmljYXRp\r\n"
"b24gQXV0aG9yaXR5MQ0wCwYDVQQDEwRDUkwxMCmgJ6AlhiNodHRwOi8vd3d3LmVu\r\n"
"dHJ1c3QubmV0L0NSTC9uZXQxLmNybDArBgNVHRAEJDAigA8xOTk5MDUyNTE2MDk0\r\n"
"MFqBDzIwMTkwNTI1MTYwOTQwWjALBgNVHQ8EBAMCAQYwHwYDVR0jBBgwFoAU8Bdi\r\n"
"E1U9s/8KAGv7UISX8+1i0BowHQYDVR0OBBYEFPAXYhNVPbP/CgBr+1CEl/PtYtAa\r\n"
"MAwGA1UdEwQFMAMBAf8wGQYJKoZIhvZ9B0EABAwwChsEVjQuMAMCBJAwDQYJKoZI\r\n"
"hvcNAQEFBQADgYEAkNwwAvpkdMKnCqV8IY00F6j7Rw7/JXyNEwr75Ji174z4xRAN\r\n"
"95K+8cPV1ZVqBLssziY2ZcgxxufuP+NXdYR6Ee9GTxj005i7qIcyunL2POI9n9cd\r\n"
"2cNgQ4xYDiKWL2KjLB+6rQXvqzJ4h6BUcxm1XAX5Uj5tLUUL9wqT6u0G+bI=\r\n"
"-----END CERTIFICATE-----\r\n"

// Entrust root certificate, serial number 45:6b:50:54, SHA1: B3:1E:B1:B7
"-----BEGIN CERTIFICATE-----\r\n"
"MIIEkTCCA3mgAwIBAgIERWtQVDANBgkqhkiG9w0BAQUFADCBsDELMAkGA1UEBhMC\r\n"
"VVMxFjAUBgNVBAoTDUVudHJ1c3QsIEluYy4xOTA3BgNVBAsTMHd3dy5lbnRydXN0\r\n"
"Lm5ldC9DUFMgaXMgaW5jb3Jwb3JhdGVkIGJ5IHJlZmVyZW5jZTEfMB0GA1UECxMW\r\n"
"KGMpIDIwMDYgRW50cnVzdCwgSW5jLjEtMCsGA1UEAxMkRW50cnVzdCBSb290IENl\r\n"
"cnRpZmljYXRpb24gQXV0aG9yaXR5MB4XDTA2MTEyNzIwMjM0MloXDTI2MTEyNzIw\r\n"
"NTM0MlowgbAxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1FbnRydXN0LCBJbmMuMTkw\r\n"
"NwYDVQQLEzB3d3cuZW50cnVzdC5uZXQvQ1BTIGlzIGluY29ycG9yYXRlZCBieSBy\r\n"
"ZWZlcmVuY2UxHzAdBgNVBAsTFihjKSAyMDA2IEVudHJ1c3QsIEluYy4xLTArBgNV\r\n"
"BAMTJEVudHJ1c3QgUm9vdCBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTCCASIwDQYJ\r\n"
"KoZIhvcNAQEBBQADggEPADCCAQoCggEBALaVtkNC+sZtKm9I35RMOVcF7sN5EUFo\r\n"
"Nu3s/poBj6E4KPz3EEZmLk0eGrEaTsbRwJWIsMn/MYszA9u3g3s+IIRe7bJWKKf4\r\n"
"4LlAcTfFy0cOlypowCKVYhXbR9n10Cv/gkvJrT7eTNuQgFA/CYqEAOwwCj0Yzfv9\r\n"
"KlmaI5UXLEWeH25DeW0MXJj+SKfFI0dcXv1u5x609mhF0YaDW6KKjbHjKYD+JXGI\r\n"
"rb68j6xSlkuqUY3kEzEZ6E5Nn9uss2rVvDlUccp6en+Q3X0dgNmBu1kmwhH+5pPi\r\n"
"94DkZfs0Nw4pgHBNrziGLp5/V6+eF67rHMsoIV+2HNjnogQi+dPa2MsCAwEAAaOB\r\n"
"sDCBrTAOBgNVHQ8BAf8EBAMCAQYwDwYDVR0TAQH/BAUwAwEB/zArBgNVHRAEJDAi\r\n"
"gA8yMDA2MTEyNzIwMjM0MlqBDzIwMjYxMTI3MjA1MzQyWjAfBgNVHSMEGDAWgBRo\r\n"
"kORnpKZTgMeGZqTx90tD+4S9bTAdBgNVHQ4EFgQUaJDkZ6SmU4DHhmak8fdLQ/uE\r\n"
"vW0wHQYJKoZIhvZ9B0EABBAwDhsIVjcuMTo0LjADAgSQMA0GCSqGSIb3DQEBBQUA\r\n"
"A4IBAQCT1DCw1wMgKtD5Y+iRDAUgqV8ZyntyTtSx29CW+1RaGSwMCPeyvIWonX9t\r\n"
"O1KzKtvn1ISMY/YPyyYBkVBs9F8U4pN0wBOeMDpQ47RgxRzwIkSNcUesyBrJ6Zua\r\n"
"AGAT/3B+XxFNSRuzFVJ7yVTav52Vr2ua2J7p8eRDjeIRRDq/r72DQnNSi6q7pynP\r\n"
"9WQcCk3RvKqsnyrQ/39/2n3qse0wJcGE2jTSW3iDVuycNsMm4hH2Z0kdkquM++v/\r\n"
"eu6FSqdQgPCnXEqULl8FmTxSQeDNtGPPAUO6nIPcj2A781q0tHuu2guQOHXvgR1m\r\n"
"0vdXcDazv/wor3ElhVsT/h5/WrQ8\r\n"
"-----END CERTIFICATE-----\r\n" ;

/*
 * Silent Phone uses the following certificate to setup TLS with the
 * provisioning servers of the development network.
 */
static const char *developmentCert =
// // Entrust root certificate, serial number: 38:63:DE:F8, SHA1: 50:30:06:09
// "-----BEGIN CERTIFICATE-----\r\n"
// "MIIEKjCCAxKgAwIBAgIEOGPe+DANBgkqhkiG9w0BAQUFADCBtDEUMBIGA1UEChMLRW50cnVzdC5u\r\n"
// "ZXQxQDA+BgNVBAsUN3d3dy5lbnRydXN0Lm5ldC9DUFNfMjA0OCBpbmNvcnAuIGJ5IHJlZi4gKGxp\r\n"
// "bWl0cyBsaWFiLikxJTAjBgNVBAsTHChjKSAxOTk5IEVudHJ1c3QubmV0IExpbWl0ZWQxMzAxBgNV\r\n"
// "BAMTKkVudHJ1c3QubmV0IENlcnRpZmljYXRpb24gQXV0aG9yaXR5ICgyMDQ4KTAeFw05OTEyMjQx\r\n"
// "NzUwNTFaFw0yOTA3MjQxNDE1MTJaMIG0MRQwEgYDVQQKEwtFbnRydXN0Lm5ldDFAMD4GA1UECxQ3\r\n"
// "d3d3LmVudHJ1c3QubmV0L0NQU18yMDQ4IGluY29ycC4gYnkgcmVmLiAobGltaXRzIGxpYWIuKTEl\r\n"
// "MCMGA1UECxMcKGMpIDE5OTkgRW50cnVzdC5uZXQgTGltaXRlZDEzMDEGA1UEAxMqRW50cnVzdC5u\r\n"
// "ZXQgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkgKDIwNDgpMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A\r\n"
// "MIIBCgKCAQEArU1LqRKGsuqjIAcVFmQqK0vRvwtKTY7tgHalZ7d4QMBzQshowNtTK91euHaYNZOL\r\n"
// "Gp18EzoOH1u3Hs/lJBQesYGpjX24zGtLA/ECDNyrpUAkAH90lKGdCCmziAv1h3edVc3kw37XamSr\r\n"
// "hRSGlVuXMlBvPci6Zgzj/L24ScF2iUkZ/cCovYmjZy/Gn7xxGWC4LeksyZB2ZnuU4q941mVTXTzW\r\n"
// "nLLPKQP5L6RQstRIzgUyVYr9smRMDuSYB3Xbf9+5CFVghTAp+XtIpGmG4zU/HoZdenoVve8AjhUi\r\n"
// "VBcAkCaTvA5JaJG/+EfTnZVCwQ5N328mz8MYIWJmQ3DW1cAH4QIDAQABo0IwQDAOBgNVHQ8BAf8E\r\n"
// "BAMCAQYwDwYDVR0TAQH/BAUwAwEB/zAdBgNVHQ4EFgQUVeSB0RGAvtiJuQijMfmhJAkWuXAwDQYJ\r\n"
// "KoZIhvcNAQEFBQADggEBADubj1abMOdTmXx6eadNl9cZlZD7Bh/KM3xGY4+WZiT6QBshJ8rmcnPy\r\n"
// "T/4xmf3IDExoU8aAghOY+rat2l098c5u9hURlIIM7j+VrxGrD9cv3h8Dj1csHsm7mhpElesYT6Yf\r\n"
// "zX1XEC+bBAlahLVu2B064dae0Wx5XnkcFMXj0EyTO2U87d89vqbllRrDtRnDvV5bu/8j72gZyxKT\r\n"
// "J1wDLW8w0B62GqzeWvfRqqgnpv55gcR5mTNXuhKwqeBCbJPKVt7+bYQLCIt+jerXmCHG8+c8eS9e\r\n"
// "nNFMFY3h7CI3zJpDC5fcgJCNs2ebb0gIFVbPv/ErfF6adulZkMV8gzURZVE=\r\n"
// "-----END CERTIFICATE-----\r\n"

// Entrust root certificate G2, serial number: 1246989352 (0x4a538c28),
// SHA1: 8C:F4:27:FD:79:0C:3A:D1:66:06:8D:E8:1E:57:EF:BB:93:22:72:D4
"-----BEGIN CERTIFICATE-----\r\n"
"MIIEPjCCAyagAwIBAgIESlOMKDANBgkqhkiG9w0BAQsFADCBvjELMAkGA1UEBhMC\r\n"
"VVMxFjAUBgNVBAoTDUVudHJ1c3QsIEluYy4xKDAmBgNVBAsTH1NlZSB3d3cuZW50\r\n"
"cnVzdC5uZXQvbGVnYWwtdGVybXMxOTA3BgNVBAsTMChjKSAyMDA5IEVudHJ1c3Qs\r\n"
"IEluYy4gLSBmb3IgYXV0aG9yaXplZCB1c2Ugb25seTEyMDAGA1UEAxMpRW50cnVz\r\n"
"dCBSb290IENlcnRpZmljYXRpb24gQXV0aG9yaXR5IC0gRzIwHhcNMDkwNzA3MTcy\r\n"
"NTU0WhcNMzAxMjA3MTc1NTU0WjCBvjELMAkGA1UEBhMCVVMxFjAUBgNVBAoTDUVu\r\n"
"dHJ1c3QsIEluYy4xKDAmBgNVBAsTH1NlZSB3d3cuZW50cnVzdC5uZXQvbGVnYWwt\r\n"
"dGVybXMxOTA3BgNVBAsTMChjKSAyMDA5IEVudHJ1c3QsIEluYy4gLSBmb3IgYXV0\r\n"
"aG9yaXplZCB1c2Ugb25seTEyMDAGA1UEAxMpRW50cnVzdCBSb290IENlcnRpZmlj\r\n"
"YXRpb24gQXV0aG9yaXR5IC0gRzIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK\r\n"
"AoIBAQC6hLZy254Ma+KZ6TABp3bqMriVQRrJ2mFOWHLP/vaCeb9zYQYKpSfYs1/T\r\n"
"RU4cctZOMvJyig/3gxnQaoCAAEUesMfnmr8SVycco2gvCoe9amsOXmXzHHfV1IWN\r\n"
"cCG0szLni6LVhjkCsbjSR87kyUnEO6fe+1R9V77w6G7CebI6C1XiUJgWMhNcL3hW\r\n"
"wcKUs/Ja5CeanyTXxuzQmyWC48zCxEXFjJd6BmsqEZ+pCm5IO2/b1BEZQvePB7/1\r\n"
"U1+cPvQXLOZprE4yTGJ36rfo5bs0vBmLrpxR57d+tVOxMyLlbc9wPBr64ptntoP0\r\n"
"jaWvYkxN4FisZDQSA/i2jZRjJKRxAgMBAAGjQjBAMA4GA1UdDwEB/wQEAwIBBjAP\r\n"
"BgNVHRMBAf8EBTADAQH/MB0GA1UdDgQWBBRqciZ60B7vfec7aVHUbI2fkBJmqzAN\r\n"
"BgkqhkiG9w0BAQsFAAOCAQEAeZ8dlsa2eT8ijYfThwMEYGprmi5ZiXMRrEPR9RP/\r\n"
"jTkrwPK9T3CMqS/qF8QLVJ7UG5aYMzyorWKiAHarWWluBh1+xLlEjZivEtRh2woZ\r\n"
"Rkfz6/djwUAFQKXSt/S1mja/qYh2iARVBCuch38aNzx+LaUa2NSJXsq9rD1s2G2v\r\n"
"1fN2D807iDginWyTmsQ9v4IbZT+mD12q/OWyFcq1rca8PdCE6OoGcrBNOTJ4vz4R\r\n"
"nAuknZoh8/CbCzB428Hch0P+vGOaysXCHMnHjf87ElgI5rY97HosTvuDls4MPGmH\r\n"
"VHOkc8KT/1EQrBVUAdj8BbGJoX90g5pJ19xOe4pIb4tF9g==\r\n"
"-----END CERTIFICATE-----\r\n"  

// Entrust root certificate
// Serial Number: 1164660820 (0x456b5054), SHA1 Fingerprint=B3:1E:B1:B7:40:E3:6C:84:02:DA:DC:37:D4:4D:F5:D4:67:49:52:F9
"-----BEGIN CERTIFICATE-----\r\n"
"MIIEkTCCA3mgAwIBAgIERWtQVDANBgkqhkiG9w0BAQUFADCBsDELMAkGA1UEBhMC\r\n"
"VVMxFjAUBgNVBAoTDUVudHJ1c3QsIEluYy4xOTA3BgNVBAsTMHd3dy5lbnRydXN0\r\n"
"Lm5ldC9DUFMgaXMgaW5jb3Jwb3JhdGVkIGJ5IHJlZmVyZW5jZTEfMB0GA1UECxMW\r\n"
"KGMpIDIwMDYgRW50cnVzdCwgSW5jLjEtMCsGA1UEAxMkRW50cnVzdCBSb290IENl\r\n"
"cnRpZmljYXRpb24gQXV0aG9yaXR5MB4XDTA2MTEyNzIwMjM0MloXDTI2MTEyNzIw\r\n"
"NTM0MlowgbAxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1FbnRydXN0LCBJbmMuMTkw\r\n"
"NwYDVQQLEzB3d3cuZW50cnVzdC5uZXQvQ1BTIGlzIGluY29ycG9yYXRlZCBieSBy\r\n"
"ZWZlcmVuY2UxHzAdBgNVBAsTFihjKSAyMDA2IEVudHJ1c3QsIEluYy4xLTArBgNV\r\n"
"BAMTJEVudHJ1c3QgUm9vdCBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTCCASIwDQYJ\r\n"
"KoZIhvcNAQEBBQADggEPADCCAQoCggEBALaVtkNC+sZtKm9I35RMOVcF7sN5EUFo\r\n"
"Nu3s/poBj6E4KPz3EEZmLk0eGrEaTsbRwJWIsMn/MYszA9u3g3s+IIRe7bJWKKf4\r\n"
"4LlAcTfFy0cOlypowCKVYhXbR9n10Cv/gkvJrT7eTNuQgFA/CYqEAOwwCj0Yzfv9\r\n"
"KlmaI5UXLEWeH25DeW0MXJj+SKfFI0dcXv1u5x609mhF0YaDW6KKjbHjKYD+JXGI\r\n"
"rb68j6xSlkuqUY3kEzEZ6E5Nn9uss2rVvDlUccp6en+Q3X0dgNmBu1kmwhH+5pPi\r\n"
"94DkZfs0Nw4pgHBNrziGLp5/V6+eF67rHMsoIV+2HNjnogQi+dPa2MsCAwEAAaOB\r\n"
"sDCBrTAOBgNVHQ8BAf8EBAMCAQYwDwYDVR0TAQH/BAUwAwEB/zArBgNVHRAEJDAi\r\n"
"gA8yMDA2MTEyNzIwMjM0MlqBDzIwMjYxMTI3MjA1MzQyWjAfBgNVHSMEGDAWgBRo\r\n"
"kORnpKZTgMeGZqTx90tD+4S9bTAdBgNVHQ4EFgQUaJDkZ6SmU4DHhmak8fdLQ/uE\r\n"
"vW0wHQYJKoZIhvZ9B0EABBAwDhsIVjcuMTo0LjADAgSQMA0GCSqGSIb3DQEBBQUA\r\n"
"A4IBAQCT1DCw1wMgKtD5Y+iRDAUgqV8ZyntyTtSx29CW+1RaGSwMCPeyvIWonX9t\r\n"
"O1KzKtvn1ISMY/YPyyYBkVBs9F8U4pN0wBOeMDpQ47RgxRzwIkSNcUesyBrJ6Zua\r\n"
"AGAT/3B+XxFNSRuzFVJ7yVTav52Vr2ua2J7p8eRDjeIRRDq/r72DQnNSi6q7pynP\r\n"
"9WQcCk3RvKqsnyrQ/39/2n3qse0wJcGE2jTSW3iDVuycNsMm4hH2Z0kdkquM++v/\r\n"
"eu6FSqdQgPCnXEqULl8FmTxSQeDNtGPPAUO6nIPcj2A781q0tHuu2guQOHXvgR1m\r\n"
"0vdXcDazv/wor3ElhVsT/h5/WrQ8\r\n"
"-----END CERTIFICATE-----\r\n"

;

// Default certificate: use the production network
static const char *provisioningCert = productionCert;


/*
 * Silent Phone uses two links: the first to use the production server,
 * the second one to use the development server.
 */
static const char *productionLink = "https://sccps.silentcircle.com";
static const char *developmentLink = "https://sccps-dev.silentcircle.com";

// Default link: use the production network
static const char *provisioningLink = productionLink;

/**
 * @brief Set provisioning link and certificate for use in development network.
 */
void setProvisioningToDevelop() {
    provisioningLink = developmentLink;
    provisioningCert = developmentCert;
}

/**
 * @brief Set provisioning link and certificate for use in production network.
 */
void setProvisioningToProduction() {
    provisioningLink = productionLink;    
    provisioningCert = productionCert;
}

/**
* @brief Returns current provisioning server
*/
const char* getCurrentProvSrv()
{
	return provisioningLink;
}

int retryConnectProvServ(){
#ifdef _WIN32
   return IDRETRY==::MessageBoxA(NULL,"Could not connect to the server. Check your internet connection and try again.","Info",MB_ICONERROR|MB_RETRYCANCEL);
#else
   return 1;
#endif
}

#ifdef _WIN32
int showSSLErrorMsg(void *ret, const char *p){
   MessageBoxA(NULL,"Server's security certificate validation has failed, phone will exit.","SSL TLS error",MB_ICONERROR|MB_OK);
   exit(1);
   return 0;
}
#else
int showSSLErrorMsg(void *ret, const char *p);
#endif

void tmp_log(const char *p);

static int respF(void *p, int i){
  
   int *rc=(int*)p;
   *rc=1;
   
   return 0;
}

void tivi_log(const char* format, ...);


typedef struct{
   void (*cb)(void *p, int ok, const char *pMsg);
   void *ptr;
}SSL_RET;

int showSSLErrorMsg2(void *ret, const char *p){
   
   SSL_RET *s=(SSL_RET*)ret;
#if defined(_WIN32) || defined(_WIN64)
   //#OZ-299, this part should be off.
   if(!s || !s->cb){
      const char *pErr="Server's security certificate validation has failed, phone will exit.";
      MessageBoxA(NULL,pErr,"SSL TLS error",MB_ICONERROR|MB_OK);
      exit(1);
      return 0;
   }
#endif
   const char *pErr="Server's security certificate validation has failed.";

   if(s){
      s->cb(s->ptr,-2,pErr);
   }
   return 0;
}



static char* download_page2Loc(const char *url, char *buf, int iMaxLen, int &iRespContentLen,
                    void (*cb)(void *p, int ok, const char *pMsg), void *cbRet, const char *pReq="GET", const char *pContent=NULL){

   char bufU[1024];
   char bufA[1024];
   memset(buf,0,iMaxLen);

   //CTSockTcp
   CTTHttp<CTTLS> *s=new CTTHttp<CTTLS>(buf,iMaxLen);
   CTTLS *tls=s->createSock();
#if  defined(__APPLE__) || defined(ANDROID_NDK)
   //TODO getInfo("get.prefLang");
   const char *getPrefLang(void);
   s->setLang(getPrefLang());
#endif
   //const char *getPrefLang()

   int r = s->splitUrl((char*)url, strlen(url), &bufA[0], &bufU[0]);
   if(r < 0) {
      cb(cbRet,-1,"Check url");
      return 0;
   }

   SSL_RET ssl_ret;
   ssl_ret.cb = cb;
   ssl_ret.ptr = cbRet;

   int iLen = strlen(provisioningCert);

   if (iLen > 0) {
       tls->errMsg = &showSSLErrorMsg2;
       tls->pRet = &ssl_ret;

       char bufZ[256];
       int i = 0;
       int iMaxL = sizeof(bufZ)-1;

       for (; i < iMaxL; i++){
           if (bufA[i] == ':' || !bufA[i]) {
               break;
           }
           bufZ[i] = bufA[i];
       }
       bufZ[i] = 0;
       printf("path ptr= %p l= %d addr=[%s]\n", provisioningCert, iLen, &bufZ[0]);
       tls->setCert(const_cast<char *>(provisioningCert), iLen, &bufZ[0]);
   }
   else {
       cb(cbRet,-1,"No cert");
       return 0;
   }

   int iRespCode=0;
   
   CTTHttp<CTTLS>::HTTP_W_TH wt;
   wt.ptr=&iRespCode;
   wt.respFnc=respF;
   s->waitResp(&wt,60);
   cb(cbRet,1,"Downloading...");
   s->getUrl(tls,&bufU[0],&bufA[0],pReq,pContent, pContent?strlen(pContent):0,pContent?"application/json":"");//locks
   
   iRespContentLen=0;
   char *p=s->getContent(iRespContentLen);

   if(p)cb(cbRet,1,"Downloading ok");
   
   int c=0;
   while(iRespCode==0){Sleep(100);c++;if(c>600)break;}//wait for waitResp thread


   delete s;
   return p;
}

char* download_page2(const char *url, char *buf, int iMaxLen, int &iRespContentLen,
                     void (*cb)(void *p, int ok, const char *pMsg), void *cbRet) {
   return download_page2Loc(url, buf, iMaxLen, iRespContentLen, cb, cbRet, "GET", NULL);
}

static void dummy_cb(void *p, int ok, const char *pMsg){
   
}

char* t_post_json(const char *url, char *bufResp, int iMaxLen, int &iRespContentLen, const char *pContent) {

   static int x = 1; //random
   return download_page2Loc(url, bufResp, iMaxLen, iRespContentLen, dummy_cb, &x, "POST", pContent);
}

char* t_send_http_json(const char *url, const char *meth,  char *bufResp, int iMaxLen, int &iRespContentLen, const char *pContent) {
    char bufReq[1024];
    static int x = 2; //random
    snprintf(bufReq, sizeof(bufReq)-10, "%s%s", provisioningLink, url);

    return download_page2Loc(bufReq, bufResp, iMaxLen, iRespContentLen, dummy_cb, &x, meth, pContent);
}



//#define PROV_TEST

int findJSonToken(const char *p, int iPLen, const char *key, char *resp, int iMaxLen){
   
   int iKeyLen=strlen(key);
   resp[0]=0;
   
   for(int i=0;i<iPLen;i++){
      if(i+iKeyLen+3>iPLen)return -1;
      if(p[i]=='"' && p[i+iKeyLen+1]=='"' && p[i+iKeyLen+2]==':' 
         && strncmp(key,&p[i+1],iKeyLen)==0){
         
         i+=iKeyLen+2;
         while(p[i] && p[i]!='"' && i<iPLen)i++;
         if(i>=iPLen)return -2;
         i++;
         
         int l=0;
         iMaxLen--;
         
         while(p[i] && p[i]!='"' && l<iMaxLen && i<iPLen){resp[l]=p[i];i++;l++;}
         if(i>=iPLen)return -2;
         resp[l]=0;
         
         return l;
      }
   }
   
   return 0;
}

/*
 * getDomainAuthURL() - Queries the SC server with a user-provided domainname.
 * Returns the authentication URL, typically for an ADFS server, to be loaded
 * into a WebView.
 */

int getDomainAuthURL(const char *pLink, const char *pUsername, char *auth_url, int auth_sz, void (*cb)(void *p, int ok, const char *pMsg), void *cbRet) {
   int iRespContentLen=0;
   char bufJSonValue[32]; /* "auth_url" -> authURL */
   char bufResp[4096];
   char *p=NULL;
   char *pUN=NULL;
   int  len=0;

   memset(bufResp, 0, sizeof(bufResp));
   p = download_page2Loc(pLink, bufResp, sizeof(bufResp) - 1, iRespContentLen, cb, cbRet, "GET", NULL);

   cb(cbRet, 1, "JSON from ");
   cb(cbRet, 1, pLink);
   if (p == NULL) {
      // Failed to download JSON
      cb(cbRet, 0, "Please check network connection.");
      return -1;
   }

   /* FIXME: We should *really* be using a proper JSON library in tiviengine/. */

   /*
    * Example JSON response:
    *
    * {
    *   "auth_type": "adfs",
    *   "can_do_username": true,
    *   "auth_url": "https://ad.lakedaemon.net/adfs/oauth2/authorize?client_id=myclientid3&resource=https://enterprise.silentcircle.com/adfs/trust&response_type=code&redirect_uri=silentcircle-entapi://redirect"
    * }
    */

   /* check auth_type */
   memset(bufJSonValue, 0, sizeof(bufJSonValue));
   len = findJSonToken(p, iRespContentLen, "auth_type", bufJSonValue, sizeof(bufJSonValue) - 1);
   if(len <= 0) {
      cb(cbRet, -1, "ERR: JSon field 'auth_type' not found");
      return -1;
   }

   if (strcmp(bufJSonValue, "adfs") != 0) {
      cb(cbRet, -1, "Server reported auth_type other than adfs!");
      return -1;
   }

   /* get the authentication URL */
   memset(bufJSonValue, 0, sizeof(bufJSonValue));
   len = findJSonToken(p, iRespContentLen, "auth_url", auth_url, auth_sz - 1);
   if(len <= 0) {
      cb(cbRet, -1, "ERR: JSon field 'auth_url' not found");
      return -1;
   }

   pUN = auth_url + len;

   /* can we append the username? */
   memset(bufJSonValue, 0, sizeof(bufJSonValue));
   len = findJSonToken(p, iRespContentLen, "can_do_username", bufJSonValue, sizeof(bufJSonValue) - 1);
   if(len <= 0) {
      /* No need to hard fail here, we just don't get to auto-fill the username on the webpage */
      cb(cbRet, 0, "WARN: JSon field 'can_do_username' not found");
      return 0;
   }

   /* afawct, most webpages will either use it, or ignore it.  So, lazily default to adding it. */
   if (strcmp(bufJSonValue, "false") != 0) {
      unsigned int maxlen = auth_sz - (pUN - auth_url + 1);

      if (strnlen(pUsername, maxlen - strlen("&username=")) == maxlen - strlen("&username=")) {
         cb(cbRet, 0, "Username would have been truncated, not auto-filling webpage");
         return 0;
      }

      snprintf(pUN, maxlen, "&username=%s", pUsername);
   }

   return 0;
}

static int getToken(const char *pLink, char *resp, int iMaxLen, void (*cb)(void *p, int ok, const char *pMsg), void *cbRet, const char *pReq="GET", const char *pContent=NULL){
   
   int iRespContentLen=0;
   
   int l;
   
   char bufResp[4096];
   char bufJSonValue[1024];
   
#if 0
   const char *pTest="{\"api_key\": \"z46d3856f8ff292f2eb8dab4e5e51edf5b951fb6e6eb01c80662157z\", \"result\": \"success\"}";
   
   iRespContentLen=strlen(pTest);
   l=findJSonToken(pTest,iRespContentLen,"api_key",&bufResp[0],1023);
   if(l>0)printf("token=[%.*s]\n",l,bufResp);

   l=findJSonToken(pTest,iRespContentLen,"result",&bufResp[0],4095);
   if(l>0)printf("token=[%.*s]\n",l,bufResp);
   exit(1);
#endif
   
   
   memset(bufResp,0,sizeof(bufResp));

   
   char *p=download_page2Loc(pLink, &bufResp[0], sizeof(bufResp)-50, iRespContentLen,cb,cbRet, pReq, pContent);
   
   if(!p){
      cb(cbRet,0,"Please check network connection.");//download json fail
      return -1;
   }
#ifdef PROV_TEST
    
    //09/08/15 per JC in Messages
    printf("pLink = [%s]\n", pLink); 
    printf("pContent = [%s]\n", pContent);
    //------------------------------------
    
   printf("rec[%.*s]\n",iRespContentLen,p);//rem
   printf("rec-t[%s]\n",bufResp);//rem
#endif
   l=findJSonToken(p,iRespContentLen,"result",&bufJSonValue[0],1023);
   if(l<=0){
      cb(cbRet,0,"ERR: Result is not found");
      return -1;
   }
   if(strcmp(&bufJSonValue[0],"success")){
      l=findJSonToken(p,iRespContentLen,"error_msg",&bufJSonValue[0],1023);
      if(l>0)
         cb(cbRet,-1,&bufJSonValue[0]);
      else{
         cb(cbRet,-1,"Could not download configuration!");
      }
      return -1;
   }
   
   
   l=findJSonToken(p,iRespContentLen,"api_key",&bufJSonValue[0],1023);
   if(l<=0 || l>256 || l>iMaxLen){
      cb(cbRet,0,"ERR: Find api_key failed");
      return -1;
   }
   int ret=snprintf(resp,iMaxLen,"%s",&bufJSonValue[0]);
   resp[iMaxLen]=0;
   
#if defined(__APPLE__)
#ifndef PROV_TEST
   int storeAPIKeyToKC(const char *p);
   storeAPIKeyToKC(resp);
#endif
#endif
   
   return ret;
}

const char *pFN_to_save[]  ={"settings.txt","tivi_cfg10555.xml","tivi_cfg.xml","tivi_cfg1.xml",NULL};
int isFileExistsW(const short *fn);
void setCfgFN(CTEditBase &b, int iIndex);
void setCfgFN(CTEditBase &b, const char *fn);

void delProvFiles(const int *p, int iCnt){
   CTEditBase b(1024);
   for(int i=0;i<2;i++){
      if(!pFN_to_save[i])break;
      setCfgFN(b,pFN_to_save[i]);
      deleteFileW(b.getText());
   }
   
   char buf[64];
   
   for(int i=0;i<iCnt;i++){
      printf("del prov %d\n",p[i]);
      if(p[i]==1)continue;//dont delete if created by user
      if(i)snprintf(buf, sizeof(buf)-1, "tivi_cfg%d.xml", i); else strcpy(buf,"tivi_cfg.xml");
      setCfgFN(b,buf);
      deleteFileW(b.getText());
   }
}

static int iProvisioned=-1;//unknown


static char bufAPIKey[1024]="";

const char *getAPIKey(){
#if defined(__APPLE__)
   
   if(!bufAPIKey[0]){
      const char *getAPIKeyFromKC(void);
      const char *k = getAPIKeyFromKC();
      if(k && k[0]){
         puts("key-KC ok");
         strncpy(bufAPIKey, k, sizeof(bufAPIKey));
         bufAPIKey[sizeof(bufAPIKey)-1]=0;
      }
      else{
         //download API key using SIP UN and PASS
         puts("download");
         int getApiKeySipUserPassNoCB(const char *pSipUN, const char *pSipPWD);
         
         char* findSZByServKey(void *pEng, const char *key);
         void *getAccountByID(int idx);
         void *a = getAccountByID(0);
         if(a){
            
            char* un = findSZByServKey(a, "un");
            char* pwd = findSZByServKey(a, "pwd");//sip pass

            getApiKeySipUserPassNoCB(un, pwd);//It can lock the getAPIKey up to 60sec
         }
      }
      return &bufAPIKey[0];
   }
   
#endif
   return &bufAPIKey[0];
}
/*

*/

int provClearAPIKey(){
   int ret=bufAPIKey[0];
   memset(bufAPIKey, 0, sizeof(bufAPIKey));
   return ret?0:-1;//if we had a key return 0
}

int checkProvWithAPIKey(const char *aAPIKey, void (*cb)(void *p, int ok, const char *pMsg), void *cbRet);


int checkProv(const char *pUserCode, void (*cb)(void *p, int ok, const char *pMsg), void *cbRet){
   /*
    http://sccps.silentcircle.com/provisioning/silent_phone/tivi_cfg.xml?api_key=12345
    http://sccps.silentcircle.com/provisioning/silent_phone/settings.txt?api_key=12345
    http://sccps.silentcircle.com/provisioning/silent_phone/tivi_cfg_glob.txt?api_key=12345
    */
   char bufReq[1024];
   const char *t_getDevID_md5();
   const char *t_getDev_name();
   
   const char *dev_id=t_getDevID_md5();
   const char *dev_name=t_getDev_name();
   

#define CHK_BUF \
   if(l+100>sizeof(bufReq)){\
      return -1;\
   }

   int l=snprintf(bufReq, sizeof(bufReq)-10, "%s/provisioning/use_code/?provisioning_code=", provisioningLink);
   
   CHK_BUF 
   
   l+=fixPostEncodingToken(&bufReq[l],sizeof(bufReq)-10-l,pUserCode,strlen(pUserCode));
   
   CHK_BUF
   
   l+=snprintf(&bufReq[l],sizeof(bufReq)-10-l,"&device_id=%s&device_name=",dev_id);
   
   CHK_BUF 
   
   l+=fixPostEncodingToken(&bufReq[l],sizeof(bufReq)-10-l, dev_name,strlen(dev_name));
   
   CHK_BUF
   
#undef CHK_BUF
   
   
   int r=getToken(&bufReq[0], &bufAPIKey[0],255,cb,cbRet);
   if(r<0){

      return -1;
   }
   
   cb(cbRet,1,"Configuration code ok");
   
   return checkProvWithAPIKey(&bufAPIKey[0],cb, cbRet);;
}

static void copyJSON_value(char *dst, const char *src, int iMax){
   iMax--;
   for(int i=0;i<iMax;i++){
      if(!*src)break;
      if(*src=='"' || *src=='\\'){*dst='\\';dst++;i++;}
      *dst=*src;
      dst++;
      src++;
   }
   *dst=0;
}
/*
 #SP-650
 
 POST https://sccps.silentcircle.com/v1/me/device/{device_id}/api-key/
 
 Request payload:
 {"username":"xxx","sip_password":"xyz"}
 
 Response payload:
 {"apikey": "sfsdfdsfs", "result": "success"}
 */
//the getApiKeySipUserPass will store an API key into the KC and the bufAPIKey;

int getApiKeySipUserPass(const char *pSipUN, const char *pSipPWD, void (*cb)(void *p, int ok, const char *pMsg), void *cbRet){
   
   /*
    /v1/me/device/[device_id]/
    http://sccps.silentcircle.com/provisioning/silent_phone/tivi_cfg.xml?api_key=12345
    http://sccps.silentcircle.com/provisioning/silent_phone/settings.txt?api_key=12345
    http://sccps.silentcircle.com/provisioning/silent_phone/tivi_cfg_glob.txt?api_key=12345
    */
   char bufReq[1024];
   char bufContent[1024];

   const char *t_getDevID_md5();
   const char *dev_id=t_getDevID_md5();
   
   
#define CHK_BUF \
   if(l+100>sizeof(bufReq)){\
      return -1;\
   }
   
   int l=snprintf(bufReq,sizeof(bufReq)-10,"%s/v1/me/device/%s/api-key/",provisioningLink,dev_id);
   
   CHK_BUF
   
   char locPassword[128];
   copyJSON_value(locPassword, pSipPWD, sizeof(locPassword)-1);
   char locUN[128];
   copyJSON_value(locUN, pSipUN, sizeof(locUN)-1);
   
   
   l = snprintf(bufContent, sizeof(bufContent),
                "{\r\n"
                "\"username\": \"%s\",\r\n"
                "\"sip_password\": \"%s\"\r\n"
                 "}\r\n",locUN, locPassword);//TODO encode pwd

   
#undef CHK_BUF
   
   
   int r=getToken(&bufReq[0], &bufAPIKey[0],255,cb,cbRet,"POST",bufContent);
   if(r<0){
      return -1;
   }
   
   cb(cbRet,1,"Configuration code ok");
   return 0;
}

int checkProvAuthCookie(const char *pUN, const char *auth_cookie, const char *pdevID, void (*cb)(void *p, int ok, const char *pMsg), void *cbRet){
   /*
    /v1/me/device/[device_id]/
    http://sccps.silentcircle.com/provisioning/silent_phone/tivi_cfg.xml?api_key=12345
    http://sccps.silentcircle.com/provisioning/silent_phone/settings.txt?api_key=12345
    http://sccps.silentcircle.com/provisioning/silent_phone/tivi_cfg_glob.txt?api_key=12345
    */
   char bufReq[1024];
   char bufContent[4096];
   const char *t_getDevID_md5();
   const char *t_getDev_name();
   const char *t_getVersion();
   const char *dev_id=t_getDevID_md5();
   const char *dev_name=t_getDev_name();
   char locAuthCookie[2048];
   char locUN[128];
   
   
#define CHK_BUF \
if(l+100>sizeof(bufReq)){\
return -1;\
}
   
   int l=snprintf(bufReq,sizeof(bufReq)-10,"%s/v1/me/device/%s/",provisioningLink,dev_id);
   
   CHK_BUF
   
#ifdef __APPLE__
   const char *dev_class = "ios";
#endif

#if defined(_WIN32) || defined(_WIN64)
   const char *dev_class = "windows";
#endif

#if defined(ANDROID_NDK)
   const char *dev_class = "android";
#endif

#if defined(__linux__) && !defined(ANDROID_NDK)
   const char *dev_class = "Linux";
#endif

   copyJSON_value(locAuthCookie, auth_cookie, sizeof(locAuthCookie)-1);
   copyJSON_value(locUN, pUN, sizeof(locUN)-1);
   
   l = snprintf(bufContent, sizeof(bufContent),
                "{\r\n"
                   "\"username\": \"%s\",\r\n"
                   "\"auth_type\": \"adfs\",\r\n"
                   "\"auth_cookie\": \"%s\",\r\n"
                   "\"device_name\": \"%s\",\r\n"
#if defined (_WIN32) || defined(_WIN64)
				   "\"app\": \"silent_phone_free\",\r\n"
#else
				   "\"app\": \"silent_phone\",\r\n"
#endif
                 "\"persistent_device_id\": \"%s\",\r\n"
                   "\"device_class\": \"%s\",\r\n"
                   "\"version\": \"%s\"\r\n"
                "}\r\n", locUN, locAuthCookie, dev_name, pdevID, dev_class, t_getVersion());
   
#undef CHK_BUF
   
    // 09/08/15 for logging per JC in Messages
    cb(cbRet,1,bufContent);
    
   int r=getToken(&bufReq[0], &bufAPIKey[0],255,cb,cbRet,"PUT",bufContent);
   if(r<0){
      return -1;
   }
   
   cb(cbRet,1,"Configuration code ok");
   
   return checkProvWithAPIKey(&bufAPIKey[0],cb, cbRet);;
}

int checkProvUserPass(const char *pUN, const char *pPWD, const char *pdevID, void (*cb)(void *p, int ok, const char *pMsg), void *cbRet){
   /*
    /v1/me/device/[device_id]/
    http://sccps.silentcircle.com/provisioning/silent_phone/tivi_cfg.xml?api_key=12345
    http://sccps.silentcircle.com/provisioning/silent_phone/settings.txt?api_key=12345
    http://sccps.silentcircle.com/provisioning/silent_phone/tivi_cfg_glob.txt?api_key=12345
    */
   char bufReq[1024];
   char bufContent[1024];
   const char *t_getDevID_md5();
   const char *t_getDev_name();
   const char *t_getVersion();
   const char *dev_id=t_getDevID_md5();
   const char *dev_name=t_getDev_name();
   
   
#define CHK_BUF \
if(l+100>sizeof(bufReq)){\
return -1;\
}
   
   int l=snprintf(bufReq,sizeof(bufReq)-10,"%s/v1/me/device/%s/",provisioningLink,dev_id);
   
   CHK_BUF
   
#ifdef __APPLE__
   const char *dev_class = "ios";
#endif

#if defined(_WIN32) || defined(_WIN64)
   const char *dev_class = "windows";
#endif

#if defined(ANDROID_NDK)
   const char *dev_class = "android";
#endif

#if defined(__linux__) && !defined(ANDROID_NDK)
   const char *dev_class = "Linux";
#endif

   char locPassword[128];
   copyJSON_value(locPassword, pPWD, sizeof(locPassword)-1);
   char locUN[128];
   copyJSON_value(locUN, pUN, sizeof(locUN)-1);
   
   
   l = snprintf(bufContent, sizeof(bufContent),
                "{\r\n"
                   "\"username\": \"%s\",\r\n"
                   "\"password\": \"%s\",\r\n"
                   "\"device_name\": \"%s\",\r\n"
#if defined (_WIN32) || defined(_WIN64)
				   "\"app\": \"silent_phone_free\",\r\n"
#else
				   "\"app\": \"silent_phone\",\r\n"
#endif
                 "\"persistent_device_id\": \"%s\",\r\n"
                   "\"device_class\": \"%s\",\r\n"
                   "\"version\": \"%s\"\r\n"
                "}\r\n",locUN, locPassword, dev_name, pdevID, dev_class, t_getVersion());//TODO encode pwd
   
   CHK_BUF

#undef CHK_BUF
   
   
   int r=getToken(&bufReq[0], &bufAPIKey[0],255,cb,cbRet,"PUT",bufContent);
   if(r<0){
      return -1;
   }
   
   cb(cbRet,1,"Configuration code ok");
   
   return checkProvWithAPIKey(&bufAPIKey[0],cb, cbRet);;
}

static int t_addJSON(int canTrim, char *pos, int iSize, const char *tag, const char *value){
   
   char bufJSonValue[1024];
   copyJSON_value(bufJSonValue, value, sizeof(bufJSonValue)-1);
   
   if(canTrim)trim(bufJSonValue);
   
   if(!bufJSonValue[0] || iSize < 120 || strlen(value) > 80)return 0;
   
   return snprintf(pos, iSize, "\"%s\":\"%s\",", tag, bufJSonValue);
}

int createUserOnWeb(const char *pUN, const char *pPWD,
                    const char *pEM, const char *pFN, const char *pLN,
                    void (*cb)(void *p, int ok, const char *pMsg), void *cbRet){

   int c, l = 0;
   int iRespContentLen=0;
   
   char bufResp[4096];
   char bufBody[4096];

   
   
   char url[1024];
   int ul = snprintf(url,sizeof(url)-10,"%s/v1/user/",provisioningLink);
   
   ul+=fixPostEncodingToken(&url[ul],sizeof(url)-10-ul,pUN,strlen(pUN));
   url[ul] = '/'; ul++; url[ul]=0;
 
   bufBody[0]='{';l = 1;
   
   c = t_addJSON(1, &bufBody[l], sizeof(bufBody)-l, "username", pUN);
   if(!c){
      cb(cbRet,0,"Please check Username field.");
      return -1;
   }
   l+=c;
   
   c = t_addJSON(0 , &bufBody[l], sizeof(bufBody)-l, "password", pPWD);
   if(!c){
      cb(cbRet,0,"Please check Password field.");
      return -1;
   }
   l+=c;
   
   c = t_addJSON(1 , &bufBody[l], sizeof(bufBody)-l, "email", pEM);
   if(!c){
      cb(cbRet,0,"Please check Email field.");
      return -1;
   }
   l+=c;
   
   c = t_addJSON(1 , &bufBody[l], sizeof(bufBody)-l, "first_name", pFN); l+=c;
   c = t_addJSON(1 , &bufBody[l], sizeof(bufBody)-l, "last_name", pLN); l+=c;

   bufBody[l - 1] = '}';//remove last , from JSON
   
   memset(bufResp,0,sizeof(bufResp));
   
   char *p=download_page2Loc(url, &bufResp[0], sizeof(bufResp)-50, iRespContentLen,cb,cbRet, "PUT", bufBody);
   
   if(!p){
      cb(cbRet,0,"Please check network connection.");//download json fail
      return -1;
   }
#ifdef PROV_TEST
   printf("rec[%.*s]\n",iRespContentLen,p);//rem
   printf("rec-t[%s]\n",bufResp);//rem
#endif
   
   /*
    {"last_name": "N", "hash": "7c4219a8bcdbfe71aaa7381a72c0b57d3471ee39", "keys": [], "active_st_device": null, "country_code": "", "silent_text": true, "subscription": {"expires": "1900-01-01T00:00:00Z", "has_expired": true}, "first_name": "J", "display_name": "J N", "avatar_url": null, "silent_phone": false, "force_password_change": false, "permissions": {"can_send_media": true, "silent_text": true, "can_receive_voicemail": false, "silent_desktop": false, "silent_phone": false, "conference_create"
    */
   
   char bufJSonValue[1024];
   l=findJSonToken(p,iRespContentLen,"result",&bufJSonValue[0],1023);
   if(l<=0){
      cb(cbRet,0,"ERR: Result is not found");
      return -1;
   }
   if(strcmp(&bufJSonValue[0],"success")){
      l=findJSonToken(p,iRespContentLen,"error_msg",&bufJSonValue[0],1023);
      if(l>0)
         cb(cbRet,-1,&bufJSonValue[0]);
      else{
         cb(cbRet,-1,"Could not download configuration!");
      }
      return -1;
   }
   
   void saveCfgFile(const char *fn, void *p, int iLen);
   saveCfgFile("userData.json", bufResp, iRespContentLen);

   return 0;
}

int checkUserCreate(const char *pUN, const char *pPWD, const char *pdevID,
                    const char *pEM, const char *pFN, const char *pLN,
                    void (*cb)(void *p, int ok, const char *pMsg), void *cbRet){
   
   int r = createUserOnWeb(pUN, pPWD, pEM, pFN, pLN, cb, cbRet);
   if(r < 0)return r;
   
   return checkProvUserPass(pUN, pPWD, pdevID, cb, cbRet);
}

int checkProvWithAPIKey(const char *pAPIKey, void (*cb)(void *p, int ok, const char *pMsg), void *cbRet){
   

   char bufReq[1024];
   char bufCfg[4096];
   
   const char *pFN_to_download[]   = {"settings.txt","tivi_cfg_glob.txt","tivi_cfg.xml","tivi_cfg1.xml",NULL};

   const char *pFNErr[]={"D-Err1","D-Err2","D-Err3","D-Err4","D-Err5","D-Err6",NULL};
   
   const char *p10_200ok="HTTP/1.0 200 OK";
   const char *p11_200ok="HTTP/1.1 200 OK";
   
   int iLen200ok=strlen(p10_200ok);
   
   int iCfgPos=0;
   
   for(int i=0;;i++){
      if(!pFN_to_download[i] || !pFN_to_save[i])break;
      snprintf(bufReq,sizeof(bufReq)-1,"%s/provisioning/silent_phone/%s?api_key=%s",provisioningLink,pFN_to_download[i],pAPIKey);
#ifdef ANDROID
      androidLog("++++ Provisioning request: %s", bufReq);
#endif
      int iRespContentLen=0;
      char* p=download_page2(&bufReq[0], &bufCfg[0], sizeof(bufCfg)-100, iRespContentLen,cb,cbRet);
      if(!p && i>2){
         // we have 1 account
         break;
      }

      if(!p || (strncmp(&bufCfg[0],p10_200ok,iLen200ok) && strncmp(&bufCfg[0],p11_200ok,iLen200ok))){
         if(i>2){
            // we have 1 account
            break;
         }
         char b[1000]; snprintf(b, sizeof(b), "Cannot load: %s, code: %.*s", pFN_to_download[i], 990, bufCfg);
        cb(cbRet,0, b);
//         cb(cbRet,0,pFNErr[i]);
         return -2;
      }
      cb(cbRet,1,pFN_to_save[i]);

      void saveCfgFile(const char *fn, void *p, int iLen);
      int saveCfgFile(int iNextPosToTest, void *p, int iLen);
#if 0
#ifndef PROV_TEST
      saveCfgFile(pFN_to_save[i],p,iRespContentLen);
#endif

      printf("Saving %s content=[%.*s]\n",pFN_to_save[i], iRespContentLen,p);
#else
      
      if(strncmp("tivi_cfg", pFN_to_save[i],8) || 0==strcmp("tivi_cfg_glob.txt", pFN_to_download[i])){
#ifndef PROV_TEST
         saveCfgFile(pFN_to_save[i],p,iRespContentLen);
#endif
         
       //  printf("Saving %s content=[%.*s]\n",pFN_to_save[i], iRespContentLen,p);
      }
      else{
         iCfgPos=saveCfgFile(iCfgPos, p,iRespContentLen);
         //printf("Saving pos=%d content=[%.*s]\n",iCfgPos-1, iRespContentLen,p);
      }
      
#endif
   }
   cb(cbRet,1,"OK");
   iProvisioned=1;
   return 0;
}

void setFileBackgroundReadable(CTEditBase &b);
void setOwnerAccessOnly(const short *fn);

int saveCfgFile(int iNextPosToTest, void *p, int iLen){

   char fn[64];
   CTEditBase b(1024);
#define MAX_CFG_FILES 10000
   
   for(int i=iNextPosToTest;i<MAX_CFG_FILES;i++){
      if(i)snprintf(fn, sizeof(fn)-1, "tivi_cfg%d.xml", i); else strcpy(fn,"tivi_cfg.xml");
      setCfgFN(b, fn);
      if(!isFileExistsW(b.getText())){
         //save into i pos
         iNextPosToTest=i+1;
         break;
      }
   }

   saveFileW(b.getText(),p,iLen);
   setOwnerAccessOnly(b.getText());
   setFileBackgroundReadable(b);
   
   return iNextPosToTest;
}



void saveCfgFile(const char *fn, void *p, int iLen){
   
   CTEditBase b(1024);
   setCfgFN(b,fn);
   saveFileW(b.getText(),p,iLen);
   
   setOwnerAccessOnly(b.getText());
   setFileBackgroundReadable(b);
}
void tivi_log1(const char *p, int val);

int isProvisioned(int iCheckNow){
#ifdef PROV_TEST
   return 0;
#endif

   if(iProvisioned!=-1 && !iCheckNow)return iProvisioned;
   
   CTEditBase b(1024);
   
//   setCfgFN(b,0);
   
   do{
      iProvisioned=0;
      /*
      if(isFileExistsW(b.getText())){
         iProvisioned=1;
         break;
      }
      */
      int getGCfgFileID();
      setCfgFN(b,getGCfgFileID());
      if(isFileExistsW(b.getText())){
         setCfgFN(b,0);
         if(isFileExistsW(b.getText())){
            iProvisioned=1;
            break;
         }
         setCfgFN(b,1);
         if(isFileExistsW(b.getText())){
            iProvisioned=1;
            break;
         }
         break;
      }
      
      tivi_log1("isProvisioned fail ",getGCfgFileID());
      
   }while(0);
   //int isFileExists(const char *fn);
   return iProvisioned;
}

//-----------------android-------------------

class CTProvNoCallBack{
   int iSteps;
public:
   int iHasData;
   int iProvStat;
   int okCode;
   char bufMsg[256];

   
   CTProvNoCallBack(){
      reset();
   }
   void setSteps(int i){iSteps=i;}
   void reset(){
      
      memset(bufMsg, 0, sizeof(bufMsg));
      iHasData=0;
      okCode=0;
      iProvStat=0;
      
   }
   void provCallBack(void *p, int ok, const char *pMsg){
      
      //if(pMsg)tmp_log(pMsg);
      if(ok<=0){
         if(okCode<ok)return;
         okCode=ok;
         strncpy(bufMsg,pMsg,sizeof(bufMsg)-2);
         bufMsg[sizeof(bufMsg)-1]=0;

      }
      else{
         if(!iSteps)iSteps=14;
         iProvStat++;
         int res=iProvStat*100/iSteps;
         if(res>100)res=100;
         sprintf(bufMsg,"%d %% done",res);
         //progress
      }
      iHasData++;
   }
   const char *getInfo(){
      iHasData=0;
      return &bufMsg[0];
   }
   
};

CTProvNoCallBack provNCB;

static void provCallBack(void *p, int ok, const char *pMsg){
   provNCB.provCallBack(p, ok, pMsg);
}

const char* getProvNoCallBackResp(){
   return provNCB.getInfo();
}
//prov.tryGetResult
//porv.start=code
int checkProvNoCallBack(const char *pUserCode){
   provNCB.reset();
   provNCB.setSteps(14);
   return checkProv(pUserCode, provCallBack, &provNCB);
}

int checkProvAPIKeyNoCallBack(const char *pApiKey){
   provNCB.reset();
   provNCB.setSteps(11);
   return checkProvWithAPIKey(pApiKey, provCallBack, &provNCB);
}

int getApiKeySipUserPassNoCB(const char *pSipUN, const char *pSipPWD){
   provNCB.reset();
   provNCB.setSteps(11);
   return  getApiKeySipUserPass(pSipUN, pSipPWD,provCallBack, &provNCB);
}

/*
-(void)cbTLS:(int)ok  msg:(const char*)msg {
   NSLog(@"prov=[%s] %d",msg,ok);
   
   if(ok<=0){
      if(iPrevErr==-2)return;
      iPrevErr=ok;
      dispatch_async(dispatch_get_main_queue(), ^{
         
         [self showMsgMT:@"Can not download configuration, check code ID and try again."  msg:msg];
      });
   }
   else{
      iProvStat++;
      
      dispatch_async(dispatch_get_main_queue(), ^{
         float f=(float)iProvStat/14.;
         if(f>1.)f=1.;
         [uiProg setProgress:f animated:YES];
      });
   }
}

 */





