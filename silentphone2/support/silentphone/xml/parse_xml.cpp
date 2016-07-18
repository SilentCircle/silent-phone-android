/*
Created by Janis Narbuts
Copyright (C) 2004-2012, Tivi LTD, www.tiviphone.com. All rights reserved.
Copyright (C) 2012-2016, Silent Circle, LLC.  All rights reserved.

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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "parse_xml.h"

void debugsi(char *c, int a);
void tivi_log(const char* format, ...);

#define DEBUG_T(a,c){if(!a)tivi_log("xml err[%s]",c);else tivi_log("xml err[%.*s]",a,c);}
#define F_DQUOTE             1 
#define F_SQUOTE             2 
#define F_TRI_BRACE          4
#define F_VNAME              8
#define F_VALUE             16      // 0x10
#define F_CONTENT           32      // 0x20
#define F_NODE_NOPEN        64      // 0x40
#define F_NODE_NCLOSE      128      // 0x80
#define F_LAST            1024      // 0x400
#define F_FIRST           2048      // 0x800


int cmpNOpenNClose(STR_XML nO,STR_XML nC);

int errorXMLF(const char * errorStr);
#define errorXML(A) errorXMLF(A)

/*
 char xmlBuff[] = 
 "<user loginname = ansis  pwd = passwordX fromname = \"adas\"/>"
 "<cfg>"
 "<sip sipport = 5060 rtpport = 16464 cseq = 1/>"
 "<phone autoanswer = FALSE currentstatus = 486 edbox = \"195.13.182.108\"/>"
 "<audio cature = TRUE play = TRUE/>"
 "</cfg>"
 ;
 */

int CParseXml::newTok(STR_XML str)
{
   if (flag == F_VALUE+F_VNAME+F_TRI_BRACE) 
      flag = F_TRI_BRACE+F_VNAME;
   
   switch (nextFlag) {
      case 100:                       // = 0x64 (F_TRI_BRACE | F_CONTENT | F_NODE_NOPEN)
         if(curFlag == 132) {        // = 0x84 (F_TRI_BRACE | F_NODE_NCLOSE)
            curFlag = F_NODE_NOPEN+F_NODE_NCLOSE;
         }
         else {
            errorXML("xxx err");
            return -1;
         }
         
      case F_TRI_BRACE+F_VNAME:       // 12 vname
      case F_TRI_BRACE+F_VALUE:       // 20 value
      case F_CONTENT:                 // 32 content
      case F_TRI_BRACE+F_NODE_NOPEN:  // 68 tag open
      case F_TRI_BRACE+F_NODE_NCLOSE: // 132 tag close
         if ((curFlag == F_TRI_BRACE+F_NODE_NCLOSE) 
             && !((nextFlag == F_TRI_BRACE+F_NODE_NCLOSE)
                  || (nextFlag == F_TRI_BRACE+F_NODE_NOPEN) || (nextFlag == F_LAST))) {
                
                errorXML("after tag close");
                
                return -1;
             }
         if ((curFlag == F_TRI_BRACE+F_VNAME) && (nextFlag != F_TRI_BRACE+F_VALUE)) {
            errorXML("not value after vname");
            return -1;
         }
         if ((curFlag == F_TRI_BRACE+F_VALUE) && (nextFlag == F_TRI_BRACE+F_VALUE)) {
            errorXML("value after value");
            return -1;
         }
         if ((curFlag == F_CONTENT) && (nextFlag == F_TRI_BRACE+F_NODE_NOPEN)) {
            errorXML("open tag after content");
            return -1;
         }
         if ((curFlag == F_TRI_BRACE+F_NODE_NOPEN) && (nextFlag == F_TRI_BRACE+F_VALUE)) {
            errorXML("value after tag open");
            return -1;
         }
         curFlag = nextFlag;
         curStr = nextStr;
         
      case F_FIRST:
         nextStr = str;
         nextFlag = flag;
         return 0;
   }
   errorXML("unkown token");
   return -1;
}

