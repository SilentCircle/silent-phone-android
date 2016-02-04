import axolotl.AxolotlNative;

public class CloudTester {

    static {
        System.loadLibrary("axolotl++");
    }

    // typical chunk size
    static byte[] bigData = new byte[64*1024];
    static final String metadataBig = "This is bigger metadata";
    
    private static long cloudRef;

    public static void main(String[] args) {
        System.out.println("Hello, CloudTester!");

        try {
        
            byte[] metadata = metadataBig.getBytes("UTF-8");

            int[] code = new int[1];
            byte[] someRandom = {0,1,2,3,4,5,6,7,8,9};
            cloudRef = AxolotlNative.cloudEncryptNew(someRandom, bigData, metadata, code);

            if (code[0] != 0) {
                System.out.println("cloudEncryptNew code: " + code[0]);
                return;
            }
            System.out.println("cloudEncryptNew done.");
            
            code[0] = AxolotlNative.cloudCalculateKey(cloudRef);
            if (code[0] != 0) {
                System.out.println("cloudCalculateKey code: " + code[0]);
                return;
            }
            System.out.println("cloudCalculateKey done.");
            
            byte[] locatorRest = AxolotlNative.cloudEncryptGetLocatorREST(cloudRef, code);
            if (code[0] != 0) {
                System.out.println("cloudEncryptGetLocatorREST code: " + code[0]);
                return;
            }
            System.out.println("cloudEncryptGetLocatorREST done: " + new String(locatorRest));
            
            byte[] keyInfo = AxolotlNative.cloudEncryptGetKeyBLOB(cloudRef, code);
            if (code[0] != 0) {
                System.out.println("cloudEncryptGetKeyBLOB code: " + code[0]);
                return;
            }
            System.out.println("cloudEncryptGetKeyBLOB done: " + new String(keyInfo));

            byte[] segmentInfo = AxolotlNative.cloudEncryptGetSegmentBLOB(cloudRef, 1, code);
            if (code[0] != 0) {
                System.out.println("cloudEncryptGetSegmentBLOB code: " + code[0]);
                return;
            }
            System.out.println("cloudEncryptGetSegmentBLOB done: " + new String(segmentInfo));
            
            byte[] encryptedChunk = AxolotlNative.cloudEncryptNext(cloudRef, code);
            if (code[0] != 0) {
                System.out.println("cloudEncryptNext code: " + code[0]);
                return;
            }
            System.out.println("cloudEncryptNext done, length: " + encryptedChunk.length);

            // OK, release the context
            AxolotlNative.cloudFree(cloudRef);
            

            // Without randome data to re-hash the locator
            cloudRef = AxolotlNative.cloudEncryptNew(null, bigData, metadata, code);

            if (code[0] != 0) {
                System.out.println("cloudEncryptNew code: " + code[0]);
                return;
            }
            System.out.println("cloudEncryptNew done.");
            
            code[0] = AxolotlNative.cloudCalculateKey(cloudRef);
            if (code[0] != 0) {
                System.out.println("cloudCalculateKey code: " + code[0]);
                return;
            }
            System.out.println("cloudCalculateKey done.");
            
            locatorRest = AxolotlNative.cloudEncryptGetLocatorREST(cloudRef, code);
            if (code[0] != 0) {
                System.out.println("cloudEncryptGetLocatorREST code: " + code[0]);
                return;
            }
            System.out.println("cloudEncryptGetLocatorREST done: " + new String(locatorRest));
            
            keyInfo = AxolotlNative.cloudEncryptGetKeyBLOB(cloudRef, code);
            if (code[0] != 0) {
                System.out.println("cloudEncryptGetKeyBLOB code: " + code[0]);
                return;
            }
            System.out.println("cloudEncryptGetKeyBLOB done: " + new String(keyInfo));

            segmentInfo = AxolotlNative.cloudEncryptGetSegmentBLOB(cloudRef, 1, code);
            if (code[0] != 0) {
                System.out.println("cloudEncryptGetSegmentBLOB code: " + code[0]);
                return;
            }
            System.out.println("cloudEncryptGetSegmentBLOB done: " + new String(segmentInfo));
            
            encryptedChunk = AxolotlNative.cloudEncryptNext(cloudRef, code);
            if (code[0] != 0) {
                System.out.println("cloudEncryptNext code: " + code[0]);
                return;
            }
            System.out.println("cloudEncryptNext done, length: " + encryptedChunk.length);

            // OK, release the context
            AxolotlNative.cloudFree(cloudRef);


            /* ****  Decrypt the data  */
            
            cloudRef = AxolotlNative.cloudDecryptNew (keyInfo, code);
            if (code[0] != 0) {
                System.out.println("cloudDecryptNew code: " + code[0]);
                return;
            }
            System.out.println("cloudDecryptNew done.");
            
            code[0] = AxolotlNative.cloudDecryptNext(cloudRef, encryptedChunk);
            if (code[0] != 0) {
                System.out.println("cloudDecryptNext code: " + code[0]);
                return;
            }
            System.out.println("cloudDecryptNext done.");
            
            byte[] decryptedData = AxolotlNative.cloudGetDecryptedData(cloudRef);
            System.out.println("cloudGetDecryptedData done, length: " + decryptedData.length);

            byte[] decryptedMetaData = AxolotlNative.cloudGetDecryptedMetaData(cloudRef);
            System.out.println("cloudGetDecryptedMetaData done: " + new String(decryptedMetaData));

            // OK, release the context
            AxolotlNative.cloudFree(cloudRef);
        }
        catch (Exception ex) {
            System.out.println("Got an exception: " + ex);
        }
    }
}
