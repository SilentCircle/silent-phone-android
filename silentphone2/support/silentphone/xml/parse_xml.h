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

typedef struct _STR_XML {
   char * s;
   int  len;
} STR_XML;

typedef struct _nameValue {
   int           bFreeValuePtr;
   STR_XML        name;
   STR_XML        value;
   struct _nameValue *next;
} nameValue;


typedef struct _NODE {
   STR_XML     name;
   nameValue * nV;     // Attributes
   STR_XML     content;
   void *pUserData;
   struct _NODE * child;
   struct _NODE * parent;
   struct _NODE * next;
   struct _NODE * prev;
} NODE;

typedef struct _NODE_LIST {
   NODE *node;
   struct _NODE_LIST * next;
} NODE_LIST;


NODE_LIST *getChildNodeList(NODE *node, char *pszNodeName);
void freeNodeList(NODE_LIST *nodeList);
void freeNode(NODE *node);
nameValue *getAttribute(NODE *node, char *pszAttribName);
NODE *getNodeWithAttrib(NODE_LIST *nl, char *pszAttribName, char *pszAttribValue);
nameValue *getAttribByNameValue(NODE *node, char* pszAttribName, char* pszAttribValue);
nameValue *getAttribByName(NODE *node, char* pszAttribName);
nameValue *getAttribByNameLen(NODE *node, char* pszAttribName, int iAttribLen);
void deleteNode(NODE *node);
void printTree(FILE *f, NODE * node, int level);
void storeXML(const char *pszFileName, NODE *node);
void storeXML_W(const short *pszFileName, NODE *node);
nameValue *createAttribute(NODE *node, char *pszName, char *pszValue);
NODE *createChildNode(NODE *node, char *pszName);
NODE *findNode(NODE *parent,char *name, int fRecusrisve, int iNameLen=0);

static NODE *getNode(NODE *n, char *name)
{
   NODE *p=findNode(n,name,0);
   if(!p)
      p=createChildNode(n,name);
   return p;
}


class CParseXml{
public:
   CParseXml(): bufFile(NULL), rootNode(NULL)  {}
   
   NODE* mainXML(const char *szFileName);
   NODE* mainXML(const short *szFileName);
   NODE* parseXml(char * inbuf, int iBufLen=0);
   ~CParseXml() {
      if(bufFile)
         delete bufFile;
      if(rootNode)
         freeNode(rootNode);
      rootNode=NULL;
   }
   
private:
   int newTok(STR_XML str);
   int splitXML();
   NODE *node();
   
   char * bufFile;
   NODE *rootNode;
   
   char *end;
   char *cur;
   STR_XML  nextStr;
   STR_XML  curStr;
   int flag;
   int curFlag;
   int nextFlag;
};