// Split XML into tokens and return token information (strings, type, ...).
int CParseXml::splitXML()
{
   STR_XML str;
   
   if (*cur != '/')
      str.s = cur+1;
   else
      str.s = cur;
   
   str.len = 0;
   
   int futf8 = 0;
   
   for (; cur < end; cur++) {
      unsigned char  cc = (unsigned char)*cur;
      int iCur = cc;//(unsigned)*cur;
      if(futf8 == 0 && iCur >= 192 ) {
         futf8 = 1;
         if (iCur&32) {
            futf8++;
            if (iCur&16) {
               futf8++;
               if (iCur&8) {
                  futf8++;
                  if (iCur&4) {
                     futf8++;
                  }
               }
            }
         }
         continue;
      }
      if (futf8 > 0 && iCur >= 128 && (iCur&64) == 0) {
         futf8--;
         continue;
      }
      futf8 = 0;
      
      if (iCur>65 && iCur != 92) //92 == '\\'
         continue;
      
      switch (iCur) {
         case '\r': 
         case '\n':
            if (flag & F_TRI_BRACE) {
               errorXML("CR or LF found after \"<\"");
               return -1;
            }
            str.len = cur-str.s;
            if (str.len>0) { 
               if (-1 == newTok(str)) 
                  return -1;
               return 0;
            }
            str.s = cur+1; 
            continue;
            
         case  0: case  1: case  2: case  3: case  4: case  5: case  6: case  7: case  8:
         case 11: case 12:          case 14: case 15: case 16: case 17: case 18: case 19:
         case 20: case 21: case 22: case 23: case 24: case 25: case 26: case 27: case 28:
         case 29: case 30: case 31:
            errorXML("found < 32");
            return -1;
            
         case '\\':
            if ((flag & (F_TRI_BRACE|F_SQUOTE|F_DQUOTE)) == 0 && *(cur+1) == '\\') {
               while(*cur && *cur != '\n')cur++;
               str.s = cur+1;
            }
            continue;
            
         case '/':
            if (flag & (F_SQUOTE|F_DQUOTE))
               continue;
            if (flag & F_TRI_BRACE) {
               str.len = cur - str.s;
               if (str.len > 0) { 
                  if (-1 == newTok(str))
                     return -1;
                  flag &= ~F_VNAME;
                  flag &= ~F_VALUE;
                  flag &= ~F_NODE_NOPEN;
                  flag |= F_NODE_NCLOSE;
                  
                  return 0;
               }
               flag &= ~F_VNAME;
               flag &= ~F_VALUE;
               flag &= ~F_NODE_NOPEN;
               flag |= F_NODE_NCLOSE;
            }
            continue;
            
         case '<': 
            if (flag & (F_SQUOTE|F_DQUOTE))
               continue;
            if (flag & F_TRI_BRACE) {
               errorXML(cur);
               errorXML("\"<\" found - after \"<\"");
               return -1;
            }
            if(*(cur+1) == '!' && *(cur+2) == '-' &&  *(cur+3) == '-') {
               cur += 3;
               while (*cur && (*(cur-2) != '-' || *(cur-1) != '-' || *(cur) != '>'))
                  cur++;
               if(*cur)
                  cur++;
               str.s = cur+1; 
               continue;
            }
            if (cur > str.s) {
               flag |= F_CONTENT;
               
               str.len = cur - str.s;
               if (-1 == newTok(str))
                  return -1;
               flag &= ~F_VALUE;
               flag &= ~F_VNAME;
               return 0;
            }
            flag |= F_TRI_BRACE; 
            flag |= F_NODE_NOPEN;
            flag &= ~F_NODE_NCLOSE;
            str.s = cur+1; 
            continue;
            
         case '>':
            if (flag & (F_SQUOTE|F_DQUOTE)) 
               continue;
            if ((flag & F_TRI_BRACE) == 0) {
               errorXML("<-not found");
               return -1;
            }
            flag &= ~F_VNAME;
            flag &= ~F_CONTENT;
            
            str.len = cur-str.s;
            if ((str.len == 0) && (cur[-1] != '<')) { 
               flag &= ~F_TRI_BRACE;str.s = cur+1;
               continue;
            }
            if (str.len > 0) {
               if (-1 == newTok(str))
                  return -1;
               return 0;
            }
            flag &= ~F_TRI_BRACE; 
            flag = F_CONTENT;
            str.s = cur+1; 
            continue;
            
         case 34:        // "
         case '\'':
            if ((flag & F_TRI_BRACE) == 0 || (flag & F_SQUOTE))
               continue; 
            flag ^= F_DQUOTE;
            if ((flag & F_DQUOTE) == 0) {
               flag |= F_VALUE;
               flag &= ~F_VNAME;
               str.len = cur - str.s;
               flag &= ~F_NODE_NCLOSE;
               flag &= ~F_NODE_NOPEN;
               if (-1 == newTok(str))
                  return -1;;
               flag &= ~F_VALUE;
               flag |= F_VNAME;
               cur++;
               return 0;
            }
            str.s = cur+1;
            continue;
            
         case '`':       // `
            if ((flag & F_TRI_BRACE) == 0 || (flag & F_DQUOTE))
               continue; 
            flag ^= F_SQUOTE;
            if ((flag & F_SQUOTE) == 0) {
               flag |= F_VALUE;
               flag &= ~F_VNAME;
               str.len = cur-str.s;
               flag &= ~F_NODE_NCLOSE;
               flag &= ~F_NODE_NOPEN;
               if (-1 == newTok(str))
                  return -1;
               /*
                continue; // *** WD: Ask Janis about this continue
                
                flag |= F_VNAME;
                flag &= ~F_VALUE;
                flag &= ~F_CONTENT;
                */
               flag &= ~F_VALUE;
               flag |= F_VNAME;
               cur++;
               return 0;
            }
            str.s = cur+1;
            continue;
            
         case '=' :
            if ((flag & F_TRI_BRACE) == 0)
               continue; 
            if (flag & (F_SQUOTE|F_DQUOTE))
               continue;
            
            flag &= ~F_NODE_NOPEN;
            flag &= ~F_NODE_NCLOSE;
            
            if (cur[-1] == '<') {
               errorXML("= sign found after '<'");
               return -1;
            }
            if (cur > str.s){
               str.len = cur - str.s;
               if (-1 == newTok(str)) 
                  return -1;
               flag &= ~F_VNAME;
               flag |= F_VALUE;
               return 0;
            }
            str.s = cur+1; 
            
            flag &= ~F_VNAME;
            flag |= F_VALUE;
            continue;
            
         case '\t': 
         case ' ':
            if (flag & (F_SQUOTE|F_DQUOTE))
               continue;
            
            if (flag & F_TRI_BRACE) {
               flag &= ~F_CONTENT;
            }
            else { 
               flag &= ~F_VALUE;
               flag &= ~F_VNAME;
               flag |= F_CONTENT;
            }
            if (cur[-1] == '<') {
               errorXML("TAB , SP or EQVAL found after '<'");
               return -1;
            }
            str.len = cur - str.s;
            if (cur > str.s) {
               if (-1 == newTok(str)) 
                  return -1;
               flag &= ~F_NODE_NOPEN;
               flag &= ~F_NODE_NCLOSE;
               if (flag & F_TRI_BRACE) 
                  flag |= F_VNAME;
               return 0;
            }
            flag &= ~F_NODE_NOPEN;
            flag &= ~F_NODE_NCLOSE;
            str.s = cur+1; 
            continue;
      }
   }
   flag = F_LAST;
   if (-1 == newTok(str)) 
      return -1;
   return 0;
}

