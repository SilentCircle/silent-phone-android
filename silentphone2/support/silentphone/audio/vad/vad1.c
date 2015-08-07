/*
*****************************************************************************
**-------------------------------------------------------------------------**
**                                                                         **
**     GSM AMR-NB speech codec   R98   Version 7.6.0   December 12, 2001       **
**                               R99   Version 3.3.0                       **
**                               REL-4 Version 4.1.0                       **
**                                                                         **
**-------------------------------------------------------------------------**
*****************************************************************************
*
*      File             : vad1.c
*      Purpose          : Voice Activity Detection (VAD) for AMR (option 1)
*
*****************************************************************************
*/

/*
*****************************************************************************
*                         MODULE INCLUDE FILE AND VERSION ID
*****************************************************************************
*/
#include "vad.h"
//const char vad1_id[] = "@(#)$Id $" vad_h;
 #define T_PRINTF //
/*
*****************************************************************************
*                         INCLUDE FILES
*****************************************************************************
*/
#include <stdlib.h>
#include <stdio.h>
#include "typedef.h"
#include "basic_op.h"
//#include "basicop2.inl"
#include "countT.h"
#include "oper_32b.inl"
#include "cnst_vad.h"

/*
*****************************************************************************
*                         LOCAL VARIABLES AND TABLES
*****************************************************************************
*/

/*
********************************************************************************
*                         PRIVATE PROGRAM CODE
********************************************************************************
*/
/****************************************************************************
 *
 *     Function     : first_filter_stage
 *     Purpose      : Scale input down by one bit. Calculate 5th order
 *                    half-band lowpass/highpass filter pair with
 *                    decimation.
 *
 ***************************************************************************/
static void first_filter_stage(Word16 in[],  /* i   : input signal                  */
                               Word16 out[], /* o   : output values, every other    */
                                             /*       output is low-pass part and   */
                                             /*       other is high-pass part every */
                               Word16 data[] /* i/o : filter memory                 */
                               )
{
  Word16 temp0, temp1, temp2, temp3, i;
  Word16 data0, data1;

  data0 = data[0];                                          move16 ();
  data1 = data[1];                                          move16 ();
 
  for (i = 0; i < FRAME_LEN/4; i++)
  {
     temp0 = sub(shr(in[4*i+0], 2), mult(COEFF5_1, data0));
     temp1 = add(data0, mult(COEFF5_1, temp0));
     
     temp3 = sub(shr(in[4*i+1], 2), mult(COEFF5_2, data1));
     temp2 = add(data1, mult(COEFF5_2, temp3));
     
     out[4*i+0] = add(temp1, temp2);                        move16 ();
     out[4*i+1] = sub(temp1, temp2);                        move16 ();
     
     data0 = sub(shr(in[4*i+2], 2), mult(COEFF5_1, temp0));
     temp1 = add(temp0, mult(COEFF5_1, data0));
     
     data1 = sub(shr(in[4*i+3], 2), mult(COEFF5_2, temp3));
     temp2 = add(temp3, mult(COEFF5_2, data1));
     
     out[4*i+2] = add(temp1, temp2);                       move16 ();
     out[4*i+3] = sub(temp1, temp2);                       move16 ();
  } 
  
  data[0] = data0;                                         move16 ();
  data[1] = data1;                                         move16 ();
}

/****************************************************************************
 *
 *     Function     : filter5
 *     Purpose      : Fifth-order half-band lowpass/highpass filter pair with
 *                    decimation.
 *
 ***************************************************************************/
static void filter5(Word16 *in0,    /* i/o : input values; output low-pass part  */
                    Word16 *in1,    /* i/o : input values; output high-pass part */
                    Word16 data[]   /* i/o : updated filter memory               */
                    )
{
  Word16 temp0, temp1, temp2;

  temp0 = sub(*in0, mult(COEFF5_1, data[0]));
  temp1 = add(data[0], mult(COEFF5_1, temp0));
  data[0] = temp0;                                move16 ();

  temp0 = sub(*in1, mult(COEFF5_2, data[1]));
  temp2 = add(data[1], mult(COEFF5_2, temp0));
  data[1] = temp0;                                move16 ();

  *in0 = shr(add(temp1, temp2), 1);               move16 ();
  *in1 = shr(sub(temp1, temp2), 1);               move16 ();
}

/****************************************************************************
 *
 *     Function     : filter3
 *     Purpose      : Third-order half-band lowpass/highpass filter pair with
 *                    decimation.
 *     Return value : 
 *
 ***************************************************************************/
