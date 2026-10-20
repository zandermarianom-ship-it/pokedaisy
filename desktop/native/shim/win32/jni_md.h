/* PokeDaisy desktop: the JDK's Windows jni_md.h, for cross-compiling from Linux (the JDK here is Linux's,
 * whose jni_md.h has ELF visibility and a 64-bit long). Same definitions as OpenJDK's win32 jni_md.h. */
#ifndef _JAVASOFT_JNI_MD_H_
#define _JAVASOFT_JNI_MD_H_

#define JNIEXPORT __declspec(dllexport)
#define JNIIMPORT __declspec(dllimport)
#define JNICALL __stdcall

/* 'long' is always 32 bit on Windows, so this matches what jdk expects. */
typedef long jint;
typedef __int64 jlong;
typedef signed char jbyte;

#endif
