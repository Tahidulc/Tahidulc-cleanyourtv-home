(()=>{var Cl="170";var Tu=0,uc=1,Au=2;var vh=1,Ru=2,Gn=3,fi=0,ze=1,qe=2,ui=0,Fn=1,pi=2,dc=3,fc=4,Cu=5,Ii=100,Iu=101,Pu=102,Fu=103,Du=104,Lu=200,Uu=201,Nu=202,Ou=203,ro=204,ao=205,Bu=206,zu=207,ku=208,Hu=209,Vu=210,Gu=211,Wu=212,Ku=213,Xu=214,oo=0,lo=1,co=2,fs=3,ho=4,uo=5,fo=6,po=7,Mh=0,qu=1,Yu=2,di=0,Zu=1,$u=2,Ju=3,Il=4,Qu=5,ju=6,td=7;var Sh=300,ps=301,ms=302,mo=303,go=304,ma=306,mi=1e3,Kn=1001,_o=1002,an=1003,ed=1004;var _r=1005;var fn=1006,Ta=1007;var Xn=1008;var Zn=1009,Eh=1010,bh=1011,Js=1012,Pl=1013,Di=1014,Pn=1015,or=1016,Fl=1017,Dl=1018,gs=1020,wh=35902,Th=1021,Ah=1022,bn=1023,Rh=1024,Ch=1025,hs=1026,_s=1027,Ll=1028,Ul=1029,Ih=1030,Nl=1031;var Ol=1033,Gr=33776,Wr=33777,Kr=33778,Xr=33779,xo=35840,yo=35841,vo=35842,Mo=35843,So=36196,Eo=37492,bo=37496,wo=37808,To=37809,Ao=37810,Ro=37811,Co=37812,Io=37813,Po=37814,Fo=37815,Do=37816,Lo=37817,Uo=37818,No=37819,Oo=37820,Bo=37821,qr=36492,zo=36494,ko=36495,Ph=36283,Ho=36284,Vo=36285,Go=36286;var Yr=2300,Wo=2301,Aa=2302,pc=2400,mc=2401,gc=2402;var nd=3200,id=3201;var Fh=0,sd=1,hi="",Re="srgb",Is="srgb-linear",ga="linear",le="srgb";var Xi=7680;var _c=519,rd=512,ad=513,od=514,Dh=515,ld=516,cd=517,hd=518,ud=519,xc=35044;var yc="300 es",qn=2e3,Zr=2001,gi=class{addEventListener(t,e){this._listeners===void 0&&(this._listeners={});let n=this._listeners;n[t]===void 0&&(n[t]=[]),n[t].indexOf(e)===-1&&n[t].push(e)}hasEventListener(t,e){if(this._listeners===void 0)return!1;let n=this._listeners;return n[t]!==void 0&&n[t].indexOf(e)!==-1}removeEventListener(t,e){if(this._listeners===void 0)return;let s=this._listeners[t];if(s!==void 0){let r=s.indexOf(e);r!==-1&&s.splice(r,1)}}dispatchEvent(t){if(this._listeners===void 0)return;let n=this._listeners[t.type];if(n!==void 0){t.target=this;let s=n.slice(0);for(let r=0,a=s.length;r<a;r++)s[r].call(this,t);t.target=null}}},Oe=["00","01","02","03","04","05","06","07","08","09","0a","0b","0c","0d","0e","0f","10","11","12","13","14","15","16","17","18","19","1a","1b","1c","1d","1e","1f","20","21","22","23","24","25","26","27","28","29","2a","2b","2c","2d","2e","2f","30","31","32","33","34","35","36","37","38","39","3a","3b","3c","3d","3e","3f","40","41","42","43","44","45","46","47","48","49","4a","4b","4c","4d","4e","4f","50","51","52","53","54","55","56","57","58","59","5a","5b","5c","5d","5e","5f","60","61","62","63","64","65","66","67","68","69","6a","6b","6c","6d","6e","6f","70","71","72","73","74","75","76","77","78","79","7a","7b","7c","7d","7e","7f","80","81","82","83","84","85","86","87","88","89","8a","8b","8c","8d","8e","8f","90","91","92","93","94","95","96","97","98","99","9a","9b","9c","9d","9e","9f","a0","a1","a2","a3","a4","a5","a6","a7","a8","a9","aa","ab","ac","ad","ae","af","b0","b1","b2","b3","b4","b5","b6","b7","b8","b9","ba","bb","bc","bd","be","bf","c0","c1","c2","c3","c4","c5","c6","c7","c8","c9","ca","cb","cc","cd","ce","cf","d0","d1","d2","d3","d4","d5","d6","d7","d8","d9","da","db","dc","dd","de","df","e0","e1","e2","e3","e4","e5","e6","e7","e8","e9","ea","eb","ec","ed","ee","ef","f0","f1","f2","f3","f4","f5","f6","f7","f8","f9","fa","fb","fc","fd","fe","ff"],vc=1234567,Ks=Math.PI/180,xs=180/Math.PI;function Gi(){let i=Math.random()*4294967295|0,t=Math.random()*4294967295|0,e=Math.random()*4294967295|0,n=Math.random()*4294967295|0;return(Oe[i&255]+Oe[i>>8&255]+Oe[i>>16&255]+Oe[i>>24&255]+"-"+Oe[t&255]+Oe[t>>8&255]+"-"+Oe[t>>16&15|64]+Oe[t>>24&255]+"-"+Oe[e&63|128]+Oe[e>>8&255]+"-"+Oe[e>>16&255]+Oe[e>>24&255]+Oe[n&255]+Oe[n>>8&255]+Oe[n>>16&255]+Oe[n>>24&255]).toLowerCase()}function Ce(i,t,e){return Math.max(t,Math.min(e,i))}function Bl(i,t){return(i%t+t)%t}function dd(i,t,e,n,s){return n+(i-t)*(s-n)/(e-t)}function fd(i,t,e){return i!==t?(e-i)/(t-i):0}function Xs(i,t,e){return(1-e)*i+e*t}function pd(i,t,e,n){return Xs(i,t,1-Math.exp(-e*n))}function md(i,t=1){return t-Math.abs(Bl(i,t*2)-t)}function gd(i,t,e){return i<=t?0:i>=e?1:(i=(i-t)/(e-t),i*i*(3-2*i))}function _d(i,t,e){return i<=t?0:i>=e?1:(i=(i-t)/(e-t),i*i*i*(i*(i*6-15)+10))}function xd(i,t){return i+Math.floor(Math.random()*(t-i+1))}function yd(i,t){return i+Math.random()*(t-i)}function vd(i){return i*(.5-Math.random())}function Md(i){i!==void 0&&(vc=i);let t=vc+=1831565813;return t=Math.imul(t^t>>>15,t|1),t^=t+Math.imul(t^t>>>7,t|61),((t^t>>>14)>>>0)/4294967296}function Sd(i){return i*Ks}function Ed(i){return i*xs}function bd(i){return(i&i-1)===0&&i!==0}function wd(i){return Math.pow(2,Math.ceil(Math.log(i)/Math.LN2))}function Td(i){return Math.pow(2,Math.floor(Math.log(i)/Math.LN2))}function Ad(i,t,e,n,s){let r=Math.cos,a=Math.sin,o=r(e/2),l=a(e/2),c=r((t+n)/2),h=a((t+n)/2),u=r((t-n)/2),d=a((t-n)/2),p=r((n-t)/2),g=a((n-t)/2);switch(s){case"XYX":i.set(o*h,l*u,l*d,o*c);break;case"YZY":i.set(l*d,o*h,l*u,o*c);break;case"ZXZ":i.set(l*u,l*d,o*h,o*c);break;case"XZX":i.set(o*h,l*g,l*p,o*c);break;case"YXY":i.set(l*p,o*h,l*g,o*c);break;case"ZYZ":i.set(l*g,l*p,o*h,o*c);break;default:console.warn("THREE.MathUtils: .setQuaternionFromProperEuler() encountered an unknown order: "+s)}}function os(i,t){switch(t.constructor){case Float32Array:return i;case Uint32Array:return i/4294967295;case Uint16Array:return i/65535;case Uint8Array:return i/255;case Int32Array:return Math.max(i/2147483647,-1);case Int16Array:return Math.max(i/32767,-1);case Int8Array:return Math.max(i/127,-1);default:throw new Error("Invalid component type.")}}function Ke(i,t){switch(t.constructor){case Float32Array:return i;case Uint32Array:return Math.round(i*4294967295);case Uint16Array:return Math.round(i*65535);case Uint8Array:return Math.round(i*255);case Int32Array:return Math.round(i*2147483647);case Int16Array:return Math.round(i*32767);case Int8Array:return Math.round(i*127);default:throw new Error("Invalid component type.")}}var Lh={DEG2RAD:Ks,RAD2DEG:xs,generateUUID:Gi,clamp:Ce,euclideanModulo:Bl,mapLinear:dd,inverseLerp:fd,lerp:Xs,damp:pd,pingpong:md,smoothstep:gd,smootherstep:_d,randInt:xd,randFloat:yd,randFloatSpread:vd,seededRandom:Md,degToRad:Sd,radToDeg:Ed,isPowerOfTwo:bd,ceilPowerOfTwo:wd,floorPowerOfTwo:Td,setQuaternionFromProperEuler:Ad,normalize:Ke,denormalize:os},at=class i{constructor(t=0,e=0){i.prototype.isVector2=!0,this.x=t,this.y=e}get width(){return this.x}set width(t){this.x=t}get height(){return this.y}set height(t){this.y=t}set(t,e){return this.x=t,this.y=e,this}setScalar(t){return this.x=t,this.y=t,this}setX(t){return this.x=t,this}setY(t){return this.y=t,this}setComponent(t,e){switch(t){case 0:this.x=e;break;case 1:this.y=e;break;default:throw new Error("index is out of range: "+t)}return this}getComponent(t){switch(t){case 0:return this.x;case 1:return this.y;default:throw new Error("index is out of range: "+t)}}clone(){return new this.constructor(this.x,this.y)}copy(t){return this.x=t.x,this.y=t.y,this}add(t){return this.x+=t.x,this.y+=t.y,this}addScalar(t){return this.x+=t,this.y+=t,this}addVectors(t,e){return this.x=t.x+e.x,this.y=t.y+e.y,this}addScaledVector(t,e){return this.x+=t.x*e,this.y+=t.y*e,this}sub(t){return this.x-=t.x,this.y-=t.y,this}subScalar(t){return this.x-=t,this.y-=t,this}subVectors(t,e){return this.x=t.x-e.x,this.y=t.y-e.y,this}multiply(t){return this.x*=t.x,this.y*=t.y,this}multiplyScalar(t){return this.x*=t,this.y*=t,this}divide(t){return this.x/=t.x,this.y/=t.y,this}divideScalar(t){return this.multiplyScalar(1/t)}applyMatrix3(t){let e=this.x,n=this.y,s=t.elements;return this.x=s[0]*e+s[3]*n+s[6],this.y=s[1]*e+s[4]*n+s[7],this}min(t){return this.x=Math.min(this.x,t.x),this.y=Math.min(this.y,t.y),this}max(t){return this.x=Math.max(this.x,t.x),this.y=Math.max(this.y,t.y),this}clamp(t,e){return this.x=Math.max(t.x,Math.min(e.x,this.x)),this.y=Math.max(t.y,Math.min(e.y,this.y)),this}clampScalar(t,e){return this.x=Math.max(t,Math.min(e,this.x)),this.y=Math.max(t,Math.min(e,this.y)),this}clampLength(t,e){let n=this.length();return this.divideScalar(n||1).multiplyScalar(Math.max(t,Math.min(e,n)))}floor(){return this.x=Math.floor(this.x),this.y=Math.floor(this.y),this}ceil(){return this.x=Math.ceil(this.x),this.y=Math.ceil(this.y),this}round(){return this.x=Math.round(this.x),this.y=Math.round(this.y),this}roundToZero(){return this.x=Math.trunc(this.x),this.y=Math.trunc(this.y),this}negate(){return this.x=-this.x,this.y=-this.y,this}dot(t){return this.x*t.x+this.y*t.y}cross(t){return this.x*t.y-this.y*t.x}lengthSq(){return this.x*this.x+this.y*this.y}length(){return Math.sqrt(this.x*this.x+this.y*this.y)}manhattanLength(){return Math.abs(this.x)+Math.abs(this.y)}normalize(){return this.divideScalar(this.length()||1)}angle(){return Math.atan2(-this.y,-this.x)+Math.PI}angleTo(t){let e=Math.sqrt(this.lengthSq()*t.lengthSq());if(e===0)return Math.PI/2;let n=this.dot(t)/e;return Math.acos(Ce(n,-1,1))}distanceTo(t){return Math.sqrt(this.distanceToSquared(t))}distanceToSquared(t){let e=this.x-t.x,n=this.y-t.y;return e*e+n*n}manhattanDistanceTo(t){return Math.abs(this.x-t.x)+Math.abs(this.y-t.y)}setLength(t){return this.normalize().multiplyScalar(t)}lerp(t,e){return this.x+=(t.x-this.x)*e,this.y+=(t.y-this.y)*e,this}lerpVectors(t,e,n){return this.x=t.x+(e.x-t.x)*n,this.y=t.y+(e.y-t.y)*n,this}equals(t){return t.x===this.x&&t.y===this.y}fromArray(t,e=0){return this.x=t[e],this.y=t[e+1],this}toArray(t=[],e=0){return t[e]=this.x,t[e+1]=this.y,t}fromBufferAttribute(t,e){return this.x=t.getX(e),this.y=t.getY(e),this}rotateAround(t,e){let n=Math.cos(e),s=Math.sin(e),r=this.x-t.x,a=this.y-t.y;return this.x=r*n-a*s+t.x,this.y=r*s+a*n+t.y,this}random(){return this.x=Math.random(),this.y=Math.random(),this}*[Symbol.iterator](){yield this.x,yield this.y}},Yt=class i{constructor(t,e,n,s,r,a,o,l,c){i.prototype.isMatrix3=!0,this.elements=[1,0,0,0,1,0,0,0,1],t!==void 0&&this.set(t,e,n,s,r,a,o,l,c)}set(t,e,n,s,r,a,o,l,c){let h=this.elements;return h[0]=t,h[1]=s,h[2]=o,h[3]=e,h[4]=r,h[5]=l,h[6]=n,h[7]=a,h[8]=c,this}identity(){return this.set(1,0,0,0,1,0,0,0,1),this}copy(t){let e=this.elements,n=t.elements;return e[0]=n[0],e[1]=n[1],e[2]=n[2],e[3]=n[3],e[4]=n[4],e[5]=n[5],e[6]=n[6],e[7]=n[7],e[8]=n[8],this}extractBasis(t,e,n){return t.setFromMatrix3Column(this,0),e.setFromMatrix3Column(this,1),n.setFromMatrix3Column(this,2),this}setFromMatrix4(t){let e=t.elements;return this.set(e[0],e[4],e[8],e[1],e[5],e[9],e[2],e[6],e[10]),this}multiply(t){return this.multiplyMatrices(this,t)}premultiply(t){return this.multiplyMatrices(t,this)}multiplyMatrices(t,e){let n=t.elements,s=e.elements,r=this.elements,a=n[0],o=n[3],l=n[6],c=n[1],h=n[4],u=n[7],d=n[2],p=n[5],g=n[8],x=s[0],m=s[3],f=s[6],y=s[1],_=s[4],M=s[7],C=s[2],A=s[5],E=s[8];return r[0]=a*x+o*y+l*C,r[3]=a*m+o*_+l*A,r[6]=a*f+o*M+l*E,r[1]=c*x+h*y+u*C,r[4]=c*m+h*_+u*A,r[7]=c*f+h*M+u*E,r[2]=d*x+p*y+g*C,r[5]=d*m+p*_+g*A,r[8]=d*f+p*M+g*E,this}multiplyScalar(t){let e=this.elements;return e[0]*=t,e[3]*=t,e[6]*=t,e[1]*=t,e[4]*=t,e[7]*=t,e[2]*=t,e[5]*=t,e[8]*=t,this}determinant(){let t=this.elements,e=t[0],n=t[1],s=t[2],r=t[3],a=t[4],o=t[5],l=t[6],c=t[7],h=t[8];return e*a*h-e*o*c-n*r*h+n*o*l+s*r*c-s*a*l}invert(){let t=this.elements,e=t[0],n=t[1],s=t[2],r=t[3],a=t[4],o=t[5],l=t[6],c=t[7],h=t[8],u=h*a-o*c,d=o*l-h*r,p=c*r-a*l,g=e*u+n*d+s*p;if(g===0)return this.set(0,0,0,0,0,0,0,0,0);let x=1/g;return t[0]=u*x,t[1]=(s*c-h*n)*x,t[2]=(o*n-s*a)*x,t[3]=d*x,t[4]=(h*e-s*l)*x,t[5]=(s*r-o*e)*x,t[6]=p*x,t[7]=(n*l-c*e)*x,t[8]=(a*e-n*r)*x,this}transpose(){let t,e=this.elements;return t=e[1],e[1]=e[3],e[3]=t,t=e[2],e[2]=e[6],e[6]=t,t=e[5],e[5]=e[7],e[7]=t,this}getNormalMatrix(t){return this.setFromMatrix4(t).invert().transpose()}transposeIntoArray(t){let e=this.elements;return t[0]=e[0],t[1]=e[3],t[2]=e[6],t[3]=e[1],t[4]=e[4],t[5]=e[7],t[6]=e[2],t[7]=e[5],t[8]=e[8],this}setUvTransform(t,e,n,s,r,a,o){let l=Math.cos(r),c=Math.sin(r);return this.set(n*l,n*c,-n*(l*a+c*o)+a+t,-s*c,s*l,-s*(-c*a+l*o)+o+e,0,0,1),this}scale(t,e){return this.premultiply(Ra.makeScale(t,e)),this}rotate(t){return this.premultiply(Ra.makeRotation(-t)),this}translate(t,e){return this.premultiply(Ra.makeTranslation(t,e)),this}makeTranslation(t,e){return t.isVector2?this.set(1,0,t.x,0,1,t.y,0,0,1):this.set(1,0,t,0,1,e,0,0,1),this}makeRotation(t){let e=Math.cos(t),n=Math.sin(t);return this.set(e,-n,0,n,e,0,0,0,1),this}makeScale(t,e){return this.set(t,0,0,0,e,0,0,0,1),this}equals(t){let e=this.elements,n=t.elements;for(let s=0;s<9;s++)if(e[s]!==n[s])return!1;return!0}fromArray(t,e=0){for(let n=0;n<9;n++)this.elements[n]=t[n+e];return this}toArray(t=[],e=0){let n=this.elements;return t[e]=n[0],t[e+1]=n[1],t[e+2]=n[2],t[e+3]=n[3],t[e+4]=n[4],t[e+5]=n[5],t[e+6]=n[6],t[e+7]=n[7],t[e+8]=n[8],t}clone(){return new this.constructor().fromArray(this.elements)}},Ra=new Yt;function Uh(i){for(let t=i.length-1;t>=0;--t)if(i[t]>=65535)return!0;return!1}function $r(i){return document.createElementNS("http://www.w3.org/1999/xhtml",i)}function Rd(){let i=$r("canvas");return i.style.display="block",i}var Mc={};function Gs(i){i in Mc||(Mc[i]=!0,console.warn(i))}function Cd(i,t,e){return new Promise(function(n,s){function r(){switch(i.clientWaitSync(t,i.SYNC_FLUSH_COMMANDS_BIT,0)){case i.WAIT_FAILED:s();break;case i.TIMEOUT_EXPIRED:setTimeout(r,e);break;default:n()}}setTimeout(r,e)})}function Id(i){let t=i.elements;t[2]=.5*t[2]+.5*t[3],t[6]=.5*t[6]+.5*t[7],t[10]=.5*t[10]+.5*t[11],t[14]=.5*t[14]+.5*t[15]}function Pd(i){let t=i.elements;t[11]===-1?(t[10]=-t[10]-1,t[14]=-t[14]):(t[10]=-t[10],t[14]=-t[14]+1)}var ne={enabled:!0,workingColorSpace:Is,spaces:{},convert:function(i,t,e){return this.enabled===!1||t===e||!t||!e||(this.spaces[t].transfer===le&&(i.r=Yn(i.r),i.g=Yn(i.g),i.b=Yn(i.b)),this.spaces[t].primaries!==this.spaces[e].primaries&&(i.applyMatrix3(this.spaces[t].toXYZ),i.applyMatrix3(this.spaces[e].fromXYZ)),this.spaces[e].transfer===le&&(i.r=us(i.r),i.g=us(i.g),i.b=us(i.b))),i},fromWorkingColorSpace:function(i,t){return this.convert(i,this.workingColorSpace,t)},toWorkingColorSpace:function(i,t){return this.convert(i,t,this.workingColorSpace)},getPrimaries:function(i){return this.spaces[i].primaries},getTransfer:function(i){return i===hi?ga:this.spaces[i].transfer},getLuminanceCoefficients:function(i,t=this.workingColorSpace){return i.fromArray(this.spaces[t].luminanceCoefficients)},define:function(i){Object.assign(this.spaces,i)},_getMatrix:function(i,t,e){return i.copy(this.spaces[t].toXYZ).multiply(this.spaces[e].fromXYZ)},_getDrawingBufferColorSpace:function(i){return this.spaces[i].outputColorSpaceConfig.drawingBufferColorSpace},_getUnpackColorSpace:function(i=this.workingColorSpace){return this.spaces[i].workingColorSpaceConfig.unpackColorSpace}};function Yn(i){return i<.04045?i*.0773993808:Math.pow(i*.9478672986+.0521327014,2.4)}function us(i){return i<.0031308?i*12.92:1.055*Math.pow(i,.41666)-.055}var Sc=[.64,.33,.3,.6,.15,.06],Ec=[.2126,.7152,.0722],bc=[.3127,.329],wc=new Yt().set(.4123908,.3575843,.1804808,.212639,.7151687,.0721923,.0193308,.1191948,.9505322),Tc=new Yt().set(3.2409699,-1.5373832,-.4986108,-.9692436,1.8759675,.0415551,.0556301,-.203977,1.0569715);ne.define({[Is]:{primaries:Sc,whitePoint:bc,transfer:ga,toXYZ:wc,fromXYZ:Tc,luminanceCoefficients:Ec,workingColorSpaceConfig:{unpackColorSpace:Re},outputColorSpaceConfig:{drawingBufferColorSpace:Re}},[Re]:{primaries:Sc,whitePoint:bc,transfer:le,toXYZ:wc,fromXYZ:Tc,luminanceCoefficients:Ec,outputColorSpaceConfig:{drawingBufferColorSpace:Re}}});var qi,Ko=class{static getDataURL(t){if(/^data:/i.test(t.src)||typeof HTMLCanvasElement=="undefined")return t.src;let e;if(t instanceof HTMLCanvasElement)e=t;else{qi===void 0&&(qi=$r("canvas")),qi.width=t.width,qi.height=t.height;let n=qi.getContext("2d");t instanceof ImageData?n.putImageData(t,0,0):n.drawImage(t,0,0,t.width,t.height),e=qi}return e.width>2048||e.height>2048?(console.warn("THREE.ImageUtils.getDataURL: Image converted to jpg for performance reasons",t),e.toDataURL("image/jpeg",.6)):e.toDataURL("image/png")}static sRGBToLinear(t){if(typeof HTMLImageElement!="undefined"&&t instanceof HTMLImageElement||typeof HTMLCanvasElement!="undefined"&&t instanceof HTMLCanvasElement||typeof ImageBitmap!="undefined"&&t instanceof ImageBitmap){let e=$r("canvas");e.width=t.width,e.height=t.height;let n=e.getContext("2d");n.drawImage(t,0,0,t.width,t.height);let s=n.getImageData(0,0,t.width,t.height),r=s.data;for(let a=0;a<r.length;a++)r[a]=Yn(r[a]/255)*255;return n.putImageData(s,0,0),e}else if(t.data){let e=t.data.slice(0);for(let n=0;n<e.length;n++)e instanceof Uint8Array||e instanceof Uint8ClampedArray?e[n]=Math.floor(Yn(e[n]/255)*255):e[n]=Yn(e[n]);return{data:e,width:t.width,height:t.height}}else return console.warn("THREE.ImageUtils.sRGBToLinear(): Unsupported image type. No color space conversion applied."),t}},Fd=0,Jr=class{constructor(t=null){this.isSource=!0,Object.defineProperty(this,"id",{value:Fd++}),this.uuid=Gi(),this.data=t,this.dataReady=!0,this.version=0}set needsUpdate(t){t===!0&&this.version++}toJSON(t){let e=t===void 0||typeof t=="string";if(!e&&t.images[this.uuid]!==void 0)return t.images[this.uuid];let n={uuid:this.uuid,url:""},s=this.data;if(s!==null){let r;if(Array.isArray(s)){r=[];for(let a=0,o=s.length;a<o;a++)s[a].isDataTexture?r.push(Ca(s[a].image)):r.push(Ca(s[a]))}else r=Ca(s);n.url=r}return e||(t.images[this.uuid]=n),n}};function Ca(i){return typeof HTMLImageElement!="undefined"&&i instanceof HTMLImageElement||typeof HTMLCanvasElement!="undefined"&&i instanceof HTMLCanvasElement||typeof ImageBitmap!="undefined"&&i instanceof ImageBitmap?Ko.getDataURL(i):i.data?{data:Array.from(i.data),width:i.width,height:i.height,type:i.data.constructor.name}:(console.warn("THREE.Texture: Unable to serialize Texture."),{})}var Dd=0,$e=class i extends gi{constructor(t=i.DEFAULT_IMAGE,e=i.DEFAULT_MAPPING,n=Kn,s=Kn,r=fn,a=Xn,o=bn,l=Zn,c=i.DEFAULT_ANISOTROPY,h=hi){super(),this.isTexture=!0,Object.defineProperty(this,"id",{value:Dd++}),this.uuid=Gi(),this.name="",this.source=new Jr(t),this.mipmaps=[],this.mapping=e,this.channel=0,this.wrapS=n,this.wrapT=s,this.magFilter=r,this.minFilter=a,this.anisotropy=c,this.format=o,this.internalFormat=null,this.type=l,this.offset=new at(0,0),this.repeat=new at(1,1),this.center=new at(0,0),this.rotation=0,this.matrixAutoUpdate=!0,this.matrix=new Yt,this.generateMipmaps=!0,this.premultiplyAlpha=!1,this.flipY=!0,this.unpackAlignment=4,this.colorSpace=h,this.userData={},this.version=0,this.onUpdate=null,this.isRenderTargetTexture=!1,this.pmremVersion=0}get image(){return this.source.data}set image(t=null){this.source.data=t}updateMatrix(){this.matrix.setUvTransform(this.offset.x,this.offset.y,this.repeat.x,this.repeat.y,this.rotation,this.center.x,this.center.y)}clone(){return new this.constructor().copy(this)}copy(t){return this.name=t.name,this.source=t.source,this.mipmaps=t.mipmaps.slice(0),this.mapping=t.mapping,this.channel=t.channel,this.wrapS=t.wrapS,this.wrapT=t.wrapT,this.magFilter=t.magFilter,this.minFilter=t.minFilter,this.anisotropy=t.anisotropy,this.format=t.format,this.internalFormat=t.internalFormat,this.type=t.type,this.offset.copy(t.offset),this.repeat.copy(t.repeat),this.center.copy(t.center),this.rotation=t.rotation,this.matrixAutoUpdate=t.matrixAutoUpdate,this.matrix.copy(t.matrix),this.generateMipmaps=t.generateMipmaps,this.premultiplyAlpha=t.premultiplyAlpha,this.flipY=t.flipY,this.unpackAlignment=t.unpackAlignment,this.colorSpace=t.colorSpace,this.userData=JSON.parse(JSON.stringify(t.userData)),this.needsUpdate=!0,this}toJSON(t){let e=t===void 0||typeof t=="string";if(!e&&t.textures[this.uuid]!==void 0)return t.textures[this.uuid];let n={metadata:{version:4.6,type:"Texture",generator:"Texture.toJSON"},uuid:this.uuid,name:this.name,image:this.source.toJSON(t).uuid,mapping:this.mapping,channel:this.channel,repeat:[this.repeat.x,this.repeat.y],offset:[this.offset.x,this.offset.y],center:[this.center.x,this.center.y],rotation:this.rotation,wrap:[this.wrapS,this.wrapT],format:this.format,internalFormat:this.internalFormat,type:this.type,colorSpace:this.colorSpace,minFilter:this.minFilter,magFilter:this.magFilter,anisotropy:this.anisotropy,flipY:this.flipY,generateMipmaps:this.generateMipmaps,premultiplyAlpha:this.premultiplyAlpha,unpackAlignment:this.unpackAlignment};return Object.keys(this.userData).length>0&&(n.userData=this.userData),e||(t.textures[this.uuid]=n),n}dispose(){this.dispatchEvent({type:"dispose"})}transformUv(t){if(this.mapping!==Sh)return t;if(t.applyMatrix3(this.matrix),t.x<0||t.x>1)switch(this.wrapS){case mi:t.x=t.x-Math.floor(t.x);break;case Kn:t.x=t.x<0?0:1;break;case _o:Math.abs(Math.floor(t.x)%2)===1?t.x=Math.ceil(t.x)-t.x:t.x=t.x-Math.floor(t.x);break}if(t.y<0||t.y>1)switch(this.wrapT){case mi:t.y=t.y-Math.floor(t.y);break;case Kn:t.y=t.y<0?0:1;break;case _o:Math.abs(Math.floor(t.y)%2)===1?t.y=Math.ceil(t.y)-t.y:t.y=t.y-Math.floor(t.y);break}return this.flipY&&(t.y=1-t.y),t}set needsUpdate(t){t===!0&&(this.version++,this.source.needsUpdate=!0)}set needsPMREMUpdate(t){t===!0&&this.pmremVersion++}};$e.DEFAULT_IMAGE=null;$e.DEFAULT_MAPPING=Sh;$e.DEFAULT_ANISOTROPY=1;var ce=class i{constructor(t=0,e=0,n=0,s=1){i.prototype.isVector4=!0,this.x=t,this.y=e,this.z=n,this.w=s}get width(){return this.z}set width(t){this.z=t}get height(){return this.w}set height(t){this.w=t}set(t,e,n,s){return this.x=t,this.y=e,this.z=n,this.w=s,this}setScalar(t){return this.x=t,this.y=t,this.z=t,this.w=t,this}setX(t){return this.x=t,this}setY(t){return this.y=t,this}setZ(t){return this.z=t,this}setW(t){return this.w=t,this}setComponent(t,e){switch(t){case 0:this.x=e;break;case 1:this.y=e;break;case 2:this.z=e;break;case 3:this.w=e;break;default:throw new Error("index is out of range: "+t)}return this}getComponent(t){switch(t){case 0:return this.x;case 1:return this.y;case 2:return this.z;case 3:return this.w;default:throw new Error("index is out of range: "+t)}}clone(){return new this.constructor(this.x,this.y,this.z,this.w)}copy(t){return this.x=t.x,this.y=t.y,this.z=t.z,this.w=t.w!==void 0?t.w:1,this}add(t){return this.x+=t.x,this.y+=t.y,this.z+=t.z,this.w+=t.w,this}addScalar(t){return this.x+=t,this.y+=t,this.z+=t,this.w+=t,this}addVectors(t,e){return this.x=t.x+e.x,this.y=t.y+e.y,this.z=t.z+e.z,this.w=t.w+e.w,this}addScaledVector(t,e){return this.x+=t.x*e,this.y+=t.y*e,this.z+=t.z*e,this.w+=t.w*e,this}sub(t){return this.x-=t.x,this.y-=t.y,this.z-=t.z,this.w-=t.w,this}subScalar(t){return this.x-=t,this.y-=t,this.z-=t,this.w-=t,this}subVectors(t,e){return this.x=t.x-e.x,this.y=t.y-e.y,this.z=t.z-e.z,this.w=t.w-e.w,this}multiply(t){return this.x*=t.x,this.y*=t.y,this.z*=t.z,this.w*=t.w,this}multiplyScalar(t){return this.x*=t,this.y*=t,this.z*=t,this.w*=t,this}applyMatrix4(t){let e=this.x,n=this.y,s=this.z,r=this.w,a=t.elements;return this.x=a[0]*e+a[4]*n+a[8]*s+a[12]*r,this.y=a[1]*e+a[5]*n+a[9]*s+a[13]*r,this.z=a[2]*e+a[6]*n+a[10]*s+a[14]*r,this.w=a[3]*e+a[7]*n+a[11]*s+a[15]*r,this}divide(t){return this.x/=t.x,this.y/=t.y,this.z/=t.z,this.w/=t.w,this}divideScalar(t){return this.multiplyScalar(1/t)}setAxisAngleFromQuaternion(t){this.w=2*Math.acos(t.w);let e=Math.sqrt(1-t.w*t.w);return e<1e-4?(this.x=1,this.y=0,this.z=0):(this.x=t.x/e,this.y=t.y/e,this.z=t.z/e),this}setAxisAngleFromRotationMatrix(t){let e,n,s,r,l=t.elements,c=l[0],h=l[4],u=l[8],d=l[1],p=l[5],g=l[9],x=l[2],m=l[6],f=l[10];if(Math.abs(h-d)<.01&&Math.abs(u-x)<.01&&Math.abs(g-m)<.01){if(Math.abs(h+d)<.1&&Math.abs(u+x)<.1&&Math.abs(g+m)<.1&&Math.abs(c+p+f-3)<.1)return this.set(1,0,0,0),this;e=Math.PI;let _=(c+1)/2,M=(p+1)/2,C=(f+1)/2,A=(h+d)/4,E=(u+x)/4,b=(g+m)/4;return _>M&&_>C?_<.01?(n=0,s=.707106781,r=.707106781):(n=Math.sqrt(_),s=A/n,r=E/n):M>C?M<.01?(n=.707106781,s=0,r=.707106781):(s=Math.sqrt(M),n=A/s,r=b/s):C<.01?(n=.707106781,s=.707106781,r=0):(r=Math.sqrt(C),n=E/r,s=b/r),this.set(n,s,r,e),this}let y=Math.sqrt((m-g)*(m-g)+(u-x)*(u-x)+(d-h)*(d-h));return Math.abs(y)<.001&&(y=1),this.x=(m-g)/y,this.y=(u-x)/y,this.z=(d-h)/y,this.w=Math.acos((c+p+f-1)/2),this}setFromMatrixPosition(t){let e=t.elements;return this.x=e[12],this.y=e[13],this.z=e[14],this.w=e[15],this}min(t){return this.x=Math.min(this.x,t.x),this.y=Math.min(this.y,t.y),this.z=Math.min(this.z,t.z),this.w=Math.min(this.w,t.w),this}max(t){return this.x=Math.max(this.x,t.x),this.y=Math.max(this.y,t.y),this.z=Math.max(this.z,t.z),this.w=Math.max(this.w,t.w),this}clamp(t,e){return this.x=Math.max(t.x,Math.min(e.x,this.x)),this.y=Math.max(t.y,Math.min(e.y,this.y)),this.z=Math.max(t.z,Math.min(e.z,this.z)),this.w=Math.max(t.w,Math.min(e.w,this.w)),this}clampScalar(t,e){return this.x=Math.max(t,Math.min(e,this.x)),this.y=Math.max(t,Math.min(e,this.y)),this.z=Math.max(t,Math.min(e,this.z)),this.w=Math.max(t,Math.min(e,this.w)),this}clampLength(t,e){let n=this.length();return this.divideScalar(n||1).multiplyScalar(Math.max(t,Math.min(e,n)))}floor(){return this.x=Math.floor(this.x),this.y=Math.floor(this.y),this.z=Math.floor(this.z),this.w=Math.floor(this.w),this}ceil(){return this.x=Math.ceil(this.x),this.y=Math.ceil(this.y),this.z=Math.ceil(this.z),this.w=Math.ceil(this.w),this}round(){return this.x=Math.round(this.x),this.y=Math.round(this.y),this.z=Math.round(this.z),this.w=Math.round(this.w),this}roundToZero(){return this.x=Math.trunc(this.x),this.y=Math.trunc(this.y),this.z=Math.trunc(this.z),this.w=Math.trunc(this.w),this}negate(){return this.x=-this.x,this.y=-this.y,this.z=-this.z,this.w=-this.w,this}dot(t){return this.x*t.x+this.y*t.y+this.z*t.z+this.w*t.w}lengthSq(){return this.x*this.x+this.y*this.y+this.z*this.z+this.w*this.w}length(){return Math.sqrt(this.x*this.x+this.y*this.y+this.z*this.z+this.w*this.w)}manhattanLength(){return Math.abs(this.x)+Math.abs(this.y)+Math.abs(this.z)+Math.abs(this.w)}normalize(){return this.divideScalar(this.length()||1)}setLength(t){return this.normalize().multiplyScalar(t)}lerp(t,e){return this.x+=(t.x-this.x)*e,this.y+=(t.y-this.y)*e,this.z+=(t.z-this.z)*e,this.w+=(t.w-this.w)*e,this}lerpVectors(t,e,n){return this.x=t.x+(e.x-t.x)*n,this.y=t.y+(e.y-t.y)*n,this.z=t.z+(e.z-t.z)*n,this.w=t.w+(e.w-t.w)*n,this}equals(t){return t.x===this.x&&t.y===this.y&&t.z===this.z&&t.w===this.w}fromArray(t,e=0){return this.x=t[e],this.y=t[e+1],this.z=t[e+2],this.w=t[e+3],this}toArray(t=[],e=0){return t[e]=this.x,t[e+1]=this.y,t[e+2]=this.z,t[e+3]=this.w,t}fromBufferAttribute(t,e){return this.x=t.getX(e),this.y=t.getY(e),this.z=t.getZ(e),this.w=t.getW(e),this}random(){return this.x=Math.random(),this.y=Math.random(),this.z=Math.random(),this.w=Math.random(),this}*[Symbol.iterator](){yield this.x,yield this.y,yield this.z,yield this.w}},Xo=class extends gi{constructor(t=1,e=1,n={}){super(),this.isRenderTarget=!0,this.width=t,this.height=e,this.depth=1,this.scissor=new ce(0,0,t,e),this.scissorTest=!1,this.viewport=new ce(0,0,t,e);let s={width:t,height:e,depth:1};n=Object.assign({generateMipmaps:!1,internalFormat:null,minFilter:fn,depthBuffer:!0,stencilBuffer:!1,resolveDepthBuffer:!0,resolveStencilBuffer:!0,depthTexture:null,samples:0,count:1},n);let r=new $e(s,n.mapping,n.wrapS,n.wrapT,n.magFilter,n.minFilter,n.format,n.type,n.anisotropy,n.colorSpace);r.flipY=!1,r.generateMipmaps=n.generateMipmaps,r.internalFormat=n.internalFormat,this.textures=[];let a=n.count;for(let o=0;o<a;o++)this.textures[o]=r.clone(),this.textures[o].isRenderTargetTexture=!0;this.depthBuffer=n.depthBuffer,this.stencilBuffer=n.stencilBuffer,this.resolveDepthBuffer=n.resolveDepthBuffer,this.resolveStencilBuffer=n.resolveStencilBuffer,this.depthTexture=n.depthTexture,this.samples=n.samples}get texture(){return this.textures[0]}set texture(t){this.textures[0]=t}setSize(t,e,n=1){if(this.width!==t||this.height!==e||this.depth!==n){this.width=t,this.height=e,this.depth=n;for(let s=0,r=this.textures.length;s<r;s++)this.textures[s].image.width=t,this.textures[s].image.height=e,this.textures[s].image.depth=n;this.dispose()}this.viewport.set(0,0,t,e),this.scissor.set(0,0,t,e)}clone(){return new this.constructor().copy(this)}copy(t){this.width=t.width,this.height=t.height,this.depth=t.depth,this.scissor.copy(t.scissor),this.scissorTest=t.scissorTest,this.viewport.copy(t.viewport),this.textures.length=0;for(let n=0,s=t.textures.length;n<s;n++)this.textures[n]=t.textures[n].clone(),this.textures[n].isRenderTargetTexture=!0;let e=Object.assign({},t.texture.image);return this.texture.source=new Jr(e),this.depthBuffer=t.depthBuffer,this.stencilBuffer=t.stencilBuffer,this.resolveDepthBuffer=t.resolveDepthBuffer,this.resolveStencilBuffer=t.resolveStencilBuffer,t.depthTexture!==null&&(this.depthTexture=t.depthTexture.clone()),this.samples=t.samples,this}dispose(){this.dispatchEvent({type:"dispose"})}},$n=class extends Xo{constructor(t=1,e=1,n={}){super(t,e,n),this.isWebGLRenderTarget=!0}},Qr=class extends $e{constructor(t=null,e=1,n=1,s=1){super(null),this.isDataArrayTexture=!0,this.image={data:t,width:e,height:n,depth:s},this.magFilter=an,this.minFilter=an,this.wrapR=Kn,this.generateMipmaps=!1,this.flipY=!1,this.unpackAlignment=1,this.layerUpdates=new Set}addLayerUpdate(t){this.layerUpdates.add(t)}clearLayerUpdates(){this.layerUpdates.clear()}};var qo=class extends $e{constructor(t=null,e=1,n=1,s=1){super(null),this.isData3DTexture=!0,this.image={data:t,width:e,height:n,depth:s},this.magFilter=an,this.minFilter=an,this.wrapR=Kn,this.generateMipmaps=!1,this.flipY=!1,this.unpackAlignment=1}};var Dn=class{constructor(t=0,e=0,n=0,s=1){this.isQuaternion=!0,this._x=t,this._y=e,this._z=n,this._w=s}static slerpFlat(t,e,n,s,r,a,o){let l=n[s+0],c=n[s+1],h=n[s+2],u=n[s+3],d=r[a+0],p=r[a+1],g=r[a+2],x=r[a+3];if(o===0){t[e+0]=l,t[e+1]=c,t[e+2]=h,t[e+3]=u;return}if(o===1){t[e+0]=d,t[e+1]=p,t[e+2]=g,t[e+3]=x;return}if(u!==x||l!==d||c!==p||h!==g){let m=1-o,f=l*d+c*p+h*g+u*x,y=f>=0?1:-1,_=1-f*f;if(_>Number.EPSILON){let C=Math.sqrt(_),A=Math.atan2(C,f*y);m=Math.sin(m*A)/C,o=Math.sin(o*A)/C}let M=o*y;if(l=l*m+d*M,c=c*m+p*M,h=h*m+g*M,u=u*m+x*M,m===1-o){let C=1/Math.sqrt(l*l+c*c+h*h+u*u);l*=C,c*=C,h*=C,u*=C}}t[e]=l,t[e+1]=c,t[e+2]=h,t[e+3]=u}static multiplyQuaternionsFlat(t,e,n,s,r,a){let o=n[s],l=n[s+1],c=n[s+2],h=n[s+3],u=r[a],d=r[a+1],p=r[a+2],g=r[a+3];return t[e]=o*g+h*u+l*p-c*d,t[e+1]=l*g+h*d+c*u-o*p,t[e+2]=c*g+h*p+o*d-l*u,t[e+3]=h*g-o*u-l*d-c*p,t}get x(){return this._x}set x(t){this._x=t,this._onChangeCallback()}get y(){return this._y}set y(t){this._y=t,this._onChangeCallback()}get z(){return this._z}set z(t){this._z=t,this._onChangeCallback()}get w(){return this._w}set w(t){this._w=t,this._onChangeCallback()}set(t,e,n,s){return this._x=t,this._y=e,this._z=n,this._w=s,this._onChangeCallback(),this}clone(){return new this.constructor(this._x,this._y,this._z,this._w)}copy(t){return this._x=t.x,this._y=t.y,this._z=t.z,this._w=t.w,this._onChangeCallback(),this}setFromEuler(t,e=!0){let n=t._x,s=t._y,r=t._z,a=t._order,o=Math.cos,l=Math.sin,c=o(n/2),h=o(s/2),u=o(r/2),d=l(n/2),p=l(s/2),g=l(r/2);switch(a){case"XYZ":this._x=d*h*u+c*p*g,this._y=c*p*u-d*h*g,this._z=c*h*g+d*p*u,this._w=c*h*u-d*p*g;break;case"YXZ":this._x=d*h*u+c*p*g,this._y=c*p*u-d*h*g,this._z=c*h*g-d*p*u,this._w=c*h*u+d*p*g;break;case"ZXY":this._x=d*h*u-c*p*g,this._y=c*p*u+d*h*g,this._z=c*h*g+d*p*u,this._w=c*h*u-d*p*g;break;case"ZYX":this._x=d*h*u-c*p*g,this._y=c*p*u+d*h*g,this._z=c*h*g-d*p*u,this._w=c*h*u+d*p*g;break;case"YZX":this._x=d*h*u+c*p*g,this._y=c*p*u+d*h*g,this._z=c*h*g-d*p*u,this._w=c*h*u-d*p*g;break;case"XZY":this._x=d*h*u-c*p*g,this._y=c*p*u-d*h*g,this._z=c*h*g+d*p*u,this._w=c*h*u+d*p*g;break;default:console.warn("THREE.Quaternion: .setFromEuler() encountered an unknown order: "+a)}return e===!0&&this._onChangeCallback(),this}setFromAxisAngle(t,e){let n=e/2,s=Math.sin(n);return this._x=t.x*s,this._y=t.y*s,this._z=t.z*s,this._w=Math.cos(n),this._onChangeCallback(),this}setFromRotationMatrix(t){let e=t.elements,n=e[0],s=e[4],r=e[8],a=e[1],o=e[5],l=e[9],c=e[2],h=e[6],u=e[10],d=n+o+u;if(d>0){let p=.5/Math.sqrt(d+1);this._w=.25/p,this._x=(h-l)*p,this._y=(r-c)*p,this._z=(a-s)*p}else if(n>o&&n>u){let p=2*Math.sqrt(1+n-o-u);this._w=(h-l)/p,this._x=.25*p,this._y=(s+a)/p,this._z=(r+c)/p}else if(o>u){let p=2*Math.sqrt(1+o-n-u);this._w=(r-c)/p,this._x=(s+a)/p,this._y=.25*p,this._z=(l+h)/p}else{let p=2*Math.sqrt(1+u-n-o);this._w=(a-s)/p,this._x=(r+c)/p,this._y=(l+h)/p,this._z=.25*p}return this._onChangeCallback(),this}setFromUnitVectors(t,e){let n=t.dot(e)+1;return n<Number.EPSILON?(n=0,Math.abs(t.x)>Math.abs(t.z)?(this._x=-t.y,this._y=t.x,this._z=0,this._w=n):(this._x=0,this._y=-t.z,this._z=t.y,this._w=n)):(this._x=t.y*e.z-t.z*e.y,this._y=t.z*e.x-t.x*e.z,this._z=t.x*e.y-t.y*e.x,this._w=n),this.normalize()}angleTo(t){return 2*Math.acos(Math.abs(Ce(this.dot(t),-1,1)))}rotateTowards(t,e){let n=this.angleTo(t);if(n===0)return this;let s=Math.min(1,e/n);return this.slerp(t,s),this}identity(){return this.set(0,0,0,1)}invert(){return this.conjugate()}conjugate(){return this._x*=-1,this._y*=-1,this._z*=-1,this._onChangeCallback(),this}dot(t){return this._x*t._x+this._y*t._y+this._z*t._z+this._w*t._w}lengthSq(){return this._x*this._x+this._y*this._y+this._z*this._z+this._w*this._w}length(){return Math.sqrt(this._x*this._x+this._y*this._y+this._z*this._z+this._w*this._w)}normalize(){let t=this.length();return t===0?(this._x=0,this._y=0,this._z=0,this._w=1):(t=1/t,this._x=this._x*t,this._y=this._y*t,this._z=this._z*t,this._w=this._w*t),this._onChangeCallback(),this}multiply(t){return this.multiplyQuaternions(this,t)}premultiply(t){return this.multiplyQuaternions(t,this)}multiplyQuaternions(t,e){let n=t._x,s=t._y,r=t._z,a=t._w,o=e._x,l=e._y,c=e._z,h=e._w;return this._x=n*h+a*o+s*c-r*l,this._y=s*h+a*l+r*o-n*c,this._z=r*h+a*c+n*l-s*o,this._w=a*h-n*o-s*l-r*c,this._onChangeCallback(),this}slerp(t,e){if(e===0)return this;if(e===1)return this.copy(t);let n=this._x,s=this._y,r=this._z,a=this._w,o=a*t._w+n*t._x+s*t._y+r*t._z;if(o<0?(this._w=-t._w,this._x=-t._x,this._y=-t._y,this._z=-t._z,o=-o):this.copy(t),o>=1)return this._w=a,this._x=n,this._y=s,this._z=r,this;let l=1-o*o;if(l<=Number.EPSILON){let p=1-e;return this._w=p*a+e*this._w,this._x=p*n+e*this._x,this._y=p*s+e*this._y,this._z=p*r+e*this._z,this.normalize(),this}let c=Math.sqrt(l),h=Math.atan2(c,o),u=Math.sin((1-e)*h)/c,d=Math.sin(e*h)/c;return this._w=a*u+this._w*d,this._x=n*u+this._x*d,this._y=s*u+this._y*d,this._z=r*u+this._z*d,this._onChangeCallback(),this}slerpQuaternions(t,e,n){return this.copy(t).slerp(e,n)}random(){let t=2*Math.PI*Math.random(),e=2*Math.PI*Math.random(),n=Math.random(),s=Math.sqrt(1-n),r=Math.sqrt(n);return this.set(s*Math.sin(t),s*Math.cos(t),r*Math.sin(e),r*Math.cos(e))}equals(t){return t._x===this._x&&t._y===this._y&&t._z===this._z&&t._w===this._w}fromArray(t,e=0){return this._x=t[e],this._y=t[e+1],this._z=t[e+2],this._w=t[e+3],this._onChangeCallback(),this}toArray(t=[],e=0){return t[e]=this._x,t[e+1]=this._y,t[e+2]=this._z,t[e+3]=this._w,t}fromBufferAttribute(t,e){return this._x=t.getX(e),this._y=t.getY(e),this._z=t.getZ(e),this._w=t.getW(e),this._onChangeCallback(),this}toJSON(){return this.toArray()}_onChange(t){return this._onChangeCallback=t,this}_onChangeCallback(){}*[Symbol.iterator](){yield this._x,yield this._y,yield this._z,yield this._w}},L=class i{constructor(t=0,e=0,n=0){i.prototype.isVector3=!0,this.x=t,this.y=e,this.z=n}set(t,e,n){return n===void 0&&(n=this.z),this.x=t,this.y=e,this.z=n,this}setScalar(t){return this.x=t,this.y=t,this.z=t,this}setX(t){return this.x=t,this}setY(t){return this.y=t,this}setZ(t){return this.z=t,this}setComponent(t,e){switch(t){case 0:this.x=e;break;case 1:this.y=e;break;case 2:this.z=e;break;default:throw new Error("index is out of range: "+t)}return this}getComponent(t){switch(t){case 0:return this.x;case 1:return this.y;case 2:return this.z;default:throw new Error("index is out of range: "+t)}}clone(){return new this.constructor(this.x,this.y,this.z)}copy(t){return this.x=t.x,this.y=t.y,this.z=t.z,this}add(t){return this.x+=t.x,this.y+=t.y,this.z+=t.z,this}addScalar(t){return this.x+=t,this.y+=t,this.z+=t,this}addVectors(t,e){return this.x=t.x+e.x,this.y=t.y+e.y,this.z=t.z+e.z,this}addScaledVector(t,e){return this.x+=t.x*e,this.y+=t.y*e,this.z+=t.z*e,this}sub(t){return this.x-=t.x,this.y-=t.y,this.z-=t.z,this}subScalar(t){return this.x-=t,this.y-=t,this.z-=t,this}subVectors(t,e){return this.x=t.x-e.x,this.y=t.y-e.y,this.z=t.z-e.z,this}multiply(t){return this.x*=t.x,this.y*=t.y,this.z*=t.z,this}multiplyScalar(t){return this.x*=t,this.y*=t,this.z*=t,this}multiplyVectors(t,e){return this.x=t.x*e.x,this.y=t.y*e.y,this.z=t.z*e.z,this}applyEuler(t){return this.applyQuaternion(Ac.setFromEuler(t))}applyAxisAngle(t,e){return this.applyQuaternion(Ac.setFromAxisAngle(t,e))}applyMatrix3(t){let e=this.x,n=this.y,s=this.z,r=t.elements;return this.x=r[0]*e+r[3]*n+r[6]*s,this.y=r[1]*e+r[4]*n+r[7]*s,this.z=r[2]*e+r[5]*n+r[8]*s,this}applyNormalMatrix(t){return this.applyMatrix3(t).normalize()}applyMatrix4(t){let e=this.x,n=this.y,s=this.z,r=t.elements,a=1/(r[3]*e+r[7]*n+r[11]*s+r[15]);return this.x=(r[0]*e+r[4]*n+r[8]*s+r[12])*a,this.y=(r[1]*e+r[5]*n+r[9]*s+r[13])*a,this.z=(r[2]*e+r[6]*n+r[10]*s+r[14])*a,this}applyQuaternion(t){let e=this.x,n=this.y,s=this.z,r=t.x,a=t.y,o=t.z,l=t.w,c=2*(a*s-o*n),h=2*(o*e-r*s),u=2*(r*n-a*e);return this.x=e+l*c+a*u-o*h,this.y=n+l*h+o*c-r*u,this.z=s+l*u+r*h-a*c,this}project(t){return this.applyMatrix4(t.matrixWorldInverse).applyMatrix4(t.projectionMatrix)}unproject(t){return this.applyMatrix4(t.projectionMatrixInverse).applyMatrix4(t.matrixWorld)}transformDirection(t){let e=this.x,n=this.y,s=this.z,r=t.elements;return this.x=r[0]*e+r[4]*n+r[8]*s,this.y=r[1]*e+r[5]*n+r[9]*s,this.z=r[2]*e+r[6]*n+r[10]*s,this.normalize()}divide(t){return this.x/=t.x,this.y/=t.y,this.z/=t.z,this}divideScalar(t){return this.multiplyScalar(1/t)}min(t){return this.x=Math.min(this.x,t.x),this.y=Math.min(this.y,t.y),this.z=Math.min(this.z,t.z),this}max(t){return this.x=Math.max(this.x,t.x),this.y=Math.max(this.y,t.y),this.z=Math.max(this.z,t.z),this}clamp(t,e){return this.x=Math.max(t.x,Math.min(e.x,this.x)),this.y=Math.max(t.y,Math.min(e.y,this.y)),this.z=Math.max(t.z,Math.min(e.z,this.z)),this}clampScalar(t,e){return this.x=Math.max(t,Math.min(e,this.x)),this.y=Math.max(t,Math.min(e,this.y)),this.z=Math.max(t,Math.min(e,this.z)),this}clampLength(t,e){let n=this.length();return this.divideScalar(n||1).multiplyScalar(Math.max(t,Math.min(e,n)))}floor(){return this.x=Math.floor(this.x),this.y=Math.floor(this.y),this.z=Math.floor(this.z),this}ceil(){return this.x=Math.ceil(this.x),this.y=Math.ceil(this.y),this.z=Math.ceil(this.z),this}round(){return this.x=Math.round(this.x),this.y=Math.round(this.y),this.z=Math.round(this.z),this}roundToZero(){return this.x=Math.trunc(this.x),this.y=Math.trunc(this.y),this.z=Math.trunc(this.z),this}negate(){return this.x=-this.x,this.y=-this.y,this.z=-this.z,this}dot(t){return this.x*t.x+this.y*t.y+this.z*t.z}lengthSq(){return this.x*this.x+this.y*this.y+this.z*this.z}length(){return Math.sqrt(this.x*this.x+this.y*this.y+this.z*this.z)}manhattanLength(){return Math.abs(this.x)+Math.abs(this.y)+Math.abs(this.z)}normalize(){return this.divideScalar(this.length()||1)}setLength(t){return this.normalize().multiplyScalar(t)}lerp(t,e){return this.x+=(t.x-this.x)*e,this.y+=(t.y-this.y)*e,this.z+=(t.z-this.z)*e,this}lerpVectors(t,e,n){return this.x=t.x+(e.x-t.x)*n,this.y=t.y+(e.y-t.y)*n,this.z=t.z+(e.z-t.z)*n,this}cross(t){return this.crossVectors(this,t)}crossVectors(t,e){let n=t.x,s=t.y,r=t.z,a=e.x,o=e.y,l=e.z;return this.x=s*l-r*o,this.y=r*a-n*l,this.z=n*o-s*a,this}projectOnVector(t){let e=t.lengthSq();if(e===0)return this.set(0,0,0);let n=t.dot(this)/e;return this.copy(t).multiplyScalar(n)}projectOnPlane(t){return Ia.copy(this).projectOnVector(t),this.sub(Ia)}reflect(t){return this.sub(Ia.copy(t).multiplyScalar(2*this.dot(t)))}angleTo(t){let e=Math.sqrt(this.lengthSq()*t.lengthSq());if(e===0)return Math.PI/2;let n=this.dot(t)/e;return Math.acos(Ce(n,-1,1))}distanceTo(t){return Math.sqrt(this.distanceToSquared(t))}distanceToSquared(t){let e=this.x-t.x,n=this.y-t.y,s=this.z-t.z;return e*e+n*n+s*s}manhattanDistanceTo(t){return Math.abs(this.x-t.x)+Math.abs(this.y-t.y)+Math.abs(this.z-t.z)}setFromSpherical(t){return this.setFromSphericalCoords(t.radius,t.phi,t.theta)}setFromSphericalCoords(t,e,n){let s=Math.sin(e)*t;return this.x=s*Math.sin(n),this.y=Math.cos(e)*t,this.z=s*Math.cos(n),this}setFromCylindrical(t){return this.setFromCylindricalCoords(t.radius,t.theta,t.y)}setFromCylindricalCoords(t,e,n){return this.x=t*Math.sin(e),this.y=n,this.z=t*Math.cos(e),this}setFromMatrixPosition(t){let e=t.elements;return this.x=e[12],this.y=e[13],this.z=e[14],this}setFromMatrixScale(t){let e=this.setFromMatrixColumn(t,0).length(),n=this.setFromMatrixColumn(t,1).length(),s=this.setFromMatrixColumn(t,2).length();return this.x=e,this.y=n,this.z=s,this}setFromMatrixColumn(t,e){return this.fromArray(t.elements,e*4)}setFromMatrix3Column(t,e){return this.fromArray(t.elements,e*3)}setFromEuler(t){return this.x=t._x,this.y=t._y,this.z=t._z,this}setFromColor(t){return this.x=t.r,this.y=t.g,this.z=t.b,this}equals(t){return t.x===this.x&&t.y===this.y&&t.z===this.z}fromArray(t,e=0){return this.x=t[e],this.y=t[e+1],this.z=t[e+2],this}toArray(t=[],e=0){return t[e]=this.x,t[e+1]=this.y,t[e+2]=this.z,t}fromBufferAttribute(t,e){return this.x=t.getX(e),this.y=t.getY(e),this.z=t.getZ(e),this}random(){return this.x=Math.random(),this.y=Math.random(),this.z=Math.random(),this}randomDirection(){let t=Math.random()*Math.PI*2,e=Math.random()*2-1,n=Math.sqrt(1-e*e);return this.x=n*Math.cos(t),this.y=e,this.z=n*Math.sin(t),this}*[Symbol.iterator](){yield this.x,yield this.y,yield this.z}},Ia=new L,Ac=new Dn,wn=class{constructor(t=new L(1/0,1/0,1/0),e=new L(-1/0,-1/0,-1/0)){this.isBox3=!0,this.min=t,this.max=e}set(t,e){return this.min.copy(t),this.max.copy(e),this}setFromArray(t){this.makeEmpty();for(let e=0,n=t.length;e<n;e+=3)this.expandByPoint(Mn.fromArray(t,e));return this}setFromBufferAttribute(t){this.makeEmpty();for(let e=0,n=t.count;e<n;e++)this.expandByPoint(Mn.fromBufferAttribute(t,e));return this}setFromPoints(t){this.makeEmpty();for(let e=0,n=t.length;e<n;e++)this.expandByPoint(t[e]);return this}setFromCenterAndSize(t,e){let n=Mn.copy(e).multiplyScalar(.5);return this.min.copy(t).sub(n),this.max.copy(t).add(n),this}setFromObject(t,e=!1){return this.makeEmpty(),this.expandByObject(t,e)}clone(){return new this.constructor().copy(this)}copy(t){return this.min.copy(t.min),this.max.copy(t.max),this}makeEmpty(){return this.min.x=this.min.y=this.min.z=1/0,this.max.x=this.max.y=this.max.z=-1/0,this}isEmpty(){return this.max.x<this.min.x||this.max.y<this.min.y||this.max.z<this.min.z}getCenter(t){return this.isEmpty()?t.set(0,0,0):t.addVectors(this.min,this.max).multiplyScalar(.5)}getSize(t){return this.isEmpty()?t.set(0,0,0):t.subVectors(this.max,this.min)}expandByPoint(t){return this.min.min(t),this.max.max(t),this}expandByVector(t){return this.min.sub(t),this.max.add(t),this}expandByScalar(t){return this.min.addScalar(-t),this.max.addScalar(t),this}expandByObject(t,e=!1){t.updateWorldMatrix(!1,!1);let n=t.geometry;if(n!==void 0){let r=n.getAttribute("position");if(e===!0&&r!==void 0&&t.isInstancedMesh!==!0)for(let a=0,o=r.count;a<o;a++)t.isMesh===!0?t.getVertexPosition(a,Mn):Mn.fromBufferAttribute(r,a),Mn.applyMatrix4(t.matrixWorld),this.expandByPoint(Mn);else t.boundingBox!==void 0?(t.boundingBox===null&&t.computeBoundingBox(),xr.copy(t.boundingBox)):(n.boundingBox===null&&n.computeBoundingBox(),xr.copy(n.boundingBox)),xr.applyMatrix4(t.matrixWorld),this.union(xr)}let s=t.children;for(let r=0,a=s.length;r<a;r++)this.expandByObject(s[r],e);return this}containsPoint(t){return t.x>=this.min.x&&t.x<=this.max.x&&t.y>=this.min.y&&t.y<=this.max.y&&t.z>=this.min.z&&t.z<=this.max.z}containsBox(t){return this.min.x<=t.min.x&&t.max.x<=this.max.x&&this.min.y<=t.min.y&&t.max.y<=this.max.y&&this.min.z<=t.min.z&&t.max.z<=this.max.z}getParameter(t,e){return e.set((t.x-this.min.x)/(this.max.x-this.min.x),(t.y-this.min.y)/(this.max.y-this.min.y),(t.z-this.min.z)/(this.max.z-this.min.z))}intersectsBox(t){return t.max.x>=this.min.x&&t.min.x<=this.max.x&&t.max.y>=this.min.y&&t.min.y<=this.max.y&&t.max.z>=this.min.z&&t.min.z<=this.max.z}intersectsSphere(t){return this.clampPoint(t.center,Mn),Mn.distanceToSquared(t.center)<=t.radius*t.radius}intersectsPlane(t){let e,n;return t.normal.x>0?(e=t.normal.x*this.min.x,n=t.normal.x*this.max.x):(e=t.normal.x*this.max.x,n=t.normal.x*this.min.x),t.normal.y>0?(e+=t.normal.y*this.min.y,n+=t.normal.y*this.max.y):(e+=t.normal.y*this.max.y,n+=t.normal.y*this.min.y),t.normal.z>0?(e+=t.normal.z*this.min.z,n+=t.normal.z*this.max.z):(e+=t.normal.z*this.max.z,n+=t.normal.z*this.min.z),e<=-t.constant&&n>=-t.constant}intersectsTriangle(t){if(this.isEmpty())return!1;this.getCenter(Ns),yr.subVectors(this.max,Ns),Yi.subVectors(t.a,Ns),Zi.subVectors(t.b,Ns),$i.subVectors(t.c,Ns),si.subVectors(Zi,Yi),ri.subVectors($i,Zi),Ei.subVectors(Yi,$i);let e=[0,-si.z,si.y,0,-ri.z,ri.y,0,-Ei.z,Ei.y,si.z,0,-si.x,ri.z,0,-ri.x,Ei.z,0,-Ei.x,-si.y,si.x,0,-ri.y,ri.x,0,-Ei.y,Ei.x,0];return!Pa(e,Yi,Zi,$i,yr)||(e=[1,0,0,0,1,0,0,0,1],!Pa(e,Yi,Zi,$i,yr))?!1:(vr.crossVectors(si,ri),e=[vr.x,vr.y,vr.z],Pa(e,Yi,Zi,$i,yr))}clampPoint(t,e){return e.copy(t).clamp(this.min,this.max)}distanceToPoint(t){return this.clampPoint(t,Mn).distanceTo(t)}getBoundingSphere(t){return this.isEmpty()?t.makeEmpty():(this.getCenter(t.center),t.radius=this.getSize(Mn).length()*.5),t}intersect(t){return this.min.max(t.min),this.max.min(t.max),this.isEmpty()&&this.makeEmpty(),this}union(t){return this.min.min(t.min),this.max.max(t.max),this}applyMatrix4(t){return this.isEmpty()?this:(Bn[0].set(this.min.x,this.min.y,this.min.z).applyMatrix4(t),Bn[1].set(this.min.x,this.min.y,this.max.z).applyMatrix4(t),Bn[2].set(this.min.x,this.max.y,this.min.z).applyMatrix4(t),Bn[3].set(this.min.x,this.max.y,this.max.z).applyMatrix4(t),Bn[4].set(this.max.x,this.min.y,this.min.z).applyMatrix4(t),Bn[5].set(this.max.x,this.min.y,this.max.z).applyMatrix4(t),Bn[6].set(this.max.x,this.max.y,this.min.z).applyMatrix4(t),Bn[7].set(this.max.x,this.max.y,this.max.z).applyMatrix4(t),this.setFromPoints(Bn),this)}translate(t){return this.min.add(t),this.max.add(t),this}equals(t){return t.min.equals(this.min)&&t.max.equals(this.max)}},Bn=[new L,new L,new L,new L,new L,new L,new L,new L],Mn=new L,xr=new wn,Yi=new L,Zi=new L,$i=new L,si=new L,ri=new L,Ei=new L,Ns=new L,yr=new L,vr=new L,bi=new L;function Pa(i,t,e,n,s){for(let r=0,a=i.length-3;r<=a;r+=3){bi.fromArray(i,r);let o=s.x*Math.abs(bi.x)+s.y*Math.abs(bi.y)+s.z*Math.abs(bi.z),l=t.dot(bi),c=e.dot(bi),h=n.dot(bi);if(Math.max(-Math.max(l,c,h),Math.min(l,c,h))>o)return!1}return!0}var Ld=new wn,Os=new L,Fa=new L,Li=class{constructor(t=new L,e=-1){this.isSphere=!0,this.center=t,this.radius=e}set(t,e){return this.center.copy(t),this.radius=e,this}setFromPoints(t,e){let n=this.center;e!==void 0?n.copy(e):Ld.setFromPoints(t).getCenter(n);let s=0;for(let r=0,a=t.length;r<a;r++)s=Math.max(s,n.distanceToSquared(t[r]));return this.radius=Math.sqrt(s),this}copy(t){return this.center.copy(t.center),this.radius=t.radius,this}isEmpty(){return this.radius<0}makeEmpty(){return this.center.set(0,0,0),this.radius=-1,this}containsPoint(t){return t.distanceToSquared(this.center)<=this.radius*this.radius}distanceToPoint(t){return t.distanceTo(this.center)-this.radius}intersectsSphere(t){let e=this.radius+t.radius;return t.center.distanceToSquared(this.center)<=e*e}intersectsBox(t){return t.intersectsSphere(this)}intersectsPlane(t){return Math.abs(t.distanceToPoint(this.center))<=this.radius}clampPoint(t,e){let n=this.center.distanceToSquared(t);return e.copy(t),n>this.radius*this.radius&&(e.sub(this.center).normalize(),e.multiplyScalar(this.radius).add(this.center)),e}getBoundingBox(t){return this.isEmpty()?(t.makeEmpty(),t):(t.set(this.center,this.center),t.expandByScalar(this.radius),t)}applyMatrix4(t){return this.center.applyMatrix4(t),this.radius=this.radius*t.getMaxScaleOnAxis(),this}translate(t){return this.center.add(t),this}expandByPoint(t){if(this.isEmpty())return this.center.copy(t),this.radius=0,this;Os.subVectors(t,this.center);let e=Os.lengthSq();if(e>this.radius*this.radius){let n=Math.sqrt(e),s=(n-this.radius)*.5;this.center.addScaledVector(Os,s/n),this.radius+=s}return this}union(t){return t.isEmpty()?this:this.isEmpty()?(this.copy(t),this):(this.center.equals(t.center)===!0?this.radius=Math.max(this.radius,t.radius):(Fa.subVectors(t.center,this.center).setLength(t.radius),this.expandByPoint(Os.copy(t.center).add(Fa)),this.expandByPoint(Os.copy(t.center).sub(Fa))),this)}equals(t){return t.center.equals(this.center)&&t.radius===this.radius}clone(){return new this.constructor().copy(this)}},zn=new L,Da=new L,Mr=new L,ai=new L,La=new L,Sr=new L,Ua=new L,Yo=class{constructor(t=new L,e=new L(0,0,-1)){this.origin=t,this.direction=e}set(t,e){return this.origin.copy(t),this.direction.copy(e),this}copy(t){return this.origin.copy(t.origin),this.direction.copy(t.direction),this}at(t,e){return e.copy(this.origin).addScaledVector(this.direction,t)}lookAt(t){return this.direction.copy(t).sub(this.origin).normalize(),this}recast(t){return this.origin.copy(this.at(t,zn)),this}closestPointToPoint(t,e){e.subVectors(t,this.origin);let n=e.dot(this.direction);return n<0?e.copy(this.origin):e.copy(this.origin).addScaledVector(this.direction,n)}distanceToPoint(t){return Math.sqrt(this.distanceSqToPoint(t))}distanceSqToPoint(t){let e=zn.subVectors(t,this.origin).dot(this.direction);return e<0?this.origin.distanceToSquared(t):(zn.copy(this.origin).addScaledVector(this.direction,e),zn.distanceToSquared(t))}distanceSqToSegment(t,e,n,s){Da.copy(t).add(e).multiplyScalar(.5),Mr.copy(e).sub(t).normalize(),ai.copy(this.origin).sub(Da);let r=t.distanceTo(e)*.5,a=-this.direction.dot(Mr),o=ai.dot(this.direction),l=-ai.dot(Mr),c=ai.lengthSq(),h=Math.abs(1-a*a),u,d,p,g;if(h>0)if(u=a*l-o,d=a*o-l,g=r*h,u>=0)if(d>=-g)if(d<=g){let x=1/h;u*=x,d*=x,p=u*(u+a*d+2*o)+d*(a*u+d+2*l)+c}else d=r,u=Math.max(0,-(a*d+o)),p=-u*u+d*(d+2*l)+c;else d=-r,u=Math.max(0,-(a*d+o)),p=-u*u+d*(d+2*l)+c;else d<=-g?(u=Math.max(0,-(-a*r+o)),d=u>0?-r:Math.min(Math.max(-r,-l),r),p=-u*u+d*(d+2*l)+c):d<=g?(u=0,d=Math.min(Math.max(-r,-l),r),p=d*(d+2*l)+c):(u=Math.max(0,-(a*r+o)),d=u>0?r:Math.min(Math.max(-r,-l),r),p=-u*u+d*(d+2*l)+c);else d=a>0?-r:r,u=Math.max(0,-(a*d+o)),p=-u*u+d*(d+2*l)+c;return n&&n.copy(this.origin).addScaledVector(this.direction,u),s&&s.copy(Da).addScaledVector(Mr,d),p}intersectSphere(t,e){zn.subVectors(t.center,this.origin);let n=zn.dot(this.direction),s=zn.dot(zn)-n*n,r=t.radius*t.radius;if(s>r)return null;let a=Math.sqrt(r-s),o=n-a,l=n+a;return l<0?null:o<0?this.at(l,e):this.at(o,e)}intersectsSphere(t){return this.distanceSqToPoint(t.center)<=t.radius*t.radius}distanceToPlane(t){let e=t.normal.dot(this.direction);if(e===0)return t.distanceToPoint(this.origin)===0?0:null;let n=-(this.origin.dot(t.normal)+t.constant)/e;return n>=0?n:null}intersectPlane(t,e){let n=this.distanceToPlane(t);return n===null?null:this.at(n,e)}intersectsPlane(t){let e=t.distanceToPoint(this.origin);return e===0||t.normal.dot(this.direction)*e<0}intersectBox(t,e){let n,s,r,a,o,l,c=1/this.direction.x,h=1/this.direction.y,u=1/this.direction.z,d=this.origin;return c>=0?(n=(t.min.x-d.x)*c,s=(t.max.x-d.x)*c):(n=(t.max.x-d.x)*c,s=(t.min.x-d.x)*c),h>=0?(r=(t.min.y-d.y)*h,a=(t.max.y-d.y)*h):(r=(t.max.y-d.y)*h,a=(t.min.y-d.y)*h),n>a||r>s||((r>n||isNaN(n))&&(n=r),(a<s||isNaN(s))&&(s=a),u>=0?(o=(t.min.z-d.z)*u,l=(t.max.z-d.z)*u):(o=(t.max.z-d.z)*u,l=(t.min.z-d.z)*u),n>l||o>s)||((o>n||n!==n)&&(n=o),(l<s||s!==s)&&(s=l),s<0)?null:this.at(n>=0?n:s,e)}intersectsBox(t){return this.intersectBox(t,zn)!==null}intersectTriangle(t,e,n,s,r){La.subVectors(e,t),Sr.subVectors(n,t),Ua.crossVectors(La,Sr);let a=this.direction.dot(Ua),o;if(a>0){if(s)return null;o=1}else if(a<0)o=-1,a=-a;else return null;ai.subVectors(this.origin,t);let l=o*this.direction.dot(Sr.crossVectors(ai,Sr));if(l<0)return null;let c=o*this.direction.dot(La.cross(ai));if(c<0||l+c>a)return null;let h=-o*ai.dot(Ua);return h<0?null:this.at(h/a,r)}applyMatrix4(t){return this.origin.applyMatrix4(t),this.direction.transformDirection(t),this}equals(t){return t.origin.equals(this.origin)&&t.direction.equals(this.direction)}clone(){return new this.constructor().copy(this)}},he=class i{constructor(t,e,n,s,r,a,o,l,c,h,u,d,p,g,x,m){i.prototype.isMatrix4=!0,this.elements=[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1],t!==void 0&&this.set(t,e,n,s,r,a,o,l,c,h,u,d,p,g,x,m)}set(t,e,n,s,r,a,o,l,c,h,u,d,p,g,x,m){let f=this.elements;return f[0]=t,f[4]=e,f[8]=n,f[12]=s,f[1]=r,f[5]=a,f[9]=o,f[13]=l,f[2]=c,f[6]=h,f[10]=u,f[14]=d,f[3]=p,f[7]=g,f[11]=x,f[15]=m,this}identity(){return this.set(1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1),this}clone(){return new i().fromArray(this.elements)}copy(t){let e=this.elements,n=t.elements;return e[0]=n[0],e[1]=n[1],e[2]=n[2],e[3]=n[3],e[4]=n[4],e[5]=n[5],e[6]=n[6],e[7]=n[7],e[8]=n[8],e[9]=n[9],e[10]=n[10],e[11]=n[11],e[12]=n[12],e[13]=n[13],e[14]=n[14],e[15]=n[15],this}copyPosition(t){let e=this.elements,n=t.elements;return e[12]=n[12],e[13]=n[13],e[14]=n[14],this}setFromMatrix3(t){let e=t.elements;return this.set(e[0],e[3],e[6],0,e[1],e[4],e[7],0,e[2],e[5],e[8],0,0,0,0,1),this}extractBasis(t,e,n){return t.setFromMatrixColumn(this,0),e.setFromMatrixColumn(this,1),n.setFromMatrixColumn(this,2),this}makeBasis(t,e,n){return this.set(t.x,e.x,n.x,0,t.y,e.y,n.y,0,t.z,e.z,n.z,0,0,0,0,1),this}extractRotation(t){let e=this.elements,n=t.elements,s=1/Ji.setFromMatrixColumn(t,0).length(),r=1/Ji.setFromMatrixColumn(t,1).length(),a=1/Ji.setFromMatrixColumn(t,2).length();return e[0]=n[0]*s,e[1]=n[1]*s,e[2]=n[2]*s,e[3]=0,e[4]=n[4]*r,e[5]=n[5]*r,e[6]=n[6]*r,e[7]=0,e[8]=n[8]*a,e[9]=n[9]*a,e[10]=n[10]*a,e[11]=0,e[12]=0,e[13]=0,e[14]=0,e[15]=1,this}makeRotationFromEuler(t){let e=this.elements,n=t.x,s=t.y,r=t.z,a=Math.cos(n),o=Math.sin(n),l=Math.cos(s),c=Math.sin(s),h=Math.cos(r),u=Math.sin(r);if(t.order==="XYZ"){let d=a*h,p=a*u,g=o*h,x=o*u;e[0]=l*h,e[4]=-l*u,e[8]=c,e[1]=p+g*c,e[5]=d-x*c,e[9]=-o*l,e[2]=x-d*c,e[6]=g+p*c,e[10]=a*l}else if(t.order==="YXZ"){let d=l*h,p=l*u,g=c*h,x=c*u;e[0]=d+x*o,e[4]=g*o-p,e[8]=a*c,e[1]=a*u,e[5]=a*h,e[9]=-o,e[2]=p*o-g,e[6]=x+d*o,e[10]=a*l}else if(t.order==="ZXY"){let d=l*h,p=l*u,g=c*h,x=c*u;e[0]=d-x*o,e[4]=-a*u,e[8]=g+p*o,e[1]=p+g*o,e[5]=a*h,e[9]=x-d*o,e[2]=-a*c,e[6]=o,e[10]=a*l}else if(t.order==="ZYX"){let d=a*h,p=a*u,g=o*h,x=o*u;e[0]=l*h,e[4]=g*c-p,e[8]=d*c+x,e[1]=l*u,e[5]=x*c+d,e[9]=p*c-g,e[2]=-c,e[6]=o*l,e[10]=a*l}else if(t.order==="YZX"){let d=a*l,p=a*c,g=o*l,x=o*c;e[0]=l*h,e[4]=x-d*u,e[8]=g*u+p,e[1]=u,e[5]=a*h,e[9]=-o*h,e[2]=-c*h,e[6]=p*u+g,e[10]=d-x*u}else if(t.order==="XZY"){let d=a*l,p=a*c,g=o*l,x=o*c;e[0]=l*h,e[4]=-u,e[8]=c*h,e[1]=d*u+x,e[5]=a*h,e[9]=p*u-g,e[2]=g*u-p,e[6]=o*h,e[10]=x*u+d}return e[3]=0,e[7]=0,e[11]=0,e[12]=0,e[13]=0,e[14]=0,e[15]=1,this}makeRotationFromQuaternion(t){return this.compose(Ud,t,Nd)}lookAt(t,e,n){let s=this.elements;return sn.subVectors(t,e),sn.lengthSq()===0&&(sn.z=1),sn.normalize(),oi.crossVectors(n,sn),oi.lengthSq()===0&&(Math.abs(n.z)===1?sn.x+=1e-4:sn.z+=1e-4,sn.normalize(),oi.crossVectors(n,sn)),oi.normalize(),Er.crossVectors(sn,oi),s[0]=oi.x,s[4]=Er.x,s[8]=sn.x,s[1]=oi.y,s[5]=Er.y,s[9]=sn.y,s[2]=oi.z,s[6]=Er.z,s[10]=sn.z,this}multiply(t){return this.multiplyMatrices(this,t)}premultiply(t){return this.multiplyMatrices(t,this)}multiplyMatrices(t,e){let n=t.elements,s=e.elements,r=this.elements,a=n[0],o=n[4],l=n[8],c=n[12],h=n[1],u=n[5],d=n[9],p=n[13],g=n[2],x=n[6],m=n[10],f=n[14],y=n[3],_=n[7],M=n[11],C=n[15],A=s[0],E=s[4],b=s[8],v=s[12],S=s[1],w=s[5],P=s[9],F=s[13],U=s[2],H=s[6],N=s[10],K=s[14],O=s[3],Y=s[7],tt=s[11],nt=s[15];return r[0]=a*A+o*S+l*U+c*O,r[4]=a*E+o*w+l*H+c*Y,r[8]=a*b+o*P+l*N+c*tt,r[12]=a*v+o*F+l*K+c*nt,r[1]=h*A+u*S+d*U+p*O,r[5]=h*E+u*w+d*H+p*Y,r[9]=h*b+u*P+d*N+p*tt,r[13]=h*v+u*F+d*K+p*nt,r[2]=g*A+x*S+m*U+f*O,r[6]=g*E+x*w+m*H+f*Y,r[10]=g*b+x*P+m*N+f*tt,r[14]=g*v+x*F+m*K+f*nt,r[3]=y*A+_*S+M*U+C*O,r[7]=y*E+_*w+M*H+C*Y,r[11]=y*b+_*P+M*N+C*tt,r[15]=y*v+_*F+M*K+C*nt,this}multiplyScalar(t){let e=this.elements;return e[0]*=t,e[4]*=t,e[8]*=t,e[12]*=t,e[1]*=t,e[5]*=t,e[9]*=t,e[13]*=t,e[2]*=t,e[6]*=t,e[10]*=t,e[14]*=t,e[3]*=t,e[7]*=t,e[11]*=t,e[15]*=t,this}determinant(){let t=this.elements,e=t[0],n=t[4],s=t[8],r=t[12],a=t[1],o=t[5],l=t[9],c=t[13],h=t[2],u=t[6],d=t[10],p=t[14],g=t[3],x=t[7],m=t[11],f=t[15];return g*(+r*l*u-s*c*u-r*o*d+n*c*d+s*o*p-n*l*p)+x*(+e*l*p-e*c*d+r*a*d-s*a*p+s*c*h-r*l*h)+m*(+e*c*u-e*o*p-r*a*u+n*a*p+r*o*h-n*c*h)+f*(-s*o*h-e*l*u+e*o*d+s*a*u-n*a*d+n*l*h)}transpose(){let t=this.elements,e;return e=t[1],t[1]=t[4],t[4]=e,e=t[2],t[2]=t[8],t[8]=e,e=t[6],t[6]=t[9],t[9]=e,e=t[3],t[3]=t[12],t[12]=e,e=t[7],t[7]=t[13],t[13]=e,e=t[11],t[11]=t[14],t[14]=e,this}setPosition(t,e,n){let s=this.elements;return t.isVector3?(s[12]=t.x,s[13]=t.y,s[14]=t.z):(s[12]=t,s[13]=e,s[14]=n),this}invert(){let t=this.elements,e=t[0],n=t[1],s=t[2],r=t[3],a=t[4],o=t[5],l=t[6],c=t[7],h=t[8],u=t[9],d=t[10],p=t[11],g=t[12],x=t[13],m=t[14],f=t[15],y=u*m*c-x*d*c+x*l*p-o*m*p-u*l*f+o*d*f,_=g*d*c-h*m*c-g*l*p+a*m*p+h*l*f-a*d*f,M=h*x*c-g*u*c+g*o*p-a*x*p-h*o*f+a*u*f,C=g*u*l-h*x*l-g*o*d+a*x*d+h*o*m-a*u*m,A=e*y+n*_+s*M+r*C;if(A===0)return this.set(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0);let E=1/A;return t[0]=y*E,t[1]=(x*d*r-u*m*r-x*s*p+n*m*p+u*s*f-n*d*f)*E,t[2]=(o*m*r-x*l*r+x*s*c-n*m*c-o*s*f+n*l*f)*E,t[3]=(u*l*r-o*d*r-u*s*c+n*d*c+o*s*p-n*l*p)*E,t[4]=_*E,t[5]=(h*m*r-g*d*r+g*s*p-e*m*p-h*s*f+e*d*f)*E,t[6]=(g*l*r-a*m*r-g*s*c+e*m*c+a*s*f-e*l*f)*E,t[7]=(a*d*r-h*l*r+h*s*c-e*d*c-a*s*p+e*l*p)*E,t[8]=M*E,t[9]=(g*u*r-h*x*r-g*n*p+e*x*p+h*n*f-e*u*f)*E,t[10]=(a*x*r-g*o*r+g*n*c-e*x*c-a*n*f+e*o*f)*E,t[11]=(h*o*r-a*u*r-h*n*c+e*u*c+a*n*p-e*o*p)*E,t[12]=C*E,t[13]=(h*x*s-g*u*s+g*n*d-e*x*d-h*n*m+e*u*m)*E,t[14]=(g*o*s-a*x*s-g*n*l+e*x*l+a*n*m-e*o*m)*E,t[15]=(a*u*s-h*o*s+h*n*l-e*u*l-a*n*d+e*o*d)*E,this}scale(t){let e=this.elements,n=t.x,s=t.y,r=t.z;return e[0]*=n,e[4]*=s,e[8]*=r,e[1]*=n,e[5]*=s,e[9]*=r,e[2]*=n,e[6]*=s,e[10]*=r,e[3]*=n,e[7]*=s,e[11]*=r,this}getMaxScaleOnAxis(){let t=this.elements,e=t[0]*t[0]+t[1]*t[1]+t[2]*t[2],n=t[4]*t[4]+t[5]*t[5]+t[6]*t[6],s=t[8]*t[8]+t[9]*t[9]+t[10]*t[10];return Math.sqrt(Math.max(e,n,s))}makeTranslation(t,e,n){return t.isVector3?this.set(1,0,0,t.x,0,1,0,t.y,0,0,1,t.z,0,0,0,1):this.set(1,0,0,t,0,1,0,e,0,0,1,n,0,0,0,1),this}makeRotationX(t){let e=Math.cos(t),n=Math.sin(t);return this.set(1,0,0,0,0,e,-n,0,0,n,e,0,0,0,0,1),this}makeRotationY(t){let e=Math.cos(t),n=Math.sin(t);return this.set(e,0,n,0,0,1,0,0,-n,0,e,0,0,0,0,1),this}makeRotationZ(t){let e=Math.cos(t),n=Math.sin(t);return this.set(e,-n,0,0,n,e,0,0,0,0,1,0,0,0,0,1),this}makeRotationAxis(t,e){let n=Math.cos(e),s=Math.sin(e),r=1-n,a=t.x,o=t.y,l=t.z,c=r*a,h=r*o;return this.set(c*a+n,c*o-s*l,c*l+s*o,0,c*o+s*l,h*o+n,h*l-s*a,0,c*l-s*o,h*l+s*a,r*l*l+n,0,0,0,0,1),this}makeScale(t,e,n){return this.set(t,0,0,0,0,e,0,0,0,0,n,0,0,0,0,1),this}makeShear(t,e,n,s,r,a){return this.set(1,n,r,0,t,1,a,0,e,s,1,0,0,0,0,1),this}compose(t,e,n){let s=this.elements,r=e._x,a=e._y,o=e._z,l=e._w,c=r+r,h=a+a,u=o+o,d=r*c,p=r*h,g=r*u,x=a*h,m=a*u,f=o*u,y=l*c,_=l*h,M=l*u,C=n.x,A=n.y,E=n.z;return s[0]=(1-(x+f))*C,s[1]=(p+M)*C,s[2]=(g-_)*C,s[3]=0,s[4]=(p-M)*A,s[5]=(1-(d+f))*A,s[6]=(m+y)*A,s[7]=0,s[8]=(g+_)*E,s[9]=(m-y)*E,s[10]=(1-(d+x))*E,s[11]=0,s[12]=t.x,s[13]=t.y,s[14]=t.z,s[15]=1,this}decompose(t,e,n){let s=this.elements,r=Ji.set(s[0],s[1],s[2]).length(),a=Ji.set(s[4],s[5],s[6]).length(),o=Ji.set(s[8],s[9],s[10]).length();this.determinant()<0&&(r=-r),t.x=s[12],t.y=s[13],t.z=s[14],Sn.copy(this);let c=1/r,h=1/a,u=1/o;return Sn.elements[0]*=c,Sn.elements[1]*=c,Sn.elements[2]*=c,Sn.elements[4]*=h,Sn.elements[5]*=h,Sn.elements[6]*=h,Sn.elements[8]*=u,Sn.elements[9]*=u,Sn.elements[10]*=u,e.setFromRotationMatrix(Sn),n.x=r,n.y=a,n.z=o,this}makePerspective(t,e,n,s,r,a,o=qn){let l=this.elements,c=2*r/(e-t),h=2*r/(n-s),u=(e+t)/(e-t),d=(n+s)/(n-s),p,g;if(o===qn)p=-(a+r)/(a-r),g=-2*a*r/(a-r);else if(o===Zr)p=-a/(a-r),g=-a*r/(a-r);else throw new Error("THREE.Matrix4.makePerspective(): Invalid coordinate system: "+o);return l[0]=c,l[4]=0,l[8]=u,l[12]=0,l[1]=0,l[5]=h,l[9]=d,l[13]=0,l[2]=0,l[6]=0,l[10]=p,l[14]=g,l[3]=0,l[7]=0,l[11]=-1,l[15]=0,this}makeOrthographic(t,e,n,s,r,a,o=qn){let l=this.elements,c=1/(e-t),h=1/(n-s),u=1/(a-r),d=(e+t)*c,p=(n+s)*h,g,x;if(o===qn)g=(a+r)*u,x=-2*u;else if(o===Zr)g=r*u,x=-1*u;else throw new Error("THREE.Matrix4.makeOrthographic(): Invalid coordinate system: "+o);return l[0]=2*c,l[4]=0,l[8]=0,l[12]=-d,l[1]=0,l[5]=2*h,l[9]=0,l[13]=-p,l[2]=0,l[6]=0,l[10]=x,l[14]=-g,l[3]=0,l[7]=0,l[11]=0,l[15]=1,this}equals(t){let e=this.elements,n=t.elements;for(let s=0;s<16;s++)if(e[s]!==n[s])return!1;return!0}fromArray(t,e=0){for(let n=0;n<16;n++)this.elements[n]=t[n+e];return this}toArray(t=[],e=0){let n=this.elements;return t[e]=n[0],t[e+1]=n[1],t[e+2]=n[2],t[e+3]=n[3],t[e+4]=n[4],t[e+5]=n[5],t[e+6]=n[6],t[e+7]=n[7],t[e+8]=n[8],t[e+9]=n[9],t[e+10]=n[10],t[e+11]=n[11],t[e+12]=n[12],t[e+13]=n[13],t[e+14]=n[14],t[e+15]=n[15],t}},Ji=new L,Sn=new he,Ud=new L(0,0,0),Nd=new L(1,1,1),oi=new L,Er=new L,sn=new L,Rc=new he,Cc=new Dn,pn=class i{constructor(t=0,e=0,n=0,s=i.DEFAULT_ORDER){this.isEuler=!0,this._x=t,this._y=e,this._z=n,this._order=s}get x(){return this._x}set x(t){this._x=t,this._onChangeCallback()}get y(){return this._y}set y(t){this._y=t,this._onChangeCallback()}get z(){return this._z}set z(t){this._z=t,this._onChangeCallback()}get order(){return this._order}set order(t){this._order=t,this._onChangeCallback()}set(t,e,n,s=this._order){return this._x=t,this._y=e,this._z=n,this._order=s,this._onChangeCallback(),this}clone(){return new this.constructor(this._x,this._y,this._z,this._order)}copy(t){return this._x=t._x,this._y=t._y,this._z=t._z,this._order=t._order,this._onChangeCallback(),this}setFromRotationMatrix(t,e=this._order,n=!0){let s=t.elements,r=s[0],a=s[4],o=s[8],l=s[1],c=s[5],h=s[9],u=s[2],d=s[6],p=s[10];switch(e){case"XYZ":this._y=Math.asin(Ce(o,-1,1)),Math.abs(o)<.9999999?(this._x=Math.atan2(-h,p),this._z=Math.atan2(-a,r)):(this._x=Math.atan2(d,c),this._z=0);break;case"YXZ":this._x=Math.asin(-Ce(h,-1,1)),Math.abs(h)<.9999999?(this._y=Math.atan2(o,p),this._z=Math.atan2(l,c)):(this._y=Math.atan2(-u,r),this._z=0);break;case"ZXY":this._x=Math.asin(Ce(d,-1,1)),Math.abs(d)<.9999999?(this._y=Math.atan2(-u,p),this._z=Math.atan2(-a,c)):(this._y=0,this._z=Math.atan2(l,r));break;case"ZYX":this._y=Math.asin(-Ce(u,-1,1)),Math.abs(u)<.9999999?(this._x=Math.atan2(d,p),this._z=Math.atan2(l,r)):(this._x=0,this._z=Math.atan2(-a,c));break;case"YZX":this._z=Math.asin(Ce(l,-1,1)),Math.abs(l)<.9999999?(this._x=Math.atan2(-h,c),this._y=Math.atan2(-u,r)):(this._x=0,this._y=Math.atan2(o,p));break;case"XZY":this._z=Math.asin(-Ce(a,-1,1)),Math.abs(a)<.9999999?(this._x=Math.atan2(d,c),this._y=Math.atan2(o,r)):(this._x=Math.atan2(-h,p),this._y=0);break;default:console.warn("THREE.Euler: .setFromRotationMatrix() encountered an unknown order: "+e)}return this._order=e,n===!0&&this._onChangeCallback(),this}setFromQuaternion(t,e,n){return Rc.makeRotationFromQuaternion(t),this.setFromRotationMatrix(Rc,e,n)}setFromVector3(t,e=this._order){return this.set(t.x,t.y,t.z,e)}reorder(t){return Cc.setFromEuler(this),this.setFromQuaternion(Cc,t)}equals(t){return t._x===this._x&&t._y===this._y&&t._z===this._z&&t._order===this._order}fromArray(t){return this._x=t[0],this._y=t[1],this._z=t[2],t[3]!==void 0&&(this._order=t[3]),this._onChangeCallback(),this}toArray(t=[],e=0){return t[e]=this._x,t[e+1]=this._y,t[e+2]=this._z,t[e+3]=this._order,t}_onChange(t){return this._onChangeCallback=t,this}_onChangeCallback(){}*[Symbol.iterator](){yield this._x,yield this._y,yield this._z,yield this._order}};pn.DEFAULT_ORDER="XYZ";var jr=class{constructor(){this.mask=1}set(t){this.mask=(1<<t|0)>>>0}enable(t){this.mask|=1<<t|0}enableAll(){this.mask=-1}toggle(t){this.mask^=1<<t|0}disable(t){this.mask&=~(1<<t|0)}disableAll(){this.mask=0}test(t){return(this.mask&t.mask)!==0}isEnabled(t){return(this.mask&(1<<t|0))!==0}},Od=0,Ic=new L,Qi=new Dn,kn=new he,br=new L,Bs=new L,Bd=new L,zd=new Dn,Pc=new L(1,0,0),Fc=new L(0,1,0),Dc=new L(0,0,1),Lc={type:"added"},kd={type:"removed"},ji={type:"childadded",child:null},Na={type:"childremoved",child:null},He=class i extends gi{constructor(){super(),this.isObject3D=!0,Object.defineProperty(this,"id",{value:Od++}),this.uuid=Gi(),this.name="",this.type="Object3D",this.parent=null,this.children=[],this.up=i.DEFAULT_UP.clone();let t=new L,e=new pn,n=new Dn,s=new L(1,1,1);function r(){n.setFromEuler(e,!1)}function a(){e.setFromQuaternion(n,void 0,!1)}e._onChange(r),n._onChange(a),Object.defineProperties(this,{position:{configurable:!0,enumerable:!0,value:t},rotation:{configurable:!0,enumerable:!0,value:e},quaternion:{configurable:!0,enumerable:!0,value:n},scale:{configurable:!0,enumerable:!0,value:s},modelViewMatrix:{value:new he},normalMatrix:{value:new Yt}}),this.matrix=new he,this.matrixWorld=new he,this.matrixAutoUpdate=i.DEFAULT_MATRIX_AUTO_UPDATE,this.matrixWorldAutoUpdate=i.DEFAULT_MATRIX_WORLD_AUTO_UPDATE,this.matrixWorldNeedsUpdate=!1,this.layers=new jr,this.visible=!0,this.castShadow=!1,this.receiveShadow=!1,this.frustumCulled=!0,this.renderOrder=0,this.animations=[],this.userData={}}onBeforeShadow(){}onAfterShadow(){}onBeforeRender(){}onAfterRender(){}applyMatrix4(t){this.matrixAutoUpdate&&this.updateMatrix(),this.matrix.premultiply(t),this.matrix.decompose(this.position,this.quaternion,this.scale)}applyQuaternion(t){return this.quaternion.premultiply(t),this}setRotationFromAxisAngle(t,e){this.quaternion.setFromAxisAngle(t,e)}setRotationFromEuler(t){this.quaternion.setFromEuler(t,!0)}setRotationFromMatrix(t){this.quaternion.setFromRotationMatrix(t)}setRotationFromQuaternion(t){this.quaternion.copy(t)}rotateOnAxis(t,e){return Qi.setFromAxisAngle(t,e),this.quaternion.multiply(Qi),this}rotateOnWorldAxis(t,e){return Qi.setFromAxisAngle(t,e),this.quaternion.premultiply(Qi),this}rotateX(t){return this.rotateOnAxis(Pc,t)}rotateY(t){return this.rotateOnAxis(Fc,t)}rotateZ(t){return this.rotateOnAxis(Dc,t)}translateOnAxis(t,e){return Ic.copy(t).applyQuaternion(this.quaternion),this.position.add(Ic.multiplyScalar(e)),this}translateX(t){return this.translateOnAxis(Pc,t)}translateY(t){return this.translateOnAxis(Fc,t)}translateZ(t){return this.translateOnAxis(Dc,t)}localToWorld(t){return this.updateWorldMatrix(!0,!1),t.applyMatrix4(this.matrixWorld)}worldToLocal(t){return this.updateWorldMatrix(!0,!1),t.applyMatrix4(kn.copy(this.matrixWorld).invert())}lookAt(t,e,n){t.isVector3?br.copy(t):br.set(t,e,n);let s=this.parent;this.updateWorldMatrix(!0,!1),Bs.setFromMatrixPosition(this.matrixWorld),this.isCamera||this.isLight?kn.lookAt(Bs,br,this.up):kn.lookAt(br,Bs,this.up),this.quaternion.setFromRotationMatrix(kn),s&&(kn.extractRotation(s.matrixWorld),Qi.setFromRotationMatrix(kn),this.quaternion.premultiply(Qi.invert()))}add(t){if(arguments.length>1){for(let e=0;e<arguments.length;e++)this.add(arguments[e]);return this}return t===this?(console.error("THREE.Object3D.add: object can't be added as a child of itself.",t),this):(t&&t.isObject3D?(t.removeFromParent(),t.parent=this,this.children.push(t),t.dispatchEvent(Lc),ji.child=t,this.dispatchEvent(ji),ji.child=null):console.error("THREE.Object3D.add: object not an instance of THREE.Object3D.",t),this)}remove(t){if(arguments.length>1){for(let n=0;n<arguments.length;n++)this.remove(arguments[n]);return this}let e=this.children.indexOf(t);return e!==-1&&(t.parent=null,this.children.splice(e,1),t.dispatchEvent(kd),Na.child=t,this.dispatchEvent(Na),Na.child=null),this}removeFromParent(){let t=this.parent;return t!==null&&t.remove(this),this}clear(){return this.remove(...this.children)}attach(t){return this.updateWorldMatrix(!0,!1),kn.copy(this.matrixWorld).invert(),t.parent!==null&&(t.parent.updateWorldMatrix(!0,!1),kn.multiply(t.parent.matrixWorld)),t.applyMatrix4(kn),t.removeFromParent(),t.parent=this,this.children.push(t),t.updateWorldMatrix(!1,!0),t.dispatchEvent(Lc),ji.child=t,this.dispatchEvent(ji),ji.child=null,this}getObjectById(t){return this.getObjectByProperty("id",t)}getObjectByName(t){return this.getObjectByProperty("name",t)}getObjectByProperty(t,e){if(this[t]===e)return this;for(let n=0,s=this.children.length;n<s;n++){let a=this.children[n].getObjectByProperty(t,e);if(a!==void 0)return a}}getObjectsByProperty(t,e,n=[]){this[t]===e&&n.push(this);let s=this.children;for(let r=0,a=s.length;r<a;r++)s[r].getObjectsByProperty(t,e,n);return n}getWorldPosition(t){return this.updateWorldMatrix(!0,!1),t.setFromMatrixPosition(this.matrixWorld)}getWorldQuaternion(t){return this.updateWorldMatrix(!0,!1),this.matrixWorld.decompose(Bs,t,Bd),t}getWorldScale(t){return this.updateWorldMatrix(!0,!1),this.matrixWorld.decompose(Bs,zd,t),t}getWorldDirection(t){this.updateWorldMatrix(!0,!1);let e=this.matrixWorld.elements;return t.set(e[8],e[9],e[10]).normalize()}raycast(){}traverse(t){t(this);let e=this.children;for(let n=0,s=e.length;n<s;n++)e[n].traverse(t)}traverseVisible(t){if(this.visible===!1)return;t(this);let e=this.children;for(let n=0,s=e.length;n<s;n++)e[n].traverseVisible(t)}traverseAncestors(t){let e=this.parent;e!==null&&(t(e),e.traverseAncestors(t))}updateMatrix(){this.matrix.compose(this.position,this.quaternion,this.scale),this.matrixWorldNeedsUpdate=!0}updateMatrixWorld(t){this.matrixAutoUpdate&&this.updateMatrix(),(this.matrixWorldNeedsUpdate||t)&&(this.matrixWorldAutoUpdate===!0&&(this.parent===null?this.matrixWorld.copy(this.matrix):this.matrixWorld.multiplyMatrices(this.parent.matrixWorld,this.matrix)),this.matrixWorldNeedsUpdate=!1,t=!0);let e=this.children;for(let n=0,s=e.length;n<s;n++)e[n].updateMatrixWorld(t)}updateWorldMatrix(t,e){let n=this.parent;if(t===!0&&n!==null&&n.updateWorldMatrix(!0,!1),this.matrixAutoUpdate&&this.updateMatrix(),this.matrixWorldAutoUpdate===!0&&(this.parent===null?this.matrixWorld.copy(this.matrix):this.matrixWorld.multiplyMatrices(this.parent.matrixWorld,this.matrix)),e===!0){let s=this.children;for(let r=0,a=s.length;r<a;r++)s[r].updateWorldMatrix(!1,!0)}}toJSON(t){let e=t===void 0||typeof t=="string",n={};e&&(t={geometries:{},materials:{},textures:{},images:{},shapes:{},skeletons:{},animations:{},nodes:{}},n.metadata={version:4.6,type:"Object",generator:"Object3D.toJSON"});let s={};s.uuid=this.uuid,s.type=this.type,this.name!==""&&(s.name=this.name),this.castShadow===!0&&(s.castShadow=!0),this.receiveShadow===!0&&(s.receiveShadow=!0),this.visible===!1&&(s.visible=!1),this.frustumCulled===!1&&(s.frustumCulled=!1),this.renderOrder!==0&&(s.renderOrder=this.renderOrder),Object.keys(this.userData).length>0&&(s.userData=this.userData),s.layers=this.layers.mask,s.matrix=this.matrix.toArray(),s.up=this.up.toArray(),this.matrixAutoUpdate===!1&&(s.matrixAutoUpdate=!1),this.isInstancedMesh&&(s.type="InstancedMesh",s.count=this.count,s.instanceMatrix=this.instanceMatrix.toJSON(),this.instanceColor!==null&&(s.instanceColor=this.instanceColor.toJSON())),this.isBatchedMesh&&(s.type="BatchedMesh",s.perObjectFrustumCulled=this.perObjectFrustumCulled,s.sortObjects=this.sortObjects,s.drawRanges=this._drawRanges,s.reservedRanges=this._reservedRanges,s.visibility=this._visibility,s.active=this._active,s.bounds=this._bounds.map(o=>({boxInitialized:o.boxInitialized,boxMin:o.box.min.toArray(),boxMax:o.box.max.toArray(),sphereInitialized:o.sphereInitialized,sphereRadius:o.sphere.radius,sphereCenter:o.sphere.center.toArray()})),s.maxInstanceCount=this._maxInstanceCount,s.maxVertexCount=this._maxVertexCount,s.maxIndexCount=this._maxIndexCount,s.geometryInitialized=this._geometryInitialized,s.geometryCount=this._geometryCount,s.matricesTexture=this._matricesTexture.toJSON(t),this._colorsTexture!==null&&(s.colorsTexture=this._colorsTexture.toJSON(t)),this.boundingSphere!==null&&(s.boundingSphere={center:s.boundingSphere.center.toArray(),radius:s.boundingSphere.radius}),this.boundingBox!==null&&(s.boundingBox={min:s.boundingBox.min.toArray(),max:s.boundingBox.max.toArray()}));function r(o,l){return o[l.uuid]===void 0&&(o[l.uuid]=l.toJSON(t)),l.uuid}if(this.isScene)this.background&&(this.background.isColor?s.background=this.background.toJSON():this.background.isTexture&&(s.background=this.background.toJSON(t).uuid)),this.environment&&this.environment.isTexture&&this.environment.isRenderTargetTexture!==!0&&(s.environment=this.environment.toJSON(t).uuid);else if(this.isMesh||this.isLine||this.isPoints){s.geometry=r(t.geometries,this.geometry);let o=this.geometry.parameters;if(o!==void 0&&o.shapes!==void 0){let l=o.shapes;if(Array.isArray(l))for(let c=0,h=l.length;c<h;c++){let u=l[c];r(t.shapes,u)}else r(t.shapes,l)}}if(this.isSkinnedMesh&&(s.bindMode=this.bindMode,s.bindMatrix=this.bindMatrix.toArray(),this.skeleton!==void 0&&(r(t.skeletons,this.skeleton),s.skeleton=this.skeleton.uuid)),this.material!==void 0)if(Array.isArray(this.material)){let o=[];for(let l=0,c=this.material.length;l<c;l++)o.push(r(t.materials,this.material[l]));s.material=o}else s.material=r(t.materials,this.material);if(this.children.length>0){s.children=[];for(let o=0;o<this.children.length;o++)s.children.push(this.children[o].toJSON(t).object)}if(this.animations.length>0){s.animations=[];for(let o=0;o<this.animations.length;o++){let l=this.animations[o];s.animations.push(r(t.animations,l))}}if(e){let o=a(t.geometries),l=a(t.materials),c=a(t.textures),h=a(t.images),u=a(t.shapes),d=a(t.skeletons),p=a(t.animations),g=a(t.nodes);o.length>0&&(n.geometries=o),l.length>0&&(n.materials=l),c.length>0&&(n.textures=c),h.length>0&&(n.images=h),u.length>0&&(n.shapes=u),d.length>0&&(n.skeletons=d),p.length>0&&(n.animations=p),g.length>0&&(n.nodes=g)}return n.object=s,n;function a(o){let l=[];for(let c in o){let h=o[c];delete h.metadata,l.push(h)}return l}}clone(t){return new this.constructor().copy(this,t)}copy(t,e=!0){if(this.name=t.name,this.up.copy(t.up),this.position.copy(t.position),this.rotation.order=t.rotation.order,this.quaternion.copy(t.quaternion),this.scale.copy(t.scale),this.matrix.copy(t.matrix),this.matrixWorld.copy(t.matrixWorld),this.matrixAutoUpdate=t.matrixAutoUpdate,this.matrixWorldAutoUpdate=t.matrixWorldAutoUpdate,this.matrixWorldNeedsUpdate=t.matrixWorldNeedsUpdate,this.layers.mask=t.layers.mask,this.visible=t.visible,this.castShadow=t.castShadow,this.receiveShadow=t.receiveShadow,this.frustumCulled=t.frustumCulled,this.renderOrder=t.renderOrder,this.animations=t.animations.slice(),this.userData=JSON.parse(JSON.stringify(t.userData)),e===!0)for(let n=0;n<t.children.length;n++){let s=t.children[n];this.add(s.clone())}return this}};He.DEFAULT_UP=new L(0,1,0);He.DEFAULT_MATRIX_AUTO_UPDATE=!0;He.DEFAULT_MATRIX_WORLD_AUTO_UPDATE=!0;var En=new L,Hn=new L,Oa=new L,Vn=new L,ts=new L,es=new L,Uc=new L,Ba=new L,za=new L,ka=new L,Ha=new ce,Va=new ce,Ga=new ce,Pi=class i{constructor(t=new L,e=new L,n=new L){this.a=t,this.b=e,this.c=n}static getNormal(t,e,n,s){s.subVectors(n,e),En.subVectors(t,e),s.cross(En);let r=s.lengthSq();return r>0?s.multiplyScalar(1/Math.sqrt(r)):s.set(0,0,0)}static getBarycoord(t,e,n,s,r){En.subVectors(s,e),Hn.subVectors(n,e),Oa.subVectors(t,e);let a=En.dot(En),o=En.dot(Hn),l=En.dot(Oa),c=Hn.dot(Hn),h=Hn.dot(Oa),u=a*c-o*o;if(u===0)return r.set(0,0,0),null;let d=1/u,p=(c*l-o*h)*d,g=(a*h-o*l)*d;return r.set(1-p-g,g,p)}static containsPoint(t,e,n,s){return this.getBarycoord(t,e,n,s,Vn)===null?!1:Vn.x>=0&&Vn.y>=0&&Vn.x+Vn.y<=1}static getInterpolation(t,e,n,s,r,a,o,l){return this.getBarycoord(t,e,n,s,Vn)===null?(l.x=0,l.y=0,"z"in l&&(l.z=0),"w"in l&&(l.w=0),null):(l.setScalar(0),l.addScaledVector(r,Vn.x),l.addScaledVector(a,Vn.y),l.addScaledVector(o,Vn.z),l)}static getInterpolatedAttribute(t,e,n,s,r,a){return Ha.setScalar(0),Va.setScalar(0),Ga.setScalar(0),Ha.fromBufferAttribute(t,e),Va.fromBufferAttribute(t,n),Ga.fromBufferAttribute(t,s),a.setScalar(0),a.addScaledVector(Ha,r.x),a.addScaledVector(Va,r.y),a.addScaledVector(Ga,r.z),a}static isFrontFacing(t,e,n,s){return En.subVectors(n,e),Hn.subVectors(t,e),En.cross(Hn).dot(s)<0}set(t,e,n){return this.a.copy(t),this.b.copy(e),this.c.copy(n),this}setFromPointsAndIndices(t,e,n,s){return this.a.copy(t[e]),this.b.copy(t[n]),this.c.copy(t[s]),this}setFromAttributeAndIndices(t,e,n,s){return this.a.fromBufferAttribute(t,e),this.b.fromBufferAttribute(t,n),this.c.fromBufferAttribute(t,s),this}clone(){return new this.constructor().copy(this)}copy(t){return this.a.copy(t.a),this.b.copy(t.b),this.c.copy(t.c),this}getArea(){return En.subVectors(this.c,this.b),Hn.subVectors(this.a,this.b),En.cross(Hn).length()*.5}getMidpoint(t){return t.addVectors(this.a,this.b).add(this.c).multiplyScalar(1/3)}getNormal(t){return i.getNormal(this.a,this.b,this.c,t)}getPlane(t){return t.setFromCoplanarPoints(this.a,this.b,this.c)}getBarycoord(t,e){return i.getBarycoord(t,this.a,this.b,this.c,e)}getInterpolation(t,e,n,s,r){return i.getInterpolation(t,this.a,this.b,this.c,e,n,s,r)}containsPoint(t){return i.containsPoint(t,this.a,this.b,this.c)}isFrontFacing(t){return i.isFrontFacing(this.a,this.b,this.c,t)}intersectsBox(t){return t.intersectsTriangle(this)}closestPointToPoint(t,e){let n=this.a,s=this.b,r=this.c,a,o;ts.subVectors(s,n),es.subVectors(r,n),Ba.subVectors(t,n);let l=ts.dot(Ba),c=es.dot(Ba);if(l<=0&&c<=0)return e.copy(n);za.subVectors(t,s);let h=ts.dot(za),u=es.dot(za);if(h>=0&&u<=h)return e.copy(s);let d=l*u-h*c;if(d<=0&&l>=0&&h<=0)return a=l/(l-h),e.copy(n).addScaledVector(ts,a);ka.subVectors(t,r);let p=ts.dot(ka),g=es.dot(ka);if(g>=0&&p<=g)return e.copy(r);let x=p*c-l*g;if(x<=0&&c>=0&&g<=0)return o=c/(c-g),e.copy(n).addScaledVector(es,o);let m=h*g-p*u;if(m<=0&&u-h>=0&&p-g>=0)return Uc.subVectors(r,s),o=(u-h)/(u-h+(p-g)),e.copy(s).addScaledVector(Uc,o);let f=1/(m+x+d);return a=x*f,o=d*f,e.copy(n).addScaledVector(ts,a).addScaledVector(es,o)}equals(t){return t.a.equals(this.a)&&t.b.equals(this.b)&&t.c.equals(this.c)}},Nh={aliceblue:15792383,antiquewhite:16444375,aqua:65535,aquamarine:8388564,azure:15794175,beige:16119260,bisque:16770244,black:0,blanchedalmond:16772045,blue:255,blueviolet:9055202,brown:10824234,burlywood:14596231,cadetblue:6266528,chartreuse:8388352,chocolate:13789470,coral:16744272,cornflowerblue:6591981,cornsilk:16775388,crimson:14423100,cyan:65535,darkblue:139,darkcyan:35723,darkgoldenrod:12092939,darkgray:11119017,darkgreen:25600,darkgrey:11119017,darkkhaki:12433259,darkmagenta:9109643,darkolivegreen:5597999,darkorange:16747520,darkorchid:10040012,darkred:9109504,darksalmon:15308410,darkseagreen:9419919,darkslateblue:4734347,darkslategray:3100495,darkslategrey:3100495,darkturquoise:52945,darkviolet:9699539,deeppink:16716947,deepskyblue:49151,dimgray:6908265,dimgrey:6908265,dodgerblue:2003199,firebrick:11674146,floralwhite:16775920,forestgreen:2263842,fuchsia:16711935,gainsboro:14474460,ghostwhite:16316671,gold:16766720,goldenrod:14329120,gray:8421504,green:32768,greenyellow:11403055,grey:8421504,honeydew:15794160,hotpink:16738740,indianred:13458524,indigo:4915330,ivory:16777200,khaki:15787660,lavender:15132410,lavenderblush:16773365,lawngreen:8190976,lemonchiffon:16775885,lightblue:11393254,lightcoral:15761536,lightcyan:14745599,lightgoldenrodyellow:16448210,lightgray:13882323,lightgreen:9498256,lightgrey:13882323,lightpink:16758465,lightsalmon:16752762,lightseagreen:2142890,lightskyblue:8900346,lightslategray:7833753,lightslategrey:7833753,lightsteelblue:11584734,lightyellow:16777184,lime:65280,limegreen:3329330,linen:16445670,magenta:16711935,maroon:8388608,mediumaquamarine:6737322,mediumblue:205,mediumorchid:12211667,mediumpurple:9662683,mediumseagreen:3978097,mediumslateblue:8087790,mediumspringgreen:64154,mediumturquoise:4772300,mediumvioletred:13047173,midnightblue:1644912,mintcream:16121850,mistyrose:16770273,moccasin:16770229,navajowhite:16768685,navy:128,oldlace:16643558,olive:8421376,olivedrab:7048739,orange:16753920,orangered:16729344,orchid:14315734,palegoldenrod:15657130,palegreen:10025880,paleturquoise:11529966,palevioletred:14381203,papayawhip:16773077,peachpuff:16767673,peru:13468991,pink:16761035,plum:14524637,powderblue:11591910,purple:8388736,rebeccapurple:6697881,red:16711680,rosybrown:12357519,royalblue:4286945,saddlebrown:9127187,salmon:16416882,sandybrown:16032864,seagreen:3050327,seashell:16774638,sienna:10506797,silver:12632256,skyblue:8900331,slateblue:6970061,slategray:7372944,slategrey:7372944,snow:16775930,springgreen:65407,steelblue:4620980,tan:13808780,teal:32896,thistle:14204888,tomato:16737095,turquoise:4251856,violet:15631086,wheat:16113331,white:16777215,whitesmoke:16119285,yellow:16776960,yellowgreen:10145074},li={h:0,s:0,l:0},wr={h:0,s:0,l:0};function Wa(i,t,e){return e<0&&(e+=1),e>1&&(e-=1),e<1/6?i+(t-i)*6*e:e<1/2?t:e<2/3?i+(t-i)*6*(2/3-e):i}var Gt=class{constructor(t,e,n){return this.isColor=!0,this.r=1,this.g=1,this.b=1,this.set(t,e,n)}set(t,e,n){if(e===void 0&&n===void 0){let s=t;s&&s.isColor?this.copy(s):typeof s=="number"?this.setHex(s):typeof s=="string"&&this.setStyle(s)}else this.setRGB(t,e,n);return this}setScalar(t){return this.r=t,this.g=t,this.b=t,this}setHex(t,e=Re){return t=Math.floor(t),this.r=(t>>16&255)/255,this.g=(t>>8&255)/255,this.b=(t&255)/255,ne.toWorkingColorSpace(this,e),this}setRGB(t,e,n,s=ne.workingColorSpace){return this.r=t,this.g=e,this.b=n,ne.toWorkingColorSpace(this,s),this}setHSL(t,e,n,s=ne.workingColorSpace){if(t=Bl(t,1),e=Ce(e,0,1),n=Ce(n,0,1),e===0)this.r=this.g=this.b=n;else{let r=n<=.5?n*(1+e):n+e-n*e,a=2*n-r;this.r=Wa(a,r,t+1/3),this.g=Wa(a,r,t),this.b=Wa(a,r,t-1/3)}return ne.toWorkingColorSpace(this,s),this}setStyle(t,e=Re){function n(r){r!==void 0&&parseFloat(r)<1&&console.warn("THREE.Color: Alpha component of "+t+" will be ignored.")}let s;if(s=/^(\w+)\(([^\)]*)\)/.exec(t)){let r,a=s[1],o=s[2];switch(a){case"rgb":case"rgba":if(r=/^\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*(\d*\.?\d+)\s*)?$/.exec(o))return n(r[4]),this.setRGB(Math.min(255,parseInt(r[1],10))/255,Math.min(255,parseInt(r[2],10))/255,Math.min(255,parseInt(r[3],10))/255,e);if(r=/^\s*(\d+)\%\s*,\s*(\d+)\%\s*,\s*(\d+)\%\s*(?:,\s*(\d*\.?\d+)\s*)?$/.exec(o))return n(r[4]),this.setRGB(Math.min(100,parseInt(r[1],10))/100,Math.min(100,parseInt(r[2],10))/100,Math.min(100,parseInt(r[3],10))/100,e);break;case"hsl":case"hsla":if(r=/^\s*(\d*\.?\d+)\s*,\s*(\d*\.?\d+)\%\s*,\s*(\d*\.?\d+)\%\s*(?:,\s*(\d*\.?\d+)\s*)?$/.exec(o))return n(r[4]),this.setHSL(parseFloat(r[1])/360,parseFloat(r[2])/100,parseFloat(r[3])/100,e);break;default:console.warn("THREE.Color: Unknown color model "+t)}}else if(s=/^\#([A-Fa-f\d]+)$/.exec(t)){let r=s[1],a=r.length;if(a===3)return this.setRGB(parseInt(r.charAt(0),16)/15,parseInt(r.charAt(1),16)/15,parseInt(r.charAt(2),16)/15,e);if(a===6)return this.setHex(parseInt(r,16),e);console.warn("THREE.Color: Invalid hex color "+t)}else if(t&&t.length>0)return this.setColorName(t,e);return this}setColorName(t,e=Re){let n=Nh[t.toLowerCase()];return n!==void 0?this.setHex(n,e):console.warn("THREE.Color: Unknown color "+t),this}clone(){return new this.constructor(this.r,this.g,this.b)}copy(t){return this.r=t.r,this.g=t.g,this.b=t.b,this}copySRGBToLinear(t){return this.r=Yn(t.r),this.g=Yn(t.g),this.b=Yn(t.b),this}copyLinearToSRGB(t){return this.r=us(t.r),this.g=us(t.g),this.b=us(t.b),this}convertSRGBToLinear(){return this.copySRGBToLinear(this),this}convertLinearToSRGB(){return this.copyLinearToSRGB(this),this}getHex(t=Re){return ne.fromWorkingColorSpace(Be.copy(this),t),Math.round(Ce(Be.r*255,0,255))*65536+Math.round(Ce(Be.g*255,0,255))*256+Math.round(Ce(Be.b*255,0,255))}getHexString(t=Re){return("000000"+this.getHex(t).toString(16)).slice(-6)}getHSL(t,e=ne.workingColorSpace){ne.fromWorkingColorSpace(Be.copy(this),e);let n=Be.r,s=Be.g,r=Be.b,a=Math.max(n,s,r),o=Math.min(n,s,r),l,c,h=(o+a)/2;if(o===a)l=0,c=0;else{let u=a-o;switch(c=h<=.5?u/(a+o):u/(2-a-o),a){case n:l=(s-r)/u+(s<r?6:0);break;case s:l=(r-n)/u+2;break;case r:l=(n-s)/u+4;break}l/=6}return t.h=l,t.s=c,t.l=h,t}getRGB(t,e=ne.workingColorSpace){return ne.fromWorkingColorSpace(Be.copy(this),e),t.r=Be.r,t.g=Be.g,t.b=Be.b,t}getStyle(t=Re){ne.fromWorkingColorSpace(Be.copy(this),t);let e=Be.r,n=Be.g,s=Be.b;return t!==Re?`color(${t} ${e.toFixed(3)} ${n.toFixed(3)} ${s.toFixed(3)})`:`rgb(${Math.round(e*255)},${Math.round(n*255)},${Math.round(s*255)})`}offsetHSL(t,e,n){return this.getHSL(li),this.setHSL(li.h+t,li.s+e,li.l+n)}add(t){return this.r+=t.r,this.g+=t.g,this.b+=t.b,this}addColors(t,e){return this.r=t.r+e.r,this.g=t.g+e.g,this.b=t.b+e.b,this}addScalar(t){return this.r+=t,this.g+=t,this.b+=t,this}sub(t){return this.r=Math.max(0,this.r-t.r),this.g=Math.max(0,this.g-t.g),this.b=Math.max(0,this.b-t.b),this}multiply(t){return this.r*=t.r,this.g*=t.g,this.b*=t.b,this}multiplyScalar(t){return this.r*=t,this.g*=t,this.b*=t,this}lerp(t,e){return this.r+=(t.r-this.r)*e,this.g+=(t.g-this.g)*e,this.b+=(t.b-this.b)*e,this}lerpColors(t,e,n){return this.r=t.r+(e.r-t.r)*n,this.g=t.g+(e.g-t.g)*n,this.b=t.b+(e.b-t.b)*n,this}lerpHSL(t,e){this.getHSL(li),t.getHSL(wr);let n=Xs(li.h,wr.h,e),s=Xs(li.s,wr.s,e),r=Xs(li.l,wr.l,e);return this.setHSL(n,s,r),this}setFromVector3(t){return this.r=t.x,this.g=t.y,this.b=t.z,this}applyMatrix3(t){let e=this.r,n=this.g,s=this.b,r=t.elements;return this.r=r[0]*e+r[3]*n+r[6]*s,this.g=r[1]*e+r[4]*n+r[7]*s,this.b=r[2]*e+r[5]*n+r[8]*s,this}equals(t){return t.r===this.r&&t.g===this.g&&t.b===this.b}fromArray(t,e=0){return this.r=t[e],this.g=t[e+1],this.b=t[e+2],this}toArray(t=[],e=0){return t[e]=this.r,t[e+1]=this.g,t[e+2]=this.b,t}fromBufferAttribute(t,e){return this.r=t.getX(e),this.g=t.getY(e),this.b=t.getZ(e),this}toJSON(){return this.getHex()}*[Symbol.iterator](){yield this.r,yield this.g,yield this.b}},Be=new Gt;Gt.NAMES=Nh;var Hd=0,Ui=class extends gi{static get type(){return"Material"}get type(){return this.constructor.type}set type(t){}constructor(){super(),this.isMaterial=!0,Object.defineProperty(this,"id",{value:Hd++}),this.uuid=Gi(),this.name="",this.blending=Fn,this.side=fi,this.vertexColors=!1,this.opacity=1,this.transparent=!1,this.alphaHash=!1,this.blendSrc=ro,this.blendDst=ao,this.blendEquation=Ii,this.blendSrcAlpha=null,this.blendDstAlpha=null,this.blendEquationAlpha=null,this.blendColor=new Gt(0,0,0),this.blendAlpha=0,this.depthFunc=fs,this.depthTest=!0,this.depthWrite=!0,this.stencilWriteMask=255,this.stencilFunc=_c,this.stencilRef=0,this.stencilFuncMask=255,this.stencilFail=Xi,this.stencilZFail=Xi,this.stencilZPass=Xi,this.stencilWrite=!1,this.clippingPlanes=null,this.clipIntersection=!1,this.clipShadows=!1,this.shadowSide=null,this.colorWrite=!0,this.precision=null,this.polygonOffset=!1,this.polygonOffsetFactor=0,this.polygonOffsetUnits=0,this.dithering=!1,this.alphaToCoverage=!1,this.premultipliedAlpha=!1,this.forceSinglePass=!1,this.visible=!0,this.toneMapped=!0,this.userData={},this.version=0,this._alphaTest=0}get alphaTest(){return this._alphaTest}set alphaTest(t){this._alphaTest>0!=t>0&&this.version++,this._alphaTest=t}onBeforeRender(){}onBeforeCompile(){}customProgramCacheKey(){return this.onBeforeCompile.toString()}setValues(t){if(t!==void 0)for(let e in t){let n=t[e];if(n===void 0){console.warn(`THREE.Material: parameter '${e}' has value of undefined.`);continue}let s=this[e];if(s===void 0){console.warn(`THREE.Material: '${e}' is not a property of THREE.${this.type}.`);continue}s&&s.isColor?s.set(n):s&&s.isVector3&&n&&n.isVector3?s.copy(n):this[e]=n}}toJSON(t){let e=t===void 0||typeof t=="string";e&&(t={textures:{},images:{}});let n={metadata:{version:4.6,type:"Material",generator:"Material.toJSON"}};n.uuid=this.uuid,n.type=this.type,this.name!==""&&(n.name=this.name),this.color&&this.color.isColor&&(n.color=this.color.getHex()),this.roughness!==void 0&&(n.roughness=this.roughness),this.metalness!==void 0&&(n.metalness=this.metalness),this.sheen!==void 0&&(n.sheen=this.sheen),this.sheenColor&&this.sheenColor.isColor&&(n.sheenColor=this.sheenColor.getHex()),this.sheenRoughness!==void 0&&(n.sheenRoughness=this.sheenRoughness),this.emissive&&this.emissive.isColor&&(n.emissive=this.emissive.getHex()),this.emissiveIntensity!==void 0&&this.emissiveIntensity!==1&&(n.emissiveIntensity=this.emissiveIntensity),this.specular&&this.specular.isColor&&(n.specular=this.specular.getHex()),this.specularIntensity!==void 0&&(n.specularIntensity=this.specularIntensity),this.specularColor&&this.specularColor.isColor&&(n.specularColor=this.specularColor.getHex()),this.shininess!==void 0&&(n.shininess=this.shininess),this.clearcoat!==void 0&&(n.clearcoat=this.clearcoat),this.clearcoatRoughness!==void 0&&(n.clearcoatRoughness=this.clearcoatRoughness),this.clearcoatMap&&this.clearcoatMap.isTexture&&(n.clearcoatMap=this.clearcoatMap.toJSON(t).uuid),this.clearcoatRoughnessMap&&this.clearcoatRoughnessMap.isTexture&&(n.clearcoatRoughnessMap=this.clearcoatRoughnessMap.toJSON(t).uuid),this.clearcoatNormalMap&&this.clearcoatNormalMap.isTexture&&(n.clearcoatNormalMap=this.clearcoatNormalMap.toJSON(t).uuid,n.clearcoatNormalScale=this.clearcoatNormalScale.toArray()),this.dispersion!==void 0&&(n.dispersion=this.dispersion),this.iridescence!==void 0&&(n.iridescence=this.iridescence),this.iridescenceIOR!==void 0&&(n.iridescenceIOR=this.iridescenceIOR),this.iridescenceThicknessRange!==void 0&&(n.iridescenceThicknessRange=this.iridescenceThicknessRange),this.iridescenceMap&&this.iridescenceMap.isTexture&&(n.iridescenceMap=this.iridescenceMap.toJSON(t).uuid),this.iridescenceThicknessMap&&this.iridescenceThicknessMap.isTexture&&(n.iridescenceThicknessMap=this.iridescenceThicknessMap.toJSON(t).uuid),this.anisotropy!==void 0&&(n.anisotropy=this.anisotropy),this.anisotropyRotation!==void 0&&(n.anisotropyRotation=this.anisotropyRotation),this.anisotropyMap&&this.anisotropyMap.isTexture&&(n.anisotropyMap=this.anisotropyMap.toJSON(t).uuid),this.map&&this.map.isTexture&&(n.map=this.map.toJSON(t).uuid),this.matcap&&this.matcap.isTexture&&(n.matcap=this.matcap.toJSON(t).uuid),this.alphaMap&&this.alphaMap.isTexture&&(n.alphaMap=this.alphaMap.toJSON(t).uuid),this.lightMap&&this.lightMap.isTexture&&(n.lightMap=this.lightMap.toJSON(t).uuid,n.lightMapIntensity=this.lightMapIntensity),this.aoMap&&this.aoMap.isTexture&&(n.aoMap=this.aoMap.toJSON(t).uuid,n.aoMapIntensity=this.aoMapIntensity),this.bumpMap&&this.bumpMap.isTexture&&(n.bumpMap=this.bumpMap.toJSON(t).uuid,n.bumpScale=this.bumpScale),this.normalMap&&this.normalMap.isTexture&&(n.normalMap=this.normalMap.toJSON(t).uuid,n.normalMapType=this.normalMapType,n.normalScale=this.normalScale.toArray()),this.displacementMap&&this.displacementMap.isTexture&&(n.displacementMap=this.displacementMap.toJSON(t).uuid,n.displacementScale=this.displacementScale,n.displacementBias=this.displacementBias),this.roughnessMap&&this.roughnessMap.isTexture&&(n.roughnessMap=this.roughnessMap.toJSON(t).uuid),this.metalnessMap&&this.metalnessMap.isTexture&&(n.metalnessMap=this.metalnessMap.toJSON(t).uuid),this.emissiveMap&&this.emissiveMap.isTexture&&(n.emissiveMap=this.emissiveMap.toJSON(t).uuid),this.specularMap&&this.specularMap.isTexture&&(n.specularMap=this.specularMap.toJSON(t).uuid),this.specularIntensityMap&&this.specularIntensityMap.isTexture&&(n.specularIntensityMap=this.specularIntensityMap.toJSON(t).uuid),this.specularColorMap&&this.specularColorMap.isTexture&&(n.specularColorMap=this.specularColorMap.toJSON(t).uuid),this.envMap&&this.envMap.isTexture&&(n.envMap=this.envMap.toJSON(t).uuid,this.combine!==void 0&&(n.combine=this.combine)),this.envMapRotation!==void 0&&(n.envMapRotation=this.envMapRotation.toArray()),this.envMapIntensity!==void 0&&(n.envMapIntensity=this.envMapIntensity),this.reflectivity!==void 0&&(n.reflectivity=this.reflectivity),this.refractionRatio!==void 0&&(n.refractionRatio=this.refractionRatio),this.gradientMap&&this.gradientMap.isTexture&&(n.gradientMap=this.gradientMap.toJSON(t).uuid),this.transmission!==void 0&&(n.transmission=this.transmission),this.transmissionMap&&this.transmissionMap.isTexture&&(n.transmissionMap=this.transmissionMap.toJSON(t).uuid),this.thickness!==void 0&&(n.thickness=this.thickness),this.thicknessMap&&this.thicknessMap.isTexture&&(n.thicknessMap=this.thicknessMap.toJSON(t).uuid),this.attenuationDistance!==void 0&&this.attenuationDistance!==1/0&&(n.attenuationDistance=this.attenuationDistance),this.attenuationColor!==void 0&&(n.attenuationColor=this.attenuationColor.getHex()),this.size!==void 0&&(n.size=this.size),this.shadowSide!==null&&(n.shadowSide=this.shadowSide),this.sizeAttenuation!==void 0&&(n.sizeAttenuation=this.sizeAttenuation),this.blending!==Fn&&(n.blending=this.blending),this.side!==fi&&(n.side=this.side),this.vertexColors===!0&&(n.vertexColors=!0),this.opacity<1&&(n.opacity=this.opacity),this.transparent===!0&&(n.transparent=!0),this.blendSrc!==ro&&(n.blendSrc=this.blendSrc),this.blendDst!==ao&&(n.blendDst=this.blendDst),this.blendEquation!==Ii&&(n.blendEquation=this.blendEquation),this.blendSrcAlpha!==null&&(n.blendSrcAlpha=this.blendSrcAlpha),this.blendDstAlpha!==null&&(n.blendDstAlpha=this.blendDstAlpha),this.blendEquationAlpha!==null&&(n.blendEquationAlpha=this.blendEquationAlpha),this.blendColor&&this.blendColor.isColor&&(n.blendColor=this.blendColor.getHex()),this.blendAlpha!==0&&(n.blendAlpha=this.blendAlpha),this.depthFunc!==fs&&(n.depthFunc=this.depthFunc),this.depthTest===!1&&(n.depthTest=this.depthTest),this.depthWrite===!1&&(n.depthWrite=this.depthWrite),this.colorWrite===!1&&(n.colorWrite=this.colorWrite),this.stencilWriteMask!==255&&(n.stencilWriteMask=this.stencilWriteMask),this.stencilFunc!==_c&&(n.stencilFunc=this.stencilFunc),this.stencilRef!==0&&(n.stencilRef=this.stencilRef),this.stencilFuncMask!==255&&(n.stencilFuncMask=this.stencilFuncMask),this.stencilFail!==Xi&&(n.stencilFail=this.stencilFail),this.stencilZFail!==Xi&&(n.stencilZFail=this.stencilZFail),this.stencilZPass!==Xi&&(n.stencilZPass=this.stencilZPass),this.stencilWrite===!0&&(n.stencilWrite=this.stencilWrite),this.rotation!==void 0&&this.rotation!==0&&(n.rotation=this.rotation),this.polygonOffset===!0&&(n.polygonOffset=!0),this.polygonOffsetFactor!==0&&(n.polygonOffsetFactor=this.polygonOffsetFactor),this.polygonOffsetUnits!==0&&(n.polygonOffsetUnits=this.polygonOffsetUnits),this.linewidth!==void 0&&this.linewidth!==1&&(n.linewidth=this.linewidth),this.dashSize!==void 0&&(n.dashSize=this.dashSize),this.gapSize!==void 0&&(n.gapSize=this.gapSize),this.scale!==void 0&&(n.scale=this.scale),this.dithering===!0&&(n.dithering=!0),this.alphaTest>0&&(n.alphaTest=this.alphaTest),this.alphaHash===!0&&(n.alphaHash=!0),this.alphaToCoverage===!0&&(n.alphaToCoverage=!0),this.premultipliedAlpha===!0&&(n.premultipliedAlpha=!0),this.forceSinglePass===!0&&(n.forceSinglePass=!0),this.wireframe===!0&&(n.wireframe=!0),this.wireframeLinewidth>1&&(n.wireframeLinewidth=this.wireframeLinewidth),this.wireframeLinecap!=="round"&&(n.wireframeLinecap=this.wireframeLinecap),this.wireframeLinejoin!=="round"&&(n.wireframeLinejoin=this.wireframeLinejoin),this.flatShading===!0&&(n.flatShading=!0),this.visible===!1&&(n.visible=!1),this.toneMapped===!1&&(n.toneMapped=!1),this.fog===!1&&(n.fog=!1),Object.keys(this.userData).length>0&&(n.userData=this.userData);function s(r){let a=[];for(let o in r){let l=r[o];delete l.metadata,a.push(l)}return a}if(e){let r=s(t.textures),a=s(t.images);r.length>0&&(n.textures=r),a.length>0&&(n.images=a)}return n}clone(){return new this.constructor().copy(this)}copy(t){this.name=t.name,this.blending=t.blending,this.side=t.side,this.vertexColors=t.vertexColors,this.opacity=t.opacity,this.transparent=t.transparent,this.blendSrc=t.blendSrc,this.blendDst=t.blendDst,this.blendEquation=t.blendEquation,this.blendSrcAlpha=t.blendSrcAlpha,this.blendDstAlpha=t.blendDstAlpha,this.blendEquationAlpha=t.blendEquationAlpha,this.blendColor.copy(t.blendColor),this.blendAlpha=t.blendAlpha,this.depthFunc=t.depthFunc,this.depthTest=t.depthTest,this.depthWrite=t.depthWrite,this.stencilWriteMask=t.stencilWriteMask,this.stencilFunc=t.stencilFunc,this.stencilRef=t.stencilRef,this.stencilFuncMask=t.stencilFuncMask,this.stencilFail=t.stencilFail,this.stencilZFail=t.stencilZFail,this.stencilZPass=t.stencilZPass,this.stencilWrite=t.stencilWrite;let e=t.clippingPlanes,n=null;if(e!==null){let s=e.length;n=new Array(s);for(let r=0;r!==s;++r)n[r]=e[r].clone()}return this.clippingPlanes=n,this.clipIntersection=t.clipIntersection,this.clipShadows=t.clipShadows,this.shadowSide=t.shadowSide,this.colorWrite=t.colorWrite,this.precision=t.precision,this.polygonOffset=t.polygonOffset,this.polygonOffsetFactor=t.polygonOffsetFactor,this.polygonOffsetUnits=t.polygonOffsetUnits,this.dithering=t.dithering,this.alphaTest=t.alphaTest,this.alphaHash=t.alphaHash,this.alphaToCoverage=t.alphaToCoverage,this.premultipliedAlpha=t.premultipliedAlpha,this.forceSinglePass=t.forceSinglePass,this.visible=t.visible,this.toneMapped=t.toneMapped,this.userData=JSON.parse(JSON.stringify(t.userData)),this}dispose(){this.dispatchEvent({type:"dispose"})}set needsUpdate(t){t===!0&&this.version++}onBuild(){console.warn("Material: onBuild() has been removed.")}},be=class extends Ui{static get type(){return"MeshBasicMaterial"}constructor(t){super(),this.isMeshBasicMaterial=!0,this.color=new Gt(16777215),this.map=null,this.lightMap=null,this.lightMapIntensity=1,this.aoMap=null,this.aoMapIntensity=1,this.specularMap=null,this.alphaMap=null,this.envMap=null,this.envMapRotation=new pn,this.combine=Mh,this.reflectivity=1,this.refractionRatio=.98,this.wireframe=!1,this.wireframeLinewidth=1,this.wireframeLinecap="round",this.wireframeLinejoin="round",this.fog=!0,this.setValues(t)}copy(t){return super.copy(t),this.color.copy(t.color),this.map=t.map,this.lightMap=t.lightMap,this.lightMapIntensity=t.lightMapIntensity,this.aoMap=t.aoMap,this.aoMapIntensity=t.aoMapIntensity,this.specularMap=t.specularMap,this.alphaMap=t.alphaMap,this.envMap=t.envMap,this.envMapRotation.copy(t.envMapRotation),this.combine=t.combine,this.reflectivity=t.reflectivity,this.refractionRatio=t.refractionRatio,this.wireframe=t.wireframe,this.wireframeLinewidth=t.wireframeLinewidth,this.wireframeLinecap=t.wireframeLinecap,this.wireframeLinejoin=t.wireframeLinejoin,this.fog=t.fog,this}};var Ee=new L,Tr=new at,ke=class{constructor(t,e,n=!1){if(Array.isArray(t))throw new TypeError("THREE.BufferAttribute: array should be a Typed Array.");this.isBufferAttribute=!0,this.name="",this.array=t,this.itemSize=e,this.count=t!==void 0?t.length/e:0,this.normalized=n,this.usage=xc,this.updateRanges=[],this.gpuType=Pn,this.version=0}onUploadCallback(){}set needsUpdate(t){t===!0&&this.version++}setUsage(t){return this.usage=t,this}addUpdateRange(t,e){this.updateRanges.push({start:t,count:e})}clearUpdateRanges(){this.updateRanges.length=0}copy(t){return this.name=t.name,this.array=new t.array.constructor(t.array),this.itemSize=t.itemSize,this.count=t.count,this.normalized=t.normalized,this.usage=t.usage,this.gpuType=t.gpuType,this}copyAt(t,e,n){t*=this.itemSize,n*=e.itemSize;for(let s=0,r=this.itemSize;s<r;s++)this.array[t+s]=e.array[n+s];return this}copyArray(t){return this.array.set(t),this}applyMatrix3(t){if(this.itemSize===2)for(let e=0,n=this.count;e<n;e++)Tr.fromBufferAttribute(this,e),Tr.applyMatrix3(t),this.setXY(e,Tr.x,Tr.y);else if(this.itemSize===3)for(let e=0,n=this.count;e<n;e++)Ee.fromBufferAttribute(this,e),Ee.applyMatrix3(t),this.setXYZ(e,Ee.x,Ee.y,Ee.z);return this}applyMatrix4(t){for(let e=0,n=this.count;e<n;e++)Ee.fromBufferAttribute(this,e),Ee.applyMatrix4(t),this.setXYZ(e,Ee.x,Ee.y,Ee.z);return this}applyNormalMatrix(t){for(let e=0,n=this.count;e<n;e++)Ee.fromBufferAttribute(this,e),Ee.applyNormalMatrix(t),this.setXYZ(e,Ee.x,Ee.y,Ee.z);return this}transformDirection(t){for(let e=0,n=this.count;e<n;e++)Ee.fromBufferAttribute(this,e),Ee.transformDirection(t),this.setXYZ(e,Ee.x,Ee.y,Ee.z);return this}set(t,e=0){return this.array.set(t,e),this}getComponent(t,e){let n=this.array[t*this.itemSize+e];return this.normalized&&(n=os(n,this.array)),n}setComponent(t,e,n){return this.normalized&&(n=Ke(n,this.array)),this.array[t*this.itemSize+e]=n,this}getX(t){let e=this.array[t*this.itemSize];return this.normalized&&(e=os(e,this.array)),e}setX(t,e){return this.normalized&&(e=Ke(e,this.array)),this.array[t*this.itemSize]=e,this}getY(t){let e=this.array[t*this.itemSize+1];return this.normalized&&(e=os(e,this.array)),e}setY(t,e){return this.normalized&&(e=Ke(e,this.array)),this.array[t*this.itemSize+1]=e,this}getZ(t){let e=this.array[t*this.itemSize+2];return this.normalized&&(e=os(e,this.array)),e}setZ(t,e){return this.normalized&&(e=Ke(e,this.array)),this.array[t*this.itemSize+2]=e,this}getW(t){let e=this.array[t*this.itemSize+3];return this.normalized&&(e=os(e,this.array)),e}setW(t,e){return this.normalized&&(e=Ke(e,this.array)),this.array[t*this.itemSize+3]=e,this}setXY(t,e,n){return t*=this.itemSize,this.normalized&&(e=Ke(e,this.array),n=Ke(n,this.array)),this.array[t+0]=e,this.array[t+1]=n,this}setXYZ(t,e,n,s){return t*=this.itemSize,this.normalized&&(e=Ke(e,this.array),n=Ke(n,this.array),s=Ke(s,this.array)),this.array[t+0]=e,this.array[t+1]=n,this.array[t+2]=s,this}setXYZW(t,e,n,s,r){return t*=this.itemSize,this.normalized&&(e=Ke(e,this.array),n=Ke(n,this.array),s=Ke(s,this.array),r=Ke(r,this.array)),this.array[t+0]=e,this.array[t+1]=n,this.array[t+2]=s,this.array[t+3]=r,this}onUpload(t){return this.onUploadCallback=t,this}clone(){return new this.constructor(this.array,this.itemSize).copy(this)}toJSON(){let t={itemSize:this.itemSize,type:this.array.constructor.name,array:Array.from(this.array),normalized:this.normalized};return this.name!==""&&(t.name=this.name),this.usage!==xc&&(t.usage=this.usage),t}};var ta=class extends ke{constructor(t,e,n){super(new Uint16Array(t),e,n)}};var ea=class extends ke{constructor(t,e,n){super(new Uint32Array(t),e,n)}};var jt=class extends ke{constructor(t,e,n){super(new Float32Array(t),e,n)}},Vd=0,dn=new he,Ka=new He,ns=new L,rn=new wn,zs=new wn,De=new L,Ie=class i extends gi{constructor(){super(),this.isBufferGeometry=!0,Object.defineProperty(this,"id",{value:Vd++}),this.uuid=Gi(),this.name="",this.type="BufferGeometry",this.index=null,this.indirect=null,this.attributes={},this.morphAttributes={},this.morphTargetsRelative=!1,this.groups=[],this.boundingBox=null,this.boundingSphere=null,this.drawRange={start:0,count:1/0},this.userData={}}getIndex(){return this.index}setIndex(t){return Array.isArray(t)?this.index=new(Uh(t)?ea:ta)(t,1):this.index=t,this}setIndirect(t){return this.indirect=t,this}getIndirect(){return this.indirect}getAttribute(t){return this.attributes[t]}setAttribute(t,e){return this.attributes[t]=e,this}deleteAttribute(t){return delete this.attributes[t],this}hasAttribute(t){return this.attributes[t]!==void 0}addGroup(t,e,n=0){this.groups.push({start:t,count:e,materialIndex:n})}clearGroups(){this.groups=[]}setDrawRange(t,e){this.drawRange.start=t,this.drawRange.count=e}applyMatrix4(t){let e=this.attributes.position;e!==void 0&&(e.applyMatrix4(t),e.needsUpdate=!0);let n=this.attributes.normal;if(n!==void 0){let r=new Yt().getNormalMatrix(t);n.applyNormalMatrix(r),n.needsUpdate=!0}let s=this.attributes.tangent;return s!==void 0&&(s.transformDirection(t),s.needsUpdate=!0),this.boundingBox!==null&&this.computeBoundingBox(),this.boundingSphere!==null&&this.computeBoundingSphere(),this}applyQuaternion(t){return dn.makeRotationFromQuaternion(t),this.applyMatrix4(dn),this}rotateX(t){return dn.makeRotationX(t),this.applyMatrix4(dn),this}rotateY(t){return dn.makeRotationY(t),this.applyMatrix4(dn),this}rotateZ(t){return dn.makeRotationZ(t),this.applyMatrix4(dn),this}translate(t,e,n){return dn.makeTranslation(t,e,n),this.applyMatrix4(dn),this}scale(t,e,n){return dn.makeScale(t,e,n),this.applyMatrix4(dn),this}lookAt(t){return Ka.lookAt(t),Ka.updateMatrix(),this.applyMatrix4(Ka.matrix),this}center(){return this.computeBoundingBox(),this.boundingBox.getCenter(ns).negate(),this.translate(ns.x,ns.y,ns.z),this}setFromPoints(t){let e=this.getAttribute("position");if(e===void 0){let n=[];for(let s=0,r=t.length;s<r;s++){let a=t[s];n.push(a.x,a.y,a.z||0)}this.setAttribute("position",new jt(n,3))}else{for(let n=0,s=e.count;n<s;n++){let r=t[n];e.setXYZ(n,r.x,r.y,r.z||0)}t.length>e.count&&console.warn("THREE.BufferGeometry: Buffer size too small for points data. Use .dispose() and create a new geometry."),e.needsUpdate=!0}return this}computeBoundingBox(){this.boundingBox===null&&(this.boundingBox=new wn);let t=this.attributes.position,e=this.morphAttributes.position;if(t&&t.isGLBufferAttribute){console.error("THREE.BufferGeometry.computeBoundingBox(): GLBufferAttribute requires a manual bounding box.",this),this.boundingBox.set(new L(-1/0,-1/0,-1/0),new L(1/0,1/0,1/0));return}if(t!==void 0){if(this.boundingBox.setFromBufferAttribute(t),e)for(let n=0,s=e.length;n<s;n++){let r=e[n];rn.setFromBufferAttribute(r),this.morphTargetsRelative?(De.addVectors(this.boundingBox.min,rn.min),this.boundingBox.expandByPoint(De),De.addVectors(this.boundingBox.max,rn.max),this.boundingBox.expandByPoint(De)):(this.boundingBox.expandByPoint(rn.min),this.boundingBox.expandByPoint(rn.max))}}else this.boundingBox.makeEmpty();(isNaN(this.boundingBox.min.x)||isNaN(this.boundingBox.min.y)||isNaN(this.boundingBox.min.z))&&console.error('THREE.BufferGeometry.computeBoundingBox(): Computed min/max have NaN values. The "position" attribute is likely to have NaN values.',this)}computeBoundingSphere(){this.boundingSphere===null&&(this.boundingSphere=new Li);let t=this.attributes.position,e=this.morphAttributes.position;if(t&&t.isGLBufferAttribute){console.error("THREE.BufferGeometry.computeBoundingSphere(): GLBufferAttribute requires a manual bounding sphere.",this),this.boundingSphere.set(new L,1/0);return}if(t){let n=this.boundingSphere.center;if(rn.setFromBufferAttribute(t),e)for(let r=0,a=e.length;r<a;r++){let o=e[r];zs.setFromBufferAttribute(o),this.morphTargetsRelative?(De.addVectors(rn.min,zs.min),rn.expandByPoint(De),De.addVectors(rn.max,zs.max),rn.expandByPoint(De)):(rn.expandByPoint(zs.min),rn.expandByPoint(zs.max))}rn.getCenter(n);let s=0;for(let r=0,a=t.count;r<a;r++)De.fromBufferAttribute(t,r),s=Math.max(s,n.distanceToSquared(De));if(e)for(let r=0,a=e.length;r<a;r++){let o=e[r],l=this.morphTargetsRelative;for(let c=0,h=o.count;c<h;c++)De.fromBufferAttribute(o,c),l&&(ns.fromBufferAttribute(t,c),De.add(ns)),s=Math.max(s,n.distanceToSquared(De))}this.boundingSphere.radius=Math.sqrt(s),isNaN(this.boundingSphere.radius)&&console.error('THREE.BufferGeometry.computeBoundingSphere(): Computed radius is NaN. The "position" attribute is likely to have NaN values.',this)}}computeTangents(){let t=this.index,e=this.attributes;if(t===null||e.position===void 0||e.normal===void 0||e.uv===void 0){console.error("THREE.BufferGeometry: .computeTangents() failed. Missing required attributes (index, position, normal or uv)");return}let n=e.position,s=e.normal,r=e.uv;this.hasAttribute("tangent")===!1&&this.setAttribute("tangent",new ke(new Float32Array(4*n.count),4));let a=this.getAttribute("tangent"),o=[],l=[];for(let b=0;b<n.count;b++)o[b]=new L,l[b]=new L;let c=new L,h=new L,u=new L,d=new at,p=new at,g=new at,x=new L,m=new L;function f(b,v,S){c.fromBufferAttribute(n,b),h.fromBufferAttribute(n,v),u.fromBufferAttribute(n,S),d.fromBufferAttribute(r,b),p.fromBufferAttribute(r,v),g.fromBufferAttribute(r,S),h.sub(c),u.sub(c),p.sub(d),g.sub(d);let w=1/(p.x*g.y-g.x*p.y);isFinite(w)&&(x.copy(h).multiplyScalar(g.y).addScaledVector(u,-p.y).multiplyScalar(w),m.copy(u).multiplyScalar(p.x).addScaledVector(h,-g.x).multiplyScalar(w),o[b].add(x),o[v].add(x),o[S].add(x),l[b].add(m),l[v].add(m),l[S].add(m))}let y=this.groups;y.length===0&&(y=[{start:0,count:t.count}]);for(let b=0,v=y.length;b<v;++b){let S=y[b],w=S.start,P=S.count;for(let F=w,U=w+P;F<U;F+=3)f(t.getX(F+0),t.getX(F+1),t.getX(F+2))}let _=new L,M=new L,C=new L,A=new L;function E(b){C.fromBufferAttribute(s,b),A.copy(C);let v=o[b];_.copy(v),_.sub(C.multiplyScalar(C.dot(v))).normalize(),M.crossVectors(A,v);let w=M.dot(l[b])<0?-1:1;a.setXYZW(b,_.x,_.y,_.z,w)}for(let b=0,v=y.length;b<v;++b){let S=y[b],w=S.start,P=S.count;for(let F=w,U=w+P;F<U;F+=3)E(t.getX(F+0)),E(t.getX(F+1)),E(t.getX(F+2))}}computeVertexNormals(){let t=this.index,e=this.getAttribute("position");if(e!==void 0){let n=this.getAttribute("normal");if(n===void 0)n=new ke(new Float32Array(e.count*3),3),this.setAttribute("normal",n);else for(let d=0,p=n.count;d<p;d++)n.setXYZ(d,0,0,0);let s=new L,r=new L,a=new L,o=new L,l=new L,c=new L,h=new L,u=new L;if(t)for(let d=0,p=t.count;d<p;d+=3){let g=t.getX(d+0),x=t.getX(d+1),m=t.getX(d+2);s.fromBufferAttribute(e,g),r.fromBufferAttribute(e,x),a.fromBufferAttribute(e,m),h.subVectors(a,r),u.subVectors(s,r),h.cross(u),o.fromBufferAttribute(n,g),l.fromBufferAttribute(n,x),c.fromBufferAttribute(n,m),o.add(h),l.add(h),c.add(h),n.setXYZ(g,o.x,o.y,o.z),n.setXYZ(x,l.x,l.y,l.z),n.setXYZ(m,c.x,c.y,c.z)}else for(let d=0,p=e.count;d<p;d+=3)s.fromBufferAttribute(e,d+0),r.fromBufferAttribute(e,d+1),a.fromBufferAttribute(e,d+2),h.subVectors(a,r),u.subVectors(s,r),h.cross(u),n.setXYZ(d+0,h.x,h.y,h.z),n.setXYZ(d+1,h.x,h.y,h.z),n.setXYZ(d+2,h.x,h.y,h.z);this.normalizeNormals(),n.needsUpdate=!0}}normalizeNormals(){let t=this.attributes.normal;for(let e=0,n=t.count;e<n;e++)De.fromBufferAttribute(t,e),De.normalize(),t.setXYZ(e,De.x,De.y,De.z)}toNonIndexed(){function t(o,l){let c=o.array,h=o.itemSize,u=o.normalized,d=new c.constructor(l.length*h),p=0,g=0;for(let x=0,m=l.length;x<m;x++){o.isInterleavedBufferAttribute?p=l[x]*o.data.stride+o.offset:p=l[x]*h;for(let f=0;f<h;f++)d[g++]=c[p++]}return new ke(d,h,u)}if(this.index===null)return console.warn("THREE.BufferGeometry.toNonIndexed(): BufferGeometry is already non-indexed."),this;let e=new i,n=this.index.array,s=this.attributes;for(let o in s){let l=s[o],c=t(l,n);e.setAttribute(o,c)}let r=this.morphAttributes;for(let o in r){let l=[],c=r[o];for(let h=0,u=c.length;h<u;h++){let d=c[h],p=t(d,n);l.push(p)}e.morphAttributes[o]=l}e.morphTargetsRelative=this.morphTargetsRelative;let a=this.groups;for(let o=0,l=a.length;o<l;o++){let c=a[o];e.addGroup(c.start,c.count,c.materialIndex)}return e}toJSON(){let t={metadata:{version:4.6,type:"BufferGeometry",generator:"BufferGeometry.toJSON"}};if(t.uuid=this.uuid,t.type=this.type,this.name!==""&&(t.name=this.name),Object.keys(this.userData).length>0&&(t.userData=this.userData),this.parameters!==void 0){let l=this.parameters;for(let c in l)l[c]!==void 0&&(t[c]=l[c]);return t}t.data={attributes:{}};let e=this.index;e!==null&&(t.data.index={type:e.array.constructor.name,array:Array.prototype.slice.call(e.array)});let n=this.attributes;for(let l in n){let c=n[l];t.data.attributes[l]=c.toJSON(t.data)}let s={},r=!1;for(let l in this.morphAttributes){let c=this.morphAttributes[l],h=[];for(let u=0,d=c.length;u<d;u++){let p=c[u];h.push(p.toJSON(t.data))}h.length>0&&(s[l]=h,r=!0)}r&&(t.data.morphAttributes=s,t.data.morphTargetsRelative=this.morphTargetsRelative);let a=this.groups;a.length>0&&(t.data.groups=JSON.parse(JSON.stringify(a)));let o=this.boundingSphere;return o!==null&&(t.data.boundingSphere={center:o.center.toArray(),radius:o.radius}),t}clone(){return new this.constructor().copy(this)}copy(t){this.index=null,this.attributes={},this.morphAttributes={},this.groups=[],this.boundingBox=null,this.boundingSphere=null;let e={};this.name=t.name;let n=t.index;n!==null&&this.setIndex(n.clone(e));let s=t.attributes;for(let c in s){let h=s[c];this.setAttribute(c,h.clone(e))}let r=t.morphAttributes;for(let c in r){let h=[],u=r[c];for(let d=0,p=u.length;d<p;d++)h.push(u[d].clone(e));this.morphAttributes[c]=h}this.morphTargetsRelative=t.morphTargetsRelative;let a=t.groups;for(let c=0,h=a.length;c<h;c++){let u=a[c];this.addGroup(u.start,u.count,u.materialIndex)}let o=t.boundingBox;o!==null&&(this.boundingBox=o.clone());let l=t.boundingSphere;return l!==null&&(this.boundingSphere=l.clone()),this.drawRange.start=t.drawRange.start,this.drawRange.count=t.drawRange.count,this.userData=t.userData,this}dispose(){this.dispatchEvent({type:"dispose"})}},Nc=new he,wi=new Yo,Ar=new Li,Oc=new L,Rr=new L,Cr=new L,Ir=new L,Xa=new L,Pr=new L,Bc=new L,Fr=new L,Ct=class extends He{constructor(t=new Ie,e=new be){super(),this.isMesh=!0,this.type="Mesh",this.geometry=t,this.material=e,this.updateMorphTargets()}copy(t,e){return super.copy(t,e),t.morphTargetInfluences!==void 0&&(this.morphTargetInfluences=t.morphTargetInfluences.slice()),t.morphTargetDictionary!==void 0&&(this.morphTargetDictionary=Object.assign({},t.morphTargetDictionary)),this.material=Array.isArray(t.material)?t.material.slice():t.material,this.geometry=t.geometry,this}updateMorphTargets(){let e=this.geometry.morphAttributes,n=Object.keys(e);if(n.length>0){let s=e[n[0]];if(s!==void 0){this.morphTargetInfluences=[],this.morphTargetDictionary={};for(let r=0,a=s.length;r<a;r++){let o=s[r].name||String(r);this.morphTargetInfluences.push(0),this.morphTargetDictionary[o]=r}}}}getVertexPosition(t,e){let n=this.geometry,s=n.attributes.position,r=n.morphAttributes.position,a=n.morphTargetsRelative;e.fromBufferAttribute(s,t);let o=this.morphTargetInfluences;if(r&&o){Pr.set(0,0,0);for(let l=0,c=r.length;l<c;l++){let h=o[l],u=r[l];h!==0&&(Xa.fromBufferAttribute(u,t),a?Pr.addScaledVector(Xa,h):Pr.addScaledVector(Xa.sub(e),h))}e.add(Pr)}return e}raycast(t,e){let n=this.geometry,s=this.material,r=this.matrixWorld;s!==void 0&&(n.boundingSphere===null&&n.computeBoundingSphere(),Ar.copy(n.boundingSphere),Ar.applyMatrix4(r),wi.copy(t.ray).recast(t.near),!(Ar.containsPoint(wi.origin)===!1&&(wi.intersectSphere(Ar,Oc)===null||wi.origin.distanceToSquared(Oc)>(t.far-t.near)**2))&&(Nc.copy(r).invert(),wi.copy(t.ray).applyMatrix4(Nc),!(n.boundingBox!==null&&wi.intersectsBox(n.boundingBox)===!1)&&this._computeIntersections(t,e,wi)))}_computeIntersections(t,e,n){let s,r=this.geometry,a=this.material,o=r.index,l=r.attributes.position,c=r.attributes.uv,h=r.attributes.uv1,u=r.attributes.normal,d=r.groups,p=r.drawRange;if(o!==null)if(Array.isArray(a))for(let g=0,x=d.length;g<x;g++){let m=d[g],f=a[m.materialIndex],y=Math.max(m.start,p.start),_=Math.min(o.count,Math.min(m.start+m.count,p.start+p.count));for(let M=y,C=_;M<C;M+=3){let A=o.getX(M),E=o.getX(M+1),b=o.getX(M+2);s=Dr(this,f,t,n,c,h,u,A,E,b),s&&(s.faceIndex=Math.floor(M/3),s.face.materialIndex=m.materialIndex,e.push(s))}}else{let g=Math.max(0,p.start),x=Math.min(o.count,p.start+p.count);for(let m=g,f=x;m<f;m+=3){let y=o.getX(m),_=o.getX(m+1),M=o.getX(m+2);s=Dr(this,a,t,n,c,h,u,y,_,M),s&&(s.faceIndex=Math.floor(m/3),e.push(s))}}else if(l!==void 0)if(Array.isArray(a))for(let g=0,x=d.length;g<x;g++){let m=d[g],f=a[m.materialIndex],y=Math.max(m.start,p.start),_=Math.min(l.count,Math.min(m.start+m.count,p.start+p.count));for(let M=y,C=_;M<C;M+=3){let A=M,E=M+1,b=M+2;s=Dr(this,f,t,n,c,h,u,A,E,b),s&&(s.faceIndex=Math.floor(M/3),s.face.materialIndex=m.materialIndex,e.push(s))}}else{let g=Math.max(0,p.start),x=Math.min(l.count,p.start+p.count);for(let m=g,f=x;m<f;m+=3){let y=m,_=m+1,M=m+2;s=Dr(this,a,t,n,c,h,u,y,_,M),s&&(s.faceIndex=Math.floor(m/3),e.push(s))}}}};function Gd(i,t,e,n,s,r,a,o){let l;if(t.side===ze?l=n.intersectTriangle(a,r,s,!0,o):l=n.intersectTriangle(s,r,a,t.side===fi,o),l===null)return null;Fr.copy(o),Fr.applyMatrix4(i.matrixWorld);let c=e.ray.origin.distanceTo(Fr);return c<e.near||c>e.far?null:{distance:c,point:Fr.clone(),object:i}}function Dr(i,t,e,n,s,r,a,o,l,c){i.getVertexPosition(o,Rr),i.getVertexPosition(l,Cr),i.getVertexPosition(c,Ir);let h=Gd(i,t,e,n,Rr,Cr,Ir,Bc);if(h){let u=new L;Pi.getBarycoord(Bc,Rr,Cr,Ir,u),s&&(h.uv=Pi.getInterpolatedAttribute(s,o,l,c,u,new at)),r&&(h.uv1=Pi.getInterpolatedAttribute(r,o,l,c,u,new at)),a&&(h.normal=Pi.getInterpolatedAttribute(a,o,l,c,u,new L),h.normal.dot(n.direction)>0&&h.normal.multiplyScalar(-1));let d={a:o,b:l,c,normal:new L,materialIndex:0};Pi.getNormal(Rr,Cr,Ir,d.normal),h.face=d,h.barycoord=u}return h}var on=class i extends Ie{constructor(t=1,e=1,n=1,s=1,r=1,a=1){super(),this.type="BoxGeometry",this.parameters={width:t,height:e,depth:n,widthSegments:s,heightSegments:r,depthSegments:a};let o=this;s=Math.floor(s),r=Math.floor(r),a=Math.floor(a);let l=[],c=[],h=[],u=[],d=0,p=0;g("z","y","x",-1,-1,n,e,t,a,r,0),g("z","y","x",1,-1,n,e,-t,a,r,1),g("x","z","y",1,1,t,n,e,s,a,2),g("x","z","y",1,-1,t,n,-e,s,a,3),g("x","y","z",1,-1,t,e,n,s,r,4),g("x","y","z",-1,-1,t,e,-n,s,r,5),this.setIndex(l),this.setAttribute("position",new jt(c,3)),this.setAttribute("normal",new jt(h,3)),this.setAttribute("uv",new jt(u,2));function g(x,m,f,y,_,M,C,A,E,b,v){let S=M/E,w=C/b,P=M/2,F=C/2,U=A/2,H=E+1,N=b+1,K=0,O=0,Y=new L;for(let tt=0;tt<N;tt++){let nt=tt*w-F;for(let Tt=0;Tt<H;Tt++){let Nt=Tt*S-P;Y[x]=Nt*y,Y[m]=nt*_,Y[f]=U,c.push(Y.x,Y.y,Y.z),Y[x]=0,Y[m]=0,Y[f]=A>0?1:-1,h.push(Y.x,Y.y,Y.z),u.push(Tt/E),u.push(1-tt/b),K+=1}}for(let tt=0;tt<b;tt++)for(let nt=0;nt<E;nt++){let Tt=d+nt+H*tt,Nt=d+nt+H*(tt+1),X=d+(nt+1)+H*(tt+1),st=d+(nt+1)+H*tt;l.push(Tt,Nt,st),l.push(Nt,X,st),O+=6}o.addGroup(p,O,v),p+=O,d+=K}}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}static fromJSON(t){return new i(t.width,t.height,t.depth,t.widthSegments,t.heightSegments,t.depthSegments)}};function ys(i){let t={};for(let e in i){t[e]={};for(let n in i[e]){let s=i[e][n];s&&(s.isColor||s.isMatrix3||s.isMatrix4||s.isVector2||s.isVector3||s.isVector4||s.isTexture||s.isQuaternion)?s.isRenderTargetTexture?(console.warn("UniformsUtils: Textures of render targets cannot be cloned via cloneUniforms() or mergeUniforms()."),t[e][n]=null):t[e][n]=s.clone():Array.isArray(s)?t[e][n]=s.slice():t[e][n]=s}}return t}function Xe(i){let t={};for(let e=0;e<i.length;e++){let n=ys(i[e]);for(let s in n)t[s]=n[s]}return t}function Wd(i){let t=[];for(let e=0;e<i.length;e++)t.push(i[e].clone());return t}function Oh(i){let t=i.getRenderTarget();return t===null?i.outputColorSpace:t.isXRRenderTarget===!0?t.texture.colorSpace:ne.workingColorSpace}var Kd={clone:ys,merge:Xe},Xd=`void main() {
	gl_Position = projectionMatrix * modelViewMatrix * vec4( position, 1.0 );
}`,qd=`void main() {
	gl_FragColor = vec4( 1.0, 0.0, 0.0, 1.0 );
}`,mn=class extends Ui{static get type(){return"ShaderMaterial"}constructor(t){super(),this.isShaderMaterial=!0,this.defines={},this.uniforms={},this.uniformsGroups=[],this.vertexShader=Xd,this.fragmentShader=qd,this.linewidth=1,this.wireframe=!1,this.wireframeLinewidth=1,this.fog=!1,this.lights=!1,this.clipping=!1,this.forceSinglePass=!0,this.extensions={clipCullDistance:!1,multiDraw:!1},this.defaultAttributeValues={color:[1,1,1],uv:[0,0],uv1:[0,0]},this.index0AttributeName=void 0,this.uniformsNeedUpdate=!1,this.glslVersion=null,t!==void 0&&this.setValues(t)}copy(t){return super.copy(t),this.fragmentShader=t.fragmentShader,this.vertexShader=t.vertexShader,this.uniforms=ys(t.uniforms),this.uniformsGroups=Wd(t.uniformsGroups),this.defines=Object.assign({},t.defines),this.wireframe=t.wireframe,this.wireframeLinewidth=t.wireframeLinewidth,this.fog=t.fog,this.lights=t.lights,this.clipping=t.clipping,this.extensions=Object.assign({},t.extensions),this.glslVersion=t.glslVersion,this}toJSON(t){let e=super.toJSON(t);e.glslVersion=this.glslVersion,e.uniforms={};for(let s in this.uniforms){let a=this.uniforms[s].value;a&&a.isTexture?e.uniforms[s]={type:"t",value:a.toJSON(t).uuid}:a&&a.isColor?e.uniforms[s]={type:"c",value:a.getHex()}:a&&a.isVector2?e.uniforms[s]={type:"v2",value:a.toArray()}:a&&a.isVector3?e.uniforms[s]={type:"v3",value:a.toArray()}:a&&a.isVector4?e.uniforms[s]={type:"v4",value:a.toArray()}:a&&a.isMatrix3?e.uniforms[s]={type:"m3",value:a.toArray()}:a&&a.isMatrix4?e.uniforms[s]={type:"m4",value:a.toArray()}:e.uniforms[s]={value:a}}Object.keys(this.defines).length>0&&(e.defines=this.defines),e.vertexShader=this.vertexShader,e.fragmentShader=this.fragmentShader,e.lights=this.lights,e.clipping=this.clipping;let n={};for(let s in this.extensions)this.extensions[s]===!0&&(n[s]=!0);return Object.keys(n).length>0&&(e.extensions=n),e}},na=class extends He{constructor(){super(),this.isCamera=!0,this.type="Camera",this.matrixWorldInverse=new he,this.projectionMatrix=new he,this.projectionMatrixInverse=new he,this.coordinateSystem=qn}copy(t,e){return super.copy(t,e),this.matrixWorldInverse.copy(t.matrixWorldInverse),this.projectionMatrix.copy(t.projectionMatrix),this.projectionMatrixInverse.copy(t.projectionMatrixInverse),this.coordinateSystem=t.coordinateSystem,this}getWorldDirection(t){return super.getWorldDirection(t).negate()}updateMatrixWorld(t){super.updateMatrixWorld(t),this.matrixWorldInverse.copy(this.matrixWorld).invert()}updateWorldMatrix(t,e){super.updateWorldMatrix(t,e),this.matrixWorldInverse.copy(this.matrixWorld).invert()}clone(){return new this.constructor().copy(this)}},ci=new L,zc=new at,kc=new at,Le=class extends na{constructor(t=50,e=1,n=.1,s=2e3){super(),this.isPerspectiveCamera=!0,this.type="PerspectiveCamera",this.fov=t,this.zoom=1,this.near=n,this.far=s,this.focus=10,this.aspect=e,this.view=null,this.filmGauge=35,this.filmOffset=0,this.updateProjectionMatrix()}copy(t,e){return super.copy(t,e),this.fov=t.fov,this.zoom=t.zoom,this.near=t.near,this.far=t.far,this.focus=t.focus,this.aspect=t.aspect,this.view=t.view===null?null:Object.assign({},t.view),this.filmGauge=t.filmGauge,this.filmOffset=t.filmOffset,this}setFocalLength(t){let e=.5*this.getFilmHeight()/t;this.fov=xs*2*Math.atan(e),this.updateProjectionMatrix()}getFocalLength(){let t=Math.tan(Ks*.5*this.fov);return .5*this.getFilmHeight()/t}getEffectiveFOV(){return xs*2*Math.atan(Math.tan(Ks*.5*this.fov)/this.zoom)}getFilmWidth(){return this.filmGauge*Math.min(this.aspect,1)}getFilmHeight(){return this.filmGauge/Math.max(this.aspect,1)}getViewBounds(t,e,n){ci.set(-1,-1,.5).applyMatrix4(this.projectionMatrixInverse),e.set(ci.x,ci.y).multiplyScalar(-t/ci.z),ci.set(1,1,.5).applyMatrix4(this.projectionMatrixInverse),n.set(ci.x,ci.y).multiplyScalar(-t/ci.z)}getViewSize(t,e){return this.getViewBounds(t,zc,kc),e.subVectors(kc,zc)}setViewOffset(t,e,n,s,r,a){this.aspect=t/e,this.view===null&&(this.view={enabled:!0,fullWidth:1,fullHeight:1,offsetX:0,offsetY:0,width:1,height:1}),this.view.enabled=!0,this.view.fullWidth=t,this.view.fullHeight=e,this.view.offsetX=n,this.view.offsetY=s,this.view.width=r,this.view.height=a,this.updateProjectionMatrix()}clearViewOffset(){this.view!==null&&(this.view.enabled=!1),this.updateProjectionMatrix()}updateProjectionMatrix(){let t=this.near,e=t*Math.tan(Ks*.5*this.fov)/this.zoom,n=2*e,s=this.aspect*n,r=-.5*s,a=this.view;if(this.view!==null&&this.view.enabled){let l=a.fullWidth,c=a.fullHeight;r+=a.offsetX*s/l,e-=a.offsetY*n/c,s*=a.width/l,n*=a.height/c}let o=this.filmOffset;o!==0&&(r+=t*o/this.getFilmWidth()),this.projectionMatrix.makePerspective(r,r+s,e,e-n,t,this.far,this.coordinateSystem),this.projectionMatrixInverse.copy(this.projectionMatrix).invert()}toJSON(t){let e=super.toJSON(t);return e.object.fov=this.fov,e.object.zoom=this.zoom,e.object.near=this.near,e.object.far=this.far,e.object.focus=this.focus,e.object.aspect=this.aspect,this.view!==null&&(e.object.view=Object.assign({},this.view)),e.object.filmGauge=this.filmGauge,e.object.filmOffset=this.filmOffset,e}},is=-90,ss=1,Zo=class extends He{constructor(t,e,n){super(),this.type="CubeCamera",this.renderTarget=n,this.coordinateSystem=null,this.activeMipmapLevel=0;let s=new Le(is,ss,t,e);s.layers=this.layers,this.add(s);let r=new Le(is,ss,t,e);r.layers=this.layers,this.add(r);let a=new Le(is,ss,t,e);a.layers=this.layers,this.add(a);let o=new Le(is,ss,t,e);o.layers=this.layers,this.add(o);let l=new Le(is,ss,t,e);l.layers=this.layers,this.add(l);let c=new Le(is,ss,t,e);c.layers=this.layers,this.add(c)}updateCoordinateSystem(){let t=this.coordinateSystem,e=this.children.concat(),[n,s,r,a,o,l]=e;for(let c of e)this.remove(c);if(t===qn)n.up.set(0,1,0),n.lookAt(1,0,0),s.up.set(0,1,0),s.lookAt(-1,0,0),r.up.set(0,0,-1),r.lookAt(0,1,0),a.up.set(0,0,1),a.lookAt(0,-1,0),o.up.set(0,1,0),o.lookAt(0,0,1),l.up.set(0,1,0),l.lookAt(0,0,-1);else if(t===Zr)n.up.set(0,-1,0),n.lookAt(-1,0,0),s.up.set(0,-1,0),s.lookAt(1,0,0),r.up.set(0,0,1),r.lookAt(0,1,0),a.up.set(0,0,-1),a.lookAt(0,-1,0),o.up.set(0,-1,0),o.lookAt(0,0,1),l.up.set(0,-1,0),l.lookAt(0,0,-1);else throw new Error("THREE.CubeCamera.updateCoordinateSystem(): Invalid coordinate system: "+t);for(let c of e)this.add(c),c.updateMatrixWorld()}update(t,e){this.parent===null&&this.updateMatrixWorld();let{renderTarget:n,activeMipmapLevel:s}=this;this.coordinateSystem!==t.coordinateSystem&&(this.coordinateSystem=t.coordinateSystem,this.updateCoordinateSystem());let[r,a,o,l,c,h]=this.children,u=t.getRenderTarget(),d=t.getActiveCubeFace(),p=t.getActiveMipmapLevel(),g=t.xr.enabled;t.xr.enabled=!1;let x=n.texture.generateMipmaps;n.texture.generateMipmaps=!1,t.setRenderTarget(n,0,s),t.render(e,r),t.setRenderTarget(n,1,s),t.render(e,a),t.setRenderTarget(n,2,s),t.render(e,o),t.setRenderTarget(n,3,s),t.render(e,l),t.setRenderTarget(n,4,s),t.render(e,c),n.texture.generateMipmaps=x,t.setRenderTarget(n,5,s),t.render(e,h),t.setRenderTarget(u,d,p),t.xr.enabled=g,n.texture.needsPMREMUpdate=!0}},ia=class extends $e{constructor(t,e,n,s,r,a,o,l,c,h){t=t!==void 0?t:[],e=e!==void 0?e:ps,super(t,e,n,s,r,a,o,l,c,h),this.isCubeTexture=!0,this.flipY=!1}get images(){return this.image}set images(t){this.image=t}},$o=class extends $n{constructor(t=1,e={}){super(t,t,e),this.isWebGLCubeRenderTarget=!0;let n={width:t,height:t,depth:1},s=[n,n,n,n,n,n];this.texture=new ia(s,e.mapping,e.wrapS,e.wrapT,e.magFilter,e.minFilter,e.format,e.type,e.anisotropy,e.colorSpace),this.texture.isRenderTargetTexture=!0,this.texture.generateMipmaps=e.generateMipmaps!==void 0?e.generateMipmaps:!1,this.texture.minFilter=e.minFilter!==void 0?e.minFilter:fn}fromEquirectangularTexture(t,e){this.texture.type=e.type,this.texture.colorSpace=e.colorSpace,this.texture.generateMipmaps=e.generateMipmaps,this.texture.minFilter=e.minFilter,this.texture.magFilter=e.magFilter;let n={uniforms:{tEquirect:{value:null}},vertexShader:`

				varying vec3 vWorldDirection;

				vec3 transformDirection( in vec3 dir, in mat4 matrix ) {

					return normalize( ( matrix * vec4( dir, 0.0 ) ).xyz );

				}

				void main() {

					vWorldDirection = transformDirection( position, modelMatrix );

					#include <begin_vertex>
					#include <project_vertex>

				}
			`,fragmentShader:`

				uniform sampler2D tEquirect;

				varying vec3 vWorldDirection;

				#include <common>

				void main() {

					vec3 direction = normalize( vWorldDirection );

					vec2 sampleUV = equirectUv( direction );

					gl_FragColor = texture2D( tEquirect, sampleUV );

				}
			`},s=new on(5,5,5),r=new mn({name:"CubemapFromEquirect",uniforms:ys(n.uniforms),vertexShader:n.vertexShader,fragmentShader:n.fragmentShader,side:ze,blending:ui});r.uniforms.tEquirect.value=e;let a=new Ct(s,r),o=e.minFilter;return e.minFilter===Xn&&(e.minFilter=fn),new Zo(1,10,this).update(t,a),e.minFilter=o,a.geometry.dispose(),a.material.dispose(),this}clear(t,e,n,s){let r=t.getRenderTarget();for(let a=0;a<6;a++)t.setRenderTarget(this,a),t.clear(e,n,s);t.setRenderTarget(r)}},qa=new L,Yd=new L,Zd=new Yt,Wn=class{constructor(t=new L(1,0,0),e=0){this.isPlane=!0,this.normal=t,this.constant=e}set(t,e){return this.normal.copy(t),this.constant=e,this}setComponents(t,e,n,s){return this.normal.set(t,e,n),this.constant=s,this}setFromNormalAndCoplanarPoint(t,e){return this.normal.copy(t),this.constant=-e.dot(this.normal),this}setFromCoplanarPoints(t,e,n){let s=qa.subVectors(n,e).cross(Yd.subVectors(t,e)).normalize();return this.setFromNormalAndCoplanarPoint(s,t),this}copy(t){return this.normal.copy(t.normal),this.constant=t.constant,this}normalize(){let t=1/this.normal.length();return this.normal.multiplyScalar(t),this.constant*=t,this}negate(){return this.constant*=-1,this.normal.negate(),this}distanceToPoint(t){return this.normal.dot(t)+this.constant}distanceToSphere(t){return this.distanceToPoint(t.center)-t.radius}projectPoint(t,e){return e.copy(t).addScaledVector(this.normal,-this.distanceToPoint(t))}intersectLine(t,e){let n=t.delta(qa),s=this.normal.dot(n);if(s===0)return this.distanceToPoint(t.start)===0?e.copy(t.start):null;let r=-(t.start.dot(this.normal)+this.constant)/s;return r<0||r>1?null:e.copy(t.start).addScaledVector(n,r)}intersectsLine(t){let e=this.distanceToPoint(t.start),n=this.distanceToPoint(t.end);return e<0&&n>0||n<0&&e>0}intersectsBox(t){return t.intersectsPlane(this)}intersectsSphere(t){return t.intersectsPlane(this)}coplanarPoint(t){return t.copy(this.normal).multiplyScalar(-this.constant)}applyMatrix4(t,e){let n=e||Zd.getNormalMatrix(t),s=this.coplanarPoint(qa).applyMatrix4(t),r=this.normal.applyMatrix3(n).normalize();return this.constant=-s.dot(r),this}translate(t){return this.constant-=t.dot(this.normal),this}equals(t){return t.normal.equals(this.normal)&&t.constant===this.constant}clone(){return new this.constructor().copy(this)}},Ti=new Li,Lr=new L,Qs=class{constructor(t=new Wn,e=new Wn,n=new Wn,s=new Wn,r=new Wn,a=new Wn){this.planes=[t,e,n,s,r,a]}set(t,e,n,s,r,a){let o=this.planes;return o[0].copy(t),o[1].copy(e),o[2].copy(n),o[3].copy(s),o[4].copy(r),o[5].copy(a),this}copy(t){let e=this.planes;for(let n=0;n<6;n++)e[n].copy(t.planes[n]);return this}setFromProjectionMatrix(t,e=qn){let n=this.planes,s=t.elements,r=s[0],a=s[1],o=s[2],l=s[3],c=s[4],h=s[5],u=s[6],d=s[7],p=s[8],g=s[9],x=s[10],m=s[11],f=s[12],y=s[13],_=s[14],M=s[15];if(n[0].setComponents(l-r,d-c,m-p,M-f).normalize(),n[1].setComponents(l+r,d+c,m+p,M+f).normalize(),n[2].setComponents(l+a,d+h,m+g,M+y).normalize(),n[3].setComponents(l-a,d-h,m-g,M-y).normalize(),n[4].setComponents(l-o,d-u,m-x,M-_).normalize(),e===qn)n[5].setComponents(l+o,d+u,m+x,M+_).normalize();else if(e===Zr)n[5].setComponents(o,u,x,_).normalize();else throw new Error("THREE.Frustum.setFromProjectionMatrix(): Invalid coordinate system: "+e);return this}intersectsObject(t){if(t.boundingSphere!==void 0)t.boundingSphere===null&&t.computeBoundingSphere(),Ti.copy(t.boundingSphere).applyMatrix4(t.matrixWorld);else{let e=t.geometry;e.boundingSphere===null&&e.computeBoundingSphere(),Ti.copy(e.boundingSphere).applyMatrix4(t.matrixWorld)}return this.intersectsSphere(Ti)}intersectsSprite(t){return Ti.center.set(0,0,0),Ti.radius=.7071067811865476,Ti.applyMatrix4(t.matrixWorld),this.intersectsSphere(Ti)}intersectsSphere(t){let e=this.planes,n=t.center,s=-t.radius;for(let r=0;r<6;r++)if(e[r].distanceToPoint(n)<s)return!1;return!0}intersectsBox(t){let e=this.planes;for(let n=0;n<6;n++){let s=e[n];if(Lr.x=s.normal.x>0?t.max.x:t.min.x,Lr.y=s.normal.y>0?t.max.y:t.min.y,Lr.z=s.normal.z>0?t.max.z:t.min.z,s.distanceToPoint(Lr)<0)return!1}return!0}containsPoint(t){let e=this.planes;for(let n=0;n<6;n++)if(e[n].distanceToPoint(t)<0)return!1;return!0}clone(){return new this.constructor().copy(this)}};function Bh(){let i=null,t=!1,e=null,n=null;function s(r,a){e(r,a),n=i.requestAnimationFrame(s)}return{start:function(){t!==!0&&e!==null&&(n=i.requestAnimationFrame(s),t=!0)},stop:function(){i.cancelAnimationFrame(n),t=!1},setAnimationLoop:function(r){e=r},setContext:function(r){i=r}}}function $d(i){let t=new WeakMap;function e(o,l){let c=o.array,h=o.usage,u=c.byteLength,d=i.createBuffer();i.bindBuffer(l,d),i.bufferData(l,c,h),o.onUploadCallback();let p;if(c instanceof Float32Array)p=i.FLOAT;else if(c instanceof Uint16Array)o.isFloat16BufferAttribute?p=i.HALF_FLOAT:p=i.UNSIGNED_SHORT;else if(c instanceof Int16Array)p=i.SHORT;else if(c instanceof Uint32Array)p=i.UNSIGNED_INT;else if(c instanceof Int32Array)p=i.INT;else if(c instanceof Int8Array)p=i.BYTE;else if(c instanceof Uint8Array)p=i.UNSIGNED_BYTE;else if(c instanceof Uint8ClampedArray)p=i.UNSIGNED_BYTE;else throw new Error("THREE.WebGLAttributes: Unsupported buffer data format: "+c);return{buffer:d,type:p,bytesPerElement:c.BYTES_PER_ELEMENT,version:o.version,size:u}}function n(o,l,c){let h=l.array,u=l.updateRanges;if(i.bindBuffer(c,o),u.length===0)i.bufferSubData(c,0,h);else{u.sort((p,g)=>p.start-g.start);let d=0;for(let p=1;p<u.length;p++){let g=u[d],x=u[p];x.start<=g.start+g.count+1?g.count=Math.max(g.count,x.start+x.count-g.start):(++d,u[d]=x)}u.length=d+1;for(let p=0,g=u.length;p<g;p++){let x=u[p];i.bufferSubData(c,x.start*h.BYTES_PER_ELEMENT,h,x.start,x.count)}l.clearUpdateRanges()}l.onUploadCallback()}function s(o){return o.isInterleavedBufferAttribute&&(o=o.data),t.get(o)}function r(o){o.isInterleavedBufferAttribute&&(o=o.data);let l=t.get(o);l&&(i.deleteBuffer(l.buffer),t.delete(o))}function a(o,l){if(o.isInterleavedBufferAttribute&&(o=o.data),o.isGLBufferAttribute){let h=t.get(o);(!h||h.version<o.version)&&t.set(o,{buffer:o.buffer,type:o.type,bytesPerElement:o.elementSize,version:o.version});return}let c=t.get(o);if(c===void 0)t.set(o,e(o,l));else if(c.version<o.version){if(c.size!==o.array.byteLength)throw new Error("THREE.WebGLAttributes: The size of the buffer attribute's array buffer does not match the original size. Resizing buffer attributes is not supported.");n(c.buffer,o,l),c.version=o.version}}return{get:s,remove:r,update:a}}var Je=class i extends Ie{constructor(t=1,e=1,n=1,s=1){super(),this.type="PlaneGeometry",this.parameters={width:t,height:e,widthSegments:n,heightSegments:s};let r=t/2,a=e/2,o=Math.floor(n),l=Math.floor(s),c=o+1,h=l+1,u=t/o,d=e/l,p=[],g=[],x=[],m=[];for(let f=0;f<h;f++){let y=f*d-a;for(let _=0;_<c;_++){let M=_*u-r;g.push(M,-y,0),x.push(0,0,1),m.push(_/o),m.push(1-f/l)}}for(let f=0;f<l;f++)for(let y=0;y<o;y++){let _=y+c*f,M=y+c*(f+1),C=y+1+c*(f+1),A=y+1+c*f;p.push(_,M,A),p.push(M,C,A)}this.setIndex(p),this.setAttribute("position",new jt(g,3)),this.setAttribute("normal",new jt(x,3)),this.setAttribute("uv",new jt(m,2))}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}static fromJSON(t){return new i(t.width,t.height,t.widthSegments,t.heightSegments)}},Jd=`#ifdef USE_ALPHAHASH
	if ( diffuseColor.a < getAlphaHashThreshold( vPosition ) ) discard;
#endif`,Qd=`#ifdef USE_ALPHAHASH
	const float ALPHA_HASH_SCALE = 0.05;
	float hash2D( vec2 value ) {
		return fract( 1.0e4 * sin( 17.0 * value.x + 0.1 * value.y ) * ( 0.1 + abs( sin( 13.0 * value.y + value.x ) ) ) );
	}
	float hash3D( vec3 value ) {
		return hash2D( vec2( hash2D( value.xy ), value.z ) );
	}
	float getAlphaHashThreshold( vec3 position ) {
		float maxDeriv = max(
			length( dFdx( position.xyz ) ),
			length( dFdy( position.xyz ) )
		);
		float pixScale = 1.0 / ( ALPHA_HASH_SCALE * maxDeriv );
		vec2 pixScales = vec2(
			exp2( floor( log2( pixScale ) ) ),
			exp2( ceil( log2( pixScale ) ) )
		);
		vec2 alpha = vec2(
			hash3D( floor( pixScales.x * position.xyz ) ),
			hash3D( floor( pixScales.y * position.xyz ) )
		);
		float lerpFactor = fract( log2( pixScale ) );
		float x = ( 1.0 - lerpFactor ) * alpha.x + lerpFactor * alpha.y;
		float a = min( lerpFactor, 1.0 - lerpFactor );
		vec3 cases = vec3(
			x * x / ( 2.0 * a * ( 1.0 - a ) ),
			( x - 0.5 * a ) / ( 1.0 - a ),
			1.0 - ( ( 1.0 - x ) * ( 1.0 - x ) / ( 2.0 * a * ( 1.0 - a ) ) )
		);
		float threshold = ( x < ( 1.0 - a ) )
			? ( ( x < a ) ? cases.x : cases.y )
			: cases.z;
		return clamp( threshold , 1.0e-6, 1.0 );
	}
#endif`,jd=`#ifdef USE_ALPHAMAP
	diffuseColor.a *= texture2D( alphaMap, vAlphaMapUv ).g;
#endif`,tf=`#ifdef USE_ALPHAMAP
	uniform sampler2D alphaMap;
#endif`,ef=`#ifdef USE_ALPHATEST
	#ifdef ALPHA_TO_COVERAGE
	diffuseColor.a = smoothstep( alphaTest, alphaTest + fwidth( diffuseColor.a ), diffuseColor.a );
	if ( diffuseColor.a == 0.0 ) discard;
	#else
	if ( diffuseColor.a < alphaTest ) discard;
	#endif
#endif`,nf=`#ifdef USE_ALPHATEST
	uniform float alphaTest;
#endif`,sf=`#ifdef USE_AOMAP
	float ambientOcclusion = ( texture2D( aoMap, vAoMapUv ).r - 1.0 ) * aoMapIntensity + 1.0;
	reflectedLight.indirectDiffuse *= ambientOcclusion;
	#if defined( USE_CLEARCOAT ) 
		clearcoatSpecularIndirect *= ambientOcclusion;
	#endif
	#if defined( USE_SHEEN ) 
		sheenSpecularIndirect *= ambientOcclusion;
	#endif
	#if defined( USE_ENVMAP ) && defined( STANDARD )
		float dotNV = saturate( dot( geometryNormal, geometryViewDir ) );
		reflectedLight.indirectSpecular *= computeSpecularOcclusion( dotNV, ambientOcclusion, material.roughness );
	#endif
#endif`,rf=`#ifdef USE_AOMAP
	uniform sampler2D aoMap;
	uniform float aoMapIntensity;
#endif`,af=`#ifdef USE_BATCHING
	#if ! defined( GL_ANGLE_multi_draw )
	#define gl_DrawID _gl_DrawID
	uniform int _gl_DrawID;
	#endif
	uniform highp sampler2D batchingTexture;
	uniform highp usampler2D batchingIdTexture;
	mat4 getBatchingMatrix( const in float i ) {
		int size = textureSize( batchingTexture, 0 ).x;
		int j = int( i ) * 4;
		int x = j % size;
		int y = j / size;
		vec4 v1 = texelFetch( batchingTexture, ivec2( x, y ), 0 );
		vec4 v2 = texelFetch( batchingTexture, ivec2( x + 1, y ), 0 );
		vec4 v3 = texelFetch( batchingTexture, ivec2( x + 2, y ), 0 );
		vec4 v4 = texelFetch( batchingTexture, ivec2( x + 3, y ), 0 );
		return mat4( v1, v2, v3, v4 );
	}
	float getIndirectIndex( const in int i ) {
		int size = textureSize( batchingIdTexture, 0 ).x;
		int x = i % size;
		int y = i / size;
		return float( texelFetch( batchingIdTexture, ivec2( x, y ), 0 ).r );
	}
#endif
#ifdef USE_BATCHING_COLOR
	uniform sampler2D batchingColorTexture;
	vec3 getBatchingColor( const in float i ) {
		int size = textureSize( batchingColorTexture, 0 ).x;
		int j = int( i );
		int x = j % size;
		int y = j / size;
		return texelFetch( batchingColorTexture, ivec2( x, y ), 0 ).rgb;
	}
#endif`,of=`#ifdef USE_BATCHING
	mat4 batchingMatrix = getBatchingMatrix( getIndirectIndex( gl_DrawID ) );
#endif`,lf=`vec3 transformed = vec3( position );
#ifdef USE_ALPHAHASH
	vPosition = vec3( position );
#endif`,cf=`vec3 objectNormal = vec3( normal );
#ifdef USE_TANGENT
	vec3 objectTangent = vec3( tangent.xyz );
#endif`,hf=`float G_BlinnPhong_Implicit( ) {
	return 0.25;
}
float D_BlinnPhong( const in float shininess, const in float dotNH ) {
	return RECIPROCAL_PI * ( shininess * 0.5 + 1.0 ) * pow( dotNH, shininess );
}
vec3 BRDF_BlinnPhong( const in vec3 lightDir, const in vec3 viewDir, const in vec3 normal, const in vec3 specularColor, const in float shininess ) {
	vec3 halfDir = normalize( lightDir + viewDir );
	float dotNH = saturate( dot( normal, halfDir ) );
	float dotVH = saturate( dot( viewDir, halfDir ) );
	vec3 F = F_Schlick( specularColor, 1.0, dotVH );
	float G = G_BlinnPhong_Implicit( );
	float D = D_BlinnPhong( shininess, dotNH );
	return F * ( G * D );
} // validated`,uf=`#ifdef USE_IRIDESCENCE
	const mat3 XYZ_TO_REC709 = mat3(
		 3.2404542, -0.9692660,  0.0556434,
		-1.5371385,  1.8760108, -0.2040259,
		-0.4985314,  0.0415560,  1.0572252
	);
	vec3 Fresnel0ToIor( vec3 fresnel0 ) {
		vec3 sqrtF0 = sqrt( fresnel0 );
		return ( vec3( 1.0 ) + sqrtF0 ) / ( vec3( 1.0 ) - sqrtF0 );
	}
	vec3 IorToFresnel0( vec3 transmittedIor, float incidentIor ) {
		return pow2( ( transmittedIor - vec3( incidentIor ) ) / ( transmittedIor + vec3( incidentIor ) ) );
	}
	float IorToFresnel0( float transmittedIor, float incidentIor ) {
		return pow2( ( transmittedIor - incidentIor ) / ( transmittedIor + incidentIor ));
	}
	vec3 evalSensitivity( float OPD, vec3 shift ) {
		float phase = 2.0 * PI * OPD * 1.0e-9;
		vec3 val = vec3( 5.4856e-13, 4.4201e-13, 5.2481e-13 );
		vec3 pos = vec3( 1.6810e+06, 1.7953e+06, 2.2084e+06 );
		vec3 var = vec3( 4.3278e+09, 9.3046e+09, 6.6121e+09 );
		vec3 xyz = val * sqrt( 2.0 * PI * var ) * cos( pos * phase + shift ) * exp( - pow2( phase ) * var );
		xyz.x += 9.7470e-14 * sqrt( 2.0 * PI * 4.5282e+09 ) * cos( 2.2399e+06 * phase + shift[ 0 ] ) * exp( - 4.5282e+09 * pow2( phase ) );
		xyz /= 1.0685e-7;
		vec3 rgb = XYZ_TO_REC709 * xyz;
		return rgb;
	}
	vec3 evalIridescence( float outsideIOR, float eta2, float cosTheta1, float thinFilmThickness, vec3 baseF0 ) {
		vec3 I;
		float iridescenceIOR = mix( outsideIOR, eta2, smoothstep( 0.0, 0.03, thinFilmThickness ) );
		float sinTheta2Sq = pow2( outsideIOR / iridescenceIOR ) * ( 1.0 - pow2( cosTheta1 ) );
		float cosTheta2Sq = 1.0 - sinTheta2Sq;
		if ( cosTheta2Sq < 0.0 ) {
			return vec3( 1.0 );
		}
		float cosTheta2 = sqrt( cosTheta2Sq );
		float R0 = IorToFresnel0( iridescenceIOR, outsideIOR );
		float R12 = F_Schlick( R0, 1.0, cosTheta1 );
		float T121 = 1.0 - R12;
		float phi12 = 0.0;
		if ( iridescenceIOR < outsideIOR ) phi12 = PI;
		float phi21 = PI - phi12;
		vec3 baseIOR = Fresnel0ToIor( clamp( baseF0, 0.0, 0.9999 ) );		vec3 R1 = IorToFresnel0( baseIOR, iridescenceIOR );
		vec3 R23 = F_Schlick( R1, 1.0, cosTheta2 );
		vec3 phi23 = vec3( 0.0 );
		if ( baseIOR[ 0 ] < iridescenceIOR ) phi23[ 0 ] = PI;
		if ( baseIOR[ 1 ] < iridescenceIOR ) phi23[ 1 ] = PI;
		if ( baseIOR[ 2 ] < iridescenceIOR ) phi23[ 2 ] = PI;
		float OPD = 2.0 * iridescenceIOR * thinFilmThickness * cosTheta2;
		vec3 phi = vec3( phi21 ) + phi23;
		vec3 R123 = clamp( R12 * R23, 1e-5, 0.9999 );
		vec3 r123 = sqrt( R123 );
		vec3 Rs = pow2( T121 ) * R23 / ( vec3( 1.0 ) - R123 );
		vec3 C0 = R12 + Rs;
		I = C0;
		vec3 Cm = Rs - T121;
		for ( int m = 1; m <= 2; ++ m ) {
			Cm *= r123;
			vec3 Sm = 2.0 * evalSensitivity( float( m ) * OPD, float( m ) * phi );
			I += Cm * Sm;
		}
		return max( I, vec3( 0.0 ) );
	}
#endif`,df=`#ifdef USE_BUMPMAP
	uniform sampler2D bumpMap;
	uniform float bumpScale;
	vec2 dHdxy_fwd() {
		vec2 dSTdx = dFdx( vBumpMapUv );
		vec2 dSTdy = dFdy( vBumpMapUv );
		float Hll = bumpScale * texture2D( bumpMap, vBumpMapUv ).x;
		float dBx = bumpScale * texture2D( bumpMap, vBumpMapUv + dSTdx ).x - Hll;
		float dBy = bumpScale * texture2D( bumpMap, vBumpMapUv + dSTdy ).x - Hll;
		return vec2( dBx, dBy );
	}
	vec3 perturbNormalArb( vec3 surf_pos, vec3 surf_norm, vec2 dHdxy, float faceDirection ) {
		vec3 vSigmaX = normalize( dFdx( surf_pos.xyz ) );
		vec3 vSigmaY = normalize( dFdy( surf_pos.xyz ) );
		vec3 vN = surf_norm;
		vec3 R1 = cross( vSigmaY, vN );
		vec3 R2 = cross( vN, vSigmaX );
		float fDet = dot( vSigmaX, R1 ) * faceDirection;
		vec3 vGrad = sign( fDet ) * ( dHdxy.x * R1 + dHdxy.y * R2 );
		return normalize( abs( fDet ) * surf_norm - vGrad );
	}
#endif`,ff=`#if NUM_CLIPPING_PLANES > 0
	vec4 plane;
	#ifdef ALPHA_TO_COVERAGE
		float distanceToPlane, distanceGradient;
		float clipOpacity = 1.0;
		#pragma unroll_loop_start
		for ( int i = 0; i < UNION_CLIPPING_PLANES; i ++ ) {
			plane = clippingPlanes[ i ];
			distanceToPlane = - dot( vClipPosition, plane.xyz ) + plane.w;
			distanceGradient = fwidth( distanceToPlane ) / 2.0;
			clipOpacity *= smoothstep( - distanceGradient, distanceGradient, distanceToPlane );
			if ( clipOpacity == 0.0 ) discard;
		}
		#pragma unroll_loop_end
		#if UNION_CLIPPING_PLANES < NUM_CLIPPING_PLANES
			float unionClipOpacity = 1.0;
			#pragma unroll_loop_start
			for ( int i = UNION_CLIPPING_PLANES; i < NUM_CLIPPING_PLANES; i ++ ) {
				plane = clippingPlanes[ i ];
				distanceToPlane = - dot( vClipPosition, plane.xyz ) + plane.w;
				distanceGradient = fwidth( distanceToPlane ) / 2.0;
				unionClipOpacity *= 1.0 - smoothstep( - distanceGradient, distanceGradient, distanceToPlane );
			}
			#pragma unroll_loop_end
			clipOpacity *= 1.0 - unionClipOpacity;
		#endif
		diffuseColor.a *= clipOpacity;
		if ( diffuseColor.a == 0.0 ) discard;
	#else
		#pragma unroll_loop_start
		for ( int i = 0; i < UNION_CLIPPING_PLANES; i ++ ) {
			plane = clippingPlanes[ i ];
			if ( dot( vClipPosition, plane.xyz ) > plane.w ) discard;
		}
		#pragma unroll_loop_end
		#if UNION_CLIPPING_PLANES < NUM_CLIPPING_PLANES
			bool clipped = true;
			#pragma unroll_loop_start
			for ( int i = UNION_CLIPPING_PLANES; i < NUM_CLIPPING_PLANES; i ++ ) {
				plane = clippingPlanes[ i ];
				clipped = ( dot( vClipPosition, plane.xyz ) > plane.w ) && clipped;
			}
			#pragma unroll_loop_end
			if ( clipped ) discard;
		#endif
	#endif
#endif`,pf=`#if NUM_CLIPPING_PLANES > 0
	varying vec3 vClipPosition;
	uniform vec4 clippingPlanes[ NUM_CLIPPING_PLANES ];
#endif`,mf=`#if NUM_CLIPPING_PLANES > 0
	varying vec3 vClipPosition;
#endif`,gf=`#if NUM_CLIPPING_PLANES > 0
	vClipPosition = - mvPosition.xyz;
#endif`,_f=`#if defined( USE_COLOR_ALPHA )
	diffuseColor *= vColor;
#elif defined( USE_COLOR )
	diffuseColor.rgb *= vColor;
#endif`,xf=`#if defined( USE_COLOR_ALPHA )
	varying vec4 vColor;
#elif defined( USE_COLOR )
	varying vec3 vColor;
#endif`,yf=`#if defined( USE_COLOR_ALPHA )
	varying vec4 vColor;
#elif defined( USE_COLOR ) || defined( USE_INSTANCING_COLOR ) || defined( USE_BATCHING_COLOR )
	varying vec3 vColor;
#endif`,vf=`#if defined( USE_COLOR_ALPHA )
	vColor = vec4( 1.0 );
#elif defined( USE_COLOR ) || defined( USE_INSTANCING_COLOR ) || defined( USE_BATCHING_COLOR )
	vColor = vec3( 1.0 );
#endif
#ifdef USE_COLOR
	vColor *= color;
#endif
#ifdef USE_INSTANCING_COLOR
	vColor.xyz *= instanceColor.xyz;
#endif
#ifdef USE_BATCHING_COLOR
	vec3 batchingColor = getBatchingColor( getIndirectIndex( gl_DrawID ) );
	vColor.xyz *= batchingColor.xyz;
#endif`,Mf=`#define PI 3.141592653589793
#define PI2 6.283185307179586
#define PI_HALF 1.5707963267948966
#define RECIPROCAL_PI 0.3183098861837907
#define RECIPROCAL_PI2 0.15915494309189535
#define EPSILON 1e-6
#ifndef saturate
#define saturate( a ) clamp( a, 0.0, 1.0 )
#endif
#define whiteComplement( a ) ( 1.0 - saturate( a ) )
float pow2( const in float x ) { return x*x; }
vec3 pow2( const in vec3 x ) { return x*x; }
float pow3( const in float x ) { return x*x*x; }
float pow4( const in float x ) { float x2 = x*x; return x2*x2; }
float max3( const in vec3 v ) { return max( max( v.x, v.y ), v.z ); }
float average( const in vec3 v ) { return dot( v, vec3( 0.3333333 ) ); }
highp float rand( const in vec2 uv ) {
	const highp float a = 12.9898, b = 78.233, c = 43758.5453;
	highp float dt = dot( uv.xy, vec2( a,b ) ), sn = mod( dt, PI );
	return fract( sin( sn ) * c );
}
#ifdef HIGH_PRECISION
	float precisionSafeLength( vec3 v ) { return length( v ); }
#else
	float precisionSafeLength( vec3 v ) {
		float maxComponent = max3( abs( v ) );
		return length( v / maxComponent ) * maxComponent;
	}
#endif
struct IncidentLight {
	vec3 color;
	vec3 direction;
	bool visible;
};
struct ReflectedLight {
	vec3 directDiffuse;
	vec3 directSpecular;
	vec3 indirectDiffuse;
	vec3 indirectSpecular;
};
#ifdef USE_ALPHAHASH
	varying vec3 vPosition;
#endif
vec3 transformDirection( in vec3 dir, in mat4 matrix ) {
	return normalize( ( matrix * vec4( dir, 0.0 ) ).xyz );
}
vec3 inverseTransformDirection( in vec3 dir, in mat4 matrix ) {
	return normalize( ( vec4( dir, 0.0 ) * matrix ).xyz );
}
mat3 transposeMat3( const in mat3 m ) {
	mat3 tmp;
	tmp[ 0 ] = vec3( m[ 0 ].x, m[ 1 ].x, m[ 2 ].x );
	tmp[ 1 ] = vec3( m[ 0 ].y, m[ 1 ].y, m[ 2 ].y );
	tmp[ 2 ] = vec3( m[ 0 ].z, m[ 1 ].z, m[ 2 ].z );
	return tmp;
}
bool isPerspectiveMatrix( mat4 m ) {
	return m[ 2 ][ 3 ] == - 1.0;
}
vec2 equirectUv( in vec3 dir ) {
	float u = atan( dir.z, dir.x ) * RECIPROCAL_PI2 + 0.5;
	float v = asin( clamp( dir.y, - 1.0, 1.0 ) ) * RECIPROCAL_PI + 0.5;
	return vec2( u, v );
}
vec3 BRDF_Lambert( const in vec3 diffuseColor ) {
	return RECIPROCAL_PI * diffuseColor;
}
vec3 F_Schlick( const in vec3 f0, const in float f90, const in float dotVH ) {
	float fresnel = exp2( ( - 5.55473 * dotVH - 6.98316 ) * dotVH );
	return f0 * ( 1.0 - fresnel ) + ( f90 * fresnel );
}
float F_Schlick( const in float f0, const in float f90, const in float dotVH ) {
	float fresnel = exp2( ( - 5.55473 * dotVH - 6.98316 ) * dotVH );
	return f0 * ( 1.0 - fresnel ) + ( f90 * fresnel );
} // validated`,Sf=`#ifdef ENVMAP_TYPE_CUBE_UV
	#define cubeUV_minMipLevel 4.0
	#define cubeUV_minTileSize 16.0
	float getFace( vec3 direction ) {
		vec3 absDirection = abs( direction );
		float face = - 1.0;
		if ( absDirection.x > absDirection.z ) {
			if ( absDirection.x > absDirection.y )
				face = direction.x > 0.0 ? 0.0 : 3.0;
			else
				face = direction.y > 0.0 ? 1.0 : 4.0;
		} else {
			if ( absDirection.z > absDirection.y )
				face = direction.z > 0.0 ? 2.0 : 5.0;
			else
				face = direction.y > 0.0 ? 1.0 : 4.0;
		}
		return face;
	}
	vec2 getUV( vec3 direction, float face ) {
		vec2 uv;
		if ( face == 0.0 ) {
			uv = vec2( direction.z, direction.y ) / abs( direction.x );
		} else if ( face == 1.0 ) {
			uv = vec2( - direction.x, - direction.z ) / abs( direction.y );
		} else if ( face == 2.0 ) {
			uv = vec2( - direction.x, direction.y ) / abs( direction.z );
		} else if ( face == 3.0 ) {
			uv = vec2( - direction.z, direction.y ) / abs( direction.x );
		} else if ( face == 4.0 ) {
			uv = vec2( - direction.x, direction.z ) / abs( direction.y );
		} else {
			uv = vec2( direction.x, direction.y ) / abs( direction.z );
		}
		return 0.5 * ( uv + 1.0 );
	}
	vec3 bilinearCubeUV( sampler2D envMap, vec3 direction, float mipInt ) {
		float face = getFace( direction );
		float filterInt = max( cubeUV_minMipLevel - mipInt, 0.0 );
		mipInt = max( mipInt, cubeUV_minMipLevel );
		float faceSize = exp2( mipInt );
		highp vec2 uv = getUV( direction, face ) * ( faceSize - 2.0 ) + 1.0;
		if ( face > 2.0 ) {
			uv.y += faceSize;
			face -= 3.0;
		}
		uv.x += face * faceSize;
		uv.x += filterInt * 3.0 * cubeUV_minTileSize;
		uv.y += 4.0 * ( exp2( CUBEUV_MAX_MIP ) - faceSize );
		uv.x *= CUBEUV_TEXEL_WIDTH;
		uv.y *= CUBEUV_TEXEL_HEIGHT;
		#ifdef texture2DGradEXT
			return texture2DGradEXT( envMap, uv, vec2( 0.0 ), vec2( 0.0 ) ).rgb;
		#else
			return texture2D( envMap, uv ).rgb;
		#endif
	}
	#define cubeUV_r0 1.0
	#define cubeUV_m0 - 2.0
	#define cubeUV_r1 0.8
	#define cubeUV_m1 - 1.0
	#define cubeUV_r4 0.4
	#define cubeUV_m4 2.0
	#define cubeUV_r5 0.305
	#define cubeUV_m5 3.0
	#define cubeUV_r6 0.21
	#define cubeUV_m6 4.0
	float roughnessToMip( float roughness ) {
		float mip = 0.0;
		if ( roughness >= cubeUV_r1 ) {
			mip = ( cubeUV_r0 - roughness ) * ( cubeUV_m1 - cubeUV_m0 ) / ( cubeUV_r0 - cubeUV_r1 ) + cubeUV_m0;
		} else if ( roughness >= cubeUV_r4 ) {
			mip = ( cubeUV_r1 - roughness ) * ( cubeUV_m4 - cubeUV_m1 ) / ( cubeUV_r1 - cubeUV_r4 ) + cubeUV_m1;
		} else if ( roughness >= cubeUV_r5 ) {
			mip = ( cubeUV_r4 - roughness ) * ( cubeUV_m5 - cubeUV_m4 ) / ( cubeUV_r4 - cubeUV_r5 ) + cubeUV_m4;
		} else if ( roughness >= cubeUV_r6 ) {
			mip = ( cubeUV_r5 - roughness ) * ( cubeUV_m6 - cubeUV_m5 ) / ( cubeUV_r5 - cubeUV_r6 ) + cubeUV_m5;
		} else {
			mip = - 2.0 * log2( 1.16 * roughness );		}
		return mip;
	}
	vec4 textureCubeUV( sampler2D envMap, vec3 sampleDir, float roughness ) {
		float mip = clamp( roughnessToMip( roughness ), cubeUV_m0, CUBEUV_MAX_MIP );
		float mipF = fract( mip );
		float mipInt = floor( mip );
		vec3 color0 = bilinearCubeUV( envMap, sampleDir, mipInt );
		if ( mipF == 0.0 ) {
			return vec4( color0, 1.0 );
		} else {
			vec3 color1 = bilinearCubeUV( envMap, sampleDir, mipInt + 1.0 );
			return vec4( mix( color0, color1, mipF ), 1.0 );
		}
	}
#endif`,Ef=`vec3 transformedNormal = objectNormal;
#ifdef USE_TANGENT
	vec3 transformedTangent = objectTangent;
#endif
#ifdef USE_BATCHING
	mat3 bm = mat3( batchingMatrix );
	transformedNormal /= vec3( dot( bm[ 0 ], bm[ 0 ] ), dot( bm[ 1 ], bm[ 1 ] ), dot( bm[ 2 ], bm[ 2 ] ) );
	transformedNormal = bm * transformedNormal;
	#ifdef USE_TANGENT
		transformedTangent = bm * transformedTangent;
	#endif
#endif
#ifdef USE_INSTANCING
	mat3 im = mat3( instanceMatrix );
	transformedNormal /= vec3( dot( im[ 0 ], im[ 0 ] ), dot( im[ 1 ], im[ 1 ] ), dot( im[ 2 ], im[ 2 ] ) );
	transformedNormal = im * transformedNormal;
	#ifdef USE_TANGENT
		transformedTangent = im * transformedTangent;
	#endif
#endif
transformedNormal = normalMatrix * transformedNormal;
#ifdef FLIP_SIDED
	transformedNormal = - transformedNormal;
#endif
#ifdef USE_TANGENT
	transformedTangent = ( modelViewMatrix * vec4( transformedTangent, 0.0 ) ).xyz;
	#ifdef FLIP_SIDED
		transformedTangent = - transformedTangent;
	#endif
#endif`,bf=`#ifdef USE_DISPLACEMENTMAP
	uniform sampler2D displacementMap;
	uniform float displacementScale;
	uniform float displacementBias;
#endif`,wf=`#ifdef USE_DISPLACEMENTMAP
	transformed += normalize( objectNormal ) * ( texture2D( displacementMap, vDisplacementMapUv ).x * displacementScale + displacementBias );
#endif`,Tf=`#ifdef USE_EMISSIVEMAP
	vec4 emissiveColor = texture2D( emissiveMap, vEmissiveMapUv );
	#ifdef DECODE_VIDEO_TEXTURE_EMISSIVE
		emissiveColor = sRGBTransferEOTF( emissiveColor );
	#endif
	totalEmissiveRadiance *= emissiveColor.rgb;
#endif`,Af=`#ifdef USE_EMISSIVEMAP
	uniform sampler2D emissiveMap;
#endif`,Rf="gl_FragColor = linearToOutputTexel( gl_FragColor );",Cf=`vec4 LinearTransferOETF( in vec4 value ) {
	return value;
}
vec4 sRGBTransferEOTF( in vec4 value ) {
	return vec4( mix( pow( value.rgb * 0.9478672986 + vec3( 0.0521327014 ), vec3( 2.4 ) ), value.rgb * 0.0773993808, vec3( lessThanEqual( value.rgb, vec3( 0.04045 ) ) ) ), value.a );
}
vec4 sRGBTransferOETF( in vec4 value ) {
	return vec4( mix( pow( value.rgb, vec3( 0.41666 ) ) * 1.055 - vec3( 0.055 ), value.rgb * 12.92, vec3( lessThanEqual( value.rgb, vec3( 0.0031308 ) ) ) ), value.a );
}`,If=`#ifdef USE_ENVMAP
	#ifdef ENV_WORLDPOS
		vec3 cameraToFrag;
		if ( isOrthographic ) {
			cameraToFrag = normalize( vec3( - viewMatrix[ 0 ][ 2 ], - viewMatrix[ 1 ][ 2 ], - viewMatrix[ 2 ][ 2 ] ) );
		} else {
			cameraToFrag = normalize( vWorldPosition - cameraPosition );
		}
		vec3 worldNormal = inverseTransformDirection( normal, viewMatrix );
		#ifdef ENVMAP_MODE_REFLECTION
			vec3 reflectVec = reflect( cameraToFrag, worldNormal );
		#else
			vec3 reflectVec = refract( cameraToFrag, worldNormal, refractionRatio );
		#endif
	#else
		vec3 reflectVec = vReflect;
	#endif
	#ifdef ENVMAP_TYPE_CUBE
		vec4 envColor = textureCube( envMap, envMapRotation * vec3( flipEnvMap * reflectVec.x, reflectVec.yz ) );
	#else
		vec4 envColor = vec4( 0.0 );
	#endif
	#ifdef ENVMAP_BLENDING_MULTIPLY
		outgoingLight = mix( outgoingLight, outgoingLight * envColor.xyz, specularStrength * reflectivity );
	#elif defined( ENVMAP_BLENDING_MIX )
		outgoingLight = mix( outgoingLight, envColor.xyz, specularStrength * reflectivity );
	#elif defined( ENVMAP_BLENDING_ADD )
		outgoingLight += envColor.xyz * specularStrength * reflectivity;
	#endif
#endif`,Pf=`#ifdef USE_ENVMAP
	uniform float envMapIntensity;
	uniform float flipEnvMap;
	uniform mat3 envMapRotation;
	#ifdef ENVMAP_TYPE_CUBE
		uniform samplerCube envMap;
	#else
		uniform sampler2D envMap;
	#endif
	
#endif`,Ff=`#ifdef USE_ENVMAP
	uniform float reflectivity;
	#if defined( USE_BUMPMAP ) || defined( USE_NORMALMAP ) || defined( PHONG ) || defined( LAMBERT )
		#define ENV_WORLDPOS
	#endif
	#ifdef ENV_WORLDPOS
		varying vec3 vWorldPosition;
		uniform float refractionRatio;
	#else
		varying vec3 vReflect;
	#endif
#endif`,Df=`#ifdef USE_ENVMAP
	#if defined( USE_BUMPMAP ) || defined( USE_NORMALMAP ) || defined( PHONG ) || defined( LAMBERT )
		#define ENV_WORLDPOS
	#endif
	#ifdef ENV_WORLDPOS
		
		varying vec3 vWorldPosition;
	#else
		varying vec3 vReflect;
		uniform float refractionRatio;
	#endif
#endif`,Lf=`#ifdef USE_ENVMAP
	#ifdef ENV_WORLDPOS
		vWorldPosition = worldPosition.xyz;
	#else
		vec3 cameraToVertex;
		if ( isOrthographic ) {
			cameraToVertex = normalize( vec3( - viewMatrix[ 0 ][ 2 ], - viewMatrix[ 1 ][ 2 ], - viewMatrix[ 2 ][ 2 ] ) );
		} else {
			cameraToVertex = normalize( worldPosition.xyz - cameraPosition );
		}
		vec3 worldNormal = inverseTransformDirection( transformedNormal, viewMatrix );
		#ifdef ENVMAP_MODE_REFLECTION
			vReflect = reflect( cameraToVertex, worldNormal );
		#else
			vReflect = refract( cameraToVertex, worldNormal, refractionRatio );
		#endif
	#endif
#endif`,Uf=`#ifdef USE_FOG
	vFogDepth = - mvPosition.z;
#endif`,Nf=`#ifdef USE_FOG
	varying float vFogDepth;
#endif`,Of=`#ifdef USE_FOG
	#ifdef FOG_EXP2
		float fogFactor = 1.0 - exp( - fogDensity * fogDensity * vFogDepth * vFogDepth );
	#else
		float fogFactor = smoothstep( fogNear, fogFar, vFogDepth );
	#endif
	gl_FragColor.rgb = mix( gl_FragColor.rgb, fogColor, fogFactor );
#endif`,Bf=`#ifdef USE_FOG
	uniform vec3 fogColor;
	varying float vFogDepth;
	#ifdef FOG_EXP2
		uniform float fogDensity;
	#else
		uniform float fogNear;
		uniform float fogFar;
	#endif
#endif`,zf=`#ifdef USE_GRADIENTMAP
	uniform sampler2D gradientMap;
#endif
vec3 getGradientIrradiance( vec3 normal, vec3 lightDirection ) {
	float dotNL = dot( normal, lightDirection );
	vec2 coord = vec2( dotNL * 0.5 + 0.5, 0.0 );
	#ifdef USE_GRADIENTMAP
		return vec3( texture2D( gradientMap, coord ).r );
	#else
		vec2 fw = fwidth( coord ) * 0.5;
		return mix( vec3( 0.7 ), vec3( 1.0 ), smoothstep( 0.7 - fw.x, 0.7 + fw.x, coord.x ) );
	#endif
}`,kf=`#ifdef USE_LIGHTMAP
	uniform sampler2D lightMap;
	uniform float lightMapIntensity;
#endif`,Hf=`LambertMaterial material;
material.diffuseColor = diffuseColor.rgb;
material.specularStrength = specularStrength;`,Vf=`varying vec3 vViewPosition;
struct LambertMaterial {
	vec3 diffuseColor;
	float specularStrength;
};
void RE_Direct_Lambert( const in IncidentLight directLight, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in LambertMaterial material, inout ReflectedLight reflectedLight ) {
	float dotNL = saturate( dot( geometryNormal, directLight.direction ) );
	vec3 irradiance = dotNL * directLight.color;
	reflectedLight.directDiffuse += irradiance * BRDF_Lambert( material.diffuseColor );
}
void RE_IndirectDiffuse_Lambert( const in vec3 irradiance, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in LambertMaterial material, inout ReflectedLight reflectedLight ) {
	reflectedLight.indirectDiffuse += irradiance * BRDF_Lambert( material.diffuseColor );
}
#define RE_Direct				RE_Direct_Lambert
#define RE_IndirectDiffuse		RE_IndirectDiffuse_Lambert`,Gf=`uniform bool receiveShadow;
uniform vec3 ambientLightColor;
#if defined( USE_LIGHT_PROBES )
	uniform vec3 lightProbe[ 9 ];
#endif
vec3 shGetIrradianceAt( in vec3 normal, in vec3 shCoefficients[ 9 ] ) {
	float x = normal.x, y = normal.y, z = normal.z;
	vec3 result = shCoefficients[ 0 ] * 0.886227;
	result += shCoefficients[ 1 ] * 2.0 * 0.511664 * y;
	result += shCoefficients[ 2 ] * 2.0 * 0.511664 * z;
	result += shCoefficients[ 3 ] * 2.0 * 0.511664 * x;
	result += shCoefficients[ 4 ] * 2.0 * 0.429043 * x * y;
	result += shCoefficients[ 5 ] * 2.0 * 0.429043 * y * z;
	result += shCoefficients[ 6 ] * ( 0.743125 * z * z - 0.247708 );
	result += shCoefficients[ 7 ] * 2.0 * 0.429043 * x * z;
	result += shCoefficients[ 8 ] * 0.429043 * ( x * x - y * y );
	return result;
}
vec3 getLightProbeIrradiance( const in vec3 lightProbe[ 9 ], const in vec3 normal ) {
	vec3 worldNormal = inverseTransformDirection( normal, viewMatrix );
	vec3 irradiance = shGetIrradianceAt( worldNormal, lightProbe );
	return irradiance;
}
vec3 getAmbientLightIrradiance( const in vec3 ambientLightColor ) {
	vec3 irradiance = ambientLightColor;
	return irradiance;
}
float getDistanceAttenuation( const in float lightDistance, const in float cutoffDistance, const in float decayExponent ) {
	float distanceFalloff = 1.0 / max( pow( lightDistance, decayExponent ), 0.01 );
	if ( cutoffDistance > 0.0 ) {
		distanceFalloff *= pow2( saturate( 1.0 - pow4( lightDistance / cutoffDistance ) ) );
	}
	return distanceFalloff;
}
float getSpotAttenuation( const in float coneCosine, const in float penumbraCosine, const in float angleCosine ) {
	return smoothstep( coneCosine, penumbraCosine, angleCosine );
}
#if NUM_DIR_LIGHTS > 0
	struct DirectionalLight {
		vec3 direction;
		vec3 color;
	};
	uniform DirectionalLight directionalLights[ NUM_DIR_LIGHTS ];
	void getDirectionalLightInfo( const in DirectionalLight directionalLight, out IncidentLight light ) {
		light.color = directionalLight.color;
		light.direction = directionalLight.direction;
		light.visible = true;
	}
#endif
#if NUM_POINT_LIGHTS > 0
	struct PointLight {
		vec3 position;
		vec3 color;
		float distance;
		float decay;
	};
	uniform PointLight pointLights[ NUM_POINT_LIGHTS ];
	void getPointLightInfo( const in PointLight pointLight, const in vec3 geometryPosition, out IncidentLight light ) {
		vec3 lVector = pointLight.position - geometryPosition;
		light.direction = normalize( lVector );
		float lightDistance = length( lVector );
		light.color = pointLight.color;
		light.color *= getDistanceAttenuation( lightDistance, pointLight.distance, pointLight.decay );
		light.visible = ( light.color != vec3( 0.0 ) );
	}
#endif
#if NUM_SPOT_LIGHTS > 0
	struct SpotLight {
		vec3 position;
		vec3 direction;
		vec3 color;
		float distance;
		float decay;
		float coneCos;
		float penumbraCos;
	};
	uniform SpotLight spotLights[ NUM_SPOT_LIGHTS ];
	void getSpotLightInfo( const in SpotLight spotLight, const in vec3 geometryPosition, out IncidentLight light ) {
		vec3 lVector = spotLight.position - geometryPosition;
		light.direction = normalize( lVector );
		float angleCos = dot( light.direction, spotLight.direction );
		float spotAttenuation = getSpotAttenuation( spotLight.coneCos, spotLight.penumbraCos, angleCos );
		if ( spotAttenuation > 0.0 ) {
			float lightDistance = length( lVector );
			light.color = spotLight.color * spotAttenuation;
			light.color *= getDistanceAttenuation( lightDistance, spotLight.distance, spotLight.decay );
			light.visible = ( light.color != vec3( 0.0 ) );
		} else {
			light.color = vec3( 0.0 );
			light.visible = false;
		}
	}
#endif
#if NUM_RECT_AREA_LIGHTS > 0
	struct RectAreaLight {
		vec3 color;
		vec3 position;
		vec3 halfWidth;
		vec3 halfHeight;
	};
	uniform sampler2D ltc_1;	uniform sampler2D ltc_2;
	uniform RectAreaLight rectAreaLights[ NUM_RECT_AREA_LIGHTS ];
#endif
#if NUM_HEMI_LIGHTS > 0
	struct HemisphereLight {
		vec3 direction;
		vec3 skyColor;
		vec3 groundColor;
	};
	uniform HemisphereLight hemisphereLights[ NUM_HEMI_LIGHTS ];
	vec3 getHemisphereLightIrradiance( const in HemisphereLight hemiLight, const in vec3 normal ) {
		float dotNL = dot( normal, hemiLight.direction );
		float hemiDiffuseWeight = 0.5 * dotNL + 0.5;
		vec3 irradiance = mix( hemiLight.groundColor, hemiLight.skyColor, hemiDiffuseWeight );
		return irradiance;
	}
#endif`,Wf=`#ifdef USE_ENVMAP
	vec3 getIBLIrradiance( const in vec3 normal ) {
		#ifdef ENVMAP_TYPE_CUBE_UV
			vec3 worldNormal = inverseTransformDirection( normal, viewMatrix );
			vec4 envMapColor = textureCubeUV( envMap, envMapRotation * worldNormal, 1.0 );
			return PI * envMapColor.rgb * envMapIntensity;
		#else
			return vec3( 0.0 );
		#endif
	}
	vec3 getIBLRadiance( const in vec3 viewDir, const in vec3 normal, const in float roughness ) {
		#ifdef ENVMAP_TYPE_CUBE_UV
			vec3 reflectVec = reflect( - viewDir, normal );
			reflectVec = normalize( mix( reflectVec, normal, roughness * roughness) );
			reflectVec = inverseTransformDirection( reflectVec, viewMatrix );
			vec4 envMapColor = textureCubeUV( envMap, envMapRotation * reflectVec, roughness );
			return envMapColor.rgb * envMapIntensity;
		#else
			return vec3( 0.0 );
		#endif
	}
	#ifdef USE_ANISOTROPY
		vec3 getIBLAnisotropyRadiance( const in vec3 viewDir, const in vec3 normal, const in float roughness, const in vec3 bitangent, const in float anisotropy ) {
			#ifdef ENVMAP_TYPE_CUBE_UV
				vec3 bentNormal = cross( bitangent, viewDir );
				bentNormal = normalize( cross( bentNormal, bitangent ) );
				bentNormal = normalize( mix( bentNormal, normal, pow2( pow2( 1.0 - anisotropy * ( 1.0 - roughness ) ) ) ) );
				return getIBLRadiance( viewDir, bentNormal, roughness );
			#else
				return vec3( 0.0 );
			#endif
		}
	#endif
#endif`,Kf=`ToonMaterial material;
material.diffuseColor = diffuseColor.rgb;`,Xf=`varying vec3 vViewPosition;
struct ToonMaterial {
	vec3 diffuseColor;
};
void RE_Direct_Toon( const in IncidentLight directLight, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in ToonMaterial material, inout ReflectedLight reflectedLight ) {
	vec3 irradiance = getGradientIrradiance( geometryNormal, directLight.direction ) * directLight.color;
	reflectedLight.directDiffuse += irradiance * BRDF_Lambert( material.diffuseColor );
}
void RE_IndirectDiffuse_Toon( const in vec3 irradiance, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in ToonMaterial material, inout ReflectedLight reflectedLight ) {
	reflectedLight.indirectDiffuse += irradiance * BRDF_Lambert( material.diffuseColor );
}
#define RE_Direct				RE_Direct_Toon
#define RE_IndirectDiffuse		RE_IndirectDiffuse_Toon`,qf=`BlinnPhongMaterial material;
material.diffuseColor = diffuseColor.rgb;
material.specularColor = specular;
material.specularShininess = shininess;
material.specularStrength = specularStrength;`,Yf=`varying vec3 vViewPosition;
struct BlinnPhongMaterial {
	vec3 diffuseColor;
	vec3 specularColor;
	float specularShininess;
	float specularStrength;
};
void RE_Direct_BlinnPhong( const in IncidentLight directLight, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in BlinnPhongMaterial material, inout ReflectedLight reflectedLight ) {
	float dotNL = saturate( dot( geometryNormal, directLight.direction ) );
	vec3 irradiance = dotNL * directLight.color;
	reflectedLight.directDiffuse += irradiance * BRDF_Lambert( material.diffuseColor );
	reflectedLight.directSpecular += irradiance * BRDF_BlinnPhong( directLight.direction, geometryViewDir, geometryNormal, material.specularColor, material.specularShininess ) * material.specularStrength;
}
void RE_IndirectDiffuse_BlinnPhong( const in vec3 irradiance, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in BlinnPhongMaterial material, inout ReflectedLight reflectedLight ) {
	reflectedLight.indirectDiffuse += irradiance * BRDF_Lambert( material.diffuseColor );
}
#define RE_Direct				RE_Direct_BlinnPhong
#define RE_IndirectDiffuse		RE_IndirectDiffuse_BlinnPhong`,Zf=`PhysicalMaterial material;
material.diffuseColor = diffuseColor.rgb * ( 1.0 - metalnessFactor );
vec3 dxy = max( abs( dFdx( nonPerturbedNormal ) ), abs( dFdy( nonPerturbedNormal ) ) );
float geometryRoughness = max( max( dxy.x, dxy.y ), dxy.z );
material.roughness = max( roughnessFactor, 0.0525 );material.roughness += geometryRoughness;
material.roughness = min( material.roughness, 1.0 );
#ifdef IOR
	material.ior = ior;
	#ifdef USE_SPECULAR
		float specularIntensityFactor = specularIntensity;
		vec3 specularColorFactor = specularColor;
		#ifdef USE_SPECULAR_COLORMAP
			specularColorFactor *= texture2D( specularColorMap, vSpecularColorMapUv ).rgb;
		#endif
		#ifdef USE_SPECULAR_INTENSITYMAP
			specularIntensityFactor *= texture2D( specularIntensityMap, vSpecularIntensityMapUv ).a;
		#endif
		material.specularF90 = mix( specularIntensityFactor, 1.0, metalnessFactor );
	#else
		float specularIntensityFactor = 1.0;
		vec3 specularColorFactor = vec3( 1.0 );
		material.specularF90 = 1.0;
	#endif
	material.specularColor = mix( min( pow2( ( material.ior - 1.0 ) / ( material.ior + 1.0 ) ) * specularColorFactor, vec3( 1.0 ) ) * specularIntensityFactor, diffuseColor.rgb, metalnessFactor );
#else
	material.specularColor = mix( vec3( 0.04 ), diffuseColor.rgb, metalnessFactor );
	material.specularF90 = 1.0;
#endif
#ifdef USE_CLEARCOAT
	material.clearcoat = clearcoat;
	material.clearcoatRoughness = clearcoatRoughness;
	material.clearcoatF0 = vec3( 0.04 );
	material.clearcoatF90 = 1.0;
	#ifdef USE_CLEARCOATMAP
		material.clearcoat *= texture2D( clearcoatMap, vClearcoatMapUv ).x;
	#endif
	#ifdef USE_CLEARCOAT_ROUGHNESSMAP
		material.clearcoatRoughness *= texture2D( clearcoatRoughnessMap, vClearcoatRoughnessMapUv ).y;
	#endif
	material.clearcoat = saturate( material.clearcoat );	material.clearcoatRoughness = max( material.clearcoatRoughness, 0.0525 );
	material.clearcoatRoughness += geometryRoughness;
	material.clearcoatRoughness = min( material.clearcoatRoughness, 1.0 );
#endif
#ifdef USE_DISPERSION
	material.dispersion = dispersion;
#endif
#ifdef USE_IRIDESCENCE
	material.iridescence = iridescence;
	material.iridescenceIOR = iridescenceIOR;
	#ifdef USE_IRIDESCENCEMAP
		material.iridescence *= texture2D( iridescenceMap, vIridescenceMapUv ).r;
	#endif
	#ifdef USE_IRIDESCENCE_THICKNESSMAP
		material.iridescenceThickness = (iridescenceThicknessMaximum - iridescenceThicknessMinimum) * texture2D( iridescenceThicknessMap, vIridescenceThicknessMapUv ).g + iridescenceThicknessMinimum;
	#else
		material.iridescenceThickness = iridescenceThicknessMaximum;
	#endif
#endif
#ifdef USE_SHEEN
	material.sheenColor = sheenColor;
	#ifdef USE_SHEEN_COLORMAP
		material.sheenColor *= texture2D( sheenColorMap, vSheenColorMapUv ).rgb;
	#endif
	material.sheenRoughness = clamp( sheenRoughness, 0.07, 1.0 );
	#ifdef USE_SHEEN_ROUGHNESSMAP
		material.sheenRoughness *= texture2D( sheenRoughnessMap, vSheenRoughnessMapUv ).a;
	#endif
#endif
#ifdef USE_ANISOTROPY
	#ifdef USE_ANISOTROPYMAP
		mat2 anisotropyMat = mat2( anisotropyVector.x, anisotropyVector.y, - anisotropyVector.y, anisotropyVector.x );
		vec3 anisotropyPolar = texture2D( anisotropyMap, vAnisotropyMapUv ).rgb;
		vec2 anisotropyV = anisotropyMat * normalize( 2.0 * anisotropyPolar.rg - vec2( 1.0 ) ) * anisotropyPolar.b;
	#else
		vec2 anisotropyV = anisotropyVector;
	#endif
	material.anisotropy = length( anisotropyV );
	if( material.anisotropy == 0.0 ) {
		anisotropyV = vec2( 1.0, 0.0 );
	} else {
		anisotropyV /= material.anisotropy;
		material.anisotropy = saturate( material.anisotropy );
	}
	material.alphaT = mix( pow2( material.roughness ), 1.0, pow2( material.anisotropy ) );
	material.anisotropyT = tbn[ 0 ] * anisotropyV.x + tbn[ 1 ] * anisotropyV.y;
	material.anisotropyB = tbn[ 1 ] * anisotropyV.x - tbn[ 0 ] * anisotropyV.y;
#endif`,$f=`struct PhysicalMaterial {
	vec3 diffuseColor;
	float roughness;
	vec3 specularColor;
	float specularF90;
	float dispersion;
	#ifdef USE_CLEARCOAT
		float clearcoat;
		float clearcoatRoughness;
		vec3 clearcoatF0;
		float clearcoatF90;
	#endif
	#ifdef USE_IRIDESCENCE
		float iridescence;
		float iridescenceIOR;
		float iridescenceThickness;
		vec3 iridescenceFresnel;
		vec3 iridescenceF0;
	#endif
	#ifdef USE_SHEEN
		vec3 sheenColor;
		float sheenRoughness;
	#endif
	#ifdef IOR
		float ior;
	#endif
	#ifdef USE_TRANSMISSION
		float transmission;
		float transmissionAlpha;
		float thickness;
		float attenuationDistance;
		vec3 attenuationColor;
	#endif
	#ifdef USE_ANISOTROPY
		float anisotropy;
		float alphaT;
		vec3 anisotropyT;
		vec3 anisotropyB;
	#endif
};
vec3 clearcoatSpecularDirect = vec3( 0.0 );
vec3 clearcoatSpecularIndirect = vec3( 0.0 );
vec3 sheenSpecularDirect = vec3( 0.0 );
vec3 sheenSpecularIndirect = vec3(0.0 );
vec3 Schlick_to_F0( const in vec3 f, const in float f90, const in float dotVH ) {
    float x = clamp( 1.0 - dotVH, 0.0, 1.0 );
    float x2 = x * x;
    float x5 = clamp( x * x2 * x2, 0.0, 0.9999 );
    return ( f - vec3( f90 ) * x5 ) / ( 1.0 - x5 );
}
float V_GGX_SmithCorrelated( const in float alpha, const in float dotNL, const in float dotNV ) {
	float a2 = pow2( alpha );
	float gv = dotNL * sqrt( a2 + ( 1.0 - a2 ) * pow2( dotNV ) );
	float gl = dotNV * sqrt( a2 + ( 1.0 - a2 ) * pow2( dotNL ) );
	return 0.5 / max( gv + gl, EPSILON );
}
float D_GGX( const in float alpha, const in float dotNH ) {
	float a2 = pow2( alpha );
	float denom = pow2( dotNH ) * ( a2 - 1.0 ) + 1.0;
	return RECIPROCAL_PI * a2 / pow2( denom );
}
#ifdef USE_ANISOTROPY
	float V_GGX_SmithCorrelated_Anisotropic( const in float alphaT, const in float alphaB, const in float dotTV, const in float dotBV, const in float dotTL, const in float dotBL, const in float dotNV, const in float dotNL ) {
		float gv = dotNL * length( vec3( alphaT * dotTV, alphaB * dotBV, dotNV ) );
		float gl = dotNV * length( vec3( alphaT * dotTL, alphaB * dotBL, dotNL ) );
		float v = 0.5 / ( gv + gl );
		return saturate(v);
	}
	float D_GGX_Anisotropic( const in float alphaT, const in float alphaB, const in float dotNH, const in float dotTH, const in float dotBH ) {
		float a2 = alphaT * alphaB;
		highp vec3 v = vec3( alphaB * dotTH, alphaT * dotBH, a2 * dotNH );
		highp float v2 = dot( v, v );
		float w2 = a2 / v2;
		return RECIPROCAL_PI * a2 * pow2 ( w2 );
	}
#endif
#ifdef USE_CLEARCOAT
	vec3 BRDF_GGX_Clearcoat( const in vec3 lightDir, const in vec3 viewDir, const in vec3 normal, const in PhysicalMaterial material) {
		vec3 f0 = material.clearcoatF0;
		float f90 = material.clearcoatF90;
		float roughness = material.clearcoatRoughness;
		float alpha = pow2( roughness );
		vec3 halfDir = normalize( lightDir + viewDir );
		float dotNL = saturate( dot( normal, lightDir ) );
		float dotNV = saturate( dot( normal, viewDir ) );
		float dotNH = saturate( dot( normal, halfDir ) );
		float dotVH = saturate( dot( viewDir, halfDir ) );
		vec3 F = F_Schlick( f0, f90, dotVH );
		float V = V_GGX_SmithCorrelated( alpha, dotNL, dotNV );
		float D = D_GGX( alpha, dotNH );
		return F * ( V * D );
	}
#endif
vec3 BRDF_GGX( const in vec3 lightDir, const in vec3 viewDir, const in vec3 normal, const in PhysicalMaterial material ) {
	vec3 f0 = material.specularColor;
	float f90 = material.specularF90;
	float roughness = material.roughness;
	float alpha = pow2( roughness );
	vec3 halfDir = normalize( lightDir + viewDir );
	float dotNL = saturate( dot( normal, lightDir ) );
	float dotNV = saturate( dot( normal, viewDir ) );
	float dotNH = saturate( dot( normal, halfDir ) );
	float dotVH = saturate( dot( viewDir, halfDir ) );
	vec3 F = F_Schlick( f0, f90, dotVH );
	#ifdef USE_IRIDESCENCE
		F = mix( F, material.iridescenceFresnel, material.iridescence );
	#endif
	#ifdef USE_ANISOTROPY
		float dotTL = dot( material.anisotropyT, lightDir );
		float dotTV = dot( material.anisotropyT, viewDir );
		float dotTH = dot( material.anisotropyT, halfDir );
		float dotBL = dot( material.anisotropyB, lightDir );
		float dotBV = dot( material.anisotropyB, viewDir );
		float dotBH = dot( material.anisotropyB, halfDir );
		float V = V_GGX_SmithCorrelated_Anisotropic( material.alphaT, alpha, dotTV, dotBV, dotTL, dotBL, dotNV, dotNL );
		float D = D_GGX_Anisotropic( material.alphaT, alpha, dotNH, dotTH, dotBH );
	#else
		float V = V_GGX_SmithCorrelated( alpha, dotNL, dotNV );
		float D = D_GGX( alpha, dotNH );
	#endif
	return F * ( V * D );
}
vec2 LTC_Uv( const in vec3 N, const in vec3 V, const in float roughness ) {
	const float LUT_SIZE = 64.0;
	const float LUT_SCALE = ( LUT_SIZE - 1.0 ) / LUT_SIZE;
	const float LUT_BIAS = 0.5 / LUT_SIZE;
	float dotNV = saturate( dot( N, V ) );
	vec2 uv = vec2( roughness, sqrt( 1.0 - dotNV ) );
	uv = uv * LUT_SCALE + LUT_BIAS;
	return uv;
}
float LTC_ClippedSphereFormFactor( const in vec3 f ) {
	float l = length( f );
	return max( ( l * l + f.z ) / ( l + 1.0 ), 0.0 );
}
vec3 LTC_EdgeVectorFormFactor( const in vec3 v1, const in vec3 v2 ) {
	float x = dot( v1, v2 );
	float y = abs( x );
	float a = 0.8543985 + ( 0.4965155 + 0.0145206 * y ) * y;
	float b = 3.4175940 + ( 4.1616724 + y ) * y;
	float v = a / b;
	float theta_sintheta = ( x > 0.0 ) ? v : 0.5 * inversesqrt( max( 1.0 - x * x, 1e-7 ) ) - v;
	return cross( v1, v2 ) * theta_sintheta;
}
vec3 LTC_Evaluate( const in vec3 N, const in vec3 V, const in vec3 P, const in mat3 mInv, const in vec3 rectCoords[ 4 ] ) {
	vec3 v1 = rectCoords[ 1 ] - rectCoords[ 0 ];
	vec3 v2 = rectCoords[ 3 ] - rectCoords[ 0 ];
	vec3 lightNormal = cross( v1, v2 );
	if( dot( lightNormal, P - rectCoords[ 0 ] ) < 0.0 ) return vec3( 0.0 );
	vec3 T1, T2;
	T1 = normalize( V - N * dot( V, N ) );
	T2 = - cross( N, T1 );
	mat3 mat = mInv * transposeMat3( mat3( T1, T2, N ) );
	vec3 coords[ 4 ];
	coords[ 0 ] = mat * ( rectCoords[ 0 ] - P );
	coords[ 1 ] = mat * ( rectCoords[ 1 ] - P );
	coords[ 2 ] = mat * ( rectCoords[ 2 ] - P );
	coords[ 3 ] = mat * ( rectCoords[ 3 ] - P );
	coords[ 0 ] = normalize( coords[ 0 ] );
	coords[ 1 ] = normalize( coords[ 1 ] );
	coords[ 2 ] = normalize( coords[ 2 ] );
	coords[ 3 ] = normalize( coords[ 3 ] );
	vec3 vectorFormFactor = vec3( 0.0 );
	vectorFormFactor += LTC_EdgeVectorFormFactor( coords[ 0 ], coords[ 1 ] );
	vectorFormFactor += LTC_EdgeVectorFormFactor( coords[ 1 ], coords[ 2 ] );
	vectorFormFactor += LTC_EdgeVectorFormFactor( coords[ 2 ], coords[ 3 ] );
	vectorFormFactor += LTC_EdgeVectorFormFactor( coords[ 3 ], coords[ 0 ] );
	float result = LTC_ClippedSphereFormFactor( vectorFormFactor );
	return vec3( result );
}
#if defined( USE_SHEEN )
float D_Charlie( float roughness, float dotNH ) {
	float alpha = pow2( roughness );
	float invAlpha = 1.0 / alpha;
	float cos2h = dotNH * dotNH;
	float sin2h = max( 1.0 - cos2h, 0.0078125 );
	return ( 2.0 + invAlpha ) * pow( sin2h, invAlpha * 0.5 ) / ( 2.0 * PI );
}
float V_Neubelt( float dotNV, float dotNL ) {
	return saturate( 1.0 / ( 4.0 * ( dotNL + dotNV - dotNL * dotNV ) ) );
}
vec3 BRDF_Sheen( const in vec3 lightDir, const in vec3 viewDir, const in vec3 normal, vec3 sheenColor, const in float sheenRoughness ) {
	vec3 halfDir = normalize( lightDir + viewDir );
	float dotNL = saturate( dot( normal, lightDir ) );
	float dotNV = saturate( dot( normal, viewDir ) );
	float dotNH = saturate( dot( normal, halfDir ) );
	float D = D_Charlie( sheenRoughness, dotNH );
	float V = V_Neubelt( dotNV, dotNL );
	return sheenColor * ( D * V );
}
#endif
float IBLSheenBRDF( const in vec3 normal, const in vec3 viewDir, const in float roughness ) {
	float dotNV = saturate( dot( normal, viewDir ) );
	float r2 = roughness * roughness;
	float a = roughness < 0.25 ? -339.2 * r2 + 161.4 * roughness - 25.9 : -8.48 * r2 + 14.3 * roughness - 9.95;
	float b = roughness < 0.25 ? 44.0 * r2 - 23.7 * roughness + 3.26 : 1.97 * r2 - 3.27 * roughness + 0.72;
	float DG = exp( a * dotNV + b ) + ( roughness < 0.25 ? 0.0 : 0.1 * ( roughness - 0.25 ) );
	return saturate( DG * RECIPROCAL_PI );
}
vec2 DFGApprox( const in vec3 normal, const in vec3 viewDir, const in float roughness ) {
	float dotNV = saturate( dot( normal, viewDir ) );
	const vec4 c0 = vec4( - 1, - 0.0275, - 0.572, 0.022 );
	const vec4 c1 = vec4( 1, 0.0425, 1.04, - 0.04 );
	vec4 r = roughness * c0 + c1;
	float a004 = min( r.x * r.x, exp2( - 9.28 * dotNV ) ) * r.x + r.y;
	vec2 fab = vec2( - 1.04, 1.04 ) * a004 + r.zw;
	return fab;
}
vec3 EnvironmentBRDF( const in vec3 normal, const in vec3 viewDir, const in vec3 specularColor, const in float specularF90, const in float roughness ) {
	vec2 fab = DFGApprox( normal, viewDir, roughness );
	return specularColor * fab.x + specularF90 * fab.y;
}
#ifdef USE_IRIDESCENCE
void computeMultiscatteringIridescence( const in vec3 normal, const in vec3 viewDir, const in vec3 specularColor, const in float specularF90, const in float iridescence, const in vec3 iridescenceF0, const in float roughness, inout vec3 singleScatter, inout vec3 multiScatter ) {
#else
void computeMultiscattering( const in vec3 normal, const in vec3 viewDir, const in vec3 specularColor, const in float specularF90, const in float roughness, inout vec3 singleScatter, inout vec3 multiScatter ) {
#endif
	vec2 fab = DFGApprox( normal, viewDir, roughness );
	#ifdef USE_IRIDESCENCE
		vec3 Fr = mix( specularColor, iridescenceF0, iridescence );
	#else
		vec3 Fr = specularColor;
	#endif
	vec3 FssEss = Fr * fab.x + specularF90 * fab.y;
	float Ess = fab.x + fab.y;
	float Ems = 1.0 - Ess;
	vec3 Favg = Fr + ( 1.0 - Fr ) * 0.047619;	vec3 Fms = FssEss * Favg / ( 1.0 - Ems * Favg );
	singleScatter += FssEss;
	multiScatter += Fms * Ems;
}
#if NUM_RECT_AREA_LIGHTS > 0
	void RE_Direct_RectArea_Physical( const in RectAreaLight rectAreaLight, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in PhysicalMaterial material, inout ReflectedLight reflectedLight ) {
		vec3 normal = geometryNormal;
		vec3 viewDir = geometryViewDir;
		vec3 position = geometryPosition;
		vec3 lightPos = rectAreaLight.position;
		vec3 halfWidth = rectAreaLight.halfWidth;
		vec3 halfHeight = rectAreaLight.halfHeight;
		vec3 lightColor = rectAreaLight.color;
		float roughness = material.roughness;
		vec3 rectCoords[ 4 ];
		rectCoords[ 0 ] = lightPos + halfWidth - halfHeight;		rectCoords[ 1 ] = lightPos - halfWidth - halfHeight;
		rectCoords[ 2 ] = lightPos - halfWidth + halfHeight;
		rectCoords[ 3 ] = lightPos + halfWidth + halfHeight;
		vec2 uv = LTC_Uv( normal, viewDir, roughness );
		vec4 t1 = texture2D( ltc_1, uv );
		vec4 t2 = texture2D( ltc_2, uv );
		mat3 mInv = mat3(
			vec3( t1.x, 0, t1.y ),
			vec3(    0, 1,    0 ),
			vec3( t1.z, 0, t1.w )
		);
		vec3 fresnel = ( material.specularColor * t2.x + ( vec3( 1.0 ) - material.specularColor ) * t2.y );
		reflectedLight.directSpecular += lightColor * fresnel * LTC_Evaluate( normal, viewDir, position, mInv, rectCoords );
		reflectedLight.directDiffuse += lightColor * material.diffuseColor * LTC_Evaluate( normal, viewDir, position, mat3( 1.0 ), rectCoords );
	}
#endif
void RE_Direct_Physical( const in IncidentLight directLight, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in PhysicalMaterial material, inout ReflectedLight reflectedLight ) {
	float dotNL = saturate( dot( geometryNormal, directLight.direction ) );
	vec3 irradiance = dotNL * directLight.color;
	#ifdef USE_CLEARCOAT
		float dotNLcc = saturate( dot( geometryClearcoatNormal, directLight.direction ) );
		vec3 ccIrradiance = dotNLcc * directLight.color;
		clearcoatSpecularDirect += ccIrradiance * BRDF_GGX_Clearcoat( directLight.direction, geometryViewDir, geometryClearcoatNormal, material );
	#endif
	#ifdef USE_SHEEN
		sheenSpecularDirect += irradiance * BRDF_Sheen( directLight.direction, geometryViewDir, geometryNormal, material.sheenColor, material.sheenRoughness );
	#endif
	reflectedLight.directSpecular += irradiance * BRDF_GGX( directLight.direction, geometryViewDir, geometryNormal, material );
	reflectedLight.directDiffuse += irradiance * BRDF_Lambert( material.diffuseColor );
}
void RE_IndirectDiffuse_Physical( const in vec3 irradiance, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in PhysicalMaterial material, inout ReflectedLight reflectedLight ) {
	reflectedLight.indirectDiffuse += irradiance * BRDF_Lambert( material.diffuseColor );
}
void RE_IndirectSpecular_Physical( const in vec3 radiance, const in vec3 irradiance, const in vec3 clearcoatRadiance, const in vec3 geometryPosition, const in vec3 geometryNormal, const in vec3 geometryViewDir, const in vec3 geometryClearcoatNormal, const in PhysicalMaterial material, inout ReflectedLight reflectedLight) {
	#ifdef USE_CLEARCOAT
		clearcoatSpecularIndirect += clearcoatRadiance * EnvironmentBRDF( geometryClearcoatNormal, geometryViewDir, material.clearcoatF0, material.clearcoatF90, material.clearcoatRoughness );
	#endif
	#ifdef USE_SHEEN
		sheenSpecularIndirect += irradiance * material.sheenColor * IBLSheenBRDF( geometryNormal, geometryViewDir, material.sheenRoughness );
	#endif
	vec3 singleScattering = vec3( 0.0 );
	vec3 multiScattering = vec3( 0.0 );
	vec3 cosineWeightedIrradiance = irradiance * RECIPROCAL_PI;
	#ifdef USE_IRIDESCENCE
		computeMultiscatteringIridescence( geometryNormal, geometryViewDir, material.specularColor, material.specularF90, material.iridescence, material.iridescenceFresnel, material.roughness, singleScattering, multiScattering );
	#else
		computeMultiscattering( geometryNormal, geometryViewDir, material.specularColor, material.specularF90, material.roughness, singleScattering, multiScattering );
	#endif
	vec3 totalScattering = singleScattering + multiScattering;
	vec3 diffuse = material.diffuseColor * ( 1.0 - max( max( totalScattering.r, totalScattering.g ), totalScattering.b ) );
	reflectedLight.indirectSpecular += radiance * singleScattering;
	reflectedLight.indirectSpecular += multiScattering * cosineWeightedIrradiance;
	reflectedLight.indirectDiffuse += diffuse * cosineWeightedIrradiance;
}
#define RE_Direct				RE_Direct_Physical
#define RE_Direct_RectArea		RE_Direct_RectArea_Physical
#define RE_IndirectDiffuse		RE_IndirectDiffuse_Physical
#define RE_IndirectSpecular		RE_IndirectSpecular_Physical
float computeSpecularOcclusion( const in float dotNV, const in float ambientOcclusion, const in float roughness ) {
	return saturate( pow( dotNV + ambientOcclusion, exp2( - 16.0 * roughness - 1.0 ) ) - 1.0 + ambientOcclusion );
}`,Jf=`
vec3 geometryPosition = - vViewPosition;
vec3 geometryNormal = normal;
vec3 geometryViewDir = ( isOrthographic ) ? vec3( 0, 0, 1 ) : normalize( vViewPosition );
vec3 geometryClearcoatNormal = vec3( 0.0 );
#ifdef USE_CLEARCOAT
	geometryClearcoatNormal = clearcoatNormal;
#endif
#ifdef USE_IRIDESCENCE
	float dotNVi = saturate( dot( normal, geometryViewDir ) );
	if ( material.iridescenceThickness == 0.0 ) {
		material.iridescence = 0.0;
	} else {
		material.iridescence = saturate( material.iridescence );
	}
	if ( material.iridescence > 0.0 ) {
		material.iridescenceFresnel = evalIridescence( 1.0, material.iridescenceIOR, dotNVi, material.iridescenceThickness, material.specularColor );
		material.iridescenceF0 = Schlick_to_F0( material.iridescenceFresnel, 1.0, dotNVi );
	}
#endif
IncidentLight directLight;
#if ( NUM_POINT_LIGHTS > 0 ) && defined( RE_Direct )
	PointLight pointLight;
	#if defined( USE_SHADOWMAP ) && NUM_POINT_LIGHT_SHADOWS > 0
	PointLightShadow pointLightShadow;
	#endif
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_POINT_LIGHTS; i ++ ) {
		pointLight = pointLights[ i ];
		getPointLightInfo( pointLight, geometryPosition, directLight );
		#if defined( USE_SHADOWMAP ) && ( UNROLLED_LOOP_INDEX < NUM_POINT_LIGHT_SHADOWS )
		pointLightShadow = pointLightShadows[ i ];
		directLight.color *= ( directLight.visible && receiveShadow ) ? getPointShadow( pointShadowMap[ i ], pointLightShadow.shadowMapSize, pointLightShadow.shadowIntensity, pointLightShadow.shadowBias, pointLightShadow.shadowRadius, vPointShadowCoord[ i ], pointLightShadow.shadowCameraNear, pointLightShadow.shadowCameraFar ) : 1.0;
		#endif
		RE_Direct( directLight, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
	}
	#pragma unroll_loop_end
#endif
#if ( NUM_SPOT_LIGHTS > 0 ) && defined( RE_Direct )
	SpotLight spotLight;
	vec4 spotColor;
	vec3 spotLightCoord;
	bool inSpotLightMap;
	#if defined( USE_SHADOWMAP ) && NUM_SPOT_LIGHT_SHADOWS > 0
	SpotLightShadow spotLightShadow;
	#endif
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_SPOT_LIGHTS; i ++ ) {
		spotLight = spotLights[ i ];
		getSpotLightInfo( spotLight, geometryPosition, directLight );
		#if ( UNROLLED_LOOP_INDEX < NUM_SPOT_LIGHT_SHADOWS_WITH_MAPS )
		#define SPOT_LIGHT_MAP_INDEX UNROLLED_LOOP_INDEX
		#elif ( UNROLLED_LOOP_INDEX < NUM_SPOT_LIGHT_SHADOWS )
		#define SPOT_LIGHT_MAP_INDEX NUM_SPOT_LIGHT_MAPS
		#else
		#define SPOT_LIGHT_MAP_INDEX ( UNROLLED_LOOP_INDEX - NUM_SPOT_LIGHT_SHADOWS + NUM_SPOT_LIGHT_SHADOWS_WITH_MAPS )
		#endif
		#if ( SPOT_LIGHT_MAP_INDEX < NUM_SPOT_LIGHT_MAPS )
			spotLightCoord = vSpotLightCoord[ i ].xyz / vSpotLightCoord[ i ].w;
			inSpotLightMap = all( lessThan( abs( spotLightCoord * 2. - 1. ), vec3( 1.0 ) ) );
			spotColor = texture2D( spotLightMap[ SPOT_LIGHT_MAP_INDEX ], spotLightCoord.xy );
			directLight.color = inSpotLightMap ? directLight.color * spotColor.rgb : directLight.color;
		#endif
		#undef SPOT_LIGHT_MAP_INDEX
		#if defined( USE_SHADOWMAP ) && ( UNROLLED_LOOP_INDEX < NUM_SPOT_LIGHT_SHADOWS )
		spotLightShadow = spotLightShadows[ i ];
		directLight.color *= ( directLight.visible && receiveShadow ) ? getShadow( spotShadowMap[ i ], spotLightShadow.shadowMapSize, spotLightShadow.shadowIntensity, spotLightShadow.shadowBias, spotLightShadow.shadowRadius, vSpotLightCoord[ i ] ) : 1.0;
		#endif
		RE_Direct( directLight, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
	}
	#pragma unroll_loop_end
#endif
#if ( NUM_DIR_LIGHTS > 0 ) && defined( RE_Direct )
	DirectionalLight directionalLight;
	#if defined( USE_SHADOWMAP ) && NUM_DIR_LIGHT_SHADOWS > 0
	DirectionalLightShadow directionalLightShadow;
	#endif
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_DIR_LIGHTS; i ++ ) {
		directionalLight = directionalLights[ i ];
		getDirectionalLightInfo( directionalLight, directLight );
		#if defined( USE_SHADOWMAP ) && ( UNROLLED_LOOP_INDEX < NUM_DIR_LIGHT_SHADOWS )
		directionalLightShadow = directionalLightShadows[ i ];
		directLight.color *= ( directLight.visible && receiveShadow ) ? getShadow( directionalShadowMap[ i ], directionalLightShadow.shadowMapSize, directionalLightShadow.shadowIntensity, directionalLightShadow.shadowBias, directionalLightShadow.shadowRadius, vDirectionalShadowCoord[ i ] ) : 1.0;
		#endif
		RE_Direct( directLight, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
	}
	#pragma unroll_loop_end
#endif
#if ( NUM_RECT_AREA_LIGHTS > 0 ) && defined( RE_Direct_RectArea )
	RectAreaLight rectAreaLight;
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_RECT_AREA_LIGHTS; i ++ ) {
		rectAreaLight = rectAreaLights[ i ];
		RE_Direct_RectArea( rectAreaLight, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
	}
	#pragma unroll_loop_end
#endif
#if defined( RE_IndirectDiffuse )
	vec3 iblIrradiance = vec3( 0.0 );
	vec3 irradiance = getAmbientLightIrradiance( ambientLightColor );
	#if defined( USE_LIGHT_PROBES )
		irradiance += getLightProbeIrradiance( lightProbe, geometryNormal );
	#endif
	#if ( NUM_HEMI_LIGHTS > 0 )
		#pragma unroll_loop_start
		for ( int i = 0; i < NUM_HEMI_LIGHTS; i ++ ) {
			irradiance += getHemisphereLightIrradiance( hemisphereLights[ i ], geometryNormal );
		}
		#pragma unroll_loop_end
	#endif
#endif
#if defined( RE_IndirectSpecular )
	vec3 radiance = vec3( 0.0 );
	vec3 clearcoatRadiance = vec3( 0.0 );
#endif`,Qf=`#if defined( RE_IndirectDiffuse )
	#ifdef USE_LIGHTMAP
		vec4 lightMapTexel = texture2D( lightMap, vLightMapUv );
		vec3 lightMapIrradiance = lightMapTexel.rgb * lightMapIntensity;
		irradiance += lightMapIrradiance;
	#endif
	#if defined( USE_ENVMAP ) && defined( STANDARD ) && defined( ENVMAP_TYPE_CUBE_UV )
		iblIrradiance += getIBLIrradiance( geometryNormal );
	#endif
#endif
#if defined( USE_ENVMAP ) && defined( RE_IndirectSpecular )
	#ifdef USE_ANISOTROPY
		radiance += getIBLAnisotropyRadiance( geometryViewDir, geometryNormal, material.roughness, material.anisotropyB, material.anisotropy );
	#else
		radiance += getIBLRadiance( geometryViewDir, geometryNormal, material.roughness );
	#endif
	#ifdef USE_CLEARCOAT
		clearcoatRadiance += getIBLRadiance( geometryViewDir, geometryClearcoatNormal, material.clearcoatRoughness );
	#endif
#endif`,jf=`#if defined( RE_IndirectDiffuse )
	RE_IndirectDiffuse( irradiance, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
#endif
#if defined( RE_IndirectSpecular )
	RE_IndirectSpecular( radiance, iblIrradiance, clearcoatRadiance, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
#endif`,tp=`#if defined( USE_LOGDEPTHBUF )
	gl_FragDepth = vIsPerspective == 0.0 ? gl_FragCoord.z : log2( vFragDepth ) * logDepthBufFC * 0.5;
#endif`,ep=`#if defined( USE_LOGDEPTHBUF )
	uniform float logDepthBufFC;
	varying float vFragDepth;
	varying float vIsPerspective;
#endif`,np=`#ifdef USE_LOGDEPTHBUF
	varying float vFragDepth;
	varying float vIsPerspective;
#endif`,ip=`#ifdef USE_LOGDEPTHBUF
	vFragDepth = 1.0 + gl_Position.w;
	vIsPerspective = float( isPerspectiveMatrix( projectionMatrix ) );
#endif`,sp=`#ifdef USE_MAP
	vec4 sampledDiffuseColor = texture2D( map, vMapUv );
	#ifdef DECODE_VIDEO_TEXTURE
		sampledDiffuseColor = sRGBTransferEOTF( sampledDiffuseColor );
	#endif
	diffuseColor *= sampledDiffuseColor;
#endif`,rp=`#ifdef USE_MAP
	uniform sampler2D map;
#endif`,ap=`#if defined( USE_MAP ) || defined( USE_ALPHAMAP )
	#if defined( USE_POINTS_UV )
		vec2 uv = vUv;
	#else
		vec2 uv = ( uvTransform * vec3( gl_PointCoord.x, 1.0 - gl_PointCoord.y, 1 ) ).xy;
	#endif
#endif
#ifdef USE_MAP
	diffuseColor *= texture2D( map, uv );
#endif
#ifdef USE_ALPHAMAP
	diffuseColor.a *= texture2D( alphaMap, uv ).g;
#endif`,op=`#if defined( USE_POINTS_UV )
	varying vec2 vUv;
#else
	#if defined( USE_MAP ) || defined( USE_ALPHAMAP )
		uniform mat3 uvTransform;
	#endif
#endif
#ifdef USE_MAP
	uniform sampler2D map;
#endif
#ifdef USE_ALPHAMAP
	uniform sampler2D alphaMap;
#endif`,lp=`float metalnessFactor = metalness;
#ifdef USE_METALNESSMAP
	vec4 texelMetalness = texture2D( metalnessMap, vMetalnessMapUv );
	metalnessFactor *= texelMetalness.b;
#endif`,cp=`#ifdef USE_METALNESSMAP
	uniform sampler2D metalnessMap;
#endif`,hp=`#ifdef USE_INSTANCING_MORPH
	float morphTargetInfluences[ MORPHTARGETS_COUNT ];
	float morphTargetBaseInfluence = texelFetch( morphTexture, ivec2( 0, gl_InstanceID ), 0 ).r;
	for ( int i = 0; i < MORPHTARGETS_COUNT; i ++ ) {
		morphTargetInfluences[i] =  texelFetch( morphTexture, ivec2( i + 1, gl_InstanceID ), 0 ).r;
	}
#endif`,up=`#if defined( USE_MORPHCOLORS )
	vColor *= morphTargetBaseInfluence;
	for ( int i = 0; i < MORPHTARGETS_COUNT; i ++ ) {
		#if defined( USE_COLOR_ALPHA )
			if ( morphTargetInfluences[ i ] != 0.0 ) vColor += getMorph( gl_VertexID, i, 2 ) * morphTargetInfluences[ i ];
		#elif defined( USE_COLOR )
			if ( morphTargetInfluences[ i ] != 0.0 ) vColor += getMorph( gl_VertexID, i, 2 ).rgb * morphTargetInfluences[ i ];
		#endif
	}
#endif`,dp=`#ifdef USE_MORPHNORMALS
	objectNormal *= morphTargetBaseInfluence;
	for ( int i = 0; i < MORPHTARGETS_COUNT; i ++ ) {
		if ( morphTargetInfluences[ i ] != 0.0 ) objectNormal += getMorph( gl_VertexID, i, 1 ).xyz * morphTargetInfluences[ i ];
	}
#endif`,fp=`#ifdef USE_MORPHTARGETS
	#ifndef USE_INSTANCING_MORPH
		uniform float morphTargetBaseInfluence;
		uniform float morphTargetInfluences[ MORPHTARGETS_COUNT ];
	#endif
	uniform sampler2DArray morphTargetsTexture;
	uniform ivec2 morphTargetsTextureSize;
	vec4 getMorph( const in int vertexIndex, const in int morphTargetIndex, const in int offset ) {
		int texelIndex = vertexIndex * MORPHTARGETS_TEXTURE_STRIDE + offset;
		int y = texelIndex / morphTargetsTextureSize.x;
		int x = texelIndex - y * morphTargetsTextureSize.x;
		ivec3 morphUV = ivec3( x, y, morphTargetIndex );
		return texelFetch( morphTargetsTexture, morphUV, 0 );
	}
#endif`,pp=`#ifdef USE_MORPHTARGETS
	transformed *= morphTargetBaseInfluence;
	for ( int i = 0; i < MORPHTARGETS_COUNT; i ++ ) {
		if ( morphTargetInfluences[ i ] != 0.0 ) transformed += getMorph( gl_VertexID, i, 0 ).xyz * morphTargetInfluences[ i ];
	}
#endif`,mp=`float faceDirection = gl_FrontFacing ? 1.0 : - 1.0;
#ifdef FLAT_SHADED
	vec3 fdx = dFdx( vViewPosition );
	vec3 fdy = dFdy( vViewPosition );
	vec3 normal = normalize( cross( fdx, fdy ) );
#else
	vec3 normal = normalize( vNormal );
	#ifdef DOUBLE_SIDED
		normal *= faceDirection;
	#endif
#endif
#if defined( USE_NORMALMAP_TANGENTSPACE ) || defined( USE_CLEARCOAT_NORMALMAP ) || defined( USE_ANISOTROPY )
	#ifdef USE_TANGENT
		mat3 tbn = mat3( normalize( vTangent ), normalize( vBitangent ), normal );
	#else
		mat3 tbn = getTangentFrame( - vViewPosition, normal,
		#if defined( USE_NORMALMAP )
			vNormalMapUv
		#elif defined( USE_CLEARCOAT_NORMALMAP )
			vClearcoatNormalMapUv
		#else
			vUv
		#endif
		);
	#endif
	#if defined( DOUBLE_SIDED ) && ! defined( FLAT_SHADED )
		tbn[0] *= faceDirection;
		tbn[1] *= faceDirection;
	#endif
#endif
#ifdef USE_CLEARCOAT_NORMALMAP
	#ifdef USE_TANGENT
		mat3 tbn2 = mat3( normalize( vTangent ), normalize( vBitangent ), normal );
	#else
		mat3 tbn2 = getTangentFrame( - vViewPosition, normal, vClearcoatNormalMapUv );
	#endif
	#if defined( DOUBLE_SIDED ) && ! defined( FLAT_SHADED )
		tbn2[0] *= faceDirection;
		tbn2[1] *= faceDirection;
	#endif
#endif
vec3 nonPerturbedNormal = normal;`,gp=`#ifdef USE_NORMALMAP_OBJECTSPACE
	normal = texture2D( normalMap, vNormalMapUv ).xyz * 2.0 - 1.0;
	#ifdef FLIP_SIDED
		normal = - normal;
	#endif
	#ifdef DOUBLE_SIDED
		normal = normal * faceDirection;
	#endif
	normal = normalize( normalMatrix * normal );
#elif defined( USE_NORMALMAP_TANGENTSPACE )
	vec3 mapN = texture2D( normalMap, vNormalMapUv ).xyz * 2.0 - 1.0;
	mapN.xy *= normalScale;
	normal = normalize( tbn * mapN );
#elif defined( USE_BUMPMAP )
	normal = perturbNormalArb( - vViewPosition, normal, dHdxy_fwd(), faceDirection );
#endif`,_p=`#ifndef FLAT_SHADED
	varying vec3 vNormal;
	#ifdef USE_TANGENT
		varying vec3 vTangent;
		varying vec3 vBitangent;
	#endif
#endif`,xp=`#ifndef FLAT_SHADED
	varying vec3 vNormal;
	#ifdef USE_TANGENT
		varying vec3 vTangent;
		varying vec3 vBitangent;
	#endif
#endif`,yp=`#ifndef FLAT_SHADED
	vNormal = normalize( transformedNormal );
	#ifdef USE_TANGENT
		vTangent = normalize( transformedTangent );
		vBitangent = normalize( cross( vNormal, vTangent ) * tangent.w );
	#endif
#endif`,vp=`#ifdef USE_NORMALMAP
	uniform sampler2D normalMap;
	uniform vec2 normalScale;
#endif
#ifdef USE_NORMALMAP_OBJECTSPACE
	uniform mat3 normalMatrix;
#endif
#if ! defined ( USE_TANGENT ) && ( defined ( USE_NORMALMAP_TANGENTSPACE ) || defined ( USE_CLEARCOAT_NORMALMAP ) || defined( USE_ANISOTROPY ) )
	mat3 getTangentFrame( vec3 eye_pos, vec3 surf_norm, vec2 uv ) {
		vec3 q0 = dFdx( eye_pos.xyz );
		vec3 q1 = dFdy( eye_pos.xyz );
		vec2 st0 = dFdx( uv.st );
		vec2 st1 = dFdy( uv.st );
		vec3 N = surf_norm;
		vec3 q1perp = cross( q1, N );
		vec3 q0perp = cross( N, q0 );
		vec3 T = q1perp * st0.x + q0perp * st1.x;
		vec3 B = q1perp * st0.y + q0perp * st1.y;
		float det = max( dot( T, T ), dot( B, B ) );
		float scale = ( det == 0.0 ) ? 0.0 : inversesqrt( det );
		return mat3( T * scale, B * scale, N );
	}
#endif`,Mp=`#ifdef USE_CLEARCOAT
	vec3 clearcoatNormal = nonPerturbedNormal;
#endif`,Sp=`#ifdef USE_CLEARCOAT_NORMALMAP
	vec3 clearcoatMapN = texture2D( clearcoatNormalMap, vClearcoatNormalMapUv ).xyz * 2.0 - 1.0;
	clearcoatMapN.xy *= clearcoatNormalScale;
	clearcoatNormal = normalize( tbn2 * clearcoatMapN );
#endif`,Ep=`#ifdef USE_CLEARCOATMAP
	uniform sampler2D clearcoatMap;
#endif
#ifdef USE_CLEARCOAT_NORMALMAP
	uniform sampler2D clearcoatNormalMap;
	uniform vec2 clearcoatNormalScale;
#endif
#ifdef USE_CLEARCOAT_ROUGHNESSMAP
	uniform sampler2D clearcoatRoughnessMap;
#endif`,bp=`#ifdef USE_IRIDESCENCEMAP
	uniform sampler2D iridescenceMap;
#endif
#ifdef USE_IRIDESCENCE_THICKNESSMAP
	uniform sampler2D iridescenceThicknessMap;
#endif`,wp=`#ifdef OPAQUE
diffuseColor.a = 1.0;
#endif
#ifdef USE_TRANSMISSION
diffuseColor.a *= material.transmissionAlpha;
#endif
gl_FragColor = vec4( outgoingLight, diffuseColor.a );`,Tp=`vec3 packNormalToRGB( const in vec3 normal ) {
	return normalize( normal ) * 0.5 + 0.5;
}
vec3 unpackRGBToNormal( const in vec3 rgb ) {
	return 2.0 * rgb.xyz - 1.0;
}
const float PackUpscale = 256. / 255.;const float UnpackDownscale = 255. / 256.;const float ShiftRight8 = 1. / 256.;
const float Inv255 = 1. / 255.;
const vec4 PackFactors = vec4( 1.0, 256.0, 256.0 * 256.0, 256.0 * 256.0 * 256.0 );
const vec2 UnpackFactors2 = vec2( UnpackDownscale, 1.0 / PackFactors.g );
const vec3 UnpackFactors3 = vec3( UnpackDownscale / PackFactors.rg, 1.0 / PackFactors.b );
const vec4 UnpackFactors4 = vec4( UnpackDownscale / PackFactors.rgb, 1.0 / PackFactors.a );
vec4 packDepthToRGBA( const in float v ) {
	if( v <= 0.0 )
		return vec4( 0., 0., 0., 0. );
	if( v >= 1.0 )
		return vec4( 1., 1., 1., 1. );
	float vuf;
	float af = modf( v * PackFactors.a, vuf );
	float bf = modf( vuf * ShiftRight8, vuf );
	float gf = modf( vuf * ShiftRight8, vuf );
	return vec4( vuf * Inv255, gf * PackUpscale, bf * PackUpscale, af );
}
vec3 packDepthToRGB( const in float v ) {
	if( v <= 0.0 )
		return vec3( 0., 0., 0. );
	if( v >= 1.0 )
		return vec3( 1., 1., 1. );
	float vuf;
	float bf = modf( v * PackFactors.b, vuf );
	float gf = modf( vuf * ShiftRight8, vuf );
	return vec3( vuf * Inv255, gf * PackUpscale, bf );
}
vec2 packDepthToRG( const in float v ) {
	if( v <= 0.0 )
		return vec2( 0., 0. );
	if( v >= 1.0 )
		return vec2( 1., 1. );
	float vuf;
	float gf = modf( v * 256., vuf );
	return vec2( vuf * Inv255, gf );
}
float unpackRGBAToDepth( const in vec4 v ) {
	return dot( v, UnpackFactors4 );
}
float unpackRGBToDepth( const in vec3 v ) {
	return dot( v, UnpackFactors3 );
}
float unpackRGToDepth( const in vec2 v ) {
	return v.r * UnpackFactors2.r + v.g * UnpackFactors2.g;
}
vec4 pack2HalfToRGBA( const in vec2 v ) {
	vec4 r = vec4( v.x, fract( v.x * 255.0 ), v.y, fract( v.y * 255.0 ) );
	return vec4( r.x - r.y / 255.0, r.y, r.z - r.w / 255.0, r.w );
}
vec2 unpackRGBATo2Half( const in vec4 v ) {
	return vec2( v.x + ( v.y / 255.0 ), v.z + ( v.w / 255.0 ) );
}
float viewZToOrthographicDepth( const in float viewZ, const in float near, const in float far ) {
	return ( viewZ + near ) / ( near - far );
}
float orthographicDepthToViewZ( const in float depth, const in float near, const in float far ) {
	return depth * ( near - far ) - near;
}
float viewZToPerspectiveDepth( const in float viewZ, const in float near, const in float far ) {
	return ( ( near + viewZ ) * far ) / ( ( far - near ) * viewZ );
}
float perspectiveDepthToViewZ( const in float depth, const in float near, const in float far ) {
	return ( near * far ) / ( ( far - near ) * depth - far );
}`,Ap=`#ifdef PREMULTIPLIED_ALPHA
	gl_FragColor.rgb *= gl_FragColor.a;
#endif`,Rp=`vec4 mvPosition = vec4( transformed, 1.0 );
#ifdef USE_BATCHING
	mvPosition = batchingMatrix * mvPosition;
#endif
#ifdef USE_INSTANCING
	mvPosition = instanceMatrix * mvPosition;
#endif
mvPosition = modelViewMatrix * mvPosition;
gl_Position = projectionMatrix * mvPosition;`,Cp=`#ifdef DITHERING
	gl_FragColor.rgb = dithering( gl_FragColor.rgb );
#endif`,Ip=`#ifdef DITHERING
	vec3 dithering( vec3 color ) {
		float grid_position = rand( gl_FragCoord.xy );
		vec3 dither_shift_RGB = vec3( 0.25 / 255.0, -0.25 / 255.0, 0.25 / 255.0 );
		dither_shift_RGB = mix( 2.0 * dither_shift_RGB, -2.0 * dither_shift_RGB, grid_position );
		return color + dither_shift_RGB;
	}
#endif`,Pp=`float roughnessFactor = roughness;
#ifdef USE_ROUGHNESSMAP
	vec4 texelRoughness = texture2D( roughnessMap, vRoughnessMapUv );
	roughnessFactor *= texelRoughness.g;
#endif`,Fp=`#ifdef USE_ROUGHNESSMAP
	uniform sampler2D roughnessMap;
#endif`,Dp=`#if NUM_SPOT_LIGHT_COORDS > 0
	varying vec4 vSpotLightCoord[ NUM_SPOT_LIGHT_COORDS ];
#endif
#if NUM_SPOT_LIGHT_MAPS > 0
	uniform sampler2D spotLightMap[ NUM_SPOT_LIGHT_MAPS ];
#endif
#ifdef USE_SHADOWMAP
	#if NUM_DIR_LIGHT_SHADOWS > 0
		uniform sampler2D directionalShadowMap[ NUM_DIR_LIGHT_SHADOWS ];
		varying vec4 vDirectionalShadowCoord[ NUM_DIR_LIGHT_SHADOWS ];
		struct DirectionalLightShadow {
			float shadowIntensity;
			float shadowBias;
			float shadowNormalBias;
			float shadowRadius;
			vec2 shadowMapSize;
		};
		uniform DirectionalLightShadow directionalLightShadows[ NUM_DIR_LIGHT_SHADOWS ];
	#endif
	#if NUM_SPOT_LIGHT_SHADOWS > 0
		uniform sampler2D spotShadowMap[ NUM_SPOT_LIGHT_SHADOWS ];
		struct SpotLightShadow {
			float shadowIntensity;
			float shadowBias;
			float shadowNormalBias;
			float shadowRadius;
			vec2 shadowMapSize;
		};
		uniform SpotLightShadow spotLightShadows[ NUM_SPOT_LIGHT_SHADOWS ];
	#endif
	#if NUM_POINT_LIGHT_SHADOWS > 0
		uniform sampler2D pointShadowMap[ NUM_POINT_LIGHT_SHADOWS ];
		varying vec4 vPointShadowCoord[ NUM_POINT_LIGHT_SHADOWS ];
		struct PointLightShadow {
			float shadowIntensity;
			float shadowBias;
			float shadowNormalBias;
			float shadowRadius;
			vec2 shadowMapSize;
			float shadowCameraNear;
			float shadowCameraFar;
		};
		uniform PointLightShadow pointLightShadows[ NUM_POINT_LIGHT_SHADOWS ];
	#endif
	float texture2DCompare( sampler2D depths, vec2 uv, float compare ) {
		return step( compare, unpackRGBAToDepth( texture2D( depths, uv ) ) );
	}
	vec2 texture2DDistribution( sampler2D shadow, vec2 uv ) {
		return unpackRGBATo2Half( texture2D( shadow, uv ) );
	}
	float VSMShadow (sampler2D shadow, vec2 uv, float compare ){
		float occlusion = 1.0;
		vec2 distribution = texture2DDistribution( shadow, uv );
		float hard_shadow = step( compare , distribution.x );
		if (hard_shadow != 1.0 ) {
			float distance = compare - distribution.x ;
			float variance = max( 0.00000, distribution.y * distribution.y );
			float softness_probability = variance / (variance + distance * distance );			softness_probability = clamp( ( softness_probability - 0.3 ) / ( 0.95 - 0.3 ), 0.0, 1.0 );			occlusion = clamp( max( hard_shadow, softness_probability ), 0.0, 1.0 );
		}
		return occlusion;
	}
	float getShadow( sampler2D shadowMap, vec2 shadowMapSize, float shadowIntensity, float shadowBias, float shadowRadius, vec4 shadowCoord ) {
		float shadow = 1.0;
		shadowCoord.xyz /= shadowCoord.w;
		shadowCoord.z += shadowBias;
		bool inFrustum = shadowCoord.x >= 0.0 && shadowCoord.x <= 1.0 && shadowCoord.y >= 0.0 && shadowCoord.y <= 1.0;
		bool frustumTest = inFrustum && shadowCoord.z <= 1.0;
		if ( frustumTest ) {
		#if defined( SHADOWMAP_TYPE_PCF )
			vec2 texelSize = vec2( 1.0 ) / shadowMapSize;
			float dx0 = - texelSize.x * shadowRadius;
			float dy0 = - texelSize.y * shadowRadius;
			float dx1 = + texelSize.x * shadowRadius;
			float dy1 = + texelSize.y * shadowRadius;
			float dx2 = dx0 / 2.0;
			float dy2 = dy0 / 2.0;
			float dx3 = dx1 / 2.0;
			float dy3 = dy1 / 2.0;
			shadow = (
				texture2DCompare( shadowMap, shadowCoord.xy + vec2( dx0, dy0 ), shadowCoord.z ) +
				texture2DCompare( shadowMap, shadowCoord.xy + vec2( 0.0, dy0 ), shadowCoord.z ) +
				texture2DCompare( shadowMap, shadowCoord.xy + vec2( dx1, dy0 ), shadowCoord.z ) +
				texture2DCompare( shadowMap, shadowCoord.xy + vec2( dx2, dy2 ), shadowCoord.z ) +
				texture2DCompare( shadowMap, shadowCoord.xy + vec2( 0.0, dy2 ), shadowCoord.z ) +
				texture2DCompare( shadowMap, shadowCoord.xy + vec2( dx3, dy2 ), shadowCoord.z ) +
				texture2DCompare( shadowMap, shadowCoord.xy + vec2( dx0, 0.0 ), shadowCoord.z ) +
				texture2DCompare( shadowMap, shadowCoord.xy + vec2( dx2, 0.0 ), shadowCoord.z ) +
				texture2DCompare( shadowMap, shadowCoord.xy, shadowCoord.z ) +
				texture2DCompare( shadowMap, shadowCoord.xy + vec2( dx3, 0.0 ), shadowCoord.z ) +
				texture2DCompare( shadowMap, shadowCoord.xy + vec2( dx1, 0.0 ), shadowCoord.z ) +
				texture2DCompare( shadowMap, shadowCoord.xy + vec2( dx2, dy3 ), shadowCoord.z ) +
				texture2DCompare( shadowMap, shadowCoord.xy + vec2( 0.0, dy3 ), shadowCoord.z ) +
				texture2DCompare( shadowMap, shadowCoord.xy + vec2( dx3, dy3 ), shadowCoord.z ) +
				texture2DCompare( shadowMap, shadowCoord.xy + vec2( dx0, dy1 ), shadowCoord.z ) +
				texture2DCompare( shadowMap, shadowCoord.xy + vec2( 0.0, dy1 ), shadowCoord.z ) +
				texture2DCompare( shadowMap, shadowCoord.xy + vec2( dx1, dy1 ), shadowCoord.z )
			) * ( 1.0 / 17.0 );
		#elif defined( SHADOWMAP_TYPE_PCF_SOFT )
			vec2 texelSize = vec2( 1.0 ) / shadowMapSize;
			float dx = texelSize.x;
			float dy = texelSize.y;
			vec2 uv = shadowCoord.xy;
			vec2 f = fract( uv * shadowMapSize + 0.5 );
			uv -= f * texelSize;
			shadow = (
				texture2DCompare( shadowMap, uv, shadowCoord.z ) +
				texture2DCompare( shadowMap, uv + vec2( dx, 0.0 ), shadowCoord.z ) +
				texture2DCompare( shadowMap, uv + vec2( 0.0, dy ), shadowCoord.z ) +
				texture2DCompare( shadowMap, uv + texelSize, shadowCoord.z ) +
				mix( texture2DCompare( shadowMap, uv + vec2( -dx, 0.0 ), shadowCoord.z ),
					 texture2DCompare( shadowMap, uv + vec2( 2.0 * dx, 0.0 ), shadowCoord.z ),
					 f.x ) +
				mix( texture2DCompare( shadowMap, uv + vec2( -dx, dy ), shadowCoord.z ),
					 texture2DCompare( shadowMap, uv + vec2( 2.0 * dx, dy ), shadowCoord.z ),
					 f.x ) +
				mix( texture2DCompare( shadowMap, uv + vec2( 0.0, -dy ), shadowCoord.z ),
					 texture2DCompare( shadowMap, uv + vec2( 0.0, 2.0 * dy ), shadowCoord.z ),
					 f.y ) +
				mix( texture2DCompare( shadowMap, uv + vec2( dx, -dy ), shadowCoord.z ),
					 texture2DCompare( shadowMap, uv + vec2( dx, 2.0 * dy ), shadowCoord.z ),
					 f.y ) +
				mix( mix( texture2DCompare( shadowMap, uv + vec2( -dx, -dy ), shadowCoord.z ),
						  texture2DCompare( shadowMap, uv + vec2( 2.0 * dx, -dy ), shadowCoord.z ),
						  f.x ),
					 mix( texture2DCompare( shadowMap, uv + vec2( -dx, 2.0 * dy ), shadowCoord.z ),
						  texture2DCompare( shadowMap, uv + vec2( 2.0 * dx, 2.0 * dy ), shadowCoord.z ),
						  f.x ),
					 f.y )
			) * ( 1.0 / 9.0 );
		#elif defined( SHADOWMAP_TYPE_VSM )
			shadow = VSMShadow( shadowMap, shadowCoord.xy, shadowCoord.z );
		#else
			shadow = texture2DCompare( shadowMap, shadowCoord.xy, shadowCoord.z );
		#endif
		}
		return mix( 1.0, shadow, shadowIntensity );
	}
	vec2 cubeToUV( vec3 v, float texelSizeY ) {
		vec3 absV = abs( v );
		float scaleToCube = 1.0 / max( absV.x, max( absV.y, absV.z ) );
		absV *= scaleToCube;
		v *= scaleToCube * ( 1.0 - 2.0 * texelSizeY );
		vec2 planar = v.xy;
		float almostATexel = 1.5 * texelSizeY;
		float almostOne = 1.0 - almostATexel;
		if ( absV.z >= almostOne ) {
			if ( v.z > 0.0 )
				planar.x = 4.0 - v.x;
		} else if ( absV.x >= almostOne ) {
			float signX = sign( v.x );
			planar.x = v.z * signX + 2.0 * signX;
		} else if ( absV.y >= almostOne ) {
			float signY = sign( v.y );
			planar.x = v.x + 2.0 * signY + 2.0;
			planar.y = v.z * signY - 2.0;
		}
		return vec2( 0.125, 0.25 ) * planar + vec2( 0.375, 0.75 );
	}
	float getPointShadow( sampler2D shadowMap, vec2 shadowMapSize, float shadowIntensity, float shadowBias, float shadowRadius, vec4 shadowCoord, float shadowCameraNear, float shadowCameraFar ) {
		float shadow = 1.0;
		vec3 lightToPosition = shadowCoord.xyz;
		
		float lightToPositionLength = length( lightToPosition );
		if ( lightToPositionLength - shadowCameraFar <= 0.0 && lightToPositionLength - shadowCameraNear >= 0.0 ) {
			float dp = ( lightToPositionLength - shadowCameraNear ) / ( shadowCameraFar - shadowCameraNear );			dp += shadowBias;
			vec3 bd3D = normalize( lightToPosition );
			vec2 texelSize = vec2( 1.0 ) / ( shadowMapSize * vec2( 4.0, 2.0 ) );
			#if defined( SHADOWMAP_TYPE_PCF ) || defined( SHADOWMAP_TYPE_PCF_SOFT ) || defined( SHADOWMAP_TYPE_VSM )
				vec2 offset = vec2( - 1, 1 ) * shadowRadius * texelSize.y;
				shadow = (
					texture2DCompare( shadowMap, cubeToUV( bd3D + offset.xyy, texelSize.y ), dp ) +
					texture2DCompare( shadowMap, cubeToUV( bd3D + offset.yyy, texelSize.y ), dp ) +
					texture2DCompare( shadowMap, cubeToUV( bd3D + offset.xyx, texelSize.y ), dp ) +
					texture2DCompare( shadowMap, cubeToUV( bd3D + offset.yyx, texelSize.y ), dp ) +
					texture2DCompare( shadowMap, cubeToUV( bd3D, texelSize.y ), dp ) +
					texture2DCompare( shadowMap, cubeToUV( bd3D + offset.xxy, texelSize.y ), dp ) +
					texture2DCompare( shadowMap, cubeToUV( bd3D + offset.yxy, texelSize.y ), dp ) +
					texture2DCompare( shadowMap, cubeToUV( bd3D + offset.xxx, texelSize.y ), dp ) +
					texture2DCompare( shadowMap, cubeToUV( bd3D + offset.yxx, texelSize.y ), dp )
				) * ( 1.0 / 9.0 );
			#else
				shadow = texture2DCompare( shadowMap, cubeToUV( bd3D, texelSize.y ), dp );
			#endif
		}
		return mix( 1.0, shadow, shadowIntensity );
	}
#endif`,Lp=`#if NUM_SPOT_LIGHT_COORDS > 0
	uniform mat4 spotLightMatrix[ NUM_SPOT_LIGHT_COORDS ];
	varying vec4 vSpotLightCoord[ NUM_SPOT_LIGHT_COORDS ];
#endif
#ifdef USE_SHADOWMAP
	#if NUM_DIR_LIGHT_SHADOWS > 0
		uniform mat4 directionalShadowMatrix[ NUM_DIR_LIGHT_SHADOWS ];
		varying vec4 vDirectionalShadowCoord[ NUM_DIR_LIGHT_SHADOWS ];
		struct DirectionalLightShadow {
			float shadowIntensity;
			float shadowBias;
			float shadowNormalBias;
			float shadowRadius;
			vec2 shadowMapSize;
		};
		uniform DirectionalLightShadow directionalLightShadows[ NUM_DIR_LIGHT_SHADOWS ];
	#endif
	#if NUM_SPOT_LIGHT_SHADOWS > 0
		struct SpotLightShadow {
			float shadowIntensity;
			float shadowBias;
			float shadowNormalBias;
			float shadowRadius;
			vec2 shadowMapSize;
		};
		uniform SpotLightShadow spotLightShadows[ NUM_SPOT_LIGHT_SHADOWS ];
	#endif
	#if NUM_POINT_LIGHT_SHADOWS > 0
		uniform mat4 pointShadowMatrix[ NUM_POINT_LIGHT_SHADOWS ];
		varying vec4 vPointShadowCoord[ NUM_POINT_LIGHT_SHADOWS ];
		struct PointLightShadow {
			float shadowIntensity;
			float shadowBias;
			float shadowNormalBias;
			float shadowRadius;
			vec2 shadowMapSize;
			float shadowCameraNear;
			float shadowCameraFar;
		};
		uniform PointLightShadow pointLightShadows[ NUM_POINT_LIGHT_SHADOWS ];
	#endif
#endif`,Up=`#if ( defined( USE_SHADOWMAP ) && ( NUM_DIR_LIGHT_SHADOWS > 0 || NUM_POINT_LIGHT_SHADOWS > 0 ) ) || ( NUM_SPOT_LIGHT_COORDS > 0 )
	vec3 shadowWorldNormal = inverseTransformDirection( transformedNormal, viewMatrix );
	vec4 shadowWorldPosition;
#endif
#if defined( USE_SHADOWMAP )
	#if NUM_DIR_LIGHT_SHADOWS > 0
		#pragma unroll_loop_start
		for ( int i = 0; i < NUM_DIR_LIGHT_SHADOWS; i ++ ) {
			shadowWorldPosition = worldPosition + vec4( shadowWorldNormal * directionalLightShadows[ i ].shadowNormalBias, 0 );
			vDirectionalShadowCoord[ i ] = directionalShadowMatrix[ i ] * shadowWorldPosition;
		}
		#pragma unroll_loop_end
	#endif
	#if NUM_POINT_LIGHT_SHADOWS > 0
		#pragma unroll_loop_start
		for ( int i = 0; i < NUM_POINT_LIGHT_SHADOWS; i ++ ) {
			shadowWorldPosition = worldPosition + vec4( shadowWorldNormal * pointLightShadows[ i ].shadowNormalBias, 0 );
			vPointShadowCoord[ i ] = pointShadowMatrix[ i ] * shadowWorldPosition;
		}
		#pragma unroll_loop_end
	#endif
#endif
#if NUM_SPOT_LIGHT_COORDS > 0
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_SPOT_LIGHT_COORDS; i ++ ) {
		shadowWorldPosition = worldPosition;
		#if ( defined( USE_SHADOWMAP ) && UNROLLED_LOOP_INDEX < NUM_SPOT_LIGHT_SHADOWS )
			shadowWorldPosition.xyz += shadowWorldNormal * spotLightShadows[ i ].shadowNormalBias;
		#endif
		vSpotLightCoord[ i ] = spotLightMatrix[ i ] * shadowWorldPosition;
	}
	#pragma unroll_loop_end
#endif`,Np=`float getShadowMask() {
	float shadow = 1.0;
	#ifdef USE_SHADOWMAP
	#if NUM_DIR_LIGHT_SHADOWS > 0
	DirectionalLightShadow directionalLight;
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_DIR_LIGHT_SHADOWS; i ++ ) {
		directionalLight = directionalLightShadows[ i ];
		shadow *= receiveShadow ? getShadow( directionalShadowMap[ i ], directionalLight.shadowMapSize, directionalLight.shadowIntensity, directionalLight.shadowBias, directionalLight.shadowRadius, vDirectionalShadowCoord[ i ] ) : 1.0;
	}
	#pragma unroll_loop_end
	#endif
	#if NUM_SPOT_LIGHT_SHADOWS > 0
	SpotLightShadow spotLight;
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_SPOT_LIGHT_SHADOWS; i ++ ) {
		spotLight = spotLightShadows[ i ];
		shadow *= receiveShadow ? getShadow( spotShadowMap[ i ], spotLight.shadowMapSize, spotLight.shadowIntensity, spotLight.shadowBias, spotLight.shadowRadius, vSpotLightCoord[ i ] ) : 1.0;
	}
	#pragma unroll_loop_end
	#endif
	#if NUM_POINT_LIGHT_SHADOWS > 0
	PointLightShadow pointLight;
	#pragma unroll_loop_start
	for ( int i = 0; i < NUM_POINT_LIGHT_SHADOWS; i ++ ) {
		pointLight = pointLightShadows[ i ];
		shadow *= receiveShadow ? getPointShadow( pointShadowMap[ i ], pointLight.shadowMapSize, pointLight.shadowIntensity, pointLight.shadowBias, pointLight.shadowRadius, vPointShadowCoord[ i ], pointLight.shadowCameraNear, pointLight.shadowCameraFar ) : 1.0;
	}
	#pragma unroll_loop_end
	#endif
	#endif
	return shadow;
}`,Op=`#ifdef USE_SKINNING
	mat4 boneMatX = getBoneMatrix( skinIndex.x );
	mat4 boneMatY = getBoneMatrix( skinIndex.y );
	mat4 boneMatZ = getBoneMatrix( skinIndex.z );
	mat4 boneMatW = getBoneMatrix( skinIndex.w );
#endif`,Bp=`#ifdef USE_SKINNING
	uniform mat4 bindMatrix;
	uniform mat4 bindMatrixInverse;
	uniform highp sampler2D boneTexture;
	mat4 getBoneMatrix( const in float i ) {
		int size = textureSize( boneTexture, 0 ).x;
		int j = int( i ) * 4;
		int x = j % size;
		int y = j / size;
		vec4 v1 = texelFetch( boneTexture, ivec2( x, y ), 0 );
		vec4 v2 = texelFetch( boneTexture, ivec2( x + 1, y ), 0 );
		vec4 v3 = texelFetch( boneTexture, ivec2( x + 2, y ), 0 );
		vec4 v4 = texelFetch( boneTexture, ivec2( x + 3, y ), 0 );
		return mat4( v1, v2, v3, v4 );
	}
#endif`,zp=`#ifdef USE_SKINNING
	vec4 skinVertex = bindMatrix * vec4( transformed, 1.0 );
	vec4 skinned = vec4( 0.0 );
	skinned += boneMatX * skinVertex * skinWeight.x;
	skinned += boneMatY * skinVertex * skinWeight.y;
	skinned += boneMatZ * skinVertex * skinWeight.z;
	skinned += boneMatW * skinVertex * skinWeight.w;
	transformed = ( bindMatrixInverse * skinned ).xyz;
#endif`,kp=`#ifdef USE_SKINNING
	mat4 skinMatrix = mat4( 0.0 );
	skinMatrix += skinWeight.x * boneMatX;
	skinMatrix += skinWeight.y * boneMatY;
	skinMatrix += skinWeight.z * boneMatZ;
	skinMatrix += skinWeight.w * boneMatW;
	skinMatrix = bindMatrixInverse * skinMatrix * bindMatrix;
	objectNormal = vec4( skinMatrix * vec4( objectNormal, 0.0 ) ).xyz;
	#ifdef USE_TANGENT
		objectTangent = vec4( skinMatrix * vec4( objectTangent, 0.0 ) ).xyz;
	#endif
#endif`,Hp=`float specularStrength;
#ifdef USE_SPECULARMAP
	vec4 texelSpecular = texture2D( specularMap, vSpecularMapUv );
	specularStrength = texelSpecular.r;
#else
	specularStrength = 1.0;
#endif`,Vp=`#ifdef USE_SPECULARMAP
	uniform sampler2D specularMap;
#endif`,Gp=`#if defined( TONE_MAPPING )
	gl_FragColor.rgb = toneMapping( gl_FragColor.rgb );
#endif`,Wp=`#ifndef saturate
#define saturate( a ) clamp( a, 0.0, 1.0 )
#endif
uniform float toneMappingExposure;
vec3 LinearToneMapping( vec3 color ) {
	return saturate( toneMappingExposure * color );
}
vec3 ReinhardToneMapping( vec3 color ) {
	color *= toneMappingExposure;
	return saturate( color / ( vec3( 1.0 ) + color ) );
}
vec3 CineonToneMapping( vec3 color ) {
	color *= toneMappingExposure;
	color = max( vec3( 0.0 ), color - 0.004 );
	return pow( ( color * ( 6.2 * color + 0.5 ) ) / ( color * ( 6.2 * color + 1.7 ) + 0.06 ), vec3( 2.2 ) );
}
vec3 RRTAndODTFit( vec3 v ) {
	vec3 a = v * ( v + 0.0245786 ) - 0.000090537;
	vec3 b = v * ( 0.983729 * v + 0.4329510 ) + 0.238081;
	return a / b;
}
vec3 ACESFilmicToneMapping( vec3 color ) {
	const mat3 ACESInputMat = mat3(
		vec3( 0.59719, 0.07600, 0.02840 ),		vec3( 0.35458, 0.90834, 0.13383 ),
		vec3( 0.04823, 0.01566, 0.83777 )
	);
	const mat3 ACESOutputMat = mat3(
		vec3(  1.60475, -0.10208, -0.00327 ),		vec3( -0.53108,  1.10813, -0.07276 ),
		vec3( -0.07367, -0.00605,  1.07602 )
	);
	color *= toneMappingExposure / 0.6;
	color = ACESInputMat * color;
	color = RRTAndODTFit( color );
	color = ACESOutputMat * color;
	return saturate( color );
}
const mat3 LINEAR_REC2020_TO_LINEAR_SRGB = mat3(
	vec3( 1.6605, - 0.1246, - 0.0182 ),
	vec3( - 0.5876, 1.1329, - 0.1006 ),
	vec3( - 0.0728, - 0.0083, 1.1187 )
);
const mat3 LINEAR_SRGB_TO_LINEAR_REC2020 = mat3(
	vec3( 0.6274, 0.0691, 0.0164 ),
	vec3( 0.3293, 0.9195, 0.0880 ),
	vec3( 0.0433, 0.0113, 0.8956 )
);
vec3 agxDefaultContrastApprox( vec3 x ) {
	vec3 x2 = x * x;
	vec3 x4 = x2 * x2;
	return + 15.5 * x4 * x2
		- 40.14 * x4 * x
		+ 31.96 * x4
		- 6.868 * x2 * x
		+ 0.4298 * x2
		+ 0.1191 * x
		- 0.00232;
}
vec3 AgXToneMapping( vec3 color ) {
	const mat3 AgXInsetMatrix = mat3(
		vec3( 0.856627153315983, 0.137318972929847, 0.11189821299995 ),
		vec3( 0.0951212405381588, 0.761241990602591, 0.0767994186031903 ),
		vec3( 0.0482516061458583, 0.101439036467562, 0.811302368396859 )
	);
	const mat3 AgXOutsetMatrix = mat3(
		vec3( 1.1271005818144368, - 0.1413297634984383, - 0.14132976349843826 ),
		vec3( - 0.11060664309660323, 1.157823702216272, - 0.11060664309660294 ),
		vec3( - 0.016493938717834573, - 0.016493938717834257, 1.2519364065950405 )
	);
	const float AgxMinEv = - 12.47393;	const float AgxMaxEv = 4.026069;
	color *= toneMappingExposure;
	color = LINEAR_SRGB_TO_LINEAR_REC2020 * color;
	color = AgXInsetMatrix * color;
	color = max( color, 1e-10 );	color = log2( color );
	color = ( color - AgxMinEv ) / ( AgxMaxEv - AgxMinEv );
	color = clamp( color, 0.0, 1.0 );
	color = agxDefaultContrastApprox( color );
	color = AgXOutsetMatrix * color;
	color = pow( max( vec3( 0.0 ), color ), vec3( 2.2 ) );
	color = LINEAR_REC2020_TO_LINEAR_SRGB * color;
	color = clamp( color, 0.0, 1.0 );
	return color;
}
vec3 NeutralToneMapping( vec3 color ) {
	const float StartCompression = 0.8 - 0.04;
	const float Desaturation = 0.15;
	color *= toneMappingExposure;
	float x = min( color.r, min( color.g, color.b ) );
	float offset = x < 0.08 ? x - 6.25 * x * x : 0.04;
	color -= offset;
	float peak = max( color.r, max( color.g, color.b ) );
	if ( peak < StartCompression ) return color;
	float d = 1. - StartCompression;
	float newPeak = 1. - d * d / ( peak + d - StartCompression );
	color *= newPeak / peak;
	float g = 1. - 1. / ( Desaturation * ( peak - newPeak ) + 1. );
	return mix( color, vec3( newPeak ), g );
}
vec3 CustomToneMapping( vec3 color ) { return color; }`,Kp=`#ifdef USE_TRANSMISSION
	material.transmission = transmission;
	material.transmissionAlpha = 1.0;
	material.thickness = thickness;
	material.attenuationDistance = attenuationDistance;
	material.attenuationColor = attenuationColor;
	#ifdef USE_TRANSMISSIONMAP
		material.transmission *= texture2D( transmissionMap, vTransmissionMapUv ).r;
	#endif
	#ifdef USE_THICKNESSMAP
		material.thickness *= texture2D( thicknessMap, vThicknessMapUv ).g;
	#endif
	vec3 pos = vWorldPosition;
	vec3 v = normalize( cameraPosition - pos );
	vec3 n = inverseTransformDirection( normal, viewMatrix );
	vec4 transmitted = getIBLVolumeRefraction(
		n, v, material.roughness, material.diffuseColor, material.specularColor, material.specularF90,
		pos, modelMatrix, viewMatrix, projectionMatrix, material.dispersion, material.ior, material.thickness,
		material.attenuationColor, material.attenuationDistance );
	material.transmissionAlpha = mix( material.transmissionAlpha, transmitted.a, material.transmission );
	totalDiffuse = mix( totalDiffuse, transmitted.rgb, material.transmission );
#endif`,Xp=`#ifdef USE_TRANSMISSION
	uniform float transmission;
	uniform float thickness;
	uniform float attenuationDistance;
	uniform vec3 attenuationColor;
	#ifdef USE_TRANSMISSIONMAP
		uniform sampler2D transmissionMap;
	#endif
	#ifdef USE_THICKNESSMAP
		uniform sampler2D thicknessMap;
	#endif
	uniform vec2 transmissionSamplerSize;
	uniform sampler2D transmissionSamplerMap;
	uniform mat4 modelMatrix;
	uniform mat4 projectionMatrix;
	varying vec3 vWorldPosition;
	float w0( float a ) {
		return ( 1.0 / 6.0 ) * ( a * ( a * ( - a + 3.0 ) - 3.0 ) + 1.0 );
	}
	float w1( float a ) {
		return ( 1.0 / 6.0 ) * ( a *  a * ( 3.0 * a - 6.0 ) + 4.0 );
	}
	float w2( float a ){
		return ( 1.0 / 6.0 ) * ( a * ( a * ( - 3.0 * a + 3.0 ) + 3.0 ) + 1.0 );
	}
	float w3( float a ) {
		return ( 1.0 / 6.0 ) * ( a * a * a );
	}
	float g0( float a ) {
		return w0( a ) + w1( a );
	}
	float g1( float a ) {
		return w2( a ) + w3( a );
	}
	float h0( float a ) {
		return - 1.0 + w1( a ) / ( w0( a ) + w1( a ) );
	}
	float h1( float a ) {
		return 1.0 + w3( a ) / ( w2( a ) + w3( a ) );
	}
	vec4 bicubic( sampler2D tex, vec2 uv, vec4 texelSize, float lod ) {
		uv = uv * texelSize.zw + 0.5;
		vec2 iuv = floor( uv );
		vec2 fuv = fract( uv );
		float g0x = g0( fuv.x );
		float g1x = g1( fuv.x );
		float h0x = h0( fuv.x );
		float h1x = h1( fuv.x );
		float h0y = h0( fuv.y );
		float h1y = h1( fuv.y );
		vec2 p0 = ( vec2( iuv.x + h0x, iuv.y + h0y ) - 0.5 ) * texelSize.xy;
		vec2 p1 = ( vec2( iuv.x + h1x, iuv.y + h0y ) - 0.5 ) * texelSize.xy;
		vec2 p2 = ( vec2( iuv.x + h0x, iuv.y + h1y ) - 0.5 ) * texelSize.xy;
		vec2 p3 = ( vec2( iuv.x + h1x, iuv.y + h1y ) - 0.5 ) * texelSize.xy;
		return g0( fuv.y ) * ( g0x * textureLod( tex, p0, lod ) + g1x * textureLod( tex, p1, lod ) ) +
			g1( fuv.y ) * ( g0x * textureLod( tex, p2, lod ) + g1x * textureLod( tex, p3, lod ) );
	}
	vec4 textureBicubic( sampler2D sampler, vec2 uv, float lod ) {
		vec2 fLodSize = vec2( textureSize( sampler, int( lod ) ) );
		vec2 cLodSize = vec2( textureSize( sampler, int( lod + 1.0 ) ) );
		vec2 fLodSizeInv = 1.0 / fLodSize;
		vec2 cLodSizeInv = 1.0 / cLodSize;
		vec4 fSample = bicubic( sampler, uv, vec4( fLodSizeInv, fLodSize ), floor( lod ) );
		vec4 cSample = bicubic( sampler, uv, vec4( cLodSizeInv, cLodSize ), ceil( lod ) );
		return mix( fSample, cSample, fract( lod ) );
	}
	vec3 getVolumeTransmissionRay( const in vec3 n, const in vec3 v, const in float thickness, const in float ior, const in mat4 modelMatrix ) {
		vec3 refractionVector = refract( - v, normalize( n ), 1.0 / ior );
		vec3 modelScale;
		modelScale.x = length( vec3( modelMatrix[ 0 ].xyz ) );
		modelScale.y = length( vec3( modelMatrix[ 1 ].xyz ) );
		modelScale.z = length( vec3( modelMatrix[ 2 ].xyz ) );
		return normalize( refractionVector ) * thickness * modelScale;
	}
	float applyIorToRoughness( const in float roughness, const in float ior ) {
		return roughness * clamp( ior * 2.0 - 2.0, 0.0, 1.0 );
	}
	vec4 getTransmissionSample( const in vec2 fragCoord, const in float roughness, const in float ior ) {
		float lod = log2( transmissionSamplerSize.x ) * applyIorToRoughness( roughness, ior );
		return textureBicubic( transmissionSamplerMap, fragCoord.xy, lod );
	}
	vec3 volumeAttenuation( const in float transmissionDistance, const in vec3 attenuationColor, const in float attenuationDistance ) {
		if ( isinf( attenuationDistance ) ) {
			return vec3( 1.0 );
		} else {
			vec3 attenuationCoefficient = -log( attenuationColor ) / attenuationDistance;
			vec3 transmittance = exp( - attenuationCoefficient * transmissionDistance );			return transmittance;
		}
	}
	vec4 getIBLVolumeRefraction( const in vec3 n, const in vec3 v, const in float roughness, const in vec3 diffuseColor,
		const in vec3 specularColor, const in float specularF90, const in vec3 position, const in mat4 modelMatrix,
		const in mat4 viewMatrix, const in mat4 projMatrix, const in float dispersion, const in float ior, const in float thickness,
		const in vec3 attenuationColor, const in float attenuationDistance ) {
		vec4 transmittedLight;
		vec3 transmittance;
		#ifdef USE_DISPERSION
			float halfSpread = ( ior - 1.0 ) * 0.025 * dispersion;
			vec3 iors = vec3( ior - halfSpread, ior, ior + halfSpread );
			for ( int i = 0; i < 3; i ++ ) {
				vec3 transmissionRay = getVolumeTransmissionRay( n, v, thickness, iors[ i ], modelMatrix );
				vec3 refractedRayExit = position + transmissionRay;
		
				vec4 ndcPos = projMatrix * viewMatrix * vec4( refractedRayExit, 1.0 );
				vec2 refractionCoords = ndcPos.xy / ndcPos.w;
				refractionCoords += 1.0;
				refractionCoords /= 2.0;
		
				vec4 transmissionSample = getTransmissionSample( refractionCoords, roughness, iors[ i ] );
				transmittedLight[ i ] = transmissionSample[ i ];
				transmittedLight.a += transmissionSample.a;
				transmittance[ i ] = diffuseColor[ i ] * volumeAttenuation( length( transmissionRay ), attenuationColor, attenuationDistance )[ i ];
			}
			transmittedLight.a /= 3.0;
		
		#else
		
			vec3 transmissionRay = getVolumeTransmissionRay( n, v, thickness, ior, modelMatrix );
			vec3 refractedRayExit = position + transmissionRay;
			vec4 ndcPos = projMatrix * viewMatrix * vec4( refractedRayExit, 1.0 );
			vec2 refractionCoords = ndcPos.xy / ndcPos.w;
			refractionCoords += 1.0;
			refractionCoords /= 2.0;
			transmittedLight = getTransmissionSample( refractionCoords, roughness, ior );
			transmittance = diffuseColor * volumeAttenuation( length( transmissionRay ), attenuationColor, attenuationDistance );
		
		#endif
		vec3 attenuatedColor = transmittance * transmittedLight.rgb;
		vec3 F = EnvironmentBRDF( n, v, specularColor, specularF90, roughness );
		float transmittanceFactor = ( transmittance.r + transmittance.g + transmittance.b ) / 3.0;
		return vec4( ( 1.0 - F ) * attenuatedColor, 1.0 - ( 1.0 - transmittedLight.a ) * transmittanceFactor );
	}
#endif`,qp=`#if defined( USE_UV ) || defined( USE_ANISOTROPY )
	varying vec2 vUv;
#endif
#ifdef USE_MAP
	varying vec2 vMapUv;
#endif
#ifdef USE_ALPHAMAP
	varying vec2 vAlphaMapUv;
#endif
#ifdef USE_LIGHTMAP
	varying vec2 vLightMapUv;
#endif
#ifdef USE_AOMAP
	varying vec2 vAoMapUv;
#endif
#ifdef USE_BUMPMAP
	varying vec2 vBumpMapUv;
#endif
#ifdef USE_NORMALMAP
	varying vec2 vNormalMapUv;
#endif
#ifdef USE_EMISSIVEMAP
	varying vec2 vEmissiveMapUv;
#endif
#ifdef USE_METALNESSMAP
	varying vec2 vMetalnessMapUv;
#endif
#ifdef USE_ROUGHNESSMAP
	varying vec2 vRoughnessMapUv;
#endif
#ifdef USE_ANISOTROPYMAP
	varying vec2 vAnisotropyMapUv;
#endif
#ifdef USE_CLEARCOATMAP
	varying vec2 vClearcoatMapUv;
#endif
#ifdef USE_CLEARCOAT_NORMALMAP
	varying vec2 vClearcoatNormalMapUv;
#endif
#ifdef USE_CLEARCOAT_ROUGHNESSMAP
	varying vec2 vClearcoatRoughnessMapUv;
#endif
#ifdef USE_IRIDESCENCEMAP
	varying vec2 vIridescenceMapUv;
#endif
#ifdef USE_IRIDESCENCE_THICKNESSMAP
	varying vec2 vIridescenceThicknessMapUv;
#endif
#ifdef USE_SHEEN_COLORMAP
	varying vec2 vSheenColorMapUv;
#endif
#ifdef USE_SHEEN_ROUGHNESSMAP
	varying vec2 vSheenRoughnessMapUv;
#endif
#ifdef USE_SPECULARMAP
	varying vec2 vSpecularMapUv;
#endif
#ifdef USE_SPECULAR_COLORMAP
	varying vec2 vSpecularColorMapUv;
#endif
#ifdef USE_SPECULAR_INTENSITYMAP
	varying vec2 vSpecularIntensityMapUv;
#endif
#ifdef USE_TRANSMISSIONMAP
	uniform mat3 transmissionMapTransform;
	varying vec2 vTransmissionMapUv;
#endif
#ifdef USE_THICKNESSMAP
	uniform mat3 thicknessMapTransform;
	varying vec2 vThicknessMapUv;
#endif`,Yp=`#if defined( USE_UV ) || defined( USE_ANISOTROPY )
	varying vec2 vUv;
#endif
#ifdef USE_MAP
	uniform mat3 mapTransform;
	varying vec2 vMapUv;
#endif
#ifdef USE_ALPHAMAP
	uniform mat3 alphaMapTransform;
	varying vec2 vAlphaMapUv;
#endif
#ifdef USE_LIGHTMAP
	uniform mat3 lightMapTransform;
	varying vec2 vLightMapUv;
#endif
#ifdef USE_AOMAP
	uniform mat3 aoMapTransform;
	varying vec2 vAoMapUv;
#endif
#ifdef USE_BUMPMAP
	uniform mat3 bumpMapTransform;
	varying vec2 vBumpMapUv;
#endif
#ifdef USE_NORMALMAP
	uniform mat3 normalMapTransform;
	varying vec2 vNormalMapUv;
#endif
#ifdef USE_DISPLACEMENTMAP
	uniform mat3 displacementMapTransform;
	varying vec2 vDisplacementMapUv;
#endif
#ifdef USE_EMISSIVEMAP
	uniform mat3 emissiveMapTransform;
	varying vec2 vEmissiveMapUv;
#endif
#ifdef USE_METALNESSMAP
	uniform mat3 metalnessMapTransform;
	varying vec2 vMetalnessMapUv;
#endif
#ifdef USE_ROUGHNESSMAP
	uniform mat3 roughnessMapTransform;
	varying vec2 vRoughnessMapUv;
#endif
#ifdef USE_ANISOTROPYMAP
	uniform mat3 anisotropyMapTransform;
	varying vec2 vAnisotropyMapUv;
#endif
#ifdef USE_CLEARCOATMAP
	uniform mat3 clearcoatMapTransform;
	varying vec2 vClearcoatMapUv;
#endif
#ifdef USE_CLEARCOAT_NORMALMAP
	uniform mat3 clearcoatNormalMapTransform;
	varying vec2 vClearcoatNormalMapUv;
#endif
#ifdef USE_CLEARCOAT_ROUGHNESSMAP
	uniform mat3 clearcoatRoughnessMapTransform;
	varying vec2 vClearcoatRoughnessMapUv;
#endif
#ifdef USE_SHEEN_COLORMAP
	uniform mat3 sheenColorMapTransform;
	varying vec2 vSheenColorMapUv;
#endif
#ifdef USE_SHEEN_ROUGHNESSMAP
	uniform mat3 sheenRoughnessMapTransform;
	varying vec2 vSheenRoughnessMapUv;
#endif
#ifdef USE_IRIDESCENCEMAP
	uniform mat3 iridescenceMapTransform;
	varying vec2 vIridescenceMapUv;
#endif
#ifdef USE_IRIDESCENCE_THICKNESSMAP
	uniform mat3 iridescenceThicknessMapTransform;
	varying vec2 vIridescenceThicknessMapUv;
#endif
#ifdef USE_SPECULARMAP
	uniform mat3 specularMapTransform;
	varying vec2 vSpecularMapUv;
#endif
#ifdef USE_SPECULAR_COLORMAP
	uniform mat3 specularColorMapTransform;
	varying vec2 vSpecularColorMapUv;
#endif
#ifdef USE_SPECULAR_INTENSITYMAP
	uniform mat3 specularIntensityMapTransform;
	varying vec2 vSpecularIntensityMapUv;
#endif
#ifdef USE_TRANSMISSIONMAP
	uniform mat3 transmissionMapTransform;
	varying vec2 vTransmissionMapUv;
#endif
#ifdef USE_THICKNESSMAP
	uniform mat3 thicknessMapTransform;
	varying vec2 vThicknessMapUv;
#endif`,Zp=`#if defined( USE_UV ) || defined( USE_ANISOTROPY )
	vUv = vec3( uv, 1 ).xy;
#endif
#ifdef USE_MAP
	vMapUv = ( mapTransform * vec3( MAP_UV, 1 ) ).xy;
#endif
#ifdef USE_ALPHAMAP
	vAlphaMapUv = ( alphaMapTransform * vec3( ALPHAMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_LIGHTMAP
	vLightMapUv = ( lightMapTransform * vec3( LIGHTMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_AOMAP
	vAoMapUv = ( aoMapTransform * vec3( AOMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_BUMPMAP
	vBumpMapUv = ( bumpMapTransform * vec3( BUMPMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_NORMALMAP
	vNormalMapUv = ( normalMapTransform * vec3( NORMALMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_DISPLACEMENTMAP
	vDisplacementMapUv = ( displacementMapTransform * vec3( DISPLACEMENTMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_EMISSIVEMAP
	vEmissiveMapUv = ( emissiveMapTransform * vec3( EMISSIVEMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_METALNESSMAP
	vMetalnessMapUv = ( metalnessMapTransform * vec3( METALNESSMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_ROUGHNESSMAP
	vRoughnessMapUv = ( roughnessMapTransform * vec3( ROUGHNESSMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_ANISOTROPYMAP
	vAnisotropyMapUv = ( anisotropyMapTransform * vec3( ANISOTROPYMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_CLEARCOATMAP
	vClearcoatMapUv = ( clearcoatMapTransform * vec3( CLEARCOATMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_CLEARCOAT_NORMALMAP
	vClearcoatNormalMapUv = ( clearcoatNormalMapTransform * vec3( CLEARCOAT_NORMALMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_CLEARCOAT_ROUGHNESSMAP
	vClearcoatRoughnessMapUv = ( clearcoatRoughnessMapTransform * vec3( CLEARCOAT_ROUGHNESSMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_IRIDESCENCEMAP
	vIridescenceMapUv = ( iridescenceMapTransform * vec3( IRIDESCENCEMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_IRIDESCENCE_THICKNESSMAP
	vIridescenceThicknessMapUv = ( iridescenceThicknessMapTransform * vec3( IRIDESCENCE_THICKNESSMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_SHEEN_COLORMAP
	vSheenColorMapUv = ( sheenColorMapTransform * vec3( SHEEN_COLORMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_SHEEN_ROUGHNESSMAP
	vSheenRoughnessMapUv = ( sheenRoughnessMapTransform * vec3( SHEEN_ROUGHNESSMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_SPECULARMAP
	vSpecularMapUv = ( specularMapTransform * vec3( SPECULARMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_SPECULAR_COLORMAP
	vSpecularColorMapUv = ( specularColorMapTransform * vec3( SPECULAR_COLORMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_SPECULAR_INTENSITYMAP
	vSpecularIntensityMapUv = ( specularIntensityMapTransform * vec3( SPECULAR_INTENSITYMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_TRANSMISSIONMAP
	vTransmissionMapUv = ( transmissionMapTransform * vec3( TRANSMISSIONMAP_UV, 1 ) ).xy;
#endif
#ifdef USE_THICKNESSMAP
	vThicknessMapUv = ( thicknessMapTransform * vec3( THICKNESSMAP_UV, 1 ) ).xy;
#endif`,$p=`#if defined( USE_ENVMAP ) || defined( DISTANCE ) || defined ( USE_SHADOWMAP ) || defined ( USE_TRANSMISSION ) || NUM_SPOT_LIGHT_COORDS > 0
	vec4 worldPosition = vec4( transformed, 1.0 );
	#ifdef USE_BATCHING
		worldPosition = batchingMatrix * worldPosition;
	#endif
	#ifdef USE_INSTANCING
		worldPosition = instanceMatrix * worldPosition;
	#endif
	worldPosition = modelMatrix * worldPosition;
#endif`,Jp=`varying vec2 vUv;
uniform mat3 uvTransform;
void main() {
	vUv = ( uvTransform * vec3( uv, 1 ) ).xy;
	gl_Position = vec4( position.xy, 1.0, 1.0 );
}`,Qp=`uniform sampler2D t2D;
uniform float backgroundIntensity;
varying vec2 vUv;
void main() {
	vec4 texColor = texture2D( t2D, vUv );
	#ifdef DECODE_VIDEO_TEXTURE
		texColor = vec4( mix( pow( texColor.rgb * 0.9478672986 + vec3( 0.0521327014 ), vec3( 2.4 ) ), texColor.rgb * 0.0773993808, vec3( lessThanEqual( texColor.rgb, vec3( 0.04045 ) ) ) ), texColor.w );
	#endif
	texColor.rgb *= backgroundIntensity;
	gl_FragColor = texColor;
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
}`,jp=`varying vec3 vWorldDirection;
#include <common>
void main() {
	vWorldDirection = transformDirection( position, modelMatrix );
	#include <begin_vertex>
	#include <project_vertex>
	gl_Position.z = gl_Position.w;
}`,t0=`#ifdef ENVMAP_TYPE_CUBE
	uniform samplerCube envMap;
#elif defined( ENVMAP_TYPE_CUBE_UV )
	uniform sampler2D envMap;
#endif
uniform float flipEnvMap;
uniform float backgroundBlurriness;
uniform float backgroundIntensity;
uniform mat3 backgroundRotation;
varying vec3 vWorldDirection;
#include <cube_uv_reflection_fragment>
void main() {
	#ifdef ENVMAP_TYPE_CUBE
		vec4 texColor = textureCube( envMap, backgroundRotation * vec3( flipEnvMap * vWorldDirection.x, vWorldDirection.yz ) );
	#elif defined( ENVMAP_TYPE_CUBE_UV )
		vec4 texColor = textureCubeUV( envMap, backgroundRotation * vWorldDirection, backgroundBlurriness );
	#else
		vec4 texColor = vec4( 0.0, 0.0, 0.0, 1.0 );
	#endif
	texColor.rgb *= backgroundIntensity;
	gl_FragColor = texColor;
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
}`,e0=`varying vec3 vWorldDirection;
#include <common>
void main() {
	vWorldDirection = transformDirection( position, modelMatrix );
	#include <begin_vertex>
	#include <project_vertex>
	gl_Position.z = gl_Position.w;
}`,n0=`uniform samplerCube tCube;
uniform float tFlip;
uniform float opacity;
varying vec3 vWorldDirection;
void main() {
	vec4 texColor = textureCube( tCube, vec3( tFlip * vWorldDirection.x, vWorldDirection.yz ) );
	gl_FragColor = texColor;
	gl_FragColor.a *= opacity;
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
}`,i0=`#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <displacementmap_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
varying vec2 vHighPrecisionZW;
void main() {
	#include <uv_vertex>
	#include <batching_vertex>
	#include <skinbase_vertex>
	#include <morphinstance_vertex>
	#ifdef USE_DISPLACEMENTMAP
		#include <beginnormal_vertex>
		#include <morphnormal_vertex>
		#include <skinnormal_vertex>
	#endif
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <displacementmap_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	vHighPrecisionZW = gl_Position.zw;
}`,s0=`#if DEPTH_PACKING == 3200
	uniform float opacity;
#endif
#include <common>
#include <packing>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
varying vec2 vHighPrecisionZW;
void main() {
	vec4 diffuseColor = vec4( 1.0 );
	#include <clipping_planes_fragment>
	#if DEPTH_PACKING == 3200
		diffuseColor.a = opacity;
	#endif
	#include <map_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	#include <logdepthbuf_fragment>
	float fragCoordZ = 0.5 * vHighPrecisionZW[0] / vHighPrecisionZW[1] + 0.5;
	#if DEPTH_PACKING == 3200
		gl_FragColor = vec4( vec3( 1.0 - fragCoordZ ), opacity );
	#elif DEPTH_PACKING == 3201
		gl_FragColor = packDepthToRGBA( fragCoordZ );
	#elif DEPTH_PACKING == 3202
		gl_FragColor = vec4( packDepthToRGB( fragCoordZ ), 1.0 );
	#elif DEPTH_PACKING == 3203
		gl_FragColor = vec4( packDepthToRG( fragCoordZ ), 0.0, 1.0 );
	#endif
}`,r0=`#define DISTANCE
varying vec3 vWorldPosition;
#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <displacementmap_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	#include <batching_vertex>
	#include <skinbase_vertex>
	#include <morphinstance_vertex>
	#ifdef USE_DISPLACEMENTMAP
		#include <beginnormal_vertex>
		#include <morphnormal_vertex>
		#include <skinnormal_vertex>
	#endif
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <displacementmap_vertex>
	#include <project_vertex>
	#include <worldpos_vertex>
	#include <clipping_planes_vertex>
	vWorldPosition = worldPosition.xyz;
}`,a0=`#define DISTANCE
uniform vec3 referencePosition;
uniform float nearDistance;
uniform float farDistance;
varying vec3 vWorldPosition;
#include <common>
#include <packing>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <clipping_planes_pars_fragment>
void main () {
	vec4 diffuseColor = vec4( 1.0 );
	#include <clipping_planes_fragment>
	#include <map_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	float dist = length( vWorldPosition - referencePosition );
	dist = ( dist - nearDistance ) / ( farDistance - nearDistance );
	dist = saturate( dist );
	gl_FragColor = packDepthToRGBA( dist );
}`,o0=`varying vec3 vWorldDirection;
#include <common>
void main() {
	vWorldDirection = transformDirection( position, modelMatrix );
	#include <begin_vertex>
	#include <project_vertex>
}`,l0=`uniform sampler2D tEquirect;
varying vec3 vWorldDirection;
#include <common>
void main() {
	vec3 direction = normalize( vWorldDirection );
	vec2 sampleUV = equirectUv( direction );
	gl_FragColor = texture2D( tEquirect, sampleUV );
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
}`,c0=`uniform float scale;
attribute float lineDistance;
varying float vLineDistance;
#include <common>
#include <uv_pars_vertex>
#include <color_pars_vertex>
#include <fog_pars_vertex>
#include <morphtarget_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	vLineDistance = scale * lineDistance;
	#include <uv_vertex>
	#include <color_vertex>
	#include <morphinstance_vertex>
	#include <morphcolor_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	#include <fog_vertex>
}`,h0=`uniform vec3 diffuse;
uniform float opacity;
uniform float dashSize;
uniform float totalSize;
varying float vLineDistance;
#include <common>
#include <color_pars_fragment>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <fog_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	if ( mod( vLineDistance, totalSize ) > dashSize ) {
		discard;
	}
	vec3 outgoingLight = vec3( 0.0 );
	#include <logdepthbuf_fragment>
	#include <map_fragment>
	#include <color_fragment>
	outgoingLight = diffuseColor.rgb;
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
}`,u0=`#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <envmap_pars_vertex>
#include <color_pars_vertex>
#include <fog_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	#include <color_vertex>
	#include <morphinstance_vertex>
	#include <morphcolor_vertex>
	#include <batching_vertex>
	#if defined ( USE_ENVMAP ) || defined ( USE_SKINNING )
		#include <beginnormal_vertex>
		#include <morphnormal_vertex>
		#include <skinbase_vertex>
		#include <skinnormal_vertex>
		#include <defaultnormal_vertex>
	#endif
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	#include <worldpos_vertex>
	#include <envmap_vertex>
	#include <fog_vertex>
}`,d0=`uniform vec3 diffuse;
uniform float opacity;
#ifndef FLAT_SHADED
	varying vec3 vNormal;
#endif
#include <common>
#include <dithering_pars_fragment>
#include <color_pars_fragment>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <aomap_pars_fragment>
#include <lightmap_pars_fragment>
#include <envmap_common_pars_fragment>
#include <envmap_pars_fragment>
#include <fog_pars_fragment>
#include <specularmap_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	#include <logdepthbuf_fragment>
	#include <map_fragment>
	#include <color_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	#include <specularmap_fragment>
	ReflectedLight reflectedLight = ReflectedLight( vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ) );
	#ifdef USE_LIGHTMAP
		vec4 lightMapTexel = texture2D( lightMap, vLightMapUv );
		reflectedLight.indirectDiffuse += lightMapTexel.rgb * lightMapIntensity * RECIPROCAL_PI;
	#else
		reflectedLight.indirectDiffuse += vec3( 1.0 );
	#endif
	#include <aomap_fragment>
	reflectedLight.indirectDiffuse *= diffuseColor.rgb;
	vec3 outgoingLight = reflectedLight.indirectDiffuse;
	#include <envmap_fragment>
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
	#include <dithering_fragment>
}`,f0=`#define LAMBERT
varying vec3 vViewPosition;
#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <displacementmap_pars_vertex>
#include <envmap_pars_vertex>
#include <color_pars_vertex>
#include <fog_pars_vertex>
#include <normal_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <shadowmap_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	#include <color_vertex>
	#include <morphinstance_vertex>
	#include <morphcolor_vertex>
	#include <batching_vertex>
	#include <beginnormal_vertex>
	#include <morphnormal_vertex>
	#include <skinbase_vertex>
	#include <skinnormal_vertex>
	#include <defaultnormal_vertex>
	#include <normal_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <displacementmap_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	vViewPosition = - mvPosition.xyz;
	#include <worldpos_vertex>
	#include <envmap_vertex>
	#include <shadowmap_vertex>
	#include <fog_vertex>
}`,p0=`#define LAMBERT
uniform vec3 diffuse;
uniform vec3 emissive;
uniform float opacity;
#include <common>
#include <packing>
#include <dithering_pars_fragment>
#include <color_pars_fragment>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <aomap_pars_fragment>
#include <lightmap_pars_fragment>
#include <emissivemap_pars_fragment>
#include <envmap_common_pars_fragment>
#include <envmap_pars_fragment>
#include <fog_pars_fragment>
#include <bsdfs>
#include <lights_pars_begin>
#include <normal_pars_fragment>
#include <lights_lambert_pars_fragment>
#include <shadowmap_pars_fragment>
#include <bumpmap_pars_fragment>
#include <normalmap_pars_fragment>
#include <specularmap_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	ReflectedLight reflectedLight = ReflectedLight( vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ) );
	vec3 totalEmissiveRadiance = emissive;
	#include <logdepthbuf_fragment>
	#include <map_fragment>
	#include <color_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	#include <specularmap_fragment>
	#include <normal_fragment_begin>
	#include <normal_fragment_maps>
	#include <emissivemap_fragment>
	#include <lights_lambert_fragment>
	#include <lights_fragment_begin>
	#include <lights_fragment_maps>
	#include <lights_fragment_end>
	#include <aomap_fragment>
	vec3 outgoingLight = reflectedLight.directDiffuse + reflectedLight.indirectDiffuse + totalEmissiveRadiance;
	#include <envmap_fragment>
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
	#include <dithering_fragment>
}`,m0=`#define MATCAP
varying vec3 vViewPosition;
#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <color_pars_vertex>
#include <displacementmap_pars_vertex>
#include <fog_pars_vertex>
#include <normal_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	#include <color_vertex>
	#include <morphinstance_vertex>
	#include <morphcolor_vertex>
	#include <batching_vertex>
	#include <beginnormal_vertex>
	#include <morphnormal_vertex>
	#include <skinbase_vertex>
	#include <skinnormal_vertex>
	#include <defaultnormal_vertex>
	#include <normal_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <displacementmap_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	#include <fog_vertex>
	vViewPosition = - mvPosition.xyz;
}`,g0=`#define MATCAP
uniform vec3 diffuse;
uniform float opacity;
uniform sampler2D matcap;
varying vec3 vViewPosition;
#include <common>
#include <dithering_pars_fragment>
#include <color_pars_fragment>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <fog_pars_fragment>
#include <normal_pars_fragment>
#include <bumpmap_pars_fragment>
#include <normalmap_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	#include <logdepthbuf_fragment>
	#include <map_fragment>
	#include <color_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	#include <normal_fragment_begin>
	#include <normal_fragment_maps>
	vec3 viewDir = normalize( vViewPosition );
	vec3 x = normalize( vec3( viewDir.z, 0.0, - viewDir.x ) );
	vec3 y = cross( viewDir, x );
	vec2 uv = vec2( dot( x, normal ), dot( y, normal ) ) * 0.495 + 0.5;
	#ifdef USE_MATCAP
		vec4 matcapColor = texture2D( matcap, uv );
	#else
		vec4 matcapColor = vec4( vec3( mix( 0.2, 0.8, uv.y ) ), 1.0 );
	#endif
	vec3 outgoingLight = diffuseColor.rgb * matcapColor.rgb;
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
	#include <dithering_fragment>
}`,_0=`#define NORMAL
#if defined( FLAT_SHADED ) || defined( USE_BUMPMAP ) || defined( USE_NORMALMAP_TANGENTSPACE )
	varying vec3 vViewPosition;
#endif
#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <displacementmap_pars_vertex>
#include <normal_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	#include <batching_vertex>
	#include <beginnormal_vertex>
	#include <morphinstance_vertex>
	#include <morphnormal_vertex>
	#include <skinbase_vertex>
	#include <skinnormal_vertex>
	#include <defaultnormal_vertex>
	#include <normal_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <displacementmap_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
#if defined( FLAT_SHADED ) || defined( USE_BUMPMAP ) || defined( USE_NORMALMAP_TANGENTSPACE )
	vViewPosition = - mvPosition.xyz;
#endif
}`,x0=`#define NORMAL
uniform float opacity;
#if defined( FLAT_SHADED ) || defined( USE_BUMPMAP ) || defined( USE_NORMALMAP_TANGENTSPACE )
	varying vec3 vViewPosition;
#endif
#include <packing>
#include <uv_pars_fragment>
#include <normal_pars_fragment>
#include <bumpmap_pars_fragment>
#include <normalmap_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( 0.0, 0.0, 0.0, opacity );
	#include <clipping_planes_fragment>
	#include <logdepthbuf_fragment>
	#include <normal_fragment_begin>
	#include <normal_fragment_maps>
	gl_FragColor = vec4( packNormalToRGB( normal ), diffuseColor.a );
	#ifdef OPAQUE
		gl_FragColor.a = 1.0;
	#endif
}`,y0=`#define PHONG
varying vec3 vViewPosition;
#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <displacementmap_pars_vertex>
#include <envmap_pars_vertex>
#include <color_pars_vertex>
#include <fog_pars_vertex>
#include <normal_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <shadowmap_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	#include <color_vertex>
	#include <morphcolor_vertex>
	#include <batching_vertex>
	#include <beginnormal_vertex>
	#include <morphinstance_vertex>
	#include <morphnormal_vertex>
	#include <skinbase_vertex>
	#include <skinnormal_vertex>
	#include <defaultnormal_vertex>
	#include <normal_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <displacementmap_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	vViewPosition = - mvPosition.xyz;
	#include <worldpos_vertex>
	#include <envmap_vertex>
	#include <shadowmap_vertex>
	#include <fog_vertex>
}`,v0=`#define PHONG
uniform vec3 diffuse;
uniform vec3 emissive;
uniform vec3 specular;
uniform float shininess;
uniform float opacity;
#include <common>
#include <packing>
#include <dithering_pars_fragment>
#include <color_pars_fragment>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <aomap_pars_fragment>
#include <lightmap_pars_fragment>
#include <emissivemap_pars_fragment>
#include <envmap_common_pars_fragment>
#include <envmap_pars_fragment>
#include <fog_pars_fragment>
#include <bsdfs>
#include <lights_pars_begin>
#include <normal_pars_fragment>
#include <lights_phong_pars_fragment>
#include <shadowmap_pars_fragment>
#include <bumpmap_pars_fragment>
#include <normalmap_pars_fragment>
#include <specularmap_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	ReflectedLight reflectedLight = ReflectedLight( vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ) );
	vec3 totalEmissiveRadiance = emissive;
	#include <logdepthbuf_fragment>
	#include <map_fragment>
	#include <color_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	#include <specularmap_fragment>
	#include <normal_fragment_begin>
	#include <normal_fragment_maps>
	#include <emissivemap_fragment>
	#include <lights_phong_fragment>
	#include <lights_fragment_begin>
	#include <lights_fragment_maps>
	#include <lights_fragment_end>
	#include <aomap_fragment>
	vec3 outgoingLight = reflectedLight.directDiffuse + reflectedLight.indirectDiffuse + reflectedLight.directSpecular + reflectedLight.indirectSpecular + totalEmissiveRadiance;
	#include <envmap_fragment>
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
	#include <dithering_fragment>
}`,M0=`#define STANDARD
varying vec3 vViewPosition;
#ifdef USE_TRANSMISSION
	varying vec3 vWorldPosition;
#endif
#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <displacementmap_pars_vertex>
#include <color_pars_vertex>
#include <fog_pars_vertex>
#include <normal_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <shadowmap_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	#include <color_vertex>
	#include <morphinstance_vertex>
	#include <morphcolor_vertex>
	#include <batching_vertex>
	#include <beginnormal_vertex>
	#include <morphnormal_vertex>
	#include <skinbase_vertex>
	#include <skinnormal_vertex>
	#include <defaultnormal_vertex>
	#include <normal_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <displacementmap_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	vViewPosition = - mvPosition.xyz;
	#include <worldpos_vertex>
	#include <shadowmap_vertex>
	#include <fog_vertex>
#ifdef USE_TRANSMISSION
	vWorldPosition = worldPosition.xyz;
#endif
}`,S0=`#define STANDARD
#ifdef PHYSICAL
	#define IOR
	#define USE_SPECULAR
#endif
uniform vec3 diffuse;
uniform vec3 emissive;
uniform float roughness;
uniform float metalness;
uniform float opacity;
#ifdef IOR
	uniform float ior;
#endif
#ifdef USE_SPECULAR
	uniform float specularIntensity;
	uniform vec3 specularColor;
	#ifdef USE_SPECULAR_COLORMAP
		uniform sampler2D specularColorMap;
	#endif
	#ifdef USE_SPECULAR_INTENSITYMAP
		uniform sampler2D specularIntensityMap;
	#endif
#endif
#ifdef USE_CLEARCOAT
	uniform float clearcoat;
	uniform float clearcoatRoughness;
#endif
#ifdef USE_DISPERSION
	uniform float dispersion;
#endif
#ifdef USE_IRIDESCENCE
	uniform float iridescence;
	uniform float iridescenceIOR;
	uniform float iridescenceThicknessMinimum;
	uniform float iridescenceThicknessMaximum;
#endif
#ifdef USE_SHEEN
	uniform vec3 sheenColor;
	uniform float sheenRoughness;
	#ifdef USE_SHEEN_COLORMAP
		uniform sampler2D sheenColorMap;
	#endif
	#ifdef USE_SHEEN_ROUGHNESSMAP
		uniform sampler2D sheenRoughnessMap;
	#endif
#endif
#ifdef USE_ANISOTROPY
	uniform vec2 anisotropyVector;
	#ifdef USE_ANISOTROPYMAP
		uniform sampler2D anisotropyMap;
	#endif
#endif
varying vec3 vViewPosition;
#include <common>
#include <packing>
#include <dithering_pars_fragment>
#include <color_pars_fragment>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <aomap_pars_fragment>
#include <lightmap_pars_fragment>
#include <emissivemap_pars_fragment>
#include <iridescence_fragment>
#include <cube_uv_reflection_fragment>
#include <envmap_common_pars_fragment>
#include <envmap_physical_pars_fragment>
#include <fog_pars_fragment>
#include <lights_pars_begin>
#include <normal_pars_fragment>
#include <lights_physical_pars_fragment>
#include <transmission_pars_fragment>
#include <shadowmap_pars_fragment>
#include <bumpmap_pars_fragment>
#include <normalmap_pars_fragment>
#include <clearcoat_pars_fragment>
#include <iridescence_pars_fragment>
#include <roughnessmap_pars_fragment>
#include <metalnessmap_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	ReflectedLight reflectedLight = ReflectedLight( vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ) );
	vec3 totalEmissiveRadiance = emissive;
	#include <logdepthbuf_fragment>
	#include <map_fragment>
	#include <color_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	#include <roughnessmap_fragment>
	#include <metalnessmap_fragment>
	#include <normal_fragment_begin>
	#include <normal_fragment_maps>
	#include <clearcoat_normal_fragment_begin>
	#include <clearcoat_normal_fragment_maps>
	#include <emissivemap_fragment>
	#include <lights_physical_fragment>
	#include <lights_fragment_begin>
	#include <lights_fragment_maps>
	#include <lights_fragment_end>
	#include <aomap_fragment>
	vec3 totalDiffuse = reflectedLight.directDiffuse + reflectedLight.indirectDiffuse;
	vec3 totalSpecular = reflectedLight.directSpecular + reflectedLight.indirectSpecular;
	#include <transmission_fragment>
	vec3 outgoingLight = totalDiffuse + totalSpecular + totalEmissiveRadiance;
	#ifdef USE_SHEEN
		float sheenEnergyComp = 1.0 - 0.157 * max3( material.sheenColor );
		outgoingLight = outgoingLight * sheenEnergyComp + sheenSpecularDirect + sheenSpecularIndirect;
	#endif
	#ifdef USE_CLEARCOAT
		float dotNVcc = saturate( dot( geometryClearcoatNormal, geometryViewDir ) );
		vec3 Fcc = F_Schlick( material.clearcoatF0, material.clearcoatF90, dotNVcc );
		outgoingLight = outgoingLight * ( 1.0 - material.clearcoat * Fcc ) + ( clearcoatSpecularDirect + clearcoatSpecularIndirect ) * material.clearcoat;
	#endif
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
	#include <dithering_fragment>
}`,E0=`#define TOON
varying vec3 vViewPosition;
#include <common>
#include <batching_pars_vertex>
#include <uv_pars_vertex>
#include <displacementmap_pars_vertex>
#include <color_pars_vertex>
#include <fog_pars_vertex>
#include <normal_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <shadowmap_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	#include <color_vertex>
	#include <morphinstance_vertex>
	#include <morphcolor_vertex>
	#include <batching_vertex>
	#include <beginnormal_vertex>
	#include <morphnormal_vertex>
	#include <skinbase_vertex>
	#include <skinnormal_vertex>
	#include <defaultnormal_vertex>
	#include <normal_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <displacementmap_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	vViewPosition = - mvPosition.xyz;
	#include <worldpos_vertex>
	#include <shadowmap_vertex>
	#include <fog_vertex>
}`,b0=`#define TOON
uniform vec3 diffuse;
uniform vec3 emissive;
uniform float opacity;
#include <common>
#include <packing>
#include <dithering_pars_fragment>
#include <color_pars_fragment>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <aomap_pars_fragment>
#include <lightmap_pars_fragment>
#include <emissivemap_pars_fragment>
#include <gradientmap_pars_fragment>
#include <fog_pars_fragment>
#include <bsdfs>
#include <lights_pars_begin>
#include <normal_pars_fragment>
#include <lights_toon_pars_fragment>
#include <shadowmap_pars_fragment>
#include <bumpmap_pars_fragment>
#include <normalmap_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	ReflectedLight reflectedLight = ReflectedLight( vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ), vec3( 0.0 ) );
	vec3 totalEmissiveRadiance = emissive;
	#include <logdepthbuf_fragment>
	#include <map_fragment>
	#include <color_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	#include <normal_fragment_begin>
	#include <normal_fragment_maps>
	#include <emissivemap_fragment>
	#include <lights_toon_fragment>
	#include <lights_fragment_begin>
	#include <lights_fragment_maps>
	#include <lights_fragment_end>
	#include <aomap_fragment>
	vec3 outgoingLight = reflectedLight.directDiffuse + reflectedLight.indirectDiffuse + totalEmissiveRadiance;
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
	#include <dithering_fragment>
}`,w0=`uniform float size;
uniform float scale;
#include <common>
#include <color_pars_vertex>
#include <fog_pars_vertex>
#include <morphtarget_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
#ifdef USE_POINTS_UV
	varying vec2 vUv;
	uniform mat3 uvTransform;
#endif
void main() {
	#ifdef USE_POINTS_UV
		vUv = ( uvTransform * vec3( uv, 1 ) ).xy;
	#endif
	#include <color_vertex>
	#include <morphinstance_vertex>
	#include <morphcolor_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <project_vertex>
	gl_PointSize = size;
	#ifdef USE_SIZEATTENUATION
		bool isPerspective = isPerspectiveMatrix( projectionMatrix );
		if ( isPerspective ) gl_PointSize *= ( scale / - mvPosition.z );
	#endif
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	#include <worldpos_vertex>
	#include <fog_vertex>
}`,T0=`uniform vec3 diffuse;
uniform float opacity;
#include <common>
#include <color_pars_fragment>
#include <map_particle_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <fog_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	vec3 outgoingLight = vec3( 0.0 );
	#include <logdepthbuf_fragment>
	#include <map_particle_fragment>
	#include <color_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	outgoingLight = diffuseColor.rgb;
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
	#include <premultiplied_alpha_fragment>
}`,A0=`#include <common>
#include <batching_pars_vertex>
#include <fog_pars_vertex>
#include <morphtarget_pars_vertex>
#include <skinning_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <shadowmap_pars_vertex>
void main() {
	#include <batching_vertex>
	#include <beginnormal_vertex>
	#include <morphinstance_vertex>
	#include <morphnormal_vertex>
	#include <skinbase_vertex>
	#include <skinnormal_vertex>
	#include <defaultnormal_vertex>
	#include <begin_vertex>
	#include <morphtarget_vertex>
	#include <skinning_vertex>
	#include <project_vertex>
	#include <logdepthbuf_vertex>
	#include <worldpos_vertex>
	#include <shadowmap_vertex>
	#include <fog_vertex>
}`,R0=`uniform vec3 color;
uniform float opacity;
#include <common>
#include <packing>
#include <fog_pars_fragment>
#include <bsdfs>
#include <lights_pars_begin>
#include <logdepthbuf_pars_fragment>
#include <shadowmap_pars_fragment>
#include <shadowmask_pars_fragment>
void main() {
	#include <logdepthbuf_fragment>
	gl_FragColor = vec4( color, opacity * ( 1.0 - getShadowMask() ) );
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
}`,C0=`uniform float rotation;
uniform vec2 center;
#include <common>
#include <uv_pars_vertex>
#include <fog_pars_vertex>
#include <logdepthbuf_pars_vertex>
#include <clipping_planes_pars_vertex>
void main() {
	#include <uv_vertex>
	vec4 mvPosition = modelViewMatrix[ 3 ];
	vec2 scale = vec2( length( modelMatrix[ 0 ].xyz ), length( modelMatrix[ 1 ].xyz ) );
	#ifndef USE_SIZEATTENUATION
		bool isPerspective = isPerspectiveMatrix( projectionMatrix );
		if ( isPerspective ) scale *= - mvPosition.z;
	#endif
	vec2 alignedPosition = ( position.xy - ( center - vec2( 0.5 ) ) ) * scale;
	vec2 rotatedPosition;
	rotatedPosition.x = cos( rotation ) * alignedPosition.x - sin( rotation ) * alignedPosition.y;
	rotatedPosition.y = sin( rotation ) * alignedPosition.x + cos( rotation ) * alignedPosition.y;
	mvPosition.xy += rotatedPosition;
	gl_Position = projectionMatrix * mvPosition;
	#include <logdepthbuf_vertex>
	#include <clipping_planes_vertex>
	#include <fog_vertex>
}`,I0=`uniform vec3 diffuse;
uniform float opacity;
#include <common>
#include <uv_pars_fragment>
#include <map_pars_fragment>
#include <alphamap_pars_fragment>
#include <alphatest_pars_fragment>
#include <alphahash_pars_fragment>
#include <fog_pars_fragment>
#include <logdepthbuf_pars_fragment>
#include <clipping_planes_pars_fragment>
void main() {
	vec4 diffuseColor = vec4( diffuse, opacity );
	#include <clipping_planes_fragment>
	vec3 outgoingLight = vec3( 0.0 );
	#include <logdepthbuf_fragment>
	#include <map_fragment>
	#include <alphamap_fragment>
	#include <alphatest_fragment>
	#include <alphahash_fragment>
	outgoingLight = diffuseColor.rgb;
	#include <opaque_fragment>
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
	#include <fog_fragment>
}`,$t={alphahash_fragment:Jd,alphahash_pars_fragment:Qd,alphamap_fragment:jd,alphamap_pars_fragment:tf,alphatest_fragment:ef,alphatest_pars_fragment:nf,aomap_fragment:sf,aomap_pars_fragment:rf,batching_pars_vertex:af,batching_vertex:of,begin_vertex:lf,beginnormal_vertex:cf,bsdfs:hf,iridescence_fragment:uf,bumpmap_pars_fragment:df,clipping_planes_fragment:ff,clipping_planes_pars_fragment:pf,clipping_planes_pars_vertex:mf,clipping_planes_vertex:gf,color_fragment:_f,color_pars_fragment:xf,color_pars_vertex:yf,color_vertex:vf,common:Mf,cube_uv_reflection_fragment:Sf,defaultnormal_vertex:Ef,displacementmap_pars_vertex:bf,displacementmap_vertex:wf,emissivemap_fragment:Tf,emissivemap_pars_fragment:Af,colorspace_fragment:Rf,colorspace_pars_fragment:Cf,envmap_fragment:If,envmap_common_pars_fragment:Pf,envmap_pars_fragment:Ff,envmap_pars_vertex:Df,envmap_physical_pars_fragment:Wf,envmap_vertex:Lf,fog_vertex:Uf,fog_pars_vertex:Nf,fog_fragment:Of,fog_pars_fragment:Bf,gradientmap_pars_fragment:zf,lightmap_pars_fragment:kf,lights_lambert_fragment:Hf,lights_lambert_pars_fragment:Vf,lights_pars_begin:Gf,lights_toon_fragment:Kf,lights_toon_pars_fragment:Xf,lights_phong_fragment:qf,lights_phong_pars_fragment:Yf,lights_physical_fragment:Zf,lights_physical_pars_fragment:$f,lights_fragment_begin:Jf,lights_fragment_maps:Qf,lights_fragment_end:jf,logdepthbuf_fragment:tp,logdepthbuf_pars_fragment:ep,logdepthbuf_pars_vertex:np,logdepthbuf_vertex:ip,map_fragment:sp,map_pars_fragment:rp,map_particle_fragment:ap,map_particle_pars_fragment:op,metalnessmap_fragment:lp,metalnessmap_pars_fragment:cp,morphinstance_vertex:hp,morphcolor_vertex:up,morphnormal_vertex:dp,morphtarget_pars_vertex:fp,morphtarget_vertex:pp,normal_fragment_begin:mp,normal_fragment_maps:gp,normal_pars_fragment:_p,normal_pars_vertex:xp,normal_vertex:yp,normalmap_pars_fragment:vp,clearcoat_normal_fragment_begin:Mp,clearcoat_normal_fragment_maps:Sp,clearcoat_pars_fragment:Ep,iridescence_pars_fragment:bp,opaque_fragment:wp,packing:Tp,premultiplied_alpha_fragment:Ap,project_vertex:Rp,dithering_fragment:Cp,dithering_pars_fragment:Ip,roughnessmap_fragment:Pp,roughnessmap_pars_fragment:Fp,shadowmap_pars_fragment:Dp,shadowmap_pars_vertex:Lp,shadowmap_vertex:Up,shadowmask_pars_fragment:Np,skinbase_vertex:Op,skinning_pars_vertex:Bp,skinning_vertex:zp,skinnormal_vertex:kp,specularmap_fragment:Hp,specularmap_pars_fragment:Vp,tonemapping_fragment:Gp,tonemapping_pars_fragment:Wp,transmission_fragment:Kp,transmission_pars_fragment:Xp,uv_pars_fragment:qp,uv_pars_vertex:Yp,uv_vertex:Zp,worldpos_vertex:$p,background_vert:Jp,background_frag:Qp,backgroundCube_vert:jp,backgroundCube_frag:t0,cube_vert:e0,cube_frag:n0,depth_vert:i0,depth_frag:s0,distanceRGBA_vert:r0,distanceRGBA_frag:a0,equirect_vert:o0,equirect_frag:l0,linedashed_vert:c0,linedashed_frag:h0,meshbasic_vert:u0,meshbasic_frag:d0,meshlambert_vert:f0,meshlambert_frag:p0,meshmatcap_vert:m0,meshmatcap_frag:g0,meshnormal_vert:_0,meshnormal_frag:x0,meshphong_vert:y0,meshphong_frag:v0,meshphysical_vert:M0,meshphysical_frag:S0,meshtoon_vert:E0,meshtoon_frag:b0,points_vert:w0,points_frag:T0,shadow_vert:A0,shadow_frag:R0,sprite_vert:C0,sprite_frag:I0},ut={common:{diffuse:{value:new Gt(16777215)},opacity:{value:1},map:{value:null},mapTransform:{value:new Yt},alphaMap:{value:null},alphaMapTransform:{value:new Yt},alphaTest:{value:0}},specularmap:{specularMap:{value:null},specularMapTransform:{value:new Yt}},envmap:{envMap:{value:null},envMapRotation:{value:new Yt},flipEnvMap:{value:-1},reflectivity:{value:1},ior:{value:1.5},refractionRatio:{value:.98}},aomap:{aoMap:{value:null},aoMapIntensity:{value:1},aoMapTransform:{value:new Yt}},lightmap:{lightMap:{value:null},lightMapIntensity:{value:1},lightMapTransform:{value:new Yt}},bumpmap:{bumpMap:{value:null},bumpMapTransform:{value:new Yt},bumpScale:{value:1}},normalmap:{normalMap:{value:null},normalMapTransform:{value:new Yt},normalScale:{value:new at(1,1)}},displacementmap:{displacementMap:{value:null},displacementMapTransform:{value:new Yt},displacementScale:{value:1},displacementBias:{value:0}},emissivemap:{emissiveMap:{value:null},emissiveMapTransform:{value:new Yt}},metalnessmap:{metalnessMap:{value:null},metalnessMapTransform:{value:new Yt}},roughnessmap:{roughnessMap:{value:null},roughnessMapTransform:{value:new Yt}},gradientmap:{gradientMap:{value:null}},fog:{fogDensity:{value:25e-5},fogNear:{value:1},fogFar:{value:2e3},fogColor:{value:new Gt(16777215)}},lights:{ambientLightColor:{value:[]},lightProbe:{value:[]},directionalLights:{value:[],properties:{direction:{},color:{}}},directionalLightShadows:{value:[],properties:{shadowIntensity:1,shadowBias:{},shadowNormalBias:{},shadowRadius:{},shadowMapSize:{}}},directionalShadowMap:{value:[]},directionalShadowMatrix:{value:[]},spotLights:{value:[],properties:{color:{},position:{},direction:{},distance:{},coneCos:{},penumbraCos:{},decay:{}}},spotLightShadows:{value:[],properties:{shadowIntensity:1,shadowBias:{},shadowNormalBias:{},shadowRadius:{},shadowMapSize:{}}},spotLightMap:{value:[]},spotShadowMap:{value:[]},spotLightMatrix:{value:[]},pointLights:{value:[],properties:{color:{},position:{},decay:{},distance:{}}},pointLightShadows:{value:[],properties:{shadowIntensity:1,shadowBias:{},shadowNormalBias:{},shadowRadius:{},shadowMapSize:{},shadowCameraNear:{},shadowCameraFar:{}}},pointShadowMap:{value:[]},pointShadowMatrix:{value:[]},hemisphereLights:{value:[],properties:{direction:{},skyColor:{},groundColor:{}}},rectAreaLights:{value:[],properties:{color:{},position:{},width:{},height:{}}},ltc_1:{value:null},ltc_2:{value:null}},points:{diffuse:{value:new Gt(16777215)},opacity:{value:1},size:{value:1},scale:{value:1},map:{value:null},alphaMap:{value:null},alphaMapTransform:{value:new Yt},alphaTest:{value:0},uvTransform:{value:new Yt}},sprite:{diffuse:{value:new Gt(16777215)},opacity:{value:1},center:{value:new at(.5,.5)},rotation:{value:0},map:{value:null},mapTransform:{value:new Yt},alphaMap:{value:null},alphaMapTransform:{value:new Yt},alphaTest:{value:0}}},In={basic:{uniforms:Xe([ut.common,ut.specularmap,ut.envmap,ut.aomap,ut.lightmap,ut.fog]),vertexShader:$t.meshbasic_vert,fragmentShader:$t.meshbasic_frag},lambert:{uniforms:Xe([ut.common,ut.specularmap,ut.envmap,ut.aomap,ut.lightmap,ut.emissivemap,ut.bumpmap,ut.normalmap,ut.displacementmap,ut.fog,ut.lights,{emissive:{value:new Gt(0)}}]),vertexShader:$t.meshlambert_vert,fragmentShader:$t.meshlambert_frag},phong:{uniforms:Xe([ut.common,ut.specularmap,ut.envmap,ut.aomap,ut.lightmap,ut.emissivemap,ut.bumpmap,ut.normalmap,ut.displacementmap,ut.fog,ut.lights,{emissive:{value:new Gt(0)},specular:{value:new Gt(1118481)},shininess:{value:30}}]),vertexShader:$t.meshphong_vert,fragmentShader:$t.meshphong_frag},standard:{uniforms:Xe([ut.common,ut.envmap,ut.aomap,ut.lightmap,ut.emissivemap,ut.bumpmap,ut.normalmap,ut.displacementmap,ut.roughnessmap,ut.metalnessmap,ut.fog,ut.lights,{emissive:{value:new Gt(0)},roughness:{value:1},metalness:{value:0},envMapIntensity:{value:1}}]),vertexShader:$t.meshphysical_vert,fragmentShader:$t.meshphysical_frag},toon:{uniforms:Xe([ut.common,ut.aomap,ut.lightmap,ut.emissivemap,ut.bumpmap,ut.normalmap,ut.displacementmap,ut.gradientmap,ut.fog,ut.lights,{emissive:{value:new Gt(0)}}]),vertexShader:$t.meshtoon_vert,fragmentShader:$t.meshtoon_frag},matcap:{uniforms:Xe([ut.common,ut.bumpmap,ut.normalmap,ut.displacementmap,ut.fog,{matcap:{value:null}}]),vertexShader:$t.meshmatcap_vert,fragmentShader:$t.meshmatcap_frag},points:{uniforms:Xe([ut.points,ut.fog]),vertexShader:$t.points_vert,fragmentShader:$t.points_frag},dashed:{uniforms:Xe([ut.common,ut.fog,{scale:{value:1},dashSize:{value:1},totalSize:{value:2}}]),vertexShader:$t.linedashed_vert,fragmentShader:$t.linedashed_frag},depth:{uniforms:Xe([ut.common,ut.displacementmap]),vertexShader:$t.depth_vert,fragmentShader:$t.depth_frag},normal:{uniforms:Xe([ut.common,ut.bumpmap,ut.normalmap,ut.displacementmap,{opacity:{value:1}}]),vertexShader:$t.meshnormal_vert,fragmentShader:$t.meshnormal_frag},sprite:{uniforms:Xe([ut.sprite,ut.fog]),vertexShader:$t.sprite_vert,fragmentShader:$t.sprite_frag},background:{uniforms:{uvTransform:{value:new Yt},t2D:{value:null},backgroundIntensity:{value:1}},vertexShader:$t.background_vert,fragmentShader:$t.background_frag},backgroundCube:{uniforms:{envMap:{value:null},flipEnvMap:{value:-1},backgroundBlurriness:{value:0},backgroundIntensity:{value:1},backgroundRotation:{value:new Yt}},vertexShader:$t.backgroundCube_vert,fragmentShader:$t.backgroundCube_frag},cube:{uniforms:{tCube:{value:null},tFlip:{value:-1},opacity:{value:1}},vertexShader:$t.cube_vert,fragmentShader:$t.cube_frag},equirect:{uniforms:{tEquirect:{value:null}},vertexShader:$t.equirect_vert,fragmentShader:$t.equirect_frag},distanceRGBA:{uniforms:Xe([ut.common,ut.displacementmap,{referencePosition:{value:new L},nearDistance:{value:1},farDistance:{value:1e3}}]),vertexShader:$t.distanceRGBA_vert,fragmentShader:$t.distanceRGBA_frag},shadow:{uniforms:Xe([ut.lights,ut.fog,{color:{value:new Gt(0)},opacity:{value:1}}]),vertexShader:$t.shadow_vert,fragmentShader:$t.shadow_frag}};In.physical={uniforms:Xe([In.standard.uniforms,{clearcoat:{value:0},clearcoatMap:{value:null},clearcoatMapTransform:{value:new Yt},clearcoatNormalMap:{value:null},clearcoatNormalMapTransform:{value:new Yt},clearcoatNormalScale:{value:new at(1,1)},clearcoatRoughness:{value:0},clearcoatRoughnessMap:{value:null},clearcoatRoughnessMapTransform:{value:new Yt},dispersion:{value:0},iridescence:{value:0},iridescenceMap:{value:null},iridescenceMapTransform:{value:new Yt},iridescenceIOR:{value:1.3},iridescenceThicknessMinimum:{value:100},iridescenceThicknessMaximum:{value:400},iridescenceThicknessMap:{value:null},iridescenceThicknessMapTransform:{value:new Yt},sheen:{value:0},sheenColor:{value:new Gt(0)},sheenColorMap:{value:null},sheenColorMapTransform:{value:new Yt},sheenRoughness:{value:1},sheenRoughnessMap:{value:null},sheenRoughnessMapTransform:{value:new Yt},transmission:{value:0},transmissionMap:{value:null},transmissionMapTransform:{value:new Yt},transmissionSamplerSize:{value:new at},transmissionSamplerMap:{value:null},thickness:{value:0},thicknessMap:{value:null},thicknessMapTransform:{value:new Yt},attenuationDistance:{value:0},attenuationColor:{value:new Gt(0)},specularColor:{value:new Gt(1,1,1)},specularColorMap:{value:null},specularColorMapTransform:{value:new Yt},specularIntensity:{value:1},specularIntensityMap:{value:null},specularIntensityMapTransform:{value:new Yt},anisotropyVector:{value:new at},anisotropyMap:{value:null},anisotropyMapTransform:{value:new Yt}}]),vertexShader:$t.meshphysical_vert,fragmentShader:$t.meshphysical_frag};var Ur={r:0,b:0,g:0},Ai=new pn,P0=new he;function F0(i,t,e,n,s,r,a){let o=new Gt(0),l=r===!0?0:1,c,h,u=null,d=0,p=null;function g(y){let _=y.isScene===!0?y.background:null;return _&&_.isTexture&&(_=(y.backgroundBlurriness>0?e:t).get(_)),_}function x(y){let _=!1,M=g(y);M===null?f(o,l):M&&M.isColor&&(f(M,1),_=!0);let C=i.xr.getEnvironmentBlendMode();C==="additive"?n.buffers.color.setClear(0,0,0,1,a):C==="alpha-blend"&&n.buffers.color.setClear(0,0,0,0,a),(i.autoClear||_)&&(n.buffers.depth.setTest(!0),n.buffers.depth.setMask(!0),n.buffers.color.setMask(!0),i.clear(i.autoClearColor,i.autoClearDepth,i.autoClearStencil))}function m(y,_){let M=g(_);M&&(M.isCubeTexture||M.mapping===ma)?(h===void 0&&(h=new Ct(new on(1,1,1),new mn({name:"BackgroundCubeMaterial",uniforms:ys(In.backgroundCube.uniforms),vertexShader:In.backgroundCube.vertexShader,fragmentShader:In.backgroundCube.fragmentShader,side:ze,depthTest:!1,depthWrite:!1,fog:!1})),h.geometry.deleteAttribute("normal"),h.geometry.deleteAttribute("uv"),h.onBeforeRender=function(C,A,E){this.matrixWorld.copyPosition(E.matrixWorld)},Object.defineProperty(h.material,"envMap",{get:function(){return this.uniforms.envMap.value}}),s.update(h)),Ai.copy(_.backgroundRotation),Ai.x*=-1,Ai.y*=-1,Ai.z*=-1,M.isCubeTexture&&M.isRenderTargetTexture===!1&&(Ai.y*=-1,Ai.z*=-1),h.material.uniforms.envMap.value=M,h.material.uniforms.flipEnvMap.value=M.isCubeTexture&&M.isRenderTargetTexture===!1?-1:1,h.material.uniforms.backgroundBlurriness.value=_.backgroundBlurriness,h.material.uniforms.backgroundIntensity.value=_.backgroundIntensity,h.material.uniforms.backgroundRotation.value.setFromMatrix4(P0.makeRotationFromEuler(Ai)),h.material.toneMapped=ne.getTransfer(M.colorSpace)!==le,(u!==M||d!==M.version||p!==i.toneMapping)&&(h.material.needsUpdate=!0,u=M,d=M.version,p=i.toneMapping),h.layers.enableAll(),y.unshift(h,h.geometry,h.material,0,0,null)):M&&M.isTexture&&(c===void 0&&(c=new Ct(new Je(2,2),new mn({name:"BackgroundMaterial",uniforms:ys(In.background.uniforms),vertexShader:In.background.vertexShader,fragmentShader:In.background.fragmentShader,side:fi,depthTest:!1,depthWrite:!1,fog:!1})),c.geometry.deleteAttribute("normal"),Object.defineProperty(c.material,"map",{get:function(){return this.uniforms.t2D.value}}),s.update(c)),c.material.uniforms.t2D.value=M,c.material.uniforms.backgroundIntensity.value=_.backgroundIntensity,c.material.toneMapped=ne.getTransfer(M.colorSpace)!==le,M.matrixAutoUpdate===!0&&M.updateMatrix(),c.material.uniforms.uvTransform.value.copy(M.matrix),(u!==M||d!==M.version||p!==i.toneMapping)&&(c.material.needsUpdate=!0,u=M,d=M.version,p=i.toneMapping),c.layers.enableAll(),y.unshift(c,c.geometry,c.material,0,0,null))}function f(y,_){y.getRGB(Ur,Oh(i)),n.buffers.color.setClear(Ur.r,Ur.g,Ur.b,_,a)}return{getClearColor:function(){return o},setClearColor:function(y,_=1){o.set(y),l=_,f(o,l)},getClearAlpha:function(){return l},setClearAlpha:function(y){l=y,f(o,l)},render:x,addToRenderList:m}}function D0(i,t){let e=i.getParameter(i.MAX_VERTEX_ATTRIBS),n={},s=d(null),r=s,a=!1;function o(S,w,P,F,U){let H=!1,N=u(F,P,w);r!==N&&(r=N,c(r.object)),H=p(S,F,P,U),H&&g(S,F,P,U),U!==null&&t.update(U,i.ELEMENT_ARRAY_BUFFER),(H||a)&&(a=!1,M(S,w,P,F),U!==null&&i.bindBuffer(i.ELEMENT_ARRAY_BUFFER,t.get(U).buffer))}function l(){return i.createVertexArray()}function c(S){return i.bindVertexArray(S)}function h(S){return i.deleteVertexArray(S)}function u(S,w,P){let F=P.wireframe===!0,U=n[S.id];U===void 0&&(U={},n[S.id]=U);let H=U[w.id];H===void 0&&(H={},U[w.id]=H);let N=H[F];return N===void 0&&(N=d(l()),H[F]=N),N}function d(S){let w=[],P=[],F=[];for(let U=0;U<e;U++)w[U]=0,P[U]=0,F[U]=0;return{geometry:null,program:null,wireframe:!1,newAttributes:w,enabledAttributes:P,attributeDivisors:F,object:S,attributes:{},index:null}}function p(S,w,P,F){let U=r.attributes,H=w.attributes,N=0,K=P.getAttributes();for(let O in K)if(K[O].location>=0){let tt=U[O],nt=H[O];if(nt===void 0&&(O==="instanceMatrix"&&S.instanceMatrix&&(nt=S.instanceMatrix),O==="instanceColor"&&S.instanceColor&&(nt=S.instanceColor)),tt===void 0||tt.attribute!==nt||nt&&tt.data!==nt.data)return!0;N++}return r.attributesNum!==N||r.index!==F}function g(S,w,P,F){let U={},H=w.attributes,N=0,K=P.getAttributes();for(let O in K)if(K[O].location>=0){let tt=H[O];tt===void 0&&(O==="instanceMatrix"&&S.instanceMatrix&&(tt=S.instanceMatrix),O==="instanceColor"&&S.instanceColor&&(tt=S.instanceColor));let nt={};nt.attribute=tt,tt&&tt.data&&(nt.data=tt.data),U[O]=nt,N++}r.attributes=U,r.attributesNum=N,r.index=F}function x(){let S=r.newAttributes;for(let w=0,P=S.length;w<P;w++)S[w]=0}function m(S){f(S,0)}function f(S,w){let P=r.newAttributes,F=r.enabledAttributes,U=r.attributeDivisors;P[S]=1,F[S]===0&&(i.enableVertexAttribArray(S),F[S]=1),U[S]!==w&&(i.vertexAttribDivisor(S,w),U[S]=w)}function y(){let S=r.newAttributes,w=r.enabledAttributes;for(let P=0,F=w.length;P<F;P++)w[P]!==S[P]&&(i.disableVertexAttribArray(P),w[P]=0)}function _(S,w,P,F,U,H,N){N===!0?i.vertexAttribIPointer(S,w,P,U,H):i.vertexAttribPointer(S,w,P,F,U,H)}function M(S,w,P,F){x();let U=F.attributes,H=P.getAttributes(),N=w.defaultAttributeValues;for(let K in H){let O=H[K];if(O.location>=0){let Y=U[K];if(Y===void 0&&(K==="instanceMatrix"&&S.instanceMatrix&&(Y=S.instanceMatrix),K==="instanceColor"&&S.instanceColor&&(Y=S.instanceColor)),Y!==void 0){let tt=Y.normalized,nt=Y.itemSize,Tt=t.get(Y);if(Tt===void 0)continue;let Nt=Tt.buffer,X=Tt.type,st=Tt.bytesPerElement,xt=X===i.INT||X===i.UNSIGNED_INT||Y.gpuType===Pl;if(Y.isInterleavedBufferAttribute){let ct=Y.data,Ut=ct.stride,kt=Y.offset;if(ct.isInstancedInterleavedBuffer){for(let At=0;At<O.locationSize;At++)f(O.location+At,ct.meshPerAttribute);S.isInstancedMesh!==!0&&F._maxInstanceCount===void 0&&(F._maxInstanceCount=ct.meshPerAttribute*ct.count)}else for(let At=0;At<O.locationSize;At++)m(O.location+At);i.bindBuffer(i.ARRAY_BUFFER,Nt);for(let At=0;At<O.locationSize;At++)_(O.location+At,nt/O.locationSize,X,tt,Ut*st,(kt+nt/O.locationSize*At)*st,xt)}else{if(Y.isInstancedBufferAttribute){for(let ct=0;ct<O.locationSize;ct++)f(O.location+ct,Y.meshPerAttribute);S.isInstancedMesh!==!0&&F._maxInstanceCount===void 0&&(F._maxInstanceCount=Y.meshPerAttribute*Y.count)}else for(let ct=0;ct<O.locationSize;ct++)m(O.location+ct);i.bindBuffer(i.ARRAY_BUFFER,Nt);for(let ct=0;ct<O.locationSize;ct++)_(O.location+ct,nt/O.locationSize,X,tt,nt*st,nt/O.locationSize*ct*st,xt)}}else if(N!==void 0){let tt=N[K];if(tt!==void 0)switch(tt.length){case 2:i.vertexAttrib2fv(O.location,tt);break;case 3:i.vertexAttrib3fv(O.location,tt);break;case 4:i.vertexAttrib4fv(O.location,tt);break;default:i.vertexAttrib1fv(O.location,tt)}}}}y()}function C(){b();for(let S in n){let w=n[S];for(let P in w){let F=w[P];for(let U in F)h(F[U].object),delete F[U];delete w[P]}delete n[S]}}function A(S){if(n[S.id]===void 0)return;let w=n[S.id];for(let P in w){let F=w[P];for(let U in F)h(F[U].object),delete F[U];delete w[P]}delete n[S.id]}function E(S){for(let w in n){let P=n[w];if(P[S.id]===void 0)continue;let F=P[S.id];for(let U in F)h(F[U].object),delete F[U];delete P[S.id]}}function b(){v(),a=!0,r!==s&&(r=s,c(r.object))}function v(){s.geometry=null,s.program=null,s.wireframe=!1}return{setup:o,reset:b,resetDefaultState:v,dispose:C,releaseStatesOfGeometry:A,releaseStatesOfProgram:E,initAttributes:x,enableAttribute:m,disableUnusedAttributes:y}}function L0(i,t,e){let n;function s(c){n=c}function r(c,h){i.drawArrays(n,c,h),e.update(h,n,1)}function a(c,h,u){u!==0&&(i.drawArraysInstanced(n,c,h,u),e.update(h,n,u))}function o(c,h,u){if(u===0)return;t.get("WEBGL_multi_draw").multiDrawArraysWEBGL(n,c,0,h,0,u);let p=0;for(let g=0;g<u;g++)p+=h[g];e.update(p,n,1)}function l(c,h,u,d){if(u===0)return;let p=t.get("WEBGL_multi_draw");if(p===null)for(let g=0;g<c.length;g++)a(c[g],h[g],d[g]);else{p.multiDrawArraysInstancedWEBGL(n,c,0,h,0,d,0,u);let g=0;for(let x=0;x<u;x++)g+=h[x]*d[x];e.update(g,n,1)}}this.setMode=s,this.render=r,this.renderInstances=a,this.renderMultiDraw=o,this.renderMultiDrawInstances=l}function U0(i,t,e,n){let s;function r(){if(s!==void 0)return s;if(t.has("EXT_texture_filter_anisotropic")===!0){let E=t.get("EXT_texture_filter_anisotropic");s=i.getParameter(E.MAX_TEXTURE_MAX_ANISOTROPY_EXT)}else s=0;return s}function a(E){return!(E!==bn&&n.convert(E)!==i.getParameter(i.IMPLEMENTATION_COLOR_READ_FORMAT))}function o(E){let b=E===or&&(t.has("EXT_color_buffer_half_float")||t.has("EXT_color_buffer_float"));return!(E!==Zn&&n.convert(E)!==i.getParameter(i.IMPLEMENTATION_COLOR_READ_TYPE)&&E!==Pn&&!b)}function l(E){if(E==="highp"){if(i.getShaderPrecisionFormat(i.VERTEX_SHADER,i.HIGH_FLOAT).precision>0&&i.getShaderPrecisionFormat(i.FRAGMENT_SHADER,i.HIGH_FLOAT).precision>0)return"highp";E="mediump"}return E==="mediump"&&i.getShaderPrecisionFormat(i.VERTEX_SHADER,i.MEDIUM_FLOAT).precision>0&&i.getShaderPrecisionFormat(i.FRAGMENT_SHADER,i.MEDIUM_FLOAT).precision>0?"mediump":"lowp"}let c=e.precision!==void 0?e.precision:"highp",h=l(c);h!==c&&(console.warn("THREE.WebGLRenderer:",c,"not supported, using",h,"instead."),c=h);let u=e.logarithmicDepthBuffer===!0,d=e.reverseDepthBuffer===!0&&t.has("EXT_clip_control"),p=i.getParameter(i.MAX_TEXTURE_IMAGE_UNITS),g=i.getParameter(i.MAX_VERTEX_TEXTURE_IMAGE_UNITS),x=i.getParameter(i.MAX_TEXTURE_SIZE),m=i.getParameter(i.MAX_CUBE_MAP_TEXTURE_SIZE),f=i.getParameter(i.MAX_VERTEX_ATTRIBS),y=i.getParameter(i.MAX_VERTEX_UNIFORM_VECTORS),_=i.getParameter(i.MAX_VARYING_VECTORS),M=i.getParameter(i.MAX_FRAGMENT_UNIFORM_VECTORS),C=g>0,A=i.getParameter(i.MAX_SAMPLES);return{isWebGL2:!0,getMaxAnisotropy:r,getMaxPrecision:l,textureFormatReadable:a,textureTypeReadable:o,precision:c,logarithmicDepthBuffer:u,reverseDepthBuffer:d,maxTextures:p,maxVertexTextures:g,maxTextureSize:x,maxCubemapSize:m,maxAttributes:f,maxVertexUniforms:y,maxVaryings:_,maxFragmentUniforms:M,vertexTextures:C,maxSamples:A}}function N0(i){let t=this,e=null,n=0,s=!1,r=!1,a=new Wn,o=new Yt,l={value:null,needsUpdate:!1};this.uniform=l,this.numPlanes=0,this.numIntersection=0,this.init=function(u,d){let p=u.length!==0||d||n!==0||s;return s=d,n=u.length,p},this.beginShadows=function(){r=!0,h(null)},this.endShadows=function(){r=!1},this.setGlobalState=function(u,d){e=h(u,d,0)},this.setState=function(u,d,p){let g=u.clippingPlanes,x=u.clipIntersection,m=u.clipShadows,f=i.get(u);if(!s||g===null||g.length===0||r&&!m)r?h(null):c();else{let y=r?0:n,_=y*4,M=f.clippingState||null;l.value=M,M=h(g,d,_,p);for(let C=0;C!==_;++C)M[C]=e[C];f.clippingState=M,this.numIntersection=x?this.numPlanes:0,this.numPlanes+=y}};function c(){l.value!==e&&(l.value=e,l.needsUpdate=n>0),t.numPlanes=n,t.numIntersection=0}function h(u,d,p,g){let x=u!==null?u.length:0,m=null;if(x!==0){if(m=l.value,g!==!0||m===null){let f=p+x*4,y=d.matrixWorldInverse;o.getNormalMatrix(y),(m===null||m.length<f)&&(m=new Float32Array(f));for(let _=0,M=p;_!==x;++_,M+=4)a.copy(u[_]).applyMatrix4(y,o),a.normal.toArray(m,M),m[M+3]=a.constant}l.value=m,l.needsUpdate=!0}return t.numPlanes=x,t.numIntersection=0,m}}function O0(i){let t=new WeakMap;function e(a,o){return o===mo?a.mapping=ps:o===go&&(a.mapping=ms),a}function n(a){if(a&&a.isTexture){let o=a.mapping;if(o===mo||o===go)if(t.has(a)){let l=t.get(a).texture;return e(l,a.mapping)}else{let l=a.image;if(l&&l.height>0){let c=new $o(l.height);return c.fromEquirectangularTexture(i,a),t.set(a,c),a.addEventListener("dispose",s),e(c.texture,a.mapping)}else return null}}return a}function s(a){let o=a.target;o.removeEventListener("dispose",s);let l=t.get(o);l!==void 0&&(t.delete(o),l.dispose())}function r(){t=new WeakMap}return{get:n,dispose:r}}var vs=class extends na{constructor(t=-1,e=1,n=1,s=-1,r=.1,a=2e3){super(),this.isOrthographicCamera=!0,this.type="OrthographicCamera",this.zoom=1,this.view=null,this.left=t,this.right=e,this.top=n,this.bottom=s,this.near=r,this.far=a,this.updateProjectionMatrix()}copy(t,e){return super.copy(t,e),this.left=t.left,this.right=t.right,this.top=t.top,this.bottom=t.bottom,this.near=t.near,this.far=t.far,this.zoom=t.zoom,this.view=t.view===null?null:Object.assign({},t.view),this}setViewOffset(t,e,n,s,r,a){this.view===null&&(this.view={enabled:!0,fullWidth:1,fullHeight:1,offsetX:0,offsetY:0,width:1,height:1}),this.view.enabled=!0,this.view.fullWidth=t,this.view.fullHeight=e,this.view.offsetX=n,this.view.offsetY=s,this.view.width=r,this.view.height=a,this.updateProjectionMatrix()}clearViewOffset(){this.view!==null&&(this.view.enabled=!1),this.updateProjectionMatrix()}updateProjectionMatrix(){let t=(this.right-this.left)/(2*this.zoom),e=(this.top-this.bottom)/(2*this.zoom),n=(this.right+this.left)/2,s=(this.top+this.bottom)/2,r=n-t,a=n+t,o=s+e,l=s-e;if(this.view!==null&&this.view.enabled){let c=(this.right-this.left)/this.view.fullWidth/this.zoom,h=(this.top-this.bottom)/this.view.fullHeight/this.zoom;r+=c*this.view.offsetX,a=r+c*this.view.width,o-=h*this.view.offsetY,l=o-h*this.view.height}this.projectionMatrix.makeOrthographic(r,a,o,l,this.near,this.far,this.coordinateSystem),this.projectionMatrixInverse.copy(this.projectionMatrix).invert()}toJSON(t){let e=super.toJSON(t);return e.object.zoom=this.zoom,e.object.left=this.left,e.object.right=this.right,e.object.top=this.top,e.object.bottom=this.bottom,e.object.near=this.near,e.object.far=this.far,this.view!==null&&(e.object.view=Object.assign({},this.view)),e}},ls=4,Hc=[.125,.215,.35,.446,.526,.582],Fi=20,Ya=new vs,Vc=new Gt,Za=null,$a=0,Ja=0,Qa=!1,Ci=(1+Math.sqrt(5))/2,rs=1/Ci,Gc=[new L(-Ci,rs,0),new L(Ci,rs,0),new L(-rs,0,Ci),new L(rs,0,Ci),new L(0,Ci,-rs),new L(0,Ci,rs),new L(-1,1,-1),new L(1,1,-1),new L(-1,1,1),new L(1,1,1)],Ms=class{constructor(t){this._renderer=t,this._pingPongRenderTarget=null,this._lodMax=0,this._cubeSize=0,this._lodPlanes=[],this._sizeLods=[],this._sigmas=[],this._blurMaterial=null,this._cubemapMaterial=null,this._equirectMaterial=null,this._compileMaterial(this._blurMaterial)}fromScene(t,e=0,n=.1,s=100){Za=this._renderer.getRenderTarget(),$a=this._renderer.getActiveCubeFace(),Ja=this._renderer.getActiveMipmapLevel(),Qa=this._renderer.xr.enabled,this._renderer.xr.enabled=!1,this._setSize(256);let r=this._allocateTargets();return r.depthBuffer=!0,this._sceneToCubeUV(t,n,s,r),e>0&&this._blur(r,0,0,e),this._applyPMREM(r),this._cleanup(r),r}fromEquirectangular(t,e=null){return this._fromTexture(t,e)}fromCubemap(t,e=null){return this._fromTexture(t,e)}compileCubemapShader(){this._cubemapMaterial===null&&(this._cubemapMaterial=Xc(),this._compileMaterial(this._cubemapMaterial))}compileEquirectangularShader(){this._equirectMaterial===null&&(this._equirectMaterial=Kc(),this._compileMaterial(this._equirectMaterial))}dispose(){this._dispose(),this._cubemapMaterial!==null&&this._cubemapMaterial.dispose(),this._equirectMaterial!==null&&this._equirectMaterial.dispose()}_setSize(t){this._lodMax=Math.floor(Math.log2(t)),this._cubeSize=Math.pow(2,this._lodMax)}_dispose(){this._blurMaterial!==null&&this._blurMaterial.dispose(),this._pingPongRenderTarget!==null&&this._pingPongRenderTarget.dispose();for(let t=0;t<this._lodPlanes.length;t++)this._lodPlanes[t].dispose()}_cleanup(t){this._renderer.setRenderTarget(Za,$a,Ja),this._renderer.xr.enabled=Qa,t.scissorTest=!1,Nr(t,0,0,t.width,t.height)}_fromTexture(t,e){t.mapping===ps||t.mapping===ms?this._setSize(t.image.length===0?16:t.image[0].width||t.image[0].image.width):this._setSize(t.image.width/4),Za=this._renderer.getRenderTarget(),$a=this._renderer.getActiveCubeFace(),Ja=this._renderer.getActiveMipmapLevel(),Qa=this._renderer.xr.enabled,this._renderer.xr.enabled=!1;let n=e||this._allocateTargets();return this._textureToCubeUV(t,n),this._applyPMREM(n),this._cleanup(n),n}_allocateTargets(){let t=3*Math.max(this._cubeSize,112),e=4*this._cubeSize,n={magFilter:fn,minFilter:fn,generateMipmaps:!1,type:or,format:bn,colorSpace:Is,depthBuffer:!1},s=Wc(t,e,n);if(this._pingPongRenderTarget===null||this._pingPongRenderTarget.width!==t||this._pingPongRenderTarget.height!==e){this._pingPongRenderTarget!==null&&this._dispose(),this._pingPongRenderTarget=Wc(t,e,n);let{_lodMax:r}=this;({sizeLods:this._sizeLods,lodPlanes:this._lodPlanes,sigmas:this._sigmas}=B0(r)),this._blurMaterial=z0(r,t,e)}return s}_compileMaterial(t){let e=new Ct(this._lodPlanes[0],t);this._renderer.compile(e,Ya)}_sceneToCubeUV(t,e,n,s){let o=new Le(90,1,e,n),l=[1,-1,1,1,1,1],c=[1,1,1,-1,-1,-1],h=this._renderer,u=h.autoClear,d=h.toneMapping;h.getClearColor(Vc),h.toneMapping=di,h.autoClear=!1;let p=new be({name:"PMREM.Background",side:ze,depthWrite:!1,depthTest:!1}),g=new Ct(new on,p),x=!1,m=t.background;m?m.isColor&&(p.color.copy(m),t.background=null,x=!0):(p.color.copy(Vc),x=!0);for(let f=0;f<6;f++){let y=f%3;y===0?(o.up.set(0,l[f],0),o.lookAt(c[f],0,0)):y===1?(o.up.set(0,0,l[f]),o.lookAt(0,c[f],0)):(o.up.set(0,l[f],0),o.lookAt(0,0,c[f]));let _=this._cubeSize;Nr(s,y*_,f>2?_:0,_,_),h.setRenderTarget(s),x&&h.render(g,o),h.render(t,o)}g.geometry.dispose(),g.material.dispose(),h.toneMapping=d,h.autoClear=u,t.background=m}_textureToCubeUV(t,e){let n=this._renderer,s=t.mapping===ps||t.mapping===ms;s?(this._cubemapMaterial===null&&(this._cubemapMaterial=Xc()),this._cubemapMaterial.uniforms.flipEnvMap.value=t.isRenderTargetTexture===!1?-1:1):this._equirectMaterial===null&&(this._equirectMaterial=Kc());let r=s?this._cubemapMaterial:this._equirectMaterial,a=new Ct(this._lodPlanes[0],r),o=r.uniforms;o.envMap.value=t;let l=this._cubeSize;Nr(e,0,0,3*l,2*l),n.setRenderTarget(e),n.render(a,Ya)}_applyPMREM(t){let e=this._renderer,n=e.autoClear;e.autoClear=!1;let s=this._lodPlanes.length;for(let r=1;r<s;r++){let a=Math.sqrt(this._sigmas[r]*this._sigmas[r]-this._sigmas[r-1]*this._sigmas[r-1]),o=Gc[(s-r-1)%Gc.length];this._blur(t,r-1,r,a,o)}e.autoClear=n}_blur(t,e,n,s,r){let a=this._pingPongRenderTarget;this._halfBlur(t,a,e,n,s,"latitudinal",r),this._halfBlur(a,t,n,n,s,"longitudinal",r)}_halfBlur(t,e,n,s,r,a,o){let l=this._renderer,c=this._blurMaterial;a!=="latitudinal"&&a!=="longitudinal"&&console.error("blur direction must be either latitudinal or longitudinal!");let h=3,u=new Ct(this._lodPlanes[s],c),d=c.uniforms,p=this._sizeLods[n]-1,g=isFinite(r)?Math.PI/(2*p):2*Math.PI/(2*Fi-1),x=r/g,m=isFinite(r)?1+Math.floor(h*x):Fi;m>Fi&&console.warn(`sigmaRadians, ${r}, is too large and will clip, as it requested ${m} samples when the maximum is set to ${Fi}`);let f=[],y=0;for(let E=0;E<Fi;++E){let b=E/x,v=Math.exp(-b*b/2);f.push(v),E===0?y+=v:E<m&&(y+=2*v)}for(let E=0;E<f.length;E++)f[E]=f[E]/y;d.envMap.value=t.texture,d.samples.value=m,d.weights.value=f,d.latitudinal.value=a==="latitudinal",o&&(d.poleAxis.value=o);let{_lodMax:_}=this;d.dTheta.value=g,d.mipInt.value=_-n;let M=this._sizeLods[s],C=3*M*(s>_-ls?s-_+ls:0),A=4*(this._cubeSize-M);Nr(e,C,A,3*M,2*M),l.setRenderTarget(e),l.render(u,Ya)}};function B0(i){let t=[],e=[],n=[],s=i,r=i-ls+1+Hc.length;for(let a=0;a<r;a++){let o=Math.pow(2,s);e.push(o);let l=1/o;a>i-ls?l=Hc[a-i+ls-1]:a===0&&(l=0),n.push(l);let c=1/(o-2),h=-c,u=1+c,d=[h,h,u,h,u,u,h,h,u,u,h,u],p=6,g=6,x=3,m=2,f=1,y=new Float32Array(x*g*p),_=new Float32Array(m*g*p),M=new Float32Array(f*g*p);for(let A=0;A<p;A++){let E=A%3*2/3-1,b=A>2?0:-1,v=[E,b,0,E+2/3,b,0,E+2/3,b+1,0,E,b,0,E+2/3,b+1,0,E,b+1,0];y.set(v,x*g*A),_.set(d,m*g*A);let S=[A,A,A,A,A,A];M.set(S,f*g*A)}let C=new Ie;C.setAttribute("position",new ke(y,x)),C.setAttribute("uv",new ke(_,m)),C.setAttribute("faceIndex",new ke(M,f)),t.push(C),s>ls&&s--}return{lodPlanes:t,sizeLods:e,sigmas:n}}function Wc(i,t,e){let n=new $n(i,t,e);return n.texture.mapping=ma,n.texture.name="PMREM.cubeUv",n.scissorTest=!0,n}function Nr(i,t,e,n,s){i.viewport.set(t,e,n,s),i.scissor.set(t,e,n,s)}function z0(i,t,e){let n=new Float32Array(Fi),s=new L(0,1,0);return new mn({name:"SphericalGaussianBlur",defines:{n:Fi,CUBEUV_TEXEL_WIDTH:1/t,CUBEUV_TEXEL_HEIGHT:1/e,CUBEUV_MAX_MIP:`${i}.0`},uniforms:{envMap:{value:null},samples:{value:1},weights:{value:n},latitudinal:{value:!1},dTheta:{value:0},mipInt:{value:0},poleAxis:{value:s}},vertexShader:zl(),fragmentShader:`

			precision mediump float;
			precision mediump int;

			varying vec3 vOutputDirection;

			uniform sampler2D envMap;
			uniform int samples;
			uniform float weights[ n ];
			uniform bool latitudinal;
			uniform float dTheta;
			uniform float mipInt;
			uniform vec3 poleAxis;

			#define ENVMAP_TYPE_CUBE_UV
			#include <cube_uv_reflection_fragment>

			vec3 getSample( float theta, vec3 axis ) {

				float cosTheta = cos( theta );
				// Rodrigues' axis-angle rotation
				vec3 sampleDirection = vOutputDirection * cosTheta
					+ cross( axis, vOutputDirection ) * sin( theta )
					+ axis * dot( axis, vOutputDirection ) * ( 1.0 - cosTheta );

				return bilinearCubeUV( envMap, sampleDirection, mipInt );

			}

			void main() {

				vec3 axis = latitudinal ? poleAxis : cross( poleAxis, vOutputDirection );

				if ( all( equal( axis, vec3( 0.0 ) ) ) ) {

					axis = vec3( vOutputDirection.z, 0.0, - vOutputDirection.x );

				}

				axis = normalize( axis );

				gl_FragColor = vec4( 0.0, 0.0, 0.0, 1.0 );
				gl_FragColor.rgb += weights[ 0 ] * getSample( 0.0, axis );

				for ( int i = 1; i < n; i++ ) {

					if ( i >= samples ) {

						break;

					}

					float theta = dTheta * float( i );
					gl_FragColor.rgb += weights[ i ] * getSample( -1.0 * theta, axis );
					gl_FragColor.rgb += weights[ i ] * getSample( theta, axis );

				}

			}
		`,blending:ui,depthTest:!1,depthWrite:!1})}function Kc(){return new mn({name:"EquirectangularToCubeUV",uniforms:{envMap:{value:null}},vertexShader:zl(),fragmentShader:`

			precision mediump float;
			precision mediump int;

			varying vec3 vOutputDirection;

			uniform sampler2D envMap;

			#include <common>

			void main() {

				vec3 outputDirection = normalize( vOutputDirection );
				vec2 uv = equirectUv( outputDirection );

				gl_FragColor = vec4( texture2D ( envMap, uv ).rgb, 1.0 );

			}
		`,blending:ui,depthTest:!1,depthWrite:!1})}function Xc(){return new mn({name:"CubemapToCubeUV",uniforms:{envMap:{value:null},flipEnvMap:{value:-1}},vertexShader:zl(),fragmentShader:`

			precision mediump float;
			precision mediump int;

			uniform float flipEnvMap;

			varying vec3 vOutputDirection;

			uniform samplerCube envMap;

			void main() {

				gl_FragColor = textureCube( envMap, vec3( flipEnvMap * vOutputDirection.x, vOutputDirection.yz ) );

			}
		`,blending:ui,depthTest:!1,depthWrite:!1})}function zl(){return`

		precision mediump float;
		precision mediump int;

		attribute float faceIndex;

		varying vec3 vOutputDirection;

		// RH coordinate system; PMREM face-indexing convention
		vec3 getDirection( vec2 uv, float face ) {

			uv = 2.0 * uv - 1.0;

			vec3 direction = vec3( uv, 1.0 );

			if ( face == 0.0 ) {

				direction = direction.zyx; // ( 1, v, u ) pos x

			} else if ( face == 1.0 ) {

				direction = direction.xzy;
				direction.xz *= -1.0; // ( -u, 1, -v ) pos y

			} else if ( face == 2.0 ) {

				direction.x *= -1.0; // ( -u, v, 1 ) pos z

			} else if ( face == 3.0 ) {

				direction = direction.zyx;
				direction.xz *= -1.0; // ( -1, v, -u ) neg x

			} else if ( face == 4.0 ) {

				direction = direction.xzy;
				direction.xy *= -1.0; // ( -u, -1, v ) neg y

			} else if ( face == 5.0 ) {

				direction.z *= -1.0; // ( u, v, -1 ) neg z

			}

			return direction;

		}

		void main() {

			vOutputDirection = getDirection( uv, faceIndex );
			gl_Position = vec4( position, 1.0 );

		}
	`}function k0(i){let t=new WeakMap,e=null;function n(o){if(o&&o.isTexture){let l=o.mapping,c=l===mo||l===go,h=l===ps||l===ms;if(c||h){let u=t.get(o),d=u!==void 0?u.texture.pmremVersion:0;if(o.isRenderTargetTexture&&o.pmremVersion!==d)return e===null&&(e=new Ms(i)),u=c?e.fromEquirectangular(o,u):e.fromCubemap(o,u),u.texture.pmremVersion=o.pmremVersion,t.set(o,u),u.texture;if(u!==void 0)return u.texture;{let p=o.image;return c&&p&&p.height>0||h&&p&&s(p)?(e===null&&(e=new Ms(i)),u=c?e.fromEquirectangular(o):e.fromCubemap(o),u.texture.pmremVersion=o.pmremVersion,t.set(o,u),o.addEventListener("dispose",r),u.texture):null}}}return o}function s(o){let l=0,c=6;for(let h=0;h<c;h++)o[h]!==void 0&&l++;return l===c}function r(o){let l=o.target;l.removeEventListener("dispose",r);let c=t.get(l);c!==void 0&&(t.delete(l),c.dispose())}function a(){t=new WeakMap,e!==null&&(e.dispose(),e=null)}return{get:n,dispose:a}}function H0(i){let t={};function e(n){if(t[n]!==void 0)return t[n];let s;switch(n){case"WEBGL_depth_texture":s=i.getExtension("WEBGL_depth_texture")||i.getExtension("MOZ_WEBGL_depth_texture")||i.getExtension("WEBKIT_WEBGL_depth_texture");break;case"EXT_texture_filter_anisotropic":s=i.getExtension("EXT_texture_filter_anisotropic")||i.getExtension("MOZ_EXT_texture_filter_anisotropic")||i.getExtension("WEBKIT_EXT_texture_filter_anisotropic");break;case"WEBGL_compressed_texture_s3tc":s=i.getExtension("WEBGL_compressed_texture_s3tc")||i.getExtension("MOZ_WEBGL_compressed_texture_s3tc")||i.getExtension("WEBKIT_WEBGL_compressed_texture_s3tc");break;case"WEBGL_compressed_texture_pvrtc":s=i.getExtension("WEBGL_compressed_texture_pvrtc")||i.getExtension("WEBKIT_WEBGL_compressed_texture_pvrtc");break;default:s=i.getExtension(n)}return t[n]=s,s}return{has:function(n){return e(n)!==null},init:function(){e("EXT_color_buffer_float"),e("WEBGL_clip_cull_distance"),e("OES_texture_float_linear"),e("EXT_color_buffer_half_float"),e("WEBGL_multisampled_render_to_texture"),e("WEBGL_render_shared_exponent")},get:function(n){let s=e(n);return s===null&&Gs("THREE.WebGLRenderer: "+n+" extension not supported."),s}}}function V0(i,t,e,n){let s={},r=new WeakMap;function a(u){let d=u.target;d.index!==null&&t.remove(d.index);for(let g in d.attributes)t.remove(d.attributes[g]);for(let g in d.morphAttributes){let x=d.morphAttributes[g];for(let m=0,f=x.length;m<f;m++)t.remove(x[m])}d.removeEventListener("dispose",a),delete s[d.id];let p=r.get(d);p&&(t.remove(p),r.delete(d)),n.releaseStatesOfGeometry(d),d.isInstancedBufferGeometry===!0&&delete d._maxInstanceCount,e.memory.geometries--}function o(u,d){return s[d.id]===!0||(d.addEventListener("dispose",a),s[d.id]=!0,e.memory.geometries++),d}function l(u){let d=u.attributes;for(let g in d)t.update(d[g],i.ARRAY_BUFFER);let p=u.morphAttributes;for(let g in p){let x=p[g];for(let m=0,f=x.length;m<f;m++)t.update(x[m],i.ARRAY_BUFFER)}}function c(u){let d=[],p=u.index,g=u.attributes.position,x=0;if(p!==null){let y=p.array;x=p.version;for(let _=0,M=y.length;_<M;_+=3){let C=y[_+0],A=y[_+1],E=y[_+2];d.push(C,A,A,E,E,C)}}else if(g!==void 0){let y=g.array;x=g.version;for(let _=0,M=y.length/3-1;_<M;_+=3){let C=_+0,A=_+1,E=_+2;d.push(C,A,A,E,E,C)}}else return;let m=new(Uh(d)?ea:ta)(d,1);m.version=x;let f=r.get(u);f&&t.remove(f),r.set(u,m)}function h(u){let d=r.get(u);if(d){let p=u.index;p!==null&&d.version<p.version&&c(u)}else c(u);return r.get(u)}return{get:o,update:l,getWireframeAttribute:h}}function G0(i,t,e){let n;function s(d){n=d}let r,a;function o(d){r=d.type,a=d.bytesPerElement}function l(d,p){i.drawElements(n,p,r,d*a),e.update(p,n,1)}function c(d,p,g){g!==0&&(i.drawElementsInstanced(n,p,r,d*a,g),e.update(p,n,g))}function h(d,p,g){if(g===0)return;t.get("WEBGL_multi_draw").multiDrawElementsWEBGL(n,p,0,r,d,0,g);let m=0;for(let f=0;f<g;f++)m+=p[f];e.update(m,n,1)}function u(d,p,g,x){if(g===0)return;let m=t.get("WEBGL_multi_draw");if(m===null)for(let f=0;f<d.length;f++)c(d[f]/a,p[f],x[f]);else{m.multiDrawElementsInstancedWEBGL(n,p,0,r,d,0,x,0,g);let f=0;for(let y=0;y<g;y++)f+=p[y]*x[y];e.update(f,n,1)}}this.setMode=s,this.setIndex=o,this.render=l,this.renderInstances=c,this.renderMultiDraw=h,this.renderMultiDrawInstances=u}function W0(i){let t={geometries:0,textures:0},e={frame:0,calls:0,triangles:0,points:0,lines:0};function n(r,a,o){switch(e.calls++,a){case i.TRIANGLES:e.triangles+=o*(r/3);break;case i.LINES:e.lines+=o*(r/2);break;case i.LINE_STRIP:e.lines+=o*(r-1);break;case i.LINE_LOOP:e.lines+=o*r;break;case i.POINTS:e.points+=o*r;break;default:console.error("THREE.WebGLInfo: Unknown draw mode:",a);break}}function s(){e.calls=0,e.triangles=0,e.points=0,e.lines=0}return{memory:t,render:e,programs:null,autoReset:!0,reset:s,update:n}}function K0(i,t,e){let n=new WeakMap,s=new ce;function r(a,o,l){let c=a.morphTargetInfluences,h=o.morphAttributes.position||o.morphAttributes.normal||o.morphAttributes.color,u=h!==void 0?h.length:0,d=n.get(o);if(d===void 0||d.count!==u){let v=function(){E.dispose(),n.delete(o),o.removeEventListener("dispose",v)};d!==void 0&&d.texture.dispose();let p=o.morphAttributes.position!==void 0,g=o.morphAttributes.normal!==void 0,x=o.morphAttributes.color!==void 0,m=o.morphAttributes.position||[],f=o.morphAttributes.normal||[],y=o.morphAttributes.color||[],_=0;p===!0&&(_=1),g===!0&&(_=2),x===!0&&(_=3);let M=o.attributes.position.count*_,C=1;M>t.maxTextureSize&&(C=Math.ceil(M/t.maxTextureSize),M=t.maxTextureSize);let A=new Float32Array(M*C*4*u),E=new Qr(A,M,C,u);E.type=Pn,E.needsUpdate=!0;let b=_*4;for(let S=0;S<u;S++){let w=m[S],P=f[S],F=y[S],U=M*C*4*S;for(let H=0;H<w.count;H++){let N=H*b;p===!0&&(s.fromBufferAttribute(w,H),A[U+N+0]=s.x,A[U+N+1]=s.y,A[U+N+2]=s.z,A[U+N+3]=0),g===!0&&(s.fromBufferAttribute(P,H),A[U+N+4]=s.x,A[U+N+5]=s.y,A[U+N+6]=s.z,A[U+N+7]=0),x===!0&&(s.fromBufferAttribute(F,H),A[U+N+8]=s.x,A[U+N+9]=s.y,A[U+N+10]=s.z,A[U+N+11]=F.itemSize===4?s.w:1)}}d={count:u,texture:E,size:new at(M,C)},n.set(o,d),o.addEventListener("dispose",v)}if(a.isInstancedMesh===!0&&a.morphTexture!==null)l.getUniforms().setValue(i,"morphTexture",a.morphTexture,e);else{let p=0;for(let x=0;x<c.length;x++)p+=c[x];let g=o.morphTargetsRelative?1:1-p;l.getUniforms().setValue(i,"morphTargetBaseInfluence",g),l.getUniforms().setValue(i,"morphTargetInfluences",c)}l.getUniforms().setValue(i,"morphTargetsTexture",d.texture,e),l.getUniforms().setValue(i,"morphTargetsTextureSize",d.size)}return{update:r}}function X0(i,t,e,n){let s=new WeakMap;function r(l){let c=n.render.frame,h=l.geometry,u=t.get(l,h);if(s.get(u)!==c&&(t.update(u),s.set(u,c)),l.isInstancedMesh&&(l.hasEventListener("dispose",o)===!1&&l.addEventListener("dispose",o),s.get(l)!==c&&(e.update(l.instanceMatrix,i.ARRAY_BUFFER),l.instanceColor!==null&&e.update(l.instanceColor,i.ARRAY_BUFFER),s.set(l,c))),l.isSkinnedMesh){let d=l.skeleton;s.get(d)!==c&&(d.update(),s.set(d,c))}return u}function a(){s=new WeakMap}function o(l){let c=l.target;c.removeEventListener("dispose",o),e.remove(c.instanceMatrix),c.instanceColor!==null&&e.remove(c.instanceColor)}return{update:r,dispose:a}}var sa=class extends $e{constructor(t,e,n,s,r,a,o,l,c,h=hs){if(h!==hs&&h!==_s)throw new Error("DepthTexture format must be either THREE.DepthFormat or THREE.DepthStencilFormat");n===void 0&&h===hs&&(n=Di),n===void 0&&h===_s&&(n=gs),super(null,s,r,a,o,l,h,n,c),this.isDepthTexture=!0,this.image={width:t,height:e},this.magFilter=o!==void 0?o:an,this.minFilter=l!==void 0?l:an,this.flipY=!1,this.generateMipmaps=!1,this.compareFunction=null}copy(t){return super.copy(t),this.compareFunction=t.compareFunction,this}toJSON(t){let e=super.toJSON(t);return this.compareFunction!==null&&(e.compareFunction=this.compareFunction),e}},zh=new $e,qc=new sa(1,1),kh=new Qr,Hh=new qo,Vh=new ia,Yc=[],Zc=[],$c=new Float32Array(16),Jc=new Float32Array(9),Qc=new Float32Array(4);function Ps(i,t,e){let n=i[0];if(n<=0||n>0)return i;let s=t*e,r=Yc[s];if(r===void 0&&(r=new Float32Array(s),Yc[s]=r),t!==0){n.toArray(r,0);for(let a=1,o=0;a!==t;++a)o+=e,i[a].toArray(r,o)}return r}function Pe(i,t){if(i.length!==t.length)return!1;for(let e=0,n=i.length;e<n;e++)if(i[e]!==t[e])return!1;return!0}function Fe(i,t){for(let e=0,n=t.length;e<n;e++)i[e]=t[e]}function _a(i,t){let e=Zc[t];e===void 0&&(e=new Int32Array(t),Zc[t]=e);for(let n=0;n!==t;++n)e[n]=i.allocateTextureUnit();return e}function q0(i,t){let e=this.cache;e[0]!==t&&(i.uniform1f(this.addr,t),e[0]=t)}function Y0(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y)&&(i.uniform2f(this.addr,t.x,t.y),e[0]=t.x,e[1]=t.y);else{if(Pe(e,t))return;i.uniform2fv(this.addr,t),Fe(e,t)}}function Z0(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y||e[2]!==t.z)&&(i.uniform3f(this.addr,t.x,t.y,t.z),e[0]=t.x,e[1]=t.y,e[2]=t.z);else if(t.r!==void 0)(e[0]!==t.r||e[1]!==t.g||e[2]!==t.b)&&(i.uniform3f(this.addr,t.r,t.g,t.b),e[0]=t.r,e[1]=t.g,e[2]=t.b);else{if(Pe(e,t))return;i.uniform3fv(this.addr,t),Fe(e,t)}}function $0(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y||e[2]!==t.z||e[3]!==t.w)&&(i.uniform4f(this.addr,t.x,t.y,t.z,t.w),e[0]=t.x,e[1]=t.y,e[2]=t.z,e[3]=t.w);else{if(Pe(e,t))return;i.uniform4fv(this.addr,t),Fe(e,t)}}function J0(i,t){let e=this.cache,n=t.elements;if(n===void 0){if(Pe(e,t))return;i.uniformMatrix2fv(this.addr,!1,t),Fe(e,t)}else{if(Pe(e,n))return;Qc.set(n),i.uniformMatrix2fv(this.addr,!1,Qc),Fe(e,n)}}function Q0(i,t){let e=this.cache,n=t.elements;if(n===void 0){if(Pe(e,t))return;i.uniformMatrix3fv(this.addr,!1,t),Fe(e,t)}else{if(Pe(e,n))return;Jc.set(n),i.uniformMatrix3fv(this.addr,!1,Jc),Fe(e,n)}}function j0(i,t){let e=this.cache,n=t.elements;if(n===void 0){if(Pe(e,t))return;i.uniformMatrix4fv(this.addr,!1,t),Fe(e,t)}else{if(Pe(e,n))return;$c.set(n),i.uniformMatrix4fv(this.addr,!1,$c),Fe(e,n)}}function tm(i,t){let e=this.cache;e[0]!==t&&(i.uniform1i(this.addr,t),e[0]=t)}function em(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y)&&(i.uniform2i(this.addr,t.x,t.y),e[0]=t.x,e[1]=t.y);else{if(Pe(e,t))return;i.uniform2iv(this.addr,t),Fe(e,t)}}function nm(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y||e[2]!==t.z)&&(i.uniform3i(this.addr,t.x,t.y,t.z),e[0]=t.x,e[1]=t.y,e[2]=t.z);else{if(Pe(e,t))return;i.uniform3iv(this.addr,t),Fe(e,t)}}function im(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y||e[2]!==t.z||e[3]!==t.w)&&(i.uniform4i(this.addr,t.x,t.y,t.z,t.w),e[0]=t.x,e[1]=t.y,e[2]=t.z,e[3]=t.w);else{if(Pe(e,t))return;i.uniform4iv(this.addr,t),Fe(e,t)}}function sm(i,t){let e=this.cache;e[0]!==t&&(i.uniform1ui(this.addr,t),e[0]=t)}function rm(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y)&&(i.uniform2ui(this.addr,t.x,t.y),e[0]=t.x,e[1]=t.y);else{if(Pe(e,t))return;i.uniform2uiv(this.addr,t),Fe(e,t)}}function am(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y||e[2]!==t.z)&&(i.uniform3ui(this.addr,t.x,t.y,t.z),e[0]=t.x,e[1]=t.y,e[2]=t.z);else{if(Pe(e,t))return;i.uniform3uiv(this.addr,t),Fe(e,t)}}function om(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y||e[2]!==t.z||e[3]!==t.w)&&(i.uniform4ui(this.addr,t.x,t.y,t.z,t.w),e[0]=t.x,e[1]=t.y,e[2]=t.z,e[3]=t.w);else{if(Pe(e,t))return;i.uniform4uiv(this.addr,t),Fe(e,t)}}function lm(i,t,e){let n=this.cache,s=e.allocateTextureUnit();n[0]!==s&&(i.uniform1i(this.addr,s),n[0]=s);let r;this.type===i.SAMPLER_2D_SHADOW?(qc.compareFunction=Dh,r=qc):r=zh,e.setTexture2D(t||r,s)}function cm(i,t,e){let n=this.cache,s=e.allocateTextureUnit();n[0]!==s&&(i.uniform1i(this.addr,s),n[0]=s),e.setTexture3D(t||Hh,s)}function hm(i,t,e){let n=this.cache,s=e.allocateTextureUnit();n[0]!==s&&(i.uniform1i(this.addr,s),n[0]=s),e.setTextureCube(t||Vh,s)}function um(i,t,e){let n=this.cache,s=e.allocateTextureUnit();n[0]!==s&&(i.uniform1i(this.addr,s),n[0]=s),e.setTexture2DArray(t||kh,s)}function dm(i){switch(i){case 5126:return q0;case 35664:return Y0;case 35665:return Z0;case 35666:return $0;case 35674:return J0;case 35675:return Q0;case 35676:return j0;case 5124:case 35670:return tm;case 35667:case 35671:return em;case 35668:case 35672:return nm;case 35669:case 35673:return im;case 5125:return sm;case 36294:return rm;case 36295:return am;case 36296:return om;case 35678:case 36198:case 36298:case 36306:case 35682:return lm;case 35679:case 36299:case 36307:return cm;case 35680:case 36300:case 36308:case 36293:return hm;case 36289:case 36303:case 36311:case 36292:return um}}function fm(i,t){i.uniform1fv(this.addr,t)}function pm(i,t){let e=Ps(t,this.size,2);i.uniform2fv(this.addr,e)}function mm(i,t){let e=Ps(t,this.size,3);i.uniform3fv(this.addr,e)}function gm(i,t){let e=Ps(t,this.size,4);i.uniform4fv(this.addr,e)}function _m(i,t){let e=Ps(t,this.size,4);i.uniformMatrix2fv(this.addr,!1,e)}function xm(i,t){let e=Ps(t,this.size,9);i.uniformMatrix3fv(this.addr,!1,e)}function ym(i,t){let e=Ps(t,this.size,16);i.uniformMatrix4fv(this.addr,!1,e)}function vm(i,t){i.uniform1iv(this.addr,t)}function Mm(i,t){i.uniform2iv(this.addr,t)}function Sm(i,t){i.uniform3iv(this.addr,t)}function Em(i,t){i.uniform4iv(this.addr,t)}function bm(i,t){i.uniform1uiv(this.addr,t)}function wm(i,t){i.uniform2uiv(this.addr,t)}function Tm(i,t){i.uniform3uiv(this.addr,t)}function Am(i,t){i.uniform4uiv(this.addr,t)}function Rm(i,t,e){let n=this.cache,s=t.length,r=_a(e,s);Pe(n,r)||(i.uniform1iv(this.addr,r),Fe(n,r));for(let a=0;a!==s;++a)e.setTexture2D(t[a]||zh,r[a])}function Cm(i,t,e){let n=this.cache,s=t.length,r=_a(e,s);Pe(n,r)||(i.uniform1iv(this.addr,r),Fe(n,r));for(let a=0;a!==s;++a)e.setTexture3D(t[a]||Hh,r[a])}function Im(i,t,e){let n=this.cache,s=t.length,r=_a(e,s);Pe(n,r)||(i.uniform1iv(this.addr,r),Fe(n,r));for(let a=0;a!==s;++a)e.setTextureCube(t[a]||Vh,r[a])}function Pm(i,t,e){let n=this.cache,s=t.length,r=_a(e,s);Pe(n,r)||(i.uniform1iv(this.addr,r),Fe(n,r));for(let a=0;a!==s;++a)e.setTexture2DArray(t[a]||kh,r[a])}function Fm(i){switch(i){case 5126:return fm;case 35664:return pm;case 35665:return mm;case 35666:return gm;case 35674:return _m;case 35675:return xm;case 35676:return ym;case 5124:case 35670:return vm;case 35667:case 35671:return Mm;case 35668:case 35672:return Sm;case 35669:case 35673:return Em;case 5125:return bm;case 36294:return wm;case 36295:return Tm;case 36296:return Am;case 35678:case 36198:case 36298:case 36306:case 35682:return Rm;case 35679:case 36299:case 36307:return Cm;case 35680:case 36300:case 36308:case 36293:return Im;case 36289:case 36303:case 36311:case 36292:return Pm}}var Jo=class{constructor(t,e,n){this.id=t,this.addr=n,this.cache=[],this.type=e.type,this.setValue=dm(e.type)}},Qo=class{constructor(t,e,n){this.id=t,this.addr=n,this.cache=[],this.type=e.type,this.size=e.size,this.setValue=Fm(e.type)}},jo=class{constructor(t){this.id=t,this.seq=[],this.map={}}setValue(t,e,n){let s=this.seq;for(let r=0,a=s.length;r!==a;++r){let o=s[r];o.setValue(t,e[o.id],n)}}},ja=/(\w+)(\])?(\[|\.)?/g;function jc(i,t){i.seq.push(t),i.map[t.id]=t}function Dm(i,t,e){let n=i.name,s=n.length;for(ja.lastIndex=0;;){let r=ja.exec(n),a=ja.lastIndex,o=r[1],l=r[2]==="]",c=r[3];if(l&&(o=o|0),c===void 0||c==="["&&a+2===s){jc(e,c===void 0?new Jo(o,i,t):new Qo(o,i,t));break}else{let u=e.map[o];u===void 0&&(u=new jo(o),jc(e,u)),e=u}}}var ds=class{constructor(t,e){this.seq=[],this.map={};let n=t.getProgramParameter(e,t.ACTIVE_UNIFORMS);for(let s=0;s<n;++s){let r=t.getActiveUniform(e,s),a=t.getUniformLocation(e,r.name);Dm(r,a,this)}}setValue(t,e,n,s){let r=this.map[e];r!==void 0&&r.setValue(t,n,s)}setOptional(t,e,n){let s=e[n];s!==void 0&&this.setValue(t,n,s)}static upload(t,e,n,s){for(let r=0,a=e.length;r!==a;++r){let o=e[r],l=n[o.id];l.needsUpdate!==!1&&o.setValue(t,l.value,s)}}static seqWithValue(t,e){let n=[];for(let s=0,r=t.length;s!==r;++s){let a=t[s];a.id in e&&n.push(a)}return n}};function th(i,t,e){let n=i.createShader(t);return i.shaderSource(n,e),i.compileShader(n),n}var Lm=37297,Um=0;function Nm(i,t){let e=i.split(`
`),n=[],s=Math.max(t-6,0),r=Math.min(t+6,e.length);for(let a=s;a<r;a++){let o=a+1;n.push(`${o===t?">":" "} ${o}: ${e[a]}`)}return n.join(`
`)}var eh=new Yt;function Om(i){ne._getMatrix(eh,ne.workingColorSpace,i);let t=`mat3( ${eh.elements.map(e=>e.toFixed(4))} )`;switch(ne.getTransfer(i)){case ga:return[t,"LinearTransferOETF"];case le:return[t,"sRGBTransferOETF"];default:return console.warn("THREE.WebGLProgram: Unsupported color space: ",i),[t,"LinearTransferOETF"]}}function nh(i,t,e){let n=i.getShaderParameter(t,i.COMPILE_STATUS),s=i.getShaderInfoLog(t).trim();if(n&&s==="")return"";let r=/ERROR: 0:(\d+)/.exec(s);if(r){let a=parseInt(r[1]);return e.toUpperCase()+`

`+s+`

`+Nm(i.getShaderSource(t),a)}else return s}function Bm(i,t){let e=Om(t);return[`vec4 ${i}( vec4 value ) {`,`	return ${e[1]}( vec4( value.rgb * ${e[0]}, value.a ) );`,"}"].join(`
`)}function zm(i,t){let e;switch(t){case Zu:e="Linear";break;case $u:e="Reinhard";break;case Ju:e="Cineon";break;case Il:e="ACESFilmic";break;case ju:e="AgX";break;case td:e="Neutral";break;case Qu:e="Custom";break;default:console.warn("THREE.WebGLProgram: Unsupported toneMapping:",t),e="Linear"}return"vec3 "+i+"( vec3 color ) { return "+e+"ToneMapping( color ); }"}var Or=new L;function km(){ne.getLuminanceCoefficients(Or);let i=Or.x.toFixed(4),t=Or.y.toFixed(4),e=Or.z.toFixed(4);return["float luminance( const in vec3 rgb ) {",`	const vec3 weights = vec3( ${i}, ${t}, ${e} );`,"	return dot( weights, rgb );","}"].join(`
`)}function Hm(i){return[i.extensionClipCullDistance?"#extension GL_ANGLE_clip_cull_distance : require":"",i.extensionMultiDraw?"#extension GL_ANGLE_multi_draw : require":""].filter(Ws).join(`
`)}function Vm(i){let t=[];for(let e in i){let n=i[e];n!==!1&&t.push("#define "+e+" "+n)}return t.join(`
`)}function Gm(i,t){let e={},n=i.getProgramParameter(t,i.ACTIVE_ATTRIBUTES);for(let s=0;s<n;s++){let r=i.getActiveAttrib(t,s),a=r.name,o=1;r.type===i.FLOAT_MAT2&&(o=2),r.type===i.FLOAT_MAT3&&(o=3),r.type===i.FLOAT_MAT4&&(o=4),e[a]={type:r.type,location:i.getAttribLocation(t,a),locationSize:o}}return e}function Ws(i){return i!==""}function ih(i,t){let e=t.numSpotLightShadows+t.numSpotLightMaps-t.numSpotLightShadowsWithMaps;return i.replace(/NUM_DIR_LIGHTS/g,t.numDirLights).replace(/NUM_SPOT_LIGHTS/g,t.numSpotLights).replace(/NUM_SPOT_LIGHT_MAPS/g,t.numSpotLightMaps).replace(/NUM_SPOT_LIGHT_COORDS/g,e).replace(/NUM_RECT_AREA_LIGHTS/g,t.numRectAreaLights).replace(/NUM_POINT_LIGHTS/g,t.numPointLights).replace(/NUM_HEMI_LIGHTS/g,t.numHemiLights).replace(/NUM_DIR_LIGHT_SHADOWS/g,t.numDirLightShadows).replace(/NUM_SPOT_LIGHT_SHADOWS_WITH_MAPS/g,t.numSpotLightShadowsWithMaps).replace(/NUM_SPOT_LIGHT_SHADOWS/g,t.numSpotLightShadows).replace(/NUM_POINT_LIGHT_SHADOWS/g,t.numPointLightShadows)}function sh(i,t){return i.replace(/NUM_CLIPPING_PLANES/g,t.numClippingPlanes).replace(/UNION_CLIPPING_PLANES/g,t.numClippingPlanes-t.numClipIntersection)}var Wm=/^[ \t]*#include +<([\w\d./]+)>/gm;function tl(i){return i.replace(Wm,Xm)}var Km=new Map;function Xm(i,t){let e=$t[t];if(e===void 0){let n=Km.get(t);if(n!==void 0)e=$t[n],console.warn('THREE.WebGLRenderer: Shader chunk "%s" has been deprecated. Use "%s" instead.',t,n);else throw new Error("Can not resolve #include <"+t+">")}return tl(e)}var qm=/#pragma unroll_loop_start\s+for\s*\(\s*int\s+i\s*=\s*(\d+)\s*;\s*i\s*<\s*(\d+)\s*;\s*i\s*\+\+\s*\)\s*{([\s\S]+?)}\s+#pragma unroll_loop_end/g;function rh(i){return i.replace(qm,Ym)}function Ym(i,t,e,n){let s="";for(let r=parseInt(t);r<parseInt(e);r++)s+=n.replace(/\[\s*i\s*\]/g,"[ "+r+" ]").replace(/UNROLLED_LOOP_INDEX/g,r);return s}function ah(i){let t=`precision ${i.precision} float;
	precision ${i.precision} int;
	precision ${i.precision} sampler2D;
	precision ${i.precision} samplerCube;
	precision ${i.precision} sampler3D;
	precision ${i.precision} sampler2DArray;
	precision ${i.precision} sampler2DShadow;
	precision ${i.precision} samplerCubeShadow;
	precision ${i.precision} sampler2DArrayShadow;
	precision ${i.precision} isampler2D;
	precision ${i.precision} isampler3D;
	precision ${i.precision} isamplerCube;
	precision ${i.precision} isampler2DArray;
	precision ${i.precision} usampler2D;
	precision ${i.precision} usampler3D;
	precision ${i.precision} usamplerCube;
	precision ${i.precision} usampler2DArray;
	`;return i.precision==="highp"?t+=`
#define HIGH_PRECISION`:i.precision==="mediump"?t+=`
#define MEDIUM_PRECISION`:i.precision==="lowp"&&(t+=`
#define LOW_PRECISION`),t}function Zm(i){let t="SHADOWMAP_TYPE_BASIC";return i.shadowMapType===vh?t="SHADOWMAP_TYPE_PCF":i.shadowMapType===Ru?t="SHADOWMAP_TYPE_PCF_SOFT":i.shadowMapType===Gn&&(t="SHADOWMAP_TYPE_VSM"),t}function $m(i){let t="ENVMAP_TYPE_CUBE";if(i.envMap)switch(i.envMapMode){case ps:case ms:t="ENVMAP_TYPE_CUBE";break;case ma:t="ENVMAP_TYPE_CUBE_UV";break}return t}function Jm(i){let t="ENVMAP_MODE_REFLECTION";if(i.envMap)switch(i.envMapMode){case ms:t="ENVMAP_MODE_REFRACTION";break}return t}function Qm(i){let t="ENVMAP_BLENDING_NONE";if(i.envMap)switch(i.combine){case Mh:t="ENVMAP_BLENDING_MULTIPLY";break;case qu:t="ENVMAP_BLENDING_MIX";break;case Yu:t="ENVMAP_BLENDING_ADD";break}return t}function jm(i){let t=i.envMapCubeUVHeight;if(t===null)return null;let e=Math.log2(t)-2,n=1/t;return{texelWidth:1/(3*Math.max(Math.pow(2,e),7*16)),texelHeight:n,maxMip:e}}function tg(i,t,e,n){let s=i.getContext(),r=e.defines,a=e.vertexShader,o=e.fragmentShader,l=Zm(e),c=$m(e),h=Jm(e),u=Qm(e),d=jm(e),p=Hm(e),g=Vm(r),x=s.createProgram(),m,f,y=e.glslVersion?"#version "+e.glslVersion+`
`:"";e.isRawShaderMaterial?(m=["#define SHADER_TYPE "+e.shaderType,"#define SHADER_NAME "+e.shaderName,g].filter(Ws).join(`
`),m.length>0&&(m+=`
`),f=["#define SHADER_TYPE "+e.shaderType,"#define SHADER_NAME "+e.shaderName,g].filter(Ws).join(`
`),f.length>0&&(f+=`
`)):(m=[ah(e),"#define SHADER_TYPE "+e.shaderType,"#define SHADER_NAME "+e.shaderName,g,e.extensionClipCullDistance?"#define USE_CLIP_DISTANCE":"",e.batching?"#define USE_BATCHING":"",e.batchingColor?"#define USE_BATCHING_COLOR":"",e.instancing?"#define USE_INSTANCING":"",e.instancingColor?"#define USE_INSTANCING_COLOR":"",e.instancingMorph?"#define USE_INSTANCING_MORPH":"",e.useFog&&e.fog?"#define USE_FOG":"",e.useFog&&e.fogExp2?"#define FOG_EXP2":"",e.map?"#define USE_MAP":"",e.envMap?"#define USE_ENVMAP":"",e.envMap?"#define "+h:"",e.lightMap?"#define USE_LIGHTMAP":"",e.aoMap?"#define USE_AOMAP":"",e.bumpMap?"#define USE_BUMPMAP":"",e.normalMap?"#define USE_NORMALMAP":"",e.normalMapObjectSpace?"#define USE_NORMALMAP_OBJECTSPACE":"",e.normalMapTangentSpace?"#define USE_NORMALMAP_TANGENTSPACE":"",e.displacementMap?"#define USE_DISPLACEMENTMAP":"",e.emissiveMap?"#define USE_EMISSIVEMAP":"",e.anisotropy?"#define USE_ANISOTROPY":"",e.anisotropyMap?"#define USE_ANISOTROPYMAP":"",e.clearcoatMap?"#define USE_CLEARCOATMAP":"",e.clearcoatRoughnessMap?"#define USE_CLEARCOAT_ROUGHNESSMAP":"",e.clearcoatNormalMap?"#define USE_CLEARCOAT_NORMALMAP":"",e.iridescenceMap?"#define USE_IRIDESCENCEMAP":"",e.iridescenceThicknessMap?"#define USE_IRIDESCENCE_THICKNESSMAP":"",e.specularMap?"#define USE_SPECULARMAP":"",e.specularColorMap?"#define USE_SPECULAR_COLORMAP":"",e.specularIntensityMap?"#define USE_SPECULAR_INTENSITYMAP":"",e.roughnessMap?"#define USE_ROUGHNESSMAP":"",e.metalnessMap?"#define USE_METALNESSMAP":"",e.alphaMap?"#define USE_ALPHAMAP":"",e.alphaHash?"#define USE_ALPHAHASH":"",e.transmission?"#define USE_TRANSMISSION":"",e.transmissionMap?"#define USE_TRANSMISSIONMAP":"",e.thicknessMap?"#define USE_THICKNESSMAP":"",e.sheenColorMap?"#define USE_SHEEN_COLORMAP":"",e.sheenRoughnessMap?"#define USE_SHEEN_ROUGHNESSMAP":"",e.mapUv?"#define MAP_UV "+e.mapUv:"",e.alphaMapUv?"#define ALPHAMAP_UV "+e.alphaMapUv:"",e.lightMapUv?"#define LIGHTMAP_UV "+e.lightMapUv:"",e.aoMapUv?"#define AOMAP_UV "+e.aoMapUv:"",e.emissiveMapUv?"#define EMISSIVEMAP_UV "+e.emissiveMapUv:"",e.bumpMapUv?"#define BUMPMAP_UV "+e.bumpMapUv:"",e.normalMapUv?"#define NORMALMAP_UV "+e.normalMapUv:"",e.displacementMapUv?"#define DISPLACEMENTMAP_UV "+e.displacementMapUv:"",e.metalnessMapUv?"#define METALNESSMAP_UV "+e.metalnessMapUv:"",e.roughnessMapUv?"#define ROUGHNESSMAP_UV "+e.roughnessMapUv:"",e.anisotropyMapUv?"#define ANISOTROPYMAP_UV "+e.anisotropyMapUv:"",e.clearcoatMapUv?"#define CLEARCOATMAP_UV "+e.clearcoatMapUv:"",e.clearcoatNormalMapUv?"#define CLEARCOAT_NORMALMAP_UV "+e.clearcoatNormalMapUv:"",e.clearcoatRoughnessMapUv?"#define CLEARCOAT_ROUGHNESSMAP_UV "+e.clearcoatRoughnessMapUv:"",e.iridescenceMapUv?"#define IRIDESCENCEMAP_UV "+e.iridescenceMapUv:"",e.iridescenceThicknessMapUv?"#define IRIDESCENCE_THICKNESSMAP_UV "+e.iridescenceThicknessMapUv:"",e.sheenColorMapUv?"#define SHEEN_COLORMAP_UV "+e.sheenColorMapUv:"",e.sheenRoughnessMapUv?"#define SHEEN_ROUGHNESSMAP_UV "+e.sheenRoughnessMapUv:"",e.specularMapUv?"#define SPECULARMAP_UV "+e.specularMapUv:"",e.specularColorMapUv?"#define SPECULAR_COLORMAP_UV "+e.specularColorMapUv:"",e.specularIntensityMapUv?"#define SPECULAR_INTENSITYMAP_UV "+e.specularIntensityMapUv:"",e.transmissionMapUv?"#define TRANSMISSIONMAP_UV "+e.transmissionMapUv:"",e.thicknessMapUv?"#define THICKNESSMAP_UV "+e.thicknessMapUv:"",e.vertexTangents&&e.flatShading===!1?"#define USE_TANGENT":"",e.vertexColors?"#define USE_COLOR":"",e.vertexAlphas?"#define USE_COLOR_ALPHA":"",e.vertexUv1s?"#define USE_UV1":"",e.vertexUv2s?"#define USE_UV2":"",e.vertexUv3s?"#define USE_UV3":"",e.pointsUvs?"#define USE_POINTS_UV":"",e.flatShading?"#define FLAT_SHADED":"",e.skinning?"#define USE_SKINNING":"",e.morphTargets?"#define USE_MORPHTARGETS":"",e.morphNormals&&e.flatShading===!1?"#define USE_MORPHNORMALS":"",e.morphColors?"#define USE_MORPHCOLORS":"",e.morphTargetsCount>0?"#define MORPHTARGETS_TEXTURE_STRIDE "+e.morphTextureStride:"",e.morphTargetsCount>0?"#define MORPHTARGETS_COUNT "+e.morphTargetsCount:"",e.doubleSided?"#define DOUBLE_SIDED":"",e.flipSided?"#define FLIP_SIDED":"",e.shadowMapEnabled?"#define USE_SHADOWMAP":"",e.shadowMapEnabled?"#define "+l:"",e.sizeAttenuation?"#define USE_SIZEATTENUATION":"",e.numLightProbes>0?"#define USE_LIGHT_PROBES":"",e.logarithmicDepthBuffer?"#define USE_LOGDEPTHBUF":"",e.reverseDepthBuffer?"#define USE_REVERSEDEPTHBUF":"","uniform mat4 modelMatrix;","uniform mat4 modelViewMatrix;","uniform mat4 projectionMatrix;","uniform mat4 viewMatrix;","uniform mat3 normalMatrix;","uniform vec3 cameraPosition;","uniform bool isOrthographic;","#ifdef USE_INSTANCING","	attribute mat4 instanceMatrix;","#endif","#ifdef USE_INSTANCING_COLOR","	attribute vec3 instanceColor;","#endif","#ifdef USE_INSTANCING_MORPH","	uniform sampler2D morphTexture;","#endif","attribute vec3 position;","attribute vec3 normal;","attribute vec2 uv;","#ifdef USE_UV1","	attribute vec2 uv1;","#endif","#ifdef USE_UV2","	attribute vec2 uv2;","#endif","#ifdef USE_UV3","	attribute vec2 uv3;","#endif","#ifdef USE_TANGENT","	attribute vec4 tangent;","#endif","#if defined( USE_COLOR_ALPHA )","	attribute vec4 color;","#elif defined( USE_COLOR )","	attribute vec3 color;","#endif","#ifdef USE_SKINNING","	attribute vec4 skinIndex;","	attribute vec4 skinWeight;","#endif",`
`].filter(Ws).join(`
`),f=[ah(e),"#define SHADER_TYPE "+e.shaderType,"#define SHADER_NAME "+e.shaderName,g,e.useFog&&e.fog?"#define USE_FOG":"",e.useFog&&e.fogExp2?"#define FOG_EXP2":"",e.alphaToCoverage?"#define ALPHA_TO_COVERAGE":"",e.map?"#define USE_MAP":"",e.matcap?"#define USE_MATCAP":"",e.envMap?"#define USE_ENVMAP":"",e.envMap?"#define "+c:"",e.envMap?"#define "+h:"",e.envMap?"#define "+u:"",d?"#define CUBEUV_TEXEL_WIDTH "+d.texelWidth:"",d?"#define CUBEUV_TEXEL_HEIGHT "+d.texelHeight:"",d?"#define CUBEUV_MAX_MIP "+d.maxMip+".0":"",e.lightMap?"#define USE_LIGHTMAP":"",e.aoMap?"#define USE_AOMAP":"",e.bumpMap?"#define USE_BUMPMAP":"",e.normalMap?"#define USE_NORMALMAP":"",e.normalMapObjectSpace?"#define USE_NORMALMAP_OBJECTSPACE":"",e.normalMapTangentSpace?"#define USE_NORMALMAP_TANGENTSPACE":"",e.emissiveMap?"#define USE_EMISSIVEMAP":"",e.anisotropy?"#define USE_ANISOTROPY":"",e.anisotropyMap?"#define USE_ANISOTROPYMAP":"",e.clearcoat?"#define USE_CLEARCOAT":"",e.clearcoatMap?"#define USE_CLEARCOATMAP":"",e.clearcoatRoughnessMap?"#define USE_CLEARCOAT_ROUGHNESSMAP":"",e.clearcoatNormalMap?"#define USE_CLEARCOAT_NORMALMAP":"",e.dispersion?"#define USE_DISPERSION":"",e.iridescence?"#define USE_IRIDESCENCE":"",e.iridescenceMap?"#define USE_IRIDESCENCEMAP":"",e.iridescenceThicknessMap?"#define USE_IRIDESCENCE_THICKNESSMAP":"",e.specularMap?"#define USE_SPECULARMAP":"",e.specularColorMap?"#define USE_SPECULAR_COLORMAP":"",e.specularIntensityMap?"#define USE_SPECULAR_INTENSITYMAP":"",e.roughnessMap?"#define USE_ROUGHNESSMAP":"",e.metalnessMap?"#define USE_METALNESSMAP":"",e.alphaMap?"#define USE_ALPHAMAP":"",e.alphaTest?"#define USE_ALPHATEST":"",e.alphaHash?"#define USE_ALPHAHASH":"",e.sheen?"#define USE_SHEEN":"",e.sheenColorMap?"#define USE_SHEEN_COLORMAP":"",e.sheenRoughnessMap?"#define USE_SHEEN_ROUGHNESSMAP":"",e.transmission?"#define USE_TRANSMISSION":"",e.transmissionMap?"#define USE_TRANSMISSIONMAP":"",e.thicknessMap?"#define USE_THICKNESSMAP":"",e.vertexTangents&&e.flatShading===!1?"#define USE_TANGENT":"",e.vertexColors||e.instancingColor||e.batchingColor?"#define USE_COLOR":"",e.vertexAlphas?"#define USE_COLOR_ALPHA":"",e.vertexUv1s?"#define USE_UV1":"",e.vertexUv2s?"#define USE_UV2":"",e.vertexUv3s?"#define USE_UV3":"",e.pointsUvs?"#define USE_POINTS_UV":"",e.gradientMap?"#define USE_GRADIENTMAP":"",e.flatShading?"#define FLAT_SHADED":"",e.doubleSided?"#define DOUBLE_SIDED":"",e.flipSided?"#define FLIP_SIDED":"",e.shadowMapEnabled?"#define USE_SHADOWMAP":"",e.shadowMapEnabled?"#define "+l:"",e.premultipliedAlpha?"#define PREMULTIPLIED_ALPHA":"",e.numLightProbes>0?"#define USE_LIGHT_PROBES":"",e.decodeVideoTexture?"#define DECODE_VIDEO_TEXTURE":"",e.decodeVideoTextureEmissive?"#define DECODE_VIDEO_TEXTURE_EMISSIVE":"",e.logarithmicDepthBuffer?"#define USE_LOGDEPTHBUF":"",e.reverseDepthBuffer?"#define USE_REVERSEDEPTHBUF":"","uniform mat4 viewMatrix;","uniform vec3 cameraPosition;","uniform bool isOrthographic;",e.toneMapping!==di?"#define TONE_MAPPING":"",e.toneMapping!==di?$t.tonemapping_pars_fragment:"",e.toneMapping!==di?zm("toneMapping",e.toneMapping):"",e.dithering?"#define DITHERING":"",e.opaque?"#define OPAQUE":"",$t.colorspace_pars_fragment,Bm("linearToOutputTexel",e.outputColorSpace),km(),e.useDepthPacking?"#define DEPTH_PACKING "+e.depthPacking:"",`
`].filter(Ws).join(`
`)),a=tl(a),a=ih(a,e),a=sh(a,e),o=tl(o),o=ih(o,e),o=sh(o,e),a=rh(a),o=rh(o),e.isRawShaderMaterial!==!0&&(y=`#version 300 es
`,m=[p,"#define attribute in","#define varying out","#define texture2D texture"].join(`
`)+`
`+m,f=["#define varying in",e.glslVersion===yc?"":"layout(location = 0) out highp vec4 pc_fragColor;",e.glslVersion===yc?"":"#define gl_FragColor pc_fragColor","#define gl_FragDepthEXT gl_FragDepth","#define texture2D texture","#define textureCube texture","#define texture2DProj textureProj","#define texture2DLodEXT textureLod","#define texture2DProjLodEXT textureProjLod","#define textureCubeLodEXT textureLod","#define texture2DGradEXT textureGrad","#define texture2DProjGradEXT textureProjGrad","#define textureCubeGradEXT textureGrad"].join(`
`)+`
`+f);let _=y+m+a,M=y+f+o,C=th(s,s.VERTEX_SHADER,_),A=th(s,s.FRAGMENT_SHADER,M);s.attachShader(x,C),s.attachShader(x,A),e.index0AttributeName!==void 0?s.bindAttribLocation(x,0,e.index0AttributeName):e.morphTargets===!0&&s.bindAttribLocation(x,0,"position"),s.linkProgram(x);function E(w){if(i.debug.checkShaderErrors){let P=s.getProgramInfoLog(x).trim(),F=s.getShaderInfoLog(C).trim(),U=s.getShaderInfoLog(A).trim(),H=!0,N=!0;if(s.getProgramParameter(x,s.LINK_STATUS)===!1)if(H=!1,typeof i.debug.onShaderError=="function")i.debug.onShaderError(s,x,C,A);else{let K=nh(s,C,"vertex"),O=nh(s,A,"fragment");console.error("THREE.WebGLProgram: Shader Error "+s.getError()+" - VALIDATE_STATUS "+s.getProgramParameter(x,s.VALIDATE_STATUS)+`

Material Name: `+w.name+`
Material Type: `+w.type+`

Program Info Log: `+P+`
`+K+`
`+O)}else P!==""?console.warn("THREE.WebGLProgram: Program Info Log:",P):(F===""||U==="")&&(N=!1);N&&(w.diagnostics={runnable:H,programLog:P,vertexShader:{log:F,prefix:m},fragmentShader:{log:U,prefix:f}})}s.deleteShader(C),s.deleteShader(A),b=new ds(s,x),v=Gm(s,x)}let b;this.getUniforms=function(){return b===void 0&&E(this),b};let v;this.getAttributes=function(){return v===void 0&&E(this),v};let S=e.rendererExtensionParallelShaderCompile===!1;return this.isReady=function(){return S===!1&&(S=s.getProgramParameter(x,Lm)),S},this.destroy=function(){n.releaseStatesOfProgram(this),s.deleteProgram(x),this.program=void 0},this.type=e.shaderType,this.name=e.shaderName,this.id=Um++,this.cacheKey=t,this.usedTimes=1,this.program=x,this.vertexShader=C,this.fragmentShader=A,this}var eg=0,el=class{constructor(){this.shaderCache=new Map,this.materialCache=new Map}update(t){let e=t.vertexShader,n=t.fragmentShader,s=this._getShaderStage(e),r=this._getShaderStage(n),a=this._getShaderCacheForMaterial(t);return a.has(s)===!1&&(a.add(s),s.usedTimes++),a.has(r)===!1&&(a.add(r),r.usedTimes++),this}remove(t){let e=this.materialCache.get(t);for(let n of e)n.usedTimes--,n.usedTimes===0&&this.shaderCache.delete(n.code);return this.materialCache.delete(t),this}getVertexShaderID(t){return this._getShaderStage(t.vertexShader).id}getFragmentShaderID(t){return this._getShaderStage(t.fragmentShader).id}dispose(){this.shaderCache.clear(),this.materialCache.clear()}_getShaderCacheForMaterial(t){let e=this.materialCache,n=e.get(t);return n===void 0&&(n=new Set,e.set(t,n)),n}_getShaderStage(t){let e=this.shaderCache,n=e.get(t);return n===void 0&&(n=new nl(t),e.set(t,n)),n}},nl=class{constructor(t){this.id=eg++,this.code=t,this.usedTimes=0}};function ng(i,t,e,n,s,r,a){let o=new jr,l=new el,c=new Set,h=[],u=s.logarithmicDepthBuffer,d=s.vertexTextures,p=s.precision,g={MeshDepthMaterial:"depth",MeshDistanceMaterial:"distanceRGBA",MeshNormalMaterial:"normal",MeshBasicMaterial:"basic",MeshLambertMaterial:"lambert",MeshPhongMaterial:"phong",MeshToonMaterial:"toon",MeshStandardMaterial:"physical",MeshPhysicalMaterial:"physical",MeshMatcapMaterial:"matcap",LineBasicMaterial:"basic",LineDashedMaterial:"dashed",PointsMaterial:"points",ShadowMaterial:"shadow",SpriteMaterial:"sprite"};function x(v){return c.add(v),v===0?"uv":`uv${v}`}function m(v,S,w,P,F){let U=P.fog,H=F.geometry,N=v.isMeshStandardMaterial?P.environment:null,K=(v.isMeshStandardMaterial?e:t).get(v.envMap||N),O=K&&K.mapping===ma?K.image.height:null,Y=g[v.type];v.precision!==null&&(p=s.getMaxPrecision(v.precision),p!==v.precision&&console.warn("THREE.WebGLProgram.getParameters:",v.precision,"not supported, using",p,"instead."));let tt=H.morphAttributes.position||H.morphAttributes.normal||H.morphAttributes.color,nt=tt!==void 0?tt.length:0,Tt=0;H.morphAttributes.position!==void 0&&(Tt=1),H.morphAttributes.normal!==void 0&&(Tt=2),H.morphAttributes.color!==void 0&&(Tt=3);let Nt,X,st,xt;if(Y){let ae=In[Y];Nt=ae.vertexShader,X=ae.fragmentShader}else Nt=v.vertexShader,X=v.fragmentShader,l.update(v),st=l.getVertexShaderID(v),xt=l.getFragmentShaderID(v);let ct=i.getRenderTarget(),Ut=i.state.buffers.depth.getReversed(),kt=F.isInstancedMesh===!0,At=F.isBatchedMesh===!0,Jt=!!v.map,j=!!v.matcap,ot=!!K,D=!!v.aoMap,Dt=!!v.lightMap,it=!!v.bumpMap,Mt=!!v.normalMap,ht=!!v.displacementMap,Ot=!!v.emissiveMap,yt=!!v.metalnessMap,I=!!v.roughnessMap,T=v.anisotropy>0,V=v.clearcoat>0,Z=v.dispersion>0,et=v.iridescence>0,$=v.sheen>0,Rt=v.transmission>0,dt=T&&!!v.anisotropyMap,vt=V&&!!v.clearcoatMap,Qt=V&&!!v.clearcoatNormalMap,rt=V&&!!v.clearcoatRoughnessMap,Et=et&&!!v.iridescenceMap,Bt=et&&!!v.iridescenceThicknessMap,Vt=$&&!!v.sheenColorMap,bt=$&&!!v.sheenRoughnessMap,te=!!v.specularMap,Zt=!!v.specularColorMap,fe=!!v.specularIntensityMap,B=Rt&&!!v.transmissionMap,ft=Rt&&!!v.thicknessMap,q=!!v.gradientMap,Q=!!v.alphaMap,gt=v.alphaTest>0,pt=!!v.alphaHash,Xt=!!v.extensions,Me=di;v.toneMapped&&(ct===null||ct.isXRRenderTarget===!0)&&(Me=i.toneMapping);let Ne={shaderID:Y,shaderType:v.type,shaderName:v.name,vertexShader:Nt,fragmentShader:X,defines:v.defines,customVertexShaderID:st,customFragmentShaderID:xt,isRawShaderMaterial:v.isRawShaderMaterial===!0,glslVersion:v.glslVersion,precision:p,batching:At,batchingColor:At&&F._colorsTexture!==null,instancing:kt,instancingColor:kt&&F.instanceColor!==null,instancingMorph:kt&&F.morphTexture!==null,supportsVertexTextures:d,outputColorSpace:ct===null?i.outputColorSpace:ct.isXRRenderTarget===!0?ct.texture.colorSpace:Is,alphaToCoverage:!!v.alphaToCoverage,map:Jt,matcap:j,envMap:ot,envMapMode:ot&&K.mapping,envMapCubeUVHeight:O,aoMap:D,lightMap:Dt,bumpMap:it,normalMap:Mt,displacementMap:d&&ht,emissiveMap:Ot,normalMapObjectSpace:Mt&&v.normalMapType===sd,normalMapTangentSpace:Mt&&v.normalMapType===Fh,metalnessMap:yt,roughnessMap:I,anisotropy:T,anisotropyMap:dt,clearcoat:V,clearcoatMap:vt,clearcoatNormalMap:Qt,clearcoatRoughnessMap:rt,dispersion:Z,iridescence:et,iridescenceMap:Et,iridescenceThicknessMap:Bt,sheen:$,sheenColorMap:Vt,sheenRoughnessMap:bt,specularMap:te,specularColorMap:Zt,specularIntensityMap:fe,transmission:Rt,transmissionMap:B,thicknessMap:ft,gradientMap:q,opaque:v.transparent===!1&&v.blending===Fn&&v.alphaToCoverage===!1,alphaMap:Q,alphaTest:gt,alphaHash:pt,combine:v.combine,mapUv:Jt&&x(v.map.channel),aoMapUv:D&&x(v.aoMap.channel),lightMapUv:Dt&&x(v.lightMap.channel),bumpMapUv:it&&x(v.bumpMap.channel),normalMapUv:Mt&&x(v.normalMap.channel),displacementMapUv:ht&&x(v.displacementMap.channel),emissiveMapUv:Ot&&x(v.emissiveMap.channel),metalnessMapUv:yt&&x(v.metalnessMap.channel),roughnessMapUv:I&&x(v.roughnessMap.channel),anisotropyMapUv:dt&&x(v.anisotropyMap.channel),clearcoatMapUv:vt&&x(v.clearcoatMap.channel),clearcoatNormalMapUv:Qt&&x(v.clearcoatNormalMap.channel),clearcoatRoughnessMapUv:rt&&x(v.clearcoatRoughnessMap.channel),iridescenceMapUv:Et&&x(v.iridescenceMap.channel),iridescenceThicknessMapUv:Bt&&x(v.iridescenceThicknessMap.channel),sheenColorMapUv:Vt&&x(v.sheenColorMap.channel),sheenRoughnessMapUv:bt&&x(v.sheenRoughnessMap.channel),specularMapUv:te&&x(v.specularMap.channel),specularColorMapUv:Zt&&x(v.specularColorMap.channel),specularIntensityMapUv:fe&&x(v.specularIntensityMap.channel),transmissionMapUv:B&&x(v.transmissionMap.channel),thicknessMapUv:ft&&x(v.thicknessMap.channel),alphaMapUv:Q&&x(v.alphaMap.channel),vertexTangents:!!H.attributes.tangent&&(Mt||T),vertexColors:v.vertexColors,vertexAlphas:v.vertexColors===!0&&!!H.attributes.color&&H.attributes.color.itemSize===4,pointsUvs:F.isPoints===!0&&!!H.attributes.uv&&(Jt||Q),fog:!!U,useFog:v.fog===!0,fogExp2:!!U&&U.isFogExp2,flatShading:v.flatShading===!0,sizeAttenuation:v.sizeAttenuation===!0,logarithmicDepthBuffer:u,reverseDepthBuffer:Ut,skinning:F.isSkinnedMesh===!0,morphTargets:H.morphAttributes.position!==void 0,morphNormals:H.morphAttributes.normal!==void 0,morphColors:H.morphAttributes.color!==void 0,morphTargetsCount:nt,morphTextureStride:Tt,numDirLights:S.directional.length,numPointLights:S.point.length,numSpotLights:S.spot.length,numSpotLightMaps:S.spotLightMap.length,numRectAreaLights:S.rectArea.length,numHemiLights:S.hemi.length,numDirLightShadows:S.directionalShadowMap.length,numPointLightShadows:S.pointShadowMap.length,numSpotLightShadows:S.spotShadowMap.length,numSpotLightShadowsWithMaps:S.numSpotLightShadowsWithMaps,numLightProbes:S.numLightProbes,numClippingPlanes:a.numPlanes,numClipIntersection:a.numIntersection,dithering:v.dithering,shadowMapEnabled:i.shadowMap.enabled&&w.length>0,shadowMapType:i.shadowMap.type,toneMapping:Me,decodeVideoTexture:Jt&&v.map.isVideoTexture===!0&&ne.getTransfer(v.map.colorSpace)===le,decodeVideoTextureEmissive:Ot&&v.emissiveMap.isVideoTexture===!0&&ne.getTransfer(v.emissiveMap.colorSpace)===le,premultipliedAlpha:v.premultipliedAlpha,doubleSided:v.side===qe,flipSided:v.side===ze,useDepthPacking:v.depthPacking>=0,depthPacking:v.depthPacking||0,index0AttributeName:v.index0AttributeName,extensionClipCullDistance:Xt&&v.extensions.clipCullDistance===!0&&n.has("WEBGL_clip_cull_distance"),extensionMultiDraw:(Xt&&v.extensions.multiDraw===!0||At)&&n.has("WEBGL_multi_draw"),rendererExtensionParallelShaderCompile:n.has("KHR_parallel_shader_compile"),customProgramCacheKey:v.customProgramCacheKey()};return Ne.vertexUv1s=c.has(1),Ne.vertexUv2s=c.has(2),Ne.vertexUv3s=c.has(3),c.clear(),Ne}function f(v){let S=[];if(v.shaderID?S.push(v.shaderID):(S.push(v.customVertexShaderID),S.push(v.customFragmentShaderID)),v.defines!==void 0)for(let w in v.defines)S.push(w),S.push(v.defines[w]);return v.isRawShaderMaterial===!1&&(y(S,v),_(S,v),S.push(i.outputColorSpace)),S.push(v.customProgramCacheKey),S.join()}function y(v,S){v.push(S.precision),v.push(S.outputColorSpace),v.push(S.envMapMode),v.push(S.envMapCubeUVHeight),v.push(S.mapUv),v.push(S.alphaMapUv),v.push(S.lightMapUv),v.push(S.aoMapUv),v.push(S.bumpMapUv),v.push(S.normalMapUv),v.push(S.displacementMapUv),v.push(S.emissiveMapUv),v.push(S.metalnessMapUv),v.push(S.roughnessMapUv),v.push(S.anisotropyMapUv),v.push(S.clearcoatMapUv),v.push(S.clearcoatNormalMapUv),v.push(S.clearcoatRoughnessMapUv),v.push(S.iridescenceMapUv),v.push(S.iridescenceThicknessMapUv),v.push(S.sheenColorMapUv),v.push(S.sheenRoughnessMapUv),v.push(S.specularMapUv),v.push(S.specularColorMapUv),v.push(S.specularIntensityMapUv),v.push(S.transmissionMapUv),v.push(S.thicknessMapUv),v.push(S.combine),v.push(S.fogExp2),v.push(S.sizeAttenuation),v.push(S.morphTargetsCount),v.push(S.morphAttributeCount),v.push(S.numDirLights),v.push(S.numPointLights),v.push(S.numSpotLights),v.push(S.numSpotLightMaps),v.push(S.numHemiLights),v.push(S.numRectAreaLights),v.push(S.numDirLightShadows),v.push(S.numPointLightShadows),v.push(S.numSpotLightShadows),v.push(S.numSpotLightShadowsWithMaps),v.push(S.numLightProbes),v.push(S.shadowMapType),v.push(S.toneMapping),v.push(S.numClippingPlanes),v.push(S.numClipIntersection),v.push(S.depthPacking)}function _(v,S){o.disableAll(),S.supportsVertexTextures&&o.enable(0),S.instancing&&o.enable(1),S.instancingColor&&o.enable(2),S.instancingMorph&&o.enable(3),S.matcap&&o.enable(4),S.envMap&&o.enable(5),S.normalMapObjectSpace&&o.enable(6),S.normalMapTangentSpace&&o.enable(7),S.clearcoat&&o.enable(8),S.iridescence&&o.enable(9),S.alphaTest&&o.enable(10),S.vertexColors&&o.enable(11),S.vertexAlphas&&o.enable(12),S.vertexUv1s&&o.enable(13),S.vertexUv2s&&o.enable(14),S.vertexUv3s&&o.enable(15),S.vertexTangents&&o.enable(16),S.anisotropy&&o.enable(17),S.alphaHash&&o.enable(18),S.batching&&o.enable(19),S.dispersion&&o.enable(20),S.batchingColor&&o.enable(21),v.push(o.mask),o.disableAll(),S.fog&&o.enable(0),S.useFog&&o.enable(1),S.flatShading&&o.enable(2),S.logarithmicDepthBuffer&&o.enable(3),S.reverseDepthBuffer&&o.enable(4),S.skinning&&o.enable(5),S.morphTargets&&o.enable(6),S.morphNormals&&o.enable(7),S.morphColors&&o.enable(8),S.premultipliedAlpha&&o.enable(9),S.shadowMapEnabled&&o.enable(10),S.doubleSided&&o.enable(11),S.flipSided&&o.enable(12),S.useDepthPacking&&o.enable(13),S.dithering&&o.enable(14),S.transmission&&o.enable(15),S.sheen&&o.enable(16),S.opaque&&o.enable(17),S.pointsUvs&&o.enable(18),S.decodeVideoTexture&&o.enable(19),S.decodeVideoTextureEmissive&&o.enable(20),S.alphaToCoverage&&o.enable(21),v.push(o.mask)}function M(v){let S=g[v.type],w;if(S){let P=In[S];w=Kd.clone(P.uniforms)}else w=v.uniforms;return w}function C(v,S){let w;for(let P=0,F=h.length;P<F;P++){let U=h[P];if(U.cacheKey===S){w=U,++w.usedTimes;break}}return w===void 0&&(w=new tg(i,S,v,r),h.push(w)),w}function A(v){if(--v.usedTimes===0){let S=h.indexOf(v);h[S]=h[h.length-1],h.pop(),v.destroy()}}function E(v){l.remove(v)}function b(){l.dispose()}return{getParameters:m,getProgramCacheKey:f,getUniforms:M,acquireProgram:C,releaseProgram:A,releaseShaderCache:E,programs:h,dispose:b}}function ig(){let i=new WeakMap;function t(a){return i.has(a)}function e(a){let o=i.get(a);return o===void 0&&(o={},i.set(a,o)),o}function n(a){i.delete(a)}function s(a,o,l){i.get(a)[o]=l}function r(){i=new WeakMap}return{has:t,get:e,remove:n,update:s,dispose:r}}function sg(i,t){return i.groupOrder!==t.groupOrder?i.groupOrder-t.groupOrder:i.renderOrder!==t.renderOrder?i.renderOrder-t.renderOrder:i.material.id!==t.material.id?i.material.id-t.material.id:i.z!==t.z?i.z-t.z:i.id-t.id}function oh(i,t){return i.groupOrder!==t.groupOrder?i.groupOrder-t.groupOrder:i.renderOrder!==t.renderOrder?i.renderOrder-t.renderOrder:i.z!==t.z?t.z-i.z:i.id-t.id}function lh(){let i=[],t=0,e=[],n=[],s=[];function r(){t=0,e.length=0,n.length=0,s.length=0}function a(u,d,p,g,x,m){let f=i[t];return f===void 0?(f={id:u.id,object:u,geometry:d,material:p,groupOrder:g,renderOrder:u.renderOrder,z:x,group:m},i[t]=f):(f.id=u.id,f.object=u,f.geometry=d,f.material=p,f.groupOrder=g,f.renderOrder=u.renderOrder,f.z=x,f.group=m),t++,f}function o(u,d,p,g,x,m){let f=a(u,d,p,g,x,m);p.transmission>0?n.push(f):p.transparent===!0?s.push(f):e.push(f)}function l(u,d,p,g,x,m){let f=a(u,d,p,g,x,m);p.transmission>0?n.unshift(f):p.transparent===!0?s.unshift(f):e.unshift(f)}function c(u,d){e.length>1&&e.sort(u||sg),n.length>1&&n.sort(d||oh),s.length>1&&s.sort(d||oh)}function h(){for(let u=t,d=i.length;u<d;u++){let p=i[u];if(p.id===null)break;p.id=null,p.object=null,p.geometry=null,p.material=null,p.group=null}}return{opaque:e,transmissive:n,transparent:s,init:r,push:o,unshift:l,finish:h,sort:c}}function rg(){let i=new WeakMap;function t(n,s){let r=i.get(n),a;return r===void 0?(a=new lh,i.set(n,[a])):s>=r.length?(a=new lh,r.push(a)):a=r[s],a}function e(){i=new WeakMap}return{get:t,dispose:e}}function ag(){let i={};return{get:function(t){if(i[t.id]!==void 0)return i[t.id];let e;switch(t.type){case"DirectionalLight":e={direction:new L,color:new Gt};break;case"SpotLight":e={position:new L,direction:new L,color:new Gt,distance:0,coneCos:0,penumbraCos:0,decay:0};break;case"PointLight":e={position:new L,color:new Gt,distance:0,decay:0};break;case"HemisphereLight":e={direction:new L,skyColor:new Gt,groundColor:new Gt};break;case"RectAreaLight":e={color:new Gt,position:new L,halfWidth:new L,halfHeight:new L};break}return i[t.id]=e,e}}}function og(){let i={};return{get:function(t){if(i[t.id]!==void 0)return i[t.id];let e;switch(t.type){case"DirectionalLight":e={shadowIntensity:1,shadowBias:0,shadowNormalBias:0,shadowRadius:1,shadowMapSize:new at};break;case"SpotLight":e={shadowIntensity:1,shadowBias:0,shadowNormalBias:0,shadowRadius:1,shadowMapSize:new at};break;case"PointLight":e={shadowIntensity:1,shadowBias:0,shadowNormalBias:0,shadowRadius:1,shadowMapSize:new at,shadowCameraNear:1,shadowCameraFar:1e3};break}return i[t.id]=e,e}}}var lg=0;function cg(i,t){return(t.castShadow?2:0)-(i.castShadow?2:0)+(t.map?1:0)-(i.map?1:0)}function hg(i){let t=new ag,e=og(),n={version:0,hash:{directionalLength:-1,pointLength:-1,spotLength:-1,rectAreaLength:-1,hemiLength:-1,numDirectionalShadows:-1,numPointShadows:-1,numSpotShadows:-1,numSpotMaps:-1,numLightProbes:-1},ambient:[0,0,0],probe:[],directional:[],directionalShadow:[],directionalShadowMap:[],directionalShadowMatrix:[],spot:[],spotLightMap:[],spotShadow:[],spotShadowMap:[],spotLightMatrix:[],rectArea:[],rectAreaLTC1:null,rectAreaLTC2:null,point:[],pointShadow:[],pointShadowMap:[],pointShadowMatrix:[],hemi:[],numSpotLightShadowsWithMaps:0,numLightProbes:0};for(let c=0;c<9;c++)n.probe.push(new L);let s=new L,r=new he,a=new he;function o(c){let h=0,u=0,d=0;for(let v=0;v<9;v++)n.probe[v].set(0,0,0);let p=0,g=0,x=0,m=0,f=0,y=0,_=0,M=0,C=0,A=0,E=0;c.sort(cg);for(let v=0,S=c.length;v<S;v++){let w=c[v],P=w.color,F=w.intensity,U=w.distance,H=w.shadow&&w.shadow.map?w.shadow.map.texture:null;if(w.isAmbientLight)h+=P.r*F,u+=P.g*F,d+=P.b*F;else if(w.isLightProbe){for(let N=0;N<9;N++)n.probe[N].addScaledVector(w.sh.coefficients[N],F);E++}else if(w.isDirectionalLight){let N=t.get(w);if(N.color.copy(w.color).multiplyScalar(w.intensity),w.castShadow){let K=w.shadow,O=e.get(w);O.shadowIntensity=K.intensity,O.shadowBias=K.bias,O.shadowNormalBias=K.normalBias,O.shadowRadius=K.radius,O.shadowMapSize=K.mapSize,n.directionalShadow[p]=O,n.directionalShadowMap[p]=H,n.directionalShadowMatrix[p]=w.shadow.matrix,y++}n.directional[p]=N,p++}else if(w.isSpotLight){let N=t.get(w);N.position.setFromMatrixPosition(w.matrixWorld),N.color.copy(P).multiplyScalar(F),N.distance=U,N.coneCos=Math.cos(w.angle),N.penumbraCos=Math.cos(w.angle*(1-w.penumbra)),N.decay=w.decay,n.spot[x]=N;let K=w.shadow;if(w.map&&(n.spotLightMap[C]=w.map,C++,K.updateMatrices(w),w.castShadow&&A++),n.spotLightMatrix[x]=K.matrix,w.castShadow){let O=e.get(w);O.shadowIntensity=K.intensity,O.shadowBias=K.bias,O.shadowNormalBias=K.normalBias,O.shadowRadius=K.radius,O.shadowMapSize=K.mapSize,n.spotShadow[x]=O,n.spotShadowMap[x]=H,M++}x++}else if(w.isRectAreaLight){let N=t.get(w);N.color.copy(P).multiplyScalar(F),N.halfWidth.set(w.width*.5,0,0),N.halfHeight.set(0,w.height*.5,0),n.rectArea[m]=N,m++}else if(w.isPointLight){let N=t.get(w);if(N.color.copy(w.color).multiplyScalar(w.intensity),N.distance=w.distance,N.decay=w.decay,w.castShadow){let K=w.shadow,O=e.get(w);O.shadowIntensity=K.intensity,O.shadowBias=K.bias,O.shadowNormalBias=K.normalBias,O.shadowRadius=K.radius,O.shadowMapSize=K.mapSize,O.shadowCameraNear=K.camera.near,O.shadowCameraFar=K.camera.far,n.pointShadow[g]=O,n.pointShadowMap[g]=H,n.pointShadowMatrix[g]=w.shadow.matrix,_++}n.point[g]=N,g++}else if(w.isHemisphereLight){let N=t.get(w);N.skyColor.copy(w.color).multiplyScalar(F),N.groundColor.copy(w.groundColor).multiplyScalar(F),n.hemi[f]=N,f++}}m>0&&(i.has("OES_texture_float_linear")===!0?(n.rectAreaLTC1=ut.LTC_FLOAT_1,n.rectAreaLTC2=ut.LTC_FLOAT_2):(n.rectAreaLTC1=ut.LTC_HALF_1,n.rectAreaLTC2=ut.LTC_HALF_2)),n.ambient[0]=h,n.ambient[1]=u,n.ambient[2]=d;let b=n.hash;(b.directionalLength!==p||b.pointLength!==g||b.spotLength!==x||b.rectAreaLength!==m||b.hemiLength!==f||b.numDirectionalShadows!==y||b.numPointShadows!==_||b.numSpotShadows!==M||b.numSpotMaps!==C||b.numLightProbes!==E)&&(n.directional.length=p,n.spot.length=x,n.rectArea.length=m,n.point.length=g,n.hemi.length=f,n.directionalShadow.length=y,n.directionalShadowMap.length=y,n.pointShadow.length=_,n.pointShadowMap.length=_,n.spotShadow.length=M,n.spotShadowMap.length=M,n.directionalShadowMatrix.length=y,n.pointShadowMatrix.length=_,n.spotLightMatrix.length=M+C-A,n.spotLightMap.length=C,n.numSpotLightShadowsWithMaps=A,n.numLightProbes=E,b.directionalLength=p,b.pointLength=g,b.spotLength=x,b.rectAreaLength=m,b.hemiLength=f,b.numDirectionalShadows=y,b.numPointShadows=_,b.numSpotShadows=M,b.numSpotMaps=C,b.numLightProbes=E,n.version=lg++)}function l(c,h){let u=0,d=0,p=0,g=0,x=0,m=h.matrixWorldInverse;for(let f=0,y=c.length;f<y;f++){let _=c[f];if(_.isDirectionalLight){let M=n.directional[u];M.direction.setFromMatrixPosition(_.matrixWorld),s.setFromMatrixPosition(_.target.matrixWorld),M.direction.sub(s),M.direction.transformDirection(m),u++}else if(_.isSpotLight){let M=n.spot[p];M.position.setFromMatrixPosition(_.matrixWorld),M.position.applyMatrix4(m),M.direction.setFromMatrixPosition(_.matrixWorld),s.setFromMatrixPosition(_.target.matrixWorld),M.direction.sub(s),M.direction.transformDirection(m),p++}else if(_.isRectAreaLight){let M=n.rectArea[g];M.position.setFromMatrixPosition(_.matrixWorld),M.position.applyMatrix4(m),a.identity(),r.copy(_.matrixWorld),r.premultiply(m),a.extractRotation(r),M.halfWidth.set(_.width*.5,0,0),M.halfHeight.set(0,_.height*.5,0),M.halfWidth.applyMatrix4(a),M.halfHeight.applyMatrix4(a),g++}else if(_.isPointLight){let M=n.point[d];M.position.setFromMatrixPosition(_.matrixWorld),M.position.applyMatrix4(m),d++}else if(_.isHemisphereLight){let M=n.hemi[x];M.direction.setFromMatrixPosition(_.matrixWorld),M.direction.transformDirection(m),x++}}}return{setup:o,setupView:l,state:n}}function ch(i){let t=new hg(i),e=[],n=[];function s(h){c.camera=h,e.length=0,n.length=0}function r(h){e.push(h)}function a(h){n.push(h)}function o(){t.setup(e)}function l(h){t.setupView(e,h)}let c={lightsArray:e,shadowsArray:n,camera:null,lights:t,transmissionRenderTarget:{}};return{init:s,state:c,setupLights:o,setupLightsView:l,pushLight:r,pushShadow:a}}function ug(i){let t=new WeakMap;function e(s,r=0){let a=t.get(s),o;return a===void 0?(o=new ch(i),t.set(s,[o])):r>=a.length?(o=new ch(i),a.push(o)):o=a[r],o}function n(){t=new WeakMap}return{get:e,dispose:n}}var il=class extends Ui{static get type(){return"MeshDepthMaterial"}constructor(t){super(),this.isMeshDepthMaterial=!0,this.depthPacking=nd,this.map=null,this.alphaMap=null,this.displacementMap=null,this.displacementScale=1,this.displacementBias=0,this.wireframe=!1,this.wireframeLinewidth=1,this.setValues(t)}copy(t){return super.copy(t),this.depthPacking=t.depthPacking,this.map=t.map,this.alphaMap=t.alphaMap,this.displacementMap=t.displacementMap,this.displacementScale=t.displacementScale,this.displacementBias=t.displacementBias,this.wireframe=t.wireframe,this.wireframeLinewidth=t.wireframeLinewidth,this}},sl=class extends Ui{static get type(){return"MeshDistanceMaterial"}constructor(t){super(),this.isMeshDistanceMaterial=!0,this.map=null,this.alphaMap=null,this.displacementMap=null,this.displacementScale=1,this.displacementBias=0,this.setValues(t)}copy(t){return super.copy(t),this.map=t.map,this.alphaMap=t.alphaMap,this.displacementMap=t.displacementMap,this.displacementScale=t.displacementScale,this.displacementBias=t.displacementBias,this}},dg=`void main() {
	gl_Position = vec4( position, 1.0 );
}`,fg=`uniform sampler2D shadow_pass;
uniform vec2 resolution;
uniform float radius;
#include <packing>
void main() {
	const float samples = float( VSM_SAMPLES );
	float mean = 0.0;
	float squared_mean = 0.0;
	float uvStride = samples <= 1.0 ? 0.0 : 2.0 / ( samples - 1.0 );
	float uvStart = samples <= 1.0 ? 0.0 : - 1.0;
	for ( float i = 0.0; i < samples; i ++ ) {
		float uvOffset = uvStart + i * uvStride;
		#ifdef HORIZONTAL_PASS
			vec2 distribution = unpackRGBATo2Half( texture2D( shadow_pass, ( gl_FragCoord.xy + vec2( uvOffset, 0.0 ) * radius ) / resolution ) );
			mean += distribution.x;
			squared_mean += distribution.y * distribution.y + distribution.x * distribution.x;
		#else
			float depth = unpackRGBAToDepth( texture2D( shadow_pass, ( gl_FragCoord.xy + vec2( 0.0, uvOffset ) * radius ) / resolution ) );
			mean += depth;
			squared_mean += depth * depth;
		#endif
	}
	mean = mean / samples;
	squared_mean = squared_mean / samples;
	float std_dev = sqrt( squared_mean - mean * mean );
	gl_FragColor = pack2HalfToRGBA( vec2( mean, std_dev ) );
}`;function pg(i,t,e){let n=new Qs,s=new at,r=new at,a=new ce,o=new il({depthPacking:id}),l=new sl,c={},h=e.maxTextureSize,u={[fi]:ze,[ze]:fi,[qe]:qe},d=new mn({defines:{VSM_SAMPLES:8},uniforms:{shadow_pass:{value:null},resolution:{value:new at},radius:{value:4}},vertexShader:dg,fragmentShader:fg}),p=d.clone();p.defines.HORIZONTAL_PASS=1;let g=new Ie;g.setAttribute("position",new ke(new Float32Array([-1,-1,.5,3,-1,.5,-1,3,.5]),3));let x=new Ct(g,d),m=this;this.enabled=!1,this.autoUpdate=!0,this.needsUpdate=!1,this.type=vh;let f=this.type;this.render=function(A,E,b){if(m.enabled===!1||m.autoUpdate===!1&&m.needsUpdate===!1||A.length===0)return;let v=i.getRenderTarget(),S=i.getActiveCubeFace(),w=i.getActiveMipmapLevel(),P=i.state;P.setBlending(ui),P.buffers.color.setClear(1,1,1,1),P.buffers.depth.setTest(!0),P.setScissorTest(!1);let F=f!==Gn&&this.type===Gn,U=f===Gn&&this.type!==Gn;for(let H=0,N=A.length;H<N;H++){let K=A[H],O=K.shadow;if(O===void 0){console.warn("THREE.WebGLShadowMap:",K,"has no shadow.");continue}if(O.autoUpdate===!1&&O.needsUpdate===!1)continue;s.copy(O.mapSize);let Y=O.getFrameExtents();if(s.multiply(Y),r.copy(O.mapSize),(s.x>h||s.y>h)&&(s.x>h&&(r.x=Math.floor(h/Y.x),s.x=r.x*Y.x,O.mapSize.x=r.x),s.y>h&&(r.y=Math.floor(h/Y.y),s.y=r.y*Y.y,O.mapSize.y=r.y)),O.map===null||F===!0||U===!0){let nt=this.type!==Gn?{minFilter:an,magFilter:an}:{};O.map!==null&&O.map.dispose(),O.map=new $n(s.x,s.y,nt),O.map.texture.name=K.name+".shadowMap",O.camera.updateProjectionMatrix()}i.setRenderTarget(O.map),i.clear();let tt=O.getViewportCount();for(let nt=0;nt<tt;nt++){let Tt=O.getViewport(nt);a.set(r.x*Tt.x,r.y*Tt.y,r.x*Tt.z,r.y*Tt.w),P.viewport(a),O.updateMatrices(K,nt),n=O.getFrustum(),M(E,b,O.camera,K,this.type)}O.isPointLightShadow!==!0&&this.type===Gn&&y(O,b),O.needsUpdate=!1}f=this.type,m.needsUpdate=!1,i.setRenderTarget(v,S,w)};function y(A,E){let b=t.update(x);d.defines.VSM_SAMPLES!==A.blurSamples&&(d.defines.VSM_SAMPLES=A.blurSamples,p.defines.VSM_SAMPLES=A.blurSamples,d.needsUpdate=!0,p.needsUpdate=!0),A.mapPass===null&&(A.mapPass=new $n(s.x,s.y)),d.uniforms.shadow_pass.value=A.map.texture,d.uniforms.resolution.value=A.mapSize,d.uniforms.radius.value=A.radius,i.setRenderTarget(A.mapPass),i.clear(),i.renderBufferDirect(E,null,b,d,x,null),p.uniforms.shadow_pass.value=A.mapPass.texture,p.uniforms.resolution.value=A.mapSize,p.uniforms.radius.value=A.radius,i.setRenderTarget(A.map),i.clear(),i.renderBufferDirect(E,null,b,p,x,null)}function _(A,E,b,v){let S=null,w=b.isPointLight===!0?A.customDistanceMaterial:A.customDepthMaterial;if(w!==void 0)S=w;else if(S=b.isPointLight===!0?l:o,i.localClippingEnabled&&E.clipShadows===!0&&Array.isArray(E.clippingPlanes)&&E.clippingPlanes.length!==0||E.displacementMap&&E.displacementScale!==0||E.alphaMap&&E.alphaTest>0||E.map&&E.alphaTest>0){let P=S.uuid,F=E.uuid,U=c[P];U===void 0&&(U={},c[P]=U);let H=U[F];H===void 0&&(H=S.clone(),U[F]=H,E.addEventListener("dispose",C)),S=H}if(S.visible=E.visible,S.wireframe=E.wireframe,v===Gn?S.side=E.shadowSide!==null?E.shadowSide:E.side:S.side=E.shadowSide!==null?E.shadowSide:u[E.side],S.alphaMap=E.alphaMap,S.alphaTest=E.alphaTest,S.map=E.map,S.clipShadows=E.clipShadows,S.clippingPlanes=E.clippingPlanes,S.clipIntersection=E.clipIntersection,S.displacementMap=E.displacementMap,S.displacementScale=E.displacementScale,S.displacementBias=E.displacementBias,S.wireframeLinewidth=E.wireframeLinewidth,S.linewidth=E.linewidth,b.isPointLight===!0&&S.isMeshDistanceMaterial===!0){let P=i.properties.get(S);P.light=b}return S}function M(A,E,b,v,S){if(A.visible===!1)return;if(A.layers.test(E.layers)&&(A.isMesh||A.isLine||A.isPoints)&&(A.castShadow||A.receiveShadow&&S===Gn)&&(!A.frustumCulled||n.intersectsObject(A))){A.modelViewMatrix.multiplyMatrices(b.matrixWorldInverse,A.matrixWorld);let F=t.update(A),U=A.material;if(Array.isArray(U)){let H=F.groups;for(let N=0,K=H.length;N<K;N++){let O=H[N],Y=U[O.materialIndex];if(Y&&Y.visible){let tt=_(A,Y,v,S);A.onBeforeShadow(i,A,E,b,F,tt,O),i.renderBufferDirect(b,null,F,tt,A,O),A.onAfterShadow(i,A,E,b,F,tt,O)}}}else if(U.visible){let H=_(A,U,v,S);A.onBeforeShadow(i,A,E,b,F,H,null),i.renderBufferDirect(b,null,F,H,A,null),A.onAfterShadow(i,A,E,b,F,H,null)}}let P=A.children;for(let F=0,U=P.length;F<U;F++)M(P[F],E,b,v,S)}function C(A){A.target.removeEventListener("dispose",C);for(let b in c){let v=c[b],S=A.target.uuid;S in v&&(v[S].dispose(),delete v[S])}}}var mg={[oo]:lo,[co]:fo,[ho]:po,[fs]:uo,[lo]:oo,[fo]:co,[po]:ho,[uo]:fs};function gg(i,t){function e(){let B=!1,ft=new ce,q=null,Q=new ce(0,0,0,0);return{setMask:function(gt){q!==gt&&!B&&(i.colorMask(gt,gt,gt,gt),q=gt)},setLocked:function(gt){B=gt},setClear:function(gt,pt,Xt,Me,Ne){Ne===!0&&(gt*=Me,pt*=Me,Xt*=Me),ft.set(gt,pt,Xt,Me),Q.equals(ft)===!1&&(i.clearColor(gt,pt,Xt,Me),Q.copy(ft))},reset:function(){B=!1,q=null,Q.set(-1,0,0,0)}}}function n(){let B=!1,ft=!1,q=null,Q=null,gt=null;return{setReversed:function(pt){if(ft!==pt){let Xt=t.get("EXT_clip_control");ft?Xt.clipControlEXT(Xt.LOWER_LEFT_EXT,Xt.ZERO_TO_ONE_EXT):Xt.clipControlEXT(Xt.LOWER_LEFT_EXT,Xt.NEGATIVE_ONE_TO_ONE_EXT);let Me=gt;gt=null,this.setClear(Me)}ft=pt},getReversed:function(){return ft},setTest:function(pt){pt?ct(i.DEPTH_TEST):Ut(i.DEPTH_TEST)},setMask:function(pt){q!==pt&&!B&&(i.depthMask(pt),q=pt)},setFunc:function(pt){if(ft&&(pt=mg[pt]),Q!==pt){switch(pt){case oo:i.depthFunc(i.NEVER);break;case lo:i.depthFunc(i.ALWAYS);break;case co:i.depthFunc(i.LESS);break;case fs:i.depthFunc(i.LEQUAL);break;case ho:i.depthFunc(i.EQUAL);break;case uo:i.depthFunc(i.GEQUAL);break;case fo:i.depthFunc(i.GREATER);break;case po:i.depthFunc(i.NOTEQUAL);break;default:i.depthFunc(i.LEQUAL)}Q=pt}},setLocked:function(pt){B=pt},setClear:function(pt){gt!==pt&&(ft&&(pt=1-pt),i.clearDepth(pt),gt=pt)},reset:function(){B=!1,q=null,Q=null,gt=null,ft=!1}}}function s(){let B=!1,ft=null,q=null,Q=null,gt=null,pt=null,Xt=null,Me=null,Ne=null;return{setTest:function(ae){B||(ae?ct(i.STENCIL_TEST):Ut(i.STENCIL_TEST))},setMask:function(ae){ft!==ae&&!B&&(i.stencilMask(ae),ft=ae)},setFunc:function(ae,yn,Nn){(q!==ae||Q!==yn||gt!==Nn)&&(i.stencilFunc(ae,yn,Nn),q=ae,Q=yn,gt=Nn)},setOp:function(ae,yn,Nn){(pt!==ae||Xt!==yn||Me!==Nn)&&(i.stencilOp(ae,yn,Nn),pt=ae,Xt=yn,Me=Nn)},setLocked:function(ae){B=ae},setClear:function(ae){Ne!==ae&&(i.clearStencil(ae),Ne=ae)},reset:function(){B=!1,ft=null,q=null,Q=null,gt=null,pt=null,Xt=null,Me=null,Ne=null}}}let r=new e,a=new n,o=new s,l=new WeakMap,c=new WeakMap,h={},u={},d=new WeakMap,p=[],g=null,x=!1,m=null,f=null,y=null,_=null,M=null,C=null,A=null,E=new Gt(0,0,0),b=0,v=!1,S=null,w=null,P=null,F=null,U=null,H=i.getParameter(i.MAX_COMBINED_TEXTURE_IMAGE_UNITS),N=!1,K=0,O=i.getParameter(i.VERSION);O.indexOf("WebGL")!==-1?(K=parseFloat(/^WebGL (\d)/.exec(O)[1]),N=K>=1):O.indexOf("OpenGL ES")!==-1&&(K=parseFloat(/^OpenGL ES (\d)/.exec(O)[1]),N=K>=2);let Y=null,tt={},nt=i.getParameter(i.SCISSOR_BOX),Tt=i.getParameter(i.VIEWPORT),Nt=new ce().fromArray(nt),X=new ce().fromArray(Tt);function st(B,ft,q,Q){let gt=new Uint8Array(4),pt=i.createTexture();i.bindTexture(B,pt),i.texParameteri(B,i.TEXTURE_MIN_FILTER,i.NEAREST),i.texParameteri(B,i.TEXTURE_MAG_FILTER,i.NEAREST);for(let Xt=0;Xt<q;Xt++)B===i.TEXTURE_3D||B===i.TEXTURE_2D_ARRAY?i.texImage3D(ft,0,i.RGBA,1,1,Q,0,i.RGBA,i.UNSIGNED_BYTE,gt):i.texImage2D(ft+Xt,0,i.RGBA,1,1,0,i.RGBA,i.UNSIGNED_BYTE,gt);return pt}let xt={};xt[i.TEXTURE_2D]=st(i.TEXTURE_2D,i.TEXTURE_2D,1),xt[i.TEXTURE_CUBE_MAP]=st(i.TEXTURE_CUBE_MAP,i.TEXTURE_CUBE_MAP_POSITIVE_X,6),xt[i.TEXTURE_2D_ARRAY]=st(i.TEXTURE_2D_ARRAY,i.TEXTURE_2D_ARRAY,1,1),xt[i.TEXTURE_3D]=st(i.TEXTURE_3D,i.TEXTURE_3D,1,1),r.setClear(0,0,0,1),a.setClear(1),o.setClear(0),ct(i.DEPTH_TEST),a.setFunc(fs),it(!1),Mt(uc),ct(i.CULL_FACE),D(ui);function ct(B){h[B]!==!0&&(i.enable(B),h[B]=!0)}function Ut(B){h[B]!==!1&&(i.disable(B),h[B]=!1)}function kt(B,ft){return u[B]!==ft?(i.bindFramebuffer(B,ft),u[B]=ft,B===i.DRAW_FRAMEBUFFER&&(u[i.FRAMEBUFFER]=ft),B===i.FRAMEBUFFER&&(u[i.DRAW_FRAMEBUFFER]=ft),!0):!1}function At(B,ft){let q=p,Q=!1;if(B){q=d.get(ft),q===void 0&&(q=[],d.set(ft,q));let gt=B.textures;if(q.length!==gt.length||q[0]!==i.COLOR_ATTACHMENT0){for(let pt=0,Xt=gt.length;pt<Xt;pt++)q[pt]=i.COLOR_ATTACHMENT0+pt;q.length=gt.length,Q=!0}}else q[0]!==i.BACK&&(q[0]=i.BACK,Q=!0);Q&&i.drawBuffers(q)}function Jt(B){return g!==B?(i.useProgram(B),g=B,!0):!1}let j={[Ii]:i.FUNC_ADD,[Iu]:i.FUNC_SUBTRACT,[Pu]:i.FUNC_REVERSE_SUBTRACT};j[Fu]=i.MIN,j[Du]=i.MAX;let ot={[Lu]:i.ZERO,[Uu]:i.ONE,[Nu]:i.SRC_COLOR,[ro]:i.SRC_ALPHA,[Vu]:i.SRC_ALPHA_SATURATE,[ku]:i.DST_COLOR,[Bu]:i.DST_ALPHA,[Ou]:i.ONE_MINUS_SRC_COLOR,[ao]:i.ONE_MINUS_SRC_ALPHA,[Hu]:i.ONE_MINUS_DST_COLOR,[zu]:i.ONE_MINUS_DST_ALPHA,[Gu]:i.CONSTANT_COLOR,[Wu]:i.ONE_MINUS_CONSTANT_COLOR,[Ku]:i.CONSTANT_ALPHA,[Xu]:i.ONE_MINUS_CONSTANT_ALPHA};function D(B,ft,q,Q,gt,pt,Xt,Me,Ne,ae){if(B===ui){x===!0&&(Ut(i.BLEND),x=!1);return}if(x===!1&&(ct(i.BLEND),x=!0),B!==Cu){if(B!==m||ae!==v){if((f!==Ii||M!==Ii)&&(i.blendEquation(i.FUNC_ADD),f=Ii,M=Ii),ae)switch(B){case Fn:i.blendFuncSeparate(i.ONE,i.ONE_MINUS_SRC_ALPHA,i.ONE,i.ONE_MINUS_SRC_ALPHA);break;case pi:i.blendFunc(i.ONE,i.ONE);break;case dc:i.blendFuncSeparate(i.ZERO,i.ONE_MINUS_SRC_COLOR,i.ZERO,i.ONE);break;case fc:i.blendFuncSeparate(i.ZERO,i.SRC_COLOR,i.ZERO,i.SRC_ALPHA);break;default:console.error("THREE.WebGLState: Invalid blending: ",B);break}else switch(B){case Fn:i.blendFuncSeparate(i.SRC_ALPHA,i.ONE_MINUS_SRC_ALPHA,i.ONE,i.ONE_MINUS_SRC_ALPHA);break;case pi:i.blendFunc(i.SRC_ALPHA,i.ONE);break;case dc:i.blendFuncSeparate(i.ZERO,i.ONE_MINUS_SRC_COLOR,i.ZERO,i.ONE);break;case fc:i.blendFunc(i.ZERO,i.SRC_COLOR);break;default:console.error("THREE.WebGLState: Invalid blending: ",B);break}y=null,_=null,C=null,A=null,E.set(0,0,0),b=0,m=B,v=ae}return}gt=gt||ft,pt=pt||q,Xt=Xt||Q,(ft!==f||gt!==M)&&(i.blendEquationSeparate(j[ft],j[gt]),f=ft,M=gt),(q!==y||Q!==_||pt!==C||Xt!==A)&&(i.blendFuncSeparate(ot[q],ot[Q],ot[pt],ot[Xt]),y=q,_=Q,C=pt,A=Xt),(Me.equals(E)===!1||Ne!==b)&&(i.blendColor(Me.r,Me.g,Me.b,Ne),E.copy(Me),b=Ne),m=B,v=!1}function Dt(B,ft){B.side===qe?Ut(i.CULL_FACE):ct(i.CULL_FACE);let q=B.side===ze;ft&&(q=!q),it(q),B.blending===Fn&&B.transparent===!1?D(ui):D(B.blending,B.blendEquation,B.blendSrc,B.blendDst,B.blendEquationAlpha,B.blendSrcAlpha,B.blendDstAlpha,B.blendColor,B.blendAlpha,B.premultipliedAlpha),a.setFunc(B.depthFunc),a.setTest(B.depthTest),a.setMask(B.depthWrite),r.setMask(B.colorWrite);let Q=B.stencilWrite;o.setTest(Q),Q&&(o.setMask(B.stencilWriteMask),o.setFunc(B.stencilFunc,B.stencilRef,B.stencilFuncMask),o.setOp(B.stencilFail,B.stencilZFail,B.stencilZPass)),Ot(B.polygonOffset,B.polygonOffsetFactor,B.polygonOffsetUnits),B.alphaToCoverage===!0?ct(i.SAMPLE_ALPHA_TO_COVERAGE):Ut(i.SAMPLE_ALPHA_TO_COVERAGE)}function it(B){S!==B&&(B?i.frontFace(i.CW):i.frontFace(i.CCW),S=B)}function Mt(B){B!==Tu?(ct(i.CULL_FACE),B!==w&&(B===uc?i.cullFace(i.BACK):B===Au?i.cullFace(i.FRONT):i.cullFace(i.FRONT_AND_BACK))):Ut(i.CULL_FACE),w=B}function ht(B){B!==P&&(N&&i.lineWidth(B),P=B)}function Ot(B,ft,q){B?(ct(i.POLYGON_OFFSET_FILL),(F!==ft||U!==q)&&(i.polygonOffset(ft,q),F=ft,U=q)):Ut(i.POLYGON_OFFSET_FILL)}function yt(B){B?ct(i.SCISSOR_TEST):Ut(i.SCISSOR_TEST)}function I(B){B===void 0&&(B=i.TEXTURE0+H-1),Y!==B&&(i.activeTexture(B),Y=B)}function T(B,ft,q){q===void 0&&(Y===null?q=i.TEXTURE0+H-1:q=Y);let Q=tt[q];Q===void 0&&(Q={type:void 0,texture:void 0},tt[q]=Q),(Q.type!==B||Q.texture!==ft)&&(Y!==q&&(i.activeTexture(q),Y=q),i.bindTexture(B,ft||xt[B]),Q.type=B,Q.texture=ft)}function V(){let B=tt[Y];B!==void 0&&B.type!==void 0&&(i.bindTexture(B.type,null),B.type=void 0,B.texture=void 0)}function Z(){try{i.compressedTexImage2D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function et(){try{i.compressedTexImage3D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function $(){try{i.texSubImage2D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function Rt(){try{i.texSubImage3D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function dt(){try{i.compressedTexSubImage2D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function vt(){try{i.compressedTexSubImage3D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function Qt(){try{i.texStorage2D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function rt(){try{i.texStorage3D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function Et(){try{i.texImage2D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function Bt(){try{i.texImage3D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function Vt(B){Nt.equals(B)===!1&&(i.scissor(B.x,B.y,B.z,B.w),Nt.copy(B))}function bt(B){X.equals(B)===!1&&(i.viewport(B.x,B.y,B.z,B.w),X.copy(B))}function te(B,ft){let q=c.get(ft);q===void 0&&(q=new WeakMap,c.set(ft,q));let Q=q.get(B);Q===void 0&&(Q=i.getUniformBlockIndex(ft,B.name),q.set(B,Q))}function Zt(B,ft){let Q=c.get(ft).get(B);l.get(ft)!==Q&&(i.uniformBlockBinding(ft,Q,B.__bindingPointIndex),l.set(ft,Q))}function fe(){i.disable(i.BLEND),i.disable(i.CULL_FACE),i.disable(i.DEPTH_TEST),i.disable(i.POLYGON_OFFSET_FILL),i.disable(i.SCISSOR_TEST),i.disable(i.STENCIL_TEST),i.disable(i.SAMPLE_ALPHA_TO_COVERAGE),i.blendEquation(i.FUNC_ADD),i.blendFunc(i.ONE,i.ZERO),i.blendFuncSeparate(i.ONE,i.ZERO,i.ONE,i.ZERO),i.blendColor(0,0,0,0),i.colorMask(!0,!0,!0,!0),i.clearColor(0,0,0,0),i.depthMask(!0),i.depthFunc(i.LESS),a.setReversed(!1),i.clearDepth(1),i.stencilMask(4294967295),i.stencilFunc(i.ALWAYS,0,4294967295),i.stencilOp(i.KEEP,i.KEEP,i.KEEP),i.clearStencil(0),i.cullFace(i.BACK),i.frontFace(i.CCW),i.polygonOffset(0,0),i.activeTexture(i.TEXTURE0),i.bindFramebuffer(i.FRAMEBUFFER,null),i.bindFramebuffer(i.DRAW_FRAMEBUFFER,null),i.bindFramebuffer(i.READ_FRAMEBUFFER,null),i.useProgram(null),i.lineWidth(1),i.scissor(0,0,i.canvas.width,i.canvas.height),i.viewport(0,0,i.canvas.width,i.canvas.height),h={},Y=null,tt={},u={},d=new WeakMap,p=[],g=null,x=!1,m=null,f=null,y=null,_=null,M=null,C=null,A=null,E=new Gt(0,0,0),b=0,v=!1,S=null,w=null,P=null,F=null,U=null,Nt.set(0,0,i.canvas.width,i.canvas.height),X.set(0,0,i.canvas.width,i.canvas.height),r.reset(),a.reset(),o.reset()}return{buffers:{color:r,depth:a,stencil:o},enable:ct,disable:Ut,bindFramebuffer:kt,drawBuffers:At,useProgram:Jt,setBlending:D,setMaterial:Dt,setFlipSided:it,setCullFace:Mt,setLineWidth:ht,setPolygonOffset:Ot,setScissorTest:yt,activeTexture:I,bindTexture:T,unbindTexture:V,compressedTexImage2D:Z,compressedTexImage3D:et,texImage2D:Et,texImage3D:Bt,updateUBOMapping:te,uniformBlockBinding:Zt,texStorage2D:Qt,texStorage3D:rt,texSubImage2D:$,texSubImage3D:Rt,compressedTexSubImage2D:dt,compressedTexSubImage3D:vt,scissor:Vt,viewport:bt,reset:fe}}function hh(i,t,e,n){let s=_g(n);switch(e){case Th:return i*t;case Rh:return i*t;case Ch:return i*t*2;case Ll:return i*t/s.components*s.byteLength;case Ul:return i*t/s.components*s.byteLength;case Ih:return i*t*2/s.components*s.byteLength;case Nl:return i*t*2/s.components*s.byteLength;case Ah:return i*t*3/s.components*s.byteLength;case bn:return i*t*4/s.components*s.byteLength;case Ol:return i*t*4/s.components*s.byteLength;case Gr:case Wr:return Math.floor((i+3)/4)*Math.floor((t+3)/4)*8;case Kr:case Xr:return Math.floor((i+3)/4)*Math.floor((t+3)/4)*16;case yo:case Mo:return Math.max(i,16)*Math.max(t,8)/4;case xo:case vo:return Math.max(i,8)*Math.max(t,8)/2;case So:case Eo:return Math.floor((i+3)/4)*Math.floor((t+3)/4)*8;case bo:return Math.floor((i+3)/4)*Math.floor((t+3)/4)*16;case wo:return Math.floor((i+3)/4)*Math.floor((t+3)/4)*16;case To:return Math.floor((i+4)/5)*Math.floor((t+3)/4)*16;case Ao:return Math.floor((i+4)/5)*Math.floor((t+4)/5)*16;case Ro:return Math.floor((i+5)/6)*Math.floor((t+4)/5)*16;case Co:return Math.floor((i+5)/6)*Math.floor((t+5)/6)*16;case Io:return Math.floor((i+7)/8)*Math.floor((t+4)/5)*16;case Po:return Math.floor((i+7)/8)*Math.floor((t+5)/6)*16;case Fo:return Math.floor((i+7)/8)*Math.floor((t+7)/8)*16;case Do:return Math.floor((i+9)/10)*Math.floor((t+4)/5)*16;case Lo:return Math.floor((i+9)/10)*Math.floor((t+5)/6)*16;case Uo:return Math.floor((i+9)/10)*Math.floor((t+7)/8)*16;case No:return Math.floor((i+9)/10)*Math.floor((t+9)/10)*16;case Oo:return Math.floor((i+11)/12)*Math.floor((t+9)/10)*16;case Bo:return Math.floor((i+11)/12)*Math.floor((t+11)/12)*16;case qr:case zo:case ko:return Math.ceil(i/4)*Math.ceil(t/4)*16;case Ph:case Ho:return Math.ceil(i/4)*Math.ceil(t/4)*8;case Vo:case Go:return Math.ceil(i/4)*Math.ceil(t/4)*16}throw new Error(`Unable to determine texture byte length for ${e} format.`)}function _g(i){switch(i){case Zn:case Eh:return{byteLength:1,components:1};case Js:case bh:case or:return{byteLength:2,components:1};case Fl:case Dl:return{byteLength:2,components:4};case Di:case Pl:case Pn:return{byteLength:4,components:1};case wh:return{byteLength:4,components:3}}throw new Error(`Unknown texture type ${i}.`)}function xg(i,t,e,n,s,r,a){let o=t.has("WEBGL_multisampled_render_to_texture")?t.get("WEBGL_multisampled_render_to_texture"):null,l=typeof navigator=="undefined"?!1:/OculusBrowser/g.test(navigator.userAgent),c=new at,h=new WeakMap,u,d=new WeakMap,p=!1;try{p=typeof OffscreenCanvas!="undefined"&&new OffscreenCanvas(1,1).getContext("2d")!==null}catch{}function g(I,T){return p?new OffscreenCanvas(I,T):$r("canvas")}function x(I,T,V){let Z=1,et=yt(I);if((et.width>V||et.height>V)&&(Z=V/Math.max(et.width,et.height)),Z<1)if(typeof HTMLImageElement!="undefined"&&I instanceof HTMLImageElement||typeof HTMLCanvasElement!="undefined"&&I instanceof HTMLCanvasElement||typeof ImageBitmap!="undefined"&&I instanceof ImageBitmap||typeof VideoFrame!="undefined"&&I instanceof VideoFrame){let $=Math.floor(Z*et.width),Rt=Math.floor(Z*et.height);u===void 0&&(u=g($,Rt));let dt=T?g($,Rt):u;return dt.width=$,dt.height=Rt,dt.getContext("2d").drawImage(I,0,0,$,Rt),console.warn("THREE.WebGLRenderer: Texture has been resized from ("+et.width+"x"+et.height+") to ("+$+"x"+Rt+")."),dt}else return"data"in I&&console.warn("THREE.WebGLRenderer: Image in DataTexture is too big ("+et.width+"x"+et.height+")."),I;return I}function m(I){return I.generateMipmaps}function f(I){i.generateMipmap(I)}function y(I){return I.isWebGLCubeRenderTarget?i.TEXTURE_CUBE_MAP:I.isWebGL3DRenderTarget?i.TEXTURE_3D:I.isWebGLArrayRenderTarget||I.isCompressedArrayTexture?i.TEXTURE_2D_ARRAY:i.TEXTURE_2D}function _(I,T,V,Z,et=!1){if(I!==null){if(i[I]!==void 0)return i[I];console.warn("THREE.WebGLRenderer: Attempt to use non-existing WebGL internal format '"+I+"'")}let $=T;if(T===i.RED&&(V===i.FLOAT&&($=i.R32F),V===i.HALF_FLOAT&&($=i.R16F),V===i.UNSIGNED_BYTE&&($=i.R8)),T===i.RED_INTEGER&&(V===i.UNSIGNED_BYTE&&($=i.R8UI),V===i.UNSIGNED_SHORT&&($=i.R16UI),V===i.UNSIGNED_INT&&($=i.R32UI),V===i.BYTE&&($=i.R8I),V===i.SHORT&&($=i.R16I),V===i.INT&&($=i.R32I)),T===i.RG&&(V===i.FLOAT&&($=i.RG32F),V===i.HALF_FLOAT&&($=i.RG16F),V===i.UNSIGNED_BYTE&&($=i.RG8)),T===i.RG_INTEGER&&(V===i.UNSIGNED_BYTE&&($=i.RG8UI),V===i.UNSIGNED_SHORT&&($=i.RG16UI),V===i.UNSIGNED_INT&&($=i.RG32UI),V===i.BYTE&&($=i.RG8I),V===i.SHORT&&($=i.RG16I),V===i.INT&&($=i.RG32I)),T===i.RGB_INTEGER&&(V===i.UNSIGNED_BYTE&&($=i.RGB8UI),V===i.UNSIGNED_SHORT&&($=i.RGB16UI),V===i.UNSIGNED_INT&&($=i.RGB32UI),V===i.BYTE&&($=i.RGB8I),V===i.SHORT&&($=i.RGB16I),V===i.INT&&($=i.RGB32I)),T===i.RGBA_INTEGER&&(V===i.UNSIGNED_BYTE&&($=i.RGBA8UI),V===i.UNSIGNED_SHORT&&($=i.RGBA16UI),V===i.UNSIGNED_INT&&($=i.RGBA32UI),V===i.BYTE&&($=i.RGBA8I),V===i.SHORT&&($=i.RGBA16I),V===i.INT&&($=i.RGBA32I)),T===i.RGB&&V===i.UNSIGNED_INT_5_9_9_9_REV&&($=i.RGB9_E5),T===i.RGBA){let Rt=et?ga:ne.getTransfer(Z);V===i.FLOAT&&($=i.RGBA32F),V===i.HALF_FLOAT&&($=i.RGBA16F),V===i.UNSIGNED_BYTE&&($=Rt===le?i.SRGB8_ALPHA8:i.RGBA8),V===i.UNSIGNED_SHORT_4_4_4_4&&($=i.RGBA4),V===i.UNSIGNED_SHORT_5_5_5_1&&($=i.RGB5_A1)}return($===i.R16F||$===i.R32F||$===i.RG16F||$===i.RG32F||$===i.RGBA16F||$===i.RGBA32F)&&t.get("EXT_color_buffer_float"),$}function M(I,T){let V;return I?T===null||T===Di||T===gs?V=i.DEPTH24_STENCIL8:T===Pn?V=i.DEPTH32F_STENCIL8:T===Js&&(V=i.DEPTH24_STENCIL8,console.warn("DepthTexture: 16 bit depth attachment is not supported with stencil. Using 24-bit attachment.")):T===null||T===Di||T===gs?V=i.DEPTH_COMPONENT24:T===Pn?V=i.DEPTH_COMPONENT32F:T===Js&&(V=i.DEPTH_COMPONENT16),V}function C(I,T){return m(I)===!0||I.isFramebufferTexture&&I.minFilter!==an&&I.minFilter!==fn?Math.log2(Math.max(T.width,T.height))+1:I.mipmaps!==void 0&&I.mipmaps.length>0?I.mipmaps.length:I.isCompressedTexture&&Array.isArray(I.image)?T.mipmaps.length:1}function A(I){let T=I.target;T.removeEventListener("dispose",A),b(T),T.isVideoTexture&&h.delete(T)}function E(I){let T=I.target;T.removeEventListener("dispose",E),S(T)}function b(I){let T=n.get(I);if(T.__webglInit===void 0)return;let V=I.source,Z=d.get(V);if(Z){let et=Z[T.__cacheKey];et.usedTimes--,et.usedTimes===0&&v(I),Object.keys(Z).length===0&&d.delete(V)}n.remove(I)}function v(I){let T=n.get(I);i.deleteTexture(T.__webglTexture);let V=I.source,Z=d.get(V);delete Z[T.__cacheKey],a.memory.textures--}function S(I){let T=n.get(I);if(I.depthTexture&&(I.depthTexture.dispose(),n.remove(I.depthTexture)),I.isWebGLCubeRenderTarget)for(let Z=0;Z<6;Z++){if(Array.isArray(T.__webglFramebuffer[Z]))for(let et=0;et<T.__webglFramebuffer[Z].length;et++)i.deleteFramebuffer(T.__webglFramebuffer[Z][et]);else i.deleteFramebuffer(T.__webglFramebuffer[Z]);T.__webglDepthbuffer&&i.deleteRenderbuffer(T.__webglDepthbuffer[Z])}else{if(Array.isArray(T.__webglFramebuffer))for(let Z=0;Z<T.__webglFramebuffer.length;Z++)i.deleteFramebuffer(T.__webglFramebuffer[Z]);else i.deleteFramebuffer(T.__webglFramebuffer);if(T.__webglDepthbuffer&&i.deleteRenderbuffer(T.__webglDepthbuffer),T.__webglMultisampledFramebuffer&&i.deleteFramebuffer(T.__webglMultisampledFramebuffer),T.__webglColorRenderbuffer)for(let Z=0;Z<T.__webglColorRenderbuffer.length;Z++)T.__webglColorRenderbuffer[Z]&&i.deleteRenderbuffer(T.__webglColorRenderbuffer[Z]);T.__webglDepthRenderbuffer&&i.deleteRenderbuffer(T.__webglDepthRenderbuffer)}let V=I.textures;for(let Z=0,et=V.length;Z<et;Z++){let $=n.get(V[Z]);$.__webglTexture&&(i.deleteTexture($.__webglTexture),a.memory.textures--),n.remove(V[Z])}n.remove(I)}let w=0;function P(){w=0}function F(){let I=w;return I>=s.maxTextures&&console.warn("THREE.WebGLTextures: Trying to use "+I+" texture units while this GPU supports only "+s.maxTextures),w+=1,I}function U(I){let T=[];return T.push(I.wrapS),T.push(I.wrapT),T.push(I.wrapR||0),T.push(I.magFilter),T.push(I.minFilter),T.push(I.anisotropy),T.push(I.internalFormat),T.push(I.format),T.push(I.type),T.push(I.generateMipmaps),T.push(I.premultiplyAlpha),T.push(I.flipY),T.push(I.unpackAlignment),T.push(I.colorSpace),T.join()}function H(I,T){let V=n.get(I);if(I.isVideoTexture&&ht(I),I.isRenderTargetTexture===!1&&I.version>0&&V.__version!==I.version){let Z=I.image;if(Z===null)console.warn("THREE.WebGLRenderer: Texture marked for update but no image data found.");else if(Z.complete===!1)console.warn("THREE.WebGLRenderer: Texture marked for update but image is incomplete");else{X(V,I,T);return}}e.bindTexture(i.TEXTURE_2D,V.__webglTexture,i.TEXTURE0+T)}function N(I,T){let V=n.get(I);if(I.version>0&&V.__version!==I.version){X(V,I,T);return}e.bindTexture(i.TEXTURE_2D_ARRAY,V.__webglTexture,i.TEXTURE0+T)}function K(I,T){let V=n.get(I);if(I.version>0&&V.__version!==I.version){X(V,I,T);return}e.bindTexture(i.TEXTURE_3D,V.__webglTexture,i.TEXTURE0+T)}function O(I,T){let V=n.get(I);if(I.version>0&&V.__version!==I.version){st(V,I,T);return}e.bindTexture(i.TEXTURE_CUBE_MAP,V.__webglTexture,i.TEXTURE0+T)}let Y={[mi]:i.REPEAT,[Kn]:i.CLAMP_TO_EDGE,[_o]:i.MIRRORED_REPEAT},tt={[an]:i.NEAREST,[ed]:i.NEAREST_MIPMAP_NEAREST,[_r]:i.NEAREST_MIPMAP_LINEAR,[fn]:i.LINEAR,[Ta]:i.LINEAR_MIPMAP_NEAREST,[Xn]:i.LINEAR_MIPMAP_LINEAR},nt={[rd]:i.NEVER,[ud]:i.ALWAYS,[ad]:i.LESS,[Dh]:i.LEQUAL,[od]:i.EQUAL,[hd]:i.GEQUAL,[ld]:i.GREATER,[cd]:i.NOTEQUAL};function Tt(I,T){if(T.type===Pn&&t.has("OES_texture_float_linear")===!1&&(T.magFilter===fn||T.magFilter===Ta||T.magFilter===_r||T.magFilter===Xn||T.minFilter===fn||T.minFilter===Ta||T.minFilter===_r||T.minFilter===Xn)&&console.warn("THREE.WebGLRenderer: Unable to use linear filtering with floating point textures. OES_texture_float_linear not supported on this device."),i.texParameteri(I,i.TEXTURE_WRAP_S,Y[T.wrapS]),i.texParameteri(I,i.TEXTURE_WRAP_T,Y[T.wrapT]),(I===i.TEXTURE_3D||I===i.TEXTURE_2D_ARRAY)&&i.texParameteri(I,i.TEXTURE_WRAP_R,Y[T.wrapR]),i.texParameteri(I,i.TEXTURE_MAG_FILTER,tt[T.magFilter]),i.texParameteri(I,i.TEXTURE_MIN_FILTER,tt[T.minFilter]),T.compareFunction&&(i.texParameteri(I,i.TEXTURE_COMPARE_MODE,i.COMPARE_REF_TO_TEXTURE),i.texParameteri(I,i.TEXTURE_COMPARE_FUNC,nt[T.compareFunction])),t.has("EXT_texture_filter_anisotropic")===!0){if(T.magFilter===an||T.minFilter!==_r&&T.minFilter!==Xn||T.type===Pn&&t.has("OES_texture_float_linear")===!1)return;if(T.anisotropy>1||n.get(T).__currentAnisotropy){let V=t.get("EXT_texture_filter_anisotropic");i.texParameterf(I,V.TEXTURE_MAX_ANISOTROPY_EXT,Math.min(T.anisotropy,s.getMaxAnisotropy())),n.get(T).__currentAnisotropy=T.anisotropy}}}function Nt(I,T){let V=!1;I.__webglInit===void 0&&(I.__webglInit=!0,T.addEventListener("dispose",A));let Z=T.source,et=d.get(Z);et===void 0&&(et={},d.set(Z,et));let $=U(T);if($!==I.__cacheKey){et[$]===void 0&&(et[$]={texture:i.createTexture(),usedTimes:0},a.memory.textures++,V=!0),et[$].usedTimes++;let Rt=et[I.__cacheKey];Rt!==void 0&&(et[I.__cacheKey].usedTimes--,Rt.usedTimes===0&&v(T)),I.__cacheKey=$,I.__webglTexture=et[$].texture}return V}function X(I,T,V){let Z=i.TEXTURE_2D;(T.isDataArrayTexture||T.isCompressedArrayTexture)&&(Z=i.TEXTURE_2D_ARRAY),T.isData3DTexture&&(Z=i.TEXTURE_3D);let et=Nt(I,T),$=T.source;e.bindTexture(Z,I.__webglTexture,i.TEXTURE0+V);let Rt=n.get($);if($.version!==Rt.__version||et===!0){e.activeTexture(i.TEXTURE0+V);let dt=ne.getPrimaries(ne.workingColorSpace),vt=T.colorSpace===hi?null:ne.getPrimaries(T.colorSpace),Qt=T.colorSpace===hi||dt===vt?i.NONE:i.BROWSER_DEFAULT_WEBGL;i.pixelStorei(i.UNPACK_FLIP_Y_WEBGL,T.flipY),i.pixelStorei(i.UNPACK_PREMULTIPLY_ALPHA_WEBGL,T.premultiplyAlpha),i.pixelStorei(i.UNPACK_ALIGNMENT,T.unpackAlignment),i.pixelStorei(i.UNPACK_COLORSPACE_CONVERSION_WEBGL,Qt);let rt=x(T.image,!1,s.maxTextureSize);rt=Ot(T,rt);let Et=r.convert(T.format,T.colorSpace),Bt=r.convert(T.type),Vt=_(T.internalFormat,Et,Bt,T.colorSpace,T.isVideoTexture);Tt(Z,T);let bt,te=T.mipmaps,Zt=T.isVideoTexture!==!0,fe=Rt.__version===void 0||et===!0,B=$.dataReady,ft=C(T,rt);if(T.isDepthTexture)Vt=M(T.format===_s,T.type),fe&&(Zt?e.texStorage2D(i.TEXTURE_2D,1,Vt,rt.width,rt.height):e.texImage2D(i.TEXTURE_2D,0,Vt,rt.width,rt.height,0,Et,Bt,null));else if(T.isDataTexture)if(te.length>0){Zt&&fe&&e.texStorage2D(i.TEXTURE_2D,ft,Vt,te[0].width,te[0].height);for(let q=0,Q=te.length;q<Q;q++)bt=te[q],Zt?B&&e.texSubImage2D(i.TEXTURE_2D,q,0,0,bt.width,bt.height,Et,Bt,bt.data):e.texImage2D(i.TEXTURE_2D,q,Vt,bt.width,bt.height,0,Et,Bt,bt.data);T.generateMipmaps=!1}else Zt?(fe&&e.texStorage2D(i.TEXTURE_2D,ft,Vt,rt.width,rt.height),B&&e.texSubImage2D(i.TEXTURE_2D,0,0,0,rt.width,rt.height,Et,Bt,rt.data)):e.texImage2D(i.TEXTURE_2D,0,Vt,rt.width,rt.height,0,Et,Bt,rt.data);else if(T.isCompressedTexture)if(T.isCompressedArrayTexture){Zt&&fe&&e.texStorage3D(i.TEXTURE_2D_ARRAY,ft,Vt,te[0].width,te[0].height,rt.depth);for(let q=0,Q=te.length;q<Q;q++)if(bt=te[q],T.format!==bn)if(Et!==null)if(Zt){if(B)if(T.layerUpdates.size>0){let gt=hh(bt.width,bt.height,T.format,T.type);for(let pt of T.layerUpdates){let Xt=bt.data.subarray(pt*gt/bt.data.BYTES_PER_ELEMENT,(pt+1)*gt/bt.data.BYTES_PER_ELEMENT);e.compressedTexSubImage3D(i.TEXTURE_2D_ARRAY,q,0,0,pt,bt.width,bt.height,1,Et,Xt)}T.clearLayerUpdates()}else e.compressedTexSubImage3D(i.TEXTURE_2D_ARRAY,q,0,0,0,bt.width,bt.height,rt.depth,Et,bt.data)}else e.compressedTexImage3D(i.TEXTURE_2D_ARRAY,q,Vt,bt.width,bt.height,rt.depth,0,bt.data,0,0);else console.warn("THREE.WebGLRenderer: Attempt to load unsupported compressed texture format in .uploadTexture()");else Zt?B&&e.texSubImage3D(i.TEXTURE_2D_ARRAY,q,0,0,0,bt.width,bt.height,rt.depth,Et,Bt,bt.data):e.texImage3D(i.TEXTURE_2D_ARRAY,q,Vt,bt.width,bt.height,rt.depth,0,Et,Bt,bt.data)}else{Zt&&fe&&e.texStorage2D(i.TEXTURE_2D,ft,Vt,te[0].width,te[0].height);for(let q=0,Q=te.length;q<Q;q++)bt=te[q],T.format!==bn?Et!==null?Zt?B&&e.compressedTexSubImage2D(i.TEXTURE_2D,q,0,0,bt.width,bt.height,Et,bt.data):e.compressedTexImage2D(i.TEXTURE_2D,q,Vt,bt.width,bt.height,0,bt.data):console.warn("THREE.WebGLRenderer: Attempt to load unsupported compressed texture format in .uploadTexture()"):Zt?B&&e.texSubImage2D(i.TEXTURE_2D,q,0,0,bt.width,bt.height,Et,Bt,bt.data):e.texImage2D(i.TEXTURE_2D,q,Vt,bt.width,bt.height,0,Et,Bt,bt.data)}else if(T.isDataArrayTexture)if(Zt){if(fe&&e.texStorage3D(i.TEXTURE_2D_ARRAY,ft,Vt,rt.width,rt.height,rt.depth),B)if(T.layerUpdates.size>0){let q=hh(rt.width,rt.height,T.format,T.type);for(let Q of T.layerUpdates){let gt=rt.data.subarray(Q*q/rt.data.BYTES_PER_ELEMENT,(Q+1)*q/rt.data.BYTES_PER_ELEMENT);e.texSubImage3D(i.TEXTURE_2D_ARRAY,0,0,0,Q,rt.width,rt.height,1,Et,Bt,gt)}T.clearLayerUpdates()}else e.texSubImage3D(i.TEXTURE_2D_ARRAY,0,0,0,0,rt.width,rt.height,rt.depth,Et,Bt,rt.data)}else e.texImage3D(i.TEXTURE_2D_ARRAY,0,Vt,rt.width,rt.height,rt.depth,0,Et,Bt,rt.data);else if(T.isData3DTexture)Zt?(fe&&e.texStorage3D(i.TEXTURE_3D,ft,Vt,rt.width,rt.height,rt.depth),B&&e.texSubImage3D(i.TEXTURE_3D,0,0,0,0,rt.width,rt.height,rt.depth,Et,Bt,rt.data)):e.texImage3D(i.TEXTURE_3D,0,Vt,rt.width,rt.height,rt.depth,0,Et,Bt,rt.data);else if(T.isFramebufferTexture){if(fe)if(Zt)e.texStorage2D(i.TEXTURE_2D,ft,Vt,rt.width,rt.height);else{let q=rt.width,Q=rt.height;for(let gt=0;gt<ft;gt++)e.texImage2D(i.TEXTURE_2D,gt,Vt,q,Q,0,Et,Bt,null),q>>=1,Q>>=1}}else if(te.length>0){if(Zt&&fe){let q=yt(te[0]);e.texStorage2D(i.TEXTURE_2D,ft,Vt,q.width,q.height)}for(let q=0,Q=te.length;q<Q;q++)bt=te[q],Zt?B&&e.texSubImage2D(i.TEXTURE_2D,q,0,0,Et,Bt,bt):e.texImage2D(i.TEXTURE_2D,q,Vt,Et,Bt,bt);T.generateMipmaps=!1}else if(Zt){if(fe){let q=yt(rt);e.texStorage2D(i.TEXTURE_2D,ft,Vt,q.width,q.height)}B&&e.texSubImage2D(i.TEXTURE_2D,0,0,0,Et,Bt,rt)}else e.texImage2D(i.TEXTURE_2D,0,Vt,Et,Bt,rt);m(T)&&f(Z),Rt.__version=$.version,T.onUpdate&&T.onUpdate(T)}I.__version=T.version}function st(I,T,V){if(T.image.length!==6)return;let Z=Nt(I,T),et=T.source;e.bindTexture(i.TEXTURE_CUBE_MAP,I.__webglTexture,i.TEXTURE0+V);let $=n.get(et);if(et.version!==$.__version||Z===!0){e.activeTexture(i.TEXTURE0+V);let Rt=ne.getPrimaries(ne.workingColorSpace),dt=T.colorSpace===hi?null:ne.getPrimaries(T.colorSpace),vt=T.colorSpace===hi||Rt===dt?i.NONE:i.BROWSER_DEFAULT_WEBGL;i.pixelStorei(i.UNPACK_FLIP_Y_WEBGL,T.flipY),i.pixelStorei(i.UNPACK_PREMULTIPLY_ALPHA_WEBGL,T.premultiplyAlpha),i.pixelStorei(i.UNPACK_ALIGNMENT,T.unpackAlignment),i.pixelStorei(i.UNPACK_COLORSPACE_CONVERSION_WEBGL,vt);let Qt=T.isCompressedTexture||T.image[0].isCompressedTexture,rt=T.image[0]&&T.image[0].isDataTexture,Et=[];for(let Q=0;Q<6;Q++)!Qt&&!rt?Et[Q]=x(T.image[Q],!0,s.maxCubemapSize):Et[Q]=rt?T.image[Q].image:T.image[Q],Et[Q]=Ot(T,Et[Q]);let Bt=Et[0],Vt=r.convert(T.format,T.colorSpace),bt=r.convert(T.type),te=_(T.internalFormat,Vt,bt,T.colorSpace),Zt=T.isVideoTexture!==!0,fe=$.__version===void 0||Z===!0,B=et.dataReady,ft=C(T,Bt);Tt(i.TEXTURE_CUBE_MAP,T);let q;if(Qt){Zt&&fe&&e.texStorage2D(i.TEXTURE_CUBE_MAP,ft,te,Bt.width,Bt.height);for(let Q=0;Q<6;Q++){q=Et[Q].mipmaps;for(let gt=0;gt<q.length;gt++){let pt=q[gt];T.format!==bn?Vt!==null?Zt?B&&e.compressedTexSubImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+Q,gt,0,0,pt.width,pt.height,Vt,pt.data):e.compressedTexImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+Q,gt,te,pt.width,pt.height,0,pt.data):console.warn("THREE.WebGLRenderer: Attempt to load unsupported compressed texture format in .setTextureCube()"):Zt?B&&e.texSubImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+Q,gt,0,0,pt.width,pt.height,Vt,bt,pt.data):e.texImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+Q,gt,te,pt.width,pt.height,0,Vt,bt,pt.data)}}}else{if(q=T.mipmaps,Zt&&fe){q.length>0&&ft++;let Q=yt(Et[0]);e.texStorage2D(i.TEXTURE_CUBE_MAP,ft,te,Q.width,Q.height)}for(let Q=0;Q<6;Q++)if(rt){Zt?B&&e.texSubImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+Q,0,0,0,Et[Q].width,Et[Q].height,Vt,bt,Et[Q].data):e.texImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+Q,0,te,Et[Q].width,Et[Q].height,0,Vt,bt,Et[Q].data);for(let gt=0;gt<q.length;gt++){let Xt=q[gt].image[Q].image;Zt?B&&e.texSubImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+Q,gt+1,0,0,Xt.width,Xt.height,Vt,bt,Xt.data):e.texImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+Q,gt+1,te,Xt.width,Xt.height,0,Vt,bt,Xt.data)}}else{Zt?B&&e.texSubImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+Q,0,0,0,Vt,bt,Et[Q]):e.texImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+Q,0,te,Vt,bt,Et[Q]);for(let gt=0;gt<q.length;gt++){let pt=q[gt];Zt?B&&e.texSubImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+Q,gt+1,0,0,Vt,bt,pt.image[Q]):e.texImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+Q,gt+1,te,Vt,bt,pt.image[Q])}}}m(T)&&f(i.TEXTURE_CUBE_MAP),$.__version=et.version,T.onUpdate&&T.onUpdate(T)}I.__version=T.version}function xt(I,T,V,Z,et,$){let Rt=r.convert(V.format,V.colorSpace),dt=r.convert(V.type),vt=_(V.internalFormat,Rt,dt,V.colorSpace),Qt=n.get(T),rt=n.get(V);if(rt.__renderTarget=T,!Qt.__hasExternalTextures){let Et=Math.max(1,T.width>>$),Bt=Math.max(1,T.height>>$);et===i.TEXTURE_3D||et===i.TEXTURE_2D_ARRAY?e.texImage3D(et,$,vt,Et,Bt,T.depth,0,Rt,dt,null):e.texImage2D(et,$,vt,Et,Bt,0,Rt,dt,null)}e.bindFramebuffer(i.FRAMEBUFFER,I),Mt(T)?o.framebufferTexture2DMultisampleEXT(i.FRAMEBUFFER,Z,et,rt.__webglTexture,0,it(T)):(et===i.TEXTURE_2D||et>=i.TEXTURE_CUBE_MAP_POSITIVE_X&&et<=i.TEXTURE_CUBE_MAP_NEGATIVE_Z)&&i.framebufferTexture2D(i.FRAMEBUFFER,Z,et,rt.__webglTexture,$),e.bindFramebuffer(i.FRAMEBUFFER,null)}function ct(I,T,V){if(i.bindRenderbuffer(i.RENDERBUFFER,I),T.depthBuffer){let Z=T.depthTexture,et=Z&&Z.isDepthTexture?Z.type:null,$=M(T.stencilBuffer,et),Rt=T.stencilBuffer?i.DEPTH_STENCIL_ATTACHMENT:i.DEPTH_ATTACHMENT,dt=it(T);Mt(T)?o.renderbufferStorageMultisampleEXT(i.RENDERBUFFER,dt,$,T.width,T.height):V?i.renderbufferStorageMultisample(i.RENDERBUFFER,dt,$,T.width,T.height):i.renderbufferStorage(i.RENDERBUFFER,$,T.width,T.height),i.framebufferRenderbuffer(i.FRAMEBUFFER,Rt,i.RENDERBUFFER,I)}else{let Z=T.textures;for(let et=0;et<Z.length;et++){let $=Z[et],Rt=r.convert($.format,$.colorSpace),dt=r.convert($.type),vt=_($.internalFormat,Rt,dt,$.colorSpace),Qt=it(T);V&&Mt(T)===!1?i.renderbufferStorageMultisample(i.RENDERBUFFER,Qt,vt,T.width,T.height):Mt(T)?o.renderbufferStorageMultisampleEXT(i.RENDERBUFFER,Qt,vt,T.width,T.height):i.renderbufferStorage(i.RENDERBUFFER,vt,T.width,T.height)}}i.bindRenderbuffer(i.RENDERBUFFER,null)}function Ut(I,T){if(T&&T.isWebGLCubeRenderTarget)throw new Error("Depth Texture with cube render targets is not supported");if(e.bindFramebuffer(i.FRAMEBUFFER,I),!(T.depthTexture&&T.depthTexture.isDepthTexture))throw new Error("renderTarget.depthTexture must be an instance of THREE.DepthTexture");let Z=n.get(T.depthTexture);Z.__renderTarget=T,(!Z.__webglTexture||T.depthTexture.image.width!==T.width||T.depthTexture.image.height!==T.height)&&(T.depthTexture.image.width=T.width,T.depthTexture.image.height=T.height,T.depthTexture.needsUpdate=!0),H(T.depthTexture,0);let et=Z.__webglTexture,$=it(T);if(T.depthTexture.format===hs)Mt(T)?o.framebufferTexture2DMultisampleEXT(i.FRAMEBUFFER,i.DEPTH_ATTACHMENT,i.TEXTURE_2D,et,0,$):i.framebufferTexture2D(i.FRAMEBUFFER,i.DEPTH_ATTACHMENT,i.TEXTURE_2D,et,0);else if(T.depthTexture.format===_s)Mt(T)?o.framebufferTexture2DMultisampleEXT(i.FRAMEBUFFER,i.DEPTH_STENCIL_ATTACHMENT,i.TEXTURE_2D,et,0,$):i.framebufferTexture2D(i.FRAMEBUFFER,i.DEPTH_STENCIL_ATTACHMENT,i.TEXTURE_2D,et,0);else throw new Error("Unknown depthTexture format")}function kt(I){let T=n.get(I),V=I.isWebGLCubeRenderTarget===!0;if(T.__boundDepthTexture!==I.depthTexture){let Z=I.depthTexture;if(T.__depthDisposeCallback&&T.__depthDisposeCallback(),Z){let et=()=>{delete T.__boundDepthTexture,delete T.__depthDisposeCallback,Z.removeEventListener("dispose",et)};Z.addEventListener("dispose",et),T.__depthDisposeCallback=et}T.__boundDepthTexture=Z}if(I.depthTexture&&!T.__autoAllocateDepthBuffer){if(V)throw new Error("target.depthTexture not supported in Cube render targets");Ut(T.__webglFramebuffer,I)}else if(V){T.__webglDepthbuffer=[];for(let Z=0;Z<6;Z++)if(e.bindFramebuffer(i.FRAMEBUFFER,T.__webglFramebuffer[Z]),T.__webglDepthbuffer[Z]===void 0)T.__webglDepthbuffer[Z]=i.createRenderbuffer(),ct(T.__webglDepthbuffer[Z],I,!1);else{let et=I.stencilBuffer?i.DEPTH_STENCIL_ATTACHMENT:i.DEPTH_ATTACHMENT,$=T.__webglDepthbuffer[Z];i.bindRenderbuffer(i.RENDERBUFFER,$),i.framebufferRenderbuffer(i.FRAMEBUFFER,et,i.RENDERBUFFER,$)}}else if(e.bindFramebuffer(i.FRAMEBUFFER,T.__webglFramebuffer),T.__webglDepthbuffer===void 0)T.__webglDepthbuffer=i.createRenderbuffer(),ct(T.__webglDepthbuffer,I,!1);else{let Z=I.stencilBuffer?i.DEPTH_STENCIL_ATTACHMENT:i.DEPTH_ATTACHMENT,et=T.__webglDepthbuffer;i.bindRenderbuffer(i.RENDERBUFFER,et),i.framebufferRenderbuffer(i.FRAMEBUFFER,Z,i.RENDERBUFFER,et)}e.bindFramebuffer(i.FRAMEBUFFER,null)}function At(I,T,V){let Z=n.get(I);T!==void 0&&xt(Z.__webglFramebuffer,I,I.texture,i.COLOR_ATTACHMENT0,i.TEXTURE_2D,0),V!==void 0&&kt(I)}function Jt(I){let T=I.texture,V=n.get(I),Z=n.get(T);I.addEventListener("dispose",E);let et=I.textures,$=I.isWebGLCubeRenderTarget===!0,Rt=et.length>1;if(Rt||(Z.__webglTexture===void 0&&(Z.__webglTexture=i.createTexture()),Z.__version=T.version,a.memory.textures++),$){V.__webglFramebuffer=[];for(let dt=0;dt<6;dt++)if(T.mipmaps&&T.mipmaps.length>0){V.__webglFramebuffer[dt]=[];for(let vt=0;vt<T.mipmaps.length;vt++)V.__webglFramebuffer[dt][vt]=i.createFramebuffer()}else V.__webglFramebuffer[dt]=i.createFramebuffer()}else{if(T.mipmaps&&T.mipmaps.length>0){V.__webglFramebuffer=[];for(let dt=0;dt<T.mipmaps.length;dt++)V.__webglFramebuffer[dt]=i.createFramebuffer()}else V.__webglFramebuffer=i.createFramebuffer();if(Rt)for(let dt=0,vt=et.length;dt<vt;dt++){let Qt=n.get(et[dt]);Qt.__webglTexture===void 0&&(Qt.__webglTexture=i.createTexture(),a.memory.textures++)}if(I.samples>0&&Mt(I)===!1){V.__webglMultisampledFramebuffer=i.createFramebuffer(),V.__webglColorRenderbuffer=[],e.bindFramebuffer(i.FRAMEBUFFER,V.__webglMultisampledFramebuffer);for(let dt=0;dt<et.length;dt++){let vt=et[dt];V.__webglColorRenderbuffer[dt]=i.createRenderbuffer(),i.bindRenderbuffer(i.RENDERBUFFER,V.__webglColorRenderbuffer[dt]);let Qt=r.convert(vt.format,vt.colorSpace),rt=r.convert(vt.type),Et=_(vt.internalFormat,Qt,rt,vt.colorSpace,I.isXRRenderTarget===!0),Bt=it(I);i.renderbufferStorageMultisample(i.RENDERBUFFER,Bt,Et,I.width,I.height),i.framebufferRenderbuffer(i.FRAMEBUFFER,i.COLOR_ATTACHMENT0+dt,i.RENDERBUFFER,V.__webglColorRenderbuffer[dt])}i.bindRenderbuffer(i.RENDERBUFFER,null),I.depthBuffer&&(V.__webglDepthRenderbuffer=i.createRenderbuffer(),ct(V.__webglDepthRenderbuffer,I,!0)),e.bindFramebuffer(i.FRAMEBUFFER,null)}}if($){e.bindTexture(i.TEXTURE_CUBE_MAP,Z.__webglTexture),Tt(i.TEXTURE_CUBE_MAP,T);for(let dt=0;dt<6;dt++)if(T.mipmaps&&T.mipmaps.length>0)for(let vt=0;vt<T.mipmaps.length;vt++)xt(V.__webglFramebuffer[dt][vt],I,T,i.COLOR_ATTACHMENT0,i.TEXTURE_CUBE_MAP_POSITIVE_X+dt,vt);else xt(V.__webglFramebuffer[dt],I,T,i.COLOR_ATTACHMENT0,i.TEXTURE_CUBE_MAP_POSITIVE_X+dt,0);m(T)&&f(i.TEXTURE_CUBE_MAP),e.unbindTexture()}else if(Rt){for(let dt=0,vt=et.length;dt<vt;dt++){let Qt=et[dt],rt=n.get(Qt);e.bindTexture(i.TEXTURE_2D,rt.__webglTexture),Tt(i.TEXTURE_2D,Qt),xt(V.__webglFramebuffer,I,Qt,i.COLOR_ATTACHMENT0+dt,i.TEXTURE_2D,0),m(Qt)&&f(i.TEXTURE_2D)}e.unbindTexture()}else{let dt=i.TEXTURE_2D;if((I.isWebGL3DRenderTarget||I.isWebGLArrayRenderTarget)&&(dt=I.isWebGL3DRenderTarget?i.TEXTURE_3D:i.TEXTURE_2D_ARRAY),e.bindTexture(dt,Z.__webglTexture),Tt(dt,T),T.mipmaps&&T.mipmaps.length>0)for(let vt=0;vt<T.mipmaps.length;vt++)xt(V.__webglFramebuffer[vt],I,T,i.COLOR_ATTACHMENT0,dt,vt);else xt(V.__webglFramebuffer,I,T,i.COLOR_ATTACHMENT0,dt,0);m(T)&&f(dt),e.unbindTexture()}I.depthBuffer&&kt(I)}function j(I){let T=I.textures;for(let V=0,Z=T.length;V<Z;V++){let et=T[V];if(m(et)){let $=y(I),Rt=n.get(et).__webglTexture;e.bindTexture($,Rt),f($),e.unbindTexture()}}}let ot=[],D=[];function Dt(I){if(I.samples>0){if(Mt(I)===!1){let T=I.textures,V=I.width,Z=I.height,et=i.COLOR_BUFFER_BIT,$=I.stencilBuffer?i.DEPTH_STENCIL_ATTACHMENT:i.DEPTH_ATTACHMENT,Rt=n.get(I),dt=T.length>1;if(dt)for(let vt=0;vt<T.length;vt++)e.bindFramebuffer(i.FRAMEBUFFER,Rt.__webglMultisampledFramebuffer),i.framebufferRenderbuffer(i.FRAMEBUFFER,i.COLOR_ATTACHMENT0+vt,i.RENDERBUFFER,null),e.bindFramebuffer(i.FRAMEBUFFER,Rt.__webglFramebuffer),i.framebufferTexture2D(i.DRAW_FRAMEBUFFER,i.COLOR_ATTACHMENT0+vt,i.TEXTURE_2D,null,0);e.bindFramebuffer(i.READ_FRAMEBUFFER,Rt.__webglMultisampledFramebuffer),e.bindFramebuffer(i.DRAW_FRAMEBUFFER,Rt.__webglFramebuffer);for(let vt=0;vt<T.length;vt++){if(I.resolveDepthBuffer&&(I.depthBuffer&&(et|=i.DEPTH_BUFFER_BIT),I.stencilBuffer&&I.resolveStencilBuffer&&(et|=i.STENCIL_BUFFER_BIT)),dt){i.framebufferRenderbuffer(i.READ_FRAMEBUFFER,i.COLOR_ATTACHMENT0,i.RENDERBUFFER,Rt.__webglColorRenderbuffer[vt]);let Qt=n.get(T[vt]).__webglTexture;i.framebufferTexture2D(i.DRAW_FRAMEBUFFER,i.COLOR_ATTACHMENT0,i.TEXTURE_2D,Qt,0)}i.blitFramebuffer(0,0,V,Z,0,0,V,Z,et,i.NEAREST),l===!0&&(ot.length=0,D.length=0,ot.push(i.COLOR_ATTACHMENT0+vt),I.depthBuffer&&I.resolveDepthBuffer===!1&&(ot.push($),D.push($),i.invalidateFramebuffer(i.DRAW_FRAMEBUFFER,D)),i.invalidateFramebuffer(i.READ_FRAMEBUFFER,ot))}if(e.bindFramebuffer(i.READ_FRAMEBUFFER,null),e.bindFramebuffer(i.DRAW_FRAMEBUFFER,null),dt)for(let vt=0;vt<T.length;vt++){e.bindFramebuffer(i.FRAMEBUFFER,Rt.__webglMultisampledFramebuffer),i.framebufferRenderbuffer(i.FRAMEBUFFER,i.COLOR_ATTACHMENT0+vt,i.RENDERBUFFER,Rt.__webglColorRenderbuffer[vt]);let Qt=n.get(T[vt]).__webglTexture;e.bindFramebuffer(i.FRAMEBUFFER,Rt.__webglFramebuffer),i.framebufferTexture2D(i.DRAW_FRAMEBUFFER,i.COLOR_ATTACHMENT0+vt,i.TEXTURE_2D,Qt,0)}e.bindFramebuffer(i.DRAW_FRAMEBUFFER,Rt.__webglMultisampledFramebuffer)}else if(I.depthBuffer&&I.resolveDepthBuffer===!1&&l){let T=I.stencilBuffer?i.DEPTH_STENCIL_ATTACHMENT:i.DEPTH_ATTACHMENT;i.invalidateFramebuffer(i.DRAW_FRAMEBUFFER,[T])}}}function it(I){return Math.min(s.maxSamples,I.samples)}function Mt(I){let T=n.get(I);return I.samples>0&&t.has("WEBGL_multisampled_render_to_texture")===!0&&T.__useRenderToTexture!==!1}function ht(I){let T=a.render.frame;h.get(I)!==T&&(h.set(I,T),I.update())}function Ot(I,T){let V=I.colorSpace,Z=I.format,et=I.type;return I.isCompressedTexture===!0||I.isVideoTexture===!0||V!==Is&&V!==hi&&(ne.getTransfer(V)===le?(Z!==bn||et!==Zn)&&console.warn("THREE.WebGLTextures: sRGB encoded textures have to use RGBAFormat and UnsignedByteType."):console.error("THREE.WebGLTextures: Unsupported texture color space:",V)),T}function yt(I){return typeof HTMLImageElement!="undefined"&&I instanceof HTMLImageElement?(c.width=I.naturalWidth||I.width,c.height=I.naturalHeight||I.height):typeof VideoFrame!="undefined"&&I instanceof VideoFrame?(c.width=I.displayWidth,c.height=I.displayHeight):(c.width=I.width,c.height=I.height),c}this.allocateTextureUnit=F,this.resetTextureUnits=P,this.setTexture2D=H,this.setTexture2DArray=N,this.setTexture3D=K,this.setTextureCube=O,this.rebindTextures=At,this.setupRenderTarget=Jt,this.updateRenderTargetMipmap=j,this.updateMultisampleRenderTarget=Dt,this.setupDepthRenderbuffer=kt,this.setupFrameBufferTexture=xt,this.useMultisampledRTT=Mt}function yg(i,t){function e(n,s=hi){let r,a=ne.getTransfer(s);if(n===Zn)return i.UNSIGNED_BYTE;if(n===Fl)return i.UNSIGNED_SHORT_4_4_4_4;if(n===Dl)return i.UNSIGNED_SHORT_5_5_5_1;if(n===wh)return i.UNSIGNED_INT_5_9_9_9_REV;if(n===Eh)return i.BYTE;if(n===bh)return i.SHORT;if(n===Js)return i.UNSIGNED_SHORT;if(n===Pl)return i.INT;if(n===Di)return i.UNSIGNED_INT;if(n===Pn)return i.FLOAT;if(n===or)return i.HALF_FLOAT;if(n===Th)return i.ALPHA;if(n===Ah)return i.RGB;if(n===bn)return i.RGBA;if(n===Rh)return i.LUMINANCE;if(n===Ch)return i.LUMINANCE_ALPHA;if(n===hs)return i.DEPTH_COMPONENT;if(n===_s)return i.DEPTH_STENCIL;if(n===Ll)return i.RED;if(n===Ul)return i.RED_INTEGER;if(n===Ih)return i.RG;if(n===Nl)return i.RG_INTEGER;if(n===Ol)return i.RGBA_INTEGER;if(n===Gr||n===Wr||n===Kr||n===Xr)if(a===le)if(r=t.get("WEBGL_compressed_texture_s3tc_srgb"),r!==null){if(n===Gr)return r.COMPRESSED_SRGB_S3TC_DXT1_EXT;if(n===Wr)return r.COMPRESSED_SRGB_ALPHA_S3TC_DXT1_EXT;if(n===Kr)return r.COMPRESSED_SRGB_ALPHA_S3TC_DXT3_EXT;if(n===Xr)return r.COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT}else return null;else if(r=t.get("WEBGL_compressed_texture_s3tc"),r!==null){if(n===Gr)return r.COMPRESSED_RGB_S3TC_DXT1_EXT;if(n===Wr)return r.COMPRESSED_RGBA_S3TC_DXT1_EXT;if(n===Kr)return r.COMPRESSED_RGBA_S3TC_DXT3_EXT;if(n===Xr)return r.COMPRESSED_RGBA_S3TC_DXT5_EXT}else return null;if(n===xo||n===yo||n===vo||n===Mo)if(r=t.get("WEBGL_compressed_texture_pvrtc"),r!==null){if(n===xo)return r.COMPRESSED_RGB_PVRTC_4BPPV1_IMG;if(n===yo)return r.COMPRESSED_RGB_PVRTC_2BPPV1_IMG;if(n===vo)return r.COMPRESSED_RGBA_PVRTC_4BPPV1_IMG;if(n===Mo)return r.COMPRESSED_RGBA_PVRTC_2BPPV1_IMG}else return null;if(n===So||n===Eo||n===bo)if(r=t.get("WEBGL_compressed_texture_etc"),r!==null){if(n===So||n===Eo)return a===le?r.COMPRESSED_SRGB8_ETC2:r.COMPRESSED_RGB8_ETC2;if(n===bo)return a===le?r.COMPRESSED_SRGB8_ALPHA8_ETC2_EAC:r.COMPRESSED_RGBA8_ETC2_EAC}else return null;if(n===wo||n===To||n===Ao||n===Ro||n===Co||n===Io||n===Po||n===Fo||n===Do||n===Lo||n===Uo||n===No||n===Oo||n===Bo)if(r=t.get("WEBGL_compressed_texture_astc"),r!==null){if(n===wo)return a===le?r.COMPRESSED_SRGB8_ALPHA8_ASTC_4x4_KHR:r.COMPRESSED_RGBA_ASTC_4x4_KHR;if(n===To)return a===le?r.COMPRESSED_SRGB8_ALPHA8_ASTC_5x4_KHR:r.COMPRESSED_RGBA_ASTC_5x4_KHR;if(n===Ao)return a===le?r.COMPRESSED_SRGB8_ALPHA8_ASTC_5x5_KHR:r.COMPRESSED_RGBA_ASTC_5x5_KHR;if(n===Ro)return a===le?r.COMPRESSED_SRGB8_ALPHA8_ASTC_6x5_KHR:r.COMPRESSED_RGBA_ASTC_6x5_KHR;if(n===Co)return a===le?r.COMPRESSED_SRGB8_ALPHA8_ASTC_6x6_KHR:r.COMPRESSED_RGBA_ASTC_6x6_KHR;if(n===Io)return a===le?r.COMPRESSED_SRGB8_ALPHA8_ASTC_8x5_KHR:r.COMPRESSED_RGBA_ASTC_8x5_KHR;if(n===Po)return a===le?r.COMPRESSED_SRGB8_ALPHA8_ASTC_8x6_KHR:r.COMPRESSED_RGBA_ASTC_8x6_KHR;if(n===Fo)return a===le?r.COMPRESSED_SRGB8_ALPHA8_ASTC_8x8_KHR:r.COMPRESSED_RGBA_ASTC_8x8_KHR;if(n===Do)return a===le?r.COMPRESSED_SRGB8_ALPHA8_ASTC_10x5_KHR:r.COMPRESSED_RGBA_ASTC_10x5_KHR;if(n===Lo)return a===le?r.COMPRESSED_SRGB8_ALPHA8_ASTC_10x6_KHR:r.COMPRESSED_RGBA_ASTC_10x6_KHR;if(n===Uo)return a===le?r.COMPRESSED_SRGB8_ALPHA8_ASTC_10x8_KHR:r.COMPRESSED_RGBA_ASTC_10x8_KHR;if(n===No)return a===le?r.COMPRESSED_SRGB8_ALPHA8_ASTC_10x10_KHR:r.COMPRESSED_RGBA_ASTC_10x10_KHR;if(n===Oo)return a===le?r.COMPRESSED_SRGB8_ALPHA8_ASTC_12x10_KHR:r.COMPRESSED_RGBA_ASTC_12x10_KHR;if(n===Bo)return a===le?r.COMPRESSED_SRGB8_ALPHA8_ASTC_12x12_KHR:r.COMPRESSED_RGBA_ASTC_12x12_KHR}else return null;if(n===qr||n===zo||n===ko)if(r=t.get("EXT_texture_compression_bptc"),r!==null){if(n===qr)return a===le?r.COMPRESSED_SRGB_ALPHA_BPTC_UNORM_EXT:r.COMPRESSED_RGBA_BPTC_UNORM_EXT;if(n===zo)return r.COMPRESSED_RGB_BPTC_SIGNED_FLOAT_EXT;if(n===ko)return r.COMPRESSED_RGB_BPTC_UNSIGNED_FLOAT_EXT}else return null;if(n===Ph||n===Ho||n===Vo||n===Go)if(r=t.get("EXT_texture_compression_rgtc"),r!==null){if(n===qr)return r.COMPRESSED_RED_RGTC1_EXT;if(n===Ho)return r.COMPRESSED_SIGNED_RED_RGTC1_EXT;if(n===Vo)return r.COMPRESSED_RED_GREEN_RGTC2_EXT;if(n===Go)return r.COMPRESSED_SIGNED_RED_GREEN_RGTC2_EXT}else return null;return n===gs?i.UNSIGNED_INT_24_8:i[n]!==void 0?i[n]:null}return{convert:e}}var rl=class extends Le{constructor(t=[]){super(),this.isArrayCamera=!0,this.cameras=t}},zt=class extends He{constructor(){super(),this.isGroup=!0,this.type="Group"}},vg={type:"move"},qs=class{constructor(){this._targetRay=null,this._grip=null,this._hand=null}getHandSpace(){return this._hand===null&&(this._hand=new zt,this._hand.matrixAutoUpdate=!1,this._hand.visible=!1,this._hand.joints={},this._hand.inputState={pinching:!1}),this._hand}getTargetRaySpace(){return this._targetRay===null&&(this._targetRay=new zt,this._targetRay.matrixAutoUpdate=!1,this._targetRay.visible=!1,this._targetRay.hasLinearVelocity=!1,this._targetRay.linearVelocity=new L,this._targetRay.hasAngularVelocity=!1,this._targetRay.angularVelocity=new L),this._targetRay}getGripSpace(){return this._grip===null&&(this._grip=new zt,this._grip.matrixAutoUpdate=!1,this._grip.visible=!1,this._grip.hasLinearVelocity=!1,this._grip.linearVelocity=new L,this._grip.hasAngularVelocity=!1,this._grip.angularVelocity=new L),this._grip}dispatchEvent(t){return this._targetRay!==null&&this._targetRay.dispatchEvent(t),this._grip!==null&&this._grip.dispatchEvent(t),this._hand!==null&&this._hand.dispatchEvent(t),this}connect(t){if(t&&t.hand){let e=this._hand;if(e)for(let n of t.hand.values())this._getHandJoint(e,n)}return this.dispatchEvent({type:"connected",data:t}),this}disconnect(t){return this.dispatchEvent({type:"disconnected",data:t}),this._targetRay!==null&&(this._targetRay.visible=!1),this._grip!==null&&(this._grip.visible=!1),this._hand!==null&&(this._hand.visible=!1),this}update(t,e,n){let s=null,r=null,a=null,o=this._targetRay,l=this._grip,c=this._hand;if(t&&e.session.visibilityState!=="visible-blurred"){if(c&&t.hand){a=!0;for(let x of t.hand.values()){let m=e.getJointPose(x,n),f=this._getHandJoint(c,x);m!==null&&(f.matrix.fromArray(m.transform.matrix),f.matrix.decompose(f.position,f.rotation,f.scale),f.matrixWorldNeedsUpdate=!0,f.jointRadius=m.radius),f.visible=m!==null}let h=c.joints["index-finger-tip"],u=c.joints["thumb-tip"],d=h.position.distanceTo(u.position),p=.02,g=.005;c.inputState.pinching&&d>p+g?(c.inputState.pinching=!1,this.dispatchEvent({type:"pinchend",handedness:t.handedness,target:this})):!c.inputState.pinching&&d<=p-g&&(c.inputState.pinching=!0,this.dispatchEvent({type:"pinchstart",handedness:t.handedness,target:this}))}else l!==null&&t.gripSpace&&(r=e.getPose(t.gripSpace,n),r!==null&&(l.matrix.fromArray(r.transform.matrix),l.matrix.decompose(l.position,l.rotation,l.scale),l.matrixWorldNeedsUpdate=!0,r.linearVelocity?(l.hasLinearVelocity=!0,l.linearVelocity.copy(r.linearVelocity)):l.hasLinearVelocity=!1,r.angularVelocity?(l.hasAngularVelocity=!0,l.angularVelocity.copy(r.angularVelocity)):l.hasAngularVelocity=!1));o!==null&&(s=e.getPose(t.targetRaySpace,n),s===null&&r!==null&&(s=r),s!==null&&(o.matrix.fromArray(s.transform.matrix),o.matrix.decompose(o.position,o.rotation,o.scale),o.matrixWorldNeedsUpdate=!0,s.linearVelocity?(o.hasLinearVelocity=!0,o.linearVelocity.copy(s.linearVelocity)):o.hasLinearVelocity=!1,s.angularVelocity?(o.hasAngularVelocity=!0,o.angularVelocity.copy(s.angularVelocity)):o.hasAngularVelocity=!1,this.dispatchEvent(vg)))}return o!==null&&(o.visible=s!==null),l!==null&&(l.visible=r!==null),c!==null&&(c.visible=a!==null),this}_getHandJoint(t,e){if(t.joints[e.jointName]===void 0){let n=new zt;n.matrixAutoUpdate=!1,n.visible=!1,t.joints[e.jointName]=n,t.add(n)}return t.joints[e.jointName]}},Mg=`
void main() {

	gl_Position = vec4( position, 1.0 );

}`,Sg=`
uniform sampler2DArray depthColor;
uniform float depthWidth;
uniform float depthHeight;

void main() {

	vec2 coord = vec2( gl_FragCoord.x / depthWidth, gl_FragCoord.y / depthHeight );

	if ( coord.x >= 1.0 ) {

		gl_FragDepth = texture( depthColor, vec3( coord.x - 1.0, coord.y, 1 ) ).r;

	} else {

		gl_FragDepth = texture( depthColor, vec3( coord.x, coord.y, 0 ) ).r;

	}

}`,al=class{constructor(){this.texture=null,this.mesh=null,this.depthNear=0,this.depthFar=0}init(t,e,n){if(this.texture===null){let s=new $e,r=t.properties.get(s);r.__webglTexture=e.texture,(e.depthNear!=n.depthNear||e.depthFar!=n.depthFar)&&(this.depthNear=e.depthNear,this.depthFar=e.depthFar),this.texture=s}}getMesh(t){if(this.texture!==null&&this.mesh===null){let e=t.cameras[0].viewport,n=new mn({vertexShader:Mg,fragmentShader:Sg,uniforms:{depthColor:{value:this.texture},depthWidth:{value:e.z},depthHeight:{value:e.w}}});this.mesh=new Ct(new Je(20,20),n)}return this.mesh}reset(){this.texture=null,this.mesh=null}getDepthTexture(){return this.texture}},ol=class extends gi{constructor(t,e){super();let n=this,s=null,r=1,a=null,o="local-floor",l=1,c=null,h=null,u=null,d=null,p=null,g=null,x=new al,m=e.getContextAttributes(),f=null,y=null,_=[],M=[],C=new at,A=null,E=new Le;E.viewport=new ce;let b=new Le;b.viewport=new ce;let v=[E,b],S=new rl,w=null,P=null;this.cameraAutoUpdate=!0,this.enabled=!1,this.isPresenting=!1,this.getController=function(X){let st=_[X];return st===void 0&&(st=new qs,_[X]=st),st.getTargetRaySpace()},this.getControllerGrip=function(X){let st=_[X];return st===void 0&&(st=new qs,_[X]=st),st.getGripSpace()},this.getHand=function(X){let st=_[X];return st===void 0&&(st=new qs,_[X]=st),st.getHandSpace()};function F(X){let st=M.indexOf(X.inputSource);if(st===-1)return;let xt=_[st];xt!==void 0&&(xt.update(X.inputSource,X.frame,c||a),xt.dispatchEvent({type:X.type,data:X.inputSource}))}function U(){s.removeEventListener("select",F),s.removeEventListener("selectstart",F),s.removeEventListener("selectend",F),s.removeEventListener("squeeze",F),s.removeEventListener("squeezestart",F),s.removeEventListener("squeezeend",F),s.removeEventListener("end",U),s.removeEventListener("inputsourceschange",H);for(let X=0;X<_.length;X++){let st=M[X];st!==null&&(M[X]=null,_[X].disconnect(st))}w=null,P=null,x.reset(),t.setRenderTarget(f),p=null,d=null,u=null,s=null,y=null,Nt.stop(),n.isPresenting=!1,t.setPixelRatio(A),t.setSize(C.width,C.height,!1),n.dispatchEvent({type:"sessionend"})}this.setFramebufferScaleFactor=function(X){r=X,n.isPresenting===!0&&console.warn("THREE.WebXRManager: Cannot change framebuffer scale while presenting.")},this.setReferenceSpaceType=function(X){o=X,n.isPresenting===!0&&console.warn("THREE.WebXRManager: Cannot change reference space type while presenting.")},this.getReferenceSpace=function(){return c||a},this.setReferenceSpace=function(X){c=X},this.getBaseLayer=function(){return d!==null?d:p},this.getBinding=function(){return u},this.getFrame=function(){return g},this.getSession=function(){return s},this.setSession=async function(X){if(s=X,s!==null){if(f=t.getRenderTarget(),s.addEventListener("select",F),s.addEventListener("selectstart",F),s.addEventListener("selectend",F),s.addEventListener("squeeze",F),s.addEventListener("squeezestart",F),s.addEventListener("squeezeend",F),s.addEventListener("end",U),s.addEventListener("inputsourceschange",H),m.xrCompatible!==!0&&await e.makeXRCompatible(),A=t.getPixelRatio(),t.getSize(C),s.renderState.layers===void 0){let st={antialias:m.antialias,alpha:!0,depth:m.depth,stencil:m.stencil,framebufferScaleFactor:r};p=new XRWebGLLayer(s,e,st),s.updateRenderState({baseLayer:p}),t.setPixelRatio(1),t.setSize(p.framebufferWidth,p.framebufferHeight,!1),y=new $n(p.framebufferWidth,p.framebufferHeight,{format:bn,type:Zn,colorSpace:t.outputColorSpace,stencilBuffer:m.stencil})}else{let st=null,xt=null,ct=null;m.depth&&(ct=m.stencil?e.DEPTH24_STENCIL8:e.DEPTH_COMPONENT24,st=m.stencil?_s:hs,xt=m.stencil?gs:Di);let Ut={colorFormat:e.RGBA8,depthFormat:ct,scaleFactor:r};u=new XRWebGLBinding(s,e),d=u.createProjectionLayer(Ut),s.updateRenderState({layers:[d]}),t.setPixelRatio(1),t.setSize(d.textureWidth,d.textureHeight,!1),y=new $n(d.textureWidth,d.textureHeight,{format:bn,type:Zn,depthTexture:new sa(d.textureWidth,d.textureHeight,xt,void 0,void 0,void 0,void 0,void 0,void 0,st),stencilBuffer:m.stencil,colorSpace:t.outputColorSpace,samples:m.antialias?4:0,resolveDepthBuffer:d.ignoreDepthValues===!1})}y.isXRRenderTarget=!0,this.setFoveation(l),c=null,a=await s.requestReferenceSpace(o),Nt.setContext(s),Nt.start(),n.isPresenting=!0,n.dispatchEvent({type:"sessionstart"})}},this.getEnvironmentBlendMode=function(){if(s!==null)return s.environmentBlendMode},this.getDepthTexture=function(){return x.getDepthTexture()};function H(X){for(let st=0;st<X.removed.length;st++){let xt=X.removed[st],ct=M.indexOf(xt);ct>=0&&(M[ct]=null,_[ct].disconnect(xt))}for(let st=0;st<X.added.length;st++){let xt=X.added[st],ct=M.indexOf(xt);if(ct===-1){for(let kt=0;kt<_.length;kt++)if(kt>=M.length){M.push(xt),ct=kt;break}else if(M[kt]===null){M[kt]=xt,ct=kt;break}if(ct===-1)break}let Ut=_[ct];Ut&&Ut.connect(xt)}}let N=new L,K=new L;function O(X,st,xt){N.setFromMatrixPosition(st.matrixWorld),K.setFromMatrixPosition(xt.matrixWorld);let ct=N.distanceTo(K),Ut=st.projectionMatrix.elements,kt=xt.projectionMatrix.elements,At=Ut[14]/(Ut[10]-1),Jt=Ut[14]/(Ut[10]+1),j=(Ut[9]+1)/Ut[5],ot=(Ut[9]-1)/Ut[5],D=(Ut[8]-1)/Ut[0],Dt=(kt[8]+1)/kt[0],it=At*D,Mt=At*Dt,ht=ct/(-D+Dt),Ot=ht*-D;if(st.matrixWorld.decompose(X.position,X.quaternion,X.scale),X.translateX(Ot),X.translateZ(ht),X.matrixWorld.compose(X.position,X.quaternion,X.scale),X.matrixWorldInverse.copy(X.matrixWorld).invert(),Ut[10]===-1)X.projectionMatrix.copy(st.projectionMatrix),X.projectionMatrixInverse.copy(st.projectionMatrixInverse);else{let yt=At+ht,I=Jt+ht,T=it-Ot,V=Mt+(ct-Ot),Z=j*Jt/I*yt,et=ot*Jt/I*yt;X.projectionMatrix.makePerspective(T,V,Z,et,yt,I),X.projectionMatrixInverse.copy(X.projectionMatrix).invert()}}function Y(X,st){st===null?X.matrixWorld.copy(X.matrix):X.matrixWorld.multiplyMatrices(st.matrixWorld,X.matrix),X.matrixWorldInverse.copy(X.matrixWorld).invert()}this.updateCamera=function(X){if(s===null)return;let st=X.near,xt=X.far;x.texture!==null&&(x.depthNear>0&&(st=x.depthNear),x.depthFar>0&&(xt=x.depthFar)),S.near=b.near=E.near=st,S.far=b.far=E.far=xt,(w!==S.near||P!==S.far)&&(s.updateRenderState({depthNear:S.near,depthFar:S.far}),w=S.near,P=S.far),E.layers.mask=X.layers.mask|2,b.layers.mask=X.layers.mask|4,S.layers.mask=E.layers.mask|b.layers.mask;let ct=X.parent,Ut=S.cameras;Y(S,ct);for(let kt=0;kt<Ut.length;kt++)Y(Ut[kt],ct);Ut.length===2?O(S,E,b):S.projectionMatrix.copy(E.projectionMatrix),tt(X,S,ct)};function tt(X,st,xt){xt===null?X.matrix.copy(st.matrixWorld):(X.matrix.copy(xt.matrixWorld),X.matrix.invert(),X.matrix.multiply(st.matrixWorld)),X.matrix.decompose(X.position,X.quaternion,X.scale),X.updateMatrixWorld(!0),X.projectionMatrix.copy(st.projectionMatrix),X.projectionMatrixInverse.copy(st.projectionMatrixInverse),X.isPerspectiveCamera&&(X.fov=xs*2*Math.atan(1/X.projectionMatrix.elements[5]),X.zoom=1)}this.getCamera=function(){return S},this.getFoveation=function(){if(!(d===null&&p===null))return l},this.setFoveation=function(X){l=X,d!==null&&(d.fixedFoveation=X),p!==null&&p.fixedFoveation!==void 0&&(p.fixedFoveation=X)},this.hasDepthSensing=function(){return x.texture!==null},this.getDepthSensingMesh=function(){return x.getMesh(S)};let nt=null;function Tt(X,st){if(h=st.getViewerPose(c||a),g=st,h!==null){let xt=h.views;p!==null&&(t.setRenderTargetFramebuffer(y,p.framebuffer),t.setRenderTarget(y));let ct=!1;xt.length!==S.cameras.length&&(S.cameras.length=0,ct=!0);for(let kt=0;kt<xt.length;kt++){let At=xt[kt],Jt=null;if(p!==null)Jt=p.getViewport(At);else{let ot=u.getViewSubImage(d,At);Jt=ot.viewport,kt===0&&(t.setRenderTargetTextures(y,ot.colorTexture,d.ignoreDepthValues?void 0:ot.depthStencilTexture),t.setRenderTarget(y))}let j=v[kt];j===void 0&&(j=new Le,j.layers.enable(kt),j.viewport=new ce,v[kt]=j),j.matrix.fromArray(At.transform.matrix),j.matrix.decompose(j.position,j.quaternion,j.scale),j.projectionMatrix.fromArray(At.projectionMatrix),j.projectionMatrixInverse.copy(j.projectionMatrix).invert(),j.viewport.set(Jt.x,Jt.y,Jt.width,Jt.height),kt===0&&(S.matrix.copy(j.matrix),S.matrix.decompose(S.position,S.quaternion,S.scale)),ct===!0&&S.cameras.push(j)}let Ut=s.enabledFeatures;if(Ut&&Ut.includes("depth-sensing")){let kt=u.getDepthInformation(xt[0]);kt&&kt.isValid&&kt.texture&&x.init(t,kt,s.renderState)}}for(let xt=0;xt<_.length;xt++){let ct=M[xt],Ut=_[xt];ct!==null&&Ut!==void 0&&Ut.update(ct,st,c||a)}nt&&nt(X,st),st.detectedPlanes&&n.dispatchEvent({type:"planesdetected",data:st}),g=null}let Nt=new Bh;Nt.setAnimationLoop(Tt),this.setAnimationLoop=function(X){nt=X},this.dispose=function(){}}},Ri=new pn,Eg=new he;function bg(i,t){function e(m,f){m.matrixAutoUpdate===!0&&m.updateMatrix(),f.value.copy(m.matrix)}function n(m,f){f.color.getRGB(m.fogColor.value,Oh(i)),f.isFog?(m.fogNear.value=f.near,m.fogFar.value=f.far):f.isFogExp2&&(m.fogDensity.value=f.density)}function s(m,f,y,_,M){f.isMeshBasicMaterial||f.isMeshLambertMaterial?r(m,f):f.isMeshToonMaterial?(r(m,f),u(m,f)):f.isMeshPhongMaterial?(r(m,f),h(m,f)):f.isMeshStandardMaterial?(r(m,f),d(m,f),f.isMeshPhysicalMaterial&&p(m,f,M)):f.isMeshMatcapMaterial?(r(m,f),g(m,f)):f.isMeshDepthMaterial?r(m,f):f.isMeshDistanceMaterial?(r(m,f),x(m,f)):f.isMeshNormalMaterial?r(m,f):f.isLineBasicMaterial?(a(m,f),f.isLineDashedMaterial&&o(m,f)):f.isPointsMaterial?l(m,f,y,_):f.isSpriteMaterial?c(m,f):f.isShadowMaterial?(m.color.value.copy(f.color),m.opacity.value=f.opacity):f.isShaderMaterial&&(f.uniformsNeedUpdate=!1)}function r(m,f){m.opacity.value=f.opacity,f.color&&m.diffuse.value.copy(f.color),f.emissive&&m.emissive.value.copy(f.emissive).multiplyScalar(f.emissiveIntensity),f.map&&(m.map.value=f.map,e(f.map,m.mapTransform)),f.alphaMap&&(m.alphaMap.value=f.alphaMap,e(f.alphaMap,m.alphaMapTransform)),f.bumpMap&&(m.bumpMap.value=f.bumpMap,e(f.bumpMap,m.bumpMapTransform),m.bumpScale.value=f.bumpScale,f.side===ze&&(m.bumpScale.value*=-1)),f.normalMap&&(m.normalMap.value=f.normalMap,e(f.normalMap,m.normalMapTransform),m.normalScale.value.copy(f.normalScale),f.side===ze&&m.normalScale.value.negate()),f.displacementMap&&(m.displacementMap.value=f.displacementMap,e(f.displacementMap,m.displacementMapTransform),m.displacementScale.value=f.displacementScale,m.displacementBias.value=f.displacementBias),f.emissiveMap&&(m.emissiveMap.value=f.emissiveMap,e(f.emissiveMap,m.emissiveMapTransform)),f.specularMap&&(m.specularMap.value=f.specularMap,e(f.specularMap,m.specularMapTransform)),f.alphaTest>0&&(m.alphaTest.value=f.alphaTest);let y=t.get(f),_=y.envMap,M=y.envMapRotation;_&&(m.envMap.value=_,Ri.copy(M),Ri.x*=-1,Ri.y*=-1,Ri.z*=-1,_.isCubeTexture&&_.isRenderTargetTexture===!1&&(Ri.y*=-1,Ri.z*=-1),m.envMapRotation.value.setFromMatrix4(Eg.makeRotationFromEuler(Ri)),m.flipEnvMap.value=_.isCubeTexture&&_.isRenderTargetTexture===!1?-1:1,m.reflectivity.value=f.reflectivity,m.ior.value=f.ior,m.refractionRatio.value=f.refractionRatio),f.lightMap&&(m.lightMap.value=f.lightMap,m.lightMapIntensity.value=f.lightMapIntensity,e(f.lightMap,m.lightMapTransform)),f.aoMap&&(m.aoMap.value=f.aoMap,m.aoMapIntensity.value=f.aoMapIntensity,e(f.aoMap,m.aoMapTransform))}function a(m,f){m.diffuse.value.copy(f.color),m.opacity.value=f.opacity,f.map&&(m.map.value=f.map,e(f.map,m.mapTransform))}function o(m,f){m.dashSize.value=f.dashSize,m.totalSize.value=f.dashSize+f.gapSize,m.scale.value=f.scale}function l(m,f,y,_){m.diffuse.value.copy(f.color),m.opacity.value=f.opacity,m.size.value=f.size*y,m.scale.value=_*.5,f.map&&(m.map.value=f.map,e(f.map,m.uvTransform)),f.alphaMap&&(m.alphaMap.value=f.alphaMap,e(f.alphaMap,m.alphaMapTransform)),f.alphaTest>0&&(m.alphaTest.value=f.alphaTest)}function c(m,f){m.diffuse.value.copy(f.color),m.opacity.value=f.opacity,m.rotation.value=f.rotation,f.map&&(m.map.value=f.map,e(f.map,m.mapTransform)),f.alphaMap&&(m.alphaMap.value=f.alphaMap,e(f.alphaMap,m.alphaMapTransform)),f.alphaTest>0&&(m.alphaTest.value=f.alphaTest)}function h(m,f){m.specular.value.copy(f.specular),m.shininess.value=Math.max(f.shininess,1e-4)}function u(m,f){f.gradientMap&&(m.gradientMap.value=f.gradientMap)}function d(m,f){m.metalness.value=f.metalness,f.metalnessMap&&(m.metalnessMap.value=f.metalnessMap,e(f.metalnessMap,m.metalnessMapTransform)),m.roughness.value=f.roughness,f.roughnessMap&&(m.roughnessMap.value=f.roughnessMap,e(f.roughnessMap,m.roughnessMapTransform)),f.envMap&&(m.envMapIntensity.value=f.envMapIntensity)}function p(m,f,y){m.ior.value=f.ior,f.sheen>0&&(m.sheenColor.value.copy(f.sheenColor).multiplyScalar(f.sheen),m.sheenRoughness.value=f.sheenRoughness,f.sheenColorMap&&(m.sheenColorMap.value=f.sheenColorMap,e(f.sheenColorMap,m.sheenColorMapTransform)),f.sheenRoughnessMap&&(m.sheenRoughnessMap.value=f.sheenRoughnessMap,e(f.sheenRoughnessMap,m.sheenRoughnessMapTransform))),f.clearcoat>0&&(m.clearcoat.value=f.clearcoat,m.clearcoatRoughness.value=f.clearcoatRoughness,f.clearcoatMap&&(m.clearcoatMap.value=f.clearcoatMap,e(f.clearcoatMap,m.clearcoatMapTransform)),f.clearcoatRoughnessMap&&(m.clearcoatRoughnessMap.value=f.clearcoatRoughnessMap,e(f.clearcoatRoughnessMap,m.clearcoatRoughnessMapTransform)),f.clearcoatNormalMap&&(m.clearcoatNormalMap.value=f.clearcoatNormalMap,e(f.clearcoatNormalMap,m.clearcoatNormalMapTransform),m.clearcoatNormalScale.value.copy(f.clearcoatNormalScale),f.side===ze&&m.clearcoatNormalScale.value.negate())),f.dispersion>0&&(m.dispersion.value=f.dispersion),f.iridescence>0&&(m.iridescence.value=f.iridescence,m.iridescenceIOR.value=f.iridescenceIOR,m.iridescenceThicknessMinimum.value=f.iridescenceThicknessRange[0],m.iridescenceThicknessMaximum.value=f.iridescenceThicknessRange[1],f.iridescenceMap&&(m.iridescenceMap.value=f.iridescenceMap,e(f.iridescenceMap,m.iridescenceMapTransform)),f.iridescenceThicknessMap&&(m.iridescenceThicknessMap.value=f.iridescenceThicknessMap,e(f.iridescenceThicknessMap,m.iridescenceThicknessMapTransform))),f.transmission>0&&(m.transmission.value=f.transmission,m.transmissionSamplerMap.value=y.texture,m.transmissionSamplerSize.value.set(y.width,y.height),f.transmissionMap&&(m.transmissionMap.value=f.transmissionMap,e(f.transmissionMap,m.transmissionMapTransform)),m.thickness.value=f.thickness,f.thicknessMap&&(m.thicknessMap.value=f.thicknessMap,e(f.thicknessMap,m.thicknessMapTransform)),m.attenuationDistance.value=f.attenuationDistance,m.attenuationColor.value.copy(f.attenuationColor)),f.anisotropy>0&&(m.anisotropyVector.value.set(f.anisotropy*Math.cos(f.anisotropyRotation),f.anisotropy*Math.sin(f.anisotropyRotation)),f.anisotropyMap&&(m.anisotropyMap.value=f.anisotropyMap,e(f.anisotropyMap,m.anisotropyMapTransform))),m.specularIntensity.value=f.specularIntensity,m.specularColor.value.copy(f.specularColor),f.specularColorMap&&(m.specularColorMap.value=f.specularColorMap,e(f.specularColorMap,m.specularColorMapTransform)),f.specularIntensityMap&&(m.specularIntensityMap.value=f.specularIntensityMap,e(f.specularIntensityMap,m.specularIntensityMapTransform))}function g(m,f){f.matcap&&(m.matcap.value=f.matcap)}function x(m,f){let y=t.get(f).light;m.referencePosition.value.setFromMatrixPosition(y.matrixWorld),m.nearDistance.value=y.shadow.camera.near,m.farDistance.value=y.shadow.camera.far}return{refreshFogUniforms:n,refreshMaterialUniforms:s}}function wg(i,t,e,n){let s={},r={},a=[],o=i.getParameter(i.MAX_UNIFORM_BUFFER_BINDINGS);function l(y,_){let M=_.program;n.uniformBlockBinding(y,M)}function c(y,_){let M=s[y.id];M===void 0&&(g(y),M=h(y),s[y.id]=M,y.addEventListener("dispose",m));let C=_.program;n.updateUBOMapping(y,C);let A=t.render.frame;r[y.id]!==A&&(d(y),r[y.id]=A)}function h(y){let _=u();y.__bindingPointIndex=_;let M=i.createBuffer(),C=y.__size,A=y.usage;return i.bindBuffer(i.UNIFORM_BUFFER,M),i.bufferData(i.UNIFORM_BUFFER,C,A),i.bindBuffer(i.UNIFORM_BUFFER,null),i.bindBufferBase(i.UNIFORM_BUFFER,_,M),M}function u(){for(let y=0;y<o;y++)if(a.indexOf(y)===-1)return a.push(y),y;return console.error("THREE.WebGLRenderer: Maximum number of simultaneously usable uniforms groups reached."),0}function d(y){let _=s[y.id],M=y.uniforms,C=y.__cache;i.bindBuffer(i.UNIFORM_BUFFER,_);for(let A=0,E=M.length;A<E;A++){let b=Array.isArray(M[A])?M[A]:[M[A]];for(let v=0,S=b.length;v<S;v++){let w=b[v];if(p(w,A,v,C)===!0){let P=w.__offset,F=Array.isArray(w.value)?w.value:[w.value],U=0;for(let H=0;H<F.length;H++){let N=F[H],K=x(N);typeof N=="number"||typeof N=="boolean"?(w.__data[0]=N,i.bufferSubData(i.UNIFORM_BUFFER,P+U,w.__data)):N.isMatrix3?(w.__data[0]=N.elements[0],w.__data[1]=N.elements[1],w.__data[2]=N.elements[2],w.__data[3]=0,w.__data[4]=N.elements[3],w.__data[5]=N.elements[4],w.__data[6]=N.elements[5],w.__data[7]=0,w.__data[8]=N.elements[6],w.__data[9]=N.elements[7],w.__data[10]=N.elements[8],w.__data[11]=0):(N.toArray(w.__data,U),U+=K.storage/Float32Array.BYTES_PER_ELEMENT)}i.bufferSubData(i.UNIFORM_BUFFER,P,w.__data)}}}i.bindBuffer(i.UNIFORM_BUFFER,null)}function p(y,_,M,C){let A=y.value,E=_+"_"+M;if(C[E]===void 0)return typeof A=="number"||typeof A=="boolean"?C[E]=A:C[E]=A.clone(),!0;{let b=C[E];if(typeof A=="number"||typeof A=="boolean"){if(b!==A)return C[E]=A,!0}else if(b.equals(A)===!1)return b.copy(A),!0}return!1}function g(y){let _=y.uniforms,M=0,C=16;for(let E=0,b=_.length;E<b;E++){let v=Array.isArray(_[E])?_[E]:[_[E]];for(let S=0,w=v.length;S<w;S++){let P=v[S],F=Array.isArray(P.value)?P.value:[P.value];for(let U=0,H=F.length;U<H;U++){let N=F[U],K=x(N),O=M%C,Y=O%K.boundary,tt=O+Y;M+=Y,tt!==0&&C-tt<K.storage&&(M+=C-tt),P.__data=new Float32Array(K.storage/Float32Array.BYTES_PER_ELEMENT),P.__offset=M,M+=K.storage}}}let A=M%C;return A>0&&(M+=C-A),y.__size=M,y.__cache={},this}function x(y){let _={boundary:0,storage:0};return typeof y=="number"||typeof y=="boolean"?(_.boundary=4,_.storage=4):y.isVector2?(_.boundary=8,_.storage=8):y.isVector3||y.isColor?(_.boundary=16,_.storage=12):y.isVector4?(_.boundary=16,_.storage=16):y.isMatrix3?(_.boundary=48,_.storage=48):y.isMatrix4?(_.boundary=64,_.storage=64):y.isTexture?console.warn("THREE.WebGLRenderer: Texture samplers can not be part of an uniforms group."):console.warn("THREE.WebGLRenderer: Unsupported uniform value type.",y),_}function m(y){let _=y.target;_.removeEventListener("dispose",m);let M=a.indexOf(_.__bindingPointIndex);a.splice(M,1),i.deleteBuffer(s[_.id]),delete s[_.id],delete r[_.id]}function f(){for(let y in s)i.deleteBuffer(s[y]);a=[],s={},r={}}return{bind:l,update:c,dispose:f}}var ra=class{constructor(t={}){let{canvas:e=Rd(),context:n=null,depth:s=!0,stencil:r=!1,alpha:a=!1,antialias:o=!1,premultipliedAlpha:l=!0,preserveDrawingBuffer:c=!1,powerPreference:h="default",failIfMajorPerformanceCaveat:u=!1,reverseDepthBuffer:d=!1}=t;this.isWebGLRenderer=!0;let p;if(n!==null){if(typeof WebGLRenderingContext!="undefined"&&n instanceof WebGLRenderingContext)throw new Error("THREE.WebGLRenderer: WebGL 1 is not supported since r163.");p=n.getContextAttributes().alpha}else p=a;let g=new Uint32Array(4),x=new Int32Array(4),m=null,f=null,y=[],_=[];this.domElement=e,this.debug={checkShaderErrors:!0,onShaderError:null},this.autoClear=!0,this.autoClearColor=!0,this.autoClearDepth=!0,this.autoClearStencil=!0,this.sortObjects=!0,this.clippingPlanes=[],this.localClippingEnabled=!1,this._outputColorSpace=Re,this.toneMapping=di,this.toneMappingExposure=1;let M=this,C=!1,A=0,E=0,b=null,v=-1,S=null,w=new ce,P=new ce,F=null,U=new Gt(0),H=0,N=e.width,K=e.height,O=1,Y=null,tt=null,nt=new ce(0,0,N,K),Tt=new ce(0,0,N,K),Nt=!1,X=new Qs,st=!1,xt=!1,ct=new he,Ut=new he,kt=new L,At=new ce,Jt={background:null,fog:null,environment:null,overrideMaterial:null,isScene:!0},j=!1;function ot(){return b===null?O:1}let D=n;function Dt(R,z){return e.getContext(R,z)}try{let R={alpha:!0,depth:s,stencil:r,antialias:o,premultipliedAlpha:l,preserveDrawingBuffer:c,powerPreference:h,failIfMajorPerformanceCaveat:u};if("setAttribute"in e&&e.setAttribute("data-engine",`three.js r${Cl}`),e.addEventListener("webglcontextlost",Q,!1),e.addEventListener("webglcontextrestored",gt,!1),e.addEventListener("webglcontextcreationerror",pt,!1),D===null){let z="webgl2";if(D=Dt(z,R),D===null)throw Dt(z)?new Error("Error creating WebGL context with your selected attributes."):new Error("Error creating WebGL context.")}}catch(R){throw console.error("THREE.WebGLRenderer: "+R.message),R}let it,Mt,ht,Ot,yt,I,T,V,Z,et,$,Rt,dt,vt,Qt,rt,Et,Bt,Vt,bt,te,Zt,fe,B;function ft(){it=new H0(D),it.init(),Zt=new yg(D,it),Mt=new U0(D,it,t,Zt),ht=new gg(D,it),Mt.reverseDepthBuffer&&d&&ht.buffers.depth.setReversed(!0),Ot=new W0(D),yt=new ig,I=new xg(D,it,ht,yt,Mt,Zt,Ot),T=new O0(M),V=new k0(M),Z=new $d(D),fe=new D0(D,Z),et=new V0(D,Z,Ot,fe),$=new X0(D,et,Z,Ot),Vt=new K0(D,Mt,I),rt=new N0(yt),Rt=new ng(M,T,V,it,Mt,fe,rt),dt=new bg(M,yt),vt=new rg,Qt=new ug(it),Bt=new F0(M,T,V,ht,$,p,l),Et=new pg(M,$,Mt),B=new wg(D,Ot,Mt,ht),bt=new L0(D,it,Ot),te=new G0(D,it,Ot),Ot.programs=Rt.programs,M.capabilities=Mt,M.extensions=it,M.properties=yt,M.renderLists=vt,M.shadowMap=Et,M.state=ht,M.info=Ot}ft();let q=new ol(M,D);this.xr=q,this.getContext=function(){return D},this.getContextAttributes=function(){return D.getContextAttributes()},this.forceContextLoss=function(){let R=it.get("WEBGL_lose_context");R&&R.loseContext()},this.forceContextRestore=function(){let R=it.get("WEBGL_lose_context");R&&R.restoreContext()},this.getPixelRatio=function(){return O},this.setPixelRatio=function(R){R!==void 0&&(O=R,this.setSize(N,K,!1))},this.getSize=function(R){return R.set(N,K)},this.setSize=function(R,z,G=!0){if(q.isPresenting){console.warn("THREE.WebGLRenderer: Can't change size while VR device is presenting.");return}N=R,K=z,e.width=Math.floor(R*O),e.height=Math.floor(z*O),G===!0&&(e.style.width=R+"px",e.style.height=z+"px"),this.setViewport(0,0,R,z)},this.getDrawingBufferSize=function(R){return R.set(N*O,K*O).floor()},this.setDrawingBufferSize=function(R,z,G){N=R,K=z,O=G,e.width=Math.floor(R*G),e.height=Math.floor(z*G),this.setViewport(0,0,R,z)},this.getCurrentViewport=function(R){return R.copy(w)},this.getViewport=function(R){return R.copy(nt)},this.setViewport=function(R,z,G,W){R.isVector4?nt.set(R.x,R.y,R.z,R.w):nt.set(R,z,G,W),ht.viewport(w.copy(nt).multiplyScalar(O).round())},this.getScissor=function(R){return R.copy(Tt)},this.setScissor=function(R,z,G,W){R.isVector4?Tt.set(R.x,R.y,R.z,R.w):Tt.set(R,z,G,W),ht.scissor(P.copy(Tt).multiplyScalar(O).round())},this.getScissorTest=function(){return Nt},this.setScissorTest=function(R){ht.setScissorTest(Nt=R)},this.setOpaqueSort=function(R){Y=R},this.setTransparentSort=function(R){tt=R},this.getClearColor=function(R){return R.copy(Bt.getClearColor())},this.setClearColor=function(){Bt.setClearColor.apply(Bt,arguments)},this.getClearAlpha=function(){return Bt.getClearAlpha()},this.setClearAlpha=function(){Bt.setClearAlpha.apply(Bt,arguments)},this.clear=function(R=!0,z=!0,G=!0){let W=0;if(R){let k=!1;if(b!==null){let lt=b.texture.format;k=lt===Ol||lt===Nl||lt===Ul}if(k){let lt=b.texture.type,mt=lt===Zn||lt===Di||lt===Js||lt===gs||lt===Fl||lt===Dl,It=Bt.getClearColor(),Pt=Bt.getClearAlpha(),Kt=It.r,qt=It.g,Ft=It.b;mt?(g[0]=Kt,g[1]=qt,g[2]=Ft,g[3]=Pt,D.clearBufferuiv(D.COLOR,0,g)):(x[0]=Kt,x[1]=qt,x[2]=Ft,x[3]=Pt,D.clearBufferiv(D.COLOR,0,x))}else W|=D.COLOR_BUFFER_BIT}z&&(W|=D.DEPTH_BUFFER_BIT),G&&(W|=D.STENCIL_BUFFER_BIT,this.state.buffers.stencil.setMask(4294967295)),D.clear(W)},this.clearColor=function(){this.clear(!0,!1,!1)},this.clearDepth=function(){this.clear(!1,!0,!1)},this.clearStencil=function(){this.clear(!1,!1,!0)},this.dispose=function(){e.removeEventListener("webglcontextlost",Q,!1),e.removeEventListener("webglcontextrestored",gt,!1),e.removeEventListener("webglcontextcreationerror",pt,!1),vt.dispose(),Qt.dispose(),yt.dispose(),T.dispose(),V.dispose(),$.dispose(),fe.dispose(),B.dispose(),Rt.dispose(),q.dispose(),q.removeEventListener("sessionstart",ic),q.removeEventListener("sessionend",sc),Si.stop()};function Q(R){R.preventDefault(),console.log("THREE.WebGLRenderer: Context Lost."),C=!0}function gt(){console.log("THREE.WebGLRenderer: Context Restored."),C=!1;let R=Ot.autoReset,z=Et.enabled,G=Et.autoUpdate,W=Et.needsUpdate,k=Et.type;ft(),Ot.autoReset=R,Et.enabled=z,Et.autoUpdate=G,Et.needsUpdate=W,Et.type=k}function pt(R){console.error("THREE.WebGLRenderer: A WebGL context could not be created. Reason: ",R.statusMessage)}function Xt(R){let z=R.target;z.removeEventListener("dispose",Xt),Me(z)}function Me(R){Ne(R),yt.remove(R)}function Ne(R){let z=yt.get(R).programs;z!==void 0&&(z.forEach(function(G){Rt.releaseProgram(G)}),R.isShaderMaterial&&Rt.releaseShaderCache(R))}this.renderBufferDirect=function(R,z,G,W,k,lt){z===null&&(z=Jt);let mt=k.isMesh&&k.matrixWorld.determinant()<0,It=Eu(R,z,G,W,k);ht.setMaterial(W,mt);let Pt=G.index,Kt=1;if(W.wireframe===!0){if(Pt=et.getWireframeAttribute(G),Pt===void 0)return;Kt=2}let qt=G.drawRange,Ft=G.attributes.position,ie=qt.start*Kt,pe=(qt.start+qt.count)*Kt;lt!==null&&(ie=Math.max(ie,lt.start*Kt),pe=Math.min(pe,(lt.start+lt.count)*Kt)),Pt!==null?(ie=Math.max(ie,0),pe=Math.min(pe,Pt.count)):Ft!=null&&(ie=Math.max(ie,0),pe=Math.min(pe,Ft.count));let ge=pe-ie;if(ge<0||ge===1/0)return;fe.setup(k,W,It,G,Pt);let Ze,se=bt;if(Pt!==null&&(Ze=Z.get(Pt),se=te,se.setIndex(Ze)),k.isMesh)W.wireframe===!0?(ht.setLineWidth(W.wireframeLinewidth*ot()),se.setMode(D.LINES)):se.setMode(D.TRIANGLES);else if(k.isLine){let Lt=W.linewidth;Lt===void 0&&(Lt=1),ht.setLineWidth(Lt*ot()),k.isLineSegments?se.setMode(D.LINES):k.isLineLoop?se.setMode(D.LINE_LOOP):se.setMode(D.LINE_STRIP)}else k.isPoints?se.setMode(D.POINTS):k.isSprite&&se.setMode(D.TRIANGLES);if(k.isBatchedMesh)if(k._multiDrawInstances!==null)se.renderMultiDrawInstances(k._multiDrawStarts,k._multiDrawCounts,k._multiDrawCount,k._multiDrawInstances);else if(it.get("WEBGL_multi_draw"))se.renderMultiDraw(k._multiDrawStarts,k._multiDrawCounts,k._multiDrawCount);else{let Lt=k._multiDrawStarts,On=k._multiDrawCounts,re=k._multiDrawCount,vn=Pt?Z.get(Pt).bytesPerElement:1,Ki=yt.get(W).currentProgram.getUniforms();for(let nn=0;nn<re;nn++)Ki.setValue(D,"_gl_DrawID",nn),se.render(Lt[nn]/vn,On[nn])}else if(k.isInstancedMesh)se.renderInstances(ie,ge,k.count);else if(G.isInstancedBufferGeometry){let Lt=G._maxInstanceCount!==void 0?G._maxInstanceCount:1/0,On=Math.min(G.instanceCount,Lt);se.renderInstances(ie,ge,On)}else se.render(ie,ge)};function ae(R,z,G){R.transparent===!0&&R.side===qe&&R.forceSinglePass===!1?(R.side=ze,R.needsUpdate=!0,gr(R,z,G),R.side=fi,R.needsUpdate=!0,gr(R,z,G),R.side=qe):gr(R,z,G)}this.compile=function(R,z,G=null){G===null&&(G=R),f=Qt.get(G),f.init(z),_.push(f),G.traverseVisible(function(k){k.isLight&&k.layers.test(z.layers)&&(f.pushLight(k),k.castShadow&&f.pushShadow(k))}),R!==G&&R.traverseVisible(function(k){k.isLight&&k.layers.test(z.layers)&&(f.pushLight(k),k.castShadow&&f.pushShadow(k))}),f.setupLights();let W=new Set;return R.traverse(function(k){if(!(k.isMesh||k.isPoints||k.isLine||k.isSprite))return;let lt=k.material;if(lt)if(Array.isArray(lt))for(let mt=0;mt<lt.length;mt++){let It=lt[mt];ae(It,G,k),W.add(It)}else ae(lt,G,k),W.add(lt)}),_.pop(),f=null,W},this.compileAsync=function(R,z,G=null){let W=this.compile(R,z,G);return new Promise(k=>{function lt(){if(W.forEach(function(mt){yt.get(mt).currentProgram.isReady()&&W.delete(mt)}),W.size===0){k(R);return}setTimeout(lt,10)}it.get("KHR_parallel_shader_compile")!==null?lt():setTimeout(lt,10)})};let yn=null;function Nn(R){yn&&yn(R)}function ic(){Si.stop()}function sc(){Si.start()}let Si=new Bh;Si.setAnimationLoop(Nn),typeof self!="undefined"&&Si.setContext(self),this.setAnimationLoop=function(R){yn=R,q.setAnimationLoop(R),R===null?Si.stop():Si.start()},q.addEventListener("sessionstart",ic),q.addEventListener("sessionend",sc),this.render=function(R,z){if(z!==void 0&&z.isCamera!==!0){console.error("THREE.WebGLRenderer.render: camera is not an instance of THREE.Camera.");return}if(C===!0)return;if(R.matrixWorldAutoUpdate===!0&&R.updateMatrixWorld(),z.parent===null&&z.matrixWorldAutoUpdate===!0&&z.updateMatrixWorld(),q.enabled===!0&&q.isPresenting===!0&&(q.cameraAutoUpdate===!0&&q.updateCamera(z),z=q.getCamera()),R.isScene===!0&&R.onBeforeRender(M,R,z,b),f=Qt.get(R,_.length),f.init(z),_.push(f),Ut.multiplyMatrices(z.projectionMatrix,z.matrixWorldInverse),X.setFromProjectionMatrix(Ut),xt=this.localClippingEnabled,st=rt.init(this.clippingPlanes,xt),m=vt.get(R,y.length),m.init(),y.push(m),q.enabled===!0&&q.isPresenting===!0){let lt=M.xr.getDepthSensingMesh();lt!==null&&wa(lt,z,-1/0,M.sortObjects)}wa(R,z,0,M.sortObjects),m.finish(),M.sortObjects===!0&&m.sort(Y,tt),j=q.enabled===!1||q.isPresenting===!1||q.hasDepthSensing()===!1,j&&Bt.addToRenderList(m,R),this.info.render.frame++,st===!0&&rt.beginShadows();let G=f.state.shadowsArray;Et.render(G,R,z),st===!0&&rt.endShadows(),this.info.autoReset===!0&&this.info.reset();let W=m.opaque,k=m.transmissive;if(f.setupLights(),z.isArrayCamera){let lt=z.cameras;if(k.length>0)for(let mt=0,It=lt.length;mt<It;mt++){let Pt=lt[mt];ac(W,k,R,Pt)}j&&Bt.render(R);for(let mt=0,It=lt.length;mt<It;mt++){let Pt=lt[mt];rc(m,R,Pt,Pt.viewport)}}else k.length>0&&ac(W,k,R,z),j&&Bt.render(R),rc(m,R,z);b!==null&&(I.updateMultisampleRenderTarget(b),I.updateRenderTargetMipmap(b)),R.isScene===!0&&R.onAfterRender(M,R,z),fe.resetDefaultState(),v=-1,S=null,_.pop(),_.length>0?(f=_[_.length-1],st===!0&&rt.setGlobalState(M.clippingPlanes,f.state.camera)):f=null,y.pop(),y.length>0?m=y[y.length-1]:m=null};function wa(R,z,G,W){if(R.visible===!1)return;if(R.layers.test(z.layers)){if(R.isGroup)G=R.renderOrder;else if(R.isLOD)R.autoUpdate===!0&&R.update(z);else if(R.isLight)f.pushLight(R),R.castShadow&&f.pushShadow(R);else if(R.isSprite){if(!R.frustumCulled||X.intersectsSprite(R)){W&&At.setFromMatrixPosition(R.matrixWorld).applyMatrix4(Ut);let mt=$.update(R),It=R.material;It.visible&&m.push(R,mt,It,G,At.z,null)}}else if((R.isMesh||R.isLine||R.isPoints)&&(!R.frustumCulled||X.intersectsObject(R))){let mt=$.update(R),It=R.material;if(W&&(R.boundingSphere!==void 0?(R.boundingSphere===null&&R.computeBoundingSphere(),At.copy(R.boundingSphere.center)):(mt.boundingSphere===null&&mt.computeBoundingSphere(),At.copy(mt.boundingSphere.center)),At.applyMatrix4(R.matrixWorld).applyMatrix4(Ut)),Array.isArray(It)){let Pt=mt.groups;for(let Kt=0,qt=Pt.length;Kt<qt;Kt++){let Ft=Pt[Kt],ie=It[Ft.materialIndex];ie&&ie.visible&&m.push(R,mt,ie,G,At.z,Ft)}}else It.visible&&m.push(R,mt,It,G,At.z,null)}}let lt=R.children;for(let mt=0,It=lt.length;mt<It;mt++)wa(lt[mt],z,G,W)}function rc(R,z,G,W){let k=R.opaque,lt=R.transmissive,mt=R.transparent;f.setupLightsView(G),st===!0&&rt.setGlobalState(M.clippingPlanes,G),W&&ht.viewport(w.copy(W)),k.length>0&&mr(k,z,G),lt.length>0&&mr(lt,z,G),mt.length>0&&mr(mt,z,G),ht.buffers.depth.setTest(!0),ht.buffers.depth.setMask(!0),ht.buffers.color.setMask(!0),ht.setPolygonOffset(!1)}function ac(R,z,G,W){if((G.isScene===!0?G.overrideMaterial:null)!==null)return;f.state.transmissionRenderTarget[W.id]===void 0&&(f.state.transmissionRenderTarget[W.id]=new $n(1,1,{generateMipmaps:!0,type:it.has("EXT_color_buffer_half_float")||it.has("EXT_color_buffer_float")?or:Zn,minFilter:Xn,samples:4,stencilBuffer:r,resolveDepthBuffer:!1,resolveStencilBuffer:!1,colorSpace:ne.workingColorSpace}));let lt=f.state.transmissionRenderTarget[W.id],mt=W.viewport||w;lt.setSize(mt.z,mt.w);let It=M.getRenderTarget();M.setRenderTarget(lt),M.getClearColor(U),H=M.getClearAlpha(),H<1&&M.setClearColor(16777215,.5),M.clear(),j&&Bt.render(G);let Pt=M.toneMapping;M.toneMapping=di;let Kt=W.viewport;if(W.viewport!==void 0&&(W.viewport=void 0),f.setupLightsView(W),st===!0&&rt.setGlobalState(M.clippingPlanes,W),mr(R,G,W),I.updateMultisampleRenderTarget(lt),I.updateRenderTargetMipmap(lt),it.has("WEBGL_multisampled_render_to_texture")===!1){let qt=!1;for(let Ft=0,ie=z.length;Ft<ie;Ft++){let pe=z[Ft],ge=pe.object,Ze=pe.geometry,se=pe.material,Lt=pe.group;if(se.side===qe&&ge.layers.test(W.layers)){let On=se.side;se.side=ze,se.needsUpdate=!0,oc(ge,G,W,Ze,se,Lt),se.side=On,se.needsUpdate=!0,qt=!0}}qt===!0&&(I.updateMultisampleRenderTarget(lt),I.updateRenderTargetMipmap(lt))}M.setRenderTarget(It),M.setClearColor(U,H),Kt!==void 0&&(W.viewport=Kt),M.toneMapping=Pt}function mr(R,z,G){let W=z.isScene===!0?z.overrideMaterial:null;for(let k=0,lt=R.length;k<lt;k++){let mt=R[k],It=mt.object,Pt=mt.geometry,Kt=W===null?mt.material:W,qt=mt.group;It.layers.test(G.layers)&&oc(It,z,G,Pt,Kt,qt)}}function oc(R,z,G,W,k,lt){R.onBeforeRender(M,z,G,W,k,lt),R.modelViewMatrix.multiplyMatrices(G.matrixWorldInverse,R.matrixWorld),R.normalMatrix.getNormalMatrix(R.modelViewMatrix),k.onBeforeRender(M,z,G,W,R,lt),k.transparent===!0&&k.side===qe&&k.forceSinglePass===!1?(k.side=ze,k.needsUpdate=!0,M.renderBufferDirect(G,z,W,k,R,lt),k.side=fi,k.needsUpdate=!0,M.renderBufferDirect(G,z,W,k,R,lt),k.side=qe):M.renderBufferDirect(G,z,W,k,R,lt),R.onAfterRender(M,z,G,W,k,lt)}function gr(R,z,G){z.isScene!==!0&&(z=Jt);let W=yt.get(R),k=f.state.lights,lt=f.state.shadowsArray,mt=k.state.version,It=Rt.getParameters(R,k.state,lt,z,G),Pt=Rt.getProgramCacheKey(It),Kt=W.programs;W.environment=R.isMeshStandardMaterial?z.environment:null,W.fog=z.fog,W.envMap=(R.isMeshStandardMaterial?V:T).get(R.envMap||W.environment),W.envMapRotation=W.environment!==null&&R.envMap===null?z.environmentRotation:R.envMapRotation,Kt===void 0&&(R.addEventListener("dispose",Xt),Kt=new Map,W.programs=Kt);let qt=Kt.get(Pt);if(qt!==void 0){if(W.currentProgram===qt&&W.lightsStateVersion===mt)return cc(R,It),qt}else It.uniforms=Rt.getUniforms(R),R.onBeforeCompile(It,M),qt=Rt.acquireProgram(It,Pt),Kt.set(Pt,qt),W.uniforms=It.uniforms;let Ft=W.uniforms;return(!R.isShaderMaterial&&!R.isRawShaderMaterial||R.clipping===!0)&&(Ft.clippingPlanes=rt.uniform),cc(R,It),W.needsLights=wu(R),W.lightsStateVersion=mt,W.needsLights&&(Ft.ambientLightColor.value=k.state.ambient,Ft.lightProbe.value=k.state.probe,Ft.directionalLights.value=k.state.directional,Ft.directionalLightShadows.value=k.state.directionalShadow,Ft.spotLights.value=k.state.spot,Ft.spotLightShadows.value=k.state.spotShadow,Ft.rectAreaLights.value=k.state.rectArea,Ft.ltc_1.value=k.state.rectAreaLTC1,Ft.ltc_2.value=k.state.rectAreaLTC2,Ft.pointLights.value=k.state.point,Ft.pointLightShadows.value=k.state.pointShadow,Ft.hemisphereLights.value=k.state.hemi,Ft.directionalShadowMap.value=k.state.directionalShadowMap,Ft.directionalShadowMatrix.value=k.state.directionalShadowMatrix,Ft.spotShadowMap.value=k.state.spotShadowMap,Ft.spotLightMatrix.value=k.state.spotLightMatrix,Ft.spotLightMap.value=k.state.spotLightMap,Ft.pointShadowMap.value=k.state.pointShadowMap,Ft.pointShadowMatrix.value=k.state.pointShadowMatrix),W.currentProgram=qt,W.uniformsList=null,qt}function lc(R){if(R.uniformsList===null){let z=R.currentProgram.getUniforms();R.uniformsList=ds.seqWithValue(z.seq,R.uniforms)}return R.uniformsList}function cc(R,z){let G=yt.get(R);G.outputColorSpace=z.outputColorSpace,G.batching=z.batching,G.batchingColor=z.batchingColor,G.instancing=z.instancing,G.instancingColor=z.instancingColor,G.instancingMorph=z.instancingMorph,G.skinning=z.skinning,G.morphTargets=z.morphTargets,G.morphNormals=z.morphNormals,G.morphColors=z.morphColors,G.morphTargetsCount=z.morphTargetsCount,G.numClippingPlanes=z.numClippingPlanes,G.numIntersection=z.numClipIntersection,G.vertexAlphas=z.vertexAlphas,G.vertexTangents=z.vertexTangents,G.toneMapping=z.toneMapping}function Eu(R,z,G,W,k){z.isScene!==!0&&(z=Jt),I.resetTextureUnits();let lt=z.fog,mt=W.isMeshStandardMaterial?z.environment:null,It=b===null?M.outputColorSpace:b.isXRRenderTarget===!0?b.texture.colorSpace:Is,Pt=(W.isMeshStandardMaterial?V:T).get(W.envMap||mt),Kt=W.vertexColors===!0&&!!G.attributes.color&&G.attributes.color.itemSize===4,qt=!!G.attributes.tangent&&(!!W.normalMap||W.anisotropy>0),Ft=!!G.morphAttributes.position,ie=!!G.morphAttributes.normal,pe=!!G.morphAttributes.color,ge=di;W.toneMapped&&(b===null||b.isXRRenderTarget===!0)&&(ge=M.toneMapping);let Ze=G.morphAttributes.position||G.morphAttributes.normal||G.morphAttributes.color,se=Ze!==void 0?Ze.length:0,Lt=yt.get(W),On=f.state.lights;if(st===!0&&(xt===!0||R!==S)){let un=R===S&&W.id===v;rt.setState(W,R,un)}let re=!1;W.version===Lt.__version?(Lt.needsLights&&Lt.lightsStateVersion!==On.state.version||Lt.outputColorSpace!==It||k.isBatchedMesh&&Lt.batching===!1||!k.isBatchedMesh&&Lt.batching===!0||k.isBatchedMesh&&Lt.batchingColor===!0&&k.colorTexture===null||k.isBatchedMesh&&Lt.batchingColor===!1&&k.colorTexture!==null||k.isInstancedMesh&&Lt.instancing===!1||!k.isInstancedMesh&&Lt.instancing===!0||k.isSkinnedMesh&&Lt.skinning===!1||!k.isSkinnedMesh&&Lt.skinning===!0||k.isInstancedMesh&&Lt.instancingColor===!0&&k.instanceColor===null||k.isInstancedMesh&&Lt.instancingColor===!1&&k.instanceColor!==null||k.isInstancedMesh&&Lt.instancingMorph===!0&&k.morphTexture===null||k.isInstancedMesh&&Lt.instancingMorph===!1&&k.morphTexture!==null||Lt.envMap!==Pt||W.fog===!0&&Lt.fog!==lt||Lt.numClippingPlanes!==void 0&&(Lt.numClippingPlanes!==rt.numPlanes||Lt.numIntersection!==rt.numIntersection)||Lt.vertexAlphas!==Kt||Lt.vertexTangents!==qt||Lt.morphTargets!==Ft||Lt.morphNormals!==ie||Lt.morphColors!==pe||Lt.toneMapping!==ge||Lt.morphTargetsCount!==se)&&(re=!0):(re=!0,Lt.__version=W.version);let vn=Lt.currentProgram;re===!0&&(vn=gr(W,z,k));let Ki=!1,nn=!1,Ls=!1,_e=vn.getUniforms(),Cn=Lt.uniforms;if(ht.useProgram(vn.program)&&(Ki=!0,nn=!0,Ls=!0),W.id!==v&&(v=W.id,nn=!0),Ki||S!==R){ht.buffers.depth.getReversed()?(ct.copy(R.projectionMatrix),Id(ct),Pd(ct),_e.setValue(D,"projectionMatrix",ct)):_e.setValue(D,"projectionMatrix",R.projectionMatrix),_e.setValue(D,"viewMatrix",R.matrixWorldInverse);let ni=_e.map.cameraPosition;ni!==void 0&&ni.setValue(D,kt.setFromMatrixPosition(R.matrixWorld)),Mt.logarithmicDepthBuffer&&_e.setValue(D,"logDepthBufFC",2/(Math.log(R.far+1)/Math.LN2)),(W.isMeshPhongMaterial||W.isMeshToonMaterial||W.isMeshLambertMaterial||W.isMeshBasicMaterial||W.isMeshStandardMaterial||W.isShaderMaterial)&&_e.setValue(D,"isOrthographic",R.isOrthographicCamera===!0),S!==R&&(S=R,nn=!0,Ls=!0)}if(k.isSkinnedMesh){_e.setOptional(D,k,"bindMatrix"),_e.setOptional(D,k,"bindMatrixInverse");let un=k.skeleton;un&&(un.boneTexture===null&&un.computeBoneTexture(),_e.setValue(D,"boneTexture",un.boneTexture,I))}k.isBatchedMesh&&(_e.setOptional(D,k,"batchingTexture"),_e.setValue(D,"batchingTexture",k._matricesTexture,I),_e.setOptional(D,k,"batchingIdTexture"),_e.setValue(D,"batchingIdTexture",k._indirectTexture,I),_e.setOptional(D,k,"batchingColorTexture"),k._colorsTexture!==null&&_e.setValue(D,"batchingColorTexture",k._colorsTexture,I));let Us=G.morphAttributes;if((Us.position!==void 0||Us.normal!==void 0||Us.color!==void 0)&&Vt.update(k,G,vn),(nn||Lt.receiveShadow!==k.receiveShadow)&&(Lt.receiveShadow=k.receiveShadow,_e.setValue(D,"receiveShadow",k.receiveShadow)),W.isMeshGouraudMaterial&&W.envMap!==null&&(Cn.envMap.value=Pt,Cn.flipEnvMap.value=Pt.isCubeTexture&&Pt.isRenderTargetTexture===!1?-1:1),W.isMeshStandardMaterial&&W.envMap===null&&z.environment!==null&&(Cn.envMapIntensity.value=z.environmentIntensity),nn&&(_e.setValue(D,"toneMappingExposure",M.toneMappingExposure),Lt.needsLights&&bu(Cn,Ls),lt&&W.fog===!0&&dt.refreshFogUniforms(Cn,lt),dt.refreshMaterialUniforms(Cn,W,O,K,f.state.transmissionRenderTarget[R.id]),ds.upload(D,lc(Lt),Cn,I)),W.isShaderMaterial&&W.uniformsNeedUpdate===!0&&(ds.upload(D,lc(Lt),Cn,I),W.uniformsNeedUpdate=!1),W.isSpriteMaterial&&_e.setValue(D,"center",k.center),_e.setValue(D,"modelViewMatrix",k.modelViewMatrix),_e.setValue(D,"normalMatrix",k.normalMatrix),_e.setValue(D,"modelMatrix",k.matrixWorld),W.isShaderMaterial||W.isRawShaderMaterial){let un=W.uniformsGroups;for(let ni=0,ii=un.length;ni<ii;ni++){let hc=un[ni];B.update(hc,vn),B.bind(hc,vn)}}return vn}function bu(R,z){R.ambientLightColor.needsUpdate=z,R.lightProbe.needsUpdate=z,R.directionalLights.needsUpdate=z,R.directionalLightShadows.needsUpdate=z,R.pointLights.needsUpdate=z,R.pointLightShadows.needsUpdate=z,R.spotLights.needsUpdate=z,R.spotLightShadows.needsUpdate=z,R.rectAreaLights.needsUpdate=z,R.hemisphereLights.needsUpdate=z}function wu(R){return R.isMeshLambertMaterial||R.isMeshToonMaterial||R.isMeshPhongMaterial||R.isMeshStandardMaterial||R.isShadowMaterial||R.isShaderMaterial&&R.lights===!0}this.getActiveCubeFace=function(){return A},this.getActiveMipmapLevel=function(){return E},this.getRenderTarget=function(){return b},this.setRenderTargetTextures=function(R,z,G){yt.get(R.texture).__webglTexture=z,yt.get(R.depthTexture).__webglTexture=G;let W=yt.get(R);W.__hasExternalTextures=!0,W.__autoAllocateDepthBuffer=G===void 0,W.__autoAllocateDepthBuffer||it.has("WEBGL_multisampled_render_to_texture")===!0&&(console.warn("THREE.WebGLRenderer: Render-to-texture extension was disabled because an external texture was provided"),W.__useRenderToTexture=!1)},this.setRenderTargetFramebuffer=function(R,z){let G=yt.get(R);G.__webglFramebuffer=z,G.__useDefaultFramebuffer=z===void 0},this.setRenderTarget=function(R,z=0,G=0){b=R,A=z,E=G;let W=!0,k=null,lt=!1,mt=!1;if(R){let Pt=yt.get(R);if(Pt.__useDefaultFramebuffer!==void 0)ht.bindFramebuffer(D.FRAMEBUFFER,null),W=!1;else if(Pt.__webglFramebuffer===void 0)I.setupRenderTarget(R);else if(Pt.__hasExternalTextures)I.rebindTextures(R,yt.get(R.texture).__webglTexture,yt.get(R.depthTexture).__webglTexture);else if(R.depthBuffer){let Ft=R.depthTexture;if(Pt.__boundDepthTexture!==Ft){if(Ft!==null&&yt.has(Ft)&&(R.width!==Ft.image.width||R.height!==Ft.image.height))throw new Error("WebGLRenderTarget: Attached DepthTexture is initialized to the incorrect size.");I.setupDepthRenderbuffer(R)}}let Kt=R.texture;(Kt.isData3DTexture||Kt.isDataArrayTexture||Kt.isCompressedArrayTexture)&&(mt=!0);let qt=yt.get(R).__webglFramebuffer;R.isWebGLCubeRenderTarget?(Array.isArray(qt[z])?k=qt[z][G]:k=qt[z],lt=!0):R.samples>0&&I.useMultisampledRTT(R)===!1?k=yt.get(R).__webglMultisampledFramebuffer:Array.isArray(qt)?k=qt[G]:k=qt,w.copy(R.viewport),P.copy(R.scissor),F=R.scissorTest}else w.copy(nt).multiplyScalar(O).floor(),P.copy(Tt).multiplyScalar(O).floor(),F=Nt;if(ht.bindFramebuffer(D.FRAMEBUFFER,k)&&W&&ht.drawBuffers(R,k),ht.viewport(w),ht.scissor(P),ht.setScissorTest(F),lt){let Pt=yt.get(R.texture);D.framebufferTexture2D(D.FRAMEBUFFER,D.COLOR_ATTACHMENT0,D.TEXTURE_CUBE_MAP_POSITIVE_X+z,Pt.__webglTexture,G)}else if(mt){let Pt=yt.get(R.texture),Kt=z||0;D.framebufferTextureLayer(D.FRAMEBUFFER,D.COLOR_ATTACHMENT0,Pt.__webglTexture,G||0,Kt)}v=-1},this.readRenderTargetPixels=function(R,z,G,W,k,lt,mt){if(!(R&&R.isWebGLRenderTarget)){console.error("THREE.WebGLRenderer.readRenderTargetPixels: renderTarget is not THREE.WebGLRenderTarget.");return}let It=yt.get(R).__webglFramebuffer;if(R.isWebGLCubeRenderTarget&&mt!==void 0&&(It=It[mt]),It){ht.bindFramebuffer(D.FRAMEBUFFER,It);try{let Pt=R.texture,Kt=Pt.format,qt=Pt.type;if(!Mt.textureFormatReadable(Kt)){console.error("THREE.WebGLRenderer.readRenderTargetPixels: renderTarget is not in RGBA or implementation defined format.");return}if(!Mt.textureTypeReadable(qt)){console.error("THREE.WebGLRenderer.readRenderTargetPixels: renderTarget is not in UnsignedByteType or implementation defined type.");return}z>=0&&z<=R.width-W&&G>=0&&G<=R.height-k&&D.readPixels(z,G,W,k,Zt.convert(Kt),Zt.convert(qt),lt)}finally{let Pt=b!==null?yt.get(b).__webglFramebuffer:null;ht.bindFramebuffer(D.FRAMEBUFFER,Pt)}}},this.readRenderTargetPixelsAsync=async function(R,z,G,W,k,lt,mt){if(!(R&&R.isWebGLRenderTarget))throw new Error("THREE.WebGLRenderer.readRenderTargetPixels: renderTarget is not THREE.WebGLRenderTarget.");let It=yt.get(R).__webglFramebuffer;if(R.isWebGLCubeRenderTarget&&mt!==void 0&&(It=It[mt]),It){let Pt=R.texture,Kt=Pt.format,qt=Pt.type;if(!Mt.textureFormatReadable(Kt))throw new Error("THREE.WebGLRenderer.readRenderTargetPixelsAsync: renderTarget is not in RGBA or implementation defined format.");if(!Mt.textureTypeReadable(qt))throw new Error("THREE.WebGLRenderer.readRenderTargetPixelsAsync: renderTarget is not in UnsignedByteType or implementation defined type.");if(z>=0&&z<=R.width-W&&G>=0&&G<=R.height-k){ht.bindFramebuffer(D.FRAMEBUFFER,It);let Ft=D.createBuffer();D.bindBuffer(D.PIXEL_PACK_BUFFER,Ft),D.bufferData(D.PIXEL_PACK_BUFFER,lt.byteLength,D.STREAM_READ),D.readPixels(z,G,W,k,Zt.convert(Kt),Zt.convert(qt),0);let ie=b!==null?yt.get(b).__webglFramebuffer:null;ht.bindFramebuffer(D.FRAMEBUFFER,ie);let pe=D.fenceSync(D.SYNC_GPU_COMMANDS_COMPLETE,0);return D.flush(),await Cd(D,pe,4),D.bindBuffer(D.PIXEL_PACK_BUFFER,Ft),D.getBufferSubData(D.PIXEL_PACK_BUFFER,0,lt),D.deleteBuffer(Ft),D.deleteSync(pe),lt}else throw new Error("THREE.WebGLRenderer.readRenderTargetPixelsAsync: requested read bounds are out of range.")}},this.copyFramebufferToTexture=function(R,z=null,G=0){R.isTexture!==!0&&(Gs("WebGLRenderer: copyFramebufferToTexture function signature has changed."),z=arguments[0]||null,R=arguments[1]);let W=Math.pow(2,-G),k=Math.floor(R.image.width*W),lt=Math.floor(R.image.height*W),mt=z!==null?z.x:0,It=z!==null?z.y:0;I.setTexture2D(R,0),D.copyTexSubImage2D(D.TEXTURE_2D,G,0,0,mt,It,k,lt),ht.unbindTexture()},this.copyTextureToTexture=function(R,z,G=null,W=null,k=0){R.isTexture!==!0&&(Gs("WebGLRenderer: copyTextureToTexture function signature has changed."),W=arguments[0]||null,R=arguments[1],z=arguments[2],k=arguments[3]||0,G=null);let lt,mt,It,Pt,Kt,qt,Ft,ie,pe,ge=R.isCompressedTexture?R.mipmaps[k]:R.image;G!==null?(lt=G.max.x-G.min.x,mt=G.max.y-G.min.y,It=G.isBox3?G.max.z-G.min.z:1,Pt=G.min.x,Kt=G.min.y,qt=G.isBox3?G.min.z:0):(lt=ge.width,mt=ge.height,It=ge.depth||1,Pt=0,Kt=0,qt=0),W!==null?(Ft=W.x,ie=W.y,pe=W.z):(Ft=0,ie=0,pe=0);let Ze=Zt.convert(z.format),se=Zt.convert(z.type),Lt;z.isData3DTexture?(I.setTexture3D(z,0),Lt=D.TEXTURE_3D):z.isDataArrayTexture||z.isCompressedArrayTexture?(I.setTexture2DArray(z,0),Lt=D.TEXTURE_2D_ARRAY):(I.setTexture2D(z,0),Lt=D.TEXTURE_2D),D.pixelStorei(D.UNPACK_FLIP_Y_WEBGL,z.flipY),D.pixelStorei(D.UNPACK_PREMULTIPLY_ALPHA_WEBGL,z.premultiplyAlpha),D.pixelStorei(D.UNPACK_ALIGNMENT,z.unpackAlignment);let On=D.getParameter(D.UNPACK_ROW_LENGTH),re=D.getParameter(D.UNPACK_IMAGE_HEIGHT),vn=D.getParameter(D.UNPACK_SKIP_PIXELS),Ki=D.getParameter(D.UNPACK_SKIP_ROWS),nn=D.getParameter(D.UNPACK_SKIP_IMAGES);D.pixelStorei(D.UNPACK_ROW_LENGTH,ge.width),D.pixelStorei(D.UNPACK_IMAGE_HEIGHT,ge.height),D.pixelStorei(D.UNPACK_SKIP_PIXELS,Pt),D.pixelStorei(D.UNPACK_SKIP_ROWS,Kt),D.pixelStorei(D.UNPACK_SKIP_IMAGES,qt);let Ls=R.isDataArrayTexture||R.isData3DTexture,_e=z.isDataArrayTexture||z.isData3DTexture;if(R.isRenderTargetTexture||R.isDepthTexture){let Cn=yt.get(R),Us=yt.get(z),un=yt.get(Cn.__renderTarget),ni=yt.get(Us.__renderTarget);ht.bindFramebuffer(D.READ_FRAMEBUFFER,un.__webglFramebuffer),ht.bindFramebuffer(D.DRAW_FRAMEBUFFER,ni.__webglFramebuffer);for(let ii=0;ii<It;ii++)Ls&&D.framebufferTextureLayer(D.READ_FRAMEBUFFER,D.COLOR_ATTACHMENT0,yt.get(R).__webglTexture,k,qt+ii),R.isDepthTexture?(_e&&D.framebufferTextureLayer(D.DRAW_FRAMEBUFFER,D.COLOR_ATTACHMENT0,yt.get(z).__webglTexture,k,pe+ii),D.blitFramebuffer(Pt,Kt,lt,mt,Ft,ie,lt,mt,D.DEPTH_BUFFER_BIT,D.NEAREST)):_e?D.copyTexSubImage3D(Lt,k,Ft,ie,pe+ii,Pt,Kt,lt,mt):D.copyTexSubImage2D(Lt,k,Ft,ie,pe+ii,Pt,Kt,lt,mt);ht.bindFramebuffer(D.READ_FRAMEBUFFER,null),ht.bindFramebuffer(D.DRAW_FRAMEBUFFER,null)}else _e?R.isDataTexture||R.isData3DTexture?D.texSubImage3D(Lt,k,Ft,ie,pe,lt,mt,It,Ze,se,ge.data):z.isCompressedArrayTexture?D.compressedTexSubImage3D(Lt,k,Ft,ie,pe,lt,mt,It,Ze,ge.data):D.texSubImage3D(Lt,k,Ft,ie,pe,lt,mt,It,Ze,se,ge):R.isDataTexture?D.texSubImage2D(D.TEXTURE_2D,k,Ft,ie,lt,mt,Ze,se,ge.data):R.isCompressedTexture?D.compressedTexSubImage2D(D.TEXTURE_2D,k,Ft,ie,ge.width,ge.height,Ze,ge.data):D.texSubImage2D(D.TEXTURE_2D,k,Ft,ie,lt,mt,Ze,se,ge);D.pixelStorei(D.UNPACK_ROW_LENGTH,On),D.pixelStorei(D.UNPACK_IMAGE_HEIGHT,re),D.pixelStorei(D.UNPACK_SKIP_PIXELS,vn),D.pixelStorei(D.UNPACK_SKIP_ROWS,Ki),D.pixelStorei(D.UNPACK_SKIP_IMAGES,nn),k===0&&z.generateMipmaps&&D.generateMipmap(Lt),ht.unbindTexture()},this.copyTextureToTexture3D=function(R,z,G=null,W=null,k=0){return R.isTexture!==!0&&(Gs("WebGLRenderer: copyTextureToTexture3D function signature has changed."),G=arguments[0]||null,W=arguments[1]||null,R=arguments[2],z=arguments[3],k=arguments[4]||0),Gs('WebGLRenderer: copyTextureToTexture3D function has been deprecated. Use "copyTextureToTexture" instead.'),this.copyTextureToTexture(R,z,G,W,k)},this.initRenderTarget=function(R){yt.get(R).__webglFramebuffer===void 0&&I.setupRenderTarget(R)},this.initTexture=function(R){R.isCubeTexture?I.setTextureCube(R,0):R.isData3DTexture?I.setTexture3D(R,0):R.isDataArrayTexture||R.isCompressedArrayTexture?I.setTexture2DArray(R,0):I.setTexture2D(R,0),ht.unbindTexture()},this.resetState=function(){A=0,E=0,b=null,ht.reset(),fe.reset()},typeof __THREE_DEVTOOLS__!="undefined"&&__THREE_DEVTOOLS__.dispatchEvent(new CustomEvent("observe",{detail:this}))}get coordinateSystem(){return qn}get outputColorSpace(){return this._outputColorSpace}set outputColorSpace(t){this._outputColorSpace=t;let e=this.getContext();e.drawingBufferColorspace=ne._getDrawingBufferColorSpace(t),e.unpackColorSpace=ne._getUnpackColorSpace()}};var Qe=class i{constructor(t,e=1,n=1e3){this.isFog=!0,this.name="",this.color=new Gt(t),this.near=e,this.far=n}clone(){return new i(this.color,this.near,this.far)}toJSON(){return{type:"Fog",name:this.name,color:this.color.getHex(),near:this.near,far:this.far}}},me=class extends He{constructor(){super(),this.isScene=!0,this.type="Scene",this.background=null,this.environment=null,this.fog=null,this.backgroundBlurriness=0,this.backgroundIntensity=1,this.backgroundRotation=new pn,this.environmentIntensity=1,this.environmentRotation=new pn,this.overrideMaterial=null,typeof __THREE_DEVTOOLS__!="undefined"&&__THREE_DEVTOOLS__.dispatchEvent(new CustomEvent("observe",{detail:this}))}copy(t,e){return super.copy(t,e),t.background!==null&&(this.background=t.background.clone()),t.environment!==null&&(this.environment=t.environment.clone()),t.fog!==null&&(this.fog=t.fog.clone()),this.backgroundBlurriness=t.backgroundBlurriness,this.backgroundIntensity=t.backgroundIntensity,this.backgroundRotation.copy(t.backgroundRotation),this.environmentIntensity=t.environmentIntensity,this.environmentRotation.copy(t.environmentRotation),t.overrideMaterial!==null&&(this.overrideMaterial=t.overrideMaterial.clone()),this.matrixAutoUpdate=t.matrixAutoUpdate,this}toJSON(t){let e=super.toJSON(t);return this.fog!==null&&(e.object.fog=this.fog.toJSON()),this.backgroundBlurriness>0&&(e.object.backgroundBlurriness=this.backgroundBlurriness),this.backgroundIntensity!==1&&(e.object.backgroundIntensity=this.backgroundIntensity),e.object.backgroundRotation=this.backgroundRotation.toArray(),this.environmentIntensity!==1&&(e.object.environmentIntensity=this.environmentIntensity),e.object.environmentRotation=this.environmentRotation.toArray(),e}};var ll=class extends $e{constructor(t=null,e=1,n=1,s,r,a,o,l,c=an,h=an,u,d){super(null,a,o,l,c,h,s,r,u,d),this.isDataTexture=!0,this.image={data:t,width:e,height:n},this.generateMipmaps=!1,this.flipY=!1,this.unpackAlignment=1}};var aa=class extends ke{constructor(t,e,n,s=1){super(t,e,n),this.isInstancedBufferAttribute=!0,this.meshPerAttribute=s}copy(t){return super.copy(t),this.meshPerAttribute=t.meshPerAttribute,this}toJSON(){let t=super.toJSON();return t.meshPerAttribute=this.meshPerAttribute,t.isInstancedBufferAttribute=!0,t}},as=new he,uh=new he,Br=[],dh=new wn,Tg=new he,ks=new Ct,Hs=new Li,oa=class extends Ct{constructor(t,e,n){super(t,e),this.isInstancedMesh=!0,this.instanceMatrix=new aa(new Float32Array(n*16),16),this.instanceColor=null,this.morphTexture=null,this.count=n,this.boundingBox=null,this.boundingSphere=null;for(let s=0;s<n;s++)this.setMatrixAt(s,Tg)}computeBoundingBox(){let t=this.geometry,e=this.count;this.boundingBox===null&&(this.boundingBox=new wn),t.boundingBox===null&&t.computeBoundingBox(),this.boundingBox.makeEmpty();for(let n=0;n<e;n++)this.getMatrixAt(n,as),dh.copy(t.boundingBox).applyMatrix4(as),this.boundingBox.union(dh)}computeBoundingSphere(){let t=this.geometry,e=this.count;this.boundingSphere===null&&(this.boundingSphere=new Li),t.boundingSphere===null&&t.computeBoundingSphere(),this.boundingSphere.makeEmpty();for(let n=0;n<e;n++)this.getMatrixAt(n,as),Hs.copy(t.boundingSphere).applyMatrix4(as),this.boundingSphere.union(Hs)}copy(t,e){return super.copy(t,e),this.instanceMatrix.copy(t.instanceMatrix),t.morphTexture!==null&&(this.morphTexture=t.morphTexture.clone()),t.instanceColor!==null&&(this.instanceColor=t.instanceColor.clone()),this.count=t.count,t.boundingBox!==null&&(this.boundingBox=t.boundingBox.clone()),t.boundingSphere!==null&&(this.boundingSphere=t.boundingSphere.clone()),this}getColorAt(t,e){e.fromArray(this.instanceColor.array,t*3)}getMatrixAt(t,e){e.fromArray(this.instanceMatrix.array,t*16)}getMorphAt(t,e){let n=e.morphTargetInfluences,s=this.morphTexture.source.data.data,r=n.length+1,a=t*r+1;for(let o=0;o<n.length;o++)n[o]=s[a+o]}raycast(t,e){let n=this.matrixWorld,s=this.count;if(ks.geometry=this.geometry,ks.material=this.material,ks.material!==void 0&&(this.boundingSphere===null&&this.computeBoundingSphere(),Hs.copy(this.boundingSphere),Hs.applyMatrix4(n),t.ray.intersectsSphere(Hs)!==!1))for(let r=0;r<s;r++){this.getMatrixAt(r,as),uh.multiplyMatrices(n,as),ks.matrixWorld=uh,ks.raycast(t,Br);for(let a=0,o=Br.length;a<o;a++){let l=Br[a];l.instanceId=r,l.object=this,e.push(l)}Br.length=0}}setColorAt(t,e){this.instanceColor===null&&(this.instanceColor=new aa(new Float32Array(this.instanceMatrix.count*3).fill(1),3)),e.toArray(this.instanceColor.array,t*3)}setMatrixAt(t,e){e.toArray(this.instanceMatrix.array,t*16)}setMorphAt(t,e){let n=e.morphTargetInfluences,s=n.length+1;this.morphTexture===null&&(this.morphTexture=new ll(new Float32Array(s*this.count),s,this.count,Ll,Pn));let r=this.morphTexture.source.data.data,a=0;for(let c=0;c<n.length;c++)a+=n[c];let o=this.geometry.morphTargetsRelative?1:1-a,l=s*t;r[l]=o,r.set(n,l+1)}updateMorphTargets(){}dispose(){return this.dispatchEvent({type:"dispose"}),this.morphTexture!==null&&(this.morphTexture.dispose(),this.morphTexture=null),this}};var Ni=class extends $e{constructor(t,e,n,s,r,a,o,l,c){super(t,e,n,s,r,a,o,l,c),this.isCanvasTexture=!0,this.needsUpdate=!0}},gn=class{constructor(){this.type="Curve",this.arcLengthDivisions=200}getPoint(){return console.warn("THREE.Curve: .getPoint() not implemented."),null}getPointAt(t,e){let n=this.getUtoTmapping(t);return this.getPoint(n,e)}getPoints(t=5){let e=[];for(let n=0;n<=t;n++)e.push(this.getPoint(n/t));return e}getSpacedPoints(t=5){let e=[];for(let n=0;n<=t;n++)e.push(this.getPointAt(n/t));return e}getLength(){let t=this.getLengths();return t[t.length-1]}getLengths(t=this.arcLengthDivisions){if(this.cacheArcLengths&&this.cacheArcLengths.length===t+1&&!this.needsUpdate)return this.cacheArcLengths;this.needsUpdate=!1;let e=[],n,s=this.getPoint(0),r=0;e.push(0);for(let a=1;a<=t;a++)n=this.getPoint(a/t),r+=n.distanceTo(s),e.push(r),s=n;return this.cacheArcLengths=e,e}updateArcLengths(){this.needsUpdate=!0,this.getLengths()}getUtoTmapping(t,e){let n=this.getLengths(),s=0,r=n.length,a;e?a=e:a=t*n[r-1];let o=0,l=r-1,c;for(;o<=l;)if(s=Math.floor(o+(l-o)/2),c=n[s]-a,c<0)o=s+1;else if(c>0)l=s-1;else{l=s;break}if(s=l,n[s]===a)return s/(r-1);let h=n[s],d=n[s+1]-h,p=(a-h)/d;return(s+p)/(r-1)}getTangent(t,e){let s=t-1e-4,r=t+1e-4;s<0&&(s=0),r>1&&(r=1);let a=this.getPoint(s),o=this.getPoint(r),l=e||(a.isVector2?new at:new L);return l.copy(o).sub(a).normalize(),l}getTangentAt(t,e){let n=this.getUtoTmapping(t);return this.getTangent(n,e)}computeFrenetFrames(t,e){let n=new L,s=[],r=[],a=[],o=new L,l=new he;for(let p=0;p<=t;p++){let g=p/t;s[p]=this.getTangentAt(g,new L)}r[0]=new L,a[0]=new L;let c=Number.MAX_VALUE,h=Math.abs(s[0].x),u=Math.abs(s[0].y),d=Math.abs(s[0].z);h<=c&&(c=h,n.set(1,0,0)),u<=c&&(c=u,n.set(0,1,0)),d<=c&&n.set(0,0,1),o.crossVectors(s[0],n).normalize(),r[0].crossVectors(s[0],o),a[0].crossVectors(s[0],r[0]);for(let p=1;p<=t;p++){if(r[p]=r[p-1].clone(),a[p]=a[p-1].clone(),o.crossVectors(s[p-1],s[p]),o.length()>Number.EPSILON){o.normalize();let g=Math.acos(Ce(s[p-1].dot(s[p]),-1,1));r[p].applyMatrix4(l.makeRotationAxis(o,g))}a[p].crossVectors(s[p],r[p])}if(e===!0){let p=Math.acos(Ce(r[0].dot(r[t]),-1,1));p/=t,s[0].dot(o.crossVectors(r[0],r[t]))>0&&(p=-p);for(let g=1;g<=t;g++)r[g].applyMatrix4(l.makeRotationAxis(s[g],p*g)),a[g].crossVectors(s[g],r[g])}return{tangents:s,normals:r,binormals:a}}clone(){return new this.constructor().copy(this)}copy(t){return this.arcLengthDivisions=t.arcLengthDivisions,this}toJSON(){let t={metadata:{version:4.6,type:"Curve",generator:"Curve.toJSON"}};return t.arcLengthDivisions=this.arcLengthDivisions,t.type=this.type,t}fromJSON(t){return this.arcLengthDivisions=t.arcLengthDivisions,this}},js=class extends gn{constructor(t=0,e=0,n=1,s=1,r=0,a=Math.PI*2,o=!1,l=0){super(),this.isEllipseCurve=!0,this.type="EllipseCurve",this.aX=t,this.aY=e,this.xRadius=n,this.yRadius=s,this.aStartAngle=r,this.aEndAngle=a,this.aClockwise=o,this.aRotation=l}getPoint(t,e=new at){let n=e,s=Math.PI*2,r=this.aEndAngle-this.aStartAngle,a=Math.abs(r)<Number.EPSILON;for(;r<0;)r+=s;for(;r>s;)r-=s;r<Number.EPSILON&&(a?r=0:r=s),this.aClockwise===!0&&!a&&(r===s?r=-s:r=r-s);let o=this.aStartAngle+t*r,l=this.aX+this.xRadius*Math.cos(o),c=this.aY+this.yRadius*Math.sin(o);if(this.aRotation!==0){let h=Math.cos(this.aRotation),u=Math.sin(this.aRotation),d=l-this.aX,p=c-this.aY;l=d*h-p*u+this.aX,c=d*u+p*h+this.aY}return n.set(l,c)}copy(t){return super.copy(t),this.aX=t.aX,this.aY=t.aY,this.xRadius=t.xRadius,this.yRadius=t.yRadius,this.aStartAngle=t.aStartAngle,this.aEndAngle=t.aEndAngle,this.aClockwise=t.aClockwise,this.aRotation=t.aRotation,this}toJSON(){let t=super.toJSON();return t.aX=this.aX,t.aY=this.aY,t.xRadius=this.xRadius,t.yRadius=this.yRadius,t.aStartAngle=this.aStartAngle,t.aEndAngle=this.aEndAngle,t.aClockwise=this.aClockwise,t.aRotation=this.aRotation,t}fromJSON(t){return super.fromJSON(t),this.aX=t.aX,this.aY=t.aY,this.xRadius=t.xRadius,this.yRadius=t.yRadius,this.aStartAngle=t.aStartAngle,this.aEndAngle=t.aEndAngle,this.aClockwise=t.aClockwise,this.aRotation=t.aRotation,this}},cl=class extends js{constructor(t,e,n,s,r,a){super(t,e,n,n,s,r,a),this.isArcCurve=!0,this.type="ArcCurve"}};function kl(){let i=0,t=0,e=0,n=0;function s(r,a,o,l){i=r,t=o,e=-3*r+3*a-2*o-l,n=2*r-2*a+o+l}return{initCatmullRom:function(r,a,o,l,c){s(a,o,c*(o-r),c*(l-a))},initNonuniformCatmullRom:function(r,a,o,l,c,h,u){let d=(a-r)/c-(o-r)/(c+h)+(o-a)/h,p=(o-a)/h-(l-a)/(h+u)+(l-o)/u;d*=h,p*=h,s(a,o,d,p)},calc:function(r){let a=r*r,o=a*r;return i+t*r+e*a+n*o}}}var zr=new L,to=new kl,eo=new kl,no=new kl,Oi=class extends gn{constructor(t=[],e=!1,n="centripetal",s=.5){super(),this.isCatmullRomCurve3=!0,this.type="CatmullRomCurve3",this.points=t,this.closed=e,this.curveType=n,this.tension=s}getPoint(t,e=new L){let n=e,s=this.points,r=s.length,a=(r-(this.closed?0:1))*t,o=Math.floor(a),l=a-o;this.closed?o+=o>0?0:(Math.floor(Math.abs(o)/r)+1)*r:l===0&&o===r-1&&(o=r-2,l=1);let c,h;this.closed||o>0?c=s[(o-1)%r]:(zr.subVectors(s[0],s[1]).add(s[0]),c=zr);let u=s[o%r],d=s[(o+1)%r];if(this.closed||o+2<r?h=s[(o+2)%r]:(zr.subVectors(s[r-1],s[r-2]).add(s[r-1]),h=zr),this.curveType==="centripetal"||this.curveType==="chordal"){let p=this.curveType==="chordal"?.5:.25,g=Math.pow(c.distanceToSquared(u),p),x=Math.pow(u.distanceToSquared(d),p),m=Math.pow(d.distanceToSquared(h),p);x<1e-4&&(x=1),g<1e-4&&(g=x),m<1e-4&&(m=x),to.initNonuniformCatmullRom(c.x,u.x,d.x,h.x,g,x,m),eo.initNonuniformCatmullRom(c.y,u.y,d.y,h.y,g,x,m),no.initNonuniformCatmullRom(c.z,u.z,d.z,h.z,g,x,m)}else this.curveType==="catmullrom"&&(to.initCatmullRom(c.x,u.x,d.x,h.x,this.tension),eo.initCatmullRom(c.y,u.y,d.y,h.y,this.tension),no.initCatmullRom(c.z,u.z,d.z,h.z,this.tension));return n.set(to.calc(l),eo.calc(l),no.calc(l)),n}copy(t){super.copy(t),this.points=[];for(let e=0,n=t.points.length;e<n;e++){let s=t.points[e];this.points.push(s.clone())}return this.closed=t.closed,this.curveType=t.curveType,this.tension=t.tension,this}toJSON(){let t=super.toJSON();t.points=[];for(let e=0,n=this.points.length;e<n;e++){let s=this.points[e];t.points.push(s.toArray())}return t.closed=this.closed,t.curveType=this.curveType,t.tension=this.tension,t}fromJSON(t){super.fromJSON(t),this.points=[];for(let e=0,n=t.points.length;e<n;e++){let s=t.points[e];this.points.push(new L().fromArray(s))}return this.closed=t.closed,this.curveType=t.curveType,this.tension=t.tension,this}};function fh(i,t,e,n,s){let r=(n-t)*.5,a=(s-e)*.5,o=i*i,l=i*o;return(2*e-2*n+r+a)*l+(-3*e+3*n-2*r-a)*o+r*i+e}function Ag(i,t){let e=1-i;return e*e*t}function Rg(i,t){return 2*(1-i)*i*t}function Cg(i,t){return i*i*t}function Ys(i,t,e,n){return Ag(i,t)+Rg(i,e)+Cg(i,n)}function Ig(i,t){let e=1-i;return e*e*e*t}function Pg(i,t){let e=1-i;return 3*e*e*i*t}function Fg(i,t){return 3*(1-i)*i*i*t}function Dg(i,t){return i*i*i*t}function Zs(i,t,e,n,s){return Ig(i,t)+Pg(i,e)+Fg(i,n)+Dg(i,s)}var la=class extends gn{constructor(t=new at,e=new at,n=new at,s=new at){super(),this.isCubicBezierCurve=!0,this.type="CubicBezierCurve",this.v0=t,this.v1=e,this.v2=n,this.v3=s}getPoint(t,e=new at){let n=e,s=this.v0,r=this.v1,a=this.v2,o=this.v3;return n.set(Zs(t,s.x,r.x,a.x,o.x),Zs(t,s.y,r.y,a.y,o.y)),n}copy(t){return super.copy(t),this.v0.copy(t.v0),this.v1.copy(t.v1),this.v2.copy(t.v2),this.v3.copy(t.v3),this}toJSON(){let t=super.toJSON();return t.v0=this.v0.toArray(),t.v1=this.v1.toArray(),t.v2=this.v2.toArray(),t.v3=this.v3.toArray(),t}fromJSON(t){return super.fromJSON(t),this.v0.fromArray(t.v0),this.v1.fromArray(t.v1),this.v2.fromArray(t.v2),this.v3.fromArray(t.v3),this}},hl=class extends gn{constructor(t=new L,e=new L,n=new L,s=new L){super(),this.isCubicBezierCurve3=!0,this.type="CubicBezierCurve3",this.v0=t,this.v1=e,this.v2=n,this.v3=s}getPoint(t,e=new L){let n=e,s=this.v0,r=this.v1,a=this.v2,o=this.v3;return n.set(Zs(t,s.x,r.x,a.x,o.x),Zs(t,s.y,r.y,a.y,o.y),Zs(t,s.z,r.z,a.z,o.z)),n}copy(t){return super.copy(t),this.v0.copy(t.v0),this.v1.copy(t.v1),this.v2.copy(t.v2),this.v3.copy(t.v3),this}toJSON(){let t=super.toJSON();return t.v0=this.v0.toArray(),t.v1=this.v1.toArray(),t.v2=this.v2.toArray(),t.v3=this.v3.toArray(),t}fromJSON(t){return super.fromJSON(t),this.v0.fromArray(t.v0),this.v1.fromArray(t.v1),this.v2.fromArray(t.v2),this.v3.fromArray(t.v3),this}},ca=class extends gn{constructor(t=new at,e=new at){super(),this.isLineCurve=!0,this.type="LineCurve",this.v1=t,this.v2=e}getPoint(t,e=new at){let n=e;return t===1?n.copy(this.v2):(n.copy(this.v2).sub(this.v1),n.multiplyScalar(t).add(this.v1)),n}getPointAt(t,e){return this.getPoint(t,e)}getTangent(t,e=new at){return e.subVectors(this.v2,this.v1).normalize()}getTangentAt(t,e){return this.getTangent(t,e)}copy(t){return super.copy(t),this.v1.copy(t.v1),this.v2.copy(t.v2),this}toJSON(){let t=super.toJSON();return t.v1=this.v1.toArray(),t.v2=this.v2.toArray(),t}fromJSON(t){return super.fromJSON(t),this.v1.fromArray(t.v1),this.v2.fromArray(t.v2),this}},ul=class extends gn{constructor(t=new L,e=new L){super(),this.isLineCurve3=!0,this.type="LineCurve3",this.v1=t,this.v2=e}getPoint(t,e=new L){let n=e;return t===1?n.copy(this.v2):(n.copy(this.v2).sub(this.v1),n.multiplyScalar(t).add(this.v1)),n}getPointAt(t,e){return this.getPoint(t,e)}getTangent(t,e=new L){return e.subVectors(this.v2,this.v1).normalize()}getTangentAt(t,e){return this.getTangent(t,e)}copy(t){return super.copy(t),this.v1.copy(t.v1),this.v2.copy(t.v2),this}toJSON(){let t=super.toJSON();return t.v1=this.v1.toArray(),t.v2=this.v2.toArray(),t}fromJSON(t){return super.fromJSON(t),this.v1.fromArray(t.v1),this.v2.fromArray(t.v2),this}},ha=class extends gn{constructor(t=new at,e=new at,n=new at){super(),this.isQuadraticBezierCurve=!0,this.type="QuadraticBezierCurve",this.v0=t,this.v1=e,this.v2=n}getPoint(t,e=new at){let n=e,s=this.v0,r=this.v1,a=this.v2;return n.set(Ys(t,s.x,r.x,a.x),Ys(t,s.y,r.y,a.y)),n}copy(t){return super.copy(t),this.v0.copy(t.v0),this.v1.copy(t.v1),this.v2.copy(t.v2),this}toJSON(){let t=super.toJSON();return t.v0=this.v0.toArray(),t.v1=this.v1.toArray(),t.v2=this.v2.toArray(),t}fromJSON(t){return super.fromJSON(t),this.v0.fromArray(t.v0),this.v1.fromArray(t.v1),this.v2.fromArray(t.v2),this}},ua=class extends gn{constructor(t=new L,e=new L,n=new L){super(),this.isQuadraticBezierCurve3=!0,this.type="QuadraticBezierCurve3",this.v0=t,this.v1=e,this.v2=n}getPoint(t,e=new L){let n=e,s=this.v0,r=this.v1,a=this.v2;return n.set(Ys(t,s.x,r.x,a.x),Ys(t,s.y,r.y,a.y),Ys(t,s.z,r.z,a.z)),n}copy(t){return super.copy(t),this.v0.copy(t.v0),this.v1.copy(t.v1),this.v2.copy(t.v2),this}toJSON(){let t=super.toJSON();return t.v0=this.v0.toArray(),t.v1=this.v1.toArray(),t.v2=this.v2.toArray(),t}fromJSON(t){return super.fromJSON(t),this.v0.fromArray(t.v0),this.v1.fromArray(t.v1),this.v2.fromArray(t.v2),this}},Ss=class extends gn{constructor(t=[]){super(),this.isSplineCurve=!0,this.type="SplineCurve",this.points=t}getPoint(t,e=new at){let n=e,s=this.points,r=(s.length-1)*t,a=Math.floor(r),o=r-a,l=s[a===0?a:a-1],c=s[a],h=s[a>s.length-2?s.length-1:a+1],u=s[a>s.length-3?s.length-1:a+2];return n.set(fh(o,l.x,c.x,h.x,u.x),fh(o,l.y,c.y,h.y,u.y)),n}copy(t){super.copy(t),this.points=[];for(let e=0,n=t.points.length;e<n;e++){let s=t.points[e];this.points.push(s.clone())}return this}toJSON(){let t=super.toJSON();t.points=[];for(let e=0,n=this.points.length;e<n;e++){let s=this.points[e];t.points.push(s.toArray())}return t}fromJSON(t){super.fromJSON(t),this.points=[];for(let e=0,n=t.points.length;e<n;e++){let s=t.points[e];this.points.push(new at().fromArray(s))}return this}},da=Object.freeze({__proto__:null,ArcCurve:cl,CatmullRomCurve3:Oi,CubicBezierCurve:la,CubicBezierCurve3:hl,EllipseCurve:js,LineCurve:ca,LineCurve3:ul,QuadraticBezierCurve:ha,QuadraticBezierCurve3:ua,SplineCurve:Ss}),dl=class extends gn{constructor(){super(),this.type="CurvePath",this.curves=[],this.autoClose=!1}add(t){this.curves.push(t)}closePath(){let t=this.curves[0].getPoint(0),e=this.curves[this.curves.length-1].getPoint(1);if(!t.equals(e)){let n=t.isVector2===!0?"LineCurve":"LineCurve3";this.curves.push(new da[n](e,t))}return this}getPoint(t,e){let n=t*this.getLength(),s=this.getCurveLengths(),r=0;for(;r<s.length;){if(s[r]>=n){let a=s[r]-n,o=this.curves[r],l=o.getLength(),c=l===0?0:1-a/l;return o.getPointAt(c,e)}r++}return null}getLength(){let t=this.getCurveLengths();return t[t.length-1]}updateArcLengths(){this.needsUpdate=!0,this.cacheLengths=null,this.getCurveLengths()}getCurveLengths(){if(this.cacheLengths&&this.cacheLengths.length===this.curves.length)return this.cacheLengths;let t=[],e=0;for(let n=0,s=this.curves.length;n<s;n++)e+=this.curves[n].getLength(),t.push(e);return this.cacheLengths=t,t}getSpacedPoints(t=40){let e=[];for(let n=0;n<=t;n++)e.push(this.getPoint(n/t));return this.autoClose&&e.push(e[0]),e}getPoints(t=12){let e=[],n;for(let s=0,r=this.curves;s<r.length;s++){let a=r[s],o=a.isEllipseCurve?t*2:a.isLineCurve||a.isLineCurve3?1:a.isSplineCurve?t*a.points.length:t,l=a.getPoints(o);for(let c=0;c<l.length;c++){let h=l[c];n&&n.equals(h)||(e.push(h),n=h)}}return this.autoClose&&e.length>1&&!e[e.length-1].equals(e[0])&&e.push(e[0]),e}copy(t){super.copy(t),this.curves=[];for(let e=0,n=t.curves.length;e<n;e++){let s=t.curves[e];this.curves.push(s.clone())}return this.autoClose=t.autoClose,this}toJSON(){let t=super.toJSON();t.autoClose=this.autoClose,t.curves=[];for(let e=0,n=this.curves.length;e<n;e++){let s=this.curves[e];t.curves.push(s.toJSON())}return t}fromJSON(t){super.fromJSON(t),this.autoClose=t.autoClose,this.curves=[];for(let e=0,n=t.curves.length;e<n;e++){let s=t.curves[e];this.curves.push(new da[s.type]().fromJSON(s))}return this}},tr=class extends dl{constructor(t){super(),this.type="Path",this.currentPoint=new at,t&&this.setFromPoints(t)}setFromPoints(t){this.moveTo(t[0].x,t[0].y);for(let e=1,n=t.length;e<n;e++)this.lineTo(t[e].x,t[e].y);return this}moveTo(t,e){return this.currentPoint.set(t,e),this}lineTo(t,e){let n=new ca(this.currentPoint.clone(),new at(t,e));return this.curves.push(n),this.currentPoint.set(t,e),this}quadraticCurveTo(t,e,n,s){let r=new ha(this.currentPoint.clone(),new at(t,e),new at(n,s));return this.curves.push(r),this.currentPoint.set(n,s),this}bezierCurveTo(t,e,n,s,r,a){let o=new la(this.currentPoint.clone(),new at(t,e),new at(n,s),new at(r,a));return this.curves.push(o),this.currentPoint.set(r,a),this}splineThru(t){let e=[this.currentPoint.clone()].concat(t),n=new Ss(e);return this.curves.push(n),this.currentPoint.copy(t[t.length-1]),this}arc(t,e,n,s,r,a){let o=this.currentPoint.x,l=this.currentPoint.y;return this.absarc(t+o,e+l,n,s,r,a),this}absarc(t,e,n,s,r,a){return this.absellipse(t,e,n,n,s,r,a),this}ellipse(t,e,n,s,r,a,o,l){let c=this.currentPoint.x,h=this.currentPoint.y;return this.absellipse(t+c,e+h,n,s,r,a,o,l),this}absellipse(t,e,n,s,r,a,o,l){let c=new js(t,e,n,s,r,a,o,l);if(this.curves.length>0){let u=c.getPoint(0);u.equals(this.currentPoint)||this.lineTo(u.x,u.y)}this.curves.push(c);let h=c.getPoint(1);return this.currentPoint.copy(h),this}copy(t){return super.copy(t),this.currentPoint.copy(t.currentPoint),this}toJSON(){let t=super.toJSON();return t.currentPoint=this.currentPoint.toArray(),t}fromJSON(t){return super.fromJSON(t),this.currentPoint.fromArray(t.currentPoint),this}},er=class i extends Ie{constructor(t=[new at(0,-.5),new at(.5,0),new at(0,.5)],e=12,n=0,s=Math.PI*2){super(),this.type="LatheGeometry",this.parameters={points:t,segments:e,phiStart:n,phiLength:s},e=Math.floor(e),s=Ce(s,0,Math.PI*2);let r=[],a=[],o=[],l=[],c=[],h=1/e,u=new L,d=new at,p=new L,g=new L,x=new L,m=0,f=0;for(let y=0;y<=t.length-1;y++)switch(y){case 0:m=t[y+1].x-t[y].x,f=t[y+1].y-t[y].y,p.x=f*1,p.y=-m,p.z=f*0,x.copy(p),p.normalize(),l.push(p.x,p.y,p.z);break;case t.length-1:l.push(x.x,x.y,x.z);break;default:m=t[y+1].x-t[y].x,f=t[y+1].y-t[y].y,p.x=f*1,p.y=-m,p.z=f*0,g.copy(p),p.x+=x.x,p.y+=x.y,p.z+=x.z,p.normalize(),l.push(p.x,p.y,p.z),x.copy(g)}for(let y=0;y<=e;y++){let _=n+y*h*s,M=Math.sin(_),C=Math.cos(_);for(let A=0;A<=t.length-1;A++){u.x=t[A].x*M,u.y=t[A].y,u.z=t[A].x*C,a.push(u.x,u.y,u.z),d.x=y/e,d.y=A/(t.length-1),o.push(d.x,d.y);let E=l[3*A+0]*M,b=l[3*A+1],v=l[3*A+0]*C;c.push(E,b,v)}}for(let y=0;y<e;y++)for(let _=0;_<t.length-1;_++){let M=_+y*t.length,C=M,A=M+t.length,E=M+t.length+1,b=M+1;r.push(C,A,b),r.push(E,b,A)}this.setIndex(r),this.setAttribute("position",new jt(a,3)),this.setAttribute("uv",new jt(o,2)),this.setAttribute("normal",new jt(c,3))}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}static fromJSON(t){return new i(t.points,t.segments,t.phiStart,t.phiLength)}},Es=class i extends er{constructor(t=1,e=1,n=4,s=8){let r=new tr;r.absarc(0,-e/2,t,Math.PI*1.5,0),r.absarc(0,e/2,t,0,Math.PI*.5),super(r.getPoints(n),s),this.type="CapsuleGeometry",this.parameters={radius:t,length:e,capSegments:n,radialSegments:s}}static fromJSON(t){return new i(t.radius,t.length,t.capSegments,t.radialSegments)}};var bs=class i extends Ie{constructor(t=1,e=1,n=1,s=32,r=1,a=!1,o=0,l=Math.PI*2){super(),this.type="CylinderGeometry",this.parameters={radiusTop:t,radiusBottom:e,height:n,radialSegments:s,heightSegments:r,openEnded:a,thetaStart:o,thetaLength:l};let c=this;s=Math.floor(s),r=Math.floor(r);let h=[],u=[],d=[],p=[],g=0,x=[],m=n/2,f=0;y(),a===!1&&(t>0&&_(!0),e>0&&_(!1)),this.setIndex(h),this.setAttribute("position",new jt(u,3)),this.setAttribute("normal",new jt(d,3)),this.setAttribute("uv",new jt(p,2));function y(){let M=new L,C=new L,A=0,E=(e-t)/n;for(let b=0;b<=r;b++){let v=[],S=b/r,w=S*(e-t)+t;for(let P=0;P<=s;P++){let F=P/s,U=F*l+o,H=Math.sin(U),N=Math.cos(U);C.x=w*H,C.y=-S*n+m,C.z=w*N,u.push(C.x,C.y,C.z),M.set(H,E,N).normalize(),d.push(M.x,M.y,M.z),p.push(F,1-S),v.push(g++)}x.push(v)}for(let b=0;b<s;b++)for(let v=0;v<r;v++){let S=x[v][b],w=x[v+1][b],P=x[v+1][b+1],F=x[v][b+1];(t>0||v!==0)&&(h.push(S,w,F),A+=3),(e>0||v!==r-1)&&(h.push(w,P,F),A+=3)}c.addGroup(f,A,0),f+=A}function _(M){let C=g,A=new at,E=new L,b=0,v=M===!0?t:e,S=M===!0?1:-1;for(let P=1;P<=s;P++)u.push(0,m*S,0),d.push(0,S,0),p.push(.5,.5),g++;let w=g;for(let P=0;P<=s;P++){let U=P/s*l+o,H=Math.cos(U),N=Math.sin(U);E.x=v*N,E.y=m*S,E.z=v*H,u.push(E.x,E.y,E.z),d.push(0,S,0),A.x=H*.5+.5,A.y=N*.5*S+.5,p.push(A.x,A.y),g++}for(let P=0;P<s;P++){let F=C+P,U=w+P;M===!0?h.push(U,U+1,F):h.push(U+1,U,F),b+=3}c.addGroup(f,b,M===!0?1:2),f+=b}}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}static fromJSON(t){return new i(t.radiusTop,t.radiusBottom,t.height,t.radialSegments,t.heightSegments,t.openEnded,t.thetaStart,t.thetaLength)}};var fl=class i extends Ie{constructor(t=[],e=[],n=1,s=0){super(),this.type="PolyhedronGeometry",this.parameters={vertices:t,indices:e,radius:n,detail:s};let r=[],a=[];o(s),c(n),h(),this.setAttribute("position",new jt(r,3)),this.setAttribute("normal",new jt(r.slice(),3)),this.setAttribute("uv",new jt(a,2)),s===0?this.computeVertexNormals():this.normalizeNormals();function o(y){let _=new L,M=new L,C=new L;for(let A=0;A<e.length;A+=3)p(e[A+0],_),p(e[A+1],M),p(e[A+2],C),l(_,M,C,y)}function l(y,_,M,C){let A=C+1,E=[];for(let b=0;b<=A;b++){E[b]=[];let v=y.clone().lerp(M,b/A),S=_.clone().lerp(M,b/A),w=A-b;for(let P=0;P<=w;P++)P===0&&b===A?E[b][P]=v:E[b][P]=v.clone().lerp(S,P/w)}for(let b=0;b<A;b++)for(let v=0;v<2*(A-b)-1;v++){let S=Math.floor(v/2);v%2===0?(d(E[b][S+1]),d(E[b+1][S]),d(E[b][S])):(d(E[b][S+1]),d(E[b+1][S+1]),d(E[b+1][S]))}}function c(y){let _=new L;for(let M=0;M<r.length;M+=3)_.x=r[M+0],_.y=r[M+1],_.z=r[M+2],_.normalize().multiplyScalar(y),r[M+0]=_.x,r[M+1]=_.y,r[M+2]=_.z}function h(){let y=new L;for(let _=0;_<r.length;_+=3){y.x=r[_+0],y.y=r[_+1],y.z=r[_+2];let M=m(y)/2/Math.PI+.5,C=f(y)/Math.PI+.5;a.push(M,1-C)}g(),u()}function u(){for(let y=0;y<a.length;y+=6){let _=a[y+0],M=a[y+2],C=a[y+4],A=Math.max(_,M,C),E=Math.min(_,M,C);A>.9&&E<.1&&(_<.2&&(a[y+0]+=1),M<.2&&(a[y+2]+=1),C<.2&&(a[y+4]+=1))}}function d(y){r.push(y.x,y.y,y.z)}function p(y,_){let M=y*3;_.x=t[M+0],_.y=t[M+1],_.z=t[M+2]}function g(){let y=new L,_=new L,M=new L,C=new L,A=new at,E=new at,b=new at;for(let v=0,S=0;v<r.length;v+=9,S+=6){y.set(r[v+0],r[v+1],r[v+2]),_.set(r[v+3],r[v+4],r[v+5]),M.set(r[v+6],r[v+7],r[v+8]),A.set(a[S+0],a[S+1]),E.set(a[S+2],a[S+3]),b.set(a[S+4],a[S+5]),C.copy(y).add(_).add(M).divideScalar(3);let w=m(C);x(A,S+0,y,w),x(E,S+2,_,w),x(b,S+4,M,w)}}function x(y,_,M,C){C<0&&y.x===1&&(a[_]=y.x-1),M.x===0&&M.z===0&&(a[_]=C/2/Math.PI+.5)}function m(y){return Math.atan2(y.z,-y.x)}function f(y){return Math.atan2(-y.y,Math.sqrt(y.x*y.x+y.z*y.z))}}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}static fromJSON(t){return new i(t.vertices,t.indices,t.radius,t.details)}};var Bi=class extends tr{constructor(t){super(t),this.uuid=Gi(),this.type="Shape",this.holes=[]}getPointsHoles(t){let e=[];for(let n=0,s=this.holes.length;n<s;n++)e[n]=this.holes[n].getPoints(t);return e}extractPoints(t){return{shape:this.getPoints(t),holes:this.getPointsHoles(t)}}copy(t){super.copy(t),this.holes=[];for(let e=0,n=t.holes.length;e<n;e++){let s=t.holes[e];this.holes.push(s.clone())}return this}toJSON(){let t=super.toJSON();t.uuid=this.uuid,t.holes=[];for(let e=0,n=this.holes.length;e<n;e++){let s=this.holes[e];t.holes.push(s.toJSON())}return t}fromJSON(t){super.fromJSON(t),this.uuid=t.uuid,this.holes=[];for(let e=0,n=t.holes.length;e<n;e++){let s=t.holes[e];this.holes.push(new tr().fromJSON(s))}return this}},Lg={triangulate:function(i,t,e=2){let n=t&&t.length,s=n?t[0]*e:i.length,r=Gh(i,0,s,e,!0),a=[];if(!r||r.next===r.prev)return a;let o,l,c,h,u,d,p;if(n&&(r=zg(i,t,r,e)),i.length>80*e){o=c=i[0],l=h=i[1];for(let g=e;g<s;g+=e)u=i[g],d=i[g+1],u<o&&(o=u),d<l&&(l=d),u>c&&(c=u),d>h&&(h=d);p=Math.max(c-o,h-l),p=p!==0?32767/p:0}return nr(r,a,e,o,l,p,0),a}};function Gh(i,t,e,n,s){let r,a;if(s===$g(i,t,e,n)>0)for(r=t;r<e;r+=n)a=ph(r,i[r],i[r+1],a);else for(r=e-n;r>=t;r-=n)a=ph(r,i[r],i[r+1],a);return a&&xa(a,a.next)&&(sr(a),a=a.next),a}function zi(i,t){if(!i)return i;t||(t=i);let e=i,n;do if(n=!1,!e.steiner&&(xa(e,e.next)||ve(e.prev,e,e.next)===0)){if(sr(e),e=t=e.prev,e===e.next)break;n=!0}else e=e.next;while(n||e!==t);return t}function nr(i,t,e,n,s,r,a){if(!i)return;!a&&r&&Wg(i,n,s,r);let o=i,l,c;for(;i.prev!==i.next;){if(l=i.prev,c=i.next,r?Ng(i,n,s,r):Ug(i)){t.push(l.i/e|0),t.push(i.i/e|0),t.push(c.i/e|0),sr(i),i=c.next,o=c.next;continue}if(i=c,i===o){a?a===1?(i=Og(zi(i),t,e),nr(i,t,e,n,s,r,2)):a===2&&Bg(i,t,e,n,s,r):nr(zi(i),t,e,n,s,r,1);break}}}function Ug(i){let t=i.prev,e=i,n=i.next;if(ve(t,e,n)>=0)return!1;let s=t.x,r=e.x,a=n.x,o=t.y,l=e.y,c=n.y,h=s<r?s<a?s:a:r<a?r:a,u=o<l?o<c?o:c:l<c?l:c,d=s>r?s>a?s:a:r>a?r:a,p=o>l?o>c?o:c:l>c?l:c,g=n.next;for(;g!==t;){if(g.x>=h&&g.x<=d&&g.y>=u&&g.y<=p&&cs(s,o,r,l,a,c,g.x,g.y)&&ve(g.prev,g,g.next)>=0)return!1;g=g.next}return!0}function Ng(i,t,e,n){let s=i.prev,r=i,a=i.next;if(ve(s,r,a)>=0)return!1;let o=s.x,l=r.x,c=a.x,h=s.y,u=r.y,d=a.y,p=o<l?o<c?o:c:l<c?l:c,g=h<u?h<d?h:d:u<d?u:d,x=o>l?o>c?o:c:l>c?l:c,m=h>u?h>d?h:d:u>d?u:d,f=pl(p,g,t,e,n),y=pl(x,m,t,e,n),_=i.prevZ,M=i.nextZ;for(;_&&_.z>=f&&M&&M.z<=y;){if(_.x>=p&&_.x<=x&&_.y>=g&&_.y<=m&&_!==s&&_!==a&&cs(o,h,l,u,c,d,_.x,_.y)&&ve(_.prev,_,_.next)>=0||(_=_.prevZ,M.x>=p&&M.x<=x&&M.y>=g&&M.y<=m&&M!==s&&M!==a&&cs(o,h,l,u,c,d,M.x,M.y)&&ve(M.prev,M,M.next)>=0))return!1;M=M.nextZ}for(;_&&_.z>=f;){if(_.x>=p&&_.x<=x&&_.y>=g&&_.y<=m&&_!==s&&_!==a&&cs(o,h,l,u,c,d,_.x,_.y)&&ve(_.prev,_,_.next)>=0)return!1;_=_.prevZ}for(;M&&M.z<=y;){if(M.x>=p&&M.x<=x&&M.y>=g&&M.y<=m&&M!==s&&M!==a&&cs(o,h,l,u,c,d,M.x,M.y)&&ve(M.prev,M,M.next)>=0)return!1;M=M.nextZ}return!0}function Og(i,t,e){let n=i;do{let s=n.prev,r=n.next.next;!xa(s,r)&&Wh(s,n,n.next,r)&&ir(s,r)&&ir(r,s)&&(t.push(s.i/e|0),t.push(n.i/e|0),t.push(r.i/e|0),sr(n),sr(n.next),n=i=r),n=n.next}while(n!==i);return zi(n)}function Bg(i,t,e,n,s,r){let a=i;do{let o=a.next.next;for(;o!==a.prev;){if(a.i!==o.i&&qg(a,o)){let l=Kh(a,o);a=zi(a,a.next),l=zi(l,l.next),nr(a,t,e,n,s,r,0),nr(l,t,e,n,s,r,0);return}o=o.next}a=a.next}while(a!==i)}function zg(i,t,e,n){let s=[],r,a,o,l,c;for(r=0,a=t.length;r<a;r++)o=t[r]*n,l=r<a-1?t[r+1]*n:i.length,c=Gh(i,o,l,n,!1),c===c.next&&(c.steiner=!0),s.push(Xg(c));for(s.sort(kg),r=0;r<s.length;r++)e=Hg(s[r],e);return e}function kg(i,t){return i.x-t.x}function Hg(i,t){let e=Vg(i,t);if(!e)return t;let n=Kh(e,i);return zi(n,n.next),zi(e,e.next)}function Vg(i,t){let e=t,n=-1/0,s,r=i.x,a=i.y;do{if(a<=e.y&&a>=e.next.y&&e.next.y!==e.y){let d=e.x+(a-e.y)*(e.next.x-e.x)/(e.next.y-e.y);if(d<=r&&d>n&&(n=d,s=e.x<e.next.x?e:e.next,d===r))return s}e=e.next}while(e!==t);if(!s)return null;let o=s,l=s.x,c=s.y,h=1/0,u;e=s;do r>=e.x&&e.x>=l&&r!==e.x&&cs(a<c?r:n,a,l,c,a<c?n:r,a,e.x,e.y)&&(u=Math.abs(a-e.y)/(r-e.x),ir(e,i)&&(u<h||u===h&&(e.x>s.x||e.x===s.x&&Gg(s,e)))&&(s=e,h=u)),e=e.next;while(e!==o);return s}function Gg(i,t){return ve(i.prev,i,t.prev)<0&&ve(t.next,i,i.next)<0}function Wg(i,t,e,n){let s=i;do s.z===0&&(s.z=pl(s.x,s.y,t,e,n)),s.prevZ=s.prev,s.nextZ=s.next,s=s.next;while(s!==i);s.prevZ.nextZ=null,s.prevZ=null,Kg(s)}function Kg(i){let t,e,n,s,r,a,o,l,c=1;do{for(e=i,i=null,r=null,a=0;e;){for(a++,n=e,o=0,t=0;t<c&&(o++,n=n.nextZ,!!n);t++);for(l=c;o>0||l>0&&n;)o!==0&&(l===0||!n||e.z<=n.z)?(s=e,e=e.nextZ,o--):(s=n,n=n.nextZ,l--),r?r.nextZ=s:i=s,s.prevZ=r,r=s;e=n}r.nextZ=null,c*=2}while(a>1);return i}function pl(i,t,e,n,s){return i=(i-e)*s|0,t=(t-n)*s|0,i=(i|i<<8)&16711935,i=(i|i<<4)&252645135,i=(i|i<<2)&858993459,i=(i|i<<1)&1431655765,t=(t|t<<8)&16711935,t=(t|t<<4)&252645135,t=(t|t<<2)&858993459,t=(t|t<<1)&1431655765,i|t<<1}function Xg(i){let t=i,e=i;do(t.x<e.x||t.x===e.x&&t.y<e.y)&&(e=t),t=t.next;while(t!==i);return e}function cs(i,t,e,n,s,r,a,o){return(s-a)*(t-o)>=(i-a)*(r-o)&&(i-a)*(n-o)>=(e-a)*(t-o)&&(e-a)*(r-o)>=(s-a)*(n-o)}function qg(i,t){return i.next.i!==t.i&&i.prev.i!==t.i&&!Yg(i,t)&&(ir(i,t)&&ir(t,i)&&Zg(i,t)&&(ve(i.prev,i,t.prev)||ve(i,t.prev,t))||xa(i,t)&&ve(i.prev,i,i.next)>0&&ve(t.prev,t,t.next)>0)}function ve(i,t,e){return(t.y-i.y)*(e.x-t.x)-(t.x-i.x)*(e.y-t.y)}function xa(i,t){return i.x===t.x&&i.y===t.y}function Wh(i,t,e,n){let s=Hr(ve(i,t,e)),r=Hr(ve(i,t,n)),a=Hr(ve(e,n,i)),o=Hr(ve(e,n,t));return!!(s!==r&&a!==o||s===0&&kr(i,e,t)||r===0&&kr(i,n,t)||a===0&&kr(e,i,n)||o===0&&kr(e,t,n))}function kr(i,t,e){return t.x<=Math.max(i.x,e.x)&&t.x>=Math.min(i.x,e.x)&&t.y<=Math.max(i.y,e.y)&&t.y>=Math.min(i.y,e.y)}function Hr(i){return i>0?1:i<0?-1:0}function Yg(i,t){let e=i;do{if(e.i!==i.i&&e.next.i!==i.i&&e.i!==t.i&&e.next.i!==t.i&&Wh(e,e.next,i,t))return!0;e=e.next}while(e!==i);return!1}function ir(i,t){return ve(i.prev,i,i.next)<0?ve(i,t,i.next)>=0&&ve(i,i.prev,t)>=0:ve(i,t,i.prev)<0||ve(i,i.next,t)<0}function Zg(i,t){let e=i,n=!1,s=(i.x+t.x)/2,r=(i.y+t.y)/2;do e.y>r!=e.next.y>r&&e.next.y!==e.y&&s<(e.next.x-e.x)*(r-e.y)/(e.next.y-e.y)+e.x&&(n=!n),e=e.next;while(e!==i);return n}function Kh(i,t){let e=new ml(i.i,i.x,i.y),n=new ml(t.i,t.x,t.y),s=i.next,r=t.prev;return i.next=t,t.prev=i,e.next=s,s.prev=e,n.next=e,e.prev=n,r.next=n,n.prev=r,n}function ph(i,t,e,n){let s=new ml(i,t,e);return n?(s.next=n.next,s.prev=n,n.next.prev=s,n.next=s):(s.prev=s,s.next=s),s}function sr(i){i.next.prev=i.prev,i.prev.next=i.next,i.prevZ&&(i.prevZ.nextZ=i.nextZ),i.nextZ&&(i.nextZ.prevZ=i.prevZ)}function ml(i,t,e){this.i=i,this.x=t,this.y=e,this.prev=null,this.next=null,this.z=0,this.prevZ=null,this.nextZ=null,this.steiner=!1}function $g(i,t,e,n){let s=0;for(let r=t,a=e-n;r<e;r+=n)s+=(i[a]-i[r])*(i[r+1]+i[a+1]),a=r;return s}var $s=class i{static area(t){let e=t.length,n=0;for(let s=e-1,r=0;r<e;s=r++)n+=t[s].x*t[r].y-t[r].x*t[s].y;return n*.5}static isClockWise(t){return i.area(t)<0}static triangulateShape(t,e){let n=[],s=[],r=[];mh(t),gh(n,t);let a=t.length;e.forEach(mh);for(let l=0;l<e.length;l++)s.push(a),a+=e[l].length,gh(n,e[l]);let o=Lg.triangulate(n,s);for(let l=0;l<o.length;l+=3)r.push(o.slice(l,l+3));return r}};function mh(i){let t=i.length;t>2&&i[t-1].equals(i[0])&&i.pop()}function gh(i,t){for(let e=0;e<t.length;e++)i.push(t[e].x),i.push(t[e].y)}var ws=class i extends Ie{constructor(t=new Bi([new at(.5,.5),new at(-.5,.5),new at(-.5,-.5),new at(.5,-.5)]),e={}){super(),this.type="ExtrudeGeometry",this.parameters={shapes:t,options:e},t=Array.isArray(t)?t:[t];let n=this,s=[],r=[];for(let o=0,l=t.length;o<l;o++){let c=t[o];a(c)}this.setAttribute("position",new jt(s,3)),this.setAttribute("uv",new jt(r,2)),this.computeVertexNormals();function a(o){let l=[],c=e.curveSegments!==void 0?e.curveSegments:12,h=e.steps!==void 0?e.steps:1,u=e.depth!==void 0?e.depth:1,d=e.bevelEnabled!==void 0?e.bevelEnabled:!0,p=e.bevelThickness!==void 0?e.bevelThickness:.2,g=e.bevelSize!==void 0?e.bevelSize:p-.1,x=e.bevelOffset!==void 0?e.bevelOffset:0,m=e.bevelSegments!==void 0?e.bevelSegments:3,f=e.extrudePath,y=e.UVGenerator!==void 0?e.UVGenerator:Jg,_,M=!1,C,A,E,b;f&&(_=f.getSpacedPoints(h),M=!0,d=!1,C=f.computeFrenetFrames(h,!1),A=new L,E=new L,b=new L),d||(m=0,p=0,g=0,x=0);let v=o.extractPoints(c),S=v.shape,w=v.holes;if(!$s.isClockWise(S)){S=S.reverse();for(let j=0,ot=w.length;j<ot;j++){let D=w[j];$s.isClockWise(D)&&(w[j]=D.reverse())}}let F=$s.triangulateShape(S,w),U=S;for(let j=0,ot=w.length;j<ot;j++){let D=w[j];S=S.concat(D)}function H(j,ot,D){return ot||console.error("THREE.ExtrudeGeometry: vec does not exist"),j.clone().addScaledVector(ot,D)}let N=S.length,K=F.length;function O(j,ot,D){let Dt,it,Mt,ht=j.x-ot.x,Ot=j.y-ot.y,yt=D.x-j.x,I=D.y-j.y,T=ht*ht+Ot*Ot,V=ht*I-Ot*yt;if(Math.abs(V)>Number.EPSILON){let Z=Math.sqrt(T),et=Math.sqrt(yt*yt+I*I),$=ot.x-Ot/Z,Rt=ot.y+ht/Z,dt=D.x-I/et,vt=D.y+yt/et,Qt=((dt-$)*I-(vt-Rt)*yt)/(ht*I-Ot*yt);Dt=$+ht*Qt-j.x,it=Rt+Ot*Qt-j.y;let rt=Dt*Dt+it*it;if(rt<=2)return new at(Dt,it);Mt=Math.sqrt(rt/2)}else{let Z=!1;ht>Number.EPSILON?yt>Number.EPSILON&&(Z=!0):ht<-Number.EPSILON?yt<-Number.EPSILON&&(Z=!0):Math.sign(Ot)===Math.sign(I)&&(Z=!0),Z?(Dt=-Ot,it=ht,Mt=Math.sqrt(T)):(Dt=ht,it=Ot,Mt=Math.sqrt(T/2))}return new at(Dt/Mt,it/Mt)}let Y=[];for(let j=0,ot=U.length,D=ot-1,Dt=j+1;j<ot;j++,D++,Dt++)D===ot&&(D=0),Dt===ot&&(Dt=0),Y[j]=O(U[j],U[D],U[Dt]);let tt=[],nt,Tt=Y.concat();for(let j=0,ot=w.length;j<ot;j++){let D=w[j];nt=[];for(let Dt=0,it=D.length,Mt=it-1,ht=Dt+1;Dt<it;Dt++,Mt++,ht++)Mt===it&&(Mt=0),ht===it&&(ht=0),nt[Dt]=O(D[Dt],D[Mt],D[ht]);tt.push(nt),Tt=Tt.concat(nt)}for(let j=0;j<m;j++){let ot=j/m,D=p*Math.cos(ot*Math.PI/2),Dt=g*Math.sin(ot*Math.PI/2)+x;for(let it=0,Mt=U.length;it<Mt;it++){let ht=H(U[it],Y[it],Dt);ct(ht.x,ht.y,-D)}for(let it=0,Mt=w.length;it<Mt;it++){let ht=w[it];nt=tt[it];for(let Ot=0,yt=ht.length;Ot<yt;Ot++){let I=H(ht[Ot],nt[Ot],Dt);ct(I.x,I.y,-D)}}}let Nt=g+x;for(let j=0;j<N;j++){let ot=d?H(S[j],Tt[j],Nt):S[j];M?(E.copy(C.normals[0]).multiplyScalar(ot.x),A.copy(C.binormals[0]).multiplyScalar(ot.y),b.copy(_[0]).add(E).add(A),ct(b.x,b.y,b.z)):ct(ot.x,ot.y,0)}for(let j=1;j<=h;j++)for(let ot=0;ot<N;ot++){let D=d?H(S[ot],Tt[ot],Nt):S[ot];M?(E.copy(C.normals[j]).multiplyScalar(D.x),A.copy(C.binormals[j]).multiplyScalar(D.y),b.copy(_[j]).add(E).add(A),ct(b.x,b.y,b.z)):ct(D.x,D.y,u/h*j)}for(let j=m-1;j>=0;j--){let ot=j/m,D=p*Math.cos(ot*Math.PI/2),Dt=g*Math.sin(ot*Math.PI/2)+x;for(let it=0,Mt=U.length;it<Mt;it++){let ht=H(U[it],Y[it],Dt);ct(ht.x,ht.y,u+D)}for(let it=0,Mt=w.length;it<Mt;it++){let ht=w[it];nt=tt[it];for(let Ot=0,yt=ht.length;Ot<yt;Ot++){let I=H(ht[Ot],nt[Ot],Dt);M?ct(I.x,I.y+_[h-1].y,_[h-1].x+D):ct(I.x,I.y,u+D)}}}X(),st();function X(){let j=s.length/3;if(d){let ot=0,D=N*ot;for(let Dt=0;Dt<K;Dt++){let it=F[Dt];Ut(it[2]+D,it[1]+D,it[0]+D)}ot=h+m*2,D=N*ot;for(let Dt=0;Dt<K;Dt++){let it=F[Dt];Ut(it[0]+D,it[1]+D,it[2]+D)}}else{for(let ot=0;ot<K;ot++){let D=F[ot];Ut(D[2],D[1],D[0])}for(let ot=0;ot<K;ot++){let D=F[ot];Ut(D[0]+N*h,D[1]+N*h,D[2]+N*h)}}n.addGroup(j,s.length/3-j,0)}function st(){let j=s.length/3,ot=0;xt(U,ot),ot+=U.length;for(let D=0,Dt=w.length;D<Dt;D++){let it=w[D];xt(it,ot),ot+=it.length}n.addGroup(j,s.length/3-j,1)}function xt(j,ot){let D=j.length;for(;--D>=0;){let Dt=D,it=D-1;it<0&&(it=j.length-1);for(let Mt=0,ht=h+m*2;Mt<ht;Mt++){let Ot=N*Mt,yt=N*(Mt+1),I=ot+Dt+Ot,T=ot+it+Ot,V=ot+it+yt,Z=ot+Dt+yt;kt(I,T,V,Z)}}}function ct(j,ot,D){l.push(j),l.push(ot),l.push(D)}function Ut(j,ot,D){At(j),At(ot),At(D);let Dt=s.length/3,it=y.generateTopUV(n,s,Dt-3,Dt-2,Dt-1);Jt(it[0]),Jt(it[1]),Jt(it[2])}function kt(j,ot,D,Dt){At(j),At(ot),At(Dt),At(ot),At(D),At(Dt);let it=s.length/3,Mt=y.generateSideWallUV(n,s,it-6,it-3,it-2,it-1);Jt(Mt[0]),Jt(Mt[1]),Jt(Mt[3]),Jt(Mt[1]),Jt(Mt[2]),Jt(Mt[3])}function At(j){s.push(l[j*3+0]),s.push(l[j*3+1]),s.push(l[j*3+2])}function Jt(j){r.push(j.x),r.push(j.y)}}}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}toJSON(){let t=super.toJSON(),e=this.parameters.shapes,n=this.parameters.options;return Qg(e,n,t)}static fromJSON(t,e){let n=[];for(let r=0,a=t.shapes.length;r<a;r++){let o=e[t.shapes[r]];n.push(o)}let s=t.options.extrudePath;return s!==void 0&&(t.options.extrudePath=new da[s.type]().fromJSON(s)),new i(n,t.options)}},Jg={generateTopUV:function(i,t,e,n,s){let r=t[e*3],a=t[e*3+1],o=t[n*3],l=t[n*3+1],c=t[s*3],h=t[s*3+1];return[new at(r,a),new at(o,l),new at(c,h)]},generateSideWallUV:function(i,t,e,n,s,r){let a=t[e*3],o=t[e*3+1],l=t[e*3+2],c=t[n*3],h=t[n*3+1],u=t[n*3+2],d=t[s*3],p=t[s*3+1],g=t[s*3+2],x=t[r*3],m=t[r*3+1],f=t[r*3+2];return Math.abs(o-h)<Math.abs(a-c)?[new at(a,1-l),new at(c,1-u),new at(d,1-g),new at(x,1-f)]:[new at(o,1-l),new at(h,1-u),new at(p,1-g),new at(m,1-f)]}};function Qg(i,t,e){if(e.shapes=[],Array.isArray(i))for(let n=0,s=i.length;n<s;n++){let r=i[n];e.shapes.push(r.uuid)}else e.shapes.push(i.uuid);return e.options=Object.assign({},t),t.extrudePath!==void 0&&(e.options.extrudePath=t.extrudePath.toJSON()),e}var _i=class i extends fl{constructor(t=1,e=0){let n=(1+Math.sqrt(5))/2,s=[-1,n,0,1,n,0,-1,-n,0,1,-n,0,0,-1,n,0,1,n,0,-1,-n,0,1,-n,n,0,-1,n,0,1,-n,0,-1,-n,0,1],r=[0,11,5,0,5,1,0,1,7,0,7,10,0,10,11,1,5,9,5,11,4,11,10,2,10,7,6,7,1,8,3,9,4,3,4,2,3,2,6,3,6,8,3,8,9,4,9,5,2,4,11,6,2,10,8,6,7,9,8,1];super(s,r,t,e),this.type="IcosahedronGeometry",this.parameters={radius:t,detail:e}}static fromJSON(t){return new i(t.radius,t.detail)}};var rr=class i extends Ie{constructor(t=.5,e=1,n=32,s=1,r=0,a=Math.PI*2){super(),this.type="RingGeometry",this.parameters={innerRadius:t,outerRadius:e,thetaSegments:n,phiSegments:s,thetaStart:r,thetaLength:a},n=Math.max(3,n),s=Math.max(1,s);let o=[],l=[],c=[],h=[],u=t,d=(e-t)/s,p=new L,g=new at;for(let x=0;x<=s;x++){for(let m=0;m<=n;m++){let f=r+m/n*a;p.x=u*Math.cos(f),p.y=u*Math.sin(f),l.push(p.x,p.y,p.z),c.push(0,0,1),g.x=(p.x/e+1)/2,g.y=(p.y/e+1)/2,h.push(g.x,g.y)}u+=d}for(let x=0;x<s;x++){let m=x*(n+1);for(let f=0;f<n;f++){let y=f+m,_=y,M=y+n+1,C=y+n+2,A=y+1;o.push(_,M,A),o.push(M,C,A)}}this.setIndex(o),this.setAttribute("position",new jt(l,3)),this.setAttribute("normal",new jt(c,3)),this.setAttribute("uv",new jt(h,2))}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}static fromJSON(t){return new i(t.innerRadius,t.outerRadius,t.thetaSegments,t.phiSegments,t.thetaStart,t.thetaLength)}};var Ts=class i extends Ie{constructor(t=1,e=32,n=16,s=0,r=Math.PI*2,a=0,o=Math.PI){super(),this.type="SphereGeometry",this.parameters={radius:t,widthSegments:e,heightSegments:n,phiStart:s,phiLength:r,thetaStart:a,thetaLength:o},e=Math.max(3,Math.floor(e)),n=Math.max(2,Math.floor(n));let l=Math.min(a+o,Math.PI),c=0,h=[],u=new L,d=new L,p=[],g=[],x=[],m=[];for(let f=0;f<=n;f++){let y=[],_=f/n,M=0;f===0&&a===0?M=.5/e:f===n&&l===Math.PI&&(M=-.5/e);for(let C=0;C<=e;C++){let A=C/e;u.x=-t*Math.cos(s+A*r)*Math.sin(a+_*o),u.y=t*Math.cos(a+_*o),u.z=t*Math.sin(s+A*r)*Math.sin(a+_*o),g.push(u.x,u.y,u.z),d.copy(u).normalize(),x.push(d.x,d.y,d.z),m.push(A+M,1-_),y.push(c++)}h.push(y)}for(let f=0;f<n;f++)for(let y=0;y<e;y++){let _=h[f][y+1],M=h[f][y],C=h[f+1][y],A=h[f+1][y+1];(f!==0||a>0)&&p.push(_,M,A),(f!==n-1||l<Math.PI)&&p.push(M,C,A)}this.setIndex(p),this.setAttribute("position",new jt(g,3)),this.setAttribute("normal",new jt(x,3)),this.setAttribute("uv",new jt(m,2))}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}static fromJSON(t){return new i(t.radius,t.widthSegments,t.heightSegments,t.phiStart,t.phiLength,t.thetaStart,t.thetaLength)}};var xi=class i extends Ie{constructor(t=1,e=.4,n=12,s=48,r=Math.PI*2){super(),this.type="TorusGeometry",this.parameters={radius:t,tube:e,radialSegments:n,tubularSegments:s,arc:r},n=Math.floor(n),s=Math.floor(s);let a=[],o=[],l=[],c=[],h=new L,u=new L,d=new L;for(let p=0;p<=n;p++)for(let g=0;g<=s;g++){let x=g/s*r,m=p/n*Math.PI*2;u.x=(t+e*Math.cos(m))*Math.cos(x),u.y=(t+e*Math.cos(m))*Math.sin(x),u.z=e*Math.sin(m),o.push(u.x,u.y,u.z),h.x=t*Math.cos(x),h.y=t*Math.sin(x),d.subVectors(u,h).normalize(),l.push(d.x,d.y,d.z),c.push(g/s),c.push(p/n)}for(let p=1;p<=n;p++)for(let g=1;g<=s;g++){let x=(s+1)*p+g-1,m=(s+1)*(p-1)+g-1,f=(s+1)*(p-1)+g,y=(s+1)*p+g;a.push(x,m,y),a.push(m,f,y)}this.setIndex(a),this.setAttribute("position",new jt(o,3)),this.setAttribute("normal",new jt(l,3)),this.setAttribute("uv",new jt(c,2))}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}static fromJSON(t){return new i(t.radius,t.tube,t.radialSegments,t.tubularSegments,t.arc)}};var As=class i extends Ie{constructor(t=new ua(new L(-1,-1,0),new L(-1,1,0),new L(1,1,0)),e=64,n=1,s=8,r=!1){super(),this.type="TubeGeometry",this.parameters={path:t,tubularSegments:e,radius:n,radialSegments:s,closed:r};let a=t.computeFrenetFrames(e,r);this.tangents=a.tangents,this.normals=a.normals,this.binormals=a.binormals;let o=new L,l=new L,c=new at,h=new L,u=[],d=[],p=[],g=[];x(),this.setIndex(g),this.setAttribute("position",new jt(u,3)),this.setAttribute("normal",new jt(d,3)),this.setAttribute("uv",new jt(p,2));function x(){for(let _=0;_<e;_++)m(_);m(r===!1?e:0),y(),f()}function m(_){h=t.getPointAt(_/e,h);let M=a.normals[_],C=a.binormals[_];for(let A=0;A<=s;A++){let E=A/s*Math.PI*2,b=Math.sin(E),v=-Math.cos(E);l.x=v*M.x+b*C.x,l.y=v*M.y+b*C.y,l.z=v*M.z+b*C.z,l.normalize(),d.push(l.x,l.y,l.z),o.x=h.x+n*l.x,o.y=h.y+n*l.y,o.z=h.z+n*l.z,u.push(o.x,o.y,o.z)}}function f(){for(let _=1;_<=e;_++)for(let M=1;M<=s;M++){let C=(s+1)*(_-1)+(M-1),A=(s+1)*_+(M-1),E=(s+1)*_+M,b=(s+1)*(_-1)+M;g.push(C,A,b),g.push(A,E,b)}}function y(){for(let _=0;_<=e;_++)for(let M=0;M<=s;M++)c.x=_/e,c.y=M/s,p.push(c.x,c.y)}}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}toJSON(){let t=super.toJSON();return t.path=this.parameters.path.toJSON(),t}static fromJSON(t){return new i(new da[t.path.type]().fromJSON(t.path),t.tubularSegments,t.radius,t.radialSegments,t.closed)}};var Ve=class extends Ui{static get type(){return"MeshStandardMaterial"}constructor(t){super(),this.isMeshStandardMaterial=!0,this.defines={STANDARD:""},this.color=new Gt(16777215),this.roughness=1,this.metalness=0,this.map=null,this.lightMap=null,this.lightMapIntensity=1,this.aoMap=null,this.aoMapIntensity=1,this.emissive=new Gt(0),this.emissiveIntensity=1,this.emissiveMap=null,this.bumpMap=null,this.bumpScale=1,this.normalMap=null,this.normalMapType=Fh,this.normalScale=new at(1,1),this.displacementMap=null,this.displacementScale=1,this.displacementBias=0,this.roughnessMap=null,this.metalnessMap=null,this.alphaMap=null,this.envMap=null,this.envMapRotation=new pn,this.envMapIntensity=1,this.wireframe=!1,this.wireframeLinewidth=1,this.wireframeLinecap="round",this.wireframeLinejoin="round",this.flatShading=!1,this.fog=!0,this.setValues(t)}copy(t){return super.copy(t),this.defines={STANDARD:""},this.color.copy(t.color),this.roughness=t.roughness,this.metalness=t.metalness,this.map=t.map,this.lightMap=t.lightMap,this.lightMapIntensity=t.lightMapIntensity,this.aoMap=t.aoMap,this.aoMapIntensity=t.aoMapIntensity,this.emissive.copy(t.emissive),this.emissiveMap=t.emissiveMap,this.emissiveIntensity=t.emissiveIntensity,this.bumpMap=t.bumpMap,this.bumpScale=t.bumpScale,this.normalMap=t.normalMap,this.normalMapType=t.normalMapType,this.normalScale.copy(t.normalScale),this.displacementMap=t.displacementMap,this.displacementScale=t.displacementScale,this.displacementBias=t.displacementBias,this.roughnessMap=t.roughnessMap,this.metalnessMap=t.metalnessMap,this.alphaMap=t.alphaMap,this.envMap=t.envMap,this.envMapRotation.copy(t.envMapRotation),this.envMapIntensity=t.envMapIntensity,this.wireframe=t.wireframe,this.wireframeLinewidth=t.wireframeLinewidth,this.wireframeLinecap=t.wireframeLinecap,this.wireframeLinejoin=t.wireframeLinejoin,this.flatShading=t.flatShading,this.fog=t.fog,this}},ln=class extends Ve{static get type(){return"MeshPhysicalMaterial"}constructor(t){super(),this.isMeshPhysicalMaterial=!0,this.defines={STANDARD:"",PHYSICAL:""},this.anisotropyRotation=0,this.anisotropyMap=null,this.clearcoatMap=null,this.clearcoatRoughness=0,this.clearcoatRoughnessMap=null,this.clearcoatNormalScale=new at(1,1),this.clearcoatNormalMap=null,this.ior=1.5,Object.defineProperty(this,"reflectivity",{get:function(){return Ce(2.5*(this.ior-1)/(this.ior+1),0,1)},set:function(e){this.ior=(1+.4*e)/(1-.4*e)}}),this.iridescenceMap=null,this.iridescenceIOR=1.3,this.iridescenceThicknessRange=[100,400],this.iridescenceThicknessMap=null,this.sheenColor=new Gt(0),this.sheenColorMap=null,this.sheenRoughness=1,this.sheenRoughnessMap=null,this.transmissionMap=null,this.thickness=0,this.thicknessMap=null,this.attenuationDistance=1/0,this.attenuationColor=new Gt(1,1,1),this.specularIntensity=1,this.specularIntensityMap=null,this.specularColor=new Gt(1,1,1),this.specularColorMap=null,this._anisotropy=0,this._clearcoat=0,this._dispersion=0,this._iridescence=0,this._sheen=0,this._transmission=0,this.setValues(t)}get anisotropy(){return this._anisotropy}set anisotropy(t){this._anisotropy>0!=t>0&&this.version++,this._anisotropy=t}get clearcoat(){return this._clearcoat}set clearcoat(t){this._clearcoat>0!=t>0&&this.version++,this._clearcoat=t}get iridescence(){return this._iridescence}set iridescence(t){this._iridescence>0!=t>0&&this.version++,this._iridescence=t}get dispersion(){return this._dispersion}set dispersion(t){this._dispersion>0!=t>0&&this.version++,this._dispersion=t}get sheen(){return this._sheen}set sheen(t){this._sheen>0!=t>0&&this.version++,this._sheen=t}get transmission(){return this._transmission}set transmission(t){this._transmission>0!=t>0&&this.version++,this._transmission=t}copy(t){return super.copy(t),this.defines={STANDARD:"",PHYSICAL:""},this.anisotropy=t.anisotropy,this.anisotropyRotation=t.anisotropyRotation,this.anisotropyMap=t.anisotropyMap,this.clearcoat=t.clearcoat,this.clearcoatMap=t.clearcoatMap,this.clearcoatRoughness=t.clearcoatRoughness,this.clearcoatRoughnessMap=t.clearcoatRoughnessMap,this.clearcoatNormalMap=t.clearcoatNormalMap,this.clearcoatNormalScale.copy(t.clearcoatNormalScale),this.dispersion=t.dispersion,this.ior=t.ior,this.iridescence=t.iridescence,this.iridescenceMap=t.iridescenceMap,this.iridescenceIOR=t.iridescenceIOR,this.iridescenceThicknessRange=[...t.iridescenceThicknessRange],this.iridescenceThicknessMap=t.iridescenceThicknessMap,this.sheen=t.sheen,this.sheenColor.copy(t.sheenColor),this.sheenColorMap=t.sheenColorMap,this.sheenRoughness=t.sheenRoughness,this.sheenRoughnessMap=t.sheenRoughnessMap,this.transmission=t.transmission,this.transmissionMap=t.transmissionMap,this.thickness=t.thickness,this.thicknessMap=t.thicknessMap,this.attenuationDistance=t.attenuationDistance,this.attenuationColor.copy(t.attenuationColor),this.specularIntensity=t.specularIntensity,this.specularIntensityMap=t.specularIntensityMap,this.specularColor.copy(t.specularColor),this.specularColorMap=t.specularColorMap,this}};function Vr(i,t,e){return!i||!e&&i.constructor===t?i:typeof t.BYTES_PER_ELEMENT=="number"?new t(i):Array.prototype.slice.call(i)}function jg(i){return ArrayBuffer.isView(i)&&!(i instanceof DataView)}var Rs=class{constructor(t,e,n,s){this.parameterPositions=t,this._cachedIndex=0,this.resultBuffer=s!==void 0?s:new e.constructor(n),this.sampleValues=e,this.valueSize=n,this.settings=null,this.DefaultSettings_={}}evaluate(t){let e=this.parameterPositions,n=this._cachedIndex,s=e[n],r=e[n-1];n:{t:{let a;e:{i:if(!(t<s)){for(let o=n+2;;){if(s===void 0){if(t<r)break i;return n=e.length,this._cachedIndex=n,this.copySampleValue_(n-1)}if(n===o)break;if(r=s,s=e[++n],t<s)break t}a=e.length;break e}if(!(t>=r)){let o=e[1];t<o&&(n=2,r=o);for(let l=n-2;;){if(r===void 0)return this._cachedIndex=0,this.copySampleValue_(0);if(n===l)break;if(s=r,r=e[--n-1],t>=r)break t}a=n,n=0;break e}break n}for(;n<a;){let o=n+a>>>1;t<e[o]?a=o:n=o+1}if(s=e[n],r=e[n-1],r===void 0)return this._cachedIndex=0,this.copySampleValue_(0);if(s===void 0)return n=e.length,this._cachedIndex=n,this.copySampleValue_(n-1)}this._cachedIndex=n,this.intervalChanged_(n,r,s)}return this.interpolate_(n,r,t,s)}getSettings_(){return this.settings||this.DefaultSettings_}copySampleValue_(t){let e=this.resultBuffer,n=this.sampleValues,s=this.valueSize,r=t*s;for(let a=0;a!==s;++a)e[a]=n[r+a];return e}interpolate_(){throw new Error("call to abstract method")}intervalChanged_(){}},gl=class extends Rs{constructor(t,e,n,s){super(t,e,n,s),this._weightPrev=-0,this._offsetPrev=-0,this._weightNext=-0,this._offsetNext=-0,this.DefaultSettings_={endingStart:pc,endingEnd:pc}}intervalChanged_(t,e,n){let s=this.parameterPositions,r=t-2,a=t+1,o=s[r],l=s[a];if(o===void 0)switch(this.getSettings_().endingStart){case mc:r=t,o=2*e-n;break;case gc:r=s.length-2,o=e+s[r]-s[r+1];break;default:r=t,o=n}if(l===void 0)switch(this.getSettings_().endingEnd){case mc:a=t,l=2*n-e;break;case gc:a=1,l=n+s[1]-s[0];break;default:a=t-1,l=e}let c=(n-e)*.5,h=this.valueSize;this._weightPrev=c/(e-o),this._weightNext=c/(l-n),this._offsetPrev=r*h,this._offsetNext=a*h}interpolate_(t,e,n,s){let r=this.resultBuffer,a=this.sampleValues,o=this.valueSize,l=t*o,c=l-o,h=this._offsetPrev,u=this._offsetNext,d=this._weightPrev,p=this._weightNext,g=(n-e)/(s-e),x=g*g,m=x*g,f=-d*m+2*d*x-d*g,y=(1+d)*m+(-1.5-2*d)*x+(-.5+d)*g+1,_=(-1-p)*m+(1.5+p)*x+.5*g,M=p*m-p*x;for(let C=0;C!==o;++C)r[C]=f*a[h+C]+y*a[c+C]+_*a[l+C]+M*a[u+C];return r}},_l=class extends Rs{constructor(t,e,n,s){super(t,e,n,s)}interpolate_(t,e,n,s){let r=this.resultBuffer,a=this.sampleValues,o=this.valueSize,l=t*o,c=l-o,h=(n-e)/(s-e),u=1-h;for(let d=0;d!==o;++d)r[d]=a[c+d]*u+a[l+d]*h;return r}},xl=class extends Rs{constructor(t,e,n,s){super(t,e,n,s)}interpolate_(t){return this.copySampleValue_(t-1)}},Tn=class{constructor(t,e,n,s){if(t===void 0)throw new Error("THREE.KeyframeTrack: track name is undefined");if(e===void 0||e.length===0)throw new Error("THREE.KeyframeTrack: no keyframes in track named "+t);this.name=t,this.times=Vr(e,this.TimeBufferType),this.values=Vr(n,this.ValueBufferType),this.setInterpolation(s||this.DefaultInterpolation)}static toJSON(t){let e=t.constructor,n;if(e.toJSON!==this.toJSON)n=e.toJSON(t);else{n={name:t.name,times:Vr(t.times,Array),values:Vr(t.values,Array)};let s=t.getInterpolation();s!==t.DefaultInterpolation&&(n.interpolation=s)}return n.type=t.ValueTypeName,n}InterpolantFactoryMethodDiscrete(t){return new xl(this.times,this.values,this.getValueSize(),t)}InterpolantFactoryMethodLinear(t){return new _l(this.times,this.values,this.getValueSize(),t)}InterpolantFactoryMethodSmooth(t){return new gl(this.times,this.values,this.getValueSize(),t)}setInterpolation(t){let e;switch(t){case Yr:e=this.InterpolantFactoryMethodDiscrete;break;case Wo:e=this.InterpolantFactoryMethodLinear;break;case Aa:e=this.InterpolantFactoryMethodSmooth;break}if(e===void 0){let n="unsupported interpolation for "+this.ValueTypeName+" keyframe track named "+this.name;if(this.createInterpolant===void 0)if(t!==this.DefaultInterpolation)this.setInterpolation(this.DefaultInterpolation);else throw new Error(n);return console.warn("THREE.KeyframeTrack:",n),this}return this.createInterpolant=e,this}getInterpolation(){switch(this.createInterpolant){case this.InterpolantFactoryMethodDiscrete:return Yr;case this.InterpolantFactoryMethodLinear:return Wo;case this.InterpolantFactoryMethodSmooth:return Aa}}getValueSize(){return this.values.length/this.times.length}shift(t){if(t!==0){let e=this.times;for(let n=0,s=e.length;n!==s;++n)e[n]+=t}return this}scale(t){if(t!==1){let e=this.times;for(let n=0,s=e.length;n!==s;++n)e[n]*=t}return this}trim(t,e){let n=this.times,s=n.length,r=0,a=s-1;for(;r!==s&&n[r]<t;)++r;for(;a!==-1&&n[a]>e;)--a;if(++a,r!==0||a!==s){r>=a&&(a=Math.max(a,1),r=a-1);let o=this.getValueSize();this.times=n.slice(r,a),this.values=this.values.slice(r*o,a*o)}return this}validate(){let t=!0,e=this.getValueSize();e-Math.floor(e)!==0&&(console.error("THREE.KeyframeTrack: Invalid value size in track.",this),t=!1);let n=this.times,s=this.values,r=n.length;r===0&&(console.error("THREE.KeyframeTrack: Track is empty.",this),t=!1);let a=null;for(let o=0;o!==r;o++){let l=n[o];if(typeof l=="number"&&isNaN(l)){console.error("THREE.KeyframeTrack: Time is not a valid number.",this,o,l),t=!1;break}if(a!==null&&a>l){console.error("THREE.KeyframeTrack: Out of order keys.",this,o,l,a),t=!1;break}a=l}if(s!==void 0&&jg(s))for(let o=0,l=s.length;o!==l;++o){let c=s[o];if(isNaN(c)){console.error("THREE.KeyframeTrack: Value is not a valid number.",this,o,c),t=!1;break}}return t}optimize(){let t=this.times.slice(),e=this.values.slice(),n=this.getValueSize(),s=this.getInterpolation()===Aa,r=t.length-1,a=1;for(let o=1;o<r;++o){let l=!1,c=t[o],h=t[o+1];if(c!==h&&(o!==1||c!==t[0]))if(s)l=!0;else{let u=o*n,d=u-n,p=u+n;for(let g=0;g!==n;++g){let x=e[u+g];if(x!==e[d+g]||x!==e[p+g]){l=!0;break}}}if(l){if(o!==a){t[a]=t[o];let u=o*n,d=a*n;for(let p=0;p!==n;++p)e[d+p]=e[u+p]}++a}}if(r>0){t[a]=t[r];for(let o=r*n,l=a*n,c=0;c!==n;++c)e[l+c]=e[o+c];++a}return a!==t.length?(this.times=t.slice(0,a),this.values=e.slice(0,a*n)):(this.times=t,this.values=e),this}clone(){let t=this.times.slice(),e=this.values.slice(),n=this.constructor,s=new n(this.name,t,e);return s.createInterpolant=this.createInterpolant,s}};Tn.prototype.TimeBufferType=Float32Array;Tn.prototype.ValueBufferType=Float32Array;Tn.prototype.DefaultInterpolation=Wo;var ki=class extends Tn{constructor(t,e,n){super(t,e,n)}};ki.prototype.ValueTypeName="bool";ki.prototype.ValueBufferType=Array;ki.prototype.DefaultInterpolation=Yr;ki.prototype.InterpolantFactoryMethodLinear=void 0;ki.prototype.InterpolantFactoryMethodSmooth=void 0;var yl=class extends Tn{};yl.prototype.ValueTypeName="color";var vl=class extends Tn{};vl.prototype.ValueTypeName="number";var Ml=class extends Rs{constructor(t,e,n,s){super(t,e,n,s)}interpolate_(t,e,n,s){let r=this.resultBuffer,a=this.sampleValues,o=this.valueSize,l=(n-e)/(s-e),c=t*o;for(let h=c+o;c!==h;c+=4)Dn.slerpFlat(r,0,a,c-o,a,c,l);return r}},fa=class extends Tn{InterpolantFactoryMethodLinear(t){return new Ml(this.times,this.values,this.getValueSize(),t)}};fa.prototype.ValueTypeName="quaternion";fa.prototype.InterpolantFactoryMethodSmooth=void 0;var Hi=class extends Tn{constructor(t,e,n){super(t,e,n)}};Hi.prototype.ValueTypeName="string";Hi.prototype.ValueBufferType=Array;Hi.prototype.DefaultInterpolation=Yr;Hi.prototype.InterpolantFactoryMethodLinear=void 0;Hi.prototype.InterpolantFactoryMethodSmooth=void 0;var Sl=class extends Tn{};Sl.prototype.ValueTypeName="vector";var El=class{constructor(t,e,n){let s=this,r=!1,a=0,o=0,l,c=[];this.onStart=void 0,this.onLoad=t,this.onProgress=e,this.onError=n,this.itemStart=function(h){o++,r===!1&&s.onStart!==void 0&&s.onStart(h,a,o),r=!0},this.itemEnd=function(h){a++,s.onProgress!==void 0&&s.onProgress(h,a,o),a===o&&(r=!1,s.onLoad!==void 0&&s.onLoad())},this.itemError=function(h){s.onError!==void 0&&s.onError(h)},this.resolveURL=function(h){return l?l(h):h},this.setURLModifier=function(h){return l=h,this},this.addHandler=function(h,u){return c.push(h,u),this},this.removeHandler=function(h){let u=c.indexOf(h);return u!==-1&&c.splice(u,2),this},this.getHandler=function(h){for(let u=0,d=c.length;u<d;u+=2){let p=c[u],g=c[u+1];if(p.global&&(p.lastIndex=0),p.test(h))return g}return null}}},t_=new El,bl=class{constructor(t){this.manager=t!==void 0?t:t_,this.crossOrigin="anonymous",this.withCredentials=!1,this.path="",this.resourcePath="",this.requestHeader={}}load(){}loadAsync(t,e){let n=this;return new Promise(function(s,r){n.load(t,s,e,r)})}parse(){}setCrossOrigin(t){return this.crossOrigin=t,this}setWithCredentials(t){return this.withCredentials=t,this}setPath(t){return this.path=t,this}setResourcePath(t){return this.resourcePath=t,this}setRequestHeader(t){return this.requestHeader=t,this}};bl.DEFAULT_MATERIAL_NAME="__DEFAULT";var Cs=class extends He{constructor(t,e=1){super(),this.isLight=!0,this.type="Light",this.color=new Gt(t),this.intensity=e}dispose(){}copy(t,e){return super.copy(t,e),this.color.copy(t.color),this.intensity=t.intensity,this}toJSON(t){let e=super.toJSON(t);return e.object.color=this.color.getHex(),e.object.intensity=this.intensity,this.groundColor!==void 0&&(e.object.groundColor=this.groundColor.getHex()),this.distance!==void 0&&(e.object.distance=this.distance),this.angle!==void 0&&(e.object.angle=this.angle),this.decay!==void 0&&(e.object.decay=this.decay),this.penumbra!==void 0&&(e.object.penumbra=this.penumbra),this.shadow!==void 0&&(e.object.shadow=this.shadow.toJSON()),this.target!==void 0&&(e.object.target=this.target.uuid),e}};var io=new he,_h=new L,xh=new L,ar=class{constructor(t){this.camera=t,this.intensity=1,this.bias=0,this.normalBias=0,this.radius=1,this.blurSamples=8,this.mapSize=new at(512,512),this.map=null,this.mapPass=null,this.matrix=new he,this.autoUpdate=!0,this.needsUpdate=!1,this._frustum=new Qs,this._frameExtents=new at(1,1),this._viewportCount=1,this._viewports=[new ce(0,0,1,1)]}getViewportCount(){return this._viewportCount}getFrustum(){return this._frustum}updateMatrices(t){let e=this.camera,n=this.matrix;_h.setFromMatrixPosition(t.matrixWorld),e.position.copy(_h),xh.setFromMatrixPosition(t.target.matrixWorld),e.lookAt(xh),e.updateMatrixWorld(),io.multiplyMatrices(e.projectionMatrix,e.matrixWorldInverse),this._frustum.setFromProjectionMatrix(io),n.set(.5,0,0,.5,0,.5,0,.5,0,0,.5,.5,0,0,0,1),n.multiply(io)}getViewport(t){return this._viewports[t]}getFrameExtents(){return this._frameExtents}dispose(){this.map&&this.map.dispose(),this.mapPass&&this.mapPass.dispose()}copy(t){return this.camera=t.camera.clone(),this.intensity=t.intensity,this.bias=t.bias,this.radius=t.radius,this.mapSize.copy(t.mapSize),this}clone(){return new this.constructor().copy(this)}toJSON(){let t={};return this.intensity!==1&&(t.intensity=this.intensity),this.bias!==0&&(t.bias=this.bias),this.normalBias!==0&&(t.normalBias=this.normalBias),this.radius!==1&&(t.radius=this.radius),(this.mapSize.x!==512||this.mapSize.y!==512)&&(t.mapSize=this.mapSize.toArray()),t.camera=this.camera.toJSON(!1).object,delete t.camera.matrix,t}},wl=class extends ar{constructor(){super(new Le(50,1,.5,500)),this.isSpotLightShadow=!0,this.focus=1}updateMatrices(t){let e=this.camera,n=xs*2*t.angle*this.focus,s=this.mapSize.width/this.mapSize.height,r=t.distance||e.far;(n!==e.fov||s!==e.aspect||r!==e.far)&&(e.fov=n,e.aspect=s,e.far=r,e.updateProjectionMatrix()),super.updateMatrices(t)}copy(t){return super.copy(t),this.focus=t.focus,this}},pa=class extends Cs{constructor(t,e,n=0,s=Math.PI/3,r=0,a=2){super(t,e),this.isSpotLight=!0,this.type="SpotLight",this.position.copy(He.DEFAULT_UP),this.updateMatrix(),this.target=new He,this.distance=n,this.angle=s,this.penumbra=r,this.decay=a,this.map=null,this.shadow=new wl}get power(){return this.intensity*Math.PI}set power(t){this.intensity=t/Math.PI}dispose(){this.shadow.dispose()}copy(t,e){return super.copy(t,e),this.distance=t.distance,this.angle=t.angle,this.penumbra=t.penumbra,this.decay=t.decay,this.target=t.target.clone(),this.shadow=t.shadow.clone(),this}},yh=new he,Vs=new L,so=new L,Tl=class extends ar{constructor(){super(new Le(90,1,.5,500)),this.isPointLightShadow=!0,this._frameExtents=new at(4,2),this._viewportCount=6,this._viewports=[new ce(2,1,1,1),new ce(0,1,1,1),new ce(3,1,1,1),new ce(1,1,1,1),new ce(3,0,1,1),new ce(1,0,1,1)],this._cubeDirections=[new L(1,0,0),new L(-1,0,0),new L(0,0,1),new L(0,0,-1),new L(0,1,0),new L(0,-1,0)],this._cubeUps=[new L(0,1,0),new L(0,1,0),new L(0,1,0),new L(0,1,0),new L(0,0,1),new L(0,0,-1)]}updateMatrices(t,e=0){let n=this.camera,s=this.matrix,r=t.distance||n.far;r!==n.far&&(n.far=r,n.updateProjectionMatrix()),Vs.setFromMatrixPosition(t.matrixWorld),n.position.copy(Vs),so.copy(n.position),so.add(this._cubeDirections[e]),n.up.copy(this._cubeUps[e]),n.lookAt(so),n.updateMatrixWorld(),s.makeTranslation(-Vs.x,-Vs.y,-Vs.z),yh.multiplyMatrices(n.projectionMatrix,n.matrixWorldInverse),this._frustum.setFromProjectionMatrix(yh)}},je=class extends Cs{constructor(t,e,n=0,s=2){super(t,e),this.isPointLight=!0,this.type="PointLight",this.distance=n,this.decay=s,this.shadow=new Tl}get power(){return this.intensity*4*Math.PI}set power(t){this.intensity=t/(4*Math.PI)}dispose(){this.shadow.dispose()}copy(t,e){return super.copy(t,e),this.distance=t.distance,this.decay=t.decay,this.shadow=t.shadow.clone(),this}},Al=class extends ar{constructor(){super(new vs(-5,5,5,-5,.5,500)),this.isDirectionalLightShadow=!0}},Vi=class extends Cs{constructor(t,e){super(t,e),this.isDirectionalLight=!0,this.type="DirectionalLight",this.position.copy(He.DEFAULT_UP),this.updateMatrix(),this.target=new He,this.shadow=new Al}dispose(){this.shadow.dispose()}copy(t){return super.copy(t),this.target=t.target.clone(),this.shadow=t.shadow.clone(),this}},Jn=class extends Cs{constructor(t,e){super(t,e),this.isAmbientLight=!0,this.type="AmbientLight"}};var Hl="\\[\\]\\.:\\/",e_=new RegExp("["+Hl+"]","g"),Vl="[^"+Hl+"]",n_="[^"+Hl.replace("\\.","")+"]",i_=/((?:WC+[\/:])*)/.source.replace("WC",Vl),s_=/(WCOD+)?/.source.replace("WCOD",n_),r_=/(?:\.(WC+)(?:\[(.+)\])?)?/.source.replace("WC",Vl),a_=/\.(WC+)(?:\[(.+)\])?/.source.replace("WC",Vl),o_=new RegExp("^"+i_+s_+r_+a_+"$"),l_=["material","materials","bones","map"],Rl=class{constructor(t,e,n){let s=n||ye.parseTrackName(e);this._targetGroup=t,this._bindings=t.subscribe_(e,s)}getValue(t,e){this.bind();let n=this._targetGroup.nCachedObjects_,s=this._bindings[n];s!==void 0&&s.getValue(t,e)}setValue(t,e){let n=this._bindings;for(let s=this._targetGroup.nCachedObjects_,r=n.length;s!==r;++s)n[s].setValue(t,e)}bind(){let t=this._bindings;for(let e=this._targetGroup.nCachedObjects_,n=t.length;e!==n;++e)t[e].bind()}unbind(){let t=this._bindings;for(let e=this._targetGroup.nCachedObjects_,n=t.length;e!==n;++e)t[e].unbind()}},ye=class i{constructor(t,e,n){this.path=e,this.parsedPath=n||i.parseTrackName(e),this.node=i.findNode(t,this.parsedPath.nodeName),this.rootNode=t,this.getValue=this._getValue_unbound,this.setValue=this._setValue_unbound}static create(t,e,n){return t&&t.isAnimationObjectGroup?new i.Composite(t,e,n):new i(t,e,n)}static sanitizeNodeName(t){return t.replace(/\s/g,"_").replace(e_,"")}static parseTrackName(t){let e=o_.exec(t);if(e===null)throw new Error("PropertyBinding: Cannot parse trackName: "+t);let n={nodeName:e[2],objectName:e[3],objectIndex:e[4],propertyName:e[5],propertyIndex:e[6]},s=n.nodeName&&n.nodeName.lastIndexOf(".");if(s!==void 0&&s!==-1){let r=n.nodeName.substring(s+1);l_.indexOf(r)!==-1&&(n.nodeName=n.nodeName.substring(0,s),n.objectName=r)}if(n.propertyName===null||n.propertyName.length===0)throw new Error("PropertyBinding: can not parse propertyName from trackName: "+t);return n}static findNode(t,e){if(e===void 0||e===""||e==="."||e===-1||e===t.name||e===t.uuid)return t;if(t.skeleton){let n=t.skeleton.getBoneByName(e);if(n!==void 0)return n}if(t.children){let n=function(r){for(let a=0;a<r.length;a++){let o=r[a];if(o.name===e||o.uuid===e)return o;let l=n(o.children);if(l)return l}return null},s=n(t.children);if(s)return s}return null}_getValue_unavailable(){}_setValue_unavailable(){}_getValue_direct(t,e){t[e]=this.targetObject[this.propertyName]}_getValue_array(t,e){let n=this.resolvedProperty;for(let s=0,r=n.length;s!==r;++s)t[e++]=n[s]}_getValue_arrayElement(t,e){t[e]=this.resolvedProperty[this.propertyIndex]}_getValue_toArray(t,e){this.resolvedProperty.toArray(t,e)}_setValue_direct(t,e){this.targetObject[this.propertyName]=t[e]}_setValue_direct_setNeedsUpdate(t,e){this.targetObject[this.propertyName]=t[e],this.targetObject.needsUpdate=!0}_setValue_direct_setMatrixWorldNeedsUpdate(t,e){this.targetObject[this.propertyName]=t[e],this.targetObject.matrixWorldNeedsUpdate=!0}_setValue_array(t,e){let n=this.resolvedProperty;for(let s=0,r=n.length;s!==r;++s)n[s]=t[e++]}_setValue_array_setNeedsUpdate(t,e){let n=this.resolvedProperty;for(let s=0,r=n.length;s!==r;++s)n[s]=t[e++];this.targetObject.needsUpdate=!0}_setValue_array_setMatrixWorldNeedsUpdate(t,e){let n=this.resolvedProperty;for(let s=0,r=n.length;s!==r;++s)n[s]=t[e++];this.targetObject.matrixWorldNeedsUpdate=!0}_setValue_arrayElement(t,e){this.resolvedProperty[this.propertyIndex]=t[e]}_setValue_arrayElement_setNeedsUpdate(t,e){this.resolvedProperty[this.propertyIndex]=t[e],this.targetObject.needsUpdate=!0}_setValue_arrayElement_setMatrixWorldNeedsUpdate(t,e){this.resolvedProperty[this.propertyIndex]=t[e],this.targetObject.matrixWorldNeedsUpdate=!0}_setValue_fromArray(t,e){this.resolvedProperty.fromArray(t,e)}_setValue_fromArray_setNeedsUpdate(t,e){this.resolvedProperty.fromArray(t,e),this.targetObject.needsUpdate=!0}_setValue_fromArray_setMatrixWorldNeedsUpdate(t,e){this.resolvedProperty.fromArray(t,e),this.targetObject.matrixWorldNeedsUpdate=!0}_getValue_unbound(t,e){this.bind(),this.getValue(t,e)}_setValue_unbound(t,e){this.bind(),this.setValue(t,e)}bind(){let t=this.node,e=this.parsedPath,n=e.objectName,s=e.propertyName,r=e.propertyIndex;if(t||(t=i.findNode(this.rootNode,e.nodeName),this.node=t),this.getValue=this._getValue_unavailable,this.setValue=this._setValue_unavailable,!t){console.warn("THREE.PropertyBinding: No target node found for track: "+this.path+".");return}if(n){let c=e.objectIndex;switch(n){case"materials":if(!t.material){console.error("THREE.PropertyBinding: Can not bind to material as node does not have a material.",this);return}if(!t.material.materials){console.error("THREE.PropertyBinding: Can not bind to material.materials as node.material does not have a materials array.",this);return}t=t.material.materials;break;case"bones":if(!t.skeleton){console.error("THREE.PropertyBinding: Can not bind to bones as node does not have a skeleton.",this);return}t=t.skeleton.bones;for(let h=0;h<t.length;h++)if(t[h].name===c){c=h;break}break;case"map":if("map"in t){t=t.map;break}if(!t.material){console.error("THREE.PropertyBinding: Can not bind to material as node does not have a material.",this);return}if(!t.material.map){console.error("THREE.PropertyBinding: Can not bind to material.map as node.material does not have a map.",this);return}t=t.material.map;break;default:if(t[n]===void 0){console.error("THREE.PropertyBinding: Can not bind to objectName of node undefined.",this);return}t=t[n]}if(c!==void 0){if(t[c]===void 0){console.error("THREE.PropertyBinding: Trying to bind to objectIndex of objectName, but is undefined.",this,t);return}t=t[c]}}let a=t[s];if(a===void 0){let c=e.nodeName;console.error("THREE.PropertyBinding: Trying to update property for track: "+c+"."+s+" but it wasn't found.",t);return}let o=this.Versioning.None;this.targetObject=t,t.needsUpdate!==void 0?o=this.Versioning.NeedsUpdate:t.matrixWorldNeedsUpdate!==void 0&&(o=this.Versioning.MatrixWorldNeedsUpdate);let l=this.BindingType.Direct;if(r!==void 0){if(s==="morphTargetInfluences"){if(!t.geometry){console.error("THREE.PropertyBinding: Can not bind to morphTargetInfluences because node does not have a geometry.",this);return}if(!t.geometry.morphAttributes){console.error("THREE.PropertyBinding: Can not bind to morphTargetInfluences because node does not have a geometry.morphAttributes.",this);return}t.morphTargetDictionary[r]!==void 0&&(r=t.morphTargetDictionary[r])}l=this.BindingType.ArrayElement,this.resolvedProperty=a,this.propertyIndex=r}else a.fromArray!==void 0&&a.toArray!==void 0?(l=this.BindingType.HasFromToArray,this.resolvedProperty=a):Array.isArray(a)?(l=this.BindingType.EntireArray,this.resolvedProperty=a):this.propertyName=s;this.getValue=this.GetterByBindingType[l],this.setValue=this.SetterByBindingTypeAndVersioning[l][o]}unbind(){this.node=null,this.getValue=this._getValue_unbound,this.setValue=this._setValue_unbound}};ye.Composite=Rl;ye.prototype.BindingType={Direct:0,EntireArray:1,ArrayElement:2,HasFromToArray:3};ye.prototype.Versioning={None:0,NeedsUpdate:1,MatrixWorldNeedsUpdate:2};ye.prototype.GetterByBindingType=[ye.prototype._getValue_direct,ye.prototype._getValue_array,ye.prototype._getValue_arrayElement,ye.prototype._getValue_toArray];ye.prototype.SetterByBindingTypeAndVersioning=[[ye.prototype._setValue_direct,ye.prototype._setValue_direct_setNeedsUpdate,ye.prototype._setValue_direct_setMatrixWorldNeedsUpdate],[ye.prototype._setValue_array,ye.prototype._setValue_array_setNeedsUpdate,ye.prototype._setValue_array_setMatrixWorldNeedsUpdate],[ye.prototype._setValue_arrayElement,ye.prototype._setValue_arrayElement_setNeedsUpdate,ye.prototype._setValue_arrayElement_setMatrixWorldNeedsUpdate],[ye.prototype._setValue_fromArray,ye.prototype._setValue_fromArray_setNeedsUpdate,ye.prototype._setValue_fromArray_setMatrixWorldNeedsUpdate]];var C_=new Float32Array(1);typeof __THREE_DEVTOOLS__!="undefined"&&__THREE_DEVTOOLS__.dispatchEvent(new CustomEvent("register",{detail:{revision:Cl}}));typeof window!="undefined"&&(window.__THREE__?console.warn("WARNING: Multiple instances of Three.js being imported."):window.__THREE__=Cl);var hn=(i,t=0,e=1)=>Math.max(t,Math.min(e,i)),St=(i,t,e)=>hn((e-i)/(t-i)),ue=(i,t,e)=>{let n=St(i,t,e);return n*n*(3-2*n)},J=(i,t,e)=>i+(t-i)*e,ee=i=>1-Math.pow(1-hn(i),3),va=i=>Math.pow(hn(i),3),_n=i=>(i=hn(i),i<.5?4*i*i*i:1-Math.pow(-2*i+2,3)/2),tn=i=>(i=hn(i),i>=1?1:1-Math.pow(2,-10*i)),Gl=(i,t=1.3)=>(i=hn(i)-1,1+(t+1)*i*i*i+t*i*i);function Se(i){let t=Math.imul(i|0,2654435761)>>>0||1;return()=>(t^=t<<13,t>>>=0,t^=t>>>17,t^=t<<5,t>>>=0,t/4294967296)}function Xh(i){let t=String(i||"").replace("#",""),e=t.length===3?t.split("").map(n=>n+n).join(""):t.padEnd(6,"0").slice(0,6);return[parseInt(e.slice(0,2),16)/255,parseInt(e.slice(2,4),16)/255,parseInt(e.slice(4,6),16)/255]}function cr(i){let[t,e,n]=Xh(i),s=Math.max(t,e,n),r=Math.min(t,e,n),a=(s+r)/2;if(s===r)return[0,0,a];let o=s-r,l=a>.5?o/(2-s-r):o/(s+r);return[(s===t?(e-n)/o+(e<n?6:0):s===e?(n-t)/o+2:(t-e)/o+4)/6,l,a]}function cn(i,t,e){let n=r=>{let a=(r+i*12)%12,o=t*Math.min(e,1-e);return e-o*Math.max(-1,Math.min(a-3,9-a,1))},s=r=>Math.round(hn(r)*255).toString(16).padStart(2,"0");return"#"+s(n(0))+s(n(8))+s(n(4))}var wt=i=>new Gt(i);function Ye(i,t){let[e,n,s]=Xh(i);return`rgba(${Math.round(e*255)},${Math.round(n*255)},${Math.round(s*255)},${t})`}function Wl(i){let t=i.base||i.color||"#3a4660",e=i.accent||i.color2||"#ffffff",[n,s,r]=cr(t),[a,o,l]=cr(e),c=s<.2||r<.1||r>.9,h=o>.3&&l>.2&&l<.86,u,d,p;c?h?[u,d,p]=[a,Math.max(.72,o),hn(l,.5,.62)]:[u,d,p]=[.6,.25,.8]:[u,d,p]=[n,Math.max(.78,s),hn(r,.48,.58)];let g=cn(u,d,p),x=c?cn(u,h?.22:.08,.12):cn(n,Math.min(.85,s),.2),m=c?cn(u,h?.3:.1,.26):cn(n,Math.min(.9,s),.36),f=c?cn(u,h?.3:.12,.04):cn(n,Math.min(.9,s),.055),y=e,_=Math.min(Math.abs(a-u),1-Math.abs(a-u))<.05;return(l>.9||o<.2||_&&!c)&&(y=c?g:cn(u,.95,.74)),cr(y)[2]<.35&&(y=cn(cr(y)[0],cr(y)[1],.6)),{glow:g,panel:x,mid:m,deep:f,ink:y,neutral:c}}function Wt(i,t){let e=document.createElement("canvas");return e.width=Math.max(2,Math.ceil(i)),e.height=Math.max(2,Math.ceil(t)),e}function jn(i,t=!0){let e=new Ni(i);return e.colorSpace=Re,e.generateMipmaps=t,e.minFilter=t?Xn:fn,e.anisotropy=4,e.needsUpdate=!0,e}function Qn(i,t){let e=Wt(i.width,i.height),n=e.getContext("2d");return n.drawImage(i,0,0),n.globalCompositeOperation="source-in",n.fillStyle=t,n.fillRect(0,0,e.width,e.height),e}function ya(i,t,e,n,s){let r=Math.ceil(n)+2,a=Wt(t+2*r,e+2*r),o=a.getContext("2d"),l=Math.max(12,Math.round(n*1.6));for(let c=0;c<2;c++){let h=c?n:n*.5;for(let u=0;u<l;u++){let d=u/l*Math.PI*2;o.drawImage(i,r+Math.cos(d)*h,r+Math.sin(d)*h,t,e)}}return o.drawImage(i,r,r,t,e),o.globalCompositeOperation="source-in",o.fillStyle=s,o.fillRect(0,0,a.width,a.height),a}function yi(i,t){let e=t/Math.max(i.width||i.naturalWidth||1,i.height||i.naturalHeight||1),n=Math.max(2,Math.round((i.width||i.naturalWidth)*e)),s=Math.max(2,Math.round((i.height||i.naturalHeight)*e)),r=Wt(n,s);return r.getContext("2d").drawImage(i,0,0,n,s),r}function oe(i,t={}){i=String(i||"");let e=t.size||100,n=t.family||"BarlowC",r=`${t.weight||800} ${e}px ${n}, 'Barlow Condensed', sans-serif`,a=Wt(4,4).getContext("2d");a.font=r;let o=[...i],l=t.spacing||0,c=t.sy||1,h=o.map(O=>a.measureText(O).width),u=h.reduce((O,Y)=>O+Y,0)+l*Math.max(0,o.length-1),d=a.measureText(i||"H"),p=a.measureText("H"),g=Math.max(d.actualBoundingBoxAscent||0,p.actualBoundingBoxAscent||e*.7),x=Math.max(0,d.actualBoundingBoxDescent||0),m=Math.tan(t.skew||0),f=t.extrude||null,y=f?Math.abs(f.dx)*f.n:0,_=f?Math.abs(f.dy)*f.n:0,M=t.glow?t.glow.blur*1.6:0,C=(t.pad==null?8:t.pad)+M+(t.stroke?t.stroke.w:0),A=(g+x)*c,E=u+Math.abs(m)*A+y+C*2,b=A+_+C*2,v=Wt(E,b),S=v.getContext("2d"),w=C+(m>0?m*x*c:-m*g*c),P=C+g*c,F=[],U=0;for(let O=0;O<o.length;O++)F.push(U),U+=h[O]+l;let H=(O,Y,tt)=>{S.save(),S.setTransform(1,0,-m*c,c,w+O,P+Y),S.font=r,S.textBaseline="alphabetic";for(let nt=0;nt<o.length;nt++)tt(o[nt],F[nt]);S.restore()},N=(O,Y,tt)=>{if(typeof O=="string")return O;let nt=S.createLinearGradient(0,Y,0,tt);for(let[Tt,Nt]of O)nt.addColorStop(Tt,Nt);return nt};if(f)for(let O=f.n;O>=1;O--)S.fillStyle=N(f.fill||[[0,f.top||"#3a4252"],[1,f.bottom||"#0d1118"]],-g,x),H(f.dx*O,f.dy*O,(Y,tt)=>S.fillText(Y,tt,0));t.glow&&(S.save(),S.shadowColor=t.glow.c,S.shadowBlur=t.glow.blur,S.globalAlpha=t.glow.a==null?1:t.glow.a,S.fillStyle=typeof t.fill=="string"?t.fill:"#ffffff",H(0,0,(O,Y)=>S.fillText(O,Y,0)),S.restore()),t.stroke&&(S.lineJoin="round",S.lineWidth=t.stroke.w*2,S.strokeStyle=t.stroke.c,H(0,0,(O,Y)=>S.strokeText(O,Y,0))),t.fill!==null&&(S.fillStyle=N(t.fill||"#ffffff",-g,x),H(0,0,(O,Y)=>S.fillText(O,Y,0))),t.outline&&(S.lineWidth=t.outline.w,S.strokeStyle=t.outline.c,H(0,0,(O,Y)=>S.strokeText(O,Y,0)));let K=o.map((O,Y)=>({ch:O,x0:w+F[Y]-(m>0?0:m*g*c)-2,x1:w+F[Y]+h[Y]+Math.abs(m)*A+y+2}));return{canvas:v,w:v.width,h:v.height,letters:K,ascent:g*c,pad:C}}var hr=[[0,"#ffffff"],[.38,"#eef2f8"],[.5,"#a5afbf"],[.53,"#5f6878"],[.6,"#dfe6ef"],[1,"#aeb8c6"]],Wi=[[0,"#ffffff"],[.62,"#f1f3f7"],[1,"#b9bfca"]];function Kl(i,t,e){let n=yi(i,t),s=n.width,r=n.height,a=Math.max(5,Math.round(t*.03)),o=a+46,l=Wt(s+o*2,r+o*2),c=l.getContext("2d"),h=ya(n,s,r,a,"#ffffff"),u=Math.ceil(a)+2;e&&(c.save(),c.filter="blur(26px)",c.globalAlpha=.95,c.drawImage(Qn(h,e),o-u,o-u),c.restore());let d=Wt(h.width,h.height),p=d.getContext("2d"),g=p.createLinearGradient(0,0,h.width*.5,h.height);g.addColorStop(0,"#ffffff"),g.addColorStop(.3,"#d9dfe9"),g.addColorStop(.48,"#8a95a6"),g.addColorStop(.56,"#f4f7fb"),g.addColorStop(.8,"#b3bdcb"),g.addColorStop(1,"#6d7787"),p.fillStyle=g,p.fillRect(0,0,h.width,h.height),p.globalCompositeOperation="destination-in",p.drawImage(h,0,0);let x=Qn(h,"rgba(255,255,255,0.9)"),m=Qn(h,"rgba(20,26,38,0.9)");c.drawImage(m,o-u+2,o-u+3),c.drawImage(d,o-u,o-u),c.save(),c.globalCompositeOperation="source-atop",c.globalAlpha=.5,c.drawImage(x,o-u-1.5,o-u-2),c.restore();let f=Math.max(2,a*.28),y=ya(n,s,r,f,"rgba(8,11,18,0.9)"),_=Math.ceil(f)+2;c.drawImage(y,o-_,o-_),c.drawImage(n,o,o);let M=Wt(s,r),C=M.getContext("2d");C.drawImage(n,0,0),C.globalCompositeOperation="source-in";let A=C.createLinearGradient(0,0,s*.25,r);A.addColorStop(0,"rgba(255,255,255,0.32)"),A.addColorStop(.42,"rgba(255,255,255,0.08)"),A.addColorStop(.5,"rgba(255,255,255,0)"),A.addColorStop(1,"rgba(0,0,0,0.18)"),C.fillStyle=A,C.fillRect(0,0,s,r),c.drawImage(M,o,o);let E=Wt(l.width,l.height),b=E.getContext("2d"),v=b.createLinearGradient(0,0,0,E.height);return v.addColorStop(0,"#4b5568"),v.addColorStop(1,"#121722"),b.drawImage(h,o-u,o-u),b.globalCompositeOperation="source-in",b.fillStyle=v,b.fillRect(0,0,E.width,E.height),{canvas:l,side:E,w:l.width,h:l.height}}function qh(i,t,e){let n=yi(i,t),s=n.width,r=n.height,a=Math.ceil(t*.08),o=Wt(s+2*a,r+2*a),l=o.getContext("2d");l.filter=`blur(${Math.max(3,t*.022).toFixed(1)}px)`,l.drawImage(Qn(n,"#ffffff"),a,a),l.filter="none";let c=l.getImageData(0,0,o.width,o.height),h=c.data;for(let E=3;E<h.length;E+=4)h[E]=h[E]>110?255:0;l.putImageData(c,0,0);let u=o.width,d=o.height,p=Math.max(6,t*.038),g=Math.max(3,t*.022),x=40,m=(E,b)=>{let v=ya(o,u,d,E,"#fff"),S=ya(o,u,d,b,"#fff"),w=Math.ceil(E)+2,P=Math.ceil(b)+2,F=v.getContext("2d");return F.globalCompositeOperation="destination-out",F.drawImage(S,w-P,w-P),{c:v,m:w}},f=m(p,g),y=m(p-(p-g)*.38,g+(p-g)*.38),_=Wt(u+x*2,d+x*2),M=_.getContext("2d"),C=E=>[x-E.m,x-E.m],A=Qn(f.c,e);return M.save(),M.filter="blur(10px)",M.globalAlpha=.8,M.drawImage(A,...C(f)),M.filter="blur(3px)",M.globalAlpha=1,M.drawImage(A,...C(f)),M.restore(),M.drawImage(A,...C(f)),M.drawImage(Qn(y.c,"rgba(255,255,255,0.92)"),...C(y)),{canvas:_,w:_.width,h:_.height}}function Yh(i,t,e=620){let s=Wt(e+120,e+120),r=s.getContext("2d"),a=s.width/2,o=s.height/2,l=e/2;r.save(),r.shadowColor=t,r.shadowBlur=50,r.beginPath(),r.arc(a,o,l,0,Math.PI*2),r.fillStyle=t,r.fill(),r.restore();let c=r.createRadialGradient(a,o-l*.3,l*.1,a,o,l);c.addColorStop(0,Ye(t,1)),c.addColorStop(1,Ye("#06040f",1)),r.save(),r.beginPath(),r.arc(a,o,l,0,Math.PI*2),r.clip(),r.fillStyle=c,r.fillRect(0,0,s.width,s.height);let h=i.width||i.naturalWidth,u=i.height||i.naturalHeight,d=e*1.02/Math.max(h,u);return r.drawImage(i,a-h*d/2,o-u*d/2+e*.06,h*d,u*d),r.restore(),r.lineWidth=e*.022,r.strokeStyle="rgba(255,255,255,0.92)",r.beginPath(),r.arc(a,o,l-r.lineWidth/2,0,Math.PI*2),r.stroke(),r.lineWidth=e*.01,r.strokeStyle=t,r.beginPath(),r.arc(a,o,l+e*.03,0,Math.PI*2),r.stroke(),{canvas:s,w:s.width,h:s.height,face:!0}}function Zh(i,t,e=.86){let n=i.width||i.naturalWidth,s=i.height||i.naturalHeight,r=40,a=Wt(n,s);a.getContext("2d").drawImage(i,0,0,n,s);let o=Wt(n+r*2,s+r),l=o.getContext("2d");l.save(),l.filter="blur(30px)",l.globalAlpha=.65,l.drawImage(Qn(a,t),r,r),l.restore(),l.save(),l.filter="contrast(1.08) saturate(1.06)",l.drawImage(a,r,r),l.restore();let c=(u,d,p)=>{let g=Wt(n,s),x=g.getContext("2d");x.drawImage(a,0,0),x.globalCompositeOperation="destination-out",x.drawImage(a,u,Math.abs(u)*.4),x.globalCompositeOperation="source-in",x.fillStyle=d,x.fillRect(0,0,n,s),l.save(),l.globalAlpha=p,l.filter="blur(2px)",l.globalCompositeOperation="lighter",l.drawImage(g,r,r),l.restore()};c(9,t,.95),c(-7,"#ffffff",.55),l.save(),l.globalCompositeOperation="destination-out";let h=l.createLinearGradient(0,r+s*e,0,r+s);return h.addColorStop(0,"rgba(0,0,0,0)"),h.addColorStop(1,"rgba(0,0,0,1)"),l.fillStyle=h,l.fillRect(0,r+s*e,o.width,s*(1-e)+2),l.restore(),{canvas:o,w:o.width,h:o.height,top:r,side:r}}function Xl(i=256,t=0){let e=Wt(i,i),n=e.getContext("2d"),s=n.createRadialGradient(i/2,i/2,0,i/2,i/2,i/2);return s.addColorStop(0,"rgba(255,255,255,1)"),s.addColorStop(Math.max(.05,t),"rgba(255,255,255,0.75)"),s.addColorStop(.4,"rgba(255,255,255,0.18)"),s.addColorStop(1,"rgba(255,255,255,0)"),n.fillStyle=s,n.fillRect(0,0,i,i),e}function ti(i=1024,t=128){let e=Wt(i,t),n=e.getContext("2d");for(let[r,a]of[[.25,t*.45],[.5,t*.16],[1,t*.05]]){let o=n.createLinearGradient(0,t/2-a,0,t/2+a);o.addColorStop(0,"rgba(255,255,255,0)"),o.addColorStop(.5,`rgba(255,255,255,${r})`),o.addColorStop(1,"rgba(255,255,255,0)"),n.fillStyle=o,n.fillRect(0,t/2-a,i,a*2)}n.globalCompositeOperation="destination-in";let s=n.createLinearGradient(0,0,i,0);return s.addColorStop(0,"rgba(0,0,0,0)"),s.addColorStop(.35,"rgba(0,0,0,1)"),s.addColorStop(.65,"rgba(0,0,0,1)"),s.addColorStop(1,"rgba(0,0,0,0)"),n.fillStyle=s,n.fillRect(0,0,i,t),e}function $h(i=64){let t=Wt(4,i),e=t.getContext("2d"),n=e.createLinearGradient(0,0,0,i);return n.addColorStop(0,"rgba(255,255,255,0)"),n.addColorStop(.36,"rgba(255,255,255,0.22)"),n.addColorStop(.47,"rgba(255,255,255,0.9)"),n.addColorStop(.5,"rgba(255,255,255,1)"),n.addColorStop(.53,"rgba(255,255,255,0.9)"),n.addColorStop(.64,"rgba(255,255,255,0.22)"),n.addColorStop(1,"rgba(255,255,255,0)"),e.fillStyle=n,e.fillRect(0,0,4,i),t}function Jh(i,t,e=0){let n=Se(t),s=256,r=1024,a=Wt(s,r),o=a.getContext("2d"),l=Wt(s,r),c=l.getContext("2d"),h=o.createLinearGradient(0,0,s*.6,r);h.addColorStop(0,i.mid),h.addColorStop(.5,i.panel),h.addColorStop(1,i.deep),o.fillStyle=h,o.fillRect(0,0,s,r);let u=o.createLinearGradient(0,0,s,0);u.addColorStop(0,"rgba(255,255,255,0.10)"),u.addColorStop(.3,"rgba(255,255,255,0)"),u.addColorStop(1,"rgba(0,0,0,0.25)"),o.fillStyle=u,o.fillRect(0,0,s,r),c.fillStyle="#000",c.fillRect(0,0,s,r);let d=(p,g,x,m,f,y)=>{c.globalAlpha=f,c.fillStyle=y?"#ffffff":i.glow,c.fillRect(p,g,x,m),o.globalAlpha=f*.7,o.fillStyle=y?"#ffffff":i.glow,o.fillRect(p,g,x,m),o.globalAlpha=1,c.globalAlpha=1};if(e===0){d(0,0,7,r,1),d(s-5,0,5,r,.55);for(let p=0;p<4;p++){let g=80+n()*(r-160);d(10,g,s-20,4,.5+n()*.5)}d(s*.5-1,0,2,r,.18,!0)}else if(e===1){d(0,0,4,r,.9),d(s-4,0,4,r,.9);let p=40+n()*(s-120);d(p,0,10+n()*18,r,.85);for(let g=0;g<3;g++)d(0,n()*r,s,3,.4,!0)}else{for(let p=0;p<r;p+=64)d(10,p+20,s-20,2,.35+n()*.4);d(0,0,5,r,.9)}return{map:a,em:l}}function Qh(i,t,e){let n=Se(e),s=1024,r=1024,a=Wt(s,r),o=a.getContext("2d"),l=o.createLinearGradient(0,0,0,r);l.addColorStop(0,i),l.addColorStop(.6,t),l.addColorStop(1,i),o.fillStyle=l,o.fillRect(0,0,s,r);for(let c=0;c<40;c++){let h=n()*s,u=n()*r*.8,d=60+n()*220,p=o.createRadialGradient(h,u,0,h,u,d);p.addColorStop(0,"rgba(255,255,255,0.05)"),p.addColorStop(1,"rgba(255,255,255,0)"),o.fillStyle=p,o.fillRect(h-d,u-d,d*2,d*2)}for(let c=0;c<260;c++){o.globalAlpha=.2+n()*.8,o.fillStyle="#ffffff";let h=n()<.08?2.2:1.1;o.fillRect(n()*s,n()*r*.85,h,h)}return o.globalAlpha=1,a}function jh(i,t,e){let r=Wt(1024,1024),a=r.getContext("2d"),o=a.createRadialGradient(1024/2,1024*.45,0,1024/2,1024/2,1024*.75);o.addColorStop(0,e?Ye(e,.55):t),o.addColorStop(.45,i),o.addColorStop(1,"#000000"),a.fillStyle=o,a.fillRect(0,0,1024,1024),a.strokeStyle=t,a.lineWidth=1.2,a.globalAlpha=.55;for(let l=-1024;l<2048;l+=34)a.beginPath(),a.moveTo(l,0),a.lineTo(l+1024,1024),a.stroke(),a.beginPath(),a.moveTo(l,1024),a.lineTo(l+1024,0),a.stroke();return a.globalAlpha=1,r}function tu(i,t,e=1400,n=260){let s=Se(t),r=Wt(e,n),a=r.getContext("2d"),o=0;for(;o<e;){let c=30+s()*90,h=n*(.25+s()*.75),u=a.createLinearGradient(0,n-h,0,n);u.addColorStop(0,i.mid),u.addColorStop(1,i.panel),a.fillStyle=u,a.fillRect(o,n-h,c-4,h),a.fillStyle=Ye(i.glow,.7);for(let d=n-h+8;d<n-6;d+=10)for(let p=o+5;p<o+c-10;p+=9)s()<.35&&a.fillRect(p,d,4,3);o+=c}a.globalCompositeOperation="destination-in";let l=a.createLinearGradient(0,0,e,0);return l.addColorStop(0,"rgba(0,0,0,0)"),l.addColorStop(.15,"rgba(0,0,0,1)"),l.addColorStop(.85,"rgba(0,0,0,1)"),l.addColorStop(1,"rgba(0,0,0,0)"),a.fillStyle=l,a.fillRect(0,0,e,n),r}function eu(i=256){let t=Wt(i,i),e=t.getContext("2d"),n=i*.46,s=i/2,r=i/2;e.save(),e.beginPath(),e.arc(s,r,n,0,Math.PI*2),e.clip(),e.fillStyle="#f4f6fa",e.fillRect(0,0,i,i);let a=(l,c,h,u)=>{e.beginPath();for(let d=0;d<5;d++){let p=u+d/5*Math.PI*2;e[d?"lineTo":"moveTo"](l+Math.cos(p)*h,c+Math.sin(p)*h)}e.closePath(),e.fill()};e.fillStyle="#1b2230",a(s,r,n*.3,-Math.PI/2);for(let l=0;l<5;l++){let c=-Math.PI/2+l/5*Math.PI*2+Math.PI/5;a(s+Math.cos(c)*n*.86,r+Math.sin(c)*n*.86,n*.3,c+Math.PI)}let o=e.createRadialGradient(s-n*.35,r-n*.4,n*.1,s,r,n*1.05);return o.addColorStop(0,"rgba(255,255,255,0.35)"),o.addColorStop(.6,"rgba(0,0,0,0)"),o.addColorStop(1,"rgba(0,0,10,0.55)"),e.fillStyle=o,e.fillRect(0,0,i,i),e.restore(),t}function nu(i,t){let e=Wt(i,i),n=e.getContext("2d"),s=i/100,r=o=>{o.beginPath(),o.moveTo(50*s,14*s),o.lineTo(86*s,44*s),o.lineTo(86*s,86*s),o.lineTo(14*s,86*s),o.lineTo(14*s,44*s),o.closePath()};n.save(),n.shadowColor=t,n.shadowBlur=18*s,r(n),n.fillStyle="#c7d2e3",n.fill(),n.restore();let a=n.createLinearGradient(0,14*s,0,86*s);for(let[o,l]of hr)a.addColorStop(o,l);return r(n),n.fillStyle=a,n.fill(),n.lineWidth=2.2*s,n.strokeStyle="rgba(30,36,50,0.9)",r(n),n.stroke(),n.beginPath(),n.moveTo(41*s,50*s),n.lineTo(41*s,76*s),n.lineTo(63*s,63*s),n.closePath(),n.fillStyle=t,n.save(),n.shadowColor=t,n.shadowBlur=10*s,n.fill(),n.restore(),n.fillStyle="rgba(255,255,255,0.65)",n.fill(),e}var en=1920,Rn=1080,ru=30,Ge=Rn/2/Math.tan(Lh.degToRad(ru/2)),Sa=new Je(1,1),Ea=new on(1,1,1);function we(){return new Le(ru,en/Rn,20,8e4)}function Te(i,t,e,n,s,r,a,o=0){i.position.set(t,e,n),i.up.set(Math.sin(o),Math.cos(o),0),i.lookAt(s,r,a)}var Yl=new WeakMap;function Ql(i){return Yl.has(i)||Yl.set(i,jn(i)),Yl.get(i)}function _t(i,t={}){let e=i.canvas||i,n=new be({map:t.texture||Ql(e),transparent:!0,depthWrite:!1,toneMapped:!1,fog:!1,blending:t.add?pi:Fn,color:t.color?wt(t.color):16777215,opacity:t.opacity==null?1:t.opacity}),s=new Ct(Sa,n),r=t.scale||1;return s.scale.set(e.width*r,e.height*r,1),s.userData.w=e.width*r,s.userData.h=e.height*r,s}function Ma(i,t={}){let e=i.canvas||i,n=new mn({uniforms:{map:{value:t.texture||Ql(e)},sweep:{value:-1},gain:{value:t.gain||.85},opacity:{value:1}},vertexShader:"varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",fragmentShader:`uniform sampler2D map; uniform float sweep, gain, opacity; varying vec2 vUv;
      void main() {
        vec4 c = texture2D(map, vUv);
        float d = vUv.x * 0.78 + (1.0 - vUv.y) * 0.32 - sweep;
        float h = exp(-d * d / 0.003) * gain + exp(-d * d / 0.04) * gain * 0.12;
        gl_FragColor = vec4(c.rgb + h * c.a, c.a * opacity);
        #include <colorspace_fragment>
      }`,transparent:!0,depthWrite:!1}),s=new Ct(Sa,n),r=t.scale||1;return s.scale.set(e.width*r,e.height*r,1),s.userData.w=e.width*r,s.userData.h=e.height*r,s.userData.sheen=n.uniforms,s}var Ht=(i,t)=>{i.traverse(e=>{e.material&&(e.material.uniforms&&e.material.uniforms.opacity?e.material.uniforms.opacity.value=t*(e.userData.a==null?1:e.userData.a):e.material.opacity=t*(e.userData.a==null?1:e.userData.a),e.visible=t>.002)})};function Ln(i,t=10,e=3){let n=new zt,s=Ql(i.side);for(let a=t;a>=1;a--){let o=_t(i.side,{texture:s});o.position.z=-a*e,n.add(o)}let r=Ma(i.canvas,{gain:.5});return n.add(r),n.userData.face=r,n.userData.w=r.userData.w,n.userData.h=r.userData.h,n}function jl(i,t){let e=new zt,n=Wt(4,4).getContext("2d"),s=t.size||100;n.font=`${t.weight||800} ${s}px ${t.family||"BarlowC"}, 'Barlow Condensed', sans-serif`;let r=0;e.userData.letters=[];for(let a of[...String(i)]){let o=n.measureText(a).width;if(a.trim()){let l=oe(a,t),c=Math.tan(t.skew||0),h=t.sy||1,u=n.measureText(a),d=Math.max(0,u.actualBoundingBoxDescent||0),p=l.pad+(c>0?c*d*h:0),g=l.pad+l.ascent,x=_t(l);x.position.set(r-p+l.w/2,g-l.h/2,0),e.add(x),e.userData.letters.push(x)}r+=o+(t.spacing||0)}return e.userData.w=r-(t.spacing||0),e}function ei(i,t,e,n,s,r=46,a=!0){let o=new zt,l=Math.hypot(e-i,n-t);o.position.set(i,t,0),o.rotation.z=Math.atan2(n-t,e-i);let c=new zt;o.add(c);let h=iu||(iu=jn($h(64),!1)),u=(d,p,g)=>{let x=new Ct(Sa,new be({map:h,color:wt(p),transparent:!0,depthWrite:!1,blending:pi,toneMapped:!1,fog:!1,opacity:g}));x.scale.set(l,d,1),x.position.x=l/2,x.userData.a=g,c.add(x)};return u(r,s,1),a&&u(r*.22,"#ffffff",.9),o.userData.grow=d=>{c.scale.x=Math.max(1e-4,d)},o.userData.grow(1),o}var iu=null;function vi(i,t,e,n){let s=new zt,r=[0,i],a=[i,0],o=[0,-i],l=[-i,0],c=[ei(...r,...l,t,n),ei(...o,...l,t,n),ei(...r,...a,e,n),ei(...o,...a,e,n)];for(let h of c)s.add(h);return s.userData.grow=h=>c.forEach(u=>u.userData.grow(h)),s}function Ue(i,t,e=1){let n=_t(su||(su=Xl(256)),{add:!0,color:i,opacity:e});return n.scale.set(t,t,1),n.userData.a=e,n}var su=null,Zl=new Map;function An(i,t,e,n,s=1.4){let r=[i.glow,i.panel,e,t%2,s,n?n.uuid:""].join("|");if(Zl.has(r))return Zl.get(r);let a=Jh(i,t%2,e),o=new Ve({color:16777215,map:jn(a.map),emissive:16777215,emissiveMap:jn(a.em),emissiveIntensity:s,metalness:.65,roughness:.3,envMap:n,envMapIntensity:1.1});return Zl.set(r,o),o}var $l=new Map;function ur(i,t,e){let n=i+"|"+t+"|"+e%2;return $l.has(n)||$l.set(n,Qh(i,t,e%2)),$l.get(n)}function xn(i,t){let e=new oa(Ea,t,i.length),n=new he,s=new Dn,r=new pn,a=new L,o=new L;return i.forEach((l,c)=>{a.set(l.x,l.y,l.z),o.set(l.w,l.h,l.d),r.set(l.rx||0,l.ry||0,l.rz||0),s.setFromEuler(r),n.compose(a,s,o),e.setMatrixAt(c,n)}),e.instanceMatrix.needsUpdate=!0,e}function Mi(i,t){i.add(new Jn(16777215,.35));let e=new Vi(16777215,1.4);e.position.set(-1,2,3),i.add(e);let n=new je(wt(t.glow),3.5,0,0);n.position.set(0,400,1200),i.add(n)}var xe={};xe.comet=(i,t)=>{let e={glow:"#38d4ff",panel:"#0a1630",mid:"#17305c",deep:"#02050d",ink:"#bfefff"},n=new me,s=we();n.background=wt("#02040b"),n.fog=new Qe(wt("#030812"),6e3,3e4),Mi(n,e);let r=_t(ur("#01030a","#0b2452",7));r.scale.set(52e3,3e4,1),r.position.set(0,4e3,-26e3),n.add(r);let a=new Ct(Ea,An(e,3,0,i,1.6));a.scale.set(1100,16e3,1100),a.position.set(900,3e3,-3200),a.rotation.y=-.42,n.add(a);let o=new zt;o.position.copy(a.position),o.rotation.y=a.rotation.y,n.add(o);let[l,c]=String(t.ko||"20:00").split(":");[[l,3600],[c,900]].forEach(([w,P])=>{let F=oe(w,{size:1e3,sy:1.12,fill:hr,extrude:{n:22,dx:1.4,dy:2.2},stroke:{w:4,c:"#10141c"}}),U=Ma(F);U.position.set(0,P-3e3,560),o.add(U),U.userData.sheen.sweep.value=.4});let h=Se(11),u=[];for(let w=0;w<40;w++){let P=(h()-.5)*3e4,F=-9e3-h()*14e3,U=3e3+h()*12e3;u.push({x:P,y:-4e3+U/2,z:F,w:900+h()*1600,h:U,d:900+h()*1600,ry:h()})}n.add(xn(u,An(e,5,1,i,1.2)));let d=_t(eu(256));d.scale.set(190,190,1);let p=Ue("#9fe9ff",900,.9),g=Ue("#ffffff",260,1),x=Wt(1024,96),m=x.getContext("2d"),f=m.createLinearGradient(0,0,1024,0);f.addColorStop(0,"rgba(120,220,255,0)"),f.addColorStop(.75,"rgba(160,235,255,0.55)"),f.addColorStop(1,"rgba(255,255,255,1)"),m.fillStyle=f,m.beginPath(),m.moveTo(0,48),m.lineTo(1024,8),m.lineTo(1024,88),m.closePath(),m.fill();let y=_t(x,{add:!0});y.scale.set(3400,150,1);let _=_t(ti(1024,128),{add:!0,color:"#7fdcff",opacity:.6});_.scale.set(5200,420,1);for(let w of[_,y,p,g,d])n.add(w);let M=new L(-3400,5600,-7e3),C=new L(1300,300,-600),A=w=>M.clone().lerp(C,w),E=new L,b=new L,v=new L,S=new L;return{scene:n,camera:s,update(w,P){let F=w/P,U=_n(F),H=St(.08,.98,F)*.85+_n(St(.08,.98,F))*.15,N=A(H),K=J(500,600,U),O=J(4600,1400,U),Y=J(-3200,-2400,U);Te(s,J(-700,-150,U),J(-1300,500,U),J(3900,2900,U),J(K,N.x,.32),J(O,N.y,.32),J(Y,N.z,.32),J(.16,-.08,U)),s.updateMatrixWorld();for(let Nt of[d,p,g])Nt.position.copy(N);for(let Nt of[d,p,g,y,_])Nt.quaternion.copy(s.quaternion);d.rotateZ(-w*9),E.copy(A(Math.max(0,H-.02))).project(s),b.copy(N).project(s);let tt=Math.atan2((b.y-E.y)*Rn,(b.x-E.x)*en);v.setFromMatrixColumn(s.matrixWorld,0),S.setFromMatrixColumn(s.matrixWorld,1);let nt=v.multiplyScalar(Math.cos(tt)).add(S.multiplyScalar(Math.sin(tt)));y.rotateZ(tt),_.rotateZ(tt),y.position.copy(N).addScaledVector(nt,-1700*.98),_.position.copy(N).addScaledVector(nt,-2e3);let Tt=St(.06,.14,F);Ht(y,Tt),Ht(_,Tt*.6),Ht(p,Tt*.9),Ht(g,Tt),Ht(d,Tt)}}};xe.tunnel=(i,t)=>{let e=t.lk,n=new me,s=we();n.background=wt(e.deep),n.fog=new Qe(wt(e.deep),1500,t.portal?14e3:10500),Mi(n,e);let r=30,a=400,o=860,l=6,c=2*o*Math.tan(Math.PI/l),h=t.twist==null?.05:t.twist,u=[],d=[],p=Se(t.seed||1);for(let y=0;y<r;y++)for(let _=0;_<l;_++){let M=_/l*Math.PI*2+y*h+Math.PI/6;u.push({x:Math.cos(M)*o,y:Math.sin(M)*o,z:-y*a,w:c*1.03,h:120,d:300,rz:M+Math.PI/2}),p()<.55&&d.push({x:Math.cos(M)*(o-64),y:Math.sin(M)*(o-64),z:-y*a+140,w:c*(.3+p()*.6),h:9,d:9,rz:M+Math.PI/2})}n.add(xn(u,An(e,(t.seed||1)+4,2,i,1.3))),n.add(xn(d,new be({color:wt(e.glow).multiplyScalar(2.2),toneMapped:!1})));let g=[];for(let y=0;y<12;y++){let _=p()*Math.PI*2,M=o*(.7+p()*.2),C=new Ct(Ea,new be({color:wt(y%3?e.glow:"#ffffff").multiplyScalar(1.5),toneMapped:!1,transparent:!0,opacity:.85,blending:pi,depthWrite:!1}));C.scale.set(7,7,1800+p()*2600),C.position.set(Math.cos(_)*M,Math.sin(_)*M,0),C.userData.z0=-p()*r*a,C.userData.v=9e3+p()*9e3,n.add(C),g.push(C)}let x=-(r-5)*a,m=new zt;m.position.z=x,n.add(m),m.add(Ue(e.glow,3600,t.portal?.75:.4)),t.portal&&m.add(Ue("#ffffff",2200,1));let f=[];if(t.crest){let y=Ln(t.crest.chrome,8,4);y.scale.setScalar((t.crestScale||1)*1.25),m.add(y),m.userData.face=y;for(let M=1;M<=3;M++){let C=_t(t.crest.neon,{add:!0});C.scale.multiplyScalar((t.crestScale||1)*1.25*(1+M*.5)),C.position.z=M*380,C.userData.a=.9-M*.22,m.add(C),f.push(C)}let _=_t(t.crest.neon,{add:!0});_.scale.multiplyScalar((t.crestScale||1)*1.25*1.02),_.position.z=6,m.add(_),f.unshift(_)}return{scene:n,camera:s,update(y,_){let M=y/_,C=t.short?x+4600:900,A=t.short?x+2200:x+(t.portal?600:2900),E=t.portal?va(M)*.6+M*.4:ee(M)*.75+M*.25,b=J(C,A,E);Te(s,Math.sin(y*1.3)*60,Math.cos(y*1.1)*40,b,0,0,b-4e3,J(t.roll0==null?-.25:t.roll0,t.roll1==null?.18:t.roll1,_n(M)));for(let v of g)v.position.z=(v.userData.z0+y*v.userData.v)%(r*a)-r*a*.8+b;f.forEach((v,S)=>{let w=.65+.35*Math.sin(y*9-S*.9);Ht(v,St(0,.25,M)*w)}),m.userData.face&&(m.userData.face.rotation.y=Math.sin(y*1.6)*.25,m.userData.face.userData.face.userData.sheen.sweep.value=J(-.4,1.4,St(.2,.9,M)))}}};xe.city=(i,t)=>{let e=t.lk,n=new me,s=we();n.background=wt(e.deep),n.fog=new Qe(wt(e.deep),3500,24e3),Mi(n,e);let r=Se(t.seed||3),a=[],o=[];for(let d of[-1,1])for(let p=0;p<16;p++){let g=4e3+r()*9e3,x=600+r()*900,m=600+r()*900;(r()<.5?a:o).push({x:d*(900+x/2+r()*900),y:-4500+g/2,z:1500-p*1250-r()*500,w:x,h:g,d:m})}for(let d=0;d<10;d++){let p=6e3+r()*9e3;o.push({x:(r()-.5)*3e3,y:-4500+p/2,z:-21e3-r()*4e3,w:900+r()*1200,h:p,d:900})}n.add(xn(a,An(e,(t.seed||3)+1,0,i,1.5))),n.add(xn(o,An(e,(t.seed||3)+2,1,i,1.5)));let l=oe(t.name,{size:520,sy:1.05,fill:[[0,"#ffffff"],[.2,e.ink],[1,e.ink]],glow:{blur:40,c:e.glow,a:1}}),c=(d,p)=>(d<0,a.concat(o)).filter(g=>Math.sign(g.x)===d&&g.z>-16e3).sort((g,x)=>x.z-g.z)[p];[[-1,2],[1,4],[-1,6],[1,8]].forEach(([d,p])=>{let g=c(d,p);if(!g)return;let x=_t(l);x.rotation.set(0,-d*Math.PI/2,Math.PI/2),x.position.set(g.x-d*(g.w/2+4),Math.min(2600,g.y+g.h/2-l.w/2-300),g.z),n.add(x)});let h=new Ct(new Je(4e4,6e4),new Ve({color:wt(e.deep),metalness:.8,roughness:.35,envMap:i}));h.rotation.x=-Math.PI/2,h.position.set(0,-4500,-15e3),n.add(h);let u=t.side||1;return{scene:n,camera:s,update(d,p){let g=_n(d/p),x=d/p;Te(s,u*J(300,-200,g),J(6200,300,g),J(3200,-6500,x),u*J(-200,100,g),J(-1800,900,g),J(-5500,-15e3,x),u*J(.38,-.06,g))}}};xe.card=(i,t)=>{let e=t.lk,n=new me,s=we(),r=t.player;n.background=wt(e.deep),Mi(n,e);let a=_t(ur(e.deep,e.panel,t.seed||5));a.scale.set(9e3,5200,1),a.position.set(0,400,-4200),n.add(a);let o=Se((t.seed||5)+9),l=[];for(let w=0;w<14;w++){let P=w%2?1:-1,F=1500+o()*2600;l.push({x:P*(300+o()*2e3),y:-1400+F/2,z:-1100-o()*2400,w:240+o()*420,h:F,d:240+o()*300,ry:(o()-.5)*.6})}n.add(xn(l,An(e,(t.seed||5)+2,0,i,1.3)));let c=new zt;n.add(c);let h=new Ct(Ea,An(e,(t.seed||5)+3,2,i,1.1));h.scale.set(330,3400,260),h.position.set(900,0,-330),h.rotation.y=-.38,c.add(h);let u=oe(t.name,{size:250,sy:1.08,fill:[[0,cn(0,0,1)],[.15,e.ink],[1,e.ink]]}),d=_t(u);d.rotation.z=Math.PI/2;let p=Math.min(1,1100/u.w);d.scale.multiplyScalar(p);let g=new zt;g.position.set(900,-60,-330),g.rotation.y=-.38,g.add(d),d.position.z=136,c.add(g);let x=Ln(t.crest,12,3.5);x.position.set(-790,545,-380),x.scale.setScalar(.86*(t.crestScale||1)),c.add(x);let m=_t(tu(e,(t.seed||5)+4,1500,280));m.position.set(-430,-390,-260),c.add(m);let f=Ue(e.glow,1500,.55);f.position.set(330,60,-200),c.add(f);let y=new zt;if(c.add(y),r&&r.lit){let w=r.lit.face?700:1180,P=_t(r.lit),F=w/r.lit.h;P.scale.multiplyScalar(F),P.position.set(0,r.lit.face?30:-540+w/2-40,0),y.add(P)}y.position.set(t.playerX==null?330:t.playerX,0,0);let _=new zt;_.position.set(-800,-120,40),c.add(_);let M=jl(r?r.last:t.name,{size:290,sy:1.08,fill:Wi,glow:{blur:18,c:"rgba(0,0,0,0.55)",a:1}}),C=Math.min(1,900/Math.max(1,M.userData.w));M.scale.setScalar(C),_.add(M);let A=r&&r.first?_t(oe(r.first,{size:70,weight:700,family:"Barlow7",spacing:2,fill:e.ink,glow:{blur:12,c:"rgba(0,0,0,0.75)",a:1}})):null;A&&(A.position.set(A.userData.w/2-6,290*1.08*.72*C+52,0),_.add(A));let E=r?`${r.num||""}${r.num&&r.pos?"  ":""}${r.pos||""}`:"",b=E.trim()?_t(oe(E,{size:74,weight:800,fill:[[0,"#ffffff"],[.25,e.ink],[1,e.ink]],glow:{blur:12,c:"rgba(0,0,0,0.7)",a:1}})):null;b&&(b.position.set(M.userData.w*C-b.userData.w/2+8,-66,0),_.add(b));let v=M.userData.letters.map((w,P)=>P).sort(()=>0).map(w=>w),S=Se((t.seed||5)+21);for(let w=v.length-1;w>0;w--){let P=Math.floor(S()*(w+1));[v[w],v[P]]=[v[P],v[w]]}return{scene:n,camera:s,update(w,P){let F=w/P,U=(1-tn(w/.45))*520;c.position.x=U,y.position.x=(t.playerX==null?330:t.playerX)+(1-ee(w/.9))*120,y.scale.setScalar(1+(1-ee(w/1.2))*.04),x.rotation.y=J(1,.42,ee(w/.9))+Math.sin(w*1.1)*.03,x.userData.face.userData.sheen.sweep.value=J(-.5,1.5,St(.35,1.3,w));let H=Ge*J(1.05,.965,ee(F));Te(s,J(60,-40,F),J(-20,10,F),H,J(30,-20,F),0,0,J(.012,-.008,F)),A&&(Ht(A,ue(.25,.5,w)),A.position.x=A.userData.w/2-6-(1-ee(St(.25,.6,w)))*30),M.userData.letters.forEach((N,K)=>{let O=.32+v.indexOf(K)*.065,Y=St(O,O+.07,w),tt=w<O+.12&&w>O+.05?.6:1;Ht(N,Y*tt),N.scale.set(N.userData.w*(1+(1-Y)*.25),N.userData.h*(1+(1-Y)*.25),1)}),b&&Ht(b,ue(.85,1.1,w))}}};xe.diamond=(i,t)=>{let e=t.lk,n=new me,s=we();n.background=wt(e.deep);let r=_t(jh(e.deep,Ye(e.glow,.35),e.panel));r.scale.set(4400,4400,1),r.position.z=-900,n.add(r);let a=vi(1380,e.glow,e.glow,70);a.position.z=-300,n.add(a);let o=vi(560,e.glow,e.glow,52);o.position.z=-120,n.add(o);let l=vi(610,e.glow,e.glow,26);l.position.z=-140,n.add(l);let c=Ln(t.crest,12,3.5);c.scale.setScalar(t.crestScale||1),n.add(c);let h=oe(t.name,{size:38,weight:700,family:"Barlow7",spacing:30,fill:"#ffffff"}),u=_t(h),d=_t(h);return u.position.set(0,430,0),d.position.set(0,-430,0),n.add(u),n.add(d),{scene:n,camera:s,update(p,g){let x=p/g;a.userData.grow(ee(St(0,.5,p))),o.userData.grow(ee(St(.08,.5,p))),l.userData.grow(ee(St(.14,.55,p)));let m=tn(St(.05,.6,p));c.scale.setScalar((t.crestScale||1)*J(.78,1,m)),c.rotation.y=J(.9,0,m)+Math.sin(p*1.4)*.05,c.userData.face.userData.sheen.sweep.value=J(-.5,1.5,St(.4,1.3,p)),Ht(u,ue(.3,.6,p)),Ht(d,ue(.35,.65,p)),Te(s,0,0,Ge*J(1.06,.97,ee(x)),0,0,0,J(-.03,.02,x))}}};xe.chant=(i,t)=>{let e=t.lk,n=new me,s=we();n.background=wt(e.deep),n.fog=new Qe(wt(e.deep),2500,9e3),Mi(n,e);let r=Se(t.seed||7),a=[];for(let m of[-1,1])for(let f=0;f<7;f++)a.push({x:m*(1e3+r()*700),y:(r()-.5)*1600,z:-300-f*650,w:900,h:260+r()*340,d:70,ry:-m*(.55+r()*.3),rz:m*(r()-.5)*.3});n.add(xn(a,An(e,(t.seed||7)+1,2,i,2.2)));let o=_t(ur(e.deep,e.panel,(t.seed||7)+2));o.scale.set(9e3,6e3,1),o.position.z=-5e3,n.add(o);let l=new zt;l.rotation.y=-.2,l.position.set(40,30,0),n.add(l);let c=cn(0,0,1),h=oe(t.chant,{size:250,sy:1.12,skew:.2,fill:[[0,c],[.08,e.ink],[1,e.ink]],glow:{blur:26,c:Ye(e.glow,.7),a:1}}),u=Math.min(1,1500/h.w),d=_t(oe(t.chant,{size:320,sy:1.12,skew:.2,fill:null,outline:{w:3,c:Ye(e.ink,.5)}}));d.scale.multiplyScalar(u),d.position.set(-60,-40,-420),d.userData.a=.55,l.add(d);let p=[[300,1],[20,1.06],[-300,1]].map(([m,f],y)=>{let _=_t(h);return _.scale.multiplyScalar(u*f),_.position.set(y===1?-20:20,m,y===1?30:0),l.add(_),_}),g=_t(oe(`//   ${t.name}   //`,{size:34,weight:700,family:"Barlow7",spacing:16,fill:"#ffffff"}));g.position.set(0,-138,20),l.add(g);let x=t.crestPlain?[_t(t.crestPlain),_t(t.crestPlain)]:[];return x.forEach((m,f)=>{let y=64/m.userData.h;m.scale.multiplyScalar(y),m.position.set((f?1:-1)*(g.userData.w/2+60),-138,20),l.add(m)}),{scene:n,camera:s,update(m,f){let y=m/f;p.forEach((_,M)=>{let C=M*.08,A=tn(St(C,C+.5,m));_.position.x=(M===1?-20:20)+(1-A)*1600,Ht(_,St(C,C+.15,m))}),Ht(d,ue(.2,.6,m)),Ht(g,ue(.35,.6,m)),x.forEach(_=>Ht(_,ue(.4,.65,m))),Te(s,J(260,-160,y),J(40,-30,y),Ge*J(1.02,.94,y),J(120,-60,y),0,0,J(.04,-.03,y))}}};xe.parade=(i,t)=>{let e=t.lk,n=new me,s=we();n.background=wt(e.deep),n.fog=new Qe(wt(e.deep),2500,9e3),Mi(n,e);let r=Se(t.seed||9),a=[];for(let u=0;u<16;u++){let d=u%2?1:-1,p=1800+r()*3e3;a.push({x:d*(500+r()*1700),y:-1300+p/2,z:-600-r()*2600,w:260+r()*500,h:p,d:300,ry:(r()-.5)*.8})}n.add(xn(a,An(e,(t.seed||9)+1,r()<.5?0:1,i,1.6)));let o=_t(ur(e.deep,e.mid,(t.seed||9)+2));o.scale.set(9e3,6e3,1),o.position.z=-5e3,n.add(o);let l=Ue(e.glow,2e3,.7);l.position.z=-300,n.add(l);let c=Ln(t.crest,14,4);c.scale.setScalar(1.55*(t.crestScale||1)),n.add(c);let h=t.side||1;return{scene:n,camera:s,update(u,d){let p=u/d;c.rotation.y=h*J(.75,.15,ee(p)),c.rotation.z=h*J(-.08,.02,p),c.position.x=h*J(-90,40,ee(p)),c.userData.face.userData.sheen.sweep.value=J(-.3,1.4,p),Te(s,h*J(240,-100,p),J(-120,60,p),Ge*J(.95,.86,p),0,0,0,h*J(.05,-.02,p))}}};function au(i,t,e,n=1e3,s=1180){let r=Se(e),a=Wt(n,s),o=a.getContext("2d"),l=o.createLinearGradient(0,0,n*.3,s);l.addColorStop(0,i.mid),l.addColorStop(.55,i.panel),l.addColorStop(1,i.deep),o.fillStyle=l,o.fillRect(0,0,n,s);for(let h=0;h<18;h++){let u=r()*n,d=20+r()*90;o.fillStyle=Ye(h%3?"#000000":i.glow,h%3?.18:.12),o.fillRect(u,0,d,s)}if(t){let h=n*.78,u=h/Math.max(t.width,t.height);o.save(),o.globalAlpha=.9,o.translate(n*.06,-s*.06),o.rotate(-.12),o.drawImage(t,-h*.15,0,t.width*u,t.height*u),o.restore(),o.fillStyle=Ye(i.deep,.35),o.fillRect(0,0,n,s)}let c=o.createLinearGradient(0,s*.62,0,s);return c.addColorStop(0,"rgba(0,0,0,0)"),c.addColorStop(1,Ye(i.deep,.95)),o.fillStyle=c,o.fillRect(0,s*.62,n,s*.38),o.fillStyle=i.glow,o.fillRect(0,0,6,s),o.fillRect(n-6,0,6,s),a}var Jl=null;function ou(i,t){if(!Jl){let n=Wt(8,256),s=n.getContext("2d"),r=s.createLinearGradient(0,0,0,256);r.addColorStop(0,"rgba(0,0,0,0)"),r.addColorStop(.55,"rgba(0,0,0,0.62)"),r.addColorStop(1,"rgba(0,0,0,0.88)"),s.fillStyle=r,s.fillRect(0,0,8,256),Jl=n}let e=_t(Jl);return e.scale.set(i,t,1),e}function lu(i,t,e=1){let n=new zt,s=i.num?_t(oe(String(i.num),{size:150*e,sy:1.08,fill:t.ink})):null,r=_t(oe(i.last,{size:92*e,sy:1.08,fill:Wi})),a=i.first?_t(oe(i.first,{size:38*e,weight:700,family:"Barlow7",spacing:1,fill:"#ffffff"})):null,o=i.pos?_t(oe(i.pos,{size:34*e,weight:700,family:"Barlow7",fill:t.ink})):null,l=0;return o&&(o.position.set(l+o.userData.w/2,-28*e,0),l+=o.userData.w-6*e,n.add(o)),s&&(s.position.set(l+s.userData.w/2,0,0),l+=s.userData.w-4*e,n.add(s)),r.position.set(l+r.userData.w/2,-16*e,0),n.add(r),a&&(a.position.set(l+a.userData.w/2+4,52*e,0),n.add(a)),l+=r.userData.w,n.children.forEach(c=>{c.position.x-=l/2}),n.userData.w=l,n}xe.duel=(i,t)=>{let e=new me,n=we();e.background=wt("#03050a");let s=t.pair.map((a,o)=>{let l=o?1:-1,c=new zt;c.position.set(l*490,0,0),c.rotation.y=-l*.1,e.add(c);let h=_t(au(a.lk,a.crestImg,(t.seed||1)+o*7));h.position.z=-40,c.add(h);let u=Ue(a.lk.glow,1100,.45);u.position.set(0,80,-20),c.add(u);let d=new zt;if(c.add(d),a.player.lit){let m=a.player.lit.face,f=m?600:920,y=_t(a.player.lit),_=f/a.player.lit.h;y.scale.multiplyScalar(_),y.position.set(0,m?20:-130,0),d.add(y)}let p=ou(1e3,380);p.position.set(0,-400,10),c.add(p);let g=lu(a.player,a.lk,1);g.position.set(0,-420,30);let x=Math.min(1,860/g.userData.w);return g.scale.setScalar(x),c.add(g),{g:c,pl:d,lb:g,s:l}}),r=ei(0,-700,0,700,"#ffffff",40);return r.position.z=60,e.add(r),{scene:e,camera:n,update(a,o){let l=a/o;s.forEach(({g:c,pl:h,lb:u,s:d})=>{let p=tn(St(0,.5,a));c.position.y=(1-p)*1250*-d,h.position.y=(1-ee(St(.1,.9,a)))*-60,h.position.x=d*J(0,-16,l),Ht(u,ue(.3,.6,a)),u.position.x=(1-ee(St(.3,.7,a)))*-d*40}),r.userData.grow(ee(St(.15,.5,a))),Te(n,0,0,Ge*J(1,.95,l),0,0,0,0)}}};function c_(i,t,e){let r=Wt(2048,1152),a=r.getContext("2d"),o=Se(e);for(let[l,c,h]of[[i,0,2048*.56],[t,2048*.44,2048]]){a.save(),a.beginPath(),c===0?(a.moveTo(0,0),a.lineTo(2048*.56,0),a.lineTo(2048*.44,1152),a.lineTo(0,1152)):(a.moveTo(2048*.56,0),a.lineTo(2048,0),a.lineTo(2048,1152),a.lineTo(2048*.44,1152)),a.clip();let u=c===0?2048*.22:2048*.78,d=a.createRadialGradient(u,1152*.45,0,u,1152*.5,2048*.55);d.addColorStop(0,l.mid),d.addColorStop(.5,l.panel),d.addColorStop(1,l.deep),a.fillStyle=d,a.fillRect(0,0,2048,1152);for(let p=0;p<12;p++){a.fillStyle=Ye(l.glow,.05+o()*.06);let g=c+o()*(h-c);a.fillRect(g,0,10+o()*60,1152)}a.restore()}return a.strokeStyle="rgba(255,255,255,0.9)",a.lineWidth=4,a.beginPath(),a.moveTo(2048*.56,0),a.lineTo(2048*.44,1152),a.stroke(),r}xe.faceoff=(i,t)=>{let e=new me,n=we(),s=_t(c_(t.home.lk,t.away.lk,t.seed||3));s.scale.set(3600,2025,1),s.position.z=-900,e.add(s);let r=[t.home,t.away].map((o,l)=>{let c=l?1:-1,h=Ue(o.lk.glow,1500,.8);h.position.set(c*470,0,-200),e.add(h);let u=Ln(o.chrome,14,4);return u.scale.setScalar(o.crestScale||1),e.add(u),{cr:u,s:c,gl:h}}),a=Ue("#ffffff",900,1);return a.position.z=50,e.add(a),{scene:e,camera:n,update(o,l){let c=o/l;r.forEach(({cr:h,s:u})=>{let d=tn(St(0,.65,o));h.position.x=u*J(1500,450,d),h.rotation.y=-u*J(1.3,.3,d)-u*Math.sin(o*1.2)*.04,h.userData.face.userData.sheen.sweep.value=J(-.5,1.5,St(.55+(u>0?.15:0),1.4,o))}),Ht(a,Math.max(0,1-Math.abs(o-.55)/.18)*.9),Te(n,J(0,0,c),J(-30,20,c),Ge*J(1.04,.93,ee(c)),0,0,0,0)}}};xe.abbr=(i,t)=>{let e=new me,n=we(),{home:s,away:r}=t,a=Wt(2048,1152),o=a.getContext("2d");o.fillStyle="#05060a",o.fillRect(0,0,2048,1152);for(let[x,m]of[[s.lk,300],[r.lk,1748]]){let f=o.createRadialGradient(m,576,0,m,576,1200);f.addColorStop(0,Ye(x.glow,.55)),f.addColorStop(.4,Ye(x.panel,.6)),f.addColorStop(1,"rgba(0,0,0,0)"),o.fillStyle=f,o.fillRect(0,0,2048,1152)}o.globalAlpha=.25,o.strokeStyle="#ffffff";for(let x=-1152;x<2048;x+=30)o.beginPath(),o.moveTo(x,1152),o.lineTo(x+1152,0),o.stroke();let l=_t(a);l.scale.set(3800,2140,1),l.position.z=-800,e.add(l);let c=(x,m,f,y,_)=>{let M=ei(x,m,x+f,m+y,_,60);return M.position.z=-200,e.add(M),M},h=[c(-1500,380,600,600,s.lk.glow),c(-1500,-380,600,-600,s.lk.glow),c(1500,380,-600,600,r.lk.glow),c(1500,-380,-600,-600,r.lk.glow)],u=vi(250,s.lk.glow,r.lk.glow,46);e.add(u);let d=vi(205,s.lk.glow,r.lk.glow,22);e.add(d);let p=(x,m)=>{let f=cn(0,0,1),y=oe(x.abbr,{size:430,sy:1.12,fill:[[0,f],[.12,x.lk.glow],[.7,x.lk.glow],[1,x.lk.mid]],extrude:{n:14,dx:1.1,dy:1.8,top:x.lk.panel,bottom:x.lk.deep}}),_=_t(y),M=Math.min(1,600/y.w);_.scale.multiplyScalar(M),_.position.set(m*(215+_.userData.w*M/2),0,20),e.add(_);let C=oe(x.name,{size:34,weight:700,family:"Barlow7",spacing:26,fill:"#ffffff"}),A=_t(C),E=_t(C);return A.position.set(_.position.x,390,0),E.position.set(_.position.x,-390,0),e.add(A),e.add(E),{p:_,t1:A,t2:E,x:_.position.x,s:m}},g=[p(s,-1),p(r,1)];return{scene:e,camera:n,update(x,m){let f=x/m;g.forEach(y=>{let _=tn(St(0,.5,x));y.p.position.x=y.x+y.s*(1-_)*1200,Ht(y.t1,ue(.35,.65,x)),Ht(y.t2,ue(.4,.7,x))}),u.userData.grow(ee(St(.12,.55,x))),d.userData.grow(ee(St(.2,.6,x))),h.forEach((y,_)=>y.userData.grow(ee(St(.05+_*.04,.5+_*.04,x)))),Te(n,0,0,Ge*J(1.05,.95,ee(f)),0,0,0,J(.015,-.01,f))}}};xe.trio=(i,t)=>{let e=t.lk,n=new me,s=we();n.background=wt(e.deep),Mi(n,e);let r=_t(au(e,null,(t.seed||4)+1,2048,1152));r.scale.set(3800,2140,1),r.position.z=-1200,n.add(r);let a=Ln(t.crest,12,4);a.position.set(0,300,-500),a.scale.setScalar(1.35*(t.crestScale||1)),n.add(a);let o=Se(t.seed||4),l=[];for(let p=0;p<10;p++){let g=p%2?1:-1,x=2400+o()*1600;l.push({x:g*(900+o()*900),y:-1400+x/2,z:-900-o()*900,w:300+o()*300,h:x,d:260,ry:g*.4})}n.add(xn(l,An(e,(t.seed||4)+2,0,i,1.4)));let c=t.players.slice(0,3),h=c.length===3?[[-600,.86,-40],[0,1,40],[600,.86,-40]]:c.length===2?[[-330,.95,0],[330,.95,0]]:[[0,1,0]],d=(c.length===3?[1,0,2]:c.map((p,g)=>g)).map((p,g)=>{let x=c[p],[m,f,y]=h[g],_=new zt;_.position.set(m,0,y),n.add(_);let M=_t(Xl(64),{color:e.glow,opacity:0}),C=new Ct(Sa,new be({color:wt(e.panel),transparent:!0,opacity:.55,depthWrite:!1,toneMapped:!1}));C.scale.set(520*f,880*f,1),C.position.set(0,-60,-30),C.rotation.y=-m/2600,C.userData.a=.55,_.add(C);let A=ei(-260*f,-500,-260*f,380*f,e.glow,22,!1),E=ei(260*f,-500,260*f,380*f,e.glow,22,!1);A.position.z=-20,E.position.z=-20,_.add(A),_.add(E);let b=new zt;if(_.add(b),x.lit){let w=(x.lit.face?520:860)*f,P=_t(x.lit),F=w/x.lit.h;P.scale.multiplyScalar(F),P.position.set(0,x.lit.face?-40:-540+w/2-30,0),b.add(P)}let v=ou(560*f,300);v.position.set(0,-420,20),_.add(v);let S=lu(x,e,.78*f);return S.position.set(0,-410,40),S.scale.setScalar(Math.min(1,520*f/S.userData.w)),_.add(S),{g:_,ph:b,lb:S,fr:C,i:g,panel:M,e1:A,e2:E}});return{scene:n,camera:s,update(p,g){let x=p/g;d.forEach(({ph:m,lb:f,fr:y,i:_,e1:M,e2:C})=>{let A=_*.07,E=tn(St(A,A+.45,p));m.position.y=(1-E)*-420,Ht(m,St(A,A+.12,p)),Ht(y,St(A,A+.2,p)),Ht(f,ue(A+.25,A+.5,p)),M.userData.grow(E),C.userData.grow(E)}),a.rotation.y=Math.sin(p*1.5)*.12,a.userData.face.userData.sheen.sweep.value=J(-.4,1.4,St(.2,1.1,p)),Te(s,0,J(-10,10,x),Ge*J(1,.95,x),0,0,0,0)}}};function h_(i,t,e){let n=new Bi;return n.moveTo(0,0),n.quadraticCurveTo(i*.5,t*.2+e,i,t*1.2+e*1.8),n.lineTo(i*.985,t*1.2+e*1.8-10),n.quadraticCurveTo(i*.5,-t*.55+e*.9,0,-t),n.closePath(),new ws(n,{depth:26,bevelEnabled:!0,bevelThickness:7,bevelSize:5,bevelSegments:2,curveSegments:14})}xe.lockup=(i,t)=>{let e={glow:"#37d6ff",panel:"#071a38",mid:"#0f2e5c",deep:"#010510",ink:"#bdefff"},n=new me,s=we();n.background=wt("#01040c"),n.fog=new Qe(wt("#020814"),3500,16e3),Mi(n,e);let r=_t(ur("#010309","#0a2a5e",19));r.scale.set(26e3,15e3,1),r.position.set(0,1500,-12e3),n.add(r);let a=[];for(let b of[-1,1])for(let v=0;v<9;v++)a.push({x:b*(980+v*380),y:300,z:-500-v*760,w:120,h:3600+v*260,d:1100,ry:b*.42});n.add(xn(a,An(e,41,1,i,1.25)));let o=[];for(let b=0;b<12;b++)o.push({x:0,y:2050+b*25,z:-900-b*650,w:5200+b*300,h:26,d:26});n.add(xn(o,new be({color:wt(e.glow).multiplyScalar(1.4),toneMapped:!1})));let l=[];for(let b=0;b<5;b++){let v=_t(ti(1024,128),{add:!0,color:e.glow,opacity:.22});v.scale.set(4200,620,1),v.rotation.z=Math.PI/2+(b-2)*.2,v.position.set((b-2)*560,1100,-2600),v.userData.a=.22,n.add(v),l.push(v)}let c=new Ct(new Je(3e4,3e4),new Ve({color:wt("#020814"),metalness:.9,roughness:.22,envMap:i}));c.rotation.x=-Math.PI/2,c.position.y=-760,n.add(c);let h=[];for(let b=0;b<14;b++)h.push({x:(b%2?1:-1)*(300+Math.floor(b/2)*260),y:-755,z:-300-Math.floor(b/2)*900,w:16,h:4,d:700});n.add(xn(h,new be({color:wt(e.glow).multiplyScalar(1.6),toneMapped:!1})));let u=Ue(e.glow,3600,.5);u.position.set(0,40,-900),n.add(u);let d=Ue("#ffffff",1500,.25);d.position.set(0,30,-700),n.add(d);let p=new Ve({color:wt("#f2f5fa"),metalness:1,roughness:.12,envMap:i,envMapIntensity:2.4,emissive:wt("#1c2a3c")}),g=[];for(let b of[-1,1])for(let v of[1,-1]){let S=h_(v>0?1050:900,v>0?46:34,v>0?110:60),w=new Ct(S,p),P=new zt;P.add(w),w.scale.set(1,v,1),w.rotation.x=-v*.42;let F=ei(40,v*-16,v>0?1e3:860,v*(v>0?160:92),e.glow,44);F.position.z=44,P.add(F),P.scale.x=b,P.userData.home={x:b*(v>0?560:520),y:v>0?125:-95},P.userData.sx=b,P.userData.sy=v,n.add(P),g.push(P)}let x=oe(t.title||"MATCH NIGHT",{size:236,sy:1,skew:.12,fill:hr,stroke:{w:3,c:"#121722"},extrude:{n:16,dx:.9,dy:1.5}}),m=Ma(x,{gain:1}),f=Math.min(1,1040/x.w);m.scale.multiplyScalar(f),m.position.set(0,20,30),n.add(m);let y=_t(oe(t.sub||"WHITEBROOK ROAD SPORTS",{size:54,weight:700,family:"Barlow7",spacing:15,fill:hr,stroke:{w:1.5,c:"#10141c"}}));y.position.set(0,-145,30),n.add(y);let _=Ma(nu(240,e.glow));_.position.set(0,255,20),n.add(_);let M=null;if(t.mark){let b=yi(t.mark,250),v=Wt(b.width+120,b.height+120),S=v.getContext("2d"),w=Qn(b,"#ffffff");S.filter="blur(16px)",S.drawImage(w,60,60),S.drawImage(w,60,60),S.filter="blur(5px)",S.drawImage(w,60,60),S.filter="none",S.drawImage(b,60,60),M=_t(v),M.position.set(0,-290,30),n.add(M)}let C=[];if(t.fixture){let b=_t(oe(t.fixture,{size:92,sy:1.06,fill:Wi,glow:{blur:22,c:Ye(e.glow,.8),a:1}})),v=Math.min(1,1500/b.userData.w);if(b.scale.multiplyScalar(v),b.position.set(0,-270,40),n.add(b),C.push(b),t.line2){let S=_t(oe(t.line2,{size:38,weight:700,family:"Barlow7",spacing:9,fill:e.ink}));S.position.set(0,-362,40),n.add(S),C.push(S)}M&&(M.position.y=-455)}let A=[],E=Se(77);for(let b=0;b<26;b++){let v=Ue(b%4?e.glow:"#ffffff",30+E()*60,1);v.position.set((E()-.5)*2600,(E()-.5)*1300,100+E()*600),v.userData.ph=E()*6,v.userData.sp=2+E()*4,n.add(v),A.push(v)}return{scene:n,camera:s,update(b,v){let S=b/v;g.forEach((P,F)=>{let U=tn(St(.02+F%2*.06,.62+F%2*.06,b)),H=P.userData.home;P.position.set(H.x+P.userData.sx*(1-U)*2400,H.y+P.userData.sy*(1-U)*300,(1-U)*600),P.rotation.z=P.userData.sx*P.userData.sy*(1-U)*.5});let w=tn(St(0,.5,b));m.scale.set(x.w*f*J(1.4,1,w),x.h*f*J(1.4,1,w),1),Ht(m,St(0,.18,b)),m.userData.sheen.sweep.value=b<2?J(-.6,1.6,St(.45,1.35,b)):J(-.6,1.6,St(2.2,3.1,b)),Ht(_,ue(.2,.5,b)),_.position.y=255+(1-ee(St(.2,.7,b)))*80,_.userData.sheen.sweep.value=J(-.5,1.5,St(.6,1.4,b)),Ht(y,ue(.4,.8,b)),M&&Ht(M,ue(t.fixture?1.4:.6,t.fixture?1.8:1,b)),C.forEach((P,F)=>{Ht(P,ue(.9+F*.3,1.3+F*.3,b)),P.position.y+=0}),Ht(u,.5*St(0,.4,b)),Ht(d,.25*St(0,.4,b)),l.forEach((P,F)=>Ht(P,(.6+.4*Math.sin(b*1.3+F))*St(.1,.6,b))),A.forEach(P=>Ht(P,Math.max(0,Math.sin(b*P.userData.sp+P.userData.ph))*St(.3,.8,b))),Te(s,J(150,-60,ee(S)),J(-60,20,S),Ge*J(1.16,1,ee(Math.min(1,b/Math.min(v,3.5)))),0,0,0,J(-.02,0,S))}}};function cu(){let i=new ln({color:15659508,metalness:1,roughness:.13,clearcoat:.4,clearcoatRoughness:.1}),t=new ln({color:14278114,metalness:1,roughness:.32}),e=new ln({color:16762970,metalness:1,roughness:.18,clearcoat:.3}),n=new ln({color:855568,metalness:.1,roughness:.28,clearcoat:1,clearcoatRoughness:.08});function s(E){let b=document.createElement("canvas");b.width=1024,b.height=256;let v=b.getContext("2d"),S=E==="malachite"?7:19,w=()=>(S=S*16807%2147483647,S/2147483647);if(E==="malachite"){v.fillStyle="#0d5a3e",v.fillRect(0,0,1024,256);for(let F=0;F<70;F++){let U=w()*1024,H=w()*256,N=20+w()*120;for(let K=0;K<9;K++)v.beginPath(),v.ellipse(U,H,N*(1-K*.1)*1.6,N*(1-K*.1),w()*.4,0,Math.PI*2),v.strokeStyle=K%2?`rgba(4,40,26,${.35+w()*.3})`:`rgba(60,170,120,${.25+w()*.3})`,v.lineWidth=2+w()*5,v.stroke()}}else{let F=v.createLinearGradient(0,0,1024,256);F.addColorStop(0,"#d9b65a"),F.addColorStop(.5,"#e8c977"),F.addColorStop(1,"#cfa64a"),v.fillStyle=F,v.fillRect(0,0,1024,256);for(let U=0;U<40;U++){v.beginPath();let H=w()*1024,N=w()*256;v.moveTo(H,N);for(let K=0;K<12;K++)H+=(w()-.3)*60,N+=(w()-.5)*30,v.lineTo(H,N);v.strokeStyle=`rgba(${w()<.5?"120,80,30":"250,235,190"},${.15+w()*.25})`,v.lineWidth=.6+w()*2,v.stroke()}}let P=new Ni(b);return P.colorSpace=Re,P.wrapS=P.wrapT=mi,P}let r=new ln({map:s("malachite"),roughness:.22,metalness:0,clearcoat:1,clearcoatRoughness:.05}),a=new ln({map:s("marble"),roughness:.3,metalness:0,clearcoat:1,clearcoatRoughness:.08});function o(E,b=160){return new Ss(E.map(([S,w])=>new at(S,w))).getPoints(b).map(S=>new at(Math.max(5e-4,S.x),S.y))}function l(E,b,v=128,S={}){let w=new er(S.raw?E.map(([P,F])=>new at(P,F)):o(E,S.n||160),v);if(S.flute){let P=w.attributes.position;for(let F=0;F<P.count;F++){let U=P.getX(F),H=P.getY(F),N=P.getZ(F);if(H<S.flute.y0||H>S.flute.y1)continue;let K=Math.atan2(N,U),O=Math.sin(Math.PI*(H-S.flute.y0)/(S.flute.y1-S.flute.y0)),Y=1+S.flute.depth*O*Math.cos(K*S.flute.n);P.setX(F,U*Y),P.setZ(F,N*Y)}w.computeVertexNormals()}return S.squash&&w.scale(1,1,S.squash),new Ct(w,b)}function c(E,b,v,S={}){let w=new Oi(E.map(([U,H,N])=>new L(U,H,N||0)),!1,"catmullrom",.5),P=new As(w,S.seg||120,b,S.radial||16,!1),F=new Ct(P,v);return S.flat&&(F.scale.z=S.flat),F}function h(E,b,v,S){let w=new Ct(new xi(E,v,16,128),S);return w.rotation.x=Math.PI/2,w.position.y=b,w}function u(E,b){return new Ct(new Ts(E,32,20),b)}function d(E){let b=E.clone();return b.scale.x*=-1,b}function p(){let E=new zt,b=new Ct(new Es(.022,.05,8,16),e);b.rotation.z=Math.PI/2.6,b.position.set(-.008,.035,0),E.add(b);let v=u(.028,e);v.position.set(-.03,.025,0),E.add(v);let S=u(.026,e);S.position.set(.018,.055,0),E.add(S);let w=new Ct(new _i(.03,1),e);w.position.set(.03,.082,0),E.add(w);let P=u(.019,e);P.position.set(.045,.085,0),E.add(P);let F=u(.011,e);F.scale.set(1.2,.8,1),F.position.set(.062,.078,0),E.add(F);for(let H of[-1,1]){let N=new Ct(new bs(.007,.008,.05,10),e);N.position.set(.03,.025,H*.012),E.add(N);let K=u(.006,e);K.position.set(.04,.104,H*.013),E.add(K)}let U=c([[-.05,.02],[-.07,.04],[-.065,.075],[-.05,.085]],.004,e,{seg:24,radial:8});return E.add(U),E}function g(){let E=new zt,b=new Ct(new Es(.02,.06,8,16),e);b.rotation.z=Math.PI/2,b.position.set(-.01,.022,0),b.scale.set(1,1,1.1),E.add(b);let v=u(.024,e);v.position.set(-.045,.026,0),E.add(v);let S=u(.024,e);S.position.set(.028,.03,0),E.add(S);let w=new Ct(new _i(.028,1),e);w.position.set(.04,.056,0),E.add(w);let P=u(.018,e);P.position.set(.055,.06,0),E.add(P);let F=u(.01,e);F.scale.set(1.3,.85,1),F.position.set(.071,.053,0),E.add(F);for(let U of[-.012,.012]){let H=new Ct(new Es(.006,.035,6,10),e);H.rotation.z=Math.PI/2,H.position.set(.06,.007,U),E.add(H);let N=u(.006,e);N.position.set(.048,.078,U),E.add(N)}return E.add(c([[-.065,.02],[-.085,.015],[-.095,.03],[-.09,.045]],.0035,e,{seg:24,radial:8})),E}let x={pl(){let E=new zt;E.add(l([[.176,0],[.178,.012],[.176,.042],[.172,.05]],r,128,{raw:!0})),E.add(h(.177,.003,.004,i)),E.add(h(.173,.05,.005,i)),E.add(l([[.17,.05],[.165,.065],[.15,.08],[.115,.1],[.085,.125],[.068,.15],[.06,.17]],i)),E.add(h(.16,.072,.005,i)),E.add(h(.1,.11,.005,i)),E.add(l([[.06,.17],[.055,.19],[.062,.21],[.085,.27],[.11,.35],[.13,.44],[.142,.53],[.146,.6],[.142,.66],[.13,.7],[.115,.725],[.09,.745],[.074,.765],[.068,.79],[.07,.83],[.078,.845]],i,192)),E.add(h(.07,.8,.006,i)),E.add(h(.062,.2,.006,i));let b=new zt,v=240,S=[],w=[],P=.845,F=.885,U=.985,H=6;for(let[K,O]of[[0,!1],[-.006,!0]]){let Y=S.length/3;for(let tt=0;tt<=v;tt++){let nt=tt/v*Math.PI*2,Tt=nt*H/(Math.PI*2)%1,Nt=1-Math.abs(Tt*2-1),X=F+(U-F)*Nt,st=.084+K,xt=.084+(X-P)*.5+K;S.push(st*Math.cos(nt),P,st*Math.sin(nt),xt*Math.cos(nt),X,xt*Math.sin(nt))}for(let tt=0;tt<v;tt++){let nt=Y+tt*2,Tt=nt+1,Nt=nt+2,X=nt+3;O?w.push(nt,Nt,Tt,Tt,Nt,X):w.push(nt,Tt,Nt,Tt,X,Nt)}}let N=new Ie;N.setAttribute("position",new jt(S,3)),N.setIndex(w),N.computeVertexNormals(),b.add(new Ct(N,new ln({color:16762970,metalness:1,roughness:.17,clearcoat:.3,side:qe}))),b.add(h(.085,P+.004,.007,e)),b.add(h(.098,P+.03,.005,e));for(let K=0;K<H;K++){let O=(K+.5)/H*Math.PI*2,Y=.084+(U-P)*.5,tt=u(.013,e);tt.position.set(Y*Math.cos(O),U+.01,Y*Math.sin(O)),b.add(tt)}b.rotation.y=Math.PI/12,E.add(b);for(let K of[-1,1])E.add(c([[K*.12,.715],[K*.2,.725],[K*.245,.7],[K*.25,.55],[K*.245,.33],[K*.2,.24],[K*.11,.21]],.0085,i,{radial:10,seg:160}));for(let K of[-1,1]){let O=g();O.position.set(K*.14,.722,.012),O.scale.set(K*1,1,1),O.rotation.y=K*-.35,E.add(O)}return E},facup(){let E=new zt;E.add(l([[.18,0],[.18,.06],[.176,.075]],n,128,{raw:!0})),E.add(h(.177,.075,.006,i)),E.add(l([[.172,.078],[.165,.1],[.135,.15],[.095,.2],[.07,.235],[.06,.25]],i)),E.add(l([[.06,.25],[.085,.265],[.088,.28],[.06,.295],[.055,.31]],i)),E.add(l([[.055,.31],[.11,.33],[.155,.37],[.162,.42],[.14,.47],[.122,.5]],i,160,{flute:{y0:.31,y1:.49,n:14,depth:.06}})),E.add(l([[.12,.5],[.125,.56],[.14,.64],[.158,.71],[.168,.745],[.175,.755]],i)),E.add(h(.13,.5,.008,i)),E.add(h(.172,.756,.007,i)),E.add(l([[.17,.758],[.168,.775],[.145,.805],[.1,.835],[.06,.852],[.045,.86]],i,160,{flute:{y0:.765,y1:.85,n:16,depth:.05}})),E.add(l([[.045,.86],[.03,.875],[.042,.9],[.028,.93],[.032,.96],[.018,.985],[.001,1]],i));let b=c([[.15,.725],[.2,.75],[.245,.72],[.25,.64],[.21,.58],[.165,.555],[.14,.54]],.011,i);E.add(b),E.add(d(b));for(let v of[-1,1]){let S=c([[v*.2,.75],[v*.215,.77],[v*.232,.765],[v*.232,.748]],.008,i,{seg:30});E.add(S)}return E},leaguecup(){let E=new zt;E.add(l([[.21,0],[.21,.05],[.19,.06],[.19,.1],[.17,.11]],n,128,{raw:!0})),E.add(h(.2,.05,.006,i)),E.add(h(.18,.105,.006,i)),E.add(l([[.165,.11],[.15,.13],[.1,.17],[.07,.2],[.06,.24]],i)),E.add(l([[.06,.24],[.08,.255],[.06,.27],[.065,.29]],i)),E.add(l([[.065,.29],[.13,.32],[.2,.38],[.235,.46],[.24,.55],[.25,.62],[.262,.64]],i,160,{flute:{y0:.3,y1:.42,n:18,depth:.04}})),E.add(h(.258,.64,.007,i)),E.add(l([[.245,.64],[.235,.6],[.2,.5],[.12,.4],[.06,.34]],t,128,{raw:!1}));for(let b=0;b<3;b++){let v=new zt;v.rotation.y=b/3*Math.PI*2+Math.PI/2,v.add(c([[.235,.6],[.3,.62],[.335,.57],[.31,.48],[.25,.44],[.2,.42]],.012,i)),v.add(c([[.3,.62],[.32,.65],[.345,.64],[.34,.615]],.008,i,{seg:30})),E.add(v)}return E},ucl(){let E=new zt;E.add(l([[.165,0],[.168,.008],[.16,.018],[.11,.03],[.075,.045]],i));for(let[v,S]of[[.055,.07],[.075,.062],[.095,.07],[.115,.06]])E.add(h(S,v,.009,i));E.add(l([[.075,.045],[.05,.06],[.045,.13],[.05,.15],[.07,.17]],i)),E.add(l([[.07,.17],[.1,.21],[.14,.3],[.175,.42],[.195,.55],[.198,.63],[.188,.69],[.165,.73],[.152,.745],[.155,.765],[.168,.79],[.172,.8]],i)),E.add(h(.17,.8,.006,i)),E.add(h(.153,.745,.005,i));let b=c([[.09,.22],[.16,.3],[.235,.45],[.275,.62],[.29,.8],[.285,.94],[.255,.995],[.215,.985],[.195,.93],[.21,.88],[.2,.83],[.17,.8]],.016,i,{flat:.45,seg:200});return E.add(b),E.add(d(b)),E},uel(){let E=new zt,b=new Ct(new on(.34,.12,.25,1,1,1),a);b.position.y=.06,E.add(b),E.add(l([[.14,.12],[.13,.135],[.09,.16],[.06,.18]],i,128,{squash:.75})),E.add(l([[.06,.18],[.05,.22],[.045,.27],[.055,.31]],i,96,{squash:.75})),E.add(h(.05,.29,.011,i)),E.add(l([[.055,.31],[.1,.36],[.15,.44],[.19,.56],[.22,.7],[.24,.84],[.25,.95],[.252,.99]],i,192,{flute:{y0:.33,y1:.98,n:20,depth:.045},squash:.7}));let v=h(.25,.99,.008,i);return v.scale.y=.7,E.add(v),E},uecl(){let E=new zt;E.add(l([[.16,0],[.16,.04],[.14,.06],[.08,.07]],i));for(let b=0;b<32;b++){let v=b/32*Math.PI*2,S=[];for(let w=0;w<=10;w++){let P=.07+w*.093,F=.07+.17*Math.pow(w/10,1.4)-.02*Math.sin(Math.PI*w/10),U=v+.5*(w/10);S.push([F*Math.cos(U),P,F*Math.sin(U)])}E.add(c(S,.0105,i,{radial:6,seg:60}))}return E.add(h(.24,1,.01,i)),E.add(h(.075,.075,.012,i)),E},generic(){let E=new zt;E.add(l([[.17,0],[.17,.08],[.155,.09]],n,96,{raw:!0})),E.add(l([[.15,.09],[.1,.14],[.06,.2],[.05,.3],[.08,.36],[.16,.48],[.2,.62],[.215,.75],[.225,.8]],i));let b=c([[.2,.72],[.28,.72],[.3,.6],[.24,.5],[.17,.47]],.013,i);return E.add(b),E.add(d(b)),E},laliga(){let E=new zt;return E.add(l([[.2,0],[.202,.03],[.195,.045],[.17,.06],[.165,.085],[.13,.11],[.09,.14],[.07,.17]],i)),E.add(h(.2,.02,.006,i)),E.add(h(.168,.075,.006,e)),E.add(l([[.07,.17],[.05,.21],[.045,.27],[.05,.32],[.07,.36]],i)),E.add(h(.05,.255,.014,i)),E.add(l([[.07,.36],[.13,.385],[.2,.43],[.255,.51],[.28,.61],[.282,.71],[.268,.81],[.255,.88],[.262,.94],[.285,.99]],i,192,{flute:{y0:.37,y1:.6,n:24,depth:.05}})),E.add(h(.283,.66,.007,e)),E.add(h(.284,.99,.008,i)),E.add(l([[.276,.99],[.25,.93],[.25,.85],[.2,.62],[.08,.42]],t,128)),E},seriea(){let E=new zt;E.add(l([[.17,0],[.172,.025],[.16,.04],[.15,.06],[.11,.085],[.075,.11],[.06,.13]],i)),E.add(h(.168,.014,.007,e)),E.add(h(.151,.056,.005,i)),E.add(l([[.06,.13],[.042,.18],[.036,.26],[.04,.33],[.055,.37]],i));let b=u(.05,e);b.scale.set(1,.6,1),b.position.y=.3,E.add(b),E.add(l([[.055,.37],[.075,.4],[.1,.45],[.125,.53],[.15,.63],[.18,.74],[.22,.85],[.26,.94],[.29,.99],[.3,1]],i,192)),E.add(h(.299,1,.007,e)),E.add(l([[.292,.998],[.25,.93],[.2,.83],[.15,.68],[.1,.5]],t,128));for(let[v,S,w,P]of[[.63,.15,24,.011],[.48,.112,18,.009]]){E.add(h(S,v,.004,e));for(let F=0;F<w;F++){let U=F/w*Math.PI*2,H=u(P,e);H.position.set((S+P*.5)*Math.cos(U),v,(S+P*.5)*Math.sin(U)),E.add(H)}}return E},bundesliga(){let E=new zt,b=new zt;b.add(l([[.001,-.012],[.08,-.008],[.2,.004],[.27,.016],[.293,.03],[.301,.044],[.297,.052],[.282,.046],[.25,.031],[.2,.022],[.12,.018],[.08,.024],[.064,.032],[.001,.034]],i,192,{n:220}));let v=w=>w<.12?.018+(.12-w)*.15:w<.2?.018+(w-.12)*.05:.022+(w-.2)*.18;for(let[w,P]of[[.128,.152],[.162,.186],[.196,.222],[.232,.258]])b.add(l([[P,v(P)+.0016],[w,v(w)+.0016]],t,192,{raw:!0}));b.add(h(.087,.03,.008,e));let S=new ln({color:817730,roughness:.05,metalness:0,clearcoat:1,clearcoatRoughness:.03,emissive:137227});for(let w=0;w<5;w++){let P=w/5*Math.PI*2+Math.PI/2,F=new Ct(new _i(.014,1),S);F.scale.set(1,.6,1),F.position.set(.087*Math.cos(P),.04,.087*Math.sin(P)),b.add(F)}return b.add(l([[.066,.033],[.058,.046],[.035,.052],[.001,.053]],i,128)),b.rotation.x=Math.PI/2-.14,E.add(b),E},ligue1(){let E=new zt,b=new Ct(new bs(.2,.215,.05,6),t);b.position.y=.025,E.add(b);let v=new Ct(new bs(.155,.18,.05,6),i);v.position.y=.075,E.add(v);let S=new ln({color:15659508,metalness:1,roughness:.12,clearcoat:.4,clearcoatRoughness:.1,side:qe}),w=60,P=.1,F=1,U=O=>.155-.075*Math.sin(Math.PI*Math.min(1,O*1.3))+.16*O*O,H=(O,Y)=>{let tt=O/6*Math.PI*2+Y*Math.PI/3+Math.PI/6,nt=U(Y);return[nt*Math.cos(tt),P+Y*(F-P),nt*Math.sin(tt)]};for(let O=0;O<6;O++){let Y=[],tt=[],nt=[],Tt=[];for(let X=0;X<=w;X++){let st=X/w,xt=H(O,st),ct=H(O+1,st),Ut=xt.map((At,Jt)=>At+(ct[Jt]-At)*.07),kt=xt.map((At,Jt)=>At+(ct[Jt]-At)*.93);if(Y.push(...Ut,...kt),X%4===0&&(nt.push(Ut),Tt.push(kt)),X<w){let At=X*2;tt.push(At,At+2,At+1,At+1,At+2,At+3)}}let Nt=new Ie;Nt.setAttribute("position",new jt(Y,3)),Nt.setIndex(tt),Nt.computeVertexNormals(),E.add(new Ct(Nt,S)),E.add(c(nt,.0055,i,{radial:8,seg:60})),E.add(c(Tt,.0055,i,{radial:8,seg:60}))}let N=new Ct(new xi(U(1)*.93,.01,10,6),i);N.rotation.x=Math.PI/2,N.rotation.z=Math.PI/6+Math.PI/3,N.position.y=F,E.add(N);let K=new Ct(new xi(U(0)*.93,.009,10,6),e);return K.rotation.x=Math.PI/2,K.rotation.z=Math.PI/6,K.position.y=P+.002,E.add(K),E}},m={};function f(E,b){let v=new Gt(E);return v.multiplyScalar(b),"#"+v.getHexString()}function y(E,b,v){let S=E+"|"+(b||"")+"|"+(v||"");if(m[S])return m[S];let w=document.createElement("canvas");w.width=128,w.height=2048;let P=w.getContext("2d"),F=P.createLinearGradient(0,0,128,0);if(F.addColorStop(0,f(E,.72)),F.addColorStop(.3,E),F.addColorStop(.5,f(E,1.15)),F.addColorStop(.75,E),F.addColorStop(1,f(E,.7)),P.fillStyle=F,P.fillRect(0,0,128,2048),b){P.save(),P.translate(66,0),P.rotate(Math.PI/2),P.font="700 76px 'Barlow Condensed', 'Arial Narrow', sans-serif",P.fillStyle=v||"#fff",P.textBaseline="middle";for(let H=30;H<2048;H+=560)P.fillText(b,H,0);P.restore()}let U=new Ni(w);return U.colorSpace=Re,U.wrapS=Kn,U.wrapT=mi,U.anisotropy=4,m[S]=U,U}let _=[];function M(E,b,v){let w=new Ie;w.setAttribute("position",new ke(new Float32Array(57*6),3)),w.setAttribute("uv",new ke(new Float32Array(57*4),2));let P=[];for(let H=0;H<56;H++){let N=H*2;P.push(N,N+2,N+1,N+1,N+2,N+3)}w.setIndex(P);let F=new ln({map:v,roughness:.42,metalness:0,sheen:1,sheenRoughness:.35,sheenColor:new Gt(16777215),side:qe}),U=new Ct(w,F);return U.userData.rib=Object.assign({p0:E,N:56,width:.032,len:.6,out:.06,side:1,zd:.03,tw0:0,tw:.5,ph:0},b),C(U,0),_.push(U),U}function C(E,b){let v=E.userData.rib,S=E.geometry.attributes.position.array,w=E.geometry.attributes.uv.array,P=v.len/(v.width*16);for(let F=0;F<=v.N;F++){let U=F/v.N,H=v.p0[1]-U*v.len,N=v.out*U*U+.014*U*Math.sin(U*5+b*1.7+v.ph),K=v.p0[0]+v.side*N,O=v.p0[2]+v.zd*U+.012*U*Math.sin(U*4+b*1.3+v.ph*1.3),Y=v.tw0+v.tw*Math.sin(U*3.1+v.ph+b*.9)*Math.min(1,U*3),tt=Math.cos(Y)*v.width/2,nt=Math.sin(Y)*v.width/2;S.set([K-tt,H,O-nt,K+tt,H,O+nt],F*6),w.set([0,1-U*P,1,1-U*P],F*4)}E.geometry.attributes.position.needsUpdate=!0,E.geometry.attributes.uv.needsUpdate=!0,E.geometry.computeVertexNormals()}function A(E){let b=new zt,v=window.RIBBON_COLORS||["#d0101e","#f5a623"];if(E==="pl"){let S=y("#c8102e","Premier League","#ffffff");for(let w of[-1,1])[[.14,.035,.64,.04,0],[.175,0,.68,.07,1.7],[.21,-.03,.62,.1,3.1]].forEach(([P,F,U,H,N])=>{b.add(M([w*P,.735,F],{side:w,len:U,out:H,ph:N+(w>0?.6:0),tw0:w*.15,tw:.4,width:.05},S))})}else if(E==="facup"||E==="leaguecup"||E==="english"){let S=E==="facup"?[.21,.745]:E==="leaguecup"?[.3,.6]:[.27,.72],w=E==="leaguecup"?.52:.64;for(let P of[-1,1])[[0,.025,0],[.035,-.02,2.2]].forEach(([F,U,H],N)=>{let K=y(v[(N+(P>0?1:0))%2]);b.add(M([P*(S[0]+F),S[1],U],{side:P,len:w-N*.04,out:.07+N*.03,ph:H+(P>0?.9:0),tw0:P*.2,tw:.6,width:.036},K))})}return b}return{BUILD:x,ribbonsFor:A,shapeRibbon:C,materials:{silver:i,silverSatin:t,gold:e,ebony:n}}}var hu={glow:"#7a8cff",panel:"#160d55",mid:"#33208f",deep:"#05031a",ink:"#c9d4ff",violet:"#a23cff",blue:"#2e48ff"},Ae={};function ec(i,t=1){i.add(new Jn(8950015,.35*t));let e=new je(wt("#b04bff"),5*t,0,0);e.position.set(1600,500,900),i.add(e);let n=new je(wt("#3b5bff"),5*t,0,0);n.position.set(-1600,300,900),i.add(n);let s=new Vi(16777215,1.1*t);s.position.set(0,2,3),i.add(s)}var uu=new Map;function ba(i,t,e,n=-6e3,s=3){let r=t+"|"+e+"|"+s,a=uu.get(r)||u_(t,e,s);uu.set(r,a);let o=_t(a);return o.scale.set(-n*2.4,-n*1.35,1),o.position.z=n,i.add(o),o}function u_(i,t,e){let n=Wt(1024,576),s=n.getContext("2d"),r=Se(e),a=s.createRadialGradient(512,330,20,512,300,700);a.addColorStop(0,t),a.addColorStop(1,i),s.fillStyle=a,s.fillRect(0,0,1024,576);for(let o=0;o<120;o++)s.globalAlpha=.15+r()*.5,s.fillStyle="#d8dcff",s.fillRect(r()*1024,r()*576,1.2,1.2);return s.globalAlpha=1,n}var dr=null;function d_(){if(dr)return dr;let i=1024,t=512,e=Wt(i,t),n=e.getContext("2d");n.fillStyle="#f2f4fb",n.fillRect(0,0,i,t);let s=(a,o,l,c,h,u)=>{n.beginPath();for(let d=0;d<10;d++){let p=h+d*Math.PI/5,g=d%2?l*.44:l;n.lineTo(a+Math.cos(p)*g*c,o+Math.sin(p)*g)}n.closePath(),n.fillStyle=u,n.fill()},r=(a,o,l,c)=>{let h=t/2-a/90*(t/2),u=1/Math.max(.25,Math.cos(a*Math.PI/180));for(let d=0;d<o;d++){let p=(d+c)/o*i;for(let g of[0,i,-i])s(p+g,h,l,u,-Math.PI/2,"#16207a"),s(p+g,h,l*.62,u,-Math.PI/2,"#3550e6"),s(p+g,h,l*.3,u,-Math.PI/2,"#8aa0ff")}};r(0,8,58,0),r(48,6,46,.5),r(-48,6,46,.25),n.strokeStyle="rgba(30,40,110,0.25)",n.lineWidth=2;for(let a=0;a<16;a++)n.beginPath(),n.moveTo(a/16*i,0),n.lineTo(a/16*i+30,t),n.stroke();return dr=jn(e),dr.wrapS=mi,dr}function du(i=512){let t=Wt(i,i),e=t.getContext("2d"),n=i/2,s=i/2+i*.03,r=i*.36,a=[];for(let o=0;o<10;o++){let l=-Math.PI/2+o*Math.PI/5,c=o%2?r*.43:r;a.push([n+Math.cos(l)*c,s+Math.sin(l)*c])}e.save(),e.shadowColor="rgba(170,190,255,1)",e.shadowBlur=i*.09,e.beginPath(),a.forEach(([o,l],c)=>e[c?"lineTo":"moveTo"](o,l)),e.closePath(),e.fillStyle="#ffffff",e.fill(),e.restore();for(let o=0;o<10;o++){let l=a[o],c=a[(o+1)%10];e.beginPath(),e.moveTo(n,s),e.lineTo(l[0],l[1]),e.lineTo(c[0],c[1]),e.closePath(),e.fillStyle=o%2?"rgba(205,214,245,0.9)":"rgba(255,255,255,1)",e.fill()}return t}function fu(i=256){let t=Wt(i,i*1.3),e=t.getContext("2d"),n=i/100;e.save(),e.shadowColor="rgba(200,210,255,1)",e.shadowBlur=16*n,e.fillStyle="#ffffff",e.strokeStyle="#ffffff",e.beginPath(),e.moveTo(30*n,18*n),e.lineTo(70*n,18*n),e.bezierCurveTo(70*n,52*n,62*n,70*n,54*n,76*n),e.lineTo(54*n,96*n),e.lineTo(64*n,104*n),e.lineTo(64*n,118*n),e.lineTo(36*n,118*n),e.lineTo(36*n,104*n),e.lineTo(46*n,96*n),e.lineTo(46*n,76*n),e.bezierCurveTo(38*n,70*n,30*n,52*n,30*n,18*n),e.closePath(),e.fill(),e.lineWidth=7*n;for(let s of[-1,1]){e.beginPath();let r=50+s*18;e.moveTo(r*n,22*n),e.bezierCurveTo((50+s*46)*n,4*n,(50+s*50)*n,46*n,(50+s*16)*n,62*n),e.stroke()}return e.restore(),t}function f_(i=1){let t=100*i,e=`700 ${Math.round(92*i)}px Outfit, sans-serif`,n=Wt(4,4).getContext("2d");n.font=e;let s=n.measureText("whitebrook road").width,r=n.measureText("sports").width+48*i,a=40*i,o=30*i,l=a*2+t+o+s+o+r,c=a*2+t*1.25,h=Wt(l,c),u=h.getContext("2d"),d=c/2,p=a,g=d-t/2,x=t*.22,m=(C,A,E,b,v)=>{u.beginPath(),u.moveTo(C+v,A),u.arcTo(C+E,A,C+E,A+b,v),u.arcTo(C+E,A+b,C,A+b,v),u.arcTo(C,A+b,C,A,v),u.arcTo(C,A,C+E,A,v),u.closePath()};u.save(),u.shadowColor="rgba(160,80,255,0.9)",u.shadowBlur=30*i;let f=u.createLinearGradient(p,g,p+t,g+t);f.addColorStop(0,"#8b3dff"),f.addColorStop(1,"#ff5d8f"),m(p,g,t,t,x),u.fillStyle=f,u.fill(),u.restore();let y=t/100;u.beginPath(),u.moveTo(p+50*y,g+20*y),u.lineTo(p+80*y,g+45*y),u.lineTo(p+80*y,g+80*y),u.lineTo(p+20*y,g+80*y),u.lineTo(p+20*y,g+45*y),u.closePath(),u.fillStyle="#ffffff",u.fill(),u.beginPath(),u.moveTo(p+43*y,g+50*y),u.lineTo(p+43*y,g+72*y),u.lineTo(p+62*y,g+61*y),u.closePath(),u.fillStyle="#b44bff",u.fill(),u.font=e,u.textBaseline="middle",u.fillStyle="#ffffff",u.save(),u.shadowColor="rgba(150,90,255,0.8)",u.shadowBlur=24*i,u.fillText("whitebrook road",p+t+o,d+4*i),u.restore(),u.fillText("whitebrook road",p+t+o,d+4*i);let _=p+t+o+s+o,M=t*.98;return u.save(),u.shadowColor="rgba(255,40,60,0.7)",u.shadowBlur=24*i,m(_,d-M/2,r,M,16*i),u.fillStyle="#d8101f",u.fill(),u.restore(),u.fillStyle="#ffffff",u.fillText("sports",_+24*i,d+2*i),h}function pu(i){return new Ct(Ae.sphere||(Ae.sphere=new Ts(1,48,32)),new Ve({map:d_(),roughness:.36,metalness:.05,envMap:i,envMapIntensity:.9}))}function mu(i,t,e="#17122e"){let n=new _i(t,1),s=Se(i),r=n.attributes.position,a=new L,o=new Map;for(let l=0;l<r.count;l++){a.fromBufferAttribute(r,l);let c=a.x.toFixed(2)+","+a.y.toFixed(2)+","+a.z.toFixed(2);o.has(c)||o.set(c,.72+s()*.5),a.multiplyScalar(o.get(c)),r.setXYZ(l,a.x,a.y,a.z)}return n.computeVertexNormals(),new Ct(n,new Ve({color:wt(e),roughness:.92,metalness:.05,flatShading:!0}))}function p_(i,t,e,n="#93a8ff"){let s=new Oi(i.map(r=>new L(...r)),!1,"catmullrom",.5);return new Ct(new As(s,140,t,16,!1),new Ve({color:wt(n),metalness:1,roughness:.16,envMap:e,envMapIntensity:1.8}))}Ae.open=(i,t)=>{let e=new me,n=we();e.background=wt(hu.deep),e.fog=new Qe(wt(hu.deep),3500,14e3),ec(e),ba(e,"#04021a","#2a1580",-9e3,4);let s=[[[-2600,-900,-1400],[-900,-300,-500],[-300,500,-900],[400,900,-2200],[1700,600,-3e3]],[[2400,-1e3,-600],[900,-600,200],[300,100,500],[-700,700,300],[-2200,1e3,-900]],[[-1800,1400,-2600],[-600,700,-1500],[700,-200,-1400],[1500,-900,-1900],[2600,-1200,-2800]],[[-3e3,200,-3500],[-1200,-500,-2600],[0,-700,-2200],[1400,-200,-2700],[3e3,700,-3600]]].map((l,c)=>{let h=p_(l,c%2?34:46,i,c%2?"#9fb1ff":"#7f8fe8");return e.add(h),h}),r=Ue("#7b4dff",2600,.6);r.position.z=-1200,e.add(r);let a=_t(du(300));a.position.set(0,120,0),e.add(a);let o=null;if(t.mark){let l=yi(t.mark,760);o=_t(l),o.position.set(0,-110,0),e.add(o)}else{let l=_t(oe("CHAMPIONS",{size:120,weight:700,family:"Barlow7",spacing:22,fill:"#ffffff"}));l.position.set(0,-110,0),e.add(l),o=l}return{scene:e,camera:n,update(l,c){let h=l/c,u=J(-.5,.35,_n(h));Te(n,Math.sin(u)*Ge*1.1,J(260,-60,h),Math.cos(u)*Ge*1.1,0,0,0,J(.05,-.03,h)),Ht(a,ue(.3,1,l)),Ht(o,ue(.6,1.4,l)),a.rotation.z=Math.sin(l*.8)*.05,a.quaternion.copy(n.quaternion),o.quaternion.copy(n.quaternion),s.forEach((d,p)=>{d.rotation.z=Math.sin(l*.3+p)*.06})}}};Ae.star=(i,t)=>{let e=new me,n=we();e.background=wt("#0a0630"),ec(e,1.2),ba(e,"#07052a","#4a1fa8",-7e3,9);let s=vi(430,"#b9c6ff","#b9c6ff",34);s.position.z=-60,e.add(s);let r=vi(470,"#7d6bff","#7d6bff",16);r.position.z=-90,e.add(r);let a=_t(du(620));e.add(a);let o=Se(21),l=[],c=[[-760,-300,300,330],[-430,-40,-200,120],[-260,-210,200,70],[430,-60,-300,110],[620,120,-700,80],[300,-260,120,64],[-560,260,-900,70],[760,-330,-300,70],[140,300,-1200,60],[-120,-380,-500,54],[900,300,-1400,70],[-980,60,-1500,64]];for(let[h,u,d,p]of t.noBig?c.slice(1):c){let g=pu(i);g.scale.setScalar(p),g.position.set(h,u,d),g.userData.spin=(o()-.5)*1.4,g.userData.y0=u,g.userData.ph=o()*6,g.rotation.set(o()*3,o()*3,0),e.add(g),l.push(g)}return{scene:e,camera:n,update(h,u){let d=h/u,p=va(St(u-.6,u,h));s.userData.grow(ee(St(0,.8,h))),r.userData.grow(ee(St(.15,.9,h))),a.scale.set(620*J(.7,1,Gl(St(.1,.8,h))),620*J(.7,1,Gl(St(.1,.8,h))),1),l.forEach(x=>{x.rotation.y=h*x.userData.spin,x.position.y=x.userData.y0+Math.sin(h*.9+x.userData.ph)*18});let g=Ge*J(1.08,.98,ee(d))-p*Ge*.93;Te(n,J(80,-40,d)*(1-p),J(-30,20,d)*(1-p),g,0,0,-400,J(.03,-.02,d))}}};function m_(i,t,e){let n=new Bi,s=i/2,r=t*.42;return n.moveTo(-s,0),n.lineTo(-s,r),n.quadraticCurveTo(-s,t*.9,0,t),n.quadraticCurveTo(s,t*.9,s,r),n.lineTo(s,0),n.lineTo(s-e,0),n.lineTo(s-e,r),n.quadraticCurveTo(s-e,t*.9-e*1.1,0,t-e*1.7),n.quadraticCurveTo(-s+e,t*.9-e*1.1,-s+e,r),n.lineTo(-s+e,0),n.closePath(),new ws(n,{depth:34,bevelEnabled:!1,curveSegments:18})}Ae.arches=(i,t)=>{let e=new me,n=we();e.background=wt("#03021a"),e.fog=new Qe(wt("#03021a"),1200,8200),ec(e,.8);let s=m_(2300,2600,70),r=new Ve({color:wt("#1b2a86"),metalness:.55,roughness:.34,envMap:i,emissive:wt("#0b0f3a")});for(let c=0;c<26;c++){let h=new Ct(s,r);h.position.set(0,-1500,-c*320),e.add(h)}let a=(c,h,u,d,p,g,x,m)=>{let f=Wt(256,256),y=f.getContext("2d"),_=y.createLinearGradient(0,0,256,256);_.addColorStop(0,m),_.addColorStop(1,"#2a0a5e"),y.fillStyle=_,y.fillRect(0,0,256,256);let M=_t(f);return M.scale.set(g,x,1),M.position.set(c,h,u),M.rotation.set(0,d,p),e.add(M),M};a(980,-260,300,-.9,.5,900,1400,"#9b3dff"),a(-1020,380,100,.8,-.4,700,1100,"#2f3fff");let o=new zt;o.position.set(0,-60,-3e3),e.add(o),o.add(Ue("#9aa8ff",1300,.55));let l;return t.crest?(l=Ln(t.crest,10,4),l.scale.setScalar(.62*(t.crestScale||1))):l=_t(fu(220)),o.add(l),{scene:e,camera:n,update(c,h){let u=c/h;Te(n,Math.sin(c*.7)*40,J(40,-20,u),J(1800,600,ee(u)),0,-60,-3e3,J(-.06,.04,u)),l.userData.face?(l.rotation.y=Math.sin(c*1.2)*.3,l.userData.face.userData.sheen.sweep.value=J(-.5,1.5,St(.3,1.6,c))):Ht(l,ue(.1,.6,c))}}};function g_(i){let t=new Je(6400,3800,240,1),e=t.attributes.position,n=new Float32Array(e.count);for(let a=0;a<e.count;a++)n[a]=e.getX(a);let s=new Ve({color:wt("#2a3bc4"),roughness:.5,metalness:.2}),r=new Ct(t,s);return r.position.z=-900,i.add(r),r.userData.wave=a=>{for(let o=0;o<e.count;o++){let l=n[o];e.setZ(o,90*Math.sin(l*.0105+a*.7)+40*Math.sin(l*.027-a*1.1)+14*Math.sin(l*.061+a*.4))}e.needsUpdate=!0,t.computeVertexNormals()},r}Ae.drapes=(i,t)=>{let e=new me,n=we();e.background=wt("#05041c"),e.add(new Jn(5923071,.25));let s=new je(wt("#b54dff"),9,0,0);s.position.set(1400,200,300),e.add(s);let r=new je(wt("#3f63ff"),8,0,0);r.position.set(-1300,-100,200),e.add(r);let a=new pa(16777215,6,0,.5,.6,0);a.position.set(0,1600,800),a.target.position.set(0,0,-900),e.add(a),e.add(a.target);let o=g_(e),l=t.team,c=Ue(l&&l.lk?l.lk.glow:"#8f7bff",1500,.55);c.position.z=-200,e.add(c);let h=null;l&&l.chrome&&(h=Ln(l.chrome,12,4),h.scale.setScalar(.92*(l.crestScale||1)),h.position.y=40,e.add(h));let u=l?_t(oe(l.name,{size:46,weight:700,family:"Barlow7",spacing:24,fill:"#ffffff"})):null;return u&&(u.position.set(0,-390,40),e.add(u)),{scene:e,camera:n,update(d,p){let g=d/p;if(o.userData.wave(d+(t.seed||0)),h){let x=tn(St(0,.7,d));h.scale.setScalar(.92*(l.crestScale||1)*J(.75,1,x)),h.rotation.y=J(1.1,0,x)+Math.sin(d*1.3)*.06,h.userData.face.userData.sheen.sweep.value=J(-.5,1.5,St(.4,1.6,d))}u&&Ht(u,ue(.4,.8,d)),Te(n,J(-120,80,g),0,Ge*J(1.05,.96,ee(g)),0,0,0,0)}}};function __(i){let e=Wt(1024,1024),n=e.getContext("2d"),s=Se(i),r=n.createRadialGradient(1024/2,1024/2,1024*.05,1024/2,1024/2,1024/2);r.addColorStop(0,"#d455ff"),r.addColorStop(.55,"#8b2cf0"),r.addColorStop(.92,"#4b1bb0"),r.addColorStop(1,"rgba(60,20,160,0)"),n.fillStyle=r,n.beginPath(),n.arc(1024/2,1024/2,1024/2,0,Math.PI*2),n.fill(),n.globalCompositeOperation="lighter";for(let a=0;a<9;a++){let o=-Math.PI/2+(s()-.5)*1.6,l=1024*(.35+s()*.2);n.strokeStyle=`rgba(190,170,255,${.08+s()*.12})`,n.lineWidth=2+s()*6,n.beginPath(),n.moveTo(1024/2,1024*.62),n.lineTo(1024/2+Math.cos(o)*l,1024*.62+Math.sin(o)*l),n.stroke()}for(let a=0;a<160;a++)n.fillStyle=`rgba(230,220,255,${s()*.5})`,n.fillRect(s()*1024,1024*.55+s()*1024*.12,3,2);return e}Ae.player=(i,t)=>{let e=new me,n=we(),s=t.player,r=t.team;e.background=wt("#070524"),ba(e,"#05031c","#1b1170",-5e3,13);let a=_t(__(t.seed||5));a.scale.set(1250,1250,1),a.position.set(260,30,-400),e.add(a);let o=new Ct(new rr(700,708,128),new be({color:wt("#b7a2ff"),transparent:!0,opacity:.6,toneMapped:!1}));o.position.set(260,30,-420),e.add(o);let l=new Ct(new rr(800,804,128),new be({color:wt("#7f8cff"),transparent:!0,opacity:.35,toneMapped:!1}));l.position.set(260,30,-460),e.add(l);let c=new zt;if(e.add(c),s&&s.lit){let y=s.lit.face?760:1180,_=_t(s.lit),M=y/s.lit.h;_.scale.multiplyScalar(M),_.position.set(s.lit.face?-40:0,s.lit.face?30:-540+y/2-40,0),c.add(_)}c.position.x=300;let h=new zt;h.position.set(-820,-70,40),e.add(h);let u=jl(s?s.last:r.name,{size:250,sy:1.08,fill:Wi,glow:{blur:18,c:"rgba(20,0,60,0.6)",a:1}}),d=Math.min(1,820/Math.max(1,u.userData.w));u.scale.setScalar(d),h.add(u);let p=s&&s.first?_t(oe(s.first,{size:64,weight:700,family:"Barlow7",spacing:3,fill:"#c9b8ff"})):null;p&&(p.position.set(p.userData.w/2-4,250*1.08*.72*d+48,0),h.add(p));let g=s?`${s.num||""}${s.num&&s.pos?"  ":""}${s.pos||""}`.trim():"",x=g?_t(oe(g,{size:66,weight:800,fill:"#9fb4ff"})):null;x&&(x.position.set(x.userData.w/2+6,-62,0),h.add(x));let m=null;r&&r.plain&&(m=_t(r.plain),m.scale.multiplyScalar(110/m.userData.h),m.position.set(-760,330,30),e.add(m));let f=u.userData.letters.map((y,_)=>_);return{scene:e,camera:n,update(y,_){let M=y/_;a.scale.setScalar(1250*J(.85,1,ee(St(0,.8,y)))),a.scale.z=1,o.rotation.z=y*.2,l.rotation.z=-y*.15,c.position.x=300+(1-tn(St(0,.6,y)))*300,Ht(c,St(0,.2,y)),u.userData.letters.forEach((C,A)=>{let E=.25+f[A]*.05,b=St(E,E+.1,y);Ht(C,b),C.position.y=(1-ee(b))*-40+(C.userData.y0==null?C.userData.y0=C.position.y:C.userData.y0)}),p&&Ht(p,ue(.2,.5,y)),x&&Ht(x,ue(.7,1,y)),m&&Ht(m,ue(.3,.7,y)),Te(n,J(50,-30,M),0,Ge*J(1.04,.96,M),0,0,0,0)}}};Ae.versus=(i,t)=>{let e=Ae.star(i,{noBig:!0}),{home:n,away:s}=t,r=[n,s].map((l,c)=>{let h=c?1:-1;if(!l.chrome)return null;let u=Ln(l.chrome,12,4);u.scale.setScalar(.8*(l.crestScale||1)),u.position.set(h*600,0,140);let d=Ue(l.lk.glow,1200,.5);return d.position.set(h*640,0,0),e.scene.add(d),e.scene.add(u),{cr:u,sd:h}}).filter(Boolean),a=_t(oe("v",{size:120,weight:700,family:"Barlow7",fill:"#0b0a2a"}));a.position.set(0,-30,30),e.scene.add(a);let o=e.update;return{scene:e.scene,camera:e.camera,update(l,c){o(l,c+.6),r.forEach(({cr:h,sd:u})=>{let d=tn(St(.1,.8,l));h.position.x=u*J(1500,600,d),h.rotation.y=-u*J(1.2,.25,d),h.userData.face.userData.sheen.sweep.value=J(-.5,1.5,St(.8,2,l))}),Ht(a,ue(.7,1,l))}}};Ae.cave=(i,t)=>{let e=new me,n=we();e.background=wt("#04031a"),e.fog=new Qe(wt("#04031a"),600,5200),e.add(new Jn(8023295,.75));let s=new je(wt("#b05bff"),14,0,0);s.position.set(700,2600,-500),e.add(s);let r=new je(wt("#4c6bff"),12,0,0);r.position.set(-900,900,200),e.add(r);let a=new je(wt("#e0d4ff"),10,0,0);a.position.set(0,4200,-1400),e.add(a);let o=Se(31);for(let c=0;c<26;c++){let h=mu(100+c,220+o()*460,c%3?"#2a2350":"#3a2f6e"),u=o()*Math.PI*2,d=650+o()*900;h.position.set(Math.cos(u)*d,-700+c/26*4e3+o()*300,-700+Math.sin(u)*d*.5),h.rotation.set(o()*3,o()*3,o()*3),e.add(h)}let l=_t(ti(1024,128),{add:!0,color:"#9c7bff",opacity:.5});return l.scale.set(5200,900,1),l.rotation.z=Math.PI/2-.2,l.position.set(200,2600,-1600),l.userData.a=.5,e.add(l),{scene:e,camera:n,update(c,h){let u=_n(c/h);Te(n,J(-200,120,u),J(-500,2300,u),1500,0,J(600,3400,u),-1400,J(.12,-.05,u)),Ht(l,.3+.4*St(.3,1,c/h))}}};var tc=null;Ae.trophy=(i,t)=>{let e=new me,n=we();e.background=wt("#060421"),e.environment=i,e.fog=new Qe(wt("#060421"),3e3,11e3),e.add(new Jn(8024319,.35));let s=new Vi(16777215,2.2);s.position.set(-2,3,3),e.add(s);let r=new je(wt("#b25bff"),8,0,0);r.position.set(900,600,-500),e.add(r);let a=new je(wt("#4e6bff"),7,0,0);a.position.set(-900,400,-400),e.add(a),ba(e,"#05031a","#2a1488",-7e3,17);let o=new Ve({color:wt("#1d2a8a"),metalness:.4,roughness:.4,envMap:i,emissive:wt("#0a0d35")});for(let[m,f,y]of[[-1600,-1600,300],[-950,-2e3,220],[950,-2e3,220],[1600,-1600,300],[-2400,-1200,380],[2400,-1200,380]]){let _=new Ct(new on(y,4200,y),o);_.position.set(m,900,f),e.add(_)}let l=new Ct(new Je(2e4,2e4),new Ve({color:wt("#0a0828"),metalness:.7,roughness:.35,envMap:i}));l.rotation.x=-Math.PI/2,l.position.y=-620,e.add(l);let c=new Ct(new xi(820,5,8,160,Math.PI*1.25),new be({color:wt("#c9a2ff"),toneMapped:!1}));c.position.set(0,120,-700),c.rotation.z=Math.PI*.6,e.add(c);let h=mu(7,420,"#1c1838");h.scale.set(1.2,.55,.9),h.position.set(0,-470,-100),e.add(h);let u=new zt;u.position.set(0,-330,-100),e.add(u);try{tc=tc||cu();let m=tc.BUILD.ucl(),f=new wn().setFromObject(m),_=640/(f.max.y-f.min.y||1);m.scale.setScalar(_),m.position.y=-f.min.y*_,u.add(m)}catch{let f=_t(fu(300));f.position.y=330,u.add(f)}for(let[m,f,y]of[[-640,-40,70],[600,0,64],[-860,120,52],[820,80,56]]){let _=pu(i);_.scale.setScalar(y),_.position.set(m,-620+y,f),_.rotation.set(1,m,.4),e.add(_)}let d=[];for(let m=0;m<3;m++){let f=_t(ti(1024,128),{add:!0,color:"#a98bff",opacity:.25});f.scale.set(4200,700,1),f.rotation.z=Math.PI/2+(m-1)*.25,f.position.set((m-1)*500,1500,-1800),f.userData.a=.25,e.add(f),d.push(f)}let p=_t(f_(.95));p.position.set(0,-335,420),e.add(p);let g=t.fixture?_t(oe(t.fixture,{size:84,sy:1.05,fill:Wi,glow:{blur:18,c:"rgba(120,80,255,0.9)",a:1}})):null;g&&(g.scale.multiplyScalar(Math.min(1,1300/g.userData.w)),g.position.set(0,-442,420),e.add(g));let x=t.line2?_t(oe(t.line2,{size:30,weight:700,family:"Barlow7",spacing:8,fill:"#c9b8ff"})):null;return x&&(x.position.set(0,-506,420),e.add(x)),{scene:e,camera:n,update(m,f){let y=m/f;u.rotation.y=J(-.9,.5,ee(y)),c.rotation.z=Math.PI*.6+m*.18,d.forEach((A,E)=>Ht(A,.6+.4*Math.sin(m*1.1+E)));let _=St(3,3.8,m);Ht(p,ue(0,1,_)),p.position.y=-335-(1-ee(_))*40,g&&Ht(g,ue(4,4.7,m)),x&&Ht(x,ue(4.4,5,m));let M=_n(St(0,3.2,m)),C=_n(St(2.6,4.6,m));Te(n,J(-300,0,M),J(-380,60,M)-C*60,J(1300,1700,M)+C*520,0,J(-200,40,M)-C*150,-100,J(.05,0,M))}}};var gu={"349":["#DA291C","#111111","#FFFFFF"],"359":["#F0161E","#5E0004","#F6C342"],"362":["#8A1A44","#33041A","#95BFE5"],"337":["#E30613","#F4F4F4","#FFD400"],"331":["#1478E0","#00205B","#FFFFFF"],"363":["#0A5BC4","#021C4A","#F5C518"],"388":["#6CC5EE","#1B5F8C","#FFFFFF"],"384":["#1B458F","#C4122E","#FFFFFF"],"368":["#1F4DC2","#001A5E","#FFFFFF"],"370":["#2E2E2E","#050505","#CC0000"],"306":["#F5A12D","#111111","#111111"],"373":["#1E5BC6","#062A6B","#DE2C37"],"357":["#1D428A","#0B1E46","#FFCD00"],"364":["#D00027","#5E0010","#F6EB61"],"382":["#8FC8EE","#2C6AA0","#F2C230"],"360":["#E2001A","#4A0007","#FBE122"],"361":["#111111","#F2F2F2","#41B6E6"],"393":["#E1001A","#6B0010","#FFFFFF"],"366":["#EB172B","#FFFFFF","#111111"],"367":["#1E3A8A","#0A1430","#FFFFFF"],"96":["#0A3FAF","#FFFFFF","#FFFFFF"],"93":["#EE2523","#FFFFFF","#111111"],"1068":["#CB3524","#FFFFFF","#272E61"],"83":["#004D98","#A50044","#EDBB00"],"85":["#8AC3EE","#1A5E9C","#E5254E"],"90":["#1B57C2","#FFFFFF","#FFFFFF"],"3751":["#19A04B","#0A4D22","#FFFFFF"],"88":["#1F6FD1","#FFFFFF","#FFFFFF"],"2922":["#1E6FD9","#062E66","#FFFFFF"],"1538":["#0B4FA3","#B5121B","#FFD400"],"99":["#6EC1E4","#FFFFFF","#FFFFFF"],"97":["#D91A21","#5E0A0E","#0A2B6B"],"87":["#1E8F3E","#FFFFFF","#FFFFFF"],"101":["#F4F4F4","#E30613","#E30613"],"244":["#0BB363","#FFFFFF","#FFFFFF"],"86":["#FFFFFF","#C9B37E","#3E1F7A"],"89":["#0067B1","#FFFFFF","#FFFFFF"],"243":["#FFFFFF","#E3E3E3","#C8102E"],"94":["#3A3A3A","#050505","#EE7F00"],"102":["#FFE667","#E3B800","#005187"],"103":["#E2001A","#111111","#FFFFFF"],"104":["#A6192E","#4D0A14","#F0BC42"],"105":["#1E71B8","#111111","#F2C230"],"107":["#A21C26","#1A2F48","#FFFFFF"],"2925":["#A6192E","#002350","#FFFFFF"],"2572":["#2F6BD6","#0C2A66","#FFFFFF"],"109":["#6B2FA8","#2A0D4D","#FFFFFF"],"4057":["#FFD84D","#E0A800","#1A3D8F"],"3263":["#A6192E","#0A2140","#F2C230"],"110":["#0068A8","#111111","#F2C230"],"111":["#111111","#FFFFFF","#FFFFFF"],"112":["#87D8F7","#2A7DB5","#FFFFFF"],"113":["#FFE500","#D2152B","#111111"],"4007":["#E2001A","#FFFFFF","#FFFFFF"],"114":["#12A0D7","#004B87","#FFFFFF"],"115":["#1A3D8F","#FFD400","#FFD400"],"3997":["#00A752","#111111","#00A752"],"239":["#8A1E03","#3D0D01","#FFFFFF"],"118":["#FFFFFF","#111111","#FFFFFF"],"17530":["#2F3A33","#050505","#F37A1F"],"598":["#D4011D","#5C0010","#FFD400"],"131":["#E32221","#3A0505","#111111"],"132":["#DC052D","#6B0016","#0066B2"],"124":["#FDE100","#E0B000","#111111"],"268":["#1A9E4B","#0B4D24","#FFFFFF"],"125":["#E1000F","#1A0003","#FFFFFF"],"3841":["#BA3733","#46793A","#FFFFFF"],"122":["#ED1C24","#6B0A0F","#FFFFFF"],"127":["#0A3EA8","#04153D","#FFFFFF"],"2950":["#ED1C24","#6B0A0F","#FFFFFF"],"11420":["#FFFFFF","#D5D9E0","#DD0741"],"126":["#D2122E","#1A0306","#FFFFFF"],"3307":["#0A50A1","#04173D","#FFFFFF"],"10388":["#2A2A2A","#000000","#FFFFFF"],"133":["#004D9D","#00163D","#FFFFFF"],"7911":["#1961B5","#062A5A","#FFFFFF"],"134":["#FFFFFF","#E32219","#E32219"],"137":["#1D9053","#0A4426","#FFFFFF"],"172":["#FFFFFF","#D6E2F2","#0F3D8F"],"174":["#E51B22","#FFFFFF","#FFFFFF"],"7868":["#111111","#FFFFFF","#FFFFFF"],"6997":["#E2001A","#5E000B","#FFFFFF"],"3236":["#8CC8EC","#0B2A63","#FFFFFF"],"2697":["#D62B11","#5E1205","#FFD400"],"175":["#E51B22","#FFD400","#111111"],"166":["#E01E13","#0B1F4D","#FFFFFF"],"273":["#F58113","#8A3D00","#111111"],"167":["#FFFFFF","#D5DDEE","#DA0812"],"176":["#8FD1F0","#1E7FB8","#FFFFFF"],"2502":["#E2001A","#111111","#FFFFFF"],"6851":["#1B2A4A","#070D1C","#8FC8EE"],"160":["#004170","#001A33","#DA291C"],"169":["#E2001A","#111111","#FFFFFF"],"180":["#0A5FD1","#032A6B","#FFFFFF"],"179":["#6A1B9A","#2A0846","#FFFFFF"],"170":["#1F5FD1","#072A66","#FFFFFF"]};var pr=48,_u=38,Fs=pr,xu=[],y_=[["arsenal","UP THE ARSENAL!"],["aston villa","UP THE VILLA!"],["bournemouth","UP THE CHERRIES!"],["brentford","COME ON YOU BEES!"],["brighton","UP THE ALBION!"],["chelsea","COME ON CHELSEA!"],["crystal palace","COME ON PALACE!"],["everton","COME ON YOU BLUES!"],["fulham","COME ON FULHAM!"],["ipswich","COME ON YOU BLUES!"],["leeds","MARCHING ON TOGETHER!"],["liverpool","COME ON YOU REDS!"],["manchester city","COME ON CITY!"],["man city","COME ON CITY!"],["manchester united","COME ON UNITED!"],["man utd","COME ON UNITED!"],["newcastle","HOWAY THE LADS!"],["nottingham forest","COME ON YOU REDS!"],["forest","COME ON YOU REDS!"],["sunderland","HA'WAY THE LADS!"],["tottenham","COME ON YOU SPURS!"],["spurs","COME ON YOU SPURS!"],["west ham","COME ON YOU IRONS!"],["wolverhampton","UP THE WOLVES!"],["wolves","UP THE WOLVES!"],["coventry","PLAY UP SKY BLUES!"],["hull","UP THE TIGERS!"],["burnley","UP THE CLARETS!"],["leicester","COME ON LEICESTER!"],["southampton","OH WHEN THE SAINTS!"],["celtic","COME ON THE HOOPS!"],["rangers","WE ARE THE PEOPLE!"],["real madrid","\xA1HALA MADRID!"],["barcelona","VISCA EL BAR\xC7A!"],["atletico","\xA1A\xDAPA ATLETI!"],["atl\xE9tico","\xA1A\xDAPA ATLETI!"],["athletic","AUPA ATHLETIC!"],["sevilla","\xA1VAMOS MI SEVILLA!"],["betis","\xA1VIVA EL BETIS!"],["valencia","AMUNT VAL\xC8NCIA!"],["bayern","MIA SAN MIA!"],["dortmund","ECHTE LIEBE!"],["paris saint-germain","ICI C'EST PARIS!"],["psg","ICI C'EST PARIS!"],["marseille","DROIT AU BUT!"],["juventus","FINO ALLA FINE!"],["inter","FORZA INTER!"],["milan","FORZA MILAN!"],["napoli","FORZA NAPOLI!"],["roma","FORZA ROMA!"],["lazio","FORZA LAZIO!"]],v_={laliga:"\xA1VAMOS %!",seriea:"FORZA %!",bundesliga:"AUF GEHT'S %!",ligue1:"ALLEZ %!"};function yu(i,t){if(i.chant)return String(i.chant).toUpperCase();let e=((i.full||"")+" "+(i.name||"")).toLowerCase();for(let[n,s]of y_)if(e.includes(n))return s;return(v_[t]||"COME ON %!").replace("%",String(i.name||"").toUpperCase())}function vu(i){let t=(i.home.players||[]).filter(h=>h.lit),e=(i.away.players||[]).filter(h=>h.lit),n=(i.others||[]).filter(h=>h.img),s=(i.duels||[]).filter(h=>h[0].lit&&h[1].lit).map(h=>h.map(u=>({player:u,team:u.teamRef})));for(let h=2;s.length<2&&h<Math.min(t.length,e.length);h++)s.push([{player:t[h],team:i.home},{player:e[h],team:i.away}]);let r=[],a=(h,u,d,p={})=>r.push({kind:h,t0:u,t1:d,o:p});a("comet",0,3),a("tunnel",3,4,{portal:!0,lk:{glow:"#37d6ff",panel:"#0a2448",mid:"#13386a",deep:"#010812",ink:"#bdefff"},seed:2,roll0:.1,roll1:-.5,twist:.02}),a("lockup",4,7.5,{});let o=(h,u)=>{let d=i[h],p=h==="home"?t:e,g=h==="home"?1:-1;a("city",u,u+1.5,{team:d,side:g,seed:g>0?3:13}),a("card",u+1.5,u+4.5,{team:d,player:p[0]||null,seed:g>0?5:15}),a("diamond",u+4.5,u+6,{team:d}),a("tunnel",u+6,u+7.5,{team:d,seed:g>0?6:16}),a("chant",u+7.5,u+9.5,{team:d,seed:g>0?7:17})};o("home",7.5),o("away",17);let l=h=>n.length?n[h%n.length]:h%2?i.away:i.home;[0,1,2].forEach(h=>a("tunnel",26.5+h*.5,27+h*.5,{team:l(h),short:!0,seed:30+h,roll0:h%2?.3:-.3,roll1:0})),[3,4,5,6].forEach((h,u)=>a("parade",28+u*.5,28.5+u*.5,{team:l(h),side:u%2?-1:1,seed:40+u})),s.length>=2?(a("duel",30,32,{pair:s[0],seed:51}),a("duel",32,34,{pair:s[1],seed:53})):[7,8,9,10,11,12,13,14].forEach((h,u)=>a("parade",30+u*.5,30.5+u*.5,{team:l(h),side:u%2?-1:1,seed:60+u})),a("faceoff",34,36.5,{}),a("abbr",36.5,38.5,{}),t.length&&e.length?a("duel",38.5,40.5,{pair:[{player:t[Math.min(1,t.length-1)],team:i.home},{player:e[Math.min(1,e.length-1)],team:i.away}],seed:57}):a("faceoff",38.5,40.5,{again:!0});let c=(h,u)=>h.length>=3?[h[2],h[0],h[3]||h[1]]:h.slice(0,3);return t.length?a("trio",40.5,41.75,{team:i.home,players:c(t,i.home),seed:61}):a("parade",40.5,41.75,{team:i.home,side:1,seed:61}),e.length?a("trio",41.75,43,{team:i.away,players:c(e,i.away),seed:62}):a("parade",41.75,43,{team:i.away,side:-1,seed:62}),a("lockup",43,pr,{end:!0}),r}var M_=[4,7.5,17,26.5,34,43],S_=[4,8,11,21,27,30],Mu=i=>i.style==="ucl"||i.comp==="ucl"&&i.style!=="network";function E_(i){let t=(i.home.players||[]).filter(r=>r.lit),e=(i.away.players||[]).filter(r=>r.lit),n=[],s=(r,a,o,l={})=>n.push({kind:r,t0:a,t1:o,o:l});return s("uclOpen",0,4),s("uclStar",4,8),s("uclArches",8,11,{}),s("uclDrapes",11,13,{team:i.home,seed:0}),t.length?s("uclPlayer",13,16,{team:i.home,player:t[0],seed:5}):s("uclArches",13,16,{team:i.home}),s("uclDrapes",16,18,{team:i.away,seed:3}),e.length?s("uclPlayer",18,21,{team:i.away,player:e[0],seed:7}):s("uclArches",18,21,{team:i.away}),s("uclVersus",21,24.5,{}),t.length>1&&e.length>1?s("duel",24.5,27,{pair:[{player:t[1],team:i.home},{player:e[1],team:i.away}],seed:71}):s("uclArches",24.5,27,{team:i.away}),s("uclCave",27,30,{}),s("uclTrophy",30,_u,{}),n}function nc(i){return i?new Promise(t=>{let e=new Image;e.onload=()=>t(e.naturalWidth>1?e:null),e.onerror=()=>t(null),e.src=i}):Promise.resolve(null)}function b_(i,t){let e=new me;e.add(new Ct(new on(10,10,10),new be({color:526864,side:ze})));let n=(a,o,l,c,h,u,d)=>{let p=new Ct(new Je(a,o),new be({color:new Gt(u).multiplyScalar(d),side:qe}));p.position.set(l,c,h),p.lookAt(0,0,0),e.add(p)};n(7,1.4,0,4.6,0,"#ffffff",7),n(1.3,7,-4.7,0,-1,t,5),n(1.3,7,4.7,.5,-.5,t,4),n(8,2.6,0,1.2,4.8,"#ffffff",3.2),n(8,1.2,0,-1.6,4.8,t,2.2),n(6,.35,0,-2.2,-4.8,"#ffffff",2),n(3,3,0,-4.8,0,t,1.2);let s=new Ms(i),r=s.fromScene(e,.02).texture;return s.dispose(),r}var We=null,Un=[],de=null,Su=!1,Ds=1,fr={LEN:pr,len:pr,init:w_,draw:R_,setRes:T_,plan:vu,chantFor:yu,uclStyle:Mu,canvas:null,renderer:null};window.MATCH3D=fr;async function w_(i){i=typeof i=="string"?JSON.parse(i):i;try{await Promise.all(["800 100px BarlowC","700 100px Barlow7","700 100px Outfit"].map(f=>document.fonts.load(f)))}catch{}let t=[i.home,i.away,...i.others||[]],e=[];(i.duels||[]).forEach(f=>f.forEach(y=>{y&&y.team&&(y.teamRef=y.team,e.push(y))}));let n=e.map(f=>f.teamRef);await Promise.all([...t,...n].map(async f=>{f.img=await nc(f.crest)}));let s=[...i.home.players||[],...i.away.players||[],...e];await Promise.all(s.map(async f=>{f.img=await nc(f.photo)}));let r=await nc(i.markImg||(/^(data:|file:|https?:|\/)/.test(String(i.mark||""))?i.mark:null));for(let f of[...t,...n]){let y=f.espn&&gu[f.espn];y&&!f.base&&(f.base=y[0],f.deep=y[1],f.accent=y[2]),f.lk=Wl(f),f.abbr=String(f.abbr||f.name||"").toUpperCase().slice(0,4),f.name=String(f.name||"").toUpperCase()}let a=[i.home,i.away];for(let f of[...t,...n]){if(!f.img)continue;let y=a.includes(f)?600:440;f.chrome||(f.chrome=Kl(f.img,y,f.lk.glow)),f.neon||(f.neon=qh(f.img,y*.9,f.lk.glow)),f.plain||(f.plain=yi(f.img,160));let _=f.img.naturalWidth/Math.max(1,f.img.naturalHeight);f.crestScale=_>1.15?.9:_<.8?1.06:1}for(let f of s){if(!f.img)continue;let y=i.home.players&&i.home.players.includes(f)?i.home:i.away.players&&i.away.players.includes(f)?i.away:f.teamRef,_=(y&&y.lk?y.lk:Wl({})).glow;f.lit=f.face?Yh(f.img,_):Zh(f.img,_),f.first=String(f.first||"").toUpperCase(),f.last=String(f.last||"").toUpperCase()}let o=fr.canvas||document.createElement("canvas");fr.canvas=o,We=new ra({canvas:o,antialias:i.aa!==!1,powerPreference:"high-performance",preserveDrawingBuffer:!!i.record,alpha:!1}),We.setPixelRatio(1),Ds=hn(i.res||1,.4,1),We.setSize(Math.round(en*Ds),Math.round(Rn*Ds),!1),We.toneMapping=Il,We.toneMappingExposure=1,We.outputColorSpace=Re,fr.renderer=We;let l=new Map,c=f=>(l.has(f)||l.set(f,b_(We,f)),l.get(f)),h=Mu(i);Un=h?E_(i):vu(i),Fs=h?_u:pr,xu=h?S_:M_,fr.len=Fs;let u=i.comp||"",d=!(parseInt(String(i.ko||"").split(":")[0],10)<17);for(let f of Un){let y=f.o,_=y.team,M=_?_.lk.glow:"#37d6ff",C=c(M),A;switch(f.kind){case"comet":A=xe.comet(C,{ko:i.ko});break;case"tunnel":A=xe.tunnel(C,{...y,lk:y.lk||_.lk,crest:_&&_.chrome?{chrome:_.chrome,neon:_.neon}:null,crestScale:_&&_.crestScale});break;case"lockup":A=xe.lockup(C,y.end?{title:d?"MATCH NIGHT":"MATCHDAY",mark:r,fixture:i.fixture,line2:[i.ground,i.ko?"KICK-OFF "+i.ko:""].filter(Boolean).join("   \xB7   ").toUpperCase()}:{title:d?"MATCH NIGHT":"MATCHDAY",mark:r});break;case"city":A=xe.city(C,{lk:_.lk,name:_.name,side:y.side,seed:y.seed});break;case"card":A=xe.card(C,{lk:_.lk,name:_.name,crest:_.chrome||Kl(Wt(4,4),10,_.lk.glow),crestScale:_.crestScale,player:y.player,seed:y.seed});break;case"diamond":A=xe.diamond(C,{lk:_.lk,name:_.name,crest:_.chrome,crestScale:_.crestScale});break;case"chant":A=xe.chant(C,{lk:_.lk,name:_.name,chant:yu(_,u),crestPlain:_.plain,seed:y.seed});break;case"parade":A=xe.parade(C,{lk:_.lk,crest:_.chrome,crestScale:_.crestScale,side:y.side,seed:y.seed});break;case"duel":A=xe.duel(C,{pair:y.pair.map(E=>({lk:E.team.lk,crestImg:E.team.img,player:E.player})),seed:y.seed});break;case"faceoff":A=xe.faceoff(C,{home:{lk:i.home.lk,chrome:i.home.chrome,crestScale:i.home.crestScale},away:{lk:i.away.lk,chrome:i.away.chrome,crestScale:i.away.crestScale},seed:y.again?9:3});break;case"abbr":A=xe.abbr(C,{home:i.home,away:i.away});break;case"trio":A=xe.trio(C,{lk:_.lk,crest:_.chrome,crestScale:_.crestScale,players:y.players,seed:y.seed});break;case"uclOpen":A=Ae.open(c("#7a8cff"),{mark:r});break;case"uclStar":A=Ae.star(c("#7a8cff"),{});break;case"uclArches":A=Ae.arches(c("#7a8cff"),_?{crest:_.chrome,crestScale:_.crestScale}:{});break;case"uclDrapes":A=Ae.drapes(c("#7a8cff"),{team:_,seed:y.seed});break;case"uclPlayer":A=Ae.player(c("#7a8cff"),{team:_,player:y.player,seed:y.seed});break;case"uclVersus":A=Ae.versus(c("#7a8cff"),{home:i.home,away:i.away});break;case"uclCave":A=Ae.cave(c("#7a8cff"),{});break;case"uclTrophy":A=Ae.trophy(c("#9a7bff"),{fixture:i.fixture,line2:[i.ground,i.ko?"KICK-OFF "+i.ko:""].filter(Boolean).join("   \xB7   ").toUpperCase()});break;default:continue}f.shot=A}Un=Un.filter(f=>f.shot),de={scene:new me,camera:new vs(-en/2,en/2,Rn/2,-Rn/2,-10,10)};let p=(f,y,_)=>{let M=new Ct(new Je(1,1),new be({map:f?jn(f,!1):null,color:_||16777215,transparent:!0,depthWrite:!1,depthTest:!1,blending:y?pi:Fn,toneMapped:!1}));return de.scene.add(M),M},g=Wt(512,288),x=g.getContext("2d"),m=x.createRadialGradient(256,144,60,256,144,300);m.addColorStop(0,"rgba(0,0,0,0)"),m.addColorStop(.65,"rgba(0,0,0,0.12)"),m.addColorStop(1,"rgba(0,0,0,0.6)"),x.fillStyle=m,x.fillRect(0,0,512,288),de.vig=p(g,!1),de.vig.scale.set(en,Rn,1),de.flash=p(null,!0,16777215),de.flash.scale.set(en,Rn,1),de.streak=p(ti(1024,128),!0,16777215),de.streak.scale.set(en*1.4,300,1),de.streak2=p(ti(1024,128),!0,new Gt("#9fe7ff")),de.streak2.scale.set(en*1.2,120,1),de.black=p(null,!1,0),de.black.scale.set(en,Rn,1);for(let f of Un)try{f.shot.update(.001,f.t1-f.t0),We.compile(f.shot.scene,f.shot.camera)}catch{}return We.compile(de.scene,de.camera),Su=!0,{len:Fs,shots:Un.length,style:h?"ucl":"network"}}function T_(i){We&&(Ds=hn(i||1,.4,1),We.setSize(Math.round(en*Ds),Math.round(Rn*Ds),!1))}var A_=null;function R_(i){if(!Su)return;i=hn(i,0,Fs-.001);let t=Un[Un.length-1];for(let a of Un)if(i>=a.t0&&i<a.t1){t=a;break}t.shot.update(i-t.t0,t.t1-t.t0),We.autoClear=!0,We.render(t.shot.scene,t.shot.camera);let e=0,n=-1,s=!1;for(let a of Un){if(a.t0<=.01)continue;let o=i-a.t0,l=xu.some(h=>Math.abs(h-a.t0)<.01),c=l?.95:a.t1-a.t0<=.6?.3:.5;o>=-.05&&o<.5&&(e=Math.max(e,c*(o<0?1+o/.05:Math.exp(-o/.085)))),o>=-.18&&o<.22&&(n=(o+.18)/.4,s=l)}if(de.flash.material.opacity=Math.min(1,e),de.flash.visible=e>.003,de.streak.visible=de.streak2.visible=n>=0,n>=0){let a=J(-en*1.1,en*1.1,_n(n)),o=Math.sin(n*Math.PI);de.streak.position.set(a,J(120,-80,n),0),de.streak.rotation.z=.12,de.streak.material.opacity=o*(s?1:.6),de.streak2.position.set(-a*.8,J(-200,160,n),0),de.streak2.rotation.z=-.08,de.streak2.material.opacity=o*(s?.8:.4)}let r=Math.max(1-St(0,.35,i),St(Fs-.9,Fs-.05,i));de.black.material.opacity=r,de.black.visible=r>.002,We.autoClear=!1,We.render(de.scene,de.camera),A_=t}})();
/**
 * @license
 * Copyright 2010-2024 Three.js Authors
 * SPDX-License-Identifier: MIT
 */
