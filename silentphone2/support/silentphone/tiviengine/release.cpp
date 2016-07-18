
#define SC_TEST_CODE

#ifdef SC_TEST_CODE
int isRelease(){return 0;}

int mustUseTLS(){return 0;}

int mustCheckTLSCert(){return 0;}

int mustUseZRTP(){return 0;}

int mustUseSDES(){return 0;}

int canAddAccounts(){return 1;}

int canEnableCFG(){return 1;}

#else

int isRelease(){return 1;}

int mustUseTLS(){return 1;}

int mustCheckTLSCert(){return 1;}

int mustUseZRTP(){return 1;}

int mustUseSDES(){return 1;}

int canAddAccounts(){return 0;}

int canEnableCFG(){return 0;}

#endif