# Replica of AtMissileEntity Javelin guidance for tuning: python3 javelin_sim.py
import math, sys
def norm(v):
    l=math.sqrt(sum(c*c for c in v)); return [c/l for c in v]
def turn(cur,want,mr):
    d=max(-1,min(1,sum(a*b for a,b in zip(cur,want)))); ang=math.acos(d)
    if ang<=mr or ang<1e-6: return want
    f=mr/ang; return norm([a*(1-f)+b*f for a,b in zip(cur,want)])
def run(D, TURN=7.0, DIVE_TURN=1.7, trig_k=1.0, trig_sp=4.0, apex_k=0.35, apex_min=14, ahead=0.45, speed=3.4, verbose=False):
    pos=[0,1.5,0]; tgt=[D/math.sqrt(2),1.4,D/math.sqrt(2)]; half=[1.5,1.4,1.5]  # grad box ~3x2.8
    v=[0.9*c for c in norm([1,0.25,1])]; apex=0; diving=False; maxy=pos[1]
    for t in range(1,300):
        sp=math.sqrt(sum(c*c for c in v))
        if t>=4 and sp<speed: sp=min(speed,sp+0.7)
        d=norm(v)
        if t>=5:
            to=[a-b for a,b in zip(tgt,pos)]; dist=math.sqrt(sum(c*c for c in to)); hz=math.hypot(to[0],to[2])
            if apex==0:
                apex=max(tgt[1],pos[1])+max(apex_min,min(60,hz*apex_k))
                if hz<25: diving=True
            if not diving and hz<(pos[1]-tgt[1])*trig_k+sp*trig_sp: diving=True
            if diving: want=[c/dist for c in to]
            else:
                fl=norm([to[0],0,to[2]]); ah=max(10,hz*ahead)
                ct=[pos[0]+fl[0]*ah,apex,pos[2]+fl[2]*ah]; want=norm([a-b for a,b in zip(ct,pos)])
            d=turn(d,want,math.radians(TURN*(DIVE_TURN if diving else 1)))
        v=[c*sp for c in d]
        if t<4: v[1]-=0.03
        # sweep hit test against target box
        for s in range(1,11):
            q=[p+c*s/10 for p,c in zip(pos,v)]
            if all(abs(q[i]-tgt[i])<=half[i] for i in range(3)):
                return ('HIT',t,maxy-1.5, v[1]/sp)
            if q[1]<0: return ('GROUND',t,maxy-1.5,[round(q[0]-tgt[0],1),round(q[2]-tgt[2],1)])
        pos=[a+b for a,b in zip(pos,v)]; maxy=max(maxy,pos[1])
    return ('LOST',)
if __name__=='__main__':
    for cfg in [dict(), dict(DIVE_TURN=2.8), dict(DIVE_TURN=2.8,trig_k=1.0,trig_sp=2.0), dict(DIVE_TURN=3.0,trig_k=0.8,trig_sp=1.5), dict(DIVE_TURN=3.0,trig_k=0.7,trig_sp=1.0,ahead=0.3)]:
        print(cfg, [ (D,)+tuple(run(D,**cfg)) for D in (30,37,60,120,300,550)])