static void filter3(Word16 *in0,   /* i/o : input values; output low-pass part  */ 
                    Word16 *in1,   /* i/o : input values; output high-pass part */
                    Word16 *data   /* i/o : updated filter memory               */
                    )
{
  Word16 temp1, temp2;

  temp1 = sub(*in1, mult(COEFF3, *data));
  temp2 = add(*data, mult(COEFF3, temp1));
  *data = temp1;                              move16 ();

  *in1 = shr(sub(*in0, temp2), 1);            move16 ();
  *in0 = shr(add(*in0, temp2), 1);            move16 ();
}

/****************************************************************************
 *
 *     Function     : level_calculation
 *     Purpose      : Calculate signal level in a sub-band. Level is calculated
 *                    by summing absolute values of the input data.
 *     Return value : signal level
 *
 ***************************************************************************/
static Word16 level_calculation(
    Word16 data[],     /* i   : signal buffer                                    */
    Word16 *sub_level, /* i   : level calculate at the end of the previous frame */
                       /* o   : level of signal calculated from the last         */
                       /*       (count2 - count1) samples                        */
    Word16 count1,     /* i   : number of samples to be counted                  */
    Word16 count2,     /* i   : number of samples to be counted                  */
    Word16 ind_m,      /* i   : step size for the index of the data buffer       */
    Word16 ind_a,      /* i   : starting index of the data buffer                */
    Word16 scale       /* i   : scaling for the level calculation                */
    )
{
  Word32 l_temp1, l_temp2;
  Word16 level, i;

  l_temp1 = 0L;                                           move32 ();
  for (i = count1; i < count2; i++)
  {
     l_temp1 = L_mac(l_temp1, 1, abs_s(data[ind_m*i+ind_a]));
  }
  
  l_temp2 = L_add(l_temp1, L_shl(*sub_level, sub(16, scale)));
  *sub_level = extract_h(L_shl(l_temp1, scale));
  
  for (i = 0; i < count1; i++)
  {
     l_temp2 = L_mac(l_temp2, 1, abs_s(data[ind_m*i+ind_a]));
  }
  level = extract_h(L_shl(l_temp2, scale));
  
  return level;
}
/*
#include <windows.h>
void drawlev(Word16 level[])
{
   int i;
  static int aa;
  

  void* h=(void*)GetDC(NULL);
  aa++;

  for(i=0;i<8;i++)
  {
     SetPixel(h,aa,i*3,level[i]/128+128);
     SetPixel(h,aa,i*3+1,level[i]/128+128);
     SetPixel(h,aa,i*3+2,level[i]/128+128);
  }
  if(aa>1200)aa=0;
  ReleaseDC(NULL,h);

}
*/
/****************************************************************************
 *
 *     Function     : filter_bank
 *     Purpose      : Divides input signal into 9-bands and calculas level of
 *                    the signal in each band 
 *
 ***************************************************************************/
