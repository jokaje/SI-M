/* SI-M runtime DNA generator adapted from the uploaded procedural Muse creator;
 * implementation and interface modified. See THIRD_PARTY_NOTICES.md. */
#include "orb_avatar_dna.h"
#include <string.h>
#include <stddef.h>
static uint32_t next(uint32_t *s) { *s^=*s<<13; *s^=*s>>17; *s^=*s<<5; return *s; }
static uint32_t crc(const uint8_t *d) {
    uint32_t c=0xffffffffu;
    for (int i=0;i<44;i++) { c^=d[i]; for(int j=0;j<8;j++) c=(c>>1)^((c&1u)?0xedb88320u:0u); }
    return c^0xffffffffu;
}
bool orb_avatar_dna_seal(uint8_t d[48]) {
    if(!d) return false;
    uint32_t c=crc(d); for(int i=0;i<4;i++) d[44+i]=(uint8_t)(c>>(8*i));
    return true;
}
bool orb_avatar_dna_validate(const uint8_t d[48]) {
    if(!d || memcmp(d,"AVD1",4) || !(d[4]|d[5]|d[6]|d[7])) return false;
    static const uint8_t max[8]={7,4,2,3,5,4,3,2};
    for(int i=0;i<8;i++) if(d[8+i]>max[i]) return false;
    for(int i=36;i<44;i++) if(d[i]) return false;
    uint32_t c=crc(d); for(int i=0;i<4;i++) if(d[44+i]!=(uint8_t)(c>>(8*i))) return false;
    return true;
}
bool orb_avatar_dna_generate(uint32_t seed,uint8_t out[48]) {
    if(!seed || !out) return false;
    static const uint32_t palettes[8][5]={
        {0xf26b38,0xfff0dc,0x6cf8ec,0x1cd4a8,0xff7898},
        {0x2888b8,0xf8d878,0x98ffee,0xffa828,0xff78a8},
        {0xf48eb4,0xfff0f8,0x80fff8,0x38d8f0,0xff5888},
        {0x5a4e8c,0xf2e8f0,0xfff098,0xffb830,0xff84a8},
        {0xf5be38,0xffdc52,0x7ceeff,0x2484e8,0xff9e44},
        {0x2eb886,0xe8fff4,0xfff480,0xff68a8,0xff8898},
        {0x9c4dcc,0xf8e8ff,0x60ffdf,0xffcf40,0xff68b8},
        {0xe04868,0xffeef2,0x70f0ff,0x40e0d0,0xff9870}
    };
    memset(out,0,48); memcpy(out,"AVD1",4);
    for(int i=0;i<4;i++) out[4+i]=(uint8_t)(seed>>(8*i));
    uint32_t s=seed;
    /* Choose a coherent silhouette family, then independent accent/face variants.
       Manual edits may mix traits: all in-range combinations remain renderable. */
    static const uint8_t families[8][5]={
        /* ears,wings,tail,belly,mouth */
        {1,0,1,2,1},{2,1,2,3,2},{3,0,3,3,0},{4,0,0,4,3},
        {5,0,0,0,0},{6,0,1,1,1},{7,2,0,5,0},{0,2,0,1,0}
    };
    unsigned family=next(&s)%8u;
    out[8]=families[family][0]; out[9]=(uint8_t)(next(&s)%5u);
    out[10]=families[family][1]; out[11]=families[family][2];
    out[12]=families[family][3]; out[13]=(uint8_t)(next(&s)%5u);
    out[14]=families[family][4]; out[15]=(uint8_t)(next(&s)%3u);
    unsigned palette=next(&s)%8u;
    for(int i=0;i<5;i++) {
        uint32_t col=palettes[palette][i];
        out[16+3*i]=(uint8_t)(col>>16); out[17+3*i]=(uint8_t)(col>>8); out[18+3*i]=(uint8_t)col;
    }
    for(int i=31;i<36;i++) out[i]=(uint8_t)next(&s);
    return orb_avatar_dna_seal(out);
}
static int nibble(char c) { if(c>='0'&&c<='9') return c-'0'; if(c>='a'&&c<='f') return c-'a'+10; return -1; }
bool orb_avatar_dna_from_hex(const char *hex,uint8_t out[48]) {
    if(!hex || !out) return false;
    /* Scan at most 97 chars: no unbounded strlen on hostile input. */
    for(int i=0;i<96;i++) if(!hex[i]) return false;
    if(hex[96]) return false;
    uint8_t tmp[48]; for(int i=0;i<48;i++) {
        int hi=nibble(hex[2*i]),lo=nibble(hex[2*i+1]); if(hi<0||lo<0) return false;
        tmp[i]=(uint8_t)((hi<<4)|lo);
    }
    if(!orb_avatar_dna_validate(tmp)) return false;
    memcpy(out,tmp,48); return true;
}
bool orb_avatar_dna_to_hex(const uint8_t dna[48],char out[97]) {
    if(!out || !orb_avatar_dna_validate(dna)) return false;
    static const char chars[]="0123456789abcdef";
    for(int i=0;i<48;i++) { out[2*i]=chars[dna[i]>>4]; out[2*i+1]=chars[dna[i]&15]; }
    out[96]=0; return true;
}
