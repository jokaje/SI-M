/* Host-only capture from accepted production render dispatch. No device I/O. */
#include "muse_pixel.h"
#include "orb_character.h"
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
static const char *ids[]={"flame","fox_spirit","crystal_dragon","celestial_owl","starlight_axolotl"};
int main(int argc,char **argv) {
    if(argc!=2) return 2;
    int id=-1;
    for(int i=0;i<5;i++) if(!strcmp(argv[1],ids[i])) id=i;
    if(id<0) return 2;
    orb_character_activate(orb_character_from_stored(ids[id]));
    muse_pixel_set_size(64);
    for(int mode=1;mode<=4;mode++) {
        if(mode==3) continue; /* idle/listening/speaking only */
        for(int f=0;f<4;f++) {
            muse_pose_t pose={.mode=(muse_mode_t)mode,.t=1.25f+f*.19f,
                .mode_t=.25f+f*.19f,.level=mode==4?.2f+f*.2f:0,
                .happy=0};
            uint16_t pixels[4096];
            muse_pixel_render(&pose);
            muse_pixel_scale(pixels,64,0,63,0,63);
            if(fwrite(pixels,sizeof(pixels),1,stdout)!=1) return 3;
        }
    }
    return fflush(stdout)==0?0:3;
}
