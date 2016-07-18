#ifndef _T_MSGING_H
#define _T_MSGING_H

typedef struct{
   char buf[1024];
   int iLen;
   int addr;
   int port;

   char bufResp[16384];
   int iRespLen;
}HTTP_MSG_SEND;

#endif
