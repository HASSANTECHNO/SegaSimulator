/*
 * native-lib.c — JNI bridge between the Kotlin frontend and the
 * Genesis Plus GX libretro core (vendored under core-src/).
 *
 * The core is loaded "inline" (no dlopen): its symbols are linked
 * directly into libsegacore.so, exactly like a statically-linked
 * libretro frontend. The frontend implements the libretro callbacks
 * (environment / video refresh / audio sample / input state) here.
 *
 * Video: core renders RGB565 into our buffer -> JNI copies to Kotlin.
 * Audio: core pushes stereo s16 @ 44100 Hz (SOUND_FREQUENCY) -> ring buffer.
 * Input: bitfield built on the Kotlin side via setInput().
 *
 * Genesis Plus GX is (c) Eke-Eke / Charles MacDonald, non-commercial
 * license. See core-src/LICENSE.txt and NOTICE.md in the project root.
 */
#include <jni.h>
#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>

#include <libretro.h>

/* ------------------------------------------------------------------ */
/* Core entry points (linked statically)                               */
/* ------------------------------------------------------------------ */
extern void retro_set_environment(retro_environment_t cb);
extern void retro_set_video_refresh(retro_video_refresh_t cb);
extern void retro_set_audio_sample(retro_audio_sample_t cb);
extern void retro_set_audio_sample_batch(retro_audio_sample_batch_t cb);
extern void retro_set_input_poll(retro_input_poll_t cb);
extern void retro_set_input_state(retro_input_state_t cb);
extern void retro_init(void);
extern void retro_deinit(void);
extern unsigned retro_api_version(void);
extern void retro_get_system_info(struct retro_system_info *info);
extern void retro_get_system_av_info(struct retro_system_av_info *info);
extern void retro_set_controller_port_device(unsigned port, unsigned device);
extern void retro_reset(void);
extern void retro_run(void);
extern size_t retro_serialize_size(void);
extern bool retro_serialize(void *data, size_t size);
extern bool retro_unserialize(const void *data, size_t size);
extern bool retro_load_game(const struct retro_game_info *game);
extern void retro_unload_game(void);
extern unsigned retro_get_region(void);

/* ------------------------------------------------------------------ */
/* Frame buffers                                                       */
/* ------------------------------------------------------------------ */
#define MAX_WIDTH  720
#define MAX_HEIGHT 576
#define AUDIO_RING_FRAMES 8192   /* stereo frames (2 samples each)   */

static uint16_t video_buf[MAX_WIDTH * MAX_HEIGHT];
static unsigned last_width = 0, last_height = 0;
static bool pix_fmt_rgb565_ok = false;

/* audio ring buffer (interleaved s16 stereo, written by core thread) */
static int16_t audio_ring[AUDIO_RING_FRAMES * 2];
static volatile size_t audio_rd = 0, audio_wr = 0;
static pthread_mutex_t audio_lock = PTHREAD_MUTEX_INITIALIZER;

/* input bitfields: joypad_bits[0] = pad 1, joypad_bits[1] = pad 2
 * (two-player HotSeat shares one device; each pad has its own bits) */
static volatile unsigned joypad_bits[2] = {0, 0};

/* environment state */
static bool perf_support = false;
static char system_dir[512] = "./";
static char save_dir[512]   = "./";

/* ------------------------------------------------------------------ */
/* libretro callbacks                                                  */
/* ------------------------------------------------------------------ */
static bool env_cb(unsigned cmd, void *data)
{
   switch (cmd)
   {
      case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
         pix_fmt_rgb565_ok = (*(enum retro_pixel_format *)data
                              == RETRO_PIXEL_FORMAT_RGB565);
         return pix_fmt_rgb565_ok;

      case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
         *(const char **)data = system_dir;
         return true;

      case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
         *(const char **)data = save_dir;
         return true;

      case RETRO_ENVIRONMENT_GET_CAN_DUPE:
         *(bool *)data = true;
         return true;

      case RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL:
         perf_support = true;
         return true;

      case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
         /* silence core logging on device */
         return false;

      case RETRO_ENVIRONMENT_GET_VARIABLE:
      {
         struct retro_variable *var = (struct retro_variable *)data;
         if (!var || !var->key)
            return false;
         /* default core behaviour: let the core use its own defaults */
         var->value = NULL;
         return false;
      }

      default:
         return false;
   }
}

