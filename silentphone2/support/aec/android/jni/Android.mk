LOCAL_PATH := $(call my-dir)
 
include $(CLEAR_VARS)
 
LOCAL_MODULE    := libaec
LOCAL_CFLAGS = -DFIXED_POINT -DUSE_KISS_FFT -DEXPORT="" -UHAVE_CONFIG_H
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
 LOCAL_LDLIBS := -llog

LOCAL_SRC_FILES = ../../src/aec.cpp

#LOCAL_SRC_FILES +=  \
./libspeex/bits.c \
./libspeex/buffer.c \
./libspeex/cb_search.c \
./libspeex/exc_10_16_table.c \
./libspeex/exc_10_32_table.c \
./libspeex/exc_20_32_table.c \
./libspeex/exc_5_256_table.c \
./libspeex/exc_5_64_table.c \
./libspeex/exc_8_128_table.c \
./libspeex/fftwrap.c \
./libspeex/filterbank.c \
./libspeex/filters.c \
./libspeex/gain_table.c \
./libspeex/gain_table_lbr.c \
./libspeex/hexc_10_32_table.c \
./libspeex/hexc_table.c \
./libspeex/high_lsp_tables.c \
./libspeex/jitter.c \
./libspeex/kiss_fft.c \
./libspeex/kiss_fftr.c \
./libspeex/lpc.c \
./libspeex/lsp.c \
./libspeex/lsp_tables_nb.c \
./libspeex/ltp.c \
./libspeex/mdf.c \
./libspeex/modes.c \
./libspeex/modes_wb.c \
./libspeex/nb_celp.c \
./libspeex/preprocess.c \
./libspeex/quant_lsp.c \
./libspeex/resample.c \
./libspeex/sb_celp.c \
./libspeex/scal.c \
./libspeex/smallft.c \
./libspeex/speex.c \
./libspeex/speex_callbacks.c \
./libspeex/speex_header.c \
./libspeex/stereo.c \
./libspeex/vbr.c \
./libspeex/vq.c \
./libspeex/window.c

# ls -1 aec/*.c aec/*.cpp | awk '{ printf "./"$1 " \\\n" }'

TPM_SRC := ../../src/webrtc_ec/

LOCAL_SRC_FILES +=\
$(TPM_SRC)aec_core.c \
$(TPM_SRC)aec_rdft.c \
$(TPM_SRC)aec_resampler.c \
$(TPM_SRC)aecm_core.c \
$(TPM_SRC)audio_buffer.cpp\
$(TPM_SRC)audio_processing_impl.cpp\
$(TPM_SRC)auto_corr_to_refl_coef.c \
$(TPM_SRC)auto_correlation.c \
$(TPM_SRC)complex_bit_reverse.c \
$(TPM_SRC)complex_fft.c \
$(TPM_SRC)condition_variable.cpp\
$(TPM_SRC)condition_variable_posix.cpp\
$(TPM_SRC)copy_set_operations.c \
$(TPM_SRC)critical_section.cpp\
$(TPM_SRC)critical_section_posix.cpp\
$(TPM_SRC)cross_correlation.c \
$(TPM_SRC)delay_estimator.c \
$(TPM_SRC)delay_estimator_wrapper.c \
$(TPM_SRC)division_operations.c \
$(TPM_SRC)dot_product_with_scale.c \
$(TPM_SRC)downsample_fast.c \
$(TPM_SRC)echo_cancellation.c \
$(TPM_SRC)echo_cancellation_impl.cpp\
$(TPM_SRC)echo_control_mobile.c \
$(TPM_SRC)echo_control_mobile_impl.cpp\
$(TPM_SRC)energy.c \
$(TPM_SRC)filter_ar.c \
$(TPM_SRC)filter_ar_fast_q12.c \
$(TPM_SRC)filter_ma_fast_q12.c \
$(TPM_SRC)get_hanning_window.c \
$(TPM_SRC)get_scaling_square.c \
$(TPM_SRC)ilbc_specific_functions.c \
$(TPM_SRC)levinson_durbin.c \
$(TPM_SRC)lpc_to_refl_coef.c \
$(TPM_SRC)min_max_operations.c \
$(TPM_SRC)processing_component.cpp\
$(TPM_SRC)randomization_functions.c \
$(TPM_SRC)real_fft.c \
$(TPM_SRC)refl_coef_to_lpc.c \
$(TPM_SRC)resample.c \
$(TPM_SRC)resample_48khz.c \
$(TPM_SRC)resample_by_2.c \
$(TPM_SRC)resample_by_2_internal.c \
$(TPM_SRC)resample_fractional.c \
$(TPM_SRC)resampler.cpp\
$(TPM_SRC)ring_buffer.c \
$(TPM_SRC)spl_init.c \
$(TPM_SRC)spl_sqrt.c \
$(TPM_SRC)spl_sqrt_floor.c \
$(TPM_SRC)spl_version.c \
$(TPM_SRC)splitting_filter.c \
$(TPM_SRC)sqrt_of_one_minus_x_squared.c \
$(TPM_SRC)vector_scaling_operations.c \
$(TPM_SRC)webrtc_fft_t_1024_8.c \
$(TPM_SRC)webrtc_fft_t_rad.c 
 
include $(BUILD_SHARED_LIBRARY)
