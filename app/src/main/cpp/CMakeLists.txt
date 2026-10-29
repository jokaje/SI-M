cmake_minimum_required(VERSION 3.22.1)
project(sim_avatar LANGUAGES C)
set(AVATAR_SHARED "${CMAKE_CURRENT_LIST_DIR}/../../../../shared")
add_library(sim_avatar SHARED avatar_jni.c
    "${AVATAR_SHARED}/orb_avatar_dna.c"
    "${AVATAR_SHARED}/orb_avatar_engine.c")
target_include_directories(sim_avatar PRIVATE "${AVATAR_SHARED}")
target_compile_features(sim_avatar PRIVATE c_std_11)
target_compile_options(sim_avatar PRIVATE -Wall -Wextra -Werror)
target_link_libraries(sim_avatar m)
