#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include "lua.h"
#include "lauxlib.h"
#include "lualib.h"

typedef struct {
    unsigned char *buf;
    size_t size;
    size_t cap;
    int err;
} dump_buffer;

static int writer(lua_State *L, const void *p, size_t sz, void *ud) {
    dump_buffer *b = (dump_buffer *) ud;
    (void) L;
    if (sz == 0) return 0;
    if (b->size + sz > b->cap) {
        size_t ncap = b->cap ? b->cap : 4096;
        while (ncap < b->size + sz) ncap *= 2;
        unsigned char *nb = (unsigned char *) realloc(b->buf, ncap);
        if (!nb) { b->err = 1; return 1; }
        b->buf = nb;
        b->cap = ncap;
    }
    memcpy(b->buf + b->size, p, sz);
    b->size += sz;
    return 0;
}

static void throwLuaError(JNIEnv *env, const char *msg) {
    jclass cls = (*env)->FindClass(env, "java/lang/RuntimeException");
    if ((*env)->ExceptionCheck(env)) return;
    (*env)->ThrowNew(env, cls, msg ? msg : "lua compile error");
}

JNIEXPORT jbyteArray JNICALL
Java_com_obbpak_tool_LuaCompiler_compile(JNIEnv *env, jclass cls,
                                         jbyteArray jsrc, jstring jchunk,
                                         jboolean strip) {
    (void) cls;
    jsize srclen = (*env)->GetArrayLength(env, jsrc);
    jbyte *src = (*env)->GetByteArrayElements(env, jsrc, NULL);
    if (!src) { throwLuaError(env, "cannot read source"); return NULL; }

    const char *chunk = "@chunk";
    if (jchunk) {
        const char *c = (*env)->GetStringUTFChars(env, jchunk, NULL);
        if (c) chunk = c;
    }

    lua_State *L = luaL_newstate();
    if (!L) {
        (*env)->ReleaseByteArrayElements(env, jsrc, src, JNI_ABORT);
        throwLuaError(env, "cannot create lua state");
        return NULL;
    }

    int status = luaL_loadbufferx(L, (const char *) src, (size_t) srclen, chunk, NULL);

    (*env)->ReleaseByteArrayElements(env, jsrc, src, JNI_ABORT);
    if (jchunk) (*env)->ReleaseStringUTFChars(env, jchunk, chunk);

    if (status != LUA_OK) {
        const char *msg = lua_tostring(L, -1);
        char buf[512];
        snprintf(buf, sizeof(buf), "%s", msg ? msg : "syntax error");
        lua_close(L);
        throwLuaError(env, buf);
        return NULL;
    }

    dump_buffer b;
    memset(&b, 0, sizeof(b));
    status = lua_dump(L, writer, &b, strip ? 1 : 0);
    lua_close(L);

    if (status != 0 || b.err || !b.buf) {
        free(b.buf);
        throwLuaError(env, status != 0 ? "lua_dump failed" : "out of memory");
        return NULL;
    }

    jbyteArray out = (*env)->NewByteArray(env, (jsize) b.size);
    if (!out) { free(b.buf); return NULL; }
    (*env)->SetByteArrayRegion(env, out, 0, (jsize) b.size, (jbyte *) b.buf);
    free(b.buf);
    return out;
}
