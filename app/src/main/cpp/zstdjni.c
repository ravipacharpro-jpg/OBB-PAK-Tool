#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <zstd.h>

#define MAX_OUT (768ULL * 1024 * 1024)

static jbyteArray emit(JNIEnv *env, void *buf, size_t size) {
    jbyteArray out = (*env)->NewByteArray(env, (jsize) size);
    if (out) (*env)->SetByteArrayRegion(env, out, 0, (jsize) size, (jbyte *) buf);
    free(buf);
    return out;
}

static void fail(JNIEnv *env, const char *msg) {
    jclass cls = (*env)->FindClass(env, "java/lang/IOException");
    if (!(*env)->ExceptionCheck(env)) (*env)->ThrowNew(env, cls, msg);
}

JNIEXPORT jbyteArray JNICALL
Java_com_obbpak_tool_Zstd_decompress(JNIEnv *env, jclass cls, jbyteArray jsrc) {
    (void) cls;
    jsize inSize = (*env)->GetArrayLength(env, jsrc);
    jbyte *in = (*env)->GetByteArrayElements(env, jsrc, NULL);
    unsigned long long outCap = ZSTD_getFrameContentSize(in, (size_t) inSize);
    if (outCap == ZSTD_CONTENTSIZE_ERROR || outCap == ZSTD_CONTENTSIZE_UNKNOWN) outCap = 1 << 20;
    if (outCap > MAX_OUT) { (*env)->ReleaseByteArrayElements(env, jsrc, in, JNI_ABORT); fail(env, "zstd output too large"); return NULL; }
    void *out = malloc(outCap ? outCap : 1);
    if (!out) { (*env)->ReleaseByteArrayElements(env, jsrc, in, JNI_ABORT); fail(env, "oom"); return NULL; }
    size_t r = ZSTD_decompress(out, outCap, in, (size_t) inSize);
    (*env)->ReleaseByteArrayElements(env, jsrc, in, JNI_ABORT);
    if (ZSTD_isError(r)) { free(out); fail(env, ZSTD_getErrorName(r)); return NULL; }
    return emit(env, out, r);
}

JNIEXPORT jbyteArray JNICALL
Java_com_obbpak_tool_Zstd_decompressWithDict(JNIEnv *env, jclass cls, jbyteArray jsrc, jbyteArray jdict) {
    (void) cls;
    jsize inSize = (*env)->GetArrayLength(env, jsrc);
    jsize dictSize = (*env)->GetArrayLength(env, jdict);
    jbyte *in = (*env)->GetByteArrayElements(env, jsrc, NULL);
    jbyte *dict = (*env)->GetByteArrayElements(env, jdict, NULL);
    ZSTD_DCtx *dctx = ZSTD_createDCtx();
    unsigned long long cap = 1 << 20;
    void *out = malloc(cap);
    size_t r = ZSTD_decompress_usingDict(dctx, out, cap, in, (size_t) inSize, dict, (size_t) dictSize);
    if (ZSTD_isError(r) && r > cap) {
        free(out);
        cap = r;
        out = malloc(cap);
        r = ZSTD_decompress_usingDict(dctx, out, cap, in, (size_t) inSize, dict, (size_t) dictSize);
    }
    (*env)->ReleaseByteArrayElements(env, jsrc, in, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, jdict, dict, JNI_ABORT);
    ZSTD_freeDCtx(dctx);
    if (ZSTD_isError(r)) { free(out); fail(env, ZSTD_getErrorName(r)); return NULL; }
    return emit(env, out, r);
}

JNIEXPORT jbyteArray JNICALL
Java_com_obbpak_tool_Zstd_compress(JNIEnv *env, jclass cls, jbyteArray jsrc, jint level) {
    (void) cls;
    jsize inSize = (*env)->GetArrayLength(env, jsrc);
    jbyte *in = (*env)->GetByteArrayElements(env, jsrc, NULL);
    size_t cap = ZSTD_compressBound((size_t) inSize);
    void *out = malloc(cap);
    if (!out) { (*env)->ReleaseByteArrayElements(env, jsrc, in, JNI_ABORT); fail(env, "oom"); return NULL; }
    int lvl = level > 0 ? level : 3;
    if (lvl > 22) lvl = 22;
    size_t r = ZSTD_compress(out, cap, in, (size_t) inSize, lvl);
    (*env)->ReleaseByteArrayElements(env, jsrc, in, JNI_ABORT);
    if (ZSTD_isError(r)) { free(out); fail(env, ZSTD_getErrorName(r)); return NULL; }
    return emit(env, out, r);
}

JNIEXPORT jbyteArray JNICALL
Java_com_obbpak_tool_Zstd_compressWithDict(JNIEnv *env, jclass cls, jbyteArray jsrc, jint level, jbyteArray jdict) {
    (void) cls;
    jsize inSize = (*env)->GetArrayLength(env, jsrc);
    jsize dictSize = (*env)->GetArrayLength(env, jdict);
    jbyte *in = (*env)->GetByteArrayElements(env, jsrc, NULL);
    jbyte *dict = (*env)->GetByteArrayElements(env, jdict, NULL);
    ZSTD_CCtx *cctx = ZSTD_createCCtx();
    ZSTD_CCtx_setParameter(cctx, ZSTD_c_compressionLevel, level > 0 ? level : 3);
    size_t cap = ZSTD_compressBound((size_t) inSize);
    void *out = malloc(cap);
    if (!out) {
        (*env)->ReleaseByteArrayElements(env, jsrc, in, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, jdict, dict, JNI_ABORT);
        ZSTD_freeCCtx(cctx);
        fail(env, "oom");
        return NULL;
    }
    size_t r = ZSTD_compress2(cctx, out, cap, in, (size_t) inSize);
    if (!ZSTD_isError(r) && dictSize > 0) {
        r = ZSTD_CCtx_refPrefix(cctx, dict, (size_t) dictSize);
        if (!ZSTD_isError(r)) r = ZSTD_compress2(cctx, out, cap, in, (size_t) inSize);
    }
    (*env)->ReleaseByteArrayElements(env, jsrc, in, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, jdict, dict, JNI_ABORT);
    ZSTD_freeCCtx(cctx);
    if (ZSTD_isError(r)) { free(out); fail(env, ZSTD_getErrorName(r)); return NULL; }
    return emit(env, out, r);
}
