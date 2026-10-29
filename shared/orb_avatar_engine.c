#include "orb_avatar_engine.h"
#include <math.h>
#include <string.h>
#include <stddef.h>
#include <stdlib.h>
#define W 64
#define PI 3.14159265358979323846f
#define TAU (2.f*PI)
enum { BG,OUT,OUT2,BD,BM,BL,BH,RIM,SKIND,SKIN,SKINL,IRIS,IRIS_R,SHINE,BROW,BLUSH,BLUSHD,MOUTH,TONGUE,CYAN,CYAND,G0,G1,G2,G3,AURA1,AURA2,SPK,ACC,SHADOW,HEART,WHITE };
enum { NONE,TAIL,WING,GILL,BODY,FACE,EAR,ARM,FOOT };
typedef struct { const uint8_t *d; uint8_t *m; uint16_t *pal; uint8_t *fb; const orb_avatar_pose_t *p; float cx,cy,rx,ry,fx,fy,bob,perk,flap; } ctx;
static float clamp(float x,float a,float b) { return x<a?a:x>b?b:x; }
static int rnd(float x) { return (int)floorf(x+0.5f); }
static float frac(float x) { return x-floorf(x); }
static const uint8_t bayer4[16]={0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5};
static float bayer(int x,int y) { return (bayer4[((unsigned)y&3u)*4+((unsigned)x&3u)]+0.5f)/16.f; }
static void px(ctx *c,int x,int y,uint8_t col) { if((unsigned)x<64u&&(unsigned)y<64u) c->fb[y*64+x]=col; }
static uint32_t rgb(const uint8_t *d,int i) { return ((uint32_t)d[i]<<16)|((uint32_t)d[i+1]<<8)|d[i+2]; }
static uint32_t mix(uint32_t a,uint32_t b,float k) {
    uint32_t o=0; for(int shift=0;shift<=16;shift+=8) {
        float x=(float)((a>>shift)&255u),y=(float)((b>>shift)&255u);
        o|=(uint32_t)rnd(clamp(x+(y-x)*k,0,255))<<shift;
    } return o;
}
static uint32_t scale(uint32_t a,float k) { return mix(0,a,k); }
static uint16_t to565(uint32_t a) { return (uint16_t)((((a>>16)&255u)>>3)<<11 | (((a>>8)&255u)>>2)<<5 | ((a&255u)>>3)); }
static void palette(ctx *c) {
    uint32_t a[32]={0}; uint32_t body=rgb(c->d,16),skin=rgb(c->d,19),accent=rgb(c->d,22),iris=rgb(c->d,25),blush=rgb(c->d,28);
    static const uint32_t schemes[7][5]={
        {0xffffff,0xcfe0ff,0x6fd8ff,0x2b7de0,0x7ce8ff},
        {0xfffbe0,0xffe46b,0x5ce1ff,0x2a6bd9,0x6ae8ff},
        {0xe8faff,0x8fdcff,0x3fa2ff,0x2a5bd7,0x5cb8ff},
        {0xffe6ff,0xff9cf0,0xd35bff,0x7a2bd9,0xe07bff},
        {0xeafff4,0x9ff5cf,0x3fd9a0,0x1f9a7a,0x6ff0bf},
        {0xffd6d6,0xff6b6b,0xc7304a,0x6b1a3a,0xff5c5c},
        {0xd8d4ff,0x8f86d9,0x5a4fb0,0x2e2870,0x7c72d0}
    };
    const uint32_t *s=schemes[c->p->mode];
    a[OUT]=mix(0x181026,body,.22f); a[OUT2]=mix(body,accent,.5f);
    a[BD]=scale(body,.72f); a[BM]=body; a[BL]=mix(body,0xffffff,.26f);
    a[BH]=mix(body,0xffffff,.58f); a[RIM]=mix(accent,s[4],.5f);
    a[SKIND]=scale(skin,.84f); a[SKIN]=skin; a[SKINL]=mix(skin,0xffffff,.65f);
    a[IRIS]=0x100c1c; a[IRIS_R]=iris; a[SHINE]=0xffffff;
    a[BROW]=mix(skin,accent,.45f); a[BLUSH]=blush; a[BLUSHD]=scale(blush,.76f);
    a[MOUTH]=0x381628; a[TONGUE]=0xff6888; a[CYAN]=accent;
    a[CYAND]=scale(accent,.72f); a[SHADOW]=0x141024;
    a[HEART]=0xff4f8b; a[WHITE]=0xffffff;
    for(int i=0;i<4;i++) a[G0+i]=s[i];
    a[ACC]=s[4]; a[AURA1]=scale(s[4],.16f); a[AURA2]=scale(s[4],.34f);
    a[SPK]=mix(s[4],0xffffff,.45f);
    for(int i=0;i<32;i++) c->pal[i]=to565(a[i]);
}
static uint32_t hash(int x,int y) {
    uint32_t h=(uint32_t)x*374761393u+(uint32_t)y*668265263u;
    h=(h^(h>>13))*1274126177u; return (h^(h>>16))&65535u;
}
static uint8_t shade(ctx *c,float nx,float ny,int x,int y,bool cream) {
    float r2=nx*nx+ny*ny,nz=sqrtf(fmaxf(0.f,1.f-r2));
    float light=-.38f*nx-.52f*ny+.78f*nz;
    float b=bayer(x,y);
    if(cream) {
        if(c->d[12]==3 && (y+20)%3==0 && b<.7f) return SKIND;
        if(c->d[12]==4 && (y+(abs(x)&1))%3==0 && abs(x)%3 && b<.65f) return BM;
        float v=light+(b-.5f)*.22f;
        return v>.68f?SKINL:v>.25f?SKIN:SKIND;
    }
    if(c->d[12]==5 && ny<-.15f && (hash(x-rnd(c->cx),y-rnd(c->cy))&63u)==0u) return CYAN;
    if(c->d[8]==5 && r2>.66f && (r2-.66f)*2.4f>b) return CYAN;
    if(r2>.8f && nx*.6f+ny*.8f>.58f && b<.48f) return RIM;
    if(c->d[12]==0 && r2<.26f && ny>-.2f) return r2<.12f?SKINL:SKIN;
    float streak=(hash(x-rnd(c->cx),(y-rnd(c->cy))/3)/65535.f-.5f)*.18f;
    float v=light+(b-.5f)*.22f+streak;
    return v>.86f?BH:v>.52f?BL:v>.2f?BM:BD;
}
static bool ear(ctx *c,float x,float y,uint8_t *color) {
    float side=x<c->cx?-1.f:1.f,dy=c->cy-c->ry*.75f-y;
    switch(c->d[8]) {
    case 1: { float lt=clamp((dy+2.f)/13.f,0,1);
        if(dy< -3 || dy>12 || fabsf(x-(c->cx+side*(7+5*lt)))>4.6f*(1-.84f*lt)) break;
        *color=lt>.75f?CYAN:(fabsf(x-(c->cx+side*(7+5*lt)))<1.7f?BLUSH:BL); return true; }
    case 2: { float lt=clamp((dy+1.5f)/11.5f,0,1);
        if(dy< -2.5f||dy>10.5f||fabsf(x-c->cx-side*(6.2f+4.8f*lt-sinf(lt*2.8f)*1.6f))>3.2f*(1-.8f*lt)) break;
        *color=lt>.78f?SKINL:SKIN; return true; }
    case 3: { float hy=c->cy-c->ry*.3f;
        if(y<hy-14||y>hy+7||fabsf(x-c->cx)<c->rx*.72f) break;
        for(int g=0;g<3;g++) {
            float ax=c->cx+side*(c->rx*.88f-(g==0?1.5f:0));
            float ay=hy+(g==0?-4.5f:g==1?-1.2f:2.2f);
            float angle=(g==0?.78f:g==1?.28f:-.18f)+sinf(c->p->t*3+g*1.1f)*.14f;
            float dx=(x-ax)*side,dy2=ay-y,u=dx*cosf(angle)+dy2*sinf(angle),v=-dx*sinf(angle)+dy2*cosf(angle);
            float len=g==0?9.2f:g==1?9.8f:8.2f;
            if(u>=-1&&u<=len&&fabsf(v)<=2.f*(1-.55f*clamp(u/len,0,1))+.65f*fabsf(sinf(u*2.4f))) {
                *color=u>len*.76f?CYAN:fabsf(v)>1?BLUSH:BLUSHD; return true;
            }
        } break; }
    case 4: { float lt=clamp((dy+1.5f)/9.5f,0,1);
        if(dy< -3||dy>9.5f||fabsf(x-c->cx-side*(6.5f+lt*5.2f+lt*lt*1.6f))>3.6f*(1-.8f*lt)) break;
        *color=lt>.72f?BROW:lt>.38f?BH:BL; return true; }
    case 5: { float lt=clamp(dy/12.5f,0,1);
        if(dy< -2||dy>13) break;
        for(int i=0;i<3;i++) {
            float h=i==0?12.f:i==1?8.5f:9.f;
            if(dy>h) continue;
            float u=clamp(dy/h,0,1),off=i==0?-1.2f:i==1?-5.f:3.5f;
            float center=c->cx+off-(i==0?3.8f:i==1?2.8f:1.8f)*u*u+sinf(c->p->t*3.2f+lt*2)*.8f*lt;
            float width=(i==0?4.8f:i==1?3.4f:3.2f)*(1-.82f*u);
            if(fabsf(x-center)<=width) { *color=u>.65f?CYAN:u>.35f?BH:BL; return true; }
        } break; }
    case 6: { float u=(y-(c->cy-c->ry*.55f))/15.f;
        if(u<0||u>1) break;
        float center=c->cx+side*(c->rx*.88f+sinf(u*2.5f)*2.2f+c->perk*1.2f*u);
        float width=3.2f*sinf(u*2.6f+.3f);
        if(fabsf(x-center)<=width) { *color=u>.78f?CYAN:fabsf(x-center)<width*.45f?BLUSH:BM; return true; } break; }
    case 7: { float u=clamp(dy/10.f,0,1),center=c->cx+side*(5.5f+u*3.8f)+sinf(c->p->t*4+side)*1.2f*u;
        if(dy< -1||dy>11.5f) break;
        float d2=(x-center)*(x-center)+(dy-9.5f)*(dy-9.5f);
        if(d2<=4.2f) { *color=d2<=1.5f?WHITE:CYAN; return true; }
        if(dy<9.5f&&fabsf(x-center)<=1.1f) { *color=BL; return true; } break; }
    default: break;
    }
    return false;
}
static bool crown(ctx *c,float x,float y,uint8_t *col) {
    float top=c->cy-c->ry*.8f;
    switch(c->d[9]) {
    case 2: { float mx=x-c->cx,my=y-(top-6.2f+sinf(c->p->t*2.6f)*.8f);
        if(mx*mx+my*my<=8.5f && (mx-1.3f)*(mx-1.3f)+(my+1.1f)*(my+1.1f)>5.2f) { *col=mx<0?WHITE:CYAN; return true; } break; }
    case 3: { float u=clamp((top-y+1)/9.f,0,1);
        if(top-y>=-1&&top-y<=8&&fabsf(x-c->cx)<=2.6f*(1-.85f*u)) { *col=u>.55f?CYAN:CYAND; return true; } break; }
    case 4: { float xx=(x-c->cx)/6.5f,yy=(y-(top-5.5f+sinf(c->p->t*2.8f)*.7f))/2.2f,v=xx*xx+yy*yy;
        if(v>=.55f&&v<=1.35f) { *col=CYAN; return true; } break; }
    default: break;
    } return false;
}
static bool wing(ctx *c,float x,float y,uint8_t *col) {
    if(!c->d[10]) return false;
    float dx=fabsf(x-c->cx),start=c->rx*.78f;
    if(dx<start-1||dx>start+12.5f) return false;
    float u=clamp((dx-start)/11.f,0,1),flap=c->flap*4.5f*u,top,bot;
    if(c->d[10]==1) { top=c->cy-3.f-sinf(u*2.5f)*4.5f-flap;
        bot=c->cy+6.5f-u*2.f-fabsf(sinf(u*TAU))*2.2f-flap*.7f;
        *col=y<top+1.8f?BL:BLUSHD;
    } else { top=c->cy-5.f-sinf(u*2.8f)*5.f-flap;
        bot=c->cy+7.f-cosf(u*1.5f)*3.f-flap*.5f;
        *col=((int)floorf(x+y)&1)?SKINL:CYAN;
    }
    return y>=top&&y<=bot;
}
static bool tail(ctx *c,float x,float y,uint8_t *col) {
    if(!c->d[11]) return false;
    float dx=x-(c->cx+c->rx*.52f),dy=c->cy+c->ry*.58f-y;
    if(dx< -2||dx>16.5f||dy< -4.5f||dy>18) return false;
    float u=clamp((dx*.66f+dy*.74f)/16.f,0,1);
    float mx=u*11.5f+sinf(u*3.1f)*2.f,my=u*u*12.f+sinf(c->p->t*2.8f)*2.2f*u;
    float d2=(dx-mx)*(dx-mx)+(dy-my)*(dy-my),radius;
    switch(c->d[11]) {
    case 1: radius=5.2f*sinf(clamp(u*2.6f+.25f,0,3.05f));
        *col=u>.62f?SKINL:dy>my+1?BL:BM; break;
    case 2: radius=u<.75f?3.8f*(1-u*.65f):3.6f*sinf((1-u)*TAU+.2f);
        *col=u>.72f?(d2<radius*radius*.4f?WHITE:CYAN):(dy>my?BL:BM); break;
    default: radius=4.8f*sinf(u*PI*.88f+.2f);
        *col=d2<4.f*(1-u*.75f)*(1-u*.75f)?BM:CYAN; break;
    }
    return d2<=radius*radius;
}
static void raster(ctx *c) {
    float hw=.08f+c->d[33]*( .16f/255.f),np=.08f+c->d[34]*( .16f/255.f),bb=.06f+c->d[35]*(.14f/255.f);
    float armsx=c->rx-.5f,arm_y=c->cy+3.f;
    for(int y=0;y<64;y++) {
        float py=y+.5f,v=(py-c->cy)/c->ry;
        float row=c->rx*(.98f+expf(-(v+.22f)*(v+.22f)*24.f)*hw-expf(-(v-.08f)*(v-.08f)*18.f)*np+(v>.1f?sinf(clamp((v-.1f)*1.85f,0,1.57f))*bb:0));
        for(int x=0;x<64;x++) {
            float fx=x+.5f,u=(fx-c->cx)/row;
            uint8_t col=0,m=NONE;
            /* Arms and feet are animated ellipses; silhouette features are independent. */
            for(int side=-1;side<=1;side+=2) {
                float ax=c->cx+side*armsx,ay=arm_y;
                if(c->p->mode==ORB_AVATAR_LISTENING) { ax=c->cx+side*(armsx-1); ay=c->fy+5; }
                if(c->p->mode==ORB_AVATAR_THINKING&&side>0) { ax=c->cx+7.5f; ay=c->fy+10; }
                if(c->p->happy>.2f) { ax=c->cx+side*(armsx+1); ay=c->cy-4; }
                float dx=(fx-ax)/2.9f,dy=(py-ay)/5.3f;
                if(dx*dx+dy*dy<=1) { col=shade(c,dx*.75f,dy*.8f,x,y,false); m=ARM; break; }
            }
            if(!m && crown(c,fx,py,&col)) m=EAR;
            if(!m && c->d[8]!=3 && ear(c,fx,py,&col)) m=EAR;
            float bf=powf(fabsf(u),v<0?2.3f:2.8f)+powf(fabsf(v),v<0?2.3f:2.8f);
            float tuft=((float)hash(x-rnd(c->cx),y-rnd(c->cy))/65535.f-.5f)*.16f;
            if(!m&&bf<=1.f+tuft) {
                bool cream=false; uint8_t belly=c->d[12];
                if(belly==1) {
                    float fu=(fx-c->fx)/(c->rx*.76f),fv=(py-c->fy)/7.3f;
                    if(fu*fu*fu*fu+fv*fv*fv*fv<=1) { col=fv<-.55f?SKIND:SKIN; m=FACE; }
                } else if(belly==2) {
                    cream=(v>-.28f&&v<.08f&&fabsf(u)<.78f&&v>-.05f-.22f*cosf(u*3.5f)) ||
                        (v>=.08f&&v<.85f&&fabsf(u)<.52f*(1-(v-.08f)*.65f));
                } else if(belly==3||belly==5) {
                    cream=v>=.08f&&v<.86f&&fabsf(u)<.55f*(1-(v-.45f)*(v-.45f)*.9f);
                } else if(belly==4) {
                    float dl=(u+.3f)*(u+.3f)*2.1f+(v+.22f)*(v+.22f)*3.8f;
                    float dr=(u-.3f)*(u-.3f)*2.1f+(v+.22f)*(v+.22f)*3.8f;
                    cream=dl<=.42f||dr<=.42f||(v>=.08f&&v<.86f&&fabsf(u)<.58f*(1-(v-.42f)*(v-.42f)*1.1f));
                }
                if(!m) { col=shade(c,u*.95f,v*.95f,x,y,cream); m=BODY; }
            }
            if(!m&&c->d[8]==3&&ear(c,fx,py,&col)) m=GILL;
            if(!m) for(int side=-1;side<=1;side+=2) {
                float dx=(fx-c->cx-side*5.8f)/4.2f,dy=(py-(c->cy+c->ry+.5f))/2.6f;
                if(dx*dx+dy*dy<=1) { col=dy<-.15f?BL:BD; m=FOOT; break; }
            }
            if(!m&&wing(c,fx,py,&col)) m=WING;
            if(!m&&tail(c,fx,py,&col)) m=TAIL;
            if(m) { c->m[y*64+x]=m; c->fb[y*64+x]=col; }
        }
    }
    for(int y=0;y<64;y++) for(int x=0;x<64;x++) {
        uint8_t m=c->m[y*64+x]; if(!m||m==FACE) continue;
        if(x==0||x==63||y==0||y==63||!c->m[y*64+x-1]||!c->m[y*64+x+1]||!c->m[(y-1)*64+x]||!c->m[(y+1)*64+x])
            c->fb[y*64+x]=m==GILL?OUT2:OUT;
    }
    if(c->d[9]==1) {
        int x=rnd(c->fx),y=rnd(c->cy-c->ry-2.f+sinf(c->p->t*3)*.6f);
        px(c,x,y-1,CYAN); px(c,x-1,y,CYAN); px(c,x,y,WHITE); px(c,x+1,y,CYAN); px(c,x,y+1,CYAN);
    }
}
static void stamp(ctx *c,const char *const *rows,int n,int x,int y,uint8_t a,uint8_t b) {
    for(int j=0;j<n;j++) for(int i=0;rows[j][i];i++) {
        if(rows[j][i]=='#') px(c,x+i,y+j,a);
        else if(rows[j][i]=='o') px(c,x+i,y+j,b);
    }
}
static void eyes(ctx *c,int x,int y,float open,bool wide,bool happy,bool error) {
    if(error) { static const char *const cross[]={"#...#",".#.#.","..#..",".#.#.","#...#"}; stamp(c,cross,5,x-2,y-2,IRIS,IRIS); return; }
    if(happy) { static const char *const smile[]={".####.","##..##","#....#"}; stamp(c,smile,3,x-3,y-1,IRIS,IRIS); return; }
    if(open<.25f) { static const char *const shut[]={".####.","#....#"}; stamp(c,shut,2,x-3,y+1,IRIS,IRIS); return; }
    if(c->d[13]==4) { static const char *const bead[]={".##.","#o##","####",".##."}; stamp(c,bead,4,x-2+rnd(c->p->gaze_x),y-2+rnd(c->p->gaze_y),IRIS,SHINE); return; }
    int rx=c->d[13]==3?3:4,ry=rnd((wide?rx+1:rx)*clamp(open,.3f,1));
    for(int yy=-ry;yy<=ry;yy++) for(int xx=-rx;xx<=rx;xx++) {
        float nd=(float)(xx*xx)/(rx*rx+.5f)+(float)(yy*yy)/(ry*ry+.5f);
        if(nd>1) continue;
        float dx=xx-c->p->gaze_x,dy=yy-c->p->gaze_y*.8f,id=dx*dx+dy*dy;
        uint8_t col=WHITE;
        switch(c->d[13]) {
        case 0: col=id<3.6f?IRIS:id<8.5f?IRIS_R:WHITE; break;
        case 1: col=dx*dx*1.8f+dy*dy*.55f<3.2f?IRIS:id<9.2f?IRIS_R:WHITE; break;
        case 2: col=nd>.82f?OUT:id<4.2f?IRIS:IRIS_R; break;
        default: col=yy>0&&nd>.52f&&bayer(x+xx,y+yy)<.65f?IRIS_R:IRIS; break;
        }
        px(c,x+xx,y+yy,col);
    }
    px(c,x-1,y-1,SHINE); px(c,x-2,y-1,SHINE); px(c,x-1,y-2,SHINE);
}
static void mouth(ctx *c,int x,int y,bool speaking,bool listening,bool thinking,bool error,bool happy) {
    if(c->d[14]==3) {
        px(c,x,y-2,CYAN); px(c,x-1,y-1,CYAND); px(c,x,y-1,CYAN); px(c,x+1,y-1,CYAND);
        px(c,x,y,CYAND);
        if(speaking||listening||happy) { px(c,x-1,y+1,MOUTH); px(c,x,y+1,TONGUE); px(c,x+1,y+1,MOUTH); }
        return;
    }
    if(c->d[14]==1) { px(c,x-1,y-2,MOUTH);px(c,x,y-2,MOUTH);px(c,x+1,y-2,MOUTH); }
    if(c->d[14]==2) { px(c,x-2,y-2,MOUTH);px(c,x+2,y-2,MOUTH); }
    if(error) { static const char *const flat[]={"####"}; stamp(c,flat,1,x-2,y+1,MOUTH,MOUTH); }
    else if(speaking) {
        int h=2+rnd(clamp(c->p->level,0,1)*3.f);
        for(int j=0;j<h;j++) for(int i=0;i<6;i++) {
            if(j==h-1&&(i==0||i==5)) continue;
            px(c,x-3+i,y+j,j>=h-2&&i>1&&i<4?TONGUE:MOUTH);
        }
    } else if(listening) { static const char *const o[]={".##.","#oo#",".##."}; stamp(c,o,3,x-2,y,MOUTH,TONGUE); }
    else if(thinking) { static const char *const hmm[]={"..#","##."}; stamp(c,hmm,2,x-1,y+1,MOUTH,MOUTH); }
    else if(happy) { static const char *const grin[]={"#######",".##o##.","..ooo..","...#..."}; stamp(c,grin,4,x-3,y,MOUTH,TONGUE); }
    else if(c->d[14]==1) { static const char *const cute[]={"#.#.#",".#.#."}; stamp(c,cute,2,x-2,y,MOUTH,MOUTH); }
    else { static const char *const smile[]={"#....#","######",".#oo#.","..##.."}; stamp(c,smile,4,x-3,y,MOUTH,TONGUE); }
}
static void effects(ctx *c) {
    float t=c->p->t,fade=c->p->mode==ORB_AVATAR_OFF?clamp(1-c->p->mode_t/1.3f,0,1):1;
    float top=c->cy-c->ry-10;
    for(int i=0;i<6;i++) {
        float a=t*(c->p->mode==ORB_AVATAR_THINKING?2.8f:.8f)+i*TAU/6.f;
        int x=rnd(c->cx+cosf(a)*26),y=rnd(c->cy-3+sinf(a)*11);
        if(fade>.35f&&bayer(x,y)<.7f) { px(c,x,y,ACC); px(c,x+1,y,SPK); }
    }
    if(c->p->mode==ORB_AVATAR_LISTENING||c->p->mode==ORB_AVATAR_SPEAKING) {
        for(int side=-1;side<=1;side+=2) for(int i=-2;i<=2;i++)
            px(c,rnd(c->cx+side*(c->rx+5.f-i*i*.2f)),rnd(c->fy+i),ACC);
    }
    if(c->p->mode==ORB_AVATAR_THINKING) for(int i=0;i<3;i++)
        px(c,rnd(c->cx+13+i*4),rnd(top+7-i*3),i==(int)(frac(t*1.6f)*3.f)?G0:ACC);
    if(c->p->mode==ORB_AVATAR_ERROR) {
        static const char *const bang[]={".##.",".##.",".##.",".##.","....",".##."};
        stamp(c,bang,6,rnd(c->cx+16),rnd(top+2),ACC,ACC);
    }
    if(c->p->happy>.1f||c->p->expression==ORB_AVATAR_LOVE) {
        static const char *const heart[]={".#.#.","#o###","#####",".###.","..#.."};
        stamp(c,heart,5,rnd(c->cx+17),rnd(top+2-sinf(t*3)*2),HEART,WHITE);
    }
    if(c->p->expression==ORB_AVATAR_SLEEPY) { static const char *const z[]={"####","..#.",".#..","####"}; stamp(c,z,4,rnd(c->cx+17),rnd(top+2),ACC,WHITE); }
    if(c->d[15]==1&&(c->p->mode==ORB_AVATAR_IDLE||c->p->mode==ORB_AVATAR_SPEAKING)) {
        static const char *const bubble[]={".##.","#o.#","#..#",".##."};
        for(int i=0;i<2;i++) { float u=frac(t*.65f+i*.5f);
            stamp(c,bubble,4,rnd(c->cx+(i?16:-19)+sinf(u*TAU)*2),rnd(top+8-u*12),CYAN,WHITE); }
    } else if(c->d[15]==2&&c->p->mode==ORB_AVATAR_SPEAKING) {
        static const char *const note[]={"..##","..#.","###.","###."};
        for(int i=0;i<2;i++) { float u=frac(t*.8f+i*.5f);
            stamp(c,note,4,rnd(c->cx+(i?15:-18)+sinf(u*TAU)*2),rnd(top+4-u*9),i?ACC:CYAN,WHITE); }
    }
}
bool orb_avatar_eye_animation(float seconds, uint32_t seed, orb_avatar_mode_t mode,
    float *blink, float *gaze_x, float *gaze_y) {
    if (!blink || !gaze_x || !gaze_y || !isfinite(seconds) ||
        seconds<0.f || seconds>1000000.f || !seed || (unsigned)mode>ORB_AVATAR_OFF) return false;
    *blink=0.f; *gaze_x=0.f; *gaze_y=0.f;
    if (mode==ORB_AVATAR_BOOT || mode==ORB_AVATAR_ERROR || mode==ORB_AVATAR_OFF) return true;
    float phase=fmodf(seconds+(seed%11u)*.01f,4.f);
    if (phase>=2.f && phase<2.24f) *blink=1.f;
    if (phase>=2.24f) *gaze_x=(seed&1u)?1.f:-1.f;
    *gaze_y=phase>=3.f ? .5f : 0.f;
    return true;
}
void orb_avatar_engine_reset(orb_avatar_engine_t *e) { if(e) memset(e,0,sizeof(*e)); }
bool orb_avatar_engine_set_dna(orb_avatar_engine_t *e,const uint8_t dna[48]) {
    if(!e||!orb_avatar_dna_validate(dna)) return false;
    memcpy(e->dna,dna,48); e->ready=true; return true;
}
static bool valid_float(float x,float low,float high) { return isfinite(x)&&x>=low&&x<=high; }
bool orb_avatar_engine_render(const orb_avatar_engine_t *e,const orb_avatar_pose_t *p,uint8_t frame[4096],uint8_t mask[4096],uint16_t pal[32]) {
    if(!e||!e->ready||!p||!frame||!mask||!pal||
       (unsigned)p->mode>ORB_AVATAR_OFF||(unsigned)p->expression>ORB_AVATAR_SURPRISED||
       !valid_float(p->t,0,1000000)||!valid_float(p->mode_t,0,1000000)||
       !valid_float(p->level,0,1)||!valid_float(p->blink,0,1)||
       !valid_float(p->gaze_x,-1,1)||!valid_float(p->gaze_y,-1,1)||
       !valid_float(p->happy,0,1)) return false;
    orb_avatar_pose_t effective=*p;
    if (p->mode!=ORB_AVATAR_IDLE && p->mode!=ORB_AVATAR_THINKING)
        effective.expression=ORB_AVATAR_NEUTRAL;
    p=&effective; /* One operational precedence rule for face AND effects. */
    ctx c={.d=e->dna,.p=p,.m=mask,.fb=frame,.pal=pal};
    memset(frame,0,4096); memset(mask,0,4096); palette(&c);
    c.bob=sinf(p->t*1.8f);
    c.perk=p->mode==ORB_AVATAR_LISTENING?.85f:p->mode==ORB_AVATAR_ERROR?-.9f:0;
    c.flap=sinf(p->t*3.6f)*.35f;
    if(p->mode==ORB_AVATAR_SPEAKING) { c.bob=sinf(p->t*5.f)*.6f-p->level*1.5f; c.flap=sinf(p->t*8.f)*(.4f+p->level*.5f); }
    if(p->mode==ORB_AVATAR_THINKING) c.bob=sinf(p->t*2.4f)*.8f;
    if(p->mode==ORB_AVATAR_ERROR) c.bob=1;
    if(p->happy>.1f&&p->mode!=ORB_AVATAR_ERROR&&p->mode!=ORB_AVATAR_OFF&&p->mode!=ORB_AVATAR_BOOT) c.bob-=fabsf(sinf(p->t*9.f))*3.f*p->happy;
    float pop=p->mode==ORB_AVATAR_BOOT?clamp(p->mode_t/.6f,0,1):1;
    float squash=1.f-(1-pop)*.35f+sinf(pop*PI)*.06f;
    c.rx=(13.8f+e->dna[31]*(2.f/255.f))*(1+sinf(p->t*2+1)*.03f)*(2-squash)+p->level*.8f;
    c.ry=(17.5f+e->dna[32]*(2.5f/255.f))*(1-sinf(p->t*2+1)*.03f)*squash;
    c.cx=(e->dna[11]?30.8f:32.f)+(p->mode==ORB_AVATAR_THINKING?sinf(p->t*1.3f)*1.2f:0);
    c.cy=55.5f-c.ry+c.bob*.5f;
    c.fx=c.cx; c.fy=c.cy-c.ry*.22f+c.bob*.3f;
    /* Aura and ground precede body; all coordinates clipped by px. */
    float aura=29.f+p->level*4.f+sinf(p->t*1.5f);
    for(int y=0;y<64;y++) for(int x=0;x<64;x++) {
        float dx=x+.5f-c.cx,dy=(y+.5f-(c.cy-3.f))*1.1f;
        float d=sqrtf(dx*dx+dy*dy)/aura;
        float strength=(1-d)*.75f;
        if(d<1&&strength>bayer(x,y)*.9f) px(&c,x,y,strength>.55f+bayer(x,y)*.35f?AURA2:AURA1);
    }
    for(int y=58;y<61;y++) for(int x=16;x<48;x++)
        if(fabsf(x-c.cx)<(y==59?13.f:9.f)&&bayer(x,y)>.3f) px(&c,x,y,SHADOW);
    raster(&c);
    int ey=rnd(c.fy-.5f),lx=rnd(c.fx-5.4f),rx=rnd(c.fx+5.4f);
    bool expr_ok=p->mode==ORB_AVATAR_IDLE||p->mode==ORB_AVATAR_THINKING;
    bool happy=(p->happy>.2f&&p->mode!=ORB_AVATAR_ERROR&&p->mode!=ORB_AVATAR_OFF&&p->mode!=ORB_AVATAR_BOOT)||(expr_ok&&(p->expression==ORB_AVATAR_HAPPY||p->expression==ORB_AVATAR_LOVE));
    bool error=p->mode==ORB_AVATAR_ERROR;
    float open=1-p->blink;
    if(p->mode==ORB_AVATAR_BOOT) open=clamp((p->mode_t-.9f)/.3f,0,1);
    if(p->mode==ORB_AVATAR_OFF) open=clamp((1-p->mode_t)*1.5f,0,1);
    if(expr_ok&&p->expression==ORB_AVATAR_SLEEPY) open*=.2f;
    eyes(&c,lx,ey,open,p->mode==ORB_AVATAR_LISTENING|| (expr_ok&&p->expression==ORB_AVATAR_SURPRISED),happy,error);
    eyes(&c,rx,ey,open,p->mode==ORB_AVATAR_LISTENING|| (expr_ok&&p->expression==ORB_AVATAR_SURPRISED),happy,error);
    for(int side=-1;side<=1;side+=2) {
        int ex=side<0?lx:rx,by=ey-6;
        px(&c,ex-1,by,BROW); px(&c,ex,by,BROW); px(&c,ex+1,by+1,BROW);
        int cheek=rnd(c.fx+side*9.5f),cy=ey+3;
        for(int j=0;j<3;j++) for(int i=0;i<4;i++)
            if((j==1||i==1||i==2)&&bayer(cheek+i,cy+j)<.75f) px(&c,cheek-2+i,cy+j,j==1?BLUSHD:BLUSH);
    }
    mouth(&c,rnd(c.fx),ey+5,p->mode==ORB_AVATAR_SPEAKING,p->mode==ORB_AVATAR_LISTENING,p->mode==ORB_AVATAR_THINKING,error,happy);
    effects(&c); return true;
}
bool orb_avatar_expand_rgb565(const uint8_t frame[4096],const uint16_t pal[32],uint16_t out[4096]) {
    if(!frame||!pal||!out) return false;
    for(int i=0;i<4096;i++) { if(frame[i]>=32) return false; out[i]=pal[frame[i]]; }
    return true;
}
bool orb_avatar_scale_strip(const uint8_t frame[4096],const uint16_t pal[32],uint16_t *dst,int stride,int size,int x0,int x1,int y0,int y1) {
    if(!frame||!pal||!dst||size<1||size>512||x0<0||y0<0||x1<x0||y1<y0||x1>=size||y1>=size||stride<x1-x0+1) return false;
    for(int y=y0;y<=y1;y++) for(int x=x0;x<=x1;x++) {
        uint8_t i=frame[(y*64/size)*64+x*64/size]; if(i>=32) return false;
        dst[(y-y0)*stride+x-x0]=pal[i];
    }
    return true;
}
