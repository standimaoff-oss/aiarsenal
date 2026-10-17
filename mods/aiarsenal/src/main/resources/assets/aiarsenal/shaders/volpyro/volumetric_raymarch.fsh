#version 150
in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uDepthTex;
uniform sampler2D uVolumeDataTex;
uniform int uVolumeCount;
uniform int uStepCount;
uniform vec3 uCameraForward;
uniform vec3 uCameraRight;
uniform vec3 uCameraUp;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform float uAspect;
uniform float uTanHalfFov;
uniform float uNear;
uniform float uFar;
uniform float uTime;

const float PI = 3.14159265359;
const int MAX_VOLUME_INSTANCES = 64;
float sq(float x) { return x*x; }   // pow(x,2.0) is undefined for x<0 in GLSL (NaN on many drivers)

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}
float valueNoise(vec3 x) {
    vec3 i = floor(x), f = fract(x);
    f = f*f*(3.0-2.0*f);
    float n000 = hash31(i + vec3(0,0,0));
    float n100 = hash31(i + vec3(1,0,0));
    float n010 = hash31(i + vec3(0,1,0));
    float n110 = hash31(i + vec3(1,1,0));
    float n001 = hash31(i + vec3(0,0,1));
    float n101 = hash31(i + vec3(1,0,1));
    float n011 = hash31(i + vec3(0,1,1));
    float n111 = hash31(i + vec3(1,1,1));
    return mix(mix(mix(n000,n100,f.x),mix(n010,n110,f.x),f.y),
               mix(mix(n001,n101,f.x),mix(n011,n111,f.x),f.y),f.z);
}
float billowFbm(vec3 p) {
    float a = 0.5, sum = 0.0, norm = 0.0;
    for (int i=0; i<3; ++i) {
        float n = valueNoise(p);
        sum += (1.0 - abs(2.0*n - 1.0)) * a;
        norm += a;
        p = p * 2.03 + vec3(7.1, 3.7, 5.3);
        a *= 0.5;
    }
    return sum / max(norm, 1e-4);
}