static void filter_bank(vadState1 *st,  /* i/o : State struct               */
                        Word16 in[],   /* i   : input frame                */
                        Word16 level[] /* 0   : signal levels at each band */
                        )
{
  Word16 i;
  Word16 *tmp_buf=st->tmp_buf;
  //Word16 tmp_buf[FRAME_LEN];
  int iOpt;

  /* calculate the filter bank */

  first_filter_stage(in, tmp_buf, st->a_data5[0]);



  for (i = 0; i < FRAME_LEN/4; i++)
  {
     iOpt=4*i;
     filter5(&tmp_buf[iOpt], &tmp_buf[iOpt+2], st->a_data5[1]);
     filter5(&tmp_buf[iOpt+1], &tmp_buf[iOpt+3], st->a_data5[2]);
  }
  for (i = 0; i < FRAME_LEN/8; i++)
  {
     iOpt=8*i;
     filter3(&tmp_buf[iOpt+0], &tmp_buf[iOpt+4], &st->a_data3[0]);
     filter3(&tmp_buf[iOpt+2], &tmp_buf[iOpt+6], &st->a_data3[1]);
     filter3(&tmp_buf[iOpt+3], &tmp_buf[iOpt+7], &st->a_data3[4]);
  }
  
  for (i = 0; i < FRAME_LEN/16; i++)
  {
     iOpt=16*i;
     filter3(&tmp_buf[iOpt+0], &tmp_buf[iOpt+8], &st->a_data3[2]);
     filter3(&tmp_buf[iOpt+4], &tmp_buf[iOpt+12], &st->a_data3[3]);
  }

  /* calculate levels in each frequency band */
  
  /* 3000 - 4000 Hz*/
  level[8] = level_calculation(tmp_buf, &st->sub_level[8], FRAME_LEN/4-8,
                               FRAME_LEN/4, 4, 1, 15);
  move16 ();
  /* 2500 - 3000 Hz*/  
  level[7] = level_calculation(tmp_buf, &st->sub_level[7], FRAME_LEN/8-4,
                               FRAME_LEN/8, 8, 7, 16);
  move16 ();
  /* 2000 - 2500 Hz*/
  level[6] = level_calculation(tmp_buf, &st->sub_level[6], FRAME_LEN/8-4,
                               FRAME_LEN/8, 8, 3, 16);
  move16 ();
  /* 1500 - 2000 Hz*/
  level[5] = level_calculation(tmp_buf, &st->sub_level[5], FRAME_LEN/8-4,
                               FRAME_LEN/8, 8, 2, 16);
  move16 ();
  /* 1000 - 1500 Hz*/
  level[4] = level_calculation(tmp_buf, &st->sub_level[4], FRAME_LEN/8-4,
                               FRAME_LEN/8, 8, 6, 16);
  move16 ();
  /* 750 - 1000 Hz*/
  level[3] = level_calculation(tmp_buf, &st->sub_level[3], FRAME_LEN/16-2,
                               FRAME_LEN/16, 16, 4, 16);
  move16 ();
  /* 500 - 750 Hz*/
  level[2] = level_calculation(tmp_buf, &st->sub_level[2], FRAME_LEN/16-2,
                               FRAME_LEN/16, 16, 12, 16);
  move16 ();
  /* 250 - 500 Hz*/
  level[1] = level_calculation(tmp_buf, &st->sub_level[1], FRAME_LEN/16-2,
                               FRAME_LEN/16, 16, 8, 16);
  move16 ();
  /* 0 - 250 Hz*/
  level[0] = level_calculation(tmp_buf, &st->sub_level[0], FRAME_LEN/16-2,
                               FRAME_LEN/16, 16, 0, 16);

 // drawlev(level);
  move16 ();
}

/****************************************************************************
 *
 *     Function   : update_cntrl
 *     Purpose    : Control update of the background noise estimate.
 *     Inputs     : pitch:      flags for pitch detection
 *                  stat_count: stationary counter
 *                  tone:       flags indicating presence of a tone
 *                  complex:      flags for complex  detection
 *                  vadreg:     intermediate VAD flags
 *     Output     : stat_count: stationary counter
 *
 ***************************************************************************/
static void update_cntrl(vadState1 *st,  /* i/o : State struct                       */
                         Word16 level[] /* i   : sub-band levels of the input frame */
                         )
{
  Word16 i, temp, stat_rat, exp;
  Word16 num, denom;
  Word16 alpha; 

  /* handle highband complex signal input  separately       */
  /* if ther has been highband correlation for some time    */
  /* make sure that the VAD update speed is low for a while */
  test ();
  if (st->complex_warning != 0)
  {
     test ();
     if (sub(st->stat_count, CAD_MIN_STAT_COUNT) < 0)
     {
        st->stat_count = CAD_MIN_STAT_COUNT;              move16 ();    
     }
  }
  /* NB stat_count is allowed to be decreased by one below again  */
  /* deadlock in speech is not possible unless the signal is very */
  /* complex and need a high rate                                 */

  /* if fullband pitch or tone have been detected for a while, initialize stat_count */
  logic16 (); test (); logic16 (); test ();
  if ((sub((st->pitch & 0x6000), 0x6000) == 0) ||
      (sub((st->tone & 0x7c00), 0x7c00) == 0))
  {
     st->stat_count = STAT_COUNT;                          move16 ();  
  }
  else
  {
     /* if 8 last vad-decisions have been "0", reinitialize stat_count */
     logic16 (); test ();
     if ((st->vadreg & 0x7f80) == 0) 
     { 
        st->stat_count = STAT_COUNT;                       move16 ();
     }
     else
     {
        stat_rat = 0;                                      move16 ();
        for (i = 0; i < COMPLEN; i++)
        {
           test ();
           if (sub(level[i], st->ave_level[i]) > 0)
           {
              num = level[i];                              move16 ();
              denom = st->ave_level[i];                    move16 ();
           }
           else
           {
              num = st->ave_level[i];                      move16 ();
              denom = level[i];                            move16 ();
           }
           /* Limit nimimum value of num and denom to STAT_THR_LEVEL */
           test ();
           if (sub(num, STAT_THR_LEVEL) < 0)
           {
              num = STAT_THR_LEVEL;                        move16 ();
           }
           test ();
           if (sub(denom, STAT_THR_LEVEL) < 0)
           {
              denom = STAT_THR_LEVEL;                      move16 ();
           }
           
           exp = norm_s(denom);
           denom = shl(denom, exp);
           
           /* stat_rat = num/denom * 64 */
           temp = div_s(shr(num, 1), denom);
           stat_rat = add(stat_rat, shr(temp, sub(8, exp)));
        }
        
        /* compare stat_rat with a threshold and update stat_count */
        test ();
        if (sub(stat_rat, STAT_THR) > 0)
        {
           st->stat_count = STAT_COUNT;                    move16 ();
        }
        else
        {
           logic16 ();test ();
           if ((st->vadreg & 0x4000) != 0)
           {
              test ();
              if (st->stat_count != 0)
              {
                 st->stat_count = sub(st->stat_count, 1);  move16 ();
              }
           }
        }
     }
  }
  
  /* Update average amplitude estimate for stationarity estimation */
  alpha = ALPHA4;                                          move16 ();
  test ();
  if (sub(st->stat_count, STAT_COUNT) == 0) 
  {
     alpha = 32767;                                        move16 ();
  }
  else if ((st->vadreg & 0x4000) == 0) 
  {
     logic16 (); test ();
     alpha = ALPHA5;                                       move16 ();
  }
  
  for (i = 0; i < COMPLEN; i++)
  {
     st->ave_level[i] = add(st->ave_level[i],
                            mult_r(alpha, sub(level[i], st->ave_level[i])));
     move16 ();
  }  
}

