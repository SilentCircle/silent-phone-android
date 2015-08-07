/*
*****************************************************************************
*
*      GSM AMR-NB speech codec   R98   Version 7.6.0   December 12, 2001
*                                R99   Version 3.3.0                
*                                REL-4 Version 4.1.0                
*
*****************************************************************************
*
*      File             : r_fft.c
*      Purpose          : Fast Fourier Transform (FFT) algorithm
*
*****************************************************************************
*/

/*****************************************************************
*
* This is an implementation of decimation-in-time FFT algorithm for
* real sequences.  The techniques used here can be found in several
* books, e.g., i) Proakis and Manolakis, "Digital Signal Processing",
* 2nd Edition, Chapter 9, and ii) W.H. Press et. al., "Numerical
* Recipes in C", 2nd Ediiton, Chapter 12.
*
* Input -  There is one input to this function:
*
*	1) An integer pointer to the input data array 
*
* Output - There is no return value.
*	The input data are replaced with transformed data.  If the
*	input is a real time domain sequence, it is replaced with
*	the complex FFT for positive frequencies.  The FFT value 
*	for DC and the foldover frequency are combined to form the
*	first complex number in the array.  The remaining complex
*	numbers correspond to increasing frequencies.  If the input
*	is a complex frequency domain sequence arranged	as above,
*	it is replaced with the corresponding time domain sequence. 
*
* Notes:
*
*	1) This function is designed to be a part of a VAD
*	   algorithm that requires 128-point FFT of real
*	   sequences.  This is achieved here through a 64-point
*	   complex FFT.  Consequently, the FFT size information is
*	   not transmitted explicitly.  However, some flexibility
*	   is provided in the function to change the size of the 
*	   FFT by specifying the size information through "define"
*	   statements.
*
*	2) The values of the complex sinusoids used in the FFT 
*	   algorithm are stored in a ROM table.
*
*	3) In the c_fft function, the FFT values are divided by
*	   2 after each stage of computation thus dividing the
*	   final FFT values by 64.  This is somewhat different
*          from the usual definition of FFT where the factor 1/N,
*          i.e., 1/64, used for the IFFT and not the FFT.  No factor
*          is used in the r_fft function.
*
*****************************************************************/

//const char r_fft_id[] = "@(#)$Id $";
#define T_PRINTF //

#include "typedef.h"
#include "cnst.h"
#include "basic_op.h"
#include "oper_32b.inl"
#include "countT.h"

#include "vad2.h"

#define			SIZE			128
#define			SIZE_BY_TWO		64
#define			NUM_STAGE		6
#define			TRUE			1
#define			FALSE			0

const static Word16 phs_tbl[] =
{

	32767, 0, 32729, -1608, 32610, -3212, 32413, -4808,
	32138, -6393, 31786, -7962, 31357, -9512, 30853, -11039,
	30274, -12540, 29622, -14010, 28899, -15447, 28106, -16846,
	27246, -18205, 26320, -19520, 25330, -20788, 24279, -22006,
	23170, -23170, 22006, -24279, 20788, -25330, 19520, -26320,
	18205, -27246, 16846, -28106, 15447, -28899, 14010, -29622,
	12540, -30274, 11039, -30853, 9512, -31357, 7962, -31786,
	6393, -32138, 4808, -32413, 3212, -32610, 1608, -32729,
	0, -32768, -1608, -32729, -3212, -32610, -4808, -32413,
	-6393, -32138, -7962, -31786, -9512, -31357, -11039, -30853,
	-12540, -30274, -14010, -29622, -15447, -28899, -16846, -28106,
	-18205, -27246, -19520, -26320, -20788, -25330, -22006, -24279,
	-23170, -23170, -24279, -22006, -25330, -20788, -26320, -19520,
	-27246, -18205, -28106, -16846, -28899, -15447, -29622, -14010,
	-30274, -12540, -30853, -11039, -31357, -9512, -31786, -7962,
	-32138, -6393, -32413, -4808, -32610, -3212, -32729, -1608

};

const static Word16 ii_table[] =
{SIZE / 2, SIZE / 4, SIZE / 8, SIZE / 16, SIZE / 32, SIZE / 64};

/* FFT function for complex sequences */

/*
 * The decimation-in-time complex FFT is implemented below.
 * The input complex numbers are presented as real part followed by
 * imaginary part for each sample.  The counters are therefore
 * incremented by two to access the complex valued samples.
 */

