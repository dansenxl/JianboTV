cmake_minimum_required(VERSION 3.22.1)

project("libmpv")

add_library(
        player
        SHARED
        main.cpp render.cpp log.cpp jni_utils.cpp property.cpp event.cpp
)

add_library(
        mpv
        SHARED
        IMPORTED
)

set_target_properties(
        mpv
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libmpv.so
)

foreach(ffmpeg_lib avcodec swresample avutil)
    add_library(
            ${ffmpeg_lib}
            SHARED
            IMPORTED
    )
    set_target_properties(
            ${ffmpeg_lib}
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_CURRENT_SOURCE_DIR}/../../../../buildscripts/prefix/${ANDROID_ABI}/lib/lib${ffmpeg_lib}.so
    )
endforeach()

include_directories( ${CMAKE_CURRENT_SOURCE_DIR}/../../../../buildscripts/prefix/${ANDROID_ABI}/include )

target_link_libraries( player mpv avcodec log )

# ExoPlayer's FFmpeg audio decoder (ffmpeg_jni.cc), on the same shared FFmpeg as mpv. Its Java half
# is in OwnTV_Core; tools/inspect_aar.py checks this library and what it links.
add_library(
        owntvffmpeg
        SHARED
        ffmpeg_jni.cc
)

set_target_properties(
        owntvffmpeg
        PROPERTIES CXX_VISIBILITY_PRESET hidden VISIBILITY_INLINES_HIDDEN ON
)

target_link_libraries( owntvffmpeg avcodec swresample avutil log )
