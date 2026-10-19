// PokeDaisy desktop: Android's logging header, sent to stderr.
#pragma once
#include <stdarg.h>
#include <stdio.h>

enum { ANDROID_LOG_VERBOSE = 2, ANDROID_LOG_DEBUG, ANDROID_LOG_INFO, ANDROID_LOG_WARN, ANDROID_LOG_ERROR };

static inline int __android_log_print(int prio, const char* tag, const char* fmt, ...) {
    static const char* const names = "??VDIWEF";
    va_list ap;
    va_start(ap, fmt);
    fprintf(stderr, "%c/%s: ", (prio >= 0 && prio < 8) ? names[prio] : '?', tag);
    int n = vfprintf(stderr, fmt, ap);
    fputc('\n', stderr);
    va_end(ap);
    return n;
}
