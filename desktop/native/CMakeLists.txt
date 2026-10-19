# PokeDaisy desktop: the app's own native bridge (pokedaisy_jni.c & co., unchanged) built as a
# desktop shared library, against the JDK's JNI headers and a stand-in for <android/log.h>.
cmake_minimum_required(VERSION 3.22.1)
project(pokedaisy_desktop C)

find_package(JNI REQUIRED)

get_filename_component(APP_CPP "${CMAKE_CURRENT_SOURCE_DIR}/../../app/src/main/cpp" ABSOLUTE)
get_filename_component(MGBA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../third_party/mgba" ABSOLUTE)
get_filename_component(RCHEEVOS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../third_party/rcheevos" ABSOLUTE)

# The same mGBA configuration as the Android build (app/src/main/cpp/CMakeLists.txt).
set(BUILD_STATIC ON CACHE BOOL "" FORCE)
set(BUILD_SHARED OFF CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
foreach(opt DISABLE_FRONTENDS DISABLE_DEPS M_CORE_GBA M_CORE_GB SKIP_INSTALL_ALL)
    set(${opt} ON CACHE BOOL "" FORCE)
endforeach()
foreach(opt SKIP_LIBRARY BUILD_GL BUILD_GLES2 BUILD_GLES3 BUILD_PERF BUILD_TEST BUILD_SUITE BUILD_LIBRETRO
        BUILD_PYTHON USE_DEBUGGERS USE_GDB_STUB USE_EDITLINE USE_FFMPEG USE_DISCORD_RPC USE_LZMA USE_LUA
        USE_ELF USE_SQLITE3 USE_LIBZIP USE_MINIZIP USE_PNG USE_ZLIB BUILD_LTO)
    set(${opt} OFF CACHE BOOL "" FORCE)
endforeach()
add_subdirectory("${MGBA_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/mgba" EXCLUDE_FROM_ALL)

file(GLOB RCHEEVOS_SOURCES
    "${RCHEEVOS_DIR}/src/*.c" "${RCHEEVOS_DIR}/src/rapi/*.c" "${RCHEEVOS_DIR}/src/rcheevos/*.c"
    "${RCHEEVOS_DIR}/src/rhash/hash.c" "${RCHEEVOS_DIR}/src/rhash/hash_rom.c" "${RCHEEVOS_DIR}/src/rhash/md5.c")
list(FILTER RCHEEVOS_SOURCES EXCLUDE REGEX "rc_libretro\\.c$|rc_client_external\\.c$|rc_client_raintegration\\.c$")
add_library(rcheevos STATIC ${RCHEEVOS_SOURCES})
target_include_directories(rcheevos PUBLIC "${RCHEEVOS_DIR}/include")
target_compile_definitions(rcheevos PUBLIC RC_CLIENT_SUPPORTS_HASH RC_HASH_NO_DISC RC_HASH_NO_ENCRYPTED RC_HASH_NO_ZIP)

add_library(pokedaisy SHARED
    "${APP_CPP}/pokedaisy_jni.c" "${APP_CPP}/pokedaisy_ra.c" "${APP_CPP}/pk_cheats.c"
    "${APP_CPP}/pk_rewind.c" "${APP_CPP}/pk_wide.c")
target_compile_definitions(pokedaisy PRIVATE USE_PTHREADS M_CORE_GBA M_CORE_GB)
target_include_directories(pokedaisy PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/shim" ${JNI_INCLUDE_DIRS}
    "${MGBA_DIR}/include" "${MGBA_DIR}/src" "${CMAKE_CURRENT_BINARY_DIR}/mgba/include")
target_link_libraries(pokedaisy mgba rcheevos m pthread)
