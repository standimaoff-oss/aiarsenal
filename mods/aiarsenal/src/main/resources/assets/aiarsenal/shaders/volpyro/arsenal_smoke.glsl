// Original AI Arsenal smoke field. The donor's nuclear fields remain byte-for-byte in their own source.
// Type 11: advected, expanding ellipsoidal powder/exhaust/dust lobes, NOT the donor's vertical chimney.
vec2 arsenalSmokeField(vec3 local, vec4 timing, vec4 material, vec4 orientation) {
    float age=timing.y, life=max(timing.z,0.1), seed=timing.w;
    float radius=max(material.z,0.025);
    vec3 q=local/radius;
    vec3 axis=orientation.xyz;
    if(dot(axis,axis)>0.001) {
        axis=normalize(axis);
        q-=axis*dot(q,axis)*0.20;
    }
    float turbulence=billowFbm(q*2.2+vec3(seed*19.0,-age*0.55,seed*11.0));
    float envelope=1.0-length(q)+(turbulence-0.5)*0.42;
    float lobe=smoothstep(0.0,0.38,envelope);
    float breakup=mix(0.55,1.0,turbulence);
    float fade=(1.0-smoothstep(life*0.35,life,age))*smoothstep(0.0,0.06,age);
    return vec2(material.x*lobe*breakup*fade,290.0);
}
vec3 arsenalSmokeAlbedo(float profile) {
    if(profile<1.5) return vec3(0.65,0.66,0.66); // modern muzzle: small, thin grey puff
    if(profile<3.5) return vec3(0.82,0.83,0.78); // black powder makes pale smoke, not black soot
    if(profile<4.5) return vec3(0.49,0.51,0.52); // running engine: thin exhaust haze
    if(profile<5.5) return vec3(0.52,0.46,0.36); // solid roundshot impact dust
    return vec3(0.30,0.31,0.32);                // ordinary explosive blast: darker rolling smoke
}