nameValue * newNV(STR_XML name, STR_XML value, nameValue * nVFirst)
{
   nameValue *nV;
   nV = (nameValue *)malloc(sizeof(nameValue));
   memset(nV, 0, sizeof (nameValue));
   
   nV->name = name;
   nV->value = value;
   nV->next = nVFirst;
   
   return nV;
}

NODE *newNode(NODE * curNode)
{
   NODE *no;
   no = (NODE *)malloc(sizeof(NODE));
   memset(no, 0, sizeof(NODE));
   no->next = curNode;
   
   return no;
}

NODE *CParseXml::node()
{
   NODE *no;
   no = newNode(NULL);
   
   for (; flag != F_LAST; ) {
      if (-1 == splitXML())
         return NULL;
      
      if (curFlag == F_TRI_BRACE+F_NODE_NOPEN) {
         no->name = curStr;
      }
      if (((curFlag == F_TRI_BRACE+F_NODE_NOPEN) || (curFlag == F_TRI_BRACE+F_VALUE))
          && (nextFlag == F_TRI_BRACE+F_NODE_NOPEN)) {
         no->child = node();
         if (no->child == NULL) 
            return NULL;
         if (no->child->name.len == 0)
            return NULL;
         continue;
      }
      if (curFlag == F_TRI_BRACE+F_VNAME) {
         no->nV = newNV(curStr,nextStr,no->nV);
         continue;
      }
      if (curFlag == F_CONTENT) {
         if (no->content.len == 0) 
            no->content = curStr;
         else
            no->content.len = curStr.s-no->content.s+curStr.len;
         continue;
      }
      if ((curFlag == F_TRI_BRACE+F_NODE_NCLOSE) && (nextFlag == F_TRI_BRACE+F_NODE_NOPEN)) { 
         if ((cmpNOpenNClose(no->name,curStr)) ||(curStr.s && curStr.s[0] == '/' && curStr.s[1] == '>')) {
            no = newNode(no);
            
         }
         else {
            printf(" Name is %.*s\nCur is %.*s", no->name.len, no->name.s, curStr.len, curStr.s);
            errorXML("in name of brother");
            return NULL;
         }
      }
      if ((cmpNOpenNClose(no->name,curStr))
          && (curFlag == F_TRI_BRACE+F_NODE_NCLOSE) && (nextFlag != F_TRI_BRACE+F_NODE_NOPEN))
         break;
      
      if (nextFlag == F_TRI_BRACE+F_NODE_NCLOSE && (curStr.s[-1] != '<'&& curStr.s[0] == '/'&& curStr.s[1] == '>'))
         break;
      
   }
   if (!(curStr.s[-1] != '<'&& curStr.s[0] == '/' && curStr.s[1] == '>') && cmpNOpenNClose(no->name,curStr) == 0) {
      printf(" Name is %.*s\nCur is %.*s", no->name.len, no->name.s, curStr.len, curStr.s);
      errorXML("tag close name");
      return NULL;
   }
   return no;
}

