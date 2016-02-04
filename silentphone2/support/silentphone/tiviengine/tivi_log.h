#ifndef TIVI_LOG_H
#define TIVI_LOG_H

void *getEncryptedPtr_debug(void *p);//if we want to log pointers we have to encrypt them using this fnc

void t_logf(void (*log_fnc)(const char *tag, const char *buf), const char *tag, const char *format, ...);

void log_zrtp(const char *tag, const char *buf);
void log_audio(const char *tag, const char *buf);
void log_sip(const char *tag, const char *buf);
void log_audio_stats(const char *tag, const char *buf);
void log_events(const char *tag, const char *buf);

void log_call_marker(int iStarts, const char *cid, int cid_len);

#endif
