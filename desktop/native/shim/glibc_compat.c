/*
 * Linux: glibc 2.38's headers turn sscanf into __isoc23_sscanf (C23's "0b" numbers), which only glibc 2.38+ has -
 * the one symbol that kept libpokedaisy.so off older systems (Batocera builds before 2024). Our own, hidden, so the
 * library binds to it and needs glibc 2.34 at most. Built as plain C11 (no _GNU_SOURCE): vsscanf here is the old one.
 */
#include <stdarg.h>
#include <stdio.h>

__attribute__((visibility("hidden"))) int __isoc23_sscanf(const char* s, const char* format, ...) {
    va_list ap;
    va_start(ap, format);
    int n = vsscanf(s, format, ap);
    va_end(ap);
    return n;
}

#include <stdlib.h>
#include <inttypes.h>

/* The same for the strto* family: identical but for "0b" prefixes, which nothing here parses. */
__attribute__((visibility("hidden"))) long __isoc23_strtol(const char* s, char** end, int base) { return strtol(s, end, base); }
__attribute__((visibility("hidden"))) unsigned long __isoc23_strtoul(const char* s, char** end, int base) { return strtoul(s, end, base); }
__attribute__((visibility("hidden"))) long long __isoc23_strtoll(const char* s, char** end, int base) { return strtoll(s, end, base); }
__attribute__((visibility("hidden"))) unsigned long long __isoc23_strtoull(const char* s, char** end, int base) { return strtoull(s, end, base); }
__attribute__((visibility("hidden"))) intmax_t __isoc23_strtoimax(const char* s, char** end, int base) { return strtoimax(s, end, base); }
__attribute__((visibility("hidden"))) uintmax_t __isoc23_strtoumax(const char* s, char** end, int base) { return strtoumax(s, end, base); }
__attribute__((visibility("hidden"))) int __isoc23_vsscanf(const char* s, const char* format, va_list ap) { return vsscanf(s, format, ap); }

#include <string.h>

/* strlcpy: in glibc only since 2.38 (mGBA uses the libc one when the build machine has it). */
__attribute__((visibility("hidden"))) size_t strlcpy(char* dst, const char* src, size_t size) {
    size_t n = strlen(src);
    if (size) {
        size_t c = n < size - 1 ? n : size - 1;
        memcpy(dst, src, c);
        dst[c] = '\0';
    }
    return n;
}

/* fmod: glibc 2.38 gave it a new symbol version (same function); bind ours to the one every glibc has. */
#if defined(__x86_64__)
double pk_fmod_old(double, double);
__asm__(".symver pk_fmod_old,fmod@GLIBC_2.2.5");
__attribute__((visibility("hidden"))) double fmod(double x, double y) { return pk_fmod_old(x, y); }
#elif defined(__aarch64__)
double pk_fmod_old(double, double);
__asm__(".symver pk_fmod_old,fmod@GLIBC_2.17");
__attribute__((visibility("hidden"))) double fmod(double x, double y) { return pk_fmod_old(x, y); }
#endif