void c_fft(Word16 * farray_ptr)
{

	Word16 i, j, k, ii, jj, kk, ji, kj, ii2;
	Word32 ftmp, ftmp_real, ftmp_imag;
	Word16 tmp, tmp1, tmp2;

	/* Rearrange the input array in bit reversed order */
	for (i = 0, j = 0; i < SIZE - 2; i = i + 2)
	{										test();
		if (sub(j, i) > 0)
		{
			ftmp = *(farray_ptr + i);					move16();
			*(farray_ptr + i) = *(farray_ptr + j);				move16();
			*(farray_ptr + j) = ftmp;					move16();

			ftmp = *(farray_ptr + i + 1);					move16();
			*(farray_ptr + i + 1) = *(farray_ptr + j + 1);			move16();
			*(farray_ptr + j + 1) = ftmp;					move16();
		}

		k = SIZE_BY_TWO;							move16();
											test();
		while (sub(j, k) >= 0)
		{
			j = sub(j, k);
			k = shr(k, 1);
		}
		j = add(j, k);
	}

	/* The FFT part */
	for (i = 0; i < NUM_STAGE; i++)
	{				/* i is stage counter */
		jj = shl(2, i);		/* FFT size */
		kk = shl(jj, 1);	/* 2 * FFT size */
		ii = ii_table[i];	/* 2 * number of FFT's */			move16();
		ii2 = shl(ii, 1);
		ji = 0;			/* ji is phase table index */			move16();

		for (j = 0; j < jj; j = j + 2)
		{					/* j is sample counter */

			for (k = j; k < SIZE; k = k + kk)
			{				/* k is butterfly top */
				kj = add(k, jj);	/* kj is butterfly bottom */

				/* Butterfly computations */
				ftmp_real = L_mult(*(farray_ptr + kj), phs_tbl[ji]);
				ftmp_real = L_msu(ftmp_real, *(farray_ptr + kj + 1), phs_tbl[ji + 1]);

				ftmp_imag = L_mult(*(farray_ptr + kj + 1), phs_tbl[ji]);
				ftmp_imag = L_mac(ftmp_imag, *(farray_ptr + kj), phs_tbl[ji + 1]);

				tmp1 = round(ftmp_real);
				tmp2 = round(ftmp_imag);

				tmp = sub(*(farray_ptr + k), tmp1);
				*(farray_ptr + kj) = shr(tmp, 1);			move16();

				tmp = sub(*(farray_ptr + k + 1), tmp2);
				*(farray_ptr + kj + 1) = shr(tmp, 1);			move16();

				tmp = add(*(farray_ptr + k), tmp1);
				*(farray_ptr + k) = shr(tmp, 1);			move16();

				tmp = add(*(farray_ptr + k + 1), tmp2);
				*(farray_ptr + k + 1) = shr(tmp, 1);			move16();
			}

			ji =  add(ji, ii2);
		}
	}
}								/* end of c_fft () */



void r_fft(Word16 * farray_ptr)
{

	Word16 ftmp1_real, ftmp1_imag, ftmp2_real, ftmp2_imag;
	Word32 Lftmp1_real, Lftmp1_imag;
	Word16 i, j;
	Word32 Ltmp1;

	/* Perform the complex FFT */
	c_fft(farray_ptr);

	/* First, handle the DC and foldover frequencies */
	ftmp1_real = *farray_ptr;							move16();
	ftmp2_real = *(farray_ptr + 1);							move16();
	*farray_ptr = add(ftmp1_real, ftmp2_real);					move16();
	*(farray_ptr + 1) = sub(ftmp1_real, ftmp2_real);				move16();

	/* Now, handle the remaining positive frequencies */
	for (i = 2, j = SIZE - i; i <= SIZE_BY_TWO; i = i + 2, j = SIZE - i)
	{
		ftmp1_real = add(*(farray_ptr + i), *(farray_ptr + j));
		ftmp1_imag = sub(*(farray_ptr + i + 1), *(farray_ptr + j + 1));
		ftmp2_real = add(*(farray_ptr + i + 1), *(farray_ptr + j + 1));
		ftmp2_imag = sub(*(farray_ptr + j), *(farray_ptr + i));

		Lftmp1_real = L_deposit_h(ftmp1_real);
		Lftmp1_imag = L_deposit_h(ftmp1_imag);

		Ltmp1 = L_mac(Lftmp1_real, ftmp2_real, phs_tbl[i]);
		Ltmp1 = L_msu(Ltmp1, ftmp2_imag, phs_tbl[i + 1]);
		*(farray_ptr + i) = round(L_shr(Ltmp1, 1));				move16();

		Ltmp1 = L_mac(Lftmp1_imag, ftmp2_imag, phs_tbl[i]);
		Ltmp1 = L_mac(Ltmp1, ftmp2_real, phs_tbl[i + 1]);
		*(farray_ptr + i + 1) = round(L_shr(Ltmp1, 1));				move16();

		Ltmp1 = L_mac(Lftmp1_real, ftmp2_real, phs_tbl[j]);
		Ltmp1 = L_mac(Ltmp1, ftmp2_imag, phs_tbl[j + 1]);
		*(farray_ptr + j) = round(L_shr(Ltmp1, 1));				move16();

		Ltmp1 = L_negate(Lftmp1_imag);
		Ltmp1 = L_msu(Ltmp1, ftmp2_imag, phs_tbl[j]);
		Ltmp1 = L_mac(Ltmp1, ftmp2_real, phs_tbl[j + 1]);
		*(farray_ptr + j + 1) = round(L_shr(Ltmp1, 1));				move16();

	}
}								/* end r_fft () */