void reverse(NODE ** node, NODE *parent)
{
   NODE * curNode;
   NODE * newNode  = NULL; 
   NODE * prevNode = NULL;
   
   curNode = *node;
   
   while (curNode) {
      NODE *tmpNode = NULL;
      
      if (curNode->child)
         reverse(&(curNode->child), curNode);
      
      if (curNode) {
         curNode->parent = parent;
      }
      
      tmpNode = newNode;
      newNode = curNode;
      curNode = curNode->next;
      
      if (curNode) {
         curNode->prev = prevNode;
      }
      
      prevNode = curNode;
      newNode->next = tmpNode;
      
      newNode->prev = curNode;
   }
   *node = newNode;
}

void terminateStrings(NODE *node) {
   
   // terminate attributes
   nameValue *nextNV = node->nV;
   while (nextNV) {
      nextNV->name.s[nextNV->name.len] = 0;
      nextNV->value.s[nextNV->value.len] = 0;
      nextNV = nextNV->next;
   }
   
   // do child
   if (node->child) {
      terminateStrings(node->child);
   }
   
   // do brother
   if (node->next) {
      terminateStrings(node->next);
   }
   
   node->name.s[node->name.len] = 0; // name done
   // free(node);
}

void freeNode(NODE *node) {
   
   // release Name-Values (attribs)
   nameValue *nextNV = NULL;
   while (node->nV) {
      nextNV = node->nV->next;
      if (node->nV->bFreeValuePtr) {
         free(node->nV->value.s);
      }
      free(node->nV);
      node->nV = nextNV;
   }
   
   // release child
   if (node->child) {
      freeNode(node->child);
   }
   
   // release brother
   if (node->next) {
      freeNode(node->next);
   }
   
   free(node);
}

