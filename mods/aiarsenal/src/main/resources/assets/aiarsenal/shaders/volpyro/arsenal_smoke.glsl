// Original AI Arsenal smoke & fire fields. The donor's nuclear fields remain byte-for-byte in their own source.
// Type 11: advected, expanding ellipsoidal powder/exhaust/dust lobes (profile 1..6) and volumetric fire tongues (profile 7).
vec2 arsenalSmokeField(vec3 local, vec4 timing, vec4 material, vec4 orientation) {
    float age=timing.y, life=max(timing.z,0.1), seed=timing.w;
    float radius=max(material.z,0.025);
    if(material.w>6.5) {
        float R=max(radius,0.45);
        float boundR=R*1.85;
        float distNorm=clamp(length(local)/boundR,0.0,1.0);
        float w1=1.0-distNorm*distNorm;
        float c2Win=w1*w1*w1;
        if(c2Win<=1e-4) return vec2(0.0,290.0);

        float nf=billowFbm(local*(0.92/R)+vec3(seed*7.0,-uTime*1.85-age*1.35,seed*5.0));
        float nf2=billowFbm(local*(1.65/R)+vec3(seed*3.0,-uTime*2.60-age*1.80,seed*11.0));
        float fy=clamp((local.y+R*0.85)/(R*2.10),0.0,1.0);
        vec2 sway=vec2(nf-0.5,nf2-0.5)*R*0.22*fy;
        float rad=length(local.xz+sway);
        float coneR=R*mix(0.85,0.34,pow(fy,0.75))*(0.88+0.24*nf);
        float rn=rad/max(coneR,1e-3);

        float flame=exp(-1.65*rn*rn)*c2Win*(0.82+0.34*(nf2-0.30));
        float fade=(1.0-smoothstep(life*0.58,life,age))*smoothstep(0.0,0.07,age);
        float rho=material.x*flame*fade;

        // Smooth, wide temperature field so max(temp, field.y) across raymarch steps never aliases with step jitter
        float thermalCore=exp(-0.42*distNorm*distNorm)*(1.0-0.28*fy);
        float temp=1720.0+(960.0*thermalCore+180.0*nf)*(1.0-0.14*age/life);
        return vec2(rho,temp);
    }
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
    if(profile<6.5) return vec3(0.30,0.31,0.32); // ordinary explosive blast: darker rolling smoke
    return vec3(0.16,0.09,0.04);                 // volumetric fire warm soot fringe
}
