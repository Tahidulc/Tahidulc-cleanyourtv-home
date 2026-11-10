(()=>{var Gl="170";var Cu=0,yc=1,Pu=2;var Ah=1,Iu=2,ti=3,xi=0,Re=1,de=2,gi=0,ds=1,Jr=2,Mc=3,Sc=4,Lu=5,Ni=100,Du=101,Uu=102,Nu=103,Fu=104,Ou=200,Bu=201,zu=202,Hu=203,_a=204,ya=205,ku=206,Vu=207,Gu=208,Wu=209,Xu=210,qu=211,Yu=212,$u=213,Zu=214,Ma=0,Sa=1,ba=2,gs=3,Ea=4,wa=5,Ta=6,Aa=7,Rh=0,Ju=1,Ku=2,vi=0,ju=1,Qu=2,td=3,Wl=4,ed=5,nd=6,id=7;var Ch=300,vs=301,xs=302,Ra=303,Ca=304,bo=306,_s=1e3,ei=1001,Pa=1002,un=1003,sd=1004;var _r=1005;var Bn=1006,Bo=1007;var ni=1008;var ri=1009,Ph=1010,Ih=1011,nr=1012,Xl=1013,Bi=1014,zn=1015,ar=1016,ql=1017,Yl=1018,ys=1020,Lh=35902,Dh=1021,Uh=1022,Cn=1023,Nh=1024,Fh=1025,fs=1026,Ms=1027,$l=1028,Zl=1029,Oh=1030,Jl=1031;var Kl=1033,Xr=33776,qr=33777,Yr=33778,$r=33779,Ia=35840,La=35841,Da=35842,Ua=35843,Na=36196,Fa=37492,Oa=37496,Ba=37808,za=37809,Ha=37810,ka=37811,Va=37812,Ga=37813,Wa=37814,Xa=37815,qa=37816,Ya=37817,$a=37818,Za=37819,Ja=37820,Ka=37821,Zr=36492,ja=36494,Qa=36495,Bh=36283,tl=36284,el=36285,nl=36286;var Kr=2300,il=2301,zo=2302,bc=2400,Ec=2401,wc=2402;var rd=3200,od=3201;var zh=0,ad=1,On="",Pe="srgb",Ps="srgb-linear",Eo="linear",ce="srgb";var Ji=7680;var Tc=519,ld=512,cd=513,hd=514,Hh=515,ud=516,dd=517,fd=518,pd=519,Ac=35044;var Rc="300 es",ii=2e3,jr=2001,_i=class{addEventListener(t,e){this._listeners===void 0&&(this._listeners={});let n=this._listeners;n[t]===void 0&&(n[t]=[]),n[t].indexOf(e)===-1&&n[t].push(e)}hasEventListener(t,e){if(this._listeners===void 0)return!1;let n=this._listeners;return n[t]!==void 0&&n[t].indexOf(e)!==-1}removeEventListener(t,e){if(this._listeners===void 0)return;let s=this._listeners[t];if(s!==void 0){let r=s.indexOf(e);r!==-1&&s.splice(r,1)}}dispatchEvent(t){if(this._listeners===void 0)return;let n=this._listeners[t.type];if(n!==void 0){t.target=this;let s=n.slice(0);for(let r=0,o=s.length;r<o;r++)s[r].call(this,t);t.target=null}}},We=["00","01","02","03","04","05","06","07","08","09","0a","0b","0c","0d","0e","0f","10","11","12","13","14","15","16","17","18","19","1a","1b","1c","1d","1e","1f","20","21","22","23","24","25","26","27","28","29","2a","2b","2c","2d","2e","2f","30","31","32","33","34","35","36","37","38","39","3a","3b","3c","3d","3e","3f","40","41","42","43","44","45","46","47","48","49","4a","4b","4c","4d","4e","4f","50","51","52","53","54","55","56","57","58","59","5a","5b","5c","5d","5e","5f","60","61","62","63","64","65","66","67","68","69","6a","6b","6c","6d","6e","6f","70","71","72","73","74","75","76","77","78","79","7a","7b","7c","7d","7e","7f","80","81","82","83","84","85","86","87","88","89","8a","8b","8c","8d","8e","8f","90","91","92","93","94","95","96","97","98","99","9a","9b","9c","9d","9e","9f","a0","a1","a2","a3","a4","a5","a6","a7","a8","a9","aa","ab","ac","ad","ae","af","b0","b1","b2","b3","b4","b5","b6","b7","b8","b9","ba","bb","bc","bd","be","bf","c0","c1","c2","c3","c4","c5","c6","c7","c8","c9","ca","cb","cc","cd","ce","cf","d0","d1","d2","d3","d4","d5","d6","d7","d8","d9","da","db","dc","dd","de","df","e0","e1","e2","e3","e4","e5","e6","e7","e8","e9","ea","eb","ec","ed","ee","ef","f0","f1","f2","f3","f4","f5","f6","f7","f8","f9","fa","fb","fc","fd","fe","ff"];var Ho=Math.PI/180,sl=180/Math.PI;function lr(){let i=Math.random()*4294967295|0,t=Math.random()*4294967295|0,e=Math.random()*4294967295|0,n=Math.random()*4294967295|0;return(We[i&255]+We[i>>8&255]+We[i>>16&255]+We[i>>24&255]+"-"+We[t&255]+We[t>>8&255]+"-"+We[t>>16&15|64]+We[t>>24&255]+"-"+We[e&63|128]+We[e>>8&255]+"-"+We[e>>16&255]+We[e>>24&255]+We[n&255]+We[n>>8&255]+We[n>>16&255]+We[n>>24&255]).toLowerCase()}function Ne(i,t,e){return Math.max(t,Math.min(e,i))}function md(i,t){return(i%t+t)%t}function ko(i,t,e){return(1-e)*i+e*t}function Gs(i,t){switch(t.constructor){case Float32Array:return i;case Uint32Array:return i/4294967295;case Uint16Array:return i/65535;case Uint8Array:return i/255;case Int32Array:return Math.max(i/2147483647,-1);case Int16Array:return Math.max(i/32767,-1);case Int8Array:return Math.max(i/127,-1);default:throw new Error("Invalid component type.")}}function en(i,t){switch(t.constructor){case Float32Array:return i;case Uint32Array:return Math.round(i*4294967295);case Uint16Array:return Math.round(i*65535);case Uint8Array:return Math.round(i*255);case Int32Array:return Math.round(i*2147483647);case Int16Array:return Math.round(i*32767);case Int8Array:return Math.round(i*127);default:throw new Error("Invalid component type.")}}var gt=class i{constructor(t=0,e=0){i.prototype.isVector2=!0,this.x=t,this.y=e}get width(){return this.x}set width(t){this.x=t}get height(){return this.y}set height(t){this.y=t}set(t,e){return this.x=t,this.y=e,this}setScalar(t){return this.x=t,this.y=t,this}setX(t){return this.x=t,this}setY(t){return this.y=t,this}setComponent(t,e){switch(t){case 0:this.x=e;break;case 1:this.y=e;break;default:throw new Error("index is out of range: "+t)}return this}getComponent(t){switch(t){case 0:return this.x;case 1:return this.y;default:throw new Error("index is out of range: "+t)}}clone(){return new this.constructor(this.x,this.y)}copy(t){return this.x=t.x,this.y=t.y,this}add(t){return this.x+=t.x,this.y+=t.y,this}addScalar(t){return this.x+=t,this.y+=t,this}addVectors(t,e){return this.x=t.x+e.x,this.y=t.y+e.y,this}addScaledVector(t,e){return this.x+=t.x*e,this.y+=t.y*e,this}sub(t){return this.x-=t.x,this.y-=t.y,this}subScalar(t){return this.x-=t,this.y-=t,this}subVectors(t,e){return this.x=t.x-e.x,this.y=t.y-e.y,this}multiply(t){return this.x*=t.x,this.y*=t.y,this}multiplyScalar(t){return this.x*=t,this.y*=t,this}divide(t){return this.x/=t.x,this.y/=t.y,this}divideScalar(t){return this.multiplyScalar(1/t)}applyMatrix3(t){let e=this.x,n=this.y,s=t.elements;return this.x=s[0]*e+s[3]*n+s[6],this.y=s[1]*e+s[4]*n+s[7],this}min(t){return this.x=Math.min(this.x,t.x),this.y=Math.min(this.y,t.y),this}max(t){return this.x=Math.max(this.x,t.x),this.y=Math.max(this.y,t.y),this}clamp(t,e){return this.x=Math.max(t.x,Math.min(e.x,this.x)),this.y=Math.max(t.y,Math.min(e.y,this.y)),this}clampScalar(t,e){return this.x=Math.max(t,Math.min(e,this.x)),this.y=Math.max(t,Math.min(e,this.y)),this}clampLength(t,e){let n=this.length();return this.divideScalar(n||1).multiplyScalar(Math.max(t,Math.min(e,n)))}floor(){return this.x=Math.floor(this.x),this.y=Math.floor(this.y),this}ceil(){return this.x=Math.ceil(this.x),this.y=Math.ceil(this.y),this}round(){return this.x=Math.round(this.x),this.y=Math.round(this.y),this}roundToZero(){return this.x=Math.trunc(this.x),this.y=Math.trunc(this.y),this}negate(){return this.x=-this.x,this.y=-this.y,this}dot(t){return this.x*t.x+this.y*t.y}cross(t){return this.x*t.y-this.y*t.x}lengthSq(){return this.x*this.x+this.y*this.y}length(){return Math.sqrt(this.x*this.x+this.y*this.y)}manhattanLength(){return Math.abs(this.x)+Math.abs(this.y)}normalize(){return this.divideScalar(this.length()||1)}angle(){return Math.atan2(-this.y,-this.x)+Math.PI}angleTo(t){let e=Math.sqrt(this.lengthSq()*t.lengthSq());if(e===0)return Math.PI/2;let n=this.dot(t)/e;return Math.acos(Ne(n,-1,1))}distanceTo(t){return Math.sqrt(this.distanceToSquared(t))}distanceToSquared(t){let e=this.x-t.x,n=this.y-t.y;return e*e+n*n}manhattanDistanceTo(t){return Math.abs(this.x-t.x)+Math.abs(this.y-t.y)}setLength(t){return this.normalize().multiplyScalar(t)}lerp(t,e){return this.x+=(t.x-this.x)*e,this.y+=(t.y-this.y)*e,this}lerpVectors(t,e,n){return this.x=t.x+(e.x-t.x)*n,this.y=t.y+(e.y-t.y)*n,this}equals(t){return t.x===this.x&&t.y===this.y}fromArray(t,e=0){return this.x=t[e],this.y=t[e+1],this}toArray(t=[],e=0){return t[e]=this.x,t[e+1]=this.y,t}fromBufferAttribute(t,e){return this.x=t.getX(e),this.y=t.getY(e),this}rotateAround(t,e){let n=Math.cos(e),s=Math.sin(e),r=this.x-t.x,o=this.y-t.y;return this.x=r*n-o*s+t.x,this.y=r*s+o*n+t.y,this}random(){return this.x=Math.random(),this.y=Math.random(),this}*[Symbol.iterator](){yield this.x,yield this.y}},Ot=class i{constructor(t,e,n,s,r,o,a,l,c){i.prototype.isMatrix3=!0,this.elements=[1,0,0,0,1,0,0,0,1],t!==void 0&&this.set(t,e,n,s,r,o,a,l,c)}set(t,e,n,s,r,o,a,l,c){let h=this.elements;return h[0]=t,h[1]=s,h[2]=a,h[3]=e,h[4]=r,h[5]=l,h[6]=n,h[7]=o,h[8]=c,this}identity(){return this.set(1,0,0,0,1,0,0,0,1),this}copy(t){let e=this.elements,n=t.elements;return e[0]=n[0],e[1]=n[1],e[2]=n[2],e[3]=n[3],e[4]=n[4],e[5]=n[5],e[6]=n[6],e[7]=n[7],e[8]=n[8],this}extractBasis(t,e,n){return t.setFromMatrix3Column(this,0),e.setFromMatrix3Column(this,1),n.setFromMatrix3Column(this,2),this}setFromMatrix4(t){let e=t.elements;return this.set(e[0],e[4],e[8],e[1],e[5],e[9],e[2],e[6],e[10]),this}multiply(t){return this.multiplyMatrices(this,t)}premultiply(t){return this.multiplyMatrices(t,this)}multiplyMatrices(t,e){let n=t.elements,s=e.elements,r=this.elements,o=n[0],a=n[3],l=n[6],c=n[1],h=n[4],d=n[7],u=n[2],f=n[5],g=n[8],y=s[0],m=s[3],p=s[6],w=s[1],E=s[4],M=s[7],U=s[2],C=s[5],v=s[8];return r[0]=o*y+a*w+l*U,r[3]=o*m+a*E+l*C,r[6]=o*p+a*M+l*v,r[1]=c*y+h*w+d*U,r[4]=c*m+h*E+d*C,r[7]=c*p+h*M+d*v,r[2]=u*y+f*w+g*U,r[5]=u*m+f*E+g*C,r[8]=u*p+f*M+g*v,this}multiplyScalar(t){let e=this.elements;return e[0]*=t,e[3]*=t,e[6]*=t,e[1]*=t,e[4]*=t,e[7]*=t,e[2]*=t,e[5]*=t,e[8]*=t,this}determinant(){let t=this.elements,e=t[0],n=t[1],s=t[2],r=t[3],o=t[4],a=t[5],l=t[6],c=t[7],h=t[8];return e*o*h-e*a*c-n*r*h+n*a*l+s*r*c-s*o*l}invert(){let t=this.elements,e=t[0],n=t[1],s=t[2],r=t[3],o=t[4],a=t[5],l=t[6],c=t[7],h=t[8],d=h*o-a*c,u=a*l-h*r,f=c*r-o*l,g=e*d+n*u+s*f;if(g===0)return this.set(0,0,0,0,0,0,0,0,0);let y=1/g;return t[0]=d*y,t[1]=(s*c-h*n)*y,t[2]=(a*n-s*o)*y,t[3]=u*y,t[4]=(h*e-s*l)*y,t[5]=(s*r-a*e)*y,t[6]=f*y,t[7]=(n*l-c*e)*y,t[8]=(o*e-n*r)*y,this}transpose(){let t,e=this.elements;return t=e[1],e[1]=e[3],e[3]=t,t=e[2],e[2]=e[6],e[6]=t,t=e[5],e[5]=e[7],e[7]=t,this}getNormalMatrix(t){return this.setFromMatrix4(t).invert().transpose()}transposeIntoArray(t){let e=this.elements;return t[0]=e[0],t[1]=e[3],t[2]=e[6],t[3]=e[1],t[4]=e[4],t[5]=e[7],t[6]=e[2],t[7]=e[5],t[8]=e[8],this}setUvTransform(t,e,n,s,r,o,a){let l=Math.cos(r),c=Math.sin(r);return this.set(n*l,n*c,-n*(l*o+c*a)+o+t,-s*c,s*l,-s*(-c*o+l*a)+a+e,0,0,1),this}scale(t,e){return this.premultiply(Vo.makeScale(t,e)),this}rotate(t){return this.premultiply(Vo.makeRotation(-t)),this}translate(t,e){return this.premultiply(Vo.makeTranslation(t,e)),this}makeTranslation(t,e){return t.isVector2?this.set(1,0,t.x,0,1,t.y,0,0,1):this.set(1,0,t,0,1,e,0,0,1),this}makeRotation(t){let e=Math.cos(t),n=Math.sin(t);return this.set(e,-n,0,n,e,0,0,0,1),this}makeScale(t,e){return this.set(t,0,0,0,e,0,0,0,1),this}equals(t){let e=this.elements,n=t.elements;for(let s=0;s<9;s++)if(e[s]!==n[s])return!1;return!0}fromArray(t,e=0){for(let n=0;n<9;n++)this.elements[n]=t[n+e];return this}toArray(t=[],e=0){let n=this.elements;return t[e]=n[0],t[e+1]=n[1],t[e+2]=n[2],t[e+3]=n[3],t[e+4]=n[4],t[e+5]=n[5],t[e+6]=n[6],t[e+7]=n[7],t[e+8]=n[8],t}clone(){return new this.constructor().fromArray(this.elements)}},Vo=new Ot;function kh(i){for(let t=i.length-1;t>=0;--t)if(i[t]>=65535)return!0;return!1}function Qr(i){return document.createElementNS("http://www.w3.org/1999/xhtml",i)}function gd(){let i=Qr("canvas");return i.style.display="block",i}var Cc={};function Ks(i){i in Cc||(Cc[i]=!0,console.warn(i))}function vd(i,t,e){return new Promise(function(n,s){function r(){switch(i.clientWaitSync(t,i.SYNC_FLUSH_COMMANDS_BIT,0)){case i.WAIT_FAILED:s();break;case i.TIMEOUT_EXPIRED:setTimeout(r,e);break;default:n()}}setTimeout(r,e)})}function xd(i){let t=i.elements;t[2]=.5*t[2]+.5*t[3],t[6]=.5*t[6]+.5*t[7],t[10]=.5*t[10]+.5*t[11],t[14]=.5*t[14]+.5*t[15]}function _d(i){let t=i.elements;t[11]===-1?(t[10]=-t[10]-1,t[14]=-t[14]):(t[10]=-t[10],t[14]=-t[14]+1)}var jt={enabled:!0,workingColorSpace:Ps,spaces:{},convert:function(i,t,e){return this.enabled===!1||t===e||!t||!e||(this.spaces[t].transfer===ce&&(i.r=si(i.r),i.g=si(i.g),i.b=si(i.b)),this.spaces[t].primaries!==this.spaces[e].primaries&&(i.applyMatrix3(this.spaces[t].toXYZ),i.applyMatrix3(this.spaces[e].fromXYZ)),this.spaces[e].transfer===ce&&(i.r=ps(i.r),i.g=ps(i.g),i.b=ps(i.b))),i},fromWorkingColorSpace:function(i,t){return this.convert(i,this.workingColorSpace,t)},toWorkingColorSpace:function(i,t){return this.convert(i,t,this.workingColorSpace)},getPrimaries:function(i){return this.spaces[i].primaries},getTransfer:function(i){return i===On?Eo:this.spaces[i].transfer},getLuminanceCoefficients:function(i,t=this.workingColorSpace){return i.fromArray(this.spaces[t].luminanceCoefficients)},define:function(i){Object.assign(this.spaces,i)},_getMatrix:function(i,t,e){return i.copy(this.spaces[t].toXYZ).multiply(this.spaces[e].fromXYZ)},_getDrawingBufferColorSpace:function(i){return this.spaces[i].outputColorSpaceConfig.drawingBufferColorSpace},_getUnpackColorSpace:function(i=this.workingColorSpace){return this.spaces[i].workingColorSpaceConfig.unpackColorSpace}};function si(i){return i<.04045?i*.0773993808:Math.pow(i*.9478672986+.0521327014,2.4)}function ps(i){return i<.0031308?i*12.92:1.055*Math.pow(i,.41666)-.055}var Pc=[.64,.33,.3,.6,.15,.06],Ic=[.2126,.7152,.0722],Lc=[.3127,.329],Dc=new Ot().set(.4123908,.3575843,.1804808,.212639,.7151687,.0721923,.0193308,.1191948,.9505322),Uc=new Ot().set(3.2409699,-1.5373832,-.4986108,-.9692436,1.8759675,.0415551,.0556301,-.203977,1.0569715);jt.define({[Ps]:{primaries:Pc,whitePoint:Lc,transfer:Eo,toXYZ:Dc,fromXYZ:Uc,luminanceCoefficients:Ic,workingColorSpaceConfig:{unpackColorSpace:Pe},outputColorSpaceConfig:{drawingBufferColorSpace:Pe}},[Pe]:{primaries:Pc,whitePoint:Lc,transfer:ce,toXYZ:Dc,fromXYZ:Uc,luminanceCoefficients:Ic,outputColorSpaceConfig:{drawingBufferColorSpace:Pe}}});var Ki,rl=class{static getDataURL(t){if(/^data:/i.test(t.src)||typeof HTMLCanvasElement=="undefined")return t.src;let e;if(t instanceof HTMLCanvasElement)e=t;else{Ki===void 0&&(Ki=Qr("canvas")),Ki.width=t.width,Ki.height=t.height;let n=Ki.getContext("2d");t instanceof ImageData?n.putImageData(t,0,0):n.drawImage(t,0,0,t.width,t.height),e=Ki}return e.width>2048||e.height>2048?(console.warn("THREE.ImageUtils.getDataURL: Image converted to jpg for performance reasons",t),e.toDataURL("image/jpeg",.6)):e.toDataURL("image/png")}static sRGBToLinear(t){if(typeof HTMLImageElement!="undefined"&&t instanceof HTMLImageElement||typeof HTMLCanvasElement!="undefined"&&t instanceof HTMLCanvasElement||typeof ImageBitmap!="undefined"&&t instanceof ImageBitmap){let e=Qr("canvas");e.width=t.width,e.height=t.height;let n=e.getContext("2d");n.drawImage(t,0,0,t.width,t.height);let s=n.getImageData(0,0,t.width,t.height),r=s.data;for(let o=0;o<r.length;o++)r[o]=si(r[o]/255)*255;return n.putImageData(s,0,0),e}else if(t.data){let e=t.data.slice(0);for(let n=0;n<e.length;n++)e instanceof Uint8Array||e instanceof Uint8ClampedArray?e[n]=Math.floor(si(e[n]/255)*255):e[n]=si(e[n]);return{data:e,width:t.width,height:t.height}}else return console.warn("THREE.ImageUtils.sRGBToLinear(): Unsupported image type. No color space conversion applied."),t}},yd=0,to=class{constructor(t=null){this.isSource=!0,Object.defineProperty(this,"id",{value:yd++}),this.uuid=lr(),this.data=t,this.dataReady=!0,this.version=0}set needsUpdate(t){t===!0&&this.version++}toJSON(t){let e=t===void 0||typeof t=="string";if(!e&&t.images[this.uuid]!==void 0)return t.images[this.uuid];let n={uuid:this.uuid,url:""},s=this.data;if(s!==null){let r;if(Array.isArray(s)){r=[];for(let o=0,a=s.length;o<a;o++)s[o].isDataTexture?r.push(Go(s[o].image)):r.push(Go(s[o]))}else r=Go(s);n.url=r}return e||(t.images[this.uuid]=n),n}};function Go(i){return typeof HTMLImageElement!="undefined"&&i instanceof HTMLImageElement||typeof HTMLCanvasElement!="undefined"&&i instanceof HTMLCanvasElement||typeof ImageBitmap!="undefined"&&i instanceof ImageBitmap?rl.getDataURL(i):i.data?{data:Array.from(i.data),width:i.width,height:i.height,type:i.data.constructor.name}:(console.warn("THREE.Texture: Unable to serialize Texture."),{})}var Md=0,nn=class i extends _i{constructor(t=i.DEFAULT_IMAGE,e=i.DEFAULT_MAPPING,n=ei,s=ei,r=Bn,o=ni,a=Cn,l=ri,c=i.DEFAULT_ANISOTROPY,h=On){super(),this.isTexture=!0,Object.defineProperty(this,"id",{value:Md++}),this.uuid=lr(),this.name="",this.source=new to(t),this.mipmaps=[],this.mapping=e,this.channel=0,this.wrapS=n,this.wrapT=s,this.magFilter=r,this.minFilter=o,this.anisotropy=c,this.format=a,this.internalFormat=null,this.type=l,this.offset=new gt(0,0),this.repeat=new gt(1,1),this.center=new gt(0,0),this.rotation=0,this.matrixAutoUpdate=!0,this.matrix=new Ot,this.generateMipmaps=!0,this.premultiplyAlpha=!1,this.flipY=!0,this.unpackAlignment=4,this.colorSpace=h,this.userData={},this.version=0,this.onUpdate=null,this.isRenderTargetTexture=!1,this.pmremVersion=0}get image(){return this.source.data}set image(t=null){this.source.data=t}updateMatrix(){this.matrix.setUvTransform(this.offset.x,this.offset.y,this.repeat.x,this.repeat.y,this.rotation,this.center.x,this.center.y)}clone(){return new this.constructor().copy(this)}copy(t){return this.name=t.name,this.source=t.source,this.mipmaps=t.mipmaps.slice(0),this.mapping=t.mapping,this.channel=t.channel,this.wrapS=t.wrapS,this.wrapT=t.wrapT,this.magFilter=t.magFilter,this.minFilter=t.minFilter,this.anisotropy=t.anisotropy,this.format=t.format,this.internalFormat=t.internalFormat,this.type=t.type,this.offset.copy(t.offset),this.repeat.copy(t.repeat),this.center.copy(t.center),this.rotation=t.rotation,this.matrixAutoUpdate=t.matrixAutoUpdate,this.matrix.copy(t.matrix),this.generateMipmaps=t.generateMipmaps,this.premultiplyAlpha=t.premultiplyAlpha,this.flipY=t.flipY,this.unpackAlignment=t.unpackAlignment,this.colorSpace=t.colorSpace,this.userData=JSON.parse(JSON.stringify(t.userData)),this.needsUpdate=!0,this}toJSON(t){let e=t===void 0||typeof t=="string";if(!e&&t.textures[this.uuid]!==void 0)return t.textures[this.uuid];let n={metadata:{version:4.6,type:"Texture",generator:"Texture.toJSON"},uuid:this.uuid,name:this.name,image:this.source.toJSON(t).uuid,mapping:this.mapping,channel:this.channel,repeat:[this.repeat.x,this.repeat.y],offset:[this.offset.x,this.offset.y],center:[this.center.x,this.center.y],rotation:this.rotation,wrap:[this.wrapS,this.wrapT],format:this.format,internalFormat:this.internalFormat,type:this.type,colorSpace:this.colorSpace,minFilter:this.minFilter,magFilter:this.magFilter,anisotropy:this.anisotropy,flipY:this.flipY,generateMipmaps:this.generateMipmaps,premultiplyAlpha:this.premultiplyAlpha,unpackAlignment:this.unpackAlignment};return Object.keys(this.userData).length>0&&(n.userData=this.userData),e||(t.textures[this.uuid]=n),n}dispose(){this.dispatchEvent({type:"dispose"})}transformUv(t){if(this.mapping!==Ch)return t;if(t.applyMatrix3(this.matrix),t.x<0||t.x>1)switch(this.wrapS){case _s:t.x=t.x-Math.floor(t.x);break;case ei:t.x=t.x<0?0:1;break;case Pa:Math.abs(Math.floor(t.x)%2)===1?t.x=Math.ceil(t.x)-t.x:t.x=t.x-Math.floor(t.x);break}if(t.y<0||t.y>1)switch(this.wrapT){case _s:t.y=t.y-Math.floor(t.y);break;case ei:t.y=t.y<0?0:1;break;case Pa:Math.abs(Math.floor(t.y)%2)===1?t.y=Math.ceil(t.y)-t.y:t.y=t.y-Math.floor(t.y);break}return this.flipY&&(t.y=1-t.y),t}set needsUpdate(t){t===!0&&(this.version++,this.source.needsUpdate=!0)}set needsPMREMUpdate(t){t===!0&&this.pmremVersion++}};nn.DEFAULT_IMAGE=null;nn.DEFAULT_MAPPING=Ch;nn.DEFAULT_ANISOTROPY=1;var re=class i{constructor(t=0,e=0,n=0,s=1){i.prototype.isVector4=!0,this.x=t,this.y=e,this.z=n,this.w=s}get width(){return this.z}set width(t){this.z=t}get height(){return this.w}set height(t){this.w=t}set(t,e,n,s){return this.x=t,this.y=e,this.z=n,this.w=s,this}setScalar(t){return this.x=t,this.y=t,this.z=t,this.w=t,this}setX(t){return this.x=t,this}setY(t){return this.y=t,this}setZ(t){return this.z=t,this}setW(t){return this.w=t,this}setComponent(t,e){switch(t){case 0:this.x=e;break;case 1:this.y=e;break;case 2:this.z=e;break;case 3:this.w=e;break;default:throw new Error("index is out of range: "+t)}return this}getComponent(t){switch(t){case 0:return this.x;case 1:return this.y;case 2:return this.z;case 3:return this.w;default:throw new Error("index is out of range: "+t)}}clone(){return new this.constructor(this.x,this.y,this.z,this.w)}copy(t){return this.x=t.x,this.y=t.y,this.z=t.z,this.w=t.w!==void 0?t.w:1,this}add(t){return this.x+=t.x,this.y+=t.y,this.z+=t.z,this.w+=t.w,this}addScalar(t){return this.x+=t,this.y+=t,this.z+=t,this.w+=t,this}addVectors(t,e){return this.x=t.x+e.x,this.y=t.y+e.y,this.z=t.z+e.z,this.w=t.w+e.w,this}addScaledVector(t,e){return this.x+=t.x*e,this.y+=t.y*e,this.z+=t.z*e,this.w+=t.w*e,this}sub(t){return this.x-=t.x,this.y-=t.y,this.z-=t.z,this.w-=t.w,this}subScalar(t){return this.x-=t,this.y-=t,this.z-=t,this.w-=t,this}subVectors(t,e){return this.x=t.x-e.x,this.y=t.y-e.y,this.z=t.z-e.z,this.w=t.w-e.w,this}multiply(t){return this.x*=t.x,this.y*=t.y,this.z*=t.z,this.w*=t.w,this}multiplyScalar(t){return this.x*=t,this.y*=t,this.z*=t,this.w*=t,this}applyMatrix4(t){let e=this.x,n=this.y,s=this.z,r=this.w,o=t.elements;return this.x=o[0]*e+o[4]*n+o[8]*s+o[12]*r,this.y=o[1]*e+o[5]*n+o[9]*s+o[13]*r,this.z=o[2]*e+o[6]*n+o[10]*s+o[14]*r,this.w=o[3]*e+o[7]*n+o[11]*s+o[15]*r,this}divide(t){return this.x/=t.x,this.y/=t.y,this.z/=t.z,this.w/=t.w,this}divideScalar(t){return this.multiplyScalar(1/t)}setAxisAngleFromQuaternion(t){this.w=2*Math.acos(t.w);let e=Math.sqrt(1-t.w*t.w);return e<1e-4?(this.x=1,this.y=0,this.z=0):(this.x=t.x/e,this.y=t.y/e,this.z=t.z/e),this}setAxisAngleFromRotationMatrix(t){let e,n,s,r,l=t.elements,c=l[0],h=l[4],d=l[8],u=l[1],f=l[5],g=l[9],y=l[2],m=l[6],p=l[10];if(Math.abs(h-u)<.01&&Math.abs(d-y)<.01&&Math.abs(g-m)<.01){if(Math.abs(h+u)<.1&&Math.abs(d+y)<.1&&Math.abs(g+m)<.1&&Math.abs(c+f+p-3)<.1)return this.set(1,0,0,0),this;e=Math.PI;let E=(c+1)/2,M=(f+1)/2,U=(p+1)/2,C=(h+u)/4,v=(d+y)/4,b=(g+m)/4;return E>M&&E>U?E<.01?(n=0,s=.707106781,r=.707106781):(n=Math.sqrt(E),s=C/n,r=v/n):M>U?M<.01?(n=.707106781,s=0,r=.707106781):(s=Math.sqrt(M),n=C/s,r=b/s):U<.01?(n=.707106781,s=.707106781,r=0):(r=Math.sqrt(U),n=v/r,s=b/r),this.set(n,s,r,e),this}let w=Math.sqrt((m-g)*(m-g)+(d-y)*(d-y)+(u-h)*(u-h));return Math.abs(w)<.001&&(w=1),this.x=(m-g)/w,this.y=(d-y)/w,this.z=(u-h)/w,this.w=Math.acos((c+f+p-1)/2),this}setFromMatrixPosition(t){let e=t.elements;return this.x=e[12],this.y=e[13],this.z=e[14],this.w=e[15],this}min(t){return this.x=Math.min(this.x,t.x),this.y=Math.min(this.y,t.y),this.z=Math.min(this.z,t.z),this.w=Math.min(this.w,t.w),this}max(t){return this.x=Math.max(this.x,t.x),this.y=Math.max(this.y,t.y),this.z=Math.max(this.z,t.z),this.w=Math.max(this.w,t.w),this}clamp(t,e){return this.x=Math.max(t.x,Math.min(e.x,this.x)),this.y=Math.max(t.y,Math.min(e.y,this.y)),this.z=Math.max(t.z,Math.min(e.z,this.z)),this.w=Math.max(t.w,Math.min(e.w,this.w)),this}clampScalar(t,e){return this.x=Math.max(t,Math.min(e,this.x)),this.y=Math.max(t,Math.min(e,this.y)),this.z=Math.max(t,Math.min(e,this.z)),this.w=Math.max(t,Math.min(e,this.w)),this}clampLength(t,e){let n=this.length();return this.divideScalar(n||1).multiplyScalar(Math.max(t,Math.min(e,n)))}floor(){return this.x=Math.floor(this.x),this.y=Math.floor(this.y),this.z=Math.floor(this.z),this.w=Math.floor(this.w),this}ceil(){return this.x=Math.ceil(this.x),this.y=Math.ceil(this.y),this.z=Math.ceil(this.z),this.w=Math.ceil(this.w),this}round(){return this.x=Math.round(this.x),this.y=Math.round(this.y),this.z=Math.round(this.z),this.w=Math.round(this.w),this}roundToZero(){return this.x=Math.trunc(this.x),this.y=Math.trunc(this.y),this.z=Math.trunc(this.z),this.w=Math.trunc(this.w),this}negate(){return this.x=-this.x,this.y=-this.y,this.z=-this.z,this.w=-this.w,this}dot(t){return this.x*t.x+this.y*t.y+this.z*t.z+this.w*t.w}lengthSq(){return this.x*this.x+this.y*this.y+this.z*this.z+this.w*this.w}length(){return Math.sqrt(this.x*this.x+this.y*this.y+this.z*this.z+this.w*this.w)}manhattanLength(){return Math.abs(this.x)+Math.abs(this.y)+Math.abs(this.z)+Math.abs(this.w)}normalize(){return this.divideScalar(this.length()||1)}setLength(t){return this.normalize().multiplyScalar(t)}lerp(t,e){return this.x+=(t.x-this.x)*e,this.y+=(t.y-this.y)*e,this.z+=(t.z-this.z)*e,this.w+=(t.w-this.w)*e,this}lerpVectors(t,e,n){return this.x=t.x+(e.x-t.x)*n,this.y=t.y+(e.y-t.y)*n,this.z=t.z+(e.z-t.z)*n,this.w=t.w+(e.w-t.w)*n,this}equals(t){return t.x===this.x&&t.y===this.y&&t.z===this.z&&t.w===this.w}fromArray(t,e=0){return this.x=t[e],this.y=t[e+1],this.z=t[e+2],this.w=t[e+3],this}toArray(t=[],e=0){return t[e]=this.x,t[e+1]=this.y,t[e+2]=this.z,t[e+3]=this.w,t}fromBufferAttribute(t,e){return this.x=t.getX(e),this.y=t.getY(e),this.z=t.getZ(e),this.w=t.getW(e),this}random(){return this.x=Math.random(),this.y=Math.random(),this.z=Math.random(),this.w=Math.random(),this}*[Symbol.iterator](){yield this.x,yield this.y,yield this.z,yield this.w}},ol=class extends _i{constructor(t=1,e=1,n={}){super(),this.isRenderTarget=!0,this.width=t,this.height=e,this.depth=1,this.scissor=new re(0,0,t,e),this.scissorTest=!1,this.viewport=new re(0,0,t,e);let s={width:t,height:e,depth:1};n=Object.assign({generateMipmaps:!1,internalFormat:null,minFilter:Bn,depthBuffer:!0,stencilBuffer:!1,resolveDepthBuffer:!0,resolveStencilBuffer:!0,depthTexture:null,samples:0,count:1},n);let r=new nn(s,n.mapping,n.wrapS,n.wrapT,n.magFilter,n.minFilter,n.format,n.type,n.anisotropy,n.colorSpace);r.flipY=!1,r.generateMipmaps=n.generateMipmaps,r.internalFormat=n.internalFormat,this.textures=[];let o=n.count;for(let a=0;a<o;a++)this.textures[a]=r.clone(),this.textures[a].isRenderTargetTexture=!0;this.depthBuffer=n.depthBuffer,this.stencilBuffer=n.stencilBuffer,this.resolveDepthBuffer=n.resolveDepthBuffer,this.resolveStencilBuffer=n.resolveStencilBuffer,this.depthTexture=n.depthTexture,this.samples=n.samples}get texture(){return this.textures[0]}set texture(t){this.textures[0]=t}setSize(t,e,n=1){if(this.width!==t||this.height!==e||this.depth!==n){this.width=t,this.height=e,this.depth=n;for(let s=0,r=this.textures.length;s<r;s++)this.textures[s].image.width=t,this.textures[s].image.height=e,this.textures[s].image.depth=n;this.dispose()}this.viewport.set(0,0,t,e),this.scissor.set(0,0,t,e)}clone(){return new this.constructor().copy(this)}copy(t){this.width=t.width,this.height=t.height,this.depth=t.depth,this.scissor.copy(t.scissor),this.scissorTest=t.scissorTest,this.viewport.copy(t.viewport),this.textures.length=0;for(let n=0,s=t.textures.length;n<s;n++)this.textures[n]=t.textures[n].clone(),this.textures[n].isRenderTargetTexture=!0;let e=Object.assign({},t.texture.image);return this.texture.source=new to(e),this.depthBuffer=t.depthBuffer,this.stencilBuffer=t.stencilBuffer,this.resolveDepthBuffer=t.resolveDepthBuffer,this.resolveStencilBuffer=t.resolveStencilBuffer,t.depthTexture!==null&&(this.depthTexture=t.depthTexture.clone()),this.samples=t.samples,this}dispose(){this.dispatchEvent({type:"dispose"})}},oi=class extends ol{constructor(t=1,e=1,n={}){super(t,e,n),this.isWebGLRenderTarget=!0}},eo=class extends nn{constructor(t=null,e=1,n=1,s=1){super(null),this.isDataArrayTexture=!0,this.image={data:t,width:e,height:n,depth:s},this.magFilter=un,this.minFilter=un,this.wrapR=ei,this.generateMipmaps=!1,this.flipY=!1,this.unpackAlignment=1,this.layerUpdates=new Set}addLayerUpdate(t){this.layerUpdates.add(t)}clearLayerUpdates(){this.layerUpdates.clear()}};var al=class extends nn{constructor(t=null,e=1,n=1,s=1){super(null),this.isData3DTexture=!0,this.image={data:t,width:e,height:n,depth:s},this.magFilter=un,this.minFilter=un,this.wrapR=ei,this.generateMipmaps=!1,this.flipY=!1,this.unpackAlignment=1}};var Hn=class{constructor(t=0,e=0,n=0,s=1){this.isQuaternion=!0,this._x=t,this._y=e,this._z=n,this._w=s}static slerpFlat(t,e,n,s,r,o,a){let l=n[s+0],c=n[s+1],h=n[s+2],d=n[s+3],u=r[o+0],f=r[o+1],g=r[o+2],y=r[o+3];if(a===0){t[e+0]=l,t[e+1]=c,t[e+2]=h,t[e+3]=d;return}if(a===1){t[e+0]=u,t[e+1]=f,t[e+2]=g,t[e+3]=y;return}if(d!==y||l!==u||c!==f||h!==g){let m=1-a,p=l*u+c*f+h*g+d*y,w=p>=0?1:-1,E=1-p*p;if(E>Number.EPSILON){let U=Math.sqrt(E),C=Math.atan2(U,p*w);m=Math.sin(m*C)/U,a=Math.sin(a*C)/U}let M=a*w;if(l=l*m+u*M,c=c*m+f*M,h=h*m+g*M,d=d*m+y*M,m===1-a){let U=1/Math.sqrt(l*l+c*c+h*h+d*d);l*=U,c*=U,h*=U,d*=U}}t[e]=l,t[e+1]=c,t[e+2]=h,t[e+3]=d}static multiplyQuaternionsFlat(t,e,n,s,r,o){let a=n[s],l=n[s+1],c=n[s+2],h=n[s+3],d=r[o],u=r[o+1],f=r[o+2],g=r[o+3];return t[e]=a*g+h*d+l*f-c*u,t[e+1]=l*g+h*u+c*d-a*f,t[e+2]=c*g+h*f+a*u-l*d,t[e+3]=h*g-a*d-l*u-c*f,t}get x(){return this._x}set x(t){this._x=t,this._onChangeCallback()}get y(){return this._y}set y(t){this._y=t,this._onChangeCallback()}get z(){return this._z}set z(t){this._z=t,this._onChangeCallback()}get w(){return this._w}set w(t){this._w=t,this._onChangeCallback()}set(t,e,n,s){return this._x=t,this._y=e,this._z=n,this._w=s,this._onChangeCallback(),this}clone(){return new this.constructor(this._x,this._y,this._z,this._w)}copy(t){return this._x=t.x,this._y=t.y,this._z=t.z,this._w=t.w,this._onChangeCallback(),this}setFromEuler(t,e=!0){let n=t._x,s=t._y,r=t._z,o=t._order,a=Math.cos,l=Math.sin,c=a(n/2),h=a(s/2),d=a(r/2),u=l(n/2),f=l(s/2),g=l(r/2);switch(o){case"XYZ":this._x=u*h*d+c*f*g,this._y=c*f*d-u*h*g,this._z=c*h*g+u*f*d,this._w=c*h*d-u*f*g;break;case"YXZ":this._x=u*h*d+c*f*g,this._y=c*f*d-u*h*g,this._z=c*h*g-u*f*d,this._w=c*h*d+u*f*g;break;case"ZXY":this._x=u*h*d-c*f*g,this._y=c*f*d+u*h*g,this._z=c*h*g+u*f*d,this._w=c*h*d-u*f*g;break;case"ZYX":this._x=u*h*d-c*f*g,this._y=c*f*d+u*h*g,this._z=c*h*g-u*f*d,this._w=c*h*d+u*f*g;break;case"YZX":this._x=u*h*d+c*f*g,this._y=c*f*d+u*h*g,this._z=c*h*g-u*f*d,this._w=c*h*d-u*f*g;break;case"XZY":this._x=u*h*d-c*f*g,this._y=c*f*d-u*h*g,this._z=c*h*g+u*f*d,this._w=c*h*d+u*f*g;break;default:console.warn("THREE.Quaternion: .setFromEuler() encountered an unknown order: "+o)}return e===!0&&this._onChangeCallback(),this}setFromAxisAngle(t,e){let n=e/2,s=Math.sin(n);return this._x=t.x*s,this._y=t.y*s,this._z=t.z*s,this._w=Math.cos(n),this._onChangeCallback(),this}setFromRotationMatrix(t){let e=t.elements,n=e[0],s=e[4],r=e[8],o=e[1],a=e[5],l=e[9],c=e[2],h=e[6],d=e[10],u=n+a+d;if(u>0){let f=.5/Math.sqrt(u+1);this._w=.25/f,this._x=(h-l)*f,this._y=(r-c)*f,this._z=(o-s)*f}else if(n>a&&n>d){let f=2*Math.sqrt(1+n-a-d);this._w=(h-l)/f,this._x=.25*f,this._y=(s+o)/f,this._z=(r+c)/f}else if(a>d){let f=2*Math.sqrt(1+a-n-d);this._w=(r-c)/f,this._x=(s+o)/f,this._y=.25*f,this._z=(l+h)/f}else{let f=2*Math.sqrt(1+d-n-a);this._w=(o-s)/f,this._x=(r+c)/f,this._y=(l+h)/f,this._z=.25*f}return this._onChangeCallback(),this}setFromUnitVectors(t,e){let n=t.dot(e)+1;return n<Number.EPSILON?(n=0,Math.abs(t.x)>Math.abs(t.z)?(this._x=-t.y,this._y=t.x,this._z=0,this._w=n):(this._x=0,this._y=-t.z,this._z=t.y,this._w=n)):(this._x=t.y*e.z-t.z*e.y,this._y=t.z*e.x-t.x*e.z,this._z=t.x*e.y-t.y*e.x,this._w=n),this.normalize()}angleTo(t){return 2*Math.acos(Math.abs(Ne(this.dot(t),-1,1)))}rotateTowards(t,e){let n=this.angleTo(t);if(n===0)return this;let s=Math.min(1,e/n);return this.slerp(t,s),this}identity(){return this.set(0,0,0,1)}invert(){return this.conjugate()}conjugate(){return this._x*=-1,this._y*=-1,this._z*=-1,this._onChangeCallback(),this}dot(t){return this._x*t._x+this._y*t._y+this._z*t._z+this._w*t._w}lengthSq(){return this._x*this._x+this._y*this._y+this._z*this._z+this._w*this._w}length(){return Math.sqrt(this._x*this._x+this._y*this._y+this._z*this._z+this._w*this._w)}normalize(){let t=this.length();return t===0?(this._x=0,this._y=0,this._z=0,this._w=1):(t=1/t,this._x=this._x*t,this._y=this._y*t,this._z=this._z*t,this._w=this._w*t),this._onChangeCallback(),this}multiply(t){return this.multiplyQuaternions(this,t)}premultiply(t){return this.multiplyQuaternions(t,this)}multiplyQuaternions(t,e){let n=t._x,s=t._y,r=t._z,o=t._w,a=e._x,l=e._y,c=e._z,h=e._w;return this._x=n*h+o*a+s*c-r*l,this._y=s*h+o*l+r*a-n*c,this._z=r*h+o*c+n*l-s*a,this._w=o*h-n*a-s*l-r*c,this._onChangeCallback(),this}slerp(t,e){if(e===0)return this;if(e===1)return this.copy(t);let n=this._x,s=this._y,r=this._z,o=this._w,a=o*t._w+n*t._x+s*t._y+r*t._z;if(a<0?(this._w=-t._w,this._x=-t._x,this._y=-t._y,this._z=-t._z,a=-a):this.copy(t),a>=1)return this._w=o,this._x=n,this._y=s,this._z=r,this;let l=1-a*a;if(l<=Number.EPSILON){let f=1-e;return this._w=f*o+e*this._w,this._x=f*n+e*this._x,this._y=f*s+e*this._y,this._z=f*r+e*this._z,this.normalize(),this}let c=Math.sqrt(l),h=Math.atan2(c,a),d=Math.sin((1-e)*h)/c,u=Math.sin(e*h)/c;return this._w=o*d+this._w*u,this._x=n*d+this._x*u,this._y=s*d+this._y*u,this._z=r*d+this._z*u,this._onChangeCallback(),this}slerpQuaternions(t,e,n){return this.copy(t).slerp(e,n)}random(){let t=2*Math.PI*Math.random(),e=2*Math.PI*Math.random(),n=Math.random(),s=Math.sqrt(1-n),r=Math.sqrt(n);return this.set(s*Math.sin(t),s*Math.cos(t),r*Math.sin(e),r*Math.cos(e))}equals(t){return t._x===this._x&&t._y===this._y&&t._z===this._z&&t._w===this._w}fromArray(t,e=0){return this._x=t[e],this._y=t[e+1],this._z=t[e+2],this._w=t[e+3],this._onChangeCallback(),this}toArray(t=[],e=0){return t[e]=this._x,t[e+1]=this._y,t[e+2]=this._z,t[e+3]=this._w,t}fromBufferAttribute(t,e){return this._x=t.getX(e),this._y=t.getY(e),this._z=t.getZ(e),this._w=t.getW(e),this._onChangeCallback(),this}toJSON(){return this.toArray()}_onChange(t){return this._onChangeCallback=t,this}_onChangeCallback(){}*[Symbol.iterator](){yield this._x,yield this._y,yield this._z,yield this._w}},P=class i{constructor(t=0,e=0,n=0){i.prototype.isVector3=!0,this.x=t,this.y=e,this.z=n}set(t,e,n){return n===void 0&&(n=this.z),this.x=t,this.y=e,this.z=n,this}setScalar(t){return this.x=t,this.y=t,this.z=t,this}setX(t){return this.x=t,this}setY(t){return this.y=t,this}setZ(t){return this.z=t,this}setComponent(t,e){switch(t){case 0:this.x=e;break;case 1:this.y=e;break;case 2:this.z=e;break;default:throw new Error("index is out of range: "+t)}return this}getComponent(t){switch(t){case 0:return this.x;case 1:return this.y;case 2:return this.z;default:throw new Error("index is out of range: "+t)}}clone(){return new this.constructor(this.x,this.y,this.z)}copy(t){return this.x=t.x,this.y=t.y,this.z=t.z,this}add(t){return this.x+=t.x,this.y+=t.y,this.z+=t.z,this}addScalar(t){return this.x+=t,this.y+=t,this.z+=t,this}addVectors(t,e){return this.x=t.x+e.x,this.y=t.y+e.y,this.z=t.z+e.z,this}addScaledVector(t,e){return this.x+=t.x*e,this.y+=t.y*e,this.z+=t.z*e,this}sub(t){return this.x-=t.x,this.y-=t.y,this.z-=t.z,this}subScalar(t){return this.x-=t,this.y-=t,this.z-=t,this}subVectors(t,e){return this.x=t.x-e.x,this.y=t.y-e.y,this.z=t.z-e.z,this}multiply(t){return this.x*=t.x,this.y*=t.y,this.z*=t.z,this}multiplyScalar(t){return this.x*=t,this.y*=t,this.z*=t,this}multiplyVectors(t,e){return this.x=t.x*e.x,this.y=t.y*e.y,this.z=t.z*e.z,this}applyEuler(t){return this.applyQuaternion(Nc.setFromEuler(t))}applyAxisAngle(t,e){return this.applyQuaternion(Nc.setFromAxisAngle(t,e))}applyMatrix3(t){let e=this.x,n=this.y,s=this.z,r=t.elements;return this.x=r[0]*e+r[3]*n+r[6]*s,this.y=r[1]*e+r[4]*n+r[7]*s,this.z=r[2]*e+r[5]*n+r[8]*s,this}applyNormalMatrix(t){return this.applyMatrix3(t).normalize()}applyMatrix4(t){let e=this.x,n=this.y,s=this.z,r=t.elements,o=1/(r[3]*e+r[7]*n+r[11]*s+r[15]);return this.x=(r[0]*e+r[4]*n+r[8]*s+r[12])*o,this.y=(r[1]*e+r[5]*n+r[9]*s+r[13])*o,this.z=(r[2]*e+r[6]*n+r[10]*s+r[14])*o,this}applyQuaternion(t){let e=this.x,n=this.y,s=this.z,r=t.x,o=t.y,a=t.z,l=t.w,c=2*(o*s-a*n),h=2*(a*e-r*s),d=2*(r*n-o*e);return this.x=e+l*c+o*d-a*h,this.y=n+l*h+a*c-r*d,this.z=s+l*d+r*h-o*c,this}project(t){return this.applyMatrix4(t.matrixWorldInverse).applyMatrix4(t.projectionMatrix)}unproject(t){return this.applyMatrix4(t.projectionMatrixInverse).applyMatrix4(t.matrixWorld)}transformDirection(t){let e=this.x,n=this.y,s=this.z,r=t.elements;return this.x=r[0]*e+r[4]*n+r[8]*s,this.y=r[1]*e+r[5]*n+r[9]*s,this.z=r[2]*e+r[6]*n+r[10]*s,this.normalize()}divide(t){return this.x/=t.x,this.y/=t.y,this.z/=t.z,this}divideScalar(t){return this.multiplyScalar(1/t)}min(t){return this.x=Math.min(this.x,t.x),this.y=Math.min(this.y,t.y),this.z=Math.min(this.z,t.z),this}max(t){return this.x=Math.max(this.x,t.x),this.y=Math.max(this.y,t.y),this.z=Math.max(this.z,t.z),this}clamp(t,e){return this.x=Math.max(t.x,Math.min(e.x,this.x)),this.y=Math.max(t.y,Math.min(e.y,this.y)),this.z=Math.max(t.z,Math.min(e.z,this.z)),this}clampScalar(t,e){return this.x=Math.max(t,Math.min(e,this.x)),this.y=Math.max(t,Math.min(e,this.y)),this.z=Math.max(t,Math.min(e,this.z)),this}clampLength(t,e){let n=this.length();return this.divideScalar(n||1).multiplyScalar(Math.max(t,Math.min(e,n)))}floor(){return this.x=Math.floor(this.x),this.y=Math.floor(this.y),this.z=Math.floor(this.z),this}ceil(){return this.x=Math.ceil(this.x),this.y=Math.ceil(this.y),this.z=Math.ceil(this.z),this}round(){return this.x=Math.round(this.x),this.y=Math.round(this.y),this.z=Math.round(this.z),this}roundToZero(){return this.x=Math.trunc(this.x),this.y=Math.trunc(this.y),this.z=Math.trunc(this.z),this}negate(){return this.x=-this.x,this.y=-this.y,this.z=-this.z,this}dot(t){return this.x*t.x+this.y*t.y+this.z*t.z}lengthSq(){return this.x*this.x+this.y*this.y+this.z*this.z}length(){return Math.sqrt(this.x*this.x+this.y*this.y+this.z*this.z)}manhattanLength(){return Math.abs(this.x)+Math.abs(this.y)+Math.abs(this.z)}normalize(){return this.divideScalar(this.length()||1)}setLength(t){return this.normalize().multiplyScalar(t)}lerp(t,e){return this.x+=(t.x-this.x)*e,this.y+=(t.y-this.y)*e,this.z+=(t.z-this.z)*e,this}lerpVectors(t,e,n){return this.x=t.x+(e.x-t.x)*n,this.y=t.y+(e.y-t.y)*n,this.z=t.z+(e.z-t.z)*n,this}cross(t){return this.crossVectors(this,t)}crossVectors(t,e){let n=t.x,s=t.y,r=t.z,o=e.x,a=e.y,l=e.z;return this.x=s*l-r*a,this.y=r*o-n*l,this.z=n*a-s*o,this}projectOnVector(t){let e=t.lengthSq();if(e===0)return this.set(0,0,0);let n=t.dot(this)/e;return this.copy(t).multiplyScalar(n)}projectOnPlane(t){return Wo.copy(this).projectOnVector(t),this.sub(Wo)}reflect(t){return this.sub(Wo.copy(t).multiplyScalar(2*this.dot(t)))}angleTo(t){let e=Math.sqrt(this.lengthSq()*t.lengthSq());if(e===0)return Math.PI/2;let n=this.dot(t)/e;return Math.acos(Ne(n,-1,1))}distanceTo(t){return Math.sqrt(this.distanceToSquared(t))}distanceToSquared(t){let e=this.x-t.x,n=this.y-t.y,s=this.z-t.z;return e*e+n*n+s*s}manhattanDistanceTo(t){return Math.abs(this.x-t.x)+Math.abs(this.y-t.y)+Math.abs(this.z-t.z)}setFromSpherical(t){return this.setFromSphericalCoords(t.radius,t.phi,t.theta)}setFromSphericalCoords(t,e,n){let s=Math.sin(e)*t;return this.x=s*Math.sin(n),this.y=Math.cos(e)*t,this.z=s*Math.cos(n),this}setFromCylindrical(t){return this.setFromCylindricalCoords(t.radius,t.theta,t.y)}setFromCylindricalCoords(t,e,n){return this.x=t*Math.sin(e),this.y=n,this.z=t*Math.cos(e),this}setFromMatrixPosition(t){let e=t.elements;return this.x=e[12],this.y=e[13],this.z=e[14],this}setFromMatrixScale(t){let e=this.setFromMatrixColumn(t,0).length(),n=this.setFromMatrixColumn(t,1).length(),s=this.setFromMatrixColumn(t,2).length();return this.x=e,this.y=n,this.z=s,this}setFromMatrixColumn(t,e){return this.fromArray(t.elements,e*4)}setFromMatrix3Column(t,e){return this.fromArray(t.elements,e*3)}setFromEuler(t){return this.x=t._x,this.y=t._y,this.z=t._z,this}setFromColor(t){return this.x=t.r,this.y=t.g,this.z=t.b,this}equals(t){return t.x===this.x&&t.y===this.y&&t.z===this.z}fromArray(t,e=0){return this.x=t[e],this.y=t[e+1],this.z=t[e+2],this}toArray(t=[],e=0){return t[e]=this.x,t[e+1]=this.y,t[e+2]=this.z,t}fromBufferAttribute(t,e){return this.x=t.getX(e),this.y=t.getY(e),this.z=t.getZ(e),this}random(){return this.x=Math.random(),this.y=Math.random(),this.z=Math.random(),this}randomDirection(){let t=Math.random()*Math.PI*2,e=Math.random()*2-1,n=Math.sqrt(1-e*e);return this.x=n*Math.cos(t),this.y=e,this.z=n*Math.sin(t),this}*[Symbol.iterator](){yield this.x,yield this.y,yield this.z}},Wo=new P,Nc=new Hn,Pn=class{constructor(t=new P(1/0,1/0,1/0),e=new P(-1/0,-1/0,-1/0)){this.isBox3=!0,this.min=t,this.max=e}set(t,e){return this.min.copy(t),this.max.copy(e),this}setFromArray(t){this.makeEmpty();for(let e=0,n=t.length;e<n;e+=3)this.expandByPoint(wn.fromArray(t,e));return this}setFromBufferAttribute(t){this.makeEmpty();for(let e=0,n=t.count;e<n;e++)this.expandByPoint(wn.fromBufferAttribute(t,e));return this}setFromPoints(t){this.makeEmpty();for(let e=0,n=t.length;e<n;e++)this.expandByPoint(t[e]);return this}setFromCenterAndSize(t,e){let n=wn.copy(e).multiplyScalar(.5);return this.min.copy(t).sub(n),this.max.copy(t).add(n),this}setFromObject(t,e=!1){return this.makeEmpty(),this.expandByObject(t,e)}clone(){return new this.constructor().copy(this)}copy(t){return this.min.copy(t.min),this.max.copy(t.max),this}makeEmpty(){return this.min.x=this.min.y=this.min.z=1/0,this.max.x=this.max.y=this.max.z=-1/0,this}isEmpty(){return this.max.x<this.min.x||this.max.y<this.min.y||this.max.z<this.min.z}getCenter(t){return this.isEmpty()?t.set(0,0,0):t.addVectors(this.min,this.max).multiplyScalar(.5)}getSize(t){return this.isEmpty()?t.set(0,0,0):t.subVectors(this.max,this.min)}expandByPoint(t){return this.min.min(t),this.max.max(t),this}expandByVector(t){return this.min.sub(t),this.max.add(t),this}expandByScalar(t){return this.min.addScalar(-t),this.max.addScalar(t),this}expandByObject(t,e=!1){t.updateWorldMatrix(!1,!1);let n=t.geometry;if(n!==void 0){let r=n.getAttribute("position");if(e===!0&&r!==void 0&&t.isInstancedMesh!==!0)for(let o=0,a=r.count;o<a;o++)t.isMesh===!0?t.getVertexPosition(o,wn):wn.fromBufferAttribute(r,o),wn.applyMatrix4(t.matrixWorld),this.expandByPoint(wn);else t.boundingBox!==void 0?(t.boundingBox===null&&t.computeBoundingBox(),yr.copy(t.boundingBox)):(n.boundingBox===null&&n.computeBoundingBox(),yr.copy(n.boundingBox)),yr.applyMatrix4(t.matrixWorld),this.union(yr)}let s=t.children;for(let r=0,o=s.length;r<o;r++)this.expandByObject(s[r],e);return this}containsPoint(t){return t.x>=this.min.x&&t.x<=this.max.x&&t.y>=this.min.y&&t.y<=this.max.y&&t.z>=this.min.z&&t.z<=this.max.z}containsBox(t){return this.min.x<=t.min.x&&t.max.x<=this.max.x&&this.min.y<=t.min.y&&t.max.y<=this.max.y&&this.min.z<=t.min.z&&t.max.z<=this.max.z}getParameter(t,e){return e.set((t.x-this.min.x)/(this.max.x-this.min.x),(t.y-this.min.y)/(this.max.y-this.min.y),(t.z-this.min.z)/(this.max.z-this.min.z))}intersectsBox(t){return t.max.x>=this.min.x&&t.min.x<=this.max.x&&t.max.y>=this.min.y&&t.min.y<=this.max.y&&t.max.z>=this.min.z&&t.min.z<=this.max.z}intersectsSphere(t){return this.clampPoint(t.center,wn),wn.distanceToSquared(t.center)<=t.radius*t.radius}intersectsPlane(t){let e,n;return t.normal.x>0?(e=t.normal.x*this.min.x,n=t.normal.x*this.max.x):(e=t.normal.x*this.max.x,n=t.normal.x*this.min.x),t.normal.y>0?(e+=t.normal.y*this.min.y,n+=t.normal.y*this.max.y):(e+=t.normal.y*this.max.y,n+=t.normal.y*this.min.y),t.normal.z>0?(e+=t.normal.z*this.min.z,n+=t.normal.z*this.max.z):(e+=t.normal.z*this.max.z,n+=t.normal.z*this.min.z),e<=-t.constant&&n>=-t.constant}intersectsTriangle(t){if(this.isEmpty())return!1;this.getCenter(Ws),Mr.subVectors(this.max,Ws),ji.subVectors(t.a,Ws),Qi.subVectors(t.b,Ws),ts.subVectors(t.c,Ws),hi.subVectors(Qi,ji),ui.subVectors(ts,Qi),Ri.subVectors(ji,ts);let e=[0,-hi.z,hi.y,0,-ui.z,ui.y,0,-Ri.z,Ri.y,hi.z,0,-hi.x,ui.z,0,-ui.x,Ri.z,0,-Ri.x,-hi.y,hi.x,0,-ui.y,ui.x,0,-Ri.y,Ri.x,0];return!Xo(e,ji,Qi,ts,Mr)||(e=[1,0,0,0,1,0,0,0,1],!Xo(e,ji,Qi,ts,Mr))?!1:(Sr.crossVectors(hi,ui),e=[Sr.x,Sr.y,Sr.z],Xo(e,ji,Qi,ts,Mr))}clampPoint(t,e){return e.copy(t).clamp(this.min,this.max)}distanceToPoint(t){return this.clampPoint(t,wn).distanceTo(t)}getBoundingSphere(t){return this.isEmpty()?t.makeEmpty():(this.getCenter(t.center),t.radius=this.getSize(wn).length()*.5),t}intersect(t){return this.min.max(t.min),this.max.min(t.max),this.isEmpty()&&this.makeEmpty(),this}union(t){return this.min.min(t.min),this.max.max(t.max),this}applyMatrix4(t){return this.isEmpty()?this:(Zn[0].set(this.min.x,this.min.y,this.min.z).applyMatrix4(t),Zn[1].set(this.min.x,this.min.y,this.max.z).applyMatrix4(t),Zn[2].set(this.min.x,this.max.y,this.min.z).applyMatrix4(t),Zn[3].set(this.min.x,this.max.y,this.max.z).applyMatrix4(t),Zn[4].set(this.max.x,this.min.y,this.min.z).applyMatrix4(t),Zn[5].set(this.max.x,this.min.y,this.max.z).applyMatrix4(t),Zn[6].set(this.max.x,this.max.y,this.min.z).applyMatrix4(t),Zn[7].set(this.max.x,this.max.y,this.max.z).applyMatrix4(t),this.setFromPoints(Zn),this)}translate(t){return this.min.add(t),this.max.add(t),this}equals(t){return t.min.equals(this.min)&&t.max.equals(this.max)}},Zn=[new P,new P,new P,new P,new P,new P,new P,new P],wn=new P,yr=new Pn,ji=new P,Qi=new P,ts=new P,hi=new P,ui=new P,Ri=new P,Ws=new P,Mr=new P,Sr=new P,Ci=new P;function Xo(i,t,e,n,s){for(let r=0,o=i.length-3;r<=o;r+=3){Ci.fromArray(i,r);let a=s.x*Math.abs(Ci.x)+s.y*Math.abs(Ci.y)+s.z*Math.abs(Ci.z),l=t.dot(Ci),c=e.dot(Ci),h=n.dot(Ci);if(Math.max(-Math.max(l,c,h),Math.min(l,c,h))>a)return!1}return!0}var Sd=new Pn,Xs=new P,qo=new P,yi=class{constructor(t=new P,e=-1){this.isSphere=!0,this.center=t,this.radius=e}set(t,e){return this.center.copy(t),this.radius=e,this}setFromPoints(t,e){let n=this.center;e!==void 0?n.copy(e):Sd.setFromPoints(t).getCenter(n);let s=0;for(let r=0,o=t.length;r<o;r++)s=Math.max(s,n.distanceToSquared(t[r]));return this.radius=Math.sqrt(s),this}copy(t){return this.center.copy(t.center),this.radius=t.radius,this}isEmpty(){return this.radius<0}makeEmpty(){return this.center.set(0,0,0),this.radius=-1,this}containsPoint(t){return t.distanceToSquared(this.center)<=this.radius*this.radius}distanceToPoint(t){return t.distanceTo(this.center)-this.radius}intersectsSphere(t){let e=this.radius+t.radius;return t.center.distanceToSquared(this.center)<=e*e}intersectsBox(t){return t.intersectsSphere(this)}intersectsPlane(t){return Math.abs(t.distanceToPoint(this.center))<=this.radius}clampPoint(t,e){let n=this.center.distanceToSquared(t);return e.copy(t),n>this.radius*this.radius&&(e.sub(this.center).normalize(),e.multiplyScalar(this.radius).add(this.center)),e}getBoundingBox(t){return this.isEmpty()?(t.makeEmpty(),t):(t.set(this.center,this.center),t.expandByScalar(this.radius),t)}applyMatrix4(t){return this.center.applyMatrix4(t),this.radius=this.radius*t.getMaxScaleOnAxis(),this}translate(t){return this.center.add(t),this}expandByPoint(t){if(this.isEmpty())return this.center.copy(t),this.radius=0,this;Xs.subVectors(t,this.center);let e=Xs.lengthSq();if(e>this.radius*this.radius){let n=Math.sqrt(e),s=(n-this.radius)*.5;this.center.addScaledVector(Xs,s/n),this.radius+=s}return this}union(t){return t.isEmpty()?this:this.isEmpty()?(this.copy(t),this):(this.center.equals(t.center)===!0?this.radius=Math.max(this.radius,t.radius):(qo.subVectors(t.center,this.center).setLength(t.radius),this.expandByPoint(Xs.copy(t.center).add(qo)),this.expandByPoint(Xs.copy(t.center).sub(qo))),this)}equals(t){return t.center.equals(this.center)&&t.radius===this.radius}clone(){return new this.constructor().copy(this)}},Jn=new P,Yo=new P,br=new P,di=new P,$o=new P,Er=new P,Zo=new P,no=class{constructor(t=new P,e=new P(0,0,-1)){this.origin=t,this.direction=e}set(t,e){return this.origin.copy(t),this.direction.copy(e),this}copy(t){return this.origin.copy(t.origin),this.direction.copy(t.direction),this}at(t,e){return e.copy(this.origin).addScaledVector(this.direction,t)}lookAt(t){return this.direction.copy(t).sub(this.origin).normalize(),this}recast(t){return this.origin.copy(this.at(t,Jn)),this}closestPointToPoint(t,e){e.subVectors(t,this.origin);let n=e.dot(this.direction);return n<0?e.copy(this.origin):e.copy(this.origin).addScaledVector(this.direction,n)}distanceToPoint(t){return Math.sqrt(this.distanceSqToPoint(t))}distanceSqToPoint(t){let e=Jn.subVectors(t,this.origin).dot(this.direction);return e<0?this.origin.distanceToSquared(t):(Jn.copy(this.origin).addScaledVector(this.direction,e),Jn.distanceToSquared(t))}distanceSqToSegment(t,e,n,s){Yo.copy(t).add(e).multiplyScalar(.5),br.copy(e).sub(t).normalize(),di.copy(this.origin).sub(Yo);let r=t.distanceTo(e)*.5,o=-this.direction.dot(br),a=di.dot(this.direction),l=-di.dot(br),c=di.lengthSq(),h=Math.abs(1-o*o),d,u,f,g;if(h>0)if(d=o*l-a,u=o*a-l,g=r*h,d>=0)if(u>=-g)if(u<=g){let y=1/h;d*=y,u*=y,f=d*(d+o*u+2*a)+u*(o*d+u+2*l)+c}else u=r,d=Math.max(0,-(o*u+a)),f=-d*d+u*(u+2*l)+c;else u=-r,d=Math.max(0,-(o*u+a)),f=-d*d+u*(u+2*l)+c;else u<=-g?(d=Math.max(0,-(-o*r+a)),u=d>0?-r:Math.min(Math.max(-r,-l),r),f=-d*d+u*(u+2*l)+c):u<=g?(d=0,u=Math.min(Math.max(-r,-l),r),f=u*(u+2*l)+c):(d=Math.max(0,-(o*r+a)),u=d>0?r:Math.min(Math.max(-r,-l),r),f=-d*d+u*(u+2*l)+c);else u=o>0?-r:r,d=Math.max(0,-(o*u+a)),f=-d*d+u*(u+2*l)+c;return n&&n.copy(this.origin).addScaledVector(this.direction,d),s&&s.copy(Yo).addScaledVector(br,u),f}intersectSphere(t,e){Jn.subVectors(t.center,this.origin);let n=Jn.dot(this.direction),s=Jn.dot(Jn)-n*n,r=t.radius*t.radius;if(s>r)return null;let o=Math.sqrt(r-s),a=n-o,l=n+o;return l<0?null:a<0?this.at(l,e):this.at(a,e)}intersectsSphere(t){return this.distanceSqToPoint(t.center)<=t.radius*t.radius}distanceToPlane(t){let e=t.normal.dot(this.direction);if(e===0)return t.distanceToPoint(this.origin)===0?0:null;let n=-(this.origin.dot(t.normal)+t.constant)/e;return n>=0?n:null}intersectPlane(t,e){let n=this.distanceToPlane(t);return n===null?null:this.at(n,e)}intersectsPlane(t){let e=t.distanceToPoint(this.origin);return e===0||t.normal.dot(this.direction)*e<0}intersectBox(t,e){let n,s,r,o,a,l,c=1/this.direction.x,h=1/this.direction.y,d=1/this.direction.z,u=this.origin;return c>=0?(n=(t.min.x-u.x)*c,s=(t.max.x-u.x)*c):(n=(t.max.x-u.x)*c,s=(t.min.x-u.x)*c),h>=0?(r=(t.min.y-u.y)*h,o=(t.max.y-u.y)*h):(r=(t.max.y-u.y)*h,o=(t.min.y-u.y)*h),n>o||r>s||((r>n||isNaN(n))&&(n=r),(o<s||isNaN(s))&&(s=o),d>=0?(a=(t.min.z-u.z)*d,l=(t.max.z-u.z)*d):(a=(t.max.z-u.z)*d,l=(t.min.z-u.z)*d),n>l||a>s)||((a>n||n!==n)&&(n=a),(l<s||s!==s)&&(s=l),s<0)?null:this.at(n>=0?n:s,e)}intersectsBox(t){return this.intersectBox(t,Jn)!==null}intersectTriangle(t,e,n,s,r){$o.subVectors(e,t),Er.subVectors(n,t),Zo.crossVectors($o,Er);let o=this.direction.dot(Zo),a;if(o>0){if(s)return null;a=1}else if(o<0)a=-1,o=-o;else return null;di.subVectors(this.origin,t);let l=a*this.direction.dot(Er.crossVectors(di,Er));if(l<0)return null;let c=a*this.direction.dot($o.cross(di));if(c<0||l+c>o)return null;let h=-a*di.dot(Zo);return h<0?null:this.at(h/o,r)}applyMatrix4(t){return this.origin.applyMatrix4(t),this.direction.transformDirection(t),this}equals(t){return t.origin.equals(this.origin)&&t.direction.equals(this.direction)}clone(){return new this.constructor().copy(this)}},ne=class i{constructor(t,e,n,s,r,o,a,l,c,h,d,u,f,g,y,m){i.prototype.isMatrix4=!0,this.elements=[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1],t!==void 0&&this.set(t,e,n,s,r,o,a,l,c,h,d,u,f,g,y,m)}set(t,e,n,s,r,o,a,l,c,h,d,u,f,g,y,m){let p=this.elements;return p[0]=t,p[4]=e,p[8]=n,p[12]=s,p[1]=r,p[5]=o,p[9]=a,p[13]=l,p[2]=c,p[6]=h,p[10]=d,p[14]=u,p[3]=f,p[7]=g,p[11]=y,p[15]=m,this}identity(){return this.set(1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1),this}clone(){return new i().fromArray(this.elements)}copy(t){let e=this.elements,n=t.elements;return e[0]=n[0],e[1]=n[1],e[2]=n[2],e[3]=n[3],e[4]=n[4],e[5]=n[5],e[6]=n[6],e[7]=n[7],e[8]=n[8],e[9]=n[9],e[10]=n[10],e[11]=n[11],e[12]=n[12],e[13]=n[13],e[14]=n[14],e[15]=n[15],this}copyPosition(t){let e=this.elements,n=t.elements;return e[12]=n[12],e[13]=n[13],e[14]=n[14],this}setFromMatrix3(t){let e=t.elements;return this.set(e[0],e[3],e[6],0,e[1],e[4],e[7],0,e[2],e[5],e[8],0,0,0,0,1),this}extractBasis(t,e,n){return t.setFromMatrixColumn(this,0),e.setFromMatrixColumn(this,1),n.setFromMatrixColumn(this,2),this}makeBasis(t,e,n){return this.set(t.x,e.x,n.x,0,t.y,e.y,n.y,0,t.z,e.z,n.z,0,0,0,0,1),this}extractRotation(t){let e=this.elements,n=t.elements,s=1/es.setFromMatrixColumn(t,0).length(),r=1/es.setFromMatrixColumn(t,1).length(),o=1/es.setFromMatrixColumn(t,2).length();return e[0]=n[0]*s,e[1]=n[1]*s,e[2]=n[2]*s,e[3]=0,e[4]=n[4]*r,e[5]=n[5]*r,e[6]=n[6]*r,e[7]=0,e[8]=n[8]*o,e[9]=n[9]*o,e[10]=n[10]*o,e[11]=0,e[12]=0,e[13]=0,e[14]=0,e[15]=1,this}makeRotationFromEuler(t){let e=this.elements,n=t.x,s=t.y,r=t.z,o=Math.cos(n),a=Math.sin(n),l=Math.cos(s),c=Math.sin(s),h=Math.cos(r),d=Math.sin(r);if(t.order==="XYZ"){let u=o*h,f=o*d,g=a*h,y=a*d;e[0]=l*h,e[4]=-l*d,e[8]=c,e[1]=f+g*c,e[5]=u-y*c,e[9]=-a*l,e[2]=y-u*c,e[6]=g+f*c,e[10]=o*l}else if(t.order==="YXZ"){let u=l*h,f=l*d,g=c*h,y=c*d;e[0]=u+y*a,e[4]=g*a-f,e[8]=o*c,e[1]=o*d,e[5]=o*h,e[9]=-a,e[2]=f*a-g,e[6]=y+u*a,e[10]=o*l}else if(t.order==="ZXY"){let u=l*h,f=l*d,g=c*h,y=c*d;e[0]=u-y*a,e[4]=-o*d,e[8]=g+f*a,e[1]=f+g*a,e[5]=o*h,e[9]=y-u*a,e[2]=-o*c,e[6]=a,e[10]=o*l}else if(t.order==="ZYX"){let u=o*h,f=o*d,g=a*h,y=a*d;e[0]=l*h,e[4]=g*c-f,e[8]=u*c+y,e[1]=l*d,e[5]=y*c+u,e[9]=f*c-g,e[2]=-c,e[6]=a*l,e[10]=o*l}else if(t.order==="YZX"){let u=o*l,f=o*c,g=a*l,y=a*c;e[0]=l*h,e[4]=y-u*d,e[8]=g*d+f,e[1]=d,e[5]=o*h,e[9]=-a*h,e[2]=-c*h,e[6]=f*d+g,e[10]=u-y*d}else if(t.order==="XZY"){let u=o*l,f=o*c,g=a*l,y=a*c;e[0]=l*h,e[4]=-d,e[8]=c*h,e[1]=u*d+y,e[5]=o*h,e[9]=f*d-g,e[2]=g*d-f,e[6]=a*h,e[10]=y*d+u}return e[3]=0,e[7]=0,e[11]=0,e[12]=0,e[13]=0,e[14]=0,e[15]=1,this}makeRotationFromQuaternion(t){return this.compose(bd,t,Ed)}lookAt(t,e,n){let s=this.elements;return cn.subVectors(t,e),cn.lengthSq()===0&&(cn.z=1),cn.normalize(),fi.crossVectors(n,cn),fi.lengthSq()===0&&(Math.abs(n.z)===1?cn.x+=1e-4:cn.z+=1e-4,cn.normalize(),fi.crossVectors(n,cn)),fi.normalize(),wr.crossVectors(cn,fi),s[0]=fi.x,s[4]=wr.x,s[8]=cn.x,s[1]=fi.y,s[5]=wr.y,s[9]=cn.y,s[2]=fi.z,s[6]=wr.z,s[10]=cn.z,this}multiply(t){return this.multiplyMatrices(this,t)}premultiply(t){return this.multiplyMatrices(t,this)}multiplyMatrices(t,e){let n=t.elements,s=e.elements,r=this.elements,o=n[0],a=n[4],l=n[8],c=n[12],h=n[1],d=n[5],u=n[9],f=n[13],g=n[2],y=n[6],m=n[10],p=n[14],w=n[3],E=n[7],M=n[11],U=n[15],C=s[0],v=s[4],b=s[8],x=s[12],_=s[1],R=s[5],I=s[9],L=s[13],N=s[2],V=s[6],F=s[10],X=s[14],z=s[3],j=s[7],it=s[11],st=s[15];return r[0]=o*C+a*_+l*N+c*z,r[4]=o*v+a*R+l*V+c*j,r[8]=o*b+a*I+l*F+c*it,r[12]=o*x+a*L+l*X+c*st,r[1]=h*C+d*_+u*N+f*z,r[5]=h*v+d*R+u*V+f*j,r[9]=h*b+d*I+u*F+f*it,r[13]=h*x+d*L+u*X+f*st,r[2]=g*C+y*_+m*N+p*z,r[6]=g*v+y*R+m*V+p*j,r[10]=g*b+y*I+m*F+p*it,r[14]=g*x+y*L+m*X+p*st,r[3]=w*C+E*_+M*N+U*z,r[7]=w*v+E*R+M*V+U*j,r[11]=w*b+E*I+M*F+U*it,r[15]=w*x+E*L+M*X+U*st,this}multiplyScalar(t){let e=this.elements;return e[0]*=t,e[4]*=t,e[8]*=t,e[12]*=t,e[1]*=t,e[5]*=t,e[9]*=t,e[13]*=t,e[2]*=t,e[6]*=t,e[10]*=t,e[14]*=t,e[3]*=t,e[7]*=t,e[11]*=t,e[15]*=t,this}determinant(){let t=this.elements,e=t[0],n=t[4],s=t[8],r=t[12],o=t[1],a=t[5],l=t[9],c=t[13],h=t[2],d=t[6],u=t[10],f=t[14],g=t[3],y=t[7],m=t[11],p=t[15];return g*(+r*l*d-s*c*d-r*a*u+n*c*u+s*a*f-n*l*f)+y*(+e*l*f-e*c*u+r*o*u-s*o*f+s*c*h-r*l*h)+m*(+e*c*d-e*a*f-r*o*d+n*o*f+r*a*h-n*c*h)+p*(-s*a*h-e*l*d+e*a*u+s*o*d-n*o*u+n*l*h)}transpose(){let t=this.elements,e;return e=t[1],t[1]=t[4],t[4]=e,e=t[2],t[2]=t[8],t[8]=e,e=t[6],t[6]=t[9],t[9]=e,e=t[3],t[3]=t[12],t[12]=e,e=t[7],t[7]=t[13],t[13]=e,e=t[11],t[11]=t[14],t[14]=e,this}setPosition(t,e,n){let s=this.elements;return t.isVector3?(s[12]=t.x,s[13]=t.y,s[14]=t.z):(s[12]=t,s[13]=e,s[14]=n),this}invert(){let t=this.elements,e=t[0],n=t[1],s=t[2],r=t[3],o=t[4],a=t[5],l=t[6],c=t[7],h=t[8],d=t[9],u=t[10],f=t[11],g=t[12],y=t[13],m=t[14],p=t[15],w=d*m*c-y*u*c+y*l*f-a*m*f-d*l*p+a*u*p,E=g*u*c-h*m*c-g*l*f+o*m*f+h*l*p-o*u*p,M=h*y*c-g*d*c+g*a*f-o*y*f-h*a*p+o*d*p,U=g*d*l-h*y*l-g*a*u+o*y*u+h*a*m-o*d*m,C=e*w+n*E+s*M+r*U;if(C===0)return this.set(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0);let v=1/C;return t[0]=w*v,t[1]=(y*u*r-d*m*r-y*s*f+n*m*f+d*s*p-n*u*p)*v,t[2]=(a*m*r-y*l*r+y*s*c-n*m*c-a*s*p+n*l*p)*v,t[3]=(d*l*r-a*u*r-d*s*c+n*u*c+a*s*f-n*l*f)*v,t[4]=E*v,t[5]=(h*m*r-g*u*r+g*s*f-e*m*f-h*s*p+e*u*p)*v,t[6]=(g*l*r-o*m*r-g*s*c+e*m*c+o*s*p-e*l*p)*v,t[7]=(o*u*r-h*l*r+h*s*c-e*u*c-o*s*f+e*l*f)*v,t[8]=M*v,t[9]=(g*d*r-h*y*r-g*n*f+e*y*f+h*n*p-e*d*p)*v,t[10]=(o*y*r-g*a*r+g*n*c-e*y*c-o*n*p+e*a*p)*v,t[11]=(h*a*r-o*d*r-h*n*c+e*d*c+o*n*f-e*a*f)*v,t[12]=U*v,t[13]=(h*y*s-g*d*s+g*n*u-e*y*u-h*n*m+e*d*m)*v,t[14]=(g*a*s-o*y*s-g*n*l+e*y*l+o*n*m-e*a*m)*v,t[15]=(o*d*s-h*a*s+h*n*l-e*d*l-o*n*u+e*a*u)*v,this}scale(t){let e=this.elements,n=t.x,s=t.y,r=t.z;return e[0]*=n,e[4]*=s,e[8]*=r,e[1]*=n,e[5]*=s,e[9]*=r,e[2]*=n,e[6]*=s,e[10]*=r,e[3]*=n,e[7]*=s,e[11]*=r,this}getMaxScaleOnAxis(){let t=this.elements,e=t[0]*t[0]+t[1]*t[1]+t[2]*t[2],n=t[4]*t[4]+t[5]*t[5]+t[6]*t[6],s=t[8]*t[8]+t[9]*t[9]+t[10]*t[10];return Math.sqrt(Math.max(e,n,s))}makeTranslation(t,e,n){return t.isVector3?this.set(1,0,0,t.x,0,1,0,t.y,0,0,1,t.z,0,0,0,1):this.set(1,0,0,t,0,1,0,e,0,0,1,n,0,0,0,1),this}makeRotationX(t){let e=Math.cos(t),n=Math.sin(t);return this.set(1,0,0,0,0,e,-n,0,0,n,e,0,0,0,0,1),this}makeRotationY(t){let e=Math.cos(t),n=Math.sin(t);return this.set(e,0,n,0,0,1,0,0,-n,0,e,0,0,0,0,1),this}makeRotationZ(t){let e=Math.cos(t),n=Math.sin(t);return this.set(e,-n,0,0,n,e,0,0,0,0,1,0,0,0,0,1),this}makeRotationAxis(t,e){let n=Math.cos(e),s=Math.sin(e),r=1-n,o=t.x,a=t.y,l=t.z,c=r*o,h=r*a;return this.set(c*o+n,c*a-s*l,c*l+s*a,0,c*a+s*l,h*a+n,h*l-s*o,0,c*l-s*a,h*l+s*o,r*l*l+n,0,0,0,0,1),this}makeScale(t,e,n){return this.set(t,0,0,0,0,e,0,0,0,0,n,0,0,0,0,1),this}makeShear(t,e,n,s,r,o){return this.set(1,n,r,0,t,1,o,0,e,s,1,0,0,0,0,1),this}compose(t,e,n){let s=this.elements,r=e._x,o=e._y,a=e._z,l=e._w,c=r+r,h=o+o,d=a+a,u=r*c,f=r*h,g=r*d,y=o*h,m=o*d,p=a*d,w=l*c,E=l*h,M=l*d,U=n.x,C=n.y,v=n.z;return s[0]=(1-(y+p))*U,s[1]=(f+M)*U,s[2]=(g-E)*U,s[3]=0,s[4]=(f-M)*C,s[5]=(1-(u+p))*C,s[6]=(m+w)*C,s[7]=0,s[8]=(g+E)*v,s[9]=(m-w)*v,s[10]=(1-(u+y))*v,s[11]=0,s[12]=t.x,s[13]=t.y,s[14]=t.z,s[15]=1,this}decompose(t,e,n){let s=this.elements,r=es.set(s[0],s[1],s[2]).length(),o=es.set(s[4],s[5],s[6]).length(),a=es.set(s[8],s[9],s[10]).length();this.determinant()<0&&(r=-r),t.x=s[12],t.y=s[13],t.z=s[14],Tn.copy(this);let c=1/r,h=1/o,d=1/a;return Tn.elements[0]*=c,Tn.elements[1]*=c,Tn.elements[2]*=c,Tn.elements[4]*=h,Tn.elements[5]*=h,Tn.elements[6]*=h,Tn.elements[8]*=d,Tn.elements[9]*=d,Tn.elements[10]*=d,e.setFromRotationMatrix(Tn),n.x=r,n.y=o,n.z=a,this}makePerspective(t,e,n,s,r,o,a=ii){let l=this.elements,c=2*r/(e-t),h=2*r/(n-s),d=(e+t)/(e-t),u=(n+s)/(n-s),f,g;if(a===ii)f=-(o+r)/(o-r),g=-2*o*r/(o-r);else if(a===jr)f=-o/(o-r),g=-o*r/(o-r);else throw new Error("THREE.Matrix4.makePerspective(): Invalid coordinate system: "+a);return l[0]=c,l[4]=0,l[8]=d,l[12]=0,l[1]=0,l[5]=h,l[9]=u,l[13]=0,l[2]=0,l[6]=0,l[10]=f,l[14]=g,l[3]=0,l[7]=0,l[11]=-1,l[15]=0,this}makeOrthographic(t,e,n,s,r,o,a=ii){let l=this.elements,c=1/(e-t),h=1/(n-s),d=1/(o-r),u=(e+t)*c,f=(n+s)*h,g,y;if(a===ii)g=(o+r)*d,y=-2*d;else if(a===jr)g=r*d,y=-1*d;else throw new Error("THREE.Matrix4.makeOrthographic(): Invalid coordinate system: "+a);return l[0]=2*c,l[4]=0,l[8]=0,l[12]=-u,l[1]=0,l[5]=2*h,l[9]=0,l[13]=-f,l[2]=0,l[6]=0,l[10]=y,l[14]=-g,l[3]=0,l[7]=0,l[11]=0,l[15]=1,this}equals(t){let e=this.elements,n=t.elements;for(let s=0;s<16;s++)if(e[s]!==n[s])return!1;return!0}fromArray(t,e=0){for(let n=0;n<16;n++)this.elements[n]=t[n+e];return this}toArray(t=[],e=0){let n=this.elements;return t[e]=n[0],t[e+1]=n[1],t[e+2]=n[2],t[e+3]=n[3],t[e+4]=n[4],t[e+5]=n[5],t[e+6]=n[6],t[e+7]=n[7],t[e+8]=n[8],t[e+9]=n[9],t[e+10]=n[10],t[e+11]=n[11],t[e+12]=n[12],t[e+13]=n[13],t[e+14]=n[14],t[e+15]=n[15],t}},es=new P,Tn=new ne,bd=new P(0,0,0),Ed=new P(1,1,1),fi=new P,wr=new P,cn=new P,Fc=new ne,Oc=new Hn,gn=class i{constructor(t=0,e=0,n=0,s=i.DEFAULT_ORDER){this.isEuler=!0,this._x=t,this._y=e,this._z=n,this._order=s}get x(){return this._x}set x(t){this._x=t,this._onChangeCallback()}get y(){return this._y}set y(t){this._y=t,this._onChangeCallback()}get z(){return this._z}set z(t){this._z=t,this._onChangeCallback()}get order(){return this._order}set order(t){this._order=t,this._onChangeCallback()}set(t,e,n,s=this._order){return this._x=t,this._y=e,this._z=n,this._order=s,this._onChangeCallback(),this}clone(){return new this.constructor(this._x,this._y,this._z,this._order)}copy(t){return this._x=t._x,this._y=t._y,this._z=t._z,this._order=t._order,this._onChangeCallback(),this}setFromRotationMatrix(t,e=this._order,n=!0){let s=t.elements,r=s[0],o=s[4],a=s[8],l=s[1],c=s[5],h=s[9],d=s[2],u=s[6],f=s[10];switch(e){case"XYZ":this._y=Math.asin(Ne(a,-1,1)),Math.abs(a)<.9999999?(this._x=Math.atan2(-h,f),this._z=Math.atan2(-o,r)):(this._x=Math.atan2(u,c),this._z=0);break;case"YXZ":this._x=Math.asin(-Ne(h,-1,1)),Math.abs(h)<.9999999?(this._y=Math.atan2(a,f),this._z=Math.atan2(l,c)):(this._y=Math.atan2(-d,r),this._z=0);break;case"ZXY":this._x=Math.asin(Ne(u,-1,1)),Math.abs(u)<.9999999?(this._y=Math.atan2(-d,f),this._z=Math.atan2(-o,c)):(this._y=0,this._z=Math.atan2(l,r));break;case"ZYX":this._y=Math.asin(-Ne(d,-1,1)),Math.abs(d)<.9999999?(this._x=Math.atan2(u,f),this._z=Math.atan2(l,r)):(this._x=0,this._z=Math.atan2(-o,c));break;case"YZX":this._z=Math.asin(Ne(l,-1,1)),Math.abs(l)<.9999999?(this._x=Math.atan2(-h,c),this._y=Math.atan2(-d,r)):(this._x=0,this._y=Math.atan2(a,f));break;case"XZY":this._z=Math.asin(-Ne(o,-1,1)),Math.abs(o)<.9999999?(this._x=Math.atan2(u,c),this._y=Math.atan2(a,r)):(this._x=Math.atan2(-h,f),this._y=0);break;default:console.warn("THREE.Euler: .setFromRotationMatrix() encountered an unknown order: "+e)}return this._order=e,n===!0&&this._onChangeCallback(),this}setFromQuaternion(t,e,n){return Fc.makeRotationFromQuaternion(t),this.setFromRotationMatrix(Fc,e,n)}setFromVector3(t,e=this._order){return this.set(t.x,t.y,t.z,e)}reorder(t){return Oc.setFromEuler(this),this.setFromQuaternion(Oc,t)}equals(t){return t._x===this._x&&t._y===this._y&&t._z===this._z&&t._order===this._order}fromArray(t){return this._x=t[0],this._y=t[1],this._z=t[2],t[3]!==void 0&&(this._order=t[3]),this._onChangeCallback(),this}toArray(t=[],e=0){return t[e]=this._x,t[e+1]=this._y,t[e+2]=this._z,t[e+3]=this._order,t}_onChange(t){return this._onChangeCallback=t,this}_onChangeCallback(){}*[Symbol.iterator](){yield this._x,yield this._y,yield this._z,yield this._order}};gn.DEFAULT_ORDER="XYZ";var io=class{constructor(){this.mask=1}set(t){this.mask=(1<<t|0)>>>0}enable(t){this.mask|=1<<t|0}enableAll(){this.mask=-1}toggle(t){this.mask^=1<<t|0}disable(t){this.mask&=~(1<<t|0)}disableAll(){this.mask=0}test(t){return(this.mask&t.mask)!==0}isEnabled(t){return(this.mask&(1<<t|0))!==0}},wd=0,Bc=new P,ns=new Hn,Kn=new ne,Tr=new P,qs=new P,Td=new P,Ad=new Hn,zc=new P(1,0,0),Hc=new P(0,1,0),kc=new P(0,0,1),Vc={type:"added"},Rd={type:"removed"},is={type:"childadded",child:null},Jo={type:"childremoved",child:null},Ze=class i extends _i{constructor(){super(),this.isObject3D=!0,Object.defineProperty(this,"id",{value:wd++}),this.uuid=lr(),this.name="",this.type="Object3D",this.parent=null,this.children=[],this.up=i.DEFAULT_UP.clone();let t=new P,e=new gn,n=new Hn,s=new P(1,1,1);function r(){n.setFromEuler(e,!1)}function o(){e.setFromQuaternion(n,void 0,!1)}e._onChange(r),n._onChange(o),Object.defineProperties(this,{position:{configurable:!0,enumerable:!0,value:t},rotation:{configurable:!0,enumerable:!0,value:e},quaternion:{configurable:!0,enumerable:!0,value:n},scale:{configurable:!0,enumerable:!0,value:s},modelViewMatrix:{value:new ne},normalMatrix:{value:new Ot}}),this.matrix=new ne,this.matrixWorld=new ne,this.matrixAutoUpdate=i.DEFAULT_MATRIX_AUTO_UPDATE,this.matrixWorldAutoUpdate=i.DEFAULT_MATRIX_WORLD_AUTO_UPDATE,this.matrixWorldNeedsUpdate=!1,this.layers=new io,this.visible=!0,this.castShadow=!1,this.receiveShadow=!1,this.frustumCulled=!0,this.renderOrder=0,this.animations=[],this.userData={}}onBeforeShadow(){}onAfterShadow(){}onBeforeRender(){}onAfterRender(){}applyMatrix4(t){this.matrixAutoUpdate&&this.updateMatrix(),this.matrix.premultiply(t),this.matrix.decompose(this.position,this.quaternion,this.scale)}applyQuaternion(t){return this.quaternion.premultiply(t),this}setRotationFromAxisAngle(t,e){this.quaternion.setFromAxisAngle(t,e)}setRotationFromEuler(t){this.quaternion.setFromEuler(t,!0)}setRotationFromMatrix(t){this.quaternion.setFromRotationMatrix(t)}setRotationFromQuaternion(t){this.quaternion.copy(t)}rotateOnAxis(t,e){return ns.setFromAxisAngle(t,e),this.quaternion.multiply(ns),this}rotateOnWorldAxis(t,e){return ns.setFromAxisAngle(t,e),this.quaternion.premultiply(ns),this}rotateX(t){return this.rotateOnAxis(zc,t)}rotateY(t){return this.rotateOnAxis(Hc,t)}rotateZ(t){return this.rotateOnAxis(kc,t)}translateOnAxis(t,e){return Bc.copy(t).applyQuaternion(this.quaternion),this.position.add(Bc.multiplyScalar(e)),this}translateX(t){return this.translateOnAxis(zc,t)}translateY(t){return this.translateOnAxis(Hc,t)}translateZ(t){return this.translateOnAxis(kc,t)}localToWorld(t){return this.updateWorldMatrix(!0,!1),t.applyMatrix4(this.matrixWorld)}worldToLocal(t){return this.updateWorldMatrix(!0,!1),t.applyMatrix4(Kn.copy(this.matrixWorld).invert())}lookAt(t,e,n){t.isVector3?Tr.copy(t):Tr.set(t,e,n);let s=this.parent;this.updateWorldMatrix(!0,!1),qs.setFromMatrixPosition(this.matrixWorld),this.isCamera||this.isLight?Kn.lookAt(qs,Tr,this.up):Kn.lookAt(Tr,qs,this.up),this.quaternion.setFromRotationMatrix(Kn),s&&(Kn.extractRotation(s.matrixWorld),ns.setFromRotationMatrix(Kn),this.quaternion.premultiply(ns.invert()))}add(t){if(arguments.length>1){for(let e=0;e<arguments.length;e++)this.add(arguments[e]);return this}return t===this?(console.error("THREE.Object3D.add: object can't be added as a child of itself.",t),this):(t&&t.isObject3D?(t.removeFromParent(),t.parent=this,this.children.push(t),t.dispatchEvent(Vc),is.child=t,this.dispatchEvent(is),is.child=null):console.error("THREE.Object3D.add: object not an instance of THREE.Object3D.",t),this)}remove(t){if(arguments.length>1){for(let n=0;n<arguments.length;n++)this.remove(arguments[n]);return this}let e=this.children.indexOf(t);return e!==-1&&(t.parent=null,this.children.splice(e,1),t.dispatchEvent(Rd),Jo.child=t,this.dispatchEvent(Jo),Jo.child=null),this}removeFromParent(){let t=this.parent;return t!==null&&t.remove(this),this}clear(){return this.remove(...this.children)}attach(t){return this.updateWorldMatrix(!0,!1),Kn.copy(this.matrixWorld).invert(),t.parent!==null&&(t.parent.updateWorldMatrix(!0,!1),Kn.multiply(t.parent.matrixWorld)),t.applyMatrix4(Kn),t.removeFromParent(),t.parent=this,this.children.push(t),t.updateWorldMatrix(!1,!0),t.dispatchEvent(Vc),is.child=t,this.dispatchEvent(is),is.child=null,this}getObjectById(t){return this.getObjectByProperty("id",t)}getObjectByName(t){return this.getObjectByProperty("name",t)}getObjectByProperty(t,e){if(this[t]===e)return this;for(let n=0,s=this.children.length;n<s;n++){let o=this.children[n].getObjectByProperty(t,e);if(o!==void 0)return o}}getObjectsByProperty(t,e,n=[]){this[t]===e&&n.push(this);let s=this.children;for(let r=0,o=s.length;r<o;r++)s[r].getObjectsByProperty(t,e,n);return n}getWorldPosition(t){return this.updateWorldMatrix(!0,!1),t.setFromMatrixPosition(this.matrixWorld)}getWorldQuaternion(t){return this.updateWorldMatrix(!0,!1),this.matrixWorld.decompose(qs,t,Td),t}getWorldScale(t){return this.updateWorldMatrix(!0,!1),this.matrixWorld.decompose(qs,Ad,t),t}getWorldDirection(t){this.updateWorldMatrix(!0,!1);let e=this.matrixWorld.elements;return t.set(e[8],e[9],e[10]).normalize()}raycast(){}traverse(t){t(this);let e=this.children;for(let n=0,s=e.length;n<s;n++)e[n].traverse(t)}traverseVisible(t){if(this.visible===!1)return;t(this);let e=this.children;for(let n=0,s=e.length;n<s;n++)e[n].traverseVisible(t)}traverseAncestors(t){let e=this.parent;e!==null&&(t(e),e.traverseAncestors(t))}updateMatrix(){this.matrix.compose(this.position,this.quaternion,this.scale),this.matrixWorldNeedsUpdate=!0}updateMatrixWorld(t){this.matrixAutoUpdate&&this.updateMatrix(),(this.matrixWorldNeedsUpdate||t)&&(this.matrixWorldAutoUpdate===!0&&(this.parent===null?this.matrixWorld.copy(this.matrix):this.matrixWorld.multiplyMatrices(this.parent.matrixWorld,this.matrix)),this.matrixWorldNeedsUpdate=!1,t=!0);let e=this.children;for(let n=0,s=e.length;n<s;n++)e[n].updateMatrixWorld(t)}updateWorldMatrix(t,e){let n=this.parent;if(t===!0&&n!==null&&n.updateWorldMatrix(!0,!1),this.matrixAutoUpdate&&this.updateMatrix(),this.matrixWorldAutoUpdate===!0&&(this.parent===null?this.matrixWorld.copy(this.matrix):this.matrixWorld.multiplyMatrices(this.parent.matrixWorld,this.matrix)),e===!0){let s=this.children;for(let r=0,o=s.length;r<o;r++)s[r].updateWorldMatrix(!1,!0)}}toJSON(t){let e=t===void 0||typeof t=="string",n={};e&&(t={geometries:{},materials:{},textures:{},images:{},shapes:{},skeletons:{},animations:{},nodes:{}},n.metadata={version:4.6,type:"Object",generator:"Object3D.toJSON"});let s={};s.uuid=this.uuid,s.type=this.type,this.name!==""&&(s.name=this.name),this.castShadow===!0&&(s.castShadow=!0),this.receiveShadow===!0&&(s.receiveShadow=!0),this.visible===!1&&(s.visible=!1),this.frustumCulled===!1&&(s.frustumCulled=!1),this.renderOrder!==0&&(s.renderOrder=this.renderOrder),Object.keys(this.userData).length>0&&(s.userData=this.userData),s.layers=this.layers.mask,s.matrix=this.matrix.toArray(),s.up=this.up.toArray(),this.matrixAutoUpdate===!1&&(s.matrixAutoUpdate=!1),this.isInstancedMesh&&(s.type="InstancedMesh",s.count=this.count,s.instanceMatrix=this.instanceMatrix.toJSON(),this.instanceColor!==null&&(s.instanceColor=this.instanceColor.toJSON())),this.isBatchedMesh&&(s.type="BatchedMesh",s.perObjectFrustumCulled=this.perObjectFrustumCulled,s.sortObjects=this.sortObjects,s.drawRanges=this._drawRanges,s.reservedRanges=this._reservedRanges,s.visibility=this._visibility,s.active=this._active,s.bounds=this._bounds.map(a=>({boxInitialized:a.boxInitialized,boxMin:a.box.min.toArray(),boxMax:a.box.max.toArray(),sphereInitialized:a.sphereInitialized,sphereRadius:a.sphere.radius,sphereCenter:a.sphere.center.toArray()})),s.maxInstanceCount=this._maxInstanceCount,s.maxVertexCount=this._maxVertexCount,s.maxIndexCount=this._maxIndexCount,s.geometryInitialized=this._geometryInitialized,s.geometryCount=this._geometryCount,s.matricesTexture=this._matricesTexture.toJSON(t),this._colorsTexture!==null&&(s.colorsTexture=this._colorsTexture.toJSON(t)),this.boundingSphere!==null&&(s.boundingSphere={center:s.boundingSphere.center.toArray(),radius:s.boundingSphere.radius}),this.boundingBox!==null&&(s.boundingBox={min:s.boundingBox.min.toArray(),max:s.boundingBox.max.toArray()}));function r(a,l){return a[l.uuid]===void 0&&(a[l.uuid]=l.toJSON(t)),l.uuid}if(this.isScene)this.background&&(this.background.isColor?s.background=this.background.toJSON():this.background.isTexture&&(s.background=this.background.toJSON(t).uuid)),this.environment&&this.environment.isTexture&&this.environment.isRenderTargetTexture!==!0&&(s.environment=this.environment.toJSON(t).uuid);else if(this.isMesh||this.isLine||this.isPoints){s.geometry=r(t.geometries,this.geometry);let a=this.geometry.parameters;if(a!==void 0&&a.shapes!==void 0){let l=a.shapes;if(Array.isArray(l))for(let c=0,h=l.length;c<h;c++){let d=l[c];r(t.shapes,d)}else r(t.shapes,l)}}if(this.isSkinnedMesh&&(s.bindMode=this.bindMode,s.bindMatrix=this.bindMatrix.toArray(),this.skeleton!==void 0&&(r(t.skeletons,this.skeleton),s.skeleton=this.skeleton.uuid)),this.material!==void 0)if(Array.isArray(this.material)){let a=[];for(let l=0,c=this.material.length;l<c;l++)a.push(r(t.materials,this.material[l]));s.material=a}else s.material=r(t.materials,this.material);if(this.children.length>0){s.children=[];for(let a=0;a<this.children.length;a++)s.children.push(this.children[a].toJSON(t).object)}if(this.animations.length>0){s.animations=[];for(let a=0;a<this.animations.length;a++){let l=this.animations[a];s.animations.push(r(t.animations,l))}}if(e){let a=o(t.geometries),l=o(t.materials),c=o(t.textures),h=o(t.images),d=o(t.shapes),u=o(t.skeletons),f=o(t.animations),g=o(t.nodes);a.length>0&&(n.geometries=a),l.length>0&&(n.materials=l),c.length>0&&(n.textures=c),h.length>0&&(n.images=h),d.length>0&&(n.shapes=d),u.length>0&&(n.skeletons=u),f.length>0&&(n.animations=f),g.length>0&&(n.nodes=g)}return n.object=s,n;function o(a){let l=[];for(let c in a){let h=a[c];delete h.metadata,l.push(h)}return l}}clone(t){return new this.constructor().copy(this,t)}copy(t,e=!0){if(this.name=t.name,this.up.copy(t.up),this.position.copy(t.position),this.rotation.order=t.rotation.order,this.quaternion.copy(t.quaternion),this.scale.copy(t.scale),this.matrix.copy(t.matrix),this.matrixWorld.copy(t.matrixWorld),this.matrixAutoUpdate=t.matrixAutoUpdate,this.matrixWorldAutoUpdate=t.matrixWorldAutoUpdate,this.matrixWorldNeedsUpdate=t.matrixWorldNeedsUpdate,this.layers.mask=t.layers.mask,this.visible=t.visible,this.castShadow=t.castShadow,this.receiveShadow=t.receiveShadow,this.frustumCulled=t.frustumCulled,this.renderOrder=t.renderOrder,this.animations=t.animations.slice(),this.userData=JSON.parse(JSON.stringify(t.userData)),e===!0)for(let n=0;n<t.children.length;n++){let s=t.children[n];this.add(s.clone())}return this}};Ze.DEFAULT_UP=new P(0,1,0);Ze.DEFAULT_MATRIX_AUTO_UPDATE=!0;Ze.DEFAULT_MATRIX_WORLD_AUTO_UPDATE=!0;var An=new P,jn=new P,Ko=new P,Qn=new P,ss=new P,rs=new P,Gc=new P,jo=new P,Qo=new P,ta=new P,ea=new re,na=new re,ia=new re,Fi=class i{constructor(t=new P,e=new P,n=new P){this.a=t,this.b=e,this.c=n}static getNormal(t,e,n,s){s.subVectors(n,e),An.subVectors(t,e),s.cross(An);let r=s.lengthSq();return r>0?s.multiplyScalar(1/Math.sqrt(r)):s.set(0,0,0)}static getBarycoord(t,e,n,s,r){An.subVectors(s,e),jn.subVectors(n,e),Ko.subVectors(t,e);let o=An.dot(An),a=An.dot(jn),l=An.dot(Ko),c=jn.dot(jn),h=jn.dot(Ko),d=o*c-a*a;if(d===0)return r.set(0,0,0),null;let u=1/d,f=(c*l-a*h)*u,g=(o*h-a*l)*u;return r.set(1-f-g,g,f)}static containsPoint(t,e,n,s){return this.getBarycoord(t,e,n,s,Qn)===null?!1:Qn.x>=0&&Qn.y>=0&&Qn.x+Qn.y<=1}static getInterpolation(t,e,n,s,r,o,a,l){return this.getBarycoord(t,e,n,s,Qn)===null?(l.x=0,l.y=0,"z"in l&&(l.z=0),"w"in l&&(l.w=0),null):(l.setScalar(0),l.addScaledVector(r,Qn.x),l.addScaledVector(o,Qn.y),l.addScaledVector(a,Qn.z),l)}static getInterpolatedAttribute(t,e,n,s,r,o){return ea.setScalar(0),na.setScalar(0),ia.setScalar(0),ea.fromBufferAttribute(t,e),na.fromBufferAttribute(t,n),ia.fromBufferAttribute(t,s),o.setScalar(0),o.addScaledVector(ea,r.x),o.addScaledVector(na,r.y),o.addScaledVector(ia,r.z),o}static isFrontFacing(t,e,n,s){return An.subVectors(n,e),jn.subVectors(t,e),An.cross(jn).dot(s)<0}set(t,e,n){return this.a.copy(t),this.b.copy(e),this.c.copy(n),this}setFromPointsAndIndices(t,e,n,s){return this.a.copy(t[e]),this.b.copy(t[n]),this.c.copy(t[s]),this}setFromAttributeAndIndices(t,e,n,s){return this.a.fromBufferAttribute(t,e),this.b.fromBufferAttribute(t,n),this.c.fromBufferAttribute(t,s),this}clone(){return new this.constructor().copy(this)}copy(t){return this.a.copy(t.a),this.b.copy(t.b),this.c.copy(t.c),this}getArea(){return An.subVectors(this.c,this.b),jn.subVectors(this.a,this.b),An.cross(jn).length()*.5}getMidpoint(t){return t.addVectors(this.a,this.b).add(this.c).multiplyScalar(1/3)}getNormal(t){return i.getNormal(this.a,this.b,this.c,t)}getPlane(t){return t.setFromCoplanarPoints(this.a,this.b,this.c)}getBarycoord(t,e){return i.getBarycoord(t,this.a,this.b,this.c,e)}getInterpolation(t,e,n,s,r){return i.getInterpolation(t,this.a,this.b,this.c,e,n,s,r)}containsPoint(t){return i.containsPoint(t,this.a,this.b,this.c)}isFrontFacing(t){return i.isFrontFacing(this.a,this.b,this.c,t)}intersectsBox(t){return t.intersectsTriangle(this)}closestPointToPoint(t,e){let n=this.a,s=this.b,r=this.c,o,a;ss.subVectors(s,n),rs.subVectors(r,n),jo.subVectors(t,n);let l=ss.dot(jo),c=rs.dot(jo);if(l<=0&&c<=0)return e.copy(n);Qo.subVectors(t,s);let h=ss.dot(Qo),d=rs.dot(Qo);if(h>=0&&d<=h)return e.copy(s);let u=l*d-h*c;if(u<=0&&l>=0&&h<=0)return o=l/(l-h),e.copy(n).addScaledVector(ss,o);ta.subVectors(t,r);let f=ss.dot(ta),g=rs.dot(ta);if(g>=0&&f<=g)return e.copy(r);let y=f*c-l*g;if(y<=0&&c>=0&&g<=0)return a=c/(c-g),e.copy(n).addScaledVector(rs,a);let m=h*g-f*d;if(m<=0&&d-h>=0&&f-g>=0)return Gc.subVectors(r,s),a=(d-h)/(d-h+(f-g)),e.copy(s).addScaledVector(Gc,a);let p=1/(m+y+u);return o=y*p,a=u*p,e.copy(n).addScaledVector(ss,o).addScaledVector(rs,a)}equals(t){return t.a.equals(this.a)&&t.b.equals(this.b)&&t.c.equals(this.c)}},Vh={aliceblue:15792383,antiquewhite:16444375,aqua:65535,aquamarine:8388564,azure:15794175,beige:16119260,bisque:16770244,black:0,blanchedalmond:16772045,blue:255,blueviolet:9055202,brown:10824234,burlywood:14596231,cadetblue:6266528,chartreuse:8388352,chocolate:13789470,coral:16744272,cornflowerblue:6591981,cornsilk:16775388,crimson:14423100,cyan:65535,darkblue:139,darkcyan:35723,darkgoldenrod:12092939,darkgray:11119017,darkgreen:25600,darkgrey:11119017,darkkhaki:12433259,darkmagenta:9109643,darkolivegreen:5597999,darkorange:16747520,darkorchid:10040012,darkred:9109504,darksalmon:15308410,darkseagreen:9419919,darkslateblue:4734347,darkslategray:3100495,darkslategrey:3100495,darkturquoise:52945,darkviolet:9699539,deeppink:16716947,deepskyblue:49151,dimgray:6908265,dimgrey:6908265,dodgerblue:2003199,firebrick:11674146,floralwhite:16775920,forestgreen:2263842,fuchsia:16711935,gainsboro:14474460,ghostwhite:16316671,gold:16766720,goldenrod:14329120,gray:8421504,green:32768,greenyellow:11403055,grey:8421504,honeydew:15794160,hotpink:16738740,indianred:13458524,indigo:4915330,ivory:16777200,khaki:15787660,lavender:15132410,lavenderblush:16773365,lawngreen:8190976,lemonchiffon:16775885,lightblue:11393254,lightcoral:15761536,lightcyan:14745599,lightgoldenrodyellow:16448210,lightgray:13882323,lightgreen:9498256,lightgrey:13882323,lightpink:16758465,lightsalmon:16752762,lightseagreen:2142890,lightskyblue:8900346,lightslategray:7833753,lightslategrey:7833753,lightsteelblue:11584734,lightyellow:16777184,lime:65280,limegreen:3329330,linen:16445670,magenta:16711935,maroon:8388608,mediumaquamarine:6737322,mediumblue:205,mediumorchid:12211667,mediumpurple:9662683,mediumseagreen:3978097,mediumslateblue:8087790,mediumspringgreen:64154,mediumturquoise:4772300,mediumvioletred:13047173,midnightblue:1644912,mintcream:16121850,mistyrose:16770273,moccasin:16770229,navajowhite:16768685,navy:128,oldlace:16643558,olive:8421376,olivedrab:7048739,orange:16753920,orangered:16729344,orchid:14315734,palegoldenrod:15657130,palegreen:10025880,paleturquoise:11529966,palevioletred:14381203,papayawhip:16773077,peachpuff:16767673,peru:13468991,pink:16761035,plum:14524637,powderblue:11591910,purple:8388736,rebeccapurple:6697881,red:16711680,rosybrown:12357519,royalblue:4286945,saddlebrown:9127187,salmon:16416882,sandybrown:16032864,seagreen:3050327,seashell:16774638,sienna:10506797,silver:12632256,skyblue:8900331,slateblue:6970061,slategray:7372944,slategrey:7372944,snow:16775930,springgreen:65407,steelblue:4620980,tan:13808780,teal:32896,thistle:14204888,tomato:16737095,turquoise:4251856,violet:15631086,wheat:16113331,white:16777215,whitesmoke:16119285,yellow:16776960,yellowgreen:10145074},pi={h:0,s:0,l:0},Ar={h:0,s:0,l:0};function sa(i,t,e){return e<0&&(e+=1),e>1&&(e-=1),e<1/6?i+(t-i)*6*e:e<1/2?t:e<2/3?i+(t-i)*6*(2/3-e):i}var Ct=class{constructor(t,e,n){return this.isColor=!0,this.r=1,this.g=1,this.b=1,this.set(t,e,n)}set(t,e,n){if(e===void 0&&n===void 0){let s=t;s&&s.isColor?this.copy(s):typeof s=="number"?this.setHex(s):typeof s=="string"&&this.setStyle(s)}else this.setRGB(t,e,n);return this}setScalar(t){return this.r=t,this.g=t,this.b=t,this}setHex(t,e=Pe){return t=Math.floor(t),this.r=(t>>16&255)/255,this.g=(t>>8&255)/255,this.b=(t&255)/255,jt.toWorkingColorSpace(this,e),this}setRGB(t,e,n,s=jt.workingColorSpace){return this.r=t,this.g=e,this.b=n,jt.toWorkingColorSpace(this,s),this}setHSL(t,e,n,s=jt.workingColorSpace){if(t=md(t,1),e=Ne(e,0,1),n=Ne(n,0,1),e===0)this.r=this.g=this.b=n;else{let r=n<=.5?n*(1+e):n+e-n*e,o=2*n-r;this.r=sa(o,r,t+1/3),this.g=sa(o,r,t),this.b=sa(o,r,t-1/3)}return jt.toWorkingColorSpace(this,s),this}setStyle(t,e=Pe){function n(r){r!==void 0&&parseFloat(r)<1&&console.warn("THREE.Color: Alpha component of "+t+" will be ignored.")}let s;if(s=/^(\w+)\(([^\)]*)\)/.exec(t)){let r,o=s[1],a=s[2];switch(o){case"rgb":case"rgba":if(r=/^\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*(\d*\.?\d+)\s*)?$/.exec(a))return n(r[4]),this.setRGB(Math.min(255,parseInt(r[1],10))/255,Math.min(255,parseInt(r[2],10))/255,Math.min(255,parseInt(r[3],10))/255,e);if(r=/^\s*(\d+)\%\s*,\s*(\d+)\%\s*,\s*(\d+)\%\s*(?:,\s*(\d*\.?\d+)\s*)?$/.exec(a))return n(r[4]),this.setRGB(Math.min(100,parseInt(r[1],10))/100,Math.min(100,parseInt(r[2],10))/100,Math.min(100,parseInt(r[3],10))/100,e);break;case"hsl":case"hsla":if(r=/^\s*(\d*\.?\d+)\s*,\s*(\d*\.?\d+)\%\s*,\s*(\d*\.?\d+)\%\s*(?:,\s*(\d*\.?\d+)\s*)?$/.exec(a))return n(r[4]),this.setHSL(parseFloat(r[1])/360,parseFloat(r[2])/100,parseFloat(r[3])/100,e);break;default:console.warn("THREE.Color: Unknown color model "+t)}}else if(s=/^\#([A-Fa-f\d]+)$/.exec(t)){let r=s[1],o=r.length;if(o===3)return this.setRGB(parseInt(r.charAt(0),16)/15,parseInt(r.charAt(1),16)/15,parseInt(r.charAt(2),16)/15,e);if(o===6)return this.setHex(parseInt(r,16),e);console.warn("THREE.Color: Invalid hex color "+t)}else if(t&&t.length>0)return this.setColorName(t,e);return this}setColorName(t,e=Pe){let n=Vh[t.toLowerCase()];return n!==void 0?this.setHex(n,e):console.warn("THREE.Color: Unknown color "+t),this}clone(){return new this.constructor(this.r,this.g,this.b)}copy(t){return this.r=t.r,this.g=t.g,this.b=t.b,this}copySRGBToLinear(t){return this.r=si(t.r),this.g=si(t.g),this.b=si(t.b),this}copyLinearToSRGB(t){return this.r=ps(t.r),this.g=ps(t.g),this.b=ps(t.b),this}convertSRGBToLinear(){return this.copySRGBToLinear(this),this}convertLinearToSRGB(){return this.copyLinearToSRGB(this),this}getHex(t=Pe){return jt.fromWorkingColorSpace(Xe.copy(this),t),Math.round(Ne(Xe.r*255,0,255))*65536+Math.round(Ne(Xe.g*255,0,255))*256+Math.round(Ne(Xe.b*255,0,255))}getHexString(t=Pe){return("000000"+this.getHex(t).toString(16)).slice(-6)}getHSL(t,e=jt.workingColorSpace){jt.fromWorkingColorSpace(Xe.copy(this),e);let n=Xe.r,s=Xe.g,r=Xe.b,o=Math.max(n,s,r),a=Math.min(n,s,r),l,c,h=(a+o)/2;if(a===o)l=0,c=0;else{let d=o-a;switch(c=h<=.5?d/(o+a):d/(2-o-a),o){case n:l=(s-r)/d+(s<r?6:0);break;case s:l=(r-n)/d+2;break;case r:l=(n-s)/d+4;break}l/=6}return t.h=l,t.s=c,t.l=h,t}getRGB(t,e=jt.workingColorSpace){return jt.fromWorkingColorSpace(Xe.copy(this),e),t.r=Xe.r,t.g=Xe.g,t.b=Xe.b,t}getStyle(t=Pe){jt.fromWorkingColorSpace(Xe.copy(this),t);let e=Xe.r,n=Xe.g,s=Xe.b;return t!==Pe?`color(${t} ${e.toFixed(3)} ${n.toFixed(3)} ${s.toFixed(3)})`:`rgb(${Math.round(e*255)},${Math.round(n*255)},${Math.round(s*255)})`}offsetHSL(t,e,n){return this.getHSL(pi),this.setHSL(pi.h+t,pi.s+e,pi.l+n)}add(t){return this.r+=t.r,this.g+=t.g,this.b+=t.b,this}addColors(t,e){return this.r=t.r+e.r,this.g=t.g+e.g,this.b=t.b+e.b,this}addScalar(t){return this.r+=t,this.g+=t,this.b+=t,this}sub(t){return this.r=Math.max(0,this.r-t.r),this.g=Math.max(0,this.g-t.g),this.b=Math.max(0,this.b-t.b),this}multiply(t){return this.r*=t.r,this.g*=t.g,this.b*=t.b,this}multiplyScalar(t){return this.r*=t,this.g*=t,this.b*=t,this}lerp(t,e){return this.r+=(t.r-this.r)*e,this.g+=(t.g-this.g)*e,this.b+=(t.b-this.b)*e,this}lerpColors(t,e,n){return this.r=t.r+(e.r-t.r)*n,this.g=t.g+(e.g-t.g)*n,this.b=t.b+(e.b-t.b)*n,this}lerpHSL(t,e){this.getHSL(pi),t.getHSL(Ar);let n=ko(pi.h,Ar.h,e),s=ko(pi.s,Ar.s,e),r=ko(pi.l,Ar.l,e);return this.setHSL(n,s,r),this}setFromVector3(t){return this.r=t.x,this.g=t.y,this.b=t.z,this}applyMatrix3(t){let e=this.r,n=this.g,s=this.b,r=t.elements;return this.r=r[0]*e+r[3]*n+r[6]*s,this.g=r[1]*e+r[4]*n+r[7]*s,this.b=r[2]*e+r[5]*n+r[8]*s,this}equals(t){return t.r===this.r&&t.g===this.g&&t.b===this.b}fromArray(t,e=0){return this.r=t[e],this.g=t[e+1],this.b=t[e+2],this}toArray(t=[],e=0){return t[e]=this.r,t[e+1]=this.g,t[e+2]=this.b,t}fromBufferAttribute(t,e){return this.r=t.getX(e),this.g=t.getY(e),this.b=t.getZ(e),this}toJSON(){return this.getHex()}*[Symbol.iterator](){yield this.r,yield this.g,yield this.b}},Xe=new Ct;Ct.NAMES=Vh;var Cd=0,Mi=class extends _i{static get type(){return"Material"}get type(){return this.constructor.type}set type(t){}constructor(){super(),this.isMaterial=!0,Object.defineProperty(this,"id",{value:Cd++}),this.uuid=lr(),this.name="",this.blending=ds,this.side=xi,this.vertexColors=!1,this.opacity=1,this.transparent=!1,this.alphaHash=!1,this.blendSrc=_a,this.blendDst=ya,this.blendEquation=Ni,this.blendSrcAlpha=null,this.blendDstAlpha=null,this.blendEquationAlpha=null,this.blendColor=new Ct(0,0,0),this.blendAlpha=0,this.depthFunc=gs,this.depthTest=!0,this.depthWrite=!0,this.stencilWriteMask=255,this.stencilFunc=Tc,this.stencilRef=0,this.stencilFuncMask=255,this.stencilFail=Ji,this.stencilZFail=Ji,this.stencilZPass=Ji,this.stencilWrite=!1,this.clippingPlanes=null,this.clipIntersection=!1,this.clipShadows=!1,this.shadowSide=null,this.colorWrite=!0,this.precision=null,this.polygonOffset=!1,this.polygonOffsetFactor=0,this.polygonOffsetUnits=0,this.dithering=!1,this.alphaToCoverage=!1,this.premultipliedAlpha=!1,this.forceSinglePass=!1,this.visible=!0,this.toneMapped=!0,this.userData={},this.version=0,this._alphaTest=0}get alphaTest(){return this._alphaTest}set alphaTest(t){this._alphaTest>0!=t>0&&this.version++,this._alphaTest=t}onBeforeRender(){}onBeforeCompile(){}customProgramCacheKey(){return this.onBeforeCompile.toString()}setValues(t){if(t!==void 0)for(let e in t){let n=t[e];if(n===void 0){console.warn(`THREE.Material: parameter '${e}' has value of undefined.`);continue}let s=this[e];if(s===void 0){console.warn(`THREE.Material: '${e}' is not a property of THREE.${this.type}.`);continue}s&&s.isColor?s.set(n):s&&s.isVector3&&n&&n.isVector3?s.copy(n):this[e]=n}}toJSON(t){let e=t===void 0||typeof t=="string";e&&(t={textures:{},images:{}});let n={metadata:{version:4.6,type:"Material",generator:"Material.toJSON"}};n.uuid=this.uuid,n.type=this.type,this.name!==""&&(n.name=this.name),this.color&&this.color.isColor&&(n.color=this.color.getHex()),this.roughness!==void 0&&(n.roughness=this.roughness),this.metalness!==void 0&&(n.metalness=this.metalness),this.sheen!==void 0&&(n.sheen=this.sheen),this.sheenColor&&this.sheenColor.isColor&&(n.sheenColor=this.sheenColor.getHex()),this.sheenRoughness!==void 0&&(n.sheenRoughness=this.sheenRoughness),this.emissive&&this.emissive.isColor&&(n.emissive=this.emissive.getHex()),this.emissiveIntensity!==void 0&&this.emissiveIntensity!==1&&(n.emissiveIntensity=this.emissiveIntensity),this.specular&&this.specular.isColor&&(n.specular=this.specular.getHex()),this.specularIntensity!==void 0&&(n.specularIntensity=this.specularIntensity),this.specularColor&&this.specularColor.isColor&&(n.specularColor=this.specularColor.getHex()),this.shininess!==void 0&&(n.shininess=this.shininess),this.clearcoat!==void 0&&(n.clearcoat=this.clearcoat),this.clearcoatRoughness!==void 0&&(n.clearcoatRoughness=this.clearcoatRoughness),this.clearcoatMap&&this.clearcoatMap.isTexture&&(n.clearcoatMap=this.clearcoatMap.toJSON(t).uuid),this.clearcoatRoughnessMap&&this.clearcoatRoughnessMap.isTexture&&(n.clearcoatRoughnessMap=this.clearcoatRoughnessMap.toJSON(t).uuid),this.clearcoatNormalMap&&this.clearcoatNormalMap.isTexture&&(n.clearcoatNormalMap=this.clearcoatNormalMap.toJSON(t).uuid,n.clearcoatNormalScale=this.clearcoatNormalScale.toArray()),this.dispersion!==void 0&&(n.dispersion=this.dispersion),this.iridescence!==void 0&&(n.iridescence=this.iridescence),this.iridescenceIOR!==void 0&&(n.iridescenceIOR=this.iridescenceIOR),this.iridescenceThicknessRange!==void 0&&(n.iridescenceThicknessRange=this.iridescenceThicknessRange),this.iridescenceMap&&this.iridescenceMap.isTexture&&(n.iridescenceMap=this.iridescenceMap.toJSON(t).uuid),this.iridescenceThicknessMap&&this.iridescenceThicknessMap.isTexture&&(n.iridescenceThicknessMap=this.iridescenceThicknessMap.toJSON(t).uuid),this.anisotropy!==void 0&&(n.anisotropy=this.anisotropy),this.anisotropyRotation!==void 0&&(n.anisotropyRotation=this.anisotropyRotation),this.anisotropyMap&&this.anisotropyMap.isTexture&&(n.anisotropyMap=this.anisotropyMap.toJSON(t).uuid),this.map&&this.map.isTexture&&(n.map=this.map.toJSON(t).uuid),this.matcap&&this.matcap.isTexture&&(n.matcap=this.matcap.toJSON(t).uuid),this.alphaMap&&this.alphaMap.isTexture&&(n.alphaMap=this.alphaMap.toJSON(t).uuid),this.lightMap&&this.lightMap.isTexture&&(n.lightMap=this.lightMap.toJSON(t).uuid,n.lightMapIntensity=this.lightMapIntensity),this.aoMap&&this.aoMap.isTexture&&(n.aoMap=this.aoMap.toJSON(t).uuid,n.aoMapIntensity=this.aoMapIntensity),this.bumpMap&&this.bumpMap.isTexture&&(n.bumpMap=this.bumpMap.toJSON(t).uuid,n.bumpScale=this.bumpScale),this.normalMap&&this.normalMap.isTexture&&(n.normalMap=this.normalMap.toJSON(t).uuid,n.normalMapType=this.normalMapType,n.normalScale=this.normalScale.toArray()),this.displacementMap&&this.displacementMap.isTexture&&(n.displacementMap=this.displacementMap.toJSON(t).uuid,n.displacementScale=this.displacementScale,n.displacementBias=this.displacementBias),this.roughnessMap&&this.roughnessMap.isTexture&&(n.roughnessMap=this.roughnessMap.toJSON(t).uuid),this.metalnessMap&&this.metalnessMap.isTexture&&(n.metalnessMap=this.metalnessMap.toJSON(t).uuid),this.emissiveMap&&this.emissiveMap.isTexture&&(n.emissiveMap=this.emissiveMap.toJSON(t).uuid),this.specularMap&&this.specularMap.isTexture&&(n.specularMap=this.specularMap.toJSON(t).uuid),this.specularIntensityMap&&this.specularIntensityMap.isTexture&&(n.specularIntensityMap=this.specularIntensityMap.toJSON(t).uuid),this.specularColorMap&&this.specularColorMap.isTexture&&(n.specularColorMap=this.specularColorMap.toJSON(t).uuid),this.envMap&&this.envMap.isTexture&&(n.envMap=this.envMap.toJSON(t).uuid,this.combine!==void 0&&(n.combine=this.combine)),this.envMapRotation!==void 0&&(n.envMapRotation=this.envMapRotation.toArray()),this.envMapIntensity!==void 0&&(n.envMapIntensity=this.envMapIntensity),this.reflectivity!==void 0&&(n.reflectivity=this.reflectivity),this.refractionRatio!==void 0&&(n.refractionRatio=this.refractionRatio),this.gradientMap&&this.gradientMap.isTexture&&(n.gradientMap=this.gradientMap.toJSON(t).uuid),this.transmission!==void 0&&(n.transmission=this.transmission),this.transmissionMap&&this.transmissionMap.isTexture&&(n.transmissionMap=this.transmissionMap.toJSON(t).uuid),this.thickness!==void 0&&(n.thickness=this.thickness),this.thicknessMap&&this.thicknessMap.isTexture&&(n.thicknessMap=this.thicknessMap.toJSON(t).uuid),this.attenuationDistance!==void 0&&this.attenuationDistance!==1/0&&(n.attenuationDistance=this.attenuationDistance),this.attenuationColor!==void 0&&(n.attenuationColor=this.attenuationColor.getHex()),this.size!==void 0&&(n.size=this.size),this.shadowSide!==null&&(n.shadowSide=this.shadowSide),this.sizeAttenuation!==void 0&&(n.sizeAttenuation=this.sizeAttenuation),this.blending!==ds&&(n.blending=this.blending),this.side!==xi&&(n.side=this.side),this.vertexColors===!0&&(n.vertexColors=!0),this.opacity<1&&(n.opacity=this.opacity),this.transparent===!0&&(n.transparent=!0),this.blendSrc!==_a&&(n.blendSrc=this.blendSrc),this.blendDst!==ya&&(n.blendDst=this.blendDst),this.blendEquation!==Ni&&(n.blendEquation=this.blendEquation),this.blendSrcAlpha!==null&&(n.blendSrcAlpha=this.blendSrcAlpha),this.blendDstAlpha!==null&&(n.blendDstAlpha=this.blendDstAlpha),this.blendEquationAlpha!==null&&(n.blendEquationAlpha=this.blendEquationAlpha),this.blendColor&&this.blendColor.isColor&&(n.blendColor=this.blendColor.getHex()),this.blendAlpha!==0&&(n.blendAlpha=this.blendAlpha),this.depthFunc!==gs&&(n.depthFunc=this.depthFunc),this.depthTest===!1&&(n.depthTest=this.depthTest),this.depthWrite===!1&&(n.depthWrite=this.depthWrite),this.colorWrite===!1&&(n.colorWrite=this.colorWrite),this.stencilWriteMask!==255&&(n.stencilWriteMask=this.stencilWriteMask),this.stencilFunc!==Tc&&(n.stencilFunc=this.stencilFunc),this.stencilRef!==0&&(n.stencilRef=this.stencilRef),this.stencilFuncMask!==255&&(n.stencilFuncMask=this.stencilFuncMask),this.stencilFail!==Ji&&(n.stencilFail=this.stencilFail),this.stencilZFail!==Ji&&(n.stencilZFail=this.stencilZFail),this.stencilZPass!==Ji&&(n.stencilZPass=this.stencilZPass),this.stencilWrite===!0&&(n.stencilWrite=this.stencilWrite),this.rotation!==void 0&&this.rotation!==0&&(n.rotation=this.rotation),this.polygonOffset===!0&&(n.polygonOffset=!0),this.polygonOffsetFactor!==0&&(n.polygonOffsetFactor=this.polygonOffsetFactor),this.polygonOffsetUnits!==0&&(n.polygonOffsetUnits=this.polygonOffsetUnits),this.linewidth!==void 0&&this.linewidth!==1&&(n.linewidth=this.linewidth),this.dashSize!==void 0&&(n.dashSize=this.dashSize),this.gapSize!==void 0&&(n.gapSize=this.gapSize),this.scale!==void 0&&(n.scale=this.scale),this.dithering===!0&&(n.dithering=!0),this.alphaTest>0&&(n.alphaTest=this.alphaTest),this.alphaHash===!0&&(n.alphaHash=!0),this.alphaToCoverage===!0&&(n.alphaToCoverage=!0),this.premultipliedAlpha===!0&&(n.premultipliedAlpha=!0),this.forceSinglePass===!0&&(n.forceSinglePass=!0),this.wireframe===!0&&(n.wireframe=!0),this.wireframeLinewidth>1&&(n.wireframeLinewidth=this.wireframeLinewidth),this.wireframeLinecap!=="round"&&(n.wireframeLinecap=this.wireframeLinecap),this.wireframeLinejoin!=="round"&&(n.wireframeLinejoin=this.wireframeLinejoin),this.flatShading===!0&&(n.flatShading=!0),this.visible===!1&&(n.visible=!1),this.toneMapped===!1&&(n.toneMapped=!1),this.fog===!1&&(n.fog=!1),Object.keys(this.userData).length>0&&(n.userData=this.userData);function s(r){let o=[];for(let a in r){let l=r[a];delete l.metadata,o.push(l)}return o}if(e){let r=s(t.textures),o=s(t.images);r.length>0&&(n.textures=r),o.length>0&&(n.images=o)}return n}clone(){return new this.constructor().copy(this)}copy(t){this.name=t.name,this.blending=t.blending,this.side=t.side,this.vertexColors=t.vertexColors,this.opacity=t.opacity,this.transparent=t.transparent,this.blendSrc=t.blendSrc,this.blendDst=t.blendDst,this.blendEquation=t.blendEquation,this.blendSrcAlpha=t.blendSrcAlpha,this.blendDstAlpha=t.blendDstAlpha,this.blendEquationAlpha=t.blendEquationAlpha,this.blendColor.copy(t.blendColor),this.blendAlpha=t.blendAlpha,this.depthFunc=t.depthFunc,this.depthTest=t.depthTest,this.depthWrite=t.depthWrite,this.stencilWriteMask=t.stencilWriteMask,this.stencilFunc=t.stencilFunc,this.stencilRef=t.stencilRef,this.stencilFuncMask=t.stencilFuncMask,this.stencilFail=t.stencilFail,this.stencilZFail=t.stencilZFail,this.stencilZPass=t.stencilZPass,this.stencilWrite=t.stencilWrite;let e=t.clippingPlanes,n=null;if(e!==null){let s=e.length;n=new Array(s);for(let r=0;r!==s;++r)n[r]=e[r].clone()}return this.clippingPlanes=n,this.clipIntersection=t.clipIntersection,this.clipShadows=t.clipShadows,this.shadowSide=t.shadowSide,this.colorWrite=t.colorWrite,this.precision=t.precision,this.polygonOffset=t.polygonOffset,this.polygonOffsetFactor=t.polygonOffsetFactor,this.polygonOffsetUnits=t.polygonOffsetUnits,this.dithering=t.dithering,this.alphaTest=t.alphaTest,this.alphaHash=t.alphaHash,this.alphaToCoverage=t.alphaToCoverage,this.premultipliedAlpha=t.premultipliedAlpha,this.forceSinglePass=t.forceSinglePass,this.visible=t.visible,this.toneMapped=t.toneMapped,this.userData=JSON.parse(JSON.stringify(t.userData)),this}dispose(){this.dispatchEvent({type:"dispose"})}set needsUpdate(t){t===!0&&this.version++}onBuild(){console.warn("Material: onBuild() has been removed.")}},Ss=class extends Mi{static get type(){return"MeshBasicMaterial"}constructor(t){super(),this.isMeshBasicMaterial=!0,this.color=new Ct(16777215),this.map=null,this.lightMap=null,this.lightMapIntensity=1,this.aoMap=null,this.aoMapIntensity=1,this.specularMap=null,this.alphaMap=null,this.envMap=null,this.envMapRotation=new gn,this.combine=Rh,this.reflectivity=1,this.refractionRatio=.98,this.wireframe=!1,this.wireframeLinewidth=1,this.wireframeLinecap="round",this.wireframeLinejoin="round",this.fog=!0,this.setValues(t)}copy(t){return super.copy(t),this.color.copy(t.color),this.map=t.map,this.lightMap=t.lightMap,this.lightMapIntensity=t.lightMapIntensity,this.aoMap=t.aoMap,this.aoMapIntensity=t.aoMapIntensity,this.specularMap=t.specularMap,this.alphaMap=t.alphaMap,this.envMap=t.envMap,this.envMapRotation.copy(t.envMapRotation),this.combine=t.combine,this.reflectivity=t.reflectivity,this.refractionRatio=t.refractionRatio,this.wireframe=t.wireframe,this.wireframeLinewidth=t.wireframeLinewidth,this.wireframeLinecap=t.wireframeLinecap,this.wireframeLinejoin=t.wireframeLinejoin,this.fog=t.fog,this}};var Ae=new P,Rr=new gt,Ut=class{constructor(t,e,n=!1){if(Array.isArray(t))throw new TypeError("THREE.BufferAttribute: array should be a Typed Array.");this.isBufferAttribute=!0,this.name="",this.array=t,this.itemSize=e,this.count=t!==void 0?t.length/e:0,this.normalized=n,this.usage=Ac,this.updateRanges=[],this.gpuType=zn,this.version=0}onUploadCallback(){}set needsUpdate(t){t===!0&&this.version++}setUsage(t){return this.usage=t,this}addUpdateRange(t,e){this.updateRanges.push({start:t,count:e})}clearUpdateRanges(){this.updateRanges.length=0}copy(t){return this.name=t.name,this.array=new t.array.constructor(t.array),this.itemSize=t.itemSize,this.count=t.count,this.normalized=t.normalized,this.usage=t.usage,this.gpuType=t.gpuType,this}copyAt(t,e,n){t*=this.itemSize,n*=e.itemSize;for(let s=0,r=this.itemSize;s<r;s++)this.array[t+s]=e.array[n+s];return this}copyArray(t){return this.array.set(t),this}applyMatrix3(t){if(this.itemSize===2)for(let e=0,n=this.count;e<n;e++)Rr.fromBufferAttribute(this,e),Rr.applyMatrix3(t),this.setXY(e,Rr.x,Rr.y);else if(this.itemSize===3)for(let e=0,n=this.count;e<n;e++)Ae.fromBufferAttribute(this,e),Ae.applyMatrix3(t),this.setXYZ(e,Ae.x,Ae.y,Ae.z);return this}applyMatrix4(t){for(let e=0,n=this.count;e<n;e++)Ae.fromBufferAttribute(this,e),Ae.applyMatrix4(t),this.setXYZ(e,Ae.x,Ae.y,Ae.z);return this}applyNormalMatrix(t){for(let e=0,n=this.count;e<n;e++)Ae.fromBufferAttribute(this,e),Ae.applyNormalMatrix(t),this.setXYZ(e,Ae.x,Ae.y,Ae.z);return this}transformDirection(t){for(let e=0,n=this.count;e<n;e++)Ae.fromBufferAttribute(this,e),Ae.transformDirection(t),this.setXYZ(e,Ae.x,Ae.y,Ae.z);return this}set(t,e=0){return this.array.set(t,e),this}getComponent(t,e){let n=this.array[t*this.itemSize+e];return this.normalized&&(n=Gs(n,this.array)),n}setComponent(t,e,n){return this.normalized&&(n=en(n,this.array)),this.array[t*this.itemSize+e]=n,this}getX(t){let e=this.array[t*this.itemSize];return this.normalized&&(e=Gs(e,this.array)),e}setX(t,e){return this.normalized&&(e=en(e,this.array)),this.array[t*this.itemSize]=e,this}getY(t){let e=this.array[t*this.itemSize+1];return this.normalized&&(e=Gs(e,this.array)),e}setY(t,e){return this.normalized&&(e=en(e,this.array)),this.array[t*this.itemSize+1]=e,this}getZ(t){let e=this.array[t*this.itemSize+2];return this.normalized&&(e=Gs(e,this.array)),e}setZ(t,e){return this.normalized&&(e=en(e,this.array)),this.array[t*this.itemSize+2]=e,this}getW(t){let e=this.array[t*this.itemSize+3];return this.normalized&&(e=Gs(e,this.array)),e}setW(t,e){return this.normalized&&(e=en(e,this.array)),this.array[t*this.itemSize+3]=e,this}setXY(t,e,n){return t*=this.itemSize,this.normalized&&(e=en(e,this.array),n=en(n,this.array)),this.array[t+0]=e,this.array[t+1]=n,this}setXYZ(t,e,n,s){return t*=this.itemSize,this.normalized&&(e=en(e,this.array),n=en(n,this.array),s=en(s,this.array)),this.array[t+0]=e,this.array[t+1]=n,this.array[t+2]=s,this}setXYZW(t,e,n,s,r){return t*=this.itemSize,this.normalized&&(e=en(e,this.array),n=en(n,this.array),s=en(s,this.array),r=en(r,this.array)),this.array[t+0]=e,this.array[t+1]=n,this.array[t+2]=s,this.array[t+3]=r,this}onUpload(t){return this.onUploadCallback=t,this}clone(){return new this.constructor(this.array,this.itemSize).copy(this)}toJSON(){let t={itemSize:this.itemSize,type:this.array.constructor.name,array:Array.from(this.array),normalized:this.normalized};return this.name!==""&&(t.name=this.name),this.usage!==Ac&&(t.usage=this.usage),t}};var so=class extends Ut{constructor(t,e,n){super(new Uint16Array(t),e,n)}};var ro=class extends Ut{constructor(t,e,n){super(new Uint32Array(t),e,n)}};var Bt=class extends Ut{constructor(t,e,n){super(new Float32Array(t),e,n)}},Pd=0,mn=new ne,ra=new Ze,os=new P,hn=new Pn,Ys=new Pn,Ue=new P,Yt=class i extends _i{constructor(){super(),this.isBufferGeometry=!0,Object.defineProperty(this,"id",{value:Pd++}),this.uuid=lr(),this.name="",this.type="BufferGeometry",this.index=null,this.indirect=null,this.attributes={},this.morphAttributes={},this.morphTargetsRelative=!1,this.groups=[],this.boundingBox=null,this.boundingSphere=null,this.drawRange={start:0,count:1/0},this.userData={}}getIndex(){return this.index}setIndex(t){return Array.isArray(t)?this.index=new(kh(t)?ro:so)(t,1):this.index=t,this}setIndirect(t){return this.indirect=t,this}getIndirect(){return this.indirect}getAttribute(t){return this.attributes[t]}setAttribute(t,e){return this.attributes[t]=e,this}deleteAttribute(t){return delete this.attributes[t],this}hasAttribute(t){return this.attributes[t]!==void 0}addGroup(t,e,n=0){this.groups.push({start:t,count:e,materialIndex:n})}clearGroups(){this.groups=[]}setDrawRange(t,e){this.drawRange.start=t,this.drawRange.count=e}applyMatrix4(t){let e=this.attributes.position;e!==void 0&&(e.applyMatrix4(t),e.needsUpdate=!0);let n=this.attributes.normal;if(n!==void 0){let r=new Ot().getNormalMatrix(t);n.applyNormalMatrix(r),n.needsUpdate=!0}let s=this.attributes.tangent;return s!==void 0&&(s.transformDirection(t),s.needsUpdate=!0),this.boundingBox!==null&&this.computeBoundingBox(),this.boundingSphere!==null&&this.computeBoundingSphere(),this}applyQuaternion(t){return mn.makeRotationFromQuaternion(t),this.applyMatrix4(mn),this}rotateX(t){return mn.makeRotationX(t),this.applyMatrix4(mn),this}rotateY(t){return mn.makeRotationY(t),this.applyMatrix4(mn),this}rotateZ(t){return mn.makeRotationZ(t),this.applyMatrix4(mn),this}translate(t,e,n){return mn.makeTranslation(t,e,n),this.applyMatrix4(mn),this}scale(t,e,n){return mn.makeScale(t,e,n),this.applyMatrix4(mn),this}lookAt(t){return ra.lookAt(t),ra.updateMatrix(),this.applyMatrix4(ra.matrix),this}center(){return this.computeBoundingBox(),this.boundingBox.getCenter(os).negate(),this.translate(os.x,os.y,os.z),this}setFromPoints(t){let e=this.getAttribute("position");if(e===void 0){let n=[];for(let s=0,r=t.length;s<r;s++){let o=t[s];n.push(o.x,o.y,o.z||0)}this.setAttribute("position",new Bt(n,3))}else{for(let n=0,s=e.count;n<s;n++){let r=t[n];e.setXYZ(n,r.x,r.y,r.z||0)}t.length>e.count&&console.warn("THREE.BufferGeometry: Buffer size too small for points data. Use .dispose() and create a new geometry."),e.needsUpdate=!0}return this}computeBoundingBox(){this.boundingBox===null&&(this.boundingBox=new Pn);let t=this.attributes.position,e=this.morphAttributes.position;if(t&&t.isGLBufferAttribute){console.error("THREE.BufferGeometry.computeBoundingBox(): GLBufferAttribute requires a manual bounding box.",this),this.boundingBox.set(new P(-1/0,-1/0,-1/0),new P(1/0,1/0,1/0));return}if(t!==void 0){if(this.boundingBox.setFromBufferAttribute(t),e)for(let n=0,s=e.length;n<s;n++){let r=e[n];hn.setFromBufferAttribute(r),this.morphTargetsRelative?(Ue.addVectors(this.boundingBox.min,hn.min),this.boundingBox.expandByPoint(Ue),Ue.addVectors(this.boundingBox.max,hn.max),this.boundingBox.expandByPoint(Ue)):(this.boundingBox.expandByPoint(hn.min),this.boundingBox.expandByPoint(hn.max))}}else this.boundingBox.makeEmpty();(isNaN(this.boundingBox.min.x)||isNaN(this.boundingBox.min.y)||isNaN(this.boundingBox.min.z))&&console.error('THREE.BufferGeometry.computeBoundingBox(): Computed min/max have NaN values. The "position" attribute is likely to have NaN values.',this)}computeBoundingSphere(){this.boundingSphere===null&&(this.boundingSphere=new yi);let t=this.attributes.position,e=this.morphAttributes.position;if(t&&t.isGLBufferAttribute){console.error("THREE.BufferGeometry.computeBoundingSphere(): GLBufferAttribute requires a manual bounding sphere.",this),this.boundingSphere.set(new P,1/0);return}if(t){let n=this.boundingSphere.center;if(hn.setFromBufferAttribute(t),e)for(let r=0,o=e.length;r<o;r++){let a=e[r];Ys.setFromBufferAttribute(a),this.morphTargetsRelative?(Ue.addVectors(hn.min,Ys.min),hn.expandByPoint(Ue),Ue.addVectors(hn.max,Ys.max),hn.expandByPoint(Ue)):(hn.expandByPoint(Ys.min),hn.expandByPoint(Ys.max))}hn.getCenter(n);let s=0;for(let r=0,o=t.count;r<o;r++)Ue.fromBufferAttribute(t,r),s=Math.max(s,n.distanceToSquared(Ue));if(e)for(let r=0,o=e.length;r<o;r++){let a=e[r],l=this.morphTargetsRelative;for(let c=0,h=a.count;c<h;c++)Ue.fromBufferAttribute(a,c),l&&(os.fromBufferAttribute(t,c),Ue.add(os)),s=Math.max(s,n.distanceToSquared(Ue))}this.boundingSphere.radius=Math.sqrt(s),isNaN(this.boundingSphere.radius)&&console.error('THREE.BufferGeometry.computeBoundingSphere(): Computed radius is NaN. The "position" attribute is likely to have NaN values.',this)}}computeTangents(){let t=this.index,e=this.attributes;if(t===null||e.position===void 0||e.normal===void 0||e.uv===void 0){console.error("THREE.BufferGeometry: .computeTangents() failed. Missing required attributes (index, position, normal or uv)");return}let n=e.position,s=e.normal,r=e.uv;this.hasAttribute("tangent")===!1&&this.setAttribute("tangent",new Ut(new Float32Array(4*n.count),4));let o=this.getAttribute("tangent"),a=[],l=[];for(let b=0;b<n.count;b++)a[b]=new P,l[b]=new P;let c=new P,h=new P,d=new P,u=new gt,f=new gt,g=new gt,y=new P,m=new P;function p(b,x,_){c.fromBufferAttribute(n,b),h.fromBufferAttribute(n,x),d.fromBufferAttribute(n,_),u.fromBufferAttribute(r,b),f.fromBufferAttribute(r,x),g.fromBufferAttribute(r,_),h.sub(c),d.sub(c),f.sub(u),g.sub(u);let R=1/(f.x*g.y-g.x*f.y);isFinite(R)&&(y.copy(h).multiplyScalar(g.y).addScaledVector(d,-f.y).multiplyScalar(R),m.copy(d).multiplyScalar(f.x).addScaledVector(h,-g.x).multiplyScalar(R),a[b].add(y),a[x].add(y),a[_].add(y),l[b].add(m),l[x].add(m),l[_].add(m))}let w=this.groups;w.length===0&&(w=[{start:0,count:t.count}]);for(let b=0,x=w.length;b<x;++b){let _=w[b],R=_.start,I=_.count;for(let L=R,N=R+I;L<N;L+=3)p(t.getX(L+0),t.getX(L+1),t.getX(L+2))}let E=new P,M=new P,U=new P,C=new P;function v(b){U.fromBufferAttribute(s,b),C.copy(U);let x=a[b];E.copy(x),E.sub(U.multiplyScalar(U.dot(x))).normalize(),M.crossVectors(C,x);let R=M.dot(l[b])<0?-1:1;o.setXYZW(b,E.x,E.y,E.z,R)}for(let b=0,x=w.length;b<x;++b){let _=w[b],R=_.start,I=_.count;for(let L=R,N=R+I;L<N;L+=3)v(t.getX(L+0)),v(t.getX(L+1)),v(t.getX(L+2))}}computeVertexNormals(){let t=this.index,e=this.getAttribute("position");if(e!==void 0){let n=this.getAttribute("normal");if(n===void 0)n=new Ut(new Float32Array(e.count*3),3),this.setAttribute("normal",n);else for(let u=0,f=n.count;u<f;u++)n.setXYZ(u,0,0,0);let s=new P,r=new P,o=new P,a=new P,l=new P,c=new P,h=new P,d=new P;if(t)for(let u=0,f=t.count;u<f;u+=3){let g=t.getX(u+0),y=t.getX(u+1),m=t.getX(u+2);s.fromBufferAttribute(e,g),r.fromBufferAttribute(e,y),o.fromBufferAttribute(e,m),h.subVectors(o,r),d.subVectors(s,r),h.cross(d),a.fromBufferAttribute(n,g),l.fromBufferAttribute(n,y),c.fromBufferAttribute(n,m),a.add(h),l.add(h),c.add(h),n.setXYZ(g,a.x,a.y,a.z),n.setXYZ(y,l.x,l.y,l.z),n.setXYZ(m,c.x,c.y,c.z)}else for(let u=0,f=e.count;u<f;u+=3)s.fromBufferAttribute(e,u+0),r.fromBufferAttribute(e,u+1),o.fromBufferAttribute(e,u+2),h.subVectors(o,r),d.subVectors(s,r),h.cross(d),n.setXYZ(u+0,h.x,h.y,h.z),n.setXYZ(u+1,h.x,h.y,h.z),n.setXYZ(u+2,h.x,h.y,h.z);this.normalizeNormals(),n.needsUpdate=!0}}normalizeNormals(){let t=this.attributes.normal;for(let e=0,n=t.count;e<n;e++)Ue.fromBufferAttribute(t,e),Ue.normalize(),t.setXYZ(e,Ue.x,Ue.y,Ue.z)}toNonIndexed(){function t(a,l){let c=a.array,h=a.itemSize,d=a.normalized,u=new c.constructor(l.length*h),f=0,g=0;for(let y=0,m=l.length;y<m;y++){a.isInterleavedBufferAttribute?f=l[y]*a.data.stride+a.offset:f=l[y]*h;for(let p=0;p<h;p++)u[g++]=c[f++]}return new Ut(u,h,d)}if(this.index===null)return console.warn("THREE.BufferGeometry.toNonIndexed(): BufferGeometry is already non-indexed."),this;let e=new i,n=this.index.array,s=this.attributes;for(let a in s){let l=s[a],c=t(l,n);e.setAttribute(a,c)}let r=this.morphAttributes;for(let a in r){let l=[],c=r[a];for(let h=0,d=c.length;h<d;h++){let u=c[h],f=t(u,n);l.push(f)}e.morphAttributes[a]=l}e.morphTargetsRelative=this.morphTargetsRelative;let o=this.groups;for(let a=0,l=o.length;a<l;a++){let c=o[a];e.addGroup(c.start,c.count,c.materialIndex)}return e}toJSON(){let t={metadata:{version:4.6,type:"BufferGeometry",generator:"BufferGeometry.toJSON"}};if(t.uuid=this.uuid,t.type=this.type,this.name!==""&&(t.name=this.name),Object.keys(this.userData).length>0&&(t.userData=this.userData),this.parameters!==void 0){let l=this.parameters;for(let c in l)l[c]!==void 0&&(t[c]=l[c]);return t}t.data={attributes:{}};let e=this.index;e!==null&&(t.data.index={type:e.array.constructor.name,array:Array.prototype.slice.call(e.array)});let n=this.attributes;for(let l in n){let c=n[l];t.data.attributes[l]=c.toJSON(t.data)}let s={},r=!1;for(let l in this.morphAttributes){let c=this.morphAttributes[l],h=[];for(let d=0,u=c.length;d<u;d++){let f=c[d];h.push(f.toJSON(t.data))}h.length>0&&(s[l]=h,r=!0)}r&&(t.data.morphAttributes=s,t.data.morphTargetsRelative=this.morphTargetsRelative);let o=this.groups;o.length>0&&(t.data.groups=JSON.parse(JSON.stringify(o)));let a=this.boundingSphere;return a!==null&&(t.data.boundingSphere={center:a.center.toArray(),radius:a.radius}),t}clone(){return new this.constructor().copy(this)}copy(t){this.index=null,this.attributes={},this.morphAttributes={},this.groups=[],this.boundingBox=null,this.boundingSphere=null;let e={};this.name=t.name;let n=t.index;n!==null&&this.setIndex(n.clone(e));let s=t.attributes;for(let c in s){let h=s[c];this.setAttribute(c,h.clone(e))}let r=t.morphAttributes;for(let c in r){let h=[],d=r[c];for(let u=0,f=d.length;u<f;u++)h.push(d[u].clone(e));this.morphAttributes[c]=h}this.morphTargetsRelative=t.morphTargetsRelative;let o=t.groups;for(let c=0,h=o.length;c<h;c++){let d=o[c];this.addGroup(d.start,d.count,d.materialIndex)}let a=t.boundingBox;a!==null&&(this.boundingBox=a.clone());let l=t.boundingSphere;return l!==null&&(this.boundingSphere=l.clone()),this.drawRange.start=t.drawRange.start,this.drawRange.count=t.drawRange.count,this.userData=t.userData,this}dispose(){this.dispatchEvent({type:"dispose"})}},Wc=new ne,Pi=new no,Cr=new yi,Xc=new P,Pr=new P,Ir=new P,Lr=new P,oa=new P,Dr=new P,qc=new P,Ur=new P,at=class extends Ze{constructor(t=new Yt,e=new Ss){super(),this.isMesh=!0,this.type="Mesh",this.geometry=t,this.material=e,this.updateMorphTargets()}copy(t,e){return super.copy(t,e),t.morphTargetInfluences!==void 0&&(this.morphTargetInfluences=t.morphTargetInfluences.slice()),t.morphTargetDictionary!==void 0&&(this.morphTargetDictionary=Object.assign({},t.morphTargetDictionary)),this.material=Array.isArray(t.material)?t.material.slice():t.material,this.geometry=t.geometry,this}updateMorphTargets(){let e=this.geometry.morphAttributes,n=Object.keys(e);if(n.length>0){let s=e[n[0]];if(s!==void 0){this.morphTargetInfluences=[],this.morphTargetDictionary={};for(let r=0,o=s.length;r<o;r++){let a=s[r].name||String(r);this.morphTargetInfluences.push(0),this.morphTargetDictionary[a]=r}}}}getVertexPosition(t,e){let n=this.geometry,s=n.attributes.position,r=n.morphAttributes.position,o=n.morphTargetsRelative;e.fromBufferAttribute(s,t);let a=this.morphTargetInfluences;if(r&&a){Dr.set(0,0,0);for(let l=0,c=r.length;l<c;l++){let h=a[l],d=r[l];h!==0&&(oa.fromBufferAttribute(d,t),o?Dr.addScaledVector(oa,h):Dr.addScaledVector(oa.sub(e),h))}e.add(Dr)}return e}raycast(t,e){let n=this.geometry,s=this.material,r=this.matrixWorld;s!==void 0&&(n.boundingSphere===null&&n.computeBoundingSphere(),Cr.copy(n.boundingSphere),Cr.applyMatrix4(r),Pi.copy(t.ray).recast(t.near),!(Cr.containsPoint(Pi.origin)===!1&&(Pi.intersectSphere(Cr,Xc)===null||Pi.origin.distanceToSquared(Xc)>(t.far-t.near)**2))&&(Wc.copy(r).invert(),Pi.copy(t.ray).applyMatrix4(Wc),!(n.boundingBox!==null&&Pi.intersectsBox(n.boundingBox)===!1)&&this._computeIntersections(t,e,Pi)))}_computeIntersections(t,e,n){let s,r=this.geometry,o=this.material,a=r.index,l=r.attributes.position,c=r.attributes.uv,h=r.attributes.uv1,d=r.attributes.normal,u=r.groups,f=r.drawRange;if(a!==null)if(Array.isArray(o))for(let g=0,y=u.length;g<y;g++){let m=u[g],p=o[m.materialIndex],w=Math.max(m.start,f.start),E=Math.min(a.count,Math.min(m.start+m.count,f.start+f.count));for(let M=w,U=E;M<U;M+=3){let C=a.getX(M),v=a.getX(M+1),b=a.getX(M+2);s=Nr(this,p,t,n,c,h,d,C,v,b),s&&(s.faceIndex=Math.floor(M/3),s.face.materialIndex=m.materialIndex,e.push(s))}}else{let g=Math.max(0,f.start),y=Math.min(a.count,f.start+f.count);for(let m=g,p=y;m<p;m+=3){let w=a.getX(m),E=a.getX(m+1),M=a.getX(m+2);s=Nr(this,o,t,n,c,h,d,w,E,M),s&&(s.faceIndex=Math.floor(m/3),e.push(s))}}else if(l!==void 0)if(Array.isArray(o))for(let g=0,y=u.length;g<y;g++){let m=u[g],p=o[m.materialIndex],w=Math.max(m.start,f.start),E=Math.min(l.count,Math.min(m.start+m.count,f.start+f.count));for(let M=w,U=E;M<U;M+=3){let C=M,v=M+1,b=M+2;s=Nr(this,p,t,n,c,h,d,C,v,b),s&&(s.faceIndex=Math.floor(M/3),s.face.materialIndex=m.materialIndex,e.push(s))}}else{let g=Math.max(0,f.start),y=Math.min(l.count,f.start+f.count);for(let m=g,p=y;m<p;m+=3){let w=m,E=m+1,M=m+2;s=Nr(this,o,t,n,c,h,d,w,E,M),s&&(s.faceIndex=Math.floor(m/3),e.push(s))}}}};function Id(i,t,e,n,s,r,o,a){let l;if(t.side===Re?l=n.intersectTriangle(o,r,s,!0,a):l=n.intersectTriangle(s,r,o,t.side===xi,a),l===null)return null;Ur.copy(a),Ur.applyMatrix4(i.matrixWorld);let c=e.ray.origin.distanceTo(Ur);return c<e.near||c>e.far?null:{distance:c,point:Ur.clone(),object:i}}function Nr(i,t,e,n,s,r,o,a,l,c){i.getVertexPosition(a,Pr),i.getVertexPosition(l,Ir),i.getVertexPosition(c,Lr);let h=Id(i,t,e,n,Pr,Ir,Lr,qc);if(h){let d=new P;Fi.getBarycoord(qc,Pr,Ir,Lr,d),s&&(h.uv=Fi.getInterpolatedAttribute(s,a,l,c,d,new gt)),r&&(h.uv1=Fi.getInterpolatedAttribute(r,a,l,c,d,new gt)),o&&(h.normal=Fi.getInterpolatedAttribute(o,a,l,c,d,new P),h.normal.dot(n.direction)>0&&h.normal.multiplyScalar(-1));let u={a,b:l,c,normal:new P,materialIndex:0};Fi.getNormal(Pr,Ir,Lr,u.normal),h.face=u,h.barycoord=d}return h}var dn=class i extends Yt{constructor(t=1,e=1,n=1,s=1,r=1,o=1){super(),this.type="BoxGeometry",this.parameters={width:t,height:e,depth:n,widthSegments:s,heightSegments:r,depthSegments:o};let a=this;s=Math.floor(s),r=Math.floor(r),o=Math.floor(o);let l=[],c=[],h=[],d=[],u=0,f=0;g("z","y","x",-1,-1,n,e,t,o,r,0),g("z","y","x",1,-1,n,e,-t,o,r,1),g("x","z","y",1,1,t,n,e,s,o,2),g("x","z","y",1,-1,t,n,-e,s,o,3),g("x","y","z",1,-1,t,e,n,s,r,4),g("x","y","z",-1,-1,t,e,-n,s,r,5),this.setIndex(l),this.setAttribute("position",new Bt(c,3)),this.setAttribute("normal",new Bt(h,3)),this.setAttribute("uv",new Bt(d,2));function g(y,m,p,w,E,M,U,C,v,b,x){let _=M/v,R=U/b,I=M/2,L=U/2,N=C/2,V=v+1,F=b+1,X=0,z=0,j=new P;for(let it=0;it<F;it++){let st=it*R-L;for(let yt=0;yt<V;yt++){let Ft=yt*_-I;j[y]=Ft*w,j[m]=st*E,j[p]=N,c.push(j.x,j.y,j.z),j[y]=0,j[m]=0,j[p]=C>0?1:-1,h.push(j.x,j.y,j.z),d.push(yt/v),d.push(1-it/b),X+=1}}for(let it=0;it<b;it++)for(let st=0;st<v;st++){let yt=u+st+V*it,Ft=u+st+V*(it+1),$=u+(st+1)+V*(it+1),nt=u+(st+1)+V*it;l.push(yt,Ft,nt),l.push(Ft,$,nt),z+=6}a.addGroup(f,z,x),f+=z,u+=X}}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}static fromJSON(t){return new i(t.width,t.height,t.depth,t.widthSegments,t.heightSegments,t.depthSegments)}};function bs(i){let t={};for(let e in i){t[e]={};for(let n in i[e]){let s=i[e][n];s&&(s.isColor||s.isMatrix3||s.isMatrix4||s.isVector2||s.isVector3||s.isVector4||s.isTexture||s.isQuaternion)?s.isRenderTargetTexture?(console.warn("UniformsUtils: Textures of render targets cannot be cloned via cloneUniforms() or mergeUniforms()."),t[e][n]=null):t[e][n]=s.clone():Array.isArray(s)?t[e][n]=s.slice():t[e][n]=s}}return t}function $e(i){let t={};for(let e=0;e<i.length;e++){let n=bs(i[e]);for(let s in n)t[s]=n[s]}return t}function Ld(i){let t=[];for(let e=0;e<i.length;e++)t.push(i[e].clone());return t}function Gh(i){let t=i.getRenderTarget();return t===null?i.outputColorSpace:t.isXRRenderTarget===!0?t.texture.colorSpace:jt.workingColorSpace}var Dd={clone:bs,merge:$e},Ud=`void main() {
	gl_Position = projectionMatrix * modelViewMatrix * vec4( position, 1.0 );
}`,Nd=`void main() {
	gl_FragColor = vec4( 1.0, 0.0, 0.0, 1.0 );
}`,sn=class extends Mi{static get type(){return"ShaderMaterial"}constructor(t){super(),this.isShaderMaterial=!0,this.defines={},this.uniforms={},this.uniformsGroups=[],this.vertexShader=Ud,this.fragmentShader=Nd,this.linewidth=1,this.wireframe=!1,this.wireframeLinewidth=1,this.fog=!1,this.lights=!1,this.clipping=!1,this.forceSinglePass=!0,this.extensions={clipCullDistance:!1,multiDraw:!1},this.defaultAttributeValues={color:[1,1,1],uv:[0,0],uv1:[0,0]},this.index0AttributeName=void 0,this.uniformsNeedUpdate=!1,this.glslVersion=null,t!==void 0&&this.setValues(t)}copy(t){return super.copy(t),this.fragmentShader=t.fragmentShader,this.vertexShader=t.vertexShader,this.uniforms=bs(t.uniforms),this.uniformsGroups=Ld(t.uniformsGroups),this.defines=Object.assign({},t.defines),this.wireframe=t.wireframe,this.wireframeLinewidth=t.wireframeLinewidth,this.fog=t.fog,this.lights=t.lights,this.clipping=t.clipping,this.extensions=Object.assign({},t.extensions),this.glslVersion=t.glslVersion,this}toJSON(t){let e=super.toJSON(t);e.glslVersion=this.glslVersion,e.uniforms={};for(let s in this.uniforms){let o=this.uniforms[s].value;o&&o.isTexture?e.uniforms[s]={type:"t",value:o.toJSON(t).uuid}:o&&o.isColor?e.uniforms[s]={type:"c",value:o.getHex()}:o&&o.isVector2?e.uniforms[s]={type:"v2",value:o.toArray()}:o&&o.isVector3?e.uniforms[s]={type:"v3",value:o.toArray()}:o&&o.isVector4?e.uniforms[s]={type:"v4",value:o.toArray()}:o&&o.isMatrix3?e.uniforms[s]={type:"m3",value:o.toArray()}:o&&o.isMatrix4?e.uniforms[s]={type:"m4",value:o.toArray()}:e.uniforms[s]={value:o}}Object.keys(this.defines).length>0&&(e.defines=this.defines),e.vertexShader=this.vertexShader,e.fragmentShader=this.fragmentShader,e.lights=this.lights,e.clipping=this.clipping;let n={};for(let s in this.extensions)this.extensions[s]===!0&&(n[s]=!0);return Object.keys(n).length>0&&(e.extensions=n),e}},oo=class extends Ze{constructor(){super(),this.isCamera=!0,this.type="Camera",this.matrixWorldInverse=new ne,this.projectionMatrix=new ne,this.projectionMatrixInverse=new ne,this.coordinateSystem=ii}copy(t,e){return super.copy(t,e),this.matrixWorldInverse.copy(t.matrixWorldInverse),this.projectionMatrix.copy(t.projectionMatrix),this.projectionMatrixInverse.copy(t.projectionMatrixInverse),this.coordinateSystem=t.coordinateSystem,this}getWorldDirection(t){return super.getWorldDirection(t).negate()}updateMatrixWorld(t){super.updateMatrixWorld(t),this.matrixWorldInverse.copy(this.matrixWorld).invert()}updateWorldMatrix(t,e){super.updateWorldMatrix(t,e),this.matrixWorldInverse.copy(this.matrixWorld).invert()}clone(){return new this.constructor().copy(this)}},mi=new P,Yc=new gt,$c=new gt,Fe=class extends oo{constructor(t=50,e=1,n=.1,s=2e3){super(),this.isPerspectiveCamera=!0,this.type="PerspectiveCamera",this.fov=t,this.zoom=1,this.near=n,this.far=s,this.focus=10,this.aspect=e,this.view=null,this.filmGauge=35,this.filmOffset=0,this.updateProjectionMatrix()}copy(t,e){return super.copy(t,e),this.fov=t.fov,this.zoom=t.zoom,this.near=t.near,this.far=t.far,this.focus=t.focus,this.aspect=t.aspect,this.view=t.view===null?null:Object.assign({},t.view),this.filmGauge=t.filmGauge,this.filmOffset=t.filmOffset,this}setFocalLength(t){let e=.5*this.getFilmHeight()/t;this.fov=sl*2*Math.atan(e),this.updateProjectionMatrix()}getFocalLength(){let t=Math.tan(Ho*.5*this.fov);return .5*this.getFilmHeight()/t}getEffectiveFOV(){return sl*2*Math.atan(Math.tan(Ho*.5*this.fov)/this.zoom)}getFilmWidth(){return this.filmGauge*Math.min(this.aspect,1)}getFilmHeight(){return this.filmGauge/Math.max(this.aspect,1)}getViewBounds(t,e,n){mi.set(-1,-1,.5).applyMatrix4(this.projectionMatrixInverse),e.set(mi.x,mi.y).multiplyScalar(-t/mi.z),mi.set(1,1,.5).applyMatrix4(this.projectionMatrixInverse),n.set(mi.x,mi.y).multiplyScalar(-t/mi.z)}getViewSize(t,e){return this.getViewBounds(t,Yc,$c),e.subVectors($c,Yc)}setViewOffset(t,e,n,s,r,o){this.aspect=t/e,this.view===null&&(this.view={enabled:!0,fullWidth:1,fullHeight:1,offsetX:0,offsetY:0,width:1,height:1}),this.view.enabled=!0,this.view.fullWidth=t,this.view.fullHeight=e,this.view.offsetX=n,this.view.offsetY=s,this.view.width=r,this.view.height=o,this.updateProjectionMatrix()}clearViewOffset(){this.view!==null&&(this.view.enabled=!1),this.updateProjectionMatrix()}updateProjectionMatrix(){let t=this.near,e=t*Math.tan(Ho*.5*this.fov)/this.zoom,n=2*e,s=this.aspect*n,r=-.5*s,o=this.view;if(this.view!==null&&this.view.enabled){let l=o.fullWidth,c=o.fullHeight;r+=o.offsetX*s/l,e-=o.offsetY*n/c,s*=o.width/l,n*=o.height/c}let a=this.filmOffset;a!==0&&(r+=t*a/this.getFilmWidth()),this.projectionMatrix.makePerspective(r,r+s,e,e-n,t,this.far,this.coordinateSystem),this.projectionMatrixInverse.copy(this.projectionMatrix).invert()}toJSON(t){let e=super.toJSON(t);return e.object.fov=this.fov,e.object.zoom=this.zoom,e.object.near=this.near,e.object.far=this.far,e.object.focus=this.focus,e.object.aspect=this.aspect,this.view!==null&&(e.object.view=Object.assign({},this.view)),e.object.filmGauge=this.filmGauge,e.object.filmOffset=this.filmOffset,e}},as=-90,ls=1,ll=class extends Ze{constructor(t,e,n){super(),this.type="CubeCamera",this.renderTarget=n,this.coordinateSystem=null,this.activeMipmapLevel=0;let s=new Fe(as,ls,t,e);s.layers=this.layers,this.add(s);let r=new Fe(as,ls,t,e);r.layers=this.layers,this.add(r);let o=new Fe(as,ls,t,e);o.layers=this.layers,this.add(o);let a=new Fe(as,ls,t,e);a.layers=this.layers,this.add(a);let l=new Fe(as,ls,t,e);l.layers=this.layers,this.add(l);let c=new Fe(as,ls,t,e);c.layers=this.layers,this.add(c)}updateCoordinateSystem(){let t=this.coordinateSystem,e=this.children.concat(),[n,s,r,o,a,l]=e;for(let c of e)this.remove(c);if(t===ii)n.up.set(0,1,0),n.lookAt(1,0,0),s.up.set(0,1,0),s.lookAt(-1,0,0),r.up.set(0,0,-1),r.lookAt(0,1,0),o.up.set(0,0,1),o.lookAt(0,-1,0),a.up.set(0,1,0),a.lookAt(0,0,1),l.up.set(0,1,0),l.lookAt(0,0,-1);else if(t===jr)n.up.set(0,-1,0),n.lookAt(-1,0,0),s.up.set(0,-1,0),s.lookAt(1,0,0),r.up.set(0,0,1),r.lookAt(0,1,0),o.up.set(0,0,-1),o.lookAt(0,-1,0),a.up.set(0,-1,0),a.lookAt(0,0,1),l.up.set(0,-1,0),l.lookAt(0,0,-1);else throw new Error("THREE.CubeCamera.updateCoordinateSystem(): Invalid coordinate system: "+t);for(let c of e)this.add(c),c.updateMatrixWorld()}update(t,e){this.parent===null&&this.updateMatrixWorld();let{renderTarget:n,activeMipmapLevel:s}=this;this.coordinateSystem!==t.coordinateSystem&&(this.coordinateSystem=t.coordinateSystem,this.updateCoordinateSystem());let[r,o,a,l,c,h]=this.children,d=t.getRenderTarget(),u=t.getActiveCubeFace(),f=t.getActiveMipmapLevel(),g=t.xr.enabled;t.xr.enabled=!1;let y=n.texture.generateMipmaps;n.texture.generateMipmaps=!1,t.setRenderTarget(n,0,s),t.render(e,r),t.setRenderTarget(n,1,s),t.render(e,o),t.setRenderTarget(n,2,s),t.render(e,a),t.setRenderTarget(n,3,s),t.render(e,l),t.setRenderTarget(n,4,s),t.render(e,c),n.texture.generateMipmaps=y,t.setRenderTarget(n,5,s),t.render(e,h),t.setRenderTarget(d,u,f),t.xr.enabled=g,n.texture.needsPMREMUpdate=!0}},ao=class extends nn{constructor(t,e,n,s,r,o,a,l,c,h){t=t!==void 0?t:[],e=e!==void 0?e:vs,super(t,e,n,s,r,o,a,l,c,h),this.isCubeTexture=!0,this.flipY=!1}get images(){return this.image}set images(t){this.image=t}},cl=class extends oi{constructor(t=1,e={}){super(t,t,e),this.isWebGLCubeRenderTarget=!0;let n={width:t,height:t,depth:1},s=[n,n,n,n,n,n];this.texture=new ao(s,e.mapping,e.wrapS,e.wrapT,e.magFilter,e.minFilter,e.format,e.type,e.anisotropy,e.colorSpace),this.texture.isRenderTargetTexture=!0,this.texture.generateMipmaps=e.generateMipmaps!==void 0?e.generateMipmaps:!1,this.texture.minFilter=e.minFilter!==void 0?e.minFilter:Bn}fromEquirectangularTexture(t,e){this.texture.type=e.type,this.texture.colorSpace=e.colorSpace,this.texture.generateMipmaps=e.generateMipmaps,this.texture.minFilter=e.minFilter,this.texture.magFilter=e.magFilter;let n={uniforms:{tEquirect:{value:null}},vertexShader:`

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
			`},s=new dn(5,5,5),r=new sn({name:"CubemapFromEquirect",uniforms:bs(n.uniforms),vertexShader:n.vertexShader,fragmentShader:n.fragmentShader,side:Re,blending:gi});r.uniforms.tEquirect.value=e;let o=new at(s,r),a=e.minFilter;return e.minFilter===ni&&(e.minFilter=Bn),new ll(1,10,this).update(t,o),e.minFilter=a,o.geometry.dispose(),o.material.dispose(),this}clear(t,e,n,s){let r=t.getRenderTarget();for(let o=0;o<6;o++)t.setRenderTarget(this,o),t.clear(e,n,s);t.setRenderTarget(r)}},aa=new P,Fd=new P,Od=new Ot,Rn=class{constructor(t=new P(1,0,0),e=0){this.isPlane=!0,this.normal=t,this.constant=e}set(t,e){return this.normal.copy(t),this.constant=e,this}setComponents(t,e,n,s){return this.normal.set(t,e,n),this.constant=s,this}setFromNormalAndCoplanarPoint(t,e){return this.normal.copy(t),this.constant=-e.dot(this.normal),this}setFromCoplanarPoints(t,e,n){let s=aa.subVectors(n,e).cross(Fd.subVectors(t,e)).normalize();return this.setFromNormalAndCoplanarPoint(s,t),this}copy(t){return this.normal.copy(t.normal),this.constant=t.constant,this}normalize(){let t=1/this.normal.length();return this.normal.multiplyScalar(t),this.constant*=t,this}negate(){return this.constant*=-1,this.normal.negate(),this}distanceToPoint(t){return this.normal.dot(t)+this.constant}distanceToSphere(t){return this.distanceToPoint(t.center)-t.radius}projectPoint(t,e){return e.copy(t).addScaledVector(this.normal,-this.distanceToPoint(t))}intersectLine(t,e){let n=t.delta(aa),s=this.normal.dot(n);if(s===0)return this.distanceToPoint(t.start)===0?e.copy(t.start):null;let r=-(t.start.dot(this.normal)+this.constant)/s;return r<0||r>1?null:e.copy(t.start).addScaledVector(n,r)}intersectsLine(t){let e=this.distanceToPoint(t.start),n=this.distanceToPoint(t.end);return e<0&&n>0||n<0&&e>0}intersectsBox(t){return t.intersectsPlane(this)}intersectsSphere(t){return t.intersectsPlane(this)}coplanarPoint(t){return t.copy(this.normal).multiplyScalar(-this.constant)}applyMatrix4(t,e){let n=e||Od.getNormalMatrix(t),s=this.coplanarPoint(aa).applyMatrix4(t),r=this.normal.applyMatrix3(n).normalize();return this.constant=-s.dot(r),this}translate(t){return this.constant-=t.dot(this.normal),this}equals(t){return t.normal.equals(this.normal)&&t.constant===this.constant}clone(){return new this.constructor().copy(this)}},Ii=new yi,Fr=new P,ir=class{constructor(t=new Rn,e=new Rn,n=new Rn,s=new Rn,r=new Rn,o=new Rn){this.planes=[t,e,n,s,r,o]}set(t,e,n,s,r,o){let a=this.planes;return a[0].copy(t),a[1].copy(e),a[2].copy(n),a[3].copy(s),a[4].copy(r),a[5].copy(o),this}copy(t){let e=this.planes;for(let n=0;n<6;n++)e[n].copy(t.planes[n]);return this}setFromProjectionMatrix(t,e=ii){let n=this.planes,s=t.elements,r=s[0],o=s[1],a=s[2],l=s[3],c=s[4],h=s[5],d=s[6],u=s[7],f=s[8],g=s[9],y=s[10],m=s[11],p=s[12],w=s[13],E=s[14],M=s[15];if(n[0].setComponents(l-r,u-c,m-f,M-p).normalize(),n[1].setComponents(l+r,u+c,m+f,M+p).normalize(),n[2].setComponents(l+o,u+h,m+g,M+w).normalize(),n[3].setComponents(l-o,u-h,m-g,M-w).normalize(),n[4].setComponents(l-a,u-d,m-y,M-E).normalize(),e===ii)n[5].setComponents(l+a,u+d,m+y,M+E).normalize();else if(e===jr)n[5].setComponents(a,d,y,E).normalize();else throw new Error("THREE.Frustum.setFromProjectionMatrix(): Invalid coordinate system: "+e);return this}intersectsObject(t){if(t.boundingSphere!==void 0)t.boundingSphere===null&&t.computeBoundingSphere(),Ii.copy(t.boundingSphere).applyMatrix4(t.matrixWorld);else{let e=t.geometry;e.boundingSphere===null&&e.computeBoundingSphere(),Ii.copy(e.boundingSphere).applyMatrix4(t.matrixWorld)}return this.intersectsSphere(Ii)}intersectsSprite(t){return Ii.center.set(0,0,0),Ii.radius=.7071067811865476,Ii.applyMatrix4(t.matrixWorld),this.intersectsSphere(Ii)}intersectsSphere(t){let e=this.planes,n=t.center,s=-t.radius;for(let r=0;r<6;r++)if(e[r].distanceToPoint(n)<s)return!1;return!0}intersectsBox(t){let e=this.planes;for(let n=0;n<6;n++){let s=e[n];if(Fr.x=s.normal.x>0?t.max.x:t.min.x,Fr.y=s.normal.y>0?t.max.y:t.min.y,Fr.z=s.normal.z>0?t.max.z:t.min.z,s.distanceToPoint(Fr)<0)return!1}return!0}containsPoint(t){let e=this.planes;for(let n=0;n<6;n++)if(e[n].distanceToPoint(t)<0)return!1;return!0}clone(){return new this.constructor().copy(this)}};function Wh(){let i=null,t=!1,e=null,n=null;function s(r,o){e(r,o),n=i.requestAnimationFrame(s)}return{start:function(){t!==!0&&e!==null&&(n=i.requestAnimationFrame(s),t=!0)},stop:function(){i.cancelAnimationFrame(n),t=!1},setAnimationLoop:function(r){e=r},setContext:function(r){i=r}}}function Bd(i){let t=new WeakMap;function e(a,l){let c=a.array,h=a.usage,d=c.byteLength,u=i.createBuffer();i.bindBuffer(l,u),i.bufferData(l,c,h),a.onUploadCallback();let f;if(c instanceof Float32Array)f=i.FLOAT;else if(c instanceof Uint16Array)a.isFloat16BufferAttribute?f=i.HALF_FLOAT:f=i.UNSIGNED_SHORT;else if(c instanceof Int16Array)f=i.SHORT;else if(c instanceof Uint32Array)f=i.UNSIGNED_INT;else if(c instanceof Int32Array)f=i.INT;else if(c instanceof Int8Array)f=i.BYTE;else if(c instanceof Uint8Array)f=i.UNSIGNED_BYTE;else if(c instanceof Uint8ClampedArray)f=i.UNSIGNED_BYTE;else throw new Error("THREE.WebGLAttributes: Unsupported buffer data format: "+c);return{buffer:u,type:f,bytesPerElement:c.BYTES_PER_ELEMENT,version:a.version,size:d}}function n(a,l,c){let h=l.array,d=l.updateRanges;if(i.bindBuffer(c,a),d.length===0)i.bufferSubData(c,0,h);else{d.sort((f,g)=>f.start-g.start);let u=0;for(let f=1;f<d.length;f++){let g=d[u],y=d[f];y.start<=g.start+g.count+1?g.count=Math.max(g.count,y.start+y.count-g.start):(++u,d[u]=y)}d.length=u+1;for(let f=0,g=d.length;f<g;f++){let y=d[f];i.bufferSubData(c,y.start*h.BYTES_PER_ELEMENT,h,y.start,y.count)}l.clearUpdateRanges()}l.onUploadCallback()}function s(a){return a.isInterleavedBufferAttribute&&(a=a.data),t.get(a)}function r(a){a.isInterleavedBufferAttribute&&(a=a.data);let l=t.get(a);l&&(i.deleteBuffer(l.buffer),t.delete(a))}function o(a,l){if(a.isInterleavedBufferAttribute&&(a=a.data),a.isGLBufferAttribute){let h=t.get(a);(!h||h.version<a.version)&&t.set(a,{buffer:a.buffer,type:a.type,bytesPerElement:a.elementSize,version:a.version});return}let c=t.get(a);if(c===void 0)t.set(a,e(a,l));else if(c.version<a.version){if(c.size!==a.array.byteLength)throw new Error("THREE.WebGLAttributes: The size of the buffer attribute's array buffer does not match the original size. Resizing buffer attributes is not supported.");n(c.buffer,a,l),c.version=a.version}}return{get:s,remove:r,update:o}}var Je=class i extends Yt{constructor(t=1,e=1,n=1,s=1){super(),this.type="PlaneGeometry",this.parameters={width:t,height:e,widthSegments:n,heightSegments:s};let r=t/2,o=e/2,a=Math.floor(n),l=Math.floor(s),c=a+1,h=l+1,d=t/a,u=e/l,f=[],g=[],y=[],m=[];for(let p=0;p<h;p++){let w=p*u-o;for(let E=0;E<c;E++){let M=E*d-r;g.push(M,-w,0),y.push(0,0,1),m.push(E/a),m.push(1-p/l)}}for(let p=0;p<l;p++)for(let w=0;w<a;w++){let E=w+c*p,M=w+c*(p+1),U=w+1+c*(p+1),C=w+1+c*p;f.push(E,M,C),f.push(M,U,C)}this.setIndex(f),this.setAttribute("position",new Bt(g,3)),this.setAttribute("normal",new Bt(y,3)),this.setAttribute("uv",new Bt(m,2))}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}static fromJSON(t){return new i(t.width,t.height,t.widthSegments,t.heightSegments)}},zd=`#ifdef USE_ALPHAHASH
	if ( diffuseColor.a < getAlphaHashThreshold( vPosition ) ) discard;
#endif`,Hd=`#ifdef USE_ALPHAHASH
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
#endif`,kd=`#ifdef USE_ALPHAMAP
	diffuseColor.a *= texture2D( alphaMap, vAlphaMapUv ).g;
#endif`,Vd=`#ifdef USE_ALPHAMAP
	uniform sampler2D alphaMap;
#endif`,Gd=`#ifdef USE_ALPHATEST
	#ifdef ALPHA_TO_COVERAGE
	diffuseColor.a = smoothstep( alphaTest, alphaTest + fwidth( diffuseColor.a ), diffuseColor.a );
	if ( diffuseColor.a == 0.0 ) discard;
	#else
	if ( diffuseColor.a < alphaTest ) discard;
	#endif
#endif`,Wd=`#ifdef USE_ALPHATEST
	uniform float alphaTest;
#endif`,Xd=`#ifdef USE_AOMAP
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
#endif`,qd=`#ifdef USE_AOMAP
	uniform sampler2D aoMap;
	uniform float aoMapIntensity;
#endif`,Yd=`#ifdef USE_BATCHING
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
#endif`,$d=`#ifdef USE_BATCHING
	mat4 batchingMatrix = getBatchingMatrix( getIndirectIndex( gl_DrawID ) );
#endif`,Zd=`vec3 transformed = vec3( position );
#ifdef USE_ALPHAHASH
	vPosition = vec3( position );
#endif`,Jd=`vec3 objectNormal = vec3( normal );
#ifdef USE_TANGENT
	vec3 objectTangent = vec3( tangent.xyz );
#endif`,Kd=`float G_BlinnPhong_Implicit( ) {
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
} // validated`,jd=`#ifdef USE_IRIDESCENCE
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
#endif`,Qd=`#ifdef USE_BUMPMAP
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
#endif`,tf=`#if NUM_CLIPPING_PLANES > 0
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
#endif`,ef=`#if NUM_CLIPPING_PLANES > 0
	varying vec3 vClipPosition;
	uniform vec4 clippingPlanes[ NUM_CLIPPING_PLANES ];
#endif`,nf=`#if NUM_CLIPPING_PLANES > 0
	varying vec3 vClipPosition;
#endif`,sf=`#if NUM_CLIPPING_PLANES > 0
	vClipPosition = - mvPosition.xyz;
#endif`,rf=`#if defined( USE_COLOR_ALPHA )
	diffuseColor *= vColor;
#elif defined( USE_COLOR )
	diffuseColor.rgb *= vColor;
#endif`,of=`#if defined( USE_COLOR_ALPHA )
	varying vec4 vColor;
#elif defined( USE_COLOR )
	varying vec3 vColor;
#endif`,af=`#if defined( USE_COLOR_ALPHA )
	varying vec4 vColor;
#elif defined( USE_COLOR ) || defined( USE_INSTANCING_COLOR ) || defined( USE_BATCHING_COLOR )
	varying vec3 vColor;
#endif`,lf=`#if defined( USE_COLOR_ALPHA )
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
#endif`,cf=`#define PI 3.141592653589793
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
} // validated`,hf=`#ifdef ENVMAP_TYPE_CUBE_UV
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
#endif`,uf=`vec3 transformedNormal = objectNormal;
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
#endif`,df=`#ifdef USE_DISPLACEMENTMAP
	uniform sampler2D displacementMap;
	uniform float displacementScale;
	uniform float displacementBias;
#endif`,ff=`#ifdef USE_DISPLACEMENTMAP
	transformed += normalize( objectNormal ) * ( texture2D( displacementMap, vDisplacementMapUv ).x * displacementScale + displacementBias );
#endif`,pf=`#ifdef USE_EMISSIVEMAP
	vec4 emissiveColor = texture2D( emissiveMap, vEmissiveMapUv );
	#ifdef DECODE_VIDEO_TEXTURE_EMISSIVE
		emissiveColor = sRGBTransferEOTF( emissiveColor );
	#endif
	totalEmissiveRadiance *= emissiveColor.rgb;
#endif`,mf=`#ifdef USE_EMISSIVEMAP
	uniform sampler2D emissiveMap;
#endif`,gf="gl_FragColor = linearToOutputTexel( gl_FragColor );",vf=`vec4 LinearTransferOETF( in vec4 value ) {
	return value;
}
vec4 sRGBTransferEOTF( in vec4 value ) {
	return vec4( mix( pow( value.rgb * 0.9478672986 + vec3( 0.0521327014 ), vec3( 2.4 ) ), value.rgb * 0.0773993808, vec3( lessThanEqual( value.rgb, vec3( 0.04045 ) ) ) ), value.a );
}
vec4 sRGBTransferOETF( in vec4 value ) {
	return vec4( mix( pow( value.rgb, vec3( 0.41666 ) ) * 1.055 - vec3( 0.055 ), value.rgb * 12.92, vec3( lessThanEqual( value.rgb, vec3( 0.0031308 ) ) ) ), value.a );
}`,xf=`#ifdef USE_ENVMAP
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
#endif`,_f=`#ifdef USE_ENVMAP
	uniform float envMapIntensity;
	uniform float flipEnvMap;
	uniform mat3 envMapRotation;
	#ifdef ENVMAP_TYPE_CUBE
		uniform samplerCube envMap;
	#else
		uniform sampler2D envMap;
	#endif
	
#endif`,yf=`#ifdef USE_ENVMAP
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
#endif`,Mf=`#ifdef USE_ENVMAP
	#if defined( USE_BUMPMAP ) || defined( USE_NORMALMAP ) || defined( PHONG ) || defined( LAMBERT )
		#define ENV_WORLDPOS
	#endif
	#ifdef ENV_WORLDPOS
		
		varying vec3 vWorldPosition;
	#else
		varying vec3 vReflect;
		uniform float refractionRatio;
	#endif
#endif`,Sf=`#ifdef USE_ENVMAP
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
#endif`,bf=`#ifdef USE_FOG
	vFogDepth = - mvPosition.z;
#endif`,Ef=`#ifdef USE_FOG
	varying float vFogDepth;
#endif`,wf=`#ifdef USE_FOG
	#ifdef FOG_EXP2
		float fogFactor = 1.0 - exp( - fogDensity * fogDensity * vFogDepth * vFogDepth );
	#else
		float fogFactor = smoothstep( fogNear, fogFar, vFogDepth );
	#endif
	gl_FragColor.rgb = mix( gl_FragColor.rgb, fogColor, fogFactor );
#endif`,Tf=`#ifdef USE_FOG
	uniform vec3 fogColor;
	varying float vFogDepth;
	#ifdef FOG_EXP2
		uniform float fogDensity;
	#else
		uniform float fogNear;
		uniform float fogFar;
	#endif
#endif`,Af=`#ifdef USE_GRADIENTMAP
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
}`,Rf=`#ifdef USE_LIGHTMAP
	uniform sampler2D lightMap;
	uniform float lightMapIntensity;
#endif`,Cf=`LambertMaterial material;
material.diffuseColor = diffuseColor.rgb;
material.specularStrength = specularStrength;`,Pf=`varying vec3 vViewPosition;
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
#define RE_IndirectDiffuse		RE_IndirectDiffuse_Lambert`,If=`uniform bool receiveShadow;
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
#endif`,Lf=`#ifdef USE_ENVMAP
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
#endif`,Df=`ToonMaterial material;
material.diffuseColor = diffuseColor.rgb;`,Uf=`varying vec3 vViewPosition;
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
#define RE_IndirectDiffuse		RE_IndirectDiffuse_Toon`,Nf=`BlinnPhongMaterial material;
material.diffuseColor = diffuseColor.rgb;
material.specularColor = specular;
material.specularShininess = shininess;
material.specularStrength = specularStrength;`,Ff=`varying vec3 vViewPosition;
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
#define RE_IndirectDiffuse		RE_IndirectDiffuse_BlinnPhong`,Of=`PhysicalMaterial material;
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
#endif`,Bf=`struct PhysicalMaterial {
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
}`,zf=`
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
#endif`,Hf=`#if defined( RE_IndirectDiffuse )
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
#endif`,kf=`#if defined( RE_IndirectDiffuse )
	RE_IndirectDiffuse( irradiance, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
#endif
#if defined( RE_IndirectSpecular )
	RE_IndirectSpecular( radiance, iblIrradiance, clearcoatRadiance, geometryPosition, geometryNormal, geometryViewDir, geometryClearcoatNormal, material, reflectedLight );
#endif`,Vf=`#if defined( USE_LOGDEPTHBUF )
	gl_FragDepth = vIsPerspective == 0.0 ? gl_FragCoord.z : log2( vFragDepth ) * logDepthBufFC * 0.5;
#endif`,Gf=`#if defined( USE_LOGDEPTHBUF )
	uniform float logDepthBufFC;
	varying float vFragDepth;
	varying float vIsPerspective;
#endif`,Wf=`#ifdef USE_LOGDEPTHBUF
	varying float vFragDepth;
	varying float vIsPerspective;
#endif`,Xf=`#ifdef USE_LOGDEPTHBUF
	vFragDepth = 1.0 + gl_Position.w;
	vIsPerspective = float( isPerspectiveMatrix( projectionMatrix ) );
#endif`,qf=`#ifdef USE_MAP
	vec4 sampledDiffuseColor = texture2D( map, vMapUv );
	#ifdef DECODE_VIDEO_TEXTURE
		sampledDiffuseColor = sRGBTransferEOTF( sampledDiffuseColor );
	#endif
	diffuseColor *= sampledDiffuseColor;
#endif`,Yf=`#ifdef USE_MAP
	uniform sampler2D map;
#endif`,$f=`#if defined( USE_MAP ) || defined( USE_ALPHAMAP )
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
#endif`,Zf=`#if defined( USE_POINTS_UV )
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
#endif`,Jf=`float metalnessFactor = metalness;
#ifdef USE_METALNESSMAP
	vec4 texelMetalness = texture2D( metalnessMap, vMetalnessMapUv );
	metalnessFactor *= texelMetalness.b;
#endif`,Kf=`#ifdef USE_METALNESSMAP
	uniform sampler2D metalnessMap;
#endif`,jf=`#ifdef USE_INSTANCING_MORPH
	float morphTargetInfluences[ MORPHTARGETS_COUNT ];
	float morphTargetBaseInfluence = texelFetch( morphTexture, ivec2( 0, gl_InstanceID ), 0 ).r;
	for ( int i = 0; i < MORPHTARGETS_COUNT; i ++ ) {
		morphTargetInfluences[i] =  texelFetch( morphTexture, ivec2( i + 1, gl_InstanceID ), 0 ).r;
	}
#endif`,Qf=`#if defined( USE_MORPHCOLORS )
	vColor *= morphTargetBaseInfluence;
	for ( int i = 0; i < MORPHTARGETS_COUNT; i ++ ) {
		#if defined( USE_COLOR_ALPHA )
			if ( morphTargetInfluences[ i ] != 0.0 ) vColor += getMorph( gl_VertexID, i, 2 ) * morphTargetInfluences[ i ];
		#elif defined( USE_COLOR )
			if ( morphTargetInfluences[ i ] != 0.0 ) vColor += getMorph( gl_VertexID, i, 2 ).rgb * morphTargetInfluences[ i ];
		#endif
	}
#endif`,tp=`#ifdef USE_MORPHNORMALS
	objectNormal *= morphTargetBaseInfluence;
	for ( int i = 0; i < MORPHTARGETS_COUNT; i ++ ) {
		if ( morphTargetInfluences[ i ] != 0.0 ) objectNormal += getMorph( gl_VertexID, i, 1 ).xyz * morphTargetInfluences[ i ];
	}
#endif`,ep=`#ifdef USE_MORPHTARGETS
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
#endif`,np=`#ifdef USE_MORPHTARGETS
	transformed *= morphTargetBaseInfluence;
	for ( int i = 0; i < MORPHTARGETS_COUNT; i ++ ) {
		if ( morphTargetInfluences[ i ] != 0.0 ) transformed += getMorph( gl_VertexID, i, 0 ).xyz * morphTargetInfluences[ i ];
	}
#endif`,ip=`float faceDirection = gl_FrontFacing ? 1.0 : - 1.0;
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
vec3 nonPerturbedNormal = normal;`,sp=`#ifdef USE_NORMALMAP_OBJECTSPACE
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
#endif`,rp=`#ifndef FLAT_SHADED
	varying vec3 vNormal;
	#ifdef USE_TANGENT
		varying vec3 vTangent;
		varying vec3 vBitangent;
	#endif
#endif`,op=`#ifndef FLAT_SHADED
	varying vec3 vNormal;
	#ifdef USE_TANGENT
		varying vec3 vTangent;
		varying vec3 vBitangent;
	#endif
#endif`,ap=`#ifndef FLAT_SHADED
	vNormal = normalize( transformedNormal );
	#ifdef USE_TANGENT
		vTangent = normalize( transformedTangent );
		vBitangent = normalize( cross( vNormal, vTangent ) * tangent.w );
	#endif
#endif`,lp=`#ifdef USE_NORMALMAP
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
#endif`,cp=`#ifdef USE_CLEARCOAT
	vec3 clearcoatNormal = nonPerturbedNormal;
#endif`,hp=`#ifdef USE_CLEARCOAT_NORMALMAP
	vec3 clearcoatMapN = texture2D( clearcoatNormalMap, vClearcoatNormalMapUv ).xyz * 2.0 - 1.0;
	clearcoatMapN.xy *= clearcoatNormalScale;
	clearcoatNormal = normalize( tbn2 * clearcoatMapN );
#endif`,up=`#ifdef USE_CLEARCOATMAP
	uniform sampler2D clearcoatMap;
#endif
#ifdef USE_CLEARCOAT_NORMALMAP
	uniform sampler2D clearcoatNormalMap;
	uniform vec2 clearcoatNormalScale;
#endif
#ifdef USE_CLEARCOAT_ROUGHNESSMAP
	uniform sampler2D clearcoatRoughnessMap;
#endif`,dp=`#ifdef USE_IRIDESCENCEMAP
	uniform sampler2D iridescenceMap;
#endif
#ifdef USE_IRIDESCENCE_THICKNESSMAP
	uniform sampler2D iridescenceThicknessMap;
#endif`,fp=`#ifdef OPAQUE
diffuseColor.a = 1.0;
#endif
#ifdef USE_TRANSMISSION
diffuseColor.a *= material.transmissionAlpha;
#endif
gl_FragColor = vec4( outgoingLight, diffuseColor.a );`,pp=`vec3 packNormalToRGB( const in vec3 normal ) {
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
}`,mp=`#ifdef PREMULTIPLIED_ALPHA
	gl_FragColor.rgb *= gl_FragColor.a;
#endif`,gp=`vec4 mvPosition = vec4( transformed, 1.0 );
#ifdef USE_BATCHING
	mvPosition = batchingMatrix * mvPosition;
#endif
#ifdef USE_INSTANCING
	mvPosition = instanceMatrix * mvPosition;
#endif
mvPosition = modelViewMatrix * mvPosition;
gl_Position = projectionMatrix * mvPosition;`,vp=`#ifdef DITHERING
	gl_FragColor.rgb = dithering( gl_FragColor.rgb );
#endif`,xp=`#ifdef DITHERING
	vec3 dithering( vec3 color ) {
		float grid_position = rand( gl_FragCoord.xy );
		vec3 dither_shift_RGB = vec3( 0.25 / 255.0, -0.25 / 255.0, 0.25 / 255.0 );
		dither_shift_RGB = mix( 2.0 * dither_shift_RGB, -2.0 * dither_shift_RGB, grid_position );
		return color + dither_shift_RGB;
	}
#endif`,_p=`float roughnessFactor = roughness;
#ifdef USE_ROUGHNESSMAP
	vec4 texelRoughness = texture2D( roughnessMap, vRoughnessMapUv );
	roughnessFactor *= texelRoughness.g;
#endif`,yp=`#ifdef USE_ROUGHNESSMAP
	uniform sampler2D roughnessMap;
#endif`,Mp=`#if NUM_SPOT_LIGHT_COORDS > 0
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
#endif`,Sp=`#if NUM_SPOT_LIGHT_COORDS > 0
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
#endif`,bp=`#if ( defined( USE_SHADOWMAP ) && ( NUM_DIR_LIGHT_SHADOWS > 0 || NUM_POINT_LIGHT_SHADOWS > 0 ) ) || ( NUM_SPOT_LIGHT_COORDS > 0 )
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
#endif`,Ep=`float getShadowMask() {
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
}`,wp=`#ifdef USE_SKINNING
	mat4 boneMatX = getBoneMatrix( skinIndex.x );
	mat4 boneMatY = getBoneMatrix( skinIndex.y );
	mat4 boneMatZ = getBoneMatrix( skinIndex.z );
	mat4 boneMatW = getBoneMatrix( skinIndex.w );
#endif`,Tp=`#ifdef USE_SKINNING
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
#endif`,Ap=`#ifdef USE_SKINNING
	vec4 skinVertex = bindMatrix * vec4( transformed, 1.0 );
	vec4 skinned = vec4( 0.0 );
	skinned += boneMatX * skinVertex * skinWeight.x;
	skinned += boneMatY * skinVertex * skinWeight.y;
	skinned += boneMatZ * skinVertex * skinWeight.z;
	skinned += boneMatW * skinVertex * skinWeight.w;
	transformed = ( bindMatrixInverse * skinned ).xyz;
#endif`,Rp=`#ifdef USE_SKINNING
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
#endif`,Cp=`float specularStrength;
#ifdef USE_SPECULARMAP
	vec4 texelSpecular = texture2D( specularMap, vSpecularMapUv );
	specularStrength = texelSpecular.r;
#else
	specularStrength = 1.0;
#endif`,Pp=`#ifdef USE_SPECULARMAP
	uniform sampler2D specularMap;
#endif`,Ip=`#if defined( TONE_MAPPING )
	gl_FragColor.rgb = toneMapping( gl_FragColor.rgb );
#endif`,Lp=`#ifndef saturate
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
vec3 CustomToneMapping( vec3 color ) { return color; }`,Dp=`#ifdef USE_TRANSMISSION
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
#endif`,Up=`#ifdef USE_TRANSMISSION
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
#endif`,Np=`#if defined( USE_UV ) || defined( USE_ANISOTROPY )
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
#endif`,Fp=`#if defined( USE_UV ) || defined( USE_ANISOTROPY )
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
#endif`,Op=`#if defined( USE_UV ) || defined( USE_ANISOTROPY )
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
#endif`,Bp=`#if defined( USE_ENVMAP ) || defined( DISTANCE ) || defined ( USE_SHADOWMAP ) || defined ( USE_TRANSMISSION ) || NUM_SPOT_LIGHT_COORDS > 0
	vec4 worldPosition = vec4( transformed, 1.0 );
	#ifdef USE_BATCHING
		worldPosition = batchingMatrix * worldPosition;
	#endif
	#ifdef USE_INSTANCING
		worldPosition = instanceMatrix * worldPosition;
	#endif
	worldPosition = modelMatrix * worldPosition;
#endif`,zp=`varying vec2 vUv;
uniform mat3 uvTransform;
void main() {
	vUv = ( uvTransform * vec3( uv, 1 ) ).xy;
	gl_Position = vec4( position.xy, 1.0, 1.0 );
}`,Hp=`uniform sampler2D t2D;
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
}`,kp=`varying vec3 vWorldDirection;
#include <common>
void main() {
	vWorldDirection = transformDirection( position, modelMatrix );
	#include <begin_vertex>
	#include <project_vertex>
	gl_Position.z = gl_Position.w;
}`,Vp=`#ifdef ENVMAP_TYPE_CUBE
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
}`,Gp=`varying vec3 vWorldDirection;
#include <common>
void main() {
	vWorldDirection = transformDirection( position, modelMatrix );
	#include <begin_vertex>
	#include <project_vertex>
	gl_Position.z = gl_Position.w;
}`,Wp=`uniform samplerCube tCube;
uniform float tFlip;
uniform float opacity;
varying vec3 vWorldDirection;
void main() {
	vec4 texColor = textureCube( tCube, vec3( tFlip * vWorldDirection.x, vWorldDirection.yz ) );
	gl_FragColor = texColor;
	gl_FragColor.a *= opacity;
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
}`,Xp=`#include <common>
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
}`,qp=`#if DEPTH_PACKING == 3200
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
}`,Yp=`#define DISTANCE
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
}`,$p=`#define DISTANCE
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
}`,Zp=`varying vec3 vWorldDirection;
#include <common>
void main() {
	vWorldDirection = transformDirection( position, modelMatrix );
	#include <begin_vertex>
	#include <project_vertex>
}`,Jp=`uniform sampler2D tEquirect;
varying vec3 vWorldDirection;
#include <common>
void main() {
	vec3 direction = normalize( vWorldDirection );
	vec2 sampleUV = equirectUv( direction );
	gl_FragColor = texture2D( tEquirect, sampleUV );
	#include <tonemapping_fragment>
	#include <colorspace_fragment>
}`,Kp=`uniform float scale;
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
}`,jp=`uniform vec3 diffuse;
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
}`,Qp=`#include <common>
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
}`,t0=`uniform vec3 diffuse;
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
}`,e0=`#define LAMBERT
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
}`,n0=`#define LAMBERT
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
}`,i0=`#define MATCAP
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
}`,s0=`#define MATCAP
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
}`,r0=`#define NORMAL
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
}`,o0=`#define NORMAL
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
}`,a0=`#define PHONG
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
}`,l0=`#define PHONG
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
}`,c0=`#define STANDARD
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
}`,h0=`#define STANDARD
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
}`,u0=`#define TOON
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
}`,d0=`#define TOON
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
}`,f0=`uniform float size;
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
}`,p0=`uniform vec3 diffuse;
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
}`,m0=`#include <common>
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
}`,g0=`uniform vec3 color;
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
}`,v0=`uniform float rotation;
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
}`,x0=`uniform vec3 diffuse;
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
}`,qt={alphahash_fragment:zd,alphahash_pars_fragment:Hd,alphamap_fragment:kd,alphamap_pars_fragment:Vd,alphatest_fragment:Gd,alphatest_pars_fragment:Wd,aomap_fragment:Xd,aomap_pars_fragment:qd,batching_pars_vertex:Yd,batching_vertex:$d,begin_vertex:Zd,beginnormal_vertex:Jd,bsdfs:Kd,iridescence_fragment:jd,bumpmap_pars_fragment:Qd,clipping_planes_fragment:tf,clipping_planes_pars_fragment:ef,clipping_planes_pars_vertex:nf,clipping_planes_vertex:sf,color_fragment:rf,color_pars_fragment:of,color_pars_vertex:af,color_vertex:lf,common:cf,cube_uv_reflection_fragment:hf,defaultnormal_vertex:uf,displacementmap_pars_vertex:df,displacementmap_vertex:ff,emissivemap_fragment:pf,emissivemap_pars_fragment:mf,colorspace_fragment:gf,colorspace_pars_fragment:vf,envmap_fragment:xf,envmap_common_pars_fragment:_f,envmap_pars_fragment:yf,envmap_pars_vertex:Mf,envmap_physical_pars_fragment:Lf,envmap_vertex:Sf,fog_vertex:bf,fog_pars_vertex:Ef,fog_fragment:wf,fog_pars_fragment:Tf,gradientmap_pars_fragment:Af,lightmap_pars_fragment:Rf,lights_lambert_fragment:Cf,lights_lambert_pars_fragment:Pf,lights_pars_begin:If,lights_toon_fragment:Df,lights_toon_pars_fragment:Uf,lights_phong_fragment:Nf,lights_phong_pars_fragment:Ff,lights_physical_fragment:Of,lights_physical_pars_fragment:Bf,lights_fragment_begin:zf,lights_fragment_maps:Hf,lights_fragment_end:kf,logdepthbuf_fragment:Vf,logdepthbuf_pars_fragment:Gf,logdepthbuf_pars_vertex:Wf,logdepthbuf_vertex:Xf,map_fragment:qf,map_pars_fragment:Yf,map_particle_fragment:$f,map_particle_pars_fragment:Zf,metalnessmap_fragment:Jf,metalnessmap_pars_fragment:Kf,morphinstance_vertex:jf,morphcolor_vertex:Qf,morphnormal_vertex:tp,morphtarget_pars_vertex:ep,morphtarget_vertex:np,normal_fragment_begin:ip,normal_fragment_maps:sp,normal_pars_fragment:rp,normal_pars_vertex:op,normal_vertex:ap,normalmap_pars_fragment:lp,clearcoat_normal_fragment_begin:cp,clearcoat_normal_fragment_maps:hp,clearcoat_pars_fragment:up,iridescence_pars_fragment:dp,opaque_fragment:fp,packing:pp,premultiplied_alpha_fragment:mp,project_vertex:gp,dithering_fragment:vp,dithering_pars_fragment:xp,roughnessmap_fragment:_p,roughnessmap_pars_fragment:yp,shadowmap_pars_fragment:Mp,shadowmap_pars_vertex:Sp,shadowmap_vertex:bp,shadowmask_pars_fragment:Ep,skinbase_vertex:wp,skinning_pars_vertex:Tp,skinning_vertex:Ap,skinnormal_vertex:Rp,specularmap_fragment:Cp,specularmap_pars_fragment:Pp,tonemapping_fragment:Ip,tonemapping_pars_fragment:Lp,transmission_fragment:Dp,transmission_pars_fragment:Up,uv_pars_fragment:Np,uv_pars_vertex:Fp,uv_vertex:Op,worldpos_vertex:Bp,background_vert:zp,background_frag:Hp,backgroundCube_vert:kp,backgroundCube_frag:Vp,cube_vert:Gp,cube_frag:Wp,depth_vert:Xp,depth_frag:qp,distanceRGBA_vert:Yp,distanceRGBA_frag:$p,equirect_vert:Zp,equirect_frag:Jp,linedashed_vert:Kp,linedashed_frag:jp,meshbasic_vert:Qp,meshbasic_frag:t0,meshlambert_vert:e0,meshlambert_frag:n0,meshmatcap_vert:i0,meshmatcap_frag:s0,meshnormal_vert:r0,meshnormal_frag:o0,meshphong_vert:a0,meshphong_frag:l0,meshphysical_vert:c0,meshphysical_frag:h0,meshtoon_vert:u0,meshtoon_frag:d0,points_vert:f0,points_frag:p0,shadow_vert:m0,shadow_frag:g0,sprite_vert:v0,sprite_frag:x0},ht={common:{diffuse:{value:new Ct(16777215)},opacity:{value:1},map:{value:null},mapTransform:{value:new Ot},alphaMap:{value:null},alphaMapTransform:{value:new Ot},alphaTest:{value:0}},specularmap:{specularMap:{value:null},specularMapTransform:{value:new Ot}},envmap:{envMap:{value:null},envMapRotation:{value:new Ot},flipEnvMap:{value:-1},reflectivity:{value:1},ior:{value:1.5},refractionRatio:{value:.98}},aomap:{aoMap:{value:null},aoMapIntensity:{value:1},aoMapTransform:{value:new Ot}},lightmap:{lightMap:{value:null},lightMapIntensity:{value:1},lightMapTransform:{value:new Ot}},bumpmap:{bumpMap:{value:null},bumpMapTransform:{value:new Ot},bumpScale:{value:1}},normalmap:{normalMap:{value:null},normalMapTransform:{value:new Ot},normalScale:{value:new gt(1,1)}},displacementmap:{displacementMap:{value:null},displacementMapTransform:{value:new Ot},displacementScale:{value:1},displacementBias:{value:0}},emissivemap:{emissiveMap:{value:null},emissiveMapTransform:{value:new Ot}},metalnessmap:{metalnessMap:{value:null},metalnessMapTransform:{value:new Ot}},roughnessmap:{roughnessMap:{value:null},roughnessMapTransform:{value:new Ot}},gradientmap:{gradientMap:{value:null}},fog:{fogDensity:{value:25e-5},fogNear:{value:1},fogFar:{value:2e3},fogColor:{value:new Ct(16777215)}},lights:{ambientLightColor:{value:[]},lightProbe:{value:[]},directionalLights:{value:[],properties:{direction:{},color:{}}},directionalLightShadows:{value:[],properties:{shadowIntensity:1,shadowBias:{},shadowNormalBias:{},shadowRadius:{},shadowMapSize:{}}},directionalShadowMap:{value:[]},directionalShadowMatrix:{value:[]},spotLights:{value:[],properties:{color:{},position:{},direction:{},distance:{},coneCos:{},penumbraCos:{},decay:{}}},spotLightShadows:{value:[],properties:{shadowIntensity:1,shadowBias:{},shadowNormalBias:{},shadowRadius:{},shadowMapSize:{}}},spotLightMap:{value:[]},spotShadowMap:{value:[]},spotLightMatrix:{value:[]},pointLights:{value:[],properties:{color:{},position:{},decay:{},distance:{}}},pointLightShadows:{value:[],properties:{shadowIntensity:1,shadowBias:{},shadowNormalBias:{},shadowRadius:{},shadowMapSize:{},shadowCameraNear:{},shadowCameraFar:{}}},pointShadowMap:{value:[]},pointShadowMatrix:{value:[]},hemisphereLights:{value:[],properties:{direction:{},skyColor:{},groundColor:{}}},rectAreaLights:{value:[],properties:{color:{},position:{},width:{},height:{}}},ltc_1:{value:null},ltc_2:{value:null}},points:{diffuse:{value:new Ct(16777215)},opacity:{value:1},size:{value:1},scale:{value:1},map:{value:null},alphaMap:{value:null},alphaMapTransform:{value:new Ot},alphaTest:{value:0},uvTransform:{value:new Ot}},sprite:{diffuse:{value:new Ct(16777215)},opacity:{value:1},center:{value:new gt(.5,.5)},rotation:{value:0},map:{value:null},mapTransform:{value:new Ot},alphaMap:{value:null},alphaMapTransform:{value:new Ot},alphaTest:{value:0}}},Fn={basic:{uniforms:$e([ht.common,ht.specularmap,ht.envmap,ht.aomap,ht.lightmap,ht.fog]),vertexShader:qt.meshbasic_vert,fragmentShader:qt.meshbasic_frag},lambert:{uniforms:$e([ht.common,ht.specularmap,ht.envmap,ht.aomap,ht.lightmap,ht.emissivemap,ht.bumpmap,ht.normalmap,ht.displacementmap,ht.fog,ht.lights,{emissive:{value:new Ct(0)}}]),vertexShader:qt.meshlambert_vert,fragmentShader:qt.meshlambert_frag},phong:{uniforms:$e([ht.common,ht.specularmap,ht.envmap,ht.aomap,ht.lightmap,ht.emissivemap,ht.bumpmap,ht.normalmap,ht.displacementmap,ht.fog,ht.lights,{emissive:{value:new Ct(0)},specular:{value:new Ct(1118481)},shininess:{value:30}}]),vertexShader:qt.meshphong_vert,fragmentShader:qt.meshphong_frag},standard:{uniforms:$e([ht.common,ht.envmap,ht.aomap,ht.lightmap,ht.emissivemap,ht.bumpmap,ht.normalmap,ht.displacementmap,ht.roughnessmap,ht.metalnessmap,ht.fog,ht.lights,{emissive:{value:new Ct(0)},roughness:{value:1},metalness:{value:0},envMapIntensity:{value:1}}]),vertexShader:qt.meshphysical_vert,fragmentShader:qt.meshphysical_frag},toon:{uniforms:$e([ht.common,ht.aomap,ht.lightmap,ht.emissivemap,ht.bumpmap,ht.normalmap,ht.displacementmap,ht.gradientmap,ht.fog,ht.lights,{emissive:{value:new Ct(0)}}]),vertexShader:qt.meshtoon_vert,fragmentShader:qt.meshtoon_frag},matcap:{uniforms:$e([ht.common,ht.bumpmap,ht.normalmap,ht.displacementmap,ht.fog,{matcap:{value:null}}]),vertexShader:qt.meshmatcap_vert,fragmentShader:qt.meshmatcap_frag},points:{uniforms:$e([ht.points,ht.fog]),vertexShader:qt.points_vert,fragmentShader:qt.points_frag},dashed:{uniforms:$e([ht.common,ht.fog,{scale:{value:1},dashSize:{value:1},totalSize:{value:2}}]),vertexShader:qt.linedashed_vert,fragmentShader:qt.linedashed_frag},depth:{uniforms:$e([ht.common,ht.displacementmap]),vertexShader:qt.depth_vert,fragmentShader:qt.depth_frag},normal:{uniforms:$e([ht.common,ht.bumpmap,ht.normalmap,ht.displacementmap,{opacity:{value:1}}]),vertexShader:qt.meshnormal_vert,fragmentShader:qt.meshnormal_frag},sprite:{uniforms:$e([ht.sprite,ht.fog]),vertexShader:qt.sprite_vert,fragmentShader:qt.sprite_frag},background:{uniforms:{uvTransform:{value:new Ot},t2D:{value:null},backgroundIntensity:{value:1}},vertexShader:qt.background_vert,fragmentShader:qt.background_frag},backgroundCube:{uniforms:{envMap:{value:null},flipEnvMap:{value:-1},backgroundBlurriness:{value:0},backgroundIntensity:{value:1},backgroundRotation:{value:new Ot}},vertexShader:qt.backgroundCube_vert,fragmentShader:qt.backgroundCube_frag},cube:{uniforms:{tCube:{value:null},tFlip:{value:-1},opacity:{value:1}},vertexShader:qt.cube_vert,fragmentShader:qt.cube_frag},equirect:{uniforms:{tEquirect:{value:null}},vertexShader:qt.equirect_vert,fragmentShader:qt.equirect_frag},distanceRGBA:{uniforms:$e([ht.common,ht.displacementmap,{referencePosition:{value:new P},nearDistance:{value:1},farDistance:{value:1e3}}]),vertexShader:qt.distanceRGBA_vert,fragmentShader:qt.distanceRGBA_frag},shadow:{uniforms:$e([ht.lights,ht.fog,{color:{value:new Ct(0)},opacity:{value:1}}]),vertexShader:qt.shadow_vert,fragmentShader:qt.shadow_frag}};Fn.physical={uniforms:$e([Fn.standard.uniforms,{clearcoat:{value:0},clearcoatMap:{value:null},clearcoatMapTransform:{value:new Ot},clearcoatNormalMap:{value:null},clearcoatNormalMapTransform:{value:new Ot},clearcoatNormalScale:{value:new gt(1,1)},clearcoatRoughness:{value:0},clearcoatRoughnessMap:{value:null},clearcoatRoughnessMapTransform:{value:new Ot},dispersion:{value:0},iridescence:{value:0},iridescenceMap:{value:null},iridescenceMapTransform:{value:new Ot},iridescenceIOR:{value:1.3},iridescenceThicknessMinimum:{value:100},iridescenceThicknessMaximum:{value:400},iridescenceThicknessMap:{value:null},iridescenceThicknessMapTransform:{value:new Ot},sheen:{value:0},sheenColor:{value:new Ct(0)},sheenColorMap:{value:null},sheenColorMapTransform:{value:new Ot},sheenRoughness:{value:1},sheenRoughnessMap:{value:null},sheenRoughnessMapTransform:{value:new Ot},transmission:{value:0},transmissionMap:{value:null},transmissionMapTransform:{value:new Ot},transmissionSamplerSize:{value:new gt},transmissionSamplerMap:{value:null},thickness:{value:0},thicknessMap:{value:null},thicknessMapTransform:{value:new Ot},attenuationDistance:{value:0},attenuationColor:{value:new Ct(0)},specularColor:{value:new Ct(1,1,1)},specularColorMap:{value:null},specularColorMapTransform:{value:new Ot},specularIntensity:{value:1},specularIntensityMap:{value:null},specularIntensityMapTransform:{value:new Ot},anisotropyVector:{value:new gt},anisotropyMap:{value:null},anisotropyMapTransform:{value:new Ot}}]),vertexShader:qt.meshphysical_vert,fragmentShader:qt.meshphysical_frag};var Or={r:0,b:0,g:0},Li=new gn,_0=new ne;function y0(i,t,e,n,s,r,o){let a=new Ct(0),l=r===!0?0:1,c,h,d=null,u=0,f=null;function g(w){let E=w.isScene===!0?w.background:null;return E&&E.isTexture&&(E=(w.backgroundBlurriness>0?e:t).get(E)),E}function y(w){let E=!1,M=g(w);M===null?p(a,l):M&&M.isColor&&(p(M,1),E=!0);let U=i.xr.getEnvironmentBlendMode();U==="additive"?n.buffers.color.setClear(0,0,0,1,o):U==="alpha-blend"&&n.buffers.color.setClear(0,0,0,0,o),(i.autoClear||E)&&(n.buffers.depth.setTest(!0),n.buffers.depth.setMask(!0),n.buffers.color.setMask(!0),i.clear(i.autoClearColor,i.autoClearDepth,i.autoClearStencil))}function m(w,E){let M=g(E);M&&(M.isCubeTexture||M.mapping===bo)?(h===void 0&&(h=new at(new dn(1,1,1),new sn({name:"BackgroundCubeMaterial",uniforms:bs(Fn.backgroundCube.uniforms),vertexShader:Fn.backgroundCube.vertexShader,fragmentShader:Fn.backgroundCube.fragmentShader,side:Re,depthTest:!1,depthWrite:!1,fog:!1})),h.geometry.deleteAttribute("normal"),h.geometry.deleteAttribute("uv"),h.onBeforeRender=function(U,C,v){this.matrixWorld.copyPosition(v.matrixWorld)},Object.defineProperty(h.material,"envMap",{get:function(){return this.uniforms.envMap.value}}),s.update(h)),Li.copy(E.backgroundRotation),Li.x*=-1,Li.y*=-1,Li.z*=-1,M.isCubeTexture&&M.isRenderTargetTexture===!1&&(Li.y*=-1,Li.z*=-1),h.material.uniforms.envMap.value=M,h.material.uniforms.flipEnvMap.value=M.isCubeTexture&&M.isRenderTargetTexture===!1?-1:1,h.material.uniforms.backgroundBlurriness.value=E.backgroundBlurriness,h.material.uniforms.backgroundIntensity.value=E.backgroundIntensity,h.material.uniforms.backgroundRotation.value.setFromMatrix4(_0.makeRotationFromEuler(Li)),h.material.toneMapped=jt.getTransfer(M.colorSpace)!==ce,(d!==M||u!==M.version||f!==i.toneMapping)&&(h.material.needsUpdate=!0,d=M,u=M.version,f=i.toneMapping),h.layers.enableAll(),w.unshift(h,h.geometry,h.material,0,0,null)):M&&M.isTexture&&(c===void 0&&(c=new at(new Je(2,2),new sn({name:"BackgroundMaterial",uniforms:bs(Fn.background.uniforms),vertexShader:Fn.background.vertexShader,fragmentShader:Fn.background.fragmentShader,side:xi,depthTest:!1,depthWrite:!1,fog:!1})),c.geometry.deleteAttribute("normal"),Object.defineProperty(c.material,"map",{get:function(){return this.uniforms.t2D.value}}),s.update(c)),c.material.uniforms.t2D.value=M,c.material.uniforms.backgroundIntensity.value=E.backgroundIntensity,c.material.toneMapped=jt.getTransfer(M.colorSpace)!==ce,M.matrixAutoUpdate===!0&&M.updateMatrix(),c.material.uniforms.uvTransform.value.copy(M.matrix),(d!==M||u!==M.version||f!==i.toneMapping)&&(c.material.needsUpdate=!0,d=M,u=M.version,f=i.toneMapping),c.layers.enableAll(),w.unshift(c,c.geometry,c.material,0,0,null))}function p(w,E){w.getRGB(Or,Gh(i)),n.buffers.color.setClear(Or.r,Or.g,Or.b,E,o)}return{getClearColor:function(){return a},setClearColor:function(w,E=1){a.set(w),l=E,p(a,l)},getClearAlpha:function(){return l},setClearAlpha:function(w){l=w,p(a,l)},render:y,addToRenderList:m}}function M0(i,t){let e=i.getParameter(i.MAX_VERTEX_ATTRIBS),n={},s=u(null),r=s,o=!1;function a(_,R,I,L,N){let V=!1,F=d(L,I,R);r!==F&&(r=F,c(r.object)),V=f(_,L,I,N),V&&g(_,L,I,N),N!==null&&t.update(N,i.ELEMENT_ARRAY_BUFFER),(V||o)&&(o=!1,M(_,R,I,L),N!==null&&i.bindBuffer(i.ELEMENT_ARRAY_BUFFER,t.get(N).buffer))}function l(){return i.createVertexArray()}function c(_){return i.bindVertexArray(_)}function h(_){return i.deleteVertexArray(_)}function d(_,R,I){let L=I.wireframe===!0,N=n[_.id];N===void 0&&(N={},n[_.id]=N);let V=N[R.id];V===void 0&&(V={},N[R.id]=V);let F=V[L];return F===void 0&&(F=u(l()),V[L]=F),F}function u(_){let R=[],I=[],L=[];for(let N=0;N<e;N++)R[N]=0,I[N]=0,L[N]=0;return{geometry:null,program:null,wireframe:!1,newAttributes:R,enabledAttributes:I,attributeDivisors:L,object:_,attributes:{},index:null}}function f(_,R,I,L){let N=r.attributes,V=R.attributes,F=0,X=I.getAttributes();for(let z in X)if(X[z].location>=0){let it=N[z],st=V[z];if(st===void 0&&(z==="instanceMatrix"&&_.instanceMatrix&&(st=_.instanceMatrix),z==="instanceColor"&&_.instanceColor&&(st=_.instanceColor)),it===void 0||it.attribute!==st||st&&it.data!==st.data)return!0;F++}return r.attributesNum!==F||r.index!==L}function g(_,R,I,L){let N={},V=R.attributes,F=0,X=I.getAttributes();for(let z in X)if(X[z].location>=0){let it=V[z];it===void 0&&(z==="instanceMatrix"&&_.instanceMatrix&&(it=_.instanceMatrix),z==="instanceColor"&&_.instanceColor&&(it=_.instanceColor));let st={};st.attribute=it,it&&it.data&&(st.data=it.data),N[z]=st,F++}r.attributes=N,r.attributesNum=F,r.index=L}function y(){let _=r.newAttributes;for(let R=0,I=_.length;R<I;R++)_[R]=0}function m(_){p(_,0)}function p(_,R){let I=r.newAttributes,L=r.enabledAttributes,N=r.attributeDivisors;I[_]=1,L[_]===0&&(i.enableVertexAttribArray(_),L[_]=1),N[_]!==R&&(i.vertexAttribDivisor(_,R),N[_]=R)}function w(){let _=r.newAttributes,R=r.enabledAttributes;for(let I=0,L=R.length;I<L;I++)R[I]!==_[I]&&(i.disableVertexAttribArray(I),R[I]=0)}function E(_,R,I,L,N,V,F){F===!0?i.vertexAttribIPointer(_,R,I,N,V):i.vertexAttribPointer(_,R,I,L,N,V)}function M(_,R,I,L){y();let N=L.attributes,V=I.getAttributes(),F=R.defaultAttributeValues;for(let X in V){let z=V[X];if(z.location>=0){let j=N[X];if(j===void 0&&(X==="instanceMatrix"&&_.instanceMatrix&&(j=_.instanceMatrix),X==="instanceColor"&&_.instanceColor&&(j=_.instanceColor)),j!==void 0){let it=j.normalized,st=j.itemSize,yt=t.get(j);if(yt===void 0)continue;let Ft=yt.buffer,$=yt.type,nt=yt.bytesPerElement,ft=$===i.INT||$===i.UNSIGNED_INT||j.gpuType===Xl;if(j.isInterleavedBufferAttribute){let ct=j.data,bt=ct.stride,Lt=j.offset;if(ct.isInstancedInterleavedBuffer){for(let Et=0;Et<z.locationSize;Et++)p(z.location+Et,ct.meshPerAttribute);_.isInstancedMesh!==!0&&L._maxInstanceCount===void 0&&(L._maxInstanceCount=ct.meshPerAttribute*ct.count)}else for(let Et=0;Et<z.locationSize;Et++)m(z.location+Et);i.bindBuffer(i.ARRAY_BUFFER,Ft);for(let Et=0;Et<z.locationSize;Et++)E(z.location+Et,st/z.locationSize,$,it,bt*nt,(Lt+st/z.locationSize*Et)*nt,ft)}else{if(j.isInstancedBufferAttribute){for(let ct=0;ct<z.locationSize;ct++)p(z.location+ct,j.meshPerAttribute);_.isInstancedMesh!==!0&&L._maxInstanceCount===void 0&&(L._maxInstanceCount=j.meshPerAttribute*j.count)}else for(let ct=0;ct<z.locationSize;ct++)m(z.location+ct);i.bindBuffer(i.ARRAY_BUFFER,Ft);for(let ct=0;ct<z.locationSize;ct++)E(z.location+ct,st/z.locationSize,$,it,st*nt,st/z.locationSize*ct*nt,ft)}}else if(F!==void 0){let it=F[X];if(it!==void 0)switch(it.length){case 2:i.vertexAttrib2fv(z.location,it);break;case 3:i.vertexAttrib3fv(z.location,it);break;case 4:i.vertexAttrib4fv(z.location,it);break;default:i.vertexAttrib1fv(z.location,it)}}}}w()}function U(){b();for(let _ in n){let R=n[_];for(let I in R){let L=R[I];for(let N in L)h(L[N].object),delete L[N];delete R[I]}delete n[_]}}function C(_){if(n[_.id]===void 0)return;let R=n[_.id];for(let I in R){let L=R[I];for(let N in L)h(L[N].object),delete L[N];delete R[I]}delete n[_.id]}function v(_){for(let R in n){let I=n[R];if(I[_.id]===void 0)continue;let L=I[_.id];for(let N in L)h(L[N].object),delete L[N];delete I[_.id]}}function b(){x(),o=!0,r!==s&&(r=s,c(r.object))}function x(){s.geometry=null,s.program=null,s.wireframe=!1}return{setup:a,reset:b,resetDefaultState:x,dispose:U,releaseStatesOfGeometry:C,releaseStatesOfProgram:v,initAttributes:y,enableAttribute:m,disableUnusedAttributes:w}}function S0(i,t,e){let n;function s(c){n=c}function r(c,h){i.drawArrays(n,c,h),e.update(h,n,1)}function o(c,h,d){d!==0&&(i.drawArraysInstanced(n,c,h,d),e.update(h,n,d))}function a(c,h,d){if(d===0)return;t.get("WEBGL_multi_draw").multiDrawArraysWEBGL(n,c,0,h,0,d);let f=0;for(let g=0;g<d;g++)f+=h[g];e.update(f,n,1)}function l(c,h,d,u){if(d===0)return;let f=t.get("WEBGL_multi_draw");if(f===null)for(let g=0;g<c.length;g++)o(c[g],h[g],u[g]);else{f.multiDrawArraysInstancedWEBGL(n,c,0,h,0,u,0,d);let g=0;for(let y=0;y<d;y++)g+=h[y]*u[y];e.update(g,n,1)}}this.setMode=s,this.render=r,this.renderInstances=o,this.renderMultiDraw=a,this.renderMultiDrawInstances=l}function b0(i,t,e,n){let s;function r(){if(s!==void 0)return s;if(t.has("EXT_texture_filter_anisotropic")===!0){let v=t.get("EXT_texture_filter_anisotropic");s=i.getParameter(v.MAX_TEXTURE_MAX_ANISOTROPY_EXT)}else s=0;return s}function o(v){return!(v!==Cn&&n.convert(v)!==i.getParameter(i.IMPLEMENTATION_COLOR_READ_FORMAT))}function a(v){let b=v===ar&&(t.has("EXT_color_buffer_half_float")||t.has("EXT_color_buffer_float"));return!(v!==ri&&n.convert(v)!==i.getParameter(i.IMPLEMENTATION_COLOR_READ_TYPE)&&v!==zn&&!b)}function l(v){if(v==="highp"){if(i.getShaderPrecisionFormat(i.VERTEX_SHADER,i.HIGH_FLOAT).precision>0&&i.getShaderPrecisionFormat(i.FRAGMENT_SHADER,i.HIGH_FLOAT).precision>0)return"highp";v="mediump"}return v==="mediump"&&i.getShaderPrecisionFormat(i.VERTEX_SHADER,i.MEDIUM_FLOAT).precision>0&&i.getShaderPrecisionFormat(i.FRAGMENT_SHADER,i.MEDIUM_FLOAT).precision>0?"mediump":"lowp"}let c=e.precision!==void 0?e.precision:"highp",h=l(c);h!==c&&(console.warn("THREE.WebGLRenderer:",c,"not supported, using",h,"instead."),c=h);let d=e.logarithmicDepthBuffer===!0,u=e.reverseDepthBuffer===!0&&t.has("EXT_clip_control"),f=i.getParameter(i.MAX_TEXTURE_IMAGE_UNITS),g=i.getParameter(i.MAX_VERTEX_TEXTURE_IMAGE_UNITS),y=i.getParameter(i.MAX_TEXTURE_SIZE),m=i.getParameter(i.MAX_CUBE_MAP_TEXTURE_SIZE),p=i.getParameter(i.MAX_VERTEX_ATTRIBS),w=i.getParameter(i.MAX_VERTEX_UNIFORM_VECTORS),E=i.getParameter(i.MAX_VARYING_VECTORS),M=i.getParameter(i.MAX_FRAGMENT_UNIFORM_VECTORS),U=g>0,C=i.getParameter(i.MAX_SAMPLES);return{isWebGL2:!0,getMaxAnisotropy:r,getMaxPrecision:l,textureFormatReadable:o,textureTypeReadable:a,precision:c,logarithmicDepthBuffer:d,reverseDepthBuffer:u,maxTextures:f,maxVertexTextures:g,maxTextureSize:y,maxCubemapSize:m,maxAttributes:p,maxVertexUniforms:w,maxVaryings:E,maxFragmentUniforms:M,vertexTextures:U,maxSamples:C}}function E0(i){let t=this,e=null,n=0,s=!1,r=!1,o=new Rn,a=new Ot,l={value:null,needsUpdate:!1};this.uniform=l,this.numPlanes=0,this.numIntersection=0,this.init=function(d,u){let f=d.length!==0||u||n!==0||s;return s=u,n=d.length,f},this.beginShadows=function(){r=!0,h(null)},this.endShadows=function(){r=!1},this.setGlobalState=function(d,u){e=h(d,u,0)},this.setState=function(d,u,f){let g=d.clippingPlanes,y=d.clipIntersection,m=d.clipShadows,p=i.get(d);if(!s||g===null||g.length===0||r&&!m)r?h(null):c();else{let w=r?0:n,E=w*4,M=p.clippingState||null;l.value=M,M=h(g,u,E,f);for(let U=0;U!==E;++U)M[U]=e[U];p.clippingState=M,this.numIntersection=y?this.numPlanes:0,this.numPlanes+=w}};function c(){l.value!==e&&(l.value=e,l.needsUpdate=n>0),t.numPlanes=n,t.numIntersection=0}function h(d,u,f,g){let y=d!==null?d.length:0,m=null;if(y!==0){if(m=l.value,g!==!0||m===null){let p=f+y*4,w=u.matrixWorldInverse;a.getNormalMatrix(w),(m===null||m.length<p)&&(m=new Float32Array(p));for(let E=0,M=f;E!==y;++E,M+=4)o.copy(d[E]).applyMatrix4(w,a),o.normal.toArray(m,M),m[M+3]=o.constant}l.value=m,l.needsUpdate=!0}return t.numPlanes=y,t.numIntersection=0,m}}function w0(i){let t=new WeakMap;function e(o,a){return a===Ra?o.mapping=vs:a===Ca&&(o.mapping=xs),o}function n(o){if(o&&o.isTexture){let a=o.mapping;if(a===Ra||a===Ca)if(t.has(o)){let l=t.get(o).texture;return e(l,o.mapping)}else{let l=o.image;if(l&&l.height>0){let c=new cl(l.height);return c.fromEquirectangularTexture(i,o),t.set(o,c),o.addEventListener("dispose",s),e(c.texture,o.mapping)}else return null}}return o}function s(o){let a=o.target;a.removeEventListener("dispose",s);let l=t.get(a);l!==void 0&&(t.delete(a),l.dispose())}function r(){t=new WeakMap}return{get:n,dispose:r}}var Es=class extends oo{constructor(t=-1,e=1,n=1,s=-1,r=.1,o=2e3){super(),this.isOrthographicCamera=!0,this.type="OrthographicCamera",this.zoom=1,this.view=null,this.left=t,this.right=e,this.top=n,this.bottom=s,this.near=r,this.far=o,this.updateProjectionMatrix()}copy(t,e){return super.copy(t,e),this.left=t.left,this.right=t.right,this.top=t.top,this.bottom=t.bottom,this.near=t.near,this.far=t.far,this.zoom=t.zoom,this.view=t.view===null?null:Object.assign({},t.view),this}setViewOffset(t,e,n,s,r,o){this.view===null&&(this.view={enabled:!0,fullWidth:1,fullHeight:1,offsetX:0,offsetY:0,width:1,height:1}),this.view.enabled=!0,this.view.fullWidth=t,this.view.fullHeight=e,this.view.offsetX=n,this.view.offsetY=s,this.view.width=r,this.view.height=o,this.updateProjectionMatrix()}clearViewOffset(){this.view!==null&&(this.view.enabled=!1),this.updateProjectionMatrix()}updateProjectionMatrix(){let t=(this.right-this.left)/(2*this.zoom),e=(this.top-this.bottom)/(2*this.zoom),n=(this.right+this.left)/2,s=(this.top+this.bottom)/2,r=n-t,o=n+t,a=s+e,l=s-e;if(this.view!==null&&this.view.enabled){let c=(this.right-this.left)/this.view.fullWidth/this.zoom,h=(this.top-this.bottom)/this.view.fullHeight/this.zoom;r+=c*this.view.offsetX,o=r+c*this.view.width,a-=h*this.view.offsetY,l=a-h*this.view.height}this.projectionMatrix.makeOrthographic(r,o,a,l,this.near,this.far,this.coordinateSystem),this.projectionMatrixInverse.copy(this.projectionMatrix).invert()}toJSON(t){let e=super.toJSON(t);return e.object.zoom=this.zoom,e.object.left=this.left,e.object.right=this.right,e.object.top=this.top,e.object.bottom=this.bottom,e.object.near=this.near,e.object.far=this.far,this.view!==null&&(e.object.view=Object.assign({},this.view)),e}},us=4,Zc=[.125,.215,.35,.446,.526,.582],Oi=20,la=new Es,Jc=new Ct,ca=null,ha=0,ua=0,da=!1,Ui=(1+Math.sqrt(5))/2,cs=1/Ui,Kc=[new P(-Ui,cs,0),new P(Ui,cs,0),new P(-cs,0,Ui),new P(cs,0,Ui),new P(0,Ui,-cs),new P(0,Ui,cs),new P(-1,1,-1),new P(1,1,-1),new P(-1,1,1),new P(1,1,1)],ws=class{constructor(t){this._renderer=t,this._pingPongRenderTarget=null,this._lodMax=0,this._cubeSize=0,this._lodPlanes=[],this._sizeLods=[],this._sigmas=[],this._blurMaterial=null,this._cubemapMaterial=null,this._equirectMaterial=null,this._compileMaterial(this._blurMaterial)}fromScene(t,e=0,n=.1,s=100){ca=this._renderer.getRenderTarget(),ha=this._renderer.getActiveCubeFace(),ua=this._renderer.getActiveMipmapLevel(),da=this._renderer.xr.enabled,this._renderer.xr.enabled=!1,this._setSize(256);let r=this._allocateTargets();return r.depthBuffer=!0,this._sceneToCubeUV(t,n,s,r),e>0&&this._blur(r,0,0,e),this._applyPMREM(r),this._cleanup(r),r}fromEquirectangular(t,e=null){return this._fromTexture(t,e)}fromCubemap(t,e=null){return this._fromTexture(t,e)}compileCubemapShader(){this._cubemapMaterial===null&&(this._cubemapMaterial=th(),this._compileMaterial(this._cubemapMaterial))}compileEquirectangularShader(){this._equirectMaterial===null&&(this._equirectMaterial=Qc(),this._compileMaterial(this._equirectMaterial))}dispose(){this._dispose(),this._cubemapMaterial!==null&&this._cubemapMaterial.dispose(),this._equirectMaterial!==null&&this._equirectMaterial.dispose()}_setSize(t){this._lodMax=Math.floor(Math.log2(t)),this._cubeSize=Math.pow(2,this._lodMax)}_dispose(){this._blurMaterial!==null&&this._blurMaterial.dispose(),this._pingPongRenderTarget!==null&&this._pingPongRenderTarget.dispose();for(let t=0;t<this._lodPlanes.length;t++)this._lodPlanes[t].dispose()}_cleanup(t){this._renderer.setRenderTarget(ca,ha,ua),this._renderer.xr.enabled=da,t.scissorTest=!1,Br(t,0,0,t.width,t.height)}_fromTexture(t,e){t.mapping===vs||t.mapping===xs?this._setSize(t.image.length===0?16:t.image[0].width||t.image[0].image.width):this._setSize(t.image.width/4),ca=this._renderer.getRenderTarget(),ha=this._renderer.getActiveCubeFace(),ua=this._renderer.getActiveMipmapLevel(),da=this._renderer.xr.enabled,this._renderer.xr.enabled=!1;let n=e||this._allocateTargets();return this._textureToCubeUV(t,n),this._applyPMREM(n),this._cleanup(n),n}_allocateTargets(){let t=3*Math.max(this._cubeSize,112),e=4*this._cubeSize,n={magFilter:Bn,minFilter:Bn,generateMipmaps:!1,type:ar,format:Cn,colorSpace:Ps,depthBuffer:!1},s=jc(t,e,n);if(this._pingPongRenderTarget===null||this._pingPongRenderTarget.width!==t||this._pingPongRenderTarget.height!==e){this._pingPongRenderTarget!==null&&this._dispose(),this._pingPongRenderTarget=jc(t,e,n);let{_lodMax:r}=this;({sizeLods:this._sizeLods,lodPlanes:this._lodPlanes,sigmas:this._sigmas}=T0(r)),this._blurMaterial=A0(r,t,e)}return s}_compileMaterial(t){let e=new at(this._lodPlanes[0],t);this._renderer.compile(e,la)}_sceneToCubeUV(t,e,n,s){let a=new Fe(90,1,e,n),l=[1,-1,1,1,1,1],c=[1,1,1,-1,-1,-1],h=this._renderer,d=h.autoClear,u=h.toneMapping;h.getClearColor(Jc),h.toneMapping=vi,h.autoClear=!1;let f=new Ss({name:"PMREM.Background",side:Re,depthWrite:!1,depthTest:!1}),g=new at(new dn,f),y=!1,m=t.background;m?m.isColor&&(f.color.copy(m),t.background=null,y=!0):(f.color.copy(Jc),y=!0);for(let p=0;p<6;p++){let w=p%3;w===0?(a.up.set(0,l[p],0),a.lookAt(c[p],0,0)):w===1?(a.up.set(0,0,l[p]),a.lookAt(0,c[p],0)):(a.up.set(0,l[p],0),a.lookAt(0,0,c[p]));let E=this._cubeSize;Br(s,w*E,p>2?E:0,E,E),h.setRenderTarget(s),y&&h.render(g,a),h.render(t,a)}g.geometry.dispose(),g.material.dispose(),h.toneMapping=u,h.autoClear=d,t.background=m}_textureToCubeUV(t,e){let n=this._renderer,s=t.mapping===vs||t.mapping===xs;s?(this._cubemapMaterial===null&&(this._cubemapMaterial=th()),this._cubemapMaterial.uniforms.flipEnvMap.value=t.isRenderTargetTexture===!1?-1:1):this._equirectMaterial===null&&(this._equirectMaterial=Qc());let r=s?this._cubemapMaterial:this._equirectMaterial,o=new at(this._lodPlanes[0],r),a=r.uniforms;a.envMap.value=t;let l=this._cubeSize;Br(e,0,0,3*l,2*l),n.setRenderTarget(e),n.render(o,la)}_applyPMREM(t){let e=this._renderer,n=e.autoClear;e.autoClear=!1;let s=this._lodPlanes.length;for(let r=1;r<s;r++){let o=Math.sqrt(this._sigmas[r]*this._sigmas[r]-this._sigmas[r-1]*this._sigmas[r-1]),a=Kc[(s-r-1)%Kc.length];this._blur(t,r-1,r,o,a)}e.autoClear=n}_blur(t,e,n,s,r){let o=this._pingPongRenderTarget;this._halfBlur(t,o,e,n,s,"latitudinal",r),this._halfBlur(o,t,n,n,s,"longitudinal",r)}_halfBlur(t,e,n,s,r,o,a){let l=this._renderer,c=this._blurMaterial;o!=="latitudinal"&&o!=="longitudinal"&&console.error("blur direction must be either latitudinal or longitudinal!");let h=3,d=new at(this._lodPlanes[s],c),u=c.uniforms,f=this._sizeLods[n]-1,g=isFinite(r)?Math.PI/(2*f):2*Math.PI/(2*Oi-1),y=r/g,m=isFinite(r)?1+Math.floor(h*y):Oi;m>Oi&&console.warn(`sigmaRadians, ${r}, is too large and will clip, as it requested ${m} samples when the maximum is set to ${Oi}`);let p=[],w=0;for(let v=0;v<Oi;++v){let b=v/y,x=Math.exp(-b*b/2);p.push(x),v===0?w+=x:v<m&&(w+=2*x)}for(let v=0;v<p.length;v++)p[v]=p[v]/w;u.envMap.value=t.texture,u.samples.value=m,u.weights.value=p,u.latitudinal.value=o==="latitudinal",a&&(u.poleAxis.value=a);let{_lodMax:E}=this;u.dTheta.value=g,u.mipInt.value=E-n;let M=this._sizeLods[s],U=3*M*(s>E-us?s-E+us:0),C=4*(this._cubeSize-M);Br(e,U,C,3*M,2*M),l.setRenderTarget(e),l.render(d,la)}};function T0(i){let t=[],e=[],n=[],s=i,r=i-us+1+Zc.length;for(let o=0;o<r;o++){let a=Math.pow(2,s);e.push(a);let l=1/a;o>i-us?l=Zc[o-i+us-1]:o===0&&(l=0),n.push(l);let c=1/(a-2),h=-c,d=1+c,u=[h,h,d,h,d,d,h,h,d,d,h,d],f=6,g=6,y=3,m=2,p=1,w=new Float32Array(y*g*f),E=new Float32Array(m*g*f),M=new Float32Array(p*g*f);for(let C=0;C<f;C++){let v=C%3*2/3-1,b=C>2?0:-1,x=[v,b,0,v+2/3,b,0,v+2/3,b+1,0,v,b,0,v+2/3,b+1,0,v,b+1,0];w.set(x,y*g*C),E.set(u,m*g*C);let _=[C,C,C,C,C,C];M.set(_,p*g*C)}let U=new Yt;U.setAttribute("position",new Ut(w,y)),U.setAttribute("uv",new Ut(E,m)),U.setAttribute("faceIndex",new Ut(M,p)),t.push(U),s>us&&s--}return{lodPlanes:t,sizeLods:e,sigmas:n}}function jc(i,t,e){let n=new oi(i,t,e);return n.texture.mapping=bo,n.texture.name="PMREM.cubeUv",n.scissorTest=!0,n}function Br(i,t,e,n,s){i.viewport.set(t,e,n,s),i.scissor.set(t,e,n,s)}function A0(i,t,e){let n=new Float32Array(Oi),s=new P(0,1,0);return new sn({name:"SphericalGaussianBlur",defines:{n:Oi,CUBEUV_TEXEL_WIDTH:1/t,CUBEUV_TEXEL_HEIGHT:1/e,CUBEUV_MAX_MIP:`${i}.0`},uniforms:{envMap:{value:null},samples:{value:1},weights:{value:n},latitudinal:{value:!1},dTheta:{value:0},mipInt:{value:0},poleAxis:{value:s}},vertexShader:jl(),fragmentShader:`

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
		`,blending:gi,depthTest:!1,depthWrite:!1})}function Qc(){return new sn({name:"EquirectangularToCubeUV",uniforms:{envMap:{value:null}},vertexShader:jl(),fragmentShader:`

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
		`,blending:gi,depthTest:!1,depthWrite:!1})}function th(){return new sn({name:"CubemapToCubeUV",uniforms:{envMap:{value:null},flipEnvMap:{value:-1}},vertexShader:jl(),fragmentShader:`

			precision mediump float;
			precision mediump int;

			uniform float flipEnvMap;

			varying vec3 vOutputDirection;

			uniform samplerCube envMap;

			void main() {

				gl_FragColor = textureCube( envMap, vec3( flipEnvMap * vOutputDirection.x, vOutputDirection.yz ) );

			}
		`,blending:gi,depthTest:!1,depthWrite:!1})}function jl(){return`

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
	`}function R0(i){let t=new WeakMap,e=null;function n(a){if(a&&a.isTexture){let l=a.mapping,c=l===Ra||l===Ca,h=l===vs||l===xs;if(c||h){let d=t.get(a),u=d!==void 0?d.texture.pmremVersion:0;if(a.isRenderTargetTexture&&a.pmremVersion!==u)return e===null&&(e=new ws(i)),d=c?e.fromEquirectangular(a,d):e.fromCubemap(a,d),d.texture.pmremVersion=a.pmremVersion,t.set(a,d),d.texture;if(d!==void 0)return d.texture;{let f=a.image;return c&&f&&f.height>0||h&&f&&s(f)?(e===null&&(e=new ws(i)),d=c?e.fromEquirectangular(a):e.fromCubemap(a),d.texture.pmremVersion=a.pmremVersion,t.set(a,d),a.addEventListener("dispose",r),d.texture):null}}}return a}function s(a){let l=0,c=6;for(let h=0;h<c;h++)a[h]!==void 0&&l++;return l===c}function r(a){let l=a.target;l.removeEventListener("dispose",r);let c=t.get(l);c!==void 0&&(t.delete(l),c.dispose())}function o(){t=new WeakMap,e!==null&&(e.dispose(),e=null)}return{get:n,dispose:o}}function C0(i){let t={};function e(n){if(t[n]!==void 0)return t[n];let s;switch(n){case"WEBGL_depth_texture":s=i.getExtension("WEBGL_depth_texture")||i.getExtension("MOZ_WEBGL_depth_texture")||i.getExtension("WEBKIT_WEBGL_depth_texture");break;case"EXT_texture_filter_anisotropic":s=i.getExtension("EXT_texture_filter_anisotropic")||i.getExtension("MOZ_EXT_texture_filter_anisotropic")||i.getExtension("WEBKIT_EXT_texture_filter_anisotropic");break;case"WEBGL_compressed_texture_s3tc":s=i.getExtension("WEBGL_compressed_texture_s3tc")||i.getExtension("MOZ_WEBGL_compressed_texture_s3tc")||i.getExtension("WEBKIT_WEBGL_compressed_texture_s3tc");break;case"WEBGL_compressed_texture_pvrtc":s=i.getExtension("WEBGL_compressed_texture_pvrtc")||i.getExtension("WEBKIT_WEBGL_compressed_texture_pvrtc");break;default:s=i.getExtension(n)}return t[n]=s,s}return{has:function(n){return e(n)!==null},init:function(){e("EXT_color_buffer_float"),e("WEBGL_clip_cull_distance"),e("OES_texture_float_linear"),e("EXT_color_buffer_half_float"),e("WEBGL_multisampled_render_to_texture"),e("WEBGL_render_shared_exponent")},get:function(n){let s=e(n);return s===null&&Ks("THREE.WebGLRenderer: "+n+" extension not supported."),s}}}function P0(i,t,e,n){let s={},r=new WeakMap;function o(d){let u=d.target;u.index!==null&&t.remove(u.index);for(let g in u.attributes)t.remove(u.attributes[g]);for(let g in u.morphAttributes){let y=u.morphAttributes[g];for(let m=0,p=y.length;m<p;m++)t.remove(y[m])}u.removeEventListener("dispose",o),delete s[u.id];let f=r.get(u);f&&(t.remove(f),r.delete(u)),n.releaseStatesOfGeometry(u),u.isInstancedBufferGeometry===!0&&delete u._maxInstanceCount,e.memory.geometries--}function a(d,u){return s[u.id]===!0||(u.addEventListener("dispose",o),s[u.id]=!0,e.memory.geometries++),u}function l(d){let u=d.attributes;for(let g in u)t.update(u[g],i.ARRAY_BUFFER);let f=d.morphAttributes;for(let g in f){let y=f[g];for(let m=0,p=y.length;m<p;m++)t.update(y[m],i.ARRAY_BUFFER)}}function c(d){let u=[],f=d.index,g=d.attributes.position,y=0;if(f!==null){let w=f.array;y=f.version;for(let E=0,M=w.length;E<M;E+=3){let U=w[E+0],C=w[E+1],v=w[E+2];u.push(U,C,C,v,v,U)}}else if(g!==void 0){let w=g.array;y=g.version;for(let E=0,M=w.length/3-1;E<M;E+=3){let U=E+0,C=E+1,v=E+2;u.push(U,C,C,v,v,U)}}else return;let m=new(kh(u)?ro:so)(u,1);m.version=y;let p=r.get(d);p&&t.remove(p),r.set(d,m)}function h(d){let u=r.get(d);if(u){let f=d.index;f!==null&&u.version<f.version&&c(d)}else c(d);return r.get(d)}return{get:a,update:l,getWireframeAttribute:h}}function I0(i,t,e){let n;function s(u){n=u}let r,o;function a(u){r=u.type,o=u.bytesPerElement}function l(u,f){i.drawElements(n,f,r,u*o),e.update(f,n,1)}function c(u,f,g){g!==0&&(i.drawElementsInstanced(n,f,r,u*o,g),e.update(f,n,g))}function h(u,f,g){if(g===0)return;t.get("WEBGL_multi_draw").multiDrawElementsWEBGL(n,f,0,r,u,0,g);let m=0;for(let p=0;p<g;p++)m+=f[p];e.update(m,n,1)}function d(u,f,g,y){if(g===0)return;let m=t.get("WEBGL_multi_draw");if(m===null)for(let p=0;p<u.length;p++)c(u[p]/o,f[p],y[p]);else{m.multiDrawElementsInstancedWEBGL(n,f,0,r,u,0,y,0,g);let p=0;for(let w=0;w<g;w++)p+=f[w]*y[w];e.update(p,n,1)}}this.setMode=s,this.setIndex=a,this.render=l,this.renderInstances=c,this.renderMultiDraw=h,this.renderMultiDrawInstances=d}function L0(i){let t={geometries:0,textures:0},e={frame:0,calls:0,triangles:0,points:0,lines:0};function n(r,o,a){switch(e.calls++,o){case i.TRIANGLES:e.triangles+=a*(r/3);break;case i.LINES:e.lines+=a*(r/2);break;case i.LINE_STRIP:e.lines+=a*(r-1);break;case i.LINE_LOOP:e.lines+=a*r;break;case i.POINTS:e.points+=a*r;break;default:console.error("THREE.WebGLInfo: Unknown draw mode:",o);break}}function s(){e.calls=0,e.triangles=0,e.points=0,e.lines=0}return{memory:t,render:e,programs:null,autoReset:!0,reset:s,update:n}}function D0(i,t,e){let n=new WeakMap,s=new re;function r(o,a,l){let c=o.morphTargetInfluences,h=a.morphAttributes.position||a.morphAttributes.normal||a.morphAttributes.color,d=h!==void 0?h.length:0,u=n.get(a);if(u===void 0||u.count!==d){let x=function(){v.dispose(),n.delete(a),a.removeEventListener("dispose",x)};u!==void 0&&u.texture.dispose();let f=a.morphAttributes.position!==void 0,g=a.morphAttributes.normal!==void 0,y=a.morphAttributes.color!==void 0,m=a.morphAttributes.position||[],p=a.morphAttributes.normal||[],w=a.morphAttributes.color||[],E=0;f===!0&&(E=1),g===!0&&(E=2),y===!0&&(E=3);let M=a.attributes.position.count*E,U=1;M>t.maxTextureSize&&(U=Math.ceil(M/t.maxTextureSize),M=t.maxTextureSize);let C=new Float32Array(M*U*4*d),v=new eo(C,M,U,d);v.type=zn,v.needsUpdate=!0;let b=E*4;for(let _=0;_<d;_++){let R=m[_],I=p[_],L=w[_],N=M*U*4*_;for(let V=0;V<R.count;V++){let F=V*b;f===!0&&(s.fromBufferAttribute(R,V),C[N+F+0]=s.x,C[N+F+1]=s.y,C[N+F+2]=s.z,C[N+F+3]=0),g===!0&&(s.fromBufferAttribute(I,V),C[N+F+4]=s.x,C[N+F+5]=s.y,C[N+F+6]=s.z,C[N+F+7]=0),y===!0&&(s.fromBufferAttribute(L,V),C[N+F+8]=s.x,C[N+F+9]=s.y,C[N+F+10]=s.z,C[N+F+11]=L.itemSize===4?s.w:1)}}u={count:d,texture:v,size:new gt(M,U)},n.set(a,u),a.addEventListener("dispose",x)}if(o.isInstancedMesh===!0&&o.morphTexture!==null)l.getUniforms().setValue(i,"morphTexture",o.morphTexture,e);else{let f=0;for(let y=0;y<c.length;y++)f+=c[y];let g=a.morphTargetsRelative?1:1-f;l.getUniforms().setValue(i,"morphTargetBaseInfluence",g),l.getUniforms().setValue(i,"morphTargetInfluences",c)}l.getUniforms().setValue(i,"morphTargetsTexture",u.texture,e),l.getUniforms().setValue(i,"morphTargetsTextureSize",u.size)}return{update:r}}function U0(i,t,e,n){let s=new WeakMap;function r(l){let c=n.render.frame,h=l.geometry,d=t.get(l,h);if(s.get(d)!==c&&(t.update(d),s.set(d,c)),l.isInstancedMesh&&(l.hasEventListener("dispose",a)===!1&&l.addEventListener("dispose",a),s.get(l)!==c&&(e.update(l.instanceMatrix,i.ARRAY_BUFFER),l.instanceColor!==null&&e.update(l.instanceColor,i.ARRAY_BUFFER),s.set(l,c))),l.isSkinnedMesh){let u=l.skeleton;s.get(u)!==c&&(u.update(),s.set(u,c))}return d}function o(){s=new WeakMap}function a(l){let c=l.target;c.removeEventListener("dispose",a),e.remove(c.instanceMatrix),c.instanceColor!==null&&e.remove(c.instanceColor)}return{update:r,dispose:o}}var lo=class extends nn{constructor(t,e,n,s,r,o,a,l,c,h=fs){if(h!==fs&&h!==Ms)throw new Error("DepthTexture format must be either THREE.DepthFormat or THREE.DepthStencilFormat");n===void 0&&h===fs&&(n=Bi),n===void 0&&h===Ms&&(n=ys),super(null,s,r,o,a,l,h,n,c),this.isDepthTexture=!0,this.image={width:t,height:e},this.magFilter=a!==void 0?a:un,this.minFilter=l!==void 0?l:un,this.flipY=!1,this.generateMipmaps=!1,this.compareFunction=null}copy(t){return super.copy(t),this.compareFunction=t.compareFunction,this}toJSON(t){let e=super.toJSON(t);return this.compareFunction!==null&&(e.compareFunction=this.compareFunction),e}},Xh=new nn,eh=new lo(1,1),qh=new eo,Yh=new al,$h=new ao,nh=[],ih=[],sh=new Float32Array(16),rh=new Float32Array(9),oh=new Float32Array(4);function Is(i,t,e){let n=i[0];if(n<=0||n>0)return i;let s=t*e,r=nh[s];if(r===void 0&&(r=new Float32Array(s),nh[s]=r),t!==0){n.toArray(r,0);for(let o=1,a=0;o!==t;++o)a+=e,i[o].toArray(r,a)}return r}function Ie(i,t){if(i.length!==t.length)return!1;for(let e=0,n=i.length;e<n;e++)if(i[e]!==t[e])return!1;return!0}function Le(i,t){for(let e=0,n=t.length;e<n;e++)i[e]=t[e]}function wo(i,t){let e=ih[t];e===void 0&&(e=new Int32Array(t),ih[t]=e);for(let n=0;n!==t;++n)e[n]=i.allocateTextureUnit();return e}function N0(i,t){let e=this.cache;e[0]!==t&&(i.uniform1f(this.addr,t),e[0]=t)}function F0(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y)&&(i.uniform2f(this.addr,t.x,t.y),e[0]=t.x,e[1]=t.y);else{if(Ie(e,t))return;i.uniform2fv(this.addr,t),Le(e,t)}}function O0(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y||e[2]!==t.z)&&(i.uniform3f(this.addr,t.x,t.y,t.z),e[0]=t.x,e[1]=t.y,e[2]=t.z);else if(t.r!==void 0)(e[0]!==t.r||e[1]!==t.g||e[2]!==t.b)&&(i.uniform3f(this.addr,t.r,t.g,t.b),e[0]=t.r,e[1]=t.g,e[2]=t.b);else{if(Ie(e,t))return;i.uniform3fv(this.addr,t),Le(e,t)}}function B0(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y||e[2]!==t.z||e[3]!==t.w)&&(i.uniform4f(this.addr,t.x,t.y,t.z,t.w),e[0]=t.x,e[1]=t.y,e[2]=t.z,e[3]=t.w);else{if(Ie(e,t))return;i.uniform4fv(this.addr,t),Le(e,t)}}function z0(i,t){let e=this.cache,n=t.elements;if(n===void 0){if(Ie(e,t))return;i.uniformMatrix2fv(this.addr,!1,t),Le(e,t)}else{if(Ie(e,n))return;oh.set(n),i.uniformMatrix2fv(this.addr,!1,oh),Le(e,n)}}function H0(i,t){let e=this.cache,n=t.elements;if(n===void 0){if(Ie(e,t))return;i.uniformMatrix3fv(this.addr,!1,t),Le(e,t)}else{if(Ie(e,n))return;rh.set(n),i.uniformMatrix3fv(this.addr,!1,rh),Le(e,n)}}function k0(i,t){let e=this.cache,n=t.elements;if(n===void 0){if(Ie(e,t))return;i.uniformMatrix4fv(this.addr,!1,t),Le(e,t)}else{if(Ie(e,n))return;sh.set(n),i.uniformMatrix4fv(this.addr,!1,sh),Le(e,n)}}function V0(i,t){let e=this.cache;e[0]!==t&&(i.uniform1i(this.addr,t),e[0]=t)}function G0(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y)&&(i.uniform2i(this.addr,t.x,t.y),e[0]=t.x,e[1]=t.y);else{if(Ie(e,t))return;i.uniform2iv(this.addr,t),Le(e,t)}}function W0(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y||e[2]!==t.z)&&(i.uniform3i(this.addr,t.x,t.y,t.z),e[0]=t.x,e[1]=t.y,e[2]=t.z);else{if(Ie(e,t))return;i.uniform3iv(this.addr,t),Le(e,t)}}function X0(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y||e[2]!==t.z||e[3]!==t.w)&&(i.uniform4i(this.addr,t.x,t.y,t.z,t.w),e[0]=t.x,e[1]=t.y,e[2]=t.z,e[3]=t.w);else{if(Ie(e,t))return;i.uniform4iv(this.addr,t),Le(e,t)}}function q0(i,t){let e=this.cache;e[0]!==t&&(i.uniform1ui(this.addr,t),e[0]=t)}function Y0(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y)&&(i.uniform2ui(this.addr,t.x,t.y),e[0]=t.x,e[1]=t.y);else{if(Ie(e,t))return;i.uniform2uiv(this.addr,t),Le(e,t)}}function $0(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y||e[2]!==t.z)&&(i.uniform3ui(this.addr,t.x,t.y,t.z),e[0]=t.x,e[1]=t.y,e[2]=t.z);else{if(Ie(e,t))return;i.uniform3uiv(this.addr,t),Le(e,t)}}function Z0(i,t){let e=this.cache;if(t.x!==void 0)(e[0]!==t.x||e[1]!==t.y||e[2]!==t.z||e[3]!==t.w)&&(i.uniform4ui(this.addr,t.x,t.y,t.z,t.w),e[0]=t.x,e[1]=t.y,e[2]=t.z,e[3]=t.w);else{if(Ie(e,t))return;i.uniform4uiv(this.addr,t),Le(e,t)}}function J0(i,t,e){let n=this.cache,s=e.allocateTextureUnit();n[0]!==s&&(i.uniform1i(this.addr,s),n[0]=s);let r;this.type===i.SAMPLER_2D_SHADOW?(eh.compareFunction=Hh,r=eh):r=Xh,e.setTexture2D(t||r,s)}function K0(i,t,e){let n=this.cache,s=e.allocateTextureUnit();n[0]!==s&&(i.uniform1i(this.addr,s),n[0]=s),e.setTexture3D(t||Yh,s)}function j0(i,t,e){let n=this.cache,s=e.allocateTextureUnit();n[0]!==s&&(i.uniform1i(this.addr,s),n[0]=s),e.setTextureCube(t||$h,s)}function Q0(i,t,e){let n=this.cache,s=e.allocateTextureUnit();n[0]!==s&&(i.uniform1i(this.addr,s),n[0]=s),e.setTexture2DArray(t||qh,s)}function tm(i){switch(i){case 5126:return N0;case 35664:return F0;case 35665:return O0;case 35666:return B0;case 35674:return z0;case 35675:return H0;case 35676:return k0;case 5124:case 35670:return V0;case 35667:case 35671:return G0;case 35668:case 35672:return W0;case 35669:case 35673:return X0;case 5125:return q0;case 36294:return Y0;case 36295:return $0;case 36296:return Z0;case 35678:case 36198:case 36298:case 36306:case 35682:return J0;case 35679:case 36299:case 36307:return K0;case 35680:case 36300:case 36308:case 36293:return j0;case 36289:case 36303:case 36311:case 36292:return Q0}}function em(i,t){i.uniform1fv(this.addr,t)}function nm(i,t){let e=Is(t,this.size,2);i.uniform2fv(this.addr,e)}function im(i,t){let e=Is(t,this.size,3);i.uniform3fv(this.addr,e)}function sm(i,t){let e=Is(t,this.size,4);i.uniform4fv(this.addr,e)}function rm(i,t){let e=Is(t,this.size,4);i.uniformMatrix2fv(this.addr,!1,e)}function om(i,t){let e=Is(t,this.size,9);i.uniformMatrix3fv(this.addr,!1,e)}function am(i,t){let e=Is(t,this.size,16);i.uniformMatrix4fv(this.addr,!1,e)}function lm(i,t){i.uniform1iv(this.addr,t)}function cm(i,t){i.uniform2iv(this.addr,t)}function hm(i,t){i.uniform3iv(this.addr,t)}function um(i,t){i.uniform4iv(this.addr,t)}function dm(i,t){i.uniform1uiv(this.addr,t)}function fm(i,t){i.uniform2uiv(this.addr,t)}function pm(i,t){i.uniform3uiv(this.addr,t)}function mm(i,t){i.uniform4uiv(this.addr,t)}function gm(i,t,e){let n=this.cache,s=t.length,r=wo(e,s);Ie(n,r)||(i.uniform1iv(this.addr,r),Le(n,r));for(let o=0;o!==s;++o)e.setTexture2D(t[o]||Xh,r[o])}function vm(i,t,e){let n=this.cache,s=t.length,r=wo(e,s);Ie(n,r)||(i.uniform1iv(this.addr,r),Le(n,r));for(let o=0;o!==s;++o)e.setTexture3D(t[o]||Yh,r[o])}function xm(i,t,e){let n=this.cache,s=t.length,r=wo(e,s);Ie(n,r)||(i.uniform1iv(this.addr,r),Le(n,r));for(let o=0;o!==s;++o)e.setTextureCube(t[o]||$h,r[o])}function _m(i,t,e){let n=this.cache,s=t.length,r=wo(e,s);Ie(n,r)||(i.uniform1iv(this.addr,r),Le(n,r));for(let o=0;o!==s;++o)e.setTexture2DArray(t[o]||qh,r[o])}function ym(i){switch(i){case 5126:return em;case 35664:return nm;case 35665:return im;case 35666:return sm;case 35674:return rm;case 35675:return om;case 35676:return am;case 5124:case 35670:return lm;case 35667:case 35671:return cm;case 35668:case 35672:return hm;case 35669:case 35673:return um;case 5125:return dm;case 36294:return fm;case 36295:return pm;case 36296:return mm;case 35678:case 36198:case 36298:case 36306:case 35682:return gm;case 35679:case 36299:case 36307:return vm;case 35680:case 36300:case 36308:case 36293:return xm;case 36289:case 36303:case 36311:case 36292:return _m}}var hl=class{constructor(t,e,n){this.id=t,this.addr=n,this.cache=[],this.type=e.type,this.setValue=tm(e.type)}},ul=class{constructor(t,e,n){this.id=t,this.addr=n,this.cache=[],this.type=e.type,this.size=e.size,this.setValue=ym(e.type)}},dl=class{constructor(t){this.id=t,this.seq=[],this.map={}}setValue(t,e,n){let s=this.seq;for(let r=0,o=s.length;r!==o;++r){let a=s[r];a.setValue(t,e[a.id],n)}}},fa=/(\w+)(\])?(\[|\.)?/g;function ah(i,t){i.seq.push(t),i.map[t.id]=t}function Mm(i,t,e){let n=i.name,s=n.length;for(fa.lastIndex=0;;){let r=fa.exec(n),o=fa.lastIndex,a=r[1],l=r[2]==="]",c=r[3];if(l&&(a=a|0),c===void 0||c==="["&&o+2===s){ah(e,c===void 0?new hl(a,i,t):new ul(a,i,t));break}else{let d=e.map[a];d===void 0&&(d=new dl(a),ah(e,d)),e=d}}}var ms=class{constructor(t,e){this.seq=[],this.map={};let n=t.getProgramParameter(e,t.ACTIVE_UNIFORMS);for(let s=0;s<n;++s){let r=t.getActiveUniform(e,s),o=t.getUniformLocation(e,r.name);Mm(r,o,this)}}setValue(t,e,n,s){let r=this.map[e];r!==void 0&&r.setValue(t,n,s)}setOptional(t,e,n){let s=e[n];s!==void 0&&this.setValue(t,n,s)}static upload(t,e,n,s){for(let r=0,o=e.length;r!==o;++r){let a=e[r],l=n[a.id];l.needsUpdate!==!1&&a.setValue(t,l.value,s)}}static seqWithValue(t,e){let n=[];for(let s=0,r=t.length;s!==r;++s){let o=t[s];o.id in e&&n.push(o)}return n}};function lh(i,t,e){let n=i.createShader(t);return i.shaderSource(n,e),i.compileShader(n),n}var Sm=37297,bm=0;function Em(i,t){let e=i.split(`
`),n=[],s=Math.max(t-6,0),r=Math.min(t+6,e.length);for(let o=s;o<r;o++){let a=o+1;n.push(`${a===t?">":" "} ${a}: ${e[o]}`)}return n.join(`
`)}var ch=new Ot;function wm(i){jt._getMatrix(ch,jt.workingColorSpace,i);let t=`mat3( ${ch.elements.map(e=>e.toFixed(4))} )`;switch(jt.getTransfer(i)){case Eo:return[t,"LinearTransferOETF"];case ce:return[t,"sRGBTransferOETF"];default:return console.warn("THREE.WebGLProgram: Unsupported color space: ",i),[t,"LinearTransferOETF"]}}function hh(i,t,e){let n=i.getShaderParameter(t,i.COMPILE_STATUS),s=i.getShaderInfoLog(t).trim();if(n&&s==="")return"";let r=/ERROR: 0:(\d+)/.exec(s);if(r){let o=parseInt(r[1]);return e.toUpperCase()+`

`+s+`

`+Em(i.getShaderSource(t),o)}else return s}function Tm(i,t){let e=wm(t);return[`vec4 ${i}( vec4 value ) {`,`	return ${e[1]}( vec4( value.rgb * ${e[0]}, value.a ) );`,"}"].join(`
`)}function Am(i,t){let e;switch(t){case ju:e="Linear";break;case Qu:e="Reinhard";break;case td:e="Cineon";break;case Wl:e="ACESFilmic";break;case nd:e="AgX";break;case id:e="Neutral";break;case ed:e="Custom";break;default:console.warn("THREE.WebGLProgram: Unsupported toneMapping:",t),e="Linear"}return"vec3 "+i+"( vec3 color ) { return "+e+"ToneMapping( color ); }"}var zr=new P;function Rm(){jt.getLuminanceCoefficients(zr);let i=zr.x.toFixed(4),t=zr.y.toFixed(4),e=zr.z.toFixed(4);return["float luminance( const in vec3 rgb ) {",`	const vec3 weights = vec3( ${i}, ${t}, ${e} );`,"	return dot( weights, rgb );","}"].join(`
`)}function Cm(i){return[i.extensionClipCullDistance?"#extension GL_ANGLE_clip_cull_distance : require":"",i.extensionMultiDraw?"#extension GL_ANGLE_multi_draw : require":""].filter(js).join(`
`)}function Pm(i){let t=[];for(let e in i){let n=i[e];n!==!1&&t.push("#define "+e+" "+n)}return t.join(`
`)}function Im(i,t){let e={},n=i.getProgramParameter(t,i.ACTIVE_ATTRIBUTES);for(let s=0;s<n;s++){let r=i.getActiveAttrib(t,s),o=r.name,a=1;r.type===i.FLOAT_MAT2&&(a=2),r.type===i.FLOAT_MAT3&&(a=3),r.type===i.FLOAT_MAT4&&(a=4),e[o]={type:r.type,location:i.getAttribLocation(t,o),locationSize:a}}return e}function js(i){return i!==""}function uh(i,t){let e=t.numSpotLightShadows+t.numSpotLightMaps-t.numSpotLightShadowsWithMaps;return i.replace(/NUM_DIR_LIGHTS/g,t.numDirLights).replace(/NUM_SPOT_LIGHTS/g,t.numSpotLights).replace(/NUM_SPOT_LIGHT_MAPS/g,t.numSpotLightMaps).replace(/NUM_SPOT_LIGHT_COORDS/g,e).replace(/NUM_RECT_AREA_LIGHTS/g,t.numRectAreaLights).replace(/NUM_POINT_LIGHTS/g,t.numPointLights).replace(/NUM_HEMI_LIGHTS/g,t.numHemiLights).replace(/NUM_DIR_LIGHT_SHADOWS/g,t.numDirLightShadows).replace(/NUM_SPOT_LIGHT_SHADOWS_WITH_MAPS/g,t.numSpotLightShadowsWithMaps).replace(/NUM_SPOT_LIGHT_SHADOWS/g,t.numSpotLightShadows).replace(/NUM_POINT_LIGHT_SHADOWS/g,t.numPointLightShadows)}function dh(i,t){return i.replace(/NUM_CLIPPING_PLANES/g,t.numClippingPlanes).replace(/UNION_CLIPPING_PLANES/g,t.numClippingPlanes-t.numClipIntersection)}var Lm=/^[ \t]*#include +<([\w\d./]+)>/gm;function fl(i){return i.replace(Lm,Um)}var Dm=new Map;function Um(i,t){let e=qt[t];if(e===void 0){let n=Dm.get(t);if(n!==void 0)e=qt[n],console.warn('THREE.WebGLRenderer: Shader chunk "%s" has been deprecated. Use "%s" instead.',t,n);else throw new Error("Can not resolve #include <"+t+">")}return fl(e)}var Nm=/#pragma unroll_loop_start\s+for\s*\(\s*int\s+i\s*=\s*(\d+)\s*;\s*i\s*<\s*(\d+)\s*;\s*i\s*\+\+\s*\)\s*{([\s\S]+?)}\s+#pragma unroll_loop_end/g;function fh(i){return i.replace(Nm,Fm)}function Fm(i,t,e,n){let s="";for(let r=parseInt(t);r<parseInt(e);r++)s+=n.replace(/\[\s*i\s*\]/g,"[ "+r+" ]").replace(/UNROLLED_LOOP_INDEX/g,r);return s}function ph(i){let t=`precision ${i.precision} float;
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
#define LOW_PRECISION`),t}function Om(i){let t="SHADOWMAP_TYPE_BASIC";return i.shadowMapType===Ah?t="SHADOWMAP_TYPE_PCF":i.shadowMapType===Iu?t="SHADOWMAP_TYPE_PCF_SOFT":i.shadowMapType===ti&&(t="SHADOWMAP_TYPE_VSM"),t}function Bm(i){let t="ENVMAP_TYPE_CUBE";if(i.envMap)switch(i.envMapMode){case vs:case xs:t="ENVMAP_TYPE_CUBE";break;case bo:t="ENVMAP_TYPE_CUBE_UV";break}return t}function zm(i){let t="ENVMAP_MODE_REFLECTION";if(i.envMap)switch(i.envMapMode){case xs:t="ENVMAP_MODE_REFRACTION";break}return t}function Hm(i){let t="ENVMAP_BLENDING_NONE";if(i.envMap)switch(i.combine){case Rh:t="ENVMAP_BLENDING_MULTIPLY";break;case Ju:t="ENVMAP_BLENDING_MIX";break;case Ku:t="ENVMAP_BLENDING_ADD";break}return t}function km(i){let t=i.envMapCubeUVHeight;if(t===null)return null;let e=Math.log2(t)-2,n=1/t;return{texelWidth:1/(3*Math.max(Math.pow(2,e),7*16)),texelHeight:n,maxMip:e}}function Vm(i,t,e,n){let s=i.getContext(),r=e.defines,o=e.vertexShader,a=e.fragmentShader,l=Om(e),c=Bm(e),h=zm(e),d=Hm(e),u=km(e),f=Cm(e),g=Pm(r),y=s.createProgram(),m,p,w=e.glslVersion?"#version "+e.glslVersion+`
`:"";e.isRawShaderMaterial?(m=["#define SHADER_TYPE "+e.shaderType,"#define SHADER_NAME "+e.shaderName,g].filter(js).join(`
`),m.length>0&&(m+=`
`),p=["#define SHADER_TYPE "+e.shaderType,"#define SHADER_NAME "+e.shaderName,g].filter(js).join(`
`),p.length>0&&(p+=`
`)):(m=[ph(e),"#define SHADER_TYPE "+e.shaderType,"#define SHADER_NAME "+e.shaderName,g,e.extensionClipCullDistance?"#define USE_CLIP_DISTANCE":"",e.batching?"#define USE_BATCHING":"",e.batchingColor?"#define USE_BATCHING_COLOR":"",e.instancing?"#define USE_INSTANCING":"",e.instancingColor?"#define USE_INSTANCING_COLOR":"",e.instancingMorph?"#define USE_INSTANCING_MORPH":"",e.useFog&&e.fog?"#define USE_FOG":"",e.useFog&&e.fogExp2?"#define FOG_EXP2":"",e.map?"#define USE_MAP":"",e.envMap?"#define USE_ENVMAP":"",e.envMap?"#define "+h:"",e.lightMap?"#define USE_LIGHTMAP":"",e.aoMap?"#define USE_AOMAP":"",e.bumpMap?"#define USE_BUMPMAP":"",e.normalMap?"#define USE_NORMALMAP":"",e.normalMapObjectSpace?"#define USE_NORMALMAP_OBJECTSPACE":"",e.normalMapTangentSpace?"#define USE_NORMALMAP_TANGENTSPACE":"",e.displacementMap?"#define USE_DISPLACEMENTMAP":"",e.emissiveMap?"#define USE_EMISSIVEMAP":"",e.anisotropy?"#define USE_ANISOTROPY":"",e.anisotropyMap?"#define USE_ANISOTROPYMAP":"",e.clearcoatMap?"#define USE_CLEARCOATMAP":"",e.clearcoatRoughnessMap?"#define USE_CLEARCOAT_ROUGHNESSMAP":"",e.clearcoatNormalMap?"#define USE_CLEARCOAT_NORMALMAP":"",e.iridescenceMap?"#define USE_IRIDESCENCEMAP":"",e.iridescenceThicknessMap?"#define USE_IRIDESCENCE_THICKNESSMAP":"",e.specularMap?"#define USE_SPECULARMAP":"",e.specularColorMap?"#define USE_SPECULAR_COLORMAP":"",e.specularIntensityMap?"#define USE_SPECULAR_INTENSITYMAP":"",e.roughnessMap?"#define USE_ROUGHNESSMAP":"",e.metalnessMap?"#define USE_METALNESSMAP":"",e.alphaMap?"#define USE_ALPHAMAP":"",e.alphaHash?"#define USE_ALPHAHASH":"",e.transmission?"#define USE_TRANSMISSION":"",e.transmissionMap?"#define USE_TRANSMISSIONMAP":"",e.thicknessMap?"#define USE_THICKNESSMAP":"",e.sheenColorMap?"#define USE_SHEEN_COLORMAP":"",e.sheenRoughnessMap?"#define USE_SHEEN_ROUGHNESSMAP":"",e.mapUv?"#define MAP_UV "+e.mapUv:"",e.alphaMapUv?"#define ALPHAMAP_UV "+e.alphaMapUv:"",e.lightMapUv?"#define LIGHTMAP_UV "+e.lightMapUv:"",e.aoMapUv?"#define AOMAP_UV "+e.aoMapUv:"",e.emissiveMapUv?"#define EMISSIVEMAP_UV "+e.emissiveMapUv:"",e.bumpMapUv?"#define BUMPMAP_UV "+e.bumpMapUv:"",e.normalMapUv?"#define NORMALMAP_UV "+e.normalMapUv:"",e.displacementMapUv?"#define DISPLACEMENTMAP_UV "+e.displacementMapUv:"",e.metalnessMapUv?"#define METALNESSMAP_UV "+e.metalnessMapUv:"",e.roughnessMapUv?"#define ROUGHNESSMAP_UV "+e.roughnessMapUv:"",e.anisotropyMapUv?"#define ANISOTROPYMAP_UV "+e.anisotropyMapUv:"",e.clearcoatMapUv?"#define CLEARCOATMAP_UV "+e.clearcoatMapUv:"",e.clearcoatNormalMapUv?"#define CLEARCOAT_NORMALMAP_UV "+e.clearcoatNormalMapUv:"",e.clearcoatRoughnessMapUv?"#define CLEARCOAT_ROUGHNESSMAP_UV "+e.clearcoatRoughnessMapUv:"",e.iridescenceMapUv?"#define IRIDESCENCEMAP_UV "+e.iridescenceMapUv:"",e.iridescenceThicknessMapUv?"#define IRIDESCENCE_THICKNESSMAP_UV "+e.iridescenceThicknessMapUv:"",e.sheenColorMapUv?"#define SHEEN_COLORMAP_UV "+e.sheenColorMapUv:"",e.sheenRoughnessMapUv?"#define SHEEN_ROUGHNESSMAP_UV "+e.sheenRoughnessMapUv:"",e.specularMapUv?"#define SPECULARMAP_UV "+e.specularMapUv:"",e.specularColorMapUv?"#define SPECULAR_COLORMAP_UV "+e.specularColorMapUv:"",e.specularIntensityMapUv?"#define SPECULAR_INTENSITYMAP_UV "+e.specularIntensityMapUv:"",e.transmissionMapUv?"#define TRANSMISSIONMAP_UV "+e.transmissionMapUv:"",e.thicknessMapUv?"#define THICKNESSMAP_UV "+e.thicknessMapUv:"",e.vertexTangents&&e.flatShading===!1?"#define USE_TANGENT":"",e.vertexColors?"#define USE_COLOR":"",e.vertexAlphas?"#define USE_COLOR_ALPHA":"",e.vertexUv1s?"#define USE_UV1":"",e.vertexUv2s?"#define USE_UV2":"",e.vertexUv3s?"#define USE_UV3":"",e.pointsUvs?"#define USE_POINTS_UV":"",e.flatShading?"#define FLAT_SHADED":"",e.skinning?"#define USE_SKINNING":"",e.morphTargets?"#define USE_MORPHTARGETS":"",e.morphNormals&&e.flatShading===!1?"#define USE_MORPHNORMALS":"",e.morphColors?"#define USE_MORPHCOLORS":"",e.morphTargetsCount>0?"#define MORPHTARGETS_TEXTURE_STRIDE "+e.morphTextureStride:"",e.morphTargetsCount>0?"#define MORPHTARGETS_COUNT "+e.morphTargetsCount:"",e.doubleSided?"#define DOUBLE_SIDED":"",e.flipSided?"#define FLIP_SIDED":"",e.shadowMapEnabled?"#define USE_SHADOWMAP":"",e.shadowMapEnabled?"#define "+l:"",e.sizeAttenuation?"#define USE_SIZEATTENUATION":"",e.numLightProbes>0?"#define USE_LIGHT_PROBES":"",e.logarithmicDepthBuffer?"#define USE_LOGDEPTHBUF":"",e.reverseDepthBuffer?"#define USE_REVERSEDEPTHBUF":"","uniform mat4 modelMatrix;","uniform mat4 modelViewMatrix;","uniform mat4 projectionMatrix;","uniform mat4 viewMatrix;","uniform mat3 normalMatrix;","uniform vec3 cameraPosition;","uniform bool isOrthographic;","#ifdef USE_INSTANCING","	attribute mat4 instanceMatrix;","#endif","#ifdef USE_INSTANCING_COLOR","	attribute vec3 instanceColor;","#endif","#ifdef USE_INSTANCING_MORPH","	uniform sampler2D morphTexture;","#endif","attribute vec3 position;","attribute vec3 normal;","attribute vec2 uv;","#ifdef USE_UV1","	attribute vec2 uv1;","#endif","#ifdef USE_UV2","	attribute vec2 uv2;","#endif","#ifdef USE_UV3","	attribute vec2 uv3;","#endif","#ifdef USE_TANGENT","	attribute vec4 tangent;","#endif","#if defined( USE_COLOR_ALPHA )","	attribute vec4 color;","#elif defined( USE_COLOR )","	attribute vec3 color;","#endif","#ifdef USE_SKINNING","	attribute vec4 skinIndex;","	attribute vec4 skinWeight;","#endif",`
`].filter(js).join(`
`),p=[ph(e),"#define SHADER_TYPE "+e.shaderType,"#define SHADER_NAME "+e.shaderName,g,e.useFog&&e.fog?"#define USE_FOG":"",e.useFog&&e.fogExp2?"#define FOG_EXP2":"",e.alphaToCoverage?"#define ALPHA_TO_COVERAGE":"",e.map?"#define USE_MAP":"",e.matcap?"#define USE_MATCAP":"",e.envMap?"#define USE_ENVMAP":"",e.envMap?"#define "+c:"",e.envMap?"#define "+h:"",e.envMap?"#define "+d:"",u?"#define CUBEUV_TEXEL_WIDTH "+u.texelWidth:"",u?"#define CUBEUV_TEXEL_HEIGHT "+u.texelHeight:"",u?"#define CUBEUV_MAX_MIP "+u.maxMip+".0":"",e.lightMap?"#define USE_LIGHTMAP":"",e.aoMap?"#define USE_AOMAP":"",e.bumpMap?"#define USE_BUMPMAP":"",e.normalMap?"#define USE_NORMALMAP":"",e.normalMapObjectSpace?"#define USE_NORMALMAP_OBJECTSPACE":"",e.normalMapTangentSpace?"#define USE_NORMALMAP_TANGENTSPACE":"",e.emissiveMap?"#define USE_EMISSIVEMAP":"",e.anisotropy?"#define USE_ANISOTROPY":"",e.anisotropyMap?"#define USE_ANISOTROPYMAP":"",e.clearcoat?"#define USE_CLEARCOAT":"",e.clearcoatMap?"#define USE_CLEARCOATMAP":"",e.clearcoatRoughnessMap?"#define USE_CLEARCOAT_ROUGHNESSMAP":"",e.clearcoatNormalMap?"#define USE_CLEARCOAT_NORMALMAP":"",e.dispersion?"#define USE_DISPERSION":"",e.iridescence?"#define USE_IRIDESCENCE":"",e.iridescenceMap?"#define USE_IRIDESCENCEMAP":"",e.iridescenceThicknessMap?"#define USE_IRIDESCENCE_THICKNESSMAP":"",e.specularMap?"#define USE_SPECULARMAP":"",e.specularColorMap?"#define USE_SPECULAR_COLORMAP":"",e.specularIntensityMap?"#define USE_SPECULAR_INTENSITYMAP":"",e.roughnessMap?"#define USE_ROUGHNESSMAP":"",e.metalnessMap?"#define USE_METALNESSMAP":"",e.alphaMap?"#define USE_ALPHAMAP":"",e.alphaTest?"#define USE_ALPHATEST":"",e.alphaHash?"#define USE_ALPHAHASH":"",e.sheen?"#define USE_SHEEN":"",e.sheenColorMap?"#define USE_SHEEN_COLORMAP":"",e.sheenRoughnessMap?"#define USE_SHEEN_ROUGHNESSMAP":"",e.transmission?"#define USE_TRANSMISSION":"",e.transmissionMap?"#define USE_TRANSMISSIONMAP":"",e.thicknessMap?"#define USE_THICKNESSMAP":"",e.vertexTangents&&e.flatShading===!1?"#define USE_TANGENT":"",e.vertexColors||e.instancingColor||e.batchingColor?"#define USE_COLOR":"",e.vertexAlphas?"#define USE_COLOR_ALPHA":"",e.vertexUv1s?"#define USE_UV1":"",e.vertexUv2s?"#define USE_UV2":"",e.vertexUv3s?"#define USE_UV3":"",e.pointsUvs?"#define USE_POINTS_UV":"",e.gradientMap?"#define USE_GRADIENTMAP":"",e.flatShading?"#define FLAT_SHADED":"",e.doubleSided?"#define DOUBLE_SIDED":"",e.flipSided?"#define FLIP_SIDED":"",e.shadowMapEnabled?"#define USE_SHADOWMAP":"",e.shadowMapEnabled?"#define "+l:"",e.premultipliedAlpha?"#define PREMULTIPLIED_ALPHA":"",e.numLightProbes>0?"#define USE_LIGHT_PROBES":"",e.decodeVideoTexture?"#define DECODE_VIDEO_TEXTURE":"",e.decodeVideoTextureEmissive?"#define DECODE_VIDEO_TEXTURE_EMISSIVE":"",e.logarithmicDepthBuffer?"#define USE_LOGDEPTHBUF":"",e.reverseDepthBuffer?"#define USE_REVERSEDEPTHBUF":"","uniform mat4 viewMatrix;","uniform vec3 cameraPosition;","uniform bool isOrthographic;",e.toneMapping!==vi?"#define TONE_MAPPING":"",e.toneMapping!==vi?qt.tonemapping_pars_fragment:"",e.toneMapping!==vi?Am("toneMapping",e.toneMapping):"",e.dithering?"#define DITHERING":"",e.opaque?"#define OPAQUE":"",qt.colorspace_pars_fragment,Tm("linearToOutputTexel",e.outputColorSpace),Rm(),e.useDepthPacking?"#define DEPTH_PACKING "+e.depthPacking:"",`
`].filter(js).join(`
`)),o=fl(o),o=uh(o,e),o=dh(o,e),a=fl(a),a=uh(a,e),a=dh(a,e),o=fh(o),a=fh(a),e.isRawShaderMaterial!==!0&&(w=`#version 300 es
`,m=[f,"#define attribute in","#define varying out","#define texture2D texture"].join(`
`)+`
`+m,p=["#define varying in",e.glslVersion===Rc?"":"layout(location = 0) out highp vec4 pc_fragColor;",e.glslVersion===Rc?"":"#define gl_FragColor pc_fragColor","#define gl_FragDepthEXT gl_FragDepth","#define texture2D texture","#define textureCube texture","#define texture2DProj textureProj","#define texture2DLodEXT textureLod","#define texture2DProjLodEXT textureProjLod","#define textureCubeLodEXT textureLod","#define texture2DGradEXT textureGrad","#define texture2DProjGradEXT textureProjGrad","#define textureCubeGradEXT textureGrad"].join(`
`)+`
`+p);let E=w+m+o,M=w+p+a,U=lh(s,s.VERTEX_SHADER,E),C=lh(s,s.FRAGMENT_SHADER,M);s.attachShader(y,U),s.attachShader(y,C),e.index0AttributeName!==void 0?s.bindAttribLocation(y,0,e.index0AttributeName):e.morphTargets===!0&&s.bindAttribLocation(y,0,"position"),s.linkProgram(y);function v(R){if(i.debug.checkShaderErrors){let I=s.getProgramInfoLog(y).trim(),L=s.getShaderInfoLog(U).trim(),N=s.getShaderInfoLog(C).trim(),V=!0,F=!0;if(s.getProgramParameter(y,s.LINK_STATUS)===!1)if(V=!1,typeof i.debug.onShaderError=="function")i.debug.onShaderError(s,y,U,C);else{let X=hh(s,U,"vertex"),z=hh(s,C,"fragment");console.error("THREE.WebGLProgram: Shader Error "+s.getError()+" - VALIDATE_STATUS "+s.getProgramParameter(y,s.VALIDATE_STATUS)+`

Material Name: `+R.name+`
Material Type: `+R.type+`

Program Info Log: `+I+`
`+X+`
`+z)}else I!==""?console.warn("THREE.WebGLProgram: Program Info Log:",I):(L===""||N==="")&&(F=!1);F&&(R.diagnostics={runnable:V,programLog:I,vertexShader:{log:L,prefix:m},fragmentShader:{log:N,prefix:p}})}s.deleteShader(U),s.deleteShader(C),b=new ms(s,y),x=Im(s,y)}let b;this.getUniforms=function(){return b===void 0&&v(this),b};let x;this.getAttributes=function(){return x===void 0&&v(this),x};let _=e.rendererExtensionParallelShaderCompile===!1;return this.isReady=function(){return _===!1&&(_=s.getProgramParameter(y,Sm)),_},this.destroy=function(){n.releaseStatesOfProgram(this),s.deleteProgram(y),this.program=void 0},this.type=e.shaderType,this.name=e.shaderName,this.id=bm++,this.cacheKey=t,this.usedTimes=1,this.program=y,this.vertexShader=U,this.fragmentShader=C,this}var Gm=0,pl=class{constructor(){this.shaderCache=new Map,this.materialCache=new Map}update(t){let e=t.vertexShader,n=t.fragmentShader,s=this._getShaderStage(e),r=this._getShaderStage(n),o=this._getShaderCacheForMaterial(t);return o.has(s)===!1&&(o.add(s),s.usedTimes++),o.has(r)===!1&&(o.add(r),r.usedTimes++),this}remove(t){let e=this.materialCache.get(t);for(let n of e)n.usedTimes--,n.usedTimes===0&&this.shaderCache.delete(n.code);return this.materialCache.delete(t),this}getVertexShaderID(t){return this._getShaderStage(t.vertexShader).id}getFragmentShaderID(t){return this._getShaderStage(t.fragmentShader).id}dispose(){this.shaderCache.clear(),this.materialCache.clear()}_getShaderCacheForMaterial(t){let e=this.materialCache,n=e.get(t);return n===void 0&&(n=new Set,e.set(t,n)),n}_getShaderStage(t){let e=this.shaderCache,n=e.get(t);return n===void 0&&(n=new ml(t),e.set(t,n)),n}},ml=class{constructor(t){this.id=Gm++,this.code=t,this.usedTimes=0}};function Wm(i,t,e,n,s,r,o){let a=new io,l=new pl,c=new Set,h=[],d=s.logarithmicDepthBuffer,u=s.vertexTextures,f=s.precision,g={MeshDepthMaterial:"depth",MeshDistanceMaterial:"distanceRGBA",MeshNormalMaterial:"normal",MeshBasicMaterial:"basic",MeshLambertMaterial:"lambert",MeshPhongMaterial:"phong",MeshToonMaterial:"toon",MeshStandardMaterial:"physical",MeshPhysicalMaterial:"physical",MeshMatcapMaterial:"matcap",LineBasicMaterial:"basic",LineDashedMaterial:"dashed",PointsMaterial:"points",ShadowMaterial:"shadow",SpriteMaterial:"sprite"};function y(x){return c.add(x),x===0?"uv":`uv${x}`}function m(x,_,R,I,L){let N=I.fog,V=L.geometry,F=x.isMeshStandardMaterial?I.environment:null,X=(x.isMeshStandardMaterial?e:t).get(x.envMap||F),z=X&&X.mapping===bo?X.image.height:null,j=g[x.type];x.precision!==null&&(f=s.getMaxPrecision(x.precision),f!==x.precision&&console.warn("THREE.WebGLProgram.getParameters:",x.precision,"not supported, using",f,"instead."));let it=V.morphAttributes.position||V.morphAttributes.normal||V.morphAttributes.color,st=it!==void 0?it.length:0,yt=0;V.morphAttributes.position!==void 0&&(yt=1),V.morphAttributes.normal!==void 0&&(yt=2),V.morphAttributes.color!==void 0&&(yt=3);let Ft,$,nt,ft;if(j){let le=Fn[j];Ft=le.vertexShader,$=le.fragmentShader}else Ft=x.vertexShader,$=x.fragmentShader,l.update(x),nt=l.getVertexShaderID(x),ft=l.getFragmentShaderID(x);let ct=i.getRenderTarget(),bt=i.state.buffers.depth.getReversed(),Lt=L.isInstancedMesh===!0,Et=L.isBatchedMesh===!0,Kt=!!x.map,Ht=!!x.matcap,ue=!!X,O=!!x.aoMap,be=!!x.lightMap,Xt=!!x.bumpMap,kt=!!x.normalMap,It=!!x.displacementMap,te=!!x.emissiveMap,Rt=!!x.metalnessMap,A=!!x.roughnessMap,S=x.anisotropy>0,D=x.clearcoat>0,G=x.dispersion>0,Z=x.iridescence>0,W=x.sheen>0,et=x.transmission>0,Q=S&&!!x.anisotropyMap,rt=D&&!!x.clearcoatMap,Nt=D&&!!x.clearcoatNormalMap,tt=D&&!!x.clearcoatRoughnessMap,lt=Z&&!!x.iridescenceMap,xt=Z&&!!x.iridescenceThicknessMap,Mt=W&&!!x.sheenColorMap,_t=W&&!!x.sheenRoughnessMap,$t=!!x.specularMap,Vt=!!x.specularColorMap,fe=!!x.specularIntensityMap,B=et&&!!x.transmissionMap,ut=et&&!!x.thicknessMap,J=!!x.gradientMap,K=!!x.alphaMap,vt=x.alphaTest>0,pt=!!x.alphaHash,Gt=!!x.extensions,ye=vi;x.toneMapped&&(ct===null||ct.isXRRenderTarget===!0)&&(ye=i.toneMapping);let Ge={shaderID:j,shaderType:x.type,shaderName:x.name,vertexShader:Ft,fragmentShader:$,defines:x.defines,customVertexShaderID:nt,customFragmentShaderID:ft,isRawShaderMaterial:x.isRawShaderMaterial===!0,glslVersion:x.glslVersion,precision:f,batching:Et,batchingColor:Et&&L._colorsTexture!==null,instancing:Lt,instancingColor:Lt&&L.instanceColor!==null,instancingMorph:Lt&&L.morphTexture!==null,supportsVertexTextures:u,outputColorSpace:ct===null?i.outputColorSpace:ct.isXRRenderTarget===!0?ct.texture.colorSpace:Ps,alphaToCoverage:!!x.alphaToCoverage,map:Kt,matcap:Ht,envMap:ue,envMapMode:ue&&X.mapping,envMapCubeUVHeight:z,aoMap:O,lightMap:be,bumpMap:Xt,normalMap:kt,displacementMap:u&&It,emissiveMap:te,normalMapObjectSpace:kt&&x.normalMapType===ad,normalMapTangentSpace:kt&&x.normalMapType===zh,metalnessMap:Rt,roughnessMap:A,anisotropy:S,anisotropyMap:Q,clearcoat:D,clearcoatMap:rt,clearcoatNormalMap:Nt,clearcoatRoughnessMap:tt,dispersion:G,iridescence:Z,iridescenceMap:lt,iridescenceThicknessMap:xt,sheen:W,sheenColorMap:Mt,sheenRoughnessMap:_t,specularMap:$t,specularColorMap:Vt,specularIntensityMap:fe,transmission:et,transmissionMap:B,thicknessMap:ut,gradientMap:J,opaque:x.transparent===!1&&x.blending===ds&&x.alphaToCoverage===!1,alphaMap:K,alphaTest:vt,alphaHash:pt,combine:x.combine,mapUv:Kt&&y(x.map.channel),aoMapUv:O&&y(x.aoMap.channel),lightMapUv:be&&y(x.lightMap.channel),bumpMapUv:Xt&&y(x.bumpMap.channel),normalMapUv:kt&&y(x.normalMap.channel),displacementMapUv:It&&y(x.displacementMap.channel),emissiveMapUv:te&&y(x.emissiveMap.channel),metalnessMapUv:Rt&&y(x.metalnessMap.channel),roughnessMapUv:A&&y(x.roughnessMap.channel),anisotropyMapUv:Q&&y(x.anisotropyMap.channel),clearcoatMapUv:rt&&y(x.clearcoatMap.channel),clearcoatNormalMapUv:Nt&&y(x.clearcoatNormalMap.channel),clearcoatRoughnessMapUv:tt&&y(x.clearcoatRoughnessMap.channel),iridescenceMapUv:lt&&y(x.iridescenceMap.channel),iridescenceThicknessMapUv:xt&&y(x.iridescenceThicknessMap.channel),sheenColorMapUv:Mt&&y(x.sheenColorMap.channel),sheenRoughnessMapUv:_t&&y(x.sheenRoughnessMap.channel),specularMapUv:$t&&y(x.specularMap.channel),specularColorMapUv:Vt&&y(x.specularColorMap.channel),specularIntensityMapUv:fe&&y(x.specularIntensityMap.channel),transmissionMapUv:B&&y(x.transmissionMap.channel),thicknessMapUv:ut&&y(x.thicknessMap.channel),alphaMapUv:K&&y(x.alphaMap.channel),vertexTangents:!!V.attributes.tangent&&(kt||S),vertexColors:x.vertexColors,vertexAlphas:x.vertexColors===!0&&!!V.attributes.color&&V.attributes.color.itemSize===4,pointsUvs:L.isPoints===!0&&!!V.attributes.uv&&(Kt||K),fog:!!N,useFog:x.fog===!0,fogExp2:!!N&&N.isFogExp2,flatShading:x.flatShading===!0,sizeAttenuation:x.sizeAttenuation===!0,logarithmicDepthBuffer:d,reverseDepthBuffer:bt,skinning:L.isSkinnedMesh===!0,morphTargets:V.morphAttributes.position!==void 0,morphNormals:V.morphAttributes.normal!==void 0,morphColors:V.morphAttributes.color!==void 0,morphTargetsCount:st,morphTextureStride:yt,numDirLights:_.directional.length,numPointLights:_.point.length,numSpotLights:_.spot.length,numSpotLightMaps:_.spotLightMap.length,numRectAreaLights:_.rectArea.length,numHemiLights:_.hemi.length,numDirLightShadows:_.directionalShadowMap.length,numPointLightShadows:_.pointShadowMap.length,numSpotLightShadows:_.spotShadowMap.length,numSpotLightShadowsWithMaps:_.numSpotLightShadowsWithMaps,numLightProbes:_.numLightProbes,numClippingPlanes:o.numPlanes,numClipIntersection:o.numIntersection,dithering:x.dithering,shadowMapEnabled:i.shadowMap.enabled&&R.length>0,shadowMapType:i.shadowMap.type,toneMapping:ye,decodeVideoTexture:Kt&&x.map.isVideoTexture===!0&&jt.getTransfer(x.map.colorSpace)===ce,decodeVideoTextureEmissive:te&&x.emissiveMap.isVideoTexture===!0&&jt.getTransfer(x.emissiveMap.colorSpace)===ce,premultipliedAlpha:x.premultipliedAlpha,doubleSided:x.side===de,flipSided:x.side===Re,useDepthPacking:x.depthPacking>=0,depthPacking:x.depthPacking||0,index0AttributeName:x.index0AttributeName,extensionClipCullDistance:Gt&&x.extensions.clipCullDistance===!0&&n.has("WEBGL_clip_cull_distance"),extensionMultiDraw:(Gt&&x.extensions.multiDraw===!0||Et)&&n.has("WEBGL_multi_draw"),rendererExtensionParallelShaderCompile:n.has("KHR_parallel_shader_compile"),customProgramCacheKey:x.customProgramCacheKey()};return Ge.vertexUv1s=c.has(1),Ge.vertexUv2s=c.has(2),Ge.vertexUv3s=c.has(3),c.clear(),Ge}function p(x){let _=[];if(x.shaderID?_.push(x.shaderID):(_.push(x.customVertexShaderID),_.push(x.customFragmentShaderID)),x.defines!==void 0)for(let R in x.defines)_.push(R),_.push(x.defines[R]);return x.isRawShaderMaterial===!1&&(w(_,x),E(_,x),_.push(i.outputColorSpace)),_.push(x.customProgramCacheKey),_.join()}function w(x,_){x.push(_.precision),x.push(_.outputColorSpace),x.push(_.envMapMode),x.push(_.envMapCubeUVHeight),x.push(_.mapUv),x.push(_.alphaMapUv),x.push(_.lightMapUv),x.push(_.aoMapUv),x.push(_.bumpMapUv),x.push(_.normalMapUv),x.push(_.displacementMapUv),x.push(_.emissiveMapUv),x.push(_.metalnessMapUv),x.push(_.roughnessMapUv),x.push(_.anisotropyMapUv),x.push(_.clearcoatMapUv),x.push(_.clearcoatNormalMapUv),x.push(_.clearcoatRoughnessMapUv),x.push(_.iridescenceMapUv),x.push(_.iridescenceThicknessMapUv),x.push(_.sheenColorMapUv),x.push(_.sheenRoughnessMapUv),x.push(_.specularMapUv),x.push(_.specularColorMapUv),x.push(_.specularIntensityMapUv),x.push(_.transmissionMapUv),x.push(_.thicknessMapUv),x.push(_.combine),x.push(_.fogExp2),x.push(_.sizeAttenuation),x.push(_.morphTargetsCount),x.push(_.morphAttributeCount),x.push(_.numDirLights),x.push(_.numPointLights),x.push(_.numSpotLights),x.push(_.numSpotLightMaps),x.push(_.numHemiLights),x.push(_.numRectAreaLights),x.push(_.numDirLightShadows),x.push(_.numPointLightShadows),x.push(_.numSpotLightShadows),x.push(_.numSpotLightShadowsWithMaps),x.push(_.numLightProbes),x.push(_.shadowMapType),x.push(_.toneMapping),x.push(_.numClippingPlanes),x.push(_.numClipIntersection),x.push(_.depthPacking)}function E(x,_){a.disableAll(),_.supportsVertexTextures&&a.enable(0),_.instancing&&a.enable(1),_.instancingColor&&a.enable(2),_.instancingMorph&&a.enable(3),_.matcap&&a.enable(4),_.envMap&&a.enable(5),_.normalMapObjectSpace&&a.enable(6),_.normalMapTangentSpace&&a.enable(7),_.clearcoat&&a.enable(8),_.iridescence&&a.enable(9),_.alphaTest&&a.enable(10),_.vertexColors&&a.enable(11),_.vertexAlphas&&a.enable(12),_.vertexUv1s&&a.enable(13),_.vertexUv2s&&a.enable(14),_.vertexUv3s&&a.enable(15),_.vertexTangents&&a.enable(16),_.anisotropy&&a.enable(17),_.alphaHash&&a.enable(18),_.batching&&a.enable(19),_.dispersion&&a.enable(20),_.batchingColor&&a.enable(21),x.push(a.mask),a.disableAll(),_.fog&&a.enable(0),_.useFog&&a.enable(1),_.flatShading&&a.enable(2),_.logarithmicDepthBuffer&&a.enable(3),_.reverseDepthBuffer&&a.enable(4),_.skinning&&a.enable(5),_.morphTargets&&a.enable(6),_.morphNormals&&a.enable(7),_.morphColors&&a.enable(8),_.premultipliedAlpha&&a.enable(9),_.shadowMapEnabled&&a.enable(10),_.doubleSided&&a.enable(11),_.flipSided&&a.enable(12),_.useDepthPacking&&a.enable(13),_.dithering&&a.enable(14),_.transmission&&a.enable(15),_.sheen&&a.enable(16),_.opaque&&a.enable(17),_.pointsUvs&&a.enable(18),_.decodeVideoTexture&&a.enable(19),_.decodeVideoTextureEmissive&&a.enable(20),_.alphaToCoverage&&a.enable(21),x.push(a.mask)}function M(x){let _=g[x.type],R;if(_){let I=Fn[_];R=Dd.clone(I.uniforms)}else R=x.uniforms;return R}function U(x,_){let R;for(let I=0,L=h.length;I<L;I++){let N=h[I];if(N.cacheKey===_){R=N,++R.usedTimes;break}}return R===void 0&&(R=new Vm(i,_,x,r),h.push(R)),R}function C(x){if(--x.usedTimes===0){let _=h.indexOf(x);h[_]=h[h.length-1],h.pop(),x.destroy()}}function v(x){l.remove(x)}function b(){l.dispose()}return{getParameters:m,getProgramCacheKey:p,getUniforms:M,acquireProgram:U,releaseProgram:C,releaseShaderCache:v,programs:h,dispose:b}}function Xm(){let i=new WeakMap;function t(o){return i.has(o)}function e(o){let a=i.get(o);return a===void 0&&(a={},i.set(o,a)),a}function n(o){i.delete(o)}function s(o,a,l){i.get(o)[a]=l}function r(){i=new WeakMap}return{has:t,get:e,remove:n,update:s,dispose:r}}function qm(i,t){return i.groupOrder!==t.groupOrder?i.groupOrder-t.groupOrder:i.renderOrder!==t.renderOrder?i.renderOrder-t.renderOrder:i.material.id!==t.material.id?i.material.id-t.material.id:i.z!==t.z?i.z-t.z:i.id-t.id}function mh(i,t){return i.groupOrder!==t.groupOrder?i.groupOrder-t.groupOrder:i.renderOrder!==t.renderOrder?i.renderOrder-t.renderOrder:i.z!==t.z?t.z-i.z:i.id-t.id}function gh(){let i=[],t=0,e=[],n=[],s=[];function r(){t=0,e.length=0,n.length=0,s.length=0}function o(d,u,f,g,y,m){let p=i[t];return p===void 0?(p={id:d.id,object:d,geometry:u,material:f,groupOrder:g,renderOrder:d.renderOrder,z:y,group:m},i[t]=p):(p.id=d.id,p.object=d,p.geometry=u,p.material=f,p.groupOrder=g,p.renderOrder=d.renderOrder,p.z=y,p.group=m),t++,p}function a(d,u,f,g,y,m){let p=o(d,u,f,g,y,m);f.transmission>0?n.push(p):f.transparent===!0?s.push(p):e.push(p)}function l(d,u,f,g,y,m){let p=o(d,u,f,g,y,m);f.transmission>0?n.unshift(p):f.transparent===!0?s.unshift(p):e.unshift(p)}function c(d,u){e.length>1&&e.sort(d||qm),n.length>1&&n.sort(u||mh),s.length>1&&s.sort(u||mh)}function h(){for(let d=t,u=i.length;d<u;d++){let f=i[d];if(f.id===null)break;f.id=null,f.object=null,f.geometry=null,f.material=null,f.group=null}}return{opaque:e,transmissive:n,transparent:s,init:r,push:a,unshift:l,finish:h,sort:c}}function Ym(){let i=new WeakMap;function t(n,s){let r=i.get(n),o;return r===void 0?(o=new gh,i.set(n,[o])):s>=r.length?(o=new gh,r.push(o)):o=r[s],o}function e(){i=new WeakMap}return{get:t,dispose:e}}function $m(){let i={};return{get:function(t){if(i[t.id]!==void 0)return i[t.id];let e;switch(t.type){case"DirectionalLight":e={direction:new P,color:new Ct};break;case"SpotLight":e={position:new P,direction:new P,color:new Ct,distance:0,coneCos:0,penumbraCos:0,decay:0};break;case"PointLight":e={position:new P,color:new Ct,distance:0,decay:0};break;case"HemisphereLight":e={direction:new P,skyColor:new Ct,groundColor:new Ct};break;case"RectAreaLight":e={color:new Ct,position:new P,halfWidth:new P,halfHeight:new P};break}return i[t.id]=e,e}}}function Zm(){let i={};return{get:function(t){if(i[t.id]!==void 0)return i[t.id];let e;switch(t.type){case"DirectionalLight":e={shadowIntensity:1,shadowBias:0,shadowNormalBias:0,shadowRadius:1,shadowMapSize:new gt};break;case"SpotLight":e={shadowIntensity:1,shadowBias:0,shadowNormalBias:0,shadowRadius:1,shadowMapSize:new gt};break;case"PointLight":e={shadowIntensity:1,shadowBias:0,shadowNormalBias:0,shadowRadius:1,shadowMapSize:new gt,shadowCameraNear:1,shadowCameraFar:1e3};break}return i[t.id]=e,e}}}var Jm=0;function Km(i,t){return(t.castShadow?2:0)-(i.castShadow?2:0)+(t.map?1:0)-(i.map?1:0)}function jm(i){let t=new $m,e=Zm(),n={version:0,hash:{directionalLength:-1,pointLength:-1,spotLength:-1,rectAreaLength:-1,hemiLength:-1,numDirectionalShadows:-1,numPointShadows:-1,numSpotShadows:-1,numSpotMaps:-1,numLightProbes:-1},ambient:[0,0,0],probe:[],directional:[],directionalShadow:[],directionalShadowMap:[],directionalShadowMatrix:[],spot:[],spotLightMap:[],spotShadow:[],spotShadowMap:[],spotLightMatrix:[],rectArea:[],rectAreaLTC1:null,rectAreaLTC2:null,point:[],pointShadow:[],pointShadowMap:[],pointShadowMatrix:[],hemi:[],numSpotLightShadowsWithMaps:0,numLightProbes:0};for(let c=0;c<9;c++)n.probe.push(new P);let s=new P,r=new ne,o=new ne;function a(c){let h=0,d=0,u=0;for(let x=0;x<9;x++)n.probe[x].set(0,0,0);let f=0,g=0,y=0,m=0,p=0,w=0,E=0,M=0,U=0,C=0,v=0;c.sort(Km);for(let x=0,_=c.length;x<_;x++){let R=c[x],I=R.color,L=R.intensity,N=R.distance,V=R.shadow&&R.shadow.map?R.shadow.map.texture:null;if(R.isAmbientLight)h+=I.r*L,d+=I.g*L,u+=I.b*L;else if(R.isLightProbe){for(let F=0;F<9;F++)n.probe[F].addScaledVector(R.sh.coefficients[F],L);v++}else if(R.isDirectionalLight){let F=t.get(R);if(F.color.copy(R.color).multiplyScalar(R.intensity),R.castShadow){let X=R.shadow,z=e.get(R);z.shadowIntensity=X.intensity,z.shadowBias=X.bias,z.shadowNormalBias=X.normalBias,z.shadowRadius=X.radius,z.shadowMapSize=X.mapSize,n.directionalShadow[f]=z,n.directionalShadowMap[f]=V,n.directionalShadowMatrix[f]=R.shadow.matrix,w++}n.directional[f]=F,f++}else if(R.isSpotLight){let F=t.get(R);F.position.setFromMatrixPosition(R.matrixWorld),F.color.copy(I).multiplyScalar(L),F.distance=N,F.coneCos=Math.cos(R.angle),F.penumbraCos=Math.cos(R.angle*(1-R.penumbra)),F.decay=R.decay,n.spot[y]=F;let X=R.shadow;if(R.map&&(n.spotLightMap[U]=R.map,U++,X.updateMatrices(R),R.castShadow&&C++),n.spotLightMatrix[y]=X.matrix,R.castShadow){let z=e.get(R);z.shadowIntensity=X.intensity,z.shadowBias=X.bias,z.shadowNormalBias=X.normalBias,z.shadowRadius=X.radius,z.shadowMapSize=X.mapSize,n.spotShadow[y]=z,n.spotShadowMap[y]=V,M++}y++}else if(R.isRectAreaLight){let F=t.get(R);F.color.copy(I).multiplyScalar(L),F.halfWidth.set(R.width*.5,0,0),F.halfHeight.set(0,R.height*.5,0),n.rectArea[m]=F,m++}else if(R.isPointLight){let F=t.get(R);if(F.color.copy(R.color).multiplyScalar(R.intensity),F.distance=R.distance,F.decay=R.decay,R.castShadow){let X=R.shadow,z=e.get(R);z.shadowIntensity=X.intensity,z.shadowBias=X.bias,z.shadowNormalBias=X.normalBias,z.shadowRadius=X.radius,z.shadowMapSize=X.mapSize,z.shadowCameraNear=X.camera.near,z.shadowCameraFar=X.camera.far,n.pointShadow[g]=z,n.pointShadowMap[g]=V,n.pointShadowMatrix[g]=R.shadow.matrix,E++}n.point[g]=F,g++}else if(R.isHemisphereLight){let F=t.get(R);F.skyColor.copy(R.color).multiplyScalar(L),F.groundColor.copy(R.groundColor).multiplyScalar(L),n.hemi[p]=F,p++}}m>0&&(i.has("OES_texture_float_linear")===!0?(n.rectAreaLTC1=ht.LTC_FLOAT_1,n.rectAreaLTC2=ht.LTC_FLOAT_2):(n.rectAreaLTC1=ht.LTC_HALF_1,n.rectAreaLTC2=ht.LTC_HALF_2)),n.ambient[0]=h,n.ambient[1]=d,n.ambient[2]=u;let b=n.hash;(b.directionalLength!==f||b.pointLength!==g||b.spotLength!==y||b.rectAreaLength!==m||b.hemiLength!==p||b.numDirectionalShadows!==w||b.numPointShadows!==E||b.numSpotShadows!==M||b.numSpotMaps!==U||b.numLightProbes!==v)&&(n.directional.length=f,n.spot.length=y,n.rectArea.length=m,n.point.length=g,n.hemi.length=p,n.directionalShadow.length=w,n.directionalShadowMap.length=w,n.pointShadow.length=E,n.pointShadowMap.length=E,n.spotShadow.length=M,n.spotShadowMap.length=M,n.directionalShadowMatrix.length=w,n.pointShadowMatrix.length=E,n.spotLightMatrix.length=M+U-C,n.spotLightMap.length=U,n.numSpotLightShadowsWithMaps=C,n.numLightProbes=v,b.directionalLength=f,b.pointLength=g,b.spotLength=y,b.rectAreaLength=m,b.hemiLength=p,b.numDirectionalShadows=w,b.numPointShadows=E,b.numSpotShadows=M,b.numSpotMaps=U,b.numLightProbes=v,n.version=Jm++)}function l(c,h){let d=0,u=0,f=0,g=0,y=0,m=h.matrixWorldInverse;for(let p=0,w=c.length;p<w;p++){let E=c[p];if(E.isDirectionalLight){let M=n.directional[d];M.direction.setFromMatrixPosition(E.matrixWorld),s.setFromMatrixPosition(E.target.matrixWorld),M.direction.sub(s),M.direction.transformDirection(m),d++}else if(E.isSpotLight){let M=n.spot[f];M.position.setFromMatrixPosition(E.matrixWorld),M.position.applyMatrix4(m),M.direction.setFromMatrixPosition(E.matrixWorld),s.setFromMatrixPosition(E.target.matrixWorld),M.direction.sub(s),M.direction.transformDirection(m),f++}else if(E.isRectAreaLight){let M=n.rectArea[g];M.position.setFromMatrixPosition(E.matrixWorld),M.position.applyMatrix4(m),o.identity(),r.copy(E.matrixWorld),r.premultiply(m),o.extractRotation(r),M.halfWidth.set(E.width*.5,0,0),M.halfHeight.set(0,E.height*.5,0),M.halfWidth.applyMatrix4(o),M.halfHeight.applyMatrix4(o),g++}else if(E.isPointLight){let M=n.point[u];M.position.setFromMatrixPosition(E.matrixWorld),M.position.applyMatrix4(m),u++}else if(E.isHemisphereLight){let M=n.hemi[y];M.direction.setFromMatrixPosition(E.matrixWorld),M.direction.transformDirection(m),y++}}}return{setup:a,setupView:l,state:n}}function vh(i){let t=new jm(i),e=[],n=[];function s(h){c.camera=h,e.length=0,n.length=0}function r(h){e.push(h)}function o(h){n.push(h)}function a(){t.setup(e)}function l(h){t.setupView(e,h)}let c={lightsArray:e,shadowsArray:n,camera:null,lights:t,transmissionRenderTarget:{}};return{init:s,state:c,setupLights:a,setupLightsView:l,pushLight:r,pushShadow:o}}function Qm(i){let t=new WeakMap;function e(s,r=0){let o=t.get(s),a;return o===void 0?(a=new vh(i),t.set(s,[a])):r>=o.length?(a=new vh(i),o.push(a)):a=o[r],a}function n(){t=new WeakMap}return{get:e,dispose:n}}var gl=class extends Mi{static get type(){return"MeshDepthMaterial"}constructor(t){super(),this.isMeshDepthMaterial=!0,this.depthPacking=rd,this.map=null,this.alphaMap=null,this.displacementMap=null,this.displacementScale=1,this.displacementBias=0,this.wireframe=!1,this.wireframeLinewidth=1,this.setValues(t)}copy(t){return super.copy(t),this.depthPacking=t.depthPacking,this.map=t.map,this.alphaMap=t.alphaMap,this.displacementMap=t.displacementMap,this.displacementScale=t.displacementScale,this.displacementBias=t.displacementBias,this.wireframe=t.wireframe,this.wireframeLinewidth=t.wireframeLinewidth,this}},vl=class extends Mi{static get type(){return"MeshDistanceMaterial"}constructor(t){super(),this.isMeshDistanceMaterial=!0,this.map=null,this.alphaMap=null,this.displacementMap=null,this.displacementScale=1,this.displacementBias=0,this.setValues(t)}copy(t){return super.copy(t),this.map=t.map,this.alphaMap=t.alphaMap,this.displacementMap=t.displacementMap,this.displacementScale=t.displacementScale,this.displacementBias=t.displacementBias,this}},tg=`void main() {
	gl_Position = vec4( position, 1.0 );
}`,eg=`uniform sampler2D shadow_pass;
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
}`;function ng(i,t,e){let n=new ir,s=new gt,r=new gt,o=new re,a=new gl({depthPacking:od}),l=new vl,c={},h=e.maxTextureSize,d={[xi]:Re,[Re]:xi,[de]:de},u=new sn({defines:{VSM_SAMPLES:8},uniforms:{shadow_pass:{value:null},resolution:{value:new gt},radius:{value:4}},vertexShader:tg,fragmentShader:eg}),f=u.clone();f.defines.HORIZONTAL_PASS=1;let g=new Yt;g.setAttribute("position",new Ut(new Float32Array([-1,-1,.5,3,-1,.5,-1,3,.5]),3));let y=new at(g,u),m=this;this.enabled=!1,this.autoUpdate=!0,this.needsUpdate=!1,this.type=Ah;let p=this.type;this.render=function(C,v,b){if(m.enabled===!1||m.autoUpdate===!1&&m.needsUpdate===!1||C.length===0)return;let x=i.getRenderTarget(),_=i.getActiveCubeFace(),R=i.getActiveMipmapLevel(),I=i.state;I.setBlending(gi),I.buffers.color.setClear(1,1,1,1),I.buffers.depth.setTest(!0),I.setScissorTest(!1);let L=p!==ti&&this.type===ti,N=p===ti&&this.type!==ti;for(let V=0,F=C.length;V<F;V++){let X=C[V],z=X.shadow;if(z===void 0){console.warn("THREE.WebGLShadowMap:",X,"has no shadow.");continue}if(z.autoUpdate===!1&&z.needsUpdate===!1)continue;s.copy(z.mapSize);let j=z.getFrameExtents();if(s.multiply(j),r.copy(z.mapSize),(s.x>h||s.y>h)&&(s.x>h&&(r.x=Math.floor(h/j.x),s.x=r.x*j.x,z.mapSize.x=r.x),s.y>h&&(r.y=Math.floor(h/j.y),s.y=r.y*j.y,z.mapSize.y=r.y)),z.map===null||L===!0||N===!0){let st=this.type!==ti?{minFilter:un,magFilter:un}:{};z.map!==null&&z.map.dispose(),z.map=new oi(s.x,s.y,st),z.map.texture.name=X.name+".shadowMap",z.camera.updateProjectionMatrix()}i.setRenderTarget(z.map),i.clear();let it=z.getViewportCount();for(let st=0;st<it;st++){let yt=z.getViewport(st);o.set(r.x*yt.x,r.y*yt.y,r.x*yt.z,r.y*yt.w),I.viewport(o),z.updateMatrices(X,st),n=z.getFrustum(),M(v,b,z.camera,X,this.type)}z.isPointLightShadow!==!0&&this.type===ti&&w(z,b),z.needsUpdate=!1}p=this.type,m.needsUpdate=!1,i.setRenderTarget(x,_,R)};function w(C,v){let b=t.update(y);u.defines.VSM_SAMPLES!==C.blurSamples&&(u.defines.VSM_SAMPLES=C.blurSamples,f.defines.VSM_SAMPLES=C.blurSamples,u.needsUpdate=!0,f.needsUpdate=!0),C.mapPass===null&&(C.mapPass=new oi(s.x,s.y)),u.uniforms.shadow_pass.value=C.map.texture,u.uniforms.resolution.value=C.mapSize,u.uniforms.radius.value=C.radius,i.setRenderTarget(C.mapPass),i.clear(),i.renderBufferDirect(v,null,b,u,y,null),f.uniforms.shadow_pass.value=C.mapPass.texture,f.uniforms.resolution.value=C.mapSize,f.uniforms.radius.value=C.radius,i.setRenderTarget(C.map),i.clear(),i.renderBufferDirect(v,null,b,f,y,null)}function E(C,v,b,x){let _=null,R=b.isPointLight===!0?C.customDistanceMaterial:C.customDepthMaterial;if(R!==void 0)_=R;else if(_=b.isPointLight===!0?l:a,i.localClippingEnabled&&v.clipShadows===!0&&Array.isArray(v.clippingPlanes)&&v.clippingPlanes.length!==0||v.displacementMap&&v.displacementScale!==0||v.alphaMap&&v.alphaTest>0||v.map&&v.alphaTest>0){let I=_.uuid,L=v.uuid,N=c[I];N===void 0&&(N={},c[I]=N);let V=N[L];V===void 0&&(V=_.clone(),N[L]=V,v.addEventListener("dispose",U)),_=V}if(_.visible=v.visible,_.wireframe=v.wireframe,x===ti?_.side=v.shadowSide!==null?v.shadowSide:v.side:_.side=v.shadowSide!==null?v.shadowSide:d[v.side],_.alphaMap=v.alphaMap,_.alphaTest=v.alphaTest,_.map=v.map,_.clipShadows=v.clipShadows,_.clippingPlanes=v.clippingPlanes,_.clipIntersection=v.clipIntersection,_.displacementMap=v.displacementMap,_.displacementScale=v.displacementScale,_.displacementBias=v.displacementBias,_.wireframeLinewidth=v.wireframeLinewidth,_.linewidth=v.linewidth,b.isPointLight===!0&&_.isMeshDistanceMaterial===!0){let I=i.properties.get(_);I.light=b}return _}function M(C,v,b,x,_){if(C.visible===!1)return;if(C.layers.test(v.layers)&&(C.isMesh||C.isLine||C.isPoints)&&(C.castShadow||C.receiveShadow&&_===ti)&&(!C.frustumCulled||n.intersectsObject(C))){C.modelViewMatrix.multiplyMatrices(b.matrixWorldInverse,C.matrixWorld);let L=t.update(C),N=C.material;if(Array.isArray(N)){let V=L.groups;for(let F=0,X=V.length;F<X;F++){let z=V[F],j=N[z.materialIndex];if(j&&j.visible){let it=E(C,j,x,_);C.onBeforeShadow(i,C,v,b,L,it,z),i.renderBufferDirect(b,null,L,it,C,z),C.onAfterShadow(i,C,v,b,L,it,z)}}}else if(N.visible){let V=E(C,N,x,_);C.onBeforeShadow(i,C,v,b,L,V,null),i.renderBufferDirect(b,null,L,V,C,null),C.onAfterShadow(i,C,v,b,L,V,null)}}let I=C.children;for(let L=0,N=I.length;L<N;L++)M(I[L],v,b,x,_)}function U(C){C.target.removeEventListener("dispose",U);for(let b in c){let x=c[b],_=C.target.uuid;_ in x&&(x[_].dispose(),delete x[_])}}}var ig={[Ma]:Sa,[ba]:Ta,[Ea]:Aa,[gs]:wa,[Sa]:Ma,[Ta]:ba,[Aa]:Ea,[wa]:gs};function sg(i,t){function e(){let B=!1,ut=new re,J=null,K=new re(0,0,0,0);return{setMask:function(vt){J!==vt&&!B&&(i.colorMask(vt,vt,vt,vt),J=vt)},setLocked:function(vt){B=vt},setClear:function(vt,pt,Gt,ye,Ge){Ge===!0&&(vt*=ye,pt*=ye,Gt*=ye),ut.set(vt,pt,Gt,ye),K.equals(ut)===!1&&(i.clearColor(vt,pt,Gt,ye),K.copy(ut))},reset:function(){B=!1,J=null,K.set(-1,0,0,0)}}}function n(){let B=!1,ut=!1,J=null,K=null,vt=null;return{setReversed:function(pt){if(ut!==pt){let Gt=t.get("EXT_clip_control");ut?Gt.clipControlEXT(Gt.LOWER_LEFT_EXT,Gt.ZERO_TO_ONE_EXT):Gt.clipControlEXT(Gt.LOWER_LEFT_EXT,Gt.NEGATIVE_ONE_TO_ONE_EXT);let ye=vt;vt=null,this.setClear(ye)}ut=pt},getReversed:function(){return ut},setTest:function(pt){pt?ct(i.DEPTH_TEST):bt(i.DEPTH_TEST)},setMask:function(pt){J!==pt&&!B&&(i.depthMask(pt),J=pt)},setFunc:function(pt){if(ut&&(pt=ig[pt]),K!==pt){switch(pt){case Ma:i.depthFunc(i.NEVER);break;case Sa:i.depthFunc(i.ALWAYS);break;case ba:i.depthFunc(i.LESS);break;case gs:i.depthFunc(i.LEQUAL);break;case Ea:i.depthFunc(i.EQUAL);break;case wa:i.depthFunc(i.GEQUAL);break;case Ta:i.depthFunc(i.GREATER);break;case Aa:i.depthFunc(i.NOTEQUAL);break;default:i.depthFunc(i.LEQUAL)}K=pt}},setLocked:function(pt){B=pt},setClear:function(pt){vt!==pt&&(ut&&(pt=1-pt),i.clearDepth(pt),vt=pt)},reset:function(){B=!1,J=null,K=null,vt=null,ut=!1}}}function s(){let B=!1,ut=null,J=null,K=null,vt=null,pt=null,Gt=null,ye=null,Ge=null;return{setTest:function(le){B||(le?ct(i.STENCIL_TEST):bt(i.STENCIL_TEST))},setMask:function(le){ut!==le&&!B&&(i.stencilMask(le),ut=le)},setFunc:function(le,bn,Yn){(J!==le||K!==bn||vt!==Yn)&&(i.stencilFunc(le,bn,Yn),J=le,K=bn,vt=Yn)},setOp:function(le,bn,Yn){(pt!==le||Gt!==bn||ye!==Yn)&&(i.stencilOp(le,bn,Yn),pt=le,Gt=bn,ye=Yn)},setLocked:function(le){B=le},setClear:function(le){Ge!==le&&(i.clearStencil(le),Ge=le)},reset:function(){B=!1,ut=null,J=null,K=null,vt=null,pt=null,Gt=null,ye=null,Ge=null}}}let r=new e,o=new n,a=new s,l=new WeakMap,c=new WeakMap,h={},d={},u=new WeakMap,f=[],g=null,y=!1,m=null,p=null,w=null,E=null,M=null,U=null,C=null,v=new Ct(0,0,0),b=0,x=!1,_=null,R=null,I=null,L=null,N=null,V=i.getParameter(i.MAX_COMBINED_TEXTURE_IMAGE_UNITS),F=!1,X=0,z=i.getParameter(i.VERSION);z.indexOf("WebGL")!==-1?(X=parseFloat(/^WebGL (\d)/.exec(z)[1]),F=X>=1):z.indexOf("OpenGL ES")!==-1&&(X=parseFloat(/^OpenGL ES (\d)/.exec(z)[1]),F=X>=2);let j=null,it={},st=i.getParameter(i.SCISSOR_BOX),yt=i.getParameter(i.VIEWPORT),Ft=new re().fromArray(st),$=new re().fromArray(yt);function nt(B,ut,J,K){let vt=new Uint8Array(4),pt=i.createTexture();i.bindTexture(B,pt),i.texParameteri(B,i.TEXTURE_MIN_FILTER,i.NEAREST),i.texParameteri(B,i.TEXTURE_MAG_FILTER,i.NEAREST);for(let Gt=0;Gt<J;Gt++)B===i.TEXTURE_3D||B===i.TEXTURE_2D_ARRAY?i.texImage3D(ut,0,i.RGBA,1,1,K,0,i.RGBA,i.UNSIGNED_BYTE,vt):i.texImage2D(ut+Gt,0,i.RGBA,1,1,0,i.RGBA,i.UNSIGNED_BYTE,vt);return pt}let ft={};ft[i.TEXTURE_2D]=nt(i.TEXTURE_2D,i.TEXTURE_2D,1),ft[i.TEXTURE_CUBE_MAP]=nt(i.TEXTURE_CUBE_MAP,i.TEXTURE_CUBE_MAP_POSITIVE_X,6),ft[i.TEXTURE_2D_ARRAY]=nt(i.TEXTURE_2D_ARRAY,i.TEXTURE_2D_ARRAY,1,1),ft[i.TEXTURE_3D]=nt(i.TEXTURE_3D,i.TEXTURE_3D,1,1),r.setClear(0,0,0,1),o.setClear(1),a.setClear(0),ct(i.DEPTH_TEST),o.setFunc(gs),Xt(!1),kt(yc),ct(i.CULL_FACE),O(gi);function ct(B){h[B]!==!0&&(i.enable(B),h[B]=!0)}function bt(B){h[B]!==!1&&(i.disable(B),h[B]=!1)}function Lt(B,ut){return d[B]!==ut?(i.bindFramebuffer(B,ut),d[B]=ut,B===i.DRAW_FRAMEBUFFER&&(d[i.FRAMEBUFFER]=ut),B===i.FRAMEBUFFER&&(d[i.DRAW_FRAMEBUFFER]=ut),!0):!1}function Et(B,ut){let J=f,K=!1;if(B){J=u.get(ut),J===void 0&&(J=[],u.set(ut,J));let vt=B.textures;if(J.length!==vt.length||J[0]!==i.COLOR_ATTACHMENT0){for(let pt=0,Gt=vt.length;pt<Gt;pt++)J[pt]=i.COLOR_ATTACHMENT0+pt;J.length=vt.length,K=!0}}else J[0]!==i.BACK&&(J[0]=i.BACK,K=!0);K&&i.drawBuffers(J)}function Kt(B){return g!==B?(i.useProgram(B),g=B,!0):!1}let Ht={[Ni]:i.FUNC_ADD,[Du]:i.FUNC_SUBTRACT,[Uu]:i.FUNC_REVERSE_SUBTRACT};Ht[Nu]=i.MIN,Ht[Fu]=i.MAX;let ue={[Ou]:i.ZERO,[Bu]:i.ONE,[zu]:i.SRC_COLOR,[_a]:i.SRC_ALPHA,[Xu]:i.SRC_ALPHA_SATURATE,[Gu]:i.DST_COLOR,[ku]:i.DST_ALPHA,[Hu]:i.ONE_MINUS_SRC_COLOR,[ya]:i.ONE_MINUS_SRC_ALPHA,[Wu]:i.ONE_MINUS_DST_COLOR,[Vu]:i.ONE_MINUS_DST_ALPHA,[qu]:i.CONSTANT_COLOR,[Yu]:i.ONE_MINUS_CONSTANT_COLOR,[$u]:i.CONSTANT_ALPHA,[Zu]:i.ONE_MINUS_CONSTANT_ALPHA};function O(B,ut,J,K,vt,pt,Gt,ye,Ge,le){if(B===gi){y===!0&&(bt(i.BLEND),y=!1);return}if(y===!1&&(ct(i.BLEND),y=!0),B!==Lu){if(B!==m||le!==x){if((p!==Ni||M!==Ni)&&(i.blendEquation(i.FUNC_ADD),p=Ni,M=Ni),le)switch(B){case ds:i.blendFuncSeparate(i.ONE,i.ONE_MINUS_SRC_ALPHA,i.ONE,i.ONE_MINUS_SRC_ALPHA);break;case Jr:i.blendFunc(i.ONE,i.ONE);break;case Mc:i.blendFuncSeparate(i.ZERO,i.ONE_MINUS_SRC_COLOR,i.ZERO,i.ONE);break;case Sc:i.blendFuncSeparate(i.ZERO,i.SRC_COLOR,i.ZERO,i.SRC_ALPHA);break;default:console.error("THREE.WebGLState: Invalid blending: ",B);break}else switch(B){case ds:i.blendFuncSeparate(i.SRC_ALPHA,i.ONE_MINUS_SRC_ALPHA,i.ONE,i.ONE_MINUS_SRC_ALPHA);break;case Jr:i.blendFunc(i.SRC_ALPHA,i.ONE);break;case Mc:i.blendFuncSeparate(i.ZERO,i.ONE_MINUS_SRC_COLOR,i.ZERO,i.ONE);break;case Sc:i.blendFunc(i.ZERO,i.SRC_COLOR);break;default:console.error("THREE.WebGLState: Invalid blending: ",B);break}w=null,E=null,U=null,C=null,v.set(0,0,0),b=0,m=B,x=le}return}vt=vt||ut,pt=pt||J,Gt=Gt||K,(ut!==p||vt!==M)&&(i.blendEquationSeparate(Ht[ut],Ht[vt]),p=ut,M=vt),(J!==w||K!==E||pt!==U||Gt!==C)&&(i.blendFuncSeparate(ue[J],ue[K],ue[pt],ue[Gt]),w=J,E=K,U=pt,C=Gt),(ye.equals(v)===!1||Ge!==b)&&(i.blendColor(ye.r,ye.g,ye.b,Ge),v.copy(ye),b=Ge),m=B,x=!1}function be(B,ut){B.side===de?bt(i.CULL_FACE):ct(i.CULL_FACE);let J=B.side===Re;ut&&(J=!J),Xt(J),B.blending===ds&&B.transparent===!1?O(gi):O(B.blending,B.blendEquation,B.blendSrc,B.blendDst,B.blendEquationAlpha,B.blendSrcAlpha,B.blendDstAlpha,B.blendColor,B.blendAlpha,B.premultipliedAlpha),o.setFunc(B.depthFunc),o.setTest(B.depthTest),o.setMask(B.depthWrite),r.setMask(B.colorWrite);let K=B.stencilWrite;a.setTest(K),K&&(a.setMask(B.stencilWriteMask),a.setFunc(B.stencilFunc,B.stencilRef,B.stencilFuncMask),a.setOp(B.stencilFail,B.stencilZFail,B.stencilZPass)),te(B.polygonOffset,B.polygonOffsetFactor,B.polygonOffsetUnits),B.alphaToCoverage===!0?ct(i.SAMPLE_ALPHA_TO_COVERAGE):bt(i.SAMPLE_ALPHA_TO_COVERAGE)}function Xt(B){_!==B&&(B?i.frontFace(i.CW):i.frontFace(i.CCW),_=B)}function kt(B){B!==Cu?(ct(i.CULL_FACE),B!==R&&(B===yc?i.cullFace(i.BACK):B===Pu?i.cullFace(i.FRONT):i.cullFace(i.FRONT_AND_BACK))):bt(i.CULL_FACE),R=B}function It(B){B!==I&&(F&&i.lineWidth(B),I=B)}function te(B,ut,J){B?(ct(i.POLYGON_OFFSET_FILL),(L!==ut||N!==J)&&(i.polygonOffset(ut,J),L=ut,N=J)):bt(i.POLYGON_OFFSET_FILL)}function Rt(B){B?ct(i.SCISSOR_TEST):bt(i.SCISSOR_TEST)}function A(B){B===void 0&&(B=i.TEXTURE0+V-1),j!==B&&(i.activeTexture(B),j=B)}function S(B,ut,J){J===void 0&&(j===null?J=i.TEXTURE0+V-1:J=j);let K=it[J];K===void 0&&(K={type:void 0,texture:void 0},it[J]=K),(K.type!==B||K.texture!==ut)&&(j!==J&&(i.activeTexture(J),j=J),i.bindTexture(B,ut||ft[B]),K.type=B,K.texture=ut)}function D(){let B=it[j];B!==void 0&&B.type!==void 0&&(i.bindTexture(B.type,null),B.type=void 0,B.texture=void 0)}function G(){try{i.compressedTexImage2D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function Z(){try{i.compressedTexImage3D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function W(){try{i.texSubImage2D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function et(){try{i.texSubImage3D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function Q(){try{i.compressedTexSubImage2D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function rt(){try{i.compressedTexSubImage3D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function Nt(){try{i.texStorage2D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function tt(){try{i.texStorage3D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function lt(){try{i.texImage2D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function xt(){try{i.texImage3D.apply(i,arguments)}catch(B){console.error("THREE.WebGLState:",B)}}function Mt(B){Ft.equals(B)===!1&&(i.scissor(B.x,B.y,B.z,B.w),Ft.copy(B))}function _t(B){$.equals(B)===!1&&(i.viewport(B.x,B.y,B.z,B.w),$.copy(B))}function $t(B,ut){let J=c.get(ut);J===void 0&&(J=new WeakMap,c.set(ut,J));let K=J.get(B);K===void 0&&(K=i.getUniformBlockIndex(ut,B.name),J.set(B,K))}function Vt(B,ut){let K=c.get(ut).get(B);l.get(ut)!==K&&(i.uniformBlockBinding(ut,K,B.__bindingPointIndex),l.set(ut,K))}function fe(){i.disable(i.BLEND),i.disable(i.CULL_FACE),i.disable(i.DEPTH_TEST),i.disable(i.POLYGON_OFFSET_FILL),i.disable(i.SCISSOR_TEST),i.disable(i.STENCIL_TEST),i.disable(i.SAMPLE_ALPHA_TO_COVERAGE),i.blendEquation(i.FUNC_ADD),i.blendFunc(i.ONE,i.ZERO),i.blendFuncSeparate(i.ONE,i.ZERO,i.ONE,i.ZERO),i.blendColor(0,0,0,0),i.colorMask(!0,!0,!0,!0),i.clearColor(0,0,0,0),i.depthMask(!0),i.depthFunc(i.LESS),o.setReversed(!1),i.clearDepth(1),i.stencilMask(4294967295),i.stencilFunc(i.ALWAYS,0,4294967295),i.stencilOp(i.KEEP,i.KEEP,i.KEEP),i.clearStencil(0),i.cullFace(i.BACK),i.frontFace(i.CCW),i.polygonOffset(0,0),i.activeTexture(i.TEXTURE0),i.bindFramebuffer(i.FRAMEBUFFER,null),i.bindFramebuffer(i.DRAW_FRAMEBUFFER,null),i.bindFramebuffer(i.READ_FRAMEBUFFER,null),i.useProgram(null),i.lineWidth(1),i.scissor(0,0,i.canvas.width,i.canvas.height),i.viewport(0,0,i.canvas.width,i.canvas.height),h={},j=null,it={},d={},u=new WeakMap,f=[],g=null,y=!1,m=null,p=null,w=null,E=null,M=null,U=null,C=null,v=new Ct(0,0,0),b=0,x=!1,_=null,R=null,I=null,L=null,N=null,Ft.set(0,0,i.canvas.width,i.canvas.height),$.set(0,0,i.canvas.width,i.canvas.height),r.reset(),o.reset(),a.reset()}return{buffers:{color:r,depth:o,stencil:a},enable:ct,disable:bt,bindFramebuffer:Lt,drawBuffers:Et,useProgram:Kt,setBlending:O,setMaterial:be,setFlipSided:Xt,setCullFace:kt,setLineWidth:It,setPolygonOffset:te,setScissorTest:Rt,activeTexture:A,bindTexture:S,unbindTexture:D,compressedTexImage2D:G,compressedTexImage3D:Z,texImage2D:lt,texImage3D:xt,updateUBOMapping:$t,uniformBlockBinding:Vt,texStorage2D:Nt,texStorage3D:tt,texSubImage2D:W,texSubImage3D:et,compressedTexSubImage2D:Q,compressedTexSubImage3D:rt,scissor:Mt,viewport:_t,reset:fe}}function xh(i,t,e,n){let s=rg(n);switch(e){case Dh:return i*t;case Nh:return i*t;case Fh:return i*t*2;case $l:return i*t/s.components*s.byteLength;case Zl:return i*t/s.components*s.byteLength;case Oh:return i*t*2/s.components*s.byteLength;case Jl:return i*t*2/s.components*s.byteLength;case Uh:return i*t*3/s.components*s.byteLength;case Cn:return i*t*4/s.components*s.byteLength;case Kl:return i*t*4/s.components*s.byteLength;case Xr:case qr:return Math.floor((i+3)/4)*Math.floor((t+3)/4)*8;case Yr:case $r:return Math.floor((i+3)/4)*Math.floor((t+3)/4)*16;case La:case Ua:return Math.max(i,16)*Math.max(t,8)/4;case Ia:case Da:return Math.max(i,8)*Math.max(t,8)/2;case Na:case Fa:return Math.floor((i+3)/4)*Math.floor((t+3)/4)*8;case Oa:return Math.floor((i+3)/4)*Math.floor((t+3)/4)*16;case Ba:return Math.floor((i+3)/4)*Math.floor((t+3)/4)*16;case za:return Math.floor((i+4)/5)*Math.floor((t+3)/4)*16;case Ha:return Math.floor((i+4)/5)*Math.floor((t+4)/5)*16;case ka:return Math.floor((i+5)/6)*Math.floor((t+4)/5)*16;case Va:return Math.floor((i+5)/6)*Math.floor((t+5)/6)*16;case Ga:return Math.floor((i+7)/8)*Math.floor((t+4)/5)*16;case Wa:return Math.floor((i+7)/8)*Math.floor((t+5)/6)*16;case Xa:return Math.floor((i+7)/8)*Math.floor((t+7)/8)*16;case qa:return Math.floor((i+9)/10)*Math.floor((t+4)/5)*16;case Ya:return Math.floor((i+9)/10)*Math.floor((t+5)/6)*16;case $a:return Math.floor((i+9)/10)*Math.floor((t+7)/8)*16;case Za:return Math.floor((i+9)/10)*Math.floor((t+9)/10)*16;case Ja:return Math.floor((i+11)/12)*Math.floor((t+9)/10)*16;case Ka:return Math.floor((i+11)/12)*Math.floor((t+11)/12)*16;case Zr:case ja:case Qa:return Math.ceil(i/4)*Math.ceil(t/4)*16;case Bh:case tl:return Math.ceil(i/4)*Math.ceil(t/4)*8;case el:case nl:return Math.ceil(i/4)*Math.ceil(t/4)*16}throw new Error(`Unable to determine texture byte length for ${e} format.`)}function rg(i){switch(i){case ri:case Ph:return{byteLength:1,components:1};case nr:case Ih:case ar:return{byteLength:2,components:1};case ql:case Yl:return{byteLength:2,components:4};case Bi:case Xl:case zn:return{byteLength:4,components:1};case Lh:return{byteLength:4,components:3}}throw new Error(`Unknown texture type ${i}.`)}function og(i,t,e,n,s,r,o){let a=t.has("WEBGL_multisampled_render_to_texture")?t.get("WEBGL_multisampled_render_to_texture"):null,l=typeof navigator=="undefined"?!1:/OculusBrowser/g.test(navigator.userAgent),c=new gt,h=new WeakMap,d,u=new WeakMap,f=!1;try{f=typeof OffscreenCanvas!="undefined"&&new OffscreenCanvas(1,1).getContext("2d")!==null}catch{}function g(A,S){return f?new OffscreenCanvas(A,S):Qr("canvas")}function y(A,S,D){let G=1,Z=Rt(A);if((Z.width>D||Z.height>D)&&(G=D/Math.max(Z.width,Z.height)),G<1)if(typeof HTMLImageElement!="undefined"&&A instanceof HTMLImageElement||typeof HTMLCanvasElement!="undefined"&&A instanceof HTMLCanvasElement||typeof ImageBitmap!="undefined"&&A instanceof ImageBitmap||typeof VideoFrame!="undefined"&&A instanceof VideoFrame){let W=Math.floor(G*Z.width),et=Math.floor(G*Z.height);d===void 0&&(d=g(W,et));let Q=S?g(W,et):d;return Q.width=W,Q.height=et,Q.getContext("2d").drawImage(A,0,0,W,et),console.warn("THREE.WebGLRenderer: Texture has been resized from ("+Z.width+"x"+Z.height+") to ("+W+"x"+et+")."),Q}else return"data"in A&&console.warn("THREE.WebGLRenderer: Image in DataTexture is too big ("+Z.width+"x"+Z.height+")."),A;return A}function m(A){return A.generateMipmaps}function p(A){i.generateMipmap(A)}function w(A){return A.isWebGLCubeRenderTarget?i.TEXTURE_CUBE_MAP:A.isWebGL3DRenderTarget?i.TEXTURE_3D:A.isWebGLArrayRenderTarget||A.isCompressedArrayTexture?i.TEXTURE_2D_ARRAY:i.TEXTURE_2D}function E(A,S,D,G,Z=!1){if(A!==null){if(i[A]!==void 0)return i[A];console.warn("THREE.WebGLRenderer: Attempt to use non-existing WebGL internal format '"+A+"'")}let W=S;if(S===i.RED&&(D===i.FLOAT&&(W=i.R32F),D===i.HALF_FLOAT&&(W=i.R16F),D===i.UNSIGNED_BYTE&&(W=i.R8)),S===i.RED_INTEGER&&(D===i.UNSIGNED_BYTE&&(W=i.R8UI),D===i.UNSIGNED_SHORT&&(W=i.R16UI),D===i.UNSIGNED_INT&&(W=i.R32UI),D===i.BYTE&&(W=i.R8I),D===i.SHORT&&(W=i.R16I),D===i.INT&&(W=i.R32I)),S===i.RG&&(D===i.FLOAT&&(W=i.RG32F),D===i.HALF_FLOAT&&(W=i.RG16F),D===i.UNSIGNED_BYTE&&(W=i.RG8)),S===i.RG_INTEGER&&(D===i.UNSIGNED_BYTE&&(W=i.RG8UI),D===i.UNSIGNED_SHORT&&(W=i.RG16UI),D===i.UNSIGNED_INT&&(W=i.RG32UI),D===i.BYTE&&(W=i.RG8I),D===i.SHORT&&(W=i.RG16I),D===i.INT&&(W=i.RG32I)),S===i.RGB_INTEGER&&(D===i.UNSIGNED_BYTE&&(W=i.RGB8UI),D===i.UNSIGNED_SHORT&&(W=i.RGB16UI),D===i.UNSIGNED_INT&&(W=i.RGB32UI),D===i.BYTE&&(W=i.RGB8I),D===i.SHORT&&(W=i.RGB16I),D===i.INT&&(W=i.RGB32I)),S===i.RGBA_INTEGER&&(D===i.UNSIGNED_BYTE&&(W=i.RGBA8UI),D===i.UNSIGNED_SHORT&&(W=i.RGBA16UI),D===i.UNSIGNED_INT&&(W=i.RGBA32UI),D===i.BYTE&&(W=i.RGBA8I),D===i.SHORT&&(W=i.RGBA16I),D===i.INT&&(W=i.RGBA32I)),S===i.RGB&&D===i.UNSIGNED_INT_5_9_9_9_REV&&(W=i.RGB9_E5),S===i.RGBA){let et=Z?Eo:jt.getTransfer(G);D===i.FLOAT&&(W=i.RGBA32F),D===i.HALF_FLOAT&&(W=i.RGBA16F),D===i.UNSIGNED_BYTE&&(W=et===ce?i.SRGB8_ALPHA8:i.RGBA8),D===i.UNSIGNED_SHORT_4_4_4_4&&(W=i.RGBA4),D===i.UNSIGNED_SHORT_5_5_5_1&&(W=i.RGB5_A1)}return(W===i.R16F||W===i.R32F||W===i.RG16F||W===i.RG32F||W===i.RGBA16F||W===i.RGBA32F)&&t.get("EXT_color_buffer_float"),W}function M(A,S){let D;return A?S===null||S===Bi||S===ys?D=i.DEPTH24_STENCIL8:S===zn?D=i.DEPTH32F_STENCIL8:S===nr&&(D=i.DEPTH24_STENCIL8,console.warn("DepthTexture: 16 bit depth attachment is not supported with stencil. Using 24-bit attachment.")):S===null||S===Bi||S===ys?D=i.DEPTH_COMPONENT24:S===zn?D=i.DEPTH_COMPONENT32F:S===nr&&(D=i.DEPTH_COMPONENT16),D}function U(A,S){return m(A)===!0||A.isFramebufferTexture&&A.minFilter!==un&&A.minFilter!==Bn?Math.log2(Math.max(S.width,S.height))+1:A.mipmaps!==void 0&&A.mipmaps.length>0?A.mipmaps.length:A.isCompressedTexture&&Array.isArray(A.image)?S.mipmaps.length:1}function C(A){let S=A.target;S.removeEventListener("dispose",C),b(S),S.isVideoTexture&&h.delete(S)}function v(A){let S=A.target;S.removeEventListener("dispose",v),_(S)}function b(A){let S=n.get(A);if(S.__webglInit===void 0)return;let D=A.source,G=u.get(D);if(G){let Z=G[S.__cacheKey];Z.usedTimes--,Z.usedTimes===0&&x(A),Object.keys(G).length===0&&u.delete(D)}n.remove(A)}function x(A){let S=n.get(A);i.deleteTexture(S.__webglTexture);let D=A.source,G=u.get(D);delete G[S.__cacheKey],o.memory.textures--}function _(A){let S=n.get(A);if(A.depthTexture&&(A.depthTexture.dispose(),n.remove(A.depthTexture)),A.isWebGLCubeRenderTarget)for(let G=0;G<6;G++){if(Array.isArray(S.__webglFramebuffer[G]))for(let Z=0;Z<S.__webglFramebuffer[G].length;Z++)i.deleteFramebuffer(S.__webglFramebuffer[G][Z]);else i.deleteFramebuffer(S.__webglFramebuffer[G]);S.__webglDepthbuffer&&i.deleteRenderbuffer(S.__webglDepthbuffer[G])}else{if(Array.isArray(S.__webglFramebuffer))for(let G=0;G<S.__webglFramebuffer.length;G++)i.deleteFramebuffer(S.__webglFramebuffer[G]);else i.deleteFramebuffer(S.__webglFramebuffer);if(S.__webglDepthbuffer&&i.deleteRenderbuffer(S.__webglDepthbuffer),S.__webglMultisampledFramebuffer&&i.deleteFramebuffer(S.__webglMultisampledFramebuffer),S.__webglColorRenderbuffer)for(let G=0;G<S.__webglColorRenderbuffer.length;G++)S.__webglColorRenderbuffer[G]&&i.deleteRenderbuffer(S.__webglColorRenderbuffer[G]);S.__webglDepthRenderbuffer&&i.deleteRenderbuffer(S.__webglDepthRenderbuffer)}let D=A.textures;for(let G=0,Z=D.length;G<Z;G++){let W=n.get(D[G]);W.__webglTexture&&(i.deleteTexture(W.__webglTexture),o.memory.textures--),n.remove(D[G])}n.remove(A)}let R=0;function I(){R=0}function L(){let A=R;return A>=s.maxTextures&&console.warn("THREE.WebGLTextures: Trying to use "+A+" texture units while this GPU supports only "+s.maxTextures),R+=1,A}function N(A){let S=[];return S.push(A.wrapS),S.push(A.wrapT),S.push(A.wrapR||0),S.push(A.magFilter),S.push(A.minFilter),S.push(A.anisotropy),S.push(A.internalFormat),S.push(A.format),S.push(A.type),S.push(A.generateMipmaps),S.push(A.premultiplyAlpha),S.push(A.flipY),S.push(A.unpackAlignment),S.push(A.colorSpace),S.join()}function V(A,S){let D=n.get(A);if(A.isVideoTexture&&It(A),A.isRenderTargetTexture===!1&&A.version>0&&D.__version!==A.version){let G=A.image;if(G===null)console.warn("THREE.WebGLRenderer: Texture marked for update but no image data found.");else if(G.complete===!1)console.warn("THREE.WebGLRenderer: Texture marked for update but image is incomplete");else{$(D,A,S);return}}e.bindTexture(i.TEXTURE_2D,D.__webglTexture,i.TEXTURE0+S)}function F(A,S){let D=n.get(A);if(A.version>0&&D.__version!==A.version){$(D,A,S);return}e.bindTexture(i.TEXTURE_2D_ARRAY,D.__webglTexture,i.TEXTURE0+S)}function X(A,S){let D=n.get(A);if(A.version>0&&D.__version!==A.version){$(D,A,S);return}e.bindTexture(i.TEXTURE_3D,D.__webglTexture,i.TEXTURE0+S)}function z(A,S){let D=n.get(A);if(A.version>0&&D.__version!==A.version){nt(D,A,S);return}e.bindTexture(i.TEXTURE_CUBE_MAP,D.__webglTexture,i.TEXTURE0+S)}let j={[_s]:i.REPEAT,[ei]:i.CLAMP_TO_EDGE,[Pa]:i.MIRRORED_REPEAT},it={[un]:i.NEAREST,[sd]:i.NEAREST_MIPMAP_NEAREST,[_r]:i.NEAREST_MIPMAP_LINEAR,[Bn]:i.LINEAR,[Bo]:i.LINEAR_MIPMAP_NEAREST,[ni]:i.LINEAR_MIPMAP_LINEAR},st={[ld]:i.NEVER,[pd]:i.ALWAYS,[cd]:i.LESS,[Hh]:i.LEQUAL,[hd]:i.EQUAL,[fd]:i.GEQUAL,[ud]:i.GREATER,[dd]:i.NOTEQUAL};function yt(A,S){if(S.type===zn&&t.has("OES_texture_float_linear")===!1&&(S.magFilter===Bn||S.magFilter===Bo||S.magFilter===_r||S.magFilter===ni||S.minFilter===Bn||S.minFilter===Bo||S.minFilter===_r||S.minFilter===ni)&&console.warn("THREE.WebGLRenderer: Unable to use linear filtering with floating point textures. OES_texture_float_linear not supported on this device."),i.texParameteri(A,i.TEXTURE_WRAP_S,j[S.wrapS]),i.texParameteri(A,i.TEXTURE_WRAP_T,j[S.wrapT]),(A===i.TEXTURE_3D||A===i.TEXTURE_2D_ARRAY)&&i.texParameteri(A,i.TEXTURE_WRAP_R,j[S.wrapR]),i.texParameteri(A,i.TEXTURE_MAG_FILTER,it[S.magFilter]),i.texParameteri(A,i.TEXTURE_MIN_FILTER,it[S.minFilter]),S.compareFunction&&(i.texParameteri(A,i.TEXTURE_COMPARE_MODE,i.COMPARE_REF_TO_TEXTURE),i.texParameteri(A,i.TEXTURE_COMPARE_FUNC,st[S.compareFunction])),t.has("EXT_texture_filter_anisotropic")===!0){if(S.magFilter===un||S.minFilter!==_r&&S.minFilter!==ni||S.type===zn&&t.has("OES_texture_float_linear")===!1)return;if(S.anisotropy>1||n.get(S).__currentAnisotropy){let D=t.get("EXT_texture_filter_anisotropic");i.texParameterf(A,D.TEXTURE_MAX_ANISOTROPY_EXT,Math.min(S.anisotropy,s.getMaxAnisotropy())),n.get(S).__currentAnisotropy=S.anisotropy}}}function Ft(A,S){let D=!1;A.__webglInit===void 0&&(A.__webglInit=!0,S.addEventListener("dispose",C));let G=S.source,Z=u.get(G);Z===void 0&&(Z={},u.set(G,Z));let W=N(S);if(W!==A.__cacheKey){Z[W]===void 0&&(Z[W]={texture:i.createTexture(),usedTimes:0},o.memory.textures++,D=!0),Z[W].usedTimes++;let et=Z[A.__cacheKey];et!==void 0&&(Z[A.__cacheKey].usedTimes--,et.usedTimes===0&&x(S)),A.__cacheKey=W,A.__webglTexture=Z[W].texture}return D}function $(A,S,D){let G=i.TEXTURE_2D;(S.isDataArrayTexture||S.isCompressedArrayTexture)&&(G=i.TEXTURE_2D_ARRAY),S.isData3DTexture&&(G=i.TEXTURE_3D);let Z=Ft(A,S),W=S.source;e.bindTexture(G,A.__webglTexture,i.TEXTURE0+D);let et=n.get(W);if(W.version!==et.__version||Z===!0){e.activeTexture(i.TEXTURE0+D);let Q=jt.getPrimaries(jt.workingColorSpace),rt=S.colorSpace===On?null:jt.getPrimaries(S.colorSpace),Nt=S.colorSpace===On||Q===rt?i.NONE:i.BROWSER_DEFAULT_WEBGL;i.pixelStorei(i.UNPACK_FLIP_Y_WEBGL,S.flipY),i.pixelStorei(i.UNPACK_PREMULTIPLY_ALPHA_WEBGL,S.premultiplyAlpha),i.pixelStorei(i.UNPACK_ALIGNMENT,S.unpackAlignment),i.pixelStorei(i.UNPACK_COLORSPACE_CONVERSION_WEBGL,Nt);let tt=y(S.image,!1,s.maxTextureSize);tt=te(S,tt);let lt=r.convert(S.format,S.colorSpace),xt=r.convert(S.type),Mt=E(S.internalFormat,lt,xt,S.colorSpace,S.isVideoTexture);yt(G,S);let _t,$t=S.mipmaps,Vt=S.isVideoTexture!==!0,fe=et.__version===void 0||Z===!0,B=W.dataReady,ut=U(S,tt);if(S.isDepthTexture)Mt=M(S.format===Ms,S.type),fe&&(Vt?e.texStorage2D(i.TEXTURE_2D,1,Mt,tt.width,tt.height):e.texImage2D(i.TEXTURE_2D,0,Mt,tt.width,tt.height,0,lt,xt,null));else if(S.isDataTexture)if($t.length>0){Vt&&fe&&e.texStorage2D(i.TEXTURE_2D,ut,Mt,$t[0].width,$t[0].height);for(let J=0,K=$t.length;J<K;J++)_t=$t[J],Vt?B&&e.texSubImage2D(i.TEXTURE_2D,J,0,0,_t.width,_t.height,lt,xt,_t.data):e.texImage2D(i.TEXTURE_2D,J,Mt,_t.width,_t.height,0,lt,xt,_t.data);S.generateMipmaps=!1}else Vt?(fe&&e.texStorage2D(i.TEXTURE_2D,ut,Mt,tt.width,tt.height),B&&e.texSubImage2D(i.TEXTURE_2D,0,0,0,tt.width,tt.height,lt,xt,tt.data)):e.texImage2D(i.TEXTURE_2D,0,Mt,tt.width,tt.height,0,lt,xt,tt.data);else if(S.isCompressedTexture)if(S.isCompressedArrayTexture){Vt&&fe&&e.texStorage3D(i.TEXTURE_2D_ARRAY,ut,Mt,$t[0].width,$t[0].height,tt.depth);for(let J=0,K=$t.length;J<K;J++)if(_t=$t[J],S.format!==Cn)if(lt!==null)if(Vt){if(B)if(S.layerUpdates.size>0){let vt=xh(_t.width,_t.height,S.format,S.type);for(let pt of S.layerUpdates){let Gt=_t.data.subarray(pt*vt/_t.data.BYTES_PER_ELEMENT,(pt+1)*vt/_t.data.BYTES_PER_ELEMENT);e.compressedTexSubImage3D(i.TEXTURE_2D_ARRAY,J,0,0,pt,_t.width,_t.height,1,lt,Gt)}S.clearLayerUpdates()}else e.compressedTexSubImage3D(i.TEXTURE_2D_ARRAY,J,0,0,0,_t.width,_t.height,tt.depth,lt,_t.data)}else e.compressedTexImage3D(i.TEXTURE_2D_ARRAY,J,Mt,_t.width,_t.height,tt.depth,0,_t.data,0,0);else console.warn("THREE.WebGLRenderer: Attempt to load unsupported compressed texture format in .uploadTexture()");else Vt?B&&e.texSubImage3D(i.TEXTURE_2D_ARRAY,J,0,0,0,_t.width,_t.height,tt.depth,lt,xt,_t.data):e.texImage3D(i.TEXTURE_2D_ARRAY,J,Mt,_t.width,_t.height,tt.depth,0,lt,xt,_t.data)}else{Vt&&fe&&e.texStorage2D(i.TEXTURE_2D,ut,Mt,$t[0].width,$t[0].height);for(let J=0,K=$t.length;J<K;J++)_t=$t[J],S.format!==Cn?lt!==null?Vt?B&&e.compressedTexSubImage2D(i.TEXTURE_2D,J,0,0,_t.width,_t.height,lt,_t.data):e.compressedTexImage2D(i.TEXTURE_2D,J,Mt,_t.width,_t.height,0,_t.data):console.warn("THREE.WebGLRenderer: Attempt to load unsupported compressed texture format in .uploadTexture()"):Vt?B&&e.texSubImage2D(i.TEXTURE_2D,J,0,0,_t.width,_t.height,lt,xt,_t.data):e.texImage2D(i.TEXTURE_2D,J,Mt,_t.width,_t.height,0,lt,xt,_t.data)}else if(S.isDataArrayTexture)if(Vt){if(fe&&e.texStorage3D(i.TEXTURE_2D_ARRAY,ut,Mt,tt.width,tt.height,tt.depth),B)if(S.layerUpdates.size>0){let J=xh(tt.width,tt.height,S.format,S.type);for(let K of S.layerUpdates){let vt=tt.data.subarray(K*J/tt.data.BYTES_PER_ELEMENT,(K+1)*J/tt.data.BYTES_PER_ELEMENT);e.texSubImage3D(i.TEXTURE_2D_ARRAY,0,0,0,K,tt.width,tt.height,1,lt,xt,vt)}S.clearLayerUpdates()}else e.texSubImage3D(i.TEXTURE_2D_ARRAY,0,0,0,0,tt.width,tt.height,tt.depth,lt,xt,tt.data)}else e.texImage3D(i.TEXTURE_2D_ARRAY,0,Mt,tt.width,tt.height,tt.depth,0,lt,xt,tt.data);else if(S.isData3DTexture)Vt?(fe&&e.texStorage3D(i.TEXTURE_3D,ut,Mt,tt.width,tt.height,tt.depth),B&&e.texSubImage3D(i.TEXTURE_3D,0,0,0,0,tt.width,tt.height,tt.depth,lt,xt,tt.data)):e.texImage3D(i.TEXTURE_3D,0,Mt,tt.width,tt.height,tt.depth,0,lt,xt,tt.data);else if(S.isFramebufferTexture){if(fe)if(Vt)e.texStorage2D(i.TEXTURE_2D,ut,Mt,tt.width,tt.height);else{let J=tt.width,K=tt.height;for(let vt=0;vt<ut;vt++)e.texImage2D(i.TEXTURE_2D,vt,Mt,J,K,0,lt,xt,null),J>>=1,K>>=1}}else if($t.length>0){if(Vt&&fe){let J=Rt($t[0]);e.texStorage2D(i.TEXTURE_2D,ut,Mt,J.width,J.height)}for(let J=0,K=$t.length;J<K;J++)_t=$t[J],Vt?B&&e.texSubImage2D(i.TEXTURE_2D,J,0,0,lt,xt,_t):e.texImage2D(i.TEXTURE_2D,J,Mt,lt,xt,_t);S.generateMipmaps=!1}else if(Vt){if(fe){let J=Rt(tt);e.texStorage2D(i.TEXTURE_2D,ut,Mt,J.width,J.height)}B&&e.texSubImage2D(i.TEXTURE_2D,0,0,0,lt,xt,tt)}else e.texImage2D(i.TEXTURE_2D,0,Mt,lt,xt,tt);m(S)&&p(G),et.__version=W.version,S.onUpdate&&S.onUpdate(S)}A.__version=S.version}function nt(A,S,D){if(S.image.length!==6)return;let G=Ft(A,S),Z=S.source;e.bindTexture(i.TEXTURE_CUBE_MAP,A.__webglTexture,i.TEXTURE0+D);let W=n.get(Z);if(Z.version!==W.__version||G===!0){e.activeTexture(i.TEXTURE0+D);let et=jt.getPrimaries(jt.workingColorSpace),Q=S.colorSpace===On?null:jt.getPrimaries(S.colorSpace),rt=S.colorSpace===On||et===Q?i.NONE:i.BROWSER_DEFAULT_WEBGL;i.pixelStorei(i.UNPACK_FLIP_Y_WEBGL,S.flipY),i.pixelStorei(i.UNPACK_PREMULTIPLY_ALPHA_WEBGL,S.premultiplyAlpha),i.pixelStorei(i.UNPACK_ALIGNMENT,S.unpackAlignment),i.pixelStorei(i.UNPACK_COLORSPACE_CONVERSION_WEBGL,rt);let Nt=S.isCompressedTexture||S.image[0].isCompressedTexture,tt=S.image[0]&&S.image[0].isDataTexture,lt=[];for(let K=0;K<6;K++)!Nt&&!tt?lt[K]=y(S.image[K],!0,s.maxCubemapSize):lt[K]=tt?S.image[K].image:S.image[K],lt[K]=te(S,lt[K]);let xt=lt[0],Mt=r.convert(S.format,S.colorSpace),_t=r.convert(S.type),$t=E(S.internalFormat,Mt,_t,S.colorSpace),Vt=S.isVideoTexture!==!0,fe=W.__version===void 0||G===!0,B=Z.dataReady,ut=U(S,xt);yt(i.TEXTURE_CUBE_MAP,S);let J;if(Nt){Vt&&fe&&e.texStorage2D(i.TEXTURE_CUBE_MAP,ut,$t,xt.width,xt.height);for(let K=0;K<6;K++){J=lt[K].mipmaps;for(let vt=0;vt<J.length;vt++){let pt=J[vt];S.format!==Cn?Mt!==null?Vt?B&&e.compressedTexSubImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+K,vt,0,0,pt.width,pt.height,Mt,pt.data):e.compressedTexImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+K,vt,$t,pt.width,pt.height,0,pt.data):console.warn("THREE.WebGLRenderer: Attempt to load unsupported compressed texture format in .setTextureCube()"):Vt?B&&e.texSubImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+K,vt,0,0,pt.width,pt.height,Mt,_t,pt.data):e.texImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+K,vt,$t,pt.width,pt.height,0,Mt,_t,pt.data)}}}else{if(J=S.mipmaps,Vt&&fe){J.length>0&&ut++;let K=Rt(lt[0]);e.texStorage2D(i.TEXTURE_CUBE_MAP,ut,$t,K.width,K.height)}for(let K=0;K<6;K++)if(tt){Vt?B&&e.texSubImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+K,0,0,0,lt[K].width,lt[K].height,Mt,_t,lt[K].data):e.texImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+K,0,$t,lt[K].width,lt[K].height,0,Mt,_t,lt[K].data);for(let vt=0;vt<J.length;vt++){let Gt=J[vt].image[K].image;Vt?B&&e.texSubImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+K,vt+1,0,0,Gt.width,Gt.height,Mt,_t,Gt.data):e.texImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+K,vt+1,$t,Gt.width,Gt.height,0,Mt,_t,Gt.data)}}else{Vt?B&&e.texSubImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+K,0,0,0,Mt,_t,lt[K]):e.texImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+K,0,$t,Mt,_t,lt[K]);for(let vt=0;vt<J.length;vt++){let pt=J[vt];Vt?B&&e.texSubImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+K,vt+1,0,0,Mt,_t,pt.image[K]):e.texImage2D(i.TEXTURE_CUBE_MAP_POSITIVE_X+K,vt+1,$t,Mt,_t,pt.image[K])}}}m(S)&&p(i.TEXTURE_CUBE_MAP),W.__version=Z.version,S.onUpdate&&S.onUpdate(S)}A.__version=S.version}function ft(A,S,D,G,Z,W){let et=r.convert(D.format,D.colorSpace),Q=r.convert(D.type),rt=E(D.internalFormat,et,Q,D.colorSpace),Nt=n.get(S),tt=n.get(D);if(tt.__renderTarget=S,!Nt.__hasExternalTextures){let lt=Math.max(1,S.width>>W),xt=Math.max(1,S.height>>W);Z===i.TEXTURE_3D||Z===i.TEXTURE_2D_ARRAY?e.texImage3D(Z,W,rt,lt,xt,S.depth,0,et,Q,null):e.texImage2D(Z,W,rt,lt,xt,0,et,Q,null)}e.bindFramebuffer(i.FRAMEBUFFER,A),kt(S)?a.framebufferTexture2DMultisampleEXT(i.FRAMEBUFFER,G,Z,tt.__webglTexture,0,Xt(S)):(Z===i.TEXTURE_2D||Z>=i.TEXTURE_CUBE_MAP_POSITIVE_X&&Z<=i.TEXTURE_CUBE_MAP_NEGATIVE_Z)&&i.framebufferTexture2D(i.FRAMEBUFFER,G,Z,tt.__webglTexture,W),e.bindFramebuffer(i.FRAMEBUFFER,null)}function ct(A,S,D){if(i.bindRenderbuffer(i.RENDERBUFFER,A),S.depthBuffer){let G=S.depthTexture,Z=G&&G.isDepthTexture?G.type:null,W=M(S.stencilBuffer,Z),et=S.stencilBuffer?i.DEPTH_STENCIL_ATTACHMENT:i.DEPTH_ATTACHMENT,Q=Xt(S);kt(S)?a.renderbufferStorageMultisampleEXT(i.RENDERBUFFER,Q,W,S.width,S.height):D?i.renderbufferStorageMultisample(i.RENDERBUFFER,Q,W,S.width,S.height):i.renderbufferStorage(i.RENDERBUFFER,W,S.width,S.height),i.framebufferRenderbuffer(i.FRAMEBUFFER,et,i.RENDERBUFFER,A)}else{let G=S.textures;for(let Z=0;Z<G.length;Z++){let W=G[Z],et=r.convert(W.format,W.colorSpace),Q=r.convert(W.type),rt=E(W.internalFormat,et,Q,W.colorSpace),Nt=Xt(S);D&&kt(S)===!1?i.renderbufferStorageMultisample(i.RENDERBUFFER,Nt,rt,S.width,S.height):kt(S)?a.renderbufferStorageMultisampleEXT(i.RENDERBUFFER,Nt,rt,S.width,S.height):i.renderbufferStorage(i.RENDERBUFFER,rt,S.width,S.height)}}i.bindRenderbuffer(i.RENDERBUFFER,null)}function bt(A,S){if(S&&S.isWebGLCubeRenderTarget)throw new Error("Depth Texture with cube render targets is not supported");if(e.bindFramebuffer(i.FRAMEBUFFER,A),!(S.depthTexture&&S.depthTexture.isDepthTexture))throw new Error("renderTarget.depthTexture must be an instance of THREE.DepthTexture");let G=n.get(S.depthTexture);G.__renderTarget=S,(!G.__webglTexture||S.depthTexture.image.width!==S.width||S.depthTexture.image.height!==S.height)&&(S.depthTexture.image.width=S.width,S.depthTexture.image.height=S.height,S.depthTexture.needsUpdate=!0),V(S.depthTexture,0);let Z=G.__webglTexture,W=Xt(S);if(S.depthTexture.format===fs)kt(S)?a.framebufferTexture2DMultisampleEXT(i.FRAMEBUFFER,i.DEPTH_ATTACHMENT,i.TEXTURE_2D,Z,0,W):i.framebufferTexture2D(i.FRAMEBUFFER,i.DEPTH_ATTACHMENT,i.TEXTURE_2D,Z,0);else if(S.depthTexture.format===Ms)kt(S)?a.framebufferTexture2DMultisampleEXT(i.FRAMEBUFFER,i.DEPTH_STENCIL_ATTACHMENT,i.TEXTURE_2D,Z,0,W):i.framebufferTexture2D(i.FRAMEBUFFER,i.DEPTH_STENCIL_ATTACHMENT,i.TEXTURE_2D,Z,0);else throw new Error("Unknown depthTexture format")}function Lt(A){let S=n.get(A),D=A.isWebGLCubeRenderTarget===!0;if(S.__boundDepthTexture!==A.depthTexture){let G=A.depthTexture;if(S.__depthDisposeCallback&&S.__depthDisposeCallback(),G){let Z=()=>{delete S.__boundDepthTexture,delete S.__depthDisposeCallback,G.removeEventListener("dispose",Z)};G.addEventListener("dispose",Z),S.__depthDisposeCallback=Z}S.__boundDepthTexture=G}if(A.depthTexture&&!S.__autoAllocateDepthBuffer){if(D)throw new Error("target.depthTexture not supported in Cube render targets");bt(S.__webglFramebuffer,A)}else if(D){S.__webglDepthbuffer=[];for(let G=0;G<6;G++)if(e.bindFramebuffer(i.FRAMEBUFFER,S.__webglFramebuffer[G]),S.__webglDepthbuffer[G]===void 0)S.__webglDepthbuffer[G]=i.createRenderbuffer(),ct(S.__webglDepthbuffer[G],A,!1);else{let Z=A.stencilBuffer?i.DEPTH_STENCIL_ATTACHMENT:i.DEPTH_ATTACHMENT,W=S.__webglDepthbuffer[G];i.bindRenderbuffer(i.RENDERBUFFER,W),i.framebufferRenderbuffer(i.FRAMEBUFFER,Z,i.RENDERBUFFER,W)}}else if(e.bindFramebuffer(i.FRAMEBUFFER,S.__webglFramebuffer),S.__webglDepthbuffer===void 0)S.__webglDepthbuffer=i.createRenderbuffer(),ct(S.__webglDepthbuffer,A,!1);else{let G=A.stencilBuffer?i.DEPTH_STENCIL_ATTACHMENT:i.DEPTH_ATTACHMENT,Z=S.__webglDepthbuffer;i.bindRenderbuffer(i.RENDERBUFFER,Z),i.framebufferRenderbuffer(i.FRAMEBUFFER,G,i.RENDERBUFFER,Z)}e.bindFramebuffer(i.FRAMEBUFFER,null)}function Et(A,S,D){let G=n.get(A);S!==void 0&&ft(G.__webglFramebuffer,A,A.texture,i.COLOR_ATTACHMENT0,i.TEXTURE_2D,0),D!==void 0&&Lt(A)}function Kt(A){let S=A.texture,D=n.get(A),G=n.get(S);A.addEventListener("dispose",v);let Z=A.textures,W=A.isWebGLCubeRenderTarget===!0,et=Z.length>1;if(et||(G.__webglTexture===void 0&&(G.__webglTexture=i.createTexture()),G.__version=S.version,o.memory.textures++),W){D.__webglFramebuffer=[];for(let Q=0;Q<6;Q++)if(S.mipmaps&&S.mipmaps.length>0){D.__webglFramebuffer[Q]=[];for(let rt=0;rt<S.mipmaps.length;rt++)D.__webglFramebuffer[Q][rt]=i.createFramebuffer()}else D.__webglFramebuffer[Q]=i.createFramebuffer()}else{if(S.mipmaps&&S.mipmaps.length>0){D.__webglFramebuffer=[];for(let Q=0;Q<S.mipmaps.length;Q++)D.__webglFramebuffer[Q]=i.createFramebuffer()}else D.__webglFramebuffer=i.createFramebuffer();if(et)for(let Q=0,rt=Z.length;Q<rt;Q++){let Nt=n.get(Z[Q]);Nt.__webglTexture===void 0&&(Nt.__webglTexture=i.createTexture(),o.memory.textures++)}if(A.samples>0&&kt(A)===!1){D.__webglMultisampledFramebuffer=i.createFramebuffer(),D.__webglColorRenderbuffer=[],e.bindFramebuffer(i.FRAMEBUFFER,D.__webglMultisampledFramebuffer);for(let Q=0;Q<Z.length;Q++){let rt=Z[Q];D.__webglColorRenderbuffer[Q]=i.createRenderbuffer(),i.bindRenderbuffer(i.RENDERBUFFER,D.__webglColorRenderbuffer[Q]);let Nt=r.convert(rt.format,rt.colorSpace),tt=r.convert(rt.type),lt=E(rt.internalFormat,Nt,tt,rt.colorSpace,A.isXRRenderTarget===!0),xt=Xt(A);i.renderbufferStorageMultisample(i.RENDERBUFFER,xt,lt,A.width,A.height),i.framebufferRenderbuffer(i.FRAMEBUFFER,i.COLOR_ATTACHMENT0+Q,i.RENDERBUFFER,D.__webglColorRenderbuffer[Q])}i.bindRenderbuffer(i.RENDERBUFFER,null),A.depthBuffer&&(D.__webglDepthRenderbuffer=i.createRenderbuffer(),ct(D.__webglDepthRenderbuffer,A,!0)),e.bindFramebuffer(i.FRAMEBUFFER,null)}}if(W){e.bindTexture(i.TEXTURE_CUBE_MAP,G.__webglTexture),yt(i.TEXTURE_CUBE_MAP,S);for(let Q=0;Q<6;Q++)if(S.mipmaps&&S.mipmaps.length>0)for(let rt=0;rt<S.mipmaps.length;rt++)ft(D.__webglFramebuffer[Q][rt],A,S,i.COLOR_ATTACHMENT0,i.TEXTURE_CUBE_MAP_POSITIVE_X+Q,rt);else ft(D.__webglFramebuffer[Q],A,S,i.COLOR_ATTACHMENT0,i.TEXTURE_CUBE_MAP_POSITIVE_X+Q,0);m(S)&&p(i.TEXTURE_CUBE_MAP),e.unbindTexture()}else if(et){for(let Q=0,rt=Z.length;Q<rt;Q++){let Nt=Z[Q],tt=n.get(Nt);e.bindTexture(i.TEXTURE_2D,tt.__webglTexture),yt(i.TEXTURE_2D,Nt),ft(D.__webglFramebuffer,A,Nt,i.COLOR_ATTACHMENT0+Q,i.TEXTURE_2D,0),m(Nt)&&p(i.TEXTURE_2D)}e.unbindTexture()}else{let Q=i.TEXTURE_2D;if((A.isWebGL3DRenderTarget||A.isWebGLArrayRenderTarget)&&(Q=A.isWebGL3DRenderTarget?i.TEXTURE_3D:i.TEXTURE_2D_ARRAY),e.bindTexture(Q,G.__webglTexture),yt(Q,S),S.mipmaps&&S.mipmaps.length>0)for(let rt=0;rt<S.mipmaps.length;rt++)ft(D.__webglFramebuffer[rt],A,S,i.COLOR_ATTACHMENT0,Q,rt);else ft(D.__webglFramebuffer,A,S,i.COLOR_ATTACHMENT0,Q,0);m(S)&&p(Q),e.unbindTexture()}A.depthBuffer&&Lt(A)}function Ht(A){let S=A.textures;for(let D=0,G=S.length;D<G;D++){let Z=S[D];if(m(Z)){let W=w(A),et=n.get(Z).__webglTexture;e.bindTexture(W,et),p(W),e.unbindTexture()}}}let ue=[],O=[];function be(A){if(A.samples>0){if(kt(A)===!1){let S=A.textures,D=A.width,G=A.height,Z=i.COLOR_BUFFER_BIT,W=A.stencilBuffer?i.DEPTH_STENCIL_ATTACHMENT:i.DEPTH_ATTACHMENT,et=n.get(A),Q=S.length>1;if(Q)for(let rt=0;rt<S.length;rt++)e.bindFramebuffer(i.FRAMEBUFFER,et.__webglMultisampledFramebuffer),i.framebufferRenderbuffer(i.FRAMEBUFFER,i.COLOR_ATTACHMENT0+rt,i.RENDERBUFFER,null),e.bindFramebuffer(i.FRAMEBUFFER,et.__webglFramebuffer),i.framebufferTexture2D(i.DRAW_FRAMEBUFFER,i.COLOR_ATTACHMENT0+rt,i.TEXTURE_2D,null,0);e.bindFramebuffer(i.READ_FRAMEBUFFER,et.__webglMultisampledFramebuffer),e.bindFramebuffer(i.DRAW_FRAMEBUFFER,et.__webglFramebuffer);for(let rt=0;rt<S.length;rt++){if(A.resolveDepthBuffer&&(A.depthBuffer&&(Z|=i.DEPTH_BUFFER_BIT),A.stencilBuffer&&A.resolveStencilBuffer&&(Z|=i.STENCIL_BUFFER_BIT)),Q){i.framebufferRenderbuffer(i.READ_FRAMEBUFFER,i.COLOR_ATTACHMENT0,i.RENDERBUFFER,et.__webglColorRenderbuffer[rt]);let Nt=n.get(S[rt]).__webglTexture;i.framebufferTexture2D(i.DRAW_FRAMEBUFFER,i.COLOR_ATTACHMENT0,i.TEXTURE_2D,Nt,0)}i.blitFramebuffer(0,0,D,G,0,0,D,G,Z,i.NEAREST),l===!0&&(ue.length=0,O.length=0,ue.push(i.COLOR_ATTACHMENT0+rt),A.depthBuffer&&A.resolveDepthBuffer===!1&&(ue.push(W),O.push(W),i.invalidateFramebuffer(i.DRAW_FRAMEBUFFER,O)),i.invalidateFramebuffer(i.READ_FRAMEBUFFER,ue))}if(e.bindFramebuffer(i.READ_FRAMEBUFFER,null),e.bindFramebuffer(i.DRAW_FRAMEBUFFER,null),Q)for(let rt=0;rt<S.length;rt++){e.bindFramebuffer(i.FRAMEBUFFER,et.__webglMultisampledFramebuffer),i.framebufferRenderbuffer(i.FRAMEBUFFER,i.COLOR_ATTACHMENT0+rt,i.RENDERBUFFER,et.__webglColorRenderbuffer[rt]);let Nt=n.get(S[rt]).__webglTexture;e.bindFramebuffer(i.FRAMEBUFFER,et.__webglFramebuffer),i.framebufferTexture2D(i.DRAW_FRAMEBUFFER,i.COLOR_ATTACHMENT0+rt,i.TEXTURE_2D,Nt,0)}e.bindFramebuffer(i.DRAW_FRAMEBUFFER,et.__webglMultisampledFramebuffer)}else if(A.depthBuffer&&A.resolveDepthBuffer===!1&&l){let S=A.stencilBuffer?i.DEPTH_STENCIL_ATTACHMENT:i.DEPTH_ATTACHMENT;i.invalidateFramebuffer(i.DRAW_FRAMEBUFFER,[S])}}}function Xt(A){return Math.min(s.maxSamples,A.samples)}function kt(A){let S=n.get(A);return A.samples>0&&t.has("WEBGL_multisampled_render_to_texture")===!0&&S.__useRenderToTexture!==!1}function It(A){let S=o.render.frame;h.get(A)!==S&&(h.set(A,S),A.update())}function te(A,S){let D=A.colorSpace,G=A.format,Z=A.type;return A.isCompressedTexture===!0||A.isVideoTexture===!0||D!==Ps&&D!==On&&(jt.getTransfer(D)===ce?(G!==Cn||Z!==ri)&&console.warn("THREE.WebGLTextures: sRGB encoded textures have to use RGBAFormat and UnsignedByteType."):console.error("THREE.WebGLTextures: Unsupported texture color space:",D)),S}function Rt(A){return typeof HTMLImageElement!="undefined"&&A instanceof HTMLImageElement?(c.width=A.naturalWidth||A.width,c.height=A.naturalHeight||A.height):typeof VideoFrame!="undefined"&&A instanceof VideoFrame?(c.width=A.displayWidth,c.height=A.displayHeight):(c.width=A.width,c.height=A.height),c}this.allocateTextureUnit=L,this.resetTextureUnits=I,this.setTexture2D=V,this.setTexture2DArray=F,this.setTexture3D=X,this.setTextureCube=z,this.rebindTextures=Et,this.setupRenderTarget=Kt,this.updateRenderTargetMipmap=Ht,this.updateMultisampleRenderTarget=be,this.setupDepthRenderbuffer=Lt,this.setupFrameBufferTexture=ft,this.useMultisampledRTT=kt}function ag(i,t){function e(n,s=On){let r,o=jt.getTransfer(s);if(n===ri)return i.UNSIGNED_BYTE;if(n===ql)return i.UNSIGNED_SHORT_4_4_4_4;if(n===Yl)return i.UNSIGNED_SHORT_5_5_5_1;if(n===Lh)return i.UNSIGNED_INT_5_9_9_9_REV;if(n===Ph)return i.BYTE;if(n===Ih)return i.SHORT;if(n===nr)return i.UNSIGNED_SHORT;if(n===Xl)return i.INT;if(n===Bi)return i.UNSIGNED_INT;if(n===zn)return i.FLOAT;if(n===ar)return i.HALF_FLOAT;if(n===Dh)return i.ALPHA;if(n===Uh)return i.RGB;if(n===Cn)return i.RGBA;if(n===Nh)return i.LUMINANCE;if(n===Fh)return i.LUMINANCE_ALPHA;if(n===fs)return i.DEPTH_COMPONENT;if(n===Ms)return i.DEPTH_STENCIL;if(n===$l)return i.RED;if(n===Zl)return i.RED_INTEGER;if(n===Oh)return i.RG;if(n===Jl)return i.RG_INTEGER;if(n===Kl)return i.RGBA_INTEGER;if(n===Xr||n===qr||n===Yr||n===$r)if(o===ce)if(r=t.get("WEBGL_compressed_texture_s3tc_srgb"),r!==null){if(n===Xr)return r.COMPRESSED_SRGB_S3TC_DXT1_EXT;if(n===qr)return r.COMPRESSED_SRGB_ALPHA_S3TC_DXT1_EXT;if(n===Yr)return r.COMPRESSED_SRGB_ALPHA_S3TC_DXT3_EXT;if(n===$r)return r.COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT}else return null;else if(r=t.get("WEBGL_compressed_texture_s3tc"),r!==null){if(n===Xr)return r.COMPRESSED_RGB_S3TC_DXT1_EXT;if(n===qr)return r.COMPRESSED_RGBA_S3TC_DXT1_EXT;if(n===Yr)return r.COMPRESSED_RGBA_S3TC_DXT3_EXT;if(n===$r)return r.COMPRESSED_RGBA_S3TC_DXT5_EXT}else return null;if(n===Ia||n===La||n===Da||n===Ua)if(r=t.get("WEBGL_compressed_texture_pvrtc"),r!==null){if(n===Ia)return r.COMPRESSED_RGB_PVRTC_4BPPV1_IMG;if(n===La)return r.COMPRESSED_RGB_PVRTC_2BPPV1_IMG;if(n===Da)return r.COMPRESSED_RGBA_PVRTC_4BPPV1_IMG;if(n===Ua)return r.COMPRESSED_RGBA_PVRTC_2BPPV1_IMG}else return null;if(n===Na||n===Fa||n===Oa)if(r=t.get("WEBGL_compressed_texture_etc"),r!==null){if(n===Na||n===Fa)return o===ce?r.COMPRESSED_SRGB8_ETC2:r.COMPRESSED_RGB8_ETC2;if(n===Oa)return o===ce?r.COMPRESSED_SRGB8_ALPHA8_ETC2_EAC:r.COMPRESSED_RGBA8_ETC2_EAC}else return null;if(n===Ba||n===za||n===Ha||n===ka||n===Va||n===Ga||n===Wa||n===Xa||n===qa||n===Ya||n===$a||n===Za||n===Ja||n===Ka)if(r=t.get("WEBGL_compressed_texture_astc"),r!==null){if(n===Ba)return o===ce?r.COMPRESSED_SRGB8_ALPHA8_ASTC_4x4_KHR:r.COMPRESSED_RGBA_ASTC_4x4_KHR;if(n===za)return o===ce?r.COMPRESSED_SRGB8_ALPHA8_ASTC_5x4_KHR:r.COMPRESSED_RGBA_ASTC_5x4_KHR;if(n===Ha)return o===ce?r.COMPRESSED_SRGB8_ALPHA8_ASTC_5x5_KHR:r.COMPRESSED_RGBA_ASTC_5x5_KHR;if(n===ka)return o===ce?r.COMPRESSED_SRGB8_ALPHA8_ASTC_6x5_KHR:r.COMPRESSED_RGBA_ASTC_6x5_KHR;if(n===Va)return o===ce?r.COMPRESSED_SRGB8_ALPHA8_ASTC_6x6_KHR:r.COMPRESSED_RGBA_ASTC_6x6_KHR;if(n===Ga)return o===ce?r.COMPRESSED_SRGB8_ALPHA8_ASTC_8x5_KHR:r.COMPRESSED_RGBA_ASTC_8x5_KHR;if(n===Wa)return o===ce?r.COMPRESSED_SRGB8_ALPHA8_ASTC_8x6_KHR:r.COMPRESSED_RGBA_ASTC_8x6_KHR;if(n===Xa)return o===ce?r.COMPRESSED_SRGB8_ALPHA8_ASTC_8x8_KHR:r.COMPRESSED_RGBA_ASTC_8x8_KHR;if(n===qa)return o===ce?r.COMPRESSED_SRGB8_ALPHA8_ASTC_10x5_KHR:r.COMPRESSED_RGBA_ASTC_10x5_KHR;if(n===Ya)return o===ce?r.COMPRESSED_SRGB8_ALPHA8_ASTC_10x6_KHR:r.COMPRESSED_RGBA_ASTC_10x6_KHR;if(n===$a)return o===ce?r.COMPRESSED_SRGB8_ALPHA8_ASTC_10x8_KHR:r.COMPRESSED_RGBA_ASTC_10x8_KHR;if(n===Za)return o===ce?r.COMPRESSED_SRGB8_ALPHA8_ASTC_10x10_KHR:r.COMPRESSED_RGBA_ASTC_10x10_KHR;if(n===Ja)return o===ce?r.COMPRESSED_SRGB8_ALPHA8_ASTC_12x10_KHR:r.COMPRESSED_RGBA_ASTC_12x10_KHR;if(n===Ka)return o===ce?r.COMPRESSED_SRGB8_ALPHA8_ASTC_12x12_KHR:r.COMPRESSED_RGBA_ASTC_12x12_KHR}else return null;if(n===Zr||n===ja||n===Qa)if(r=t.get("EXT_texture_compression_bptc"),r!==null){if(n===Zr)return o===ce?r.COMPRESSED_SRGB_ALPHA_BPTC_UNORM_EXT:r.COMPRESSED_RGBA_BPTC_UNORM_EXT;if(n===ja)return r.COMPRESSED_RGB_BPTC_SIGNED_FLOAT_EXT;if(n===Qa)return r.COMPRESSED_RGB_BPTC_UNSIGNED_FLOAT_EXT}else return null;if(n===Bh||n===tl||n===el||n===nl)if(r=t.get("EXT_texture_compression_rgtc"),r!==null){if(n===Zr)return r.COMPRESSED_RED_RGTC1_EXT;if(n===tl)return r.COMPRESSED_SIGNED_RED_RGTC1_EXT;if(n===el)return r.COMPRESSED_RED_GREEN_RGTC2_EXT;if(n===nl)return r.COMPRESSED_SIGNED_RED_GREEN_RGTC2_EXT}else return null;return n===ys?i.UNSIGNED_INT_24_8:i[n]!==void 0?i[n]:null}return{convert:e}}var xl=class extends Fe{constructor(t=[]){super(),this.isArrayCamera=!0,this.cameras=t}},Dt=class extends Ze{constructor(){super(),this.isGroup=!0,this.type="Group"}},lg={type:"move"},Qs=class{constructor(){this._targetRay=null,this._grip=null,this._hand=null}getHandSpace(){return this._hand===null&&(this._hand=new Dt,this._hand.matrixAutoUpdate=!1,this._hand.visible=!1,this._hand.joints={},this._hand.inputState={pinching:!1}),this._hand}getTargetRaySpace(){return this._targetRay===null&&(this._targetRay=new Dt,this._targetRay.matrixAutoUpdate=!1,this._targetRay.visible=!1,this._targetRay.hasLinearVelocity=!1,this._targetRay.linearVelocity=new P,this._targetRay.hasAngularVelocity=!1,this._targetRay.angularVelocity=new P),this._targetRay}getGripSpace(){return this._grip===null&&(this._grip=new Dt,this._grip.matrixAutoUpdate=!1,this._grip.visible=!1,this._grip.hasLinearVelocity=!1,this._grip.linearVelocity=new P,this._grip.hasAngularVelocity=!1,this._grip.angularVelocity=new P),this._grip}dispatchEvent(t){return this._targetRay!==null&&this._targetRay.dispatchEvent(t),this._grip!==null&&this._grip.dispatchEvent(t),this._hand!==null&&this._hand.dispatchEvent(t),this}connect(t){if(t&&t.hand){let e=this._hand;if(e)for(let n of t.hand.values())this._getHandJoint(e,n)}return this.dispatchEvent({type:"connected",data:t}),this}disconnect(t){return this.dispatchEvent({type:"disconnected",data:t}),this._targetRay!==null&&(this._targetRay.visible=!1),this._grip!==null&&(this._grip.visible=!1),this._hand!==null&&(this._hand.visible=!1),this}update(t,e,n){let s=null,r=null,o=null,a=this._targetRay,l=this._grip,c=this._hand;if(t&&e.session.visibilityState!=="visible-blurred"){if(c&&t.hand){o=!0;for(let y of t.hand.values()){let m=e.getJointPose(y,n),p=this._getHandJoint(c,y);m!==null&&(p.matrix.fromArray(m.transform.matrix),p.matrix.decompose(p.position,p.rotation,p.scale),p.matrixWorldNeedsUpdate=!0,p.jointRadius=m.radius),p.visible=m!==null}let h=c.joints["index-finger-tip"],d=c.joints["thumb-tip"],u=h.position.distanceTo(d.position),f=.02,g=.005;c.inputState.pinching&&u>f+g?(c.inputState.pinching=!1,this.dispatchEvent({type:"pinchend",handedness:t.handedness,target:this})):!c.inputState.pinching&&u<=f-g&&(c.inputState.pinching=!0,this.dispatchEvent({type:"pinchstart",handedness:t.handedness,target:this}))}else l!==null&&t.gripSpace&&(r=e.getPose(t.gripSpace,n),r!==null&&(l.matrix.fromArray(r.transform.matrix),l.matrix.decompose(l.position,l.rotation,l.scale),l.matrixWorldNeedsUpdate=!0,r.linearVelocity?(l.hasLinearVelocity=!0,l.linearVelocity.copy(r.linearVelocity)):l.hasLinearVelocity=!1,r.angularVelocity?(l.hasAngularVelocity=!0,l.angularVelocity.copy(r.angularVelocity)):l.hasAngularVelocity=!1));a!==null&&(s=e.getPose(t.targetRaySpace,n),s===null&&r!==null&&(s=r),s!==null&&(a.matrix.fromArray(s.transform.matrix),a.matrix.decompose(a.position,a.rotation,a.scale),a.matrixWorldNeedsUpdate=!0,s.linearVelocity?(a.hasLinearVelocity=!0,a.linearVelocity.copy(s.linearVelocity)):a.hasLinearVelocity=!1,s.angularVelocity?(a.hasAngularVelocity=!0,a.angularVelocity.copy(s.angularVelocity)):a.hasAngularVelocity=!1,this.dispatchEvent(lg)))}return a!==null&&(a.visible=s!==null),l!==null&&(l.visible=r!==null),c!==null&&(c.visible=o!==null),this}_getHandJoint(t,e){if(t.joints[e.jointName]===void 0){let n=new Dt;n.matrixAutoUpdate=!1,n.visible=!1,t.joints[e.jointName]=n,t.add(n)}return t.joints[e.jointName]}},cg=`
void main() {

	gl_Position = vec4( position, 1.0 );

}`,hg=`
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

}`,_l=class{constructor(){this.texture=null,this.mesh=null,this.depthNear=0,this.depthFar=0}init(t,e,n){if(this.texture===null){let s=new nn,r=t.properties.get(s);r.__webglTexture=e.texture,(e.depthNear!=n.depthNear||e.depthFar!=n.depthFar)&&(this.depthNear=e.depthNear,this.depthFar=e.depthFar),this.texture=s}}getMesh(t){if(this.texture!==null&&this.mesh===null){let e=t.cameras[0].viewport,n=new sn({vertexShader:cg,fragmentShader:hg,uniforms:{depthColor:{value:this.texture},depthWidth:{value:e.z},depthHeight:{value:e.w}}});this.mesh=new at(new Je(20,20),n)}return this.mesh}reset(){this.texture=null,this.mesh=null}getDepthTexture(){return this.texture}},yl=class extends _i{constructor(t,e){super();let n=this,s=null,r=1,o=null,a="local-floor",l=1,c=null,h=null,d=null,u=null,f=null,g=null,y=new _l,m=e.getContextAttributes(),p=null,w=null,E=[],M=[],U=new gt,C=null,v=new Fe;v.viewport=new re;let b=new Fe;b.viewport=new re;let x=[v,b],_=new xl,R=null,I=null;this.cameraAutoUpdate=!0,this.enabled=!1,this.isPresenting=!1,this.getController=function($){let nt=E[$];return nt===void 0&&(nt=new Qs,E[$]=nt),nt.getTargetRaySpace()},this.getControllerGrip=function($){let nt=E[$];return nt===void 0&&(nt=new Qs,E[$]=nt),nt.getGripSpace()},this.getHand=function($){let nt=E[$];return nt===void 0&&(nt=new Qs,E[$]=nt),nt.getHandSpace()};function L($){let nt=M.indexOf($.inputSource);if(nt===-1)return;let ft=E[nt];ft!==void 0&&(ft.update($.inputSource,$.frame,c||o),ft.dispatchEvent({type:$.type,data:$.inputSource}))}function N(){s.removeEventListener("select",L),s.removeEventListener("selectstart",L),s.removeEventListener("selectend",L),s.removeEventListener("squeeze",L),s.removeEventListener("squeezestart",L),s.removeEventListener("squeezeend",L),s.removeEventListener("end",N),s.removeEventListener("inputsourceschange",V);for(let $=0;$<E.length;$++){let nt=M[$];nt!==null&&(M[$]=null,E[$].disconnect(nt))}R=null,I=null,y.reset(),t.setRenderTarget(p),f=null,u=null,d=null,s=null,w=null,Ft.stop(),n.isPresenting=!1,t.setPixelRatio(C),t.setSize(U.width,U.height,!1),n.dispatchEvent({type:"sessionend"})}this.setFramebufferScaleFactor=function($){r=$,n.isPresenting===!0&&console.warn("THREE.WebXRManager: Cannot change framebuffer scale while presenting.")},this.setReferenceSpaceType=function($){a=$,n.isPresenting===!0&&console.warn("THREE.WebXRManager: Cannot change reference space type while presenting.")},this.getReferenceSpace=function(){return c||o},this.setReferenceSpace=function($){c=$},this.getBaseLayer=function(){return u!==null?u:f},this.getBinding=function(){return d},this.getFrame=function(){return g},this.getSession=function(){return s},this.setSession=async function($){if(s=$,s!==null){if(p=t.getRenderTarget(),s.addEventListener("select",L),s.addEventListener("selectstart",L),s.addEventListener("selectend",L),s.addEventListener("squeeze",L),s.addEventListener("squeezestart",L),s.addEventListener("squeezeend",L),s.addEventListener("end",N),s.addEventListener("inputsourceschange",V),m.xrCompatible!==!0&&await e.makeXRCompatible(),C=t.getPixelRatio(),t.getSize(U),s.renderState.layers===void 0){let nt={antialias:m.antialias,alpha:!0,depth:m.depth,stencil:m.stencil,framebufferScaleFactor:r};f=new XRWebGLLayer(s,e,nt),s.updateRenderState({baseLayer:f}),t.setPixelRatio(1),t.setSize(f.framebufferWidth,f.framebufferHeight,!1),w=new oi(f.framebufferWidth,f.framebufferHeight,{format:Cn,type:ri,colorSpace:t.outputColorSpace,stencilBuffer:m.stencil})}else{let nt=null,ft=null,ct=null;m.depth&&(ct=m.stencil?e.DEPTH24_STENCIL8:e.DEPTH_COMPONENT24,nt=m.stencil?Ms:fs,ft=m.stencil?ys:Bi);let bt={colorFormat:e.RGBA8,depthFormat:ct,scaleFactor:r};d=new XRWebGLBinding(s,e),u=d.createProjectionLayer(bt),s.updateRenderState({layers:[u]}),t.setPixelRatio(1),t.setSize(u.textureWidth,u.textureHeight,!1),w=new oi(u.textureWidth,u.textureHeight,{format:Cn,type:ri,depthTexture:new lo(u.textureWidth,u.textureHeight,ft,void 0,void 0,void 0,void 0,void 0,void 0,nt),stencilBuffer:m.stencil,colorSpace:t.outputColorSpace,samples:m.antialias?4:0,resolveDepthBuffer:u.ignoreDepthValues===!1})}w.isXRRenderTarget=!0,this.setFoveation(l),c=null,o=await s.requestReferenceSpace(a),Ft.setContext(s),Ft.start(),n.isPresenting=!0,n.dispatchEvent({type:"sessionstart"})}},this.getEnvironmentBlendMode=function(){if(s!==null)return s.environmentBlendMode},this.getDepthTexture=function(){return y.getDepthTexture()};function V($){for(let nt=0;nt<$.removed.length;nt++){let ft=$.removed[nt],ct=M.indexOf(ft);ct>=0&&(M[ct]=null,E[ct].disconnect(ft))}for(let nt=0;nt<$.added.length;nt++){let ft=$.added[nt],ct=M.indexOf(ft);if(ct===-1){for(let Lt=0;Lt<E.length;Lt++)if(Lt>=M.length){M.push(ft),ct=Lt;break}else if(M[Lt]===null){M[Lt]=ft,ct=Lt;break}if(ct===-1)break}let bt=E[ct];bt&&bt.connect(ft)}}let F=new P,X=new P;function z($,nt,ft){F.setFromMatrixPosition(nt.matrixWorld),X.setFromMatrixPosition(ft.matrixWorld);let ct=F.distanceTo(X),bt=nt.projectionMatrix.elements,Lt=ft.projectionMatrix.elements,Et=bt[14]/(bt[10]-1),Kt=bt[14]/(bt[10]+1),Ht=(bt[9]+1)/bt[5],ue=(bt[9]-1)/bt[5],O=(bt[8]-1)/bt[0],be=(Lt[8]+1)/Lt[0],Xt=Et*O,kt=Et*be,It=ct/(-O+be),te=It*-O;if(nt.matrixWorld.decompose($.position,$.quaternion,$.scale),$.translateX(te),$.translateZ(It),$.matrixWorld.compose($.position,$.quaternion,$.scale),$.matrixWorldInverse.copy($.matrixWorld).invert(),bt[10]===-1)$.projectionMatrix.copy(nt.projectionMatrix),$.projectionMatrixInverse.copy(nt.projectionMatrixInverse);else{let Rt=Et+It,A=Kt+It,S=Xt-te,D=kt+(ct-te),G=Ht*Kt/A*Rt,Z=ue*Kt/A*Rt;$.projectionMatrix.makePerspective(S,D,G,Z,Rt,A),$.projectionMatrixInverse.copy($.projectionMatrix).invert()}}function j($,nt){nt===null?$.matrixWorld.copy($.matrix):$.matrixWorld.multiplyMatrices(nt.matrixWorld,$.matrix),$.matrixWorldInverse.copy($.matrixWorld).invert()}this.updateCamera=function($){if(s===null)return;let nt=$.near,ft=$.far;y.texture!==null&&(y.depthNear>0&&(nt=y.depthNear),y.depthFar>0&&(ft=y.depthFar)),_.near=b.near=v.near=nt,_.far=b.far=v.far=ft,(R!==_.near||I!==_.far)&&(s.updateRenderState({depthNear:_.near,depthFar:_.far}),R=_.near,I=_.far),v.layers.mask=$.layers.mask|2,b.layers.mask=$.layers.mask|4,_.layers.mask=v.layers.mask|b.layers.mask;let ct=$.parent,bt=_.cameras;j(_,ct);for(let Lt=0;Lt<bt.length;Lt++)j(bt[Lt],ct);bt.length===2?z(_,v,b):_.projectionMatrix.copy(v.projectionMatrix),it($,_,ct)};function it($,nt,ft){ft===null?$.matrix.copy(nt.matrixWorld):($.matrix.copy(ft.matrixWorld),$.matrix.invert(),$.matrix.multiply(nt.matrixWorld)),$.matrix.decompose($.position,$.quaternion,$.scale),$.updateMatrixWorld(!0),$.projectionMatrix.copy(nt.projectionMatrix),$.projectionMatrixInverse.copy(nt.projectionMatrixInverse),$.isPerspectiveCamera&&($.fov=sl*2*Math.atan(1/$.projectionMatrix.elements[5]),$.zoom=1)}this.getCamera=function(){return _},this.getFoveation=function(){if(!(u===null&&f===null))return l},this.setFoveation=function($){l=$,u!==null&&(u.fixedFoveation=$),f!==null&&f.fixedFoveation!==void 0&&(f.fixedFoveation=$)},this.hasDepthSensing=function(){return y.texture!==null},this.getDepthSensingMesh=function(){return y.getMesh(_)};let st=null;function yt($,nt){if(h=nt.getViewerPose(c||o),g=nt,h!==null){let ft=h.views;f!==null&&(t.setRenderTargetFramebuffer(w,f.framebuffer),t.setRenderTarget(w));let ct=!1;ft.length!==_.cameras.length&&(_.cameras.length=0,ct=!0);for(let Lt=0;Lt<ft.length;Lt++){let Et=ft[Lt],Kt=null;if(f!==null)Kt=f.getViewport(Et);else{let ue=d.getViewSubImage(u,Et);Kt=ue.viewport,Lt===0&&(t.setRenderTargetTextures(w,ue.colorTexture,u.ignoreDepthValues?void 0:ue.depthStencilTexture),t.setRenderTarget(w))}let Ht=x[Lt];Ht===void 0&&(Ht=new Fe,Ht.layers.enable(Lt),Ht.viewport=new re,x[Lt]=Ht),Ht.matrix.fromArray(Et.transform.matrix),Ht.matrix.decompose(Ht.position,Ht.quaternion,Ht.scale),Ht.projectionMatrix.fromArray(Et.projectionMatrix),Ht.projectionMatrixInverse.copy(Ht.projectionMatrix).invert(),Ht.viewport.set(Kt.x,Kt.y,Kt.width,Kt.height),Lt===0&&(_.matrix.copy(Ht.matrix),_.matrix.decompose(_.position,_.quaternion,_.scale)),ct===!0&&_.cameras.push(Ht)}let bt=s.enabledFeatures;if(bt&&bt.includes("depth-sensing")){let Lt=d.getDepthInformation(ft[0]);Lt&&Lt.isValid&&Lt.texture&&y.init(t,Lt,s.renderState)}}for(let ft=0;ft<E.length;ft++){let ct=M[ft],bt=E[ft];ct!==null&&bt!==void 0&&bt.update(ct,nt,c||o)}st&&st($,nt),nt.detectedPlanes&&n.dispatchEvent({type:"planesdetected",data:nt}),g=null}let Ft=new Wh;Ft.setAnimationLoop(yt),this.setAnimationLoop=function($){st=$},this.dispose=function(){}}},Di=new gn,ug=new ne;function dg(i,t){function e(m,p){m.matrixAutoUpdate===!0&&m.updateMatrix(),p.value.copy(m.matrix)}function n(m,p){p.color.getRGB(m.fogColor.value,Gh(i)),p.isFog?(m.fogNear.value=p.near,m.fogFar.value=p.far):p.isFogExp2&&(m.fogDensity.value=p.density)}function s(m,p,w,E,M){p.isMeshBasicMaterial||p.isMeshLambertMaterial?r(m,p):p.isMeshToonMaterial?(r(m,p),d(m,p)):p.isMeshPhongMaterial?(r(m,p),h(m,p)):p.isMeshStandardMaterial?(r(m,p),u(m,p),p.isMeshPhysicalMaterial&&f(m,p,M)):p.isMeshMatcapMaterial?(r(m,p),g(m,p)):p.isMeshDepthMaterial?r(m,p):p.isMeshDistanceMaterial?(r(m,p),y(m,p)):p.isMeshNormalMaterial?r(m,p):p.isLineBasicMaterial?(o(m,p),p.isLineDashedMaterial&&a(m,p)):p.isPointsMaterial?l(m,p,w,E):p.isSpriteMaterial?c(m,p):p.isShadowMaterial?(m.color.value.copy(p.color),m.opacity.value=p.opacity):p.isShaderMaterial&&(p.uniformsNeedUpdate=!1)}function r(m,p){m.opacity.value=p.opacity,p.color&&m.diffuse.value.copy(p.color),p.emissive&&m.emissive.value.copy(p.emissive).multiplyScalar(p.emissiveIntensity),p.map&&(m.map.value=p.map,e(p.map,m.mapTransform)),p.alphaMap&&(m.alphaMap.value=p.alphaMap,e(p.alphaMap,m.alphaMapTransform)),p.bumpMap&&(m.bumpMap.value=p.bumpMap,e(p.bumpMap,m.bumpMapTransform),m.bumpScale.value=p.bumpScale,p.side===Re&&(m.bumpScale.value*=-1)),p.normalMap&&(m.normalMap.value=p.normalMap,e(p.normalMap,m.normalMapTransform),m.normalScale.value.copy(p.normalScale),p.side===Re&&m.normalScale.value.negate()),p.displacementMap&&(m.displacementMap.value=p.displacementMap,e(p.displacementMap,m.displacementMapTransform),m.displacementScale.value=p.displacementScale,m.displacementBias.value=p.displacementBias),p.emissiveMap&&(m.emissiveMap.value=p.emissiveMap,e(p.emissiveMap,m.emissiveMapTransform)),p.specularMap&&(m.specularMap.value=p.specularMap,e(p.specularMap,m.specularMapTransform)),p.alphaTest>0&&(m.alphaTest.value=p.alphaTest);let w=t.get(p),E=w.envMap,M=w.envMapRotation;E&&(m.envMap.value=E,Di.copy(M),Di.x*=-1,Di.y*=-1,Di.z*=-1,E.isCubeTexture&&E.isRenderTargetTexture===!1&&(Di.y*=-1,Di.z*=-1),m.envMapRotation.value.setFromMatrix4(ug.makeRotationFromEuler(Di)),m.flipEnvMap.value=E.isCubeTexture&&E.isRenderTargetTexture===!1?-1:1,m.reflectivity.value=p.reflectivity,m.ior.value=p.ior,m.refractionRatio.value=p.refractionRatio),p.lightMap&&(m.lightMap.value=p.lightMap,m.lightMapIntensity.value=p.lightMapIntensity,e(p.lightMap,m.lightMapTransform)),p.aoMap&&(m.aoMap.value=p.aoMap,m.aoMapIntensity.value=p.aoMapIntensity,e(p.aoMap,m.aoMapTransform))}function o(m,p){m.diffuse.value.copy(p.color),m.opacity.value=p.opacity,p.map&&(m.map.value=p.map,e(p.map,m.mapTransform))}function a(m,p){m.dashSize.value=p.dashSize,m.totalSize.value=p.dashSize+p.gapSize,m.scale.value=p.scale}function l(m,p,w,E){m.diffuse.value.copy(p.color),m.opacity.value=p.opacity,m.size.value=p.size*w,m.scale.value=E*.5,p.map&&(m.map.value=p.map,e(p.map,m.uvTransform)),p.alphaMap&&(m.alphaMap.value=p.alphaMap,e(p.alphaMap,m.alphaMapTransform)),p.alphaTest>0&&(m.alphaTest.value=p.alphaTest)}function c(m,p){m.diffuse.value.copy(p.color),m.opacity.value=p.opacity,m.rotation.value=p.rotation,p.map&&(m.map.value=p.map,e(p.map,m.mapTransform)),p.alphaMap&&(m.alphaMap.value=p.alphaMap,e(p.alphaMap,m.alphaMapTransform)),p.alphaTest>0&&(m.alphaTest.value=p.alphaTest)}function h(m,p){m.specular.value.copy(p.specular),m.shininess.value=Math.max(p.shininess,1e-4)}function d(m,p){p.gradientMap&&(m.gradientMap.value=p.gradientMap)}function u(m,p){m.metalness.value=p.metalness,p.metalnessMap&&(m.metalnessMap.value=p.metalnessMap,e(p.metalnessMap,m.metalnessMapTransform)),m.roughness.value=p.roughness,p.roughnessMap&&(m.roughnessMap.value=p.roughnessMap,e(p.roughnessMap,m.roughnessMapTransform)),p.envMap&&(m.envMapIntensity.value=p.envMapIntensity)}function f(m,p,w){m.ior.value=p.ior,p.sheen>0&&(m.sheenColor.value.copy(p.sheenColor).multiplyScalar(p.sheen),m.sheenRoughness.value=p.sheenRoughness,p.sheenColorMap&&(m.sheenColorMap.value=p.sheenColorMap,e(p.sheenColorMap,m.sheenColorMapTransform)),p.sheenRoughnessMap&&(m.sheenRoughnessMap.value=p.sheenRoughnessMap,e(p.sheenRoughnessMap,m.sheenRoughnessMapTransform))),p.clearcoat>0&&(m.clearcoat.value=p.clearcoat,m.clearcoatRoughness.value=p.clearcoatRoughness,p.clearcoatMap&&(m.clearcoatMap.value=p.clearcoatMap,e(p.clearcoatMap,m.clearcoatMapTransform)),p.clearcoatRoughnessMap&&(m.clearcoatRoughnessMap.value=p.clearcoatRoughnessMap,e(p.clearcoatRoughnessMap,m.clearcoatRoughnessMapTransform)),p.clearcoatNormalMap&&(m.clearcoatNormalMap.value=p.clearcoatNormalMap,e(p.clearcoatNormalMap,m.clearcoatNormalMapTransform),m.clearcoatNormalScale.value.copy(p.clearcoatNormalScale),p.side===Re&&m.clearcoatNormalScale.value.negate())),p.dispersion>0&&(m.dispersion.value=p.dispersion),p.iridescence>0&&(m.iridescence.value=p.iridescence,m.iridescenceIOR.value=p.iridescenceIOR,m.iridescenceThicknessMinimum.value=p.iridescenceThicknessRange[0],m.iridescenceThicknessMaximum.value=p.iridescenceThicknessRange[1],p.iridescenceMap&&(m.iridescenceMap.value=p.iridescenceMap,e(p.iridescenceMap,m.iridescenceMapTransform)),p.iridescenceThicknessMap&&(m.iridescenceThicknessMap.value=p.iridescenceThicknessMap,e(p.iridescenceThicknessMap,m.iridescenceThicknessMapTransform))),p.transmission>0&&(m.transmission.value=p.transmission,m.transmissionSamplerMap.value=w.texture,m.transmissionSamplerSize.value.set(w.width,w.height),p.transmissionMap&&(m.transmissionMap.value=p.transmissionMap,e(p.transmissionMap,m.transmissionMapTransform)),m.thickness.value=p.thickness,p.thicknessMap&&(m.thicknessMap.value=p.thicknessMap,e(p.thicknessMap,m.thicknessMapTransform)),m.attenuationDistance.value=p.attenuationDistance,m.attenuationColor.value.copy(p.attenuationColor)),p.anisotropy>0&&(m.anisotropyVector.value.set(p.anisotropy*Math.cos(p.anisotropyRotation),p.anisotropy*Math.sin(p.anisotropyRotation)),p.anisotropyMap&&(m.anisotropyMap.value=p.anisotropyMap,e(p.anisotropyMap,m.anisotropyMapTransform))),m.specularIntensity.value=p.specularIntensity,m.specularColor.value.copy(p.specularColor),p.specularColorMap&&(m.specularColorMap.value=p.specularColorMap,e(p.specularColorMap,m.specularColorMapTransform)),p.specularIntensityMap&&(m.specularIntensityMap.value=p.specularIntensityMap,e(p.specularIntensityMap,m.specularIntensityMapTransform))}function g(m,p){p.matcap&&(m.matcap.value=p.matcap)}function y(m,p){let w=t.get(p).light;m.referencePosition.value.setFromMatrixPosition(w.matrixWorld),m.nearDistance.value=w.shadow.camera.near,m.farDistance.value=w.shadow.camera.far}return{refreshFogUniforms:n,refreshMaterialUniforms:s}}function fg(i,t,e,n){let s={},r={},o=[],a=i.getParameter(i.MAX_UNIFORM_BUFFER_BINDINGS);function l(w,E){let M=E.program;n.uniformBlockBinding(w,M)}function c(w,E){let M=s[w.id];M===void 0&&(g(w),M=h(w),s[w.id]=M,w.addEventListener("dispose",m));let U=E.program;n.updateUBOMapping(w,U);let C=t.render.frame;r[w.id]!==C&&(u(w),r[w.id]=C)}function h(w){let E=d();w.__bindingPointIndex=E;let M=i.createBuffer(),U=w.__size,C=w.usage;return i.bindBuffer(i.UNIFORM_BUFFER,M),i.bufferData(i.UNIFORM_BUFFER,U,C),i.bindBuffer(i.UNIFORM_BUFFER,null),i.bindBufferBase(i.UNIFORM_BUFFER,E,M),M}function d(){for(let w=0;w<a;w++)if(o.indexOf(w)===-1)return o.push(w),w;return console.error("THREE.WebGLRenderer: Maximum number of simultaneously usable uniforms groups reached."),0}function u(w){let E=s[w.id],M=w.uniforms,U=w.__cache;i.bindBuffer(i.UNIFORM_BUFFER,E);for(let C=0,v=M.length;C<v;C++){let b=Array.isArray(M[C])?M[C]:[M[C]];for(let x=0,_=b.length;x<_;x++){let R=b[x];if(f(R,C,x,U)===!0){let I=R.__offset,L=Array.isArray(R.value)?R.value:[R.value],N=0;for(let V=0;V<L.length;V++){let F=L[V],X=y(F);typeof F=="number"||typeof F=="boolean"?(R.__data[0]=F,i.bufferSubData(i.UNIFORM_BUFFER,I+N,R.__data)):F.isMatrix3?(R.__data[0]=F.elements[0],R.__data[1]=F.elements[1],R.__data[2]=F.elements[2],R.__data[3]=0,R.__data[4]=F.elements[3],R.__data[5]=F.elements[4],R.__data[6]=F.elements[5],R.__data[7]=0,R.__data[8]=F.elements[6],R.__data[9]=F.elements[7],R.__data[10]=F.elements[8],R.__data[11]=0):(F.toArray(R.__data,N),N+=X.storage/Float32Array.BYTES_PER_ELEMENT)}i.bufferSubData(i.UNIFORM_BUFFER,I,R.__data)}}}i.bindBuffer(i.UNIFORM_BUFFER,null)}function f(w,E,M,U){let C=w.value,v=E+"_"+M;if(U[v]===void 0)return typeof C=="number"||typeof C=="boolean"?U[v]=C:U[v]=C.clone(),!0;{let b=U[v];if(typeof C=="number"||typeof C=="boolean"){if(b!==C)return U[v]=C,!0}else if(b.equals(C)===!1)return b.copy(C),!0}return!1}function g(w){let E=w.uniforms,M=0,U=16;for(let v=0,b=E.length;v<b;v++){let x=Array.isArray(E[v])?E[v]:[E[v]];for(let _=0,R=x.length;_<R;_++){let I=x[_],L=Array.isArray(I.value)?I.value:[I.value];for(let N=0,V=L.length;N<V;N++){let F=L[N],X=y(F),z=M%U,j=z%X.boundary,it=z+j;M+=j,it!==0&&U-it<X.storage&&(M+=U-it),I.__data=new Float32Array(X.storage/Float32Array.BYTES_PER_ELEMENT),I.__offset=M,M+=X.storage}}}let C=M%U;return C>0&&(M+=U-C),w.__size=M,w.__cache={},this}function y(w){let E={boundary:0,storage:0};return typeof w=="number"||typeof w=="boolean"?(E.boundary=4,E.storage=4):w.isVector2?(E.boundary=8,E.storage=8):w.isVector3||w.isColor?(E.boundary=16,E.storage=12):w.isVector4?(E.boundary=16,E.storage=16):w.isMatrix3?(E.boundary=48,E.storage=48):w.isMatrix4?(E.boundary=64,E.storage=64):w.isTexture?console.warn("THREE.WebGLRenderer: Texture samplers can not be part of an uniforms group."):console.warn("THREE.WebGLRenderer: Unsupported uniform value type.",w),E}function m(w){let E=w.target;E.removeEventListener("dispose",m);let M=o.indexOf(E.__bindingPointIndex);o.splice(M,1),i.deleteBuffer(s[E.id]),delete s[E.id],delete r[E.id]}function p(){for(let w in s)i.deleteBuffer(s[w]);o=[],s={},r={}}return{bind:l,update:c,dispose:p}}var co=class{constructor(t={}){let{canvas:e=gd(),context:n=null,depth:s=!0,stencil:r=!1,alpha:o=!1,antialias:a=!1,premultipliedAlpha:l=!0,preserveDrawingBuffer:c=!1,powerPreference:h="default",failIfMajorPerformanceCaveat:d=!1,reverseDepthBuffer:u=!1}=t;this.isWebGLRenderer=!0;let f;if(n!==null){if(typeof WebGLRenderingContext!="undefined"&&n instanceof WebGLRenderingContext)throw new Error("THREE.WebGLRenderer: WebGL 1 is not supported since r163.");f=n.getContextAttributes().alpha}else f=o;let g=new Uint32Array(4),y=new Int32Array(4),m=null,p=null,w=[],E=[];this.domElement=e,this.debug={checkShaderErrors:!0,onShaderError:null},this.autoClear=!0,this.autoClearColor=!0,this.autoClearDepth=!0,this.autoClearStencil=!0,this.sortObjects=!0,this.clippingPlanes=[],this.localClippingEnabled=!1,this._outputColorSpace=Pe,this.toneMapping=vi,this.toneMappingExposure=1;let M=this,U=!1,C=0,v=0,b=null,x=-1,_=null,R=new re,I=new re,L=null,N=new Ct(0),V=0,F=e.width,X=e.height,z=1,j=null,it=null,st=new re(0,0,F,X),yt=new re(0,0,F,X),Ft=!1,$=new ir,nt=!1,ft=!1,ct=new ne,bt=new ne,Lt=new P,Et=new re,Kt={background:null,fog:null,environment:null,overrideMaterial:null,isScene:!0},Ht=!1;function ue(){return b===null?z:1}let O=n;function be(T,H){return e.getContext(T,H)}try{let T={alpha:!0,depth:s,stencil:r,antialias:a,premultipliedAlpha:l,preserveDrawingBuffer:c,powerPreference:h,failIfMajorPerformanceCaveat:d};if("setAttribute"in e&&e.setAttribute("data-engine",`three.js r${Gl}`),e.addEventListener("webglcontextlost",K,!1),e.addEventListener("webglcontextrestored",vt,!1),e.addEventListener("webglcontextcreationerror",pt,!1),O===null){let H="webgl2";if(O=be(H,T),O===null)throw be(H)?new Error("Error creating WebGL context with your selected attributes."):new Error("Error creating WebGL context.")}}catch(T){throw console.error("THREE.WebGLRenderer: "+T.message),T}let Xt,kt,It,te,Rt,A,S,D,G,Z,W,et,Q,rt,Nt,tt,lt,xt,Mt,_t,$t,Vt,fe,B;function ut(){Xt=new C0(O),Xt.init(),Vt=new ag(O,Xt),kt=new b0(O,Xt,t,Vt),It=new sg(O,Xt),kt.reverseDepthBuffer&&u&&It.buffers.depth.setReversed(!0),te=new L0(O),Rt=new Xm,A=new og(O,Xt,It,Rt,kt,Vt,te),S=new w0(M),D=new R0(M),G=new Bd(O),fe=new M0(O,G),Z=new P0(O,G,te,fe),W=new U0(O,Z,G,te),Mt=new D0(O,kt,A),tt=new E0(Rt),et=new Wm(M,S,D,Xt,kt,fe,tt),Q=new dg(M,Rt),rt=new Ym,Nt=new Qm(Xt),xt=new y0(M,S,D,It,W,f,l),lt=new ng(M,W,kt),B=new fg(O,te,kt,It),_t=new S0(O,Xt,te),$t=new I0(O,Xt,te),te.programs=et.programs,M.capabilities=kt,M.extensions=Xt,M.properties=Rt,M.renderLists=rt,M.shadowMap=lt,M.state=It,M.info=te}ut();let J=new yl(M,O);this.xr=J,this.getContext=function(){return O},this.getContextAttributes=function(){return O.getContextAttributes()},this.forceContextLoss=function(){let T=Xt.get("WEBGL_lose_context");T&&T.loseContext()},this.forceContextRestore=function(){let T=Xt.get("WEBGL_lose_context");T&&T.restoreContext()},this.getPixelRatio=function(){return z},this.setPixelRatio=function(T){T!==void 0&&(z=T,this.setSize(F,X,!1))},this.getSize=function(T){return T.set(F,X)},this.setSize=function(T,H,q=!0){if(J.isPresenting){console.warn("THREE.WebGLRenderer: Can't change size while VR device is presenting.");return}F=T,X=H,e.width=Math.floor(T*z),e.height=Math.floor(H*z),q===!0&&(e.style.width=T+"px",e.style.height=H+"px"),this.setViewport(0,0,T,H)},this.getDrawingBufferSize=function(T){return T.set(F*z,X*z).floor()},this.setDrawingBufferSize=function(T,H,q){F=T,X=H,z=q,e.width=Math.floor(T*q),e.height=Math.floor(H*q),this.setViewport(0,0,T,H)},this.getCurrentViewport=function(T){return T.copy(R)},this.getViewport=function(T){return T.copy(st)},this.setViewport=function(T,H,q,Y){T.isVector4?st.set(T.x,T.y,T.z,T.w):st.set(T,H,q,Y),It.viewport(R.copy(st).multiplyScalar(z).round())},this.getScissor=function(T){return T.copy(yt)},this.setScissor=function(T,H,q,Y){T.isVector4?yt.set(T.x,T.y,T.z,T.w):yt.set(T,H,q,Y),It.scissor(I.copy(yt).multiplyScalar(z).round())},this.getScissorTest=function(){return Ft},this.setScissorTest=function(T){It.setScissorTest(Ft=T)},this.setOpaqueSort=function(T){j=T},this.setTransparentSort=function(T){it=T},this.getClearColor=function(T){return T.copy(xt.getClearColor())},this.setClearColor=function(){xt.setClearColor.apply(xt,arguments)},this.getClearAlpha=function(){return xt.getClearAlpha()},this.setClearAlpha=function(){xt.setClearAlpha.apply(xt,arguments)},this.clear=function(T=!0,H=!0,q=!0){let Y=0;if(T){let k=!1;if(b!==null){let ot=b.texture.format;k=ot===Kl||ot===Jl||ot===Zl}if(k){let ot=b.texture.type,mt=ot===ri||ot===Bi||ot===nr||ot===ys||ot===ql||ot===Yl,wt=xt.getClearColor(),Tt=xt.getClearAlpha(),zt=wt.r,Wt=wt.g,At=wt.b;mt?(g[0]=zt,g[1]=Wt,g[2]=At,g[3]=Tt,O.clearBufferuiv(O.COLOR,0,g)):(y[0]=zt,y[1]=Wt,y[2]=At,y[3]=Tt,O.clearBufferiv(O.COLOR,0,y))}else Y|=O.COLOR_BUFFER_BIT}H&&(Y|=O.DEPTH_BUFFER_BIT),q&&(Y|=O.STENCIL_BUFFER_BIT,this.state.buffers.stencil.setMask(4294967295)),O.clear(Y)},this.clearColor=function(){this.clear(!0,!1,!1)},this.clearDepth=function(){this.clear(!1,!0,!1)},this.clearStencil=function(){this.clear(!1,!1,!0)},this.dispose=function(){e.removeEventListener("webglcontextlost",K,!1),e.removeEventListener("webglcontextrestored",vt,!1),e.removeEventListener("webglcontextcreationerror",pt,!1),rt.dispose(),Nt.dispose(),Rt.dispose(),S.dispose(),D.dispose(),W.dispose(),fe.dispose(),B.dispose(),et.dispose(),J.dispose(),J.removeEventListener("sessionstart",dc),J.removeEventListener("sessionend",fc),Ai.stop()};function K(T){T.preventDefault(),console.log("THREE.WebGLRenderer: Context Lost."),U=!0}function vt(){console.log("THREE.WebGLRenderer: Context Restored."),U=!1;let T=te.autoReset,H=lt.enabled,q=lt.autoUpdate,Y=lt.needsUpdate,k=lt.type;ut(),te.autoReset=T,lt.enabled=H,lt.autoUpdate=q,lt.needsUpdate=Y,lt.type=k}function pt(T){console.error("THREE.WebGLRenderer: A WebGL context could not be created. Reason: ",T.statusMessage)}function Gt(T){let H=T.target;H.removeEventListener("dispose",Gt),ye(H)}function ye(T){Ge(T),Rt.remove(T)}function Ge(T){let H=Rt.get(T).programs;H!==void 0&&(H.forEach(function(q){et.releaseProgram(q)}),T.isShaderMaterial&&et.releaseShaderCache(T))}this.renderBufferDirect=function(T,H,q,Y,k,ot){H===null&&(H=Kt);let mt=k.isMesh&&k.matrixWorld.determinant()<0,wt=Tu(T,H,q,Y,k);It.setMaterial(Y,mt);let Tt=q.index,zt=1;if(Y.wireframe===!0){if(Tt=Z.getWireframeAttribute(q),Tt===void 0)return;zt=2}let Wt=q.drawRange,At=q.attributes.position,ee=Wt.start*zt,pe=(Wt.start+Wt.count)*zt;ot!==null&&(ee=Math.max(ee,ot.start*zt),pe=Math.min(pe,(ot.start+ot.count)*zt)),Tt!==null?(ee=Math.max(ee,0),pe=Math.min(pe,Tt.count)):At!=null&&(ee=Math.max(ee,0),pe=Math.min(pe,At.count));let ge=pe-ee;if(ge<0||ge===1/0)return;fe.setup(k,Y,wt,q,Tt);let tn,ie=_t;if(Tt!==null&&(tn=G.get(Tt),ie=$t,ie.setIndex(tn)),k.isMesh)Y.wireframe===!0?(It.setLineWidth(Y.wireframeLinewidth*ue()),ie.setMode(O.LINES)):ie.setMode(O.TRIANGLES);else if(k.isLine){let Pt=Y.linewidth;Pt===void 0&&(Pt=1),It.setLineWidth(Pt*ue()),k.isLineSegments?ie.setMode(O.LINES):k.isLineLoop?ie.setMode(O.LINE_LOOP):ie.setMode(O.LINE_STRIP)}else k.isPoints?ie.setMode(O.POINTS):k.isSprite&&ie.setMode(O.TRIANGLES);if(k.isBatchedMesh)if(k._multiDrawInstances!==null)ie.renderMultiDrawInstances(k._multiDrawStarts,k._multiDrawCounts,k._multiDrawCount,k._multiDrawInstances);else if(Xt.get("WEBGL_multi_draw"))ie.renderMultiDraw(k._multiDrawStarts,k._multiDrawCounts,k._multiDrawCount);else{let Pt=k._multiDrawStarts,$n=k._multiDrawCounts,se=k._multiDrawCount,En=Tt?G.get(Tt).bytesPerElement:1,Zi=Rt.get(Y).currentProgram.getUniforms();for(let ln=0;ln<se;ln++)Zi.setValue(O,"_gl_DrawID",ln),ie.render(Pt[ln]/En,$n[ln])}else if(k.isInstancedMesh)ie.renderInstances(ee,ge,k.count);else if(q.isInstancedBufferGeometry){let Pt=q._maxInstanceCount!==void 0?q._maxInstanceCount:1/0,$n=Math.min(q.instanceCount,Pt);ie.renderInstances(ee,ge,$n)}else ie.render(ee,ge)};function le(T,H,q){T.transparent===!0&&T.side===de&&T.forceSinglePass===!1?(T.side=Re,T.needsUpdate=!0,xr(T,H,q),T.side=xi,T.needsUpdate=!0,xr(T,H,q),T.side=de):xr(T,H,q)}this.compile=function(T,H,q=null){q===null&&(q=T),p=Nt.get(q),p.init(H),E.push(p),q.traverseVisible(function(k){k.isLight&&k.layers.test(H.layers)&&(p.pushLight(k),k.castShadow&&p.pushShadow(k))}),T!==q&&T.traverseVisible(function(k){k.isLight&&k.layers.test(H.layers)&&(p.pushLight(k),k.castShadow&&p.pushShadow(k))}),p.setupLights();let Y=new Set;return T.traverse(function(k){if(!(k.isMesh||k.isPoints||k.isLine||k.isSprite))return;let ot=k.material;if(ot)if(Array.isArray(ot))for(let mt=0;mt<ot.length;mt++){let wt=ot[mt];le(wt,q,k),Y.add(wt)}else le(ot,q,k),Y.add(ot)}),E.pop(),p=null,Y},this.compileAsync=function(T,H,q=null){let Y=this.compile(T,H,q);return new Promise(k=>{function ot(){if(Y.forEach(function(mt){Rt.get(mt).currentProgram.isReady()&&Y.delete(mt)}),Y.size===0){k(T);return}setTimeout(ot,10)}Xt.get("KHR_parallel_shader_compile")!==null?ot():setTimeout(ot,10)})};let bn=null;function Yn(T){bn&&bn(T)}function dc(){Ai.stop()}function fc(){Ai.start()}let Ai=new Wh;Ai.setAnimationLoop(Yn),typeof self!="undefined"&&Ai.setContext(self),this.setAnimationLoop=function(T){bn=T,J.setAnimationLoop(T),T===null?Ai.stop():Ai.start()},J.addEventListener("sessionstart",dc),J.addEventListener("sessionend",fc),this.render=function(T,H){if(H!==void 0&&H.isCamera!==!0){console.error("THREE.WebGLRenderer.render: camera is not an instance of THREE.Camera.");return}if(U===!0)return;if(T.matrixWorldAutoUpdate===!0&&T.updateMatrixWorld(),H.parent===null&&H.matrixWorldAutoUpdate===!0&&H.updateMatrixWorld(),J.enabled===!0&&J.isPresenting===!0&&(J.cameraAutoUpdate===!0&&J.updateCamera(H),H=J.getCamera()),T.isScene===!0&&T.onBeforeRender(M,T,H,b),p=Nt.get(T,E.length),p.init(H),E.push(p),bt.multiplyMatrices(H.projectionMatrix,H.matrixWorldInverse),$.setFromProjectionMatrix(bt),ft=this.localClippingEnabled,nt=tt.init(this.clippingPlanes,ft),m=rt.get(T,w.length),m.init(),w.push(m),J.enabled===!0&&J.isPresenting===!0){let ot=M.xr.getDepthSensingMesh();ot!==null&&Oo(ot,H,-1/0,M.sortObjects)}Oo(T,H,0,M.sortObjects),m.finish(),M.sortObjects===!0&&m.sort(j,it),Ht=J.enabled===!1||J.isPresenting===!1||J.hasDepthSensing()===!1,Ht&&xt.addToRenderList(m,T),this.info.render.frame++,nt===!0&&tt.beginShadows();let q=p.state.shadowsArray;lt.render(q,T,H),nt===!0&&tt.endShadows(),this.info.autoReset===!0&&this.info.reset();let Y=m.opaque,k=m.transmissive;if(p.setupLights(),H.isArrayCamera){let ot=H.cameras;if(k.length>0)for(let mt=0,wt=ot.length;mt<wt;mt++){let Tt=ot[mt];mc(Y,k,T,Tt)}Ht&&xt.render(T);for(let mt=0,wt=ot.length;mt<wt;mt++){let Tt=ot[mt];pc(m,T,Tt,Tt.viewport)}}else k.length>0&&mc(Y,k,T,H),Ht&&xt.render(T),pc(m,T,H);b!==null&&(A.updateMultisampleRenderTarget(b),A.updateRenderTargetMipmap(b)),T.isScene===!0&&T.onAfterRender(M,T,H),fe.resetDefaultState(),x=-1,_=null,E.pop(),E.length>0?(p=E[E.length-1],nt===!0&&tt.setGlobalState(M.clippingPlanes,p.state.camera)):p=null,w.pop(),w.length>0?m=w[w.length-1]:m=null};function Oo(T,H,q,Y){if(T.visible===!1)return;if(T.layers.test(H.layers)){if(T.isGroup)q=T.renderOrder;else if(T.isLOD)T.autoUpdate===!0&&T.update(H);else if(T.isLight)p.pushLight(T),T.castShadow&&p.pushShadow(T);else if(T.isSprite){if(!T.frustumCulled||$.intersectsSprite(T)){Y&&Et.setFromMatrixPosition(T.matrixWorld).applyMatrix4(bt);let mt=W.update(T),wt=T.material;wt.visible&&m.push(T,mt,wt,q,Et.z,null)}}else if((T.isMesh||T.isLine||T.isPoints)&&(!T.frustumCulled||$.intersectsObject(T))){let mt=W.update(T),wt=T.material;if(Y&&(T.boundingSphere!==void 0?(T.boundingSphere===null&&T.computeBoundingSphere(),Et.copy(T.boundingSphere.center)):(mt.boundingSphere===null&&mt.computeBoundingSphere(),Et.copy(mt.boundingSphere.center)),Et.applyMatrix4(T.matrixWorld).applyMatrix4(bt)),Array.isArray(wt)){let Tt=mt.groups;for(let zt=0,Wt=Tt.length;zt<Wt;zt++){let At=Tt[zt],ee=wt[At.materialIndex];ee&&ee.visible&&m.push(T,mt,ee,q,Et.z,At)}}else wt.visible&&m.push(T,mt,wt,q,Et.z,null)}}let ot=T.children;for(let mt=0,wt=ot.length;mt<wt;mt++)Oo(ot[mt],H,q,Y)}function pc(T,H,q,Y){let k=T.opaque,ot=T.transmissive,mt=T.transparent;p.setupLightsView(q),nt===!0&&tt.setGlobalState(M.clippingPlanes,q),Y&&It.viewport(R.copy(Y)),k.length>0&&vr(k,H,q),ot.length>0&&vr(ot,H,q),mt.length>0&&vr(mt,H,q),It.buffers.depth.setTest(!0),It.buffers.depth.setMask(!0),It.buffers.color.setMask(!0),It.setPolygonOffset(!1)}function mc(T,H,q,Y){if((q.isScene===!0?q.overrideMaterial:null)!==null)return;p.state.transmissionRenderTarget[Y.id]===void 0&&(p.state.transmissionRenderTarget[Y.id]=new oi(1,1,{generateMipmaps:!0,type:Xt.has("EXT_color_buffer_half_float")||Xt.has("EXT_color_buffer_float")?ar:ri,minFilter:ni,samples:4,stencilBuffer:r,resolveDepthBuffer:!1,resolveStencilBuffer:!1,colorSpace:jt.workingColorSpace}));let ot=p.state.transmissionRenderTarget[Y.id],mt=Y.viewport||R;ot.setSize(mt.z,mt.w);let wt=M.getRenderTarget();M.setRenderTarget(ot),M.getClearColor(N),V=M.getClearAlpha(),V<1&&M.setClearColor(16777215,.5),M.clear(),Ht&&xt.render(q);let Tt=M.toneMapping;M.toneMapping=vi;let zt=Y.viewport;if(Y.viewport!==void 0&&(Y.viewport=void 0),p.setupLightsView(Y),nt===!0&&tt.setGlobalState(M.clippingPlanes,Y),vr(T,q,Y),A.updateMultisampleRenderTarget(ot),A.updateRenderTargetMipmap(ot),Xt.has("WEBGL_multisampled_render_to_texture")===!1){let Wt=!1;for(let At=0,ee=H.length;At<ee;At++){let pe=H[At],ge=pe.object,tn=pe.geometry,ie=pe.material,Pt=pe.group;if(ie.side===de&&ge.layers.test(Y.layers)){let $n=ie.side;ie.side=Re,ie.needsUpdate=!0,gc(ge,q,Y,tn,ie,Pt),ie.side=$n,ie.needsUpdate=!0,Wt=!0}}Wt===!0&&(A.updateMultisampleRenderTarget(ot),A.updateRenderTargetMipmap(ot))}M.setRenderTarget(wt),M.setClearColor(N,V),zt!==void 0&&(Y.viewport=zt),M.toneMapping=Tt}function vr(T,H,q){let Y=H.isScene===!0?H.overrideMaterial:null;for(let k=0,ot=T.length;k<ot;k++){let mt=T[k],wt=mt.object,Tt=mt.geometry,zt=Y===null?mt.material:Y,Wt=mt.group;wt.layers.test(q.layers)&&gc(wt,H,q,Tt,zt,Wt)}}function gc(T,H,q,Y,k,ot){T.onBeforeRender(M,H,q,Y,k,ot),T.modelViewMatrix.multiplyMatrices(q.matrixWorldInverse,T.matrixWorld),T.normalMatrix.getNormalMatrix(T.modelViewMatrix),k.onBeforeRender(M,H,q,Y,T,ot),k.transparent===!0&&k.side===de&&k.forceSinglePass===!1?(k.side=Re,k.needsUpdate=!0,M.renderBufferDirect(q,H,Y,k,T,ot),k.side=xi,k.needsUpdate=!0,M.renderBufferDirect(q,H,Y,k,T,ot),k.side=de):M.renderBufferDirect(q,H,Y,k,T,ot),T.onAfterRender(M,H,q,Y,k,ot)}function xr(T,H,q){H.isScene!==!0&&(H=Kt);let Y=Rt.get(T),k=p.state.lights,ot=p.state.shadowsArray,mt=k.state.version,wt=et.getParameters(T,k.state,ot,H,q),Tt=et.getProgramCacheKey(wt),zt=Y.programs;Y.environment=T.isMeshStandardMaterial?H.environment:null,Y.fog=H.fog,Y.envMap=(T.isMeshStandardMaterial?D:S).get(T.envMap||Y.environment),Y.envMapRotation=Y.environment!==null&&T.envMap===null?H.environmentRotation:T.envMapRotation,zt===void 0&&(T.addEventListener("dispose",Gt),zt=new Map,Y.programs=zt);let Wt=zt.get(Tt);if(Wt!==void 0){if(Y.currentProgram===Wt&&Y.lightsStateVersion===mt)return xc(T,wt),Wt}else wt.uniforms=et.getUniforms(T),T.onBeforeCompile(wt,M),Wt=et.acquireProgram(wt,Tt),zt.set(Tt,Wt),Y.uniforms=wt.uniforms;let At=Y.uniforms;return(!T.isShaderMaterial&&!T.isRawShaderMaterial||T.clipping===!0)&&(At.clippingPlanes=tt.uniform),xc(T,wt),Y.needsLights=Ru(T),Y.lightsStateVersion=mt,Y.needsLights&&(At.ambientLightColor.value=k.state.ambient,At.lightProbe.value=k.state.probe,At.directionalLights.value=k.state.directional,At.directionalLightShadows.value=k.state.directionalShadow,At.spotLights.value=k.state.spot,At.spotLightShadows.value=k.state.spotShadow,At.rectAreaLights.value=k.state.rectArea,At.ltc_1.value=k.state.rectAreaLTC1,At.ltc_2.value=k.state.rectAreaLTC2,At.pointLights.value=k.state.point,At.pointLightShadows.value=k.state.pointShadow,At.hemisphereLights.value=k.state.hemi,At.directionalShadowMap.value=k.state.directionalShadowMap,At.directionalShadowMatrix.value=k.state.directionalShadowMatrix,At.spotShadowMap.value=k.state.spotShadowMap,At.spotLightMatrix.value=k.state.spotLightMatrix,At.spotLightMap.value=k.state.spotLightMap,At.pointShadowMap.value=k.state.pointShadowMap,At.pointShadowMatrix.value=k.state.pointShadowMatrix),Y.currentProgram=Wt,Y.uniformsList=null,Wt}function vc(T){if(T.uniformsList===null){let H=T.currentProgram.getUniforms();T.uniformsList=ms.seqWithValue(H.seq,T.uniforms)}return T.uniformsList}function xc(T,H){let q=Rt.get(T);q.outputColorSpace=H.outputColorSpace,q.batching=H.batching,q.batchingColor=H.batchingColor,q.instancing=H.instancing,q.instancingColor=H.instancingColor,q.instancingMorph=H.instancingMorph,q.skinning=H.skinning,q.morphTargets=H.morphTargets,q.morphNormals=H.morphNormals,q.morphColors=H.morphColors,q.morphTargetsCount=H.morphTargetsCount,q.numClippingPlanes=H.numClippingPlanes,q.numIntersection=H.numClipIntersection,q.vertexAlphas=H.vertexAlphas,q.vertexTangents=H.vertexTangents,q.toneMapping=H.toneMapping}function Tu(T,H,q,Y,k){H.isScene!==!0&&(H=Kt),A.resetTextureUnits();let ot=H.fog,mt=Y.isMeshStandardMaterial?H.environment:null,wt=b===null?M.outputColorSpace:b.isXRRenderTarget===!0?b.texture.colorSpace:Ps,Tt=(Y.isMeshStandardMaterial?D:S).get(Y.envMap||mt),zt=Y.vertexColors===!0&&!!q.attributes.color&&q.attributes.color.itemSize===4,Wt=!!q.attributes.tangent&&(!!Y.normalMap||Y.anisotropy>0),At=!!q.morphAttributes.position,ee=!!q.morphAttributes.normal,pe=!!q.morphAttributes.color,ge=vi;Y.toneMapped&&(b===null||b.isXRRenderTarget===!0)&&(ge=M.toneMapping);let tn=q.morphAttributes.position||q.morphAttributes.normal||q.morphAttributes.color,ie=tn!==void 0?tn.length:0,Pt=Rt.get(Y),$n=p.state.lights;if(nt===!0&&(ft===!0||T!==_)){let pn=T===_&&Y.id===x;tt.setState(Y,T,pn)}let se=!1;Y.version===Pt.__version?(Pt.needsLights&&Pt.lightsStateVersion!==$n.state.version||Pt.outputColorSpace!==wt||k.isBatchedMesh&&Pt.batching===!1||!k.isBatchedMesh&&Pt.batching===!0||k.isBatchedMesh&&Pt.batchingColor===!0&&k.colorTexture===null||k.isBatchedMesh&&Pt.batchingColor===!1&&k.colorTexture!==null||k.isInstancedMesh&&Pt.instancing===!1||!k.isInstancedMesh&&Pt.instancing===!0||k.isSkinnedMesh&&Pt.skinning===!1||!k.isSkinnedMesh&&Pt.skinning===!0||k.isInstancedMesh&&Pt.instancingColor===!0&&k.instanceColor===null||k.isInstancedMesh&&Pt.instancingColor===!1&&k.instanceColor!==null||k.isInstancedMesh&&Pt.instancingMorph===!0&&k.morphTexture===null||k.isInstancedMesh&&Pt.instancingMorph===!1&&k.morphTexture!==null||Pt.envMap!==Tt||Y.fog===!0&&Pt.fog!==ot||Pt.numClippingPlanes!==void 0&&(Pt.numClippingPlanes!==tt.numPlanes||Pt.numIntersection!==tt.numIntersection)||Pt.vertexAlphas!==zt||Pt.vertexTangents!==Wt||Pt.morphTargets!==At||Pt.morphNormals!==ee||Pt.morphColors!==pe||Pt.toneMapping!==ge||Pt.morphTargetsCount!==ie)&&(se=!0):(se=!0,Pt.__version=Y.version);let En=Pt.currentProgram;se===!0&&(En=xr(Y,H,k));let Zi=!1,ln=!1,ks=!1,ve=En.getUniforms(),Nn=Pt.uniforms;if(It.useProgram(En.program)&&(Zi=!0,ln=!0,ks=!0),Y.id!==x&&(x=Y.id,ln=!0),Zi||_!==T){It.buffers.depth.getReversed()?(ct.copy(T.projectionMatrix),xd(ct),_d(ct),ve.setValue(O,"projectionMatrix",ct)):ve.setValue(O,"projectionMatrix",T.projectionMatrix),ve.setValue(O,"viewMatrix",T.matrixWorldInverse);let li=ve.map.cameraPosition;li!==void 0&&li.setValue(O,Lt.setFromMatrixPosition(T.matrixWorld)),kt.logarithmicDepthBuffer&&ve.setValue(O,"logDepthBufFC",2/(Math.log(T.far+1)/Math.LN2)),(Y.isMeshPhongMaterial||Y.isMeshToonMaterial||Y.isMeshLambertMaterial||Y.isMeshBasicMaterial||Y.isMeshStandardMaterial||Y.isShaderMaterial)&&ve.setValue(O,"isOrthographic",T.isOrthographicCamera===!0),_!==T&&(_=T,ln=!0,ks=!0)}if(k.isSkinnedMesh){ve.setOptional(O,k,"bindMatrix"),ve.setOptional(O,k,"bindMatrixInverse");let pn=k.skeleton;pn&&(pn.boneTexture===null&&pn.computeBoneTexture(),ve.setValue(O,"boneTexture",pn.boneTexture,A))}k.isBatchedMesh&&(ve.setOptional(O,k,"batchingTexture"),ve.setValue(O,"batchingTexture",k._matricesTexture,A),ve.setOptional(O,k,"batchingIdTexture"),ve.setValue(O,"batchingIdTexture",k._indirectTexture,A),ve.setOptional(O,k,"batchingColorTexture"),k._colorsTexture!==null&&ve.setValue(O,"batchingColorTexture",k._colorsTexture,A));let Vs=q.morphAttributes;if((Vs.position!==void 0||Vs.normal!==void 0||Vs.color!==void 0)&&Mt.update(k,q,En),(ln||Pt.receiveShadow!==k.receiveShadow)&&(Pt.receiveShadow=k.receiveShadow,ve.setValue(O,"receiveShadow",k.receiveShadow)),Y.isMeshGouraudMaterial&&Y.envMap!==null&&(Nn.envMap.value=Tt,Nn.flipEnvMap.value=Tt.isCubeTexture&&Tt.isRenderTargetTexture===!1?-1:1),Y.isMeshStandardMaterial&&Y.envMap===null&&H.environment!==null&&(Nn.envMapIntensity.value=H.environmentIntensity),ln&&(ve.setValue(O,"toneMappingExposure",M.toneMappingExposure),Pt.needsLights&&Au(Nn,ks),ot&&Y.fog===!0&&Q.refreshFogUniforms(Nn,ot),Q.refreshMaterialUniforms(Nn,Y,z,X,p.state.transmissionRenderTarget[T.id]),ms.upload(O,vc(Pt),Nn,A)),Y.isShaderMaterial&&Y.uniformsNeedUpdate===!0&&(ms.upload(O,vc(Pt),Nn,A),Y.uniformsNeedUpdate=!1),Y.isSpriteMaterial&&ve.setValue(O,"center",k.center),ve.setValue(O,"modelViewMatrix",k.modelViewMatrix),ve.setValue(O,"normalMatrix",k.normalMatrix),ve.setValue(O,"modelMatrix",k.matrixWorld),Y.isShaderMaterial||Y.isRawShaderMaterial){let pn=Y.uniformsGroups;for(let li=0,ci=pn.length;li<ci;li++){let _c=pn[li];B.update(_c,En),B.bind(_c,En)}}return En}function Au(T,H){T.ambientLightColor.needsUpdate=H,T.lightProbe.needsUpdate=H,T.directionalLights.needsUpdate=H,T.directionalLightShadows.needsUpdate=H,T.pointLights.needsUpdate=H,T.pointLightShadows.needsUpdate=H,T.spotLights.needsUpdate=H,T.spotLightShadows.needsUpdate=H,T.rectAreaLights.needsUpdate=H,T.hemisphereLights.needsUpdate=H}function Ru(T){return T.isMeshLambertMaterial||T.isMeshToonMaterial||T.isMeshPhongMaterial||T.isMeshStandardMaterial||T.isShadowMaterial||T.isShaderMaterial&&T.lights===!0}this.getActiveCubeFace=function(){return C},this.getActiveMipmapLevel=function(){return v},this.getRenderTarget=function(){return b},this.setRenderTargetTextures=function(T,H,q){Rt.get(T.texture).__webglTexture=H,Rt.get(T.depthTexture).__webglTexture=q;let Y=Rt.get(T);Y.__hasExternalTextures=!0,Y.__autoAllocateDepthBuffer=q===void 0,Y.__autoAllocateDepthBuffer||Xt.has("WEBGL_multisampled_render_to_texture")===!0&&(console.warn("THREE.WebGLRenderer: Render-to-texture extension was disabled because an external texture was provided"),Y.__useRenderToTexture=!1)},this.setRenderTargetFramebuffer=function(T,H){let q=Rt.get(T);q.__webglFramebuffer=H,q.__useDefaultFramebuffer=H===void 0},this.setRenderTarget=function(T,H=0,q=0){b=T,C=H,v=q;let Y=!0,k=null,ot=!1,mt=!1;if(T){let Tt=Rt.get(T);if(Tt.__useDefaultFramebuffer!==void 0)It.bindFramebuffer(O.FRAMEBUFFER,null),Y=!1;else if(Tt.__webglFramebuffer===void 0)A.setupRenderTarget(T);else if(Tt.__hasExternalTextures)A.rebindTextures(T,Rt.get(T.texture).__webglTexture,Rt.get(T.depthTexture).__webglTexture);else if(T.depthBuffer){let At=T.depthTexture;if(Tt.__boundDepthTexture!==At){if(At!==null&&Rt.has(At)&&(T.width!==At.image.width||T.height!==At.image.height))throw new Error("WebGLRenderTarget: Attached DepthTexture is initialized to the incorrect size.");A.setupDepthRenderbuffer(T)}}let zt=T.texture;(zt.isData3DTexture||zt.isDataArrayTexture||zt.isCompressedArrayTexture)&&(mt=!0);let Wt=Rt.get(T).__webglFramebuffer;T.isWebGLCubeRenderTarget?(Array.isArray(Wt[H])?k=Wt[H][q]:k=Wt[H],ot=!0):T.samples>0&&A.useMultisampledRTT(T)===!1?k=Rt.get(T).__webglMultisampledFramebuffer:Array.isArray(Wt)?k=Wt[q]:k=Wt,R.copy(T.viewport),I.copy(T.scissor),L=T.scissorTest}else R.copy(st).multiplyScalar(z).floor(),I.copy(yt).multiplyScalar(z).floor(),L=Ft;if(It.bindFramebuffer(O.FRAMEBUFFER,k)&&Y&&It.drawBuffers(T,k),It.viewport(R),It.scissor(I),It.setScissorTest(L),ot){let Tt=Rt.get(T.texture);O.framebufferTexture2D(O.FRAMEBUFFER,O.COLOR_ATTACHMENT0,O.TEXTURE_CUBE_MAP_POSITIVE_X+H,Tt.__webglTexture,q)}else if(mt){let Tt=Rt.get(T.texture),zt=H||0;O.framebufferTextureLayer(O.FRAMEBUFFER,O.COLOR_ATTACHMENT0,Tt.__webglTexture,q||0,zt)}x=-1},this.readRenderTargetPixels=function(T,H,q,Y,k,ot,mt){if(!(T&&T.isWebGLRenderTarget)){console.error("THREE.WebGLRenderer.readRenderTargetPixels: renderTarget is not THREE.WebGLRenderTarget.");return}let wt=Rt.get(T).__webglFramebuffer;if(T.isWebGLCubeRenderTarget&&mt!==void 0&&(wt=wt[mt]),wt){It.bindFramebuffer(O.FRAMEBUFFER,wt);try{let Tt=T.texture,zt=Tt.format,Wt=Tt.type;if(!kt.textureFormatReadable(zt)){console.error("THREE.WebGLRenderer.readRenderTargetPixels: renderTarget is not in RGBA or implementation defined format.");return}if(!kt.textureTypeReadable(Wt)){console.error("THREE.WebGLRenderer.readRenderTargetPixels: renderTarget is not in UnsignedByteType or implementation defined type.");return}H>=0&&H<=T.width-Y&&q>=0&&q<=T.height-k&&O.readPixels(H,q,Y,k,Vt.convert(zt),Vt.convert(Wt),ot)}finally{let Tt=b!==null?Rt.get(b).__webglFramebuffer:null;It.bindFramebuffer(O.FRAMEBUFFER,Tt)}}},this.readRenderTargetPixelsAsync=async function(T,H,q,Y,k,ot,mt){if(!(T&&T.isWebGLRenderTarget))throw new Error("THREE.WebGLRenderer.readRenderTargetPixels: renderTarget is not THREE.WebGLRenderTarget.");let wt=Rt.get(T).__webglFramebuffer;if(T.isWebGLCubeRenderTarget&&mt!==void 0&&(wt=wt[mt]),wt){let Tt=T.texture,zt=Tt.format,Wt=Tt.type;if(!kt.textureFormatReadable(zt))throw new Error("THREE.WebGLRenderer.readRenderTargetPixelsAsync: renderTarget is not in RGBA or implementation defined format.");if(!kt.textureTypeReadable(Wt))throw new Error("THREE.WebGLRenderer.readRenderTargetPixelsAsync: renderTarget is not in UnsignedByteType or implementation defined type.");if(H>=0&&H<=T.width-Y&&q>=0&&q<=T.height-k){It.bindFramebuffer(O.FRAMEBUFFER,wt);let At=O.createBuffer();O.bindBuffer(O.PIXEL_PACK_BUFFER,At),O.bufferData(O.PIXEL_PACK_BUFFER,ot.byteLength,O.STREAM_READ),O.readPixels(H,q,Y,k,Vt.convert(zt),Vt.convert(Wt),0);let ee=b!==null?Rt.get(b).__webglFramebuffer:null;It.bindFramebuffer(O.FRAMEBUFFER,ee);let pe=O.fenceSync(O.SYNC_GPU_COMMANDS_COMPLETE,0);return O.flush(),await vd(O,pe,4),O.bindBuffer(O.PIXEL_PACK_BUFFER,At),O.getBufferSubData(O.PIXEL_PACK_BUFFER,0,ot),O.deleteBuffer(At),O.deleteSync(pe),ot}else throw new Error("THREE.WebGLRenderer.readRenderTargetPixelsAsync: requested read bounds are out of range.")}},this.copyFramebufferToTexture=function(T,H=null,q=0){T.isTexture!==!0&&(Ks("WebGLRenderer: copyFramebufferToTexture function signature has changed."),H=arguments[0]||null,T=arguments[1]);let Y=Math.pow(2,-q),k=Math.floor(T.image.width*Y),ot=Math.floor(T.image.height*Y),mt=H!==null?H.x:0,wt=H!==null?H.y:0;A.setTexture2D(T,0),O.copyTexSubImage2D(O.TEXTURE_2D,q,0,0,mt,wt,k,ot),It.unbindTexture()},this.copyTextureToTexture=function(T,H,q=null,Y=null,k=0){T.isTexture!==!0&&(Ks("WebGLRenderer: copyTextureToTexture function signature has changed."),Y=arguments[0]||null,T=arguments[1],H=arguments[2],k=arguments[3]||0,q=null);let ot,mt,wt,Tt,zt,Wt,At,ee,pe,ge=T.isCompressedTexture?T.mipmaps[k]:T.image;q!==null?(ot=q.max.x-q.min.x,mt=q.max.y-q.min.y,wt=q.isBox3?q.max.z-q.min.z:1,Tt=q.min.x,zt=q.min.y,Wt=q.isBox3?q.min.z:0):(ot=ge.width,mt=ge.height,wt=ge.depth||1,Tt=0,zt=0,Wt=0),Y!==null?(At=Y.x,ee=Y.y,pe=Y.z):(At=0,ee=0,pe=0);let tn=Vt.convert(H.format),ie=Vt.convert(H.type),Pt;H.isData3DTexture?(A.setTexture3D(H,0),Pt=O.TEXTURE_3D):H.isDataArrayTexture||H.isCompressedArrayTexture?(A.setTexture2DArray(H,0),Pt=O.TEXTURE_2D_ARRAY):(A.setTexture2D(H,0),Pt=O.TEXTURE_2D),O.pixelStorei(O.UNPACK_FLIP_Y_WEBGL,H.flipY),O.pixelStorei(O.UNPACK_PREMULTIPLY_ALPHA_WEBGL,H.premultiplyAlpha),O.pixelStorei(O.UNPACK_ALIGNMENT,H.unpackAlignment);let $n=O.getParameter(O.UNPACK_ROW_LENGTH),se=O.getParameter(O.UNPACK_IMAGE_HEIGHT),En=O.getParameter(O.UNPACK_SKIP_PIXELS),Zi=O.getParameter(O.UNPACK_SKIP_ROWS),ln=O.getParameter(O.UNPACK_SKIP_IMAGES);O.pixelStorei(O.UNPACK_ROW_LENGTH,ge.width),O.pixelStorei(O.UNPACK_IMAGE_HEIGHT,ge.height),O.pixelStorei(O.UNPACK_SKIP_PIXELS,Tt),O.pixelStorei(O.UNPACK_SKIP_ROWS,zt),O.pixelStorei(O.UNPACK_SKIP_IMAGES,Wt);let ks=T.isDataArrayTexture||T.isData3DTexture,ve=H.isDataArrayTexture||H.isData3DTexture;if(T.isRenderTargetTexture||T.isDepthTexture){let Nn=Rt.get(T),Vs=Rt.get(H),pn=Rt.get(Nn.__renderTarget),li=Rt.get(Vs.__renderTarget);It.bindFramebuffer(O.READ_FRAMEBUFFER,pn.__webglFramebuffer),It.bindFramebuffer(O.DRAW_FRAMEBUFFER,li.__webglFramebuffer);for(let ci=0;ci<wt;ci++)ks&&O.framebufferTextureLayer(O.READ_FRAMEBUFFER,O.COLOR_ATTACHMENT0,Rt.get(T).__webglTexture,k,Wt+ci),T.isDepthTexture?(ve&&O.framebufferTextureLayer(O.DRAW_FRAMEBUFFER,O.COLOR_ATTACHMENT0,Rt.get(H).__webglTexture,k,pe+ci),O.blitFramebuffer(Tt,zt,ot,mt,At,ee,ot,mt,O.DEPTH_BUFFER_BIT,O.NEAREST)):ve?O.copyTexSubImage3D(Pt,k,At,ee,pe+ci,Tt,zt,ot,mt):O.copyTexSubImage2D(Pt,k,At,ee,pe+ci,Tt,zt,ot,mt);It.bindFramebuffer(O.READ_FRAMEBUFFER,null),It.bindFramebuffer(O.DRAW_FRAMEBUFFER,null)}else ve?T.isDataTexture||T.isData3DTexture?O.texSubImage3D(Pt,k,At,ee,pe,ot,mt,wt,tn,ie,ge.data):H.isCompressedArrayTexture?O.compressedTexSubImage3D(Pt,k,At,ee,pe,ot,mt,wt,tn,ge.data):O.texSubImage3D(Pt,k,At,ee,pe,ot,mt,wt,tn,ie,ge):T.isDataTexture?O.texSubImage2D(O.TEXTURE_2D,k,At,ee,ot,mt,tn,ie,ge.data):T.isCompressedTexture?O.compressedTexSubImage2D(O.TEXTURE_2D,k,At,ee,ge.width,ge.height,tn,ge.data):O.texSubImage2D(O.TEXTURE_2D,k,At,ee,ot,mt,tn,ie,ge);O.pixelStorei(O.UNPACK_ROW_LENGTH,$n),O.pixelStorei(O.UNPACK_IMAGE_HEIGHT,se),O.pixelStorei(O.UNPACK_SKIP_PIXELS,En),O.pixelStorei(O.UNPACK_SKIP_ROWS,Zi),O.pixelStorei(O.UNPACK_SKIP_IMAGES,ln),k===0&&H.generateMipmaps&&O.generateMipmap(Pt),It.unbindTexture()},this.copyTextureToTexture3D=function(T,H,q=null,Y=null,k=0){return T.isTexture!==!0&&(Ks("WebGLRenderer: copyTextureToTexture3D function signature has changed."),q=arguments[0]||null,Y=arguments[1]||null,T=arguments[2],H=arguments[3],k=arguments[4]||0),Ks('WebGLRenderer: copyTextureToTexture3D function has been deprecated. Use "copyTextureToTexture" instead.'),this.copyTextureToTexture(T,H,q,Y,k)},this.initRenderTarget=function(T){Rt.get(T).__webglFramebuffer===void 0&&A.setupRenderTarget(T)},this.initTexture=function(T){T.isCubeTexture?A.setTextureCube(T,0):T.isData3DTexture?A.setTexture3D(T,0):T.isDataArrayTexture||T.isCompressedArrayTexture?A.setTexture2DArray(T,0):A.setTexture2D(T,0),It.unbindTexture()},this.resetState=function(){C=0,v=0,b=null,It.reset(),fe.reset()},typeof __THREE_DEVTOOLS__!="undefined"&&__THREE_DEVTOOLS__.dispatchEvent(new CustomEvent("observe",{detail:this}))}get coordinateSystem(){return ii}get outputColorSpace(){return this._outputColorSpace}set outputColorSpace(t){this._outputColorSpace=t;let e=this.getContext();e.drawingBufferColorspace=jt._getDrawingBufferColorSpace(t),e.unpackColorSpace=jt._getUnpackColorSpace()}};var vn=class extends Ze{constructor(){super(),this.isScene=!0,this.type="Scene",this.background=null,this.environment=null,this.fog=null,this.backgroundBlurriness=0,this.backgroundIntensity=1,this.backgroundRotation=new gn,this.environmentIntensity=1,this.environmentRotation=new gn,this.overrideMaterial=null,typeof __THREE_DEVTOOLS__!="undefined"&&__THREE_DEVTOOLS__.dispatchEvent(new CustomEvent("observe",{detail:this}))}copy(t,e){return super.copy(t,e),t.background!==null&&(this.background=t.background.clone()),t.environment!==null&&(this.environment=t.environment.clone()),t.fog!==null&&(this.fog=t.fog.clone()),this.backgroundBlurriness=t.backgroundBlurriness,this.backgroundIntensity=t.backgroundIntensity,this.backgroundRotation.copy(t.backgroundRotation),this.environmentIntensity=t.environmentIntensity,this.environmentRotation.copy(t.environmentRotation),t.overrideMaterial!==null&&(this.overrideMaterial=t.overrideMaterial.clone()),this.matrixAutoUpdate=t.matrixAutoUpdate,this}toJSON(t){let e=super.toJSON(t);return this.fog!==null&&(e.object.fog=this.fog.toJSON()),this.backgroundBlurriness>0&&(e.object.backgroundBlurriness=this.backgroundBlurriness),this.backgroundIntensity!==1&&(e.object.backgroundIntensity=this.backgroundIntensity),e.object.backgroundRotation=this.backgroundRotation.toArray(),this.environmentIntensity!==1&&(e.object.environmentIntensity=this.environmentIntensity),e.object.environmentRotation=this.environmentRotation.toArray(),e}};var Ml=class extends nn{constructor(t=null,e=1,n=1,s,r,o,a,l,c=un,h=un,d,u){super(null,o,a,l,c,h,s,r,d,u),this.isDataTexture=!0,this.image={data:t,width:e,height:n},this.generateMipmaps=!1,this.flipY=!1,this.unpackAlignment=1}};var ho=class extends Ut{constructor(t,e,n,s=1){super(t,e,n),this.isInstancedBufferAttribute=!0,this.meshPerAttribute=s}copy(t){return super.copy(t),this.meshPerAttribute=t.meshPerAttribute,this}toJSON(){let t=super.toJSON();return t.meshPerAttribute=this.meshPerAttribute,t.isInstancedBufferAttribute=!0,t}},hs=new ne,_h=new ne,Hr=[],yh=new Pn,pg=new ne,$s=new at,Zs=new yi,uo=class extends at{constructor(t,e,n){super(t,e),this.isInstancedMesh=!0,this.instanceMatrix=new ho(new Float32Array(n*16),16),this.instanceColor=null,this.morphTexture=null,this.count=n,this.boundingBox=null,this.boundingSphere=null;for(let s=0;s<n;s++)this.setMatrixAt(s,pg)}computeBoundingBox(){let t=this.geometry,e=this.count;this.boundingBox===null&&(this.boundingBox=new Pn),t.boundingBox===null&&t.computeBoundingBox(),this.boundingBox.makeEmpty();for(let n=0;n<e;n++)this.getMatrixAt(n,hs),yh.copy(t.boundingBox).applyMatrix4(hs),this.boundingBox.union(yh)}computeBoundingSphere(){let t=this.geometry,e=this.count;this.boundingSphere===null&&(this.boundingSphere=new yi),t.boundingSphere===null&&t.computeBoundingSphere(),this.boundingSphere.makeEmpty();for(let n=0;n<e;n++)this.getMatrixAt(n,hs),Zs.copy(t.boundingSphere).applyMatrix4(hs),this.boundingSphere.union(Zs)}copy(t,e){return super.copy(t,e),this.instanceMatrix.copy(t.instanceMatrix),t.morphTexture!==null&&(this.morphTexture=t.morphTexture.clone()),t.instanceColor!==null&&(this.instanceColor=t.instanceColor.clone()),this.count=t.count,t.boundingBox!==null&&(this.boundingBox=t.boundingBox.clone()),t.boundingSphere!==null&&(this.boundingSphere=t.boundingSphere.clone()),this}getColorAt(t,e){e.fromArray(this.instanceColor.array,t*3)}getMatrixAt(t,e){e.fromArray(this.instanceMatrix.array,t*16)}getMorphAt(t,e){let n=e.morphTargetInfluences,s=this.morphTexture.source.data.data,r=n.length+1,o=t*r+1;for(let a=0;a<n.length;a++)n[a]=s[o+a]}raycast(t,e){let n=this.matrixWorld,s=this.count;if($s.geometry=this.geometry,$s.material=this.material,$s.material!==void 0&&(this.boundingSphere===null&&this.computeBoundingSphere(),Zs.copy(this.boundingSphere),Zs.applyMatrix4(n),t.ray.intersectsSphere(Zs)!==!1))for(let r=0;r<s;r++){this.getMatrixAt(r,hs),_h.multiplyMatrices(n,hs),$s.matrixWorld=_h,$s.raycast(t,Hr);for(let o=0,a=Hr.length;o<a;o++){let l=Hr[o];l.instanceId=r,l.object=this,e.push(l)}Hr.length=0}}setColorAt(t,e){this.instanceColor===null&&(this.instanceColor=new ho(new Float32Array(this.instanceMatrix.count*3).fill(1),3)),e.toArray(this.instanceColor.array,t*3)}setMatrixAt(t,e){e.toArray(this.instanceMatrix.array,t*16)}setMorphAt(t,e){let n=e.morphTargetInfluences,s=n.length+1;this.morphTexture===null&&(this.morphTexture=new Ml(new Float32Array(s*this.count),s,this.count,$l,zn));let r=this.morphTexture.source.data.data,o=0;for(let c=0;c<n.length;c++)o+=n[c];let a=this.geometry.morphTargetsRelative?1:1-o,l=s*t;r[l]=a,r.set(n,l+1)}updateMorphTargets(){}dispose(){return this.dispatchEvent({type:"dispose"}),this.morphTexture!==null&&(this.morphTexture.dispose(),this.morphTexture=null),this}};var Sl=class extends Mi{static get type(){return"PointsMaterial"}constructor(t){super(),this.isPointsMaterial=!0,this.color=new Ct(16777215),this.map=null,this.alphaMap=null,this.size=1,this.sizeAttenuation=!0,this.fog=!0,this.setValues(t)}copy(t){return super.copy(t),this.color.copy(t.color),this.map=t.map,this.alphaMap=t.alphaMap,this.size=t.size,this.sizeAttenuation=t.sizeAttenuation,this.fog=t.fog,this}},Mh=new ne,bl=new no,kr=new yi,Vr=new P,ze=class extends Ze{constructor(t=new Yt,e=new Sl){super(),this.isPoints=!0,this.type="Points",this.geometry=t,this.material=e,this.updateMorphTargets()}copy(t,e){return super.copy(t,e),this.material=Array.isArray(t.material)?t.material.slice():t.material,this.geometry=t.geometry,this}raycast(t,e){let n=this.geometry,s=this.matrixWorld,r=t.params.Points.threshold,o=n.drawRange;if(n.boundingSphere===null&&n.computeBoundingSphere(),kr.copy(n.boundingSphere),kr.applyMatrix4(s),kr.radius+=r,t.ray.intersectsSphere(kr)===!1)return;Mh.copy(s).invert(),bl.copy(t.ray).applyMatrix4(Mh);let a=r/((this.scale.x+this.scale.y+this.scale.z)/3),l=a*a,c=n.index,d=n.attributes.position;if(c!==null){let u=Math.max(0,o.start),f=Math.min(c.count,o.start+o.count);for(let g=u,y=f;g<y;g++){let m=c.getX(g);Vr.fromBufferAttribute(d,m),Sh(Vr,m,l,s,t,e,this)}}else{let u=Math.max(0,o.start),f=Math.min(d.count,o.start+o.count);for(let g=u,y=f;g<y;g++)Vr.fromBufferAttribute(d,g),Sh(Vr,g,l,s,t,e,this)}}updateMorphTargets(){let e=this.geometry.morphAttributes,n=Object.keys(e);if(n.length>0){let s=e[n[0]];if(s!==void 0){this.morphTargetInfluences=[],this.morphTargetDictionary={};for(let r=0,o=s.length;r<o;r++){let a=s[r].name||String(r);this.morphTargetInfluences.push(0),this.morphTargetDictionary[a]=r}}}}};function Sh(i,t,e,n,s,r,o){let a=bl.distanceSqToPoint(i);if(a<e){let l=new P;bl.closestPointToPoint(i,l),l.applyMatrix4(n);let c=s.ray.origin.distanceTo(l);if(c<s.near||c>s.far)return;r.push({distance:c,distanceToRay:Math.sqrt(a),point:l,index:t,face:null,faceIndex:null,barycoord:null,object:o})}}var ai=class extends nn{constructor(t,e,n,s,r,o,a,l,c){super(t,e,n,s,r,o,a,l,c),this.isCanvasTexture=!0,this.needsUpdate=!0}},xn=class{constructor(){this.type="Curve",this.arcLengthDivisions=200}getPoint(){return console.warn("THREE.Curve: .getPoint() not implemented."),null}getPointAt(t,e){let n=this.getUtoTmapping(t);return this.getPoint(n,e)}getPoints(t=5){let e=[];for(let n=0;n<=t;n++)e.push(this.getPoint(n/t));return e}getSpacedPoints(t=5){let e=[];for(let n=0;n<=t;n++)e.push(this.getPointAt(n/t));return e}getLength(){let t=this.getLengths();return t[t.length-1]}getLengths(t=this.arcLengthDivisions){if(this.cacheArcLengths&&this.cacheArcLengths.length===t+1&&!this.needsUpdate)return this.cacheArcLengths;this.needsUpdate=!1;let e=[],n,s=this.getPoint(0),r=0;e.push(0);for(let o=1;o<=t;o++)n=this.getPoint(o/t),r+=n.distanceTo(s),e.push(r),s=n;return this.cacheArcLengths=e,e}updateArcLengths(){this.needsUpdate=!0,this.getLengths()}getUtoTmapping(t,e){let n=this.getLengths(),s=0,r=n.length,o;e?o=e:o=t*n[r-1];let a=0,l=r-1,c;for(;a<=l;)if(s=Math.floor(a+(l-a)/2),c=n[s]-o,c<0)a=s+1;else if(c>0)l=s-1;else{l=s;break}if(s=l,n[s]===o)return s/(r-1);let h=n[s],u=n[s+1]-h,f=(o-h)/u;return(s+f)/(r-1)}getTangent(t,e){let s=t-1e-4,r=t+1e-4;s<0&&(s=0),r>1&&(r=1);let o=this.getPoint(s),a=this.getPoint(r),l=e||(o.isVector2?new gt:new P);return l.copy(a).sub(o).normalize(),l}getTangentAt(t,e){let n=this.getUtoTmapping(t);return this.getTangent(n,e)}computeFrenetFrames(t,e){let n=new P,s=[],r=[],o=[],a=new P,l=new ne;for(let f=0;f<=t;f++){let g=f/t;s[f]=this.getTangentAt(g,new P)}r[0]=new P,o[0]=new P;let c=Number.MAX_VALUE,h=Math.abs(s[0].x),d=Math.abs(s[0].y),u=Math.abs(s[0].z);h<=c&&(c=h,n.set(1,0,0)),d<=c&&(c=d,n.set(0,1,0)),u<=c&&n.set(0,0,1),a.crossVectors(s[0],n).normalize(),r[0].crossVectors(s[0],a),o[0].crossVectors(s[0],r[0]);for(let f=1;f<=t;f++){if(r[f]=r[f-1].clone(),o[f]=o[f-1].clone(),a.crossVectors(s[f-1],s[f]),a.length()>Number.EPSILON){a.normalize();let g=Math.acos(Ne(s[f-1].dot(s[f]),-1,1));r[f].applyMatrix4(l.makeRotationAxis(a,g))}o[f].crossVectors(s[f],r[f])}if(e===!0){let f=Math.acos(Ne(r[0].dot(r[t]),-1,1));f/=t,s[0].dot(a.crossVectors(r[0],r[t]))>0&&(f=-f);for(let g=1;g<=t;g++)r[g].applyMatrix4(l.makeRotationAxis(s[g],f*g)),o[g].crossVectors(s[g],r[g])}return{tangents:s,normals:r,binormals:o}}clone(){return new this.constructor().copy(this)}copy(t){return this.arcLengthDivisions=t.arcLengthDivisions,this}toJSON(){let t={metadata:{version:4.6,type:"Curve",generator:"Curve.toJSON"}};return t.arcLengthDivisions=this.arcLengthDivisions,t.type=this.type,t}fromJSON(t){return this.arcLengthDivisions=t.arcLengthDivisions,this}},sr=class extends xn{constructor(t=0,e=0,n=1,s=1,r=0,o=Math.PI*2,a=!1,l=0){super(),this.isEllipseCurve=!0,this.type="EllipseCurve",this.aX=t,this.aY=e,this.xRadius=n,this.yRadius=s,this.aStartAngle=r,this.aEndAngle=o,this.aClockwise=a,this.aRotation=l}getPoint(t,e=new gt){let n=e,s=Math.PI*2,r=this.aEndAngle-this.aStartAngle,o=Math.abs(r)<Number.EPSILON;for(;r<0;)r+=s;for(;r>s;)r-=s;r<Number.EPSILON&&(o?r=0:r=s),this.aClockwise===!0&&!o&&(r===s?r=-s:r=r-s);let a=this.aStartAngle+t*r,l=this.aX+this.xRadius*Math.cos(a),c=this.aY+this.yRadius*Math.sin(a);if(this.aRotation!==0){let h=Math.cos(this.aRotation),d=Math.sin(this.aRotation),u=l-this.aX,f=c-this.aY;l=u*h-f*d+this.aX,c=u*d+f*h+this.aY}return n.set(l,c)}copy(t){return super.copy(t),this.aX=t.aX,this.aY=t.aY,this.xRadius=t.xRadius,this.yRadius=t.yRadius,this.aStartAngle=t.aStartAngle,this.aEndAngle=t.aEndAngle,this.aClockwise=t.aClockwise,this.aRotation=t.aRotation,this}toJSON(){let t=super.toJSON();return t.aX=this.aX,t.aY=this.aY,t.xRadius=this.xRadius,t.yRadius=this.yRadius,t.aStartAngle=this.aStartAngle,t.aEndAngle=this.aEndAngle,t.aClockwise=this.aClockwise,t.aRotation=this.aRotation,t}fromJSON(t){return super.fromJSON(t),this.aX=t.aX,this.aY=t.aY,this.xRadius=t.xRadius,this.yRadius=t.yRadius,this.aStartAngle=t.aStartAngle,this.aEndAngle=t.aEndAngle,this.aClockwise=t.aClockwise,this.aRotation=t.aRotation,this}},El=class extends sr{constructor(t,e,n,s,r,o){super(t,e,n,n,s,r,o),this.isArcCurve=!0,this.type="ArcCurve"}};function Ql(){let i=0,t=0,e=0,n=0;function s(r,o,a,l){i=r,t=a,e=-3*r+3*o-2*a-l,n=2*r-2*o+a+l}return{initCatmullRom:function(r,o,a,l,c){s(o,a,c*(a-r),c*(l-o))},initNonuniformCatmullRom:function(r,o,a,l,c,h,d){let u=(o-r)/c-(a-r)/(c+h)+(a-o)/h,f=(a-o)/h-(l-o)/(h+d)+(l-a)/d;u*=h,f*=h,s(o,a,u,f)},calc:function(r){let o=r*r,a=o*r;return i+t*r+e*o+n*a}}}var Gr=new P,pa=new Ql,ma=new Ql,ga=new Ql,rr=class extends xn{constructor(t=[],e=!1,n="centripetal",s=.5){super(),this.isCatmullRomCurve3=!0,this.type="CatmullRomCurve3",this.points=t,this.closed=e,this.curveType=n,this.tension=s}getPoint(t,e=new P){let n=e,s=this.points,r=s.length,o=(r-(this.closed?0:1))*t,a=Math.floor(o),l=o-a;this.closed?a+=a>0?0:(Math.floor(Math.abs(a)/r)+1)*r:l===0&&a===r-1&&(a=r-2,l=1);let c,h;this.closed||a>0?c=s[(a-1)%r]:(Gr.subVectors(s[0],s[1]).add(s[0]),c=Gr);let d=s[a%r],u=s[(a+1)%r];if(this.closed||a+2<r?h=s[(a+2)%r]:(Gr.subVectors(s[r-1],s[r-2]).add(s[r-1]),h=Gr),this.curveType==="centripetal"||this.curveType==="chordal"){let f=this.curveType==="chordal"?.5:.25,g=Math.pow(c.distanceToSquared(d),f),y=Math.pow(d.distanceToSquared(u),f),m=Math.pow(u.distanceToSquared(h),f);y<1e-4&&(y=1),g<1e-4&&(g=y),m<1e-4&&(m=y),pa.initNonuniformCatmullRom(c.x,d.x,u.x,h.x,g,y,m),ma.initNonuniformCatmullRom(c.y,d.y,u.y,h.y,g,y,m),ga.initNonuniformCatmullRom(c.z,d.z,u.z,h.z,g,y,m)}else this.curveType==="catmullrom"&&(pa.initCatmullRom(c.x,d.x,u.x,h.x,this.tension),ma.initCatmullRom(c.y,d.y,u.y,h.y,this.tension),ga.initCatmullRom(c.z,d.z,u.z,h.z,this.tension));return n.set(pa.calc(l),ma.calc(l),ga.calc(l)),n}copy(t){super.copy(t),this.points=[];for(let e=0,n=t.points.length;e<n;e++){let s=t.points[e];this.points.push(s.clone())}return this.closed=t.closed,this.curveType=t.curveType,this.tension=t.tension,this}toJSON(){let t=super.toJSON();t.points=[];for(let e=0,n=this.points.length;e<n;e++){let s=this.points[e];t.points.push(s.toArray())}return t.closed=this.closed,t.curveType=this.curveType,t.tension=this.tension,t}fromJSON(t){super.fromJSON(t),this.points=[];for(let e=0,n=t.points.length;e<n;e++){let s=t.points[e];this.points.push(new P().fromArray(s))}return this.closed=t.closed,this.curveType=t.curveType,this.tension=t.tension,this}};function bh(i,t,e,n,s){let r=(n-t)*.5,o=(s-e)*.5,a=i*i,l=i*a;return(2*e-2*n+r+o)*l+(-3*e+3*n-2*r-o)*a+r*i+e}function mg(i,t){let e=1-i;return e*e*t}function gg(i,t){return 2*(1-i)*i*t}function vg(i,t){return i*i*t}function tr(i,t,e,n){return mg(i,t)+gg(i,e)+vg(i,n)}function xg(i,t){let e=1-i;return e*e*e*t}function _g(i,t){let e=1-i;return 3*e*e*i*t}function yg(i,t){return 3*(1-i)*i*i*t}function Mg(i,t){return i*i*i*t}function er(i,t,e,n,s){return xg(i,t)+_g(i,e)+yg(i,n)+Mg(i,s)}var fo=class extends xn{constructor(t=new gt,e=new gt,n=new gt,s=new gt){super(),this.isCubicBezierCurve=!0,this.type="CubicBezierCurve",this.v0=t,this.v1=e,this.v2=n,this.v3=s}getPoint(t,e=new gt){let n=e,s=this.v0,r=this.v1,o=this.v2,a=this.v3;return n.set(er(t,s.x,r.x,o.x,a.x),er(t,s.y,r.y,o.y,a.y)),n}copy(t){return super.copy(t),this.v0.copy(t.v0),this.v1.copy(t.v1),this.v2.copy(t.v2),this.v3.copy(t.v3),this}toJSON(){let t=super.toJSON();return t.v0=this.v0.toArray(),t.v1=this.v1.toArray(),t.v2=this.v2.toArray(),t.v3=this.v3.toArray(),t}fromJSON(t){return super.fromJSON(t),this.v0.fromArray(t.v0),this.v1.fromArray(t.v1),this.v2.fromArray(t.v2),this.v3.fromArray(t.v3),this}},wl=class extends xn{constructor(t=new P,e=new P,n=new P,s=new P){super(),this.isCubicBezierCurve3=!0,this.type="CubicBezierCurve3",this.v0=t,this.v1=e,this.v2=n,this.v3=s}getPoint(t,e=new P){let n=e,s=this.v0,r=this.v1,o=this.v2,a=this.v3;return n.set(er(t,s.x,r.x,o.x,a.x),er(t,s.y,r.y,o.y,a.y),er(t,s.z,r.z,o.z,a.z)),n}copy(t){return super.copy(t),this.v0.copy(t.v0),this.v1.copy(t.v1),this.v2.copy(t.v2),this.v3.copy(t.v3),this}toJSON(){let t=super.toJSON();return t.v0=this.v0.toArray(),t.v1=this.v1.toArray(),t.v2=this.v2.toArray(),t.v3=this.v3.toArray(),t}fromJSON(t){return super.fromJSON(t),this.v0.fromArray(t.v0),this.v1.fromArray(t.v1),this.v2.fromArray(t.v2),this.v3.fromArray(t.v3),this}},po=class extends xn{constructor(t=new gt,e=new gt){super(),this.isLineCurve=!0,this.type="LineCurve",this.v1=t,this.v2=e}getPoint(t,e=new gt){let n=e;return t===1?n.copy(this.v2):(n.copy(this.v2).sub(this.v1),n.multiplyScalar(t).add(this.v1)),n}getPointAt(t,e){return this.getPoint(t,e)}getTangent(t,e=new gt){return e.subVectors(this.v2,this.v1).normalize()}getTangentAt(t,e){return this.getTangent(t,e)}copy(t){return super.copy(t),this.v1.copy(t.v1),this.v2.copy(t.v2),this}toJSON(){let t=super.toJSON();return t.v1=this.v1.toArray(),t.v2=this.v2.toArray(),t}fromJSON(t){return super.fromJSON(t),this.v1.fromArray(t.v1),this.v2.fromArray(t.v2),this}},Tl=class extends xn{constructor(t=new P,e=new P){super(),this.isLineCurve3=!0,this.type="LineCurve3",this.v1=t,this.v2=e}getPoint(t,e=new P){let n=e;return t===1?n.copy(this.v2):(n.copy(this.v2).sub(this.v1),n.multiplyScalar(t).add(this.v1)),n}getPointAt(t,e){return this.getPoint(t,e)}getTangent(t,e=new P){return e.subVectors(this.v2,this.v1).normalize()}getTangentAt(t,e){return this.getTangent(t,e)}copy(t){return super.copy(t),this.v1.copy(t.v1),this.v2.copy(t.v2),this}toJSON(){let t=super.toJSON();return t.v1=this.v1.toArray(),t.v2=this.v2.toArray(),t}fromJSON(t){return super.fromJSON(t),this.v1.fromArray(t.v1),this.v2.fromArray(t.v2),this}},mo=class extends xn{constructor(t=new gt,e=new gt,n=new gt){super(),this.isQuadraticBezierCurve=!0,this.type="QuadraticBezierCurve",this.v0=t,this.v1=e,this.v2=n}getPoint(t,e=new gt){let n=e,s=this.v0,r=this.v1,o=this.v2;return n.set(tr(t,s.x,r.x,o.x),tr(t,s.y,r.y,o.y)),n}copy(t){return super.copy(t),this.v0.copy(t.v0),this.v1.copy(t.v1),this.v2.copy(t.v2),this}toJSON(){let t=super.toJSON();return t.v0=this.v0.toArray(),t.v1=this.v1.toArray(),t.v2=this.v2.toArray(),t}fromJSON(t){return super.fromJSON(t),this.v0.fromArray(t.v0),this.v1.fromArray(t.v1),this.v2.fromArray(t.v2),this}},go=class extends xn{constructor(t=new P,e=new P,n=new P){super(),this.isQuadraticBezierCurve3=!0,this.type="QuadraticBezierCurve3",this.v0=t,this.v1=e,this.v2=n}getPoint(t,e=new P){let n=e,s=this.v0,r=this.v1,o=this.v2;return n.set(tr(t,s.x,r.x,o.x),tr(t,s.y,r.y,o.y),tr(t,s.z,r.z,o.z)),n}copy(t){return super.copy(t),this.v0.copy(t.v0),this.v1.copy(t.v1),this.v2.copy(t.v2),this}toJSON(){let t=super.toJSON();return t.v0=this.v0.toArray(),t.v1=this.v1.toArray(),t.v2=this.v2.toArray(),t}fromJSON(t){return super.fromJSON(t),this.v0.fromArray(t.v0),this.v1.fromArray(t.v1),this.v2.fromArray(t.v2),this}},Ts=class extends xn{constructor(t=[]){super(),this.isSplineCurve=!0,this.type="SplineCurve",this.points=t}getPoint(t,e=new gt){let n=e,s=this.points,r=(s.length-1)*t,o=Math.floor(r),a=r-o,l=s[o===0?o:o-1],c=s[o],h=s[o>s.length-2?s.length-1:o+1],d=s[o>s.length-3?s.length-1:o+2];return n.set(bh(a,l.x,c.x,h.x,d.x),bh(a,l.y,c.y,h.y,d.y)),n}copy(t){super.copy(t),this.points=[];for(let e=0,n=t.points.length;e<n;e++){let s=t.points[e];this.points.push(s.clone())}return this}toJSON(){let t=super.toJSON();t.points=[];for(let e=0,n=this.points.length;e<n;e++){let s=this.points[e];t.points.push(s.toArray())}return t}fromJSON(t){super.fromJSON(t),this.points=[];for(let e=0,n=t.points.length;e<n;e++){let s=t.points[e];this.points.push(new gt().fromArray(s))}return this}},Al=Object.freeze({__proto__:null,ArcCurve:El,CatmullRomCurve3:rr,CubicBezierCurve:fo,CubicBezierCurve3:wl,EllipseCurve:sr,LineCurve:po,LineCurve3:Tl,QuadraticBezierCurve:mo,QuadraticBezierCurve3:go,SplineCurve:Ts}),Rl=class extends xn{constructor(){super(),this.type="CurvePath",this.curves=[],this.autoClose=!1}add(t){this.curves.push(t)}closePath(){let t=this.curves[0].getPoint(0),e=this.curves[this.curves.length-1].getPoint(1);if(!t.equals(e)){let n=t.isVector2===!0?"LineCurve":"LineCurve3";this.curves.push(new Al[n](e,t))}return this}getPoint(t,e){let n=t*this.getLength(),s=this.getCurveLengths(),r=0;for(;r<s.length;){if(s[r]>=n){let o=s[r]-n,a=this.curves[r],l=a.getLength(),c=l===0?0:1-o/l;return a.getPointAt(c,e)}r++}return null}getLength(){let t=this.getCurveLengths();return t[t.length-1]}updateArcLengths(){this.needsUpdate=!0,this.cacheLengths=null,this.getCurveLengths()}getCurveLengths(){if(this.cacheLengths&&this.cacheLengths.length===this.curves.length)return this.cacheLengths;let t=[],e=0;for(let n=0,s=this.curves.length;n<s;n++)e+=this.curves[n].getLength(),t.push(e);return this.cacheLengths=t,t}getSpacedPoints(t=40){let e=[];for(let n=0;n<=t;n++)e.push(this.getPoint(n/t));return this.autoClose&&e.push(e[0]),e}getPoints(t=12){let e=[],n;for(let s=0,r=this.curves;s<r.length;s++){let o=r[s],a=o.isEllipseCurve?t*2:o.isLineCurve||o.isLineCurve3?1:o.isSplineCurve?t*o.points.length:t,l=o.getPoints(a);for(let c=0;c<l.length;c++){let h=l[c];n&&n.equals(h)||(e.push(h),n=h)}}return this.autoClose&&e.length>1&&!e[e.length-1].equals(e[0])&&e.push(e[0]),e}copy(t){super.copy(t),this.curves=[];for(let e=0,n=t.curves.length;e<n;e++){let s=t.curves[e];this.curves.push(s.clone())}return this.autoClose=t.autoClose,this}toJSON(){let t=super.toJSON();t.autoClose=this.autoClose,t.curves=[];for(let e=0,n=this.curves.length;e<n;e++){let s=this.curves[e];t.curves.push(s.toJSON())}return t}fromJSON(t){super.fromJSON(t),this.autoClose=t.autoClose,this.curves=[];for(let e=0,n=t.curves.length;e<n;e++){let s=t.curves[e];this.curves.push(new Al[s.type]().fromJSON(s))}return this}},Cl=class extends Rl{constructor(t){super(),this.type="Path",this.currentPoint=new gt,t&&this.setFromPoints(t)}setFromPoints(t){this.moveTo(t[0].x,t[0].y);for(let e=1,n=t.length;e<n;e++)this.lineTo(t[e].x,t[e].y);return this}moveTo(t,e){return this.currentPoint.set(t,e),this}lineTo(t,e){let n=new po(this.currentPoint.clone(),new gt(t,e));return this.curves.push(n),this.currentPoint.set(t,e),this}quadraticCurveTo(t,e,n,s){let r=new mo(this.currentPoint.clone(),new gt(t,e),new gt(n,s));return this.curves.push(r),this.currentPoint.set(n,s),this}bezierCurveTo(t,e,n,s,r,o){let a=new fo(this.currentPoint.clone(),new gt(t,e),new gt(n,s),new gt(r,o));return this.curves.push(a),this.currentPoint.set(r,o),this}splineThru(t){let e=[this.currentPoint.clone()].concat(t),n=new Ts(e);return this.curves.push(n),this.currentPoint.copy(t[t.length-1]),this}arc(t,e,n,s,r,o){let a=this.currentPoint.x,l=this.currentPoint.y;return this.absarc(t+a,e+l,n,s,r,o),this}absarc(t,e,n,s,r,o){return this.absellipse(t,e,n,n,s,r,o),this}ellipse(t,e,n,s,r,o,a,l){let c=this.currentPoint.x,h=this.currentPoint.y;return this.absellipse(t+c,e+h,n,s,r,o,a,l),this}absellipse(t,e,n,s,r,o,a,l){let c=new sr(t,e,n,s,r,o,a,l);if(this.curves.length>0){let d=c.getPoint(0);d.equals(this.currentPoint)||this.lineTo(d.x,d.y)}this.curves.push(c);let h=c.getPoint(1);return this.currentPoint.copy(h),this}copy(t){return super.copy(t),this.currentPoint.copy(t.currentPoint),this}toJSON(){let t=super.toJSON();return t.currentPoint=this.currentPoint.toArray(),t}fromJSON(t){return super.fromJSON(t),this.currentPoint.fromArray(t.currentPoint),this}},or=class i extends Yt{constructor(t=[new gt(0,-.5),new gt(.5,0),new gt(0,.5)],e=12,n=0,s=Math.PI*2){super(),this.type="LatheGeometry",this.parameters={points:t,segments:e,phiStart:n,phiLength:s},e=Math.floor(e),s=Ne(s,0,Math.PI*2);let r=[],o=[],a=[],l=[],c=[],h=1/e,d=new P,u=new gt,f=new P,g=new P,y=new P,m=0,p=0;for(let w=0;w<=t.length-1;w++)switch(w){case 0:m=t[w+1].x-t[w].x,p=t[w+1].y-t[w].y,f.x=p*1,f.y=-m,f.z=p*0,y.copy(f),f.normalize(),l.push(f.x,f.y,f.z);break;case t.length-1:l.push(y.x,y.y,y.z);break;default:m=t[w+1].x-t[w].x,p=t[w+1].y-t[w].y,f.x=p*1,f.y=-m,f.z=p*0,g.copy(f),f.x+=y.x,f.y+=y.y,f.z+=y.z,f.normalize(),l.push(f.x,f.y,f.z),y.copy(g)}for(let w=0;w<=e;w++){let E=n+w*h*s,M=Math.sin(E),U=Math.cos(E);for(let C=0;C<=t.length-1;C++){d.x=t[C].x*M,d.y=t[C].y,d.z=t[C].x*U,o.push(d.x,d.y,d.z),u.x=w/e,u.y=C/(t.length-1),a.push(u.x,u.y);let v=l[3*C+0]*M,b=l[3*C+1],x=l[3*C+0]*U;c.push(v,b,x)}}for(let w=0;w<e;w++)for(let E=0;E<t.length-1;E++){let M=E+w*t.length,U=M,C=M+t.length,v=M+t.length+1,b=M+1;r.push(U,C,b),r.push(v,b,C)}this.setIndex(r),this.setAttribute("position",new Bt(o,3)),this.setAttribute("uv",new Bt(a,2)),this.setAttribute("normal",new Bt(c,3))}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}static fromJSON(t){return new i(t.points,t.segments,t.phiStart,t.phiLength)}},Si=class i extends or{constructor(t=1,e=1,n=4,s=8){let r=new Cl;r.absarc(0,-e/2,t,Math.PI*1.5,0),r.absarc(0,e/2,t,0,Math.PI*.5),super(r.getPoints(n),s),this.type="CapsuleGeometry",this.parameters={radius:t,length:e,capSegments:n,radialSegments:s}}static fromJSON(t){return new i(t.radius,t.length,t.capSegments,t.radialSegments)}},vo=class i extends Yt{constructor(t=1,e=32,n=0,s=Math.PI*2){super(),this.type="CircleGeometry",this.parameters={radius:t,segments:e,thetaStart:n,thetaLength:s},e=Math.max(3,e);let r=[],o=[],a=[],l=[],c=new P,h=new gt;o.push(0,0,0),a.push(0,0,1),l.push(.5,.5);for(let d=0,u=3;d<=e;d++,u+=3){let f=n+d/e*s;c.x=t*Math.cos(f),c.y=t*Math.sin(f),o.push(c.x,c.y,c.z),a.push(0,0,1),h.x=(o[u]/t+1)/2,h.y=(o[u+1]/t+1)/2,l.push(h.x,h.y)}for(let d=1;d<=e;d++)r.push(d,d+1,0);this.setIndex(r),this.setAttribute("position",new Bt(o,3)),this.setAttribute("normal",new Bt(a,3)),this.setAttribute("uv",new Bt(l,2))}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}static fromJSON(t){return new i(t.radius,t.segments,t.thetaStart,t.thetaLength)}},Ke=class i extends Yt{constructor(t=1,e=1,n=1,s=32,r=1,o=!1,a=0,l=Math.PI*2){super(),this.type="CylinderGeometry",this.parameters={radiusTop:t,radiusBottom:e,height:n,radialSegments:s,heightSegments:r,openEnded:o,thetaStart:a,thetaLength:l};let c=this;s=Math.floor(s),r=Math.floor(r);let h=[],d=[],u=[],f=[],g=0,y=[],m=n/2,p=0;w(),o===!1&&(t>0&&E(!0),e>0&&E(!1)),this.setIndex(h),this.setAttribute("position",new Bt(d,3)),this.setAttribute("normal",new Bt(u,3)),this.setAttribute("uv",new Bt(f,2));function w(){let M=new P,U=new P,C=0,v=(e-t)/n;for(let b=0;b<=r;b++){let x=[],_=b/r,R=_*(e-t)+t;for(let I=0;I<=s;I++){let L=I/s,N=L*l+a,V=Math.sin(N),F=Math.cos(N);U.x=R*V,U.y=-_*n+m,U.z=R*F,d.push(U.x,U.y,U.z),M.set(V,v,F).normalize(),u.push(M.x,M.y,M.z),f.push(L,1-_),x.push(g++)}y.push(x)}for(let b=0;b<s;b++)for(let x=0;x<r;x++){let _=y[x][b],R=y[x+1][b],I=y[x+1][b+1],L=y[x][b+1];(t>0||x!==0)&&(h.push(_,R,L),C+=3),(e>0||x!==r-1)&&(h.push(R,I,L),C+=3)}c.addGroup(p,C,0),p+=C}function E(M){let U=g,C=new gt,v=new P,b=0,x=M===!0?t:e,_=M===!0?1:-1;for(let I=1;I<=s;I++)d.push(0,m*_,0),u.push(0,_,0),f.push(.5,.5),g++;let R=g;for(let I=0;I<=s;I++){let N=I/s*l+a,V=Math.cos(N),F=Math.sin(N);v.x=x*F,v.y=m*_,v.z=x*V,d.push(v.x,v.y,v.z),u.push(0,_,0),C.x=V*.5+.5,C.y=F*.5*_+.5,f.push(C.x,C.y),g++}for(let I=0;I<s;I++){let L=U+I,N=R+I;M===!0?h.push(N,N+1,L):h.push(N+1,N,L),b+=3}c.addGroup(p,b,M===!0?1:2),p+=b}}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}static fromJSON(t){return new i(t.radiusTop,t.radiusBottom,t.height,t.radialSegments,t.heightSegments,t.openEnded,t.thetaStart,t.thetaLength)}};var Pl=class i extends Yt{constructor(t=[],e=[],n=1,s=0){super(),this.type="PolyhedronGeometry",this.parameters={vertices:t,indices:e,radius:n,detail:s};let r=[],o=[];a(s),c(n),h(),this.setAttribute("position",new Bt(r,3)),this.setAttribute("normal",new Bt(r.slice(),3)),this.setAttribute("uv",new Bt(o,2)),s===0?this.computeVertexNormals():this.normalizeNormals();function a(w){let E=new P,M=new P,U=new P;for(let C=0;C<e.length;C+=3)f(e[C+0],E),f(e[C+1],M),f(e[C+2],U),l(E,M,U,w)}function l(w,E,M,U){let C=U+1,v=[];for(let b=0;b<=C;b++){v[b]=[];let x=w.clone().lerp(M,b/C),_=E.clone().lerp(M,b/C),R=C-b;for(let I=0;I<=R;I++)I===0&&b===C?v[b][I]=x:v[b][I]=x.clone().lerp(_,I/R)}for(let b=0;b<C;b++)for(let x=0;x<2*(C-b)-1;x++){let _=Math.floor(x/2);x%2===0?(u(v[b][_+1]),u(v[b+1][_]),u(v[b][_])):(u(v[b][_+1]),u(v[b+1][_+1]),u(v[b+1][_]))}}function c(w){let E=new P;for(let M=0;M<r.length;M+=3)E.x=r[M+0],E.y=r[M+1],E.z=r[M+2],E.normalize().multiplyScalar(w),r[M+0]=E.x,r[M+1]=E.y,r[M+2]=E.z}function h(){let w=new P;for(let E=0;E<r.length;E+=3){w.x=r[E+0],w.y=r[E+1],w.z=r[E+2];let M=m(w)/2/Math.PI+.5,U=p(w)/Math.PI+.5;o.push(M,1-U)}g(),d()}function d(){for(let w=0;w<o.length;w+=6){let E=o[w+0],M=o[w+2],U=o[w+4],C=Math.max(E,M,U),v=Math.min(E,M,U);C>.9&&v<.1&&(E<.2&&(o[w+0]+=1),M<.2&&(o[w+2]+=1),U<.2&&(o[w+4]+=1))}}function u(w){r.push(w.x,w.y,w.z)}function f(w,E){let M=w*3;E.x=t[M+0],E.y=t[M+1],E.z=t[M+2]}function g(){let w=new P,E=new P,M=new P,U=new P,C=new gt,v=new gt,b=new gt;for(let x=0,_=0;x<r.length;x+=9,_+=6){w.set(r[x+0],r[x+1],r[x+2]),E.set(r[x+3],r[x+4],r[x+5]),M.set(r[x+6],r[x+7],r[x+8]),C.set(o[_+0],o[_+1]),v.set(o[_+2],o[_+3]),b.set(o[_+4],o[_+5]),U.copy(w).add(E).add(M).divideScalar(3);let R=m(U);y(C,_+0,w,R),y(v,_+2,E,R),y(b,_+4,M,R)}}function y(w,E,M,U){U<0&&w.x===1&&(o[E]=w.x-1),M.x===0&&M.z===0&&(o[E]=U/2/Math.PI+.5)}function m(w){return Math.atan2(w.z,-w.x)}function p(w){return Math.atan2(-w.y,Math.sqrt(w.x*w.x+w.z*w.z))}}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}static fromJSON(t){return new i(t.vertices,t.indices,t.radius,t.details)}};var bi=class i extends Pl{constructor(t=1,e=0){let n=(1+Math.sqrt(5))/2,s=[-1,n,0,1,n,0,-1,-n,0,1,-n,0,0,-1,n,0,1,n,0,-1,-n,0,1,-n,n,0,-1,n,0,1,-n,0,-1,-n,0,1],r=[0,11,5,0,5,1,0,1,7,0,7,10,0,10,11,1,5,9,5,11,4,11,10,2,10,7,6,7,1,8,3,9,4,3,4,2,3,2,6,3,6,8,3,8,9,4,9,5,2,4,11,6,2,10,8,6,7,9,8,1];super(s,r,t,e),this.type="IcosahedronGeometry",this.parameters={radius:t,detail:e}}static fromJSON(t){return new i(t.radius,t.detail)}};var kn=class i extends Yt{constructor(t=1,e=32,n=16,s=0,r=Math.PI*2,o=0,a=Math.PI){super(),this.type="SphereGeometry",this.parameters={radius:t,widthSegments:e,heightSegments:n,phiStart:s,phiLength:r,thetaStart:o,thetaLength:a},e=Math.max(3,Math.floor(e)),n=Math.max(2,Math.floor(n));let l=Math.min(o+a,Math.PI),c=0,h=[],d=new P,u=new P,f=[],g=[],y=[],m=[];for(let p=0;p<=n;p++){let w=[],E=p/n,M=0;p===0&&o===0?M=.5/e:p===n&&l===Math.PI&&(M=-.5/e);for(let U=0;U<=e;U++){let C=U/e;d.x=-t*Math.cos(s+C*r)*Math.sin(o+E*a),d.y=t*Math.cos(o+E*a),d.z=t*Math.sin(s+C*r)*Math.sin(o+E*a),g.push(d.x,d.y,d.z),u.copy(d).normalize(),y.push(u.x,u.y,u.z),m.push(C+M,1-E),w.push(c++)}h.push(w)}for(let p=0;p<n;p++)for(let w=0;w<e;w++){let E=h[p][w+1],M=h[p][w],U=h[p+1][w],C=h[p+1][w+1];(p!==0||o>0)&&f.push(E,M,C),(p!==n-1||l<Math.PI)&&f.push(M,U,C)}this.setIndex(f),this.setAttribute("position",new Bt(g,3)),this.setAttribute("normal",new Bt(y,3)),this.setAttribute("uv",new Bt(m,2))}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}static fromJSON(t){return new i(t.radius,t.widthSegments,t.heightSegments,t.phiStart,t.phiLength,t.thetaStart,t.thetaLength)}};var As=class i extends Yt{constructor(t=1,e=.4,n=12,s=48,r=Math.PI*2){super(),this.type="TorusGeometry",this.parameters={radius:t,tube:e,radialSegments:n,tubularSegments:s,arc:r},n=Math.floor(n),s=Math.floor(s);let o=[],a=[],l=[],c=[],h=new P,d=new P,u=new P;for(let f=0;f<=n;f++)for(let g=0;g<=s;g++){let y=g/s*r,m=f/n*Math.PI*2;d.x=(t+e*Math.cos(m))*Math.cos(y),d.y=(t+e*Math.cos(m))*Math.sin(y),d.z=e*Math.sin(m),a.push(d.x,d.y,d.z),h.x=t*Math.cos(y),h.y=t*Math.sin(y),u.subVectors(d,h).normalize(),l.push(u.x,u.y,u.z),c.push(g/s),c.push(f/n)}for(let f=1;f<=n;f++)for(let g=1;g<=s;g++){let y=(s+1)*f+g-1,m=(s+1)*(f-1)+g-1,p=(s+1)*(f-1)+g,w=(s+1)*f+g;o.push(y,m,w),o.push(m,p,w)}this.setIndex(o),this.setAttribute("position",new Bt(a,3)),this.setAttribute("normal",new Bt(l,3)),this.setAttribute("uv",new Bt(c,2))}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}static fromJSON(t){return new i(t.radius,t.tube,t.radialSegments,t.tubularSegments,t.arc)}};var xo=class i extends Yt{constructor(t=new go(new P(-1,-1,0),new P(-1,1,0),new P(1,1,0)),e=64,n=1,s=8,r=!1){super(),this.type="TubeGeometry",this.parameters={path:t,tubularSegments:e,radius:n,radialSegments:s,closed:r};let o=t.computeFrenetFrames(e,r);this.tangents=o.tangents,this.normals=o.normals,this.binormals=o.binormals;let a=new P,l=new P,c=new gt,h=new P,d=[],u=[],f=[],g=[];y(),this.setIndex(g),this.setAttribute("position",new Bt(d,3)),this.setAttribute("normal",new Bt(u,3)),this.setAttribute("uv",new Bt(f,2));function y(){for(let E=0;E<e;E++)m(E);m(r===!1?e:0),w(),p()}function m(E){h=t.getPointAt(E/e,h);let M=o.normals[E],U=o.binormals[E];for(let C=0;C<=s;C++){let v=C/s*Math.PI*2,b=Math.sin(v),x=-Math.cos(v);l.x=x*M.x+b*U.x,l.y=x*M.y+b*U.y,l.z=x*M.z+b*U.z,l.normalize(),u.push(l.x,l.y,l.z),a.x=h.x+n*l.x,a.y=h.y+n*l.y,a.z=h.z+n*l.z,d.push(a.x,a.y,a.z)}}function p(){for(let E=1;E<=e;E++)for(let M=1;M<=s;M++){let U=(s+1)*(E-1)+(M-1),C=(s+1)*E+(M-1),v=(s+1)*E+M,b=(s+1)*(E-1)+M;g.push(U,C,b),g.push(C,v,b)}}function w(){for(let E=0;E<=e;E++)for(let M=0;M<=s;M++)c.x=E/e,c.y=M/s,f.push(c.x,c.y)}}copy(t){return super.copy(t),this.parameters=Object.assign({},t.parameters),this}toJSON(){let t=super.toJSON();return t.path=this.parameters.path.toJSON(),t}static fromJSON(t){return new i(new Al[t.path.type]().fromJSON(t.path),t.tubularSegments,t.radius,t.radialSegments,t.closed)}};var Rs=class extends Mi{static get type(){return"MeshStandardMaterial"}constructor(t){super(),this.isMeshStandardMaterial=!0,this.defines={STANDARD:""},this.color=new Ct(16777215),this.roughness=1,this.metalness=0,this.map=null,this.lightMap=null,this.lightMapIntensity=1,this.aoMap=null,this.aoMapIntensity=1,this.emissive=new Ct(0),this.emissiveIntensity=1,this.emissiveMap=null,this.bumpMap=null,this.bumpScale=1,this.normalMap=null,this.normalMapType=zh,this.normalScale=new gt(1,1),this.displacementMap=null,this.displacementScale=1,this.displacementBias=0,this.roughnessMap=null,this.metalnessMap=null,this.alphaMap=null,this.envMap=null,this.envMapRotation=new gn,this.envMapIntensity=1,this.wireframe=!1,this.wireframeLinewidth=1,this.wireframeLinecap="round",this.wireframeLinejoin="round",this.flatShading=!1,this.fog=!0,this.setValues(t)}copy(t){return super.copy(t),this.defines={STANDARD:""},this.color.copy(t.color),this.roughness=t.roughness,this.metalness=t.metalness,this.map=t.map,this.lightMap=t.lightMap,this.lightMapIntensity=t.lightMapIntensity,this.aoMap=t.aoMap,this.aoMapIntensity=t.aoMapIntensity,this.emissive.copy(t.emissive),this.emissiveMap=t.emissiveMap,this.emissiveIntensity=t.emissiveIntensity,this.bumpMap=t.bumpMap,this.bumpScale=t.bumpScale,this.normalMap=t.normalMap,this.normalMapType=t.normalMapType,this.normalScale.copy(t.normalScale),this.displacementMap=t.displacementMap,this.displacementScale=t.displacementScale,this.displacementBias=t.displacementBias,this.roughnessMap=t.roughnessMap,this.metalnessMap=t.metalnessMap,this.alphaMap=t.alphaMap,this.envMap=t.envMap,this.envMapRotation.copy(t.envMapRotation),this.envMapIntensity=t.envMapIntensity,this.wireframe=t.wireframe,this.wireframeLinewidth=t.wireframeLinewidth,this.wireframeLinecap=t.wireframeLinecap,this.wireframeLinejoin=t.wireframeLinejoin,this.flatShading=t.flatShading,this.fog=t.fog,this}},fn=class extends Rs{static get type(){return"MeshPhysicalMaterial"}constructor(t){super(),this.isMeshPhysicalMaterial=!0,this.defines={STANDARD:"",PHYSICAL:""},this.anisotropyRotation=0,this.anisotropyMap=null,this.clearcoatMap=null,this.clearcoatRoughness=0,this.clearcoatRoughnessMap=null,this.clearcoatNormalScale=new gt(1,1),this.clearcoatNormalMap=null,this.ior=1.5,Object.defineProperty(this,"reflectivity",{get:function(){return Ne(2.5*(this.ior-1)/(this.ior+1),0,1)},set:function(e){this.ior=(1+.4*e)/(1-.4*e)}}),this.iridescenceMap=null,this.iridescenceIOR=1.3,this.iridescenceThicknessRange=[100,400],this.iridescenceThicknessMap=null,this.sheenColor=new Ct(0),this.sheenColorMap=null,this.sheenRoughness=1,this.sheenRoughnessMap=null,this.transmissionMap=null,this.thickness=0,this.thicknessMap=null,this.attenuationDistance=1/0,this.attenuationColor=new Ct(1,1,1),this.specularIntensity=1,this.specularIntensityMap=null,this.specularColor=new Ct(1,1,1),this.specularColorMap=null,this._anisotropy=0,this._clearcoat=0,this._dispersion=0,this._iridescence=0,this._sheen=0,this._transmission=0,this.setValues(t)}get anisotropy(){return this._anisotropy}set anisotropy(t){this._anisotropy>0!=t>0&&this.version++,this._anisotropy=t}get clearcoat(){return this._clearcoat}set clearcoat(t){this._clearcoat>0!=t>0&&this.version++,this._clearcoat=t}get iridescence(){return this._iridescence}set iridescence(t){this._iridescence>0!=t>0&&this.version++,this._iridescence=t}get dispersion(){return this._dispersion}set dispersion(t){this._dispersion>0!=t>0&&this.version++,this._dispersion=t}get sheen(){return this._sheen}set sheen(t){this._sheen>0!=t>0&&this.version++,this._sheen=t}get transmission(){return this._transmission}set transmission(t){this._transmission>0!=t>0&&this.version++,this._transmission=t}copy(t){return super.copy(t),this.defines={STANDARD:"",PHYSICAL:""},this.anisotropy=t.anisotropy,this.anisotropyRotation=t.anisotropyRotation,this.anisotropyMap=t.anisotropyMap,this.clearcoat=t.clearcoat,this.clearcoatMap=t.clearcoatMap,this.clearcoatRoughness=t.clearcoatRoughness,this.clearcoatRoughnessMap=t.clearcoatRoughnessMap,this.clearcoatNormalMap=t.clearcoatNormalMap,this.clearcoatNormalScale.copy(t.clearcoatNormalScale),this.dispersion=t.dispersion,this.ior=t.ior,this.iridescence=t.iridescence,this.iridescenceMap=t.iridescenceMap,this.iridescenceIOR=t.iridescenceIOR,this.iridescenceThicknessRange=[...t.iridescenceThicknessRange],this.iridescenceThicknessMap=t.iridescenceThicknessMap,this.sheen=t.sheen,this.sheenColor.copy(t.sheenColor),this.sheenColorMap=t.sheenColorMap,this.sheenRoughness=t.sheenRoughness,this.sheenRoughnessMap=t.sheenRoughnessMap,this.transmission=t.transmission,this.transmissionMap=t.transmissionMap,this.thickness=t.thickness,this.thicknessMap=t.thicknessMap,this.attenuationDistance=t.attenuationDistance,this.attenuationColor.copy(t.attenuationColor),this.specularIntensity=t.specularIntensity,this.specularIntensityMap=t.specularIntensityMap,this.specularColor.copy(t.specularColor),this.specularColorMap=t.specularColorMap,this}};function Wr(i,t,e){return!i||!e&&i.constructor===t?i:typeof t.BYTES_PER_ELEMENT=="number"?new t(i):Array.prototype.slice.call(i)}function Sg(i){return ArrayBuffer.isView(i)&&!(i instanceof DataView)}var Cs=class{constructor(t,e,n,s){this.parameterPositions=t,this._cachedIndex=0,this.resultBuffer=s!==void 0?s:new e.constructor(n),this.sampleValues=e,this.valueSize=n,this.settings=null,this.DefaultSettings_={}}evaluate(t){let e=this.parameterPositions,n=this._cachedIndex,s=e[n],r=e[n-1];n:{t:{let o;e:{i:if(!(t<s)){for(let a=n+2;;){if(s===void 0){if(t<r)break i;return n=e.length,this._cachedIndex=n,this.copySampleValue_(n-1)}if(n===a)break;if(r=s,s=e[++n],t<s)break t}o=e.length;break e}if(!(t>=r)){let a=e[1];t<a&&(n=2,r=a);for(let l=n-2;;){if(r===void 0)return this._cachedIndex=0,this.copySampleValue_(0);if(n===l)break;if(s=r,r=e[--n-1],t>=r)break t}o=n,n=0;break e}break n}for(;n<o;){let a=n+o>>>1;t<e[a]?o=a:n=a+1}if(s=e[n],r=e[n-1],r===void 0)return this._cachedIndex=0,this.copySampleValue_(0);if(s===void 0)return n=e.length,this._cachedIndex=n,this.copySampleValue_(n-1)}this._cachedIndex=n,this.intervalChanged_(n,r,s)}return this.interpolate_(n,r,t,s)}getSettings_(){return this.settings||this.DefaultSettings_}copySampleValue_(t){let e=this.resultBuffer,n=this.sampleValues,s=this.valueSize,r=t*s;for(let o=0;o!==s;++o)e[o]=n[r+o];return e}interpolate_(){throw new Error("call to abstract method")}intervalChanged_(){}},Il=class extends Cs{constructor(t,e,n,s){super(t,e,n,s),this._weightPrev=-0,this._offsetPrev=-0,this._weightNext=-0,this._offsetNext=-0,this.DefaultSettings_={endingStart:bc,endingEnd:bc}}intervalChanged_(t,e,n){let s=this.parameterPositions,r=t-2,o=t+1,a=s[r],l=s[o];if(a===void 0)switch(this.getSettings_().endingStart){case Ec:r=t,a=2*e-n;break;case wc:r=s.length-2,a=e+s[r]-s[r+1];break;default:r=t,a=n}if(l===void 0)switch(this.getSettings_().endingEnd){case Ec:o=t,l=2*n-e;break;case wc:o=1,l=n+s[1]-s[0];break;default:o=t-1,l=e}let c=(n-e)*.5,h=this.valueSize;this._weightPrev=c/(e-a),this._weightNext=c/(l-n),this._offsetPrev=r*h,this._offsetNext=o*h}interpolate_(t,e,n,s){let r=this.resultBuffer,o=this.sampleValues,a=this.valueSize,l=t*a,c=l-a,h=this._offsetPrev,d=this._offsetNext,u=this._weightPrev,f=this._weightNext,g=(n-e)/(s-e),y=g*g,m=y*g,p=-u*m+2*u*y-u*g,w=(1+u)*m+(-1.5-2*u)*y+(-.5+u)*g+1,E=(-1-f)*m+(1.5+f)*y+.5*g,M=f*m-f*y;for(let U=0;U!==a;++U)r[U]=p*o[h+U]+w*o[c+U]+E*o[l+U]+M*o[d+U];return r}},Ll=class extends Cs{constructor(t,e,n,s){super(t,e,n,s)}interpolate_(t,e,n,s){let r=this.resultBuffer,o=this.sampleValues,a=this.valueSize,l=t*a,c=l-a,h=(n-e)/(s-e),d=1-h;for(let u=0;u!==a;++u)r[u]=o[c+u]*d+o[l+u]*h;return r}},Dl=class extends Cs{constructor(t,e,n,s){super(t,e,n,s)}interpolate_(t){return this.copySampleValue_(t-1)}},In=class{constructor(t,e,n,s){if(t===void 0)throw new Error("THREE.KeyframeTrack: track name is undefined");if(e===void 0||e.length===0)throw new Error("THREE.KeyframeTrack: no keyframes in track named "+t);this.name=t,this.times=Wr(e,this.TimeBufferType),this.values=Wr(n,this.ValueBufferType),this.setInterpolation(s||this.DefaultInterpolation)}static toJSON(t){let e=t.constructor,n;if(e.toJSON!==this.toJSON)n=e.toJSON(t);else{n={name:t.name,times:Wr(t.times,Array),values:Wr(t.values,Array)};let s=t.getInterpolation();s!==t.DefaultInterpolation&&(n.interpolation=s)}return n.type=t.ValueTypeName,n}InterpolantFactoryMethodDiscrete(t){return new Dl(this.times,this.values,this.getValueSize(),t)}InterpolantFactoryMethodLinear(t){return new Ll(this.times,this.values,this.getValueSize(),t)}InterpolantFactoryMethodSmooth(t){return new Il(this.times,this.values,this.getValueSize(),t)}setInterpolation(t){let e;switch(t){case Kr:e=this.InterpolantFactoryMethodDiscrete;break;case il:e=this.InterpolantFactoryMethodLinear;break;case zo:e=this.InterpolantFactoryMethodSmooth;break}if(e===void 0){let n="unsupported interpolation for "+this.ValueTypeName+" keyframe track named "+this.name;if(this.createInterpolant===void 0)if(t!==this.DefaultInterpolation)this.setInterpolation(this.DefaultInterpolation);else throw new Error(n);return console.warn("THREE.KeyframeTrack:",n),this}return this.createInterpolant=e,this}getInterpolation(){switch(this.createInterpolant){case this.InterpolantFactoryMethodDiscrete:return Kr;case this.InterpolantFactoryMethodLinear:return il;case this.InterpolantFactoryMethodSmooth:return zo}}getValueSize(){return this.values.length/this.times.length}shift(t){if(t!==0){let e=this.times;for(let n=0,s=e.length;n!==s;++n)e[n]+=t}return this}scale(t){if(t!==1){let e=this.times;for(let n=0,s=e.length;n!==s;++n)e[n]*=t}return this}trim(t,e){let n=this.times,s=n.length,r=0,o=s-1;for(;r!==s&&n[r]<t;)++r;for(;o!==-1&&n[o]>e;)--o;if(++o,r!==0||o!==s){r>=o&&(o=Math.max(o,1),r=o-1);let a=this.getValueSize();this.times=n.slice(r,o),this.values=this.values.slice(r*a,o*a)}return this}validate(){let t=!0,e=this.getValueSize();e-Math.floor(e)!==0&&(console.error("THREE.KeyframeTrack: Invalid value size in track.",this),t=!1);let n=this.times,s=this.values,r=n.length;r===0&&(console.error("THREE.KeyframeTrack: Track is empty.",this),t=!1);let o=null;for(let a=0;a!==r;a++){let l=n[a];if(typeof l=="number"&&isNaN(l)){console.error("THREE.KeyframeTrack: Time is not a valid number.",this,a,l),t=!1;break}if(o!==null&&o>l){console.error("THREE.KeyframeTrack: Out of order keys.",this,a,l,o),t=!1;break}o=l}if(s!==void 0&&Sg(s))for(let a=0,l=s.length;a!==l;++a){let c=s[a];if(isNaN(c)){console.error("THREE.KeyframeTrack: Value is not a valid number.",this,a,c),t=!1;break}}return t}optimize(){let t=this.times.slice(),e=this.values.slice(),n=this.getValueSize(),s=this.getInterpolation()===zo,r=t.length-1,o=1;for(let a=1;a<r;++a){let l=!1,c=t[a],h=t[a+1];if(c!==h&&(a!==1||c!==t[0]))if(s)l=!0;else{let d=a*n,u=d-n,f=d+n;for(let g=0;g!==n;++g){let y=e[d+g];if(y!==e[u+g]||y!==e[f+g]){l=!0;break}}}if(l){if(a!==o){t[o]=t[a];let d=a*n,u=o*n;for(let f=0;f!==n;++f)e[u+f]=e[d+f]}++o}}if(r>0){t[o]=t[r];for(let a=r*n,l=o*n,c=0;c!==n;++c)e[l+c]=e[a+c];++o}return o!==t.length?(this.times=t.slice(0,o),this.values=e.slice(0,o*n)):(this.times=t,this.values=e),this}clone(){let t=this.times.slice(),e=this.values.slice(),n=this.constructor,s=new n(this.name,t,e);return s.createInterpolant=this.createInterpolant,s}};In.prototype.TimeBufferType=Float32Array;In.prototype.ValueBufferType=Float32Array;In.prototype.DefaultInterpolation=il;var zi=class extends In{constructor(t,e,n){super(t,e,n)}};zi.prototype.ValueTypeName="bool";zi.prototype.ValueBufferType=Array;zi.prototype.DefaultInterpolation=Kr;zi.prototype.InterpolantFactoryMethodLinear=void 0;zi.prototype.InterpolantFactoryMethodSmooth=void 0;var Ul=class extends In{};Ul.prototype.ValueTypeName="color";var Nl=class extends In{};Nl.prototype.ValueTypeName="number";var Fl=class extends Cs{constructor(t,e,n,s){super(t,e,n,s)}interpolate_(t,e,n,s){let r=this.resultBuffer,o=this.sampleValues,a=this.valueSize,l=(n-e)/(s-e),c=t*a;for(let h=c+a;c!==h;c+=4)Hn.slerpFlat(r,0,o,c-a,o,c,l);return r}},_o=class extends In{InterpolantFactoryMethodLinear(t){return new Fl(this.times,this.values,this.getValueSize(),t)}};_o.prototype.ValueTypeName="quaternion";_o.prototype.InterpolantFactoryMethodSmooth=void 0;var Hi=class extends In{constructor(t,e,n){super(t,e,n)}};Hi.prototype.ValueTypeName="string";Hi.prototype.ValueBufferType=Array;Hi.prototype.DefaultInterpolation=Kr;Hi.prototype.InterpolantFactoryMethodLinear=void 0;Hi.prototype.InterpolantFactoryMethodSmooth=void 0;var Ol=class extends In{};Ol.prototype.ValueTypeName="vector";var Bl=class{constructor(t,e,n){let s=this,r=!1,o=0,a=0,l,c=[];this.onStart=void 0,this.onLoad=t,this.onProgress=e,this.onError=n,this.itemStart=function(h){a++,r===!1&&s.onStart!==void 0&&s.onStart(h,o,a),r=!0},this.itemEnd=function(h){o++,s.onProgress!==void 0&&s.onProgress(h,o,a),o===a&&(r=!1,s.onLoad!==void 0&&s.onLoad())},this.itemError=function(h){s.onError!==void 0&&s.onError(h)},this.resolveURL=function(h){return l?l(h):h},this.setURLModifier=function(h){return l=h,this},this.addHandler=function(h,d){return c.push(h,d),this},this.removeHandler=function(h){let d=c.indexOf(h);return d!==-1&&c.splice(d,2),this},this.getHandler=function(h){for(let d=0,u=c.length;d<u;d+=2){let f=c[d],g=c[d+1];if(f.global&&(f.lastIndex=0),f.test(h))return g}return null}}},bg=new Bl,zl=class{constructor(t){this.manager=t!==void 0?t:bg,this.crossOrigin="anonymous",this.withCredentials=!1,this.path="",this.resourcePath="",this.requestHeader={}}load(){}loadAsync(t,e){let n=this;return new Promise(function(s,r){n.load(t,s,e,r)})}parse(){}setCrossOrigin(t){return this.crossOrigin=t,this}setWithCredentials(t){return this.withCredentials=t,this}setPath(t){return this.path=t,this}setResourcePath(t){return this.resourcePath=t,this}setRequestHeader(t){return this.requestHeader=t,this}};zl.DEFAULT_MATERIAL_NAME="__DEFAULT";var yo=class extends Ze{constructor(t,e=1){super(),this.isLight=!0,this.type="Light",this.color=new Ct(t),this.intensity=e}dispose(){}copy(t,e){return super.copy(t,e),this.color.copy(t.color),this.intensity=t.intensity,this}toJSON(t){let e=super.toJSON(t);return e.object.color=this.color.getHex(),e.object.intensity=this.intensity,this.groundColor!==void 0&&(e.object.groundColor=this.groundColor.getHex()),this.distance!==void 0&&(e.object.distance=this.distance),this.angle!==void 0&&(e.object.angle=this.angle),this.decay!==void 0&&(e.object.decay=this.decay),this.penumbra!==void 0&&(e.object.penumbra=this.penumbra),this.shadow!==void 0&&(e.object.shadow=this.shadow.toJSON()),this.target!==void 0&&(e.object.target=this.target.uuid),e}};var va=new ne,Eh=new P,wh=new P,Mo=class{constructor(t){this.camera=t,this.intensity=1,this.bias=0,this.normalBias=0,this.radius=1,this.blurSamples=8,this.mapSize=new gt(512,512),this.map=null,this.mapPass=null,this.matrix=new ne,this.autoUpdate=!0,this.needsUpdate=!1,this._frustum=new ir,this._frameExtents=new gt(1,1),this._viewportCount=1,this._viewports=[new re(0,0,1,1)]}getViewportCount(){return this._viewportCount}getFrustum(){return this._frustum}updateMatrices(t){let e=this.camera,n=this.matrix;Eh.setFromMatrixPosition(t.matrixWorld),e.position.copy(Eh),wh.setFromMatrixPosition(t.target.matrixWorld),e.lookAt(wh),e.updateMatrixWorld(),va.multiplyMatrices(e.projectionMatrix,e.matrixWorldInverse),this._frustum.setFromProjectionMatrix(va),n.set(.5,0,0,.5,0,.5,0,.5,0,0,.5,.5,0,0,0,1),n.multiply(va)}getViewport(t){return this._viewports[t]}getFrameExtents(){return this._frameExtents}dispose(){this.map&&this.map.dispose(),this.mapPass&&this.mapPass.dispose()}copy(t){return this.camera=t.camera.clone(),this.intensity=t.intensity,this.bias=t.bias,this.radius=t.radius,this.mapSize.copy(t.mapSize),this}clone(){return new this.constructor().copy(this)}toJSON(){let t={};return this.intensity!==1&&(t.intensity=this.intensity),this.bias!==0&&(t.bias=this.bias),this.normalBias!==0&&(t.normalBias=this.normalBias),this.radius!==1&&(t.radius=this.radius),(this.mapSize.x!==512||this.mapSize.y!==512)&&(t.mapSize=this.mapSize.toArray()),t.camera=this.camera.toJSON(!1).object,delete t.camera.matrix,t}};var Th=new ne,Js=new P,xa=new P,Hl=class extends Mo{constructor(){super(new Fe(90,1,.5,500)),this.isPointLightShadow=!0,this._frameExtents=new gt(4,2),this._viewportCount=6,this._viewports=[new re(2,1,1,1),new re(0,1,1,1),new re(3,1,1,1),new re(1,1,1,1),new re(3,0,1,1),new re(1,0,1,1)],this._cubeDirections=[new P(1,0,0),new P(-1,0,0),new P(0,0,1),new P(0,0,-1),new P(0,1,0),new P(0,-1,0)],this._cubeUps=[new P(0,1,0),new P(0,1,0),new P(0,1,0),new P(0,1,0),new P(0,0,1),new P(0,0,-1)]}updateMatrices(t,e=0){let n=this.camera,s=this.matrix,r=t.distance||n.far;r!==n.far&&(n.far=r,n.updateProjectionMatrix()),Js.setFromMatrixPosition(t.matrixWorld),n.position.copy(Js),xa.copy(n.position),xa.add(this._cubeDirections[e]),n.up.copy(this._cubeUps[e]),n.lookAt(xa),n.updateMatrixWorld(),s.makeTranslation(-Js.x,-Js.y,-Js.z),Th.multiplyMatrices(n.projectionMatrix,n.matrixWorldInverse),this._frustum.setFromProjectionMatrix(Th)}},So=class extends yo{constructor(t,e,n=0,s=2){super(t,e),this.isPointLight=!0,this.type="PointLight",this.distance=n,this.decay=s,this.shadow=new Hl}get power(){return this.intensity*4*Math.PI}set power(t){this.intensity=t/(4*Math.PI)}dispose(){this.shadow.dispose()}copy(t,e){return super.copy(t,e),this.distance=t.distance,this.decay=t.decay,this.shadow=t.shadow.clone(),this}},kl=class extends Mo{constructor(){super(new Es(-5,5,5,-5,.5,500)),this.isDirectionalLightShadow=!0}},ki=class extends yo{constructor(t,e){super(t,e),this.isDirectionalLight=!0,this.type="DirectionalLight",this.position.copy(Ze.DEFAULT_UP),this.updateMatrix(),this.target=new Ze,this.shadow=new kl}dispose(){this.shadow.dispose()}copy(t){return super.copy(t),this.target=t.target.clone(),this.shadow=t.shadow.clone(),this}};var tc="\\[\\]\\.:\\/",Eg=new RegExp("["+tc+"]","g"),ec="[^"+tc+"]",wg="[^"+tc.replace("\\.","")+"]",Tg=/((?:WC+[\/:])*)/.source.replace("WC",ec),Ag=/(WCOD+)?/.source.replace("WCOD",wg),Rg=/(?:\.(WC+)(?:\[(.+)\])?)?/.source.replace("WC",ec),Cg=/\.(WC+)(?:\[(.+)\])?/.source.replace("WC",ec),Pg=new RegExp("^"+Tg+Ag+Rg+Cg+"$"),Ig=["material","materials","bones","map"],Vl=class{constructor(t,e,n){let s=n||xe.parseTrackName(e);this._targetGroup=t,this._bindings=t.subscribe_(e,s)}getValue(t,e){this.bind();let n=this._targetGroup.nCachedObjects_,s=this._bindings[n];s!==void 0&&s.getValue(t,e)}setValue(t,e){let n=this._bindings;for(let s=this._targetGroup.nCachedObjects_,r=n.length;s!==r;++s)n[s].setValue(t,e)}bind(){let t=this._bindings;for(let e=this._targetGroup.nCachedObjects_,n=t.length;e!==n;++e)t[e].bind()}unbind(){let t=this._bindings;for(let e=this._targetGroup.nCachedObjects_,n=t.length;e!==n;++e)t[e].unbind()}},xe=class i{constructor(t,e,n){this.path=e,this.parsedPath=n||i.parseTrackName(e),this.node=i.findNode(t,this.parsedPath.nodeName),this.rootNode=t,this.getValue=this._getValue_unbound,this.setValue=this._setValue_unbound}static create(t,e,n){return t&&t.isAnimationObjectGroup?new i.Composite(t,e,n):new i(t,e,n)}static sanitizeNodeName(t){return t.replace(/\s/g,"_").replace(Eg,"")}static parseTrackName(t){let e=Pg.exec(t);if(e===null)throw new Error("PropertyBinding: Cannot parse trackName: "+t);let n={nodeName:e[2],objectName:e[3],objectIndex:e[4],propertyName:e[5],propertyIndex:e[6]},s=n.nodeName&&n.nodeName.lastIndexOf(".");if(s!==void 0&&s!==-1){let r=n.nodeName.substring(s+1);Ig.indexOf(r)!==-1&&(n.nodeName=n.nodeName.substring(0,s),n.objectName=r)}if(n.propertyName===null||n.propertyName.length===0)throw new Error("PropertyBinding: can not parse propertyName from trackName: "+t);return n}static findNode(t,e){if(e===void 0||e===""||e==="."||e===-1||e===t.name||e===t.uuid)return t;if(t.skeleton){let n=t.skeleton.getBoneByName(e);if(n!==void 0)return n}if(t.children){let n=function(r){for(let o=0;o<r.length;o++){let a=r[o];if(a.name===e||a.uuid===e)return a;let l=n(a.children);if(l)return l}return null},s=n(t.children);if(s)return s}return null}_getValue_unavailable(){}_setValue_unavailable(){}_getValue_direct(t,e){t[e]=this.targetObject[this.propertyName]}_getValue_array(t,e){let n=this.resolvedProperty;for(let s=0,r=n.length;s!==r;++s)t[e++]=n[s]}_getValue_arrayElement(t,e){t[e]=this.resolvedProperty[this.propertyIndex]}_getValue_toArray(t,e){this.resolvedProperty.toArray(t,e)}_setValue_direct(t,e){this.targetObject[this.propertyName]=t[e]}_setValue_direct_setNeedsUpdate(t,e){this.targetObject[this.propertyName]=t[e],this.targetObject.needsUpdate=!0}_setValue_direct_setMatrixWorldNeedsUpdate(t,e){this.targetObject[this.propertyName]=t[e],this.targetObject.matrixWorldNeedsUpdate=!0}_setValue_array(t,e){let n=this.resolvedProperty;for(let s=0,r=n.length;s!==r;++s)n[s]=t[e++]}_setValue_array_setNeedsUpdate(t,e){let n=this.resolvedProperty;for(let s=0,r=n.length;s!==r;++s)n[s]=t[e++];this.targetObject.needsUpdate=!0}_setValue_array_setMatrixWorldNeedsUpdate(t,e){let n=this.resolvedProperty;for(let s=0,r=n.length;s!==r;++s)n[s]=t[e++];this.targetObject.matrixWorldNeedsUpdate=!0}_setValue_arrayElement(t,e){this.resolvedProperty[this.propertyIndex]=t[e]}_setValue_arrayElement_setNeedsUpdate(t,e){this.resolvedProperty[this.propertyIndex]=t[e],this.targetObject.needsUpdate=!0}_setValue_arrayElement_setMatrixWorldNeedsUpdate(t,e){this.resolvedProperty[this.propertyIndex]=t[e],this.targetObject.matrixWorldNeedsUpdate=!0}_setValue_fromArray(t,e){this.resolvedProperty.fromArray(t,e)}_setValue_fromArray_setNeedsUpdate(t,e){this.resolvedProperty.fromArray(t,e),this.targetObject.needsUpdate=!0}_setValue_fromArray_setMatrixWorldNeedsUpdate(t,e){this.resolvedProperty.fromArray(t,e),this.targetObject.matrixWorldNeedsUpdate=!0}_getValue_unbound(t,e){this.bind(),this.getValue(t,e)}_setValue_unbound(t,e){this.bind(),this.setValue(t,e)}bind(){let t=this.node,e=this.parsedPath,n=e.objectName,s=e.propertyName,r=e.propertyIndex;if(t||(t=i.findNode(this.rootNode,e.nodeName),this.node=t),this.getValue=this._getValue_unavailable,this.setValue=this._setValue_unavailable,!t){console.warn("THREE.PropertyBinding: No target node found for track: "+this.path+".");return}if(n){let c=e.objectIndex;switch(n){case"materials":if(!t.material){console.error("THREE.PropertyBinding: Can not bind to material as node does not have a material.",this);return}if(!t.material.materials){console.error("THREE.PropertyBinding: Can not bind to material.materials as node.material does not have a materials array.",this);return}t=t.material.materials;break;case"bones":if(!t.skeleton){console.error("THREE.PropertyBinding: Can not bind to bones as node does not have a skeleton.",this);return}t=t.skeleton.bones;for(let h=0;h<t.length;h++)if(t[h].name===c){c=h;break}break;case"map":if("map"in t){t=t.map;break}if(!t.material){console.error("THREE.PropertyBinding: Can not bind to material as node does not have a material.",this);return}if(!t.material.map){console.error("THREE.PropertyBinding: Can not bind to material.map as node.material does not have a map.",this);return}t=t.material.map;break;default:if(t[n]===void 0){console.error("THREE.PropertyBinding: Can not bind to objectName of node undefined.",this);return}t=t[n]}if(c!==void 0){if(t[c]===void 0){console.error("THREE.PropertyBinding: Trying to bind to objectIndex of objectName, but is undefined.",this,t);return}t=t[c]}}let o=t[s];if(o===void 0){let c=e.nodeName;console.error("THREE.PropertyBinding: Trying to update property for track: "+c+"."+s+" but it wasn't found.",t);return}let a=this.Versioning.None;this.targetObject=t,t.needsUpdate!==void 0?a=this.Versioning.NeedsUpdate:t.matrixWorldNeedsUpdate!==void 0&&(a=this.Versioning.MatrixWorldNeedsUpdate);let l=this.BindingType.Direct;if(r!==void 0){if(s==="morphTargetInfluences"){if(!t.geometry){console.error("THREE.PropertyBinding: Can not bind to morphTargetInfluences because node does not have a geometry.",this);return}if(!t.geometry.morphAttributes){console.error("THREE.PropertyBinding: Can not bind to morphTargetInfluences because node does not have a geometry.morphAttributes.",this);return}t.morphTargetDictionary[r]!==void 0&&(r=t.morphTargetDictionary[r])}l=this.BindingType.ArrayElement,this.resolvedProperty=o,this.propertyIndex=r}else o.fromArray!==void 0&&o.toArray!==void 0?(l=this.BindingType.HasFromToArray,this.resolvedProperty=o):Array.isArray(o)?(l=this.BindingType.EntireArray,this.resolvedProperty=o):this.propertyName=s;this.getValue=this.GetterByBindingType[l],this.setValue=this.SetterByBindingTypeAndVersioning[l][a]}unbind(){this.node=null,this.getValue=this._getValue_unbound,this.setValue=this._setValue_unbound}};xe.Composite=Vl;xe.prototype.BindingType={Direct:0,EntireArray:1,ArrayElement:2,HasFromToArray:3};xe.prototype.Versioning={None:0,NeedsUpdate:1,MatrixWorldNeedsUpdate:2};xe.prototype.GetterByBindingType=[xe.prototype._getValue_direct,xe.prototype._getValue_array,xe.prototype._getValue_arrayElement,xe.prototype._getValue_toArray];xe.prototype.SetterByBindingTypeAndVersioning=[[xe.prototype._setValue_direct,xe.prototype._setValue_direct_setNeedsUpdate,xe.prototype._setValue_direct_setMatrixWorldNeedsUpdate],[xe.prototype._setValue_array,xe.prototype._setValue_array_setNeedsUpdate,xe.prototype._setValue_array_setMatrixWorldNeedsUpdate],[xe.prototype._setValue_arrayElement,xe.prototype._setValue_arrayElement_setNeedsUpdate,xe.prototype._setValue_arrayElement_setMatrixWorldNeedsUpdate],[xe.prototype._setValue_fromArray,xe.prototype._setValue_fromArray_setNeedsUpdate,xe.prototype._setValue_fromArray_setMatrixWorldNeedsUpdate]];var lv=new Float32Array(1);typeof __THREE_DEVTOOLS__!="undefined"&&__THREE_DEVTOOLS__.dispatchEvent(new CustomEvent("register",{detail:{revision:Gl}}));typeof window!="undefined"&&(window.__THREE__?console.warn("WARNING: Multiple instances of Three.js being imported."):window.__THREE__=Gl);var He={about:"The matchday intro's one tempo map, read by the page (intro.js, bundled) and the music (tools/intro_synth.py). 96 BPM in 4/4: a beat is 0.625 s, a bar 2.5 s. Times in seconds from the first frame.",bpm:96,beat:.625,bar:2.5,len:51.5,fadeIn:[0,.35],fadeOut:[49.75,51.25],sections:[{name:"sky",t0:0,t1:10,chords:["Dm","Dm","Bb","C"]},{name:"arrival",t0:10,t1:20,chords:["Dm","Bb","F","C"]},{name:"strike",t0:20,t1:30,chords:["Dm","Bb","F","C"]},{name:"build",t0:30,t1:40,chords:["Dm","Bb","Gm","A"]},{name:"climax",t0:40,t1:45,chords:["Dm","Bb"]},{name:"card",t0:45,t1:51.5,chords:["D"]}],shots:[{id:"sky",t0:0,t1:10,what:"night sky, stars drifting; a footballer and a ball drawn in stars, a star a note"},{id:"tilt",t0:10,t1:15,what:"the camera tilts down through the floodlights' glare into the stadium bowl"},{id:"tunnel",t0:15,t1:17.5,what:"the walk-out: silhouettes walking up the tunnel into the light"},{id:"mast",t0:17.5,t1:18.75,what:"low angle up at a floodlight mast, its lamps blazing"},{id:"crowd",t0:18.75,t1:20,what:"long lens along the stand, camera flashes"},{id:"runup",t0:20,t1:22.5,what:"grass-level slow motion: the striker's last strides"},{id:"strike",t0:22.5,t1:25,what:"the strike: the ball leaves the boot trailing light"},{id:"boot",t0:25,t1:26.25,what:"close: boot through the ball, the grass spraying"},{id:"flight",t0:26.25,t1:27.5,what:"behind the ball as it streaks across the ground"},{id:"keeper",t0:27.5,t1:28.75,what:"the goalkeeper's dive in silhouette against the lights"},{id:"net",t0:28.75,t1:30,what:"from behind the goal: the net ripples"},{id:"lights",t0:30,t1:31.25,what:"the floodlight banks blaze on, bank by bank"},{id:"hero",t0:31.25,t1:32.5,what:"the striker, low angle, rim-lit"},{id:"bowl",t0:32.5,t1:33.75,what:"high over the bowl, circling, flashes"},{id:"gk",t0:33.75,t1:35,what:"the goalkeeper set, the lights behind"},{id:"boot2",t0:35,t1:35.625,what:"boot and ball again, closer"},{id:"whip",t0:35.625,t1:36.25,what:"the ball streaks past the lens"},{id:"tunnel2",t0:36.25,t1:36.875,what:"the walk-out, closer"},{id:"spin",t0:36.875,t1:37.5,what:"spinning down over the centre circle"},{id:"rise",t0:37.5,t1:40,what:"the ball flies up into the night and becomes a star"},{id:"trophy",t0:40,t1:45,what:"the star bursts; the stars gather into the trophy's outline; the trophy forms and turns"},{id:"card",t0:45,t1:51.5,what:"Whitebrook Road Sports, MATCHDAY, the two teams; then it fades to the channel"}],stars:{t0:1.25,step:.3125,n:14,ball:[5.625,6.25]},glare:12.2,strike:22.5,net:28.9,star:39.375,hush:[39.6,40],climax:40,reveal:[41.25,42.5],card:45,letters:45.625,teams:46.25,flashes:[[10,.5],[20,.35],[22.5,.55],[28.9,.45],[30,.5],[35,.35],[40,.7],[42.5,.35],[45,.9]],streaks:[[20,.62,-.06],[26.25,.4,.05],[30,.3,-.12],[31.25,.7,.08],[32.5,.45,-.04],[33.75,.62,.1],[35,.35,-.08],[35.625,.66,.06],[36.25,.5,-.1],[36.875,.4,.12],[37.5,.58,-.05],[45,.5,0]]};var Oe=Math.PI*2,me=(i,t=0,e=1)=>Math.max(t,Math.min(e,i)),Ce=(i,t,e)=>i+(t-i)*e,oe=(i,t,e)=>{let n=me((e-i)/(t-i));return n*n*(3-2*n)},je=i=>(i=me(i),i<.5?4*i*i*i:1-Math.pow(-2*i+2,3)/2),Ls=i=>1-Math.pow(1-me(i),3);var dt=(i,t,e)=>new P(i,t,e);function Gn(i){let t=i>>>0;return()=>{t=t+1831565813|0;let e=Math.imul(t^t>>>15,1|t);return e=e+Math.imul(e^e>>>7,61|e)^e,((e^e>>>14)>>>0)/4294967296}}var Ds=(i,t,e)=>i<t?0:Math.exp(-(i-t)/e),Vi={uTime:{value:0},uHalfH:{value:540},uPx:{value:1},uFogCol:{value:new Ct(659230)},uFogD:{value:.0045}},Zt=`
#include <tonemapping_fragment>
#include <colorspace_fragment>
`,ke=`uniform vec3 uFogCol; uniform float uFogD;
vec3 fogged(vec3 c, float d) { float f = 1.0 - exp(-uFogD * uFogD * d * d); return mix(c, uFogCol, f); }
`,Zh=`float hash1(float n) { return fract(sin(n) * 43758.5453123); }
float hash2(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
`;function Me(i){return new sn(Object.assign({uniforms:{},transparent:!1,depthWrite:!0},i,{uniforms:Object.assign({},Vi,i.uniforms||{})}))}var Ee=i=>Me(Object.assign({transparent:!0,depthWrite:!1,blending:Jr},i));function _n(i,t={}){let e=new Yt,n=new Float32Array(i*12),s=new Float32Array(i*8),r=new Float32Array(i*8),o=new Float32Array(i*16),a=new Float32Array(i*4),l=[];for(let u=0;u<i;u++)s.set([-1,-1,1,-1,1,1,-1,1],u*8),l.push(u*4,u*4+1,u*4+2,u*4,u*4+2,u*4+3);e.setAttribute("position",new Ut(n,3)),e.setAttribute("aCorner",new Ut(s,2)),e.setAttribute("aSize",new Ut(r,2)),e.setAttribute("aColor",new Ut(o,4)),e.setAttribute("aKind",new Ut(a,1)),e.setIndex(l);let c=Ee({depthTest:t.depthTest!==!1,uniforms:{uGain:{value:1}},vertexShader:`
      attribute vec2 aCorner; attribute vec2 aSize; attribute vec4 aColor; attribute float aKind;
      varying vec2 vUv; varying vec4 vCol; varying float vKind;
      void main() {
        vec4 mv = modelViewMatrix * vec4(position, 1.0);
        mv.xy += aCorner * aSize;
        gl_Position = projectionMatrix * mv;
        vUv = aCorner; vCol = aColor; vKind = aKind;
      }`,fragmentShader:`
      uniform float uGain;
      varying vec2 vUv; varying vec4 vCol; varying float vKind;
      void main() {
        float r2 = dot(vUv, vUv);
        float edge = 1.0 - smoothstep(0.55, 1.0, r2);
        float g;
        if (vKind < 0.5) g = exp(-r2 * 7.0) + 0.18 * exp(-r2 * 2.2);
        else if (vKind < 1.5) {
          float sp = exp(-abs(vUv.y) * 70.0) * exp(-abs(vUv.x) * 3.0) + exp(-abs(vUv.x) * 70.0) * exp(-abs(vUv.y) * 3.0);
          g = exp(-r2 * 18.0) * 1.4 + 0.12 * exp(-r2 * 3.0) + sp * 0.9;
        } else if (vKind < 2.5) g = exp(-abs(vUv.y) * 9.0) * pow(1.0 - min(abs(vUv.x), 1.0), 2.0) + 0.6 * exp(-vUv.y * vUv.y * 400.0) * (1.0 - abs(vUv.x));
        else { float r = sqrt(r2); g = exp(-pow((r - 0.7) * 9.0, 2.0)) * 0.6 + exp(-r2 * 30.0) * 0.4; }
        vec3 c = vCol.rgb * vCol.a * g * edge * uGain;
        gl_FragColor = vec4(c, 1.0);
        ${Zt}
      }`}),h=new at(e,c);return h.frustumCulled=!1,h.renderOrder=t.order||5,{mesh:h,n:i,set(u,f,g,y,m,p,w=0){for(let E=0;E<4;E++){n.set([f.x,f.y,f.z],(u*4+E)*3),r.set([g,y],(u*4+E)*2);let M=m.isColor?[m.r,m.g,m.b]:m;o.set([M[0],M[1],M[2],p],(u*4+E)*4),a[u*4+E]=w}},alpha(u,f){for(let g=0;g<4;g++)o[(u*4+g)*4+3]=f},pos(u,f){for(let g=0;g<4;g++)n.set([f.x,f.y,f.z],(u*4+g)*3)},sz(u,f,g){for(let y=0;y<4;y++)r.set([f,g==null?f:g],(u*4+y)*2)},touch(){for(let u of["position","aSize","aColor","aKind"])e.attributes[u].needsUpdate=!0}}}var To=900,jh=new P(0,Math.sin(.66),-Math.cos(.66)),Dg=600,Jh=300,Qh=new P(1,0,0),Ug=new P().crossVectors(Qh,jh).normalize(),Ao=(i,t)=>jh.clone().multiplyScalar(Dg).addScaledVector(Qh,i*Jh).addScaledVector(Ug,(t-.45)*Jh),cr=new P(.55,.35,.76).normalize(),Co=new P().crossVectors(cr,new P(0,1,0)).normalize(),nc=new P().crossVectors(cr,Co).normalize();function tu(i,t){let e=new Dt,n=Gn(11),s=i.low?1024:2048,r=s/2,o=document.createElement("canvas");o.width=s,o.height=r;let a=o.getContext("2d"),l=a.createLinearGradient(0,0,0,r);l.addColorStop(0,"#02030a"),l.addColorStop(.36,"#050816"),l.addColorStop(.47,"#0b1330"),l.addColorStop(.5,"#141c3c"),l.addColorStop(.56,"#05070f"),l.addColorStop(1,"#020308"),a.fillStyle=l,a.fillRect(0,0,s,r);let c=["120,110,190","150,120,220","90,120,200","200,150,190","170,170,210","230,170,150"],h=(v,b,x,_)=>{let R=Math.acos(me(v.y,-1,1)),L=(Math.atan2(v.z,-v.x)+Oe)%Oe/Oe*s,N=R/Math.PI*r,V=Math.min(6,1/Math.max(.15,Math.sin(R))),F=b*r;for(let X of[-s,0,s]){if(L+X+F*V<0||L+X-F*V>s)continue;a.save(),a.translate(L+X,N),a.scale(V,1);let z=a.createRadialGradient(0,0,0,0,0,F);z.addColorStop(0,`rgba(${x},${_})`),z.addColorStop(1,`rgba(${x},0)`),a.fillStyle=z,a.fillRect(-F,-F,2*F,2*F),a.restore()}};a.globalCompositeOperation="lighter";for(let v=0;v<(i.low?700:1400);v++){let b=n()*Oe,x=(n()+n()+n()-1.5)*.16*(.6+.4*Math.sin(b*2+1)),_=Co.clone().multiplyScalar(Math.cos(b)).addScaledVector(nc,Math.sin(b)).addScaledVector(cr,x).normalize(),R=Math.exp(-Math.pow(Math.sin((b-1.2)/2),2)*3);h(_,.012+n()*.05,c[Math.floor(n()*c.length)],(.02+n()*.05)*(.45+R))}a.globalCompositeOperation="source-over";for(let v=0;v<(i.low?500:1e3);v++){let b=n()*Oe,x=.018*Math.sin(b*3+1)+(n()-.5)*.012,_=Co.clone().multiplyScalar(Math.cos(b)).addScaledVector(nc,Math.sin(b)).addScaledVector(cr,x).normalize();h(_,.004+n()*.01,"4,5,12",.07)}let d=new ai(o);d.colorSpace=On;let u=new sn({side:Re,depthWrite:!1,uniforms:{map:{value:d},color:{value:new Ct(1,1,1)}},vertexShader:"varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",fragmentShader:`uniform sampler2D map; uniform vec3 color; varying vec2 vUv;
      void main() {
        vec3 c = pow(texture2D(map, vUv).rgb, vec3(2.2)) * color;
        c += (fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) * 0.0025;
        gl_FragColor = vec4(max(c, 0.0), 1.0);
        ${Zt}
      }`}),f=new at(new kn(To+40,48,24),u);f.renderOrder=-10,e.add(f);let g=i.low?2600:5200,y=new Float32Array(g*3),m=new Float32Array(g),p=new Float32Array(g*3),w=new Float32Array(g*2);for(let v=0;v<g;v++){let b;if(n()<.42){let L=n()*Oe,N=(n()+n()-1)*.14;b=Co.clone().multiplyScalar(Math.cos(L)).addScaledVector(nc,Math.sin(L)).addScaledVector(cr,N).normalize()}else{let L=n()*2-1,N=n()*Oe,V=Math.sqrt(1-L*L);b=new P(V*Math.cos(N),L,V*Math.sin(N))}y.set([b.x*To,b.y*To,b.z*To],v*3);let x=Math.pow(n(),7);m[v]=1.3+7.5*x+n()*.8;let _=n(),R=_<.25?[.72,.8,1]:_<.8?[.95,.96,1]:[1,.86,.7],I=.35+.65*Math.sqrt(x)+.25*n();p.set([R[0]*I,R[1]*I,R[2]*I],v*3),w.set([n()*Oe,.6+n()*3.2],v*2)}let E=new Yt;E.setAttribute("position",new Ut(y,3)),E.setAttribute("aSize",new Ut(m,1)),E.setAttribute("aColor",new Ut(p,3)),E.setAttribute("aTw",new Ut(w,2));let M=Ee({uniforms:{uFade:{value:1},uSizeK:{value:1}},vertexShader:`
      attribute float aSize; attribute vec3 aColor; attribute vec2 aTw;
      uniform float uTime, uPx, uFade, uSizeK;
      varying vec3 vCol;
      void main() {
        vec4 mv = modelViewMatrix * vec4(position, 1.0);
        gl_Position = projectionMatrix * mv;
        float tw = 0.62 + 0.38 * sin(uTime * aTw.y + aTw.x);
        float s = aSize * uSizeK * uPx;
        gl_PointSize = max(1.5, s);
        vCol = aColor * tw * uFade * min(1.0, s / 1.5);
      }`,fragmentShader:`
      varying vec3 vCol;
      void main() {
        vec2 p = gl_PointCoord * 2.0 - 1.0; float r = dot(p, p);
        float a = exp(-r * 9.0) + 0.22 * exp(-r * 3.0);
        a *= 1.0 - smoothstep(0.7, 1.0, r);
        gl_FragColor = vec4(vCol * a * 1.6, 1.0);
        ${Zt}
      }`}),U=new ze(E,M);U.frustumCulled=!1,U.renderOrder=-5,e.add(U);let C=Fg(t);return e.add(C.group),{group:e,stars:U,starGeo:E,starMat:M,milky:f,milkyMat:u,C}}var ic={head:[.05,1.02],neck:[.06,.88],shR:[.2,.83],elR:[.38,.92],hR:[.52,1.02],shL:[-.1,.83],elL:[-.28,.72],hL:[-.4,.6],hip:[0,.48],kS:[-.04,.24],fS:[-.14,-.02],kK:[.24,.36],fK:[.47,.22]},Kh=["head","neck","shR","elR","hR","shL","elL","hL","hip","kS","fS","kK","fK"],Ng=[["head","neck"],["neck","shR"],["shR","elR"],["elR","hR"],["neck","shL"],["shL","elL"],["elL","hL"],["neck","hip"],["hip","kS"],["kS","fS"],["hip","kK"],["kK","fK"]],Us=[.66,.2],Ro=.085;function Fg(i){let t=new Dt,e={},n=i.stars;Kh.forEach((v,b)=>{e[v]=n.t0+b*n.step});let s=[];for(let v=0;v<8;v++){let b=v/8*Oe+.2;s.push([Us[0]+Ro*Math.cos(b),Us[1]+Ro*Math.sin(b)])}for(let v=0;v<5;v++){let b=v/5*Oe+Math.PI/2;s.push([Us[0]+Ro*.45*Math.cos(b),Us[1]+Ro*.45*Math.sin(b)])}let r=n.ball[0],o=(n.ball[1]-n.ball[0])/s.length,a=[],l=[],c=[];Kh.forEach(v=>{a.push(ic[v]),l.push(e[v]),c.push(v==="head"?15:11)}),s.forEach((v,b)=>{a.push(v),l.push(r+b*o),c.push(b<8?9:7)});let h=new Float32Array(a.length*3),d=new Float32Array(a.length*2);a.forEach((v,b)=>{let x=Ao(v[0],v[1]);h.set([x.x,x.y,x.z],b*3),d.set([l[b],c[b]],b*2)});let u=new Yt;u.setAttribute("position",new Ut(h,3)),u.setAttribute("aLit",new Ut(d,2));let f=Ee({uniforms:{uA:{value:1},uBoost:{value:0}},vertexShader:`
      attribute vec2 aLit; uniform float uTime, uPx, uA, uBoost;
      varying float vB; varying float vF;
      void main() {
        vec4 mv = modelViewMatrix * vec4(position, 1.0); gl_Position = projectionMatrix * mv;
        float since = uTime - aLit.x;
        float on = smoothstep(0.0, 0.05, since);
        float pop = since > 0.0 ? exp(-since / 0.35) : 0.0;
        vB = (0.12 + 0.88 * on) * uA * (0.85 + 0.15 * sin(uTime * 3.0 + aLit.x * 7.0)) + uBoost;
        vF = pop;
        gl_PointSize = aLit.y * uPx * (1.0 + 1.6 * pop + 0.4 * uBoost) * (0.55 + 0.45 * on);
      }`,fragmentShader:`
      varying float vB; varying float vF;
      void main() {
        vec2 p = gl_PointCoord * 2.0 - 1.0; float r = dot(p, p);
        float core = exp(-r * 14.0) * 1.6 + 0.3 * exp(-r * 3.5);
        float sp = (exp(-abs(p.y) * 30.0) * exp(-abs(p.x) * 2.5) + exp(-abs(p.x) * 30.0) * exp(-abs(p.y) * 2.5)) * (0.5 + vF);
        float a = (core + sp) * (1.0 - smoothstep(0.75, 1.0, r));
        vec3 c = mix(vec3(0.8, 0.88, 1.0), vec3(1.0, 0.95, 0.8), vF);
        gl_FragColor = vec4(c * a * (vB + vF * 1.5), 1.0);
        ${Zt}
      }`}),g=new ze(u,f);g.frustumCulled=!1,g.renderOrder=2,t.add(g);let y=[];Ng.forEach(([v,b])=>y.push([ic[v],ic[b],Math.max(e[v],e[b]),.28]));for(let v=0;v<8;v++)y.push([s[v],s[(v+1)%8],r+.25+v*.03,.2]);for(let v=0;v<5;v++)y.push([s[8+v],s[8+(v+2)%5],r+.4+v*.03,.2]);let m=[],p=[],w=[],E=.0085;y.forEach(([v,b,x,_],R)=>{let I=b[0]-v[0],L=b[1]-v[1],N=Math.hypot(I,L),V=-L/N,F=I/N,X=.03,z=[v[0]+I*X/N*.9,v[1]+L*X/N*.9],j=[b[0]-I*X/N*.9,b[1]-L*X/N*.9];[[z,-1,0],[z,1,0],[j,1,1],[j,-1,1]].forEach(([yt,Ft,$])=>{let nt=Ao(yt[0]+V*E*Ft,yt[1]+F*E*Ft);m.push(nt.x,nt.y,nt.z),p.push($,Ft,x,_)});let st=R*4;w.push(st,st+1,st+2,st,st+2,st+3)});let M=new Yt;M.setAttribute("position",new Bt(m,3)),M.setAttribute("aLine",new Bt(p,4)),M.setIndex(w);let U=Ee({side:de,uniforms:{uA:{value:1}},vertexShader:`
      attribute vec4 aLine; varying vec4 vL;
      void main() { vL = aLine; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,fragmentShader:`
      uniform float uTime, uA; varying vec4 vL;
      void main() {
        float prog = clamp((uTime - vL.z) / vL.w, 0.0, 1.0);
        float drawn = 1.0 - smoothstep(prog - 0.04, prog, vL.x);
        if (prog >= 1.0) drawn = 1.0;
        float x = vL.y;
        float a = exp(-x * x * 14.0) * 0.9 + exp(-x * x * 2.5) * 0.25;
        float head = prog < 1.0 ? exp(-pow((vL.x - prog) * 14.0, 2.0)) * 1.5 : 0.0;
        vec3 c = vec3(0.62, 0.74, 1.0) * a * drawn * uA * (0.7 + head) + vec3(1.0, 0.9, 0.7) * head * a * uA;
        gl_FragColor = vec4(c, 1.0);
        ${Zt}
      }`}),C=new at(M,U);return C.frustumCulled=!1,C.renderOrder=1,t.add(C),{group:t,starMat:f,lineMat:U,ballStar:Ao(Us[0],Us[1]),centre:Ao(.2,.5)}}var sc=59.5,rc=40,Ns=5,qe=[[70,-78],[-70,-78],[70,78],[-70,78]].map(([i,t])=>new P(i,63,t)),Po=new P(36.5,.11,-5.5),Og=52.5;function Bg(i){let t=[];for(let a=0;a<4e3;a++){let l=a/4e3*Oe,c=Math.cos(l),h=Math.sin(l),d=sc*Math.sign(c)*Math.pow(Math.abs(c),2/Ns),u=rc*Math.sign(h)*Math.pow(Math.abs(h),2/Ns);t.push([d,u])}let n=[0];for(let a=1;a<=4e3;a++){let l=t[a%4e3],c=t[a-1];n.push(n[a-1]+Math.hypot(l[0]-c[0],l[1]-c[1]))}let s=n[4e3],r=[],o=0;for(let a=0;a<i;a++){let l=a/i*s;for(;n[o+1]<l;)o++;let c=(l-n[o])/(n[o+1]-n[o]),h=t[o],d=t[(o+1)%4e3],u=h[0]+(d[0]-h[0])*c,f=h[1]+(d[1]-h[1])*c,g=Ns/sc*Math.pow(Math.abs(u/sc),Ns-1)*Math.sign(u),y=Ns/rc*Math.pow(Math.abs(f/rc),Ns-1)*Math.sign(f),m=Math.hypot(g,y);g/=m,y/=m,r.push({x:u,z:f,nx:g,nz:y})}return{pts:r,len:s}}function hr(i,t,e=!0){let n=i.pts,s=n.length,r=t.length,o=[],a=[],l=[],c=[],h=[0];for(let u=1;u<r;u++)h.push(h[u-1]+Math.hypot(t[u][0]-t[u-1][0],t[u][1]-t[u-1][1]));for(let u=0;u<=s;u++){let f=n[u%s];for(let g=0;g<r;g++)o.push(f.x+f.nx*t[g][0],t[g][1],f.z+f.nz*t[g][0]),a.push(u/s),l.push(h[g])}for(let u=0;u<s;u++)for(let f=0;f<r-1;f++){let g=u*r+f,y=(u+1)*r+f;c.push(g,y,g+1,g+1,y,y+1)}let d=new Yt;return d.setAttribute("position",new Bt(o,3)),d.setAttribute("aS",new Bt(a,1)),d.setAttribute("aSec",new Bt(l,1)),d.setIndex(c),d.computeVertexNormals(),d}var oc=`
  attribute float aS; attribute float aSec;
  varying vec3 vW; varying vec3 vN; varying float vS; varying float vSec; varying float vD;
  void main() {
    vec4 wp = modelMatrix * vec4(position, 1.0);
    vW = wp.xyz; vN = normalize(mat3(modelMatrix) * normal); vS = aS; vSec = aSec;
    vec4 mv = viewMatrix * wp; vD = length(mv.xyz);
    gl_Position = projectionMatrix * mv;
  }`;function eu(i){let t=new Dt,e=Gn(23),n=Bg(i.low?160:240),s=n.len,r=Me({uniforms:{uLit:{value:1}},extensions:{},vertexShader:`
      varying vec3 vW; varying float vD;
      void main() { vec4 wp = modelMatrix * vec4(position, 1.0); vW = wp.xyz; vec4 mv = viewMatrix * wp; vD = length(mv.xyz); gl_Position = projectionMatrix * mv; }`,fragmentShader:`
      uniform float uLit;
      ${ke}
      ${Zh}
      varying vec3 vW; varying float vD;
      float sdBox(vec2 p, vec2 b) { vec2 d = abs(p) - b; return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0); }
      float lines(vec2 p) {
        float d = abs(sdBox(p, vec2(52.5, 34.0)));
        d = min(d, length(vec2(p.x, max(abs(p.y) - 34.0, 0.0))));
        d = min(d, abs(length(p) - 9.15));
        vec2 q = vec2(abs(p.x), p.y);
        d = min(d, abs(sdBox(q - vec2(44.25, 0.0), vec2(8.25, 20.16))));
        d = min(d, abs(sdBox(q - vec2(49.75, 0.0), vec2(2.75, 9.16))));
        float arc = abs(length(q - vec2(41.5, 0.0)) - 9.15); d = min(d, q.x < 36.0 ? arc : 1e3);
        vec2 cq = abs(p) - vec2(52.5, 34.0); float ca = abs(length(cq) - 1.0); d = min(d, (cq.x < 0.0 && cq.y < 0.0) ? ca : 1e3);
        d = min(d, max(0.0, length(p) - 0.12));
        d = min(d, max(0.0, length(q - vec2(41.5, 0.0)) - 0.12));
        return d;
      }
      void main() {
        vec2 p = vW.xz;
        float stripe = mod(floor((p.x + 52.5) / 5.25), 2.0);
        vec3 g1 = vec3(0.075, 0.2, 0.07), g2 = vec3(0.1, 0.26, 0.09);
        vec3 col = mix(g1, g2, stripe);
        // close up: tufts and blades (faded where a pixel covers more than a few centimetres)
        float px = length(fwidth(p));
        float fine = clamp(1.0 - px * 12.0, 0.0, 1.0);
        float n1 = hash2(floor(p * 40.0)), n2 = hash2(floor(p * 9.0) + 7.0);
        col *= 1.0 + fine * (n1 - 0.5) * 0.45 + (n2 - 0.5) * 0.12 * clamp(1.0 - px * 2.0, 0.0, 1.0);
        // the floodlights' throw: even, a touch brighter in the middle, darker beyond the touchlines
        float lit = 1.05 - 0.25 * smoothstep(30.0, 70.0, length(p * vec2(0.8, 1.2)));
        col *= lit * uLit * 1.5;
        float d = lines(p), w = 0.06, aa = max(px * 0.7, 0.004);
        float m = 1.0 - smoothstep(w - aa, w + aa, d);
        m *= clamp(0.12 / max(px, 0.001), 0.25, 1.0);
        col = mix(col, vec3(0.78, 0.82, 0.8) * uLit * 1.3, m * 0.92);
        gl_FragColor = vec4(fogged(col, vD), 1.0);
        ${Zt}
      }`}),o=new at(new Je(170,140).rotateX(-Math.PI/2),r);o.frustumCulled=!1,t.add(o);let a=[[2,.6],[4,1.6],[27,14.5]],l=[[27.6,17.4],[48,35],[48.4,39]],c=Me({uniforms:{uPerim:{value:s},uLit:{value:1}},vertexShader:oc,fragmentShader:`
      uniform float uPerim, uLit;
      ${ke}
      varying vec3 vW; varying vec3 vN; varying float vS; varying float vSec; varying float vD;
      void main() {
        vec3 n = normalize(vN);
        float r = vSec / 0.82;
        float aa = fwidth(r);
        float row = fract(r);
        float steps = mix(0.55 + 0.45 * smoothstep(0.1, 0.25, row) * (1.0 - smoothstep(0.75, 0.9, row)), 0.75, clamp(aa * 1.5, 0.0, 1.0));
        float u = vS * uPerim / 13.0;
        float ai = abs(fract(u) - 0.5);
        float aisle = 1.0 - smoothstep(0.035, 0.045 + fwidth(u), 0.5 - ai);
        vec3 toC = normalize(vec3(-vW.x, 0.0, -vW.z) + vec3(0.0, 1.3, 0.0));
        float lit = 0.3 + 0.7 * max(dot(n, toC), 0.0);
        lit *= mix(1.25, 0.6, clamp(vW.y / 38.0, 0.0, 1.0));
        vec3 seat = vec3(0.045, 0.05, 0.075);
        vec3 col = seat * steps * lit * 1.4 + vec3(0.07, 0.075, 0.09) * aisle * lit;
        gl_FragColor = vec4(fogged(col * uLit, vD), 1.0);
        ${Zt}
      }`}),h=new Dt;h.add(new at(hr(n,a),c)),h.add(new at(hr(n,l),c));let d=Me({vertexShader:oc,fragmentShader:`${ke}
      varying vec3 vW; varying vec3 vN; varying float vS; varying float vSec; varying float vD;
      void main() { vec3 c = vec3(0.02, 0.022, 0.03) + vec3(0.5, 0.55, 0.7) * smoothstep(17.9, 18.4, vSec) * 0.22; gl_FragColor = vec4(fogged(c, vD), 1.0); ${Zt} }`,side:de});h.add(new at(hr(n,[[48.4,39],[48.6,40.6],[30.5,41.6],[30.2,41]]),d));let u=A=>Me({side:de,uniforms:{uPerim:{value:s},uLed:{value:1}},vertexShader:oc,fragmentShader:`
      uniform float uPerim, uLed, uTime;
      ${ke}
      varying vec3 vW; varying vec3 vN; varying float vS; varying float vSec; varying float vD;
      void main() {
        float u = vS * uPerim, h = vSec;
        float wave = 0.5 + 0.5 * sin(u * ${A?"0.09":"0.22"} - uTime * ${A?"1.6":"2.4"});
        vec3 c = mix(vec3(0.05, 0.12, 0.55), vec3(0.2, 0.32, 0.75), wave);
        float chev = step(0.72, fract((u + abs(h - ${A?"1.4":"0.45"}) * 2.0) * ${A?"0.06":"0.18"} - uTime * 0.7));
        c = mix(c, vec3(0.85, 0.9, 1.0), chev * ${A?"0.3":"0.55"});
        float edge = smoothstep(0.0, 0.06, h) * (1.0 - smoothstep(${A?"2.7":"0.86"}, ${A?"2.8":"0.92"}, h));
        gl_FragColor = vec4(fogged(c * uLed * edge * ${A?"0.3":"0.85"}, vD), 1.0);
        ${Zt}
      }`}),f=u(0),g=u(1);h.add(new at(hr(n,[[-2.6,0],[-2.6,.92]]),f)),h.add(new at(hr(n,[[27.05,14.5],[27.05,17.3]]),g)),h.children.forEach(A=>{A.frustumCulled=!1}),t.add(h);let y=n.pts,m=y.length,p=(A,S,D)=>{let G=A*m,Z=Math.floor(G)%m,W=G-Math.floor(G),et=y[Z],Q=y[(Z+1)%m],rt=et.x+(Q.x-et.x)*W,Nt=et.z+(Q.z-et.z)*W,tt=et.nx+(Q.nx-et.nx)*W,lt=et.nz+(Q.nz-et.nz)*W,xt=D?l:a,Mt=D?xt[0]:xt[1],_t=D?xt[1]:xt[2],$t=(S-Mt[0])/(_t[0]-Mt[0]),Vt=Mt[1]+(_t[1]-Mt[1])*$t;return[rt+tt*S,Vt,Nt+lt*S]},w=i.low?9e3:22e3,E=new Float32Array(w*3),M=new Float32Array(w*3),U=new Float32Array(w*4),C=[[.06,.065,.08],[.1,.1,.11],[.04,.045,.06],[.26,.06,.07],[.06,.09,.24],[.42,.42,.44],[.3,.26,.1],[.16,.14,.13]],v=0;for(;v<w;){let A=e(),S=e()<.55?0:1;if(Math.abs(A*s/13%1-.5)>.455)continue;let D=S?l:a,G=S?D[0][0]:D[1][0],Z=S?D[1][0]:D[2][0],W=G+.4+e()*(Z-G-.8),et=p(A,W,S);E.set([et[0],et[1]+.5+e()*.15,et[2]],v*3);let Q=e()<.62?Math.floor(e()*3):3+Math.floor(e()*5),rt=C[Q],Nt=.75+e()*.5;M.set([rt[0]*Nt,rt[1]*Nt,rt[2]*Nt],v*3),U.set([e(),e(),e(),e()],v*4),v++}let b=new Yt;b.setAttribute("position",new Ut(E,3)),b.setAttribute("aColor",new Ut(M,3)),b.setAttribute("aRnd",new Ut(U,4));let x=Me({uniforms:{uBright:{value:1},uBob:{value:1}},vertexShader:`
      attribute vec3 aColor; attribute vec4 aRnd;
      uniform float uTime, uHalfH, uBright, uBob;
      varying vec3 vCol; varying vec3 vSkin; varying float vBig; varying float vD;
      void main() {
        vec3 p = position;
        p.y += 0.07 * uBob * sin(uTime * (2.0 + aRnd.x * 4.0) + aRnd.y * 40.0);
        vec4 mv = modelViewMatrix * vec4(p, 1.0);
        gl_Position = projectionMatrix * mv;
        float s = 0.62 * projectionMatrix[1][1] * uHalfH / -mv.z;
        gl_PointSize = clamp(s, 1.0, 48.0);
        float lit = mix(1.25, 0.6, clamp(p.y / 36.0, 0.0, 1.0));
        vCol = aColor * uBright * lit * min(1.0, s * s);
        vSkin = mix(vec3(0.3, 0.21, 0.16), vec3(0.12, 0.08, 0.06), aRnd.z) * uBright * lit;
        vBig = smoothstep(3.0, 7.0, s);
        vD = length(mv.xyz);
      }`,fragmentShader:`
      ${ke}
      varying vec3 vCol; varying vec3 vSkin; varying float vBig; varying float vD;
      void main() {
        vec2 q = gl_PointCoord * 2.0 - 1.0;
        // near enough, a person: a head over shoulders; far off, a soft dot
        float head = length(q - vec2(0.0, -0.52)) - 0.27;
        float body = length((q - vec2(0.0, 0.5)) * vec2(0.6, 0.85)) - 0.42;
        float d = mix(length(q) - 0.85, min(head, body), vBig);
        if (d > 0.0) discard;
        vec3 c = mix(vCol, vSkin, step(head, body) * vBig) * (1.0 - 0.3 * max(q.y, 0.0));
        gl_FragColor = vec4(fogged(c, vD), 1.0);
        ${Zt}
      }`}),_=new ze(b,x);_.frustumCulled=!1,t.add(_);let R=i.low?1600:4e3,I=new Float32Array(R*3),L=new Float32Array(R*2);for(let A=0;A<R;A++){let S=Math.floor(e()*w);I.set([E[S*3],E[S*3+1]+.3,E[S*3+2]],A*3),L.set([e(),e()],A*2)}let N=new Yt;N.setAttribute("position",new Ut(I,3)),N.setAttribute("aRnd",new Ut(L,2));let V=Ee({uniforms:{uFlash:{value:.1},uRate:{value:3}},vertexShader:`
      attribute vec2 aRnd;
      uniform float uTime, uHalfH, uPx, uFlash, uRate;
      varying float vF;
      void main() {
        vec4 mv = modelViewMatrix * vec4(position, 1.0);
        gl_Position = projectionMatrix * mv;
        float k = uTime * uRate * (0.6 + aRnd.x) + aRnd.y * 91.0;
        float slot = floor(k), ph = fract(k) / (uRate * (0.6 + aRnd.x));
        float h = fract(sin(slot * 12.9898 + aRnd.x * 437.585) * 43758.5453);
        float on = step(1.0 - uFlash, h) * exp(-ph / 0.035);
        vF = on;
        float s = 2.6 * projectionMatrix[1][1] * uHalfH / -mv.z;
        gl_PointSize = on > 0.02 ? clamp(s, 7.0 * uPx, 70.0 * uPx) : 0.0;
      }`,fragmentShader:`
      varying float vF;
      void main() {
        vec2 q = gl_PointCoord * 2.0 - 1.0; float r = dot(q, q);
        float g = exp(-r * 22.0) * 2.5 + 0.25 * exp(-r * 5.0);
        g += (exp(-abs(q.y) * 40.0) * exp(-abs(q.x) * 3.0) + exp(-abs(q.x) * 40.0) * exp(-abs(q.y) * 3.0)) * 0.8;
        g *= 1.0 - smoothstep(0.7, 1.0, r);
        gl_FragColor = vec4(vec3(1.0, 0.97, 0.92) * g * vF * 1.6, 1.0);
        ${Zt}
      }`}),F=new ze(N,V);F.frustumCulled=!1,F.renderOrder=6,t.add(F);let X=Me({uniforms:{uCol:{value:new Ct(.03,.033,.04)}},vertexShader:"varying float vD; varying vec3 vN; void main() { vec4 mv = modelViewMatrix * vec4(position, 1.0); vD = length(mv.xyz); vN = normalize(normalMatrix * normal); gl_Position = projectionMatrix * mv; }",fragmentShader:`uniform vec3 uCol; ${ke} varying float vD; varying vec3 vN; void main() { vec3 c = uCol * (0.7 + 0.5 * abs(vN.x)); gl_FragColor = vec4(fogged(c, vD), 1.0); ${Zt} }`}),z=7,j=4,it=_n(qe.length*(z*j),{order:7}),st=_n(qe.length*2,{order:8,depthTest:!1}),yt=[];qe.forEach((A,S)=>{let D=new at(new Ke(.6,1.3,A.y,6),X);D.position.set(A.x,A.y/2-1,A.z),t.add(D);let G=new Dt;G.position.copy(A),G.lookAt(0,0,0),t.add(G);let Z=new at(new dn(15,8.5,.7),X);G.add(Z);let W=[];for(let Q=0;Q<j;Q++)for(let rt=0;rt<z;rt++){let Nt=new P(-6.3+rt*2.1,2.9-Q*1.95,.5);G.updateMatrixWorld(!0),W.push({p:G.localToWorld(Nt.clone()),row:Q,col:rt})}G.updateMatrixWorld(!0);let et=new P(0,0,1).applyQuaternion(G.quaternion);yt.push({m:A,head:G,fwd:et,lamps:W,glarePos:A.clone().addScaledVector(et,2)})});let Ft=Ee({side:de,uniforms:{uA:{value:.05}},vertexShader:"varying vec3 vN; varying vec3 vV; varying vec2 vUv; void main() { vec4 mv = modelViewMatrix * vec4(position, 1.0); vN = normalize(normalMatrix * normal); vV = normalize(-mv.xyz); vUv = uv; gl_Position = projectionMatrix * mv; }",fragmentShader:`uniform float uA; varying vec3 vN; varying vec3 vV; varying vec2 vUv;
      void main() { float f = pow(abs(dot(normalize(vN), vV)), 2.0); float along = pow(vUv.y, 1.6); gl_FragColor = vec4(vec3(0.8, 0.86, 1.0) * f * along * uA, 1.0); ${Zt} }`}),$=new Dt;yt.forEach(A=>{let S=new P(A.m.x*.25,0,A.m.z*.15),D=A.m.distanceTo(S),G=new at(new Ke(5,34,D,24,1,!0),Ft);G.position.lerpVectors(A.m,S,.5),G.quaternion.setFromUnitVectors(new P(0,1,0),A.m.clone().sub(S).normalize()),$.add(G)}),$.renderOrder=4,t.add($),t.add(it.mesh),t.add(st.mesh);let nt=_n(1,{order:3});nt.set(0,new P(0,25,0),190,110,[.35,.42,.7],.35,0),nt.touch(),t.add(nt.mesh);let ft=new P;function ct(A,S=1,D=1,G=1,Z=1){let W=0;yt.forEach((et,Q)=>{ft.copy(A.position).sub(et.glarePos).normalize();let rt=Math.max(0,et.fwd.dot(ft)),Nt=me((et.fwd.dot(ft)+.15)/.35);for(let lt of et.lamps){let xt=typeof S=="function"?S(Q,lt.row,lt.col):S;it.set(W++,lt.p,.95,.95,[1,.96,.9],(1.2+2.2*Math.pow(rt,3))*xt*Nt*Z,0)}let tt=typeof S=="function"?S(Q,1,3):S;st.set(Q*2,et.glarePos,30*G,30*G,[.85,.9,1],Math.pow(rt,4)*1.3*tt*D,1),st.set(Q*2+1,et.glarePos,110*G,2.6*G,[.6,.75,1],Math.pow(rt,6)*1.1*tt*D,2)}),it.touch(),st.touch()}let bt=Me({uniforms:{uCol:{value:new Ct(1.6,1.6,1.65)}},vertexShader:"varying float vD; varying vec3 vN; void main() { vec4 mv = modelViewMatrix * vec4(position, 1.0); vD = length(mv.xyz); vN = normalize(normalMatrix * normal); gl_Position = projectionMatrix * mv; }",fragmentShader:`uniform vec3 uCol; ${ke} varying float vD; varying vec3 vN; void main() { vec3 c = uCol * (0.45 + 0.55 * max(vN.y, 0.0) + 0.3 * abs(vN.x)); gl_FragColor = vec4(fogged(c, vD), 1.0); ${Zt} }`}),Lt=()=>Me({transparent:!0,depthWrite:!1,side:de,uniforms:{uHit:{value:new re(0,1.5,-2.8,99)},uAmp:{value:0}},vertexShader:`
      attribute vec3 aNet;   // (u, v) in metres on its panel, panel (0 back, 1 top, 2 side)
      uniform vec4 uHit; uniform float uAmp;
      varying vec2 vUv; varying float vD;
      void main() {
        vec3 p = position;
        vec3 hit = vec3(2.0, uHit.y, uHit.z);
        float d = length(p - hit), age = uHit.w;
        float push = (1.0 - exp(-age * 16.0)) * exp(-age * 2.6) * 0.95;
        float wave = 0.14 * sin(d * 5.0 - age * 15.0) * exp(-d * 0.55) * exp(-age * 1.8) * step(0.0, age);
        float k = exp(-d * d / 0.9) * push + wave;
        vec3 dir = aNet.z < 0.5 ? vec3(1.0, 0.0, 0.0) : aNet.z < 1.5 ? vec3(0.0, 1.0, 0.0) : vec3(0.0, 0.0, sign(p.z));
        float pin = aNet.z < 0.5 ? smoothstep(0.0, 0.5, p.y) * smoothstep(0.0, 0.4, 3.66 - abs(p.z)) * smoothstep(0.0, 0.3, 2.44 - p.y + 0.3) : 0.35 * smoothstep(0.0, 1.0, p.x);
        p += dir * k * uAmp * pin;
        vUv = aNet.xy;
        vec4 mv = modelViewMatrix * vec4(p, 1.0); vD = length(mv.xyz);
        gl_Position = projectionMatrix * mv;
      }`,fragmentShader:`
      ${ke}
      varying vec2 vUv; varying float vD;
      void main() {
        vec2 q = mat2(0.7071, 0.7071, -0.7071, 0.7071) * vUv / 0.13;
        vec2 f = abs(fract(q) - 0.5);
        vec2 w = fwidth(q) * 0.9 + 0.035;
        float line = max(1.0 - smoothstep(0.0, w.x, 0.5 - f.x), 1.0 - smoothstep(0.0, w.y, 0.5 - f.y));
        float fade = clamp(0.1 / max(fwidth(q.x), 0.01), 0.3, 1.0);
        gl_FragColor = vec4(fogged(vec3(0.85, 0.88, 0.92) * 1.2, vD), line * 0.85 * fade + 0.03);
        ${Zt}
      }`}),Et=Lt();function Kt(){let A=[],S=[],D=[],G=(W,et,Q,rt)=>{let Nt=A.length/3;for(let tt=0;tt<=et;tt++)for(let lt=0;lt<=W;lt++){let[xt,Mt]=Q(lt/W,tt/et);A.push(...xt),S.push(Mt[0],Mt[1],rt)}for(let tt=0;tt<et;tt++)for(let lt=0;lt<W;lt++){let xt=Nt+tt*(W+1)+lt,Mt=xt+W+1;D.push(xt,Mt,xt+1,xt+1,Mt,Mt+1)}};G(40,14,(W,et)=>[[2,et*2.44,-3.66+W*7.32],[W*7.32,et*2.44]],0),G(40,10,(W,et)=>[[et*2,2.44,-3.66+W*7.32],[W*7.32,et*2]],1);for(let W of[-1,1])G(10,14,(et,Q)=>[[et*2,Q*2.44,W*3.66],[et*2,Q*2.44]],2);let Z=new Yt;return Z.setAttribute("position",new Bt(A,3)),Z.setAttribute("aNet",new Bt(S,3)),Z.setIndex(D),Z}let Ht=Kt(),ue=[];for(let A of[1,-1]){let S=new Dt;S.position.set(A*Og,0,0),S.rotation.y=A>0?0:Math.PI;let D=.062;for(let W of[-3.66,3.66]){let et=new at(new Ke(D,D,2.44+D,10),bt);et.position.set(0,1.22,W),S.add(et)}let G=new at(new Ke(D,D,7.32+2*D,10),bt);G.rotation.x=Math.PI/2,G.position.set(0,2.44,0),S.add(G);let Z=new at(Ht,A>0?Et:Lt());Z.frustumCulled=!1,Z.renderOrder=3,S.add(Z),t.add(S),ue.push({G:S,net:Z})}let O=i.low?2e3:14e3,be=new Yt;be.setAttribute("position",new Bt([-.0035,0,0,.0035,0,0,0,1,0],3)),be.setAttribute("normal",new Bt([0,0,1,0,0,1,0,0,1],3)),be.setAttribute("aH",new Bt([0,0,1],1));let Xt=Me({side:de,uniforms:{uL:{value:new P(0,.3,-1)}},vertexShader:`
      attribute float aH; uniform float uTime;
      varying float vH; varying float vD; varying vec3 vW;
      void main() {
        vec4 wp = modelMatrix * instanceMatrix * vec4(position, 1.0);
        wp.x += aH * aH * 0.012 * sin(uTime * 1.3 + wp.z * 3.0 + wp.x);
        vH = aH; vW = wp.xyz;
        vec4 mv = viewMatrix * wp; vD = length(mv.xyz);
        gl_Position = projectionMatrix * mv;
      }`,fragmentShader:`
      ${ke}
      varying float vH; varying float vD; varying vec3 vW;
      void main() {
        float k = fract(sin(dot(floor(vW.xz * 50.0), vec2(12.9898, 78.233))) * 43758.5453);
        vec3 c = mix(vec3(0.02, 0.05, 0.02), mix(vec3(0.12, 0.3, 0.09), vec3(0.18, 0.36, 0.12), k), vH);
        c += vec3(0.5, 0.6, 0.55) * pow(vH, 8.0) * 0.45;
        gl_FragColor = vec4(fogged(c * 1.25, vD), 1.0);
        ${Zt}
      }`}),kt=new uo(be,Xt,O),It=new ne,te=new Hn,Rt=new gn;for(let A=0;A<O;A++){let S=(i.low?3:4.5)*Math.sqrt(e()),D=e()*Oe,G=.025+e()*e()*.07;Rt.set((e()-.5)*.8,e()*Oe,(e()-.5)*.8),te.setFromEuler(Rt),It.compose(new P(Po.x+S*Math.cos(D),0,Po.z+S*Math.sin(D)),te,new P(.7+e()*.8,G,1)),kt.setMatrixAt(A,It)}return kt.frustumCulled=!1,t.add(kt),{group:t,pitch:o,pitchMat:r,stands:h,standMat:c,boardsMat:f,fasciaMat:g,crowd:_,crowdMat:x,flashes:F,flashMat:V,masts:yt,lights:ct,lampG:it,glareG:st,beams:$,beamMat:Ft,hazeG:nt,goals:ue,netMat:Et,blades:kt,darkMat:X,perimeter:s}}function Io(){return Me({uniforms:{uL1:{value:new P(0,0,-1)},uC1:{value:new Ct(1.6,1.7,2)},uL2:{value:new P(1,0,0)},uC2:{value:new Ct(.9,.65,.45)},uBase:{value:new Ct(.006,.007,.011)},uFill:{value:.04}},vertexShader:`
      varying vec3 vN; varying vec3 vV;
      void main() {
        vec4 wp = modelMatrix * vec4(position, 1.0);
        #ifdef USE_INSTANCING
        wp = modelMatrix * instanceMatrix * vec4(position, 1.0);
        #endif
        vec4 mv = viewMatrix * wp;
        vN = normalize(normalMatrix * normal);
        vV = -mv.xyz;
        gl_Position = projectionMatrix * mv;
      }`,fragmentShader:`
      uniform vec3 uL1, uL2, uC1, uC2, uBase; uniform float uFill;
      ${ke}
      varying vec3 vN; varying vec3 vV;
      void main() {
        vec3 n = normalize(vN), v = normalize(vV);
        float ndv = max(dot(n, v), 0.0);
        float fr = pow(1.0 - ndv, 3.5);
        // light from behind outlines the whole figure, strongest on the side the light is on
        float l1 = dot(n, uL1), l2 = dot(n, uL2);
        float b1 = clamp(dot(uL1, -v) * 0.5 + 0.5, 0.0, 1.0), b2 = clamp(dot(uL2, -v) * 0.5 + 0.5, 0.0, 1.0);
        vec3 c = uBase * (1.0 + uFill * 10.0 * (0.5 + 0.5 * n.y));
        c += uC1 * fr * clamp(l1 + 0.55, 0.0, 1.0) * (0.12 + 1.7 * b1 * b1) + uC1 * pow(max(l1, 0.0), 4.0) * 0.05;
        c += uC2 * fr * clamp(l2 + 0.45, 0.0, 1.0) * (0.08 + 1.0 * b2 * b2) * 0.8;
        gl_FragColor = vec4(fogged(c, length(vV)), 1.0);
        ${Zt}
      }`})}var Ye={};function zg(){if(Ye.thigh)return Ye;let i=(t,e)=>new Si(t,e,3,9);return Ye.thigh=i(.068,.34).translate(0,-.21,0),Ye.shin=i(.05,.36).translate(0,-.22,0),Ye.foot=i(.045,.14).rotateX(Math.PI/2).translate(0,-.035,.065),Ye.upper=i(.046,.21).translate(0,-.145,0),Ye.fore=i(.04,.2).translate(0,-.135,0),Ye.hand=new kn(.046,8,6).translate(0,-.29,0),Ye.torso=new Ke(.2,.15,.48,10,1).scale(1,1,.62).translate(0,.27,0),Ye.chest=i(.09,.3).rotateZ(Math.PI/2).translate(0,.47,0),Ye.hips=i(.092,.17).rotateZ(Math.PI/2).translate(0,0,0),Ye.shorts=new Ke(.145,.16,.2,10,1).scale(1,1,.72).translate(0,-.04,0),Ye.neck=new Ke(.05,.055,.12,8).translate(0,.05,0),Ye.head=new kn(.108,12,9).scale(.92,1.1,1).translate(0,.17,.01),Ye.glove=new kn(.066,8,6).scale(1,1.25,.6).translate(0,-.3,0),Ye}function dr(i,t={}){let e=zg(),n=c=>{let h=new at(c,i);return h.frustumCulled=!1,h},s=new Dt,r=new Dt;r.position.y=.98,s.add(r),r.add(n(e.hips)),r.add(n(e.shorts));let o=new Dt;o.position.y=.06,r.add(o),o.add(n(e.torso)),o.add(n(e.chest));let a=new Dt;a.position.y=.55,o.add(a),a.add(n(e.neck)),a.add(n(e.head));let l={root:s,pelvis:r,spine:o,neck:a};for(let[c,h]of[["L",1],["R",-1]]){let d=new Dt;d.position.set(.215*h,.48,0),o.add(d),d.add(n(e.upper));let u=new Dt;u.position.y=-.29,d.add(u),u.add(n(e.fore)),u.add(n(t.gloves?e.glove:e.hand));let f=new Dt;f.position.set(.1*h,-.02,0),r.add(f),f.add(n(e.thigh));let g=new Dt;g.position.y=-.44,f.add(g),g.add(n(e.shin));let y=new Dt;y.position.y=-.45,g.add(y),y.add(n(e.foot)),l["sh"+c]=d,l["el"+c]=u,l["hip"+c]=f,l["kn"+c]=g,l["an"+c]=y}return l.footR=l.anR,l.footL=l.anL,{root:s,J:l,pose:c=>kg(l,c)}}var Hg={x:0,y:0,z:0,ry:0,py:0,pitch:0,roll:0,lean:0,twist:0,side:0,head:0,headY:0,thL:0,thLz:0,knL:0,anL:0,thR:0,thRz:0,knR:0,anR:0,arL:0,arLz:.12,elL:-.25,arR:0,arRz:.12,elR:-.25};function kg(i,t){let e=Object.assign({},Hg,t);i.root.position.set(e.x,e.y,e.z),i.root.rotation.set(0,e.ry,0),i.pelvis.position.y=.98+e.py,i.pelvis.rotation.set(e.pitch,0,e.roll),i.spine.rotation.set(e.lean,e.twist,e.side),i.neck.rotation.set(e.head,e.headY,0),i.hipL.rotation.set(e.thL,0,e.thLz),i.knL.rotation.x=e.knL,i.anL.rotation.x=e.anL,i.hipR.rotation.set(e.thR,0,-e.thRz),i.knR.rotation.x=e.knR,i.anR.rotation.x=e.anR,i.shL.rotation.set(e.arL,0,e.arLz),i.elL.rotation.x=e.elL,i.shR.rotation.set(e.arR,0,-e.arRz),i.elR.rotation.x=e.elR}function nu(i,t){var h,d,u;if(t<=i[0][0])return Object.assign({},i[0][1]);let e=i.length;if(t>=i[e-1][0])return Object.assign({},i[e-1][1]);let n=0;for(;i[n+1][0]<t;)n++;let s=i[Math.max(0,n-1)],r=i[n],o=i[n+1],a=i[Math.min(e-1,n+2)],l=(t-r[0])/(o[0]-r[0]),c={};for(let f in r[1]){let g=(h=s[1][f])!=null?h:r[1][f],y=r[1][f],m=(d=o[1][f])!=null?d:y,p=(u=a[1][f])!=null?u:m,w=l*l,E=w*l;c[f]=.5*(2*y+(-g+m)*l+(2*g-5*y+4*m-p)*w+(-g+3*y-3*m+p)*E)}return c}function iu(i){let t=i*Math.PI*2,e=Math.sin(t),n=Math.cos(t);return{lean:.03,py:-.015+.02*Math.abs(n),head:-.05,thL:-.38*e,knL:.12+.45*Math.max(0,Math.sin(t+1.4)),anL:.08,thR:.38*e,knR:.12+.45*Math.max(0,Math.sin(t+1.4+Math.PI)),anR:.08,arL:.32*e,elL:-.3,arR:-.32*e,elR:-.3,arLz:.12,arRz:.12,twist:-.06*e}}var ur={lean:.24,py:-.03,thL:-.7,knL:.35,anL:.2,thR:.55,knR:1.3,anR:.3,arL:.6,elL:-1.3,arR:-.6,elR:-1.2,arLz:.18,arRz:.18,side:0,twist:.1,head:0,pz:-2.95,thLz:0,thRz:0},Vg=[[-.62,Object.assign({},ur,{thL:.5,knL:1.2,thR:-.6,knR:.35,arL:-.6,arR:.6,twist:-.1,pz:-3.6})],[-.48,ur],[-.34,Object.assign({},ur,{thL:.35,knL:1,thR:-.45,knR:.5,arL:-.45,arR:.45,py:.05,twist:-.1,pz:-2.1})],[-.2,Object.assign({},ur,{thL:.55,knL:.9,thR:-.55,knR:.25,arL:-.3,arR:.5,arLz:.6,py:-.07,lean:.18,pz:-1.3})],[-.09,{lean:.05,py:-.09,side:-.18,twist:.25,head:.25,thL:-.38,knL:.42,anL:0,thR:.95,knR:1.85,anR:.6,arL:-.25,elL:-.4,arLz:1.05,arR:.55,elR:-.7,arRz:.45,pz:-.55,thLz:.05,thRz:.05}],[-.035,{lean:0,py:-.11,side:-.24,twist:.15,head:.35,thL:-.22,knL:.48,anL:0,thR:.25,knR:1.35,anR:.65,arL:-.2,elL:-.4,arLz:1.25,arR:.4,elR:-.6,arRz:.55,pz:-.38,thLz:.05,thRz:.02}],[0,{lean:-.1,py:-.1,side:-.28,twist:0,head:.4,thL:-.06,knL:.45,anL:0,thR:-.5,knR:.32,anR:.75,arL:-.15,elL:-.35,arLz:1.35,arR:.3,elR:-.5,arRz:.6,pz:-.3,thLz:.05,thRz:0}],[.1,{lean:-.2,py:-.02,side:-.25,twist:-.2,head:.25,thL:.05,knL:.3,anL:-.2,thR:-1.35,knR:.12,anR:.6,arL:.1,elL:-.4,arLz:1.2,arR:0,elR:-.6,arRz:.7,pz:-.18,thLz:.05,thRz:0}],[.3,{lean:.1,py:-.04,side:-.1,twist:-.1,head:.1,thL:.45,knL:.7,anL:.2,thR:-.45,knR:.35,anR:.3,arL:.3,elL:-.8,arLz:.6,arR:-.3,elR:-.9,arRz:.4,pz:.55,thLz:0,thRz:0}],[.6,Object.assign({},ur,{lean:.12,pz:2.1})]];function Lo(i){let t=nu(Vg,i);return t.pz=t.pz||0,t}var Gi={py:-.2,lean:.32,head:-.2,thL:-.42,knL:.8,anL:-.3,thLz:.3,thR:-.42,knR:.8,anR:-.3,thRz:.3,arL:-.55,elL:-.6,arLz:.55,arR:-.55,elR:-.6,arRz:.55,roll:0,side:0,lat:0,rise:0,twist:0},Gg=[[0,Gi],[.2,Object.assign({},Gi,{py:-.26,lat:.15,side:.05})],[.32,Object.assign({},Gi,{py:-.3,roll:.35,lat:-.35,thL:-.2,knL:.5,thR:-.6,knR:1.1,arL:-1.2,arR:-1.4,arRz:.9})],[.44,{py:.1,lean:.1,head:-.3,roll:1.05,lat:-1,rise:.25,thL:.2,knL:.25,anL:.5,thLz:.35,thR:-.4,knR:.9,anR:.4,thRz:.1,arL:-2.5,elL:-.2,arLz:.2,arR:-2.6,elR:-.15,arRz:.25,side:.15,twist:0}],[.6,{py:.25,lean:0,head:-.4,roll:1.38,lat:-1.7,rise:.45,thL:.25,knL:.15,anL:.6,thLz:.4,thR:.05,knR:.35,anR:.5,thRz:.15,arL:-2.95,elL:-.05,arLz:.15,arR:-3,elR:-.05,arRz:.12,side:.2,twist:0}],[.85,{py:0,lean:.1,head:-.2,roll:1.5,lat:-2.1,rise:.1,thL:.3,knL:.4,anL:.5,thLz:.3,thR:-.1,knR:.6,anR:.4,thRz:.1,arL:-2.7,elL:-.3,arLz:.3,arR:-2.8,elR:-.3,arRz:.3,side:.15,twist:0}]];function su(i){return nu(Gg,i)}var ru=i=>Object.assign({},Gi,{py:Gi.py+.012*Math.sin(i),arLz:Gi.arLz+.04*Math.sin(i*1.3),arRz:Gi.arRz+.04*Math.sin(i*1.3+1)}),ac=i=>({lean:-.08,head:-.32-.04*Math.sin(i),headY:.15,py:0,thL:-.05,thLz:.08,knL:.06,thR:.05,thRz:.08,knR:.06,arL:.05,arLz:.32+.03*Math.sin(i*1.2),elL:-.25,arR:.05,arRz:.32+.03*Math.sin(i*1.2+1),elR:-.25,twist:.1});function ou(i,t,e){let n=e.lat||0;return new P(i.x+n*Math.cos(t),i.y+(e.rise||0),i.z-n*Math.sin(t))}function au(){let i=[],t=(1+Math.sqrt(5))/2;for(let[r,o]of[[1,t],[1,-t],[-1,t],[-1,-t]])i.push([0,r,o],[r,o,0],[o,0,r]);let e=i.map(r=>{let o=Math.hypot(...r);return`vec3(${(r[0]/o).toFixed(5)}, ${(r[1]/o).toFixed(5)}, ${(r[2]/o).toFixed(5)})`}),n=Me({uniforms:{uL1:{value:new P(0,.5,-1)},uC1:{value:new Ct(1.5,1.6,1.8)},uGlow:{value:0}},vertexShader:`
      varying vec3 vO; varying vec3 vN; varying vec3 vV;
      void main() { vO = normalize(position); vN = normalize(normalMatrix * normal); vec4 mv = modelViewMatrix * vec4(position, 1.0); vV = -mv.xyz; gl_Position = projectionMatrix * mv; }`,fragmentShader:`
      uniform vec3 uL1, uC1; uniform float uGlow;
      ${ke}
      varying vec3 vO; varying vec3 vN; varying vec3 vV;
      const vec3 IV[12] = vec3[12](${e.join(", ")});
      void main() {
        float m = 0.0;
        for (int i = 0; i < 12; i++) m = max(m, dot(vO, IV[i]));
        float pent = smoothstep(0.928, 0.94, m);
        vec3 base = mix(vec3(0.85, 0.86, 0.9), vec3(0.03, 0.04, 0.09), pent);
        vec3 n = normalize(vN), v = normalize(vV);
        float l = max(dot(n, normalize(uL1)), 0.0), fr = pow(1.0 - max(dot(n, v), 0.0), 2.5);
        vec3 c = base * (0.12 + 0.5 * max(n.y, 0.0) + 0.9 * l) + uC1 * fr * smoothstep(0.0, 0.5, l) + vec3(1.0, 0.9, 0.7) * uGlow;
        gl_FragColor = vec4(fogged(c, length(vV)), 1.0);
        ${Zt}
      }`}),s=new at(new bi(.11,4),n);return s.frustumCulled=!1,{mesh:s,mat:n}}function lu(i=56){let t=new Yt,e=new Float32Array((i+1)*6),n=new Float32Array((i+1)*4),s=[];for(let d=0;d<=i;d++)if(n.set([d/i,-1,d/i,1],d*4),d<i){let u=d*2;s.push(u,u+2,u+1,u+1,u+2,u+3)}t.setAttribute("position",new Ut(e,3)),t.setAttribute("aUv",new Ut(n,2)),t.setIndex(s);let r=Ee({side:de,uniforms:{uA:{value:1},uCore:{value:new Ct(1,.95,.85)},uEdge:{value:new Ct(1,.62,.25)}},vertexShader:"attribute vec2 aUv; varying vec2 vUv; void main() { vUv = aUv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",fragmentShader:`
      uniform float uA; uniform vec3 uCore, uEdge; varying vec2 vUv;
      void main() {
        float along = pow(1.0 - vUv.x, 1.4), x = vUv.y;
        float core = exp(-x * x * 18.0), glow = exp(-x * x * 2.5) * 0.45;
        vec3 c = (uCore * core * 1.6 + uEdge * glow) * along * uA;
        gl_FragColor = vec4(c, 1.0);
        ${Zt}
      }`}),o=new at(t,r);o.frustumCulled=!1,o.renderOrder=9;let a=new P,l=new P,c=new P;function h(d,u,f,g){let y=d.length;for(let m=0;m<=i;m++){let p=Math.min(y-1,Math.round(m/i*(y-1))),w=d[Math.max(0,p-1)],E=d[Math.min(y-1,p+1)],M=d[p];a.subVectors(w,E),a.lengthSq()<1e-10&&a.set(1,0,0),c.subVectors(u,M),l.crossVectors(a,c).normalize();let U=f+(g-f)*(m/i);e.set([M.x-l.x*U,M.y-l.y*U,M.z-l.z*U,M.x+l.x*U,M.y+l.y*U,M.z+l.z*U],m*6)}t.attributes.position.needsUpdate=!0}return{mesh:o,mat:r,update:h}}function cu(i){let t=Gn(41),e=(d,u)=>{let f=new Float32Array(d*3),g=new Float32Array(d*3),y=new Float32Array(d*2);for(let p=0;p<d;p++){let w=(t()-.5)*2.4,E=1.2+t()*3.2,M=1+t()*4.5;f.set([(t()-.5)*.12,t()*.03,(t()-.5)*.12],p*3),g.set([M*(u?1.2:1),E*(u?1.1:1),w*(u?1.6:1)],p*3),y.set([t(),t()],p*2)}let m=new Yt;return m.setAttribute("position",new Ut(f,3)),m.setAttribute("aVel",new Ut(g,3)),m.setAttribute("aRnd",new Ut(y,2)),m},n=d=>`
    attribute vec3 aVel; attribute vec2 aRnd;
    uniform float uE, uHalfH, uPx; uniform mat3 uRot; uniform vec3 uOrigin;
    varying float vA; varying float vD;
    void main() {
      float e = max(uE - aRnd.x * 0.02, 0.0);
      vec3 p = uOrigin + uRot * (position + aVel * e) + vec3(0.0, -4.9 * e * e, 0.0);
      vec4 mv = viewMatrix * vec4(p, 1.0);
      gl_Position = projectionMatrix * mv;
      float s = ${d?"0.012":"0.022"} * (0.6 + aRnd.y) * projectionMatrix[1][1] * uHalfH / -mv.z;
      float on = step(0.0, uE) * step(0.0, p.y) * (1.0 - smoothstep(0.6, 1.2, e));
      gl_PointSize = on > 0.0 ? clamp(s, 1.0, 40.0 * uPx) : 0.0;
      vA = on; vD = length(mv.xyz);
    }`,s=()=>({uE:{value:-1},uRot:{value:new Ot},uOrigin:{value:new P}}),r=Me({uniforms:s(),vertexShader:n(!1),fragmentShader:`${ke} varying float vA; varying float vD;
      void main() { vec2 q = gl_PointCoord * 2.0 - 1.0; if (dot(q, q) > 1.0) discard; gl_FragColor = vec4(fogged(vec3(0.07, 0.2, 0.06) * (1.2 - 0.5 * q.y), vD), 1.0); ${Zt} }`}),o=Ee({uniforms:s(),vertexShader:n(!0),fragmentShader:`varying float vA; varying float vD;
      void main() { vec2 q = gl_PointCoord * 2.0 - 1.0; float r = dot(q, q); float g = exp(-r * 6.0) * (1.0 - smoothstep(0.6, 1.0, r)); gl_FragColor = vec4(vec3(0.85, 0.95, 1.0) * g * vA * 1.8, 1.0); ${Zt} }`}),a=new ze(e(i.low?160:320,!1),r),l=new ze(e(i.low?160:360,!0),o);a.frustumCulled=l.frustumCulled=!1;let c=new Dt;c.add(a),c.add(l);function h(d,u,f){let g=new Ot().set(Math.cos(f),0,Math.sin(f),0,1,0,-Math.sin(f),0,Math.cos(f));for(let y of[r,o])y.uniforms.uE.value=d,y.uniforms.uOrigin.value.copy(u),y.uniforms.uRot.value.copy(g)}return{group:c,set:h}}function hu(){let i=new vn,t=new Es(0,1920,1080,0,-10,10),e=new Je(1,1),n=new at(e,Me({transparent:!0,depthTest:!1,depthWrite:!1,uniforms:{uA:{value:.6},uDim:{value:0}},vertexShader:"varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",fragmentShader:"uniform float uA, uDim; varying vec2 vUv; void main() { vec2 p = (vUv - 0.5) * vec2(1.6, 1.0); float v = smoothstep(0.35, 0.95, length(p)); gl_FragColor = vec4(0.0, 0.0, 0.0, max(v * uA, uDim)); }"}));n.scale.set(1920,1080,1),n.position.set(960,540,0),n.renderOrder=1;let s=Ee({depthTest:!1,uniforms:{uA:{value:0},uCol:{value:new Ct(1,.97,.92)}},vertexShader:"void main() { gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",fragmentShader:"uniform float uA; uniform vec3 uCol; void main() { gl_FragColor = vec4(uCol * uA, 1.0); }"}),r=new at(e,s);r.scale.set(1920,1080,1),r.position.set(960,540,0),r.renderOrder=50;let o=()=>Ee({depthTest:!1,uniforms:{uA:{value:0},uCol:{value:new Ct(1,.85,.6)}},vertexShader:"varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",fragmentShader:`uniform float uA; uniform vec3 uCol; varying vec2 vUv;
      void main() { float y = (vUv.y - 0.5) * 2.0, x = (vUv.x - 0.5) * 2.0; float taper = pow(max(0.0, 1.0 - x * x), 1.5);
        float g = (exp(-y * y * 600.0) * 1.8 + exp(-y * y * 30.0) * 0.35 + exp(-y * y * 4.0) * 0.08) * taper;
        gl_FragColor = vec4(mix(uCol, vec3(1.0), exp(-y * y * 900.0)) * g * uA, 1.0); }`}),a=[0,1,2].map(()=>{let d=new at(e,o());return d.renderOrder=20,d.visible=!1,i.add(d),d}),l=_n(10,{order:30,depthTest:!1});i.add(n),i.add(r),i.add(l.mesh);let c=new P,h=[[.55,70,[.4,.6,1],.08,3],[.2,26,[.6,1,.7],.12,0],[-.3,50,[1,.55,.3],.07,0],[-.75,120,[.5,.4,1],.05,3],[-1.05,34,[.9,.8,.6],.1,0]];return{scene:i,cam:t,vig:n,flash:r,flashMat:s,streaks:a,ghosts:l,streak(d,u,f,g,y,m){let p=a[d];p.visible=y>.001;let w=1300,E=-w*.5+u*(1920+w);p.position.set(E,1080-f,0),p.rotation.z=g,p.scale.set(w,90,1),p.material.uniforms.uA.value=y,m&&p.material.uniforms.uCol.value.setRGB(m[0],m[1],m[2])},flare(d,u){let f=0;for(let[g,y]of u){if(c.copy(g).project(d),c.z>1||Math.abs(c.x)>1.4||Math.abs(c.y)>1.4||y<.01)continue;let m=(c.x*.5+.5)*1920,p=(c.y*.5+.5)*1080;for(let[w,E,M,U,C]of h){if(f>=10)break;l.set(f++,new P(960+(m-960)*w,540+(p-540)*w,0),E,E,M,U*y,C)}}for(;f<10;f++)l.alpha(f,0);l.touch()}}}var Do=class extends vn{constructor(){super();let t=new dn;t.deleteAttribute("uv");let e=new Rs({side:Re}),n=new Rs,s=new So(16777215,900,28,2);s.position.set(.418,16.199,.3),this.add(s);let r=new at(t,e);r.position.set(-.757,13.219,.717),r.scale.set(31.713,28.305,28.591),this.add(r);let o=new at(t,n);o.position.set(-10.906,2.009,1.846),o.rotation.set(0,-.195,0),o.scale.set(2.328,7.905,4.651),this.add(o);let a=new at(t,n);a.position.set(-5.607,-.754,-.758),a.rotation.set(0,.994,0),a.scale.set(1.97,1.534,3.955),this.add(a);let l=new at(t,n);l.position.set(6.167,.857,7.803),l.rotation.set(0,.561,0),l.scale.set(3.927,6.285,3.687),this.add(l);let c=new at(t,n);c.position.set(-2.017,.018,6.124),c.rotation.set(0,.333,0),c.scale.set(2.002,4.566,2.064),this.add(c);let h=new at(t,n);h.position.set(2.291,-.756,-2.621),h.rotation.set(0,-.286,0),h.scale.set(1.546,1.552,1.496),this.add(h);let d=new at(t,n);d.position.set(-2.193,-.369,-5.547),d.rotation.set(0,.516,0),d.scale.set(3.875,3.487,2.986),this.add(d);let u=new at(t,Fs(50));u.position.set(-16.116,14.37,8.208),u.scale.set(.1,2.428,2.739),this.add(u);let f=new at(t,Fs(50));f.position.set(-16.109,18.021,-8.207),f.scale.set(.1,2.425,2.751),this.add(f);let g=new at(t,Fs(17));g.position.set(14.904,12.198,-1.832),g.scale.set(.15,4.265,6.331),this.add(g);let y=new at(t,Fs(43));y.position.set(-.462,8.89,14.52),y.scale.set(4.38,5.441,.088),this.add(y);let m=new at(t,Fs(20));m.position.set(3.235,11.486,-12.541),m.scale.set(2.5,2,.1),this.add(m);let p=new at(t,Fs(100));p.position.set(0,20,0),p.scale.set(1,.1,1),this.add(p)}dispose(){let t=new Set;this.traverse(e=>{e.isMesh&&(t.add(e.geometry),t.add(e.material))});for(let e of t)e.dispose()}};function Fs(i){let t=new Ss;return t.color.setScalar(i),t}function uu(){let i=new fn({color:15659508,metalness:1,roughness:.13,clearcoat:.4,clearcoatRoughness:.1}),t=new fn({color:14278114,metalness:1,roughness:.32}),e=new fn({color:16762970,metalness:1,roughness:.18,clearcoat:.3}),n=new fn({color:855568,metalness:.1,roughness:.28,clearcoat:1,clearcoatRoughness:.08});function s(v){let b=document.createElement("canvas");b.width=1024,b.height=256;let x=b.getContext("2d"),_=v==="malachite"?7:19,R=()=>(_=_*16807%2147483647,_/2147483647);if(v==="malachite"){x.fillStyle="#0d5a3e",x.fillRect(0,0,1024,256);for(let L=0;L<70;L++){let N=R()*1024,V=R()*256,F=20+R()*120;for(let X=0;X<9;X++)x.beginPath(),x.ellipse(N,V,F*(1-X*.1)*1.6,F*(1-X*.1),R()*.4,0,Math.PI*2),x.strokeStyle=X%2?`rgba(4,40,26,${.35+R()*.3})`:`rgba(60,170,120,${.25+R()*.3})`,x.lineWidth=2+R()*5,x.stroke()}}else{let L=x.createLinearGradient(0,0,1024,256);L.addColorStop(0,"#d9b65a"),L.addColorStop(.5,"#e8c977"),L.addColorStop(1,"#cfa64a"),x.fillStyle=L,x.fillRect(0,0,1024,256);for(let N=0;N<40;N++){x.beginPath();let V=R()*1024,F=R()*256;x.moveTo(V,F);for(let X=0;X<12;X++)V+=(R()-.3)*60,F+=(R()-.5)*30,x.lineTo(V,F);x.strokeStyle=`rgba(${R()<.5?"120,80,30":"250,235,190"},${.15+R()*.25})`,x.lineWidth=.6+R()*2,x.stroke()}}let I=new ai(b);return I.colorSpace=Pe,I.wrapS=I.wrapT=_s,I}let r=new fn({map:s("malachite"),roughness:.22,metalness:0,clearcoat:1,clearcoatRoughness:.05}),o=new fn({map:s("marble"),roughness:.3,metalness:0,clearcoat:1,clearcoatRoughness:.08});function a(v,b=160){return new Ts(v.map(([_,R])=>new gt(_,R))).getPoints(b).map(_=>new gt(Math.max(5e-4,_.x),_.y))}function l(v,b,x=128,_={}){let R=new or(_.raw?v.map(([I,L])=>new gt(I,L)):a(v,_.n||160),x);if(_.flute){let I=R.attributes.position;for(let L=0;L<I.count;L++){let N=I.getX(L),V=I.getY(L),F=I.getZ(L);if(V<_.flute.y0||V>_.flute.y1)continue;let X=Math.atan2(F,N),z=Math.sin(Math.PI*(V-_.flute.y0)/(_.flute.y1-_.flute.y0)),j=1+_.flute.depth*z*Math.cos(X*_.flute.n);I.setX(L,N*j),I.setZ(L,F*j)}R.computeVertexNormals()}return _.squash&&R.scale(1,1,_.squash),new at(R,b)}function c(v,b,x,_={}){let R=new rr(v.map(([N,V,F])=>new P(N,V,F||0)),!1,"catmullrom",.5),I=new xo(R,_.seg||120,b,_.radial||16,!1),L=new at(I,x);return _.flat&&(L.scale.z=_.flat),L}function h(v,b,x,_){let R=new at(new As(v,x,16,128),_);return R.rotation.x=Math.PI/2,R.position.y=b,R}function d(v,b){return new at(new kn(v,32,20),b)}function u(v){let b=v.clone();return b.scale.x*=-1,b}function f(){let v=new Dt,b=new at(new Si(.022,.05,8,16),e);b.rotation.z=Math.PI/2.6,b.position.set(-.008,.035,0),v.add(b);let x=d(.028,e);x.position.set(-.03,.025,0),v.add(x);let _=d(.026,e);_.position.set(.018,.055,0),v.add(_);let R=new at(new bi(.03,1),e);R.position.set(.03,.082,0),v.add(R);let I=d(.019,e);I.position.set(.045,.085,0),v.add(I);let L=d(.011,e);L.scale.set(1.2,.8,1),L.position.set(.062,.078,0),v.add(L);for(let V of[-1,1]){let F=new at(new Ke(.007,.008,.05,10),e);F.position.set(.03,.025,V*.012),v.add(F);let X=d(.006,e);X.position.set(.04,.104,V*.013),v.add(X)}let N=c([[-.05,.02],[-.07,.04],[-.065,.075],[-.05,.085]],.004,e,{seg:24,radial:8});return v.add(N),v}function g(){let v=new Dt,b=new at(new Si(.02,.06,8,16),e);b.rotation.z=Math.PI/2,b.position.set(-.01,.022,0),b.scale.set(1,1,1.1),v.add(b);let x=d(.024,e);x.position.set(-.045,.026,0),v.add(x);let _=d(.024,e);_.position.set(.028,.03,0),v.add(_);let R=new at(new bi(.028,1),e);R.position.set(.04,.056,0),v.add(R);let I=d(.018,e);I.position.set(.055,.06,0),v.add(I);let L=d(.01,e);L.scale.set(1.3,.85,1),L.position.set(.071,.053,0),v.add(L);for(let N of[-.012,.012]){let V=new at(new Si(.006,.035,6,10),e);V.rotation.z=Math.PI/2,V.position.set(.06,.007,N),v.add(V);let F=d(.006,e);F.position.set(.048,.078,N),v.add(F)}return v.add(c([[-.065,.02],[-.085,.015],[-.095,.03],[-.09,.045]],.0035,e,{seg:24,radial:8})),v}let y={pl(){let v=new Dt;v.add(l([[.176,0],[.178,.012],[.176,.042],[.172,.05]],r,128,{raw:!0})),v.add(h(.177,.003,.004,i)),v.add(h(.173,.05,.005,i)),v.add(l([[.17,.05],[.165,.065],[.15,.08],[.115,.1],[.085,.125],[.068,.15],[.06,.17]],i)),v.add(h(.16,.072,.005,i)),v.add(h(.1,.11,.005,i)),v.add(l([[.06,.17],[.055,.19],[.062,.21],[.085,.27],[.11,.35],[.13,.44],[.142,.53],[.146,.6],[.142,.66],[.13,.7],[.115,.725],[.09,.745],[.074,.765],[.068,.79],[.07,.83],[.078,.845]],i,192)),v.add(h(.07,.8,.006,i)),v.add(h(.062,.2,.006,i));let b=new Dt,x=240,_=[],R=[],I=.845,L=.885,N=.985,V=6;for(let[X,z]of[[0,!1],[-.006,!0]]){let j=_.length/3;for(let it=0;it<=x;it++){let st=it/x*Math.PI*2,yt=st*V/(Math.PI*2)%1,Ft=1-Math.abs(yt*2-1),$=L+(N-L)*Ft,nt=.084+X,ft=.084+($-I)*.5+X;_.push(nt*Math.cos(st),I,nt*Math.sin(st),ft*Math.cos(st),$,ft*Math.sin(st))}for(let it=0;it<x;it++){let st=j+it*2,yt=st+1,Ft=st+2,$=st+3;z?R.push(st,Ft,yt,yt,Ft,$):R.push(st,yt,Ft,yt,$,Ft)}}let F=new Yt;F.setAttribute("position",new Bt(_,3)),F.setIndex(R),F.computeVertexNormals(),b.add(new at(F,new fn({color:16762970,metalness:1,roughness:.17,clearcoat:.3,side:de}))),b.add(h(.085,I+.004,.007,e)),b.add(h(.098,I+.03,.005,e));for(let X=0;X<V;X++){let z=(X+.5)/V*Math.PI*2,j=.084+(N-I)*.5,it=d(.013,e);it.position.set(j*Math.cos(z),N+.01,j*Math.sin(z)),b.add(it)}b.rotation.y=Math.PI/12,v.add(b);for(let X of[-1,1])v.add(c([[X*.12,.715],[X*.2,.725],[X*.245,.7],[X*.25,.55],[X*.245,.33],[X*.2,.24],[X*.11,.21]],.0085,i,{radial:10,seg:160}));for(let X of[-1,1]){let z=g();z.position.set(X*.14,.722,.012),z.scale.set(X*1,1,1),z.rotation.y=X*-.35,v.add(z)}return v},facup(){let v=new Dt;v.add(l([[.18,0],[.18,.06],[.176,.075]],n,128,{raw:!0})),v.add(h(.177,.075,.006,i)),v.add(l([[.172,.078],[.165,.1],[.135,.15],[.095,.2],[.07,.235],[.06,.25]],i)),v.add(l([[.06,.25],[.085,.265],[.088,.28],[.06,.295],[.055,.31]],i)),v.add(l([[.055,.31],[.11,.33],[.155,.37],[.162,.42],[.14,.47],[.122,.5]],i,160,{flute:{y0:.31,y1:.49,n:14,depth:.06}})),v.add(l([[.12,.5],[.125,.56],[.14,.64],[.158,.71],[.168,.745],[.175,.755]],i)),v.add(h(.13,.5,.008,i)),v.add(h(.172,.756,.007,i)),v.add(l([[.17,.758],[.168,.775],[.145,.805],[.1,.835],[.06,.852],[.045,.86]],i,160,{flute:{y0:.765,y1:.85,n:16,depth:.05}})),v.add(l([[.045,.86],[.03,.875],[.042,.9],[.028,.93],[.032,.96],[.018,.985],[.001,1]],i));let b=c([[.15,.725],[.2,.75],[.245,.72],[.25,.64],[.21,.58],[.165,.555],[.14,.54]],.011,i);v.add(b),v.add(u(b));for(let x of[-1,1]){let _=c([[x*.2,.75],[x*.215,.77],[x*.232,.765],[x*.232,.748]],.008,i,{seg:30});v.add(_)}return v},leaguecup(){let v=new Dt;v.add(l([[.21,0],[.21,.05],[.19,.06],[.19,.1],[.17,.11]],n,128,{raw:!0})),v.add(h(.2,.05,.006,i)),v.add(h(.18,.105,.006,i)),v.add(l([[.165,.11],[.15,.13],[.1,.17],[.07,.2],[.06,.24]],i)),v.add(l([[.06,.24],[.08,.255],[.06,.27],[.065,.29]],i)),v.add(l([[.065,.29],[.13,.32],[.2,.38],[.235,.46],[.24,.55],[.25,.62],[.262,.64]],i,160,{flute:{y0:.3,y1:.42,n:18,depth:.04}})),v.add(h(.258,.64,.007,i)),v.add(l([[.245,.64],[.235,.6],[.2,.5],[.12,.4],[.06,.34]],t,128,{raw:!1}));for(let b=0;b<3;b++){let x=new Dt;x.rotation.y=b/3*Math.PI*2+Math.PI/2,x.add(c([[.235,.6],[.3,.62],[.335,.57],[.31,.48],[.25,.44],[.2,.42]],.012,i)),x.add(c([[.3,.62],[.32,.65],[.345,.64],[.34,.615]],.008,i,{seg:30})),v.add(x)}return v},ucl(){let v=new Dt;v.add(l([[.165,0],[.168,.008],[.16,.018],[.11,.03],[.075,.045]],i));for(let[x,_]of[[.055,.07],[.075,.062],[.095,.07],[.115,.06]])v.add(h(_,x,.009,i));v.add(l([[.075,.045],[.05,.06],[.045,.13],[.05,.15],[.07,.17]],i)),v.add(l([[.07,.17],[.1,.21],[.14,.3],[.175,.42],[.195,.55],[.198,.63],[.188,.69],[.165,.73],[.152,.745],[.155,.765],[.168,.79],[.172,.8]],i)),v.add(h(.17,.8,.006,i)),v.add(h(.153,.745,.005,i));let b=c([[.09,.22],[.16,.3],[.235,.45],[.275,.62],[.29,.8],[.285,.94],[.255,.995],[.215,.985],[.195,.93],[.21,.88],[.2,.83],[.17,.8]],.016,i,{flat:.45,seg:200});return v.add(b),v.add(u(b)),v},uel(){let v=new Dt,b=new at(new dn(.34,.12,.25,1,1,1),o);b.position.y=.06,v.add(b),v.add(l([[.14,.12],[.13,.135],[.09,.16],[.06,.18]],i,128,{squash:.75})),v.add(l([[.06,.18],[.05,.22],[.045,.27],[.055,.31]],i,96,{squash:.75})),v.add(h(.05,.29,.011,i)),v.add(l([[.055,.31],[.1,.36],[.15,.44],[.19,.56],[.22,.7],[.24,.84],[.25,.95],[.252,.99]],i,192,{flute:{y0:.33,y1:.98,n:20,depth:.045},squash:.7}));let x=h(.25,.99,.008,i);return x.scale.y=.7,v.add(x),v},uecl(){let v=new Dt;v.add(l([[.16,0],[.16,.04],[.14,.06],[.08,.07]],i));for(let b=0;b<32;b++){let x=b/32*Math.PI*2,_=[];for(let R=0;R<=10;R++){let I=.07+R*.093,L=.07+.17*Math.pow(R/10,1.4)-.02*Math.sin(Math.PI*R/10),N=x+.5*(R/10);_.push([L*Math.cos(N),I,L*Math.sin(N)])}v.add(c(_,.0105,i,{radial:6,seg:60}))}return v.add(h(.24,1,.01,i)),v.add(h(.075,.075,.012,i)),v},generic(){let v=new Dt;v.add(l([[.17,0],[.17,.08],[.155,.09]],n,96,{raw:!0})),v.add(l([[.15,.09],[.1,.14],[.06,.2],[.05,.3],[.08,.36],[.16,.48],[.2,.62],[.215,.75],[.225,.8]],i));let b=c([[.2,.72],[.28,.72],[.3,.6],[.24,.5],[.17,.47]],.013,i);return v.add(b),v.add(u(b)),v},laliga(){let v=new Dt;return v.add(l([[.2,0],[.202,.03],[.195,.045],[.17,.06],[.165,.085],[.13,.11],[.09,.14],[.07,.17]],i)),v.add(h(.2,.02,.006,i)),v.add(h(.168,.075,.006,e)),v.add(l([[.07,.17],[.05,.21],[.045,.27],[.05,.32],[.07,.36]],i)),v.add(h(.05,.255,.014,i)),v.add(l([[.07,.36],[.13,.385],[.2,.43],[.255,.51],[.28,.61],[.282,.71],[.268,.81],[.255,.88],[.262,.94],[.285,.99]],i,192,{flute:{y0:.37,y1:.6,n:24,depth:.05}})),v.add(h(.283,.66,.007,e)),v.add(h(.284,.99,.008,i)),v.add(l([[.276,.99],[.25,.93],[.25,.85],[.2,.62],[.08,.42]],t,128)),v},seriea(){let v=new Dt;v.add(l([[.17,0],[.172,.025],[.16,.04],[.15,.06],[.11,.085],[.075,.11],[.06,.13]],i)),v.add(h(.168,.014,.007,e)),v.add(h(.151,.056,.005,i)),v.add(l([[.06,.13],[.042,.18],[.036,.26],[.04,.33],[.055,.37]],i));let b=d(.05,e);b.scale.set(1,.6,1),b.position.y=.3,v.add(b),v.add(l([[.055,.37],[.075,.4],[.1,.45],[.125,.53],[.15,.63],[.18,.74],[.22,.85],[.26,.94],[.29,.99],[.3,1]],i,192)),v.add(h(.299,1,.007,e)),v.add(l([[.292,.998],[.25,.93],[.2,.83],[.15,.68],[.1,.5]],t,128));for(let[x,_,R,I]of[[.63,.15,24,.011],[.48,.112,18,.009]]){v.add(h(_,x,.004,e));for(let L=0;L<R;L++){let N=L/R*Math.PI*2,V=d(I,e);V.position.set((_+I*.5)*Math.cos(N),x,(_+I*.5)*Math.sin(N)),v.add(V)}}return v},bundesliga(){let v=new Dt,b=new Dt;b.add(l([[.001,-.012],[.08,-.008],[.2,.004],[.27,.016],[.293,.03],[.301,.044],[.297,.052],[.282,.046],[.25,.031],[.2,.022],[.12,.018],[.08,.024],[.064,.032],[.001,.034]],i,192,{n:220}));let x=R=>R<.12?.018+(.12-R)*.15:R<.2?.018+(R-.12)*.05:.022+(R-.2)*.18;for(let[R,I]of[[.128,.152],[.162,.186],[.196,.222],[.232,.258]])b.add(l([[I,x(I)+.0016],[R,x(R)+.0016]],t,192,{raw:!0}));b.add(h(.087,.03,.008,e));let _=new fn({color:817730,roughness:.05,metalness:0,clearcoat:1,clearcoatRoughness:.03,emissive:137227});for(let R=0;R<5;R++){let I=R/5*Math.PI*2+Math.PI/2,L=new at(new bi(.014,1),_);L.scale.set(1,.6,1),L.position.set(.087*Math.cos(I),.04,.087*Math.sin(I)),b.add(L)}return b.add(l([[.066,.033],[.058,.046],[.035,.052],[.001,.053]],i,128)),b.rotation.x=Math.PI/2-.14,v.add(b),v},ligue1(){let v=new Dt,b=new at(new Ke(.2,.215,.05,6),t);b.position.y=.025,v.add(b);let x=new at(new Ke(.155,.18,.05,6),i);x.position.y=.075,v.add(x);let _=new fn({color:15659508,metalness:1,roughness:.12,clearcoat:.4,clearcoatRoughness:.1,side:de}),R=60,I=.1,L=1,N=z=>.155-.075*Math.sin(Math.PI*Math.min(1,z*1.3))+.16*z*z,V=(z,j)=>{let it=z/6*Math.PI*2+j*Math.PI/3+Math.PI/6,st=N(j);return[st*Math.cos(it),I+j*(L-I),st*Math.sin(it)]};for(let z=0;z<6;z++){let j=[],it=[],st=[],yt=[];for(let $=0;$<=R;$++){let nt=$/R,ft=V(z,nt),ct=V(z+1,nt),bt=ft.map((Et,Kt)=>Et+(ct[Kt]-Et)*.07),Lt=ft.map((Et,Kt)=>Et+(ct[Kt]-Et)*.93);if(j.push(...bt,...Lt),$%4===0&&(st.push(bt),yt.push(Lt)),$<R){let Et=$*2;it.push(Et,Et+2,Et+1,Et+1,Et+2,Et+3)}}let Ft=new Yt;Ft.setAttribute("position",new Bt(j,3)),Ft.setIndex(it),Ft.computeVertexNormals(),v.add(new at(Ft,_)),v.add(c(st,.0055,i,{radial:8,seg:60})),v.add(c(yt,.0055,i,{radial:8,seg:60}))}let F=new at(new As(N(1)*.93,.01,10,6),i);F.rotation.x=Math.PI/2,F.rotation.z=Math.PI/6+Math.PI/3,F.position.y=L,v.add(F);let X=new at(new As(N(0)*.93,.009,10,6),e);return X.rotation.x=Math.PI/2,X.rotation.z=Math.PI/6,X.position.y=I+.002,v.add(X),v}},m={};function p(v,b){let x=new Ct(v);return x.multiplyScalar(b),"#"+x.getHexString()}function w(v,b,x){let _=v+"|"+(b||"")+"|"+(x||"");if(m[_])return m[_];let R=document.createElement("canvas");R.width=128,R.height=2048;let I=R.getContext("2d"),L=I.createLinearGradient(0,0,128,0);if(L.addColorStop(0,p(v,.72)),L.addColorStop(.3,v),L.addColorStop(.5,p(v,1.15)),L.addColorStop(.75,v),L.addColorStop(1,p(v,.7)),I.fillStyle=L,I.fillRect(0,0,128,2048),b){I.save(),I.translate(66,0),I.rotate(Math.PI/2),I.font="700 76px 'Barlow Condensed', 'Arial Narrow', sans-serif",I.fillStyle=x||"#fff",I.textBaseline="middle";for(let V=30;V<2048;V+=560)I.fillText(b,V,0);I.restore()}let N=new ai(R);return N.colorSpace=Pe,N.wrapS=ei,N.wrapT=_s,N.anisotropy=4,m[_]=N,N}let E=[];function M(v,b,x){let R=new Yt;R.setAttribute("position",new Ut(new Float32Array(57*6),3)),R.setAttribute("uv",new Ut(new Float32Array(57*4),2));let I=[];for(let V=0;V<56;V++){let F=V*2;I.push(F,F+2,F+1,F+1,F+2,F+3)}R.setIndex(I);let L=new fn({map:x,roughness:.42,metalness:0,sheen:1,sheenRoughness:.35,sheenColor:new Ct(16777215),side:de}),N=new at(R,L);return N.userData.rib=Object.assign({p0:v,N:56,width:.032,len:.6,out:.06,side:1,zd:.03,tw0:0,tw:.5,ph:0},b),U(N,0),E.push(N),N}function U(v,b){let x=v.userData.rib,_=v.geometry.attributes.position.array,R=v.geometry.attributes.uv.array,I=x.len/(x.width*16);for(let L=0;L<=x.N;L++){let N=L/x.N,V=x.p0[1]-N*x.len,F=x.out*N*N+.014*N*Math.sin(N*5+b*1.7+x.ph),X=x.p0[0]+x.side*F,z=x.p0[2]+x.zd*N+.012*N*Math.sin(N*4+b*1.3+x.ph*1.3),j=x.tw0+x.tw*Math.sin(N*3.1+x.ph+b*.9)*Math.min(1,N*3),it=Math.cos(j)*x.width/2,st=Math.sin(j)*x.width/2;_.set([X-it,V,z-st,X+it,V,z+st],L*6),R.set([0,1-N*I,1,1-N*I],L*4)}v.geometry.attributes.position.needsUpdate=!0,v.geometry.attributes.uv.needsUpdate=!0,v.geometry.computeVertexNormals()}function C(v){let b=new Dt,x=window.RIBBON_COLORS||["#d0101e","#f5a623"];if(v==="pl"){let _=w("#c8102e","Premier League","#ffffff");for(let R of[-1,1])[[.14,.035,.64,.04,0],[.175,0,.68,.07,1.7],[.21,-.03,.62,.1,3.1]].forEach(([I,L,N,V,F])=>{b.add(M([R*I,.735,L],{side:R,len:N,out:V,ph:F+(R>0?.6:0),tw0:R*.15,tw:.4,width:.05},_))})}else if(v==="facup"||v==="leaguecup"||v==="english"){let _=v==="facup"?[.21,.745]:v==="leaguecup"?[.3,.6]:[.27,.72],R=v==="leaguecup"?.52:.64;for(let I of[-1,1])[[0,.025,0],[.035,-.02,2.2]].forEach(([L,N,V],F)=>{let X=w(x[(F+(I>0?1:0))%2]);b.add(M([I*(_[0]+L),_[1],N],{side:I,len:R-F*.04,out:.07+F*.03,ph:V+(I>0?.9:0),tw0:I*.2,tw:.6,width:.036},X))})}return b}return{BUILD:y,ribbonsFor:C,shapeRibbon:U,materials:{silver:i,silverSatin:t,gold:e,ebony:n}}}var Wg=2;function du(i,t,e,n){let s=new vn,r=new ws(i);s.environment=r.fromScene(new Do,.03).texture,r.dispose(),s.environmentIntensity=.85;let o=new ki(16777215,2);o.position.set(-2.5,3,3),s.add(o);let a=new ki(13623551,2.4);a.position.set(3,1.8,-2.5),s.add(a);let l=new ki(16767400,1.4);l.position.set(-3,.5,-2.5),s.add(l);let c=new ki(16773853,.5);c.position.set(2,-1,3),s.add(c);let h=new Fe(26,16/9,.1,2e3),d=new ze(e.starGeo,e.starMat);d.frustumCulled=!1,d.renderOrder=-5,s.add(d);let u=uu(),f=(u.BUILD[t]||u.BUILD.generic)(),g=new Pn().setFromObject(f);f.add(u.ribbonsFor(t));let y=g.max.y-g.min.y,m=Math.max(g.max.x-g.min.x,g.max.z-g.min.z),p=Wg/Math.max(y,m*.92),w=new Dt;w.add(f),f.position.y=-g.min.y,w.scale.setScalar(p);let E=new Dt;E.add(w),s.add(E);let M=y*p,U=new Rn(new P(0,-1,0),10),C=[];f.traverse(D=>{D.isMesh&&(D.material.clippingPlanes=[U],D.frustumCulled=!1),D.userData&&D.userData.rib&&C.push(D)});let v=t==="bundesliga",b=v?0:-.5;E.rotation.y=b,E.updateMatrixWorld(!0);let x=new P(0,0,1),_=new ne().copy(f.matrixWorld).invert(),R=x.clone().transformDirection(_),I=Gn(77),L=[],N=[],V=[],F=new P,X=new P,z=new Ot;f.traverse(D=>{if(!D.isMesh||D.userData.rib)return;let G=new ne().multiplyMatrices(_,D.matrixWorld);z.getNormalMatrix(G);let Z=D.geometry.attributes.position,W=D.geometry.attributes.normal,et=Math.max(1,Math.floor(Z.count/3e3));for(let Q=0;Q<Z.count;Q+=et){if(F.fromBufferAttribute(Z,Q).applyMatrix4(G),W){X.fromBufferAttribute(W,Q).applyMatrix3(z).normalize();let rt=X.dot(R);Math.abs(rt)<.16?L.push(F.clone()):rt>.75&&V.push(F.clone())}N.push(F.clone())}});let j=(D,G)=>{let Z=[];for(let W=0;W<G&&D.length;W++)Z.push(D[Math.floor(I()*D.length)]);return Z},it=n.low?520:900,st=n.low?160:300,yt=j(L,it).concat(j(N,st)),Ft=new P(0,M*.9,-2.5).applyMatrix4(_),$=yt.length,nt=new Float32Array($*3),ft=new Float32Array($*3),ct=new Float32Array($*4);yt.forEach((D,G)=>{nt.set([D.x,D.y,D.z],G*3);let Z=I()*2-1,W=I()*Oe,et=Math.sqrt(1-Z*Z),Q=(1.2+I()*3.2)/p;ft.set([et*Math.cos(W)*Q,Z*Q*.7,et*Math.sin(W)*Q],G*3),ct.set([I(),I(),I(),I()],G*4)});let bt=new Yt;bt.setAttribute("position",new Ut(nt,3)),bt.setAttribute("aBurst",new Ut(ft,3)),bt.setAttribute("aRnd",new Ut(ct,4));let Lt=Ee({uniforms:{uT:{value:0},uOrigin:{value:Ft},uOut:{value:0}},vertexShader:`
      attribute vec3 aBurst; attribute vec4 aRnd;
      uniform float uT, uPx, uOut, uTime; uniform vec3 uOrigin;
      varying float vB; varying float vPop;
      void main() {
        float b = 1.0 - exp(-uT * 2.6);
        vec3 burst = uOrigin + aBurst * b;
        float k0 = 0.12 + aRnd.x * 0.38, k1 = k0 + 0.55 + aRnd.y * 0.22;
        float k = clamp((uT - k0) / (k1 - k0), 0.0, 1.0); k = k * k * (3.0 - 2.0 * k);
        vec3 p = mix(burst, position, k);
        float sw = (1.0 - k) * (1.4 + aRnd.z);       // a swirl round the upright as they gather
        float cs = cos(sw), sn = sin(sw);
        p.xz = mat2(cs, -sn, sn, cs) * p.xz;
        vec4 mv = modelViewMatrix * vec4(p, 1.0);
        gl_Position = projectionMatrix * mv;
        float arrive = uT - k1;
        vPop = arrive > 0.0 ? exp(-arrive / 0.25) : 0.0;
        float fo = clamp((uOut - aRnd.w * 0.5) / 0.5, 0.0, 1.0);
        vB = (0.55 + 0.45 * sin(uTime * (3.0 + aRnd.z * 5.0) + aRnd.w * 30.0)) * (1.0 - fo) * smoothstep(0.0, 0.15, uT);
        gl_PointSize = uPx * (4.0 + 5.0 * aRnd.z + 10.0 * vPop + (1.0 - k) * 4.0 * b) * (1.0 - fo * 0.5);
      }`,fragmentShader:`
      varying float vB; varying float vPop;
      void main() {
        vec2 q = gl_PointCoord * 2.0 - 1.0; float r = dot(q, q);
        float g = exp(-r * 10.0) * 1.4 + 0.2 * exp(-r * 3.0);
        g += (exp(-abs(q.y) * 30.0) * exp(-abs(q.x) * 3.0) + exp(-abs(q.x) * 30.0) * exp(-abs(q.y) * 3.0)) * vPop;
        g *= 1.0 - smoothstep(0.7, 1.0, r);
        vec3 c = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.86, 0.55), vPop);
        gl_FragColor = vec4(c * g * (vB * 1.5 + vPop * 1.8), 1.0);
        ${Zt}
      }`}),Et=new ze(bt,Lt);Et.frustumCulled=!1,Et.renderOrder=6,f.add(Et);let Kt=j(V.length?V:N,36),Ht=_n(Kt.length+3,{order:8});s.add(Ht.mesh);let ue=Kt.map(()=>[I()*Oe,.8+I()*1.6]),O=new at(new Je(1,1),Ee({uniforms:{uA:{value:0}},vertexShader:"varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",fragmentShader:`uniform float uA, uTime; varying vec2 vUv;
      void main() {
        vec2 p = vUv * 2.0 - 1.0; float r = length(p), a = atan(p.y, p.x);
        float rays = pow(0.5 + 0.5 * sin(a * 13.0 + sin(a * 5.0 + uTime * 0.4) * 1.5 + uTime * 0.15), 5.0) + 0.6 * pow(0.5 + 0.5 * sin(a * 23.0 - uTime * 0.25), 9.0);
        float fall = exp(-r * 2.4) * (1.0 - smoothstep(0.7, 1.0, r));
        vec3 c = vec3(1.0, 0.82, 0.5) * rays * fall * 0.3 + vec3(0.55, 0.62, 1.0) * exp(-r * r * 5.0) * 0.22;
        gl_FragColor = vec4(c * uA, 1.0); ${Zt}
      }`}));O.renderOrder=-2,s.add(O);let be=new at(new vo(3.2,48).rotateX(-Math.PI/2),Ee({uniforms:{uA:{value:0}},vertexShader:"varying vec2 vP; void main() { vP = position.xz; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",fragmentShader:`uniform float uA; varying vec2 vP; void main() { float r = length(vP) / 3.2; float g = exp(-r * r * 6.0) * 0.5 + exp(-r * r * 40.0) * 0.4; gl_FragColor = vec4(vec3(0.75, 0.8, 1.0) * g * uA, 1.0); ${Zt} }`}));be.position.y=.002,s.add(be);let Xt=220,kt=new Float32Array(Xt*3),It=new Float32Array(Xt*2);for(let D=0;D<Xt;D++)kt.set([(I()-.5)*6,I()*3.2,(I()-.5)*3-.5],D*3),It.set([I(),I()],D*2);let te=new Yt;te.setAttribute("position",new Ut(kt,3)),te.setAttribute("aRnd",new Ut(It,2));let Rt=new ze(te,Ee({uniforms:{uA:{value:0}},vertexShader:`attribute vec2 aRnd; uniform float uTime, uPx, uA; varying float vA;
      void main() { vec3 p = position; p.y = mod(p.y + uTime * (0.05 + aRnd.x * 0.08), 3.2); p.x += 0.1 * sin(uTime * 0.5 + aRnd.y * 9.0);
        vec4 mv = modelViewMatrix * vec4(p, 1.0); gl_Position = projectionMatrix * mv; gl_PointSize = uPx * (1.5 + aRnd.x * 2.5); vA = uA * (0.3 + 0.7 * aRnd.y) * smoothstep(0.0, 0.4, p.y) * (1.0 - smoothstep(2.6, 3.2, p.y)); }`,fragmentShader:`varying float vA; void main() { vec2 q = gl_PointCoord * 2.0 - 1.0; float g = exp(-dot(q, q) * 5.0); gl_FragColor = vec4(vec3(1.0, 0.92, 0.8) * g * vA * 0.6, 1.0); ${Zt} }`}));Rt.frustumCulled=!1,s.add(Rt);let A=new P;function S(D,G){let Z=v?.55*Math.sin(D*.55-.4):b+.34*D;E.rotation.y=Z;let W=.1*Math.sin(D*.25)-.04*D,et=Ce(7.3,5.95,je(D/5)),Q=Ce(1.1,.5,je((D-1.5)/3.5));h.position.set(Math.sin(W)*et,Q,Math.cos(W)*et),h.lookAt(0,M*.5,0);let rt=je(me((D-1.25)/1.25)),Nt=Ce(-.02,M*1.04,rt);U.constant=rt>=1?1e3:Nt,E.visible=!0,Lt.uniforms.uT.value=D,Lt.uniforms.uOut.value=me((D-2.3)/1.4),Et.visible=D<4.6;let tt=0;f.updateMatrixWorld(!0),Kt.forEach((xt,Mt)=>{A.copy(xt).applyMatrix4(f.matrixWorld);let _t=Math.pow(Math.max(0,Math.sin(G*ue[Mt][1]*2.2+ue[Mt][0])),14)*oe(2.4,3,D);Ht.set(tt++,A,.13,.13,[1,.95,.85],_t*1.6,1)});let lt=.9*p*Math.max(.3,g.max.x-g.min.x);Ht.set(tt++,new P(0,Nt,0),lt*1.4,.2,[1,.85,.6],rt>0&&rt<1?1.6:0,2),Ht.set(tt++,new P(0,Nt,0),lt*.5,lt*.5,[1,.9,.7],rt>0&&rt<1?.25:0,0),Ht.set(tt++,new P(0,M*.9,-2.5),3.5,3.5,[1,.95,.85],Math.exp(-D/.18)*2,1),Ht.touch(),O.position.set(0,M*.55,-1.8),O.scale.setScalar(9),O.quaternion.copy(h.quaternion),O.material.uniforms.uA.value=oe(1.2,2.6,D),be.material.uniforms.uA.value=oe(1,2.5,D),Rt.material.uniforms.uA.value=oe(1.5,3,D),C.forEach(xt=>{window.TROPHY_NO_RIBBONS&&(xt.visible=!1)});for(let xt of C)u.shapeRibbon(xt,G)}return{scene:s,cam:h,frame:S,stars:d,HT:M,kind:t,pivot:E}}var fu="#6D28F2",pu="#FF6B6B",Xg="#E10A1E",qg="#B0061A",Yg="M31 75 L31 50 L54 31 L77 50 L77 75 Z",$g="M49 53 L49 69 L63 61 Z";function Xi(i,t){let e=document.createElement("canvas");return e.width=Math.ceil(i),e.height=Math.ceil(t),e}function Wi(i,t,e,n){i.font=`${t} ${e}px Outfit`,i.letterSpacing=`${n||0}px`}function Zg(i,t,e,n){i.save();let s=i.createLinearGradient(t,e,t+n,e+n);s.addColorStop(0,fu),s.addColorStop(1,pu),i.beginPath(),i.roundRect(t,e,n,n,n*.24),i.shadowColor="rgba(0,0,0,0.45)",i.shadowBlur=n*.35,i.shadowOffsetY=n*.08,i.fillStyle=s,i.fill(),i.shadowColor="transparent",i.shadowBlur=0,i.shadowOffsetY=0;let r=i.createRadialGradient(t+.28*n,e+.18*n,0,t+.28*n,e+.18*n,.6*n);r.addColorStop(0,"rgba(255,255,255,0.2)"),r.addColorStop(1,"rgba(255,255,255,0)"),i.fillStyle=r,i.fill();let o=n*.62,a=o/51,l=t+(n-o)/2,c=e+(n-49*a)/2;i.translate(l-28.5*a,c-28.5*a),i.scale(a,a),i.lineJoin="round";let h=new Path2D(Yg),d=new Path2D($g);i.fillStyle="#fff",i.strokeStyle="#fff",i.lineWidth=5,i.fill(h),i.stroke(h);let u=(t-l)/a+28.5,f=(e-c)/a+28.5,g=i.createLinearGradient(u,f,u+n/a,f+n/a);g.addColorStop(0,fu),g.addColorStop(1,pu),i.fillStyle=g,i.strokeStyle=g,i.lineWidth=2.6,i.fill(d),i.stroke(d),i.restore()}function Jg(i){let t=Xi(10,10).getContext("2d");Wi(t,800,i,0);let e=t.measureText("whitebrook road").width;Wi(t,800,i*.9,0);let n=t.measureText("sports").width,s=i*1.18,r=i*.3,o=n+i*.52,a=i*1.14,l=i*.6,c=s+r+e+i*.3+o+l*2,h=Math.max(s,a)+l*2,d=Xi(c,h),u=d.getContext("2d"),f=h/2;Zg(u,l,f-s/2,s);let g=l+s+r,y=f+i*.33;u.shadowColor="rgba(0,0,0,0.5)",u.shadowBlur=i*.4,u.shadowOffsetY=i*.06,Wi(u,800,i,0),u.textAlign="left",u.textBaseline="alphabetic",u.fillStyle="#fff",u.fillText("whitebrook road",g,y);let m=g+e+i*.3,p=u.createLinearGradient(0,f-a/2,0,f+a/2);return p.addColorStop(0,Xg),p.addColorStop(1,qg),u.beginPath(),u.roundRect(m,f-a/2,o,a,i*.14),u.fillStyle=p,u.fill(),u.shadowColor="transparent",u.shadowBlur=0,u.shadowOffsetY=0,Wi(u,800,i*.9,0),u.fillStyle="#fff",u.fillText("sports",m+i*.26,y-i*.03),{c:d,W:c,H:h}}function mu(i,t,e,n,s,r=24){let o=Xi(10,10).getContext("2d");Wi(o,t,e,n);let a=o.measureText(i).width,l=Xi(a+r*2,e*1.6+r),c=l.getContext("2d");return Wi(c,t,e,n),c.textAlign="left",c.textBaseline="middle",c.fillStyle=s,c.shadowColor="rgba(0,0,0,0.5)",c.shadowBlur=e*.35,c.shadowOffsetY=e*.05,c.fillText(i,r,l.height/2+e*.04),{c:l,W:l.width,H:l.height,tw:a}}function Os(i,t,e){let n=new ai(i);n.colorSpace=Pe,n.anisotropy=4,n.minFilter=ni,n.generateMipmaps=!0;let s=Me({transparent:!0,depthTest:!1,depthWrite:!1,toneMapped:!1,side:de,uniforms:{map:{value:n},uA:{value:0},uSweep:{value:-1}},vertexShader:"varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",fragmentShader:`uniform sampler2D map; uniform float uA, uSweep; varying vec2 vUv;
      void main() {
        vec4 c = texture2D(map, vUv);
        ${e?"float b = exp(-pow((vUv.x - uSweep + (vUv.y - 0.5) * 0.25) * 9.0, 2.0)); c.rgb = mix(c.rgb, vec3(1.0), b * 0.75 * step(0.5, c.a) * 0.9);":""}
        gl_FragColor = vec4(c.rgb, c.a * uA);
        #include <colorspace_fragment>
      }`}),r=new at(new Je(1,1),s);return r.renderOrder=t,r.visible=!1,r}function gu(i){let t=new Dt,e=Jg(118),n=Os(e.c,40,!0);n.userData.size=[e.W,e.H],t.add(n);let s=[],r=74,o=.5*r,a=Xi(10,10).getContext("2d");Wi(a,700,r,0);let l="MATCHDAY".split("").map(b=>a.measureText(b).width),h=-(l.reduce((b,x)=>b+x,0)+o*7)/2;"MATCHDAY".split("").forEach((b,x)=>{let _=mu(b,700,r,0,"#ffffff",18),R=Os(_.c,41,!1);R.userData={size:[_.W,_.H],cx:h+l[x]/2,i:x},t.add(R),s.push(R),h+=l[x]+o});let d=Xi(512,8),u=d.getContext("2d"),f=u.createLinearGradient(0,0,512,0);f.addColorStop(0,"rgba(255,255,255,0)"),f.addColorStop(1,"rgba(255,255,255,0.85)"),u.fillStyle=f,u.fillRect(0,3,512,2);let g=Os(d,41,!1),y=Os(d,41,!1);t.add(g),t.add(y);let m=String(i.home&&i.home.name||"").toUpperCase(),p=String(i.away&&i.away.name||"").toUpperCase(),w=null;if(m&&p){let b=mu(m+"   v   "+p,600,40,5,"rgba(214,220,240,0.92)");w=Os(b.c,41,!1),w.userData.size=[b.W,b.H],t.add(w)}let E=Xi(512,256),M=E.getContext("2d");for(let[b,x]of[[190,"109,40,242"],[330,"255,107,107"]]){let _=M.createRadialGradient(b,128,0,b,128,200);_.addColorStop(0,`rgba(${x},0.55)`),_.addColorStop(1,`rgba(${x},0)`),M.fillStyle=_,M.fillRect(0,0,512,256)}let U=Os(E,39,!1);t.add(U);let C={word:618,md:474,teams:380};function v(b){if(t.visible=b>=0,b<0)return;let x=1+.025*je(me((b-1.5)/5)),_=Ls(me(b/.45)),R=Ce(1.12,1,Ls(me(b/.7)))*x,[I,L]=n.userData.size,N=1;n.visible=!0,n.material.uniforms.uA.value=_,n.scale.set(I*N*R,L*N*R,1),n.position.set(960,C.word,0),n.material.uniforms.uSweep.value=Ce(-.3,1.3,me((b-.35)/.9));let V=.625;s.forEach(z=>{let{size:j,cx:it,i:st}=z.userData,yt=Ls(me((b-V-st*.045)/.75));z.visible=yt>0,z.material.uniforms.uA.value=yt,z.scale.set(j[0]*x,j[1]*x,1),z.position.set(960+it*Ce(2.1,1,yt)*x,C.md,0)});let F=je(me((b-1)/.8)),X=380*x;for(let[z,j]of[[g,-1],[y,1]]){z.visible=F>0,z.material.uniforms.uA.value=F*.8;let it=300*F;z.scale.set(it*j*-1,8,1),z.position.set(960+j*(X+it/2),C.md+2,0)}if(w){let z=Ls(me((b-1.25)/.6));w.visible=z>0,w.material.uniforms.uA.value=z,w.scale.set(w.userData.size[0]*x,w.userData.size[1]*x,1),w.position.set(960,C.teams-14*(1-z),0)}U.visible=!0,U.material.uniforms.uA.value=.55*Ls(me(b/1.2)),U.scale.set(1500*x,760*x,1),U.position.set(960,540,0)}return{group:t,frame:v}}var he={W:4.2,H:3.3,L:46,END:-46};function vu(i){let t=new vn,e=Gn(5),n=Me({side:Re,vertexShader:"varying vec3 vW; varying vec3 vN; varying float vD; void main() { vec4 wp = modelMatrix * vec4(position, 1.0); vW = wp.xyz; vN = normalize(mat3(modelMatrix) * normal); vec4 mv = viewMatrix * wp; vD = length(mv.xyz); gl_Position = projectionMatrix * mv; }",fragmentShader:`
      ${ke}
      varying vec3 vW; varying vec3 vN; varying float vD;
      void main() {
        // light from the opening (z = ${he.END}), falling off back down the tunnel; strips of light overhead every 5 m
        float toEnd = max(0.0, vW.z - (${he.END.toFixed(1)}));
        float open = exp(-toEnd * 0.07) * 2.6;
        float strip = exp(-pow(fract(vW.z / 5.0) - 0.5, 2.0) * 60.0) * 0.35;
        vec3 base = vec3(0.025, 0.028, 0.035);
        float floorK = smoothstep(0.2, -0.5, vW.y - 0.0) ;
        vec3 c = base * (0.4 + open + strip * step(${(he.H-.4).toFixed(2)}, vW.y) * 3.0 + strip);
        c += vec3(0.5, 0.6, 0.8) * open * 0.08;
        // the floor shines: the opening's light in it
        float gl = (vW.y < 0.05) ? exp(-abs(vW.x) * 1.5) * open * 0.25 : 0.0;
        c += vec3(0.7, 0.8, 1.0) * gl;
        // panel joints along the walls
        float j = 1.0 - smoothstep(0.0, 0.03, abs(fract(vW.z / 2.5) - 0.5) - 0.47);
        c *= 1.0 - 0.3 * j * step(0.06, vW.y);
        gl_FragColor = vec4(fogged(c, vD), 1.0);
        ${Zt}
      }`}),s=new at(new dn(he.W,he.H,he.L,1,1,24),n);s.position.set(0,he.H/2,he.END/2),t.add(s);let r=new at(new Je(he.W*.98,he.H*.98),Me({vertexShader:"varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",fragmentShader:`varying vec2 vUv; void main() { vec2 p = abs(vUv - 0.5) * 2.0; float k = 1.0 - 0.25 * max(p.x, p.y); gl_FragColor = vec4(vec3(3.2, 3.4, 3.8) * k, 1.0); ${Zt} }`}));r.position.set(0,he.H/2,he.END+.05),t.add(r);let o=_n(3,{order:9});o.set(0,new P(0,he.H/2,he.END+1),9,7,[.75,.82,1],1.2,0),o.set(1,new P(0,he.H/2,he.END+1.2),26,1.2,[.6,.75,1],.9,2),o.set(2,new P(0,he.H/2,he.END+1.5),4,4,[1,1,1],1.5,1),o.touch(),t.add(o.mesh);let a=Ee({side:de,vertexShader:"varying vec2 vUv; void main() { vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }",fragmentShader:`varying vec2 vUv; void main() { float a = pow(vUv.y, 2.2) * exp(-pow((vUv.x - 0.5) * 4.0, 2.0)); gl_FragColor = vec4(vec3(0.6, 0.7, 0.95) * a * 0.12, 1.0); ${Zt} }`});for(let m=0;m<5;m++){let p=new at(new Je(1.4+e()*1.5,30),a);p.rotation.x=-Math.PI/2+.05,p.rotation.z=(e()-.5)*.3,p.rotation.y=(e()-.5)*.6,p.position.set((e()-.5)*2.5,.6+e()*2.2,he.END+15),t.add(p)}let l=400,c=new Float32Array(l*3),h=new Float32Array(l*2);for(let m=0;m<l;m++)c.set([(e()-.5)*he.W,e()*he.H,he.END+e()*30],m*3),h.set([e(),e()],m*2);let d=new Yt;d.setAttribute("position",new Ut(c,3)),d.setAttribute("aRnd",new Ut(h,2));let u=new ze(d,Ee({vertexShader:`attribute vec2 aRnd; uniform float uTime, uPx, uHalfH; varying float vA;
      void main() { vec3 p = position + vec3(0.15 * sin(uTime * 0.3 + aRnd.x * 20.0), 0.1 * sin(uTime * 0.2 + aRnd.y * 20.0), 0.0);
        vec4 mv = modelViewMatrix * vec4(p, 1.0); gl_Position = projectionMatrix * mv;
        gl_PointSize = clamp(0.012 * projectionMatrix[1][1] * uHalfH / -mv.z, 1.0, 12.0 * uPx);
        vA = exp(-(p.z - (${he.END.toFixed(1)})) * 0.08) * (0.4 + 0.6 * aRnd.y); }`,fragmentShader:`varying float vA; void main() { vec2 q = gl_PointCoord * 2.0 - 1.0; float g = exp(-dot(q, q) * 4.0); gl_FragColor = vec4(vec3(0.9, 0.95, 1.0) * g * vA * 0.9, 1.0); ${Zt} }`}));u.frustumCulled=!1,t.add(u);let f=Io();f.uniforms.uC1.value.setRGB(2.2,2.35,2.7),f.uniforms.uC2.value.setRGB(.5,.55,.7);let g=[];for(let m=0;m<8;m++){let p=dr(f),w=m%2,E=Math.floor(m/2);p.base={x:w?.72:-.72,z:-8-E*3-w*1.4,ph:e(),s:.97+e()*.08},p.root.scale.setScalar(p.base.s),t.add(p.root),g.push(p)}function y(m,p){for(let w of g){let E=w.base.z-m*p,M=w.base.ph+m*p/1.6;w.pose(Object.assign(iu(M),{x:w.base.x,z:E,ry:Math.PI}))}}return{scene:t,mat:f,figs:g,walk:y}}var lc={pl:"pl",facup:"facup",leaguecup:"leaguecup",ucl:"ucl",uel:"uel",uecl:"uecl",laliga:"laliga",seriea:"seriea",bundesliga:"bundesliga",ligue1:"ligue1"},Bs=1920,Ei=1080,Wn,_e,Un,ae,St,Ve,Jt,an,qi,on,Qe,$i,zs,Qt,Dn,Yi,Sn,Fo,wi=1,Ti=Math.PI/2-.3,Be=dt(Math.sin(Ti),0,Math.cos(Ti)),Uo=dt(-Math.cos(Ti),0,Math.sin(Ti)),Te=Po.clone(),Hs=dt(54.35,2.15,-3.3),Mu=dt(45.4,2.55,-3.4),Mn=.62,Kg=Hs.clone().sub(Mu).normalize(),Su=dt(51.6,0,-.3),xu=-Math.PI/2,gr=dt(0,0,0),cc=dt(-22,0,4),hc=dt(.05,0,-1.25).add(cc),uc=dt(0,.11,-1.5);function jg(i,t,e,n){let s=1-n;return dt(s*s*i.x+2*s*n*t.x+n*n*e.x,s*s*i.y+2*s*n*t.y+n*n*e.y,s*s*i.z+2*s*n*t.z+n*n*e.z)}function yn(i){if(i<=0)return Te.clone();if(i<=Mn)return jg(Te,Mu,Hs,i/Mn);let t=i-Mn,e=Hs.clone().addScaledVector(Kg,.5*(1-Math.exp(-t*7)));return e.y=Math.max(.11,Hs.y-.4*t-4*t*t),e}function mr(i,t){let e=Lo(i);an.pose(Object.assign(e,{x:gr.x+Be.x*e.pz,z:gr.z+Be.z*e.pz,ry:Ti},t||{}))}function fr(i){let t=i<0?ru(i*6):su(i),e=ou(Su,xu,t);qi.pose(Object.assign(t,{x:e.x,y:e.y,z:e.z,ry:xu}))}function Xn(i,t){Qe.mesh.quaternion.setFromAxisAngle(t||dt(0,0,1).cross(Be).normalize().negate(),i*55)}function pr(i,t,e,n,s){let r=[];for(let o=0;o<=40;o++)r.push(yn(Math.max(0,i-t*o/40)));$i.update(r,St.position,e,n),$i.mat.uniforms.uA.value=s*me(i/.03)}function Qg(){try{let i=document.createElement("canvas").getContext("webgl2")||document.createElement("canvas").getContext("webgl"),t=i&&i.getExtension("WEBGL_debug_renderer_info");return i?String(t?i.getParameter(t.UNMASKED_RENDERER_WEBGL):i.getParameter(i.RENDERER)):"none"}catch{return"error"}}async function tv(i,t={}){let e=i.q||{},n=Qg(),s=/PowerVR|Rogue|GE8|GE9|Mali-[0-9T]|Mali-G[0-9]{2}\b|Adreno \(TM\) [345]/i.test(n);Wn={gpu:n,weak:s,low:e.low!=null?!!e.low:s,aa:e.aa!=null?!!e.aa:!s,capture:!!t.capture||!!e.capture},wi=+e.scale>0?Math.min(1,+e.scale):s?.5:1,Un=document.createElement("canvas"),Un.id="intro",Un.style.cssText="position:absolute;left:0;top:0;width:100%;height:100%;opacity:0;",(t.parent||document.body).appendChild(Un),_e=new co({canvas:Un,antialias:Wn.aa,alpha:!0,premultipliedAlpha:!0,preserveDrawingBuffer:Wn.capture,powerPreference:"high-performance",stencil:!1}),_e.setPixelRatio(1),bu(wi),_e.toneMapping=Wl,_e.outputColorSpace=Pe,_e.autoClear=!1,_e.localClippingEnabled=!0,_e.setClearColor(197899,1),Eu(),document.fonts&&document.fonts.load&&await Promise.race([Promise.all(["800 118px Outfit","700 74px Outfit","600 40px Outfit",'700 76px "Barlow Condensed"'].map(l=>document.fonts.load(l))),new Promise(l=>setTimeout(l,3e3))]).catch(()=>{}),ae=new vn,St=new Fe(50,Bs/Ei,.03,3e3),Ve=tu(Wn,He),ae.add(Ve.group),Jt=eu(Wn),ae.add(Jt.group),on=Io(),an=dr(on),qi=dr(on,{gloves:!0}),ae.add(an.root),ae.add(qi.root),Qe=au(),ae.add(Qe.mesh),$i=lu(),ae.add($i.mesh),zs=cu(Wn),ae.add(zs.group),Qt=_n(3,{order:10}),ae.add(Qt.mesh),mr(0),an.root.updateMatrixWorld(!0);let r=an.J.anR.localToWorld(dt(0,-.035,.09));gr=Te.clone().addScaledVector(Be,-.11).sub(r).add(gr),gr.y=0;let o=Lo(0);an.pose(Object.assign(o,{x:hc.x,z:hc.z-o.pz,ry:Math.PI})),an.root.updateMatrixWorld(!0),uc=an.J.anR.localToWorld(dt(0,-.035,.09)).add(dt(0,0,-.11)),uc.y=.11,Dn=vu(Wn);let a=lc[i.trophy]||lc[i.comp]||"generic";return Yi=du(_e,a,Ve,Wn),Sn=hu(),Fo=gu(i),Sn.scene.add(Fo.group),{gpu:n,weak:s,low:Wn.low,aa:Wn.aa,scale:wi,kind:a}}function bu(i){wi=me(i,.3,1);let t=Math.round(Bs*wi),e=Math.round(Ei*wi);_e.setSize(t,e,!1),Vi.uHalfH.value=e/2,Vi.uPx.value=e/Ei}function Eu(){let i=document.documentElement,t=i.clientWidth||window.innerWidth||Bs,e=i.clientHeight||window.innerHeight||Ei;if(Un._vw===t&&Un._vh===e)return;Un._vw=t,Un._vh=e;let n=Math.max(t/Bs,e/Ei);Object.assign(Un.style,{width:Bs*n+"px",height:Ei*n+"px",left:(t-Bs*n)/2+"px",top:(e-Ei*n)/2+"px"})}function wu(i){return oe(He.fadeIn[0],He.fadeIn[1],i)*(1-oe(He.fadeOut[0],He.fadeOut[1],i))}function we(i,t,e,n=0){St.position.copy(i),St.up.set(0,1,0),St.lookAt(t),n&&St.rotateZ(n),St.fov=e,St.updateProjectionMatrix(),St.updateMatrixWorld(!0)}function De(i){Ve.group.visible=i.sky!==!1,Ve.C.group.visible=!!i.constellation,Jt.group.visible=i.stadium!==!1,Jt.blades.visible=!!i.blades,Jt.beams.visible=!!i.beams,Jt.hazeG.mesh.visible=!!i.haze,Jt.goals.forEach(t=>{t.G.visible=i.goals!==!1}),an.root.visible=!!i.striker,qi.root.visible=!!i.keeper,Qe.mesh.visible=!!i.ball,$i.mesh.visible=!!i.trail,zs.group.visible=!!i.spray,Qt.mesh.visible=!!i.ball||!!i.bglow}var _u=new P;function ev(i){let t=i.clone().sub(St.position).normalize();return qe.slice().sort((e,n)=>n.clone().sub(i).normalize().dot(t)-e.clone().sub(i).normalize().dot(t))}function Ln(i,t,e,n,s=1,r=1){if(!e){let o=ev(t);e=o[0],n=o[1]}i.uniforms.uL1.value.copy(e).sub(t).normalize().transformDirection(St.matrixWorldInverse),i.uniforms.uL2.value.copy(n).sub(t).normalize().transformDirection(St.matrixWorldInverse),i.uniforms.uC1.value.setRGB(1.6*s,1.7*s,2*s),i.uniforms.uC2.value.setRGB(.95*r,.7*r,.5*r)}function qn(i,t){Qe.mat.uniforms.uL1.value.copy(t).sub(i).normalize().transformDirection(St.matrixWorldInverse)}function rn(i=1){return Jt.masts.map(t=>(_u.copy(St.position).sub(t.glarePos).normalize(),[t.glarePos,Math.pow(Math.max(0,t.fwd.dot(_u)),5)*i]))}function Se(i={}){var t,e,n,s,r,o,a,l,c,h,d,u,f,g,y;Vi.uFogD.value=(t=i.fog)!=null?t:.0045,Vi.uFogCol.value.setRGB(...i.fogCol||[.035,.05,.1]),Ve.starMat.uniforms.uFade.value=(e=i.stars)!=null?e:.55,Ve.milkyMat.uniforms.color.value.setScalar((n=i.milky)!=null?n:.5),Ve.C.starMat.uniforms.uA.value=(s=i.cA)!=null?s:1,Ve.C.lineMat.uniforms.uA.value=(r=i.cA)!=null?r:1,Ve.C.starMat.uniforms.uBoost.value=(o=i.cBoost)!=null?o:0,Jt.pitchMat.uniforms.uLit.value=(a=i.pitch)!=null?a:1,Jt.standMat.uniforms.uLit.value=(l=i.standsLit)!=null?l:1,Jt.crowdMat.uniforms.uBright.value=(c=i.crowd)!=null?c:1,Jt.flashMat.uniforms.uFlash.value=(h=i.flash)!=null?h:.06,Jt.flashMat.uniforms.uRate.value=(d=i.rate)!=null?d:3,Jt.beamMat.uniforms.uA.value=(u=i.beam)!=null?u:.05,_e.toneMappingExposure=(f=i.exposure)!=null?f:1,Sn.vig.material.uniforms.uA.value=(g=i.vig)!=null?g:.6,Sn.vig.material.uniforms.uDim.value=(y=i.dim)!=null?y:0}var yu=dt(0,45,62),nv={sky(i,t){De({stadium:!1,constellation:!0}),Se({stars:oe(0,1.8,i),milky:.85*oe(.3,3,i),cBoost:.7*Ds(i,He.stars.ball[1],.6),fog:0,exposure:1,vig:.7});let e=Ve.C.centre.clone(),n=Ve.C.ballStar.clone(),s=je(t/6.25),r=je((t-6.25)/3.75),o=e.clone().add(dt(-30+30*s,45*(1-s),0)).lerp(n,r*.85);return we(yu,yu.clone().add(o),Ce(Ce(52,43,s),31,r),.03*Math.sin(i*.3)-.02),[ae,St]},tilt(i,t){De({constellation:!0,beams:!0,goals:!0});let e=je(t/3.9),n=je(t/5),s=dt(Ce(0,-16,n),Ce(45,31,n),Ce(62,74,n)),r=Ve.C.ballStar.clone().normalize(),o=dt(6,0,-10).sub(s).normalize(),a=r.lerp(o,e).normalize();return we(s,s.clone().add(a),Ce(31,60,je((t-.2)/3.4)),.04*(1-e)-.015),Se({stars:.95-.55*oe(1.4,3.4,t),milky:.85-.45*oe(1.4,3.4,t),cA:1-oe(1,2.4,t),flash:.04,beam:.045,fog:.0035,exposure:1}),Jt.lights(St,1,.9),[ae,St,{flares:rn(1.1)}]},tunnel(i,t){let e=-2-1.3*t;we(dt(.35,.55+.08*t,e),dt(0,1.45,he.END),Ce(44,38,t/2.5),.02),Dn.walk(2.2+t*.55,1.3),Se({fog:.012,fogCol:[.03,.035,.05],exposure:1.05,vig:.75});let n=dt(0,1.6,he.END);return Dn.mat.uniforms.uL1.value.copy(n).sub(dt(0,1,-16)).normalize().transformDirection(St.matrixWorldInverse),Dn.mat.uniforms.uL2.value.set(0,1,0).transformDirection(St.matrixWorldInverse),[Dn.scene,St]},mast(i,t){De({beams:!0,goals:!0});let e=qe[0],n=dt(34,2,-22).lerp(e,.06*t);return we(n,e.clone().add(dt(0,-2,0)),Ce(36,31,t/1.25),.12-.04*t),Se({flash:.12,rate:4,beam:.06,stars:.4,exposure:1}),Jt.lights(St,1,.55,.6,.7),[ae,St,{flares:rn(.8)}]},crowd(i,t){De({goals:!1});let e=-34+t*7;return we(dt(e,3.2,22),dt(e+10,13,-60),22,-.03),Se({flash:.3,rate:4.5,crowd:1.25,fog:.004,exposure:1.05}),Jt.lights(St,1,1),[ae,St,{flares:rn(.8)}]},runup(i,t){De({striker:!0,ball:!0,blades:!0,goals:!0});let e=-.62+t*.236;mr(e);let n=an.root.position.clone(),s=Te.clone().addScaledVector(Be,2.7).addScaledVector(Uo,-1+.25*t).add(dt(0,.16+.05*t,0));return we(s,n.clone().add(dt(0,1.05,0)).addScaledVector(Be,.5),46,.03),Se({fog:.004,exposure:1.05,flash:.05,beam:.04}),Jt.lights(St,1,1.2),Ln(on,n,null,null,1.2,.9),Qe.mesh.position.copy(Te),Xn(0),qn(Te,qe[1]),Qt.set(0,Te,0,0,[0,0,0],0,0),Qt.touch(),[ae,St,{flares:rn(.9)}]},strike(i,t){De({striker:!0,ball:!0,trail:!0,spray:!0,blades:!0,keeper:!0,goals:!0,beams:!0});let e=t<.7?.02*t:.014+.02*(t-.7)+.062*(t-.7)*(t-.7);mr(e),fr(e);let n=yn(e),s=Te.clone().addScaledVector(Uo,3.3+.5*t).addScaledVector(Be,.5-.3*t).add(dt(0,.3+.1*t,0)),r=Te.clone().add(dt(0,.75,0)).addScaledVector(Be,-.3).lerp(n.clone().add(dt(0,.2,0)),oe(.5,2.3,t));return we(s,r,Ce(40,46,oe(.5,2.5,t)),-.04*oe(.5,2.5,t)),Se({fog:.004,exposure:1.05,flash:.05,beam:.05}),Jt.lights(St,1,1.2),Ln(on,an.root.position,null,null,1.2,1),Qe.mesh.position.copy(n),Xn(e),qn(n,qe[0]),pr(e,.14,.055,.008,1.2),zs.set(e,Te.clone().addScaledVector(Be,-.06).setY(.02),Ti-Math.PI/2),Qt.set(0,n,.45,.45,[1,.85,.6],.6*me(e/.02),0),Qt.set(1,Te.clone().addScaledVector(Be,-.08),.7,.7,[1,.95,.85],1*Ds(i,He.strike,.2),1),Qt.touch(),[ae,St,{flares:rn(1)}]},boot(i,t){De({striker:!0,ball:!0,spray:!0,blades:!0});let e=-.035+t*.06;mr(e);let n=yn(e),s=Te.clone().addScaledVector(Be,.95+.05*t).addScaledVector(Uo,.8).add(dt(0,.1,0));return we(s,Te.clone().addScaledVector(Be,-.22).add(dt(0,.2,0)),32,.05),Se({fog:.004,exposure:1.15,flash:.04}),Jt.lights(St,1,.8),Ln(on,Te,null,null,1.4,1),Qe.mesh.position.copy(n),Xn(e),qn(n,qe[1]),zs.set(e,Te.clone().addScaledVector(Be,-.06).setY(.02),Ti-Math.PI/2),Qt.set(0,n,0,0,[0,0,0],0,0),Qt.set(1,Te.clone().addScaledVector(Be,-.1).add(dt(0,.05,0)),.3,.3,[1,.95,.85],1.1*(e>0?Math.exp(-e/.01):0),1),Qt.touch(),[ae,St]},flight(i,t){De({ball:!0,trail:!0,keeper:!0,goals:!0,beams:!0});let e=.2+t*.19;fr(e);let n=yn(e),s=yn(e+.01).sub(n).normalize(),r=dt(0,1,0).cross(s).normalize(),o=yn(e-.06).add(dt(0,.32,0)).addScaledVector(r,.45);return we(o,n.clone().addScaledVector(s,8).add(dt(0,-.2,0)),52,-.05),Se({fog:.004,exposure:1.05,flash:.1,rate:4,beam:.05}),Jt.lights(St,1,1.2),Ln(on,qi.root.position,null,null,1.3,.9),Qe.mesh.position.copy(n),Xn(e),qn(n,qe[0]),pr(e,.12,.05,.006,1.3),Qt.set(0,n,.5,.5,[1,.85,.6],.7,0),Qt.set(1,n,0,0,[0,0,0],0,1),Qt.touch(),[ae,St,{flares:rn(1)}]},keeper(i,t){De({ball:!0,trail:!0,keeper:!0,goals:!0,beams:!0});let e=.36+t*.24;fr(e);let n=yn(e);return we(dt(47.4+.3*t,.34,-1.4),dt(51.6,1.6,-2),52,.05),Se({fog:.004,exposure:1,flash:.08,beam:.06}),Jt.lights(St,1,1.3),Ln(on,qi.root.position,null,null,1.5,1),Qe.mesh.position.copy(n),Xn(e),qn(n,qe[0]),pr(e,.1,.045,.006,1.3),Qt.set(0,n,.5,.5,[1,.85,.6],.7,0),Qt.set(1,n,0,0,[0,0,0],0,1),Qt.touch(),[ae,St,{flares:rn(1.2)}]},net(i,t){De({ball:!0,trail:!0,keeper:!0,goals:!0});let e=.6+t*.133;fr(e);let n=yn(e);return we(dt(56.5-.15*t,1.3,-1.9),dt(53.6,1.95,-3.1),50,-.04),Se({fog:.004,exposure:1.05,flash:.22,rate:5,beam:.05}),Jt.lights(St,1,1),Jt.netMat.uniforms.uHit.value.set(0,Hs.y,Hs.z,e-Mn),Jt.netMat.uniforms.uAmp.value=e>Mn?1:0,Ln(on,qi.root.position,null,null,1.2,.9),Qe.mesh.position.copy(n),Xn(e),qn(n,qe[1]),pr(Math.min(e,Mn),.1,.045,.006,1*(1-oe(Mn,Mn+.06,e))),Qt.set(0,n,.5,.5,[1,.85,.6],.6*(1-oe(Mn,Mn+.1,e)),0),Qt.set(1,n,1.1,1.1,[1,.95,.85],.7*(e>Mn?Math.exp(-(e-Mn)/.025):0),1),Qt.touch(),[ae,St]},lights(i,t){De({goals:!1,beams:!0});let e=Jt.masts[1],n=e.m.clone(),s=dt(0,0,0).sub(e.m).normalize(),r=e.m.clone().addScaledVector(s,70-8*t).add(dt(-8,-8,0));we(r,n,34,.1-.06*t);let o=He.flashes[4][0];return Jt.lights(St,(a,l)=>a===1?i>=o+l*.156?1:0:1,oe(o+.55,o+.75,i)*.75+.1,.7,.75),Se({beam:.04*oe(o+.5,o+.8,i),exposure:1,flash:.08,stars:.6}),[ae,St,{flares:rn(1.4*oe(o+.55,o+.75,i))}]},hero(i,t){De({striker:!0,goals:!0,beams:!0});let e=dt(-6,0,8);an.pose(Object.assign(ac(i),{x:e.x,z:e.z,ry:.5}));let n=.7+.22*t,s=e.clone().add(dt(Math.sin(n)*2.4,.22,Math.cos(n)*2.4));return we(s,e.clone().add(dt(0,1.55,0)),44,-.05),Se({exposure:1,flash:.08,beam:.06,fog:.004}),Jt.lights(St,1,1.3),Ln(on,e,null,null,1.6,1.1),[ae,St,{flares:rn(1.2)}]},bowl(i,t){De({goals:!0,beams:!0});let e=2.25+.17*t;return we(dt(Math.cos(e)*112,72-4*t,Math.sin(e)*92),dt(0,-4,0),52,.03),Se({flash:.25,rate:4,beam:.04,exposure:1,stars:.5}),Jt.lights(St,1,1),[ae,St,{flares:rn(.8)}]},gk(i,t){return De({keeper:!0,goals:!0,beams:!0}),fr(-1+t*.2),we(dt(46.6+.6*t,.42,1.8),dt(51.6,1.15,-.3),40,-.04),Se({exposure:1,flash:.08,beam:.06}),Jt.lights(St,1,1.3),Ln(on,Su,null,null,1.5,1),[ae,St,{flares:rn(1.2)}]},boot2(i,t){De({striker:!0,ball:!0,spray:!0,blades:!0});let e=-.02+t*.07;mr(e);let n=yn(e),s=Te.clone().addScaledVector(Be,.85).addScaledVector(Uo,-.75).add(dt(0,.08,0));return we(s,Te.clone().addScaledVector(Be,-.2).add(dt(0,.22,0)),30,-.06),Se({fog:.004,exposure:1.15}),Jt.lights(St,1,.8),Ln(on,Te,null,null,1.4,1),Qe.mesh.position.copy(n),Xn(e),qn(n,qe[1]),zs.set(e,Te.clone().addScaledVector(Be,-.06).setY(.02),Ti-Math.PI/2),Qt.set(0,n,0,0,[0,0,0],0,0),Qt.set(1,Te.clone().addScaledVector(Be,-.1).add(dt(0,.05,0)),.3,.3,[1,.95,.85],1.1*(e>0?Math.exp(-e/.01):0),1),Qt.touch(),[ae,St]},whip(i,t){De({ball:!0,trail:!0,goals:!0,beams:!0});let e=.27+t*.23,n=yn(e),s=yn(.36),r=yn(.37).sub(s).normalize(),o=dt(0,1,0).cross(r).normalize(),a=s.clone().addScaledVector(o,1.1).add(dt(0,.15,0));return we(a,a.clone().addScaledVector(r,-1).addScaledVector(o,-.25).add(dt(0,.08,0)),50,.12),Se({fog:.004,exposure:1.05,flash:.1}),Jt.lights(St,1,1.1),Qe.mesh.position.copy(n),Xn(e),qn(n,qe[1]),pr(e,.13,.05,.006,1.5),Qt.set(0,n,.5,.5,[1,.85,.6],.8,0),Qt.set(1,n,0,0,[0,0,0],0,1),Qt.touch(),[ae,St,{flares:rn(1)}]},tunnel2(i,t){return we(dt(-.5,1.25,-7.5-t*2),dt(.2,1.5,he.END),34,-.03),Dn.walk(3.6+t*.6,1.3),Se({fog:.012,fogCol:[.03,.035,.05],exposure:1.1,vig:.75}),Dn.mat.uniforms.uL1.value.copy(dt(0,1.6,he.END)).sub(dt(0,1,-16)).normalize().transformDirection(St.matrixWorldInverse),Dn.mat.uniforms.uL2.value.set(0,1,0).transformDirection(St.matrixWorldInverse),[Dn.scene,St]},spin(i,t){De({striker:!0,ball:!0,goals:!0,beams:!0});let e=dt(-.25,0,.45);an.pose(Object.assign(ac(i),{x:e.x,z:e.z,ry:2.6,head:-.1})),Qe.mesh.position.set(.05,.11,-.1),Xn(0);let n=1.2+je(t/.625)*1.9,s=3.4-.8*t;return we(e.clone().add(dt(Math.sin(n)*s,.45+.5*t,Math.cos(n)*s)),e.clone().add(dt(0,1.25,0)),52,.1),Se({flash:.3,rate:5,exposure:1,fog:.004}),Jt.lights(St,1,1),Ln(on,e,null,null,1.4,1),qn(dt(0,0,0),qe[0]),Qt.set(0,dt(0,0,0),0,0,[0,0,0],0,0),Qt.set(1,dt(0,0,0),0,0,[0,0,0],0,1),Qt.touch(),[ae,St,{flares:rn(1)}]},rise(i,t){De({striker:!0,ball:!0,trail:!0,constellation:!0,goals:!0,beams:!0});let e=dt(.2,.38,2.4).add(cc),n=Ve.C.ballStar.clone().normalize(),s=dt(0,1.1,-6).add(cc).sub(e).normalize(),r=s.clone(),o=(t-.12)*.9,a=Lo(o),l=hc;an.pose(Object.assign(a,{x:l.x,z:l.z-a.pz,ry:Math.PI}));let c=uc,d=e.clone().add(Ve.C.ballStar).clone().sub(c),u=M=>M<.12?0:Math.pow(me((M-.12)/1.75),2.4),f=u(t),g=c.clone().addScaledVector(d,f*.985);Qe.mesh.position.copy(g),Xn(t*.3);let y=g.clone().sub(e).normalize();r=s.clone().lerp(y,oe(.15,.7,t)).lerp(n,oe(1.2,1.9,t)).normalize(),we(e,e.clone().add(r),Ce(56,28,je(me((t-.3)/1.8))),0);let m=g.distanceTo(e),p=[];for(let M=0;M<=40;M++){let U=u(t-M/40*.22);p.push(c.clone().addScaledVector(d,U*.985))}$i.update(p,St.position,.06+m*.0035,.004+m*6e-4),$i.mat.uniforms.uA.value=1.3*oe(.12,.2,t)*(1-oe(1.75,1.95,t));let w=He.star;Qt.set(0,g,.35+m*.006,.35+m*.006,[1,.9,.7],.9*oe(.12,.25,t),0),Qt.set(1,g,m*.09,m*.09,[1,.95,.85],2.2*Ds(i,w,.5)+.5*oe(w,w+.2,i),1),Qt.set(2,g,m*.35,m*.012,[.7,.8,1],1.4*Ds(i,w,.6),2),Qt.touch();let E=oe(He.hush[0],He.hush[1],i);return Se({stars:.75+.25*oe(.8,1.8,t),milky:.55,cA:.25+.75*oe(w-.3,w+.2,i),fog:.004,flash:.15*(1-E),rate:4,exposure:1-.35*E,dim:.55*E,beam:.05}),Jt.lights(St,1,.3*(1-E*.7),.8,.45),Ln(on,l,null,null,1.3,1),qn(g,qe[1]),[ae,St,{flares:rn(.35*(1-E)).concat([[g,1.6*Ds(i,w,.5)]])}]},trophy(i,t){return Yi.frame(t,i),Se({stars:.75,exposure:1,vig:.65}),[Yi.scene,Yi.cam]},card(i,t){De({stadium:!1});let e=.2+.012*t;return we(dt(0,0,0),dt(Math.sin(e)*10,6.5+.05*t,-Math.cos(e)*10),50,.08),Se({stars:.8,milky:.7,fog:0,exposure:1,vig:.45}),Fo.frame(t),[ae,St]}},No=He.shots;function iv(i){for(let t of No)if(i<t.t1)return t;return No[No.length-1]}function sv(i,t){Eu(),_e.info.autoReset=!1,_e.info.reset(),Vi.uTime.value=i;let e=iv(i);Fo.group.visible=e.id==="card";let[n,s,r]=nv[e.id](i,i-e.t0,(i-e.t0)/(e.t1-e.t0));n===ae&&Ve.group.position.copy(s.position),_e.clear(),_e.render(n,s);let o=0;for(let[l,c]of He.flashes)i>=l&&(o+=c*Math.exp(-(i-l)/(c>.8?.2:.12)));Sn.flashMat.uniforms.uA.value=Math.min(1.2,o);let a=0;for(He.streaks.forEach(([l,c,h],d)=>{let u=(i-l)/.45;u<0||u>1||a>2||Sn.streak(a++,u,c*Ei,h,1.2*Math.sqrt(Math.sin(Math.PI*u)),d%2?[.55,.75,1]:[1,.8,.5])});a<3;a++)Sn.streaks[a].visible=!1;Sn.flare(s,r&&r.flares||[]),_e.render(Sn.scene,Sn.cam),Un.style.opacity=(t==null?wu(i):t).toFixed(3)}function rv(){let i=_e.info;return{calls:i.render.calls,tris:i.render.triangles,points:i.render.points,geos:i.memory.geometries,tex:i.memory.textures,scale:wi,w:_e.domElement.width,h:_e.domElement.height}}function ov(i){return _e.compile(ae,St),_e.compile(Dn.scene,St),_e.compile(Yi.scene,Yi.cam),_e.compile(Sn.scene,Sn.cam),No.map(t=>(t.t0+t.t1)/2)}var av=()=>_e.getContext();window.INTRO={init:tv,draw:sv,opacity:wu,setScale:bu,info:rv,warm:ov,gl:av,LEN:He.len,TEMPO:He,TROPHY:lc,getScale:()=>wi,parts:()=>({st:Jt,sky:Ve,stage:Yi,tunnel:Dn})};})();
/**
 * @license
 * Copyright 2010-2024 Three.js Authors
 * SPDX-License-Identifier: MIT
 */