char *loadFile(const  char *fn, int &iLen);
char *loadFileW(const  short *fn, int &iLen);

// short* : Used for systems (Windows) that use wide-chars in filenames
NODE*  CParseXml::mainXML(const short *szFileName)
{
   int buffLen;
   
   if(bufFile)
      delete bufFile;
   
   bufFile =loadFileW(szFileName, buffLen);
   
   if (bufFile == NULL) {
      DEBUG_T(0,"xml file not found");
      return NULL;
   }
   return parseXml(bufFile, buffLen);
}

NODE*  CParseXml::mainXML(const char *szFileName)
{
   int buffLen;
   if (bufFile)
      delete bufFile;
   
   // bufFile is always terminated with 'nul' bytes
   bufFile = loadFile(szFileName, buffLen);
   
   if (bufFile == NULL) {
      DEBUG_T(0,"xml file not found");
      return NULL;
   }
   return parseXml(bufFile, buffLen);
}

NODE* CParseXml::parseXml(char * inbuf, int iBufLen)
{
   char * buf = inbuf;
   flag = 0;
   curFlag = 0;
   nextFlag = F_FIRST;
   NODE *root = NULL;
   
   memset(&nextStr, 0, sizeof(nextStr));
   memset(&curStr, 0, sizeof(curStr));
   
   // Buf (from inbuf) is always terminated with 'nul' bytes
   if(iBufLen == 0)
      iBufLen = strlen(buf);
   
   end = buf + iBufLen;
   cur = buf;
   
   flag = 0;                   //F_NODE_NOPEN;
   
   // skip leeading whitespace chars
   while(*cur == ' ' || *cur == '\t' || *cur == '\n' || *cur == '\r')
      cur++;
   if (cur[0] != '<') {
      errorXML("invalid start of document");
      return root;
   }
   
   //skip <?    ?>
   if (cur[1] == '?') {
      // TODO _TEST_XML;
      cur += 2;
      while (cur[0] != '?' && cur[1] != '>') {
         if (*cur < ' ') {
            errorXML("<?  ?>");
            return root;
         }
         cur++;
      }
      cur += 2;
   }
   
   while (*cur == ' ' || *cur == '\t' || *cur == '\n' || *cur == '\r')
      cur++;
   
   if (cur[0] == '<' && cur[1] == '!') {
      cur += 2; 
      while(*cur >= ' ' && *cur != '>')
         cur++; 
      cur++;
   }
   
   while (*cur == ' ' || *cur == '\t' || *cur == '\n' || *cur == '\r')
      cur++;
   
   if (rootNode)
      freeNode(rootNode);
   
   root = node();
   rootNode = root;
   if (!root) {
      DEBUG_T(0,"root is null");
      return root;
   }
   
   reverse(&root, NULL);
   terminateStrings(root);
   rootNode = root;
   return rootNode;
}

void stopXML() 
{
}

int cmpNOpenNClose(STR_XML nO,STR_XML nC)
{
   int i = 0;
   if (nO.s == NULL || nC.s == NULL)
      return 0;
   if (nO.len != nC.len-1)
      return 0;
   for (;i<nO.len;i++)
      if (nO.s[i] != nC.s[i+1])
         return 0;
   return 1;
}

int errorXMLF(const char * errorStr)
{
   char s[128];
   int el = strlen(errorStr);
   if (el > 100)
      el = 100;
   
   sprintf(s," Error XML %.*s!\n",el,errorStr);
   DEBUG_T(0,s);
   return -1;
}


nameValue *getAttribute(NODE *node, char *pszAttribName)
{
   nameValue *nv = node->nV;
   if (!nv)
      return NULL;
   int iStrLen = strlen(pszAttribName);
   
   while (nv) {
      if (nv->name.len == iStrLen && !memcmp(nv->name.s, pszAttribName, iStrLen)) {
         return nv;
      }
      nv = nv->next;
   }
   return NULL;
}

