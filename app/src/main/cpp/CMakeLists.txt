cmake_minimum_required(VERSION 3.22)
project(luajni C)

set(LUA_SOURCES
    lua/lapi.c
    lua/lauxlib.c
    lua/lbaselib.c
    lua/lbitlib.c
    lua/lcode.c
    lua/lcorolib.c
    lua/lctype.c
    lua/ldblib.c
    lua/ldebug.c
    lua/ldo.c
    lua/ldump.c
    lua/lfunc.c
    lua/lgc.c
    lua/linit.c
    lua/liolib.c
    lua/llex.c
    lua/lmathlib.c
    lua/lmem.c
    lua/loadlib.c
    lua/lobject.c
    lua/lopcodes.c
    lua/loslib.c
    lua/lparser.c
    lua/lstate.c
    lua/lstring.c
    lua/lstrlib.c
    lua/ltable.c
    lua/ltablib.c
    lua/ltm.c
    lua/lundump.c
    lua/lutf8lib.c
    lua/lvm.c
    lua/lzio.c
)

file(GLOB_RECURSE ZSTD_SOURCES zstd/*.c)

add_library(luajni SHARED luajni.c zstdjni.c ${LUA_SOURCES} ${ZSTD_SOURCES})

target_include_directories(luajni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/lua)
target_include_directories(luajni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/zstd)
target_include_directories(luajni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/zstd/common)
target_compile_options(luajni PRIVATE -O2 -DLUA_USE_POSIX)
target_compile_options(luajni PRIVATE -Wno-unused-parameter -Wno-sign-compare)