/****************************************************************************
 *
 *     Function     : hangover_addition
 *     Purpose      : Add hangover for complex signal or after speech bursts
 *     Inputs       : burst_count:  counter for the length of speech bursts
 *                    hang_count:   hangover counter
 *                    vadreg:       intermediate VAD decision
 *     Outputs      : burst_count:  counter for the length of speech bursts
 *                    hang_count:   hangover counter
 *     Return value : VAD_flag indicating final VAD decision
 *
 ***************************************************************************/
static Word16 hangover_addition(
              vadState1 *st,       /* i/o : State struct                     */
              Word16 noise_level, /* i   : average level of the noise       */
                                  /*       estimates                        */
              Word16 low_power    /* i   : flag power of the input frame    */
              )
{
   Word16 hang_len, burst_len;
   
   /* 
      Calculate burst_len and hang_len
      burst_len: number of consecutive intermediate vad flags with "1"-decision
                 required for hangover addition
      hang_len:  length of the hangover
      */

   test ();
   if (sub(noise_level, HANG_NOISE_THR) > 0)
   {
      burst_len = BURST_LEN_HIGH_NOISE;                           move16 ();
      hang_len = HANG_LEN_HIGH_NOISE;                             move16 ();
   }
   else
   {
      burst_len = BURST_LEN_LOW_NOISE;                            move16 ();
      hang_len = HANG_LEN_LOW_NOISE;                              move16 ();
   }
   
   /* if the input power (pow_sum) is lower than a threshold, clear
      counters and set VAD_flag to "0"  "fast exit"                 */
   test ();
   if (low_power != 0)
   {
      st->burst_count = 0;                                        move16 ();
      st->hang_count = 0;                                         move16 ();
      st->complex_hang_count = 0;                                 move16 ();
      st->complex_hang_timer = 0;                                 move16 ();
      return 0;
   }
   
   test ();
   if (sub(st->complex_hang_timer, CVAD_HANG_LIMIT) > 0)
   {
      test ();
      if (sub(st->complex_hang_count, CVAD_HANG_LENGTH) < 0)
      {
         st->complex_hang_count = CVAD_HANG_LENGTH;               move16 ();
      }      
   }
   
   /* long time very complex signal override VAD output function */
   test ();
   if (st->complex_hang_count != 0)
   {
      st->burst_count = BURST_LEN_HIGH_NOISE;                     move16 ();
      st->complex_hang_count = sub(st->complex_hang_count, 1);    move16 ();
      return 1; 
   }
   else
   {
      /* let hp_corr work in from a noise_period indicated by the VAD */
      test (); test (); logic16 ();
      if (((st->vadreg & 0x3ff0) == 0) &&
          (sub(st->corr_hp_fast, CVAD_THRESH_IN_NOISE) > 0))
      {
         return 1;
      }  
   }

   /* update the counters (hang_count, burst_count) */
   logic16 (); test ();
   if ((st->vadreg & 0x4000) != 0)
   {
      st->burst_count = add(st->burst_count, 1);                  move16 ();
      test ();
      if (sub(st->burst_count, burst_len) >= 0)
      {
         st->hang_count = hang_len;                               move16 ();
      }
      return 1;
   }
   else
   {
      st->burst_count = 0;                                        move16 ();
      test ();
      if (st->hang_count > 0)
      {
         st->hang_count = sub(st->hang_count, 1);                 move16 ();
         return 1;
      }
   }
   return 0;
}

