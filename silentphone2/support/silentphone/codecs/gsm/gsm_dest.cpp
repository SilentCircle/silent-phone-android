/*
 * Copyright 1992 by Jutta Degener and Carsten Bormann, Technische
 * Universitaet Berlin.  See the accompanying file "COPYRIGHT" for
 * details.  THERE IS ABSOLUTELY NO WARRANTY FOR THIS SOFTWARE.
 */

/* $Header: /cvs/sources/codecs/gsm/gsm_dest.cpp,v 1.1.1.1 2005/10/07 13:03:49 janis Exp $ */

#include "gsm.h"
#include "private.h"
#include "proto.h"

void gsm_destroy P1((S), gsm S)
{
		if (S) free((char *)S);
}