static void video_refresh_cb(const void *data, unsigned width, unsigned height,
                             size_t pitch)
{
   if (!data || !width || !height)
      return;

   last_width           = width;
   last_height          = height;

   const uint16_t *src  = (const uint16_t *)data;
   size_t src_pitch     = pitch / sizeof(uint16_t);

   if (width > MAX_WIDTH || height > MAX_HEIGHT)
      return;

   /* row-wise copy, normalising the pitch */
   for (unsigned y = 0; y < height; y++)
      memcpy(video_buf + (size_t)y * width, src + (size_t)y * src_pitch,
             width * sizeof(uint16_t));
}

static void audio_sample_cb(int16_t left, int16_t right)
{
   pthread_mutex_lock(&audio_lock);
   audio_ring[(audio_wr % AUDIO_RING_FRAMES) * 2 + 0] = left;
   audio_ring[(audio_wr % AUDIO_RING_FRAMES) * 2 + 1] = right;
   audio_wr++;
   pthread_mutex_unlock(&audio_lock);
}

static size_t audio_sample_batch_cb(const int16_t *data, size_t frames)
{
   pthread_mutex_lock(&audio_lock);
   for (size_t f = 0; f < frames; f++)
   {
      audio_ring[(audio_wr % AUDIO_RING_FRAMES) * 2 + 0] = data[f * 2 + 0];
      audio_ring[(audio_wr % AUDIO_RING_FRAMES) * 2 + 1] = data[f * 2 + 1];
      audio_wr++;
   }
   pthread_mutex_unlock(&audio_lock);
   return frames;
}

static void input_poll_cb(void) { /* input comes from JNI bitfield */ }

/* The core reads standard RETRO_DEVICE_ID_JOYPAD_* ids (0..15).
 * Our Kotlin layer already encodes them 1:1 into joypad_bits[pad]. */
static int16_t input_state_cb(unsigned port, unsigned device,
                              unsigned index, unsigned id)
{
   (void)device; (void)index;
   if (port >= 2)
      return 0;
   if (id >= 16)
      return 0;
   return (joypad_bits[port] >> id) & 1;
}

/* ------------------------------------------------------------------ */
/* Init / load / run                                                   */
/* ------------------------------------------------------------------ */
static void ensure_started(void)
{
   static bool started = false;
   if (!started)
   {
      retro_set_environment(env_cb);
      retro_set_video_refresh(video_refresh_cb);
      retro_set_audio_sample(audio_sample_cb);
      retro_set_audio_sample_batch(audio_sample_batch_cb);
      retro_set_input_poll(input_poll_cb);
      retro_set_input_state(input_state_cb);
      retro_init();
      started = true;
   }
}

JNIEXPORT jint JNICALL
Java_ir_segasim_emu_EmulatorCore_nativeVersion(JNIEnv *env, jclass clazz)
{
   (void)env; (void)clazz;
   return (jint)retro_api_version();
}

JNIEXPORT jstring JNICALL
Java_ir_segasim_emu_EmulatorCore_nativeSystemName(JNIEnv *env, jclass clazz)
{
   (void)clazz;
   struct retro_system_info info;
   ensure_started();
   retro_get_system_info(&info);
   return (*env)->NewStringUTF(env, info.library_name ? info.library_name : "?");
}