/****************************************************************************
 *
 *     Function   : noise_estimate_update
 *     Purpose    : Update of background noise estimate
 *     Inputs     : bckr_est:   background noise estimate
 *                  pitch:      flags for pitch detection
 *                  stat_count: stationary counter
 *     Outputs    : bckr_est:   background noise estimate
 *
 ***************************************************************************/
static void noise_estimate_update(
                  vadState1 *st,    /* i/o : State struct                       */
                  Word16 level[]   /* i   : sub-band levels of the input frame */
                  )
{
   Word16 i, alpha_up, alpha_down, bckr_add;
   
   /* Control update of bckr_est[] */
   update_cntrl(st, level);
   
   /* Choose update speed */
   bckr_add = 2;                                           move16 ();
   
   logic16 (); test (); logic16 (); test (); test ();
   if (((0x7800 & st->vadreg) == 0) && 
       ((st->pitch & 0x7800) == 0) 
       &&  (st->complex_hang_count == 0))
   {
      alpha_up = ALPHA_UP1;                                move16 ();
      alpha_down = ALPHA_DOWN1;                            move16 ();
   }
   else 
   {
      test (); test ();
      if ((st->stat_count == 0) 
          && (st->complex_hang_count == 0))
      {
         alpha_up = ALPHA_UP2;                             move16 ();
         alpha_down = ALPHA_DOWN2;                         move16 ();
      }
      else
      {
         alpha_up = 0;                                     move16 ();
         alpha_down = ALPHA3;                              move16 ();
         bckr_add = 0;                                     move16 ();
      }
   }
   
   /* Update noise estimate (bckr_est) */
   for (i = 0; i < COMPLEN; i++)
   {
      Word16 temp;
      temp = sub(st->old_level[i], st->bckr_est[i]);
      
      test ();
      if (temp < 0)
      { /* update downwards*/
         st->bckr_est[i] = add(-2, add(st->bckr_est[i], mult_r(alpha_down, temp)));
         move16 ();
         
         /* limit minimum value of the noise estimate to NOISE_MIN */
         test ();
         if (sub(st->bckr_est[i], NOISE_MIN) < 0)
         {
            st->bckr_est[i] = NOISE_MIN;                  move16 ();
         }
      }
      else
      { /* update upwards */
         st->bckr_est[i] = add(bckr_add, add(st->bckr_est[i], mult_r(alpha_up, temp)));
         move16 ();
         
         /* limit maximum value of the noise estimate to NOISE_MAX */
         test ();
         if (sub(st->bckr_est[i], NOISE_MAX) > 0)
         {
            st->bckr_est[i] = NOISE_MAX;                  move16 ();
         }
      }
   }
   
   /* Update signal levels of the previous frame (old_level) */
   for(i = 0; i < COMPLEN; i++)
   {
      st->old_level[i] = level[i];                        move16 ();
   }
}

/****************************************************************************
 *
 *     Function   : complex_estimate_adapt
 *     Purpose    : Update/adapt of complex signal estimate
 *     Inputs     : low_power:   low signal power flag 
 *     Outputs    : st->corr_hp_fast:   long term complex signal estimate
 *
 ***************************************************************************/
static void complex_estimate_adapt(
         vadState1 *st,       /* i/o : VAD state struct                       */
         Word16 low_power    /* i   : very low level flag of the input frame */
         )
{
   Word16 alpha;            /* Q15 */
   Word32 L_tmp;            /* Q31 */


   /* adapt speed on own state */
   test ();
   if (sub(st->best_corr_hp, st->corr_hp_fast) < 0) /* decrease */
   {
      test ();
      if (sub(st->corr_hp_fast, CVAD_THRESH_ADAPT_HIGH) < 0)
      {  /* low state  */
         alpha = CVAD_ADAPT_FAST;                          move16(); 
      }  
      else 
      {  /* high state */
         alpha = CVAD_ADAPT_REALLY_FAST;                   move16();   
      }      
   }
   else  /* increase */ 
   {
      test ();
      if (sub(st->corr_hp_fast, CVAD_THRESH_ADAPT_HIGH) < 0)
      {  
         alpha = CVAD_ADAPT_FAST;                          move16(); 
      }  
      else 
      {  
         alpha = CVAD_ADAPT_SLOW;                          move16();
      }      
   }

   L_tmp = L_deposit_h(st->corr_hp_fast);
   L_tmp = L_msu(L_tmp, alpha, st->corr_hp_fast);
   L_tmp = L_mac(L_tmp, alpha, st->best_corr_hp);
   st->corr_hp_fast = round(L_tmp);           /* Q15 */    move16();   

   test ();
   if (sub(st->corr_hp_fast, CVAD_MIN_CORR) <  0)
   {
      st->corr_hp_fast = CVAD_MIN_CORR;                    move16();
   }

   test ();
   if (low_power != 0)
   {
      st->corr_hp_fast = CVAD_MIN_CORR;                    move16();
   }   
}