NODE_LIST *getChildNodeList(NODE *node, char *pszNodeName) {
   
   if (!node) 
      return NULL;
   
   NODE      *child    = node->child;
   NODE_LIST *nodeList = NULL;
   NODE_LIST *prevList = NULL;
   
   int iNodeLen = strlen(pszNodeName);
   
   while (child) {
      
      if (child->name.len == iNodeLen && !memcmp(child->name.s, pszNodeName, iNodeLen)) {
         
         NODE_LIST *p = (NODE_LIST *)malloc(sizeof(NODE_LIST));
         //memset(p, 0, sizeof(NODE_LIST));
         
         p->node = child;
         p->next = NULL;
         
         if (prevList) {
            prevList->next = p;
         } 
         else {
            nodeList = p;
         }
         prevList = p;
      }
      child = child->next;
   }
   return nodeList;
}

NODE *getNodeWithAttrib(NODE_LIST *nl, char *pszAttribName, char *pszAttribValue)
{
   while (nl) {
      
      if (getAttribByNameValue(nl->node, pszAttribName, pszAttribValue)) {
         return nl->node;
      }
      
      nl = nl->next;
   }
   
   return NULL;
}

nameValue *getAttribByNameValue(NODE *node, char* pszAttribName, char* pszAttribValue)
{
   int iAttribNameLen = strlen(pszAttribName);
   int iAttribValueLen = strlen(pszAttribValue);
   
   nameValue *nv = node->nV;
   
   while (nv) {
      if (nv->name.len == iAttribNameLen && !memcmp(nv->name.s, pszAttribName, iAttribNameLen))
         if (nv->value.len == iAttribValueLen && !memcmp(nv->value.s, pszAttribValue, iAttribValueLen)) {
            return nv;
         }
      nv = nv->next;
   }
   
   return NULL;
}

nameValue *getAttribByNameLen(NODE *node, char* pszAttribName, int iAttribNameLen)
{
   nameValue *nv = node->nV;
   
   while (nv) {
      if (nv->name.len == iAttribNameLen && !memcmp(nv->name.s, pszAttribName, iAttribNameLen)) {
         return nv;
      }
      nv = nv->next;
   }
   return NULL;
   
}

nameValue *getAttribByName(NODE *node, char* pszAttribName)
{
   nameValue *nv = node->nV;
   if(!nv)
      return NULL;
   int iAttribNameLen = strlen(pszAttribName);
   
   while (nv) {
      if (nv->name.len == iAttribNameLen && !memcmp(nv->name.s, pszAttribName, iAttribNameLen)) {
         return nv;
      }
      nv = nv->next;
   }
   
   return NULL;
}

nameValue *createAttribute(NODE *node, char *pszName, char *pszValue)
{
   int iNameLen  = strlen(pszName);
   int iValueLen = strlen(pszValue);
   
   nameValue *attrib = getAttribByName(node, pszName);
   
   if (!attrib) {
      attrib = (nameValue *)malloc(sizeof(nameValue));
      memset(attrib, 0, sizeof(nameValue));
      
      // add name
      attrib->name.s = (char *)malloc(iNameLen+1);
      strcpy(attrib->name.s, pszName);
      attrib->name.len = iNameLen;
      
      nameValue *nextAttrib = node->nV;
      node->nV = attrib;
      attrib->next = nextAttrib;
   }
   
   if (attrib->bFreeValuePtr) {
      free(attrib->value.s);
   }
   
   attrib->bFreeValuePtr = true;
   attrib->value.s = (char *)malloc(iValueLen+1);
   strcpy(attrib->value.s, pszValue);
   attrib->value.len = iValueLen;
   
   return attrib;
}

NODE *createChildNode(NODE *node, char *pszName) {
   
   int iNameLen  = strlen(pszName);
   
   NODE *childNode = (NODE *)malloc(sizeof(NODE));
   memset(childNode, 0, sizeof(NODE));
   
   childNode->name.s = (char *)malloc(iNameLen + 1);
   strcpy(childNode->name.s, pszName);
   childNode->name.len = iNameLen;
   
   NODE *nextNode = NULL;
   
   if(node) {
      nextNode = node->child;
      node->child = childNode;
   }
   childNode->parent = node;
   
   if (nextNode) {
      childNode->next = nextNode;
      nextNode->prev = childNode;
   }
   
   return childNode;
}

