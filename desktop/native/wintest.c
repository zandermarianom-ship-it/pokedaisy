// PokeDaisy desktop: a Windows smoke test of the cross-compiled core - mGBA + pk_wide.c, as linked into
// pokedaisy.dll. Boots a ROM with its save, presses through the title, runs frames, writes the EXPAND frame (PPM).
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <mgba/core/core.h>
#include <mgba/core/config.h>
#include <mgba-util/vfs.h>
#include "pk_wide.h"

static uint32_t crc32(const uint8_t* p, size_t n) {
    uint32_t c = 0xFFFFFFFFu;
    for (size_t i = 0; i < n; i++) { c ^= p[i]; for (int k = 0; k < 8; k++) c = (c >> 1) ^ (0xEDB88320u & -(c & 1)); }
    return ~c;
}

int main(int argc, char** argv) {
    if (argc < 4) { fprintf(stderr, "usage: wintest rom save out.ppm\n"); return 2; }
    struct VFile* rom = VFileOpen(argv[1], O_RDONLY);
    if (!rom) { fprintf(stderr, "no ROM\n"); return 1; }
    size_t size = rom->size(rom);
    uint8_t* bytes = malloc(size);
    rom->seek(rom, 0, SEEK_SET); rom->read(rom, bytes, size);
    uint32_t crc = crc32(bytes, size);
    free(bytes);
    struct mCore* core = mCoreFindVF(rom);
    if (!core || !core->init(core)) { fprintf(stderr, "no core\n"); return 1; }
    mCoreInitConfig(core, NULL);
    unsigned w, h; core->desiredVideoDimensions(core, &w, &h);
    color_t* video = calloc(w * h, sizeof(color_t));
    core->setVideoBuffer(core, video, w);
    core->loadROM(core, rom);
    struct VFile* save = VFileOpen(argv[2], O_RDWR);
    if (save) core->loadSave(core, save);
    core->reset(core);
    int known = pk_wide_configure(crc);
    printf("ROM CRC32 %08x, %ux%u, save %s, EXPAND tables %s\n", crc, w, h, save ? "loaded" : "missing", known ? "yes" : "no");
    // Boot to the title, START, CONTINUE (A) twice, then walk right.
    const struct { int frames, keys; } script[] = { {400, 0}, {4, 8}, {120, 0}, {4, 1}, {150, 0}, {4, 1}, {240, 0}, {4, 1}, {120, 0}, {30, 16}, {40, 0} };
    static uint32_t out[PK_WIDE_MAX_VIEW_W * PK_WIDE_MAX_VIEW_H];
    struct PkWideView v = { -22, 0, 284, 160 };
    int frames = 0, lastScore = -1;
    for (size_t s = 0; s < sizeof(script) / sizeof(script[0]); s++)
        for (int f = 0; f < script[s].frames; f++) {
            core->setKeys(core, script[s].keys);
            core->runFrame(core);
            pk_wide_view(core, (const uint32_t*) video, out, v, NULL);
            lastScore = pk_wide_last_score();
            frames++;
        }
    printf("ran %d frames; map match %d/1000, mode %d, scene %d\n", frames, lastScore, pk_wide_last_mode(), pk_wide_scene());
    FILE* fp = fopen(argv[3], "wb");
    fprintf(fp, "P6\n%d %d\n255\n", v.w, v.h);
    for (int i = 0; i < v.w * v.h; i++) { uint32_t p = out[i]; fputc(p & 0xFF, fp); fputc((p >> 8) & 0xFF, fp); fputc((p >> 16) & 0xFF, fp); }
    fclose(fp);
    core->deinit(core);
    return 0;
}