/****************************************************************************
 *
 *     Function     : complex_vad
 *     Purpose      : complex background decision
 *     Return value : the complex background decision
 *
 ***************************************************************************/
static Word16 complex_vad(vadState1 *st,    /* i/o : VAD state struct              */
                          Word16 low_power /* i   : flag power of the input frame */
                          )
{
   st->complex_high = shr(st->complex_high, 1);                      move16 ();
   st->complex_low = shr(st->complex_low, 1);                        move16 ();

   test ();
   if (low_power == 0)
   {
      test ();
      if (sub(st->corr_hp_fast, CVAD_THRESH_ADAPT_HIGH) > 0)
      {
         st->complex_high = st->complex_high | 0x4000;   logic16 (); move16 ();
      }
      
      test ();
      if (sub(st->corr_hp_fast, CVAD_THRESH_ADAPT_LOW) > 0 )
      {
         st->complex_low = st->complex_low | 0x4000;     logic16 (); move16 ();
      }
   }

   test ();
   if (sub(st->corr_hp_fast, CVAD_THRESH_HANG) > 0)
   {
      st->complex_hang_timer = add(st->complex_hang_timer, 1);       move16 ();
   }
   else
   {
      st->complex_hang_timer =  0;                                   move16 ();
   }               
   
   test (); logic16 (); test (); logic16 ();
   return ((sub((st->complex_high & 0x7f80), 0x7f80) == 0) ||
           (sub((st->complex_low & 0x7fff), 0x7fff) == 0));
}

/****************************************************************************
 *
 *     Function     : vad_decision
 *     Purpose      : Calculates VAD_flag
 *     Inputs       : bckr_est:    background noise estimate
 *                    vadreg:      intermediate VAD flags
 *     Outputs      : noise_level: average level of the noise estimates
 *                    vadreg:      intermediate VAD flags
 *     Return value : VAD_flag
 *
 ***************************************************************************/
static Word16 vad_decision(
             vadState1 *st,          /* i/o : State struct                       */
             Word16 level[COMPLEN], /* i   : sub-band levels of the input frame */
             Word32 pow_sum         /* i   : power of the input frame           */
             )
{
   Word16 i;
   Word16 snr_sum;
   Word32 L_temp;
   Word16 vad_thr, temp, noise_level;
   Word16 low_power_flag;
   
   /* 
      Calculate squared sum of the input levels (level)
      divided by the background noise components (bckr_est).
      */
   L_temp = 0;                                            move32();
   for (i = 0; i < COMPLEN; i++)
   {
      Word16 exp;
      
      exp = norm_s(st->bckr_est[i]);
      temp = shl(st->bckr_est[i], exp);
      temp = div_s(shr(level[i], 1), temp);
      temp = shl(temp, sub(exp, UNIRSHFT-1));
      L_temp = L_mac(L_temp, temp, temp);
   }
   snr_sum = extract_h(L_shl(L_temp, 6));
   snr_sum = mult(snr_sum, INV_COMPLEN);

   /* Calculate average level of estimated background noise */
   L_temp = 0;                                            move32();
   for (i = 0; i < COMPLEN; i++)
   {
      L_temp = L_add(L_temp, st->bckr_est[i]);
   }
   
   noise_level = extract_h(L_shl(L_temp, 13));
   
   /* Calculate VAD threshold */
   vad_thr = add(mult(VAD_SLOPE, sub(noise_level, VAD_P1)), VAD_THR_HIGH);
   
   test ();
   if (sub(vad_thr, VAD_THR_LOW) < 0)
   {
      vad_thr = VAD_THR_LOW;                              move16 ();
   }
   
   /* Shift VAD decision register */
   st->vadreg = shr(st->vadreg, 1);                       move16 ();
   
   /* Make intermediate VAD decision */
   test ();
   if (sub(snr_sum, vad_thr) > 0)
   {
      st->vadreg = st->vadreg | 0x4000;       logic16 (); move16 ();
   }
   /* primary vad decsion made */
   
   /* check if the input power (pow_sum) is lower than a threshold" */
   test ();
   if (L_sub(pow_sum, VAD_POW_LOW) < 0)
   {
      low_power_flag = 1;                                 move16 ();
   }
   else
   {
      low_power_flag = 0;                                 move16 ();
   }
   
   /* update complex signal estimate st->corr_hp_fast and hangover reset timer using */
   /* low_power_flag and corr_hp_fast  and various adaptation speeds                 */
   complex_estimate_adapt(st, low_power_flag);

   /* check multiple thresholds of the st->corr_hp_fast value */
   st->complex_warning = complex_vad(st, low_power_flag); move16();    

   /* Update speech subband vad background noise estimates */
   noise_estimate_update(st, level);
     
   /*  Add speech and complex hangover and return speech VAD_flag */
   /*  long term complex hangover may be added */
   st->speech_vad_decision = hangover_addition(st, noise_level, low_power_flag);
   move16 ();
   
   return (st->speech_vad_decision);
}