float dualHenyeyGreenstein(float cosTheta, float gF, float gB, float w) {
    const float INV_4PI = 0.07957747;
    float gf2 = gF*gF, gb2 = gB*gB;
    float hgF = INV_4PI * (1.0-gf2) / pow(max(1.0+gf2-2.0*gF*cosTheta,1e-4),1.5);
    float hgB = INV_4PI * (1.0-gb2) / pow(max(1.0+gb2-2.0*gB*cosTheta,1e-4),1.5);
    return mix(hgB,hgF,w);
}
vec3 computeMultiScattering(float tauSun, float cosTheta, vec3 sunColor, vec3 albedo) {
    vec3 sumLight = vec3(0.0);
    float a=1.0, b=1.0, c=1.0;
    for (int octave=0; octave<3; ++octave) {
        float phase = dualHenyeyGreenstein(cosTheta,0.72*c,-0.30*c,0.75);
        sumLight += (albedo*a)*sunColor*exp(-tauSun*b)*phase;
        a *= 0.55; b *= 0.50; c *= 0.50;
    }
    return sumLight;
}
vec3 blackbodyRadiation(float tempK) {
    if (tempK < 750.0) return vec3(0.0);
    float t = clamp(tempK,800.0,12000.0)/100.0;
    vec3 col;
    col.r = (t <= 66.0) ? 1.0 : clamp(1.292936*pow(t-60.0,-0.1332047),0.0,1.0);
    col.g = (t <= 66.0) ? clamp(0.3900815*log(t)-0.6318414,0.0,1.0)
                        : clamp(1.129891*pow(t-60.0,-0.0755148),0.0,1.0);
    col.b = (t >= 66.0) ? 1.0 : ((t <= 19.0) ? 0.0 : clamp(0.5432068*log(t-10.0)-1.196254,0.0,1.0));
    vec3 linearCol = pow(col,vec3(2.2));
    float normT = (tempK-750.0)/2200.0;
    float intensity = pow(max(normT,0.0),3.2)*4.5;
    return linearCol*intensity;
}
vec4 volumeTexel(int index) {
    return texelFetch(uVolumeDataTex, ivec2(index & 63, index >> 6), 0);
}
bool raySphere(vec3 ro, vec3 rd, vec3 c, float r, out vec2 interval) {
    vec3 oc=ro-c;
    float b=dot(oc,rd), h=b*b-dot(oc,oc)+r*r;
    if (h < 0.0) return false;
    float s=sqrt(h);
    interval=vec2(-b-s,-b+s);
    return interval.y > 0.0;
}
vec2 evaluateVolume(vec3 p, vec4 h0, vec4 h1, vec4 h2, vec4 h3) {
    int type=int(h1.x+0.5);
    vec3 local=p-h0.xyz;
    float age=h1.y, lifetime=max(h1.z,0.1), seed=h1.w;
    float density=0.0, temperature=290.0;
    float n=billowFbm(local*1.45 + vec3(seed*17.0, -age*0.32, seed*7.0));
    if (type == 1) {
        float height=max(h2.z,0.5);
        float h=local.y;
        if (h >= 0.0 && h <= height*1.25) {
            float f=clamp(h/height,0.0,1.0);
            float width=h0.w*(0.13+0.19*f) + height*0.035;
            float radial=length(local.xz);
            float plume=exp(-radial*radial/max(width*width*1.6,1e-3));
            float wisps=clamp(0.46+0.94*(n-0.42),0.05,1.25);
            density=h2.x*plume*wisps*smoothstep(0.0,0.09,f)*exp(-f*0.45);
            temperature=290.0+(h2.y-290.0)*exp(-age*0.25)*exp(-f*1.65);
        }
        density *= 1.0-smoothstep(lifetime*0.82,lifetime,age);
    } else if (type == 2) {
        // ---- Conventional explosion: fast fireball -> rolling smoke puff that rises, spreads and thins ----
        float scale=max(h2.z,0.5);
        bool he=(h2.w>0.5);                                   // high-explosive warhead style
        // bigger charges evolve more slowly (fireball/column time scale ~ sqrt(size)): light ~0.75x, medium 1x, ICBM ~1.65x
        float t=he ? age/sqrt(scale/8.8) : age;
        float n2=billowFbm(local*(3.4/scale)+vec3(seed*5.0,-t*0.6,seed*13.0));
        vec3 fireC=vec3(0.0,scale*0.22,0.0);
        float fbR=he ? scale*mix(0.45,0.95,smoothstep(0.0,0.35,t)) : scale*mix(0.28,0.72,smoothstep(0.0,0.45,t));
        float fireDist=length(local-fireC)-fbR;
        float fire=clamp(0.7-fireDist/(scale*0.2)+(n-0.5)*(he ? 1.5 : 0.7)+(he ? (n2-0.5)*0.8 : 0.0),0.0,1.0)*(he ? (1.0-smoothstep(0.3,1.0,t)) : (1.0-smoothstep(0.35,1.1,t)));
        // smoke: three offset lobes so the puff is lumpy instead of a sphere
        float rise=scale*(0.25+0.28*t);
        float grow=scale*(0.5+0.42*smoothstep(0.0,5.0,t));
        vec3 sc=vec3(0.0,rise,0.0);
        vec3 lobeA=sc+vec3(0.35,0.12,0.2)*scale*(0.6+0.4*seed);
        vec3 lobeB=sc+vec3(-0.3,0.25,-0.25)*scale;
        vec3 lobeC=sc+vec3(0.1,-0.1,-0.4)*scale*(0.7+0.3*seed);
        float sd=min(min(length(local-sc)-grow,length(local-lobeA)-grow*0.75),min(length(local-lobeB)-grow*0.8,length(local-lobeC)-grow*0.7));
        float puff=clamp(0.55-sd/(scale*0.3)+(n-0.5)*1.3+(n2-0.5)*0.7,0.0,1.0)*smoothstep(he ? 0.12 : 0.2, he ? 0.7 : 1.0, t);
        float thinning=exp(-t*0.22);
        float core=clamp(1.0-length(local-fireC)/(fbR*1.15),0.0,1.0);
        temperature=290.0+h2.y*mix(core,clamp(1.0-n*1.1,0.0,1.0),0.25)*exp(-t*2.4);
        float shockRadius=scale*1.4*pow(max(t/0.22,0.001),0.5);
        float shock=0.0;   // no condensation shell: a warhead shows only the explosion itself
        float fade=1.0-smoothstep(lifetime*0.45,lifetime*0.85,age);
        if (he) {
            // crisp spherical shock front removed: warhead rockets show only the explosion, no shock wave
            float heShockR=scale*0.5+scale*4.5*pow(max(t,0.001),0.6);
            shock=0.0;
            // dirt / debris column thrown straight up, slows and collapses back
            float colH=scale*(0.4+2.6*mix(0.8,1.35,smoothstep(5.0,22.0,scale))*(1.0-exp(-t*1.1)))*(1.0-smoothstep(3.0,8.0,t)*0.6);
            float colR=scale*(0.16+0.14*smoothstep(0.0,3.0,t))*(1.0+0.6*clamp(local.y/max(colH,0.5),0.0,1.0));
            float colN=billowFbm(local*(5.0/scale)+vec3(seed*4.0,-t*1.1,seed*9.0));
            float column=step(0.0,local.y)*(1.0-smoothstep(colH*0.8,colH*1.05,local.y))*clamp(0.5-(length(local.xz)-colR)/(scale*0.16)+(colN-0.5)*1.5+(n-0.5)*0.5,0.0,1.0)*smoothstep(0.05,0.3,t)*(1.0-smoothstep(4.0,9.0,t));
            // low, wide FILLED dust mound hugging the ground (a shell here read as a shock wave at ICBM scale)
            float baseR=scale*(0.5+0.9*smoothstep(0.0,2.5,t));
            float brad=length(local.xz);
            float hB=scale*0.30*(1.0-0.7*sq(clamp(brad/max(baseR,0.5),0.0,1.0)))*(0.4+0.8*n+0.5*n2);
            float base=clamp(0.5+(hB-max(local.y,0.0))/(scale*0.10),0.0,1.0)
                        *(1.0-smoothstep(baseR*0.85,baseR*1.10,brad+(n-0.5)*scale*0.2))
                        *smoothstep(0.2,0.9,t)*exp(-t*0.12)*step(-scale*0.05,local.y);
            density=(max(max(puff*0.45*thinning,base*0.5),max(fire*1.1,column*0.7))+shock)*fade;
            temperature=290.0+h2.y*mix(max(core,fire*0.8),clamp(1.0-n*1.1,0.0,1.0),0.15)*exp(-t*1.7);
        } else {
            density=(max(puff*0.85*thinning,fire)+shock)*fade;
            // the whole early fireball skin is incandescent, not only its centre
            temperature=290.0+h2.y*mix(max(core,fire*0.85),clamp(1.0-n*1.1,0.0,1.0),0.2)*exp(-t*1.9);
        }
        if (temperature < 800.0) temperature=290.0;
    } else if (type == 3) {
        // ---- Rocket smoke trail chord. h3.w = time span of the chord; the start end is the oldest. ----
        vec3 axis=normalize(h3.xyz+vec3(1e-6));
        float halfLen=max(h2.z,0.05);
        float along=dot(local,axis);
        float span=max(h3.w,0.0);
        float tl=max(age-span*clamp((along+halfLen)/(2.0*halfLen),0.0,1.0),0.0);   // local age at this point
        // the trail stays exactly where the rocket laid it (no buoyant rise / "wind"), thickening into a cone
        vec3 q=local;
        along=dot(q,axis);
        float closest=clamp(along,-halfLen,halfLen);
        float radial=length(q-axis*closest);
        float w0=max(h2.w,0.08);
        float width=w0*(1.0+3.2*(1.0-exp(-tl/3.0))+0.06*tl);
        if (abs(along)<=halfLen+width*1.5 && radial<width*2.2) {
            float capEnd=1.0-smoothstep(halfLen-width*0.7,halfLen+width*0.7,abs(along));   // cross-fade at chord joints (sums to ~1)
            float nb=billowFbm(q*(1.6/width)+vec3(seed*23.0,-tl*0.12,seed*9.0));
            float nb2=billowFbm(q*(4.5/width)+vec3(seed*5.0,tl*0.2,seed*31.0));
            float envelope=clamp(0.62-(radial-width*0.55)/(width*0.42)+(nb-0.5)*1.9+(nb2-0.5)*0.6,0.0,1.0);
            float fade=exp(-tl/16.0)*(1.0-smoothstep(lifetime*0.7,lifetime,age));
            density=h2.x*envelope*capEnd*fade*(0.85+0.3*nb2)*smoothstep(0.03,0.16,tl);   // smoke starts where the flame ends
            // only the freshest metre or two still glows (afterburning aluminium / soot)
            float glow=exp(-tl*5.0)*exp(-radial/max(w0*0.8,0.05));
            temperature=290.0+(h2.y-290.0)*glow;
            if (temperature < 800.0) temperature=290.0;
        }
    } else if (type == 5) {
        vec3 axis=normalize(h3.xyz+vec3(1e-6));
        float along=dot(local,axis);
        float halfLen=max(h2.z,0.05);
        float width=max(h2.w,0.045);
        float closest=clamp(along,-halfLen,halfLen);
        float radial=length(local-axis*closest);
        if (abs(along)<=halfLen+width && radial<width*2.5) {
            float envelope=1.0-smoothstep(width*0.45,width*2.35,radial);
            float fade=exp(-age*1.15);
            density=h2.x*envelope*(0.58+0.72*n)*fade;
            float cooling=exp(-age*1.7);
            temperature=290.0+(h2.y-290.0)*cooling*exp(-radial/max(width,0.01));
        }
    } else if (type == 4) {
        // ---- Rocket exhaust flame: white-hot core with shock diamonds, orange fringe, tapering to the smoke ----
        vec3 axis=normalize(h3.xyz+vec3(1e-6));
        float z=dot(local,axis);
        float radial=length(local-axis*z);
        float nozzleR=max(h2.w,0.07);
        float L=max(h2.z,0.5);
        if (z>=-nozzleR && z<=L) {
            float f=clamp(z/L,0.0,1.0);
            float cellLen=max(nozzleR*7.0,0.4);
            float diamond=pow(max(0.0,-cos(PI*z/cellLen)),4.0)*exp(-2.5*f);
            float flameR=nozzleR*(1.3+1.5*f)*(1.0-smoothstep(0.55,1.0,f))+nozzleR*0.3;
            float core=exp(-radial*radial/max(flameR*flameR*0.55,1e-4));
            float taper=1.0-smoothstep(0.6,1.0,f);
            float decay=(1.0-smoothstep(lifetime*0.75,lifetime,age));
            density=h2.x*(core*1.2+0.25*exp(-radial*radial/max(flameR*flameR*1.6,1e-4)))*taper*decay;
            float heat=(1.0-0.5*smoothstep(0.15,1.0,f))*(1.0+0.35*diamond);
            temperature=290.0+5400.0*heat*exp(-radial*radial/max(flameR*flameR*0.7,1e-4))*decay;
        }
    } else if (type == 6) {
        // ---- Nuclear detonation. h2.w: 0 nuke, 1 RDS-4, 2 Tsar, 3 Fat Man, 4 underwater burst ----
        float S = max(h2.z, 4.0);
        float T = 1.0 + S*0.012;                          // big devices evolve in slow motion
        float u = age / T;
        float v = h2.w;
        float rds = step(0.5, v)*step(v, 1.5);   // RDS-4 "Tatyana": tall narrow stem, compact fiery cap
        float fat = step(2.5, v)*step(v, 3.5);   // Fat Man (Mk-3): taller stem, big tiered white-grey cap
        float uw  = step(3.5, v);                // underwater: white spray dome + base surge, no fire
        float fade = 1.0 - smoothstep(lifetime*0.7, lifetime, age);
        vec3 g = local + vec3(0.0, 1.5*S, 0.0)* (1.0 - uw);   // ground-zero relative (unused underwater)
        float nz = billowFbm(g*(2.6/S) + vec3(seed*17.0, -u*0.22, seed*7.0));
        float nz2 = billowFbm(g*(7.0/S) + vec3(seed*3.0, -u*0.5, seed*11.0));
        if (uw > 0.5) {
            // ---- Underwater burst (Hardtack Wahoo / Umbrella): cauliflower spray dome on a flat base surge ----
            // 1.5.6: absolute clock, same fast tempo for every device: the white spray GROWS in ~1.8 s
            // and then FALLS back into the sea completely by ~3.6 s (it used to linger for 30-60 s).
            vec3 w = local;                                    // instance centre sits at the water surface
            float grow = smoothstep(0.0, 1.8, age);            // full dome in ~1.8 s
            float fall = smoothstep(2.0, 3.6, age);            // ...and completely back down by ~3.6 s
            float nw3 = billowFbm(w*(19.0/S) + vec3(seed*3.0, -u*0.35, seed*13.0));
            float domeR = S*mix(0.30, 1.45, grow)*(1.0 - 0.85*fall);
            vec3 dc = w - vec3(0.0, domeR*0.72, 0.0) + vec3(0.0, fall*S*0.55, 0.0);   // sinks as it falls
            float spikes = (1.0 - abs(2.0*nw3 - 1.0))*0.35 + (1.0 - abs(2.0*nz2 - 1.0))*0.22;   // ridged cauliflower spikes
            float domeDist = length(dc) - domeR*(1.0 + 0.16*spikes) - (nz-0.5)*S*0.10;
            float dome = clamp(0.62 - domeDist/(S*0.19) + (nz-0.5)*0.55 + (nz2-0.5)*0.45, 0.0, 1.0)*step(-S*0.10, w.y)*(1.0 - fall);
            // flat white base surge racing out over the surface
            float surgeR = S*mix(0.5, 2.9, smoothstep(0.0, 1.5, age));
            float surgeH = S*0.15*(1.0 + 0.6*nz);
            float surge = clamp(0.5 - (length(vec2(max(length(w.xz) - surgeR*0.72, 0.0), (abs(w.y) + surgeH*0.2)*2.2)) - surgeH*0.55)/(S*0.09)
                          + (nz-0.5)*0.8 + (nz2-0.5)*0.5, 0.0, 1.0)*(1.0 - smoothstep(2.2, 3.8, age));
            density = max(dome, surge*0.85)*h2.x*(1.0 - fall)*fade;
            temperature = 290.0;                               // no fire: everything is water spray
        } else {
            // fireball
            float fbR = S*mix(0.22, 0.62, smoothstep(0.0, 2.5, u));
            float capY = S*(0.2 + (mix(2.0, 2.9, rds) + 0.6*fat)*smoothstep(0.4, 16.0, u));
            vec3 cr = g - vec3(0.0, capY, 0.0);
            float radial = length(cr.xz);
            float capMajor = S*mix(0.1, mix(1.15, 0.72, rds) + 0.30*fat, smoothstep(1.0, 14.0, u));
            float capMinor = S*mix(0.4, mix(0.58, 0.80, rds) + 0.15*fat, smoothstep(1.0, 14.0, u));
            float torus = length(vec2(radial - capMajor, cr.y*(mix(1.35, 1.05, rds) - 0.15*fat))) - capMinor;
            float sphere = length(cr) - fbR;
            float capDist = min(torus, sphere);
            float early = smoothstep(0.3, 3.0, u);                    // young fireball: compact, nearly spherical, fully incandescent
            float cap = clamp(0.62 - capDist/(S*0.24*mix(0.45, 1.0, early)) + (nz-0.5)*0.95*mix(0.35, 1.0, early) + (nz2-0.5)*mix(0.25, 0.65, max(rds*early, fat*0.8)), 0.0, 1.0);
            // Fat Man: stacked tiers (horizontal grooves) across the big cap
            cap *= mix(1.0, 0.55 + 0.75*smoothstep(0.25, 0.75, 0.5 + 0.5*sin(cr.y*3.4/S + nz*1.6)), fat);
            // stem
            float stemR = S*mix(0.12, mix(0.32, 0.21, rds)*(1.0 - 0.18*fat), smoothstep(0.0, 12.0, u))*(1.0 + 0.3*clamp(1.0 - g.y/max(capY,1.0), 0.0, 1.0));
            float stemDist = length(g.xz) - stemR*(1.0 + 0.2*(nz-0.5));
            float stem = step(0.0, g.y)*step(g.y, capY)*clamp(0.55 - stemDist/(S*0.09) + (nz-0.5)*0.5, 0.0, 1.0)*smoothstep(0.5, 3.0, u);
            // ground skirt: every land variant throws up a huge filled, lumpy dust base (like the RDS-4 tests)
            float skR = S*(0.62 + 2.2*smoothstep(0.0, 12.0, u));
            float skMinor = S*(0.12 + 0.26*smoothstep(0.0, 12.0, u));
            float skirtDist = length(vec2(max(length(g.xz) - skR, 0.0), (g.y - skMinor*0.4)*1.6)) - skMinor;
            float skirt = clamp(0.5 - skirtDist/(S*0.1) + (nz-0.5)*0.9 + (nz2-0.5)*0.4, 0.0, 1.0)*(1.0 - smoothstep(0.8, 2.5, u))*step(-skMinor, g.y);  // very early dust ring
            // filled, lumpy dust base: height falls off from the stem to the rim, lumps from three noise octaves
            float rr = length(g.xz)/max(skR, 1.0);
            float nz3 = billowFbm(g*(14.0/S) + vec3(seed*5.0, -u*0.3, seed*2.0));
            float hTop = skMinor*(0.62 - 0.42*rr*rr)*(0.3 + 0.6*nz + 0.8*nz2 + 0.6*nz3);
            float baseLife = (1.0 - smoothstep(14.0, 40.0, u))*smoothstep(0.3, 2.0, u);
            float filled = clamp(0.5 + (hTop - g.y)/(S*0.04), 0.0, 1.0)*(1.0 - smoothstep(0.8, 1.15, rr + (nz2-0.5)*0.4))*step(-skMinor, g.y)*baseLife*(1.0 - 0.5*smoothstep(0.0, 1.0, rr));
            skirt = max(skirt, filled);
            // RDS-4: a second dust collar a third of the way up the stem
            float collarY = capY*0.32;
            float collar = rds*clamp(0.35 - (length(vec2(length(g.xz) - stemR*2.0, (g.y - collarY)*0.9)) - S*0.14)/(S*0.1) + (nz-0.5)*1.6 + (nz2-0.5)*1.2, 0.0, 1.0)*smoothstep(3.0, 8.0, u)*(1.0 - smoothstep(18.0, 32.0, u));
            // Wilson condensation ring: brief translucent disc around the fireball
            float wilsonR = S*mix(0.6, 1.4, smoothstep(0.6, 3.0, u));
            float wilson = exp(-sq((radial - wilsonR)/(S*0.08)))*exp(-sq(cr.y/(S*0.06)))*0.25*smoothstep(0.6, 1.2, u)*(1.0 - smoothstep(2.5, 4.0, u));
            float shape = max(max(max(cap, stem*0.85), skirt*0.7), collar*0.45) + wilson;
            // temperature: fireball core glows for a long time, then cools
            float core = clamp(1.0 - length(cr)/(fbR*1.25), 0.0, 1.0);
            // the whole fireball is incandescent (not only its centre) and stays hot for many seconds;
            // afterwards the rising cap keeps a dull red glow from below that fades out
            float fbHeat = (0.5 + 0.5*core)*step(sphere, fbR*mix(0.7, 0.15, early))*exp(-u*0.14);
            // RDS-4: fire keeps burning in the upper half of the cap and shows through the gaps between the lobes
            float capGlow = mix(0.42*cap*exp(-u*0.22),
                                0.9*smoothstep(0.0, 0.05, cap)*exp(-u*0.042)*smoothstep(-0.3, 0.45, cr.y/max(capMinor, 1.0))*(0.45 + 0.9*clamp(1.0 - nz2*1.25, 0.0, 1.0) + 0.5*clamp(1.0 - nz*1.2, 0.0, 1.0)),
                                rds);
            float heat = max(fbHeat, capGlow)*(0.8 + 0.2*clamp(1.0 - nz*1.1, 0.0, 1.0));
            // Tsar Bomba (variant 2): the early fireball glows a deep orange-red, then whitens
            float tempScale = (v > 1.5 && v < 2.5) ? mix(0.58, 0.9, smoothstep(0.0, 4.5, u)) : 1.0;
            temperature = 290.0 + h2.y*tempScale*heat*step(0.001, shape);
            density = shape*h2.x*fade;
        }
        if (temperature < 800.0) temperature = 290.0;
    } else if (type == 7) {
        // ---- Ground shock ring: detaches from the fireball at 0.35R and runs out to 6R at 120 blocks/s ----
        float R = max(h2.z, 4.0);
        float ringR = 0.35*R + 120.0*age;
        float horiz = length(local.xz);
        float w = max(0.24*R*(1.0 + 0.06*age), 3.0);               // horizontal half-width (grows a little)
        float hgt = max(0.16*R*exp(-age*0.14), 1.5);                // vertical half-height (shrinks)
        float nr = billowFbm(local*vec3(2.2/R, 5.0/R, 2.2/R) + vec3(seed*9.0, -age*0.5, seed*4.0));
        float ringDist = abs(horiz - ringR + w*0.35);
        float dust = clamp(0.6 - ringDist/w + (nr-0.5)*0.9, 0.0, 1.0)*exp(-sq(max(local.y, 0.0)/hgt))*step(-hgt*0.5, local.y);
        // sharp bright front slightly ahead of the dust
        float front = exp(-sq((horiz - ringR - w*0.4)/max(w*0.18, 0.6)))*exp(-sq(local.y/(hgt*2.0)))*0.35;
        // hemispherical blast front in the air, fades quickly
        float dome = 0.0;   // the thin air-shock shell aliased into visible bands when ray-marched; the ground dust wall carries the effect
        float intensity = exp(-age*0.075);
        float fade = 1.0 - smoothstep(lifetime*0.8, lifetime, age);
        density = (dust*0.9 + front + dome)*intensity*fade;
        temperature = 290.0;
    } else if (type == 8) {
        // ---- Kinetic penetrator impact: brief impact flash, cone of ejecta along h3 axis, dust that settles ----
        float S=max(h2.z,1.5);
        vec3 ax=normalize(h3.xyz.y==0.0 && h3.x==0.0 && h3.z==0.0 ? vec3(0.0,1.0,0.0) : h3.xyz);
        float t=age/sqrt(S/10.0);                            // heavier penetrators throw ejecta higher and for longer
        float nk=billowFbm(local*(3.2/S)+vec3(seed*11.0,-t*0.35,seed*5.0));
        float nk2=billowFbm(local*(9.0/S)+vec3(seed*3.0,-t*0.9,seed*13.0));
        // ejecta cone: material leaves along the axis and sags under gravity
        float sag=0.5*9.8*sq(max(t-0.25,0.0))*0.12;
        vec3 q=local+vec3(0.0,sag,0.0);
        float along=dot(q,ax);
        float across=length(q-ax*along);
        float coneLen=S*(0.3+1.6*(1.0-exp(-t*1.4)));
        float coneR=along*0.45+S*0.06;
        float cone=step(0.0,along)*(1.0-smoothstep(coneLen*0.5,coneLen,along))*clamp(0.45-(across-coneR)/(S*0.18)+(nk-0.5)*1.7+(nk2-0.5)*0.7,0.0,1.0);
        cone*=smoothstep(0.0,0.15,t)*(1.0-smoothstep(3.0,7.0,t));
        // slow hemispherical dust cloud around the hole, grows and thins
        float cloudR=S*(0.35+0.9*smoothstep(0.0,5.0,t));
        float cloud=clamp(0.5-(length(vec3(local.x,local.y*1.8+S*0.1,local.z))-cloudR)/(S*0.18)+(nk-0.5)*1.0+(nk2-0.5)*0.5,0.0,1.0)*smoothstep(0.1,0.8,t)*exp(-t*0.16)*step(-S*0.15,local.y);
        // thin dust column rising above the crater
        float colH=S*(0.5+1.3*(1.0-exp(-t*0.5)));
        float col=step(0.0,local.y)*(1.0-smoothstep(colH*0.7,colH,local.y))*clamp(0.4-(length(local.xz)-S*(0.12+0.1*smoothstep(0.0,4.0,t)))/(S*0.14)+(nk-0.5)*1.5+(nk2-0.5)*0.5,0.0,1.0)*smoothstep(0.3,1.5,t)*(1.0-smoothstep(5.0,10.0,t));
        // impact flash: tiny, white-hot, gone in a quarter second (no sustained fire – nothing to burn)
        float flash=clamp(1.0-length(local)/(S*0.3),0.0,1.0)*(1.0-smoothstep(0.0,0.3,t));
        float fade=1.0-smoothstep(lifetime*0.55,lifetime,age);
        density=(max(max(cone*0.6,cloud*0.7),col*0.5)+flash*1.5)*h2.x*fade;
        temperature=290.0+(h2.y-290.0)*flash;
        if (temperature < 800.0) temperature=290.0;
    } else if (type == 9) {
        // ---- Thermobaric (fuel-air): aerosol cloud -> slow huge orange fireball -> sooty column + lingering ground fire ----
        float S=max(h2.z,2.0);
        vec3 g=local+vec3(0.0,0.9*S,0.0);                  // ground-relative
        float T=1.0+S*0.015;
        float u=age/T;
        float nt=billowFbm(g*(2.4/S)+vec3(seed*17.0,-u*0.3,seed*7.0));
        float nt2=billowFbm(g*(6.5/S)+vec3(seed*3.0,-u*0.7,seed*11.0));
        // 1) dispersal: a translucent grey mist sphere before ignition
        float mistR=S*0.65;
        float mist=clamp(0.55-(length(g-vec3(0.0,S*0.3,0.0))-mistR)/(S*0.2)+(nt-0.5)*0.8,0.0,1.0)*0.35*(1.0-smoothstep(0.18,0.32,u));
        // 2) fireball: grows slowly to ~1.1R, flattened hemisphere that rolls up at the rim
        float fbR=S*mix(0.25,1.1,smoothstep(0.22,1.4,u));
        float fbY=S*(0.35+0.55*smoothstep(0.6,4.0,u));
        vec3 fc=g-vec3(0.0,fbY,0.0);
        float ball=length(vec3(fc.x,fc.y*1.25,fc.z))-fbR;
        float rimR=fbR*0.9;
        float rim=length(vec2(length(fc.xz)-rimR,(fc.y+fbR*0.25)*1.4))-fbR*0.42;
        float fireD=min(ball,rim);
        float fire=clamp(0.62-fireD/(S*0.2)+(nt-0.5)*1.2+(nt2-0.5)*0.5,0.0,1.0)*smoothstep(0.2,0.35,u)*(1.0-smoothstep(2.6,5.0,u))*step(-S*0.1,g.y);
        // 3) sooty column with a bulging top, rises for a long time
        float colH=S*(0.5+2.8*smoothstep(1.0,12.0,u));
        float colR=S*(0.32+0.22*smoothstep(1.0,10.0,u))*(1.0+0.9*smoothstep(colH*0.6,colH,g.y));
        float column=step(0.0,g.y)*(1.0-smoothstep(colH*0.8,colH*1.05,g.y))*clamp(0.45-(length(g.xz)-colR)/(S*0.2)+(nt-0.5)*1.6+(nt2-0.5)*0.6,0.0,1.0)*smoothstep(1.2,3.0,u)*exp(-u*0.05);
        // 4) lingering ground fire ring and low smoke where the fuel burned
        float ringR=S*(0.6+0.5*smoothstep(0.5,3.0,u));
        float groundFire=exp(-sq((length(g.xz)-ringR)/(S*0.28)))*exp(-sq(max(g.y,0.0)/(S*0.14)))*clamp(0.5+(nt2-0.5)*1.6,0.0,1.0)*smoothstep(0.8,1.6,u)*(1.0-smoothstep(5.0,11.0,u))*0.8;
        // 5) overpressure front removed: warhead rockets show only the explosion, no shock wave
        float shockR=S*0.4+S*3.0*pow(max(u-0.2,0.0),0.7);
        float shock=0.0;
        float core=clamp(1.0-length(fc)/(fbR*1.3),0.0,1.0);
        float fireHeat=(0.5+0.5*mix(core,clamp(1.0-nt*1.15,0.0,1.0),0.35))*step(0.01,fire)*exp(-max(u-0.8,0.0)*0.25);
        float groundHeat=0.45*step(0.05,groundFire)*clamp(0.6+(nt2-0.5),0.0,1.0);
        float fade=1.0-smoothstep(lifetime*0.7,lifetime,age);
        density=(max(max(fire*1.05,column*0.55),max(mist,groundFire*0.6))+shock)*h2.x*fade;
        temperature=290.0+h2.y*max(fireHeat,groundHeat*(1.0-smoothstep(4.0,10.0,u)));
        if (temperature < 800.0) temperature=290.0;
    } else if (type == 10) {
        // ---- Launch pad cloud: exhaust slams into the deck and billows out sideways as a flat white/tan cloud ----
        float S=max(h2.z,2.0);
        vec3 w=local+vec3(0.0,0.05*S,0.0);                 // pad deck level
        float u=age;                                      // seconds (lifetime ~20 s)
        float n1=billowFbm(w*(3.0/S)+vec3(seed*9.0,-u*0.22,seed*5.0));
        float n2=billowFbm(w*(8.0/S)+vec3(seed*3.0,u*0.13,seed*17.0));
        float R=S*mix(0.35,2.6,smoothstep(0.0,6.0,u));     // surge front racing outwards
        float H=S*mix(0.45,0.85,smoothstep(0.0,4.0,u));    // cloud top
        float rr=length(w.xz);
        float hTop=H*(1.0-0.55*smoothstep(R*0.35,R,rr))*(0.45+0.75*n1+0.5*n2);
        float disc=clamp(0.5+(hTop-max(w.y,-S*0.1))/(S*0.09),0.0,1.0)
                   *(1.0-smoothstep(R*0.85,R*1.12,rr+(n1-0.5)*R*0.25))*step(-S*0.12,w.y);
        // central bulge where the jet still punches upwards (first ~2 s)
        float bulge=clamp(0.55-(length(vec3(w.xz,(w.y-S*0.55)*0.8))-S*0.5)/(S*0.16)+(n1-0.5)*1.2,0.0,1.0)*(1.0-smoothstep(1.2,3.0,u));
        float fade=1.0-smoothstep(lifetime*0.7,lifetime,age);
        density=max(disc*0.85,bulge*0.9)*h2.x*fade;
        temperature=290.0+900.0*bulge*exp(-u*1.5);
        if (temperature < 800.0) temperature=290.0;
    }
    return vec2(max(density,0.0),max(temperature,290.0));
}
void main() {
    vec2 ndc=vUv*2.0-1.0;
    vec3 rd=normalize(uCameraForward + uCameraRight*(ndc.x*uAspect*uTanHalfFov) + uCameraUp*(ndc.y*uTanHalfFov));
    float depth=texture(uDepthTex,vUv).r;
    float z=depth*2.0-1.0;
    float viewDistance=(2.0*uNear*uFar)/max(uFar+uNear-z*(uFar-uNear),1e-5);
    float sceneDistance=(depth>=0.999999) ? 1.0e5 : viewDistance/max(dot(rd,uCameraForward),0.03);
    float tStart=1e20, tEnd=0.0;
    int intersected[MAX_VOLUME_INSTANCES];
    int intersectedCount=0;
    for (int i=0;i<MAX_VOLUME_INSTANCES;++i) {
        if (i>=uVolumeCount) break;
        int base=i*4;
        vec4 h0=volumeTexel(base);
        vec2 interval;
        if (raySphere(vec3(0.0),rd,h0.xyz,h0.w,interval)) {
            float lo=max(interval.x,0.0), hi=min(interval.y,sceneDistance);
            if (hi>lo) {
                intersected[intersectedCount]=i;
                intersectedCount++;
                tStart=min(tStart,lo); tEnd=max(tEnd,hi);
            }
        }
    }
    if (tEnd<=tStart || tStart>1.0e5) { fragColor=vec4(0.0); return; }
    float steps=float(clamp(uStepCount,12,96));
    float ds=(tEnd-tStart)/steps;
    // static interleaved-gradient dither: hides banding without the boiling white noise a per-frame jitter gives
    float jitter=0.25+0.5*fract(52.9829189*fract(dot(gl_FragCoord.xy,vec2(0.06711056,0.00583715))));
    float transmittance=1.0;
    vec3 accumulated=vec3(0.0);
    vec3 sunDir=normalize(uSunDir);
    float cosTheta=dot(-rd,sunDir);
    for (int stepIndex=0;stepIndex<96;++stepIndex) {
        if (stepIndex>=uStepCount) break;
        float t=tStart+(float(stepIndex)+jitter)*ds;
        vec3 p=rd*t;
        float sigma=0.0, temp=290.0, sigmaSun=0.0, emitGain=1.0;
        vec3 albedo=vec3(0.14,0.15,0.16);
        float hotWeight=0.0;
        for (int candidate=0;candidate<MAX_VOLUME_INSTANCES;++candidate) {
            if (candidate>=intersectedCount) break;
            int base=intersected[candidate]*4;
            vec4 h0=volumeTexel(base), h1=volumeTexel(base+1), h2=volumeTexel(base+2), h3=volumeTexel(base+3);
            if (distance(p,h0.xyz)>h0.w) continue;
            vec2 field=evaluateVolume(p,h0,h1,h2,h3);
            float rho=field.x;
            if (rho<=0.001) continue;
            sigma+=rho*1.15;
            // one-tap self-shadowing towards the sun: gives the cloud a lit and a shaded side
            float shadowStep=max(0.8,h0.w*0.07);
            sigmaSun+=evaluateVolume(p+sunDir*shadowStep,h0,h1,h2,h3).x*1.15*shadowStep;
            float tw=clamp((field.y-290.0)/5000.0,0.0,1.0)*rho;
            temp=max(temp,field.y);
            hotWeight+=tw;
            int vtype=int(h1.x+0.5);
            if (vtype==1) albedo=vec3(0.24,0.25,0.26);
            else if (vtype==3) albedo=vec3(0.80,0.76,0.70);
            else if (vtype==6) {
                // per-variant cloud colour: tan dust bases for the land bursts, white spray underwater
                if (h2.w > 3.5) albedo = vec3(0.86, 0.88, 0.92);
                else if (h2.w > 2.5) albedo = mix(vec3(0.60, 0.58, 0.55), vec3(0.75, 0.74, 0.73), smoothstep(8.0, 30.0, h1.y));      // Fat Man: white-grey
                else if (h2.w > 1.5) albedo = (h1.y < 6.0) ? vec3(0.46, 0.34, 0.27) : mix(vec3(0.50, 0.42, 0.34), vec3(0.66, 0.63, 0.60), smoothstep(6.0, 20.0, h1.y));   // Tsar
                else if (h2.w > 0.5) albedo = mix(vec3(0.47, 0.38, 0.28), vec3(0.56, 0.50, 0.43), smoothstep(10.0, 45.0, h1.y));   // RDS-4 tan
                else albedo = mix(vec3(0.47, 0.40, 0.31), vec3(0.62, 0.58, 0.52), smoothstep(8.0, 30.0, h1.y));                    // nuke: tan dust -> grey
                emitGain=2.0;
            }
            else if (vtype==10) { albedo=mix(vec3(0.70,0.64,0.54), vec3(0.82,0.80,0.76), smoothstep(1.5,8.0,h1.y)); }
            else if (vtype==7) albedo=vec3(0.38,0.35,0.30);
            else if (vtype==8) albedo=vec3(0.40,0.36,0.30);
            else if (vtype==9) { albedo=(h1.y<0.3*(1.0+h2.z*0.015)) ? vec3(0.55,0.56,0.58) : vec3(0.20,0.19,0.18); emitGain=3.5; }
            else if (vtype==2) { albedo=(h2.w>0.5) ? ((h1.y<3.0) ? vec3(0.44,0.38,0.30) : vec3(0.33,0.31,0.29)) : mix(vec3(0.24,0.21,0.18), vec3(0.34,0.34,0.35), smoothstep(1.0,5.0,h1.y)); emitGain=1.8; }
        }
        if (sigma>1e-4) {
            float opticalDepth=sigma*0.22+min(sigmaSun*0.9,2.2);   // capped: multiple scattering keeps thick clouds from going black
            float sunT=exp(-opticalDepth);
            // single scattering (phase) + an isotropic multiple-scattering floor, both shadowed
            vec3 scattered=computeMultiScattering(opticalDepth,cosTheta,uSunColor,albedo)*4.5 + albedo*uSunColor*(0.34*sunT+0.16);
            vec3 ambient=vec3(0.40,0.45,0.55)*albedo*(0.55+0.45*sunT);
            float emissionWeight=clamp(hotWeight/max(sigma,1e-4),0.0,1.0);
            vec3 emitted=blackbodyRadiation(temp)*(0.16*emitGain*emissionWeight);
            vec3 source=scattered+ambient+emitted;
            float stepTrans=exp(-sigma*ds);
            // in-scattered / emitted radiance integrated over the step: L*(1-exp(-sigma*ds)).
            // (dividing by sigma here would make thin shells glow as brightly as dense smoke)
            vec3 stepColor=transmittance*source*(1.0-stepTrans);
            accumulated+=stepColor;
            transmittance*=stepTrans;
            if (transmittance<0.012) break;
        }
    }
    fragColor=vec4(accumulated,clamp(1.0-transmittance,0.0,1.0));
}
