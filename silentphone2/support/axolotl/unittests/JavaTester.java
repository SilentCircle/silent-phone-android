import axolotl.AxolotlNative;

public class JavaTester extends AxolotlNative {

    // exactly 32 bytes for a 256bit key
    static final byte[] passphrase = {1,2,3,4,5,6,7,8,9,0,0,9,8,7,6,5,4,3,2,1,1,3,5,7,9,2,4,6,8,0,4,2};
    static {
        System.loadLibrary("axolotl++");
    }

    public int receiveMessage(byte[] messageDescriptor, byte[] attachementDescriptor, byte[] messageAttributes) {
        return 0;
    }

    public void messageStateReport(long messageIdentfier, int statusCode, byte[] stateInformation) { 
    }

    public void notifyCallback(int notifyActionCode, byte[] actionInformation, byte[] deviceId) {
    }

    public byte[] httpHelper(byte[] requestUri, String method, byte[] requestData, int[] code) {
        String uri = new String(requestUri);
        String data = new String(requestData);
        byte[] retVal = null;
        try {
            retVal = new String("From networköäüß").getBytes("UTF-8");
        }
        catch (Exception ex) {
            System.out.println("Got an exception: " + ex);
        }
        code[0] = 200;

        System.out.println("method: " + method);
        System.out.println("uri: " + uri);
        System.out.println("data: " + data);
        return retVal;
    }

    public static void main(String[] args) {
        System.out.println("Hello, JavaTester!");

        JavaTester tester = new JavaTester();
        try {
            int initResult = tester.doInit(1, ":memory:", passphrase, new String("wernerd").getBytes("UTF-8"), 
                                           new String("someapikey").getBytes("UTF-8"), new String("somedevid").getBytes("UTF-8"));
            System.out.println("initResult: " + initResult + ", pwlength: " + passphrase.length);

            final byte[] msg = new String("Hello, Axolotl! öäüß").getBytes("UTF-8");
            System.out.println("Byte array length: " + msg.length);
            long[] msgIds = tester.sendMessage(msg, null, null);
            if (msgIds == null) {
                System.out.println("Error code: " + tester.getErrorCode());
                System.out.println("Error info: " + tester.getErrorInfo());
            }

            final byte[] attachment = new String("This is an attachement").getBytes("UTF-8");
            msgIds = tester.sendMessage(msg, attachment, null);

            final byte[] attributes = new String("This is a message attribute").getBytes("UTF-8");
            msgIds = tester.sendMessage(msg, attachment, attributes);

            byte[] names = tester.getKnownUsers();
            System.out.println("Names array length: " + names.length);
            String namesString = new String(names);
            System.out.println("Names: " + namesString);
            
            tester.testCommand("http", new String("some data to request").getBytes("UTF-8"));
        }
        catch (Exception ex) {
            System.out.println("Got an exception: " + ex);
        }
    }
}