/*
*****************************************************************************
*                         PUBLIC PROGRAM CODE
*****************************************************************************
*/
/*************************************************************************
*
*  Function:   vad1_init
*  Purpose:    Allocates state memory and initializes state memory
*
**************************************************************************
*/
int vad1_init (vadState1 **state)
{
    vadState1* s;
    
    if (state == (vadState1 **) NULL){
        fprintf(stderr, "vad_init: invalid parameter\n");
        return -1;
    }
    *state = NULL;
    
    /* allocate memory */
    if ((s = (vadState1 *) malloc(sizeof(vadState1))) == NULL){
        fprintf(stderr, "vad_init: can not malloc state structure\n");
        return -1;
    }
    
    vad1_reset(s);
    
    *state = s;
    
    return 0;
}
 
/*************************************************************************
*
*  Function:   vad1_reset
*  Purpose:    Initializes state memory to zero
*
**************************************************************************
*/
int vad1_reset (vadState1 *state)
{
   Word16 i, j;
   
   if (state == (vadState1 *) NULL){
      fprintf(stderr, "vad_reset: invalid parameter\n");
      return -1;
   }
   
   /* Initialize pitch detection variables */
   state->oldlag_count = 0;
   state->oldlag = 0;         
   state->pitch = 0;
   state->tone = 0;            

   state->complex_high = 0;            
   state->complex_low = 0;            
   state->complex_hang_timer = 0;

   state->vadreg = 0;         

   state->stat_count = 0;    
   state->burst_count = 0;    
   state->hang_count = 0;     
   state->complex_hang_count = 0;     
   
   /* initialize memory used by the filter bank */
   for (i = 0; i < 3; i++)
   {
      for (j = 0; j < 2; j++) 
      {
         state->a_data5[i][j] = 0;  
      }
   }
   
   for (i = 0; i < 5; i++)
   {
      state->a_data3[i] = 0;        
   }
   
   /* initialize the rest of the memory */
   for (i = 0; i < COMPLEN; i++)
   {
      state->bckr_est[i] = NOISE_INIT;  
      state->old_level[i] = NOISE_INIT; 
      state->ave_level[i] = NOISE_INIT; 
      state->sub_level[i] = 0;          
   }
   
   state->best_corr_hp = CVAD_LOWPOW_RESET; 

   state->speech_vad_decision = 0;
   state->complex_warning = 0;
   state->sp_burst_count = 0;        

   state->corr_hp_fast = CVAD_LOWPOW_RESET;
 
   return 0;
}

/*************************************************************************
*
*  Function:   vad1_exit
*  Purpose:    The memory used for state memory is freed
*
**************************************************************************
*/
void vad1_exit (vadState1 **state)
{
    if (state == NULL || *state == NULL)
        return;
    
    /* deallocate memory */
    free(*state);
    *state = NULL;
    
    return;
}

/****************************************************************************
 *
 *     Function     : vad_complex_detection_update
 *     Purpose      : update vad->bestCorr_hp  complex signal feature state 
 *
 ***************************************************************************/
void vad_complex_detection_update (vadState1 *st,       /* i/o : State struct */
                                   Word16 best_corr_hp /* i   : best Corr    */
                                   )
{
   st->best_corr_hp = best_corr_hp;         move16();
}

