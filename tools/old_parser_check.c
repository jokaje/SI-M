/* Host-only acceptance of old parser behavior; no firmware source edits. */
#include "orb_protocol.h"
#include <stdio.h>
#include <string.h>
int main(void) {
    const char *request="{\"v\":1,\"id\":31,\"op\":\"avatar_caps\"}";
    orb_request_t out={0};
    if(orb_parse(request,strlen(request),&out)!=ORB_INVALID) return 1;
    puts("old production parser rejects avatar_caps (ORB_INVALID)");
    return 0;
}
