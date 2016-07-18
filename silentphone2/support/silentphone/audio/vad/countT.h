/*
 * functions for counting operations
 *
 * These functions, and the ones in basic_op.h, makes it possible to measure
 * the wMOPS of a codec.
 *
 * All functions in this file, and in basic_op.h, uppdates a structure so that
 * it will be possible the see how many calls to add, mul mulAdd ... that the
 * code made, and estimate the wMOPS (and MIPS) for a sertain part of code
 *
 * It is also possible to measure the wMOPS separatly for different parts
 * of the codec.
 *
 * This is done by creating a counter group (getCounterId) for each part of the
 * code that one wants a separte measure for. Before a part of the code
 * is executed a call to the "setCounter" function is needed to identify
 * which counter group to use.
 *
 * Currently there is a limit of 255 different counter groups.
 *
 * In the end of this file there is a pice of code illustration how the
 * functions can be used.
 */
#ifndef count_h
#define count_h "$Id $"


static void move16 (void){}
static void move32 (void){}
static void logic16 (void){}
static void logic32 (void){}
static void test (void){}

#endif
