/*
Created by Janis Narbuts
Copyright (C) 2004-2012, Tivi LTD, www.tiviphone.com. All rights reserved.
Copyright (C) 2012-2015, Silent Circle, LLC.  All rights reserved.

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

#include <string.h>
#include "CTLangStrings.h"

#ifdef _WIN32
#define snprintf _snprintf
#endif

CTLangStrings *g_getLang();

static int iLangIsEN=1;

const char *tg_translate(const char *key, int iKeyLen){
   CTLangStrings *l = g_getLang();
   if(!l || iLangIsEN)return key;
   const char *p = l->findByKeyUtf8(key, iKeyLen);
   if(p && p[0]=='<' && strcmp(p+1, "***>")==0)return key;
   return p;
}

void initLang(const char *pDef){
   
   CTLangStrings *lang = g_getLang();
   lang->constrEng();
   
   if(pDef && strncmp(pDef,"en",2)==0)pDef="en";
   
   if(pDef && strcmp(pDef,"en")){
      iLangIsEN=0;
      if(strncmp(pDef,"zh",2)==0)pDef="zh";
      else if(strncmp(pDef,"ja",2)==0)pDef="jp";
      
#if defined(__APPLE__)
      char *iosLoadFile(const char *fn, int &iLen );
      char buf[64];
      snprintf(buf, sizeof(buf), "%s.lang",pDef);
      int iLen=0;
      char *p = iosLoadFile(buf, iLen);
      if(p){
         lang->loadLang(p, iLen);
         delete p;
      }
      lang->translateRes();
      
#else
      //TODO
#endif
   }
}

void translateZRTP_errMsg(CTEditBase &warn, CTEditBase *general, CTEditBase *descr){

#define T_ZRTP_CODE_LEN 7
   
   CTLangStrings *lang = g_getLang();
   if(!general && !descr)return;
   
   if(descr)descr->setText("");
   
   if(!lang || warn.getChar(0)!='s' || warn.getLen()<T_ZRTP_CODE_LEN){
      if(general)general->setText(warn);
      return;
   }
   
   lang->initZRTPMsgs();
   
   enum{eCMP, eCLS, eUPD, eAPP};
   CTEditBase *gm[]={&lang->zrtp_common_cmp, &lang->zrtp_common_cls, &lang->zrtp_common_upd, &lang->zrtp_common_app};
   
   struct{
      const char *code;
      int enum_id;
   }table[]={
      {"s2_c002", eCLS},
      {"s2_c004", eCMP},
      {"s2_c005", eCLS},
      {"s2_c006", eCLS},
      {"s2_c007", eCLS},
      {"s2_c008", eCMP},
      {"s2_c050", eCMP},
      
      {"s3_c001", eCLS},
      {"s3_c002", eCLS},
      {"s3_c003", eCLS},
      {"s3_c004", eCLS},
      {"s3_c005", eCLS},
      {"s3_c006", eCLS},
      {"s3_c007", eCLS},
      {"s3_c008", eCLS},
      
      {"s4_c016", eCLS},
      {"s4_c020", eCLS},
      {"s4_c048", eUPD},
      {"s4_c064", eUPD},
      {"s4_c081", eUPD},
      {"s4_c082", eUPD},
      {"s4_c083", eUPD},
      {"s4_c084", eUPD},
      {"s4_c085", eUPD},
      {"s4_c086", eCLS},
      {"s4_c097", eCLS},
      {"s4_c098", eCLS},
      {"s4_c099", eCLS},
      {"s4_c112", eCLS},
      {"s4_c128", eCLS},
      {"s4_c144", eAPP}
   };
   
   const int sz=sizeof(table)/sizeof(*table);
   
   char bufC[T_ZRTP_CODE_LEN*2];
   
   for(int i=0;i<T_ZRTP_CODE_LEN;i++)bufC[i]=warn.getChar(i);
   
   int ok=0;
   for(int i=0;i<sz;i++){
      if(strncmp(table[i].code, bufC, T_ZRTP_CODE_LEN)==0){
         if(general)general->setText(*gm[table[i].enum_id]);
         ok=1;
         break;
      }
   }
   if(general && !ok)general->setText(warn);
   
   if(descr){
      char key[64+T_ZRTP_CODE_LEN];
      
      int l = snprintf(key, sizeof(key), "zrtp_%.*s_explanation",T_ZRTP_CODE_LEN, bufC);
      int r = lang->setGetTextById(key, l, *descr, 0);
      
      if(!r)descr->setText(warn);//temp
   }
   

}

void CTLangStrings::initZRTPMsgs(){
   
   if(iZRTPInitOk)return;
   iZRTPInitOk=1;
   //--------------------------
   zrtp_common_cmp.setText("You should verbally compare the authentication code with the person you are calling. If it doesn’t match, it indicates that someone may have intercepted the call.");
   zrtp_common_cls.setText("Connection Error. Please close and call again.");
   zrtp_common_upd.setText("The person you are calling needs to update their Silent Phone application to the latest version");
   zrtp_common_app.setText("Application Error. Please re-install Silent Phone.");
   //--------------------------
   
   zrtp_s2_c004_explanation.setText("You should verbally compare this authentication code with your partner. If it doesn’t match, it indicates the presence of a wiretapper.");
   zrtp_s2_c005_explanation.setText("The application received wrong ZRTP security data. Often indicates a bad network connection and you may try to setup a new call.");
   zrtp_s2_c006_explanation.setText("The application received unauthenticated media.  May indicate misrouted packets, network errors, or attempted injection of false media packets from a third party.  You may try to setup a new call.");
   zrtp_s2_c007_explanation.setText("The application received repeated media. Often indicates a bad network connection and you may try to setup a new call.");
   zrtp_s2_c008_explanation.setText("You must verbally compare the authentication code with your partner. If it doesn’t match, it indicates the presence of a wiretapper.");
   zrtp_s2_c050_explanation.setText("This may indicate a software error, or a possible attempted attack.");
   zrtp_s3_c001_explanation.setText("ZRTP Hello message integrity failure. May indicate software error or possible attack. Close this call and setup a new call.");
   zrtp_s3_c002_explanation.setText("ZRTP Commit message integrity failure. May indicate software error or possible attack. Close this call and setup a new call.");
   zrtp_s3_c003_explanation.setText("ZRTP DHPart1 message integrity failure. May indicate software error or possible attack. Close this call and setup a new call.");
   zrtp_s3_c004_explanation.setText("ZRTP DHPart2 message integrity failure. May indicate software error or possible attack. Close this call and setup a new call.");
   zrtp_s3_c005_explanation.setText("Network connection lost during security setup. This indicates a network problem. Close this call and setup a new call.");
   zrtp_s3_c006_explanation.setText("Internal application error. Close this call, restart the program and setup a new call.");
   zrtp_s3_c007_explanation.setText("Internal application error. Close this call, restart the program and setup a new call.");
   zrtp_s3_c008_explanation.setText("This indicates either a network problem or your partner’s software encountered a problem. Close this call and setup a new call.");
   zrtp_s4_c016_explanation.setText("May indicate a software error, or a possible attempted attack. Close this call and setup a new call.");
   zrtp_s4_c020_explanation.setText("Internal application error. Close this call, restart the app and setup a new call.");
   zrtp_s4_c048_explanation.setText("The other party does not support the correct ZRTP version. Please ask the other party to update the application.");
   zrtp_s4_c081_explanation.setText("The other party tried to use an unsupported hash algorithm. Close this call and ask the other party to update the application.");
   zrtp_s4_c082_explanation.setText("The other party tried to use an unsupported encryption algorithm. Close this call and ask the other party to update the application.");
   zrtp_s4_c083_explanation.setText("The other party tried to use an unsupported key exchange algorithm. Close this call and ask the other party to update the application.");
   zrtp_s4_c084_explanation.setText("The other party tried to use an unsupported secure media authentication algorithm. Close this call and ask the other party to update the application.");
   zrtp_s4_c085_explanation.setText("The other party tried to use an unsupported Short Authentication String (SAS) algorithm. Close this call and ask the other party to update the application.");
   zrtp_s4_c097_explanation.setText("The other party computed insecure DH or ECDH key exchange data. Close this call and setup a new call.");
   zrtp_s4_c098_explanation.setText("ZRTP Hash Commit did not match DH packet. Close this call and setup a new call.");
   zrtp_s4_c099_explanation.setText("Wrong SAS relay behavior from an untrusted PBX. Please inform adminsitrator of the PBX if possible.");
   zrtp_s4_c112_explanation.setText("ZRTP Confirm message integrity failure. Close this call and setup a new call.");
   zrtp_s4_c144_explanation.setText("Your VoIP client and the other party’s client use the same ZRTP id (ZID). You may avoid this happening again if you dial *##*9787257* to generate a new ZID.");

}

void CTLangStrings::constrLatv()
{
   lInvPhNr.setText("Kljuudains telefona numurs");
   lAllSesBusy.setText("Visas sesijas aiznemtas");
   lError.setText("Kljuuda");
   
   lConTimeOut.setText("Nevar savienoties");
   lCannotDeliv.setText("Nevar nosuutiit zinju");
   lCannotReg.setText("Nevar pieregistreeties");
   lCannotCon.setText("Nevar izveidot savienojumu");
   lReason.setText("Iemesls:\r\n");
   lNoConn.setText("Nav piesleeguma");
   lCalling.setText("Zvanu...");
   lRegist.setText("Registreejos..");
   
   lCallEnded.setText("Zvans izbeigts");
   lRegSucc.setText("Registreeshanaas veiksmiiga");
   lMissCall.setText("Missed call");
   lIncomCall.setText("Ienaakoshais zvans no");
   lConnecting.setText("Savienojos ar tiiklu");
   
   
   lMyUN.setText("Mans lietotaajvards");
   
   lMyPwd.setText("Mana parole");
   lMyPhNr.setText("Mans tivi numurs");
   lFind.setText("Mekleet");
   
   lConfig.setText("Konfiguraacija");
   lPhoneBook.setText("Telefongraamata");
   
   lCall.setText("Zvaniit");lHangUp.setText("Beigt");
   
   lEdit.setText("Labot");
   lRemove.setText("Dzeest");
   lAdd.setText("Pievienot");
   
   lAbout.setText("Par");
   lLogin.setText("Login");
   lLogout.setText("Logout");
   lExit.setText("Iziet");
   
   lDialledNumbers.setText(" Zvaniitie numuri->");
   lReceivedCalls.setText("<-Sanjemtie zvani->");//,lMissedCalls;
   lMissedCalls.setText("<-Nesanemties zvani ");
   lToEnterCfgLogout.setText("Lai labotu, izlogoties");
   lDeleteEntryFromList.setText("Vai dzeest ierakstu no saraksta?");
   
   lVideoCall.setText("Video Call");
}
void CTLangStrings::constrJap()
{
   
   lInvPhNr.setText("\x53\x30\x6e\x30\x6a\x75\xf7\x53\x6b\x30\x4b\x30\x51\x30\x89\x30\x8c\x30\x7e\x30\x5b\x30\x93\x30",12,1); 
   lAllSesBusy.setText("\x59\x30\x79\x30\x66\x30\x6e\x30\xa5\x63\x9a\x7d\x4c\x30\x7f\x4f\x28\x75\x2d\x4e\x67\x30\x59\x30",12,1);
   lError.setText("\xa8\x30\xe9\x30\xfc\x30",3,1);
   lConTimeOut.setText("\xbf\x30\xa4\x30\xe0\x30\xa2\x30\xa6\x30\xc8\x30",6,1); 
   lCannotDeliv.setText("\xe1\x30\xc3\x30\xbb\x30\xfc\x30\xb8\x30\x92\x30\x01\x90\xe1\x4f\x67\x30\x4d\x30\x7e\x30\x5b\x30\x93\x30",13,1);  
   lCannotReg.setText("\x7b\x76\x32\x93\x67\x30\x4d\x30\x7e\x30\x5b\x30\x93\x30",7,1);   
   lCannotCon.setText("\xa5\x63\x9a\x7d\x67\x30\x4d\x30\x7e\x30\x5b\x30\x93\x30",7,1);  
   // :\r\n
   //   lReason.setText("\x9f\x53\xe0\x56\x3a\0\x5c\0\x72\0\x5c\0\x6e\0",7,1); 
   lReason.setText("\x9f\x53\xe0\x56:\0 \0\r\0\n\0",6,1); 
   lNoConn.setText("\xa5\x63\x9a\x7d\x57\x30\x66\x30\x44\x30\x7e\x30\x5b\x30\x93\x30",8,1);
   lCalling.setText("\xa5\x63\x9a\x7d\x2d\x4e\xfb\x30\xfb\x30",5,1);  
   lRegist.setText("\x7b\x76\x32\x93\x2d\x4e\xfb\x30\xfb\x30",5,1);  
   lCallEnded.setText("\xa5\x63\x9a\x7d\x42\x7d\x86\x4e",4,1);  
   lRegSucc.setText("\x7b\x76\x32\x93\x8c\x5b\x86\x4e",4,1);  
   lMissCall.setText("\x0d\x4e\x28\x57\x40\x77\xe1\x4f",4,1);   
   lIncomCall.setText("\xd7\x53\xe1\x4f\x48\x51",3,1);  
   lConnecting.setText("\xa4\x30\xf3\x30\xbf\x30\xfc\x30\xcd\x30\xc3\x30\xc8\x30\x6b\x30\xa5\x63\x9a\x7d\x2d\x4e\xfb\x30\xfb\x30",13,1);  
   lMyUN.setText("\xed\x30\xb0\x30\xa4\x30\xf3\x30\x0d\x54",5,1);  
   lMyPwd.setText("\xd1\x30\xb9\x30\xef\x30\xfc\x30\xc9\x30",5,1);   
   lMyPhNr.setText("\x54\0\x69\0\x56\0\x69\0\xfb\x96\x71\x8a\x6a\x75\xf7\x53",8,1);  
   lFind.setText("\x1c\x69\x22\x7d",2,1);
   lConfig.setText("\x2d\x8a\x9a\x5b",2,1); 
   lPhoneBook.setText("\xfb\x96\x71\x8a\x33\x5e",3,1); 
   
   lCall.setText("\x7a\x76\xe1\x4f",2,1); 
   lHangUp.setText("\xd7\x53\xe1\x4f",2,1); 
   
   lEdit.setText("\xe8\x7d\xc6\x96",2,1);  
   lRemove.setText("\x64\x96\xbb\x53",2,1); 
   lAdd.setText("\xfd\x8f\xa0\x52",2,1);  
   lAbout.setText("\xd8\x30\xeb\x30\xd7\x30",3,1);  
   lLogin.setText("\xed\x30\xb0\x30\xa4\x30\xf3\x30",4,1);  
   lLogout.setText("\xed\x30\xb0\x30\xa2\x30\xa6\x30\xc8\x30",5,1);
   lExit.setText("\x42\x7d\x86\x4e",2,1);    
   lDialledNumbers.setText("\x20\0\x20\0\x7a\x76\xe1\x4f\x6a\x75\xf7\x53\x2d\0\x3e\0",8,1);  
   lReceivedCalls.setText("\x3c\0\x2d\0\xd7\x53\xe1\x4f\x6a\x75\xf7\x53\x2d\0\x3e\0",8,1);//,lMissedCalls;   
   lMissedCalls.setText("\x3c\0\x2d\0\x0d\x4e\x28\x57\x40\x77\xe1\x4f\x20\0",7,1);  
   lToEnterCfgLogout.setText("\x09\x59\xf4\x66\x92\x30\x2d\x8a\x9a\x5b\x59\x30\x8b\x30\x6b\x30\x6f\x30\xed\x30\xb0\x30\xa2\x30\xa6\x30\xc8\x30\x59\x30\x8b\x30\xc5\x5f\x81\x89\x4c\x30\x42\x30\x8a\x30\x7e\x30\x59\x30",23,1); 
   lDeleteEntryFromList.setText("\x53\x30\x6e\x30\xa8\x30\xf3\x30\xc8\x30\xea\x30\xfc\x30\x92\x30\xea\x30\xb9\x30\xc8\x30\x4b\x30\x89\x30\x4a\x52\x64\x96\x57\x30\x7e\x30\x59\x30\x4b\x30\x1f\xff",20,1); 
   //lEnterUN_PWD.setText("Enter username and password");
   lEnterUN_PWD.setText("\xe6\x30\xfc\x30\xb6\x30\xfc\x30\x0d\x54\x68\x30\xd1\x30\xb9\x30\xef\x30\xfc\x30\xc9\x30\x92\x30\x65\x51\x9b\x52\x57\x30\x66\x30\x4f\x30\x60\x30\x55\x30\x44\x30",20,1);
   lRunAtStartup.setText("Run at startup");
   
   lVideoCall.setText("Video Call");
}
void CTLangStrings::constrItalian()
{
   lInvPhNr.setText("Numero di telefono non valido ");
   lAllSesBusy.setText("Tutte le sessioni sono occupate. ");
   lError.setText("Errore");
   
   lConTimeOut.setText("Richiesta scaduta");
   lCannotDeliv.setText("Impossibile inviare il messaggio. ");
   lCannotReg.setText("Non puÚ registrare. ");
   lCannotCon.setText("Impossibile collegarsi. ");
   lReason.setText("Motivo: \r\n");
   lNoConn.setText("Nessun collegamento");
   lCalling.setText("Chiamata...");
   lRegist.setText("Registrazione..");
   
   lCallEnded.setText("Chiamata conclusa");
   lRegSucc.setText("Registrazione riuscita");
   lMissCall.setText("Chiamata sig.na");
   lIncomCall.setText("Chiamata ricevuta da");
   lConnecting.setText("Collegando ad internet...");
   
   
   lMyUN.setText("Nome Utente");
   
   lMyPwd.setText("Password");
   lMyPhNr.setText("Numero di Telefono");
   lFind.setText("Cerca");
   
   lConfig.setText("Configurazione");
   lPhoneBook.setText("Rubrica");
   
   lCall.setText("Chiama");//no nokia tel
   lHangUp.setText("Fine");//no nokia tel
   
   lEdit.setText("Modifica");//no nokia tel
   lRemove.setText("Cancella");//no nokia tel
   lAdd.setText("Aggiungi");//no nokia tel
   
   lAbout.setText("Info");
   lLogin.setText("Entra");
   lLogout.setText("Esci");
   lExit.setText("Uscita");
   
   lDialledNumbers.setText(" Chiamate effettuate>");//no nokia tel
   lReceivedCalls.setText("<Chiamate ricevute>");//,lMissedCalls;
   lMissedCalls.setText("<Chiamate Perse");
   lToEnterCfgLogout.setText("Prima devi Uscire");
   lDeleteEntryFromList.setText("Entrata di cancellazione dalla lista?");
   
   lEnterUN_PWD.setText("Enter username and password");
   lRunAtStartup.setText("Parti all'avvio");
   
   lVideoCall.setText("Video Call");
   //Options Opzioni //no nokia tel
}


void CTLangStrings::constrEng()
{
   //#define T_TR(_T_KEY) tg_translate(_T_KEY, sizeof(_T_KEY)-1)
  
   lCouldNotReachServer.setText("Could not reach server");
   lRemoteOutOfReach.setText("Remote party is out of coverage");
   
   lRestartLang.setText("Instant language switch requires phone restart, continue?");
   
   lInvPhNr.setText("Invalid phone number");
   lAllSesBusy.setText("All sessions are busy");
   lError.setText("Error");
   
   lConTimeOut.setText("Connection timed out");
   lCannotDeliv.setText("Cannot deliver a message");
   lCannotReg.setText("Cannot register");
   lCannotCon.setText("Cannot connect");
   lReason.setText("Reason: \r\n");
   lNoConn.setText("Network not available");
   lCalling.setText("Calling...");
   lRegist.setText("Registering...");
   
   lCallEnded.setText("Call ended");
   lRegSucc.setText("Registration successful");
   lMissCall.setText("Missed call");
   lIncomCall.setText("Incoming call from");
   lConnecting.setText("Connecting...");
   
   
   lMyUN.setText("My login name");
   
   lMyPwd.setText("My password");
   lMyPhNr.setText("My ");
   lMyPhNr.addText(lApiShortName);
   //
   lMyPhNr.addText(" number, (if you have one)");
   
   lEnterUN_PWD.setText("Enter username and password");
   
   lFind.setText("Find");
   lConfig.setText("Config");
   lPhoneBook.setText("Phone book");
   
   lCall.setText("Call");lHangUp.setText("End call");
   
   lEdit.setText("Edit");
   lRemove.setText("Remove");
   lAdd.setText("Add");
   
   lAbout.setText("About");
   lLogin.setText("Login");
   lLogout.setText("Logout");
   lExit.setText("Exit");
   
   lDialledNumbers.setText("  Dialled numbers ->");
   lReceivedCalls.setText("<- Received calls ->");//,lMissedCalls;
   lMissedCalls.setText("<- Missed calls  ");
   lToEnterCfgLogout.setText("To enter config Logout.");
   lDeleteEntryFromList.setText("Delete entry from list?");
   
   
   lRunAtStartup.setText("Run at startup");
   lUsingAP.setText("Using AP - ");
   
   lVideoCall.setText("Video Call");
   lEnterNumberChatWith.setText("Enter the username or number you want to chat with\nand press Chat button again.");
   
   lOptions.setText("Options");
   lOk.setText("Ok");
   lCancel.setText("Cancel");
   lChat.setText("Chat");
   
   lNetworkConfiguration.setText("Network Configuration");
   lDefault.setText("Default");
   
   lSoundAlertMsg.setText("Sound an alert on incoming messages");
   lShowTSFrontMsg.setText("Show timestamp in front of messages");
   lOutputSpeakersHead.setText("Output - speakers or headset");
   lInputMicHead.setText("Input - microphone or headset");
   lNRingsDings.setText("Notifications - ring and dings");
   lAudio.setText("Audio");
   lCalls.setText("Calls");
   lEnterNumberHere.setText("Enter number here");
   
   lSend.setText("Send");
   lMyScreenName.setText("My screen name");
   lDoUWantSelectNewAp.setText("Do you want to select new access point?");
   lDontShowMsgAgain.setText("Don't show this message again.");
   
   lForYourInfo.setText("For your information");
   lNotifyMicCameraUsage.setText(
                                 "This application will use of the following "
                                 "features of your phone. If you have any questions or "
                                 "concerns, please contact us at info@tivi.com:\n\n"
                                 "* Using camera and microphone\n"
                                 "* Making a connection to the internet\n"
                                 );
   lBillableEvent.setText("Billable event");
   lAllowApiConnect.setText("Allow  this application to connect to the internet?");
   
   lKeyInvalid.setText("Licence is wrong... :(\nYou can get it from\nwww.tivi.com");
   lKeyValid.setText("Thank You,\nkey is valid.");
   
   lKeyInvalidUnlimited.setText("Can not use ZRTP in unlimited mode!\n Please enter a valid ZRTP key or get it from\nwww.tivi.com");
   lKeyInvalidActive.setText("Can not use ZRTP in active mode!\n Please enter a valid ZRTP key or get it from\nwww.tivi.com");
   
   
}

short * getLine(short *line, short *end, char *name, int iMaxNameLen, int &iNameLen,CTEditBase *val){
   short *s=line;
   char *ps=(char*)s;
   int iBigEnd=(ps[0]==0);
   if(iBigEnd){
      
      iNameLen=0;
      name[0]=0;
      while(s<end){
         if(s[0]==('^'<<8)){name[iNameLen]=0;s++;break;}
         
         if(s[0]==('\\'<<8) && s[1]==('n'<<8))
         { name[iNameLen]='\n'; s++;}
         else
            name[iNameLen]=s[0]>>8;
         
         
         if(iNameLen+1<iMaxNameLen)iNameLen++;
         s++;
      }
      //if(s>=end)return end;
      while((s[0]&0xff00) ){
         //&& s[0]<=(' '<<8)
         int c=(((s[0]>>8)&0xff)|((s[0]<<8)&0xff00))&0xffff;
         if(c>' ')break;
         s++;
      }
      val->setLen(0);
      while(s<end){
         if(s[0]==('\\'<<8)){
            if(s[1]==('n'<<8)){val->addChar('\n');s+=2;continue;}
            else if(s[1]==('r'<<8)){val->addChar('\r');s+=2;continue;}
            
         }
         if(s[0]==('\n'<<8) || s[0]==('\r'<<8))break;
         int c=(((s[0]>>8)&0xff)|((s[0]<<8)&0xff00))&0xffff;
         val->addChar(c);
         s++;
      }
      //if(s>=end)return end;
      //      while((s[0]&0xff00) && s[0]<(' '<<8))s++;
      while((s[0]&0xff00) ){
         //&& s[0]<=(' '<<8)
         int c=(((s[0]>>8)&0xff)|((s[0]<<8)&0xff00))&0xffff;
         if(c>=' ')break;
         s++;
      }
      
   }
   else{
      
      iNameLen=0;
      name[0]=0;
      while(s<end){
         if(s[0]=='^'){name[iNameLen]=0;s++;break;}
         
         if(s[0]=='\\' && s[1]=='n')
         { name[iNameLen]='\n'; s++;}
         else
           name[iNameLen]=s[0];
         
         if(iNameLen+1<iMaxNameLen)iNameLen++;
         s++;
      }
      //if(s>=end)return end;
      while(((unsigned short*)s)[0]<=' ')s++;
      val->setLen(0);
      while(s<end){
         if(s[0]=='\\'){
            if(s[1]=='n'){val->addChar('\n');s+=2;continue;}
            else if(s[1]=='r'){val->addChar('\r');s+=2;continue;}
            
         }
         if(s[0]=='\n' || s[0]=='\r' || s[0]==0)break;
         val->addChar((unsigned short)s[0]);
         s++;
      }
      //if(s>=end)return end;
      while(s[0]<' ')s++;
      
   }
   while(0){
      //<=' '
      int iLastC=val->getChar(val->getLen()-1);
      if(iLastC>' ')break;
      if(iLastC=='\n')break;
      val->remLastChar();
   }
   
   return s;
}
int CTLangStrings::loadLang(char *p, int iLen){
   
   int iShift=0;
   
   if((unsigned char)p[0]==0xff && (unsigned char)p[1]==0xfe){
      //little endian
      iShift = 0;
   }
   else if((unsigned char)p[0]==0xfe && (unsigned char)p[1]==0xff){
      //big  endian
      iShift = 8;
   }
   else
     return -2;
   
   clearList();
   //debugss("loadLang2",1,1);
   
   short *sh=new short[(iLen>>1)+2];
   
   int iDataLen=(iLen+1)>>1;
   if(0&& (iLen&1)==0){
      char *tmp=p;
      while(*tmp!='l' && tmp[1]!=0){
         if(tmp<p+10){
            delete p;
            delete sh;
            return -1;
         }
         tmp++;
      }
      
      memcpy(sh,tmp,iLen+p-tmp);
      iDataLen++;
   }
   else{
      memcpy(sh,p+2,iLen-2);
   }
   
   //debugss("loadLang3",1,1);
   short *end=sh+iDataLen;
   short *tmp=sh;
   end[0]=0;
   
   char name[512];
   int iNameLen;
   CTEditBase val(1024);
   
   while(tmp+5<end){

      // skip a comment line
      if(tmp[0]==('*'<<iShift) || tmp[0]==('#'<<iShift)){

         tmp++;
         while(tmp+5<end && tmp[0]!=('\n'<<iShift)){
            tmp++;
         }
         
         if(tmp+5<end && tmp[0]!=('\r'<<iShift))
            tmp++;
         continue;
         
      }
      while(tmp+5<end && ( tmp[0]==(' '<<iShift) || tmp[0]==('\n'<<iShift) || tmp[0]==('\r'<<iShift) || tmp[0]==('\t'<<iShift)))tmp++;//skip spaces
      
      tmp=getLine(tmp,end,&name[0],sizeof(name)-1,iNameLen,&val);
    //  printf("[tr=(%.*s)]\n",iNameLen, name);
      if(val=="<***>")continue;
      
      setGetTextById(&name[0],iNameLen,val,1);
      
   }
   
endFnc:
   delete sh;
   return 0;
}

int CTLangStrings::loadLang(CTEditBase *langFile){
   int iLen;
   char *p=loadFileW(langFile->getText(),iLen);
   if(!p)return -1;
   
   int r = loadLang(p, iLen);
   
   delete p;
   
   return r;
}


#define T_LOAD_STRINGS
#include "CTLangStrings.h"