void freeNodeList(NODE_LIST *nodeList) {
   NODE_LIST *next;
   
   while(nodeList) {
      next = nodeList->next;
      free(nodeList);
      nodeList = next;
   }
}

// deletes a node anywhere in the tree, links prev 
// with next and sets child of parent if needed
void deleteNode(NODE *node) {
   
   // void freeNode(NODE *node) {
   
   if (!node) return;
   
   if (node->prev) {
      node->prev->next = node->next;
   }
   else if (node->parent) {
      node->parent->child = node->next;
   }
   
   if (node->next) {
      node->next->prev = node->prev;
   }
   
   node->next = NULL;
   
   freeNode(node);
}

void offsetN(FILE *f, int level){
   int i = 0;
   for (i = 0; i < level; i++) 
      fprintf(f, "   ");
}

void printTree(FILE *f, NODE * node, int level)
{
   NODE *tmpNode;
   tmpNode = node;
   
   // brothers
   while (tmpNode) {
      // opentag
      offsetN(f, level);
      fprintf(f, "<%.*s", tmpNode->name.len, tmpNode->name.s);
      
      if (tmpNode->nV){
         nameValue *tmpNV;
         
         tmpNV = tmpNode->nV;
         // atributes
         while (tmpNV) {
            fprintf(f, " %.*s = \"%.*s\"", tmpNV->name.len, tmpNV->name.s, tmpNV->value.len, tmpNV->value.s);
            tmpNV = tmpNV->next;
         }
      }
      
      if (tmpNode->content.s || tmpNode->child) {
         fprintf(f, ">");
      }
      else {
         fprintf(f, " />");
      }
      
      if (tmpNode->content.s){
         // content
         fprintf(f, "%.*s", tmpNode->content.len, tmpNode->content.s);
      }
      else {
         fprintf(f, "\n");
         
         // children
         if (tmpNode->child) {
            printTree(f, tmpNode->child, level+1);
         }
      }
      
      // close tag
      if (tmpNode->content.s || tmpNode->child) {
         offsetN(f, level);
         fprintf(f, "</%.*s>\n", tmpNode->name.len, tmpNode->name.s); 
      }
      tmpNode = tmpNode->next;
   }
}

void storeXML(const char *pszFileName, NODE *node){
   
   FILE *f = fopen(pszFileName, "w");
   
   if(!f) return;
   
   printTree(f, node, 0);
   
   fclose(f);
}
#ifdef __SYMBIAN32__
#include <f32file.h>
#define _wfopen wfopen
#endif

void storeXML_W(const short *pszFileName, NODE *node){
   
#if !defined(ANDROID_NDK) && !defined(__APPLE__) && !defined(__linux__)
   
   wchar_t rb[] = {'w',0};
   FILE *f = _wfopen((wchar_t*)pszFileName,&rb[0]); //"rb");
#else
   
   char bufFn[1024];
   void convert16to8S(char *dst, int iMaxDstSize, const short *src, int iLen);
   convert16to8S(&bufFn[0], sizeof(bufFn)-1, (short *)pszFileName, 0); // *** WD: what happens if file name is longer that 128 chars?
   FILE *f = fopen(&bufFn[0], "w");
#endif
   
   if(!f)
      return;
   
   printTree(f, node, 0);
   
   fclose(f);
}

NODE *findNode(NODE *parent,char *name, int fRecusrisve, int iNameLen)
{
   NODE *p = NULL;
   
   if (!parent)
      return NULL;
   NODE *ch = parent->child;
   
   if(iNameLen == 0)
      iNameLen = strlen(name);
   if(iNameLen == parent->name.len && strncmp(name,parent->name.s,iNameLen) == 0)
      return parent;
   
   while (ch) {
      if(iNameLen == ch->name.len && strncmp(name,ch->name.s,iNameLen) == 0) {
         p = ch;
         break;
      }
      if(fRecusrisve && ch->child) {
         p = findNode(ch,name,fRecusrisve,iNameLen);
         if(p)break;
      }
      ch = ch->next;
   }
   return p;
}