/****************************************************************************
 *
 *     Function     : vad_tone_detection
 *     Purpose      : Set tone flag if pitch gain is high. This is used to detect
 *                    signaling tones and other signals with high pitch gain.
 *     Inputs       : tone: flags indicating presence of a tone
 *     Outputs      : tone: flags indicating presence of a tone
 *
 ***************************************************************************/
void vad_tone_detection (vadState1 *st,  /* i/o : State struct            */
                         Word32 t0,     /* i   : autocorrelation maxima  */
                         Word32 t1      /* i   : energy                  */
                         )
{
   Word16 temp;
   /* 
      if (t0 > TONE_THR * t1)
      set tone flag
      */
   temp = round(t1);
   
   test (); test ();
   if ((temp > 0) && (L_msu(t0, temp, TONE_THR) > 0))
   {
      st->tone = st->tone | 0x4000;              logic16 (); move16 ();
   }
}

/****************************************************************************
 *
 *     Function     : vad_tone_detection_update
 *     Purpose      : Update the tone flag register. Tone flags are shifted right
 *                    by one bit. This function should be called from the speech
 *                    encoder before call to Vad_tone_detection() function.
 *
 ***************************************************************************/
void vad_tone_detection_update (
                vadState1 *st,              /* i/o : State struct              */
                Word16 one_lag_per_frame   /* i   : 1 if one open-loop lag is
                                              calculated per each frame,
                                              otherwise 0                     */
                )
{
   /* Shift tone flags right by one bit */
   st->tone = shr(st->tone, 1);                move16 ();
   
   /* If open-loop lag is calculated only once in each frame, do extra update
      and assume that the other tone flag of the frame is one. */
   if (one_lag_per_frame != 0)
   {
      st->tone = shr(st->tone, 1);            
      st->tone = st->tone | 0x2000;            logic16 (); move16 ();
   }
}

/****************************************************************************
 *
 *     Function     : vad_pitch_detection
 *     Purpose      : Test whether signal contains pitch or other periodic
 *                    component.
 *     Return value : Boolean voiced / unvoiced decision in state variable 
 *
 ***************************************************************************/
void vad_pitch_detection (vadState1 *st,   /* i/o : State struct                  */
                          Word16 T_op[]   /* i   : speech encoder open loop lags */
                          )
{
   Word16 lagcount, i;
   
   lagcount = 0;               move16 ();
   
   for (i = 0; i < 2; i++)
   {
      test ();
      if (sub (abs_s (sub (st->oldlag, T_op[i])), LTHRESH) < 0)
      {
         lagcount = add (lagcount, 1);
      }
      
      /* Save the current LTP lag */
      st->oldlag = T_op[i];       move16 ();
   }
   
   /* Make pitch decision.
      Save flag of the pitch detection to the variable pitch.
      */
   st->pitch = shr(st->pitch, 1); move16();
   
   test ();
   if (sub ( add (st->oldlag_count, lagcount), NTHRESH) >= 0)
   {
      st->pitch = st->pitch | 0x4000; logic16(); move16();
   }
   
   /* Update oldlagcount */
   st->oldlag_count = lagcount;     move16 ();
}

/****************************************************************************
 *
 *     Function     : vad
 *     Purpose      : Main program for Voice Activity Detection (VAD) for AMR 
 *     Return value : VAD Decision, 1 = speech, 0 = noise
 *
 ***************************************************************************/
Word16 vad1(vadState1 *st,      /* i/o : State struct                 */
           Word16 in_buf[]    /* i   : samples of the input frame   */
           )
{
   Word16 level[COMPLEN];
   Word32 pow_sum;
   Word16 i;
   
   /* Calculate power of the input frame. */
   pow_sum = 0L;                                     move32 ();

   for (i = 0; i < FRAME_LEN; i++)
   {  
      pow_sum = L_mac(pow_sum, in_buf[i-LOOKAHEAD], in_buf[i-LOOKAHEAD]);
   }

   /*
     If input power is very low, clear pitch flag of the current frame
     */
   test ();
   if (L_sub(pow_sum, POW_PITCH_THR) < 0)
   {
      st->pitch = st->pitch & 0x3fff;                logic16 (); move16 ();
   }

   /*
     If input power is very low, clear complex flag of the "current" frame
     */
   test ();
   if (L_sub(pow_sum, POW_COMPLEX_THR) < 0)
   {
      st->complex_low = st->complex_low & 0x3fff;    logic16 (); move16 ();
   }
   
   /*
     Run the filter bank which calculates signal levels at each band
     */
   filter_bank(st, in_buf, level);
  // printf("%d ",pow_sum);
   
   return (vad_decision(st, level, pow_sum));
}
