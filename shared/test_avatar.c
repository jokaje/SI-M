#include "orb_avatar_engine.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#define CHECK(c) do { if (!(c)) { fprintf(stderr,"FAIL line %d: %s\n",__LINE__,#c); exit(1); } } while(0)
static uint8_t frame[4096], mask[4096];
static uint16_t pal[32], output[4096];
int main(void) {
    uint8_t dna[48], again[48], altered[48]; char hex[97];
    CHECK(!orb_avatar_dna_generate(0,dna));
    CHECK(orb_avatar_dna_generate(1,dna));
    static const char *fixture="415644310100000002040102030102022888b8f8d87898ffeeffa828ff78a8d01ab225740000000000000000cbd8b93a";
    char fixed[97]; CHECK(orb_avatar_dna_to_hex(dna,fixed) && strcmp(fixed,fixture)==0);
    CHECK(orb_avatar_dna_validate(dna));
    CHECK(dna[0]=='A' && dna[3]=='1' && dna[4]==1 && dna[5]==0);
    CHECK(orb_avatar_dna_generate(1,again) && memcmp(dna,again,48)==0);
    CHECK(orb_avatar_dna_to_hex(dna,hex) && strlen(hex)==96);
    CHECK(orb_avatar_dna_from_hex(hex,again) && memcmp(dna,again,48)==0);
    hex[0]='A'; CHECK(!orb_avatar_dna_from_hex(hex,again)); hex[0]='4';
    hex[95]=0; CHECK(!orb_avatar_dna_from_hex(hex,again));
    memcpy(altered,dna,48); altered[36]=1; CHECK(orb_avatar_dna_seal(altered) && !orb_avatar_dna_validate(altered));
    memcpy(altered,dna,48); altered[8]=8; CHECK(orb_avatar_dna_seal(altered) && !orb_avatar_dna_validate(altered));
    memcpy(altered,dna,48); altered[44]^=1; CHECK(!orb_avatar_dna_validate(altered));
    memcpy(altered,dna,48); altered[0]='B'; CHECK(orb_avatar_dna_seal(altered) && !orb_avatar_dna_validate(altered));
    memcpy(altered,dna,48); memset(altered+4,0,4); CHECK(orb_avatar_dna_seal(altered) && !orb_avatar_dna_validate(altered));
    orb_avatar_engine_t e; orb_avatar_engine_reset(&e);
    orb_avatar_pose_t pose={.mode=ORB_AVATAR_IDLE,.t=2,.mode_t=1};
    CHECK(!orb_avatar_engine_render(&e,&pose,frame,mask,pal));
    CHECK(orb_avatar_engine_set_dna(&e,dna));
    for (int mode=0;mode<7;mode++) {
        pose.mode=(orb_avatar_mode_t)mode; pose.mode_t=0.5f;
        CHECK(orb_avatar_engine_render(&e,&pose,frame,mask,pal));
        CHECK(orb_avatar_expand_rgb565(frame,pal,output));
        int occupied=0; for(int i=0;i<4096;i++) { CHECK(frame[i]<32 && mask[i]<9); if(frame[i]) occupied++; }
        CHECK(occupied>250);
    }
    pose.mode=(orb_avatar_mode_t)7; CHECK(!orb_avatar_engine_render(&e,&pose,frame,mask,pal));
    pose.mode=ORB_AVATAR_IDLE; pose.t=NAN; CHECK(!orb_avatar_engine_render(&e,&pose,frame,mask,pal)); pose.t=2;
    pose.gaze_x=INFINITY; CHECK(!orb_avatar_engine_render(&e,&pose,frame,mask,pal)); pose.gaze_x=0;
    pose.expression=(orb_avatar_expression_t)6; CHECK(!orb_avatar_engine_render(&e,&pose,frame,mask,pal)); pose.expression=ORB_AVATAR_NEUTRAL;
    uint16_t guarded[1024]; for(int i=0;i<1024;i++) guarded[i]=0xa55a;
    CHECK(orb_avatar_scale_strip(frame,pal,guarded+1,32,320,40,63,80,87));
    CHECK(guarded[0]==0xa55a && guarded[1+7*32+24]==0xa55a);
    CHECK(!orb_avatar_scale_strip(frame,pal,guarded,32,320,40,72,80,87));
    CHECK(!orb_avatar_scale_strip(frame,pal,guarded,32,0,0,0,0,0));
    for(int trait=8;trait<=15;trait++) {
        static const int counts[]={8,5,3,4,6,5,4,3};
        for(int value=0;value<counts[trait-8];value++) {
            memcpy(altered,dna,48); altered[trait]=(uint8_t)value;
            CHECK(orb_avatar_dna_seal(altered) && orb_avatar_dna_validate(altered));
            CHECK(orb_avatar_engine_set_dna(&e,altered));
            CHECK(orb_avatar_engine_render(&e,&pose,frame,mask,pal));
        }
    }
    for(uint32_t seed=1;seed<=128;seed++) {
        CHECK(orb_avatar_dna_generate(seed,dna) && orb_avatar_engine_set_dna(&e,dna));
        pose.mode=ORB_AVATAR_SPEAKING; pose.level=(seed%101)/100.f;
        pose.t=seed/13.f; pose.expression=(orb_avatar_expression_t)(seed%6);
        CHECK(orb_avatar_engine_render(&e,&pose,frame,mask,pal));
    }
    /* Every optional expression must be pixel-identical to neutral outside
     * IDLE/THINKING, including effects layered after face rendering. */
    CHECK(orb_avatar_dna_generate(42,dna) && orb_avatar_engine_set_dna(&e,dna));
    uint16_t neutral[4096];
    for(int mode=0;mode<7;mode++) for(int expression=0;expression<=ORB_AVATAR_SURPRISED;expression++) {
        pose=(orb_avatar_pose_t){.mode=(orb_avatar_mode_t)mode,.t=3.2f,.mode_t=1.5f,.level=.3f};
        CHECK(orb_avatar_engine_render(&e,&pose,frame,mask,pal));
        CHECK(orb_avatar_expand_rgb565(frame,pal,neutral));
        pose.expression=(orb_avatar_expression_t)expression;
        CHECK(orb_avatar_engine_render(&e,&pose,frame,mask,pal));
        CHECK(orb_avatar_expand_rgb565(frame,pal,output));
        if(mode!=ORB_AVATAR_IDLE && mode!=ORB_AVATAR_THINKING)
            CHECK(!memcmp(neutral,output,sizeof neutral));
    }
    float blink,gx,gy;
    CHECK(!orb_avatar_eye_animation(NAN,11,ORB_AVATAR_IDLE,&blink,&gx,&gy));
    CHECK(!orb_avatar_eye_animation(2.f,0,ORB_AVATAR_IDLE,&blink,&gx,&gy));
    CHECK(!orb_avatar_eye_animation(2.f,11,(orb_avatar_mode_t)7,&blink,&gx,&gy));
    for(int mode=0;mode<7;mode++) {
        CHECK(orb_avatar_eye_animation(2.12f,11,(orb_avatar_mode_t)mode,&blink,&gx,&gy));
        CHECK(blink==((mode==ORB_AVATAR_BOOT||mode==ORB_AVATAR_ERROR||mode==ORB_AVATAR_OFF)?0.f:1.f));
        CHECK(orb_avatar_eye_animation(2.5f,11,(orb_avatar_mode_t)mode,&blink,&gx,&gy));
        CHECK(gx==((mode==ORB_AVATAR_BOOT||mode==ORB_AVATAR_ERROR||mode==ORB_AVATAR_OFF)?0.f:1.f));
    }
    CHECK(orb_avatar_eye_animation(2.5f,12,ORB_AVATAR_IDLE,&blink,&gx,&gy) && gx==-1.f);
    printf("PASS DNA/traits/128 seeds/seven modes/expression precedence/eye policy/strips; state=%zu bytes\n",sizeof(e));
}
