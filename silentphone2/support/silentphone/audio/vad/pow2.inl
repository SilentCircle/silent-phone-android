/*
********************************************************************************
*
*      GSM AMR-NB speech codec   R98   Version 7.6.0   December 12, 2001
*                                R99   Version 3.3.0                
*                                REL-4 Version 4.1.0                
*
********************************************************************************
*
*      File             : pow2.c
*      Purpose          : computes  L_x = pow(2.0, exponent.fraction)
*
********************************************************************************
*/
/*
********************************************************************************
*                         MODULE INCLUDE FILE AND VERSION ID
********************************************************************************
*/


 
/*
********************************************************************************
*                         INCLUDE FILES
********************************************************************************
*/
//#include "typedef.h"
//#include "basic_op.h"
//#include "count.h"
 
/*
********************************************************************************
*                         LOCAL VARIABLES AND TABLES
********************************************************************************
*/
#include "pow2.tab"     /* Table for Pow2() */
#define T_POW2_STATIC static
/*
********************************************************************************
*                         PUBLIC PROGRAM CODE
********************************************************************************
*/
/*************************************************************************
 *
 *   FUNCTION:  Pow2()
 *
 *   PURPOSE: computes  L_x = pow(2.0, exponent.fraction)
 *
 *   DESCRIPTION:
 *       The function Pow2(L_x) is approximated by a table and linear
 *       interpolation.
 *          1- i = bit10-b15 of fraction,   0 <= i <= 31
 *          2- a = bit0-b9   of fraction   
 *          3- L_x = table[i]<<16 - (table[i] - table[i+1]) * a * 2
 *          4- L_x = L_x >> (30-exponent)     (with rounding)
 *
 *************************************************************************/
T_POW2_STATIC Word32 Pow2 (           /* (o)  : result       (range: 0<=val<=0x7fffffff) */
    Word16 exponent,    /* (i)  : Integer part.      (range: 0<=val<=30)   */
    Word16 fraction     /* (i)  : Fractional part.  (range: 0.0<=val<1.0) */
)
{
    Word16 exp, i, a, tmp;
    Word32 L_x;

    L_x = L_mult (fraction, 32);/* L_x = fraction<<6           */
    i = extract_h (L_x);        /* Extract b10-b16 of fraction */
    L_x = L_shr (L_x, 1);
    a = extract_l (L_x);        /* Extract b0-b9   of fraction */
    a = a & (Word16) 0x7fff;    logic16 (); 

    L_x = L_deposit_h (table[i]);       /* table[i] << 16        */
    tmp = sub (table[i], table[i + 1]); /* table[i] - table[i+1] */
    L_x = L_msu (L_x, tmp, a);  /* L_x -= tmp*a*2        */

    exp = sub (30, exponent);
    L_x = L_shr_r (L_x, exp);

    return (L_x);
}