JNIEXPORT jboolean JNICALL
Java_ir_segasim_emu_EmulatorCore_nativeLoadRom(JNIEnv *env, jclass clazz,
                                               jbyteArray rom, jstring dir)
{
   (void)clazz;
   jsize len = (*env)->GetArrayLength(env, rom);
   jbyte *buf = (*env)->GetByteArrayElements(env, rom, NULL);

   const char *d = (*env)->GetStringUTFChars(env, dir, NULL);
   snprintf(system_dir, sizeof(system_dir), "%s", d);
   snprintf(save_dir, sizeof(save_dir), "%s", d);

   /* the core reports need_fullpath=true, so the ROM must exist on disk */
   char rom_path[768];
   snprintf(rom_path, sizeof(rom_path), "%s/_rom.bin", d);
   FILE *f = fopen(rom_path, "wb");
   if (!f)
   {
      (*env)->ReleaseStringUTFChars(env, dir, d);
      (*env)->ReleaseByteArrayElements(env, rom, buf, JNI_ABORT);
      return JNI_FALSE;
   }
   fwrite(buf, 1, (size_t)len, f);
   fclose(f);

   (*env)->ReleaseStringUTFChars(env, dir, d);
   (*env)->ReleaseByteArrayElements(env, rom, buf, JNI_ABORT);

   ensure_started();

   struct retro_game_info game;
   memset(&game, 0, sizeof(game));
   game.path = rom_path;
   game.data = NULL;
   game.size = 0;

   bool ok = retro_load_game(&game);

   if (ok)
   {
      retro_set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
      retro_set_controller_port_device(1, RETRO_DEVICE_JOYPAD);
      audio_rd = audio_wr = 0;   /* drop stale samples between sessions */
   }
   return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_ir_segasim_emu_EmulatorCore_nativeRunFrame(JNIEnv *env, jclass clazz)
{
   (void)env; (void)clazz;
   retro_run();
}

JNIEXPORT jint JNICALL
Java_ir_segasim_emu_EmulatorCore_nativeGetWidth(JNIEnv *env, jclass clazz)
{
   (void)env; (void)clazz;
   return (jint)last_width;
}

JNIEXPORT jint JNICALL
Java_ir_segasim_emu_EmulatorCore_nativeGetHeight(JNIEnv *env, jclass clazz)
{
   (void)env; (void)clazz;
   return (jint)last_height;
}

/* Copies one full frame (RGB565) into the provided ShortBuffer. */
JNIEXPORT jint JNICALL
Java_ir_segasim_emu_EmulatorCore_nativeReadFrame(JNIEnv *env, jclass clazz,
                                                 jshortArray out, jint maxLen)
{
   (void)clazz;
   jint w = (jint)last_width, h = (jint)last_height;
   if (w <= 0 || h <= 0)
      return 0;
   if (w * h > maxLen)
      return 0;
   (*env)->SetShortArrayRegion(env, out, 0, w * h, (const jshort *)video_buf);
   return w * h;
}

JNIEXPORT jint JNICALL
Java_ir_segasim_emu_EmulatorCore_nativeGetFrameWidth(JNIEnv *env, jclass clazz)
{
   (void)env; (void)clazz;
   return (jint)last_width;
}

/* Reads up to maxFrames stereo frames from the audio ring. */
JNIEXPORT jint JNICALL
Java_ir_segasim_emu_EmulatorCore_nativeReadAudio(JNIEnv *env, jclass clazz,
                                                 jshortArray out, jint maxFrames)
{
   (void)clazz;
   size_t avail;

   pthread_mutex_lock(&audio_lock);
   avail = audio_wr - audio_rd;
   if (avail > (size_t)maxFrames)
   {
      /* overrun: drop everything stale, keep the newest maxFrames */
      audio_rd = audio_wr - (size_t)maxFrames;
      avail = (size_t)maxFrames;
   }
   if (avail > 0)
   {
      /* two-segment copy when the ring wraps */
      size_t first = AUDIO_RING_FRAMES - (audio_rd % AUDIO_RING_FRAMES);
      if (first > avail)
         first = avail;
      (*env)->SetShortArrayRegion(env, out, 0, (jsize)(first * 2),
            &audio_ring[(audio_rd % AUDIO_RING_FRAMES) * 2]);
      if (avail > first)
         (*env)->SetShortArrayRegion(env, out, (jsize)(first * 2),
               (jsize)((avail - first) * 2), &audio_ring[0]);
      audio_rd += avail;
   }
   pthread_mutex_unlock(&audio_lock);

   return (jint)avail;
}

JNIEXPORT void JNICALL
Java_ir_segasim_emu_EmulatorCore_nativeSetInput(JNIEnv *env, jclass clazz,
                                                jint pad, jint bits)
{
   (void)env; (void)clazz;
   if (pad >= 0 && pad < 2)
      joypad_bits[pad] = (unsigned)bits;
}

JNIEXPORT jint JNICALL
Java_ir_segasim_emu_EmulatorCore_nativeSerializeSize(JNIEnv *env, jclass clazz)
{
   (void)env; (void)clazz;
   return (jint)retro_serialize_size();
}

JNIEXPORT jboolean JNICALL
Java_ir_segasim_emu_EmulatorCore_nativeSaveState(JNIEnv *env, jclass clazz,
                                                 jbyteArray out)
{
   (void)clazz;
   jsize len = (*env)->GetArrayLength(env, out);
   jbyte *buf = (*env)->GetByteArrayElements(env, out, NULL);
   bool ok = retro_serialize(buf, (size_t)len);
   (*env)->ReleaseByteArrayElements(env, out, buf, 0);
   return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_ir_segasim_emu_EmulatorCore_nativeLoadState(JNIEnv *env, jclass clazz,
                                                 jbyteArray data)
{
   (void)clazz;
   jsize len = (*env)->GetArrayLength(env, data);
   jbyte *buf = (*env)->GetByteArrayElements(env, data, NULL);
   bool ok = retro_unserialize(buf, (size_t)len);
   (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
   return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_ir_segasim_emu_EmulatorCore_nativeReset(JNIEnv *env, jclass clazz)
{
   (void)env; (void)clazz;
   retro_reset();
}
